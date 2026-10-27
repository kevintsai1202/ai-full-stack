package world.springai.survey.mail;

import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * 透過 Amazon SES（v2 API）寄信。
 *
 * <p>SES 沒有排程與取消 API，這裡用本機佇列補上，讓 {@link MailSender} 的語意與 ZSend 一致：</p>
 * <ul>
 *   <li>{@link #send}：交易信（登入、確認、歡迎），同步呼叫 SES，讀者正在等。</li>
 *   <li>{@link #schedule}：寫入佇列，回傳 {@code ses-sched-{id}}，到期由 {@link ScheduledMailDispatcher} 送出。</li>
 *   <li>{@link #sendBatch}：同樣寫入佇列、預定時間為現在。310 封逐封同步呼叫 SES 要二、三十秒，
 *       放在後台的 HTTP 請求裡會逾時；ZSend 的批次 API 本來也是「收件後非同步寄出」。</li>
 *   <li>{@link #cancelScheduled}：只認自己發的 {@code ses-sched-*}，且只能取消還沒被派送器認領的信。</li>
 * </ul>
 */
public class SesMailSender implements MailSender {

    /** SES v2 客戶端 */
    private final SesV2Client client;
    /** 本機待寄佇列 */
    private final ScheduledMailRepository queue;
    /** 寄件人（已做 RFC 2047 編碼） */
    private final String from;
    /** 回信地址，可空白 */
    private final String replyTo;
    /** SES configuration set 名稱，可空白（日後接退信事件用） */
    private final String configurationSet;
    /** 時鐘，測試可固定 */
    private final Clock clock;

    /** 注入 SES 客戶端、佇列與寄件設定 */
    public SesMailSender(SesV2Client client, ScheduledMailRepository queue,
                         String from, String replyTo, String configurationSet, Clock clock) {
        this.client = client;
        this.queue = queue;
        this.from = MailAddressEncoder.encode(from);
        this.replyTo = replyTo;
        this.configurationSet = configurationSet;
        this.clock = clock;
    }

    /** 交易信：同步寄出，回傳 SES MessageId */
    @Override
    public String send(String to, String subject, String html) {
        return deliver(to, subject, html);
    }

    /** 批次群發：整批以同一個參照寫入佇列，由派送器依速率送出 */
    @Override
    public String sendBatch(List<Email> emails) {
        String ref = ScheduledMail.BATCH_PREFIX + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        queue.saveAll(emails.stream()
            .map(e -> new ScheduledMail(ref, e.to(), e.subject(), e.html(), now))
            .toList());
        return ref;
    }

    /** 排程單封：寫入佇列，回傳可取消的 ses-sched-{id} */
    @Override
    public String schedule(Email email, Instant scheduledAt) {
        ScheduledMail saved = queue.save(new ScheduledMail(null, email.to(), email.subject(), email.html(),
            OffsetDateTime.ofInstant(scheduledAt, ZoneOffset.UTC)));
        return saved.providerRef();
    }

    /**
     * 取消排程：不是自己發的 id（例如切換前留下的 ZSend 排程）一律回 false；
     * 只靠條件式 UPDATE 判斷是否還沒被派送器認領。
     */
    @Override
    public boolean cancelScheduled(String providerId) {
        if (providerId == null || !providerId.startsWith(ScheduledMail.SCHEDULE_PREFIX)) {
            return false;
        }
        long id;
        try {
            id = Long.parseLong(providerId.substring(ScheduledMail.SCHEDULE_PREFIX.length()));
        } catch (NumberFormatException e) {
            return false;
        }
        return queue.cancel(id) == 1;
    }

    /**
     * 實際呼叫 SES 寄出一封信，回傳 MessageId。供交易信與派送器共用。
     *
     * <p>主旨與內文都明確指定 UTF-8：SES 的 Content 預設是 7-bit ASCII，不指定的話中文會變亂碼。</p>
     */
    public String deliver(String to, String subject, String html) {
        SendEmailRequest.Builder request = SendEmailRequest.builder()
            .fromEmailAddress(from)
            .destination(Destination.builder().toAddresses(to).build())
            .content(EmailContent.builder()
                .simple(Message.builder()
                    .subject(utf8(subject))
                    .body(Body.builder().html(utf8(html)).build())
                    .build())
                .build());
        if (StringUtils.hasText(replyTo)) {
            request.replyToAddresses(replyTo);
        }
        if (StringUtils.hasText(configurationSet)) {
            request.configurationSetName(configurationSet);
        }
        return client.sendEmail(request.build()).messageId();
    }

    /** 包成指定 UTF-8 的 SES Content */
    private static Content utf8(String data) {
        return Content.builder().charset("UTF-8").data(data).build();
    }
}
