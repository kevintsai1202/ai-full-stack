package world.springai.survey.reader;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.AppSettingService;
import world.springai.survey.newsletter.Campaign;
import world.springai.survey.newsletter.CampaignRepository;
import world.springai.survey.newsletter.MarkdownRenderer;

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

    /** PREMIUM 解鎖點數的後備預設值 */
    private static final int DEFAULT_PREMIUM_COST = 10;

    private final CampaignRepository campaignRepository;
    private final MarkdownRenderer markdownRenderer;
    private final ContentSplitter contentSplitter;
    private final AccessDecisionService accessDecisionService;
    private final ArticleAccessRepository articleAccessRepository;
    private final ReaderContext readerContext;
    private final HtmlTemplate htmlTemplate;
    private final AppSettingService appSettingService;

    /** 注入內容、授權與渲染所需的服務 */
    public ReaderPageController(CampaignRepository campaignRepository,
                               MarkdownRenderer markdownRenderer,
                               ContentSplitter contentSplitter,
                               AccessDecisionService accessDecisionService,
                               ArticleAccessRepository articleAccessRepository,
                               ReaderContext readerContext,
                               HtmlTemplate htmlTemplate,
                               AppSettingService appSettingService) {
        this.campaignRepository = campaignRepository;
        this.markdownRenderer = markdownRenderer;
        this.contentSplitter = contentSplitter;
        this.accessDecisionService = accessDecisionService;
        this.articleAccessRepository = articleAccessRepository;
        this.readerContext = readerContext;
        this.htmlTemplate = htmlTemplate;
        this.appSettingService = appSettingService;
    }

    /** 歷史內容列表：只列已發布者，登入者會看到自己的解鎖狀態 */
    @GetMapping(value = "/r/archive", produces = MediaType.TEXT_HTML_VALUE)
    public String archive(
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {
        Optional<ReaderContext.Current> current = readerContext.resolve(sessionCookie);
        List<Campaign> articles = campaignRepository.findByPublishedAtIsNotNullOrderByPublishedAtDesc();

        // 登入者的已解鎖清單，用於在列表上標示；未登入則為空集合
        Set<Long> unlocked = current
            .map(c -> articleAccessRepository.findByReaderId(c.reader().getId()).stream()
                .map(ArticleAccess::getCampaignId)
                .collect(Collectors.toSet()))
            .orElse(Collections.emptySet());

        return htmlTemplate.render("static/reader/archive.html", Map.of(
            "<!--NAV_LINKS-->", navLinks(current.isPresent()),
            "<!--ARTICLE_LIST-->", renderArticleList(articles, unlocked)));
    }

    /**
     * 單篇文章。
     *
     * <p>依授權決策決定是否把受限區渲染進 HTML——PARTIAL 時受限區
     * <b>完全不進入回應</b>，不是靠 CSS 隱藏。</p>
     */
    @GetMapping(value = "/r/news/{slug}", produces = MediaType.TEXT_HTML_VALUE)
    public String article(@PathVariable String slug,
                         @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {
        Campaign campaign = campaignRepository.findBySlug(slug)
            .filter(Campaign::isPublished)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到這篇文章"));

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

        if (full) {
            accessDecisionService.recordAccess(reader, campaign, decision);
        }

        Map<String, String> vars = new HashMap<>();
        vars.put("<!--PAGE_TITLE-->", HtmlTemplate.escapeHtml(campaign.getSubject()) + "｜凱文大叔的電子報");
        vars.put("<!--PAGE_DESCRIPTION-->", HtmlTemplate.escapeHtml(summaryOf(split.freeMarkdown())));
        vars.put("<!--ARTICLE_TITLE-->", HtmlTemplate.escapeHtml(campaign.getSubject()));
        vars.put("<!--ARTICLE_META-->", renderMeta(campaign));
        vars.put("<!--ARTICLE_CONTENT-->", contentHtml); // 已是渲染後的 HTML，不可再跳脫
        vars.put("<!--NAV_LINKS-->", navLinks(current.isPresent()));
        vars.put("<!--GATE_BLOCK-->", full || !split.hasGate() ? "" : renderGate(decision, campaign, slug));
        return htmlTemplate.render("static/reader/article.html", vars);
    }

    /** 依登入狀態顯示不同的導覽連結 */
    private String navLinks(boolean loggedIn) {
        if (loggedIn) {
            return "<a href=\"/r/archive\">歷史內容</a>";
        }
        return "<a href=\"/r/archive\">歷史內容</a><a href=\"/r/login\">登入</a>";
    }

    /** 渲染 archive 的文章列表；摘要一律取自免費區 */
    private String renderArticleList(List<Campaign> articles, Set<Long> unlocked) {
        if (articles.isEmpty()) {
            return "<p class=\"empty\">還沒有已發布的內容，訂閱後第一封就會寄給你。</p>";
        }
        StringBuilder sb = new StringBuilder("<ul class=\"article-list\">");
        for (Campaign c : articles) {
            sb.append("<li class=\"article-item\">")
              .append("<h2><a href=\"/r/news/").append(HtmlTemplate.escapeHtml(c.getSlug())).append("\">")
              .append(HtmlTemplate.escapeHtml(c.getSubject())).append("</a></h2>")
              .append("<div class=\"article-meta\">").append(renderMeta(c));
            if (unlocked.contains(c.getId())) {
                sb.append("<span class=\"tag unlocked\">已解鎖</span>");
            }
            sb.append("</div></li>");
        }
        return sb.append("</ul>").toString();
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
     * <p>階段 B 一律只發布 BASIC 文章（見 Global Constraints），因此 NEEDS_CREDITS
     * 的文案是為階段 C 預備的；此時真正會出現的是「未登入」與「未確認訂閱」兩種。</p>
     */
    private String renderGate(AccessDecisionService.Decision decision, Campaign campaign, String slug) {
        String encodedRedirect = "/r/news/" + slug;
        return switch (decision.reason()) {
            case NOT_LOGGED_IN -> gateHtml("接下來的內容需要登入",
                "用訂閱時的 email 登入就能繼續看，不需要密碼。",
                "<a class=\"btn\" href=\"/r/login?redirect=" + HtmlTemplate.escapeHtml(encodedRedirect) + "\">登入繼續閱讀</a>");
            case NOT_SUBSCRIBED -> gateHtml("確認訂閱後就能看完整內容",
                "你的信箱裡應該有一封確認信，點了就完成訂閱。找不到的話可以重新訂閱一次。",
                "<a class=\"btn\" href=\"/r/\">重新訂閱</a>");
            case NEEDS_CREDITS -> gateHtml("這是進階內容",
                decision.shortfall() > 0
                    ? "解鎖需要 " + resolveCost(campaign) + " 點，你還差 " + decision.shortfall() + " 點。"
                    : "解鎖需要 " + resolveCost(campaign) + " 點。",
                "<a class=\"btn\" href=\"/r/archive\">先看其他內容</a>");
            default -> "";
        };
    }

    /** 組 paywall 區塊的 HTML（含漸層淡出） */
    private String gateHtml(String title, String description, String action) {
        return """
            <div class="gate">
              <div class="fade"></div>
              <h3>%s</h3>
              <p>%s</p>
              %s
            </div>
            """.formatted(title, description, action);
    }

    /** 取得解鎖成本；與 AccessDecisionService 用同一套後備規則 */
    private int resolveCost(Campaign campaign) {
        return campaign.getCreditCost() > 0
            ? campaign.getCreditCost()
            : appSettingService.getInt(AppSettingService.CREDIT_PREMIUM_COST, DEFAULT_PREMIUM_COST);
    }

    /** 從免費區 markdown 取前 120 字作為 meta description（去掉標記符號） */
    private String summaryOf(String freeMarkdown) {
        String plain = freeMarkdown.replaceAll("[#*`>\\[\\]()!_]", " ").replaceAll("\\s+", " ").trim();
        return plain.length() <= 120 ? plain : plain.substring(0, 120) + "…";
    }
}
