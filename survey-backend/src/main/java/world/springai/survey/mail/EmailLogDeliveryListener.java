package world.springai.survey.mail;

import io.github.kevintsai1202.sesmail.DeliveryListener;
import org.springframework.stereotype.Component;

/**
 * SES 待寄佇列派送失敗時，把對應的 email_log 改成 failed。
 *
 * <p>批次群發的 email_log 在「入列」當下就記成 sent，實際送出在之後；送不出去時靠這裡讓記錄反映事實，
 * 邀請信等「看 sent 判斷寄過沒」的流程下次才會重寄。成功不需處理。
 * 沒啟用 SES 時 starter 不會呼叫它，這個 bean 不起作用。</p>
 */
@Component
public class EmailLogDeliveryListener implements DeliveryListener {

    private final EmailLogRepository emailLogs;

    /** 注入寄送記錄存取層 */
    public EmailLogDeliveryListener(EmailLogRepository emailLogs) {
        this.emailLogs = emailLogs;
    }

    /** 以參照（ses-batch-* 或 ses-sched-*）與收件人回寫失敗 */
    @Override
    public void onFailed(String ref, String recipient, String error) {
        emailLogs.markDeliveryFailed(ref, recipient, error);
    }
}
