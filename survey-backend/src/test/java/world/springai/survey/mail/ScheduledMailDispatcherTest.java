package world.springai.survey.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sesv2.model.MessageRejectedException;
import software.amazon.awssdk.services.sesv2.model.TooManyRequestsException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ScheduledMailDispatcher：撈到期待寄信、依速率送出，並依錯誤種類決定重試或失敗 */
class ScheduledMailDispatcherTest {

    /** 固定的「現在」 */
    private static final Instant NOW = Instant.parse("2026-10-27T08:00:00Z");
    private static final OffsetDateTime NOW_ODT = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);

    private ScheduledMailRepository queue;
    private EmailLogRepository emailLogs;
    private SesMailSender sender;
    /** 記錄派送器每次暫停的毫秒數，驗證速率限制 */
    private List<Long> pauses;
    private ScheduledMailDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        queue = mock(ScheduledMailRepository.class);
        emailLogs = mock(EmailLogRepository.class);
        sender = mock(SesMailSender.class);
        pauses = new ArrayList<>();
        dispatcher = new ScheduledMailDispatcher(queue, emailLogs, sender, 5.0,
            Clock.fixed(NOW, ZoneOffset.UTC), pauses::add);
    }

    /** 建一筆已被認領（attempts 已 +1）的待寄信 */
    private ScheduledMail claimedRow(long id, String batchRef, int attempts) {
        ScheduledMail row = new ScheduledMail(batchRef, "reader" + id + "@example.com",
            "主旨", "<p>內文</p>", NOW_ODT);
        row.assignIdForTest(id);
        row.setAttemptsForTest(attempts);
        when(queue.findById(id)).thenReturn(Optional.of(row));
        return row;
    }

    /** 成功：認領 → 寄出 → 記下 SES MessageId */
    @Test
    void sendsDueMailAndMarksSent() {
        when(queue.findDueIds(eq(NOW_ODT), anyInt())).thenReturn(List.of(1L));
        when(queue.claim(1L)).thenReturn(1);
        claimedRow(1L, null, 1);
        when(sender.deliver("reader1@example.com", "主旨", "<p>內文</p>")).thenReturn("ses-msg-1");

        assertEquals(1, dispatcher.dispatchDue());

        verify(queue).markSent(1L, "ses-msg-1");
    }

    /** 認領失敗（剛被取消或已被處理）：不寄、不改狀態 */
    @Test
    void skipsRowThatCannotBeClaimed() {
        when(queue.findDueIds(eq(NOW_ODT), anyInt())).thenReturn(List.of(2L));
        when(queue.claim(2L)).thenReturn(0);

        assertEquals(0, dispatcher.dispatchDue());

        verify(sender, never()).deliver(anyString(), anyString(), anyString());
        verify(queue, never()).markSent(anyLong(), anyString());
    }

    /** SES 節流：退回待寄，第一次重試延後 30 秒，不標失敗 */
    @Test
    void throttlingIsRetriedLater() {
        when(queue.findDueIds(eq(NOW_ODT), anyInt())).thenReturn(List.of(3L));
        when(queue.claim(3L)).thenReturn(1);
        claimedRow(3L, null, 1);
        when(sender.deliver(anyString(), anyString(), anyString()))
            .thenThrow(TooManyRequestsException.builder().message("slow down").build());

        dispatcher.dispatchDue();

        verify(queue).retryLater(eq(3L), anyString(), eq(NOW_ODT.plusSeconds(30)));
        verify(queue, never()).markFailed(anyLong(), anyString());
    }

    /** 網路錯誤也是暫時性的：第二次失敗退避加倍為 60 秒 */
    @Test
    void networkErrorBacksOffExponentially() {
        when(queue.findDueIds(eq(NOW_ODT), anyInt())).thenReturn(List.of(4L));
        when(queue.claim(4L)).thenReturn(1);
        claimedRow(4L, null, 2);
        when(sender.deliver(anyString(), anyString(), anyString()))
            .thenThrow(SdkClientException.create("connection reset"));

        dispatcher.dispatchDue();

        verify(queue).retryLater(eq(4L), anyString(), eq(NOW_ODT.plusSeconds(60)));
    }

    /** 暫時性錯誤但已達重試上限：標為失敗並回寫 email_log */
    @Test
    void transientErrorGivesUpAfterMaxAttempts() {
        when(queue.findDueIds(eq(NOW_ODT), anyInt())).thenReturn(List.of(5L));
        when(queue.claim(5L)).thenReturn(1);
        claimedRow(5L, null, ScheduledMailDispatcher.MAX_ATTEMPTS);
        when(sender.deliver(anyString(), anyString(), anyString()))
            .thenThrow(TooManyRequestsException.builder().message("slow down").build());

        dispatcher.dispatchDue();

        verify(queue).markFailed(eq(5L), anyString());
        verify(queue, never()).retryLater(anyLong(), anyString(), any());
        verify(emailLogs).markDeliveryFailed(eq("ses-sched-5"), eq("reader5@example.com"), anyString());
    }

    /** 被 SES 拒絕（例如收件位址無效）：不重試，直接失敗；批次信用整批參照回寫 email_log */
    @Test
    void rejectedMailFailsImmediately() {
        when(queue.findDueIds(eq(NOW_ODT), anyInt())).thenReturn(List.of(6L));
        when(queue.claim(6L)).thenReturn(1);
        claimedRow(6L, "ses-batch-abc", 1);
        when(sender.deliver(anyString(), anyString(), anyString()))
            .thenThrow(MessageRejectedException.builder().message("Email address is not verified").build());

        dispatcher.dispatchDue();

        verify(queue).markFailed(eq(6L), anyString());
        verify(queue, never()).retryLater(anyLong(), anyString(), any());
        verify(emailLogs).markDeliveryFailed(eq("ses-batch-abc"), eq("reader6@example.com"), anyString());
    }

    /** 速率限制：每秒 5 封 → 每封之間暫停 200 毫秒 */
    @Test
    void pausesBetweenSendsToRespectRate() {
        when(queue.findDueIds(eq(NOW_ODT), anyInt())).thenReturn(List.of(7L, 8L));
        when(queue.claim(anyLong())).thenReturn(1);
        claimedRow(7L, null, 1);
        claimedRow(8L, null, 1);
        when(sender.deliver(anyString(), anyString(), anyString())).thenReturn("m");

        assertEquals(2, dispatcher.dispatchDue());

        assertEquals(List.of(200L, 200L), pauses);
    }

    /** 程序中斷後卡在 SENDING 的列只回報數量，絕不自動重寄（重複寄出比漏寄更糟） */
    @Test
    void stuckRowsAreReportedNotResent() {
        when(queue.countStuck(NOW_ODT.minus(Duration.ofMinutes(10)))).thenReturn(3L);

        assertEquals(3L, dispatcher.reportStuck());

        verify(queue, never()).retryLater(anyLong(), anyString(), any());
        verify(sender, never()).deliver(anyString(), anyString(), anyString());
    }
}
