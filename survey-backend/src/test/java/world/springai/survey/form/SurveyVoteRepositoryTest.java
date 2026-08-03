package world.springai.survey.form;

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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link SurveyVote} 唯一身分約束驗證：透過 Spring Data JPA 存取時，
 * {@code uq_survey_vote_identity} partial unique index（V21）撞鍵的例外
 * 是否正確轉譯成 {@code DataIntegrityViolationException}——這是後續
 * 「一鍵改票用 upsert」流程能否安全依賴唯一約束的最終防線。
 *
 * <p>基底類別、@SpringBootTest 屬性設定與資料庫重建 helper 逐字比照
 * {@code SurveyRewardConstraintTest}（同套 V21 驗證模式）。</p>
 */
@SpringBootTest(properties = {
    // 資料庫已由 @BeforeAll 以 Flyway 建好，Spring 這邊不再跑一次
    "spring.flyway.enabled=false",
    // 順帶驗證 SurveyVote 的 entity 對應通得過啟動時的 validate
    "spring.jpa.hibernate.ddl-auto=validate"
})
class SurveyVoteRepositoryTest {

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
    private static final String TEST_DB = "survey_vote_repository_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    /** 把連線三項交給 Spring context，確保 @BeforeAll 建的資料庫與 Spring 用的是同一個 */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TEST_URL);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASS);
    }

    @Autowired
    SurveyVoteRepository surveyVoteRepository;

    /** 重建乾淨資料庫並套用全部 migration（含 V21，本測試的驗證對象） */
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
                連不到專用測試容器（%s）。本測試驗證的是資料庫層的併發防線，
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
     * 存一筆 RECIPIENT 身分票，能透過複合鍵找回同一筆，且 created/updated 時間
     * 由 {@code @PrePersist} 寫回實體欄位（非 NULL）。
     */
    @Test
    void 存一筆RECIPIENT票後可依複合鍵找回() {
        SurveyVote vote = new SurveyVote("reader-poll", "q1", "選項A",
            null, SurveyVote.CHANNEL_EMAIL, SurveyVote.IDENTITY_RECIPIENT, "recipient-1");

        SurveyVote saved = surveyVoteRepository.saveAndFlush(vote);

        assertTrue(saved.getId() > 0, "應產生自增主鍵");
        assertTrue(saved.getCreatedAt() != null, "created_at 應由 @PrePersist 寫回實體欄位");
        assertTrue(saved.getUpdatedAt() != null, "updated_at 應由 @PrePersist 寫回實體欄位");

        Optional<SurveyVote> found = surveyVoteRepository
            .findByFormKeyAndIdentityTypeAndIdentityKey("reader-poll", SurveyVote.IDENTITY_RECIPIENT, "recipient-1");

        assertTrue(found.isPresent(), "應能依 form_key + identity_type + identity_key 找回同一筆票");
        assertEquals("選項A", found.get().getOptionValue());
    }

    /**
     * 同一 form_key + identity_type + identity_key 的第二筆票必須被
     * {@code uq_survey_vote_identity} 擋下——這是「具名一人一票」規則的最終防線。
     */
    @Test
    void 同身分第二筆票撞唯一約束() {
        SurveyVote first = new SurveyVote("reader-poll", "q1", "選項A",
            null, SurveyVote.CHANNEL_EMAIL, SurveyVote.IDENTITY_RECIPIENT, "recipient-dup");
        surveyVoteRepository.saveAndFlush(first);

        SurveyVote dup = new SurveyVote("reader-poll", "q1", "選項B",
            null, SurveyVote.CHANNEL_EMAIL, SurveyVote.IDENTITY_RECIPIENT, "recipient-dup");

        assertThrows(DataIntegrityViolationException.class,
            () -> surveyVoteRepository.saveAndFlush(dup),
            "同一身分對同一問卷的第二筆票必須被唯一索引擋下");
    }

    /**
     * ANON（identityKey 為 null）不受唯一索引限制，同一問卷可以存多筆匿名票。
     */
    @Test
    void ANON身分兩筆都存成功() {
        SurveyVote anon1 = new SurveyVote("reader-poll", "q1", "選項A",
            null, SurveyVote.CHANNEL_WEB, SurveyVote.IDENTITY_ANON, null);
        SurveyVote anon2 = new SurveyVote("reader-poll", "q1", "選項B",
            null, SurveyVote.CHANNEL_WEB, SurveyVote.IDENTITY_ANON, null);

        surveyVoteRepository.saveAndFlush(anon1);
        surveyVoteRepository.saveAndFlush(anon2);

        assertFalse(anon1.getId().equals(anon2.getId()), "兩筆匿名票應各自產生不同主鍵，皆存成功");
    }
}
