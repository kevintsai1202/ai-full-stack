package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 規則頁：數字必須全部來自 CreditPolicy。
 *
 * <p>測試策略刻意用「非典型數值」（77 / 33 / 55 / 111）而非真實的
 * 300 / 10 / 100 / 365。用真實值的話，即使實作把數字寫死在 HTML 裡，
 * 測試也會通過——那種測試什麼都證明不了。</p>
 */
class RulesPageControllerTest {

    private CreditPolicy creditPolicy;
    private ReaderContext readerContext;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        creditPolicy = mock(CreditPolicy.class);
        readerContext = mock(ReaderContext.class);
        when(readerContext.resolve(any())).thenReturn(Optional.empty());
        when(creditPolicy.signupGrant()).thenReturn(77);
        when(creditPolicy.premiumCost()).thenReturn(33);
        when(creditPolicy.referralReward()).thenReturn(55);
        when(creditPolicy.vipDefaultDays()).thenReturn(111);
        // standalone MockMvc 沒有 Spring Boot 的 WebMvcAutoConfiguration，
        // 預設的 StringHttpMessageConverter 用 ISO-8859-1，中文字會變亂碼。
        // 產品程式碼維持 MediaType.TEXT_HTML_VALUE（與套件內其他頁面一致），
        // UTF-8 的保證改由這裡手動註冊一個帶 UTF-8 預設值的 converter 負責——
        // 真實部署時 Spring Boot 會自動註冊等效的 converter，這裡只是補上
        // standalone 環境缺少的那一層，不是多餘的樣板。
        mvc = MockMvcBuilders
            .standaloneSetup(new RulesPageController(new HtmlTemplate(), creditPolicy, readerContext))
            .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8))
            .build();
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

    /** PREMIUM 單篇點數必須來自 CreditPolicy */
    @Test
    void injectsPremiumCost() throws Exception {
        mvc.perform(get("/r/rules")).andExpect(content().string(org.hamcrest.Matchers.containsString("33")));
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
     * 獎勵為 0 時，頁面<b>不得承諾「成功邀請仍會被記錄」</b>——程式不保證這件事。
     *
     * <p>{@code ReferralService.rewardFor} 在 {@code reward <= 0} 時直接 return、
     * 完全不寫帳本，而邀請頁的成效區塊數的正是 {@code credit_txn} 的 REFERRAL 筆數。
     * 於是讀者會在 {@code /r/invite} 同一個回應裡看到「成功邀請仍會被記錄」與
     * 「還沒有人透過你的連結完成訂閱」並列，即使他已經成功邀請五個人。
     * 文案不得對行為做出程式碼不保證的承諾。</p>
     *
     * <p>刻意用「記錄／記下／累計中」這類承諾字眼做黑名單，而不是比對整句：
     * 換句話說也不行，換掉的必須是那個承諾本身。</p>
     */
    @Test
    void zeroRewardCopyMakesNoPromiseThatInvitesAreRecorded() throws Exception {
        when(creditPolicy.referralReward()).thenReturn(0);
        String html = mvc.perform(get("/r/rules")).andReturn().getResponse().getContentAsString();
        for (String promise : java.util.List.of("會被記錄", "仍會記錄", "仍會被記下", "會先記錄", "累計中")) {
            org.junit.jupiter.api.Assertions.assertFalse(html.contains(promise),
                "獎勵為 0 時承諾了程式不保證的「" + promise + "」：邀請不會寫進帳本，成效頁永遠是 0");
        }
        // 仍要說明目前的狀態，不能靠整段刪掉來「通過」
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("暫停發放"));
    }

    /**
     * 規則頁的「每篇 N 點」必須標明是參考值，實際以各篇文章頁為準。
     *
     * <p>本頁顯示的是全域預設 {@code CreditPolicy.premiumCost()}，而 paywall 與
     * {@code UnlockService} 實際扣的是該篇自己的 {@code campaign.credit_cost}；
     * 又因 {@code ck_campaign_premium_cost} 與 {@code validateCreditCost} 都強制
     * PREMIUM 的 {@code credit_cost > 0}，{@code costOf()} 退回全域預設的分支是死碼——
     * 這個數字<b>結構性地</b>不會是實際扣款額。規則頁的存在理由就是點數機制的
     * 可信度來源，帶著結構性錯誤的數字上線比沒有規則頁更傷。</p>
     */
    @Test
    void premiumCostIsPresentedAsTypicalNotExact() throws Exception {
        String html = mvc.perform(get("/r/rules")).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("通常每篇 33 點"),
            "沒有把全域預設標示為「通常」，讀者會以為那就是每篇的實際扣款額");
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("實際點數以各篇文章頁顯示為準"),
            "沒有指出實際點數要看文章頁");
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
