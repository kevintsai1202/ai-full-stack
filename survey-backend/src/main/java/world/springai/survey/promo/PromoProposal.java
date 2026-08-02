package world.springai.survey.promo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 工商時間提案實體，對應資料表 promo_proposal。
 *
 * <p>讀者提交、管理員審核；{@code unitCost} 是申請當下單價快照（退點以此計算，
 * 不受日後單價調整影響）。{@code placementUsed} 是已排入版位的次數，
 * 受 {@code placementQuota} 上限約束，扣／還配額一律走 repository 的條件式 UPDATE，
 * 不可用 read-modify-write（理由同 {@code ReaderRepository.deductCredits}）。</p>
 */
@Entity
@Table(name = "promo_proposal")
public class PromoProposal {

    /** 待審核 */
    public static final String STATUS_PENDING = "PENDING";
    /** 已核准，可排入版位 */
    public static final String STATUS_APPROVED = "APPROVED";
    /** 已拒絕（全額退點） */
    public static final String STATUS_REJECTED = "REJECTED";
    /** 已封存（退還未投放餘額） */
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 免費方案 */
    public static final String PRICING_TYPE_FREE = "FREE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 提案人（讀者） */
    @Column(name = "reader_id", nullable = false)
    private Long readerId;

    /** 聯絡人姓名 */
    @Column(name = "contact_name", nullable = false)
    private String contactName;

    /** 聯絡人 email */
    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    /** 提案標題 */
    @Column(nullable = false)
    private String title;

    /** 內文 */
    @Column(name = "body_text", nullable = false)
    private String bodyText;

    /** 連結文字（CTA） */
    @Column(name = "link_text", nullable = false)
    private String linkText;

    /** 連結網址，必須是 https */
    @Column(name = "link_url", nullable = false)
    private String linkUrl;

    /** 審核狀態，取本類的 STATUS_* 常數 */
    @Column(nullable = false)
    private String status;

    /** 審核備註（拒絕原因等） */
    @Column(name = "review_note")
    private String reviewNote;

    /** 審核時間 */
    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    /** 版位配額上限（1~3，由 DB CHECK 約束） */
    @Column(name = "placement_quota", nullable = false)
    private int placementQuota;

    /** 已使用的版位次數，扣／還一律透過 repository 條件式 UPDATE */
    @Column(name = "placement_used", nullable = false)
    private int placementUsed;

    /** 申請當下的單價快照，退點金額以此計算 */
    @Column(name = "unit_cost", nullable = false)
    private int unitCost;

    /** 計價方式，目前僅 FREE */
    @Column(name = "pricing_type", nullable = false)
    private String pricingType;

    /** 付款狀態（非 FREE 方案使用，目前保留欄位） */
    @Column(name = "payment_status")
    private String paymentStatus;

    /** 建立時間，由資料庫維護 */
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** 最後更新時間，由資料庫維護；本案不依賴此欄位排序，不提供 setter */
    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    /** JPA 需要的無參數建構子 */
    protected PromoProposal() {
    }

    /** 建立一筆待審核提案（status 預設 PENDING、pricingType 預設 FREE、placementUsed 預設 0） */
    public PromoProposal(Long readerId, String contactName, String contactEmail, String title,
                          String bodyText, String linkText, String linkUrl,
                          int placementQuota, int unitCost) {
        this.readerId = readerId;
        this.contactName = contactName;
        this.contactEmail = contactEmail;
        this.title = title;
        this.bodyText = bodyText;
        this.linkText = linkText;
        this.linkUrl = linkUrl;
        this.placementQuota = placementQuota;
        this.unitCost = unitCost;
        this.status = STATUS_PENDING;
        this.pricingType = PRICING_TYPE_FREE;
        this.placementUsed = 0;
    }

    public Long getId() { return id; }

    public Long getReaderId() { return readerId; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBodyText() { return bodyText; }
    public void setBodyText(String bodyText) { this.bodyText = bodyText; }

    public String getLinkText() { return linkText; }
    public void setLinkText(String linkText) { this.linkText = linkText; }

    public String getLinkUrl() { return linkUrl; }
    public void setLinkUrl(String linkUrl) { this.linkUrl = linkUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }

    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public int getPlacementQuota() { return placementQuota; }
    public void setPlacementQuota(int placementQuota) { this.placementQuota = placementQuota; }

    public int getPlacementUsed() { return placementUsed; }
    public void setPlacementUsed(int placementUsed) { this.placementUsed = placementUsed; }

    public int getUnitCost() { return unitCost; }
    public void setUnitCost(int unitCost) { this.unitCost = unitCost; }

    public String getPricingType() { return pricingType; }
    public void setPricingType(String pricingType) { this.pricingType = pricingType; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public OffsetDateTime getCreatedAt() { return createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
