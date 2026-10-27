package world.springai.survey.mail;

import io.github.kevintsai1202.sesmail.OutgoingMail;
import io.github.kevintsai1202.sesmail.SesMailClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SesMailSender：把專案的 MailSender 介面轉給 ses-mail-spring-boot-starter 的 SesMailClient */
class SesMailSenderTest {

    private SesMailClient client;
    private SesMailSender sender;

    @BeforeEach
    void setUp() {
        client = mock(SesMailClient.class);
        sender = new SesMailSender(client);
    }

    /** 交易信：同步寄出，回傳 SES MessageId */
    @Test
    void sendDelegatesToClient() {
        when(client.send("reader@example.com", "主旨", "<p>h</p>")).thenReturn("ses-msg-1");

        assertEquals("ses-msg-1", sender.send("reader@example.com", "主旨", "<p>h</p>"));
    }

    /** 批次群發：逐封轉成 OutgoingMail、整批入列，回傳 ses-batch-* 參照 */
    @Test
    void sendBatchEnqueuesAll() {
        List<OutgoingMail> expected = List.of(
            new OutgoingMail("a@example.com", "s", "<p>a</p>"),
            new OutgoingMail("b@example.com", "s", "<p>b</p>"));
        when(client.enqueueBatch(expected)).thenReturn("ses-batch-1");

        String ref = sender.sendBatch(List.of(
            new MailSender.Email("a@example.com", "s", "<p>a</p>"),
            new MailSender.Email("b@example.com", "s", "<p>b</p>")));

        assertEquals("ses-batch-1", ref);
        verify(client).enqueueBatch(expected);
    }

    /** 排程：轉給 client.schedule，回傳可取消的參照 */
    @Test
    void scheduleDelegates() {
        Instant at = Instant.parse("2026-10-28T01:00:00Z");
        when(client.schedule(new OutgoingMail("a@example.com", "s", "<p>a</p>"), at)).thenReturn("ses-sched-9");

        assertEquals("ses-sched-9", sender.schedule(new MailSender.Email("a@example.com", "s", "<p>a</p>"), at));
    }

    /** 取消：結果照 client.cancel 回傳（ZSend 舊 id 由 client 回 false） */
    @Test
    void cancelDelegates() {
        when(client.cancel("ses-sched-9")).thenReturn(true);
        when(client.cancel("zsend-abc")).thenReturn(false);

        assertTrue(sender.cancelScheduled("ses-sched-9"));
        assertFalse(sender.cancelScheduled("zsend-abc"));
    }
}
