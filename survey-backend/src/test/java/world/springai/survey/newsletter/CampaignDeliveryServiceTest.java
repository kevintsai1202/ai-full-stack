package world.springai.survey.newsletter;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import world.springai.survey.ReaderSiteLinks;
import world.springai.survey.audience.RecipientService;
import world.springai.survey.audience.SubscriptionLinkBuilder;
import world.springai.survey.form.FormSchemaService;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.EmailTemplate;
import world.springai.survey.mail.MailQuotaService;
import world.springai.survey.mail.MailSender;
import world.springai.survey.promo.PromoRecipientTokenService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 同篇電子報補寄資格與永久防重複規則測試。 */
class CampaignDeliveryServiceTest {

    /**
     * 本檔所有 markdown 皆不含 {@code <!--survey:...-->} 標記，用真實
     * {@link SurveyBlockRenderer} 搭配 mock {@link FormSchemaService} 即為安全的
     * no-op（regex 掃不到任何標記，FormSchemaService 完全不會被呼叫），
     * 避免用 mock 版 SurveyBlockRenderer 卻忘記 stub 而讓 expandForEmail 回傳 null。
     */
    private static SurveyBlockRenderer surveyBlockRenderer() {
        return new SurveyBlockRenderer(mock(FormSchemaService.class),
            mock(world.springai.survey.SurveyVoteRewardView.class));
    }

    /** 已寄出者不可再選；新加入與失敗者會出現在「尚未寄送」篩選。 */
    @Test
    void eligibleFilterIncludesNewAndRetryableButExcludesSent() {
        CampaignRepository campaignRepository = mock(CampaignRepository.class);
        CampaignBatchRepository batchRepository = mock(CampaignBatchRepository.class);
        CampaignRecipientRepository recipientRepository = mock(CampaignRecipientRepository.class);
        RecipientService recipientService = mock(RecipientService.class);
        Campaign campaign = new Campaign(
            "主旨", "內容", "<p>內容</p>", null, null, "now", null, 3, "sent");
        ReflectionTestUtils.setField(campaign, "id", 42L);
        CampaignRecipient sent = recipient("sent@example.com", "SENT");
        CampaignRecipient failed = recipient("failed@example.com", "FAILED");

        when(campaignRepository.findById(42L)).thenReturn(Optional.of(campaign));
        when(batchRepository.existsByCampaignId(42L)).thenReturn(true);
        when(recipientService.recipients(null, null, null, null))
            .thenReturn(List.of("sent@example.com", "failed@example.com", "new@example.com"));
        when(recipientRepository.findByCampaignId(42L)).thenReturn(List.of(sent, failed));

        PromoRecipientTokenService promoTokenService = mock(PromoRecipientTokenService.class);
        when(promoTokenService.issue(anyString())).thenReturn("DEFAULT.TOKEN");
        CampaignDeliveryService service = new CampaignDeliveryService(
            campaignRepository,
            batchRepository,
            recipientRepository,
            mock(EmailLogRepository.class),
            recipientService,
            mock(MailSender.class),
            mock(MailQuotaService.class),
            new EmailTemplate(),
            mock(SubscriptionLinkBuilder.class),
            mock(ReaderSiteLinks.class),
            mock(JdbcTemplate.class),
            new MailBodyRenderer(new ContentSplitter(), new MarkdownRenderer(),
                mock(ReaderSiteLinks.class), surveyBlockRenderer(), "https://reader.example.com"),
            promoTokenService);

        CampaignDeliveryService.RecipientPage page =
            service.recipients(42L, "ELIGIBLE", null, 0, 50);

        assertEquals(List.of("new@example.com", "failed@example.com"),
            page.items().stream().map(CampaignDeliveryService.RecipientView::email).toList());
        assertTrue(page.items().getFirst().newlyEligible());
        assertTrue(page.items().get(1).selectable());
        assertFalse(sentView(service).selectable());
    }

    /** 取得已寄出列並驗證永久不可勾選。 */
    private CampaignDeliveryService.RecipientView sentView(CampaignDeliveryService service) {
        return service.recipients(42L, "SENT", null, 0, 50).items().getFirst();
    }

