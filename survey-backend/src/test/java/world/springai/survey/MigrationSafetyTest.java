package world.springai.survey;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * migration 的既有資料保全與 backfill 正確性測試。
 *
 * <p><b>涵蓋範圍不只 V7／V8</b>：下方流程只套用到 V6 來模擬正式資料庫現況，
 * 之後那道 {@code Flyway.migrate()} 沒有指定 {@code target}，也就是<b>一路套到最新版</b>。
 * 所以 V9 以及日後每一支新增的 migration 都自動由本測試守著「既有列一筆都不能少、
 * 一個字都不能變」。新增 migration 時<b>不需要</b>在這裡加什麼，但若那支 migration
 * 會（合理地）改動 {@code survey_response} 的既有欄位，就必須在
 * {@link #CHECKSUM_SQL} 的減號串後面補上欄名並在註解裡說明理由——
 * 否則本測試會紅，而那正是它該做的事。</p>
 *
 * <p><b>為什麼需要真實 PostgreSQL</b>：本專案用到 jsonb 與 @&gt; 運算子，H2 不支援。
 * 而「既有訂閱名單不可清除」是硬約束（spec §4.0）——訂閱者的同意是他們親自點確認信
 * 給出的，清掉就只能重新徵求。這道防線不該靠人記得跑腳本，所以做成每次 mvn test
 * 都會執行的自動化測試。</p>
 *
 * <p><b>為什麼不用 Testcontainers</b>：本機 Docker Desktop 29.6.1（API 1.55）與
 * docker-java 的 npipe 客戶端不相容，會誤報「Could not find a valid Docker
 * environment」，即使 docker CLI 與 named pipe 的手動 HTTP 請求都正常。已實測
 * testcontainers 1.21.0、2.0.5 與明確指定 DOCKER_HOST 皆無效。改為直接連本機
 * 專用測試容器。</p>
 *
 * <p><b>環境前提</b>：容器 survey-test-db 必須在執行中（見下方連線失敗時的指引）。
 * 連不上時本測試會明確失敗而非靜默跳過——寧可紅燈也不要假綠燈。</p>
 *
 * <p>流程：重建乾淨的測試資料庫 → 只套用 V1–V6（模擬正式庫現況）→ 塞入代表性
 * 既有資料 → 套用其餘全部 migration（V7 起，目前到 V9）→ 斷言既有資料逐列未變
 * 且 backfill 正確。</p>
 */
class MigrationSafetyTest {

    /** 取得環境變數，未設定或空字串時退回預設值；用於讓連線資訊可在別人的機器上覆寫 */
    private static String env(String name, String defaultValue) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }

    /** 測試資料庫主機，可用環境變數 MIGRATION_TEST_DB_HOST 覆寫（預設 127.0.0.1） */
    private static final String DB_HOST = env("MIGRATION_TEST_DB_HOST", "127.0.0.1");
    /** 測試資料庫連接埠，可用環境變數 MIGRATION_TEST_DB_PORT 覆寫（預設 5433） */
    private static final String DB_PORT = env("MIGRATION_TEST_DB_PORT", "5433");
    /** 連線帳號，可用環境變數 MIGRATION_TEST_DB_USER 覆寫（預設 postgres） */
    private static final String USER = env("MIGRATION_TEST_DB_USER", "postgres");
    /** 連線密碼，可用環境變數 MIGRATION_TEST_DB_PASSWORD 覆寫（預設 password） */
    private static final String PASS = env("MIGRATION_TEST_DB_PASSWORD", "password");
    /** 專用測試容器的維護資料庫連線（用於重建測試資料庫）；主機/埠/帳密皆可由環境變數覆寫 */
    private static final String ADMIN_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/postgres";
    /** 測試資料庫名稱；每次執行都會重建，只有本測試使用 */
    private static final String TEST_DB = "survey_migration_test";
    /** 測試資料庫連線 */
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    /**
     * 既有資料的指紋：涵蓋整列（排除新增的 last_engaged_at 與 version 欄位）。
     *
     * <p>用 {@code to_jsonb(t) - 'last_engaged_at' - 'version'} 而非列出個別欄位，是為了讓這道防線
     * 自動涵蓋所有既有欄位（name／role／experience／frontend_experience／interest／
     * budget／utm／answers／source／created_at…），不會因為漏列某個欄位而讓誤改寫
     * 的資料矇混過關。jsonb 序列化的 key 順序是決定性的，指紋才會穩定。</p>
     *
     * <p>減掉 {@code last_engaged_at} 是因為它是 V8 新增的欄位，migration 後
     * 必然從 NULL 被 backfill 成非 NULL（見 confirmedSubscribersAreBackfilled），
     * 屬於預期中的變動；{@code version} 則是 V15 的樂觀鎖欄位。兩者不應被既有資料指紋擋下。<b>未來若再新增欄位，一律在
     * 減號後面補上欄名</b>（例如 {@code to_jsonb(t) - 'last_engaged_at' - 'new_col'}），
     * 這樣既有欄位才會持續全數在防線內，不需要每次手動加回歸斷言。</p>
     */
    private static final String CHECKSUM_SQL = """
        SELECT md5(string_agg((to_jsonb(t) - 'last_engaged_at' - 'version')::text, ',' ORDER BY t.id))
          FROM survey_response t
        """;

    /** migration 前的 survey_response 筆數 */
    private static int beforeCount;
    /** migration 前的既有資料指紋 */
    private static String beforeChecksum;

    /** 重建測試資料庫 → 套用 V1–V6 → 塞既有資料 → 記錄狀態 → 套用 V7／V8 */
    @BeforeAll
    static void applyMigrations() throws SQLException {
        requireTestDatabase();
        recreateTestDatabase();

        // 只套用到 V6，模擬正式資料庫目前的狀態
        Flyway.configure()
            .dataSource(TEST_URL, USER, PASS)
            .target(MigrationVersion.fromVersion("6"))
            .load()
            .migrate();

        // 三種代表性的既有名單，以及正式環境中需要 V16 精準補資料的歷史 campaign。
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("""
                INSERT INTO survey_response (email, consent, unsubscribed, source) VALUES
                  ('confirmed@example.com', TRUE,  FALSE, 'survey_form'),
                  ('pending@example.com',   FALSE, FALSE, 'exam'),
                  ('gone@example.com',      TRUE,  TRUE,  'survey_form')
                """);
            st.execute("""
                INSERT INTO campaign (subject, markdown, mode, recipient_count,
                                      accepted_count, failed_count, status)
                VALUES ('既有電子報', '# 內容', 'now', 1, 1, 0, 'sent')
                """);
            st.execute("""
                INSERT INTO campaign (
                    id, subject, markdown, mode, recipient_count,
                    accepted_count, failed_count, status, created_at
                ) VALUES
                  (3, '我自己寫了一套問卷＋電子報系統，這是我學到的事',
                      '# 已取消', 'schedule', 0, 0, 0, 'cancelled',
                      TIMESTAMPTZ '2026-07-23 23:56:31Z'),
                  (4, '我自己寫了一套問卷＋電子報系統，這是我學到的事',
                      '# 已寄出', 'schedule', 62, 62, 0, 'sent',
                      TIMESTAMPTZ '2026-07-24 10:44:17Z'),
                  (7, 'RAG的應用範例',
                      '# 已取消', 'schedule', 0, 0, 0, 'cancelled',
                      TIMESTAMPTZ '2026-07-26 19:58:43Z'),
                  (8, 'RAG的應用範例',
                      '# 已寄出', 'now', 78, 78, 0, 'sent',
                      TIMESTAMPTZ '2026-07-26 20:08:47Z')
                """);
        }

        beforeCount = queryInt("SELECT count(*) FROM survey_response");
        beforeChecksum = queryString(CHECKSUM_SQL);

        // 套用其餘全部 migration（V7 起，刻意不指定 target，新增的 migration 自動涵蓋）
        Flyway.configure()
            .dataSource(TEST_URL, USER, PASS)
            .load()
            .migrate();
    }

    /** 既有列一筆都不能少，email 與同意狀態一個字都不能變 */
    @Test
    void existingRowsAreUntouched() throws SQLException {
        assertEquals(beforeCount, queryInt("SELECT count(*) FROM survey_response"),
            "migration 後 survey_response 筆數改變");
        assertEquals(beforeChecksum, queryString(CHECKSUM_SQL),
            "migration 後 email／consent／unsubscribed 有變動");
    }

    /**
     * 已確認訂閱者必須被回填 last_engaged_at。
     *
     * <p>若不回填，階段 F 的參與度分級會因「已寄多期 + last_engaged_at 為 NULL」
     * 把老訂閱者整批判為 sunset 而停寄——資料沒少但收不到信，且要到下次發送才顯現。</p>
     */
    @Test
    void confirmedSubscribersAreBackfilled() throws SQLException {
        assertEquals(1, queryInt("""
            SELECT count(*) FROM survey_response
             WHERE consent = TRUE AND unsubscribed = FALSE AND last_engaged_at IS NOT NULL
            """), "已確認訂閱者未被回填 last_engaged_at");
    }

    /** 待確認與已退訂者刻意不回填，保持 NULL（回填會造出假的參與紀錄） */
    @Test
    void nonSubscribersAreNotBackfilled() throws SQLException {
        assertEquals(0, queryInt("""
            SELECT count(*) FROM survey_response
             WHERE (consent = FALSE OR unsubscribed = TRUE) AND last_engaged_at IS NOT NULL
            """), "未確認或已退訂者被誤回填");
    }

    /** V7 的五張新表都要建立在 public schema（避免同名表存在於其他 schema 而誤判通過） */
    @Test
    void newTablesAreCreated() throws SQLException {
        for (String table : new String[] {"app_setting", "reader", "credit_txn", "article_access", "login_token"}) {
            assertEquals(1, queryInt(
                "SELECT count(*) FROM information_schema.tables"
                    + " WHERE table_schema = 'public' AND table_name = '" + table + "'"),
                "資料表 " + table + " 未建立");
        }
    }

    /** V14 只新增媒體表與 nullable 封面外鍵，不改寫既有 campaign。 */
    @Test
    void articleMediaSchemaIsAdditiveAndReady() throws SQLException {
        assertEquals(1, queryInt("""
            SELECT count(*) FROM information_schema.tables
             WHERE table_schema = 'public' AND table_name = 'media_asset'
            """));
        assertEquals(1, queryInt("""
            SELECT count(*) FROM information_schema.columns
             WHERE table_schema = 'public' AND table_name = 'campaign'
               AND column_name = 'cover_media_id' AND is_nullable = 'YES'
            """));
        assertEquals(0, queryInt("SELECT count(*) FROM campaign WHERE cover_media_id IS NOT NULL"),
            "既有文章不得被自動綁定任意封面");
    }

    /** V10 的彈性名單底座必須完整建立，避免只建立人物表卻無法保存活動、Fact 或匯入稽核。 */
    @Test
    void flexibleAudienceTablesAreCreated() throws SQLException {
        for (String table : new String[] {
                "audience_person", "audience_consent", "audience_identity",
                "audience_record", "audience_fact", "form_definition", "form_field",
                "import_profile", "import_batch", "import_item",
                "audience_selection_snapshot", "audience_selection_target",
                "audience_bulk_operation", "audience_segment",
                "integration_sync_cursor", "audience_data_request", "audience_suppression"
        }) {
            assertEquals(1, queryInt(
                "SELECT count(*) FROM information_schema.tables"
                    + " WHERE table_schema = 'public' AND table_name = '" + table + "'"),
                "資料表 " + table + " 未建立");
        }
    }

    /** 相同 Email 的多筆舊問卷要合成一個人物，但每一筆歷史活動都必須保留。 */
    @Test
    void legacySurveyRowsAreBackfilledWithoutLosingHistory() throws SQLException {
        assertEquals(3, queryInt("SELECT count(*) FROM audience_person"),
            "既有三個 Email 應回填成三個唯一人物");
        assertEquals(beforeCount, queryInt("""
            SELECT count(*) FROM audience_record
             WHERE record_type = 'survey_submission'
               AND external_record_id LIKE 'survey_response:%'
            """), "每一筆 survey_response 都必須有對應活動");
    }

    /** 退訂狀態優先於已確認；匯入後不得把退訂者重新視為可寄送。 */
    @Test
    void legacyConsentPrecedenceIsPreserved() throws SQLException {
        assertEquals("CONFIRMED", queryString("""
            SELECT c.status
              FROM audience_consent c
              JOIN audience_person p ON p.id = c.person_id
             WHERE p.email_normalized = 'confirmed@example.com'
             ORDER BY c.occurred_at DESC, c.id DESC
             LIMIT 1
            """));
        assertEquals("PENDING", queryString("""
            SELECT c.status
              FROM audience_consent c
              JOIN audience_person p ON p.id = c.person_id
             WHERE p.email_normalized = 'pending@example.com'
             ORDER BY c.occurred_at DESC, c.id DESC
             LIMIT 1
            """));
        assertEquals("UNSUBSCRIBED", queryString("""
            SELECT c.status
              FROM audience_consent c
              JOIN audience_person p ON p.id = c.person_id
             WHERE p.email_normalized = 'gone@example.com'
             ORDER BY c.occurred_at DESC, c.id DESC
             LIMIT 1
            """));
    }

    /**
     * 參數初始值要逐項核對 8 組 key 與 value（而非只驗筆數與抽驗 2 筆）。
     *
     * <p>若只驗 count=8 與部分 key，一旦某個 key 名稱打錯（例如
     * engagement.active_days 誤植為 engagement.active_day），count 仍是 8、
     * 測試照樣全綠，但上線後讀取端查不到該 key 會靜默退回程式內建預設值，
     * 參數調整從此無效且不會有任何錯誤訊息。逐項斷言才能守住這道防線。</p>
     */
    @Test
    void appSettingsAreSeeded() throws SQLException {
        assertEquals(8, queryInt("SELECT count(*) FROM app_setting"), "app_setting 初始值筆數不符");
        assertEquals("300", queryString(
            "SELECT value FROM app_setting WHERE setting_key = 'credit.signup_grant'"));
        assertEquals("10", queryString(
            "SELECT value FROM app_setting WHERE setting_key = 'credit.premium_cost'"));
        assertEquals("100", queryString(
            "SELECT value FROM app_setting WHERE setting_key = 'credit.referral_reward'"));
        assertEquals("365", queryString(
            "SELECT value FROM app_setting WHERE setting_key = 'vip.default_days'"));
        assertEquals("6", queryString(
            "SELECT value FROM app_setting WHERE setting_key = 'engagement.dormant_after_campaigns'"));
        assertEquals("12", queryString(
            "SELECT value FROM app_setting WHERE setting_key = 'engagement.sunset_after_campaigns'"));
        assertEquals("90", queryString(
            "SELECT value FROM app_setting WHERE setting_key = 'engagement.active_days'"));
        assertEquals("365", queryString(
            "SELECT value FROM app_setting WHERE setting_key = 'engagement.sunset_days'"));
    }

    /** 非 V16 精準回填目標的既有 campaign 應取得新欄位預設值，且不得被自動發布。 */
    @Test
    void existingCampaignGetsColumnDefaults() throws SQLException {
        assertEquals(0, queryInt("""
            SELECT count(*) FROM campaign
             WHERE id NOT IN (4, 8)
               AND (tier IS DISTINCT FROM 'BASIC'
                OR  credit_cost <> 0
                OR  filter_levels IS DISTINCT FROM 'active'
                OR  vip_full_in_mail IS DISTINCT FROM FALSE
                OR  slug IS NOT NULL
                OR  published_at IS NOT NULL)
            """), "既有 campaign 未取得新欄位的預設值");
    }

    /** V16 只發布兩篇已寄出但缺少公開欄位的歷史文章，並補上 Emoji 與 hashtag。 */
    @Test
    void historicalArticlesAreBackfilledWithMetadata() throws SQLException {
        assertEquals("survey-newsletter-system-lessons-20260724", queryString(
            "SELECT slug FROM campaign WHERE id = 4"));
        assertEquals("rag-law-powers-ai-verification-20260726", queryString(
            "SELECT slug FROM campaign WHERE id = 8"));
        assertEquals(2, queryInt("""
            SELECT count(*) FROM campaign
             WHERE id IN (4, 8)
               AND published_at = created_at
               AND cover_emoji IS NOT NULL
            """), "兩篇遺漏文章未完整補上發布時間與 Emoji");
        assertEquals("全端開發,電子報經營", queryString("""
            SELECT string_agg(t.normalized_key, ',' ORDER BY t.sort_order)
              FROM campaign_tag ct
              JOIN content_tag t ON t.id = ct.tag_id
             WHERE ct.campaign_id = 4
            """));
        assertEquals("ai,ai agent,rag", queryString("""
            SELECT string_agg(t.normalized_key, ',' ORDER BY t.sort_order)
              FROM campaign_tag ct
              JOIN content_tag t ON t.id = ct.tag_id
             WHERE ct.campaign_id = 8
            """));
    }

    /** 同主旨的 cancelled 排程只是重複草稿，V16 不得把它們發布或加上中繼資料。 */
    @Test
    void cancelledCampaignDuplicatesStayHidden() throws SQLException {
        assertEquals(0, queryInt("""
            SELECT count(*) FROM campaign
             WHERE id IN (3, 7)
               AND (slug IS NOT NULL OR published_at IS NOT NULL OR cover_emoji IS NOT NULL)
            """), "已取消的重複排程不應被發布");
        assertEquals(0, queryInt(
            "SELECT count(*) FROM campaign_tag WHERE campaign_id IN (3, 7)"),
            "已取消的重複排程不應取得 hashtag");
    }

    /** PREMIUM 卻沒有解鎖成本必須被 CHECK 約束擋下——否則進階內容會全面免費外洩 */
    @Test
    void premiumWithoutCostIsRejected() {
        assertThrows(SQLException.class, () -> {
            try (Connection c = connect(); Statement st = c.createStatement()) {
                st.execute("""
                    INSERT INTO campaign (subject, markdown, mode, recipient_count,
                                          accepted_count, failed_count, status, tier, credit_cost)
                    VALUES ('壞資料', '# x', 'now', 0, 0, 0, 'sent', 'PREMIUM', 0)
                    """);
            }
        }, "tier=PREMIUM 且 credit_cost=0 應被 CHECK 約束拒絕");
    }

    /**
     * 確認本機測試容器可用；連不上時以明確指引失敗。
     *
     * <p>刻意不用 assumeTrue 跳過：這個測試守的是「既有訂閱名單不可清除」，
     * 靜默跳過等於讓防線失效卻顯示綠燈。</p>
     */
    private static void requireTestDatabase() {
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS)) {
            // 能連上即可
        } catch (SQLException e) {
            fail("""
                連不上本機測試資料庫（%s，帳號：%s）。

                本測試驗證 migration 不會破壞既有訂閱名單（spec §4.0 的硬約束），
                不能靜默跳過。連線資訊可用環境變數 MIGRATION_TEST_DB_HOST／
                MIGRATION_TEST_DB_PORT／MIGRATION_TEST_DB_USER／
                MIGRATION_TEST_DB_PASSWORD 覆寫。請先啟動專用測試容器：

                  docker start survey-test-db

                容器不存在時建立它（不要用 5432，那是別的專案的容器）：

                  docker run -d --name survey-test-db -e POSTGRES_PASSWORD=password ^
                    -p 5433:5432 pgvector/pgvector:pg18

                原始錯誤：%s""".formatted(ADMIN_URL, USER, e.getMessage()));
        }
    }

    /**
     * 重建乾淨的測試資料庫。
     *
     * <p>WITH (FORCE) 會斷開既有連線（PostgreSQL 13+），避免前次執行殘留的連線
     * 導致 DROP 失敗。此處 DROP 的是本測試專屬的資料庫，與 spec §4.0 禁止
     * 對正式資料執行 DROP 並不衝突。</p>
     */
    private static void recreateTestDatabase() throws SQLException {
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS);
             Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + TEST_DB + " WITH (FORCE)");
            st.execute("CREATE DATABASE " + TEST_DB);
        }
    }

    /** 取得測試資料庫連線 */
    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(TEST_URL, USER, PASS);
    }

    /** 執行單值整數查詢 */
    private static int queryInt(String sql) throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(), "查詢無結果：" + sql);
            return rs.getInt(1);
        }
    }

    /** 執行單值字串查詢 */
    private static String queryString(String sql) throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(), "查詢無結果：" + sql);
            return rs.getString(1);
        }
    }
}
