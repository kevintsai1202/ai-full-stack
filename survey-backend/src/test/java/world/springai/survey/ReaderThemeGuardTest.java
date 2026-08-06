package world.springai.survey;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 主題啟動守衛：每個讀者模板都必須在樣式表載入前內聯主題啟動腳本，
 * 否則暗色偏好者進站會閃白（新增模板時此測試會自動把規範帶到新頁）。
 */
class ReaderThemeGuardTest {

    @Test
    void everyReaderTemplateBootsThemeBeforeStylesheet() throws IOException {
        Path dir = Path.of("src/main/resources/templates/reader");
        try (Stream<Path> files = Files.list(dir)) {
            List<String> violations = files
                .filter(p -> p.toString().endsWith(".html"))
                .filter(p -> {
                    String html = read(p);
                    int boot = html.indexOf("localStorage.getItem('reader-theme')");
                    int css = html.indexOf("reader.css");
                    return boot < 0 || (css >= 0 && boot > css);
                })
                .map(p -> p.getFileName().toString())
                .toList();
            assertTrue(violations.isEmpty(), "缺少主題啟動腳本或位置在樣式表之後：" + violations);
        }
    }

    /** 讀檔小 helper，照 ReaderNavGuardTest 的寫法。 */
    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
