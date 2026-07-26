package world.springai.survey.newsletter;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import world.springai.survey.audience.RecipientService;
import world.springai.survey.audience.SubscriptionLinkBuilder;
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.EmailTemplate;
import world.springai.survey.mail.MailQuotaService;
import world.springai.survey.mail.MailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CampaignService：立即走 batch、排程走 schedule、每封含個人化退訂連結、統計與失敗處理 */
class CampaignServiceTest {

    private final MailSender mailSender = mock(MailSender.class);
    private final RecipientService recipientService = mock(RecipientService.class);
    private final CampaignRepository campaignRepository = mock(CampaignRepository.class);
    private final EmailLogRepository emailLogRepository = mock(EmailLogRepository.class);
    private final MarkdownRenderer markdownRenderer = new MarkdownRenderer();
    private final EmailTemplate emailTemplate = new EmailTemplate();
    // 連結格式已由 SubscriptionLinkBuilderTest 鎖住，這裡只 stub 固定回傳值以驗證
    // 「每封信帶上該收件人的個人化連結」，不重複斷言連結字串本身的正確性
    private final SubscriptionLinkBuilder linkBuilder = mock(SubscriptionLinkBuilder.class);
    private final MailQuotaService mailQuotaService = mock(MailQuotaService.class);
    // 用真實實作而非 mock：發布守門要問的正是「讀者端會不會渲染 gate」，
    // 而那個判斷就是這個類別。mock 掉就只是在驗自己的假設。
    private final ContentSplitter contentSplitter = new ContentSplitter();

    private final CampaignService svc = new CampaignService(
        mailSender, recipientService, campaignRepository, emailLogRepository,
        markdownRenderer, emailTemplate, linkBuilder, mailQuotaService, contentSplitter,
        "https://news.example.com");

    {
        // 除非測試特別 stub 更小的量，否則額度視為充足——避免所有既有發送測試
        // 都在額度檢查處撞到 NPE 或被誤判為額度不足
        when(mailQuotaService.current()).thenReturn(quotaWithMarketing(10000));
    }

    /** 建一個只關心 marketingRemaining 的 Quota；其餘欄位給合理但無關的值 */
    private MailQuotaService.Quota quotaWithMarketing(long marketingRemaining) {
        long remaining = marketingRemaining + 50;
        return new MailQuotaService.Quota("zeabur", "healthy",
            999999999L, 0, 999999999L,
            50000, 0, remaining,
            remaining, Math.min(remaining, 500),
            50, marketingRemaining, Math.min(marketingRemaining, 500),
            false, null, null);
    }

    /** 立即發送：呼叫 sendBatch，每封 html 含該收件人的退訂連結，campaign 記為 sent、accepted=2 */
    @Test
    void immediateSendUsesBatchWithPersonalizedLinks() {
        when(recipientService.recipients(null, null)).thenReturn(List.of("a@x.com", "b@x.com"));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(i -> i.getArgument(0));
        when(mailSender.sendBatch(anyList())).thenReturn("job-1");
        // stub 固定值，驗證「每封信帶上該收件人專屬的連結」；連結格式本身由
        // SubscriptionLinkBuilderTest 負責，這裡不重複斷言
        when(linkBuilder.unsubscribeLink("a@x.com")).thenReturn("https://x/unsubscribe?u=a");
        when(linkBuilder.unsubscribeLink("b@x.com")).thenReturn("https://x/unsubscribe?u=b");

        CampaignService.SendResult r = svc.send("主旨", "# 內文", null, null, "now", null);

        assertEquals(2, r.recipientCount());
        assertEquals(2, r.accepted());
        assertEquals(0, r.failed());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MailSender.Email>> captor = ArgumentCaptor.forClass(List.class);
        verify(mailSender).sendBatch(captor.capture());
        List<MailSender.Email> sent = captor.getValue();
        assertEquals(2, sent.size());
        assertTrue(sent.get(0).html().contains("https://x/unsubscribe?u=a"), sent.get(0).html());
        assertTrue(sent.get(1).html().contains("https://x/unsubscribe?u=b"), sent.get(1).html());
        assertTrue(sent.get(0).html().contains("內文"));
        verify(mailSender, never()).schedule(any(), any());
    }

    /** 排程發送：對每位收件人呼叫 schedule，帶 scheduledAt，campaign 記為 scheduled */
    @Test
    void scheduledSendUsesScheduleApi() {
        Instant at = Instant.parse("2030-01-01T00:00:00Z");
        when(recipientService.recipients(null, null)).thenReturn(List.of("a@x.com"));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(i -> i.getArgument(0));
        when(mailSender.schedule(any(), eq(at))).thenReturn("sched-1");

        CampaignService.SendResult r = svc.send("主旨", "內文", null, null, "schedule", at);

        assertEquals(1, r.accepted());
        verify(mailSender).schedule(any(), eq(at));
        verify(mailSender, never()).sendBatch(anyList());
    }

    /** 修改排程：取消舊排程信、以新內容與新時間重排，並就地更新同一筆 campaign */
    @Test
    void rescheduleCancelsOldAndReschedulesInPlace() {
        Instant newAt = Instant.parse("2030-06-01T10:00:00Z");
        Campaign existing = new Campaign("舊主旨", "舊內文", "<p>舊</p>", null, null, "schedule",
            null, 1, "scheduled");
        when(campaignRepository.findById(7L)).thenReturn(java.util.Optional.of(existing));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(i -> i.getArgument(0));
        when(emailLogRepository.findByCampaignIdAndStatus(7L, "scheduled"))
            .thenReturn(List.of(new EmailLog("old@x.com", "舊主旨", "campaign", "old-1", "scheduled", null, 7L)));
        when(mailSender.cancelScheduled("old-1")).thenReturn(true);
        when(recipientService.recipients("學生", null)).thenReturn(List.of("a@x.com", "b@x.com"));
        when(mailSender.schedule(any(), eq(newAt))).thenReturn("sched-2");

        CampaignService.SendResult r = svc.reschedule(7L, "新主旨", "# 新內文", "學生", null, newAt);

        assertEquals(2, r.recipientCount());
        assertEquals(2, r.accepted());
        verify(mailSender).cancelScheduled("old-1");         // 舊排程被取消
        verify(mailSender, times(2)).schedule(any(), eq(newAt)); // 兩位新收件人重排
        assertEquals("新主旨", existing.getSubject());        // 就地更新內容
        assertEquals("scheduled", existing.getStatus());
    }

