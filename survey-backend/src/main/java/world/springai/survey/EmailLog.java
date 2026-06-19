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

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** JPA 需要的無參數建構子 */
    protected EmailLog() {
    }

    /** 建立一筆寄送記錄 */
    public EmailLog(String recipient, String subject, String type,
                    String providerMessageId, String status, String error) {
        this.recipient = recipient;
        this.subject = subject;
        this.type = type;
        this.providerMessageId = providerMessageId;
        this.status = status;
        this.error = error;
    }

    public Long getId() { return id; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getType() { return type; }
    public String getProviderMessageId() { return providerMessageId; }
    public String getStatus() { return status; }
    public String getError() { return error; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
