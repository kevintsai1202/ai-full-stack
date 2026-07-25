package world.springai.survey.reader;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

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
@Import({HtmlTemplate.class, ReaderSessionService.class})
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
           .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }
}
