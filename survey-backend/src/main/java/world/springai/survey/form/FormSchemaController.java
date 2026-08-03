package world.springai.survey.form;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import world.springai.survey.AdminKeyGuard;
import world.springai.survey.audience.WelcomeMailService;
import world.springai.survey.reader.ReaderSessionService;

import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 動態表單公開提交、公開安全統計與 Admin schema／分析 API。 */
@RestController
public class FormSchemaController {

    private final FormSchemaService service;
    private final AdminKeyGuard guard;
    private final WelcomeMailService welcomeMailService;
    private final NewsletterSubmissionService newsletterSubmissionService;

    /** 注入 schema 服務、Admin 金鑰守衛、歡迎信服務與電子報通道問卷服務。 */
    public FormSchemaController(
            FormSchemaService service,
            AdminKeyGuard guard,
            WelcomeMailService welcomeMailService,
            NewsletterSubmissionService newsletterSubmissionService) {
        this.service = service;
        this.guard = guard;
        this.welcomeMailService = welcomeMailService;
        this.newsletterSubmissionService = newsletterSubmissionService;
    }

    /** 公開取得目前發布版本 schema，前端不需硬編碼欄位。 */
    @GetMapping("/api/forms/{formKey}")
    public FormSchemaService.FormDefinition publicDefinition(@PathVariable String formKey) {
        return service.getDefinition(formKey, null);
    }

    /** 公開提交任意表單版本；成功後寄送既有歡迎／確認信。 */
    @PostMapping("/api/forms/{formKey}/submissions")
    public ResponseEntity<FormSchemaService.SubmissionResult> submit(
            @PathVariable String formKey,
            @RequestBody FormSchemaService.SubmissionRequest request) {
        // 蜜罐有內容視為機器人；維持無錯誤回應，但不建立人物、活動或寄信。
        if (request.website() != null && !request.website().isBlank()) {
            return ResponseEntity.noContent().build();
        }
        FormSchemaService.SubmissionResult result = service.submit(formKey, request);
        welcomeMailService.sendWelcome(request.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /** 電子報通道問卷提交；身分由後端解析（rt token 或讀者 session），不信任前端 email。 */
    @PostMapping("/api/forms/{formKey}/newsletter-submissions")
    public NewsletterSubmissionService.SubmitResult submitNewsletter(
            @PathVariable String formKey,
            @RequestBody NewsletterSubmissionService.SubmitRequest request,
            @CookieValue(
                value = ReaderSessionService.COOKIE_NAME,
                required = false) String sessionCookie) {
        // 委派完整邏輯到服務層：身分解析、答案驗證、人物合併、發點
        return newsletterSubmissionService.submit(formKey, request, sessionCookie);
    }

    /** 公開統計只輸出 schema 明確允許且非敏感的欄位。 */
    @GetMapping("/api/forms/{formKey}/stats")
    public Map<String, Object> publicStats(
            @PathVariable String formKey,
            @RequestParam(required = false) Integer version) {
        return service.analytics(formKey, version, false, null, null, null, true);
    }

    /** Admin 列出全部表單與版本。 */
    @GetMapping("/api/admin/forms")
    public List<FormSchemaService.FormDefinition> listDefinitions(
            @RequestHeader(value = "X-Admin-Key", required = false) String key) {
        guard.verify(key);
        return service.listDefinitions();
    }

    /** 建立全新問卷（v1 草稿空殼）；欄位之後用既有欄位編輯端點補。 */
    public record CreateFormRequest(String formKey, String title) {}

    /** Admin 建立全新問卷；formKey 格式不符 400、重複 409。 */
    @PostMapping("/api/admin/forms")
    public FormSchemaService.FormDefinition createForm(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestBody CreateFormRequest request) {
        guard.verify(key);
        return service.createForm(request.formKey(), request.title());
    }

    /** Admin 從最新版本建立新草稿，已發布版本不會被原地修改。 */
    @PostMapping("/api/admin/forms/{formKey}/versions")
    public ResponseEntity<FormSchemaService.FormDefinition> createVersion(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @PathVariable String formKey,
            @RequestBody(required = false) FormSchemaService.VersionRequest request) {
        guard.verify(key);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createVersion(formKey, request));
    }

    /** Admin 在草稿版本新增欄位。 */
    @PostMapping("/api/admin/forms/{formKey}/versions/{version}/fields/{fieldKey}")
    public ResponseEntity<FormSchemaService.FormDefinition> addField(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @PathVariable String formKey,
            @PathVariable int version,
            @PathVariable String fieldKey,
            @RequestBody FormSchemaService.FieldRequest request) {
        guard.verify(key);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(service.addField(formKey, version, fieldKey, request));
    }

    /** Admin 更新草稿欄位設定。 */
    @org.springframework.web.bind.annotation.PutMapping(
        "/api/admin/forms/{formKey}/versions/{version}/fields/{fieldKey}")
    public FormSchemaService.FormDefinition updateField(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @PathVariable String formKey,
            @PathVariable int version,
            @PathVariable String fieldKey,
            @RequestBody FormSchemaService.FieldRequest request) {
        guard.verify(key);
        return service.updateField(formKey, version, fieldKey, request);
    }

    /** Admin 發布草稿版本；舊發布版本會自動封存。 */
    @PostMapping("/api/admin/forms/{formKey}/versions/{version}/publish")
    public FormSchemaService.FormDefinition publish(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @PathVariable String formKey,
            @PathVariable int version) {
        guard.verify(key);
        return service.publish(formKey, version);
    }

    /** Admin 指定或清除信中一鍵題所綁定的欄位；fieldKey 為 null 表示清除。 */
    public record EmailVoteFieldRequest(String fieldKey) {}

    /** Admin 指定信中一鍵題欄位；欄位不存在或非單選（select）題以 400 拒絕。 */
    @PutMapping("/api/admin/forms/{formKey}/versions/{version}/email-vote-field")
    public ResponseEntity<Void> updateEmailVoteField(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @PathVariable String formKey,
            @PathVariable int version,
            @RequestBody(required = false) EmailVoteFieldRequest request) {
        guard.verify(key);
        service.updateEmailVoteField(formKey, version, request == null ? null : request.fieldKey());
        return ResponseEntity.noContent().build();
    }

    /** Admin 列出全部已發布且已設信中一鍵題的問卷，供電子報編輯器插入選單。 */
    @GetMapping("/api/admin/forms/embeddable")
    public List<FormSchemaService.EmailVoteQuestion> listEmbeddable(
            @RequestHeader(value = "X-Admin-Key", required = false) String key) {
        guard.verify(key);
        return service.listEmbeddable();
    }

    /** Admin 動態分析；可選單一版本或合併相同 fieldKey 的全部版本。 */
    @GetMapping("/api/admin/analytics/forms/{formKey}")
    public Map<String, Object> analytics(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @PathVariable String formKey,
            @RequestParam(required = false) Integer version,
            @RequestParam(defaultValue = "false") boolean allVersions,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) String source) {
        guard.verify(key);
        return service.analytics(formKey, version, allVersions, from, to, source, false);
    }

