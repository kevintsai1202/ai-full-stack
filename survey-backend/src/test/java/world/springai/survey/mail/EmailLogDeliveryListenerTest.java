package world.springai.survey.mail;

import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** EmailLogDeliveryListener：佇列派送失敗時把對應的 email_log 改成 failed */
class EmailLogDeliveryListenerTest {

    /** 失敗：以參照與收件人回寫 */
    @Test
    void failureMarksEmailLog() {
        EmailLogRepository repository = mock(EmailLogRepository.class);

        new EmailLogDeliveryListener(repository).onFailed("ses-batch-1", "reader@example.com", "rejected");

        verify(repository).markDeliveryFailed("ses-batch-1", "reader@example.com", "rejected");
    }

    /** 成功：email_log 入列時已記為 sent／scheduled，不需要再改 */
    @Test
    void successDoesNothing() {
        EmailLogRepository repository = mock(EmailLogRepository.class);

        new EmailLogDeliveryListener(repository).onSent("ses-batch-1", "reader@example.com", "m-1");

        verify(repository, never()).markDeliveryFailed(anyString(), anyString(), anyString());
    }
}
