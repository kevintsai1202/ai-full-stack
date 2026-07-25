package world.springai.survey.newsletter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import world.springai.survey.audience.RecipientService;
import world.springai.survey.audience.UnsubscribeTokenService;
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.EmailTemplate;
import world.springai.survey.mail.MailSender;

/** 電子報發送：渲染內文、組個人化退訂連結、立即(batch)/排程(schedule) 發送、記錄 campaign 與 email_log */
@Service
public class CampaignService {

    private static final Logger log = LoggerFactory.getLogger(CampaignService.class);
    private static final int BATCH_SIZE = 100; // ZSend 單次 batch 上限
    /** slug 會直接組進 /r/news/{slug} 網址，僅接受小寫英數與連字號，長度 1~80 */
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9-]{1,80}$");

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
     * 發送電子報（既有 6 參數版本）：等同 tier=BASIC、creditCost=0、slug/publishedAt 皆未指定。
     * 保留此簽章供既有呼叫端與測試直接使用，避免無謂改動。
     */
    public SendResult send(String subject, String markdown, String role, String interest,
                           String mode, Instant scheduledAt) {
        return send(subject, markdown, role, interest, mode, scheduledAt,
            Campaign.TIER_BASIC, 0, null, null);
    }

    /**
     * 發送電子報（含發布欄位）：mode=now 用 batch、mode=schedule 用 schedule。
     * 額外可指定內容分級（tier）、PREMIUM 解鎖點數（creditCost）、網頁文章網址片段（slug）
     * 與發布時間（publishedAt）。這些欄位在寫入 campaign 前會先驗證，避免資料庫 CHECK 約束
     * 或唯一索引以 500 的形式失敗。
     * 刻意不加 @Transactional：迴圈中夾帶外部 ZSend 呼叫，且 provider 副作用無法回滾，
     * 部分失敗時保留已寫入的 email_log 記錄比整批回滾更誠實。
     */
    public SendResult send(String subject, String markdown, String role, String interest,
                           String mode, Instant scheduledAt,
                           String tier, Integer creditCost, String slug, Instant publishedAt) {
        // 驗證並正規化發布欄位，須在建立 campaign 前完成，否則會讓 DB 約束以 500 的形式失敗
        String normalizedTier = validateTier(tier);
        int normalizedCreditCost = validateCreditCost(normalizedTier, creditCost);
        String normalizedSlug = validateSlug(slug);
        OffsetDateTime normalizedPublishedAt = resolvePublishedAt(normalizedSlug, publishedAt);

        // 守門：階段 D（依 tier 產生折疊版內文 foldedHtml）完成前，PREMIUM 內容一旦寄出，
        // markdownRenderer.toHtml(markdown) 會把受限區塊與 <!--paywall--> 之後的全文一併渲染
        // 給「所有」收件人 —— 網頁端擋得再嚴，信件端都已外流付費內容。
        // 待 CampaignService 補上 foldedHtml（依 tier 分兩種內文：全文版／折疊版）後，此檢查方可移除。
        if (!Campaign.TIER_BASIC.equals(normalizedTier)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "PREMIUM 內容的信件折疊尚未實作（階段 D），目前僅能發送 BASIC 內容；此文章可在網頁上以 PREMIUM 發布");
        }

        // 取得收件人清單
        List<String> recipients = recipientService.recipients(role, interest);
        // 渲染 markdown 為 HTML 內文
        String bodyHtml = markdownRenderer.toHtml(markdown);
        boolean scheduled = "schedule".equals(mode);

        // 建立 campaign 紀錄並預存（之後更新統計）
        Campaign campaign = new Campaign(subject, markdown, bodyHtml, role, interest, mode,
            scheduledAt == null ? null : OffsetDateTime.ofInstant(scheduledAt, ZoneOffset.UTC),
            recipients.size(), scheduled ? "scheduled" : "sending");
        // 設定發布相關欄位（本次擴充：tier / creditCost / slug / publishedAt）
        campaign.setTier(normalizedTier);
        campaign.setCreditCost(normalizedCreditCost);
        campaign.setSlug(normalizedSlug);
        campaign.setPublishedAt(normalizedPublishedAt);
        campaign = campaignRepository.save(campaign);
        Long campaignId = campaign.getId();

        int accepted = 0;
        int failed = 0;

        if (scheduled) {
            // 排程模式：每封個別呼叫 schedule API（邏輯抽到 scheduleAll 供 reschedule 共用）
            int[] rc = scheduleAll(campaignId, subject, bodyHtml, recipients, scheduledAt);
            accepted = rc[0];
            failed = rc[1];
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
        int[] rc = cancelProviderScheduled(campaignId);
        int cancelled = rc[0];
        int failed = rc[1];
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

    /**
     * 修改未寄出的排程：先取消該 campaign 現有的 provider 排程信，再以新內容、新時間與（依新篩選）
     * 當下重查的名單重排，並就地更新同一筆 campaign（不另開新紀錄）。
     * 只允許狀態為 scheduled 的 campaign；否則拋 409。
     */
    public SendResult reschedule(Long campaignId, String subject, String markdown,
                                 String role, String interest, Instant scheduledAt) {
        Campaign campaign = campaignRepository.findById(campaignId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到此電子報批次"));
        if (!"scheduled".equals(campaign.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只能修改尚未寄出的排程");
        }
        // 守門：重排仍會實際寄出信件，同 send() 的理由——階段 D 的信件折疊尚未實作前，
        // 非 BASIC 的 campaign 一律拒絕重排寄送。此檢查待 foldedHtml 實作後可移除。
        if (!Campaign.TIER_BASIC.equals(campaign.getTier())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "PREMIUM 內容的信件折疊尚未實作（階段 D），目前僅能發送 BASIC 內容；此文章可在網頁上以 PREMIUM 發布");
        }

        // 1. 取消舊的 provider 排程信（把舊 email_log 標 cancelled）
        cancelProviderScheduled(campaignId);

        // 2. 以新篩選當下重查名單、渲染新內文並重排
        List<String> recipients = recipientService.recipients(role, interest);
        String bodyHtml = markdownRenderer.toHtml(markdown);
        int[] rc = scheduleAll(campaignId, subject, bodyHtml, recipients, scheduledAt);

        // 3. 就地更新同一筆 campaign 的內容、篩選、時間與統計
        campaign.setSubject(subject);
        campaign.setMarkdown(markdown);
        campaign.setBodyHtml(bodyHtml);
        campaign.setFilterRole(role);
        campaign.setFilterInterest(interest);
        campaign.setScheduledAt(OffsetDateTime.ofInstant(scheduledAt, ZoneOffset.UTC));
        campaign.setRecipientCount(recipients.size());
        campaign.setAcceptedCount(rc[0]);
        campaign.setFailedCount(rc[1]);
        campaign.setStatus(finalStatus(true, rc[0], rc[1]));
        campaignRepository.save(campaign);

        return new SendResult(campaignId, recipients.size(), rc[0], rc[1]);
    }

    /**
     * 對名單逐封呼叫 schedule 並寫入 email_log；回傳 [accepted, failed]。
     * 供立即發送的排程分支與 reschedule 共用。
     */
    private int[] scheduleAll(Long campaignId, String subject, String bodyHtml,
                              List<String> recipients, Instant scheduledAt) {
        int accepted = 0;
        int failed = 0;
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
        return new int[]{accepted, failed};
    }

    /**
     * 取消某 campaign 現有的 provider 排程信，並把對應 email_log 標為 cancelled；
     * 回傳 [cancelled, failed]。不改動 campaign 本身狀態（由呼叫端決定）。
     */
    private int[] cancelProviderScheduled(Long campaignId) {
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
        return new int[]{cancelled, failed};
    }

    /** 歷史列表（依建立時間降冪） */
    public List<Campaign> list() {
        return campaignRepository.findAllByOrderByCreatedAtDesc();
    }

    /** 驗證並正規化 tier：null 視為未指定 → 預設 BASIC；非 BASIC/PREMIUM 回 400 */
    private String validateTier(String tier) {
        if (tier == null) {
            return Campaign.TIER_BASIC;
        }
        if (!Campaign.TIER_BASIC.equals(tier) && !Campaign.TIER_PREMIUM.equals(tier)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tier 僅接受 BASIC 或 PREMIUM");
        }
        return tier;
    }

    /**
     * 驗證 creditCost：tier=PREMIUM 時必須 > 0（否則等同免費卻顯示為付費內容，
     * 資料庫另有 ck_campaign_premium_cost 約束，此處提前攔截以回 400 而非讓寫入以 500 失敗）。
     * tier=BASIC 時忽略呼叫端傳入值，一律正規化為 0。
     */
    private int validateCreditCost(String tier, Integer creditCost) {
        if (Campaign.TIER_PREMIUM.equals(tier)) {
            if (creditCost == null || creditCost <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PREMIUM 內容的 creditCost 必須大於 0");
            }
            return creditCost;
        }
        return 0;
    }

    /**
     * 驗證 slug：會直接組進 /r/news/{slug} 網址，只接受小寫英數與連字號；
     * 重複時回明確 400（而非讓 uq_campaign_slug 唯一索引丟出 500）。
     */
    private String validateSlug(String slug) {
        if (slug == null) {
            return null;
        }
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slug 格式錯誤，僅接受小寫英數與連字號");
        }
        if (campaignRepository.findBySlug(slug).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slug 已被使用");
        }
        return slug;
    }

    /**
     * 設了 slug 卻沒指定 publishedAt → 視為立即發布，取當下時間。
     * 理由：設了 slug 卻沒設發布時間，文章會查不到（isPublished() 靠 publishedAt 非 NULL 判斷），
     * 那是使用者最可能沒預期到的失敗。
     */
    private OffsetDateTime resolvePublishedAt(String slug, Instant publishedAt) {
        if (publishedAt != null) {
            return OffsetDateTime.ofInstant(publishedAt, ZoneOffset.UTC);
        }
        if (slug != null) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        return null;
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
