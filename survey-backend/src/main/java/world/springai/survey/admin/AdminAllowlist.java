package world.springai.survey.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理者白名單：由環境變數 {@code ADMIN_EMAILS} 指定，逗號分隔。
 *
 * <p>刻意不進資料庫（決策 D1）：目前僅一位管理者，換人只需改 Zeabur 變數。
 * 未設定時 {@link #isEnabled()} 為 false，JWT 登入路徑停用而金鑰路徑照常，
 * 確保漏設變數時後台不會完全無法進入。</p>
 */
@Component
public class AdminAllowlist {

    /** 正規化後的管理者 email 集合（小寫、去空白） */
    private final Set<String> emails;

    /** 注入白名單設定 */
    public AdminAllowlist(@Value("${app.admin.emails:}") String rawEmails) {
        this.emails = rawEmails == null ? Set.of() : Arrays.stream(rawEmails.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(s -> s.toLowerCase())
            .collect(Collectors.toUnmodifiableSet());
    }

    /** 白名單是否已設定；未設定即停用 JWT 登入 */
    public boolean isEnabled() {
        return !emails.isEmpty();
    }

    /** 比對 email 是否為管理者；不分大小寫、忽略前後空白 */
    public boolean isAdmin(String email) {
        return email != null && emails.contains(email.trim().toLowerCase());
    }
}
