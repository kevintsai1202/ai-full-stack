package world.springai.survey.newsletter;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import world.springai.survey.ReaderSiteLinks;
import world.springai.survey.audience.RecipientService;
import world.springai.survey.audience.SubscriptionLinkBuilder;
import world.springai.survey.form.FormSchemaService;
import world.springai.survey.form.FormSchemaService.EmailVoteQuestion;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.EmailTemplate;
import world.springai.survey.mail.MailQuotaService;
import world.springai.survey.mail.MailSender;
import world.springai.survey.promo.PromoPlacementService;
import world.springai.survey.promo.PromoRecipientTokenService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Task 9 渲染接線：驗證 Task 8 的 {@link SurveyBlockRenderer} 真的在寄送信、
 * 測試信、CID 佔位符替換三處接上（消費端整合測試，不重複展開本身的 HTML
 * 細節——那已由 {@link SurveyBlockRendererTest} 覆蓋）。
 *
 * <p>ReaderPageController 的 {@code expandForWeb} 接線改由
 * {@code ReaderPageControllerTest#articlePageExpandsSurveyBlockWithCampaignId}
 * 驗證（controller 層才能確認「展開結果真的進入 HTTP 回應」），本檔額外補一則
 * service 層測試確認同一個 {@link SurveyBlockRenderer} 實例可直接呼叫。</p>
 */
class CampaignSurveyWiringTest {

    private static final String FORM_KEY = "reader-poll";
    private static final String MARKER = "<!--survey:" + FORM_KEY + "-->";
    private static final String READER_BASE_URL = "https://reader.example.com";

    private final MailSender mailSender = mock(MailSender.class);
    private final RecipientService recipientService = mock(RecipientService.class);
    private final CampaignRepository campaignRepository = mock(CampaignRepository.class);
    private final EmailLogRepository emailLogRepository = mock(EmailLogRepository.class);
    private final MarkdownRenderer markdownRenderer = new MarkdownRenderer();
    private final EmailTemplate emailTemplate = new EmailTemplate();
    private final SubscriptionLinkBuilder linkBuilder = mock(SubscriptionLinkBuilder.class);
    private final MailQuotaService mailQuotaService = mock(MailQuotaService.class);
    private final ContentSplitter contentSplitter = new ContentSplitter();
    private final ReaderSiteLinks readerSiteLinks = new ReaderSiteLinks(READER_BASE_URL);
    private final PromoPlacementService promoPlacementService = mock(PromoPlacementService.class);
    private final PromoRecipientTokenService promoTokenService = mock(PromoRecipientTokenService.class);
    // 真實 SurveyBlockRenderer + mock FormSchemaService：只關心「有沒有接上」，
    // 展開的 HTML 細節已由 SurveyBlockRendererTest 覆蓋，這裡不重複斷言卡片樣式。
    private final FormSchemaService formSchemaService = mock(FormSchemaService.class);
    private final SurveyBlockRenderer surveyBlockRenderer = new SurveyBlockRenderer(formSchemaService);
    private final MailBodyRenderer mailBodyRenderer = new MailBodyRenderer(
        contentSplitter, markdownRenderer, readerSiteLinks, surveyBlockRenderer, READER_BASE_URL);

    private final CampaignService svc = new CampaignService(
        mailSender, recipientService, campaignRepository, emailLogRepository,
        markdownRenderer, emailTemplate, linkBuilder, mailQuotaService, contentSplitter,
        readerSiteLinks, mailBodyRenderer, promoPlacementService, promoTokenService,
        surveyBlockRenderer);

    {
        // 額度與工商 token 皆給充足／非 null 的預設值，理由同 CampaignServiceTest：
        // 未 stub 會讓既有斷言全部改觀（額度誤判不足／replace 對 null 拋 NPE）。
        when(mailQuotaService.current()).thenReturn(quota());
        when(promoTokenService.issue(anyString())).thenReturn("DEFAULT.TOKEN");
    }

    private MailQuotaService.Quota quota() {
        return new MailQuotaService.Quota("zeabur", "healthy",
            999999999L, 0, 999999999L, 50000, 0, 10050,
            10050, 500, 50, 10000, 500, false, null, null);
    }

    /** 準備一則「可嵌入」的信中一鍵題，供 formSchemaService mock 回傳 */
    private void givenEmbeddable() {
        when(formSchemaService.emailVoteQuestion(FORM_KEY)).thenReturn(
            Optional.of(new EmailVoteQuestion(FORM_KEY, "滿意度調查", "rating", "你覺得如何？",
                List.of("很有幫助", "普通"))));
    }

    /** 準備一則「不可嵌入」的問卷（未發布或未設信中一鍵題） */
    private void givenNotEmbeddable() {
        when(formSchemaService.emailVoteQuestion(FORM_KEY)).thenReturn(Optional.empty());
    }

    private String markdownWithSurvey() {
        return "電子報內容\n\n" + MARKER + "\n\n更多內容";
    }

    /**
     * 情境 1：{@code CampaignService.mailBodyHtml}（現已委派給 {@link MailBodyRenderer#html}）
     * 的產物含投票卡與兩個佔位符（{@code __SURVEY_CID__} 與 {@code __PROMO_RT__}）。
     */
    @Test
    void mailBodyHtml產物含投票卡與兩個佔位符() {
        givenEmbeddable();

        String html = mailBodyRenderer.html(markdownWithSurvey(), null);

        assertTrue(html.contains(SurveyBlockRenderer.CID_PLACEHOLDER),
            "信件通道展開後應留下待替換的 CID 佔位符：" + html);
        assertTrue(html.contains(PromoRecipientTokenService.PLACEHOLDER),
            "信件通道選項連結應帶收件人 token 佔位符：" + html);
        assertTrue(html.contains(READER_BASE_URL + "/s/v/" + FORM_KEY),
            "應渲染出投票卡的選項連結：" + html);
        assertFalse(html.contains("<!--survey:"), "HTML 註解標記不應殘留：" + html);
    }

    /**
     * 情境 2：send 流程（立即模式）後，最終寄出的信體不再含 {@code __SURVEY_CID__}，
     * 而是被真正的 campaignId 取代。
     */
    @Test
    void send流程後最終信體以campaignId取代CID佔位符() {
        givenEmbeddable();
        when(recipientService.recipients(null, null)).thenReturn(List.of("a@x.com"));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> {
            Campaign c = invocation.getArgument(0);
            if (c.getId() == null) {
                ReflectionTestUtils.setField(c, "id", 55L);
            }
            return c;
        });
        when(mailSender.sendBatch(anyList())).thenReturn("job-1");
        when(linkBuilder.unsubscribeLink("a@x.com")).thenReturn("https://x/unsubscribe?u=a");

        svc.send("主旨", markdownWithSurvey(), null, null, "now", null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MailSender.Email>> captor = ArgumentCaptor.forClass(List.class);
        verify(mailSender).sendBatch(captor.capture());
        String html = captor.getValue().getFirst().html();
        assertFalse(html.contains(SurveyBlockRenderer.CID_PLACEHOLDER),
            "寄出的信體不得殘留 CID 佔位符：" + html);
        assertTrue(html.contains("c=55"), "選項連結應帶真正的 campaignId：" + html);
    }

    /**
     * 情境 3：測試信路徑（sendTest → renderFor(body, to, null, count)）的 CID
     * 佔位符固定替換為 "0"，對應「campaign 不存在不落票」的既有保證。
     */
    @Test
    void 測試信CID替換為0() {
        givenEmbeddable();
        when(recipientService.subscriberCount()).thenReturn(100L);
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("ok");

        svc.sendTest("測試主旨", markdownWithSurvey(), "test@x.com");

        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(eq("test@x.com"), anyString(), htmlCaptor.capture());
        String html = htmlCaptor.getValue();
        assertFalse(html.contains(SurveyBlockRenderer.CID_PLACEHOLDER),
            "測試信不得殘留 CID 佔位符：" + html);
        assertTrue(html.contains("c=0"), "測試信的問卷連結應帶 c=0：" + html);
    }

    /**
     * 情境 4：內文含壞標記（問卷未發布或未設信中一鍵題）時，send 必須在建立
     * Campaign 之前就被擋下，回應為 4xx，且資料庫不得留下殘留列、不得寄出任何信。
     */
    @Test
    void 壞標記擋下send且不建campaign不寄信() {
        givenNotEmbeddable();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> svc.send("主旨", markdownWithSurvey(), null, null, "now", null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason() != null && ex.getReason().contains(FORM_KEY),
            "錯誤訊息應指名是哪個問卷標記無法嵌入：" + ex.getReason());
        verify(campaignRepository, never()).save(any());
        verify(mailSender, never()).sendBatch(anyList());
    }

    /**
     * 情境 5（web 展開接線的 service 層佐證）：CampaignService 與
     * ReaderPageController 共用同一顆 {@link SurveyBlockRenderer} Spring 單例，
     * 該實例的 {@code expandForWeb} 對相同輸入必產生一致結果——HTTP 層的實際接線
     * 由 {@code ReaderPageControllerTest.articlePageExpandsSurveyBlockWithCampaignId}
     * 驗證（那裡才能確認展開結果真的進入回應本文，而不只是被呼叫過）。
     */
    @Test
    void expandForWeb以相同SurveyBlockRenderer運作於campaignId() {
        givenEmbeddable();

        String html = surveyBlockRenderer.expandForWeb(markdownWithSurvey(), 9L);

        assertTrue(html.contains("c=9"), "讀者頁通道應帶入真正的 campaignId：" + html);
        assertFalse(html.contains(SurveyBlockRenderer.CID_PLACEHOLDER), html);
        assertFalse(html.contains("rt="), "讀者頁通道不應帶 rt（改由 session 歸戶）：" + html);
    }
}
