package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.AdminKeyGuard;
import world.springai.survey.ApiExceptionHandler;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 後台讀者管理：金鑰保護、VIP 授予、加點、帳本查詢。
 *
 * <p><b>本測試看不到交易語意。</b>它用 {@code standaloneSetup} 直接 new 出
 * controller 與 {@link AdminReaderService}，沒有 Spring proxy，
 * {@code @Transactional} 完全不參與。「餘額變動與帳本寫入在同一交易內」
 * 這條核心保證由 {@link AdminReaderTransactionTest} 負責——那裡才拿得到
 * 被 proxy 的 bean。</p>
 */
class AdminReaderControllerTest {

    private static final long READER_ID = 3L;
    private static final String KEY = "X-Admin-Key";

    private AdminKeyGuard guard;
    private ReaderRepository readerRepository;
    private CreditTxnRepository creditTxnRepository;
    private ReaderAccountService readerAccountService;
    private CreditPolicy creditPolicy;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        guard = mock(AdminKeyGuard.class);
        readerRepository = mock(ReaderRepository.class);
        creditTxnRepository = mock(CreditTxnRepository.class);
        readerAccountService = mock(ReaderAccountService.class);
        creditPolicy = mock(CreditPolicy.class);
        when(creditPolicy.vipDefaultDays()).thenReturn(365);
        AdminReaderService service = new AdminReaderService(
            readerRepository, creditTxnRepository, readerAccountService);
        mvc = MockMvcBuilders.standaloneSetup(
                new AdminReaderController(guard, service, creditPolicy))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    /** 建一個帶 id 的讀者 */
    private Reader reader(String email) {
        Reader r = new Reader(email, "CODE1234");
        ReflectionTestUtils.setField(r, "id", READER_ID);
        r.setCredits(300);
        return r;
    }

    /** 讓 VIP 的條件式 UPDATE 回報「更新到 1 列」 */
    private void givenVipUpdateSucceeds() {
        when(readerRepository.updateVip(anyLong(), anyString(), any())).thenReturn(1);
    }

