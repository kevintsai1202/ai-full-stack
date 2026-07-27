package world.springai.survey.newsletter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 電子報寄送批次資料存取層。 */
public interface CampaignBatchRepository extends JpaRepository<CampaignBatch, Long> {

    /** 依 campaign 取得新到舊批次。 */
    List<CampaignBatch> findByCampaignIdOrderByCreatedAtDesc(Long campaignId);

    /** 判斷 campaign 是否已建立任何批次。 */
    boolean existsByCampaignId(Long campaignId);
}
