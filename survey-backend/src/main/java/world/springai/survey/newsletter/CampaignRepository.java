package world.springai.survey.newsletter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 電子報批次資料存取層 */
public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    /** 依建立時間新到舊列出（歷史頁用） */
    List<Campaign> findAllByOrderByCreatedAtDesc();

    /** archive 列表：只列已發布者，新到舊 */
    List<Campaign> findByPublishedAtIsNotNullOrderByPublishedAtDesc();

    /** 依 slug 查單篇文章 */
    Optional<Campaign> findBySlug(String slug);
}
