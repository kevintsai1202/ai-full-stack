package world.springai.survey.promo;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 以真實 PostgreSQL + Hibernate 驗證 reconcile 的交易語意：
 * - 部分失敗時整輪回滾（已 COMMIT 的版位也被回滾）
 * - 消失掃描不誤殺剛 COMMIT 的版位
 *
 * <p>mock 測試無法驗證這些，因為 Spring proxy 與 Hibernate persistence context
 * 完全被繞過，唯有真實資料庫與完整交易邊界才能釘住這些語意。</p>
 */
@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:5433/survey_promo_reconcile_test",
    "spring.datasource.username=postgres",
    "spring.datasource.password=password"
})
class PromoPlacementReconcileIntegrationTest {

    private static final String DB_HOST = "127.0.0.1";
    private static final String DB_PORT = "5433";
    private static final String USER = "postgres";
    private static final String PASS = "password";
    private static final String ADMIN_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/postgres";
    private static final String TEST_DB = "survey_promo_reconcile_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    @Autowired PromoPlacementService service;
    @Autowired DataSource dataSource;

    /** 重建乾淨資料庫並套用全部 migration */
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

    /** 連不上專用測試容器時以明確訊息失敗 */
    private static void requireTestDatabase() {
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS)) {
            // 連得上即可
        } catch (SQLException e) {
            fail("""
                連不到專用測試容器（%s）。本測試驗證的是 Hibernate 與 PostgreSQL
                的交易回滾與 persistence context 的語意，無法用 mock 取代。
                請先啟動容器：
                  docker start survey-test-db
                若容器不存在：
                  docker run -d --name survey-test-db -e POSTGRES_PASSWORD=password \\
                    -p 5433:5432 pgvector/pgvector:pg18
                """.formatted(ADMIN_URL));
        }
    }

    /** 每個測試前重建讀者與提案資料 */
    @BeforeEach
    void seed() throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            // 清空測試數據
            st.executeUpdate("DELETE FROM promo_placement");
            st.executeUpdate("DELETE FROM promo_proposal");
            st.executeUpdate("DELETE FROM reader WHERE email LIKE 'promo-test-%'");
            st.executeUpdate("DELETE FROM campaign WHERE slug LIKE 'promo-test-%'");
        }
    }

    /** 新增讀者 */
    private long createReader(String email) throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "INSERT INTO reader (email, credits, referral_code) VALUES ('" + email
                 + "', 100, 'TEST" + System.currentTimeMillis() + "')"
                 + " RETURNING id")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** 新增已核准提案 */
    private long createProposal(long readerId, String title, int quota) throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "INSERT INTO promo_proposal (reader_id, contact_name, contact_email, title, "
                 + "body_text, link_text, link_url, placement_quota, placement_used, "
                 + "unit_cost, status, pricing_type) "
                 + "VALUES (" + readerId + ", 'Test', 'test@example.com', '" + title + "', "
                 + "'Body', 'Click', 'https://example.com', " + quota + ", 0, 100, 'APPROVED', 'FREE')"
                 + " RETURNING id")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** 新增版位 */
    private long createPlacement(long proposalId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "INSERT INTO promo_placement (proposal_id, status) "
                 + "VALUES (" + proposalId + ", 'DRAFT')"
                 + " RETURNING id")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** 新增 campaign */
    private long createCampaign(String slug) throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "INSERT INTO campaign (subject, markdown, mode, recipient_count, status, "
                 + "tier, credit_cost, slug) "
                 + "VALUES ('Test Campaign', 'Content', 'publish', 0, 'draft', 'BASIC', 0, '" + slug + "')"
                 + " RETURNING id")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** 查詢 placement 的 placement_used */
    private int queryPlacementUsed(long proposalId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT placement_used FROM promo_proposal WHERE id = " + proposalId)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** 查詢 placement 的狀態與 campaign_id */
    private PlacementRow queryPlacement(long placementId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT status, campaign_id FROM promo_placement WHERE id = " + placementId)) {
            rs.next();
            return new PlacementRow(rs.getString(1), rs.getObject(2, Long.class));
        }
    }

    record PlacementRow(String status, Long campaignId) {}

    /**
     * ★ 案例 A：部分失敗回滾
     *
     * <p>兩個提案：P1(quota=1 未用) + P2(quota=1 已滿，placement_used=1)
     * 各一個 DRAFT 版位，markdown 同時含兩個連結。
     * reconcile 應拋 IllegalStateException，之後查 DB：
     * - P1 的 placement_used 仍為 0（不被消失掃描誤殺）
     * - 兩個版位皆仍 DRAFT、campaign_id 為 NULL（交易回滾）
     */
    @Test
    void 案例A_部分失敗時同輪已COMMIT的版位回滾() throws SQLException {
        long readerId = createReader("promo-test-a@example.com");
        long campaignId = createCampaign("promo-test-a");

        // P1: quota=1 未用
        long p1Id = createProposal(readerId, "提案1", 1);
        long pl1Id = createPlacement(p1Id);

        // P2: quota=1 已滿
        long p2Id = createProposal(readerId, "提案2", 1);
        long pl2Id = createPlacement(p2Id);
        // 手動設定 P2 已用完配額
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.executeUpdate("UPDATE promo_proposal SET placement_used = 1 WHERE id = " + p2Id);
        }

        // 嘗試同時對帳兩個版位（P1 會 COMMIT，P2 會因配額不足而拋例外）
        String markdown = "[A](/promo/c/" + pl1Id + "?rt=x) [B](/promo/c/" + pl2Id + "?rt=x)";
        assertThrows(IllegalStateException.class,
            () -> service.reconcile(campaignId, markdown),
            "配額不足應拋 IllegalStateException");

        // 驗證回滾：P1 的版位應仍 DRAFT、P1 的 placement_used 應仍為 0
        assertEquals(0, queryPlacementUsed(p1Id), "P1 placement_used 應被回滾到 0");
        PlacementRow pl1 = queryPlacement(pl1Id);
        assertEquals(PromoPlacement.STATUS_DRAFT, pl1.status(), "P1 版位應仍 DRAFT");
        assertEquals(null, pl1.campaignId(), "P1 版位的 campaign_id 應仍為 NULL");

        // 驗證 P2 也沒被改動
        PlacementRow pl2 = queryPlacement(pl2Id);
        assertEquals(PromoPlacement.STATUS_DRAFT, pl2.status(), "P2 版位應仍 DRAFT");
        assertEquals(null, pl2.campaignId(), "P2 版位的 campaign_id 應仍為 NULL");
    }

    /**
     * ★ 案例 B：消失掃描不誤殺
     *
     * <p>單一提案 P1(quota=2)，一個 DRAFT 版位。markdown 含該版位連結。
     * reconcile 成功後查 DB：版位 COMMITTED、placement_used=1。
     * 驗證剛 COMMIT 的版位沒有被同一輪的消失掃描誤判轉成 REMOVED。
     */
    @Test
    void 案例B_消失掃描不誤殺剛COMMITTED的版位() throws SQLException {
        long readerId = createReader("promo-test-b@example.com");
        long campaignId = createCampaign("promo-test-b");

        // P1: quota=2 未用
        long p1Id = createProposal(readerId, "提案1", 2);
        long pl1Id = createPlacement(p1Id);

        // 對帳
        String markdown = "[看](/promo/c/" + pl1Id + "?rt=x)";
        service.reconcile(campaignId, markdown);

        // 驗證：版位應 COMMITTED、campaign_id 應綁定、placement_used 應為 1
        PlacementRow pl1 = queryPlacement(pl1Id);
        assertEquals(PromoPlacement.STATUS_COMMITTED, pl1.status(),
            "版位應被 COMMIT");
        assertEquals(campaignId, pl1.campaignId(),
            "版位應綁定到對帳的 campaign");
        assertEquals(1, queryPlacementUsed(p1Id),
            "P1 的 placement_used 應被扣為 1");
    }
}
