package world.springai.survey.reader;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * V23 投票發點唯一約束：驗證 {@code uq_credit_txn_survey_vote_reward} partial unique
 * index 真的存在於 migration 中，且透過 {@code CreditTxnRepository}（Spring 管理）
 * 存取時撞鍵的例外會轉譯成 {@code DataIntegrityViolationException}——這是應用層
 * {@code existsByReaderIdAndSurveyFormKeyAndReason} 冪等檢查的最終防線，
 * 防止併發重複投票造成重複發點。
 *
 * <p>基底類別、@SpringBootTest 屬性設定與 reader 建立 helper 比照
 * {@code SurveyRewardConstraintTest}（V21 同型測試，同套件同基底）；
 * 因為要驗證的是「透過 Spring Data JPA 存取時例外型別是否正確」，
 * 額外比照 {@code ReferralIdempotencyTest} 的既有模式加上
 * {@code @SpringBootTest} 與 {@code @Autowired CreditTxnRepository}。</p>
 */
@SpringBootTest(properties = {
    // 資料庫已由 @BeforeAll 以 Flyway 建好，Spring 這邊不再跑一次
    "spring.flyway.enabled=false",
    // 順帶驗證 V23 之後 CreditTxn 的 entity 對應仍通得過啟動時的 validate
    "spring.jpa.hibernate.ddl-auto=validate"
})
class SurveyVoteRewardConstraintTest {

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
    private static final String TEST_DB = "survey_vote_reward_credit_txn_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    /** 把連線三項交給 Spring context，確保 @BeforeAll 建的資料庫與 Spring 用的是同一個 */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TEST_URL);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASS);
    }

    @Autowired
    CreditTxnRepository creditTxnRepository;

    /** 重建乾淨資料庫並套用全部 migration（含 V23，本測試的驗證對象） */
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
    private long insertReader(String email) throws SQLException {
        try (Connection c = DriverManager.getConnection(TEST_URL, USER, PASS);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "INSERT INTO reader (email, credits, referral_code) VALUES ('"
                 + email + "', 100, 'TEST" + System.nanoTime() + "') RETURNING id")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /**
     * 同一讀者同一問卷的第二筆 SURVEY_VOTE_REWARD 必須被
     * {@code uq_credit_txn_survey_vote_reward} 擋下——這是應用層
     * {@code existsByReaderIdAndSurveyFormKeyAndReason} 冪等檢查的最終防線，
     * 防止併發重複投票造成重複發點。
     */
    @Test
    void 同讀者同問卷第二筆SURVEY_VOTE_REWARD撞唯一約束() throws SQLException {
        long readerId = insertReader("survey-vote-dup@example.com");

        CreditTxn first = new CreditTxn(readerId, 5,
            CreditTxn.REASON_SURVEY_VOTE_REWARD, null, "投票獎勵", null);
        first.setSurveyFormKey("reader-poll");
        creditTxnRepository.saveAndFlush(first);

        CreditTxn dup = new CreditTxn(readerId, 5,
            CreditTxn.REASON_SURVEY_VOTE_REWARD, null, "投票獎勵", null);
        dup.setSurveyFormKey("reader-poll");
        assertThrows(DataIntegrityViolationException.class,
            () -> creditTxnRepository.saveAndFlush(dup),
            "同讀者同問卷的第二筆 SURVEY_VOTE_REWARD 必須被唯一索引擋下");
    }

    /**
     * 完整填答獎勵與投票獎勵是兩個獨立的 reason：同一讀者同一問卷可以各有一筆，
     * 兩條 partial unique index 不得互相干擾。
     */
    @Test
    void 填答獎勵與投票獎勵可並存() throws SQLException {
        long readerId = insertReader("survey-both-rewards@example.com");

        CreditTxn vote = new CreditTxn(readerId, 5,
            CreditTxn.REASON_SURVEY_VOTE_REWARD, null, "投票獎勵", null);
        vote.setSurveyFormKey("reader-poll");
        creditTxnRepository.saveAndFlush(vote);

        CreditTxn full = new CreditTxn(readerId, 20,
            CreditTxn.REASON_SURVEY_REWARD, null, "填答獎勵", null);
        full.setSurveyFormKey("reader-poll");
        assertDoesNotThrow(() -> creditTxnRepository.saveAndFlush(full),
            "兩種發點原因各自獨立防重發，同問卷應可並存");
    }
}
