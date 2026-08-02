package world.springai.survey.promo;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * I4 修正：驗證 {@code uq_credit_txn_promo_refund} partial unique index 真的存在於 migration 中。
 *
 * <p>本測試<b>不</b>經過 {@code PromoProposalService}，全部 SQL 由本檔手寫直接送出——
 * 驗的是「V19 是否真的建了這個 UNIQUE 約束」這個資料庫層事實，作為應用層
 * {@code existsByPromoProposalIdAndReason} 冪等檢查的最終防線。比照
 * {@code UnlockConstraintTest} 的既有模式（同一測試容器，獨立資料庫名稱）。</p>
 */
class PromoCreditTxnConstraintTest {

    private static String env(String name, String defaultValue) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }

    private static final String DB_HOST = env("MIGRATION_TEST_DB_HOST", "127.0.0.1");
    private static final String DB_PORT = env("MIGRATION_TEST_DB_PORT", "5433");
    private static final String USER = env("MIGRATION_TEST_DB_USER", "postgres");
    private static final String PASS = env("MIGRATION_TEST_DB_PASSWORD", "password");
    private static final String ADMIN_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/postgres";
    private static final String TEST_DB = "survey_promo_credit_txn_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

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

    /** 連不上專用測試容器時以明確訊息失敗，不靜默跳過 */
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

    /** 建一位讀者，回傳其 id */
    private long insertReader(Connection c, String email) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "INSERT INTO reader (email, credits, referral_code) VALUES ('"
                 + email + "', 100, 'TEST" + System.nanoTime() + "') RETURNING id")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** 建一筆已核准提案，回傳其 id */
    private long insertProposal(Connection c, long readerId, String title) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "INSERT INTO promo_proposal (reader_id, contact_name, contact_email, title, "
                 + "body_text, link_text, link_url, placement_quota, placement_used, "
                 + "unit_cost, status, pricing_type) "
                 + "VALUES (" + readerId + ", 'Test', 'test@example.com', '" + title + "', "
                 + "'Body', 'Click', 'https://example.com', 3, 1, 100, 'APPROVED', 'FREE') "
                 + "RETURNING id")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** 寫入一筆 credit_txn */
    private void insertCreditTxn(Connection c, long readerId, int delta, String reason,
                                 Long promoProposalId) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("INSERT INTO credit_txn (reader_id, delta, reason, promo_proposal_id) VALUES ("
                + readerId + ", " + delta + ", '" + reason + "', "
                + (promoProposalId == null ? "NULL" : promoProposalId) + ")");
        }
    }

    /**
     * 同一提案的第二筆 PROMO_REFUND 必須被 {@code uq_credit_txn_promo_refund} 擋下——
     * 這是應用層 {@code existsByPromoProposalIdAndReason} 冪等檢查的最終防線，
     * 防止併發雙擊 reject／archive 造成重複退點。
     */
    @Test
    void 同一提案第二筆PROMO_REFUND被唯一索引擋下() throws SQLException {
        try (Connection c = DriverManager.getConnection(TEST_URL, USER, PASS)) {
            long readerId = insertReader(c, "refund-dup@example.com");
            long proposalId = insertProposal(c, readerId, "重複退點提案");

            insertCreditTxn(c, readerId, 100, CreditTxn_REASON_PROMO_REFUND(), proposalId);

            assertThrows(SQLException.class,
                () -> insertCreditTxn(c, readerId, 100, CreditTxn_REASON_PROMO_REFUND(), proposalId),
                "同一提案的第二筆 PROMO_REFUND 必須被唯一索引擋下");
        }
    }

    /** 不同提案各自可以有一筆 PROMO_REFUND（確認 UNIQUE 是複合鍵，不是只鎖 reason） */
    @Test
    void 不同提案各自可有PROMO_REFUND() throws SQLException {
        try (Connection c = DriverManager.getConnection(TEST_URL, USER, PASS)) {
            long readerId = insertReader(c, "refund-multi@example.com");
            long p1 = insertProposal(c, readerId, "提案甲");
            long p2 = insertProposal(c, readerId, "提案乙");

            insertCreditTxn(c, readerId, 100, CreditTxn_REASON_PROMO_REFUND(), p1);
            insertCreditTxn(c, readerId, 100, CreditTxn_REASON_PROMO_REFUND(), p2);

            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT count(*) FROM credit_txn WHERE reason = '"
                     + CreditTxn_REASON_PROMO_REFUND() + "' AND promo_proposal_id IN (" + p1 + "," + p2 + ")")) {
                rs.next();
                org.junit.jupiter.api.Assertions.assertEquals(2, rs.getInt(1));
            }
        }
    }

    /** 借用 CreditTxn 的 reason 常數，避免字面量與生產程式碼失聯 */
    private static String CreditTxn_REASON_PROMO_REFUND() {
        return world.springai.survey.reader.CreditTxn.REASON_PROMO_REFUND;
    }
}
