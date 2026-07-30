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

    /** 圖片必須用 inline style 限制在信件容器內，不能只依賴瀏覽器 CSS。 */
    @Test
    void constrainsImagesToContainer() {
        String html = renderer.toHtml("![封面](https://example.com/very-large.png)");
        assertTrue(html.contains("max-width:100%"), html);
        assertTrue(html.contains("height:auto"), html);
    }

    /** fenced code 應保留語言 class，並限制長行不撐破容器。 */
    @Test
    void rendersLanguageAwareCodeBlock() {
        String html = renderer.toHtml("```java\nSystem.out.println(\"Hi\");\n```");
        assertTrue(html.contains("class=\"language-java\""), html);
        assertTrue(html.contains("overflow-x:auto"), html);
    }

    /** 成對的 promo 標記應把中間內容包成工商卡片（Email 相容的 table + inline style）。 */
    @Test
    void wrapsPromoMarkersAsCard() {
        String html = renderer.toHtml(
            "<!--promo-->\n\n**工商時間**\n\n優惠碼 PUBAI85\n\n<!--/promo-->");
        assertTrue(html.contains("border-left:5px solid #087f72"), html);
        assertTrue(html.contains("<strong>工商時間</strong>"), html);
        assertTrue(html.contains("PUBAI85"), html);
        // 標記本身不應殘留在輸出中
        assertTrue(!html.contains("<!--promo-->"), html);
        assertTrue(!html.contains("<!--/promo-->"), html);
        // 內容必須位於卡片容器之內
        assertTrue(html.indexOf("border-left:5px solid #087f72") < html.indexOf("工商時間"), html);
        assertTrue(html.indexOf("工商時間") < html.indexOf("</table>"), html);
    }

    /** 不成對的 promo 標記（如被 paywall 切分）不轉換，保留為無害的 HTML 註解。 */
    @Test
    void leavesUnpairedPromoMarkerUntouched() {
        String html = renderer.toHtml("<!--promo-->\n\n只有開頭標記");
        assertTrue(html.contains("<!--promo-->"), html);
        assertTrue(!html.contains("border-left:5px solid #087f72"), html);
    }

    /** 同一篇內文允許多個 promo 區塊，各自獨立包卡片。 */
    @Test
    void wrapsEachPromoPairSeparately() {
        String html = renderer.toHtml(
            "<!--promo-->\n\n第一段\n\n<!--/promo-->\n\n正文\n\n<!--promo-->\n\n第二段\n\n<!--/promo-->");
        int first = html.indexOf("border-left:5px solid #087f72");
        int second = html.indexOf("border-left:5px solid #087f72", first + 1);
        assertTrue(first >= 0 && second > first, html);
        // 兩張卡片之間的正文不應被包進卡片
        int firstClose = html.indexOf("</table>");
        assertTrue(firstClose < html.indexOf("正文"), html);
    }
}
