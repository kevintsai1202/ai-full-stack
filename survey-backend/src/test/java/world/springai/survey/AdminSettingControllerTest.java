package world.springai.survey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 後台參數設定：金鑰保護、白名單、型別驗證 */
class AdminSettingControllerTest {

    private static final String KEY = "X-Admin-Key";

    private AdminKeyGuard guard;
    private AppSettingService settings;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        guard = mock(AdminKeyGuard.class);
        settings = mock(AppSettingService.class);
        when(settings.getInt(anyString(), anyInt()))
            .thenAnswer(inv -> inv.getArgument(1, Integer.class));
        mvc = MockMvcBuilders.standaloneSetup(new AdminSettingController(guard, settings))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    /** 兩個端點都要金鑰 */
    @Test
    void bothEndpointsRequireAdminKey() throws Exception {
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED))
            .when(guard).verify(any());

        mvc.perform(get("/api/admin/settings")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/settings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"credit.premium_cost\":\"20\"}"))
           .andExpect(status().isUnauthorized());

        verify(settings, never()).set(anyString(), anyString());
    }

    /** 讀取回全部可調參數 */
    @Test
    void listsAllAdjustableSettings() throws Exception {
        mvc.perform(get("/api/admin/settings").header(KEY, "ok"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$['credit.premium_cost']").exists())
           .andExpect(jsonPath("$['credit.signup_grant']").exists())
           .andExpect(jsonPath("$['credit.referral_reward']").exists())
           .andExpect(jsonPath("$['vip.default_days']").exists());
    }

    /** 更新單筆參數 */
    @Test
    void updatesSetting() throws Exception {
        mvc.perform(put("/api/admin/settings").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credit.premium_cost\":\"20\"}"))
           .andExpect(status().isOk());

        verify(settings).set(eq(AppSettingService.CREDIT_PREMIUM_COST), eq("20"));
    }

    /** 可一次更新多筆 */
    @Test
    void updatesMultipleSettings() throws Exception {
        mvc.perform(put("/api/admin/settings").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credit.premium_cost\":\"20\",\"credit.referral_reward\":\"150\"}"))
           .andExpect(status().isOk());

        verify(settings).set(eq(AppSettingService.CREDIT_PREMIUM_COST), eq("20"));
        verify(settings).set(eq(AppSettingService.CREDIT_REFERRAL_REWARD), eq("150"));
    }

    /**
     * 不在白名單的鍵必須回 400。
     *
     * <p>沒有白名單，這個端點就變成「往 app_setting 寫任意鍵值」的通用寫入口。
     * 那不只是資料髒污——若日後有任何功能改讀 app_setting 的某個鍵，
     * 這個洞就成了行為注入點。</p>
     */
    @Test
    void unknownKeyIsRejected() throws Exception {
        mvc.perform(put("/api/admin/settings").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"evil.key\":\"boom\"}"))
           .andExpect(status().isBadRequest());

        verify(settings, never()).set(anyString(), anyString());
    }

    /**
     * 不在白名單的鍵，即使值是合法整數，仍必須回 400。
     *
     * <p>{@link #unknownKeyIsRejected} 用的值 "boom" 不是整數，會被型別檢查
     * 攔下，不足以單獨證明白名單本身有在作用（拿掉白名單、只留型別檢查，
     * 那個測試依然會綠燈）。這裡改用合法整數 "999"，只有白名單真的存在
     * 才會擋下——用來破壞性驗證「拿掉白名單」這個失效模式。</p>
     */
    @Test
    void unknownKeyWithValidIntegerValueIsRejected() throws Exception {
        mvc.perform(put("/api/admin/settings").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"evil.key\":\"999\"}"))
           .andExpect(status().isBadRequest());

        verify(settings, never()).set(anyString(), anyString());
    }

    /** 非整數值必須回 400（全部可調參數都是整數） */
    @Test
    void nonIntegerValueIsRejected() throws Exception {
        mvc.perform(put("/api/admin/settings").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credit.premium_cost\":\"abc\"}"))
           .andExpect(status().isBadRequest());

        verify(settings, never()).set(anyString(), anyString());
    }

    /**
     * 一筆無效就整批不寫入。
     *
     * <p>部分成功會讓後台顯示「已儲存」卻只改了一半，而使用者無從得知
     * 哪一筆沒進去。先全部驗證再全部寫入。</p>
     */
    @Test
    void invalidEntryRejectsWholeBatch() throws Exception {
        mvc.perform(put("/api/admin/settings").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credit.premium_cost\":\"20\",\"credit.referral_reward\":\"abc\"}"))
           .andExpect(status().isBadRequest());

        // 有效的那筆也不可以被寫入
        verify(settings, never()).set(anyString(), anyString());
    }

    /**
     * premium_cost 設為 0 或負數必須回 400。
     *
     * <p>CreditPolicy 會把它夾成 1，所以不會外洩內容——但後台顯示 0
     * 而實際是 1 的落差同樣會誤導營運判斷。在入口就擋掉比較誠實。</p>
     */
    @Test
    void nonPositivePremiumCostIsRejected() throws Exception {
        mvc.perform(put("/api/admin/settings").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credit.premium_cost\":\"0\"}"))
           .andExpect(status().isBadRequest());
    }

    /** 贈點與邀請獎勵允許 0（關閉該機制），但不允許負數 */
    @Test
    void zeroGrantIsAllowedButNegativeIsNot() throws Exception {
        mvc.perform(put("/api/admin/settings").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credit.signup_grant\":\"0\"}"))
           .andExpect(status().isOk());

        mvc.perform(put("/api/admin/settings").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credit.signup_grant\":\"-1\"}"))
           .andExpect(status().isBadRequest());
    }
}
