package world.springai.survey.audience;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/** 問卷回應資料存取層 */
public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, Long> {

    /** 依建立時間新到舊回傳全部回應（管理 API 用） */
    List<SurveyResponse> findAllByOrderByCreatedAtDesc();

    /** 檢查 email 是否已存在（不分大小寫），匯入時用於略過重複 */
    boolean existsByEmailIgnoreCase(String email);

    /** 指定來源的待確認名單（尚未同意且未退訂），邀請確認信寄送對象 */
    List<SurveyResponse> findBySourceAndConsentFalseAndUnsubscribedFalse(String source);

    /** 指定來源已確認訂閱（同意且未退訂）的人數，邀請成效統計用 */
    long countBySourceAndConsentTrueAndUnsubscribedFalse(String source);

    /** 將指定 email（大小寫不敏感）標記為已退訂；回傳受影響筆數 */
    @Modifying
    @Transactional
    @Query("update SurveyResponse s set s.unsubscribed = true where lower(s.email) = lower(:email)")
    int unsubscribeByEmail(@Param("email") String email);

    /** 確認訂閱：將指定 email（大小寫不敏感）轉為已同意；回傳受影響筆數 */
    @Modifying
    @Transactional
    @Query("update SurveyResponse s set s.consent = true where lower(s.email) = lower(:email)")
    int confirmByEmail(@Param("email") String email);

    /** 可寄送名單：同意且未退訂的去重 email（小寫），供未來批量發送使用 */
    @Query("select distinct lower(s.email) from SurveyResponse s where s.consent = true and s.unsubscribed = false")
    List<String> findDistinctRecipients();

    /**
     * 該 email 是否為已確認訂閱者（同意且未退訂）。
     *
     * <p>讀者端的授權判斷用它——訂閱狀態只有名單中心這一份真相，
     * reader 表刻意不自帶訂閱狀態。</p>
     */
    @Query("""
        select count(s) > 0 from SurveyResponse s
         where lower(s.email) = lower(:email)
           and s.consent = true
           and s.unsubscribed = false
        """)
    boolean isSubscribed(@Param("email") String email);

    /**
     * 更新最後互動時間（供參與度分級使用）。
     *
     * <p>高可靠互動訊號：確認訂閱、登入、解鎖文章、更新個人資料。
     * 開信是低可靠訊號（信箱常封鎖圖片）但同樣會更新。</p>
     *
     * @return 受影響筆數；0 表示該 email 不在名單中（讀者可能尚未訂閱）
     */
    @Modifying
    @Transactional
    @Query("update SurveyResponse s set s.lastEngagedAt = :at where lower(s.email) = lower(:email)")
    int touchEngagement(@Param("email") String email, @Param("at") OffsetDateTime at);

    /**
     * 可寄送名單（去重小寫 email）：同意且未退訂；
     * role 為 null 不限；interest 為 null 不限，否則用 jsonb 包含比對。
     */
    @Query(value = """
        select distinct lower(email) from survey_response
        where consent = true and unsubscribed = false
          and (:role is null or role = :role)
          and (:interest is null or interest @> jsonb_build_array(:interest))
        """, nativeQuery = true)
    java.util.List<String> findRecipients(@Param("role") String role, @Param("interest") String interest);
}
