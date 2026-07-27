package world.springai.survey.newsletter;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import world.springai.survey.AdminKeyGuard;

import java.util.List;
import java.util.Map;

/** 後台文章 hashtag 選項 API。 */
@RestController
public class AdminCampaignMetadataController {

    private final AdminKeyGuard guard;
    private final CampaignMetadataService service;

    /** 注入管理金鑰守衛與文章中繼資料服務。 */
    public AdminCampaignMetadataController(AdminKeyGuard guard, CampaignMetadataService service) {
        this.guard = guard;
        this.service = service;
    }

    /** 取得預設與自訂 hashtag。 */
    @GetMapping("/api/admin/campaign/tags")
    public List<Map<String, Object>> tags(
            @RequestHeader(value = "X-Admin-Key", required = false) String key) {
        guard.verify(key);
        return service.listTags();
    }
}
