package world.springai.survey.reader;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import world.springai.survey.audience.SurveyResponse;
import world.springai.survey.audience.SurveyResponseRepository;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 驗證 {@link ReaderProfileService#updateName} 的 {@code @Transactional}
 * <b>真的經過 Spring proxy</b>。
 *
 * <p><b>為什麼一定要一個會啟動 context 的測試</b>：
 * {@code ReaderPortalControllerTest} 用 {@code standaloneSetup} 直接 new 出 controller
 * 與服務，那裡沒有任何 proxy——把 {@code updateName} 的 {@code @Transactional} 整個
 * 註解掉，那 35 個測試依然全綠。也就是說「改名與參與度時間戳寫在同一交易內」
 * 這個保證，在那份測試裡是零覆蓋。這正是 Task 11 抓到的同一個盲區。</p>
 *
 * <p>日後可能讓它失效而不會有其他測試變紅的重構：
 * ① 把交易性程式碼搬回 {@code ReaderPortalController}（同類別內部呼叫不過 proxy，
 *    而且交易會在身分驗證之前就開，公開端點被打時白借連線）；
 * ② 把 {@link ReaderProfileService} 改成 controller 自己 {@code new} 出來的物件；
 * ③ 把兩個寫入拆進同類別的私有方法再加註解。</p>
 *
 * <p>作法比照 {@link AdminReaderTransactionTest} 與
 * {@code AppSettingServiceTransactionTest}：在被 {@code @Autowired} 注入（因而帶 proxy）
 * 的 bean 上呼叫方法，於 Mockito stub 的回呼裡斷言
 * {@link TransactionSynchronizationManager#isActualTransactionActive()}。
 * {@code SurveyResponseRepository} 是 {@code @MockBean}，本身不會開任何交易，
 * 所以「交易存在」只可能來自 {@code updateName} 的註解。</p>
 */
@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:5433/postgres",
    "spring.datasource.username=postgres",
    "spring.datasource.password=password"
})
class ReaderProfileTransactionTest {

    private static final String EMAIL = "reader@example.com";

    /** 由 Spring 注入，因此帶著交易 proxy——這正是本測試的重點 */
    @Autowired ReaderProfileService service;

    @MockBean SurveyResponseRepository surveyResponseRepository;

    /** 目前是否處於實體交易中；不在交易內回 null 名稱 */
    private static String currentTransactionName() {
        return TransactionSynchronizationManager.isActualTransactionActive()
            ? TransactionSynchronizationManager.getCurrentTransactionName()
            : null;
    }

    /** 建一筆名單列（帶 id：改名走的是以 id 為條件的 UPDATE） */
    private static SurveyResponse row() {
        SurveyResponse r = new SurveyResponse();
        r.setId(42L);
        r.setEmail(EMAIL);
        return r;
    }

    /**
     * 改名與參與度時間戳這兩個寫入必須落在<b>同一個實體交易</b>內。
     *
     * <p>把 {@code ReaderProfileService.updateName} 的 {@code @Transactional} 拿掉，
     * 這個測試就會失敗（repository 是 mock，不會自己開交易）——那正是它存在的理由。</p>
     */
    @Test
    void updateNameWritesNameAndEngagementInsideSameTransaction() {
        AtomicReference<String> nameTxn = new AtomicReference<>();
        AtomicReference<String> touchTxn = new AtomicReference<>();

        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(anyString()))
            .thenReturn(Optional.of(row()));
        // 改名走的是只寫 name 一欄的條件式 UPDATE，不是 save(entity) 整列寫回
        when(surveyResponseRepository.updateName(any(), anyString())).thenAnswer(inv -> {
            nameTxn.set(currentTransactionName());
            return 1;
        });
        when(surveyResponseRepository.touchEngagement(anyString(), any())).thenAnswer(inv -> {
            touchTxn.set(currentTransactionName());
            return 1;
        });

        assertTrue(service.updateName(EMAIL, "凱文"));

        assertNotNull(nameTxn.get(), "改名寫入不在交易內：@Transactional 沒有經過 proxy");
        assertNotNull(touchTxn.get(), "參與度寫入不在交易內：@Transactional 沒有經過 proxy");
        // 同一個交易名稱才能證明兩個寫入會一起提交、一起回滾
        assertEquals(nameTxn.get(), touchTxn.get(), "改名與參與度寫入落在不同交易");
        assertTrue(nameTxn.get().contains("ReaderProfileService.updateName"),
            "交易不是由 ReaderProfileService.updateName 開的：" + nameTxn.get());
        // 絕不整列寫回：那會把 consent／unsubscribed 一起覆蓋回 SELECT 當下的舊值
        org.mockito.Mockito.verify(surveyResponseRepository, org.mockito.Mockito.never())
            .save(any(SurveyResponse.class));
    }

    /**
     * 該列在 SELECT 與 UPDATE 之間被刪掉時回 {@code false}（→ 404），不假裝成功，
     * 也不寫參與度時間戳。
     *
     * <p>正確性來自受影響筆數，不是來自「先前查到過那一列」。若把回傳值丟掉不看，
     * 讀者會看到「已儲存」而名稱其實沒有任何地方存著。</p>
     */
    @Test
    void updateNameReportsFailureWhenNoRowUpdated() {
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(anyString()))
            .thenReturn(Optional.of(row()));
        when(surveyResponseRepository.updateName(any(), anyString())).thenReturn(0);

        org.junit.jupiter.api.Assertions.assertFalse(service.updateName(EMAIL, "凱文"));

        org.mockito.Mockito.verify(surveyResponseRepository, org.mockito.Mockito.never())
            .touchEngagement(anyString(), any());
    }

    /**
     * 名單中查無此 email 時不得有任何寫入，也不該把 404 判斷留在交易裡做完。
     *
     * <p>這條同時釘住「不建新列」——名單中心的每一列都是一份同意紀錄。</p>
     */
    @Test
    void updateNameWithoutAudienceRowWritesNothing() {
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(anyString()))
            .thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertFalse(service.updateName(EMAIL, "凱文"));

        org.mockito.Mockito.verify(surveyResponseRepository, org.mockito.Mockito.never())
            .save(any(SurveyResponse.class));
        org.mockito.Mockito.verify(surveyResponseRepository, org.mockito.Mockito.never())
            .updateName(any(), anyString());
        org.mockito.Mockito.verify(surveyResponseRepository, org.mockito.Mockito.never())
            .touchEngagement(anyString(), any());
    }
}
