package world.springai.survey.mail;

import io.github.kevintsai1202.sesmail.SesQuotaService;
import io.github.kevintsai1202.sesmail.SesQuotaSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** MailQuotaService 的 SES 分支：額度來自 starter 的快照（已扣佇列待寄），再扣交易信保留額度 */
class MailQuotaServiceSesTest {

    /** 建立有 SES 額度服務的 MailQuotaService */
    private MailQuotaService service(SesQuotaService ses, long reserve) {
        return new MailQuotaService(RestClient.builder(), "", 100, reserve, () -> ses);
    }

    /** 剩餘量 = 上限 − 已寄 − 佇列待寄；行銷可用再扣保留額度 */
    @Test
    void remainingSubtractsPendingQueue() {
        SesQuotaService ses = mock(SesQuotaService.class);
        when(ses.snapshot()).thenReturn(new SesQuotaSnapshot("healthy", 50000, 1000, 310, 14));

        MailQuotaService.Quota q = service(ses, 50).current();

        assertEquals(MailQuotaService.SOURCE_SES, q.source());
        assertEquals("healthy", q.status());
        assertEquals(50000, q.dailyQuota());
        assertEquals(1000, q.dailySent());
        assertEquals(48690, q.dailyRemaining());
        assertEquals(48690, q.remaining());
        assertEquals(48640, q.marketingRemaining());
        assertEquals(MailQuotaService.BATCH_CAP, q.marketingBatchMax());
    }

    /** 沙箱狀態要原樣傳到後台 */
    @Test
    void sandboxStatusIsVisible() {
        SesQuotaService ses = mock(SesQuotaService.class);
        when(ses.snapshot()).thenReturn(new SesQuotaSnapshot("sandbox", 200, 0, 0, 1));

        MailQuotaService.Quota q = service(ses, 50).current();

        assertEquals("sandbox", q.status());
        assertEquals(200, q.remaining());
        assertEquals(150, q.marketingRemaining());
    }

    /** 查詢 SES 失敗：退回保守的 fallback，不拋例外 */
    @Test
    void sesFailureFallsBack() {
        SesQuotaService ses = mock(SesQuotaService.class);
        when(ses.snapshot()).thenThrow(new IllegalStateException("network"));

        assertEquals(MailQuotaService.SOURCE_FALLBACK, service(ses, 50).current().source());
    }

    /** 沒啟用 SES（沒有額度服務 bean）且沒設 Zeabur token：走 fallback */
    @Test
    void withoutSesUsesZeaburPath() {
        assertEquals(MailQuotaService.SOURCE_FALLBACK, service(null, 50).current().source());
    }
}
