package world.springai.survey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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

    /**
     * 寫入後必須立即生效：set() 會主動清掉該 key 的快取（後台改完立即生效的硬要求）。
     * 光看「set() 之後讀到新值」無法區分「快取被主動清除」「快取根本沒生效」「快取剛好過期」
     * 這三種情況，因為 mock 在第二次呼叫前就已經改成回傳新值。
     * 所以這裡額外驗證 repository.findById 的呼叫次數：
     * set() 之前先讀兩次，確認只打了 1 次 DB（第一次 miss 之後都命中快取，證明快取確實生效）；
     * set() 內部本身會多查一次現有值（用來決定是新增還是覆蓋），使累計變成 2 次；
     * set() 之後再讀一次，若快取真的被清除，必須再打一次 DB，累計變成 3 次
     * （若快取沒被清除，這裡仍會是 2 次，測試就會抓到強化前那種「證明不了自己」的假綠燈）。
     */
    @Test
    void setInvalidatesCacheSoChangeTakesEffectImmediately() {
        when(repository.findById("credit.premium_cost"))
            .thenReturn(Optional.of(new AppSetting("credit.premium_cost", "10")));
        when(repository.save(any(AppSetting.class))).thenAnswer(i -> i.getArgument(0));

        // set() 之前連續讀兩次：只有第一次會打 DB，第二次應命中快取
        assertEquals(10, service.getInt(AppSettingService.CREDIT_PREMIUM_COST, 10));
        assertEquals(10, service.getInt(AppSettingService.CREDIT_PREMIUM_COST, 10));
        verify(repository, times(1)).findById("credit.premium_cost");

        service.set(AppSettingService.CREDIT_PREMIUM_COST, "50");
        when(repository.findById("credit.premium_cost"))
            .thenReturn(Optional.of(new AppSetting("credit.premium_cost", "50")));

        // set() 之後再讀一次：若快取真的被清除，這裡必須重新打一次 DB，累計變成 3 次
        assertEquals(50, service.getInt(AppSettingService.CREDIT_PREMIUM_COST, 10));
        verify(repository, times(3)).findById("credit.premium_cost");
    }
}
