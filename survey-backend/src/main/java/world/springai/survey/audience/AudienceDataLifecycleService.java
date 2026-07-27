package world.springai.survey.audience;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 人物資料匯出、刪除與原始活動資料保留期限。 */
@Service
public class AudienceDataLifecycleService {

    private final JdbcTemplate jdbc;
    private final int rawRetentionDays;

    /** 注入資料層與保留天數；0 代表不自動清理。 */
    public AudienceDataLifecycleService(
            JdbcTemplate jdbc,
            @Value("${app.audience.raw-retention-days:0}") int rawRetentionDays) {
        this.jdbc = jdbc;
        this.rawRetentionDays = rawRetentionDays;
    }

    /** 匯出指定 Email 的人物、同意、來源、活動、Fact 與 Reader 摘要。 */
    public Map<String, Object> export(String email) {
        String normalized = requireEmail(email);
        List<Map<String, Object>> people = jdbc.queryForList("""
            SELECT id, email, display_name, created_at, updated_at
              FROM audience_person WHERE email_normalized = ?
            """, normalized);
        if (people.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到此人物");
        }
        long personId = ((Number) people.getFirst().get("id")).longValue();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exportedAt", OffsetDateTime.now(ZoneOffset.UTC));
        result.put("person", people.getFirst());
        result.put("consents", jdbc.queryForList("""
            SELECT channel, status, source_key, consent_version, evidence,
                   occurred_at, created_at
              FROM audience_consent WHERE person_id = ?
             ORDER BY occurred_at, id
            """, personId));
        result.put("identities", jdbc.queryForList("""
            SELECT source_key, external_type, external_id, created_at, updated_at
              FROM audience_identity WHERE person_id = ? ORDER BY source_key, id
            """, personId));
        result.put("records", jdbc.queryForList("""
            SELECT id, source_key, record_type, schema_key, external_record_id,
                   occurred_at, raw_data, summary_data, created_at, updated_at
              FROM audience_record WHERE person_id = ? ORDER BY occurred_at, id
            """, personId));
        result.put("facts", jdbc.queryForList("""
            SELECT record_id, fact_key, value_text, value_number, value_boolean,
                   value_time, source_key, observed_at
              FROM audience_fact WHERE person_id = ? ORDER BY observed_at, id
            """, personId));
        result.put("reader", jdbc.queryForList("""
            SELECT tier, vip_expires_at, credits, last_login_at, created_at
              FROM reader WHERE lower(email) = ?
            """, normalized));
        return result;
    }

    /**
     * 刪除個資與活動。若最新狀態為退訂，保留不可逆 hash 防止未來匯入重新建立。
     * Reader 帳本需保留對帳，因此只匿名化 Reader email，不刪除帳本。
     */
    @Transactional
    public Map<String, Object> delete(String email, String reason) {
        String normalized = requireEmail(email);
        if (!StringUtils.hasText(reason)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "刪除原因為必填");
        }
        List<Long> personIds = jdbc.query("""
            SELECT id FROM audience_person WHERE email_normalized = ?
            """, (rs, rowNum) -> rs.getLong("id"), normalized);
        if (personIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到此人物");
        }
        long personId = personIds.getFirst();
        String latestConsent = jdbc.query("""
            SELECT status FROM audience_consent
             WHERE person_id = ? AND channel = 'EMAIL'
             ORDER BY occurred_at DESC, id DESC LIMIT 1
            """, (rs, rowNum) -> rs.getString("status"), personId)
            .stream().findFirst().orElse(AudiencePlatformService.CONSENT_PENDING);
        String emailHash = AudiencePlatformService.hashEmail(normalized);
        if (AudiencePlatformService.CONSENT_UNSUBSCRIBED.equals(latestConsent)) {
            jdbc.update("""
                INSERT INTO audience_suppression (email_hash, reason)
                VALUES (?, ?)
                ON CONFLICT (email_hash) DO NOTHING
                """, emailHash, reason.trim());
        }
        UUID requestId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO audience_data_request (
                id, person_id, email_normalized, request_type, status
            ) VALUES (?, ?, ?, 'DELETE', 'RUNNING')
            """, requestId, personId, normalized);

        int surveyRows = jdbc.update(
            "DELETE FROM survey_response WHERE lower(btrim(email)) = ?", normalized);
        int loginTokens = jdbc.update(
            "DELETE FROM login_token WHERE lower(btrim(email)) = ?", normalized);
        int emailLogs = jdbc.update(
            "DELETE FROM email_log WHERE lower(btrim(recipient)) = ?", normalized);
        String anonymizedEmail = "deleted-" + emailHash.substring(0, 24) + "@invalid.local";
        int readers = jdbc.update("""
            UPDATE reader
               SET email = ?, referral_code = ?
             WHERE lower(email) = ?
            """, anonymizedEmail, "deleted-" + emailHash.substring(0, 20), normalized);
        int people = jdbc.update("DELETE FROM audience_person WHERE id = ?", personId);
        Map<String, Object> counts = Map.of(
            "peopleDeleted", people,
            "legacySurveyRowsDeleted", surveyRows,
            "loginTokensDeleted", loginTokens,
            "emailLogsDeleted", emailLogs,
            "readersAnonymized", readers,
            "suppressionPreserved",
                AudiencePlatformService.CONSENT_UNSUBSCRIBED.equals(latestConsent));
        jdbc.update("""
            UPDATE audience_data_request
               SET status = 'COMPLETED', result_data = ?::jsonb, completed_at = now()
             WHERE id = ?
            """, toJson(counts), requestId);
        Map<String, Object> result = new LinkedHashMap<>(counts);
        result.put("requestId", requestId);
        return result;
    }

    /**
     * 依設定清理過期 raw_data；summary 與 Fact 保留統計用途，預設 0 天代表停用。
     */
    @Scheduled(cron = "${app.audience.retention-cron:0 35 3 * * *}")
    public void redactExpiredRawData() {
        if (rawRetentionDays <= 0) {
            return;
        }
        int affected = jdbc.update("""
            UPDATE audience_record
               SET raw_data = jsonb_build_object(
                       'retained', false,
                       'redactedAt', now()::text
                   ),
                   updated_at = now()
             WHERE occurred_at < now() - (? * interval '1 day')
               AND COALESCE(raw_data ->> 'retained', 'true') <> 'false'
            """, rawRetentionDays);
        if (affected > 0) {
            org.slf4j.LoggerFactory.getLogger(AudienceDataLifecycleService.class)
                .info("已依資料保留政策清理 {} 筆 audience_record raw_data", affected);
        }
    }

    /** 正規化並驗證 Email。 */
    private String requireEmail(String email) {
        String normalized = AudiencePlatformService.normalizeEmail(email);
        if (!StringUtils.hasText(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email 為必填");
        }
        return normalized;
    }

    /** 小型結果 Map 轉 JSON；內容由系統產生，不含未驗證物件。 */
    private String toJson(Map<String, Object> value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("刪除結果無法序列化", exception);
        }
    }
}
