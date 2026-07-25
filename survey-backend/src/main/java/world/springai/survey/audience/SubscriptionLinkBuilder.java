package world.springai.survey.audience;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * confirm / unsubscribe 連結組裝的唯一擁有者。
 *
 * <p><b>為什麼要收斂</b>：這兩個路由原本被四處各自用字串拼出
 * （{@code WelcomeMailService}、{@code CampaignService} 兩處、{@code InviteService}），
 * 其中三處在 {@code newsletter} 與 {@code audience}，卻依賴當時長在 {@code form}
 * 的端點。那是一條 {@code PackageDependencyTest} 抓不到的隱形反向依賴——
 * 字串不是 import。收斂到本類後，路由只有一個擁有者，拆解線才真的可拆。</p>
 *
 * <p><b>路徑刻意保留 {@code /api/survey/} 前綴</b>：已寄出的信件內含這些網址，
 * 改路徑會讓所有在途的確認信與退訂連結失效。網址是對外契約，
 * 程式碼搬家不該改契約。</p>
 */
@Component
public class SubscriptionLinkBuilder {

    /** 確認訂閱端點路徑（對外契約，不可變更） */
    public static final String CONFIRM_PATH = "/api/survey/confirm";
    /** 退訂端點路徑（對外契約，不可變更） */
    public static final String UNSUBSCRIBE_PATH = "/api/survey/unsubscribe";

    private final UnsubscribeTokenService tokenService;
    /** 對外網址，用於組出完整連結 */
    private final String publicBaseUrl;

    /** 注入 HMAC 簽章服務與對外網址 */
    public SubscriptionLinkBuilder(UnsubscribeTokenService tokenService,
                                   @Value("${app.public-base-url}") String publicBaseUrl) {
        this.tokenService = tokenService;
        this.publicBaseUrl = publicBaseUrl;
    }

    /** 組確認訂閱連結（含該 email 的個人化 HMAC 簽章） */
    public String confirmLink(String email) {
        return link(CONFIRM_PATH, email, tokenService.sign(email));
    }

    /** 組退訂連結（含該 email 的個人化 HMAC 簽章） */
    public String unsubscribeLink(String email) {
        return link(UNSUBSCRIBE_PATH, email, tokenService.sign(email));
    }

    /**
     * 後台預覽用的退訂連結：假 email、假簽章。
     *
     * <p>刻意不帶真實簽章——預覽內容會顯示在後台頁面與測試信中，
     * 不該讓一個可用的退訂 token 隨預覽外流。</p>
     */
    public String previewUnsubscribeLink() {
        return link(UNSUBSCRIBE_PATH, "preview@example.com", "preview");
    }

    /** 組出 {base}{path}?email={urlencoded}&t={token} */
    private String link(String path, String email, String token) {
        return publicBaseUrl + path
            + "?email=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
            + "&t=" + token;
    }
}
