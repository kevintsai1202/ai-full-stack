package world.springai.survey.reader;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import world.springai.survey.AppSettingService;
import world.springai.survey.newsletter.Campaign;
import world.springai.survey.newsletter.CampaignRepository;
import world.springai.survey.newsletter.MarkdownRenderer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ReaderPageController 行為測試。
 *
 * <p>最重要的一組是「受限區不洩漏」：以哨兵字串斷言未授權者的回應**完全不含**
 * 受限內容。只檢查有無某個 CSS class 或提示文字是不夠的——那不能證明內容
 * 沒被送到瀏覽器。</p>
 */
@WebMvcTest(ReaderPageController.class)
@Import({HtmlTemplate.class, ContentSplitter.class, MarkdownRenderer.class})
@TestPropertySource(properties = {
    "app.cors-allowed-origins=http://localhost",
    "app.public-base-url=https://news.example.com"
})
class ReaderPageControllerTest {

    /** 受限區的哨兵字串：只要它出現在回應中就是洩漏 */
    private static final String SENTINEL = "SENTINEL_GATED_9f3a";

    /** 免費區的標記字串：應該永遠看得到 */
    private static final String FREE_MARKER = "FREE_INTRO_TEXT";

    @Autowired MockMvc mvc;

    @MockBean CampaignRepository campaignRepository;
    @MockBean AccessDecisionService accessDecisionService;
    @MockBean ArticleAccessRepository articleAccessRepository;
    @MockBean ReaderContext readerContext;
    @MockBean AppSettingService appSettingService;

    /** 建立一篇含 paywall 標記的文章 */
    private Campaign gatedArticle(String tier, int cost) {
        String markdown = FREE_MARKER + "\n\n<!--paywall-->\n\n" + SENTINEL;
        Campaign c = new Campaign("測試文章", markdown, null, null, null, "now", null, 1, "sent");
        c.setTier(tier);
        c.setCreditCost(cost);
        c.setSlug("test-article");
        c.setPublishedAt(OffsetDateTime.parse("2026-07-20T10:00:00+08:00"));
        return c;
    }

    /** 建立一位讀者 */
    private Reader reader(String tier, int credits) {
        Reader r = new Reader("user@example.com", "CODE1234");
        r.setId(1L);
        r.setTier(tier);
        r.setCredits(credits);
        return r;
    }

    /** 讓 decide 回傳指定決策 */
    private void stubDecision(AccessDecisionService.Access access, AccessDecisionService.Reason reason, int shortfall) {
        when(accessDecisionService.decide(any(), org.mockito.ArgumentMatchers.anyBoolean(), any(), any()))
            .thenReturn(new AccessDecisionService.Decision(access, reason, shortfall));
    }

    /** 未登入：回應不得含受限區，但要看得到免費區 */
    @Test
    void anonymousResponseNeverContainsGatedContent() throws Exception {
        when(campaignRepository.findBySlug("test-article")).thenReturn(Optional.of(gatedArticle(Campaign.TIER_BASIC, 0)));
        when(readerContext.resolve(any())).thenReturn(Optional.empty());
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_LOGGED_IN, 0);

        String body = mvc.perform(get("/r/news/test-article"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains(SENTINEL), "未登入者的回應不得含受限區內容");
        assertTrue(body.contains(FREE_MARKER), "免費區應正常顯示");
    }

    /** 已登入但未確認訂閱：同樣不得含受限區 */
    @Test
    void unsubscribedResponseNeverContainsGatedContent() throws Exception {
        when(campaignRepository.findBySlug("test-article")).thenReturn(Optional.of(gatedArticle(Campaign.TIER_BASIC, 0)));
        when(readerContext.resolve(any()))
            .thenReturn(Optional.of(new ReaderContext.Current(reader(Reader.TIER_FREE, 300), false)));
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_SUBSCRIBED, 0);

