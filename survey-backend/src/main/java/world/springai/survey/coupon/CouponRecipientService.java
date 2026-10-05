package world.springai.survey.coupon;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import world.springai.survey.audience.AudiencePlatformService;
import world.springai.survey.audience.AudienceSearchService;
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;

/**
 * 優惠券活動的名單查詢與收件資格驗證，依寄送模式分兩種口徑。
 *
 * <p><b>批次寄送（優惠券分頁）</b>：以活動建立當下快照的問卷篩選條件
 * （{@code formKey}＋{@code answerFilter}）向 {@link AudienceSearchService} 查詢命中名單；
 * 不論快照本身是否指定同意狀態，一律強制覆寫為 {@code CONFIRMED}。{@link #findIllegal}
 * 是批次寄送的注入防線：後台勾選補寄名單時，只能從這份命中集合挑人。</p>
 *
 * <p><b>單筆寄送（問卷分析逐列寄券）</b>：不看命中名單——收件人不必填過該券的名單來源問卷，
 * 改以 {@link #findSingleSendRejection} 判斷訂閱資格（2026-10-06 使用者決策：
 * 問卷勾選同意但尚未點確認信者也可寄）。退訂者在兩種模式下都不會收到信。</p>
 */
@Service
public class CouponRecipientService {

    /**
     * 分頁拉取上限。刻意另訂常數而非直接引用
     * {@code AudienceSearchService.MAX_PAGE_SIZE}——該常數為套件私有（無存取修飾詞），
     * coupon 套件無法跨套件引用；數值須與其保持一致（見該類 {@code search()} 的
     * size 上限驗證，超過 200 會回 400）。
     */
    private static final int PAGE_SIZE = 200;

    /**
     * 分頁用穩定排序鍵：{@code email}（對應 {@code p.email_normalized}）。
     *
     * <p><b>為什麼不能用預設排序</b>：{@code searchAll} 用 OFFSET 分頁跨多次呼叫拉取
     * 全量名單，{@link AudienceSearchService#search} 在 {@code sort} 為 null 時預設以
     * {@code lastActivityAt}（{@code activity.last_activity_at}）排序——這是一個會隨
     * 使用者互動（例如提交問卷、測驗）即時變動的欄位。若在分頁期間有人的活動時間更新，
     * 該筆列在排序中的名次會位移，可能造成 OFFSET 分頁跨頁漏筆（合法收件人被漏收）
     * 或重複（同一人被算兩次，影響 {@link #findIllegal} 的判斷基準）。</p>
     *
     * <p><b>為什麼選 email</b>：查過 {@code AudienceSearchService.orderBy()} 的排序白名單
     * （email／name／credits／vipExpiresAt／lastLoginAt／lastActivityAt／deliveryCount／
     * lastDeliveryAt／unlockCount／lastUnlockAt），白名單內沒有直接開放 {@code personId}
     * 排序，但 {@code email} 對應的 {@code p.email_normalized} 是白名單中最接近不可變鍵的
     * 欄位（正常操作下不會在一次名單解析的極短時間內被改變）；{@code orderBy()} 也固定在
     * 任何排序欄位之後補上 {@code p.id ASC} 作為次要排序鍵（tie-breaker），兩者合計即可
     * 保證同一份查詢條件下分頁順序穩定、不受 activity 等易變欄位影響。</p>
     */
    private static final AudienceSearchService.Sort STABLE_SORT =
        new AudienceSearchService.Sort("email", "ASC");

    /**
     * 「填過問卷」的真實口徑：僅計官網問卷（{@code survey_form}）與電子報問卷
     * （{@code newsletter_survey}）兩個真實表單提交來源。
     *
     * <p>Dify 學員、線上測驗、名單匯入等管線也會為同一份問卷（同 {@code formKey}）
     * 建立 {@code record_type='survey_submission'} 的 {@code audience_record}，若不限
     * 來源查詢會把這些「未填答、只是被匯入」的人也算成命中名單——正式環境實測
     * fullstack-course-interest 一題共 536 筆 records（survey_form=57、dify=190、
     * exam=254、newsletter=35），不過濾來源會把應收 57 人誤灌成 233 人。</p>
     */
    private static final List<String> REAL_SUBMISSION_SOURCES = List.of("survey_form", "newsletter_survey");

    private final AudienceSearchService audienceSearchService;
    private final EmailLogRepository emailLogRepository;
    private final ObjectMapper objectMapper;
    /** 單筆寄送訂閱資格查詢用（名單中心同意事件與 survey_response 旗標） */
    private final JdbcTemplate jdbc;

