package world.springai.survey.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import world.springai.survey.audience.SurveyResponse;
import world.springai.survey.audience.SurveyResponseRepository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 邀請歸因與獎勵發放。
 *
 * <p>獎勵只在「被邀者真的點了自己信箱裡的確認信」時發放（spec §5.4），
 * 因此本服務由 {@link ReferralRewardListener} 在確認訂閱事件後呼叫，
 * 而不是在訂閱時就發——填假 email 拿不到點數。這也是第一版不設邀請人數
 * 上限的理由：濫用面很窄。</p>
 */
@Service
public class ReferralService {

    private static final Logger log = LoggerFactory.getLogger(ReferralService.class);

    /**
     * 邀請歸因在 survey_response.answers 內的鍵名。
     *
     * <p><b>與 {@code form.SurveyController.REF_KEY} 必須永遠一致。</b>
     * 刻意不 import 那個常數：{@code reader} 依賴 {@code form} 會讓上層
     * package 互相糾纏（spec §3 只授權 reader → audience/mail/newsletter）。
     * 這是「重複一個字串常數」與「多一條跨 package 依賴」的取捨，選擇前者，
     * 並以本註解與兩處各自的測試守住一致性。</p>
     */
    static final String REF_KEY = "_ref";

    /** 發放結果 */
    public enum RewardOutcome {
        /** 已發放獎勵 */
        REWARDED,
        /** 沒有推薦人（無 _ref、名單查無此人，或獎勵設為 0）——絕大多數訂閱者走這條 */
        NO_REFERRER,
        /**
         * 這位被邀者的獎勵已發過。
         *
         * <p><b>本值不由 {@link #rewardFor} 回傳</b>：冪等由資料庫的
         * {@code uq_credit_txn_referral_note} 保證，重複發放會以
         * {@code DataIntegrityViolationException} 表現，而該例外必須在交易邊界
         * 之外才能安全捕捉（見 {@link #rewardFor} 的說明）。因此這個語意由
         * 呼叫端在捕捉到該例外時使用，見 {@link ReferralRewardListener}。</p>
         */
        ALREADY_REWARDED,
        /** 推薦碼指向自己 */
        SELF_INVITE,
        /** 推薦碼查不到對應讀者（亂改連結） */
        REFERRER_NOT_FOUND
    }

    /**
     * 邀請成效。
     *
     * @param invitedCount  成功邀請人數，來源是 {@code reader.referred_by}
     * @param earnedCredits 累計獲得點數，來源是 {@code credit_txn} 的 REFERRAL 列
     *                      （兩者為何不同源、何時會有落差，見 {@link #stats}）
     */
    public record ReferralStats(int invitedCount, int earnedCredits) {}

    private final SurveyResponseRepository surveyResponseRepository;
    private final ReaderRepository readerRepository;
    private final CreditTxnRepository creditTxnRepository;
    private final CreditPolicy creditPolicy;

    /** 注入名單中心、讀者、帳本與點數參數 */
    public ReferralService(SurveyResponseRepository surveyResponseRepository,
                           ReaderRepository readerRepository,
                           CreditTxnRepository creditTxnRepository,
                           CreditPolicy creditPolicy) {
        this.surveyResponseRepository = surveyResponseRepository;
        this.readerRepository = readerRepository;
        this.creditTxnRepository = creditTxnRepository;
        this.creditPolicy = creditPolicy;
    }

