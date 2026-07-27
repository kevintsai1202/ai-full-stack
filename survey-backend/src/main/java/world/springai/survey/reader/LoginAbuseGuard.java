package world.springai.survey.reader;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Locale;

/**
 * Magic Link 公開寄信端點的來源 IP 與全域節流。
 *
 * <p>既有 {@link LoginTokenService} 保留每個 Email 的上限；本守門補上攻擊者輪替
 * Email 時仍無法繞過的來源 IP 與全站配額。紀錄放在 PostgreSQL，而不是 JVM
 * 記憶體，確保 Zeabur 多實例及服務重啟後仍有效。</p>
 */
@Service
public class LoginAbuseGuard {

    private final LoginRequestAttemptRepository repository;
    private final int windowMinutes;
    private final int ipLimit;
    private final int globalLimit;

    /** 注入資料存取層與節流門檻。 */
    public LoginAbuseGuard(
            LoginRequestAttemptRepository repository,
            @Value("${app.reader.login-throttle-minutes}") int windowMinutes,
            @Value("${app.reader.login-ip-throttle-count}") int ipLimit,
            @Value("${app.reader.login-global-throttle-count}") int globalLimit) {
        this.repository = repository;
        this.windowMinutes = requirePositive("login-throttle-minutes", windowMinutes);
        this.ipLimit = requirePositive("login-ip-throttle-count", ipLimit);
        this.globalLimit = requirePositive("login-global-throttle-count", globalLimit);
    }

    /**
     * 檢查來源與全站額度並記錄本次允許的請求。
     *
     * @return true 表示可以繼續簽發及寄送，false 表示已達任一上限
     */
    @Transactional
    public boolean tryAcquire(String clientAddress, OffsetDateTime now) {
        OffsetDateTime since = now.minusMinutes(windowMinutes);
        String ipHash = hash(normalizeAddress(clientAddress));

        // 僅需支援目前節流視窗；保留一天方便短期事故調查，之後自動刪除。
        repository.deleteByCreatedAtBefore(now.minusDays(1));
        if (repository.countByIpHashAndCreatedAtAfter(ipHash, since) >= ipLimit) {
            return false;
        }
        if (repository.countByCreatedAtAfter(since) >= globalLimit) {
            return false;
        }

        repository.saveAndFlush(new LoginRequestAttempt(ipHash, now));
        return true;
    }

    /** 將 IPv6 大小寫與空白正規化；取不到位址時共用 unknown bucket，仍可節流。 */
    String normalizeAddress(String clientAddress) {
        if (clientAddress == null || clientAddress.isBlank()) {
            return "unknown";
        }
        return clientAddress.trim().toLowerCase(Locale.ROOT);
    }

    /** 計算不可逆 SHA-256 雜湊，避免資料庫保存原始 IP。 */
    String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] raw = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("找不到 SHA-256 演算法", e);
        }
    }

    /** 部署參數必須為正數，避免誤設為 0 讓所有讀者都無法登入。 */
    private int requirePositive(String name, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " 必須大於 0");
        }
        return value;
    }
}
