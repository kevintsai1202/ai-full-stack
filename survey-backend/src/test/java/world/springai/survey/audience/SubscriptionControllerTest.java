package world.springai.survey.audience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
    private AudiencePlatformService audiencePlatformService;
    private SubscriptionController controller;

    @BeforeEach
    void setUp() {
        repository = mock(SurveyResponseRepository.class);
        tokenService = mock(UnsubscribeTokenService.class);
        publisher = mock(ApplicationEventPublisher.class);
        audiencePlatformService = mock(AudiencePlatformService.class);
        controller = new SubscriptionController(
            repository, tokenService, publisher, audiencePlatformService);
    }

    /** 簽章正確時確認訂閱，並發布事件供 reader 發放推薦獎勵；回應頁必須真的是確認成功頁（正向內容） */
    @Test
    void validConfirmUpdatesConsentAndPublishesEvent() {
        when(tokenService.verify(eq("a@b.com"), eq("SIG"))).thenReturn(true);
        when(repository.confirmByEmail("a@b.com")).thenReturn(1);

        ResponseEntity<String> response = controller.confirm("a@b.com", "SIG");

        assertEquals(200, response.getStatusCode().value());
        verify(repository).confirmByEmail("a@b.com");
        verify(publisher).publishEvent(new SubscriptionConfirmedEvent("a@b.com"));
        assertTrue(response.getBody().contains("訂閱確認成功"),
            "確認頁必須含成功文案，否則 CONFIRM_HTML/UNSUBSCRIBE_HTML 互換也測不出來");
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
     * 事件發布失敗（下游監聽器拋例外）不得讓 confirm 端點回 500。
     *
     * <p>publishEvent 是同步呼叫，若不在 controller 端吞掉例外，
     * confirmByEmail 早已提交、但使用者卻看到 500——而且只有「有推薦
     * 關係」的 email 會走到會拋例外的監聽器，於是這個端點就變成了
     * 「這個 email 有沒有推薦關係」的探測器，破壞不洩漏名單這條性質。</p>
     */
    @Test
    void eventPublishFailureDoesNotFailConfirm() {
        when(tokenService.verify(anyString(), anyString())).thenReturn(true);
        when(repository.confirmByEmail(anyString())).thenReturn(1);
        doThrow(new RuntimeException("監聽器炸了")).when(publisher).publishEvent(any(SubscriptionConfirmedEvent.class));

        ResponseEntity<String> response = controller.confirm("a@b.com", "SIG");

        assertEquals(200, response.getStatusCode().value());
        verify(repository).confirmByEmail("a@b.com");
    }

    /**
     * 簽章不符時不得寫入、不得發事件，且回應必須與成功時「完全相同」的字串。
     *
     * <p>回應相同是刻意的：若失敗回不同訊息，任何人都能用這個端點
     * 逐一測試「某個 email 在不在名單裡」。這裡直接比對兩種情境的
     * body 字串，而不是只斷言 200 與 never()——否則日後有人加一條
     * 「查無此 email 就回較友善的頁」（仍回 200），測試依然是綠的，
     * 但那正是這個測試唯一該擋住的事。</p>
     */
    @Test
    void invalidSignatureChangesNothingButLooksIdentical() {
        when(tokenService.verify(anyString(), anyString())).thenReturn(true);
        when(repository.confirmByEmail(anyString())).thenReturn(1);
        String successBody = controller.confirm("a@b.com", "SIG").getBody();

        when(tokenService.verify(anyString(), anyString())).thenReturn(false);
        ResponseEntity<String> response = controller.confirm("a@b.com", "BAD");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(successBody, response.getBody(), "簽章不符時的回應必須與成功時完全相同");
        // confirmByEmail 只應被第一次（簽章正確）的呼叫觸發一次，簽章不符的第二次呼叫不得再寫入
        verify(repository, org.mockito.Mockito.times(1)).confirmByEmail("a@b.com");
        verify(publisher, org.mockito.Mockito.times(1)).publishEvent(any(SubscriptionConfirmedEvent.class));
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

    /** 簽章正確時退訂，且回應頁必須真的是退訂成功頁（正向內容） */
    @Test
    void validUnsubscribeMarksUnsubscribed() {
        when(tokenService.verify(eq("a@b.com"), eq("SIG"))).thenReturn(true);

        ResponseEntity<String> response = controller.unsubscribe("a@b.com", "SIG");

        assertEquals(200, response.getStatusCode().value());
        verify(repository).unsubscribeByEmail("a@b.com");
        assertTrue(response.getBody().contains("已成功取消訂閱"),
            "退訂頁必須含成功文案，否則 CONFIRM_HTML/UNSUBSCRIBE_HTML 互換也測不出來");
    }

    /**
     * 退訂簽章不符時不得寫入，且回應必須與成功時「完全相同」的字串。
     * 理由同 confirm 的 invalidSignatureChangesNothingButLooksIdentical。
     */
    @Test
    void invalidUnsubscribeSignatureChangesNothing() {
        when(tokenService.verify(anyString(), anyString())).thenReturn(true);
        String successBody = controller.unsubscribe("a@b.com", "SIG").getBody();

        when(tokenService.verify(anyString(), anyString())).thenReturn(false);
        ResponseEntity<String> response = controller.unsubscribe("a@b.com", "BAD");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(successBody, response.getBody(), "簽章不符時的回應必須與成功時完全相同");
        verify(repository, org.mockito.Mockito.times(1)).unsubscribeByEmail("a@b.com");
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
     * <p>用一個無跳脫爭議的標記（zzmarker）當 email 的一部分，斷言它
     * 不出現在回應中——不是斷言「有做跳脫」，而是斷言「根本不回顯」。
     * 若只用 XSS 載荷斷言，日後有人把 email 印出來但做了 HTML escape
     * （例如 &lt;script&gt;），這個測試仍會通過，而「根本不回顯」這條
     * 已經被破壞。保留 XSS 載荷斷言作為第二道防線。</p>
     */
    @Test
    void responseNeverReflectsUserInput() {
        when(tokenService.verify(anyString(), anyString())).thenReturn(true);
        when(repository.confirmByEmail(anyString())).thenReturn(1);
        String marker = "zzmarker@b.com";
        String payload = "<script>alert(1)</script>@b.com";

        assertFalse(controller.confirm(marker, "SIG").getBody().contains("zzmarker"));
        assertFalse(controller.unsubscribe(marker, "SIG").getBody().contains("zzmarker"));
        assertFalse(controller.confirm(payload, "SIG").getBody().contains("script>alert"));
        assertFalse(controller.unsubscribe(payload, "SIG").getBody().contains("script>alert"));
    }
}
