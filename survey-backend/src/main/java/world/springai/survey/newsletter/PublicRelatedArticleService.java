package world.springai.survey.newsletter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 文章頁側欄用的「相關文章」唯讀查詢服務。
 *
 * <p>放在 newsletter 套件、與 {@link PublicCampaignTagService} 同層：兩者都是
 * 「公開文章的唯讀查詢」，而 {@code reader → newsletter} 是架構守衛授權的方向
 * （反向會形成上層循環，見 {@code PackageDependencyTest}）。</p>
 *
 * <p><b>刻意不回傳摘要</b>：摘要必須經 {@code ContentSplitter} 只取免費區，
 * 多一條可能把受限區帶出來的路徑；側欄寬度也放不下摘要。因此本服務只回
 * 標題、日期與封面這些本來就公開的欄位。</p>
 */
@Service
public class PublicRelatedArticleService {

    /** 側欄用的精簡文章描述；coverMediaId 為 null 時由呼叫端退回 coverEmoji */
    public record RelatedArticle(String slug, String subject, OffsetDateTime publishedAt,
                                 Long coverMediaId, String coverEmoji) {}

    private final JdbcTemplate jdbc;

    /** 注入資料庫查詢工具 */
    public PublicRelatedArticleService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 取得與指定文章相關的已發布文章，最多 limit 篇。
     *
     * <p>兩段式：先取共同 hashtag 最多者，不足時以最新已發布文章補齊
     * （排除本篇與第一段已入選者）。本篇沒有標籤時第一段自然回空，全部由補齊處理。</p>
     */
    public List<RelatedArticle> relatedTo(long campaignId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<RelatedArticle> result = new ArrayList<>(sameTag(campaignId, limit));
        if (result.size() < limit) {
            List<String> exclude = result.stream().map(RelatedArticle::slug).toList();
            result.addAll(latestExcluding(campaignId, exclude, limit - result.size()));
        }
        return result;
    }

    /** 第一段：共同 hashtag 數量多者優先，同數量時新的優先 */
    private List<RelatedArticle> sameTag(long campaignId, int limit) {
        return jdbc.query("""
            SELECT c.slug, c.subject, c.published_at, c.cover_media_id, c.cover_emoji
              FROM campaign c
              JOIN campaign_tag ct ON ct.campaign_id = c.id
              JOIN content_tag t ON t.id = ct.tag_id AND t.active = TRUE
             WHERE ct.tag_id IN (SELECT tag_id FROM campaign_tag WHERE campaign_id = ?)
               AND c.id <> ?
               AND c.slug IS NOT NULL AND c.published_at IS NOT NULL
             GROUP BY c.id, c.slug, c.subject, c.published_at, c.cover_media_id, c.cover_emoji
             ORDER BY count(*) DESC, c.published_at DESC
             LIMIT ?
            """, this::mapRow, campaignId, campaignId, limit);
    }

    /** 第二段：最新已發布文章，排除本篇與已入選的 slug */
    private List<RelatedArticle> latestExcluding(long campaignId, List<String> excludeSlugs, int limit) {
        if (excludeSlugs.isEmpty()) {
            return jdbc.query("""
                SELECT slug, subject, published_at, cover_media_id, cover_emoji
                  FROM campaign
                 WHERE id <> ? AND slug IS NOT NULL AND published_at IS NOT NULL
                 ORDER BY published_at DESC
                 LIMIT ?
                """, this::mapRow, campaignId, limit);
        }
        // 參數化 IN：slug 來自前一段查詢結果而非外部輸入，仍一律用 placeholder，
        // 不做字串拼接——這是本專案對所有動態 IN 清單的一致寫法（見 PublicCampaignTagService）
        String placeholders = String.join(",", Collections.nCopies(excludeSlugs.size(), "?"));
        List<Object> params = new ArrayList<>();
        params.add(campaignId);
        params.addAll(excludeSlugs);
        params.add(limit);
        return jdbc.query("""
            SELECT slug, subject, published_at, cover_media_id, cover_emoji
              FROM campaign
             WHERE id <> ? AND slug IS NOT NULL AND published_at IS NOT NULL
               AND slug NOT IN (%s)
             ORDER BY published_at DESC
             LIMIT ?
            """.formatted(placeholders), this::mapRow, params.toArray());
    }

    /** 把一列查詢結果轉成 RelatedArticle；cover_media_id 為 NULL 時保持 null */
    private RelatedArticle mapRow(ResultSet rs, int rowNum) throws SQLException {
        Object coverMediaIdRaw = rs.getObject("cover_media_id");
        Long coverMediaId = coverMediaIdRaw == null ? null : rs.getLong("cover_media_id");
        return new RelatedArticle(
            rs.getString("slug"),
            rs.getString("subject"),
            rs.getObject("published_at", OffsetDateTime.class),
            coverMediaId,
            rs.getString("cover_emoji"));
    }
}
