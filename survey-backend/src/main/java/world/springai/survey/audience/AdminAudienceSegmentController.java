package world.springai.survey.audience;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import world.springai.survey.AdminKeyGuard;

import java.util.List;

/** Admin 常用分眾條件 CRUD。 */
@RestController
public class AdminAudienceSegmentController {

    private final AdminKeyGuard guard;
    private final AudienceSegmentService service;

    /** 注入 Admin 守衛與分眾服務。 */
    public AdminAudienceSegmentController(AdminKeyGuard guard, AudienceSegmentService service) {
        this.guard = guard;
        this.service = service;
    }

    /** 列出保存的分眾。 */
    @GetMapping("/api/admin/audience/segments")
    public List<AudienceSegmentService.Segment> list(
            @RequestHeader(value = "X-Admin-Key", required = false) String key) {
        guard.verify(key);
        return service.list();
    }

    /** 新增分眾。 */
    @PostMapping("/api/admin/audience/segments")
    public AudienceSegmentService.Segment create(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestBody AudienceSegmentService.SegmentRequest request) {
        guard.verify(key);
        return service.create(request);
    }

    /** 修改分眾。 */
    @PutMapping("/api/admin/audience/segments/{id}")
    public AudienceSegmentService.Segment update(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @PathVariable long id,
            @RequestBody AudienceSegmentService.SegmentRequest request) {
        guard.verify(key);
        return service.update(id, request);
    }

    /** 刪除分眾。 */
    @DeleteMapping("/api/admin/audience/segments/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @PathVariable long id) {
        guard.verify(key);
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
