package world.springai.survey.reader;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 去識別化的每日唯一分享點擊。 */
@Entity
@Table(name = "referral_click")
public class ReferralClick {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "referrer_id", nullable = false)
    private Long referrerId;
    @Column(name = "referral_code", nullable = false)
    private String referralCode;
    @Column(name = "source_slug")
    private String sourceSlug;
    @Column(name = "visitor_key", nullable = false)
    private String visitorKey;
    @Column(name = "click_day", nullable = false)
    private LocalDate clickDay;
    @Column(name = "clicked_at", insertable = false, updatable = false)
    private OffsetDateTime clickedAt;

    /** JPA 建構子。 */
    protected ReferralClick() {}

    /** 建立一筆每日唯一點擊；訪客代碼由瀏覽器隨機產生，不含個資。 */
    public ReferralClick(Long referrerId, String referralCode, String sourceSlug,
                         String visitorKey, LocalDate clickDay) {
        this.referrerId = referrerId;
        this.referralCode = referralCode;
        this.sourceSlug = sourceSlug;
        this.visitorKey = visitorKey;
        this.clickDay = clickDay;
    }

    public Long getId() { return id; }
}

