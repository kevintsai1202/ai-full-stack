package world.springai.survey.promo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 點擊紀錄實體，對應資料表 promo_click。
 *
 * <p>append-only：每次點擊寫入一筆，不做彙總更新，統計一律於查詢時計算
 * （見 {@link PromoClickRepository#statsForPlacements}）。</p>
 */
@Entity
@Table(name = "promo_click")
public class PromoClick {

    /** 來源通道：電子報信件 */
    public static final String CHANNEL_EMAIL = "EMAIL";
    /** 來源通道：網頁 */
    public static final String CHANNEL_WEB = "WEB";

    /** 身分類型：電子報收件人（可去重） */
    public static final String IDENTITY_RECIPIENT = "RECIPIENT";
    /** 身分類型：已登入讀者（可去重） */
    public static final String IDENTITY_READER = "READER";
    /** 身分類型：匿名（不進唯一點擊計數） */
    public static final String IDENTITY_ANON = "ANON";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所屬版位 */
    @Column(name = "placement_id", nullable = false)
    private Long placementId;

    /** 來源通道，取本類的 CHANNEL_* 常數 */
    @Column(nullable = false)
    private String channel;

    /** 身分類型，取本類的 IDENTITY_* 常數 */
    @Column(name = "identity_type", nullable = false)
    private String identityType;

    /** 身分鍵值（收件人 email 雜湊或讀者 id 等），ANON 時可為 null */
    @Column(name = "identity_key")
    private String identityKey;

    /** 點擊時間，由資料庫維護 */
    @Column(name = "clicked_at", insertable = false, updatable = false)
    private OffsetDateTime clickedAt;

    /** JPA 需要的無參數建構子 */
    protected PromoClick() {
    }

    /** 建立一筆點擊紀錄 */
    public PromoClick(Long placementId, String channel, String identityType, String identityKey) {
        this.placementId = placementId;
        this.channel = channel;
        this.identityType = identityType;
        this.identityKey = identityKey;
    }

    public Long getId() { return id; }

    public Long getPlacementId() { return placementId; }

    public String getChannel() { return channel; }

    public String getIdentityType() { return identityType; }

    public String getIdentityKey() { return identityKey; }

    public OffsetDateTime getClickedAt() { return clickedAt; }
}
