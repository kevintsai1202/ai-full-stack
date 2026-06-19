package world.springai.survey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SurveyController 行為測試：驗證、蜜罐、admin 金鑰 */
@WebMvcTest(SurveyController.class)
@TestPropertySource(properties = {"app.admin-api-key=test-key", "app.cors-allowed-origins=http://localhost"})
class SurveyControllerTest {
    @Autowired MockMvc mvc;
    @MockBean SurveyResponseRepository repository;

    @Test
    void validSurveyReturns201() throws Exception {
        String body = "{\"email\":\"a@b.com\",\"consent\":true}";
        mvc.perform(post("/api/survey").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isCreated());
        verify(repository).save(any(SurveyResponse.class));
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
    @Test
    void honeypotFilledReturns204AndSkips() throws Exception {
        String body = "{\"email\":\"a@b.com\",\"consent\":true,\"website\":\"spam\"}";
        mvc.perform(post("/api/survey").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isNoContent());
        verify(repository, never()).save(any());
    }
    @Test
    void adminWithoutKeyReturns401() throws Exception {
        mvc.perform(get("/api/admin/survey")).andExpect(status().isUnauthorized());
    }
    @Test
    void adminWithKeyReturns200() throws Exception {
        mvc.perform(get("/api/admin/survey").header("X-Admin-Key", "test-key")).andExpect(status().isOk());
    }

    @Test
    void publicStatsAggregatesWithoutKey() throws Exception {
        // 準備兩筆樣本：驗證複選攤平計數、status 聚合與總數
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
           // RAG 知識庫 被選 2 次，應排在 interest 第一
           .andExpect(jsonPath("$.interest[0].label").value("RAG 知識庫"))
           .andExpect(jsonPath("$.interest[0].count").value(2))
           // 兩筆同為「後端工程師」，role 第一名計數為 2
           .andExpect(jsonPath("$.role[0].label").value("後端工程師"))
           .andExpect(jsonPath("$.role[0].count").value(2));
    }
}
