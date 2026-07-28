package world.springai.survey.reader;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.regex.Pattern;

/** 公開分享點擊追蹤；回應永遠相同，不洩漏推薦碼是否存在。 */
@RestController
public class ReferralTrackingController {

    private static final Pattern VISITOR_KEY = Pattern.compile("[A-Za-z0-9_-]{16,64}");
    private static final Pattern SAFE_SLUG = Pattern.compile("[a-z0-9][a-z0-9-]{0,99}");

    private final ReaderRepository readers;
    private final ReferralClickRepository clicks;

    /** 注入推薦碼與點擊資料來源。 */
    public ReferralTrackingController(ReaderRepository readers, ReferralClickRepository clicks) {
        this.readers = readers;
        this.clicks = clicks;
    }

    /** 點擊請求；無效、重複或不存在皆回 204，避免成為推薦碼探測器。 */
    @PostMapping("/api/referrals/click")
    public ResponseEntity<Void> click(@RequestBody ClickRequest request) {
        if (request == null
                || !StringUtils.hasText(request.ref())
                || request.ref().length() > 128
                || request.visitorKey() == null
                || !VISITOR_KEY.matcher(request.visitorKey()).matches()) {
            return ResponseEntity.noContent().build();
        }
        String slug = request.slug();
        if (StringUtils.hasText(slug) && !SAFE_SLUG.matcher(slug).matches()) {
            return ResponseEntity.noContent().build();
        }
        readers.findByReferralCode(request.ref().trim()).ifPresent(referrer -> {
            try {
                clicks.saveAndFlush(new ReferralClick(referrer.getId(), request.ref().trim(),
                    StringUtils.hasText(slug) ? slug : null, request.visitorKey(),
                    LocalDate.now(ZoneOffset.UTC)));
            } catch (DataIntegrityViolationException ignored) {
                // 同一訪客同一天重複開啟不重複計數；公開回應仍保持完全相同。
            }
        });
        return ResponseEntity.noContent().build();
    }

    /** 公開點擊資料，只接受推薦碼、文章 slug 與隨機訪客代碼。 */
    public record ClickRequest(String ref, String slug, String visitorKey) {}
}

