package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.MailSender;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** LoginMailService 行為測試：magic link 內容、節流、失敗回報、不含退訂連結 */
class LoginMailServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-25T12:00:00+08:00");

    private LoginTokenService tokenService;
    private MailSender mailSender;
    private EmailLogRepository emailLogRepository;
    private LoginMailService service;

    @BeforeEach
    void setUp() {
        tokenService = mock(LoginTokenService.class);
        mailSender = mock(MailSender.class);
        emailLogRepository = mock(EmailLogRepository.class);
        service = new LoginMailService(tokenService, mailSender, emailLogRepository,
            "https://news.example.com");
    }

    /** 正常寄送：信中含帶 token 的登入連結 */
    @Test
    void sendsMailWithMagicLink() {
        when(tokenService.isThrottled("user@example.com", NOW)).thenReturn(false);
        when(tokenService.issue("user@example.com", NOW)).thenReturn("RAW-TOKEN-123");
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        LoginMailService.SendResult result = service.sendLoginLink("user@example.com", null, NOW);

        assertTrue(result.sent());
        assertFalse(result.throttled());

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(eq("user@example.com"), anyString(), html.capture());
        assertTrue(html.getValue().contains("https://news.example.com/api/reader/login/verify?t=RAW-TOKEN-123"),
            "信中必須含帶 token 的登入連結");
    }

    /** 交易信不得含退訂連結：讀者退訂行銷信後仍須能登入看已解鎖的文章 */
    @Test
    void loginMailDoesNotContainUnsubscribeLink() {
        when(tokenService.isThrottled(anyString(), any())).thenReturn(false);
        when(tokenService.issue(anyString(), any())).thenReturn("RAW-TOKEN-123");
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        service.sendLoginLink("user@example.com", null, NOW);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(anyString(), anyString(), html.capture());
        assertFalse(html.getValue().contains("unsubscribe"), "登入信是交易信，不得含退訂連結");
        assertFalse(html.getValue().contains("取消訂閱"), "登入信是交易信，不得含退訂字樣");
    }

    /** redirect 參數要帶進連結並做 URL 編碼 */
    @Test
    void redirectIsAppendedAndEncoded() {
        when(tokenService.isThrottled(anyString(), any())).thenReturn(false);
        when(tokenService.issue(anyString(), any())).thenReturn("TOK");
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        service.sendLoginLink("user@example.com", "/r/news/hello-world", NOW);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(anyString(), anyString(), html.capture());
        assertTrue(html.getValue().contains("redirect=%2Fr%2Fnews%2Fhello-world"),
            "redirect 必須經過 URL 編碼");
    }

    /** 節流時不簽發 token、不寄信，並回報 throttled */
    @Test
    void throttledRequestDoesNotSendOrIssueToken() {
        when(tokenService.isThrottled("user@example.com", NOW)).thenReturn(true);

        LoginMailService.SendResult result = service.sendLoginLink("user@example.com", null, NOW);

        assertFalse(result.sent());
        assertTrue(result.throttled());
        verify(tokenService, never()).issue(anyString(), any());
        verify(mailSender, never()).send(anyString(), anyString(), anyString());
    }

    /** 寄送成功要寫 email_log，type=login */
    @Test
    void logsSuccessfulSend() {
        when(tokenService.isThrottled(anyString(), any())).thenReturn(false);
        when(tokenService.issue(anyString(), any())).thenReturn("TOK");
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-9");

        service.sendLoginLink("user@example.com", null, NOW);

        ArgumentCaptor<EmailLog> logCaptor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(logCaptor.capture());
        assertEquals(LoginMailService.LOG_TYPE, logCaptor.getValue().getType());
        assertEquals("sent", logCaptor.getValue().getStatus());
    }

    /**
     * 寄送失敗必須回報 sent=false（與 WelcomeMailService 吞例外的做法相反）。
     * 讀者正在等這封信，顯示成功假象會讓他一直重試。
     */
    @Test
    void failedSendIsReportedAndLogged() {
        when(tokenService.isThrottled(anyString(), any())).thenReturn(false);
        when(tokenService.issue(anyString(), any())).thenReturn("TOK");
        when(mailSender.send(anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("provider down"));

        LoginMailService.SendResult result = service.sendLoginLink("user@example.com", null, NOW);

        assertFalse(result.sent(), "寄送失敗不得回報成功");
        assertFalse(result.throttled());

        ArgumentCaptor<EmailLog> logCaptor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(logCaptor.capture());
        assertEquals("failed", logCaptor.getValue().getStatus());
    }

    /** 站外 redirect 必須被丟棄，避免變成開放式轉址 */
    @Test
    void externalRedirectIsRejected() {
        when(tokenService.isThrottled(anyString(), any())).thenReturn(false);
        when(tokenService.issue(anyString(), any())).thenReturn("TOK");
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        service.sendLoginLink("user@example.com", "https://evil.example.com/steal", NOW);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(anyString(), anyString(), html.capture());
        assertFalse(html.getValue().contains("evil.example.com"), "站外 redirect 必須被丟棄");
        assertFalse(html.getValue().contains("redirect="), "無效 redirect 不應出現在連結中");
    }
}
