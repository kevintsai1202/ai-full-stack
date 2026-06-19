package world.springai.survey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** AdminCampaignController：金鑰守衛 + 收件數 + 預覽 + 發送 */
@WebMvcTest(AdminCampaignController.class)
@Import(AdminKeyGuard.class)
@TestPropertySource(properties = {"app.admin-api-key=test-key"})
class AdminCampaignControllerTest {

    @Autowired MockMvc mvc;
    @MockBean CampaignService campaignService;
    @MockBean RecipientService recipientService;

    /** 無金鑰一律 401 */
    @Test
    void recipientsWithoutKeyReturns401() throws Exception {
        mvc.perform(get("/api/admin/recipients"))
           .andExpect(status().isUnauthorized());
    }

    /** 有金鑰：回收件數與樣本 */
    @Test
    void recipientsWithKeyReturnsCount() throws Exception {
        when(recipientService.recipients(null, null)).thenReturn(List.of("a@x.com", "b@x.com"));
        mvc.perform(get("/api/admin/recipients").header("X-Admin-Key", "test-key"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.count").value(2));
    }

    /** 預覽：回渲染後 HTML */
    @Test
    void previewReturnsHtml() throws Exception {
        when(campaignService.preview(eq("主旨"), eq("# 內文"))).thenReturn("<div>內文</div>");
        mvc.perform(post("/api/admin/campaign/preview").header("X-Admin-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"主旨\",\"markdown\":\"# 內文\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.html").value("<div>內文</div>"));
    }

    /** 發送：立即模式回摘要 */
    @Test
    void sendNowReturnsSummary() throws Exception {
        when(campaignService.send(eq("主旨"), eq("內文"), any(), any(), eq("now"), any()))
            .thenReturn(new CampaignService.SendResult(7L, 3, 3, 0));
        mvc.perform(post("/api/admin/campaign/send").header("X-Admin-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"主旨\",\"markdown\":\"內文\",\"mode\":\"now\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.campaignId").value(7))
           .andExpect(jsonPath("$.accepted").value(3));
    }
}
