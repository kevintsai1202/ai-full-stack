package world.springai.survey.newsletter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** 電子報發送批次，對應資料表 campaign */
@Entity
@Table(name = "campaign")
public class Campaign {

    /** 基本內容：已確認訂閱者即可閱讀 */
    public static final String TIER_BASIC = "BASIC";
    /** 進階內容：需點數解鎖或 VIP 身分 */
    public static final String TIER_PREMIUM = "PREMIUM";

    /**
     * 模式：只發布到網頁、完全不寄信。
     *
     * <p>與既有的 {@code now}／{@code schedule} 併列於同一個 {@code mode} 欄位
     * （資料庫沒有列舉約束，是自由文字）。刻意用一個「不是寄送模式」的新值而非
     * 沿用 {@code now}：後台歷史列表直接顯示 {@code mode}，若沿用 {@code now}，
     * 這筆紀錄會被讀成「立即群發但只寄了 0 封」——那是一次失敗的群發，
     * 與事實（根本沒打算寄信）完全相反。</p>
     */
    public static final String MODE_PUBLISH = "publish";

    /**
     * 狀態：已發布到網頁。
     *
     * <p>同樣刻意不重用 {@code sent}／{@code failed}：沒有寄出任何信，說 sent 是謊；
     * 而 {@code failed}（{@code finalStatus} 對 accepted=0 的判斷）會讓管理者以為
     * 寄送出了問題而去重送。</p>
     */
    public static final String STATUS_PUBLISHED = "published";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String markdown;

    @Column(name = "body_html")
    private String bodyHtml;

    @Column(name = "filter_role")
    private String filterRole;

    @Column(name = "filter_interest")
    private String filterInterest;

    @Column(nullable = false)
    private String mode;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;

    @Column(name = "accepted_count", nullable = false)
    private int acceptedCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(nullable = false)
    private String status;

    /** 內容分級：BASIC 或 PREMIUM */
    @Column(nullable = false)
    private String tier = TIER_BASIC;

    /** PREMIUM 解鎖所需點數；BASIC 為 0。資料庫層有 CHECK 約束禁止 PREMIUM 卻為 0 */
    @Column(name = "credit_cost", nullable = false)
    private int creditCost = 0;

    /** 網頁網址片段，供 /r/news/{slug} 使用；NULL 表示不在 archive 中露出 */
    private String slug;

    /** 發布時間；非 NULL 才會出現在 archive */
    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    /** VIP 是否在信件中直接收到全文（階段 D 才會使用；第一版一律折疊） */
    @Column(name = "vip_full_in_mail", nullable = false)
    private boolean vipFullInMail = false;

    /** 本次寄送的參與度級別，逗號分隔（階段 F 才會使用），供補寄重建相同對象 */
    @Column(name = "filter_levels", nullable = false)
    private String filterLevels = "active";

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** JPA 需要的無參數建構子 */
    protected Campaign() {
    }

    /** 建立一筆發送批次（統計與狀態之後再更新） */
    public Campaign(String subject, String markdown, String bodyHtml,
                    String filterRole, String filterInterest,
                    String mode, OffsetDateTime scheduledAt,
                    int recipientCount, String status) {
        this.subject = subject;
        this.markdown = markdown;
        this.bodyHtml = bodyHtml;
        this.filterRole = filterRole;
        this.filterInterest = filterInterest;
        this.mode = mode;
        this.scheduledAt = scheduledAt;
        this.recipientCount = recipientCount;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getSubject() { return subject; }
    public String getMarkdown() { return markdown; }
    public String getBodyHtml() { return bodyHtml; }
    public String getFilterRole() { return filterRole; }
    public String getFilterInterest() { return filterInterest; }
    public String getMode() { return mode; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public int getRecipientCount() { return recipientCount; }
    public int getAcceptedCount() { return acceptedCount; }
    public int getFailedCount() { return failedCount; }
    public String getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setAcceptedCount(int acceptedCount) { this.acceptedCount = acceptedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
    public void setStatus(String status) { this.status = status; }

    // 以下 setter 供「修改未寄出的排程」就地更新使用（reschedule）
    public void setSubject(String subject) { this.subject = subject; }
    public void setMarkdown(String markdown) { this.markdown = markdown; }
    public void setBodyHtml(String bodyHtml) { this.bodyHtml = bodyHtml; }
    public void setFilterRole(String filterRole) { this.filterRole = filterRole; }
    public void setFilterInterest(String filterInterest) { this.filterInterest = filterInterest; }
    public void setScheduledAt(OffsetDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public void setRecipientCount(int recipientCount) { this.recipientCount = recipientCount; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
    public int getCreditCost() { return creditCost; }
    public void setCreditCost(int creditCost) { this.creditCost = creditCost; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
    public boolean isVipFullInMail() { return vipFullInMail; }
    public void setVipFullInMail(boolean vipFullInMail) { this.vipFullInMail = vipFullInMail; }
    public String getFilterLevels() { return filterLevels; }
    public void setFilterLevels(String filterLevels) { this.filterLevels = filterLevels; }

    /** 是否為進階內容（需點數或 VIP） */
    public boolean isPremium() {
        return TIER_PREMIUM.equals(tier);
    }

    /** 是否已發布（未發布者不出現在 archive） */
    public boolean isPublished() {
        return publishedAt != null;
    }
}
