package world.springai.survey.form;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.audience.AudiencePlatformService;
import world.springai.survey.audience.SurveyResponse;
import world.springai.survey.audience.SurveyResponseRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 動態表單 schema、提交與統計服務；Admin、CSV 與公開統計共用同一份欄位描述。
 */
@Service
public class FormSchemaService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AudiencePlatformService audience;
    private final SurveyResponseRepository legacyRepository;

    /** 注入 SQL、JSON、新名單核心與舊相容資料層。 */
    public FormSchemaService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            AudiencePlatformService audience,
            SurveyResponseRepository legacyRepository) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.audience = audience;
        this.legacyRepository = legacyRepository;
    }

    /** 欄位 schema；前端直接依此產生輸入元件、統計與表格欄位。 */
    public record FieldDefinition(
            long id,
            String key,
            String label,
            String type,
            boolean required,
            List<Object> options,
            boolean analyticsEnabled,
            String analyticsView,
            boolean filterable,
            boolean sensitive,
            boolean publicAnalytics,
            int displayOrder,
            String factKey) {}

    /** 表單版本 schema。emailVoteFieldKey 為此版本信中一鍵題所綁定的欄位 key，未設為 null。 */
    public record FormDefinition(
            long id,
            String key,
            int version,
            String title,
            String status,
            boolean publicAnalyticsEnabled,
            String emailVoteFieldKey,
            List<FieldDefinition> fields) {}

    /** 信中一鍵題完整描述；options 為選項文字（依序，optionIndex 對映） */
    public record EmailVoteQuestion(String formKey, String title, String fieldKey,
                                    String label, List<String> options) {}

    /** 動態表單提交要求；Email 與姓名維持人物主檔，不混入一般答案。 */
    public record SubmissionRequest(
            String email,
            String name,
            Map<String, Object> answers,
            Map<String, String> utm,
            boolean consent,
            String website) {}

    /** 提交成功結果，recordId 可供客服追查但不回傳內部人物 ID。 */
    public record SubmissionResult(String submissionId, String schemaKey) {}

    /** 建立新版本時可覆寫標題；空白時沿用上一版。 */
    public record VersionRequest(String title) {}

    /** Admin 欄位設定要求；所有會影響統計與篩選的描述都由同一份 schema 保存。 */
    public record FieldRequest(
            String label,
            String type,
            boolean required,
            List<Object> options,
            boolean analyticsEnabled,
            String analyticsView,
            boolean filterable,
            boolean sensitive,
            boolean publicAnalytics,
            int displayOrder,
            String factKey) {}

    /** 列出全部表單版本，供 Admin 下拉選單與簡易欄位設定使用。 */
    public List<FormDefinition> listDefinitions() {
        return jdbc.query("""
            SELECT id, form_key, version, title, status, public_analytics_enabled,
                   email_vote_field_key
              FROM form_definition
             ORDER BY form_key, version DESC
            """, (rs, rowNum) -> definition(
                rs.getLong("id"),
                rs.getString("form_key"),
                rs.getInt("version"),
                rs.getString("title"),
                rs.getString("status"),
                rs.getBoolean("public_analytics_enabled"),
                rs.getString("email_vote_field_key")));
    }

    /** 取得指定版本；version 為 null 時取最新發布版本。 */
    public FormDefinition getDefinition(String formKey, Integer version) {
        List<Map<String, Object>> rows = version == null
            ? jdbc.queryForList("""
                SELECT id, form_key, version, title, status, public_analytics_enabled,
                       email_vote_field_key
                  FROM form_definition
                 WHERE form_key = ? AND status = 'PUBLISHED'
                 ORDER BY version DESC
                 LIMIT 1
                """, formKey)
            : jdbc.queryForList("""
                SELECT id, form_key, version, title, status, public_analytics_enabled,
                       email_vote_field_key
                  FROM form_definition
                 WHERE form_key = ? AND version = ?
                """, formKey, version);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到指定表單版本");
        }
        Map<String, Object> row = rows.getFirst();
        return definition(
            ((Number) row.get("id")).longValue(),
            String.valueOf(row.get("form_key")),
            ((Number) row.get("version")).intValue(),
            String.valueOf(row.get("title")),
            String.valueOf(row.get("status")),
            Boolean.TRUE.equals(row.get("public_analytics_enabled")),
            (String) row.get("email_vote_field_key"));
    }

    /** 建立全新問卷：v1 DRAFT 空殼；formKey 限 [a-z0-9-]{3,50} 且不可重複。 */
    @Transactional
    public FormDefinition createForm(String formKey, String title) {
        if (formKey == null || !formKey.matches("[a-z0-9-]{3,50}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "表單代號限小寫英數與連字號（3–50 字）");
        }
        if (!StringUtils.hasText(title)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "表單標題為必填");
        }
        Integer exists = jdbc.queryForObject(
            "SELECT count(*) FROM form_definition WHERE form_key = ?", Integer.class, formKey);
        if (exists != null && exists > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "表單代號已存在");
        }
        jdbc.update("""
            INSERT INTO form_definition (form_key, version, title, status, public_analytics_enabled)
            VALUES (?, 1, ?, 'DRAFT', FALSE)
            """, formKey, title.trim());
        return getDefinition(formKey, 1);
    }

    /** 從最新版本複製一份 DRAFT，避免修改已發布版本後讓歷史統計失去原始定義。 */
    @Transactional
    public FormDefinition createVersion(String formKey, VersionRequest request) {
        FormDefinition latest = listDefinitions().stream()
            .filter(form -> form.key().equals(formKey))
            .max(Comparator.comparingInt(FormDefinition::version))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到指定表單"));
        int nextVersion = latest.version() + 1;
        String title = request != null && StringUtils.hasText(request.title())
            ? request.title().trim()
            : latest.title();
        Long definitionId = jdbc.queryForObject("""
            INSERT INTO form_definition (
                form_key, version, title, status, public_analytics_enabled
            ) VALUES (?, ?, ?, 'DRAFT', ?)
            RETURNING id
            """, Long.class, formKey, nextVersion, title, latest.publicAnalyticsEnabled());
        jdbc.update("""
            INSERT INTO form_field (
                form_definition_id, field_key, label, field_type, required, options,
                analytics_enabled, analytics_view, filterable, sensitive,
                public_analytics, display_order, fact_key
            )
            SELECT ?, field_key, label, field_type, required, options,
                   analytics_enabled, analytics_view, filterable, sensitive,
                   public_analytics, display_order, fact_key
              FROM form_field
             WHERE form_definition_id = ?
            """, definitionId, latest.id());
        return getDefinition(formKey, nextVersion);
    }

    /** 在 DRAFT 版本新增欄位；fieldKey 會成為提交 JSON 與 Fact 的穩定 key。 */
    @Transactional
    public FormDefinition addField(
            String formKey,
            int version,
            String fieldKey,
            FieldRequest request) {
        FormDefinition form = requireDraft(formKey, version);
        validateField(fieldKey, request);
        jdbc.update("""
            INSERT INTO form_field (
                form_definition_id, field_key, label, field_type, required, options,
                analytics_enabled, analytics_view, filterable, sensitive,
                public_analytics, display_order, fact_key
            ) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, ?, ?)
            """,
            form.id(), fieldKey.trim(), request.label().trim(), request.type(),
            request.required(), json(request.options()), request.analyticsEnabled(),
            request.analyticsView(), request.filterable(), request.sensitive(),
            request.publicAnalytics(), request.displayOrder(), blankToNull(request.factKey()));
        return getDefinition(formKey, version);
    }

    /** 更新 DRAFT 欄位描述；不允許改 fieldKey，避免歷史答案與 Fact 對不到。 */
    @Transactional
    public FormDefinition updateField(
            String formKey,
            int version,
            String fieldKey,
            FieldRequest request) {
        FormDefinition form = requireDraft(formKey, version);
        validateField(fieldKey, request);
        int updated = jdbc.update("""
            UPDATE form_field
               SET label = ?, field_type = ?, required = ?, options = CAST(? AS jsonb),
                   analytics_enabled = ?, analytics_view = ?, filterable = ?,
                   sensitive = ?, public_analytics = ?, display_order = ?, fact_key = ?
             WHERE form_definition_id = ? AND field_key = ?
            """,
            request.label().trim(), request.type(), request.required(), json(request.options()),
            request.analyticsEnabled(), request.analyticsView(), request.filterable(),
            request.sensitive(), request.publicAnalytics(), request.displayOrder(),
            blankToNull(request.factKey()), form.id(), fieldKey);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到指定欄位");
        }
        return getDefinition(formKey, version);
    }

    /** 發布 DRAFT 並封存同 key 的舊發布版；新提交從此使用新版本。 */
    @Transactional
    public FormDefinition publish(String formKey, int version) {
        FormDefinition form = requireDraft(formKey, version);
        jdbc.update("""
            UPDATE form_definition
               SET status = 'ARCHIVED', updated_at = now()
             WHERE form_key = ? AND status = 'PUBLISHED'
            """, formKey);
        jdbc.update("""
            UPDATE form_definition
               SET status = 'PUBLISHED', updated_at = now()
             WHERE id = ?
            """, form.id());
        return getDefinition(formKey, version);
    }

    /** 取得指定問卷最新已發布版本所綁定的信中一鍵題；未設定、欄位已不是單選或找不到已發布版本都回 empty。 */
    public Optional<EmailVoteQuestion> emailVoteQuestion(String formKey) {
        FormDefinition form;
        try {
            form = getDefinition(formKey, null);
        } catch (ResponseStatusException notFound) {
            return Optional.empty();
        }
        return toEmailVoteQuestion(form);
    }

    /**
     * 指定或清除某版本的信中一鍵題欄位；fieldKey 為 null 表示清除。
     * 指定的欄位必須存在於該版本且型別為 select，否則以 400 拒絕。
     */
    @Transactional
    public void updateEmailVoteField(String formKey, int version, String fieldKey) {
        FormDefinition form = getDefinition(formKey, version);
        if (fieldKey != null) {
            boolean validSelectField = form.fields().stream()
                .anyMatch(field -> field.key().equals(fieldKey) && "select".equals(field.type()));
            if (!validSelectField) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "指定欄位不存在或非單選（select）欄位");
            }
        }
        jdbc.update("""
            UPDATE form_definition
               SET email_vote_field_key = ?, updated_at = now()
             WHERE id = ?
            """, fieldKey, form.id());
    }

    /** 列出全部已發布且已設定信中一鍵題的問卷，供電子報編輯器插入選單使用。 */
    public List<EmailVoteQuestion> listEmbeddable() {
        return listDefinitions().stream()
            .filter(form -> "PUBLISHED".equals(form.status()))
            .flatMap(form -> toEmailVoteQuestion(form).stream())
            .toList();
    }

    /** 依表單版本與其 emailVoteFieldKey 組出信中一鍵題描述；條件不符回 empty。 */
    private Optional<EmailVoteQuestion> toEmailVoteQuestion(FormDefinition form) {
        if (!StringUtils.hasText(form.emailVoteFieldKey())) {
            return Optional.empty();
        }
        return form.fields().stream()
            .filter(field -> field.key().equals(form.emailVoteFieldKey())
                && "select".equals(field.type()))
            .findFirst()
            .map(field -> new EmailVoteQuestion(
                form.key(),
                form.title(),
                field.key(),
                field.label(),
                field.options().stream().map(String::valueOf).toList()));
    }

    /**
     * 提交任意 schema 表單；新欄位只需 form_field 設定，不需 Java Entity 或 migration。
     */
    @Transactional
    public SubmissionResult submit(String formKey, SubmissionRequest request) {
        FormDefinition form = getDefinition(formKey, null);
        Map<String, Object> answers = request.answers() == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(request.answers());
        validateAnswers(form, answers);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AudiencePlatformService.PersonResult person;
        try {
            person = audience.mergePerson(request.email(), request.name(), now);
        } catch (AudiencePlatformService.SuppressedEmailException exception) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "此 Email 已要求停止處理，如需重新訂閱請聯絡管理員");
        }
        audience.upsertIdentity(
            person.personId(), "survey_form", "email", person.emailNormalized(), now);
        String submissionId = UUID.randomUUID().toString();
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("answers", answers);
        raw.put("utm", request.utm() == null ? Map.of() : request.utm());
        AudiencePlatformService.RecordResult record = audience.upsertRecord(
            person.personId(),
            "survey_form",
            "survey_submission",
            schemaKey(form),
            submissionId,
            now,
            raw,
            Map.of("formTitle", form.title(), "version", form.version()));
        audience.replaceFacts(
            person.personId(), record.recordId(), "survey_form", now, facts(form, answers));
        audience.appendConsent(
            person.personId(),
            request.consent()
                ? AudiencePlatformService.CONSENT_CONFIRMED
                : AudiencePlatformService.CONSENT_PENDING,
            "survey_form",
            null,
            Map.of("submissionId", submissionId, "schemaKey", schemaKey(form)),
            now);

        // 相容期保留最小舊列，讓確認／退訂、讀者登入與既有寄信名單仍可運作。
        SurveyResponse legacy = new SurveyResponse();
        legacy.setEmail(request.email().trim());
        legacy.setName(request.name());
        legacy.setAnswers(answers);
        legacy.setUtm(request.utm());
        legacy.setSource("survey_form");
        legacy.setConsent(request.consent());
        legacyRepository.save(legacy);
        return new SubmissionResult(submissionId, schemaKey(form));
    }

    /**
     * 動態分析指定表單；所有 renderer 都只依 dimensions，不認識 role 或 interest。
     */
    public Map<String, Object> analytics(
            String formKey,
            Integer version,
            boolean allVersions,
            OffsetDateTime from,
            OffsetDateTime to,
            String source,
            boolean publicOnly) {
        FormDefinition selected = getDefinition(formKey, version);
        List<FormDefinition> definitions = allVersions
            ? listDefinitions().stream().filter(form -> form.key().equals(formKey)).toList()
            : List.of(selected);
        if (publicOnly && !selected.publicAnalyticsEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "此表單未開放公開統計");
        }
        List<String> schemaKeys = definitions.stream().map(this::schemaKey).toList();
        List<Map<String, Object>> records = loadRecords(schemaKeys, from, to, source);

        Map<String, FieldDefinition> fields = new LinkedHashMap<>();
        definitions.forEach(form -> form.fields().forEach(field -> fields.putIfAbsent(field.key(), field)));
        List<Map<String, Object>> dimensions = new ArrayList<>();
        for (FieldDefinition field : fields.values()) {
            if (!field.analyticsEnabled() || (publicOnly && (!field.publicAnalytics() || field.sensitive()))) {
                continue;
            }
            dimensions.add(dimension(field, records));
        }
        long uniquePeople = records.stream()
            .map(record -> record.get("person_id"))
            .distinct()
            .count();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("form", Map.of(
            "key", selected.key(),
            "title", selected.title(),
            "version", selected.version(),
            "allVersions", allVersions));
        response.put("summary", Map.of(
            "submissions", records.size(),
            "uniquePeople", uniquePeople,
            "completionRate", completionRate(records, fields.values())));
        response.put("dimensions", dimensions);
        return response;
    }

    /** 匯出動態原始資料；欄位答案保留 JSON 型別，Email／姓名從人物主檔即時取得。 */
    public List<Map<String, Object>> rawRecords(
            String formKey,
            Integer version,
            boolean allVersions,
            OffsetDateTime from,
            OffsetDateTime to,
            String source) {
        FormDefinition selected = getDefinition(formKey, version);
        List<FormDefinition> definitions = allVersions
            ? listDefinitions().stream().filter(form -> form.key().equals(formKey)).toList()
            : List.of(selected);
        List<Map<String, Object>> records = loadRecords(
            definitions.stream().map(this::schemaKey).toList(), from, to, source);
        Map<Long, Map<String, Object>> people = new LinkedHashMap<>();
        if (!records.isEmpty()) {
            List<Long> ids = records.stream()
                .map(record -> ((Number) record.get("person_id")).longValue())
                .distinct()
                .toList();
            String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
            jdbc.query("""
                SELECT id, email, display_name
                  FROM audience_person
                 WHERE id IN (
                """ + placeholders + ")",
                rs -> {
                    people.put(rs.getLong("id"), Map.of(
                        "email", rs.getString("email"),
                        "name", rs.getString("display_name") == null ? "" : rs.getString("display_name")));
                },
                ids.toArray());
        }
        return records.stream().map(record -> {
            long personId = ((Number) record.get("person_id")).longValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("recordId", record.get("record_id"));
            row.put("schemaKey", record.get("schema_key"));
            row.put("source", record.get("source_key"));
            row.put("occurredAt", record.get("occurred_at"));
            row.putAll(people.getOrDefault(personId, Map.of("email", "", "name", "")));
            row.put("answers", record.get("answers"));
            return row;
        }).toList();
    }

    /** 建立表單版本物件並讀取其欄位。 */
    private FormDefinition definition(
            long id,
            String key,
            int version,
            String title,
            String status,
            boolean publicAnalyticsEnabled,
            String emailVoteFieldKey) {
        return new FormDefinition(
            id, key, version, title, status, publicAnalyticsEnabled, emailVoteFieldKey, fields(id));
    }

    /** 依顯示順序讀取欄位定義。 */
    private List<FieldDefinition> fields(long formDefinitionId) {
        return jdbc.query("""
            SELECT id, field_key, label, field_type, required, options,
                   analytics_enabled, analytics_view, filterable, sensitive,
                   public_analytics, display_order, fact_key
              FROM form_field
             WHERE form_definition_id = ?
             ORDER BY display_order, id
            """, (rs, rowNum) -> new FieldDefinition(
                rs.getLong("id"),
                rs.getString("field_key"),
                rs.getString("label"),
                rs.getString("field_type"),
                rs.getBoolean("required"),
                parseList(rs.getString("options")),
                rs.getBoolean("analytics_enabled"),
                rs.getString("analytics_view"),
                rs.getBoolean("filterable"),
                rs.getBoolean("sensitive"),
                rs.getBoolean("public_analytics"),
                rs.getInt("display_order"),
                rs.getString("fact_key")), formDefinitionId);
    }

    /**
     * 驗證必填與未知欄位，避免拼錯 key 後資料靜默消失在統計之外。
     *
     * <p>套件層級存取（非 private）：{@link NewsletterSubmissionService} 電子報通道提交
     * 需重用同一份驗證規則，避免兩處各自維護一套「必填／未知欄位」判斷而逐漸失準。</p>
     */
    void validateAnswers(FormDefinition form, Map<String, Object> answers) {
        Map<String, FieldDefinition> allowed = new LinkedHashMap<>();
        form.fields().forEach(field -> allowed.put(field.key(), field));
        List<String> unknown = answers.keySet().stream()
            .filter(key -> !allowed.containsKey(key))
            .toList();
        if (!unknown.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "包含未定義欄位：" + String.join(", ", unknown));
        }
        for (FieldDefinition field : form.fields()) {
            Object value = answers.get(field.key());
            if (field.required() && isMissing(value)) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "請填寫「" + field.label() + "」");
            }
        }
    }

    /** 依欄位 factKey 建立 typed Fact，沒有設定時使用 survey.{fieldKey}。 */
    private Map<String, Object> facts(FormDefinition form, Map<String, Object> answers) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (FieldDefinition field : form.fields()) {
            Object value = answers.get(field.key());
            if (!isMissing(value)) {
                result.put(
                    StringUtils.hasText(field.factKey()) ? field.factKey() : "survey." + field.key(),
                    value);
            }
        }
        return result;
    }

    /** 查詢活動原始答案；日期與來源篩選在資料庫執行，不把全表送到前端。 */
    private List<Map<String, Object>> loadRecords(
            List<String> schemaKeys,
            OffsetDateTime from,
            OffsetDateTime to,
            String source) {
        if (schemaKeys.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(schemaKeys.size(), "?"));
        StringBuilder sql = new StringBuilder("""
            SELECT id AS record_id, person_id, schema_key, source_key, occurred_at, raw_data
              FROM audience_record
             WHERE record_type = 'survey_submission'
               AND schema_key IN (
            """).append(placeholders).append(")");
        List<Object> params = new ArrayList<>(schemaKeys);
        if (from != null) {
            sql.append(" AND occurred_at >= ?");
            params.add(from);
        }
        if (to != null) {
            sql.append(" AND occurred_at < ?");
            params.add(to);
        }
        if (StringUtils.hasText(source)) {
            sql.append(" AND source_key = ?");
            params.add(source.trim());
        }
        sql.append(" ORDER BY occurred_at DESC, id DESC");
        return jdbc.query(sql.toString(), (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("record_id", rs.getLong("record_id"));
            row.put("person_id", rs.getLong("person_id"));
            row.put("schema_key", rs.getString("schema_key"));
            row.put("source_key", rs.getString("source_key"));
            row.put("occurred_at", rs.getObject("occurred_at", OffsetDateTime.class));
            row.put("answers", parseMap(rs.getString("raw_data")).getOrDefault("answers", Map.of()));
            return row;
        }, params.toArray());
    }

    /** 依欄位類型產生一個 dimension；未填寫永遠獨立計數。 */
    private Map<String, Object> dimension(
            FieldDefinition field,
            List<Map<String, Object>> records) {
        List<Object> answeredValues = new ArrayList<>();
        int missing = 0;
        for (Map<String, Object> record : records) {
            Object answersObject = record.get("answers");
            Object value = answersObject instanceof Map<?, ?> answers
                ? answers.get(field.key())
                : null;
            if (isMissing(value)) {
                missing++;
            } else {
                answeredValues.add(value);
            }
        }
        Map<String, Object> dimension = new LinkedHashMap<>();
        dimension.put("fieldKey", field.key());
        dimension.put("label", field.label());
        dimension.put("fieldType", field.type());
        dimension.put("view", field.analyticsView());
        dimension.put("answered", answeredValues.size());
        dimension.put("missing", missing);
        if (List.of("rating", "number").contains(field.type())) {
            dimension.put("summary", numberSummary(answeredValues));
            dimension.put("values", buckets(answeredValues, false));
        } else if ("multi_select".equals(field.type())) {
            dimension.put("values", buckets(answeredValues, true));
        } else if (List.of("short_text", "long_text").contains(field.type())) {
            dimension.put("responses", answeredValues.stream()
                .map(String::valueOf)
                .filter(StringUtils::hasText)
                .limit(500)
                .toList());
        } else {
            dimension.put("values", buckets(answeredValues, false));
        }
        return dimension;
    }

    /** 建立選項分布；多選題分母是有作答的人數，不是選取總次數。 */
    private List<Map<String, Object>> buckets(List<Object> values, boolean multiSelect) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object value : values) {
            if (multiSelect && value instanceof Iterable<?> iterable) {
                iterable.forEach(item -> counts.merge(String.valueOf(item), 1L, Long::sum));
            } else {
                counts.merge(String.valueOf(value), 1L, Long::sum);
            }
        }
        long denominator = values.size();
        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .map(entry -> {
                Map<String, Object> bucket = new LinkedHashMap<>();
                bucket.put("value", entry.getKey());
                bucket.put("label", entry.getKey());
                bucket.put("count", entry.getValue());
                bucket.put("percent", denominator == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(entry.getValue())
                        .divide(BigDecimal.valueOf(denominator), 3, RoundingMode.HALF_UP));
                return bucket;
            })
            .toList();
    }

    /** 數值欄位摘要：平均、中位數、最小與最大。 */
    private Map<String, Object> numberSummary(List<Object> values) {
        List<BigDecimal> numbers = values.stream()
            .map(String::valueOf)
            .map(value -> {
                try {
                    return new BigDecimal(value);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            })
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.naturalOrder())
            .toList();
        if (numbers.isEmpty()) {
            return Map.of();
        }
        BigDecimal sum = numbers.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal median = numbers.size() % 2 == 1
            ? numbers.get(numbers.size() / 2)
            : numbers.get(numbers.size() / 2 - 1)
                .add(numbers.get(numbers.size() / 2))
                .divide(BigDecimal.valueOf(2), 3, RoundingMode.HALF_UP);
        return Map.of(
            "average", sum.divide(BigDecimal.valueOf(numbers.size()), 3, RoundingMode.HALF_UP),
            "median", median,
            "min", numbers.getFirst(),
            "max", numbers.getLast());
    }

    /** 完成率以所有非敏感必填欄位都有值的提交數計算。 */
    private BigDecimal completionRate(
            List<Map<String, Object>> records,
            java.util.Collection<FieldDefinition> fields) {
        List<FieldDefinition> required = fields.stream()
            .filter(FieldDefinition::required)
            .filter(field -> !field.sensitive())
            .toList();
        if (records.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long complete = records.stream().filter(record -> {
            Object answerObject = record.get("answers");
            if (!(answerObject instanceof Map<?, ?> answers)) {
                return required.isEmpty();
            }
            return required.stream().allMatch(field -> !isMissing(answers.get(field.key())));
        }).count();
        return BigDecimal.valueOf(complete)
            .divide(BigDecimal.valueOf(records.size()), 3, RoundingMode.HALF_UP);
    }

    /** 空字串、空集合與 null 都視為未填寫。 */
    private boolean isMissing(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.isBlank();
        }
        if (value instanceof java.util.Collection<?> collection) {
            return collection.isEmpty();
        }
        return false;
    }

    /** formKey 與 version 組成活動 schemaKey。 */
    private String schemaKey(FormDefinition form) {
        return form.key() + "@" + form.version();
    }

    /** 取得並確認版本仍為草稿。 */
    private FormDefinition requireDraft(String formKey, int version) {
        FormDefinition form = getDefinition(formKey, version);
        if (!"DRAFT".equals(form.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只能修改 DRAFT 表單版本");
        }
        return form;
    }

    /** 驗證欄位設定，錯誤在寫入前以易懂訊息回給 Admin。 */
    private void validateField(String fieldKey, FieldRequest request) {
        if (!StringUtils.hasText(fieldKey) || !fieldKey.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "欄位代稱格式錯誤");
        }
        if (request == null || !StringUtils.hasText(request.label())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "欄位名稱不可空白");
        }
        List<String> types = List.of(
            "select", "multi_select", "boolean", "rating", "number",
            "short_text", "long_text", "date", "email");
        if (!types.contains(request.type())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支援的欄位類型");
        }
    }

    /** 將 options 安全序列化成 PostgreSQL jsonb。 */
    private String json(List<Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "選項格式錯誤");
        }
    }

    /** 空字串以 null 保存，避免出現無意義的空 Fact key。 */
    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** 解析 JSON 陣列；壞資料回空陣列，避免整個 Admin 頁面失效。 */
    private List<Object> parseList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Object>>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    /** 解析 JSON 物件；壞資料回空物件。 */
    private Map<String, Object> parseMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }
}
