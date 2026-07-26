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
        // ReaderProfileService 用真貨（而非 mock）：本檔既有的個人資料測試斷言的是
        // surveyResponseRepository 的實際寫入，把服務 mock 掉會讓那些斷言全部落空。
        // 注意這裡沒有 Spring proxy，所以它的 @Transactional 在本檔完全不參與——
        // 交易是否真的生效由 ReaderProfileTransactionTest 負責。
        mvc = MockMvcBuilders.standaloneSetup(new ReaderPortalController(
                new HtmlTemplate(), readerContext, creditTxnRepository,
                surveyResponseRepository, referralService, creditPolicy,
                new ReaderProfileService(surveyResponseRepository),
                "https://survey.example.com"))
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
        nameRow(null);

        mvc.perform(post("/api/reader/profile").cookie(cookie())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"name\":\"凱文\"}"))
           .andExpect(status().isOk());

        assertStoredName("凱文");
        // 身分只能來自 session：驗證傳給 touchEngagement 的 email 就是 reader@example.com，
        // 而不是隨便哪個字串——把 controller 改成傳其他值（例如 request body 帶的值）
        // 用 anyString() 驗不出來，必須釘死實際值。
        verify(surveyResponseRepository).touchEngagement(eq("reader@example.com"), any());
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
        // nameRow 自己已經 stub 好 findFirstBy...，不可再包在另一個 when(...) 的引數裡
        // （Mockito 會判為 UnfinishedStubbing）
        nameRow("\"><script>x</script>");

        String html = mvc.perform(get("/r/me").cookie(cookie()))
            .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(html.contains("&quot;"), "雙引號必須跳脫");
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("&lt;script&gt;"), "角括號標籤必須跳脫");
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("\"><script>x</script>"), "不得出現未跳脫的原始字元序列");
    }

    /**
     * 建一筆帶顯示名稱的名單列，並讓「只寫 name 一欄的條件式 UPDATE」回報成功。
     *
     * <p>{@code id} 是必要的：改名走的是 {@code updateName(id, name)} 而不是
     * {@code save(entity)}——後者會把 {@code consent}／{@code unsubscribed} 一起
     * 整列寫回，覆蓋併發的退訂（見 {@code ReaderProfileNamePersistenceTest}）。</p>
     */
    private SurveyResponse nameRow(String name) {
        SurveyResponse row = new SurveyResponse();
        row.setId(77L);
        row.setEmail("reader@example.com");
        row.setName(name);
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc("reader@example.com"))
            .thenReturn(Optional.of(row));
        when(surveyResponseRepository.updateName(eq(77L), any())).thenReturn(1);
        return row;
    }

    /**
     * 斷言「實際寫入資料庫的顯示名稱」是預期值。
     *
     * <p>不能再斷言 {@code row.getName()}：實作刻意<b>不對受管理的 entity 呼叫
     * setter</b>——只要碰了 setter，Hibernate 的 dirty check 就會在提交時自己補一道
     * 帶全欄位的 UPDATE，等於繞過條件式 UPDATE。因此要驗的是傳給
     * {@code updateName} 的那個值。</p>
     */
    private void assertStoredName(String expected) {
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(surveyResponseRepository).updateName(eq(77L), stored.capture());
        org.junit.jupiter.api.Assertions.assertEquals(expected, stored.getValue());
        // 絕不整列寫回：那會把同意與退訂狀態覆蓋回 SELECT 當下的舊值
        verify(surveyResponseRepository, org.mockito.Mockito.never()).save(any(SurveyResponse.class));
    }

    /** name 為 null（請求體是 {}）：應 200 且存空字串，不得拋例外 */
    @Test
    void nullNameIsStoredAsEmptyString() throws Exception {
        givenLoggedIn(reader(300));
        nameRow("舊名稱");

        mvc.perform(post("/api/reader/profile").cookie(cookie())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{}"))
           .andExpect(status().isOk());

        assertStoredName("");
    }

    /** 名稱前後空白必須去除 */
    @Test
    void nameIsTrimmed() throws Exception {
        givenLoggedIn(reader(300));
        nameRow("");

        mvc.perform(post("/api/reader/profile").cookie(cookie())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"name\":\"  凱文  \"}"))
           .andExpect(status().isOk());

        assertStoredName("凱文");
    }

    /** 41 字以上只存前 40 字（依 code point，而非 UTF-16 char） */
    @Test
    void nameLongerThan40CharsIsTruncatedTo40() throws Exception {
        givenLoggedIn(reader(300));
        nameRow("");
        String longName = "測".repeat(45);

        mvc.perform(post("/api/reader/profile").cookie(cookie())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + longName + "\"}"))
           .andExpect(status().isOk());

        assertStoredName("測".repeat(40));
    }

    /**
     * 清空輸入框後儲存必須真的把姓名清空成空字串，而不是保留讀者當初在問卷
     * 填的舊名稱——該欄位同時被 CSV 匯出與後台名單使用，行為必須明文鎖住。
     */
    @Test
    void emptyNameClearsPreviouslyStoredName() throws Exception {
        givenLoggedIn(reader(300));
        nameRow("原本的姓名");

        mvc.perform(post("/api/reader/profile").cookie(cookie())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
           .andExpect(status().isOk());

        assertStoredName("");
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

    // ------------------------------------------------------------------
    // /r/invite：我的邀請
    // ------------------------------------------------------------------

    /** 未登入導向登入頁並帶回跳目標 */
    @Test
    void anonymousInviteRedirectsToLogin() throws Exception {
        when(readerContext.resolve(any())).thenReturn(Optional.empty());

        mvc.perform(get("/r/invite"))
           .andExpect(status().isFound())
           .andExpect(header().string("Location", "/r/login?redirect=/r/invite"));
    }

    /** 顯示完整的邀請連結（含 ?ref= 與讀者自己的邀請碼） */
    @Test
    void showsFullInviteLinkWithReferralCode() throws Exception {
        givenLoggedIn(reader(300));

        mvc.perform(get("/r/invite").cookie(cookie()))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("https://survey.example.com/r/?ref=CODE1234")));
    }

    /** 顯示邀請成效：人數與累計點數 */
    @Test
    void showsReferralStats() throws Exception {
        givenLoggedIn(reader(300));
        when(referralService.stats(READER_ID))
            .thenReturn(new ReferralService.ReferralStats(3, 300));

        String html = mvc.perform(get("/r/invite").cookie(cookie()))
            .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(html.contains("3"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("300"));
    }

    /**
     * 每位獎勵點數必須來自 {@link CreditPolicy#referralReward()}，不可寫死。
     *
     * <p>破壞性驗證動機：把 mock 值設成 55（非 {@code DEFAULT_REFERRAL_REWARD}
     * 的 100，也非 0），若實作把 REWARD 佔位符改成寫死常數，本測試會抓到。</p>
     */
    @Test
    void rewardPerInviteComesFromPolicy() throws Exception {
        givenLoggedIn(reader(300));
        when(creditPolicy.referralReward()).thenReturn(55);

        mvc.perform(get("/r/invite").cookie(cookie()))
           .andExpect(content().string(containsString("55")));
    }

    /** 尚無成功邀請時顯示鼓勵性空狀態，而不是「0 人」的冷數字 */
    @Test
    void showsEmptyStateWithNoInvites() throws Exception {
        givenLoggedIn(reader(300));
        when(referralService.stats(READER_ID))
            .thenReturn(new ReferralService.ReferralStats(0, 0));

        mvc.perform(get("/r/invite").cookie(cookie()))
           .andExpect(content().string(containsString("還沒有人")));
    }

    /**
     * <b>人數 0 但點數不為 0 時，絕不可走空狀態。</b>
     *
     * <p>{@code ReferralStats(0, 100)} 代表「站方已經為某次邀請發了 100 點，
     * 但那位被邀者沒被計入人數」。若空狀態只看 {@code invitedCount == 0}，
     * 這一頁會印「還沒有人透過你的連結完成訂閱」——一句與帳戶頁那筆
     * 「邀請獎勵 +100」直接矛盾的假話——並且把<b>已發放的點數整個藏起來</b>，
     * 讀者在這頁一個字都看不到那 100 點。點數是實際發生的資產變動，
     * 不論人數是多少都必須顯示。</p>
     *
     * <p>破壞性驗證：把空狀態條件改回只看 {@code invitedCount == 0} → 本測試變紅。</p>
     */
    @Test
    void showsEarnedCreditsEvenWhenInvitedCountIsZero() throws Exception {
        givenLoggedIn(reader(300));
        when(referralService.stats(READER_ID))
            .thenReturn(new ReferralService.ReferralStats(0, 100));

        String html = mvc.perform(get("/r/invite").cookie(cookie()))
            .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertFalse(html.contains("還沒有人"),
            "已經發出去 100 點卻說「還沒有人透過你的連結完成訂閱」：這是一句假話");
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("100"),
            "已發放的點數被空狀態藏起來了：讀者在這頁看不到那 100 點");
    }

    /**
     * 獎勵為 0（後台關閉邀請獎勵）時，文案不得出現「拿到 0 點」這種讀起來像
     * 故障的字，必須改用講得通的說法，並沿用規則頁（{@code /r/rules}）
     * 「依值切換整段文字」的慣例。
     *
     * <p>破壞性驗證動機：若實作把 REWARD 佔位符原樣套進固定句型
     * （「你會拿到 0 點」），本測試會抓到「拿到 0 點」這個具體字串。</p>
     */
    @Test
    void rewardIntroDoesNotSayZeroPointsWhenRewardIsZero() throws Exception {
        givenLoggedIn(reader(300));
        when(creditPolicy.referralReward()).thenReturn(0);

        mvc.perform(get("/r/invite").cookie(cookie()))
           .andExpect(content().string(not(containsString("拿到 0 點"))))
           .andExpect(content().string(containsString("暫停發放")));
    }

    /**
     * 獎勵為 0 時，{@code /r/invite} 的文案必須與同一頁下半部的成效區塊一致。
     *
     * <p>成效人數是「帳本 REFERRAL 的 note」與 {@code reader.referred_by} 的聯集，
     * 所以「仍會計入邀請人數」不是空頭承諾，可以講。但<b>獎勵為 0 時帳本那一邊完全
     * 不寫</b>（{@code rewardFor} 刻意不占用冪等鍵），聯集只剩 {@code referred_by}，
     * 而它是被邀者<b>首次登入建立帳戶</b>時才寫入——因此這個分支的文案必須把
     * 「首次登入」這個條件寫出來，否則讀者會在朋友「已確認訂閱、還沒登入」的空窗期
     * 盯著沒動的數字以為壞了。點數則確實暫停，不得暗示還會累積。</p>
     *
     * <p>注意這個附註<b>只在 0 值分支需要</b>：獎勵 &gt; 0 時帳本在確認訂閱當下就寫，
     * 人數立刻成長，靜態文案「點開確認信才算一次成功邀請」本身就是準確的。</p>
     */
    @Test
    void zeroRewardInviteCopyStatesExactlyWhatIsRecorded() throws Exception {
        givenLoggedIn(reader(300));
        when(creditPolicy.referralReward()).thenReturn(0);

        String html = mvc.perform(get("/r/invite").cookie(cookie()))
            .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(html.contains("暫停發放"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("邀請人數"),
            "沒有告訴讀者人數仍會計入——那是 V9 之後真的成立的事");
        // 斷言整句片段而非孤立的「首次登入」：後者容易在頁面其他地方（例如初始贈點
        // 說明）出現而變成恆真的斷言。這裡要守的是「人數計入的條件裡包含首次登入」。
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("並首次登入後"),
            "承諾了程式不保證的時序：獎勵為 0 時帳本不寫，人數只能靠 referred_by，"
                + "而它是被邀者首次登入建帳時才寫入");
        for (String promise : java.util.List.of("點數仍會累計", "仍會獲得點數", "仍會拿到點數", "點數照樣累計")) {
            org.junit.jupiter.api.Assertions.assertFalse(html.contains(promise),
                "承諾了「" + promise + "」，但獎勵為 0 時 rewardFor 根本不寫帳本");
        }
    }

    /** {@code /r/me} 的「每篇 N 點」必須與規則頁一樣標明是參考值，實際以文章頁為準 */
    @Test
    void mePagePresentsPremiumCostAsTypicalNotExact() throws Exception {
        givenLoggedIn(reader(300));

        mvc.perform(get("/r/me").cookie(cookie()))
           .andExpect(content().string(containsString("通常每篇 33 點")))
           .andExpect(content().string(containsString("實際點數以各篇文章頁顯示為準")));
    }

    /**
     * 必須說明「被邀者點確認信才算成功」。
     *
     * <p>spec §5.4 明訂先講清楚以避免爭議：讀者分享了連結、朋友也訂閱了，
     * 但點數沒進來——若頁面沒事先說明，那就是一次客訴。</p>
     */
    @Test
    void explainsConfirmationRequirement() throws Exception {
        givenLoggedIn(reader(300));

        mvc.perform(get("/r/invite").cookie(cookie()))
           .andExpect(content().string(containsString("確認信")));
    }

    /** 邀請頁同樣不可被共享快取（含個人邀請碼） */
    @Test
    void invitePageIsNeverSharedCached() throws Exception {
        givenLoggedIn(reader(300));

        mvc.perform(get("/r/invite").cookie(cookie()))
           .andExpect(header().string("Cache-Control", "private, no-store"))
           .andExpect(header().string("Vary", "Cookie"));
    }

    /**
     * 個資防線：/r/invite 只能顯示彙總數字，絕不能出現任何被邀者的 email
     * （即使 local part）。{@link ReferralService.ReferralStats} 本身不帶
     * email，此測試釘住這個契約，避免日後有人「順手」改成直接查帳本明細
     * 並把 note（存的是被邀者 email）顯示出來。
     */
    @Test
    void neverLeaksInviteeEmail() throws Exception {
        givenLoggedIn(reader(300));
        when(referralService.stats(READER_ID))
            .thenReturn(new ReferralService.ReferralStats(1, 100));
        when(creditTxnRepository.findByReaderIdOrderByCreatedAtDesc(anyLong(), any(Pageable.class)))
            .thenReturn(List.of(new CreditTxn(READER_ID, 100, CreditTxn.REASON_REFERRAL, null, "friend@example.com")));

        String html = mvc.perform(get("/r/invite").cookie(cookie()))
            .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertFalse(html.contains("friend@example.com"), "不得洩漏完整 email");
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("friend"), "連 local part 都不得洩漏");
    }

    /**
     * 頁面模板的佔位符一定要被實際內容取代；漏 put 任何一個都會讓 HTML 注解
     * 字面殘留在回應裡。
     */
    @Test
    void inviteNoTemplatePlaceholderIsLeftUnfilled() throws Exception {
        givenLoggedIn(reader(300));

        String html = mvc.perform(get("/r/invite").cookie(cookie()))
            .andReturn().getResponse().getContentAsString();

        for (String placeholder : List.of(
                "<!--NAV_LINKS-->", "<!--REWARD_INTRO-->", "<!--INVITE_LINK-->",
                "<!--REFERRAL_CODE-->", "<!--STATS_BLOCK-->")) {
            org.junit.jupiter.api.Assertions.assertFalse(html.contains(placeholder),
                "佔位符 " + placeholder + " 不得殘留在回應中");
        }
    }
}