    /**
     * 為某位「剛完成確認訂閱」的被邀者發放推薦獎勵。
     *
     * <p><b>冪等由資料庫保證</b>：{@code uq_credit_txn_referral_note}
     * （V9，{@code UNIQUE (note) WHERE reason = 'REFERRAL'}）是唯一的防線。
     * 本方法<b>刻意不先查「有沒有發過」再寫</b>——那是 check-then-act，
     * 兩個近乎同時的確認請求（Outlook Safe Links／Gmail 圖片代理的背景 GET
     * 與讀者本人的點擊）會各自判讀為「未發過」而重複發獎。
     * 現在的流程是「直接寫、撞了才知道」：第二筆會被資料庫拒絕。</p>
     *
     * <p><b>為什麼連「快取式的前置檢查」都不留</b>：留著它會讓真正的防線
     * （UNIQUE 索引）在絕大多數情境下永遠不被執行到，等於把唯一有效的機制
     * 放在測不到也跑不到的路徑上——本專案已經因為「mock 掉 repository 使
     * 資料庫層防線從未被真的執行」踩過同一種坑（見 {@code UnlockConstraintTest}）。
     * 代價是重複點擊舊確認信時會白做一次 SELECT + 一次回滾的 INSERT，
     * 那是無害且罕見的成本。</p>
     *
     * <p><b>{@code REQUIRES_NEW} 與捕捉位置</b>：撞上唯一鍵時 Spring 會把交易
     * 標記為 rollback-only，因此<b>絕不可在本方法內捕捉
     * {@code DataIntegrityViolationException} 並正常回傳</b>——提交時會改拋
     * {@code UnexpectedRollbackException}（與 {@link UnlockService#unlock} 同一個
     * 陷阱）。捕捉必須發生在交易邊界之外，也就是呼叫端
     * （{@link ReferralRewardListener}，它刻意不帶 {@code @Transactional}）。
     * 本方法用 {@code REQUIRES_NEW} 而非預設的 {@code REQUIRED}，是為了讓
     * 「交易邊界就在本方法的 proxy 上」這件事不受呼叫端影響：即使日後某個
     * 呼叫端自己開了交易，獎勵仍是獨立的原子單位，撞鍵回滾也不會污染對方的交易。</p>
     *
     * <p><b>核心不變式</b>：{@code reader.credits} 恆等於 {@code credit_txn} 的總和。
     * 加點與寫帳本都在本交易內，任一失敗（撞唯一鍵、加點影響 0 列）都整組回滾，
     * 不會出現「加了點卻沒寫帳本」或「寫了帳本卻沒加點」。</p>
     *
     * @param inviteeEmail 被邀者 email（呼叫端已正規化，此處仍再正規化一次以防直接呼叫）
     * @throws org.springframework.dao.DataIntegrityViolationException 這位被邀者的獎勵已發過
     *         （含近乎同時的併發重複），呼叫端應視為 {@link RewardOutcome#ALREADY_REWARDED}
     * @throws IllegalStateException 加點影響 0 列（推薦人那一列不存在）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RewardOutcome rewardFor(String inviteeEmail) {
        String invitee = normalize(inviteeEmail);

        Optional<String> code = surveyResponseRepository
            .findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(invitee)
            .flatMap(ReferralService::referralCodeOf);
        if (code.isEmpty()) {
            return RewardOutcome.NO_REFERRER;
        }

        Optional<Reader> found = readerRepository.findByReferralCode(code.get());
        if (found.isEmpty()) {
            log.info("推薦碼 {} 查不到對應讀者，不發獎", code.get());
            return RewardOutcome.REFERRER_NOT_FOUND;
        }
        Reader referrer = found.get();

        // 自我邀請：用自己的碼訂閱自己的 email。冪等鍵雖然會擋住第二次，
        // 但第一次仍會發獎，所以必須明確拒絕。
        if (normalize(referrer.getEmail()).equals(invitee)) {
            log.info("推薦碼 {} 指向自己，不發獎", code.get());
            return RewardOutcome.SELF_INVITE;
        }

        int reward = creditPolicy.referralReward();
        // 後台可把獎勵調成 0（關閉此機制）。此時不寫帳本也不佔用冪等鍵——
        // 否則日後把獎勵調回 100，這位被邀者的獎勵就永遠拿不到了。
        // 邀請「人數」不受這條影響：那個數字來自 reader.referred_by（見 stats）。
        if (reward <= 0) {
            log.info("邀請獎勵設定為 {}，不發獎", reward);
            return RewardOutcome.NO_REFERRER;
        }

        // 先寫帳本、後加點，且用 saveAndFlush，這三件事都是刻意的：
        // ① 帳本列就是帶唯一索引的那一列，先寫它可以在「這位被邀者已經發過獎」時
        //    連推薦人的餘額都還沒動過，也不必為了回滾而先取得該列的鎖。
        // ② saveAndFlush 讓約束違反在這一行就以 DataIntegrityViolationException
        //    浮現（經 repository 的例外轉譯）。若用 save()，違反會延到提交時才發生，
        //    那時已離開 repository 的轉譯範圍，呼叫端收到的會是型別完全不同的
        //    JPA 例外，交易外的捕捉就接不到了。
        // ③ 兩個寫入在同一交易內，任一失敗一起回滾——餘額與帳本永遠同進同出。
        creditTxnRepository.saveAndFlush(new CreditTxn(
            referrer.getId(), reward, CreditTxn.REASON_REFERRAL, null, invitee));

        int updated = readerRepository.addCredits(referrer.getId(), reward);
        if (updated == 0) {
            // 推薦人那一列不存在。若靜默成功，帳本會多一筆沒有對應餘額變動的紀錄，
            // 而 reader.credits 是 credit_txn 的物化總和——不變式會破。
            // 拋出例外讓上面剛寫入的帳本列隨交易一起回滾。
            throw new IllegalStateException(
                "加點失敗：推薦人 id=" + referrer.getId() + " 不存在");
        }

        log.info("邀請獎勵已發放：推薦人 id={} +{} 點（被邀者 {}）",
            referrer.getId(), reward, invitee);
        return RewardOutcome.REWARDED;
    }

    /** 從名單資料取出推薦碼；無 answers、無 _ref 或為空白時回 empty */
    public static Optional<String> referralCodeOf(SurveyResponse response) {
        if (response.getAnswers() == null) {
            return Optional.empty();
        }
        Object raw = response.getAnswers().get(REF_KEY);
        if (raw == null) {
            return Optional.empty();
        }
        String code = String.valueOf(raw).trim();
        return code.isEmpty() ? Optional.empty() : Optional.of(code);
    }

