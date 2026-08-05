package world.springai.survey.reader;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 讀者頁樣式的可讀性回歸測試。 */
class ReaderStylesheetTest {

    /** 讀者端共用樣式表。 */
    private static final Path READER_STYLESHEET =
        Path.of("src/main/resources/static/reader/reader.css");

    /**
     * fenced code 內層的 code 不得沿用行內程式碼淺色底，避免深色區塊出現淺底淺字。
     */
    @Test
    void fencedCodeInheritsPreformattedBlockColors() throws IOException {
        String css = Files.readString(READER_STYLESHEET, StandardCharsets.UTF_8);

        assertTrue(css.contains(".article-body pre code"),
            "reader.css 必須區分 fenced code 與一般行內 code");
        assertTrue(css.contains("background:transparent; color:inherit"),
            "fenced code 內層必須透明並繼承 pre 的高對比文字色");
    }

    /**
     * 文章頁兩欄版面：側欄樣式存在，且 .wrap 的 760px 單欄寬度未被改動
     * ——放寬只能加在 .article-wrap 上，否則 archive／me／rules 全部跟著變寬。
     */
    @Test
    void articleSidebarLayoutExistsWithoutWideningOtherPages() throws IOException {
        String css = Files.readString(READER_STYLESHEET, StandardCharsets.UTF_8);

        assertTrue(css.contains(".wrap { width:min(100% - 36px, 760px)"),
            "共用 .wrap 的 760px 單欄寬度不得被改動");
        assertTrue(css.contains(".article-wrap"), "文章頁需有自己的加寬容器");
        assertTrue(css.contains(".article-layout"), "文章頁需有兩欄 grid 容器");
        assertTrue(css.contains(".article-side"), "需有側欄樣式");
        assertTrue(css.contains("@media (max-width:960px)"),
            "需有窄螢幕斷點讓側欄降到內文下方");
    }
}
