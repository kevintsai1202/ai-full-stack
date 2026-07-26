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
}
