package world.springai.survey.newsletter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import world.springai.survey.AdminKeyGuard;
import world.springai.survey.audience.AudienceSearchService;
import world.springai.survey.audience.RecipientService;
import world.springai.survey.mail.MailQuotaService;
import world.springai.survey.mail.MailTemplate;

/** 寄信後台 API（全部經 AdminKeyGuard 驗 X-Admin-Key） */
@RestController
public class AdminCampaignController {

    /** HTTP Header 名稱：後台金鑰 */
    private static final String KEY_HEADER = "X-Admin-Key";

    /** 金鑰驗證元件 */
    private final AdminKeyGuard guard;
    /** 電子報服務 */
    private final CampaignService campaignService;
    /** 收件人服務 */
    private final RecipientService recipientService;
    /** 二次確認邀請信服務 */
    private final InviteService inviteService;
    /** 寄信額度偵測服務 */
    private final MailQuotaService mailQuotaService;
    /** 文章 Emoji 與 hashtag 服務 */
    private final CampaignMetadataService metadataService;

    /** Spring 正式執行時注入完整依賴。 */
    @Autowired
    public AdminCampaignController(AdminKeyGuard guard,
                                   CampaignService campaignService,
                                   RecipientService recipientService,
                                   InviteService inviteService,
                                   MailQuotaService mailQuotaService,
                                   ObjectProvider<CampaignMetadataService> metadataServiceProvider) {
        this.guard = guard;
        this.campaignService = campaignService;
        this.recipientService = recipientService;
        this.inviteService = inviteService;
        this.mailQuotaService = mailQuotaService;
        this.metadataService = metadataServiceProvider.getIfAvailable();
    }

    /** 舊單元測試相容建構式；不處理新文章中繼資料。 */
    public AdminCampaignController(AdminKeyGuard guard,
                                   CampaignService campaignService,
                                   RecipientService recipientService,
                                   InviteService inviteService,
                                   MailQuotaService mailQuotaService) {
        this.guard = guard;
        this.campaignService = campaignService;
        this.recipientService = recipientService;
        this.inviteService = inviteService;
        this.mailQuotaService = mailQuotaService;
        this.metadataService = null;
    }

    /** 預覽用請求：主旨與 markdown 內文 */
    public record PreviewRequest(String subject, String markdown) {}

    /** 測試寄送請求：主旨、markdown 內文、目標信箱 */
    public record TestRequest(String subject, String markdown, String to) {}

    /** 收件人篩選：保留舊 role/interest，並支援動態條件或保存分眾 ID。 */
    public record Filter(
            String role,
            String interest,
            AudienceSearchService.Filters audience,
            Long savedSegmentId) {

        /** 舊測試與 Java 呼叫相容建構式。 */
        public Filter(String role, String interest) {
            this(role, interest, null, null);
        }
    }

    /**
     * 發送請求：主旨、markdown 內文、篩選條件、發送模式、排程時間（ISO-8601）。
     * 以下四個為本次擴充的發布欄位（皆可省略）：
     * tier（BASIC/PREMIUM，省略視為 BASIC）、creditCost（PREMIUM 解鎖點數）、
     * slug（網頁文章網址片段）、publishedAt（發布時間 ISO-8601；省略且有 slug 時視為立即發布）。
     * vipFullInMail 與 filterLevels 屬階段 D／F 範圍，本次不開放設定。
     */
    public record SendRequest(String subject, String markdown, Filter filter, String mode, String scheduledAt,
                              String tier, Integer creditCost, String slug, String publishedAt,
                              String coverEmoji, List<String> tags) {

        /** 舊 Java 呼叫相容建構式。 */
        public SendRequest(String subject, String markdown, Filter filter, String mode,
                           String scheduledAt, String tier, Integer creditCost,
                           String slug, String publishedAt) {
            this(subject, markdown, filter, mode, scheduledAt, tier, creditCost, slug,
                publishedAt, null, null);
        }
    }

    /** 收件名單計數與樣本（前 5 筆），需提供有效金鑰 */
    @GetMapping("/api/admin/recipients")
    public Map<String, Object> recipients(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String interest) {
        guard.verify(key);
        List<String> all = recipientService.recipients(role, interest);
        return Map.of("count", all.size(), "sample", all.stream().limit(5).toList());
    }

