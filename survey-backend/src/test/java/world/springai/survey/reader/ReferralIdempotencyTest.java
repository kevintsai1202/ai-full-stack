package world.springai.survey.reader;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import world.springai.survey.audience.SubscriptionConfirmedEvent;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 以真實 PostgreSQL 驗證邀請獎勵的冪等<b>由資料庫保證</b>（V9 的
 * {@code uq_credit_txn_referral_note}），以及捕捉點確實落在交易邊界之外。
 *
 * <p><b>為什麼一定要真實資料庫</b>：{@code ReferralServiceTest} 把 repository 全部
 * mock 掉，那裡「撞上唯一索引」是由 Mockito 假裝拋例外，索引本體是否真的存在於
 * 資料庫、述詞是否真的只涵蓋 {@code reason='REFERRAL'}，那份測試一個字都驗不到。
 * 這與 {@link UnlockConstraintTest} 存在的理由完全相同——併發正確性的本體在
 * 資料庫裡，不能只靠 mock 的回傳值來「驗證」。</p>
 *
 * <p><b>{@code ddl-auto=validate}（本檔刻意不用 {@code none}）</b>：V9 只新增索引，
 * 理論上不影響 entity 對應，但「理論上」不算驗證。本檔的資料庫已由 Flyway 套用
 * 全部 migration，因此 Spring context 啟動時的 validate 會真的比對一次
 * ——若 V9 意外影響了對應，這個 context 會直接起不來。</p>
 *
 * <p>連線與容器要求同 {@link UnlockConstraintTest}（同一個 {@code survey-test-db}
 * 容器、獨立資料庫名稱），連不上時以明確中文訊息失敗，<b>不靜默跳過</b>。</p>
 */
@SpringBootTest(properties = {
    // 資料庫已由 @BeforeAll 以 Flyway 建好，Spring 這邊不再跑一次
    "spring.flyway.enabled=false",
    // 刻意不是 none：順帶驗證 V9 之後 entity 對應仍通得過啟動時的 validate
    "spring.jpa.hibernate.ddl-auto=validate"
    // 連線三項刻意不寫在這裡：註解裡只能放字面常數，一旦寫死就沒有任何
    // 覆寫途徑（連環境變數都插不進去），別人的機器改不了 host／port／帳密。
    // 改由下面的 @DynamicPropertySource 提供，沿用與 MigrationSafetyTest／
    // UnlockConstraintTest 同一組 MIGRATION_TEST_DB_* 環境變數。
})
class ReferralIdempotencyTest {

