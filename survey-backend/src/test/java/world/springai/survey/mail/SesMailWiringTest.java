package world.springai.survey.mail;

import io.github.kevintsai1202.sesmail.DeliveryListener;
import io.github.kevintsai1202.sesmail.ScheduledMailDispatcher;
import io.github.kevintsai1202.sesmail.SesMailClient;
import io.github.kevintsai1202.sesmail.SesQuotaService;
import io.github.kevintsai1202.sesmail.events.SesEventListener;
import org.flywaydb.core.Flyway;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import world.springai.survey.audience.SesSuppressionListener;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SESMAIL_ENABLED=true 時整個 Spring context 要能啟動，且 starter 的 SES bean 都接上。
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
    "sesmail.enabled=true",
    "sesmail.access-key-id=AKIATESTONLY",
    "sesmail.secret-access-key=test-only-secret",
    "sesmail.dispatch-interval=1h",
    "sesmail.sns.enabled=true",
    "sesmail.sns.topic-arns=arn:aws:sns:ap-northeast-1:123456789012:ses-events"
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
    /** 用來建 MockMvc，走過完整的 filter 鏈 */
    @Autowired WebApplicationContext webContext;

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

    /** starter 的客戶端、派送器、額度服務都建立，派送器已啟動；對外的 MailSender 仍是包過額度失效的版本 */
    @Test
    void sesBeansAreWired() {
        assertEquals(1, context.getBeansOfType(SesMailClient.class).size());
        assertEquals(1, context.getBeansOfType(SesQuotaService.class).size());
        assertTrue(context.getBean(ScheduledMailDispatcher.class).isRunning(), "派送器必須隨 context 啟動");
        assertTrue(mailSender instanceof QuotaAwareMailSender);
    }

    /** email_log 回寫的 listener 有登記成 bean，starter 才找得到 */
    @Test
    void deliveryListenerIsRegistered() {
        assertTrue(context.getBeansOfType(DeliveryListener.class).values().stream()
            .anyMatch(l -> l instanceof EmailLogDeliveryListener));
    }

    /**
     * 退信／檢舉 webhook 要經過本專案所有 filter（Host 白名單、安全標頭）後仍打得到 controller：
     * 偽造的訊息應由 starter 驗簽拒絕回 403，而不是被 filter 擋成 404 或被導向。
     */
    @Test
    void snsWebhookIsReachableThroughFilters() throws Exception {
        assertTrue(context.getBeansOfType(SesEventListener.class).values().stream()
            .anyMatch(l -> l instanceof SesSuppressionListener));
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(webContext)
            .addFilters(context.getBeansOfType(jakarta.servlet.Filter.class).values().toArray(new jakarta.servlet.Filter[0]))
            .build();
        mvc.perform(post("/sesmail/sns").contentType(MediaType.TEXT_PLAIN)
                .content("{\"Type\":\"Notification\",\"SigningCertURL\":\"https://evil.example.com/c.pem\"}"))
            .andExpect(status().isForbidden());
    }
}
