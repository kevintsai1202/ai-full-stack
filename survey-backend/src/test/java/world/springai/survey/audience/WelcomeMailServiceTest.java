package world.springai.survey.audience;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.EmailTemplate;
import world.springai.survey.mail.MailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    /**
     * 寄送成功應寫入 status=sent 並帶 provider id，且信件頁腳必須含退訂連結。
     *
     * <p>linkBuilder 是純 mock，若不 stub，unsubscribeLink() 回傳 null，
     * 頁腳的 href 實際上會是 "null"——若日後有人拿掉
     * {@code linkBuilder.unsubscribeLink(email)} 這行呼叫，寄出的行銷信
     * 就會沒有退訂連結（法遵問題），但只看 EmailLog.status 的測試發現不了，
     * 所以這裡直接 capture 寄出的 HTML 內容比對。</p>
     */
    @Test
    void successLogsSent() {
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("zsend-id-1");
        when(linkBuilder.unsubscribeLink("user@example.com")).thenReturn("UNSUB_LINK_MARKER");

        svc.sendWelcome("user@example.com");

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        EmailLog saved = captor.getValue();
        assertEquals("sent", saved.getStatus());
        assertEquals("zsend-id-1", saved.getProviderMessageId());
        assertEquals("user@example.com", saved.getRecipient());

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(anyString(), anyString(), html.capture());
        assertTrue(html.getValue().contains("UNSUB_LINK_MARKER"), "歡迎信頁腳必須含退訂連結");
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
