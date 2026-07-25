package world.springai.survey.reader;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
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

    private final LoginMailService loginMailService;
    private final LoginTokenService loginTokenService;
    private final ReaderAccountService readerAccountService;
    private final ReaderSessionService sessionService;
    private final ReaderRepository readerRepository;
    private final HtmlTemplate htmlTemplate;

    /** 注入登入流程所需的服務 */
    public ReaderAuthController(LoginMailService loginMailService,
                               LoginTokenService loginTokenService,
                               ReaderAccountService readerAccountService,
                               ReaderSessionService sessionService,
                               ReaderRepository readerRepository,
                               HtmlTemplate htmlTemplate) {
        this.loginMailService = loginMailService;
        this.loginTokenService = loginTokenService;
        this.readerAccountService = readerAccountService;
        this.sessionService = sessionService;
        this.readerRepository = readerRepository;
        this.htmlTemplate = htmlTemplate;
    }

    /** 登入請求：email 必填且需為合法格式，redirect 選填 */
    public record LoginRequest(@NotBlank @Email String email, String redirect) {}

    /** 登入頁（無動態內容，但需由 controller 提供以支援 /r/login 這種無副檔名路徑） */
    @GetMapping(value = "/r/login", produces = MediaType.TEXT_HTML_VALUE)
    public String loginPage() {
        return htmlTemplate.render("static/reader/login.html",
            Map.of("<!--PAGE_TITLE-->", "登入｜凱文大叔的電子報"));
    }

    /** 訂閱入口頁 */
    @GetMapping(value = "/r/", produces = MediaType.TEXT_HTML_VALUE)
    public String indexPage() {
        return htmlTemplate.render("static/reader/index.html", Map.of());
    }

    /**
     * 請求登入信。
     *
     * <p>回傳 sent / throttled 兩個布林讓前端顯示不同訊息——寄送失敗時不可顯示
     * 成功假象（spec §6），讀者正在等這封信。</p>
     */
    @PostMapping("/api/reader/login")
    public Map<String, Boolean> requestLogin(@Valid @RequestBody LoginRequest request) {
        LoginMailService.SendResult result =
            loginMailService.sendLoginLink(request.email(), request.redirect(), OffsetDateTime.now());
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

    /** 由 session cookie 取出目前登入的讀者；無效一律視為未登入 */
    private Optional<Reader> currentReader(String sessionCookie) {
        return sessionService.readReaderId(sessionCookie, OffsetDateTime.now())
            .flatMap(readerRepository::findById);
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
