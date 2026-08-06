package world.springai.survey.admin;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Optional;

/**
 * 管理後台的登入態：簽發／解析 JWT 並組 session cookie。
 *
 * <p>刻意與 {@code ReaderSessionService} 分離並使用不同秘鑰：讀者 token 的簽發若
 * 出現瑕疵，不應蔓延到權限更大的後台。</p>
 */
@Service
public class AdminSessionService {

    private static final Logger log = LoggerFactory.getLogger(AdminSessionService.class);

    /** session cookie 名稱 */
    public static final String COOKIE_NAME = "admin_session";

    /** JWT 簽章金鑰（HS256） */
    private final SecretKey key;
    /** 登入態有效天數 */
    private final int ttlDays;
    /** cookie 是否帶 Secure；依對外網址是否為 https 自動決定，本機 http 下不可帶否則會被丟棄 */
    private final boolean secureCookie;

    /** 注入 JWT 秘鑰、效期與後台對外網址 */
    public AdminSessionService(@Value("${app.admin.jwt-secret}") String secret,
                               @Value("${app.admin.jwt-ttl-days}") int ttlDays,
                               @Value("${app.admin.base-url:${app.public-base-url}}") String baseUrl) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlDays = ttlDays;
        this.secureCookie = baseUrl != null && baseUrl.trim().toLowerCase().startsWith("https://");
    }

    /** 簽發 JWT，subject 為管理者 email */
    public String issueJwt(String email, OffsetDateTime now) {
        return Jwts.builder()
            .subject(email)
            .issuedAt(Date.from(now.toInstant()))
            .expiration(Date.from(now.plusDays(ttlDays).toInstant()))
            .signWith(key)
            .compact();
    }

    /** 從 JWT 讀出 email；簽章不符、過期、格式錯誤一律回 empty，不拋例外 */
    public Optional<String> readEmail(String jwt, OffsetDateTime now) {
        if (!StringUtils.hasText(jwt)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(now.toInstant()))
                .build()
                .parseSignedClaims(jwt)
                .getPayload()
                .getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("管理者 session 無效：{}", e.getMessage());
            return Optional.empty();
        }
    }

    /** 組 session cookie：httpOnly 防 XSS、SameSite=Lax 防 CSRF */
    public ResponseCookie buildSessionCookie(String jwt) {
        return ResponseCookie.from(COOKIE_NAME, jwt)
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ofDays(ttlDays))
            .build();
    }

    /** 組登出用的清除 cookie */
    public ResponseCookie buildClearCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ZERO)
            .build();
    }
}
