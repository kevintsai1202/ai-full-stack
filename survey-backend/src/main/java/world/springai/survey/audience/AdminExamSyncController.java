package world.springai.survey.audience;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import world.springai.survey.AdminKeyGuard;

/** Admin Exam 名單同步：先預覽，再由管理員確認執行。 */
@RestController
public class AdminExamSyncController {

    private final AdminKeyGuard guard;
    private final ExamAudienceSyncService service;

    /** 注入 Admin 金鑰守衛與 Exam 同步服務。 */
    public AdminExamSyncController(AdminKeyGuard guard, ExamAudienceSyncService service) {
        this.guard = guard;
        this.service = service;
    }

    /** 下載增量資料並預覽，不寫入人物、活動或 cursor。 */
    @PostMapping("/api/admin/integrations/exam/preview")
    public ExamAudienceSyncService.Preview preview(
            @RequestHeader(value = "X-Admin-Key", required = false) String key) {
        guard.verify(key);
        return service.preview();
    }

    /** 確認後執行冪等同步並前進 cursor。 */
    @PostMapping("/api/admin/integrations/exam/sync")
    public ExamAudienceSyncService.SyncResult sync(
            @RequestHeader(value = "X-Admin-Key", required = false) String key) {
        guard.verify(key);
        return service.sync();
    }
}
