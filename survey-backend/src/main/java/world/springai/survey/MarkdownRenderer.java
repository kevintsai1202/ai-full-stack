package world.springai.survey;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

/** 把電子報內文的 Markdown 轉成 HTML（管理者為可信作者） */
@Component
public class MarkdownRenderer {

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().build();

    /** Markdown 轉 HTML；null 視為空字串 */
    public String toHtml(String markdown) {
        if (markdown == null) {
            return "";
        }
        return renderer.render(parser.parse(markdown));
    }
}
