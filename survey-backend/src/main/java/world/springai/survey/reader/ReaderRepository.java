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

    /**
     * 條件式扣點：只有餘額足夠時才扣。回傳受影響筆數，0 表示餘額不足或讀者不存在。
     *
     * <p><b>{@code WHERE credits >= :cost} 是併發防線</b>，不是重複檢查。
     * 呼叫端已經讀過餘額並判斷足夠，但在「讀取」與「扣款」之間，
     * 同一讀者的另一個請求可能已經扣走點數。把條件放進 SQL 讓資料庫
     * 以單一原子操作決定成敗——正確性來自受影響筆數，不是來自先前的檢查。</p>
     *
     * <p><b>{@code clearAutomatically = true} 不可省略</b>：JPA 的一級快取會讓
     * 同一交易內、扣款後再對同一 id 呼叫 {@code findById} 直接命中快取，
     * 拿回扣款「之前」載入的舊 {@code Reader} 物件，完全不會重新查詢資料庫。
     * 少了這個設定，呼叫端想在扣款後讀取最新餘額（見
     * {@link UnlockService#unlock}）會靜默失敗——查詢照樣執行、
     * Hibernate 卻直接回傳快取物件，看起來像是修好了，實際上餘額仍是舊的。</p>
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update Reader r set r.credits = r.credits - :cost where r.id = :id and r.credits >= :cost")
    int deductCredits(@Param("id") Long id, @Param("cost") int cost);
}
