package world.springai.survey.form;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link FormSchemaService#createForm(String, String)} 驗證：admin 建立全新問卷
 * 產生 v1 DRAFT 空殼、formKey 格式檢查與重複拒絕。
 *
 * <p>{@code FormSchemaService} 全部走 {@code JdbcTemplate} 直接下 SQL，沒有 entity
 * 可供 mock，因此本測試比照 {@code SurveyVoteRepositoryTest} 走真實 5433 PG 整合測試：
 * 專用資料庫、Flyway 建表後才啟動 Spring context，並直接對被注入的真實 service
 * 呼叫方法。</p>
 */
@SpringBootTest(properties = {
    // 資料庫已由 @BeforeAll 以 Flyway 建好，Spring 這邊不再跑一次
    "spring.flyway.enabled=false"
})
class FormSchemaServiceCreateFormTest {

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
    private static final String TEST_DB = "form_schema_create_form_test";
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

    /** 建立全新問卷會產生 v1 DRAFT 空殼版本。 */
    @Test
    void 建立新問卷_v1_DRAFT() {
        FormSchemaService.FormDefinition form = service.createForm("reader-poll", "讀者意見調查");

        assertEquals(1, form.version());
        assertEquals("DRAFT", form.status());
    }

    /** formKey 不符 [a-z0-9-]{3,50} 時以 400 拒絕。 */
    @Test
    void formKey格式不符拒絕() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
            () -> service.createForm("Bad_Key!", "x"));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    /** 同一 formKey 已存在時第二次建立以 409 拒絕。 */
    @Test
    void formKey重複拒絕() {
        service.createForm("dup-poll", "一");

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
            () -> service.createForm("dup-poll", "二"));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
    }
}
