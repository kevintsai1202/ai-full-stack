package world.springai.survey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 寄送記錄資料存取層 */
public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {
    /** 某 campaign 的所有寄送記錄 */
    List<EmailLog> findByCampaignId(Long campaignId);
    /** 某 campaign 中特定狀態的寄送記錄（取消排程用） */
    List<EmailLog> findByCampaignIdAndStatus(Long campaignId, String status);

    /** 特定類型與狀態的所有寄送記錄（邀請信跳過已寄過者用） */
    List<EmailLog> findByTypeAndStatus(String type, String status);
}
