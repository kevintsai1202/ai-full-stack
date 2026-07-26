package world.springai.survey.reader;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import world.springai.survey.newsletter.Campaign;
import world.springai.survey.newsletter.CampaignRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 以真實 PostgreSQL 執行<b>生產查詢本體</b> {@link ReaderRepository#deductCredits}，
 * 釘住它的 {@code WHERE credits >= :cost}——本系統<b>唯一會動錢的併發防線</b>。
 *
 * <p><b>為什麼非得再開一支測試（{@link UnlockConstraintTest} 不算覆蓋）</b>：
 * 那一支沒有 Spring context、沒有注入 repository，它手寫
 * {@code UPDATE reader SET credits = credits - 10 WHERE id = ? AND credits >= 10}
 * 再用 JDBC 送出——驗的是 PostgreSQL 對它自己寫的 SQL 的行為，對生產 JPQL 是同義反覆。
 * {@code UnlockServiceTest} 則把整個 repository mock 掉。實測結果（不是推測）：
 * 把 {@code deductCredits} 的 {@code and r.credits >= :cost} 整個刪掉，
 * 改動前的全套 501 條測試<b>維持全綠</b>。這支測試就是為了讓那個改動變紅而存在。</p>
 *
 * <p><b>失效情境</b>：兩個併發解鎖請求（<b>不同文章</b>，故 {@code uq_article_access}
 * 幫不上忙）都通過 {@code AccessDecisionService}／{@code UnlockService} 的餘額檢查
 * （{@code reader.getCredits() >= cost}）；第二道防線失效後兩次扣點都成功 →
 * {@code reader.credits} <b>變成負數</b>。而負餘額會讓 {@code credits >= cost}
 * 之後永遠為假——讀者連 0 點的提示都看不對，已扣掉的點數也拿不回來。全程無錯誤訊息。</p>
 *
 * <p><b>為什麼刻意不寫「兩條連線解鎖<u>同一篇</u>」的併發測試</b>：那個情境即使把
 * {@code WHERE} 條件刪掉也不會留下壞掉的狀態——{@code UnlockService.unlock} 先扣款、
 * 後 {@code saveAndFlush(ArticleAccess)}，第二個請求會撞上 {@code uq_article_access}
 * 而讓<b>扣款隨交易一起回滾</b>（防線①③接住了）。也就是說那樣的測試在防線②被拆掉時
 * 依然全綠，是一個假的驗證。真正只由 {@code WHERE credits >= :cost} 守住的，
 * 是「同一份餘額被兩篇<b>不同</b>文章的解鎖同時消費」——見
 * {@link #concurrentUnlockOfAnotherArticleCannotDriveBalanceNegative}。</p>
 *
 * <p><b>核心不變式</b>：{@code reader.credits} 恆等於該讀者 {@code credit_txn} 的總和。
 * 直接呼叫 {@code deductCredits} 的測試（那是 repository 層，不會自己寫帳本）
 * 一律自行補上對應的帳本列，並由 {@link #invariantMustHoldAfterEveryTest} 逐條檢查
 * ——包含「餘額不得為負」這一項。</p>
 *
 * <p>連線與容器要求同 {@link UnlockConstraintTest}（同一個 {@code survey-test-db} 容器、
 * 獨立資料庫名稱、{@code MIGRATION_TEST_DB_*} 可覆寫），連不上時以明確中文訊息失敗，
 * <b>不靜默跳過</b>。</p>
 */
@SpringBootTest(properties = {
    // 資料庫已由 @BeforeAll 以 Flyway 建好，Spring 這邊不再跑一次
    "spring.flyway.enabled=false",
    // 刻意不是 none：順帶驗證 entity 對應通得過啟動時的 validate
    "spring.jpa.hibernate.ddl-auto=validate",
    // 每一個 @SpringBootTest 的屬性組合都會被快取成獨立的 ApplicationContext，
    // 各自帶一個預設 10 條連線的 Hikari 池。全庫的真實資料庫測試加起來會撞上
    // PostgreSQL 的 max_connections=100（實測撞過：後面的測試連 admin 連線都拿不到，
    // 錯誤訊息卻是「連不到測試容器」，非常難查）。本測試同時最多只需要
    // 「交易內那一條 + 模擬併發的那一條 + 讀值的那一條」，給 3 就夠。
    "spring.datasource.hikari.maximum-pool-size=3",
    "spring.datasource.hikari.minimum-idle=1"
    // 連線三項刻意不寫在這裡：註解裡只能放字面常數，寫死就沒有任何覆寫途徑
    // （連環境變數都插不進去）。改由下面的 @DynamicPropertySource 提供，
    // 沿用與 MigrationSafetyTest／UnlockConstraintTest 同一組 MIGRATION_TEST_DB_*。
})
class UnlockDeductionPersistenceTest {

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
    private static final String TEST_DB = "survey_unlock_deduct_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    /**
     * 把連線三項交給 Spring context（作法與理由同 {@link ReferralIdempotencyTest}）。
     *
     * <p>值來自上面那組 {@code static final}，所以 {@code @BeforeAll} 用的連線與
     * Spring 用的連線必然是同一個資料庫，不可能出現「資料建在 A、context 連到 B」。</p>
     */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TEST_URL);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASS);
    }

    /** 訂閱者 email 是 PII，一律用不可能寄達的 .example 網域 */
    private static final String EMAIL = "buyer@unlock-deduct.example";
    /** 旁觀者：用來抓「WHERE 比對錯欄位」——它的欄位任何時候都不該被碰到 */
    private static final String BYSTANDER_EMAIL = "bystander@unlock-deduct.example";
    /** 本測試建立的兩篇 PREMIUM 文章 */
    private static final String SLUG_A = "deduct-article-a";
    private static final String SLUG_B = "deduct-article-b";
    /** 兩篇文章的單篇解鎖點數 */
    private static final int COST = 10;
    /**
     * 解鎖時傳給 {@code UnlockService} 的時間戳。
     *
     * <p>刻意截斷到微秒：PostgreSQL 的 {@code TIMESTAMPTZ} 只存到微秒且是四捨五入，
     * 帶奈秒的值回讀後不會逐位元相同，精確斷言就得改成容差——而容差會讓
     * 「{@code touchEngagement} 寫錯值」這類缺陷溜過去。</p>
     */
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-26T12:34:56.123456Z");

    @Autowired ReaderRepository readerRepository;
    @Autowired UnlockService unlockService;
    @Autowired CampaignRepository campaignRepository;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;

    /** 重建乾淨資料庫並套用全部 migration（需要 V7 的 reader/credit_txn/article_access 與 V8 的參與度欄位） */
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
                連不到專用測試容器（%s）。本測試執行的是生產查詢 deductCredits 本體
                （唯一會動錢的併發防線），無法用 mock 取代，因此不能靜默跳過。
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
     * 每個測試前把本測試自己的資料清乾淨並重建（只碰本測試建的列，不動其他資料）。
     *
     * <p>刪除順序受 {@code credit_txn.reader_id}／{@code article_access.reader_id} 的
     * 外鍵約束限制：先兩張子表、後 {@code reader}。</p>
     *
     * <p>初始餘額 300 <b>並同時寫一筆 {@code delta=+300} 的帳本列</b>：
     * 只設 {@code credits} 不寫帳本會在測試資料庫裡留下一列永久違反核心不變式的資料，
     * 而本專案的驗證腳本已經因為這樣做而製造過假陽性（見 spec §13 的驗證腳本問題）。</p>
     */
    @BeforeEach
    void seed() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM article_access WHERE reader_id IN "
                + "(SELECT id FROM reader WHERE email LIKE '%@unlock-deduct.example')");
            st.executeUpdate("DELETE FROM credit_txn WHERE reader_id IN "
                + "(SELECT id FROM reader WHERE email LIKE '%@unlock-deduct.example')");
            st.executeUpdate("DELETE FROM reader WHERE email LIKE '%@unlock-deduct.example'");
            st.executeUpdate("DELETE FROM survey_response WHERE email LIKE '%@unlock-deduct.example'");
            st.executeUpdate("DELETE FROM campaign WHERE slug IN ('" + SLUG_A + "', '" + SLUG_B + "')");

            insertCampaign(st, SLUG_A, "扣點測試文章 A");
            insertCampaign(st, SLUG_B, "扣點測試文章 B");
            st.executeUpdate("INSERT INTO reader (email, credits, referral_code) VALUES ('"
                + EMAIL + "', 300, 'DEDUCT01')");
            st.executeUpdate("INSERT INTO credit_txn (reader_id, delta, reason, note) "
                + "SELECT id, 300, 'SIGNUP_GRANT', '首次登入初始贈點' FROM reader WHERE email = '"
                + EMAIL + "'");
            // 名單中心的兩列：解鎖會 touchEngagement 打第一列，第二列（旁觀者）永遠不該被碰
            st.executeUpdate("INSERT INTO survey_response (email, consent, unsubscribed, source) "
                + "VALUES ('" + EMAIL + "', true, false, 'survey_form')");
            st.executeUpdate("INSERT INTO survey_response (email, consent, unsubscribed, source) "
                + "VALUES ('" + BYSTANDER_EMAIL + "', true, false, 'survey_form')");
        }
    }

    /** 建一篇已發布、單篇 {@link #COST} 點的 PREMIUM 文章 */
    private void insertCampaign(Statement st, String slug, String subject) throws SQLException {
        st.executeUpdate("INSERT INTO campaign "
            + "(subject, markdown, mode, recipient_count, status, tier, credit_cost, slug, published_at) "
            + "VALUES ('" + subject + "', '免費區\n\n<!--paywall-->\n\n受限區', "
            + "'publish', 0, 'published', 'PREMIUM', " + COST + ", '" + slug + "', "
            + "'2026-07-20T04:00:00Z')");
    }

    /**
     * ★ 每一條測試結束後，核心不變式與「餘額不得為負」都必須成立。
     *
     * <p>放在 {@code @AfterEach} 而不是各測試末尾，是為了讓它<b>無法被忘記</b>：
     * 日後新增的測試若扣了點卻沒寫帳本，或讓餘額掉到負值，這裡就會直接紅燈。</p>
     */
    @AfterEach
    void invariantMustHoldAfterEveryTest() throws SQLException {
        long credits = queryLong("SELECT credits FROM reader WHERE email = '" + EMAIL + "'");
        long ledger = queryLong("SELECT coalesce(sum(delta), 0) FROM credit_txn WHERE reader_id IN "
            + "(SELECT id FROM reader WHERE email = '" + EMAIL + "')");
        assertEquals(ledger, credits,
            "核心不變式破了：reader.credits(" + credits + ") 與 credit_txn 總和(" + ledger + ") 對不上");
        assertTrue(credits >= 0,
            "餘額變成負數（" + credits + "）：credits >= cost 之後永遠為假，讀者連 0 點的提示都看不對");
    }

    /** 本測試那位讀者的 id */
    private long readerId() throws SQLException {
        return queryLong("SELECT id FROM reader WHERE email = '" + EMAIL + "'");
    }

    /** 本測試那位讀者目前的餘額 */
    private int creditsInDb() throws SQLException {
        return (int) queryLong("SELECT credits FROM reader WHERE email = '" + EMAIL + "'");
    }

    /** 執行一個回傳單一數字的查詢 */
    private long queryLong(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** 讀某個 email 在名單中心的最後互動時間；NULL 時回 null */
    private OffsetDateTime lastEngagedAt(String email) throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT last_engaged_at FROM survey_response "
                 + "WHERE email = '" + email + "'")) {
            rs.next();
            return rs.getObject(1, OffsetDateTime.class);
        }
    }

    /**
     * 把餘額調成指定值，並讓帳本總和跟著相符。
     *
     * <p>測試需要「餘額恰好等於／小於成本」這種起始狀態，而直接 UPDATE 餘額會破壞
     * 核心不變式，所以一律連帳本一起補。</p>
     */
    private void setBalance(int credits) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            int affected = st.executeUpdate("UPDATE reader SET credits = " + credits
                + " WHERE email = '" + EMAIL + "'");
            if (affected != 1) {
                throw new IllegalStateException("調整餘額應影響 1 列，實際 " + affected);
            }
            st.executeUpdate("DELETE FROM credit_txn WHERE reader_id IN "
                + "(SELECT id FROM reader WHERE email = '" + EMAIL + "')");
            st.executeUpdate("INSERT INTO credit_txn (reader_id, delta, reason, note) "
                + "SELECT id, " + credits + ", 'ADMIN_GRANT', '測試起始餘額' FROM reader "
                + "WHERE email = '" + EMAIL + "'");
        }
    }

    /**
     * 補一筆帳本列，對應一次直接呼叫 {@code deductCredits} 造成的餘額變動。
     *
     * <p>{@code deductCredits} 是 repository 層方法，本身<b>不寫帳本</b>（寫帳本是
     * {@code UnlockService} 的職責）。直接測 repository 的條文若不補這一筆，
     * 測試自己就會破壞核心不變式。</p>
     */
    private void recordLedger(int delta) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            int affected = st.executeUpdate("INSERT INTO credit_txn (reader_id, delta, reason, note) "
                + "SELECT id, " + delta + ", 'READ', '對應測試中的 deductCredits' FROM reader "
                + "WHERE email = '" + EMAIL + "'");
            if (affected != 1) {
                throw new IllegalStateException("補帳本應影響 1 列，實際 " + affected);
            }
        }
    }

    /**
     * ★ 餘額不足時，生產查詢 {@code deductCredits} 必須回 0 列且餘額完全不變。
     *
     * <p>把 {@code and r.credits >= :cost} 刪掉，這裡會拿到 1 且餘額變成 {@code -5}
     * ——那正是本條存在的理由。</p>
     */
    @Test
    void deductCreditsRejectsInsufficientBalance() throws SQLException {
        setBalance(COST - 5);

        int affected = readerRepository.deductCredits(readerId(), COST);

        assertEquals(0, affected, "餘額不足時 deductCredits 不該有任何一列被更新");
        assertEquals(COST - 5, creditsInDb(), "餘額必須完全不變");
    }

    /** 餘額恰好等於成本時必須扣款成功並歸零（別把防線「修」成連合法扣款都擋掉） */
    @Test
    void deductCreditsAllowsExactBalance() throws SQLException {
        setBalance(COST);

        int affected = readerRepository.deductCredits(readerId(), COST);

        assertEquals(1, affected, "餘額恰好等於成本時必須扣款成功");
        assertEquals(0, creditsInDb(), "餘額應歸零而非變負");
        recordLedger(-COST);
    }

    /**
     * ★ 餘額歸零後再扣一次必須被拒絕——這是「餘額永遠不會變負」的最短證明。
     *
     * <p>兩次呼叫之間完全沒有併發，但這正是併發下的等價狀態：
     * 呼叫端讀到的餘額已經被另一筆交易花掉了。防線失效時第二次會回 1，
     * 餘額變成 {@code -10}，而 {@link #invariantMustHoldAfterEveryTest} 也會一起紅。</p>
     */
    @Test
    void secondDeductionAtZeroBalanceIsRejected() throws SQLException {
        setBalance(COST);
        long id = readerId();

        assertEquals(1, readerRepository.deductCredits(id, COST), "第一次扣款應成功");
        recordLedger(-COST);
        int second = readerRepository.deductCredits(id, COST);

        assertEquals(0, second, "餘額已為 0，第二次扣款必須回 0 列");
        assertEquals(0, creditsInDb(), "餘額不得被扣成負數");
    }

    /**
     * ★★ 併發情境的逐字重現：解鎖交易<b>已讀到 {@code credits=10}</b> 之後、
     * 扣款<b>之前</b>，同一份餘額被<b>另一篇文章</b>的解鎖花掉；本次解鎖必須失敗，
     * 且餘額不得變負。
     *
     * <p><b>為什麼是「另一篇文章」</b>：同一篇的併發解鎖會撞上
     * {@code uq_article_access}（防線①）而讓扣款隨交易回滾，那個情境即使拆掉
     * {@code WHERE credits >= :cost} 也不會壞——所以它驗不到防線②。
     * 不同文章共用同一份餘額，才是<b>只有</b>防線②守得住的情境。</p>
     *
     * <p><b>為什麼用 {@link TransactionTemplate} 而不是開兩條執行緒</b>：
     * 真執行緒的交錯不可控——若 A 完全跑完 B 才開始讀，B 會看到餘額 0 而回
     * {@code INSUFFICIENT_CREDITS}，於是<b>防線被拆掉時測試依然全綠</b>，
     * 是一個偶發的假驗證。這裡手動撐開一個交易，在裡面先讀出讀者
     * （entity 進入一級快取，等價於 {@code unlock} 內那次 {@code findById}），
     * 再用另一條連線提交扣款，最後才呼叫 {@code unlock}——它會加入同一個交易並
     * 直接命中快取裡那份 {@code credits=10} 的快照，因此<b>必然</b>通過餘額檢查
     * 而走到 {@code deductCredits}。交錯是 100% 確定的。</p>
     *
     * <p>防線在位時 {@code deductCredits} 回 0 列，{@code unlock} 拋
     * {@link UnlockService.UnlockUnavailableException}（它刻意不回報成
     * {@code INSUFFICIENT_CREDITS}：餘額檢查方才通過，這是真正的併發衝突）。
     * 防線被拆掉時它會回 1、正常回傳 {@code UNLOCKED} 並提交，
     * 餘額變成 {@code -10}、憑證與帳本都留下——這條 {@code assertThrows} 就是紅燈。</p>
     */
    @Test
    void concurrentUnlockOfAnotherArticleCannotDriveBalanceNegative() throws SQLException {
        setBalance(COST);
        long id = readerId();
        Campaign articleA = campaignRepository.findBySlug(SLUG_A).orElseThrow();

        assertThrows(UnlockService.UnlockUnavailableException.class, () ->
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                // ① 解鎖交易讀到的快照：credits=10（足以支付 A）
                Reader loaded = readerRepository.findById(id).orElseThrow();
                assertEquals(COST, loaded.getCredits());

                // ② 另一條連線（另一個分頁正在解鎖文章 B）把同一份餘額花掉並立即提交
                spendOnSeparateConnection(COST);

                // ③ 本次解鎖接續執行：findById 命中一級快取拿到 credits=10，
                //    餘額檢查必然通過，正確性只能來自 deductCredits 的受影響筆數
                unlockService.unlock(id, articleA, NOW);
            }),
            "併發扣款失敗時 unlock 必須拋 UnlockUnavailableException；"
                + "沒拋代表 deductCredits 的 WHERE credits >= :cost 沒有擋住，餘額已被扣成負數");

        assertEquals(0, creditsInDb(), "餘額應停在 0（被文章 B 花掉），不得再被扣成負數");
        assertEquals(0, queryLong("SELECT count(*) FROM article_access WHERE reader_id = " + id),
            "扣款失敗的解鎖不得留下憑證，否則讀者永久免費看到一篇沒付錢的文章");
    }

    /**
     * 以獨立連線（不參與目前交易）花掉餘額並立即提交，模擬另一篇文章的併發解鎖。
     *
     * <p>連帳本一起寫，讓核心不變式在模擬的那一端也成立——這是真的解鎖會做的事，
     * 少寫帳本會讓 {@link #invariantMustHoldAfterEveryTest} 對著測試自己造的假資料紅燈。</p>
     */
    private void spendOnSeparateConnection(int cost) {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            int affected = st.executeUpdate("UPDATE reader SET credits = credits - " + cost
                + " WHERE email = '" + EMAIL + "' AND credits >= " + cost);
            if (affected != 1) {
                throw new IllegalStateException("模擬併發扣點應影響 1 列，實際 " + affected);
            }
            st.executeUpdate("INSERT INTO credit_txn (reader_id, delta, reason, note) "
                + "SELECT id, " + (-cost) + ", 'READ', '模擬另一篇文章的併發解鎖' FROM reader "
                + "WHERE email = '" + EMAIL + "'");
        } catch (SQLException e) {
            throw new IllegalStateException("模擬併發扣點失敗", e);
        }
    }

    /**
     * ★ 解鎖成功路徑：餘額、憑證、帳本三者一致，且名單中心的
     * {@code last_engaged_at} 被寫成<b>精確的</b>那個時間戳。
     *
     * <p>這條同時是 {@code SurveyResponseRepository.touchEngagement} 在
     * <b>{@code unlock} 呼叫路徑</b>上的唯一覆蓋（四個呼叫端裡風險最高的一條——
     * 它與帳本寫入在同一個交易內）。斷言精確值而非只斷言 non-null：後者連
     * 「寫錯值」都抓不到，而寫錯值的參與度時間戳會讓名單評分與再行銷判斷全歪。</p>
     *
     * <p>旁觀者那一列必須維持 NULL：{@code touchEngagement} 的 WHERE 若比錯欄位
     * （例如漏掉 email 條件），整張名單的參與度時間戳會被一次刷掉。</p>
     */
    @Test
    void unlockWritesLedgerAndTouchesEngagementWithExactTimestamp() throws SQLException {
        long id = readerId();
        Campaign articleA = campaignRepository.findBySlug(SLUG_A).orElseThrow();

        UnlockService.Result result = unlockService.unlock(id, articleA, NOW);

        assertEquals(UnlockService.Outcome.UNLOCKED, result.outcome());
        assertEquals(COST, result.cost());
        assertEquals(300 - COST, result.credits(), "回傳餘額必須是扣款後重新讀取的權威值");
        assertEquals(300 - COST, creditsInDb(), "資料庫裡的餘額必須真的被扣");
        assertEquals(1, queryLong("SELECT count(*) FROM article_access WHERE reader_id = " + id
                + " AND campaign_id = " + articleA.getId() + " AND cost = " + COST),
            "解鎖憑證沒有被寫入，或成本記錯");
        assertEquals(1, queryLong("SELECT count(*) FROM credit_txn WHERE reader_id = " + id
                + " AND delta = " + (-COST) + " AND reason = 'READ'"),
            "帳本沒有留下這次扣點");

        OffsetDateTime engaged = lastEngagedAt(EMAIL);
        assertNotNull(engaged, "解鎖是高可靠參與度訊號，last_engaged_at 必須被更新");
        assertEquals(NOW.toInstant(), engaged.toInstant(),
            "last_engaged_at 不是傳進 unlock 的那個時間戳（寫錯值時只斷言 non-null 抓不到）");
        assertNull(lastEngagedAt(BYSTANDER_EMAIL),
            "touchEngagement 動到了別人的列，WHERE 沒有正確比對 email");
    }
}
