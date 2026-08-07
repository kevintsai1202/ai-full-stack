package world.springai.survey.reader;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 邀請／問卷獲點排行榜（{@link AdminReaderService#rewardLeaderboard}）真實資料庫測試。
 *
 * <p>排行榜的核心是一條彙總查詢（依 reason 分桶加總、排序、取前 N），
 * 這種 SQL 行為無法用 mock repository 驗證——mock 只會回傳你餵它的答案，
 * 查詢寫錯（漏 reason、沒排除負項、排序反了）完全測不出來，
 * 因此比照 {@code FormSchemaServiceHomepageTest} 用獨立資料庫跑完整 migration。</p>
 */
@SpringBootTest(properties = {
    // 資料庫已由 @BeforeAll 以 Flyway 建好，Spring 這邊不再跑一次
    "spring.flyway.enabled=false"
})
class RewardLeaderboardTest {

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
    private static final String TEST_DB = "reward_leaderboard_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    /** 把連線三項交給 Spring context，確保 @BeforeAll 建的資料庫與 Spring 用的是同一個 */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TEST_URL);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASS);
    }

    @Autowired
    AdminReaderService service;

    @Autowired
    ReaderRepository readerRepository;

    @Autowired
    CreditTxnRepository creditTxnRepository;

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

    /** 連不上專用測試容器時以明確訊息失敗，不靜默跳過 */
    private static void requireTestDatabase() {
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS)) {
            // 連得上即可
        } catch (SQLException e) {
            fail("連不到專用測試容器（" + ADMIN_URL + "）。請先啟動：docker start survey-test-db");
        }
    }

    /** 建立讀者並寫入一批帳本交易 */
    private Reader readerWithTxns(String email, String code, Map<String, Integer> reasonToDelta) {
        Reader reader = readerRepository.save(new Reader(email, code));
        // note 帶上 email：uq_credit_txn_referral_note（V9）以 note 做 REFERRAL 冪等，
        // 共用同一個 note 會讓第二位讀者的 REFERRAL 交易被資料庫擋下
        reasonToDelta.forEach((reason, delta) ->
            creditTxnRepository.save(new CreditTxn(reader.getId(), delta, reason, null, "測試:" + email)));
        return reader;
    }

    /**
     * 排行榜依「邀請＋問卷」合計降冪；初始贈點、後台加點與扣點（負項）都不計入。
     *
     * <p>資料設計——
     * heavy：邀請 100＋20＋里程碑 50＋問卷 20＋投票 5（獎勵合計 195），另有不該計入的
     * SIGNUP_GRANT 300 與 READ -10；light：問卷 20（合計 20）；none：只有 SIGNUP_GRANT，
     * 不該出現在榜上。</p>
     */
    @Test
    void ranksByReferralPlusSurveyRewardsOnly() {
        Reader heavy = readerWithTxns("heavy@example.com", "HEAVY123", Map.of(
            CreditTxn.REASON_REFERRAL, 100,
            CreditTxn.REASON_REFERRAL_INVITEE, 20,
            CreditTxn.REASON_REFERRAL_MILESTONE, 50,
            CreditTxn.REASON_SURVEY_REWARD, 20,
            CreditTxn.REASON_SURVEY_VOTE_REWARD, 5,
            CreditTxn.REASON_SIGNUP_GRANT, 300,
            CreditTxn.REASON_READ, -10));
        Reader light = readerWithTxns("light@example.com", "LIGHT123", Map.of(
            CreditTxn.REASON_SURVEY_REWARD, 20));
        readerWithTxns("none@example.com", "NONE1234", Map.of(
            CreditTxn.REASON_SIGNUP_GRANT, 300));

        List<Map<String, Object>> board = service.rewardLeaderboard(10);

        assertEquals(2, board.size(), "只有拿過邀請／問卷獎勵的讀者上榜：" + board);
        Map<String, Object> first = board.get(0);
        assertEquals("heavy@example.com", first.get("email"));
        assertEquals(1, first.get("rank"));
        assertEquals(170L, first.get("referralCredits")); // 100+20+50
        assertEquals(25L, first.get("surveyCredits"));    // 20+5
        assertEquals(195L, first.get("totalCredits"));
        Map<String, Object> second = board.get(1);
        assertEquals("light@example.com", second.get("email"));
        assertEquals(2, second.get("rank"));
        assertEquals(0L, second.get("referralCredits"));
        assertEquals(20L, second.get("surveyCredits"));
        assertEquals(20L, second.get("totalCredits"));
    }

    /** limit 生效：只回前 N 名 */
    @Test
    void respectsLimit() {
        for (int i = 0; i < 3; i++) {
            readerWithTxns("limit-" + i + "@example.com", "LIMIT00" + i, Map.of(
                CreditTxn.REASON_REFERRAL, 100 - i));
        }
        List<Map<String, Object>> board = service.rewardLeaderboard(2);
        assertTrue(board.size() <= 2, "limit=2 不得回超過 2 筆：" + board.size());
    }
}
