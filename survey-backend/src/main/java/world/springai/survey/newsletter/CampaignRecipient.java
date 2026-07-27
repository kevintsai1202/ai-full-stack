package world.springai.survey.newsletter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** 一篇電子報對單一收件人的永久寄送狀態。 */
@Entity
@Table(name = "campaign_recipient")
public class CampaignRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "person_id")
    private Long personId;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(nullable = false)
    private String email;

    @Column(name = "email_normalized", nullable = false)
    private String emailNormalized;

    @Column(nullable = false)
    private String status;

    @Column(name = "provider_message_id")
    private String providerMessageId;

    private String error;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    /** JPA 使用的無參數建構子。 */
    protected CampaignRecipient() {
    }

    public Long getId() { return id; }
    public Long getCampaignId() { return campaignId; }
    public Long getPersonId() { return personId; }
    public Long getBatchId() { return batchId; }
    public String getEmail() { return email; }
    public String getEmailNormalized() { return emailNormalized; }
    public String getStatus() { return status; }
    public String getProviderMessageId() { return providerMessageId; }
    public String getError() { return error; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public OffsetDateTime getSentAt() { return sentAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
