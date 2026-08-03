package world.springai.survey.form;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import world.springai.survey.promo.PromoRecipientTokenService;
import world.springai.survey.reader.CreditPolicy;
import world.springai.survey.reader.HtmlTemplate;
import world.springai.survey.reader.Reader;
import world.springai.survey.reader.ReaderRepository;
import world.springai.survey.reader.ReaderSessionService;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 讀者接續填答頁 {@code GET /r/survey/{formKey}}：常由電子報信件（一鍵題轉址、
 * 轉寄連結）進入，渲染 schema 動態表單讓讀者補完整份問卷。
 *
 * <p><b>身分完全由後端解析</b>：優先採 {@code rt} token（信中收件人），其次讀者
 * session cookie；兩者皆無效視為匿名訪客，只顯示登入引導、表單唯讀不可送出——
 * 與 {@link NewsletterSubmissionService} 的身分解析順序一致，讀者在頁面上看到的
 * 身分狀態與實際送出時後端認定的身分不會有落差。</p>
 *
 * <p><b>Email 遮罩</b>：頁面上只顯示遮罩後的 email（{@link #maskEmail}），完整 email
 * 絕不進入回應——本頁常見的散布途徑是信件轉寄，若把完整 email 印在 HTML 裡，
 * 收到轉寄信的第三人開啟連結就會看到別人的信箱地址。</p>
 *
 * <p><b>Cache-Control: private, no-store</b>：回應內容含身分遮罩與登入引導等
 * 與訪客身分相關的資訊，不可被瀏覽器或任何共用快取保存。</p>
 */
@RestController
public class SurveyPortalController {

    private final FormSchemaService formSchemaService;
    private final PromoRecipientTokenService tokenService;
    private final ReaderSessionService sessionService;
    private final ReaderRepository readerRepository;
    private final CreditPolicy creditPolicy;
    private final HtmlTemplate htmlTemplate;
    private final ObjectMapper objectMapper;

    /** 注入表單 schema、身分解析（token／session）、點數規則與頁面渲染工具 */
    public SurveyPortalController(
            FormSchemaService formSchemaService,
            PromoRecipientTokenService tokenService,
            ReaderSessionService sessionService,
            ReaderRepository readerRepository,
            CreditPolicy creditPolicy,
            HtmlTemplate htmlTemplate,
            ObjectMapper objectMapper) {
        this.formSchemaService = formSchemaService;
        this.tokenService = tokenService;
        this.sessionService = sessionService;
        this.readerRepository = readerRepository;
        this.creditPolicy = creditPolicy;
        this.htmlTemplate = htmlTemplate;
        this.objectMapper = objectMapper;
    }

    /** 前端動態產生表單所需的最小欄位描述；不外洩 analytics／sensitive 等後台專用欄位。 */
    private record PublicField(String key, String label, String type, boolean required, List<Object> options) {}

    /**
     * 渲染接續填答頁；問卷未發布時 {@link FormSchemaService#getDefinition} 會拋
     * {@code ResponseStatusException(404)}，直接讓例外透傳交給全域例外處理器。
     */
    @GetMapping("/r/survey/{formKey}")
    public ResponseEntity<String> survey(
            @PathVariable String formKey,
            @RequestParam(required = false) String voted,
            @RequestParam(required = false) String c,
            @RequestParam(required = false) String rt,
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {
        FormSchemaService.FormDefinition form = formSchemaService.getDefinition(formKey, null);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Optional<String> identifiedEmail = resolveIdentity(rt, sessionCookie, now);
        int rewardCredits = creditPolicy.surveyReward();

        Map<String, String> vars = new HashMap<>();
        vars.put("<!--FORM_TITLE-->", HtmlTemplate.escapeHtml(form.title()));
        vars.put("<!--VOTED_BANNER-->", votedBanner(voted));
        vars.put("<!--IDENTITY_BLOCK-->", identityBlock(identifiedEmail, formKey, rewardCredits));
        vars.put("<!--FIELDS_JSON-->", toJsLiteral(publicFields(form.fields())));
        vars.put("<!--FORM_KEY-->", toJsLiteral(formKey));
        vars.put("<!--REWARD_CREDITS-->", toJsLiteral(rewardCredits));
        vars.put("<!--CAMPAIGN_ID-->", toJsLiteral(parseLong(c)));
        vars.put("<!--RT-->", toJsLiteral(rt));
        vars.put("<!--VOTED_INDEX-->", toJsLiteral(parseInt(voted)));
        vars.put("<!--VOTE_FIELD_KEY-->", toJsLiteral(form.emailVoteFieldKey()));

        String html = htmlTemplate.render("templates/reader/survey.html", vars);
        // charset 必須明講：只給 text/html 時 StringHttpMessageConverter 可能以
        // ISO-8859-1 解讀，中文會變亂碼（同 PromoClickController.respond）
        return ResponseEntity.ok()
            .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
            // 含身分遮罩與登入引導，不可被任何快取保存
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .body(html);
    }

    /** rt 優先解析（信中收件人），其次讀者 session；兩者皆無效視為匿名。 */
    private Optional<String> resolveIdentity(String rt, String sessionCookie, OffsetDateTime now) {
        Optional<String> rtEmail = tokenService.verify(rt);
        if (rtEmail.isPresent()) {
            return rtEmail;
        }
        return sessionService.readReaderId(sessionCookie, now)
            .flatMap(readerRepository::findById)
            .map(Reader::getEmail);
    }

    /** 有 voted 參數（即使空字串）才顯示已收到投票的提示區塊。 */
    private String votedBanner(String voted) {
        if (voted == null) {
            return "";
        }
        return "<div class=\"msg ok show\">✅ 已收到你的投票，歡迎補充更多想法！</div>";
    }

    /**
     * 身分區塊：已識別（rt 或 session）顯示遮罩 email＋「不是你？」；
     * 未歸戶顯示登入引導。{@code data-identified} 供前端 JS 判斷表單是否唯讀。
     */
    private String identityBlock(Optional<String> email, String formKey, int rewardCredits) {
        String loginHref = "/r/login?redirect=" + HtmlTemplate.escapeHtml("/r/survey/" + formKey);
        if (email.isPresent()) {
            return """
                <div class="identity-block" data-identified="true">
                  <p>以 <strong>%s</strong> 身分作答。<a href="%s">不是你？</a></p>
                </div>
                """.formatted(HtmlTemplate.escapeHtml(maskEmail(email.get())), loginHref);
        }
        return """
            <div class="identity-block" data-identified="false">
              <p>登入作答可獲得 %d 點！<a href="%s">前往登入 / 訂閱</a></p>
            </div>
            """.formatted(rewardCredits, loginHref);
    }

    /**
     * 遮罩 email 供頁面顯示：本地部分僅留首字元＋***，網域完整保留。
     *
     * <p>完整 email 絕不可回傳——本頁常經信件轉寄流通，若印出完整地址，
     * 收到轉寄信的陌生人開啟連結就會看到原收件人的信箱。</p>
     */
    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***@" + email.substring(at + 1);
    }

    /** 投影 schema 欄位為前端所需的最小描述，維持與 Admin 專用欄位（sensitive 等）隔離。 */
    private List<PublicField> publicFields(List<FormSchemaService.FieldDefinition> fields) {
        return fields.stream()
            .map(field -> new PublicField(
                field.key(), field.label(), field.type(), field.required(), field.options()))
            .toList();
    }

    /**
     * 序列化成可安全內嵌 {@code <script>} 的 JS 字面值：{@code null} 值序列化為
     * JS 的 {@code null}，字串會帶引號。{@code </} 一律轉成 {@code <\/}，避免值中
     * 恰好含 {@code </script>} 時提前關閉標籤（HTML 解析先於 JS/JSON 語法）。
     */
    private String toJsLiteral(Object value) {
        try {
            return objectMapper.writeValueAsString(value).replace("</", "<\\/");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("無法序列化頁面內嵌資料", e);
        }
    }

    /** 寬鬆解析 campaignId；缺漏或非數字一律回 null，不擋頁面渲染。 */
    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** 寬鬆解析 voted 選項索引；缺漏或非數字一律回 null。 */
    private Integer parseInt(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
