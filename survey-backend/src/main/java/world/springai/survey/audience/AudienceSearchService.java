package world.springai.survey.audience;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Audience 伺服器端搜尋：基本資料、同意、Reader、VIP、點數、Survey、Exam 與活動條件。
 */
@Service
public class AudienceSearchService {

    /** 一般列表單頁上限。 */
    static final int MAX_PAGE_SIZE = 200;
    /** 同步批次操作上限多取一筆，用來辨識是否超過 500。 */
    static final int BULK_LIMIT_PLUS_ONE = 501;

    private final JdbcTemplate jdbc;

    /** 注入 PostgreSQL 查詢工具。 */
    public AudienceSearchService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Survey 條件；answers 的 key 會對應 form field key。 */
    public record SurveyFilter(String formKey, Integer version, Map<String, Object> answers) {}

    /** Exam 條件。 */
    public record ExamFilter(
            String examId,
            BigDecimalRange score,
            BigDecimalRange scoreRate,
            OffsetDateTime attemptedFrom,
            OffsetDateTime attemptedTo) {}

    /** 可重用數值區間。 */
    public record BigDecimalRange(java.math.BigDecimal min, java.math.BigDecimal max) {}

    /** 活動時間與類型條件。 */
    public record ActivityFilter(
            List<String> recordTypes,
            OffsetDateTime from,
            OffsetDateTime to) {}

    /** 電子報寄送軌跡條件；status 使用 campaign_recipient 的大寫狀態。 */
    public record DeliveryFilter(
            Long campaignId,
            List<String> statuses,
            OffsetDateTime from,
            OffsetDateTime to,
            Integer countMin,
            Integer countMax,
            Boolean neverReceived) {}

    /** 文章解鎖條件；可依文章或 hashtag 限制。 */
    public record UnlockFilter(
            Long campaignId,
            String tagSlug,
            OffsetDateTime from,
            OffsetDateTime to,
            Integer countMin,
            Integer countMax,
            Boolean neverUnlocked) {}

    /** 搜尋條件；空值代表不限。 */
    public record Filters(
            String query,
            List<String> sourceKeys,
            List<String> consentStatus,
            List<String> accountStatus,
            List<String> vipStatus,
            Integer creditsMin,
            Integer creditsMax,
            SurveyFilter survey,
            ExamFilter exam,
            ActivityFilter activity,
            DeliveryFilter delivery,
            UnlockFilter unlock) {

        /** 舊 API 與既有 Java 測試相容建構式。 */
        public Filters(
                String query,
                List<String> sourceKeys,
                List<String> consentStatus,
                List<String> accountStatus,
                List<String> vipStatus,
                Integer creditsMin,
                Integer creditsMax,
                SurveyFilter survey,
                ExamFilter exam,
                ActivityFilter activity) {
            this(query, sourceKeys, consentStatus, accountStatus, vipStatus,
                creditsMin, creditsMax, survey, exam, activity, null, null);
        }
    }

    /** 排序白名單；未知欄位會回 400，不直接串進 SQL。 */
    public record Sort(String field, String direction) {}

    /** 搜尋請求。 */
    public record SearchRequest(Filters filters, Sort sort, Integer page, Integer size) {}

    /** 回傳分頁、動態欄位與常用 facet。 */
    public record SearchResult(
            List<Map<String, Object>> items,
            long total,
            int page,
            int size,
            List<String> dynamicFields,
            Map<String, Object> facets) {}

