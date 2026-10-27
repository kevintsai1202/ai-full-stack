package world.springai.survey.mail;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.GetAccountRequest;
import software.amazon.awssdk.services.sesv2.model.GetAccountResponse;
import software.amazon.awssdk.services.sesv2.model.SendQuota;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** SesQuotaProbe：把 SES GetAccount 與本機佇列待寄數整理成額度快照 */
class SesQuotaProbeTest {

    /** 組一份 GetAccount 回應 */
    private static GetAccountResponse account(boolean production, boolean sendingEnabled,
                                              String enforcement, double max, double sent) {
        return GetAccountResponse.builder()
            .productionAccessEnabled(production)
            .sendingEnabled(sendingEnabled)
            .enforcementStatus(enforcement)
            .sendQuota(SendQuota.builder().max24HourSend(max).sentLast24Hours(sent).maxSendRate(14.0).build())
            .build();
    }

    /** 正式環境：狀態取 enforcementStatus（小寫），待寄數來自佇列 */
    @Test
    void productionAccount() {
        SesV2Client client = mock(SesV2Client.class);
        ScheduledMailRepository queue = mock(ScheduledMailRepository.class);
        when(client.getAccount(any(GetAccountRequest.class)))
            .thenReturn(account(true, true, "HEALTHY", 50000, 1200));
        when(queue.countByStatus(ScheduledMail.STATUS_PENDING)).thenReturn(310L);

        SesQuotaProbe.Snapshot s = new SesQuotaProbe(client, queue).fetch();

        assertEquals(new SesQuotaProbe.Snapshot("healthy", 50000, 1200, 310), s);
    }

    /** 還在沙箱：狀態標 sandbox，提醒只能寄給已驗證信箱 */
    @Test
    void sandboxAccount() {
        SesV2Client client = mock(SesV2Client.class);
        ScheduledMailRepository queue = mock(ScheduledMailRepository.class);
        when(client.getAccount(any(GetAccountRequest.class)))
            .thenReturn(account(false, true, "HEALTHY", 200, 3));

        assertEquals("sandbox", new SesQuotaProbe(client, queue).fetch().status());
    }

    /** 寄送被暫停（例如退信率過高被 AWS 停權）：狀態標 paused */
    @Test
    void pausedAccount() {
        SesV2Client client = mock(SesV2Client.class);
        ScheduledMailRepository queue = mock(ScheduledMailRepository.class);
        when(client.getAccount(any(GetAccountRequest.class)))
            .thenReturn(account(true, false, "SHUTDOWN", 50000, 0));

        assertEquals("paused", new SesQuotaProbe(client, queue).fetch().status());
    }

    /** max24HourSend 為 -1 代表不限：轉成一個夠大的數，避免算出負的剩餘量 */
    @Test
    void unlimitedQuota() {
        SesV2Client client = mock(SesV2Client.class);
        ScheduledMailRepository queue = mock(ScheduledMailRepository.class);
        when(client.getAccount(any(GetAccountRequest.class)))
            .thenReturn(account(true, true, "HEALTHY", -1, 0));

        assertEquals(SesQuotaProbe.UNLIMITED, new SesQuotaProbe(client, queue).fetch().max24h());
    }
}
