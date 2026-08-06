package world.springai.survey.newsletter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import world.springai.survey.audience.AudienceSearchService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** 電子報發送批次，對應資料表 campaign */
@Entity
@Table(name = "campaign")
public class Campaign {

    /** 基本內容：已確認訂閱者即可閱讀 */
    public static final String TIER_BASIC = "BASIC";
    /** 進階內容：需點數解鎖或 VIP 身分 */
    public static final String TIER_PREMIUM = "PREMIUM";
    /** 尚未到寄送時間、仍可修改或取消的排程 */
    public static final String STATUS_SCHEDULED = "scheduled";
    /** 已完成送交寄信商的寄送批次 */
    public static final String STATUS_SENT = "sent";
    /** 已由管理員取消的排程 */
    public static final String STATUS_CANCELLED = "cancelled";

    /**
     * 模式：只發布到網頁、完全不寄信。
     *
     * <p>與既有的 {@code now}／{@code schedule} 併列於同一個 {@code mode} 欄位
     * （資料庫沒有列舉約束，是自由文字）。刻意用一個「不是寄送模式」的新值而非
     * 沿用 {@code now}：後台歷史列表直接顯示 {@code mode}，若沿用 {@code now}，
     * 這筆紀錄會被讀成「立即群發但只寄了 0 封」——那是一次失敗的群發，
     * 與事實（根本沒打算寄信）完全相反。</p>
     */
    public static final String MODE_PUBLISH = "publish";

    /**
     * 狀態：已發布到網頁。
     *
     * <p>同樣刻意不重用 {@code sent}／{@code failed}：沒有寄出任何信，說 sent 是謊；
     * 而 {@code failed}（{@code finalStatus} 對 accepted=0 的判斷）會讓管理者以為
     * 寄送出了問題而去重送。</p>
     */
    public static final String STATUS_PUBLISHED = "published";

    /**
     * 狀態：已下架（曾經發布到網頁，目前對外不可見）。
     *
     * <p><b>為什麼需要一個新的狀態值</b>：下架原本只把 {@code published_at} 設回 NULL，
     * {@code status} 仍留在 {@code published}。於是後台只能靠「{@code publishedAt} 是不是
     * null」反推，歷史列表的 pill 照樣顯示 {@code published}——畫面說這篇是已發布，
     * 事實是讀者看不到。更嚴重的是沒有任何重新上架的路徑：{@code slug} 有 UNIQUE 約束、
     * 那一列還佔著它，所以同 slug 重發必定 400，只能改用新 slug 或手動
     * {@code UPDATE campaign}——而手動 UPDATE 正是下架端點宣稱要消滅的操作模式。</p>
     *
     * <p><b>為什麼不需要 migration</b>：{@code campaign.status} 在 V4 定義為純
     * {@code TEXT}，沒有列舉約束也沒有 CHECK（{@code mode} 同理，見
     * {@link #MODE_PUBLISH}）。新增一個值只是新增一個字串，資料庫結構不變。</p>
     *
     * <p><b>不是「刪除」也不是「草稿」</b>：這一列的 {@code article_access} 與
     * {@code credit_txn} 完整保留，重新上架後已解鎖者仍然有效（見
     * {@code CampaignService#unpublish} 與 {@code #republish}）。</p>
     */
    public static final String STATUS_UNPUBLISHED = "unpublished";

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

    /** 新版動態分眾條件快照；即使日後刪除保存分眾，歷史仍可追溯。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filter_json", columnDefinition = "jsonb")
    private AudienceSearchService.Filters filterJson;

    /** 使用的保存分眾 ID；刪除分眾時 DB 會設為 null。 */
    @Column(name = "saved_segment_id")
    private Long savedSegmentId;

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

    /** 內容分級：BASIC 或 PREMIUM */
    @Column(nullable = false)
    private String tier = TIER_BASIC;

    /** PREMIUM 解鎖所需點數；BASIC 為 0。資料庫層有 CHECK 約束禁止 PREMIUM 卻為 0 */
    @Column(name = "credit_cost", nullable = false)
    private int creditCost = 0;

    /** 網頁網址片段，供 /r/news/{slug} 使用；NULL 表示不在 archive 中露出 */
    private String slug;

    /** 發布時間；非 NULL 才會出現在 archive */
    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    /** VIP 是否在信件中直接收到全文（階段 D 才會使用；第一版一律折疊） */
    @Column(name = "vip_full_in_mail", nullable = false)
    private boolean vipFullInMail = false;

    /** 本次寄送的參與度級別，逗號分隔（階段 F 才會使用），供補寄重建相同對象 */
    @Column(name = "filter_levels", nullable = false)
    private String filterLevels = "active";

