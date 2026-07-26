package world.springai.survey.reader;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import world.springai.survey.audience.SurveyResponse;
import world.springai.survey.audience.SurveyResponseRepository;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 讀者自助頁面：我的帳戶與我的邀請。
 *
 * <p>與 {@link ReaderPageController}（內容頁）、{@link ReaderAuthController}
 * （登入流程）分開：這裡處理的是「讀者對自己帳戶的操作」，依賴組合完全不同。</p>
 *
 * <p>頁面而非 API：未登入時<b>導向登入頁並帶 redirect</b>，而不是回 401——
 * 讀者在瀏覽器看到空白的 401 是死路。</p>
 */
@RestController
public class ReaderPortalController {

    /** 日期顯示格式 */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final HtmlTemplate htmlTemplate;
    private final ReaderContext readerContext;
    private final CreditTxnRepository creditTxnRepository;
    private final SurveyResponseRepository surveyResponseRepository;
    private final ReferralService referralService;
    private final CreditPolicy creditPolicy;

    /** 注入渲染、身分解析、帳本、名單中心、邀請統計與點數參數 */
    public ReaderPortalController(HtmlTemplate htmlTemplate,
                                 ReaderContext readerContext,
                                 CreditTxnRepository creditTxnRepository,
                                 SurveyResponseRepository surveyResponseRepository,
                                 ReferralService referralService,
                                 CreditPolicy creditPolicy) {
        this.htmlTemplate = htmlTemplate;
        this.readerContext = readerContext;
        this.creditTxnRepository = creditTxnRepository;
        this.surveyResponseRepository = surveyResponseRepository;
        this.referralService = referralService;
        this.creditPolicy = creditPolicy;
    }

    /** 個人資料更新請求；目前只開放顯示名稱 */
    public record ProfileRequest(String name) {}

