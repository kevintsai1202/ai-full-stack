package world.springai.survey.newsletter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

import world.springai.survey.audience.SubscriptionLinkBuilder;
import world.springai.survey.audience.SurveyResponse;
import world.springai.survey.audience.SurveyResponseRepository;
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.MailSender;
import world.springai.survey.mail.MailTemplate;
import world.springai.survey.mail.MailTemplateRepository;

/**
 * 二次確認（re-permission）邀請信：對「匯入的待確認名單」（consent=false 且未退訂）
 * 逐封寄送含個人化 HMAC 確認連結的邀請信；點擊確認後才轉為可寄送名單。
 */
@Service
public class InviteService {

    private static final Logger log = LoggerFactory.getLogger(InviteService.class);

    /** 範本識別鍵 */
    static final String TEMPLATE_KEY = "invite";
    /** 內文中的確認連結佔位符（儲存範本時強制必須存在） */
    static final String CONFIRM_LINK_PLACEHOLDER = "{{confirmLink}}";

    /** 邀請信預設主旨（資料庫無範本時的退路） */
    private static final String SUBJECT = "你上過我的課——要不要一起繼續深入？";

    private final SurveyResponseRepository repository;
    private final MailSender mailSender;
    private final EmailLogRepository emailLogRepository;
    private final MailTemplateRepository templateRepository; // 邀請信範本（後台可編輯）
    private final SubscriptionLinkBuilder linkBuilder; // 確認連結組裝的唯一擁有者

    /** 注入資料層、寄信、寄送記錄、範本與連結組裝器 */
    public InviteService(SurveyResponseRepository repository,
                         MailSender mailSender,
                         EmailLogRepository emailLogRepository,
                         MailTemplateRepository templateRepository,
                         SubscriptionLinkBuilder linkBuilder) {
        this.repository = repository;
        this.mailSender = mailSender;
        this.emailLogRepository = emailLogRepository;
        this.templateRepository = templateRepository;
        this.linkBuilder = linkBuilder;
    }

    /**
     * 邀請寄送結果摘要：
     * recipientCount=本次實際嘗試數、alreadyInvited=已寄過而跳過數、remaining=因 limit 未寄的剩餘數
     */
    public record InviteResult(int recipientCount, int accepted, int failed,
                               int alreadyInvited, int remaining) {}

    /**
     * 對指定來源的待確認名單逐封寄邀請信；單封失敗不中斷整批。
     * 已寄過邀請者（email_log type=invite status=sent）自動跳過，避免重跑時重複騷擾；
     * limit 有值時單次最多寄 limit 封（配合寄信額度分天寄送），其餘回報於 remaining。
     */
    public InviteResult sendInvites(String source, Integer limit) {
        List<SurveyResponse> pending = repository.findBySourceAndConsentFalseAndUnsubscribedFalse(source);
        // 已寄過邀請的 email 集合（正規化為小寫比對）
        java.util.Set<String> invited = emailLogRepository.findByTypeAndStatus("invite", "sent").stream()
            .map(l -> l.getRecipient().trim().toLowerCase())
            .collect(java.util.stream.Collectors.toSet());
        List<SurveyResponse> eligible = pending.stream()
            .filter(r -> !invited.contains(r.getEmail().trim().toLowerCase()))
            .toList();
        int alreadyInvited = pending.size() - eligible.size();
        // 套用單次寄送上限；剩餘數留給下次呼叫
        List<SurveyResponse> targets = (limit != null && limit > 0 && limit < eligible.size())
            ? eligible.subList(0, limit) : eligible;
        int remaining = eligible.size() - targets.size();

        // 整批共用同一份範本（資料庫可編輯，無資料時退回內建預設）
        MailTemplate template = getTemplate();
        int accepted = 0;
        int failed = 0;
        for (SurveyResponse r : targets) {
            try {
                String html = template.getBodyHtml().replace(CONFIRM_LINK_PLACEHOLDER, linkBuilder.confirmLink(r.getEmail()));
                String id = mailSender.send(r.getEmail(), template.getSubject(), html);
                emailLogRepository.save(new EmailLog(r.getEmail(), template.getSubject(), "invite", id, "sent", null));
                accepted++;
            } catch (Exception e) {
                log.warn("邀請信寄送失敗 to={}：{}", r.getEmail(), e.getMessage());
                emailLogRepository.save(new EmailLog(r.getEmail(), template.getSubject(), "invite", null, "failed", e.getMessage()));
                failed++;
            }
        }
        return new InviteResult(targets.size(), accepted, failed, alreadyInvited, remaining);
    }

    /** 補送提醒的最小間隔天數：距上次邀請未滿此天數不寄，避免騷擾 */
    static final int REMINDER_MIN_INTERVAL_DAYS = 3;
    /** 提醒信的 email_log 類型（與首次邀請 invite 區分，用於「每人最多補送一次」） */
    static final String REMINDER_TYPE = "invite_reminder";

