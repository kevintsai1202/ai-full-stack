package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
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
import static org.mockito.ArgumentMatchers.eq;
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
        when(creditTxnRepository.findByReaderIdOrderByCreatedAtDesc(anyLong(), any(Pageable.class)))
            .thenReturn(List.of());
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
        when(creditTxnRepository.findByReaderIdOrderByCreatedAtDesc(eq(READER_ID), any(Pageable.class))).thenReturn(List.of(
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
        when(creditTxnRepository.findByReaderIdOrderByCreatedAtDesc(eq(READER_ID), any(Pageable.class))).thenReturn(List.of(
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
        // 身分只能來自 session：驗證傳給 touchEngagement 的 email 就是 reader@example.com，
        // 而不是隨便哪個字串——把 controller 改成傳其他值（例如 request body 帶的值）
        // 用 anyString() 驗不出來，必須釘死實際值。
        verify(surveyResponseRepository).touchEngagement(eq("reader@example.com"), any());
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

    /**
     * REFERRAL 交易的 note 存的是被邀者 email（發獎冪等鍵），不得顯示在邀請人的帳戶頁上，
     * 兩人可能素不相識。應改顯示固定文字「一位朋友完成訂閱」。
     *
     * <p>連 email 的 local part（{@code @} 前半段）都要驗：只驗完整字串
     * {@code "friend@example.com"} 不出現，漏抓「把 note 拆開只顯示 local part」
     * 這種局部洩漏。</p>
     */
    @Test
    void referralNoteIsMaskedWithFixedText() throws Exception {
        givenLoggedIn(reader(287));
        when(creditTxnRepository.findByReaderIdOrderByCreatedAtDesc(eq(READER_ID), any(Pageable.class)))
            .thenReturn(List.of(
                new CreditTxn(READER_ID, 50, CreditTxn.REASON_REFERRAL, null, "friend@example.com")));

        String html = mvc.perform(get("/r/me").cookie(cookie()))
            .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertFalse(html.contains("friend@example.com"), "不得洩漏完整 email");
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("friend"), "連 local part 都不得洩漏");
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("一位朋友完成訂閱"));
    }

    /**
     * 顯示名稱是本任務唯一「讀者自己可寫入、再渲染回頁面」的注入面，而且注入位置是
     * {@code value="..."} 屬性——與既有 {@code escapesTransactionNotes} 驗的元素內容位置
     * 不同（來源與位置都不同），不能互相替代。
     */
    @Test
    void displayNameIsHtmlEscapedInValueAttribute() throws Exception {
        givenLoggedIn(reader(287));
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(anyString()))
            .thenReturn(Optional.of(nameRow("\"><script>x</script>")));

        String html = mvc.perform(get("/r/me").cookie(cookie()))
            .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(html.contains("&quot;"), "雙引號必須跳脫");
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("&lt;script&gt;"), "角括號標籤必須跳脫");
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("\"><script>x</script>"), "不得出現未跳脫的原始字元序列");
    }

    /** 建一筆帶顯示名稱的名單列 */
    private SurveyResponse nameRow(String name) {
        SurveyResponse row = new SurveyResponse();
        row.setEmail("reader@example.com");
        row.setName(name);
        return row;
    }

    /** name 為 null（請求體是 {}）：應 200 且存空字串，不得拋例外 */
    @Test
    void nullNameIsStoredAsEmptyString() throws Exception {
        givenLoggedIn(reader(300));
        SurveyResponse row = nameRow("舊名稱");
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc("reader@example.com"))
            .thenReturn(Optional.of(row));

        mvc.perform(post("/api/reader/profile").cookie(cookie())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{}"))
           .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals("", row.getName());
    }

    /** 名稱前後空白必須去除 */
    @Test
    void nameIsTrimmed() throws Exception {
        givenLoggedIn(reader(300));
        SurveyResponse row = nameRow("");
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc("reader@example.com"))
            .thenReturn(Optional.of(row));

        mvc.perform(post("/api/reader/profile").cookie(cookie())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"name\":\"  凱文  \"}"))
           .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals("凱文", row.getName());
    }

    /** 41 字以上只存前 40 字（依 code point，而非 UTF-16 char） */
    @Test
    void nameLongerThan40CharsIsTruncatedTo40() throws Exception {
        givenLoggedIn(reader(300));
        SurveyResponse row = nameRow("");
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc("reader@example.com"))
            .thenReturn(Optional.of(row));
        String longName = "測".repeat(45);

        mvc.perform(post("/api/reader/profile").cookie(cookie())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + longName + "\"}"))
           .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals("測".repeat(40), row.getName());
    }

    /**
     * 清空輸入框後儲存必須真的把姓名清空成空字串，而不是保留讀者當初在問卷
     * 填的舊名稱——該欄位同時被 CSV 匯出與後台名單使用，行為必須明文鎖住。
     */
    @Test
    void emptyNameClearsPreviouslyStoredName() throws Exception {
        givenLoggedIn(reader(300));
        SurveyResponse row = nameRow("原本的姓名");
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc("reader@example.com"))
            .thenReturn(Optional.of(row));

        mvc.perform(post("/api/reader/profile").cookie(cookie())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
           .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals("", row.getName());
    }

    /**
     * 四個中文交易原因標籤都要出現。若把整個 switch 改成回空字串，
     * 既有測試（只斷言金額與 note）仍會全綠，唯有這個測試會抓到。
     */
    @Test
    void reasonLabelsAreShownForAllKnownReasons() throws Exception {
        givenLoggedIn(reader(287));
        when(creditTxnRepository.findByReaderIdOrderByCreatedAtDesc(eq(READER_ID), any(Pageable.class)))
            .thenReturn(List.of(
                new CreditTxn(READER_ID, 300, CreditTxn.REASON_SIGNUP_GRANT, null, ""),
                new CreditTxn(READER_ID, 50, CreditTxn.REASON_REFERRAL, null, "friend@example.com"),
                new CreditTxn(READER_ID, -13, CreditTxn.REASON_READ, 42L, "某篇文章"),
                new CreditTxn(READER_ID, 100, CreditTxn.REASON_ADMIN_GRANT, null, "客服補償")));

        String html = mvc.perform(get("/r/me").cookie(cookie()))
            .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(html.contains("初始贈點"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("邀請獎勵"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("解鎖文章"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("站方贈點"));
    }

    /** 交易明細只取最近 50 筆：釘住傳給 repository 的 Pageable 的 page size */
    @Test
    void transactionListIsLimitedTo50ViaPageable() throws Exception {
        givenLoggedIn(reader(287));

        mvc.perform(get("/r/me").cookie(cookie())).andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(creditTxnRepository).findByReaderIdOrderByCreatedAtDesc(eq(READER_ID), pageableCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(50, pageableCaptor.getValue().getPageSize());
        org.junit.jupiter.api.Assertions.assertEquals(0, pageableCaptor.getValue().getPageNumber());
    }

    /**
     * {@code createdAt} 是 {@code insertable=false}，測試裡直接 new 出來的 {@link CreditTxn}
     * 永遠是 null，只走得到空字串分支。這裡用 {@link ReflectionTestUtils} 塞一個
     * <b>會跨日的 UTC 值</b>：{@code 2026-07-26T22:00Z} 在台北時區（UTC+8）已經是 27 日，
     * 藉此把「換算台北時區再格式化」這件事釘住，而不只是驗證格式本身。
     */
    @Test
    void createdAtIsFormattedInTaipeiTimezoneAcrossDateBoundary() throws Exception {
        givenLoggedIn(reader(287));
        CreditTxn txn = new CreditTxn(READER_ID, -13, CreditTxn.REASON_READ, 42L, "某篇文章");
        ReflectionTestUtils.setField(txn, "createdAt", OffsetDateTime.parse("2026-07-26T22:00:00Z"));
        when(creditTxnRepository.findByReaderIdOrderByCreatedAtDesc(eq(READER_ID), any(Pageable.class)))
            .thenReturn(List.of(txn));

        String html = mvc.perform(get("/r/me").cookie(cookie()))
            .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(html.contains("2026-07-27"), "UTC 22:00 換算台北時區應為隔日 27 日");
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("2026-07-26"), "不得仍顯示 UTC 當日日期");
    }

    /** VIP 但無到期日（tier=VIP、vipExpiresAt=null）：顯示「無到期日」 */
    @Test
    void vipWithNoExpiryShowsNoExpiryLabel() throws Exception {
        Reader vip = reader(0);
        vip.setTier(Reader.TIER_VIP);
        vip.setVipExpiresAt(null);
        givenLoggedIn(vip);

        mvc.perform(get("/r/me").cookie(cookie()))
           .andExpect(content().string(containsString("無到期日")));
    }

    /**
     * 頁面模板的佔位符（{@code <!--NAV_LINKS-->}、{@code <!--TXN_LIST-->} 等）
     * 一定要被實際內容取代；漏 put 任何一個都會讓 HTML 注解字面殘留在回應裡。
     *
     * <p>注意：不能直接斷言「回應不含 {@code <!--}」——{@code me.html} 本身還有一句
     * 與 {@code /r/invite} 暫時 404 有關的、真正的 HTML 註解（非佔位符），
     * 那句不該被當成缺陷。因此逐一比對本頁用到的六個佔位符字面值。</p>
     */
    @Test
    void noTemplatePlaceholderIsLeftUnfilled() throws Exception {
        givenLoggedIn(reader(287));

        String html = mvc.perform(get("/r/me").cookie(cookie()))
            .andReturn().getResponse().getContentAsString();

        for (String placeholder : List.of(
                "<!--NAV_LINKS-->", "<!--CREDITS-->", "<!--PREMIUM_COST-->",
                "<!--EMAIL-->", "<!--TIER_STATUS-->", "<!--DISPLAY_NAME-->", "<!--TXN_LIST-->")) {
            org.junit.jupiter.api.Assertions.assertFalse(html.contains(placeholder),
                "佔位符 " + placeholder + " 不得殘留在回應中");
        }
    }
}
