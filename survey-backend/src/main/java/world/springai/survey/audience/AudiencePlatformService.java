package world.springai.survey.audience;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 彈性名單資料核心：集中處理人物合併、同意保護、活動冪等寫入與 Fact 重建。
 *
 * <p>舊 {@code survey_response} 在相容期仍會保留，但任何新功能都應透過本服務寫入
 * 新模型，避免不同 Controller 各自實作 Email 正規化與退訂保護。</p>
 */
@Service
public class AudiencePlatformService {

    /** 行銷 Email 管道代碼。 */
    public static final String CHANNEL_EMAIL = "EMAIL";
    /** 待確認同意狀態。 */
    public static final String CONSENT_PENDING = "PENDING";
    /** 已確認同意狀態。 */
    public static final String CONSENT_CONFIRMED = "CONFIRMED";
    /** 已退訂狀態；任何匯入都不得覆蓋。 */
    public static final String CONSENT_UNSUBSCRIBED = "UNSUBSCRIBED";

    /**
     * 「讀者實際點了信裡的確認連結」這個行為證據的 source_key（唯一真相來源）。
     *
     * <p>這個字面值同時決定三件事：{@code SubscriptionController} 寫入時的來源標記、
     * 後台「信箱確認」KPI 的篩選條件，以及補寄確認信名單的排除條件。三處若各自
     * 寫死字串，其中一處被改到就會讓另外兩處無聲失準，所以集中在這裡。</p>
     */
    public static final String SOURCE_CONFIRMATION_LINK = "confirmation-link";

    /**
     * 「這個人留下過點擊確認連結的紀錄」的共用 SQL 條件片段。
     *
     * <p>使用時外層需自備 {@code audience_person p} 與 {@code audience_consent c}
     * 兩個別名。channel 明確寫出：目前 EMAIL 是唯一管道，未來新增管道時這個
     * 數字不會無聲混入別的管道。</p>
     */
    public static final String CONFIRMED_BY_LINK_CONDITIONS = """
        c.channel = '%s'
               and c.status = '%s'
               and c.source_key = '%s'""".formatted(
        CHANNEL_EMAIL, CONSENT_CONFIRMED, SOURCE_CONFIRMATION_LINK);

    /** 全站「實際點過確認連結」的人數查詢；後台 KPI 與補寄統計共用同一份口徑。 */
    public static final String CONFIRMED_BY_LINK_COUNT_SQL = """
        select count(distinct p.id)
          from audience_person p
          join audience_consent c on c.person_id = p.id
         where %s
        """.formatted(CONFIRMED_BY_LINK_CONDITIONS);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    /** 注入 PostgreSQL 存取工具與共用 JSON 序列化器。 */
    public AudiencePlatformService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** 人物合併結果；created 用於匯入預覽與稽核計數。 */
    public record PersonResult(long personId, boolean created, String emailNormalized) {}

    /** 活動寫入結果；狀態分為 CREATED、UPDATED、UNCHANGED。 */
    public record RecordResult(long recordId, String status, String payloadHash) {}

