package world.springai.survey.admin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** admin 登入連結必須指向後台網域，否則 cookie 會種在錯誤的 host */
class AdminSiteLinksTest {

    /** 連結應以設定的後台網址為基底，並帶上 token 查詢參數 */
    @Test
    void verifyLoginPointsToAdminHost() {
        AdminSiteLinks links = new AdminSiteLinks("https://admin.springai.world", "");

        assertEquals("https://admin.springai.world/api/admin/login/verify?t=abc123",
            links.verifyLogin("abc123"));
    }

    /** 尾端斜線與前後空白不得造成重複斜線 */
    @Test
    void trailingSlashIsNormalized() {
        AdminSiteLinks links = new AdminSiteLinks("  https://admin.springai.world/  ", "");

        assertEquals("https://admin.springai.world/api/admin/login/verify?t=abc123",
            links.verifyLogin("abc123"));
    }

    /** 產生的連結必須是絕對網址（以 https:// 開頭），避免 HTML 郵件中相對路徑無法點擊的問題 */
    @Test
    void verifyLoginReturnsAbsoluteUrl() {
        AdminSiteLinks links = new AdminSiteLinks("https://admin.springai.world", "");

        String result = links.verifyLogin("tok");
        assertTrue(result.startsWith("https://"),
            "連結應以 https:// 開頭以確保是絕對網址");
    }

    /**
     * ADMIN_ENTRY_HOST 有設但 ADMIN_BASE_URL 指向別的 host：必須判定為不一致。
     *
     * <p>這正是「只設了入口網域、漏設 base-url」的實際形狀——登入信連結會指向
     * 問卷網域，被入口過濾器擋成 404 或把 cookie 種錯 host，且 token 已被消耗。</p>
     */
    @Test
    void mismatchedEntryHostIsDetected() {
        assertTrue(AdminSiteLinks.entryHostMismatch(
            "https://survey.springai.world", "admin.springai.world"));
    }

    /** 未設定入口網域（空字串）時不得誤報：那是完全合法的預設部署形態 */
    @Test
    void emptyEntryHostIsNotAMismatch() {
        assertFalse(AdminSiteLinks.entryHostMismatch("https://survey.springai.world", ""));
        assertFalse(AdminSiteLinks.entryHostMismatch("https://survey.springai.world", "   "));
    }

    /** host 相同即為一致：port、大小寫與尾端斜線都不得造成假警告（假警告會讓真警告被忽略） */
    @Test
    void samePortOrCaseVariationsAreNotMismatches() {
        assertFalse(AdminSiteLinks.entryHostMismatch(
            "https://admin.springai.world", "admin.springai.world"));
        assertFalse(AdminSiteLinks.entryHostMismatch(
            "http://admin.springai.world:8080", "admin.springai.world"));
        assertFalse(AdminSiteLinks.entryHostMismatch(
            "https://ADMIN.springai.world", "admin.springai.world"));
        assertFalse(AdminSiteLinks.entryHostMismatch(
            "http://127.0.0.1:8080", "127.0.0.1:8080"));
    }
}
