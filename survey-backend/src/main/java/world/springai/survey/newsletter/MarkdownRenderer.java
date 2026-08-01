package world.springai.survey.newsletter;

import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Image;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 把電子報內文的 Markdown 轉成 HTML（管理者為可信作者） */
@Component
public class MarkdownRenderer {

    /**
     * 啟用的 CommonMark 擴充。
     *
     * <p>目前只有 GFM 表格。CommonMark 規格本身<b>不含</b>表格語法，不掛這個擴充時
     * 整張表會被當成普通段落，管線符號原樣輸出給讀者。</p>
     *
     * <p>parser 與 renderer <b>必須掛同一份</b>：只掛 parser 會產生 renderer 不認得的
     * 節點而輸出空白，只掛 renderer 則根本不會有表格節點產生。</p>
     */
    private static final List<org.commonmark.Extension> EXTENSIONS =
        List.of(TablesExtension.create());

    private final Parser parser = Parser.builder().extensions(EXTENSIONS).build();
    /**
     * 單一換行保留為 br，符合後台編輯器的直覺；CommonMark 預設會把 soft break
     * 當成空白，導致管理者明明換行、寄出與文章頁卻黏成同一段。
     */
    private final HtmlRenderer renderer = HtmlRenderer.builder()
        .extensions(EXTENSIONS)
        .softbreak("<br />\n")
        .attributeProviderFactory(context -> this::responsiveAttributes)
        .build();

    /** 工商區塊起始標記；與 {@code <!--paywall-->} 同為「整行單獨存在」的內容標記慣例 */
    public static final String PROMO_START = "<!--promo-->";
    /** 工商區塊結束標記 */
    public static final String PROMO_END = "<!--/promo-->";

    /**
     * 工商卡片容器的開頭 HTML。
     *
     * <p>用單格 table + inline style 而非 div + class：信件端沒有外部 CSS，
     * 且 Outlook 桌面版對 div 樣式支援不佳，table 是 Email 相容度最高的容器。
     * 配色沿用讀者頁 reader.css 的主題色（--accent:#087f72、--accent-soft:#d9f1ec），
     * 讓信件與網頁版視覺一致。</p>
     */
    private static final String PROMO_CARD_OPEN =
        "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\""
            + " style=\"margin:28px 0;border-collapse:separate;\"><tr>"
            + "<td bgcolor=\"#d9f1ec\" style=\"border:1px solid #9fd6cc;"
            + "border-left:5px solid #087f72;border-radius:10px;padding:20px 24px;\">";
    private static final String PROMO_CARD_CLOSE = "</td></tr></table>";

    /** Markdown 轉 HTML；null 視為空字串 */
    public String toHtml(String markdown) {
        if (markdown == null) {
            return "";
        }
        return wrapPromoBlocks(renderer.render(parser.parse(markdown)));
    }

    /**
     * 把渲染後 HTML 中成對的 promo 標記換成工商卡片容器。
     *
     * <p>在「渲染後」處理而非操作 AST：commonmark 會把 HTML 註解原樣輸出，
     * 字串層級的成對替換即可，且信件、後台預覽、讀者頁都經過 {@link #toHtml}，
     * 一處實作三條路徑同時生效。</p>
     *
     * <p><b>不成對的標記刻意不轉換</b>：ContentSplitter 在渲染前就以
     * {@code <!--paywall-->} 切分內文，若 promo 區塊橫跨 paywall，切分後兩側
     * 各只剩單邊標記；此時保留原樣（HTML 註解對讀者不可見）是安全的降級，
     * 若硬包卡片反而會產生未閉合的 table。</p>
     */
    private String wrapPromoBlocks(String html) {
        StringBuilder result = new StringBuilder(html.length());
        int cursor = 0;
        while (true) {
            int start = html.indexOf(PROMO_START, cursor);
            if (start < 0) {
                break;
            }
            int end = html.indexOf(PROMO_END, start + PROMO_START.length());
            if (end < 0) {
                // 只有開頭沒有結尾：不轉換，讓標記以無害註解的形式留在輸出中
                break;
            }
            result.append(html, cursor, start)
                .append(PROMO_CARD_OPEN)
                .append(html, start + PROMO_START.length(), end)
                .append(PROMO_CARD_CLOSE);
            cursor = end + PROMO_END.length();
        }
        return result.append(html, cursor, html.length()).toString();
    }

    /**
     * 為圖片與程式碼區塊補上 Email 可攜的 inline style。
     *
     * <p>多數郵件客戶端不可靠地支援外部 CSS，因此圖片尺寸不能只靠網頁樣式表。
     * Markdown 圖片一律限制在容器寬度內；程式碼則允許橫向捲動並保留換行，避免長行
     * 撐破後台預覽、讀者頁或信件版面。</p>
     */
    private void responsiveAttributes(Node node, String tagName, Map<String, String> attributes) {
        if (node instanceof Image && "img".equals(tagName)) {
            attributes.put("style",
                "display:block;width:auto;max-width:100%;height:auto;margin:18px auto;"
                    + "border-radius:10px");
        }
        if (node instanceof FencedCodeBlock && "pre".equals(tagName)) {
            attributes.put("style",
                "max-width:100%;overflow-x:auto;padding:16px;border-radius:10px;"
                    + "background:#10231f;color:#e7f7f2;white-space:pre");
        }
        tableAttributes(node, tagName, attributes);
    }

    /**
     * 為 GFM 表格補上 Email 可攜的 inline style。
     *
     * <p>裸 {@code <table>} 在信箱裡是一張沒有框線、沒有間距、表頭與內容分不出來的東西——
     * 郵件客戶端不可靠地支援外部 CSS，理由與圖片、程式碼區塊完全相同。</p>
     *
     * <p>{@code border-collapse:collapse} 是必要的：不合併的話 Outlook 會在每格之間
     * 留下雙線縫隙。配色沿用讀者頁主題綠（{@code #087f72} / {@code #d9f1ec}），
     * 讓信件與網頁版視覺一致。</p>
     *
     * <p><b>只處理 {@code <table>} 與儲存格，不動 {@code <tr>}</b>：斑馬紋需要依列數
     * 決定底色，而 attributeProvider 拿不到列索引；為此改寫成自訂 renderer 不划算，
     * 有框線就足以辨識。</p>
     */
    private void tableAttributes(Node node, String tagName, Map<String, String> attributes) {
        if (node instanceof TableBlock && "table".equals(tagName)) {
            attributes.put("style",
                "border-collapse:collapse;width:100%;max-width:100%;margin:20px 0;"
                    + "font-size:14px");
        }
        if (node instanceof TableCell) {
            boolean header = ((TableCell) node).isHeader();
            attributes.put("style",
                "border:1px solid #9fd6cc;padding:8px 12px;text-align:left;"
                    + (header ? "background:#d9f1ec;color:#08665c;font-weight:700" : ""));
        }
    }
}
