package world.springai.survey.audience;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import world.springai.survey.AppSettingService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 名單來源服務測試：預設值、自訂持久化、同名冪等與輸入驗證。 */
class AudienceSourceServiceTest {

    private final AppSettingService settings = mock(AppSettingService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AudienceSourceService service = new AudienceSourceService(settings, objectMapper);

    /** 沒有自訂設定時仍應提供三個既有來源，確保舊流程不變。 */
    @Test
    void listReturnsDefaultsWithoutSetting() {
        when(settings.get(AudienceSourceService.SETTING_KEY)).thenReturn(null);

        List<AudienceSourceService.SourceOption> result = service.list();

        assertEquals(List.of("survey_form", "exam", "dify"),
                result.stream().map(AudienceSourceService.SourceOption::key).toList());
    }

    /** 新增來源應保存 JSON，並產生不暴露給管理員理解的內部鍵。 */
    @Test
    void addPersistsCustomSource() {
        when(settings.get(AudienceSourceService.SETTING_KEY)).thenReturn(null);

        AudienceSourceService.SourceOption created = service.add("2026 夏季班");

        assertEquals("2026 夏季班", created.label());
        assertTrue(created.key().startsWith("custom-"));
        verify(settings).set(eq(AudienceSourceService.SETTING_KEY),
                eq("[{\"key\":\"" + created.key() + "\",\"label\":\"2026 夏季班\"}]"));
    }

    /** 同名來源不分大小寫應直接回傳既有項目，不重複寫入設定。 */
    @Test
    void addSameLabelIsIdempotent() {
        when(settings.get(AudienceSourceService.SETTING_KEY))
                .thenReturn("[{\"key\":\"custom-partner\",\"label\":\"Partner List\"}]");

        AudienceSourceService.SourceOption result = service.add("partner list");

        assertEquals("custom-partner", result.key());
        verify(settings, never()).set(eq(AudienceSourceService.SETTING_KEY), org.mockito.ArgumentMatchers.anyString());
    }

    /** 空白與過長名稱都應在寫入前拒絕。 */
    @Test
    void addRejectsInvalidLabel() {
        assertEquals("請輸入名單來源名稱",
                assertThrows(IllegalArgumentException.class, () -> service.add("  ")).getMessage());
        assertEquals("名單來源名稱不可超過 40 個字",
                assertThrows(IllegalArgumentException.class, () -> service.add("a".repeat(41))).getMessage());
        verify(settings, never()).set(eq(AudienceSourceService.SETTING_KEY), org.mockito.ArgumentMatchers.anyString());
    }
}
