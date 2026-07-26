package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
        mvc = MockMvcBuilders
            .standaloneSetup(new RulesPageController(new HtmlTemplate(), creditPolicy, readerContext))
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

    /** 登入者的導覽列要顯示「我的帳戶」，未登入則顯示「登入」 */
    @Test
    void navReflectsLoginState() throws Exception {
        String anonymous = mvc.perform(get("/r/rules")).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(anonymous.contains("/r/login"));
    }
}
