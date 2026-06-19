package world.springai.survey;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
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
}
