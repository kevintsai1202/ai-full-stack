package world.springai.survey.reader;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.newsletter.Campaign;
import world.springai.survey.newsletter.CampaignRepository;
import world.springai.survey.newsletter.ContentSplitter;
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

    private final CampaignRepository campaignRepository;
    private final MarkdownRenderer markdownRenderer;
    private final ContentSplitter contentSplitter;
    private final AccessDecisionService accessDecisionService;
    private final ArticleAccessRepository articleAccessRepository;
    private final ReaderContext readerContext;
    private final HtmlTemplate htmlTemplate;

    /**
     * 注入內容、授權與渲染所需的服務。
     *
     * <p>不再直接注入 {@code AppSettingService}：解鎖成本的計算（含 §5.2 第 4 條
     * 的下限保護）已收斂到 {@link AccessDecisionService#resolveCost(Campaign)}
     * 唯一一份實作，controller 不應再自行讀取 app_setting 重算一次。</p>
     */
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
    }

    /** 歷史內容列表：只列已發布者，登入者會看到自己的解鎖狀態 */
    @GetMapping(value = "/r/archive", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> archive(
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {
        Optional<ReaderContext.Current> current = readerContext.resolve(sessionCookie);
        List<Campaign> articles = campaignRepository.findBySlugIsNotNullAndPublishedAtIsNotNullOrderByPublishedAtDesc();

        // 登入者的已解鎖清單，用於在列表上標示；未登入則為空集合
        Set<Long> unlocked = current
            .map(c -> articleAccessRepository.findByReaderId(c.reader().getId()).stream()
                .map(ArticleAccess::getCampaignId)
                .collect(Collectors.toSet()))
            .orElse(Collections.emptySet());

        String html = htmlTemplate.render("static/reader/archive.html", Map.of(
            "<!--NAV_LINKS-->", navLinks(current.isPresent()),
            "<!--ARTICLE_LIST-->", renderArticleList(articles, unlocked)));

        // 同一個 URL 的內容會因 reader_session cookie 而異（登入者看到已解鎖標記），
        // 缺這兩個標頭會讓共享快取（CDN、app-gateway 反向代理）把某位讀者的
        // 授權結果快取下來餵給別人，因此一律標記為不可共享快取、且依 Cookie 變化。
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
            .body(html);
    }

    /**
     * 單篇文章。
     *
     * <p>依授權決策決定是否把受限區渲染進 HTML——PARTIAL 時受限區
     * <b>完全不進入回應</b>，不是靠 CSS 隱藏。</p>
     */
    @GetMapping(value = "/r/news/{slug}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> article(@PathVariable String slug,
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
        // 只有「未取得全文且該文章真的有受限區」時才需要 paywall 區塊
        boolean gateRendered = !full && split.hasGate();
        vars.put("<!--GATE_BLOCK-->", gateRendered ? renderGate(decision, campaign, slug) : "");
        // CAN_UNLOCK 時才需要解鎖腳本，其餘情況輸出空字串——
        // 不讓不需要的頁面帶著一段用不到的 JS。
        // 額外要求 gateRendered：沒有渲染出 #unlock-btn 卻輸出腳本，
        // getElementById 會回 null 而讓 addEventListener 在讀者的 console 報錯。
        vars.put("<!--UNLOCK_SCRIPT-->",
            gateRendered && decision.reason() == AccessDecisionService.Reason.CAN_UNLOCK ? UNLOCK_SCRIPT : "");
        String html = htmlTemplate.render("static/reader/article.html", vars);

        // 同一篇文章的網址對登入 VIP 與匿名訪客回傳不同內容（全文 vs 截斷），
        // 若缺這兩個標頭，CDN／app-gateway 這類共享快取可能把某位 VIP 的 FULL
        // 回應快取下來，直接餵給後續的匿名訪客，造成全站付費內容外洩。
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
            .body(html);
    }

    /** 依登入狀態顯示不同的導覽連結 */
    private String navLinks(boolean loggedIn) {
        if (loggedIn) {
            return "<a href=\"/r/archive\">歷史內容</a>";
        }
        return "<a href=\"/r/archive\">歷史內容</a><a href=\"/r/login\">登入</a>";
    }

    /** 渲染 archive 的文章列表；列表不輸出任何內文，日後若要加摘要，必須取自 split(...).freeMarkdown() */
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
            // 未持久化（尚未存檔）的 campaign id 為 null，Set.of()/不可變集合的
            // contains(null) 會直接拋 NPE，因此在呼叫點先擋掉 null，
            // 而不是依賴 unlocked 集合的實作細節（例如原本用 Collections.emptySet()
            // 只是恰好不會踩到，換個集合實作就會靜默復發）。
            if (c.getId() != null && unlocked.contains(c.getId())) {
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
     * <p>這是讀者第一次遇到點數的地方，也是唯一會認真讀規則的時刻
     * （spec §5.11），因此點數相關的兩種狀態都必須附上規則頁連結。</p>
     *
     * <p>所有點數數字都取自 {@link AccessDecisionService#resolveCost}，
     * 不在此重算——頁面顯示的代價與實際扣的必須是同一個來源。</p>
     */
    private String renderGate(AccessDecisionService.Decision decision, Campaign campaign, String slug) {
        String encodedRedirect = "/r/news/" + slug;
        int cost = accessDecisionService.resolveCost(campaign);
        return switch (decision.reason()) {
            case NOT_LOGGED_IN -> gateHtml("接下來的內容需要登入",
                "用訂閱時的 email 登入就能繼續看，不需要密碼。",
                "<a class=\"btn\" href=\"/r/login?redirect=" + HtmlTemplate.escapeHtml(encodedRedirect) + "\">登入繼續閱讀</a>");
            case NOT_SUBSCRIBED -> gateHtml("這個 email 尚未完成訂閱確認",
                "看完整內容需要先完成訂閱確認，可以直接在訂閱頁重新訂閱一次。",
                "<a class=\"btn\" href=\"/r/\">重新訂閱</a>");
            case CAN_UNLOCK -> gateHtml("這是進階內容",
                "解鎖需要 " + cost + " 點，<strong>一次解鎖永久可讀</strong>。",
                "<button class=\"btn\" id=\"unlock-btn\" data-slug=\"" + HtmlTemplate.escapeHtml(slug)
                    + "\" data-cost=\"" + cost + "\">用 " + cost + " 點解鎖</button>"
                    + "<div class=\"msg\" id=\"unlock-msg\"></div>"
                    + rulesHint());
            case NEEDS_CREDITS -> gateHtml("這是進階內容",
                "解鎖需要 " + cost + " 點，你還差 " + decision.shortfall() + " 點。"
                    + "邀請朋友訂閱可以獲得點數。",
                "<a class=\"btn\" href=\"/r/invite\">看我的邀請連結</a>" + rulesHint());
            default -> "";
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
     */
    private static final String UNLOCK_SCRIPT = """
        <script>
          const unlockBtn = document.getElementById('unlock-btn');
          const unlockMsg = document.getElementById('unlock-msg');
          unlockBtn.addEventListener('click', async () => {
            unlockBtn.disabled = true;
            unlockMsg.textContent = '處理中…';
            unlockMsg.className = 'msg show';
            try {
              const res = await fetch('/api/reader/unlock/' + encodeURIComponent(unlockBtn.dataset.slug),
                { method: 'POST' });
              if (res.status === 401) {
                unlockMsg.textContent = '登入已過期，請重新登入。';
                unlockMsg.className = 'msg show err';
                unlockBtn.disabled = false;
                return;
              }
              if (!res.ok) { throw new Error('unlock failed'); }
              const data = await res.json();
              if (data.outcome === 'UNLOCKED' || data.outcome === 'ALREADY_UNLOCKED') {
                // 重新載入讓 server 重新渲染受限區，不由前端插入內容
                location.reload();
                return;
              }
              if (data.outcome === 'INSUFFICIENT_CREDITS') {
                unlockMsg.textContent = '點數不足，目前有 ' + data.credits + ' 點。';
                unlockMsg.className = 'msg show err';
              }
            } catch (e) {
              unlockMsg.textContent = '解鎖失敗，請稍後再試。';
              unlockMsg.className = 'msg show err';
            } finally {
              unlockBtn.disabled = false;
            }
          });
        </script>
        """;

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

    /** 從免費區 markdown 取前 120 字作為 meta description（去掉標記符號） */
    private String summaryOf(String freeMarkdown) {
        String plain = freeMarkdown.replaceAll("[#*`>\\[\\]()!_]", " ").replaceAll("\\s+", " ").trim();
        return plain.length() <= 120 ? plain : plain.substring(0, 120) + "…";
    }
}
