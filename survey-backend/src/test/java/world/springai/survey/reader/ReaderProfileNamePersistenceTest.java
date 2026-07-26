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
import world.springai.survey.audience.SurveyResponse;
import world.springai.survey.audience.SurveyResponseRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 以真實 PostgreSQL + 真實 Hibernate 驗證：<b>改顯示名稱絕不寫
 * {@code unsubscribed} 與 {@code consent} 欄位</b>。
 *
 * <p><b>失效情境（這是合規事項）</b>：讀者在 A 分頁開著 {@code /r/me}，那次 SELECT
 * 讀到 {@code unsubscribed=false}；期間他在 B 分頁、或直接從信件裡的退訂連結點了退訂，
 * {@code /api/survey/unsubscribe} 讓 {@code unsubscribeByEmail} 把欄位改成 true。
 * 然後 A 分頁按下「儲存顯示名稱」——整列 UPDATE 把 {@code unsubscribed} 寫回
 * <b>false</b>。<b>退訂狀態被無聲還原</b>：沒有錯誤、沒有日誌，而站方會繼續寄信給
 * 一位已經明確表達不想再收信的人。同理 {@code confirmByEmail} 寫入的 {@code consent}
 * 也會被覆蓋回去。</p>
 *
 * <p><b>為什麼 mock 測不出來</b>：{@code ReaderPortalControllerTest} 與
 * {@code ReaderProfileTransactionTest} 都把 repository mock 掉，只能驗證「有沒有呼叫
 * 哪個方法」。但這個缺陷的一半根本不在 {@code save()} 上——
 * {@code findFirstByEmailIgnoreCaseOrderByCreatedAtDesc} 回傳的是<b>受管理的</b> entity，
 * 光是 {@code row.setName(...)} 這一行，Hibernate 的 dirty check 就會在提交時自己發出
 * 一道帶全欄位（含 {@code unsubscribed}、{@code consent}）的 UPDATE，
 * <b>完全不需要呼叫 {@code save()}</b>。換言之，只把 {@code save()} 刪掉、其餘照舊的
 * 「修法」在 mock 測試裡會全綠，在真實環境裡卻一個字都沒修好。要釘住的是
 * <b>實際發出的 SQL</b>，只能用真資料庫。</p>
 *
 * <p>作法比照 {@link ReaderLoginPersistenceTest}（同一個 {@code survey-test-db} 容器、
 * 獨立資料庫名稱、Hibernate {@code StatementInspector}）。連不上時以明確中文訊息失敗，
 * <b>不靜默跳過</b>。</p>
 */
@SpringBootTest(properties = {
    // 資料庫已由 @BeforeAll 以 Flyway 建好，Spring 這邊不再跑一次
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:5433/survey_profile_name_test",
    "spring.datasource.username=postgres",
    "spring.datasource.password=password",
    // 攔截 Hibernate 實際送出的每一道 SQL，這是本測試的觀測手段
    "spring.jpa.properties.hibernate.session_factory.statement_inspector="
        + "world.springai.survey.reader.ReaderProfileNamePersistenceTest$SqlCapture"
})
class ReaderProfileNamePersistenceTest {

    private static final String DB_HOST = "127.0.0.1";
    private static final String DB_PORT = "5433";
    private static final String USER = "postgres";
    private static final String PASS = "password";
    private static final String ADMIN_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/postgres";
    /** 獨立的資料庫名稱，不與其他測試共用 */
    private static final String TEST_DB = "survey_profile_name_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    private static final String EMAIL = "name-persist@example.com";

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

    @Autowired ReaderProfileService readerProfileService;
    @Autowired SurveyResponseRepository surveyResponseRepository;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;

