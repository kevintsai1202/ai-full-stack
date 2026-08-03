package world.springai.survey.form;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 信中一鍵投票（含讀者頁快投）資料存取層 */
public interface SurveyVoteRepository extends JpaRepository<SurveyVote, Long> {

    /**
     * 依「問卷 + 身分類型 + 身分識別鍵」複合鍵找票，供改票（upsert）流程判斷
     * 是否已投過票。ANON 身分因唯一約束排除，此查詢對 ANON 不具冪等意義。
     */
    Optional<SurveyVote> findByFormKeyAndIdentityTypeAndIdentityKey(
        String formKey, String identityType, String identityKey);
}
