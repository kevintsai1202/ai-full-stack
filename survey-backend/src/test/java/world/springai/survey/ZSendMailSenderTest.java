package world.springai.survey;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** ZSendMailSender 請求格式測試：驗證 URL、Bearer 標頭與 body 欄位，並回傳 provider id */
class ZSendMailSenderTest {

    /** 應 POST 到 ZSend、帶 Bearer 金鑰與正確 body，並回傳回應中的 id */
    @Test
    void sendsCorrectRequestAndReturnsId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ZSendMailSender sender = new ZSendMailSender(builder, "zs_test", "noreply@springai.world", "");

        server.expect(requestTo("https://api.zeabur.com/api/v1/zsend/emails"))
              .andExpect(method(POST))
              .andExpect(header("Authorization", "Bearer zs_test"))
              .andExpect(jsonPath("$.from").value("noreply@springai.world"))
              .andExpect(jsonPath("$.to[0]").value("user@example.com"))
              .andExpect(jsonPath("$.subject").value("主旨"))
              .andExpect(jsonPath("$.html").value("<p>內容</p>"))
              .andRespond(withSuccess("{\"id\":\"abc123\",\"status\":\"pending\"}", APPLICATION_JSON));

        String id = sender.send("user@example.com", "主旨", "<p>內容</p>");

        assertEquals("abc123", id);
        server.verify();
    }

    /** sendBatch 應 POST 到 /emails/batch，body 用 emails 陣列，回 job_id */
    @Test
    void sendBatchPostsEmailsArrayAndReturnsJobId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ZSendMailSender sender = new ZSendMailSender(builder, "zs_test", "noreply@springai.world", "");

        server.expect(requestTo("https://api.zeabur.com/api/v1/zsend/emails/batch"))
              .andExpect(method(POST))
              .andExpect(header("Authorization", "Bearer zs_test"))
              .andExpect(jsonPath("$.emails[0].from").value("noreply@springai.world"))
              .andExpect(jsonPath("$.emails[0].to[0]").value("a@example.com"))
              .andExpect(jsonPath("$.emails[1].to[0]").value("b@example.com"))
              .andRespond(withSuccess("{\"job_id\":\"job-1\",\"status\":\"pending\",\"total_count\":2}", APPLICATION_JSON));

        String jobId = sender.sendBatch(List.of(
            new MailSender.Email("a@example.com", "主旨", "<p>A</p>"),
            new MailSender.Email("b@example.com", "主旨", "<p>B</p>")));

        assertEquals("job-1", jobId);
        server.verify();
    }

    /** schedule 應 POST 到 /emails/schedule，帶 scheduled_at，回 id */
    @Test
    void schedulePostsScheduledAtAndReturnsId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ZSendMailSender sender = new ZSendMailSender(builder, "zs_test", "noreply@springai.world", "");

        server.expect(requestTo("https://api.zeabur.com/api/v1/zsend/emails/schedule"))
              .andExpect(method(POST))
              .andExpect(jsonPath("$.to[0]").value("a@example.com"))
              .andExpect(jsonPath("$.scheduled_at").value("2030-01-01T00:00:00Z"))
              .andRespond(withSuccess("{\"id\":\"sched-1\",\"status\":\"enqueued\"}", APPLICATION_JSON));

        String id = sender.schedule(
            new MailSender.Email("a@example.com", "主旨", "<p>A</p>"),
            Instant.parse("2030-01-01T00:00:00Z"));

        assertEquals("sched-1", id);
        server.verify();
    }

    /** cancelScheduled 應 DELETE /emails/scheduled/{id}，2xx 回 true */
    @Test
    void cancelScheduledDeletesAndReturnsTrue() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ZSendMailSender sender = new ZSendMailSender(builder, "zs_test", "noreply@springai.world", "");

        server.expect(requestTo("https://api.zeabur.com/api/v1/zsend/emails/scheduled/sched-1"))
              .andExpect(method(DELETE))
              .andExpect(header("Authorization", "Bearer zs_test"))
              .andRespond(withNoContent());

        assertTrue(sender.cancelScheduled("sched-1"));
        server.verify();
    }
}
