package world.springai.survey.form;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.AdminKeyGuard;
import world.springai.survey.ApiExceptionHandler;
import world.springai.survey.audience.WelcomeMailService;
import world.springai.survey.reader.ReaderSessionService;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FormSchemaController 首頁曝光端點（PUT /api/admin/forms/{formKey}/homepage）測試（Task 2） */
class FormSchemaControllerHomepageTest {

    private FormSchemaController controller;
    private MockMvc mvc;
    private FormSchemaService service;
    private AdminKeyGuard guard;
    private WelcomeMailService welcomeMailService;
    private NewsletterSubmissionService newsletterSubmissionService;
    private SurveyVoteStatsService voteStatsService;

    @BeforeEach
    void setUp() {
        // 建立全 mock 協作者，比照 AdminCouponControllerTest 的 standalone MockMvc 建法
        service = mock(FormSchemaService.class);
        guard = mock(AdminKeyGuard.class);
        welcomeMailService = mock(WelcomeMailService.class);
        newsletterSubmissionService = mock(NewsletterSubmissionService.class);
        voteStatsService = mock(SurveyVoteStatsService.class);

        controller = new FormSchemaController(
            service, guard, welcomeMailService, newsletterSubmissionService, voteStatsService);

        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiExceptionHandler())
            .setMessageConverters(
                new StringHttpMessageConverter(StandardCharsets.UTF_8),
                new MappingJackson2HttpMessageConverter())
            .build();
    }

    /** 首頁曝光端點：驗證 guard 先行、參數透傳、204 回應 */
    @Test
    void updateHomepageExposureDelegatesToService() throws Exception {
        mvc.perform(put("/api/admin/forms/my-form/homepage")
                .header("X-Admin-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visible\":true,\"order\":3}"))
            .andExpect(status().isNoContent());
        verify(service).updateHomepageExposure("my-form", true, 3);
    }

    /** order 省略時傳 null（清除排序） */
    @Test
    void omittedOrderBecomesNull() throws Exception {
        mvc.perform(put("/api/admin/forms/my-form/homepage")
                .header("X-Admin-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visible\":false}"))
            .andExpect(status().isNoContent());
        verify(service).updateHomepageExposure("my-form", false, null);
    }

    /** 未帶 X-Admin-Key 時 guard 先行拋 401，透傳為 401 JSON 回應（比照檔內既有測試慣例） */
    @Test
    void 缺Admin金鑰回401() throws Exception {
        doThrow(new ResponseStatusException(UNAUTHORIZED, "invalid admin credential"))
            .when(guard).verify(nullable(String.class));

        mvc.perform(put("/api/admin/forms/my-form/homepage")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visible\":true,\"order\":3}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail").value("invalid admin credential"));
    }
}
