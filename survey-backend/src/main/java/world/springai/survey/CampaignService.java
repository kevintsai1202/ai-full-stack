package world.springai.survey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 電子報發送：渲染內文、組個人化退訂連結、立即(batch)/排程(schedule) 發送、記錄 campaign 與 email_log */
@Service
public class CampaignService {

    private static final Logger log = LoggerFactory.getLogger(CampaignService.class);
    private static final int BATCH_SIZE = 100; // ZSend 單次 batch 上限

    private final MailSender mailSender;
    private final RecipientService recipientService;
    private final CampaignRepository campaignRepository;
    private final EmailLogRepository emailLogRepository;
    private final MarkdownRenderer markdownRenderer;
    private final EmailTemplate emailTemplate;
    private final UnsubscribeTokenService tokenService;
    private final String publicBaseUrl;

    public CampaignService(MailSender mailSender,
                           RecipientService recipientService,
                           CampaignRepository campaignRepository,
                           EmailLogRepository emailLogRepository,
                           MarkdownRenderer markdownRenderer,
                           EmailTemplate emailTemplate,
                           UnsubscribeTokenService tokenService,
                           @Value("${app.public-base-url}") String publicBaseUrl) {
        this.mailSender = mailSender;
        this.recipientService = recipientService;
        this.campaignRepository = campaignRepository;
        this.emailLogRepository = emailLogRepository;
        this.markdownRenderer = markdownRenderer;
        this.emailTemplate = emailTemplate;
        this.tokenService = tokenService;
        this.publicBaseUrl = publicBaseUrl;
    }

    /** 發送結果摘要 */
    public record SendResult(Long campaignId, int recipientCount, int accepted, int failed) {}

    /** 預覽：把 markdown 渲染並套外框（用示意退訂連結） */
    public String preview(String subject, String markdown) {
        String body = markdownRenderer.toHtml(markdown);
        return emailTemplate.wrap(body, publicBaseUrl + "/api/survey/unsubscribe?email=preview%40example.com&t=preview");
    }

    /** 寄一封測試信給指定信箱（立即、單封） */
    public String sendTest(String subject, String markdown, String to) {
        String html = renderFor(markdownRenderer.toHtml(markdown), to);
        return mailSender.send(to, subject, html);
    }

    /**
     * 發送電子報：mode=now 用 batch、mode=schedule 用 schedule。
     * 刻意不加 @Transactional：迴圈中夾帶外部 ZSend 呼叫，且 provider 副作用無法回滾，
     * 部分失敗時保留已寫入的 email_log 記錄比整批回滾更誠實。
     */
    public SendResult send(String subject, String markdown, String role, String interest,
                           String mode, Instant scheduledAt) {
        // 取得收件人清單
        List<String> recipients = recipientService.recipients(role, interest);
        // 渲染 markdown 為 HTML 內文
        String bodyHtml = markdownRenderer.toHtml(markdown);
        boolean scheduled = "schedule".equals(mode);

        // 建立 campaign 紀錄並預存（之後更新統計）
        Campaign campaign = new Campaign(subject, markdown, bodyHtml, role, interest, mode,
            scheduledAt == null ? null : OffsetDateTime.ofInstant(scheduledAt, ZoneOffset.UTC),
            recipients.size(), scheduled ? "scheduled" : "sending");
        campaign = campaignRepository.save(campaign);
        Long campaignId = campaign.getId();

        int accepted = 0;
        int failed = 0;

        if (scheduled) {
            // 排程模式：每封個別呼叫 schedule API
            for (String email : recipients) {
                try {
                    String id = mailSender.schedule(
                        new MailSender.Email(email, subject, renderFor(bodyHtml, email)), scheduledAt);
                    emailLogRepository.save(new EmailLog(email, subject, "campaign", id, "scheduled", null, campaignId));
                    accepted++;
                } catch (Exception e) {
                    log.warn("排程寄信失敗 to={}：{}", email, e.getMessage());
                    emailLogRepository.save(new EmailLog(email, subject, "campaign", null, "failed", e.getMessage(), campaignId));
                    failed++;
                }
            }
        } else {
            // 立即模式：每批 ≤100 封呼叫 sendBatch，整批共用一個 job id
            for (int i = 0; i < recipients.size(); i += BATCH_SIZE) {
                List<String> chunk = recipients.subList(i, Math.min(i + BATCH_SIZE, recipients.size()));
                List<MailSender.Email> emails = new ArrayList<>();
                for (String email : chunk) {
                    emails.add(new MailSender.Email(email, subject, renderFor(bodyHtml, email)));
                }
                try {
                    String jobId = mailSender.sendBatch(emails);
                    for (String email : chunk) {
                        emailLogRepository.save(new EmailLog(email, subject, "campaign", jobId, "sent", null, campaignId));
                    }
                    accepted += chunk.size();
                } catch (Exception e) {
                    log.warn("批量寄信失敗 size={}：{}", chunk.size(), e.getMessage());
                    for (String email : chunk) {
                        emailLogRepository.save(new EmailLog(email, subject, "campaign", null, "failed", e.getMessage(), campaignId));
                    }
                    failed += chunk.size();
                }
            }
        }

        // 更新 campaign 統計與最終狀態
        campaign.setAcceptedCount(accepted);
        campaign.setFailedCount(failed);
        campaign.setStatus(finalStatus(scheduled, accepted, failed));
        campaignRepository.save(campaign);

        return new SendResult(campaignId, recipients.size(), accepted, failed);
    }

    /** 取消某 campaign 的所有排程信 */
    public Map<String, Integer> cancelSchedule(Long campaignId) {
        List<EmailLog> rows = emailLogRepository.findByCampaignIdAndStatus(campaignId, "scheduled");
        int cancelled = 0;
        int failed = 0;
        for (EmailLog row : rows) {
            try {
                if (mailSender.cancelScheduled(row.getProviderMessageId())) {
                    row.setStatus("cancelled");
                    emailLogRepository.save(row);
                    cancelled++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                log.warn("取消排程失敗 id={}：{}", row.getProviderMessageId(), e.getMessage());
                failed++;
            }
        }
        // 僅在確實有排程信被取消時才把 campaign 標為 cancelled；
        // 對「已立即寄出」或無排程信的 campaign 呼叫取消則為 no-op，不誤改其狀態
        if (cancelled > 0) {
            campaignRepository.findById(campaignId).ifPresent(c -> {
                c.setStatus("cancelled");
                campaignRepository.save(c);
            });
        }
        return Map.of("cancelled", cancelled, "failed", failed);
    }

    /** 歷史列表（依建立時間降冪） */
    public List<Campaign> list() {
        return campaignRepository.findAllByOrderByCreatedAtDesc();
    }

    /** 把內文 HTML 套上「該收件人」的個人化退訂連結 */
    private String renderFor(String bodyHtml, String email) {
        String link = publicBaseUrl + "/api/survey/unsubscribe?email="
            + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&t=" + tokenService.sign(email);
        return emailTemplate.wrap(bodyHtml, link);
    }

    /** 依是否排程與成敗決定最終狀態字串 */
    private String finalStatus(boolean scheduled, int accepted, int failed) {
        if (accepted == 0 && failed > 0) {
            return "failed";
        }
        return scheduled ? "scheduled" : "sent";
    }
}
