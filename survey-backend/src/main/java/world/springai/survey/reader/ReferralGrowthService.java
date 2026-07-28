package world.springai.survey.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.springai.survey.audience.SurveyResponse;
import world.springai.survey.audience.SurveyResponseRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** 歸因、風控、雙邊獎勵、活動倍率與里程碑的單一交易邊界。 */
@Service
public class ReferralGrowthService {

    private static final Logger log = LoggerFactory.getLogger(ReferralGrowthService.class);
    private static final int[] MILESTONES = {3, 5, 10};

    /** 確認處理結果。 */
    public enum Outcome {
        REWARDED, PENDING_REVIEW, ALREADY_PROCESSED, NO_REFERRER, SELF_INVITE
    }

    /** 里程碑顯示資料。 */
    public record MilestoneView(int milestone, String badgeCode, String badgeName,
                                int bonusCredits, boolean earned) {}

    private final SurveyResponseRepository surveyResponses;
    private final ReaderRepository readers;
    private final CreditTxnRepository credits;
    private final ReferralConversionRepository conversions;
    private final ReferralBadgeRepository badges;
    private final ReferralCampaignRepository campaigns;
    private final CreditPolicy policy;

    /** 注入成長系統所有資料來源。 */
    public ReferralGrowthService(SurveyResponseRepository surveyResponses,
                                 ReaderRepository readers,
                                 CreditTxnRepository credits,
                                 ReferralConversionRepository conversions,
                                 ReferralBadgeRepository badges,
                                 ReferralCampaignRepository campaigns,
                                 CreditPolicy policy) {
        this.surveyResponses = surveyResponses;
        this.readers = readers;
        this.credits = credits;
        this.conversions = conversions;
        this.badges = badges;
        this.campaigns = campaigns;
        this.policy = policy;
    }

