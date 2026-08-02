package world.springai.survey.promo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 工商提案資料存取層 */
public interface PromoProposalRepository extends JpaRepository<PromoProposal, Long> {

    /** 某讀者在特定狀態下的提案數量（送出前檢查同時進行中的提案上限用） */
    int countByReaderIdAndStatus(Long readerId, String status);

    /** 讀者本人的提案列表，新到舊 */
    List<PromoProposal> findByReaderIdOrderByCreatedAtDesc(Long readerId);

    /** 後台依狀態篩選提案列表，新到舊 */
    List<PromoProposal> findByStatusOrderByCreatedAtDesc(String status);

    /** 可進編輯器選單的提案：已核准且配額未滿 */
    @Query("select p from PromoProposal p where p.status = 'APPROVED' "
        + "and p.placementUsed < p.placementQuota order by p.createdAt desc")
    List<PromoProposal> findSelectable();

    /**
     * 條件式扣配額：只有未滿額才扣。回傳 0 表示配額已滿（或提案不存在），
     * 呼叫端必須據此擋下寄送——正確性來自受影響筆數，不是先前的檢查。
     *
     * <p>{@code flushAutomatically}／{@code clearAutomatically} 的理由與
     * {@code ReaderRepository.deductCredits} 逐字相同：避免同交易內尚未 flush
     * 的寫入被本 UPDATE 錯過，也避免一級快取讓呼叫端在扣配額後讀到舊的
     * {@link PromoProposal}（含 dirty check 把整列覆寫回舊值的風險）。</p>
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("update PromoProposal p set p.placementUsed = p.placementUsed + 1 "
        + "where p.id = :id and p.placementUsed < p.placementQuota")
    int consumeQuota(@Param("id") Long id);

    /** 歸還一次配額（重排移除版位／取消排程時；下限 0 由 SQL 條件保證） */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("update PromoProposal p set p.placementUsed = p.placementUsed - 1 "
        + "where p.id = :id and p.placementUsed > 0")
    int releaseQuota(@Param("id") Long id);
}
