package world.springai.survey.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.AdminKeyGuard;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 課程優惠券 Admin API：建立活動、列表、預覽收件人、寄送優惠券。
 * 全部端點皆須驗證 {@code X-Admin-Key}。
 */
@RestController
public class AdminCouponController {

    /** HTTP Header 名稱：後台金鑰 */
    private static final String KEY_HEADER = "X-Admin-Key";

    private final CouponCampaignRepository campaignRepository;
    private final CouponRecipientService recipientService;
    private final CouponSendService sendService;
    private final AdminKeyGuard guard;
    private final ObjectMapper objectMapper;

    /** 注入依賴：活動與寄送服務、金鑰驗證、JSON 解析器 */
    public AdminCouponController(
            CouponCampaignRepository campaignRepository,
            CouponRecipientService recipientService,
            CouponSendService sendService,
            AdminKeyGuard guard,
            ObjectMapper objectMapper) {
        this.campaignRepository = campaignRepository;
        this.recipientService = recipientService;
        this.sendService = sendService;
        this.guard = guard;
        this.objectMapper = objectMapper;
    }

    /** 建立優惠券活動請求 */
    public record CreateCampaignRequest(
            String courseName,
            String pitch,
            String courseUrl,
            String couponCode,
            String expiresAt,
            String formKey,
            Map<String, Object> answerFilter) {}

    /** 寄送優惠券請求 */
    public record SendRequest(
            List<String> emails,
            Integer limit) {}

    /**
     * 建立優惠券活動：驗證必填欄位與 courseUrl 格式（https:// 開頭），
     * 將 answerFilter Map 序列化為 JSON 字串後儲存。
     * 成功 200 回傳活動 JSON；驗證失敗或金鑰錯誤 400/401。
     */
    @PostMapping("/api/admin/coupons")
    public CouponCampaign createCampaign(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @RequestBody CreateCampaignRequest request) {
        // 驗證 Admin 金鑰
        guard.verify(key);

        // 驗證必填欄位
        if (request.courseName() == null || request.courseName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "courseName 不得為空");
        }
        if (request.pitch() == null || request.pitch().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pitch 不得為空");
        }
        if (request.couponCode() == null || request.couponCode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "couponCode 不得為空");
        }
        if (request.formKey() == null || request.formKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "formKey 不得為空");
        }

        // 驗證 courseUrl：必填且限 https:// 開頭
        if (request.courseUrl() == null || request.courseUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "courseUrl 不得為空");
        }
        if (!request.courseUrl().startsWith("https://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "courseUrl 必須使用 https:// 開頭");
        }

        // 轉換 answerFilter Map 為 JSON 字串（null 或空 Map → "{}"）
        String answerFilterJson = "{}";
        if (request.answerFilter() != null && !request.answerFilter().isEmpty()) {
            try {
                answerFilterJson = objectMapper.writeValueAsString(request.answerFilter());
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "answerFilter 序列化失敗：" + e.getMessage());
            }
        }

        // 解析 expiresAt（可為 null）
        LocalDate expiresAt = null;
        if (request.expiresAt() != null && !request.expiresAt().isBlank()) {
            try {
                expiresAt = LocalDate.parse(request.expiresAt());
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expiresAt 格式不正確，應為 YYYY-MM-DD");
            }
        }

        // 建立活動物件並存入資料庫
        CouponCampaign campaign = new CouponCampaign(
            request.courseName(),
            request.pitch(),
            request.courseUrl(),
            request.couponCode(),
            expiresAt,
            request.formKey(),
            answerFilterJson);

        return campaignRepository.save(campaign);
    }

    /**
     * 列表所有優惠券活動，按建立時間新到舊排序。
     */
    @GetMapping("/api/admin/coupons")
    public List<CouponCampaign> listCampaigns(
            @RequestHeader(value = KEY_HEADER, required = false) String key) {
        // 驗證 Admin 金鑰
        guard.verify(key);

        return campaignRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * 預覽指定活動的命中收件人清單（以建立當下的問卷與篩選條件）；
     * 活動不存在拋 404。
     */
    @PostMapping("/api/admin/coupons/{id}/preview-recipients")
    public List<CouponRecipientService.Recipient> previewRecipients(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @PathVariable Long id) {
        // 驗證 Admin 金鑰
        guard.verify(key);

        // 取得活動，不存在拋 404
        CouponCampaign campaign = campaignRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到指定優惠券活動"));

        // 委派給名單服務查詢命中名單
        return recipientService.resolve(campaign);
    }

    /**
     * 對指定活動的收件人子集寄送優惠券信；
     * 驗證失敗（404 活動不存在、400 名單驗證）拋 ResponseStatusException。
     */
    @PostMapping("/api/admin/coupons/{id}/send")
    public CouponSendService.SendResult sendCoupons(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @PathVariable Long id,
            @RequestBody SendRequest request) {
        // 驗證 Admin 金鑰
        guard.verify(key);

        // 委派給寄送服務（會自行驗證活動存在與名單合法性）
        return sendService.send(id, request.emails(), request.limit());
    }
}
