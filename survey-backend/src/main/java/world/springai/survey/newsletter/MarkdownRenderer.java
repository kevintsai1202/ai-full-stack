package world.springai.survey.newsletter;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

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
        .build();

    /** Markdown 轉 HTML；null 視為空字串 */
    public String toHtml(String markdown) {
        if (markdown == null) {
            return "";
        }
        return renderer.render(parser.parse(markdown));
    }
}
