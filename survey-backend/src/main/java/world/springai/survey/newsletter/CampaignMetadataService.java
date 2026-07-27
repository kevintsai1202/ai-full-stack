package world.springai.survey.newsletter;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 管理文章封面 Emoji 與正規化 hashtag。 */
@Service
public class CampaignMetadataService {

    private static final int MAX_TAGS = 8;
    private final JdbcTemplate jdbc;

    /** 注入資料庫查詢工具。 */
    public CampaignMetadataService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 列出後台可選 hashtag。 */
    public List<Map<String, Object>> listTags() {
        return jdbc.queryForList("""
            SELECT id, name, slug, preset
              FROM content_tag
             WHERE active = TRUE
             ORDER BY sort_order, name
            """);
    }

    /** 儲存文章 Emoji 與 hashtag；同名標籤會自動共用。 */
    @Transactional
    public void update(long campaignId, String coverEmoji, List<String> requestedTags) {
        validate(coverEmoji, requestedTags);
        if (jdbc.queryForObject("SELECT count(*) FROM campaign WHERE id = ?", Long.class, campaignId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到指定文章");
        }
        String emoji = coverEmoji == null ? null : coverEmoji.strip();
        jdbc.update("UPDATE campaign SET cover_emoji = ? WHERE id = ?",
            emoji == null || emoji.isBlank() ? null : emoji, campaignId);

        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (requestedTags != null) {
            requestedTags.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::strip)
                .map(value -> value.startsWith("#") ? value.substring(1).strip() : value)
                .filter(value -> !value.isBlank())
                .forEach(tags::add);
        }
        jdbc.update("DELETE FROM campaign_tag WHERE campaign_id = ?", campaignId);
        int order = 100;
        for (String name : tags) {
            String normalizedKey = Normalizer.normalize(name, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
            String slug = slug(normalizedKey);
            jdbc.update("""
                INSERT INTO content_tag (name, normalized_key, slug, preset, sort_order)
                VALUES (?, ?, ?, FALSE, ?)
                ON CONFLICT (normalized_key) DO UPDATE SET active = TRUE
                """, name, normalizedKey, slug, order++);
            jdbc.update("""
                INSERT INTO campaign_tag (campaign_id, tag_id)
                SELECT ?, id FROM content_tag WHERE normalized_key = ?
                ON CONFLICT DO NOTHING
                """, campaignId, normalizedKey);
        }
    }

    /** 在寄信或發布產生副作用前先驗證中繼資料，避免信已寄出才回 400。 */
    public void validate(String coverEmoji, List<String> requestedTags) {
        String emoji = coverEmoji == null ? null : coverEmoji.strip();
        if (emoji != null && emoji.codePointCount(0, emoji.length()) > 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "封面 Emoji 最多 4 個字元");
        }
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (requestedTags != null) {
            requestedTags.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::strip)
                .map(value -> value.startsWith("#") ? value.substring(1).strip() : value)
                .filter(value -> !value.isBlank())
                .forEach(tags::add);
        }
        if (tags.size() > MAX_TAGS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "每篇文章最多 8 個 hashtag");
        }
        if (tags.stream().anyMatch(name -> name.codePointCount(0, name.length()) > 30)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hashtag 最多 30 個字元");
        }
    }

    /** 將自訂標籤轉為可放在查詢參數中的穩定 slug。 */
    private String slug(String normalizedKey) {
        String value = normalizedKey.replaceAll("\\s+", "-")
            .replaceAll("[^\\p{L}\\p{N}-]", "")
            .replaceAll("-{2,}", "-")
            .replaceAll("^-|-$", "");
        String hash = Integer.toUnsignedString(normalizedKey.hashCode(), 36);
        if (value.isBlank()) {
            return "tag-" + hash;
        }
        Long collision = jdbc.queryForObject("""
            SELECT count(*) FROM content_tag
             WHERE slug = ? AND normalized_key <> ?
            """, Long.class, value, normalizedKey);
        return collision != null && collision > 0 ? value + "-" + hash : value;
    }
}
