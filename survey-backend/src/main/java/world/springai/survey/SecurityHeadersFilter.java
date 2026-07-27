package world.springai.survey;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 全站瀏覽器安全標頭與敏感 API 快取防護。
 *
 * <p>後台使用自訂 {@code X-Admin-Key}，共享快取不會像處理 Authorization 一樣
 * 自動避開，因此管理 API 必須明確 no-store 並把金鑰加入 Vary。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final String CONTENT_SECURITY_POLICY = String.join(" ",
        "default-src 'self';",
        "base-uri 'self';",
        "object-src 'none';",
        "frame-ancestors 'none';",
        "form-action 'self';",
        "img-src 'self' data: https:;",
        "style-src 'self' 'unsafe-inline';",
        "script-src 'self' 'unsafe-inline';",
        "frame-src 'self';");

    /** 在 controller 或靜態資源處理前先寫入標頭，錯誤回應也不會漏掉。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);

        String path = request.getRequestURI();
        if (path.startsWith("/api/admin/") || path.startsWith("/api/reader/")) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
            response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        }
        if (path.startsWith("/api/admin/")) {
            response.addHeader(HttpHeaders.VARY, "X-Admin-Key");
        }

        filterChain.doFilter(request, response);
    }
}
