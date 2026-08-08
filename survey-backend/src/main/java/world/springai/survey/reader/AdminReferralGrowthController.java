package world.springai.survey.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
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

    private static final Logger log = LoggerFactory.getLogger(AdminReferralGrowthController.class);

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
        // D7：語意正確的漏斗結構——頂端為平行入口加總，其下分訂閱／解鎖兩條路徑
        result.put("readerFunnelStructured", ReaderFunnelView.from(
            articleViews, subscriptionHomeViews, subscribeAttempts, subscribeSuccess, unlockClicks, unlockSuccess));
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

    /** 補發掃描：帶推薦碼、已同意且未退訂者，取最早一筆問卷時間作為轉換時點。 */
    private static final String BACKFILL_SCAN_SQL = """
        select lower(sr.email) as email, min(sr.created_at) as occurred_at
          from survey_response sr
          join reader r on r.referral_code = (sr.answers ->> '_ref')
         where sr.answers ? '_ref'
           and sr.consent = true
           and sr.unsubscribed = false
         group by lower(sr.email)
         order by min(sr.created_at)
        """;

    /**
     * 補發歷史推薦獎勵：對「帶推薦碼且訂閱已成立」者建立轉換並直接核准發點。
     *
     * <p>為什麼需要這支端點：{@code confirmAndReward} 是唯一發獎入口，而它只由
     * 讀者點確認信觸發。在歡迎信加上確認 CTA 之前，沒有任何人收到過含確認連結的信，
     * 因此所有歷史推薦的獎勵都沒發出去（spec §1.3）。
     *
     * <p><b>逐筆容錯不中斷整批</b>：一位推薦人的資料異常不該讓其餘十幾筆都補不到。
     * 失敗計入 {@code failed} 並記 ERROR 供人工處理。
     *
     * <p><b>冪等</b>：完全依賴 {@code referral_conversion} 的 invitee 唯一鍵與
     * {@code uq_credit_txn_referral_note}。重跑會全部回 ALREADY_PROCESSED。
     *
     * @param dryRun true 時只回傳掃描名單（email 遮罩）與筆數，不發放任何點數
     */
    @PostMapping("/api/admin/referrals/backfill")
    public Map<String, Object> backfill(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
        guard.verify(key);
        List<Map<String, Object>> candidates = jdbc.queryForList(BACKFILL_SCAN_SQL);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dryRun", dryRun);
        result.put("scanned", candidates.size());

        if (dryRun) {
            result.put("candidates", candidates.stream().map(row -> {
                Map<String, Object> view = new LinkedHashMap<>();
                view.put("email", ReferralGrowthService.maskEmail((String) row.get("email")));
                view.put("occurredAt", row.get("occurred_at"));
                return view;
            }).toList());
            return result;
        }

        int rewarded = 0, alreadyProcessed = 0, selfInvite = 0, noReferrer = 0, failed = 0;
        for (Map<String, Object> row : candidates) {
            String email = (String) row.get("email");
            try {
                OffsetDateTime occurredAt = toOffsetDateTime(row.get("occurred_at"));
                ReferralGrowthService.Outcome outcome = growth.backfillAndApprove(email, occurredAt);
                switch (outcome) {
                    case REWARDED -> rewarded++;
                    case SELF_INVITE -> selfInvite++;
                    case NO_REFERRER -> noReferrer++;
                    // 補發不跑風控，PENDING_REVIEW 只可能來自先前已存在的待審轉換
                    case ALREADY_PROCESSED, PENDING_REVIEW -> alreadyProcessed++;
                }
            } catch (Exception e) {
                failed++;
                log.error("推薦獎勵補發失敗（其餘筆數繼續）：{}",
                    ReferralGrowthService.maskEmail(email), e);
            }
        }
        result.put("rewarded", rewarded);
        result.put("alreadyProcessed", alreadyProcessed);
        result.put("selfInvite", selfInvite);
        result.put("noReferrer", noReferrer);
        result.put("failed", failed);
        return result;
    }

    /**
     * 把 JDBC 回傳的時間值轉為 OffsetDateTime。
     *
     * <p>PostgreSQL 的 timestamptz 經 JdbcTemplate 可能回 OffsetDateTime 或
     * java.sql.Timestamp（依驅動版本與欄位推導而異），兩者都要能吃。</p>
     */
    private static OffsetDateTime toOffsetDateTime(Object value) {
        if (value instanceof OffsetDateTime odt) return odt;
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
        }
        throw new IllegalStateException("無法解析的時間型別：" + value);
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
