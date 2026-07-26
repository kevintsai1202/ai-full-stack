package world.springai.survey.reader;

import org.flywaydb.core.Flyway;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 以真實 PostgreSQL + 真實 Hibernate 執行<b>生產查詢本體</b>
 * {@link ReaderRepository#updateVip}，釘住「後台授予／取消 VIP 絕不寫 {@code credits} 欄位」。
 *
 * <p><b>為什麼非得再開一支測試</b>：{@code updateVip} 的 javadoc 花一整段解釋
 * 「整列寫回會靜默覆蓋 {@code credits}」，卻<b>沒有任何測試守著它不退化回
 * {@code save(reader)}</b>。{@link AdminReaderTransactionTest} 的名字看起來像是覆蓋，
 * 但那裡 {@link ReaderRepository} 是 {@code @MockBean}——SQL 一句都不會執行。
 * {@code AdminReaderControllerTest} 用 {@code standaloneSetup}，同樣沒有資料庫。
 * 實測結果（不是推測）：把 {@code updateVip} 的 JPQL 改成什麼都不做，
 * 改動前的全套 501 條測試<b>維持全綠</b>。</p>
 *
 * <p><b>失效情境</b>：站方在後台按下「授予 VIP」，那個交易讀到 {@code credits=300}；
 * 同一時間讀者本人在另一個分頁解鎖一篇 10 點文章（{@code deductCredits} 把 DB 改成 290
 * 並寫入 {@code delta=-10} 的帳本列）。授予 VIP 的整列 UPDATE 提交時把 300 寫回去
 * ——扣點被靜默還原，帳本那筆 {@code -10} 卻留著，{@code reader.credits} 與
 * {@code sum(credit_txn)} 從此對不上，且沒有任何錯誤訊息，要等對帳才會發現。</p>
 *
 * <p><b>觀測手段</b>：Hibernate 的 {@link StatementInspector}（作法同
 * {@link ReaderLoginPersistenceTest}）。要釘住的是<b>實際送出的 SQL 文字</b>——
 * 只把 {@code save()} 刪掉、仍在受管理的 entity 上呼叫 setter 的「修法」，
 * 在 mock 測試裡會全綠，在真實環境裡卻一個字都沒修好（dirty check 會補一道全欄位 UPDATE）。</p>
 *
 * <p>連線與容器要求同 {@link UnlockConstraintTest}（同一個 {@code survey-test-db} 容器、
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
    "spring.datasource.hikari.maximum-pool-size=3",
    "spring.datasource.hikari.minimum-idle=1",
    // 攔截 Hibernate 實際送出的每一道 SQL，這是本測試的觀測手段
    "spring.jpa.properties.hibernate.session_factory.statement_inspector="
        + "world.springai.survey.reader.AdminVipPersistenceTest$SqlCapture"
    // 連線三項由 @DynamicPropertySource 提供（註解裡的字面值無法覆寫）
})
class AdminVipPersistenceTest {

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
    private static final String TEST_DB = "survey_admin_vip_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    /** 把連線三項交給 Spring context（理由同 {@link ReferralIdempotencyTest}） */
    @org.springframework.test.context.DynamicPropertySource
    static void datasourceProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TEST_URL);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASS);
    }

    /** 已存在帳戶的讀者；訂閱者 email 是 PII，用不可能寄達的 .example 網域 */
    private static final String EXISTING_EMAIL = "member@admin-vip.example";
    /** 尚無 reader 列的 email：授予 VIP 會先建帳戶（同一交易內 INSERT 後緊接 UPDATE 同一列） */
    private static final String FRESH_EMAIL = "newcomer@admin-vip.example";
    private static final int INITIAL_CREDITS = 300;
    /** 授予 VIP 的基準時間；截斷到微秒才能與 PostgreSQL 回讀值逐位元相同 */
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-26T09:00:00.500000Z");
    private static final int VIP_DAYS = 30;

    /**
     * Hibernate 的 SQL 攔截器：把每一道實際送出的敘述記下來供斷言。
     *
     * <p>必須是 public static 且有無參數建構子——Hibernate 依類名反射建立實例。</p>
     */
    public static class SqlCapture implements StatementInspector {

        /** 已攔截到的 SQL；Hibernate 可能在不同執行緒送出，故用執行緒安全的容器 */
        static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();

        @Override
        public String inspect(String sql) {
            STATEMENTS.add(sql);
            return sql;
        }
    }

    @Autowired AdminReaderService adminReaderService;
    @Autowired ReaderRepository readerRepository;
    /** 初始贈點是後台可調參數，斷言時取這裡而不是寫死數字 */
    @Autowired CreditPolicy creditPolicy;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;

    /** 重建乾淨資料庫並套用全部 migration（需要 V7 的 reader.tier / vip_expires_at） */
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
                連不到專用測試容器（%s）。本測試驗證的是 Hibernate 實際送出的 SQL
                （授予 VIP 不得整列寫回 credits），無法用 mock 取代，因此不能靜默跳過。
                請先啟動容器：
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
     * 每個測試前重建本測試自己的兩位讀者（只碰本測試建的列）。
     *
     * <p>{@link #EXISTING_EMAIL} 的餘額<b>連帳本一起寫</b>：只設 {@code credits}
     * 會在測試資料庫裡留下一列永久違反核心不變式的資料。</p>
     */
    @BeforeEach
    void seed() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM credit_txn WHERE reader_id IN "
                + "(SELECT id FROM reader WHERE email LIKE '%@admin-vip.example')");
            st.executeUpdate("DELETE FROM reader WHERE email LIKE '%@admin-vip.example'");
            st.executeUpdate("DELETE FROM survey_response WHERE email LIKE '%@admin-vip.example'");

            st.executeUpdate("INSERT INTO reader (email, credits, referral_code) VALUES ('"
                + EXISTING_EMAIL + "', " + INITIAL_CREDITS + ", 'ADMVIP01')");
            st.executeUpdate("INSERT INTO credit_txn (reader_id, delta, reason, note) "
                + "SELECT id, " + INITIAL_CREDITS + ", 'SIGNUP_GRANT', '首次登入初始贈點' "
                + "FROM reader WHERE email = '" + EXISTING_EMAIL + "'");
        }
        SqlCapture.STATEMENTS.clear();
    }

    /**
     * ★ 每一條測試結束後，本測試涉及的每位讀者都必須滿足核心不變式。
     *
     * <p>放在 {@code @AfterEach} 讓它無法被忘記——授予 VIP 若不小心動到餘額，
     * 這裡就會直接紅燈，不必依賴每條測試自己記得檢查。</p>
     */
    @AfterEach
    void invariantMustHoldAfterEveryTest() throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("""
                 SELECT r.email, r.credits, coalesce(sum(t.delta), 0)
                   FROM reader r LEFT JOIN credit_txn t ON t.reader_id = r.id
                  WHERE r.email LIKE '%@admin-vip.example'
                  GROUP BY r.email, r.credits
                 """)) {
            while (rs.next()) {
                // email 是 PII：這裡的值是本測試自己造的 .example 位址，不是真實訂閱者
                assertEquals(rs.getLong(3), rs.getLong(2),
                    "核心不變式破了：" + rs.getString(1) + " 的餘額與帳本總和對不上");
            }
        }
    }

    /** 已攔截到的、針對 reader 表的 UPDATE 敘述（空白正規化並轉小寫） */
    private static List<String> readerUpdates() {
        return SqlCapture.STATEMENTS.stream()
            .map(s -> s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim())
            .filter(s -> s.startsWith("update reader "))
            .toList();
    }

    /**
     * 取出 UPDATE 敘述的 SET 子句（{@code " set "} 與 {@code " where "} 之間）。
     *
     * <p>判斷「這道 UPDATE 有沒有寫 credits」必須只看 SET 子句：直接對整句
     * {@code contains("credits")} 會把 WHERE 裡出現的欄位也算進去
     * （{@code deductCredits} 的 WHERE 就有 {@code credits}），
     * 那種斷言會在無害的情況下誤報，而誤報久了就會有人把斷言拿掉。</p>
     */
    private static String setClauseOf(String normalizedSql) {
        int from = normalizedSql.indexOf(" set ");
        int to = normalizedSql.indexOf(" where ");
        return (from < 0 || to < 0 || to < from) ? "" : normalizedSql.substring(from + 5, to);
    }

    /** 讀某位讀者的一個欄位（文字形式）；NULL 時回 null */
    private String columnOf(String email, String column) throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT " + column + " FROM reader WHERE email = '"
                 + email + "'")) {
            rs.next();
            return rs.getString(1);
        }
    }

    /** 讀某位讀者的 vip_expires_at；NULL 時回 null */
    private OffsetDateTime vipExpiresAt(String email) throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT vip_expires_at FROM reader WHERE email = '"
                 + email + "'")) {
            rs.next();
            return rs.getObject(1, OffsetDateTime.class);
        }
    }

    /** 讀某位讀者的餘額 */
    private int creditsOf(String email) throws SQLException {
        return Integer.parseInt(columnOf(email, "credits"));
    }

    /**
     * ★ 授予 VIP 時送給資料庫的 {@code update reader} 敘述<b>不得包含 credits 欄位</b>，
     * 且 {@code tier} / {@code vip_expires_at} 必須真的被寫成預期值。
     *
     * <p>把 {@code AdminReaderService.grantVip} 改回
     * {@code reader.setTier(...); reader.setVipExpiresAt(...); readerRepository.save(reader);}，
     * 這條就會變紅——那正是它存在的理由。<b>只把 {@code save()} 刪掉而仍在受管理的
     * entity 上呼叫 setter，也一樣會變紅</b>（dirty check 會補一道全欄位 UPDATE）。
     * 反之把 {@code updateVip} 的 JPQL 改成什麼都不做，後半段的值斷言會變紅
     * ——別把缺陷「修」成空操作。</p>
     */
    @Test
    void grantVipNeverWritesCreditsColumn() throws SQLException {
        adminReaderService.grantVip(EXISTING_EMAIL, VIP_DAYS, NOW);

        List<String> updates = readerUpdates();
        assertFalse(updates.isEmpty(), "完全沒有發出 update reader：VIP 根本沒被寫入");
        assertTrue(updates.stream().noneMatch(s -> setClauseOf(s).contains("credits")),
            "授予 VIP 時發出了包含 credits 的整列 UPDATE，併發的扣點會被靜默還原：" + updates);
        assertTrue(updates.stream().anyMatch(s -> s.contains("tier") && s.contains("vip_expires_at")),
            "沒有任何一道 UPDATE 同時寫 tier 與 vip_expires_at：" + updates);

        assertEquals(Reader.TIER_VIP, columnOf(EXISTING_EMAIL, "tier"), "tier 沒有被寫成 VIP");
        OffsetDateTime expires = vipExpiresAt(EXISTING_EMAIL);
        assertNotNull(expires, "vip_expires_at 沒有被寫入，VIP 會被視為無限期");
        assertEquals(NOW.plusDays(VIP_DAYS).toInstant(), expires.toInstant(),
            "vip_expires_at 不是「授予當下 + 天數」");
        assertEquals(INITIAL_CREDITS, creditsOf(EXISTING_EMAIL), "授予 VIP 不得改動餘額");
    }

    /**
     * ★ 對<b>尚無 reader 列</b>的 email 授予 VIP：同一交易內先 INSERT 新讀者、
     * 緊接著對同一列執行 {@code updateVip}，兩件事都必須真的落地。
     *
     * <p>這是 {@code updateVip} 的 {@code flushAutomatically = true} 唯一真正在守的情境
     * （全檔唯一「在交易內、緊接同一列的 INSERT 之後」執行的 {@link Reader} 更新）。
     * 目前之所以安全只因 {@link Reader} 用 {@code GenerationType.IDENTITY}；
     * 若日後主鍵改成 {@code SEQUENCE} 而旗標又被拿掉，這道 UPDATE 會打在一列還不存在的
     * 資料上而回 0 列，{@code grantVip} 對 0 列的處置是拋 404——站方看到「查無此讀者」，
     * 帳戶卻真的被建出來了。這條測試就是那個組合的紅燈。</p>
     *
     * <p>順帶釘住：新建帳戶的初始贈點與帳本同步落地（核心不變式由
     * {@link #invariantMustHoldAfterEveryTest} 逐列檢查）。</p>
     *
     * <p><b>本路徑上確實有一道帶 {@code credits} 的整列 UPDATE，而它<u>不是</u>缺陷</b>
     * （實測發現，記錄在此以免下一位讀者誤判）：{@code ReaderAccountService
     * #createWithSignupGrant} 在 {@code save(newReader)}（INSERT）<b>之後</b>才
     * {@code reader.setCredits(grant)}，於是 dirty check 補一道
     * {@code update reader set credits=?,email=?,... where id=?}。那一列是<b>本交易剛剛
     * 建立</b>的——在提交之前其他交易看不到、也改不了它，所以不存在「靜默還原別人的
     * 併發變更」這件事（那才是本專案兩個 Critical 的機制）。因此本條只要求
     * <b>{@code updateVip} 自己送出的那一道</b> UPDATE 不含 {@code credits}，
     * 而不是全路徑一道都不許有。已存在帳戶的路徑（{@link #grantVipNeverWritesCreditsColumn}）
     * 才是那個嚴格版本，也是真正有覆蓋風險的那一條。</p>
     */
    @Test
    void grantVipForBrandNewEmailUpdatesTheFreshlyInsertedRow() throws SQLException {
        adminReaderService.grantVip(FRESH_EMAIL, VIP_DAYS, NOW);

        assertEquals(Reader.TIER_VIP, columnOf(FRESH_EMAIL, "tier"),
            "新建帳戶的 tier 沒有被寫成 VIP：UPDATE 可能打在一列還不存在的資料上");
        OffsetDateTime expires = vipExpiresAt(FRESH_EMAIL);
        assertNotNull(expires, "新建帳戶的 vip_expires_at 沒有被寫入");
        assertEquals(NOW.plusDays(VIP_DAYS).toInstant(), expires.toInstant());
        // 初始贈點必須真的落地（帳本一致性由 @AfterEach 的不變式檢查把關）。
        // 取自 CreditPolicy 而非寫死數字：贈點是後台可調參數，寫死會在別人改設定時假失敗
        assertEquals(creditPolicy.signupGrant(), creditsOf(FRESH_EMAIL),
            "新建帳戶的初始贈點沒有落地；授予 VIP 也不該把它覆蓋掉");

        // updateVip 自己送出的那一道（SET 只有 tier 與 vip_expires_at）必須存在且唯一。
        // 理由與「為什麼不是全路徑一道都不許有」見上方 javadoc。
        List<String> vipOnlyUpdates = readerUpdates().stream()
            .filter(s -> setClauseOf(s).contains("tier")
                && setClauseOf(s).contains("vip_expires_at")
                && !setClauseOf(s).contains("credits"))
            .toList();
        assertEquals(1, vipOnlyUpdates.size(),
            "應該恰有一道「只寫 tier 與 vip_expires_at」的 UPDATE（那是 updateVip）："
                + readerUpdates());

        // 本路徑上唯一帶 credits 的 UPDATE 必須是「剛 INSERT 的那一列被 dirty check
        // 整列寫回」（SET 裡連 email 與 referral_code 都在）。若出現一道
        // 「只寫 VIP 欄位卻又帶 credits」的敘述，那就是 updateVip 被改成整列寫回了。
        List<String> creditsBearing = readerUpdates().stream()
            .filter(s -> setClauseOf(s).contains("credits"))
            .toList();
        assertTrue(creditsBearing.stream().allMatch(s -> setClauseOf(s).contains("email")
                && setClauseOf(s).contains("referral_code")),
            "出現了非「整列材質化」的 credits 寫入，代表 updateVip 已退化成整列寫回："
                + creditsBearing);
    }

    /**
     * ★ 取消 VIP 必須把 {@code tier} 改回 FREE 並把 {@code vip_expires_at} 清成 NULL，
     * 且同樣不得碰 {@code credits}。
     *
     * <p>到期日留著會讓日後重新授予時在後台看到舊日期而誤判「這人還是 VIP」。</p>
     */
    @Test
    void revokeVipClearsTierAndExpiryWithoutTouchingCredits() throws SQLException {
        adminReaderService.grantVip(EXISTING_EMAIL, VIP_DAYS, NOW);
        SqlCapture.STATEMENTS.clear();

        adminReaderService.revokeVip(EXISTING_EMAIL);

        assertEquals(Reader.TIER_FREE, columnOf(EXISTING_EMAIL, "tier"));
        assertNull(vipExpiresAt(EXISTING_EMAIL), "取消 VIP 必須把到期日清成 NULL");
        assertEquals(INITIAL_CREDITS, creditsOf(EXISTING_EMAIL), "取消 VIP 不得改動餘額");
        assertTrue(readerUpdates().stream().noneMatch(s -> setClauseOf(s).contains("credits")),
            "取消 VIP 時發出了包含 credits 的整列 UPDATE：" + readerUpdates());
    }

    /**
     * ★★ 併發情境的逐字重現：授予 VIP 的交易<b>已讀到 {@code credits=300}</b> 之後、
     * 提交<b>之前</b>，讀者本人在另一個分頁解鎖文章把餘額扣成 290；
     * 授予 VIP 提交後餘額必須仍是 290。
     *
     * <p>作法同 {@link ReaderLoginPersistenceTest#concurrentDeductionSurvivesLogin}：
     * 用 {@link TransactionTemplate} 手動撐開一個交易，在裡面先讀出讀者
     * （entity 進入一級快取，等價於 {@code grantVip} 內那次 {@code findByEmailIgnoreCase}），
     * 再以另一條連線提交扣點，最後才呼叫 {@code grantVip}——它會加入同一個交易並
     * 直接命中快取裡那份 {@code credits=300} 的快照。</p>
     *
     * <p>整列寫回的版本在這裡會把 290 寫回 300，沒有任何錯誤訊息，而帳本上那筆
     * {@code -10} 仍然留著——{@link #invariantMustHoldAfterEveryTest} 會一起紅。</p>
     */
    @Test
    void concurrentDeductionSurvivesGrantVip() throws SQLException {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            // ① 授予 VIP 的交易讀到的快照：credits=300
            Reader loaded = readerRepository.findByEmailIgnoreCase(EXISTING_EMAIL).orElseThrow();
            assertEquals(INITIAL_CREDITS, loaded.getCredits());

            // ② 另一條連線（讀者本人的另一個分頁正在解鎖文章）扣 10 點並立即提交
            deductOnSeparateConnection(10);

            // ③ 授予 VIP 接續執行並在本交易結束時提交
            adminReaderService.grantVip(EXISTING_EMAIL, VIP_DAYS, NOW);
        });

        assertEquals(INITIAL_CREDITS - 10, creditsOfUnchecked(EXISTING_EMAIL),
            "授予 VIP 把併發扣掉的點數還原了：reader.credits 與 credit_txn 從此對不上");
        assertEquals(Reader.TIER_VIP, columnOf(EXISTING_EMAIL, "tier"),
            "VIP 仍必須真的被設定——修法不是靠「什麼都不做」來通過");
    }

    /** 以獨立連線（不參與目前交易）扣點並立即提交，連帳本一起寫以維持核心不變式 */
    private void deductOnSeparateConnection(int cost) {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            int affected = st.executeUpdate("UPDATE reader SET credits = credits - " + cost
                + " WHERE email = '" + EXISTING_EMAIL + "' AND credits >= " + cost);
            if (affected != 1) {
                throw new IllegalStateException("模擬併發扣點應影響 1 列，實際 " + affected);
            }
            st.executeUpdate("INSERT INTO credit_txn (reader_id, delta, reason, note) "
                + "SELECT id, " + (-cost) + ", 'READ', '模擬併發解鎖' FROM reader WHERE email = '"
                + EXISTING_EMAIL + "'");
        } catch (SQLException e) {
            throw new IllegalStateException("模擬併發扣點失敗", e);
        }
    }

    /** {@link #creditsOf} 的免受檢例外版本，供 lambda 之後的斷言使用 */
    private int creditsOfUnchecked(String email) {
        try {
            return creditsOf(email);
        } catch (SQLException e) {
            throw new IllegalStateException("讀取餘額失敗", e);
        }
    }
}
