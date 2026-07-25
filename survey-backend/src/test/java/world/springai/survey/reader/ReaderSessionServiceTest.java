package world.springai.survey.reader;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ReaderSessionService 行為測試：JWT 往返、篡改與過期拒絕、cookie 安全屬性 */
class ReaderSessionServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-25T12:00:00+08:00");
    /** 秘鑰需 ≥ 32 bytes（HS256 要求 256 bits） */
    private static final String SECRET = "test-secret-key-at-least-32-bytes-long!!";

    /** 建立以 https 為對外網址的服務（正式環境情境） */
    private ReaderSessionService httpsService() {
        return new ReaderSessionService(SECRET, 28, "https://news.example.com");
    }

    /** 簽發後應能讀回同一個 readerId */
    @Test
    void issuedJwtRoundTripsReaderId() {
        ReaderSessionService service = httpsService();

        String jwt = service.issueJwt(42L, NOW);
        Optional<Long> readerId = service.readReaderId(jwt, NOW.plusDays(1));

        assertEquals(Optional.of(42L), readerId);
    }

    /** 被篡改的 JWT 必須拒絕 */
    @Test
    void tamperedJwtIsRejected() {
        ReaderSessionService service = httpsService();
        String jwt = service.issueJwt(42L, NOW);

        // 改掉最後一個字元破壞簽章
        String tampered = jwt.substring(0, jwt.length() - 1) + (jwt.endsWith("A") ? "B" : "A");

        assertTrue(service.readReaderId(tampered, NOW).isEmpty());
    }

    /** 以別的秘鑰簽的 JWT 必須拒絕 */
    @Test
    void jwtSignedWithOtherSecretIsRejected() {
        String otherJwt = new ReaderSessionService(
            "another-secret-key-at-least-32-bytes!!!!", 28, "https://news.example.com")
            .issueJwt(42L, NOW);

        assertTrue(httpsService().readReaderId(otherJwt, NOW).isEmpty());
    }

    /** 過期的 JWT 必須拒絕 */
    @Test
    void expiredJwtIsRejected() {
        ReaderSessionService service = httpsService();
        String jwt = service.issueJwt(42L, NOW);

        assertTrue(service.readReaderId(jwt, NOW.plusDays(29)).isEmpty(), "28 天效期，第 29 天應失效");
    }

    /** 格式錯誤或空白的 JWT 一律拒絕，不拋例外 */
    @Test
    void malformedJwtIsRejected() {
        ReaderSessionService service = httpsService();

        assertTrue(service.readReaderId(null, NOW).isEmpty());
        assertTrue(service.readReaderId("", NOW).isEmpty());
        assertTrue(service.readReaderId("not-a-jwt", NOW).isEmpty());
    }

    /** session cookie 必須 httpOnly（防 XSS 竊取）、SameSite=Lax、path=/、帶有效期 */
    @Test
    void sessionCookieHasSecurityAttributes() {
        ResponseCookie cookie = httpsService().buildSessionCookie("dummy-jwt");

        assertEquals(ReaderSessionService.COOKIE_NAME, cookie.getName());
        assertTrue(cookie.isHttpOnly(), "必須 httpOnly，否則 XSS 可讀取 session");
        assertEquals("Lax", cookie.getSameSite());
        assertEquals("/", cookie.getPath());
        assertEquals(Duration.ofDays(28), cookie.getMaxAge());
    }

    /** 對外網址為 https 時 cookie 必須帶 Secure */
    @Test
    void secureFlagIsSetForHttpsBaseUrl() {
        assertTrue(httpsService().buildSessionCookie("dummy-jwt").isSecure());
    }

    /** 對外網址為 http（本機開發）時不得帶 Secure，否則瀏覽器會丟棄 cookie 導致永遠登不進去 */
    @Test
    void secureFlagIsOmittedForHttpBaseUrl() {
        ReaderSessionService local = new ReaderSessionService(SECRET, 28, "http://127.0.0.1:8080");

        assertFalse(local.buildSessionCookie("dummy-jwt").isSecure());
    }

    /** 清除用的 cookie 必須 maxAge=0 且值為空 */
    @Test
    void clearCookieExpiresImmediately() {
        ResponseCookie cookie = httpsService().buildClearCookie();

        assertEquals(ReaderSessionService.COOKIE_NAME, cookie.getName());
        assertEquals("", cookie.getValue());
        assertEquals(Duration.ZERO, cookie.getMaxAge());
    }
}
