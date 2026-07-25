package world.springai.survey.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import world.springai.survey.audience.SubscriptionConfirmedEvent;

/**
 * 確認訂閱後發放推薦獎勵。
 *
 * <p><b>為什麼是監聽器而不是直接呼叫</b>：確認訂閱的端點在 {@code audience}，
 * 而 spec §3 規定 {@code audience} 不得依賴 {@code reader}。事件讓依賴方向
 * 保持 {@code reader → audience}（本類 import audience 的事件型別），
 * 拆解線不被破壞。</p>
 *
 * <p><b>為什麼是普通的 {@code @EventListener} 而不是
 * {@code @TransactionalEventListener(AFTER_COMMIT)}</b>：因為
 * <b>發布端沒有交易</b>。{@code SubscriptionController.confirm} 沒有
 * {@code @Transactional}，它呼叫的 {@code confirmByEmail} 與
 * {@code touchEngagement} 是 repository 上各自帶 {@code @Transactional} 的方法，
 * 各自立即提交。所以 {@code publishEvent} 被呼叫時<b>沒有進行中的交易</b>，
 * 而 {@code @TransactionalEventListener} 在沒有交易時<b>預設完全不觸發</b>
 * （也不報錯）。用它的結果是：獎勵永遠不會發放，日誌乾淨，測試因為
 * 監聽器被 mock 掉而全綠——最惡劣的靜默失效。</p>
 *
 * <p><b>不需要「同一交易」也能保住優先順序</b>：確認訂閱在 publish 之前
 * 就已經提交（repository 方法自帶交易），所以「確認成功但發獎失敗」時，
 * 同意紀錄已經落地。這正是我們要的方向——確認訂閱是<b>不可重建的同意紀錄</b>
 * （讀者親手點了信裡的連結，沒記下來只能重新徵求同意），而推薦獎勵是
 * 可補救的（後台能手動加點）。spec §5.4 原本寫「同一交易內」，
 * 那會讓可補救的失敗回滾掉不可補救的資產，方向是錯的。</p>
 *
 * <p>{@code REQUIRES_NEW} 讓獎勵的三個寫入（加餘額、寫帳本）成為一個
 * 獨立的原子單位——不是為了與確認訂閱隔離（本來就已經隔離），而是為了
 * 讓獎勵本身不會只寫一半。</p>
 *
 * <p>例外在此與發布端<b>雙重</b>吞掉：本類吞是為了記錄可讀的 ERROR 供人工補點；
 * {@code SubscriptionController} 也吞是因為 {@code publishEvent} 同步且例外會
 * 往上拋，若變成 500，「不論結果一律回相同的 200」這條性質就破了，端點會變成
 * 「這個 email 有沒有推薦關係」的探測器。防護不依賴任一端記得。</p>
 */
@Component
public class ReferralRewardListener {

    private static final Logger log = LoggerFactory.getLogger(ReferralRewardListener.class);

    private final ReferralService referralService;

    /** 注入獎勵發放服務 */
    public ReferralRewardListener(ReferralService referralService) {
        this.referralService = referralService;
    }

    /**
     * 發放邀請獎勵。同步執行，但在自己的交易內。
     *
     * <p>例外一律在此吞掉並記為 ERROR：此時確認訂閱已經提交，讓例外往上拋
     * 會讓公開端點回 500 而破壞「不洩漏名單」的性質。</p>
     */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSubscriptionConfirmed(SubscriptionConfirmedEvent event) {
        try {
            ReferralService.RewardOutcome outcome = referralService.rewardFor(event.email());
            // NO_REFERRER 是絕大多數訂閱者的情形，不值得每次都寫一行 log
            if (outcome != ReferralService.RewardOutcome.NO_REFERRER) {
                log.info("確認訂閱後的邀請獎勵處理結果：{}（{}）", outcome, event.email());
            }
        } catch (Exception e) {
            log.error("邀請獎勵發放失敗（確認訂閱已完成，可於後台手動加點）：{}",
                event.email(), e);
        }
    }
}