    /** 注入 audience 搜尋服務、寄送記錄、JSON 解析器與 JDBC 查詢 */
    public CouponRecipientService(AudienceSearchService audienceSearchService,
                                   EmailLogRepository emailLogRepository,
                                   ObjectMapper objectMapper,
                                   JdbcTemplate jdbc) {
        this.audienceSearchService = audienceSearchService;
        this.emailLogRepository = emailLogRepository;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    /**
     * 單筆寄送的訂閱資格判斷：回傳拒絕原因，可寄時回傳空值。
     *
     * <p>判斷順序（退訂優先，任何同意證據都蓋不過退訂）：</p>
     * <ol>
     *   <li>名單中心最新 EMAIL 同意事件為 {@code UNSUBSCRIBED}，或 {@code survey_response}
     *       任一列已退訂 → 拒絕。兩處都要看：退訂連結會同時寫兩處，但匯入與歷史資料
     *       不保證兩邊一致，寧可多擋。</li>
     *   <li>最新同意事件為 {@code CONFIRMED} → 可寄。最新事件為準，與
     *       {@code SurveyResponseRepository#findRecipients} 的寄送名單口徑一致。</li>
     *   <li>否則 {@code survey_response} 有該 email 且勾選同意 → 可寄（使用者決策：
     *       問卷勾選同意但尚未點確認信者也可寄）。</li>
     *   <li>以上皆非 → 拒絕（未同意）。</li>
     * </ol>
     *
     * @param email 收件人 email（大小寫不敏感、忽略前後空白）
     * @return 拒絕原因；可寄時為 {@link Optional#empty()}
     */
    public Optional<String> findSingleSendRejection(String email) {
        String normalized = AudiencePlatformService.normalizeEmail(email);
        if (normalized.isEmpty()) {
            return Optional.of("收件人 email 不得為空");
        }
        // 名單中心最新一筆 EMAIL 同意事件（查無人物或查無事件時為 null）
        String latestConsent = jdbc.query("""
            SELECT c.status
              FROM audience_person p
              JOIN audience_consent c ON c.person_id = p.id
             WHERE p.email_normalized = ? AND c.channel = ?
             ORDER BY c.occurred_at DESC, c.id DESC
             LIMIT 1
            """, (rs, rowNum) -> rs.getString("status"), normalized, AudiencePlatformService.CHANNEL_EMAIL)
            .stream().findFirst().orElse(null);
        // survey_response 多列彙整：同一人可能填過多次問卷，任一列退訂即算退訂、任一列同意即算同意
        Map<String, Object> flags = jdbc.queryForMap("""
            SELECT COALESCE(bool_or(s.unsubscribed), false) AS any_unsubscribed,
                   COALESCE(bool_or(s.consent), false)      AS any_consent
              FROM survey_response s
             WHERE lower(btrim(s.email)) = ?
            """, normalized);
        boolean anyUnsubscribed = Boolean.TRUE.equals(flags.get("any_unsubscribed"));
        boolean anyConsent = Boolean.TRUE.equals(flags.get("any_consent"));

        if (AudiencePlatformService.CONSENT_UNSUBSCRIBED.equals(latestConsent) || anyUnsubscribed) {
            return Optional.of("收件人已退訂，不寄送行銷信：" + email.trim());
        }
        if (AudiencePlatformService.CONSENT_CONFIRMED.equals(latestConsent) || anyConsent) {
            return Optional.empty();
        }
        return Optional.of("收件人未同意接收行銷信，不寄送：" + email.trim());
    }

    /** 單一收件人：email 完整值（admin 介面）、稱呼、是否已寄過本活動 */
    public record Recipient(String email, String name, boolean alreadySent) {}

    /**
     * 以活動快照條件查命中名單（固定 consent=CONFIRMED），alreadySent 由
     * email_log（type={@code coupon:{活動id}}、status={@code sent}）判定，
     * email 比對正規化為小寫、忽略大小寫差異。
     */
    public List<Recipient> resolve(CouponCampaign campaign) {
        List<Map<String, Object>> items = searchAll(campaign);
        Set<String> alreadySent = sentEmails(campaign.getId());
        List<Recipient> recipients = new ArrayList<>();
        for (Map<String, Object> item : items) {
            String email = (String) item.get("email");
            String name = (String) item.get("name");
            boolean sent = email != null && alreadySent.contains(normalize(email));
            recipients.add(new Recipient(email, name, sent));
        }
        return recipients;
    }

    /**
     * 子集驗證：{@code requested} 中不屬於 {@link #resolve} 命中集合者（正規化小寫比對）；
     * 回傳這些不合法的 email（保留呼叫端原始大小寫，方便顯示）。空清單代表全部合法，
     * 且不觸發任何名單查詢（呼叫端沒有要驗證的對象）。
     */
    public List<String> findIllegal(CouponCampaign campaign, List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        Set<String> eligible = new LinkedHashSet<>();
        for (Map<String, Object> item : searchAll(campaign)) {
            String email = (String) item.get("email");
            if (email != null) {
                eligible.add(normalize(email));
            }
        }
        List<String> illegal = new ArrayList<>();
        for (String email : requested) {
            if (email == null || !eligible.contains(normalize(email))) {
                illegal.add(email);
            }
        }
        return illegal;
    }

    /**
     * 依活動快照條件分頁拉全量命中名單；每頁 {@link #PAGE_SIZE} 筆，
     * 依 {@code total} 累加到滿為止。以「本頁為空」作為額外停損，避免
     * 極端情況下 {@code total} 與實際回傳不一致造成無窮迴圈。
     */
    private List<Map<String, Object>> searchAll(CouponCampaign campaign) {
        AudienceSearchService.Filters filters = buildFilters(campaign);
        List<Map<String, Object>> all = new ArrayList<>();
        int page = 0;
        long total = Long.MAX_VALUE;
        while (all.size() < total) {
            AudienceSearchService.SearchRequest request =
                new AudienceSearchService.SearchRequest(filters, STABLE_SORT, page, PAGE_SIZE);
            AudienceSearchService.SearchResult result = audienceSearchService.search(request);
            if (result.items().isEmpty()) {
                break;
            }
            all.addAll(result.items());
            total = result.total();
            page++;
        }
        return all;
    }

    /**
     * 組出查詢條件：consent 固定覆寫為 CONFIRMED（不論活動快照本身有無指定），
     * survey 條件取自活動快照的 formKey／解析後的 answerFilter。
     */
    private AudienceSearchService.Filters buildFilters(CouponCampaign campaign) {
        Map<String, Object> answers = parseAnswerFilter(campaign.getAnswerFilter());
        AudienceSearchService.SurveyFilter survey = new AudienceSearchService.SurveyFilter(
            campaign.getFormKey(), null, answers, REAL_SUBMISSION_SOURCES);
        return new AudienceSearchService.Filters(
            null, null, List.of("CONFIRMED"), null, null,
            null, null, survey, null, null);
    }

    /** 解析活動快照的答案篩選條件 JSON 字串為 Map；空物件 "{}" 視為不限任何答案 */
    private Map<String, Object> parseAnswerFilter(String answerFilterJson) {
        if (answerFilterJson == null || answerFilterJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(answerFilterJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            // 快照理論上只會由建立活動當下（已驗證過的 JSON）寫入，此處視為資料損毀，
            // 明確失敗優於吞例外後靜默查出「不限任何答案」的錯誤名單。
            throw new IllegalStateException(
                "優惠券活動 answerFilter 非合法 JSON：" + answerFilterJson, e);
        }
    }

    /** 該活動已成功寄過的 email 集合（正規化小寫），type = coupon:{campaignId} */
    private Set<String> sentEmails(Long campaignId) {
        List<EmailLog> logs = emailLogRepository.findByTypeAndStatus("coupon:" + campaignId, "sent");
        Set<String> set = new LinkedHashSet<>();
        for (EmailLog log : logs) {
            set.add(normalize(log.getRecipient()));
        }
        return set;
    }

    /** email 正規化：去除頭尾空白並轉小寫，作為大小寫不敏感比對的統一格式 */
    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 已寄總覽（B2）：彙整 email_log 中所有 type=coupon:{id}、status=sent 的記錄，
     * 回傳「email（小寫正規化）→ 已成功收過的活動 id 清單（依 id 升冪）」。
     * 資料本就存在 email_log，不需新表（spec §4.2）。
     */
    public Map<String, List<Long>> sentCouponsByEmail() {
        Map<String, java.util.SortedSet<Long>> grouped = new java.util.HashMap<>();
        for (EmailLog log : emailLogRepository.findByTypeStartingWithAndStatus("coupon:", "sent")) {
            long campaignId = Long.parseLong(log.getType().substring("coupon:".length()));
            grouped.computeIfAbsent(normalize(log.getRecipient()), k -> new java.util.TreeSet<>()).add(campaignId);
        }
        Map<String, List<Long>> result = new java.util.HashMap<>();
        grouped.forEach((email, ids) -> result.put(email, List.copyOf(ids)));
        return result;
    }
}
