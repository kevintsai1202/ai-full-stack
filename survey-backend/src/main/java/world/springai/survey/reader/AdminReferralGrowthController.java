package world.springai.survey.reader;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.AdminKeyGuard;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 病毒成長漏斗、人工審核與限時活動的管理 API。 */
@RestController
public class AdminReferralGrowthController {

    private final AdminKeyGuard guard;
    private final JdbcTemplate jdbc;
    private final ReferralConversionRepository conversions;
    private final ReferralCampaignRepository campaigns;
    private final ReferralGrowthService growth;

    /** 注入金鑰守衛、彙總查詢與成長服務。 */
    public AdminReferralGrowthController(AdminKeyGuard guard, JdbcTemplate jdbc,
                                         ReferralConversionRepository conversions,
                                         ReferralCampaignRepository campaigns,
                                         ReferralGrowthService growth) {
        this.guard = guard;
        this.jdbc = jdbc;
        this.conversions = conversions;
        this.campaigns = campaigns;
        this.growth = growth;
    }

    /** 漏斗 KPI、熱門文章、待審核與活動一次回傳，減少後台往返。 */
    @GetMapping("/api/admin/referrals/dashboard")
    public Map<String, Object> dashboard(
            @RequestHeader(value = "X-Admin-Key", required = false) String key) {
        guard.verify(key);
        long clicks = count("select count(*) from referral_click");
        long submitted = count("""
            select count(*)
              from survey_response sr
              join reader r on r.referral_code = (sr.answers ->> '_ref')
             where sr.answers ? '_ref'
            """);
        long confirmed = count("select count(*) from referral_conversion where confirmed_at is not null");
        long approved = count("select count(*) from referral_conversion where status = 'APPROVED'");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("funnel", Map.of(
            "clicks", clicks,
            "submitted", submitted,
            "confirmed", confirmed,
            "approved", approved,
            "clickToSubmitRate", rate(submitted, clicks),
            "submitToConfirmRate", rate(confirmed, submitted)));
        long articleViews = readerEventCount("ARTICLE_VIEW");
        long subscriptionHomeViews = readerEventCount("SUBSCRIPTION_HOME_VIEW");
        long subscribeAttempts = readerEventCount("SUBSCRIBE_ATTEMPT");
        long subscribeSuccess = readerEventCount("SUBSCRIBE_SUCCESS");
        long unlockClicks = readerEventCount("UNLOCK_CLICK");
        long unlockSuccess = readerEventCount("UNLOCK_SUCCESS");
        result.put("readerFunnel", Map.of(
            "articleViews", articleViews,
            "subscriptionHomeViews", subscriptionHomeViews,
            "subscribeAttempts", subscribeAttempts,
            "subscribeSuccess", subscribeSuccess,
            "unlockClicks", unlockClicks,
            "unlockSuccess", unlockSuccess,
            "homeToSubscribeRate", rate(subscribeSuccess, subscriptionHomeViews),
            "unlockSuccessRate", rate(unlockSuccess, unlockClicks)));
        result.put("readerTopArticles", jdbc.queryForList("""
            select article_slug,
                   count(distinct visitor_key) filter (where event_name = 'ARTICLE_VIEW') views,
                   count(distinct visitor_key) filter (where event_name = 'UNLOCK_CLICK') unlock_clicks,
                   count(distinct visitor_key) filter (where event_name = 'UNLOCK_SUCCESS') unlock_success
              from reader_funnel_event
             where article_slug is not null
             group by article_slug
             order by views desc, unlock_clicks desc
             limit 20
            """));
        result.put("topArticles", jdbc.queryForList("""
            with click_stats as (
              select coalesce(source_slug, '(一般邀請連結)') source, count(*) clicks
                from referral_click group by source_slug
            ), submission_stats as (
              select coalesce(sr.answers ->> '_share_article', '(一般邀請連結)') source,
                     count(*) submissions
                from survey_response sr
                join reader r on r.referral_code = (sr.answers ->> '_ref')
               where sr.answers ? '_ref'
               group by sr.answers ->> '_share_article'
            ), confirmation_stats as (
              select coalesce(source_slug, '(一般邀請連結)') source,
                     count(*) filter (where confirmed_at is not null) confirmations
                from referral_conversion group by source_slug
            ), sources as (
              select source from click_stats union
              select source from submission_stats union
              select source from confirmation_stats
            )
            select s.source,
                   coalesce(c.clicks, 0) clicks,
                   coalesce(f.submissions, 0) submissions,
                   coalesce(v.confirmations, 0) confirmations
              from sources s
              left join click_stats c on c.source = s.source
              left join submission_stats f on f.source = s.source
              left join confirmation_stats v on v.source = s.source
             order by coalesce(v.confirmations, 0) desc, coalesce(c.clicks, 0) desc limit 20
            """));
        result.put("reviews", conversions.findTop100ByStatusOrderByConfirmedAtAsc(
            ReferralConversion.STATUS_PENDING_REVIEW).stream().map(this::reviewView).toList());
        result.put("campaigns", campaigns.findAllByOrderByStartsAtDesc());
        return result;
    }

