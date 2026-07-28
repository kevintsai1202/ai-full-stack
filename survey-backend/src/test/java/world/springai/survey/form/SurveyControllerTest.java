package world.springai.survey.form;

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

import world.springai.survey.AdminKeyGuard;
import world.springai.survey.audience.SurveyResponse;
import world.springai.survey.audience.SurveyResponseRepository;
import world.springai.survey.audience.WelcomeMailService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SurveyController 行為測試：驗證、蜜罐、admin 金鑰、即時統計、歡迎信觸發（confirm/unsubscribe 已搬至 SubscriptionControllerTest） */
@WebMvcTest(SurveyController.class)
@Import(AdminKeyGuard.class) // 注入金鑰守衛
@TestPropertySource(properties = {
    "app.admin-api-key=test-key",
    "app.cors-allowed-origins=http://localhost"
})
class SurveyControllerTest {
    @Autowired MockMvc mvc;
    @Autowired SurveyController controller; // 供直接呼叫 stats() 的測試使用（@WebMvcTest 下 controller 本身是真實 bean）
    @MockBean SurveyResponseRepository repository;
    @MockBean WelcomeMailService welcomeMailService;
    @MockBean SurveySubmissionService surveySubmissionService;

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
               "id,email,name,role,experience,frontend_experience,interest,budget,answers,utm,consent,unsubscribed,source,created_at")))
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

    /** 公開統計只計問卷填寫（survey_form），匯入名單（如 exam）不得灌水 */
    @Test
    void publicStatsExcludesImportedRows() throws Exception {
        SurveyResponse filled = new SurveyResponse();
        filled.setRole("後端工程師");
        filled.setSource("survey_form");
        SurveyResponse imported = new SurveyResponse();
        imported.setSource("exam");
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(filled, imported));

        mvc.perform(get("/api/survey/stats"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.total").value(1));
    }

    /** 問卷送出的資料來源應標記為 survey_form */
    @Test
    void submitMarksSourceAsSurveyForm() throws Exception {
        String body = "{\"email\":\"a@b.com\",\"consent\":true}";
        mvc.perform(post("/api/survey").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isCreated());
        org.mockito.ArgumentCaptor<SurveyResponse> captor =
            org.mockito.ArgumentCaptor.forClass(SurveyResponse.class);
        verify(repository).save(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("survey_form", captor.getValue().getSource());
    }

    /** /r/ 訂閱頁帶 source=newsletter 時，寫入的 source 應為 newsletter（白名單放行） */
    @Test
    void submitWithNewsletterSourceMarksAsNewsletter() throws Exception {
        String body = "{\"email\":\"reader@example.com\",\"consent\":true,\"source\":\"newsletter\"}";
        mvc.perform(post("/api/survey").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isCreated());
        org.mockito.ArgumentCaptor<SurveyResponse> captor =
            org.mockito.ArgumentCaptor.forClass(SurveyResponse.class);
        verify(repository).save(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("newsletter", captor.getValue().getSource());
    }

    /**
     * source 白名單：任意非 "newsletter" 的值一律忽略，落回預設 survey_form。
     * 這是防止外部呼叫端偽造 survey_form 以外的字面值來灌水公開統計的關鍵測試——
     * 白名單本身不會被誤放行成「照單全收」。
     */
    @Test
    void submitWithArbitrarySourceFallsBackToSurveyForm() throws Exception {
        String body = "{\"email\":\"faker@example.com\",\"consent\":true,\"source\":\"totally-made-up\"}";
        mvc.perform(post("/api/survey").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isCreated());
        org.mockito.ArgumentCaptor<SurveyResponse> captor =
            org.mockito.ArgumentCaptor.forClass(SurveyResponse.class);
        verify(repository).save(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("survey_form", captor.getValue().getSource());
    }

    /** 公開統計不得含 newsletter 來源的列，避免電子報訂閱者污染問卷公開數字 */
    @Test
    void publicStatsExcludesNewsletterSource() throws Exception {
        SurveyResponse filled = new SurveyResponse();
        filled.setRole("後端工程師");
        filled.setSource("survey_form");
        SurveyResponse newsletter = new SurveyResponse();
        newsletter.setSource("newsletter");
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(filled, newsletter));

        mvc.perform(get("/api/survey/stats"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.total").value(1));
    }

    /** CSV 匯出須包含來源欄位，可分辨 survey_form 與 exam */
    @Test
    void adminCsvIncludesSourceColumn() throws Exception {
        SurveyResponse response = new SurveyResponse();
        response.setId(9L);
        response.setEmail("exam-student@example.com");
        response.setSource("exam");
        response.setConsent(false);
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(response));

        mvc.perform(get("/api/admin/survey")
                .param("format", "csv")
                .header("X-Admin-Key", "test-key"))
           .andExpect(status().isOk())
           .andExpect(content().string(org.hamcrest.Matchers.containsString("source")))
           .andExpect(content().string(org.hamcrest.Matchers.containsString("exam")));
    }

    /** 帶 ref 的訂閱請求應把推薦碼寫進 answers 的 _ref 鍵 */
    @Test
    void refIsStoredIntoAnswersUnderscoreRef() throws Exception {
        mvc.perform(post("/api/survey")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"invitee@example.com","consent":true,"source":"newsletter","ref":"ABCD2345"}
                    """))
           .andExpect(status().isCreated());

        org.mockito.ArgumentCaptor<SurveyResponse> captor = org.mockito.ArgumentCaptor.forClass(SurveyResponse.class);
        verify(repository).save(captor.capture());
        assertEquals("ABCD2345", captor.getValue().getAnswers().get("_ref"));
    }

    /**
     * answers 原本為 null 時也要能放進 _ref。
     *
     * <p>這是實際會走到的路徑：/r/ 訂閱表單只送 email、consent、source、ref，
     * 完全沒有問卷答案，所以 answers 是 null。若實作直接對 null 呼叫 put()
     * 會 NPE，而這條路徑正是邀請功能的主線。</p>
     */
    @Test
    void refIsStoredEvenWhenAnswersAbsent() throws Exception {
        mvc.perform(post("/api/survey")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"invitee2@example.com","consent":true,"source":"newsletter","ref":"WXYZ6789"}
                    """))
           .andExpect(status().isCreated());

        org.mockito.ArgumentCaptor<SurveyResponse> captor = org.mockito.ArgumentCaptor.forClass(SurveyResponse.class);
        verify(repository).save(captor.capture());
        assertNotNull(captor.getValue().getAnswers());
        assertEquals("WXYZ6789", captor.getValue().getAnswers().get("_ref"));
    }

    /** 文章分享訂閱應同時保存推薦碼與文章來源，供後續轉換分析。 */
    @Test
    void articleShareAttributionIsStoredWithReferral() throws Exception {
        mvc.perform(post("/api/survey")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"shared@example.com","consent":true,"source":"newsletter",
                     "ref":"ABCD2345","share":"spring-ai-agent-notes"}
                    """))
           .andExpect(status().isCreated());

        org.mockito.ArgumentCaptor<SurveyResponse> captor = org.mockito.ArgumentCaptor.forClass(SurveyResponse.class);
        verify(repository).save(captor.capture());
        assertEquals("ABCD2345", captor.getValue().getAnswers().get("_ref"));
        assertEquals("spring-ai-agent-notes", captor.getValue().getAnswers().get("_share_article"));
    }

    /** 沒有推薦碼時不得單獨保存文章分享歸因，避免把普通流量誤算成推薦轉換。 */
    @Test
    void articleShareAttributionWithoutReferralIsIgnored() throws Exception {
        mvc.perform(post("/api/survey")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"organic@example.com","consent":true,"source":"newsletter",
                     "share":"spring-ai-agent-notes"}
                    """))
           .andExpect(status().isCreated());

        org.mockito.ArgumentCaptor<SurveyResponse> captor = org.mockito.ArgumentCaptor.forClass(SurveyResponse.class);
        verify(repository).save(captor.capture());
        Map<String, Object> answers = captor.getValue().getAnswers();
        assertTrue(answers == null || !answers.containsKey("_share_article"));
    }

    /** 被竄改的文章來源只忽略歸因，不可讓合法訂閱失敗。 */
    @Test
    void unsafeArticleShareAttributionIsIgnoredWithoutRejectingSubscription() throws Exception {
        mvc.perform(post("/api/survey")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"safe@example.com","consent":true,"source":"newsletter",
                     "ref":"ABCD2345","share":"../admin.html"}
                    """))
           .andExpect(status().isCreated());

        org.mockito.ArgumentCaptor<SurveyResponse> captor = org.mockito.ArgumentCaptor.forClass(SurveyResponse.class);
        verify(repository).save(captor.capture());
        assertEquals("ABCD2345", captor.getValue().getAnswers().get("_ref"));
        assertFalse(captor.getValue().getAnswers().containsKey("_share_article"));
    }

    /** 沒有 ref 時不可留下空的 _ref 鍵，否則後續「有沒有推薦人」的判斷要多處理空字串 */
    @Test
    void absentRefLeavesNoUnderscoreRefKey() throws Exception {
        mvc.perform(post("/api/survey")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"plain@example.com","consent":true,"source":"newsletter"}
                    """))
           .andExpect(status().isCreated());

        org.mockito.ArgumentCaptor<SurveyResponse> captor = org.mockito.ArgumentCaptor.forClass(SurveyResponse.class);
        verify(repository).save(captor.capture());
        Map<String, Object> answers = captor.getValue().getAnswers();
        assertTrue(answers == null || !answers.containsKey("_ref"));
    }

    /** 空白字串的 ref 視同沒有 ref */
    @Test
    void blankRefIsIgnored() throws Exception {
        mvc.perform(post("/api/survey")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"blank@example.com","consent":true,"source":"newsletter","ref":"   "}
                    """))
           .andExpect(status().isCreated());

        org.mockito.ArgumentCaptor<SurveyResponse> captor = org.mockito.ArgumentCaptor.forClass(SurveyResponse.class);
        verify(repository).save(captor.capture());
        Map<String, Object> answers = captor.getValue().getAnswers();
        assertTrue(answers == null || !answers.containsKey("_ref"));
    }

    /**
     * 公開統計必須排除底線開頭的系統鍵。
     *
     * <p>沒有這道過濾，`_ref` 會被當成一道問卷答案出現在 /api/survey/stats
     * 對外公開的圖表裡——那不只是難看，而是把讀者的邀請碼關係公開了。</p>
     */
    @Test
    void statsExcludeUnderscorePrefixedSystemKeys() {
        SurveyResponse withRef = new SurveyResponse();
        withRef.setSource("survey_form");
        withRef.setAnswers(Map.of(
            "status", "在職",
            "_ref", "ABCD2345",
            "_share_article", "spring-ai-agent-notes"));
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(withRef));

        SurveyStats stats = controller.stats();

        // status 應被統計；_ref 不得出現在任何一組 bucket 的標籤中
        assertTrue(stats.status().stream().anyMatch(b -> "在職".equals(b.label())));
        assertTrue(stats.status().stream().noneMatch(b -> b.label().startsWith("_")));

        // 直接鎖住 answerOf 的契約：底線開頭的鍵一律視為不存在。
        //
        // 為什麼要這一行：stats() 目前唯一的呼叫是 answerOf(r, "status")，key 永遠是
        // 字面量，永遠不會是 "_ref"。若只斷言上面兩行，把 answerOf 的
        // key.startsWith("_") 檢查整段刪掉，這個測試照樣是綠的——它驗的其實只是
        // 「status 有被統計」。而這道過濾存在的理由是「日後有人攤平 answers
        // 全部鍵做統計時，系統鍵不會外洩」，那條路徑現在還不存在，所以必須直接
        // 對 answerOf 斷言，契約才真的被鎖住。
        assertNull(SurveyController.answerOf(withRef, "_ref"),
            "底線開頭的系統鍵必須被視為不存在，否則推薦碼會出現在無需金鑰的公開統計中");
        assertNull(SurveyController.answerOf(withRef, "_share_article"),
            "文章分享來源也是系統鍵，不得出現在無需金鑰的公開統計中");
        assertEquals("在職", SurveyController.answerOf(withRef, "status"),
            "一般問卷鍵仍必須讀得到，過濾不可過寬");
    }
}
