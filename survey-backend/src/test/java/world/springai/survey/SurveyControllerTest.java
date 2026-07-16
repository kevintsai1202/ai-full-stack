package world.springai.survey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SurveyController 行為測試：驗證、蜜罐、admin 金鑰、即時統計、退訂 token、歡迎信觸發 */
@WebMvcTest(SurveyController.class)
@Import({UnsubscribeTokenService.class, AdminKeyGuard.class}) // 用真實 token 服務以便計算合法 token，並注入金鑰守衛
@TestPropertySource(properties = {
    "app.admin-api-key=test-key",
    "app.cors-allowed-origins=http://localhost",
    "app.unsubscribe-secret=test-secret"
})
class SurveyControllerTest {
    @Autowired MockMvc mvc;
    @Autowired UnsubscribeTokenService tokenService;
    @MockBean SurveyResponseRepository repository;
    @MockBean WelcomeMailService welcomeMailService;

    @Test
    void validSurveyReturns201() throws Exception {
        String body = "{\"email\":\"a@b.com\",\"consent\":true}";
        mvc.perform(post("/api/survey").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isCreated());
        verify(repository).save(any(SurveyResponse.class));
    }

    /** 合法問卷送出後應觸發歡迎信寄送一次 */
    @Test
    void validSurveyTriggersWelcomeMail() throws Exception {
        String body = "{\"email\":\"a@b.com\",\"consent\":true}";
        mvc.perform(post("/api/survey").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isCreated());
        verify(welcomeMailService).sendWelcome("a@b.com");
    }

    @Test
    void missingConsentReturns400() throws Exception {
        String body = "{\"email\":\"a@b.com\",\"consent\":false}";
        mvc.perform(post("/api/survey").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isBadRequest());
        verify(repository, never()).save(any());
    }

    @Test
    void invalidEmailReturns400() throws Exception {
        String body = "{\"email\":\"not-an-email\",\"consent\":true}";
        mvc.perform(post("/api/survey").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isBadRequest());
        verify(repository, never()).save(any());
    }

    /** 蜜罐有值：回 204、不寫入、且不寄歡迎信 */
    @Test
    void honeypotFilledReturns204AndSkips() throws Exception {
        String body = "{\"email\":\"a@b.com\",\"consent\":true,\"website\":\"spam\"}";
        mvc.perform(post("/api/survey").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isNoContent());
        verify(repository, never()).save(any());
        verify(welcomeMailService, never()).sendWelcome(any());
    }

    @Test
    void adminWithoutKeyReturns401() throws Exception {
        mvc.perform(get("/api/admin/survey")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminWithKeyReturns200() throws Exception {
        mvc.perform(get("/api/admin/survey").header("X-Admin-Key", "test-key")).andExpect(status().isOk());
    }

    /** CSV 匯出必須涵蓋問卷的所有資料欄位，包含 UTM 與延伸答案 */
    @Test
    void adminCsvExportsAllSurveyFields() throws Exception {
        SurveyResponse response = new SurveyResponse();
        response.setId(7L);
        response.setEmail("user@example.com");
        response.setName("測試者");
        response.setRole("全端工程師");
        response.setExperience("3-5 年");
        response.setFrontendExperience("1-3 年");
        response.setInterest(List.of("RAG 知識庫", "前端整合"));
        response.setBudget("NT$3,000-5,000");
        response.setAnswers(Map.of("status", "在職", "suggestion", "希望增加實作"));
        response.setUtm(Map.of("source", "newsletter", "campaign", "summer"));
        response.setConsent(true);
        response.setUnsubscribed(false);
        response.setCreatedAt(OffsetDateTime.parse("2026-07-16T10:30:00+08:00"));
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(response));

        mvc.perform(get("/api/admin/survey")
                .param("format", "csv")
                .header("X-Admin-Key", "test-key"))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith("text/csv"))
           .andExpect(content().string(org.hamcrest.Matchers.containsString(
               "id,email,name,role,experience,frontend_experience,interest,budget,answers,utm,consent,unsubscribed,created_at")))
           .andExpect(content().string(org.hamcrest.Matchers.containsString("newsletter")))
           .andExpect(content().string(org.hamcrest.Matchers.containsString("希望增加實作")));
    }

    @Test
    void publicStatsAggregatesWithoutKey() throws Exception {
        SurveyResponse a = new SurveyResponse();
        a.setRole("後端工程師");
        a.setInterest(List.of("RAG 知識庫", "Tool Calling"));
        a.setAnswers(Map.of("status", "在職工程師，想技能升級"));
        SurveyResponse b = new SurveyResponse();
        b.setRole("後端工程師");
        b.setInterest(List.of("RAG 知識庫"));
        b.setAnswers(Map.of("status", "想轉職全端／AI 工程師"));
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(a, b));

        mvc.perform(get("/api/survey/stats"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.total").value(2))
           .andExpect(jsonPath("$.interest[0].label").value("RAG 知識庫"))
           .andExpect(jsonPath("$.interest[0].count").value(2))
           .andExpect(jsonPath("$.role[0].label").value("後端工程師"))
           .andExpect(jsonPath("$.role[0].count").value(2));
    }

    /** 退訂：合法 token 應更新該 email 並回 200 HTML */
    @Test
    void unsubscribeWithValidTokenUpdates() throws Exception {
        String email = "user@example.com";
        String token = tokenService.sign(email);
        mvc.perform(get("/api/survey/unsubscribe").param("email", email).param("t", token))
           .andExpect(status().isOk());
        verify(repository).unsubscribeByEmail(email);
    }

    /** 退訂：token 錯誤不更新，但仍回 200 同頁（不洩漏） */
    @Test
    void unsubscribeWithBadTokenDoesNotUpdate() throws Exception {
        mvc.perform(get("/api/survey/unsubscribe").param("email", "user@example.com").param("t", "bad"))
           .andExpect(status().isOk());
        verify(repository, never()).unsubscribeByEmail(any());
    }

    /** 退訂：缺 token 不更新，仍回 200 */
    @Test
    void unsubscribeWithoutTokenDoesNotUpdate() throws Exception {
        mvc.perform(get("/api/survey/unsubscribe").param("email", "user@example.com"))
           .andExpect(status().isOk());
        verify(repository, never()).unsubscribeByEmail(any());
    }
}
