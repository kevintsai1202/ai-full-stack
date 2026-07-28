package world.springai.survey.reader;

import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import world.springai.survey.audience.SurveyResponse;
import world.springai.survey.audience.SurveyResponseRepository;
import world.springai.survey.ReaderSiteLinks;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 讀者自助頁面：我的帳戶與我的邀請。
 *
 * <p>與 {@link ReaderPageController}（內容頁）、{@link ReaderAuthController}
 * （登入流程）分開：這裡處理的是「讀者對自己帳戶的操作」，依賴組合完全不同。</p>
 *
 * <p>頁面而非 API：未登入時<b>導向登入頁並帶 redirect</b>，而不是回 401——
 * 讀者在瀏覽器看到空白的 401 是死路。</p>
 */
@RestController
public class ReaderPortalController {

    /** 日期顯示格式 */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 讀者面向的日期一律換算成台北時區再顯示，避免 UTC 存值在跨日時顯示成前一天 */
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    /** 交易明細一次只顯示最近幾筆，避免帳本無限成長拖慢頁面 */
    private static final int TXN_DISPLAY_LIMIT = 50;

    private final HtmlTemplate htmlTemplate;
    private final ReaderContext readerContext;
    private final CreditTxnRepository creditTxnRepository;
    private final SurveyResponseRepository surveyResponseRepository;
    private final ReferralService referralService;
    private final CreditPolicy creditPolicy;
    /**
     * 「進階內容每篇多少點」的片語來源。
     *
     * <p>刻意不用 {@link CreditPolicy#premiumCost()}：那是全域預設，而 PREMIUM 的
     * 實際扣款一律取該篇自己的 {@code campaign.credit_cost}（{@code costOf()} 的
     * 全域退路是死碼）。與 {@code /r/rules} 共用同一份判斷，兩頁不會各說一套。</p>
     */
    private final PremiumCostDisplay premiumCostDisplay;
    /** 個人資料寫入；交易邊界在它身上（見 {@link #updateProfile}） */
    private final ReaderProfileService readerProfileService;
    /** 讀者站公開網址的唯一組裝入口。 */
    private final ReaderSiteLinks readerSiteLinks;
    /** 病毒成長里程碑與活動；舊隔離測試未提供時維持空區塊。 */
    private final ReferralGrowthService referralGrowthService;

    /**
     * 注入渲染、身分解析、帳本、名單中心、邀請統計、點數參數、進階文章點數區間、
     * 個人資料寫入與對外網域
     */
    public ReaderPortalController(HtmlTemplate htmlTemplate,
                                 ReaderContext readerContext,
                                 CreditTxnRepository creditTxnRepository,
                                 SurveyResponseRepository surveyResponseRepository,
                                 ReferralService referralService,
                                 CreditPolicy creditPolicy,
                                 PremiumCostDisplay premiumCostDisplay,
                                 ReaderProfileService readerProfileService,
                                 ReaderSiteLinks readerSiteLinks) {
        this(htmlTemplate, readerContext, creditTxnRepository, surveyResponseRepository,
            referralService, creditPolicy, premiumCostDisplay, readerProfileService,
            readerSiteLinks, null);
    }

    /** 正式環境建構子，加入里程碑與活動資料。 */
    @Autowired
    public ReaderPortalController(HtmlTemplate htmlTemplate,
                                 ReaderContext readerContext,
                                 CreditTxnRepository creditTxnRepository,
                                 SurveyResponseRepository surveyResponseRepository,
                                 ReferralService referralService,
                                 CreditPolicy creditPolicy,
                                 PremiumCostDisplay premiumCostDisplay,
                                 ReaderProfileService readerProfileService,
                                 ReaderSiteLinks readerSiteLinks,
                                 ReferralGrowthService referralGrowthService) {
        this.htmlTemplate = htmlTemplate;
        this.readerContext = readerContext;
        this.creditTxnRepository = creditTxnRepository;
        this.surveyResponseRepository = surveyResponseRepository;
        this.referralService = referralService;
        this.creditPolicy = creditPolicy;
        this.premiumCostDisplay = premiumCostDisplay;
        this.readerProfileService = readerProfileService;
        this.readerSiteLinks = readerSiteLinks;
        this.referralGrowthService = referralGrowthService;
    }

