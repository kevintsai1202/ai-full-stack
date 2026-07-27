package world.springai.survey.audience;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import world.springai.survey.AdminKeyGuard;

import java.util.Map;

/** Admin 人物資料匯出與刪除 API。 */
@RestController
public class AdminAudienceDataController {

    private final AdminKeyGuard guard;
    private final AudienceDataLifecycleService service;

    /** 注入 Admin 守衛與資料生命週期服務。 */
    public AdminAudienceDataController(
            AdminKeyGuard guard,
            AudienceDataLifecycleService service) {
        this.guard = guard;
        this.service = service;
    }

    /** 匯出單一人物的完整可攜資料。 */
    @GetMapping("/api/admin/audience/data/export")
    public Map<String, Object> export(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestParam String email) {
        guard.verify(key);
        return service.export(email);
    }

    /** 刪除人物資料；原因必填，Reader 帳本改採匿名保留。 */
    @DeleteMapping("/api/admin/audience/data")
    public Map<String, Object> delete(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestParam String email,
            @RequestParam String reason) {
        guard.verify(key);
        return service.delete(email, reason);
    }
}
