package world.springai.survey.mail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 本機待寄佇列的一封信，對應資料表 scheduled_mail。
 *
 * <p>只有使用 Amazon SES 時才會寫入：SES 沒有排程 API，排程信與批次群發都先存在這裡，
 * 由 {@link ScheduledMailDispatcher} 到期後送出。狀態轉換一律走
 * {@link ScheduledMailRepository} 的條件式 UPDATE，不透過本實體的 setter，
 * 派送與取消才不會互相覆蓋。</p>
 */
@Entity
@Table(name = "scheduled_mail")
public class ScheduledMail {

    /** 待寄：等待到期 */
    public static final String STATUS_PENDING = "PENDING";
    /** 派送器已認領、正在呼叫 SES */
    public static final String STATUS_SENDING = "SENDING";
    /** 已交給 SES */
    public static final String STATUS_SENT = "SENT";
    /** 放棄重試或被 SES 拒絕 */
    public static final String STATUS_FAILED = "FAILED";
    /** 到期前被取消 */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 排程單封回傳給呼叫端的 provider id 前綴（後接本表 id） */
    public static final String SCHEDULE_PREFIX = "ses-sched-";
    /** 批次群發回傳給呼叫端的整批參照前綴 */
    public static final String BATCH_PREFIX = "ses-batch-";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** sendBatch 的整批參照；排程單封為 null */
    @Column(name = "batch_ref")
    private String batchRef;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false)
    private String subject;

    /** 已個人化的完整信件 HTML */
    @Column(nullable = false)
    private String html;

    /** 預定寄出時間 */
    @Column(name = "send_at", nullable = false)
    private OffsetDateTime sendAt;

    /** 下次可嘗試時間（初始等於 sendAt，暫時性錯誤後往後延） */
    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(nullable = false)
    private String status;

    /** 已嘗試次數（認領時 +1） */
    @Column(nullable = false)
    private int attempts;

    @Column(name = "provider_message_id")
    private String providerMessageId;

    private String error;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    /** JPA 需要的無參數建構子 */
    protected ScheduledMail() {
    }

    /** 建立一筆待寄信：狀態 PENDING、尚未嘗試、到期時間即預定寄出時間 */
    public ScheduledMail(String batchRef, String recipient, String subject, String html,
                         OffsetDateTime sendAt) {
        this.batchRef = batchRef;
        this.recipient = recipient;
        this.subject = subject;
        this.html = html;
        this.sendAt = sendAt;
        this.nextAttemptAt = sendAt;
        this.status = STATUS_PENDING;
        this.attempts = 0;
    }

    /**
     * 呼叫端手上的 provider id：批次信是整批參照，排程單封是 ses-sched-{id}。
     * email_log 存的就是這個值，派送失敗時用它回寫。
     */
    public String providerRef() {
        return batchRef != null ? batchRef : SCHEDULE_PREFIX + id;
    }

    /** 測試用：模擬資料庫指派的 id */
    void assignIdForTest(Long id) {
        this.id = id;
    }

    /** 測試用：模擬認領後的嘗試次數 */
    void setAttemptsForTest(int attempts) {
        this.attempts = attempts;
    }

    public Long getId() { return id; }
    public String getBatchRef() { return batchRef; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getHtml() { return html; }
    public OffsetDateTime getSendAt() { return sendAt; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getProviderMessageId() { return providerMessageId; }
    public String getError() { return error; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
