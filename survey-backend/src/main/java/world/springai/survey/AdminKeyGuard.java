package world.springai.survey;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.admin.AdminAllowlist;
import world.springai.survey.admin.AdminSessionAccess;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;

/**
 * 集中管理後台端點的身分驗證；供所有 {@code /api/admin} 端點共用。
 *
 * <p><b>兩條路徑擇一通過</b>：機器（驗證腳本、CI）帶 {@code X-Admin-Key}，
 * 瀏覽器帶 {@code admin_session} cookie。方法簽名刻意維持不變，讓既有 78 處
 * 呼叫點與 9 支驗證腳本零改動。</p>
 */
@Component
public class AdminKeyGuard {

    /** 管理金鑰（環境變數設定） */
    private final String adminApiKey;
    /** admin session cookie 的讀取與驗證 */
    private final AdminSessionAccess sessionAccess;
    /** 管理者白名單；JWT 通過簽章驗證後仍須比對此白名單 */
    private final AdminAllowlist allowlist;

    /** 注入管理金鑰、session 存取與白名單 */
    public AdminKeyGuard(@Value("${app.admin-api-key}") String adminApiKey,
                         AdminSessionAccess sessionAccess,
                         AdminAllowlist allowlist) {
        this.adminApiKey = adminApiKey;
        this.sessionAccess = sessionAccess;
        this.allowlist = allowlist;
    }

    /** 金鑰或 admin session 任一通過即放行；皆無則 401 */
    public void verify(String key) {
        if (matchesApiKey(key)) {
            return;
        }
        if (hasValidSession()) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid admin credential");
    }

    /** 以固定時間比對金鑰，避免時序側通道 */
    private boolean matchesApiKey(String key) {
        return key != null && MessageDigest.isEqual(
            key.getBytes(StandardCharsets.UTF_8), adminApiKey.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 從當前請求取 cookie 驗證 session；email 須仍在白名單內。
     *
     * <p>白名單比對不可省略：白名單來自環境變數，管理者換人後改了變數，但被撤下的人
     * 手上的 JWT 仍在效期內。若只驗簽章不比對白名單，被撤權者可繼續持舊 JWT
     * 通過驗證直到 token 自然過期。</p>
     */
    private boolean hasValidSession() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return false;
        }
        return sessionAccess.readEmail(request, OffsetDateTime.now())
            .filter(allowlist::isAdmin)
            .isPresent();
    }

    /**
     * 取得當前請求。
     *
     * <p>透過 {@code RequestContextHolder} 取得而非由呼叫端傳入，是本方案的支點：
     * 若改為參數傳遞，78 處呼叫點全部都要改。</p>
     */
    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }
}
