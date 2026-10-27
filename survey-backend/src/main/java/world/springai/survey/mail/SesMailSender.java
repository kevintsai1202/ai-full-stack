package world.springai.survey.mail;

import io.github.kevintsai1202.sesmail.OutgoingMail;
import io.github.kevintsai1202.sesmail.SesMailClient;

import java.time.Instant;
import java.util.List;

/**
 * 透過 Amazon SES 寄信：把本專案的 {@link MailSender} 介面轉給
 * ses-mail-spring-boot-starter 的 {@link SesMailClient}。
 *
 * <p>語意與 ZSend 一致：{@link #send} 同步寄出；{@link #sendBatch}、{@link #schedule}
 * 寫入 starter 的待寄佇列，由它的派送器依速率送出；{@link #cancelScheduled} 只取消還沒被派送的排程，
 * 切換前留下的 ZSend 排程 id 一律回 false。</p>
 */
public class SesMailSender implements MailSender {

    /** starter 提供的寄信入口 */
    private final SesMailClient client;

    /** 注入 starter 的寄信客戶端 */
    public SesMailSender(SesMailClient client) {
        this.client = client;
    }

    /** 交易信：同步寄出，回傳 SES MessageId */
    @Override
    public String send(String to, String subject, String html) {
        return client.send(to, subject, html);
    }

    /** 批次群發：整批入列（全有或全無），回傳 ses-batch-* 參照 */
    @Override
    public String sendBatch(List<Email> emails) {
        return client.enqueueBatch(emails.stream().map(SesMailSender::toOutgoing).toList());
    }

    /** 排程單封：入列，回傳可取消的 ses-sched-{id} */
    @Override
    public String schedule(Email email, Instant scheduledAt) {
        return client.schedule(toOutgoing(email), scheduledAt);
    }

    /** 取消排程：只有還沒被派送器認領的 ses-sched-* 取消得了 */
    @Override
    public boolean cancelScheduled(String providerId) {
        return client.cancel(providerId);
    }

    /** 本專案的 Email 轉成 starter 的 OutgoingMail */
    private static OutgoingMail toOutgoing(Email email) {
        return new OutgoingMail(email.to(), email.subject(), email.html());
    }
}