    /**
     * 補寄<b>不得</b>把受限區寄出，即使 campaign 列裡存的 body_html 是折疊前的全文。
     *
     * <p>這條路徑原本直接重播 {@code campaign.getBodyHtml()}，完全不看 markdown，
     * 所以 CampaignService 的折疊修好之後它仍然是個洞——而且是最難發現的那種：
     * 資料庫裡早期建立的每一列都存著全文，補寄任何一篇舊電子報都會外洩。
     * 修法是「不信任存下來的 HTML，一律從 markdown 重新折疊」。</p>
     */
    @Test
    void resendFoldsPaywallEvenWhenStoredBodyHtmlHasFullText() {
        CampaignRepository campaignRepository = mock(CampaignRepository.class);
        CampaignBatchRepository batchRepository = mock(CampaignBatchRepository.class);
        CampaignRecipientRepository recipientRepository = mock(CampaignRecipientRepository.class);
        MailSender mailSender = mock(MailSender.class);
        MailQuotaService quotaService = mock(MailQuotaService.class);
        SubscriptionLinkBuilder linkBuilder = mock(SubscriptionLinkBuilder.class);

        // 折疊前建立的舊列：markdown 有付費牆，body_html 卻是整份全文
        Campaign campaign = new Campaign(
            "進階主題", "免費導讀\n\n<!--paywall-->\n\n祕密付費內容",
            "<p>免費導讀</p>\n<!--paywall-->\n<p>祕密付費內容</p>",
            null, null, "now", null, 1, "sent");
        ReflectionTestUtils.setField(campaign, "id", 55L);
        ReflectionTestUtils.setField(campaign, "tier", Campaign.TIER_BASIC);
        ReflectionTestUtils.setField(campaign, "slug", "legacy-post");

        when(campaignRepository.findById(55L)).thenReturn(Optional.of(campaign));
        when(batchRepository.existsByCampaignId(55L)).thenReturn(true);
        when(batchRepository.save(org.mockito.ArgumentMatchers.any(CampaignBatch.class)))
            .thenAnswer(invocation -> {
                CampaignBatch batch = invocation.getArgument(0);
                ReflectionTestUtils.setField(batch, "id", 900L);
                return batch;
            });
        when(recipientRepository.findByCampaignId(55L)).thenReturn(List.of());
        when(recipientRepository.reserveNew(
            org.mockito.ArgumentMatchers.eq(55L), org.mockito.ArgumentMatchers.eq(900L),
            org.mockito.ArgumentMatchers.eq("a@x.com"), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(quotaService.current()).thenReturn(ampleQuota());
        when(linkBuilder.unsubscribeLink("a@x.com")).thenReturn("https://x/unsubscribe?u=a");
        when(mailSender.sendBatch(org.mockito.ArgumentMatchers.anyList())).thenReturn("job-9");

        RecipientService recipientService = mock(RecipientService.class);
        when(recipientService.recipients(null, null, null, null))
            .thenReturn(List.of("a@x.com"));

        PromoRecipientTokenService promoTokenService = mock(PromoRecipientTokenService.class);
        when(promoTokenService.issue(anyString())).thenReturn("DEFAULT.TOKEN");
        CampaignDeliveryService service = new CampaignDeliveryService(
            campaignRepository, batchRepository, recipientRepository,
            mock(EmailLogRepository.class), recipientService, mailSender, quotaService,
            new EmailTemplate(), linkBuilder,
            new ReaderSiteLinks("https://reader.example.com"),
            mock(JdbcTemplate.class),
            new MailBodyRenderer(new ContentSplitter(), new MarkdownRenderer(),
                new ReaderSiteLinks("https://reader.example.com"),
                surveyBlockRenderer(), "https://reader.example.com"),
            promoTokenService);

        service.createBatch(55L, List.of("a@x.com"), "now", null);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<MailSender.Email>> captor =
            org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(mailSender).sendBatch(captor.capture());
        String html = captor.getValue().getFirst().html();
        assertTrue(html.contains("免費導讀"), html);
        assertFalse(html.contains("祕密付費內容"),
            "補寄不可把受限區寄出，即使 body_html 存的是全文：" + html);
        assertFalse(html.contains("<!--paywall-->"),
            "控制標記不可洩漏到信件原始碼中：" + html);
        assertTrue(html.contains("這是進階內容"), "折疊後必須附上解鎖卡片：" + html);
    }

    /**
     * C2 修正：補寄路徑原本沒有注入 {@code PromoRecipientTokenService}，
     * 導致補寄出去的信件內文原樣保留 {@code __PROMO_RT__} 佔位符，
     * 收件人點擊工商連結時驗簽必定失敗——與 {@code CampaignService.renderFor}
     * 同一個機制，第二條路徑（補寄）沒接上。
     *
     * <p>連帶驗證 C1：markdown 中的工商連結即使已經是絕對網址
     * （{@code https://example.org/promo/c/...}），置換佔位符後仍應保留該絕對網址、
     * 只替換 {@code rt} 參數值，不受連結格式影響。</p>
     */
    @Test
    void resendReplacesPromoPlaceholderWithAbsoluteUrlIntact() {
        CampaignRepository campaignRepository = mock(CampaignRepository.class);
        CampaignBatchRepository batchRepository = mock(CampaignBatchRepository.class);
        CampaignRecipientRepository recipientRepository = mock(CampaignRecipientRepository.class);
        MailSender mailSender = mock(MailSender.class);
        MailQuotaService quotaService = mock(MailQuotaService.class);
        SubscriptionLinkBuilder linkBuilder = mock(SubscriptionLinkBuilder.class);

        String markdown = "電子報內容\n\n<!--promo-->\n工商文案\n\n"
            + "[馬上看](https://example.org/promo/c/55?rt=__PROMO_RT__)\n<!--/promo-->\n";
        Campaign campaign = new Campaign(
            "主旨", markdown, "<p>折疊前全文（不應被信任）</p>",
            null, null, "now", null, 1, "sent");
        ReflectionTestUtils.setField(campaign, "id", 77L);
        ReflectionTestUtils.setField(campaign, "tier", Campaign.TIER_BASIC);
        ReflectionTestUtils.setField(campaign, "slug", "promo-resend");

        when(campaignRepository.findById(77L)).thenReturn(Optional.of(campaign));
        when(batchRepository.existsByCampaignId(77L)).thenReturn(true);
        when(batchRepository.save(org.mockito.ArgumentMatchers.any(CampaignBatch.class)))
            .thenAnswer(invocation -> {
                CampaignBatch batch = invocation.getArgument(0);
                ReflectionTestUtils.setField(batch, "id", 901L);
                return batch;
            });
        when(recipientRepository.findByCampaignId(77L)).thenReturn(List.of());
        when(recipientRepository.reserveNew(
            org.mockito.ArgumentMatchers.eq(77L), org.mockito.ArgumentMatchers.eq(901L),
            org.mockito.ArgumentMatchers.eq("b@x.com"), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(quotaService.current()).thenReturn(ampleQuota());
        when(linkBuilder.unsubscribeLink("b@x.com")).thenReturn("https://x/unsubscribe?u=b");
        when(mailSender.sendBatch(org.mockito.ArgumentMatchers.anyList())).thenReturn("job-10");

        RecipientService recipientService = mock(RecipientService.class);
        when(recipientService.recipients(null, null, null, null))
            .thenReturn(List.of("b@x.com"));

        PromoRecipientTokenService promoTokenService = mock(PromoRecipientTokenService.class);
        when(promoTokenService.issue("b@x.com")).thenReturn("BASE64.SIGNATURE");

        CampaignDeliveryService service = new CampaignDeliveryService(
            campaignRepository, batchRepository, recipientRepository,
            mock(EmailLogRepository.class), recipientService, mailSender, quotaService,
            new EmailTemplate(), linkBuilder,
            new ReaderSiteLinks("https://reader.example.com"),
            mock(JdbcTemplate.class),
            new MailBodyRenderer(new ContentSplitter(), new MarkdownRenderer(),
                new ReaderSiteLinks("https://reader.example.com"),
                surveyBlockRenderer(), "https://reader.example.com"),
            promoTokenService);

        service.createBatch(77L, List.of("b@x.com"), "now", null);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<MailSender.Email>> captor =
            org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(mailSender).sendBatch(captor.capture());
        String html = captor.getValue().getFirst().html();
        assertFalse(html.contains(PromoRecipientTokenService.PLACEHOLDER),
            "補寄輸出不可殘留佔位符：" + html);
        assertTrue(html.contains("https://example.org/promo/c/55?rt=BASE64.SIGNATURE"),
            "絕對網址須保留、僅替換 rt 參數值：" + html);
    }

    /** 額度充足的 Quota，讓測試專注在內容折疊而非額度裁切。 */
    private MailQuotaService.Quota ampleQuota() {
        return new MailQuotaService.Quota("zeabur", "healthy",
            999999999L, 0, 999999999L, 50000, 0, 10050,
            10050, 500, 50, 10000, 500, false, null, null);
    }

    /** 以反射建立 JPA 唯讀測試資料。 */
    private CampaignRecipient recipient(String email, String status) {
        CampaignRecipient recipient = new CampaignRecipient();
        ReflectionTestUtils.setField(recipient, "campaignId", 42L);
        ReflectionTestUtils.setField(recipient, "email", email);
        ReflectionTestUtils.setField(recipient, "emailNormalized", email);
        ReflectionTestUtils.setField(recipient, "status", status);
        return recipient;
    }
}
