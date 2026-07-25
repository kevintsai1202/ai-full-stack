package world.springai.survey.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.springai.survey.audience.SurveyResponse;
import world.springai.survey.audience.SurveyResponseRepository;

import java.util.List;
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
        /** 這位被邀者的獎勵已發過 */
        ALREADY_REWARDED,
        /** 推薦碼指向自己 */
        SELF_INVITE,
        /** 推薦碼查不到對應讀者（亂改連結） */
        REFERRER_NOT_FOUND
    }

    /** 邀請成效：成功邀請人數與累計獲得點數 */
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
     * <p>冪等：以 {@code (reason='REFERRAL', note=被邀者 email)} 判斷是否已發過，
     * 所以重複點確認信、退訂後再確認，都不會重複發獎。</p>
     *
     * @param inviteeEmail 被邀者 email（呼叫端已正規化，此處仍再正規化一次以防直接呼叫）
     */
    @Transactional
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

        // 冪等檢查：note 存的正是被邀者 email
        if (creditTxnRepository.existsByReasonAndNote(CreditTxn.REASON_REFERRAL, invitee)) {
            return RewardOutcome.ALREADY_REWARDED;
        }

        int reward = creditPolicy.referralReward();
        // 後台可把獎勵調成 0（關閉此機制）。此時不寫帳本也不佔用冪等鍵——
        // 否則日後把獎勵調回 100，這位被邀者的獎勵就永遠拿不到了。
        if (reward <= 0) {
            log.info("邀請獎勵設定為 {}，不發獎", reward);
            return RewardOutcome.NO_REFERRER;
        }

        int updated = readerRepository.addCredits(referrer.getId(), reward);
        if (updated == 0) {
            // 推薦人那一列不存在。若靜默成功，帳本會多一筆沒有對應餘額變動的紀錄，
            // 而 reader.credits 是 credit_txn 的物化總和——不變式會破。
            throw new IllegalStateException(
                "加點失敗：推薦人 id=" + referrer.getId() + " 不存在");
        }
        creditTxnRepository.save(new CreditTxn(
            referrer.getId(), reward, CreditTxn.REASON_REFERRAL, null, invitee));

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

    /** 某位推薦人的邀請成效：成功邀請人數與累計獲得點數 */
    public ReferralStats stats(Long referrerId) {
        List<CreditTxn> rewards = creditTxnRepository
            .findByReaderIdAndReasonOrderByCreatedAtDesc(referrerId, CreditTxn.REASON_REFERRAL);
        int earned = rewards.stream().mapToInt(CreditTxn::getDelta).sum();
        return new ReferralStats(rewards.size(), earned);
    }

    /** email 正規化：去前後空白並轉小寫 */
    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
