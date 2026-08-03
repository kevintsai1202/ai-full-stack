package world.springai.survey.coupon;

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
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link CouponCampaign} 存讀與 DB CHECK 約束驗證。
 *
 * <p>基底類別、@SpringBootTest 屬性設定與資料庫重建 helper 逐字比照
 * {@code SurveyVoteRepositoryTest}（同套 V22 驗證模式）。</p>
 */
@SpringBootTest(properties = {
    // 資料庫已由 @BeforeAll 以 Flyway 建好，Spring 這邊不再跑一次
    "spring.flyway.enabled=false",
    // 順帶驗證 CouponCampaign 的 entity 對應通得過啟動時的 validate
    "spring.jpa.hibernate.ddl-auto=validate"
})
class CouponCampaignRepositoryTest {

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
    private static final String TEST_DB = "coupon_campaign_repository_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    /** 把連線三項交給 Spring context，確保 @BeforeAll 建的資料庫與 Spring 用的是同一個 */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TEST_URL);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASS);
    }

    @Autowired
    CouponCampaignRepository repository;

    /** 重建乾淨資料庫並套用全部 migration（含 V22，本測試的驗證對象） */
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
                連不到專用測試容器（%s）。本測試驗證的是資料庫層的約束，
                無法用 mock 取代，因此不能靜默跳過。請先啟動容器：
                  docker start survey-test-db
                若容器不存在：
                  docker run -d --name survey-test-db -e POSTGRES_PASSWORD=password \\
                    -p 5433:5432 pgvector/pgvector:pg18
                連線資訊可用 MIGRATION_TEST_DB_HOST／PORT／USER／PASSWORD 覆寫。
                """.formatted(ADMIN_URL));
        }
    }

    /**
     * 存一筆活動後可讀回，狀態預設 DRAFT，且 created_at 由 {@code @PrePersist} 寫回實體欄位。
     */
    @Test
    void 儲存與讀回活動_預設DRAFT() {
        CouponCampaign saved = repository.save(new CouponCampaign(
            "AI 全端開發", "推薦文案", "https://hahow.in/cr/x", "SAVE300",
            LocalDate.of(2026, 9, 30), "reader-poll", "{}"));

        assertEquals(CouponCampaign.STATUS_DRAFT, saved.getStatus());
        assertNotNull(saved.getCreatedAt(), "created_at 應由 @PrePersist 寫回實體欄位");

        List<CouponCampaign> all = repository.findAllByOrderByCreatedAtDesc();
        assertEquals(1, all.size());
        assertEquals("AI 全端開發", all.get(0).getCourseName());
        assertEquals("{}", all.get(0).getAnswerFilter());
    }

    /**
     * course_url 非 https 開頭必須撞 DB 的 {@code ck_coupon_course_url} CHECK 約束，
     * 這是應用層驗證之外的最終防線（2026-08-03 mailto 案的教訓：改連結規則必查 CHECK）。
     */
    @Test
    void courseUrl非https撞DB_CHECK() {
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(
            new CouponCampaign("課", "文", "http://x.com", "C", null, "k", "{}")));
    }
}
