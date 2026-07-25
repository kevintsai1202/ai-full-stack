package world.springai.survey.reader;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 讀者帳戶資料存取層 */
public interface ReaderRepository extends JpaRepository<Reader, Long> {

    /** 依 email 查讀者（不分大小寫），登入時使用 */
    Optional<Reader> findByEmailIgnoreCase(String email);

    /** 邀請碼是否已存在，產生新碼時用於避免碰撞 */
    boolean existsByReferralCode(String referralCode);

    /** 依邀請碼查推薦人，訂閱歸因時使用 */
    Optional<Reader> findByReferralCode(String referralCode);
}
