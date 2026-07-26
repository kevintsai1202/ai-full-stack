package world.springai.survey.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QuotaAwareMailSender：把「寄了信就讓額度快取失效」綁在寄信本身，
 * 而不是綁在每個呼叫端的記性上。
 */
class QuotaAwareMailSenderTest {

    private MailSender delegate;
    private MailQuotaService quotaService;
    private QuotaAwareMailSender sender;

    @BeforeEach
    void setUp() {
        delegate = mock(MailSender.class);
        quotaService = mock(MailQuotaService.class);
        sender = new QuotaAwareMailSender(delegate, quotaService);
    }

    /** 單封寄送成功後必須讓額度快取失效，並原樣回傳 provider id */
    @Test
    void singleSendInvalidatesQuotaCache() {
        when(delegate.send("a@example.com", "s", "<p>h</p>")).thenReturn("msg-1");

        assertEquals("msg-1", sender.send("a@example.com", "s", "<p>h</p>"));

        verify(delegate).send("a@example.com", "s", "<p>h</p>");
        verify(quotaService).invalidate();
    }

    /** 批量寄送成功後必須讓額度快取失效 */
    @Test
    void batchSendInvalidatesQuotaCache() {
        List<MailSender.Email> emails = List.of(new MailSender.Email("a@example.com", "s", "h"));
        when(delegate.sendBatch(emails)).thenReturn("job-1");

        assertEquals("job-1", sender.sendBatch(emails));

        verify(quotaService).invalidate();
    }

    /** 排程也會在 provider 端佔用額度，成功後同樣必須失效 */
    @Test
    void scheduleInvalidatesQuotaCache() {
        MailSender.Email email = new MailSender.Email("a@example.com", "s", "h");
        Instant at = Instant.parse("2026-08-01T00:00:00Z");
        when(delegate.schedule(email, at)).thenReturn("sched-1");

        assertEquals("sched-1", sender.schedule(email, at));

        verify(quotaService).invalidate();
    }

    /**
     * 寄送失敗（delegate 拋例外）時不得讓快取失效。
     *
     * <p>沒寄出就沒消耗額度，硬要失效只會讓下一次額度檢查多打一次外部 API；
     * 更重要的是這條斷言鎖住「失效發生在成功回傳路徑上」，
     * 而不是被寫成 finally 之類的無條件執行。</p>
     */
    @Test
    void failedSendDoesNotInvalidateQuotaCache() {
        when(delegate.send(anyString(), anyString(), anyString()))
            .thenThrow(new IllegalStateException("provider 掛了"));

        assertThrows(IllegalStateException.class, () -> sender.send("a@example.com", "s", "h"));

        verify(quotaService, never()).invalidate();
    }

    /** 取消排程只會釋放額度，沿用舊快照是保守的，不觸發失效 */
    @Test
    void cancelDoesNotInvalidateQuotaCache() {
        when(delegate.cancelScheduled("id-1")).thenReturn(true);

        assertTrue(sender.cancelScheduled("id-1"));

        verify(quotaService, never()).invalidate();
    }

    /**
     * Spring 實際注入的 MailSender bean 必須是被包過的版本。
     *
     * <p>少了這條，上面所有斷言都只證明「這個裝飾器本身正確」，
     * 卻不保證它真的在正式流程的路徑上——那正是上一輪出問題的模式：
     * 保護寫好了，但某條路徑沒接上。有無 ZSend 金鑰的兩種組態都要驗，
     * 本機/測試環境（Noop）與線上（ZSend）的行為必須一致。</p>
     */
    @Test
    void springBeanIsAlwaysWrapped() {
        MailConfig config = new MailConfig();
        MailQuotaService quota = mock(MailQuotaService.class);

        MailSender noopBean = config.mailSender(
            RestClient.builder(), quota, "", "from@example.com", "");
        MailSender zsendBean = config.mailSender(
            RestClient.builder(), quota, "zs-key", "from@example.com", "");

        assertTrue(noopBean instanceof QuotaAwareMailSender, "未設金鑰時的 bean 也必須被包過");
        assertTrue(zsendBean instanceof QuotaAwareMailSender, "設了金鑰時的 bean 必須被包過");
    }
}
