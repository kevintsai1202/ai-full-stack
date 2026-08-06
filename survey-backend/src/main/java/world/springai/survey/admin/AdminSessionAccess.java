package world.springai.survey.admin;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;

/**
 * 從請求 cookie 取出並驗證 admin session；供認證端點與 {@code AdminKeyGuard} 共用。
 *
 * <p>必須是 public 的 Spring bean：{@code AdminKeyGuard} 位於上層 package
 * {@code world.springai.survey}，package-private 的類別它存取不到。</p>
 */
@Component
public class AdminSessionAccess {

    /** JWT 簽發與驗證服務 */
    private final AdminSessionService sessionService;

    /** 注入 session 服務 */
    public AdminSessionAccess(AdminSessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** 讀取並驗證 admin_session cookie，回傳管理者 email；缺 cookie 或驗證失敗一律回 empty */
    public Optional<String> readEmail(HttpServletRequest request, OffsetDateTime now) {
        if (request == null || request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
            .filter(c -> AdminSessionService.COOKIE_NAME.equals(c.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .flatMap(jwt -> sessionService.readEmail(jwt, now));
    }
}
