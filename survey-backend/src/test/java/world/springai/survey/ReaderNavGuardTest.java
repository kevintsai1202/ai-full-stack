package world.springai.survey;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 架構守衛：讀者端導覽列只能來自 {@code reader.ReaderNav}，不得在 controller 或
 * 模板裡各自寫死。
 *
 * <p><b>背景</b>：{@code ReaderNav} 的 javadoc 曾經記載「掃描生產程式碼裡的
 * {@code <a href="/r/} 不可行，偽陽性太多，只能靠 code review 把關」。實測結果推翻了
 * 這個結論：只鎖定三個<b>逐字</b>字串（{@code <a href="/r/archive"}、
 * {@code <a href="/r/me"}、{@code <a href="/r/login"}，而非任何 {@code <a href="/r/}
 * 開頭的寬鬆比對），{@code reader} 套件目前的偽陽性數量是零——paywall 的行動按鈕、
 * 規則頁提示連結用的都是完整的 {@code <a href="/r/rules">} 這個不同的字串，不會撞到
 * 這三個逐字比對。窄 pattern 是可以機械化把關的，不必只靠人工審查。</p>
 *
 * <p>兩道檢查對應兩種「順手改回自己拼」的方式：</p>
 * <ol>
 *   <li>某個 controller 直接在程式碼裡 inline 一段逐字相同的導覽連結（例如複製
 *       {@code ReaderNav} 的輸出貼進自己的字串常數）。</li>
 *   <li>某個 {@code templates/reader/*.html} 模板的 {@code <nav>} 區塊寫死連結，
 *       而不是留 {@code <!--NAV_LINKS-->} 佔位符讓 {@code HtmlTemplate} 動態注入。</li>
 * </ol>
 */
class ReaderNavGuardTest {

    /** reader 套件的生產程式碼根目錄 */
    private static final Path READER_JAVA_ROOT =
        Path.of("src/main/java/world/springai/survey/reader");

    /** 讀者端模板根目錄 */
    private static final Path READER_STATIC_ROOT =
        Path.of("src/main/resources/templates/reader");

    /** 依 URL 套用選取狀態的共用導覽腳本。 */
    private static final Path READER_NAV_SCRIPT =
        Path.of("src/main/resources/static/reader/reader-nav.js");

    /**
     * 只能出現在 {@code ReaderNav.java} 裡的三個逐字字串（Java 原始碼中雙引號會被
     * 跳脫成 {@code \"}，故此處的比對字面值同樣使用跳脫後的形式，才對得上原始檔案
     * 實際的位元組內容）。刻意只列這三個而非任何 {@code <a href="/r/} 開頭的字串，
     * 避免撞上 paywall 行動按鈕、{@code rulesHint()} 等本來就合法的其他連結。
     */
    private static final List<String> FORBIDDEN_LITERALS = List.of(
        "<a href=\\\"/r/archive\\\"",
        "<a href=\\\"/r/me\\\"",
        "<a href=\\\"/r/login\\\"");

    /**
     * 這兩頁是終點頁（讀者到那裡是為了完成一件事或離開），{@code <nav>} 刻意維持
     * 最小的靜態導覽，不呼叫 {@code ReaderNav}——見 {@code ReaderNav} 類 javadoc
     * 的說明，這是範圍決定而非漏改。
     */
    private static final Set<String> STATIC_NAV_TEMPLATES = Set.of("login.html", "not-found.html");

    /** 抓出 {@code <nav>...</nav>} 區塊本體（DOTALL 讓 {@code .} 跨行比對） */
    private static final Pattern NAV_BLOCK = Pattern.compile("<nav>(.*?)</nav>", Pattern.DOTALL);

    /**
     * reader 套件的生產程式碼中，除 {@code ReaderNav.java} 外不得出現三個導覽連結的
     * 逐字字串。
     */
    @Test
    void readerProductionCodeDoesNotInlineNavLinks() throws IOException {
        List<String> violations = new ArrayList<>();
        // 實際掃到的 .java 檔數：0 代表 READER_JAVA_ROOT 解析錯誤，此時「違規清單為空」
        // 不代表規則成立，而是守衛真空失效
        int scannedFiles = 0;

        for (Path javaFile : javaFilesIn(READER_JAVA_ROOT)) {
            scannedFiles++;
            if (javaFile.getFileName().toString().equals("ReaderNav.java")) {
                continue;
            }
            String content = Files.readString(javaFile, StandardCharsets.UTF_8);
            for (String literal : FORBIDDEN_LITERALS) {
                if (content.contains(literal)) {
                    violations.add(javaFile.getFileName() + " 內嵌了導覽連結字面值：" + literal);
                }
            }
        }

        assertTrue(scannedFiles > 0,
            "未掃到任何 reader 生產類，READER_JAVA_ROOT 可能解析錯誤："
                + READER_JAVA_ROOT.toAbsolutePath());
        assertTrue(violations.isEmpty(),
            "導覽列只能來自 reader.ReaderNav，不得在其他生產類裡自行拼接同樣的連結"
                + "（新增讀者端頁面時導覽列一律呼叫 ReaderNav）：\n"
                + String.join("\n", violations));
    }

    /**
     * {@code templates/reader/*.html} 除 {@code login.html} 與 {@code not-found.html} 外，
     * {@code <nav>} 區塊內只能有 {@code ReaderNav} 動態注入的佔位符，不得含
     * {@code <a}（也就是不得寫死任何連結）。
     */
    @Test
    void readerTemplatesDoNotHardcodeNavLinks() throws IOException {
        List<String> violations = new ArrayList<>();
        int scannedFiles = 0;

        try (Stream<Path> files = Files.list(READER_STATIC_ROOT)) {
            List<Path> htmlFiles = files.filter(p -> p.toString().endsWith(".html")).toList();
            for (Path htmlFile : htmlFiles) {
                scannedFiles++;
                String name = htmlFile.getFileName().toString();
                if (STATIC_NAV_TEMPLATES.contains(name)) {
                    continue;
                }
                String content = Files.readString(htmlFile, StandardCharsets.UTF_8);
                Matcher m = NAV_BLOCK.matcher(content);
                if (!m.find()) {
                    violations.add(name + "：找不到 <nav> 區塊，無法確認導覽列來源");
                    continue;
                }
                String navBody = m.group(1);
                if (navBody.contains("<a")) {
                    violations.add(name + "：<nav> 區塊內含寫死的連結，應改用 "
                        + "<!--NAV_LINKS--> 佔位符讓 ReaderNav 動態注入 → " + navBody.trim());
                }
            }
        }

        assertTrue(scannedFiles > 0,
            "未掃到任何 reader 模板，READER_STATIC_ROOT 可能解析錯誤："
                + READER_STATIC_ROOT.toAbsolutePath());
        assertTrue(violations.isEmpty(),
            "reader 模板的 <nav> 必須只用 ReaderNav 動態注入的佔位符，不得寫死連結：\n"
                + String.join("\n", violations));
    }

    /** 每一份讀者 HTML 都必須載入共用導覽腳本，避免部分頁面沒有 icon 高亮語意。 */
    @Test
    void everyReaderTemplateLoadsNavigationEnhancement() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.list(READER_STATIC_ROOT)) {
            for (Path htmlFile : files.filter(p -> p.toString().endsWith(".html")).toList()) {
                String content = Files.readString(htmlFile, StandardCharsets.UTF_8);
                if (!content.contains("<script src=\"/r/reader-nav.js\" defer></script>")) {
                    violations.add(htmlFile.getFileName().toString());
                }
            }
        }
        String script = Files.readString(READER_NAV_SCRIPT, StandardCharsets.UTF_8);
        assertTrue(script.contains("aria-current"),
            "reader-nav.js 必須同步 aria-current，不能只有視覺 class");
        assertTrue(violations.isEmpty(),
            "下列讀者模板未載入共用導覽強化腳本：" + violations);
    }

    /**
     * 讀者模板不得放回 {@code static/}。
     *
     * <p>Spring Boot 預設把 {@code classpath:/static/**} 整個對外供應，模板放在那裡
     * 等於原始檔（含未替換的 {@code <!--NAV_LINKS-->} 等佔位符）可經
     * {@code /reader/xxx.html} 直接取得——看起來像壞掉的重複頁面，且不受 controller
     * 的登入檢查與快取標頭管制。模板已於 2026-07-27 搬到 {@code templates/reader/}
     * （不在任何預設靜態供應路徑內），本測試防止有人「順手」把新模板加回
     * {@code static/reader/}。{@code reader.css} 留在原地是刻意的：
     * {@code WebConfig} 的 {@code /r/*.css} 映射從那裡供應靜態附件。</p>
     */
    @Test
    void readerTemplatesMustNotLiveUnderStaticResources() throws IOException {
        Path staticReader = Path.of("src/main/resources/static/reader");
        if (!Files.isDirectory(staticReader)) {
            return; // 目錄整個不存在也算合格（css 若日後搬走，目錄可能被刪）
        }
        try (Stream<Path> files = Files.list(staticReader)) {
            List<String> htmlFiles = files.map(p -> p.getFileName().toString())
                .filter(name -> name.endsWith(".html"))
                .toList();
            assertTrue(htmlFiles.isEmpty(),
                "static/reader/ 下不得有 HTML 模板（會被 Spring 靜態資源處理器原樣供出，"
                    + "含未替換的佔位符）。請放到 templates/reader/：" + htmlFiles);
        }
    }

    /** 列出目錄下所有 .java 檔（與 {@code PackageDependencyTest} 同樣的四行慣例） */
    private static List<Path> javaFilesIn(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }
}
