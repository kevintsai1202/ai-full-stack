package world.springai.survey.audience;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import world.springai.survey.form.FormSchemaService;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.containsString;

/** 使用真實 PostgreSQL 驗證人物合併、冪等活動、退訂保護與動態 schema。 */
class FlexibleAudiencePlatformIntegrationTest {

    /** 取得可覆寫的測試資料庫環境參數。 */
    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static final String HOST = env("MIGRATION_TEST_DB_HOST", "127.0.0.1");
    private static final String PORT = env("MIGRATION_TEST_DB_PORT", "5433");
    private static final String USER = env("MIGRATION_TEST_DB_USER", "postgres");
    private static final String PASS = env("MIGRATION_TEST_DB_PASSWORD", "password");
    private static final String DB = "survey_flexible_audience_test";
    private static final String ADMIN_URL = "jdbc:postgresql://" + HOST + ":" + PORT + "/postgres";
    private static final String URL = "jdbc:postgresql://" + HOST + ":" + PORT + "/" + DB;

    private static JdbcTemplate jdbc;
    private static AudiencePlatformService audience;
    private static FormSchemaService forms;

    /** 重建專用資料庫並套用全部 migration，測試不依賴開發資料庫狀態。 */
    @BeforeAll
    static void prepareDatabase() throws SQLException {
        requireDatabase();
        try (Connection connection = DriverManager.getConnection(ADMIN_URL, USER, PASS);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + DB + " WITH (FORCE)");
            statement.execute("CREATE DATABASE " + DB);
        }
        Flyway.configure().dataSource(URL, USER, PASS).load().migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(URL, USER, PASS);
        jdbc = new JdbcTemplate(dataSource);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        audience = new AudiencePlatformService(jdbc, objectMapper);
        SurveyResponseRepository legacyRepository = mock(SurveyResponseRepository.class);
        when(legacyRepository.save(any(SurveyResponse.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        forms = new FormSchemaService(jdbc, objectMapper, audience, legacyRepository);
    }

    /** 相同 Email 不分大小寫與空白只建立一個人物。 */
    @Test
    void sameEmailMergesIntoOnePerson() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AudiencePlatformService.PersonResult first =
            audience.mergePerson(" Alice@Example.com ", "Alice", now);
        AudiencePlatformService.PersonResult second =
            audience.mergePerson("alice@example.com", "Alice Updated", now.plusMinutes(1));

        assertTrue(first.created());
        assertFalse(second.created());
        assertEquals(first.personId(), second.personId());
        assertEquals(1, jdbc.queryForObject("""
            SELECT count(*) FROM audience_person
             WHERE email_normalized = 'alice@example.com'
            """, Integer.class));
    }

    /** 同一外部活動重跑不新增，內容改變才更新。 */
    @Test
    void recordImportIsIdempotentAndUpdateAware() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        long personId = audience.mergePerson("record@example.com", "Record", now).personId();
        AudiencePlatformService.RecordResult created = audience.upsertRecord(
            personId, "exam", "exam_attempt", "exam@1", "attempt-1",
            now, Map.of("score", 7), Map.of("title", "測驗"));
        AudiencePlatformService.RecordResult unchanged = audience.upsertRecord(
            personId, "exam", "exam_attempt", "exam@1", "attempt-1",
            now, Map.of("score", 7), Map.of("title", "測驗"));
        AudiencePlatformService.RecordResult updated = audience.upsertRecord(
            personId, "exam", "exam_attempt", "exam@1", "attempt-1",
            now, Map.of("score", 8), Map.of("title", "測驗"));

        assertEquals("CREATED", created.status());
        assertEquals("UNCHANGED", unchanged.status());
        assertEquals("UPDATED", updated.status());
        assertEquals(1, jdbc.queryForObject("""
            SELECT count(*) FROM audience_record
             WHERE source_key = 'exam' AND external_record_id = 'attempt-1'
            """, Integer.class));
    }

    /** 退訂是終止狀態；後續匯入 pending 不得把它蓋回去。 */
    @Test
    void importCannotOverrideUnsubscribe() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        long personId = audience.mergePerson("gone@example.com", "Gone", now).personId();
        assertTrue(audience.appendConsent(
            personId, AudiencePlatformService.CONSENT_CONFIRMED,
            "survey", null, Map.of(), now));
        assertTrue(audience.appendConsent(
            personId, AudiencePlatformService.CONSENT_UNSUBSCRIBED,
            "unsubscribe-link", null, Map.of(), now.plusSeconds(1)));
        assertFalse(audience.appendConsent(
            personId, AudiencePlatformService.CONSENT_PENDING,
            "exam", null, Map.of(), now.plusSeconds(2)));

