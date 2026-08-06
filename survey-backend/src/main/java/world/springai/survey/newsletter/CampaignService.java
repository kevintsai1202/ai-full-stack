package world.springai.survey.newsletter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import world.springai.survey.AdminSettingController;
import world.springai.survey.ReaderSiteLinks;
import world.springai.survey.audience.AudienceSearchService;
import world.springai.survey.audience.RecipientService;
import world.springai.survey.audience.SubscriptionLinkBuilder;
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.EmailTemplate;
import world.springai.survey.mail.MailQuotaService;
import world.springai.survey.mail.MailSender;
import world.springai.survey.promo.PromoPlacementService;
import world.springai.survey.promo.PromoRecipientTokenService;

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
    /**
     * 受限區切分器：發布 PREMIUM 前用來確認「這篇真的有受限區」。
     *
     * <p>與讀者端渲染共用<b>同一份</b>切分實作（同套件、無跨層依賴），
     * 才能保證「後台認定有 gate」與「讀者頁真的渲染 gate」是同一個判斷。</p>
     */
    private final ContentSplitter contentSplitter;
    /** 讀者網站公開連結的唯一組裝入口。 */
    private final ReaderSiteLinks readerSiteLinks;
    /** 信件版內文（含付費牆折疊）的唯一產生點，與補寄路徑共用 */
    private final MailBodyRenderer mailBodyRenderer;
    /**
     * 工商版位服務：寄送/發布前的可投放性預檢（{@code assertCommittable}）、
     * 內文定案後的對帳扣配額／歸還（{@code reconcile}）、排程取消後的整批歸還
     * （{@code releaseForCampaign}）。CampaignService 刻意無交易，接線順序見各呼叫點註解。
     */
    private final PromoPlacementService promoPlacementService;
    /** 工商轉址連結的收件人 token 簽發器：renderFor 對每一位收件人各自簽發一枚 */
    private final PromoRecipientTokenService promoTokenService;
    /**
     * 問卷標記展開器（Task 9 接線）：本類只直接呼叫 {@code assertEmbeddable}
     * 做寄送前預檢；實際展開（{@code expandForEmail}）已收斂到
     * {@link MailBodyRenderer#html}，是同一個 Spring 單例，兩處各自注入即可。
     */
    private final SurveyBlockRenderer surveyBlockRenderer;
    /**
     * 封面與標籤的驗證／落庫唯一入口：{@code updateContent}（本次擴充）與
     * publish 端點共用同一套規則，避免兩條路徑各自實作導致驗證邏輯分歧。
     */
    private final CampaignMetadataService metadataService;

    public CampaignService(MailSender mailSender,
                           RecipientService recipientService,
                           CampaignRepository campaignRepository,
                           EmailLogRepository emailLogRepository,
                           MarkdownRenderer markdownRenderer,
                           EmailTemplate emailTemplate,
                           SubscriptionLinkBuilder linkBuilder,
                           MailQuotaService mailQuotaService,
                           ContentSplitter contentSplitter,
                           ReaderSiteLinks readerSiteLinks,
                           MailBodyRenderer mailBodyRenderer,
                           PromoPlacementService promoPlacementService,
                           PromoRecipientTokenService promoTokenService,
                           SurveyBlockRenderer surveyBlockRenderer,
                           CampaignMetadataService metadataService) {
        this.contentSplitter = contentSplitter;
        this.readerSiteLinks = readerSiteLinks;
        this.mailBodyRenderer = mailBodyRenderer;
        this.mailSender = mailSender;
        this.recipientService = recipientService;
        this.campaignRepository = campaignRepository;
        this.emailLogRepository = emailLogRepository;
        this.markdownRenderer = markdownRenderer;
        this.emailTemplate = emailTemplate;
        this.linkBuilder = linkBuilder;
        this.mailQuotaService = mailQuotaService;
        this.promoPlacementService = promoPlacementService;
        this.promoTokenService = promoTokenService;
        this.surveyBlockRenderer = surveyBlockRenderer;
        this.metadataService = metadataService;
    }

    /**
     * 發送結果摘要。
     *
     * @param skippedForQuota 因保留交易信額度而未寄出的人數；> 0 時後台必須顯示原因
     */
    public record SendResult(Long campaignId, int recipientCount, int accepted, int failed,
                             int skippedForQuota) {}

    /**
     * 預覽：把 markdown 渲染並套外框（用示意退訂連結）。
     *
     * <p>若內容含付費牆，使用和讀者端相同的切分器呈現清楚的分界及受限區預覽。
     * 此視覺提示只存在於管理後台預覽，不會進入測試信或正式寄送內容。</p>
     */
    public String preview(String subject, String markdown) {
        return preview(subject, markdown, null, null, null);
    }

    /**
     * 預覽電子報與文章中繼資料；封面 URL 必須由伺服器依媒體 ID 解析後傳入。
     */
    public String preview(String subject, String markdown, String coverEmoji,
                          List<String> tags, String coverUrl) {
        ContentSplitter.Split split = contentSplitter.split(markdown);
        String body = split.hasGate()
            ? paywallPreview(split)
            : markdownRenderer.toHtml(markdown);
        // 問卷標記展開（Task 9）：預覽通道連結一律 href="#" 且附「預覽不計票」標示，
        // 不會產生 CID／RT 佔位符，故此處不需要任何後續替換。
        body = surveyBlockRenderer.expandForPreview(body);
        return emailTemplate.wrapCampaign(articlePreview(subject, body, coverEmoji, tags, coverUrl),
            linkBuilder.previewUnsubscribeLink(),
            readerSiteLinks.archive(), readerSiteLinks.login("/r/archive"),
            recipientService.subscriberCount());
    }

    /** 組合付費牆預覽，讓管理員同時看見免費區、分界與受限區內容。 */
    private String paywallPreview(ContentSplitter.Split split) {
        String freeHtml = markdownRenderer.toHtml(split.freeMarkdown());
        String gatedHtml = markdownRenderer.toHtml(split.gatedMarkdown());
        return freeHtml + """
            <div role="separator" aria-label="付費牆分界"
                 style="margin:28px 0 18px;padding:16px 18px;border:2px dashed #0f766e;\
                 border-radius:12px;background:#f0fdfa;color:#134e4a;text-align:center">
              <strong style="display:block;font-size:16px">🔒 付費牆分界</strong>
              <span style="display:block;margin-top:6px;font-size:13px">下方內容需符合權限或解鎖後才能閱讀</span>
            </div>
            <div style="padding:18px;border:1px solid #99f6e4;border-radius:12px;\
                 background:#f8fffe">
              <div style="margin-bottom:12px;color:#0f766e;font-size:12px;font-weight:700;\
                   letter-spacing:.08em">付費內容預覽</div>
            """ + gatedHtml + "</div>";
    }

    /** 寄一封測試信給指定信箱（立即、單封） */
    public String sendTest(String subject, String markdown, String to) {
        return sendTest(subject, markdown, to, null, null, null);
    }

    /**
     * 寄一封包含目前封面與 hashtag 的測試信；先做應用層驗證，避免把供應商 400
     * 誤包成無法理解的 500。
     *
     * <p>內容含 {@code <!--paywall-->} 時模擬讀者「未解鎖」視角：只寄免費區＋解鎖卡片，
     * 受限區內容絕不進入信件。與後台預覽（兩側都顯示）刻意不同——預覽是給管理員
     * 校對全文用的，測試信是驗證讀者實際收到／看到什麼用的。</p>
     */
    public String sendTest(String subject, String markdown, String to, String coverEmoji,
                           List<String> tags, String coverUrl) {
        if (subject == null || subject.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請先填寫主旨");
        }
        if (markdown == null || markdown.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請先填寫內文");
        }
        if (to == null || to.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請填寫測試信箱");
        }
        // 測試信沒有實際文章 slug，解鎖卡片的 CTA 因此導向歷史內容
        String body = articlePreview(subject, mailBodyHtml(markdown, null),
            coverEmoji, tags, coverUrl);
        // 問卷 CID 佔位符替換為 "0"（Task 9）：測試信沒有真正的 campaign，
        // c=0 對應 SurveyVoteService「campaign 不存在不落票」的既有保證，
        // 天然把測試信排除在投票統計之外，不需要額外旗標判斷寄送類型。
        body = body.replace(SurveyBlockRenderer.CID_PLACEHOLDER, "0");
        String html = renderFor(body, to.strip(), null, recipientService.subscriberCount());
        String testSubject = subject.strip().startsWith("[測試]")
            ? subject.strip()
            : "[測試] " + subject.strip();
        try {
            return mailSender.send(to.strip(), testSubject, html);
        } catch (RestClientResponseException exception) {
            log.warn("測試寄送遭郵件服務拒絕 status={}", exception.getStatusCode().value());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "郵件服務拒絕測試信，請檢查寄件設定或稍後重試");
        }
    }

    /**
     * 產生「信件版內文」，委派給 {@link MailBodyRenderer}——那是折疊的唯一決策點，
     * 補寄路徑（{@link CampaignDeliveryService}）也用同一份。
     *
     * <p>保留這個私有轉接方法只是為了讓本類的三個呼叫點讀起來短一些；
     * 任何新的寄送路徑都應該直接注入 {@link MailBodyRenderer}，不要再抄一份判斷。</p>
     */
    private String mailBodyHtml(String markdown, String slug) {
        return mailBodyRenderer.html(markdown, slug);
    }

    /**
     * 將封面、主旨與 hashtag 組成預覽／測試信共用的安全 HTML 區塊。
     */
    private String articlePreview(String subject, String bodyHtml, String coverEmoji,
                                  List<String> tags, String coverUrl) {
        StringBuilder html = new StringBuilder();
        if (coverUrl != null && !coverUrl.isBlank()) {
            html.append("<img src=\"").append(HtmlUtils.htmlEscape(coverUrl)).append("\" alt=\"\" ")
                .append("style=\"display:block;width:100%;max-width:560px;max-height:360px;")
                .append("object-fit:contain;height:auto;margin:0 auto 22px;border-radius:14px\">");
        } else if (coverEmoji != null && !coverEmoji.isBlank()) {
            html.append("<div aria-hidden=\"true\" style=\"font-size:48px;line-height:1;")
                .append("margin:0 0 18px\">")
                .append(HtmlUtils.htmlEscape(coverEmoji.strip())).append("</div>");
        }
        if (subject != null && !subject.isBlank()) {
            html.append("<h1 style=\"margin:0 0 14px;font-size:28px;line-height:1.3\">")
                .append(HtmlUtils.htmlEscape(subject.strip())).append("</h1>");
        }
        if (tags != null && !tags.isEmpty()) {
            html.append("<div aria-label=\"文章 Hashtag\" style=\"margin:0 0 22px\">");
            tags.stream().filter(java.util.Objects::nonNull).map(String::strip)
                .map(tag -> tag.startsWith("#") ? tag.substring(1).strip() : tag)
                .filter(tag -> !tag.isBlank()).distinct().limit(8)
                .forEach(tag -> html.append("<span style=\"display:inline-block;margin:0 7px 7px 0;")
                    .append("padding:4px 10px;border-radius:999px;background:#e4f5f1;")
                    .append("color:#08665c;font-size:13px;font-weight:700\">#")
                    .append(HtmlUtils.htmlEscape(tag)).append("</span>"));
            html.append("</div>");
        }
        return html.append(bodyHtml == null ? "" : bodyHtml).toString();
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
        return send(subject, markdown, role, interest, mode, scheduledAt,
            tier, creditCost, slug, publishedAt, null, null);
    }

    /**
     * 發送電子報並支援動態／保存分眾；舊簽名委派到此方法以維持 API 與測試相容。
     */
    public SendResult send(String subject, String markdown, String role, String interest,
                           String mode, Instant scheduledAt,
                           String tier, Integer creditCost, String slug, Instant publishedAt,
                           AudienceSearchService.Filters audienceFilters, Long savedSegmentId) {
        // 驗證並正規化發布欄位，須在建立 campaign 前完成，否則會讓 DB 約束以 500 的形式失敗
        String normalizedTier = validateTier(tier);
        int normalizedCreditCost = validateCreditCost(normalizedTier, creditCost);
        validatePaywallTier(markdown, normalizedTier);
        String normalizedSlug = validateSlug(slug);
        // slug 留空時自動產生：每一封寄出的電子報都要出現在 /r/archive（產品決定，
        // 2026-07-27）。在此之前 slug 留空＝只寄不上架，寄過的內容讀者事後找不到——
        // 生產已累積 3 封這樣的「孤兒電子報」。只有 send 這條路徑自動產生；
        // 「只發布不寄送」（publish）維持必填，因為那條路徑的唯一目的就是建立文章，
        // slug 是文章的門牌，該由操作者自己決定。
        if (normalizedSlug == null) {
            normalizedSlug = generateSlug();
        }
        // 自動產生之後 normalizedSlug 恆非 null，舊守門「publishedAt 有值卻沒 slug → 400」
        // 已無可達路徑，故移除；publishedAt 有值時沿用呼叫端指定的時間發布。
        OffsetDateTime normalizedPublishedAt = resolvePublishedAt(normalizedSlug, publishedAt);

        // 取得收件人清單
        List<String> recipients = recipients(
            role, interest, audienceFilters, savedSegmentId);

        // 保留交易信額度（spec §6）：群發不得吃掉登入信與確認信的可用量，
        // 否則讀者收不到 magic link 就整個登不進讀者端。
        QuotaLimited limited = applyMarketingQuota(recipients, "群發");
        recipients = limited.recipients();
        int skippedForQuota = limited.skippedForQuota();

        // 渲染信件版 HTML 內文：有付費牆標記時只取免費區＋解鎖卡片（見 mailBodyHtml）。
        // 存進 campaign.body_html 的也是這一份——那個欄位的語意就是「信件版內文」，
        // 存全文等於在資料庫裡留一份現成的外洩來源。網頁端渲染讀的是 markdown 原文
        // 再經 ContentSplitter 切分，與 body_html 無關，所以受限區不會因此消失。
        String bodyHtml = mailBodyHtml(markdown, normalizedSlug);
        long subscriberCount = recipientService.subscriberCount();
        boolean scheduled = "schedule".equals(mode);

        // 工商版位可投放性預檢：必須在建立 Campaign 列之前完成，失敗時資料庫不留殘留列
        // （CampaignService 無交易，一旦 Campaign 列已寫入就無法回滾，唯一的「安全失敗」
        // 時機只剩「什麼都還沒發生」的這一刻）。
        promoPlacementService.assertCommittable(markdown);

        // 問卷卡可嵌入性預檢（Task 9）：與工商預檢同一時機、同一理由——必須在建立
        // Campaign 列之前完成，失敗時資料庫不留殘留列。IllegalArgumentException
        // 不在 ApiExceptionHandler 的全域映射範圍內（刻意窄範圍，理由見該檔案），
        // 故在此就地轉譯為 400，比照 sendTest() 對 RestClientResponseException
        // 轉譯供應商例外的既有慣例，讓後台看得懂原因而非收到裸 500。
        try {
            surveyBlockRenderer.assertEmbeddable(markdown);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        // 建立 campaign 紀錄並預存（之後更新統計）
        Campaign campaign = new Campaign(subject, markdown, bodyHtml, role, interest, mode,
            scheduledAt == null ? null : OffsetDateTime.ofInstant(scheduledAt, ZoneOffset.UTC),
            recipients.size(), scheduled ? "scheduled" : "sending");
        // 設定發布相關欄位（本次擴充：tier / creditCost / slug / publishedAt）
        campaign.setTier(normalizedTier);
        campaign.setCreditCost(normalizedCreditCost);
        campaign.setSlug(normalizedSlug);
        campaign.setPublishedAt(normalizedPublishedAt);
        campaign.setFilterJson(audienceFilters);
        campaign.setSavedSegmentId(savedSegmentId);
        campaign = campaignRepository.save(campaign);
        Long campaignId = campaign.getId();

        // 問卷 CID 佔位符替換（Task 9）：campaign id 在此刻才誕生，必須在下方任何
        // renderFor（進而寄出／排程）之前完成，否則收件人會收到字面上的
        // __SURVEY_CID__ 字串。只替換本地變數 bodyHtml，campaign.body_html
        // 欄位保留替換前的內容——該欄位目前沒有任何讀取路徑（僅存查），不影響正確性。
        bodyHtml = bodyHtml.replace(SurveyBlockRenderer.CID_PLACEHOLDER, String.valueOf(campaignId));

        // 對帳定案：campaign id 在此刻誕生，必須在任何寄信副作用（sendBatch／schedule）
        // 之前完成，讓「這批版位確實屬於這期電子報」的事實與「即將寄出的信」同步——
        // 對帳失敗時整批寄送中止，不會出現「信寄出了但版位沒扣配額」的不一致。
        promoPlacementService.reconcile(campaignId, markdown);

        int accepted = 0;
        int failed = 0;

        if (scheduled) {
            // 排程模式：每封個別呼叫 schedule API（邏輯抽到 scheduleAll 供 reschedule 共用）
            int[] rc = scheduleAll(campaignId, subject, bodyHtml, recipients, scheduledAt,
                normalizedSlug, subscriberCount);
            accepted = rc[0];
            failed = rc[1];
        } else {
            // 立即模式：每批 ≤100 封呼叫 sendBatch，整批共用一個 job id
            for (int i = 0; i < recipients.size(); i += BATCH_SIZE) {
                List<String> chunk = recipients.subList(i, Math.min(i + BATCH_SIZE, recipients.size()));
                List<MailSender.Email> emails = new ArrayList<>();
                for (String email : chunk) {
                    emails.add(new MailSender.Email(email, subject,
                        renderFor(bodyHtml, email, normalizedSlug, subscriberCount)));
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
     * <p><b>為什麼需要這條路徑</b>：{@link #send} 對非 BASIC 的 tier 無條件回 400
     * （理由見該處註解），副作用是 PREMIUM 文章連 API 都沒有建立路徑，
     * 只剩手動 {@code INSERT INTO campaign}——整套點數機制沒有任何操作人員能讓它跑起來。</p>
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
        // 工商版位可投放性預檢：這條路徑雖無寄信副作用，但 campaignRepository.save()
        // 一落地文章即對外可見（STATUS_PUBLISHED），若之後才對帳而失敗（配額用罄／
        // 版位已綁其他期），會留下「讀者看得到、但工商未定案」的已發布文章，且 slug
        // 唯一索引讓重試必定 400。因此預檢必須在 Campaign 物件誕生之前，與 send() 同一道防線。
        promoPlacementService.assertCommittable(markdown);
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
        validatePaywallTier(markdown, normalizedTier);

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

        // 對帳定案：這條路徑沒有信件副作用，但內文一經發布即定案，版位歸屬與配額
        // 扣抵必須與「文章已公開」同步；campaign id 在 save() 之後才存在，故對帳只能在此刻進行。
        promoPlacementService.reconcile(campaign.getId(), markdown);

        log.info("只發布不寄送：campaignId={} slug={} tier={} creditCost={}",
            campaign.getId(), normalizedSlug, normalizedTier, normalizedCreditCost);
        return new PublishResult(campaign.getId(), normalizedSlug, normalizedTier,
            normalizedCreditCost, normalizedPublishedAt, articleUrl(normalizedSlug));
    }

    /**
     * 下架結果。
     *
     * @param campaignId 被下架的批次 id
     * @param slug       被下架的文章 slug（供後台訊息顯示是哪一篇）
     */
    public record UnpublishResult(Long campaignId, String slug) {}

    /**
     * 下架（撤回發布）：把 {@code published_at} 設回 NULL，讓文章從 {@code /r/archive}
     * 與 {@code /r/news/{slug}} 消失（{@code isPublished()} 立刻為 false）。
     *
     * <p><b>為什麼需要這條路徑</b>：{@link #publish} 之後沒有任何修改、改價或下架的手段——
     * {@link #reschedule} 因 {@code status != 'scheduled'} 回 409、{@link #cancelSchedule}
     * 是 no-op、{@link #send} 只能建新列、slug 有 UNIQUE 所以連「用同一個 slug 重發一次」
     * 都會 400。唯一的修復手段是手動 {@code UPDATE campaign}，正是 publish 端點宣稱要
     * 消滅的操作模式。而傷害是立即的（錢）：把解鎖點數打成 1200（本意 12）或內文貼漏一段，
     * 空窗期內讀者會以錯價解鎖，{@code credit_txn} 留下真實且<b>不可撤銷</b>的扣點紀錄。</p>
     *
     * <p><b>刻意只做「止血」而不是完整 CRUD</b>：下架後操作者可以刪掉那筆（或用新 slug）
     * 重新發布，比在這裡開一條「可改任意欄位」的路徑安全得多——那條路徑會讓
     * 「已解鎖的讀者付的價格」與「文章現在的價格」永久對不起來。</p>
     *
     * <p><b>只允許 {@code status='published'}</b>：其他狀態的列是寄送批次
     * （{@code sent}／{@code scheduled}／{@code failed}…），下架它們等於用一條
     * 「撤回網頁發布」的端點去改寄送紀錄的語意，回 409 並說明理由。</p>
     *
     * <p><b>只允許 {@code email_log} 為空的列</b>：有寄送記錄代表這篇已經寄進讀者信箱，
     * 信裡的連結指向 {@code /r/news/{slug}}；下架會讓已收到信的讀者點到 404。
     * 對他們來說那是「站方寄了一封連結壞掉的信」，同樣回 409。</p>
     *
     * <p><b>不刪列、不動 {@code article_access}、不動 {@code credit_txn}</b>：
     * 已經付點解鎖的讀者，他們的授權紀錄與帳本必須完整保留。理由有兩層——
     * ① 核心不變式「{@code reader.credits} 恆等於 {@code credit_txn} 總和」要求帳本只增不改，
     * 刪掉扣點紀錄會讓餘額與帳本永久對不上；② {@code article_access} 是「這個人已經買過這篇」
     * 的憑證，{@link #republish 重新上架}後仍應有效，否則讀者會被要求為同一篇文章付第二次
     * （{@code AccessDecisionService.decide} 查 {@code article_access} 得到
     * {@code ALREADY_UNLOCKED}，該查詢只看 {@code reader_id} 與 {@code campaign_id}，
     * 與發布狀態無關，所以只要這一列不被刪，憑證自然續存）。
     * 下架只改「這篇現在對外可見嗎」這一個事實，不改任何已發生的交易。</p>
     *
     * <p><b>{@code status} 一併改成 {@link Campaign#STATUS_UNPUBLISHED}</b>：
     * 留在 {@code published} 會讓後台只能靠 {@code publishedAt} 是否為 null 反推，
     * pill 也繼續顯示 {@code published}；而且沒有那個狀態值就沒有
     * {@link #republish} 的守門依據。{@code status} 在 V4 是純 TEXT，無需 migration。</p>
     *
     * @return 下架結果；找不到列回 404，狀態不符或已寄過信回 409
     */
    public UnpublishResult unpublish(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到此批次"));
        if (!Campaign.STATUS_PUBLISHED.equals(campaign.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "只能下架狀態為 " + Campaign.STATUS_PUBLISHED + " 的文章；此批次狀態為 "
                    + campaign.getStatus() + "（寄送批次請用取消排程，不要用下架改寄送紀錄的語意）");
        }
        if (emailLogRepository.countByCampaignId(campaignId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "此批次已有寄送記錄，不可下架：信件裡的連結指向 /r/news/{slug}，"
                    + "下架會讓已收到信的讀者點到 404");
        }
        String slug = campaign.getSlug();

        // 只寫 published_at 與 status 兩欄的條件式 UPDATE（不用 save(entity) 整列寫回，
        // 理由見 CampaignRepository.markUnpublished 的註解）。回傳 0 代表狀態在讀取後被改掉。
        int updated = campaignRepository.markUnpublished(
            campaignId, Campaign.STATUS_PUBLISHED, Campaign.STATUS_UNPUBLISHED);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "下架失敗：此批次的狀態在處理期間已被變更，請重新載入後再試");
        }

        log.info("下架文章：campaignId={} slug={}（published_at 設為 NULL、status 改為 {}，"
                + "未動 article_access 與 credit_txn）",
            campaignId, slug, Campaign.STATUS_UNPUBLISHED);
        return new UnpublishResult(campaignId, slug);
    }

    /**
     * 重新上架結果。
     *
     * @param campaignId  被重新上架的批次 id
     * @param slug        文章 slug
     * @param publishedAt 新的發布時間（一律為當下，理由見 {@link #republish}）
     * @param url         文章公開網址，讓管理者按完按鈕直接點開驗證
     */
    public record RepublishResult(Long campaignId, String slug, OffsetDateTime publishedAt, String url) {}

    /**
     * 重新上架（撤回下架）：把 {@code published_at} 設回當下、{@code status} 改回
     * {@link Campaign#STATUS_PUBLISHED}，文章重新出現在 {@code /r/archive} 與
     * {@code /r/news/{slug}}。
     *
     * <p><b>為什麼需要這條路徑</b>：{@link #unpublish} 原本是單向門。{@code slug} 有
     * UNIQUE 約束、被下架的那一列仍佔著它，所以「用同一個 slug 重發一次」必定 400
     * （{@link #validateSlug} 的「slug 已被使用」）。於是下架之後只剩兩個選擇：
     * 改用新 slug（舊連結全部失效，而 slug 就是對外網址），或手動
     * {@code UPDATE campaign}——後者正是下架端點宣稱要消滅的操作模式。
     * 下架的正當用途本來就是「先止血、改完再放回去」，缺了放回去的那一半，
     * 操作者會傾向不敢下架。</p>
     *
     * <p><b>{@code published_at} 用「當下」而非沿用原本的值</b>，理由有三，
     * 且刻意接受「archive 的排序會變動」這個代價：
     * ① 這個欄位在程式裡的唯一語意是「從什麼時候起對外可見」——
     *    下架期間它<b>確實不可見</b>，填回舊時間等於宣稱從未中斷過；
     * ② {@code /r/archive} 以它排序、文章頁以它顯示日期，沿用舊值會讓文章
     *    悄悄插回列表深處，已經滑過那個日期的讀者永遠不會發現它回來了——
     *    而會被下架又上架的文章，正是內容或價格被改過、最需要被看見的那些；
     * ③ 「保留原始日期」的需求可以靠不下架來達成（下架是止血手段，不是編輯流程），
     *    但「讓讀者知道它回來了」沒有別的達成方式。
     * 沒有任何授權判斷依賴 {@code published_at} 的<b>具體值</b>
     * （{@code AccessDecisionService} 只看 {@code isPublished()}，即是否為 NULL），
     * 所以改時間戳不影響任何人的解鎖狀態或帳務。</p>
     *
     * <p><b>守門與 {@link #unpublish} 對稱</b>：只接受「目前對外不可見」的列。
     * 判斷寫成兩種形狀的聯集，因為在本次改動之前下架只清 {@code published_at}
     * 而<b>沒有</b>改 {@code status}，那時被下架的列長成
     * {@code status='published'} + {@code published_at IS NULL}。若只認
     * {@code status='unpublished'}，那些列會永久卡住、只能手動 SQL 救——
     * 正是這條端點要消滅的東西。兩種形狀都以「{@code published_at IS NULL}」
     * 為共同前提，所以已經在線上可見的文章不可能誤入。</p>
     *
     * <p><b>不檢查 {@code email_log}</b>：{@link #unpublish} 已拒絕寄過信的列，
     * 所以能走到這裡的列不可能有寄送記錄；再檢查一次會是永遠跑不到的死碼，
     * 讓人誤以為多了一層保護。而且方向上也不需要——寄過信的顧慮是
     * 「已收到信的讀者點到 404」，重新上架正是在解決那件事。</p>
     *
     * <p><b>不刪列、不動 {@code article_access}、不動 {@code credit_txn}、也不碰點數</b>：
     * 與 {@link #unpublish} 完全一致。下架期間已解鎖者的憑證原封不動保留，
     * 重新上架後他們<b>不需要、也不會</b>被要求再付一次
     * （{@code AccessDecisionService} 查到 {@code article_access} 即回
     * {@code ALREADY_UNLOCKED} → FULL）。本方法只寫 campaign 一列的兩個欄位，
     * 核心不變式「{@code reader.credits} 恆等於 {@code credit_txn} 總和」不受影響。</p>
     *
     * <p><b>受限內容不會因此外洩</b>：重新上架只讓文章重新可被開啟，
     * 受限區是否進入 HTTP 回應仍由 {@code ReaderPageController} 依
     * {@code AccessDecisionService} 的判定逐次決定（tier／VIP／已解鎖／餘額），
     * 與發布狀態是兩件獨立的事。</p>
     *
     * <p>單一 UPDATE 敘述本身即為原子操作，故不需要 {@code @Transactional}。</p>
     *
     * @return 重新上架結果；找不到列回 404，狀態不符或已可見回 409
     */
    public RepublishResult republish(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到此批次"));

        String status = campaign.getStatus();
        boolean unpublishedRow = Campaign.STATUS_UNPUBLISHED.equals(status);
        // 本次改動之前被下架的列：status 還留在 published，只有 published_at 被清空
        boolean legacyUnpublishedRow = Campaign.STATUS_PUBLISHED.equals(status)
            && campaign.getPublishedAt() == null;
        if (!unpublishedRow && !legacyUnpublishedRow) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "只能重新上架已下架的文章；此批次狀態為 " + status
                    + (campaign.isPublished() ? "、且目前已對外可見" : "")
                    + "（寄送批次沒有「網頁發布」這件事可以復原）");
        }
        // slug 是 /r/news/{slug} 的唯一入口。沒有它，重新上架後 archive 查詢
        // （findBySlugIsNotNull...）也撈不到這一列，後台卻會顯示「已重新上架」——
        // 一次完全靜默的空操作。這種列只可能來自手動 SQL，仍要明確拒絕。
        if (campaign.getSlug() == null || campaign.getSlug().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "此批次沒有 slug，重新上架後讀者仍然打不開（/r/news/{slug} 是唯一入口）");
        }

        // 截斷到微秒，因為 PostgreSQL 的 TIMESTAMPTZ 只存到微秒，而 JDK 的
        // OffsetDateTime.now() 會帶到奈秒——且 PostgreSQL 是「四捨五入」而非截斷
        // （實測 ...558463500ns 進資料庫後變成 ...558464µs）。不截斷的話，本方法
        // 回傳給後台的 publishedAt 與資料庫實存值最多可以差半個微秒：後台畫面顯示的
        // 發布時間與事實不同源，而 spec 要求顯示值與實際寫入值同源。截斷之後兩者
        // 逐位元相同，測試也可以直接比精確值而不需要容差。
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        // 只寫 published_at 與 status 兩欄的條件式 UPDATE；expectedStatus 用剛讀到的值，
        // WHERE 的 published_at is null 才是真正的併發防線（理由見 markRepublished）。
        int updated = campaignRepository.markRepublished(
            campaignId, status, Campaign.STATUS_PUBLISHED, now);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "重新上架失敗：此批次的狀態在處理期間已被變更（可能已被另一個請求上架），"
                    + "請重新載入後再試");
        }

        log.info("重新上架文章：campaignId={} slug={} publishedAt={}（未動 article_access 與 credit_txn，"
                + "已解鎖者無需再付）", campaignId, campaign.getSlug(), now);
        return new RepublishResult(campaignId, campaign.getSlug(), now, articleUrl(campaign.getSlug()));
    }

    /**
     * 更新已發布文章的內容欄位。
     *
     * <p><b>刻意不做三件事</b>：不重寄信（不碰 {@code email_log} 與排程）、
     * 不改 {@code bodyHtml}（那是寄出信件的歷史快照，改它等於竄改「當初寄了什麼」）、
     * 不動解鎖與扣點。讀者站即時渲染 markdown，因此更新完成即生效。</p>
     *
     * <p><b>驗證必須先於落庫</b>：{@code metadataService.validate} 要在
     * {@code campaignRepository.updateContentFields} 之前執行——若順序寫反，封面驗證
     * 失敗時就會留下「標題已改、封面沒改」的部分更新（見 CampaignUpdateContentTest
     * 的呼叫順序測試）。</p>
     *
     * <p><b>絕不可改用 {@code campaignRepository.save(campaign)}</b>：本方法與
     * {@code metadataService.update()} 在同一個交易內，而後者是用 {@code JdbcTemplate}
     * 直接下原生 SQL 更新封面欄位——不觸發 Hibernate flush、也不讓一級快取失效。
     * 用 {@code save} 會在提交時以載入當下的舊快照整列寫回，把剛寫好的封面
     * <b>靜默還原</b>（API 仍回 updated: true）。細節見
     * {@link CampaignRepository#updateContentFields} 的說明。</p>
     *
     * @param campaignId   要更新的文章 id；找不到時回 404
     * @param subject      新主旨；空白一律 400（DB 為 NOT NULL，且空字串等同靜默清空已發布文章）
     * @param markdown     新內文（markdown 原文，網頁端即時渲染）；空白一律 400
     * @param coverEmoji   新封面 Emoji；與 {@code coverMediaId} 二擇一，交給 metadataService 驗證
     * @param coverMediaId 新封面圖片媒體 id
     * @param tags         新標籤清單，整批覆寫（{@code null} 或空清單＝清空該文所有標籤，
     *                     沒有「維持原樣」語意）。因此呼叫端必須送出完整清單——後台
     *                     {@code GET /api/admin/campaigns} 回應已含 {@code tags} 供編輯畫面回填
     * @param now          更新時間，由呼叫端注入（不直接讀取系統時鐘，利於測試）
     */
    @Transactional
    public void updateContent(long campaignId, String subject, String markdown,
                              String coverEmoji, Long coverMediaId, List<String> tags,
                              OffsetDateTime now) {
        // 主旨與內文是 TEXT NOT NULL：null 會在 flush 時炸成 500（應為 400），
        // 空字串則會通過 NOT NULL 而把一篇已發布文章靜默清空。兩者都必須在寫入前擋掉。
        // 前端雖已擋，但這是可被直接呼叫的 admin API，防線不能只留在瀏覽器裡。
        if (!StringUtils.hasText(subject)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "主旨不得空白");
        }
        if (!StringUtils.hasText(markdown)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "內文不得空白");
        }

        // 存在性檢查刻意用 existsById 而非 findById：這條路徑一個實體欄位都不需要，
        // 而載入實體會讓它被 Hibernate 管理——那正是整列寫回缺陷的起點。
        if (!campaignRepository.existsById(campaignId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "campaign not found");
        }

        // 封面與標籤走既有服務，確保與 publish 路徑的驗證規則一致；
        // 必須在任何欄位寫入之前完成，驗證失敗時不得留下部分更新
        metadataService.validate(coverEmoji, tags, coverMediaId);

        // 只寫三欄的條件式 UPDATE（不是 save 整列寫回，理由見 javadoc）。
        // 受影響筆數為 0 表示該列在上面的存在性檢查之後被刪除——正確性來自受影響筆數，
        // 不是來自先前的檢查（作法比照 markUnpublished）。
        if (campaignRepository.updateContentFields(campaignId, subject, markdown, now) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "campaign not found");
        }

        metadataService.update(campaignId, coverEmoji, tags, coverMediaId);
    }

    /**
     * 組文章公開網址。
     *
     * <p>設定值可能帶結尾斜線（環境變數由人填），去掉後再串接，
     * 避免產生 {@code https://host//r/news/x} 這種在某些反向代理下會 404 的網址。</p>
     */
    private String articleUrl(String slug) {
        return readerSiteLinks.article(slug);
    }

    /** 取消某 campaign 的所有排程信 */
    public Map<String, Integer> cancelSchedule(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到此電子報批次"));
        requireModifiableSchedule(campaign, OffsetDateTime.now(ZoneOffset.UTC));

        int[] rc = cancelProviderScheduled(campaignId);
        int cancelled = rc[0];
        int failed = rc[1];
        // 僅在確實有排程信被取消時才把 campaign 標為 cancelled；
        // 對「已立即寄出」或無排程信的 campaign 呼叫取消則為 no-op，不誤改其狀態
        if (cancelled > 0) {
            campaign.setStatus(Campaign.STATUS_CANCELLED);
            campaignRepository.save(campaign);
        }
        // 版位歸還：只在「全數取消成功」（cancelled>0 且 failed==0）時才歸還配額，
        // 與上面的狀態判斷刻意不同條件——部分取消失敗（failed>0）代表那幾封信仍會
        // 實際寄出，其中含工商內容，等同已投放，spec §6.5「已實際寄出的批次不歸還」
        // 之意；若無條件在 cancelled>0 就歸還，會讓已送達的工商版位配額被誤放回去，
        // 造成同一次投放被重複計入下一批的可用額度。
        if (cancelled > 0 && failed == 0) {
            promoPlacementService.releaseForCampaign(campaignId);
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
        return reschedule(
            campaignId, subject, markdown, role, interest, scheduledAt, null, null);
    }

    /** 修改排程並可切換為動態／保存分眾。 */
    public SendResult reschedule(Long campaignId, String subject, String markdown,
                                 String role, String interest, Instant scheduledAt,
                                 AudienceSearchService.Filters audienceFilters,
                                 Long savedSegmentId) {
        Campaign campaign = campaignRepository.findById(campaignId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到此電子報批次"));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        requireModifiableSchedule(campaign, now);
        if (scheduledAt == null || !scheduledAt.isAfter(now.toInstant())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新的排程時間需為未來");
        }
        // 重排會改寫原 campaign 的 markdown，因此也必須重驗付費牆與 tier 的配對；
        // 否則建立時的守門可被「排程後修改內容」繞過。
        validatePaywallTier(markdown, campaign.getTier());

        // 問卷卡可嵌入性預檢（I1 修正）：與 send() 同一時機、同一理由——重排讀進來的
        // 是全新 markdown，若其中的問卷標記已失效（問卷被下架或信中一鍵題被清除），
        // 必須在任何 provider 呼叫（對帳、取消舊排程、重新排程寄送）之前擋下，
        // 否則會靜默用「未展開卡片」的內容重寄整批，而不是像 send() 一樣直接拒絕。
        try {
            surveyBlockRenderer.assertEmbeddable(markdown);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        // 對帳：重排是「新內文對消失版位歸還配額」的唯一入口（原內文已綁定的版位，
        // 若新內文不再引用即視為未刊登並釋放）。必須在任何 provider 呼叫（取消舊排程、
        // 重新排程寄送）之前完成——對帳失敗時（如新增版位的提案配額已用罄）要整個中止，
        // 不能已經取消了舊排程卻沒有新內容可以替補。
        promoPlacementService.reconcile(campaignId, markdown);

        // 1. 取消舊的 provider 排程信（把舊 email_log 標 cancelled）
        cancelProviderScheduled(campaignId);

        // 2. 以新篩選當下重查名單、渲染新內文並重排
        List<String> recipients = recipients(
            role, interest, audienceFilters, savedSegmentId);

        // 保留交易信額度（spec §6）：重排仍會實際寄出信件，同 send() 的理由。
        // 與 send() 共用同一份判斷（applyMarketingQuota），避免兩條路徑各改一邊。
        QuotaLimited limited = applyMarketingQuota(recipients, "重排");
        recipients = limited.recipients();
        int skippedForQuota = limited.skippedForQuota();

        // 與 send() 共用同一份折疊判斷：重排看起來只是「改時間」，實際上會用新內容
        // 重寄整批，只修 send() 而漏掉這裡就是一條繞過折疊的後門。
        String bodyHtml = mailBodyHtml(markdown, campaign.getSlug());
        // 問卷 CID 佔位符替換（Task 9）：重排沿用同一個既有 campaignId（不像 send()
        // 需要等待新建才知道 id），mailBodyHtml 內部一律經 MailBodyRenderer 展開問卷卡，
        // 這裡不補替換會讓重寄出去的信件內文原樣殘留 __SURVEY_CID__ 字面字串。
        bodyHtml = bodyHtml.replace(SurveyBlockRenderer.CID_PLACEHOLDER, String.valueOf(campaignId));
        int[] rc = scheduleAll(campaignId, subject, bodyHtml, recipients, scheduledAt,
            campaign.getSlug(), recipientService.subscriberCount());

        // 3. 就地更新同一筆 campaign 的內容、篩選、時間與統計
        campaign.setSubject(subject);
        campaign.setMarkdown(markdown);
        campaign.setBodyHtml(bodyHtml);
        campaign.setFilterRole(role);
        campaign.setFilterInterest(interest);
        campaign.setFilterJson(audienceFilters);
        campaign.setSavedSegmentId(savedSegmentId);
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
                              List<String> recipients, Instant scheduledAt, String slug,
                              long subscriberCount) {
        int accepted = 0;
        int failed = 0;
        for (String email : recipients) {
            try {
                String id = mailSender.schedule(
                    new MailSender.Email(email, subject,
                        renderFor(bodyHtml, email, slug, subscriberCount)), scheduledAt);
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
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int reconciled = campaignRepository.markElapsedSchedules(
            Campaign.STATUS_SCHEDULED, Campaign.STATUS_SENT, now);
        if (reconciled > 0) {
            log.info("整理已到期電子報排程：{} 個批次由 scheduled 更新為 sent", reconciled);
        }
        return campaignRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * 排程操作守門：只有狀態仍為 scheduled 且排程時間晚於後端目前時間才可操作。
     *
     * <p>這道檢查必須留在後端；即使 Admin 頁面已隱藏按鈕，使用者仍可能停留在
     * 舊頁面，或直接呼叫 API。排程時間剛好跨過時，以後端收到請求的時間為準。</p>
     */
    private void requireModifiableSchedule(Campaign campaign, OffsetDateTime now) {
        if (!campaign.canModifyScheduleAt(now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "此排程已到寄送時間或不再是待寄狀態，無法修改或取消");
        }
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
     * 驗證付費牆與內容分級必須雙向一致。
     *
     * <p>{@code <!--paywall-->} 現在明確代表「需點數解鎖」，所以 BASIC 不得帶標記；
     * PREMIUM 也不得缺標記，否則會出現標示付費但全文免費的矛盾資料。所有建立與
     * 重排路徑共用此方法，避免 UI、API 或排程修改留下繞過入口。</p>
     */
    private void validatePaywallTier(String markdown, String tier) {
        boolean hasPaywall = contentSplitter.split(markdown).hasGate();
        if (hasPaywall && !Campaign.TIER_PREMIUM.equals(tier)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "含 " + ContentSplitter.PAYWALL_MARKER
                    + " 的付費牆內容必須使用 PREMIUM 分級與正數解鎖點數");
        }
        if (!hasPaywall && Campaign.TIER_PREMIUM.equals(tier)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "PREMIUM 內容必須含一行 " + ContentSplitter.PAYWALL_MARKER
                    + " 標記，否則整篇都是免費區（標記需完全小寫）");
        }
    }

    /**
     * {@code campaign.credit_cost} 的上限：與 {@link AdminSettingController#CREDIT_MAX}
     * 共用同一個常數，理由見該常數的 javadoc——單篇文章的解鎖成本與後台的
     * {@code credit.premium_cost} 是同一類風險（打錯位數把內容鎖死），必須維持
     * 同一量級，各自維護一份遲早只會改到一邊。
     */
    private static final int CREDIT_COST_MAX = AdminSettingController.CREDIT_MAX;

    /**
     * 驗證 creditCost：tier=PREMIUM 時必須落在 [1, {@link #CREDIT_COST_MAX}] 區間
     * （資料庫另有 ck_campaign_premium_cost 約束下限，此處提前攔截以回 400 而非讓寫入以
     * 500 失敗；上限則是 B5 上限漏未涵蓋的同一類輸入路徑——{@code CreditPolicy.costOf()}
     * 優先採用這個欄位，B5 對 {@code app_setting} 的上限完全擋不到它，且發布後沒有任何
     * UI／API 可以改價，打錯位數只能靠手動 UPDATE 補救）。
     * tier=BASIC 時忽略呼叫端傳入值，一律正規化為 0。
     */
    private int validateCreditCost(String tier, Integer creditCost) {
        if (Campaign.TIER_PREMIUM.equals(tier)) {
            if (creditCost == null || creditCost < 1 || creditCost > CREDIT_COST_MAX) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "PREMIUM 內容的 creditCost 必須在 1 到 " + CREDIT_COST_MAX + " 之間，收到："
                        + creditCost);
            }
            return creditCost;
        }
        return 0;
    }

    /**
     * 驗證 slug：會直接組進 /r/news/{slug} 網址，只接受小寫英數與連字號；
     * 重複時回明確 400（而非讓 uq_campaign_slug 唯一索引丟出 500）。
     */
    /**
     * 為 slug 留空的寄送自動產生一個：{@code nl-YYYYMMDD-xxxx}。
     *
     * <p>格式取捨：中文主旨無法可靠轉成可讀的英文 slug，硬轉的結果比亂碼更難認；
     * 「nl-日期-隨機碼」讓網址看得出是哪一天的電子報，隨機尾碼負責唯一性。
     * 尾碼字元集只用小寫英數（符合 {@link #SLUG_PATTERN}），撞號時重試——
     * 30 秒內寄兩封才可能同日期，31^4 ≈ 92 萬種尾碼，實務上一次就過。</p>
     *
     * <p>上限 20 次重試後拋例外而非無窮迴圈：撞 20 次代表隨機源壞了或資料異常，
     * 靜默重試到天荒地老只會把問題藏起來。</p>
     */
    /** 沒有新式條件時沿用舊查詢入口，維持既有寄送與測試相容。 */
    private List<String> recipients(
            String role,
            String interest,
            AudienceSearchService.Filters audienceFilters,
            Long savedSegmentId) {
        return audienceFilters == null && savedSegmentId == null
            ? recipientService.recipients(role, interest)
            : recipientService.recipients(role, interest, audienceFilters, savedSegmentId);
    }

    private String generateSlug() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        String alphabet = "abcdefghjkmnpqrstuvwxyz23456789"; // 避開 i/l/o/0/1，肉眼核對網址時不易看錯
        String datePart = OffsetDateTime.now(ZoneOffset.UTC).toLocalDate()
            .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder suffix = new StringBuilder(4);
            for (int i = 0; i < 4; i++) {
                suffix.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            String candidate = "nl-" + datePart + "-" + suffix;
            if (campaignRepository.findBySlug(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("自動產生 slug 連續 20 次撞號，請檢查資料或改為手動指定");
    }

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

    /**
     * 把內文 HTML 套上個人化退訂連結、文章直達按鈕與登入入口。
     * 測試信沒有實際文章 slug，因此改導向歷史內容。
     */
    private String renderFor(String bodyHtml, String email, String slug, long subscriberCount) {
        // 工商轉址連結的收件人 token：每收件人一枚，佔位符來源唯一（Task 4）。
        // 測試信／預覽寄送也會經過這裡替換出有效 token，但對應版位仍是 DRAFT
        // （只有 reconcile 定案才會轉 COMMITTED），因此不會計入任何投放統計——
        // spec §5「測試信不入統計」由此免費達成，不需要額外旗標判斷寄送類型。
        bodyHtml = bodyHtml.replace(PromoRecipientTokenService.PLACEHOLDER,
            promoTokenService.issue(email));
        String path = slug == null ? "/r/archive" : "/r/news/" + slug;
        String articleLink = slug == null ? readerSiteLinks.archive() : readerSiteLinks.article(slug);
        return emailTemplate.wrapCampaign(bodyHtml, linkBuilder.unsubscribeLink(email),
            articleLink, readerSiteLinks.login(path), subscriberCount);
    }

    /** 依是否排程與成敗決定最終狀態字串 */
    private String finalStatus(boolean scheduled, int accepted, int failed) {
        if (accepted == 0 && failed > 0) {
            return "failed";
        }
        return scheduled ? "scheduled" : "sent";
    }
}
