package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import world.springai.survey.audience.SurveyResponseRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ReaderAccountService 行為測試：首次建帳發初始贈點、既有帳戶不重複發、邀請碼不碰撞 */
class ReaderAccountServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-25T12:00:00+08:00");

    private ReaderRepository readerRepository;
    private CreditTxnRepository creditTxnRepository;
    private SurveyResponseRepository surveyResponseRepository;
    private CreditPolicy creditPolicy;
    private ReaderAccountService service;

    @BeforeEach
    void setUp() {
        readerRepository = mock(ReaderRepository.class);
        creditTxnRepository = mock(CreditTxnRepository.class);
        surveyResponseRepository = mock(SurveyResponseRepository.class);
        creditPolicy = mock(CreditPolicy.class);
        when(creditPolicy.signupGrant()).thenReturn(300);
        // save 回傳帶 id 的物件，模擬資料庫產生主鍵
        when(readerRepository.save(any(Reader.class))).thenAnswer(i -> {
            Reader r = i.getArgument(0);
            if (r.getId() == null) {
                r.setId(1L);
            }
            return r;
        });
        service = new ReaderAccountService(readerRepository, creditTxnRepository,
            surveyResponseRepository, creditPolicy);
    }

    /** 首次登入：建立帳戶、發初始贈點、餘額同步為贈點數 */
    @Test
    void firstLoginCreatesAccountWithSignupGrant() {
        when(readerRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());

        Reader reader = service.findOrCreate("user@example.com", NOW);

        assertEquals("user@example.com", reader.getEmail());
        assertEquals(300, reader.getCredits(), "餘額應同步為初始贈點");
        assertNotNull(reader.getReferralCode());

        ArgumentCaptor<CreditTxn> txn = ArgumentCaptor.forClass(CreditTxn.class);
        verify(creditTxnRepository).save(txn.capture());
        assertEquals(300, txn.getValue().getDelta());
        assertEquals(CreditTxn.REASON_SIGNUP_GRANT, txn.getValue().getReason());
    }

    /** 初始贈點金額取自可調參數，不寫死 */
    @Test
    void signupGrantAmountComesFromSettings() {
        when(creditPolicy.signupGrant()).thenReturn(150);
        when(readerRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());

        Reader reader = service.findOrCreate("user@example.com", NOW);

        assertEquals(150, reader.getCredits());
    }

    /**
     * 贈點設為 0（後台關閉贈點）時，帳戶要照建，但不可寫入 delta=0 的帳本紀錄。
     *
     * <p>credit_txn 是只增不改的帳本，讀者的「交易明細」頁會逐筆顯示它。
     * 一筆 delta=0 的紀錄對讀者毫無意義，卻會永久留在明細裡——帳本無法事後
     * 清理，所以只能在寫入前擋掉。</p>
     */
    @Test
    void zeroSignupGrantCreatesAccountWithoutLedgerEntry() {
        when(creditPolicy.signupGrant()).thenReturn(0);
        when(readerRepository.findByEmailIgnoreCase("nogrant@example.com")).thenReturn(Optional.empty());

        Reader reader = service.findOrCreate("nogrant@example.com", NOW);

        // 帳戶仍要建立，只是沒有點數
        assertEquals("nogrant@example.com", reader.getEmail());
        assertEquals(0, reader.getCredits());
        assertNotNull(reader.getReferralCode(), "邀請碼與贈點無關，仍必須產生");
        verify(creditTxnRepository, never()).save(any(CreditTxn.class));
    }

    /** 既有帳戶再次登入：不得重複發贈點 */
    @Test
    void existingAccountDoesNotReceiveGrantAgain() {
        Reader existing = new Reader("user@example.com", "OLDCODE1");
        existing.setId(7L);
        existing.setCredits(120);
        when(readerRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(existing));

        Reader reader = service.findOrCreate("user@example.com", NOW);

        assertEquals(7L, reader.getId());
        assertEquals(120, reader.getCredits(), "餘額不得被重設");
        verify(creditTxnRepository, never()).save(any(CreditTxn.class));
    }

    /** email 一律正規化為小寫後才查詢與建立 */
    @Test
    void emailIsNormalisedToLowerCase() {
        when(readerRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());

        Reader reader = service.findOrCreate("  User@EXAMPLE.com  ", NOW);

        assertEquals("user@example.com", reader.getEmail());
    }

    /** 邀請碼碰撞時要重新產生，不得直接寫入造成 UNIQUE 衝突 */
    @Test
    void referralCodeCollisionTriggersRetry() {
        when(readerRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        // 前兩次都說碼已存在，第三次才放行
        when(readerRepository.existsByReferralCode(anyString()))
            .thenReturn(true, true, false);

        Reader reader = service.findOrCreate("user@example.com", NOW);

        assertNotNull(reader.getReferralCode());
        verify(readerRepository, times(3)).existsByReferralCode(anyString());
    }

    /** 邀請碼不含容易看錯的字元（0/O、1/I/L），因為讀者會口頭或手抄傳播 */
    @Test
    void referralCodeAvoidsAmbiguousCharacters() {
        when(readerRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(readerRepository.existsByReferralCode(anyString())).thenReturn(false);

        for (int i = 0; i < 50; i++) {
            String code = service.findOrCreate("user" + i + "@example.com", NOW).getReferralCode();
            assertTrue(code.matches("[A-HJ-NP-Z2-9]{8}"),
                "邀請碼 " + code + " 含有易混淆字元或長度不符");
        }
    }

    /** 登入是高可靠互動訊號，必須更新名單中心的 last_engaged_at */
    @Test
    void loginTouchesEngagementTimestamp() {
        when(readerRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());

        service.findOrCreate("user@example.com", NOW);

        verify(surveyResponseRepository).touchEngagement("user@example.com", NOW);
    }

    /** 每次登入都要更新 last_login_at */
    @Test
    void loginUpdatesLastLoginAt() {
        Reader existing = new Reader("user@example.com", "OLDCODE1");
        existing.setId(7L);
        when(readerRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(existing));

        Reader reader = service.findOrCreate("user@example.com", NOW);

        assertEquals(NOW, reader.getLastLoginAt());
    }

    /**
     * 後台代為建帳（findOrCreateWithoutLogin）不得偽造登入與參與度訊號。
     *
     * <p>站方為從未登入的學員設 VIP 時，若沿用登入路徑，該讀者會立刻在後台顯示
     * 「剛剛登入過」，名單中心的參與度時間戳也被推到今天。參與度是名單評分與
     * 再行銷判斷的依據，被後台操作污染之後，站方再也分不出誰真的來過。</p>
     */
    @Test
    void findOrCreateWithoutLoginDoesNotTouchLoginOrEngagement() {
        when(readerRepository.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.empty());

        Reader reader = service.findOrCreateWithoutLogin("new@example.com", NOW);

        org.junit.jupiter.api.Assertions.assertNull(reader.getLastLoginAt(), "後台建帳不是一次登入");
        verify(surveyResponseRepository, never()).touchEngagement(anyString(), any());
    }

    /**
     * 後台代為建帳的其餘行為與登入建帳<b>完全相同</b>：初始贈點、帳本、邀請碼一個都不少。
     *
     * <p>這條是「不要繞過 findOrCreate 自己 new Reader」的保證：只要建帳入口唯一，
     * 後台建出來的帳戶就不會少發贈點或少了邀請碼。</p>
     */
    @Test
    void findOrCreateWithoutLoginStillGrantsSignupCredits() {
        when(readerRepository.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.empty());

        Reader reader = service.findOrCreateWithoutLogin("new@example.com", NOW);

        assertEquals(300, reader.getCredits(), "初始贈點不因後台建帳而缺席");
        assertNotNull(reader.getReferralCode());
        ArgumentCaptor<CreditTxn> txn = ArgumentCaptor.forClass(CreditTxn.class);
        verify(creditTxnRepository).save(txn.capture());
        assertEquals(CreditTxn.REASON_SIGNUP_GRANT, txn.getValue().getReason());
    }

    /** 後台對既有讀者建帳：既不重複發贈點，也不動最後登入時間 */
    @Test
    void findOrCreateWithoutLoginLeavesExistingReaderUntouched() {
        Reader existing = new Reader("user@example.com", "OLDCODE1");
        existing.setId(7L);
        existing.setCredits(120);
        existing.setLastLoginAt(NOW.minusDays(30));
        when(readerRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(existing));

        Reader reader = service.findOrCreateWithoutLogin("user@example.com", NOW);

        assertEquals(NOW.minusDays(30), reader.getLastLoginAt(), "既有的最後登入時間不得被覆寫");
        assertEquals(120, reader.getCredits());
        verify(creditTxnRepository, never()).save(any(CreditTxn.class));
        verify(surveyResponseRepository, never()).touchEngagement(anyString(), any());
    }

    /** 一般登入路徑的行為不得因為新增後台路徑而改變 */
    @Test
    void loginPathStillTouchesLoginAndEngagement() {
        when(readerRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());

        Reader reader = service.findOrCreate("user@example.com", NOW);

        assertEquals(NOW, reader.getLastLoginAt());
        verify(surveyResponseRepository).touchEngagement("user@example.com", NOW);
    }

    /** 首次登入時應把名單中心的推薦碼轉成 reader.referred_by */
    @Test
    void firstLoginRecordsReferrer() {
        when(readerRepository.findByEmailIgnoreCase("newbie@example.com")).thenReturn(Optional.empty());

        // 名單中心有這筆訂閱，且帶著推薦碼
        world.springai.survey.audience.SurveyResponse row =
            new world.springai.survey.audience.SurveyResponse();
        row.setEmail("newbie@example.com");
        row.setAnswers(java.util.Map.of("_ref", "HOSTCODE"));
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc("newbie@example.com"))
            .thenReturn(Optional.of(row));

        // 推薦碼對應的推薦人
        Reader referrer = new Reader("host@example.com", "HOSTCODE");
        referrer.setId(7L);
        when(readerRepository.findByReferralCode("HOSTCODE")).thenReturn(Optional.of(referrer));

        service.findOrCreate("newbie@example.com", NOW);

        ArgumentCaptor<Reader> saved = ArgumentCaptor.forClass(Reader.class);
        verify(readerRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertEquals(7L, saved.getValue().getReferredBy());
    }

    /**
     * 沒有推薦碼時 referred_by 必須為 null。
     *
     * <p>絕大多數訂閱者走這條路徑。寫入 0 或空值會讓「有沒有推薦人」的判斷
     * 在日後每個使用點都要多處理一種情況。</p>
     */
    @Test
    void firstLoginWithoutReferrerLeavesReferredByNull() {
        when(readerRepository.findByEmailIgnoreCase("plain@example.com")).thenReturn(Optional.empty());
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(anyString()))
            .thenReturn(Optional.empty());

        Reader reader = service.findOrCreate("plain@example.com", NOW);

        org.junit.jupiter.api.Assertions.assertNull(reader.getReferredBy());
    }

    /** 自我邀請不記錄 referred_by（與 ReferralService 的判定保持一致） */
    @Test
    void selfReferralIsNotRecorded() {
        when(readerRepository.findByEmailIgnoreCase("host@example.com")).thenReturn(Optional.empty());

        world.springai.survey.audience.SurveyResponse row =
            new world.springai.survey.audience.SurveyResponse();
        row.setEmail("host@example.com");
        row.setAnswers(java.util.Map.of("_ref", "HOSTCODE"));
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(anyString()))
            .thenReturn(Optional.of(row));

        Reader self = new Reader("host@example.com", "HOSTCODE");
        self.setId(7L);
        when(readerRepository.findByReferralCode("HOSTCODE")).thenReturn(Optional.of(self));

        Reader reader = service.findOrCreate("host@example.com", NOW);

        org.junit.jupiter.api.Assertions.assertNull(reader.getReferredBy());
    }
}
