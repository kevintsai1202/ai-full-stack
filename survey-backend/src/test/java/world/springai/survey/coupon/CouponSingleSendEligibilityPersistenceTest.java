package world.springai.survey.coupon;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 以真實 PostgreSQL 執行 {@link CouponRecipientService#findSingleSendRejection} 的查詢本體。
 *
 * <p><b>為什麼需要真實資料庫</b>：單筆寄券（問卷分析逐列寄券）跳過了券的命中名單驗證，
 * 改由「訂閱資格」決定能不能寄行銷信——退訂者收到信是合規事故，所以 SQL 本身
 * （最新同意事件的排序方向、email 正規化、{@code survey_response} 多列彙整）必須真的跑過，
 * 不能用 mock 取代。</p>
 *
 * <p><b>資格規則</b>（2026-10-06 使用者決策）：</p>
 * <ol>
 *   <li>名單中心最新 EMAIL 同意事件為 {@code UNSUBSCRIBED}，或 {@code survey_response}
 *       任一列已退訂 → 拒絕（已退訂）</li>
 *   <li>最新同意事件為 {@code CONFIRMED} → 可寄</li>
 *   <li>否則 {@code survey_response} 有該 email 且勾選同意 → 可寄（即使尚未點確認信）</li>
 *   <li>以上皆非 → 拒絕（未同意）</li>
 * </ol>
 *
 * <p>連線與容器要求同 {@code SubscriptionConsentPersistenceTest}（同一個
 * {@code survey-test-db} 容器、獨立資料庫名稱、{@code MIGRATION_TEST_DB_*} 可覆寫），
 * 連不上時以明確中文訊息失敗，<b>不靜默跳過</b>。</p>
 */
@SpringBootTest(properties = {
    // 資料庫已由 @BeforeAll 以 Flyway 建好，Spring 這邊不再跑一次
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=validate",
    // 限制連線池大小：理由見 SubscriptionConsentPersistenceTest（撞過 max_connections=100）
    "spring.datasource.hikari.maximum-pool-size=2",
    "spring.datasource.hikari.minimum-idle=1"
})
class CouponSingleSendEligibilityPersistenceTest {

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
    private static final String TEST_DB = "survey_coupon_single_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    /** 本測試造的 email 一律用不可能寄達的 .example 網域（訂閱者 email 是 PII） */
    private static final String DOMAIN = "@coupon-single-test.example";

    /** 把連線三項交給 Spring context（註解裡的字面值無法由環境變數覆寫） */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TEST_URL);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASS);
    }

    @Autowired CouponRecipientService recipientService;
    @Autowired JdbcTemplate jdbc;

    /** 重建乾淨資料庫並套用全部 migration */
    @BeforeAll
    static void prepare() throws SQLException {
        requireTestDatabase();
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS);
             Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + TEST_DB + " WITH (FORCE)");
            st.execute("CREATE DATABASE " + TEST_DB);
        }
        Flyway.configure().dataSource(TEST_URL, USER, PASS).load().migrate();
    }

    /** 連不上專用測試容器時以明確訊息失敗，並附上啟動指令 */
    private static void requireTestDatabase() {
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS)) {
            // 連得上即可
        } catch (SQLException e) {
            fail("""
                連不到專用測試容器（%s）。本測試執行的是單筆寄券的訂閱資格查詢本體
                （寄給退訂者是合規事故），無法用 mock 取代，因此不能靜默跳過。請先啟動容器：
                  docker start survey-test-db
                若容器不存在：
                  docker run -d --name survey-test-db -e POSTGRES_PASSWORD=password \\
                    -p 5433:5432 pgvector/pgvector:pg18
                連線資訊可用 MIGRATION_TEST_DB_HOST／PORT／USER／PASSWORD 覆寫。
                原始錯誤：%s
                """.formatted(ADMIN_URL, e.getMessage()));
        }
    }

    /** 每個測試前清掉本測試造的列（audience_consent 隨 person 串聯刪除） */
    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM survey_response WHERE email ILIKE ?", "%" + DOMAIN);
        jdbc.update("DELETE FROM audience_person WHERE email_normalized LIKE ?", "%" + DOMAIN);
    }

    /** 建一位名單中心人物，回傳 person id（email_normalized 依 schema 約束存小寫） */
    private long person(String email) {
        return jdbc.queryForObject("""
            INSERT INTO audience_person (email, email_normalized) VALUES (?, ?) RETURNING id
            """, Long.class, email, email.trim().toLowerCase());
    }

    /** 對人物追加一筆 EMAIL 同意事件；minutesAgo 越小代表越新 */
    private void consent(long personId, String status, int minutesAgo) {
        jdbc.update("""
            INSERT INTO audience_consent (person_id, channel, status, source_key, occurred_at)
            VALUES (?, 'EMAIL', ?, 'test', ?)
            """, personId, status, OffsetDateTime.now().minusMinutes(minutesAgo));
    }

    /** 建一列 survey_response（問卷分析原始資料表的來源） */
    private void response(String email, boolean consent, boolean unsubscribed) {
        jdbc.update("""
            INSERT INTO survey_response (email, name, consent, unsubscribed, source)
            VALUES (?, '測試', ?, ?, 'survey_form')
            """, email, consent, unsubscribed);
    }

    /** 斷言可寄 */
    private void assertEligible(String email, String why) {
        Optional<String> rejection = recipientService.findSingleSendRejection(email);
        assertTrue(rejection.isEmpty(), why + "（實際被拒：" + rejection.orElse("") + "）");
    }

    /** 斷言被拒且原因含指定字樣 */
    private void assertRejected(String email, String reasonContains, String why) {
        Optional<String> rejection = recipientService.findSingleSendRejection(email);
        assertTrue(rejection.isPresent(), why + "（實際可寄）");
        assertTrue(rejection.get().contains(reasonContains),
            why + "（原因應含「" + reasonContains + "」，實際：" + rejection.get() + "）");
    }

    /** 規則 2：最新同意事件為 CONFIRMED → 可寄（不需要在 survey_response 有資料） */
    @Test
    void confirmedSubscriberIsEligible() {
        long id = person("confirmed" + DOMAIN);
        consent(id, "CONFIRMED", 10);

        assertEligible("confirmed" + DOMAIN, "已確認訂閱者應可寄");
    }

    /**
     * ★ 規則 3：名單中心沒有任何同意事件，但問卷勾了同意 → 可寄。
     * 這是使用者「同意寄送：是但尚未點確認信也允許寄」決策真正生效的情境。
     */
    @Test
    void surveyConsentWithoutConsentEventIsEligible() {
        response("survey.only" + DOMAIN, true, false);

        assertEligible("survey.only" + DOMAIN, "問卷勾選同意者應可寄");
    }

    /** 規則 3 的另一形態：最新事件為 PENDING，但問卷勾了同意 → 可寄 */
    @Test
    void pendingWithSurveyConsentIsEligible() {
        long id = person("pending.agreed" + DOMAIN);
        consent(id, "PENDING", 10);
        response("pending.agreed" + DOMAIN, true, false);

        assertEligible("pending.agreed" + DOMAIN, "未點確認信但問卷勾選同意者應可寄");
    }

    /**
     * ★ 規則 1：名單中心最新事件為 UNSUBSCRIBED，即使問卷表沒標退訂、還勾著同意，也必須拒絕。
     * 同時驗證排序方向：較舊的 CONFIRMED 不能蓋過較新的 UNSUBSCRIBED。
     */
    @Test
    void latestUnsubscribeBeatsSurveyConsent() {
        long id = person("left" + DOMAIN);
        consent(id, "CONFIRMED", 20);
        consent(id, "UNSUBSCRIBED", 10);
        response("left" + DOMAIN, true, false);

        assertRejected("left" + DOMAIN, "已退訂", "最新事件為退訂者不得寄送");
    }

    /** 規則 1：問卷表任一列標記退訂，即使名單中心最新為 CONFIRMED 也拒絕 */
    @Test
    void surveyUnsubscribeBeatsConfirmed() {
        long id = person("survey.left" + DOMAIN);
        consent(id, "CONFIRMED", 10);
        response("survey.left" + DOMAIN, true, false);
        response("survey.left" + DOMAIN, true, true);

        assertRejected("survey.left" + DOMAIN, "已退訂", "問卷表標記退訂者不得寄送");
    }

    /** 最新事件為準：退訂後又透過確認連結重新確認（較新的 CONFIRMED）→ 可寄，與寄送名單口徑一致 */
    @Test
    void reconfirmAfterUnsubscribeIsEligible() {
        long id = person("came.back" + DOMAIN);
        consent(id, "UNSUBSCRIBED", 20);
        consent(id, "CONFIRMED", 10);

        assertEligible("came.back" + DOMAIN, "退訂後重新確認者應可寄");
    }

    /** 規則 4：最新為 PENDING 且問卷未勾同意 → 拒絕（未同意） */
    @Test
    void pendingWithoutSurveyConsentIsRejected() {
        long id = person("pending.no" + DOMAIN);
        consent(id, "PENDING", 10);
        response("pending.no" + DOMAIN, false, false);

        assertRejected("pending.no" + DOMAIN, "未同意", "未同意者不得寄送");
    }

    /** 規則 4：名單中心與問卷表都查無此人 → 拒絕（未同意） */
    @Test
    void unknownEmailIsRejected() {
        assertRejected("nobody" + DOMAIN, "未同意", "查無此人不得寄送");
    }

    /** email 比對不分大小寫、忽略前後空白：兩張表存的大小寫與呼叫端帶入的不同時仍要判得出來 */
    @Test
    void matchingIsCaseInsensitive() {
        response("Mixed.Case" + DOMAIN, true, false);
        long id = person("Upper.Person" + DOMAIN);
        consent(id, "UNSUBSCRIBED", 10);

        assertEligible("  MIXED.CASE" + DOMAIN.toUpperCase() + " ", "問卷表的同意不分大小寫應命中");
        assertRejected("upper.PERSON" + DOMAIN, "已退訂", "名單中心的退訂不分大小寫應命中");
    }
}
