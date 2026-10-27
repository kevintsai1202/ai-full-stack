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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 待寄佇列的條件式 UPDATE 在真資料庫上的行為。
 *
 * <p><b>為什麼不能用 mock</b>：派送器與取消排程可能同時碰同一列，正確性完全仰賴
 * {@code WHERE status = 'PENDING'} 這類條件式 UPDATE 的「只有一方改得到」。
 * 這個保證只存在於 SQL 本身，mock repository 驗不到。</p>
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
class ScheduledMailRepositoryTest {

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
    private static final String TEST_DB = "survey_scheduled_mail_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    /** 測試用的「現在」 */
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 10, 27, 8, 0, 0, 0, ZoneOffset.UTC);

    /** 把連線三項交給 Spring context */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TEST_URL);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASS);
    }

    @Autowired ScheduledMailRepository queue;
    @Autowired EmailLogRepository emailLogs;

    /** 重建乾淨資料庫並套用全部 migration */
    @BeforeAll
    static void prepare() throws SQLException {
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS);
             Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + TEST_DB + " WITH (FORCE)");
            st.execute("CREATE DATABASE " + TEST_DB);
        } catch (SQLException e) {
            fail("""
                連不到專用測試容器（%s）。待寄佇列的競爭保證只存在於 SQL 本身，無法用 mock 取代。
                請先啟動容器：docker start survey-test-db
                原始錯誤：%s
                """.formatted(ADMIN_URL, e.getMessage()));
        }
        Flyway.configure().dataSource(TEST_URL, USER, PASS).load().migrate();
    }

    /** 每個測試前清空佇列與寄送記錄 */
    @BeforeEach
    void clean() {
        queue.deleteAll();
        emailLogs.deleteAll();
    }

    /** 寫入一筆預定在 sendAt 寄出的信 */
    private ScheduledMail enqueue(String to, OffsetDateTime sendAt) {
        return queue.save(new ScheduledMail(null, to, "主旨", "<p>內文</p>", sendAt));
    }

    /** 只撈 PENDING 且到期的列，依到期時間排序 */
    @Test
    void findDueIdsOnlyReturnsDuePending() {
        enqueue("later@example.com", NOW.plusMinutes(5)); // 還沒到期，不得被撈到
        ScheduledMail second = enqueue("second@example.com", NOW.minusMinutes(1));
        ScheduledMail first = enqueue("first@example.com", NOW.minusMinutes(2));
        ScheduledMail cancelled = enqueue("cancelled@example.com", NOW.minusMinutes(3));
        queue.cancel(cancelled.getId());

        assertEquals(List.of(first.getId(), second.getId()), queue.findDueIds(NOW, 10));
    }

    /** 同一列只能被認領一次，認領時嘗試次數 +1 */
    @Test
    void claimSucceedsOnlyOnce() {
        ScheduledMail row = enqueue("a@example.com", NOW);

        assertEquals(1, queue.claim(row.getId()));
        assertEquals(0, queue.claim(row.getId()));
        ScheduledMail claimed = queue.findById(row.getId()).orElseThrow();
        assertEquals(ScheduledMail.STATUS_SENDING, claimed.getStatus());
        assertEquals(1, claimed.getAttempts());
    }

    /** 派送器先認領後，取消就改不到（回 0）；尚未認領的才取消得了 */
    @Test
    void cancelLosesToClaimAndViceVersa() {
        ScheduledMail claimedFirst = enqueue("a@example.com", NOW);
        ScheduledMail cancelledFirst = enqueue("b@example.com", NOW);

        queue.claim(claimedFirst.getId());
        assertEquals(0, queue.cancel(claimedFirst.getId()));

        assertEquals(1, queue.cancel(cancelledFirst.getId()));
        assertEquals(0, queue.claim(cancelledFirst.getId()));
    }

    /** 送出成功、重試、失敗都只從 SENDING 轉換 */
    @Test
    void statusTransitionsFromSending() {
        ScheduledMail row = enqueue("a@example.com", NOW);
        assertEquals(0, queue.markSent(row.getId(), "m-0"), "未認領的列不得直接標為已寄");

        queue.claim(row.getId());
        assertEquals(1, queue.retryLater(row.getId(), "throttled", NOW.plusSeconds(30)));
        ScheduledMail retried = queue.findById(row.getId()).orElseThrow();
        assertEquals(ScheduledMail.STATUS_PENDING, retried.getStatus());
        assertEquals(NOW.plusSeconds(30).toInstant(), retried.getNextAttemptAt().toInstant());
        assertEquals(List.of(), queue.findDueIds(NOW, 10), "退避期間不得再被撈到");

        queue.claim(row.getId());
        assertEquals(1, queue.markSent(row.getId(), "ses-msg-1"));
        ScheduledMail sent = queue.findById(row.getId()).orElseThrow();
        assertEquals(ScheduledMail.STATUS_SENT, sent.getStatus());
        assertEquals("ses-msg-1", sent.getProviderMessageId());

        ScheduledMail failing = enqueue("b@example.com", NOW);
        queue.claim(failing.getId());
        assertEquals(1, queue.markFailed(failing.getId(), "rejected"));
        assertEquals(ScheduledMail.STATUS_FAILED, queue.findById(failing.getId()).orElseThrow().getStatus());
    }

    /** 待寄數只算 PENDING；卡在 SENDING 超過門檻的列另外計數 */
    @Test
    void countsPendingAndStuck() {
        enqueue("a@example.com", NOW);
        enqueue("b@example.com", NOW);
        ScheduledMail stuck = enqueue("c@example.com", NOW);
        queue.claim(stuck.getId());

        assertEquals(2, queue.countByStatus(ScheduledMail.STATUS_PENDING));
        // 剛認領的列 updated_at 是資料庫的 now()，以「未來」為門檻就會被算進去
        assertEquals(1, queue.countStuck(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1)));
        assertEquals(0, queue.countStuck(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10)));
    }

    /** 派送失敗回寫 email_log：只改對應參照與收件人（大小寫不敏感）、且仍是 sent／scheduled 的列 */
    @Test
    void markDeliveryFailedTargetsMatchingLogRow() {
        EmailLog target = emailLogs.save(new EmailLog("Reader@Example.com", "s", "campaign",
            "ses-batch-1", "sent", null, 1L));
        EmailLog otherRecipient = emailLogs.save(new EmailLog("other@example.com", "s", "campaign",
            "ses-batch-1", "sent", null, 1L));
        EmailLog otherBatch = emailLogs.save(new EmailLog("reader@example.com", "s", "campaign",
            "ses-batch-2", "sent", null, 1L));

        assertEquals(1, emailLogs.markDeliveryFailed("ses-batch-1", "reader@example.com", "rejected"));

        assertEquals("failed", emailLogs.findById(target.getId()).orElseThrow().getStatus());
        assertEquals("rejected", emailLogs.findById(target.getId()).orElseThrow().getError());
        assertEquals("sent", emailLogs.findById(otherRecipient.getId()).orElseThrow().getStatus());
        assertEquals("sent", emailLogs.findById(otherBatch.getId()).orElseThrow().getStatus());
    }
}
