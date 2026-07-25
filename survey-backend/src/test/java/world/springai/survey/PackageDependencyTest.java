package world.springai.survey;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 架構守衛：確保 package 依賴方向單向。
 *
 * <p>下層（audience／mail／media）是基礎設施，不得依賴上層（reader／newsletter／form）。
 * 這層邊界是日後真要拆成獨立服務時的拆解線，一旦出現反向依賴就拆不開了。
 * 以掃描 import 語句實作，避免為此引入 ArchUnit 等新測試依賴。</p>
 *
 * <p><b>本守衛的偵測盲區（務必知悉，勿誤把「綠燈」當成「分層完好」）：</b></p>
 * <ul>
 *   <li>只偵測原始碼中的 {@code import} 語句，<b>不涵蓋全限定名引用</b>
 *       （例如直接寫 {@code world.springai.survey.newsletter.Campaign c = ...} 而不 import）。
 *       這是不引入 ArchUnit 的必然取捨，這類寫法仍須靠 code review 把關。</li>
 *   <li>只掃描 {@code src/main/java}，<b>不涵蓋 {@code src/test/java}</b>。</li>
 *   <li>只檢查「下層 → 上層」與「mail → audience」兩條依賴線，
 *       <b>未涵蓋上層 package 彼此之間</b>的依賴（例如 {@code form} 與 {@code newsletter} 互相依賴）。</li>
 * </ul>
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
        // 實際掃到的 .java 檔案數，用來偵測 SOURCE_ROOT 解析錯誤導致「掃到零檔案就綠燈」的假安全感
        int scannedFiles = 0;

        for (String lower : LOWER_LAYERS) {
            Path dir = SOURCE_ROOT.resolve(lower);
            // 尚未建立的 package（如階段 A 的 media）直接略過
            if (!Files.isDirectory(dir)) {
                continue;
            }
            List<Path> javaFiles = javaFilesIn(dir);
            scannedFiles += javaFiles.size();
            for (Path javaFile : javaFiles) {
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

        // 若一個檔案都沒掃到，代表 SOURCE_ROOT 解析錯誤（例如工作目錄改變），
        // 此時「違規清單為空」不代表分層完好，而是守衛真空失效，必須大聲失敗
        assertTrue(scannedFiles > 0,
            "未掃到任何生產類，SOURCE_ROOT 可能解析錯誤：" + SOURCE_ROOT.toAbsolutePath());

        assertTrue(violations.isEmpty(),
            "下層 package 出現反向依賴上層，違反 spec §3 的單向依賴約束：\n"
                + String.join("\n", violations));
    }

    /** mail 是最底層，連同層的 audience 都不該依賴（audience → mail 允許，反向不允許） */
    @Test
    void mailMustNotDependOnAudience() throws IOException {
        Path dir = SOURCE_ROOT.resolve("mail");

        // mail 目錄消失只有兩種可能：有人刪光了寄信基礎設施，或 SOURCE_ROOT 路徑解析錯誤，
        // 兩者都必須大聲失敗，而不是被 IOException 或「靜默 skip」掩蓋
        if (!Files.isDirectory(dir)) {
            fail("mail 目錄不存在，SOURCE_ROOT 可能解析錯誤，或 mail 基礎設施已被移除："
                + dir.toAbsolutePath());
        }

        List<String> violations = new ArrayList<>();
        List<Path> javaFiles = javaFilesIn(dir);

        // 同樣防止「掃到零檔案卻綠燈通過」的假安全感
        assertTrue(!javaFiles.isEmpty(),
            "mail 目錄下未掃到任何生產類，SOURCE_ROOT 可能解析錯誤：" + dir.toAbsolutePath());

        for (Path javaFile : javaFiles) {
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
