package world.springai.survey.reader;

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
 * <p>正交性：本類決定「哪裡開始要權限」，campaign 的 tier 與 credit_cost 決定
 * 「要什麼權限」。兩者互不干涉，所以 BASIC 文章也可以有受限區。</p>
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
            if (PAYWALL_MARKER.equals(lines[i].trim())) {
                return i;
            }
        }
        return -1;
    }

    /** 取出 from 之後的所有行，並濾掉其中多餘的標記行 */
    private List<String> stripMarkerLines(String[] lines, int from) {
        List<String> kept = new ArrayList<>();
        for (int i = from; i < lines.length; i++) {
            if (!PAYWALL_MARKER.equals(lines[i].trim())) {
                kept.add(lines[i]);
            }
        }
        return kept;
    }
}
