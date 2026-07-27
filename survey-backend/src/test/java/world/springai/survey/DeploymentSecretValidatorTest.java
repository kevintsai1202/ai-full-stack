package world.springai.survey;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 部署祕密 fail-closed 行為測試。 */
class DeploymentSecretValidatorTest {

    /** 公開預設值在未 opt-in 時必須阻止啟動。 */
    @Test
    void rejectsPublicDevelopmentDefaults() {
        DeploymentSecretValidator validator = new DeploymentSecretValidator(
            false, "dev-admin-key", "dev-unsub-secret",
            "dev-reader-jwt-secret-change-me-32chars");

        assertThrows(IllegalStateException.class, validator::validate);
    }

    /** 本機明確 opt-in 才可使用固定測試祕密。 */
    @Test
    void explicitDevelopmentOptInAllowsFixedSecrets() {
        DeploymentSecretValidator validator = new DeploymentSecretValidator(
            true, "dev-admin-key", "dev-unsub-secret",
            "dev-reader-jwt-secret-change-me-32chars");

        assertDoesNotThrow(validator::validate);
    }

    /** 三種用途共用同一把祕密會擴大單點外洩影響，必須拒絕。 */
    @Test
    void rejectsReusedSecrets() {
        String repeated = "a-strong-but-reused-secret-value-123456789";
        DeploymentSecretValidator validator = new DeploymentSecretValidator(
            false, repeated, repeated, repeated);

        assertThrows(IllegalStateException.class, validator::validate);
    }

    /** 足夠長且彼此獨立的祕密應通過。 */
    @Test
    void acceptsIndependentStrongSecrets() {
        DeploymentSecretValidator validator = new DeploymentSecretValidator(
            false,
            "admin-secret-0123456789-abcdefghijkl",
            "unsubscribe-secret-0123456789-abcdef",
            "reader-jwt-secret-0123456789-abcdefg");

        assertDoesNotThrow(validator::validate);
    }
}
