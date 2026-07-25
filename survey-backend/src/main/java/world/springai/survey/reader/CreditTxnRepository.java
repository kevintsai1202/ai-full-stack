package world.springai.survey.reader;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 點數帳本資料存取層；只新增不修改，故無 update 方法 */
public interface CreditTxnRepository extends JpaRepository<CreditTxn, Long> {

    /** 某讀者的交易明細，新到舊（客訴對帳與「我的帳戶」頁使用） */
    List<CreditTxn> findByReaderIdOrderByCreatedAtDesc(Long readerId);
}
