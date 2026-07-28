package world.springai.survey.reader;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** 限時邀請加碼活動。 */
@Entity
@Table(name = "referral_campaign")
public class ReferralCampaign {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(name = "article_slug")
    private String articleSlug;
    @Column(name = "tag_slug")
    private String tagSlug;
    @Column(nullable = false)
    private int multiplier;
    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;
    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** JPA 建構子。 */
    protected ReferralCampaign() {}

    /** 建立一個限定文章或 hashtag 的限時活動。 */
    public ReferralCampaign(String name, String articleSlug, String tagSlug, int multiplier,
                            OffsetDateTime startsAt, OffsetDateTime endsAt) {
        this.name = name;
        this.articleSlug = articleSlug;
        this.tagSlug = tagSlug;
        this.multiplier = multiplier;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    /** 停用活動；歷史資料保留。 */
    public void deactivate() { this.active = false; }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getArticleSlug() { return articleSlug; }
    public String getTagSlug() { return tagSlug; }
    public int getMultiplier() { return multiplier; }
    public OffsetDateTime getStartsAt() { return startsAt; }
    public OffsetDateTime getEndsAt() { return endsAt; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}

