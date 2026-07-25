package world.springai.survey.reader;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

/** 登入 token 資料存取層 */
public interface LoginTokenRepository extends JpaRepository<LoginToken, Long> {

    /** 依雜湊查 token（驗證 magic link 時使用；明文永不入庫） */
    Optional<LoginToken> findByTokenHash(String tokenHash);

    /** 某 email 在指定時間之後發出的 token 數，用於登入信節流 */
    long countByEmailAndCreatedAtAfter(String email, OffsetDateTime since);
}
