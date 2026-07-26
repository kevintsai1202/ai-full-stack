package world.springai.survey.mail;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** MailQuotaService：動態額度偵測、剩餘量計算與失敗退路 */
class MailQuotaServiceTest {

    /** Zeabur Pro 方案的典型回應：日額度近似無限、月額度 50000 */
    private static final String PRO_RESPONSE = """
        {"data":{"getZSendUserStatus":{"status":"healthy",\
        "dailyQuota":999999999,"dailySent":0,"quotaResetAt":"2026-07-26T00:00:00Z",\
        "monthlyQuota":50000,"monthlySent":1200,"monthlyResetAt":"2026-07-28T16:18:35Z",\
        "quotaType":"both","overageBillingEnabled":true}}}""";

    /** 應 POST GraphQL 查詢、帶帳號 token，並以月剩餘量作為可用額度、單批上限收斂到 BATCH_CAP */
    @Test
    void detectsQuotaFromZeabur() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MailQuotaService service = new MailQuotaService(builder, "sk-test", 100, 0);

        server.expect(requestTo("https://api.zeabur.com/graphql"))
              .andExpect(method(POST))
              .andExpect(header("Authorization", "Bearer sk-test"))
              .andExpect(jsonPath("$.query").exists())
              .andRespond(withSuccess(PRO_RESPONSE, APPLICATION_JSON));

        MailQuotaService.Quota q = service.current();

        assertEquals("zeabur", q.source());
        assertEquals("healthy", q.status());
        assertEquals(50000, q.monthlyQuota());
        assertEquals(48800, q.monthlyRemaining());
        // 日額度近似無限時，可用額度應由月剩餘量決定
        assertEquals(48800, q.remaining());
        // 單批上限不得超過安全閾值，避免逐封同步寄送逾時
        assertEquals(MailQuotaService.BATCH_CAP, q.batchMax());
        assertTrue(q.overageBillingEnabled());
        server.verify();
    }

    /** 未設定 Zeabur token 時不打外部 API，直接回退到設定檔的保守額度 */
    @Test
    void fallsBackWhenTokenMissing() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MailQuotaService service = new MailQuotaService(builder, "", 100, 0);

        MailQuotaService.Quota q = service.current();

        assertEquals("fallback", q.source());
        assertEquals(100, q.remaining());
        assertEquals(100, q.batchMax());
        assertFalse(q.overageBillingEnabled());
        server.verify(); // 沒有任何外部請求
    }

    /** 查詢失敗時不可讓後台整頁壞掉，應退回保守額度 */
    @Test
    void fallsBackWhenApiFails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MailQuotaService service = new MailQuotaService(builder, "sk-test", 80, 0);

        server.expect(requestTo("https://api.zeabur.com/graphql")).andRespond(withServerError());

        MailQuotaService.Quota q = service.current();

        assertEquals("fallback", q.source());
        assertEquals(80, q.remaining());
        server.verify();
    }

    /** 已寄量超過額度（超量計費情境）時剩餘量以 0 為底，不得出現負數 */
    @Test
    void remainingNeverGoesNegative() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MailQuotaService service = new MailQuotaService(builder, "sk-test", 100, 0);

        server.expect(requestTo("https://api.zeabur.com/graphql"))
              .andRespond(withSuccess("""
                  {"data":{"getZSendUserStatus":{"status":"healthy",\
                  "dailyQuota":1000,"dailySent":1200,"monthlyQuota":50000,"monthlySent":60000,\
                  "overageBillingEnabled":true}}}""", APPLICATION_JSON));

        MailQuotaService.Quota q = service.current();

        assertEquals(0, q.dailyRemaining());
        assertEquals(0, q.monthlyRemaining());
        assertEquals(0, q.remaining());
        assertEquals(0, q.batchMax());
        server.verify();
    }

    /** 月額度剩 120 封的回應，用於保留額度的計算測試 */
    private static final String LOW_QUOTA_RESPONSE = """
        {"data":{"getZSendUserStatus":{"status":"healthy",\
        "dailyQuota":999999999,"dailySent":0,"quotaResetAt":"2026-07-26T00:00:00Z",\
        "monthlyQuota":50000,"monthlySent":49880,"monthlyResetAt":"2026-07-28T16:18:35Z",\
        "quotaType":"both","overageBillingEnabled":false}}}""";

    /**
     * 行銷可用量 = 剩餘額度 - 保留額度。
     *
     * <p>保留額度是給登入信、確認信、歡迎信的。若群發把額度用到 0，
     * 讀者就收不到 magic link——那不是「信少寄一封」，而是整個讀者端
     * 登不進去（spec §6）。</p>
     */
    @Test
    void marketingRemainingSubtractsReserve() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MailQuotaService service = new MailQuotaService(builder, "sk-test", 100, 50);

        server.expect(requestTo("https://api.zeabur.com/graphql"))
              .andRespond(withSuccess(LOW_QUOTA_RESPONSE, APPLICATION_JSON));

        MailQuotaService.Quota q = service.current();

        assertEquals(120, q.remaining());
        assertEquals(50, q.reserve());
        assertEquals(70, q.marketingRemaining());
        server.verify();
    }

    /** 剩餘額度低於保留額度時，行銷可用量為 0 而非負數 */
    @Test
    void marketingRemainingNeverGoesNegative() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        // fallback 額度 30、保留 50 → 行銷可用量應為 0，不可是 -20
        MailQuotaService service = new MailQuotaService(builder, "", 30, 50);

        MailQuotaService.Quota q = service.current();

        assertEquals(0, q.marketingRemaining());
        assertEquals(0, q.marketingBatchMax());
    }

    /** 行銷單批上限同時受 BATCH_CAP 與行銷可用量限制 */
    @Test
    void marketingBatchMaxRespectsBothCaps() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MailQuotaService service = new MailQuotaService(builder, "sk-test", 100, 50);

        server.expect(requestTo("https://api.zeabur.com/graphql"))
              .andRespond(withSuccess(PRO_RESPONSE, APPLICATION_JSON));

        MailQuotaService.Quota q = service.current();

        // 剩餘 48800 − 保留 50 = 48750，但單批仍收斂到 BATCH_CAP
        assertEquals(48750, q.marketingRemaining());
        assertEquals(MailQuotaService.BATCH_CAP, q.marketingBatchMax());
        server.verify();
    }

    /** fallback 路徑也要扣除保留額度（不能只在成功偵測時才保留） */
    @Test
    void fallbackQuotaAlsoReservesTransactional() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MailQuotaService service = new MailQuotaService(builder, "", 100, 50);

        MailQuotaService.Quota q = service.current();

        assertEquals("fallback", q.source());
        assertEquals(50, q.marketingRemaining());
        server.verify();
    }

    /** 保留額度設為 0 時，行銷可用量等於剩餘額度（等同關閉此機制） */
    @Test
    void zeroReserveMeansNoRestriction() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MailQuotaService service = new MailQuotaService(builder, "", 100, 0);

        MailQuotaService.Quota q = service.current();

        assertEquals(q.remaining(), q.marketingRemaining());
    }
}
