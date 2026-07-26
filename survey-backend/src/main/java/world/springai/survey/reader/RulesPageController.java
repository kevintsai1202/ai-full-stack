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
 * {@link CreditPolicy}，與 paywall 提示區塊、{@code /r/me} 同源。</p>
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

    /**
     * 依登入狀態顯示不同的導覽連結。
     *
     * <p><b>注意</b>：{@code /r/me}（我的帳戶）頁面由 Task 9 提供，目前 repo 內
     * 尚無對應的 {@code @GetMapping}。在 Task 9 完成前，已登入讀者點這個連結
     * 會得到 404——這是預期中的暫時狀態，連結本身沒有打錯，不需改掉。</p>
     */
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

    /** 「邀請怎麼算成功」段落末句；X 為 0 時說明獎勵暫停發放，而非顯示「拿到 0 點」 */
    private String referralRewardNote(int referralReward) {
        if (referralReward == 0) {
            return "。目前邀請獎勵暫停發放，成功邀請仍會被記錄。";
        }
        return "，你才會拿到 " + referralReward + " 點。";
    }
}
