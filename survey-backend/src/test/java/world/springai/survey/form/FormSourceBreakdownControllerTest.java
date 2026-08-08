package world.springai.survey.form;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.AdminKeyGuard;
import world.springai.survey.ApiExceptionHandler;
import world.springai.survey.audience.WelcomeMailService;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/admin/analytics/forms/{formKey}/sources} 端點測試：
 * 金鑰守衛先行、查詢參數完整透傳、回應結構含 sources 與 totals。
 */
class FormSourceBreakdownControllerTest {

    private MockMvc mvc;
    private AdminKeyGuard guard;
    private FormSourceBreakdownService breakdownService;

    @BeforeEach
    void setUp() {
        guard = mock(AdminKeyGuard.class);
        breakdownService = mock(FormSourceBreakdownService.class);

        FormSchemaController controller = new FormSchemaController(
            mock(FormSchemaService.class),
            guard,
            mock(WelcomeMailService.class),
            mock(NewsletterSubmissionService.class),
            mock(SurveyVoteStatsService.class),
            breakdownService);

        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiExceptionHandler())
            .setMessageConverters(
                new StringHttpMessageConverter(StandardCharsets.UTF_8),
                new MappingJackson2HttpMessageConverter())
            .build();
    }

    /** 回應同時帶出各來源明細與加總，讓畫面能並列顯示總提交與有答案筆數。 */
    @Test
    void 回傳來源明細與加總() throws Exception {
        when(breakdownService.breakdown(
                eq("course-interest"), isNull(), eq(false), isNull(), isNull(), isNull()))
            .thenReturn(new FormSourceBreakdownService.Breakdown(
                List.of(
                    new FormSourceBreakdownService.SourceRow("exam", "線上測驗", 254, 0),
                    new FormSourceBreakdownService.SourceRow(
                        "newsletter_survey", "讀者接續填答", 1, 1)),
                new FormSourceBreakdownService.Totals(255, 1)));

        mvc.perform(get("/api/admin/analytics/forms/course-interest/sources")
                .header("X-Admin-Key", "secret"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.sources[0].key").value("exam"))
           .andExpect(jsonPath("$.sources[0].label").value("線上測驗"))
           .andExpect(jsonPath("$.sources[0].total").value(254))
           .andExpect(jsonPath("$.sources[0].answered").value(0))
           .andExpect(jsonPath("$.sources[1].label").value("讀者接續填答"))
           .andExpect(jsonPath("$.totals.total").value(255))
           .andExpect(jsonPath("$.totals.answered").value(1));
    }

    /** version／allVersions／日期區間／期別全部透傳，畫面篩選與這支端點才會是同一份資料。 */
    @Test
    void 查詢參數完整透傳至服務層() throws Exception {
        when(breakdownService.breakdown(
                any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any(), any(), any()))
            .thenReturn(new FormSourceBreakdownService.Breakdown(
                List.of(), new FormSourceBreakdownService.Totals(0, 0)));

        mvc.perform(get("/api/admin/analytics/forms/course-interest/sources")
                .header("X-Admin-Key", "secret")
                .param("version", "2")
                .param("allVersions", "true")
                .param("from", "2026-01-01T00:00:00Z")
                .param("to", "2026-02-01T00:00:00Z")
                .param("campaignId", "77"))
           .andExpect(status().isOk());

        verify(breakdownService).breakdown(
            "course-interest",
            2,
            true,
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            OffsetDateTime.parse("2026-02-01T00:00:00Z"),
            77L);
    }

    /** 金鑰無效時守衛先擋下，不可讓查詢真的打到資料庫。 */
    @Test
    void 金鑰無效時不查詢() throws Exception {
        doThrow(new ResponseStatusException(UNAUTHORIZED, "未授權"))
            .when(guard).verify(nullable(String.class));

        mvc.perform(get("/api/admin/analytics/forms/course-interest/sources"))
           .andExpect(status().isUnauthorized());

        verify(breakdownService, never()).breakdown(any(), any(),
            org.mockito.ArgumentMatchers.anyBoolean(), any(), any(), any());
    }
}
