package world.springai.survey.reader;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import world.springai.survey.newsletter.Campaign;
import world.springai.survey.newsletter.CampaignRepository;
import world.springai.survey.newsletter.ContentSplitter;
import world.springai.survey.newsletter.MarkdownRenderer;
import world.springai.survey.newsletter.PublicCampaignTagService;
import world.springai.survey.newsletter.PublicRelatedArticleService;
import world.springai.survey.newsletter.SurveyBlockRenderer;
import world.springai.survey.media.MediaAssetService;
import world.springai.survey.ReaderSiteLinks;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 讀者端內容頁面：歷史列表與單篇文章，皆為 server 端渲染。
 *
 * <p><b>為什麼不做成「靜態頁 + fetch API」</b>：只有 server 渲染才能讓
 * 「未授權者的回應完全不含受限區」在 HTTP 層次成立。若改由前端 fetch，
 * API 就得回傳整篇內容再由 JS 決定顯示哪段，受限區便出現在網路回應中，
 * paywall 形同虛設。同時 server 渲染也讓免費區確定能被搜尋引擎索引（spec §5.3）。</p>
 */
@RestController
public class ReaderPageController {

    /** 日期顯示格式 */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CampaignRepository campaignRepository;
    private final MarkdownRenderer markdownRenderer;
    private final ContentSplitter contentSplitter;
    private final AccessDecisionService accessDecisionService;
    private final ArticleAccessRepository articleAccessRepository;
    private final ReaderContext readerContext;
    private final HtmlTemplate htmlTemplate;
    private final PublicCampaignTagService campaignTagService;
    private final MediaAssetService mediaAssetService;
    /** 社群 meta 與圖卡使用的正式讀者網域。 */
    private final ReaderSiteLinks readerSiteLinks;
    /**
     * 問卷標記展開器（Task 9 接線）：reader 套件可合法依賴 newsletter 套件
     * （{@code PackageDependencyTest} 只禁止反方向），contentHtml 定案後統一展開。
     */
    private final SurveyBlockRenderer surveyBlockRenderer;
    /** 側欄相關文章查詢；舊單元測試相容建構式為 null，此時側欄不輸出相關文章卡 */
    private final PublicRelatedArticleService relatedArticleService;
    /**
     * 投票統計服務（Task 7 / B1）：查詢內嵌問卷的選項票數分布，供文章側邊欄投票卡使用。
     * 舊單元測試相容建構式為 null，此時側邊欄不輸出投票卡。
     */
    private final world.springai.survey.form.SurveyVoteStatsService surveyVoteStatsService;
    /**
     * 問卷 schema 服務（Task 7 / B1）：查詢內嵌問卷的信中一鍵題標題與選項文字。
     * 舊單元測試相容建構式為 null，此時側邊欄不輸出投票卡。
     */
    private final world.springai.survey.form.FormSchemaService formSchemaService;

    /**
     * 注入內容、授權與渲染所需的服務。
     *
     * <p>不再直接注入 {@code AppSettingService}：解鎖成本的計算（含 §5.2 第 4 條
     * 的下限保護）已收斂到 {@link AccessDecisionService#resolveCost(Campaign)}
     * 唯一一份實作，controller 不應再自行讀取 app_setting 重算一次。</p>
     */
    @Autowired
    public ReaderPageController(CampaignRepository campaignRepository,
                               MarkdownRenderer markdownRenderer,
                               ContentSplitter contentSplitter,
                               AccessDecisionService accessDecisionService,
                               ArticleAccessRepository articleAccessRepository,
                               ReaderContext readerContext,
                               HtmlTemplate htmlTemplate,
                               ObjectProvider<PublicCampaignTagService> campaignTagServiceProvider,
                               MediaAssetService mediaAssetService,
                               ObjectProvider<ReaderSiteLinks> readerSiteLinksProvider,
                               SurveyBlockRenderer surveyBlockRenderer,
                               PublicRelatedArticleService relatedArticleService,
                               world.springai.survey.form.SurveyVoteStatsService surveyVoteStatsService,
                               world.springai.survey.form.FormSchemaService formSchemaService) {
        this.campaignRepository = campaignRepository;
        this.markdownRenderer = markdownRenderer;
        this.contentSplitter = contentSplitter;
        this.accessDecisionService = accessDecisionService;
        this.articleAccessRepository = articleAccessRepository;
        this.readerContext = readerContext;
        this.htmlTemplate = htmlTemplate;
        this.campaignTagService = campaignTagServiceProvider.getIfAvailable();
        this.mediaAssetService = mediaAssetService;
        this.readerSiteLinks = readerSiteLinksProvider.getIfAvailable();
        this.surveyBlockRenderer = surveyBlockRenderer;
        this.relatedArticleService = relatedArticleService;
        this.surveyVoteStatsService = surveyVoteStatsService;
        this.formSchemaService = formSchemaService;
    }

