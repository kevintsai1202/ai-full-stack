package world.springai.survey.reader;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** 一位被邀者從填表到確認、審核與發獎的完整歸因紀錄。 */
@Entity
@Table(name = "referral_conversion")
public class ReferralConversion {

    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "invitee_email_normalized", nullable = false, unique = true)
    private String inviteeEmailNormalized;
    @Column(name = "referrer_id", nullable = false)
    private Long referrerId;
    @Column(name = "referral_code", nullable = false)
    private String referralCode;
    @Column(name = "source_slug")
    private String sourceSlug;
    @Column(nullable = false)
    private String status = STATUS_SUBMITTED;
    @Column(name = "risk_score", nullable = false)
    private int riskScore;
    @Column(name = "risk_reasons")
    private String riskReasons;
    @Column(name = "base_reward", nullable = false)
    private int baseReward;
    @Column(nullable = false)
    private int multiplier = 1;
    @Column(name = "referrer_reward", nullable = false)
    private int referrerReward;
    @Column(name = "invitee_reward", nullable = false)
    private int inviteeReward;
    @Column(name = "invitee_reward_granted", nullable = false)
    private boolean inviteeRewardGranted;
    @Column(name = "submitted_at", insertable = false, updatable = false)
    private OffsetDateTime submittedAt;
    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;
    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;
    @Column(name = "reviewed_by")
    private String reviewedBy;
    @Column(name = "review_note")
    private String reviewNote;

    /** JPA 建構子。 */
    protected ReferralConversion() {}

    /** 建立一筆有效推薦碼的填表歸因。 */
    public ReferralConversion(String inviteeEmailNormalized, Long referrerId,
                              String referralCode, String sourceSlug) {
        this.inviteeEmailNormalized = inviteeEmailNormalized;
        this.referrerId = referrerId;
        this.referralCode = referralCode;
        this.sourceSlug = sourceSlug;
    }

    public Long getId() { return id; }
    public String getInviteeEmailNormalized() { return inviteeEmailNormalized; }
    public Long getReferrerId() { return referrerId; }
    public String getReferralCode() { return referralCode; }
    public String getSourceSlug() { return sourceSlug; }
    public String getStatus() { return status; }
    public int getRiskScore() { return riskScore; }
    public String getRiskReasons() { return riskReasons; }
    public int getBaseReward() { return baseReward; }
    public int getMultiplier() { return multiplier; }
    public int getReferrerReward() { return referrerReward; }
    public int getInviteeReward() { return inviteeReward; }
    public boolean isInviteeRewardGranted() { return inviteeRewardGranted; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public String getReviewedBy() { return reviewedBy; }
    public String getReviewNote() { return reviewNote; }

    /** 記錄確認當下的風險與預計獎勵。 */
    public void confirm(String status, int riskScore, String riskReasons,
                        int baseReward, int multiplier, int referrerReward,
                        int inviteeReward, OffsetDateTime now) {
        this.status = status;
        this.riskScore = riskScore;
        this.riskReasons = riskReasons;
        this.baseReward = baseReward;
        this.multiplier = multiplier;
        this.referrerReward = referrerReward;
        this.inviteeReward = inviteeReward;
        this.confirmedAt = now;
    }

    /** 記錄人工審核結果。 */
    public void review(String status, String reviewer, String note, OffsetDateTime now) {
        this.status = status;
        this.reviewedBy = reviewer;
        this.reviewNote = note;
        this.reviewedAt = now;
    }

    /** 標記被邀者加碼已實際進入點數帳本。 */
    public void markInviteeRewardGranted() {
        this.inviteeRewardGranted = true;
    }
}

