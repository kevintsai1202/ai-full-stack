package world.springai.survey.mail;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * MAIL_PROVIDER=ses 時整個 Spring context 要能啟動，且 SES 相關 bean 都接上。
 *
 * <p>單元測試各自 new 物件，驗不到 {@code @ConditionalOnProperty}、{@code ObjectProvider}
 * 與 {@link MailQuotaService} 多個建構子的注入選擇；這些只有真的啟動 context 才看得出來。
 * 金鑰是假的，SES 客戶端建立時不連網；派送間隔調到很長，測試期間不會真的去派送。</p>
 */
@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.datasource.hikari.maximum-pool-size=2",
    "spring.datasource.hikari.minimum-idle=1",
    "app.mail.provider=ses",
    "app.mail.ses.access-key-id=AKIATESTONLY",
    "app.mail.ses.secret-access-key=test-only-secret",
    "app.mail.ses.dispatch-interval-ms=3600000"
})
class SesMailWiringTest {

    /** 取得環境變數，未設定或空字串時退回預設值 */
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
    private static final String TEST_DB = "survey_ses_wiring_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    /** 把連線三項交給 Spring context */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TEST_URL);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASS);
    }

    @Autowired ApplicationContext context;
    @Autowired MailSender mailSender;
    /** 負責登記 @Scheduled 方法的後處理器，用來確認派送器真的有被排程 */
    @Autowired ScheduledAnnotationBeanPostProcessor scheduler;

    /** 重建乾淨資料庫並套用全部 migration */
    @BeforeAll
    static void prepare() throws SQLException {
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS);
             Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + TEST_DB + " WITH (FORCE)");
            st.execute("CREATE DATABASE " + TEST_DB);
        } catch (SQLException e) {
            fail("連不到專用測試容器（" + ADMIN_URL + "），請先 docker start survey-test-db：" + e.getMessage());
        }
        Flyway.configure().dataSource(TEST_URL, USER, PASS).load().migrate();
    }

    /** SES 寄信實作、派送器、額度探測器都要建立，對外的 MailSender 仍是包過額度失效的版本 */
    @Test
    void sesBeansAreWired() {
        assertEquals(1, context.getBeansOfType(SesMailSender.class).size());
        assertEquals(1, context.getBeansOfType(ScheduledMailDispatcher.class).size());
        assertEquals(1, context.getBeansOfType(SesQuotaProbe.class).size());
        assertTrue(mailSender instanceof QuotaAwareMailSender);
    }

    /**
     * 派送器的兩個 @Scheduled 方法（tick、stuckCheck）真的被排程器登記。
     * 沒登記的話佇列永遠不會被清空，而其他測試全都看不出來。
     */
    @Test
    void dispatcherIsScheduled() {
        // Spring 6.2 會把 ScheduledMethodRunnable 包在 Task$OutcomeTrackingRunnable 裡，
        // 不能用 instanceof 判斷；兩者的 toString 都是「類別全名.方法名」
        List<String> names = scheduler.getScheduledTasks().stream()
            .map(task -> task.getTask().getRunnable().toString())
            .toList();
        String prefix = ScheduledMailDispatcher.class.getName() + ".";
        assertEquals(List.of(prefix + "stuckCheck", prefix + "tick"),
            names.stream().filter(name -> name.startsWith(prefix)).sorted().toList(),
            "已登記的排程：" + names);
    }
}
