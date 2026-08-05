package world.springai.survey.form;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.springai.survey.reader.CreditPolicy;
import world.springai.survey.reader.CreditTxn;
import world.springai.survey.reader.CreditTxnRepository;
import world.springai.survey.reader.Reader;
import world.springai.survey.reader.ReaderRepository;

import java.util.Optional;

/**
 * 一鍵投票發點服務：把投票身分對映到讀者帳號後發放點數。
 *
 * <p><b>與完整填答發點的關係</b>：兩者是各自獨立的 reason
 * （{@code SURVEY_VOTE_REWARD} 與 {@code SURVEY_REWARD}），各有一條
 * partial unique index 防重發，因此同一位讀者同一份問卷可以「投票拿一次、
 * 填完整問卷再拿一次」。形狀刻意比照
 * {@link NewsletterSubmissionService} 的 {@code grantRewardIfEligible}。</p>
 *
 * <p><b>不發點的四種情況</b>：匿名投票、email 找不到對應讀者（訂閱者尚未建帳）、
 * 該問卷已發過投票點數（改票不重發）、後台把獎勵設為 0（關閉發點）。
 * 前三種都照常計票，只是不觸發發點。</p>
 */
@Service
public class SurveyVoteRewardService {

    private static final Logger log = LoggerFactory.getLogger(SurveyVoteRewardService.class);

    private final ReaderRepository readerRepository;
    private final CreditTxnRepository creditTxnRepository;
    private final CreditPolicy creditPolicy;

    /** 注入讀者、帳本與點數規則 */
    public SurveyVoteRewardService(ReaderRepository readerRepository,
                                   CreditTxnRepository creditTxnRepository,
                                   CreditPolicy creditPolicy) {
        this.readerRepository = readerRepository;
        this.creditTxnRepository = creditTxnRepository;
        this.creditPolicy = creditPolicy;
    }

    /**
     * 依投票身分發點；回傳實際發出的點數，未發點回 {@link Optional#empty()}。
     *
     * @param formKey      問卷代號，同時是冪等鍵的一部分
     * @param formTitle    問卷標題，只用於帳本 note
     * @param identityType {@code SurveyVote.IDENTITY_*} 之一
     * @param identityKey  RECIPIENT 為 email、READER 為 readerId 的字串形式
     * @param campaignId   觸發投票的電子報活動，可為 null
     */
    @Transactional
    public Optional<Integer> grantIfEligible(String formKey, String formTitle,
                                             String identityType, String identityKey, Long campaignId) {
        Optional<Long> readerId = resolveReaderId(identityType, identityKey);
        if (readerId.isEmpty()) {
            return Optional.empty();
        }
        long id = readerId.get();
        if (creditTxnRepository.existsByReaderIdAndSurveyFormKeyAndReason(
                id, formKey, CreditTxn.REASON_SURVEY_VOTE_REWARD)) {
            return Optional.empty(); // 改票不重發；唯一索引為併發時的最終防線
        }
        int reward = creditPolicy.surveyVoteReward();
        if (reward <= 0) {
            return Optional.empty(); // 後台關閉投票發點時不留一堆 0 點交易
        }
        String note = "投票「" + formTitle + "」獎勵";
        CreditTxn txn = new CreditTxn(id, reward, CreditTxn.REASON_SURVEY_VOTE_REWARD, campaignId, note);
        txn.setSurveyFormKey(formKey);
        creditTxnRepository.save(txn);
        // 條件式 UPDATE 回 0 列代表讀者列已不存在；帳本已寫入卻靜默放行會讓
        // reader.credits 與 sum(credit_txn) 對不起來——比照 ReferralGrowthService.addCredit
        // 一律拋例外讓交易回滾。
        if (readerRepository.addCredits(id, reward) == 0) {
            throw new IllegalStateException("投票發點失敗：readerId=" + id);
        }
        return Optional.of(reward);
    }

    /**
     * 把投票身分對映到讀者 id。
     *
     * <p>RECIPIENT 以 email 反查（信件收件人未必已建帳）；READER 的 identityKey
     * 本身即 readerId，但仍確認該列存在，避免對已刪除的帳號加點。匿名與病態值
     * 一律回 empty——這裡是 best-effort 路徑的一部分，不該讓格式問題變成例外。</p>
     */
    private Optional<Long> resolveReaderId(String identityType, String identityKey) {
        if (identityKey == null || identityKey.isBlank()) {
            return Optional.empty();
        }
        if (SurveyVote.IDENTITY_RECIPIENT.equals(identityType)) {
            return readerRepository.findByEmailIgnoreCase(identityKey).map(Reader::getId);
        }
        if (SurveyVote.IDENTITY_READER.equals(identityType)) {
            try {
                long id = Long.parseLong(identityKey.trim());
                return readerRepository.findById(id).map(Reader::getId);
            } catch (NumberFormatException e) {
                log.warn("READER 身分的 identityKey 不是數字，跳過發點：{}", identityKey);
                return Optional.empty();
            }
        }
        return Optional.empty(); // ANON 與未知身分型別
    }
}