    /**
     * 以 Email 合併人物；相同 Email 只更新最新姓名，不會刪除任何歷史活動。
     */
    @Transactional
    public PersonResult mergePerson(String email, String displayName, OffsetDateTime occurredAt) {
        String normalized = normalizeEmail(email);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Email 不可為空");
        }
        if (isSuppressed(normalized)) {
            throw new SuppressedEmailException("此 Email 已要求停止處理，不能由匯入重新建立");
        }
        String normalizedName = normalizeOptional(displayName);
        OffsetDateTime timestamp = occurredAt == null
            ? OffsetDateTime.now(ZoneOffset.UTC)
            : occurredAt;
        Map<String, Object> row = jdbc.queryForMap("""
            INSERT INTO audience_person (
                email, email_normalized, display_name, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (email_normalized) DO UPDATE SET
                email = EXCLUDED.email,
                display_name = COALESCE(EXCLUDED.display_name, audience_person.display_name),
                updated_at = GREATEST(audience_person.updated_at, EXCLUDED.updated_at)
            RETURNING id, (xmax = 0) AS inserted
            """, email.trim(), normalized, normalizedName, timestamp, timestamp);
        return new PersonResult(
            ((Number) row.get("id")).longValue(),
            Boolean.TRUE.equals(row.get("inserted")),
            normalized);
    }

    /**
     * 建立或更新外部來源身分；同一外部 ID 改綁不同人物時以最新可驗證 Email 為準。
     */
    @Transactional
    public void upsertIdentity(
            long personId,
            String sourceKey,
            String externalType,
            String externalId,
            OffsetDateTime occurredAt) {
        OffsetDateTime timestamp = occurredAt == null
            ? OffsetDateTime.now(ZoneOffset.UTC)
            : occurredAt;
        jdbc.update("""
            INSERT INTO audience_identity (
                person_id, source_key, external_type, external_id, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (source_key, external_type, external_id) DO UPDATE SET
                person_id = EXCLUDED.person_id,
                updated_at = GREATEST(audience_identity.updated_at, EXCLUDED.updated_at)
            """, personId, required(sourceKey, "sourceKey"), required(externalType, "externalType"),
            required(externalId, "externalId"), timestamp, timestamp);
    }

    /**
     * 寫入同意事件。若目前最新狀態已是退訂，匯入或舊表單不得改回待確認／已確認。
     *
     * <p><b>{@link #SOURCE_CONFIRMATION_LINK} 是「狀態未變即略過」的唯一例外</b>：
     * 讀者在訂閱首頁勾了同意送出時，{@code SurveySubmissionService} 當下就寫了一列
     * {@code CONFIRMED}；等他之後真的去信箱點確認連結時，狀態相同而被略過，
     * 於是「確認連結被點過」這件事在資料庫裡一列都不會留下——後台的「信箱確認」
     * KPI 與補寄名單的排除條件因此對所有 {@code consent=true} 的訂閱者結構性恆為 0。
     * 這個 source_key 記錄的是<b>行為證據</b>而非狀態同步，必須照寫。</p>
     *
     * <p><b>退訂保護仍優先於這個例外</b>：已退訂者點了舊確認信不得被改回訂閱狀態，
     * 所以順序上先擋 UNSUBSCRIBED，再處理 confirmation-link 的例外。</p>
     *
     * @return true 表示新增事件，false 表示因退訂保護或狀態未變而略過
     */
    @Transactional
    public boolean appendConsent(
            long personId,
            String status,
            String sourceKey,
            String consentVersion,
            Map<String, Object> evidence,
            OffsetDateTime occurredAt) {
        String requestedStatus = required(status, "status").toUpperCase(Locale.ROOT);
        if (!List.of(CONSENT_PENDING, CONSENT_CONFIRMED, CONSENT_UNSUBSCRIBED)
                .contains(requestedStatus)) {
            throw new IllegalArgumentException("不支援的同意狀態：" + requestedStatus);
        }
        List<String> current = jdbc.query("""
            SELECT status FROM audience_consent
             WHERE person_id = ? AND channel = ?
             ORDER BY occurred_at DESC, id DESC
             LIMIT 1
            """, (rs, rowNum) -> rs.getString("status"), personId, CHANNEL_EMAIL);
        // 確認連結的點擊要留下行為證據，但退訂保護仍然優先（順序不可對調）
        boolean confirmationLink = SOURCE_CONFIRMATION_LINK.equals(normalizeOptional(sourceKey));
        if (!current.isEmpty()) {
            if (CONSENT_UNSUBSCRIBED.equals(current.getFirst())
                    && !CONSENT_UNSUBSCRIBED.equals(requestedStatus)) {
                return false;
            }
            if (current.getFirst().equals(requestedStatus) && !confirmationLink) {
                return false;
            }
        }
        OffsetDateTime timestamp = occurredAt == null
            ? OffsetDateTime.now(ZoneOffset.UTC)
            : occurredAt;
        jdbc.update("""
            INSERT INTO audience_consent (
                person_id, channel, status, source_key, consent_version, evidence, occurred_at
            ) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
            """, personId, CHANNEL_EMAIL, requestedStatus, required(sourceKey, "sourceKey"),
            normalizeOptional(consentVersion), json(evidence == null ? Map.of() : evidence), timestamp);
        return true;
    }

    /**
     * 依 Email 寫入同意事件；相容端點查無新人物時安全略過，不額外建立陌生人物。
     */
    @Transactional
    public boolean appendConsentByEmail(
            String email,
            String status,
            String sourceKey,
            Map<String, Object> evidence,
            OffsetDateTime occurredAt) {
        List<Long> personIds = jdbc.query("""
            SELECT id FROM audience_person WHERE email_normalized = ?
            """, (rs, rowNum) -> rs.getLong("id"), normalizeEmail(email));
        if (personIds.isEmpty()) {
            return false;
        }
        return appendConsent(
            personIds.getFirst(), status, sourceKey, null, evidence, occurredAt);
    }

    /**
     * 以來源、活動類型與外部 ID 冪等寫入活動；payload 相同時不做無意義 UPDATE。
     */
    @Transactional
    public RecordResult upsertRecord(
            long personId,
            String sourceKey,
            String recordType,
            String schemaKey,
            String externalRecordId,
            OffsetDateTime occurredAt,
            Map<String, Object> rawData,
            Map<String, Object> summaryData) {
        String rawJson = json(rawData == null ? Map.of() : rawData);
        String summaryJson = json(summaryData == null ? Map.of() : summaryData);
        String payloadHash = sha256(rawJson);
        List<Map<String, Object>> existing = jdbc.queryForList("""
            SELECT id, payload_hash FROM audience_record
             WHERE source_key = ? AND record_type = ? AND external_record_id = ?
            """, required(sourceKey, "sourceKey"), required(recordType, "recordType"),
            required(externalRecordId, "externalRecordId"));
        OffsetDateTime timestamp = occurredAt == null
            ? OffsetDateTime.now(ZoneOffset.UTC)
            : occurredAt;
        if (existing.isEmpty()) {
            Long id = jdbc.queryForObject("""
                INSERT INTO audience_record (
                    person_id, source_key, record_type, schema_key, external_record_id,
                    occurred_at, raw_data, summary_data, payload_hash, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)
                RETURNING id
                """, Long.class, personId, sourceKey, recordType, normalizeOptional(schemaKey),
                externalRecordId, timestamp, rawJson, summaryJson, payloadHash, timestamp, timestamp);
            return new RecordResult(id, "CREATED", payloadHash);
        }
        long recordId = ((Number) existing.getFirst().get("id")).longValue();
        if (payloadHash.equals(existing.getFirst().get("payload_hash"))) {
            return new RecordResult(recordId, "UNCHANGED", payloadHash);
        }
        jdbc.update("""
            UPDATE audience_record
               SET person_id = ?, schema_key = ?, occurred_at = ?,
                   raw_data = ?::jsonb, summary_data = ?::jsonb,
                   payload_hash = ?, updated_at = now()
             WHERE id = ?
            """, personId, normalizeOptional(schemaKey), timestamp, rawJson, summaryJson,
            payloadHash, recordId);
        return new RecordResult(recordId, "UPDATED", payloadHash);
    }

    /**
     * 以新的鍵值集合重建單一活動的 Fact；更新活動時不留下舊分數或舊答案。
     */
    @Transactional
    public void replaceFacts(
            long personId,
            long recordId,
            String sourceKey,
            OffsetDateTime observedAt,
            Map<String, ?> facts) {
        jdbc.update("DELETE FROM audience_fact WHERE record_id = ?", recordId);
        if (facts == null || facts.isEmpty()) {
            return;
        }
        OffsetDateTime timestamp = observedAt == null
            ? OffsetDateTime.now(ZoneOffset.UTC)
            : observedAt;
        List<Object[]> rows = new ArrayList<>();
        facts.forEach((factKey, value) ->
            flattenFact(rows, personId, recordId, required(factKey, "factKey"),
                value, required(sourceKey, "sourceKey"), timestamp));
        if (!rows.isEmpty()) {
            jdbc.batchUpdate("""
                INSERT INTO audience_fact (
                    person_id, record_id, fact_key,
                    value_text, value_number, value_boolean, value_time,
                    source_key, observed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, rows);
        }
    }

    /**
     * 建立一筆匯入批次，後續每個 item 與計數都可回查。
     */
    @Transactional
    public long startImportBatch(String sourceKey, String importType, String cursor) {
        Long id = jdbc.queryForObject("""
            INSERT INTO import_batch (source_key, import_type, status, cursor_value)
            VALUES (?, ?, 'RUNNING', ?)
            RETURNING id
            """, Long.class, required(sourceKey, "sourceKey"),
            required(importType, "importType"), normalizeOptional(cursor));
        return id;
    }

    /** 寫入單筆匯入結果；payload 只保存 hash，不在 log 或錯誤訊息洩漏 Email。 */
    @Transactional
    public void addImportItem(
            long batchId,
            String externalRecordId,
            Long personId,
            String status,
            String errorCode,
            String errorMessage,
            String payloadHash) {
        jdbc.update("""
            INSERT INTO import_item (
                batch_id, external_record_id, person_id, status,
                error_code, error_message, payload_hash
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """, batchId, normalizeOptional(externalRecordId), personId, required(status, "status"),
            normalizeOptional(errorCode), normalizeOptional(errorMessage), normalizeOptional(payloadHash));
    }

    /** 完成匯入批次並保存六種結果計數。 */
    @Transactional
    public void completeImportBatch(long batchId, ImportSummary summary) {
        jdbc.update("""
            UPDATE import_batch
               SET status = 'COMPLETED',
                   people_created = ?, people_merged = ?,
                   records_created = ?, records_updated = ?,
                   unchanged_count = ?, invalid_count = ?,
                   completed_at = now()
             WHERE id = ?
            """, summary.peopleCreated(), summary.peopleMerged(), summary.recordsCreated(),
            summary.recordsUpdated(), summary.unchanged(), summary.invalid(), batchId);
    }

    /** 匯入途中發生不可恢復錯誤時保存失敗狀態，讓 Admin 不會只看到永遠 RUNNING。 */
    @Transactional
    public void failImportBatch(long batchId, String message) {
        jdbc.update("""
            UPDATE import_batch
               SET status = 'FAILED', error_message = ?, completed_at = now()
             WHERE id = ?
            """, normalizeOptional(message), batchId);
    }

    /** 通用匯入摘要，對應 Admin 預覽與執行結果。 */
    public record ImportSummary(
            int peopleCreated,
            int peopleMerged,
            int recordsCreated,
            int recordsUpdated,
            int unchanged,
            int invalid) {}

    /** Email 正規化規則：trim + Locale.ROOT lowercase。 */
    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** 不可逆 Email hash；刪除個資後只保留此值作退訂抑制比對。 */
    public static String hashEmail(String email) {
        return sha256Static(normalizeEmail(email));
    }

    /** 查詢 Email 是否在刪除／退訂抑制名單。 */
    public boolean isSuppressed(String email) {
        Integer count = jdbc.queryForObject("""
            SELECT count(*) FROM audience_suppression WHERE email_hash = ?
            """, Integer.class, hashEmail(email));
        return count != null && count > 0;
    }

    /** 被資料抑制名單阻擋的輸入；匯入服務可將它列為 invalid 而不是整批失敗。 */
    public static class SuppressedEmailException extends IllegalArgumentException {
        public SuppressedEmailException(String message) {
            super(message);
        }
    }

    /** 將單值或集合攤平成 typed Fact 列。 */
    private void flattenFact(
            List<Object[]> rows,
            long personId,
            long recordId,
            String factKey,
            Object value,
            String sourceKey,
            OffsetDateTime observedAt) {
        if (value == null) {
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item ->
                flattenFact(rows, personId, recordId, factKey, item, sourceKey, observedAt));
            return;
        }
        String text = null;
        BigDecimal number = null;
        Boolean bool = null;
        OffsetDateTime time = null;
        if (value instanceof Number numeric) {
            number = new BigDecimal(numeric.toString());
        } else if (value instanceof Boolean booleanValue) {
            bool = booleanValue;
        } else if (value instanceof OffsetDateTime offsetDateTime) {
            time = offsetDateTime;
        } else if (StringUtils.hasText(String.valueOf(value))) {
            text = String.valueOf(value);
        } else {
            return;
        }
        rows.add(new Object[] {
            personId, recordId, factKey, text, number, bool, time, sourceKey, observedAt
        });
    }

    /** 必填字串清理；空白時及早拒絕，避免資料庫留下無法查詢的空 key。 */
    private String required(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " 不可為空");
        }
        return value.trim();
    }

    /** 選填字串清理。 */
    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** 共用 JSON 序列化；失敗代表輸入不是可保存的資料，直接中止交易。 */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("資料無法轉成 JSON", exception);
        }
    }

    /** 產生穩定 payload hash，供匯入重跑判斷 unchanged。 */
    private String sha256(String value) {
        return sha256Static(value);
    }

    /** 靜態 SHA-256 實作，供 Email 抑制與 payload hash 共用。 */
    private static String sha256Static(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("執行環境缺少 SHA-256", exception);
        }
    }
}
