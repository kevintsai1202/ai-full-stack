package world.springai.survey.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 寄信額度偵測：向 Zeabur GraphQL API 查詢 ZSend 帳號的真實日／月額度與已寄量，
 * 讓後台不必寫死「每日 100 封」。查不到時退回設定檔的保守預設值。
 *
 * <p>注意：此查詢用的是 <b>Zeabur 帳號 token</b>（sk-...，即 CLI 登入用的那把），
 * 與 {@code app.mail.api-key}（ZSend 寄信金鑰）是不同的憑證；ZSend 寄信 REST API
 * 本身沒有提供額度查詢端點。</p>
 */
@Service
public class MailQuotaService {

    private static final Logger log = LoggerFactory.getLogger(MailQuotaService.class);

    /** Zeabur GraphQL 端點 */
    private static final String GRAPHQL_URI = "/graphql";

    /** 查詢 ZSend 帳號狀態（額度／已寄量／重置時間）的 GraphQL 查詢字串 */
    private static final String QUERY = """
        query { getZSendUserStatus { \
        status dailyQuota dailySent quotaResetAt \
        monthlyQuota monthlySent monthlyResetAt \
        quotaType overageBillingEnabled } }""";

    /** 額度查詢結果快取秒數：後台每次更新人數都會問一次，避免頻繁打外部 API */
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    /** {@link Quota#source()} 的值：實際向 Zeabur 偵測而來 */
    public static final String SOURCE_ZEABUR = "zeabur";

    /** {@link Quota#source()} 的值：偵測不可用，數字為設定檔推測值 */
    public static final String SOURCE_FALLBACK = "fallback";

    /**
     * fallback 路徑的行銷可用量硬上限（封）。
     *
     * <p>偵測失敗時我們<b>不知道</b>實際還剩多少額度，{@code fallbackQuota} 只是一個猜測值。
     * 而它來自環境變數 {@code MAIL_FALLBACK_QUOTA}：若有人為了「偵測壞掉時不要卡住營運」
     * 把它調成 5000，偵測一失敗就等於放行 4950 封毫無保護的群發，把交易信額度連同
     * 保留額度一起吃光——那正是 reserve 機制要防的事，卻由一個「只是預設值」的設定繞過。
     * 因此 fallback 路徑的行銷可用量另外收斂到這個保守常數：猜測值只能用來寄一小批，
     * 不能用來授權大量群發。</p>
     */
    static final int FALLBACK_MARKETING_CAP = 50;

    /**
     * 單次後台操作的安全上限。
     * 邀請信是逐封同步呼叫 {@link MailSender#send}，一封一個 HTTP request，
     * 因此即使月額度有數萬封，單一 HTTP 請求也不能一次吃下——否則必被 gateway 逾時切斷
     * 且中途失敗無法續傳。剩餘額度用來「顯示」，這個常數用來「限制單批」。
     */
    static final int BATCH_CAP = 500;

    /** 綁定 Zeabur API base URL 的 HTTP 客戶端 */
    private final RestClient client;
    /** Zeabur 帳號 token；空白時停用動態偵測 */
    private final String zeaburToken;
    /** 偵測失敗時採用的保守額度（封） */
    private final long fallbackQuota;
    /**
     * 保留給交易信的額度（封）。
     *
     * <p>登入信、確認信、歡迎信不受此限制——它們正是 reserve 的使用者。
     * 這個數字存在的理由：群發把額度用到 0 時，讀者就收不到 magic link，
     * 那不是「信少寄一封」，而是整個讀者端登不進去（spec §6）。</p>
     */
    private final long transactionalReserve;

    /** 快取的查詢結果與寫入時間（null 表示尚未查過或已失效） */
    private final AtomicReference<Cached> cache = new AtomicReference<>();

    /** 快取內容：查詢結果 + 寫入時間戳 */
    private record Cached(Quota quota, Instant at) {}