    /** 核准或拒絕可疑邀請。 */
    @PostMapping("/api/admin/referrals/reviews/{id}")
    public Map<String, Object> review(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @PathVariable Long id, @RequestBody ReviewRequest request) {
        guard.verify(key);
        if (request == null || !List.of("approve", "reject").contains(request.action())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "action 必須是 approve 或 reject");
        }
        ReferralGrowthService.Outcome outcome = "approve".equals(request.action())
            ? growth.approve(id, "admin", request.note())
            : growth.reject(id, "admin", request.note());
        return Map.of("outcome", outcome);
    }

    /** 建立限時活動；倍率、期間與範圍皆由後端再驗證。 */
    @PostMapping("/api/admin/referrals/campaigns")
    public ReferralCampaign createCampaign(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestBody CampaignRequest request) {
        guard.verify(key);
        if (request == null || !StringUtils.hasText(request.name())
                || request.multiplier() < 2 || request.multiplier() > 3
                || request.startsAt() == null || request.endsAt() == null
                || !request.endsAt().isAfter(request.startsAt())
                || (!StringUtils.hasText(request.articleSlug()) && !StringUtils.hasText(request.tagSlug()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "請提供名稱、2 至 3 倍、有效期間，以及文章或 hashtag 範圍");
        }
        return campaigns.save(new ReferralCampaign(request.name().trim(),
            cleanScope(request.articleSlug()), cleanScope(request.tagSlug()),
            request.multiplier(), request.startsAt(), request.endsAt()));
    }

    /** 停用活動；歷史轉換仍保留當時套用的倍率。 */
    @PostMapping("/api/admin/referrals/campaigns/{id}/deactivate")
    public ReferralCampaign deactivate(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @PathVariable Long id) {
        guard.verify(key);
        ReferralCampaign campaign = campaigns.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "查無活動"));
        campaign.deactivate();
        return campaigns.save(campaign);
    }

    /** 安全取得單一 count。 */
    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    /** 依事件名稱計算全期間匿名訪客數，供 reader 漏斗使用。 */
    private long readerEventCount(String eventName) {
        Long value = jdbc.queryForObject(
            "select count(distinct visitor_key) from reader_funnel_event where event_name = ?",
            Long.class, eventName);
        return value == null ? 0 : value;
    }

    /** 百分比保留一位小數，分母為零時回 0。 */
    private double rate(long numerator, long denominator) {
        return denominator == 0 ? 0 : Math.round(numerator * 1000.0 / denominator) / 10.0;
    }

    /** 待審核回應遮罩 email，不把個資暴露在儀表板。 */
    private Map<String, Object> reviewView(ReferralConversion conversion) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", conversion.getId());
        row.put("invitee", ReferralGrowthService.maskEmail(conversion.getInviteeEmailNormalized()));
        row.put("sourceSlug", conversion.getSourceSlug());
        row.put("riskScore", conversion.getRiskScore());
        row.put("riskReasons", conversion.getRiskReasons());
        row.put("referrerReward", conversion.getReferrerReward());
        row.put("inviteeReward", conversion.getInviteeReward());
        row.put("confirmedAt", conversion.getConfirmedAt());
        return row;
    }

    /** 清理活動範圍；空值轉 null，最大 100 字。 */
    private String cleanScope(String value) {
        if (!StringUtils.hasText(value)) return null;
        String clean = value.trim();
        if (clean.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "活動範圍過長");
        }
        return clean;
    }

    public record ReviewRequest(String action, String note) {}
    public record CampaignRequest(String name, String articleSlug, String tagSlug, int multiplier,
                                  OffsetDateTime startsAt, OffsetDateTime endsAt) {}
}
