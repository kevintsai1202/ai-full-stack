package world.springai.survey.audience;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import world.springai.survey.AdminKeyGuard;

import java.util.UUID;

/** Admin 讀者批次贈點與 VIP 的預覽、執行、進度 API。 */
@RestController
public class AdminAudienceBulkController {

    private final AdminKeyGuard guard;
    private final AudienceBulkOperationService service;

    /** 注入 Admin 守衛與批次操作服務。 */
    public AdminAudienceBulkController(
            AdminKeyGuard guard,
            AudienceBulkOperationService service) {
        this.guard = guard;
        this.service = service;
    }

    /** 依篩選或勾選人物建立 10 分鐘固定快照。 */
    @PostMapping("/api/admin/readers/bulk/preview")
    public AudienceBulkOperationService.PreviewResult preview(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestBody AudienceBulkOperationService.PreviewRequest request) {
        guard.verify(key);
        return service.preview(request);
    }

    /** 二次確認後執行；idempotencyKey 重送不會重複發放。 */
    @PostMapping("/api/admin/readers/bulk/execute")
    public AudienceBulkOperationService.OperationResult execute(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestBody AudienceBulkOperationService.ExecuteRequest request) {
        guard.verify(key);
        return service.execute(request);
    }

    /** 查詢批次操作結果。 */
    @GetMapping("/api/admin/readers/bulk/{operationId}")
    public AudienceBulkOperationService.OperationResult get(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @PathVariable UUID operationId) {
        guard.verify(key);
        return service.get(operationId);
    }
}
