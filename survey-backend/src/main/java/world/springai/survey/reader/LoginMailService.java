package world.springai.survey.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.MailSender;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

/**
 * 寄送 magic link 登入信。
 *
 * <p><b>刻意不使用 EmailTemplate.wrap()</b>：那個外框的頁腳寫「你會收到這封信，
 * 是因為你填寫了興趣調查並同意接收課程資訊」並附退訂連結。但登入信是**交易信**——
 * 讀者即使退訂了行銷信，仍然必須能登入看他已解鎖的文章。給交易信附退訂連結
 * 是把兩種同意混為一談。</p>
 *
 * <p><b>失敗必須回報</b>（spec §6）：與 WelcomeMailService 刻意吞例外的做法相反。
 * 歡迎信晚到沒差，但讀者正在等登入信，顯示成功假象會讓他一直重試。</p>
 */
@Service
public class LoginMailService {

    private static final Logger log = LoggerFactory.getLogger(LoginMailService.class);

    /** email_log 的信件類型 */
    public static final String LOG_TYPE = "login";

    /** 登入信主旨 */
    private static final String SUBJECT = "你的登入連結";

    private final LoginTokenService tokenService;
    private final MailSender mailSender;
    private final EmailLogRepository emailLogRepository;
    /** 組登入連結用的對外網址 */
    private final String publicBaseUrl;

    /** 注入 token 服務、寄信、寄送記錄與對外網址 */
    public LoginMailService(LoginTokenService tokenService,
                           MailSender mailSender,
                           EmailLogRepository emailLogRepository,
                           @Value("${app.public-base-url}") String publicBaseUrl) {
        this.tokenService = tokenService;
        this.mailSender = mailSender;
        this.emailLogRepository = emailLogRepository;
        this.publicBaseUrl = publicBaseUrl;
    }

    /**
     * 寄送結果。
     *
     * @param sent      是否成功寄出
     * @param throttled 是否因節流而未寄（前端要顯示不同訊息）
     */
    public record SendResult(boolean sent, boolean throttled) {}

    /**
     * 寄一封登入信。
     *
     * @param redirect 登入成功後要回到的站內路徑；站外網址一律丟棄（防開放式轉址）
     */
    public SendResult sendLoginLink(String email, String redirect, OffsetDateTime now) {
        if (tokenService.isThrottled(email, now)) {
            log.info("登入信節流，暫不寄送 to={}", email);
            return new SendResult(false, true);
        }

        String rawToken = tokenService.issue(email, now);
        String link = buildLoginLink(rawToken, redirect);
        String html = buildHtml(link);

        try {
            String providerId = mailSender.send(email, SUBJECT, html);
            saveLog(email, providerId, "sent", null);
            return new SendResult(true, false);
        } catch (Exception e) {
            log.warn("登入信寄送失敗 to={}：{}", email, e.getMessage());
            saveLog(email, null, "failed", e.getMessage());
            return new SendResult(false, false);
        }
    }

    /** 組登入連結；只接受站內相對路徑作為 redirect */
    private String buildLoginLink(String rawToken, String redirect) {
        StringBuilder link = new StringBuilder(publicBaseUrl)
            .append("/api/reader/login/verify?t=")
            .append(URLEncoder.encode(rawToken, StandardCharsets.UTF_8));
        if (RedirectGuard.isSafe(redirect)) {
            // 放進連結的是「原始值」而非正規化後的值：RedirectGuard.isSafe 已確認
            // 正規化後（反斜線轉斜線）的樣子不含 scheme/host/authority，
            // 而若原始值含反斜線，瀏覽器解析時本來就會等價地轉成斜線，
            // 所以放原始值不會讓使用者實際跳轉到與正規化判斷不同的地方，
            // 且能保留使用者原本輸入的路徑字面樣式（例如大小寫、既有跳脫）。
            link.append("&redirect=").append(URLEncoder.encode(redirect, StandardCharsets.UTF_8));
        }
        return link.toString();
    }

    /** 組登入信 HTML；刻意不含退訂連結（交易信） */
    private String buildHtml(String loginLink) {
        return """
            <div style="font-family:system-ui,'Microsoft JhengHei',sans-serif;line-height:1.7;max-width:560px;margin:0 auto;color:#102033">
              <h2>點下面的按鈕就能登入</h2>
              <p>這個連結 15 分鐘內有效，而且只能使用一次。</p>
              <p style="text-align:center;margin:28px 0">
                <a href="%s" style="background:#0d9488;color:#fff;padding:12px 28px;border-radius:8px;text-decoration:none;font-weight:700">登入電子報</a>
              </p>
              <hr style="border:none;border-top:1px solid #dce5ee;margin:24px 0">
              <p style="color:#8190a3;font-size:.85rem">
                如果不是你要求登入的，直接忽略這封信即可——沒有人能在未點擊此連結的情況下進入你的帳戶。
              </p>
            </div>
            """.formatted(loginLink);
    }

    /** 寫一筆寄送記錄；記錄失敗只記 log，不影響回報給呼叫端的結果 */
    private void saveLog(String email, String providerId, String status, String error) {
        try {
            emailLogRepository.save(new EmailLog(email, SUBJECT, LOG_TYPE, providerId, status, error));
        } catch (Exception e) {
            log.warn("寫入 email_log 失敗 to={}：{}", email, e.getMessage());
        }
    }
}
