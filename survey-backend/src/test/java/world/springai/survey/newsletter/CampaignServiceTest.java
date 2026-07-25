package world.springai.survey.newsletter;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import world.springai.survey.audience.RecipientService;
import world.springai.survey.audience.UnsubscribeTokenService;
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.EmailTemplate;
import world.springai.survey.mail.MailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
    private final UnsubscribeTokenService tokenService = new UnsubscribeTokenService("secret");

    private final CampaignService svc = new CampaignService(
        mailSender, recipientService, campaignRepository, emailLogRepository,
        markdownRenderer, emailTemplate, tokenService, "https://api.example.com");

    /** 立即發送：呼叫 sendBatch，每封 html 含該收件人的退訂連結，campaign 記為 sent、accepted=2 */
    @Test
    void immediateSendUsesBatchWithPersonalizedLinks() {
        when(recipientService.recipients(null, null)).thenReturn(List.of("a@x.com", "b@x.com"));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(i -> i.getArgument(0));
        when(mailSender.sendBatch(anyList())).thenReturn("job-1");

        CampaignService.SendResult r = svc.send("主旨", "# 內文", null, null, "now", null);

        assertEquals(2, r.recipientCount());
        assertEquals(2, r.accepted());
        assertEquals(0, r.failed());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MailSender.Email>> captor = ArgumentCaptor.forClass(List.class);
        verify(mailSender).sendBatch(captor.capture());
        List<MailSender.Email> sent = captor.getValue();
        assertEquals(2, sent.size());
        assertTrue(sent.get(0).html().contains("email=a%40x.com"), sent.get(0).html());
        assertTrue(sent.get(1).html().contains("email=b%40x.com"), sent.get(1).html());
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
}
