package world.springai.survey;

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
}