        assertEquals("UNSUBSCRIBED", jdbc.queryForObject("""
            SELECT status FROM audience_consent
             WHERE person_id = ?
             ORDER BY occurred_at DESC, id DESC
             LIMIT 1
            """, String.class, personId));
    }

    /** 新增 form_field 後，提交與 analytics 不改 Java 就會出現新 dimension。 */
    @Test
    void newlyConfiguredFieldAppearsInSubmissionAndAnalytics() {
        long definitionId = jdbc.queryForObject("""
            SELECT id FROM form_definition
             WHERE form_key = 'fullstack-course-interest' AND version = 1
            """, Long.class);
        jdbc.update("""
            INSERT INTO form_field (
                form_definition_id, field_key, label, field_type,
                analytics_enabled, analytics_view, filterable,
                sensitive, public_analytics, display_order, fact_key
            ) VALUES (?, 'favoriteTool', '最常用工具', 'select',
                      TRUE, 'bar', TRUE, FALSE, FALSE, 999, 'profile.favorite_tool')
            """, definitionId);

        forms.submit("fullstack-course-interest", new FormSchemaService.SubmissionRequest(
            "schema@example.com",
            "Schema User",
            Map.of("favoriteTool", "Codex"),
            Map.of("source", "test"),
            false,
            null));
        Map<String, Object> analytics = forms.analytics(
            "fullstack-course-interest", 1, false, null, null, null, false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dimensions =
            (List<Map<String, Object>>) analytics.get("dimensions");

        assertTrue(dimensions.stream()
            .anyMatch(dimension -> "favoriteTool".equals(dimension.get("fieldKey"))));
        assertEquals("Codex", jdbc.queryForObject("""
            SELECT value_text FROM audience_fact
             WHERE fact_key = 'profile.favorite_tool'
            """, String.class));
    }

    /** 已發布版本不可原地修改；新草稿可加欄位、發布並匯出動態原始資料。 */
    @Test
    void formVersionWorkflowPreservesPublishedSchemaAndExportsNewField() {
        FormSchemaService.FormDefinition draft = forms.createVersion(
            "fullstack-course-interest",
            new FormSchemaService.VersionRequest("全端課程興趣新版"));
        forms.addField(
            draft.key(),
            draft.version(),
            "companySize",
            new FormSchemaService.FieldRequest(
                "公司規模",
                "select",
                true,
                List.of("1-10", "11-50"),
                true,
                "bar",
                true,
                false,
                false,
                1000,
                "survey.company_size"));
        FormSchemaService.FormDefinition published =
            forms.publish(draft.key(), draft.version());

        assertEquals("PUBLISHED", published.status());
        assertEquals("ARCHIVED", forms.getDefinition(draft.key(), 1).status());
        forms.submit(draft.key(), new FormSchemaService.SubmissionRequest(
            "versioned-form@example.com",
            "Versioned User",
            Map.of("companySize", "11-50"),
            Map.of(),
            false,
            null));
        List<Map<String, Object>> rows = forms.rawRecords(
            draft.key(), draft.version(), false, null, null, null);

        assertTrue(rows.stream().anyMatch(row ->
            "versioned-form@example.com".equals(row.get("email"))
                && row.get("answers") instanceof Map<?, ?> answers
                && "11-50".equals(answers.get("companySize"))));
    }

    /** 複合篩選在伺服器端執行，來源與同意狀態不會讓同一人物重複出現。 */
    @Test
    void audienceSearchFiltersBySourceAndConsent() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AudiencePlatformService.PersonResult matching =
            audience.mergePerson("search-match@example.com", "Search Match", now);
        audience.upsertIdentity(matching.personId(), "exam", "student_profile", "search-1", now);
        audience.appendConsent(
            matching.personId(), AudiencePlatformService.CONSENT_CONFIRMED,
            "survey", null, Map.of(), now);
        AudiencePlatformService.PersonResult pending =
            audience.mergePerson("search-pending@example.com", "Search Pending", now);
        audience.upsertIdentity(pending.personId(), "exam", "student_profile", "search-2", now);
        audience.appendConsent(
            pending.personId(), AudiencePlatformService.CONSENT_PENDING,
            "exam", null, Map.of(), now);

        AudienceSearchService search = new AudienceSearchService(jdbc);
        AudienceSearchService.Filters filters = new AudienceSearchService.Filters(
            "search-", List.of("exam"), List.of("CONFIRMED"),
            null, null, null, null, null, null, null);
        AudienceSearchService.SearchResult result = search.search(
            new AudienceSearchService.SearchRequest(
                filters, new AudienceSearchService.Sort("email", "ASC"), 0, 50));

        assertEquals(1, result.total());
        assertEquals(matching.personId(), result.items().getFirst().get("personId"));
    }

    /** Preview 固定資格，Execute 相同冪等鍵重送只呼叫一次加點服務。 */
    @Test
    void bulkCreditsUseSnapshotAndIdempotencyKey() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AudiencePlatformService.PersonResult withReader =
            audience.mergePerson("bulk-reader@example.com", "Bulk Reader", now);
        AudiencePlatformService.PersonResult withoutReader =
            audience.mergePerson("bulk-no-account@example.com", "Bulk No Account", now);
        jdbc.update("""
            INSERT INTO reader (email, tier, credits, referral_code)
            VALUES ('bulk-reader@example.com', 'FREE', 0, 'BULK-READER')
            """);
        AudienceReaderOperations readerService = mock(AudienceReaderOperations.class);
        when(readerService.grantCreditsForAudience(
            any(String.class), any(Integer.class), any(String.class))).thenReturn(true);
        AudienceBulkOperationService bulk = new AudienceBulkOperationService(
            jdbc, new ObjectMapper().findAndRegisterModules(),
            new AudienceSearchService(jdbc), readerService);
        AudienceBulkOperationService.PreviewResult preview = bulk.preview(
            new AudienceBulkOperationService.PreviewRequest(
                "GRANT_CREDITS",
                List.of(withReader.personId(), withoutReader.personId()),
                null,
                100,
                "課程活動贈點",
                null));

        assertEquals(2, preview.targeted());
        assertEquals(1, preview.eligible());
        assertEquals(1, preview.skipped());
        AudienceBulkOperationService.ExecuteRequest execute =
            new AudienceBulkOperationService.ExecuteRequest(preview.selectionToken(), "bulk-test-1");
        AudienceBulkOperationService.OperationResult first = bulk.execute(execute);
        AudienceBulkOperationService.OperationResult repeated = bulk.execute(execute);

        assertEquals(first.operationId(), repeated.operationId());
        assertEquals(1, first.succeeded());
        verify(readerService, times(1))
            .grantCreditsForAudience("bulk-reader@example.com", 100, "課程活動贈點");
    }

    /** 個資匯出涵蓋活動與來源；退訂後刪除只保留不可逆抑制 hash。 */
    @Test
    void dataLifecycleExportsDeletesAndPreservesUnsubscribeSuppression() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AudiencePlatformService.PersonResult person =
            audience.mergePerson("privacy@example.com", "Privacy User", now);
        audience.upsertIdentity(
            person.personId(), "exam", "student_profile", "privacy-profile", now);
        audience.appendConsent(
            person.personId(), AudiencePlatformService.CONSENT_UNSUBSCRIBED,
            "unsubscribe-link", null, Map.of(), now);
        AudiencePlatformService.RecordResult record = audience.upsertRecord(
            person.personId(), "exam", "exam_attempt", "exam@privacy", "privacy-attempt",
            now, Map.of("totalScore", 9), Map.of());
        audience.replaceFacts(
            person.personId(), record.recordId(), "exam", now, Map.of("exam.score", 9));
        jdbc.update("""
            INSERT INTO reader (email, tier, credits, referral_code)
            VALUES ('privacy@example.com', 'FREE', 20, 'PRIVACY-USER')
            """);
        AudienceDataLifecycleService lifecycle =
            new AudienceDataLifecycleService(jdbc, 0);

        Map<String, Object> exported = lifecycle.export(" PRIVACY@example.com ");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records =
            (List<Map<String, Object>>) exported.get("records");
        Map<String, Object> deleted =
            lifecycle.delete("privacy@example.com", "使用者要求刪除");

        assertEquals(1, records.size());
        assertEquals(true, deleted.get("suppressionPreserved"));
        assertEquals(0, jdbc.queryForObject("""
            SELECT count(*) FROM audience_person
             WHERE email_normalized = 'privacy@example.com'
            """, Integer.class));
        assertTrue(audience.isSuppressed("privacy@example.com"));
        assertThrows(
            AudiencePlatformService.SuppressedEmailException.class,
            () -> audience.mergePerson("privacy@example.com", "Reimport", now.plusDays(1)));
    }

    /** 保留期限只清除 raw_data，摘要與 Fact 仍可供統計使用。 */
    @Test
    void retentionRedactsRawDataButKeepsFacts() {
        OffsetDateTime old = OffsetDateTime.now(ZoneOffset.UTC).minusDays(3);
        long personId = audience.mergePerson(
            "retention@example.com", "Retention User", old).personId();
        AudiencePlatformService.RecordResult record = audience.upsertRecord(
            personId, "exam", "exam_attempt", "exam@retention", "retention-attempt",
            old, Map.of("answers", List.of("sensitive")), Map.of("totalScore", 5));
        audience.replaceFacts(
            personId, record.recordId(), "exam", old, Map.of("exam.score", 5));

        new AudienceDataLifecycleService(jdbc, 1).redactExpiredRawData();

        assertEquals("false", jdbc.queryForObject("""
            SELECT raw_data ->> 'retained' FROM audience_record WHERE id = ?
            """, String.class, record.recordId()));
        assertEquals("5", jdbc.queryForObject("""
            SELECT value_number::text FROM audience_fact
             WHERE record_id = ? AND fact_key = 'exam.score'
            """, String.class, record.recordId()));
    }

    /** Exam 預覽不寫入；確認同步後保存人物、PENDING consent、作答與 cursor。 */
    @Test
    void examPreviewAndSyncUseReadonlyContractAndIdempotentRecords() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String payload = """
            {
              "nextCursor":"2026-07-27T12:00|700",
              "profiles":[{
                "externalProfileId":"profile-700",
                "email":"exam-sync@example.com",
                "name":"Exam Sync",
                "createdAt":"2026-07-27T12:00:00Z",
                "acquisitionSource":"exam",
                "firstConsentAt":null,
                "consentVersion":null
              }],
              "attempts":[{
                "externalAttemptId":"attempt-700",
                "externalProfileId":"profile-700",
                "externalExamId":"exam-70",
                "examTitle":"整合測驗",
                "joinedAt":"2026-07-27T12:00:00Z",
                "totalScore":8,
                "questionCount":10,
                "answeredCount":10,
                "correctCount":8,
                "scoreRate":0.8,
                "surveyData":{"role":"後端工程師"},
                "answers":[]
              }]
            }
            """;
        ExamAudienceSyncService sync = new ExamAudienceSyncService(
            builder, audience, jdbc, "https://exam.internal", "exam-token", false);
        server.expect(requestTo(containsString("/api/integrations/audience-export")))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer exam-token"))
            .andRespond(withSuccess(payload, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/api/integrations/audience-export")))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer exam-token"))
            .andRespond(withSuccess(payload, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/api/integrations/audience-export")))
            .andRespond(withSuccess(payload, MediaType.APPLICATION_JSON));

        ExamAudienceSyncService.Preview preview = sync.preview();

        assertEquals(1, preview.peopleCreated());
        assertEquals(1, preview.attempts());
        assertEquals(0, jdbc.queryForObject("""
            SELECT count(*) FROM audience_person
             WHERE email_normalized = 'exam-sync@example.com'
            """, Integer.class));

        ExamAudienceSyncService.SyncResult first = sync.sync();

        assertEquals(1, first.summary().peopleCreated());
        assertEquals(1, first.summary().recordsCreated());
        assertEquals("PENDING", jdbc.queryForObject("""
            SELECT c.status
              FROM audience_consent c
              JOIN audience_person p ON p.id = c.person_id
             WHERE p.email_normalized = 'exam-sync@example.com'
             ORDER BY c.occurred_at DESC, c.id DESC LIMIT 1
            """, String.class));
        assertEquals("2026-07-27T12:00|700", jdbc.queryForObject("""
            SELECT cursor_value FROM integration_sync_cursor WHERE source_key = 'exam'
            """, String.class));

        // 模擬管理員重設 cursor 後重跑相同上游資料，活動仍只更新／不新增。
        jdbc.update("""
            UPDATE integration_sync_cursor SET cursor_value = '' WHERE source_key = 'exam'
            """);
        ExamAudienceSyncService.SyncResult repeated = sync.sync();

        assertEquals(1, repeated.summary().unchanged());
        assertEquals(1, jdbc.queryForObject("""
            SELECT count(*) FROM audience_record
             WHERE source_key = 'exam' AND external_record_id = 'attempt-700'
            """, Integer.class));
        server.verify();
    }

    /** 真實資料庫不可用時明確失敗，不讓核心 migration 測試被靜默略過。 */
    private static void requireDatabase() {
        try (Connection ignored = DriverManager.getConnection(ADMIN_URL, USER, PASS)) {
            // 能連線即可。
        } catch (SQLException exception) {
            fail("請先啟動 survey-test-db（port 5433）：" + exception.getMessage());
        }
    }
}
