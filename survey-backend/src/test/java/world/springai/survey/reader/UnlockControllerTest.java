package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import world.springai.survey.ApiExceptionHandler;
import world.springai.survey.newsletter.Campaign;
import world.springai.survey.newsletter.CampaignRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 解鎖端點：登入檢查、授權檢查、結果回報與併發處理 */
class UnlockControllerTest {

    private static final long READER_ID = 3L;
    private static final long CAMPAIGN_ID = 42L;

    private CampaignRepository campaignRepository;
    private ReaderContext readerContext;
    private UnlockService unlockService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        campaignRepository = mock(CampaignRepository.class);
        readerContext = mock(ReaderContext.class);
        unlockService = mock(UnlockService.class);
        mvc = MockMvcBuilders
            .standaloneSetup(new UnlockController(campaignRepository, readerContext, unlockService))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    /** 建一篇已發布的 PREMIUM 文章 */
    private Campaign article() {
        Campaign c = new Campaign("主旨", "# 內容", "<h1>內容</h1>", null, null, "now", null, 0, "sent");
        c.setTier(Campaign.TIER_PREMIUM);
        c.setCreditCost(10);
        c.setSlug("my-post");
        c.setPublishedAt(OffsetDateTime.now().minusDays(1));
        ReflectionTestUtils.setField(c, "id", CAMPAIGN_ID);
        return c;
    }

    /** 讓 readerContext 回一個已確認訂閱的登入讀者 */
    private void givenLoggedInSubscriber() {
        Reader reader = new Reader("r@b.com", "CODE1234");
        ReflectionTestUtils.setField(reader, "id", READER_ID);
        when(readerContext.resolve(anyString()))
            .thenReturn(Optional.of(new ReaderContext.Current(reader, true)));
    }

    /** 成功解鎖回 200 與結果明細 */
    @Test
    void successfulUnlockReturnsOutcomeAndBalance() throws Exception {
        givenLoggedInSubscriber();
        when(campaignRepository.findBySlug("my-post")).thenReturn(Optional.of(article()));
        when(unlockService.unlock(anyLong(), any(), any()))
            .thenReturn(new UnlockService.Result(UnlockService.Outcome.UNLOCKED, 10, 290));

        mvc.perform(post("/api/reader/unlock/my-post").cookie(
                new jakarta.servlet.http.Cookie(ReaderSessionService.COOKIE_NAME, "JWT")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.outcome").value("UNLOCKED"))
           .andExpect(jsonPath("$.cost").value(10))
           .andExpect(jsonPath("$.credits").value(290));
    }

    /** 未登入回 401，且絕不呼叫解鎖 */
    @Test
    void anonymousRequestIsRejected() throws Exception {
        when(readerContext.resolve(any())).thenReturn(Optional.empty());

        mvc.perform(post("/api/reader/unlock/my-post"))
           .andExpect(status().isUnauthorized());

        verify(unlockService, never()).unlock(anyLong(), any(), any());
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
        Reader reader = new Reader("r@b.com", "CODE1234");
        ReflectionTestUtils.setField(reader, "id", READER_ID);
        when(readerContext.resolve(anyString()))
            .thenReturn(Optional.of(new ReaderContext.Current(reader, false)));
        when(campaignRepository.findBySlug("my-post")).thenReturn(Optional.of(article()));

        mvc.perform(post("/api/reader/unlock/my-post").cookie(
                new jakarta.servlet.http.Cookie(ReaderSessionService.COOKIE_NAME, "JWT")))
           .andExpect(status().isForbidden());

        verify(unlockService, never()).unlock(anyLong(), any(), any());
    }

    /** 找不到文章回 404 */
    @Test
    void unknownSlugReturnsNotFound() throws Exception {
        givenLoggedInSubscriber();
        when(campaignRepository.findBySlug("ghost")).thenReturn(Optional.empty());

        mvc.perform(post("/api/reader/unlock/ghost").cookie(
                new jakarta.servlet.http.Cookie(ReaderSessionService.COOKIE_NAME, "JWT")))
           .andExpect(status().isNotFound());
    }

    /** 未發布的文章回 404（與 /r/news/{slug} 的行為一致，不洩漏草稿存在） */
    @Test
    void unpublishedArticleReturnsNotFound() throws Exception {
        givenLoggedInSubscriber();
        Campaign draft = article();
        draft.setPublishedAt(null);
        when(campaignRepository.findBySlug("my-post")).thenReturn(Optional.of(draft));

        mvc.perform(post("/api/reader/unlock/my-post").cookie(
                new jakarta.servlet.http.Cookie(ReaderSessionService.COOKIE_NAME, "JWT")))
           .andExpect(status().isNotFound());

        verify(unlockService, never()).unlock(anyLong(), any(), any());
    }

    /** 餘額不足回 200 與 INSUFFICIENT_CREDITS（不是錯誤，是正常的業務結果） */
    @Test
    void insufficientCreditsIsReportedNotThrown() throws Exception {
        givenLoggedInSubscriber();
        when(campaignRepository.findBySlug("my-post")).thenReturn(Optional.of(article()));
        when(unlockService.unlock(anyLong(), any(), any()))
            .thenReturn(new UnlockService.Result(UnlockService.Outcome.INSUFFICIENT_CREDITS, 10, 3));

        mvc.perform(post("/api/reader/unlock/my-post").cookie(
                new jakarta.servlet.http.Cookie(ReaderSessionService.COOKIE_NAME, "JWT")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.outcome").value("INSUFFICIENT_CREDITS"))
           .andExpect(jsonPath("$.credits").value(3));
    }

    /**
     * 併發撞上 UNIQUE 時要轉譯成 ALREADY_UNLOCKED，不可讓 500 外洩。
     *
     * <p>這個轉譯必須在 controller（交易邊界之外）做——UnlockService 內
     * 捕捉會因 rollback-only 標記而在 commit 時改拋
     * UnexpectedRollbackException。讀者的實際情境是「開了兩個分頁各按一次
     * 解鎖」，正確結果是「已解鎖」而不是伺服器錯誤。</p>
     */
    @Test
    void concurrentUniqueViolationIsTranslatedToAlreadyUnlocked() throws Exception {
        givenLoggedInSubscriber();
        when(campaignRepository.findBySlug("my-post")).thenReturn(Optional.of(article()));
        when(unlockService.unlock(anyLong(), any(), any()))
            .thenThrow(new DataIntegrityViolationException("uq_article_access"));

        mvc.perform(post("/api/reader/unlock/my-post").cookie(
                new jakarta.servlet.http.Cookie(ReaderSessionService.COOKIE_NAME, "JWT")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.outcome").value("ALREADY_UNLOCKED"));
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
