package world.springai.survey.audience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exam 跨系統同步：先以唯讀 API 取得資料，再以 external ID 與 payload hash 冪等寫入。
 */
@Service
public class ExamAudienceSyncService {

    private static final Logger log = LoggerFactory.getLogger(ExamAudienceSyncService.class);
    /** 單頁上限與 Exam 匯出端一致。 */
    private static final int PAGE_SIZE = 500;
    /** 防止上游 cursor 壞掉時無限迴圈。 */
    private static final int MAX_PAGES = 100;

    private final RestClient restClient;
    private final AudiencePlatformService audience;
    private final JdbcTemplate jdbc;
    private final String baseUrl;
    private final String token;
    private final boolean scheduledEnabled;

    /** 注入 HTTP client、名單核心、cursor 資料層與部署設定。 */
    public ExamAudienceSyncService(
            RestClient.Builder restClientBuilder,
            AudiencePlatformService audience,
            JdbcTemplate jdbc,
            @Value("${app.integrations.exam.base-url:}") String baseUrl,
            @Value("${app.integrations.exam.token:}") String token,
            @Value("${app.integrations.exam.scheduled-enabled:false}") boolean scheduledEnabled) {
        this.restClient = restClientBuilder.build();
        this.audience = audience;
        this.jdbc = jdbc;
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.token = token;
        this.scheduledEnabled = scheduledEnabled;
    }

    /** Exam 匯出契約；欄位名稱與 exam_system_new 的整合 DTO 完全一致。 */
    public record ExportResponse(
            String nextCursor,
            List<Profile> profiles,
            List<Attempt> attempts) {}

    /** Exam 人物主檔。 */
    public record Profile(
            String externalProfileId,
            String email,
            String name,
            OffsetDateTime createdAt,
            String acquisitionSource,
            OffsetDateTime firstConsentAt,
            String consentVersion) {}

    /** Exam 單次測驗活動。 */
    public record Attempt(
            String externalAttemptId,
            String externalProfileId,
            String externalExamId,
            String examTitle,
            OffsetDateTime joinedAt,
            int totalScore,
            int questionCount,
            int answeredCount,
            int correctCount,
            double scoreRate,
            Map<String, String> surveyData,
            List<AttemptAnswer> answers) {}

    /** Exam 逐題結果。 */
    public record AttemptAnswer(
            String questionId,
            String selectedOptionId,
            boolean correct,
            OffsetDateTime answeredAt) {}

    /** 預覽結果不寫資料庫，但會依現況區分新增人物與合併人物。 */
    public record Preview(
            String currentCursor,
            String nextCursor,
            int peopleCreated,
            int peopleMerged,
            int attempts,
            int invalid,
            List<String> errors) {}

    /** 同步執行結果。 */
    public record SyncResult(
            long batchId,
            String previousCursor,
            String nextCursor,
            AudiencePlatformService.ImportSummary summary) {}

    /** 下載從目前 cursor 開始的全部頁面並計算預覽，不寫入名單資料。 */
    public Preview preview() {
        String currentCursor = currentCursor();
        List<ExportResponse> pages = fetchAll(currentCursor);
        Map<String, Profile> profiles = collectProfiles(pages);
        int created = 0;
        int merged = 0;
        int invalid = 0;
        List<String> errors = new ArrayList<>();
        for (Profile profile : profiles.values()) {
            String email = AudiencePlatformService.normalizeEmail(profile.email());
            if (!StringUtils.hasText(email)) {
                invalid++;
                errors.add("profile " + profile.externalProfileId() + " 缺少 Email");
            } else if (personExists(email)) {
                merged++;
            } else {
                created++;
            }
        }
        int attempts = pages.stream()
            .mapToInt(page -> safe(page.attempts()).size())
            .sum();
        return new Preview(
            currentCursor, lastCursor(currentCursor, pages), created, merged,
            attempts, invalid, List.copyOf(errors));
    }

