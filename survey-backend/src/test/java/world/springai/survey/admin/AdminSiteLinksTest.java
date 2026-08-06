package world.springai.survey.admin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** admin 登入連結必須指向後台網域，否則 cookie 會種在錯誤的 host */
class AdminSiteLinksTest {

    /** 連結應以設定的後台網址為基底，並帶上 token 查詢參數 */
    @Test
    void verifyLoginPointsToAdminHost() {
        AdminSiteLinks links = new AdminSiteLinks("https://admin.springai.world");

        assertEquals("https://admin.springai.world/api/admin/login/verify?t=abc123",
            links.verifyLogin("abc123"));
    }

    /** 尾端斜線與前後空白不得造成重複斜線 */
    @Test
    void trailingSlashIsNormalized() {
        AdminSiteLinks links = new AdminSiteLinks("  https://admin.springai.world/  ");

        assertEquals("https://admin.springai.world/api/admin/login/verify?t=abc123",
            links.verifyLogin("abc123"));
    }

    /** 產生的連結必須是絕對網址（以 https:// 開頭），避免 HTML 郵件中相對路徑無法點擊的問題 */
    @Test
    void verifyLoginReturnsAbsoluteUrl() {
        AdminSiteLinks links = new AdminSiteLinks("https://admin.springai.world");

        String result = links.verifyLogin("tok");
        assertTrue(result.startsWith("https://"),
            "連結應以 https:// 開頭以確保是絕對網址");
    }
}
