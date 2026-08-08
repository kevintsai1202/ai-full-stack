package world.springai.survey.newsletter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

import world.springai.survey.audience.SubscriptionLinkBuilder;
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.EmailTemplate;
import world.springai.survey.mail.MailSender;

/**
 * 對「已訂閱但從未點過確認連結」的既有名單補寄一次確認信。
 *
 * <p><b>為什麼不擴充 {@link InviteService}</b>：那支服務的名單口徑是
 * {@code consent = false}（匯入的待確認名單），本服務要的是
 * {@code consent = true} 但沒有確認紀錄的人——條件正好相反。
 * {@code InviteService} 已有 sendInvites／sendReminders 兩個高度相似的方法，
 * 塞進第三種語意只會讓那個檔案更難讀。</p>
 *
 * <p><b>本服務屬行銷側，必須受寄信額度 reserve 約束</b>：讀者不在等這封信，
 * 晚一天寄沒有損失，而 magic link 登入信是讀者當下盯著信箱等的那一封。
 * 額度收斂由 {@code AdminCampaignController.clampLimit()} 在端點層負責
 * （見 {@code MailQuotaService} 對「確認信」兩種語意的區分）。</p>
 */
@Service
public class ReconfirmService {

    private static final Logger log = LoggerFactory.getLogger(ReconfirmService.class);

    /** email_log 類型；每人終身只補寄一次靠它判斷 */
    static final String LOG_TYPE = "reconfirm";

    /** 補寄信主旨 */
    private static final String SUBJECT = "請確認你的訂閱信箱｜AI 賦能全端開發";

    /**
     * 待補寄名單：已同意、未退訂、沒有確認連結紀錄、沒被補寄過。
     *
     * <p>第三個條件（audience_consent）避免騷擾已經確認過的人；
     * 第四個條件（email_log）讓每人終身只收到一次。</p>
     */
    private static final String PENDING_SQL = """
        select distinct lower(sr.email)
          from survey_response sr
         where sr.consent = true
           and sr.unsubscribed = false
           and not exists (
             select 1 from audience_person p
               join audience_consent c on c.person_id = p.id
              where p.email_normalized = lower(sr.email)
                and c.channel = 'EMAIL'
                and c.status = 'CONFIRMED'
                and c.source_key = 'confirmation-link')
           and not exists (
             select 1 from email_log el
              where lower(el.recipient) = lower(sr.email)
                and el.type = 'reconfirm'
                and el.status = 'sent')
         order by lower(sr.email)
        """;

    /** 已補寄過的人數（供操作者理解名單為何變小） */
    private static final String ALREADY_SENT_SQL = """
        select count(distinct lower(el.recipient))
          from email_log el
         where el.type = 'reconfirm' and el.status = 'sent'
        """;

    /** 已透過確認連結確認過的人數（同上，屬被排除的另一半原因） */
    private static final String ALREADY_CONFIRMED_SQL = """
        select count(distinct p.id)
          from audience_person p
          join audience_consent c on c.person_id = p.id
         where c.channel = 'EMAIL'
           and c.status = 'CONFIRMED'
           and c.source_key = 'confirmation-link'
        """;

    private final JdbcTemplate jdbc;
    private final MailSender mailSender;
    private final EmailLogRepository emailLogRepository;
    private final EmailTemplate emailTemplate; // 共用信件外框（含退訂頁腳）
    private final SubscriptionLinkBuilder linkBuilder; // 確認／退訂連結組裝的唯一擁有者

    /** 注入名單查詢、寄信、寄送記錄、信件外框與連結組裝器 */
    public ReconfirmService(JdbcTemplate jdbc,
                            MailSender mailSender,
                            EmailLogRepository emailLogRepository,
                            EmailTemplate emailTemplate,
                            SubscriptionLinkBuilder linkBuilder) {
        this.jdbc = jdbc;
        this.mailSender = mailSender;
        this.emailLogRepository = emailLogRepository;
        this.emailTemplate = emailTemplate;
        this.linkBuilder = linkBuilder;
    }

