package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import world.springai.survey.ApiExceptionHandler;
import world.springai.survey.newsletter.Campaign;
import world.springai.survey.newsletter.CampaignRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 解鎖端點：授權分派、結果回報與併發處理。
 *
 * <p>授權判斷全部來自 {@link AccessDecisionService#decide}（此處為 mock），
 * controller 不得自行判斷 tier／VIP／訂閱／發布狀態——因此每個拒絕案例都以
 * 「decide 回什麼 reason」為輸入，並斷言 {@code unlockService} 絕不被呼叫。</p>
 */
class UnlockControllerTest {

    private static final long READER_ID = 3L;
    private static final long CAMPAIGN_ID = 42L;
    private static final int COST = 10;
    /** 資料庫中的權威餘額，用於驗證回應不是回傳假值 */
    private static final int DB_CREDITS = 287;

    private CampaignRepository campaignRepository;
    private ReaderContext readerContext;
    private UnlockService unlockService;
    private AccessDecisionService accessDecisionService;
    private ReaderRepository readerRepository;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        campaignRepository = mock(CampaignRepository.class);
        readerContext = mock(ReaderContext.class);
        unlockService = mock(UnlockService.class);
        accessDecisionService = mock(AccessDecisionService.class);
        readerRepository = mock(ReaderRepository.class);
        when(accessDecisionService.resolveCost(any())).thenReturn(COST);
        mvc = MockMvcBuilders
            .standaloneSetup(new UnlockController(campaignRepository, readerContext, unlockService,
                accessDecisionService, readerRepository))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    /** 建一篇已發布的 PREMIUM 文章 */
    private Campaign article() {
        Campaign c = new Campaign("主旨", "# 內容", "<h1>內容</h1>", null, null, "now", null, 0, "sent");
        c.setTier(Campaign.TIER_PREMIUM);
        c.setCreditCost(COST);
        c.setSlug("my-post");
        c.setPublishedAt(OffsetDateTime.now().minusDays(1));
        ReflectionTestUtils.setField(c, "id", CAMPAIGN_ID);
        return c;
    }

    /** 建一位讀者物件（session 快照） */
    private Reader newReader(int credits) {
        Reader reader = new Reader("r@b.com", "CODE1234");
        ReflectionTestUtils.setField(reader, "id", READER_ID);
        reader.setCredits(credits);
        return reader;
    }

    /**
     * 讓 readerContext 回一個已確認訂閱的登入讀者，並讓資料庫的權威餘額
     * 與 session 快照刻意不同——回應中的 credits 若取快照就會被抓到。
     */
    private void givenLoggedInSubscriber() {
        Reader reader = newReader(999);
        when(readerContext.resolve(anyString()))
            .thenReturn(Optional.of(new ReaderContext.Current(reader, true)));
        when(readerRepository.findById(READER_ID)).thenReturn(Optional.of(newReader(DB_CREDITS)));
    }

    /** 讓 decide() 回傳指定的判定原因 */
    private void givenDecision(AccessDecisionService.Access access,
                               AccessDecisionService.Reason reason, int shortfall) {
        when(accessDecisionService.decide(any(), anyBoolean(), any(), any()))
            .thenReturn(new AccessDecisionService.Decision(access, reason, shortfall));
    }

    /** 帶 session cookie 的 POST */
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postUnlock(String slug) {
        return post("/api/reader/unlock/" + slug).cookie(
            new jakarta.servlet.http.Cookie(ReaderSessionService.COOKIE_NAME, "JWT"));
    }

    /** 成功解鎖回 200 與結果明細 */
    @Test
    void successfulUnlockReturnsOutcomeAndBalance() throws Exception {
        givenLoggedInSubscriber();
        when(campaignRepository.findBySlug("my-post")).thenReturn(Optional.of(article()));
        givenDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.CAN_UNLOCK, 0);
        when(unlockService.unlock(anyLong(), any(), any()))
            .thenReturn(new UnlockService.Result(UnlockService.Outcome.UNLOCKED, COST, 290));

        mvc.perform(postUnlock("my-post"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.outcome").value("UNLOCKED"))
           .andExpect(jsonPath("$.cost").value(COST))
           .andExpect(jsonPath("$.credits").value(290));
    }

    /**
     * 未登入回 401，且絕不呼叫解鎖。
     *
     * <p>審查發現：只驗 HTTP 狀態碼證明不了 controller 真的把「未登入」餵給
     * decide()——把 {@code subscribed} 寫死成 {@code true} 這 12 個測試仍然全綠，
     * 因為 reason 完全由 mock 決定。這裡額外用 {@link ArgumentCaptor} 斷言
     * 傳給 {@code decide()} 的 reader 為 null、subscribed 為 false，仿照
     * {@code ReaderPageControllerTest} 已有的先例。</p>
     */
    @Test
    void anonymousRequestIsRejected() throws Exception {
        when(readerContext.resolve(any())).thenReturn(Optional.empty());
        Campaign campaign = article();
        when(campaignRepository.findBySlug("my-post")).thenReturn(Optional.of(campaign));
        givenDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_LOGGED_IN, 0);

        mvc.perform(post("/api/reader/unlock/my-post"))
           .andExpect(status().isUnauthorized());

        verify(unlockService, never()).unlock(anyLong(), any(), any());

        ArgumentCaptor<Reader> readerCaptor = ArgumentCaptor.forClass(Reader.class);
        ArgumentCaptor<Boolean> subscribedCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Campaign> campaignCaptor = ArgumentCaptor.forClass(Campaign.class);
        verify(accessDecisionService, times(1))
            .decide(readerCaptor.capture(), subscribedCaptor.capture(), campaignCaptor.capture(), any());
        assertNull(readerCaptor.getValue(), "未登入時傳給 decide() 的 reader 必須是 null");
        assertFalse(subscribedCaptor.getValue(), "未登入時傳給 decide() 的 subscribed 必須是 false");
        assertSame(campaign, campaignCaptor.getValue(), "傳給 decide() 的必須是 findBySlug 查回來的那一篇");
    }

    /**
     * 未確認訂閱者不可解鎖。
     *
     * <p>沒有這道檢查，一個登入但未確認訂閱的人可以直接 POST 這個端點
     * 繞過 AccessDecisionService 的 NOT_SUBSCRIBED 判定——頁面上看不到
     * 解鎖按鈕不等於端點不能被呼叫。</p>
     */
    @Test
    void loggedInButUnsubscribedIsRejected() throws Exception {
        Reader loggedInReader = newReader(300);
        when(readerContext.resolve(anyString()))
            .thenReturn(Optional.of(new ReaderContext.Current(loggedInReader, false)));
        Campaign campaign = article();
        when(campaignRepository.findBySlug("my-post")).thenReturn(Optional.of(campaign));
        givenDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_SUBSCRIBED, 0);

        mvc.perform(postUnlock("my-post"))
           .andExpect(status().isForbidden());

        verify(unlockService, never()).unlock(anyLong(), any(), any());

        // 同上：斷言傳給 decide() 的正是 ReaderContext 給的那個讀者物件、
        // subscribed 為 false，而不是只驗 HTTP 狀態碼。
        ArgumentCaptor<Reader> readerCaptor = ArgumentCaptor.forClass(Reader.class);
        ArgumentCaptor<Boolean> subscribedCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Campaign> campaignCaptor = ArgumentCaptor.forClass(Campaign.class);
        verify(accessDecisionService, times(1))
            .decide(readerCaptor.capture(), subscribedCaptor.capture(), campaignCaptor.capture(), any());
        assertSame(loggedInReader, readerCaptor.getValue(),
            "傳給 decide() 的 reader 必須是 ReaderContext.Current 提供的那個物件");
        assertFalse(subscribedCaptor.getValue(), "已登入但未確認訂閱時 subscribed 必須是 false");
        assertSame(campaign, campaignCaptor.getValue(), "傳給 decide() 的必須是 findBySlug 查回來的那一篇");
    }

    /** 找不到文章回 404，且絕不呼叫解鎖 */
    @Test
    void unknownSlugReturnsNotFound() throws Exception {
        givenLoggedInSubscriber();
        when(campaignRepository.findBySlug("ghost")).thenReturn(Optional.empty());

        mvc.perform(postUnlock("ghost"))
           .andExpect(status().isNotFound());

        // 與其他三個拒絕案例一致地驗「沒有走下去」：少了這一行，這個案例只是
        // 恰好因為 mock 回 null 而不會扣點，改動流程時不會有測試變紅。
        verify(unlockService, never()).unlock(anyLong(), any(), any());
    }

    /** 未發布的文章回 404（與 /r/news/{slug} 的行為一致，不洩漏草稿存在） */
    @Test
    void unpublishedArticleReturnsNotFound() throws Exception {
        givenLoggedInSubscriber();
        Campaign draft = article();
        draft.setPublishedAt(null);
        when(campaignRepository.findBySlug("my-post")).thenReturn(Optional.of(draft));
        givenDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_PUBLISHED, 0);

        mvc.perform(postUnlock("my-post"))
           .andExpect(status().isNotFound());

        verify(unlockService, never()).unlock(anyLong(), any(), any());
    }

    /**
     * <b>VIP 絕不可被扣點。</b>
     *
     * <p>VIP 對 PREMIUM 的 decide() 是 FULL/VIP——本來就免費。但
     * {@code article_access} 只在 VIP 瀏覽過該篇時才由 recordAccess 補寫，
     * 所以一位還沒瀏覽過該篇的 VIP 直接 POST 這個端點時，UnlockService 的
     * 三道檢查（已發布、PREMIUM、餘額足夠）會全部通過而真的扣點並寫帳本，
     * 且系統沒有退點路徑。分派必須在 controller 依 reason 擋掉。</p>
     */
    @Test
    void vipIsNeverChargedForPremiumArticle() throws Exception {
        givenLoggedInSubscriber();
        when(campaignRepository.findBySlug("my-post")).thenReturn(Optional.of(article()));
        givenDecision(AccessDecisionService.Access.FULL, AccessDecisionService.Reason.VIP, 0);

        mvc.perform(postUnlock("my-post"))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.outcome").value("NOT_REQUIRED"))
           // VIP 分支保留 cost：「這篇原本要 N 點、你因 VIP 免費」是有意義的資訊。
           .andExpect(jsonPath("$.cost").value(COST))
           // 回應的餘額必須是資料庫的權威值，不是寫死的 0，也不是 session 快照
           .andExpect(jsonPath("$.credits").value(DB_CREDITS));

        verify(unlockService, never()).unlock(anyLong(), any(), any());
    }

    /**
     * BASIC 文章不需要解鎖，回 409 而不是 500，且回應不帶無意義的 cost。
     *
     * <p>沒有這條分派，請求會往下走進 UnlockService 撞上 fail-closed 的
     * {@code UnlockUnavailableException}（未扣點，這部分正確），但對呼叫端
     * 呈現為 500 + ERROR log，而正確語意只是「這篇不需要解鎖」。</p>
     *
     * <p>BASIC 文章 {@code credit_cost = 0}，{@link AccessDecisionService#resolveCost}
     * 對 0 會退回 PREMIUM 的下限保護值（見 {@code CreditPolicy}）——若這裡仍然
     * 輸出 cost，任何呼叫端拿 cost 顯示都會對一篇免費文章標示出一個無意義的
     * 點數，因此斷言 cost 欄位完全不存在。</p>
     */
    @Test
    void basicArticleNeedsNoUnlock() throws Exception {
        givenLoggedInSubscriber();
        Campaign basic = article();
        basic.setTier(Campaign.TIER_BASIC);
        when(campaignRepository.findBySlug("my-post")).thenReturn(Optional.of(basic));
        givenDecision(AccessDecisionService.Access.FULL, AccessDecisionService.Reason.BASIC_OPEN, 0);

        mvc.perform(postUnlock("my-post"))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.outcome").value("NOT_REQUIRED"))
           .andExpect(jsonPath("$.cost").doesNotExist())
           .andExpect(jsonPath("$.credits").value(DB_CREDITS));

        verify(unlockService, never()).unlock(anyLong(), any(), any());
    }

    /** 餘額不足回 200 與 INSUFFICIENT_CREDITS（不是錯誤，是正常的業務結果） */
    @Test
    void insufficientCreditsIsReportedNotThrown() throws Exception {
        givenLoggedInSubscriber();
        when(campaignRepository.findBySlug("my-post")).thenReturn(Optional.of(article()));
        givenDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NEEDS_CREDITS, 7);
        when(unlockService.unlock(anyLong(), any(), any()))
            .thenReturn(new UnlockService.Result(UnlockService.Outcome.INSUFFICIENT_CREDITS, COST, 3));

        mvc.perform(postUnlock("my-post"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.outcome").value("INSUFFICIENT_CREDITS"))
           .andExpect(jsonPath("$.credits").value(3));
    }

    /**
     * service 正常回傳 ALREADY_UNLOCKED（非併發例外）時照原樣回報。
     *
     * <p>這條路徑走的是 decide() 的 ALREADY_UNLOCKED——讀者在別的分頁已解鎖、
     * 或頁面是舊的快取。必須回 200 而不是被當成錯誤，數字全部取自 service。</p>
     */
    @Test
    void alreadyUnlockedFromServiceIsReportedAsIs() throws Exception {
        givenLoggedInSubscriber();
        when(campaignRepository.findBySlug("my-post")).thenReturn(Optional.of(article()));
        givenDecision(AccessDecisionService.Access.FULL, AccessDecisionService.Reason.ALREADY_UNLOCKED, 0);
        when(unlockService.unlock(anyLong(), any(), any()))
            .thenReturn(new UnlockService.Result(UnlockService.Outcome.ALREADY_UNLOCKED, COST, 290));

        mvc.perform(postUnlock("my-post"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.outcome").value("ALREADY_UNLOCKED"))
           .andExpect(jsonPath("$.cost").value(COST))
           .andExpect(jsonPath("$.credits").value(290));
    }

    /**
     * 併發撞上 UNIQUE 時要轉譯成 ALREADY_UNLOCKED，不可讓 500 外洩。
     *
     * <p>這個轉譯必須在 controller（交易邊界之外）做——UnlockService 內
     * 捕捉會因 rollback-only 標記而在 commit 時改拋
     * UnexpectedRollbackException。讀者的實際情境是「開了兩個分頁各按一次
     * 解鎖」，正確結果是「已解鎖」而不是伺服器錯誤。</p>
     *
     * <p>{@code cost} 與 {@code credits} 不可回 0 這種假值：任何拿此回應
     * 更新餘額顯示的程式都會顯示「0 點」，違反 spec §5.11「頁面顯示的點數
     * 必須與實際同源」。cost 取自 resolveCost，credits 重讀資料庫。</p>
     */
    @Test
    void concurrentUniqueViolationIsTranslatedToAlreadyUnlocked() throws Exception {
        givenLoggedInSubscriber();
        when(campaignRepository.findBySlug("my-post")).thenReturn(Optional.of(article()));
        givenDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.CAN_UNLOCK, 0);
        when(unlockService.unlock(anyLong(), any(), any()))
            .thenThrow(new DataIntegrityViolationException("uq_article_access"));

        mvc.perform(postUnlock("my-post"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.outcome").value("ALREADY_UNLOCKED"))
           .andExpect(jsonPath("$.cost").value(COST))
           .andExpect(jsonPath("$.credits").value(DB_CREDITS));
    }

    /**
     * service 的 fail-closed 出口（併發扣款失敗等）回 409，不是 500。
     *
     * <p>{@code UnlockUnavailableException} 的訊息含讀者 id 與 tier，讓它
     * 變成 500 會把內部狀態寫進 ERROR log 與錯誤頁；而這其實不是伺服器故障，
     * 未扣點。刻意用 {@code UnlockService.UnlockUnavailableException}（而不是
     * 泛用的 {@code IllegalStateException}）拋出，證明 controller 只認這個
     * 專用子型別。</p>
     */
    @Test
    void failClosedIllegalStateBecomesConflictNotServerError() throws Exception {
        givenLoggedInSubscriber();
        when(campaignRepository.findBySlug("my-post")).thenReturn(Optional.of(article()));
        givenDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.CAN_UNLOCK, 0);
        when(unlockService.unlock(anyLong(), any(), any()))
            .thenThrow(new UnlockService.UnlockUnavailableException("扣點失敗（併發衝突）：reader=3 cost=10"));

        mvc.perform(postUnlock("my-post"))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.outcome").value("UNLOCK_UNAVAILABLE"))
           .andExpect(jsonPath("$.credits").value(DB_CREDITS));
    }

    /**
     * <b>Important B 的破壞性驗證</b>：非 {@code UnlockUnavailableException} 的泛用
     * {@code IllegalStateException}（模擬 JPA／交易基礎設施的真實故障，或日後在
     * service 內誤用某個 API 而拋出的例外）必須讓 500 照常外洩，不可被本端點
     * 吞成 409。
     *
     * <p>捕捉範圍收窄到 {@code UnlockUnavailableException} 之後，這類例外不再
     * 被 controller 捕捉，會直接從 {@code mvc.perform(...)} 拋出（standalone
     * MockMvc 沒有為泛用 RuntimeException 註冊 handler，等同於在真實容器中
     * 變成 500）。若日後有人把 catch 改回 {@code catch (IllegalStateException)}，
     * 這裡會因為呼叫端不再拋出例外（改成正常回傳 409）而變紅。</p>
     */
    @Test
    void infraIllegalStateExceptionIsNotSwallowedAsConflict() throws Exception {
        givenLoggedInSubscriber();
        when(campaignRepository.findBySlug("my-post")).thenReturn(Optional.of(article()));
        givenDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.CAN_UNLOCK, 0);
        when(unlockService.unlock(anyLong(), any(), any()))
            .thenThrow(new IllegalStateException("infra"));

        Exception thrown = org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
            () -> mvc.perform(postUnlock("my-post")));
        Throwable root = thrown;
        while (root.getCause() != null && !(root instanceof IllegalStateException)) {
            root = root.getCause();
        }
        org.junit.jupiter.api.Assertions.assertTrue(root instanceof IllegalStateException,
            "應是未被捕捉、往外拋的 IllegalStateException");
        org.junit.jupiter.api.Assertions.assertFalse(root instanceof UnlockService.UnlockUnavailableException,
            "本測試模擬的是非 UnlockUnavailableException 的泛用 ISE，不該被 controller 捕捉成 409");
    }

    /**
     * 解鎖必須是 POST，不可用 GET。
     *
     * <p>GET 會被瀏覽器預抓、被 email 客戶端的連結掃描器觸發、被搜尋引擎爬——
     * 任何一個都會在讀者不知情的狀況下扣掉點數。這與 magic link 遇到的
     * Outlook Safe Links 問題同源。</p>
     */
    @Test
    void unlockIsNotReachableByGet() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/reader/unlock/my-post"))
           .andExpect(status().isMethodNotAllowed());
    }
}
