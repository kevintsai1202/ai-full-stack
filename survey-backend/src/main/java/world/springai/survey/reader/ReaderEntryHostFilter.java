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
 * 讀者入口網域的根路徑導向。
 *
 * <p><b>為什麼需要這個 filter</b>：Zeabur 的網域綁定只能指到服務根路徑，
 * 轉址型網域（redirectTo）也只能指向同服務的既有網域、不能帶路徑——
 * 平台層做不出「springai-reader.zeabur.app → /r/」。因此改為把讀者網域
 * 綁成同一個 app 的第二個網域，由本 filter 依 Host 判斷：來自讀者入口網域的
 * 根路徑請求導向 {@code /r/}，其餘請求（含問卷網域的所有請求）原樣放行。</p>
 *
 * <p><b>只攔根路徑</b>（{@code /} 與 {@code /index.html}）：讀者網域下的其他
 * 路徑（{@code /r/**}、{@code /api/**}）本來就是同一個 app 的合法內容，
 * 信件裡的 confirm／unsubscribe 連結不論從哪個網域進來都必須照常運作。</p>
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
        if (isReaderEntryRoot(request)) {
            // 302：見類別 javadoc——301 會被永久快取，收不回來
            response.setStatus(HttpServletResponse.SC_FOUND);
            response.setHeader("Location", "/r/");
            return;
        }
        chain.doFilter(request, response);
    }

    /** 是否為「讀者入口網域的根路徑」請求；host 比對不分大小寫（DNS 不分大小寫） */
    private boolean isReaderEntryRoot(HttpServletRequest request) {
        if (entryHost.isEmpty()) {
            return false;
        }
        String path = request.getRequestURI();
        if (!"/".equals(path) && !"/index.html".equals(path)) {
            return false;
        }
        return entryHost.equalsIgnoreCase(request.getServerName());
    }
}
