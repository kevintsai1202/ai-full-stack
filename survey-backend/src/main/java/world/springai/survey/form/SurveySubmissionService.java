package world.springai.survey.form;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.springai.survey.audience.AudiencePlatformService;
import world.springai.survey.audience.SurveyResponse;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Survey 相容寫入轉接器：舊端點保存 survey_response 後，同步寫入彈性人物與活動模型。
 */
@Service
public class SurveySubmissionService {

    /** 舊問卷固定對應的 schema key。 */
    static final String LEGACY_SCHEMA_KEY = "fullstack-course-interest@1";

    private final AudiencePlatformService audience;

    /** 注入彈性名單核心。 */
    public SurveySubmissionService(AudiencePlatformService audience) {
        this.audience = audience;
    }

    /**
     * 將一筆已保存的舊問卷轉成新模型；每個 survey_response ID 都是獨立活動。
     */
    @Transactional
    public void mirrorLegacySubmission(SurveyResponse response) {
        OffsetDateTime occurredAt = response.getCreatedAt() == null
            ? OffsetDateTime.now(ZoneOffset.UTC)
            : response.getCreatedAt();
        AudiencePlatformService.PersonResult person =
            audience.mergePerson(response.getEmail(), response.getName(), occurredAt);
        audience.upsertIdentity(
            person.personId(), response.getSource(), "email", person.emailNormalized(), occurredAt);

        Map<String, Object> answers = new LinkedHashMap<>();
        putIfPresent(answers, "role", response.getRole());
        putIfPresent(answers, "experience", response.getExperience());
        putIfPresent(answers, "frontendExperience", response.getFrontendExperience());
        putIfPresent(answers, "interest", response.getInterest());
        putIfPresent(answers, "budget", response.getBudget());
        if (response.getAnswers() != null) {
            answers.putAll(response.getAnswers());
        }
        Map<String, Object> rawData = new LinkedHashMap<>();
        rawData.put("answers", answers);
        rawData.put("utm", response.getUtm() == null ? Map.of() : response.getUtm());
        rawData.put("legacySurveyResponseId", response.getId());

        AudiencePlatformService.RecordResult record = audience.upsertRecord(
            person.personId(),
            response.getSource(),
            "survey_submission",
            LEGACY_SCHEMA_KEY,
            "survey_response:" + response.getId(),
            occurredAt,
            rawData,
            Map.of("formTitle", "AI 全端課程興趣問卷"));
        if (!"UNCHANGED".equals(record.status())) {
            audience.replaceFacts(
                person.personId(), record.recordId(), response.getSource(), occurredAt, toFacts(answers));
        }
        audience.appendConsent(
            person.personId(),
            response.isUnsubscribed()
                ? AudiencePlatformService.CONSENT_UNSUBSCRIBED
                : response.isConsent()
                    ? AudiencePlatformService.CONSENT_CONFIRMED
                    : AudiencePlatformService.CONSENT_PENDING,
            response.getSource(),
            null,
            Map.of("surveyResponseId", response.getId()),
            occurredAt);
    }

    /** 將表單答案映射為可搜尋 Fact；未知欄位也自動使用 survey.{fieldKey}。 */
    private Map<String, Object> toFacts(Map<String, Object> answers) {
        Map<String, Object> facts = new LinkedHashMap<>();
        answers.forEach((key, value) -> {
            if (key == null || key.startsWith("_")) {
                return;
            }
            String factKey = switch (key) {
                case "role" -> "profile.role";
                case "experience" -> "profile.backend_experience";
                case "frontendExperience" -> "profile.frontend_experience";
                case "interest" -> "profile.interest";
                case "budget" -> "profile.budget";
                default -> "survey." + key;
            };
            facts.put(factKey, value);
        });
        return facts;
    }

    /** 非空值才放入答案集合，避免把未填寫誤當成統計選項。 */
    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
