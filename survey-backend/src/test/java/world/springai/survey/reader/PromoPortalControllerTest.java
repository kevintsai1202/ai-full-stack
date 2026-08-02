package world.springai.survey.reader;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import world.springai.survey.audience.RecipientService;
import world.springai.survey.promo.PromoProposal;
import world.springai.survey.promo.PromoProposalRepository;
import world.springai.survey.promo.PromoProposalService;
import world.springai.survey.promo.PromoProposalService.ApplyResult;
import world.springai.survey.promo.PromoProposalService.InsufficientCreditsException;
import world.springai.survey.promo.PromoProposalService.PromoValidationException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 工商合作頁：登入要求、頁面渲染（含跳脫）、申請端點的成功／各例外分支 */
class PromoPortalControllerTest {

    private static final long READER_ID = 3L;

    private ReaderContext readerContext;
    private PromoProposalService promoProposalService;
    private PromoProposalRepository proposalRepository;
    private CreditPolicy creditPolicy;
    private RecipientService recipientService;
    private PromoPortalController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        readerContext = mock(ReaderContext.class);
        promoProposalService = mock(PromoProposalService.class);
        proposalRepository = mock(PromoProposalRepository.class);
        creditPolicy = mock(CreditPolicy.class);
        recipientService = mock(RecipientService.class);
        when(creditPolicy.promoPlacementCost()).thenReturn(100);
        when(recipientService.subscriberCount()).thenReturn(1234L);
        when(proposalRepository.findByReaderIdOrderByCreatedAtDesc(anyLong())).thenReturn(List.of());

        controller = new PromoPortalController(new HtmlTemplate(), readerContext,
            promoProposalService, proposalRepository, creditPolicy, recipientService);
        // standalone MockMvc 沒有 Spring Boot 的 WebMvcAutoConfiguration，預設的
        // StringHttpMessageConverter 用 ISO-8859-1，中文字會變亂碼；本頁另有 JSON 請求體
        // （POST /r/promo/apply），需要 MappingJackson2HttpMessageConverter 才能解析。
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(
                new StringHttpMessageConverter(StandardCharsets.UTF_8),
                new MappingJackson2HttpMessageConverter())
            .build();
    }

    private Reader reader(int credits) {
        Reader r = new Reader("reader@example.com", "CODE1234");
        r.setId(READER_ID);
        r.setCredits(credits);
        return r;
    }

    private void givenLoggedIn(Reader r) {
        when(readerContext.resolve(anyString()))
            .thenReturn(Optional.of(new ReaderContext.Current(r, true)));
    }

    /** 建一個帶 session cookie 的請求 */
    private Cookie cookie() {
        return new Cookie(ReaderSessionService.COOKIE_NAME, "JWT");
    }

    /** 未登入必須導向登入頁並帶回跳目標，而不是回 401——這是頁面而非 API */
    @Test
    void 未登入時導向登入頁並帶回跳目標() throws Exception {
        when(readerContext.resolve(anyString())).thenReturn(Optional.empty());

        mvc.perform(get("/r/promo"))
           .andExpect(status().isFound())
           .andExpect(header().string("Location", "/r/login?redirect=/r/promo"));
    }

    /** 登入後應渲染導覽（含工商合作）、單價、訂閱規模與目前餘額，皆取自後端而非前端寫死 */
    @Test
    void 登入後渲染單價訂閱規模與餘額() throws Exception {
        givenLoggedIn(reader(287));

        mvc.perform(get("/r/promo").cookie(cookie()))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("工商合作")))
           .andExpect(content().string(containsString("100")))
           .andExpect(content().string(containsString("1234")))
           .andExpect(content().string(containsString("287")));
    }

    /** 提案名稱含 HTML 特殊字元時，PROPOSAL_ROWS 必須跳脫，不可讓其原樣進入回應 */
    @Test
    void 提案名稱含特殊字元時渲染需跳脫() throws Exception {
        givenLoggedIn(reader(0));
        PromoProposal proposal = new PromoProposal(READER_ID, "聯絡人", "a@b.com",
            "<script>alert(1)</script>", "文案", "看更多", "https://example.com", 2, 100);
        when(proposalRepository.findByReaderIdOrderByCreatedAtDesc(READER_ID))
            .thenReturn(List.of(proposal));

        mvc.perform(get("/r/promo").cookie(cookie()))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString(
               "&lt;script&gt;alert(1)&lt;/script&gt;")))
           .andExpect(content().string(org.hamcrest.Matchers.not(
               containsString("<script>alert(1)</script>"))))
           .andExpect(content().string(containsString("待審核")));
    }

    /** POST /r/promo/apply 未登入回 401 JSON，而非導轉——這是表單以 fetch 呼叫的 API 端點 */
    @Test
    void 申請未登入回401() throws Exception {
        when(readerContext.resolve(anyString())).thenReturn(Optional.empty());

        mvc.perform(post("/r/promo/apply")
                .contentType(APPLICATION_JSON)
                .content(validApplyJson()))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.message").value("請先登入"));
    }

    /** 驗證失敗（PromoValidationException）轉 400，body 帶讀者看得懂的訊息 */
    @Test
    void 驗證失敗回400() throws Exception {
        givenLoggedIn(reader(1000));
        when(promoProposalService.apply(anyLong(), any()))
            .thenThrow(new PromoValidationException("投放次數僅接受 1–3 次"));

        mvc.perform(post("/r/promo/apply")
                .cookie(cookie())
                .contentType(APPLICATION_JSON)
                .content(validApplyJson()))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message").value("投放次數僅接受 1–3 次"));
    }

    /** 點數不足（InsufficientCreditsException）轉 409 */
    @Test
    void 點數不足回409() throws Exception {
        givenLoggedIn(reader(10));
        when(promoProposalService.apply(anyLong(), any()))
            .thenThrow(new InsufficientCreditsException("點數不足：需要 100 點，目前 10 點"));

        mvc.perform(post("/r/promo/apply")
                .cookie(cookie())
                .contentType(APPLICATION_JSON)
                .content(validApplyJson()))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.message").value("點數不足：需要 100 點，目前 10 點"));
    }

    /** 成功申請回 200，body 含 proposalId／totalCost／credits（credits 為扣款後的權威餘額） */
    @Test
    void 成功申請回傳結果() throws Exception {
        givenLoggedIn(reader(1000));
        when(promoProposalService.apply(anyLong(), any()))
            .thenReturn(new ApplyResult(42L, 200, 800));

        mvc.perform(post("/r/promo/apply")
                .cookie(cookie())
                .contentType(APPLICATION_JSON)
                .content(validApplyJson()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.proposalId").value(42))
           .andExpect(jsonPath("$.totalCost").value(200))
           .andExpect(jsonPath("$.credits").value(800));
    }

    /** 一份格式正確的申請請求 JSON，供各測試共用 */
    private String validApplyJson() {
        return """
            {"contactName":"凱文","contactEmail":"kevin@example.com","title":"新品發表",
             "bodyText":"歡迎參加","linkText":"看更多","linkUrl":"https://example.com","placements":2}
            """;
    }
}
