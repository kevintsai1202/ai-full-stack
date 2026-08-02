package world.springai.survey.reader;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import world.springai.survey.audience.RecipientService;
import world.springai.survey.promo.PromoProposal;
import world.springai.survey.promo.PromoProposalRepository;
import world.springai.survey.promo.PromoProposalService;
import world.springai.survey.promo.PromoProposalService.ApplyRequest;
import world.springai.survey.promo.PromoProposalService.ApplyResult;
import world.springai.survey.promo.PromoProposalService.InsufficientCreditsException;
import world.springai.survey.promo.PromoProposalService.PromoValidationException;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 讀者端「工商合作」頁：申請工商時間投放（扣點）與檢視自己送出的提案。
 *
 * <p>與 {@link ReaderPortalController} 分開成獨立 controller：工商申請是完全不同的
 * 依賴組合（{@code promo} 套件的服務與資料存取層），塞進帳戶頁只會讓那個 controller
 * 的職責範圍失控。</p>
 *
 * <p><b>頁面而非 API（GET 端點）</b>：未登入時導向登入頁並帶 redirect，理由與
 * {@link ReaderPortalController} 相同——讀者在瀏覽器看到空白的 401 是死路。
 * 但 {@code POST /r/promo/apply} 是頁內表單以 fetch 呼叫的 API，未登入時回 401 JSON
 * 才是前端能處理的形狀（見 {@link #apply}）。</p>
 */
@RestController
public class PromoPortalController {

    /** 讀者端的日期一律換算成台北時區再顯示，避免 UTC 存值在跨日時顯示成前一天 */
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    /** 提案列表的日期顯示格式 */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final HtmlTemplate htmlTemplate;
    private final ReaderContext readerContext;
    private final PromoProposalService promoProposalService;
    private final PromoProposalRepository proposalRepository;
    private final CreditPolicy creditPolicy;
    /** 訂閱規模顯示：與 {@code CampaignService} 同一來源，讓讀者看到的規模與實際觸及一致 */
    private final RecipientService recipientService;

    /** 注入頁面渲染、身分解析、工商提案服務／資料存取、點數參數與訂閱規模來源 */
    public PromoPortalController(HtmlTemplate htmlTemplate,
                                ReaderContext readerContext,
                                PromoProposalService promoProposalService,
                                PromoProposalRepository proposalRepository,
                                CreditPolicy creditPolicy,
                                RecipientService recipientService) {
        this.htmlTemplate = htmlTemplate;
        this.readerContext = readerContext;
        this.promoProposalService = promoProposalService;
        this.proposalRepository = proposalRepository;
        this.creditPolicy = creditPolicy;
        this.recipientService = recipientService;
    }

    /** 工商合作頁：申請表單＋我的提案列表。未登入導向登入頁並帶回跳目標。 */
    @GetMapping(value = "/r/promo", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> promo(
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {
        Optional<ReaderContext.Current> current = readerContext.resolve(sessionCookie);
        if (current.isEmpty()) {
            return redirectToLogin("/r/promo");
        }
        Reader reader = current.get().reader();

        Map<String, String> vars = new HashMap<>();
        // 固定傳 true：未登入者在上面就被導向登入頁了，走到這裡必然是已登入狀態
        vars.put("<!--NAV_LINKS-->", ReaderNav.links(true));
        vars.put("<!--UNIT_COST-->", String.valueOf(creditPolicy.promoPlacementCost()));
        vars.put("<!--SUBSCRIBER_COUNT-->", String.valueOf(recipientService.subscriberCount()));
        vars.put("<!--CREDITS-->", String.valueOf(reader.getCredits()));
        vars.put("<!--PROPOSAL_ROWS-->",
            renderProposalRows(proposalRepository.findByReaderIdOrderByCreatedAtDesc(reader.getId())));

        return privatePage(htmlTemplate.render("templates/reader/promo.html", vars));
    }

    /**
     * 送出工商提案申請：驗證與扣點交由 {@link PromoProposalService#apply}，
     * 本方法只負責身分檢查與例外轉譯成讀者看得懂的 HTTP 狀態碼。
     *
     * <p>未登入回 401 JSON（而非導轉登入頁）：這是表單以 fetch 呼叫的 API 端點，
     * 前端只能處理狀態碼與 JSON body，導轉對它沒有意義。</p>
     */
    @PostMapping("/r/promo/apply")
    public ResponseEntity<Map<String, Object>> apply(
            @RequestBody ApplyRequest request,
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {
        Optional<ReaderContext.Current> current = readerContext.resolve(sessionCookie);
        if (current.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "請先登入"));
        }
        Reader reader = current.get().reader();

        try {
            ApplyResult result = promoProposalService.apply(reader.getId(), request);
            return ResponseEntity.ok(Map.of(
                "proposalId", result.proposalId(),
                "totalCost", result.totalCost(),
                "credits", result.credits()));
        } catch (PromoValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", e.getMessage()));
        } catch (InsufficientCreditsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", e.getMessage()));
        }
    }

    /** 導向登入頁並帶回跳目標 */
    private ResponseEntity<String> redirectToLogin(String target) {
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, "/r/login?redirect=" + target)
            .build();
    }

    /**
     * 個人頁面的回應標頭。
     *
     * <p>內容含餘額與本人提案列表，絕不可被共享快取（CDN、app-gateway 反向代理）
     * 拿去餵給別的讀者。</p>
     */
    private ResponseEntity<String> privatePage(String html) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
            .body(html);
    }

    /**
     * 渲染「我的提案」表格列。
     *
     * <p><b>每個動態值都先過 {@link HtmlTemplate#escapeHtml}</b>：提案名稱、審核備註
     * 都是使用者（或審核者）輸入的自由文字，{@link HtmlTemplate#render} 的佔位符替換
     * 不做跳脫，這裡是唯一把關點。</p>
     */
    private String renderProposalRows(List<PromoProposal> proposals) {
        if (proposals.isEmpty()) {
            return "<tr><td colspan=\"4\" class=\"empty\">尚未送出任何提案。</td></tr>";
        }
        StringBuilder sb = new StringBuilder();
        for (PromoProposal p : proposals) {
            sb.append("<tr><td>").append(HtmlTemplate.escapeHtml(p.getTitle())).append("</td>")
              .append("<td>").append(statusLabel(p)).append("</td>")
              .append("<td>").append(p.getPlacementUsed()).append('/').append(p.getPlacementQuota())
              .append("</td>")
              .append("<td>").append(p.getCreatedAt() == null ? "" : formatTaipeiDate(p.getCreatedAt()))
              .append("</td></tr>");
        }
        return sb.toString();
    }

    /**
     * 把審核狀態代碼轉成讀者看得懂的中文；REJECTED 附上審核備註（跳脫過）。
     *
     * <p>default 分支回固定文字而非原樣輸出 {@code status}：理由與
     * {@link ReaderPortalController#reasonLabel} 相同——{@code promo_proposal.status}
     * 沒有 CHECK 約束，若日後有非常數值流入，原樣拼進 HTML 會是一個未跳脫的注入點。</p>
     */
    private String statusLabel(PromoProposal p) {
        return switch (p.getStatus()) {
            case PromoProposal.STATUS_PENDING -> "待審核";
            case PromoProposal.STATUS_APPROVED -> "已核准";
            case PromoProposal.STATUS_REJECTED -> rejectedLabel(p.getReviewNote());
            case PromoProposal.STATUS_ARCHIVED -> "已封存";
            default -> "狀態未知";
        };
    }

    /** REJECTED 的顯示文字：有審核備註才附上（附上的值須跳脫，備註為審核者自由輸入） */
    private String rejectedLabel(String reviewNote) {
        if (reviewNote == null || reviewNote.isBlank()) {
            return "已拒絕";
        }
        return "已拒絕（" + HtmlTemplate.escapeHtml(reviewNote) + "）";
    }

    /**
     * 把儲存的 {@link OffsetDateTime}（通常是 UTC）換算成台北時區再格式化，
     * 理由與 {@link ReaderPortalController#formatTaipeiDate} 相同：直接用實體儲存的
     * offset 格式化會有跨日誤差。
     */
    private static String formatTaipeiDate(OffsetDateTime value) {
        return value.atZoneSameInstant(TAIPEI).format(DATE_FORMAT);
    }
}
