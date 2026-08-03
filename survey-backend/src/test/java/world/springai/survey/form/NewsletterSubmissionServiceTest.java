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
 */
class NewsletterSubmissionServiceTest {

    private final FormSchemaService formSchemaService = mock(FormSchemaService.class);
    private final AudiencePlatformService audience = mock(AudiencePlatformService.class);
    private final PromoRecipientTokenService tokenService = mock(PromoRecipientTokenService.class);
    private final ReaderSessionService sessionService = mock(ReaderSessionService.class);
    private final ReaderRepository readerRepository = mock(ReaderRepository.class);
    private final CreditTxnRepository creditTxnRepository = mock(CreditTxnRepository.class);
    private final CreditPolicy creditPolicy = mock(CreditPolicy.class);
    /** 刻意不注入進 service：本測試要證明服務完全不依賴 legacy repository。 */
    private final SurveyResponseRepository legacyRepository = mock(SurveyResponseRepository.class);

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
        verifyNoInteractions(legacyRepository);
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
        verifyNoInteractions(legacyRepository);
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
}
