package world.springai.survey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** AppSettingService 行為測試：查無回預設、快取避免重複查詢、寫入後立即生效 */
class AppSettingServiceTest {

    private AppSettingRepository repository;
    private AppSettingService service;

    @BeforeEach
    void setUp() {
        repository = mock(AppSettingRepository.class);
        service = new AppSettingService(repository);
    }

    /** 查無此 key 時回傳呼叫端給的預設值（故新增參數不需 data migration） */
    @Test
    void missingKeyFallsBackToDefault() {
        when(repository.findById("credit.signup_grant")).thenReturn(Optional.empty());
        assertEquals(300, service.getInt(AppSettingService.CREDIT_SIGNUP_GRANT, 300));
    }

    /** 有值時回傳 DB 的值 */
    @Test
    void storedValueOverridesDefault() {
        when(repository.findById("credit.premium_cost"))
            .thenReturn(Optional.of(new AppSetting("credit.premium_cost", "50")));
        assertEquals(50, service.getInt(AppSettingService.CREDIT_PREMIUM_COST, 10));
    }

    /** 值不是數字時退回預設，不得讓壞資料炸掉授權判斷 */
    @Test
    void nonNumericValueFallsBackToDefault() {
        when(repository.findById("credit.premium_cost"))
            .thenReturn(Optional.of(new AppSetting("credit.premium_cost", "abc")));
        assertEquals(10, service.getInt(AppSettingService.CREDIT_PREMIUM_COST, 10));
    }

    /** 同一 key 連續讀取只查一次 DB（授權判斷每次都會讀，不能每次打 DB） */
    @Test
    void repeatedReadsUseCache() {
        when(repository.findById("credit.premium_cost"))
            .thenReturn(Optional.of(new AppSetting("credit.premium_cost", "10")));

        service.getInt(AppSettingService.CREDIT_PREMIUM_COST, 10);
        service.getInt(AppSettingService.CREDIT_PREMIUM_COST, 10);
        service.getInt(AppSettingService.CREDIT_PREMIUM_COST, 10);

        verify(repository, times(1)).findById("credit.premium_cost");
    }

    /** 寫入後必須立即生效：儲存會清掉該 key 的快取（後台改完立即生效的硬要求） */
    @Test
    void setInvalidatesCacheSoChangeTakesEffectImmediately() {
        when(repository.findById("credit.premium_cost"))
            .thenReturn(Optional.of(new AppSetting("credit.premium_cost", "10")));
        when(repository.save(any(AppSetting.class))).thenAnswer(i -> i.getArgument(0));
        assertEquals(10, service.getInt(AppSettingService.CREDIT_PREMIUM_COST, 10));

        service.set(AppSettingService.CREDIT_PREMIUM_COST, "50");
        when(repository.findById("credit.premium_cost"))
            .thenReturn(Optional.of(new AppSetting("credit.premium_cost", "50")));

        assertEquals(50, service.getInt(AppSettingService.CREDIT_PREMIUM_COST, 10));
    }
}
