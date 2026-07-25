package world.springai.survey.reader;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 點數帳本資料存取層；只新增不修改，故無 update 方法 */
public interface CreditTxnRepository extends JpaRepository<CreditTxn, Long> {

    /** 某讀者的交易明細，新到舊（客訴對帳與「我的帳戶」頁使用） */
    List<CreditTxn> findByReaderIdOrderByCreatedAtDesc(Long readerId);

    /**
     * 是否已有這筆原因與註記的交易——邀請獎勵的冪等鍵。
     *
     * <p>note 存的是被邀者 email，所以「同一個被邀者只發一次獎」由此保證。
     * 重複點擊確認信、退訂後再確認，都不會重複發獎。</p>
     */
    boolean existsByReasonAndNote(String reason, String note);

    /** 某讀者某類交易的明細（新到舊）；邀請成效統計使用 */
    List<CreditTxn> findByReaderIdAndReasonOrderByCreatedAtDesc(Long readerId, String reason);
}
