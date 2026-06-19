package world.springai.survey;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** 依條件取得可寄送名單（同意、未退訂、去重） */
@Service
public class RecipientService {

    private final SurveyResponseRepository repository;

    public RecipientService(SurveyResponseRepository repository) {
        this.repository = repository;
    }

    /** 取得符合篩選的去重收件 email；role/interest 空字串視為不限 */
    public List<String> recipients(String role, String interest) {
        return repository.findRecipients(blankToNull(role), blankToNull(interest));
    }

    /** 空白字串轉 null，讓 native query 的「is null 不限」生效 */
    private String blankToNull(String v) {
        return StringUtils.hasText(v) ? v : null;
    }
}
