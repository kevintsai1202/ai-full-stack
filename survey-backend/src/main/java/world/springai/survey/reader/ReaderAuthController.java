package world.springai.survey.reader;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import world.springai.survey.ReaderSiteLinks;
import world.springai.survey.form.FormSchemaService;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 讀者登入相關端點：magic link 請求與驗證、登出、帳戶資訊。
 *
 * <p>與內容頁面分成兩個 controller（另一個是 ReaderPageController）：合在一起
 * 需要注入十個依賴，違反單一任務原則。</p>
 */
@RestController
public class ReaderAuthController {

    /** 登入成功後的預設落點 */
    private static final String DEFAULT_REDIRECT = "/r/archive";

    /** 未登入時的訂閱表單區塊（自 index.html 原樣搬入；行為由頁內既有 script 驅動） */
    private static final String SUBSCRIBE_FORM_HTML = """
        <p class="muted">填入 Email，訂閱立即生效，之後會收到一封歡迎信。</p>
        <form id="subscribe-form" class="form-row">
          <input type="email" id="email" name="email" placeholder="your@email.com" required autocomplete="email">
          <button class="btn" type="submit">訂閱</button>
        </form>
        <div class="msg" id="msg"></div>""";

    private final LoginMailService loginMailService;
    private final LoginTokenService loginTokenService;
    private final ReaderAccountService readerAccountService;
    private final ReaderSessionService sessionService;
    private final ReaderContext readerContext;
    private final HtmlTemplate htmlTemplate;
    private final LoginAbuseGuard loginAbuseGuard;
    /** 首頁問卷列表（A4）所需：查詢後台勾選曝光且已發布的問卷 */
    private final FormSchemaService formSchemaService;
    /**
     * 點數規則：首頁問卷區塊的贈點說明必須取自與實際發點同一個來源
     * （{@link CreditPolicy#surveyReward()}），不得在頁面上寫死數字。
     */
    private final CreditPolicy creditPolicy;
    /** 首頁 canonical 標記所需的正式讀者網域（訂閱入口常帶 ?ref= 推薦碼，需收斂到標準網址） */
    private final ReaderSiteLinks readerSiteLinks;

    /** 注入登入流程所需的服務 */
    public ReaderAuthController(LoginMailService loginMailService,
                               LoginTokenService loginTokenService,
                               ReaderAccountService readerAccountService,
                               ReaderSessionService sessionService,
                               ReaderContext readerContext,
                               HtmlTemplate htmlTemplate,
                               LoginAbuseGuard loginAbuseGuard,
                               FormSchemaService formSchemaService,
                               CreditPolicy creditPolicy,
                               ReaderSiteLinks readerSiteLinks) {
        this.loginMailService = loginMailService;
        this.loginTokenService = loginTokenService;
        this.readerAccountService = readerAccountService;
        this.sessionService = sessionService;
        this.readerContext = readerContext;
        this.htmlTemplate = htmlTemplate;
        this.loginAbuseGuard = loginAbuseGuard;
        this.formSchemaService = formSchemaService;
        this.creditPolicy = creditPolicy;
        this.readerSiteLinks = readerSiteLinks;
    }

    /** 登入請求：email 必填且需為合法格式，redirect 選填 */
    public record LoginRequest(@NotBlank @Email String email, String redirect) {}

    /**
     * 登入頁。已有有效 session 時直接前往原目標，避免重寄 Magic Link；
     * 未登入才顯示表單，並禁止共享快取保存個別登入狀態。
     */
    @GetMapping(value = "/r/login", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> loginPage(
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie,
            @RequestParam(value = "redirect", required = false) String redirect) {
        if (sessionCookie != null && readerContext.resolve(sessionCookie).isPresent()) {
            return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, safeRedirect(redirect))
                .build();
        }
        String html = htmlTemplate.render("templates/reader/login.html",
            Map.of("<!--PAGE_TITLE-->", "登入｜凱文大叔的電子報"));
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
            .body(html);
    }

