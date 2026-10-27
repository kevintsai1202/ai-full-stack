package world.springai.survey.newsletter;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import world.springai.survey.mail.EmailLogRepository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 送達時間（V28 的 delivered_at）在真資料庫上的寫入規則：只改對應參照與收件人、只記第一次送達時間（重送冪等）、
 * 不動狀態欄位。
 *
 * <p>連線與容器要求同 {@code SubscriptionConsentPersistenceTest}（{@code survey-test-db}、獨立資料庫、
 * {@code MIGRATION_TEST_DB_*} 可覆寫），連不上時明確失敗。</p>
 */
@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.datasource.hikari.maximum-pool-size=2",
    "spring.datasource.hikari.minimum-idle=1"
})
class DeliveredAtPersistenceTest {

    /** 取得環境變數，未設定或空字串時退回預設值 */
    private static String env(String name, String defaultValue) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }

    private static final String DB_HOST = env("MIGRATION_TEST_DB_HOST", "127.0.0.1");
    private static final String DB_PORT = env("MIGRATION_TEST_DB_PORT", "5433");
    private static final String USER = env("MIGRATION_TEST_DB_USER", "postgres");
    private static final String PASS = env("MIGRATION_TEST_DB_PASSWORD", "password");
    private static final String ADMIN_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/postgres";
    /** 獨立的資料庫名稱 */
    private static final String TEST_DB = "survey_delivered_at_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    /** 第一次送達時間 */
    private static final OffsetDateTime FIRST = OffsetDateTime.of(2026, 10, 27, 10, 0, 0, 0, ZoneOffset.UTC);

    /** 把連線三項交給 Spring context */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TEST_URL);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASS);
    }

    @Autowired EmailLogRepository emailLogs;
    @Autowired CampaignRecipientRepository recipients;
    @Autowired JdbcTemplate jdbc;

    /** 重建乾淨資料庫並套用全部 migration（含 V28） */
    @BeforeAll
    static void prepare() throws SQLException {
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS);
             Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + TEST_DB + " WITH (FORCE)");
            st.execute("CREATE DATABASE " + TEST_DB);
        } catch (SQLException e) {
            fail("連不到專用測試容器（" + ADMIN_URL + "），請先 docker start survey-test-db：" + e.getMessage());
        }
        Flyway.configure().dataSource(TEST_URL, USER, PASS).load().migrate();
    }

    /** 每個測試前清空並建一篇 campaign（收件人表有外鍵） */
    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM campaign_recipient");
        jdbc.update("DELETE FROM email_log");
        jdbc.update("DELETE FROM campaign");
        jdbc.update("""
            INSERT INTO campaign (id, subject, markdown, mode, status) VALUES (1, 's', 'm', 'now', 'sent')
            """);
    }

    /** 寫一筆群發收件人 */
    private void recipient(String email, String ref) {
        jdbc.update("""
            INSERT INTO campaign_recipient (campaign_id, email, email_normalized, status, provider_message_id)
            VALUES (1, ?, lower(?), 'SENT', ?)
            """, email, email, ref);
    }

    /** 寫一筆寄送記錄 */
    private void log(String email, String ref) {
        jdbc.update("""
            INSERT INTO email_log (recipient, subject, type, provider_message_id, status, campaign_id)
            VALUES (?, 's', 'campaign', ?, 'sent', 1)
            """, email, ref);
    }

    /** 讀送達時間 */
    private OffsetDateTime deliveredAt(String table, String emailColumn, String email) {
        return jdbc.queryForObject("SELECT delivered_at FROM " + table + " WHERE lower(" + emailColumn + ") = lower(?)",
            OffsetDateTime.class, email);
    }

    /** 只改對應參照與收件人；狀態不變；重送時保留第一次的送達時間 */
    @Test
    void marksOnlyMatchingRowsAndKeepsFirstTime() {
        recipient("Reader@Example.com", "ses-batch-1");
        recipient("other@example.com", "ses-batch-1");
        log("Reader@Example.com", "ses-batch-1");
        log("reader@example.com", "ses-batch-2");

        assertEquals(1, recipients.markDelivered("ses-batch-1", "reader@example.com", FIRST));
        assertEquals(1, emailLogs.markDelivered("ses-batch-1", "reader@example.com", FIRST));
        // 同一則事件重送：時間不被覆蓋
        recipients.markDelivered("ses-batch-1", "reader@example.com", FIRST.plusHours(1));
        emailLogs.markDelivered("ses-batch-1", "reader@example.com", FIRST.plusHours(1));

        assertEquals(FIRST.toInstant(), deliveredAt("campaign_recipient", "email", "Reader@Example.com").toInstant());
        assertNull(deliveredAt("campaign_recipient", "email", "other@example.com"));
        assertEquals("SENT", jdbc.queryForObject(
            "SELECT status FROM campaign_recipient WHERE email = 'Reader@Example.com'", String.class));
        assertEquals(FIRST.toInstant(), jdbc.queryForObject(
            "SELECT delivered_at FROM email_log WHERE provider_message_id = 'ses-batch-1'", OffsetDateTime.class)
            .toInstant());
        assertNull(jdbc.queryForObject(
            "SELECT delivered_at FROM email_log WHERE provider_message_id = 'ses-batch-2'", OffsetDateTime.class));
        assertEquals("sent", jdbc.queryForObject(
            "SELECT status FROM email_log WHERE provider_message_id = 'ses-batch-1'", String.class));
    }
}
