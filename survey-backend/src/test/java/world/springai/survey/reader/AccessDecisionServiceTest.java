package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import world.springai.survey.AppSettingService;
import world.springai.survey.newsletter.Campaign;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    /** 建立一篇文章 */
    private Campaign article(String tier, int cost) {
        Campaign c = new Campaign("主旨", "# 內容", "<h1>內容</h1>", null, null, "now", null, 0, "sent");
        c.setTier(tier);
        c.setCreditCost(cost);
        c.setPublishedAt(NOW.minusDays(1));
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
        when(articleAccessRepository.existsByReaderIdAndCampaignId(1L, null)).thenReturn(false);

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

    /** recordAccess 只在 FULL 時寫入，且已存在紀錄時不重複寫 */
    @Test
    void recordAccessWritesOnlyOnceForFullDecision() {
        Campaign premium = article(Campaign.TIER_PREMIUM, 10);
        Reader vip = reader(Reader.TIER_VIP, 0);
        vip.setVipExpiresAt(NOW.plusDays(30));
        AccessDecisionService.Decision full = new AccessDecisionService.Decision(
            AccessDecisionService.Access.FULL, AccessDecisionService.Reason.VIP, 0);
        when(articleAccessRepository.existsByReaderIdAndCampaignId(anyLong(), any())).thenReturn(false);

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
}
