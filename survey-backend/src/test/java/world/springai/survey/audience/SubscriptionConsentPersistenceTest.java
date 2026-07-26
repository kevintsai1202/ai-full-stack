package world.springai.survey.audience;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 以真實 PostgreSQL 執行<b>生產查詢本體</b>
 * {@link SurveyResponseRepository#unsubscribeByEmail} 與
 * {@link SurveyResponseRepository#confirmByEmail}。
 *
 * <p><b>為什麼必須有這一支（合規事項）</b>：退訂是法規要求，而在本測試出現之前
 * {@code unsubscribeByEmail} 這支查詢<b>從未被任何測試真的執行過</b>——
 * {@code SubscriptionControllerTest} 與 {@code SubscriptionRoutingTest} 都把
 * repository mock 掉，只驗證「有沒有以哪個引數呼叫哪個方法」。
 * 實測結果（不是推測）：把這兩支 JPQL 都加上 {@code and 1 = 0}（等於什麼都不做），
 * 改動前的全套 501 條測試<b>維持全綠</b>。</p>
 *
 * <p><b>失效情境</b>：讀者點了信裡的退訂連結、看到「已取消訂閱」的成功頁
 * （端點刻意<b>不</b>回報結果以避免變成名單查詢工具，所以連 UPDATE 影響 0 列都是靜默的），
 * 站方卻繼續寄信給他。{@code confirmByEmail} 失效則是反方向：沒有人能完成訂閱確認，
 * 每個新訂閱者都停在「未同意」而永遠收不到電子報。</p>
 *
 * <p><b>刻意涵蓋的兩件事</b>：① <b>大小寫不敏感</b>——信件裡的 email 是使用者當初
 * 自己填的，大小寫與資料庫裡的不見得相同（{@code lower(s.email) = lower(:email)}
 * 若被「簡化」成 {@code s.email = :email}，一部分讀者的退訂會靜默失效）；
 * ② <b>同一 email 的多列全部生效</b>——已在正式資料中實測到有人相隔一個月填了兩次問卷
 * （見 {@code findFirstByEmailIgnoreCaseOrderByCreatedAtDesc} 的註解），只更新一列
 * 等於漏掉另一列，而寄送名單查的是「有沒有任何一列同意且未退訂」。</p>
 *
 * <p>連線與容器要求同 {@code UnlockConstraintTest}（同一個 {@code survey-test-db} 容器、
 * 獨立資料庫名稱、{@code MIGRATION_TEST_DB_*} 可覆寫），連不上時以明確中文訊息失敗，
 * <b>不靜默跳過</b>。</p>
 */
@SpringBootTest(properties = {
    // 資料庫已由 @BeforeAll 以 Flyway 建好，Spring 這邊不再跑一次
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=validate",
    // 限制連線池大小：每個 @SpringBootTest 屬性組合都是一個獨立且被快取的
    // ApplicationContext，各帶一個預設 10 條連線的 Hikari 池，全庫加起來會撞上
    // PostgreSQL 的 max_connections=100（實測撞過，而錯誤訊息會偽裝成「連不到容器」）。
    "spring.datasource.hikari.maximum-pool-size=2",
    "spring.datasource.hikari.minimum-idle=1"
    // 連線三項由 @DynamicPropertySource 提供（註解裡的字面值無法覆寫）
})
class SubscriptionConsentPersistenceTest {

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
    private static final String TEST_DB = "survey_consent_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    /** 把連線三項交給 Spring context（註解裡的字面值無法由環境變數覆寫） */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TEST_URL);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASS);
    }

    /**
     * 目標訂閱者；資料庫裡刻意存成<b>混合大小寫</b>。
     *
     * <p>訂閱者 email 是 PII，一律用不可能寄達的 {@code .example} 網域，
     * 失敗訊息也只印本測試自己造的位址。</p>
     */
    private static final String STORED_EMAIL = "Twice.Filled@consent-test.example";
    /** 使用者從信件點連結時帶上的形式：全小寫（與資料庫裡存的大小寫不同） */
    private static final String LOWERCASE_EMAIL = "twice.filled@consent-test.example";
    /** 全大寫形式：驗證兩邊都套 lower() 而不是只套一邊 */
    private static final String UPPERCASE_EMAIL = "TWICE.FILLED@CONSENT-TEST.EXAMPLE";
    /** 旁觀者：任何時候都不該被碰到，用來抓「WHERE 條件失效而更新全表」 */
    private static final String BYSTANDER_EMAIL = "bystander@consent-test.example";
    /** 同一 email 在名單中的列數（實際資料中真的出現過同一人填兩次問卷） */
    private static final int ROWS_PER_EMAIL = 2;

    @Autowired SurveyResponseRepository repository;
    @Autowired DataSource dataSource;

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
                連不到專用測試容器（%s）。本測試執行的是退訂與確認訂閱的生產查詢本體
                （退訂是合規事項），無法用 mock 取代，因此不能靜默跳過。請先啟動容器：
                  docker start survey-test-db
                若容器不存在：
                  docker run -d --name survey-test-db -e POSTGRES_PASSWORD=password \\
                    -p 5433:5432 pgvector/pgvector:pg18
                連線資訊可用 MIGRATION_TEST_DB_HOST／PORT／USER／PASSWORD 覆寫。
                原始錯誤：%s
                """.formatted(ADMIN_URL, e.getMessage()));
        }
    }

    /**
     * 每個測試前重建名單（只碰本測試建的列）。
     *
     * <p>目標 email 建 {@link #ROWS_PER_EMAIL} 列、大小寫與呼叫端帶進來的不同，
     * 兩列的起始狀態都是「未同意、未退訂」——這正是兩支查詢各自要改變的狀態。</p>
     */
    @BeforeEach
    void seed() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM survey_response WHERE email ILIKE '%@consent-test.example'");
            for (int i = 0; i < ROWS_PER_EMAIL; i++) {
                st.executeUpdate("INSERT INTO survey_response (email, name, consent, unsubscribed, source) "
                    + "VALUES ('" + STORED_EMAIL + "', '第 " + (i + 1) + " 次填答', false, false, 'survey_form')");
            }
            st.executeUpdate("INSERT INTO survey_response (email, name, consent, unsubscribed, source) "
                + "VALUES ('" + BYSTANDER_EMAIL + "', '旁觀者', false, false, 'survey_form')");
        }
    }

    /** 某個布林欄位為 true 的列數（以 email 大小寫不敏感比對） */
    private int countWhere(String email, String column, boolean value) throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM survey_response "
                 + "WHERE lower(email) = lower('" + email + "') AND " + column + " = " + value)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** 旁觀者那一列的某個布林欄位 */
    private boolean bystanderFlag(String column) throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT " + column + " FROM survey_response "
                 + "WHERE email = '" + BYSTANDER_EMAIL + "'")) {
            rs.next();
            return rs.getBoolean(1);
        }
    }

    /**
     * ★ 退訂必須讓該 email 的<b>每一列</b>都變成已退訂，即使呼叫端帶進來的
     * 大小寫與資料庫裡存的不同。
     *
     * <p>把 {@code lower(s.email) = lower(:email)} 改成 {@code s.email = :email}，
     * 或給 WHERE 加上 {@code and 1 = 0}，這條就會變紅——那正是它存在的理由。
     * 失效時讀者會看到「已取消訂閱」的成功頁（端點刻意不回報結果），
     * 而站方繼續寄信給一位已明確表達不想再收信的人。</p>
     */
    @Test
    void unsubscribeAffectsEveryRowOfThatEmailCaseInsensitively() throws SQLException {
        int affected = repository.unsubscribeByEmail(LOWERCASE_EMAIL);

        assertEquals(ROWS_PER_EMAIL, affected,
            "退訂必須涵蓋該 email 的所有列（同一人可能填過多次問卷），且不分大小寫");
        assertEquals(ROWS_PER_EMAIL, countWhere(STORED_EMAIL, "unsubscribed", true),
            "資料庫裡仍有列沒被標記退訂");
        assertFalse(bystanderFlag("unsubscribed"),
            "退訂動到了別人的列：WHERE 條件失效，整張名單都被退訂了");
    }

    /** 全大寫也必須命中（確認 {@code lower()} 套在兩邊，不是只套參數那一邊） */
    @Test
    void unsubscribeAlsoMatchesUppercaseInput() throws SQLException {
        assertEquals(ROWS_PER_EMAIL, repository.unsubscribeByEmail(UPPERCASE_EMAIL));
        assertEquals(ROWS_PER_EMAIL, countWhere(STORED_EMAIL, "unsubscribed", true));
    }

    /** 退訂只碰 {@code unsubscribed} 一欄，不得順手改動同意狀態 */
    @Test
    void unsubscribeDoesNotTouchConsent() throws SQLException {
        repository.unsubscribeByEmail(LOWERCASE_EMAIL);

        assertEquals(ROWS_PER_EMAIL, countWhere(STORED_EMAIL, "consent", false),
            "退訂不該改動 consent：SET 寫錯欄位");
    }

    /**
     * ★ 確認訂閱必須讓該 email 的<b>每一列</b>都變成已同意，且不分大小寫。
     *
     * <p>失效＝沒有人能完成訂閱確認：{@code SubscriptionController.confirm} 只在
     * 受影響筆數 &gt; 0 時才發布 {@code SubscriptionConfirmedEvent}，所以連邀請獎勵
     * 也會一起停擺，而使用者看到的仍是固定的「訂閱已確認」成功頁。</p>
     */
    @Test
    void confirmAffectsEveryRowOfThatEmailCaseInsensitively() throws SQLException {
        int affected = repository.confirmByEmail(LOWERCASE_EMAIL);

        assertEquals(ROWS_PER_EMAIL, affected, "確認訂閱必須涵蓋該 email 的所有列，且不分大小寫");
        assertEquals(ROWS_PER_EMAIL, countWhere(STORED_EMAIL, "consent", true),
            "資料庫裡仍有列沒被標記為已同意");
        assertFalse(bystanderFlag("consent"),
            "確認訂閱動到了別人的列：WHERE 條件失效，整張名單都被標成已同意");
    }

    /** 全大寫也必須命中 */
    @Test
    void confirmAlsoMatchesUppercaseInput() throws SQLException {
        assertEquals(ROWS_PER_EMAIL, repository.confirmByEmail(UPPERCASE_EMAIL));
        assertEquals(ROWS_PER_EMAIL, countWhere(STORED_EMAIL, "consent", true));
    }

    /** 確認訂閱只碰 {@code consent} 一欄，不得順手清掉退訂狀態 */
    @Test
    void confirmDoesNotResurrectAnUnsubscribedRow() throws SQLException {
        repository.unsubscribeByEmail(LOWERCASE_EMAIL);

        repository.confirmByEmail(LOWERCASE_EMAIL);

        assertEquals(ROWS_PER_EMAIL, countWhere(STORED_EMAIL, "unsubscribed", true),
            "確認訂閱把退訂狀態清掉了：退訂是合規事項，不得被確認連結還原");
        // isSubscribed 要求「同意且未退訂」，所以已退訂者不會回到寄送名單
        assertFalse(repository.isSubscribed(LOWERCASE_EMAIL),
            "已退訂者不該因為同意狀態被寫成 true 而重新進入寄送名單");
    }

    /** 名單中查無此 email 時兩支查詢都必須回 0，呼叫端才能據此不發事件、不回報成功 */
    @Test
    void unknownEmailAffectsNothing() throws SQLException {
        String unknown = "nobody@consent-test.example";

        assertEquals(0, repository.unsubscribeByEmail(unknown));
        assertEquals(0, repository.confirmByEmail(unknown));
        assertEquals(ROWS_PER_EMAIL, countWhere(STORED_EMAIL, "consent", false),
            "查無此 email 的呼叫改動了其他人的列");
        assertTrue(countWhere(unknown, "unsubscribed", true) == 0);
    }
}
