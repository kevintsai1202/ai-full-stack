package world.springai.survey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** 問卷送出後寄歡迎信：組信（含退訂連結）→ 寄送 → 寫 email_log；任何失敗只記 log，永不拋例外 */
@Service
public class WelcomeMailService {

    private static final Logger log = LoggerFactory.getLogger(WelcomeMailService.class);

    /** 歡迎信主旨 */
    private static final String SUBJECT = "歡迎加入｜AI 賦能全端開發課程資訊";

    private final MailSender mailSender;
    private final UnsubscribeTokenService tokenService;
    private final EmailLogRepository emailLogRepository;
    private final String publicBaseUrl; // 組退訂連結用的對外網址

    /** 注入寄信、token 服務、寄送記錄與對外網址 */
    public WelcomeMailService(MailSender mailSender,
                              UnsubscribeTokenService tokenService,
                              EmailLogRepository emailLogRepository,
                              @Value("${app.public-base-url}") String publicBaseUrl) {
        this.mailSender = mailSender;
        this.tokenService = tokenService;
        this.emailLogRepository = emailLogRepository;
        this.publicBaseUrl = publicBaseUrl;
    }

    /** 寄一封歡迎信給填寫者；成功記 sent、失敗記 failed，皆不向外拋例外 */
    public void sendWelcome(String email) {
        try {
            String link = buildUnsubscribeLink(email);
            String html = buildHtml(link);
            String id = mailSender.send(email, SUBJECT, html);
            saveLog(email, id, "sent", null);
        } catch (Exception e) {
            log.warn("歡迎信寄送失敗 to={}：{}", email, e.getMessage());
            saveLog(email, null, "failed", e.getMessage());
        }
    }

    /** 寫一筆寄送記錄；連記錄都失敗時僅記 log，不影響主流程 */
    private void saveLog(String email, String providerId, String status, String error) {
        try {
            emailLogRepository.save(new EmailLog(email, SUBJECT, "welcome", providerId, status, error));
        } catch (Exception e) {
            log.warn("寫入 email_log 失敗 to={}：{}", email, e.getMessage());
        }
    }

    /** 組退訂連結：?email=<urlencoded>&t=<HMAC token> */
    private String buildUnsubscribeLink(String email) {
        String encoded = URLEncoder.encode(email, StandardCharsets.UTF_8);
        String token = tokenService.sign(email);
        return publicBaseUrl + "/api/survey/unsubscribe?email=" + encoded + "&t=" + token;
    }

    /** 組歡迎信 HTML，內含可用退訂連結 */
    private String buildHtml(String unsubscribeLink) {
        return """
            <div style="font-family:system-ui,'Microsoft JhengHei',sans-serif;line-height:1.7;max-width:560px;margin:0 auto;color:#1a1a2e">
              <h2>歡迎你！🎉</h2>
              <p>謝謝你填寫「AI 賦能全端開發」課程興趣調查。我們會在課程開放報名、釋出早鳥優惠時優先通知你。</p>
              <p>在那之前，你可以先看看課程網站，了解整個實戰學習路徑。</p>
              <hr style="border:none;border-top:1px solid #eee;margin:24px 0">
              <p style="color:#888;font-size:.85rem">
                你會收到這封信，是因為你在課程網站填寫了興趣調查並同意接收課程資訊。<br>
                若不想再收到，<a href="%s" style="color:#4f46e5">點此取消訂閱</a>。
              </p>
            </div>
            """.formatted(unsubscribeLink);
    }
}
