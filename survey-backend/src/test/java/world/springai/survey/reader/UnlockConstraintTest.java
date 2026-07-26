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
 * <b>本測試<u>不</u>執行任何 repository 方法或 {@code @Query}</b>——它沒有 Spring context、
 * 沒有注入 {@code ReaderRepository}，全部 SQL 都是本檔<b>自己手寫</b>並用 JDBC
 * {@code Statement} 直接送出的。因此它驗的是 <b>PostgreSQL 對這些手寫敘述的行為</b>
 * （條件式 UPDATE 的受影響筆數語意、{@code uq_article_access} 這個 UNIQUE 約束真的存在
 * 於 migration 中），<b>不是</b> {@code ReaderRepository.deductCredits} 這支生產查詢。
 *
 * <p><b>務必不要把它讀成 {@code deductCredits} 的覆蓋</b>：它手寫的
 * {@code UPDATE reader SET credits = credits - 10 WHERE id = ? AND credits >= 10}
 * 與生產 JPQL 只是<b>長得像</b>。把 {@code ReaderRepository.deductCredits} 的
 * {@code and r.credits >= :cost} 整個刪掉，本測試仍然全綠——已實測。
 * 生產查詢本體的覆蓋在 {@link UnlockDeductionPersistenceTest}
 * （真實 PostgreSQL + {@code @Autowired ReaderRepository}／{@code UnlockService}）。
 * 本檔的類名與「連真資料庫」的設定曾經誤導過一整批工作，故此段不可刪。</p>
 *
 * <p><b>那它還有什麼價值</b>：① 釘住 migration 真的建了
 * {@code uq_article_access UNIQUE (reader_id, campaign_id)}（若 V7 漏掉這個約束，
 * 併發解鎖會扣兩次點，而任何走 JPA 的測試都看不出約束存不存在）；
 * ② 釘住 UNIQUE 是複合鍵而非只看 {@code reader_id}；
 * ③ 記錄「條件式 UPDATE 在餘額不足時回 0 列且不改值」這個資料庫層事實，
 * 作為 {@link UnlockDeductionPersistenceTest} 的參照基準。</p>
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
     * 餘額不足時，<b>手寫的</b>條件式 UPDATE 必須回 0 列且餘額完全不變。
     *
     * <p><b>這裡驗的是 PostgreSQL 的語意，不是 {@code deductCredits}</b>：
     * 本方法自己組出 SQL 字串再送出，生產查詢完全沒有參與。它的用途是把
     * 「{@code WHERE credits >= cost} 在餘額不足時回 0 列、且不會把餘額寫成負數」
     * 這個資料庫層事實記錄下來，作為 {@link UnlockDeductionPersistenceTest}
     * （那裡才是真的呼叫 {@code ReaderRepository.deductCredits}）的參照基準。
     * 生產 JPQL 的 WHERE 若被刪掉，本方法<b>不會</b>變紅。</p>
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

    /** 餘額剛好等於成本時，手寫的條件式 UPDATE 應成功並歸零（同上：驗資料庫，非生產查詢） */
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
