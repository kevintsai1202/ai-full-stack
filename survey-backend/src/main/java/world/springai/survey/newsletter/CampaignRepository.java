package world.springai.survey.newsletter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 電子報批次資料存取層 */
public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    /** 依建立時間新到舊列出（歷史頁用） */
    List<Campaign> findAllByOrderByCreatedAtDesc();

    /**
     * archive 列表：只列「真正可開啟」的已發布文章，新到舊。
     *
     * <p>同時要求 slug 與 publishedAt 皆非 NULL，是防禦既有資料的第二道關卡——
     * 即使 service 層已擋下「設了 publishedAt 卻沒設 slug」的矛盾輸入，資料庫裡
     * 仍可能存在手動 SQL 造成的這種列；若查詢只看 publishedAt，archive 會列出
     * 一篇沒有 slug 可組連結的文章，讀者點下去會打到 /r/news/（空 path variable）
     * 而 404。</p>
     */
    List<Campaign> findBySlugIsNotNullAndPublishedAtIsNotNullOrderByPublishedAtDesc();

    /** 依 slug 查單篇文章 */
    Optional<Campaign> findBySlug(String slug);
}