    /**
     * 某位推薦人的邀請成效：成功邀請人數與累計獲得點數。
     *
     * <p><b>兩個數字刻意來自兩個不同的來源</b>：</p>
     * <ul>
     *   <li><b>人數</b>來自 {@code reader.referred_by}（{@link ReaderRepository#countByReferredBy}）
     *       ——「誰邀請了我」的長期歸因紀錄，與獎勵是否發放無關。</li>
     *   <li><b>點數</b>來自 {@code credit_txn} 的 REFERRAL 列加總——帳本是餘額的稽核來源，
     *       這個數字必須與讀者實際拿到的點數同源，不可用「人數 × 目前獎勵金額」推算
     *       （獎勵金額會被後台調整，推算值會與歷史實付金額不符）。</li>
     * </ul>
     *
     * <p><b>兩者會有合理的落差，且兩個方向都可能</b>：</p>
     * <ul>
     *   <li><b>人數增加而點數不變</b>：後台把邀請獎勵設為 0（合法的營運設定）期間，
     *       {@link #rewardFor} 完全不寫帳本（刻意，避免占用冪等鍵），但被邀者首次登入時
     *       {@code referred_by} 照樣寫入。這正是本次修正的目的——在此之前人數也數帳本筆數，
     *       於是關閉獎勵期間朋友明明完成了訂閱，邀請人的頁面卻毫無反應。</li>
     *   <li><b>點數增加而人數不變</b>：被邀者<b>早就有 reader 帳戶</b>時，
     *       {@code referred_by} 只在建帳當下寫入（見
     *       {@code ReaderAccountService#createWithSignupGrant}），既有帳戶不會被回填，
     *       但 {@link #rewardFor} 仍會發獎。這種人本來就不是「新帶進來的讀者」，
     *       不計入人數是可接受的；帳務仍然正確。</li>
     * </ul>
     *
     * <p><b>時序</b>：人數在被邀者<b>首次登入</b>時才成長，不是在他確認訂閱時
     * ——{@code referred_by} 由建帳流程寫入。對讀者的文案必須反映這件事
     * （見 {@code ReaderPortalController#rewardIntro}）。</p>
     */
    public ReferralStats stats(Long referrerId) {
        // countByReferredBy 回 long。用 Math.min 夾住而不是裸轉型：裸轉型溢位會變成
        // 負數，頁面就會顯示負的邀請人數（雖然單一推薦人不可能真的超過 21 億人）。
        int invited = (int) Math.min(
            readerRepository.countByReferredBy(referrerId), Integer.MAX_VALUE);
        List<CreditTxn> rewards = creditTxnRepository
            .findByReaderIdAndReasonOrderByCreatedAtDesc(referrerId, CreditTxn.REASON_REFERRAL);
        int earned = rewards.stream().mapToInt(CreditTxn::getDelta).sum();
        return new ReferralStats(invited, earned);
    }

    /**
     * email 正規化：去前後空白並轉小寫。
     *
     * <p>{@code Locale.ROOT} 不可省略，而且這裡比別處更不能省：此處的值會成為
     * <b>發獎冪等鍵</b>（{@code credit_txn.note}）。土耳其語系（tr-TR）下無參數的
     * {@code toLowerCase()} 會把 {@code I} 轉成 {@code ı}，正規化結果與
     * {@code ReaderAccountService#normalize} 及 {@code AdminReaderService#normalizeEmail}
     * 這兩處（都已帶 {@code Locale.ROOT}）不一致——同一個 email 會算出兩把不同的
     * 冪等鍵，於是資料庫層的 {@code uq_credit_txn_referral_note} 也擋不住
     * （對它而言那是兩個不同的 note 值），獎勵重複發放。
     * 換句話說，資料庫的唯一索引只保證「同一個字串不重複」，
     * 「同一個人算出同一個字串」仍然是本方法的責任。</p>
     */
    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
