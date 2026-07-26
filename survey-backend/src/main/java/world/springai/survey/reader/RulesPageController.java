package world.springai.survey.reader;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 遊戲規則頁。
 *
 * <p><b>所有數字動態注入，不寫死</b>（spec §5.11 硬要求）：§9.2 明訂第一版
 * 參數就是要靠上線後數據校準。若頁面寫死「一篇 10 點」而後台已調成 50 點，
 * 讀者看到的代價與實際扣的不一致——那是最傷信任的一類落差。數字一律取自
 * {@link CreditPolicy}，與 {@code /r/me} 同源。</p>
 *
 * <p><b>但它與 paywall 顯示的價格<u>不</u>同源，文案必須說清楚</b>：本頁與
 * {@code /r/me} 用的是全域預設 {@link CreditPolicy#premiumCost()}，而文章頁的
 * gate 與 {@code UnlockService} 實際扣的是 {@code CreditPolicy.costOf(campaign)}
 * ——<b>該篇自己的 {@code campaign.credit_cost}</b>。且 {@code ck_campaign_premium_cost}
 * 與 {@code CampaignService.validateCreditCost} 都強制 PREMIUM 的 {@code credit_cost > 0}，
 * 所以 {@code costOf()} 退回全域預設的那條分支是死碼：本頁顯示的數字<b>結構性地</b>
 * 不會是任何一篇文章的實際扣款額（除非數值恰好巧合）。這不是三處不同步的邊緣情況。
 * 每篇文章有自己的定價本來就是對的（已解鎖的讀者付的是當時的價，不該被全域參數
 * 追溯改價），所以修的是文案而不是行為——頁面明講「通常每篇 N 點，實際以各篇
 * 文章頁為準」。規則頁的存在理由就是點數機制的可信度來源，帶著結構性錯誤的
 * 數字上線比沒有規則頁更傷。</p>
 *
 * <p><b>刻意不做 CMS</b>（YAGNI）：文案寫在靜態 HTML，只有數字動態注入。
 * 文案大改需要部署一次，但這頻率遠低於參數調整。不為此建
 * {@code static_page} 表或後台編輯器——{@code mail_template} 那種入庫模式
 * 是因為信件範本需要頻繁微調，規則頁沒有同等需求。</p>
 */
@RestController
public class RulesPageController {

    /**
     * 規則最後更新日期。
     *
     * <p>規則涉及讀者權益，必須有日期。刻意寫成常數而非 {@code LocalDate.now()}：
     * 顯示「今天」會讓讀者以為規則天天在改，反而降低可信度。<b>修改本頁文案時
     * 請一併更新這個日期</b>。</p>
     */
    private static final String LAST_UPDATED = "2026-07-26";

    private final HtmlTemplate htmlTemplate;
    private final CreditPolicy creditPolicy;
    private final ReaderContext readerContext;

    /** 注入頁面渲染、點數參數與讀者身分解析 */
    public RulesPageController(HtmlTemplate htmlTemplate,
                              CreditPolicy creditPolicy,
                              ReaderContext readerContext) {
        this.htmlTemplate = htmlTemplate;
        this.creditPolicy = creditPolicy;
        this.readerContext = readerContext;
    }

    /**
     * 規則頁：公開，不需登入。
     *
     * <p>與套件內其他頁面（{@code /r/archive}、{@code /r/news/{slug}}、
     * {@code /r/login}、{@code /r/}）一致，統一用 {@link MediaType#TEXT_HTML_VALUE}。
     * 實際部署時 Spring Boot 的 {@code WebMvcAutoConfiguration} 會註冊帶 UTF-8
     * 預設值的 {@code StringHttpMessageConverter}，回應本來就是 UTF-8；
     * standalone MockMvc 測試環境沒有這層自動配置，UTF-8 的保證改由測試自行
     * 註冊對應的 converter 負責（見 {@code RulesPageControllerTest}）。</p>
     */
    @GetMapping(value = "/r/rules", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> rules(
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {
        boolean loggedIn = readerContext.resolve(sessionCookie).isPresent();

        int signupGrant = creditPolicy.signupGrant();
        int referralReward = creditPolicy.referralReward();

        Map<String, String> vars = new HashMap<>();
        vars.put("<!--NAV_LINKS-->", navLinks(loggedIn));
        vars.put("<!--SIGNUP_GRANT_LINE-->", signupGrantLine(signupGrant));
        vars.put("<!--SIGNUP_GRANT_NOTE-->", signupGrantNote(signupGrant));
        vars.put("<!--PREMIUM_COST-->", String.valueOf(creditPolicy.premiumCost()));
        vars.put("<!--REFERRAL_REWARD_LINE-->", referralRewardLine(referralReward));
        vars.put("<!--REFERRAL_REWARD_NOTE-->", referralRewardNote(referralReward));
        vars.put("<!--VIP_DAYS-->", String.valueOf(creditPolicy.vipDefaultDays()));
        vars.put("<!--LAST_UPDATED-->", LAST_UPDATED);

        // 導覽列會因登入狀態而異，故不可被共享快取；規則本身則允許讀者端瀏覽器快取，
        // 但參數改動要立即生效，所以一律 no-store。
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
            .body(htmlTemplate.render("static/reader/rules.html", vars));
    }

    /** 依登入狀態顯示不同的導覽連結 */
    private String navLinks(boolean loggedIn) {
        if (loggedIn) {
            return "<a href=\"/r/archive\">歷史內容</a><a href=\"/r/me\">我的帳戶</a>";
        }
        return "<a href=\"/r/archive\">歷史內容</a><a href=\"/r/login\">登入</a>";
    }

    /** 「首次登入送 X 點」列項文案；X 為 0 時改用不荒謬的說法（關閉贈點是合法營運設定） */
    private String signupGrantLine(int signupGrant) {
        if (signupGrant == 0) {
            return "目前暫無首次登入贈點";
        }
        return "首次登入送 " + signupGrant + " 點";
    }

    /** 「為什麼有些文章要點數」段落末句；X 為 0 時不提贈點字眼，避免文意矛盾 */
    private String signupGrantNote(int signupGrant) {
        if (signupGrant == 0) {
            return "而不是把好東西鎖起來，只是目前首次登入暫無贈點。";
        }
        return "而不是把好東西鎖起來——" + signupGrant
            + " 點的初始贈點就是希望你先看幾篇再決定值不值得。";
    }

    /** 「邀請朋友訂閱，每位 +X 點」列項文案；X 為 0 時改用不荒謬的說法 */
    private String referralRewardLine(int referralReward) {
        if (referralReward == 0) {
            return "目前暫無邀請獎勵";
        }
        return "邀請朋友訂閱，每位 +" + referralReward + " 點";
    }

    /**
     * 「邀請怎麼算成功」段落末句；X 為 0 時說明獎勵暫停發放，而非顯示「拿到 0 點」。
     *
     * <p><b>0 值文案不得承諾「成功邀請仍會被記錄」</b>：程式並不保證這件事。
     * {@code ReferralService.rewardFor} 在 {@code reward <= 0} 時直接 return、
     * <b>完全不寫帳本</b>（刻意如此——占用了冪等鍵，日後把獎勵調回 100，
     * 這位被邀者的獎勵就永遠拿不到了），而 {@code ReferralService.stats} 數的正是
     * {@code credit_txn} 裡 REFERRAL 的筆數。於是舊文案會造成同一個 HTTP 回應內
     * 自我矛盾：上半頁寫「成功邀請仍會被記錄」，下半頁的成效區塊卻顯示
     * 「還沒有人透過你的連結完成訂閱」——即使讀者確實已經邀請成功五個人。
     * 文案不得對行為做出程式碼不保證的承諾。</p>
     *
     * <p>機制本身（獎勵為 0 時該不該留下計數）屬 spec §5.4 的待辦，需要另一張
     * 表或冪等鍵設計，排在下一階段；但這句話現在就在誤導讀者，先改掉。</p>
     */
    private String referralRewardNote(int referralReward) {
        if (referralReward == 0) {
            return "。目前邀請獎勵暫停發放，恢復發放後成功的邀請才會開始累計。";
        }
        return "，你才會拿到 " + referralReward + " 點。";
    }
}
