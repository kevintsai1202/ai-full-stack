package world.springai.survey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 驗證 {@link AppSettingService#setAll} 的 {@code @Transactional} <b>真的經過 Spring proxy</b>。
 *
 * <p><b>為什麼一定要一個會啟動 context 的測試</b>：
 * {@link AdminSettingControllerTest} 用 {@code standaloneSetup} 直接 new 出
 * controller、並把 {@code AppSettingService} 整個 mock 掉，交易註解在那份測試裡
 * 完全不參與——把 {@code setAll} 的 {@code @Transactional} 整個拿掉，那份測試依然
 * 全綠。也就是說，「批次寫入包在單一交易內」這個保證，在那份測試裡是零覆蓋。</p>
 *
 * <p>作法比照 {@code AdminReaderTransactionTest}：在被 proxy 的 bean
 * （由 {@code @Autowired} 注入）上呼叫方法，於 Mockito stub 的回呼裡斷言
 * {@link TransactionSynchronizationManager#isActualTransactionActive()}。
 * {@code AppSettingRepository} 是 {@code @MockBean}，本身不會開任何交易，
 * 所以「交易存在」只可能來自 {@code setAll} 的註解——這是唯一能證明 proxy
 * 真的生效的方式。</p>
 */
@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:5433/postgres",
    "spring.datasource.username=postgres",
    "spring.datasource.password=password"
})
class AppSettingServiceTransactionTest {

    /** 由 Spring 注入，因此帶著交易 proxy——這正是本測試的重點 */
    @Autowired AppSettingService service;

    @MockBean AppSettingRepository repository;

    /** 目前是否處於實體交易中；不在交易內回 null 名稱 */
    private static String currentTransactionName() {
        return TransactionSynchronizationManager.isActualTransactionActive()
            ? TransactionSynchronizationManager.getCurrentTransactionName()
            : null;
    }

    /**
     * 批次寫入的每一筆 {@code repository.save()} 都必須發生在同一個實體交易內。
     *
     * <p>把 {@code AppSettingService.setAll} 的 {@code @Transactional} 拿掉，
     * 這個測試就會失敗（{@code repository} 是 mock，不會自己開交易）——
     * 那正是它存在的理由。</p>
     */
    @Test
    void setAllWritesEveryKeyInsideSameTransaction() {
        when(repository.findById(anyString())).thenReturn(Optional.empty());

        AtomicReference<String> firstTxn = new AtomicReference<>();
        AtomicReference<String> secondTxn = new AtomicReference<>();
        when(repository.save(any(AppSetting.class))).thenAnswer(inv -> {
            AppSetting saved = inv.getArgument(0);
            String txn = currentTransactionName();
            if (AppSettingService.CREDIT_PREMIUM_COST.equals(saved.getSettingKey())) {
                firstTxn.set(txn);
            } else if (AppSettingService.CREDIT_REFERRAL_REWARD.equals(saved.getSettingKey())) {
                secondTxn.set(txn);
            }
            return saved;
        });

        Map<String, Integer> updates = new LinkedHashMap<>();
        updates.put(AppSettingService.CREDIT_PREMIUM_COST, 20);
        updates.put(AppSettingService.CREDIT_REFERRAL_REWARD, 150);
        service.setAll(updates);

        assertNotNull(firstTxn.get(), "第一筆寫入不在交易內：@Transactional 沒有經過 proxy");
        assertNotNull(secondTxn.get(), "第二筆寫入不在交易內：@Transactional 沒有經過 proxy");
        // 同一個交易名稱才能證明兩筆寫入會一起提交、一起回滾
        assertEquals(firstTxn.get(), secondTxn.get(), "兩筆寫入落在不同交易");
        assertTrue(firstTxn.get().contains("AppSettingService.setAll"),
            "交易不是由 AppSettingService.setAll 開的：" + firstTxn.get());
    }
}
