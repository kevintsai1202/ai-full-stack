package world.springai.survey.form;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import world.springai.survey.audience.AudiencePlatformService;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link SurveyVoteStatsService} 驗證：塞入 {@code survey_vote} 樣本列與對應的
 * {@code audience_record} 完填紀錄，驗證選項具名／匿名分布與 campaign 轉換率聚合。
 *
 * <p>統計聚合走真實 5433 PG 整合測試（比照 {@code SurveyVoteRepositoryTest} 與
 * {@code FormSchemaServiceCreateFormTest} 的基底模式），因為聚合邏輯是 SQL
 * {@code GROUP BY}，沒有 entity 可供 mock。</p>
 */
class SurveyVoteStatsServiceTest {

    private static String env(String name, String defaultValue) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }

    private static final String DB_HOST = env("MIGRATION_TEST_DB_HOST", "127.0.0.1");
    private static final String DB_PORT = env("MIGRATION_TEST_DB_PORT", "5433");
    private static final String USER = env("MIGRATION_TEST_DB_USER", "postgres");
    private static final String PASS = env("MIGRATION_TEST_DB_PASSWORD", "password");
    private static final String ADMIN_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/postgres";
    /** 獨立的資料庫名稱，不與其他測試共用 */
    private static final String TEST_DB = "survey_vote_stats_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    private static JdbcTemplate jdbc;
    private static AudiencePlatformService audience;
    private static SurveyVoteStatsService stats;

    /** 重建乾淨資料庫並套用全部 migration，測試不依賴開發資料庫狀態。 */
    @BeforeAll
    static void prepare() throws SQLException {
        requireTestDatabase();
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS);
             Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + TEST_DB + " WITH (FORCE)");
            st.execute("CREATE DATABASE " + TEST_DB);
        }
        Flyway.configure().dataSource(TEST_URL, USER, PASS).load().migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(TEST_URL, USER, PASS);
        jdbc = new JdbcTemplate(dataSource);
        audience = new AudiencePlatformService(jdbc, new ObjectMapper().findAndRegisterModules());
        stats = new SurveyVoteStatsService(jdbc);
    }

    /** 連不上專用測試容器時以明確訊息失敗，不靜默跳過 */
    private static void requireTestDatabase() {
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS)) {
            // 連得上即可
        } catch (SQLException e) {
            fail("""
                連不到專用測試容器（%s）。本測試驗證的是資料庫層的 SQL 聚合行為，
                無法用 mock 取代，因此不能靜默跳過。請先啟動容器：
                  docker start survey-test-db
                若容器不存在：
                  docker run -d --name survey-test-db -e POSTGRES_PASSWORD=password \\
                    -p 5433:5432 pgvector/pgvector:pg18
                連線資訊可用 MIGRATION_TEST_DB_HOST／PORT／USER／PASSWORD 覆寫。
                """.formatted(ADMIN_URL));
        }
    }

    /** 插入一筆最小 campaign 列並回傳自增 id；survey_vote.campaign_id 有 FK 約束，不能填假值。 */
    private long insertCampaign(String slug) {
        return jdbc.queryForObject("""
            INSERT INTO campaign (
                subject, markdown, mode, recipient_count, accepted_count,
                failed_count, status, slug, published_at
            ) VALUES (?, '內容', 'now', 0, 0, 0, 'sent', ?, now())
            RETURNING id
            """, Long.class, slug, slug);
    }

    /** 插入一筆投票樣本列。 */
    private void insertVote(String formKey, String optionValue, Long campaignId,
                             String identityType, String identityKey) {
        jdbc.update("""
            INSERT INTO survey_vote (
                form_key, field_key, option_value, campaign_id, channel,
                identity_type, identity_key
            ) VALUES (?, 'q1', ?, ?, 'EMAIL', ?, ?)
            """, formKey, optionValue, campaignId, identityType, identityKey);
    }

    /** 插入一筆電子報通道問卷完填紀錄（audience_record，source=newsletter_survey），供轉換率分子計算。 */
    private void insertSubmission(String formKey, int version, Long campaignId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        long personId = audience.mergePerson(
            "vote-stats-" + java.util.UUID.randomUUID() + "@example.com", "投票統計測試", now).personId();
        audience.upsertRecord(
            personId, "newsletter_survey", "survey_submission",
            formKey + "@" + version, java.util.UUID.randomUUID().toString(), now,
            Map.of("answers", Map.of(), "campaignId", campaignId, "channel", "EMAIL"),
            Map.of());
    }

    /**
     * 具名（RECIPIENT/READER）與匿名（ANON）票分別計入 named／anon；
     * 選項聚合不區分 campaign。
     */
    @Test
    void 依選項聚合具名與匿名票數() {
        long campaignId = insertCampaign("vote-stats-option-campaign");
        insertVote("vote-stats-poll", "很有幫助", campaignId, "RECIPIENT", "reader-1@example.com");
        insertVote("vote-stats-poll", "很有幫助", campaignId, "READER", "reader-2");
        insertVote("vote-stats-poll", "很有幫助", null, "ANON", null);
        insertVote("vote-stats-poll", "普通", campaignId, "RECIPIENT", "reader-3@example.com");

        Map<String, Object> result = stats.voteStats("vote-stats-poll");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> options = (List<Map<String, Object>>) result.get("options");
        Map<String, Object> helpful = options.stream()
            .filter(row -> "很有幫助".equals(row.get("value")))
            .findFirst().orElseThrow();
        assertEquals(2L, ((Number) helpful.get("named")).longValue());
        assertEquals(1L, ((Number) helpful.get("anon")).longValue());
        assertEquals(4L, ((Number) result.get("totalVotes")).longValue());
        assertEquals(3L, ((Number) result.get("totalNamed")).longValue());
    }

    /**
     * byCampaign 依 campaign 聚合票數與完填數，conversionRate＝完填數÷票數；
     * 完填數查 audience_record（source=newsletter_survey）raw->>'campaignId' 相符列。
     */
    @Test
    void 依campaign聚合票數完填數與轉換率() {
        String formKey = "vote-stats-campaign-poll";
        long campaignId = insertCampaign("vote-stats-conversion-campaign");
        insertVote(formKey, "A", campaignId, "RECIPIENT", "campaign-r1@example.com");
        insertVote(formKey, "B", campaignId, "RECIPIENT", "campaign-r2@example.com");
        insertVote(formKey, "A", campaignId, "READER", "campaign-reader-3");
        insertVote(formKey, "A", campaignId, "ANON", null);
        insertVote(formKey, "A", campaignId, "ANON", null);
        insertSubmission(formKey, 1, campaignId);
        insertSubmission(formKey, 1, campaignId);

        Map<String, Object> result = stats.voteStats(formKey);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byCampaign = (List<Map<String, Object>>) result.get("byCampaign");
        Map<String, Object> campaign9 = byCampaign.stream()
            .filter(row -> campaignId == ((Number) row.get("campaignId")).longValue())
            .findFirst().orElseThrow();
        assertEquals(5L, ((Number) campaign9.get("votes")).longValue());
        assertEquals(2L, ((Number) campaign9.get("submissions")).longValue());
        assertEquals(
            new BigDecimal("0.400"),
            new BigDecimal(String.valueOf(campaign9.get("conversionRate"))));
    }

    /** 沒有 campaign 歸因（campaign_id 為 null）的票不進入 byCampaign。 */
    @Test
    void 沒有campaign歸因的票不計入byCampaign() {
        String formKey = "vote-stats-no-campaign-poll";
        insertVote(formKey, "A", null, "ANON", null);

        Map<String, Object> result = stats.voteStats(formKey);

        assertTrue(((List<?>) result.get("byCampaign")).isEmpty());
        assertEquals(1L, ((Number) result.get("totalVotes")).longValue());
    }

    /** 完全沒有票的表單回零值結構，不拋例外／不回 404 語意。 */
    @Test
    void 沒有任何票的表單回零值結構() {
        Map<String, Object> result = stats.voteStats("vote-stats-empty-poll");

        assertTrue(((List<?>) result.get("options")).isEmpty());
        assertTrue(((List<?>) result.get("byCampaign")).isEmpty());
        assertEquals(0L, ((Number) result.get("totalVotes")).longValue());
        assertEquals(0L, ((Number) result.get("totalNamed")).longValue());
    }
}
