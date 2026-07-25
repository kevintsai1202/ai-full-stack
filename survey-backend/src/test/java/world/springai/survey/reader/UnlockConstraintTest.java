package world.springai.survey.reader;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 以真實 PostgreSQL 驗證扣點的兩道資料庫層防線。
 *
 * <p><b>為什麼一定要真實資料庫</b>：{@code UnlockServiceTest} 把 repository
 * 全部 mock 掉了，所以 {@code deductCredits} 的 {@code WHERE credits >= :cost}
 * 條件與 {@code uq_article_access} 這兩道防線<b>從未被真的執行過</b>。
 * 它們是併發正確性的本體，不能只靠 mock 回傳值來「驗證」。</p>
 *
 * <p>連線資訊與 {@code MigrationSafetyTest} 相同（同一個專用測試容器），
 * 但使用獨立資料庫名稱避免兩者互相干擾。連不上時以明確中文訊息失敗，
 * <b>不靜默跳過</b>——寧可紅燈也不要假綠燈。</p>
 */
class UnlockConstraintTest {

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
    /** 獨立的資料庫名稱，不與 MigrationSafetyTest 共用 */
    private static final String TEST_DB = "survey_unlock_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    /** 重建乾淨資料庫並套用全部 migration（需要 V7 建立的 reader/article_access） */
    @BeforeAll
    static void prepare() throws SQLException {
        requireTestDatabase();
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS);
             Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + TEST_DB);
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
                連不到專用測試容器（%s）。本測試驗證的是資料庫層的併發防線，
                無法用 mock 取代，因此不能靜默跳過。請先啟動容器：
                  docker start survey-test-db
                若容器不存在：
                  docker run -d --name survey-test-db -e POSTGRES_PASSWORD=password \\
                    -p 5433:5432 pgvector/pgvector:pg18
                連線資訊可用 MIGRATION_TEST_DB_HOST／PORT／USER／PASSWORD 覆寫。
                """.formatted(ADMIN_URL));
        }
    }

    /** 建一位指定餘額的讀者，回傳其 id */
    private long insertReader(Connection c, String email, int credits) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("INSERT INTO reader (email, credits, referral_code) VALUES ('"
                + email + "', " + credits + ", '" + email.substring(0, 6).toUpperCase() + "')");
            try (ResultSet rs = st.executeQuery(
                    "SELECT id FROM reader WHERE email = '" + email + "'")) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** 讀取某讀者目前餘額 */
    private int creditsOf(Connection c, long readerId) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT credits FROM reader WHERE id = " + readerId)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /**
     * 餘額不足時條件式扣點必須回 0 列，且餘額完全不變。
     *
     * <p>這是防線本體：若 WHERE 條件寫錯（或被「簡化」掉），餘額會變成負數，
     * 而負餘額會讓 {@code credits >= cost} 永遠為假——讀者連 0 點的提示
     * 都看不對，且已經被扣掉的點數再也拿不回來。</p>
     */
    @Test
    void conditionalDeductRejectsInsufficientBalance() throws SQLException {
        try (Connection c = DriverManager.getConnection(TEST_URL, USER, PASS)) {
            long id = insertReader(c, "poor@example.com", 5);

            int affected;
            try (Statement st = c.createStatement()) {
                affected = st.executeUpdate(
                    "UPDATE reader SET credits = credits - 10 WHERE id = " + id + " AND credits >= 10");
            }

            assertEquals(0, affected, "餘額不足時不該有任何一列被更新");
            assertEquals(5, creditsOf(c, id), "餘額必須完全不變");
        }
    }

    /** 餘額剛好等於成本時應扣款成功並歸零 */
    @Test
    void conditionalDeductAllowsExactBalance() throws SQLException {
        try (Connection c = DriverManager.getConnection(TEST_URL, USER, PASS)) {
            long id = insertReader(c, "exact@example.com", 10);

            int affected;
            try (Statement st = c.createStatement()) {
                affected = st.executeUpdate(
                    "UPDATE reader SET credits = credits - 10 WHERE id = " + id + " AND credits >= 10");
            }

            assertEquals(1, affected);
            assertEquals(0, creditsOf(c, id), "餘額應歸零而非變負");
        }
    }

    /**
     * 同一讀者同一文章不可有第二筆解鎖紀錄。
     *
     * <p>{@code uq_article_access} 是「同一篇不重複扣點」的最終保證。
     * 若這個約束不存在（或被 migration 漏掉），併發解鎖會扣兩次點。</p>
     */
    @Test
    void uniqueConstraintPreventsDuplicateUnlock() throws SQLException {
        try (Connection c = DriverManager.getConnection(TEST_URL, USER, PASS)) {
            long id = insertReader(c, "dup@example.com", 300);

            try (Statement st = c.createStatement()) {
                st.execute("INSERT INTO article_access (reader_id, campaign_id, cost) VALUES ("
                    + id + ", 999, 10)");
            }

            assertThrows(SQLException.class, () -> {
                try (Statement st = c.createStatement()) {
                    st.execute("INSERT INTO article_access (reader_id, campaign_id, cost) VALUES ("
                        + id + ", 999, 10)");
                }
            }, "第二筆相同 (reader_id, campaign_id) 必須被 uq_article_access 擋下");
        }
    }

    /** 不同文章可以各自解鎖（確認 UNIQUE 是複合鍵而非只看 reader_id） */
    @Test
    void differentArticlesCanBothBeUnlocked() throws SQLException {
        try (Connection c = DriverManager.getConnection(TEST_URL, USER, PASS)) {
            long id = insertReader(c, "multi@example.com", 300);

            try (Statement st = c.createStatement()) {
                st.execute("INSERT INTO article_access (reader_id, campaign_id, cost) VALUES ("
                    + id + ", 1001, 10)");
                st.execute("INSERT INTO article_access (reader_id, campaign_id, cost) VALUES ("
                    + id + ", 1002, 10)");
            }

            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT count(*) FROM article_access WHERE reader_id = " + id)) {
                rs.next();
                assertEquals(2, rs.getInt(1));
            }
        }
    }
}
