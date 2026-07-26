package world.springai.survey.reader;

import org.flywaydb.core.Flyway;
import org.hibernate.resource.jdbc.spi.StatementInspector;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 以真實 PostgreSQL + 真實 Hibernate 驗證：<b>登入路徑絕不寫 {@code credits} 欄位</b>。
 *
 * <p><b>為什麼 mock 測不出來</b>：{@code ReaderAccountServiceTest} 把 repository 全部
 * mock 掉，只能驗證「有沒有呼叫 save()」。但這個缺陷的一半根本不在 {@code save()} 上——
 * {@code findByEmailIgnoreCase} 回傳的是<b>受管理的 entity</b>，光是
 * {@code reader.setLastLoginAt(now)} 這一行，Hibernate 的 dirty check 就會在提交時
 * 自己發出一道帶全欄位（含 {@code credits}）的 UPDATE，完全不需要呼叫 {@code save()}。
 * 換言之，只把 {@code save()} 刪掉、其餘照舊的「修法」在 mock 測試裡會全綠，
 * 在真實環境裡卻一個字都沒修好。要釘住的是<b>實際發出的 SQL</b>，只能用真資料庫。</p>
 *
 * <p><b>失效情境</b>：讀者在 A 分頁點 magic link，交易讀到 {@code credits=300}；
 * 同時 B 分頁的解鎖 POST 讓 {@code deductCredits} 把 DB 改成 290 並寫入 {@code delta=-10}
 * 的帳本列。A 提交時的整列 UPDATE 把 300 寫回去——扣點被靜默還原，帳本卻留著，
 * {@code reader.credits} 與 {@code sum(credit_txn)} 從此對不上，無錯誤、無日誌。</p>
 *
 * <p>連線與容器要求同 {@link UnlockConstraintTest}（同一個 {@code survey-test-db}
 * 容器、獨立資料庫名稱），連不上時以明確中文訊息失敗，<b>不靜默跳過</b>。</p>
 */
@SpringBootTest(properties = {
    // 資料庫已由 @BeforeAll 以 Flyway 建好，Spring 這邊不再跑一次
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:5433/survey_reader_login_test",
    "spring.datasource.username=postgres",
    "spring.datasource.password=password",
    // 攔截 Hibernate 實際送出的每一道 SQL，這是本測試的觀測手段
    "spring.jpa.properties.hibernate.session_factory.statement_inspector="
        + "world.springai.survey.reader.ReaderLoginPersistenceTest$SqlCapture"
})
class ReaderLoginPersistenceTest {

    private static final String DB_HOST = "127.0.0.1";
    private static final String DB_PORT = "5433";
    private static final String USER = "postgres";
    private static final String PASS = "password";
    private static final String ADMIN_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/postgres";
    /** 獨立的資料庫名稱，不與其他測試共用 */
    private static final String TEST_DB = "survey_reader_login_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    private static final String EMAIL = "login-persist@example.com";
    private static final int INITIAL_CREDITS = 300;

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

    @Autowired ReaderAccountService readerAccountService;
    @Autowired ReaderRepository readerRepository;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;

