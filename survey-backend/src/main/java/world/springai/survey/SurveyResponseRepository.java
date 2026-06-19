package world.springai.survey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 問卷回應資料存取層 */
public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, Long> {

    /** 依建立時間新到舊回傳全部回應（管理 API 用） */
    List<SurveyResponse> findAllByOrderByCreatedAtDesc();

    /** 將指定 email（大小寫不敏感）標記為已退訂；回傳受影響筆數 */
    @Modifying
    @Transactional
    @Query("update SurveyResponse s set s.unsubscribed = true where lower(s.email) = lower(:email)")
    int unsubscribeByEmail(@Param("email") String email);

    /** 可寄送名單：同意且未退訂的去重 email（小寫），供未來批量發送使用 */
    @Query("select distinct lower(s.email) from SurveyResponse s where s.consent = true and s.unsubscribed = false")
    List<String> findDistinctRecipients();
}
