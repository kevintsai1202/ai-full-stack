package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import world.springai.survey.audience.SurveyResponse;
import world.springai.survey.audience.SurveyResponseRepository;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 我的帳戶頁：登入要求、餘額與明細顯示、個人資料更新 */
class ReaderPortalControllerTest {

    private static final long READER_ID = 3L;

    private ReaderContext readerContext;
    private CreditTxnRepository creditTxnRepository;
    private SurveyResponseRepository surveyResponseRepository;
    private ReferralService referralService;
    private CreditPolicy creditPolicy;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        readerContext = mock(ReaderContext.class);
        creditTxnRepository = mock(CreditTxnRepository.class);
        surveyResponseRepository = mock(SurveyResponseRepository.class);
        referralService = mock(ReferralService.class);
        creditPolicy = mock(CreditPolicy.class);
        when(creditPolicy.premiumCost()).thenReturn(33);
        when(creditTxnRepository.findByReaderIdOrderByCreatedAtDesc(anyLong())).thenReturn(List.of());
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(anyString()))
            .thenReturn(Optional.empty());
        when(referralService.stats(anyLong()))
            .thenReturn(new ReferralService.ReferralStats(0, 0));
        // standalone MockMvc 沒有 Spring Boot 的 WebMvcAutoConfiguration，
        // 預設的 StringHttpMessageConverter 用 ISO-8859-1，中文字會變亂碼——
        // 與 RulesPageControllerTest 相同，補上帶 UTF-8 預設值的 converter。
        // 這裡另外補上 MappingJackson2HttpMessageConverter：本頁比 RulesPageController
        // 多了 POST /api/reader/profile 的 JSON 請求體，setMessageConverters 會整組取代
        // 預設 converter 列表，若只放 String converter，JSON 請求會直接被拒（415）。
        mvc = MockMvcBuilders.standaloneSetup(new ReaderPortalController(
                new HtmlTemplate(), readerContext, creditTxnRepository,
                surveyResponseRepository, referralService, creditPolicy))
            .setMessageConverters(
                new StringHttpMessageConverter(StandardCharsets.UTF_8),
                new MappingJackson2HttpMessageConverter())
            .build();
    }

    /** 建一個帶 id 與餘額的登入讀者 */
    private Reader reader(int credits) {
        Reader r = new Reader("reader@example.com", "CODE1234");
        ReflectionTestUtils.setField(r, "id", READER_ID);
        r.setCredits(credits);
        return r;
    }

    /** 讓 readerContext 回一個登入且已確認訂閱的讀者 */
    private void givenLoggedIn(Reader r) {
        when(readerContext.resolve(anyString()))
            .thenReturn(Optional.of(new ReaderContext.Current(r, true)));
    }

    /** 建一個帶 session cookie 的請求 */
    private jakarta.servlet.http.Cookie cookie() {
        return new jakarta.servlet.http.Cookie(ReaderSessionService.COOKIE_NAME, "JWT");
    }

    /**
     * 未登入必須導向登入頁並帶 redirect，而不是回 401。
     *
     * <p>這是頁面而非 API：讀者在瀏覽器裡看到 401 空白頁是死路，
     * 導向登入頁並在登入後回到這裡才是可走完的流程。</p>
     */
    @Test
    void anonymousIsRedirectedToLoginWithRedirectBack() throws Exception {
        when(readerContext.resolve(any())).thenReturn(Optional.empty());

        mvc.perform(get("/r/me"))
           .andExpect(status().isFound())
           .andExpect(header().string("Location", "/r/login?redirect=/r/me"));
    }

    /** 已登入顯示餘額 */
    @Test
    void showsCreditBalance() throws Exception {
        givenLoggedIn(reader(287));

        mvc.perform(get("/r/me").cookie(cookie()))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("287")));
    }

    /** 顯示 email */
    @Test
    void showsEmail() throws Exception {
        givenLoggedIn(reader(287));

        mvc.perform(get("/r/me").cookie(cookie()))
           .andExpect(content().string(containsString("reader@example.com")));
    }

    /**
     * 進階內容解鎖成本必須取自 {@link CreditPolicy#premiumCost()}，不可寫死。
     *
     * <p>破壞性驗證動機：{@code setUp} 已把 mock 值設為 33（非
     * {@code CreditPolicy.DEFAULT_PREMIUM_COST} 的 10），若實作把
     * {@code PREMIUM_COST} 佔位符改成寫死的 10，本測試會抓到——用真實預設值
     * 測不出「寫死」這種最傷信任的落差（spec §5.11）。</p>
     */
    @Test
    void showsPremiumCostFromCreditPolicyNotHardcoded() throws Exception {
        givenLoggedIn(reader(287));

        mvc.perform(get("/r/me").cookie(cookie()))
           .andExpect(content().string(containsString("33")))
           .andExpect(content().string(not(containsString("10 點"))));
    }

    /** 餘額區塊旁必須有規則頁連結（spec §5.11 的三個曝光位置之三） */
    @Test
    void linksToRulesPage() throws Exception {
        givenLoggedIn(reader(287));

        mvc.perform(get("/r/me").cookie(cookie()))
           .andExpect(content().string(containsString("/r/rules")));
    }

    /** 交易明細逐筆顯示，正負號要能分辨 */
    @Test
    void listsCreditTransactions() throws Exception {
        givenLoggedIn(reader(287));
        when(creditTxnRepository.findByReaderIdOrderByCreatedAtDesc(READER_ID)).thenReturn(List.of(
            new CreditTxn(READER_ID, -13, CreditTxn.REASON_READ, 42L, "某篇進階文章"),
            new CreditTxn(READER_ID, 300, CreditTxn.REASON_SIGNUP_GRANT, null, "首次登入初始贈點")));

        String html = mvc.perform(get("/r/me").cookie(cookie()))
            .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(html.contains("-13"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("+300"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("某篇進階文章"));
    }

    /**
     * 帳本內容必須經過 HTML 跳脫。
     *
     * <p>note 欄位存的是文章主旨（由後台輸入）與被邀者 email。主旨含
     * {@code <script>} 時若不跳脫，就是一個儲存型 XSS——而且是打在
     * 讀者自己的帳戶頁上。</p>
     */
    @Test
    void escapesTransactionNotes() throws Exception {
        givenLoggedIn(reader(287));
        when(creditTxnRepository.findByReaderIdOrderByCreatedAtDesc(READER_ID)).thenReturn(List.of(
            new CreditTxn(READER_ID, -13, CreditTxn.REASON_READ, 42L, "<script>alert(1)</script>")));

        mvc.perform(get("/r/me").cookie(cookie()))
           .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
           .andExpect(content().string(containsString("&lt;script&gt;")));
    }

    /** 沒有交易時顯示空狀態，不是空白區塊 */
    @Test
    void showsEmptyStateWhenNoTransactions() throws Exception {
        givenLoggedIn(reader(300));

        mvc.perform(get("/r/me").cookie(cookie()))
           .andExpect(content().string(containsString("還沒有交易紀錄")));
    }

    /** VIP 且未到期顯示到期日 */
    @Test
    void showsVipStatusWithExpiry() throws Exception {
        Reader vip = reader(0);
        vip.setTier(Reader.TIER_VIP);
        vip.setVipExpiresAt(OffsetDateTime.parse("2027-01-31T00:00:00Z"));
        givenLoggedIn(vip);

        mvc.perform(get("/r/me").cookie(cookie()))
           .andExpect(content().string(containsString("VIP")))
           .andExpect(content().string(containsString("2027-01-31")));
    }

    /**
     * tier 為 VIP 但已到期時不得顯示成有效 VIP。
     *
     * <p>系統刻意不做自動降級排程（spec §13.5），所以資料庫裡會存在
     * 「tier=VIP 但 vip_expires_at 已過」的列。頁面若照 tier 顯示，
     * 讀者會以為自己還是 VIP，然後在解鎖時發現要扣點。</p>
     */
    @Test
    void expiredVipIsNotShownAsActive() throws Exception {
        Reader expired = reader(50);
        expired.setTier(Reader.TIER_VIP);
        expired.setVipExpiresAt(OffsetDateTime.now().minusDays(1));
        givenLoggedIn(expired);

        mvc.perform(get("/r/me").cookie(cookie()))
           .andExpect(content().string(containsString("已到期")));
    }

    /** 更新顯示名稱成功並更新參與度時間戳 */
    @Test
    void updatesDisplayNameAndTouchesEngagement() throws Exception {
        givenLoggedIn(reader(300));
        SurveyResponse row = new SurveyResponse();
        row.setEmail("reader@example.com");
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc("reader@example.com"))
            .thenReturn(Optional.of(row));

        mvc.perform(post("/api/reader/profile").cookie(cookie())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"name\":\"凱文\"}"))
           .andExpect(status().isOk());

        verify(surveyResponseRepository).save(row);
        verify(surveyResponseRepository).touchEngagement(anyString(), any());
        org.junit.jupiter.api.Assertions.assertEquals("凱文", row.getName());
    }

    /** 未登入不可更新個人資料 */
    @Test
    void anonymousCannotUpdateProfile() throws Exception {
        when(readerContext.resolve(any())).thenReturn(Optional.empty());

        mvc.perform(post("/api/reader/profile")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"name\":\"凱文\"}"))
           .andExpect(status().isUnauthorized());
    }

    /**
     * 個人資料只能改自己的：名單中查無此 email 時回 404 而不是建新列。
     *
     * <p>建新列會讓「讀者自行維護個人資訊」變成「讀者可以往名單中心插資料」，
     * 而名單中心的每一列都代表一份同意紀錄。</p>
     */
    @Test
    void profileUpdateDoesNotCreateAudienceRow() throws Exception {
        givenLoggedIn(reader(300));
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(anyString()))
            .thenReturn(Optional.empty());

        mvc.perform(post("/api/reader/profile").cookie(cookie())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"name\":\"凱文\"}"))
           .andExpect(status().isNotFound());

        verify(surveyResponseRepository, org.mockito.Mockito.never()).save(any());
    }

    /** 帳戶頁不可被共享快取（含餘額等個人資料） */
    @Test
    void isNeverSharedCached() throws Exception {
        givenLoggedIn(reader(300));

        mvc.perform(get("/r/me").cookie(cookie()))
           .andExpect(header().string("Cache-Control", "private, no-store"))
           .andExpect(header().string("Vary", "Cookie"));
    }
}
