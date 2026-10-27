package world.springai.survey.newsletter;

import io.github.kevintsai1202.sesmail.events.SesDelivery;
import org.junit.jupiter.api.Test;
import world.springai.survey.mail.EmailLogRepository;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SesDeliveryListener：收到 SES 送達事件，把寄送記錄與群發收件人標上送達時間 */
class SesDeliveryListenerTest {

    /** 依參照與收件人（正規化後）寫入送達時間；兩張表都要更新 */
    @Test
    void marksBothTables() {
        EmailLogRepository emailLogs = mock(EmailLogRepository.class);
        CampaignRecipientRepository recipients = mock(CampaignRecipientRepository.class);

        new SesDeliveryListener(emailLogs, recipients)
            .onDelivery(new SesDelivery("ses-batch-1", List.of(" Reader@Example.com "), "msg-1"));

        verify(emailLogs).markDelivered(eq("ses-batch-1"), eq("reader@example.com"), any(OffsetDateTime.class));
        verify(recipients).markDelivered(eq("ses-batch-1"), eq("reader@example.com"), any(OffsetDateTime.class));
    }

    /** 資料庫失敗要往外拋：starter 會保留 SQS 訊息稍後重試，不能吞掉 */
    @Test
    void databaseFailurePropagates() {
        EmailLogRepository emailLogs = mock(EmailLogRepository.class);
        when(emailLogs.markDelivered(any(), any(), any())).thenThrow(new IllegalStateException("db down"));

        assertThrows(IllegalStateException.class, () -> new SesDeliveryListener(emailLogs,
            mock(CampaignRecipientRepository.class))
            .onDelivery(new SesDelivery("msg-2", List.of("a@example.com"), "msg-2")));
    }
}