    /**
     * 補寄結果摘要。
     *
     * @param recipientCount   本次實際嘗試寄送數
     * @param accepted         寄送成功數
     * @param failed           寄送失敗數
     * @param alreadySent      先前已補寄過而被排除的人數
     * @param alreadyConfirmed 已點過確認連結而被排除的人數
     * @param remaining        因 limit 未寄的剩餘數
     */
    public record ReconfirmResult(int recipientCount, int accepted, int failed,
                                  int alreadySent, int alreadyConfirmed, int remaining) {}

    /** 待補寄人數，供後台顯示按鈕旁的數字 */
    public int pendingCount() {
        return jdbc.queryForList(PENDING_SQL, String.class).size();
    }

    /** 單一 count 查詢的取值；null 視為 0 */
    private int countOf(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value.intValue();
    }

    /**
     * 對待補寄名單逐封寄確認信；單封失敗不中斷整批。
     *
     * @param limit 單次寄送上限；null 或 &lt;= 0 視為不限（端點層已先收斂到額度內）
     */
    public ReconfirmResult sendReconfirmations(Integer limit) {
        List<String> pending = jdbc.queryForList(PENDING_SQL, String.class);
        List<String> targets = (limit != null && limit > 0 && limit < pending.size())
            ? pending.subList(0, limit) : pending;
        int remaining = pending.size() - targets.size();

        int accepted = 0;
        int failed = 0;
        for (String email : targets) {
            try {
                String html = buildHtml(linkBuilder.unsubscribeLink(email),
                    linkBuilder.confirmLink(email));
                String id = mailSender.send(email, SUBJECT, html);
                emailLogRepository.save(new EmailLog(email, SUBJECT, LOG_TYPE, id, "sent", null));
                accepted++;
            } catch (Exception e) {
                log.warn("補寄確認信失敗 to={}：{}", email, e.getMessage());
                emailLogRepository.save(
                    new EmailLog(email, SUBJECT, LOG_TYPE, null, "failed", e.getMessage()));
                failed++;
            }
        }
        return new ReconfirmResult(targets.size(), accepted, failed,
            countOf(ALREADY_SENT_SQL), countOf(ALREADY_CONFIRMED_SQL), remaining);
    }

    /**
     * 組補寄信 HTML。
     *
     * <p>文案刻意<b>不提推薦獎勵</b>（全體收件，多數人沒有推薦人），
     * 也<b>不主動說明不點確認的後果</b>——但同樣不得暗示會被取消訂閱，
     * 那是欺騙。訴求收斂在「信箱可達性」這一件對每個收件人都成立的事。</p>
     */
    private String buildHtml(String unsubscribeLink, String confirmLink) {
        String body = """
            <h2>請確認你的訂閱信箱</h2>
            <p>你先前訂閱了這份電子報，訂閱目前仍然有效——這封信不是重新徵求你的同意。</p>
            <p>我們最近加上了信箱確認機制。沒有經過確認的地址，我們無法分辨「你收到了但還沒打開」
               和「這封信根本沒送達」——打錯字的地址、已停用的信箱、被歸進垃圾信匣的，
               在數據上長得一模一樣。</p>
            <div style="margin:28px 0;padding:20px;border:1px solid #dce5ee;border-radius:12px;background:#f7fafc;text-align:center">
              <p style="margin:0 0 18px;color:#5c6b7d;font-size:.92rem">點一下按鈕即可完成，只需幾秒。</p>
              <a href="%s" style="display:inline-block;background:#0d9488;color:#fff;padding:12px 28px;border-radius:8px;text-decoration:none;font-weight:700">確認我的信箱</a>
            </div>
            """.formatted(confirmLink);
        return emailTemplate.wrap(body, unsubscribeLink);
    }
}