    /**
     * 額度回報結果。
     *
     * @param source     額度來源：{@code zeabur}（實際偵測）或 {@code fallback}（設定檔預設）
     * @param status     ZSend 帳號健康狀態（healthy / suspended…），fallback 時為 unknown
     * @param remaining  可用剩餘封數 = min(日剩餘, 月剩餘)
     * @param batchMax   後台單次操作允許的最大封數 = min(remaining, {@link #BATCH_CAP})
     * @param reserve            保留給交易信的額度
     * @param marketingRemaining 可用於行銷信的量 = max(0, remaining - reserve)
     * @param marketingBatchMax  行銷信單批上限 = min(marketingRemaining, BATCH_CAP)
     */
    public record Quota(String source, String status,
                        long dailyQuota, long dailySent, long dailyRemaining,
                        long monthlyQuota, long monthlySent, long monthlyRemaining,
                        long remaining, long batchMax,
                        long reserve, long marketingRemaining, long marketingBatchMax,
                        boolean overageBillingEnabled,
                        String quotaResetAt, String monthlyResetAt) {}

    /**
     * 注入 HTTP 客戶端建構器、Zeabur 帳號 token、偵測失敗時的保守額度與交易信保留額度。
     *
     * <p><b>保留額度必須夾到 0 以上</b>：{@link #marketingLimits} 的
     * {@code Math.max(0, remaining - transactionalReserve)} 只保護 marketingRemaining
     * 不為負，卻不保護 reserve 本身為負。{@code MAIL_TRANSACTIONAL_RESERVE=-500}
     * （打錯正負號，或誤以為負數代表「不保留」）會讓 remaining=1000 時算出
     * marketingRemaining=1500——保留機制反向放大可用量，群發被允許超額寄出 500 封。
     * 負值一律視為 0 並留下警告，讓設定錯誤在 log 裡看得見而不是靜默生效。</p>
     */
    public MailQuotaService(RestClient.Builder builder,
                            @Value("${app.mail.zeabur-token:}") String zeaburToken,
                            @Value("${app.mail.fallback-quota:100}") long fallbackQuota,
                            @Value("${app.mail.transactional-reserve:50}") long transactionalReserve) {
        this.client = builder.baseUrl("https://api.zeabur.com").build();
        this.zeaburToken = zeaburToken;
        this.fallbackQuota = fallbackQuota;
        if (transactionalReserve < 0) {
            log.warn("app.mail.transactional-reserve 為負值（{}），已視為 0；"
                + "負的保留額度會反向放大群發可用量，請檢查 MAIL_TRANSACTIONAL_RESERVE 設定",
                transactionalReserve);
        }
        this.transactionalReserve = Math.max(0, transactionalReserve);
    }

    /**
     * 取得目前額度：優先用 60 秒內的快取，其次實際查詢 Zeabur，
     * 未設定 token 或查詢失敗時回傳 fallback 額度（不拋例外，後台仍可運作）。
     */
    public Quota current() {
        Cached c = cache.get();
        if (c != null && Duration.between(c.at(), Instant.now()).compareTo(CACHE_TTL) < 0) {
            return c.quota();
        }
        if (!StringUtils.hasText(zeaburToken)) {
            return fallback(); // 未設定 ZEABUR_API_TOKEN：不打 API，直接用保守值
        }
        try {
            Quota q = fetch();
            cache.set(new Cached(q, Instant.now()));
            return q;
        } catch (Exception e) {
            log.warn("查詢 Zeabur 寄信額度失敗，改用預設額度 {} 封：{}", fallbackQuota, e.getMessage());
            return fallback();
        }
    }

    /**
     * 讓快取立即失效，強迫下一次 {@link #current()} 重新向外部查詢。
     *
     * <p><b>為什麼必須有這支</b>：額度檢查是無狀態的——每次群發只問一次外部快照，
     * 寄出後不做任何本地扣減。快取 60 秒的情況下，管理者送出 950 人的群發（成功）後，
     * 60 秒內再送第二批 950 人時 {@link #current()} 回的仍是同一份舊快照，
     * marketingRemaining 還是 950，第二批照樣放行——實際寄出 1900 封 vs 額度 1000，
     * 保留給登入信的額度被吃光，讀者收不到 magic link。凡是實際寄出過信的路徑
     * 都必須在寄完後呼叫本方法，把這個窗口從 60 秒縮成單次查詢的延遲。</p>
     */
    public void invalidate() {
        cache.set(null);
    }

