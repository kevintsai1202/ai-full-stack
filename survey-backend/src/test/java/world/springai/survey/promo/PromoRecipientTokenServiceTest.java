package world.springai.survey.promo;

import org.junit.jupiter.api.Test;
import world.springai.survey.audience.UnsubscribeTokenService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** 自包含 token：簽發→驗證往返、竄改拒絕、與退訂 token 不可互換 */
class PromoRecipientTokenServiceTest {

    private final PromoRecipientTokenService service = new PromoRecipientTokenService("test-secret");

    @Test
    void 簽發後可驗回正規化email() {
        String token = service.issue("  Alice@Example.COM ");
        assertEquals(Optional.of("alice@example.com"), service.verify(token));
    }

    @Test
    void 竄改任一段即驗證失敗() {
        String token = service.issue("alice@example.com");
        String[] parts = token.split("\\.", 2);
        assertTrue(service.verify("x" + token).isEmpty());
        assertTrue(service.verify(parts[0] + ".AAAA").isEmpty());
        assertTrue(service.verify(parts[0]).isEmpty()); // 缺簽章段
    }

    @Test
    void null與空字串與佔位符一律失敗不拋例外() {
        assertTrue(service.verify(null).isEmpty());
        assertTrue(service.verify("").isEmpty());
        assertTrue(service.verify(PromoRecipientTokenService.PLACEHOLDER).isEmpty());
    }

    @Test
    void 與退訂token不可互換_domainSeparation() {
        // 同一把 secret 下，把退訂簽章拼進 promo token 必須驗不過
        UnsubscribeTokenService unsub = new UnsubscribeTokenService("test-secret");
        String email = "alice@example.com";
        String b64 = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(email.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(service.verify(b64 + "." + unsub.sign(email)).isEmpty());
    }
}
