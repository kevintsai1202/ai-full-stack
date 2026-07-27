package world.springai.survey;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 瀏覽器安全標頭與敏感回應快取測試。 */
class SecurityHeadersFilterTest {

    private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

    /** 後台 API 必須 no-store 並依管理金鑰區隔。 */
    @Test
    void adminApiIsNotCacheableAndVariesByAdminKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/audience/1/detail");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertEquals("private, no-store", response.getHeader("Cache-Control"));
        assertTrue(response.getHeaders("Vary").contains("X-Admin-Key"));
    }

    /** HTML 頁與 API 都必須拒絕被第三方 iframe 嵌入。 */
    @Test
    void browserSecurityHeadersAreAlwaysPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/r/archive");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertEquals("DENY", response.getHeader("X-Frame-Options"));
        assertTrue(response.getHeader("Content-Security-Policy").contains("frame-ancestors 'none'"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("max-age=31536000; includeSubDomains",
            response.getHeader("Strict-Transport-Security"));
    }
}
