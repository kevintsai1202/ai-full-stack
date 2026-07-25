package world.springai.survey.reader;

import org.springframework.util.StringUtils;

/**
 * 共用的 redirect 安全判斷：只允許站內相對路徑（防開放式轉址）。
 *
 * <p>抽成獨立類別的原因：{@code LoginMailService}（寄信時組連結）與
 * {@code ReaderAuthController}（驗證 magic link 後導向）都需要同一份判斷。
 * 安全檢查有兩份實作就會漂移，而其中一份漂移就是一個漏洞。</p>
 */
public final class RedirectGuard {

    private RedirectGuard() {
    }

    /**
     * 只允許站內相對路徑（防開放式轉址）。
     *
     * <p><b>不能只比對字串前綴</b>：舊版只檢查「以 / 開頭、且不以 // 開頭」，
     * 但這擋不住反斜線變體，例如 {@code /\evil.com}——它以 / 開頭，且不是以
     * // 開頭，字串比對會判定為安全。然而依 WHATWG URL 規範，瀏覽器在解析
     * http/https 這類 special scheme 時，會把 {@code \} 與 {@code /} 視為
     * 等價字元；也就是說瀏覽器實際看到的是 {@code //evil.com}，會被當成
     * protocol-relative 網址導去外部網域。因此判斷前必須先把反斜線正規化成
     * 斜線，用「瀏覽器看到的樣子」而不是「字面上的樣子」來判斷是否安全。</p>
     *
     * <p>此外，字串比對永遠只能防禦「已知的變形」，無法窮舉所有前綴陷阱
     * （例如未來瀏覽器行為變化、其他等價字元）。所以正規化後再多一道
     * {@link java.net.URI} 解析，確認 scheme、host、authority 三者皆為
     * null——這是結構化的保險，用來擋掉沒被明確列舉到的站外網址形式，而不是
     * 又疊一條字串前綴規則。</p>
     *
     * <p>也必須先擋控制字元（尤其是 {@code \r} {@code \n}）：redirect 最終
     * 會被組進信件內容，若放行 CR/LF 有 header injection 之類的風險，且
     * URLEncoder 不會替我們過濾原始輸入是否「看似安全」，必須在判斷階段就拒絕。</p>
     *
     * <p><b>切勿把這個方法「簡化」回三行字串比對</b>——那正是本次修的漏洞來源。</p>
     */
    public static boolean isSafe(String redirect) {
        if (!StringUtils.hasText(redirect)) {
            return false;
        }
        String trimmed = redirect.trim();

        // 拒絕任何控制字元（含 \r \n），避免 header injection 或其他注入手法
        if (trimmed.chars().anyMatch(Character::isISOControl)) {
            return false;
        }

        // 反斜線正規化為斜線：瀏覽器對 special scheme（http/https）會這樣解析，
        // 所以判斷必須依「正規化後」的樣子，而不是字面原始輸入
        String normalized = trimmed.replace('\\', '/');
        if (!normalized.startsWith("/") || normalized.startsWith("//")) {
            return false;
        }

        // 再用 URI 解析正規化後的值，確保沒有 scheme/host/authority——
        // 這是擋掉前面字串比對沒想到的站外網址形式的最後一道保險
        try {
            java.net.URI uri = new java.net.URI(normalized);
            return uri.getScheme() == null
                && uri.getHost() == null
                && uri.getAuthority() == null;
        } catch (java.net.URISyntaxException e) {
            return false;
        }
    }
}
