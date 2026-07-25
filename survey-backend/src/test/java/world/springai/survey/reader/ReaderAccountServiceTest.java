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
}
