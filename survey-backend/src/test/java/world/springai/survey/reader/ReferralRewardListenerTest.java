package world.springai.survey.reader;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import world.springai.survey.audience.SubscriptionConfirmedEvent;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * <b>本測試<u>不</u>執行任何 SQL，也不驗證任何 repository 的 {@code @Query}</b>——
 * {@link ReferralService} 整個是 {@code @MockBean}，所以 {@code addCredits}、
 * {@code confirmByEmail}、{@code touchEngagement} 與帳本寫入一句都不會發生。
 * 它<b>唯一</b>驗證的事情是 ReferralRewardListener 真的會被 Spring 的事件機制觸發
 * （以及監聽器自己會吞掉下游例外）。
 *
 * <p><b>不要把它讀成發獎路徑的資料庫覆蓋</b>：{@code @SpringBootTest} 上那組真實
 * datasource 設定只是為了讓 context 起得來，本測試從頭到尾沒有用它讀寫任何一列。
 * 發獎的冪等與帳本／餘額不變式由 {@code ReferralIdempotencyTest} 守著。</p>
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
    /** 正式監聽器優先走新版成長引擎，本測試同樣只驗證事件接線。 */
    @MockBean ReferralGrowthService growthService;

    /**
     * 發布事件後，監聽器必須真的被呼叫。
     *
     * <p>若 {@code @EventListener} 被換回
     * {@code @TransactionalEventListener}，這個測試會失敗（zero interactions）
     * ——那正是它存在的理由。</p>
     */
    @Test
    void listenerIsActuallyInvokedByPublishedEvent() {
        when(growthService.confirmAndReward(anyString()))
            .thenReturn(ReferralGrowthService.Outcome.NO_REFERRER);

        publisher.publishEvent(new SubscriptionConfirmedEvent("invitee@example.com"));

        verify(growthService).confirmAndReward("invitee@example.com");
    }

    /**
     * 發放邏輯拋例外時不可讓例外傳出去。
     *
     * <p>發布端是公開端點且必須「不論結果一律回相同的 200」。若例外往上拋，
     * 有推薦關係的 email 回 500、其他人回 200，端點就變成關係探測器。</p>
     */
    @Test
    void listenerSwallowsExceptionsSoPublisherIsUnaffected() {
        when(growthService.confirmAndReward(anyString()))
            .thenThrow(new RuntimeException("模擬帳本寫入失敗"));

        // 不應拋出：監聽器內部必須自行吞掉
        publisher.publishEvent(new SubscriptionConfirmedEvent("invitee@example.com"));

        verify(growthService).confirmAndReward("invitee@example.com");
    }

    /**
     * 撞上冪等唯一索引（{@code DataIntegrityViolationException}）必須被視為
     * 「已發過」而正常結束，<b>而且只能記 INFO、不可記 ERROR</b>。
     *
     * <p>這是 V9 之後<b>正常且會頻繁發生</b>的路徑：依 spec §5.4，
     * {@code confirmByEmail} 對已確認過的人仍回報 1 列，所以每次點擊舊確認信
     * 都會發出事件、都會撞到唯一索引。它必須是安靜的成功路徑。</p>
     *
     * <p><b>為什麼要斷言 log level，而不只是「沒拋出」</b>：只驗「publishEvent 沒拋」
     * 加「rewardFor 被呼叫過」的話，把 {@link ReferralRewardListener} 裡那個專屬的
     * {@code catch (DataIntegrityViolationException)} 整段刪掉，後面的
     * {@code catch (Exception)} 照樣接住、照樣不外洩，測試依然全綠——那個測試證明不了
     * 自己的名字。而「記 INFO 不記 ERROR」在程式註解裡被明確描述為 load-bearing：
     * 冪等命中是常態，若每次都寫一行 ERROR + 完整 stacktrace，真正的發獎失敗
     * （需要人工補點的那些）就會被雜訊淹掉。這條性質必須有測試守著。</p>
     *
     * <p>捕捉發生在<b>交易邊界之外</b>（本監聽器刻意不帶 {@code @Transactional}），
     * 交易邊界是 {@code ReferralService.rewardFor} 自己的 proxy。真實資料庫下的
     * 完整驗證見 {@code ReferralIdempotencyTest}。</p>
     */
    @Test
    void listenerTreatsUniqueViolationAsAlreadyRewarded() {
        when(growthService.confirmAndReward(anyString()))
            .thenThrow(new DataIntegrityViolationException("uq_credit_txn_referral_note"));

        // 掛一個 ListAppender 到監聽器自己的 logger，攔下本事件產生的所有紀錄。
        // 用 logback 的 ListAppender 而不是解析 stdout：後者依賴 pattern 格式，
        // 換一次 logging.pattern 就會靜默失效。
        Logger listenerLogger = (Logger) LoggerFactory.getLogger(ReferralRewardListener.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        listenerLogger.addAppender(appender);
        try {
            publisher.publishEvent(new SubscriptionConfirmedEvent("invitee@example.com"));
        } finally {
            // 一定要卸下：appender 掛在共用的 logger 上，留著會汙染同一個 JVM 的其他測試
            listenerLogger.detachAppender(appender);
            appender.stop();
        }

        verify(growthService).confirmAndReward("invitee@example.com");

        List<ILoggingEvent> events = appender.list;
        assertTrue(events.stream().noneMatch(e -> e.getLevel() == Level.ERROR),
            "冪等命中被記成 ERROR：重複點擊舊確認信是常態路徑，"
                + "每次寫一行 ERROR + stacktrace 會把真正需要人工補點的失敗淹掉。"
                + "實際紀錄：" + describe(events));
        assertTrue(events.stream().anyMatch(
                e -> e.getLevel() == Level.INFO
                    && e.getFormattedMessage().contains("已處理")),
            "沒有任何一行 INFO 說明這是「已發過」：冪等命中若完全不留痕跡，"
                + "客訴時無法分辨「發過了」與「根本沒收到事件」。實際紀錄：" + describe(events));
    }

    /** 把攔到的紀錄整理成可讀字串，讓斷言失敗時直接看得到 level 與訊息 */
    private static String describe(List<ILoggingEvent> events) {
        return events.stream()
            .map(e -> e.getLevel() + " " + e.getFormattedMessage())
            .collect(Collectors.joining(" | "));
    }
}
