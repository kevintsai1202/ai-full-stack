package world.springai.survey.audience;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** 問卷回應資料存取層 */
public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, Long> {

    /** 依建立時間新到舊回傳全部回應（管理 API 用） */
    List<SurveyResponse> findAllByOrderByCreatedAtDesc();

    /** 檢查 email 是否已存在（不分大小寫），匯入時用於略過重複 */
    boolean existsByEmailIgnoreCase(String email);

    /** 指定來源的待確認名單（尚未同意且未退訂），邀請確認信寄送對象 */
    List<SurveyResponse> findBySourceAndConsentFalseAndUnsubscribedFalse(String source);

    /**
     * 依 email 取最新一筆名單資料（不分大小寫）。
     *
     * <p>刻意用 findFirst + OrderBy 而非 findByEmail：同一個 email 可能有多筆
     * （已在正式資料中實測到有人相隔一個月填了兩次問卷）。若寫成回傳
     * Optional 的 findByEmailIgnoreCase，遇到多筆時 Spring Data 會拋
     * IncorrectResultSizeDataAccessException，讓確認訂閱整個失敗。</p>
     */
    Optional<SurveyResponse> findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    /** 指定來源已確認訂閱（同意且未退訂）的人數，邀請成效統計用 */
    long countBySourceAndConsentTrueAndUnsubscribedFalse(String source);

    /**
     * 只更新顯示名稱一欄的條件式 UPDATE（讀者在 {@code /r/me} 改名時使用）。
     *
     * <p><b>為什麼不能用 {@code save(entity)}</b>：{@link SurveyResponse} 沒有
     * {@code @Version} 也沒有 {@code @DynamicUpdate}，Hibernate 的 UPDATE 會帶上
     * <b>所有</b>可更新欄位——包含 {@code consent}、{@code unsubscribed}、
     * {@code last_engaged_at}。失效情境：讀者在 A 分頁開著 {@code /r/me}
     * （SELECT 讀到 {@code unsubscribed=false}），期間他在 B 分頁或從信件連結點了退訂，
     * {@code /api/survey/unsubscribe} 讓 {@link #unsubscribeByEmail} 把它改成 true；
     * 然後 A 分頁按下「儲存顯示名稱」→ 整列 UPDATE 把 {@code unsubscribed} 寫回
     * <b>false</b>。<b>退訂狀態被無聲還原，而退訂是合規事項</b>，沒有任何錯誤訊息。
     * 同理 {@link #confirmByEmail} 寫入的 {@code consent} 也會被覆蓋。
     * 這與本專案已修的兩個 Critical（{@code grantVip} 與登入路徑的整列寫回）
     * 是同一個機制，只是發生在最敏感的同意狀態上。</p>
     *
     * <p><b>{@code clearAutomatically = true} 不是可選的</b>：{@code findFirstBy...}
     * 回傳的是<b>受管理的</b> entity，光是在它身上呼叫 {@code setName(...)}，
     * Hibernate 的 dirty check 就會在提交時自己補一道帶全欄位的 UPDATE，
     * <b>完全不需要呼叫 {@code save()}</b>。換掉 {@code save()} 並不夠——必須讓 entity
     * 脫離管理。作法與理由逐字同 {@code ReaderRepository.touchLastLogin}。</p>
     *
     * <p><b>為什麼以 id 而非 email 為條件</b>（與本檔其他 UPDATE 不同）：同一個 email
     * 可能有多筆（已在正式資料中實測到有人相隔一個月填了兩次問卷，見
     * {@link #findFirstByEmailIgnoreCaseOrderByCreatedAtDesc}）。改用 email 當條件會把
     * 名稱寫進<b>全部</b>歷史列，那是一個沒人要求的行為變更；以 id 為條件則精確保留
     * 「只改最新那一筆」的既有語意。{@code consent}／{@code unsubscribed} 用 email
     * 是對的——退訂必須涵蓋該 email 的所有列。</p>
     *
     * <p>顯示名稱的長度截斷由呼叫端（{@code ReaderProfileService}）負責，
     * 那裡才知道以 code point 計數的規則。</p>
     *
     * @return 受影響筆數，0 表示該列已不存在（呼叫端應回 404，不可回報成功）
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update SurveyResponse s set s.name = :name where s.id = :id")
    int updateName(@Param("id") Long id, @Param("name") String name);

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
