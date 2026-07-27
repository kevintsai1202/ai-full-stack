package world.springai.survey.newsletter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Markdown 轉 HTML 測試 */
class MarkdownRendererTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    /** 標題與連結應轉成對應 HTML 標籤 */
    @Test
    void rendersHeadingAndLink() {
        String html = renderer.toHtml("# 標題\n\n[連結](https://example.com)");
        assertTrue(html.contains("<h1>標題</h1>"), html);
        assertTrue(html.contains("href=\"https://example.com\""), html);
    }

    /** null 輸入回空字串，不丟例外 */
    @Test
    void nullSafe() {
        assertEquals("", renderer.toHtml(null));
    }

    /** 編輯器中的單一換行必須保留，避免寄出後兩行被折成同一段。 */
    @Test
    void preservesSingleLineBreak() {
        String html = renderer.toHtml("第一行\n第二行");
        assertTrue(html.contains("第一行<br />\n第二行"), html);
    }
}
