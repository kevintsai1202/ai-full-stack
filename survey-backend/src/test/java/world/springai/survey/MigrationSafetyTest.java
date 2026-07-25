package world.springai.survey;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * V7／V8 migration 的既有資料保全與 backfill 正確性測試。
 *
 * <p><b>為什麼需要真實 PostgreSQL</b>：本專案用到 jsonb 與 @&gt; 運算子，H2 不支援。
 * 而「既有訂閱名單不可清除」是硬約束（spec §4.0）——訂閱者的同意是他們親自點確認信
 * 給出的，清掉就只能重新徵求。這道防線不該靠人記得跑腳本，所以做成每次 mvn test
 * 都會執行的自動化測試。</p>
 *
 * <p><b>為什麼不用 Testcontainers</b>：本機 Docker Desktop 29.6.1（API 1.55）與
 * docker-java 的 npipe 客戶端不相容，會誤報「Could not find a valid Docker
 * environment」，即使 docker CLI 與 named pipe 的手動 HTTP 請求都正常。已實測
 * testcontainers 1.21.0、2.0.5 與明確指定 DOCKER_HOST 皆無效。改為直接連本機
 * 專用測試容器。</p>
 *
 * <p><b>環境前提</b>：容器 survey-test-db 必須在執行中（見下方連線失敗時的指引）。
 * 連不上時本測試會明確失敗而非靜默跳過——寧可紅燈也不要假綠燈。</p>
 *
 * <p>流程：重建乾淨的測試資料庫 → 只套用 V1–V6（模擬正式庫現況）→ 塞入代表性
 * 既有資料 → 套用 V7／V8 → 斷言既有資料逐列未變且 backfill 正確。</p>
 */
class MigrationSafetyTest {

    /** 專用測試容器的維護資料庫連線（用於重建測試資料庫） */
    private static final String ADMIN_URL = "jdbc:postgresql://127.0.0.1:5433/postgres";
    /** 測試資料庫名稱；每次執行都會重建，只有本測試使用 */
    private static final String TEST_DB = "survey_migration_test";
    /** 測試資料庫連線 */
    private static final String TEST_URL = "jdbc:postgresql://127.0.0.1:5433/" + TEST_DB;
    private static final String USER = "postgres";
    private static final String PASS = "password";

    /** 既有資料的指紋：email 與同意狀態的組合，用於證明這些欄位逐列未被改寫 */
    private static final String CHECKSUM_SQL = """
        SELECT md5(string_agg(email || ':' || consent || ':' || unsubscribed, ',' ORDER BY id))
          FROM survey_response
        """;

    /** migration 前的 survey_response 筆數 */
    private static int beforeCount;
    /** migration 前的既有資料指紋 */
    private static String beforeChecksum;

    /** 重建測試資料庫 → 套用 V1–V6 → 塞既有資料 → 記錄狀態 → 套用 V7／V8 */
    @BeforeAll
    static void applyMigrations() throws SQLException {
        requireTestDatabase();
        recreateTestDatabase();

        // 只套用到 V6，模擬正式資料庫目前的狀態
        Flyway.configure()
            .dataSource(TEST_URL, USER, PASS)
            .target(MigrationVersion.fromVersion("6"))
            .load()
            .migrate();

        // 三種代表性的既有名單：已確認訂閱、待確認匯入、已退訂；外加一筆既有 campaign
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("""
                INSERT INTO survey_response (email, consent, unsubscribed, source) VALUES
                  ('confirmed@example.com', TRUE,  FALSE, 'survey_form'),
                  ('pending@example.com',   FALSE, FALSE, 'exam'),
                  ('gone@example.com',      TRUE,  TRUE,  'survey_form')
                """);
            st.execute("""
                INSERT INTO campaign (subject, markdown, mode, recipient_count,
                                      accepted_count, failed_count, status)
                VALUES ('既有電子報', '# 內容', 'now', 1, 1, 0, 'sent')
                """);
        }

        beforeCount = queryInt("SELECT count(*) FROM survey_response");
        beforeChecksum = queryString(CHECKSUM_SQL);

