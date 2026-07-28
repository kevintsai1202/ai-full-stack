package world.springai.survey.reader;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 公開分享點擊端點不可洩漏推薦碼是否有效。 */
class ReferralTrackingControllerTest {

    @Test
    void invalidAndUnknownCodesReturnSameNoContentResponse() {
        ReaderRepository readers = mock(ReaderRepository.class);
        ReferralClickRepository clicks = mock(ReferralClickRepository.class);
        ReferralTrackingController controller = new ReferralTrackingController(readers, clicks);
        when(readers.findByReferralCode("UNKNOWN")).thenReturn(Optional.empty());

        var invalid = controller.click(new ReferralTrackingController.ClickRequest(
            "", "article", "1234567890123456"));
        var unknown = controller.click(new ReferralTrackingController.ClickRequest(
            "UNKNOWN", "article", "1234567890123456"));

        assertThat(invalid.getStatusCode().value()).isEqualTo(204);
        assertThat(unknown.getStatusCode().value()).isEqualTo(204);
        verify(clicks, never()).saveAndFlush(any());
    }

    @Test
    void validArticleClickIsStoredWithoutPersonalData() {
        ReaderRepository readers = mock(ReaderRepository.class);
        ReferralClickRepository clicks = mock(ReferralClickRepository.class);
        ReferralTrackingController controller = new ReferralTrackingController(readers, clicks);
        Reader referrer = new Reader("owner@example.com", "CODE1234");
        referrer.setId(7L);
        when(readers.findByReferralCode("CODE1234")).thenReturn(Optional.of(referrer));

        var response = controller.click(new ReferralTrackingController.ClickRequest(
            "CODE1234", "ai-agent-guide", "1234567890123456"));

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(clicks).saveAndFlush(any(ReferralClick.class));
    }
}

