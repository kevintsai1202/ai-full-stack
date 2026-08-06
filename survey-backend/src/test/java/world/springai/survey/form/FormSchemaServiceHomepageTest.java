package world.springai.survey.form;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link FormSchemaService#listHomepageForms()} 與
 * {@link FormSchemaService#updateHomepageExposure(String, boolean, Integer)} 驗證：
 * 首頁問卷曝光的預設值、曝光條件（勾選且已發布）、排序規則與 404。
 *
 * <p>比照 {@code FormSchemaServiceCreateFormTest}：全部走 JdbcTemplate 直接下 SQL，
 * 沒有 entity 可供 mock，因此走真實 5433 PG 整合測試。</p>
 */
@SpringBootTest(properties = {
    // 資料庫已由 @BeforeAll 以 Flyway 建好，Spring 這邊不再跑一次
    "spring.flyway.enabled=false"
})
class FormSchemaServiceHomepageTest {

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
    private static final String TEST_DB = "form_schema_homepage_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    /** 把連線三項交給 Spring context，確保 @BeforeAll 建的資料庫與 Spring 用的是同一個 */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TEST_URL);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASS);
    }

    @Autowired
    FormSchemaService service;

    @Autowired
    JdbcTemplate jdbc;

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
            fail("""
                連不到專用測試容器（%s）。本測試驗證的是資料庫層的 SQL 行為，
                無法用 mock 取代，因此不能靜默跳過。請先啟動容器：
                  docker start survey-test-db
                若容器不存在：
                  docker run -d --name survey-test-db -e POSTGRES_PASSWORD=password \\
                    -p 5433:5432 pgvector/pgvector:pg18
                連線資訊可用 MIGRATION_TEST_DB_HOST／PORT／USER／PASSWORD 覆寫。
                """.formatted(ADMIN_URL));
        }
    }

    /** 預設不曝光：新建並發布的問卷不出現在首頁清單（spec §6「既有問卷不被曝光」） */
    @Test
    void newlyCreatedFormIsHiddenByDefault() {
        service.createForm("hp-default-test", "預設隱藏");
        service.publish("hp-default-test", 1);
        assertTrue(service.listHomepageForms().stream()
            .noneMatch(f -> f.key().equals("hp-default-test")));
    }

    /** 勾選曝光後出現；未發布（DRAFT-only）的 key 即使勾選也不出現 */
    @Test
    void exposureRequiresBothVisibleAndPublished() {
        service.createForm("hp-visible-test", "已勾選已發布");
        service.publish("hp-visible-test", 1);
        service.updateHomepageExposure("hp-visible-test", true, null);

        service.createForm("hp-draft-test", "已勾選未發布");
        service.updateHomepageExposure("hp-draft-test", true, null);

        List<String> keys = service.listHomepageForms().stream()
            .map(FormSchemaService.HomepageForm::key).toList();
        assertTrue(keys.contains("hp-visible-test"));
        assertFalse(keys.contains("hp-draft-test"));
    }

    /** 排序：homepage_order 升冪、NULL 排最後、NULL 內部依建立時間新到舊 */
    @Test
    void orderingPutsNullLastThenNewestFirst() {
        // 依序建立 a(order=2)、b(order=1)、c(order=null 較舊)、d(order=null 較新)
        for (String k : List.of("hp-ord-a", "hp-ord-b", "hp-ord-c", "hp-ord-d")) {
            service.createForm(k, k);
            service.publish(k, 1);
        }
        service.updateHomepageExposure("hp-ord-a", true, 2);
        service.updateHomepageExposure("hp-ord-b", true, 1);
        service.updateHomepageExposure("hp-ord-c", true, null);
        service.updateHomepageExposure("hp-ord-d", true, null);
        // c 比 d 早建立 → NULL 區內 d 在前
        jdbc.update("UPDATE form_definition SET created_at = created_at - interval '1 minute' WHERE form_key = 'hp-ord-c'");
        List<String> keys = service.listHomepageForms().stream()
            .map(FormSchemaService.HomepageForm::key)
            .filter(k -> k.startsWith("hp-ord-")).toList();
        assertEquals(List.of("hp-ord-b", "hp-ord-a", "hp-ord-d", "hp-ord-c"), keys);
    }

    /** 不存在的 key → 404 */
    @Test
    void updateUnknownKeyThrows404() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.updateHomepageExposure("hp-no-such-key", true, null));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
