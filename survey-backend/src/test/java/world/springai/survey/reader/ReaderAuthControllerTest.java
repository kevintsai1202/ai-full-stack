package world.springai.survey.reader;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import world.springai.survey.form.FormSchemaService;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ReaderAuthController 行為測試：登入請求、magic link 驗證、cookie 設定、登出、redirect 安全 */
@WebMvcTest(ReaderAuthController.class)
@Import({HtmlTemplate.class, ReaderSessionService.class, world.springai.survey.ReaderSiteLinks.class})
@TestPropertySource(properties = {
    "app.reader.jwt-secret=test-secret-key-at-least-32-bytes-long!!",
    "app.reader.jwt-ttl-days=28",
    "app.public-base-url=https://news.example.com",
    "app.cors-allowed-origins=http://localhost"
})
class ReaderAuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ReaderSessionService sessionService;

    @MockBean LoginMailService loginMailService;
    @MockBean LoginTokenService loginTokenService;
    @MockBean ReaderAccountService readerAccountService;
    @MockBean ReaderContext readerContext;
    @MockBean LoginAbuseGuard loginAbuseGuard;
    @MockBean FormSchemaService formSchemaService;
    /** 首頁問卷區塊贈點說明所需（@WebMvcTest 切片不含 @Component，須以 mock 補上） */
    @MockBean CreditPolicy creditPolicy;

    /** 建立一位讀者 */
    private Reader reader() {
        Reader r = new Reader("user@example.com", "CODE1234");
        r.setId(1L);
        r.setCredits(300);
        return r;
    }

    /** 登入請求成功時回 sent=true */
    @Test
    void loginRequestReportsSent() throws Exception {
        when(loginAbuseGuard.tryAcquire(anyString(), any())).thenReturn(true);
        when(loginMailService.sendLoginLink(eq("user@example.com"), any(), any()))
            .thenReturn(new LoginMailService.SendResult(true, false));

        mvc.perform(post("/api/reader/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.sent").value(true))
           .andExpect(jsonPath("$.throttled").value(false));
    }

    /** 節流時回 throttled=true，前端要顯示不同訊息 */
    @Test
    void loginRequestReportsThrottled() throws Exception {
        when(loginAbuseGuard.tryAcquire(anyString(), any())).thenReturn(true);
        when(loginMailService.sendLoginLink(anyString(), any(), any()))
            .thenReturn(new LoginMailService.SendResult(false, true));

        mvc.perform(post("/api/reader/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.sent").value(false))
           .andExpect(jsonPath("$.throttled").value(true));
    }

    /** email 格式無效回 400，且不寄信 */
    @Test
    void invalidEmailIsRejected() throws Exception {
        mvc.perform(post("/api/reader/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\"}"))
           .andExpect(status().isBadRequest());

        verify(loginMailService, never()).sendLoginLink(anyString(), any(), any());
    }

    /** 同一來源 IP 或全站達上限時，不得進入簽發與寄信服務。 */
    @Test
    void abuseGuardStopsLoginMailBeforeTokenIssuance() throws Exception {
        when(loginAbuseGuard.tryAcquire(anyString(), any())).thenReturn(false);

        mvc.perform(post("/api/reader/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.sent").value(false))
           .andExpect(jsonPath("$.throttled").value(true));

        verify(loginMailService, never()).sendLoginLink(anyString(), any(), any());
    }

    /** magic link 驗證成功：設定 session cookie 並 302 導向 */
    @Test
    void verifyValidTokenSetsCookieAndRedirects() throws Exception {
        when(loginTokenService.consume(eq("GOOD-TOKEN"), any()))
            .thenReturn(Optional.of("user@example.com"));
        when(readerAccountService.findOrCreate(eq("user@example.com"), any())).thenReturn(reader());

        mvc.perform(get("/api/reader/login/verify").param("t", "GOOD-TOKEN"))
           .andExpect(status().isFound())
           .andExpect(header().string("Location", "/r/archive"))
           .andExpect(cookie().exists(ReaderSessionService.COOKIE_NAME))
           .andExpect(cookie().httpOnly(ReaderSessionService.COOKIE_NAME, true));
    }

    /** 帶站內 redirect 時導向該路徑 */
    @Test
    void verifyHonoursInternalRedirect() throws Exception {
        when(loginTokenService.consume(anyString(), any())).thenReturn(Optional.of("user@example.com"));
        when(readerAccountService.findOrCreate(anyString(), any())).thenReturn(reader());

        mvc.perform(get("/api/reader/login/verify")
                .param("t", "GOOD-TOKEN")
                .param("redirect", "/r/news/hello"))
           .andExpect(status().isFound())
           .andExpect(header().string("Location", "/r/news/hello"));
    }

    /** 站外 redirect 必須被丟棄，改導向預設頁（防開放式轉址） */
    @Test
    void verifyRejectsExternalRedirect() throws Exception {
        when(loginTokenService.consume(anyString(), any())).thenReturn(Optional.of("user@example.com"));
        when(readerAccountService.findOrCreate(anyString(), any())).thenReturn(reader());

        mvc.perform(get("/api/reader/login/verify")
                .param("t", "GOOD-TOKEN")
                .param("redirect", "https://evil.example.com"))
           .andExpect(status().isFound())
           .andExpect(header().string("Location", "/r/archive"));
    }

    /**
     * protocol-relative 與反斜線變體都必須被丟棄。
     *
     * <p>反斜線變體是 Task 8 在 LoginMailService 上實際發生過的漏洞：
     * {@code /\evil.com} 的「以 / 開頭且不以 // 開頭」為真，但瀏覽器把
     * \ 與 / 視為等價，等同 {@code //evil.com}。兩種形式都要驗。</p>
     */
    @Test
    void verifyRejectsProtocolRelativeRedirect() throws Exception {
        when(loginTokenService.consume(anyString(), any())).thenReturn(Optional.of("user@example.com"));
        when(readerAccountService.findOrCreate(anyString(), any())).thenReturn(reader());

        for (String evil : new String[] {"//evil.example.com", "/\\evil.example.com", "\\\\evil.example.com"}) {
            mvc.perform(get("/api/reader/login/verify")
                    .param("t", "GOOD-TOKEN")
                    .param("redirect", evil))
               .andExpect(status().isFound())
               .andExpect(header().string("Location", "/r/archive"));
        }
    }

    /** 無效 token：導向登入頁並帶錯誤標記，不設 cookie */
    @Test
    void verifyInvalidTokenRedirectsToLoginWithoutCookie() throws Exception {
        when(loginTokenService.consume(anyString(), any())).thenReturn(Optional.empty());

        mvc.perform(get("/api/reader/login/verify").param("t", "BAD-TOKEN"))
           .andExpect(status().isFound())
           .andExpect(header().string("Location", "/r/login?error=invalid"))
           .andExpect(cookie().doesNotExist(ReaderSessionService.COOKIE_NAME));

        verify(readerAccountService, never()).findOrCreate(anyString(), any());
    }

    /** 登出：清除 cookie */
    @Test
    void logoutClearsCookie() throws Exception {
        mvc.perform(post("/api/reader/logout"))
           .andExpect(status().isNoContent())
           .andExpect(cookie().maxAge(ReaderSessionService.COOKIE_NAME, 0));
    }

    /** /api/reader/me 未登入回 401 */
    @Test
    void meRequiresLogin() throws Exception {
        mvc.perform(get("/api/reader/me"))
           .andExpect(status().isUnauthorized());
    }

    /** /api/reader/me 已登入回帳戶資訊 */
    @Test
    void meReturnsAccountInfo() throws Exception {
        when(readerContext.resolve(anyString()))
            .thenReturn(Optional.of(new ReaderContext.Current(reader(), true)));
        String jwt = sessionService.issueJwt(1L, OffsetDateTime.now());

        mvc.perform(get("/api/reader/me").cookie(
                new jakarta.servlet.http.Cookie(ReaderSessionService.COOKIE_NAME, jwt)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.email").value("user@example.com"))
           .andExpect(jsonPath("$.credits").value(300))
           .andExpect(jsonPath("$.referralCode").value("CODE1234"));
    }

    /** 登入頁可正常載入 */
    @Test
    void loginPageRenders() throws Exception {
        mvc.perform(get("/r/login"))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
           .andExpect(header().string("Cache-Control", "private, no-store"));
    }

    /** 已登入者點信件中的登入連結時，直接回到原文章，不重寄登入信。 */
    @Test
    void loginPageRedirectsLoggedInReaderToRequestedArticle() throws Exception {
        when(readerContext.resolve("VALID-SESSION"))
            .thenReturn(Optional.of(new ReaderContext.Current(reader(), true)));

        mvc.perform(get("/r/login")
                .param("redirect", "/r/news/hello-world")
                .cookie(new jakarta.servlet.http.Cookie(
                    ReaderSessionService.COOKIE_NAME, "VALID-SESSION")))
           .andExpect(status().isFound())
           .andExpect(header().string("Location", "/r/news/hello-world"));
    }

    /** 已登入者帶站外 redirect 時仍只回預設站內頁，避免登入頁成為開放式轉址。 */
    @Test
    void loginPageRejectsUnsafeRedirectForLoggedInReader() throws Exception {
        when(readerContext.resolve("VALID-SESSION"))
            .thenReturn(Optional.of(new ReaderContext.Current(reader(), true)));

        mvc.perform(get("/r/login")
                .param("redirect", "https://evil.example.com")
                .cookie(new jakarta.servlet.http.Cookie(
                    ReaderSessionService.COOKIE_NAME, "VALID-SESSION")))
           .andExpect(status().isFound())
           .andExpect(header().string("Location", "/r/archive"));
    }

    /**
     * 導覽列的連結（{@code ReaderNav} 產生的形式）。
     *
     * <p>刻意在測試裡另寫一份字串而不引用 {@code ReaderNav} 的常數：讀同一份
     * 實作的斷言恆為真，把連結刪掉也不會變紅。比對整個 {@code <a>} 標籤而不是
     * 只比對路徑——{@code index.html} 的內文本來就有一句「看遊戲規則」連向
     * {@code /r/rules}，只比對路徑會讓導覽列少了這一項時仍然通過。</p>
     */
    private static final String NAV_RULES = "<a href=\"/r/rules\">遊戲規則</a>";
    private static final String NAV_ME = "<a href=\"/r/me\">我的帳戶</a>";
    private static final String NAV_LOGIN = "<a href=\"/r/login\">登入</a>";

    /**
     * {@code /r/} 首頁的導覽列必須反映登入狀態。
     *
     * <p>這頁的導覽列原本<b>寫死在 index.html 裡</b>，已登入的讀者回到首頁仍看到
     * 「登入」——點下去會再寄一封 magic link 給早就登入的人。兩個方向都要驗：
     * 只驗未登入分支的話，把渲染改成無條件輸出未登入版本仍會全綠。</p>
     */
    @Test
    void indexNavReflectsLoginState() throws Exception {
        when(readerContext.resolve(any())).thenReturn(Optional.empty());

        String anonymous = mvc.perform(get("/r/"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(anonymous.contains(NAV_LOGIN),
            "未登入時首頁導覽列應含登入連結");
        org.junit.jupiter.api.Assertions.assertFalse(anonymous.contains(NAV_ME),
            "未登入時首頁導覽列不得含我的帳戶連結");

        when(readerContext.resolve(anyString()))
            .thenReturn(Optional.of(new ReaderContext.Current(reader(), true)));
        String jwt = sessionService.issueJwt(1L, OffsetDateTime.now());

        String loggedIn = mvc.perform(get("/r/").cookie(
                new jakarta.servlet.http.Cookie(ReaderSessionService.COOKIE_NAME, jwt)))
            .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(loggedIn.contains(NAV_ME),
            "已登入時首頁導覽列應含我的帳戶連結");
        org.junit.jupiter.api.Assertions.assertFalse(loggedIn.contains(NAV_LOGIN),
            "已登入時首頁導覽列不得再含登入連結——點下去會再寄一封 magic link");
    }

    /** 首頁導覽列必須含規則頁連結（點數機制的可信度來源，spec §5.11） */
    @Test
    void indexNavContainsRulesLink() throws Exception {
        when(readerContext.resolve(any())).thenReturn(Optional.empty());

        mvc.perform(get("/r/"))
           .andExpect(content().string(org.hamcrest.Matchers.containsString(NAV_RULES)));
    }

    /**
     * 首頁的導覽列既然依 cookie 而異，就必須帶上不可共享快取的標頭。
     *
     * <p>少了它們，CDN／反向代理可能把某位登入者的導覽列（含「我的帳戶」）
     * 快取下來餵給匿名訪客。與 {@code /r/archive}、{@code /r/rules} 同一套慣例。</p>
     */
    @Test
    void indexIsNotSharedCacheable() throws Exception {
        when(readerContext.resolve(any())).thenReturn(Optional.empty());

        var response = mvc.perform(get("/r/"))
            .andExpect(header().string("Cache-Control", "private, no-store"))
            .andReturn().getResponse();

        // CORS 設定也會為 Vary 加上 Origin 等值（沿用 ReaderPageControllerTest 的做法），
        // 因此逐一檢查是否含 Cookie，而不是斷言整個標頭等於 "Cookie"
        org.junit.jupiter.api.Assertions.assertTrue(
            response.getHeaders(org.springframework.http.HttpHeaders.VARY).stream()
                .anyMatch(v -> v.contains(org.springframework.http.HttpHeaders.COOKIE)),
            "Vary 標頭必須包含 Cookie，否則共享快取無法區分登入者與匿名訪客的導覽列");
    }

    /** 首頁不得殘留未被替換的佔位符 */
    @Test
    void indexHasNoUnfilledPlaceholder() throws Exception {
        when(readerContext.resolve(any())).thenReturn(Optional.empty());

        String html = mvc.perform(get("/r/")).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("<!--NAV_LINKS-->"),
            "佔位符 <!--NAV_LINKS--> 不得殘留在回應中");
    }
}
