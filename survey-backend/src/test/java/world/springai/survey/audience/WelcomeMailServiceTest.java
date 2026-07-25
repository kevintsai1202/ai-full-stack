package world.springai.survey.audience;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.EmailTemplate;
import world.springai.survey.mail.MailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 歡迎信服務測試：成功記 sent、失敗記 failed 且不拋例外 */
class WelcomeMailServiceTest {

    private final MailSender mailSender = mock(MailSender.class);
    private final EmailLogRepository emailLogRepository = mock(EmailLogRepository.class);
    private final EmailTemplate emailTemplate = new EmailTemplate(); // 使用真實模板，驗證外框套用正確
    // 連結格式已由 SubscriptionLinkBuilderTest 鎖住，這裡只 stub 固定回傳值，
    // 不重複斷言連結字串正確性
    private final SubscriptionLinkBuilder linkBuilder = mock(SubscriptionLinkBuilder.class);
    private final WelcomeMailService svc =
        new WelcomeMailService(mailSender, emailLogRepository, emailTemplate, linkBuilder);

    /** 寄送成功應寫入 status=sent 並帶 provider id */
    @Test
    void successLogsSent() {
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("zsend-id-1");

        svc.sendWelcome("user@example.com");

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        EmailLog saved = captor.getValue();
        assertEquals("sent", saved.getStatus());
        assertEquals("zsend-id-1", saved.getProviderMessageId());
        assertEquals("user@example.com", saved.getRecipient());
    }

    /** 寄送丟例外時不應向上拋，且寫入 status=failed */
    @Test
    void failureLogsFailedAndDoesNotThrow() {
        when(mailSender.send(anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("boom"));

        svc.sendWelcome("user@example.com"); // 不應拋例外

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        assertEquals("failed", captor.getValue().getStatus());
    }
}
