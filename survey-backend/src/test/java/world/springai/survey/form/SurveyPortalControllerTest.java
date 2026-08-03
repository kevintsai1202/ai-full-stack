package world.springai.survey.form;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.ApiExceptionHandler;
import world.springai.survey.promo.PromoRecipientTokenService;
import world.springai.survey.reader.CreditPolicy;
import world.springai.survey.reader.HtmlTemplate;
import world.springai.survey.reader.Reader;
import world.springai.survey.reader.ReaderRepository;
import world.springai.survey.reader.ReaderSessionService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 讀者接續填答頁 {@code GET /r/survey/{formKey}}：schema 動態表單、身分遮罩、
 * 未歸戶引導與快取標頭。standalone MockMvc＋mock 依賴＋真實 {@link HtmlTemplate}
 * （同時驗證模板佔位符是否存在），照 {@code PromoClickControllerTest} 慣例。
 */
class SurveyPortalControllerTest {

    private FormSchemaService formSchemaService;
    private PromoRecipientTokenService tokenService;
    private ReaderSessionService sessionService;
    private ReaderRepository readerRepository;
    private CreditPolicy creditPolicy;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        formSchemaService = mock(FormSchemaService.class);
        tokenService = mock(PromoRecipientTokenService.class);
        sessionService = mock(ReaderSessionService.class);
        readerRepository = mock(ReaderRepository.class);
        creditPolicy = mock(CreditPolicy.class);
        // 預設：rt 與 session 都無效（未登入），問卷完整填答獎勵為 20 點
        when(tokenService.verify(any())).thenReturn(Optional.empty());
        when(sessionService.readReaderId(any(), any())).thenReturn(Optional.empty());
        when(creditPolicy.surveyReward()).thenReturn(20);

        SurveyPortalController controller = new SurveyPortalController(
            formSchemaService, tokenService, sessionService, readerRepository,
            creditPolicy, new HtmlTemplate(), new ObjectMapper());
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiExceptionHandler())
            .setMessageConverters(
                new StringHttpMessageConverter(StandardCharsets.UTF_8),
                new MappingJackson2HttpMessageConverter())
            .build();
    }

    /** 測試用已發布問卷：單一必填 short_text 欄位即可涵蓋 schema JSON 內嵌行為 */
    private FormSchemaService.FormDefinition form() {
        FormSchemaService.FieldDefinition field = new FormSchemaService.FieldDefinition(
            1L, "feedback", "你的回饋", "short_text", true, List.of(),
            false, null, false, false, false, 1, null);
        return new FormSchemaService.FormDefinition(
            1L, "survey-form", 1, "回饋問卷", "PUBLISHED", false, null, List.of(field));
    }

    @Test
    void 已發布問卷回200含標題與欄位JSON() throws Exception {
        when(formSchemaService.getDefinition("survey-form", null)).thenReturn(form());

        mvc.perform(get("/r/survey/survey-form"))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith("text/html"))
           .andExpect(content().string(containsString("回饋問卷")))
           .andExpect(content().string(containsString("\"feedback\"")))
           .andExpect(content().string(containsString("\"short_text\"")));
    }

    @Test
    void rt有效時顯示遮罩email且不含完整email() throws Exception {
        when(formSchemaService.getDefinition("survey-form", null)).thenReturn(form());
        when(tokenService.verify("tok")).thenReturn(Optional.of("alice@example.com"));

        mvc.perform(get("/r/survey/survey-form").param("rt", "tok"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("a***@example.com")))
           .andExpect(content().string(not(containsString("alice@example.com"))));
    }

    @Test
    void session歸戶時也顯示遮罩email且不含完整email() throws Exception {
        when(formSchemaService.getDefinition("survey-form", null)).thenReturn(form());
        when(sessionService.readReaderId(eq("cookie-value"), any())).thenReturn(Optional.of(5L));
        Reader reader = new Reader("bob@site.io", "REF1");
        reader.setId(5L);
        when(readerRepository.findById(5L)).thenReturn(Optional.of(reader));

        mvc.perform(get("/r/survey/survey-form")
                .cookie(new Cookie(ReaderSessionService.COOKIE_NAME, "cookie-value")))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("b***@site.io")))
           .andExpect(content().string(not(containsString("bob@site.io"))));
    }

    @Test
    void 未歸戶時顯示登入引導且表單標記唯讀() throws Exception {
        when(formSchemaService.getDefinition("survey-form", null)).thenReturn(form());

        mvc.perform(get("/r/survey/survey-form"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("登入作答")))
           .andExpect(content().string(containsString("data-identified=\"false\"")));
    }

    @Test
    void voted參數存在時顯示已收到投票banner() throws Exception {
        when(formSchemaService.getDefinition("survey-form", null)).thenReturn(form());

        mvc.perform(get("/r/survey/survey-form").param("voted", "0"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("已收到你的投票")));
    }

    @Test
    void 問卷未發布時回404() throws Exception {
        when(formSchemaService.getDefinition("missing-form", null))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到指定表單版本"));

        mvc.perform(get("/r/survey/missing-form"))
           .andExpect(status().isNotFound());
    }

    @Test
    void 回應含私有不快取標頭() throws Exception {
        when(formSchemaService.getDefinition("survey-form", null)).thenReturn(form());

        mvc.perform(get("/r/survey/survey-form"))
           .andExpect(status().isOk())
           .andExpect(header().string("Cache-Control", containsString("no-store")))
           .andExpect(header().string("Cache-Control", containsString("private")));
    }
}
