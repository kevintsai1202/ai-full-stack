package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

/** 後台讀者管理：金鑰保護、VIP 授予、加點、帳本查詢 */
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
        mvc = MockMvcBuilders.standaloneSetup(new AdminReaderController(
                guard, readerRepository, creditTxnRepository, readerAccountService, creditPolicy))
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
        verify(readerRepository, never()).addCredits(anyLong(), anyInt());
        verify(readerRepository, never()).deductCredits(anyLong(), anyInt());
        verify(creditTxnRepository, never()).save(any());
        verify(readerAccountService, never()).findOrCreate(anyString(), any());
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
        when(readerRepository.findByEmailContainingIgnoreCaseOrderByEmailAsc("kevin"))
            .thenReturn(List.of(reader("kevin@example.com")));

        mvc.perform(get("/api/admin/readers").param("q", "kevin").header(KEY, "ok"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].email").value("kevin@example.com"))
           .andExpect(jsonPath("$[0].credits").value(300));
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
        when(readerRepository.findByEmailContainingIgnoreCaseOrderByEmailAsc(anyString()))
            .thenReturn(List.of(reader("kevin@example.com")));

        mvc.perform(get("/api/admin/readers").param("q", "kevin").header(KEY, "ok"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].token").doesNotExist())
           .andExpect(jsonPath("$[0].sessionToken").doesNotExist())
           .andExpect(jsonPath("$[0].loginToken").doesNotExist());
    }

    /** 授予 VIP：未指定天數時採用 CreditPolicy 的預設效期 */
    @Test
    void grantsVipWithDefaultDurationFromPolicy() throws Exception {
        Reader r = reader("a@b.com");
        when(readerRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(r));
        when(readerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/api/admin/readers/vip").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\"}"))
           .andExpect(status().isOk());

        ArgumentCaptor<Reader> captor = ArgumentCaptor.forClass(Reader.class);
        verify(readerRepository).save(captor.capture());
        assertEquals(Reader.TIER_VIP, captor.getValue().getTier());
        assertNotNull(captor.getValue().getVipExpiresAt());
        // 預設 365 天：允許 1 天誤差以避免測試在午夜前後失敗
        long days = java.time.Duration.between(
            OffsetDateTime.now(), captor.getValue().getVipExpiresAt()).toDays();
        org.junit.jupiter.api.Assertions.assertTrue(days >= 363 && days <= 366, "實際天數 " + days);
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
        Reader r = reader("a@b.com");
        when(readerRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(r));
        when(readerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/api/admin/readers/vip").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\"}"))
           .andExpect(status().isOk());

        verify(creditPolicy).vipDefaultDays();
        ArgumentCaptor<Reader> captor = ArgumentCaptor.forClass(Reader.class);
        verify(readerRepository).save(captor.capture());
        long days = java.time.Duration.between(
            OffsetDateTime.now(), captor.getValue().getVipExpiresAt()).toDays();
        org.junit.jupiter.api.Assertions.assertTrue(days >= 29 && days <= 31, "實際天數 " + days);
    }

    /** 可指定自訂天數 */
    @Test
    void grantsVipWithExplicitDuration() throws Exception {
        Reader r = reader("a@b.com");
        when(readerRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(r));
        when(readerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/api/admin/readers/vip").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\",\"days\":30}"))
           .andExpect(status().isOk());

        ArgumentCaptor<Reader> captor = ArgumentCaptor.forClass(Reader.class);
        verify(readerRepository).save(captor.capture());
        long days = java.time.Duration.between(
            OffsetDateTime.now(), captor.getValue().getVipExpiresAt()).toDays();
        org.junit.jupiter.api.Assertions.assertTrue(days >= 29 && days <= 31, "實際天數 " + days);
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
        when(readerAccountService.findOrCreate(anyString(), any())).thenReturn(reader("new@b.com"));
        when(readerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/api/admin/readers/vip").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new@b.com\"}"))
           .andExpect(status().isOk());

        verify(readerAccountService).findOrCreate(anyString(), any());
    }

    /** 天數為 0 或負數必須回 400，不可產生立即過期的 VIP */
    @Test
    void nonPositiveDaysIsRejected() throws Exception {
        mvc.perform(post("/api/admin/readers/vip").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\",\"days\":0}"))
           .andExpect(status().isBadRequest());

        verify(readerRepository, never()).save(any());
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
        verify(readerRepository, never()).save(any());
    }

    /** 取消 VIP：tier 回 FREE 且清掉到期日 */
    @Test
    void revokesVip() throws Exception {
        Reader r = reader("a@b.com");
        r.setTier(Reader.TIER_VIP);
        r.setVipExpiresAt(OffsetDateTime.now().plusDays(30));
        when(readerRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(r));
        when(readerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(delete("/api/admin/readers/vip").param("email", "a@b.com").header(KEY, "ok"))
           .andExpect(status().isOk());

        ArgumentCaptor<Reader> captor = ArgumentCaptor.forClass(Reader.class);
        verify(readerRepository).save(captor.capture());
        assertEquals(Reader.TIER_FREE, captor.getValue().getTier());
        // 到期日必須一併清掉：留著會讓日後重新授予時看到舊日期而誤判
        assertNull(captor.getValue().getVipExpiresAt());
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
        when(readerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

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

        verify(readerRepository, never()).save(any());
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
