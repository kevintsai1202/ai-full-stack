package world.springai.survey.newsletter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link CampaignService#updateContent} 對<b>真實 PostgreSQL</b> 的落庫驗證。
 *
 * <p><b>為什麼一定要連真的資料庫</b>：本端點的 Critical 缺陷是
 * 「同一交易內 Hibernate 以舊快照整列寫回，覆蓋掉 JdbcTemplate 剛寫進去的封面」。
 * 那是 <b>flush 時序</b>的問題——{@code verify(repo).save(campaign)} 這種 mock 斷言
 * 在結構上不可能看見它：mock 的 repository 根本不存在一級快取，也不會 flush。
 * {@code CampaignUpdateContentTest} 三層測試全綠、封面卻永遠改不動，就是這麼發生的。
 * 因此這份測試刻意<b>不 mock 任何東西</b>，寫進去之後<b>在新交易中重讀那一列</b>。</p>
 *
 * <p><b>不是 {@code @Transactional} 測試</b>：若整個測試包在一個會回滾的交易裡，
 * service 的交易會直接加入它，「提交後資料庫裡是什麼」永遠測不到——那正是要測的東西。
 * 因此改為手動建立 fixture、於 {@code @AfterEach} 自行清除。</p>
 *
 * <p>連線位置與 {@code AppSettingServiceTransactionTest} 相同（survey-test-db:5433），
 * schema 由該環境既有的 Flyway 結果提供，測試本身不跑 migration。</p>
 */
@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:5433/postgres",
    "spring.datasource.username=postgres",
    "spring.datasource.password=password"
})
class CampaignUpdateContentPersistenceTest {

    /** 被測服務；由 Spring 注入才帶得到 @Transactional proxy（缺陷正是在交易邊界上發生） */
    @Autowired CampaignService campaignService;
    @Autowired CampaignRepository campaignRepository;
    /** 用來建立 fixture 與「在新交易中重讀」——每次呼叫都是獨立的 auto-commit 連線 */
    @Autowired JdbcTemplate jdbc;

    private static final String OLD_COVER = "📮";
    private static final String NEW_COVER = "🎯";

    /** 本次測試建立的 campaign id，供 @AfterEach 清除 */
    private Long fixtureId;

    /** 測試留下的資料必須自行清除，否則會累積在共用的 survey-test-db 裡 */
    @AfterEach
    void cleanUp() {
        if (fixtureId != null) {
            jdbc.update("DELETE FROM campaign_tag WHERE campaign_id = ?", fixtureId);
            jdbc.update("DELETE FROM campaign WHERE id = ?", fixtureId);
            fixtureId = null;
        }
    }

    /**
     * <b>C2 回歸護欄</b>：改封面必須真的落庫，且不得順手把其他欄位以舊快照寫回。
     *
     * <p>把 {@code updateContent} 改回 {@code campaignRepository.save(campaign)}，
     * 第一個斷言（封面）就會變紅——那正是這份測試存在的理由。</p>
     */
    @Test
    void coverUpdateSurvivesCommitAndOtherColumnsAreUntouched() {
        long runId = System.nanoTime();
        OffsetDateTime publishedAt = OffsetDateTime.parse("2026-08-01T09:00:00Z");

        // ── fixture：一篇「已寄出且已發布」的文章，帶舊封面與寄送統計 ──
        Campaign fixture = new Campaign(
            "C2 落庫驗證原標題 " + runId, "原內文 " + runId, "<p>寄出時的信件快照</p>",
            null, null, Campaign.MODE_PUBLISH, null, 42, Campaign.STATUS_PUBLISHED);
        fixture.setSlug("c2-persistence-fixture-" + runId);
        fixture.setTier(Campaign.TIER_BASIC);
        fixture.setPublishedAt(publishedAt);
        fixture.setAcceptedCount(40);
        fixture.setFailedCount(2);
        fixtureId = campaignRepository.saveAndFlush(fixture).getId();
        assertNotNull(fixtureId);
        // 封面直接寫進資料庫（與正式 publish 路徑一致：封面走 CampaignMetadataService 的原生 SQL）
        jdbc.update("UPDATE campaign SET cover_emoji = ? WHERE id = ?", OLD_COVER, fixtureId);
        assertEquals(OLD_COVER, readRow().get("cover_emoji"), "fixture 前提不成立：舊封面沒寫進去");

        // ── 受測動作：把封面從 📮 改成 🎯，同時改主旨與內文 ──
        String newSubject = "C2 落庫驗證新標題 " + runId;
        String newMarkdown = "新內文 " + runId;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        campaignService.updateContent(fixtureId, newSubject, newMarkdown,
            NEW_COVER, null, List.of("c2tag" + runId), now);

        // ── 在新交易中重讀那一列（jdbc 每次呼叫都是獨立連線／獨立交易） ──
        Map<String, Object> row = readRow();

        // ★ 這一項就是 Critical 本身：整列寫回會把它還原成 📮
        assertEquals(NEW_COVER, row.get("cover_emoji"),
            "封面沒有落庫：Hibernate 以舊快照整列寫回，覆蓋了 JdbcTemplate 寫入的新封面");

        assertEquals(newSubject, row.get("subject"), "主旨沒有落庫");
        assertEquals(newMarkdown, row.get("markdown"), "內文沒有落庫");
        assertNotNull(row.get("updated_at"), "updated_at 沒有寫入");

        // ── 整列寫回會傷到的欄位：必須維持原值 ──
        assertEquals("<p>寄出時的信件快照</p>", row.get("body_html"),
            "body_html 被改動：那是寄出信件的歷史快照，等於竄改「當初寄了什麼」");
        assertEquals(Campaign.STATUS_PUBLISHED, row.get("status"), "status 被改動");
        assertNotNull(row.get("published_at"), "published_at 被清空：文章會從 /r/archive 消失");
        assertEquals(publishedAt.toInstant(),
            ((java.sql.Timestamp) row.get("published_at")).toInstant(), "published_at 被改動");
        assertEquals(40, ((Number) row.get("accepted_count")).intValue(), "accepted_count 被舊快照覆蓋");
        assertEquals(2, ((Number) row.get("failed_count")).intValue(), "failed_count 被舊快照覆蓋");
        assertEquals(42, ((Number) row.get("recipient_count")).intValue(), "recipient_count 被舊快照覆蓋");
        assertEquals("c2-persistence-fixture-" + runId, row.get("slug"), "slug 被改動");
        assertEquals(Campaign.TIER_BASIC, row.get("tier"), "tier 被改動");
    }

    /** 以獨立連線重讀 fixture 那一列的原始欄位值（繞過 Hibernate 一級快取） */
    private Map<String, Object> readRow() {
        return jdbc.queryForMap("""
            SELECT subject, markdown, body_html, cover_emoji, status, published_at,
                   accepted_count, failed_count, recipient_count, slug, tier, updated_at
              FROM campaign WHERE id = ?
            """, fixtureId);
    }
}
