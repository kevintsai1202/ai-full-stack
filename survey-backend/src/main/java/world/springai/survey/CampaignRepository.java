package world.springai.survey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 電子報批次資料存取層 */
public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    /** 依建立時間新到舊列出（歷史頁用） */
    List<Campaign> findAllByOrderByCreatedAtDesc();
}
