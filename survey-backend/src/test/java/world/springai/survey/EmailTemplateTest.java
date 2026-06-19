package world.springai.survey;

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
}
