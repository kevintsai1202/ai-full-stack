package world.springai.survey.reader;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 讀者入口網域的根路徑導向與功能邊界。
 *
 * <p><b>為什麼需要這個 filter</b>：Zeabur 的網域綁定只能指到服務根路徑，
 * 轉址型網域（redirectTo）也只能指向同服務的既有網域、不能帶路徑——
 * 平台層做不出「springai-reader.zeabur.app → /r/」。因此改為把讀者網域
 * 綁成同一個 app 的第二個網域，由本 filter 依 Host 判斷：來自讀者入口網域的
 * 根路徑請求導向 {@code /r/}，問卷網域則完全不受影響。</p>
 *
 * <p><b>讀者 Host 採允許清單</b>：只放行 {@code /r/**}、{@code /api/reader/**}、
 * 分享點擊的 {@code POST /api/referrals/click}
 * 與首頁訂閱所需的 {@code POST /api/survey}。Admin、問卷統計與其他營運端點
 * 即使部署在同一個 app，也不可經由讀者網域存取。</p>
 *
 * <p><b>用 302 而非 301</b>：301 會被瀏覽器永久快取，日後若想把讀者入口
 * 換成獨立頁面，被快取的舊轉址收不回來；302 讓目的地保持可調整。</p>
 *
 * <p>{@code app.reader.entry-host}（環境變數 {@code READER_ENTRY_HOST}）
 * 未設定時本 filter 完全不動作——本機開發與測試環境不受影響。</p>
 */
@Component
public class ReaderEntryHostFilter extends OncePerRequestFilter {

    /** 讀者入口網域（不含協定與路徑，例如 springai-reader.zeabur.app）；空值＝停用 */
    private final String entryHost;

    /** 注入讀者入口網域設定 */
    public ReaderEntryHostFilter(@Value("${app.reader.entry-host:}") String entryHost) {
        this.entryHost = entryHost == null ? "" : entryHost.trim();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!isReaderHost(request)) {
            chain.doFilter(request, response);
            return;
        }
        if (isReaderEntryRoot(request)) {
            // 302：見類別 javadoc——301 會被永久快取，收不回來
            response.setStatus(HttpServletResponse.SC_FOUND);
            response.setHeader("Location", "/r/");
            return;
        }
        if (isAllowedReaderPath(request)) {
            chain.doFilter(request, response);
            return;
        }
        // 同一個 Spring Boot 同時承載問卷與讀者站；Gateway 可把兩個 Host 導向同一服務，
        // 但讀者 Host 不應因此暴露 admin.html、問卷統計或其他營運端點。
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("text/plain;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("此網址只提供電子報讀者功能。");
    }

    /** 是否為「讀者入口網域的根路徑」請求；host 比對不分大小寫（DNS 不分大小寫） */
    private boolean isReaderEntryRoot(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!"/".equals(path) && !"/index.html".equals(path)) {
            return false;
        }
        return true;
    }

    /** 是否從設定的讀者 Host 進站；空設定代表停用整個 filter。 */
    private boolean isReaderHost(HttpServletRequest request) {
        return !entryHost.isEmpty() && entryHost.equalsIgnoreCase(request.getServerName());
    }

    /**
     * 讀者 Host 的允許路徑。
     *
     * <p>訂閱首頁仍沿用既有 {@code POST /api/survey} 寫入同意紀錄，所以只為 POST
     * 精準放行該端點；GET 統計、Admin 與其他問卷 API 一律不在讀者網域公開。</p>
     *
     * <p>{@code GET /promo/c/{placementId}} 是工商連結的安全轉址端點，信件與
     * 讀者頁的點擊都會落在讀者網域，且該端點需要讀取讀者 session cookie 才能
     * 正確歸戶（見 {@code PromoClickController}），未放行會讓讀者網域部署下
     * 所有工商連結點擊都 404。</p>
     */
    private boolean isAllowedReaderPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.equals("/r") || path.startsWith("/r/")
                || path.equals("/api/reader") || path.startsWith("/api/reader/")) {
            return true;
        }
        if ("GET".equalsIgnoreCase(request.getMethod()) && path.startsWith("/promo/c/")) {
            return true;
        }
        return "POST".equalsIgnoreCase(request.getMethod())
            && ("/api/survey".equals(path) || "/api/referrals/click".equals(path));
    }
}
