package world.springai.survey.audience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.EmailTemplate;
import world.springai.survey.mail.MailSender;

/** 問卷送出後寄歡迎信：組信（含退訂連結）→ 寄送 → 寫 email_log；任何失敗只記 log，永不拋例外 */
@Service
public class WelcomeMailService {

    private static final Logger log = LoggerFactory.getLogger(WelcomeMailService.class);

    /** 歡迎信主旨 */
    private static final String SUBJECT = "歡迎加入｜AI 賦能全端開發課程資訊";

    private final MailSender mailSender;
    private final EmailLogRepository emailLogRepository;
    private final EmailTemplate emailTemplate; // 共用信件外框模板
    private final SubscriptionLinkBuilder linkBuilder; // 退訂連結組裝的唯一擁有者

    /** 注入寄信、寄送記錄、信件模板與連結組裝器 */
    public WelcomeMailService(MailSender mailSender,
                              EmailLogRepository emailLogRepository,
                              EmailTemplate emailTemplate,
                              SubscriptionLinkBuilder linkBuilder) {
        this.mailSender = mailSender;
        this.emailLogRepository = emailLogRepository;
        this.emailTemplate = emailTemplate;
        this.linkBuilder = linkBuilder;
    }

    /** 寄一封歡迎信給填寫者；成功記 sent、失敗記 failed，皆不向外拋例外 */
    public void sendWelcome(String email) {
        try {
            String unsubscribeLink = linkBuilder.unsubscribeLink(email);
            // 確認連結：讓讀者能主動驗證信箱可達性，同時是推薦獎勵發放的唯一觸發點
            String confirmLink = linkBuilder.confirmLink(email);
            String html = buildHtml(unsubscribeLink, confirmLink);
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

    /** 組歡迎信 HTML：歡迎內文＋信箱確認 CTA，外框與退訂頁腳交給共用模板 */
    private String buildHtml(String unsubscribeLink, String confirmLink) {
        String body = """
            <h2>歡迎你！🎉</h2>
            <p>謝謝你填寫「AI 賦能全端開發」課程興趣調查。我們會在課程開放報名、釋出早鳥優惠時優先通知你。</p>
            <p>在那之前，你可以先看看課程網站，了解整個實戰學習路徑。</p>
            <div style="margin:28px 0;padding:20px;border:1px solid #dce5ee;border-radius:12px;background:#f7fafc;text-align:center">
              <p style="margin:0 0 14px;font-weight:700">順手確認一下你的信箱</p>
              <p style="margin:0 0 18px;color:#5c6b7d;font-size:.92rem">
                點一下按鈕，我們就能確認這個地址收得到信，之後的內容不會漏掉。
              </p>
              <a href="%s" style="display:inline-block;background:#0d9488;color:#fff;padding:12px 28px;border-radius:8px;text-decoration:none;font-weight:700">確認我的信箱</a>
            </div>
            """.formatted(confirmLink);
        return emailTemplate.wrap(body, unsubscribeLink);
    }
}
