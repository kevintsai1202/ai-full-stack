package world.springai.survey.audience;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/** 對讀者網站公開不含個資的訂閱人數。 */
@RestController
public class PublicSubscriberController {

    private final RecipientService recipientService;

    /** 注入與實際寄送共用的名單服務，確保人數定義一致。 */
    public PublicSubscriberController(RecipientService recipientService) {
        this.recipientService = recipientService;
    }

    /**
     * 回傳目前可寄送訂閱人數；短暫快取降低每個 reader 頁面都查資料庫的成本。
     */
    @GetMapping("/api/reader/subscriber-count")
    public ResponseEntity<Map<String, Long>> count() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
            .body(Map.of("count", recipientService.subscriberCount()));
    }
}
