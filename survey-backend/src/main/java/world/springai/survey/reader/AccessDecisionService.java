package world.springai.survey.reader;

import org.springframework.stereotype.Service;
import world.springai.survey.AppSettingService;
import world.springai.survey.newsletter.Campaign;

import java.time.OffsetDateTime;

/**
 * 閱讀授權的**唯一**決策點。
 *
 * <p>所有 controller 都只呼叫本類，不得自行判斷 tier、VIP 或餘額。授權規則散落在
 * 各個端點是這類系統最常見的腐化方式——改一次規則要改十個地方，漏一個就是漏洞。</p>
 *
 * <p>{@link #decide} 是純函式（不寫入任何東西），寫入閱讀歷史交給
 * {@link #recordAccess}。這與 spec §5.2 把「VIP 時補寫 article_access」寫在
 * 決策規則裡的描述略有不同：行為相同，但決策可獨立測試，且不會因為
 * decide() 被呼叫多次而重複寫入。</p>
 *
 * <p><b>階段 B 範圍</b>：扣點路徑尚未接上，PREMIUM 對非 VIP 一律 PARTIAL。
 * shortfall 已回傳正確值供前端顯示，實際扣點是階段 C 的工作。</p>
 */
@Service
public class AccessDecisionService {

    /** 可讀取的範圍 */
    public enum Access {
        /** 全文（含受限區） */
        FULL,
        /** 只有免費區 */
        PARTIAL
    }

    /** 判定原因，供前端顯示對應的提示與行動按鈕 */
    public enum Reason {
        /** 尚未登入 */
        NOT_LOGGED_IN,
        /** 已登入但未確認訂閱 */
        NOT_SUBSCRIBED,
        /** 基本內容，訂閱者免費 */
        BASIC_OPEN,
        /** 有效 VIP */
        VIP,
        /** 先前已解鎖 */
        ALREADY_UNLOCKED,
        /** 需要點數才能解鎖 */
        NEEDS_CREDITS
    }

    /**
     * 授權判定結果。
     *
     * @param access    可讀取範圍
     * @param reason    判定原因
     * @param shortfall 還差幾點才能解鎖；非 NEEDS_CREDITS 時為 0
     */
    public record Decision(Access access, Reason reason, int shortfall) {}

    /** PREMIUM 解鎖點數的後備預設值；實際值取自 app_setting */
    private static final int DEFAULT_PREMIUM_COST = 10;

    private final ArticleAccessRepository articleAccessRepository;
    private final AppSettingService appSettingService;

    /** 注入解鎖紀錄與參數服務 */
    public AccessDecisionService(ArticleAccessRepository articleAccessRepository,
                                AppSettingService appSettingService) {
        this.articleAccessRepository = articleAccessRepository;
        this.appSettingService = appSettingService;
    }

    /**
     * 判定該讀者對該文章的可讀範圍。純函式，不產生任何寫入。
     *
     * @param reader     讀者；null 表示未登入
     * @param subscribed 是否為已確認訂閱者（由名單中心提供，不從 reader 推導）
     */
    public Decision decide(Reader reader, boolean subscribed, Campaign campaign, OffsetDateTime now) {
        if (reader == null) {
            return new Decision(Access.PARTIAL, Reason.NOT_LOGGED_IN, 0);
        }
        if (!subscribed) {
            return new Decision(Access.PARTIAL, Reason.NOT_SUBSCRIBED, 0);
        }
        if (!campaign.isPremium()) {
            return new Decision(Access.FULL, Reason.BASIC_OPEN, 0);
        }
        if (reader.isActiveVip(now)) {
            return new Decision(Access.FULL, Reason.VIP, 0);
        }
        if (articleAccessRepository.existsByReaderIdAndCampaignId(reader.getId(), campaign.getId())) {
            return new Decision(Access.FULL, Reason.ALREADY_UNLOCKED, 0);
        }

        // 階段 B：不扣點，一律回 PARTIAL 並附上還差幾點（階段 C 會在此接上扣點路徑）
        int cost = resolveCost(campaign);
        int shortfall = Math.max(0, cost - reader.getCredits());
        return new Decision(Access.PARTIAL, Reason.NEEDS_CREDITS, shortfall);
    }

    /**
     * 記錄閱讀歷史：僅在 FULL 且尚無紀錄時寫入，cost 為 0（本階段不扣點）。
     *
     * <p>由 controller 在取得 FULL 決策後呼叫一次。已有紀錄時跳過，
     * 避免撞上 article_access 的 UNIQUE 約束。</p>
     */
    public void recordAccess(Reader reader, Campaign campaign, Decision decision) {
        if (decision.access() != Access.FULL || reader == null) {
            return;
        }
        if (articleAccessRepository.existsByReaderIdAndCampaignId(reader.getId(), campaign.getId())) {
            return;
        }
        articleAccessRepository.save(new ArticleAccess(reader.getId(), campaign.getId(), 0));
    }

    /**
     * 取得該文章的解鎖成本。
     *
     * <p>campaign.creditCost 為 0 時改用參數預設值——PREMIUM 卻成本為 0 理論上
     * 已被資料庫 CHECK 擋掉，但若真的出現，把它當成免費會讓進階內容全面外洩，
     * 所以這裡選擇保守處理。</p>
     */
    private int resolveCost(Campaign campaign) {
        if (campaign.getCreditCost() > 0) {
            return campaign.getCreditCost();
        }
        return appSettingService.getInt(AppSettingService.CREDIT_PREMIUM_COST, DEFAULT_PREMIUM_COST);
    }
}
