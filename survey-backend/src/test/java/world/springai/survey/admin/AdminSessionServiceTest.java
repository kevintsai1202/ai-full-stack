package world.springai.survey.admin;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AdminSessionService：JWT 往返、過期與篡改拒絕、cookie 安全屬性 */
class AdminSessionServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-06T12:00:00+08:00");
    /** HS256 要求秘鑰 ≥ 32 bytes */
    private static final String SECRET = "admin-test-secret-at-least-32-bytes!!!!";

    private AdminSessionService httpsService() {
        return new AdminSessionService(SECRET, 7, "https://admin.example.com");
    }

    /** 簽發後應能讀回同一個 email */
    @Test
    void issuedJwtRoundTripsEmail() {
        AdminSessionService service = httpsService();

        String jwt = service.issueJwt("kevin@example.com", NOW);

        assertEquals(Optional.of("kevin@example.com"), service.readEmail(jwt, NOW.plusDays(1)));
    }

    /** 超過效期的 JWT 一律視為未登入 */
    @Test
    void expiredJwtIsRejected() {
        AdminSessionService service = httpsService();

        String jwt = service.issueJwt("kevin@example.com", NOW);

        assertTrue(service.readEmail(jwt, NOW.plusDays(8)).isEmpty());
    }

    /**
     * <b>spec §3.2「兩把秘鑰刻意分離」在 code 層面唯一的可執行證明</b>：
     * 以另一把秘鑰簽出的 JWT，即使結構完全合法、未過期，也必須被拒。
     *
     * <p>沒有這個測試時，「{@code ADMIN_JWT_SECRET} 與 {@code READER_JWT_SECRET}
     * 不可相同」只是一句設定檔註解——把驗證改成不檢查簽章、或兩邊共用同一把秘鑰，
     * 既有測試（都用同一個 SECRET 簽發與驗證）全部照樣綠。
     * 它同時是 {@code DeploymentSecretValidator} 新增 ADMIN_JWT_SECRET 檢查的另一半：
     * 那邊擋住「設成同一把」，這邊證明「不同把就進不來」。</p>
     */
    @Test
    void jwtSignedWithDifferentSecretIsRejected() {
        // 模擬讀者站那把秘鑰：長度合法、格式合法，只是不同把
        AdminSessionService foreignIssuer =
            new AdminSessionService("reader-jwt-secret-at-least-32-bytes!!!", 7, "https://admin.example.com");
        String foreignJwt = foreignIssuer.issueJwt("kevin@example.com", NOW);

        // 先證明這枚 token 本身是好的（用簽它的那把秘鑰讀得回來），
        // 否則本測試可能因為 token 根本是壞的而假通過
        assertEquals(Optional.of("kevin@example.com"), foreignIssuer.readEmail(foreignJwt, NOW));

        assertTrue(httpsService().readEmail(foreignJwt, NOW).isEmpty(),
            "以不同秘鑰簽發的 JWT 必須被拒——兩把秘鑰分離的保護才成立");
    }

    /** 被篡改的 JWT 一律視為未登入，且不得拋出例外 */
    @Test
    void tamperedJwtIsRejected() {
        AdminSessionService service = httpsService();

        String jwt = service.issueJwt("kevin@example.com", NOW);

        assertTrue(service.readEmail(jwt + "x", NOW).isEmpty());
        assertTrue(service.readEmail("not-a-jwt", NOW).isEmpty());
    }

    /** cookie 必須 httpOnly、SameSite=Lax，https 站台需帶 Secure */
    @Test
    void cookieCarriesSecurityAttributes() {
        ResponseCookie cookie = httpsService().buildSessionCookie("token");

        assertEquals("admin_session", cookie.getName());
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.isSecure());
        assertEquals("Lax", cookie.getSameSite());
    }

    /** http 站台（本機開發）不得帶 Secure，否則瀏覽器會丟棄 cookie */
    @Test
    void plainHttpSiteOmitsSecureFlag() {
        AdminSessionService service = new AdminSessionService(SECRET, 7, "http://127.0.0.1:8080");

        assertFalse(service.buildSessionCookie("token").isSecure());
    }

    /**
     * 補上計畫遺漏的登出測試：buildClearCookie() 是登出功能唯一機制，
     * 若 maxAge 沒設成 0 或值寫錯，登出會靜默失效（cookie 其實還在，使用者卻以為登出了）。
     * 驗證 cookie 名稱、maxAge 為零、值為空字串，並保有與 session cookie 相同的安全屬性。
     */
    @Test
    void buildClearCookieExpiresSessionImmediately() {
        ResponseCookie cookie = httpsService().buildClearCookie();

        assertEquals("admin_session", cookie.getName());
        assertTrue(cookie.getMaxAge().isZero(), "maxAge 必須為 0，否則瀏覽器不會立即清除 cookie");
        assertEquals("", cookie.getValue());
        assertTrue(cookie.isHttpOnly(), "必須保持 httpOnly，與 session cookie 屬性一致");
        assertEquals("Lax", cookie.getSameSite(), "必須保持 SameSite=Lax，與 session cookie 屬性一致");
    }
}
