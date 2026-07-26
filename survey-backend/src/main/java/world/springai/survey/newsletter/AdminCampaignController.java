package world.springai.survey.newsletter;

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

    /** 注入依賴 */
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
    }

    /** 預覽用請求：主旨與 markdown 內文 */
    public record PreviewRequest(String subject, String markdown) {}

    /** 測試寄送請求：主旨、markdown 內文、目標信箱 */
    public record TestRequest(String subject, String markdown, String to) {}

    /** 收件人篩選條件：職業角色、興趣主題 */
    public record Filter(String role, String interest) {}

    /**
     * 發送請求：主旨、markdown 內文、篩選條件、發送模式、排程時間（ISO-8601）。
     * 以下四個為本次擴充的發布欄位（皆可省略）：
     * tier（BASIC/PREMIUM，省略視為 BASIC）、creditCost（PREMIUM 解鎖點數）、
     * slug（網頁文章網址片段）、publishedAt（發布時間 ISO-8601；省略且有 slug 時視為立即發布）。
     * vipFullInMail 與 filterLevels 屬階段 D／F 範圍，本次不開放設定。
     */
    public record SendRequest(String subject, String markdown, Filter filter, String mode, String scheduledAt,
                              String tier, Integer creditCost, String slug, String publishedAt) {}

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

        // 解析發布時間（ISO-8601），格式錯誤回 400 而非讓例外以 500 洩漏
        Instant publishedAt = null;
        if (req.publishedAt() != null) {
            try {
                publishedAt = Instant.parse(req.publishedAt());
            } catch (java.time.format.DateTimeParseException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "publishedAt 格式錯誤");
            }
        }

        return campaignService.send(req.subject(), req.markdown(), role, interest, req.mode(), scheduledAt,
            req.tier(), req.creditCost(), req.slug(), publishedAt);
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
        // scheduledAt 必填且須為未來時間
        if (req.scheduledAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "排程模式需要 scheduledAt");
        }
        Instant scheduledAt = Instant.parse(req.scheduledAt());
        if (!scheduledAt.isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "排程時間需為未來");
        }
        // 從篩選條件取出 role / interest（允許 filter 為 null）
        String role = req.filter() == null ? null : req.filter().role();
        String interest = req.filter() == null ? null : req.filter().interest();
        return campaignService.reschedule(id, req.subject(), req.markdown(), role, interest, scheduledAt);
    }
}
