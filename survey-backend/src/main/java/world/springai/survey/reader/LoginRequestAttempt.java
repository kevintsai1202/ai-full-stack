package world.springai.survey.reader;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** Magic Link 公開請求的防濫用紀錄；來源 IP 只保存不可逆雜湊。 */
@Entity
@Table(name = "login_request_attempt")
public class LoginRequestAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 正規化來源位址的 SHA-256 Base64URL 雜湊。 */
    @Column(name = "ip_hash", nullable = false)
    private String ipHash;

    /** 請求時間；由服務傳入，避免應用程式與資料庫時鐘混用。 */
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /** JPA 需要的無參數建構子。 */
    protected LoginRequestAttempt() {
    }

    /** 建立一筆已允許的登入信請求。 */
    public LoginRequestAttempt(String ipHash, OffsetDateTime createdAt) {
        this.ipHash = ipHash;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getIpHash() { return ipHash; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
