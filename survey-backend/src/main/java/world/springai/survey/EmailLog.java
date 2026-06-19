package world.springai.survey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** 寄送記錄實體，對應資料表 email_log */
@Entity
@Table(name = "email_log")
public class EmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String recipient;

    private String subject;
    private String type;

    @Column(name = "provider_message_id")
    private String providerMessageId;

    @Column(nullable = false)
    private String status;

    private String error;

    @Column(name = "campaign_id")
    private Long campaignId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** JPA 需要的無參數建構子 */
    protected EmailLog() {
    }

    /** 建立一筆寄送記錄（不指定 campaign，campaignId 為 null） */
    public EmailLog(String recipient, String subject, String type,
                    String providerMessageId, String status, String error) {
        this(recipient, subject, type, providerMessageId, status, error, null);
    }

    /** 建立一筆寄送記錄（可指定所屬 campaign） */
    public EmailLog(String recipient, String subject, String type,
                    String providerMessageId, String status, String error, Long campaignId) {
        this.recipient = recipient;
        this.subject = subject;
        this.type = type;
        this.providerMessageId = providerMessageId;
        this.status = status;
        this.error = error;
        this.campaignId = campaignId;
    }

    public Long getId() { return id; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getType() { return type; }
    public String getProviderMessageId() { return providerMessageId; }
    public String getStatus() { return status; }
    public String getError() { return error; }
    public Long getCampaignId() { return campaignId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setStatus(String status) { this.status = status; }
}