    /**
     * 補送提醒結果：
     * recipientCount=本次實際嘗試數、alreadyReminded=已提醒過而跳過數（每人最多 1 次）、
     * tooRecent=距上次邀請未滿間隔天數而跳過數、remaining=因 limit 未寄的剩餘數
     */
    public record ReminderResult(int recipientCount, int accepted, int failed,
                                 int alreadyReminded, int tooRecent, int remaining) {}

    /**
     * 對「已邀請但尚未確認」者補送提醒信（沿用邀請信範本）。防騷擾規則：
     * 距最近一次邀請需滿 {@link #REMINDER_MIN_INTERVAL_DAYS} 天、每人最多補送一次；
     * 已確認（consent=true）與已退訂者天然不在待確認名單中，不會被提醒。
     */
    public ReminderResult sendReminders(String source, Integer limit) {
        List<SurveyResponse> pending = repository.findBySourceAndConsentFalseAndUnsubscribedFalse(source);
        // 每人最近一次成功邀請的時間（小寫 email → createdAt；createdAt 為 null 視為夠久遠）
        java.util.Map<String, java.time.OffsetDateTime> invitedAt = new java.util.HashMap<>();
        for (EmailLog l : emailLogRepository.findByTypeAndStatus("invite", "sent")) {
            String k = l.getRecipient().trim().toLowerCase();
            java.time.OffsetDateTime t = l.getCreatedAt();
            if (!invitedAt.containsKey(k) || (t != null && invitedAt.get(k) != null && t.isAfter(invitedAt.get(k)))) {
                invitedAt.put(k, t);
            }
        }
        // 已提醒過的 email 集合（每人最多補送一次）
        java.util.Set<String> reminded = emailLogRepository.findByTypeAndStatus(REMINDER_TYPE, "sent").stream()
            .map(l -> l.getRecipient().trim().toLowerCase())
            .collect(java.util.stream.Collectors.toSet());

        java.time.OffsetDateTime threshold = java.time.OffsetDateTime.now().minusDays(REMINDER_MIN_INTERVAL_DAYS);
        int alreadyReminded = 0;
        int tooRecent = 0;
        List<SurveyResponse> eligible = new java.util.ArrayList<>();
        for (SurveyResponse r : pending) {
            String k = r.getEmail().trim().toLowerCase();
            if (!invitedAt.containsKey(k)) {
                continue; // 從未邀請過的人走首次邀請流程，不在補送範圍
            }
            if (reminded.contains(k)) {
                alreadyReminded++;
                continue;
            }
            java.time.OffsetDateTime last = invitedAt.get(k);
            if (last != null && last.isAfter(threshold)) {
                tooRecent++;
                continue;
            }
            eligible.add(r);
        }
        // 套用單次寄送上限；剩餘數留給下次呼叫
        List<SurveyResponse> targets = (limit != null && limit > 0 && limit < eligible.size())
            ? eligible.subList(0, limit) : eligible;
        int remaining = eligible.size() - targets.size();

        MailTemplate template = getTemplate();
        int accepted = 0;
        int failed = 0;
        for (SurveyResponse r : targets) {
            try {
                String html = template.getBodyHtml().replace(CONFIRM_LINK_PLACEHOLDER, linkBuilder.confirmLink(r.getEmail()));
                String id = mailSender.send(r.getEmail(), template.getSubject(), html);
                emailLogRepository.save(new EmailLog(r.getEmail(), template.getSubject(), REMINDER_TYPE, id, "sent", null));
                accepted++;
            } catch (Exception e) {
                log.warn("提醒信寄送失敗 to={}：{}", r.getEmail(), e.getMessage());
                emailLogRepository.save(new EmailLog(r.getEmail(), template.getSubject(), REMINDER_TYPE, null, "failed", e.getMessage()));
                failed++;
            }
        }
        return new ReminderResult(targets.size(), accepted, failed, alreadyReminded, tooRecent, remaining);
    }

    /** 取得邀請信範本：優先用資料庫版本，無資料時退回內建預設（不落庫） */
    public MailTemplate getTemplate() {
        return templateRepository.findByTemplateKey(TEMPLATE_KEY)
            .orElseGet(() -> new MailTemplate(TEMPLATE_KEY, SUBJECT, defaultBody()));
    }

