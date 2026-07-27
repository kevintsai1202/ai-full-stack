package world.springai.survey.audience;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 讀者安全批次操作：Preview 建立 10 分鐘固定快照，Execute 以冪等鍵逐筆執行。
 */
@Service
public class AudienceBulkOperationService {

    /** 同步批次上限；超過需改背景工作，本版明確拒絕而非讓 HTTP 長時間卡住。 */
    static final int MAX_SYNC_TARGETS = 500;
    /** 快照有效分鐘數。 */
    static final int SNAPSHOT_TTL_MINUTES = 10;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AudienceSearchService searchService;
    private final AudienceReaderOperations readerService;

    /** 注入快照資料層、JSON、搜尋與既有點數／VIP 交易服務。 */
    public AudienceBulkOperationService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            AudienceSearchService searchService,
            AudienceReaderOperations readerService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.searchService = searchService;
        this.readerService = readerService;
    }

    /** 預覽請求；personIds 與 filters 二擇一。 */
    public record PreviewRequest(
            String action,
            List<Long> personIds,
            AudienceSearchService.Filters filters,
            Integer creditDelta,
            String note,
            Integer vipDays) {}

    /** 預覽摘要；selectionToken 是固定目標集合，不是即時查詢 token。 */
    public record PreviewResult(
            UUID selectionToken,
            String action,
            int targeted,
            int eligible,
            int skipped,
            int willCreateReaderAccounts,
            long totalCreditDelta,
            OffsetDateTime expiresAt) {}

    /** 執行請求必須帶 idempotencyKey。 */
    public record ExecuteRequest(UUID selectionToken, String idempotencyKey) {}

    /** 執行結果；failedDetails 只含人物 ID 與錯誤，不回傳多餘個資。 */
    public record OperationResult(
            UUID operationId,
            String action,
            String status,
            int targeted,
            int succeeded,
            int failed,
            int skipped,
            List<Map<String, Object>> failedDetails) {}

    /** 建立固定目標快照並預覽資格；不變更任何點數或 VIP。 */
    @Transactional
    public PreviewResult preview(PreviewRequest request) {
        validatePreview(request);
        String action = request.action().trim().toUpperCase(java.util.Locale.ROOT);
        List<Long> personIds = request.personIds() != null && !request.personIds().isEmpty()
            ? searchService.existingPersonIds(request.personIds())
            : searchService.findPersonIds(request.filters());
        if (personIds.size() > MAX_SYNC_TARGETS) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "同步批次最多 500 人；目前條件超過上限，請縮小篩選範圍");
        }
        UUID snapshotId = UUID.randomUUID();
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC)
            .plusMinutes(SNAPSHOT_TTL_MINUTES);
        List<Target> targets = loadTargets(personIds, action);
        int eligible = (int) targets.stream().filter(Target::eligible).count();
        int willCreate = (int) targets.stream()
            .filter(Target::eligible)
            .filter(target -> !target.readerAccount())
            .count();
        int skipped = targets.size() - eligible;
        long totalCreditDelta = "GRANT_CREDITS".equals(action)
            ? (long) eligible * request.creditDelta()
            : 0L;
        jdbc.update("""
            INSERT INTO audience_selection_snapshot (
                id, action, request_data, targeted, eligible, skipped, expires_at
            ) VALUES (?, ?, ?::jsonb, ?, ?, ?, ?)
            """, snapshotId, action, json(request), targets.size(), eligible, skipped, expiresAt);
        for (Target target : targets) {
            jdbc.update("""
                INSERT INTO audience_selection_target (
                    snapshot_id, person_id, eligibility, reason
                ) VALUES (?, ?, ?, ?)
                """, snapshotId, target.personId(),
                target.eligible() ? "ELIGIBLE" : "SKIPPED", target.reason());
        }
        return new PreviewResult(
            snapshotId, action, targets.size(), eligible, skipped,
            willCreate, totalCreditDelta, expiresAt);
    }

    /**
     * 依快照逐筆執行。單筆失敗不回滾其他成功者，每筆仍由既有交易服務保護帳本。
     */
    public OperationResult execute(ExecuteRequest request) {
        if (request == null || request.selectionToken() == null
                || !StringUtils.hasText(request.idempotencyKey())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "selectionToken 與 idempotencyKey 為必填");
        }
        OperationResult existing = findByIdempotencyKey(request.idempotencyKey().trim());
        if (existing != null) {
            return existing;
        }
        Map<String, Object> snapshot = loadSnapshot(request.selectionToken());
        OffsetDateTime expiresAt = offsetDateTime(snapshot.get("expires_at"));
        if (!expiresAt.isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "選取快照已過期，請重新預覽");
        }
        String action = String.valueOf(snapshot.get("action"));
        PreviewRequest previewRequest = parseRequest(String.valueOf(snapshot.get("request_data")));
        UUID operationId = UUID.randomUUID();
        try {
            jdbc.update("""
                INSERT INTO audience_bulk_operation (
                    id, snapshot_id, idempotency_key, action, status, targeted, skipped
                ) VALUES (?, ?, ?, ?, 'RUNNING', ?, ?)
                """, operationId, request.selectionToken(), request.idempotencyKey().trim(),
                action, ((Number) snapshot.get("targeted")).intValue(),
                ((Number) snapshot.get("skipped")).intValue());
        } catch (DataIntegrityViolationException duplicate) {
            OperationResult raced = findByIdempotencyKey(request.idempotencyKey().trim());
            if (raced != null) {
                return raced;
            }
            throw duplicate;
        }

        List<Map<String, Object>> eligibleTargets = jdbc.queryForList("""
            SELECT t.person_id, p.email
              FROM audience_selection_target t
              JOIN audience_person p ON p.id = t.person_id
             WHERE t.snapshot_id = ? AND t.eligibility = 'ELIGIBLE'
             ORDER BY t.person_id
            """, request.selectionToken());
        int succeeded = 0;
        int failed = 0;
        List<Map<String, Object>> failures = new ArrayList<>();
        for (Map<String, Object> target : eligibleTargets) {
            long personId = ((Number) target.get("person_id")).longValue();
            String email = String.valueOf(target.get("email"));
            try {
                if ("GRANT_CREDITS".equals(action)) {
                    if (!readerService.grantCreditsForAudience(
                            email, previewRequest.creditDelta(), previewRequest.note().trim())) {
                        throw new IllegalStateException("讀者帳戶不存在或點數更新失敗");
                    }
                } else {
                    readerService.grantVipForAudience(
                        email, previewRequest.vipDays(), OffsetDateTime.now(ZoneOffset.UTC));
                }
                succeeded++;
            } catch (RuntimeException exception) {
                failed++;
                failures.add(Map.of(
                    "personId", personId,
                    "error", safeMessage(exception)));
            }
        }
        int skipped = ((Number) snapshot.get("skipped")).intValue();
        String status = failed == 0 ? "COMPLETED" : "COMPLETED_WITH_ERRORS";
        OperationResult result = new OperationResult(
            operationId,
            action,
            status,
            ((Number) snapshot.get("targeted")).intValue(),
            succeeded,
            failed,
            skipped,
            List.copyOf(failures));
        jdbc.update("""
            UPDATE audience_bulk_operation
               SET status = ?, succeeded = ?, failed = ?, skipped = ?,
                   result_data = ?::jsonb, completed_at = now()
             WHERE id = ?
            """, status, succeeded, failed, skipped, json(result), operationId);
        return result;
    }

    /** 依 operationId 查詢進度／結果。 */
    public OperationResult get(UUID operationId) {
        List<String> rows = jdbc.query("""
            SELECT result_data::text FROM audience_bulk_operation WHERE id = ?
            """, (rs, rowNum) -> rs.getString(1), operationId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到批次操作");
        }
        if ("{}".equals(rows.getFirst())) {
            Map<String, Object> running = jdbc.queryForMap("""
                SELECT id, action, status, targeted, succeeded, failed, skipped
                  FROM audience_bulk_operation WHERE id = ?
                """, operationId);
            return new OperationResult(
                operationId,
                String.valueOf(running.get("action")),
                String.valueOf(running.get("status")),
                ((Number) running.get("targeted")).intValue(),
                ((Number) running.get("succeeded")).intValue(),
                ((Number) running.get("failed")).intValue(),
                ((Number) running.get("skipped")).intValue(),
                List.of());
        }
        return parseResult(rows.getFirst());
    }

    /** 驗證操作參數。 */
    private void validatePreview(PreviewRequest request) {
        if (request == null || !StringUtils.hasText(request.action())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "action 為必填");
        }
        String action = request.action().trim().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("GRANT_CREDITS", "GRANT_VIP").contains(action)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支援的批次操作");
        }
        if ((request.personIds() == null || request.personIds().isEmpty())
                && request.filters() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請提供 personIds 或 filters");
        }
        if ("GRANT_CREDITS".equals(action)) {
            if (request.creditDelta() == null || request.creditDelta() == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "點數變動不得為 0");
            }
            if (!StringUtils.hasText(request.note())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "點數操作必須填寫原因");
            }
        }
        if ("GRANT_VIP".equals(action)
                && (request.vipDays() == null || request.vipDays() <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VIP 天數必須大於 0");
        }
    }

    /** 載入快照資格；點數只處理已有 Reader，VIP 可建立帳戶。 */
    private List<Target> loadTargets(List<Long> personIds, String action) {
        if (personIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(personIds.size(), "?"));
        return jdbc.query("""
            SELECT p.id, (r.id IS NOT NULL) AS reader_account
              FROM audience_person p
              LEFT JOIN reader r ON lower(r.email) = p.email_normalized
             WHERE p.id IN (
            """ + placeholders + ") ORDER BY p.id", (rs, rowNum) -> {
                boolean readerAccount = rs.getBoolean("reader_account");
                boolean eligible = "GRANT_VIP".equals(action) || readerAccount;
                return new Target(
                    rs.getLong("id"),
                    readerAccount,
                    eligible,
                    eligible ? null : "尚未建立 Reader 帳戶");
            }, personIds.toArray());
    }

    /** 載入未過期判斷需要的快照欄位。 */
    private Map<String, Object> loadSnapshot(UUID snapshotId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT action, request_data::text AS request_data,
                   targeted, eligible, skipped, expires_at
              FROM audience_selection_snapshot WHERE id = ?
            """, snapshotId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到選取快照");
        }
        return rows.getFirst();
    }

    /** 相同 idempotencyKey 直接回第一次結果。 */
    private OperationResult findByIdempotencyKey(String idempotencyKey) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id, status, result_data::text AS result_data
              FROM audience_bulk_operation WHERE idempotency_key = ?
            """, idempotencyKey);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.getFirst();
        if (!"{}".equals(row.get("result_data"))) {
            return parseResult(String.valueOf(row.get("result_data")));
        }
        return get((UUID) row.get("id"));
    }

    /** JSON 序列化。 */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("批次操作資料無法序列化", exception);
        }
    }

    /** 還原預覽參數。 */
    private PreviewRequest parseRequest(String value) {
        try {
            return objectMapper.readValue(value, PreviewRequest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("選取快照內容損壞", exception);
        }
    }

    /** 還原已完成操作結果。 */
    private OperationResult parseResult(String value) {
        try {
            return objectMapper.readValue(value, OperationResult.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("批次操作結果損壞", exception);
        }
    }

    /** 錯誤訊息不可為空，也不回傳完整 stack trace。 */
    private String safeMessage(RuntimeException exception) {
        return StringUtils.hasText(exception.getMessage())
            ? exception.getMessage()
            : exception.getClass().getSimpleName();
    }

    /** JdbcTemplate 的 Map 可能把 timestamptz 回成 Timestamp，統一轉成 UTC OffsetDateTime。 */
    private OffsetDateTime offsetDateTime(Object value) {
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        }
        throw new IllegalStateException("快照到期時間格式錯誤");
    }

    /** 快照內的單一資格判斷。 */
    private record Target(long personId, boolean readerAccount, boolean eligible, String reason) {}
}