    /** 讓金鑰守衛一律拒絕，模擬「沒帶金鑰或金鑰錯誤」 */
    private void givenAdminKeyRejected() {
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED))
            .when(guard).verify(any());
    }

    /**
     * 斷言某個請求在金鑰不符時回 401，且沒有做任何寫入。
     *
     * <p>「沒有寫入」和「回 401」要一起檢查：只檢查狀態碼的話，
     * 「先加點再驗金鑰」這種寫法照樣會通過測試——點數已經發出去了，
     * 只是回應是 401。</p>
     */
    private void expectUnauthorizedAndNoWrite(RequestBuilder request) throws Exception {
        givenAdminKeyRejected();
        mvc.perform(request).andExpect(status().isUnauthorized());
        verify(readerRepository, never()).save(any());
        verify(readerRepository, never()).updateVip(anyLong(), anyString(), any());
        verify(readerRepository, never()).addCredits(anyLong(), anyInt());
        verify(readerRepository, never()).deductCredits(anyLong(), anyInt());
        verify(creditTxnRepository, never()).save(any());
        verify(readerAccountService, never()).findOrCreate(anyString(), any());
        verify(readerAccountService, never()).findOrCreateWithoutLogin(anyString(), any());
    }

    /**
     * 每一個端點都必須經過金鑰驗證。
     *
     * <p>逐一驗證而不是只測一個：漏掉任何一個端點的 verify，就是一個
     * 讓任何人都能授予自己 VIP 或無限加點的洞。這種漏洞不會在功能測試中
     * 出現——功能測試都會帶金鑰。</p>
     */
    @Test
    void everyEndpointRequiresAdminKey() throws Exception {
        givenAdminKeyRejected();

        mvc.perform(get("/api/admin/readers").param("q", "a"))
           .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/readers/vip").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\"}"))
           .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/admin/readers/vip").param("email", "a@b.com"))
           .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/readers/credits").contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"a@b.com\"],\"delta\":10,\"note\":\"x\"}"))
           .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/readers/ledger").param("email", "a@b.com"))
           .andExpect(status().isUnauthorized());

        // 沒有任何一個端點在金鑰不符時還做了寫入
        verify(readerRepository, never()).save(any());
        verify(readerRepository, never()).updateVip(anyLong(), anyString(), any());
        verify(readerRepository, never()).addCredits(anyLong(), anyInt());
        verify(creditTxnRepository, never()).save(any());
    }

    /**
     * 以下五個測試把「每個端點各自的未授權檢查」拆開。
     *
     * <p>與上面的合併測試重複是刻意的：合併測試在第一個斷言失敗時就停止，
     * 後面四個端點的結果看不到。拆開後，哪一個端點漏了 verify 一目了然，
     * 也確保未來新增端點時不會有人「順手把新端點加進合併測試卻忘了 verify」。</p>
     */
    @Test
    void searchRequiresAdminKey() throws Exception {
        expectUnauthorizedAndNoWrite(get("/api/admin/readers").param("q", "a"));
    }

    /** 授予 VIP 需要金鑰 */
    @Test
    void grantVipRequiresAdminKey() throws Exception {
        expectUnauthorizedAndNoWrite(post("/api/admin/readers/vip")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"a@b.com\"}"));
    }

    /** 取消 VIP 需要金鑰 */
    @Test
    void revokeVipRequiresAdminKey() throws Exception {
        expectUnauthorizedAndNoWrite(delete("/api/admin/readers/vip").param("email", "a@b.com"));
    }

    /** 加點需要金鑰（漏這個等於任何人都能給自己無限點數） */
    @Test
    void grantCreditsRequiresAdminKey() throws Exception {
        expectUnauthorizedAndNoWrite(post("/api/admin/readers/credits")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"emails\":[\"a@b.com\"],\"delta\":10,\"note\":\"x\"}"));
    }

    /** 帳本查詢需要金鑰（漏這個等於任何人都能查訂閱者的閱讀紀錄） */
    @Test
    void ledgerRequiresAdminKey() throws Exception {
        expectUnauthorizedAndNoWrite(get("/api/admin/readers/ledger").param("email", "a@b.com"));
    }

    /** 依 email 片段搜尋讀者 */
    @Test
    void searchesReadersByEmailFragment() throws Exception {
        when(readerRepository.searchByEmailPattern(anyString(), any()))
            .thenReturn(List.of(reader("kevin@example.com")));

        mvc.perform(get("/api/admin/readers").param("q", "kevin").header(KEY, "ok"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].email").value("kevin@example.com"))
           .andExpect(jsonPath("$[0].credits").value(300));

        // 關鍵字被包成「包含」語意的 LIKE 樣式
        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        verify(readerRepository).searchByEmailPattern(pattern.capture(), any());
        assertEquals("%kevin%", pattern.getValue());
    }

    /**
     * 搜尋關鍵字為空白必須回 400，不可退化成「撈全表」。
     *
     * <p>舊版只做 {@code trim()} 就送出去，{@code ?q=} 會變成
     * {@code like '%%'}，一次把整張 reader 表（email、餘額、邀請碼、
     * 最後登入時間）序列化成單一回應。</p>
     */
    @Test
    void blankSearchQueryIsRejected() throws Exception {
        mvc.perform(get("/api/admin/readers").param("q", "   ").header(KEY, "ok"))
           .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/readers").param("q", "").header(KEY, "ok"))
           .andExpect(status().isBadRequest());

        verify(readerRepository, never()).searchByEmailPattern(anyString(), any());
    }

    /**
     * 搜尋關鍵字裡的 LIKE 萬用字元必須被跳脫成字面字元。
     *
     * <p>沒跳脫的話，{@code ?q=%} 或 {@code ?q=_} 就是一次全表匯出——
     * 參數雖然有綁定（沒有 SQL injection），但 {@code %} 與 {@code _}
     * 在 LIKE 裡是萬用字元，綁定救不了。</p>
     */
    @Test
    void searchEscapesLikeWildcards() throws Exception {
        when(readerRepository.searchByEmailPattern(anyString(), any())).thenReturn(List.of());

        mvc.perform(get("/api/admin/readers").param("q", "%_\\a").header(KEY, "ok"))
           .andExpect(status().isOk());

        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        verify(readerRepository).searchByEmailPattern(pattern.capture(), any());
        // 跳脫字元本身要先跳脫，否則 \ 會把後面剛加上的跳脫字元吃掉
        assertEquals("%\\%\\_\\\\a%", pattern.getValue());
    }

    /**
     * 搜尋必須帶筆數上限。
     *
     * <p>沒有上限時，一個能匹配大量讀者的關鍵字（例如 {@code @gmail.com}）
     * 就會把整張表塞進單一回應。</p>
     */
    @Test
    void searchIsCapped() throws Exception {
        when(readerRepository.searchByEmailPattern(anyString(), any())).thenReturn(List.of());

        mvc.perform(get("/api/admin/readers").param("q", "a").header(KEY, "ok"))
           .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(readerRepository).searchByEmailPattern(anyString(), pageable.capture());
        assertEquals(AdminReaderService.MAX_SEARCH_RESULTS, pageable.getValue().getPageSize());
    }

    /**
     * 搜尋結果不得夾帶登入憑證等不必要欄位。
     *
     * <p>後台看得到訂閱者資料是正常的，但回應只該有管理需要的欄位。
     * 多帶一個 token 欄位就是一條「後台頁面的 HTML 原始碼裡有可直接冒用的憑證」
     * 的外洩路徑。</p>
     */
    @Test
    void summaryDoesNotLeakCredentials() throws Exception {
        when(readerRepository.searchByEmailPattern(anyString(), any()))
            .thenReturn(List.of(reader("kevin@example.com")));

        mvc.perform(get("/api/admin/readers").param("q", "kevin").header(KEY, "ok"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].token").doesNotExist())
           .andExpect(jsonPath("$[0].sessionToken").doesNotExist())
           .andExpect(jsonPath("$[0].loginToken").doesNotExist());
    }

    /**
     * 摘要的 JSON 欄位順序必須穩定。
     *
     * <p>{@code HashMap} 的順序取決於雜湊值，新增一個欄位就可能讓既有欄位
     * 全部重排；回應難以肉眼比對，也做不了字面快照測試。</p>
     */
    @Test
    void summaryFieldOrderIsStable() throws Exception {
        when(readerRepository.searchByEmailPattern(anyString(), any()))
            .thenReturn(List.of(reader("kevin@example.com")));

        String body = mvc.perform(get("/api/admin/readers").param("q", "k").header(KEY, "ok"))
           .andExpect(status().isOk())
           .andReturn().getResponse().getContentAsString();

        List<String> order = List.of("email", "tier", "vipActive", "vipExpiresAt",
            "credits", "referralCode", "lastLoginAt");
        int previous = -1;
        for (String field : order) {
            int at = body.indexOf("\"" + field + "\"");
            assertTrue(at > previous, "欄位 " + field + " 的順序不符預期：" + body);
            previous = at;
        }
    }

    /** 授予 VIP：未指定天數時採用 CreditPolicy 的預設效期 */
    @Test
    void grantsVipWithDefaultDurationFromPolicy() throws Exception {
        when(readerRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(reader("a@b.com")));
        givenVipUpdateSucceeds();

        mvc.perform(post("/api/admin/readers/vip").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.tier").value(Reader.TIER_VIP));

        ArgumentCaptor<OffsetDateTime> expiresAt = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(readerRepository).updateVip(anyLong(), anyString(), expiresAt.capture());
        assertNotNull(expiresAt.getValue());
        // 預設 365 天：允許 1 天誤差以避免測試在午夜前後失敗
        long days = java.time.Duration.between(OffsetDateTime.now(), expiresAt.getValue()).toDays();
        assertTrue(days >= 363 && days <= 366, "實際天數 " + days);
    }

    /**
     * 預設天數必須真的來自 CreditPolicy，不可寫死。
     *
     * <p>把 policy 調成非預設值（30）後仍然給 365 天，就是寫死常數——
     * 後台調了設定卻沒有作用，而上面那個測試（policy 也回 365）看不出差別。</p>
     */
    @Test
    void defaultVipDurationFollowsPolicyValue() throws Exception {
        when(creditPolicy.vipDefaultDays()).thenReturn(30);
        when(readerRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(reader("a@b.com")));
        givenVipUpdateSucceeds();

        mvc.perform(post("/api/admin/readers/vip").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\"}"))
           .andExpect(status().isOk());

        verify(creditPolicy).vipDefaultDays();
        ArgumentCaptor<OffsetDateTime> expiresAt = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(readerRepository).updateVip(anyLong(), anyString(), expiresAt.capture());
        long days = java.time.Duration.between(OffsetDateTime.now(), expiresAt.getValue()).toDays();
        assertTrue(days >= 29 && days <= 31, "實際天數 " + days);
    }

    /** 可指定自訂天數 */
    @Test
    void grantsVipWithExplicitDuration() throws Exception {
        when(readerRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(reader("a@b.com")));
        givenVipUpdateSucceeds();

        mvc.perform(post("/api/admin/readers/vip").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\",\"days\":30}"))
           .andExpect(status().isOk());

        ArgumentCaptor<OffsetDateTime> expiresAt = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(readerRepository).updateVip(anyLong(), anyString(), expiresAt.capture());
        long days = java.time.Duration.between(OffsetDateTime.now(), expiresAt.getValue()).toDays();
        assertTrue(days >= 29 && days <= 31, "實際天數 " + days);
    }

    /**
     * <b>VIP 授予不得整列寫回讀者。</b>
     *
     * <p>{@link Reader} 沒有 {@code @Version} 也沒有 {@code @DynamicUpdate}，
     * {@code save(reader)} 產生的 UPDATE 會帶上所有可更新欄位，<b>包含
     * {@code credits}</b>。於是站方授予 VIP 時，SELECT 當下讀到的餘額會被整個寫回去，
     * 靜默還原這段期間讀者在別的分頁解鎖文章扣掉的點——但那筆 credit_txn 還留著，
     * {@code reader.credits} 與帳本總和就此對不起來，而且沒有任何錯誤訊息，
     * 要等對帳才會發現。故授予 VIP 只能走只碰兩欄的條件式 UPDATE。</p>
     */
    @Test
    void grantingVipDoesNotRewriteWholeReaderRow() throws Exception {
        when(readerRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(reader("a@b.com")));
        givenVipUpdateSucceeds();

        mvc.perform(post("/api/admin/readers/vip").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\",\"days\":30}"))
           .andExpect(status().isOk());

        // save() 會把 credits 一併寫回去，絕不可使用
        verify(readerRepository, never()).save(any());
        verify(readerRepository).updateVip(anyLong(), anyString(), any());
    }

    /** 同理，取消 VIP 也只能改兩欄，不得整列寫回覆蓋 credits */
    @Test
    void revokingVipDoesNotRewriteWholeReaderRow() throws Exception {
        Reader r = reader("a@b.com");
        r.setTier(Reader.TIER_VIP);
        when(readerRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(r));
        givenVipUpdateSucceeds();

        mvc.perform(delete("/api/admin/readers/vip").param("email", "a@b.com").header(KEY, "ok"))
           .andExpect(status().isOk());

        verify(readerRepository, never()).save(any());
    }

    /**
     * 對還沒有 reader 帳戶的 email 授予 VIP 時，要先建立帳戶。
     *
     * <p>這是實際會遇到的情境：課程學員名單匯入後尚未登入過，站方要先把
     * VIP 設好。若直接回 404，站方就得請學員先登入一次再回來設定——
     * 而這正是最容易漏掉的一步。</p>
     */
    @Test
    void grantingVipToUnknownEmailCreatesAccountFirst() throws Exception {
        when(readerRepository.findByEmailIgnoreCase("new@b.com")).thenReturn(Optional.empty());
        when(readerAccountService.findOrCreateWithoutLogin(anyString(), any()))
            .thenReturn(reader("new@b.com"));
        givenVipUpdateSucceeds();

        mvc.perform(post("/api/admin/readers/vip").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new@b.com\"}"))
           .andExpect(status().isOk());

        verify(readerAccountService).findOrCreateWithoutLogin(anyString(), any());
    }

    /**
     * <b>後台代為建帳不得偽造登入與參與度訊號。</b>
     *
     * <p>{@code findOrCreate} 一律 {@code setLastLoginAt(now)} 並
     * {@code touchEngagement}。站方為從未登入的學員設 VIP 時若走那條路，
     * 該讀者立刻在後台顯示「剛剛登入過」，名單中心的參與度時間戳也被推到今天——
     * 而參與度是名單評分與再行銷判斷的依據，之後就分不出誰真的來過。
     * 故後台路徑必須走 {@code findOrCreateWithoutLogin}。</p>
     */
    @Test
    void grantingVipDoesNotFakeLoginSignals() throws Exception {
        when(readerRepository.findByEmailIgnoreCase("new@b.com")).thenReturn(Optional.empty());
        when(readerAccountService.findOrCreateWithoutLogin(anyString(), any()))
            .thenReturn(reader("new@b.com"));
        givenVipUpdateSucceeds();

        mvc.perform(post("/api/admin/readers/vip").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new@b.com\"}"))
           .andExpect(status().isOk())
           // 從未登入過的讀者，摘要裡的最後登入時間必須維持空白
           .andExpect(jsonPath("$.lastLoginAt").doesNotExist());

        verify(readerAccountService, never()).findOrCreate(anyString(), any());
    }

    /** 天數為 0 或負數必須回 400，不可產生立即過期的 VIP */
    @Test
    void nonPositiveDaysIsRejected() throws Exception {
        mvc.perform(post("/api/admin/readers/vip").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\",\"days\":0}"))
           .andExpect(status().isBadRequest());

        verify(readerRepository, never()).updateVip(anyLong(), anyString(), any());
    }

    /**
     * email 空白必須回 400。
     *
     * <p>若照流程走下去，findOrCreate 會建出一列 {@code email=''} 的垃圾讀者，
     * 還連帶發了初始贈點——一筆永遠對不到人的帳本紀錄。</p>
     */
    @Test
    void blankEmailForVipIsRejected() throws Exception {
        mvc.perform(post("/api/admin/readers/vip").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"   \"}"))
           .andExpect(status().isBadRequest());

        verify(readerAccountService, never()).findOrCreate(anyString(), any());
        verify(readerAccountService, never()).findOrCreateWithoutLogin(anyString(), any());
        verify(readerRepository, never()).updateVip(anyLong(), anyString(), any());
    }

    /** 取消 VIP：tier 回 FREE 且清掉到期日 */
    @Test
    void revokesVip() throws Exception {
        Reader r = reader("a@b.com");
        r.setTier(Reader.TIER_VIP);
        r.setVipExpiresAt(OffsetDateTime.now().plusDays(30));
        when(readerRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(r));
        givenVipUpdateSucceeds();

        mvc.perform(delete("/api/admin/readers/vip").param("email", "a@b.com").header(KEY, "ok"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.tier").value(Reader.TIER_FREE))
           // 到期日必須一併清掉：留著會讓日後重新授予時看到舊日期而誤判
           .andExpect(jsonPath("$.vipExpiresAt").doesNotExist());

        ArgumentCaptor<String> tier = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<OffsetDateTime> expiresAt = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(readerRepository).updateVip(anyLong(), tier.capture(), expiresAt.capture());
        assertEquals(Reader.TIER_FREE, tier.getValue());
        assertNull(expiresAt.getValue());
    }

    /**
     * 取消 VIP 只改等級，不刪除讀者列。
     *
     * <p>讀者列上還有點數餘額、邀請碼與帳本關聯，刪掉就等於把對帳依據銷毀。</p>
     */
    @Test
    void revokingVipDoesNotDeleteReader() throws Exception {
        Reader r = reader("a@b.com");
        r.setTier(Reader.TIER_VIP);
        when(readerRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(r));
        givenVipUpdateSucceeds();

        mvc.perform(delete("/api/admin/readers/vip").param("email", "a@b.com").header(KEY, "ok"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.credits").value(300));

        verify(readerRepository, never()).delete(any());
        verify(readerRepository, never()).deleteById(anyLong());
    }

    /** 取消不存在讀者的 VIP 回 404 */
    @Test
    void revokingVipForUnknownReaderIsNotFound() throws Exception {
        when(readerRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        mvc.perform(delete("/api/admin/readers/vip").param("email", "ghost@b.com").header(KEY, "ok"))
           .andExpect(status().isNotFound());

        verify(readerRepository, never()).updateVip(anyLong(), anyString(), any());
    }

    /**
     * VIP 的條件式 UPDATE 回 0 列（讀者列在查詢與更新之間消失）時必須回 404。
     *
     * <p>回 200 會讓站方以為 VIP 設好了，實際上什麼都沒發生。</p>
     */
    @Test
    void vipUpdateAffectingZeroRowsIsNotFound() throws Exception {
        when(readerRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(reader("a@b.com")));
        when(readerRepository.updateVip(anyLong(), anyString(), any())).thenReturn(0);

        mvc.perform(post("/api/admin/readers/vip").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\"}"))
           .andExpect(status().isNotFound());
    }

    /** 批次加點：每個 email 各寫一筆帳本 */
    @Test
    void grantsCreditsToMultipleReaders() throws Exception {
        when(readerRepository.findByEmailIgnoreCase(anyString()))
            .thenReturn(Optional.of(reader("a@b.com")));
        when(readerRepository.addCredits(anyLong(), anyInt())).thenReturn(1);

        mvc.perform(post("/api/admin/readers/credits").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"a@b.com\",\"c@b.com\"],\"delta\":100,\"note\":\"2026 春季班\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.granted").value(2));

        verify(readerRepository, times(2)).addCredits(anyLong(), anyInt());
        verify(creditTxnRepository, times(2)).save(any(CreditTxn.class));
    }

    /**
     * 清單中重複的 email 會加兩次點，<b>不去重</b>。
     *
     * <p>這條把語意明文鎖住。站方貼同一個 email 兩次時，兩筆帳本各自對應一次
     * 真實的餘額變動，「餘額＝帳本總和」的不變式仍然成立；反之若「順手去重」，
     * 站方就無法用重複列表達「這個人要加兩份」，而且靜默丟掉一筆輸入
     * 比多加一次更難察覺。沒有這條測試，日後有人加上去重不會有任何測試變紅。</p>
     *
     * <p>清單刻意同時放「完全相同的字串」與「只差大小寫的字串」：前者擋掉在原始
     * 清單上去重（{@code new LinkedHashSet<>(emails)}），後者擋掉在正規化之後去重。
     * 只測一種的話，另一種去重方式照樣能悄悄溜過去。</p>
     */
    @Test
    void duplicateEmailsAreGrantedTwiceNotDeduplicated() throws Exception {
        when(readerRepository.findByEmailIgnoreCase("a@b.com"))
            .thenReturn(Optional.of(reader("a@b.com")));
        when(readerRepository.addCredits(anyLong(), anyInt())).thenReturn(1);

        mvc.perform(post("/api/admin/readers/credits").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"a@b.com\",\"a@b.com\",\"A@B.com\"],"
                    + "\"delta\":100,\"note\":\"x\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.granted").value(3));

        // 三次餘額變動、三筆帳本——數量必須一致，不變式才成立
        verify(readerRepository, times(3)).addCredits(READER_ID, 100);
        verify(creditTxnRepository, times(3)).save(any(CreditTxn.class));
    }

    /** 加點的帳本要記 ADMIN_GRANT 與說明文字（客訴對帳靠這個） */
    @Test
    void adminGrantRecordsReasonAndNote() throws Exception {
        when(readerRepository.findByEmailIgnoreCase(anyString()))
            .thenReturn(Optional.of(reader("a@b.com")));
        when(readerRepository.addCredits(anyLong(), anyInt())).thenReturn(1);

        mvc.perform(post("/api/admin/readers/credits").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"a@b.com\"],\"delta\":100,\"note\":\"2026 春季班\"}"))
           .andExpect(status().isOk());

        ArgumentCaptor<CreditTxn> captor = ArgumentCaptor.forClass(CreditTxn.class);
        verify(creditTxnRepository).save(captor.capture());
        assertEquals(CreditTxn.REASON_ADMIN_GRANT, captor.getValue().getReason());
        assertEquals("2026 春季班", captor.getValue().getNote());
        assertEquals(100, captor.getValue().getDelta());
    }

    /**
     * 加點必須同時更新餘額與寫帳本，兩者缺一不可。
     *
     * <p>reader.credits 是 credit_txn 的物化總和。只加餘額不寫帳本 →
     * 對帳時憑空多出點數；只寫帳本不加餘額 → 讀者看得到紀錄卻沒有點數可用。
     * 兩種都會讓「餘額永遠可由帳本重算」這條不變式失效，之後任何客訴都無法查證。</p>
     */
    @Test
    void grantUpdatesBalanceAndLedgerTogether() throws Exception {
        when(readerRepository.findByEmailIgnoreCase(anyString()))
            .thenReturn(Optional.of(reader("a@b.com")));
        when(readerRepository.addCredits(anyLong(), anyInt())).thenReturn(1);

        mvc.perform(post("/api/admin/readers/credits").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"a@b.com\"],\"delta\":100,\"note\":\"x\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.granted").value(1));

        verify(readerRepository, times(1)).addCredits(READER_ID, 100);
        ArgumentCaptor<CreditTxn> captor = ArgumentCaptor.forClass(CreditTxn.class);
        verify(creditTxnRepository, times(1)).save(captor.capture());
        // 帳本的 delta 必須與實際加到餘額上的數字一致，否則重算不出餘額
        assertEquals(100, captor.getValue().getDelta());
        assertEquals(READER_ID, captor.getValue().getReaderId());
    }

    /**
     * 說明文字必填。
     *
     * <p>ADMIN_GRANT 沒有說明就無法對帳——半年後看到「某人 +500 點」
     * 卻不知道為什麼，這筆就變成永遠的疑問。帳本是只增不改的，
     * 事後補不了說明。</p>
     */
    @Test
    void adminGrantRequiresNote() throws Exception {
        mvc.perform(post("/api/admin/readers/credits").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"a@b.com\"],\"delta\":100,\"note\":\"  \"}"))
           .andExpect(status().isBadRequest());

        verify(creditTxnRepository, never()).save(any());
    }

    /**
     * 超長說明必須回 400，<b>不可靜默截斷</b>。
     *
     * <p>DB 欄位是 TEXT，200 只是後台顯示上的偏好，沒有技術理由。
     * 帳本只增不改，被截掉的尾巴永遠補不回來——note 又正是對帳依據，
     * 該丟哪一半必須由站方自己決定。</p>
     */
    @Test
    void overlongNoteIsRejectedNotTruncated() throws Exception {
        String longNote = "說".repeat(201);

        mvc.perform(post("/api/admin/readers/credits").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"a@b.com\"],\"delta\":100,\"note\":\"" + longNote + "\"}"))
           .andExpect(status().isBadRequest());

        verify(readerRepository, never()).addCredits(anyLong(), anyInt());
        verify(creditTxnRepository, never()).save(any());
    }

    /** delta 為 0 必須回 400（沒有意義的操作，只會污染帳本） */
    @Test
    void zeroDeltaIsRejected() throws Exception {
        mvc.perform(post("/api/admin/readers/credits").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"a@b.com\"],\"delta\":0,\"note\":\"x\"}"))
           .andExpect(status().isBadRequest());

        verify(readerRepository, never()).addCredits(anyLong(), anyInt());
        verify(creditTxnRepository, never()).save(any());
    }

    /** 省略 delta 等同 0，同樣回 400（不可當成「加 0 點」而寫出無意義帳本） */
    @Test
    void missingDeltaIsRejected() throws Exception {
        mvc.perform(post("/api/admin/readers/credits").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"a@b.com\"],\"note\":\"x\"}"))
           .andExpect(status().isBadRequest());

        verify(creditTxnRepository, never()).save(any());
    }

    /** email 清單為空必須回 400，而不是回一個「成功 0 筆」的假成功 */
    @Test
    void emptyEmailListIsRejected() throws Exception {
        mvc.perform(post("/api/admin/readers/credits").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[],\"delta\":10,\"note\":\"x\"}"))
           .andExpect(status().isBadRequest());
    }

    /**
     * 超過批次上限必須回 400。
     *
     * <p>Tomcat 預設 2MB 的 POST 上限約可塞 5 萬筆 email，等於在單一交易內做
     * 5 萬次 SELECT + UPDATE + INSERT：交易持續數分鐘、連線與列鎖被長時間佔住，
     * HTTP 逾時後站方也看不到究竟寫進去幾筆（實際會整批回滾，但站方不知道），
     * 失敗清單本身還可能是數 MB 的回應。</p>
     */
    @Test
    void oversizedBatchIsRejected() throws Exception {
        String emails = IntStream.rangeClosed(0, 1000)
            .mapToObj(i -> "\"user" + i + "@b.com\"")
            .collect(Collectors.joining(","));

        mvc.perform(post("/api/admin/readers/credits").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[" + emails + "],\"delta\":10,\"note\":\"x\"}"))
           .andExpect(status().isBadRequest());

        // 一筆都不可以寫進去：超量請求必須在開始迴圈之前就被擋掉
        verify(readerRepository, never()).addCredits(anyLong(), anyInt());
        verify(creditTxnRepository, never()).save(any());
    }

    /** 剛好等於批次上限仍要放行（邊界不可誤擋） */
    @Test
    void batchAtExactLimitIsAccepted() throws Exception {
        when(readerRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        String emails = IntStream.range(0, 1000)
            .mapToObj(i -> "\"user" + i + "@b.com\"")
            .collect(Collectors.joining(","));

        mvc.perform(post("/api/admin/readers/credits").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[" + emails + "],\"delta\":10,\"note\":\"x\"}"))
           .andExpect(status().isOk());
    }

    /**
     * 允許扣點（負 delta），但不可讓餘額變負。
     *
     * <p>後台需要扣點的能力（誤加後修正）。用 deductCredits 的條件式 UPDATE
     * 而非 addCredits 負值：後者會讓餘額變成負數，而負餘額會讓
     * {@code credits >= cost} 永遠為假，讀者連 0 點狀態的提示都看不對。</p>
     */
    @Test
    void negativeDeltaCannotDriveBalanceBelowZero() throws Exception {
        when(readerRepository.findByEmailIgnoreCase(anyString()))
            .thenReturn(Optional.of(reader("a@b.com")));
        // 餘額 300，要扣 500 → 條件式扣款回 0 列
        when(readerRepository.deductCredits(anyLong(), anyInt())).thenReturn(0);

        mvc.perform(post("/api/admin/readers/credits").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"a@b.com\"],\"delta\":-500,\"note\":\"修正誤加\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.granted").value(0))
           .andExpect(jsonPath("$.failed").value(1));

        // 扣款失敗就不該寫帳本，否則餘額與帳本總和會不一致
        verify(creditTxnRepository, never()).save(any());
        // 絕不可改用 addCredits 送負值繞過餘額下限
        verify(readerRepository, never()).addCredits(anyLong(), anyInt());
    }

    /** 扣點成功時帳本記的 delta 必須是負數，重算才能得到扣後餘額 */
    @Test
    void successfulDeductionRecordsNegativeDelta() throws Exception {
        when(readerRepository.findByEmailIgnoreCase(anyString()))
            .thenReturn(Optional.of(reader("a@b.com")));
        when(readerRepository.deductCredits(anyLong(), anyInt())).thenReturn(1);

        mvc.perform(post("/api/admin/readers/credits").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"a@b.com\"],\"delta\":-50,\"note\":\"修正誤加\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.granted").value(1))
           .andExpect(jsonPath("$.failed").value(0));

        verify(readerRepository).deductCredits(READER_ID, 50);
        ArgumentCaptor<CreditTxn> captor = ArgumentCaptor.forClass(CreditTxn.class);
        verify(creditTxnRepository).save(captor.capture());
        assertEquals(-50, captor.getValue().getDelta());
    }

    /**
     * 加點的條件式 UPDATE 回 0 列（讀者列在查詢與更新之間消失）時，
     * 必須計為失敗且不寫帳本。
     *
     * <p>這是最容易被寫成「靜默略過」的地方：忽略回傳值直接寫帳本，
     * 就產生一筆沒有對應餘額變動的帳本列，reader.credits 再也無法由
     * credit_txn 重算——而且沒有任何錯誤訊息，要等到對帳時才會發現。</p>
     */
    @Test
    void addCreditsReturningZeroIsFailureWithoutLedgerRow() throws Exception {
        when(readerRepository.findByEmailIgnoreCase(anyString()))
            .thenReturn(Optional.of(reader("a@b.com")));
        when(readerRepository.addCredits(anyLong(), anyInt())).thenReturn(0);

        mvc.perform(post("/api/admin/readers/credits").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"a@b.com\"],\"delta\":100,\"note\":\"x\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.granted").value(0))
           .andExpect(jsonPath("$.failed").value(1))
           .andExpect(jsonPath("$.failedEmails[0]").value("a@b.com"));

        verify(creditTxnRepository, never()).save(any());
    }

    /** 查不到的 email 計入 failed 而不中斷整批 */
    @Test
    void unknownEmailsAreReportedNotFatal() throws Exception {
        when(readerRepository.findByEmailIgnoreCase("known@b.com"))
            .thenReturn(Optional.of(reader("known@b.com")));
        when(readerRepository.findByEmailIgnoreCase("ghost@b.com")).thenReturn(Optional.empty());
        when(readerRepository.addCredits(anyLong(), anyInt())).thenReturn(1);

        mvc.perform(post("/api/admin/readers/credits").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"known@b.com\",\"ghost@b.com\"],\"delta\":50,\"note\":\"x\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.granted").value(1))
           .andExpect(jsonPath("$.failed").value(1));
    }

    /**
     * 逐筆獨立語意：清單中間有一筆失敗時，它後面的 email 仍要照樣加點。
     *
     * <p>這一條釘住「不是全有全無」。貼一整班學員名單時，其中一個打錯字
     * 不該讓排在後面的人都拿不到點數；而且失敗的那一筆不會留下帳本列，
     * 所以成功的那些筆餘額與帳本依然一致。</p>
     */
    @Test
    void failureInMiddleDoesNotAbortRemainingEmails() throws Exception {
        when(readerRepository.findByEmailIgnoreCase("first@b.com"))
            .thenReturn(Optional.of(reader("first@b.com")));
        when(readerRepository.findByEmailIgnoreCase("ghost@b.com")).thenReturn(Optional.empty());
        when(readerRepository.findByEmailIgnoreCase("last@b.com"))
            .thenReturn(Optional.of(reader("last@b.com")));
        when(readerRepository.addCredits(anyLong(), anyInt())).thenReturn(1);

        mvc.perform(post("/api/admin/readers/credits").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"first@b.com\",\"ghost@b.com\",\"last@b.com\"],"
                    + "\"delta\":50,\"note\":\"x\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.granted").value(2))
           .andExpect(jsonPath("$.failed").value(1))
           .andExpect(jsonPath("$.failedEmails[0]").value("ghost@b.com"));

        // 失敗的那一筆之後仍有兩次加點與兩筆帳本
        verify(readerRepository, times(2)).addCredits(anyLong(), anyInt());
        verify(creditTxnRepository, times(2)).save(any(CreditTxn.class));
    }

    /** email 大小寫與前後空白不影響查詢；失敗清單回報正規化後的 email */
    @Test
    void emailsAreNormalizedBeforeLookup() throws Exception {
        when(readerRepository.findByEmailIgnoreCase("ghost@b.com")).thenReturn(Optional.empty());

        mvc.perform(post("/api/admin/readers/credits").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"  Ghost@B.com \"],\"delta\":50,\"note\":\"x\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.failedEmails[0]").value("ghost@b.com"));

        verify(readerRepository).findByEmailIgnoreCase("ghost@b.com");
    }

    /**
     * email 正規化必須指定 {@code Locale.ROOT}。
     *
     * <p>土耳其語系（tr-TR）下無參數的 {@code toLowerCase()} 會把 {@code I}
     * 轉成 {@code ı}（無點小寫 i），正規化結果與資料庫裡的 email 對不起來，
     * 該讀者就此查不到——而伺服器的預設語系是部署環境決定的。</p>
     */
    @Test
    void emailNormalizationIsLocaleIndependent() {
        java.util.Locale original = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            assertEquals("kevin@example.com", AdminReaderService.normalizeEmail(" KEVIN@EXAMPLE.COM "));
        } finally {
            java.util.Locale.setDefault(original);
        }
    }

    /** 帳本查詢回該讀者的全部交易 */
    @Test
    void returnsLedgerForReader() throws Exception {
        when(readerRepository.findByEmailIgnoreCase("a@b.com"))
            .thenReturn(Optional.of(reader("a@b.com")));
        when(creditTxnRepository.findByReaderIdOrderByCreatedAtDesc(READER_ID))
            .thenReturn(List.of(new CreditTxn(READER_ID, -10, CreditTxn.REASON_READ, 42L, "某文章")));

        mvc.perform(get("/api/admin/readers/ledger").param("email", "a@b.com").header(KEY, "ok"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].delta").value(-10))
           .andExpect(jsonPath("$[0].reason").value("READ"));
    }

    /** 查不到讀者時帳本回 404 */
    @Test
    void ledgerForUnknownReaderIsNotFound() throws Exception {
        when(readerRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        mvc.perform(get("/api/admin/readers/ledger").param("email", "ghost@b.com").header(KEY, "ok"))
           .andExpect(status().isNotFound());
    }
}