    /** 個人資料更新請求；目前只開放顯示名稱 */
    public record ProfileRequest(String name) {}

    /** 我的帳戶：餘額、方案、交易明細、顯示名稱編輯 */
    @GetMapping(value = "/r/me", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> me(
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {
        Optional<ReaderContext.Current> current = readerContext.resolve(sessionCookie);
        if (current.isEmpty()) {
            return redirectToLogin("/r/me");
        }
        Reader reader = current.get().reader();

        Map<String, String> vars = new HashMap<>();
        // 固定傳 true：未登入者在上面就被導向登入頁了，走到這裡必然是已登入狀態
        vars.put("<!--NAV_LINKS-->", ReaderNav.links(true));
        vars.put("<!--CREDITS-->", String.valueOf(reader.getCredits()));
        vars.put("<!--PREMIUM_COST_PHRASE-->", premiumCostDisplay.perArticlePhrase());
        vars.put("<!--EMAIL-->", HtmlTemplate.escapeHtml(reader.getEmail()));
        vars.put("<!--TIER_STATUS-->", renderTierStatus(reader));
        vars.put("<!--DISPLAY_NAME-->", HtmlTemplate.escapeHtml(displayNameOf(reader.getEmail())));
        // 只取最近 50 筆：credit_txn 只增不刪，隨解鎖與邀請無限成長，
        // 重度讀者若一次撈全部帳本，頁面會逐漸變慢
        vars.put("<!--TXN_LIST-->", renderTransactions(
            creditTxnRepository.findByReaderIdOrderByCreatedAtDesc(reader.getId(), PageRequest.of(0, TXN_DISPLAY_LIMIT))));

        return privatePage(htmlTemplate.render("templates/reader/me.html", vars));
    }

    /** 我的邀請：邀請連結、邀請碼與成效 */
    @GetMapping(value = "/r/invite", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> invite(
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {
        Optional<ReaderContext.Current> current = readerContext.resolve(sessionCookie);
        if (current.isEmpty()) {
            return redirectToLogin("/r/invite");
        }
        Reader reader = current.get().reader();
        ReferralService.ReferralStats stats = referralService.stats(reader.getId());
        int reward = creditPolicy.referralReward();

        Map<String, String> vars = new HashMap<>();
        // 固定傳 true：未登入者在上面就被導向登入頁了，走到這裡必然是已登入狀態
        vars.put("<!--NAV_LINKS-->", ReaderNav.links(true));
        vars.put("<!--REWARD_INTRO-->", rewardIntro(reward));
        // 完整網址：讀者要把它貼給別人，相對路徑沒有用
        vars.put("<!--INVITE_LINK-->",
            HtmlTemplate.escapeHtml(readerSiteLinks.subscribeWithReferral(reader.getReferralCode())));
        vars.put("<!--REFERRAL_CODE-->", HtmlTemplate.escapeHtml(reader.getReferralCode()));
        vars.put("<!--STATS_BLOCK-->", renderStats(stats));
        vars.put("<!--MILESTONE_BLOCK-->", referralGrowthService == null ? ""
            : renderMilestones(stats.invitedCount(), referralGrowthService.milestones(reader.getId())));
        vars.put("<!--ACTIVE_CAMPAIGN_BLOCK-->", referralGrowthService == null ? ""
            : renderActiveCampaigns(referralGrowthService.activeCampaigns()));

        return privatePage(htmlTemplate.render("templates/reader/invite.html", vars));
    }

    /**
     * 「邀請說明」段落文案；獎勵為 0 時改用不荒謬的說法，而不是「拿到 0 點」。
     *
     * <p>沿用 {@link RulesPageController#referralRewardNote} 的慣例：{@code
     * CreditPolicy#referralReward()} 的下限刻意是 0（關閉邀請獎勵是合法的營運
     * 設定），若整段文案原樣套用數字，讀者會看到「你會拿到 0 點」這種讀起來像
     * 故障的字。</p>
     *
     * <p><b>兩種設定的文案精確度來自不同的資料來源，不可互相套用</b>
     * （{@code ReferralService.stats} 的人數是「帳本 REFERRAL 的 note」與
     * {@code reader.referred_by} 的<b>聯集</b>）：</p>
     * <ul>
     *   <li><b>獎勵 &gt; 0</b>（本方法的 else 分支）：帳本在被邀者<b>點確認信的那一刻</b>
     *       就寫入，所以人數立刻成長，<b>不需要</b>「首次登入」這個附註。頁面靜態文案
     *       （{@code invite.html} 的「還要點開確認信才算一次成功邀請」）因此是準確的。</li>
     *   <li><b>獎勵 = 0</b>：{@code rewardFor} 在 {@code reward <= 0} 時完全不寫帳本
     *       （刻意，避免占用冪等鍵），聯集只剩 {@code referred_by} 那一邊，而它是在被邀者
     *       <b>首次登入建立帳戶</b>時才寫入。因此 0 值文案的「並首次登入」
     *       <b>必須保留</b>——少了它就是承諾了程式在這個設定下不保證的時序。
     *       點數也確實暫停（點數只來自帳本）。</li>
     * </ul>
     *
     * <p>0 值文案的理由與 {@link RulesPageController#referralRewardNote} 逐字相同，
     * 兩處必須同步。</p>
     */
    private String rewardIntro(int reward) {
        if (reward == 0) {
            return "把連結分享給可能有興趣的人。目前邀請獎勵暫停發放；朋友完成訂閱並首次登入後，"
                + "仍會計入下方的邀請人數，點數則要等恢復發放後才開始累計。";
        }
        return "把連結分享給可能有興趣的人。對方確認訂閱後，你會拿到 " + reward + " 點。";
    }

    /**
     * 渲染邀請成效。
     *
     * <p>零邀請時給鼓勵性的空狀態而非「0 人 / 0 點」——冷數字讀起來像
     * 失敗提示，而這頁的目的是讓人想去分享。</p>
     *
     * <p><b>空狀態的條件是兩個數字都為 0</b>，不是只看人數。只看人數的話，任何
     * 「站方已經為某次邀請付了點數、但那位被邀者沒被計入人數」的情形都會連
     * <b>已發放的點數</b>一起藏起來——讀者的帳戶頁看得到那筆「邀請獎勵 +100」，
     * 邀請頁卻說「還沒有人透過你的連結完成訂閱」。點數是實際發生的資產變動，
     * 不論人數是多少都必須顯示。</p>
     *
     * <p>只顯示彙總數字：{@link ReferralService.ReferralStats} 本身不帶被邀者
     * email 或任何可辨識資訊，邀請碼是可公開分享的連結，透過它訂閱的陌生人
     * 與邀請人彼此並不認識，此頁不得洩漏對方身分。</p>
     */
    private String renderStats(ReferralService.ReferralStats stats) {
        if (stats.invitedCount() == 0 && stats.earnedCredits() == 0) {
            return "<p class=\"empty\">還沒有人透過你的連結完成訂閱。分享出去試試看？</p>";
        }
        return """
            <p class="balance">%d 人</p>
            <p style="color:var(--muted);font-size:.92rem">累計獲得 %d 點</p>
            """.formatted(stats.invitedCount(), stats.earnedCredits());
    }

    /** 顯示已取得徽章與下一階進度，不包含任何被邀者個資。 */
    private String renderMilestones(int invitedCount,
                                    List<ReferralGrowthService.MilestoneView> milestones) {
        StringBuilder html = new StringBuilder("<div class=\"card\"><h2>邀請里程碑</h2><div class=\"milestone-grid\">");
        for (ReferralGrowthService.MilestoneView item : milestones) {
            int progress = Math.min(100, invitedCount * 100 / item.milestone());
            html.append("<div class=\"milestone ")
                .append(item.earned() ? "earned" : "")
                .append("\"><span class=\"milestone-icon\">")
                .append(item.earned() ? "🏅" : "🔒")
                .append("</span><strong>")
                .append(HtmlTemplate.escapeHtml(item.badgeName()))
                .append("</strong><small>")
                .append(item.milestone()).append(" 人");
            if (item.bonusCredits() > 0) {
                html.append("・+").append(item.bonusCredits()).append(" 點");
            }
            html.append("</small><div class=\"milestone-bar\"><i style=\"width:")
                .append(progress).append("%\"></i></div></div>");
        }
        return html.append("</div></div>").toString();
    }

    /** 顯示進行中的加碼活動。 */
    private String renderActiveCampaigns(List<ReferralCampaign> campaigns) {
        if (campaigns.isEmpty()) return "";
        StringBuilder html = new StringBuilder("<aside class=\"campaign-banner\"><strong>限時加碼進行中</strong><ul>");
        for (ReferralCampaign campaign : campaigns) {
            html.append("<li>").append(HtmlTemplate.escapeHtml(campaign.getName()))
                .append("：邀請獎勵 ×").append(campaign.getMultiplier()).append("</li>");
        }
        return html.append("</ul></aside>").toString();
    }

    /**
     * 更新顯示名稱。
     *
     * <p><b>本方法刻意沒有 {@code @Transactional}</b>：交易由
     * {@link ReaderProfileService#updateName} 負責。註解掛在 controller 上會讓交易
     * 在<b>身分驗證之前</b>就開啟——未帶 cookie 的請求先借連線、開交易，再回 401，
     * 而這是公開端點（不需 admin key），等於讓未授權流量消耗連線池。
     * 這裡只做身分解析與 404 判斷，寫入一律委派給服務層。</p>
     */
    @PostMapping("/api/reader/profile")
    public ResponseEntity<Void> updateProfile(
            @RequestBody ProfileRequest request,
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {
        Optional<ReaderContext.Current> current = readerContext.resolve(sessionCookie);
        if (current.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String email = current.get().reader().getEmail();

        // 名單中查無此 email 時回 404 而不建新列：建列會讓「讀者維護個人資訊」
        // 變成「讀者可往名單中心插資料」，而名單中心的每一列都代表一份同意紀錄。
        if (!readerProfileService.updateName(email, request.name())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok().build();
    }

    /** 導向登入頁並帶回跳目標 */
    private ResponseEntity<String> redirectToLogin(String target) {
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, "/r/login?redirect=" + target)
            .build();
    }

    /**
     * 個人頁面的回應標頭。
     *
     * <p>內容含餘額與交易明細，絕不可被共享快取（CDN、app-gateway 反向代理）
     * 拿去餵給別的讀者。</p>
     */
    private ResponseEntity<String> privatePage(String html) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
            .body(html);
    }

    /**
     * 描述方案狀態。
     *
     * <p>本系統刻意不做 VIP 自動降級排程（spec §13.5），所以資料庫裡會有
     * 「tier=VIP 但已過期」的列。這裡以 {@link Reader#isActiveVip} 判斷而非
     * 直接看 tier——否則讀者會以為自己還是 VIP，然後在解鎖時發現要扣點。</p>
     */
    private String renderTierStatus(Reader reader) {
        OffsetDateTime now = OffsetDateTime.now();
        if (reader.isActiveVip(now)) {
            return reader.getVipExpiresAt() == null
                ? "VIP（無到期日）"
                : "VIP（有效至 " + formatTaipeiDate(reader.getVipExpiresAt()) + "）";
        }
        if (Reader.TIER_VIP.equals(reader.getTier())) {
            return "VIP 已到期，目前為一般訂閱者";
        }
        return "一般訂閱者";
    }

    /**
     * 把儲存的 {@link OffsetDateTime}（通常是 UTC）換算成台北時區再格式化。
     *
     * <p>直接用實體儲存的 offset 格式化會有跨日誤差：UTC 存的
     * {@code 2026-07-26T22:00Z} 讀者當地（UTC+8）已經是 27 日。</p>
     */
    private static String formatTaipeiDate(OffsetDateTime value) {
        return value.atZoneSameInstant(TAIPEI).format(DATE_FORMAT);
    }

    /** 從名單中心取顯示名稱；查無資料回空字串 */
    private String displayNameOf(String email) {
        return surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(email)
            .map(SurveyResponse::getName)
            .orElse("");
    }

    /** 邀請獎勵明細列的固定顯示文字，取代被邀者 email（見 {@link #renderTransactions}） */
    private static final String REFERRAL_NOTE_PLACEHOLDER = "一位朋友完成訂閱";

    /**
     * 渲染交易明細。
     *
     * <p><b>REFERRAL 一律不顯示 note</b>：{@code reason=REFERRAL} 的 note 存的是
     * 被邀者的 email（{@code ReferralService} 用它當發獎冪等鍵，這個值本身
     * 不能改），而邀請碼是可公開分享的連結——任何陌生人透過該連結訂閱，
     * 他的 email 就會出現在邀請人的帳戶頁上，兩人可能素不相識。因此這裡只改
     * 顯示層，用固定文字取代，不動 note 實際存的值。</p>
     *
     * <p>其餘 reason 的 note 一律跳脫（存的是後台輸入的文章主旨）。</p>
     */
    private String renderTransactions(List<CreditTxn> transactions) {
        if (transactions.isEmpty()) {
            return "<p class=\"empty\">還沒有交易紀錄。</p>";
        }
        StringBuilder sb = new StringBuilder("<ul class=\"txn-list\">");
        for (CreditTxn txn : transactions) {
            String sign = txn.getDelta() >= 0 ? "+" : "";
            String cls = txn.getDelta() >= 0 ? "gain" : "spend";
            String noteDisplay = CreditTxn.REASON_REFERRAL.equals(txn.getReason())
                ? REFERRAL_NOTE_PLACEHOLDER
                : HtmlTemplate.escapeHtml(txn.getNote());
            sb.append("<li class=\"txn-item\">")
              .append("<span class=\"txn-delta ").append(cls).append("\">")
              .append(sign).append(txn.getDelta()).append("</span>")
              .append("<span class=\"txn-reason\">").append(reasonLabel(txn.getReason())).append("</span>")
              .append("<span class=\"txn-note\">").append(noteDisplay).append("</span>")
              .append("<span class=\"txn-date\">")
              .append(txn.getCreatedAt() == null ? "" : formatTaipeiDate(txn.getCreatedAt()))
              .append("</span></li>");
        }
        return sb.append("</ul>").toString();
    }

    /**
     * 把交易原因代碼轉成讀者看得懂的中文。
     *
     * <p>default 分支改回固定的通用中文，而非原樣輸出 {@code reason}：
     * {@code credit_txn.reason} 是無 CHECK 約束的 TEXT 欄位，若日後任何
     * 從輸入取得 reason 的後台功能寫入非常數值，原樣拼進 HTML 會是一個
     * 未經 escape 的注入點，違反 {@link HtmlTemplate#render} 的契約。
     * 顯示固定文案既 fail-safe，也不會把內部英文代碼洩漏給讀者。</p>
     */
    private String reasonLabel(String reason) {
        return switch (reason) {
            case CreditTxn.REASON_SIGNUP_GRANT -> "初始贈點";
            case CreditTxn.REASON_REFERRAL -> "邀請獎勵";
            case CreditTxn.REASON_REFERRAL_INVITEE -> "受邀確認加碼";
            case CreditTxn.REASON_REFERRAL_MILESTONE -> "邀請里程碑";
            case CreditTxn.REASON_READ -> "解鎖文章";
            case CreditTxn.REASON_ADMIN_GRANT -> "站方贈點";
            default -> "點數調整";
        };
    }
}