    /** 我的帳戶：餘額、方案、交易明細、顯示名稱編輯 */
    @GetMapping(value = "/r/me", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> me(
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {
        Optional<ReaderContext.Current> current = readerContext.resolve(sessionCookie);
        if (current.isEmpty()) {
            return redirectToLogin("/r/me");
        }
        Reader reader = current.get().reader();

        Map<String, String> vars = new HashMap<>();
        vars.put("<!--NAV_LINKS-->", "<a href=\"/r/archive\">歷史內容</a><a href=\"/r/invite\">我的邀請</a>");
        vars.put("<!--CREDITS-->", String.valueOf(reader.getCredits()));
        vars.put("<!--PREMIUM_COST-->", String.valueOf(creditPolicy.premiumCost()));
        vars.put("<!--EMAIL-->", HtmlTemplate.escapeHtml(reader.getEmail()));
        vars.put("<!--TIER_STATUS-->", renderTierStatus(reader));
        vars.put("<!--DISPLAY_NAME-->", HtmlTemplate.escapeHtml(displayNameOf(reader.getEmail())));
        vars.put("<!--TXN_LIST-->", renderTransactions(
            creditTxnRepository.findByReaderIdOrderByCreatedAtDesc(reader.getId())));

        return privatePage(htmlTemplate.render("static/reader/me.html", vars));
    }

    /**
     * 更新顯示名稱。
     *
     * <p>名單中查無此 email 時回 404 而<b>不建新列</b>：建列會讓「讀者維護
     * 個人資訊」變成「讀者可往名單中心插資料」，而名單中心的每一列都代表
     * 一份同意紀錄。</p>
     */
    @PostMapping("/api/reader/profile")
    public ResponseEntity<Void> updateProfile(
            @RequestBody ProfileRequest request,
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {
        Optional<ReaderContext.Current> current = readerContext.resolve(sessionCookie);
        if (current.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String email = current.get().reader().getEmail();

        Optional<SurveyResponse> row =
            surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(email);
        if (row.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // 只截斷不拒絕：使用者輸入超長名稱時默默存前 40 字比回 400 友善，
        // 而顯示名稱沒有任何正確性要求
        String name = request.name() == null ? "" : request.name().trim();
        row.get().setName(name.length() > 40 ? name.substring(0, 40) : name);
        surveyResponseRepository.save(row.get());
        // 更新個人資料是高可靠的參與度訊號（spec §5.10）
        surveyResponseRepository.touchEngagement(email, OffsetDateTime.now());

        return ResponseEntity.ok().build();
    }

    /** 導向登入頁並帶回跳目標 */
    private ResponseEntity<String> redirectToLogin(String target) {
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, "/r/login?redirect=" + target)
            .build();
    }

    /**
     * 個人頁面的回應標頭。
     *
     * <p>內容含餘額與交易明細，絕不可被共享快取（CDN、app-gateway 反向代理）
     * 拿去餵給別的讀者。</p>
     */
    private ResponseEntity<String> privatePage(String html) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
            .body(html);
    }

    /**
     * 描述方案狀態。
     *
     * <p>本系統刻意不做 VIP 自動降級排程（spec §13.5），所以資料庫裡會有
     * 「tier=VIP 但已過期」的列。這裡以 {@link Reader#isActiveVip} 判斷而非
     * 直接看 tier——否則讀者會以為自己還是 VIP，然後在解鎖時發現要扣點。</p>
     */
    private String renderTierStatus(Reader reader) {
        OffsetDateTime now = OffsetDateTime.now();
        if (reader.isActiveVip(now)) {
            return reader.getVipExpiresAt() == null
                ? "VIP（無到期日）"
                : "VIP（有效至 " + reader.getVipExpiresAt().format(DATE_FORMAT) + "）";
        }
        if (Reader.TIER_VIP.equals(reader.getTier())) {
            return "VIP 已到期，目前為一般訂閱者";
        }
        return "一般訂閱者";
    }

    /** 從名單中心取顯示名稱；查無資料回空字串 */
    private String displayNameOf(String email) {
        return surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(email)
            .map(SurveyResponse::getName)
            .orElse("");
    }

    /** 渲染交易明細；note 一律跳脫（存的是後台輸入的文章主旨與 email） */
    private String renderTransactions(List<CreditTxn> transactions) {
        if (transactions.isEmpty()) {
            return "<p class=\"empty\">還沒有交易紀錄。</p>";
        }
        StringBuilder sb = new StringBuilder("<ul class=\"txn-list\">");
        for (CreditTxn txn : transactions) {
            String sign = txn.getDelta() >= 0 ? "+" : "";
            String cls = txn.getDelta() >= 0 ? "gain" : "spend";
            sb.append("<li class=\"txn-item\">")
              .append("<span class=\"txn-delta ").append(cls).append("\">")
              .append(sign).append(txn.getDelta()).append("</span>")
              .append("<span class=\"txn-reason\">").append(reasonLabel(txn.getReason())).append("</span>")
              .append("<span class=\"txn-note\">").append(HtmlTemplate.escapeHtml(txn.getNote())).append("</span>")
              .append("<span class=\"txn-date\">")
              .append(txn.getCreatedAt() == null ? "" : txn.getCreatedAt().format(DATE_FORMAT))
              .append("</span></li>");
        }
        return sb.append("</ul>").toString();
    }

    /** 把交易原因代碼轉成讀者看得懂的中文 */
    private String reasonLabel(String reason) {
        return switch (reason) {
            case CreditTxn.REASON_SIGNUP_GRANT -> "初始贈點";
            case CreditTxn.REASON_REFERRAL -> "邀請獎勵";
            case CreditTxn.REASON_READ -> "解鎖文章";
            case CreditTxn.REASON_ADMIN_GRANT -> "站方贈點";
            // 未知代碼原樣顯示：新增 reason 時忘記加對應中文，
            // 顯示代碼比顯示空字串好——讀者看得到「有這筆」而非憑空消失
            default -> reason;
        };
    }
}
