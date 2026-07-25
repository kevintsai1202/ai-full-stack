package world.springai.survey.reader;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

/** 登入 token 資料存取層 */
public interface LoginTokenRepository extends JpaRepository<LoginToken, Long> {

    /** 依雜湊查 token（驗證 magic link 時使用；明文永不入庫） */
    Optional<LoginToken> findByTokenHash(String tokenHash);

    /** 某 email 在指定時間之後發出的 token 數，用於登入信節流 */
    long countByEmailAndCreatedAtAfter(String email, OffsetDateTime since);

    /**
     * 原子地把尚未使用的 token 標記為已使用。
     *
     * <p>條件包含 usedAt IS NULL，因此併發下只有一個請求能成功——回傳 0 表示
     * 這個 token 已被別人兌換掉了。這是「一次性」的實際保證所在：先查再寫的
     * 做法會有 TOCTOU 窗口，兩個請求都會查到「未使用」而各自標記成功。</p>
     *
     * @return 受影響筆數；1 表示本次兌換成功取得，0 表示已被兌換
     */
    @Modifying
    @Transactional
    @Query("update LoginToken t set t.usedAt = :now where t.tokenHash = :hash and t.usedAt is null")
    int markUsedIfUnused(@Param("hash") String tokenHash, @Param("now") OffsetDateTime now);
}
