package world.springai.survey.reader;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 讀者帳戶實體，對應資料表 reader。
 *
 * <p>刻意不與 survey_response 合併：survey_response 管「同意與來源」（名單中心的職責），
 * 本表管「帳戶與點數」，兩者以 email 關聯。合併會讓名單中心的 schema 綁上讀者端關注點。</p>
 *
 * <p><b>不變式</b>：本列存在不代表已確認訂閱。訂閱狀態一律查
 * survey_response.consent 與 unsubscribed。</p>
 */
@Entity
@Table(name = "reader")
public class Reader {

    /** 免費讀者 */
    public static final String TIER_FREE = "FREE";
    /** VIP 讀者：進階內容不需點數 */
    public static final String TIER_VIP = "VIP";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 讀者 email，一律正規化為小寫 */
    @Column(nullable = false, unique = true)
    private String email;

    /** 等級：FREE 或 VIP */
    @Column(nullable = false)
    private String tier = TIER_FREE;

    /** VIP 到期時間；NULL 表無限期（僅 tier=VIP 時有意義） */
    @Column(name = "vip_expires_at")
    private OffsetDateTime vipExpiresAt;

    /** 目前點數餘額，為 credit_txn 的物化總和 */
    @Column(nullable = false)
    private int credits = 0;

    /** 個人邀請碼 */
    @Column(name = "referral_code", nullable = false, unique = true)
    private String referralCode;

    /** 推薦人的 reader.id */
    @Column(name = "referred_by")
    private Long referredBy;

    /** 最後登入時間 */
    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    /** 建立時間，由資料庫維護 */
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** JPA 需要的無參數建構子 */
    protected Reader() {
    }

    /** 建立新讀者：預設 FREE、0 點，email 由呼叫端負責正規化為小寫 */
    public Reader(String email, String referralCode) {
        this.email = email;
        this.referralCode = referralCode;
    }

    /**
     * 是否為目前有效的 VIP。
     *
     * <p>不做自動降級排程（spec §13.5）：tier 保持 VIP 但到期時間已過時，
     * 一律在判斷當下視為 FREE。時間由呼叫端傳入，方便測試。</p>
     */
    public boolean isActiveVip(OffsetDateTime now) {
        if (!TIER_VIP.equals(tier)) {
            return false;
        }
        return vipExpiresAt == null || vipExpiresAt.isAfter(now);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
    public OffsetDateTime getVipExpiresAt() { return vipExpiresAt; }
    public void setVipExpiresAt(OffsetDateTime vipExpiresAt) { this.vipExpiresAt = vipExpiresAt; }
    public int getCredits() { return credits; }
    public void setCredits(int credits) { this.credits = credits; }
    public String getReferralCode() { return referralCode; }
    public void setReferralCode(String referralCode) { this.referralCode = referralCode; }
    public Long getReferredBy() { return referredBy; }
    public void setReferredBy(Long referredBy) { this.referredBy = referredBy; }
    public OffsetDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(OffsetDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
