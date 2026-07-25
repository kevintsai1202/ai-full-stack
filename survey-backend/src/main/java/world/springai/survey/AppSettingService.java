package world.springai.survey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 可調參數讀寫：點數與門檻類參數存資料庫，讓後台改完立即生效、不必重新部署。
 *
 * <p>設計理由（spec §9.1）：這些數字第一版是估的，要靠上線後的真實行為迭代。
 * 放進 application.yml 意味著每次調整都要重新部署——實務結果是不會有人去調，
 * 參數就永遠停在初版猜測值。</p>
 *
 * <p>授權判斷每次都會讀參數，因此加 60 秒快取（比照 MailQuotaService 的既有做法）；
 * 寫入時主動清除該鍵的快取，做到「改完立即生效」。</p>
 */
@Service
public class AppSettingService {

    private static final Logger log = LoggerFactory.getLogger(AppSettingService.class);

    /** 首次登入的初始贈點 */
    public static final String CREDIT_SIGNUP_GRANT = "credit.signup_grant";
    /** PREMIUM 文章的預設解鎖點數 */
    public static final String CREDIT_PREMIUM_COST = "credit.premium_cost";
    /** 邀請成功的獎勵點數 */
    public static final String CREDIT_REFERRAL_REWARD = "credit.referral_reward";
    /** VIP 預設效期天數 */
    public static final String VIP_DEFAULT_DAYS = "vip.default_days";

    /** 快取存活時間：授權判斷頻繁讀取，但參數變動極少 */
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final AppSettingRepository repository;

    /** 快取項目：值與寫入時間 */
    private record Cached(String value, Instant at) {}

    /** 以參數鍵為 key 的快取 */
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    /** 注入參數資料存取層 */
    public AppSettingService(AppSettingRepository repository) {
        this.repository = repository;
    }

    /**
     * 讀取整數參數；查無此鍵或值無法解析為整數時回傳呼叫端給的預設值。
     *
     * <p>回傳預設值而非拋例外是刻意的：新增參數不需要 data migration，
     * 而壞資料不應該讓授權判斷整個掛掉。</p>
     */
    public int getInt(String key, int defaultValue) {
        String raw = get(key);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("參數 {} 的值無法解析為整數（{}），改用預設值 {}", key, raw, defaultValue);
            return defaultValue;
        }
    }

    /** 讀取字串參數；查無此鍵回 null。快取命中則不查資料庫 */
    public String get(String key) {
        Cached cached = cache.get(key);
        if (cached != null && Duration.between(cached.at(), Instant.now()).compareTo(CACHE_TTL) < 0) {
            return cached.value();
        }
        String value = repository.findById(key).map(AppSetting::getValue).orElse(null);
        cache.put(key, new Cached(value, Instant.now()));
        return value;
    }

    /** 寫入參數並清除該鍵的快取，讓變更立即生效 */
    public void set(String key, String value) {
        Optional<AppSetting> existing = repository.findById(key);
        AppSetting entity = existing.orElseGet(() -> new AppSetting(key, value));
        entity.setValue(value);
        repository.save(entity);
        cache.remove(key); // 立即生效的關鍵：不等快取自然過期
        log.info("參數 {} 已更新為 {}", key, value);
    }

    /** 清除全部快取（測試與後台批次更新後使用） */
    public void clearCache() {
        cache.clear();
    }
}
