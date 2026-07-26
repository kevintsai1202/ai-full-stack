package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import world.springai.survey.newsletter.Campaign;
import world.springai.survey.newsletter.CampaignRepository;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 規則頁：數字必須全部來自 CreditPolicy，「每篇 N 點」則必須來自實際已發布文章的區間。
 *
 * <p>測試策略刻意用「非典型數值」（77 / 33 / 55 / 111、區間 12–48）而非真實的
 * 300 / 10 / 100 / 365。用真實值的話，即使實作把數字寫死在 HTML 裡，
 * 測試也會通過——那種測試什麼都證明不了。</p>
 *
 * <p><b>{@link PremiumCostDisplay} 用真貨、只 mock 掉 {@link CampaignRepository}</b>：
 * 若連 {@code PremiumCostDisplay} 也 mock 掉，測試就只證明了「controller 會把某個字串
 * 貼上頁面」，證明不了那個字串來自 {@code campaign.credit_cost} 的區間查詢——
 * 而那正是本次修正的全部目的。</p>
 */
class RulesPageControllerTest {

    private CreditPolicy creditPolicy;
    private ReaderContext readerContext;
    private CampaignRepository campaignRepository;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        creditPolicy = mock(CreditPolicy.class);
        readerContext = mock(ReaderContext.class);
        campaignRepository = mock(CampaignRepository.class);
        when(readerContext.resolve(any())).thenReturn(Optional.empty());
        when(creditPolicy.signupGrant()).thenReturn(77);
        when(creditPolicy.premiumCost()).thenReturn(33);
        when(creditPolicy.referralReward()).thenReturn(55);
        when(creditPolicy.vipDefaultDays()).thenReturn(111);
        // 預設情境：站上有已發布的 PREMIUM 文章，最便宜 12 點、最貴 48 點。
        // 刻意與 premiumCost() 的 33 不同，才能分辨頁面到底讀了哪一個來源。
        givenPublishedPremiumCostRange(12, 48);
        // standalone MockMvc 沒有 Spring Boot 的 WebMvcAutoConfiguration，
        // 預設的 StringHttpMessageConverter 用 ISO-8859-1，中文字會變亂碼。
        // 產品程式碼維持 MediaType.TEXT_HTML_VALUE（與套件內其他頁面一致），
        // UTF-8 的保證改由這裡手動註冊一個帶 UTF-8 預設值的 converter 負責——
        // 真實部署時 Spring Boot 會自動註冊等效的 converter，這裡只是補上
        // standalone 環境缺少的那一層，不是多餘的樣板。
        mvc = MockMvcBuilders
            .standaloneSetup(new RulesPageController(new HtmlTemplate(), creditPolicy, readerContext,
                new PremiumCostDisplay(campaignRepository, creditPolicy)))
            .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8))
            .build();
    }

    /**
     * 讓區間查詢回傳指定的 min／max；兩者傳 null 代表「站上沒有任何已發布 PREMIUM 文章」
     * （聚合查詢在零列時回一列而兩欄皆 NULL）。
     */
    private void givenPublishedPremiumCostRange(Integer min, Integer max) {
        when(campaignRepository.findPremiumCostRange(Campaign.TIER_PREMIUM))
            .thenReturn(new CampaignRepository.PremiumCostRange() {
                @Override public Integer getMinCost() { return min; }
                @Override public Integer getMaxCost() { return max; }
            });
    }

    /** 頁面可公開存取（不需登入） */
    @Test
    void rulesPageIsPublic() throws Exception {
        mvc.perform(get("/r/rules")).andExpect(status().isOk());
    }

    /** 初始贈點必須來自 CreditPolicy */
    @Test
    void injectsSignupGrant() throws Exception {
        mvc.perform(get("/r/rules")).andExpect(content().string(org.hamcrest.Matchers.containsString("77")));
    }

    /**
     * PREMIUM 單篇點數必須來自「已發布文章的實際區間」，不是全域預設。
     *
     * <p>{@code setUp} 讓區間查詢回 12–48、讓 {@code premiumCost()} 回 33。
     * 若實作退回讀全域預設，頁面會出現 33 而沒有 12–48，本測試會抓到。</p>
     */
    @Test
    void injectsPublishedPremiumCostRange() throws Exception {
        String html = mvc.perform(get("/r/rules")).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("目前每篇 12–48 點"),
            "沒有顯示已發布 PREMIUM 文章的實際點數區間");
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("33"),
            "頁面出現了全域預設 33——「每篇 N 點」的來源必須是 campaign.credit_cost，"
                + "全域預設結構性地不會是任何一篇的實際扣款額");
    }

    /** 邀請獎勵必須來自 CreditPolicy */
    @Test
    void injectsReferralReward() throws Exception {
        mvc.perform(get("/r/rules")).andExpect(content().string(org.hamcrest.Matchers.containsString("55")));
    }

    /** VIP 效期必須來自 CreditPolicy */
    @Test
    void injectsVipDays() throws Exception {
        mvc.perform(get("/r/rules")).andExpect(content().string(org.hamcrest.Matchers.containsString("111")));
    }

    /**
     * 真實預設值不得出現在頁面中。
     *
     * <p>這是上一組測試的反面守衛：若實作把 300 / 10 / 100 / 365 寫死在
     * HTML 裡，同時又注入了 mock 的值，上面四個測試會全部通過而頁面
     * 卻同時顯示兩組數字。斷言「舊值不存在」才真的守住了單一來源。</p>
     */
    @Test
    void hardcodedDefaultsAreAbsent() throws Exception {
        String html = mvc.perform(get("/r/rules")).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("300 點"));
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("10 點"));
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("100 點"));
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("365 天"));
    }

    /**
     * VIP 段落必須逐字採用 spec §5.11 已定案的措辭。
     *
     * <p>「尚未開放付費訂閱」這句是刻意的：VIP 若寫得模糊會顯得不透明，
     * 反而傷害整頁的可信度。這個測試守的是日後有人「順手改順一點」。</p>
     */
    @Test
    void vipWordingIsExactlyAsSpecified() throws Exception {
        mvc.perform(get("/r/rules"))
           .andExpect(content().string(org.hamcrest.Matchers.containsString(
               "VIP 目前由站方主動授予給課程學員與合作夥伴，尚未開放付費訂閱")));
    }

    /** 必須載明「點數不過期」與「規則調整不扣減既有餘額」兩項承諾 */
    @Test
    void containsBothStandingPromises() throws Exception {
        String html = mvc.perform(get("/r/rules")).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("不會過期"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("不會扣減"));
    }

    /** 必須有最後更新日期（規則涉及權益） */
    @Test
    void containsLastUpdatedDate() throws Exception {
        mvc.perform(get("/r/rules"))
           .andExpect(content().string(org.hamcrest.Matchers.containsString("最後更新")));
    }

    /**
     * 登入者的導覽列要顯示「我的帳戶」（{@code /r/me}）且不含未登入版連結，
     * 未登入則顯示「登入」（{@code /r/login}）且不含已登入版連結。
     *
     * <p>兩個方向都要驗：只驗未登入分支的話，把 {@code navLinks} 的
     * {@code loggedIn} 分支改成恆回未登入版本，這個測試依然全綠，等於證明不了
     * 自己的名字（「反映登入狀態」）。</p>
     */
    @Test
    void navReflectsLoginState() throws Exception {
        String anonymous = mvc.perform(get("/r/rules")).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(anonymous.contains("/r/login"));
        org.junit.jupiter.api.Assertions.assertFalse(anonymous.contains("/r/me"));

        // 讓 resolve 回傳非 empty，模擬已登入讀者
        Reader loggedInReader = new Reader("user@example.com", "CODE1234");
        loggedInReader.setId(1L);
        when(readerContext.resolve(any()))
            .thenReturn(Optional.of(new ReaderContext.Current(loggedInReader, true)));

        String loggedIn = mvc.perform(get("/r/rules")).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(loggedIn.contains("/r/me"));
        org.junit.jupiter.api.Assertions.assertFalse(loggedIn.contains("/r/login"));
    }

    /**
     * 導覽列必須含規則頁連結，登入與否都要有。
     *
     * <p>規則頁是點數機制的可信度來源（spec §5.11），在此之前它<b>不在任何一份
     * 導覽列裡</b>，讀者只能從 paywall 或頁內文字連結進去——也就是「還沒撞到
     * 付費牆的人」永遠不會知道有這一頁。這個斷言在五個讀者頁各有一份，
     * 是刻意的重複：每一頁都必須能獨立證明自己有這條連結。</p>
     *
     * <p>斷言整個 {@code <a>} 標籤而不是只斷言 {@code "/r/rules"}：規則頁本身
     * 的內文與 paywall 的提示連結（{@code >看遊戲規則<}）也含這個路徑，
     * 只比對路徑會讓斷言在導覽列少了這一項時仍然通過。</p>
     */
    @Test
    void navContainsRulesLinkForBothLoginStates() throws Exception {
        String anonymous = mvc.perform(get("/r/rules")).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(anonymous.contains(NAV_RULES_LINK),
            "未登入的導覽列少了遊戲規則連結");

        Reader loggedInReader = new Reader("user@example.com", "CODE1234");
        loggedInReader.setId(1L);
        when(readerContext.resolve(any()))
            .thenReturn(Optional.of(new ReaderContext.Current(loggedInReader, true)));

        String loggedIn = mvc.perform(get("/r/rules")).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(loggedIn.contains(NAV_RULES_LINK),
            "已登入的導覽列少了遊戲規則連結");
    }

    /**
     * 導覽列的規則頁連結（{@code ReaderNav} 產生的形式）。
     *
     * <p>刻意在測試裡另寫一份字串，不引用 {@code ReaderNav} 的常數：
     * 讀同一份實作的斷言恆為真，把連結刪掉也不會變紅。</p>
     */
    private static final String NAV_RULES_LINK = "<a href=\"/r/rules\">遊戲規則</a>";

    /**
     * 首次登入贈點為 0 時（合法的「關閉贈點」營運設定），頁面不得出現
     * 「送 0 點」這種讀起來像系統故障的文案，須改用講得通的說法。
     *
     * <p>破壞性驗證：拿掉 0 的文案分支（一律回傳「首次登入送 X 點」）→
     * 本測試會抓到「送 0 點」而變紅。</p>
     */
    @Test
    void signupGrantZeroUsesSensibleWording() throws Exception {
        when(creditPolicy.signupGrant()).thenReturn(0);
        String html = mvc.perform(get("/r/rules")).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("送 0 點"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("目前暫無首次登入贈點"));
    }

    /**
     * 邀請獎勵為 0 時，頁面不得出現「+0 點」這種文案，須改用講得通的說法。
     *
     * <p>破壞性驗證：拿掉 0 的文案分支（一律回傳「每位 +X 點」）→
     * 本測試會抓到「+0 點」而變紅。</p>
     */
    @Test
    void referralRewardZeroUsesSensibleWording() throws Exception {
        when(creditPolicy.referralReward()).thenReturn(0);
        String html = mvc.perform(get("/r/rules")).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("+0 點"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("目前暫無邀請獎勵"));
    }

    /**
     * 獎勵為 0 時的文案必須精確：人數會計入（但要講清楚是<b>被邀者首次登入之後</b>），
     * 點數則暫停。
     *
     * <p>{@code ReferralService.stats} 的人數是「帳本 REFERRAL 的 note」與
     * {@code reader.referred_by} 的聯集，所以「成功邀請仍會被計入人數」真的成立，
     * 文案可以講。但<b>獎勵為 0 時帳本那一邊完全不寫</b>（{@code rewardFor} 刻意
     * 不占用冪等鍵），聯集只剩 {@code referred_by}，而它是在被邀者<b>首次登入建立
     * 帳戶</b>時才寫入，不是在他點確認信的那一刻——只寫「成功邀請仍會被記錄」
     * 仍然是承諾了程式在這個設定下不保證的時序。因此本測試要求文案同時具備三件事：
     * 說明暫停發放、寫出「首次登入」這個條件、不承諾暫停期間點數還會累積。</p>
     *
     * <p>破壞性驗證：把 0 值文案裡的「並首次登入」刪掉 → 本測試變紅。</p>
     */
    @Test
    void zeroRewardCopyStatesExactlyWhatIsRecorded() throws Exception {
        when(creditPolicy.referralReward()).thenReturn(0);
        String html = mvc.perform(get("/r/rules")).andReturn().getResponse().getContentAsString();

        // 仍要說明目前的狀態，不能靠整段刪掉來「通過」
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("暫停發放"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("邀請人數"),
            "沒有告訴讀者人數仍會計入——那是 V9 之後真的成立的事，不說反而低估了行為");
        // 必須斷言「整句片段」而不是孤立的「首次登入」四個字：/r/rules 一定會渲染
        // <!--SIGNUP_GRANT_LINE-->，而 signupGrantLine 的兩個分支都含「首次登入」
        //（「首次登入送 N 點」／「目前暫無首次登入贈點」），setUp 又把 signupGrant()
        // 設成 77——所以 html.contains("首次登入") 無論 referralRewardNote(0) 回傳
        // 什麼都會通過，是一條恆真的斷言，把「並首次登入」刪掉也照樣全綠。
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("並首次登入後"),
            "承諾了程式不保證的時序：獎勵為 0 時帳本不寫，人數只能靠 referred_by，"
                + "而它是被邀者首次登入建帳時才寫入；只說「成功邀請就會被記錄」"
                + "會讓讀者盯著還沒成長的數字以為壞了");
        // 暫停期間點數確實不會累積，這些說法一律不成立
        for (String promise : java.util.List.of("點數仍會累計", "仍會獲得點數", "仍會拿到點數", "點數照樣累計")) {
            org.junit.jupiter.api.Assertions.assertFalse(html.contains(promise),
                "獎勵為 0 時承諾了「" + promise + "」，但 rewardFor 根本不寫帳本");
        }
    }

    /**
     * 所有已發布 PREMIUM 文章價格相同時，只顯示單一數字，不得寫成「20–20 點」。
     *
     * <p>「20–20 點」讀起來像壞掉，而它其實只是把 min／max 的實作細節漏給讀者看。
     * 規則頁是點數機制的可信度來源，這種字會直接扣掉可信度。</p>
     */
    @Test
    void singleCostIsNotRenderedAsARangeOfItself() throws Exception {
        givenPublishedPremiumCostRange(20, 20);
        String html = mvc.perform(get("/r/rules")).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("目前每篇 20 點"),
            "min == max 時沒有收斂成單一數字");
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("20–20"),
            "min == max 時仍寫成區間「20–20」，讀起來像壞掉");
    }

    /**
     * 站上還沒有任何已發布 PREMIUM 文章時，退回全域預設並標明是「通常」。
     *
     * <p>這是<b>唯一</b>合法使用 {@code CreditPolicy.premiumCost()} 的情況：此時沒有任何
     * 「實際扣款額」存在可供顯示，而全域預設正是後台建立下一篇 PREMIUM 時會被預填的值，
     * 確實是讀者接下來最可能遇到的價格。用「通常」而非「目前」，因為它是預估不是事實。</p>
     */
    @Test
    void fallsBackToGlobalDefaultWhenNoPublishedPremiumArticleExists() throws Exception {
        // 聚合查詢在零列時回一列而兩欄皆 NULL
        givenPublishedPremiumCostRange(null, null);
        String html = mvc.perform(get("/r/rules")).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("通常每篇 33 點"),
            "沒有文章可統計時應退回全域預設，並用「通常」標示這是參考值");
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("實際點數以各篇文章頁顯示為準"),
            "沒有指出實際點數要看文章頁");
    }

    /** 不論走哪個分支，「實際點數以各篇文章頁顯示為準」都必須在（區間也不等於逐篇價格） */
    @Test
    void alwaysPointsReadersAtThePerArticlePrice() throws Exception {
        String html = mvc.perform(get("/r/rules")).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("實際點數以各篇文章頁顯示為準"),
            "顯示區間時仍必須指出實際點數要看文章頁——區間不告訴讀者某一篇要多少");
    }

    /** 佔位符必須被實際內容取代，不得讓 HTML 註解字面殘留在回應裡 */
    @Test
    void noTemplatePlaceholderIsLeftUnfilled() throws Exception {
        String html = mvc.perform(get("/r/rules")).andReturn().getResponse().getContentAsString();
        for (String placeholder : java.util.List.of(
                "<!--NAV_LINKS-->", "<!--SIGNUP_GRANT_LINE-->", "<!--SIGNUP_GRANT_NOTE-->",
                "<!--PREMIUM_COST_PHRASE-->", "<!--REFERRAL_REWARD_LINE-->",
                "<!--REFERRAL_REWARD_NOTE-->", "<!--VIP_DAYS-->", "<!--LAST_UPDATED-->")) {
            org.junit.jupiter.api.Assertions.assertFalse(html.contains(placeholder),
                "佔位符 " + placeholder + " 不得殘留在回應中");
        }
    }

    /**
     * VIP 段落必須講明「到期後 VIP 期間讀過的文章仍永久免費」。
     *
     * <p>這是 {@code AccessDecisionService.recordAccess} 已經成立的行為
     * （VIP 閱讀會寫一筆 {@code cost=0} 的 {@code article_access}，到期後仍命中
     * ALREADY_UNLOCKED）。對讀者有利、講出來只會加分，不講反而讓人以為 VIP
     * 一到期就全部鎖回去。</p>
     */
    @Test
    void vipSectionStatesReadArticlesStayFreeAfterExpiry() throws Exception {
        String html = mvc.perform(get("/r/rules")).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(
            html.contains("VIP 到期後，VIP 期間讀過的文章仍然永久免費"),
            "VIP 段落沒有說明到期後已讀文章仍免費");
    }
}
