package world.springai.survey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 後台參數設定（spec §7、§9.1）。
 *
 * <p>參數存 DB 的唯一理由就是「改完立即生效、不必重新部署」，
 * 而在此之前沒有任何端點可以改——設定在資料庫裡卻改不動，等於還是寫死的。</p>
 *
 * <p><b>置於根 package 的理由</b>：這些參數跨越多個領域（點數給
 * {@code reader}、參與度門檻給 {@code audience}），不屬於任何單一 package。
 * {@link AppSettingService} 在根 package 也是同一個理由。</p>
 */
@RestController
public class AdminSettingController {

    private static final Logger log = LoggerFactory.getLogger(AdminSettingController.class);

    /**
     * 單一可調參數的允許區間（閉區間，{@code min} 與 {@code max} 本身都合法）。
     *
     * @param min 允許的最小值
     * @param max 允許的最大值
     */
    record Bound(int min, int max) {}

    /**
     * 點數類參數的共用上限。
     *
     * <p><b>為什麼三個點數參數共用同一個常數、而不是各寫一個數字</b>：它們限制的
     * 是同一件事——「一位讀者手上可能出現的點數量級」。初始贈點的預設值是 300，
     * 10000 已是 33 倍；三者要有意義就必須維持同一個量級（例如把邀請獎勵的上限
     * 訂得比單篇成本的上限低，就等於宣告「邀請永遠賺不回一篇文章」，那是營運政策
     * 而不是防打錯位數的護欄）。共用一個具名常數才能讓這個關聯關係看得見；
     * 三處各寫 {@code 10000} 反而是真正的魔術數字——日後調整時很容易只改到一處。</p>
     *
     * <p>超過這個量級的輸入實務上只有一種來源：位數打錯（想輸 300 打成 300000）。
     * 而後果不對稱——{@code credit.signup_grant} 打錯會讓之後每位新讀者拿到天文
     * 數字的點數（且點數不過期、規則調整也不回收，見 {@code /r/rules} 的承諾，
     * 事後無法清乾淨）；{@code credit.premium_cost} 打錯會讓單篇成本超過任何人
     * 可能累積的餘額，等於把全站進階內容鎖死。</p>
     *
     * <p><b>公開給 {@code newsletter.CampaignService} 共用</b>：
     * {@code campaign.credit_cost}（單篇文章可自訂的解鎖成本，
     * {@code CreditPolicy.costOf()} 會優先採用）是與這裡完全同一類風險——
     * 打錯位數會把單篇文章的解鎖成本鎖到任何讀者都不可能湊到的天文數字。
     * 兩者共用同一個常數而非各寫一份 10000，是刻意的：它們限制的是同一件事
     * （一位讀者手上可能出現／需要湊到的點數量級），各自維護一份遲早只會改到一邊。
     * {@code newsletter} 匯入根 package 的型別不受套件分層限制（{@code PackageDependencyTest}
     * 只禁止 {@code newsletter} 依賴 {@code reader}），故直接共用常數而非另立測試互相釘住。</p>
     */
    public static final int CREDIT_MAX = 10_000;

    /**
     * VIP 預設效期的上限：十年。
     *
     * <p>{@code vipExpiresAt} 是「現在 + 天數」算出來的，過大的天數會產生荒謬的
     * 到期日（且本系統刻意沒有 VIP 自動降級排程，那筆資料會一直留著）。十年
     * 遠超過任何合理的授予期間，同時仍容納「等同永久」的營運用法。</p>
     */
    private static final int VIP_MAX_DAYS = 3_650;

