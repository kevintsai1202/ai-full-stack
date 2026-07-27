package world.springai.survey.reader;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;

/** Magic Link IP／全域節流資料存取層。 */
public interface LoginRequestAttemptRepository extends JpaRepository<LoginRequestAttempt, Long> {

    /** 指定來源在節流視窗內的請求數。 */
    long countByIpHashAndCreatedAtAfter(String ipHash, OffsetDateTime since);

    /** 全站在節流視窗內的請求數。 */
    long countByCreatedAtAfter(OffsetDateTime since);

    /** 清除超過保留期限的防濫用紀錄，避免資料無限成長。 */
    long deleteByCreatedAtBefore(OffsetDateTime before);
}
