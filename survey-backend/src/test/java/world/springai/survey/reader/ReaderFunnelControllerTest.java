package world.springai.survey.reader;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Reader 第一方匿名漏斗端點測試。 */
class ReaderFunnelControllerTest {

    /** 合法事件只保存匿名識別碼與文章脈絡，並回 204。 */
    @Test
    void validArticleViewIsStoredWithoutPersonalData() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReaderFunnelController controller = new ReaderFunnelController(jdbc);

        var response = controller.record(new ReaderFunnelController.EventRequest(
            "ARTICLE_VIEW", "12345678-1234-1234-1234-123456789012",
            "/r/news/ai-agent-guide?utm_source=email", "ai-agent-guide"));

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(jdbc).update(anyString(),
            eq("12345678-1234-1234-1234-123456789012"), eq("ARTICLE_VIEW"),
            eq("/r/news/ai-agent-guide"), eq("ai-agent-guide"));
    }

    /** 未定義事件或含 reader 站外路徑時拒絕，不能污染分析表。 */
    @Test
    void invalidEventIsRejectedBeforeInsert() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReaderFunnelController controller = new ReaderFunnelController(jdbc);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> controller.record(new ReaderFunnelController.EventRequest(
                "EMAIL_CAPTURED", "12345678-1234-1234-1234-123456789012",
                "/admin", null)));

        assertThat(error.getStatusCode().value()).isEqualTo(400);
        verify(jdbc, never()).update(anyString(),
            org.mockito.ArgumentMatchers.<Object[]>any());
    }
}
