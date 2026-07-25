package world.springai.survey.reader;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 極簡的 HTML 佔位符替換，用於 server 端渲染讀者端頁面。
 *
 * <p>為什麼需要 server 渲染而不是「靜態頁 + fetch API」：spec §5.3 要求免費區
 * 可被搜尋引擎索引；更關鍵的是，只有 server 渲染才能讓「未授權者的回應完全不含
 * 受限區」在 HTTP 層次成立——若改由前端 fetch，API 就得回傳整篇內容再由 JS
 * 決定顯示哪段，受限區便出現在網路回應中，paywall 形同虛設。</p>
 *
 * <p>為什麼不引入 Thymeleaf：需求只是替換幾個佔位符，HTML 仍維護在 .html 檔中。
 * 為此加一個 template engine 依賴不划算。</p>
 *
 * <p><b>刻意不快取</b>：每次請求都重讀檔案。讀取 classpath 資源的成本遠低於
 * 一次資料庫查詢，而不快取讓開發時改 HTML 不必重啟——這不是高流量系統，
 * 用可觀測的微小成本換開發體驗是值得的。</p>
 */
@Component
public class HtmlTemplate {

    /**
     * 讀取資源並替換佔位符。
     *
     * <p><b>注意</b>：替換值會原樣插入 HTML。若值來自使用者輸入或需視為純文字，
     * 呼叫端必須先經過 {@link #escapeHtml}；已渲染完成的 HTML（如 markdown 輸出）
     * 才可直接傳入。</p>
     *
     * @param resourcePath classpath 路徑，如 {@code static/reader/article.html}
     */
    public String render(String resourcePath, Map<String, String> replacements) {
        String html = load(resourcePath);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            html = html.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return html;
    }

    /** 讀取 classpath 資源；找不到時明確拋例外，不回空字串讓頁面靜默變空白 */
    private String load(String resourcePath) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("找不到或無法讀取頁面資源：" + resourcePath, e);
        }
    }

    /**
     * 把文字跳脫成可安全插入 HTML 的形式。
     *
     * <p>{@code &} 必須最先處理，否則後續產生的實體會被再次跳脫
     * （{@code &lt;} 變成 {@code &amp;lt;}）。</p>
     */
    public static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
