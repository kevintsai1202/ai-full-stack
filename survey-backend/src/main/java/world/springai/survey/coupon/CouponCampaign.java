package world.springai.survey.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 課程優惠券活動實體，對應資料表 coupon_campaign。
 *
 * <p>單一活動＝一組固定欄位套版內容（課程名／文案／連結／優惠碼／期限）
 * ＋一份問卷名單快照條件（{@code answerFilter}）。建立時為 {@code DRAFT}，
 * 首次寄送成功後轉為 {@code SENT}；SENT 之後仍可補寄（由 email_log 冪等把關），
 * 因此 {@code status} 只有這兩態，不需要額外的「已補寄」狀態。</p>
 */
@Entity
@Table(name = "coupon_campaign")
public class CouponCampaign {

    /** 草稿：尚未成功寄出過 */
    public static final String STATUS_DRAFT = "DRAFT";
    /** 已寄：至少成功寄送過一次（仍可補寄） */
    public static final String STATUS_SENT = "SENT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 課程名稱 */
    @Column(name = "course_name", nullable = false)
    private String courseName;

    /** 推薦文案 */
    @Column(nullable = false)
    private String pitch;

    /** 課程連結，DB 端有 ck_coupon_course_url CHECK 限定 https:// 開頭 */
    @Column(name = "course_url", nullable = false)
    private String courseUrl;

    /** 優惠碼（平台端建碼，本系統只負責寄，不驗證兌換） */
    @Column(name = "coupon_code", nullable = false)
    private String couponCode;

    /** 優惠期限，可不填（不限期） */
    @Column(name = "expires_at")
    private LocalDate expiresAt;

    /** 名單來源問卷 key */
    @Column(name = "form_key", nullable = false)
    private String formKey;

    /**
     * 建立時的答案篩選條件快照，以 JSON 字串儲存於 jsonb 欄，空條件為 {@code "{}"}。
     *
     * <p><b>已比對專案內既有型別化 jsonb 前例</b>：{@code SurveyResponse.answers}
     * 用 {@code Map<String, Object>}＋{@code @JdbcTypeCode}、{@code Campaign.filterJson}
     * 用型別化 POJO（{@code AudienceSearchService.Filters}）＋{@code @JdbcTypeCode}——
     * 兩者都是「讀出後即拿來當 Java 物件用」的欄位。本欄位語意不同：它是**條件快照**，
     * 補寄時要原封不動重放同一份查詢條件，若改成型別化物件，等於「反序列化再序列化」
     * 一輪，任何欄位鍵序調整或型別演進都可能讓補寄口徑與建立當下悄悄產生落差；
     * 刻意採 {@code String} 直通，讀寫之間不經過任何 Java 型別的再解析。
     * {@code @JdbcTypeCode(SqlTypes.JSON)} 讓 Hibernate 6 對 String 欄位改採「原文直通」
     * 寫入 jsonb，不會被誤當成需要再包一層 JSON 字串引號的一般字串。</p>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer_filter", nullable = false, columnDefinition = "jsonb")
    private String answerFilter;

    /** 活動狀態：DRAFT / SENT，DB 端有 ck_coupon_status CHECK 同步把關 */
    @Column(nullable = false)
    private String status = STATUS_DRAFT;

    /** 首次寄送成功時間，尚未寄送為 null */
    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    /** 累計成功寄出封數 */
    @Column(name = "sent_count", nullable = false)
    private int sentCount;

    /** 建立時間，由 {@link #onCreate()} 寫回實體欄位（不可用 @CreationTimestamp——
     *  該註解只注入 INSERT 語句、不寫回實體，同交易後續 UPDATE 會把值洗成 NULL） */
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /** 更新時間，同上理由改用 @PrePersist 寫回，UPDATE 時的維護交由呼叫端顯式設定 */
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** JPA 需要的無參建構子 */
    protected CouponCampaign() {
    }

    /**
     * 建立一筆新的優惠券活動（狀態固定從 DRAFT 起始）。
     *
     * @param courseName   課程名稱
     * @param pitch        推薦文案
     * @param courseUrl    課程連結（須 https:// 開頭）
     * @param couponCode   優惠碼
     * @param expiresAt    優惠期限，可為 null（不限期）
     * @param formKey      名單來源問卷 key
     * @param answerFilter 答案篩選條件快照，JSON 字串，空條件為 "{}"
     */
    public CouponCampaign(String courseName, String pitch, String courseUrl,
                           String couponCode, LocalDate expiresAt,
                           String formKey, String answerFilter) {
        this.courseName = courseName;
        this.pitch = pitch;
        this.courseUrl = courseUrl;
        this.couponCode = couponCode;
        this.expiresAt = expiresAt;
        this.formKey = formKey;
        this.answerFilter = answerFilter;
        // status 維持欄位宣告處的預設值 STATUS_DRAFT，不在此重複賦值
    }

    /** 新增前補上建立/更新時間（寫回實體欄位，避免同交易後續 UPDATE 洗掉） */
    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public String getCourseName() {
        return courseName;
    }

    public String getPitch() {
        return pitch;
    }

    public String getCourseUrl() {
        return courseUrl;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public LocalDate getExpiresAt() {
        return expiresAt;
    }

    public String getFormKey() {
        return formKey;
    }

    public String getAnswerFilter() {
        return answerFilter;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }

    public int getSentCount() {
        return sentCount;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** 更新活動狀態（寄送成功後轉為 SENT） */
    public void setStatus(String status) {
        this.status = status;
    }

    /** 記錄首次寄送成功時間 */
    public void setSentAt(OffsetDateTime sentAt) {
        this.sentAt = sentAt;
    }

    /** 更新累計寄出封數 */
    public void setSentCount(int sentCount) {
        this.sentCount = sentCount;
    }
}
