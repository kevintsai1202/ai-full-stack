package world.springai.survey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 集中管理 X-Admin-Key 驗證；供所有 /api/admin 端點共用，避免重複 */
@Component
public class AdminKeyGuard {

    private final String adminApiKey;

    /** 注入管理金鑰 */
    public AdminKeyGuard(@Value("${app.admin-api-key}") String adminApiKey) {
        this.adminApiKey = adminApiKey;
    }

    /** 以固定時間比對驗證金鑰；不符拋 401 */
    public void verify(String key) {
        if (key == null || !MessageDigest.isEqual(
                key.getBytes(StandardCharsets.UTF_8), adminApiKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid admin key");
        }
    }
}
