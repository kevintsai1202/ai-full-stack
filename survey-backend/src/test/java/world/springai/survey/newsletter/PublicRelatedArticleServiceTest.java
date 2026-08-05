package world.springai.survey.newsletter;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link PublicRelatedArticleService} 整合測試：同標籤優先排序、不足補最新、
 * 排除本篇與未發布文章。走真實 5433 PG（服務全走 JdbcTemplate，無 entity 可 mock），
 * 模式比照 {@code FormSchemaServiceCreateFormTest}。
 */
@SpringBootTest(properties = "spring.flyway.enabled=false")
class PublicRelatedArticleServiceTest {

    private static String env(String name, String defaultValue) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }

    private static final String DB_HOST = env("MIGRATION_TEST_DB_HOST", "127.0.0.1");
    private static final String DB_PORT = env("MIGRATION_TEST_DB_PORT", "5433");
    private static final String USER = env("MIGRATION_TEST_DB_USER", "postgres");
    private static final String PASS = env("MIGRATION_TEST_DB_PASSWORD", "password");
    private static final String ADMIN_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/postgres";
    private static final String TEST_DB = "related_article_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TEST_URL);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASS);
    }

    @Autowired PublicRelatedArticleService service;

    /** 重建乾淨資料庫並套用全部 migration */
    @BeforeAll
    static void prepare() throws SQLException {
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS)) {
            // 連得上即可
        } catch (SQLException e) {
            fail("""
                連不到專用測試容器（%s）。請先啟動：
                  docker start survey-test-db
                若容器不存在：
                  docker run -d --name survey-test-db -e POSTGRES_PASSWORD=password \\
                    -p 5433:5432 pgvector/pgvector:pg18
                """.formatted(ADMIN_URL));
        }
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS);
             Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + TEST_DB + " WITH (FORCE)");
            st.execute("CREATE DATABASE " + TEST_DB);
        }
        Flyway.configure().dataSource(TEST_URL, USER, PASS).load().migrate();
    }

    /** 每個測試前清空文章與標籤關聯，避免 migration 內建資料與前一個測試互相干擾 */
    @BeforeEach
    void clean() throws SQLException {
        exec("DELETE FROM campaign_tag");
        exec("DELETE FROM campaign");
    }

    /** 執行一段 SQL */
    private void exec(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(TEST_URL, USER, PASS);
             Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    /**
     * 插入一篇文章並回傳 id。publishedAt 為 null 代表未發布。
     * campaign 的 NOT NULL 欄位（markdown/mode/status/tier 等）都給最小合法值。
     */
    private long insertCampaign(String slug, String subject, String publishedAt) throws SQLException {
        String published = publishedAt == null ? "NULL" : "'" + publishedAt + "'";
        String slugValue = slug == null ? "NULL" : "'" + slug + "'";
        try (Connection c = DriverManager.getConnection(TEST_URL, USER, PASS);
             Statement st = c.createStatement();
             var rs = st.executeQuery("""
                 INSERT INTO campaign (subject, markdown, mode, status, recipient_count,
                                       accepted_count, failed_count, tier, credit_cost,
                                       slug, published_at, cover_emoji)
                 VALUES ('%s', 'body', 'now', 'sent', 0, 0, 0, 'BASIC', 0, %s, %s, '🚀')
                 RETURNING id
                 """.formatted(subject, slugValue, published))) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** 把文章綁到指定 slug 的標籤上（標籤由 V12 預設資料提供） */
    private void tag(long campaignId, String tagSlug) throws SQLException {
        exec("""
            INSERT INTO campaign_tag (campaign_id, tag_id)
            SELECT %d, id FROM content_tag WHERE slug = '%s'
            """.formatted(campaignId, tagSlug));
    }

    /** 共同標籤數多的排前面；完全無共同標籤者不進第一段 */
    @Test
    void 同標籤交集多者優先() throws Exception {
        long base = insertCampaign("base", "本篇", "2026-07-01T10:00:00+08:00");
        tag(base, "ai");
        tag(base, "rag");

        long two = insertCampaign("two-shared", "共兩個標籤", "2026-06-01T10:00:00+08:00");
        tag(two, "ai");
        tag(two, "rag");
        long one = insertCampaign("one-shared", "共一個標籤", "2026-06-20T10:00:00+08:00");
        tag(one, "ai");

        List<PublicRelatedArticleService.RelatedArticle> result = service.relatedTo(base, 5);

        assertEquals("two-shared", result.get(0).slug(),
            "共同標籤較多者必須排在前面，即使發布日較舊");
        assertEquals("one-shared", result.get(1).slug());
        assertFalse(result.stream().anyMatch(a -> a.slug().equals("base")), "不得列出本篇");
    }

    /** 同標籤不足 limit 時，用最新已發布文章補齊，且不重複已入選者 */
    @Test
    void 不足時補最新且不重複() throws Exception {
        long base = insertCampaign("base", "本篇", "2026-07-01T10:00:00+08:00");
        tag(base, "ai");
        long shared = insertCampaign("shared", "同標籤", "2026-05-01T10:00:00+08:00");
        tag(shared, "ai");
        insertCampaign("fresh", "無標籤但最新", "2026-06-30T10:00:00+08:00");

        List<PublicRelatedArticleService.RelatedArticle> result = service.relatedTo(base, 2);

        assertEquals(2, result.size());
        assertEquals("shared", result.get(0).slug(), "同標籤者永遠排在補齊者之前");
        assertEquals("fresh", result.get(1).slug());
        assertEquals(1L, result.stream().filter(a -> a.slug().equals("shared")).count(),
            "補齊時不得重複列出第一段已入選的文章");
    }

    /** 未發布（slug 或 published_at 為 null）一律不出現在任何一段 */
    @Test
    void 未發布文章不列入() throws Exception {
        long base = insertCampaign("base", "本篇", "2026-07-01T10:00:00+08:00");
        tag(base, "ai");
        long draft = insertCampaign("draft", "同標籤但未發布", null);
        tag(draft, "ai");
        insertCampaign(null, "沒有 slug", "2026-06-01T10:00:00+08:00");

        List<PublicRelatedArticleService.RelatedArticle> result = service.relatedTo(base, 5);

        assertTrue(result.isEmpty(), "只有未發布的候選時應回空清單：" + result);
    }

    /** 本篇沒有任何標籤時，全部由最新補齊（不需特例分支，但行為必須被釘住） */
    @Test
    void 本篇無標籤時全走補齊() throws Exception {
        long base = insertCampaign("base", "本篇", "2026-07-01T10:00:00+08:00");
        insertCampaign("older", "較舊", "2026-05-01T10:00:00+08:00");
        insertCampaign("newer", "較新", "2026-06-01T10:00:00+08:00");

        List<PublicRelatedArticleService.RelatedArticle> result = service.relatedTo(base, 5);

        assertEquals(List.of("newer", "older"), result.stream()
            .map(PublicRelatedArticleService.RelatedArticle::slug).toList(),
            "無標籤時應依發布日新到舊回傳");
    }

    /** limit 為 0 或負數時直接回空清單，不下任何 SQL */
    @Test
    void limit非正數回空清單() throws Exception {
        long base = insertCampaign("base", "本篇", "2026-07-01T10:00:00+08:00");
        insertCampaign("other", "其他", "2026-06-01T10:00:00+08:00");

        assertTrue(service.relatedTo(base, 0).isEmpty());
        assertTrue(service.relatedTo(base, -1).isEmpty());
    }
}
