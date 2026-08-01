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

    /**
     * GFM 表格必須渲染成真正的 {@code <table>}。
     *
     * <p>CommonMark 規格本身<b>不含</b>表格，那是 GFM 擴充。沒掛擴充時，
     * 整張表會被當成普通段落輸出，管線符號原樣噴給讀者看——而且因為預覽、
     * 測試信與正式寄送共用同一個 toHtml()，四個地方會一起壞。</p>
     */
    @Test
    void rendersGfmTable() {
        String html = renderer.toHtml("""
            | 設定 | 在回答什麼 |
            |---|---|
            | **MX** | 只管收信 |
            | SPF | 誰可以寄信 |
            """);

        assertTrue(html.contains("<table"), "表格必須渲染成 <table>：" + html);
        assertTrue(html.contains("<th"), "表頭必須是 <th>：" + html);
        assertTrue(html.contains("<td"), "儲存格必須是 <td>：" + html);
        assertTrue(html.contains("<strong>MX</strong>"), "儲存格內的 Markdown 仍要生效：" + html);
        assertTrue(!html.contains("|---|"), "分隔列不可原樣輸出：" + html);
    }

    /**
     * 表格必須自帶 inline style。
     *
     * <p>Email 端沒有外部 CSS 可用，裸 {@code <table>} 在信箱裡會是一張沒有框線、
     * 沒有間距、表頭和內容分不出來的東西。與圖片、程式碼區塊同理——
     * 這個專案已經用 attributeProvider 對那兩者補樣式，表格沿用同一套做法。</p>
     */
    @Test
    void stylesTableForEmailClients() {
        String html = renderer.toHtml("""
            | 設定 | 說明 |
            |---|---|
            | MX | 只管收信 |
            """);

        assertTrue(html.contains("border-collapse"), "表格需可靠地合併框線：" + html);
        assertTrue(html.contains("max-width:100%"), "表格不可撐破信件容器：" + html);
        // 表頭與儲存格都要有框線，否則信箱中看起來只是一堆散字
        assertTrue(html.contains("<th style=") && html.contains("<td style="),
            "表頭與儲存格都要帶 inline style：" + html);
    }

    /** 表格是選用語法：一般段落不得因為擴充而被誤判成表格。 */
    @Test
    void leavesOrdinaryTextUnchangedByTableExtension() {
        String html = renderer.toHtml("這句話有一個 | 管線符號，但不是表格。");

        assertTrue(!html.contains("<table"), "不該把普通句子當成表格：" + html);
        assertTrue(html.contains("管線符號"), html);
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
