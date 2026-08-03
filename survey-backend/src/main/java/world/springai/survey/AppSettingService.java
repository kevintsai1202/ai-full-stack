package world.springai.survey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    /** 工商提案每次投放的點數單價 */
    public static final String CREDIT_PROMO_PLACEMENT_COST = "credit.promo_placement_cost";
    /** 邀請成功的獎勵點數 */
    public static final String CREDIT_REFERRAL_REWARD = "credit.referral_reward";
    /** 被邀者完成確認後的加碼點數 */
    public static final String CREDIT_REFERRAL_INVITEE_REWARD = "credit.referral_invitee_reward";
    /** 問卷完整填答的獎勵點數 */
    public static final String CREDIT_SURVEY_REWARD = "credit.survey_reward";
    /** 3 人里程碑獎勵 */
    public static final String CREDIT_REFERRAL_MILESTONE_3 = "credit.referral_milestone_3";
    /** 5 人里程碑獎勵 */
    public static final String CREDIT_REFERRAL_MILESTONE_5 = "credit.referral_milestone_5";
    /** 10 人里程碑獎勵 */
    public static final String CREDIT_REFERRAL_MILESTONE_10 = "credit.referral_milestone_10";
    /** 每位推薦人每日可自動核准的確認人數 */
    public static final String REFERRAL_DAILY_AUTO_APPROVE_LIMIT = "referral.daily_auto_approve_limit";
    /** 十分鐘內達到此確認數即轉人工審核 */
    public static final String REFERRAL_VELOCITY_REVIEW_THRESHOLD = "referral.velocity_review_threshold";
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

    /**
     * 批次寫入多筆參數，全部包在單一交易內。
     *
     * <p><b>為什麼需要這個方法</b>：{@link AdminSettingController#update} 先驗證
     * 全部、再全部寫入，目的是避免「一半參數生效、一半沒生效，而站方只看到
     * 一個錯誤訊息」。但若呼叫端對每個鍵各自呼叫 {@link #set}，寫入階段本身
     * 並未包在同一交易——批次中某一筆的 {@code repository.save()} 若因連線
     * 中斷或鎖逾時拋錯，前面已寫入的鍵不會回滾，呼叫端只收到一個 500 卻已
     * 造成部分寫入，觸發條件從「驗證失敗」換成「寫入時的基礎設施故障」，
     * 但失效模式與這支端點原本要防的完全相同。</p>
     *
     * <p><b>必須由外部 bean 呼叫</b>：{@code @Transactional} 只有在跨 bean 呼叫
     * 經過 Spring proxy 時才會生效；若把這個迴圈寫成本類別內部呼叫的私有方法，
     * 註解會靜默失效。{@link AdminSettingController} 持有的是被 proxy 包裹的
     * {@code AppSettingService} bean，因此從那裡呼叫本方法才真的會開交易。</p>
     *
     * <p><b>為什麼提交後要再清一次快取</b>：{@link #set} 的 {@code cache.remove}
     * 發生在交易<b>提交之前</b>。在交易還沒提交的那段時間內，任何並行的
     * {@link #get} 都會讀到資料庫裡的<b>舊值</b>（新值尚未提交、對其他連線不可見）
     * 並把它重新寫進快取，存活 60 秒。結果就是「已儲存，立即生效」的提示出現了，
     * 後台與讀者頁面卻最長還會顯示舊數字一分鐘——正是這支端點存在的理由被繞過。
     * 故在提交之後再清一次；沒有交易同步時（例如被非交易路徑呼叫）就直接清。</p>
     */
    @Transactional
    public void setAll(Map<String, Integer> updates) {
        updates.forEach((k, v) -> set(k, String.valueOf(v)));

        // 快照鍵集合：afterCommit 回呼在方法返回後才執行，不可依賴呼叫端的 map 仍未變動
        Set<String> keys = Set.copyOf(updates.keySet());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /** 提交後再清一次，蓋掉交易期間並行讀取所快取的未提交舊值 */
                @Override
                public void afterCommit() {
                    keys.forEach(cache::remove);
                }
            });
        } else {
            keys.forEach(cache::remove);
        }
    }

    /** 清除全部快取（測試與後台批次更新後使用） */
    public void clearCache() {
        cache.clear();
    }
}
