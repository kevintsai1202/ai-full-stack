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
     * 可調參數白名單：鍵 → 允許的最小值。
     *
     * <p><b>白名單是必要的</b>：沒有它，這個端點就變成「往 app_setting 寫
     * 任意鍵值」的通用寫入口。那不只是資料髒污——若日後有任何功能改讀
     * app_setting 的某個鍵，這個洞就成了行為注入點。</p>
     *
     * <p><b>最小值各不相同</b>（與 {@code CreditPolicy} 的下限保護對應）：
     * premium_cost 與 vip.default_days 為 0 會造成權限外洩，故最小 1；
     * signup_grant 與 referral_reward 為 0 是合法的「關閉贈點」設定，故最小 0。</p>
     */
    private static final Map<String, Integer> ADJUSTABLE = Map.of(
        AppSettingService.CREDIT_SIGNUP_GRANT, 0,
        AppSettingService.CREDIT_PREMIUM_COST, 1,
        AppSettingService.CREDIT_REFERRAL_REWARD, 0,
        AppSettingService.VIP_DEFAULT_DAYS, 1);

    /** 各參數在查無設定時顯示的預設值（與 CreditPolicy 的後備值一致） */
    private static final Map<String, Integer> DISPLAY_DEFAULTS = Map.of(
        AppSettingService.CREDIT_SIGNUP_GRANT, 300,
        AppSettingService.CREDIT_PREMIUM_COST, 10,
        AppSettingService.CREDIT_REFERRAL_REWARD, 100,
        AppSettingService.VIP_DEFAULT_DAYS, 365);

    private final AdminKeyGuard guard;
    private final AppSettingService settings;

    /** 注入金鑰守衛與參數服務 */
    public AdminSettingController(AdminKeyGuard guard, AppSettingService settings) {
        this.guard = guard;
        this.settings = settings;
    }

    /** 讀取全部可調參數的目前值 */
    @GetMapping("/api/admin/settings")
    public Map<String, Integer> list(
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
    private Map<String, Integer> currentSettings() {
        // LinkedHashMap 保持固定順序，讓後台欄位不會每次重新載入就跳動
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String settingKey : ordered()) {
            result.put(settingKey, settings.getInt(settingKey, DISPLAY_DEFAULTS.get(settingKey)));
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
    public Map<String, Integer> update(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestBody Map<String, String> updates) {
        guard.verify(key);

        // 第一遍：全部驗證
        Map<String, Integer> validated = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            Integer min = ADJUSTABLE.get(entry.getKey());
            if (min == null) {
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
            if (value < min) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    entry.getKey() + " 不得小於 " + min + "，收到：" + value);
            }
            validated.put(entry.getKey(), value);
        }

        // 第二遍：全部寫入，包在單一交易內（見 AppSettingService.setAll 的說明）
        settings.setAll(validated);
        log.info("後台更新參數：{}", validated);

        return currentSettings();
    }

    /** 固定的參數顯示順序 */
    private java.util.List<String> ordered() {
        return java.util.List.of(
            AppSettingService.CREDIT_SIGNUP_GRANT,
            AppSettingService.CREDIT_PREMIUM_COST,
            AppSettingService.CREDIT_REFERRAL_REWARD,
            AppSettingService.VIP_DEFAULT_DAYS);
    }
}
