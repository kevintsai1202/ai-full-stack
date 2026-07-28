package world.springai.survey.reader;

import org.springframework.stereotype.Component;
import world.springai.survey.AppSettingService;
import world.springai.survey.newsletter.Campaign;

/**
 * 點數相關參數的唯一來源。
 *
 * <p><b>為什麼需要這一層</b>：spec §5.11 要求規則頁、{@code /r/me} 與 paywall
 * 提示區塊顯示的點數數字必須與實際扣點一致。若每個呼叫點各自呼叫
 * {@link AppSettingService} 並各自帶後備值，調整參數後就會出現「頁面說 10 點、
 * 實際扣 50 點」這類最傷信任的落差。把讀取集中在此，順帶把「下限保護」也集中，
 * 不讓每個呼叫點各自記得要夾。</p>
 *
 * <p><b>各參數的下限刻意不同</b>：{@link #premiumCost()} 與
 * {@link #vipDefaultDays()} 若為 0 會造成權限外洩（所有 PREMIUM 免費／VIP
 * 授予後立即失效），故夾到 1；{@link #signupGrant()} 與
 * {@link #referralReward()} 為 0 只代表「關閉贈點」，是合法營運設定，
 * 只夾掉負值。把四者一律夾成 ≥ 1 會讓後台無法關閉贈點。</p>
 */
@Component
public class CreditPolicy {

    /** 初始贈點的後備值（查不到設定時採用） */
    static final int DEFAULT_SIGNUP_GRANT = 300;
    /** PREMIUM 單篇解鎖點數的後備值 */
    static final int DEFAULT_PREMIUM_COST = 10;
    /** 邀請成功獎勵的後備值 */
    static final int DEFAULT_REFERRAL_REWARD = 100;
    /** 被邀者確認加碼的後備值 */
    static final int DEFAULT_INVITEE_REWARD = 20;
    /** VIP 預設效期天數的後備值 */
    static final int DEFAULT_VIP_DAYS = 365;

    private final AppSettingService appSettingService;

    /** 注入參數讀寫服務 */
    public CreditPolicy(AppSettingService appSettingService) {
        this.appSettingService = appSettingService;
    }

    /** 首次登入的初始贈點；0 為合法值（關閉贈點），負值夾到 0 */
    public int signupGrant() {
        return Math.max(0, appSettingService.getInt(
            AppSettingService.CREDIT_SIGNUP_GRANT, DEFAULT_SIGNUP_GRANT));
    }

    /**
     * PREMIUM 文章的全域預設解鎖點數。
     *
     * <p>永遠 ≥ 1：0 或負數會讓 {@code credits >= cost} 恆真，等於所有進階內容免費。
     * 這個下限是 paywall 的最後一道防線，不把正確性寄望在後台設定上。</p>
     */
    public int premiumCost() {
        return Math.max(1, appSettingService.getInt(
            AppSettingService.CREDIT_PREMIUM_COST, DEFAULT_PREMIUM_COST));
    }

    /** 邀請成功的獎勵點數；0 為合法值（關閉邀請獎勵），負值夾到 0 */
    public int referralReward() {
        return Math.max(0, appSettingService.getInt(
            AppSettingService.CREDIT_REFERRAL_REWARD, DEFAULT_REFERRAL_REWARD));
    }

    /** 被邀者完成信箱確認後的加碼；0 可關閉雙邊獎勵。 */
    public int referralInviteeReward() {
        return Math.max(0, appSettingService.getInt(
            AppSettingService.CREDIT_REFERRAL_INVITEE_REWARD, DEFAULT_INVITEE_REWARD));
    }

    /** 指定里程碑的額外點數；未知里程碑不發點。 */
    public int referralMilestoneReward(int milestone) {
        String key = switch (milestone) {
            case 3 -> AppSettingService.CREDIT_REFERRAL_MILESTONE_3;
            case 5 -> AppSettingService.CREDIT_REFERRAL_MILESTONE_5;
            case 10 -> AppSettingService.CREDIT_REFERRAL_MILESTONE_10;
            default -> null;
        };
        int fallback = switch (milestone) {
            case 3 -> 50;
            case 5 -> 100;
            case 10 -> 300;
            default -> 0;
        };
        return key == null ? 0 : Math.max(0, appSettingService.getInt(key, fallback));
    }

    /** 每日自動核准上限，至少 1 人，避免錯誤設定讓所有邀請停擺。 */
    public int referralDailyLimit() {
        return Math.max(1, appSettingService.getInt(
            AppSettingService.REFERRAL_DAILY_AUTO_APPROVE_LIMIT, 10));
    }

    /** 十分鐘速度審核門檻，至少 2 人。 */
    public int referralVelocityThreshold() {
        return Math.max(2, appSettingService.getInt(
            AppSettingService.REFERRAL_VELOCITY_REVIEW_THRESHOLD, 3));
    }

    /** VIP 預設效期天數；永遠 ≥ 1，否則後台授予 VIP 後會立即過期 */
    public int vipDefaultDays() {
        return Math.max(1, appSettingService.getInt(
            AppSettingService.VIP_DEFAULT_DAYS, DEFAULT_VIP_DAYS));
    }

    /**
     * 取得該文章的解鎖成本：文章自訂值優先，未設定（0）時退回全域預設。
     *
     * <p>結果永遠 ≥ 1。PREMIUM 卻成本為 0 理論上已被資料庫的
     * {@code ck_campaign_premium_cost} 擋掉，但該 CHECK 只檢查
     * {@code tier <> 'PREMIUM' OR credit_cost > 0}，遇到 {@code tier} 大小寫
     * 不符時會整條放行，所以這裡不能假設資料庫已經把關。</p>
     */
    public int costOf(Campaign campaign) {
        if (campaign.getCreditCost() > 0) {
            return campaign.getCreditCost();
        }
        return premiumCost();
    }
}