    /** 舊單元測試相容建構式；沒有標籤服務時維持原本列表行為。 */
    public ReaderPageController(CampaignRepository campaignRepository,
                                MarkdownRenderer markdownRenderer,
                                ContentSplitter contentSplitter,
                                AccessDecisionService accessDecisionService,
                                ArticleAccessRepository articleAccessRepository,
                                ReaderContext readerContext,
                                HtmlTemplate htmlTemplate) {
        this.campaignRepository = campaignRepository;
        this.markdownRenderer = markdownRenderer;
        this.contentSplitter = contentSplitter;
        this.accessDecisionService = accessDecisionService;
        this.articleAccessRepository = articleAccessRepository;
        this.readerContext = readerContext;
        this.htmlTemplate = htmlTemplate;
        this.campaignTagService = null;
        this.mediaAssetService = null;
        this.readerSiteLinks = null;
        this.surveyBlockRenderer = null;
        this.relatedArticleService = null;
        this.surveyVoteStatsService = null;
        this.formSchemaService = null;
    }

    /** 歷史內容列表：只列已發布者，登入者會看到自己的解鎖狀態 */
    @GetMapping(value = "/r/archive", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> archiveFiltered(
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie,
            @RequestParam(required = false) String tag) {
        Optional<ReaderContext.Current> current = readerContext.resolve(sessionCookie);
        List<Campaign> articles = campaignRepository.findBySlugIsNotNullAndPublishedAtIsNotNullOrderByPublishedAtDesc();
        Set<Long> matchingIds = campaignTagService == null ? Set.of() : campaignTagService.campaignIds(tag);
        if (tag != null && !tag.isBlank()) {
            articles = campaignTagService == null
                ? List.of()
                : articles.stream().filter(article -> matchingIds.contains(article.getId())).toList();
        }

        // 登入者的已解鎖清單，用於在列表上標示；未登入則為空集合
        Set<Long> unlocked = current
            .map(c -> articleAccessRepository.findByReaderId(c.reader().getId()).stream()
                .map(ArticleAccess::getCampaignId)
                .collect(Collectors.toSet()))
            .orElse(Collections.emptySet());

        String html = htmlTemplate.render("templates/reader/archive.html", Map.of(
            "<!--NAV_LINKS-->", ReaderNav.links(current.isPresent()),
            "<!--TAG_FILTERS-->", renderTagFilters(tag),
            "<!--ARCHIVE_RESULT-->", renderArchiveResult(tag, articles.size()),
            "<!--ARTICLE_LIST-->", renderArticleList(articles, unlocked)));

        // 同一個 URL 的內容會因 reader_session cookie 而異（登入者看到已解鎖標記），
        // 缺這兩個標頭會讓共享快取（CDN、app-gateway 反向代理）把某位讀者的
        // 授權結果快取下來餵給別人，因此一律標記為不可共享快取、且依 Cookie 變化。
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
            .body(html);
    }

    /** 舊單元測試直接呼叫相容方法。 */
    public ResponseEntity<String> archive(String sessionCookie) {
        return archiveFiltered(sessionCookie, null);
    }

    /**
     * 單篇文章。
     *
     * <p>依授權決策決定是否把受限區渲染進 HTML——PARTIAL 時受限區
     * <b>完全不進入回應</b>，不是靠 CSS 隱藏。</p>
     */
    @GetMapping(value = "/r/news/{slug}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> article(@PathVariable String slug,
                         @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie,
                         @RequestParam(required = false) String ref) {
        // 未發布（含已下架）與不存在一律走同一條 404 路徑：兩者若給出不同回應，
        // 這個公開端點就成了「這個 slug 存在嗎」的探測器。
        Optional<Campaign> found = campaignRepository.findBySlug(slug).filter(Campaign::isPublished);
        if (found.isEmpty()) {
            return notFoundPage();
        }
        Campaign campaign = found.get();

        Optional<ReaderContext.Current> current = readerContext.resolve(sessionCookie);
        Reader reader = current.map(ReaderContext.Current::reader).orElse(null);
        boolean subscribed = current.map(ReaderContext.Current::subscribed).orElse(false);

        AccessDecisionService.Decision decision =
            accessDecisionService.decide(reader, subscribed, campaign, OffsetDateTime.now());

        ContentSplitter.Split split = contentSplitter.split(campaign.getMarkdown());
        boolean full = decision.access() == AccessDecisionService.Access.FULL;

        // 關鍵：PARTIAL 時只渲染免費區，受限區的 markdown 根本不進入輸出
        String contentHtml = markdownRenderer.toHtml(split.freeMarkdown());
        if (full && split.hasGate()) {
            contentHtml += markdownRenderer.toHtml(split.gatedMarkdown());
        }

        // 側邊欄投票統計（B1）：標記在 expandForWeb 後會被換成投票卡 HTML，必須先掃，
        // 否則 embeddedFormKeys 對著已展開的 HTML 找不到任何 <!--survey:...--> 標記。
        List<String> embeddedFormKeys = surveyBlockRenderer != null
            ? surveyBlockRenderer.embeddedFormKeys(contentHtml) : List.of();

        // 問卷標記展開（Task 9 接線）：contentHtml 定案（免費區／全文皆已決定）後
        // 統一展開，campaignId 在讀者頁一律已知，選項連結改由 session 歸戶不帶 rt。
        // 第三參數為登入狀態——匿名投票不發點，提示文字必須跟著分歧，否則會對
        // 拿不到點的訪客說謊。surveyBlockRenderer 為 null 只會發生在舊單元測試
        // 相容建構式，維持原內容不動。
        if (surveyBlockRenderer != null) {
            contentHtml = surveyBlockRenderer.expandForWeb(contentHtml, campaign.getId(), reader != null);
        }

        if (full) {
            accessDecisionService.recordAccess(reader, campaign, decision);
        }

        Map<String, String> vars = new HashMap<>();
        vars.put("<!--PAGE_TITLE-->", HtmlTemplate.escapeHtml(campaign.getSubject()) + "｜凱文大叔的電子報");
        vars.put("<!--PAGE_DESCRIPTION-->", HtmlTemplate.escapeHtml(summaryOf(split.freeMarkdown())));
        vars.put("<!--SOCIAL_META-->", renderSocialMeta(campaign, summaryOf(split.freeMarkdown())));
        vars.put("<!--ARTICLE_TITLE-->", HtmlTemplate.escapeHtml(campaign.getSubject()));
        vars.put("<!--ARTICLE_META-->", renderMeta(campaign));
        vars.put("<!--ARTICLE_TAGS-->", renderArticleTags(campaign.getId()));
        vars.put("<!--ARTICLE_SIDEBAR-->", renderSidebar(campaign, embeddedFormKeys));
        vars.put("<!--ARTICLE_COVER-->", renderArticleCover(campaign));
        vars.put("<!--ARTICLE_CONTENT-->", contentHtml); // 已是渲染後的 HTML，不可再跳脫
        vars.put("<!--NAV_LINKS-->", ReaderNav.links(current.isPresent()));
        vars.put("<!--SHARE_URL-->", articleSharePath(slug, reader));
        vars.put("<!--SHARE_NOTE-->", articleShareNote(slug, reader));
        vars.put("<!--STORY_CARD_URL-->", readerSiteLinks == null ? ""
            : HtmlTemplate.escapeHtml(readerSiteLinks.shareCard(slug, "story")));
        vars.put("<!--SUBSCRIBE_CTA-->", renderSharedArticleSubscribeCta(slug, ref, current.isPresent()));
        // 只有「未取得全文且該文章真的有受限區」時才需要 paywall 區塊
        boolean gateRendered = !full && split.hasGate();
        vars.put("<!--GATE_BLOCK-->",
            gateRendered ? renderGate(decision, campaign, slug, split.gatedMarkdown()) : "");
        // CAN_UNLOCK 時才需要解鎖腳本，其餘情況輸出空字串——
        // 不讓不需要的頁面帶著一段用不到的 JS。
        // 額外要求 gateRendered：沒有渲染出 #unlock-btn 卻輸出腳本，
        // getElementById 會回 null 而讓 addEventListener 在讀者的 console 報錯。
        vars.put("<!--UNLOCK_SCRIPT-->",
            gateRendered && decision.reason() == AccessDecisionService.Reason.CAN_UNLOCK ? UNLOCK_SCRIPT : "");
        String html = htmlTemplate.render("templates/reader/article.html", vars);

        // 同一篇文章的網址對登入 VIP 與匿名訪客回傳不同內容（全文 vs 截斷），
        // 若缺這兩個標頭，CDN／app-gateway 這類共享快取可能把某位 VIP 的 FULL
        // 回應快取下來，直接餵給後續的匿名訪客，造成全站付費內容外洩。
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
            .body(html);
    }

    /**
     * 組出文章分享路徑；登入讀者帶自己的推薦碼，匿名訪客只分享一般文章網址。
     *
     * <p>這裡刻意輸出站內路徑，完整 origin 由瀏覽器依正式環境組成，避免測試站或
     * 自訂網域把部署設定中的另一個 host 分享出去。</p>
     */
    private String articleSharePath(String slug, Reader reader) {
        String path = "/r/news/" + java.net.URLEncoder.encode(slug, java.nio.charset.StandardCharsets.UTF_8);
        if (reader == null || reader.getReferralCode() == null || reader.getReferralCode().isBlank()) {
            return HtmlTemplate.escapeHtml(path);
        }
        return HtmlTemplate.escapeHtml(path + "?ref="
            + java.net.URLEncoder.encode(reader.getReferralCode(), java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 依登入狀態說明文章分享是否會累積邀請點數。 */
    private String articleShareNote(String slug, Reader reader) {
        if (reader == null) {
            String redirect = java.net.URLEncoder.encode(
                "/r/news/" + slug, java.nio.charset.StandardCharsets.UTF_8);
            return "分享這篇文章給朋友；登入後可取得會累積邀請點數的專屬連結。"
                + " <a href=\"/r/login?redirect=" + HtmlTemplate.escapeHtml(redirect)
                + "\">登入取得專屬連結</a>";
        }
        return "這是你的專屬文章連結。朋友從連結訂閱並完成信箱確認後，邀請點數會自動入帳。";
    }

    /** Open Graph 使用絕對 URL 與 1200×630 PNG，讓社群抓取器不依賴目前 Host。 */
    private String renderSocialMeta(Campaign campaign, String description) {
        if (readerSiteLinks == null) return "";
        String title = HtmlTemplate.escapeHtml(campaign.getSubject());
        String desc = HtmlTemplate.escapeHtml(description);
        String url = HtmlTemplate.escapeHtml(readerSiteLinks.article(campaign.getSlug()));
        String image = HtmlTemplate.escapeHtml(readerSiteLinks.shareCard(campaign.getSlug(), "og"));
        return """
            <meta property="og:type" content="article">
            <meta property="og:title" content="%s">
            <meta property="og:description" content="%s">
            <meta property="og:url" content="%s">
            <meta property="og:image" content="%s">
            <meta property="og:image:width" content="1200">
            <meta property="og:image:height" content="630">
            <meta name="twitter:card" content="summary_large_image">
            """.formatted(title, desc, url, image);
    }

    /**
     * 被分享連結帶進來的匿名訪客會看到可完成歸因的訂閱入口。
     *
     * <p>推薦碼只放進 URL、最終仍由確認信與既有 ReferralService 驗證；此處不查
     * 推薦碼是否存在，避免公開文章端點成為推薦碼有效性的探測器。</p>
     */
    private String renderSharedArticleSubscribeCta(String slug, String ref, boolean loggedIn) {
        if (loggedIn || ref == null || ref.isBlank() || ref.length() > 128) {
            return "";
        }
        String href = "/r/?ref="
            + java.net.URLEncoder.encode(ref.trim(), java.nio.charset.StandardCharsets.UTF_8)
            + "&share="
            + java.net.URLEncoder.encode(slug, java.nio.charset.StandardCharsets.UTF_8);
        return """
            <aside class="share-subscribe-cta">
              <div><strong>還沒訂閱或建立帳號？</strong><span>免費訂閱後，新文章會寄到信箱，之後可用同一個 Email 登入。</span></div>
              <a class="btn" href="%s">免費訂閱</a>
            </aside>
            """.formatted(HtmlTemplate.escapeHtml(href));
    }

    /**
     * 找不到文章時回傳的 <b>HTML</b> 404 頁。
     *
     * <p><b>為什麼不能沿用 {@code throw new ResponseStatusException(NOT_FOUND, ...)}</b>：
     * {@code ApiExceptionHandler} 是沒有範圍限制的 {@code @RestControllerAdvice}，
     * 它的 {@code @ExceptionHandler(ResponseStatusException.class)} 會連這條讀者頁的例外
     * 一起攔下，回一個 {@code application/problem+json}。實機確認的輸出（帶瀏覽器的
     * Accept 標頭）是：</p>
     * <pre>
     * HTTP/1.1 404
     * Content-Type: application/problem+json
     * {"type":"about:blank","title":"Not Found","status":404,
     *  "detail":"找不到這篇文章","instance":"/r/news/definitely-not-a-real-slug"}
     * </pre>
     * <p>也就是說讀者點到失效連結時，瀏覽器直接把一串 JSON 印在畫面上。那個 advice
     * 本身是必要的（後台的 400 必須把 reason 送出來，否則「slug 已被使用」之類的
     * 原因全部消失），所以修的是<b>這一端</b>：讀者頁自己回 HTML，不再拋例外，
     * 那個 advice 的行為與涵蓋範圍完全不動。</p>
     *
     * <p><b>為什麼不改成限制 advice 的範圍</b>：{@code reader} 套件裡同時住著 HTML 頁
     * （本類）與 JSON API（{@code ReaderPortalController}、{@code UnlockController}、
     * {@code ReaderAuthController} 的 POST），{@code basePackages} 切不開；改用「API
     * controller 掛自訂註解」則是 fail-open 設計——日後新增的後台 controller 只要忘了
     * 掛註解，它的 400 reason 就靜默消失，正好是上一批工作刻意修掉的那個缺陷。
     * 相對地，會拋 {@code ResponseStatusException} 的 HTML 端點全庫只有這<b>一處</b>，
     * 就地修是與問題等寬的改動。</p>
     *
     * <p><b>{@code no-store}</b>：這個 404 是暫時狀態——同一個 slug 之後可能被
     * 重新上架（{@code POST /api/admin/campaigns/{id}/publication}）。若讓共享快取
     * 收下這個 404，重新上架後讀者仍會拿到快取的錯誤頁。頁面內容對所有人相同，
     * 因此不需要 {@code Vary: Cookie}。</p>
     */
    private ResponseEntity<String> notFoundPage() {
        String html = htmlTemplate.render("templates/reader/not-found.html", Map.of());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .header(HttpHeaders.CONTENT_TYPE, "text/html;charset=UTF-8")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(html);
    }

    /** 渲染 archive 部落格卡片；摘要只取免費區，絕不輸出受限內容。 */
    private String renderArticleList(List<Campaign> articles, Set<Long> unlocked) {
        if (articles.isEmpty()) {
            return "<p class=\"empty\">還沒有已發布的內容，訂閱後第一封就會寄給你。</p>";
        }
        Map<Long, List<PublicCampaignTagService.TagSummary>> tags = campaignTagService == null
            ? Map.of()
            : campaignTagService.tagsByCampaign(
                articles.stream().map(Campaign::getId).filter(java.util.Objects::nonNull).toList());
        Map<Long, String> coverUrls = mediaAssetService == null
            ? Map.of()
            : mediaAssetService.publicUrls(articles.stream()
                .map(Campaign::getCoverMediaId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList());
        StringBuilder sb = new StringBuilder("<div class=\"article-grid\">");
        for (Campaign c : articles) {
            ContentSplitter.Split split = contentSplitter.split(c.getMarkdown());
            String summary = summaryOf(split.freeMarkdown());
            int minutes = Math.max(1, summary.replaceAll("\\s+", "").length() / 220 + 1);
            sb.append("<article class=\"article-card\">")
              .append("<a class=\"article-cover\" href=\"/r/news/")
              .append(HtmlTemplate.escapeHtml(c.getSlug())).append("\" aria-hidden=\"true\" tabindex=\"-1\">");
            String coverUrl = c.getCoverMediaId() == null ? null : coverUrls.get(c.getCoverMediaId());
            if (coverUrl != null) {
                sb.append("<img src=\"").append(HtmlTemplate.escapeHtml(coverUrl))
                    .append("\" alt=\"\" loading=\"lazy\" decoding=\"async\">");
            } else {
                sb.append(HtmlTemplate.escapeHtml(c.getCoverEmoji() == null ? "📝" : c.getCoverEmoji()));
            }
            sb
              .append("</a><div class=\"article-card-body\"><div class=\"article-meta\">")
              .append(renderMeta(c)).append("<span>").append(minutes).append(" 分鐘閱讀</span></div>")
              .append("<h2><a href=\"/r/news/").append(HtmlTemplate.escapeHtml(c.getSlug())).append("\">")
              .append(HtmlTemplate.escapeHtml(c.getSubject())).append("</a></h2>")
              .append("<p class=\"article-excerpt\">").append(HtmlTemplate.escapeHtml(summary)).append("</p>")
              .append(renderTagLinks(c.getId() == null
                  ? List.of()
                  : tags.getOrDefault(c.getId(), List.of())));
            // 未持久化（尚未存檔）的 campaign id 為 null，Set.of()/不可變集合的
            // contains(null) 會直接拋 NPE，因此在呼叫點先擋掉 null，
            // 而不是依賴 unlocked 集合的實作細節（例如原本用 Collections.emptySet()
            // 只是恰好不會踩到，換個集合實作就會靜默復發）。
            if (c.getId() != null && unlocked.contains(c.getId())) {
                sb.append("<span class=\"tag unlocked\">已解鎖</span>");
            }
            sb.append("<a class=\"read-more\" href=\"/r/news/")
              .append(HtmlTemplate.escapeHtml(c.getSlug())).append("\">閱讀文章 →</a></div></article>");
        }
        return sb.append("</div>").toString();
    }

    /** 渲染單篇文章封面；沒有圖片時不額外輸出 Emoji 大圖。 */
    private String renderArticleCover(Campaign campaign) {
        if (mediaAssetService == null || campaign.getCoverMediaId() == null) {
            return "";
        }
        return mediaAssetService.publicUrl(campaign.getCoverMediaId())
            .map(url -> "<figure class=\"article-hero-cover\"><img src=\""
                + HtmlTemplate.escapeHtml(url) + "\" alt=\""
                + HtmlTemplate.escapeHtml(campaign.getSubject())
                + "\" fetchpriority=\"high\" decoding=\"async\"></figure>")
            .orElse("");
    }

    /** 渲染 archive 的 hashtag 篩選列。 */
    private String renderTagFilters(String selectedSlug) {
        if (campaignTagService == null) {
            return "";
        }
        StringBuilder html = new StringBuilder("<nav class=\"tag-filters\" aria-label=\"依 hashtag 篩選\"><a class=\"filter-chip")
            .append(selectedSlug == null || selectedSlug.isBlank() ? " active" : "")
            .append("\" href=\"/r/archive\">全部</a>");
        for (PublicCampaignTagService.TagSummary tag : campaignTagService.publicTags()) {
            html.append("<a class=\"filter-chip")
                .append(tag.slug().equals(selectedSlug) ? " active" : "")
                .append("\" href=\"/r/archive?tag=")
                .append(java.net.URLEncoder.encode(tag.slug(), java.nio.charset.StandardCharsets.UTF_8))
                .append("\">#").append(HtmlTemplate.escapeHtml(tag.name()))
                .append(" <span>").append(tag.articleCount()).append("</span></a>");
        }
        return html.append("</nav>").toString();
    }

    /** 顯示目前篩選結果摘要。 */
    private String renderArchiveResult(String tag, int count) {
        if (tag == null || tag.isBlank()) {
            return "<p class=\"archive-result\">共 " + count + " 篇文章</p>";
        }
        return "<p class=\"archive-result\">目前篩選 <strong>#" + HtmlTemplate.escapeHtml(tag)
            + "</strong>，共 " + count + " 篇文章。</p>";
    }

    /** 渲染單篇文章標籤。 */
    private String renderArticleTags(Long campaignId) {
        if (campaignTagService == null || campaignId == null) {
            return "";
        }
        return renderTagLinks(campaignTagService.tagsByCampaign(List.of(campaignId))
            .getOrDefault(campaignId, List.of()));
    }

    /** 側欄相關文章的顯示篇數上限 */
    private static final int SIDEBAR_RELATED_LIMIT = 5;

    /**
     * 渲染文章頁右側欄：投票統計卡、分類選單與相關文章卡。
     *
     * <p>各卡皆可獨立缺席——服務未注入（舊相容建構式）或查無資料時
     * 該卡輸出空字串，不留一張空卡在側欄。投票卡排最前：文章專屬資訊
     * 優先於通用的分類／相關文章。</p>
     */
    private String renderSidebar(Campaign campaign, List<String> embeddedFormKeys) {
        return renderVoteStatsCards(embeddedFormKeys) + renderCategoryCard(campaign) + renderRelatedCard(campaign);
    }

    /**
     * 內嵌問卷投票統計卡（B1）：每份問卷一張卡，依標記在內文出現的順序全部列出（spec §4.1 不截斷）。
     * 顯示各選項票數＋百分比與「共 N 人參與」；不顯示轉換率（D4）。
     * 未設定信中一鍵題（emailVoteQuestion 為空）的 key 跳過——該標記本來就不會被渲染成投票卡。
     */
    private String renderVoteStatsCards(List<String> formKeys) {
        if (formKeys.isEmpty() || surveyVoteStatsService == null || formSchemaService == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String formKey : formKeys) {
            Optional<world.springai.survey.form.FormSchemaService.EmailVoteQuestion> question =
                formSchemaService.emailVoteQuestion(formKey);
            if (question.isEmpty()) {
                continue;
            }
            Map<String, Object> stats = surveyVoteStatsService.voteStats(formKey);
            long total = ((Number) stats.getOrDefault("totalVotes", 0L)).longValue();
            sb.append("<section class=\"side-card\"><h2 class=\"side-title\">")
              .append(HtmlTemplate.escapeHtml(question.get().title())).append("</h2>");
            if (total == 0) {
                sb.append("<p class=\"side-note\">尚無人投票</p></section>");
                continue;
            }
            sb.append("<ul class=\"side-votes\">");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> options = (List<Map<String, Object>>) stats.getOrDefault("options", List.of());
            for (Map<String, Object> option : options) {
                long votes = ((Number) option.getOrDefault("named", 0L)).longValue()
                           + ((Number) option.getOrDefault("anon", 0L)).longValue();
                long percent = Math.round(votes * 100.0 / total);
                sb.append("<li><span class=\"vote-label\">")
                  .append(HtmlTemplate.escapeHtml(String.valueOf(option.get("value"))))
                  .append("</span><span class=\"vote-bar\"><i style=\"width:").append(percent)
                  .append("%\"></i></span><span class=\"vote-count\">").append(votes)
                  .append(" 票 · ").append(percent).append("%</span></li>");
            }
            sb.append("</ul><p class=\"side-note\">共 ").append(total).append(" 人參與</p></section>");
        }
        return sb.toString();
    }

    /** 分類卡：列出所有公開 hashtag 與篇數，本篇所屬者標 active */
    private String renderCategoryCard(Campaign campaign) {
        if (campaignTagService == null) {
            return "";
        }
        List<PublicCampaignTagService.TagSummary> all = campaignTagService.publicTags();
        if (all.isEmpty()) {
            return "";
        }
        Set<String> own = campaign.getId() == null
            ? Set.of()
            : campaignTagService.tagsByCampaign(List.of(campaign.getId()))
                .getOrDefault(campaign.getId(), List.of()).stream()
                .map(PublicCampaignTagService.TagSummary::slug)
                .collect(Collectors.toSet());
        StringBuilder html = new StringBuilder(
            "<section class=\"side-card\"><h2 class=\"side-title\">分類</h2><div class=\"side-tags\">");
        for (PublicCampaignTagService.TagSummary tag : all) {
            html.append("<a class=\"side-tag")
                .append(own.contains(tag.slug()) ? " active" : "")
                .append("\" href=\"/r/archive?tag=")
                .append(java.net.URLEncoder.encode(tag.slug(), java.nio.charset.StandardCharsets.UTF_8))
                .append("\">#").append(HtmlTemplate.escapeHtml(tag.name()))
                .append(" <span>").append(tag.articleCount()).append("</span></a>");
        }
        return html.append("</div></section>").toString();
    }

    /**
     * 相關文章卡：同標籤優先、不足補最新（規則見 PublicRelatedArticleService）。
     *
     * <p>只輸出標題、日期與封面——刻意不放摘要，避免多一條可能帶出受限區的路徑。</p>
     */
    private String renderRelatedCard(Campaign campaign) {
        if (relatedArticleService == null || campaign.getId() == null) {
            return "";
        }
        List<PublicRelatedArticleService.RelatedArticle> related =
            relatedArticleService.relatedTo(campaign.getId(), SIDEBAR_RELATED_LIMIT);
        if (related.isEmpty()) {
            return "";
        }
        Map<Long, String> coverUrls = mediaAssetService == null
            ? Map.of()
            : mediaAssetService.publicUrls(related.stream()
                .map(PublicRelatedArticleService.RelatedArticle::coverMediaId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList());
        StringBuilder html = new StringBuilder(
            "<section class=\"side-card\"><h2 class=\"side-title\">相關文章</h2><ul class=\"side-list\">");
        for (var article : related) {
            String coverUrl = article.coverMediaId() == null ? null : coverUrls.get(article.coverMediaId());
            html.append("<li><a href=\"/r/news/")
                .append(HtmlTemplate.escapeHtml(article.slug())).append("\">")
                .append("<span class=\"side-thumb\">");
            if (coverUrl != null) {
                html.append("<img src=\"").append(HtmlTemplate.escapeHtml(coverUrl))
                    .append("\" alt=\"\" loading=\"lazy\" decoding=\"async\">");
            } else {
                html.append(HtmlTemplate.escapeHtml(
                    article.coverEmoji() == null ? "📝" : article.coverEmoji()));
            }
            html.append("</span><span class=\"side-copy\"><strong>")
                .append(HtmlTemplate.escapeHtml(article.subject())).append("</strong>");
            if (article.publishedAt() != null) {
                html.append("<small>").append(article.publishedAt().format(DATE_FORMAT)).append("</small>");
            }
            html.append("</span></a></li>");
        }
        return html.append("</ul></section>").toString();
    }

    /** 渲染可點選的 hashtag 連結。 */
    private String renderTagLinks(List<PublicCampaignTagService.TagSummary> tags) {
        if (tags.isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder("<div class=\"article-tags\">");
        for (PublicCampaignTagService.TagSummary tag : tags) {
            html.append("<a href=\"/r/archive?tag=")
                .append(java.net.URLEncoder.encode(tag.slug(), java.nio.charset.StandardCharsets.UTF_8))
                .append("\">#").append(HtmlTemplate.escapeHtml(tag.name())).append("</a>");
        }
        return html.append("</div>").toString();
    }

    /** 渲染日期與分級標籤 */
    private String renderMeta(Campaign campaign) {
        StringBuilder sb = new StringBuilder();
        if (campaign.getPublishedAt() != null) {
            sb.append("<span>").append(campaign.getPublishedAt().format(DATE_FORMAT)).append("</span>");
        }
        if (campaign.isPremium()) {
            sb.append("<span class=\"tag premium\">進階</span>");
        }
        return sb.toString();
    }

    /**
     * 渲染 paywall 提示區塊。
     *
     * <p>這是讀者第一次遇到點數的地方，也是唯一會認真讀規則的時刻
     * （spec §5.11），因此點數相關的兩種狀態都必須附上規則頁連結。</p>
     *
     * <p>所有點數數字都取自 {@link AccessDecisionService#resolveCost}，
     * 不在此重算——頁面顯示的代價與實際扣的必須是同一個來源。</p>
     */
    private String renderGate(AccessDecisionService.Decision decision, Campaign campaign, String slug,
                              String gatedMarkdown) {
        String encodedRedirect = "/r/news/" + slug;
        int cost = accessDecisionService.resolveCost(campaign);
        // 隱藏章節預告：四種 gate 狀態都顯示——讓免費讀者知道牆後有什麼，是解鎖動機的一部分
        String outline = renderGateOutline(gatedMarkdown);
        return switch (decision.reason()) {
            case NOT_LOGGED_IN -> gateHtml("接下來是付費內容",
                "請先用訂閱時的 email 登入；登入後仍需使用點數解鎖，不需要密碼。", outline,
                "<a class=\"btn\" href=\"/r/login?redirect=" + HtmlTemplate.escapeHtml(encodedRedirect) + "\">登入查看解鎖方式</a>");
            case NOT_SUBSCRIBED -> gateHtml("這個 email 尚未完成訂閱確認",
                "請先在訂閱頁完成確認；確認後仍需使用點數解鎖付費內容。", outline,
                "<a class=\"btn\" href=\"/r/\">重新訂閱</a>");
            case CAN_UNLOCK -> gateHtml("這是進階內容",
                "解鎖需要 " + cost + " 點，<strong>一次解鎖永久可讀</strong>。", outline,
                // 只輸出 data-slug：腳本唯一讀取的資料屬性。data-cost 沒有任何
                // 消費者，留著只會讓後人以為前端會用它做金額計算。
                "<button class=\"btn\" id=\"unlock-btn\" data-slug=\"" + HtmlTemplate.escapeHtml(slug)
                    + "\">用 " + cost + " 點解鎖</button>"
                    + "<div class=\"msg\" id=\"unlock-msg\"></div>"
                    + rulesHint());
            case NEEDS_CREDITS -> gateHtml("這是進階內容",
                "解鎖需要 " + cost + " 點，你還差 " + decision.shortfall() + " 點。"
                    + "邀請朋友訂閱可以獲得點數。", outline,
                "<a class=\"btn\" href=\"/r/me#invite\">看我的邀請連結</a>" + rulesHint());
            // 窮舉列出而非 default -> ""：這個方法只在 !full 時被呼叫（見呼叫端
            // gateRendered 的判斷），這幾個 reason 代表「本來就不需要顯示
            // paywall」的情況，理論上不會在此出現，仍需明列而不是 default，
            // 是為了與 UnlockController 的 switch 一致——那裡刻意不寫 default，
            // 讓日後 Reason 新增值時兩處都編譯失敗，而不是這裡靜默吞成空字串，
            // 讓讀者拿到一篇被截斷、卻完全沒有 paywall 說明的文章。
            case BASIC_OPEN, VIP, ALREADY_UNLOCKED, NOT_PUBLISHED -> "";
        };
    }

    /** 規則頁連結：點數機制的可信度來源，兩種點數狀態都必須附上（spec §5.11） */
    private String rulesHint() {
        return "<p class=\"gate-hint\">不清楚點數怎麼運作？<a href=\"/r/rules\">看遊戲規則</a></p>";
    }

    /**
     * 解鎖按鈕的前端腳本。
     *
     * <p>解鎖成功後以 {@code location.reload()} 重新載入，讓受限區由 server
     * 重新渲染進來——不是用 JS 把內容插進頁面。受限區必須始終由 server 端
     * 依授權結果決定是否輸出（spec §5.3），前端插入等於受限區曾經出現在
     * 某個 API 回應中，paywall 就形同虛設。</p>
     *
     * <p><b>解除 disabled 只寫在各個錯誤分支，不用 {@code finally}</b>：
     * finally 會在 {@code location.reload()} 之後執行，而 reload 是非同步的
     * ——瀏覽器實際換頁前按鈕已被解除鎖定，讀者能再按一次而多打一次扣點端點。</p>
     */
    private static final String UNLOCK_SCRIPT = """
        <script>
          const unlockBtn = document.getElementById('unlock-btn');
          const unlockMsg = document.getElementById('unlock-msg');
          unlockBtn.addEventListener('click', async () => {
            window.ReaderAnalytics?.event('UNLOCK_CLICK', 'unlock_click');
            unlockBtn.disabled = true;
            unlockMsg.textContent = '處理中…';
            unlockMsg.className = 'msg show';
            try {
              const res = await fetch('/api/reader/unlock/' + encodeURIComponent(unlockBtn.dataset.slug),
                { method: 'POST' });
              if (res.status === 401) {
                window.ReaderAnalytics?.event('UNLOCK_ERROR', 'unlock_error');
                unlockMsg.textContent = '登入已過期，請重新登入。';
                unlockMsg.className = 'msg show err';
                unlockBtn.disabled = false;
                return;
              }
              if (res.status === 409) {
                const conflict = await res.json();
                if (conflict.outcome === 'NOT_REQUIRED') {
                  window.ReaderAnalytics?.event('UNLOCK_SUCCESS', 'unlock_success');
                  // 這篇文章的授權狀態在按下按鈕之前就已改變（例如另一個分頁
                  // 取得 VIP、或後台把這篇改成 BASIC），實際狀態是「本來就
                  // 看得到全文」，不是失敗。重新載入讓 server 依最新授權狀態
                  // 重新渲染即可，不解除 disabled——理由與下方 UNLOCKED 分支
                  // 相同：reload 生效前的空窗期若按鈕可再按，會多打一次端點。
                  location.reload();
                  return;
                }
                // 其餘 409（UNLOCK_UNAVAILABLE）維持原本的錯誤訊息語意。
                throw new Error('unlock failed');
              }
              if (!res.ok) { throw new Error('unlock failed'); }
              const data = await res.json();
              if (data.outcome === 'UNLOCKED' || data.outcome === 'ALREADY_UNLOCKED') {
                window.ReaderAnalytics?.event('UNLOCK_SUCCESS', 'unlock_success');
                // 重新載入讓 server 重新渲染受限區，不由前端插入內容。
                // 這裡刻意不解除 disabled：reload 生效前的空窗期若按鈕可再按，
                // 讀者會多打一次扣點端點。
                location.reload();
                return;
              }
              if (data.outcome === 'INSUFFICIENT_CREDITS') {
                window.ReaderAnalytics?.event('UNLOCK_INSUFFICIENT', 'unlock_insufficient_credits');
                unlockMsg.textContent = '點數不足，目前有 ' + data.credits + ' 點。';
                unlockMsg.className = 'msg show err';
              }
              unlockBtn.disabled = false;
            } catch (e) {
              window.ReaderAnalytics?.event('UNLOCK_ERROR', 'unlock_error');
              unlockMsg.textContent = '解鎖失敗，請稍後再試。';
              unlockMsg.className = 'msg show err';
              unlockBtn.disabled = false;
            }
          });
        </script>
        """;

    /** 組 paywall 區塊的 HTML（含漸層淡出）；outline 為隱藏章節預告，可為空字串 */
    private String gateHtml(String title, String description, String outline, String action) {
        return """
            <div class="gate">
              <div class="fade"></div>
              <h3>%s</h3>
              <p>%s</p>
              %s%s
            </div>
            """.formatted(title, description, outline, action);
    }

    /**
     * 渲染「隱藏了什麼」章節預告清單；受限區沒有任何 H2／H3 標題時回空字串。
     *
     * <p>只輸出標題文字（逐一經 {@link HtmlTemplate#escapeHtml}），內文絕不經過
     * 這條路徑——受限內容在 PARTIAL 時不進入回應的鐵律（spec §5.3）不因預告而放寬。</p>
     */
    private String renderGateOutline(String gatedMarkdown) {
        List<String> headings = contentSplitter.headings(gatedMarkdown);
        if (headings.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("<div class=\"gate-outline\"><p>解鎖後可閱讀以下章節：</p><ul>");
        for (String heading : headings) {
            sb.append("<li>").append(HtmlTemplate.escapeHtml(heading)).append("</li>");
        }
        return sb.append("</ul></div>").toString();
    }

    /** 從免費區 markdown 取前 120 字作為 meta description（去掉標記符號） */
    private String summaryOf(String freeMarkdown) {
        String plain = freeMarkdown.replaceAll("[#*`>\\[\\]()!_]", " ").replaceAll("\\s+", " ").trim();
        return plain.length() <= 120 ? plain : plain.substring(0, 120) + "…";
    }
}
