package world.springai.survey.audience;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 通用人物名單匯入：相同 Email 合併人物，但來源活動仍各自保存並可重跑。
 */
@Service
public class AudienceImportService {

    /** 與公開表單一致的寬鬆 Email 格式檢查。 */
    private static final Pattern EMAIL_RE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final AudiencePlatformService audience;
    private final SurveyResponseRepository legacyRepository;

    /** 注入新名單核心與舊相容資料層。 */
    public AudienceImportService(
            AudiencePlatformService audience,
            SurveyResponseRepository legacyRepository) {
        this.audience = audience;
        this.legacyRepository = legacyRepository;
    }

    /** 通用匯入列；attributes 讓 CSV/API 日後可帶入自訂人物資料。 */
    public record ImportPerson(String email, String name, Map<String, Object> attributes) {}

    /** 執行結果同時保留舊 imported/skipped 欄位，避免既有 Admin 頁面中斷。 */
    public record ImportResult(
            long batchId,
            int peopleCreated,
            int peopleMerged,
            int recordsCreated,
            int recordsUpdated,
            int unchanged,
            int invalid,
            int imported,
            int skipped) {}

    /**
     * 執行通用名單匯入。請求內重複 Email 只處理一次，避免同批產生誤導的重複結果。
     */
    @Transactional
    public ImportResult importPeople(String sourceKey, List<ImportPerson> people) {
        String source = sourceKey.trim();
        long batchId = audience.startImportBatch(source, "PEOPLE_LIST", null);
        int peopleCreated = 0;
        int peopleMerged = 0;
        int recordsCreated = 0;
        int recordsUpdated = 0;
        int unchanged = 0;
        int invalid = 0;
        int imported = 0;
        int legacySkipped = 0;
        Set<String> seen = new HashSet<>();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        for (ImportPerson input : people) {
            String normalized = AudiencePlatformService.normalizeEmail(input == null ? null : input.email());
            if (!EMAIL_RE.matcher(normalized).matches() || !seen.add(normalized)) {
                invalid++;
                audience.addImportItem(batchId, normalized.isEmpty() ? null : "person:" + normalized,
                    null, "INVALID", "INVALID_OR_DUPLICATE_EMAIL",
                    "Email 格式錯誤或在同一批重複", null);
                continue;
            }
            if (audience.isSuppressed(normalized)) {
                invalid++;
                audience.addImportItem(batchId, "person:" + normalized,
                    null, "INVALID", "EMAIL_SUPPRESSED",
                    "此 Email 已要求停止處理", null);
                continue;
            }

            Map<String, Object> attributes = input.attributes() == null
                ? Map.of()
                : new LinkedHashMap<>(input.attributes());
            AudiencePlatformService.PersonResult person =
                audience.mergePerson(normalized, input.name(), now);
            if (person.created()) {
                peopleCreated++;
            } else {
                peopleMerged++;
            }
            audience.upsertIdentity(person.personId(), source, "email", normalized, now);

            Map<String, Object> rawData = new LinkedHashMap<>();
            rawData.put("name", input.name());
            rawData.put("attributes", attributes);
            AudiencePlatformService.RecordResult record = audience.upsertRecord(
                person.personId(),
                source,
                "list_import",
                null,
                "person:" + normalized,
                now,
                rawData,
                Map.of("source", source));
            Map<String, Object> facts = new LinkedHashMap<>();
            attributes.forEach((key, value) -> facts.put("profile." + key, value));
            if (!"UNCHANGED".equals(record.status())) {
                audience.replaceFacts(person.personId(), record.recordId(), source, now, facts);
            }
            switch (record.status()) {
                case "CREATED" -> recordsCreated++;
                case "UPDATED" -> recordsUpdated++;
                default -> unchanged++;
            }
            audience.appendConsent(person.personId(), AudiencePlatformService.CONSENT_PENDING,
                source, null, Map.of("importBatchId", batchId), now);
            audience.addImportItem(batchId, "person:" + normalized, person.personId(),
                record.status(), null, null, record.payloadHash());

            // 相容期仍建立一筆待確認 survey_response，讓既有確認信、退訂與寄信流程繼續運作。
            if (!legacyRepository.existsByEmailIgnoreCase(normalized)) {
                SurveyResponse legacy = new SurveyResponse();
                legacy.setEmail(normalized);
                legacy.setName(input.name());
                legacy.setSource(source);
                legacy.setConsent(false);
                legacyRepository.save(legacy);
                imported++;
            } else {
                legacySkipped++;
            }
        }

        AudiencePlatformService.ImportSummary summary = new AudiencePlatformService.ImportSummary(
            peopleCreated, peopleMerged, recordsCreated, recordsUpdated, unchanged, invalid);
        audience.completeImportBatch(batchId, summary);
        return new ImportResult(
            batchId, peopleCreated, peopleMerged, recordsCreated, recordsUpdated,
            unchanged, invalid, imported, invalid + legacySkipped);
    }
}
