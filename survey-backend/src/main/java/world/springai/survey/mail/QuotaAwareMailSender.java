package world.springai.survey.mail;

import java.time.Instant;
import java.util.List;

/**
 * 「寄了信就讓額度快取失效」的裝飾器。
 *
 * <p><b>為什麼要有這一層</b>：{@link MailQuotaService} 的額度是每 60 秒才向外部
 * 重新查一次的快照，寄出後不做本地扣減。所以每一條「實際寄出信件」的路徑都必須在
 * 寄完後呼叫 {@link MailQuotaService#invalidate()}，否則 60 秒內的下一次額度檢查
 * 拿到的是寄出<b>之前</b>的數字，會照舊放行——群發把登入信的保留額度吃光，
 * 讀者收不到 magic link（spec §6）。</p>
 *
 * <p>原本這件事靠「每個呼叫端記得寫一行」，而 {@code CampaignService} 記得了、
 * {@code InviteService} 的邀請信與補送提醒（單批可達 500 封）沒有記得——同一個洞
 * 補在一條路徑上，另一條原封不動。把失效搬進寄信本身的成功回傳路徑後，
 * 這個保證就不再依賴任何呼叫端的記性：日後新增第三條寄信路徑也自動受保護。</p>
 *
 * <p><b>為什麼放在 {@code mail} 而不是各業務層</b>：{@code mail} 是下層基礎設施，
 * {@link MailSender} 與 {@link MailQuotaService} 都在其中，本類不 import 任何上層
 * package（{@code reader} / {@code newsletter} / {@code form}），符合
 * {@code PackageDependencyTest} 的分層規則；{@code MailQuotaService} 也不依賴
 * {@code MailSender}，因此不會構成循環依賴。</p>
 *
 * <p><b>{@link #cancelScheduled} 刻意不失效</b>：取消排程只會<b>釋放</b>額度，
 * 沿用舊快照只會讓可用量被低估，方向是保守的；而失效會多打一次外部 API。
 * 只有「消耗額度」的方向需要即時修正。</p>
 */
public class QuotaAwareMailSender implements MailSender {

    /** 真正負責寄信的實作（ZSend 或 Noop） */
    private final MailSender delegate;
    /** 額度快取的擁有者，寄出成功後讓它失效 */
    private final MailQuotaService quotaService;

    /** 注入被裝飾的寄信實作與額度服務 */
    public QuotaAwareMailSender(MailSender delegate, MailQuotaService quotaService) {
        this.delegate = delegate;
        this.quotaService = quotaService;
    }

    /**
     * 寄單封信；只有 delegate 正常回傳（＝確實送出）時才讓額度快取失效。
     * 拋例外代表沒寄成功，額度沒被消耗，不需要重新查詢。
     */
    @Override
    public String send(String to, String subject, String html) {
        String id = delegate.send(to, subject, html);
        quotaService.invalidate();
        return id;
    }

    /** 批量寄送；成功回傳後讓額度快取失效 */
    @Override
    public String sendBatch(List<Email> emails) {
        String jobId = delegate.sendBatch(emails);
        quotaService.invalidate();
        return jobId;
    }

    /**
     * 排程單封；成功回傳後讓額度快取失效。
     * 排程信在 provider 端已佔用額度，不能等到實際寄出才算。
     */
    @Override
    public String schedule(Email email, Instant scheduledAt) {
        String id = delegate.schedule(email, scheduledAt);
        quotaService.invalidate();
        return id;
    }

    /** 取消排程：只會釋放額度，沿用舊快照是保守的，故不觸發失效（理由見類別註解） */
    @Override
    public boolean cancelScheduled(String providerId) {
        return delegate.cancelScheduled(providerId);
    }
}
