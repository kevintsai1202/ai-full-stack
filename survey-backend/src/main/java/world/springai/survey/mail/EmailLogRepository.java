package world.springai.survey.mail;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 寄送記錄資料存取層 */
public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {
    /** 某 campaign 的所有寄送記錄 */
    List<EmailLog> findByCampaignId(Long campaignId);
    /** 某 campaign 中特定狀態的寄送記錄（取消排程用） */
    List<EmailLog> findByCampaignIdAndStatus(Long campaignId, String status);

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
}
