package world.springai.survey.newsletter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** 電子報批次資料存取層 */
public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    /**
     * 下架：只寫 {@code published_at}（設回 NULL）與 {@code status} 兩欄的條件式 UPDATE。
     *
     * <p>{@code published_at} 為 NULL 讓 {@code isPublished()} 立刻為 false（文章從
     * {@code /r/archive} 與 {@code /r/news/{slug}} 消失）；{@code status} 一併改成
     * {@link Campaign#STATUS_UNPUBLISHED}，否則後台只能靠「{@code publishedAt} 是不是
     * null」反推，而歷史列表的 pill 會繼續顯示 {@code published}——畫面說已發布、
     * 事實是讀者看不到。它同時是重新上架端點唯一的守門依據。</p>
     *
     * <p><b>為什麼不能用 {@code save(campaign)}</b>：{@link Campaign} 沒有 {@code @Version}
     * 也沒有 {@code @DynamicUpdate}，Hibernate 的 UPDATE 會帶上<b>所有</b>可更新欄位——
     * 包含 {@code subject}／{@code markdown}／{@code tier}／{@code credit_cost}／
     * {@code accepted_count} 等。於是「下架」會把 SELECT 當下讀到的整列快照寫回去，
     * 靜默還原這段期間別的請求（例如同時在跑的 reschedule 統計更新）對這一列的變更，
     * 而且沒有任何錯誤訊息。本專案已有兩個 Critical 源於整列寫回，
     * 作法比照 {@code ReaderRepository.updateVip} 與 {@code touchLastLogin}。</p>
     *
     * <p><b>WHERE 帶上 status 是併發防線</b>，不是重複檢查：service 層已讀過狀態並判斷是
     * {@code published}，但在「讀取」與「更新」之間狀態可能已被別的請求改掉。
     * 正確性來自受影響筆數，不是來自先前的檢查。</p>
     *
     * <p><b>{@code clearAutomatically = true}</b>：清掉一級快取，避免呼叫端在同一交易內
     * 仍持有被管理的 {@link Campaign} 物件——只要那個物件還被 Hibernate 管理，
     * 之後對它做任何 setter，提交時的 dirty check 都會再發一次帶全欄位的 UPDATE，
     * 等於繞過本方法白做工。</p>
     *
     * @param newStatus 要寫入的新狀態。刻意用參數而非在 JPQL 裡寫死字串常值：
     *                  JPQL 無法引用 Java 常數，寫死會讓 {@code 'unpublished'} 這個值
     *                  在 {@link Campaign} 之外多出第二個定義點，改一邊漏一邊。
     * @return 受影響筆數，0 表示該列不存在或狀態已不是 expectedStatus
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update Campaign c set c.publishedAt = null, c.status = :newStatus "
        + "where c.id = :id and c.status = :expectedStatus")
    int markUnpublished(@Param("id") Long id,
                        @Param("expectedStatus") String expectedStatus,
                        @Param("newStatus") String newStatus);

    /**
     * 重新上架：只寫 {@code published_at} 與 {@code status} 兩欄的條件式 UPDATE，
     * 與 {@link #markUnpublished} 對稱。
     *
     * <p><b>WHERE 帶 {@code publishedAt is null} 是真正的併發防線</b>：它直接表達
     * 「這一列目前對外不可見」這個前提。只比對 {@code status} 是不夠的——狀態相同
     * 而 {@code published_at} 已被別的請求填回去時，本 UPDATE 會用一個新的時間戳
     * 覆蓋掉那次發布，且沒有任何錯誤。正確性來自受影響筆數。</p>
     *
     * <p><b>不重用 {@link #markUnpublished}</b>（例如把 publishedAt 也做成參數）：
     * 那會產生一支「可把任意 campaign 改成任意發布狀態」的通用方法，
     * 兩個方向各自的守門條件（{@code expectedStatus} 與 {@code published_at is null}）
     * 也就無處可放。兩支各自封閉的方法比一支通用方法安全。</p>
     *
     * <p><b>不碰其他任何欄位</b>：{@code markdown}／{@code tier}／{@code credit_cost}
     * 都維持下架前的值，重新上架不是「重新發布一篇新文章」。
     * {@code clearAutomatically} 的理由同 {@link #markUnpublished}。</p>
     *
     * @return 受影響筆數，0 表示該列不存在、狀態已變、或已被別的請求重新上架
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update Campaign c set c.publishedAt = :publishedAt, c.status = :newStatus "
        + "where c.id = :id and c.status = :expectedStatus and c.publishedAt is null")
    int markRepublished(@Param("id") Long id,
                        @Param("expectedStatus") String expectedStatus,
                        @Param("newStatus") String newStatus,
                        @Param("publishedAt") java.time.OffsetDateTime publishedAt);

    /** 依建立時間新到舊列出（歷史頁用） */
    List<Campaign> findAllByOrderByCreatedAtDesc();

    /**
     * archive 列表：只列「真正可開啟」的已發布文章，新到舊。
     *
     * <p>同時要求 slug 與 publishedAt 皆非 NULL，是防禦既有資料的第二道關卡——
     * 即使 service 層已擋下「設了 publishedAt 卻沒設 slug」的矛盾輸入，資料庫裡
     * 仍可能存在手動 SQL 造成的這種列；若查詢只看 publishedAt，archive 會列出
     * 一篇沒有 slug 可組連結的文章，讀者點下去會打到 /r/news/（空 path variable）
     * 而 404。</p>
     */
    List<Campaign> findBySlugIsNotNullAndPublishedAtIsNotNullOrderByPublishedAtDesc();

    /** 依 slug 查單篇文章 */
    Optional<Campaign> findBySlug(String slug);
}
