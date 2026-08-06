package world.springai.survey.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 後台站連結組裝器。
 *
 * <p>登入信裡的連結必須指向後台網域，否則 cookie 會種在讀者站的 host 上而後台讀不到
 * （cookie 為 host-only）。</p>
 */
@Component
public class AdminSiteLinks {

    /** 後台對外網址，已去除尾端斜線 */
    private final String baseUrl;

    /** 注入後台對外網址 */
    public AdminSiteLinks(@Value("${app.admin.base-url:${app.public-base-url}}") String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        this.baseUrl = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    /** 組 magic-link 兌換連結 */
    public String verifyLogin(String rawToken) {
        return baseUrl + "/api/admin/login/verify?t="
            + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }
}
