package world.springai.survey;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HMAC 退訂 token 簽發與驗證測試 */
class UnsubscribeTokenServiceTest {

    private final UnsubscribeTokenService svc = new UnsubscribeTokenService("secret-A");

    /** 同 email 簽出的 token 應通過驗證 */
    @Test
    void signedTokenVerifies() {
        String token = svc.sign("User@Example.com");
        assertTrue(svc.verify("User@Example.com", token));
    }

    /** email 大小寫/前後空白不影響驗證（正規化） */
    @Test
    void verifyIsCaseAndTrimInsensitive() {
        String token = svc.sign("user@example.com");
        assertTrue(svc.verify("  USER@EXAMPLE.COM  ", token));
    }

    /** 竄改的 token 不通過 */
    @Test
    void tamperedTokenRejected() {
        assertFalse(svc.verify("user@example.com", "not-a-valid-token"));
    }

    /** 不同秘鑰簽出的 token 互不通過 */
    @Test
    void differentSecretRejected() {
        String token = new UnsubscribeTokenService("secret-B").sign("user@example.com");
        assertFalse(svc.verify("user@example.com", token));
    }

    /** 空 token 不通過、不丟例外 */
    @Test
    void blankTokenRejected() {
        assertFalse(svc.verify("user@example.com", null));
        assertFalse(svc.verify("user@example.com", ""));
    }
}
