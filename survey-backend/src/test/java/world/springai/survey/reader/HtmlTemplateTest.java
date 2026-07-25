package world.springai.survey.reader;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HtmlTemplate 行為測試：佔位符替換、HTML 跳脫、找不到資源時明確失敗 */
class HtmlTemplateTest {

    private final HtmlTemplate template = new HtmlTemplate();

    /** 佔位符會被替換掉，且替換後不留殘跡 */
    @Test
    void replacesPlaceholders() {
        String rendered = template.render("static/reader/login.html",
            Map.of("<!--PAGE_TITLE-->", "登入測試"));

        assertTrue(rendered.contains("登入測試"));
        assertFalse(rendered.contains("<!--PAGE_TITLE-->"), "佔位符不得殘留");
    }

    /** 找不到資源要明確拋例外，不可回空字串讓頁面靜默變空白 */
    @Test
    void missingResourceFailsLoudly() {
        assertThrows(IllegalStateException.class,
            () -> template.render("static/reader/does-not-exist.html", Map.of()));
    }

    /** HTML 跳脫：五個危險字元都要處理 */
    @Test
    void escapesDangerousCharacters() {
        assertEquals("&lt;script&gt;", HtmlTemplate.escapeHtml("<script>"));
        assertEquals("&amp;", HtmlTemplate.escapeHtml("&"));
        assertEquals("&quot;", HtmlTemplate.escapeHtml("\""));
        assertEquals("&#39;", HtmlTemplate.escapeHtml("'"));
    }

    /** & 必須先跳脫，否則會把後續產生的實體再次跳脫成 &amp;lt; */
    @Test
    void escapesAmpersandFirst() {
        assertEquals("&amp;lt;", HtmlTemplate.escapeHtml("&lt;"));
    }

    /** null 視為空字串 */
    @Test
    void escapeHandlesNull() {
        assertEquals("", HtmlTemplate.escapeHtml(null));
    }
}
