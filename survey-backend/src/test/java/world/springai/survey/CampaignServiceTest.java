package world.springai.survey;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import world.springai.survey.audience.RecipientService;
import world.springai.survey.audience.UnsubscribeTokenService;
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.EmailTemplate;
import world.springai.survey.mail.MailSender;
import world.springai.survey.newsletter.Campaign;
import world.springai.survey.newsletter.CampaignRepository;
import world.springai.survey.newsletter.CampaignService;
import world.springai.survey.newsletter.MarkdownRenderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
