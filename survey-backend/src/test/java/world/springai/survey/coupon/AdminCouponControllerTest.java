package world.springai.survey.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.AdminKeyGuard;
import world.springai.survey.ApiExceptionHandler;
import world.springai.survey.audience.SubscriptionLinkBuilder;
import world.springai.survey.form.FormSchemaService;
import world.springai.survey.mail.CouponMailRenderer;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 優惠券 Admin API：建立、列表、preview 收件人、寄送端點測試 */
class AdminCouponControllerTest {

    private AdminCouponController controller;
    private MockMvc mvc;
    private CouponCampaignRepository campaignRepository;
    private CouponRecipientService recipientService;
    private CouponSendService sendService;
    private AdminKeyGuard guard;
    private ObjectMapper objectMapper;
    private CouponMailRenderer mailRenderer;
    private SubscriptionLinkBuilder linkBuilder;
    private FormSchemaService formSchemaService;

    @BeforeEach
    void setUp() {
        // 建立所有 mock 服務
        campaignRepository = mock(CouponCampaignRepository.class);
        recipientService = mock(CouponRecipientService.class);
        sendService = mock(CouponSendService.class);
        guard = mock(AdminKeyGuard.class);
        objectMapper = new ObjectMapper();
        // 信件渲染器直接用真實實例（無外部依賴），讓 preview-mail 測試驗證真正的渲染輸出
        mailRenderer = new CouponMailRenderer();
        linkBuilder = mock(SubscriptionLinkBuilder.class);
        formSchemaService = mock(FormSchemaService.class);
        when(linkBuilder.previewUnsubscribeLink()).thenReturn("https://example.com/api/survey/unsubscribe?email=preview%40example.com&t=preview");
        when(formSchemaService.listDefinitions()).thenReturn(List.of());

        // 注入依賴建立 controller
        controller = new AdminCouponController(
            campaignRepository,
            recipientService,
            sendService,
            guard,
            objectMapper,
            mailRenderer,
            linkBuilder,
            formSchemaService);

        // MockMvc 需要 UTF-8 StringHttpMessageConverter（中文）與 JSON converter，
        // 以及異常處理器將 ResponseStatusException 轉成 JSON 回應
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiExceptionHandler())
            .setMessageConverters(
                new StringHttpMessageConverter(StandardCharsets.UTF_8),
                new MappingJackson2HttpMessageConverter())
            .build();
    }

    /** 建立活動請求 JSON */
    private String createCampaignJson(String courseName, String pitch, String courseUrl,
                                      String couponCode, String expiresAt, String formKey,
                                      Map<String, Object> answerFilter) {
        try {
            Map<String, Object> body = Map.of(
                "courseName", courseName != null ? courseName : "",
                "pitch", pitch != null ? pitch : "",
                "courseUrl", courseUrl != null ? courseUrl : "",
                "couponCode", couponCode != null ? couponCode : "",
                "expiresAt", expiresAt != null ? expiresAt : "",
                "formKey", formKey != null ? formKey : "",
                "answerFilter", answerFilter != null ? answerFilter : Map.of()
            );
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 預覽信件請求 JSON（同建立活動七欄位，不落庫） */
    private String previewMailJson(String courseName, String pitch, String courseUrl,
                                   String couponCode, String expiresAt, String formKey) {
        try {
            Map<String, Object> body = Map.of(
                "courseName", courseName != null ? courseName : "",
                "pitch", pitch != null ? pitch : "",
                "courseUrl", courseUrl != null ? courseUrl : "",
                "couponCode", couponCode != null ? couponCode : "",
                "expiresAt", expiresAt != null ? expiresAt : "",
                "formKey", formKey != null ? formKey : "",
                "answerFilter", Map.of()
            );
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 寄送請求 JSON */
    private String sendJson(List<String> emails, Integer limit) {
        try {
            Map<String, Object> body = Map.of(
                "emails", emails != null ? emails : List.of(),
                "limit", limit != null ? limit : 0
            );
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ========== 建立活動測試 ==========

    /** 成功建立活動回 200，body 含 id、courseName、status 等欄位 */
    @Test
    void 成功建立活動回200帶活動物件() throws Exception {
        // 模擬 save 返回有 id 的 campaign（用反射設定 private 欄位）
        when(campaignRepository.save(any(CouponCampaign.class)))
            .thenAnswer(invocation -> {
                CouponCampaign arg = invocation.getArgument(0);
                try {
                    var idField = CouponCampaign.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(arg, 1L);
                    var createdAtField = CouponCampaign.class.getDeclaredField("createdAt");
                    createdAtField.setAccessible(true);
                    createdAtField.set(arg, OffsetDateTime.now());
                    var updatedAtField = CouponCampaign.class.getDeclaredField("updatedAt");
                    updatedAtField.setAccessible(true);
                    updatedAtField.set(arg, OffsetDateTime.now());
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
                return arg;
            });

        mvc.perform(post("/api/admin/coupons")
                .header("X-Admin-Key", "valid-key")
                .contentType(APPLICATION_JSON)
                .content(createCampaignJson(
                    "Python 入門", "學習 Python", "https://example.com",
                    "COUPON123", "2026-09-30", "survey-key", Map.of())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.courseName").value("Python 入門"))
            .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    /** courseUrl 非 https 開頭拋 400 */
    @Test
    void courseUrl非https開頭回400() throws Exception {
        mvc.perform(post("/api/admin/coupons")
                .header("X-Admin-Key", "valid-key")
                .contentType(APPLICATION_JSON)
                .content(createCampaignJson(
                    "Python 入門", "學習 Python", "http://example.com",
                    "COUPON123", "2026-09-30", "survey-key", Map.of())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").exists());
    }

    /** courseName 必填，缺失拋 400 */
    @Test
    void courseName必填缺失回400() throws Exception {
        mvc.perform(post("/api/admin/coupons")
                .header("X-Admin-Key", "valid-key")
                .contentType(APPLICATION_JSON)
                .content(createCampaignJson(
                    "", "學習 Python", "https://example.com",
                    "COUPON123", "2026-09-30", "survey-key", Map.of())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").exists());
    }

    /** 缺 X-Admin-Key 拋 401 */
    @Test
    void 缺Admin金鑰回401() throws Exception {
        doThrow(new ResponseStatusException(UNAUTHORIZED, "invalid admin key"))
            .when(guard).verify(nullable(String.class));

        mvc.perform(post("/api/admin/coupons")
                .contentType(APPLICATION_JSON)
                .content(createCampaignJson(
                    "Python 入門", "學習 Python", "https://example.com",
                    "COUPON123", "2026-09-30", "survey-key", Map.of())))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail").value("invalid admin key"));
    }

    // ========== 列表測試 ==========

    /** 列表回 200，按建立時間倒序排列 */
    @Test
    void 列表回200並按倒序排列() throws Exception {
        // 建立 mock campaign 物件，模擬 DB 返回的結果
        var campaign1 = new CouponCampaign("課程1", "文案1", "https://example.com", "CODE1", null, "form1", "{}");
        var campaign2 = new CouponCampaign("課程2", "文案2", "https://example.com", "CODE2", null, "form2", "{}");

        // 模擬反射設定 id（mock 物件用來測試序列化，不需要實際 DB id）
        when(campaignRepository.findAllByOrderByCreatedAtDesc())
            .thenReturn(List.of(campaign2, campaign1)); // 新到舊排序

        mvc.perform(get("/api/admin/coupons")
                .header("X-Admin-Key", "valid-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].courseName").value("課程2"))
            .andExpect(jsonPath("$[1].courseName").value("課程1"));
    }

    // ========== Preview 收件人測試 ==========

    /** preview-recipients 回 200，含 email、name、alreadySent */
    @Test
    void previewRecipients回200帶收件人清單() throws Exception {
        var campaign = new CouponCampaign("課程", "文案", "https://example.com", "CODE", null, "form", "{}");
        var recipient1 = new CouponRecipientService.Recipient("user1@example.com", "用戶1", false);
        var recipient2 = new CouponRecipientService.Recipient("user2@example.com", "用戶2", true);

        when(campaignRepository.findById(1L)).thenReturn(java.util.Optional.of(campaign));
        when(recipientService.resolve(campaign)).thenReturn(List.of(recipient1, recipient2));

        mvc.perform(post("/api/admin/coupons/1/preview-recipients")
                .header("X-Admin-Key", "valid-key")
                .contentType(APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].email").value("user1@example.com"))
            .andExpect(jsonPath("$[0].name").value("用戶1"))
            .andExpect(jsonPath("$[0].alreadySent").value(false))
            .andExpect(jsonPath("$[1].email").value("user2@example.com"))
            .andExpect(jsonPath("$[1].alreadySent").value(true));
    }

    /** preview-recipients 找不到活動回 404 */
    @Test
    void previewRecipients活動不存在回404() throws Exception {
        when(campaignRepository.findById(999L)).thenReturn(java.util.Optional.empty());

        mvc.perform(post("/api/admin/coupons/999/preview-recipients")
                .header("X-Admin-Key", "valid-key")
                .contentType(APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").exists());
    }

    // ========== 寄送測試 ==========

    /** send 回 200 帶 SendResult */
    @Test
    void send回200帶寄送結果() throws Exception {
        var sendResult = new CouponSendService.SendResult(2, 2, 0, 0, 0);

        when(sendService.send(1L, List.of("user1@example.com", "user2@example.com"), 100))
            .thenReturn(sendResult);

        mvc.perform(post("/api/admin/coupons/1/send")
                .header("X-Admin-Key", "valid-key")
                .contentType(APPLICATION_JSON)
                .content(sendJson(List.of("user1@example.com", "user2@example.com"), 100)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.attempted").value(2))
            .andExpect(jsonPath("$.sent").value(2))
            .andExpect(jsonPath("$.skipped").value(0))
            .andExpect(jsonPath("$.failed").value(0))
            .andExpect(jsonPath("$.remaining").value(0));
    }

    /** send 活動不存在拋 404，透傳為 404 JSON 回應 */
    @Test
    void send活動不存在透傳404() throws Exception {
        when(sendService.send(anyLong(), anyList(), nullable(Integer.class)))
            .thenThrow(new ResponseStatusException(NOT_FOUND, "找不到指定優惠券活動"));

        mvc.perform(post("/api/admin/coupons/999/send")
                .header("X-Admin-Key", "valid-key")
                .contentType(APPLICATION_JSON)
                .content(sendJson(List.of("user@example.com"), 100)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("找不到指定優惠券活動"));
    }

    /** send 名單驗證失敗拋 400，透傳為 400 JSON 回應 */
    @Test
    void send名單驗證失敗透傳400() throws Exception {
        when(sendService.send(anyLong(), anyList(), nullable(Integer.class)))
            .thenThrow(new ResponseStatusException(BAD_REQUEST, "以下收件人不在本活動命中名單內，拒絕寄送：bad@example.com"));

        mvc.perform(post("/api/admin/coupons/1/send")
                .header("X-Admin-Key", "valid-key")
                .contentType(APPLICATION_JSON)
                .content(sendJson(List.of("bad@example.com"), 100)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").exists());
    }

    // ========== 預覽信件測試（spec §8.1，不落庫） ==========

    /** preview-mail 回 200，html 內含優惠碼與課程名，subject 內含課程名 */
    @Test
    void previewMail回200帶主旨與含優惠碼的html() throws Exception {
        mvc.perform(post("/api/admin/coupons/preview-mail")
                .header("X-Admin-Key", "valid-key")
                .contentType(APPLICATION_JSON)
                .content(previewMailJson(
                    "Python 入門", "學習 Python 的好夥伴", "https://example.com/course",
                    "SAVE300", "2026-09-30", "survey-key")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subject").value(org.hamcrest.Matchers.containsString("Python 入門")))
            .andExpect(jsonPath("$.html").value(org.hamcrest.Matchers.containsString("SAVE300")))
            .andExpect(jsonPath("$.html").value(org.hamcrest.Matchers.containsString("Python 入門")));
    }

    /** preview-mail 缺 X-Admin-Key 回 401，不觸發任何渲染 */
    @Test
    void previewMail缺Admin金鑰回401() throws Exception {
        doThrow(new ResponseStatusException(UNAUTHORIZED, "invalid admin key"))
            .when(guard).verify(nullable(String.class));

        mvc.perform(post("/api/admin/coupons/preview-mail")
                .contentType(APPLICATION_JSON)
                .content(previewMailJson(
                    "Python 入門", "學習 Python 的好夥伴", "https://example.com/course",
                    "SAVE300", "2026-09-30", "survey-key")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail").value("invalid admin key"));
    }
}
