package world.springai.survey;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** AdminImportController 行為測試：金鑰保護、待確認匯入、既有 email 略過、無效 email 略過 */
@WebMvcTest(AdminImportController.class)
@Import(AdminKeyGuard.class)
@TestPropertySource(properties = {
    "app.admin-api-key=test-key",
    "app.cors-allowed-origins=http://localhost"
})
class AdminImportControllerTest {
    @Autowired MockMvc mvc;
    @MockBean SurveyResponseRepository repository;

    /** 未帶金鑰應回 401，不寫入任何資料 */
    @Test
    void importWithoutKeyReturns401() throws Exception {
        String body = "{\"source\":\"exam\",\"people\":[{\"email\":\"a@b.com\"}]}";
        mvc.perform(post("/api/admin/import").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isUnauthorized());
        verify(repository, never()).save(any());
    }

    /** 新 email 應以待確認狀態（consent=false）與指定來源寫入 */
    @Test
    void importSavesNewEmailAsPendingConsentWithSource() throws Exception {
        when(repository.existsByEmailIgnoreCase("student@example.com")).thenReturn(false);
        String body = "{\"source\":\"exam\",\"people\":[{\"email\":\"student@example.com\",\"name\":\"王小明\"}]}";
        mvc.perform(post("/api/admin/import").header("X-Admin-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.imported").value(1))
           .andExpect(jsonPath("$.skipped").value(0));
        ArgumentCaptor<SurveyResponse> captor = ArgumentCaptor.forClass(SurveyResponse.class);
        verify(repository).save(captor.capture());
        SurveyResponse saved = captor.getValue();
        assertEquals("student@example.com", saved.getEmail());
        assertEquals("王小明", saved.getName());
        assertEquals("exam", saved.getSource());
        assertEquals(false, saved.isConsent());
    }

    /** 已存在的 email（不分大小寫）應略過不重複寫入 */
    @Test
    void importSkipsExistingEmail() throws Exception {
        when(repository.existsByEmailIgnoreCase("dup@example.com")).thenReturn(true);
        String body = "{\"source\":\"exam\",\"people\":[{\"email\":\"dup@example.com\"}]}";
        mvc.perform(post("/api/admin/import").header("X-Admin-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.imported").value(0))
           .andExpect(jsonPath("$.skipped").value(1));
        verify(repository, never()).save(any());
    }

    /** 空白或格式無效的 email 應略過並計入 skipped */
    @Test
    void importSkipsInvalidEmail() throws Exception {
        String body = "{\"source\":\"exam\",\"people\":[{\"email\":\"\"},{\"email\":\"not-an-email\"}]}";
        mvc.perform(post("/api/admin/import").header("X-Admin-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.imported").value(0))
           .andExpect(jsonPath("$.skipped").value(2));
        verify(repository, never()).save(any());
    }

    /** 缺 source 或空名單應回 400 */
    @Test
    void importWithoutSourceReturns400() throws Exception {
        String body = "{\"people\":[{\"email\":\"a@b.com\"}]}";
        mvc.perform(post("/api/admin/import").header("X-Admin-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isBadRequest());
        verify(repository, never()).save(any());
    }
}
