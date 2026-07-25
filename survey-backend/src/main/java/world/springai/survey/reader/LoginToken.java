package world.springai.survey.reader;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * magic link 一次性登入 token 實體，對應資料表 login_token。
 *
 * <p>刻意不用 UnsubscribeTokenService 的無狀態 HMAC：那個簽章沒有到期概念也無法作廢，
 * 對退訂連結是特性（永久有效才對），對登入則是漏洞。因此登入 token 走資料庫，
 * 具備 expires_at 與 used_at。</p>
 *
 * <p>只存 SHA-256 雜湊，明文 token 僅出現在寄出的信裡——資料庫外洩時 token 不可用。</p>
 */
@Entity
@Table(name = "login_token")
public class LoginToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 明文 token 的 SHA-256 雜湊（Base64 URL-safe） */
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    /** 要登入的 email，一律正規化為小寫 */
    @Column(nullable = false)
    private String email;

    /** 到期時間 */
    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    /** 使用時間；非 NULL 即已使用，不可重用 */
    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    /** 建立時間，由資料庫維護 */
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** JPA 需要的無參數建構子 */
    protected LoginToken() {
    }

    /** 建立一筆待使用的登入 token */
    public LoginToken(String tokenHash, String email, OffsetDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.email = email;
        this.expiresAt = expiresAt;
    }

    /** 是否已被使用過 */
    public boolean isUsed() {
        return usedAt != null;
    }

    /** 相對於指定時間是否已過期 */
    public boolean isExpired(OffsetDateTime now) {
        return !expiresAt.isAfter(now);
    }

    /** 標記為已使用 */
    public void markUsed(OffsetDateTime at) {
        this.usedAt = at;
    }

    public Long getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public String getEmail() { return email; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getUsedAt() { return usedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
