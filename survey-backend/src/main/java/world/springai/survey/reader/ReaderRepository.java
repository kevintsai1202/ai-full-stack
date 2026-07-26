package world.springai.survey.reader;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** 讀者帳戶資料存取層 */
public interface ReaderRepository extends JpaRepository<Reader, Long> {

    /** 依 email 查讀者（不分大小寫），登入時使用 */
    Optional<Reader> findByEmailIgnoreCase(String email);

    /**
     * 依 email 片段搜尋（不分大小寫、可分頁），後台讀者管理使用。
     *
     * <p><b>為什麼不用衍生查詢 {@code findByEmailContainingIgnoreCase}</b>：
     * 那個寫法綁成 {@code like '%' || ?1 || '%'}，參數雖有綁定（沒有 SQL injection），
     * 但值裡的 {@code %} 與 {@code _} 不會被跳脫——搜尋 {@code %} 或 {@code _}
     * 就會匹配全部讀者，一次把整張 reader 表（email、餘額、邀請碼、最後登入時間）
     * 序列化成單一回應。這裡改成顯式 {@code escape}，由呼叫端把萬用字元跳脫掉。</p>
     *
     * <p>{@code pageable} 是筆數上限（比照 {@link CreditTxnRepository} 的
     * 「無上限版 vs. 分頁版」慣例）：後台搜尋只是找人，不是匯出全表。</p>
     *
     * @param pattern 已跳脫並前後補上 {@code %} 的 LIKE 樣式
     */
    @Query("select r from Reader r where lower(r.email) like lower(:pattern) escape '\\' "
        + "order by r.email asc")
    List<Reader> searchByEmailPattern(@Param("pattern") String pattern, Pageable pageable);

    /**
     * 只更新 VIP 等級與到期日兩欄的條件式 UPDATE（後台授予／取消 VIP 使用）。
     *
     * <p><b>為什麼不能用 {@code save(reader)}</b>：{@link Reader} 沒有 {@code @Version}
     * 也沒有 {@code @DynamicUpdate}，Hibernate 的 UPDATE 會帶上所有可更新欄位，
     * <b>包含 {@code credits}</b>。於是「後台授予 VIP」會把 SELECT 當下讀到的餘額
     * 整個寫回去，靜默還原這段期間讀者在別的分頁解鎖文章所扣掉的點——但那筆
     * {@code credit_txn} 還留著，{@code reader.credits} 與帳本總和就此對不起來，
     * 而且沒有任何錯誤訊息。這裡只碰 tier 與 vip_expires_at，credits 永遠不在
     * UPDATE 敘述裡，與 {@link #addCredits} / {@link #deductCredits} 刻意避開
     * read-modify-write 的設計一致。</p>
     *
     * <p>單一 UPDATE 敘述本身即為原子操作，故呼叫端不需要額外的交易。</p>
     *
     * @return 受影響筆數，0 表示該讀者列不存在
     */
    @Modifying
    @Transactional
    @Query("update Reader r set r.tier = :tier, r.vipExpiresAt = :expiresAt where r.id = :id")
    int updateVip(@Param("id") Long id,
                  @Param("tier") String tier,
                  @Param("expiresAt") OffsetDateTime expiresAt);

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
