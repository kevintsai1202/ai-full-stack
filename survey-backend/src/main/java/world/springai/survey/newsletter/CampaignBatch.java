package world.springai.survey.newsletter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** 一篇電子報的一次實際寄送批次，對應 campaign_batch。 */
@Entity
@Table(name = "campaign_batch")
public class CampaignBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(nullable = false)
    private String mode;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(nullable = false)
    private String status;

    @Column(name = "requested_count", nullable = false)
    private int requestedCount;

    @Column(name = "accepted_count", nullable = false)
    private int acceptedCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    /** JPA 使用的無參數建構子。 */
    protected CampaignBatch() {
    }

    /** 建立新寄送批次，統計會在寄送結束後更新。 */
    public CampaignBatch(
            Long campaignId,
            String mode,
            OffsetDateTime scheduledAt,
            String status,
            int requestedCount) {
        this.campaignId = campaignId;
        this.mode = mode;
        this.scheduledAt = scheduledAt;
        this.status = status;
        this.requestedCount = requestedCount;
    }

    public Long getId() { return id; }
    public Long getCampaignId() { return campaignId; }
    public String getMode() { return mode; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public String getStatus() { return status; }
    public int getRequestedCount() { return requestedCount; }
    public int getAcceptedCount() { return acceptedCount; }
    public int getFailedCount() { return failedCount; }
    public int getSkippedCount() { return skippedCount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }

    /** 寄送完成後一次寫入統計與最終狀態。 */
    public void complete(String status, int accepted, int failed, int skipped) {
        this.status = status;
        this.acceptedCount = accepted;
        this.failedCount = failed;
        this.skippedCount = skipped;
        this.completedAt = OffsetDateTime.now();
    }

    /** 取消排程批次。 */
    public void cancel(int cancelled, int failed) {
        this.status = failed == 0 ? "cancelled" : "partial";
        this.acceptedCount = cancelled;
        this.failedCount = failed;
        this.completedAt = OffsetDateTime.now();
    }
}