    /**
     * 可調參數白名單：鍵 → 允許的區間。
     *
     * <p><b>白名單是必要的</b>：沒有它，這個端點就變成「往 app_setting 寫
     * 任意鍵值」的通用寫入口。那不只是資料髒污——若日後有任何功能改讀
     * app_setting 的某個鍵，這個洞就成了行為注入點。</p>
     *
     * <p><b>最小值各不相同</b>（與 {@code CreditPolicy} 的下限保護對應）：
     * premium_cost 與 vip.default_days 為 0 會造成權限外洩，故最小 1；
     * signup_grant 與 referral_reward 為 0 是合法的「關閉贈點」設定，故最小 0。</p>
     *
     * <p><b>最大值只擋在這個入口，{@code CreditPolicy} 刻意不跟著夾上限</b>：
     * 下限之所以在兩邊都有（此處擋、{@code CreditPolicy} 再夾一次），是因為
     * app_setting 的值可能來自 SQL 直接寫入或舊資料，而下限外的值會造成
     * <b>權限外洩</b>（成本 0 等於全站免費），那是必須 fail-safe 的方向。
     * 上限外的值造成的是「太貴／太多」，不會外洩內容；若 {@code CreditPolicy}
     * 也夾上限，就會出現「後台存了 50000、實際生效 10000」這種頁面說 A 實際做 B
     * 的落差——那正是 {@code CreditPolicy} 這一層存在的目的所要避免的。
     * 因此上限只在寫入口擋，讀取端如實反映資料庫裡的值。</p>
     *
     * <p>刻意不加 {@code private}：{@code AdminSettingControllerTest} 需要直接讀取
     * 這個欄位，與 {@link #DISPLAY_DEFAULTS}、{@link #ordered()} 的 keySet 互相釘住
     * （三者理應描述同一批可調參數，只往其中一個加鍵會讓另外兩個悄悄漏掉）。</p>
     */
    static final Map<String, Bound> ADJUSTABLE = Map.ofEntries(
        Map.entry(AppSettingService.CREDIT_SIGNUP_GRANT, new Bound(0, CREDIT_MAX)),
        Map.entry(AppSettingService.CREDIT_PREMIUM_COST, new Bound(1, CREDIT_MAX)),
        Map.entry(AppSettingService.CREDIT_REFERRAL_REWARD, new Bound(0, CREDIT_MAX)),
        Map.entry(AppSettingService.CREDIT_SURVEY_VOTE_REWARD, new Bound(0, CREDIT_MAX)),
        Map.entry(AppSettingService.CREDIT_REFERRAL_INVITEE_REWARD, new Bound(0, CREDIT_MAX)),
        Map.entry(AppSettingService.CREDIT_REFERRAL_MILESTONE_3, new Bound(0, CREDIT_MAX)),
        Map.entry(AppSettingService.CREDIT_REFERRAL_MILESTONE_5, new Bound(0, CREDIT_MAX)),
        Map.entry(AppSettingService.CREDIT_REFERRAL_MILESTONE_10, new Bound(0, CREDIT_MAX)),
        Map.entry(AppSettingService.REFERRAL_DAILY_AUTO_APPROVE_LIMIT, new Bound(1, 100)),
        Map.entry(AppSettingService.REFERRAL_VELOCITY_REVIEW_THRESHOLD, new Bound(2, 50)),
        Map.entry(AppSettingService.VIP_DEFAULT_DAYS, new Bound(1, VIP_MAX_DAYS)));

    /**
     * 各參數在查無設定時顯示的預設值（與 {@code CreditPolicy} 的後備值一致）。
     *
     * <p>刻意不加 {@code private}：理由與 {@link #ADJUSTABLE} 相同。</p>
     */
    static final Map<String, Integer> DISPLAY_DEFAULTS = Map.ofEntries(
        Map.entry(AppSettingService.CREDIT_SIGNUP_GRANT, 300),
        Map.entry(AppSettingService.CREDIT_PREMIUM_COST, 10),
        Map.entry(AppSettingService.CREDIT_REFERRAL_REWARD, 100),
        Map.entry(AppSettingService.CREDIT_SURVEY_VOTE_REWARD, 5),
        Map.entry(AppSettingService.CREDIT_REFERRAL_INVITEE_REWARD, 20),
        Map.entry(AppSettingService.CREDIT_REFERRAL_MILESTONE_3, 50),
        Map.entry(AppSettingService.CREDIT_REFERRAL_MILESTONE_5, 100),
        Map.entry(AppSettingService.CREDIT_REFERRAL_MILESTONE_10, 300),
        Map.entry(AppSettingService.REFERRAL_DAILY_AUTO_APPROVE_LIMIT, 10),
        Map.entry(AppSettingService.REFERRAL_VELOCITY_REVIEW_THRESHOLD, 3),
        Map.entry(AppSettingService.VIP_DEFAULT_DAYS, 365));

    private final AdminKeyGuard guard;
    private final AppSettingService settings;

    /** 注入金鑰守衛與參數服務 */
    public AdminSettingController(AdminKeyGuard guard, AppSettingService settings) {
        this.guard = guard;
        this.settings = settings;
    }

