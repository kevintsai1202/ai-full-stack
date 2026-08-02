package world.springai.survey.audience;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 公開訂閱人數端點測試。 */
class PublicSubscriberControllerTest {

    /** 回應只包含彙總人數，且沿用實際寄送名單服務。 */
    @Test
    void countReturnsRecipientServiceAggregate() {
        RecipientService recipients = mock(RecipientService.class);
        when(recipients.subscriberCount()).thenReturn(321L);

        var response = new PublicSubscriberController(recipients).count();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("count", 321L);
        assertThat(response.getHeaders().getCacheControl()).contains("max-age=60");
        verify(recipients).subscriberCount();
    }
}
