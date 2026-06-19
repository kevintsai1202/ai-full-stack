package world.springai.survey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/** 退訂連結防偽：以 HMAC-SHA256 對正規化 email 簽發/驗證無狀態 token */
@Component
public class UnsubscribeTokenService {

    /** HMAC 秘鑰，由環境變數注入 */
    private final String secret;

    /** 注入退訂秘鑰（測試可直接以建構子傳入） */
    public UnsubscribeTokenService(@Value("${app.unsubscribe-secret}") String secret) {
        this.secret = secret;
    }

    /** 對 email 正規化（trim + 轉小寫）後算 HMAC-SHA256，回 Base64 URL-safe 無 padding */
    public String sign(String email) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(normalize(email).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("無法簽發退訂 token", e);
        }
    }

    /** 常數時間比對驗證 token；token 為空或不符一律回 false，不拋例外 */
    public boolean verify(String email, String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        String expected = sign(email);
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            token.getBytes(StandardCharsets.UTF_8));
    }

    /** email 正規化：去前後空白並轉小寫，確保簽發與驗證基準一致 */
    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
