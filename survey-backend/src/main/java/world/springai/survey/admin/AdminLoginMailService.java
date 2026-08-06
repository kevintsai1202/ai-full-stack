package world.springai.survey.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import world.springai.survey.mail.MailSender;
import world.springai.survey.reader.LoginToken;
import world.springai.survey.reader.LoginTokenService;

import java.time.OffsetDateTime;

/**
 * 寄送管理後台的 magic-link 登入信。
 *
 * <p><b>只寄給白名單</b>，且刻意不回報「有沒有寄出」：呼叫端一律回相同訊息，
 * 避免這個端點被拿來逐一測試哪個 email 是管理者。</p>
 */
@Service
public class AdminLoginMailService {

    private static final Logger log = LoggerFactory.getLogger(AdminLoginMailService.class);

    /** 登入信主旨 */
    private static final String SUBJECT = "管理後台登入連結";

    private final AdminAllowlist allowlist;
    private final LoginTokenService tokenService;
    private final MailSender mailSender;
    private final AdminSiteLinks siteLinks;

    /** 注入白名單、token 服務、寄信與連結組裝器 */
    public AdminLoginMailService(AdminAllowlist allowlist,
                                 LoginTokenService tokenService,
                                 MailSender mailSender,
                                 AdminSiteLinks siteLinks) {
        this.allowlist = allowlist;
        this.tokenService = tokenService;
        this.mailSender = mailSender;
        this.siteLinks = siteLinks;
    }

    /**
     * 若 email 為管理者則寄出登入信；否則靜默略過（不得讓呼叫端得知差異）。
     *
     * <p><b>節流是必要的</b>：本方法對外只回 void，端點一律回相同結果，所以沒有任何
     * 回應差異能讓外部得知是否被節流——防枚舉設計完全不受影響。但少了它，
     * 任何人只要猜中管理者 email 就能無限觸發寄信：信箱被灌爆事小，
     * <b>吃光交易信額度會讓讀者無法登入</b>（見 {@code app.mail.transactional-reserve}
     * 的說明，那是產品故障而非延遲），{@code login_token} 也會無限增長。
     * 讀者路徑的 {@code LoginMailService.sendLoginLink} 第一行就是同一道檢查，
     * 這裡沿用同一個服務、同一組門檻，不另立一套。</p>
     *
     * <p>節流<b>必須在 {@code issue} 之前</b>：先簽發再判斷等於每次請求都寫一列
     * {@code login_token}，節流形同虛設。</p>
     */
    public void sendIfAdmin(String email, OffsetDateTime now) {
        if (!allowlist.isAdmin(email)) {
            log.info("非管理者的後台登入請求，略過寄送");
            return;
        }
        if (tokenService.isThrottled(email, now)) {
            log.info("後台登入信節流，暫不寄送");
            return;
        }
        try {
            String rawToken = tokenService.issue(email, LoginToken.PURPOSE_ADMIN, now);
            mailSender.send(email, SUBJECT, buildHtml(siteLinks.verifyLogin(rawToken)));
        } catch (Exception e) {
            // 不得往外拋：端點回 500 等於告訴對方這個 email 是管理者。
            // 但必須帶著 stack trace 進日誌：magic-link 是本功能唯一的登入手段，
            // 只印 e.getMessage() 時「連結一直收不到」這種故障沒有任何可診斷的線索。
            log.warn("後台登入信寄送失敗", e);
        }
    }

    /** 組登入信 HTML；交易信，刻意不含退訂連結 */
    private String buildHtml(String loginLink) {
        return """
            <div style="font-family:system-ui,'Microsoft JhengHei',sans-serif;line-height:1.7;max-width:560px;margin:0 auto;color:#102033">
              <h2>管理後台登入</h2>
              <p>這個連結 15 分鐘內有效，而且只能使用一次。</p>
              <p style="text-align:center;margin:28px 0">
                <a href="%s" style="background:#0d9488;color:#fff;padding:12px 28px;border-radius:8px;text-decoration:none;font-weight:700">登入後台</a>
              </p>
              <p style="color:#8190a3;font-size:.85rem">若不是你本人操作，請忽略這封信。</p>
            </div>
            """.formatted(loginLink);
    }
}
