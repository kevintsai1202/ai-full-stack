package world.springai.survey.newsletter;

import org.junit.jupiter.api.Test;
import world.springai.survey.form.FormSchemaService;
import world.springai.survey.form.FormSchemaService.EmailVoteQuestion;
import world.springai.survey.promo.PromoRecipientTokenService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link SurveyBlockRenderer} 純 Mockito 單元測試：驗證三通道標記展開與寄送前
 * 可嵌入性檢查，mock {@link FormSchemaService}、不需 DB（照 SurveyVoteServiceTest 模式）。
 */
class SurveyBlockRendererTest {

    private final FormSchemaService formSchemaService = mock(FormSchemaService.class);
    private final SurveyBlockRenderer renderer = new SurveyBlockRenderer(formSchemaService);

    /** 測試用問卷標記：<!--survey:reader-poll--> */
    private static final String MARKER_COMMENT = "<!--survey:reader-poll-->";

    /** 準備一則「可嵌入」的信中一鍵題，供 formSchemaService mock 回傳 */
    private void givenEmbeddable(String formKey, String fieldKey, String title, String label,
                                  List<String> options) {
        when(formSchemaService.emailVoteQuestion(formKey))
            .thenReturn(Optional.of(new EmailVoteQuestion(formKey, title, fieldKey, label, options)));
    }

    /** 信件通道：展開後含 CID／RT 佔位符與三個選項連結，且原始 HTML 註解標記不殘留 */
    @Test
    void email展開含佔位符與三選項連結() {
        givenEmbeddable("reader-poll", "rating", "滿意度調查", "你覺得如何？",
            List.of("很有幫助", "普通", "沒幫助"));

        String html = renderer.expandForEmail("前言" + MARKER_COMMENT + "後語", "https://news.example.com");

        assertFalse(html.contains("<!--survey:"), "HTML 註解標記不應殘留：" + html);
        assertTrue(html.contains(PromoRecipientTokenService.PLACEHOLDER), html);
        assertTrue(html.contains(SurveyBlockRenderer.CID_PLACEHOLDER), html);
        assertTrue(html.contains("https://news.example.com/s/v/reader-poll?f=rating&o=0"), html);
        assertTrue(html.contains("&o=1"), html);
        assertTrue(html.contains("&o=2"), html);
        assertTrue(html.contains("前言"), html);
        assertTrue(html.contains("後語"), html);
    }

    /** 問卷不可嵌入（未發布／未設信中題）時，標記維持原樣：無害 HTML 註解，安全降級不拋例外 */
    @Test
    void 問卷不可嵌入時標記保留原樣() {
        when(formSchemaService.emailVoteQuestion("reader-poll")).thenReturn(Optional.empty());

        String html = renderer.expandForEmail("前言" + MARKER_COMMENT + "後語", "https://news.example.com");

        assertEquals("前言" + MARKER_COMMENT + "後語", html);
    }

    /** 讀者頁通道：選項連結帶 c（campaignId）不帶 rt（session 歸戶），並附「繼續填完整問卷」連結 */
    @Test
    void web展開連結含campaignId無rt() {
        givenEmbeddable("reader-poll", "rating", "滿意度調查", "你覺得如何？",
            List.of("很有幫助", "普通"));

        String html = renderer.expandForWeb(MARKER_COMMENT, 9L);

        assertTrue(html.contains("/s/v/reader-poll?f=rating&o=0&c=9"), html);
        assertFalse(html.contains("rt="), html);
        assertTrue(html.contains("/r/survey/reader-poll?c=9"), html);
    }

    /** 讀者頁通道：campaignId 為 null 時不帶 c 參數 */
    @Test
    void web展開campaignId為null時不帶c參數() {
        givenEmbeddable("reader-poll", "rating", "滿意度調查", "你覺得如何？", List.of("很有幫助"));

        String html = renderer.expandForWeb(MARKER_COMMENT, null);

        assertTrue(html.contains("/s/v/reader-poll?f=rating&o=0\""), html);
        assertFalse(html.contains("&c="), html);
        assertTrue(html.contains("/r/survey/reader-poll\""), html);
    }

    /** 預覽通道：卡片含「預覽不計票」標示，且連結一律 href="#"、不含任何 /s/v/ 真連結 */
    @Test
    void preview展開含不計票標示且無真連結() {
        givenEmbeddable("reader-poll", "rating", "滿意度調查", "你覺得如何？",
            List.of("很有幫助", "普通"));

        String html = renderer.expandForPreview(MARKER_COMMENT);

        assertTrue(html.contains("預覽不計票"), html);
        assertFalse(html.contains("/s/v/"), html);
        assertTrue(html.contains("href=\"#\""), html);
    }

    /** 寄送前驗證：內文含標記但問卷不可嵌入時，拋出帶問卷 key 的明確例外訊息 */
    @Test
    void assertEmbeddable對壞標記拋例外() {
        when(formSchemaService.emailVoteQuestion("bad-key")).thenReturn(Optional.empty());
        String markdown = "內文" + "<!--survey:bad-key-->" + "結尾";

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> renderer.assertEmbeddable(markdown));
        assertTrue(ex.getMessage().contains("bad-key"), ex.getMessage());
    }

    /** 寄送前驗證：無標記的內文靜默通過，不掃描不拋例外 */
    @Test
    void assertEmbeddable對無標記內文靜默通過() {
        assertDoesNotThrow(() -> renderer.assertEmbeddable("普通內文沒有任何標記"));
    }

    /** 選項文字、標題與 label 含 < > 等字元時必須跳脫，避免破壞卡片 HTML 結構 */
    @Test
    void 選項文字含角括號要跳脫() {
        givenEmbeddable("reader-poll", "rating", "滿意度<test>", "問題<label>",
            List.of("選項<A>", "選項B"));

        String html = renderer.expandForPreview(MARKER_COMMENT);

        assertFalse(html.contains("選項<A>"), html);
        assertTrue(html.contains("選項&lt;A&gt;"), html);
        assertTrue(html.contains("滿意度&lt;test&gt;"), html);
    }
}
