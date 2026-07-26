package world.springai.survey.reader;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import world.springai.survey.AppSettingService;
import world.springai.survey.newsletter.Campaign;
import world.springai.survey.newsletter.CampaignRepository;
import world.springai.survey.newsletter.ContentSplitter;
import world.springai.survey.newsletter.MarkdownRenderer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

        var response = mvc.perform(get("/r/news/test-article"))
            .andExpect(status().isOk())
            // 【Important 1】單篇頁必須帶上這兩個標頭，否則共享快取（CDN／app-gateway）
            // 可能把某位讀者的授權結果快取下來餵給別人。
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
            .andReturn().getResponse();
        // CORS 設定也會為 Vary 加上 Origin 等值，因此逐一檢查是否含 Cookie，
        // 而不是假設 Cookie 是唯一或第一個值。
        assertTrue(response.getHeaders(HttpHeaders.VARY).stream().anyMatch(v -> v.contains(HttpHeaders.COOKIE)),
            "Vary 標頭必須包含 Cookie，否則共享快取無法區分不同讀者的回應");
        String body = response.getContentAsString();

        assertFalse(body.contains(SENTINEL), "未登入者的回應不得含受限區內容");
        assertTrue(body.contains(FREE_MARKER), "免費區應正常顯示");

        // 【Important 5】NOT_LOGGED_IN：頁面須含登入連結，且帶正確的 redirect 目標
        assertTrue(body.contains("/r/login?redirect=/r/news/test-article"), "應含帶正確 redirect 的登入連結");

        // 【Important 2/3】decide() 只應被呼叫一次，且引數必須是「未登入」的正確組合：
        // reader 為 null、subscribed 為 false。若 controller 把 subscribed 寫死成 true，
        // 這裡會抓到。
        ArgumentCaptor<Boolean> subscribedCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Reader> readerCaptor = ArgumentCaptor.forClass(Reader.class);
        verify(accessDecisionService, times(1))
            .decide(readerCaptor.capture(), subscribedCaptor.capture(), any(), any());
        assertNull(readerCaptor.getValue(), "未登入時傳給 decide() 的 reader 必須是 null");
        assertFalse(subscribedCaptor.getValue(), "未登入時傳給 decide() 的 subscribed 必須是 false");
    }

    /** 已登入但未確認訂閱：同樣不得含受限區 */
    @Test
    void unsubscribedResponseNeverContainsGatedContent() throws Exception {
        when(campaignRepository.findBySlug("test-article")).thenReturn(Optional.of(gatedArticle(Campaign.TIER_BASIC, 0)));
        Reader loggedInReader = reader(Reader.TIER_FREE, 300);
        when(readerContext.resolve(any()))
            .thenReturn(Optional.of(new ReaderContext.Current(loggedInReader, false)));
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_SUBSCRIBED, 0);

        String body = mvc.perform(get("/r/news/test-article"))
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains(SENTINEL), "未確認訂閱者的回應不得含受限區內容");

        // 【Important 5】NOT_SUBSCRIBED：頁面須含「重新訂閱」的指引
        assertTrue(body.contains("重新訂閱"), "應含重新訂閱的指引文案");

        // 【Important 2】已登入未訂閱：decide() 只呼叫一次，reader 必須是 ReaderContext 提供的
        // 那個物件、subscribed 必須是 false。
        ArgumentCaptor<Boolean> subscribedCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Reader> readerCaptor = ArgumentCaptor.forClass(Reader.class);
        verify(accessDecisionService, times(1))
            .decide(readerCaptor.capture(), subscribedCaptor.capture(), any(), any());
        assertEquals(loggedInReader, readerCaptor.getValue(), "傳給 decide() 的 reader 必須是 ReaderContext.Current 提供的那個物件");
        assertFalse(subscribedCaptor.getValue(), "已登入但未確認訂閱時 subscribed 必須是 false");

        // 【Important 3】PARTIAL 時絕不能呼叫 recordAccess，否則階段 C 接上扣點後會重複扣款
        verify(accessDecisionService, never()).recordAccess(any(), any(), any());
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

        // 【Important 5】NEEDS_CREDITS：頁面須含 shortfall 的文案
        assertTrue(body.contains("還差 6 點"), "應含差幾點才能解鎖的文案");

        // 【Important 3】PARTIAL 時絕不能呼叫 recordAccess
        verify(accessDecisionService, never()).recordAccess(any(), any(), any());
    }

    /** 授權為 FULL：受限區才會出現 */
    @Test
    void fullAccessIncludesGatedContent() throws Exception {
        when(campaignRepository.findBySlug("test-article")).thenReturn(Optional.of(gatedArticle(Campaign.TIER_BASIC, 0)));
        Reader loggedInReader = reader(Reader.TIER_FREE, 300);
        when(readerContext.resolve(any()))
            .thenReturn(Optional.of(new ReaderContext.Current(loggedInReader, true)));
        stubDecision(AccessDecisionService.Access.FULL, AccessDecisionService.Reason.BASIC_OPEN, 0);

        var response = mvc.perform(get("/r/news/test-article"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
            .andReturn().getResponse();
        // CORS 設定也會為 Vary 加上 Origin 等值，因此逐一檢查是否含 Cookie，
        // 而不是假設 Cookie 是唯一或第一個值。
        assertTrue(response.getHeaders(HttpHeaders.VARY).stream().anyMatch(v -> v.contains(HttpHeaders.COOKIE)),
            "Vary 標頭必須包含 Cookie，否則共享快取無法區分不同讀者的回應");
        String body = response.getContentAsString();

        assertTrue(body.contains(SENTINEL), "授權為 FULL 時受限區應顯示");
        assertTrue(body.contains(FREE_MARKER));

        // 【Important 2】已登入已訂閱：decide() 只呼叫一次，subscribed 必須是 true
        ArgumentCaptor<Boolean> subscribedCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Reader> readerCaptor = ArgumentCaptor.forClass(Reader.class);
        verify(accessDecisionService, times(1))
            .decide(readerCaptor.capture(), subscribedCaptor.capture(), any(), any());
        assertEquals(loggedInReader, readerCaptor.getValue());
        assertTrue(subscribedCaptor.getValue(), "已訂閱時 subscribed 必須是 true");

        // 【Important 3】FULL 時 recordAccess 恰好呼叫一次
        verify(accessDecisionService, times(1)).recordAccess(any(), any(), any());
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
        when(campaignRepository.findBySlugIsNotNullAndPublishedAtIsNotNullOrderByPublishedAtDesc())
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
        when(campaignRepository.findBySlugIsNotNullAndPublishedAtIsNotNullOrderByPublishedAtDesc())
            .thenReturn(List.of(gatedArticle(Campaign.TIER_PREMIUM, 10)));
        when(readerContext.resolve(any())).thenReturn(Optional.empty());

        String body = mvc.perform(get("/r/archive"))
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains(SENTINEL), "archive 列表不得含任何受限區內容");
    }

    /** 沒有已發布文章時顯示空狀態，不是錯誤頁 */
    @Test
    void emptyArchiveShowsEmptyState() throws Exception {
        when(campaignRepository.findBySlugIsNotNullAndPublishedAtIsNotNullOrderByPublishedAtDesc()).thenReturn(List.of());
        when(readerContext.resolve(any())).thenReturn(Optional.empty());

        mvc.perform(get("/r/archive")).andExpect(status().isOk());
    }

    /**
     * CAN_UNLOCK 時要有解鎖按鈕與成本數字，且**仍不含受限區內容**。
     *
     * <p>最後那個斷言是 paywall 的驗收條件本身：只檢查「有解鎖按鈕」
     * 不能證明受限內容沒被送到瀏覽器。</p>
     */
    @Test
    void canUnlockRendersUnlockButtonWithoutGatedContent() throws Exception {
        when(campaignRepository.findBySlug("test-article"))
            .thenReturn(Optional.of(gatedArticle(Campaign.TIER_PREMIUM, 10)));
        when(readerContext.resolve(any()))
            .thenReturn(Optional.of(new ReaderContext.Current(reader(Reader.TIER_FREE, 300), true)));
        when(accessDecisionService.resolveCost(any())).thenReturn(10);
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.CAN_UNLOCK, 0);

        String html = mvc.perform(get("/r/news/test-article")
                .cookie(new jakarta.servlet.http.Cookie(ReaderSessionService.COOKIE_NAME, "JWT")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("用 10 點解鎖"), "應顯示成本與解鎖按鈕文字");
        assertTrue(html.contains("id=\"unlock-btn\""), "應有解鎖按鈕");
        assertTrue(html.contains("/r/rules"), "gate 區塊必須附規則頁連結（spec §5.11）");
        assertTrue(html.contains(FREE_MARKER), "免費區必須看得到");
        assertFalse(html.contains(SENTINEL), "受限區絕不可出現在 PARTIAL 回應中");
    }

    /** NEEDS_CREDITS 時顯示差額與邀請連結，不得有解鎖按鈕，也不得含受限區 */
    @Test
    void needsCreditsRendersShortfallAndInviteLink() throws Exception {
        when(campaignRepository.findBySlug("test-article"))
            .thenReturn(Optional.of(gatedArticle(Campaign.TIER_PREMIUM, 10)));
        when(readerContext.resolve(any()))
            .thenReturn(Optional.of(new ReaderContext.Current(reader(Reader.TIER_FREE, 3), true)));
        when(accessDecisionService.resolveCost(any())).thenReturn(10);
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NEEDS_CREDITS, 7);

        String html = mvc.perform(get("/r/news/test-article")
                .cookie(new jakarta.servlet.http.Cookie(ReaderSessionService.COOKIE_NAME, "JWT")))
            .andReturn().getResponse().getContentAsString();

        // 成本與差額都必須取自 resolveCost（唯一來源）。少了「解鎖需要 10 點」這一項，
        // 把 renderGate 的 cost 改成寫死的數字時 NEEDS_CREDITS 分支不會有任何測試變紅，
        // 頁面就能長期顯示與實際扣點不同的數字（spec §5.11 最在意的信任缺陷）。
        assertTrue(html.contains("解鎖需要 10 點"), "應顯示取自 resolveCost 的成本");
        assertTrue(html.contains("還差 7 點"), "應顯示差額");
        assertTrue(html.contains("/r/invite"), "應引導去邀請頁賺點數");
        assertTrue(html.contains("/r/rules"), "gate 區塊必須附規則頁連結");
        assertFalse(html.contains("id=\"unlock-btn\""), "餘額不足時不可出現解鎖按鈕");
        assertFalse(html.contains(SENTINEL), "受限區絕不可出現在 PARTIAL 回應中");
    }

    /**
     * 解鎖腳本只在 CAN_UNLOCK 時輸出。
     *
     * <p>不是效能考量——未登入者頁面帶著一段解鎖腳本，會讓「這篇要付費」
     * 的訊息在錯誤的時機出現，而該讀者要做的是登入。</p>
     */
    @Test
    void unlockScriptOnlyAppearsForCanUnlock() throws Exception {
        when(campaignRepository.findBySlug("test-article"))
            .thenReturn(Optional.of(gatedArticle(Campaign.TIER_PREMIUM, 10)));
        when(readerContext.resolve(any())).thenReturn(Optional.empty());
        when(accessDecisionService.resolveCost(any())).thenReturn(10);
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_LOGGED_IN, 0);

        String html = mvc.perform(get("/r/news/test-article"))
            .andReturn().getResponse().getContentAsString();

        assertFalse(html.contains("unlock-btn"), "未登入時不該有解鎖腳本或按鈕");
        assertTrue(html.contains("/r/login"), "應引導登入");
        assertFalse(html.contains(SENTINEL));
    }

    /** FULL 時受限區必須出現，且不該有 gate 區塊 */
    @Test
    void fullAccessRendersGatedContentAndNoGate() throws Exception {
        when(campaignRepository.findBySlug("test-article"))
            .thenReturn(Optional.of(gatedArticle(Campaign.TIER_PREMIUM, 10)));
        when(readerContext.resolve(any()))
            .thenReturn(Optional.of(new ReaderContext.Current(reader(Reader.TIER_FREE, 290), true)));
        stubDecision(AccessDecisionService.Access.FULL, AccessDecisionService.Reason.ALREADY_UNLOCKED, 0);

        String html = mvc.perform(get("/r/news/test-article")
                .cookie(new jakarta.servlet.http.Cookie(ReaderSessionService.COOKIE_NAME, "JWT")))
            .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains(SENTINEL), "已解鎖者必須看得到受限區");
        assertFalse(html.contains("id=\"unlock-btn\""), "已解鎖不該再顯示解鎖按鈕");
    }

    /**
     * 【Important 6】meta description 不得從受限區洩漏。
     *
     * <p>原本的三個哨兵測試之所以能抓到「summaryOf 改吃 campaign.getMarkdown()」這種洩漏，
     * 純粹是因為 fixture 的免費區只有約 50 字元，哨兵字串因此仍落在 120 字元截斷範圍內。
     * 這裡刻意把免費區撐到超過 120 字元，讓「meta 洩漏受限內容」這個獨立的失敗模式
     * 不再依賴 fixture 長度的偶然性。</p>
     */
    @Test
    void metaDescriptionNeverLeaksGatedContentEvenWhenFreeSectionIsLong() throws Exception {
        // 免費區故意超過 120 字元，確保就算 summaryOf 改吃 campaign.getMarkdown()，
        // 若哨兵洩漏進去，也不會只是「恰好被截斷保護住」。
        String longFreeMarkdown = "免費區內容。".repeat(30); // 中文全形字元，遠超過 120 字元
        String markdown = longFreeMarkdown + "\n\n<!--paywall-->\n\n" + SENTINEL;
        Campaign c = new Campaign("測試文章", markdown, null, null, null, "now", null, 1, "sent");
        c.setTier(Campaign.TIER_BASIC);
        c.setCreditCost(0);
        c.setSlug("test-article");
        c.setPublishedAt(OffsetDateTime.parse("2026-07-20T10:00:00+08:00"));

        when(campaignRepository.findBySlug("test-article")).thenReturn(Optional.of(c));
        when(readerContext.resolve(any())).thenReturn(Optional.empty());
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_LOGGED_IN, 0);

        String body = mvc.perform(get("/r/news/test-article"))
            .andReturn().getResponse().getContentAsString();

        int metaStart = body.indexOf("<meta name=\"description\"");
        assertTrue(metaStart >= 0, "應輸出 meta description 標籤");
        int metaEnd = body.indexOf(">", metaStart);
        String metaTag = body.substring(metaStart, metaEnd + 1);
        assertFalse(metaTag.contains(SENTINEL), "meta description 不得含受限區的哨兵字串");
    }
}
