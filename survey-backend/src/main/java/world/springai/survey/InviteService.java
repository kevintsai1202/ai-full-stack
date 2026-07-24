package world.springai.survey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 二次確認（re-permission）邀請信：對「匯入的待確認名單」（consent=false 且未退訂）
 * 逐封寄送含個人化 HMAC 確認連結的邀請信；點擊確認後才轉為可寄送名單。
 */
@Service
public class InviteService {

    private static final Logger log = LoggerFactory.getLogger(InviteService.class);

    /** 邀請信主旨 */
    private static final String SUBJECT = "你上過我的課——要不要一起繼續深入？";

    private final SurveyResponseRepository repository;
    private final MailSender mailSender;
    private final EmailLogRepository emailLogRepository;
    private final UnsubscribeTokenService tokenService; // 確認連結重用同一 HMAC 簽章
    private final String publicBaseUrl;                 // 組確認連結用的對外網址

    /** 注入資料層、寄信、寄送記錄、token 服務與對外網址 */
    public InviteService(SurveyResponseRepository repository,
                         MailSender mailSender,
                         EmailLogRepository emailLogRepository,
                         UnsubscribeTokenService tokenService,
                         @Value("${app.public-base-url}") String publicBaseUrl) {
        this.repository = repository;
        this.mailSender = mailSender;
        this.emailLogRepository = emailLogRepository;
        this.tokenService = tokenService;
        this.publicBaseUrl = publicBaseUrl;
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

        int accepted = 0;
        int failed = 0;
        for (SurveyResponse r : targets) {
            try {
                String id = mailSender.send(r.getEmail(), SUBJECT, buildHtml(r.getEmail()));
                emailLogRepository.save(new EmailLog(r.getEmail(), SUBJECT, "invite", id, "sent", null));
                accepted++;
            } catch (Exception e) {
                log.warn("邀請信寄送失敗 to={}：{}", r.getEmail(), e.getMessage());
                emailLogRepository.save(new EmailLog(r.getEmail(), SUBJECT, "invite", null, "failed", e.getMessage()));
                failed++;
            }
        }
        return new InviteResult(targets.size(), accepted, failed, alreadyInvited, remaining);
    }

    /** 組確認連結：/api/survey/confirm?email=<urlencoded>&t=<HMAC token> */
    private String buildConfirmLink(String email) {
        return publicBaseUrl + "/api/survey/confirm?email="
            + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&t=" + tokenService.sign(email);
    }

    /**
     * 邀請信 HTML：說明來意與訂閱好處，附確認按鈕。
     * 不套 EmailTemplate（其頁腳的「已同意接收」敘述不適用於尚未同意者）；
     * 未點確認者不會再收到信，故頁腳明示「略過即不再打擾」。
     */
    private String buildHtml(String email) {
        String link = buildConfirmLink(email);
        return """
            <div style="font-family:system-ui,'Microsoft JhengHei',sans-serif;line-height:1.7;max-width:560px;margin:0 auto;color:#1a1a2e">
              <h2>嗨，好久不見！</h2>
              <p>感謝你先前上過我的基礎課程、參加線上測驗。</p>
              <p>我現在經營一份電子報，把平常研究和實戰的東西整理起來分享。訂閱之後你會固定收到：</p>
              <ul>
                <li><strong>深入的技術討論</strong>：RAG、AI Agent、全端實戰的實作細節與踩雷筆記</li>
                <li><strong>AI 新知與新技術</strong>：新模型、新工具與趨勢的第一手觀察整理</li>
                <li><strong>各種好康優惠</strong>：包含我自己線上、線下課程的專屬優惠與活動</li>
              </ul>
              <p>如果你願意收到，點下面確認一下就好：</p>
              <p style="text-align:center;margin:28px 0">
                <a href="%s" style="background:#0d9488;color:#fff;padding:12px 28px;border-radius:8px;text-decoration:none;font-weight:700">是的，我要訂閱</a>
              </p>
              <hr style="border:none;border-top:1px solid #eee;margin:24px 0">
              <p style="color:#888;font-size:.85rem">
                你會收到這封信，是因為你曾參加我的課程線上測驗。<br>
                若不想收到，直接略過這封信即可——未確認前我們不會再寄信給你。
              </p>
            </div>
            """.formatted(link);
    }
}
