package world.springai.survey.newsletter;

import io.github.kevintsai1202.sesmail.events.SesDelivery;
import io.github.kevintsai1202.sesmail.events.SesEventListener;
import org.springframework.stereotype.Component;
import world.springai.survey.mail.EmailLogRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

/**
 * SES 送達事件：把寄送記錄（email_log）與群發收件人（campaign_recipient）標上送達時間。
 *
 * <p>參照由 starter 對好：佇列寄出的信是 ses-batch-*／ses-sched-*，交易信是 SES MessageId，
 * 與兩張表的 provider_message_id 一致。只寫 delivered_at、不動狀態值（見 V28 說明）。
 * 資料庫失敗直接往外拋，讓 starter 保留 SQS 訊息稍後重試。</p>
 *
 * <p>放在 newsletter 而非 mail：同時需要兩個 package 的 repository，mail 不得依賴上層。</p>
 */
@Component
public class SesDeliveryListener implements SesEventListener {

    private final EmailLogRepository emailLogs;
    private final CampaignRecipientRepository recipients;

    /** 注入兩張表的存取層 */
    public SesDeliveryListener(EmailLogRepository emailLogs, CampaignRecipientRepository recipients) {
        this.emailLogs = emailLogs;
        this.recipients = recipients;
    }

    /** 逐一收件人寫入送達時間（只記第一次） */
    @Override
    public void onDelivery(SesDelivery delivery) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (String email : delivery.recipients()) {
            String normalized = email.trim().toLowerCase(Locale.ROOT);
            emailLogs.markDelivered(delivery.ref(), normalized, now);
            recipients.markDelivered(delivery.ref(), normalized, now);
        }
    }
}
