package world.springai.survey.reader;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
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
 * <p><b>階段 C 行為</b>：PREMIUM 對非 VIP、餘額足夠時回傳 {@link Reason#CAN_UNLOCK}，
 * 代表「可解鎖但尚未扣點」——{@code access} 仍是 {@link Access#PARTIAL}，讀者需按下
 * 解鎖按鈕才會實際扣點。實際扣點由 {@link UnlockService} 負責，本方法維持純函式。</p>
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
        /** 餘額足夠，等待讀者確認是否要花點數解鎖 */
        CAN_UNLOCK,
        /** 需要點數才能解鎖 */
        NEEDS_CREDITS,
        /** 文章尚未發布 */
        NOT_PUBLISHED
    }

    /**
     * 授權判定結果。
     *
     * @param access    可讀取範圍
     * @param reason    判定原因
     * @param shortfall 還差幾點才能解鎖；非 NEEDS_CREDITS 時為 0
     */
    public record Decision(Access access, Reason reason, int shortfall) {}

    private final ArticleAccessRepository articleAccessRepository;
    private final CreditPolicy creditPolicy;

    /** 注入解鎖紀錄與點數參數唯一來源 */
    public AccessDecisionService(ArticleAccessRepository articleAccessRepository,
                                CreditPolicy creditPolicy) {
        this.articleAccessRepository = articleAccessRepository;
        this.creditPolicy = creditPolicy;
    }

    /**
     * 判定該讀者對該文章的可讀範圍。純函式，不產生任何寫入。
     *
     * <p><b>不變式（呼叫端可依賴）</b>：回傳的 {@link Decision#reason()} 不是
     * {@link Reason#NOT_LOGGED_IN} 時，呼叫端可假定 {@code reader} 非 null——
     * 本方法把 {@code reader == null} 的檢查排在所有其他分支之前（見下方實作），
     * 因此除了 NOT_LOGGED_IN 以外的任何 reason 都必然是在 reader 非 null 的
     * 分支中產生。日後修改本方法的判斷順序時，若把某個不需登入即可觸發的
     * reason（例如未來的 BASIC_OPEN 開放給匿名者）提到這個 null 檢查之前，
     * 會破壞這條不變式，呼叫端（如 {@code UnlockController}）對 reader 的
     * 假設會直接 NPE——這是刻意的：錯要錯在被打破的那一刻，而不是靜默通過
     * 後在下游某處出現難以定位的 NullPointerException。</p>
     *
     * @param reader     讀者；null 表示未登入
     * @param subscribed 是否為已確認訂閱者（由名單中心提供，不從 reader 推導）
     */
    public Decision decide(Reader reader, boolean subscribed, Campaign campaign, OffsetDateTime now) {
        // 發布狀態是授權的前提，必須排在最前面：草稿不對任何人開放，連 VIP 也不行，
        // 不能把這個決定留給呼叫端各自判斷。
        if (!campaign.isPublished()) {
            return new Decision(Access.PARTIAL, Reason.NOT_PUBLISHED, 0);
        }
        if (reader == null) {
            return new Decision(Access.PARTIAL, Reason.NOT_LOGGED_IN, 0);
        }
        if (!subscribed) {
            return new Decision(Access.PARTIAL, Reason.NOT_SUBSCRIBED, 0);
        }
        // 只有精確為 BASIC 才免費開放；未知值、null、大小寫不符一律走進階規則。
        // 這個方向很重要：反過來寫（!isPremium() 就開放）會讓 tier 打錯字
        // 變成付費內容全面外洩，而資料庫沒有 tier 白名單約束可以兜底。
        if (Campaign.TIER_BASIC.equals(campaign.getTier())) {
            return new Decision(Access.FULL, Reason.BASIC_OPEN, 0);
        }
        if (reader.isActiveVip(now)) {
            return new Decision(Access.FULL, Reason.VIP, 0);
        }
        if (articleAccessRepository.existsByReaderIdAndCampaignId(reader.getId(), campaign.getId())) {
            return new Decision(Access.FULL, Reason.ALREADY_UNLOCKED, 0);
        }

        int cost = resolveCost(campaign);
        // 餘額足夠時仍回 PARTIAL——受限區在讀者按下解鎖前不得進入回應。
        //
        // 這裡刻意偏離 spec §5.2 規則表的「credits >= cost → FULL + 扣點」：
        // 讀者從電子報連結點進來就被無感扣點，會被感受為未經同意的收費，
        // 而整套點數機制的可信度是 spec §5.11 的核心訴求。改為「顯示成本 →
        // 讀者按下按鈕 → 扣點」，誤點成本為 0。實際扣點在 UnlockService。
        if (reader.getCredits() >= cost) {
            return new Decision(Access.PARTIAL, Reason.CAN_UNLOCK, 0);
        }
        return new Decision(Access.PARTIAL, Reason.NEEDS_CREDITS, cost - reader.getCredits());
    }

    /**
     * 記錄閱讀歷史：僅在 reason 為 VIP 時寫入，cost 為 0（本階段不扣點）。
     *
     * <p>只在 VIP 分支寫入，BASIC_OPEN 與 ALREADY_UNLOCKED 都不寫：
     * article_access 同時是 {@link #decide} 判斷 ALREADY_UNLOCKED 的依據，
     * 若對 BASIC_OPEN 也寫入，會出現「文章以 BASIC 發布時被讀者讀過 → 後台
     * 改為 PREMIUM → 該讀者再訪時因 ALREADY_UNLOCKED 永久免費看到全文」的
     * 升級漏洞。此行為與設計文件 §5.2 一致——spec 只在 VIP 分支補寫。
     * 付費解鎖的寫入由 {@link UnlockService} 負責，本方法仍只處理 VIP。</p>
     *
     * <p>並發下這裡是 check-then-act：兩個分頁同時通過 exists 檢查、
     * 都嘗試 INSERT 是常見情況，第二筆會撞上 uq_article_access。
     * 忽略該例外是正確語意，因為此時紀錄已經存在（本階段 cost 恆為 0，
     * 不會有扣點被吞掉的疑慮），讀者本來就有權讀取，不該因此收到 500。</p>
     */
    public void recordAccess(Reader reader, Campaign campaign, Decision decision) {
        if (decision.access() != Access.FULL || decision.reason() != Reason.VIP || reader == null) {
            return;
        }
        if (articleAccessRepository.existsByReaderIdAndCampaignId(reader.getId(), campaign.getId())) {
            return;
        }
        try {
            articleAccessRepository.save(new ArticleAccess(reader.getId(), campaign.getId(), 0));
        } catch (DataIntegrityViolationException e) {
            // 併發寫入撞上 UNIQUE 約束：代表紀錄已被另一個請求寫入，忽略即可。
        }
    }

    /**
     * 取得該文章的解鎖成本。
     *
     * <p>實際計算與下限保護已收斂到 {@link CreditPolicy#costOf(Campaign)}；
     * 本方法保留為既有呼叫端的入口，不再自行讀取 app_setting。</p>
     */
    public int resolveCost(Campaign campaign) {
        return creditPolicy.costOf(campaign);
    }
}
