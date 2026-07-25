package world.springai.survey.audience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * confirm / unsubscribe 端點：自 form.SurveyController 搬入。
 *
 * <p>原有的四項安全性質必須完整保留：防偽（HMAC）、冪等、不洩漏名單
 * （不論結果一律相同回應）、回應頁不回顯使用者輸入。</p>
 */
class SubscriptionControllerTest {

    private SurveyResponseRepository repository;
    private UnsubscribeTokenService tokenService;
    private ApplicationEventPublisher publisher;
    private SubscriptionController controller;

    @BeforeEach
    void setUp() {
        repository = mock(SurveyResponseRepository.class);
        tokenService = mock(UnsubscribeTokenService.class);
        publisher = mock(ApplicationEventPublisher.class);
        controller = new SubscriptionController(repository, tokenService, publisher);
    }

    /** 簽章正確時確認訂閱，並發布事件供 reader 發放推薦獎勵 */
    @Test
    void validConfirmUpdatesConsentAndPublishesEvent() {
        when(tokenService.verify(eq("a@b.com"), eq("SIG"))).thenReturn(true);
        when(repository.confirmByEmail("a@b.com")).thenReturn(1);

        ResponseEntity<String> response = controller.confirm("a@b.com", "SIG");

        assertEquals(200, response.getStatusCode().value());
        verify(repository).confirmByEmail("a@b.com");
        verify(publisher).publishEvent(new SubscriptionConfirmedEvent("a@b.com"));
    }

    /** 事件的 email 必須是正規化後的小寫，否則監聽端查不到推薦關係 */
    @Test
    void publishedEventCarriesNormalizedEmail() {
        when(tokenService.verify(anyString(), anyString())).thenReturn(true);
        when(repository.confirmByEmail(anyString())).thenReturn(1);

        controller.confirm("  MixedCase@B.COM  ", "SIG");

        verify(publisher).publishEvent(new SubscriptionConfirmedEvent("mixedcase@b.com"));
    }

    /**
     * 簽章不符時不得寫入、不得發事件，但回應必須與成功時完全相同。
     *
     * <p>回應相同是刻意的：若失敗回不同訊息，任何人都能用這個端點
     * 逐一測試「某個 email 在不在名單裡」。</p>
     */
    @Test
    void invalidSignatureChangesNothingButLooksIdentical() {
        when(tokenService.verify(anyString(), anyString())).thenReturn(false);

        ResponseEntity<String> response = controller.confirm("a@b.com", "BAD");

        assertEquals(200, response.getStatusCode().value());
        verify(repository, never()).confirmByEmail(anyString());
        verify(publisher, never()).publishEvent(any(SubscriptionConfirmedEvent.class));
    }

    /**
     * 名單中查無此 email（confirmByEmail 回 0）時不得發事件。
     *
     * <p>沒有這道檢查，任何持有合法簽章的 email 每次點擊都會觸發一次獎勵計算。
     * 雖然 Task 4 的冪等檢查會擋掉重複發獎，但「名單裡沒有這筆卻發出
     * 確認事件」本身就是錯的狀態，不該靠下游擋。</p>
     */
    @Test
    void confirmingUnknownEmailPublishesNoEvent() {
        when(tokenService.verify(anyString(), anyString())).thenReturn(true);
        when(repository.confirmByEmail(anyString())).thenReturn(0);

        controller.confirm("ghost@b.com", "SIG");

        verify(publisher, never()).publishEvent(any(SubscriptionConfirmedEvent.class));
    }

    /** 確認訂閱是高可靠的參與度訊號，必須更新 last_engaged_at */
    @Test
    void validConfirmTouchesEngagement() {
        when(tokenService.verify(anyString(), anyString())).thenReturn(true);
        when(repository.confirmByEmail(anyString())).thenReturn(1);

        controller.confirm("a@b.com", "SIG");

        verify(repository).touchEngagement(eq("a@b.com"), any());
    }

    /** 簽章正確時退訂 */
    @Test
    void validUnsubscribeMarksUnsubscribed() {
        when(tokenService.verify(eq("a@b.com"), eq("SIG"))).thenReturn(true);

        ResponseEntity<String> response = controller.unsubscribe("a@b.com", "SIG");

        assertEquals(200, response.getStatusCode().value());
        verify(repository).unsubscribeByEmail("a@b.com");
    }

    /** 退訂簽章不符時不得寫入，回應仍相同 */
    @Test
    void invalidUnsubscribeSignatureChangesNothing() {
        when(tokenService.verify(anyString(), anyString())).thenReturn(false);

        controller.unsubscribe("a@b.com", "BAD");

        verify(repository, never()).unsubscribeByEmail(anyString());
    }

    /** email 缺漏時兩個端點都不得寫入（也不能 NPE） */
    @Test
    void missingEmailIsHandledSafely() {
        controller.confirm(null, "SIG");
        controller.unsubscribe(null, "SIG");

        verify(repository, never()).confirmByEmail(anyString());
        verify(repository, never()).unsubscribeByEmail(anyString());
    }

    /**
     * 回應頁不得回顯使用者輸入。
     *
     * <p>用一段 XSS 載荷當 email，斷言它不出現在回應中——不是斷言
     * 「有做跳脫」，而是斷言「根本不回顯」。固定字串頁面沒有回顯管道，
     * 這個測試守的是「日後有人為了友善提示而把 email 印出來」。</p>
     */
    @Test
    void responseNeverReflectsUserInput() {
        when(tokenService.verify(anyString(), anyString())).thenReturn(true);
        when(repository.confirmByEmail(anyString())).thenReturn(1);
        String payload = "<script>alert(1)</script>@b.com";

        assertFalse(controller.confirm(payload, "SIG").getBody().contains("script>alert"));
        assertFalse(controller.unsubscribe(payload, "SIG").getBody().contains("script>alert"));
    }
}