    /** 執行分頁搜尋；不得把全表傳到前端再篩選。 */
    public SearchResult search(SearchRequest request) {
        SearchRequest safeRequest = request == null
            ? new SearchRequest(null, null, 0, 50)
            : request;
        int page = safeRequest.page() == null ? 0 : safeRequest.page();
        int size = safeRequest.size() == null ? 50 : safeRequest.size();
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "page 不得小於 0，size 必須介於 1 到 200");
        }
        SqlFilter filter = buildFilter(safeRequest.filters());
        String orderBy = orderBy(safeRequest.sort());
        String base = baseSql() + filter.where();
        List<Object> dataParams = new ArrayList<>(filter.params());
        dataParams.add(size);
        dataParams.add(page * size);
        List<Map<String, Object>> items = jdbc.query(
            base + " " + orderBy + " LIMIT ? OFFSET ?",
            (rs, rowNum) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("personId", rs.getLong("person_id"));
                item.put("email", rs.getString("email"));
                item.put("name", rs.getString("display_name"));
                item.put("consentStatus", rs.getString("consent_status"));
                item.put("sources", rs.getArray("sources") == null
                    ? List.of()
                    : List.of((Object[]) rs.getArray("sources").getArray()));
                item.put("lastActivityAt", rs.getObject("last_activity_at", OffsetDateTime.class));
                item.put("surveyCount", rs.getInt("survey_count"));
                item.put("examCount", rs.getInt("exam_count"));
                item.put("bestExamScore", rs.getBigDecimal("best_exam_score"));
                item.put("readerAccount", rs.getObject("reader_id") != null);
                item.put("tier", rs.getString("tier"));
                item.put("vipExpiresAt", rs.getObject("vip_expires_at", OffsetDateTime.class));
                item.put("vipStatus", vipStatus(
                    rs.getString("tier"),
                    rs.getObject("vip_expires_at", OffsetDateTime.class)));
                item.put("credits", rs.getObject("credits"));
                item.put("lastLoginAt", rs.getObject("last_login_at", OffsetDateTime.class));
                item.put("deliveryCount", rs.getInt("delivery_count"));
                item.put("lastDeliveryAt",
                    rs.getObject("last_delivery_at", OffsetDateTime.class));
                item.put("unlockCount", rs.getInt("unlock_count"));
                item.put("lastUnlockAt",
                    rs.getObject("last_unlock_at", OffsetDateTime.class));
                return item;
            },
            dataParams.toArray());
        Long total = jdbc.queryForObject(
            "SELECT count(*) FROM (" + base + ") audience_filtered",
            Long.class,
            filter.params().toArray());
        return new SearchResult(
            items,
            total == null ? 0 : total,
            page,
            size,
            dynamicFields(),
            facets());
    }

    /**
     * 取得批次快照目標；最多回 501 筆，呼叫端可據此拒絕超過同步上限的操作。
     */
    public List<Long> findPersonIds(Filters filters) {
        SqlFilter filter = buildFilter(filters);
        return jdbc.query(
            "SELECT person_id FROM (" + baseSql() + filter.where()
                + ") audience_filtered ORDER BY person_id LIMIT " + BULK_LIMIT_PLUS_ONE,
            (rs, rowNum) -> rs.getLong("person_id"),
            filter.params().toArray());
    }

    /** Campaign 使用的完整收件名單；無論儲存條件為何都強制只取已確認訂閱者。 */
    public List<String> findRecipientEmails(Filters filters) {
        Filters marketingSafe = withConfirmedConsent(filters);
        SqlFilter filter = buildFilter(marketingSafe);
        return jdbc.query(
            "SELECT email FROM (" + baseSql() + filter.where()
                + ") audience_filtered ORDER BY email",
            (rs, rowNum) -> rs.getString("email"),
            filter.params().toArray());
    }

    /** 依明確 personId 清單驗證存在並去重，供「勾選部分人物」使用。 */
    public List<Long> existingPersonIds(List<Long> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            return List.of();
        }
        List<Long> unique = requestedIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        String placeholders = String.join(",", java.util.Collections.nCopies(unique.size(), "?"));
        return jdbc.query(
            "SELECT id FROM audience_person WHERE id IN (" + placeholders + ") ORDER BY id",
            (rs, rowNum) -> rs.getLong("id"),
            unique.toArray());
    }

    /** 主查詢固定投影，避免篩選條件改變 API 欄位。 */
    private String baseSql() {
        return """
            SELECT
                p.id AS person_id,
                p.email,
                p.display_name,
                COALESCE(consent.status, 'PENDING') AS consent_status,
                COALESCE(identities.sources, ARRAY[]::text[]) AS sources,
                activity.last_activity_at,
                COALESCE(activity.survey_count, 0) AS survey_count,
                COALESCE(activity.exam_count, 0) AS exam_count,
                activity.best_exam_score,
                r.id AS reader_id,
                r.tier,
                r.vip_expires_at,
                r.credits,
                r.last_login_at,
                COALESCE(delivery.delivery_count, 0) AS delivery_count,
                delivery.last_delivery_at,
                COALESCE(unlocks.unlock_count, 0) AS unlock_count,
                unlocks.last_unlock_at
              FROM audience_person p
              LEFT JOIN LATERAL (
                    SELECT status
                      FROM audience_consent c
                     WHERE c.person_id = p.id AND c.channel = 'EMAIL'
                     ORDER BY c.occurred_at DESC, c.id DESC
                     LIMIT 1
              ) consent ON TRUE
              LEFT JOIN LATERAL (
                    SELECT array_agg(DISTINCT source_key ORDER BY source_key) AS sources
                      FROM audience_identity i
                     WHERE i.person_id = p.id
              ) identities ON TRUE
              LEFT JOIN LATERAL (
                    SELECT
                        max(ar.occurred_at) AS last_activity_at,
                        count(*) FILTER (WHERE ar.record_type = 'survey_submission') AS survey_count,
                        count(*) FILTER (WHERE ar.record_type = 'exam_attempt') AS exam_count,
                        max((ar.raw_data ->> 'totalScore')::numeric)
                            FILTER (WHERE ar.record_type = 'exam_attempt') AS best_exam_score
                      FROM audience_record ar
                     WHERE ar.person_id = p.id
              ) activity ON TRUE
              LEFT JOIN reader r ON lower(r.email) = p.email_normalized
              LEFT JOIN LATERAL (
                    SELECT
                        count(*) FILTER (WHERE cr.status = 'SENT') AS delivery_count,
                        max(cr.sent_at) FILTER (WHERE cr.status = 'SENT') AS last_delivery_at
                      FROM campaign_recipient cr
                     WHERE cr.person_id = p.id OR cr.email_normalized = p.email_normalized
              ) delivery ON TRUE
              LEFT JOIN LATERAL (
                    SELECT count(*) AS unlock_count, max(aa.unlocked_at) AS last_unlock_at
                      FROM article_access aa
                     WHERE aa.reader_id = r.id
              ) unlocks ON TRUE
             WHERE 1 = 1
            """;
    }

    /** 依條件建立參數化 SQL，不把任何使用者字串直接拼進查詢。 */
    private SqlFilter buildFilter(Filters filters) {
        if (filters == null) {
            return new SqlFilter("", List.of());
        }
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(filters.query())) {
            where.append(" AND (p.email_normalized LIKE ? OR lower(COALESCE(p.display_name, '')) LIKE ?)");
            String pattern = "%" + escapeLike(filters.query().trim().toLowerCase(java.util.Locale.ROOT)) + "%";
            params.add(pattern);
            params.add(pattern);
        }
        appendIn(where, params, "COALESCE(consent.status, 'PENDING')", filters.consentStatus());
        if (filters.sourceKeys() != null && !filters.sourceKeys().isEmpty()) {
            where.append("""
                 AND EXISTS (
                     SELECT 1 FROM audience_identity source_identity
                      WHERE source_identity.person_id = p.id
                        AND source_identity.source_key IN (
                """);
            appendPlaceholders(where, params, filters.sourceKeys());
            where.append("))");
        }
        if (filters.accountStatus() != null && !filters.accountStatus().isEmpty()) {
            List<String> status = filters.accountStatus();
            if (status.size() == 1) {
                where.append(" AND r.id IS ").append("EXISTS".equalsIgnoreCase(status.getFirst()) ? "NOT NULL" : "NULL");
            }
        }
        appendVipFilter(where, params, filters.vipStatus());
        if (filters.creditsMin() != null) {
            where.append(" AND r.credits >= ?");
            params.add(filters.creditsMin());
        }
        if (filters.creditsMax() != null) {
            where.append(" AND r.credits <= ?");
            params.add(filters.creditsMax());
        }
        appendSurveyFilter(where, params, filters.survey());
        appendExamFilter(where, params, filters.exam());
        appendActivityFilter(where, params, filters.activity());
        appendDeliveryFilter(where, params, filters.delivery());
        appendUnlockFilter(where, params, filters.unlock());
        return new SqlFilter(where.toString(), params);
    }

    /** 電子報寄送條件使用 EXISTS 與彙總欄位，避免逐列 join 造成同一人物重複。 */
    private void appendDeliveryFilter(
            StringBuilder where,
            List<Object> params,
            DeliveryFilter delivery) {
        if (delivery == null) {
            return;
        }
        if (Boolean.TRUE.equals(delivery.neverReceived())) {
            where.append(" AND COALESCE(delivery.delivery_count, 0) = 0");
        } else if (Boolean.FALSE.equals(delivery.neverReceived())) {
            where.append(" AND COALESCE(delivery.delivery_count, 0) > 0");
        }
        if (delivery.countMin() != null) {
            where.append(" AND COALESCE(delivery.delivery_count, 0) >= ?");
            params.add(delivery.countMin());
        }
        if (delivery.countMax() != null) {
            where.append(" AND COALESCE(delivery.delivery_count, 0) <= ?");
            params.add(delivery.countMax());
        }
        if (delivery.campaignId() == null
                && (delivery.statuses() == null || delivery.statuses().isEmpty())
                && delivery.from() == null
                && delivery.to() == null) {
            return;
        }
        where.append("""
             AND EXISTS (
                 SELECT 1 FROM campaign_recipient delivery_row
                  WHERE (delivery_row.person_id = p.id
                         OR delivery_row.email_normalized = p.email_normalized)
            """);
        if (delivery.campaignId() != null) {
            where.append(" AND delivery_row.campaign_id = ?");
            params.add(delivery.campaignId());
        }
        if (delivery.statuses() != null && !delivery.statuses().isEmpty()) {
            where.append(" AND delivery_row.status IN (");
            appendPlaceholders(
                where,
                params,
                delivery.statuses().stream()
                    .map(value -> value.toUpperCase(java.util.Locale.ROOT))
                    .toList());
            where.append(")");
        }
        if (delivery.from() != null) {
            where.append(" AND COALESCE(delivery_row.sent_at, delivery_row.created_at) >= ?");
            params.add(delivery.from());
        }
        if (delivery.to() != null) {
            where.append(" AND COALESCE(delivery_row.sent_at, delivery_row.created_at) < ?");
            params.add(delivery.to());
        }
        where.append(")");
    }

    /** 解鎖條件由 article_access 關聯文章與 hashtag，不碰不可變點數帳本。 */
    private void appendUnlockFilter(
            StringBuilder where,
            List<Object> params,
            UnlockFilter unlock) {
        if (unlock == null) {
            return;
        }
        if (Boolean.TRUE.equals(unlock.neverUnlocked())) {
            where.append(" AND COALESCE(unlocks.unlock_count, 0) = 0");
        } else if (Boolean.FALSE.equals(unlock.neverUnlocked())) {
            where.append(" AND COALESCE(unlocks.unlock_count, 0) > 0");
        }
        if (unlock.countMin() != null) {
            where.append(" AND COALESCE(unlocks.unlock_count, 0) >= ?");
            params.add(unlock.countMin());
        }
        if (unlock.countMax() != null) {
            where.append(" AND COALESCE(unlocks.unlock_count, 0) <= ?");
            params.add(unlock.countMax());
        }
        if (unlock.campaignId() == null
                && !StringUtils.hasText(unlock.tagSlug())
                && unlock.from() == null
                && unlock.to() == null) {
            return;
        }
        where.append("""
             AND EXISTS (
                 SELECT 1
                   FROM article_access unlock_row
                   JOIN campaign unlock_campaign ON unlock_campaign.id = unlock_row.campaign_id
                  WHERE unlock_row.reader_id = r.id
            """);
        if (unlock.campaignId() != null) {
            where.append(" AND unlock_row.campaign_id = ?");
            params.add(unlock.campaignId());
        }
        if (StringUtils.hasText(unlock.tagSlug())) {
            where.append("""
                 AND EXISTS (
                     SELECT 1
                       FROM campaign_tag unlock_campaign_tag
                       JOIN content_tag unlock_tag ON unlock_tag.id = unlock_campaign_tag.tag_id
                      WHERE unlock_campaign_tag.campaign_id = unlock_row.campaign_id
                        AND unlock_tag.slug = ?
                 )
                """);
            params.add(unlock.tagSlug().trim().toLowerCase(java.util.Locale.ROOT));
        }
        if (unlock.from() != null) {
            where.append(" AND unlock_row.unlocked_at >= ?");
            params.add(unlock.from());
        }
        if (unlock.to() != null) {
            where.append(" AND unlock_row.unlocked_at < ?");
            params.add(unlock.to());
        }
        where.append(")");
    }

    /** Survey 條件使用 record schema 與 Fact，不依賴固定 survey_response 欄位。 */
    private void appendSurveyFilter(StringBuilder where, List<Object> params, SurveyFilter survey) {
        if (survey == null || !StringUtils.hasText(survey.formKey())) {
            return;
        }
        where.append("""
             AND EXISTS (
                 SELECT 1 FROM audience_record survey_record
                  WHERE survey_record.person_id = p.id
                    AND survey_record.record_type = 'survey_submission'
                    AND survey_record.schema_key
            """);
        if (survey.version() == null) {
            where.append(" LIKE ?");
            params.add(survey.formKey().trim() + "@%");
        } else {
            where.append(" = ?");
            params.add(survey.formKey().trim() + "@" + survey.version());
        }
        if (survey.answers() != null) {
            survey.answers().forEach((key, value) -> {
                where.append("""
                     AND survey_record.raw_data -> 'answers' -> ? = ?::jsonb
                    """);
                params.add(key);
                params.add(toJsonScalar(value));
            });
        }
        where.append(")");
    }

    /** Exam 條件使用 typed Fact，可同時限制場次、分數、正確率與時間。 */
    private void appendExamFilter(StringBuilder where, List<Object> params, ExamFilter exam) {
        if (exam == null) {
            return;
        }
        where.append("""
             AND EXISTS (
                 SELECT 1 FROM audience_record exam_record
                  WHERE exam_record.person_id = p.id
                    AND exam_record.record_type = 'exam_attempt'
            """);
        if (StringUtils.hasText(exam.examId())) {
            where.append(" AND exam_record.schema_key = ?");
            params.add("exam:" + exam.examId().trim());
        }
        appendJsonNumberRange(where, params, "exam_record.raw_data ->> 'totalScore'", exam.score());
        appendJsonNumberRange(where, params, "exam_record.raw_data ->> 'scoreRate'", exam.scoreRate());
        if (exam.attemptedFrom() != null) {
            where.append(" AND exam_record.occurred_at >= ?");
            params.add(exam.attemptedFrom());
        }
        if (exam.attemptedTo() != null) {
            where.append(" AND exam_record.occurred_at < ?");
            params.add(exam.attemptedTo());
        }
        where.append(")");
    }

    /** 活動條件使用 EXISTS，避免同一人物因多筆活動在結果中重複。 */
    private void appendActivityFilter(StringBuilder where, List<Object> params, ActivityFilter activity) {
        if (activity == null) {
            return;
        }
        where.append(" AND EXISTS (SELECT 1 FROM audience_record activity_record WHERE activity_record.person_id = p.id");
        if (activity.recordTypes() != null && !activity.recordTypes().isEmpty()) {
            where.append(" AND activity_record.record_type IN (");
            appendPlaceholders(where, params, activity.recordTypes());
            where.append(")");
        }
        if (activity.from() != null) {
            where.append(" AND activity_record.occurred_at >= ?");
            params.add(activity.from());
        }
        if (activity.to() != null) {
            where.append(" AND activity_record.occurred_at < ?");
            params.add(activity.to());
        }
        where.append(")");
    }

    /** VIP 狀態以現在時間計算，不直接相信 tier 字串。 */
    private void appendVipFilter(StringBuilder where, List<Object> params, List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return;
        }
        List<String> clauses = new ArrayList<>();
        for (String status : statuses) {
            switch (status.toUpperCase(java.util.Locale.ROOT)) {
                case "NONE" -> clauses.add("(r.id IS NULL OR r.tier <> 'VIP')");
                case "ACTIVE" -> clauses.add("(r.tier = 'VIP' AND (r.vip_expires_at IS NULL OR r.vip_expires_at > now()))");
                case "EXPIRING" -> clauses.add("(r.tier = 'VIP' AND r.vip_expires_at > now() AND r.vip_expires_at <= now() + interval '30 days')");
                case "EXPIRED" -> clauses.add("(r.tier = 'VIP' AND r.vip_expires_at <= now())");
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支援的 VIP 篩選：" + status);
            }
        }
        where.append(" AND (").append(String.join(" OR ", clauses)).append(")");
    }

    /** 數值 JSON 欄位區間。 */
    private void appendJsonNumberRange(
            StringBuilder where,
            List<Object> params,
            String expression,
            BigDecimalRange range) {
        if (range == null) {
            return;
        }
        if (range.min() != null) {
            where.append(" AND NULLIF(").append(expression).append(", '')::numeric >= ?");
            params.add(range.min());
        }
        if (range.max() != null) {
            where.append(" AND NULLIF(").append(expression).append(", '')::numeric <= ?");
            params.add(range.max());
        }
    }

    /** 一般 IN 條件。 */
    private void appendIn(
            StringBuilder where,
            List<Object> params,
            String expression,
            List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        where.append(" AND ").append(expression).append(" IN (");
        appendPlaceholders(where, params, values);
        where.append(")");
    }

    /** 產生參數 placeholder 並加入值。 */
    private void appendPlaceholders(
            StringBuilder sql,
            List<Object> params,
            List<String> values) {
        sql.append(String.join(",", java.util.Collections.nCopies(values.size(), "?")));
        params.addAll(values);
    }

    /** 排序欄位與方向皆使用白名單。 */
    private String orderBy(Sort sort) {
        String field = sort == null || !StringUtils.hasText(sort.field())
            ? "lastActivityAt"
            : sort.field();
        String column = switch (field) {
            case "email" -> "p.email_normalized";
            case "name" -> "p.display_name";
            case "credits" -> "r.credits";
            case "vipExpiresAt" -> "r.vip_expires_at";
            case "lastLoginAt" -> "r.last_login_at";
            case "lastActivityAt" -> "activity.last_activity_at";
            case "deliveryCount" -> "delivery.delivery_count";
            case "lastDeliveryAt" -> "delivery.last_delivery_at";
            case "unlockCount" -> "unlocks.unlock_count";
            case "lastUnlockAt" -> "unlocks.last_unlock_at";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支援的排序欄位");
        };
        String direction = sort != null && "ASC".equalsIgnoreCase(sort.direction()) ? "ASC" : "DESC";
        return "ORDER BY " + column + " " + direction + " NULLS LAST, p.id ASC";
    }

    /** 可勾選成列表欄位的動態 Fact key。 */
    private List<String> dynamicFields() {
        return jdbc.query("""
            SELECT fact_key
              FROM audience_fact
             GROUP BY fact_key
             ORDER BY count(*) DESC, fact_key
             LIMIT 100
            """, (rs, rowNum) -> rs.getString("fact_key"));
    }

    /** 常用 facet 先回來源與同意狀態；其餘可依同一模式擴充。 */
    private Map<String, Object> facets() {
        Map<String, Object> facets = new LinkedHashMap<>();
        facets.put("sources", jdbc.query("""
            SELECT source_key AS value, count(DISTINCT person_id) AS count
              FROM audience_identity GROUP BY source_key ORDER BY count DESC, source_key
            """, (rs, rowNum) -> Map.of(
                "value", rs.getString("value"),
                "count", rs.getLong("count"))));
        facets.put("consent", jdbc.query("""
            SELECT status AS value, count(*) AS count
              FROM (
                    SELECT DISTINCT ON (person_id) person_id, status
                      FROM audience_consent
                     WHERE channel = 'EMAIL'
                     ORDER BY person_id, occurred_at DESC, id DESC
              ) latest
             GROUP BY status ORDER BY count DESC, status
            """, (rs, rowNum) -> Map.of(
                "value", rs.getString("value"),
                "count", rs.getLong("count"))));
        return facets;
    }

    /** Reader tier 與到期時間轉成 UI 使用狀態。 */
    private String vipStatus(String tier, OffsetDateTime expiresAt) {
        if (!"VIP".equals(tier)) {
            return "NONE";
        }
        if (expiresAt == null || expiresAt.isAfter(OffsetDateTime.now().plusDays(30))) {
            return "ACTIVE";
        }
        if (expiresAt.isAfter(OffsetDateTime.now())) {
            return "EXPIRING";
        }
        return "EXPIRED";
    }

    /** LIKE 跳脫，讓 % 與 _ 被當成一般文字。 */
    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** 將簡單答案值轉成 PostgreSQL jsonb literal 參數。 */
    private String toJsonScalar(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return "\"" + String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** 行銷寄送永遠覆寫成 CONFIRMED，不能讓保存的分眾條件繞過同意狀態。 */
    private Filters withConfirmedConsent(Filters filters) {
        if (filters == null) {
            return new Filters(
                null, null, List.of("CONFIRMED"), null, null,
                null, null, null, null, null);
        }
        return new Filters(
            filters.query(),
            filters.sourceKeys(),
            List.of("CONFIRMED"),
            filters.accountStatus(),
            filters.vipStatus(),
            filters.creditsMin(),
            filters.creditsMax(),
            filters.survey(),
            filters.exam(),
            filters.activity(),
            filters.delivery(),
            filters.unlock());
    }

    /** SQL 片段與參數必須一起傳遞，避免漏加或順序錯位。 */
    private record SqlFilter(String where, List<Object> params) {}
}
