package world.springai.survey.promo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

/**
 * 工商轉址連結的收件人 token：{@code base64url(email) + "." + HMAC("promo|" + email)}。
 *
 * <p><b>為什麼要自包含 email</b>：HMAC 單向不可逆，轉址端點只拿得到 token，
 * 必須能從 token 本身取回 email 才能重算簽章驗證（spec §5）。</p>
 *
 * <p><b>domain separation</b>：沿用 {@code app.unsubscribe-secret} 同一把 secret，
 * 但簽名內容加 {@code "promo|"} 前綴——promo token 與退訂 token 不可互換冒用。</p>
 */
@Component
public class PromoRecipientTokenService {

    /** 寄送時每收件人替換的佔位符；全案唯一來源 */
    public static final String PLACEHOLDER = "__PROMO_RT__";

    /** HMAC 秘鑰（與退訂共用，簽名前綴不同） */
    private final String secret;

    /** 注入秘鑰（測試可直接以建構子傳入） */
    public PromoRecipientTokenService(@Value("${app.unsubscribe-secret}") String secret) {
        this.secret = secret;
    }

    /** 簽發：email 正規化（trim＋小寫）後編碼並簽章 */
    public String issue(String email) {
        String normalized = normalize(email);
        String b64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(normalized.getBytes(StandardCharsets.UTF_8));
        return b64 + "." + hmac(normalized);
    }

    /** 驗證：格式不符、解碼失敗、簽章不符一律回 empty，不拋例外 */
    public Optional<String> verify(String token) {
        if (!StringUtils.hasText(token)) return Optional.empty();
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) return Optional.empty();
        String email;
        try {
            email = new String(Base64.getUrlDecoder().decode(token.substring(0, dot)),
                StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String normalized = normalize(email);
        boolean ok = MessageDigest.isEqual(
            hmac(normalized).getBytes(StandardCharsets.UTF_8),
            token.substring(dot + 1).getBytes(StandardCharsets.UTF_8));
        return ok ? Optional.of(normalized) : Optional.empty();
    }

    /** HMAC-SHA256（含 promo| 前綴），Base64 URL-safe 無 padding */
    private String hmac(String normalizedEmail) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(("promo|" + normalizedEmail).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("無法簽發 promo token", e);
        }
    }

    /** email 正規化：與 UnsubscribeTokenService 同基準（trim＋小寫） */
    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
