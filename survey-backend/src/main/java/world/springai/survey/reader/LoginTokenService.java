package world.springai.survey.reader;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

/**
 * magic link 登入 token 的簽發與兌換。
 *
 * <p>刻意不用 UnsubscribeTokenService 的無狀態 HMAC：那個簽章沒有到期概念也無法作廢
 * （對退訂連結是正確的特性），但登入 token 必須能到期、能一次性作廢。因此走資料庫。</p>
 *
 * <p>資料庫只存 SHA-256 雜湊，明文 token 僅在簽發當下存在於記憶體並寄進信裡——
 * 資料庫外洩時 token 不可被反推使用。</p>
 */
@Service
public class LoginTokenService {

    /** token 隨機位元組數；32 bytes = 256 bits，足以抵抗暴力猜測 */
    private static final int TOKEN_BYTES = 32;

    /** 密碼學安全的隨機來源 */
    private final SecureRandom random = new SecureRandom();

    private final LoginTokenRepository repository;
    /** magic link 有效分鐘數 */
    private final int ttlMinutes;
    /** 節流視窗內允許的最大封數 */
    private final int throttleCount;
    /** 節流視窗分鐘數 */
    private final int throttleMinutes;

    /** 注入資料存取層與部署設定 */
    public LoginTokenService(LoginTokenRepository repository,
                            @Value("${app.reader.login-token-ttl-minutes}") int ttlMinutes,
                            @Value("${app.reader.login-throttle-count}") int throttleCount,
                            @Value("${app.reader.login-throttle-minutes}") int throttleMinutes) {
        this.repository = repository;
        this.ttlMinutes = ttlMinutes;
        this.throttleCount = throttleCount;
        this.throttleMinutes = throttleMinutes;
    }

    /**
     * 簽發指定用途的 token。
     *
     * @return **明文** token，呼叫端應立即組成連結寄出，不得記錄於日誌
     */
    public String issue(String email, String purpose, OffsetDateTime now) {
        byte[] raw = new byte[TOKEN_BYTES];
        random.nextBytes(raw);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        repository.save(new LoginToken(hash(rawToken), normalize(email), now.plusMinutes(ttlMinutes), purpose));
        return rawToken;
    }

    /** 相容既有呼叫端：未指定用途即為讀者登入 */
    public String issue(String email, OffsetDateTime now) {
        return issue(email, LoginToken.PURPOSE_READER, now);
    }

    /**
     * 兌換 token：驗證存在、未過期、未使用，成功則標記已使用並回傳 email。
     *
     * <p>任何失敗一律回 empty 而不拋例外，也不區分「不存在」與「已使用」——
     * 對外不洩漏 token 的狀態。</p>
     *
     * <p><b>正確性由 {@code markUsedIfUnused} 的回傳值決定，不是這裡的前置檢查。</b>
     * 先查 {@code findByTokenHash} 再依記憶體中的 {@code token.isUsed()} 判斷、
     * 最後 {@code save()} 回去的寫法，在併發下有 TOCTOU 窗口：兩個請求可能都在
     * 檢查當下讀到「未使用」，於是都通過檢查、都呼叫 save，同一個 token 被
     * 兌換兩次。{@code token_hash} 的 UNIQUE 約束只防止插入重複列，防不了對
     * 同一列的重複更新，所以無法倚賴它擋下這個競態。
     * 這裡保留的 {@code token.isUsed()} 檢查只是省一次 UPDATE 的快速路徑，
     * 不得被視為正確性保證——唯一的保證來自 {@code markUsedIfUnused} 這個
     * 帶條件的原子 UPDATE：只有 usedAt 仍是 NULL 時才會更新成功，回傳受影響
     * 筆數。若日後想把這段「簡化」回先查再存，請先讀這段註解。</p>
     */
    public Optional<String> consume(String rawToken, String purpose, OffsetDateTime now) {
        if (!StringUtils.hasText(rawToken)) {
            return Optional.empty();
        }
        Optional<LoginToken> found = repository.findByTokenHash(hash(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        LoginToken token = found.get();
        // 用途不符一律拒絕，且不消耗 token：reader 的連結不得換到 admin 權限
        if (!purpose.equals(token.getPurpose()) || token.isUsed() || token.isExpired(now)) {
            return Optional.empty();
        }
        // 正確性依賴這裡的回傳值，不是上面 isUsed() 的前置檢查：
        // 0 表示這個 token 在原子更新發生前已被別的併發請求兌換走了。
        int updated = repository.markUsedIfUnused(token.getTokenHash(), now);
        if (updated == 0) {
            return Optional.empty();
        }
        return Optional.of(token.getEmail());
    }

    /** 相容既有呼叫端：未指定用途即為讀者登入 */
    public Optional<String> consume(String rawToken, OffsetDateTime now) {
        return consume(rawToken, LoginToken.PURPOSE_READER, now);
    }

    /** 該 email 在節流視窗內是否已達上限（避免服務被當成寄信放大器） */
    public boolean isThrottled(String email, OffsetDateTime now) {
        long recent = repository.countByEmailAndCreatedAtAfter(
            normalize(email), now.minusMinutes(throttleMinutes));
        return recent >= throttleCount;
    }

    /** 計算 token 的 SHA-256 雜湊（Base64 URL-safe 無 padding）；測試需要故為 public */
    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("找不到 SHA-256 演算法", e);
        }
    }

    /** email 正規化：去前後空白並轉小寫，與名單中心的比對基準一致 */
    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
