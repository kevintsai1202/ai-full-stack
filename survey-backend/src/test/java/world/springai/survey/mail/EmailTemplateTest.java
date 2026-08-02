package world.springai.survey.mail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 信件外框模板測試 */
class EmailTemplateTest {

    private final EmailTemplate template = new EmailTemplate();

    /** 輸出應包含內文、退訂連結與「取消訂閱」字樣 */
    @Test
    void wrapContainsBodyAndUnsubscribeLink() {
        String out = template.wrap("<p>內文段落</p>", "https://api.example.com/api/survey/unsubscribe?email=a%40b.com&t=tok");
        assertTrue(out.contains("<p>內文段落</p>"), out);
        assertTrue(out.contains("https://api.example.com/api/survey/unsubscribe?email=a%40b.com&t=tok"), out);
        assertTrue(out.contains("取消訂閱"), out);
    }

    /** 電子報最上方應顯示呼叫端於寄送當下取得的訂閱人數。 */
    @Test
    void campaignWrapShowsCurrentSubscriberCountFirst() {
        String out = template.wrapCampaign(
            "<p>電子報內文</p>", "https://example.com/unsubscribe",
            "https://example.com/article", "https://example.com/login", 128L);

        assertTrue(out.contains("已有 128 位讀者訂閱"), out);
        assertTrue(out.indexOf("已有 128 位讀者訂閱") < out.indexOf("電子報內文"), out);
    }
}
