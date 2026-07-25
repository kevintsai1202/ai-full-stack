package world.springai.survey.newsletter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import world.springai.survey.AdminKeyGuard;
import world.springai.survey.audience.RecipientService;
import world.springai.survey.mail.MailQuotaService;
import world.springai.survey.mail.MailTemplate;

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
    @MockBean InviteService inviteService;
    @MockBean MailQuotaService mailQuotaService;

    /** 測試用額度：月額度 50000、剩餘 49000、單批上限 500（模擬 Zeabur Pro 偵測結果） */
    private static final MailQuotaService.Quota QUOTA = new MailQuotaService.Quota(
        "zeabur", "healthy",
        999999999L, 0L, 999999999L,
        50000L, 1000L, 49000L,
        49000L, 500L,
        true, "2026-07-26T00:00:00Z", "2026-07-28T16:18:35Z");

    /** 每個測試都先給定額度偵測結果（控制器的 limit 收斂會用到） */
    @org.junit.jupiter.api.BeforeEach
    void stubQuota() {
        when(mailQuotaService.current()).thenReturn(QUOTA);
    }

    /** 邀請確認信：無金鑰回 401 */
    @Test
    void inviteWithoutKeyReturns401() throws Exception {
        mvc.perform(post("/api/admin/campaign/invite")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\":\"exam\"}"))
           .andExpect(status().isUnauthorized());
    }

    /** 邀請確認信：有金鑰時委派 InviteService（含 limit）並回寄送摘要 */
    @Test
    void inviteWithKeyDelegatesAndReturnsSummary() throws Exception {
        when(inviteService.sendInvites("exam", 100))
            .thenReturn(new InviteService.InviteResult(3, 2, 1, 5, 10));
        mvc.perform(post("/api/admin/campaign/invite").header("X-Admin-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\":\"exam\",\"limit\":100}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.recipientCount").value(3))
           .andExpect(jsonPath("$.accepted").value(2))
           .andExpect(jsonPath("$.failed").value(1))
           .andExpect(jsonPath("$.alreadyInvited").value(5))
           .andExpect(jsonPath("$.remaining").value(10));
    }

    /** 額度查詢：回傳偵測到的日／月額度與單批上限 */
    @Test
    void mailQuotaReturnsDetectedQuota() throws Exception {
        mvc.perform(get("/api/admin/mail-quota").header("X-Admin-Key", "test-key"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.source").value("zeabur"))
           .andExpect(jsonPath("$.monthlyQuota").value(50000))
           .andExpect(jsonPath("$.remaining").value(49000))
           .andExpect(jsonPath("$.batchMax").value(500));
    }

    /** 額度查詢：無金鑰回 401 */
    @Test
    void mailQuotaWithoutKeyReturns401() throws Exception {
        mvc.perform(get("/api/admin/mail-quota"))
           .andExpect(status().isUnauthorized());
    }

    /** 邀請確認信：limit 超過單批上限時收斂成 batchMax，避免逐封同步寄送逾時 */
    @Test
    void inviteLimitIsClampedToBatchMax() throws Exception {
        when(inviteService.sendInvites("exam", 500))
            .thenReturn(new InviteService.InviteResult(500, 500, 0, 0, 200));
        mvc.perform(post("/api/admin/campaign/invite").header("X-Admin-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\":\"exam\",\"limit\":9999}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.recipientCount").value(500));
    }

    /** 邀請確認信：未給 limit 時同樣收斂成 batchMax（不放行全部名單） */
    @Test
    void inviteWithoutLimitFallsBackToBatchMax() throws Exception {
        when(inviteService.sendInvites("exam", 500))
            .thenReturn(new InviteService.InviteResult(500, 500, 0, 0, 0));
        mvc.perform(post("/api/admin/campaign/invite").header("X-Admin-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\":\"exam\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.recipientCount").value(500));
    }

    /** 邀請確認信：缺 source 回 400 */
    @Test
    void inviteWithoutSourceReturns400() throws Exception {
        mvc.perform(post("/api/admin/campaign/invite").header("X-Admin-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
           .andExpect(status().isBadRequest());
    }

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
