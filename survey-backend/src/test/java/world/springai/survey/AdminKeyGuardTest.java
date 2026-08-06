package world.springai.survey;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.admin.AdminAllowlist;
import world.springai.survey.admin.AdminSessionAccess;
import world.springai.survey.admin.AdminSessionService;

import jakarta.servlet.http.Cookie;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** AdminKeyGuard：金鑰與 admin session 兩條路徑皆可通過，皆無則 401 */
class AdminKeyGuardTest {

    private static final String SECRET = "admin-test-secret-at-least-32-bytes!!!!";
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-06T12:00:00+08:00");

    /** 正確金鑰應通過（既有 78 處呼叫點與 9 支腳本的行為必須維持） */
    @Test
    void correctApiKeyPasses() {
        assertDoesNotThrow(() -> newGuard().verify("secret-key"));
    }

    /** 帶有效 admin_session cookie 時，即使未給金鑰也應通過 */
    @Test
    void validSessionCookiePasses() {
        AdminSessionService session = new AdminSessionService(SECRET, 7, "https://admin.example.com");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AdminSessionService.COOKIE_NAME,
            session.issueJwt("kevin@example.com", NOW)));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        try {
            assertDoesNotThrow(() -> newGuard().verify(null));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    /** 金鑰錯誤且無 session：必須回 401 */
    @Test
    void wrongKeyWithoutSessionFails() {
        RequestContextHolder.resetRequestAttributes();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> newGuard().verify("wrong-key"));

        assertEquals(401, ex.getStatusCode().value());
    }

    /**
     * 白名單過濾：JWT 簽章有效且未過期，但 email 已不在白名單時仍須回 401。
     *
     * <p>驗證情境：管理者換人後，環境變數白名單已更新移除舊管理者，但舊管理者手上
     * 的 JWT 仍在 7 天效期內。若 guard 只驗簽章不比對白名單，被撤權者可繼續打
     * 全部 78 個管理端點直到 token 自然過期。本測試使用真實簽發的 JWT（簽章與效期
     * 皆合法），搭配只含其他 email 的白名單，證明擋下它的是白名單比對而非簽章失敗。</p>
     */
    @Test
    void validJwtButEmailNotInAllowlistFails() {
        AdminSessionService session = new AdminSessionService(SECRET, 7, "https://admin.example.com");
        // 真實簽發、簽章與效期皆合法的 JWT，subject 為已被撤權的舊管理者
        String jwtForRevokedAdmin = session.issueJwt("former@example.com", NOW);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AdminSessionService.COOKIE_NAME, jwtForRevokedAdmin));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        try {
            // 白名單只含其他 email，不含 former@example.com
            AdminKeyGuard guard = new AdminKeyGuard("secret-key", new AdminSessionAccess(session),
                new AdminAllowlist("kevin@example.com"));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> guard.verify(null));

            assertEquals(401, ex.getStatusCode().value());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    /** 建構受測 guard；白名單含測試用 email */
    private AdminKeyGuard newGuard() {
        return new AdminKeyGuard("secret-key",
            new AdminSessionAccess(new AdminSessionService(SECRET, 7, "https://admin.example.com")),
            new AdminAllowlist("kevin@example.com"));
    }
}