    /** 修改排程：對非 scheduled 狀態的 campaign 應拒絕（不呼叫 provider） */
    @Test
    void rescheduleRejectsNonScheduledCampaign() {
        Campaign sent = new Campaign("主旨", "內文", "<p>x</p>", null, null, "now", null, 1, "sent");
        when(campaignRepository.findById(9L)).thenReturn(java.util.Optional.of(sent));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
            () -> svc.reschedule(9L, "新主旨", "內文", null, null, Instant.parse("2030-01-01T00:00:00Z")));
        verify(mailSender, never()).schedule(any(), any());
        verify(mailSender, never()).cancelScheduled(any());
    }

    /** 批量丟例外：整批記 failed、不中斷 */
    @Test
    void batchFailureCountsFailed() {
        when(recipientService.recipients(null, null)).thenReturn(List.of("a@x.com", "b@x.com"));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(i -> i.getArgument(0));
        when(mailSender.sendBatch(anyList())).thenThrow(new RuntimeException("429"));

        CampaignService.SendResult r = svc.send("主旨", "內文", null, null, "now", null);

        assertEquals(0, r.accepted());
        assertEquals(2, r.failed());
    }

    // ===================== 發布欄位（tier / creditCost / slug / publishedAt）驗證 =====================

    /** tier 為未知值時回 400，且不建立 campaign（save 從未被呼叫） */
    @Test
    void sendWithUnknownTierRejected() {
        when(recipientService.recipients(null, null)).thenReturn(List.of("a@x.com"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> svc.send("主旨", "內文", null, null, "now", null,
                "GOLD", null, null, null));

        assertEquals(400, ex.getStatusCode().value());
        verify(campaignRepository, never()).save(any(Campaign.class));
    }

    /** slug 格式不合法（含大寫、底線等）時回 400，且不建立 campaign */
    @Test
    void sendWithInvalidSlugFormatRejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> svc.send("主旨", "內文", null, null, "now", null,
                null, null, "Hello_World", null));

        assertEquals(400, ex.getStatusCode().value());
        verify(campaignRepository, never()).save(any(Campaign.class));
    }

    /** tier=PREMIUM 但 creditCost 為 0（或未指定）時回 400，且不建立 campaign */
    @Test
    void sendWithPremiumZeroCreditCostRejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> svc.send("主旨", "內文", null, null, "now", null,
                Campaign.TIER_PREMIUM, 0, null, null));

        assertEquals(400, ex.getStatusCode().value());
        verify(campaignRepository, never()).save(any(Campaign.class));
    }

    /** slug 重複時回明確 400（而非讓唯一索引丟 500），且不建立 campaign */
    @Test
    void sendWithDuplicateSlugRejected() {
        when(campaignRepository.findBySlug("hello-world"))
            .thenReturn(Optional.of(new Campaign("舊", "舊", "舊", null, null, "now", null, 0, "sent")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> svc.send("主旨", "內文", null, null, "now", null,
                null, null, "hello-world", null));

        assertEquals(400, ex.getStatusCode().value());
        verify(campaignRepository, never()).save(any(Campaign.class));
    }

    /** 設了 slug 但沒設 publishedAt → 自動視為立即發布（publishedAt 非 NULL，archive 查得到） */
    @Test
    void sendWithSlugButNoPublishedAtAutoPublishes() {
        when(recipientService.recipients(null, null)).thenReturn(List.of("a@x.com"));
        when(campaignRepository.findBySlug("hello-world")).thenReturn(Optional.empty());
        ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
        when(campaignRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));
        when(mailSender.sendBatch(anyList())).thenReturn("job-1");

        svc.send("主旨", "內文", null, null, "now", null,
            null, null, "hello-world", null);

        Campaign saved = captor.getAllValues().get(0);
        assertNotNull(saved.getPublishedAt(), "設了 slug 卻沒設 publishedAt 應自動視為立即發布");
        assertEquals("hello-world", saved.getSlug());
    }

    /**
     * 反向的矛盾輸入：設了 publishedAt 卻沒設 slug → 回 400，且不建立 campaign。
     *
     * <p>與「設了 slug 未設 publishedAt 自動發布」對稱——這裡沒有自動補值的空間，
     * 因為 slug 是網址片段，服務端無法安全地替使用者「猜」一個；若放行，
     * archive 會列出一篇 publishedAt 非 NULL 但 slug 為 null 的文章，讀者點下去
     * 命中 /r/news/（空 path variable）而 404。</p>
     */
    @Test
    void sendWithPublishedAtButNoSlugRejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> svc.send("主旨", "內文", null, null, "now", null,
                null, null, null, Instant.parse("2026-07-25T04:00:00Z")));

        assertEquals(400, ex.getStatusCode().value());
        verify(campaignRepository, never()).save(any(Campaign.class));
        verify(mailSender, never()).sendBatch(anyList());
    }

    /** 正常發布 BASIC：campaign 建立且 tier/creditCost/slug/publishedAt 欄位正確落地 */
    @Test
    void sendBasicWithPublishFieldsCreatesCampaignCorrectly() {
        when(recipientService.recipients(null, null)).thenReturn(List.of("a@x.com"));
        when(campaignRepository.findBySlug("my-post")).thenReturn(Optional.empty());
        ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
        when(campaignRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));
        when(mailSender.sendBatch(anyList())).thenReturn("job-1");
        Instant publishedAt = Instant.parse("2026-07-25T04:00:00Z");

        CampaignService.SendResult r = svc.send("主旨", "內文", null, null, "now", null,
            Campaign.TIER_BASIC, 0, "my-post", publishedAt);

        assertEquals(1, r.accepted());
        Campaign saved = captor.getAllValues().get(0);
        assertEquals(Campaign.TIER_BASIC, saved.getTier());
        assertEquals(0, saved.getCreditCost());
        assertEquals("my-post", saved.getSlug());
        assertEquals(publishedAt, saved.getPublishedAt().toInstant());
    }

    // ── 只發布不寄送（publish）────────────────────────────────────────────

    /**
     * 只發布不寄送的核心性質：PREMIUM 可以放行，且<b>一封信都不能寄</b>。
     *
     * <p>這條端點存在的全部理由就是讓 PREMIUM 有一條後台路徑；而它敢放行 PREMIUM
     * 的唯一根據是「不寄信 ⇒ 沒有信件端外洩」。因此 mailSender 的<b>三個</b>寄送方法
     * 都要驗 never()：立即群發走 {@code sendBatch(List)}、排程走 {@code schedule}、
     * 單封測試信走 {@code send(3 args)}。只驗其中一個會讓另外兩條路徑的回歸靜默通過。</p>
     */
    @Test
    void publishPremiumCreatesArticleWithoutSendingAnyMail() {
        when(campaignRepository.findBySlug("premium-web-only")).thenReturn(Optional.empty());
        ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
        when(campaignRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));
        Instant publishedAt = Instant.parse("2026-07-25T04:00:00Z");

        CampaignService.PublishResult r = svc.publish("主旨", "免費區\n\n<!--paywall-->\n\n受限區",
            Campaign.TIER_PREMIUM, 10, "premium-web-only", publishedAt);

        assertEquals(Campaign.TIER_PREMIUM, r.tier());
        assertEquals(10, r.creditCost());
        assertEquals("premium-web-only", r.slug());
        assertEquals(publishedAt, r.publishedAt().toInstant());
        assertEquals("https://news.example.com/r/news/premium-web-only", r.url());

        Campaign saved = captor.getValue();
        assertEquals(Campaign.TIER_PREMIUM, saved.getTier());
        assertEquals(10, saved.getCreditCost());
        assertEquals("premium-web-only", saved.getSlug());
        assertNotNull(saved.getPublishedAt());
        // 網頁端渲染讀的是 markdown（經 ContentSplitter 切分），必須完整落地
        assertTrue(saved.getMarkdown().contains("<!--paywall-->"), saved.getMarkdown());
        assertTrue(saved.getMarkdown().contains("受限區"));
        // bodyHtml 是「信件版內文」；這條路徑沒有信件版，存全文只會成為階段 D 的外洩來源
        assertNull(saved.getBodyHtml());
        // 不得被後台歷史列表讀成「一次寄了 0 封的失敗群發」
        assertEquals(Campaign.MODE_PUBLISH, saved.getMode());
        assertEquals(Campaign.STATUS_PUBLISHED, saved.getStatus());
        assertEquals(0, saved.getRecipientCount());

        // ★ 一封信都不能寄（三種寄送方法全驗）
        verify(mailSender, never()).sendBatch(anyList());
        verify(mailSender, never()).schedule(any(), any());
        verify(mailSender, never()).send(any(), any(), any());
        // 沒有收件人查詢：這條路徑不需要名單
        verify(recipientService, never()).recipients(any(), any());
    }

    /**
     * 不消耗任何寄信額度：既不查行銷可用量，也不讓額度快取失效。
     *
     * <p>若這條端點誤用了 {@code applyMarketingQuota}，行銷可用量為 0 時會回 409——
     * 一篇「根本不寄信」的文章會因為額度用盡而發不出去，那是完全無關的失敗理由。</p>
     */
    @Test
    void publishDoesNotTouchMailQuota() {
        when(campaignRepository.findBySlug("no-quota")).thenReturn(Optional.empty());
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(i -> i.getArgument(0));

        svc.publish("主旨", "內文", Campaign.TIER_BASIC, null, "no-quota", null);

        verify(mailQuotaService, never()).current();
        verify(mailQuotaService, never()).invalidate();
        verify(emailLogRepository, never()).save(any(EmailLog.class));
    }

    /**
     * slug 對這條端點是<b>必填</b>：缺 slug 回 400，且不寫入任何 campaign。
     *
     * <p>沒有 slug 的「純網頁文章」沒有 {@code /r/news/{slug}} 網址，讀者永遠打不開，
     * 寫進資料庫等於消失。把 slug 改成選填，本測試變紅。</p>
     */
    @Test
    void publishWithoutSlugRejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> svc.publish("主旨", "內文", Campaign.TIER_PREMIUM, 10, null, null));
        assertEquals(400, ex.getStatusCode().value());

        // 空白字串同樣視為未填（否則會落到 validateSlug 而回一個誤導的「格式錯誤」）
        ResponseStatusException blank = assertThrows(ResponseStatusException.class,
            () -> svc.publish("主旨", "內文", Campaign.TIER_PREMIUM, 10, "   ", null));
        assertEquals(400, blank.getStatusCode().value());

        verify(campaignRepository, never()).save(any(Campaign.class));
    }

    /**
     * tier 判斷 fail-closed：未知 tier 必須被拒絕，不得放行。
     *
     * <p>資料庫沒有 tier 白名單約束。若這裡改成 fail-open（未知 tier 當成
     * 可發布），一個打錯字的 {@code PREMIUN} 會讓 {@code AccessDecisionService}
     * 走進階規則、卻以 {@code credit_cost=0} 落地——付費內容全面免費外洩。</p>
     */
    @Test
    void publishWithUnknownTierRejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> svc.publish("主旨", "內文", "GOLD", 10, "unknown-tier", null));
        assertEquals(400, ex.getStatusCode().value());
        verify(campaignRepository, never()).save(any(Campaign.class));
    }

    /** PREMIUM 的 creditCost 必須 > 0（與 send 共用 validateCreditCost，不另立一套規則） */
    @Test
    void publishPremiumWithZeroCreditCostRejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> svc.publish("主旨", "內文", Campaign.TIER_PREMIUM, 0, "premium-free", null));
        assertEquals(400, ex.getStatusCode().value());
        verify(campaignRepository, never()).save(any(Campaign.class));
    }

    /**
     * {@code campaign.credit_cost} 現在也有上限（與 {@code AdminSettingController} 的
     * {@code credit.premium_cost} 共用同一個常數 10000）：上限值本身必須是合法輸入，
     * 不能把區間寫成半開而誤擋恰好等於上限的正常設定。刻意不引用生產程式的常數，
     * 讀同一個常數的測試改壞了也不會變紅。
     */
    @Test
    void publishPremiumCreditCostAtMaxAccepted() {
        when(campaignRepository.findBySlug("premium-at-max")).thenReturn(Optional.empty());
        ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
        when(campaignRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

        CampaignService.PublishResult r = svc.publish("主旨", "免費區\n\n<!--paywall-->\n\n受限區",
            Campaign.TIER_PREMIUM, 10000, "premium-at-max", null);

        assertEquals(10000, r.creditCost());
        assertEquals(10000, captor.getValue().getCreditCost());
    }

    /**
     * 超過上限必須回 400，且不建立 campaign。
     *
     * <p><b>沒有上限的實際後果</b>：{@code campaign.credit_cost} 是
     * {@code CreditPolicy.costOf()} 優先採用的欄位，B5 對 {@code app_setting} 的上限
     * 完全擋不到它；打錯成 {@link Integer#MAX_VALUE} 之後，讀者會看到「解鎖需要
     * 2147483647 點，你還差 2147483347 點」，且發布後<b>沒有任何 UI 或 API 能改價</b>
     * （唯一手段是手動 {@code UPDATE}），是這一類輸入錯誤裡唯一無法從介面復原的。</p>
     */
    @Test
    void publishPremiumCreditCostAboveMaxRejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> svc.publish("主旨", "免費區\n\n<!--paywall-->\n\n受限區",
                Campaign.TIER_PREMIUM, 10001, "premium-over-max", null));
        assertEquals(400, ex.getStatusCode().value());

        ResponseStatusException extreme = assertThrows(ResponseStatusException.class,
            () -> svc.publish("主旨", "免費區\n\n<!--paywall-->\n\n受限區",
                Campaign.TIER_PREMIUM, Integer.MAX_VALUE, "premium-extreme", null));
        assertEquals(400, extreme.getStatusCode().value());

        verify(campaignRepository, never()).save(any(Campaign.class));
    }

    /** slug 重複時回 400（與 send 共用 validateSlug，避免唯一索引以 500 的形式失敗） */
    @Test
    void publishWithDuplicateSlugRejected() {
        Campaign existing = new Campaign("舊", "舊", null, null, null, "now", null, 0, "sent");
        when(campaignRepository.findBySlug("taken")).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> svc.publish("主旨", "內文", Campaign.TIER_BASIC, null, "taken", null));
        assertEquals(400, ex.getStatusCode().value());
        verify(campaignRepository, never()).save(any(Campaign.class));
    }

    /** 省略 publishedAt 視為立即發布：publishedAt 必須非 NULL，否則文章不會出現在 archive */
    @Test
    void publishWithoutPublishedAtPublishesImmediately() {
        when(campaignRepository.findBySlug("now-post")).thenReturn(Optional.empty());
        ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
        when(campaignRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

        CampaignService.PublishResult r = svc.publish("主旨", "內文",
            Campaign.TIER_BASIC, null, "now-post", null);

        assertNotNull(r.publishedAt());
        assertNotNull(captor.getValue().getPublishedAt());
        assertTrue(captor.getValue().isPublished());
    }

    /** 主旨或內文為空一律 400（campaign 兩欄皆 NOT NULL，不讓寫入以 500 失敗） */
    @Test
    void publishWithBlankSubjectOrMarkdownRejected() {
        assertEquals(400, assertThrows(ResponseStatusException.class,
            () -> svc.publish("  ", "內文", Campaign.TIER_BASIC, null, "blank-a", null))
            .getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
            () -> svc.publish("主旨", null, Campaign.TIER_BASIC, null, "blank-b", null))
            .getStatusCode().value());
        verify(campaignRepository, never()).save(any(Campaign.class));
    }

    /** 公開網址：設定值帶結尾斜線時不得產生雙斜線（某些反向代理下會 404） */
    @Test
    void publishUrlHandlesTrailingSlashInBaseUrl() {
        CampaignService withSlash = new CampaignService(
            mailSender, recipientService, campaignRepository, emailLogRepository,
            markdownRenderer, emailTemplate, linkBuilder, mailQuotaService, contentSplitter,
            "https://news.example.com/");
        when(campaignRepository.findBySlug("slash")).thenReturn(Optional.empty());
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(i -> i.getArgument(0));

        CampaignService.PublishResult r = withSlash.publish("主旨", "內文",
            Campaign.TIER_BASIC, null, "slash", null);

        assertEquals("https://news.example.com/r/news/slash", r.url());
    }

    /**
     * ★ PREMIUM 沒有 {@code <!--paywall-->} 標記必須回 400，且一列都不寫入。
     *
     * <p>這是「頁面說 PREMIUM 12 點、實際做免費全開」的唯一入口守門。無標記時
     * {@link ContentSplitter} 把全文都當免費區，於是 {@code /r/archive} 依
     * {@code isPremium()} 掛上「進階」標籤、單篇頁顯示「解鎖 12 點」，而未登入訪客
     * 直接拿到整篇全文、一點都不用付。<b>而且沒有任何回饋管道會揭露它</b>：不寄信所以
     * 沒有寄送統計，{@code credit_txn} 永遠不會有這篇的 READ，看起來就像「沒人想解鎖」。</p>
     *
     * <p>讀者端「無標記就不收費、不渲染 gate」是刻意且有測試的設計
     * （{@code ReaderPageControllerTest.premiumWithoutPaywallMarkerRendersNeitherGateNorScript}），
     * 所以缺陷不在渲染層——publish 是 PREMIUM 唯一的建立路徑，也是唯一能攔的地方。
     * 把 CampaignService.publish 裡的這道檢查刪掉，本測試立刻變紅。</p>
     *
     * <p>大小寫變體一併驗：{@code ContentSplitter.isMarkerLine} 是大小寫敏感的
     * {@code contentEquals}，把標記打成 {@code <!--PAYWALL-->} 與整行忘記貼是<b>同一個</b>
     * 失效（都沒有受限區），而前者是操作者最容易犯、最不容易自己看出來的那一種。</p>
     */
    @Test
    void publishPremiumWithoutPaywallMarkerRejected() {
        when(campaignRepository.findBySlug("premium-no-marker")).thenReturn(Optional.empty());
        when(campaignRepository.findBySlug("premium-upper-marker")).thenReturn(Optional.empty());

        // ① 整行忘了貼標記
        ResponseStatusException missing = assertThrows(ResponseStatusException.class,
            () -> svc.publish("主旨", "# 標題\n\n全篇都是內容，沒有任何標記",
                Campaign.TIER_PREMIUM, 12, "premium-no-marker", null));
        assertEquals(400, missing.getStatusCode().value());
        assertTrue(missing.getReason() != null && missing.getReason().contains("paywall"),
            missing.getReason());

        // ② 標記打成大寫（ContentSplitter 是大小寫敏感的精確比對）
        ResponseStatusException wrongCase = assertThrows(ResponseStatusException.class,
            () -> svc.publish("主旨", "免費區\n\n<!--PAYWALL-->\n\n本該收費的內容",
                Campaign.TIER_PREMIUM, 12, "premium-upper-marker", null));
        assertEquals(400, wrongCase.getStatusCode().value());

        // 標示為付費卻全篇免費的文章，一列都不得寫進資料庫
        verify(campaignRepository, never()).save(any(Campaign.class));
    }

    /**
     * 這道守門只加在 PREMIUM：BASIC 沒有標記是完全正常的（整篇免費就是它的意思）。
     *
     * <p>若誤把檢查寫成「所有 tier 都必須有標記」，一般免費文章會全部發不出去——
     * 那會讓人為了發文而亂加標記，反而製造出「BASIC 卻有受限區」的怪資料。</p>
     */
    @Test
    void publishBasicWithoutPaywallMarkerAllowed() {
        when(campaignRepository.findBySlug("basic-no-marker")).thenReturn(Optional.empty());
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(i -> i.getArgument(0));

        CampaignService.PublishResult r = svc.publish("主旨", "整篇都是免費內容",
            Campaign.TIER_BASIC, null, "basic-no-marker", null);

        assertEquals(Campaign.TIER_BASIC, r.tier());
        assertEquals(0, r.creditCost());
    }

    // ── 下架（撤回發布）───────────────────────────────────────────────────

    /** 建一個已發布、狀態為 published 的假 campaign（下架測試共用） */
    private Campaign publishedCampaign(String slug) {
        Campaign c = new Campaign("主旨", "內文", null, null, null,
            Campaign.MODE_PUBLISH, null, 0, Campaign.STATUS_PUBLISHED);
        c.setSlug(slug);
        c.setPublishedAt(java.time.OffsetDateTime.parse("2026-07-25T04:00:00Z"));
        return c;
    }

    /**
     * ★ 下架成功：走<b>只寫 published_at 一欄</b>的條件式 UPDATE，不用 save(entity) 整列寫回。
     *
     * <p>{@link Campaign} 沒有 {@code @Version} 也沒有 {@code @DynamicUpdate}，
     * {@code save(entity)} 的 UPDATE 會帶上所有可更新欄位（subject／markdown／tier／
     * credit_cost／統計…），把 SELECT 當下的整列快照寫回去，靜默覆蓋這段期間別的請求
     * 對同一列的變更。本專案已有兩個 Critical 源於整列寫回。把實作改成 {@code save(campaign)}
     * 之後，這裡的 {@code verify(markUnpublished)} 與 {@code never()).save} 兩條都會變紅。</p>
     *
     * <p><b>{@code status} 必須一併改成 {@code unpublished}</b>：留在 {@code published}
     * 會讓後台只能靠 {@code publishedAt} 是否為 null 反推，pill 也繼續顯示
     * {@code published}——畫面說已發布、事實是讀者看不到；而且沒有那個狀態值就沒有
     * 重新上架的守門依據。把第三個引數改回 {@code STATUS_PUBLISHED}（或把 status
     * 從 UPDATE 敘述裡拿掉）→ 這裡的 stub 不再匹配而回 0，實作會走進「狀態已被變更」
     * 的 409 分支，本測試立刻變紅。</p>
     */
    @Test
    void unpublishClearsPublishedAtWithConditionalUpdate() {
        Campaign c = publishedCampaign("to-unpublish");
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(c));
        when(emailLogRepository.countByCampaignId(7L)).thenReturn(0L);
        when(campaignRepository.markUnpublished(
            7L, Campaign.STATUS_PUBLISHED, Campaign.STATUS_UNPUBLISHED)).thenReturn(1);

        CampaignService.UnpublishResult r = svc.unpublish(7L);

        assertEquals(7L, r.campaignId());
        assertEquals("to-unpublish", r.slug());
        verify(campaignRepository).markUnpublished(
            7L, Campaign.STATUS_PUBLISHED, Campaign.STATUS_UNPUBLISHED);
        // ★ 不得整列寫回
        verify(campaignRepository, never()).save(any(Campaign.class));
        // ★ 不刪列：已解鎖者的 article_access 以 campaign_id 指向這一列，刪了就毀掉他們的憑證
        verify(campaignRepository, never()).delete(any(Campaign.class));
        verify(campaignRepository, never()).deleteById(any());
        // ★ 不動帳本、不動寄送記錄（下架只改「這篇現在對外可見嗎」這一個事實）
        verify(emailLogRepository, never()).save(any(EmailLog.class));
    }

    /**
     * 下架只允許 {@code status='published'}；其他狀態回 409 且不執行任何 UPDATE。
     *
     * <p>{@code sent}／{@code scheduled}／{@code failed} 的列是寄送批次，
     * 用一條「撤回網頁發布」的端點去改它們等於混淆兩種語意。
     * 把狀態檢查拿掉後，本測試（三個狀態全驗）立刻變紅。</p>
     */
    @Test
    void unpublishRejectsNonPublishedStatus() {
        for (String status : List.of("sent", "scheduled", "failed")) {
            Campaign c = new Campaign("主旨", "內文", null, null, null, "now", null, 1, status);
            c.setSlug("some-" + status);
            when(campaignRepository.findById(11L)).thenReturn(Optional.of(c));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> svc.unpublish(11L), "狀態 " + status + " 不該可下架");
            assertEquals(409, ex.getStatusCode().value(), status);
        }
        verify(campaignRepository, never()).markUnpublished(any(), any(), any());
        verify(campaignRepository, never()).save(any(Campaign.class));
    }

    /**
     * ★ 寄過信的列不可下架：回 409 且不執行任何 UPDATE。
     *
     * <p>有 {@code email_log} 代表這篇已經寄進讀者信箱，信裡的連結指向
     * {@code /r/news/{slug}}；下架會讓已收到信的讀者點到 404——對他們來說那是
     * 「站方寄了一封連結壞掉的信」。把 email_log 檢查拿掉，本測試立刻變紅。</p>
     */
    @Test
    void unpublishRejectsCampaignWithEmailLog() {
        Campaign c = publishedCampaign("already-mailed");
        when(campaignRepository.findById(12L)).thenReturn(Optional.of(c));
        when(emailLogRepository.countByCampaignId(12L)).thenReturn(3L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> svc.unpublish(12L));
        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason() != null && ex.getReason().contains("寄送記錄"), ex.getReason());

        verify(campaignRepository, never()).markUnpublished(any(), any(), any());
        verify(campaignRepository, never()).save(any(Campaign.class));
    }

    /** 找不到批次回 404（而非讓 Optional.get() 以 500 失敗） */
    @Test
    void unpublishUnknownCampaignReturns404() {
        when(campaignRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> svc.unpublish(999L));
        assertEquals(404, ex.getStatusCode().value());
    }

    /**
     * 條件式 UPDATE 影響 0 列（狀態在讀取後被別的請求改掉）→ 回 409，不假裝成功。
     *
     * <p>正確性來自受影響筆數，不是來自先前的狀態檢查。若把回傳值丟掉不看，
     * 後台會顯示「已下架」而文章仍在 /r/archive 上——頁面說下架了、實際沒下架。</p>
     */
    @Test
    void unpublishReportsConflictWhenNoRowUpdated() {
        Campaign c = publishedCampaign("race");
        when(campaignRepository.findById(13L)).thenReturn(Optional.of(c));
        when(emailLogRepository.countByCampaignId(13L)).thenReturn(0L);
        when(campaignRepository.markUnpublished(
            13L, Campaign.STATUS_PUBLISHED, Campaign.STATUS_UNPUBLISHED)).thenReturn(0);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> svc.unpublish(13L));
        assertEquals(409, ex.getStatusCode().value());
    }

    // ── 重新上架（撤回下架）────────────────────────────────────────────────

    /** 建一個已下架（status=unpublished、publishedAt=NULL）的假 campaign */
    private Campaign unpublishedCampaign(String slug) {
        Campaign c = new Campaign("主旨", "內文", null, null, null,
            Campaign.MODE_PUBLISH, null, 0, Campaign.STATUS_UNPUBLISHED);
        c.setSlug(slug);
        return c;
    }

    /**
     * ★ 重新上架成功：走<b>只寫 published_at 與 status 兩欄</b>的條件式 UPDATE，
     * 不用 {@code save(entity)} 整列寫回，也不動 {@code article_access} 與 {@code credit_txn}。
     *
     * <p>發布時間一律取當下（不沿用下架前的舊值）：{@code published_at} 的語意是
     * 「從什麼時候起對外可見」，下架期間確實不可見；而 {@code /r/archive} 以它排序，
     * 沿用舊值會讓文章悄悄插回列表深處，沒有讀者會發現它回來了。</p>
     *
     * <p>把實作改成 {@code save(campaign)} 之後，{@code verify(markRepublished)} 與
     * {@code never()).save} 兩條都會變紅。</p>
     */
    @Test
    void republishRestoresPublicationWithConditionalUpdate() {
        Campaign c = unpublishedCampaign("to-republish");
        when(campaignRepository.findById(21L)).thenReturn(Optional.of(c));
        when(campaignRepository.markRepublished(eq(21L), eq(Campaign.STATUS_UNPUBLISHED),
            eq(Campaign.STATUS_PUBLISHED), any())).thenReturn(1);

        // 下界也要截斷到微秒：republish 內部的時間戳是 truncatedTo(MICROS)（讓寫入
        // 資料庫的值與回傳給後台的值逐位元相同，理由見那裡的註解），所以它可以比
        // 未截斷的 now() 早最多 1 微秒。不截斷下界會在奈秒非零時偶發失敗。
        java.time.OffsetDateTime before = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
            .truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        CampaignService.RepublishResult r = svc.republish(21L);

        assertEquals(21L, r.campaignId());
        assertEquals("to-republish", r.slug());
        assertEquals("https://news.example.com/r/news/to-republish", r.url());
        // 發布時間是「當下」而非下架前的舊值
        assertNotNull(r.publishedAt());
        assertTrue(!r.publishedAt().isBefore(before), "publishedAt 應為呼叫當下的時間：" + r.publishedAt());

        // 傳給資料庫的時間必須與回傳給後台的是同一個值，否則畫面顯示的與實際寫入的不同
        ArgumentCaptor<java.time.OffsetDateTime> at =
            ArgumentCaptor.forClass(java.time.OffsetDateTime.class);
        verify(campaignRepository).markRepublished(eq(21L), eq(Campaign.STATUS_UNPUBLISHED),
            eq(Campaign.STATUS_PUBLISHED), at.capture());
        assertEquals(r.publishedAt(), at.getValue());

        // ★ 不得整列寫回
        verify(campaignRepository, never()).save(any(Campaign.class));
        // ★ 不刪列、不動寄送記錄（重新上架只改「這篇現在對外可見嗎」這一個事實）
        verify(campaignRepository, never()).delete(any(Campaign.class));
        verify(campaignRepository, never()).deleteById(any());
        verify(emailLogRepository, never()).save(any(EmailLog.class));
        // ★ 不寄任何信：重新上架是網頁狀態的變更，不是重新發送
        verify(mailSender, never()).sendBatch(anyList());
        verify(mailSender, never()).schedule(any(), any());
    }

    /**
     * ★ 重新上架只接受「目前對外不可見」的列；其餘狀態一律 409 且不執行任何 UPDATE。
     *
     * <p>涵蓋兩類必須被拒絕的列：① 寄送批次（{@code sent}／{@code scheduled}／
     * {@code failed}／{@code cancelled}）——它們沒有「網頁發布」這件事可以復原，
     * 用這條端點去改它們等於混淆兩種語意；② <b>已經對外可見</b>的 {@code published}
     * 列——對它重新上架會用一個新的時間戳覆蓋掉原本的發布時間，文章無聲跳到
     * archive 最上面，而操作者以為自己什麼都沒改。</p>
     *
     * <p>把狀態守門拿掉（或只保留「不是 published 就放行」這種反向寫法），
     * 本測試立刻變紅。</p>
     */
    @Test
    void republishRejectsRowsThatAreNotUnpublished() {
        for (String status : List.of("sent", "scheduled", "failed", "cancelled")) {
            Campaign c = new Campaign("主旨", "內文", null, null, null, "now", null, 1, status);
            c.setSlug("some-" + status);
            when(campaignRepository.findById(22L)).thenReturn(Optional.of(c));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> svc.republish(22L), "狀態 " + status + " 不該可重新上架");
            assertEquals(409, ex.getStatusCode().value(), status);
        }

        // 已經對外可見的 published 列同樣要拒絕
        when(campaignRepository.findById(22L)).thenReturn(Optional.of(publishedCampaign("already-live")));
        ResponseStatusException visible = assertThrows(ResponseStatusException.class,
            () -> svc.republish(22L), "已對外可見的文章不該可重新上架");
        assertEquals(409, visible.getStatusCode().value());
        assertTrue(visible.getReason() != null && visible.getReason().contains("已對外可見"),
            visible.getReason());

        verify(campaignRepository, never()).markRepublished(any(), any(), any(), any());
        verify(campaignRepository, never()).save(any(Campaign.class));
    }

    /**
     * 本功能之前被下架的舊資料（{@code status='published'} 但 {@code published_at} 為 NULL）
     * 仍可重新上架。
     *
     * <p>上一版的下架只清 {@code published_at}、<b>沒有</b>改 {@code status}。若守門只認
     * {@code status='unpublished'}，那些列會永久卡住——只能手動 {@code UPDATE campaign}，
     * 正是這條端點要消滅的操作模式。共同前提是 {@code published_at IS NULL}，
     * 所以線上可見的文章不會誤入（由上一條測試釘住）。</p>
     */
    @Test
    void republishAcceptsLegacyRowsUnpublishedBeforeStatusExisted() {
        Campaign legacy = publishedCampaign("legacy-unpublished");
        legacy.setPublishedAt(null); // 舊版下架的形狀：status 仍是 published
        when(campaignRepository.findById(23L)).thenReturn(Optional.of(legacy));
        when(campaignRepository.markRepublished(eq(23L), eq(Campaign.STATUS_PUBLISHED),
            eq(Campaign.STATUS_PUBLISHED), any())).thenReturn(1);

        CampaignService.RepublishResult r = svc.republish(23L);

        assertEquals("legacy-unpublished", r.slug());
        // expectedStatus 必須是「剛剛讀到的那個狀態」，否則 WHERE 對不上而白做工
        verify(campaignRepository).markRepublished(eq(23L), eq(Campaign.STATUS_PUBLISHED),
            eq(Campaign.STATUS_PUBLISHED), any());
    }

    /** 找不到批次回 404（而非讓 Optional.get() 以 500 失敗） */
    @Test
    void republishUnknownCampaignReturns404() {
        when(campaignRepository.findById(998L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> svc.republish(998L));
        assertEquals(404, ex.getStatusCode().value());
    }

    /**
     * 沒有 slug 的列不可重新上架：回 409 而不是「成功」。
     *
     * <p>{@code /r/news/{slug}} 是文章的唯一入口，archive 查詢也要求 slug 非 NULL。
     * 放行等於後台顯示「已重新上架」而讀者哪裡都看不到它——一次完全靜默的空操作。</p>
     */
    @Test
    void republishRejectsRowWithoutSlug() {
        Campaign noSlug = unpublishedCampaign(null);
        when(campaignRepository.findById(24L)).thenReturn(Optional.of(noSlug));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> svc.republish(24L));
        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason() != null && ex.getReason().contains("slug"), ex.getReason());
        verify(campaignRepository, never()).markRepublished(any(), any(), any(), any());
    }

    /**
     * 條件式 UPDATE 影響 0 列（別的請求先上架了）→ 回 409，不假裝成功。
     *
     * <p>正確性來自受影響筆數。若把回傳值丟掉不看，後台會顯示「已重新上架」
     * 並附上網址，而那個時間戳其實沒寫進去。</p>
     */
    @Test
    void republishReportsConflictWhenNoRowUpdated() {
        Campaign c = unpublishedCampaign("race-back");
        when(campaignRepository.findById(25L)).thenReturn(Optional.of(c));
        when(campaignRepository.markRepublished(eq(25L), any(), any(), any())).thenReturn(0);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> svc.republish(25L));
        assertEquals(409, ex.getStatusCode().value());
    }

    /**
     * ★ 下架與重新上架都不得碰點數或帳本。
     *
     * <p>核心不變式是「{@code reader.credits} 恆等於 {@code credit_txn} 總和」，
     * 而帳本只增不改。這一對端點改的是「這篇現在對外可見嗎」，
     * 對任何讀者的餘額與已解鎖憑證都不該有副作用——{@code CampaignService} 連
     * {@code ReaderRepository} 與 {@code CreditTxnRepository} 都沒有注入
     * （{@code newsletter} 不得 import {@code reader}，由 {@code PackageDependencyTest}
     * 守著），所以這裡改為釘住「整條路徑只碰 campaign 那一列的發布欄位」。</p>
     */
    @Test
    void unpublishAndRepublishTouchOnlyThePublicationColumns() {
        Campaign c = publishedCampaign("round-trip");
        when(campaignRepository.findById(31L)).thenReturn(Optional.of(c));
        when(emailLogRepository.countByCampaignId(31L)).thenReturn(0L);
        when(campaignRepository.markUnpublished(
            31L, Campaign.STATUS_PUBLISHED, Campaign.STATUS_UNPUBLISHED)).thenReturn(1);
        svc.unpublish(31L);

        Campaign back = unpublishedCampaign("round-trip");
        when(campaignRepository.findById(31L)).thenReturn(Optional.of(back));
        when(campaignRepository.markRepublished(eq(31L), eq(Campaign.STATUS_UNPUBLISHED),
            eq(Campaign.STATUS_PUBLISHED), any())).thenReturn(1);
        svc.republish(31L);

        // 全程只有兩道條件式 UPDATE，沒有整列寫回、沒有刪列、沒有寄信
        verify(campaignRepository, never()).save(any(Campaign.class));
        verify(campaignRepository, never()).delete(any(Campaign.class));
        verify(campaignRepository, never()).deleteById(any());
        verify(emailLogRepository, never()).save(any(EmailLog.class));
        verify(mailSender, never()).sendBatch(anyList());
        verify(mailSender, never()).schedule(any(), any());
        verify(mailSender, never()).send(any(), any(), any());
        // 額度快取也不該被動：這一對端點完全不寄信，沒有消耗任何額度
        verify(mailQuotaService, never()).invalidate();
    }

    /**
     * 守門：tier=PREMIUM 時即使 creditCost/slug 皆合法，發送仍被拒絕
     * （階段 D 的信件折疊尚未實作，PREMIUM 內容不得寄出，只能在網頁上發布）。
     * 這是本次任務最重要的一條測試——確保「能設定 PREMIUM」與「信件端全開」不會同時成立。
     */
    @Test
    void sendPremiumTierIsRejectedByFoldingGuard() {
        when(campaignRepository.findBySlug("premium-post")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> svc.send("主旨", "內文", null, null, "now", null,
                Campaign.TIER_PREMIUM, 10, "premium-post", null));

        assertEquals(400, ex.getStatusCode().value());
        verify(campaignRepository, never()).save(any(Campaign.class));
        verify(mailSender, never()).sendBatch(anyList());
    }

    /** 重排（reschedule）同樣受折疊守門保護：既有 campaign 的 tier 為 PREMIUM 時拒絕重排寄送 */
    @Test
    void reschedulePremiumTierIsRejectedByFoldingGuard() {
        Campaign existing = new Campaign("舊主旨", "舊內文", "<p>舊</p>", null, null, "schedule",
            null, 1, "scheduled");
        existing.setTier(Campaign.TIER_PREMIUM);
        existing.setCreditCost(10);
        when(campaignRepository.findById(11L)).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> svc.reschedule(11L, "新主旨", "新內文", null, null,
                Instant.parse("2030-01-01T00:00:00Z")));

        assertEquals(400, ex.getStatusCode().value());
        verify(mailSender, never()).schedule(any(), any());
        verify(mailSender, never()).cancelScheduled(any());
    }

    // ===================== 交易信保留額度（spec §6） =====================

    /**
     * 行銷可用量為 0 時拒絕發送並回 409。
     *
     * <p>不可寄 0 封後回報成功——那會讓後台顯示「已發送」而實際上沒人收到。</p>
     */
    @Test
    void sendIsRejectedWhenNoMarketingQuota() {
        when(recipientService.recipients(null, null)).thenReturn(List.of("a@b.com", "c@b.com"));
        when(mailQuotaService.current()).thenReturn(quotaWithMarketing(0));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> svc.send("主旨", "# 內容", null, null, "now", null));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        // 一封都不能寄出。立即群發走的是 sendBatch(List)，不是 send(to,subject,html)——
        // 後者只有 sendTest() 會呼叫，用它斷言等於什麼都沒鎖住。
        verify(mailSender, never()).sendBatch(anyList());
        // 更關鍵的是不能留下 campaign 紀錄：留了就會在後台歷史裡出現一筆「寄了 0 人」的批次
        verify(campaignRepository, never()).save(any(Campaign.class));
    }

    /**
     * 重排（reschedule）同樣要受行銷可用額度截斷。
     *
     * <p><b>為什麼非有這條不可</b>：reschedule 看起來像「只是改時間」，實際上它會
     * 取消舊排程信後<b>重新寄出整批</b>。若這段檢查缺席或被改壞，它就是一條繞過
     * 保留額度的後門，而三個既有的 reschedule 測試全跑在額度充足之下，
     * 把整段檢查刪掉都不會變紅。</p>
     */
    @Test
    void rescheduleTruncatesToMarketingQuota() {
        Instant newAt = Instant.parse("2030-06-01T10:00:00Z");
        Campaign existing = new Campaign("舊主旨", "舊內文", "<p>舊</p>", null, null, "schedule",
            null, 5, "scheduled");
        when(campaignRepository.findById(21L)).thenReturn(Optional.of(existing));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(i -> i.getArgument(0));
        when(emailLogRepository.findByCampaignIdAndStatus(21L, "scheduled")).thenReturn(List.of());
        when(recipientService.recipients(null, null))
            .thenReturn(List.of("a@b.com", "b@b.com", "c@b.com", "d@b.com", "e@b.com"));
        when(mailQuotaService.current()).thenReturn(quotaWithMarketing(2));
        when(mailSender.schedule(any(), eq(newAt))).thenReturn("sched-x");

        CampaignService.SendResult r = svc.reschedule(21L, "新主旨", "新內文", null, null, newAt);

        assertEquals(2, r.recipientCount(), "只應重排額度允許的人數");
        assertEquals(3, r.skippedForQuota(), "縮減數必須回報");
        // 實際只對 2 人呼叫 provider——回傳值對了但仍寄 5 封的話等於檢查形同虛設
        verify(mailSender, times(2)).schedule(any(), eq(newAt));
        assertEquals(2, existing.getRecipientCount(), "campaign 應記錄實際寄送人數");
    }

    /** 重排時行銷可用量為 0 → 回 409，且完全不呼叫 provider（同 send 的理由） */
    @Test
    void rescheduleIsRejectedWhenNoMarketingQuota() {
        Campaign existing = new Campaign("舊主旨", "舊內文", "<p>舊</p>", null, null, "schedule",
            null, 2, "scheduled");
        when(campaignRepository.findById(22L)).thenReturn(Optional.of(existing));
        when(emailLogRepository.findByCampaignIdAndStatus(22L, "scheduled")).thenReturn(List.of());
        when(recipientService.recipients(null, null)).thenReturn(List.of("a@b.com", "c@b.com"));
        when(mailQuotaService.current()).thenReturn(quotaWithMarketing(0));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> svc.reschedule(22L, "新主旨", "新內文", null, null,
                Instant.parse("2030-06-01T10:00:00Z")));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(mailSender, never()).schedule(any(), any());
        // 舊 campaign 不可被改成任何新狀態：整批被拒時它應維持原樣等下次重排
        assertEquals("scheduled", existing.getStatus());
    }

    // ===================== 額度快取失效（連續兩批群發不得穿過同一份快照） =====================

    /**
     * 群發後必須讓額度快取失效。
     *
     * <p>額度檢查是無狀態的：每次只問一次外部快照、寄出後不做本地扣減。
     * {@code MailQuotaService} 的快取存活 60 秒，若群發後不主動失效，
     * 60 秒內的第二批群發會拿到<b>同一份</b>還沒扣掉第一批的快照而被放行——
     * 兩批 950 人可以在額度只剩 1000 的情況下全部寄出，保留給登入信的額度被吃光。</p>
     */
    @Test
    void sendInvalidatesQuotaCacheAfterSending() {
        when(recipientService.recipients(null, null)).thenReturn(List.of("a@b.com"));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(i -> i.getArgument(0));
        when(mailSender.sendBatch(anyList())).thenReturn("job-1");

        svc.send("主旨", "# 內容", null, null, "now", null);

        verify(mailQuotaService).invalidate();
    }

    /** 重排同樣實際寄出信件，寄完也必須讓額度快取失效 */
    @Test
    void rescheduleInvalidatesQuotaCacheAfterSending() {
        Instant newAt = Instant.parse("2030-06-01T10:00:00Z");
        Campaign existing = new Campaign("舊主旨", "舊內文", "<p>舊</p>", null, null, "schedule",
            null, 1, "scheduled");
        when(campaignRepository.findById(23L)).thenReturn(Optional.of(existing));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(i -> i.getArgument(0));
        when(emailLogRepository.findByCampaignIdAndStatus(23L, "scheduled")).thenReturn(List.of());
        when(recipientService.recipients(null, null)).thenReturn(List.of("a@b.com"));
        when(mailSender.schedule(any(), eq(newAt))).thenReturn("sched-y");

        svc.reschedule(23L, "新主旨", "新內文", null, null, newAt);

        verify(mailQuotaService).invalidate();
    }

    /** 額度不足被拒（409）時不算寄出，不需要也不應該去清額度快取 */
    @Test
    void rejectedSendDoesNotInvalidateQuotaCache() {
        when(recipientService.recipients(null, null)).thenReturn(List.of("a@b.com"));
        when(mailQuotaService.current()).thenReturn(quotaWithMarketing(0));

        assertThrows(ResponseStatusException.class,
            () -> svc.send("主旨", "# 內容", null, null, "now", null));

        verify(mailQuotaService, never()).invalidate();
    }

    /**
     * 收件人多於行銷可用量時縮減批量並回報縮減數。
     *
     * <p>`recipientCount` 必須是**實際寄送人數**而非原始人數：階段 E 的補寄
     * 會用它算差集，記成原始人數會讓被縮減的人被判定為「已寄但失敗」。</p>
     */
    @Test
    void sendTruncatesToMarketingQuotaAndReportsSkipped() {
        when(recipientService.recipients(null, null))
            .thenReturn(List.of("a@b.com", "b@b.com", "c@b.com", "d@b.com", "e@b.com"));
        when(mailQuotaService.current()).thenReturn(quotaWithMarketing(2));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(i -> i.getArgument(0));
        when(mailSender.sendBatch(anyList())).thenReturn("job-1");

        CampaignService.SendResult result = svc.send("主旨", "# 內容", null, null, "now", null);

        assertEquals(2, result.recipientCount(), "只應寄送額度允許的人數");
        assertEquals(3, result.skippedForQuota(), "縮減數必須回報，否則會被誤解為全部寄出");

        ArgumentCaptor<Campaign> saved = ArgumentCaptor.forClass(Campaign.class);
        verify(campaignRepository, atLeastOnce()).save(saved.capture());
        assertEquals(2, saved.getValue().getRecipientCount(), "應記錄實際寄送人數，供補寄算差集");
    }

    /** 額度充足時行為與原本完全相同，skippedForQuota 為 0 */
    @Test
    void sendIsUnchangedWhenQuotaIsAmple() {
        when(recipientService.recipients(null, null)).thenReturn(List.of("a@b.com", "c@b.com"));
        when(mailQuotaService.current()).thenReturn(quotaWithMarketing(1000));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(i -> i.getArgument(0));
        when(mailSender.sendBatch(anyList())).thenReturn("job-1");

        CampaignService.SendResult result = svc.send("主旨", "# 內容", null, null, "now", null);

        assertEquals(2, result.recipientCount());
        assertEquals(0, result.skippedForQuota());
    }
}