    /** 確認訂閱後計算風險並發獎；同推薦人會鎖列，避免上限與里程碑競態。 */
    @Transactional
    public Outcome confirmAndReward(String inviteeEmail) {
        String invitee = normalize(inviteeEmail);
        Optional<SurveyResponse> response = surveyResponses
            .findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(invitee);
        Optional<String> code = response.flatMap(ReferralService::referralCodeOf);
        if (code.isEmpty()) return Outcome.NO_REFERRER;
        Optional<Reader> found = readers.findByReferralCode(code.get());
        if (found.isEmpty()) return Outcome.NO_REFERRER;
        Reader referrer = readers.findByIdForUpdate(found.get().getId()).orElseThrow();
        if (normalize(referrer.getEmail()).equals(invitee)) return Outcome.SELF_INVITE;

        ReferralConversion conversion = conversions.findForUpdate(invitee)
            .orElseGet(() -> conversions.saveAndFlush(new ReferralConversion(
                invitee, referrer.getId(), code.get(), sourceSlugOf(response.orElse(null)))));
        if (conversion.getConfirmedAt() != null) {
            return ReferralConversion.STATUS_PENDING_REVIEW.equals(conversion.getStatus())
                ? Outcome.PENDING_REVIEW : Outcome.ALREADY_PROCESSED;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Risk risk = assessRisk(referrer.getId(), now);
        int multiplier = campaignMultiplier(conversion.getSourceSlug(), now);
        int baseReward = policy.referralReward();
        int inviteeReward = policy.referralInviteeReward();
        int totalReward = Math.multiplyExact(baseReward, multiplier);
        String status = risk.reviewRequired()
            ? ReferralConversion.STATUS_PENDING_REVIEW : ReferralConversion.STATUS_APPROVED;
        conversion.confirm(status, risk.score(), risk.reasons(), baseReward,
            multiplier, totalReward, inviteeReward, now);
        conversions.saveAndFlush(conversion);

        if (risk.reviewRequired()) {
            log.warn("邀請轉人工審核：referrerId={} invitee={} reasons={}",
                referrer.getId(), maskEmail(invitee), risk.reasons());
            return Outcome.PENDING_REVIEW;
        }
        grantAll(conversion, referrer);
        return Outcome.REWARDED;
    }

    /** 人工核准待審項目；核准與所有獎勵在同一交易提交。 */
    @Transactional
    public Outcome approve(Long conversionId, String reviewer, String note) {
        ReferralConversion conversion = conversions.findByIdForUpdate(conversionId)
            .orElseThrow(() -> new IllegalArgumentException("查無邀請轉換"));
        if (!ReferralConversion.STATUS_PENDING_REVIEW.equals(conversion.getStatus())) {
            return Outcome.ALREADY_PROCESSED;
        }
        Reader referrer = readers.findByIdForUpdate(conversion.getReferrerId()).orElseThrow();
        conversion.review(ReferralConversion.STATUS_APPROVED, reviewer, cleanNote(note),
            OffsetDateTime.now(ZoneOffset.UTC));
        conversions.saveAndFlush(conversion);
        grantAll(conversion, referrer);
        return Outcome.REWARDED;
    }

    /** 人工拒絕待審項目；保留原因與歷史，不刪資料。 */
    @Transactional
    public Outcome reject(Long conversionId, String reviewer, String note) {
        ReferralConversion conversion = conversions.findByIdForUpdate(conversionId)
            .orElseThrow(() -> new IllegalArgumentException("查無邀請轉換"));
        if (!ReferralConversion.STATUS_PENDING_REVIEW.equals(conversion.getStatus())) {
            return Outcome.ALREADY_PROCESSED;
        }
        conversion.review(ReferralConversion.STATUS_REJECTED, reviewer, cleanNote(note),
            OffsetDateTime.now(ZoneOffset.UTC));
        conversions.save(conversion);
        return Outcome.ALREADY_PROCESSED;
    }

    /** 被邀者較晚首次登入時補發確認加碼。 */
    @Transactional
    public int grantPendingInviteeReward(Reader invitee) {
        ReferralConversion conversion = conversions.findForUpdate(normalize(invitee.getEmail()))
            .orElse(null);
        if (conversion == null
                || !ReferralConversion.STATUS_APPROVED.equals(conversion.getStatus())
                || conversion.isInviteeRewardGranted()
                || conversion.getInviteeReward() <= 0) return 0;
        addCredit(invitee.getId(), conversion.getInviteeReward(),
            CreditTxn.REASON_REFERRAL_INVITEE, "邀請確認加碼");
        conversion.markInviteeRewardGranted();
        conversions.save(conversion);
        return conversion.getInviteeReward();
    }

    /** 讀者邀請頁所需的里程碑狀態。 */
    @Transactional(readOnly = true)
    public List<MilestoneView> milestones(Long readerId) {
        var earned = badges.findByReaderIdOrderByMilestoneAsc(readerId).stream()
            .map(ReferralBadge::getMilestone).collect(java.util.stream.Collectors.toSet());
        return java.util.Arrays.stream(MILESTONES)
            .mapToObj(m -> milestoneView(m, earned.contains(m))).toList();
    }

    /** 目前進行中的活動。 */
    @Transactional(readOnly = true)
    public List<ReferralCampaign> activeCampaigns() {
        return campaigns.findActiveAt(OffsetDateTime.now(ZoneOffset.UTC));
    }

    /** 發放推薦人、被邀者與剛達成的里程碑獎勵。 */
    private void grantAll(ReferralConversion conversion, Reader referrer) {
        if (conversion.getReferrerReward() > 0) {
            addCredit(referrer.getId(), conversion.getReferrerReward(),
                CreditTxn.REASON_REFERRAL, conversion.getInviteeEmailNormalized());
        }
        readers.findByEmailIgnoreCase(conversion.getInviteeEmailNormalized()).ifPresent(invitee -> {
            if (conversion.getInviteeReward() > 0 && !conversion.isInviteeRewardGranted()) {
                addCredit(invitee.getId(), conversion.getInviteeReward(),
                    CreditTxn.REASON_REFERRAL_INVITEE, "邀請確認加碼");
                conversion.markInviteeRewardGranted();
                conversions.save(conversion);
            }
        });

        long confirmed = conversions.countByReferrerIdAndStatus(
            referrer.getId(), ReferralConversion.STATUS_APPROVED);
        for (int milestone : MILESTONES) {
            if (confirmed >= milestone && !badges.existsByReaderIdAndMilestone(referrer.getId(), milestone)) {
                MilestoneView view = milestoneView(milestone, true);
                badges.saveAndFlush(new ReferralBadge(referrer.getId(), milestone,
                    view.badgeCode(), view.badgeName(), view.bonusCredits()));
                if (view.bonusCredits() > 0) {
                    addCredit(referrer.getId(), view.bonusCredits(),
                        CreditTxn.REASON_REFERRAL_MILESTONE, "milestone:" + milestone);
                }
            }
        }
    }

    /** 帳本與物化餘額同進同出。 */
    private void addCredit(Long readerId, int amount, String reason, String note) {
        credits.saveAndFlush(new CreditTxn(readerId, amount, reason, null, note));
        if (readers.addCredits(readerId, amount) == 0) {
            throw new IllegalStateException("加點失敗：readerId=" + readerId);
        }
    }

    /** 每日上限與十分鐘速度規則。 */
    private Risk assessRisk(Long referrerId, OffsetDateTime now) {
        OffsetDateTime dayStart = now.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        long today = conversions.countByReferrerIdAndStatusAndConfirmedAtBetween(
            referrerId, ReferralConversion.STATUS_APPROVED, dayStart, dayStart.plusDays(1));
        long recent = conversions.countByReferrerIdAndConfirmedAtAfter(referrerId, now.minusMinutes(10));
        java.util.ArrayList<String> reasons = new java.util.ArrayList<>();
        int score = 0;
        if (today >= policy.referralDailyLimit()) {
            score += 100;
            reasons.add("超過每日自動核准上限 " + policy.referralDailyLimit());
        }
        if (recent >= policy.referralVelocityThreshold()) {
            score += 70;
            reasons.add("10 分鐘內確認速度異常");
        }
        return new Risk(score, String.join("；", reasons), score >= 70);
    }

    /** 找出確認時間點命中的最高活動倍率。 */
    private int campaignMultiplier(String sourceSlug, OffsetDateTime now) {
        if (sourceSlug == null || sourceSlug.isBlank()) return 1;
        return campaigns.findActiveAt(now).stream()
            .filter(c -> sourceSlug.equals(c.getArticleSlug())
                || (c.getTagSlug() != null && campaigns.articleHasTag(sourceSlug, c.getTagSlug())))
            .mapToInt(ReferralCampaign::getMultiplier).max().orElse(1);
    }

    /** 從系統答案取文章來源。 */
    private static String sourceSlugOf(SurveyResponse response) {
        if (response == null || response.getAnswers() == null) return null;
        Object raw = response.getAnswers().get("_share_article");
        return raw == null ? null : String.valueOf(raw).trim();
    }

    /** 里程碑名稱與可調點數的唯一映射。 */
    private MilestoneView milestoneView(int milestone, boolean earned) {
        return switch (milestone) {
            case 3 -> new MilestoneView(3, "TRAILBLAZER", "開路者",
                policy.referralMilestoneReward(3), earned);
            case 5 -> new MilestoneView(5, "GROWTH_CHAMPION", "成長推手",
                policy.referralMilestoneReward(5), earned);
            case 10 -> new MilestoneView(10, "COMMUNITY_LEADER", "社群領航員",
                policy.referralMilestoneReward(10), earned);
            default -> throw new IllegalArgumentException("未知里程碑");
        };
    }

    /** 審核備註限制長度。 */
    private static String cleanNote(String note) {
        if (note == null) return "";
        String clean = note.trim();
        return clean.length() <= 500 ? clean : clean.substring(0, 500);
    }

    /** 日誌與後台只顯示遮罩 email。 */
    public static String maskEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }

    /** email 正規化。 */
    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** 可解釋風險結果。 */
    private record Risk(int score, String reasons, boolean reviewRequired) {}
}
