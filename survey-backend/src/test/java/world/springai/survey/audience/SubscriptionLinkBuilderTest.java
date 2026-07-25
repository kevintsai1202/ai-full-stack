package world.springai.survey.audience;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 連結組裝的唯一擁有者。
 *
 * <p>測試重點是「路徑字串與 URL 編碼」——這兩者一錯，寄出去的信裡就是死連結，
 * 而且信已經送到讀者信箱，無法回收。</p>
 */
class SubscriptionLinkBuilderTest {

    /** 固定回傳假簽章的 token 服務 */
    private final UnsubscribeTokenService tokenService = mock(UnsubscribeTokenService.class);

    /** 建一個綁定固定對外網址的 builder */
    private SubscriptionLinkBuilder builder() {
        when(tokenService.sign(anyString())).thenReturn("SIG");
        return new SubscriptionLinkBuilder(tokenService, "https://survey.example.com");
    }

    /**
     * 確認連結的路徑必須完全是 /api/survey/confirm。
     *
     * <p>已寄出的邀請信內含這個路徑，改動等於讓在途連結全部失效，
     * 所以這裡用<b>字面值</b>斷言而不是引用 CONFIRM_PATH 常數——
     * 用常數比對的話，改常數會讓測試跟著改而永遠不會變紅。</p>
     */
    @Test
    void confirmLinkUsesTheExactLegacyPath() {
        assertEquals("https://survey.example.com/api/survey/confirm?email=a%40b.com&t=SIG",
            builder().confirmLink("a@b.com"));
    }

    /** 退訂連結的路徑必須完全是 /api/survey/unsubscribe，同上理由 */
    @Test
    void unsubscribeLinkUsesTheExactLegacyPath() {
        assertEquals("https://survey.example.com/api/survey/unsubscribe?email=a%40b.com&t=SIG",
            builder().unsubscribeLink("a@b.com"));
    }

    /** email 內的加號必須編碼成 %2B，否則收件端會解讀成空白而查不到名單 */
    @Test
    void encodesPlusSignInEmail() {
        assertTrue(builder().unsubscribeLink("a+tag@b.com").contains("email=a%2Btag%40b.com"));
    }

    /** 預覽用連結不得帶真實簽章：預覽內容會顯示在後台，不該外流可用的退訂 token */
    @Test
    void previewLinkCarriesNoRealSignature() {
        String link = builder().previewUnsubscribeLink();
        assertTrue(link.contains("t=preview"));
        assertTrue(link.contains("preview%40example.com"));
    }
}
