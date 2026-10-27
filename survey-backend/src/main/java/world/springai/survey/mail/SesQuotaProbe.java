package world.springai.survey.mail;

import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.GetAccountRequest;
import software.amazon.awssdk.services.sesv2.model.GetAccountResponse;
import software.amazon.awssdk.services.sesv2.model.SendQuota;

import java.util.Locale;

/**
 * 查 SES 帳號的寄送額度，並附上本機佇列裡還沒送出的封數。
 *
 * <p>SES 的 {@code SentLast24Hours} 只算已經交給它的信；佇列裡排著的信 SES 看不到。
 * 不把待寄數一起扣掉的話，群發剛入列、額度快取一失效，查回來的數字還是群發前的，
 * 保留給登入信的額度就會被第二批群發吃掉（見 {@link MailQuotaService} 的 reserve 說明）。</p>
 */
public class SesQuotaProbe {

    /** max24HourSend 為 -1（不限）時採用的數字：夠大、又不會在相減時溢位 */
    static final long UNLIMITED = 1_000_000_000L;

    /**
     * 額度快照。
     *
     * @param status  帳號狀態：sandbox（尚未離開沙箱）／paused（寄送被暫停）／其餘為 enforcementStatus 小寫
     * @param max24h  24 小時內可寄上限
     * @param sent24h 過去 24 小時已寄
     * @param pending 本機佇列中尚未送出的封數
     */
    public record Snapshot(String status, long max24h, long sent24h, long pending) {}

    private final SesV2Client client;
    private final ScheduledMailRepository queue;

    /** 注入 SES 客戶端與待寄佇列 */
    public SesQuotaProbe(SesV2Client client, ScheduledMailRepository queue) {
        this.client = client;
        this.queue = queue;
    }

    /** 呼叫 GetAccount 並組成快照；失敗直接拋出，由 {@link MailQuotaService} 退回 fallback */
    public Snapshot fetch() {
        GetAccountResponse account = client.getAccount(GetAccountRequest.builder().build());
        SendQuota quota = account.sendQuota();
        long max = quota == null || quota.max24HourSend() == null ? 0 : quota.max24HourSend().longValue();
        long sent = quota == null || quota.sentLast24Hours() == null ? 0 : quota.sentLast24Hours().longValue();
        long pending = queue.countByStatus(ScheduledMail.STATUS_PENDING);
        return new Snapshot(status(account), max < 0 ? UNLIMITED : max, sent, pending);
    }

    /** 沙箱與暫停優先顯示，其餘用 AWS 的 enforcementStatus（HEALTHY／PROBATION／SHUTDOWN） */
    private static String status(GetAccountResponse account) {
        if (!Boolean.TRUE.equals(account.productionAccessEnabled())) {
            return "sandbox";
        }
        if (!Boolean.TRUE.equals(account.sendingEnabled())) {
            return "paused";
        }
        String enforcement = account.enforcementStatus();
        return enforcement == null ? "unknown" : enforcement.toLowerCase(Locale.ROOT);
    }
}
