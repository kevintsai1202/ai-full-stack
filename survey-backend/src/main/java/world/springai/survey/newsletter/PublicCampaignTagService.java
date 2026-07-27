package world.springai.survey.newsletter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 公開文章頁使用的 hashtag 查詢服務。 */
@Service
public class PublicCampaignTagService {

    private final JdbcTemplate jdbc;

    /** 公開 hashtag 與已發布文章數量。 */
    public record TagSummary(String name, String slug, long articleCount) {}

    /** 注入資料庫查詢工具。 */
    public PublicCampaignTagService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 只列出至少有一篇已發布文章的 hashtag。 */
    public List<TagSummary> publicTags() {
        return jdbc.query("""
            SELECT t.name, t.slug, count(*) AS article_count
              FROM content_tag t
              JOIN campaign_tag ct ON ct.tag_id = t.id
              JOIN campaign c ON c.id = ct.campaign_id
             WHERE t.active = TRUE AND c.slug IS NOT NULL AND c.published_at IS NOT NULL
             GROUP BY t.id, t.name, t.slug, t.sort_order
             ORDER BY t.sort_order, t.name
            """, (rs, rowNum) -> new TagSummary(
            rs.getString("name"), rs.getString("slug"), rs.getLong("article_count")));
    }

    /** 取得符合指定 hashtag 的公開文章 ID；slug 空白時不限制。 */
    public Set<Long> campaignIds(String tagSlug) {
        if (tagSlug == null || tagSlug.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(jdbc.query("""
            SELECT c.id
              FROM campaign c
              JOIN campaign_tag ct ON ct.campaign_id = c.id
              JOIN content_tag t ON t.id = ct.tag_id
             WHERE t.slug = ? AND t.active = TRUE
               AND c.slug IS NOT NULL AND c.published_at IS NOT NULL
            """, (rs, rowNum) -> rs.getLong(1), tagSlug));
    }

    /** 批次回傳文章 ID 對應的 hashtag，避免列表逐篇查詢。 */
    public Map<Long, List<TagSummary>> tagsByCampaign(List<Long> campaignIds) {
        if (campaignIds == null || campaignIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(campaignIds.size(), "?"));
        Map<Long, List<TagSummary>> result = new LinkedHashMap<>();
        jdbc.query("""
            SELECT ct.campaign_id, t.name, t.slug
              FROM campaign_tag ct
              JOIN content_tag t ON t.id = ct.tag_id
             WHERE t.active = TRUE AND ct.campaign_id IN (%s)
             ORDER BY t.sort_order, t.name
            """.formatted(placeholders), rs -> {
                result.computeIfAbsent(rs.getLong("campaign_id"), ignored -> new java.util.ArrayList<>())
                    .add(new TagSummary(rs.getString("name"), rs.getString("slug"), 0));
            }, campaignIds.toArray());
        return result;
    }
}