    /** 重建乾淨資料庫並套用全部 migration（需要 V7 建立的 reader 表） */
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
                連不到專用測試容器（%s）。本測試驗證的是 Hibernate 實際送出的 SQL，
                無法用 mock 取代，因此不能靜默跳過。請先啟動容器：
                  docker start survey-test-db
                若容器不存在：
                  docker run -d --name survey-test-db -e POSTGRES_PASSWORD=password \\
                    -p 5433:5432 pgvector/pgvector:pg18
                """.formatted(ADMIN_URL));
        }
    }

    /** 每個測試前重建這位讀者（只刪本測試自己建的那一列，不碰其他資料） */
    @BeforeEach
    void seedReader() throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM credit_txn WHERE reader_id IN "
                + "(SELECT id FROM reader WHERE email = '" + EMAIL + "')");
            st.executeUpdate("DELETE FROM reader WHERE email = '" + EMAIL + "'");
            st.executeUpdate("INSERT INTO reader (email, credits, referral_code) VALUES ('"
                + EMAIL + "', " + INITIAL_CREDITS + ", 'LOGINP01')");
        }
        SqlCapture.STATEMENTS.clear();
    }

    /** 讀取這位讀者目前的餘額 */
    private int creditsInDb() throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT credits FROM reader WHERE email = '" + EMAIL + "'")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** 已攔截到的、針對 reader 表的 UPDATE 敘述 */
    private static List<String> readerUpdates() {
        return SqlCapture.STATEMENTS.stream()
            .map(s -> s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim())
            .filter(s -> s.startsWith("update reader "))
            .toList();
    }

    /**
     * 登入既有讀者時，送給資料庫的 reader UPDATE <b>不得包含 credits 欄位</b>。
     *
     * <p>把 {@code ReaderAccountService} 的登入分支改回
     * {@code reader.setLastLoginAt(now); readerRepository.save(reader);}，
     * 這個測試就會變紅——那正是它存在的理由。只把 {@code save()} 刪掉而仍在
     * 受管理的 entity 上呼叫 setter，也一樣會變紅（dirty check 會補一道全欄位 UPDATE）。</p>
     */
    @Test
    void loginNeverWritesCreditsColumn() throws SQLException {
        readerAccountService.findOrCreate(EMAIL, OffsetDateTime.now());

        List<String> updates = readerUpdates();
        assertTrue(updates.stream().noneMatch(s -> s.contains("credits")),
            "登入時發出了包含 credits 的整列 UPDATE，併發的扣點會被靜默還原：" + updates);
        assertEquals(INITIAL_CREDITS, creditsInDb(), "登入不得改動餘額");
    }

    /** 登入仍必須真的把 last_login_at 寫進資料庫（別把缺陷「修」成什麼都不做） */
    @Test
    void loginStillPersistsLastLoginAt() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-26T10:00:00+08:00");

        readerAccountService.findOrCreate(EMAIL, now);

        Reader reloaded = readerRepository.findByEmailIgnoreCase(EMAIL).orElseThrow();
        assertNotNull(reloaded.getLastLoginAt(), "last_login_at 沒有被寫入");
        assertEquals(now.toInstant(), reloaded.getLastLoginAt().toInstant(),
            "last_login_at 不是這次登入的時間");
        assertTrue(readerUpdates().stream().anyMatch(s -> s.contains("last_login_at")),
            "沒有任何一道 UPDATE 碰到 last_login_at：" + readerUpdates());
    }

    /**
     * 併發情境的逐字重現：登入交易<b>已讀到 {@code credits=300}</b> 之後、提交<b>之前</b>，
     * 另一條連線把餘額扣成 290；登入提交後餘額必須仍是 290。
     *
     * <p>用 {@link TransactionTemplate} 手動撐開一個交易，在裡面先讀出讀者
     * （這模擬 {@code findOrCreate} 內的那次 SELECT，entity 進入一級快取），
     * 再以另一條連線提交扣點，最後才呼叫 {@code findOrCreate}——它會加入
     * 同一個交易並直接命中快取裡那份 {@code credits=300} 的快照。</p>
     *
     * <p>整列 UPDATE 的版本在這裡會把 290 寫回 300，沒有任何錯誤訊息，
     * 而帳本上那筆 {@code -10} 仍然留著。</p>
     */
    @Test
    void concurrentDeductionSurvivesLogin() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            // ① 登入交易讀到的快照：credits=300
            Reader loaded = readerRepository.findByEmailIgnoreCase(EMAIL).orElseThrow();
            assertEquals(INITIAL_CREDITS, loaded.getCredits());

            // ② 另一條連線（另一個分頁的解鎖 POST）把餘額扣成 290 並立即提交
            deductOnSeparateConnection(10);

            // ③ 登入流程接續執行並在本交易結束時提交
            readerAccountService.findOrCreate(EMAIL, OffsetDateTime.now());
        });

        assertEquals(INITIAL_CREDITS - 10, creditsInDbUnchecked(),
            "登入把併發扣掉的點數還原了：reader.credits 與 credit_txn 從此對不上");
    }

    /** 以獨立連線（不參與目前交易）扣點並立即提交，模擬另一個分頁的解鎖 */
    private void deductOnSeparateConnection(int cost) {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.executeUpdate("UPDATE reader SET credits = credits - " + cost
                + " WHERE email = '" + EMAIL + "'");
        } catch (SQLException e) {
            throw new IllegalStateException("模擬併發扣點失敗", e);
        }
    }

    /** {@link #creditsInDb()} 的免受檢例外版本，供 lambda 之後的斷言使用 */
    private int creditsInDbUnchecked() {
        try {
            return creditsInDb();
        } catch (SQLException e) {
            throw new IllegalStateException("讀取餘額失敗", e);
        }
    }
}
