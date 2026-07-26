package world.springai.survey.newsletter;

import org.flywaydb.core.Flyway;
import org.hibernate.resource.jdbc.spi.StatementInspector;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 以真實 PostgreSQL + 真實 Hibernate 驗證「下架 → 重新上架」的完整往返。
 *
 * <p><b>為什麼 {@code CampaignServiceTest} 不夠（實測結果，不是推測）</b>：那份測試把
 * {@code CampaignRepository} 完全 mock 掉，所以斷言的只是「service 用哪些引數呼叫了
 * 哪個方法」。破壞性驗證時實測過：把 {@code markUnpublished} 的 JPQL 從
 * {@code set c.publishedAt = null, c.status = :newStatus} 改回<b>只寫 publishedAt</b>
 * （{@code :newStatus} 留在 WHERE 裡以維持簽章），{@code CampaignServiceTest} 的 45 條
 * <b>全部維持綠燈</b>——mock 依然收到一模一樣的三個引數，而資料庫裡的 {@code status}
 * 一個字都沒改。「下架必須改 status」這件事只能由真實資料庫釘住。</p>
 *
 * <p>本測試同時守住三件無法用 mock 表達的事：</p>
 * <ol>
 *   <li>{@code status} 與 {@code published_at} 兩欄<b>實際</b>被寫成什麼值；</li>
 *   <li>送出的 {@code update campaign} 敘述<b>不含</b>內容與定價欄位——
 *       {@link Campaign} 沒有 {@code @Version} 也沒有 {@code @DynamicUpdate}，
 *       一旦有人把實作改回 {@code save(entity)}，UPDATE 就會帶上整列快照；</li>
 *   <li>往返之後 {@code article_access}、{@code credit_txn} 與 {@code reader.credits}
 *       完全不變——已解鎖的讀者不會被要求為同一篇文章付第二次，
 *       核心不變式「餘額恆等於帳本總和」不受影響。</li>
 * </ol>
 *
 * <p>連線與容器要求同 {@code UnlockConstraintTest}（同一個 {@code survey-test-db}
 * 容器、獨立資料庫名稱），連不上時以明確中文訊息失敗，<b>不靜默跳過</b>。</p>
 */
@SpringBootTest(properties = {
    // 資料庫已由 @BeforeAll 以 Flyway 建好，Spring 這邊不再跑一次
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:5433/survey_campaign_pub_test",
    "spring.datasource.username=postgres",
    "spring.datasource.password=password",
    // 攔截 Hibernate 實際送出的每一道 SQL，這是「UPDATE 不得帶內容欄位」的觀測手段
    "spring.jpa.properties.hibernate.session_factory.statement_inspector="
        + "world.springai.survey.newsletter.CampaignPublicationLifecycleTest$SqlCapture"
})
class CampaignPublicationLifecycleTest {

    private static final String DB_HOST = "127.0.0.1";
    private static final String DB_PORT = "5433";
    private static final String USER = "postgres";
    private static final String PASS = "password";
    private static final String ADMIN_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/postgres";
    /** 獨立的資料庫名稱，不與其他測試共用 */
    private static final String TEST_DB = "survey_campaign_pub_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    private static final String SLUG = "lifecycle-post";
    /** 受限區哨兵：只要它出現在任何 update campaign 敘述裡，就是整列寫回 */
    private static final String GATED_SENTINEL = "SENTINEL_GATED_LIFECYCLE";
    private static final String READER_EMAIL = "lifecycle-reader@example.com";
    /** 讀者餘額；同時是 credit_txn 的總和（300 贈點 - 10 解鎖） */
    private static final int READER_CREDITS = 290;

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

    @Autowired CampaignService campaignService;
    @Autowired DataSource dataSource;