    /** 實際發出 GraphQL 查詢並轉成 {@link Quota}（含日／月剩餘與單批上限計算） */
    private Quota fetch() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", QUERY);
        Map<?, ?> resp = client.post()
            .uri(GRAPHQL_URI)
            .header("Authorization", "Bearer " + zeaburToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Map.class);

        Map<?, ?> data = resp == null ? null : asMap(resp.get("data"));
        Map<?, ?> s = data == null ? null : asMap(data.get("getZSendUserStatus"));
        if (s == null) {
            throw new IllegalStateException("回應缺少 getZSendUserStatus：" + resp);
        }

        long dailyQuota = num(s.get("dailyQuota"));
        long dailySent = num(s.get("dailySent"));
        long monthlyQuota = num(s.get("monthlyQuota"));
        long monthlySent = num(s.get("monthlySent"));
        // 剩餘數不允許負值（超量計費情境下 sent 可能超過 quota）
        long dailyRemaining = Math.max(0, dailyQuota - dailySent);
        long monthlyRemaining = Math.max(0, monthlyQuota - monthlySent);
        // Pro 方案的 dailyQuota 近似無限（999999999），真正天花板通常是月額度，故取兩者較小值
        long remaining = Math.min(dailyRemaining, monthlyRemaining);
        long[] marketing = marketingLimits(remaining);

        return new Quota(SOURCE_ZEABUR, str(s.get("status")),
            dailyQuota, dailySent, dailyRemaining,
            monthlyQuota, monthlySent, monthlyRemaining,
            remaining, Math.min(remaining, BATCH_CAP),
            transactionalReserve, marketing[0], marketing[1],
            Boolean.TRUE.equals(s.get("overageBillingEnabled")),
            str(s.get("quotaResetAt")), str(s.get("monthlyResetAt")));
    }

    /**
     * 偵測不可用時的保守額度：日／月皆以 fallbackQuota 計，已寄量未知以 0 計。
     *
     * <p>行銷可用量另外收斂到 {@link #FALLBACK_MARKETING_CAP}——理由見該常數的說明：
     * 這條路徑上的數字是猜的，不能用來授權大量群發。</p>
     */
    private Quota fallback() {
        long[] marketing = marketingLimits(fallbackQuota);
        // 猜測值只授權一小批：行銷可用量與單批上限一併收斂到保守常數
        marketing[0] = Math.min(marketing[0], FALLBACK_MARKETING_CAP);
        marketing[1] = Math.min(marketing[1], marketing[0]);
        return new Quota(SOURCE_FALLBACK, "unknown",
            fallbackQuota, 0, fallbackQuota,
            fallbackQuota, 0, fallbackQuota,
            fallbackQuota, Math.min(fallbackQuota, BATCH_CAP),
            transactionalReserve, marketing[0], marketing[1],
            false, null, null);
    }

    /** 依剩餘額度算出行銷可用量與單批上限（扣除交易信保留額度） */
    private long[] marketingLimits(long remaining) {
        long marketingRemaining = Math.max(0, remaining - transactionalReserve);
        return new long[] { marketingRemaining, Math.min(marketingRemaining, BATCH_CAP) };
    }

    /** 保留給交易信的額度（封），供後台顯示「為什麼行銷可用量只剩這麼多」 */
    public long reserve() {
        return transactionalReserve;
    }

    /** 安全轉 Map（非 Map 回 null，避免 GraphQL 回應結構變動時炸出 ClassCastException） */
    private static Map<?, ?> asMap(Object o) {
        return o instanceof Map<?, ?> m ? m : null;
    }

    /** 安全轉數值（GraphQL Int 可能被解析成 Integer 或 Double） */
    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    /** 安全轉字串（null 保持 null，供前端判斷「無資料」） */
    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
