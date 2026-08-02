package world.springai.survey.newsletter;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 依作者標記把電子報 markdown 切成「免費區」與「受限區」。
 *
 * <p>作者在 markdown 中單獨一行插入 {@value #PAYWALL_MARKER}（沿用 WordPress
 * {@code <!--more-->} 慣例，任何編輯器都不會破版），該行之後的內容需要權限才能看。</p>
 *
 * <p>刻意在 markdown 層切分而非渲染後切 HTML：截斷 HTML 字串會斷在標籤中間造成破版；
 * 而且先渲染再切的話，commonmark 會把這行 HTML 註解原樣輸出到頁面上。</p>
 *
 * <p>本類只負責辨識付費內容從哪裡開始；產品規則由 {@link CampaignService}
 * 統一守門：含標記的文章必須是 PREMIUM 且具備正數解鎖點數，BASIC 必須全文免費。</p>
 */
@Component
public class ContentSplitter {

    /** 受限區起點標記 */
    public static final String PAYWALL_MARKER = "<!--paywall-->";

    /**
     * 切分結果。
     *
     * @param freeMarkdown  免費區 markdown（所有人可見）
     * @param gatedMarkdown 受限區 markdown（需權限；未授權時**絕不可**輸出給前端）
     * @param hasGate       原文是否含標記
     */
    public record Split(String freeMarkdown, String gatedMarkdown, boolean hasGate) {}

    /** 以第一個標記為界切分；無標記時全文皆為免費區。null 視為空字串 */
    public Split split(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return new Split("", "", false);
        }

        String[] lines = markdown.split("\r?\n", -1);
        int markerIndex = indexOfMarkerLine(lines);

        if (markerIndex < 0) {
            return new Split(markdown, "", false);
        }

        String free = String.join("\n", List.of(lines).subList(0, markerIndex));
        // 受限區可能還有多餘標記（作者插了不只一個），一併清除避免顯示在頁面上
        String gated = String.join("\n", stripMarkerLines(lines, markerIndex + 1));
        return new Split(free, gated, true);
    }

    /** 找出第一個「整行只有標記」的行號；找不到回 -1 */
    private int indexOfMarkerLine(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            if (isMarkerLine(lines[i])) {
                return i;
            }
        }
        return -1;
    }

    /** 取出 from 之後的所有行，並濾掉其中多餘的標記行 */
    private List<String> stripMarkerLines(String[] lines, int from) {
        List<String> kept = new ArrayList<>();
        for (int i = from; i < lines.length; i++) {
            if (!isMarkerLine(lines[i])) {
                kept.add(lines[i]);
            }
        }
        return kept;
    }

    /**
     * 判斷一行是否「去除空白後恰好等於標記」（精確比對，不是 contains）。
     *
     * <p>刻意不用 {@code line.trim()} 或 {@code line.strip()}：{@code trim()} 只移除
     * 碼位 &le; 0x20 的 ASCII 空白，{@code strip()} 雖然涵蓋 {@link Character#isWhitespace}，
     * 但兩者都無法處理 {@code  }（NO-BREAK SPACE）等「空白外觀字元」——因為
     * {@code Character.isWhitespace(' ')} 回傳 {@code false}（它被歸類為
     * space char 而非 whitespace char）。從 Notion、Word、網頁複製貼上的內容常夾帶
     * NBSP，作者只要在標記行貼進一個 NBSP，{@code trim()}/{@code strip()} 版比對就會
     * 失敗，導致該行被當成普通內容，整篇受限內容因而外洩為免費區——失效方向是「洩漏」，
     * 是所有失效模式中最壞的一種，因此這裡要用更寬容的正規化來守住識別，
     * 而非用更寬容的比對（例如 contains）放寬「是不是分隔線」的判斷。</p>
     *
     * <p>做法：逐字元移除同時符合 {@link Character#isWhitespace} 或
     * {@link Character#isSpaceChar} 的字元（前者涵蓋 tab、換行等控制性空白，
     * 後者涵蓋 NBSP 與各種 Unicode 空格），再與標記做「整行相等」比對。
     * 一行內容除了標記外還夾帶其他文字時，去除空白後不會恰好等於標記，
     * 因此仍會被判為普通內容，不會被誤判為分隔線。</p>
     */
    private boolean isMarkerLine(String line) {
        StringBuilder normalized = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) {
                normalized.append(c);
            }
        }
        return PAYWALL_MARKER.contentEquals(normalized);
    }
}
