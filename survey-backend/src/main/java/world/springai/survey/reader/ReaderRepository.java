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

    /**
     * 只更新最後登入時間一欄的條件式 UPDATE（登入路徑使用）。
     *
     * <p><b>為什麼不能用 {@code save(reader)}</b>：理由與 {@link #updateVip} 逐字相同——
     * {@link Reader} 沒有 {@code @Version} 也沒有 {@code @DynamicUpdate}，整列 UPDATE 會
     * 連 {@code credits} 一起寫回 SELECT 當下的快照。而登入路徑比後台授予 VIP <b>更容易
     * 撞上</b>：兩端都是讀者本人的即時操作——A 分頁點 magic link 進入交易讀到
     * {@code credits=300}，同時 B 分頁的解鎖 POST 讓 {@code deductCredits} 把 DB 改成 290
     * 並寫入 {@code delta=-10} 的帳本列；A 提交時把 300 寫回去，扣點被靜默還原，
     * 帳本那筆 -10 卻留著，{@code reader.credits} 與 {@code sum(credit_txn)} 從此對不上，
     * 且無任何錯誤訊息。</p>
     *
     * <p><b>{@code flushAutomatically = true}</b>：同一交易內若已有待寫入的變更
     * （例如首次登入剛 INSERT 的新讀者列），必須先 flush 再執行本 UPDATE，
     * 否則 UPDATE 會打在一列還不存在的資料上。</p>
     *
     * <p><b>{@code clearAutomatically = true}</b>：UPDATE 之後清掉一級快取，讓被載入的
     * {@link Reader} 物件<b>脫離管理</b>。這不只是為了「重讀能拿到新值」——更關鍵的是：
     * 只要那個物件還被 Hibernate 管理，呼叫端在交易內對它做<b>任何</b> setter，
     * 提交時的 dirty check 都會再發一次帶全欄位（含 {@code credits}）的 UPDATE，
     * 等於繞過本方法白做工。呼叫端據此可以安全地把 {@code lastLoginAt} 設回物件上
     * 供回傳值使用（見 {@code ReaderAccountService#findOrCreate}）。</p>
     *
     * @return 受影響筆數，0 表示該讀者列不存在
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("update Reader r set r.lastLoginAt = :now where r.id = :id")
    int touchLastLogin(@Param("id") Long id, @Param("now") OffsetDateTime now);

    /** 邀請碼是否已存在，產生新碼時用於避免碰撞 */
    boolean existsByReferralCode(String referralCode);

    /** 依邀請碼查推薦人，訂閱歸因時使用 */
    Optional<Reader> findByReferralCode(String referralCode);

    /**
     * 某位推薦人所帶進來的被邀者 email（{@code referred_by} 指向他的讀者列）。
     *
     * <p><b>為什麼是取 email 而不是 {@code count(*)}</b>：邀請人數是「帳本的 REFERRAL
     * note」與「本欄位」<b>兩個來源的聯集去重計數</b>（見 {@code ReferralService#stats}）。
     * 兩邊各自都有涵蓋不到的情境：帳本在獎勵被關成 0 時完全不寫，本欄位則只在被邀者
     * <b>首次登入建立帳戶</b>時寫入（{@code ReaderAccountService#createWithSignupGrant}，
     * 既有帳戶不回填），而絕大多數電子報訂閱者永遠不會來 {@code /r/} 登入。
     * 只數其中一邊都會在預設設定下顯示錯的人數，所以必須取得<b>可去重的鍵</b>
     * （email，與帳本 note 存的是同一個值）而非單純的筆數。</p>
     *
     * <p><b>個資</b>：這裡回傳的是被邀者 email，而邀請碼是可公開分享的連結——透過它
     * 訂閱的陌生人與邀請人並不認識。這些值<b>只能用來算集合大小</b>，
     * 絕不可進入任何 HTTP 回應（{@code ReferralService.ReferralStats} 刻意只帶數字，
     * 並由 {@code ReaderPortalControllerTest.neverLeaksInviteeEmail} 釘住）。</p>
     *
     * <p>刻意沒有留下 {@code countByReferredBy}：那個方法只能算單一來源的筆數，
     * 留著會邀請日後有人把「只數 referred_by」的缺陷重新加回來。</p>
     */
    @Query("select r.email from Reader r where r.referredBy = :referrerId")
    List<String> findInviteeEmailsByReferredBy(@Param("referrerId") Long referrerId);

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
     *
     * <p><b>{@code flushAutomatically = true} 是給未來的保險</b>：{@code clearAutomatically}
     * 會在 UPDATE 後 {@code em.clear()}，但<b>不會先 flush</b>。目前之所以安全，
     * 只是因為 {@link CreditTxn} 用 {@code GenerationType.IDENTITY}——persist 當下就發出
     * INSERT，沒有東西留在 persistence context 裡等著被清掉。若日後為了批次效能把主鍵
     * 改成 {@code SEQUENCE}，批次扣點迴圈中前面幾筆尚未 flush 的帳本列就會被
     * {@code clear()} 丟棄，而餘額的 UPDATE 已經打進資料庫——餘額扣了、帳本沒寫，
     * 直接破壞「{@code reader.credits} 恆等於 {@code sum(credit_txn)}」這條核心不變式，
     * 而且沒有任何錯誤訊息。加這個旗標的成本是零。</p>
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("update Reader r set r.credits = r.credits - :cost where r.id = :id and r.credits >= :cost")
    int deductCredits(@Param("id") Long id, @Param("cost") int cost);
}
