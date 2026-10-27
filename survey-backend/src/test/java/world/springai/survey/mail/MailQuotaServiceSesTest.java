package world.springai.survey.mail;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** MailQuotaService 的 SES 分支：24 小時上限 − 已寄 − 本機佇列待寄 */
class MailQuotaServiceSesTest {

    /** 建立 provider=ses 的額度服務，探測器由測試提供 */
    private MailQuotaService service(SesQuotaProbe probe, long reserve) {
        return new MailQuotaService(RestClient.builder(), "", 100, reserve, "ses", () -> probe);
    }

    /** 剩餘量必須扣掉佇列裡還沒送出的信，否則保留給交易信的額度會被群發吃掉 */
    @Test
    void remainingSubtractsPendingQueue() {
        SesQuotaProbe probe = mock(SesQuotaProbe.class);
        when(probe.fetch()).thenReturn(new SesQuotaProbe.Snapshot("healthy", 50000, 1000, 310));

        MailQuotaService.Quota q = service(probe, 50).current();

        assertEquals(MailQuotaService.SOURCE_SES, q.source());
        assertEquals("healthy", q.status());
        assertEquals(50000, q.dailyQuota());
        assertEquals(1000, q.dailySent());
        assertEquals(48690, q.dailyRemaining());
        assertEquals(48690, q.remaining());
        assertEquals(48640, q.marketingRemaining());
        assertEquals(MailQuotaService.BATCH_CAP, q.marketingBatchMax());
    }

    /** 沙箱狀態要原樣傳到後台，管理者才知道為什麼信寄不出去 */
    @Test
    void sandboxStatusIsVisible() {
        SesQuotaProbe probe = mock(SesQuotaProbe.class);
        when(probe.fetch()).thenReturn(new SesQuotaProbe.Snapshot("sandbox", 200, 0, 0));

        MailQuotaService.Quota q = service(probe, 50).current();

        assertEquals("sandbox", q.status());
        assertEquals(200, q.remaining());
        assertEquals(150, q.marketingRemaining());
    }

    /** 已寄加待寄超過上限時剩餘量不得為負 */
    @Test
    void remainingNeverNegative() {
        SesQuotaProbe probe = mock(SesQuotaProbe.class);
        when(probe.fetch()).thenReturn(new SesQuotaProbe.Snapshot("healthy", 200, 180, 50));

        MailQuotaService.Quota q = service(probe, 50).current();

        assertEquals(0, q.remaining());
        assertEquals(0, q.marketingRemaining());
    }

    /** 查詢 SES 失敗：退回保守的 fallback，不拋例外 */
    @Test
    void probeFailureFallsBack() {
        SesQuotaProbe probe = mock(SesQuotaProbe.class);
        when(probe.fetch()).thenThrow(new IllegalStateException("network"));

        MailQuotaService.Quota q = service(probe, 50).current();

        assertEquals(MailQuotaService.SOURCE_FALLBACK, q.source());
    }

    /** provider=ses 卻沒有探測器（SES bean 未建立）：同樣走 fallback */
    @Test
    void missingProbeFallsBack() {
        MailQuotaService.Quota q = service(null, 50).current();

        assertEquals(MailQuotaService.SOURCE_FALLBACK, q.source());
    }
}
