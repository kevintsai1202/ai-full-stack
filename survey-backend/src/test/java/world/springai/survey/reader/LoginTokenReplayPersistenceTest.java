package world.springai.survey.reader;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 以真實 PostgreSQL 執行<b>生產查詢本體</b>
 * {@link LoginTokenRepository#markUsedIfUnused}，釘住 magic link 的一次性保證。
 *
 * <p><b>為什麼必須有這一支</b>：{@code LoginTokenService.consume} 的 javadoc 明寫
 * 「唯一的保證來自 {@code markUsedIfUnused} 這個帶條件的原子 UPDATE」，
 * 而在本測試出現之前，那個保證<b>完全靠人工閱讀維持</b>——
 * {@code LoginTokenServiceTest} 把 repository mock 掉（{@code and t.usedAt is null}
 * 這個條件在那裡只是一個被 stub 的回傳值），{@code ReaderAuthControllerTest} 也是。
 * 實測結果（不是推測）：把 {@code and t.usedAt is null} 整個刪掉，
 * 改動前的全套 501 條測試<b>維持全綠</b>。</p>
 *
 * <p><b>失效情境</b>：登入連結可被重放。而 magic link 會以明文出現在
 * ① 使用者的收件匣（信可能被轉寄）、② 瀏覽器歷史與 referrer、
 * ③ 郵件伺服器與安全閘道的連結預掃描紀錄——任一情況都足以讓別人再登入一次，
 * <b>且完全靜默</b>（端點對成功與失敗回同一個頁面，不會有任何日誌說「這個連結被用第二次」）。</p>
 *
 * <p><b>為什麼直接呼叫 repository 而不是只走 {@code consume}</b>：
 * {@code consume} 內有一段 {@code token.isUsed()} 的<b>快速路徑</b>檢查，
 * 第二次呼叫會在那裡就回 empty——所以就算把 {@code and t.usedAt is null} 拆掉，
 * 「連續兩次 {@code consume}」的測試依然全綠。那個檢查<b>不是</b>正確性保證
 * （併發下兩個請求可以都讀到「未使用」而都通過它），只是省一次 UPDATE。
 * 要驗真正的防線，必須繞過快速路徑直接打那道 UPDATE，
 * 見 {@link #markUsedIfUnusedSucceedsOnceThenRefusesTheReplay}。</p>
 *
 * <p>連線與容器要求同 {@link UnlockConstraintTest}（同一個 {@code survey-test-db} 容器、
 * 獨立資料庫名稱、{@code MIGRATION_TEST_DB_*} 可覆寫），連不上時以明確中文訊息失敗，
 * <b>不靜默跳過</b>。</p>
 */
@SpringBootTest(properties = {
    // 資料庫已由 @BeforeAll 以 Flyway 建好，Spring 這邊不再跑一次
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=validate",
    // 限制連線池大小：每個 @SpringBootTest 屬性組合都是一個獨立且被快取的
    // ApplicationContext，各帶一個預設 10 條連線的 Hikari 池，全庫加起來會撞上
    // PostgreSQL 的 max_connections=100（實測撞過，而錯誤訊息會偽裝成「連不到容器」）。
    "spring.datasource.hikari.maximum-pool-size=2",
    "spring.datasource.hikari.minimum-idle=1"
    // 連線三項由 @DynamicPropertySource 提供（註解裡的字面值無法覆寫）
})
class LoginTokenReplayPersistenceTest {

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
    private static final String TEST_DB = "survey_login_token_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    /** 把連線三項交給 Spring context（註解裡的字面值無法由環境變數覆寫） */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TEST_URL);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASS);
    }

    /** 讀者 email 是 PII，一律用不可能寄達的 .example 網域 */
    private static final String EMAIL = "magic@login-token.example";
    /** 資料庫裡只存雜湊；本測試自己造一個固定值，不必真的算 SHA-256 */
    private static final String TOKEN_HASH = "hash-of-a-fake-magic-link-token";
    /** 第一次兌換的時間 */
    private static final OffsetDateTime FIRST_USE = OffsetDateTime.parse("2026-07-26T10:00:00.000000Z");
    /** 重放（第二次兌換）的時間；刻意與第一次不同，才能斷言 used_at 沒被覆寫 */
    private static final OffsetDateTime REPLAY = OffsetDateTime.parse("2026-07-26T10:05:00.000000Z");

    @Autowired LoginTokenRepository loginTokenRepository;
    @Autowired LoginTokenService loginTokenService;
    @Autowired DataSource dataSource;

    /** 重建乾淨資料庫並套用全部 migration（需要 V7 建立的 login_token） */
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

    /** 連不上專用測試容器時以明確訊息失敗，並附上啟動指令 */
    private static void requireTestDatabase() {
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS)) {
            // 連得上即可
        } catch (SQLException e) {
            fail("""
                連不到專用測試容器（%s）。本測試執行的是 magic link 一次性保證的
                生產查詢本體（markUsedIfUnused），無法用 mock 取代，因此不能靜默跳過。
                請先啟動容器：
                  docker start survey-test-db
                若容器不存在：
                  docker run -d --name survey-test-db -e POSTGRES_PASSWORD=password \\
                    -p 5433:5432 pgvector/pgvector:pg18
                連線資訊可用 MIGRATION_TEST_DB_HOST／PORT／USER／PASSWORD 覆寫。
                原始錯誤：%s
                """.formatted(ADMIN_URL, e.getMessage()));
        }
    }

    /** 每個測試前重建一筆未使用、未過期的 token（只碰本測試建的列） */
    @BeforeEach
    void seedUnusedToken() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM login_token WHERE email LIKE '%@login-token.example'");
            st.executeUpdate("INSERT INTO login_token (token_hash, email, expires_at) VALUES ('"
                + TOKEN_HASH + "', '" + EMAIL + "', '2030-01-01T00:00:00Z')");
        }
    }

    /** 讀某個 token 的 used_at；NULL 時回 null */
    private OffsetDateTime usedAt(String hash) throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT used_at FROM login_token WHERE token_hash = '"
                 + hash + "'")) {
            rs.next();
            return rs.getObject(1, OffsetDateTime.class);
        }
    }

    /**
     * ★ 同一個 token 連續兌換兩次：第一次回 1、第二次回 <b>0</b>，
     * 且 {@code used_at} 必須維持第一次的時間、不被第二次覆寫。
     *
     * <p>把 {@code and t.usedAt is null} 從 JPQL 刪掉，這條會拿到 {@code 1, 1}
     * 且 {@code used_at} 被改成 {@link #REPLAY}——那正是它存在的理由。
     * 那個狀態的真實後果是：一條登入連結可以無限次登入，
     * 而 {@code LoginTokenService.consume} 會對每一次都回傳 email、簽出一個新 session。</p>
     *
     * <p>{@code used_at} 不得被覆寫是獨立的一項：它是「這個連結什麼時候被兌換」的
     * 唯一稽核紀錄，被後到的請求蓋掉之後，事後追查誰在什麼時候登入就失去依據。</p>
     */
    @Test
    void markUsedIfUnusedSucceedsOnceThenRefusesTheReplay() throws SQLException {
        int first = loginTokenRepository.markUsedIfUnused(TOKEN_HASH, FIRST_USE);
        int replay = loginTokenRepository.markUsedIfUnused(TOKEN_HASH, REPLAY);

        assertEquals(1, first, "第一次兌換必須成功（別把防線「修」成連合法登入都擋掉）");
        assertEquals(0, replay,
            "第二次兌換必須回 0：回 1 代表 magic link 可被重放，"
                + "而連結會出現在轉寄的信件、瀏覽器歷史與郵件閘道的預掃描紀錄裡");

        OffsetDateTime stored = usedAt(TOKEN_HASH);
        assertNotNull(stored, "used_at 沒有被寫入：token 仍是未使用狀態");
        assertEquals(FIRST_USE.toInstant(), stored.toInstant(),
            "used_at 被第二次兌換覆寫了，「這個連結何時被兌換」的稽核紀錄就此消失");
    }

    /** 不存在的雜湊必須回 0，呼叫端才能據此拒絕登入（而不是誤以為兌換成功） */
    @Test
    void unknownHashAffectsNothing() throws SQLException {
        assertEquals(0, loginTokenRepository.markUsedIfUnused("hash-that-was-never-issued", FIRST_USE));
        assertTrue(usedAt(TOKEN_HASH) == null, "不存在的雜湊不該動到別人的 token");
    }

    /**
     * 端到端：{@code LoginTokenService.consume} 對同一條 magic link 只成功一次。
     *
     * <p><b>這一條不是防線的驗證</b>（見類別 javadoc）：{@code consume} 內的
     * {@code token.isUsed()} 快速路徑會讓第二次呼叫在打 UPDATE 之前就回 empty，
     * 所以拆掉 {@code and t.usedAt is null} 之後這條依然會綠。
     * 它的用途是釘住「兌換成功會回傳正確的 email」與「快速路徑本身沒壞」，
     * 真正的防線由 {@link #markUsedIfUnusedSucceedsOnceThenRefusesTheReplay} 守著。</p>
     */
    @Test
    void consumeReturnsEmailOnceAndThenRefuses() {
        String rawToken = loginTokenService.issue(EMAIL, FIRST_USE);

        Optional<String> first = loginTokenService.consume(rawToken, FIRST_USE);
        Optional<String> second = loginTokenService.consume(rawToken, REPLAY);

        assertEquals(Optional.of(EMAIL), first, "第一次兌換必須回傳該 token 的 email");
        assertTrue(second.isEmpty(), "同一條 magic link 不得兌換第二次");
    }
}
