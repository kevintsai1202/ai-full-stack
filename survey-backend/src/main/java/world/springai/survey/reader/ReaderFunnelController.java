package world.springai.survey.reader;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/** 收集 reader 網站不含個資的第一方匿名漏斗事件。 */
@RestController
public class ReaderFunnelController {

    /** 公開端點只接受產品定義過的事件，避免任意字串灌入分析資料。 */
    private static final Set<String> ALLOWED_EVENTS = Set.of(
        "READER_PAGE_VIEW", "ARTICLE_VIEW", "SUBSCRIPTION_HOME_VIEW",
        "SUBSCRIPTION_CTA_CLICK", "SUBSCRIBE_ATTEMPT", "SUBSCRIBE_SUCCESS",
        "SUBSCRIBE_ERROR", "UNLOCK_CLICK", "UNLOCK_SUCCESS",
        "UNLOCK_INSUFFICIENT", "UNLOCK_ERROR");
    private static final Pattern VISITOR_KEY = Pattern.compile("^[a-zA-Z0-9-]{16,64}$");
    private static final Pattern ARTICLE_SLUG = Pattern.compile("^[a-z0-9-]{1,80}$");

    private final JdbcTemplate jdbc;

    /** 注入參數化 SQL 執行器。 */
    public ReaderFunnelController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 寫入單一漏斗事件；同一匿名訪客、事件、文章每天只計一次，避免重新整理灌水。
     */
    @PostMapping("/api/reader/funnel")
    public ResponseEntity<Void> record(@RequestBody EventRequest request) {
        if (request == null || !ALLOWED_EVENTS.contains(request.eventName())) {
            throw new ResponseStatusException(BAD_REQUEST, "不支援的 reader 漏斗事件");
        }
        String visitorKey = cleanVisitorKey(request.visitorKey());
        String pagePath = cleanPagePath(request.pagePath());
        String articleSlug = cleanArticleSlug(request.articleSlug());
        jdbc.update("""
            insert into reader_funnel_event (visitor_key, event_name, page_path, article_slug)
            values (?, ?, ?, ?)
            on conflict on constraint uq_reader_funnel_daily do nothing
            """, visitorKey, request.eventName(), pagePath, articleSlug);
        return ResponseEntity.noContent().build();
    }

    /** 驗證匿名識別碼格式；它只能是瀏覽器隨機值，不接受 Email 或任意文字。 */
    private String cleanVisitorKey(String value) {
        if (value == null || !VISITOR_KEY.matcher(value).matches()) {
            throw new ResponseStatusException(BAD_REQUEST, "visitorKey 格式錯誤");
        }
        return value;
    }

    /** 只接受 reader 站內路徑，且移除 query string，避免意外保存追蹤參數。 */
    private String cleanPagePath(String value) {
        if (value == null || value.isBlank()) return null;
        String clean = value.split("\\?", 2)[0].trim();
        if (!clean.startsWith("/r/") || clean.length() > 240) {
            throw new ResponseStatusException(BAD_REQUEST, "pagePath 格式錯誤");
        }
        return clean;
    }

    /** 驗證文章 slug；非文章頁可省略。 */
    private String cleanArticleSlug(String value) {
        if (value == null || value.isBlank()) return null;
        String clean = value.trim();
        if (!ARTICLE_SLUG.matcher(clean).matches()) {
            throw new ResponseStatusException(BAD_REQUEST, "articleSlug 格式錯誤");
        }
        return clean;
    }

    /** 前端事件 DTO；刻意不提供 Email、名稱、IP 等個資欄位。 */
    public record EventRequest(String eventName, String visitorKey,
                               String pagePath, String articleSlug) {}
}
