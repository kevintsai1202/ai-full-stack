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
        "READER_JWT_SECRET", "dev-reader-jwt-secret-change-me-32chars");

    private final boolean allowInsecureDevSecrets;
    private final Map<String, String> secrets;

    /** 注入三個安全邊界使用的祕密與開發 opt-in。 */
    public DeploymentSecretValidator(
            @Value("${app.security.allow-insecure-dev-secrets:false}") boolean allowInsecureDevSecrets,
            @Value("${app.admin-api-key}") String adminApiKey,
            @Value("${app.unsubscribe-secret}") String unsubscribeSecret,
            @Value("${app.reader.jwt-secret}") String readerJwtSecret) {
        this.allowInsecureDevSecrets = allowInsecureDevSecrets;
        this.secrets = new LinkedHashMap<>();
        this.secrets.put("ADMIN_API_KEY", adminApiKey);
        this.secrets.put("UNSUBSCRIBE_SECRET", unsubscribeSecret);
        this.secrets.put("READER_JWT_SECRET", readerJwtSecret);
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
                "安全設定錯誤：ADMIN_API_KEY、UNSUBSCRIBE_SECRET、READER_JWT_SECRET 必須使用不同祕密");
        }
    }

    /** 建立不包含祕密內容的安全錯誤訊息，避免祕密被寫進啟動日誌。 */
    private IllegalStateException invalid(String name, String reason) {
        return new IllegalStateException(
            "安全設定錯誤：" + name + " " + reason
                + "；本機開發才可明確設定 APP_ALLOW_INSECURE_DEV_SECRETS=true");
    }
}
