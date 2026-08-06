package world.springai.survey.reader;

import org.junit.jupiter.api.Test;
import world.springai.survey.form.FormSchemaService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 讀者首頁（/r/）問卷列表區塊（Task 3）與身分區（Task 4）測試。
 *
 * <p>驗證安全不變量：問卷列表只列出後台勾選曝光的問卷、標題經 HTML 跳脫、
 * 無曝光問卷時整個區塊（含佔位符）不殘留；首頁身分區的 email 必須經跳脫，
 * 且未登入時仍維持原本的訂閱表單。</p>
 */
class ReaderIndexPageTest {

    private final LoginMailService loginMailService = mock(LoginMailService.class);
    private final LoginTokenService loginTokenService = mock(LoginTokenService.class);
    private final ReaderAccountService readerAccountService = mock(ReaderAccountService.class);
    private final ReaderSessionService sessionService = mock(ReaderSessionService.class);
    private final ReaderContext readerContext = mock(ReaderContext.class);
    private final HtmlTemplate htmlTemplate = new HtmlTemplate();
    private final LoginAbuseGuard loginAbuseGuard = mock(LoginAbuseGuard.class);
    private final FormSchemaService formSchemaService = mock(FormSchemaService.class);

    private final ReaderAuthController controller = new ReaderAuthController(
        loginMailService, loginTokenService, readerAccountService, sessionService,
        readerContext, htmlTemplate, loginAbuseGuard, formSchemaService);

    /** 有曝光問卷時：首頁出現「問卷調查」區塊，每份問卷連向 /r/survey/{key}，標題經跳脫 */
    @Test
    void homepageListsExposedSurveys() {
        when(readerContext.resolve(null)).thenReturn(Optional.empty());
        when(formSchemaService.listHomepageForms()).thenReturn(List.of(
            new FormSchemaService.HomepageForm("course-interest", "課程興趣調查 <b>", null)));
        String html = controller.indexPage(null).getBody();
        assertTrue(html.contains("href=\"/r/survey/course-interest\""));
        assertTrue(html.contains("課程興趣調查 &lt;b&gt;"));   // escapeHtml 生效
        assertTrue(html.contains("問卷調查"));
    }

    /** 無任何曝光問卷時：整個區塊不出現（不出現空標題） */
    @Test
    void homepageHidesSurveySectionWhenEmpty() {
        when(readerContext.resolve(null)).thenReturn(Optional.empty());
        when(formSchemaService.listHomepageForms()).thenReturn(List.of());
        String html = controller.indexPage(null).getBody();
        assertFalse(html.contains("問卷調查"));
        assertFalse(html.contains("<!--SURVEY_LIST-->"));  // 佔位符必須被替換為空字串，不能殘留
    }

    /** 已登入：訂閱表單換成「已訂閱：email」，email 經跳脫；不出現 subscribe-form */
    @Test
    void loggedInReaderSeesIdentityInsteadOfSubscribeForm() {
        Reader reader = mock(Reader.class);
        when(reader.getEmail()).thenReturn("a<b>@example.com");
        when(readerContext.resolve("cookie")).thenReturn(
            Optional.of(new ReaderContext.Current(reader, true)));
        String html = controller.indexPage("cookie").getBody();
        assertTrue(html.contains("已訂閱："));
        assertTrue(html.contains("a&lt;b&gt;@example.com"));
        assertFalse(html.contains("id=\"subscribe-form\""));
    }

    /** 未登入：維持現況——email 輸入框 + 訂閱按鈕 */
    @Test
    void anonymousReaderSeesSubscribeForm() {
        when(readerContext.resolve(null)).thenReturn(Optional.empty());
        String html = controller.indexPage(null).getBody();
        assertTrue(html.contains("id=\"subscribe-form\""));
        assertFalse(html.contains("已訂閱："));
    }
}
