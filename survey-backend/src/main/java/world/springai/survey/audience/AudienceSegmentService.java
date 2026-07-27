package world.springai.survey.audience;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** 常用分眾條件的保存、讀取與刪除服務。 */
@Service
public class AudienceSegmentService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    /** 注入 SQL 與 JSON 工具。 */
    public AudienceSegmentService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** 分眾條件資料。 */
    public record Segment(
            long id,
            String key,
            String label,
            AudienceSearchService.Filters filters) {}

    /** 新增或修改請求；key 可留空自動產生。 */
    public record SegmentRequest(
            String key,
            String label,
            AudienceSearchService.Filters filters) {}

    /** 列出全部保存條件。 */
    public List<Segment> list() {
        return jdbc.query("""
            SELECT id, segment_key, label, filters::text
              FROM audience_segment ORDER BY label, id
            """, (rs, rowNum) -> new Segment(
                rs.getLong("id"),
                rs.getString("segment_key"),
                rs.getString("label"),
                parseFilters(rs.getString("filters"))));
    }

    /** 依 ID 取得分眾，Campaign 寄送前會重新套用目前資料。 */
    public Segment get(long id) {
        return list().stream()
            .filter(segment -> segment.id() == id)
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到分眾條件"));
    }

    /** 新增分眾條件；key 重複時回 409，不靜默覆蓋。 */
    public Segment create(SegmentRequest request) {
        validate(request);
        String key = StringUtils.hasText(request.key())
            ? normalizeKey(request.key())
            : normalizeKey(request.label());
        try {
            Long id = jdbc.queryForObject("""
                INSERT INTO audience_segment (segment_key, label, filters)
                VALUES (?, ?, ?::jsonb)
                RETURNING id
                """, Long.class, key, request.label().trim(), json(request.filters()));
            return new Segment(id, key, request.label().trim(), request.filters());
        } catch (org.springframework.dao.DataIntegrityViolationException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "分眾 key 已存在");
        }
    }

    /** 更新指定分眾；保留穩定 ID 供既有 Campaign 參照。 */
    public Segment update(long id, SegmentRequest request) {
        validate(request);
        String key = StringUtils.hasText(request.key())
            ? normalizeKey(request.key())
            : normalizeKey(request.label());
        int affected = jdbc.update("""
            UPDATE audience_segment
               SET segment_key = ?, label = ?, filters = ?::jsonb, updated_at = now()
             WHERE id = ?
            """, key, request.label().trim(), json(request.filters()), id);
        if (affected == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到分眾條件");
        }
        return new Segment(id, key, request.label().trim(), request.filters());
    }

    /** 刪除未被 Campaign 參照的分眾；被參照時 FK 會改為 null，歷史 filter_json 仍保留。 */
    public void delete(long id) {
        if (jdbc.update("DELETE FROM audience_segment WHERE id = ?", id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到分眾條件");
        }
    }

    /** 驗證顯示名稱與 filters。 */
    private void validate(SegmentRequest request) {
        if (request == null || !StringUtils.hasText(request.label()) || request.filters() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "label 與 filters 為必填");
        }
        if (request.label().trim().length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分眾名稱不可超過 80 字");
        }
    }

    /** 產生 URL/JSON 友善的穩定 key。 */
    private String normalizeKey(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-+|-+$)", "");
        return normalized.isBlank()
            ? "segment-" + Integer.toUnsignedString(value.hashCode(), 36)
            : normalized;
    }

    /** Filters 序列化。 */
    private String json(AudienceSearchService.Filters filters) {
        try {
            return objectMapper.writeValueAsString(filters);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("分眾條件無法序列化", exception);
        }
    }

    /** Filters 反序列化；資料損壞時明確失敗，避免誤寄給不限名單。 */
    private AudienceSearchService.Filters parseFilters(String value) {
        try {
            return objectMapper.readValue(value, AudienceSearchService.Filters.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("分眾條件資料損壞", exception);
        }
    }
}
