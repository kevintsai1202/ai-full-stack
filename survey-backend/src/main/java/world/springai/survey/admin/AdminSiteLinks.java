package world.springai.survey.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
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

    private static final Logger log = LoggerFactory.getLogger(AdminSiteLinks.class);

    /** 後台對外網址，已去除尾端斜線 */
    private final String baseUrl;

    /**
     * 注入後台對外網址與後台入口網域，並在兩者不一致時於啟動期示警。
     *
     * <p><b>為什麼需要這道檢查</b>：{@code ADMIN_BASE_URL} 未設時會沿用
     * {@code APP_PUBLIC_BASE_URL}（＝問卷網域）。運維若只設了 {@code ADMIN_ENTRY_HOST}
     * 而漏設 {@code ADMIN_BASE_URL}，登入信的連結就會指向問卷網域，結果是被
     * {@code AdminEntryHostFilter} 回 404，或 cookie 種在錯誤的 host 上
     * （「明明點了連結卻還是要我登入」）——而且 token 已經被消耗掉，重試也沒用。
     * 兩種都是難以診斷的靜默失敗。</p>
     *
     * <p><b>為什麼記 WARN 而不是拒絕啟動</b>（與 {@code DeploymentSecretValidator}
     * 的 fail-closed 姿態刻意不同）：
     * ① {@code ADMIN_ENTRY_HOST} 在 {@code application.yml} 裡就被定位成可漸進啟用的
     * 選項（「建議確認管理網域 DNS/HTTPS 正常後再設定」），把它設錯而讓<b>整個服務</b>
     * ——含讀者站與問卷——起不來，傷害遠大於它要防的問題；
     * ② 這個缺陷的實際後果有明確上界：magic-link 登入失效，但既有的 {@code X-Admin-Key}
     * 金鑰路徑與 9 支自動化腳本完全不受影響，後台仍進得去；
     * ③ 這裡比對的是人填的網址字串（有無 port、有無協定、大小寫），
     * 嚴格相等的守衛誤判機率不低，而誤判的代價是全站中斷。
     * 這個缺陷真正的問題是「無聲」，把它變吵就已經解決；祕密外洩不同——那是即使
     * 有人看到日誌也已經來不及，所以那邊才必須拒絕啟動。</p>
     */
    public AdminSiteLinks(@Value("${app.admin.base-url:${app.public-base-url}}") String baseUrl,
                          @Value("${app.admin.entry-host:}") String entryHost) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        this.baseUrl = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        if (entryHostMismatch(this.baseUrl, entryHost)) {
            log.warn("後台網域設定不一致：ADMIN_ENTRY_HOST={} 但 ADMIN_BASE_URL={}。"
                    + "登入信的 magic-link 會指向後者，很可能被入口過濾器擋成 404，"
                    + "或把 admin_session cookie 種在錯誤的 host 上（點了連結仍要求登入），"
                    + "而 token 已被消耗。請將 ADMIN_BASE_URL 設為指向 ADMIN_ENTRY_HOST 的網址。",
                entryHost, this.baseUrl);
        }
    }

    /** 組 magic-link 兌換連結 */
    public String verifyLogin(String rawToken) {
        return baseUrl + "/api/admin/login/verify?t="
            + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    /**
     * 入口網域有設定、但與 base-url 的 host 不符時回 true。
     *
     * <p>刻意做成可直接呼叫的純函式（而非只把判斷藏在建構子裡記一行日誌）：
     * 「什麼情況算不一致」本身就是需要被測試鎖住的規則——特別是
     * <b>不比對 port</b> 這一條（反向代理後端常以 {@code :8080} 對外，
     * 而 {@code ADMIN_ENTRY_HOST} 依定義不含協定與路徑），若誤判成不一致，
     * 每次啟動都會噴一則假警告，而假警告會讓真警告被忽略。</p>
     *
     * @param baseUrl   已正規化的後台對外網址
     * @param entryHost 後台入口網域；空白代表未啟用，一律回 false
     */
    static boolean entryHostMismatch(String baseUrl, String entryHost) {
        String expected = entryHost == null ? "" : entryHost.trim();
        if (expected.isEmpty()) {
            return false;   // 未啟用入口網域，沒有可比對的對象
        }
        // ADMIN_ENTRY_HOST 依定義不含協定與路徑，但人可能連 port 一起填，一律只取 host 段
        int portSeparator = expected.indexOf(':');
        String expectedHost = portSeparator < 0 ? expected : expected.substring(0, portSeparator);
        String actualHost = hostOf(baseUrl);
        if (actualHost == null) {
            return true;    // base-url 連 host 都解析不出來，本身就是設定錯誤，值得示警
        }
        return !actualHost.equalsIgnoreCase(expectedHost);
    }

    /** 取出網址的 host；解析失敗回 null（設定值由人填，不可假設一定合法） */
    private static String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
