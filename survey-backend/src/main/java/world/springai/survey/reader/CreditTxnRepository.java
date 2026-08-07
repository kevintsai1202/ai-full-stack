package world.springai.survey.reader;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 點數帳本資料存取層；只新增不修改，故無 update 方法 */
public interface CreditTxnRepository extends JpaRepository<CreditTxn, Long> {

    /** 某讀者的交易明細，新到舊（客訴對帳使用；無筆數上限，勿用於頁面渲染） */
    List<CreditTxn> findByReaderIdOrderByCreatedAtDesc(Long readerId);

    /**
     * 某讀者的交易明細，新到舊，帶分頁（「我的帳戶」頁使用）。
     *
     * <p>與上面無分頁的版本分開，是因為既有呼叫點（客訴對帳）需要完整帳本，
     * 不應被本任務的顯示上限影響；「我的帳戶」頁改用本方法搭配
     * {@code PageRequest.of(0, 50)} 只取最近 50 筆，避免帳本隨解鎖與邀請
     * 無限成長拖慢頁面。</p>
     */
    List<CreditTxn> findByReaderIdOrderByCreatedAtDesc(Long readerId, Pageable pageable);

    // 刻意沒有 existsByReasonAndNote：邀請獎勵的冪等曾經靠它做 check-then-act，
    // 現在改由資料庫的 uq_credit_txn_referral_note（V9）保證。留著一個
    // 「查有沒有發過」的方法會邀請日後有人把那條競態重新加回來，故一併移除。

    /** 某讀者某類交易的明細（新到舊）；邀請成效的「累計獲得點數」使用 */
    List<CreditTxn> findByReaderIdAndReasonOrderByCreatedAtDesc(Long readerId, String reason);

    /** 該提案是否已有指定原因的交易——退點冪等防線（REJECTED→ARCHIVED 不重複退） */
    boolean existsByPromoProposalIdAndReason(Long promoProposalId, String reason);

    /** 該讀者是否已有指定問卷、指定原因的交易——填答發點冪等防線（防重複領取） */
    boolean existsByReaderIdAndSurveyFormKeyAndReason(Long readerId, String surveyFormKey, String reason);

    /**
     * 依讀者彙總「邀請」與「問卷」兩類獎勵點數，合計降冪（排行榜使用）。
     *
     * <p>每列為 {@code [readerId, referralSum, surveySum]}（SUM 在 JPQL 回 Long）。
     * 只計正項：獎勵交易本來只會是正數，но {@code delta > 0} 仍要明寫——
     * 日後若出現獎勵回收（負項沖銷），排行榜顯示的是「獲得過多少」而非淨額，
     * 語意由這條 WHERE 鎖住。SIGNUP_GRANT／ADMIN_GRANT 等非邀請、非問卷
     * 的進帳一律不入榜，reason 白名單與 {@link CreditTxn} 常數同步維護。</p>
     */
    @org.springframework.data.jpa.repository.Query("""
        SELECT t.readerId,
               SUM(CASE WHEN t.reason IN ('REFERRAL', 'REFERRAL_INVITEE', 'REFERRAL_MILESTONE')
                        THEN t.delta ELSE 0 END),
               SUM(CASE WHEN t.reason IN ('SURVEY_REWARD', 'SURVEY_VOTE_REWARD')
                        THEN t.delta ELSE 0 END)
          FROM CreditTxn t
         WHERE t.delta > 0
           AND t.reason IN ('REFERRAL', 'REFERRAL_INVITEE', 'REFERRAL_MILESTONE',
                            'SURVEY_REWARD', 'SURVEY_VOTE_REWARD')
         GROUP BY t.readerId
         ORDER BY SUM(t.delta) DESC
        """)
    List<Object[]> sumRewardsByReader(Pageable pageable);
}
