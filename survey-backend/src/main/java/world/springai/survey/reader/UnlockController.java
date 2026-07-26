package world.springai.survey.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.newsletter.Campaign;
import world.springai.survey.newsletter.CampaignRepository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 以點數解鎖文章的端點。
 *
 * <p><b>必須是 POST</b>：GET 會被瀏覽器預抓、被 email 客戶端的連結掃描器觸發、
 * 被搜尋引擎爬，任何一個都會在讀者不知情的狀況下扣掉點數。這與 magic link
 * 遇到 Outlook Safe Links 的問題同源。</p>
 *
 * <p><b>授權一律交給 {@link AccessDecisionService#decide}，本類別不自行判斷
 * tier、VIP、訂閱狀態或發布狀態</b>：看不到按鈕不代表端點不能被直接呼叫，
 * 但「重新檢查」不等於「重寫一份規則」。自行判斷會產生兩份會漂移的授權規則
 * ——日後 decide() 新增任何拒絕條件（停權讀者、地區限制）本端點不會跟著更新，
 * 而這裡是唯一會扣點的入口，漂移的代價是讀者被錯誤收費。
 * decide() 是純函式、不寫入任何東西，在 controller 呼叫沒有交易問題。</p>
 *
 * <p><b>本類別（含 {@link #unlock}）刻意不加 {@code @Transactional}</b>：
 * 它必須留在交易邊界之外，才能安全地把 {@code DataIntegrityViolationException}
 * 轉譯成 ALREADY_UNLOCKED。詳見 {@link UnlockService#unlock} 的說明。</p>
 *
 * <p><b>CSRF 防護的實際來源</b>：本專案沒有 Spring Security，也沒有 CSRF token
 * 機制，別誤以為這個寫入端點有 token 保護。它能抵擋跨站偽造請求靠三件事疊加：
 * ① {@code reader_session} cookie 帶 {@code SameSite=Lax}，跨站發出的 POST
 * 不會附上 cookie，端點會判為未登入而回 401；② 只接受 POST，所以
 * {@code SameSite=Lax} 對「跨站頂層 GET 導航」的例外不適用；
 * ③ CORS 設定未開 {@code allowCredentials}，跨來源的 fetch 帶不了 cookie。
 * 任何一項被改動（例如把 cookie 改成 {@code SameSite=None}、或為了方便前端
 * 開啟 {@code allowCredentials}）都會讓這個扣點端點對 CSRF 敞開。</p>
 */
@RestController
public class UnlockController {

    private static final Logger log = LoggerFactory.getLogger(UnlockController.class);

    private final CampaignRepository campaignRepository;
    private final ReaderContext readerContext;
    private final UnlockService unlockService;
    private final AccessDecisionService accessDecisionService;
    private final ReaderRepository readerRepository;

    /** 注入文章查詢、讀者身分解析、授權決策、解鎖服務與讀者帳戶（重讀權威餘額用） */
    public UnlockController(CampaignRepository campaignRepository,
                           ReaderContext readerContext,
                           UnlockService unlockService,
                           AccessDecisionService accessDecisionService,
                           ReaderRepository readerRepository) {
        this.campaignRepository = campaignRepository;
        this.readerContext = readerContext;
        this.unlockService = unlockService;
        this.accessDecisionService = accessDecisionService;
        this.readerRepository = readerRepository;
    }

    /**
     * 解鎖指定文章。
     *
     * <p>回傳 {@code outcome} / {@code cost} / {@code credits}，讓前端能直接
     * 更新餘額顯示並決定是否重新載入頁面。餘額不足回 200 而非錯誤碼——
     * 那是正常的業務結果，不是失敗。</p>
     *
     * <p>授權結果依 {@link AccessDecisionService.Reason} 分派，回應一律只含
     * 自己的狀態，不洩漏其他讀者或未發布內容的存在。</p>
     */
    @PostMapping("/api/reader/unlock/{slug}")
    public ResponseEntity<Map<String, Object>> unlock(
            @PathVariable String slug,
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {

        Optional<ReaderContext.Current> current = readerContext.resolve(sessionCookie);
        Reader reader = current.map(ReaderContext.Current::reader).orElse(null);
        boolean subscribed = current.map(ReaderContext.Current::subscribed).orElse(false);

        // slug 不存在就沒有可判斷的對象，必須在 decide() 之前擋掉。
        // 發布狀態不在這裡過濾——那是 decide() 的 NOT_PUBLISHED 職責，
        // 在此重複一次就又是兩份規則。
        Campaign campaign = campaignRepository.findBySlug(slug)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到這篇文章"));

        AccessDecisionService.Decision decision =
            accessDecisionService.decide(reader, subscribed, campaign, OffsetDateTime.now());

        // 刻意窮舉所有 Reason 而不寫 default：日後 decide() 新增拒絕條件時
        // 這個 switch 會編譯失敗，強迫作者決定該條件在扣點端點的語意，
        // 而不是靜默落進 default 而讓讀者被扣點。
        return switch (decision.reason()) {
            case NOT_LOGGED_IN -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            case NOT_SUBSCRIBED -> ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            // 與 /r/news/{slug} 行為一致：未發布一律 404，不洩漏草稿存在
            case NOT_PUBLISHED -> ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            // BASIC_OPEN 與 VIP 對這位讀者本來就免費，扣點是純粹的損失。
            //
            // 選 409 而不是 200 no-op 的理由：這兩個 reason 代表「請求與伺服器
            // 的實際狀態相牴觸」（要求為不需付費的內容付費），而 200 會讓任何
            // 呼叫端把它記成一次成功的解鎖／扣點事件。UI 上這條路走不到
            // （解鎖按鈕只在 CAN_UNLOCK 時渲染），會走到的都是直接打端點的
            // 呼叫端，讓它明確收到衝突比靜默成功更容易發現問題。
            // 回應只說「這篇不需要解鎖」與自己的餘額，不含任何他人資訊。
            case BASIC_OPEN, VIP -> ResponseEntity.status(HttpStatus.CONFLICT)
                .body(notRequiredBody(campaign, reader));
            case ALREADY_UNLOCKED, CAN_UNLOCK, NEEDS_CREDITS -> performUnlock(campaign, reader);
        };
    }

    /**
     * 實際執行扣點解鎖，並把兩類例外轉成對讀者有意義的回應。
     *
     * @param campaign 已通過 {@code decide()} 授權判定、確定需要解鎖的文章
     * @param reader   已登入且已確認訂閱的讀者（session 快照，餘額可能已過時）
     */
    private ResponseEntity<Map<String, Object>> performUnlock(Campaign campaign, Reader reader) {
        try {
            UnlockService.Result result =
                unlockService.unlock(reader.getId(), campaign, OffsetDateTime.now());
            return ResponseEntity.ok(body(result.outcome().name(), result.cost(), result.credits()));
        } catch (DataIntegrityViolationException e) {
            // 併發：另一個請求（多半是讀者自己的另一個分頁）已經解鎖。
            // 這個捕捉必須在交易邊界之外——在 UnlockService 內捕捉會因為
            // rollback-only 標記而讓 commit 改拋 UnexpectedRollbackException。
            //
            // cost 與 credits 不可回 0：那是假值，任何拿此回應更新餘額顯示的
            // 程式都會顯示「0 點」（spec §5.11 要求頁面顯示的點數與實際同源）。
            // 撞 UNIQUE 時本次交易整組回滾（未扣點），扣點是另一個請求做的，
            // 所以餘額必須重讀資料庫才是權威值。
            return ResponseEntity.ok(body(
                UnlockService.Outcome.ALREADY_UNLOCKED.name(),
                accessDecisionService.resolveCost(campaign),
                currentCredits(reader)));
        } catch (IllegalStateException e) {
            // UnlockService 的 fail-closed 出口（併發扣款失敗、讀者不存在、
            // tier 非 PREMIUM）。這些狀態未扣點，但不是伺服器故障，回 500 會
            // 讓內部訊息（含讀者 id 與 tier）進到 ERROR log 與錯誤頁。
            //
            // 刻意用 try/catch 而不是在 ApiExceptionHandler 加全域 handler：
            // IllegalStateException 是 JDK 通用例外，全域轉 409 會把其他端點
            // 真正的程式錯誤（例如誤用某個 API 而拋出的 IllegalStateException）
            // 一併偽裝成正常的業務衝突，遮蔽真實故障。範圍限制在這個端點內。
            log.warn("解鎖被拒（fail-closed）：slug={} reader={}",
                campaign.getSlug(), reader.getId(), e);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(body("UNLOCK_UNAVAILABLE",
                    accessDecisionService.resolveCost(campaign), currentCredits(reader)));
        }
    }

    /** 「這篇不需要解鎖」的回應內容：維持與成功回應相同的三個欄位形狀 */
    private Map<String, Object> notRequiredBody(Campaign campaign, Reader reader) {
        return body("NOT_REQUIRED", accessDecisionService.resolveCost(campaign), currentCredits(reader));
    }

    /** 統一的回應形狀：{@code outcome} / {@code cost} / {@code credits} */
    private Map<String, Object> body(String outcome, int cost, int credits) {
        return Map.of("outcome", outcome, "cost", cost, "credits", credits);
    }

    /**
     * 讀取該讀者當前的權威餘額。
     *
     * <p>優先重讀資料庫，而不是用 session 解析出來的快照——快照可能是幾分鐘前的值。
     * 讀不到（極端情況：帳號在這期間被刪除）才退回快照，仍不回傳寫死的 0。</p>
     */
    private int currentCredits(Reader reader) {
        return readerRepository.findById(reader.getId())
            .map(Reader::getCredits)
            .orElseGet(reader::getCredits);
    }
}
