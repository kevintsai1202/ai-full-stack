package world.springai.survey;

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
        MailQuotaService service = new MailQuotaService(builder, "sk-test", 100);

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
        MailQuotaService service = new MailQuotaService(builder, "", 100);

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
        MailQuotaService service = new MailQuotaService(builder, "sk-test", 80);

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
        MailQuotaService service = new MailQuotaService(builder, "sk-test", 100);

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
}
