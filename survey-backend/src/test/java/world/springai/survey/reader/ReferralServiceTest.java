package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import world.springai.survey.audience.SurveyResponse;
import world.springai.survey.audience.SurveyResponseRepository;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 邀請獎勵：發放條件與冪等 */
class ReferralServiceTest {

    private static final long REFERRER_ID = 7L;

    private SurveyResponseRepository surveyResponseRepository;
    private ReaderRepository readerRepository;
    private CreditTxnRepository creditTxnRepository;
    private CreditPolicy creditPolicy;
    private ReferralService service;

    @BeforeEach
    void setUp() {
        surveyResponseRepository = mock(SurveyResponseRepository.class);
        readerRepository = mock(ReaderRepository.class);
        creditTxnRepository = mock(CreditTxnRepository.class);
        creditPolicy = mock(CreditPolicy.class);
        when(creditPolicy.referralReward()).thenReturn(100);
        service = new ReferralService(surveyResponseRepository, readerRepository,
            creditTxnRepository, creditPolicy);
    }

    /** 建一筆帶（或不帶）推薦碼的名單資料 */
    private SurveyResponse invitee(String email, String refCode) {
        SurveyResponse r = new SurveyResponse();
        r.setEmail(email);
        if (refCode != null) {
            r.setAnswers(Map.of("_ref", refCode));
        }
        return r;
    }

    /** 建一個帶 id 的推薦人 */
    private Reader referrer(String email, String code) {
        Reader reader = new Reader(email, code);
        ReflectionTestUtils.setField(reader, "id", REFERRER_ID);
        return reader;
    }

