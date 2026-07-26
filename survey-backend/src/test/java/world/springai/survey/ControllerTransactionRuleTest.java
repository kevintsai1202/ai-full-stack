package world.springai.survey;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 架構守衛：{@code @Transactional} 不得出現在 controller 類別中。
 *
 * <p><b>為什麼需要機械化守衛</b>：本專案已經發生過<b>兩次</b>「{@code @Transactional}
 * 掛在 controller 上」（{@code AdminReaderController} 與 {@code ReaderPortalController}，
 * 都已修——交易抽到被注入的 service）。兩次都是靠逐任務的人工審查抓到的，
 * 沒有守衛的話第三次還會發生。</p>
 *
 * <p><b>這條規則的兩個理由</b>：</p>
 * <ol>
 *   <li>{@code @Transactional} 只有<b>跨 bean 呼叫</b>才會經過 Spring proxy。
 *       controller 的同類別內部呼叫不經 proxy，註解會<b>靜默失效</b>——
 *       沒有交易、沒有錯誤、沒有任何跡象。</li>
 *   <li>Spring MVC 的 handler 方法一被選中就進入 proxy，所以交易會開在
 *       <b>身分驗證之前</b>：未帶 cookie／未帶 admin key 的請求先借一條連線、
 *       開一個交易，才回 401。等於讓未授權流量消耗連線池。</li>
 * </ol>
 *
 * <p><b>正確做法</b>：把需要交易的那段抽成獨立的 {@code @Service}，由 controller
 * 注入後呼叫，{@code @Transactional} 掛在那個 service 的方法上。既有範例：
 * {@code ReaderProfileService#updateName}、{@code AdminReaderService#grantVip}。</p>
 *
 * <p><b>與 {@code PackageDependencyTest} 分開的理由</b>：那支守的是 package 依賴
 * 方向，只需要逐行看 {@code import}；本支必須<b>先剝除註解與字串字面值</b>才能比對
 * （見下方 {@link #stripCommentsAndStrings}），否則現有三個 controller 的 javadoc
 * 就會讓它一上來就誤報——{@code AdminReaderController}、{@code ReaderPortalController}、
 * {@code UnlockController} 的 javadoc 都刻意寫了 {@code {@code @Transactional}}
 * 來說明「為什麼這裡不加」。兩支守衛的掃描前處理不同，合在一起只會讓兩邊都變複雜。</p>
 *
 * <p><b>本守衛的偵測盲區</b>（勿把綠燈當成「絕不會發生」）：只看原始碼文字，
 * 不涵蓋 ① 以 meta-annotation 包裝的 {@code @Transactional}（自訂註解上掛
 * {@code @Transactional}，再把自訂註解掛到 controller）② 全限定名寫法
 * {@code @org.springframework.transaction.annotation.Transactional}——
 * 後者其實會被抓到（字串裡含 {@code @...Transactional} 但比對的是
 * {@code @Transactional} 開頭，見 {@link #TRANSACTIONAL}），這裡明列是為了
 * 提醒它<b>只</b>抓得到這一種寫法。</p>
 */
class ControllerTransactionRuleTest {

    /** 生產程式碼根目錄（surefire 的工作目錄是 module 根，即 survey-backend） */
    private static final Path SOURCE_ROOT = Path.of("src/main/java/world/springai/survey");

    /**
     * controller 類別的標記。
     *
     * <p>{@code \b} 是關鍵：少了它，{@code @RestControllerAdvice}
     * （{@code ApiExceptionHandler}）也會被當成 controller。它不是 handler，
     * 這條規則對它沒有意義。</p>
     */
    private static final Pattern CONTROLLER = Pattern.compile("@(?:Rest)?Controller\\b");

    /**
     * 交易註解本體。
     *
     * <p>{@code \b} 讓 {@code @TransactionalEventListener}
     * （{@code ReferralRewardListener} 用的那個）不會被誤判——它掛在 listener 上，
     * 與本規則無關。</p>
     */
    private static final Pattern TRANSACTIONAL = Pattern.compile("@Transactional\\b");

    /**
     * 失敗訊息：講清楚規則、理由與正確做法。
     *
     * <p>觸犯的人不該需要考古才知道為什麼被擋。</p>
     */
    private static final String RULE_EXPLANATION = """
        @Transactional 不得出現在 @RestController／@Controller 類別中（類別級或方法級都不行）。
        ① 同類別內部呼叫不經 Spring proxy，註解會靜默失效——沒有交易也沒有任何錯誤跡象。
        ② 交易會開在身分驗證之前：未授權的請求先借連線、開交易，才回 401，等於讓未授權流量吃連線池。
        正確做法：把需要交易的那段抽成獨立的 @Service，由 controller 注入後呼叫，
        @Transactional 掛在那個 service 的方法上（既有範例：ReaderProfileService#updateName、
        AdminReaderService#grantVip）。本專案已經為此修過兩次，這支測試就是為了不要有第三次。
        違規位置：""";

    /** 所有 controller 類別都不得帶 {@code @Transactional} */
    @Test
    void controllersMustNotDeclareTransactional() throws IOException {
        List<String> violations = new ArrayList<>();
        // 實際掃到的 .java 檔數與其中的 controller 數：兩者任一為 0 都代表守衛真空失效
        int scannedFiles = 0;
        List<String> controllerFiles = new ArrayList<>();

        for (Path javaFile : javaFilesIn(SOURCE_ROOT)) {
            scannedFiles++;
            String stripped = stripCommentsAndStrings(
                Files.readString(javaFile, StandardCharsets.UTF_8));
            if (!CONTROLLER.matcher(stripped).find()) {
                continue;
            }
            controllerFiles.add(javaFile.getFileName().toString());

            Matcher m = TRANSACTIONAL.matcher(stripped);
            while (m.find()) {
                violations.add("%s 第 %d 行"
                    .formatted(javaFile.getFileName(), lineOf(stripped, m.start())));
            }
        }

        // 若一個檔案都沒掃到，代表 SOURCE_ROOT 解析錯誤（例如工作目錄改變），
        // 此時「違規清單為空」不代表規則成立，而是守衛真空失效，必須大聲失敗
        assertTrue(scannedFiles > 0,
            "未掃到任何生產類，SOURCE_ROOT 可能解析錯誤：" + SOURCE_ROOT.toAbsolutePath());

        // 同理：若 controller 一個都沒認出來（例如 CONTROLLER 這個 pattern 被改壞、
        // 或剝註解時把整個檔案都吃掉了），這個測試就變成恆綠的空轉。
        // 斷言認得出已知的 controller，而不只是「數量 > 0」——後者在只認出一個
        // 而漏掉其餘十幾個時仍會通過。
        for (String known : List.of(
                "AdminSettingController.java", "ReaderPortalController.java",
                "AdminReaderController.java", "UnlockController.java",
                "ReaderPageController.java", "SurveyController.java")) {
            assertTrue(controllerFiles.contains(known),
                "沒有把 " + known + " 認成 controller，掃描或剝註解的邏輯壞了；"
                    + "實際認出的是：" + controllerFiles);
        }

        assertTrue(violations.isEmpty(), RULE_EXPLANATION + String.join("、", violations));
    }

    /**
     * 剝除邏輯必須認得註解與字串裡的假陽性，同時仍抓得到真的註解。
     *
     * <p>這條直接守 {@link #stripCommentsAndStrings}：主測試目前是綠的，
     * 而它綠的原因之一是剝除有效——但若剝除壞成「整個檔案都被吃掉」，
     * 主測試同樣會綠（沒有 controller 被認出來時會被上面那組斷言擋下，
     * 這裡再從正面證明一次）。三個現有 controller 的 javadoc 就是現成的反例集，
     * 這裡用等價的合成樣本讓失敗訊息一眼看得出是哪一種寫法出錯。</p>
     */
    @Test
    void commentsAndStringLiteralsAreNotMistakenForAnnotations() {
        String benign = """
            /**
             * 本方法刻意沒有 {@code @Transactional}：交易由 service 負責。
             */
            // @Transactional 也不算
            /* @Transactional 也不算 */
            @RestController
            class Sample {
                private static final String NOTE = "@Transactional 寫在字串裡也不算";
                private static final String BLOCK = \"""
                    @Transactional 寫在文字區塊裡也不算
                    \""";
                private static final char QUOTE = '"';
            }
            """;
        String strippedBenign = stripCommentsAndStrings(benign);
        assertTrue(CONTROLLER.matcher(strippedBenign).find(),
            "剝除後連 @RestController 都不見了，代表剝太多（會讓主測試變成空轉）");
        assertEquals(false, TRANSACTIONAL.matcher(strippedBenign).find(),
            "註解／字串／文字區塊裡的 @Transactional 被誤判成註解本體");

        String offending = """
            @RestController
            class Sample {
                @Transactional
                public void handler() {}
            }
            """;
        assertTrue(TRANSACTIONAL.matcher(stripCommentsAndStrings(offending)).find(),
            "真的掛在方法上的 @Transactional 沒被抓到");
    }

    /**
     * {@code @TransactionalEventListener} 不得被誤判。
     *
     * <p>{@code ReferralRewardListener} 用的就是它。若 {@link #TRANSACTIONAL}
     * 少了 {@code \\b}，日後任何把該註解用在 controller 附近的寫法都會被誤報，
     * 而誤報的守衛最後一定會被關掉。</p>
     */
    @Test
    void transactionalEventListenerIsNotAViolation() {
        assertEquals(false, TRANSACTIONAL.matcher("@TransactionalEventListener(AFTER_COMMIT)").find());
    }

    /** 列出目錄下所有 .java 檔（與 {@code PackageDependencyTest} 同樣的四行，刻意不共用：兩支守衛互不依賴） */
    private static List<Path> javaFilesIn(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    /** 由字元位移換算行號（1 起算），讓失敗訊息能指到行 */
    private static int lineOf(String source, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /**
     * 把註解（行、區塊、javadoc）與字面值（字串、文字區塊、字元）換成空白。
     *
     * <p><b>為什麼一定要做</b>：現有三個 controller 的 javadoc 都寫了
     * {@code {@code @Transactional}} 來說明「為什麼刻意不加」——不剝除的話，
     * 這支守衛上線第一次執行就誤報三個檔案，而誤報的守衛會在下一次被關掉。</p>
     *
     * <p><b>換成空白而不是刪掉</b>：長度與換行位置維持不變，
     * {@link #lineOf} 算出的行號才會對得上原始檔案。</p>
     *
     * <p>順序很重要：文字區塊（{@code """}）必須在一般字串之前判斷，
     * 否則開頭的 {@code ""} 會被當成一個空字串而讓第三個引號開啟新字串。</p>
     */
    private static String stripCommentsAndStrings(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);

            // 行註解：吃到行尾（換行本身保留）
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                while (i < n && source.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
                continue;
            }
            // 區塊註解與 javadoc
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                out.append("  ");
                i += 2;
                while (i < n && !(source.charAt(i) == '*' && i + 1 < n && source.charAt(i + 1) == '/')) {
                    out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < n) {
                    out.append("  ");
                    i += 2;
                }
                continue;
            }
            // 文字區塊
            if (isTripleQuote(source, i)) {
                out.append("   ");
                i += 3;
                while (i < n && !isTripleQuote(source, i)) {
                    if (source.charAt(i) == '\\' && i + 1 < n) {
                        // 跳脫序列：兩個字元一起吃掉，避免 \""" 被誤認為結尾
                        out.append(' ').append(source.charAt(i + 1) == '\n' ? '\n' : ' ');
                        i += 2;
                        continue;
                    }
                    out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < n) {
                    out.append("   ");
                    i += 3;
                }
                continue;
            }
            // 一般字串與字元字面值（處理方式相同，只差結束的引號）
            if (c == '"' || c == '\'') {
                out.append(' ');
                i++;
                while (i < n && source.charAt(i) != c) {
                    // 未閉合的字面值（理論上不該出現在能編譯的檔案裡）：
                    // 遇到換行就收手，不讓它把後面整個檔案吃掉而使守衛靜默失效
                    if (source.charAt(i) == '\n') {
                        break;
                    }
                    if (source.charAt(i) == '\\' && i + 1 < n) {
                        out.append("  ");
                        i += 2;
                        continue;
                    }
                    out.append(' ');
                    i++;
                }
                if (i < n && source.charAt(i) == c) {
                    out.append(' ');
                    i++;
                }
                continue;
            }

            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** 該位置是否為三連引號（文字區塊的界線） */
    private static boolean isTripleQuote(String source, int i) {
        return i + 2 < source.length()
            && source.charAt(i) == '"' && source.charAt(i + 1) == '"' && source.charAt(i + 2) == '"';
    }
}
