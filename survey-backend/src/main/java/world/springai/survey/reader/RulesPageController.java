package world.springai.survey.reader;

import org.springframework.http.HttpHeaders;
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
     * <p><b>必須明確指定 charset=UTF-8</b>：{@code MediaType.TEXT_HTML_VALUE}
     * 不含 charset 參數，standalone MockMvc（無 Spring Boot 自動配置）用的
     * {@code StringHttpMessageConverter} 預設 charset 是 ISO-8859-1，中文字會
     * 被錯誤編碼成亂碼。實際部署時 Spring Boot 會覆寫成 UTF-8，但測試環境
     * 不能依賴這個假設，故在此明確寫死。</p>
     */
    @GetMapping(value = "/r/rules", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> rules(
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {
        boolean loggedIn = readerContext.resolve(sessionCookie).isPresent();

        Map<String, String> vars = new HashMap<>();
        vars.put("<!--NAV_LINKS-->", navLinks(loggedIn));
        vars.put("<!--SIGNUP_GRANT-->", String.valueOf(creditPolicy.signupGrant()));
        vars.put("<!--PREMIUM_COST-->", String.valueOf(creditPolicy.premiumCost()));
        vars.put("<!--REFERRAL_REWARD-->", String.valueOf(creditPolicy.referralReward()));
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
}
