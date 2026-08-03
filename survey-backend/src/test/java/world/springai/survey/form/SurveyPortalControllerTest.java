package world.springai.survey.form;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
           // 全域鐵律：手動設 Content-Type 回 HTML 必須明講 charset=UTF-8，否則中文可能被
           // StringHttpMessageConverter 誤判成 ISO-8859-1 解讀而亂碼
           .andExpect(content().contentType("text/html;charset=UTF-8"))
           .andExpect(content().string(containsString("回饋問卷")))
           .andExpect(content().string(containsString("\"feedback\"")))
           .andExpect(content().string(containsString("\"short_text\"")));
    }

    /**
     * rt 帶惡意值（一個雙引號後接右括號、分號、alert(1) 呼叫與註解符號）企圖提前結束
     * {@code var RT = <!--RT-->;} 這段 JS 字串字面值。{@code toJsLiteral} 以
     * {@code ObjectMapper} 序列化，字串內部的雙引號會被 JSON 規則跳脫成 {@code \"}，
     * 讓瀏覽器把整段值當成單一字串常數，不會被解析成可執行的 JS 語句。
     *
     * <p>刻意不能只用 {@code assertFalse(body.contains(malicious))}：正確跳脫後的輸出
     * 仍會把 {@code malicious} 原始字元序列完整包在跳脫後的引號段落裡（只是前面多一個
     * 反斜線），單純的子字串比對測不出跳脫有沒有生效。必須改為比對「兩個雙引號緊鄰、
     * 中間沒有反斜線」這個唯有跳脫失效時才會出現的特徵字串。</p>
     */
    @Test
    void rt含惡意字元時JSON跳脫生效不洩漏可提前結束JS字串的未跳脫序列() throws Exception {
        when(formSchemaService.getDefinition("survey-form", null)).thenReturn(form());
        String malicious = "\"});alert(1);//";

        MvcResult result = mvc.perform(get("/r/survey/survey-form").param("rt", malicious))
            .andExpect(status().isOk())
            .andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        // 正確跳脫：值內部的雙引號前必定帶一個反斜線
        assertTrue(body.contains("\\\"});alert(1);"),
            "toJsLiteral 必須把值中的雙引號跳脫成 \\\"，否則會提前結束 JS 字串字面值");
        // 未跳脫才會出現的特徵：緊鄰兩個雙引號（等同提前把字串關閉成空字串）
        assertFalse(body.contains("\"\"});alert(1);"),
            "不得出現未跳脫的雙引號序列，代表 JS 字串字面值被提前關閉、後續內容可被當成程式碼執行");
    }

    /**
     * 欄位 label 含 {@code </script><script>} 企圖提前關閉
     * {@code <script type="application/json">} 標籤並注入新的 {@code <script>} 元素。
     * {@code toJsLiteral} 對 {@code FIELDS_JSON} 額外做 {@code </} → {@code <\/} 取代，
     * 因為 HTML 解析器判斷 {@code </script>} 是在 JSON/JS 語法之前，任何 {@code type}
     * 屬性都無法阻止提前關閉。
     */
    @Test
    void 欄位label含scriptTag時FIELDS_JSON防止提前關閉標籤() throws Exception {
        FormSchemaService.FieldDefinition field = new FormSchemaService.FieldDefinition(
            1L, "feedback", "</script><script>alert(1)</script>", "short_text", true, List.of(),
            false, null, false, false, false, 1, null);
        FormSchemaService.FormDefinition maliciousForm = new FormSchemaService.FormDefinition(
            1L, "survey-form", 1, "回饋問卷", "PUBLISHED", false, null, List.of(field));
        when(formSchemaService.getDefinition("survey-form", null)).thenReturn(maliciousForm);

        MvcResult result = mvc.perform(get("/r/survey/survey-form"))
            .andExpect(status().isOk())
            .andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(body.contains("<\\/script>"),
            "FIELDS_JSON 必須把 </ 轉成 <\\/，避免提前關閉外層 <script> 標籤");
        assertFalse(body.contains("</script><script>"),
            "不得出現未跳脫的 </script><script> 攻擊序列，否則會提前結束內嵌資料的 script 標籤並注入新標籤");
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
