package world.springai.survey.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.springai.survey.audience.SurveyResponseRepository;
import world.springai.survey.newsletter.Campaign;

import java.time.OffsetDateTime;

/**
 * 扣點解鎖：本系統唯一會扣除讀者點數的地方。
 *
 * <p><b>為什麼必須是交易性的</b>（與 {@code CampaignService} 相反）：
 * 那裡有無法回滾的 ZSend 寄信副作用，所以刻意不加 {@code @Transactional}；
 * 這裡三個寫入（扣餘額、寫帳本、寫解鎖紀錄）全是本地狀態，
 * 任一失敗都必須整組回滾，否則 {@code reader.credits} 與
 * {@code credit_txn} 的總和會不一致——而「餘額永遠可由帳本重算稽核」
 * 是本系統的核心不變式。</p>
 *
 * <p><b>三個併發防線</b>：
 * ① {@code article_access} 的 UNIQUE(reader_id, campaign_id)；
 * ② {@link ReaderRepository#deductCredits} 的 {@code WHERE credits >= :cost}；
 * ③ <b>扣款先於寫入解鎖紀錄</b>——若插入撞 UNIQUE，扣款隨交易一起回滾。
 * 反過來的順序在插入成功、扣款失敗時會留下「有解鎖紀錄但沒扣點」的
 * 永久免費解鎖，而該紀錄同時是 ALREADY_UNLOCKED 的判斷來源，無法自我修復。</p>
 */
@Service
public class UnlockService {

    private static final Logger log = LoggerFactory.getLogger(UnlockService.class);

    /** 解鎖結果 */
    public enum Outcome {
        /** 本次成功解鎖並扣點 */
        UNLOCKED,
        /** 先前已解鎖，未扣點 */
        ALREADY_UNLOCKED,
        /** 餘額不足，未扣點 */
        INSUFFICIENT_CREDITS
    }

    /**
     * 本服務刻意 fail-closed 的出口專用例外型別（未發布／非 PREMIUM／讀者不存在／
     * 併發扣款影響 0 列）。
     *
     * <p>與泛用的 {@link IllegalStateException} 區分開，是為了讓
     * {@link UnlockController} 只捕捉「這幾個明確已知、未扣點」的狀態、
     * 轉成 409，而不會把 JPA／交易基礎設施或日後誤用某個 API 拋出的
     * 其他 {@code IllegalStateException} 一併吞成 409——那類才是真正的
     * 伺服器故障，必須讓它照常變成 500 才會被監控看到。</p>
     */
    public static class UnlockUnavailableException extends IllegalStateException {
        public UnlockUnavailableException(String message) {
            super(message);
        }
    }

    /**
     * 解鎖結果。
     *
     * @param outcome 結果
     * @param cost    該文章的解鎖成本
     * @param credits <b>操作後</b>的餘額（未扣點時即為目前餘額）
     */
    public record Result(Outcome outcome, int cost, int credits) {}

    private final ReaderRepository readerRepository;
    private final ArticleAccessRepository articleAccessRepository;
    private final CreditTxnRepository creditTxnRepository;
    private final SurveyResponseRepository surveyResponseRepository;
    private final CreditPolicy creditPolicy;

    /** 注入讀者、解鎖紀錄、帳本、名單中心與點數參數 */
    public UnlockService(ReaderRepository readerRepository,
                         ArticleAccessRepository articleAccessRepository,
                         CreditTxnRepository creditTxnRepository,
                         SurveyResponseRepository surveyResponseRepository,
                         CreditPolicy creditPolicy) {
        this.readerRepository = readerRepository;
        this.articleAccessRepository = articleAccessRepository;
        this.creditTxnRepository = creditTxnRepository;
        this.surveyResponseRepository = surveyResponseRepository;
        this.creditPolicy = creditPolicy;
    }

