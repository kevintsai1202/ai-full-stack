package world.springai.survey.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SesMailSender：交易信同步寄出，排程與批次群發寫入本機待寄佇列 */
class SesMailSenderTest {

    /** 固定的「現在」，用來驗證立即群發的預定時間 */
    private static final Instant NOW = Instant.parse("2026-10-27T08:00:00Z");

    private SesV2Client client;
    private ScheduledMailRepository queue;
    private SesMailSender sender;

    @BeforeEach
    void setUp() {
        client = mock(SesV2Client.class);
        queue = mock(ScheduledMailRepository.class);
        sender = new SesMailSender(client, queue,
            "凱文大叔 <noreply@springai.world>", "kevin@springai.world", "newsletter",
            Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** 單封寄送：主旨與內文都指定 UTF-8，寄件人名稱經 RFC 2047 編碼，回傳 SES 的 MessageId */
    @Test
    void sendUsesUtf8AndEncodedSender() {
        when(client.sendEmail(any(SendEmailRequest.class)))
            .thenReturn(SendEmailResponse.builder().messageId("ses-msg-1").build());

        String id = sender.send("reader@example.com", "第 030 期：Vibe Coding 之後", "<p>你好</p>");

        assertEquals("ses-msg-1", id);
        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(client).sendEmail(captor.capture());
        SendEmailRequest request = captor.getValue();
        assertEquals(MailAddressEncoder.encode("凱文大叔 <noreply@springai.world>"),
            request.fromEmailAddress());
        assertEquals(List.of("reader@example.com"), request.destination().toAddresses());
        assertEquals(List.of("kevin@springai.world"), request.replyToAddresses());
        assertEquals("newsletter", request.configurationSetName());
        assertEquals("UTF-8", request.content().simple().subject().charset());
        assertEquals("第 030 期：Vibe Coding 之後", request.content().simple().subject().data());
        assertEquals("UTF-8", request.content().simple().body().html().charset());
        assertEquals("<p>你好</p>", request.content().simple().body().html().data());
    }

    /** 未設定回信地址與 configuration set 時，請求不帶這兩個欄位 */
    @Test
    void sendOmitsOptionalFields() {
        SesMailSender plain = new SesMailSender(client, queue, "noreply@springai.world", "", "",
            Clock.fixed(NOW, ZoneOffset.UTC));
        when(client.sendEmail(any(SendEmailRequest.class)))
            .thenReturn(SendEmailResponse.builder().messageId("ses-msg-2").build());

        plain.send("reader@example.com", "s", "<p>h</p>");

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(client).sendEmail(captor.capture());
        assertTrue(captor.getValue().replyToAddresses().isEmpty());
        assertNull(captor.getValue().configurationSetName());
    }

    /** 排程：只寫入佇列、不呼叫 SES，回傳可用來取消的 ses-sched-{id} */
    @Test
    void scheduleEnqueuesAndReturnsCancellableId() {
        Instant at = Instant.parse("2026-10-28T01:00:00Z");
        when(queue.save(any(ScheduledMail.class))).thenAnswer(invocation -> {
            ScheduledMail row = invocation.getArgument(0);
            row.assignIdForTest(42L);
            return row;
        });

        String id = sender.schedule(new MailSender.Email("reader@example.com", "s", "<p>h</p>"), at);

        assertEquals("ses-sched-42", id);
        ArgumentCaptor<ScheduledMail> captor = ArgumentCaptor.forClass(ScheduledMail.class);
        verify(queue).save(captor.capture());
        ScheduledMail row = captor.getValue();
        assertEquals("reader@example.com", row.getRecipient());
        assertEquals(OffsetDateTime.ofInstant(at, ZoneOffset.UTC), row.getSendAt());
        assertEquals(ScheduledMail.STATUS_PENDING, row.getStatus());
        assertNull(row.getBatchRef());
        verify(client, never()).sendEmail(any(SendEmailRequest.class));
    }

    /** 批次群發：整批以同一個 ses-batch-* 參照寫入佇列、預定時間為現在，不同步呼叫 SES */
    @Test
    @SuppressWarnings("unchecked")
    void sendBatchEnqueuesAllWithSharedRef() {
        String ref = sender.sendBatch(List.of(
            new MailSender.Email("a@example.com", "s", "<p>a</p>"),
            new MailSender.Email("b@example.com", "s", "<p>b</p>")));

        assertTrue(ref.startsWith(ScheduledMail.BATCH_PREFIX), ref);
        ArgumentCaptor<List<ScheduledMail>> captor = ArgumentCaptor.forClass(List.class);
        verify(queue).saveAll(captor.capture());
        List<ScheduledMail> rows = captor.getValue();
        assertEquals(2, rows.size());
        for (ScheduledMail row : rows) {
            assertEquals(ref, row.getBatchRef());
            assertEquals(ref, row.providerRef());
            assertEquals(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), row.getSendAt());
        }
        verify(client, never()).sendEmail(any(SendEmailRequest.class));
    }

    /** 取消：只認自己發的 ses-sched-*；ZSend 舊 id 或格式錯誤一律回 false，不碰資料庫 */
    @Test
    void cancelIgnoresForeignIds() {
        assertFalse(sender.cancelScheduled("zsend-abc"));
        assertFalse(sender.cancelScheduled("ses-sched-notanumber"));
        assertFalse(sender.cancelScheduled(null));
        verify(queue, never()).cancel(anyLong());
    }

    /** 取消自己的排程：只有條件式 UPDATE 改到一列（仍是 PENDING）才算成功 */
    @Test
    void cancelOwnScheduleOnlyWhilePending() {
        when(queue.cancel(42L)).thenReturn(1);
        when(queue.cancel(43L)).thenReturn(0);

        assertTrue(sender.cancelScheduled("ses-sched-42"));
        assertFalse(sender.cancelScheduled("ses-sched-43"));
    }

    /** 取消只靠條件式 UPDATE 判斷，不先讀列（先讀再改會與派送器產生競爭） */
    @Test
    void cancelDoesNotReadRow() {
        when(queue.cancel(7L)).thenReturn(1);
        sender.cancelScheduled("ses-sched-7");
        verify(queue, never()).findById(any());
    }
}
