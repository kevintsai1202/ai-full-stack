package world.springai.survey.form;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import world.springai.survey.audience.AudiencePlatformService;
import world.springai.survey.audience.AudienceSourceService;
import world.springai.survey.audience.SurveyResponse;
import world.springai.survey.audience.SurveyResponseRepository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link FormSourceBreakdownService} 驗證：塞入不同 {@code source_key} 的
 * {@code audience_record} 樣本，驗證來源聚合、「有答案」判定與標籤解析順序。
 *
 * <p>走真實 PG 整合測試（比照 {@code SurveyVoteStatsServiceTest}），因為核心是
 * SQL 的 {@code GROUP BY} 與 {@code FILTER} 聚合，沒有 entity 可供 mock。</p>
 */
class FormSourceBreakdownServiceTest {

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    private static final String DB_HOST = env("MIGRATION_TEST_DB_HOST", "127.0.0.1");
    private static final String DB_PORT = env("MIGRATION_TEST_DB_PORT", "5433");
    private static final String USER = env("MIGRATION_TEST_DB_USER", "postgres");
    private static final String PASS = env("MIGRATION_TEST_DB_PASSWORD", "password");
    private static final String ADMIN_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/postgres";
    /** 獨立資料庫名稱，不與其他測試共用 */
    private static final String TEST_DB = "survey_source_breakdown_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    /** migration 已建好並發布的課程興趣問卷，欄位含 role／interest 等 */
    private static final String FORM_KEY = "fullstack-course-interest";

    private static AudiencePlatformService audience;
    private static FormSourceBreakdownService breakdown;

