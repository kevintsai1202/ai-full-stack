package world.springai.survey.audience;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** 依條件取得可寄送名單（同意、未退訂、去重） */
@Service
public class RecipientService {

    private final SurveyResponseRepository repository;
    private final AudienceSearchService searchService;
    private final AudienceSegmentService segmentService;

    /** Spring 正式注入：支援舊 role/interest 與新版動態／保存分眾。 */
    @Autowired
    public RecipientService(
            SurveyResponseRepository repository,
            AudienceSearchService searchService,
            AudienceSegmentService segmentService) {
        this.repository = repository;
        this.searchService = searchService;
        this.segmentService = segmentService;
    }

    /** 測試與舊呼叫相容建構式；只支援 role/interest。 */
    public RecipientService(SurveyResponseRepository repository) {
        this.repository = repository;
        this.searchService = null;
        this.segmentService = null;
    }

    /** 取得符合篩選的去重收件 email；role/interest 空字串視為不限 */
    public List<String> recipients(String role, String interest) {
        return repository.findRecipients(blankToNull(role), blankToNull(interest));
    }

    /** 取得目前已確認且未退訂的去重訂閱人數。 */
    public long subscriberCount() {
        return repository.countRecipients();
    }

    /**
     * 取得動態分眾收件人；保存分眾與臨時條件都會被強制套用 CONFIRMED。
     * 若同時提供舊 role/interest，結果取交集。
     */
    public List<String> recipients(
            String role,
            String interest,
            AudienceSearchService.Filters filters,
            Long savedSegmentId) {
        if (filters == null && savedSegmentId == null) {
            return recipients(role, interest);
        }
        if (searchService == null || segmentService == null) {
            throw new IllegalStateException("動態分眾服務未注入");
        }
        AudienceSearchService.Filters effective = savedSegmentId == null
            ? filters
            : segmentService.get(savedSegmentId).filters();
        List<String> dynamic = new java.util.ArrayList<>(
            searchService.findRecipientEmails(effective));
        if (StringUtils.hasText(role) || StringUtils.hasText(interest)) {
            dynamic.retainAll(recipients(role, interest));
        }
        return List.copyOf(dynamic);
    }

    /** 空白字串轉 null，讓 native query 的「is null 不限」生效 */
    private String blankToNull(String v) {
        return StringUtils.hasText(v) ? v : null;
    }
}