    /** 重建乾淨資料庫並套用全部 migration（需要 V2 的 name 與 V8 的 last_engaged_at） */
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
                無法用 mock 取代（受管理 entity 的 dirty check 在 mock 測試中不存在），
                因此不能靜默跳過。請先啟動容器：
                  docker start survey-test-db
                若容器不存在：
                  docker run -d --name survey-test-db -e POSTGRES_PASSWORD=password \\
                    -p 5433:5432 pgvector/pgvector:pg18
                """.formatted(ADMIN_URL));
        }
    }

    /** 每個測試前重建這一列名單（只刪本測試自己建的那一列，不碰其他資料） */
    @BeforeEach
    void seedAudienceRow() throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM survey_response WHERE email = '" + EMAIL + "'");
            // 已確認訂閱、未退訂：這正是覆蓋風險最高的狀態
            st.executeUpdate("INSERT INTO survey_response (email, name, consent, unsubscribed, source) "
                + "VALUES ('" + EMAIL + "', '原本的名字', true, false, 'survey_form')");
        }
        SqlCapture.STATEMENTS.clear();
    }

    /** 已攔截到的、針對 survey_response 表的 UPDATE 敘述（空白正規化並轉小寫） */
    private static List<String> audienceUpdates() {
        return SqlCapture.STATEMENTS.stream()
            .map(s -> s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim())
            .filter(s -> s.startsWith("update survey_response "))
            .toList();
    }

    /** 讀取一個布林欄位目前的值 */
    private boolean flagInDb(String column) throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT " + column + " FROM survey_response WHERE email = '" + EMAIL + "'")) {
            rs.next();
            return rs.getBoolean(1);
        }
    }

    /** 讀取顯示名稱目前的值 */
    private String nameInDb() throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT name FROM survey_response WHERE email = '" + EMAIL + "'")) {
            rs.next();
            return rs.getString(1);
        }
    }

    /**
     * ★ 改名時送給資料庫的 {@code survey_response} UPDATE <b>不得包含
     * {@code unsubscribed} 或 {@code consent} 欄位</b>。
     *
     * <p>把 {@code ReaderProfileService.updateName} 改回
     * {@code row.get().setName(...); surveyResponseRepository.save(row.get());}，
     * 這個測試就會變紅——那正是它存在的理由。<b>只把 {@code save()} 刪掉而仍在受管理的
     * entity 上呼叫 setter，也一樣會變紅</b>（dirty check 會補一道全欄位 UPDATE），
     * 而那個版本在 mock 測試裡是全綠的。</p>
     */
    @Test
    void renameNeverWritesConsentOrUnsubscribedColumns() throws SQLException {
        assertTrue(readerProfileService.updateName(EMAIL, "新名字"));

        List<String> updates = audienceUpdates();
        assertFalse(updates.isEmpty(), "完全沒有發出 UPDATE：名稱根本沒被寫入");
        assertTrue(updates.stream().noneMatch(s -> s.contains("unsubscribed")),
            "改名時發出了包含 unsubscribed 的整列 UPDATE，併發的退訂會被靜默還原：" + updates);
        assertTrue(updates.stream().noneMatch(s -> s.contains("consent")),
            "改名時發出了包含 consent 的整列 UPDATE，同意狀態會被靜默覆蓋：" + updates);
        // 名單裡不該有任何欄位被順手改動
        assertFalse(flagInDb("unsubscribed"), "改名不得改動退訂狀態");
        assertTrue(flagInDb("consent"), "改名不得改動同意狀態");
    }

    /** 改名仍必須真的把 name 寫進資料庫（別把缺陷「修」成什麼都不做） */
    @Test
    void renameStillPersistsTheName() throws SQLException {
        assertTrue(readerProfileService.updateName(EMAIL, "凱文大叔"));

        assertEquals("凱文大叔", nameInDb(), "顯示名稱沒有被寫入");
        assertTrue(audienceUpdates().stream().anyMatch(s -> s.contains("name")),
            "沒有任何一道 UPDATE 碰到 name：" + audienceUpdates());
    }

    /** 參與度時間戳仍必須被更新（spec §5.10：更新個人資料是高可靠互動訊號） */
    @Test
    void renameStillTouchesEngagementTimestamp() throws SQLException {
        assertTrue(readerProfileService.updateName(EMAIL, "凱文"));

        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT last_engaged_at FROM survey_response "
                 + "WHERE email = '" + EMAIL + "'")) {
            rs.next();
            org.junit.jupiter.api.Assertions.assertNotNull(rs.getObject(1),
                "last_engaged_at 沒有被更新");
        }
    }

    /**
     * ★ 併發情境的逐字重現：改名交易<b>已讀到 {@code unsubscribed=false}</b> 之後、
     * 提交<b>之前</b>，讀者從另一條連線點了退訂；改名提交後退訂狀態必須仍然成立。
     *
     * <p>用 {@link TransactionTemplate} 手動撐開一個交易，在裡面先讀出那一列
     * （這模擬 {@code updateName} 內的那次 SELECT，entity 進入一級快取），
     * 再以另一條連線提交退訂，最後才呼叫 {@code updateName}——它會加入同一個交易
     * 並直接命中快取裡那份 {@code unsubscribed=false} 的快照。</p>
     *
     * <p>整列 UPDATE 的版本在這裡會把 true 寫回 false，沒有任何錯誤訊息，
     * 而站方會繼續寄信給一位已經明確退訂的讀者。</p>
     */
    @Test
    void concurrentUnsubscribeSurvivesRename() throws SQLException {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            // ① 改名交易讀到的快照：unsubscribed=false
            SurveyResponse loaded = surveyResponseRepository
                .findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(EMAIL).orElseThrow();
            org.junit.jupiter.api.Assertions.assertFalse(loaded.isUnsubscribed());

            // ② 另一條連線（另一個分頁或信件裡的退訂連結）把它改成 true 並立即提交
            unsubscribeOnSeparateConnection();

            // ③ 改名流程接續執行並在本交易結束時提交
            readerProfileService.updateName(EMAIL, "改名後");
        });

        assertTrue(flagInDbUnchecked("unsubscribed"),
            "改名把併發的退訂還原了：站方會繼續寄信給一位已經明確退訂的讀者");
        // 名稱仍應成功寫入——修法不是靠「什麼都不做」來通過
        assertEquals("改名後", nameInDb());
    }

    /** 以獨立連線（不參與目前交易）退訂並立即提交，模擬另一個分頁或信件連結 */
    private void unsubscribeOnSeparateConnection() {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            int affected = st.executeUpdate("UPDATE survey_response SET unsubscribed = true "
                + "WHERE email = '" + EMAIL + "'");
            if (affected != 1) {
                throw new IllegalStateException("模擬併發退訂應影響 1 列，實際 " + affected);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("模擬併發退訂失敗", e);
        }
    }

    /** {@link #flagInDb} 的免受檢例外版本，供 lambda 之後的斷言使用 */
    private boolean flagInDbUnchecked(String column) {
        try {
            return flagInDb(column);
        } catch (SQLException e) {
            throw new IllegalStateException("讀取欄位失敗", e);
        }
    }
}