    /** 公開文章列表使用的封面 Emoji；空值時由畫面使用預設圖示。 */
    @Column(name = "cover_emoji")
    private String coverEmoji;

    /** MinIO 圖片封面 ID；空值時使用封面 Emoji。 */
    @Column(name = "cover_media_id")
    private Long coverMediaId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** 內容最後修改時間；僅記錄時間，不保存修改歷史（決策 D8） */
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /**
     * 後台列表回傳用的 hashtag 名稱；<b>不是資料庫欄位</b>（標籤存在 {@code campaign_tag} 關聯表）。
     *
     * <p><b>為什麼要掛在實體上</b>：後台「編輯已發布文章」進入編輯模式時必須回填既有標籤，
     * 而後台原本<b>沒有任何管道</b>讀得到某篇文章目前有哪些標籤——列表 API 不回傳、
     * 也沒有取單篇的端點。少了它，編輯畫面只能從「一個都沒勾」開始，
     * 存檔時就會把該文既有的 hashtag 全部刪光（{@code CampaignMetadataService.update()}
     * 是先刪再依送出清單重建，沒有「維持不變」語意）——改一個錯字就掉光標籤。</p>
     *
     * <p>由 {@code AdminCampaignController} 以一次批次查詢填入（不是 N+1），
     * 未填入時為 {@code null}，序列化後為 {@code "tags": null}，對舊呼叫端無害。</p>
     */
    @jakarta.persistence.Transient
    private java.util.List<String> tags;

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
    public AudienceSearchService.Filters getFilterJson() { return filterJson; }
    public Long getSavedSegmentId() { return savedSegmentId; }
    public String getMode() { return mode; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public int getRecipientCount() { return recipientCount; }
    public int getAcceptedCount() { return acceptedCount; }
    public int getFailedCount() { return failedCount; }
    public String getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public java.util.List<String> getTags() { return tags; }
    public void setTags(java.util.List<String> tags) { this.tags = tags; }

    /**
     * 回傳給 Admin 的排程操作權限；時間以後端時鐘為準，前端不可自行用狀態字串推測。
     */
    public boolean getCanModifySchedule() {
        return canModifyScheduleAt(OffsetDateTime.now(ZoneOffset.UTC));
    }

    /**
     * 判斷指定時間點是否仍可修改或取消排程，供 Service 與序列化結果共用同一條規則。
     */
    public boolean canModifyScheduleAt(OffsetDateTime now) {
        return STATUS_SCHEDULED.equals(status)
            && scheduledAt != null
            && scheduledAt.isAfter(now);
    }

    public void setAcceptedCount(int acceptedCount) { this.acceptedCount = acceptedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
    public void setStatus(String status) { this.status = status; }

    // 以下 setter 供「修改未寄出的排程」就地更新使用（reschedule）
    public void setSubject(String subject) { this.subject = subject; }
    public void setMarkdown(String markdown) { this.markdown = markdown; }
    public void setBodyHtml(String bodyHtml) { this.bodyHtml = bodyHtml; }
    public void setFilterRole(String filterRole) { this.filterRole = filterRole; }
    public void setFilterInterest(String filterInterest) { this.filterInterest = filterInterest; }
    public void setFilterJson(AudienceSearchService.Filters filterJson) { this.filterJson = filterJson; }
    public void setSavedSegmentId(Long savedSegmentId) { this.savedSegmentId = savedSegmentId; }
    public void setScheduledAt(OffsetDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public void setRecipientCount(int recipientCount) { this.recipientCount = recipientCount; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
    public int getCreditCost() { return creditCost; }
    public void setCreditCost(int creditCost) { this.creditCost = creditCost; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
    public boolean isVipFullInMail() { return vipFullInMail; }
    public void setVipFullInMail(boolean vipFullInMail) { this.vipFullInMail = vipFullInMail; }
    public String getFilterLevels() { return filterLevels; }
    public void setFilterLevels(String filterLevels) { this.filterLevels = filterLevels; }
    public String getCoverEmoji() { return coverEmoji; }
    public void setCoverEmoji(String coverEmoji) { this.coverEmoji = coverEmoji; }
    public Long getCoverMediaId() { return coverMediaId; }
    public void setCoverMediaId(Long coverMediaId) { this.coverMediaId = coverMediaId; }

    /** 是否為進階內容（需點數或 VIP） */
    public boolean isPremium() {
        return TIER_PREMIUM.equals(tier);
    }

    /** 是否已發布（未發布者不出現在 archive） */
    public boolean isPublished() {
        return publishedAt != null;
    }
}
