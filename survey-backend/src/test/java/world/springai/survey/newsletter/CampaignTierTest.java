package world.springai.survey.newsletter;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Campaign 內容分級與發布狀態的行為測試 */
class CampaignTierTest {

    /** 建立一筆最小可用的 campaign */
    private Campaign campaign() {
        return new Campaign("主旨", "# 內容", "<h1>內容</h1>",
            null, null, "now", null, 0, "sent");
    }

    /** 新建的 campaign 預設為 BASIC、0 點、未發布（與 V8 的 DEFAULT 一致） */
    @Test
    void defaultsToBasicUnpublished() {
        Campaign c = campaign();

        assertEquals(Campaign.TIER_BASIC, c.getTier());
        assertEquals(0, c.getCreditCost());
        assertFalse(c.isPremium());
        assertFalse(c.isPublished(), "未設 publishedAt 即未發布，不出現在 archive");
        assertFalse(c.isVipFullInMail(), "第一版預設所有人信件都折疊");
        assertEquals("active", c.getFilterLevels(), "預設只寄給 active 級別");
    }

    /** 設為 PREMIUM 後 isPremium 為 true */
    @Test
    void premiumTierIsRecognised() {
        Campaign c = campaign();
        c.setTier(Campaign.TIER_PREMIUM);
        c.setCreditCost(10);

        assertTrue(c.isPremium());
        assertEquals(10, c.getCreditCost());
    }

    /** 設了 publishedAt 才算已發布 */
    @Test
    void publishedAtDeterminesPublishState() {
        Campaign c = campaign();
        assertFalse(c.isPublished());

        c.setPublishedAt(OffsetDateTime.parse("2026-07-25T12:00:00+08:00"));
        assertTrue(c.isPublished());
    }

    /** slug 可設定，供 /r/news/{slug} 使用 */
    @Test
    void slugIsSettable() {
        Campaign c = campaign();
        c.setSlug("hello-world");

        assertEquals("hello-world", c.getSlug());
    }
}
