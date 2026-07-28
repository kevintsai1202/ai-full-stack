package world.springai.survey.reader;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** 讀者已取得的邀請里程碑徽章。 */
@Entity
@Table(name = "referral_badge")
public class ReferralBadge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "reader_id", nullable = false)
    private Long readerId;
    @Column(nullable = false)
    private int milestone;
    @Column(name = "badge_code", nullable = false)
    private String badgeCode;
    @Column(name = "badge_name", nullable = false)
    private String badgeName;
    @Column(name = "bonus_credits", nullable = false)
    private int bonusCredits;
    @Column(name = "awarded_at", insertable = false, updatable = false)
    private OffsetDateTime awardedAt;

    /** JPA 建構子。 */
    protected ReferralBadge() {}

    /** 建立一個只會頒發一次的里程碑。 */
    public ReferralBadge(Long readerId, int milestone, String badgeCode,
                         String badgeName, int bonusCredits) {
        this.readerId = readerId;
        this.milestone = milestone;
        this.badgeCode = badgeCode;
        this.badgeName = badgeName;
        this.bonusCredits = bonusCredits;
    }

    public Long getId() { return id; }
    public Long getReaderId() { return readerId; }
    public int getMilestone() { return milestone; }
    public String getBadgeCode() { return badgeCode; }
    public String getBadgeName() { return badgeName; }
    public int getBonusCredits() { return bonusCredits; }
    public OffsetDateTime getAwardedAt() { return awardedAt; }
}

