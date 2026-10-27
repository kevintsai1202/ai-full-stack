package world.springai.survey.audience;

import io.github.kevintsai1202.sesmail.events.SesBounce;
import io.github.kevintsai1202.sesmail.events.SesComplaint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SesSuppressionListener：永久退信與檢舉 → 退訂並留下同意事件；暫時退信不處理 */
class SesSuppressionListenerTest {

    private SurveyResponseRepository repository;
    private AudiencePlatformService audience;
    private SesSuppressionListener listener;

    @BeforeEach
    void setUp() {
        repository = mock(SurveyResponseRepository.class);
        audience = mock(AudiencePlatformService.class);
        listener = new SesSuppressionListener(repository, audience);
    }

    /** 永久退信：email 正規化（去空白、轉小寫）後退訂，並寫入 ses-bounce 退訂事件 */
    @Test
    @SuppressWarnings("unchecked")
    void permanentBounceUnsubscribes() {
        when(repository.unsubscribeByEmail("gone@example.com")).thenReturn(1);

        listener.onBounce(new SesBounce("Permanent", "NoEmail", List.of(" Gone@Example.COM "), "m-1"));

        verify(repository).unsubscribeByEmail("gone@example.com");
        ArgumentCaptor<Map<String, Object>> evidence = ArgumentCaptor.forClass(Map.class);
        verify(audience).appendConsentByEmail(eq("gone@example.com"),
            eq(AudiencePlatformService.CONSENT_UNSUBSCRIBED), eq("ses-bounce"), evidence.capture(),
            any(OffsetDateTime.class));
        assertEquals("NoEmail", evidence.getValue().get("bounceSubType"));
        assertEquals("m-1", evidence.getValue().get("messageId"));
    }

    /** 暫時退信（信箱滿等）：不退訂 */
    @Test
    void transientBounceIsIgnored() {
        listener.onBounce(new SesBounce("Transient", "MailboxFull", List.of("full@example.com"), "m-2"));

        verify(repository, never()).unsubscribeByEmail(anyString());
    }

    /** 檢舉：立即退訂；feedbackType 為 null 也不出錯 */
    @Test
    void complaintUnsubscribes() {
        when(repository.unsubscribeByEmail("angry@example.com")).thenReturn(1);

        listener.onComplaint(new SesComplaint(List.of("angry@example.com"), null, "m-3"));

        verify(repository).unsubscribeByEmail("angry@example.com");
        verify(audience).appendConsentByEmail(eq("angry@example.com"),
            eq(AudiencePlatformService.CONSENT_UNSUBSCRIBED), eq("ses-complaint"), any(), any(OffsetDateTime.class));
    }

    /** 已經退訂過（重送的通知）：不重複寫同意事件，保持冪等 */
    @Test
    void alreadyUnsubscribedWritesNoEvent() {
        when(repository.unsubscribeByEmail("gone@example.com")).thenReturn(0);

        listener.onBounce(new SesBounce("Permanent", "General", List.of("gone@example.com"), "m-4"));

        verify(audience, never()).appendConsentByEmail(anyString(), anyString(), anyString(), any(), any());
    }
}
