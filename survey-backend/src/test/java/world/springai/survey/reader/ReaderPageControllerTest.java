package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import world.springai.survey.AppSettingService;
import world.springai.survey.newsletter.Campaign;
import world.springai.survey.newsletter.CampaignRepository;
import world.springai.survey.newsletter.ContentSplitter;
import world.springai.survey.newsletter.MarkdownRenderer;
import world.springai.survey.newsletter.SurveyBlockRenderer;
import world.springai.survey.media.MediaAssetService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    /** 用於斷言 ApiExceptionHandler 真的在這個切片裡（否則 404 頁的測試會恆綠） */
    @Autowired org.springframework.context.ApplicationContext applicationContext;

    @MockBean CampaignRepository campaignRepository;
    @MockBean AccessDecisionService accessDecisionService;
    @MockBean ArticleAccessRepository articleAccessRepository;
    @MockBean ReaderContext readerContext;
    @MockBean AppSettingService appSettingService;
    @MockBean MediaAssetService mediaAssetService;
    /** 側欄相關文章服務（mock）；未 stub 時 Mockito 對 List 回傳空清單，等於「沒有相關文章」 */
    @MockBean world.springai.survey.newsletter.PublicRelatedArticleService relatedArticleService;
    /** 側欄分類服務（mock）：controller 以 ObjectProvider 取得，此處提供 bean 讓分類卡有資料 */
    @MockBean world.springai.survey.newsletter.PublicCampaignTagService campaignTagService;
    /** Task 9 接線：問卷標記展開器（mock），預設 stub 為直通避免影響既有斷言 */
    @MockBean SurveyBlockRenderer surveyBlockRenderer;
    /** Task 7 接線：投票統計服務（mock），供側邊欄投票卡取用 */
    @MockBean world.springai.survey.form.SurveyVoteStatsService surveyVoteStatsService;
    /** Task 7 接線：問卷 schema 服務（mock），供側邊欄投票卡取用信中一鍵題標題與選項 */
    @MockBean world.springai.survey.form.FormSchemaService formSchemaService;

    /**
     * 預設直通：多數既有測試不關心問卷卡展開。若不 stub，Mockito 對未 stub 的
     * 方法回傳 null，contentHtml 會整段變成 null，讓本檔既有的所有內容斷言全數改觀
     * ——這正是 @MockBean 新依賴最容易踩到的坑（同一坑先前在 CampaignServiceTest
     * 對 promoTokenService.issue() 出現過一次）。
     */
    @BeforeEach
    void stubSurveyBlockRendererPassthrough() {
        when(surveyBlockRenderer.expandForWeb(any(), any(), org.mockito.ArgumentMatchers.anyBoolean()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        // 預設無內嵌問卷標記：多數既有測試不關心側邊欄投票卡
        when(surveyBlockRenderer.embeddedFormKeys(any())).thenReturn(List.of());
    }

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
        assertTrue(body.contains("登入後仍需使用點數解鎖"), "不得讓讀者誤以為登入即可免費閱讀付費內容");

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

    /** 設定 MinIO 封面時，單篇頁輸出圖片且 alt 使用已跳脫文章主旨。 */
    @Test
    void articleRendersConfiguredMediaCover() throws Exception {
        Campaign campaign = gatedArticle(Campaign.TIER_BASIC, 0);
        campaign.setCoverMediaId(77L);
        when(campaignRepository.findBySlug("test-article")).thenReturn(Optional.of(campaign));
        when(readerContext.resolve(any())).thenReturn(Optional.empty());
        when(mediaAssetService.publicUrl(77L))
            .thenReturn(Optional.of("https://media.example.com/newsletter-media/images/cover.png"));
        stubDecision(AccessDecisionService.Access.FULL, AccessDecisionService.Reason.BASIC_OPEN, 0);

        String body = mvc.perform(get("/r/news/test-article"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("class=\"article-hero-cover\""));
        assertTrue(body.contains("https://media.example.com/newsletter-media/images/cover.png"));
        assertTrue(body.contains("alt=\"測試文章\""));
    }

    /**
     * Task 9 接線：contentHtml 定案後必須呼叫 {@code expandForWeb} 並帶入正確的
     * campaignId，且展開結果要真的進入回應本文——只驗證呼叫過但不驗證有沒有
     * 進到輸出，等於沒驗證接線是否真的生效。
     */
    @Test
    void articlePageExpandsSurveyBlockWithCampaignId() throws Exception {
        Campaign campaign = gatedArticle(Campaign.TIER_BASIC, 0);
        ReflectionTestUtils.setField(campaign, "id", 77L);
        when(campaignRepository.findBySlug("test-article")).thenReturn(Optional.of(campaign));
        when(readerContext.resolve(any())).thenReturn(Optional.empty());
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_LOGGED_IN, 0);
        when(surveyBlockRenderer.expandForWeb(any(), eq(77L), org.mockito.ArgumentMatchers.anyBoolean()))
            .thenReturn("<div>SURVEY_CARD_MARKER</div>");

        String body = mvc.perform(get("/r/news/test-article"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("SURVEY_CARD_MARKER"), body);
        verify(surveyBlockRenderer).expandForWeb(any(), eq(77L), org.mockito.ArgumentMatchers.anyBoolean());
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
        assertTrue(body.contains("確認後仍需使用點數解鎖"), "不得讓讀者誤以為確認訂閱即可免費閱讀付費內容");

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

    /**
     * ★ 讀者頁的 404 必須是 <b>HTML</b>，不得是 {@code application/problem+json}。
     *
     * <p><b>釘住的是什麼</b>：{@code ApiExceptionHandler} 是<b>沒有範圍限制</b>的
     * {@code @RestControllerAdvice}，它的 {@code @ExceptionHandler(ResponseStatusException.class)}
     * 會攔下任何 controller 拋出的 {@code ResponseStatusException} 並回 JSON。
     * 這一頁是給人看的 HTML 頁，讀者點到失效連結時實機拿到的是：</p>
     * <pre>
     * Content-Type: application/problem+json
     * {"type":"about:blank","title":"Not Found","status":404,"detail":"找不到這篇文章",...}
     * </pre>
     * <p>——瀏覽器直接把一串 JSON 印在畫面上。修法是讀者頁自己回 HTML 而不拋例外
     * （見 {@code ReaderPageController#notFoundPage}）。把它改回
     * {@code orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ...))}，
     * 本測試立刻變紅。</p>
     *
     * <p><b>前置斷言不可省略</b>：如果這個 {@code @WebMvcTest} 切片裡根本沒有那個
     * advice，這條測試就會<b>恆綠而毫無意義</b>——它測到的只是 Spring 的預設行為，
     * 而不是「advice 在場但沒有攔到讀者頁」。因此先斷言 advice 真的被註冊。</p>
     */
    @Test
    void articleNotFoundRendersHtmlPageNotProblemJson() throws Exception {
        assertTrue(applicationContext.getBeanNamesForType(
                world.springai.survey.ApiExceptionHandler.class).length > 0,
            "這個測試切片沒有載入 ApiExceptionHandler，本測試會恆綠而測不到任何東西");

        when(campaignRepository.findBySlug("nope")).thenReturn(Optional.empty());

        String body = mvc.perform(get("/r/news/nope")
                // 瀏覽器實際送出的 Accept 標頭
                .header(HttpHeaders.ACCEPT,
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"))
            .andExpect(status().isNotFound())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                org.hamcrest.Matchers.containsString("text/html")))
            // 這個 404 是暫時狀態：同一個 slug 之後可能被重新上架，
            // 共享快取收下它會讓重新上架後的讀者仍拿到錯誤頁
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("problem+json"), "回應本文不得是 ProblemDetail");
        assertFalse(body.contains("\"status\":404"), "回應本文不得是裸 JSON：" + body);
        assertTrue(body.contains("<html"), "必須是可讀的 HTML 頁：" + body);
        assertTrue(body.contains("找不到這篇文章"), "應顯示中文說明");
        // 死路不可以是死路：必須有一條回到站內的路
        assertTrue(body.contains("/r/archive"), "404 頁必須提供回歷史內容的連結");
    }

    /**
     * 404 頁不得洩漏任何受限內容，也不得洩漏「這個 slug 到底存不存在」。
     *
     * <p>已下架（{@code published_at} 為 NULL）與從未存在的 slug 必須拿到
     * <b>逐字相同</b>的回應本文；若兩者有差異，這個公開端點就成了
     * 「哪些 slug 存在」的探測器。</p>
     */
    @Test
    void notFoundPageIsIdenticalForUnpublishedAndUnknownSlug() throws Exception {
        Campaign unpublished = gatedArticle(Campaign.TIER_PREMIUM, 10);
        unpublished.setPublishedAt(null);
        when(campaignRepository.findBySlug("test-article")).thenReturn(Optional.of(unpublished));
        when(campaignRepository.findBySlug("nope")).thenReturn(Optional.empty());

        String unpublishedBody = mvc.perform(get("/r/news/test-article"))
            .andReturn().getResponse().getContentAsString();
        String unknownBody = mvc.perform(get("/r/news/nope"))
            .andReturn().getResponse().getContentAsString();

        assertFalse(unpublishedBody.contains(SENTINEL), "已下架文章的受限區洩漏到 404 頁");
        assertFalse(unpublishedBody.contains(FREE_MARKER), "已下架文章的免費區也不該出現");
        assertEquals(unknownBody, unpublishedBody,
            "已下架與不存在的回應本文不同，等於洩漏 slug 是否存在");
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

    /** 登入讀者的文章分享連結必須帶自己的推薦碼，才能在確認訂閱後獲得點數。 */
    @Test
    void loggedInReaderGetsPersonalizedArticleShareLink() throws Exception {
        when(campaignRepository.findBySlug("test-article"))
            .thenReturn(Optional.of(gatedArticle(Campaign.TIER_BASIC, 0)));
        when(readerContext.resolve(any()))
            .thenReturn(Optional.of(new ReaderContext.Current(reader(Reader.TIER_FREE, 300), true)));
        stubDecision(AccessDecisionService.Access.FULL, AccessDecisionService.Reason.BASIC_OPEN, 0);

        String body = mvc.perform(get("/r/news/test-article"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("data-share-url=\"/r/news/test-article?ref=CODE1234\""),
            "文章分享網址必須帶登入讀者的推薦碼");
        assertTrue(body.contains("/r/reader-share.js"), "文章頁必須載入分享互動腳本");
        assertTrue(body.contains("data-platform=\"facebook\""));
        assertTrue(body.contains("data-platform=\"instagram\""));
        assertTrue(body.contains("data-platform=\"threads\""));
        assertFalse(body.contains("class=\"share-subscribe-cta\""),
            "已登入讀者不應看到重複訂閱入口");
    }

    /** 從專屬文章連結進站的匿名訪客，訂閱 CTA 必須把推薦碼與文章來源帶到訂閱頁。 */
    @Test
    void sharedArticleVisitorGetsAttributedSubscribeCta() throws Exception {
        when(campaignRepository.findBySlug("test-article"))
            .thenReturn(Optional.of(gatedArticle(Campaign.TIER_BASIC, 0)));
        when(readerContext.resolve(any())).thenReturn(Optional.empty());
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_LOGGED_IN, 0);

        String body = mvc.perform(get("/r/news/test-article").param("ref", "CODE1234"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("/r/?ref=CODE1234&amp;share=test-article"),
            "訂閱 CTA 必須保留推薦碼與文章 slug，否則轉換後無法發點或分析來源");
        int firstCta = body.indexOf("class=\"share-subscribe-cta\"");
        int lastCta = body.lastIndexOf("class=\"share-subscribe-cta\"");
        int articleBody = body.indexOf("class=\"article-body\"");
        assertTrue(firstCta >= 0 && firstCta < articleBody,
            "分享連結訪客必須在文章開頭就看到訂閱入口，不能只藏在長文底部");
        assertTrue(lastCta > articleBody && lastCta != firstCta,
            "文章底部仍應保留第二次訂閱機會");
        assertTrue(body.contains("還沒訂閱或建立帳號？"),
            "CTA 應明確告知未建帳號的訪客可以先免費訂閱");
        assertFalse(body.contains("data-share-url=\"/r/news/test-article?ref=CODE1234\""),
            "匿名訪客不應把原推薦人的專屬連結當成自己的分享連結");
    }

    /**
     * 登入者的導覽列要顯示「我的帳戶」（{@code /r/me}）且不含未登入版連結，
     * 未登入則顯示「登入」（{@code /r/login}）且不含已登入版連結。
     *
     * <p>比照 {@code RulesPageControllerTest#navReflectsLoginState}：兩個方向都要驗，
     * 只驗未登入分支的話，把 {@code navLinks} 改回不含 {@code /r/me} 的版本，
     * 全套測試仍會是綠的，等於證明不了這個測試的名字。</p>
     */
    @Test
    void navReflectsLoginState() throws Exception {
        when(readerContext.resolve(any())).thenReturn(Optional.empty());

        String anonymous = mvc.perform(get("/r/archive")).andReturn().getResponse().getContentAsString();
        assertTrue(anonymous.contains("/r/login"), "未登入時導覽列應含登入連結");
        assertFalse(anonymous.contains("/r/me"), "未登入時導覽列不得含我的帳戶連結");

        Reader loggedInReader = reader(Reader.TIER_FREE, 300);
        when(readerContext.resolve(any()))
            .thenReturn(Optional.of(new ReaderContext.Current(loggedInReader, true)));

        String loggedIn = mvc.perform(get("/r/archive")).andReturn().getResponse().getContentAsString();
        assertTrue(loggedIn.contains("/r/me"), "已登入時導覽列應含我的帳戶連結");
        assertFalse(loggedIn.contains("/r/login"), "已登入時導覽列不得再含登入連結");
    }

    /**
     * 導覽列的規則頁連結（{@code ReaderNav} 產生的形式）。
     *
     * <p>刻意在測試裡另寫一份字串而不引用 {@code ReaderNav} 的常數：讀同一份
     * 實作的斷言恆為真，把連結刪掉也不會變紅。斷言整個 {@code <a>} 標籤而不是
     * 只比對 {@code "/r/rules"}——paywall 的提示連結（{@code >看遊戲規則<}）
     * 也含這個路徑，只比對路徑會讓文章頁的斷言在導覽列少了這一項時仍然通過。</p>
     */
    private static final String NAV_RULES_LINK = "<a href=\"/r/rules\">遊戲規則</a>";

    /**
     * 歷史列表與文章頁的導覽列都必須含規則頁連結，登入與否都要有。
     *
     * <p>規則頁是點數機制的可信度來源（spec §5.11），在此之前它不在任何一份
     * 導覽列裡，「還沒撞到付費牆的人」永遠不會知道有這一頁。</p>
     */
    @Test
    void navContainsRulesLinkOnBothPages() throws Exception {
        when(campaignRepository.findBySlugIsNotNullAndPublishedAtIsNotNullOrderByPublishedAtDesc())
            .thenReturn(List.of(gatedArticle(Campaign.TIER_BASIC, 0)));
        when(campaignRepository.findBySlug("test-article"))
            .thenReturn(Optional.of(gatedArticle(Campaign.TIER_BASIC, 0)));
        stubDecision(AccessDecisionService.Access.FULL, AccessDecisionService.Reason.BASIC_OPEN, 0);

        for (boolean loggedIn : new boolean[] {false, true}) {
            when(readerContext.resolve(any())).thenReturn(loggedIn
                ? Optional.of(new ReaderContext.Current(reader(Reader.TIER_FREE, 300), true))
                : Optional.empty());

            String archive = mvc.perform(get("/r/archive")).andReturn().getResponse().getContentAsString();
            assertTrue(archive.contains(NAV_RULES_LINK),
                "/r/archive 的導覽列少了遊戲規則連結（loggedIn=" + loggedIn + "）");

            String article = mvc.perform(get("/r/news/test-article"))
                .andReturn().getResponse().getContentAsString();
            assertTrue(article.contains(NAV_RULES_LINK),
                "/r/news/{slug} 的導覽列少了遊戲規則連結（loggedIn=" + loggedIn + "）");
        }
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
        // 光有按鈕不夠：沒有腳本的解鎖按鈕是死按鈕，讀者按了毫無反應、永遠無法解鎖。
        // 因此直接斷言腳本本體的兩個必要元素——綁定與端點路徑。
        assertTrue(html.contains("addEventListener"), "解鎖按鈕必須有綁定事件的腳本，否則是死按鈕");
        assertTrue(html.contains("/api/reader/unlock/"), "腳本必須真的打得到解鎖端點");
        assertTrue(html.contains("/r/rules"), "gate 區塊必須附規則頁連結（spec §5.11）");
        assertTrue(html.contains(FREE_MARKER), "免費區必須看得到");
        assertFalse(html.contains(SENTINEL), "受限區絕不可出現在 PARTIAL 回應中");
    }

    /**
     * PARTIAL 時 gate 卡片須列出受限區的章節標題（讓免費讀者知道隱藏了什麼），
     * 但**只有標題**——內文、程式碼註解一律不得外洩，標題本身也必須經過 HTML 跳脫。
     */
    @Test
    void gateBlockListsHiddenSectionHeadingsWithoutLeakingBody() throws Exception {
        String markdown = FREE_MARKER + "\n\n<!--paywall-->\n\n"
            + "## 關卡一：簽章驗證 <b>粗體</b>\n\n" + SENTINEL + "\n\n"
            + "```powershell\n## 圍欄內的程式碼註解\n```\n\n"
            + "### 關卡二：冪等去重\n";
        Campaign c = new Campaign("測試文章", markdown, null, null, null, "now", null, 1, "sent");
        c.setTier(Campaign.TIER_PREMIUM);
        c.setCreditCost(10);
        c.setSlug("test-article");
        c.setPublishedAt(OffsetDateTime.parse("2026-07-20T10:00:00+08:00"));
        when(campaignRepository.findBySlug("test-article")).thenReturn(Optional.of(c));
        when(readerContext.resolve(any())).thenReturn(Optional.empty());
        when(accessDecisionService.resolveCost(any())).thenReturn(10);
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_LOGGED_IN, 0);

        String html = mvc.perform(get("/r/news/test-article"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("class=\"gate-outline\""), "gate 卡片應含隱藏章節清單");
        // 標題必須以「跳脫後」的形式出現——原樣的 <b> 出現即是 XSS 注入口
        assertTrue(html.contains("關卡一：簽章驗證 &lt;b&gt;粗體&lt;/b&gt;"), "章節標題應顯示且經 HTML 跳脫");
        assertFalse(html.contains("關卡一：簽章驗證 <b>粗體</b>"), "未跳脫的標題是 XSS 注入口");
        assertTrue(html.contains("關卡二：冪等去重"), "H3 章節也應列出");
        assertFalse(html.contains(SENTINEL), "清單只能有標題，受限區內文不得外洩");
        assertFalse(html.contains("圍欄內的程式碼註解"), "程式碼圍欄內的 # 行不是標題，不得外洩");
    }

    /** 受限區沒有任何 H2／H3 標題時，gate 卡片不渲染空清單 */
    @Test
    void gateOutlineOmittedWhenGatedContentHasNoHeadings() throws Exception {
        when(campaignRepository.findBySlug("test-article"))
            .thenReturn(Optional.of(gatedArticle(Campaign.TIER_PREMIUM, 10)));
        when(readerContext.resolve(any())).thenReturn(Optional.empty());
        when(accessDecisionService.resolveCost(any())).thenReturn(10);
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_LOGGED_IN, 0);

        String html = mvc.perform(get("/r/news/test-article"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertFalse(html.contains("class=\"gate-outline\""), "沒有章節標題就不該渲染空清單");
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
        assertTrue(html.contains("/r/me#invite"), "應引導到帳戶頁的邀請區賺點數");
        assertTrue(html.contains("/r/rules"), "gate 區塊必須附規則頁連結");
        assertFalse(html.contains("id=\"unlock-btn\""), "餘額不足時不可出現解鎖按鈕");
        assertFalse(html.contains(SENTINEL), "受限區絕不可出現在 PARTIAL 回應中");
    }

    /**
     * 解鎖腳本「只」在 CAN_UNLOCK 時輸出——兩個方向都驗。
     *
     * <p>只驗否定的一半（NOT_LOGGED_IN 時沒有腳本）證明不了這個測試的名字：
     * 把腳本改成永遠輸出空字串，只驗否定面的測試仍會全綠，而解鎖按鈕就變成
     * 死按鈕。因此同一個測試內先驗 NOT_LOGGED_IN 沒有腳本，再驗 CAN_UNLOCK
     * 真的有腳本。</p>
     *
     * <p>NOT_LOGGED_IN 不輸出腳本也不是效能考量——未登入者頁面帶著一段解鎖
     * 腳本，會讓「這篇要付費」的訊息在錯誤的時機出現，而該讀者要做的是登入。</p>
     */
    @Test
    void unlockScriptOnlyAppearsForCanUnlock() throws Exception {
        when(campaignRepository.findBySlug("test-article"))
            .thenReturn(Optional.of(gatedArticle(Campaign.TIER_PREMIUM, 10)));
        when(accessDecisionService.resolveCost(any())).thenReturn(10);

        // 方向一：未登入 → 不得有腳本或按鈕
        when(readerContext.resolve(any())).thenReturn(Optional.empty());
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_LOGGED_IN, 0);

        String anonymousHtml = mvc.perform(get("/r/news/test-article"))
            .andReturn().getResponse().getContentAsString();

        assertFalse(anonymousHtml.contains("unlock-btn"), "未登入時不該有解鎖腳本或按鈕");
        assertFalse(anonymousHtml.contains("/api/reader/unlock/"), "未登入時不該輸出解鎖端點路徑");
        assertTrue(anonymousHtml.contains("/r/login"), "應引導登入");
        assertFalse(anonymousHtml.contains(SENTINEL));

        // 方向二：CAN_UNLOCK → 必須有腳本，否則按鈕是死的
        when(readerContext.resolve(any()))
            .thenReturn(Optional.of(new ReaderContext.Current(reader(Reader.TIER_FREE, 300), true)));
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.CAN_UNLOCK, 0);

        String canUnlockHtml = mvc.perform(get("/r/news/test-article")
                .cookie(new jakarta.servlet.http.Cookie(ReaderSessionService.COOKIE_NAME, "JWT")))
            .andReturn().getResponse().getContentAsString();

        assertTrue(canUnlockHtml.contains("id=\"unlock-btn\""), "CAN_UNLOCK 必須有解鎖按鈕");
        assertTrue(canUnlockHtml.contains("addEventListener"), "CAN_UNLOCK 必須輸出綁定事件的解鎖腳本");
        assertTrue(canUnlockHtml.contains("/api/reader/unlock/"), "腳本必須真的打得到解鎖端點");
        assertFalse(canUnlockHtml.contains(SENTINEL), "受限區絕不可出現在 PARTIAL 回應中");
    }

    /**
     * PREMIUM 且 CAN_UNLOCK，但 markdown 沒有 {@code <!--paywall-->} 標記時，
     * 既不渲染 gate 也不輸出解鎖腳本。
     *
     * <p>整篇都是免費區，沒有東西被擋住，顯示「用 10 點解鎖」等於向讀者
     * 收取他已經能讀到的內容的費用。腳本更不能輸出：頁面上沒有
     * {@code #unlock-btn}，{@code getElementById} 會回 null 而讓
     * {@code addEventListener} 在讀者的 console 直接報錯。</p>
     *
     * <p>沒有這個測試，{@code gateRendered &&} 這個條件會被後人「順手簡化」掉。</p>
     */
    @Test
    void premiumWithoutPaywallMarkerRendersNeitherGateNorScript() throws Exception {
        // 刻意不含 <!--paywall-->：整篇都是免費區
        Campaign c = new Campaign("測試文章", FREE_MARKER + "\n\n沒有受限區的內容。",
            null, null, null, "now", null, 1, "sent");
        c.setTier(Campaign.TIER_PREMIUM);
        c.setCreditCost(10);
        c.setSlug("test-article");
        c.setPublishedAt(OffsetDateTime.parse("2026-07-20T10:00:00+08:00"));

        when(campaignRepository.findBySlug("test-article")).thenReturn(Optional.of(c));
        when(readerContext.resolve(any()))
            .thenReturn(Optional.of(new ReaderContext.Current(reader(Reader.TIER_FREE, 300), true)));
        when(accessDecisionService.resolveCost(any())).thenReturn(10);
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.CAN_UNLOCK, 0);

        String html = mvc.perform(get("/r/news/test-article")
                .cookie(new jakarta.servlet.http.Cookie(ReaderSessionService.COOKIE_NAME, "JWT")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains(FREE_MARKER), "整篇都是免費區，內容必須完整顯示");
        assertFalse(html.contains("class=\"gate\""), "沒有受限區就不該渲染 paywall 區塊");
        assertFalse(html.contains("unlock-btn"), "沒有受限區就不該有解鎖按鈕");
        assertFalse(html.contains("/api/reader/unlock/"),
            "沒有 #unlock-btn 卻輸出腳本，addEventListener 會在讀者的 console 報錯");
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

    /** 側欄：相關文章與分類都渲染出來，且連結指向正確路徑 */
    @Test
    void 文章頁輸出側欄相關文章與分類() throws Exception {
        Campaign article = gatedArticle(Campaign.TIER_BASIC, 0);
        // 相關文章卡需要 campaign.getId() 非 null 才會查詢（與 renderArticleTags 的慣例一致，
        // 真正已發布的文章一定有 id；gatedArticle() fixture 本身不設 id，故補上）
        ReflectionTestUtils.setField(article, "id", 3L);
        when(campaignRepository.findBySlug("test-article"))
            .thenReturn(Optional.of(article));
        when(readerContext.resolve(any())).thenReturn(Optional.empty());
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_LOGGED_IN, 0);
        when(relatedArticleService.relatedTo(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(List.of(new world.springai.survey.newsletter.PublicRelatedArticleService
                .RelatedArticle("other-post", "另一篇文章", OffsetDateTime.parse("2026-06-01T10:00:00+08:00"),
                    null, "🚀")));
        when(campaignTagService.publicTags())
            .thenReturn(List.of(new world.springai.survey.newsletter.PublicCampaignTagService
                .TagSummary("RAG", "rag", 3)));

        String body = mvc.perform(get("/r/news/test-article"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("class=\"article-side\""), "文章頁必須輸出側欄容器");
        assertTrue(body.contains("/r/news/other-post"), "側欄必須含相關文章連結");
        assertTrue(body.contains("另一篇文章"), "側欄必須含相關文章標題");
        assertTrue(body.contains("/r/archive?tag=rag"), "側欄分類必須連到 archive 的標籤篩選");
        assertFalse(body.contains(SENTINEL), "側欄不得讓受限區內容外洩");
    }

    /** 沒有相關文章時整張卡不輸出，不留一張空卡在側欄 */
    @Test
    void 無相關文章時不輸出該卡() throws Exception {
        when(campaignRepository.findBySlug("test-article"))
            .thenReturn(Optional.of(gatedArticle(Campaign.TIER_BASIC, 0)));
        when(readerContext.resolve(any())).thenReturn(Optional.empty());
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_LOGGED_IN, 0);
        when(relatedArticleService.relatedTo(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());

        String body = mvc.perform(get("/r/news/test-article"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("相關文章"), "沒有相關文章時不應輸出該卡標題");
    }

    /** 本篇所屬分類在側欄標成 active，讀者才看得出自己在哪一類 */
    @Test
    void 側欄標示本篇所屬分類() throws Exception {
        Campaign article = gatedArticle(Campaign.TIER_BASIC, 0);
        // Campaign 沒有公開的 setId，沿用本檔既有慣例（見第 181 行）以反射設定 id
        ReflectionTestUtils.setField(article, "id", 7L);
        when(campaignRepository.findBySlug("test-article")).thenReturn(Optional.of(article));
        when(readerContext.resolve(any())).thenReturn(Optional.empty());
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_LOGGED_IN, 0);
        when(campaignTagService.publicTags()).thenReturn(List.of(
            new world.springai.survey.newsletter.PublicCampaignTagService.TagSummary("RAG", "rag", 3),
            new world.springai.survey.newsletter.PublicCampaignTagService.TagSummary("AI", "ai", 5)));
        when(campaignTagService.tagsByCampaign(List.of(7L))).thenReturn(java.util.Map.of(
            7L, List.of(new world.springai.survey.newsletter.PublicCampaignTagService
                .TagSummary("RAG", "rag", 0))));

        String body = mvc.perform(get("/r/news/test-article"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("side-tag active\" href=\"/r/archive?tag=rag"),
            "本篇所屬分類必須帶 active；實際輸出：" + body);
    }

    /** 內文含 survey 標記時，側邊欄出現投票卡：題目、各選項票數與百分比、共 N 人參與；不含轉換率（D4） */
    @Test
    void sidebarShowsVoteStatsForEmbeddedSurveys() throws Exception {
        Campaign article = gatedArticle(Campaign.TIER_BASIC, 0);
        String markdown = FREE_MARKER + "\n\n<!--survey:vote-key-->\n\n<!--paywall-->\n\n" + SENTINEL;
        ReflectionTestUtils.setField(article, "markdown", markdown);
        when(campaignRepository.findBySlug("test-article")).thenReturn(Optional.of(article));
        when(readerContext.resolve(any())).thenReturn(Optional.empty());
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_LOGGED_IN, 0);
        when(surveyBlockRenderer.embeddedFormKeys(any())).thenReturn(List.of("vote-key"));
        when(surveyVoteStatsService.voteStats("vote-key")).thenReturn(Map.of(
            "options", List.of(
                Map.of("value", "選項Ａ", "named", 6L, "anon", 2L),
                Map.of("value", "選項Ｂ", "named", 3L, "anon", 3L)),
            "totalVotes", 14L, "totalNamed", 9L));
        when(formSchemaService.emailVoteQuestion("vote-key")).thenReturn(Optional.of(
            new world.springai.survey.form.FormSchemaService.EmailVoteQuestion(
                "vote-key", "你最想學什麼？", "q1", "題目", List.of())));

        String body = mvc.perform(get("/r/news/test-article"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("你最想學什麼？"), body);
        assertTrue(body.contains("選項Ａ"), body);
        assertTrue(body.contains("8 票"), body); // 6+2
        assertTrue(body.contains("57%"), body); // 8/14 四捨五入
        assertTrue(body.contains("共 14 人參與"), body);
        assertFalse(body.contains("轉換率"), body); // D4
    }

    /** 無內嵌問卷的文章：側邊欄維持原樣（無投票卡） */
    @Test
    void sidebarUnchangedWithoutEmbeddedSurvey() throws Exception {
        when(campaignRepository.findBySlug("test-article")).thenReturn(Optional.of(gatedArticle(Campaign.TIER_BASIC, 0)));
        when(readerContext.resolve(any())).thenReturn(Optional.empty());
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_LOGGED_IN, 0);

        String body = mvc.perform(get("/r/news/test-article"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("人參與"), body);
    }

    /** 首頁曝光問卷存在時：文章側邊欄出現「問卷調查」卡，連向 /r/survey/{key}，標題經跳脫 */
    @Test
    void sidebarShowsHomepageSurveyCard() throws Exception {
        when(campaignRepository.findBySlug("test-article")).thenReturn(Optional.of(gatedArticle(Campaign.TIER_BASIC, 0)));
        when(readerContext.resolve(any())).thenReturn(Optional.empty());
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_LOGGED_IN, 0);
        when(formSchemaService.listHomepageForms()).thenReturn(List.of(
            new world.springai.survey.form.FormSchemaService.HomepageForm(
                "fullstack-course-interest", "AI 全端課程興趣問卷 <b>", 1)));

        String body = mvc.perform(get("/r/news/test-article"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("href=\"/r/survey/fullstack-course-interest\""), body);
        assertTrue(body.contains("AI 全端課程興趣問卷 &lt;b&gt;"), body); // escapeHtml 生效
        assertTrue(body.contains("問卷調查"), body);
    }

    /** 無任何曝光問卷（mock 預設空清單）：側邊欄不出現問卷卡，不留空卡 */
    @Test
    void sidebarHidesSurveyCardWhenNoneExposed() throws Exception {
        when(campaignRepository.findBySlug("test-article")).thenReturn(Optional.of(gatedArticle(Campaign.TIER_BASIC, 0)));
        when(readerContext.resolve(any())).thenReturn(Optional.empty());
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_LOGGED_IN, 0);

        String body = mvc.perform(get("/r/news/test-article"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("問卷調查"), body);
    }
}
