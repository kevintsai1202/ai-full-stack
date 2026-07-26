package world.springai.survey.mail;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.times;
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

    /**
     * fallback 路徑也要扣除保留額度（不能只在成功偵測時才保留）。
     *
     * <p><b>數字刻意選成 60／30</b>：fallback 路徑上有兩個機制會壓低行銷可用量
     * ——「減去 reserve」與 {@link MailQuotaService#FALLBACK_MARKETING_CAP}（50）。
     * 若用 100／50，兩者都會算出 50，任一被改壞這條測試都照樣綠燈，
     * 等於兩個保護互相遮蔽。60−30=30 小於 cap，於是這條測試只會因為
     * 「減去 reserve」被改壞而變紅；cap 的收斂另由
     * {@link #fallbackMarketingRemainingIsCappedConservatively} 覆蓋。</p>
     */
    @Test
    void fallbackQuotaAlsoReservesTransactional() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MailQuotaService service = new MailQuotaService(builder, "", 60, 30);

        MailQuotaService.Quota q = service.current();

        assertEquals("fallback", q.source());
        assertEquals(60, q.remaining(), "顯示用的剩餘量仍照設定值");
        assertEquals(30, q.marketingRemaining(), "行銷可用量必須是 60−30，而非被 cap 夾成 50");
        server.verify();
    }

    /**
     * 保留額度設為 0 時，行銷可用量等於剩餘額度（等同關閉此機制）。
     *
     * <p>刻意走「偵測成功」的路徑：fallback 路徑另有
     * {@link MailQuotaService#FALLBACK_MARKETING_CAP} 的獨立收斂（推測值不授權大量群發），
     * 在那條路徑上驗證「reserve=0 等同不限制」會把兩個不同的保護混為一談。</p>
     */
    @Test
    void zeroReserveMeansNoRestriction() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MailQuotaService service = new MailQuotaService(builder, "sk-test", 100, 0);

        server.expect(requestTo("https://api.zeabur.com/graphql"))
              .andRespond(withSuccess(PRO_RESPONSE, APPLICATION_JSON));

        MailQuotaService.Quota q = service.current();

        assertEquals(q.remaining(), q.marketingRemaining());
        server.verify();
    }

    /**
     * 保留額度為負值時一律夾到 0，不得反向放大行銷可用量。
     *
     * <p>{@code MAIL_TRANSACTIONAL_RESERVE=-500}（打錯正負號，或誤以為負數代表
     * 「不保留」）若原樣採用，{@code remaining - reserve} 會算成 1000 -(-500) = 1500，
     * 保留機制反過來授權群發超額寄出 500 封——比完全沒有保留機制更糟。</p>
     *
     * <p><b>刻意走「偵測成功」路徑</b>：fallback 路徑上有
     * {@link MailQuotaService#FALLBACK_MARKETING_CAP} 兜底，就算放大也會立刻被夾成 50，
     * 「不得反向放大」這件事在那條路徑上根本驗不到——測試會因為另一個保護而綠燈，
     * 正是它自己要防的那種互相遮蔽。偵測成功路徑沒有 cap，放大會原樣顯現：
     * remaining=48800、reserve=−500 時，若少了建構子的 {@code Math.max(0, …)}，
     * marketingRemaining 會是 49300，下面的等值斷言立刻變紅。</p>
     */
    @Test
    void negativeReserveIsClampedToZero() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        // 偵測成功、月剩餘 48800，保留 -500：若不夾到 0，行銷可用量會變成 49300
        MailQuotaService service = new MailQuotaService(builder, "sk-test", 100, -500);

        server.expect(requestTo("https://api.zeabur.com/graphql"))
              .andRespond(withSuccess(PRO_RESPONSE, APPLICATION_JSON));

        MailQuotaService.Quota q = service.current();

        assertEquals(0, q.reserve(), "負的保留額度必須被視為 0");
        assertEquals(48800, q.remaining());
        assertEquals(q.remaining(), q.marketingRemaining(),
            "負的保留額度不得反向放大行銷可用量：" + q.marketingRemaining() + " vs " + q.remaining());
        server.verify();
    }

    /**
     * 偵測失敗時的行銷可用量必須收斂到保守上限，不隨 {@code MAIL_FALLBACK_QUOTA} 放大。
     *
     * <p>fallback 路徑上的數字是猜的。若有人把環境變數調成 5000「以免偵測壞掉時卡住營運」，
     * 偵測一失敗就等於放行 4950 封毫無保護的群發。</p>
     */
    @Test
    void fallbackMarketingRemainingIsCappedConservatively() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MailQuotaService service = new MailQuotaService(builder, "", 5000, 50);

        MailQuotaService.Quota q = service.current();

        assertEquals("fallback", q.source());
        assertEquals(5000, q.remaining(), "顯示用的剩餘量仍照設定值");
        assertEquals(MailQuotaService.FALLBACK_MARKETING_CAP, q.marketingRemaining(),
            "推測值不得授權大量群發");
        assertEquals(MailQuotaService.FALLBACK_MARKETING_CAP, q.marketingBatchMax());
        server.verify();
    }

    /**
     * {@code invalidate()} 之後必須重新向外部查詢，不得回快取。
     *
     * <p>本測試同時鎖住兩件事：① 未失效時 60 秒內只查一次（第二次 current 若打外部
     * API，MockRestServiceServer 會因超出預期次數而失敗）；② invalidate 之後必須
     * 再查一次（少於兩次時 {@code server.verify()} 會因「還有預期請求未發生」而失敗）。
     * 群發寄出後若不讓快取失效，60 秒內的第二批群發會拿到同一份舊快照而被放行。</p>
     */
    @Test
    void invalidateForcesRefetchInsteadOfServingCache() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MailQuotaService service = new MailQuotaService(builder, "sk-test", 100, 50);

        server.expect(times(2), requestTo("https://api.zeabur.com/graphql"))
              .andRespond(withSuccess(PRO_RESPONSE, APPLICATION_JSON));

        service.current();
        service.current();   // 快取命中：不得再打外部 API
        service.invalidate();
        service.current();   // 快取已失效：必須重新查詢

        server.verify();     // 恰好兩次請求
    }
}
