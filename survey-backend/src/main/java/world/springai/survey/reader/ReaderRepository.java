package world.springai.survey.reader;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** 讀者帳戶資料存取層 */
public interface ReaderRepository extends JpaRepository<Reader, Long> {

    /** 依 email 查讀者（不分大小寫），登入時使用 */
    Optional<Reader> findByEmailIgnoreCase(String email);

    /** 邀請碼是否已存在，產生新碼時用於避免碰撞 */
    boolean existsByReferralCode(String referralCode);

    /** 依邀請碼查推薦人，訂閱歸因時使用 */
    Optional<Reader> findByReferralCode(String referralCode);

    /**
     * 加點（正數）。回傳受影響筆數，0 表示該讀者不存在。
     *
     * <p>用條件式 UPDATE 而不是「讀出來改再存回」：後者在併發下會覆蓋
     * 另一筆交易剛寫入的餘額（讀到舊值 → 加 → 寫回，另一筆的變動就消失了）。
     * 這裡直接讓資料庫算 {@code credits = credits + :delta}。</p>
     */
    @Modifying
    @Transactional
    @Query("update Reader r set r.credits = r.credits + :delta where r.id = :id")
    int addCredits(@Param("id") Long id, @Param("delta") int delta);
}
