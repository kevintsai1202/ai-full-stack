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

    private final CampaignService svc = new CampaignService(
        mailSender, recipientService, campaignRepository, emailLogRepository,
        markdownRenderer, emailTemplate, linkBuilder, mailQuotaService,
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
            markdownRenderer, emailTemplate, linkBuilder, mailQuotaService,
            "https://news.example.com/");
        when(campaignRepository.findBySlug("slash")).thenReturn(Optional.empty());
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(i -> i.getArgument(0));

        CampaignService.PublishResult r = withSlash.publish("主旨", "內文",
            Campaign.TIER_BASIC, null, "slash", null);

        assertEquals("https://news.example.com/r/news/slash", r.url());
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
