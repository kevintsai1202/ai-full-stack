package world.springai.survey.admin;

import org.junit.jupiter.api.Test;
import world.springai.survey.mail.MailSender;
import world.springai.survey.reader.LoginToken;
import world.springai.survey.reader.LoginTokenService;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** admin 登入信：只寄給白名單、必須以 admin 用途簽發 token，且受同一組 per-email 節流保護 */
class AdminLoginMailServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-06T12:00:00+08:00");

    /** 白名單內的 email 應收到信，且 token 用途為 admin */
    @Test
    void sendsToAllowlistedEmailWithAdminPurpose() throws Exception {
        LoginTokenService tokenService = mock(LoginTokenService.class);
        MailSender mailSender = mock(MailSender.class);
        when(tokenService.issue(eq("kevin@example.com"), eq(LoginToken.PURPOSE_ADMIN), any()))
            .thenReturn("raw-token");

        newService(tokenService, mailSender).sendIfAdmin("kevin@example.com", NOW);

        verify(tokenService).issue(eq("kevin@example.com"), eq(LoginToken.PURPOSE_ADMIN), any());
        verify(mailSender).send(eq("kevin@example.com"), any(), any());
    }

    /** 非白名單的 email 不得寄信，也不得簽發任何 token */
    @Test
    void doesNotSendToUnlistedEmail() throws Exception {
        LoginTokenService tokenService = mock(LoginTokenService.class);
        MailSender mailSender = mock(MailSender.class);

        newService(tokenService, mailSender).sendIfAdmin("attacker@example.com", NOW);

        verify(tokenService, never()).issue(any(), any(), any());
        verify(mailSender, never()).send(any(), any(), any());
    }

    /**
     * 已達 per-email 節流上限時，既不得寄信也<b>不得簽發 token</b>。
     *
     * <p>沒有這道檢查時，只要猜中管理者 email 就能無限觸發寄信：信箱被灌爆事小，
     * 吃光交易信額度會讓讀者無法登入（產品故障），{@code login_token} 也會無限增長。
     * 特別斷言「不得 issue」——若把節流放在 issue 之後，每次請求仍會寫一列 token，
     * 節流形同虛設。</p>
     */
    @Test
    void doesNotSendWhenEmailIsThrottled() throws Exception {
        LoginTokenService tokenService = mock(LoginTokenService.class);
        MailSender mailSender = mock(MailSender.class);
        when(tokenService.isThrottled(eq("kevin@example.com"), any())).thenReturn(true);

        newService(tokenService, mailSender).sendIfAdmin("kevin@example.com", NOW);

        verify(tokenService, never()).issue(any(), any(), any());
        verify(mailSender, never()).send(any(), any(), any());
    }

    /** 寄信失敗不得往外拋例外，否則端點會回 500 而洩漏該 email 是管理者 */
    @Test
    void swallowsMailFailure() throws Exception {
        LoginTokenService tokenService = mock(LoginTokenService.class);
        MailSender mailSender = mock(MailSender.class);
        when(tokenService.issue(any(), any(), any())).thenReturn("raw-token");
        when(mailSender.send(any(), any(), any())).thenThrow(new RuntimeException("smtp down"));

        newService(tokenService, mailSender).sendIfAdmin("kevin@example.com", NOW);  // 不應拋例外
    }

    /** 建構受測服務；白名單固定含 kevin@example.com */
    private AdminLoginMailService newService(LoginTokenService tokenService, MailSender mailSender) {
        return new AdminLoginMailService(
            new AdminAllowlist("kevin@example.com"), tokenService, mailSender,
            new AdminSiteLinks("https://admin.example.com", ""));
    }
}
