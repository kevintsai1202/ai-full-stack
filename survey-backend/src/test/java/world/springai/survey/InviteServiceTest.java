package world.springai.survey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** InviteService 行為測試：對待確認名單寄邀請信（含 HMAC 確認連結）、記錄 email_log、統計成敗 */
class InviteServiceTest {

    private SurveyResponseRepository repository;
    private MailSender mailSender;
    private EmailLogRepository emailLogRepository;
    private UnsubscribeTokenService tokenService;
    private InviteService service;

    @BeforeEach
    void setUp() {
        repository = mock(SurveyResponseRepository.class);
        mailSender = mock(MailSender.class);
        emailLogRepository = mock(EmailLogRepository.class);
        tokenService = new UnsubscribeTokenService("test-secret");
        service = new InviteService(repository, mailSender, emailLogRepository,
            tokenService, "https://survey.example.com");
    }

    /** 建立一筆待確認名單資料 */
    private SurveyResponse pending(String email) {
        SurveyResponse r = new SurveyResponse();
        r.setEmail(email);
        r.setSource("exam");
        r.setConsent(false);
        return r;
    }

    /** 應對每位待確認者寄信，信中含該收件人的個人化 HMAC 確認連結 */
    @Test
    void sendsInviteWithPersonalConfirmLink() {
        when(repository.findBySourceAndConsentFalseAndUnsubscribedFalse("exam"))
            .thenReturn(List.of(pending("a@example.com"), pending("b@example.com")));
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        InviteService.InviteResult result = service.sendInvites("exam", null);

        assertEquals(2, result.recipientCount());
        assertEquals(2, result.accepted());
        assertEquals(0, result.failed());
        // 驗證 a@example.com 的信含其專屬確認連結（HMAC token）
        String expectedToken = tokenService.sign("a@example.com");
        verify(mailSender).send(eq("a@example.com"), anyString(),
            contains("/api/survey/confirm?email=a%40example.com&t=" + expectedToken));
    }

    /** 已寄過邀請（email_log type=invite status=sent）的人應跳過，不重複寄 */
    @Test
    void skipsAlreadyInvitedRecipients() {
        when(repository.findBySourceAndConsentFalseAndUnsubscribedFalse("exam"))
            .thenReturn(List.of(pending("a@example.com"), pending("b@example.com")));
        when(emailLogRepository.findByTypeAndStatus("invite", "sent"))
            .thenReturn(List.of(new EmailLog("A@example.com", "主旨", "invite", "msg-0", "sent", null)));
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        InviteService.InviteResult result = service.sendInvites("exam", null);

        assertEquals(1, result.recipientCount());
        assertEquals(1, result.accepted());
        assertEquals(1, result.alreadyInvited());
        verify(mailSender, org.mockito.Mockito.never()).send(eq("a@example.com"), anyString(), anyString());
        verify(mailSender).send(eq("b@example.com"), anyString(), anyString());
    }

    /** limit 應限制單次寄送數量，其餘計入 remaining 供分天寄送 */
    @Test
    void limitCapsSendCountAndReportsRemaining() {
        when(repository.findBySourceAndConsentFalseAndUnsubscribedFalse("exam"))
            .thenReturn(List.of(pending("a@example.com"), pending("b@example.com"), pending("c@example.com")));
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        InviteService.InviteResult result = service.sendInvites("exam", 2);

        assertEquals(2, result.recipientCount());
        assertEquals(2, result.accepted());
        assertEquals(1, result.remaining());
        verify(mailSender, org.mockito.Mockito.times(2)).send(anyString(), anyString(), anyString());
    }

    /** 每封信成功寄出後應寫入 type=invite、status=sent 的寄送記錄 */
    @Test
    void logsSentInvite() {
        when(repository.findBySourceAndConsentFalseAndUnsubscribedFalse("exam"))
            .thenReturn(List.of(pending("a@example.com")));
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-9");

        service.sendInvites("exam", null);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        assertEquals("invite", captor.getValue().getType());
        assertEquals("sent", captor.getValue().getStatus());
        assertEquals("msg-9", captor.getValue().getProviderMessageId());
    }

    /** 寄送失敗不中斷整批：計入 failed 並記錄 status=failed */
    @Test
    void failedSendIsCountedAndLogged() {
        when(repository.findBySourceAndConsentFalseAndUnsubscribedFalse("exam"))
            .thenReturn(List.of(pending("bad@example.com"), pending("ok@example.com")));
        when(mailSender.send(eq("bad@example.com"), anyString(), anyString()))
            .thenThrow(new RuntimeException("boom"));
        when(mailSender.send(eq("ok@example.com"), anyString(), anyString())).thenReturn("msg-2");

        InviteService.InviteResult result = service.sendInvites("exam", null);

        assertEquals(2, result.recipientCount());
        assertEquals(1, result.accepted());
        assertEquals(1, result.failed());
        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(l -> "failed".equals(l.getStatus())));
    }

    /** 邀請信內文以電子報為核心：技術討論、AI 新知、好康優惠三大好處都要提到 */
    @Test
    void inviteBodyMentionsBenefits() {
        when(repository.findBySourceAndConsentFalseAndUnsubscribedFalse("exam"))
            .thenReturn(List.of(pending("a@example.com")));
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        service.sendInvites("exam", null);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(anyString(), anyString(), html.capture());
        assertTrue(html.getValue().contains("深入的技術討論"), "應提到技術討論");
        assertTrue(html.getValue().contains("AI 新知"), "應提到 AI 新知與新技術");
        assertTrue(html.getValue().contains("優惠"), "應提到好康優惠");
    }

    /** 邀請信不以課程宣傳為主軸：內文不得出現課程名稱推銷 */
    @Test
    void inviteBodyDoesNotPromoteCourse() {
        when(repository.findBySourceAndConsentFalseAndUnsubscribedFalse("exam"))
            .thenReturn(List.of(pending("a@example.com")));
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        service.sendInvites("exam", null);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(anyString(), anyString(), html.capture());
        assertTrue(!html.getValue().contains("AI 賦能全端開發"), "不應以新課程宣傳為主軸");
    }
}
