package world.springai.survey.newsletter;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 以真實 PostgreSQL 驗證 {@link CampaignRepository#findPremiumCostRange(String)} 的 WHERE 條件。
 *
 * <p><b>為什麼 mock 測不出來（不是推測，是結構事實）</b>：{@code PremiumCostDisplayTest} 與
 * 兩支頁面測試都把 {@code CampaignRepository} mock 掉，斷言的只是「片語怎麼組」。
 * 把 JPQL 的 {@code publishedAt is not null} 整條刪掉，那些測試<b>全部維持綠燈</b>——
 * mock 依然回一樣的 12／48。「已下架的文章不得計入區間」這件事只能由真實資料庫釘住。</p>
 *
 * <p>這條也順便是那個 interface projection 唯一的真實驗證：聚合查詢 + 別名投影能不能
 * 被 Spring Data 正確組出來、零列時兩欄是不是 NULL 而不是拋錯，都只有跑真的 SQL 才知道。</p>
 *
 * <p>連線與容器要求同 {@code CampaignPublicationLifecycleTest}（同一個 {@code survey-test-db}
 * 容器、獨立資料庫名稱），連不上時以明確中文訊息失敗，<b>不靜默跳過</b>。</p>
 */
@SpringBootTest(properties = {
    // 資料庫已由 @BeforeAll 以 Flyway 建好，Spring 這邊不再跑一次
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:5433/survey_premium_range_test",
    "spring.datasource.username=postgres",
    "spring.datasource.password=password"
})
class CampaignPremiumCostRangeTest {

    private static final String DB_HOST = "127.0.0.1";
    private static final String DB_PORT = "5433";
    private static final String USER = "postgres";
    private static final String PASS = "password";
    private static final String ADMIN_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/postgres";
    /** 獨立的資料庫名稱，不與其他測試共用 */
    private static final String TEST_DB = "survey_premium_range_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    @Autowired CampaignRepository campaignRepository;
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

    /** 連不上專用測試容器時以明確訊息失敗，並附上啟動指令 */
    private static void requireTestDatabase() {
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS)) {
            // 連得上即可
        } catch (SQLException e) {
            fail("""
                連不到專用測試容器（%s）。本測試驗證的是 JPQL 的 WHERE 條件實際篩掉了哪些列，
                已實測 mock 測試對「已下架文章仍計入區間」完全無感，因此不能靜默跳過。
                請先啟動容器：
                  docker start survey-test-db
                若容器不存在：
                  docker run -d --name survey-test-db -e POSTGRES_PASSWORD=password \\
                    -p 5433:5432 pgvector/pgvector:pg18
                """.formatted(ADMIN_URL));
        }
    }

    /** 每個測試前清空 campaign，避免前一條測試的列影響聚合結果 */
    @BeforeEach
    void clean() throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM credit_txn");
            st.executeUpdate("DELETE FROM article_access");
            st.executeUpdate("DELETE FROM email_log");
            st.executeUpdate("DELETE FROM campaign");
        }
    }

    /**
     * 直接以 SQL 插入一列 campaign。
     *
     * <p>刻意不用 {@code campaignRepository.save()}：那會經過實體的預設值與 service 層
     * 的驗證，反而難以造出「已下架」「沒有 slug」這些本測試正要驗的邊界狀態。</p>
     *
     * @param slug        網址代稱，null 代表沒有發布到網頁
     * @param publishedAt 發布時間（ISO 字串），null 代表已下架或從未發布
     */
    private void insertCampaign(String tier, int creditCost, String slug, String publishedAt)
            throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO campaign "
                + "(subject, markdown, mode, recipient_count, status, tier, credit_cost, slug, published_at) "
                + "VALUES ('區間測試', '內文', 'publish', 0, 'published', "
                + "'" + tier + "', " + creditCost + ", "
                + (slug == null ? "NULL" : "'" + slug + "'") + ", "
                + (publishedAt == null ? "NULL" : "'" + publishedAt + "'") + ")");
        }
    }

    /** 查詢區間；tier 一律傳 {@link Campaign#TIER_PREMIUM} */
    private CampaignRepository.PremiumCostRange range() {
        return campaignRepository.findPremiumCostRange(Campaign.TIER_PREMIUM);
    }

    /** 多篇已發布 PREMIUM：min 與 max 取自 campaign.credit_cost，與實際扣款同源 */
    @Test
    void returnsMinAndMaxOfPublishedPremiumCosts() throws SQLException {
        insertCampaign(Campaign.TIER_PREMIUM, 12, "range-a", "2026-07-20T04:00:00Z");
        insertCampaign(Campaign.TIER_PREMIUM, 30, "range-b", "2026-07-21T04:00:00Z");
        insertCampaign(Campaign.TIER_PREMIUM, 48, "range-c", "2026-07-22T04:00:00Z");

        CampaignRepository.PremiumCostRange r = range();
        assertNotNull(r, "聚合查詢不該回 null 投影");
        assertEquals(12, r.getMinCost());
        assertEquals(48, r.getMaxCost());
    }

    /**
     * <b>已下架的文章不得計入區間。</b>
     *
     * <p>下架把 {@code published_at} 設回 NULL（見 {@code markUnpublished}），讀者從
     * {@code /r/archive} 與 {@code /r/news/{slug}} 都打不開它。若它仍影響區間，
     * 規則頁會顯示一個站上根本買不到的價格——這正是本次修正要消除的那類落差。</p>
     */
    @Test
    void unpublishedCampaignIsExcludedFromTheRange() throws SQLException {
        insertCampaign(Campaign.TIER_PREMIUM, 30, "range-live", "2026-07-20T04:00:00Z");
        // 曾經以 5 點發布、現已下架（published_at 為 NULL，slug 保留）
        insertCampaign(Campaign.TIER_PREMIUM, 5, "range-gone", null);
        // 曾經以 900 點發布、現已下架
        insertCampaign(Campaign.TIER_PREMIUM, 900, "range-gone-2", null);

        CampaignRepository.PremiumCostRange r = range();
        assertEquals(30, r.getMinCost(), "已下架的 5 點文章被算進了最低價");
        assertEquals(30, r.getMaxCost(), "已下架的 900 點文章被算進了最高價");
    }

    /** BASIC 文章不得計入：它們對訂閱者免費，credit_cost 不是解鎖價 */
    @Test
    void basicTierIsExcludedFromTheRange() throws SQLException {
        insertCampaign(Campaign.TIER_PREMIUM, 30, "range-premium", "2026-07-20T04:00:00Z");
        insertCampaign(Campaign.TIER_BASIC, 7, "range-basic", "2026-07-21T04:00:00Z");

        CampaignRepository.PremiumCostRange r = range();
        assertEquals(30, r.getMinCost(), "BASIC 文章的 credit_cost 被算進了區間");
        assertEquals(30, r.getMaxCost());
    }

    /** 沒有 slug 的列不得計入：沒有網址就沒有讀者打得開的頁面，那個價格不存在 */
    @Test
    void campaignWithoutSlugIsExcludedFromTheRange() throws SQLException {
        insertCampaign(Campaign.TIER_PREMIUM, 30, "range-with-slug", "2026-07-20T04:00:00Z");
        insertCampaign(Campaign.TIER_PREMIUM, 3, null, "2026-07-21T04:00:00Z");

        CampaignRepository.PremiumCostRange r = range();
        assertEquals(30, r.getMinCost(), "沒有 slug 的殘列被算進了區間");
        assertEquals(30, r.getMaxCost());
    }

    /**
     * 完全沒有已發布 PREMIUM 文章時，兩欄皆為 NULL（不是 0、也不是回 null 投影）。
     *
     * <p>呼叫端（{@code PremiumCostDisplay}）就是靠這個訊號決定要不要退回全域預設。
     * 若這裡實際回的是 0，頁面會顯示「目前每篇 0 點」，等於對外宣告進階內容免費。</p>
     */
    @Test
    void bothBoundsAreNullWhenNoPublishedPremiumArticleExists() throws SQLException {
        insertCampaign(Campaign.TIER_BASIC, 7, "range-basic-only", "2026-07-21T04:00:00Z");
        insertCampaign(Campaign.TIER_PREMIUM, 40, "range-unpublished-only", null);

        CampaignRepository.PremiumCostRange r = range();
        assertNotNull(r, "零列的聚合查詢仍應回一個投影（兩欄為 NULL），而非 null");
        assertNull(r.getMinCost(), "沒有可統計的文章時 min 必須是 null，不能是 0");
        assertNull(r.getMaxCost(), "沒有可統計的文章時 max 必須是 null，不能是 0");
    }
}
