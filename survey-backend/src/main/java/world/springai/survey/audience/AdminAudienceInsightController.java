package world.springai.survey.audience;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.AdminKeyGuard;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 後台讀者寄送、解鎖與點數歷程查詢 API。 */
@RestController
public class AdminAudienceInsightController {

    private final AdminKeyGuard guard;
    private final JdbcTemplate jdbc;

    /** 注入管理金鑰守衛與參數化查詢工具。 */
    public AdminAudienceInsightController(AdminKeyGuard guard, JdbcTemplate jdbc) {
        this.guard = guard;
        this.jdbc = jdbc;
    }

    /** 回傳進階篩選可使用的文章與 hashtag 選項。 */
    @GetMapping("/api/admin/audience/insights/options")
    public Map<String, Object> options(
            @RequestHeader(value = "X-Admin-Key", required = false) String key) {
        guard.verify(key);
        List<Map<String, Object>> campaigns = jdbc.queryForList("""
            SELECT id, subject, status, published_at AS "publishedAt", created_at AS "createdAt"
              FROM campaign
             ORDER BY COALESCE(published_at, created_at) DESC, id DESC
            """);
        List<Map<String, Object>> tags = jdbc.queryForList("""
            SELECT slug, name, preset
              FROM content_tag
             WHERE active = TRUE
             ORDER BY sort_order, name
            """);
        return Map.of("campaigns", campaigns, "tags", tags);
    }

    /** 回傳單一人物的帳戶摘要、收信、解鎖及點數帳本。 */
    @GetMapping("/api/admin/audience/{personId}/detail")
    public Map<String, Object> detail(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @PathVariable long personId) {
        guard.verify(key);
        List<Map<String, Object>> overviews = jdbc.query("""
            SELECT p.id AS "personId", p.email, p.display_name AS name,
                   p.created_at AS "createdAt",
                   COALESCE(consent.status, 'PENDING') AS "consentStatus",
                   COALESCE(sources.values, ARRAY[]::text[]) AS sources,
                   r.id AS "readerId", r.tier, r.vip_expires_at AS "vipExpiresAt",
                   r.credits, r.last_login_at AS "lastLoginAt"
              FROM audience_person p
              LEFT JOIN LATERAL (
                    SELECT status
                      FROM audience_consent
                     WHERE person_id = p.id AND channel = 'EMAIL'
                     ORDER BY occurred_at DESC, id DESC LIMIT 1
              ) consent ON TRUE
              LEFT JOIN LATERAL (
                    SELECT array_agg(DISTINCT source_key ORDER BY source_key) AS values
                      FROM audience_identity
                     WHERE person_id = p.id
              ) sources ON TRUE
             LEFT JOIN reader r ON lower(r.email) = p.email_normalized
             WHERE p.id = ?
            """, (rs, rowNum) -> {
                Map<String, Object> overview = new LinkedHashMap<>();
                overview.put("personId", rs.getLong("personId"));
                overview.put("email", rs.getString("email"));
                overview.put("name", rs.getString("name"));
                overview.put("createdAt", rs.getObject("createdAt"));
                overview.put("consentStatus", rs.getString("consentStatus"));
                overview.put("sources", rs.getArray("sources") == null
                    ? List.of()
                    : List.of((Object[]) rs.getArray("sources").getArray()));
                overview.put("readerId", rs.getObject("readerId"));
                overview.put("tier", rs.getString("tier"));
                overview.put("vipExpiresAt", rs.getObject("vipExpiresAt"));
                overview.put("credits", rs.getObject("credits"));
                overview.put("lastLoginAt", rs.getObject("lastLoginAt"));
                return overview;
            }, personId);
        if (overviews.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到指定讀者");
        }

        List<Map<String, Object>> deliveries = jdbc.queryForList("""
            SELECT cr.campaign_id AS "campaignId", c.subject,
                   cr.status, cr.email, cr.sent_at AS "sentAt",
                   cr.scheduled_at AS "scheduledAt", cr.updated_at AS "updatedAt",
                   cr.error, cr.batch_id AS "batchId", b.mode
              FROM campaign_recipient cr
              JOIN campaign c ON c.id = cr.campaign_id
              LEFT JOIN campaign_batch b ON b.id = cr.batch_id
             WHERE cr.person_id = ?
                OR (cr.person_id IS NULL
                    AND cr.email_normalized = (
                        SELECT email_normalized FROM audience_person WHERE id = ?
                    ))
             ORDER BY COALESCE(cr.sent_at, cr.scheduled_at, cr.updated_at) DESC, cr.id DESC
            """, personId, personId);

        List<Map<String, Object>> unlocks = jdbc.query("""
            SELECT aa.campaign_id AS "campaignId", c.subject, c.slug,
                   aa.cost, aa.unlocked_at AS "unlockedAt",
                   COALESCE(tags.names, ARRAY[]::text[]) AS tags
              FROM article_access aa
              JOIN reader r ON r.id = aa.reader_id
              JOIN audience_person p ON p.email_normalized = lower(r.email)
              JOIN campaign c ON c.id = aa.campaign_id
              LEFT JOIN LATERAL (
                    SELECT array_agg(t.name ORDER BY t.sort_order, t.name) AS names
                      FROM campaign_tag ct
                      JOIN content_tag t ON t.id = ct.tag_id
                     WHERE ct.campaign_id = c.id
              ) tags ON TRUE
             WHERE p.id = ?
             ORDER BY aa.unlocked_at DESC, aa.id DESC
            """, (rs, rowNum) -> {
                Map<String, Object> unlock = new LinkedHashMap<>();
                unlock.put("campaignId", rs.getLong("campaignId"));
                unlock.put("subject", rs.getString("subject"));
                unlock.put("slug", rs.getString("slug"));
                unlock.put("cost", rs.getInt("cost"));
                unlock.put("unlockedAt", rs.getObject("unlockedAt"));
                unlock.put("tags", rs.getArray("tags") == null
                    ? List.of()
                    : List.of((Object[]) rs.getArray("tags").getArray()));
                return unlock;
            }, personId);

        List<Map<String, Object>> ledger = jdbc.queryForList("""
            SELECT tx.id, tx.delta, tx.reason, tx.note,
                   tx.campaign_id AS "campaignId", c.subject,
                   tx.created_at AS "createdAt"
              FROM credit_txn tx
              JOIN reader r ON r.id = tx.reader_id
              JOIN audience_person p ON p.email_normalized = lower(r.email)
              LEFT JOIN campaign c ON c.id = tx.campaign_id
             WHERE p.id = ?
             ORDER BY tx.created_at DESC, tx.id DESC
            """, personId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overview", overviews.getFirst());
        result.put("deliveries", deliveries);
        result.put("unlocks", unlocks);
        result.put("ledger", ledger);
        return result;
    }
}