    /** 重建乾淨資料庫並套用全部 migration，測試不依賴開發資料庫狀態。 */
    @BeforeAll
    static void prepare() throws SQLException {
        requireTestDatabase();
        try (Connection connection = DriverManager.getConnection(ADMIN_URL, USER, PASS);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + TEST_DB + " WITH (FORCE)");
            statement.execute("CREATE DATABASE " + TEST_DB);
        }
        Flyway.configure().dataSource(TEST_URL, USER, PASS).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(TEST_URL, USER, PASS));
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        audience = new AudiencePlatformService(jdbc, objectMapper);

        SurveyResponseRepository legacyRepository = mock(SurveyResponseRepository.class);
        when(legacyRepository.save(any(SurveyResponse.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        FormSchemaService forms = new FormSchemaService(jdbc, objectMapper, audience, legacyRepository);

        // 只回傳系統預設三項，模擬「匯入註冊表不含程式自動產生的來源」這個實際狀態
        AudienceSourceService sources = mock(AudienceSourceService.class);
        when(sources.list()).thenReturn(List.of(
            new AudienceSourceService.SourceOption("survey_form", "問卷填寫"),
            new AudienceSourceService.SourceOption("exam", "線上測驗"),
            new AudienceSourceService.SourceOption("dify", "Dify 學員")));

        breakdown = new FormSourceBreakdownService(jdbc, forms, sources);
    }

    /** 連不上專用測試容器時以明確訊息失敗，不靜默跳過 */
    private static void requireTestDatabase() {
        try (Connection connection = DriverManager.getConnection(ADMIN_URL, USER, PASS)) {
            // 連得上即可
        } catch (SQLException exception) {
            fail("""
                連不到專用測試容器（%s）。本測試驗證的是資料庫層的 SQL 聚合行為，
                無法用 mock 取代，因此不能靜默跳過。請先啟動容器：
                  docker start survey-test-db
                若容器不存在：
                  docker run -d --name survey-test-db -e POSTGRES_PASSWORD=password \\
                    -p 5433:5432 pgvector/pgvector:pg18
                連線資訊可用 MIGRATION_TEST_DB_HOST／PORT／USER／PASSWORD 覆寫。
                """.formatted(ADMIN_URL));
        }
    }

    /** 插入一筆 survey_submission 紀錄；answers 直接指定，模擬各來源的實際 raw_data 形狀。 */
    private void insertRecord(String sourceKey, Map<String, Object> answers) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        long personId = audience.mergePerson(
            "breakdown-" + UUID.randomUUID() + "@example.com", "來源分佈測試", now).personId();
        audience.upsertRecord(
            personId, sourceKey, "survey_submission",
            FORM_KEY + "@1", UUID.randomUUID().toString(), now,
            Map.of("answers", answers), Map.of());
    }

    /** 從結果中取出指定來源那一列。 */
    private FormSourceBreakdownService.SourceRow row(
            FormSourceBreakdownService.Breakdown result, String key) {
        return result.sources().stream()
            .filter(source -> source.key().equals(key))
            .findFirst()
            .orElseThrow(() -> new AssertionError("結果中找不到來源 " + key));
    }

    /**
     * 依來源聚合筆數，並把「有 schema 欄位答案」與「空殼紀錄」分開計數。
     *
     * <p>這是本服務存在的理由：舊畫面只看得到總筆數，無法分辨 254 筆考試空殼
     * 與 60 筆真實填答的差別。</p>
     */
    @Test
    void 依來源聚合總筆數與有答案筆數() {
        insertRecord("survey_form", Map.of("role", "工程師", "interest", "很有興趣"));
        insertRecord("survey_form", Map.of("role", "PM"));
        insertRecord("exam", Map.of());
        insertRecord("exam", Map.of());
        insertRecord("exam", Map.of());

        FormSourceBreakdownService.Breakdown result =
            breakdown.breakdown(FORM_KEY, null, false, null, null, null);

        assertEquals(2L, row(result, "survey_form").total());
        assertEquals(2L, row(result, "survey_form").answered());
        assertEquals(3L, row(result, "exam").total());
        assertEquals(0L, row(result, "exam").answered());
    }

    /**
     * 只帶 {@code _ref}（推薦碼）的紀錄不算有答案。
     *
     * <p>{@code newsletter} 來源的紀錄就是這個形狀。若用「answers 非空」當判定，
     * 這些歸因用的中繼資料會被誤算成真實填答。</p>
     */
    @Test
    void 只帶推薦碼的紀錄不算有答案() {
        insertRecord("newsletter", Map.of("_ref", "abc123"));
        insertRecord("newsletter", Map.of("_ref", "def456"));

        FormSourceBreakdownService.Breakdown result =
            breakdown.breakdown(FORM_KEY, null, false, null, null, null);

        assertEquals(2L, row(result, "newsletter").total());
        assertEquals(0L, row(result, "newsletter").answered());
    }

    /**
     * 標籤解析順序：匯入註冊表 → 內建對照表 → 退回原始 key。
     *
     * <p>第三段是刻意的：未知來源必須看得見，否則就重演「資料在但看不到」的缺陷。</p>
     */
    @Test
    void 標籤依序取註冊表內建對照表與原始key() {
        insertRecord("newsletter_survey", Map.of("role", "工程師"));
        insertRecord("dify", Map.of());
        insertRecord("some-unknown-source", Map.of());

        FormSourceBreakdownService.Breakdown result =
            breakdown.breakdown(FORM_KEY, null, false, null, null, null);

        assertEquals("Dify 學員", row(result, "dify").label());
        assertEquals("讀者接續填答", row(result, "newsletter_survey").label());
        assertEquals("電子報通道", row(result, "newsletter").label());
        assertEquals("some-unknown-source", row(result, "some-unknown-source").label());
    }

    /** totals 為各來源加總，且來源依總筆數由大到小排序。 */
    @Test
    void totals為各來源加總且依筆數排序() {
        FormSourceBreakdownService.Breakdown result =
            breakdown.breakdown(FORM_KEY, null, false, null, null, null);

        long expectedTotal = result.sources().stream()
            .mapToLong(FormSourceBreakdownService.SourceRow::total).sum();
        long expectedAnswered = result.sources().stream()
            .mapToLong(FormSourceBreakdownService.SourceRow::answered).sum();
        assertEquals(expectedTotal, result.totals().total());
        assertEquals(expectedAnswered, result.totals().answered());

        List<Long> counts = result.sources().stream()
            .map(FormSourceBreakdownService.SourceRow::total).toList();
        List<Long> sorted = counts.stream()
            .sorted(java.util.Comparator.reverseOrder()).toList();
        assertEquals(sorted, counts, "來源應依總筆數由大到小排序");
    }
}
