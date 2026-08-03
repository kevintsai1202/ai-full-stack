package world.springai.survey.form;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 信中一鍵投票（含讀者頁快投）實體，對應資料表 survey_vote。
 *
 * <p><b>具名一人一票</b>：{@code form_key + identity_type + identity_key} 由
 * V21 migration 的 partial unique index {@code uq_survey_vote_identity} 限制
 * （排除 identity_type='ANON'），跨期同問卷仍只算一票，後投視為改票（upsert 覆蓋）。
 * 匿名（{@code IDENTITY_ANON}，identityKey 為 null）不受此限制。</p>
 */
@Entity
@Table(name = "survey_vote")
public class SurveyVote {

    /** 投票管道：信件內一鍵點擊 */
    public static final String CHANNEL_EMAIL = "EMAIL";
    /** 投票管道：讀者頁網頁快投 */
    public static final String CHANNEL_WEB = "WEB";
    /** 身分類型：電子報收件者（以信件收件位址識別） */
    public static final String IDENTITY_RECIPIENT = "RECIPIENT";
    /** 身分類型：已登入讀者 */
    public static final String IDENTITY_READER = "READER";
    /** 身分類型：匿名（不受一人一票唯一約束限制） */
    public static final String IDENTITY_ANON = "ANON";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所屬問卷表單的 key */
    @Column(name = "form_key", nullable = false)
    private String formKey;

    /** 所屬問卷欄位的 key（信中一鍵指定的 select 單選欄位） */
    @Column(name = "field_key", nullable = false)
    private String fieldKey;

    /** 投票選項值 */
    @Column(name = "option_value", nullable = false)
    private String optionValue;

    /** 所屬電子報活動；非信件管道（純網頁快投）時可為 null */
    @Column(name = "campaign_id")
    private Long campaignId;

    /** 投票管道，取本類 CHANNEL_* 常數 */
    @Column(nullable = false)
    private String channel;

    /** 身分類型，取本類 IDENTITY_* 常數 */
    @Column(name = "identity_type", nullable = false)
    private String identityType;

    /** 身分識別鍵（RECIPIENT/READER 用收件位址或讀者 id；ANON 為 null） */
    @Column(name = "identity_key")
    private String identityKey;

    /** 建立時間，由 @PrePersist 寫回實體欄位（勿用 @CreationTimestamp，見知識庫教訓） */
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    /** 最後更新時間，由 @PrePersist 寫回實體欄位；改票（upsert）時由呼叫端透過 setUpdatedAt 更新 */
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** JPA 需要的無參數建構子 */
    protected SurveyVote() {
    }

    /** 建構子；updatedAt/createdAt 由 @PrePersist 設（勿用 @CreationTimestamp，見知識庫教訓） */
    public SurveyVote(String formKey, String fieldKey, String optionValue,
                       Long campaignId, String channel, String identityType, String identityKey) {
        this.formKey = formKey;
        this.fieldKey = fieldKey;
        this.optionValue = optionValue;
        this.campaignId = campaignId;
        this.channel = channel;
        this.identityType = identityType;
        this.identityKey = identityKey;
    }

    /**
     * 新增前補上建立與更新時間。
     *
     * <p>用 {@code @PrePersist} 而非 {@code @CreationTimestamp} 的理由：
     * {@code @CreationTimestamp} 的值只注入 INSERT 語句、不寫回實體欄位；
     * 若同一交易內接著再對同一實體 {@code save()}（例如改票路徑），該次 UPDATE
     * 會帶 {@code create_time=NULL} 把值洗掉。{@code @PrePersist} 把值寫在
     * 實體欄位上，後續 UPDATE 自然帶回正確值。</p>
     */
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

    public Long getId() { return id; }
    public String getFormKey() { return formKey; }
    public String getFieldKey() { return fieldKey; }
    public String getOptionValue() { return optionValue; }
    public Long getCampaignId() { return campaignId; }
    public String getChannel() { return channel; }
    public String getIdentityType() { return identityType; }
    public String getIdentityKey() { return identityKey; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    /** 改票時更新選項值 */
    public void setOptionValue(String optionValue) { this.optionValue = optionValue; }

    /**
     * 改票時更新所屬欄位（M1 修正）：信中一鍵題可能被改綁到不同欄位，
     * 若既有列的 fieldKey 未同步更新，會留下 optionValue 屬於新欄位、
     * fieldKey 卻仍指向舊欄位的自相矛盾列。
     */
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }

    /** 改票時更新所屬活動 */
    public void setCampaignId(Long campaignId) { this.campaignId = campaignId; }

    /** 改票時更新投票管道 */
    public void setChannel(String channel) { this.channel = channel; }

    /** 改票（upsert 覆蓋）時由呼叫端顯式更新最後更新時間 */
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