        // 套用 V7／V8
        Flyway.configure()
            .dataSource(TEST_URL, USER, PASS)
            .load()
            .migrate();
    }

    /** 既有列一筆都不能少，email 與同意狀態一個字都不能變 */
    @Test
    void existingRowsAreUntouched() throws SQLException {
        assertEquals(beforeCount, queryInt("SELECT count(*) FROM survey_response"),
            "migration 後 survey_response 筆數改變");
        assertEquals(beforeChecksum, queryString(CHECKSUM_SQL),
            "migration 後 email／consent／unsubscribed 有變動");
    }

    /**
     * 已確認訂閱者必須被回填 last_engaged_at。
     *
     * <p>若不回填，階段 F 的參與度分級會因「已寄多期 + last_engaged_at 為 NULL」
     * 把老訂閱者整批判為 sunset 而停寄——資料沒少但收不到信，且要到下次發送才顯現。</p>
     */
    @Test
    void confirmedSubscribersAreBackfilled() throws SQLException {
        assertEquals(1, queryInt("""
            SELECT count(*) FROM survey_response
             WHERE consent = TRUE AND unsubscribed = FALSE AND last_engaged_at IS NOT NULL
            """), "已確認訂閱者未被回填 last_engaged_at");
    }

    /** 待確認與已退訂者刻意不回填，保持 NULL（回填會造出假的參與紀錄） */
    @Test
    void nonSubscribersAreNotBackfilled() throws SQLException {
        assertEquals(0, queryInt("""
            SELECT count(*) FROM survey_response
             WHERE (consent = FALSE OR unsubscribed = TRUE) AND last_engaged_at IS NOT NULL
            """), "未確認或已退訂者被誤回填");
    }

    /** V7 的五張新表都要建立 */
    @Test
    void newTablesAreCreated() throws SQLException {
        for (String table : new String[] {"app_setting", "reader", "credit_txn", "article_access", "login_token"}) {
            assertEquals(1, queryInt(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = '" + table + "'"),
                "資料表 " + table + " 未建立");
        }
    }

    /** 參數初始值要進去，且可安全重跑（ON CONFLICT DO NOTHING） */
    @Test
    void appSettingsAreSeeded() throws SQLException {
        assertEquals(8, queryInt("SELECT count(*) FROM app_setting"), "app_setting 初始值筆數不符");
        assertEquals("300", queryString(
            "SELECT value FROM app_setting WHERE setting_key = 'credit.signup_grant'"));
        assertEquals("10", queryString(
            "SELECT value FROM app_setting WHERE setting_key = 'credit.premium_cost'"));
    }

    /** 既有 campaign 應取得新欄位的預設值，不得為 NULL */
    @Test
    void existingCampaignGetsColumnDefaults() throws SQLException {
        assertEquals(0, queryInt("""
            SELECT count(*) FROM campaign
             WHERE tier IS DISTINCT FROM 'BASIC'
                OR credit_cost <> 0
                OR filter_levels IS DISTINCT FROM 'active'
            """), "既有 campaign 未取得新欄位的預設值");
    }

    /** PREMIUM 卻沒有解鎖成本必須被 CHECK 約束擋下——否則進階內容會全面免費外洩 */
    @Test
    void premiumWithoutCostIsRejected() {
        assertThrows(SQLException.class, () -> {
            try (Connection c = connect(); Statement st = c.createStatement()) {
                st.execute("""
                    INSERT INTO campaign (subject, markdown, mode, recipient_count,
                                          accepted_count, failed_count, status, tier, credit_cost)
                    VALUES ('壞資料', '# x', 'now', 0, 0, 0, 'sent', 'PREMIUM', 0)
                    """);
            }
        }, "tier=PREMIUM 且 credit_cost=0 應被 CHECK 約束拒絕");
    }

    /**
     * 確認本機測試容器可用；連不上時以明確指引失敗。
     *
     * <p>刻意不用 assumeTrue 跳過：這個測試守的是「既有訂閱名單不可清除」，
     * 靜默跳過等於讓防線失效卻顯示綠燈。</p>
     */
    private static void requireTestDatabase() {
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS)) {
            // 能連上即可
        } catch (SQLException e) {
            fail("""
                連不上本機測試資料庫（%s）。

                本測試驗證 migration 不會破壞既有訂閱名單（spec §4.0 的硬約束），
                不能靜默跳過。請先啟動專用測試容器：

                  docker start survey-test-db

                容器不存在時建立它（不要用 5432，那是別的專案的容器）：

                  docker run -d --name survey-test-db -e POSTGRES_PASSWORD=password ^
                    -p 5433:5432 pgvector/pgvector:pg18

                原始錯誤：%s""".formatted(ADMIN_URL, e.getMessage()));
        }
    }

    /**
     * 重建乾淨的測試資料庫。
     *
     * <p>WITH (FORCE) 會斷開既有連線（PostgreSQL 13+），避免前次執行殘留的連線
     * 導致 DROP 失敗。此處 DROP 的是本測試專屬的資料庫，與 spec §4.0 禁止
     * 對正式資料執行 DROP 並不衝突。</p>
     */
    private static void recreateTestDatabase() throws SQLException {
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS);
             Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + TEST_DB + " WITH (FORCE)");
            st.execute("CREATE DATABASE " + TEST_DB);
        }
    }

    /** 取得測試資料庫連線 */
    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(TEST_URL, USER, PASS);
    }

    /** 執行單值整數查詢 */
    private static int queryInt(String sql) throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(), "查詢無結果：" + sql);
            return rs.getInt(1);
        }
    }

    /** 執行單值字串查詢 */
    private static String queryString(String sql) throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(), "查詢無結果：" + sql);
            return rs.getString(1);
        }
    }
}