    /** 取得環境變數，未設定或空字串時退回預設值；與 {@link UnlockConstraintTest} 逐字相同 */
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
    private static final String TEST_DB = "survey_referral_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    /**
     * 把連線三項交給 Spring context。
     *
     * <p>{@code @SpringBootTest(properties = ...)} 只吃註解裡的字面常數，
     * 寫死在那裡等於<b>沒有覆寫途徑</b>；{@code @DynamicPropertySource} 是註解字面值
     * 與「可由環境變數決定的值」之間唯一的橋。值來自上面那組 {@code static final}，
     * 所以 {@code @BeforeAll} 用的連線與 Spring 用的連線必然是同一個資料庫，
     * 不可能出現「測試資料建在 A、context 連到 B」的錯配。</p>
     */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TEST_URL);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASS);
    }

    private static final String REFERRER_EMAIL = "host@referral-test.example";
    private static final String REFERRAL_CODE = "REFTEST1";
    private static final String INVITEE_EMAIL = "invitee@referral-test.example";
    private static final String SECOND_INVITEE_EMAIL = "invitee2@referral-test.example";

    @Autowired ReferralService referralService;
    @Autowired ReferralRewardListener referralRewardListener;
    @Autowired CreditPolicy creditPolicy;
    @Autowired DataSource dataSource;

    /** 重建乾淨資料庫並套用<b>全部</b> migration（含 V9，本測試的驗證對象） */
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
                連不到專用測試容器（%s）。本測試驗證的是資料庫層的冪等防線
                （uq_credit_txn_referral_note），無法用 mock 取代，因此不能靜默跳過。
                請先啟動容器：
                  docker start survey-test-db
                若容器不存在：
                  docker run -d --name survey-test-db -e POSTGRES_PASSWORD=password \\
                    -p 5433:5432 pgvector/pgvector:pg18
                連線資訊可用 MIGRATION_TEST_DB_HOST／PORT／USER／PASSWORD 覆寫。
                """.formatted(ADMIN_URL));
        }
    }

    /**
     * 每個測試前把本測試自己的資料清乾淨並重建（只碰本測試建的列，不動其他資料）。
     *
     * <p>刪除順序受 {@code credit_txn.reader_id} 的外鍵約束限制：先帳本、後讀者。</p>
     */
    @BeforeEach
    void seed() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM credit_txn WHERE reader_id IN "
                + "(SELECT id FROM reader WHERE email LIKE '%@referral-test.example')");
            st.executeUpdate("DELETE FROM reader WHERE email LIKE '%@referral-test.example'");
            st.executeUpdate("DELETE FROM survey_response WHERE email LIKE '%@referral-test.example'");

            st.executeUpdate("INSERT INTO reader (email, credits, referral_code) VALUES ('"
                + REFERRER_EMAIL + "', 0, '" + REFERRAL_CODE + "')");
            // 被邀者的名單列：answers 的 _ref 就是歸因來源（spec §5.4）
            insertInvitee(st, INVITEE_EMAIL);
        }
    }

    /** 塞一筆帶推薦碼的已確認名單列 */
    private void insertInvitee(Statement st, String email) throws SQLException {
        st.executeUpdate("INSERT INTO survey_response (email, consent, answers) VALUES ('"
            + email + "', TRUE, '{\"_ref\": \"" + REFERRAL_CODE + "\"}'::jsonb)");
    }

    /** 推薦人目前餘額 */
    private int referrerCredits() throws SQLException {
        return queryInt("SELECT credits FROM reader WHERE email = '" + REFERRER_EMAIL + "'");
    }

    /** 推薦人的 REFERRAL 帳本筆數 */
    private int referralLedgerRows() throws SQLException {
        return queryInt("SELECT count(*) FROM credit_txn WHERE reason = 'REFERRAL' AND reader_id = "
            + "(SELECT id FROM reader WHERE email = '" + REFERRER_EMAIL + "')");
    }

    /** 單值整數查詢 */
    private int queryInt(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(), "查詢無結果：" + sql);
            return rs.getInt(1);
        }
    }

    /**
     * 索引本體必須存在，而且是<b>只涵蓋 REFERRAL 的部分唯一索引</b>。
     *
     * <p>直接讀 {@code pg_indexes} 而不是只看行為：行為測試可能因為別的原因
     * （例如某天有人加了 CHECK 約束）而通過，而本專案要保證的是「那個索引在」。
     * 同時斷言述詞裡帶著 {@code REFERRAL}——若有人把它「簡化」成
     * {@code UNIQUE (reason, note)}，SIGNUP_GRANT 的第二位讀者就會建不了帳。</p>
     */
    @Test
    void partialUniqueIndexExistsOnReferralNote() throws SQLException {
        String indexDef;
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public'"
                     + " AND tablename = 'credit_txn'"
                     + " AND indexname = 'uq_credit_txn_referral_note'")) {
            indexDef = rs.next() ? rs.getString(1) : null;
        }

        assertNotNull(indexDef, "資料庫裡沒有 uq_credit_txn_referral_note："
            + "邀請獎勵的冪等就只剩程式端的 check-then-act，併發會重複發獎");
        assertTrue(indexDef.contains("UNIQUE"), "不是唯一索引：" + indexDef);
        assertTrue(indexDef.contains("note"), "不是建在 note 上：" + indexDef);
        assertTrue(indexDef.contains("REFERRAL"),
            "不是只涵蓋 REFERRAL 的部分索引：其他 reason 的 note 本來就會重複"
                + "（SIGNUP_GRANT 每位讀者都是同一句、READ 是文章主旨），"
                + "全表唯一會擋掉日常操作。實際述詞：" + indexDef);
    }

    /**
     * {@code reader.referred_by} 必須有索引，而且是排除 NULL 的部分索引。
     *
     * <p><b>為什麼這件事值得一條測試</b>：{@code /r/invite} 是登入讀者可以任意重新
     * 整理的頁面，而邀請人數的其中一個來源是
     * {@code ReaderRepository.findInviteeEmailsByReferredBy}，它的 WHERE 條件就是這個欄位。
     * V7 只宣告了欄位、沒建索引（當時沒有查詢用到它），讀者數成長到數萬之後，
     * 每次重載 {@code /r/invite} 就是一次 {@code reader} 全表掃描。這種缺陷不會讓任何
     * 功能測試變紅，只會讓頁面慢慢變慢，所以必須用「索引在不在」直接釘住。</p>
     *
     * <p>同時斷言述詞排除 NULL：{@code referred_by} 只有透過邀請連結進來且已建帳的
     * 讀者才有值，絕大多數列是 NULL，把它們留在索引裡只是白佔空間與寫入成本。</p>
     */
    @Test
    void partialIndexExistsOnReaderReferredBy() throws SQLException {
        String indexDef;
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public'"
                     + " AND tablename = 'reader'"
                     + " AND indexname = 'idx_reader_referred_by'")) {
            indexDef = rs.next() ? rs.getString(1) : null;
        }

        assertNotNull(indexDef, "資料庫裡沒有 idx_reader_referred_by："
            + "邀請人數的其中一個來源（findInviteeEmailsByReferredBy）會全表掃描 reader，"
            + "而 /r/invite 是讀者可任意重載的頁面");
        assertTrue(indexDef.contains("referred_by"), "不是建在 referred_by 上：" + indexDef);
        assertTrue(indexDef.contains("IS NOT NULL"),
            "不是排除 NULL 的部分索引：referred_by 絕大多數列是 NULL，"
                + "把它們納入索引只是白佔空間與寫入成本。實際定義：" + indexDef);
    }

    /**
     * 首次發獎成功；<b>第二次必須被資料庫擋下，而且餘額與帳本一起不變</b>。
     *
     * <p>這一條同時守住三件事：</p>
     * <ul>
     *   <li>索引真的生效（拿掉 V9 的索引 → 第二次會成功 → 餘額變兩倍、帳本兩筆 → 變紅）；</li>
     *   <li>例外<b>沒有</b>在 {@code rewardFor} 的交易內被捕捉
     *       （若在交易內捕捉並正常回傳，提交時會改拋 {@code UnexpectedRollbackException}，
     *       型別不符 → 變紅）；</li>
     *   <li>核心不變式：撞鍵時整組回滾，不會「加了點卻沒寫帳本」或反之。</li>
     * </ul>
     */
    @Test
    void secondRewardIsRejectedByDatabaseAndLeavesBalanceAndLedgerUntouched() throws SQLException {
        int reward = creditPolicy.referralReward();
        assertTrue(reward > 0, "測試前提：邀請獎勵必須是正值（V7 種下 100）");

        assertEquals(ReferralService.RewardOutcome.REWARDED,
            referralService.rewardFor(INVITEE_EMAIL));
        assertEquals(reward, referrerCredits());
        assertEquals(1, referralLedgerRows());

        assertThrows(DataIntegrityViolationException.class,
            () -> referralService.rewardFor(INVITEE_EMAIL),
            "第二次發獎沒有被 uq_credit_txn_referral_note 擋下，或例外被交易內的"
                + "捕捉吞掉了（那會在提交時改拋 UnexpectedRollbackException）");

        assertEquals(reward, referrerCredits(),
            "撞冪等鍵時餘額被改動了：整個交易應該回滾");
        assertEquals(1, referralLedgerRows(),
            "撞冪等鍵時帳本多了一列：餘額與帳本必須一起不變");
    }

    /**
     * 從監聽器走完整條路：重複的確認訂閱事件不可拋出任何例外，且不重複發獎。
     *
     * <p>依 spec §5.4，{@code confirmByEmail} 對「早已確認過的人」仍回報 1 列，
     * 所以每次點擊舊確認信都會發出事件、都會走到這條路——它必須是安靜的成功路徑。</p>
     *
     * <p>破壞性驗證：把 {@code @Transactional}（預設 {@code REQUIRED}）加回
     * {@link ReferralRewardListener#onSubscriptionConfirmed} 並把
     * {@code rewardFor} 改成 {@code REQUIRED}，捕捉點就回到交易邊界之內，
     * 監聽器的 proxy 會在提交時拋 {@code UnexpectedRollbackException} → 本測試變紅。</p>
     */
    @Test
    void duplicateConfirmThroughListenerIsSilentAndPaysOnlyOnce() throws SQLException {
        int reward = creditPolicy.referralReward();
        SubscriptionConfirmedEvent event = new SubscriptionConfirmedEvent(INVITEE_EMAIL);

        referralRewardListener.onSubscriptionConfirmed(event);
        assertDoesNotThrow(() -> referralRewardListener.onSubscriptionConfirmed(event),
            "重複的確認事件讓例外逃出監聽器：公開端點會回 500，"
                + "「不論結果一律回相同的 200」這條安全性質就破了");

        assertEquals(reward, referrerCredits(), "重複的確認事件發了第二次獎");
        assertEquals(1, referralLedgerRows(), "重複的確認事件寫了第二筆帳本");
    }

    /** 不同被邀者各自都能發獎——索引是「一個被邀者一次」，不是「一位推薦人一次」 */
    @Test
    void differentInviteesAreEachRewarded() throws SQLException {
        int reward = creditPolicy.referralReward();
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            insertInvitee(st, SECOND_INVITEE_EMAIL);
        }

        assertEquals(ReferralService.RewardOutcome.REWARDED,
            referralService.rewardFor(INVITEE_EMAIL));
        assertEquals(ReferralService.RewardOutcome.REWARDED,
            referralService.rewardFor(SECOND_INVITEE_EMAIL));

        assertEquals(reward * 2, referrerCredits());
        assertEquals(2, referralLedgerRows());
    }

    /**
     * 在真實資料庫上驗聯集計數：<b>被邀者確認了訂閱但從未登入，人數就必須是 1</b>，
     * 而且他之後真的來登入（{@code reader} 列出現、{@code referred_by} 寫入）之後
     * 仍然只算一人。
     *
     * <p>這是預設設定（{@code referralReward()=100}）下最常見的情境，也是
     * 「只數 {@code referred_by}」那版實作最嚴重的缺陷所在：站方已經付了 100 點，
     * 頁面卻說「還沒有人透過你的連結完成訂閱」。{@code ReferralServiceTest} 用 mock
     * 驗過聯集的邏輯，這裡驗的是<b>兩支真實 SQL 查到的資料真的能對上</b>
     * ——尤其是「帳本 note 與 reader.email 存的是同一個正規化值」這個去重前提，
     * 那件事只有真實資料庫能證明。</p>
     */
    @Test
    void invitedCountCountsConfirmedInviteeBeforeAndAfterFirstLogin() throws SQLException {
        long referrerId = queryInt("SELECT id FROM reader WHERE email = '" + REFERRER_EMAIL + "'");

        // ① 確認訂閱：帳本寫入，但被邀者還沒有 reader 列（他從未登入）
        assertEquals(ReferralService.RewardOutcome.REWARDED,
            referralService.rewardFor(INVITEE_EMAIL));
        assertEquals(0, queryInt("SELECT count(*) FROM reader WHERE referred_by = " + referrerId),
            "測試前提：被邀者尚未登入，referred_by 這一邊應該完全沒有列");
        assertEquals(1, referralService.stats(referrerId).invitedCount(),
            "確認訂閱但未登入的被邀者沒被計入：站方已經付了點數，頁面卻會說還沒有人");

        // ② 被邀者首次登入：reader 列出現，referred_by 指向邀請人。同一個人不可變成兩人。
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO reader (email, credits, referral_code, referred_by)"
                + " VALUES ('" + INVITEE_EMAIL + "', 0, 'INVITEE1', " + referrerId + ")");
        }
        assertEquals(1, referralService.stats(referrerId).invitedCount(),
            "被邀者同時出現在帳本與 referred_by 時被算成兩人：聯集沒有去重");
    }

    /**
     * <b>其他 reason 的 note 重複必須照樣被允許</b>——這是「部分索引」的存在理由。
     *
     * <p>{@code SIGNUP_GRANT} 的 note 對每一位讀者都是同一句「首次登入初始贈點」，
     * {@code READ} 的 note 是文章主旨（同一篇被第二個人解鎖就重複）。
     * 若有人把索引改成 {@code UNIQUE (reason, note)} 或全表唯一，
     * 擋掉的不是濫用而是日常：第二位讀者建不了帳、第二個人解不了同一篇文章。</p>
     */
    @Test
    void duplicateNotesAreStillAllowedForOtherReasons() throws SQLException {
        long readerId = queryInt("SELECT id FROM reader WHERE email = '" + REFERRER_EMAIL + "'");

        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO credit_txn (reader_id, delta, reason, note) VALUES "
                + "(" + readerId + ", 300, 'SIGNUP_GRANT', '首次登入初始贈點'),"
                + "(" + readerId + ", 300, 'SIGNUP_GRANT', '首次登入初始贈點')");
            st.executeUpdate("INSERT INTO credit_txn (reader_id, delta, reason, note) VALUES "
                + "(" + readerId + ", -10, 'READ', '同一篇文章主旨'),"
                + "(" + readerId + ", -10, 'READ', '同一篇文章主旨')");
        }

        assertEquals(2, queryInt("SELECT count(*) FROM credit_txn WHERE reason = 'SIGNUP_GRANT'"
            + " AND note = '首次登入初始贈點' AND reader_id = " + readerId));
        assertEquals(2, queryInt("SELECT count(*) FROM credit_txn WHERE reason = 'READ'"
            + " AND note = '同一篇文章主旨' AND reader_id = " + readerId));
    }
}
