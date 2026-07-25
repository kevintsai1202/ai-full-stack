package world.springai.survey.reader;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 已解鎖文章資料存取層 */
public interface ArticleAccessRepository extends JpaRepository<ArticleAccess, Long> {

    /** 該讀者是否已解鎖此文章（授權判斷的「已解鎖」路徑） */
    boolean existsByReaderIdAndCampaignId(Long readerId, Long campaignId);

    /** 該讀者已解鎖的全部文章，archive 列表用於標示解鎖狀態 */
    List<ArticleAccess> findByReaderId(Long readerId);
}
