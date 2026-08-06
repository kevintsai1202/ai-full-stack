package world.springai.survey;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 部署祕密 fail-closed 行為測試。 */
class DeploymentSecretValidatorTest {

    /** application.yml 內已 commit 進版控的公開開發預設值，任一把都不得用於部署 */
    private static final String DEV_ADMIN_KEY = "dev-admin-key";
    private static final String DEV_UNSUB = "dev-unsub-secret";
    private static final String DEV_READER_JWT = "dev-reader-jwt-secret-change-me-32chars";
    private static final String DEV_ADMIN_JWT = "dev-admin-jwt-secret-change-me-32chars";

    /** 公開預設值在未 opt-in 時必須阻止啟動。 */
    @Test
    void rejectsPublicDevelopmentDefaults() {
        DeploymentSecretValidator validator = new DeploymentSecretValidator(
            false, DEV_ADMIN_KEY, DEV_UNSUB, DEV_READER_JWT, DEV_ADMIN_JWT);

        assertThrows(IllegalStateException.class, validator::validate);
    }

    /** 本機明確 opt-in 才可使用固定測試祕密。 */
    @Test
    void explicitDevelopmentOptInAllowsFixedSecrets() {
        DeploymentSecretValidator validator = new DeploymentSecretValidator(
            true, DEV_ADMIN_KEY, DEV_UNSUB, DEV_READER_JWT, DEV_ADMIN_JWT);

        assertDoesNotThrow(validator::validate);
    }

    /** 各種用途共用同一把祕密會擴大單點外洩影響，必須拒絕。 */
    @Test
    void rejectsReusedSecrets() {
        String repeated = "a-strong-but-reused-secret-value-123456789";
        DeploymentSecretValidator validator = new DeploymentSecretValidator(
            false, repeated, repeated, repeated, repeated);

        assertThrows(IllegalStateException.class, validator::validate);
    }

    /** 足夠長且彼此獨立的祕密應通過。 */
    @Test
    void acceptsIndependentStrongSecrets() {
        DeploymentSecretValidator validator = new DeploymentSecretValidator(
            false,
            "admin-secret-0123456789-abcdefghijkl",
            "unsubscribe-secret-0123456789-abcdef",
            "reader-jwt-secret-0123456789-abcdefg",
            "admin-jwt-secret-0123456789-abcdefgh");

        assertDoesNotThrow(validator::validate);
    }

    /**
     * <b>C1 回歸護欄</b>：其他三把都設對，只有 {@code ADMIN_JWT_SECRET} 仍是
     * application.yml 裡那個公開預設值時，必須拒絕啟動。
     *
     * <p>這個組合正是原缺陷的實際形狀——運維設了看得見的三把、漏了新加的第四把，
     * 服務照常啟動、登入照常成功、零症狀，但任何讀過 repo 的人都能用公開字串
     * 自簽 {@code admin_session} cookie 打開全部管理端點。把這把祕密從
     * {@code secrets} map 拿掉，本測試就會變綠——那正是它存在的理由。</p>
     */
    @Test
    void rejectsDefaultAdminJwtSecretEvenWhenOtherSecretsAreStrong() {
        DeploymentSecretValidator validator = new DeploymentSecretValidator(
            false,
            "admin-secret-0123456789-abcdefghijkl",
            "unsubscribe-secret-0123456789-abcdef",
            "reader-jwt-secret-0123456789-abcdefg",
            DEV_ADMIN_JWT);

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validate);
        assertMessageMentions(ex, "ADMIN_JWT_SECRET");
    }

    /**
     * <b>spec §3.2 的可執行證明</b>：管理者與讀者兩把 JWT 秘鑰刻意分離以切開爆炸半徑，
     * 設成同一把等於把兩個信任域併成一個，必須拒絕啟動。
     */
    @Test
    void rejectsAdminJwtSecretIdenticalToReaderJwtSecret() {
        String shared = "shared-jwt-secret-0123456789-abcdefgh";
        DeploymentSecretValidator validator = new DeploymentSecretValidator(
            false,
            "admin-secret-0123456789-abcdefghijkl",
            "unsubscribe-secret-0123456789-abcdef",
            shared,
            shared);

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validate);
        assertMessageMentions(ex, "必須使用不同祕密");
    }

    /** 錯誤訊息需指出問題所在，否則運維無從得知是哪一把祕密不合格 */
    private static void assertMessageMentions(IllegalStateException ex, String fragment) {
        org.junit.jupiter.api.Assertions.assertTrue(
            ex.getMessage() != null && ex.getMessage().contains(fragment),
            "錯誤訊息未提及「" + fragment + "」：" + ex.getMessage());
    }
}
