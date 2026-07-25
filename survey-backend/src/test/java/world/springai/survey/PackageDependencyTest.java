package world.springai.survey;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 架構守衛：確保 package 依賴方向單向。
 *
 * <p>下層（audience／mail／media）是基礎設施，不得依賴上層（reader／newsletter／form）。
 * 這層邊界是日後真要拆成獨立服務時的拆解線，一旦出現反向依賴就拆不開了。
 * 以掃描 import 語句實作，避免為此引入 ArchUnit 等新測試依賴。</p>
 */
class PackageDependencyTest {

    /** 下層 package：基礎設施，不得反向依賴上層 */
    private static final List<String> LOWER_LAYERS = List.of("audience", "mail", "media");

    /** 上層 package：領域功能，可以依賴下層 */
    private static final List<String> UPPER_LAYERS = List.of("reader", "newsletter", "form");

    /** 生產程式碼根目錄（surefire 的工作目錄是 module 根，即 survey-backend） */
    private static final Path SOURCE_ROOT = Path.of("src/main/java/world/springai/survey");

    /** 下層 package 不得 import 任何上層 package 的型別 */
    @Test
    void lowerLayersMustNotDependOnUpperLayers() throws IOException {
        List<String> violations = new ArrayList<>();

        for (String lower : LOWER_LAYERS) {
            Path dir = SOURCE_ROOT.resolve(lower);
            // 尚未建立的 package（如階段 A 的 media）直接略過
            if (!Files.isDirectory(dir)) {
                continue;
            }
            for (Path javaFile : javaFilesIn(dir)) {
                for (String line : Files.readAllLines(javaFile)) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("import ")) {
                        continue;
                    }
                    for (String upper : UPPER_LAYERS) {
                        if (trimmed.contains("world.springai.survey." + upper + ".")) {
                            violations.add("%s（%s 層）→ %s：%s"
                                .formatted(javaFile.getFileName(), lower, upper, trimmed));
                        }
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(),
            "下層 package 出現反向依賴上層，違反 spec §3 的單向依賴約束：\n"
                + String.join("\n", violations));
    }

    /** mail 是最底層，連同層的 audience 都不該依賴（audience → mail 允許，反向不允許） */
    @Test
    void mailMustNotDependOnAudience() throws IOException {
        Path dir = SOURCE_ROOT.resolve("mail");
        List<String> violations = new ArrayList<>();

        for (Path javaFile : javaFilesIn(dir)) {
            for (String line : Files.readAllLines(javaFile)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("import ") && trimmed.contains("world.springai.survey.audience.")) {
                    violations.add(javaFile.getFileName() + "：" + trimmed);
                }
            }
        }

        assertTrue(violations.isEmpty(),
            "mail 是最底層，不得依賴 audience（WelcomeMailService 的 audience → mail 方向才是對的）：\n"
                + String.join("\n", violations));
    }

    /** 列出目錄下所有 .java 檔 */
    private static List<Path> javaFilesIn(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }
}
