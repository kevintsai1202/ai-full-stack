package world.springai.survey;

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
 * 管理後台專用網域的導向與隔離測試。
 */
class AdminEntryHostFilterTest {

    private static final String ADMIN_HOST = "admin.springai.world";
    private static final String SURVEY_HOST = "survey.springai.world";

    /** 執行 filter，並用 MockFilterChain 判斷請求是否被放行。 */
    private MockHttpServletResponse run(String adminHost, String surveyHost, String serverName,
                                        String uri, MockFilterChain chain)
            throws ServletException, IOException {
        AdminEntryHostFilter filter = new AdminEntryHostFilter(adminHost, surveyHost);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setServerName(serverName);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    /** 管理網域根路徑應以暫時轉址進入管理頁。 */
    @Test
    void adminHostRootRedirectsToAdminPage() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(ADMIN_HOST, SURVEY_HOST, ADMIN_HOST, "/", chain);

        assertEquals(302, response.getStatus());
        assertEquals("/admin.html", response.getHeader("Location"));
        assertNull(chain.getRequest());
    }

    /** index.html 是根路徑別名，也應導向管理頁。 */
    @Test
    void adminHostIndexRedirectsToAdminPage() throws Exception {
        MockHttpServletResponse response =
            run(ADMIN_HOST, SURVEY_HOST, ADMIN_HOST, "/index.html", new MockFilterChain());

        assertEquals(302, response.getStatus());
        assertEquals("/admin.html", response.getHeader("Location"));
    }

    /** 管理頁與管理 API 可從管理網域正常存取。 */
    @Test
    void adminHostAllowsOnlyAdminFeatures() throws Exception {
        for (String path : new String[] {"/admin.html", "/api/admin", "/api/admin/campaigns"}) {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response =
                run(ADMIN_HOST, SURVEY_HOST, ADMIN_HOST, path, chain);

            assertEquals(200, response.getStatus(), path + " 應正常放行");
            assertNotNull(chain.getRequest(), path + " 應進入應用端點");
        }
    }

    /** 問卷、讀者頁與名稱相近但不屬於 Admin 的端點不可從管理網域開啟。 */
    @Test
    void adminHostBlocksNonAdminFeatures() throws Exception {
        for (String path : new String[] {"/r/", "/api/reader/me", "/api/survey", "/api/admin-tools"}) {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response =
                run(ADMIN_HOST, SURVEY_HOST, ADMIN_HOST, path, chain);

            assertEquals(404, response.getStatus(), path + " 應在管理 Host 隱藏");
            assertNull(chain.getRequest());
            assertEquals("no-store", response.getHeader("Cache-Control"));
        }
    }

    /** 啟用問卷 Host 隔離後，舊管理頁與 API 不可再經由問卷網域存取。 */
    @Test
    void surveyHostBlocksAdminFeatures() throws Exception {
        for (String path : new String[] {"/admin.html", "/api/admin", "/api/admin/campaigns"}) {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response =
                run(ADMIN_HOST, SURVEY_HOST, SURVEY_HOST, path, chain);

            assertEquals(404, response.getStatus(), path + " 應改由管理網域存取");
            assertNull(chain.getRequest());
        }
    }

    /** 問卷網域的問卷頁與公開 API 不受管理入口隔離影響。 */
    @Test
    void surveyHostKeepsSurveyFeatures() throws Exception {
        for (String path : new String[] {"/", "/api/survey"}) {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response =
                run(ADMIN_HOST, SURVEY_HOST, SURVEY_HOST, path, chain);

            assertEquals(200, response.getStatus());
            assertNotNull(chain.getRequest());
        }
    }

    /** DNS Host 比對不分大小寫。 */
    @Test
    void hostComparisonIsCaseInsensitive() throws Exception {
        MockHttpServletResponse response =
            run(ADMIN_HOST, SURVEY_HOST, "Admin.SpringAI.World", "/", new MockFilterChain());

        assertEquals(302, response.getStatus());
    }

    /** 僅設定管理 Host 時，新入口可用但舊問卷入口仍保留，支援安全分階段上線。 */
    @Test
    void blankSurveyHostKeepsLegacyAdminEntry() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response =
            run(ADMIN_HOST, "", SURVEY_HOST, "/admin.html", chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    /** 所有 Host 設定為空時，整個 filter 停用，不影響本機開發。 */
    @Test
    void blankHostsDisableTheFilter() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(null, null, ADMIN_HOST, "/", chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }
}