        String body = mvc.perform(get("/r/news/test-article"))
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains(SENTINEL), "未確認訂閱者的回應不得含受限區內容");
    }

    /** 已訂閱但 PREMIUM 點數不足：同樣不得含受限區 */
    @Test
    void insufficientCreditsResponseNeverContainsGatedContent() throws Exception {
        when(campaignRepository.findBySlug("test-article"))
            .thenReturn(Optional.of(gatedArticle(Campaign.TIER_PREMIUM, 10)));
        when(readerContext.resolve(any()))
            .thenReturn(Optional.of(new ReaderContext.Current(reader(Reader.TIER_FREE, 4), true)));
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NEEDS_CREDITS, 6);

        String body = mvc.perform(get("/r/news/test-article"))
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains(SENTINEL), "點數不足者的回應不得含受限區內容");
    }

    /** 授權為 FULL：受限區才會出現 */
    @Test
    void fullAccessIncludesGatedContent() throws Exception {
        when(campaignRepository.findBySlug("test-article")).thenReturn(Optional.of(gatedArticle(Campaign.TIER_BASIC, 0)));
        when(readerContext.resolve(any()))
            .thenReturn(Optional.of(new ReaderContext.Current(reader(Reader.TIER_FREE, 300), true)));
        stubDecision(AccessDecisionService.Access.FULL, AccessDecisionService.Reason.BASIC_OPEN, 0);

        String body = mvc.perform(get("/r/news/test-article"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains(SENTINEL), "授權為 FULL 時受限區應顯示");
        assertTrue(body.contains(FREE_MARKER));
    }

    /** paywall 標記本身不得出現在頁面上 */
    @Test
    void paywallMarkerNeverAppearsInOutput() throws Exception {
        when(campaignRepository.findBySlug("test-article")).thenReturn(Optional.of(gatedArticle(Campaign.TIER_BASIC, 0)));
        when(readerContext.resolve(any()))
            .thenReturn(Optional.of(new ReaderContext.Current(reader(Reader.TIER_FREE, 300), true)));
        stubDecision(AccessDecisionService.Access.FULL, AccessDecisionService.Reason.BASIC_OPEN, 0);

        String body = mvc.perform(get("/r/news/test-article"))
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains(ContentSplitter.PAYWALL_MARKER), "paywall 標記不得洩漏到頁面");
    }

    /** 未發布的文章回 404（即使 slug 存在） */
    @Test
    void unpublishedArticleReturns404() throws Exception {
        Campaign unpublished = gatedArticle(Campaign.TIER_BASIC, 0);
        unpublished.setPublishedAt(null);
        when(campaignRepository.findBySlug("test-article")).thenReturn(Optional.of(unpublished));

        mvc.perform(get("/r/news/test-article")).andExpect(status().isNotFound());
    }

    /** 不存在的 slug 回 404 */
    @Test
    void unknownSlugReturns404() throws Exception {
        when(campaignRepository.findBySlug("nope")).thenReturn(Optional.empty());

        mvc.perform(get("/r/news/nope")).andExpect(status().isNotFound());
    }

    /** 文章標題必須經 HTML 跳脫，避免標題含標籤時破版或注入 */
    @Test
    void articleTitleIsHtmlEscaped() throws Exception {
        Campaign c = gatedArticle(Campaign.TIER_BASIC, 0);
        c.setSubject("<img src=x onerror=alert(1)>");
        when(campaignRepository.findBySlug("test-article")).thenReturn(Optional.of(c));
        when(readerContext.resolve(any())).thenReturn(Optional.empty());
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_LOGGED_IN, 0);

        String body = mvc.perform(get("/r/news/test-article"))
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("<img src=x onerror"), "標題必須跳脫");
        assertTrue(body.contains("&lt;img src=x onerror"), "應為跳脫後的形式");
    }

    /** archive 只列已發布文章 */
    @Test
    void archiveListsPublishedArticles() throws Exception {
        when(campaignRepository.findByPublishedAtIsNotNullOrderByPublishedAtDesc())
            .thenReturn(List.of(gatedArticle(Campaign.TIER_BASIC, 0)));
        when(readerContext.resolve(any())).thenReturn(Optional.empty());

        String body = mvc.perform(get("/r/archive"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("測試文章"));
        assertTrue(body.contains("/r/news/test-article"));
    }

    /** archive 的列表也不得洩漏任何文章內容（連摘要都取自免費區） */
    @Test
    void archiveNeverLeaksGatedContent() throws Exception {
        when(campaignRepository.findByPublishedAtIsNotNullOrderByPublishedAtDesc())
            .thenReturn(List.of(gatedArticle(Campaign.TIER_PREMIUM, 10)));
        when(readerContext.resolve(any())).thenReturn(Optional.empty());

        String body = mvc.perform(get("/r/archive"))
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains(SENTINEL), "archive 列表不得含任何受限區內容");
    }

    /** 沒有已發布文章時顯示空狀態，不是錯誤頁 */
    @Test
    void emptyArchiveShowsEmptyState() throws Exception {
        when(campaignRepository.findByPublishedAtIsNotNullOrderByPublishedAtDesc()).thenReturn(List.of());
        when(readerContext.resolve(any())).thenReturn(Optional.empty());

        mvc.perform(get("/r/archive")).andExpect(status().isOk());
    }
}
