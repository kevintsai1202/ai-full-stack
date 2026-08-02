package world.springai.survey.promo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 版位實體，對應資料表 promo_placement，代表「一則提案排進某一期電子報」的關聯。
 *
 * <p>{@code campaignId} 建立時為 {@code null}：編輯器把提案插入草稿電子報時，
 * 對應的 {@code campaign} 列尚不存在，要等對帳（寄送排程確立）時才回填綁定，
 * 見設計文件「campaign_id 延後綁定」。</p>
 */
@Entity
@Table(name = "promo_placement")
public class PromoPlacement {

    /** 草稿：已插入編輯器但尚未綁定期別 */
    public static final String STATUS_DRAFT = "DRAFT";
    /** 已綁定期別並確立排程 */
    public static final String STATUS_COMMITTED = "COMMITTED";
    /** 已從編輯器移除 */
    public static final String STATUS_REMOVED = "REMOVED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所屬電子報期別，建立當下為 null，對帳時才綁定 */
    @Column(name = "campaign_id")
    private Long campaignId;

    /** 對應的提案 */
    @Column(name = "proposal_id", nullable = false)
    private Long proposalId;

    /** 版位狀態，取本類的 STATUS_* 常數 */
    @Column(nullable = false)
    private String status;

    /** 綁定期別（確立排程）的時間 */
    @Column(name = "committed_at")
    private OffsetDateTime committedAt;

    /** 建立時間，由資料庫維護 */
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** JPA 需要的無參數建構子 */
    protected PromoPlacement() {
    }

    /** 建立一筆草稿版位（campaignId 為 null、status 預設 DRAFT） */
    public PromoPlacement(Long proposalId) {
        this.proposalId = proposalId;
        this.campaignId = null;
        this.status = STATUS_DRAFT;
    }

    public Long getId() { return id; }

    public Long getCampaignId() { return campaignId; }
    public void setCampaignId(Long campaignId) { this.campaignId = campaignId; }

    public Long getProposalId() { return proposalId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getCommittedAt() { return committedAt; }
    public void setCommittedAt(OffsetDateTime committedAt) { this.committedAt = committedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
