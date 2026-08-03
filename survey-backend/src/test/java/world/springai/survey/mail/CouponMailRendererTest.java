package world.springai.survey.mail;

import org.junit.jupiter.api.Test;
import world.springai.survey.coupon.CouponCampaign;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CouponMailRenderer} 的渲染結果驗證：主旨、優惠卡內容、期限有無、
 * 動態值（課程名／文案）HTML 跳脫。純單元測試，{@link CouponCampaign} 直接 new，不需資料庫。
 */
class CouponMailRendererTest {

    /** 待測渲染器：不需注入任何協作物件 */
    private final CouponMailRenderer renderer = new CouponMailRenderer();

    @Test
    void 主旨含課程名與優惠語() {
        String subject = renderer.subject(campaign("AI 全端開發", "超值文案", "https://hahow.in/cr/x",
            "SAVE300", LocalDate.of(2026, 9, 30)));
        assertTrue(subject.contains("AI 全端開發"));
        assertTrue(subject.contains("優惠"));
    }

    @Test
    void 優惠卡含課程名文案優惠碼與按鈕() {
        String html = renderer.body(campaign("AI 全端開發", "超值文案", "https://hahow.in/cr/x",
            "SAVE300", LocalDate.of(2026, 9, 30)), "讀者意見調查", "https://x/unsub");
        assertTrue(html.contains("AI 全端開發"));
        assertTrue(html.contains("SAVE300"));
        assertTrue(html.contains("href=\"https://hahow.in/cr/x\""));
        assertTrue(html.contains("優惠至 2026-09-30"));
        assertTrue(html.contains("讀者意見調查")); // 寄送原因
        assertTrue(html.contains("https://x/unsub")); // 退訂
        assertTrue(html.contains("前往課程")); // 按鈕文案照 spec §7 釘住，不得偏離
    }

    @Test
    void 期限為null不顯示期限行() {
        String html = renderer.body(campaign("AI 全端開發", "超值文案", "https://hahow.in/cr/x",
            "SAVE300", null), "讀者意見調查", "https://x/unsub");
        assertFalse(html.contains("優惠至"));
    }

    @Test
    void 動態值跳脫_課程名含HTML() {
        String html = renderer.body(campaign("A<b>", "超值文案", "https://hahow.in/cr/x",
            "SAVE300", null), "讀者意見調查", "https://x/unsub");
        assertTrue(html.contains("A&lt;b&gt;"));
        assertFalse(html.contains("A<b>"));
    }

    @Test
    void 動態值跳脫_退訂連結含雙引號() {
        // unsubscribeLink 若含 " 且未跳脫，會提早關閉 href 屬性、破壞版面甚至被注入額外屬性
        String html = renderer.body(campaign("AI 全端開發", "超值文案", "https://hahow.in/cr/x",
            "SAVE300", null), "讀者意見調查", "https://x/unsub?t=\"onmouseover=alert(1)");
        assertTrue(html.contains("https://x/unsub?t=&quot;onmouseover=alert(1)"));
        assertFalse(html.contains("href=\"https://x/unsub?t=\"onmouseover"));
    }

    /** 建一筆測試用優惠券活動，formKey／answerFilter 用固定值（渲染器不會用到這兩個欄位） */
    private CouponCampaign campaign(String courseName, String pitch, String courseUrl,
                                     String couponCode, LocalDate expiresAt) {
        return new CouponCampaign(courseName, pitch, courseUrl, couponCode, expiresAt,
            "survey-form", "{}");
    }
}
