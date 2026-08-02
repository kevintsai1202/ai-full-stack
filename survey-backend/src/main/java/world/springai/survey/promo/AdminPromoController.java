package world.springai.survey.promo;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import world.springai.survey.AdminKeyGuard;

/**
 * 工商時間提案後台 API：審核（核准／拒絕／封存）、編輯器插入、成效統計。
 * 全部端點皆須驗證 {@code X-Admin-Key}，慣例比照 {@code AdminCampaignController}。
 */
@RestController
public class AdminPromoController {

    /** HTTP Header 名稱：後台金鑰 */
    private static final String KEY_HEADER = "X-Admin-Key";

    /** 金鑰驗證元件 */
    private final AdminKeyGuard guard;
    /** 提案審核服務 */
    private final PromoProposalService proposalService;
    /** 版位（編輯器插入）服務 */
    private final PromoPlacementService placementService;
    /** 統計服務 */
    private final PromoStatsService statsService;
    /** 提案資料存取層（列表查詢用） */
    private final PromoProposalRepository proposalRepository;

    /** 注入金鑰驗證與 promo 套件各服務 */
    public AdminPromoController(AdminKeyGuard guard,
                                PromoProposalService proposalService,
                                PromoPlacementService placementService,
                                PromoStatsService statsService,
                                PromoProposalRepository proposalRepository) {
        this.guard = guard;
        this.proposalService = proposalService;
        this.placementService = placementService;
        this.statsService = statsService;
        this.proposalRepository = proposalRepository;
    }

    /** 拒絕請求：拒絕原因（顯示給提案人） */
    public record RejectRequest(String note) {}

    /** 插入版位請求：目標提案 id */
    public record PlacementRequest(Long proposalId) {}

    /**
     * 提案列表；省略 status 時回全部。回傳 {@link PromoProposal} 實體，
     * 前端所需的 id/title/contactName/contactEmail/status/placementQuota/
     * placementUsed/unitCost/reviewNote/createdAt 皆為既有 getter，直接序列化即可，
     * 不另建 DTO（比照 {@code AdminCampaignController#campaigns} 直接回 {@code List<Campaign>}）。
     */
    @GetMapping("/api/admin/promo/proposals")
    public List<PromoProposal> proposals(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @RequestParam(required = false) String status) {
        guard.verify(key);
        if (status == null || status.isBlank()) {
            return proposalRepository.findAll();
        }
        return proposalRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    /** 核准提案：僅 PENDING 可核准 */
    @PostMapping("/api/admin/promo/proposals/{id}/approve")
    public PromoProposal approve(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @PathVariable Long id) {
        guard.verify(key);
        return proposalService.approve(id);
    }

    /** 拒絕提案：僅 PENDING 可拒絕，全額退點，note 落入 reviewNote 供提案人查看 */
    @PostMapping("/api/admin/promo/proposals/{id}/reject")
    public PromoProposal reject(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @PathVariable Long id,
            @RequestBody RejectRequest req) {
        guard.verify(key);
        return proposalService.reject(id, req == null ? null : req.note());
    }

    /** 封存提案：APPROVED／REJECTED 皆可，退還未投放餘額 */
    @PostMapping("/api/admin/promo/proposals/{id}/archive")
    public PromoProposal archive(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @PathVariable Long id) {
        guard.verify(key);
        return proposalService.archive(id);
    }

    /** 編輯器選單：APPROVED 且配額未滿的提案，供插入工商區塊時選擇 */
    @GetMapping("/api/admin/promo/selectable")
    public List<PromoProposal> selectable(
            @RequestHeader(value = KEY_HEADER, required = false) String key) {
        guard.verify(key);
        return proposalRepository.findSelectable();
    }

    /** 於編輯器插入工商區塊：建立 DRAFT 版位並回傳可直接貼入內文的 markdown 片段 */
    @PostMapping("/api/admin/promo/placements")
    public Map<String, Object> createPlacement(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @RequestBody PlacementRequest req) {
        guard.verify(key);
        PromoPlacementService.Snippet snippet = placementService.createPlacement(req.proposalId());
        return Map.of("placementId", snippet.placementId(), "markdown", snippet.markdown());
    }

    /** 成效統計總覽：各提案旗下已定案版位的通道點擊與 EMAIL CTR */
    @GetMapping("/api/admin/promo/stats")
    public List<PromoStatsService.ProposalStats> stats(
            @RequestHeader(value = KEY_HEADER, required = false) String key) {
        guard.verify(key);
        return statsService.overview();
    }

    /**
     * 將驗證失敗（欄位格式、狀態機不符等）轉為 400，訊息直接顯示給前端。
     * 慣例比照 {@code AdminCampaignController} 對輸入錯誤一律回 400 的作法。
     */
    @ExceptionHandler(PromoProposalService.PromoValidationException.class)
    public void handleValidation(PromoProposalService.PromoValidationException e) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
