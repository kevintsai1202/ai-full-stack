package world.springai.survey.reader;

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
 * <p><b>授權在此重新檢查，不依賴頁面上有沒有顯示按鈕</b>：看不到按鈕不代表
 * 端點不能被直接呼叫。</p>
 *
 * <p><b>本類別（含 {@link #unlock}）刻意不加 {@code @Transactional}</b>：
 * 它必須留在交易邊界之外，才能安全地把 {@code DataIntegrityViolationException}
 * 轉譯成 ALREADY_UNLOCKED。詳見 {@link UnlockService#unlock} 的說明。</p>
 */
@RestController
public class UnlockController {

    private final CampaignRepository campaignRepository;
    private final ReaderContext readerContext;
    private final UnlockService unlockService;

    /** 注入文章查詢、讀者身分解析與解鎖服務 */
    public UnlockController(CampaignRepository campaignRepository,
                           ReaderContext readerContext,
                           UnlockService unlockService) {
        this.campaignRepository = campaignRepository;
        this.readerContext = readerContext;
        this.unlockService = unlockService;
    }

    /**
     * 解鎖指定文章。
     *
     * <p>回傳 {@code outcome} / {@code cost} / {@code credits}，讓前端能直接
     * 更新餘額顯示並決定是否重新載入頁面。餘額不足回 200 而非錯誤碼——
     * 那是正常的業務結果，不是失敗。</p>
     */
    @PostMapping("/api/reader/unlock/{slug}")
    public ResponseEntity<Map<String, Object>> unlock(
            @PathVariable String slug,
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {

        Optional<ReaderContext.Current> current = readerContext.resolve(sessionCookie);
        if (current.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // 未確認訂閱者不可解鎖：頁面上看不到按鈕不等於端點不能被呼叫
        if (!current.get().subscribed()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // 與 /r/news/{slug} 行為一致：未發布一律 404，不洩漏草稿存在
        Campaign campaign = campaignRepository.findBySlug(slug)
            .filter(Campaign::isPublished)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到這篇文章"));

        UnlockService.Result result;
        try {
            result = unlockService.unlock(current.get().reader().getId(), campaign, OffsetDateTime.now());
        } catch (DataIntegrityViolationException e) {
            // 併發：另一個請求（多半是讀者自己的另一個分頁）已經解鎖。
            // 這個捕捉必須在交易邊界之外——在 UnlockService 內捕捉會因為
            // rollback-only 標記而讓 commit 改拋 UnexpectedRollbackException。
            result = new UnlockService.Result(UnlockService.Outcome.ALREADY_UNLOCKED, 0, 0);
        }

        return ResponseEntity.ok(Map.of(
            "outcome", result.outcome().name(),
            "cost", result.cost(),
            "credits", result.credits()));
    }
}