    /**
     * 單一參數對後台的完整描述：目前值與允許區間。
     *
     * <p><b>為什麼界限要隨值一起回傳</b>：{@code admin.html} 需要替
     * {@code <input type=number>} 設 {@code min} / {@code max}，若那些數字由前端
     * 自己寫一份，就會出現「頁面允許輸入、後端回 400」或更糟的「頁面擋住、
     * 後端其實允許」——同一個規則兩份實作，遲早不同步。界限的唯一來源是
     * {@link #ADJUSTABLE}，前端只負責顯示。</p>
     *
     * @param value 目前生效的值（查無設定時為顯示預設值）
     * @param min   允許的最小值
     * @param max   允許的最大值
     */
    public record SettingView(int value, int min, int max) {}

    /** 讀取全部可調參數的目前值與允許區間 */
    @GetMapping("/api/admin/settings")
    public Map<String, SettingView> list(
            @RequestHeader(value = "X-Admin-Key", required = false) String key) {
        guard.verify(key);
        return currentSettings();
    }

    /**
     * 讀取全部可調參數的目前值（不驗證金鑰）。
     *
     * <p>抽出來讓 {@link #update} 可以在寫入後直接組回應，而不必再呼叫
     * {@link #list} 對同一把已經驗證過的金鑰重驗一次。</p>
     */
    private Map<String, SettingView> currentSettings() {
        // LinkedHashMap 保持固定順序，讓後台欄位不會每次重新載入就跳動
        Map<String, SettingView> result = new LinkedHashMap<>();
        for (String settingKey : ordered()) {
            Bound bound = ADJUSTABLE.get(settingKey);
            result.put(settingKey, new SettingView(
                settings.getInt(settingKey, DISPLAY_DEFAULTS.get(settingKey)),
                bound.min(), bound.max()));
        }
        return result;
    }

    /**
     * 更新參數（可一次多筆）。
     *
     * <p><b>先全部驗證再全部寫入</b>：部分成功會讓後台顯示「已儲存」卻只改了
     * 一半，而使用者無從得知哪一筆沒進去。</p>
     */
    @PutMapping("/api/admin/settings")
    public Map<String, SettingView> update(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestBody Map<String, String> updates) {
        guard.verify(key);

        // 第一遍：全部驗證
        Map<String, Integer> validated = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            Bound bound = ADJUSTABLE.get(entry.getKey());
            if (bound == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "不是可調參數：" + entry.getKey());
            }
            int value;
            try {
                value = Integer.parseInt(entry.getValue().trim());
            } catch (NumberFormatException | NullPointerException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    entry.getKey() + " 必須是整數，收到：" + entry.getValue());
            }
            // 上下限用同一則訊息並寫明整個允許區間：操作者看到「不得小於 1」時
            // 仍要自己猜上限在哪，而 api() 會把 reason 直接顯示給他，
            // 一次講完區間可以省掉一輪試錯。
            if (value < bound.min() || value > bound.max()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    entry.getKey() + " 必須在 " + bound.min() + " 到 " + bound.max()
                        + " 之間，收到：" + value);
            }
            validated.put(entry.getKey(), value);
        }

        // 第二遍：全部寫入，包在單一交易內（見 AppSettingService.setAll 的說明）
        settings.setAll(validated);
        log.info("後台更新參數：{}", validated);

        return currentSettings();
    }

    /**
     * 固定的參數顯示順序。
     *
     * <p>刻意不加 {@code private}：理由與 {@link #ADJUSTABLE} 相同——
     * 測試需要直接比對這份順序清單的 keySet 與 {@link #ADJUSTABLE}／
     * {@link #DISPLAY_DEFAULTS} 是否一致。</p>
     */
    java.util.List<String> ordered() {
        return java.util.List.of(
            AppSettingService.CREDIT_SIGNUP_GRANT,
            AppSettingService.CREDIT_PREMIUM_COST,
            AppSettingService.CREDIT_REFERRAL_REWARD,
            AppSettingService.CREDIT_SURVEY_VOTE_REWARD,
            AppSettingService.CREDIT_REFERRAL_INVITEE_REWARD,
            AppSettingService.CREDIT_REFERRAL_MILESTONE_3,
            AppSettingService.CREDIT_REFERRAL_MILESTONE_5,
            AppSettingService.CREDIT_REFERRAL_MILESTONE_10,
            AppSettingService.REFERRAL_DAILY_AUTO_APPROVE_LIMIT,
            AppSettingService.REFERRAL_VELOCITY_REVIEW_THRESHOLD,
            AppSettingService.VIP_DEFAULT_DAYS);
    }
}
