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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link FormSchemaService#createQuickVoteForm} 驗證：一次呼叫即產出
 * 「已發布且已綁定信中一鍵題」的可嵌入問卷，以及選項與標題的輸入驗證。
 *
 * <p>走真實 5433 PG，模式比照 {@code FormSchemaServiceCreateFormTest}。</p>
 */
@SpringBootTest(properties = "spring.flyway.enabled=false")
class FormSchemaServiceQuickVoteTest {

    private static String env(String name, String defaultValue) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }

    private static final String DB_HOST = env("MIGRATION_TEST_DB_HOST", "127.0.0.1");
    private static final String DB_PORT = env("MIGRATION_TEST_DB_PORT", "5433");
    private static final String USER = env("MIGRATION_TEST_DB_USER", "postgres");
    private static final String PASS = env("MIGRATION_TEST_DB_PASSWORD", "password");
    private static final String ADMIN_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/postgres";
    private static final String TEST_DB = "quick_vote_form_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TEST_URL);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASS);
    }

    @Autowired FormSchemaService service;

    @BeforeAll
    static void prepare() throws SQLException {
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS)) {
            // 連得上即可
        } catch (SQLException e) {
            fail("連不到專用測試容器（" + ADMIN_URL + "）。請先 docker start survey-test-db");
        }
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS);
             Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + TEST_DB + " WITH (FORCE)");
            st.execute("CREATE DATABASE " + TEST_DB);
        }
        Flyway.configure().dataSource(TEST_URL, USER, PASS).load().migrate();
    }

    /** 一次呼叫即產出可嵌入問卷：PUBLISHED、已綁定信中一鍵題、選項順序保留 */
    @Test
    void 一次呼叫即產出可嵌入問卷() {
        FormSchemaService.EmailVoteQuestion question = service.createQuickVoteForm(
            new FormSchemaService.QuickVoteRequest("這期你最想看哪個主題？", "選一個最想深入的",
                List.of("RAG 實戰", "Agent 架構", "部署維運")));

        assertTrue(question.formKey().matches("[a-z0-9-]{3,50}"),
            "自動生成的 formKey 必須符合既有格式：" + question.formKey());
        assertEquals("vote", question.fieldKey(), "信中一鍵題固定綁在 vote 欄位");
        assertEquals("這期你最想看哪個主題？", question.title());
        assertEquals("選一個最想深入的", question.label());
        assertEquals(List.of("RAG 實戰", "Agent 架構", "部署維運"), question.options(),
            "選項順序必須原樣保留（optionIndex 依此對映）");

        // 真正的驗收條件：立刻就能被電子報標記嵌入，不需再到動態表單分頁做任何設定
        assertTrue(service.emailVoteQuestion(question.formKey()).isPresent(),
            "建立完成後應立即可嵌入");
        assertTrue(service.listEmbeddable().stream()
                .anyMatch(q -> q.formKey().equals(question.formKey())),
            "應出現在可嵌入清單中");
    }

    /** 選項少於 2 個沒有投票意義，以 400 擋在寫入前 */
    @Test
    void 選項少於兩個回400() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.createQuickVoteForm(new FormSchemaService.QuickVoteRequest(
                "標題", "說明", List.of("只有一個"))));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    /** 選項超過 6 個在信件裡會排到爆版，以 400 擋下 */
    @Test
    void 選項超過六個回400() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.createQuickVoteForm(new FormSchemaService.QuickVoteRequest(
                "標題", "說明", List.of("1", "2", "3", "4", "5", "6", "7"))));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    /** 重複選項會讓統計無法區分，以 400 擋下（去空白後比對） */
    @Test
    void 重複選項回400() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.createQuickVoteForm(new FormSchemaService.QuickVoteRequest(
                "標題", "說明", List.of("同一個", " 同一個 "))));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    /** 空白標題回 400（沿用 createForm 既有的標題必填規則） */
    @Test
    void 空白標題回400() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.createQuickVoteForm(new FormSchemaService.QuickVoteRequest(
                "   ", "說明", List.of("A", "B"))));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    /** 連續建立兩份不得撞 formKey（同一天內的生成必須帶足夠亂度） */
    @Test
    void 連續建立兩份不撞代號() {
        FormSchemaService.EmailVoteQuestion first = service.createQuickVoteForm(
            new FormSchemaService.QuickVoteRequest("第一份", "說明", List.of("A", "B")));
        FormSchemaService.EmailVoteQuestion second = service.createQuickVoteForm(
            new FormSchemaService.QuickVoteRequest("第二份", "說明", List.of("A", "B")));

        assertTrue(!first.formKey().equals(second.formKey()),
            "兩份問卷的 formKey 不可相同：" + first.formKey());
    }
}
