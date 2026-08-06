package world.springai.survey;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 部署祕密啟動檢查。
 *
 * <p>設定檔保留開發預設值是為了讓範例容易理解，但服務預設不允許以那些值啟動。
 * 本機開發必須明確設定 {@code APP_ALLOW_INSECURE_DEV_SECRETS=true}；部署環境沒有
 * 這個 opt-in 時，只要祕密空白、太短、彼此重複或仍是公開預設值就直接停止啟動。</p>
 */
@Component
public class DeploymentSecretValidator implements SmartInitializingSingleton {

    private static final int MIN_SECRET_LENGTH = 32;

    private static final Map<String, String> INSECURE_DEFAULTS = Map.of(
        "ADMIN_API_KEY", "dev-admin-key",
        "UNSUBSCRIBE_SECRET", "dev-unsub-secret",
        "READER_JWT_SECRET", "dev-reader-jwt-secret-change-me-32chars",
        "ADMIN_JWT_SECRET", "dev-admin-jwt-secret-change-me-32chars");

    private final boolean allowInsecureDevSecrets;
    private final Map<String, String> secrets;

    /**
     * 注入四個安全邊界使用的祕密與開發 opt-in。
     *
     * <p><b>{@code ADMIN_JWT_SECRET} 為什麼一定要在這裡</b>：它簽的是
     * {@code admin_session} cookie，而 {@code AdminKeyGuard} 認這枚 cookie 就等於認可
     * 全部管理端點。{@code application.yml} 的開發預設值是已 commit 進版控的公開字串，
     * 漏設環境變數時服務會<b>正常啟動、登入正常、零症狀</b>，但任何人都能用那個公開字串
     * 自簽一枚 cookie。少了這一列，這裡就是唯一一個 fail-open 的祕密。</p>
     *
     * <p>它同時受下方的 distinct 檢查保護：{@code ADMIN_JWT_SECRET} 與
     * {@code READER_JWT_SECRET} 刻意分離（spec §3.2「切開爆炸半徑」），
     * 設成同一把會讓兩個信任域合而為一。</p>
     */
    public DeploymentSecretValidator(
            @Value("${app.security.allow-insecure-dev-secrets:false}") boolean allowInsecureDevSecrets,
            @Value("${app.admin-api-key}") String adminApiKey,
            @Value("${app.unsubscribe-secret}") String unsubscribeSecret,
            @Value("${app.reader.jwt-secret}") String readerJwtSecret,
            @Value("${app.admin.jwt-secret}") String adminJwtSecret) {
        this.allowInsecureDevSecrets = allowInsecureDevSecrets;
        this.secrets = new LinkedHashMap<>();
        this.secrets.put("ADMIN_API_KEY", adminApiKey);
        this.secrets.put("UNSUBSCRIBE_SECRET", unsubscribeSecret);
        this.secrets.put("READER_JWT_SECRET", readerJwtSecret);
        this.secrets.put("ADMIN_JWT_SECRET", adminJwtSecret);
    }

    /** Spring 完成單例建構後立即驗證；失敗會讓應用程式無法開始接受流量。 */
    @Override
    public void afterSingletonsInstantiated() {
        validate();
    }

    /** 驗證部署祕密；獨立方法方便單元測試覆蓋所有失敗條件。 */
    void validate() {
        if (allowInsecureDevSecrets) {
            return;
        }

        for (Map.Entry<String, String> entry : secrets.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (!StringUtils.hasText(value)) {
                throw invalid(name, "不得空白");
            }
            if (value.length() < MIN_SECRET_LENGTH) {
                throw invalid(name, "長度至少需要 " + MIN_SECRET_LENGTH + " 字元");
            }
            if (value.equals(INSECURE_DEFAULTS.get(name))) {
                throw invalid(name, "仍使用公開的開發預設值");
            }
        }

        if (secrets.values().stream().distinct().count() != secrets.size()) {
            throw new IllegalStateException(
                "安全設定錯誤：" + String.join("、", secrets.keySet()) + " 必須使用不同祕密");
        }
    }

    /** 建立不包含祕密內容的安全錯誤訊息，避免祕密被寫進啟動日誌。 */
    private IllegalStateException invalid(String name, String reason) {
        return new IllegalStateException(
            "安全設定錯誤：" + name + " " + reason
                + "；本機開發才可明確設定 APP_ALLOW_INSECURE_DEV_SECRETS=true");
    }
}
