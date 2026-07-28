package world.springai.survey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 讀者網站公開連結的唯一組裝入口。
 *
 * <p>讀者站與問卷／退訂 API 可以共用同一個服務，卻使用不同公開網域；
 * 因此不可再用 {@code app.public-base-url} 到處手動串接。</p>
 */
@Component
public class ReaderSiteLinks {

    /** 已移除結尾斜線的讀者站公開網址。 */
    private final String baseUrl;

    /** 注入讀者站網址；未另外設定時沿用既有公開網址，讓本機與舊環境相容。 */
    public ReaderSiteLinks(
            @Value("${app.reader.base-url:${app.public-base-url}}") String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        this.baseUrl = normalized;
    }

    /** 組出指定文章的公開網址。 */
    public String article(String slug) {
        return baseUrl + "/r/news/" + slug;
    }

    /** 組出文章社群預覽 PNG。 */
    public String shareCard(String slug, String layout) {
        return baseUrl + "/r/share-card/" + slug + ".png?layout="
            + URLEncoder.encode(layout, StandardCharsets.UTF_8);
    }

    /** 組出歷史文章入口。 */
    public String archive() {
        return baseUrl + "/r/archive";
    }

    /** 組出帶推薦碼的讀者站訂閱入口。 */
    public String subscribeWithReferral(String referralCode) {
        return baseUrl + "/r/?ref="
            + URLEncoder.encode(referralCode, StandardCharsets.UTF_8);
    }

    /**
     * 組出登入頁網址，並保留登入後要回去的站內路徑。
     *
     * <p>此處只負責編碼；redirect 的安全性仍由登入頁與 Magic Link 驗證端共同把關。</p>
     */
    public String login(String redirect) {
        String target = redirect == null || redirect.isBlank() ? "/r/archive" : redirect;
        return baseUrl + "/r/login?redirect="
            + URLEncoder.encode(target, StandardCharsets.UTF_8);
    }

    /** 組出一次性 Magic Link 驗證網址。 */
    public String verifyLogin(String rawToken, String redirect) {
        StringBuilder link = new StringBuilder(baseUrl)
            .append("/api/reader/login/verify?t=")
            .append(URLEncoder.encode(rawToken, StandardCharsets.UTF_8));
        if (redirect != null && !redirect.isBlank()) {
            link.append("&redirect=")
                .append(URLEncoder.encode(redirect, StandardCharsets.UTF_8));
        }
        return link.toString();
    }
}
