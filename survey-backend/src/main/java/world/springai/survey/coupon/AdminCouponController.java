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
import world.springai.survey.audience.SubscriptionLinkBuilder;
import world.springai.survey.form.FormSchemaService;
import world.springai.survey.mail.CouponMailRenderer;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 課程優惠券 Admin API：建立活動、列表、預覽收件人、寄送優惠券、預覽信件。
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
    private final CouponMailRenderer mailRenderer;
    private final SubscriptionLinkBuilder linkBuilder;
    private final FormSchemaService formSchemaService;

    /** 注入依賴：活動與寄送服務、金鑰驗證、JSON 解析器、信件渲染與退訂連結組裝、表單 schema 服務 */
    public AdminCouponController(
            CouponCampaignRepository campaignRepository,
            CouponRecipientService recipientService,
            CouponSendService sendService,
            AdminKeyGuard guard,
            ObjectMapper objectMapper,
            CouponMailRenderer mailRenderer,
            SubscriptionLinkBuilder linkBuilder,
            FormSchemaService formSchemaService) {
        this.campaignRepository = campaignRepository;
        this.recipientService = recipientService;
        this.sendService = sendService;
        this.guard = guard;
        this.objectMapper = objectMapper;
        this.mailRenderer = mailRenderer;
        this.linkBuilder = linkBuilder;
        this.formSchemaService = formSchemaService;
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

    /** 預覽信件請求：與 {@link CreateCampaignRequest} 同七欄位，僅供渲染、不落庫 */
    public record PreviewMailRequest(
            String courseName,
            String pitch,
            String courseUrl,
            String couponCode,
            String expiresAt,
            String formKey,
            Map<String, Object> answerFilter) {}

    /** 信件預覽結果：主旨與內文 HTML */
    public record MailPreview(String subject, String html) {}

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

    /**
     * 預覽即將寄出的優惠券信件（spec §8.1）：body 與建立活動同七欄位，但刻意不落庫，
     * 純粹以 {@link CouponMailRenderer} 渲染出主旨與內文供後台即時檢視。
     *
     * <p>退訂連結一律用 {@link SubscriptionLinkBuilder#previewUnsubscribeLink()}（假
     * email、假簽章）——預覽內容會直接顯示在後台頁面，不該讓一個可用的退訂 token 隨預覽外流，
     * 理由與該方法本身的 Javadoc 一致。寄送原因用的問卷標題與
     * {@link CouponSendService#send} 同口徑：取該 formKey 版本號最大者的標題，查無版本
     * 時退回 formKey 原字串——此處刻意獨立實作一份（而非讓 controller 依賴 service 內部
     * private 方法），因為兩者分屬 controller／service 不同層級，重複四行 stream 邏輯比
     * 額外增加跨層耦合的成本更低。</p>
     */
    @PostMapping("/api/admin/coupons/preview-mail")
    public MailPreview previewMail(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @RequestBody PreviewMailRequest request) {
        // 驗證 Admin 金鑰
        guard.verify(key);

        // 解析 expiresAt（可為 null／空白）；格式不正確直接 400，不進入渲染
        LocalDate expiresAt = null;
        if (request.expiresAt() != null && !request.expiresAt().isBlank()) {
            try {
                expiresAt = LocalDate.parse(request.expiresAt());
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expiresAt 格式不正確，應為 YYYY-MM-DD");
            }
        }

        // 建立暫時性活動物件供渲染器使用，刻意不呼叫 campaignRepository.save，不落庫
        CouponCampaign draft = new CouponCampaign(
            request.courseName(),
            request.pitch(),
            request.courseUrl(),
            request.couponCode(),
            expiresAt,
            request.formKey(),
            "{}");

        String formTitle = resolveFormTitle(request.formKey());
        String subject = mailRenderer.subject(draft);
        String html = mailRenderer.body(draft, formTitle, linkBuilder.previewUnsubscribeLink());
        return new MailPreview(subject, html);
    }

    /**
     * 取該 formKey 版本號最大的問卷標題，供預覽信件的寄送原因顯示；查無任何版本或
     * formKey 空白時退回 formKey 原字串（不擋預覽）。口徑對齊 {@code CouponSendService.resolveFormTitle}。
     */
    private String resolveFormTitle(String formKey) {
        if (formKey == null || formKey.isBlank()) {
            return "";
        }
        return formSchemaService.listDefinitions().stream()
            .filter(definition -> definition.key().equals(formKey))
            .max(Comparator.comparingInt(FormSchemaService.FormDefinition::version))
            .map(FormSchemaService.FormDefinition::title)
            .orElse(formKey);
    }
}
