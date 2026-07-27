package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import world.springai.survey.ReaderSiteLinks;
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
            new ReaderSiteLinks("https://reader.example.com"));
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
        assertTrue(html.getValue().contains("https://reader.example.com/api/reader/login/verify?t=RAW-TOKEN-123"),
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

    /**
     * 站外／開放式轉址的各種變形都必須被丟棄。
     *
     * <p>逐一驗證的案例與理由：</p>
     * <ul>
     *   <li>{@code https://evil.example.com/steal}：絕對網址，最基本的案例，
     *       既有 startsWith("/") 檢查就能擋下。</li>
     *   <li>{@code //evil.example.com}：protocol-relative 網址，舊版三行防線
     *       原本就是為了擋這個而寫的，必須繼續守住。</li>
     *   <li>{@code /\evil.example.com}：本次修正的主角。字面上以單一 / 開頭、
     *       不是以 // 開頭，舊版字串比對會誤判為安全；但瀏覽器對 http/https
     *       這類 special scheme 會把 {@code \} 當成 {@code /}，實際等同
     *       {@code //evil.example.com}，會導去外部網域。</li>
     *   <li>{@code \\evil.example.com}：雙反斜線變體，正規化後同樣變成
     *       {@code //evil.example.com}，必須一併擋下。</li>
     *   <li>{@code /path\r\nSet-Cookie:x}：直接用真正的 CR/LF 字元（而非
     *       {@code %0D%0A} 編碼字串）來驗證控制字元防線。之所以不用編碼後的
     *       字串，是因為 {@code %0D%0A} 只是文字上的百分比符號，並不含真正的
     *       控制字元，本來就不會觸發 header injection 風險，也不會被
     *       isSafeRedirect 的控制字元檢查攔下（這層防線防的是「輸入本身含
     *       控制字元」，不是防「輸入看起來像編碼過的控制字元」）；用真正的
     *       {@code \r\n} 字元才能驗證到目前程式碼真正要防的風險。</li>
     * </ul>
     */
    @Test
    void externalRedirectIsRejected() {
        when(tokenService.isThrottled(anyString(), any())).thenReturn(false);
        when(tokenService.issue(anyString(), any())).thenReturn("TOK");
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        String[] unsafeRedirects = {
            "https://evil.example.com/steal",
            "//evil.example.com",
            "/\\evil.example.com",
            "\\\\evil.example.com",
            "/path\r\nSet-Cookie:x"
        };

        for (String redirect : unsafeRedirects) {
            service.sendLoginLink("user@example.com", redirect, NOW);

            ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
            verify(mailSender, org.mockito.Mockito.atLeastOnce())
                .send(anyString(), anyString(), html.capture());
            String sentHtml = html.getValue();
            assertFalse(sentHtml.contains("evil.example.com"),
                "站外 redirect 必須被丟棄：" + redirect);
            assertFalse(sentHtml.contains("redirect="),
                "無效 redirect 不應出現在連結中：" + redirect);
        }
    }
}
