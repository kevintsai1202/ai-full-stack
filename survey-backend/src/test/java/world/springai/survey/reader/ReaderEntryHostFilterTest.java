package world.springai.survey.reader;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 讀者入口網域導向的行為測試。
 *
 * <p>核心性質：讀者 Host 只公開讀者頁、讀者 API 與訂閱送出；
 * 問卷／Admin 路徑不可因為兩個網域共用同一服務而跟著曝光。</p>
 */
class ReaderEntryHostFilterTest {

    private static final String READER_HOST = "springai-reader.zeabur.app";
    private static final String SURVEY_HOST = "springai-survey.zeabur.app";

    /** 執行 filter 並回傳 response；chain 是 MockFilterChain，可由 getRequest() 判斷是否被放行 */
    private MockHttpServletResponse run(String entryHost, String serverName, String uri,
                                        MockFilterChain chain) throws ServletException, IOException {
        return run(entryHost, serverName, "GET", uri, chain);
    }

    /** 可指定 HTTP method 的 filter 執行器，用來驗證訂閱 POST 的精準例外。 */
    private MockHttpServletResponse run(String entryHost, String serverName, String method, String uri,
                                        MockFilterChain chain) throws ServletException, IOException {
        ReaderEntryHostFilter filter = new ReaderEntryHostFilter(entryHost);
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setServerName(serverName);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    /** 讀者網域的根路徑 → 302 到 /r/，且不得繼續走 filter chain（否則問卷首頁會被一併輸出） */
    @Test
    void readerHostRootRedirectsToReaderHome() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(READER_HOST, READER_HOST, "/", chain);

        assertEquals(302, response.getStatus(), "應為 302（刻意不用會被瀏覽器永久快取的 301）");
        assertEquals("/r/", response.getHeader("Location"));
        assertNull(chain.getRequest(), "導向後不得繼續執行 chain");
    }

    /** /index.html 是根頁面的別名，同樣要導向 */
    @Test
    void readerHostIndexHtmlAlsoRedirects() throws Exception {
        MockHttpServletResponse response = run(READER_HOST, READER_HOST, "/index.html", new MockFilterChain());

        assertEquals(302, response.getStatus());
        assertEquals("/r/", response.getHeader("Location"));
    }

    /** Host 比對不分大小寫（DNS 本來就不分大小寫，requests 可能帶任意大小寫） */
    @Test
    void hostComparisonIsCaseInsensitive() throws Exception {
        MockHttpServletResponse response =
            run(READER_HOST, "SpringAI-Reader.Zeabur.App", "/", new MockFilterChain());

        assertEquals(302, response.getStatus());
    }

    /** 讀者頁與讀者 API 在讀者網域正常放行。 */
    @Test
    void readerPathsPassThrough() throws Exception {
        for (String path : new String[] {"/r/", "/r/rules", "/api/reader/me"}) {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response = run(READER_HOST, READER_HOST, path, chain);

            assertEquals(200, response.getStatus(), path + " 不得被導向");
            assertNotNull(chain.getRequest(), path + " 必須繼續走 chain");
        }
    }

    /** 讀者首頁的訂閱 POST 仍要能寫入，但同路徑的 GET 不得放行。 */
    @Test
    void readerHostAllowsOnlyPostForSurveySubscription() throws Exception {
        MockFilterChain postChain = new MockFilterChain();
        MockHttpServletResponse postResponse =
            run(READER_HOST, READER_HOST, "POST", "/api/survey", postChain);
        assertEquals(200, postResponse.getStatus());
        assertNotNull(postChain.getRequest());

        MockFilterChain getChain = new MockFilterChain();
        MockHttpServletResponse getResponse =
            run(READER_HOST, READER_HOST, "GET", "/api/survey", getChain);
        assertEquals(404, getResponse.getStatus());
        assertNull(getChain.getRequest());
    }

    /**
     * 工商連結轉址端點在讀者網域須放行（C1 修正）：該端點需要讀取讀者 session
     * cookie 才能正確歸戶點擊，信件與讀者頁的點擊都會落在讀者網域，
     * 未放行時讀者網域部署下所有工商連結點擊都會變成 404。
     */
    @Test
    void readerHostAllowsPromoClickRedirect() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response =
            run(READER_HOST, READER_HOST, "GET", "/promo/c/55", chain);

        assertEquals(200, response.getStatus(), "工商連結轉址端點必須在讀者網域放行");
        assertNotNull(chain.getRequest(), "必須繼續走 chain 才能真正觸發轉址邏輯");
    }

    /** 分享點擊只放行精準 POST；GET 不可藉此擴大讀者網域 API 面。 */
    @Test
    void readerHostAllowsOnlyPostForReferralClick() throws Exception {
        MockFilterChain postChain = new MockFilterChain();
        assertEquals(200, run(READER_HOST, READER_HOST, "POST",
            "/api/referrals/click", postChain).getStatus());
        assertNotNull(postChain.getRequest());

        MockFilterChain getChain = new MockFilterChain();
        assertEquals(404, run(READER_HOST, READER_HOST, "GET",
            "/api/referrals/click", getChain).getStatus());
        assertNull(getChain.getRequest());
    }

    /** Admin 與問卷統計不可透過讀者網域開啟。 */
    @Test
    void readerHostBlocksNonReaderFeatures() throws Exception {
        for (String path : new String[] {"/admin.html", "/api/survey/stats", "/api/admin/campaigns"}) {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response = run(READER_HOST, READER_HOST, path, chain);

            assertEquals(404, response.getStatus(), path + " 應在讀者 Host 隱藏");
            assertNull(chain.getRequest(), path + " 不得進入應用端點");
            assertEquals("no-store", response.getHeader("Cache-Control"));
        }
    }

    /** 問卷網域的根路徑不受影響——問卷表單仍是那個網域的首頁 */
    @Test
    void surveyHostRootIsUntouched() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(READER_HOST, SURVEY_HOST, "/", chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest(), "問卷網域的根路徑必須放行");
    }

    /** entry-host 未設定（空值）＝整個 filter 停用，本機開發與測試環境不受影響 */
    @Test
    void blankEntryHostDisablesTheFilter() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run("", READER_HOST, "/", chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    /** null 設定值同樣視為停用（@Value 對缺漏屬性可能給 null） */
    @Test
    void nullEntryHostDisablesTheFilter() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(null, READER_HOST, "/", chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }
}