    /** 讓「查得到被邀者且帶推薦碼、查得到推薦人」成立 */
    private void givenReferralChain(String inviteeEmail, String referrerEmail, String code) {
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(anyString()))
            .thenReturn(Optional.of(invitee(inviteeEmail, code)));
        when(readerRepository.findByReferralCode(code))
            .thenReturn(Optional.of(referrer(referrerEmail, code)));
    }

    /** 正常路徑：發放獎勵、寫帳本、加餘額 */
    @Test
    void rewardsReferrerOnFirstConfirm() {
        givenReferralChain("invitee@b.com", "host@b.com", "CODE1234");
        when(readerRepository.addCredits(REFERRER_ID, 100)).thenReturn(1);

        assertEquals(ReferralService.RewardOutcome.REWARDED, service.rewardFor("invitee@b.com"));

        verify(readerRepository).addCredits(REFERRER_ID, 100);
        verify(creditTxnRepository).saveAndFlush(any(CreditTxn.class));
    }

    /**
     * 帳本的 note 必須恰好是被邀者 email。
     *
     * <p>note 同時是資料庫冪等索引 {@code uq_credit_txn_referral_note} 的鍵。
     * 若實作寫成「邀請 invitee@b.com」這種帶前綴的可讀字串，或加上時間戳，
     * 每次都會是不同的值，唯一索引就形同不存在——重複點確認信會重複發獎。
     * 這條測試釘住的正是那個值本身。</p>
     */
    @Test
    void ledgerNoteIsExactlyTheInviteeEmail() {
        givenReferralChain("invitee@b.com", "host@b.com", "CODE1234");
        when(readerRepository.addCredits(anyLong(), anyInt())).thenReturn(1);

        service.rewardFor("invitee@b.com");

        ArgumentCaptor<CreditTxn> captor = ArgumentCaptor.forClass(CreditTxn.class);
        verify(creditTxnRepository).saveAndFlush(captor.capture());
        assertEquals("invitee@b.com", captor.getValue().getNote());
        assertEquals(CreditTxn.REASON_REFERRAL, captor.getValue().getReason());
        assertEquals(100, captor.getValue().getDelta());
        assertEquals(REFERRER_ID, captor.getValue().getReaderId());
    }

    /**
     * 撞上資料庫的冪等索引時，例外必須<b>原樣往外拋</b>，不可在本服務內被吞掉。
     *
     * <p>{@code rewardFor} 帶 {@code @Transactional}，交易一旦因約束違反被標記為
     * rollback-only，在方法內捕捉並正常回傳（例如回 {@code ALREADY_REWARDED}）
     * 會讓提交時改拋 {@code UnexpectedRollbackException}，呼叫端收到一個看起來
     * 毫無關聯的錯誤。捕捉必須發生在交易邊界之外（{@link ReferralRewardListener}）。</p>
     *
     * <p>破壞性驗證：把 {@code ReferralRewardListener} 的
     * {@code DataIntegrityViolationException} catch 搬進 {@code rewardFor} 內，
     * 本測試立刻變紅。</p>
     */
    @Test
    void uniqueViolationPropagatesOutOfTheTransactionalMethod() {
        givenReferralChain("invitee@b.com", "host@b.com", "CODE1234");
        when(creditTxnRepository.saveAndFlush(any(CreditTxn.class)))
            .thenThrow(new DataIntegrityViolationException("uq_credit_txn_referral_note"));

        assertThrows(DataIntegrityViolationException.class,
            () -> service.rewardFor("invitee@b.com"),
            "冪等索引的撞擊被服務內部吞掉了：交易已是 rollback-only，"
                + "正常回傳會讓提交改拋 UnexpectedRollbackException");
    }

    /**
     * 帳本必須用 {@code saveAndFlush} 而不是 {@code save}。
     *
     * <p>唯一索引的違反要在 repository 呼叫當下就浮現，才會被轉譯成
     * {@code DataIntegrityViolationException}；用 {@code save} 會延到提交時才違反，
     * 那時已離開 repository 的例外轉譯範圍，交易外的捕捉接到的是型別完全不同的
     * JPA 例外，冪等會靜默失效成 500。</p>
     */
    @Test
    void ledgerWriteIsFlushedSoTheViolationSurfacesHere() {
        givenReferralChain("invitee@b.com", "host@b.com", "CODE1234");
        when(readerRepository.addCredits(anyLong(), anyInt())).thenReturn(1);

        service.rewardFor("invitee@b.com");

        verify(creditTxnRepository).saveAndFlush(any(CreditTxn.class));
        verify(creditTxnRepository, never()).save(any(CreditTxn.class));
    }

    /** 沒有推薦碼就沒有獎勵（絕大多數訂閱者走這條） */
    @Test
    void noReferralCodeMeansNoReward() {
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(anyString()))
            .thenReturn(Optional.of(invitee("plain@b.com", null)));

        assertEquals(ReferralService.RewardOutcome.NO_REFERRER, service.rewardFor("plain@b.com"));
        verify(readerRepository, never()).addCredits(anyLong(), anyInt());
    }

    /** 推薦碼查不到對應讀者（亂改連結）時不發獎，也不可拋例外 */
    @Test
    void unknownReferralCodeMeansNoReward() {
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(anyString()))
            .thenReturn(Optional.of(invitee("invitee@b.com", "GARBAGE1")));
        when(readerRepository.findByReferralCode("GARBAGE1")).thenReturn(Optional.empty());

        assertEquals(ReferralService.RewardOutcome.REFERRER_NOT_FOUND, service.rewardFor("invitee@b.com"));
        verify(readerRepository, never()).addCredits(anyLong(), anyInt());
    }

    /**
     * 自我邀請不得發獎。
     *
     * <p>用自己的邀請碼訂閱自己的 email：冪等鍵（被邀者 email）雖然會擋掉
     * 第二次，但第一次仍會發獎，所以必須明確拒絕。</p>
     */
    @Test
    void selfInviteIsRejected() {
        givenReferralChain("host@b.com", "host@b.com", "CODE1234");

        assertEquals(ReferralService.RewardOutcome.SELF_INVITE, service.rewardFor("host@b.com"));
        verify(readerRepository, never()).addCredits(anyLong(), anyInt());
    }

    /** 自我邀請的大小寫變體同樣要擋掉 */
    @Test
    void selfInviteWithDifferentCaseIsRejected() {
        givenReferralChain("HOST@B.com", "host@b.com", "CODE1234");

        assertEquals(ReferralService.RewardOutcome.SELF_INVITE, service.rewardFor("HOST@B.com"));
    }

    /** 名單中查無此 email 時不發獎（理論上不會發生，因為事件只在 affected > 0 時發） */
    @Test
    void unknownInviteeMeansNoReward() {
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(anyString()))
            .thenReturn(Optional.empty());

        assertEquals(ReferralService.RewardOutcome.NO_REFERRER, service.rewardFor("ghost@b.com"));
    }

    /**
     * 獎勵設為 0 時不寫帳本、不佔用冪等鍵。
     *
     * <p>後台可把邀請獎勵調成 0（關閉此機制）。若此時仍寫一筆 delta=0 的帳本，
     * 冪等鍵就被佔用了——日後把獎勵調回 100，這位推薦人再也拿不到
     * 這位被邀者的獎勵。</p>
     */
    @Test
    void zeroRewardWritesNoLedgerEntry() {
        when(creditPolicy.referralReward()).thenReturn(0);
        givenReferralChain("invitee@b.com", "host@b.com", "CODE1234");

        service.rewardFor("invitee@b.com");

        verify(creditTxnRepository, never()).saveAndFlush(any(CreditTxn.class));
        verify(creditTxnRepository, never()).save(any(CreditTxn.class));
        verify(readerRepository, never()).addCredits(anyLong(), anyInt());
    }

    /**
     * 加點的受影響筆數為 0 時必須拋例外，不可回報成功。
     *
     * <p>addCredits 回 0 代表推薦人那一列不存在。若靜默回 REWARDED，帳本會出現
     * 一筆沒有對應餘額變動的紀錄，而 reader.credits 是 credit_txn 的物化總和
     * ——「餘額永遠可由帳本重算稽核」這個不變式就破了。拋出例外讓已寫入的
     * 帳本列隨交易一起回滾。</p>
     */
    @Test
    void failedCreditUpdateThrows() {
        givenReferralChain("invitee@b.com", "host@b.com", "CODE1234");
        when(readerRepository.addCredits(anyLong(), anyInt())).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.rewardFor("invitee@b.com"));
    }

    // ------------------------------------------------------------------
    // stats()：邀請人數是「帳本 REFERRAL 的 note」與「reader.referred_by」的聯集
    // ------------------------------------------------------------------

    /** 讓帳本回傳指定 note 的 REFERRAL 列（每筆 100 點） */
    private void givenLedgerNotes(String... notes) {
        java.util.List<CreditTxn> rows = new java.util.ArrayList<>();
        for (String note : notes) {
            rows.add(new CreditTxn(REFERRER_ID, 100, CreditTxn.REASON_REFERRAL, null, note));
        }
        when(creditTxnRepository.findByReaderIdAndReasonOrderByCreatedAtDesc(
                REFERRER_ID, CreditTxn.REASON_REFERRAL))
            .thenReturn(rows);
    }

    /** 讓 reader.referred_by 那一邊回傳指定的被邀者 email */
    private void givenReferredByEmails(String... emails) {
        when(readerRepository.findInviteeEmailsByReferredBy(REFERRER_ID))
            .thenReturn(java.util.List.of(emails));
    }

    /**
     * <b>最常見的情境：獎勵 &gt; 0、被邀者確認了訂閱但從未登入。</b>
     *
     * <p>電子報訂閱者絕大多數永遠不會來 {@code /r/} 登入（沒有任何流程會逼他登入），
     * 所以 {@code reader} 列根本不存在、{@code referred_by} 也就沒有值；但站方已經
     * 為那次邀請寫了帳本、付了點數。只數 {@code referred_by} 的實作在這裡會回 0，
     * 頁面會印「還沒有人透過你的連結完成訂閱」——而那是一句假話。</p>
     *
     * <p>破壞性驗證：把 {@code stats} 改回只數 {@code referred_by} → 本測試變紅。</p>
     */
    @Test
    void invitedCountIncludesLedgerInviteesWhoNeverLoggedIn() {
        givenLedgerNotes("a@b.com", "c@b.com");
        givenReferredByEmails();

        ReferralService.ReferralStats stats = service.stats(REFERRER_ID);

        assertEquals(2, stats.invitedCount(),
            "確認訂閱但未登入的被邀者沒被計入：站方已經付了點數，頁面卻說還沒有人");
        assertEquals(200, stats.earnedCredits(),
            "累計點數必須來自帳本（稽核來源），不可用人數 × 目前獎勵金額推算");
    }

    /**
     * <b>邀請獎勵被後台關成 0 時，人數仍必須成長。</b>
     *
     * <p>{@code rewardFor} 在 {@code reward <= 0} 時完全不寫帳本（刻意，避免占用
     * 冪等鍵），所以帳本這一邊是空的；但朋友確實完成了訂閱並登入，
     * {@code referred_by} 已寫入。只數帳本筆數的實作在這裡會回 0，
     * 邀請人的頁面顯示「還沒有人透過你的連結完成訂閱」——朋友說「我訂閱了」，
     * 頁面卻毫無反應。</p>
     *
     * <p>本測試<b>刻意不 stub {@code creditPolicy.referralReward()}</b>：
     * {@code stats()} 從不讀 {@link CreditPolicy}，那個 stub 對本測試毫無作用，
     * 只會讓人誤以為「獎勵為 0」這件事是由程式碼分支判斷的。真正代表「獎勵暫停」
     * 的事實是<b>帳本為空</b>，那才是這裡餵進去的前提。</p>
     *
     * <p>破壞性驗證：把 {@code stats} 改回只數帳本筆數 → 本測試變紅。</p>
     */
    @Test
    void invitedCountStillGrowsWhileRewardIsPaused() {
        givenLedgerNotes();
        givenReferredByEmails("a@b.com", "c@b.com");

        ReferralService.ReferralStats stats = service.stats(REFERRER_ID);

        assertEquals(2, stats.invitedCount(),
            "獎勵暫停期間人數沒有成長：邀請人會以為朋友的訂閱沒有生效");
        assertEquals(0, stats.earnedCredits(), "獎勵暫停期間不該有點數");
    }

    /**
     * 同一位被邀者同時出現在兩個來源時<b>只能算一人</b>。
     *
     * <p>這是「獎勵 &gt; 0 且被邀者也登入了」的情境，而且是設定正常時的常態：
     * 帳本有他的 note、{@code referred_by} 也指向邀請人。若聯集沒有去重
     * （例如寫成 {@code ledger.size() + referredBy.size()}），人數會膨脹成 2 倍，
     * 讀者用「累計點數 ÷ 每人獎勵」一算就發現對不上。</p>
     *
     * <p>破壞性驗證：把 {@code Set} 換成 {@code List}（或直接相加兩邊筆數）→
     * 本測試變紅（3 而非 2）。</p>
     */
    @Test
    void invitedCountDeduplicatesInviteePresentInBothSources() {
        givenLedgerNotes("a@b.com");
        givenReferredByEmails("a@b.com", "c@b.com");

        ReferralService.ReferralStats stats = service.stats(REFERRER_ID);

        assertEquals(2, stats.invitedCount(),
            "同一位被邀者同時出現在帳本與 referred_by 時被算了兩次：人數會膨脹");
        assertEquals(100, stats.earnedCredits());
    }

    /**
     * 去重必須不分大小寫。
     *
     * <p>帳本 note 是 {@code ReferralService.normalize} 之後的值，
     * {@code reader.email} 也是正規化為小寫的，理論上兩邊一致；但這條測試釘住
     * 「聯集用的鍵一定要再經過同一個正規化」這件事——若日後任一端出現大小寫變體
     * （例如後台手動補帳本列），沒有正規化的聯集會把同一個人算成兩人。</p>
     */
    @Test
    void invitedCountDeduplicationIsCaseInsensitive() {
        givenLedgerNotes("Friend@B.com");
        givenReferredByEmails("friend@b.com");

        assertEquals(1, service.stats(REFERRER_ID).invitedCount(),
            "同一個 email 的大小寫變體被算成兩人：聯集的鍵沒有經過 normalize");
    }

    /**
     * 帳本裡 note 為 NULL 的 REFERRAL 列不得湊出一個不存在的人。
     *
     * <p>{@code credit_txn.note} 可為 NULL 是 V7 的既有慣例，而 V9 的部分唯一索引
     * 也刻意不強制非空（PostgreSQL 的 UNIQUE 視 NULL 互異）。這種列雖然不該由
     * {@code rewardFor} 產生，但後台補點或歷史資料可能有；它沒有可去重的鍵，
     * 計入只會讓人數比實際多。</p>
     */
    @Test
    void ledgerRowsWithoutNoteDoNotCountAsInvitees() {
        givenLedgerNotes("a@b.com", null, "  ");
        givenReferredByEmails();

        assertEquals(1, service.stats(REFERRER_ID).invitedCount(),
            "note 為 NULL 或空白的帳本列被當成一位被邀者：人數會比實際多");
    }
}
