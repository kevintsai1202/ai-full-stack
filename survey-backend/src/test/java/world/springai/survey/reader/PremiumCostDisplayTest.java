package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import world.springai.survey.newsletter.Campaign;
import world.springai.survey.newsletter.CampaignRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「每篇多少點」片語的三個分支。
 *
 * <p>刻意用非典型數值（區間 12–48、單值 20、全域預設 33）：用真實預設值 10 的話，
 * 即使實作把數字寫死，測試也會通過。</p>
 */
class PremiumCostDisplayTest {

    private CampaignRepository campaignRepository;
    private CreditPolicy creditPolicy;
    private PremiumCostDisplay display;

    @BeforeEach
    void setUp() {
        campaignRepository = mock(CampaignRepository.class);
        creditPolicy = mock(CreditPolicy.class);
        when(creditPolicy.premiumCost()).thenReturn(33);
        display = new PremiumCostDisplay(campaignRepository, creditPolicy);
    }

    /** 讓區間查詢回傳指定的 min／max（皆為 null 代表沒有任何已發布 PREMIUM 文章） */
    private void givenRange(Integer min, Integer max) {
        when(campaignRepository.findPremiumCostRange(Campaign.TIER_PREMIUM))
            .thenReturn(new CampaignRepository.PremiumCostRange() {
                @Override public Integer getMinCost() { return min; }
                @Override public Integer getMaxCost() { return max; }
            });
    }

    /** 價格不一：顯示區間，並用「目前」表示這是事實而非預估 */
    @Test
    void rangeIsRenderedWhenPricesDiffer() {
        givenRange(12, 48);
        assertEquals("目前每篇 12–48 點", display.perArticlePhrase());
    }

    /** 價格相同：收斂成單一數字，不得輸出「20–20 點」 */
    @Test
    void singleValueIsNotRenderedAsARangeOfItself() {
        givenRange(20, 20);
        assertEquals("目前每篇 20 點", display.perArticlePhrase());
    }

    /**
     * 沒有任何已發布 PREMIUM 文章：退回全域預設，並改用「通常」。
     *
     * <p>聚合查詢在零列時仍回一列，只是 min／max 皆為 NULL——不是回 null 投影，
     * 也不是回 0。若實作把 null 當成 0，讀者會看到「目前每篇 0 點」（等於宣告免費）。</p>
     */
    @Test
    void fallsBackToGlobalDefaultWhenNoPublishedPremiumArticle() {
        givenRange(null, null);
        assertEquals("通常每篇 33 點", display.perArticlePhrase());
    }

    /**
     * 投影本身為 null 時也要退回全域預設，不可 NPE。
     *
     * <p>Spring Data 對零列的聚合查詢理論上會給一個兩欄皆 null 的投影，但正確性
     * 不該寄望在那個實作細節上——頁面因 NPE 回 500 比顯示參考值嚴重得多。</p>
     */
    @Test
    void nullProjectionAlsoFallsBackInsteadOfThrowing() {
        when(campaignRepository.findPremiumCostRange(Campaign.TIER_PREMIUM)).thenReturn(null);
        assertEquals("通常每篇 33 點", display.perArticlePhrase());
    }

    /**
     * 有區間可用時<b>不得</b>讀全域預設。
     *
     * <p>這條守的是「同源」：只斷言輸出字串的話，實作可以先讀 {@code premiumCost()}
     * 再湊出一樣的字串而測試依然全綠。驗 {@code never()} 才真的釘住資料來源。</p>
     */
    @Test
    void globalDefaultIsNotConsultedWhenRangeExists() {
        givenRange(12, 48);
        display.perArticlePhrase();
        verify(creditPolicy, never()).premiumCost();
    }

    /** 查詢一律以 {@link Campaign#TIER_PREMIUM} 為條件，不得統計到 BASIC 文章 */
    @Test
    void onlyPremiumTierIsCounted() {
        givenRange(12, 48);
        display.perArticlePhrase();
        verify(campaignRepository).findPremiumCostRange(Campaign.TIER_PREMIUM);
    }
}