    /** 執行 Exam 增量同步並保存 batch/item/cursor；相同資料重跑不會重複建立活動。 */
    public SyncResult sync() {
        String previousCursor = currentCursor();
        List<ExportResponse> pages = fetchAll(previousCursor);
        String nextCursor = lastCursor(previousCursor, pages);
        long batchId = audience.startImportBatch("exam", "EXAM_API", previousCursor);
        int peopleCreated = 0;
        int peopleMerged = 0;
        int recordsCreated = 0;
        int recordsUpdated = 0;
        int unchanged = 0;
        int invalid = 0;
        try {
            Map<String, Profile> profiles = collectProfiles(pages);
            Map<String, AudiencePlatformService.PersonResult> people = new HashMap<>();
            for (Profile profile : profiles.values()) {
                String email = AudiencePlatformService.normalizeEmail(profile.email());
                if (!StringUtils.hasText(email)) {
                    invalid++;
                    audience.addImportItem(
                        batchId, "profile:" + profile.externalProfileId(), null,
                        "INVALID", "MISSING_EMAIL", "Exam profile 缺少 Email", null);
                    continue;
                }
                if (audience.isSuppressed(email)) {
                    invalid++;
                    audience.addImportItem(
                        batchId, "profile:" + profile.externalProfileId(), null,
                        "INVALID", "EMAIL_SUPPRESSED", "此 Email 已要求停止處理", null);
                    continue;
                }
                AudiencePlatformService.PersonResult person =
                    audience.mergePerson(email, profile.name(), profile.createdAt());
                people.put(profile.externalProfileId(), person);
                if (person.created()) {
                    peopleCreated++;
                } else {
                    peopleMerged++;
                }
                audience.upsertIdentity(
                    person.personId(), "exam", "student_profile",
                    profile.externalProfileId(), profile.createdAt());
                String consentStatus =
                    profile.firstConsentAt() != null && StringUtils.hasText(profile.consentVersion())
                        ? AudiencePlatformService.CONSENT_CONFIRMED
                        : AudiencePlatformService.CONSENT_PENDING;
                audience.appendConsent(
                    person.personId(), consentStatus, "exam", profile.consentVersion(),
                    Map.of("externalProfileId", profile.externalProfileId()),
                    profile.firstConsentAt() == null ? profile.createdAt() : profile.firstConsentAt());
            }

            for (ExportResponse page : pages) {
                for (Attempt attempt : safe(page.attempts())) {
                    AudiencePlatformService.PersonResult person =
                        people.get(attempt.externalProfileId());
                    if (person == null) {
                        invalid++;
                        audience.addImportItem(
                            batchId, attempt.externalAttemptId(), null,
                            "INVALID", "PROFILE_NOT_FOUND",
                            "測驗活動找不到有效人物", null);
                        continue;
                    }
                    Map<String, Object> raw = new LinkedHashMap<>();
                    raw.put("surveyData", attempt.surveyData() == null ? Map.of() : attempt.surveyData());
                    raw.put("answers", safe(attempt.answers()));
                    raw.put("totalScore", attempt.totalScore());
                    raw.put("questionCount", attempt.questionCount());
                    raw.put("answeredCount", attempt.answeredCount());
                    raw.put("correctCount", attempt.correctCount());
                    raw.put("scoreRate", attempt.scoreRate());
                    Map<String, Object> summary = Map.of(
                        "exam", Map.of(
                            "id", attempt.externalExamId(),
                            "title", attempt.examTitle()),
                        "score", attempt.totalScore(),
                        "correctCount", attempt.correctCount(),
                        "questionCount", attempt.questionCount());
                    AudiencePlatformService.RecordResult record = audience.upsertRecord(
                        person.personId(), "exam", "exam_attempt",
                        "exam:" + attempt.externalExamId(),
                        attempt.externalAttemptId(), attempt.joinedAt(), raw, summary);
                    if (!"UNCHANGED".equals(record.status())) {
                        audience.replaceFacts(
                            person.personId(), record.recordId(), "exam", attempt.joinedAt(),
                            examFacts(attempt));
                    }
                    switch (record.status()) {
                        case "CREATED" -> recordsCreated++;
                        case "UPDATED" -> recordsUpdated++;
                        default -> unchanged++;
                    }
                    audience.addImportItem(
                        batchId, attempt.externalAttemptId(), person.personId(),
                        record.status(), null, null, record.payloadHash());
                }
            }
            AudiencePlatformService.ImportSummary summary =
                new AudiencePlatformService.ImportSummary(
                    peopleCreated, peopleMerged, recordsCreated,
                    recordsUpdated, unchanged, invalid);
            audience.completeImportBatch(batchId, summary);
            saveCursor(nextCursor, batchId, "COMPLETED");
            return new SyncResult(batchId, previousCursor, nextCursor, summary);
        } catch (RuntimeException exception) {
            audience.failImportBatch(batchId, exception.getMessage());
            saveCursor(previousCursor, batchId, "FAILED");
            throw exception;
        }
    }

    /** 排程增量同步；預設關閉，需部署環境明確啟用。 */
    @Scheduled(cron = "${app.integrations.exam.sync-cron:0 15 * * * *}")
    public void scheduledSync() {
        if (!scheduledEnabled) {
            return;
        }
        try {
            SyncResult result = sync();
            log.info("Exam 名單自動同步完成：batch={} cursor={}",
                result.batchId(), result.nextCursor());
        } catch (RuntimeException exception) {
            log.error("Exam 名單自動同步失敗", exception);
        }
    }

