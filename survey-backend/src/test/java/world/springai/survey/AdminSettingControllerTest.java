package world.springai.survey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
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

        verify(settings, never()).setAll(anyMap());
    }

    /** 讀取回全部可調參數 */
    @Test
    void listsAllAdjustableSettings() throws Exception {
        mvc.perform(get("/api/admin/settings").header(KEY, "ok"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$['credit.premium_cost'].value").exists())
           .andExpect(jsonPath("$['credit.signup_grant'].value").exists())
           .andExpect(jsonPath("$['credit.referral_reward'].value").exists())
           .andExpect(jsonPath("$['vip.default_days'].value").exists());
    }

    /**
     * 每個參數都必須帶著允許區間回傳。
     *
     * <p>{@code admin.html} 用這兩個欄位設 {@code <input type=number>} 的
     * {@code min} / {@code max}。少了它們，前端就得自己寫一份界限——同一個
     * 規則兩份實作，遲早出現「頁面允許輸入、後端回 400」或更糟的「頁面擋住、
     * 後端其實允許」。這個測試守的是那份唯一來源真的送到前端。</p>
     *
     * <p>斷言具體數值（而不是只斷言 {@code exists()}）：只驗欄位存在的話，
     * 把 min/max 對調或全部回 0 都不會被抓到。</p>
     */
    @Test
    void listExposesBoundsForEverySetting() throws Exception {
        mvc.perform(get("/api/admin/settings").header(KEY, "ok"))
           .andExpect(status().isOk())
           // 下限與 CreditPolicy 的夾值語意一致：贈點類允許 0（關閉），成本與效期最小 1
           .andExpect(jsonPath("$['credit.signup_grant'].min").value(0))
           .andExpect(jsonPath("$['credit.premium_cost'].min").value(1))
           .andExpect(jsonPath("$['credit.referral_reward'].min").value(0))
           .andExpect(jsonPath("$['vip.default_days'].min").value(1))
           // 上限：三個點數參數共用同一個量級，VIP 效期是十年
           .andExpect(jsonPath("$['credit.signup_grant'].max").value(10000))
           .andExpect(jsonPath("$['credit.premium_cost'].max").value(10000))
           .andExpect(jsonPath("$['credit.referral_reward'].max").value(10000))
           .andExpect(jsonPath("$['vip.default_days'].max").value(3650));
    }

    /** 更新單筆參數 */
    @Test
    void updatesSetting() throws Exception {
        mvc.perform(put("/api/admin/settings").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credit.premium_cost\":\"20\"}"))
           .andExpect(status().isOk());

        verify(settings).setAll(eq(Map.of(AppSettingService.CREDIT_PREMIUM_COST, 20)));
    }

    /** 可一次更新多筆 */
    @Test
    void updatesMultipleSettings() throws Exception {
        mvc.perform(put("/api/admin/settings").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credit.premium_cost\":\"20\",\"credit.referral_reward\":\"150\"}"))
           .andExpect(status().isOk());

        verify(settings).setAll(eq(Map.of(
            AppSettingService.CREDIT_PREMIUM_COST, 20,
            AppSettingService.CREDIT_REFERRAL_REWARD, 150)));
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

        verify(settings, never()).setAll(anyMap());
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

        verify(settings, never()).setAll(anyMap());
    }

    /** 非整數值必須回 400（全部可調參數都是整數） */
    @Test
    void nonIntegerValueIsRejected() throws Exception {
        mvc.perform(put("/api/admin/settings").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credit.premium_cost\":\"abc\"}"))
           .andExpect(status().isBadRequest());

        verify(settings, never()).setAll(anyMap());
    }

    /**
     * 值為 JSON {@code null} 必須回 400，而不是讓 {@code .trim()} 拋出 NPE 變成 500。
     *
     * <p>{@code Map<String, String>} 反序列化 JSON {@code null} 會得到 Java
     * {@code null}，型別驗證那段 {@code catch (NumberFormatException | NullPointerException)}
     * 就是為了接住這個情形；先前沒有測試覆蓋這一支，補上。</p>
     */
    @Test
    void nullValueIsRejected() throws Exception {
        mvc.perform(put("/api/admin/settings").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credit.premium_cost\":null}"))
           .andExpect(status().isBadRequest());

        verify(settings, never()).setAll(anyMap());
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
        verify(settings, never()).setAll(anyMap());
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

    /**
     * 每個參數的上限值本身必須是合法輸入。
     *
     * <p>上限存在的意義是擋住「打錯位數」，不是把營運空間縮到用不了。若把區間
     * 寫成半開（{@code value >= max} 就拒），這個測試會抓到。</p>
     */
    @Test
    void upperBoundValueItselfIsAccepted() throws Exception {
        for (Map.Entry<String, Integer> entry : MAX_VALUES.entrySet()) {
            mvc.perform(put("/api/admin/settings").header(KEY, "ok")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"}"))
               .andExpect(status().isOk());

            verify(settings).setAll(eq(Map.of(entry.getKey(), entry.getValue())));
        }
    }

    /**
     * 超過上限必須回 400，且一筆都不寫入。
     *
     * <p><b>沒有上限的實際後果</b>：{@code credit.signup_grant} 設成
     * {@link Integer#MAX_VALUE} 之後每位新讀者都拿到 21 億點，而點數不過期、
     * 規則調整也不回收（{@code /r/rules} 的明文承諾），事後清不乾淨；
     * {@code credit.premium_cost} 設成極大值則讓單篇成本超過任何人可能累積的
     * 餘額，等於把全站進階內容鎖死。只有後台可達，但下限既然已逐項寫了，
     * 補上限的邊際成本接近零。</p>
     */
    @Test
    void aboveUpperBoundIsRejected() throws Exception {
        for (Map.Entry<String, Integer> entry : MAX_VALUES.entrySet()) {
            mvc.perform(put("/api/admin/settings").header(KEY, "ok")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"" + entry.getKey() + "\":\"" + (entry.getValue() + 1) + "\"}"))
               .andExpect(status().isBadRequest());

            // 極端值（打錯位數最常見的樣子）同樣要被擋
            mvc.perform(put("/api/admin/settings").header(KEY, "ok")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"" + entry.getKey() + "\":\"" + Integer.MAX_VALUE + "\"}"))
               .andExpect(status().isBadRequest());
        }

        verify(settings, never()).setAll(anyMap());
    }

    /**
     * 400 的錯誤訊息必須寫明允許區間。
     *
     * <p>後台的 {@code api()} 會把 reason 直接顯示給操作者。只說「不得小於 1」
     * 或「超出範圍」，他仍得靠試錯才知道上限在哪；一次講完區間可以省掉那一輪。</p>
     */
    @Test
    void rejectionMessageStatesAllowedRange() throws Exception {
        // 超過上限
        mvc.perform(put("/api/admin/settings").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"vip.default_days\":\"4000\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.detail")
               .value(org.hamcrest.Matchers.containsString("必須在 1 到 3650 之間")));

        // 低於下限也要講整個區間，而不是只講下限
        mvc.perform(put("/api/admin/settings").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credit.signup_grant\":\"-1\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.detail")
               .value(org.hamcrest.Matchers.containsString("必須在 0 到 10000 之間")));
    }

    /**
     * 每個參數的上限（與 {@code AdminSettingController.ADJUSTABLE} 一致）。
     *
     * <p>刻意在測試裡另寫一份數字而不是讀生產程式的常數：讀同一個常數的測試
     * 恆為真，改壞了也不會變紅。</p>
     */
    private static final Map<String, Integer> MAX_VALUES = new java.util.LinkedHashMap<>(Map.of(
        AppSettingService.CREDIT_SIGNUP_GRANT, 10000,
        AppSettingService.CREDIT_PREMIUM_COST, 10000,
        AppSettingService.CREDIT_REFERRAL_REWARD, 10000,
        AppSettingService.VIP_DEFAULT_DAYS, 3650));
}