    /**
     * 訂閱入口頁。
     *
     * <p><b>為什麼要解析 session cookie</b>：這頁的導覽列原本寫死在
     * {@code index.html} 裡，已登入的讀者回到首頁仍看到「登入」——點下去會再寄
     * 一封 magic link 給早就登入的人。改由 {@link ReaderNav} 依登入狀態渲染。</p>
     *
     * <p><b>代價</b>：回應內容從此依 cookie 而異，因此必須改回
     * {@code ResponseEntity} 並帶 {@code private, no-store} + {@code Vary: Cookie}
     * ——與 {@code /r/archive}、{@code /r/rules} 同一套慣例。這頁是站台的公開
     * 入口，理論上最適合被共享快取，但它在此之前也沒有任何 {@code Cache-Control}
     * （Spring 對 {@code String} 回傳值不加標頭），所以實際上沒有失去既有的快取
     * 效益；反之，少了這兩個標頭才是真正的風險：CDN 可能把某位登入者的導覽列
     * （含「我的帳戶」）快取下來餵給匿名訪客。</p>
     */
    @GetMapping(value = "/r/", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> indexPage(
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {
        Optional<ReaderContext.Current> current = readerContext.resolve(sessionCookie);
        Map<String, String> vars = new HashMap<>();
        vars.put("<!--NAV_LINKS-->", ReaderNav.links(current.isPresent()));
        // canonical 指向不帶 ?ref=/&share= 的首頁標準網址：分享訂閱入口帶推薦碼
        // 會讓首頁有多個網址，缺 canonical 會被 GSC 判為「重複網頁」
        vars.put("<!--CANONICAL-->", readerSiteLinks.canonicalTag(readerSiteLinks.home()));
        vars.put("<!--SURVEY_LIST-->", renderSurveyList());
        vars.put("<!--SUBSCRIBE_BLOCK-->", renderSubscribeBlock(current));
        String html = htmlTemplate.render("templates/reader/index.html", vars);
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
            .body(html);
    }

    /**
     * 請求登入信。
     *
     * <p>回傳 sent / throttled 兩個布林讓前端顯示不同訊息——寄送失敗時不可顯示
     * 成功假象（spec §6），讀者正在等這封信。</p>
     */
    @PostMapping("/api/reader/login")
    public Map<String, Boolean> requestLogin(@Valid @RequestBody LoginRequest request,
                                             HttpServletRequest servletRequest) {
        OffsetDateTime now = OffsetDateTime.now();
        if (!loginAbuseGuard.tryAcquire(servletRequest.getRemoteAddr(), now)) {
            return Map.of("sent", false, "throttled", true);
        }
        LoginMailService.SendResult result =
            loginMailService.sendLoginLink(request.email(), request.redirect(), now);
        return Map.of("sent", result.sent(), "throttled", result.throttled());
    }

    /**
     * 承接 magic link：兌換 token、建立或取得帳戶、簽發 session cookie 後導向。
     *
     * <p>token 無效（不存在／過期／已使用）時導向登入頁並帶 error 標記，不設 cookie，
     * 也不建立任何帳戶。</p>
     */
    @GetMapping("/api/reader/login/verify")
    public ResponseEntity<Void> verifyLogin(@RequestParam("t") String token,
                                           @RequestParam(value = "redirect", required = false) String redirect) {
        OffsetDateTime now = OffsetDateTime.now();
        Optional<String> email = loginTokenService.consume(token, now);

        if (email.isEmpty()) {
            return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, "/r/login?error=invalid")
                .build();
        }

        Reader reader = readerAccountService.findOrCreate(email.get(), now);
        String jwt = sessionService.issueJwt(reader.getId(), now);

        return ResponseEntity.status(302)
            .header(HttpHeaders.LOCATION, safeRedirect(redirect))
            .header(HttpHeaders.SET_COOKIE, sessionService.buildSessionCookie(jwt).toString())
            .build();
    }

    /** 登出：清除 session cookie */
    @PostMapping("/api/reader/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, sessionService.buildClearCookie().toString())
            .build();
    }

    /** 目前登入者的帳戶資訊；未登入回 401 */
    @GetMapping("/api/reader/me")
    public ResponseEntity<Map<String, Object>> me(
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {
        Optional<Reader> reader = currentReader(sessionCookie);
        if (reader.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        Reader r = reader.get();
        return ResponseEntity.ok(Map.of(
            "email", r.getEmail(),
            "tier", r.getTier(),
            "credits", r.getCredits(),
            "referralCode", r.getReferralCode()));
    }

    /** 首頁問卷列表（A4）：列出後台勾選曝光的問卷；無任何曝光問卷時回空字串，整個區塊不顯示 */
    private String renderSurveyList() {
        List<FormSchemaService.HomepageForm> forms = formSchemaService.listHomepageForms();
        if (forms.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("<div class=\"card\"><h2 class=\"section-title\">問卷調查</h2>");
        // 贈點說明：數字取自 CreditPolicy（與實際發點同源）；獎勵關閉（0 點）時整行不出現，
        // 不對讀者許下不會兌現的承諾。條件寫「登入填答」——匿名填答不發點，措辭不可省略前提。
        int reward = creditPolicy == null ? 0 : creditPolicy.surveyReward();
        if (reward > 0) {
            sb.append("<p class=\"survey-reward-note\">登入填答完成問卷，即贈 ").append(reward)
              .append(" 點——可用於解鎖進階文章</p>");
        }
        sb.append("<ul class=\"survey-list\">");
        for (FormSchemaService.HomepageForm form : forms) {
            sb.append("<li><a href=\"/r/survey/").append(HtmlTemplate.escapeHtml(form.key()))
              .append("\">").append(HtmlTemplate.escapeHtml(form.title())).append("</a></li>");
        }
        return sb.append("</ul></div>").toString();
    }

    /** 首頁身分區（A3）：登入顯示「已訂閱：email」（經跳脫），未登入顯示訂閱表單 */
    private String renderSubscribeBlock(Optional<ReaderContext.Current> current) {
        return current
            .map(c -> "<p class=\"identity-line\">已訂閱：<strong>"
                + HtmlTemplate.escapeHtml(c.reader().getEmail()) + "</strong></p>")
            .orElse(SUBSCRIBE_FORM_HTML);
    }

    /** 由 session cookie 取出目前登入的讀者；無效一律視為未登入 */
    private Optional<Reader> currentReader(String sessionCookie) {
        return readerContext.resolve(sessionCookie).map(ReaderContext.Current::reader);
    }

    /**
     * 只接受站內相對路徑作為導向目標，否則回預設落點。
     *
     * <p>判斷邏輯委派給 {@code RedirectGuard}（見 Step 0），**不要在這裡自己寫
     * 前綴比對**。原因見該類的註解：{@code /\evil.com} 這種反斜線變體會通過
     * 「以 / 開頭且不以 // 開頭」的檢查，但瀏覽器把 \ 與 / 視為等價，等同
     * {@code //evil.com}。這個漏洞已在 LoginMailService 上實際發生過一次，
     * 所以兩處必須共用同一份實作，不得各寫一份。</p>
     */
    private String safeRedirect(String redirect) {
        return RedirectGuard.isSafe(redirect) ? redirect.trim() : DEFAULT_REDIRECT;
    }
}