    /** 下載所有增量頁面，若上游 cursor 未前進則中止並回報。 */
    private List<ExportResponse> fetchAll(String initialCursor) {
        verifyConfigured();
        List<ExportResponse> pages = new ArrayList<>();
        String cursor = initialCursor;
        for (int pageNumber = 0; pageNumber < MAX_PAGES; pageNumber++) {
            ExportResponse page = restClient.get()
                .uri(baseUrl + "/api/integrations/audience-export?since={since}&limit={limit}",
                    cursor == null ? "" : cursor, PAGE_SIZE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(ExportResponse.class);
            if (page == null) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Exam 匯出 API 回傳空內容");
            }
            pages.add(page);
            if (safe(page.attempts()).isEmpty()) {
                return pages;
            }
            if (!StringUtils.hasText(page.nextCursor())
                    || java.util.Objects.equals(cursor, page.nextCursor())) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Exam 匯出 cursor 未前進");
            }
            cursor = page.nextCursor();
            if (safe(page.attempts()).size() < PAGE_SIZE) {
                return pages;
            }
        }
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY, "Exam 匯出超過單次同步安全頁數");
    }

    /** 將人物資料依 externalProfileId 去重。 */
    private Map<String, Profile> collectProfiles(List<ExportResponse> pages) {
        Map<String, Profile> profiles = new LinkedHashMap<>();
        pages.forEach(page -> safe(page.profiles())
            .forEach(profile -> profiles.put(profile.externalProfileId(), profile)));
        return profiles;
    }

    /** Exam 摘要與 survey_data 轉成跨來源可搜尋 Fact。 */
    private Map<String, Object> examFacts(Attempt attempt) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("exam.id", attempt.externalExamId());
        facts.put("exam.title", attempt.examTitle());
        facts.put("exam.score", attempt.totalScore());
        facts.put("exam.question_count", attempt.questionCount());
        facts.put("exam.answered_count", attempt.answeredCount());
        facts.put("exam.correct_count", attempt.correctCount());
        facts.put("exam.score_rate", attempt.scoreRate());
        if (attempt.surveyData() != null) {
            attempt.surveyData().forEach((key, value) ->
                facts.put("exam.survey." + key, value));
        }
        return facts;
    }

    /** 讀取目前 Exam cursor；第一次同步回空字串。 */
    private String currentCursor() {
        List<String> cursors = jdbc.query("""
            SELECT cursor_value FROM integration_sync_cursor WHERE source_key = 'exam'
            """, (rs, rowNum) -> rs.getString("cursor_value"));
        return cursors.isEmpty() || cursors.getFirst() == null ? "" : cursors.getFirst();
    }

    /** 保存同步 cursor 與最後批次；失敗時仍保留舊 cursor，方便安全重跑。 */
    private void saveCursor(String cursor, long batchId, String status) {
        jdbc.update("""
            INSERT INTO integration_sync_cursor (
                source_key, cursor_value, last_status, last_batch_id, last_synced_at
            ) VALUES ('exam', ?, ?, ?, now())
            ON CONFLICT (source_key) DO UPDATE SET
                cursor_value = EXCLUDED.cursor_value,
                last_status = EXCLUDED.last_status,
                last_batch_id = EXCLUDED.last_batch_id,
                last_synced_at = EXCLUDED.last_synced_at
            """, cursor, status, batchId);
    }

    /** 預覽使用的存在判斷只查正規化 Email 索引。 */
    private boolean personExists(String emailNormalized) {
        Integer count = jdbc.queryForObject("""
            SELECT count(*) FROM audience_person WHERE email_normalized = ?
            """, Integer.class, emailNormalized);
        return count != null && count > 0;
    }

    /** 取得最後一頁 cursor；沒有資料時保留原 cursor。 */
    private String lastCursor(String initial, List<ExportResponse> pages) {
        return pages.stream()
            .map(ExportResponse::nextCursor)
            .filter(StringUtils::hasText)
            .reduce((first, second) -> second)
            .orElse(initial);
    }

    /** null 集合一律轉空集合，避免上游省略欄位造成整批失敗。 */
    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    /** 整合 URL 與 token 必須同時設定，否則 Admin 得到可理解的 503。 */
    private void verifyConfigured() {
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(token)) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Exam 同步尚未設定 URL 或 token");
        }
    }

    /** 移除 URL 尾端斜線，避免組出雙斜線路徑。 */
    private static String stripTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("/+$", "");
    }
}