    /** 以和實際寄送相同的動態條件／保存分眾計算人數，避免預覽與寄送名單不同。 */
    @PostMapping("/api/admin/recipients/search")
    public Map<String, Object> recipients(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @RequestBody(required = false) Filter filter) {
        guard.verify(key);
        List<String> all = filter == null
            ? recipientService.recipients(null, null)
            : recipientService.recipients(
                filter.role(), filter.interest(), filter.audience(), filter.savedSegmentId());
        return Map.of("count", all.size(), "sample", all.stream().limit(5).toList());
    }

    /** 以 markdown 預覽渲染後的 HTML 內文，需提供有效金鑰 */
    @PostMapping("/api/admin/campaign/preview")
    public Map<String, String> preview(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @RequestBody PreviewRequest req) {
        guard.verify(key);
        return Map.of("html", campaignService.preview(req.subject(), req.markdown()));
    }

    /** 寄一封測試信到指定信箱，需提供有效金鑰 */
    @PostMapping("/api/admin/campaign/test")
    public Map<String, String> test(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @RequestBody TestRequest req) {
        guard.verify(key);
        return Map.of("providerId", campaignService.sendTest(req.subject(), req.markdown(), req.to()));
    }

    /**
     * 發送電子報（立即 mode=now 或排程 mode=schedule），需提供有效金鑰。
     * 排程模式時 scheduledAt 必填且須為未來時間。
     */
    @PostMapping("/api/admin/campaign/send")
    public CampaignService.SendResult send(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @RequestBody SendRequest req) {
        guard.verify(key);
        validateMetadata(req.coverEmoji(), req.tags());
        // 從篩選條件取出 role / interest（允許 filter 為 null）
        String role = req.filter() == null ? null : req.filter().role();
        String interest = req.filter() == null ? null : req.filter().interest();

        // 排程模式驗證：scheduledAt 必填且為未來時間
        Instant scheduledAt = null;
        if ("schedule".equals(req.mode())) {
            if (req.scheduledAt() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "排程模式需要 scheduledAt");
            }
            scheduledAt = Instant.parse(req.scheduledAt());
            if (!scheduledAt.isAfter(Instant.now())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "排程時間需為未來");
            }
        }

        CampaignService.SendResult result = campaignService.send(
            req.subject(), req.markdown(), role, interest, req.mode(), scheduledAt,
            req.tier(), req.creditCost(), req.slug(), parsePublishedAt(req.publishedAt()),
            req.filter() == null ? null : req.filter().audience(),
            req.filter() == null ? null : req.filter().savedSegmentId());
        updateMetadata(result.campaignId(), req.coverEmoji(), req.tags());
        return result;
    }

    /**
     * 只發布不寄送的請求：主旨、markdown 內文、分級、解鎖點數、網址代稱、發布時間。
     *
     * <p>沒有 filter／mode／scheduledAt——這條路徑不寄信，收件人篩選與寄送模式毫無意義。
     * slug 為<b>必填</b>（與 {@link SendRequest} 不同），缺少時由 CampaignService 回 400。</p>
     */
    public record PublishRequest(String subject, String markdown, String tier, Integer creditCost,
                                 String slug, String publishedAt, String coverEmoji, List<String> tags) {

        /** 舊 Java 呼叫相容建構式。 */
        public PublishRequest(String subject, String markdown, String tier, Integer creditCost,
                              String slug, String publishedAt) {
            this(subject, markdown, tier, creditCost, slug, publishedAt, null, null);
        }
    }

    /**
     * 只把文章發布到網頁（{@code /r/news/{slug}} 與 {@code /r/archive}），<b>完全不寄信</b>，
     * 需提供有效金鑰。
     *
     * <p><b>與 {@code /api/admin/campaign/send} 的差異與存在理由</b>：send 對 PREMIUM
     * 無條件回 400（階段 D 的信件折疊未完成前必要的守門），於是 PREMIUM 文章原本
     * 只能手動寫資料庫才能上線。這條端點不寄信，因此沒有「信件端外流付費內容」的
     * 風險，PREMIUM 可以放行——這是它存在的全部目的。</p>
     *
     * <p>回應含文章公開網址，方便管理者按完按鈕直接點開驗證 paywall 是否如預期。</p>
     */
    @PostMapping("/api/admin/campaign/publish")
    public CampaignService.PublishResult publish(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @RequestBody PublishRequest req) {
        guard.verify(key);
        validateMetadata(req.coverEmoji(), req.tags());
        CampaignService.PublishResult result = campaignService.publish(
            req.subject(), req.markdown(), req.tier(), req.creditCost(),
            req.slug(), parsePublishedAt(req.publishedAt()));
        updateMetadata(result.campaignId(), req.coverEmoji(), req.tags());
        return result;
    }

    /**
     * 下架（撤回發布）指定批次：把 {@code published_at} 設回 NULL，讓文章從
     * {@code /r/archive} 與 {@code /r/news/{slug}} 消失，需提供有效金鑰。
     *
     * <p><b>為什麼需要它</b>：{@code POST /api/admin/campaign/publish} 發布後，既有的
     * reschedule（要求 {@code status='scheduled'}）／cancelSchedule（no-op）／send（只能建新列）
     * 全都碰不到已發布的文章，slug 的 UNIQUE 又讓「同 slug 重發」必定 400——唯一的
     * 修復手段是手動 {@code UPDATE campaign}，正是 publish 端點宣稱要消滅的操作模式。
     * 解鎖點數打錯（12 打成 1200）時，空窗期內每一位解鎖的讀者都留下不可撤銷的扣點紀錄。</p>
     *
     * <p><b>路徑用 {@code /publication} 而非 {@code /publish}</b>：DELETE 的受詞是那個
     * 「已發布狀態」這個資源，與 {@code DELETE /api/admin/campaigns/{id}/schedule}
     * （受詞是排程）的既有慣例一致。<b>不刪除 campaign 那一列</b>，也不動
     * {@code article_access} 與 {@code credit_txn}（理由見 {@code CampaignService.unpublish}）。</p>
     *
     * <p>限制條件（狀態非 published、或已有寄送記錄 → 409）由 service 層判斷，
     * 那裡是唯一持有 campaign 與 email_log 的地方。</p>
     */
    @DeleteMapping("/api/admin/campaigns/{id}/publication")
    public CampaignService.UnpublishResult unpublish(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @PathVariable Long id) {
        guard.verify(key);
        return campaignService.unpublish(id);
    }

    /**
     * 重新上架（撤回下架）指定批次：把 {@code published_at} 設回當下、{@code status} 改回
     * {@code published}，文章重新出現在 {@code /r/archive} 與 {@code /r/news/{slug}}，
     * 需提供有效金鑰。
     *
     * <p><b>為什麼需要它</b>：下架原本是單向門。{@code slug} 有 UNIQUE 約束、被下架的
     * 那一列仍佔著它，所以同 slug 重發必定 400；下架之後只能改用新 slug（對外網址全部
     * 失效）或手動 {@code UPDATE campaign}——後者正是這一組端點要消滅的操作模式。</p>
     *
     * <p><b>方法用 POST、路徑與下架相同</b>：受詞是同一個「已發布狀態」資源，
     * {@code POST} 建立它、{@code DELETE} 移除它。這比另開一個 {@code /republish}
     * 路徑更能表達「兩者是同一件事的兩個方向」，也與
     * {@code /api/admin/campaigns/{id}/schedule} 的既有慣例一致。</p>
     *
     * <p><b>沒有 request body</b>：這條端點<b>只能</b>把文章放回去，不能順手改內容、
     * 價格或發布時間。允許帶欄位會讓它變成一條「可改任意欄位」的後門，而那會讓
     * 「已解鎖的讀者付的價格」與「文章現在的價格」永久對不起來。發布時間一律取當下
     * （理由見 {@code CampaignService.republish}）。</p>
     *
     * <p>限制條件（狀態不符、已對外可見、沒有 slug → 409）由 service 層判斷。
     * <b>不動 {@code article_access} 與 {@code credit_txn}</b>：下架期間已解鎖者的憑證
     * 原封不動，重新上架後不會被要求再付一次。</p>
     */
    @PostMapping("/api/admin/campaigns/{id}/publication")
    public CampaignService.RepublishResult republish(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @PathVariable Long id) {
        guard.verify(key);
        return campaignService.republish(id);
    }

    /**
     * 解析發布時間（ISO-8601）；格式錯誤回 400 而非讓例外以 500 洩漏。
     *
     * <p>send 與 publish 共用同一份解析，避免兩條路徑各自實作而對同一個輸入
     * 給出不同的錯誤碼。</p>
     */
    private Instant parsePublishedAt(String publishedAt) {
        if (publishedAt == null) {
            return null;
        }
        try {
            return Instant.parse(publishedAt);
        } catch (java.time.format.DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "publishedAt 格式錯誤");
        }
    }

    /** 新服務存在時，把 UI 中的 Emoji 與 hashtag 寫入剛建立的文章。 */
    private void updateMetadata(long campaignId, String coverEmoji, List<String> tags) {
        if (metadataService != null) {
            metadataService.update(campaignId, coverEmoji, tags);
        }
    }

    /** 在任何寄送／發布副作用前先驗證新欄位。 */
    private void validateMetadata(String coverEmoji, List<String> tags) {
        if (metadataService != null) {
            metadataService.validate(coverEmoji, tags);
        }
    }

    /**
     * 目前寄信額度（動態偵測 Zeabur ZSend 帳號的日／月額度與已寄量），需提供有效金鑰。
     * 後台以此決定「單次上限」欄位的上界與超量警告門檻，不再寫死 100 封。
     */
    @GetMapping("/api/admin/mail-quota")
    public MailQuotaService.Quota mailQuota(
            @RequestHeader(value = KEY_HEADER, required = false) String key) {
        guard.verify(key);
        return mailQuotaService.current();
    }

    /** 邀請確認信請求：待確認名單的來源標記（如 exam）與單次寄送上限（配合寄信額度） */
    public record InviteRequest(String source, Integer limit) {}

    /** 對指定來源的待確認名單寄二次確認邀請信，需提供有效金鑰 */
    @PostMapping("/api/admin/campaign/invite")
    public InviteService.InviteResult invite(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @RequestBody InviteRequest req) {
        guard.verify(key);
        if (req.source() == null || req.source().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "source 為必填");
        }
        return inviteService.sendInvites(req.source(), clampLimit(req.limit()));
    }

    /**
     * 將前端傳來的單次上限收斂到「行銷可用額度」與「單批安全上限」之內。
     *
     * <p>前端已依 {@code /api/admin/mail-quota} 設好欄位 max，這裡是伺服器端的第二道防線：
     * 避免直接打 API 或前端快取過期時一次送出超量請求（逐封同步寄送會逾時）。
     * limit 為 null 時代表「不限」，同樣收斂成上限而非放行全部。</p>
     *
     * <p><b>用 {@code marketingBatchMax()} 而非 {@code batchMax()}</b>：後者是
     * {@code min(剩餘額度, 500)}，<b>沒有扣掉交易信保留額度</b>。邀請信與提醒信走的是
     * {@link InviteService}，全程沒有任何額度判斷——{@code CampaignService} 內的
     * reserve 檢查完全管不到這條路徑。於是「月剩餘 300、reserve 50」時按下「寄邀請信」，
     * 這裡會回 300、逐封寄光額度，之後任何讀者點 magic link 都收不到登入信，
     * 整個讀者端登不進去。那正是 spec §6 開宗明義要防的產品級故障。</p>
     *
     * <p><b>為什麼邀請信該讓位</b>：邀請信是站方主動外推的再徵詢，讀者不在等它，
     * 晚一天寄沒有損失；magic link 才是讀者當下盯著信箱等的那一封。
     * （{@code MailQuotaService.Quota#marketingBatchMax} 早就算出了正確的值，
     * 在此之前卻沒有任何生產程式碼消費它——正確答案一直在，只是沒接上唯一需要它的呼叫點。）</p>
     *
     * <p><b>可用量為 0 時必須拋 409，絕不可回傳 0</b>：{@link InviteService} 把
     * {@code limit <= 0} 解讀為「不限」（{@code limit > 0 && limit < eligible.size()}
     * 才切分批次），所以把 0 傳下去的效果是<b>整份名單全寄</b>——與意圖完全相反，
     * 而且正好發生在額度最吃緊的時候。這裡改為明確拒絕，與
     * {@code CampaignService} 在 {@code marketingRemaining <= 0} 時回 409 的作法一致：
     * 不寄 0 封卻回報成功，管理者會以為寄完了。</p>
     */
    private Integer clampLimit(Integer limit) {
        long marketingBatchMax = mailQuotaService.current().marketingBatchMax();
        if (marketingBatchMax <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "行銷可用寄信額度已用盡（剩餘額度全數保留給登入信等交易信），請待額度重置後再寄");
        }
        if (limit == null || limit <= 0 || limit > marketingBatchMax) {
            return (int) marketingBatchMax;
        }
        return limit;
    }

    /** 對「已邀請滿 3 天仍未確認」者補送提醒信（每人最多一次），需提供有效金鑰 */
    @PostMapping("/api/admin/campaign/invite/remind")
    public InviteService.ReminderResult remind(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @RequestBody InviteRequest req) {
        guard.verify(key);
        if (req.source() == null || req.source().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "source 為必填");
        }
        return inviteService.sendReminders(req.source(), clampLimit(req.limit()));
    }

    /** 取得邀請信範本（主旨與 HTML 內文，內文以 {{confirmLink}} 佔位確認連結），需提供有效金鑰 */
    @GetMapping("/api/admin/templates/invite")
    public MailTemplate inviteTemplate(
            @RequestHeader(value = KEY_HEADER, required = false) String key) {
        guard.verify(key);
        return inviteService.getTemplate();
    }

    /** 範本更新請求：主旨與 HTML 內文 */
    public record TemplateRequest(String subject, String bodyHtml) {}

    /** 更新邀請信範本並存入資料庫（內文必須含 {{confirmLink}}），需提供有效金鑰 */
    @org.springframework.web.bind.annotation.PutMapping("/api/admin/templates/invite")
    public MailTemplate updateInviteTemplate(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @RequestBody TemplateRequest req) {
        guard.verify(key);
        return inviteService.updateTemplate(req.subject(), req.bodyHtml());
    }

    /** 取得邀請記錄與成效統計（記錄含全部來源；統計以 source 為準，預設 exam），需提供有效金鑰 */
    @GetMapping("/api/admin/invites")
    public InviteService.InviteOverview invites(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @RequestParam(defaultValue = "exam") String source) {
        guard.verify(key);
        return inviteService.overview(source);
    }

    /** 取得歷史 campaign 列表（依建立時間降冪），需提供有效金鑰 */
    @GetMapping("/api/admin/campaigns")
    public List<Campaign> campaigns(
            @RequestHeader(value = KEY_HEADER, required = false) String key) {
        guard.verify(key);
        return campaignService.list();
    }

    /** 取消指定 campaign 的排程，需提供有效金鑰 */
    @DeleteMapping("/api/admin/campaigns/{id}/schedule")
    public Map<String, Integer> cancel(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @PathVariable Long id) {
        guard.verify(key);
        return campaignService.cancelSchedule(id);
    }

    /** 修改排程請求：新的主旨、markdown 內文、篩選條件與排程時間（ISO-8601） */
    public record RescheduleRequest(String subject, String markdown, Filter filter, String scheduledAt) {}

    /**
     * 修改指定 campaign 的未寄出排程（取消舊排程信後以新內容與新時間重排），需提供有效金鑰。
     * scheduledAt 必填且須為未來時間。
     */
    @org.springframework.web.bind.annotation.PutMapping("/api/admin/campaigns/{id}/schedule")
    public CampaignService.SendResult reschedule(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @PathVariable Long id,
            @RequestBody RescheduleRequest req) {
        guard.verify(key);
        // scheduledAt 必填；「舊排程是否仍可操作」與「新時間是否為未來」都由
        // CampaignService 依同一個後端時間點判斷，避免跨過排程時間時先回錯誤的 400。
        if (req.scheduledAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "排程模式需要 scheduledAt");
        }
        Instant scheduledAt = Instant.parse(req.scheduledAt());
        // 從篩選條件取出 role / interest（允許 filter 為 null）
        String role = req.filter() == null ? null : req.filter().role();
        String interest = req.filter() == null ? null : req.filter().interest();
        return campaignService.reschedule(
            id, req.subject(), req.markdown(), role, interest, scheduledAt,
            req.filter() == null ? null : req.filter().audience(),
            req.filter() == null ? null : req.filter().savedSegmentId());
    }
}
