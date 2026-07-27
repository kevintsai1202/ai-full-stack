package world.springai.survey.newsletter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
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

    /** 補寄完成後以逐收件人狀態回寫 campaign 的累計摘要，不整列 save 避免併發覆蓋。 */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update Campaign c set c.recipientCount = :recipients, "
        + "c.acceptedCount = :accepted, c.failedCount = :failed "
        + "where c.id = :id")
    int updateDeliveryTotals(@Param("id") Long id,
                             @Param("recipients") int recipients,
                             @Param("accepted") int accepted,
                             @Param("failed") int failed);

    /**
     * 將已到寄送時間但仍停在 scheduled 的舊資料整理為 sent。
     *
     * <p>只更新狀態欄位，不以 {@code save(entity)} 整列寫回，避免覆蓋同時間其他請求
     * 更新的主旨、內文或統計。條件包含原狀態與排程時間，重複執行是冪等的。</p>
     *
     * @return 本次被整理的批次數
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update Campaign c set c.status = :newStatus "
        + "where c.status = :expectedStatus and c.scheduledAt is not null and c.scheduledAt <= :now")
    int markElapsedSchedules(@Param("expectedStatus") String expectedStatus,
                             @Param("newStatus") String newStatus,
                             @Param("now") OffsetDateTime now);

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

    /**
     * 已發布 PREMIUM 文章的解鎖點數區間投影。
     *
     * <p>兩個 getter 都可能是 {@code null}：聚合查詢在完全沒有符合條件的列時仍會回一列，
     * 而 {@code min}／{@code max} 於該列皆為 NULL。呼叫端必須把「兩者為 null」當成
     * 「目前沒有任何已發布的 PREMIUM 文章」處理，而不是當成 0。</p>
     */
    interface PremiumCostRange {

        /** 最低解鎖點數；沒有任何符合條件的文章時為 null */
        Integer getMinCost();

        /** 最高解鎖點數；沒有任何符合條件的文章時為 null */
        Integer getMaxCost();
    }

    /**
     * 讀者端要顯示的「每篇進階文章多少點」區間，直接取自 {@code campaign.credit_cost}。
     *
     * <p><b>為什麼一定要查這個欄位，不能用全域預設</b>：實際扣款走
     * {@code CreditPolicy.costOf(campaign)}，它優先取的就是 {@code campaign.credit_cost}；
     * 而 {@code ck_campaign_premium_cost} 與 {@code CampaignService.validateCreditCost}
     * 都強制 PREMIUM 的 {@code credit_cost > 0}，所以 {@code costOf()} 退回全域預設的
     * 那條分支是死碼——全域預設<b>結構性地</b>不會是任何一篇文章的實際扣款額。
     * 頁面上的數字必須與實際生效的數字同源，這支查詢就是那個同源點。</p>
     *
     * <p><b>WHERE 同時要求 slug 與 publishedAt 非 NULL</b>，與
     * {@link #findBySlugIsNotNullAndPublishedAtIsNotNullOrderByPublishedAtDesc()} 的條件一致：
     * 區間只能反映「讀者現在真的打得開、真的會被收這個價」的文章。
     * 少了 {@code publishedAt is not null}，已下架的文章會繼續影響區間，讀者看到一個
     * 站上根本買不到的價格；少了 {@code slug is not null}，沒有網址可開的殘列同樣會混進來。</p>
     *
     * <p><b>用一次聚合查詢而非撈回全部列在 Java 端算</b>：區間只需要兩個數字，
     * 把每一篇已發布 PREMIUM 文章的實體載進記憶體只為了取 min／max，會隨文章數線性變差。</p>
     *
     * <p><b>效能取捨（刻意不加快取、目前也不加索引）</b>：本查詢在公開免登入的
     * {@code /r/rules} 與 {@code /r/me} 每次請求都執行，而 {@code campaign} 表沒有
     * 涵蓋 {@code (tier, slug, published_at)} 的索引（V4／V8 只有 slug 的部分唯一索引），
     * 所以是一次全表掃描。<b>不加快取是刻意的</b>：同頁其他數字走 60 秒快取，但這個
     * 區間一旦快取，就會與 gate 顯示的即時價格出現時間差——那正是本查詢要消除的
     * 「顯示與實際不同源」落差。全表掃描的實際成本以文章數為界：電子報的發文頻率
     * 讓 {@code campaign} 成長極慢（每週個位數），數百列以內可忽略；
     * <b>若文章數成長到數千列、或 /r/rules 出現明顯延遲，屆時再補
     * {@code (tier, published_at)} 的部分索引</b>，不要先補——多一個索引就多一份
     * 寫入成本與一個要維護的物件。（對照：{@code /r/archive} 在公開路徑上做的
     * 是更貴的全表查詢且回傳所有列。）</p>
     *
     * <p><b>區間裡不可能摻進全域預設值</b>——這是整個修正成立的關鍵：WHERE 是
     * {@code tier = 'PREMIUM'} 精確比對，而 DB 的 {@code ck_campaign_premium_cost}
     * 對這些列強制 {@code credit_cost > 0}，所以被算進區間的每一列，
     * {@code costOf()} 必然回它自己的 {@code credit_cost}。</p>
     *
     * @param tier 要統計的分級。刻意用參數而非在 JPQL 裡寫死 {@code 'PREMIUM'}：
     *             JPQL 無法引用 Java 常數，寫死會讓 {@link Campaign#TIER_PREMIUM}
     *             在 {@link Campaign} 之外多出第二個定義點。
     */
    @Query("select min(c.creditCost) as minCost, max(c.creditCost) as maxCost from Campaign c "
        + "where c.tier = :tier and c.slug is not null and c.publishedAt is not null")
    PremiumCostRange findPremiumCostRange(@Param("tier") String tier);
}
