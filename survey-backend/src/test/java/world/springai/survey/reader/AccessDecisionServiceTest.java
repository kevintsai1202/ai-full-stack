package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import world.springai.survey.AppSettingService;
import world.springai.survey.newsletter.Campaign;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AccessDecisionService 行為測試。
 *
 * <p>每條路徑一個測試，含 VIP 已到期、餘額剛好、餘額少 1 點等邊界。
 * 授權是本階段最敏感的邏輯，路徑覆蓋必須完整。</p>
 */
class AccessDecisionServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-25T12:00:00+08:00");

    private ArticleAccessRepository articleAccessRepository;
    private AppSettingService appSettingService;
    private AccessDecisionService service;

    @BeforeEach
    void setUp() {
        articleAccessRepository = mock(ArticleAccessRepository.class);
        appSettingService = mock(AppSettingService.class);
        when(appSettingService.getInt(eq(AppSettingService.CREDIT_PREMIUM_COST), anyInt())).thenReturn(10);
        service = new AccessDecisionService(articleAccessRepository, appSettingService);
    }

    /** 文章 id 測試固定值；Campaign 沒有 setId，透過 ReflectionTestUtils 設定，
     *  確保「以正確 campaignId 查解鎖紀錄」這件事真的被驗證到（傳錯 id 也會被抓到）。 */
    private static final long ARTICLE_ID = 42L;

    /** 建立一篇文章（預設已發布） */
    private Campaign article(String tier, int cost) {
        Campaign c = new Campaign("主旨", "# 內容", "<h1>內容</h1>", null, null, "now", null, 0, "sent");
        c.setTier(tier);
        c.setCreditCost(cost);
        c.setPublishedAt(NOW.minusDays(1));
        ReflectionTestUtils.setField(c, "id", ARTICLE_ID);
        return c;
    }

    /** 建立一篇尚未發布的文章 */
    private Campaign unpublishedArticle(String tier, int cost) {
        Campaign c = article(tier, cost);
        c.setPublishedAt(null);
        return c;
    }

    /** 建立一位讀者 */
    private Reader reader(String tier, int credits) {
        Reader r = new Reader("user@example.com", "CODE1234");
        r.setId(1L);
        r.setTier(tier);
        r.setCredits(credits);
        return r;
    }

    /** 未登入：一律 PARTIAL，即使文章是 BASIC */
    @Test
    void notLoggedInGetsPartial() {
        AccessDecisionService.Decision d =
            service.decide(null, false, article(Campaign.TIER_BASIC, 0), NOW);

        assertEquals(AccessDecisionService.Access.PARTIAL, d.access());
        assertEquals(AccessDecisionService.Reason.NOT_LOGGED_IN, d.reason());
    }

    /** 已登入但未確認訂閱：PARTIAL（訂閱狀態來自名單中心，不是 reader 表） */
    @Test
    void loggedInButNotSubscribedGetsPartial() {
        AccessDecisionService.Decision d =
            service.decide(reader(Reader.TIER_FREE, 300), false, article(Campaign.TIER_BASIC, 0), NOW);

        assertEquals(AccessDecisionService.Access.PARTIAL, d.access());
        assertEquals(AccessDecisionService.Reason.NOT_SUBSCRIBED, d.reason());
    }

    /** 已確認訂閱 + BASIC 文章：FULL */
    @Test
    void subscribedReaderGetsFullBasicArticle() {
        AccessDecisionService.Decision d =
            service.decide(reader(Reader.TIER_FREE, 0), true, article(Campaign.TIER_BASIC, 0), NOW);

        assertEquals(AccessDecisionService.Access.FULL, d.access());
        assertEquals(AccessDecisionService.Reason.BASIC_OPEN, d.reason());
    }

    /** 有效 VIP + PREMIUM 文章：FULL，且不需要查解鎖紀錄 */
    @Test
    void activeVipGetsFullPremiumArticle() {
        Reader vip = reader(Reader.TIER_VIP, 0);
        vip.setVipExpiresAt(NOW.plusDays(30));

        AccessDecisionService.Decision d =
            service.decide(vip, true, article(Campaign.TIER_PREMIUM, 10), NOW);

        assertEquals(AccessDecisionService.Access.FULL, d.access());
        assertEquals(AccessDecisionService.Reason.VIP, d.reason());
    }

    /** VIP 已到期 + PREMIUM：視為 FREE，餘額不足則 PARTIAL */
    @Test
    void expiredVipFallsBackToFreeRules() {
        Reader expired = reader(Reader.TIER_VIP, 5);
        expired.setVipExpiresAt(NOW.minusDays(1));
        when(articleAccessRepository.existsByReaderIdAndCampaignId(1L, ARTICLE_ID)).thenReturn(false);

        AccessDecisionService.Decision d =
            service.decide(expired, true, article(Campaign.TIER_PREMIUM, 10), NOW);

        assertEquals(AccessDecisionService.Access.PARTIAL, d.access());
        assertEquals(AccessDecisionService.Reason.NEEDS_CREDITS, d.reason());
    }

    /** 已解鎖過的 PREMIUM：FULL 且不重複扣點（一次解鎖永久可讀的承諾） */
    @Test
    void alreadyUnlockedArticleStaysFull() {
        Campaign premium = article(Campaign.TIER_PREMIUM, 10);
        when(articleAccessRepository.existsByReaderIdAndCampaignId(1L, premium.getId())).thenReturn(true);

        AccessDecisionService.Decision d = service.decide(reader(Reader.TIER_FREE, 0), true, premium, NOW);

        assertEquals(AccessDecisionService.Access.FULL, d.access());
        assertEquals(AccessDecisionService.Reason.ALREADY_UNLOCKED, d.reason());
    }

    /** 餘額不足：PARTIAL 並回報還差幾點 */
    @Test
    void insufficientCreditsReportsShortfall() {
        AccessDecisionService.Decision d =
            service.decide(reader(Reader.TIER_FREE, 4), true, article(Campaign.TIER_PREMIUM, 10), NOW);

        assertEquals(AccessDecisionService.Access.PARTIAL, d.access());
        assertEquals(AccessDecisionService.Reason.NEEDS_CREDITS, d.reason());
        assertEquals(6, d.shortfall(), "還差 6 點");
    }

    /** 餘額剛好等於成本：階段 B 尚未接上扣點，仍為 PARTIAL 但 shortfall 為 0 */
    @Test
    void exactCreditsInStageBStillPartialWithZeroShortfall() {
        AccessDecisionService.Decision d =
            service.decide(reader(Reader.TIER_FREE, 10), true, article(Campaign.TIER_PREMIUM, 10), NOW);

        assertEquals(AccessDecisionService.Access.PARTIAL, d.access(),
            "階段 B 未接扣點路徑，PREMIUM 對非 VIP 一律 PARTIAL");
        assertEquals(0, d.shortfall());
    }

    /** creditCost 為 0 的 PREMIUM（理論上被 DB CHECK 擋掉）改用參數預設值，不當成免費 */
    @Test
    void premiumWithZeroCostFallsBackToSettingValue() {
        AccessDecisionService.Decision d =
            service.decide(reader(Reader.TIER_FREE, 4), true, article(Campaign.TIER_PREMIUM, 0), NOW);

        assertEquals(AccessDecisionService.Access.PARTIAL, d.access());
        assertEquals(6, d.shortfall(), "應改用 app_setting 的 10 點計算，而非把 0 當免費");
    }

    /** recordAccess 只在 VIP 決策時寫入，且已存在紀錄時不重複寫 */
    @Test
    void recordAccessWritesForVipDecision() {
        Campaign premium = article(Campaign.TIER_PREMIUM, 10);
        Reader vip = reader(Reader.TIER_VIP, 0);
        vip.setVipExpiresAt(NOW.plusDays(30));
        AccessDecisionService.Decision full = new AccessDecisionService.Decision(
            AccessDecisionService.Access.FULL, AccessDecisionService.Reason.VIP, 0);
        when(articleAccessRepository.existsByReaderIdAndCampaignId(1L, ARTICLE_ID)).thenReturn(false);

        service.recordAccess(vip, premium, full);

        verify(articleAccessRepository).save(any(ArticleAccess.class));
    }

    /** recordAccess 對 PARTIAL 決策不得寫入 */
    @Test
    void recordAccessSkipsPartialDecision() {
        AccessDecisionService.Decision partial = new AccessDecisionService.Decision(
            AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NEEDS_CREDITS, 6);

        service.recordAccess(reader(Reader.TIER_FREE, 4), article(Campaign.TIER_PREMIUM, 10), partial);

        verify(articleAccessRepository, never()).save(any(ArticleAccess.class));
    }

    /** recordAccess 對已有紀錄者不重複寫入（避免 UNIQUE 衝突） */
    @Test
    void recordAccessSkipsWhenAlreadyRecorded() {
        Campaign premium = article(Campaign.TIER_PREMIUM, 10);
        AccessDecisionService.Decision full = new AccessDecisionService.Decision(
            AccessDecisionService.Access.FULL, AccessDecisionService.Reason.ALREADY_UNLOCKED, 0);
        when(articleAccessRepository.existsByReaderIdAndCampaignId(anyLong(), any())).thenReturn(true);

        service.recordAccess(reader(Reader.TIER_FREE, 0), premium, full);

        verify(articleAccessRepository, never()).save(any(ArticleAccess.class));
    }

    /**
     * 【Important 1】recordAccess 對 BASIC_OPEN 決策不得寫入。
     *
     * <p>若對 BASIC_OPEN 也寫入 article_access，文章升級為 PREMIUM 後，
     * 同一位讀者會因為 ALREADY_UNLOCKED 永久免費看到全文，等於繞過付費牆。</p>
     */
    @Test
    void recordAccessSkipsBasicOpenDecision() {
        Campaign basic = article(Campaign.TIER_BASIC, 0);
        AccessDecisionService.Decision basicOpen = new AccessDecisionService.Decision(
            AccessDecisionService.Access.FULL, AccessDecisionService.Reason.BASIC_OPEN, 0);

        service.recordAccess(reader(Reader.TIER_FREE, 0), basic, basicOpen);

        verify(articleAccessRepository, never()).save(any(ArticleAccess.class));
    }

    /**
     * 【Important 3】recordAccess 並發時撞上 UNIQUE 約束應被靜默忽略，
     * 不得讓 DataIntegrityViolationException 冒到呼叫端（controller）變成 500。
     */
    @Test
    void recordAccessIgnoresConcurrentUniqueViolation() {
        Campaign premium = article(Campaign.TIER_PREMIUM, 10);
        Reader vip = reader(Reader.TIER_VIP, 0);
        vip.setVipExpiresAt(NOW.plusDays(30));
        AccessDecisionService.Decision full = new AccessDecisionService.Decision(
            AccessDecisionService.Access.FULL, AccessDecisionService.Reason.VIP, 0);
        when(articleAccessRepository.existsByReaderIdAndCampaignId(1L, ARTICLE_ID)).thenReturn(false);
        doThrow(new DataIntegrityViolationException("duplicate key"))
            .when(articleAccessRepository).save(any(ArticleAccess.class));

        assertDoesNotThrow(() -> service.recordAccess(vip, premium, full));
    }

    /**
     * 【Important 4】app_setting 後備值被誤設為 0 或負數時，成本仍須 >= 1，
     * 不可讓 PREMIUM 文章因此被當成免費。
     */
    @Test
    void resolveCostFallbackNeverGoesToZeroOrBelow() {
        when(appSettingService.getInt(eq(AppSettingService.CREDIT_PREMIUM_COST), anyInt())).thenReturn(0);

        AccessDecisionService.Decision d =
            service.decide(reader(Reader.TIER_FREE, 0), true, article(Campaign.TIER_PREMIUM, 0), NOW);

        assertEquals(AccessDecisionService.Access.PARTIAL, d.access());
        assertEquals(1, d.shortfall(), "後備成本被誤設為 0 時，仍應以 1 點計算，不得視為免費");
    }

    /**
     * 【Important 5】VIP 到期時間等於 now 時，應視為已到期（fail-closed 方向）。
     * 若把 isActiveVip 的判斷從 isAfter(now) 改成 !isBefore(now)，此測試會失敗。
     */
    @Test
    void vipExpiringExactlyNowIsTreatedAsExpired() {
        Reader vip = reader(Reader.TIER_VIP, 0);
        vip.setVipExpiresAt(NOW);
        when(articleAccessRepository.existsByReaderIdAndCampaignId(1L, ARTICLE_ID)).thenReturn(false);

        AccessDecisionService.Decision d =
            service.decide(vip, true, article(Campaign.TIER_PREMIUM, 10), NOW);

        assertEquals(AccessDecisionService.Access.PARTIAL, d.access());
        assertEquals(AccessDecisionService.Reason.NEEDS_CREDITS, d.reason(),
            "到期當下不再是有效 VIP，應走一般 PREMIUM 規則");
    }

    /**
     * 【Important 6】decide() 必須是純函式：即使決策為 FULL（VIP），也不得寫入 article_access。
     * 若有人把寫入邏輯搬進 decide()，此測試會失敗。
     */
    @Test
    void decideNeverWritesArticleAccess() {
        Reader vip = reader(Reader.TIER_VIP, 0);
        vip.setVipExpiresAt(NOW.plusDays(30));

        service.decide(vip, true, article(Campaign.TIER_PREMIUM, 10), NOW);

        verify(articleAccessRepository, never()).save(any());
    }

    /**
     * 【Important 7】reader 為 null 時，即使 subscribed 傳 true，也一律 PARTIAL。
     * 現有 notLoggedInGetsPartial 傳的是 subscribed=false，刪掉 reader==null 檢查
     * 後仍可能靠其他分支誤判為 NOT_SUBSCRIBED 而非真正守住「未登入不得 FULL」。
     */
    @Test
    void nullReaderWithSubscribedTrueStillGetsPartial() {
        AccessDecisionService.Decision d =
            service.decide(null, true, article(Campaign.TIER_BASIC, 0), NOW);

        assertEquals(AccessDecisionService.Access.PARTIAL, d.access());
    }

    /** 未發布文章：一律 PARTIAL / NOT_PUBLISHED，連 VIP 也不例外 */
    @Test
    void unpublishedArticleGetsPartialEvenForVip() {
        Reader vip = reader(Reader.TIER_VIP, 0);
        vip.setVipExpiresAt(NOW.plusDays(30));

        AccessDecisionService.Decision d =
            service.decide(vip, true, unpublishedArticle(Campaign.TIER_PREMIUM, 10), NOW);

        assertEquals(AccessDecisionService.Access.PARTIAL, d.access());
        assertEquals(AccessDecisionService.Reason.NOT_PUBLISHED, d.reason());
    }

    /**
     * 【Critical】tier 為小寫 "premium"（非精確符合 Campaign.TIER_BASIC）時，
     * 不得被視為 BASIC 而放行 FULL——應走進階規則（fail-closed）。
     */
    @Test
    void lowercasePremiumTierDoesNotGetFullAccess() {
        AccessDecisionService.Decision d =
            service.decide(reader(Reader.TIER_FREE, 0), true, article("premium", 10), NOW);

        assertEquals(AccessDecisionService.Access.PARTIAL, d.access(),
            "tier 打字錯誤（大小寫不符）不得被當成 BASIC 全文開放");
    }

    /**
     * 【Critical】tier 為 null 時，不得被視為 BASIC 而放行 FULL——應走進階規則（fail-closed）。
     */
    @Test
    void nullTierDoesNotGetFullAccess() {
        Campaign campaign = article(Campaign.TIER_BASIC, 0);
        campaign.setTier(null);

        AccessDecisionService.Decision d =
            service.decide(reader(Reader.TIER_FREE, 0), true, campaign, NOW);

        assertEquals(AccessDecisionService.Access.PARTIAL, d.access(),
            "tier 為 null 不得被當成 BASIC 全文開放");
    }
}
