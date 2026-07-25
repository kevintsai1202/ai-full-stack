package world.springai.survey.reader;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 已解鎖文章實體，對應資料表 article_access。
 *
 * <p>資料表上的 UNIQUE(reader_id, campaign_id) 同時扮演兩個角色：
 * 「同一篇不重複扣點」的保證，以及並發解鎖的防線（同時兩個請求只有一個
 * 能插入成功，另一個轉為「已解鎖」路徑）。</p>
 */
@Entity
@Table(name = "article_access")
public class ArticleAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 解鎖者 */
    @Column(name = "reader_id", nullable = false)
    private Long readerId;

    /** 被解鎖的文章 */
    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    /** 當時實扣點數；0 表 VIP 或 BASIC 免費通行 */
    @Column(nullable = false)
    private int cost;

    /** 解鎖時間，由資料庫維護 */
    @Column(name = "unlocked_at", insertable = false, updatable = false)
    private OffsetDateTime unlockedAt;

    /** JPA 需要的無參數建構子 */
    protected ArticleAccess() {
    }

    /** 建立一筆解鎖紀錄 */
    public ArticleAccess(Long readerId, Long campaignId, int cost) {
        this.readerId = readerId;
        this.campaignId = campaignId;
        this.cost = cost;
    }

    public Long getId() { return id; }
    public Long getReaderId() { return readerId; }
    public Long getCampaignId() { return campaignId; }
    public int getCost() { return cost; }
    public OffsetDateTime getUnlockedAt() { return unlockedAt; }
}
