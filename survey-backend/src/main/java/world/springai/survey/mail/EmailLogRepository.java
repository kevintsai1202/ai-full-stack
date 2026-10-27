package world.springai.survey.mail;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 寄送記錄資料存取層 */
public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {
    /** 某 campaign 的所有寄送記錄 */
    List<EmailLog> findByCampaignId(Long campaignId);
    /** 某 campaign 中特定狀態的寄送記錄（取消排程用） */
    List<EmailLog> findByCampaignIdAndStatus(Long campaignId, String status);
    /** 某實際批次的特定狀態寄送記錄（取消補寄排程用）。 */
    List<EmailLog> findByBatchIdAndStatus(Long batchId, String status);

    /**
     * 某 campaign 的寄送記錄筆數。
     *
     * <p>用 count 而非 {@link #findByCampaignId} 再取 size：下架前只需要知道
     * 「這篇有沒有寄過信」，把整批寄送記錄載進記憶體只為了問一個布林值，
     * 在大批次（上千封）時是白花的記憶體與查詢時間。</p>
     */
    long countByCampaignId(Long campaignId);

    /** 特定類型與狀態的所有寄送記錄（邀請信跳過已寄過者用） */
    List<EmailLog> findByTypeAndStatus(String type, String status);

    /** 特定類型的所有寄送記錄，依時間新到舊（後台邀請記錄列表用） */
    List<EmailLog> findByTypeOrderByCreatedAtDesc(String type);

    /** 依 type 前綴＋狀態查詢：供優惠券「已寄總覽」以 coupon: 前綴一次撈出所有活動的寄送記錄 */
    List<EmailLog> findByTypeStartingWithAndStatus(String typePrefix, String status);

    /**
     * SES 待寄佇列派送失敗時回寫：把對應參照與收件人的 sent／scheduled 記錄改成 failed。
     *
     * <p>批次群發的 email_log 在「入列」當下就記成 sent，實際送出是之後的事；
     * 送不出去時靠這支讓記錄反映事實，邀請信等「看 sent 判斷寄過沒」的流程下次才會重寄。</p>
     */
    @Modifying
    @Transactional
    @Query(value = """
        UPDATE email_log SET status = 'failed', error = :error
        WHERE provider_message_id = :ref
          AND lower(recipient) = lower(:recipient)
          AND status IN ('sent', 'scheduled')
        """, nativeQuery = true)
    int markDeliveryFailed(@Param("ref") String ref, @Param("recipient") String recipient,
                           @Param("error") String error);

    /**
     * SES 送達事件：記下送達時間。只保留第一次的時間（重送冪等），不動 status。
     *
     * @param ref       呼叫端參照（ses-batch-*／ses-sched-*，交易信為 SES MessageId）
     * @param recipient 收件人（大小寫不敏感）
     */
    @Modifying
    @Transactional
    @Query(value = """
        UPDATE email_log SET delivered_at = COALESCE(delivered_at, :at)
        WHERE provider_message_id = :ref AND lower(recipient) = lower(:recipient)
        """, nativeQuery = true)
    int markDelivered(@Param("ref") String ref, @Param("recipient") String recipient,
                      @Param("at") java.time.OffsetDateTime at);
}
