package world.springai.survey.newsletter;

import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Image;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 把電子報內文的 Markdown 轉成 HTML（管理者為可信作者） */
@Component
public class MarkdownRenderer {

    private final Parser parser = Parser.builder().build();
    /**
     * 單一換行保留為 br，符合後台編輯器的直覺；CommonMark 預設會把 soft break
     * 當成空白，導致管理者明明換行、寄出與文章頁卻黏成同一段。
     */
    private final HtmlRenderer renderer = HtmlRenderer.builder()
        .softbreak("<br />\n")
        .attributeProviderFactory(context -> this::responsiveAttributes)
        .build();

    /** Markdown 轉 HTML；null 視為空字串 */
    public String toHtml(String markdown) {
        if (markdown == null) {
            return "";
        }
        return renderer.render(parser.parse(markdown));
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
    }
}
