package world.springai.survey;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 外部名單匯入後台 API：匯入為待確認狀態（consent=false），需 X-Admin-Key */
@RestController
public class AdminImportController {

    /** 簡易 email 格式檢查（與前端一致的寬鬆規則：有 @ 與網域點號） */
    private static final Pattern EMAIL_RE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final SurveyResponseRepository repository;
    private final AdminKeyGuard guard;

    /** 注入資料層與金鑰守衛 */
    public AdminImportController(SurveyResponseRepository repository, AdminKeyGuard guard) {
        this.repository = repository;
        this.guard = guard;
    }

    /** 匯入的單一人員：email 必填，name 選填 */
    public record Person(String email, String name) {}

    /** 匯入請求：來源標記（如 exam）與人員清單 */
    public record ImportRequest(String source, List<Person> people) {}

    /**
     * 匯入外部名單：每筆以 consent=false（待確認）寫入並標記來源；
     * email 無效或已存在（不分大小寫）者略過。回傳 imported/skipped 計數。
     */
    @PostMapping("/api/admin/import")
    public Map<String, Integer> importPeople(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestBody ImportRequest req) {
        guard.verify(key);
        // source 與 people 為必填，缺一回 400
        if (!StringUtils.hasText(req.source()) || req.people() == null || req.people().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "source 與 people 為必填");
        }
        int imported = 0;
        int skipped = 0;
        for (Person p : req.people()) {
            String email = p.email() == null ? "" : p.email().trim();
            // 無效或重複的 email 一律略過
            if (!EMAIL_RE.matcher(email).matches() || repository.existsByEmailIgnoreCase(email)) {
                skipped++;
                continue;
            }
            SurveyResponse entity = new SurveyResponse();
            entity.setEmail(email);
            entity.setName(p.name());
            entity.setSource(req.source().trim());
            entity.setConsent(false); // 待確認：點擊確認信連結後才轉 true
            repository.save(entity);
            imported++;
        }
        return Map.of("imported", imported, "skipped", skipped);
    }
}
