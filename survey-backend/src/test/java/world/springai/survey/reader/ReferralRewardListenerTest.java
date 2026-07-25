package world.springai.survey.reader;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import world.springai.survey.audience.SubscriptionConfirmedEvent;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 驗證 ReferralRewardListener 真的會被 Spring 的事件機制觸發。
 *
 * <p><b>為什麼需要一個會啟動 context 的測試</b>：本計畫第一版把監聽器寫成
 * {@code @TransactionalEventListener(AFTER_COMMIT)}，而發布端
 * {@code SubscriptionController.confirm} 沒有交易——那個註解在無交易時
 * <b>預設完全不觸發，也不報錯</b>。若測試只直接呼叫
 * {@code listener.onSubscriptionConfirmed(event)}，這種失效永遠不會被發現：
 * 獎勵不發、日誌乾淨、測試全綠。唯一能抓到它的方式，就是真的用
 * {@link ApplicationEventPublisher} 發布事件，讓 Spring 決定要不要呼叫。</p>
 */
@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:5433/postgres",
    "spring.datasource.username=postgres",
    "spring.datasource.password=password"
})
class ReferralRewardListenerTest {

    @Autowired ApplicationEventPublisher publisher;

    /** 把真正的發放邏輯換成 mock：本測試只關心「監聽器有沒有被呼叫」 */
    @MockBean ReferralService referralService;

    /**
     * 發布事件後，監聽器必須真的被呼叫。
     *
     * <p>若 {@code @EventListener} 被換回
     * {@code @TransactionalEventListener}，這個測試會失敗（zero interactions）
     * ——那正是它存在的理由。</p>
     */
    @Test
    void listenerIsActuallyInvokedByPublishedEvent() {
        when(referralService.rewardFor(anyString()))
            .thenReturn(ReferralService.RewardOutcome.NO_REFERRER);

        publisher.publishEvent(new SubscriptionConfirmedEvent("invitee@example.com"));

        verify(referralService).rewardFor("invitee@example.com");
    }

    /**
     * 發放邏輯拋例外時不可讓例外傳出去。
     *
     * <p>發布端是公開端點且必須「不論結果一律回相同的 200」。若例外往上拋，
     * 有推薦關係的 email 回 500、其他人回 200，端點就變成關係探測器。</p>
     */
    @Test
    void listenerSwallowsExceptionsSoPublisherIsUnaffected() {
        when(referralService.rewardFor(anyString()))
            .thenThrow(new RuntimeException("模擬帳本寫入失敗"));

        // 不應拋出：監聽器內部必須自行吞掉
        publisher.publishEvent(new SubscriptionConfirmedEvent("invitee@example.com"));

        verify(referralService).rewardFor("invitee@example.com");
    }
}
