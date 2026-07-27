package world.springai.survey.audience;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import world.springai.survey.AdminKeyGuard;

/** Admin 彈性人物搜尋 API。 */
@RestController
public class AdminAudienceController {

    private final AdminKeyGuard guard;
    private final AudienceSearchService service;

    /** 注入 Admin 守衛與伺服器端搜尋服務。 */
    public AdminAudienceController(AdminKeyGuard guard, AudienceSearchService service) {
        this.guard = guard;
        this.service = service;
    }

    /** 依複合條件搜尋人物並分頁回傳。 */
    @PostMapping("/api/admin/audience/search")
    public AudienceSearchService.SearchResult search(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestBody(required = false) AudienceSearchService.SearchRequest request) {
        guard.verify(key);
        return service.search(request);
    }
}
