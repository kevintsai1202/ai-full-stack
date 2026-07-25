package world.springai.survey.reader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ContentSplitter 行為測試：無標記、單一標記、多重標記、標記在首尾、null 輸入 */
class ContentSplitterTest {

    private final ContentSplitter splitter = new ContentSplitter();

    /** 無標記：全文皆為免費區，hasGate 為 false */
    @Test
    void noMarkerMeansEverythingIsFree() {
        String markdown = "# 標題\n\n內文全部免費。";

        ContentSplitter.Split split = splitter.split(markdown);

        assertFalse(split.hasGate());
        assertEquals(markdown, split.freeMarkdown());
        assertEquals("", split.gatedMarkdown());
    }

    /**
     * 單一標記：標記前為免費區、標記後為受限區，標記本身不出現在任何一段。
     * 同時以 LF 與 CRLF（Windows 編輯器貼上常見）兩種換行各驗證一次，
     * 確保 {@code split("\r?\n", -1)} 對 CRLF 輸入也能正確切分。
     */
    @Test
    void singleMarkerSplitsIntoTwoParts() {
        String markdownLf = """
            免費開場，勾住讀者。

            <!--paywall-->

            受限內容，需要權限。""";

        ContentSplitter.Split splitLf = splitter.split(markdownLf);

        assertTrue(splitLf.hasGate());
        assertTrue(splitLf.freeMarkdown().contains("免費開場"));
        assertFalse(splitLf.freeMarkdown().contains("受限內容"), "免費區不得含受限內容");
        assertFalse(splitLf.freeMarkdown().contains("<!--paywall-->"), "標記不得殘留在免費區");
        assertTrue(splitLf.gatedMarkdown().contains("受限內容"));
        assertFalse(splitLf.gatedMarkdown().contains("<!--paywall-->"), "標記不得殘留在受限區");

        // 將同一份內容改為 CRLF 換行，驗證 Windows 編輯器貼上的內容也能正確切分
        String markdownCrLf = markdownLf.replace("\n", "\r\n");
        ContentSplitter.Split splitCrLf = splitter.split(markdownCrLf);

        assertTrue(splitCrLf.hasGate());
        assertTrue(splitCrLf.freeMarkdown().contains("免費開場"));
        assertFalse(splitCrLf.freeMarkdown().contains("受限內容"), "免費區不得含受限內容（CRLF）");
        assertFalse(splitCrLf.freeMarkdown().contains("<!--paywall-->"), "標記不得殘留在免費區（CRLF）");
        assertTrue(splitCrLf.gatedMarkdown().contains("受限內容"));
        assertFalse(splitCrLf.gatedMarkdown().contains("<!--paywall-->"), "標記不得殘留在受限區（CRLF）");
    }

    /** 多個標記：以第一個為界，其餘標記視為受限區的普通內容並移除 */
    @Test
    void multipleMarkersUseTheFirstOne() {
        String markdown = "免費\n\n<!--paywall-->\n\n受限一\n\n<!--paywall-->\n\n受限二";

        ContentSplitter.Split split = splitter.split(markdown);

        assertTrue(split.hasGate());
        assertEquals("免費", split.freeMarkdown().trim());
        assertTrue(split.gatedMarkdown().contains("受限一"));
        assertTrue(split.gatedMarkdown().contains("受限二"));
        assertFalse(split.gatedMarkdown().contains("<!--paywall-->"), "多餘標記須清除");
    }

    /** 標記在首行：免費區為空，整篇都受限 */
    @Test
    void markerAtStartMeansEverythingIsGated() {
        ContentSplitter.Split split = splitter.split("<!--paywall-->\n\n全部受限");

        assertTrue(split.hasGate());
        assertEquals("", split.freeMarkdown().trim());
        assertEquals("全部受限", split.gatedMarkdown().trim());
    }

    /** 標記在末行：受限區為空，等同全文免費（但 hasGate 仍為 true） */
    @Test
    void markerAtEndMeansNothingIsGated() {
        ContentSplitter.Split split = splitter.split("全部免費\n\n<!--paywall-->");

        assertTrue(split.hasGate());
        assertEquals("全部免費", split.freeMarkdown().trim());
        assertEquals("", split.gatedMarkdown().trim());
    }

    /**
     * 標記前後有空白也要能認出：涵蓋一般 ASCII 空白（作者手動輸入難免帶空白），
     * 也涵蓋 NBSP（{@code  }，從 Notion／Word／網頁複製貼上內容常夾帶，
     * 且 {@code String.trim()}／{@code String.strip()} 都無法移除，
     * 若不識別會讓整篇受限內容外洩為免費區）。
     */
    @Test
    void markerWithSurroundingWhitespaceIsRecognised() {
        ContentSplitter.Split splitAsciiSpace = splitter.split("免費\n\n   <!--paywall-->   \n\n受限");

        assertTrue(splitAsciiSpace.hasGate());
        assertEquals("免費", splitAsciiSpace.freeMarkdown().trim());
        assertEquals("受限", splitAsciiSpace.gatedMarkdown().trim());

        // NBSP 版本：標記行前後包夾 NO-BREAK SPACE（ ），而非一般 ASCII 空白
        String markerLineWithNbsp = " <!--paywall--> ";
        ContentSplitter.Split splitNbsp = splitter.split("免費\n\n" + markerLineWithNbsp + "\n\n受限");

        assertTrue(splitNbsp.hasGate(), "帶 NBSP 的標記行仍須被識別為分隔線");
        assertEquals("免費", splitNbsp.freeMarkdown().trim());
        assertEquals("受限", splitNbsp.gatedMarkdown().trim());
    }

    /** null 視為空字串，不拋例外 */
    @Test
    void nullIsTreatedAsEmpty() {
        ContentSplitter.Split split = splitter.split(null);

        assertFalse(split.hasGate());
        assertEquals("", split.freeMarkdown());
        assertEquals("", split.gatedMarkdown());
    }
}
