package world.springai.survey.reader;

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
 * 讀者登入態：以 JWT 承載 reader id，放在 httpOnly cookie。
 *
 * <p>放 cookie 而非 localStorage 是因為 httpOnly 讓 XSS 無法讀取 session；
 * 前端與後端同源（同一個 Spring Boot），所以不需處理跨域 cookie。</p>
 */
@Service
public class ReaderSessionService {

    private static final Logger log = LoggerFactory.getLogger(ReaderSessionService.class);

    /** session cookie 名稱 */
    public static final String COOKIE_NAME = "reader_session";

    /** JWT 簽章金鑰（HS256） */
    private final SecretKey key;
    /** 登入態有效天數 */
    private final int ttlDays;
    /**
     * cookie 是否帶 Secure 旗標。
     * 依對外網址是否為 https 自動決定：Secure cookie 在 http 下會被瀏覽器丟棄，
     * 本機開發就永遠登不進去；改用自動判斷可避免多一個設定項又忘記在正式環境開啟。
     *
     * <p>判斷時會先 trim 再忽略大小寫比對：${app.reader.base-url} 來自環境變數，
     * 可能帶前導/尾部空白或不同的大小寫，須強健處理以免誤判為非 https 而遺漏安全旗標。</p>
     */
    private final boolean secureCookie;

    /** 注入 JWT 秘鑰、效期與對外網址 */
    public ReaderSessionService(@Value("${app.reader.jwt-secret}") String secret,
                                @Value("${app.reader.jwt-ttl-days}") int ttlDays,
                                @Value("${app.reader.base-url:${app.public-base-url}}") String publicBaseUrl) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlDays = ttlDays;
        // trim 後忽略大小寫比對，確保環境變數帶空白或不同大小寫時也能正確判斷 Secure 旗標
        this.secureCookie = publicBaseUrl != null && publicBaseUrl.trim().toLowerCase().startsWith("https://");
    }

    /** 簽發 JWT，subject 為 reader id，效期為 ttlDays 天 */
    public String issueJwt(Long readerId, OffsetDateTime now) {
        return Jwts.builder()
            .subject(String.valueOf(readerId))
            .issuedAt(Date.from(now.toInstant()))
            .expiration(Date.from(now.plusDays(ttlDays).toInstant()))
            .signWith(key)
            .compact();
    }

    /**
     * 從 JWT 讀出 reader id；簽章不符、過期、格式錯誤一律回 empty。
     *
     * <p>刻意不拋例外：無效的 session 應被當成「未登入」處理，而不是讓請求變成 500。</p>
     */
    public Optional<Long> readReaderId(String jwt, OffsetDateTime now) {
        if (!StringUtils.hasText(jwt)) {
            return Optional.empty();
        }
        try {
            String subject = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(now.toInstant())) // 由呼叫端決定「現在」，方便測試過期
                .build()
                .parseSignedClaims(jwt)
                .getPayload()
                .getSubject();
            return Optional.of(Long.valueOf(subject));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("讀者 session 無效：{}", e.getMessage());
            return Optional.empty();
        }
    }

    /** 組 session cookie：httpOnly 防 XSS、SameSite=Lax 防 CSRF、Secure 依對外網址決定 */
    public ResponseCookie buildSessionCookie(String jwt) {
        return ResponseCookie.from(COOKIE_NAME, jwt)
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ofDays(ttlDays))
            .build();
    }

    /** 組登出用的清除 cookie（同名、空值、立即過期） */
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
