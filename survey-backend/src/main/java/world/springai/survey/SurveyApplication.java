package world.springai.survey;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/** 問卷收集後端啟動類 */
@SpringBootApplication
public class SurveyApplication {
    public static void main(String[] args) {
        SpringApplication.run(SurveyApplication.class, args);
    }
}

/** 健康檢查端點，供 Zeabur 與監控確認服務存活 */
@RestController
class HealthController {
    /** 回傳服務存活狀態 */
    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
