package world.springai.survey.reader;

import org.springframework.stereotype.Component;
import world.springai.survey.newsletter.Campaign;
import world.springai.survey.newsletter.CampaignRepository;

/**
 * 「進階文章每篇多少點」這句話的唯一產生點（{@code /r/rules} 與 {@code /r/me} 共用）。
 *
 * <p><b>為什麼不是直接顯示全域預設</b>：實際扣款走
 * {@link CreditPolicy#costOf(Campaign)}，它優先取的是該篇自己的
 * {@code campaign.credit_cost}；而 {@code ck_campaign_premium_cost} 與
 * {@code CampaignService.validateCreditCost} 都強制 PREMIUM 的
 * {@code credit_cost > 0}，所以 {@code costOf()} 退回 {@link CreditPolicy#premiumCost()}
 * 的那條分支是死碼。換句話說，全域預設<b>結構性地</b>不會是任何一篇文章的實際扣款額
 * ——把它印在頁面上，讀者看到的代價與實際扣的永遠不一致（spec §5.11 要防的正是這件事）。
 * 本類別改為顯示<b>已發布 PREMIUM 文章的實際點數區間</b>，資料來源就是實際扣款讀的
 * 同一個欄位 {@code campaign.credit_cost}。</p>
 *
 * <p><b>為什麼要有這一層而不是各頁自己算</b>：兩頁的三分支判斷（區間／單值／無文章）
 * 必須完全一致，否則 {@code /r/rules} 說「10–30 點」而 {@code /r/me} 說「通常 10 點」，
 * 讀者會不知道該信哪個。判斷寫一份，兩頁只負責把片語接進自己的句子。</p>
 *
 * <p><b>套件方向</b>：{@code reader} import {@code newsletter} 是合法方向
 * （{@code ReaderPageController} 已在用 {@link CampaignRepository}），
 * 反向則被 {@code PackageDependencyTest} 擋著。</p>
 */
@Component
public class PremiumCostDisplay {

    /** 區間查詢的來源；讀的是與實際扣款同一個 {@code campaign.credit_cost} 欄位 */
    private final CampaignRepository campaignRepository;

    /** 只在「站上沒有任何已發布 PREMIUM 文章」時才會用到（見 {@link #perArticlePhrase()}） */
    private final CreditPolicy creditPolicy;

    /** 注入文章查詢與點數參數 */
    public PremiumCostDisplay(CampaignRepository campaignRepository, CreditPolicy creditPolicy) {
        this.campaignRepository = campaignRepository;
        this.creditPolicy = creditPolicy;
    }

    /**
     * 產生「每篇多少點」的片語，供頁面接在「進階文章」／「進階內容」之後。
     *
     * <p>三種情況：</p>
     * <ol>
     *   <li><b>有文章且價格不一</b> → {@code 目前每篇 10–30 點}。這是事實陳述，
     *       數字來自已發布文章的 {@code credit_cost}，讀者拿去對照任何一篇都對得上。</li>
     *   <li><b>有文章且價格相同</b> → {@code 目前每篇 10 點}。刻意不輸出「10–10 點」
     *       ——那是把實作細節漏給讀者看，讀起來像壞掉。</li>
     *   <li><b>沒有任何已發布 PREMIUM 文章</b> → {@code 通常每篇 N 點}，N 取
     *       {@link CreditPolicy#premiumCost()}。<b>這是唯一合法使用全域預設的情況</b>：
     *       此時沒有任何「實際扣款額」存在可供顯示，而全域預設正是後台建立下一篇
     *       PREMIUM 時會被預填的值（見 admin.html 的預填邏輯），所以它確實是讀者
     *       接下來最可能遇到的價格。用「通常」而不是「目前」，因為它是預估不是事實。</li>
     * </ol>
     */
    public String perArticlePhrase() {
        CampaignRepository.PremiumCostRange range =
            campaignRepository.findPremiumCostRange(Campaign.TIER_PREMIUM);
        // 聚合查詢在零列時仍回一列而兩欄皆 NULL；投影本身理論上不會是 null，
        // 但這裡不把正確性寄望在 Spring Data 的實作細節上。
        Integer min = range == null ? null : range.getMinCost();
        Integer max = range == null ? null : range.getMaxCost();

        if (min == null || max == null) {
            return "通常每篇 " + creditPolicy.premiumCost() + " 點";
        }
        if (min.intValue() == max.intValue()) {
            return "目前每篇 " + min + " 點";
        }
        // 連接號用 en dash（U+2013，半形寬度），與後台參數頁的「可填 1–10000」用法一致
        return "目前每篇 " + min + "–" + max + " 點";
    }
}
