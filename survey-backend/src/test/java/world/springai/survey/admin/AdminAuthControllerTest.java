package world.springai.survey.admin;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import world.springai.survey.reader.LoginAbuseGuard;
import world.springai.survey.reader.LoginToken;
import world.springai.survey.reader.LoginTokenService;

import jakarta.servlet.http.Cookie;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** admin 認證端點：白名單二次比對、用途隔離、cookie 種發 */
class AdminAuthControllerTest {

    private static final String SECRET = "admin-test-secret-at-least-32-bytes!!!!";

    /** token 有效且仍在白名單內：應種 cookie 並導向後台 */
    @Test
    void verifyIssuesCookieForAllowlistedEmail() {
        LoginTokenService tokenService = mock(LoginTokenService.class);
        when(tokenService.consume(eq("tok"), eq(LoginToken.PURPOSE_ADMIN), any()))
            .thenReturn(Optional.of("kevin@example.com"));

        AdminAuthController controller = newController(tokenService, "kevin@example.com");
        ResponseEntity<Void> response = controller.verifyLogin("tok");

        assertEquals(302, response.getStatusCode().value());
        assertEquals("/admin.html", response.getHeaders().getFirst(HttpHeaders.LOCATION));
        assertTrue(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE).startsWith("admin_session="));
    }

    /**
     * token 有效但該 email 已不在白名單：必須拒絕。
     * 白名單來自環境變數，可能在簽發後被調整（管理者換人），故 verify 端必須二次比對。
     */
    @Test
    void verifyRejectsEmailRemovedFromAllowlist() {
        LoginTokenService tokenService = mock(LoginTokenService.class);
        when(tokenService.consume(eq("tok"), eq(LoginToken.PURPOSE_ADMIN), any()))
            .thenReturn(Optional.of("former@example.com"));

        AdminAuthController controller = newController(tokenService, "kevin@example.com");
        ResponseEntity<Void> response = controller.verifyLogin("tok");

        assertEquals(302, response.getStatusCode().value());
        assertNull(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE), "不得種 cookie");
    }

    /** token 無效：導回登入頁且不種 cookie */
    @Test
    void verifyRejectsInvalidToken() {
        LoginTokenService tokenService = mock(LoginTokenService.class);
        when(tokenService.consume(any(), any(), any())).thenReturn(Optional.empty());

        AdminAuthController controller = newController(tokenService, "kevin@example.com");
        ResponseEntity<Void> response = controller.verifyLogin("bad");

        assertEquals(302, response.getStatusCode().value());
        assertNull(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
    }

    /**
     * requestLogin 防枚舉：不論傳入的 email 是否在白名單內，回應都必須完全相同。
     * 這是本端點的核心安全設計——回應若有任何差異，這個端點就變成「查詢誰是
     * 管理者」的工具，因此以兩次呼叫（白名單內／白名單外）斷言回傳值相等。
     */
    @Test
    void requestLoginRespondsIdenticallyRegardlessOfAllowlistMembership() {
        LoginTokenService tokenService = mock(LoginTokenService.class);
        AdminAuthController controller = newController(tokenService, "kevin@example.com");

        Map<String, Boolean> allowlistedResponse =
            controller.requestLogin(new AdminAuthController.LoginRequest("kevin@example.com"),
                new MockHttpServletRequest());
        Map<String, Boolean> nonAllowlistedResponse =
            controller.requestLogin(new AdminAuthController.LoginRequest("stranger@example.com"),
                new MockHttpServletRequest());

        assertEquals(allowlistedResponse, nonAllowlistedResponse, "白名單內外的回應不得有差異");
    }

    /**
     * 登出必須種出清除用 cookie：admin_session 值清空且 Max-Age=0。
     * 登出是唯一的 session 終止機制，若這裡沒種對 cookie，使用者以為登出了但
     * session 其實還在。
     */
    @Test
    void logoutSetsClearingCookie() {
        LoginTokenService tokenService = mock(LoginTokenService.class);
        AdminAuthController controller = newController(tokenService, "kevin@example.com");

        ResponseEntity<Void> response = controller.logout();

        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertTrue(setCookie != null && setCookie.startsWith("admin_session="), "應清空 admin_session 值");
        assertTrue(setCookie.contains("Max-Age=0"), "應立即失效才是真正的登出");
    }

    /** 帶有效 admin_session cookie 且該 email 仍在白名單內：回 200 並附上 email */
    @Test
    void meReturnsEmailForValidSessionInAllowlist() {
        AdminSessionService sessionService = new AdminSessionService(SECRET, 7, "https://admin.example.com");
        AdminAuthController controller = newController(mock(LoginTokenService.class), "kevin@example.com",
            sessionService);
        OffsetDateTime now = OffsetDateTime.now();
        String jwt = sessionService.issueJwt("kevin@example.com", now);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AdminSessionService.COOKIE_NAME, jwt));

        ResponseEntity<Map<String, Object>> response = controller.me(request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("kevin@example.com", response.getBody().get("email"));
    }

    /** 未帶 admin_session cookie：視為未登入，回 401 */
    @Test
    void meReturnsUnauthorizedWhenNoCookie() {
        AdminAuthController controller = newController(mock(LoginTokenService.class), "kevin@example.com");

        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<Map<String, Object>> response = controller.me(request);

        assertEquals(401, response.getStatusCode().value());
    }

    /**
     * JWT 有效但該 email 已被移出白名單：必須回 401。
     * 白名單來自環境變數，管理者換人後舊 JWT 在效期內仍能通過簽章驗證，若不
     * 二次比對白名單，被撤下的管理者可以繼續使用後台直到 token 自然過期。
     */
    @Test
    void meReturnsUnauthorizedWhenEmailRemovedFromAllowlist() {
        AdminSessionService sessionService = new AdminSessionService(SECRET, 7, "https://admin.example.com");
        AdminAuthController controller = newController(mock(LoginTokenService.class), "kevin@example.com",
            sessionService);
        OffsetDateTime now = OffsetDateTime.now();
        String jwt = sessionService.issueJwt("former@example.com", now);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AdminSessionService.COOKIE_NAME, jwt));

        ResponseEntity<Map<String, Object>> response = controller.me(request);

        assertEquals(401, response.getStatusCode().value());
    }

    /**
     * IP／全站配額用盡時，仍須回與正常情況完全相同的回應，且<b>不得</b>寄出登入信。
     *
     * <p>節流結果一旦反映在回應上（例如多一個 {@code throttled} 欄位），這個端點就
     * 重新變回可枚舉的——攻擊者只要看哪個 email 讓計數往前跳就知道誰是管理者。
     * 讀者端刻意回報 {@code throttled}（使用者需要知道要等一下），本端點刻意不回報。</p>
     */
    @Test
    void requestLoginStaysSilentAndIdenticalWhenAbuseGuardRejects() {
        AdminLoginMailService mailService = mock(AdminLoginMailService.class);
        LoginAbuseGuard guard = mock(LoginAbuseGuard.class);
        AdminAuthController controller = newController(mock(LoginTokenService.class),
            "kevin@example.com", new AdminSessionService(SECRET, 7, "https://admin.example.com"),
            mailService, guard);

        when(guard.tryAcquire(any(), any())).thenReturn(true);
        Map<String, Boolean> allowedResponse = controller.requestLogin(
            new AdminAuthController.LoginRequest("kevin@example.com"), new MockHttpServletRequest());

        when(guard.tryAcquire(any(), any())).thenReturn(false);
        Map<String, Boolean> throttledResponse = controller.requestLogin(
            new AdminAuthController.LoginRequest("kevin@example.com"), new MockHttpServletRequest());

        assertEquals(allowedResponse, throttledResponse, "被節流與未被節流的回應不得有差異");
        // 放行一次、擋下一次：寄信服務總共只能被呼叫到一次
        verify(mailService, times(1)).sendIfAdmin(eq("kevin@example.com"), any());
    }

    /** 建構受測 controller，白名單內容可指定；session 服務用固定測試密鑰 */
    private AdminAuthController newController(LoginTokenService tokenService, String allowlist) {
        return newController(tokenService, allowlist,
            new AdminSessionService(SECRET, 7, "https://admin.example.com"));
    }

    /** 建構受測 controller，白名單內容與 session 服務皆可指定 */
    private AdminAuthController newController(LoginTokenService tokenService, String allowlist,
                                              AdminSessionService sessionService) {
        return newController(tokenService, allowlist, sessionService,
            mock(AdminLoginMailService.class), alwaysAllowingGuard());
    }

    /** 建構受測 controller，寄信服務與濫用守門也可指定（節流相關案例使用） */
    private AdminAuthController newController(LoginTokenService tokenService, String allowlist,
                                              AdminSessionService sessionService,
                                              AdminLoginMailService mailService,
                                              LoginAbuseGuard abuseGuard) {
        AdminAllowlist list = new AdminAllowlist(allowlist);
        return new AdminAuthController(
            mailService, tokenService, list,
            sessionService, new AdminSessionAccess(sessionService), abuseGuard);
    }

    /** 預設守門一律放行，讓其他案例的行為與加入節流前完全相同 */
    private LoginAbuseGuard alwaysAllowingGuard() {
        LoginAbuseGuard guard = mock(LoginAbuseGuard.class);
        when(guard.tryAcquire(any(), any())).thenReturn(true);
        return guard;
    }
}
