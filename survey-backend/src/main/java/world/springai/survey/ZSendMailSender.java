package world.springai.survey;

import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 透過 Zeabur Email（ZSend）REST API 寄信 */
public class ZSendMailSender implements MailSender {

    /** 預先綁定 ZSend base URL 的 HTTP 客戶端 */
    private final RestClient client;
    private final String apiKey;   // ZSend Bearer 金鑰
    private final String from;     // 寄件人（須為已驗證網域）
    private final String replyTo;  // 回信地址，可空白

    /** 以注入的 RestClient.Builder 建立綁定 ZSend 的客戶端（builder 可由測試綁 MockRestServiceServer） */
    public ZSendMailSender(RestClient.Builder builder, String apiKey, String from, String replyTo) {
        this.client = builder.baseUrl("https://api.zeabur.com").build();
        this.apiKey = apiKey;
        this.from = from;
        this.replyTo = replyTo;
    }

    /** 組 ZSend 請求並送出，回傳回應中的訊息 id */
    @Override
    public String send(String to, String subject, String html) {
        // 用 LinkedHashMap 維持欄位順序，方便除錯
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", from);
        body.put("to", List.of(to));
        body.put("subject", subject);
        body.put("html", html);
        if (StringUtils.hasText(replyTo)) {
            body.put("reply_to", List.of(replyTo));
        }
        Map<?, ?> resp = client.post()
            .uri("/api/v1/zsend/emails")
            .header("Authorization", "Bearer " + apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Map.class);
        return resp == null ? null : String.valueOf(resp.get("id"));
    }

    /** 批量立即寄送：body 為 {emails:[...]}；回傳整批 job_id */
    @Override
    public String sendBatch(List<Email> emails) {
        List<Map<String, Object>> arr = new ArrayList<>();
        for (Email e : emails) {
            arr.add(toPayload(e));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("emails", arr);
        Map<?, ?> resp = client.post()
            .uri("/api/v1/zsend/emails/batch")
            .header("Authorization", "Bearer " + apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Map.class);
        return resp == null ? null : String.valueOf(resp.get("job_id"));
    }

    /** 排程單封：在 payload 加 scheduled_at（ISO 8601）；回傳該封 id */
    @Override
    public String schedule(Email email, Instant scheduledAt) {
        Map<String, Object> body = toPayload(email);
        body.put("scheduled_at", DateTimeFormatter.ISO_INSTANT.format(scheduledAt));
        Map<?, ?> resp = client.post()
            .uri("/api/v1/zsend/emails/schedule")
            .header("Authorization", "Bearer " + apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Map.class);
        return resp == null ? null : String.valueOf(resp.get("id"));
    }

    /** 取消排程信：DELETE /emails/scheduled/{id}；2xx 未拋例外即回 true */
    @Override
    public boolean cancelScheduled(String providerId) {
        client.delete()
            .uri("/api/v1/zsend/emails/scheduled/{id}", providerId)
            .header("Authorization", "Bearer " + apiKey)
            .retrieve()
            .toBodilessEntity();
        return true;
    }

    /** 把一封 Email 組成 ZSend 單封 payload（含寄件人與選填 reply_to） */
    private Map<String, Object> toPayload(Email email) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", from);
        m.put("to", List.of(email.to()));
        m.put("subject", email.subject());
        m.put("html", email.html());
        if (StringUtils.hasText(replyTo)) {
            m.put("reply_to", List.of(replyTo));
        }
        return m;
    }
}
