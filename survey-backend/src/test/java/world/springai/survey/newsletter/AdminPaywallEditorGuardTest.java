package world.springai.survey.newsletter;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 後台付費牆編輯器的分級同步回歸測試。 */
class AdminPaywallEditorGuardTest {

    /** 電子報管理後台單頁檔案。 */
    private static final Path ADMIN_PAGE = Path.of("src/main/resources/static/admin.html");

    /** 點擊付費牆時必須同步切換 PREMIUM，不能只插入標記而留下 BASIC。 */
    @Test
    void paywallToolbarSelectsPremiumTier() throws IOException {
        String html = Files.readString(ADMIN_PAGE, StandardCharsets.UTF_8);

        assertTrue(html.contains("data-paywall-tier=\"PREMIUM\""),
            "付費牆按鈕必須宣告 PREMIUM 分級");
        assertTrue(html.contains("paywallTier === 'PREMIUM'"),
            "Markdown 工具列必須在插入付費牆時同步切換分級");
        assertTrue(html.contains("tierSelect.dispatchEvent(new Event('change'))"),
            "切換後必須觸發既有點數預填與提示邏輯");
    }
}
