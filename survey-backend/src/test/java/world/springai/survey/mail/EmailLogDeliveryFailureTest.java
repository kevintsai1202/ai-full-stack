package world.springai.survey.mail;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * SES 佇列派送失敗時回寫 email_log 的 SQL（{@link EmailLogRepository#markDeliveryFailed}）在真資料庫上的行為。
 *
 * <p>連線與容器要求同 {@code SubscriptionConsentPersistenceTest}（{@code survey-test-db}、
 * 獨立資料庫名稱、{@code MIGRATION_TEST_DB_*} 可覆寫），連不上時明確失敗，不靜默跳過。</p>
 */
@SpringBootTest(properties = {
    // 資料庫已由 @BeforeAll 以 Flyway 建好，Spring 這邊不再跑一次
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=validate",
    // 限制連線池大小，避免多個快取 context 撞上 max_connections
    "spring.datasource.hikari.maximum-pool-size=2",
    "spring.datasource.hikari.minimum-idle=1"
})
class EmailLogDeliveryFailureTest {

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
    /** 獨立的資料庫名稱，不與其他測試共用 */
    private static final String TEST_DB = "survey_email_log_failure_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    /** 把連線三項交給 Spring context */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TEST_URL);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASS);
    }

    @Autowired EmailLogRepository emailLogs;

    /** 重建乾淨資料庫並套用全部 migration */
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

    /** 每個測試前清空寄送記錄 */
    @BeforeEach
    void clean() {
        emailLogs.deleteAll();
    }

    /** 只改對應參照與收件人（大小寫不敏感）、且仍是 sent／scheduled 的列 */
    @Test
    void markDeliveryFailedTargetsMatchingLogRow() {
        EmailLog target = emailLogs.save(new EmailLog("Reader@Example.com", "s", "campaign",
            "ses-batch-1", "sent", null, 1L));
        EmailLog otherRecipient = emailLogs.save(new EmailLog("other@example.com", "s", "campaign",
            "ses-batch-1", "sent", null, 1L));
        EmailLog otherBatch = emailLogs.save(new EmailLog("reader@example.com", "s", "campaign",
            "ses-batch-2", "sent", null, 1L));
        EmailLog alreadyCancelled = emailLogs.save(new EmailLog("reader@example.com", "s", "campaign",
            "ses-batch-1", "cancelled", null, 1L));

        assertEquals(1, emailLogs.markDeliveryFailed("ses-batch-1", "reader@example.com", "rejected"));

        assertEquals("failed", emailLogs.findById(target.getId()).orElseThrow().getStatus());
        assertEquals("rejected", emailLogs.findById(target.getId()).orElseThrow().getError());
        assertEquals("sent", emailLogs.findById(otherRecipient.getId()).orElseThrow().getStatus());
        assertEquals("sent", emailLogs.findById(otherBatch.getId()).orElseThrow().getStatus());
        assertEquals("cancelled", emailLogs.findById(alreadyCancelled.getId()).orElseThrow().getStatus());
    }
}
