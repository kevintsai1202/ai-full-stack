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
}
