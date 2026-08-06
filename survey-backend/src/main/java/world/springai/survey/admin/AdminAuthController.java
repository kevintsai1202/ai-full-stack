package world.springai.survey.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import world.springai.survey.reader.LoginToken;
import world.springai.survey.reader.LoginTokenService;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 管理後台認證端點。
 *
 * <p>全部掛在 {@code /api/admin/} 之下，天然通過 {@code AdminEntryHostFilter} 的
 * 路徑白名單，不需修改該 filter。</p>
 */
@RestController
public class AdminAuthController {

    /** 寄送管理後台登入信（只寄給白名單，且對外表現一律相同） */
    private final AdminLoginMailService loginMailService;
    /** 一次性登入 token 的兌換服務 */
    private final LoginTokenService tokenService;
    /** 管理者白名單，用於 token 兌換後與 me 端點的二次比對 */
    private final AdminAllowlist allowlist;
    /** 簽發 JWT 與組 session cookie */
    private final AdminSessionService sessionService;
    /** 從請求 cookie 讀出目前登入的管理者 */
    private final AdminSessionAccess sessionAccess;

    /** 注入登入信、token、白名單、session 服務與 cookie 讀取器 */
    public AdminAuthController(AdminLoginMailService loginMailService,
                               LoginTokenService tokenService,
                               AdminAllowlist allowlist,
                               AdminSessionService sessionService,
                               AdminSessionAccess sessionAccess) {
        this.loginMailService = loginMailService;
        this.tokenService = tokenService;
        this.allowlist = allowlist;
        this.sessionService = sessionService;
        this.sessionAccess = sessionAccess;
    }

    /** 登入請求內容 */
    public record LoginRequest(@NotBlank @Email String email) {}

    /**
     * 請求後台登入信。
     *
     * <p><b>一律回相同結果</b>，不論該 email 是否為管理者——回應若有差異，
     * 這個端點就會變成管理者名單的查詢工具。</p>
     */
    @PostMapping("/api/admin/login")
    public Map<String, Boolean> requestLogin(@Valid @RequestBody LoginRequest request) {
        loginMailService.sendIfAdmin(request.email(), OffsetDateTime.now());
        return Map.of("accepted", true);
    }

    /**
     * 承接 magic link：兌換 admin 用途的 token、二次比對白名單後種 cookie。
     *
     * <p>白名單來自環境變數，可能在 token 簽發後被調整，故此處必須再次比對，
     * 確保已撤下的信箱無法用手上的舊連結登入。</p>
     */
    @GetMapping("/api/admin/login/verify")
    public ResponseEntity<Void> verifyLogin(@RequestParam("t") String token) {
        OffsetDateTime now = OffsetDateTime.now();
        Optional<String> email = tokenService.consume(token, LoginToken.PURPOSE_ADMIN, now);

        if (email.isEmpty() || !allowlist.isAdmin(email.get())) {
            return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, "/admin.html?error=invalid")
                .build();
        }

        String jwt = sessionService.issueJwt(email.get(), now);
        return ResponseEntity.status(302)
            .header(HttpHeaders.LOCATION, "/admin.html")
            .header(HttpHeaders.SET_COOKIE, sessionService.buildSessionCookie(jwt).toString())
            .build();
    }

    /** 登出：清除 session cookie */
    @PostMapping("/api/admin/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, sessionService.buildClearCookie().toString())
            .build();
    }

    /**
     * 回傳目前登入者；未登入回 401，供前端決定是否顯示登入 gate。
     *
     * <p>JWT 簽章有效仍須二次比對白名單：管理者換人後舊 token 在效期內仍會通過
     * 簽章驗證，若不比對白名單，被撤下的管理者可繼續使用後台直到 token 自然過期。</p>
     */
    @GetMapping("/api/admin/me")
    public ResponseEntity<Map<String, Object>> me(HttpServletRequest request) {
        Optional<String> email = sessionAccess.readEmail(request, OffsetDateTime.now());
        if (email.isEmpty() || !allowlist.isAdmin(email.get())) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(Map.of("email", email.get(), "mode", "jwt"));
    }
}
