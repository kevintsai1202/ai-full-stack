package world.springai.survey.form;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.audience.AudiencePlatformService;
import world.springai.survey.audience.SurveyResponseRepository;
import world.springai.survey.form.FormSchemaService.FormDefinition;
import world.springai.survey.form.NewsletterSubmissionService.SubmitRequest;
import world.springai.survey.form.NewsletterSubmissionService.SubmitResult;
import world.springai.survey.promo.PromoRecipientTokenService;
import world.springai.survey.reader.CreditPolicy;
import world.springai.survey.reader.CreditTxn;
import world.springai.survey.reader.CreditTxnRepository;
import world.springai.survey.reader.Reader;
import world.springai.survey.reader.ReaderRepository;
import world.springai.survey.reader.ReaderSessionService;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link NewsletterSubmissionService} 純 Mockito 單元測試（全 mock、不需 DB），
 * 照 {@code SurveyVoteServiceTest} 模式。核心是 spec §3.2 的通道感知衝突解法：
 * 電子報通道提交必須寫 audience record／facts，但絕對不能觸發訂閱漏斗的
 * consent 副作用，也不能碰 legacy {@code survey_response}。
 *
 * <p><b>legacy repository 的守衛改用結構斷言，不再用「注入但不呼叫」的空 mock</b>：
 * {@link NewsletterSubmissionService} 的建構子根本沒有 {@code SurveyResponseRepository}
 * 參數，所以先前 {@code verifyNoInteractions(legacyRepository)} 驗證的是一個
 * <b>從未注入服務的 mock</b>——不管實作內容是什麼，那個 mock 永遠沒有互動，
 * 斷言恆真、測不出任何東西（審查發現的空洞守衛）。{@link #legacy不注入建構子_結構守住不會被悄悄接上}
 * 改用反射檢查建構子參數型別，未來有人真的把 legacy repository 加回建構子時
 * 才會讓這條測試失敗。</p>
 */
class NewsletterSubmissionServiceTest {

    private final FormSchemaService formSchemaService = mock(FormSchemaService.class);
    private final AudiencePlatformService audience = mock(AudiencePlatformService.class);
    private final PromoRecipientTokenService tokenService = mock(PromoRecipientTokenService.class);
    private final ReaderSessionService sessionService = mock(ReaderSessionService.class);
    private final ReaderRepository readerRepository = mock(ReaderRepository.class);
    private final CreditTxnRepository creditTxnRepository = mock(CreditTxnRepository.class);
    private final CreditPolicy creditPolicy = mock(CreditPolicy.class);

    private NewsletterSubmissionService service;

    @BeforeEach
    void setUp() {
        service = new NewsletterSubmissionService(
            formSchemaService, audience, tokenService, sessionService,
            readerRepository, creditTxnRepository, creditPolicy);
        // 預設：問卷存在、驗證通過（mock 對 void 方法預設不拋例外）
        when(formSchemaService.getDefinition("news-form", null)).thenReturn(form());
        when(audience.mergePerson(any(), any(), any()))
            .thenReturn(new AudiencePlatformService.PersonResult(1L, true, "a@b.c"));
        when(audience.upsertRecord(anyLong(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new AudiencePlatformService.RecordResult(100L, "CREATED", "hash"));
        when(creditPolicy.surveyReward()).thenReturn(20);
        // 預設加點成功（受影響 1 列）；「讀者列已不存在」的例外路徑由專屬測試覆寫此 stub
        when(readerRepository.addCredits(any(), anyInt())).thenReturn(1);
    }

    /**
     * 結構守衛（取代原本空洞的 {@code verifyNoInteractions(legacyRepository)}）：
     * {@link NewsletterSubmissionService} 建構子參數不得含 {@code SurveyResponseRepository}。
     * 這樣「不碰 legacy」不是靠自我約束，而是編譯期就沒有這個依賴可用——
     * 未來有人想在本服務裡寫回 legacy 表，第一步就要先改建構子，這條測試會先爆。
     */
    @Test
    void legacy不注入建構子_結構守住不會被悄悄接上() {
        Constructor<?>[] constructors = NewsletterSubmissionService.class.getDeclaredConstructors();
        for (Constructor<?> constructor : constructors) {
            for (Class<?> paramType : constructor.getParameterTypes()) {
                assertFalse(SurveyResponseRepository.class.isAssignableFrom(paramType),
                    "NewsletterSubmissionService 建構子不應注入 SurveyResponseRepository（spec §3.2）");
            }
        }
    }

    /** 測試用問卷版本：不需要欄位即可驗證身分解析與衝突解法路徑。 */
    private FormDefinition form() {
        return new FormDefinition(1L, "news-form", 1, "電子報問卷", "PUBLISHED", false, null, List.of());
    }

    @Test
    void rt歸戶提交_寫入newsletter_survey且絕不碰訂閱漏斗副作用() {
        when(tokenService.verify("tok")).thenReturn(Optional.of("a@b.c"));
        when(readerRepository.findByEmailIgnoreCase("a@b.c")).thenReturn(Optional.empty());

        SubmitRequest request = new SubmitRequest(Map.of(), 9L, "tok");
        SubmitResult result = service.submit("news-form", request, null);

        assertFalse(result.rewarded());
        verify(audience).upsertRecord(
            eq(1L), eq("newsletter_survey"), eq("survey_submission"), eq("news-form@1"),
            any(), any(),
            argThat(raw -> Long.valueOf(9L).equals(raw.get("campaignId"))
                && NewsletterSubmissionService.CHANNEL_EMAIL.equals(raw.get("channel"))),
            any());
        verify(audience, never()).appendConsent(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void email對應讀者且首次填答_加點並寫入SURVEY_REWARD帳本() {
        when(tokenService.verify("tok")).thenReturn(Optional.of("a@b.c"));
        Reader reader = new Reader("a@b.c", "REF1");
        reader.setId(5L);
        when(readerRepository.findByEmailIgnoreCase("a@b.c")).thenReturn(Optional.of(reader));
        when(creditTxnRepository.existsByReaderIdAndSurveyFormKeyAndReason(
            5L, "news-form", CreditTxn.REASON_SURVEY_REWARD)).thenReturn(false);

        SubmitResult result = service.submit(
            "news-form", new SubmitRequest(Map.of(), 9L, "tok"), null);

        assertTrue(result.rewarded());
        assertEquals(20, result.rewardCredits());
        verify(readerRepository).addCredits(5L, 20);
        verify(creditTxnRepository).save(argThat(txn ->
            txn.getReaderId().equals(5L)
                && txn.getDelta() == 20
                && CreditTxn.REASON_SURVEY_REWARD.equals(txn.getReason())
                && "news-form".equals(txn.getSurveyFormKey())));
    }

    @Test
    void email對應讀者但已發過點_不重發() {
        when(tokenService.verify("tok")).thenReturn(Optional.of("a@b.c"));
        Reader reader = new Reader("a@b.c", "REF1");
        reader.setId(5L);
        when(readerRepository.findByEmailIgnoreCase("a@b.c")).thenReturn(Optional.of(reader));
        when(creditTxnRepository.existsByReaderIdAndSurveyFormKeyAndReason(
            5L, "news-form", CreditTxn.REASON_SURVEY_REWARD)).thenReturn(true);

        SubmitResult result = service.submit(
            "news-form", new SubmitRequest(Map.of(), 9L, "tok"), null);

        assertFalse(result.rewarded());
        assertEquals(0, result.rewardCredits());
        verify(readerRepository, never()).addCredits(any(), anyInt());
        verify(creditTxnRepository, never()).save(any());
    }

    @Test
    void email非讀者_照收答案但不發點且提示含訂閱字樣() {
        when(tokenService.verify("tok")).thenReturn(Optional.of("stranger@b.c"));
        when(readerRepository.findByEmailIgnoreCase("stranger@b.c")).thenReturn(Optional.empty());

        SubmitResult result = service.submit(
            "news-form", new SubmitRequest(Map.of(), 9L, "tok"), null);

        assertFalse(result.rewarded());
        assertEquals(0, result.rewardCredits());
        assertTrue(result.rewardHint().contains("訂閱"));
        verify(audience).upsertRecord(
            anyLong(), any(), any(), any(), any(), any(), any(), any());
        verify(readerRepository, never()).addCredits(any(), anyInt());
    }

    @Test
    void 無rt無有效session_拋401() {
        when(tokenService.verify(any())).thenReturn(Optional.empty());
        when(sessionService.readReaderId(any(), any())).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> service.submit("news-form", new SubmitRequest(Map.of(), null, null), null));
        assertEquals(401, exception.getStatusCode().value());
        verifyNoInteractions(audience);
    }

    @Test
    void session歸戶_readerId有效時直接取reader的email() {
        when(tokenService.verify(any())).thenReturn(Optional.empty());
        when(sessionService.readReaderId(eq("cookie"), any())).thenReturn(Optional.of(5L));
        Reader reader = new Reader("session@b.c", "REF2");
        reader.setId(5L);
        when(readerRepository.findById(5L)).thenReturn(Optional.of(reader));
        when(readerRepository.findByEmailIgnoreCase("session@b.c")).thenReturn(Optional.of(reader));
        when(creditTxnRepository.existsByReaderIdAndSurveyFormKeyAndReason(
            5L, "news-form", CreditTxn.REASON_SURVEY_REWARD)).thenReturn(false);

        SubmitResult result = service.submit(
            "news-form", new SubmitRequest(Map.of(), null, null), "cookie");

        assertTrue(result.rewarded());
        verify(audience).mergePerson(eq("session@b.c"), any(), any());
        verify(audience).upsertRecord(
            anyLong(), any(), any(), any(), any(), any(),
            argThat(raw -> NewsletterSubmissionService.CHANNEL_READER.equals(raw.get("channel"))),
            any());
    }

    @Test
    void 答案驗證失敗時原例外直接透傳() {
        when(tokenService.verify("tok")).thenReturn(Optional.of("a@b.c"));
        org.springframework.web.server.ResponseStatusException badRequest =
            new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "缺必填欄位");
        org.mockito.Mockito.doThrow(badRequest).when(formSchemaService).validateAnswers(any(), any());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> service.submit("news-form", new SubmitRequest(Map.of(), 9L, "tok"), null));
        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(audience);
    }

    /**
     * 對照 {@code FormSchemaService.submit}（L348-353）：{@code mergePerson} 拋
     * {@link AudiencePlatformService.SuppressedEmailException}（email 在停止處理名單上）
     * 時必須轉 409，不可讓例外裸露成 500。
     */
    @Test
    void email在停止處理名單_mergePerson拋SuppressedEmailException時轉409() {
        when(tokenService.verify("tok")).thenReturn(Optional.of("blocked@b.c"));
        when(audience.mergePerson(any(), any(), any()))
            .thenThrow(new AudiencePlatformService.SuppressedEmailException("已停止處理"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> service.submit("news-form", new SubmitRequest(Map.of(), 9L, "tok"), null));
        assertEquals(409, exception.getStatusCode().value());
        verify(audience, never()).upsertRecord(
            anyLong(), any(), any(), any(), any(), any(), any(), any());
    }

    /**
     * 條件式 {@code addCredits} 回 0 列代表讀者列已不存在（帳本已寫入，若靜默放行
     * {@code reader.credits} 與帳本總和就會對不起來）；此時必須拋例外讓交易回滾，
     * 比照 {@code ReferralGrowthService.addCredit} 的既有慣例。
     */
    @Test
    void addCredits回0列時拋IllegalStateException不可靜默放行() {
        when(tokenService.verify("tok")).thenReturn(Optional.of("a@b.c"));
        Reader reader = new Reader("a@b.c", "REF1");
        reader.setId(5L);
        when(readerRepository.findByEmailIgnoreCase("a@b.c")).thenReturn(Optional.of(reader));
        when(creditTxnRepository.existsByReaderIdAndSurveyFormKeyAndReason(
            5L, "news-form", CreditTxn.REASON_SURVEY_REWARD)).thenReturn(false);
        when(readerRepository.addCredits(5L, 20)).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.submit(
            "news-form", new SubmitRequest(Map.of(), 9L, "tok"), null));
    }
}
