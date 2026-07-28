package world.springai.survey.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
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
 * <p><b>本類刻意<u>不</u>帶 {@code @Transactional}</b>（先前是
 * {@code @Transactional(propagation = REQUIRES_NEW)}，V9 之後移除）：
 * 邀請獎勵的冪等改由資料庫的 {@code uq_credit_txn_referral_note} 保證，
 * 重複發放會在 {@code ReferralService.rewardFor} 內以
 * {@code DataIntegrityViolationException} 表現。而 Spring 的交易語意是：
 * 例外一旦觸發，該交易就被標記為 rollback-only，<b>在交易內捕捉並正常回傳</b>
 * 會讓提交時改拋 {@code UnexpectedRollbackException}（與 {@code UnlockService}
 * 同一個陷阱）。所以捕捉點必須在交易邊界之外——本類若自己開交易，本類的
 * {@code catch} 就在邊界<b>之內</b>，等於把陷阱原封不動搬進來。
 * 交易改由 {@code rewardFor} 自己的 {@code @Transactional(REQUIRES_NEW)} 負責，
 * 那個 proxy 就是唯一的邊界，本類站在它外面接例外。
 * 作法與 {@code UnlockController}／{@code UnlockService} 這一對完全一致。</p>
 *
 * <p>移除 {@code REQUIRES_NEW} 不影響原本的保證：它當初的作用是「讓獎勵的兩個
 * 寫入（加餘額、寫帳本）成為獨立的原子單位」，而 {@code rewardFor} 現在自己
 * 帶著 {@code REQUIRES_NEW}，同樣是獨立的原子單位；本類不再是交易邊界而已。</p>
 *
 * <p><b>本設計有一個隱含前提：{@code application.yml} 的
 * {@code spring.jpa.open-in-view: false}</b>（目前已是 false，<b>不可改回 true</b>）。
 * 上面「本類站在交易邊界之外」的推論只涵蓋了 {@code @Transactional}；OSIV 是另一條
 * 會憑空造出共用狀態的路徑：它開著時，{@code OpenEntityManagerInViewInterceptor} 會在
 * 請求一開始就建立一個 {@code EntityManager} 並綁到整個請求的執行緒上，
 * {@code JpaTransactionManager} 之後<b>沿用那個既有的 EntityManager</b> 而不是開新的。
 * 於是 {@code rewardFor} 撞唯一鍵時，flush 失敗留下的髒狀態會留在<b>整個請求共用</b>的
 * persistence context 裡（Hibernate 在 flush 失敗後不保證 session 仍可用），
 * 汙染同一請求後續所有 JPA 操作——而本類的 {@code catch} 會照樣把它當成
 * 「安靜的冪等命中」正常返回，錯誤要到請求更後面才以完全無關的樣貌爆出來。
 * 換句話說：OSIV 一旦打開，本類的捕捉點就不再是真正的邊界外，
 * {@code REQUIRES_NEW} 也救不了（它換的是交易，不是 EntityManager）。</p>
 *
 * <p><b>兩道 catch 的職責（V9 後有變化，不要照舊理解）</b>：本類不再開交易，
 * 所以 {@code rewardFor} 拋出的例外（撞唯一鍵、加點影響 0 列）就在本類的
 * {@code catch} 真正被接住並就地結束，<b>不會</b>再有「本方法返回之後才從外層
 * proxy 冒出 {@code UnexpectedRollbackException}」這回事。
 * {@code SubscriptionController.confirm} 那道仍然必要，但角色從「唯一真正接住的
 * 那層」變成縱深防禦：{@code publishEvent} 是同步的，任何監聽器（現在的或日後
 * 新增的）漏出例外都會讓公開端點回 500，而「不論結果一律回相同的 200」一破，
 * 端點就成了「這個 email 有沒有推薦關係」的探測器。防護不依賴任一端記得。</p>
 */
@Component
public class ReferralRewardListener {

    private static final Logger log = LoggerFactory.getLogger(ReferralRewardListener.class);

    private final ReferralService referralService;
    /** 新版成長引擎；舊單元測試使用單參數建構子時為 null。 */
    private final ReferralGrowthService growthService;

    /** 注入獎勵發放服務 */
    public ReferralRewardListener(ReferralService referralService) {
        this.referralService = referralService;
        this.growthService = null;
    }

    /** 正式環境注入新版成長引擎，保留舊服務供相容測試與補救工具使用。 */
    @org.springframework.beans.factory.annotation.Autowired
    public ReferralRewardListener(ReferralService referralService,
                                  ReferralGrowthService growthService) {
        this.referralService = referralService;
        this.growthService = growthService;
    }

    /**
     * 發放邀請獎勵。同步執行，交易由 {@code ReferralService.rewardFor} 自己開。
     *
     * <p><b>本方法不可加 {@code @Transactional}</b>：下面那個
     * {@code DataIntegrityViolationException} 的捕捉必須在交易邊界之外，
     * 否則提交時會改拋 {@code UnexpectedRollbackException}。理由詳見類別層級說明。</p>
     *
     * <p>兩類例外的處理刻意不同：</p>
     * <ul>
     *   <li>撞上 {@code uq_credit_txn_referral_note}：這是<b>冪等生效</b>，不是失敗。
     *       記 INFO 就好，不可記 ERROR——重複點擊舊確認信（依 spec §5.4，
     *       已確認過的人每次點都會發出事件）會頻繁走到這條路，
     *       記成 ERROR 會讓真正的失敗被雜訊蓋掉。</li>
     *   <li>其餘例外：記 ERROR 供人工補點。此時確認訂閱已經提交，
     *       發獎失敗不該影響已成立的同意紀錄（同意紀錄不可重建，獎勵可後台補）。</li>
     * </ul>
     */
    @EventListener
    public void onSubscriptionConfirmed(SubscriptionConfirmedEvent event) {
        if (growthService != null) {
            try {
                ReferralGrowthService.Outcome outcome = growthService.confirmAndReward(event.email());
                if (outcome != ReferralGrowthService.Outcome.NO_REFERRER) {
                    log.info("確認訂閱後的成長獎勵結果：{}（{}）",
                        outcome, ReferralGrowthService.maskEmail(event.email()));
                }
            } catch (DataIntegrityViolationException e) {
                log.info("確認訂閱後的成長獎勵已處理（{}）",
                    ReferralGrowthService.maskEmail(event.email()));
            } catch (Exception e) {
                log.error("成長獎勵發放失敗（確認訂閱已完成，可由後台審核補發）：{}",
                    ReferralGrowthService.maskEmail(event.email()), e);
            }
            return;
        }
        try {
            ReferralService.RewardOutcome outcome = referralService.rewardFor(event.email());
            // NO_REFERRER 是絕大多數訂閱者的情形，不值得每次都寫一行 log
            if (outcome != ReferralService.RewardOutcome.NO_REFERRER) {
                log.info("確認訂閱後的邀請獎勵處理結果：{}（{}）", outcome, event.email());
            }
        } catch (DataIntegrityViolationException e) {
            // 這位被邀者的獎勵已經發過（含近乎同時的併發重複）。rewardFor 的交易
            // 已整組回滾，餘額與帳本都沒有第二次變動——正是唯一索引存在的目的。
            log.info("確認訂閱後的邀請獎勵處理結果：{}（{}）",
                ReferralService.RewardOutcome.ALREADY_REWARDED, event.email());
        } catch (Exception e) {
            log.error("邀請獎勵發放失敗（確認訂閱已完成，可於後台手動加點）：{}",
                event.email(), e);
        }
    }
}
