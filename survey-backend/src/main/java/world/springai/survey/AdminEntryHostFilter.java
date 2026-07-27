package world.springai.survey;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 管理後台專用網域的入口導向與功能隔離。
 *
 * <p>同一個 Spring Boot 服務同時承載問卷、讀者站與管理後台，因此不能只依賴
 * Gateway 將不同 Host 指向同一個服務。本 filter 讓管理網域只公開
 * {@code admin.html} 與 {@code /api/admin/**}，並可在問卷網域阻擋相同路徑。</p>
 *
 * <p>{@code app.admin.entry-host} 設定後會啟用管理網域白名單；
 * {@code app.survey.entry-host} 設定後則會關閉問卷網域上的舊管理入口。
 * 兩者可分階段設定，避免新網域 DNS 尚未生效時管理員無法登入。</p>
 */
@Component
public class AdminEntryHostFilter extends OncePerRequestFilter {

    /** 管理後台專用網域；空值代表尚未啟用新入口。 */
    private final String adminEntryHost;

    /** 問卷公開網域；空值代表暫不關閉舊管理入口。 */
    private final String surveyEntryHost;

    /** 注入管理與問卷入口網域設定。 */
    public AdminEntryHostFilter(
            @Value("${app.admin.entry-host:}") String adminEntryHost,
            @Value("${app.survey.entry-host:}") String surveyEntryHost) {
        this.adminEntryHost = normalizeHost(adminEntryHost);
        this.surveyEntryHost = normalizeHost(surveyEntryHost);
    }

    /** 依 Host 與路徑決定導向、放行或隱藏管理功能。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (matchesHost(request, adminEntryHost)) {
            handleAdminHost(request, response, chain);
            return;
        }
        if (matchesHost(request, surveyEntryHost) && isAdminPath(request.getRequestURI())) {
            writeNotFound(response, "管理後台請改由專用網址進入。");
            return;
        }
        chain.doFilter(request, response);
    }

    /** 管理網域根路徑導向管理頁，其他路徑採最小允許清單。 */
    private void handleAdminHost(HttpServletRequest request, HttpServletResponse response,
                                 FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if ("/".equals(path) || "/index.html".equals(path)) {
            response.setStatus(HttpServletResponse.SC_FOUND);
            response.setHeader("Location", "/admin.html");
            return;
        }
        if (isAdminPath(path)) {
            chain.doFilter(request, response);
            return;
        }
        writeNotFound(response, "此網址只提供管理後台功能。");
    }

    /** 判斷是否為管理頁或管理 API，避免誤放行名稱相近的路徑。 */
    private boolean isAdminPath(String path) {
        return "/admin.html".equals(path)
            || "/api/admin".equals(path)
            || path.startsWith("/api/admin/");
    }

    /** 寫入不可快取的 404，避免暴露同服務中的其他功能。 */
    private void writeNotFound(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("text/plain;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(message);
    }

    /** Host 比對不分大小寫；空設定代表停用該規則。 */
    private boolean matchesHost(HttpServletRequest request, String configuredHost) {
        return !configuredHost.isEmpty()
            && configuredHost.equalsIgnoreCase(request.getServerName());
    }

    /** 將可能為 null 的設定轉成可安全比對的字串。 */
    private static String normalizeHost(String host) {
        return host == null ? "" : host.trim();
    }
}
