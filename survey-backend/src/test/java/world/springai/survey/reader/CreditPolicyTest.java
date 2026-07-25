package world.springai.survey.reader;

import org.junit.jupiter.api.Test;
import world.springai.survey.AppSettingService;
import world.springai.survey.newsletter.Campaign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CreditPolicy 的測試重點不是「讀得到設定值」，而是**下限保護的方向**。
 *
 * <p>每個參數的下限不同，而且理由不同：premiumCost 與 vipDefaultDays 若為 0
 * 會造成權限外洩（所有 PREMIUM 免費／VIP 立即失效），signupGrant 與
 * referralReward 為 0 只是「不送點」，是合法的營運設定。把四者都夾成 ≥ 1
 * 會讓後台無法關閉贈點；都不夾則會外洩。所以逐一驗證。</p>
 */
class CreditPolicyTest {

    /**
     * 建一個「指定鍵回傳指定值、其餘鍵回傳呼叫端預設值」的 AppSettingService 假物件。
     *
     * <p>第一個 stub 讓未指定的鍵回傳第 2 個引數（呼叫端的 defaultValue），
     * 忠實模擬 {@link AppSettingService#getInt} 查無此鍵時的真實行為。
     * 沒有這一行的話，Mockito 對未 stub 的 int 方法一律回 <b>0</b>——那會讓
     * 所有參數看起來都是 0，測試變成在驗證 Mockito 的預設值而不是 CreditPolicy。</p>
     */
    private AppSettingService settingsReturning(String key, int value) {
        AppSettingService settings = mock(AppSettingService.class);
        when(settings.getInt(anyString(), anyInt()))
            .thenAnswer(invocation -> invocation.getArgument(1, Integer.class));
        when(settings.getInt(eq(key), anyInt())).thenReturn(value);
        return settings;
    }

    /** 建一篇指定 tier 與成本的文章；沿用 AccessDecisionServiceTest 的建立方式 */
    private Campaign article(String tier, int cost) {
        Campaign c = new Campaign("主旨", "# 內容", "<h1>內容</h1>", null, null, "now", null, 0, "sent");
        c.setTier(tier);
        c.setCreditCost(cost);
        return c;
    }

    /** 正常設定值應原樣回傳 */
    @Test
    void readsConfiguredValues() {
        AppSettingService settings = mock(AppSettingService.class);
        when(settings.getInt(eq(AppSettingService.CREDIT_SIGNUP_GRANT), anyInt())).thenReturn(300);
        when(settings.getInt(eq(AppSettingService.CREDIT_PREMIUM_COST), anyInt())).thenReturn(10);
        when(settings.getInt(eq(AppSettingService.CREDIT_REFERRAL_REWARD), anyInt())).thenReturn(100);
        when(settings.getInt(eq(AppSettingService.VIP_DEFAULT_DAYS), anyInt())).thenReturn(365);

        CreditPolicy policy = new CreditPolicy(settings);

        assertEquals(300, policy.signupGrant());
        assertEquals(10, policy.premiumCost());
        assertEquals(100, policy.referralReward());
        assertEquals(365, policy.vipDefaultDays());
    }

    /** premiumCost 設成 0 必須被夾到 1：否則所有 PREMIUM 文章變免費 */
    @Test
    void premiumCostIsClampedToAtLeastOne() {
        CreditPolicy policy = new CreditPolicy(settingsReturning(AppSettingService.CREDIT_PREMIUM_COST, 0));
        assertEquals(1, policy.premiumCost());
    }

    /** premiumCost 設成負數同樣夾到 1 */
    @Test
    void negativePremiumCostIsClampedToAtLeastOne() {
        CreditPolicy policy = new CreditPolicy(settingsReturning(AppSettingService.CREDIT_PREMIUM_COST, -5));
        assertEquals(1, policy.premiumCost());
    }

    /** vipDefaultDays 設成 0 必須被夾到 1：0 天等於授予後立刻失效 */
    @Test
    void vipDaysIsClampedToAtLeastOne() {
        CreditPolicy policy = new CreditPolicy(settingsReturning(AppSettingService.VIP_DEFAULT_DAYS, 0));
        assertEquals(1, policy.vipDefaultDays());
    }

    /** signupGrant 為 0 是合法設定（關閉贈點），不可被夾成 1 */
    @Test
    void signupGrantZeroIsAllowed() {
        CreditPolicy policy = new CreditPolicy(settingsReturning(AppSettingService.CREDIT_SIGNUP_GRANT, 0));
        assertEquals(0, policy.signupGrant());
    }

    /** signupGrant 為負數則夾到 0：負的贈點會讓新讀者一開始就是負餘額 */
    @Test
    void negativeSignupGrantIsClampedToZero() {
        CreditPolicy policy = new CreditPolicy(settingsReturning(AppSettingService.CREDIT_SIGNUP_GRANT, -100));
        assertEquals(0, policy.signupGrant());
    }

    /** referralReward 為 0 是合法設定（關閉邀請獎勵） */
    @Test
    void referralRewardZeroIsAllowed() {
        CreditPolicy policy = new CreditPolicy(settingsReturning(AppSettingService.CREDIT_REFERRAL_REWARD, 0));
        assertEquals(0, policy.referralReward());
    }

    /** 文章自訂成本優先於全域預設 */
    @Test
    void perArticleCostWinsOverGlobalDefault() {
        CreditPolicy policy = new CreditPolicy(settingsReturning(AppSettingService.CREDIT_PREMIUM_COST, 10));
        assertEquals(50, policy.costOf(article(Campaign.TIER_PREMIUM, 50)));
    }

    /** 文章成本為 0 時退回全域預設，且仍受 ≥ 1 保護 */
    @Test
    void zeroArticleCostFallsBackToGlobalDefaultWithFloor() {
        CreditPolicy policy = new CreditPolicy(settingsReturning(AppSettingService.CREDIT_PREMIUM_COST, 0));
        assertEquals(1, policy.costOf(article(Campaign.TIER_PREMIUM, 0)));
    }

    /** 查無設定時採用內建後備值（此路徑不 stub 該鍵，讓 getInt 回傳 defaultValue） */
    @Test
    void fallsBackToBuiltInDefaultsWhenSettingAbsent() {
        AppSettingService settings = mock(AppSettingService.class);
        // 全部鍵都回傳呼叫端給的 defaultValue，模擬 app_setting 內查無此鍵
        when(settings.getInt(anyString(), anyInt()))
            .thenAnswer(invocation -> invocation.getArgument(1, Integer.class));

        CreditPolicy policy = new CreditPolicy(settings);

        assertEquals(CreditPolicy.DEFAULT_SIGNUP_GRANT, policy.signupGrant());
        assertEquals(CreditPolicy.DEFAULT_PREMIUM_COST, policy.premiumCost());
        assertEquals(CreditPolicy.DEFAULT_REFERRAL_REWARD, policy.referralReward());
        assertEquals(CreditPolicy.DEFAULT_VIP_DAYS, policy.vipDefaultDays());
    }
}