    /**
     * 更新邀請信範本並存入資料庫：
     * 主旨與內文不得空白，內文必須含 {{confirmLink}} 佔位符（否則收件人無法確認訂閱）。
     */
    public MailTemplate updateTemplate(String subject, String bodyHtml) {
        if (subject == null || subject.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "主旨不得空白");
        }
        if (bodyHtml == null || bodyHtml.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "內文不得空白");
        }
        if (!bodyHtml.contains(CONFIRM_LINK_PLACEHOLDER)) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "內文必須包含確認連結佔位符 " + CONFIRM_LINK_PLACEHOLDER);
        }
        // 已有範本則就地更新，否則建立新紀錄（首次部署 migration 已種入，一般走更新路徑）
        MailTemplate template = templateRepository.findByTemplateKey(TEMPLATE_KEY)
            .orElseGet(() -> new MailTemplate(TEMPLATE_KEY, subject, bodyHtml));
        template.setSubject(subject);
        template.setBodyHtml(bodyHtml);
        return templateRepository.save(template);
    }

    /**
     * 後台邀請記錄總覽：
     * invitedCount=已成功寄出邀請的不重複人數、remindedCount=已補送提醒的不重複人數、
     * confirmedCount=該來源已確認訂閱人數、pendingCount=該來源尚未邀請的待確認人數、
     * logs=全部邀請與提醒寄送記錄（新到舊）
     */
    public record InviteOverview(long invitedCount, long remindedCount, long confirmedCount,
                                 long pendingCount, List<EmailLog> logs) {}

    /** 彙整指定來源的邀請寄送記錄與成效統計，供後台「邀請記錄」顯示 */
    public InviteOverview overview(String source) {
        // 記錄列表同時包含首次邀請與補送提醒，合併後依時間新到舊
        List<EmailLog> logs = new java.util.ArrayList<>(emailLogRepository.findByTypeOrderByCreatedAtDesc("invite"));
        logs.addAll(emailLogRepository.findByTypeOrderByCreatedAtDesc(REMINDER_TYPE));
        logs.sort(java.util.Comparator.comparing(EmailLog::getCreatedAt,
            java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        // 已成功寄出邀請／提醒的 email 集合（小寫去重）
        java.util.Set<String> invited = logs.stream()
            .filter(l -> "invite".equals(l.getType()) && "sent".equals(l.getStatus()))
            .map(l -> l.getRecipient().trim().toLowerCase())
            .collect(java.util.stream.Collectors.toSet());
        long remindedCount = logs.stream()
            .filter(l -> REMINDER_TYPE.equals(l.getType()) && "sent".equals(l.getStatus()))
            .map(l -> l.getRecipient().trim().toLowerCase())
            .distinct().count();
        // 該來源尚未邀請的待確認人數
        long pending = repository.findBySourceAndConsentFalseAndUnsubscribedFalse(source).stream()
            .filter(r -> !invited.contains(r.getEmail().trim().toLowerCase()))
            .count();
        long confirmed = repository.countBySourceAndConsentTrueAndUnsubscribedFalse(source);
        return new InviteOverview(invited.size(), remindedCount, confirmed, pending, logs);
    }

    /**
     * 邀請信預設 HTML（資料庫無範本時的退路，與 V6 migration 種子內容一致）：
     * 說明來意與訂閱好處，附確認按鈕（{{confirmLink}} 佔位）。
     * 不套 EmailTemplate（其頁腳的「已同意接收」敘述不適用於尚未同意者）；
     * 未點確認者不會再收到信，故頁腳明示「略過即不再打擾」。
     */
    private String defaultBody() {
        return """
            <div style="font-family:system-ui,'Microsoft JhengHei',sans-serif;line-height:1.7;max-width:560px;margin:0 auto;color:#1a1a2e">
              <h2>嗨，好久不見，我是凱文大叔！</h2>
              <p>你會收到這封信，是因為你之前上過我的<strong>基礎課程</strong>，也參加過課後的<strong>線上測驗</strong>——先謝謝你當時的參與。</p>
              <p>最近我正在準備一份<strong>電子報</strong>，想把平常研究和實戰的東西整理起來，固定分享給老同學。訂閱之後你會收到：</p>
              <ul>
                <li><strong>深入的技術討論</strong>：RAG、AI Agent、全端實戰的實作細節與踩雷筆記</li>
                <li><strong>AI 新知與新技術</strong>：新模型、新工具與趨勢的第一手觀察整理</li>
                <li><strong>各種好康優惠</strong>：除了 AI 產品的優惠活動外，還包含我自己線上、線下課程的專屬優惠</li>
              </ul>
              <p>如果你願意收到，點下面確認一下就好：</p>
              <p style="text-align:center;margin:28px 0">
                <a href="{{confirmLink}}" style="background:#0d9488;color:#fff;padding:12px 28px;border-radius:8px;text-decoration:none;font-weight:700">是的，我要訂閱</a>
              </p>
              <hr style="border:none;border-top:1px solid #eee;margin:24px 0">
              <p style="color:#888;font-size:.85rem">
                寄件人：凱文大叔（你曾參加過我的基礎課程與線上測驗）。<br>
                若不想收到，直接略過這封信即可——未確認前我們不會再寄信給你。
              </p>
            </div>
            """;
    }
}
