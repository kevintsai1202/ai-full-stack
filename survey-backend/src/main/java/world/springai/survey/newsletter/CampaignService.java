package world.springai.survey.newsletter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import world.springai.survey.audience.RecipientService;
import world.springai.survey.audience.SubscriptionLinkBuilder;
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.EmailTemplate;
import world.springai.survey.mail.MailQuotaService;
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
    private final SubscriptionLinkBuilder linkBuilder; // 退訂連結組裝的唯一擁有者
    private final MailQuotaService mailQuotaService;
    /** 對外網址前綴，用於回傳文章公開網址；全專案唯一設定來源 app.public-base-url */
    private final String publicBaseUrl;

    public CampaignService(MailSender mailSender,
                           RecipientService recipientService,
                           CampaignRepository campaignRepository,
                           EmailLogRepository emailLogRepository,
                           MarkdownRenderer markdownRenderer,
                           EmailTemplate emailTemplate,
                           SubscriptionLinkBuilder linkBuilder,
                           MailQuotaService mailQuotaService,
                           @Value("${app.public-base-url}") String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
        this.mailSender = mailSender;
        this.recipientService = recipientService;
        this.campaignRepository = campaignRepository;
        this.emailLogRepository = emailLogRepository;
        this.markdownRenderer = markdownRenderer;
        this.emailTemplate = emailTemplate;
        this.linkBuilder = linkBuilder;
        this.mailQuotaService = mailQuotaService;
    }

    /**
     * 發送結果摘要。
     *
     * @param skippedForQuota 因保留交易信額度而未寄出的人數；> 0 時後台必須顯示原因
     */
    public record SendResult(Long campaignId, int recipientCount, int accepted, int failed,
                             int skippedForQuota) {}

    /** 預覽：把 markdown 渲染並套外框（用示意退訂連結） */
    public String preview(String subject, String markdown) {
        String body = markdownRenderer.toHtml(markdown);
        return emailTemplate.wrap(body, linkBuilder.previewUnsubscribeLink());
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
        // 與「設了 slug 未設 publishedAt 自動發布」對稱的反向檢查：
        // 設了 publishedAt 卻沒設 slug 同樣是使用者最可能沒預期到的失敗——
        // publishedAt 非 NULL 就會讓 archive 查詢把這篇文章列出來，但沒有 slug
        // 就沒有 /r/news/{slug} 網址可點，讀者會點到一個永遠 404 的空連結。
        // 因此兩種矛盾組合都必須在寫入 campaign 前擋下，而非各自只顧單一方向。
        if (publishedAt != null && normalizedSlug == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "指定 publishedAt 時必須同時指定 slug，否則文章會出現在列表卻無法開啟");
        }
        OffsetDateTime normalizedPublishedAt = resolvePublishedAt(normalizedSlug, publishedAt);

        // 守門：階段 D（依 tier 產生折疊版內文 foldedHtml）完成前，PREMIUM 內容一旦寄出，
        // markdownRenderer.toHtml(markdown) 會把受限區塊與 <!--paywall--> 之後的全文一併渲染
        // 給「所有」收件人 —— 網頁端擋得再嚴，信件端都已外流付費內容。
        // 待 CampaignService 補上 foldedHtml（依 tier 分兩種內文：全文版／折疊版）後，此檢查方可移除。
        if (!Campaign.TIER_BASIC.equals(normalizedTier)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "目前僅支援 BASIC 內容寄送；PREMIUM 內容需等信件折疊功能（階段 D）實作後才能發布");
        }

        // 取得收件人清單
        List<String> recipients = recipientService.recipients(role, interest);

        // 保留交易信額度（spec §6）：群發不得吃掉登入信與確認信的可用量，
        // 否則讀者收不到 magic link 就整個登不進讀者端。
        QuotaLimited limited = applyMarketingQuota(recipients, "群發");
        recipients = limited.recipients();
        int skippedForQuota = limited.skippedForQuota();

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

        // 本批已消耗額度，讓下一次查詢重新向外部取數（理由見 MailQuotaService.invalidate）。
        // 注意：主要保證已改由 QuotaAwareMailSender 提供（寄信成功即失效，不靠呼叫端記得）；
        // 這裡保留顯式呼叫，讓「整批全部寄送失敗」等 delegate 未成功回傳的情形也重新取數。
        mailQuotaService.invalidate();

        return new SendResult(campaignId, recipients.size(), accepted, failed, skippedForQuota);
    }

    /**
     * 發布結果。
     *
     * <p>刻意不重用 {@link SendResult}：那個 record 的每一個欄位（recipientCount／
     * accepted／failed／skippedForQuota）在這條路徑上都恆為 0，回傳它只會讓後台
     * 與 API 使用者以為「這是一次寄了 0 封的群發」。這裡改為只回傳真正發生的事實，
     * 並附上文章公開網址讓管理者能立刻點開驗證。</p>
     *
     * @param url 文章公開網址（{@code {app.public-base-url}/r/news/{slug}}）
     */
    public record PublishResult(Long campaignId, String slug, String tier, int creditCost,
                                OffsetDateTime publishedAt, String url) {}

    /**
     * 只發布到網頁、<b>完全不寄送任何信件</b>。
     *
     * <p><b>為什麼需要這條路徑</b>：{@link #send} 對非 BASIC 的 tier 無條件回 400，
     * 因為階段 D 的信件折疊（依 tier 產生折疊版內文）尚未實作，PREMIUM 內容一旦寄出
     * 就會把受限區完整送進<b>所有</b>收件人的信箱。那個守門是正確的，但副作用是
     * PREMIUM 文章連 API 都沒有建立路徑，只剩手動 {@code INSERT INTO campaign}——
     * 整套點數機制沒有任何操作人員能讓它跑起來。</p>
     *
     * <p><b>為什麼 PREMIUM 在這裡可以放行</b>：不寄信就沒有「信件端外流付費內容」
     * 這個風險。網頁端的受限區由 {@code ReaderPageController} 依授權結果決定是否
     * 渲染（未授權時受限區不進入 HTTP 回應），paywall 在這條路徑上完整成立。</p>
     *
     * <p><b>與 {@link #send} 的根本差異</b>：本方法<b>不呼叫 {@code mailSender} 的任何方法，
     * 也不走 {@code applyMarketingQuota}</b>——不寄信就不該佔用（更不該吃掉）
     * 交易信的保留額度，也不需要讓 {@code MailQuotaService} 的快取失效。</p>
     *
     * <p><b>不碰點數與帳本</b>：本方法只寫入 {@code campaign} 一列，
     * 不觸及 {@code reader.credits} 或 {@code credit_txn}，故核心不變式
     * 「餘額 == 帳本總和」不受影響。</p>
     *
     * <p>單一交易只有一次 {@code save()}，無外部呼叫，故不需要 {@code @Transactional}
     * （理由與 {@link #send} 不同：那裡是「不能回滾」，這裡是「沒有東西要協調」）。</p>
     *
     * @param slug        <b>必填</b>。沒有 slug 的「純網頁文章」讀者永遠打不開
     *                    （{@code /r/news/{slug}} 是唯一入口），寫進資料庫等於消失；
     *                    {@link #send} 的 slug 可省略是因為那條路徑主要目的是寄信。
     * @param publishedAt 省略時視為立即發布（沿用 {@link #resolvePublishedAt}）
     */
    public PublishResult publish(String subject, String markdown, String tier, Integer creditCost,
                                 String slug, Instant publishedAt) {
        // 主旨與內文在 campaign 表皆為 NOT NULL；提前擋下以回 400 而非讓寫入以 500 失敗
        if (subject == null || subject.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "subject 為必填");
        }
        if (markdown == null || markdown.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "markdown 為必填");
        }
        // slug 對這條端點是必填（理由見 javadoc）；空白字串也視為未填，
        // 否則會被 validateSlug 當成格式錯誤而回一個誤導的原因
        if (slug == null || slug.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "slug 為必填：沒有 slug 的文章沒有 /r/news/{slug} 網址，讀者永遠打不開");
        }

        // 驗證與正規化重用 send() 的同一份實作（validateTier / validateCreditCost /
        // validateSlug / resolvePublishedAt），不複寫第二份——同一概念兩份實作
        // 遲早只會被改一邊，而漏改的那一邊就是漏洞。
        String normalizedTier = validateTier(tier);
        int normalizedCreditCost = validateCreditCost(normalizedTier, creditCost);
        String normalizedSlug = validateSlug(slug);
        OffsetDateTime normalizedPublishedAt = resolvePublishedAt(normalizedSlug, publishedAt);

        // bodyHtml 刻意留 null：那是「信件版內文」，這條路徑沒有信件版。
        // 存一份全文 HTML 進去只會成為階段 D 實作折疊時的現成外洩來源
        // （拿 bodyHtml 直接寄出就等於寄出受限區）；網頁端渲染讀的是 markdown
        // 再經 ContentSplitter 切分，與 bodyHtml 無關。
        Campaign campaign = new Campaign(subject, markdown, null, null, null,
            Campaign.MODE_PUBLISH, null, 0, Campaign.STATUS_PUBLISHED);
        campaign.setTier(normalizedTier);
        campaign.setCreditCost(normalizedCreditCost);
        campaign.setSlug(normalizedSlug);
        campaign.setPublishedAt(normalizedPublishedAt);
        campaign = campaignRepository.save(campaign);

        log.info("只發布不寄送：campaignId={} slug={} tier={} creditCost={}",
            campaign.getId(), normalizedSlug, normalizedTier, normalizedCreditCost);
        return new PublishResult(campaign.getId(), normalizedSlug, normalizedTier,
            normalizedCreditCost, normalizedPublishedAt, articleUrl(normalizedSlug));
    }

    /**
     * 組文章公開網址。
     *
     * <p>設定值可能帶結尾斜線（環境變數由人填），去掉後再串接，
     * 避免產生 {@code https://host//r/news/x} 這種在某些反向代理下會 404 的網址。</p>
     */
    private String articleUrl(String slug) {
        String base = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/r/news/" + slug;
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
                "目前僅支援 BASIC 內容寄送；PREMIUM 內容需等信件折疊功能（階段 D）實作後才能發布");
        }

        // 1. 取消舊的 provider 排程信（把舊 email_log 標 cancelled）
        cancelProviderScheduled(campaignId);

        // 2. 以新篩選當下重查名單、渲染新內文並重排
        List<String> recipients = recipientService.recipients(role, interest);

        // 保留交易信額度（spec §6）：重排仍會實際寄出信件，同 send() 的理由。
        // 與 send() 共用同一份判斷（applyMarketingQuota），避免兩條路徑各改一邊。
        QuotaLimited limited = applyMarketingQuota(recipients, "重排");
        recipients = limited.recipients();
        int skippedForQuota = limited.skippedForQuota();

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

        // 重排同樣實際寄出信件，消耗額度後必須讓快取失效（同 send()）
        mailQuotaService.invalidate();

        return new SendResult(campaignId, recipients.size(), rc[0], rc[1], skippedForQuota);
    }

    /** 套用保留額度後的收件人清單與被略過人數 */
    private record QuotaLimited(List<String> recipients, int skippedForQuota) {}

    /**
     * 依「行銷可用額度」裁切收件人清單（spec §6）。
     *
     * <p><b>為什麼要抽成共用方法</b>：{@code send()} 與 {@code reschedule()} 都會實際寄出信件，
     * 兩邊原本是字面重複的 15 行。重複的判斷遲早只會被改一邊，而漏改的那一邊就是一條
     * 繞過保留額度的後門——reschedule 尤其危險，它看起來像「只是改時間」，實際上會重寄整批。</p>
     *
     * @param recipients 原始收件人清單
     * @param action     log 用的動作名稱（群發／重排）
     * @return 裁切後的清單與被略過人數；行銷可用量為 0 時直接拋 409
     */
    private QuotaLimited applyMarketingQuota(List<String> recipients, String action) {
        MailQuotaService.Quota quota = mailQuotaService.current();
        // 偵測失敗時的額度是「推測值」而非實際剩餘量，必須在 log 裡標明，
        // 否則事後查「為什麼只寄了這些人」會誤以為那是真實額度
        if (MailQuotaService.SOURCE_FALLBACK.equals(quota.source())) {
            log.warn("{}：本次額度為推測值（未偵測到實際額度 source=fallback），行銷可用 {} 封",
                action, quota.marketingRemaining());
        }
        if (quota.marketingRemaining() <= 0) {
            // 完全沒有行銷可用量時直接拒絕，而不是寄 0 封後回報成功——
            // 後者會讓後台顯示「已發送」而實際上沒人收到。
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "行銷可用額度為 0（剩餘 " + quota.remaining() + " 封已全數保留給登入信等交易信）。"
                    + "請等額度重置後再發送。");
        }
        if (recipients.size() <= quota.marketingRemaining()) {
            return new QuotaLimited(recipients, 0);
        }
        // 縮減批量而非全部拒絕：先寄一部分，剩下的下次再寄。
        // 縮減量必須回報，否則「已發送」會被誤解成全部寄出。
        int skipped = (int) (recipients.size() - quota.marketingRemaining());
        List<String> allowed = recipients.subList(0, (int) quota.marketingRemaining());
        log.warn("{}縮減批量：原 {} 人，因保留 {} 封交易信額度而只寄 {} 人",
            action, recipients.size(), quota.reserve(), allowed.size());
        return new QuotaLimited(allowed, skipped);
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
        return emailTemplate.wrap(bodyHtml, linkBuilder.unsubscribeLink(email));
    }

    /** 依是否排程與成敗決定最終狀態字串 */
    private String finalStatus(boolean scheduled, int accepted, int failed) {
        if (accepted == 0 && failed > 0) {
            return "failed";
        }
        return scheduled ? "scheduled" : "sent";
    }
}
