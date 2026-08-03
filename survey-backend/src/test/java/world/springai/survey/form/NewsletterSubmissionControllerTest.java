package world.springai.survey.form;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.ApiExceptionHandler;
import world.springai.survey.reader.ReaderSessionService;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 電子報通道問卷提交端點：成功 200 帶 rewarded；異常轉譯 401／400 JSON */
class NewsletterSubmissionControllerTest {

    private NewsletterSubmissionService submissionService;
    private FormSchemaController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        // 注入 mock 服務
        submissionService = mock(NewsletterSubmissionService.class);
        FormSchemaService formSchemaService = mock(FormSchemaService.class);
        var adminKeyGuard = mock(world.springai.survey.AdminKeyGuard.class);
        var welcomeMailService = mock(world.springai.survey.audience.WelcomeMailService.class);

        // 正確注入所有依賴，包括 newsletterSubmissionService
        controller = new FormSchemaController(
            formSchemaService,
            adminKeyGuard,
            welcomeMailService,
            submissionService);

        // MockMvc 需要 UTF-8 StringHttpMessageConverter（中文）與 JSON converter，
        // 以及異常處理器將 ResponseStatusException 轉成 JSON 回應
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiExceptionHandler())
            .setMessageConverters(
                new StringHttpMessageConverter(StandardCharsets.UTF_8),
                new MappingJackson2HttpMessageConverter())
            .build();
    }

    /** 建立電子報問卷提交請求 JSON */
    private String submitJson(Map<String, Object> answers, Long campaignId) {
        return String.format(
            """
            {"answers":%s,"campaignId":%d}
            """,
            toJson(answers),
            campaignId);
    }

    /** 將 Map 轉成簡單 JSON 字串 */
    private String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    /** 建立電子報 session cookie */
    private Cookie sessionCookie() {
        return new Cookie(ReaderSessionService.COOKIE_NAME, "valid-jwt-token");
    }

    /** 成功提交回 200，body 含 submissionId、rewarded、rewardCredits、rewardHint */
    @Test
    void 成功提交回200帶rewarded欄位() throws Exception {
        var result = new NewsletterSubmissionService.SubmitResult(
            "uuid-123", true, 50, "感謝填答，已發送 50 點數");
        when(submissionService.submit(anyString(), any(), anyString()))
            .thenReturn(result);

        mvc.perform(post("/api/forms/my-form/newsletter-submissions")
                .cookie(sessionCookie())
                .contentType(APPLICATION_JSON)
                .content(submitJson(Map.of("q1", "yes"), 1L)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.submissionId").value("uuid-123"))
           .andExpect(jsonPath("$.rewarded").value(true))
           .andExpect(jsonPath("$.rewardCredits").value(50))
           .andExpect(jsonPath("$.rewardHint").value("感謝填答，已發送 50 點數"));
    }

    /** 身分驗證失敗拋 401，轉譯為 401 JSON 回應（ProblemDetail 格式）*/
    @Test
    void 身分驗證失敗回401() throws Exception {
        // 注意：沒有 cookie，所以 sessionCookie 參數為 null，需用 nullable() 匹配
        when(submissionService.submit(anyString(), any(), nullable(String.class)))
            .thenThrow(new ResponseStatusException(UNAUTHORIZED, "請先登入"));

        mvc.perform(post("/api/forms/my-form/newsletter-submissions")
                .contentType(APPLICATION_JSON)
                .content(submitJson(Map.of("q1", "no"), 1L)))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.detail").value("請先登入"));
    }

    /** 答案驗證失敗拋 400，轉譯為 400 JSON 回應（ProblemDetail 格式）*/
    @Test
    void 答案驗證失敗回400() throws Exception {
        when(submissionService.submit(anyString(), any(), anyString()))
            .thenThrow(new ResponseStatusException(BAD_REQUEST, "必填欄位未填寫：q1"));

        mvc.perform(post("/api/forms/my-form/newsletter-submissions")
                .cookie(sessionCookie())
                .contentType(APPLICATION_JSON)
                .content(submitJson(Map.of(), 1L)))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.detail").value("必填欄位未填寫：q1"));
    }
}
