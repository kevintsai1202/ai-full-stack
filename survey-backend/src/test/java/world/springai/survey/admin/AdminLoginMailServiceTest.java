package world.springai.survey.admin;

import org.junit.jupiter.api.Test;
import world.springai.survey.mail.MailSender;
import world.springai.survey.reader.LoginToken;
import world.springai.survey.reader.LoginTokenService;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** admin 登入信：只寄給白名單，且必須以 admin 用途簽發 token */
class AdminLoginMailServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-06T12:00:00+08:00");

    /** 白名單內的 email 應收到信，且 token 用途為 admin */
    @Test
    void sendsToAllowlistedEmailWithAdminPurpose() throws Exception {
        LoginTokenService tokenService = mock(LoginTokenService.class);
        MailSender mailSender = mock(MailSender.class);
        when(tokenService.issue(eq("kevin@example.com"), eq(LoginToken.PURPOSE_ADMIN), any()))
            .thenReturn("raw-token");

        AdminLoginMailService service = new AdminLoginMailService(
            new AdminAllowlist("kevin@example.com"), tokenService, mailSender,
            new AdminSiteLinks("https://admin.example.com"));

        service.sendIfAdmin("kevin@example.com", NOW);

        verify(tokenService).issue(eq("kevin@example.com"), eq(LoginToken.PURPOSE_ADMIN), any());
        verify(mailSender).send(eq("kevin@example.com"), any(), any());
    }

    /** 非白名單的 email 不得寄信，也不得簽發任何 token */
    @Test
    void doesNotSendToUnlistedEmail() throws Exception {
        LoginTokenService tokenService = mock(LoginTokenService.class);
        MailSender mailSender = mock(MailSender.class);

        AdminLoginMailService service = new AdminLoginMailService(
            new AdminAllowlist("kevin@example.com"), tokenService, mailSender,
            new AdminSiteLinks("https://admin.example.com"));

        service.sendIfAdmin("attacker@example.com", NOW);

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

        AdminLoginMailService service = new AdminLoginMailService(
            new AdminAllowlist("kevin@example.com"), tokenService, mailSender,
            new AdminSiteLinks("https://admin.example.com"));

        service.sendIfAdmin("kevin@example.com", NOW);  // 不應拋例外
    }
}
