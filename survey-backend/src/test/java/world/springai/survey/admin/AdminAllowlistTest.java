package world.springai.survey.admin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 白名單比對：大小寫與空白容錯、未設定時停用 */
class AdminAllowlistTest {

    /** 名單內的 email 應通過，且比對不分大小寫、忽略前後空白 */
    @Test
    void matchesIgnoringCaseAndWhitespace() {
        AdminAllowlist allowlist = new AdminAllowlist(" Kevin@Example.com , other@example.com ");

        assertTrue(allowlist.isAdmin("kevin@example.com"));
        assertTrue(allowlist.isAdmin("  OTHER@EXAMPLE.COM  "));
    }

    /** 不在名單內的 email 一律拒絕 */
    @Test
    void rejectsUnlistedEmail() {
        AdminAllowlist allowlist = new AdminAllowlist("kevin@example.com");

        assertFalse(allowlist.isAdmin("attacker@example.com"));
        assertFalse(allowlist.isAdmin(null));
    }

    /** 未設定白名單時停用 JWT 登入，且任何 email 都不是管理者 */
    @Test
    void disabledWhenUnset() {
        AdminAllowlist allowlist = new AdminAllowlist("");

        assertFalse(allowlist.isEnabled());
        assertFalse(allowlist.isAdmin("kevin@example.com"));
    }
}
