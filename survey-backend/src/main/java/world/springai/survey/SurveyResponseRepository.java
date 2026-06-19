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

    /**
     * 依 email（不分大小寫）將該使用者所有問卷回應標記為退訂。
     * 使用 LOWER() 函式進行不分大小寫比對，確保退訂冪等且容忍大小寫差異。
     */
    @Modifying
    @Transactional
    @Query("UPDATE SurveyResponse r SET r.unsubscribed = true WHERE LOWER(r.email) = LOWER(:email)")
    void unsubscribeByEmail(@Param("email") String email);
}
