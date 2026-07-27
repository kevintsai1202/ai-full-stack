package world.springai.survey.newsletter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Campaign 逐收件人狀態資料存取層。 */
public interface CampaignRecipientRepository extends JpaRepository<CampaignRecipient, Long> {

    /** 取得某篇文章所有已建立的收件人狀態。 */
    List<CampaignRecipient> findByCampaignId(Long campaignId);

    /** 依文章與正規化 email 取得唯一狀態。 */
    Optional<CampaignRecipient> findByCampaignIdAndEmailNormalized(
        Long campaignId, String emailNormalized);

    /** 取得某批次的收件人。 */
    List<CampaignRecipient> findByBatchIdOrderByEmailNormalized(Long batchId);

    /** 某文章特定狀態的人數，供 campaign 歷史摘要更新。 */
    long countByCampaignIdAndStatus(Long campaignId, String status);

    /** 某文章已建立狀態的唯一收件人數。 */
    long countByCampaignId(Long campaignId);

    /** 將 email_log 的歷史資料匯入永久狀態，供新 migration 後仍走舊 send 入口的資料使用。 */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO campaign_recipient (
            campaign_id, person_id, batch_id, email, email_normalized,
            status, provider_message_id, error, scheduled_at, sent_at,
            created_at, updated_at
        ) VALUES (
            :campaignId,
            (SELECT p.id FROM audience_person p WHERE p.email_normalized = lower(trim(:email)) LIMIT 1),
            :batchId,
            :email,
            lower(trim(:email)),
            :status,
            :providerId,
            :error,
            :scheduledAt,
            :sentAt,
            :createdAt,
            :createdAt
        )
        ON CONFLICT (campaign_id, email_normalized) DO NOTHING
        """, nativeQuery = true)
    int importHistorical(
        @Param("campaignId") Long campaignId,
        @Param("batchId") Long batchId,
        @Param("email") String email,
        @Param("status") String status,
        @Param("providerId") String providerId,
        @Param("error") String error,
        @Param("scheduledAt") OffsetDateTime scheduledAt,
        @Param("sentAt") OffsetDateTime sentAt,
        @Param("createdAt") OffsetDateTime createdAt);

    /**
     * 原子保留新收件人。ON CONFLICT 保證兩個同時送出的請求只有一個能取得寄送權。
     */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO campaign_recipient (
            campaign_id, person_id, batch_id, email, email_normalized,
            status, scheduled_at, created_at, updated_at
        )
        SELECT :campaignId, p.id, :batchId, p.email, p.email_normalized,
               :status, :scheduledAt, now(), now()
          FROM audience_person p
         WHERE p.email_normalized = :email
        ON CONFLICT (campaign_id, email_normalized) DO NOTHING
        """, nativeQuery = true)
    int reserveNew(
        @Param("campaignId") Long campaignId,
        @Param("batchId") Long batchId,
        @Param("email") String email,
        @Param("status") String status,
        @Param("scheduledAt") OffsetDateTime scheduledAt);

    /** 只有失敗或已取消者可被原子保留重試；已寄出與排程中的列永遠不會被覆寫。 */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
        UPDATE campaign_recipient
           SET batch_id = :batchId,
               status = :status,
               provider_message_id = NULL,
               error = NULL,
               scheduled_at = :scheduledAt,
               sent_at = NULL,
               updated_at = now()
         WHERE campaign_id = :campaignId
           AND email_normalized = :email
           AND status IN ('FAILED', 'CANCELLED')
        """, nativeQuery = true)
    int reserveRetry(
        @Param("campaignId") Long campaignId,
        @Param("batchId") Long batchId,
        @Param("email") String email,
        @Param("status") String status,
        @Param("scheduledAt") OffsetDateTime scheduledAt);

    /** 寄送或排程結果只允許由同一批次回寫，避免舊請求覆蓋新狀態。 */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
        UPDATE campaign_recipient
           SET status = :status,
               provider_message_id = :providerId,
               error = :error,
               sent_at = :sentAt,
               updated_at = now()
         WHERE campaign_id = :campaignId
           AND email_normalized = :email
           AND batch_id = :batchId
           AND status IN ('SENDING', 'SCHEDULED')
        """, nativeQuery = true)
    int finishAttempt(
        @Param("campaignId") Long campaignId,
        @Param("batchId") Long batchId,
        @Param("email") String email,
        @Param("status") String status,
        @Param("providerId") String providerId,
        @Param("error") String error,
        @Param("sentAt") OffsetDateTime sentAt);

    /** 已到時間的排程在讀取列表前整理為 SENT。 */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
        UPDATE campaign_recipient
           SET status = 'SENT', sent_at = scheduled_at, updated_at = now()
         WHERE campaign_id = :campaignId
           AND status = 'SCHEDULED'
           AND scheduled_at <= :now
        """, nativeQuery = true)
    int markElapsedSchedules(
        @Param("campaignId") Long campaignId,
        @Param("now") OffsetDateTime now);

    /** 成功取消 provider 排程後允許日後重新選取。 */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
        UPDATE campaign_recipient
           SET status = 'CANCELLED', updated_at = now()
         WHERE id = :id AND batch_id = :batchId AND status = 'SCHEDULED'
        """, nativeQuery = true)
    int markCancelled(@Param("id") Long id, @Param("batchId") Long batchId);
}