    /** 重建乾淨資料庫並套用全部 migration（需要 V8 的 campaign 發布欄位） */
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
                連不到專用測試容器（%s）。本測試驗證的是資料庫裡實際被寫入的值
                與 Hibernate 實際送出的 SQL，無法用 mock 取代（已實測 mock 測試
                對「下架不改 status」完全無感），因此不能靜默跳過。請先啟動容器：
                  docker start survey-test-db
                若容器不存在：
                  docker run -d --name survey-test-db -e POSTGRES_PASSWORD=password \\
                    -p 5433:5432 pgvector/pgvector:pg18
                """.formatted(ADMIN_URL));
        }
    }

    /**
     * 每個測試前重建一篇已發布的 PREMIUM 文章，以及一位已解鎖它的讀者。
     *
     * <p>只刪本測試自己建的那幾列（以 slug 與 email 為條件），不碰其他資料。</p>
     */
    @BeforeEach
    void seed() throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM article_access WHERE reader_id IN "
                + "(SELECT id FROM reader WHERE email = '" + READER_EMAIL + "')");
            st.executeUpdate("DELETE FROM credit_txn WHERE reader_id IN "
                + "(SELECT id FROM reader WHERE email = '" + READER_EMAIL + "')");
            st.executeUpdate("DELETE FROM reader WHERE email = '" + READER_EMAIL + "'");
            st.executeUpdate("DELETE FROM campaign WHERE slug = '" + SLUG + "'");

            st.executeUpdate("INSERT INTO campaign "
                + "(subject, markdown, mode, recipient_count, status, tier, credit_cost, slug, published_at) "
                + "VALUES ('週期測試文章', '免費區\n\n<!--paywall-->\n\n" + GATED_SENTINEL + "', "
                + "'publish', 0, 'published', 'PREMIUM', 10, '" + SLUG + "', "
                + "'2026-07-20T04:00:00Z')");
            st.executeUpdate("INSERT INTO reader (email, credits, referral_code) VALUES ('"
                + READER_EMAIL + "', " + READER_CREDITS + ", 'LIFECYC1')");
            // 帳本：+300 首次登入贈點、-10 解鎖本篇 → 總和 290 = reader.credits
            st.executeUpdate("INSERT INTO credit_txn (reader_id, delta, reason, note) "
                + "SELECT id, 300, 'SIGNUP_GRANT', '首次登入初始贈點' FROM reader WHERE email = '"
                + READER_EMAIL + "'");
            st.executeUpdate("INSERT INTO credit_txn (reader_id, delta, reason, campaign_id) "
                + "SELECT r.id, -10, 'READ', c.id FROM reader r, campaign c "
                + "WHERE r.email = '" + READER_EMAIL + "' AND c.slug = '" + SLUG + "'");
            st.executeUpdate("INSERT INTO article_access (reader_id, campaign_id, cost) "
                + "SELECT r.id, c.id, 10 FROM reader r, campaign c "
                + "WHERE r.email = '" + READER_EMAIL + "' AND c.slug = '" + SLUG + "'");
        }
        SqlCapture.STATEMENTS.clear();
    }

    /** 本測試那篇文章的 campaign id */
    private long campaignId() throws SQLException {
        return queryLong("SELECT id FROM campaign WHERE slug = '" + SLUG + "'");
    }

    /** 讀取 campaign 的 status 欄位 */
    private String statusInDb() throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT status FROM campaign WHERE slug = '" + SLUG + "'")) {
            rs.next();
            return rs.getString(1);
        }
    }

    /** 讀取 campaign 的 published_at 欄位；NULL 時回 null */
    private OffsetDateTime publishedAtInDb() throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT published_at FROM campaign WHERE slug = '" + SLUG + "'")) {
            rs.next();
            return rs.getObject(1, OffsetDateTime.class);
        }
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

    /** 已攔截到的、針對 campaign 表的 UPDATE 敘述（空白正規化並轉小寫） */
    private static List<String> campaignUpdates() {
        return SqlCapture.STATEMENTS.stream()
            .map(s -> s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim())
            .filter(s -> s.startsWith("update campaign "))
            .toList();
    }

    /**
     * ★ 下架必須<b>同時</b>把 {@code status} 改成 {@code unpublished} 與把
     * {@code published_at} 設為 NULL。
     *
     * <p><b>為什麼 status 不能不改</b>：只清 {@code published_at} 的話，後台歷史列表的
     * pill 會繼續顯示 {@code published}——畫面說這篇是已發布，事實是讀者看不到；
     * 而且重新上架端點就失去唯一的守門依據。把 JPQL 的
     * {@code , c.status = :newStatus} 拿掉，本測試立刻變紅（而 mock 測試不會，
     * 那正是本測試存在的理由）。</p>
     */
    @Test
    void unpublishWritesUnpublishedStatusAndClearsPublishedAt() throws SQLException {
        campaignService.unpublish(campaignId());

        assertEquals(Campaign.STATUS_UNPUBLISHED, statusInDb(),
            "下架沒有把 status 改成 unpublished：後台的 pill 會繼續顯示 published");
        assertNull(publishedAtInDb(), "下架必須把 published_at 設為 NULL，文章才會從 archive 消失");
    }

    /**
     * ★ 重新上架必須把 {@code status} 改回 {@code published}，並寫入一個<b>新的</b>時間戳。
     *
     * <p>發布時間刻意不沿用下架前的舊值：{@code published_at} 的語意是「從什麼時候起
     * 對外可見」，而下架期間確實不可見；{@code /r/archive} 也以它排序，沿用舊值會讓
     * 文章悄悄插回列表深處，沒有讀者會發現它回來了。</p>
     */
    @Test
    void republishWritesPublishedStatusAndFreshTimestamp() throws SQLException {
        OffsetDateTime original = publishedAtInDb();
        assertNotNull(original);
        campaignService.unpublish(campaignId());

        CampaignService.RepublishResult r = campaignService.republish(campaignId());

        assertEquals(Campaign.STATUS_PUBLISHED, statusInDb(), "重新上架必須把 status 改回 published");
        OffsetDateTime after = publishedAtInDb();
        assertNotNull(after, "重新上架必須把 published_at 寫回去，否則文章仍不可見");
        assertTrue(after.isAfter(original),
            "published_at 應為上架當下的新時間而非下架前的舊值：" + after + " vs " + original);
        // 回傳給後台的時間必須就是寫進資料庫的那一個，否則畫面顯示的與事實不符。
        //
        // 容許 1 微秒誤差，而不是直接比 Instant 也不是各自 truncate：
        // PostgreSQL 的 TIMESTAMPTZ 只存到微秒，而 JDK 21 的 OffsetDateTime.now()
        // 會帶到奈秒——關鍵在於 PostgreSQL 是<b>四捨五入</b>而非截斷
        // （實測 ...558463500ns 進資料庫後變成 ...558464µs）。所以兩邊各自
        // truncate 到微秒仍會在剛好過半的奈秒值上偶發失敗，那是測試自己的
        // 精度瑕疵，不是程式缺陷。誤差上界是半個微秒，取 1 微秒足以涵蓋，
        // 同時仍能抓到「回傳的時間根本不是寫進去的那一個」（那會差好幾毫秒以上）。
        long deltaNanos = java.time.Duration
            .between(r.publishedAt().toInstant(), after.toInstant()).abs().toNanos();
        assertTrue(deltaNanos <= 1000,
            "回傳的發布時間與資料庫裡的不一致：" + r.publishedAt() + " vs " + after);
        assertEquals(SLUG, r.slug());
    }

    /**
     * ★ 往返過程送出的 {@code update campaign} 敘述<b>只能</b>碰發布欄位，
     * 不得帶上內容、定價或統計欄位。
     *
     * <p>{@link Campaign} 沒有 {@code @Version} 也沒有 {@code @DynamicUpdate}，
     * 一旦有人把實作改回 {@code save(entity)}，UPDATE 會帶上 SELECT 當下讀到的
     * <b>整列快照</b>，靜默還原這段期間別的請求對同一列的變更（例如同時在跑的
     * reschedule 統計更新），而且沒有任何錯誤訊息。本專案已有兩個 Critical
     * 源於整列寫回，這條就是防它復發的觀測點。</p>
     */
    @Test
    void roundTripNeverWritesContentOrPricingColumns() throws SQLException {
        long id = campaignId();
        campaignService.unpublish(id);
        campaignService.republish(id);

        List<String> updates = campaignUpdates();
        assertEquals(2, updates.size(), "往返應該只有兩道 UPDATE：" + updates);
        for (String sql : updates) {
            // 只允許 published_at 與 status；其餘欄位出現在 SET 子句就是整列寫回
            for (String forbidden : List.of("markdown", "subject", "credit_cost", "tier",
                    "body_html", "accepted_count", "failed_count", "recipient_count",
                    "slug", "mode")) {
                assertTrue(!sql.contains(forbidden),
                    "UPDATE 帶了不該碰的欄位 " + forbidden + "，這是整列寫回：" + sql);
            }
            assertTrue(sql.contains("published_at") && sql.contains("status"),
                "UPDATE 必須同時寫 published_at 與 status：" + sql);
        }
        // 受限區內容連出現在 SQL 裡都不該（整列寫回時它會被當成參數帶進 UPDATE）
        assertTrue(SqlCapture.STATEMENTS.stream().noneMatch(s -> s.contains(GATED_SENTINEL)),
            "受限區內容出現在送出的 SQL 中，代表整列被寫回");
    }

    /**
     * ★ 往返之後，已解鎖者的憑證、帳本與餘額必須<b>完全不變</b>。
     *
     * <p>兩層理由：① 核心不變式「{@code reader.credits} 恆等於 {@code credit_txn} 總和」
     * 要求帳本只增不改，刪掉扣點紀錄會讓餘額與帳本永久對不上；
     * ② {@code article_access} 是「這個人已經買過這篇」的憑證，重新上架後仍應有效，
     * 否則讀者會被要求為同一篇文章付第二次。</p>
     *
     * <p><b>也不得刪除 campaign 那一列</b>：{@code article_access.campaign_id} 指向它，
     * 刪列等於毀掉所有已購讀者的憑證。</p>
     */
    @Test
    void roundTripPreservesAccessLedgerAndCredits() throws SQLException {
        long id = campaignId();

        campaignService.unpublish(id);
        campaignService.republish(id);

        assertEquals(1, queryLong("SELECT count(*) FROM campaign WHERE id = " + id),
            "campaign 那一列不可被刪除：article_access 指向它");
        assertEquals(1, queryLong("SELECT count(*) FROM article_access WHERE campaign_id = " + id),
            "已解鎖者的憑證必須保留，否則他要為同一篇付第二次");
        assertEquals(2, queryLong("SELECT count(*) FROM credit_txn WHERE reader_id IN "
                + "(SELECT id FROM reader WHERE email = '" + READER_EMAIL + "')"),
            "帳本只增不改：往返不得新增也不得刪除任何一列");
        assertEquals(READER_CREDITS, queryLong(
                "SELECT credits FROM reader WHERE email = '" + READER_EMAIL + "'"),
            "下架／重新上架不得改動任何讀者的餘額");
        // 核心不變式：餘額恆等於帳本總和
        assertEquals(queryLong("SELECT coalesce(sum(delta), 0) FROM credit_txn WHERE reader_id IN "
                + "(SELECT id FROM reader WHERE email = '" + READER_EMAIL + "')"),
            queryLong("SELECT credits FROM reader WHERE email = '" + READER_EMAIL + "'"),
            "reader.credits 與 credit_txn 總和對不上");
    }

    /**
     * 對「已經對外可見」的文章重新上架必須被拒絕，且<b>不得改動 published_at</b>。
     *
     * <p>若守門失效，這條端點會用一個新的時間戳覆蓋掉原本的發布時間——
     * 文章無聲跳到 archive 最上面，而操作者以為自己什麼都沒改。
     * 這裡驗的是資料庫裡的值真的沒動，不只是有沒有拋例外。</p>
     */
    @Test
    void republishOnVisibleArticleChangesNothingInDb() throws SQLException {
        OffsetDateTime before = publishedAtInDb();

        org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.web.server.ResponseStatusException.class,
            () -> campaignService.republish(campaignId()));

        assertEquals(before.toInstant(), publishedAtInDb().toInstant(),
            "被拒絕的重新上架不得改動 published_at");
        assertEquals(Campaign.STATUS_PUBLISHED, statusInDb());
        assertTrue(campaignUpdates().isEmpty(), "被拒絕的請求不該送出任何 UPDATE：" + campaignUpdates());
    }
}