    /** Admin 匯出動態表單原始資料；CSV 表頭依實際 schema 答案 key 自動展開。 */
    @GetMapping("/api/admin/analytics/forms/{formKey}/records")
    public ResponseEntity<?> records(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @PathVariable String formKey,
            @RequestParam(required = false) Integer version,
            @RequestParam(defaultValue = "false") boolean allVersions,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "json") String format) {
        guard.verify(key);
        List<Map<String, Object>> rows =
            service.rawRecords(formKey, version, allVersions, from, to, source);
        if (!"csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok(rows);
        }
        byte[] csv = csv(rows).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + formKey + "-records.csv\"")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(csv);
    }

    /** 將 answers 動態欄位攤平成 CSV；每個值完整引用以避免逗號與換行破壞欄位。 */
    private String csv(List<Map<String, Object>> rows) {
        Set<String> answerKeys = new LinkedHashSet<>();
        rows.forEach(row -> {
            if (row.get("answers") instanceof Map<?, ?> answers) {
                answers.keySet().forEach(value -> answerKeys.add(String.valueOf(value)));
            }
        });
        List<String> headers = new ArrayList<>(
            List.of("recordId", "schemaKey", "source", "occurredAt", "email", "name"));
        answerKeys.forEach(answerKey -> headers.add("answers." + answerKey));
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append(headers.stream().map(this::csvCell).collect(java.util.stream.Collectors.joining(",")))
            .append("\r\n");
        rows.forEach(row -> {
            Map<?, ?> answers = row.get("answers") instanceof Map<?, ?> values ? values : Map.of();
            List<Object> values = new ArrayList<>();
            headers.forEach(header -> values.add(header.startsWith("answers.")
                ? answers.get(header.substring("answers.".length()))
                : row.get(header)));
            csv.append(values.stream()
                .map(value -> csvCell(value == null ? "" : value))
                .collect(java.util.stream.Collectors.joining(",")))
                .append("\r\n");
        });
        return csv.toString();
    }

    /** 引用單一 CSV 值。 */
    private String csvCell(Object value) {
        return "\"" + String.valueOf(value).replace("\"", "\"\"") + "\"";
    }
}
