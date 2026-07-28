package world.springai.survey.reader;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** 邀請轉換資料存取層。 */
public interface ReferralConversionRepository extends JpaRepository<ReferralConversion, Long> {

    Optional<ReferralConversion> findByInviteeEmailNormalized(String email);

    /** 確認與人工審核都鎖定同一列，避免同時重複發點。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ReferralConversion c where c.inviteeEmailNormalized = :email")
    Optional<ReferralConversion> findForUpdate(@Param("email") String email);

    /** 人工審核鎖定指定轉換。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ReferralConversion c where c.id = :id")
    Optional<ReferralConversion> findByIdForUpdate(@Param("id") Long id);

    long countByReferrerIdAndStatusAndConfirmedAtBetween(
        Long referrerId, String status, OffsetDateTime from, OffsetDateTime to);

    long countByReferrerIdAndConfirmedAtAfter(Long referrerId, OffsetDateTime after);

    long countByReferrerIdAndStatus(Long referrerId, String status);

    List<ReferralConversion> findTop100ByStatusOrderByConfirmedAtAsc(String status);

    List<ReferralConversion> findByStatusOrderByConfirmedAtDesc(String status);
}

