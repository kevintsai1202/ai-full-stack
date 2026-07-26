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

    /**
     * 邀請<b>人數</b>必須來自 {@code reader.referred_by}，<b>點數</b>來自帳本。
     *
     * <p>兩個數字刻意不同源，所以這條測試把兩邊餵成<b>不一樣</b>的值：
     * {@code referred_by} 有 3 人、帳本只有 2 筆各 100 點。若實作把人數改回數
     * 帳本筆數（{@code rewards.size()}），人數會變成 2 而變紅。</p>
     */
    @Test
    void invitedCountComesFromReferredByNotFromLedger() {
        when(readerRepository.countByReferredBy(REFERRER_ID)).thenReturn(3L);
        when(creditTxnRepository.findByReaderIdAndReasonOrderByCreatedAtDesc(
                REFERRER_ID, CreditTxn.REASON_REFERRAL))
            .thenReturn(java.util.List.of(
                new CreditTxn(REFERRER_ID, 100, CreditTxn.REASON_REFERRAL, null, "a@b.com"),
                new CreditTxn(REFERRER_ID, 100, CreditTxn.REASON_REFERRAL, null, "c@b.com")));

        ReferralService.ReferralStats stats = service.stats(REFERRER_ID);

        assertEquals(3, stats.invitedCount(),
            "邀請人數不是來自 reader.referred_by：獎勵關閉期間人數就不會成長");
        assertEquals(200, stats.earnedCredits(),
            "累計點數必須來自帳本（稽核來源），不可用人數 × 目前獎勵金額推算");
    }

    /**
     * <b>邀請獎勵被後台關成 0 時，人數仍必須成長。</b>
     *
     * <p>這是本次修正的核心情境：{@code rewardFor} 在 {@code reward <= 0} 時
     * 完全不寫帳本（刻意，避免占用冪等鍵），所以帳本是空的；但朋友確實完成了
     * 訂閱並登入，{@code referred_by} 已寫入。舊實作（數帳本筆數）在這裡會回 0，
     * 邀請人的頁面顯示「還沒有人透過你的連結完成訂閱」——朋友說「我訂閱了」，
     * 頁面卻毫無反應。</p>
     */
    @Test
    void invitedCountStillGrowsWhileRewardIsPaused() {
        when(creditPolicy.referralReward()).thenReturn(0);
        when(readerRepository.countByReferredBy(REFERRER_ID)).thenReturn(2L);
        when(creditTxnRepository.findByReaderIdAndReasonOrderByCreatedAtDesc(
                REFERRER_ID, CreditTxn.REASON_REFERRAL))
            .thenReturn(java.util.List.of());

        ReferralService.ReferralStats stats = service.stats(REFERRER_ID);

        assertEquals(2, stats.invitedCount(),
            "獎勵暫停期間人數沒有成長：邀請人會以為朋友的訂閱沒有生效");
        assertEquals(0, stats.earnedCredits(), "獎勵暫停期間不該有點數");
    }
}
