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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link FormSchemaService#emailVoteQuestion(String)}、
 * {@link FormSchemaService#updateEmailVoteField(String, int, String)}、
 * {@link FormSchemaService#listEmbeddable()} 驗證：信中一鍵題指定的讀寫與
 * 可嵌入問卷清單。
 *
 * <p>比照 {@code FormSchemaServiceCreateFormTest}，{@code FormSchemaService}
 * 全部走 {@code JdbcTemplate} 直接下 SQL 沒有 entity 可 mock，走真實 5433 PG
 * 整合測試：專用資料庫、Flyway 建表後才啟動 Spring context。</p>
 */
@SpringBootTest(properties = {
    // 資料庫已由 @BeforeAll 以 Flyway 建好，Spring 這邊不再跑一次
    "spring.flyway.enabled=false"
})
class EmailVoteQuestionTest {

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
    private static final String TEST_DB = "email_vote_question_test";
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

    /** 建立一份含單選欄位的草稿版本，回傳版本物件供各測試延伸使用。 */
    private FormSchemaService.FormDefinition draftWithSelectField(
            String formKey, String title, String fieldKey, List<Object> options) {
        FormSchemaService.FormDefinition draft = service.createForm(formKey, title);
        return service.addField(
            formKey,
            draft.version(),
            fieldKey,
            new FormSchemaService.FieldRequest(
                "喜好投票", "select", true, options,
                true, "bar", true, false, false, 1000, null));
    }

    /** 已發布版本指定信中一鍵題後，emailVoteQuestion 回傳完整題目與選項。 */
    @Test
    void emailVoteQuestion_已設定信中題回傳完整資料() {
        FormSchemaService.FormDefinition draft = draftWithSelectField(
            "email-vote-happy", "信中投票問卷", "rating", List.of("讚", "普通", "差"));
        service.publish(draft.key(), draft.version());
        service.updateEmailVoteField(draft.key(), draft.version(), "rating");

        Optional<FormSchemaService.EmailVoteQuestion> question =
            service.emailVoteQuestion(draft.key());

        assertTrue(question.isPresent());
        assertEquals(draft.key(), question.get().formKey());
        assertEquals("信中投票問卷", question.get().title());
        assertEquals("rating", question.get().fieldKey());
        assertEquals("喜好投票", question.get().label());
        assertEquals(List.of("讚", "普通", "差"), question.get().options());
    }

    /** 未發布（僅 DRAFT）時 emailVoteQuestion 回 empty，即使 DRAFT 上已設欄位。 */
    @Test
    void emailVoteQuestion_未發布回empty() {
        FormSchemaService.FormDefinition draft = draftWithSelectField(
            "email-vote-draft", "尚未發布問卷", "rating", List.of("A", "B"));
        service.updateEmailVoteField(draft.key(), draft.version(), "rating");

        Optional<FormSchemaService.EmailVoteQuestion> question =
            service.emailVoteQuestion(draft.key());

        assertTrue(question.isEmpty());
    }

    /** 從未指定信中題的已發布問卷，emailVoteQuestion 回 empty。 */
    @Test
    void emailVoteQuestion_未設定信中題回empty() {
        FormSchemaService.FormDefinition draft = draftWithSelectField(
            "email-vote-unset", "未指定信中題問卷", "rating", List.of("A", "B"));
        service.publish(draft.key(), draft.version());

        Optional<FormSchemaService.EmailVoteQuestion> question =
            service.emailVoteQuestion(draft.key());

        assertTrue(question.isEmpty());
    }

    /** 指定非 select 欄位（long_text）以 400 拒絕。 */
    @Test
    void updateEmailVoteField_非select欄位拒絕400() {
        FormSchemaService.FormDefinition draft = service.createForm(
            "email-vote-longtext", "長文問卷");
        FormSchemaService.FormDefinition withField = service.addField(
            draft.key(), draft.version(), "feedback",
            new FormSchemaService.FieldRequest(
                "意見回饋", "long_text", false, List.of(),
                false, null, false, false, false, 1000, null));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
            () -> service.updateEmailVoteField(withField.key(), withField.version(), "feedback"));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    /** 指定不存在的欄位 key 以 400 拒絕。 */
    @Test
    void updateEmailVoteField_欄位不存在拒絕400() {
        FormSchemaService.FormDefinition draft = service.createForm(
            "email-vote-missing-field", "空問卷");

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
            () -> service.updateEmailVoteField(draft.key(), draft.version(), "no-such-field"));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    /** fieldKey 傳 null 可清除已設定的信中題。 */
    @Test
    void updateEmailVoteField_null清除已設定欄位() {
        FormSchemaService.FormDefinition draft = draftWithSelectField(
            "email-vote-clear", "可清除問卷", "rating", List.of("A", "B"));
        service.publish(draft.key(), draft.version());
        service.updateEmailVoteField(draft.key(), draft.version(), "rating");
        assertTrue(service.emailVoteQuestion(draft.key()).isPresent());

        service.updateEmailVoteField(draft.key(), draft.version(), null);

        assertTrue(service.emailVoteQuestion(draft.key()).isEmpty());
    }

    /** listEmbeddable 只回傳已發布且已設信中題的問卷，草稿與未設定的問卷都不列入。 */
    @Test
    void listEmbeddable_只含已發布且已設信中題的問卷() {
        FormSchemaService.FormDefinition configured = draftWithSelectField(
            "email-vote-embeddable-ok", "可嵌入問卷", "rating", List.of("A", "B"));
        service.publish(configured.key(), configured.version());
        service.updateEmailVoteField(configured.key(), configured.version(), "rating");

        FormSchemaService.FormDefinition publishedNoVote = draftWithSelectField(
            "email-vote-embeddable-novote", "已發布未設信中題", "rating", List.of("A", "B"));
        service.publish(publishedNoVote.key(), publishedNoVote.version());

        draftWithSelectField(
            "email-vote-embeddable-draft", "草稿問卷", "rating", List.of("A", "B"));

        List<FormSchemaService.EmailVoteQuestion> embeddable = service.listEmbeddable();

        assertTrue(embeddable.stream().anyMatch(q -> q.formKey().equals(configured.key())));
        assertFalse(embeddable.stream().anyMatch(q -> q.formKey().equals(publishedNoVote.key())));
        assertFalse(embeddable.stream().anyMatch(q -> q.formKey().equals("email-vote-embeddable-draft")));
    }
}
