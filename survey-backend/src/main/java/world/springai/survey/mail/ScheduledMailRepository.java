package world.springai.survey.mail;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 待寄佇列的存取。
 *
 * <p>所有狀態轉換都是「帶舊狀態條件」的 UPDATE，回傳影響列數：派送器認領（PENDING → SENDING）
 * 與取消（PENDING → CANCELLED）可能同時碰同一列，只有一方改得到，另一方拿到 0 就知道自己輸了。
 * 不要改成「先讀再存」，那會讓兩邊都以為自己成功。</p>
 */
public interface ScheduledMailRepository extends JpaRepository<ScheduledMail, Long> {

    /** 到期的待寄信 id（依到期時間、id 排序），供派送器逐筆認領 */
    @Query(value = """
        SELECT id FROM scheduled_mail
        WHERE status = 'PENDING' AND next_attempt_at <= :now
        ORDER BY next_attempt_at, id
        LIMIT :limit
        """, nativeQuery = true)
    List<Long> findDueIds(@Param("now") OffsetDateTime now, @Param("limit") int limit);

    /** 認領：PENDING → SENDING，嘗試次數 +1；回傳 1 才可以寄 */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
        UPDATE scheduled_mail
        SET status = 'SENDING', attempts = attempts + 1, updated_at = now()
        WHERE id = :id AND status = 'PENDING'
        """, nativeQuery = true)
    int claim(@Param("id") long id);

    /** 取消：只有還在 PENDING 的列取消得了；已被認領或已寄出回傳 0 */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
        UPDATE scheduled_mail
        SET status = 'CANCELLED', updated_at = now()
        WHERE id = :id AND status = 'PENDING'
        """, nativeQuery = true)
    int cancel(@Param("id") long id);

    /** 送出成功：SENDING → SENT，記下 SES MessageId */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
        UPDATE scheduled_mail
        SET status = 'SENT', provider_message_id = :messageId, error = NULL, updated_at = now()
        WHERE id = :id AND status = 'SENDING'
        """, nativeQuery = true)
    int markSent(@Param("id") long id, @Param("messageId") String messageId);

    /** 暫時性錯誤：SENDING → PENDING，延後到 nextAttemptAt 再試 */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
        UPDATE scheduled_mail
        SET status = 'PENDING', error = :error, next_attempt_at = :nextAttemptAt, updated_at = now()
        WHERE id = :id AND status = 'SENDING'
        """, nativeQuery = true)
    int retryLater(@Param("id") long id, @Param("error") String error,
                   @Param("nextAttemptAt") OffsetDateTime nextAttemptAt);

    /** 放棄：SENDING → FAILED */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
        UPDATE scheduled_mail
        SET status = 'FAILED', error = :error, updated_at = now()
        WHERE id = :id AND status = 'SENDING'
        """, nativeQuery = true)
    int markFailed(@Param("id") long id, @Param("error") String error);

    /** 指定狀態的列數；額度計算用 PENDING（已排入但 SES 還沒算進已寄量） */
    long countByStatus(String status);

    /** 卡在 SENDING 且最後變更早於 before 的列數（程序中斷的跡象，需人工確認） */
    @Query(value = """
        SELECT count(*) FROM scheduled_mail
        WHERE status = 'SENDING' AND updated_at < :before
        """, nativeQuery = true)
    long countStuck(@Param("before") OffsetDateTime before);
}