    /**
     * 以點數解鎖一篇進階文章。
     *
     * <p><b>參數刻意收 {@code readerId} 而非 {@code Reader}</b>：呼叫端的 Reader
     * 來自 session cookie 解析，餘額可能是幾分鐘前的快照。餘額判斷必須用
     * 交易內的即時值，用簽章保證呼叫端無法把舊餘額餵進來——介面設計層面
     * 就杜絕了「傳入舊餘額」這條路，不需要額外測試去證明呼叫端做不到
     * 一件簽章本身就不允許的事。</p>
     *
     * <p><b>回傳的 {@code Result.credits} 一律是扣款後重新讀取的權威值</b>，
     * 不是用扣款前的快照做記憶體算術（{@code reader.getCredits() - cost}）。
     * 兩者在單機無併發時結果相同，但 {@code findById} 與
     * {@link ReaderRepository#deductCredits} 之間若有另一筆交易改動了
     * 同一讀者的餘額（例如同時到達的推薦獎勵加點、另一篇文章的併發解鎖），
     * 記憶體算術就會回傳與資料庫不符的數字——而 spec §5.11 明訂規則頁、
     * {@code /r/me} 與 paywall 提示三處顯示的點數必須與實際扣點同源。</p>
     *
     * <p><b>UNIQUE 撞擊不在此捕捉</b>，而是往外拋給 controller 處理。
     * 原因是 Spring 的交易語意：一旦 {@code saveAndFlush} 觸發
     * {@code DataIntegrityViolationException}，交易已被標記 rollback-only，
     * 在本方法內捕捉並正常回傳會讓 commit 改拋
     * {@code UnexpectedRollbackException}——呼叫端收到一個看起來毫無關聯的錯誤。
     * 捕捉必須發生在交易邊界<b>之外</b>。</p>
     *
     * @throws UnlockUnavailableException      讀者不存在、文章未發布或非 PREMIUM、併發扣款失敗
     * @throws org.springframework.dao.DataIntegrityViolationException 併發解鎖撞上 UNIQUE
     */
    @Transactional
    public Result unlock(Long readerId, Campaign campaign, OffsetDateTime now) {
        // 扣點是不可逆的寫入，不完全信任呼叫端已做過授權判斷。
        // 草稿被解鎖 → 讀者付了點數卻看到未完成的內容，而點數已經扣掉。
        if (!campaign.isPublished()) {
            throw new UnlockUnavailableException("文章尚未發布，不可解鎖：id=" + campaign.getId());
        }
        // 只有精確等於 PREMIUM 才允許扣點。fail-closed 方向：tier 打錯字時
        // 寧可拒絕解鎖（讀者仍看得到免費區、能回報問題），也不要對一篇
        // 判斷不明的文章扣點。BASIC 對訂閱者本來就免費，扣點是純粹的損失。
        if (!Campaign.TIER_PREMIUM.equals(campaign.getTier())) {
            throw new UnlockUnavailableException(
                "只有 PREMIUM 文章需要解鎖，tier=" + campaign.getTier());
        }

        Reader reader = readerRepository.findById(readerId)
            .orElseThrow(() -> new UnlockUnavailableException("讀者不存在：id=" + readerId));

        int cost = creditPolicy.costOf(campaign);

        // 已解鎖：不寫入任何東西就回傳，交易內無任何變動，可安全正常返回
        if (articleAccessRepository.existsByReaderIdAndCampaignId(readerId, campaign.getId())) {
            return new Result(Outcome.ALREADY_UNLOCKED, cost, reader.getCredits());
        }

        // 餘額不足：同樣沒有寫入，可安全正常返回
        if (reader.getCredits() < cost) {
            return new Result(Outcome.INSUFFICIENT_CREDITS, cost, reader.getCredits());
        }

        // 防線②：條件式扣款。回 0 列代表檢查與扣款之間有另一筆交易扣走了點數。
        int deducted = readerRepository.deductCredits(readerId, cost);
        if (deducted == 0) {
            // 不可回報成 INSUFFICIENT_CREDITS：餘額檢查方才已通過，
            // 這是真正的併發衝突，靜默處理會把問題藏起來。
            throw new UnlockUnavailableException(
                "扣點失敗（併發衝突）：reader=" + readerId + " cost=" + cost);
        }

        // 防線①③：扣款成功後才寫解鎖紀錄；撞 UNIQUE 時扣款隨交易回滾
        articleAccessRepository.saveAndFlush(new ArticleAccess(readerId, campaign.getId(), cost));
        creditTxnRepository.save(new CreditTxn(
            readerId, -cost, CreditTxn.REASON_READ, campaign.getId(), campaign.getSubject()));

        // 解鎖是高可靠的參與度訊號（spec §5.10）
        surveyResponseRepository.touchEngagement(reader.getEmail(), now);

        // 扣款後重新讀取權威餘額，而不是用扣款前的快照做 reader.getCredits() - cost。
        // 兩者在單機無併發時相同，但 findById 與 deductCredits 之間若有另一筆交易
        // 改動同一讀者的餘額（同時到達的推薦獎勵加點、另一篇文章的併發解鎖），
        // 記憶體算術就會回傳與資料庫不符的數字，讓頁面顯示的餘額與實際不一致
        // （spec §5.11 明訂三處顯示的點數必須與實際扣點同源）。
        //
        // deductCredits 帶 clearAutomatically = true，所以這次 findById 會真的
        // 重新查詢；少了那個設定，一級快取會回傳同一個舊物件而使本段失效。
        int remaining = readerRepository.findById(readerId)
            .map(Reader::getCredits)
            .orElseThrow(() -> new IllegalStateException("扣款後讀不到讀者：id=" + readerId));

        log.info("讀者 id={} 以 {} 點解鎖文章 id={}", readerId, cost, campaign.getId());
        return new Result(Outcome.UNLOCKED, cost, remaining);
    }
}
