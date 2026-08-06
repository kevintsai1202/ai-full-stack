package world.springai.survey.media;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import world.springai.survey.AdminKeyGuard;
import world.springai.survey.admin.AdminAllowlist;
import world.springai.survey.admin.AdminSessionAccess;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin 媒體 API 的金鑰守門與 multipart 契約測試。 */
@WebMvcTest(AdminMediaController.class)
@Import(AdminKeyGuard.class)
@TestPropertySource(properties = "app.admin-api-key=test-key")
class AdminMediaControllerTest {

    @Autowired MockMvc mvc;
    @MockBean MediaAssetService mediaService;
    // AdminKeyGuard 建構子新增依賴（Task 7）：@WebMvcTest slice 需補上這兩個 bean，context 才能建構真實 AdminKeyGuard
    @MockBean AdminSessionAccess sessionAccess;
    @MockBean AdminAllowlist allowlist;

    /** 沒有管理金鑰不得列出媒體庫。 */
    @Test
    void listWithoutAdminKeyReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/admin/media"))
            .andExpect(status().isUnauthorized());
    }

    /** 有效金鑰可取得安全 DTO，不暴露 object key 與內容 hash。 */
    @Test
    void listWithAdminKeyReturnsMediaViews() throws Exception {
        when(mediaService.list(100)).thenReturn(List.of(new MediaAssetService.MediaView(
            9L, "IMAGE", "image/png", 123L, "cover.png", 1200, 630,
            "2026-07-28T00:00:00Z", "https://media.example.com/newsletter-media/images/x.png")));

        mvc.perform(get("/api/admin/media").header("X-Admin-Key", "test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(9))
            .andExpect(jsonPath("$[0].url").value(
                "https://media.example.com/newsletter-media/images/x.png"))
            .andExpect(jsonPath("$[0].objectKey").doesNotExist())
            .andExpect(jsonPath("$[0].sha256").doesNotExist());
    }

    /** multipart 上傳會把檔案交給媒體服務。 */
    @Test
    void uploadWithAdminKeyReturnsCreatedMedia() throws Exception {
        when(mediaService.upload(any())).thenReturn(new MediaAssetService.MediaView(
            10L, "FILE", "application/pdf", 20L, "guide.pdf", null, null,
            "2026-07-28T00:00:00Z", "https://media.example.com/newsletter-media/files/x.pdf"));
        MockMultipartFile file = new MockMultipartFile(
            "file", "guide.pdf", "application/pdf", "%PDF-1.7".getBytes());

        mvc.perform(multipart("/api/admin/media").file(file).header("X-Admin-Key", "test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kind").value("FILE"))
            .andExpect(jsonPath("$.originalName").value("guide.pdf"));
    }
}
