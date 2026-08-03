package world.springai.survey.coupon;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import world.springai.survey.audience.AudienceSearchService;
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;

/**
 * 優惠券活動的名單查詢與子集驗證。
 *
 * <p>以活動建立當下快照的問卷篩選條件（{@code formKey}＋{@code answerFilter}）
 * 向 {@link AudienceSearchService} 查詢命中名單；不論快照本身是否指定同意狀態，
 * 一律強制覆寫為 {@code CONFIRMED}——這是行銷寄送不得繞過的合規防線，也是
 * {@link #findIllegal} 存在的注入防線：後台勾選補寄名單時，只能從這份命中集合
 * 挑人，外部夾帶的 email 一律視為不合法。</p>
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

    private final AudienceSearchService audienceSearchService;
    private final EmailLogRepository emailLogRepository;
    private final ObjectMapper objectMapper;

    /** 注入 audience 搜尋服務、寄送記錄與 JSON 解析器 */
    public CouponRecipientService(AudienceSearchService audienceSearchService,
                                   EmailLogRepository emailLogRepository,
                                   ObjectMapper objectMapper) {
        this.audienceSearchService = audienceSearchService;
        this.emailLogRepository = emailLogRepository;
        this.objectMapper = objectMapper;
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
                new AudienceSearchService.SearchRequest(filters, null, page, PAGE_SIZE);
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
        AudienceSearchService.SurveyFilter survey =
            new AudienceSearchService.SurveyFilter(campaign.getFormKey(), null, answers);
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
}
