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
        50L, 48950L, 500L,
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

    /**
     * 額度吃緊時，邀請信的單次上限必須收斂到<b>扣掉交易信保留額度</b>之後的量。
     *
     * <p><b>失效情境</b>：ZSend 月剩餘 300 封、reserve 50。若這裡用的是
     * {@code batchMax()}（= min(remaining, 500) = 300，未扣 reserve），
     * 管理者按一次「寄邀請信」就會逐封寄出 300 封把額度歸零；此後任何讀者點
     * magic link 都收不到登入信——整個讀者端登不進去。這不是「信晚一點到」，
     * 是 spec §6 要防的產品級故障。邀請信是站方主動外推的再徵詢，讀者不在等它，
     * 該讓位給交易信。</p>
     *
     * <p>邀請信與提醒信走 {@link InviteService}，本身<b>沒有任何額度判斷</b>
     * （{@code CampaignService} 內的 reserve 檢查管不到這條路徑），
     * 所以這個 clamp 是這條路徑上唯一的防線。把 {@code clampLimit} 改回
     * {@code batchMax()}，本測試與下一個測試都會變紅。</p>
     */
    @Test
    void inviteLimitReservesQuotaForTransactionalMail() throws Exception {
        // 月剩餘 300、reserve 50 → 行銷可用 250，單批上限 250
        when(mailQuotaService.current()).thenReturn(new MailQuotaService.Quota(
            "zeabur", "healthy",
            999999999L, 0L, 999999999L,
            50000L, 49700L, 300L,
            300L, 300L,
            50L, 250L, 250L,
            true, null, null));
        when(inviteService.sendInvites("exam", 250))
            .thenReturn(new InviteService.InviteResult(250, 250, 0, 0, 50));

        mvc.perform(post("/api/admin/campaign/invite").header("X-Admin-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\":\"exam\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.recipientCount").value(250));

        // 若用 batchMax（300）就會是這個呼叫——它必須從未發生
        org.mockito.Mockito.verify(inviteService, org.mockito.Mockito.never())
            .sendInvites("exam", 300);
    }

    /** 補送提醒信走的是同一個 clamp，保護不得只在首次邀請那條路徑上 */
    @Test
    void reminderLimitAlsoReservesQuotaForTransactionalMail() throws Exception {
        when(mailQuotaService.current()).thenReturn(new MailQuotaService.Quota(
            "zeabur", "healthy",
            999999999L, 0L, 999999999L,
            50000L, 49700L, 300L,
            300L, 300L,
            50L, 250L, 250L,
            true, null, null));
        when(inviteService.sendReminders("exam", 250))
            .thenReturn(new InviteService.ReminderResult(250, 250, 0, 0, 0, 50));

        mvc.perform(post("/api/admin/campaign/invite/remind").header("X-Admin-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\":\"exam\",\"limit\":9999}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.recipientCount").value(250));

        org.mockito.Mockito.verify(inviteService, org.mockito.Mockito.never())
            .sendReminders("exam", 300);
    }

    /**
     * 行銷可用量已被 reserve 吃光時整批拒絕（409），且<b>一封都不能寄出</b>。
     *
     * <p>額度只剩 40 封、reserve 50 → 這 40 封全部保留給登入信。</p>
     *
     * <p><b>為什麼不能「clamp 成 0 再照常呼叫」</b>：{@link InviteService} 把
     * {@code limit <= 0} 解讀為「不限」，傳 0 下去的效果是整份名單全寄——
     * 與意圖完全相反，而且正好發生在額度最吃緊的時候。這條測試同時釘住
     * 「回 409」與「完全沒有呼叫 InviteService」兩件事。</p>
     */
    @Test
    void inviteIsRejectedWhenOnlyReserveRemains() throws Exception {
        when(mailQuotaService.current()).thenReturn(new MailQuotaService.Quota(
            "zeabur", "healthy",
            999999999L, 0L, 999999999L,
            50000L, 49960L, 40L,
            40L, 40L,
            50L, 0L, 0L,
            true, null, null));

        mvc.perform(post("/api/admin/campaign/invite").header("X-Admin-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\":\"exam\",\"limit\":100}"))
           .andExpect(status().isConflict());

        org.mockito.Mockito.verify(inviteService, org.mockito.Mockito.never())
            .sendInvites(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
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

    /** 發送：立即模式回摘要（控制器實際委派至含發布欄位的 10 參數 send 版本） */
    @Test
    void sendNowReturnsSummary() throws Exception {
        when(campaignService.send(eq("主旨"), eq("內文"), any(), any(), eq("now"), any(),
                any(), any(), any(), any()))
            .thenReturn(new CampaignService.SendResult(7L, 3, 3, 0, 0));
        mvc.perform(post("/api/admin/campaign/send").header("X-Admin-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"主旨\",\"markdown\":\"內文\",\"mode\":\"now\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.campaignId").value(7))
           .andExpect(jsonPath("$.accepted").value(3));
    }

    /** 發送：可一併帶入發布欄位（tier/creditCost/slug/publishedAt），控制器原樣轉交 CampaignService */
    @Test
    void sendWithPublishFieldsDelegatesAllFields() throws Exception {
        when(campaignService.send(eq("主旨"), eq("內文"), any(), any(), eq("now"), any(),
                eq("PREMIUM"), eq(10), eq("hello-world"), any()))
            .thenReturn(new CampaignService.SendResult(8L, 1, 1, 0, 0));
        mvc.perform(post("/api/admin/campaign/send").header("X-Admin-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"主旨\",\"markdown\":\"內文\",\"mode\":\"now\","
                    + "\"tier\":\"PREMIUM\",\"creditCost\":10,\"slug\":\"hello-world\","
                    + "\"publishedAt\":\"2026-07-25T04:00:00Z\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.campaignId").value(8));
    }

    /**
     * 只發布不寄送：<b>無金鑰一律 401</b>，且完全不呼叫 CampaignService。
     *
     * <p>這條端點能建立 PREMIUM 文章、決定它的價格與是否發布，權限等級與寄送端點相同。
     * 把 {@code guard.verify(key)} 拿掉，本測試立刻變紅。</p>
     */
    @Test
    void publishWithoutKeyReturns401() throws Exception {
        mvc.perform(post("/api/admin/campaign/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"主旨\",\"markdown\":\"內文\",\"slug\":\"a-post\"}"))
           .andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(campaignService);
    }

    /** 只發布不寄送：金鑰錯誤同樣 401（固定時間比對由 AdminKeyGuard 負責） */
    @Test
    void publishWithWrongKeyReturns401() throws Exception {
        mvc.perform(post("/api/admin/campaign/publish").header("X-Admin-Key", "wrong-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"主旨\",\"markdown\":\"內文\",\"slug\":\"a-post\"}"))
           .andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(campaignService);
    }

    /** 只發布不寄送：原樣轉交欄位並回傳文章公開網址（讓管理者能直接點開驗證） */
    @Test
    void publishDelegatesAndReturnsPublicUrl() throws Exception {
        when(campaignService.publish(eq("主旨"), eq("內文"), eq("PREMIUM"), eq(10),
                eq("premium-post"), any()))
            .thenReturn(new CampaignService.PublishResult(9L, "premium-post", "PREMIUM", 10,
                java.time.OffsetDateTime.parse("2026-07-25T04:00:00Z"),
                "https://news.example.com/r/news/premium-post"));

        mvc.perform(post("/api/admin/campaign/publish").header("X-Admin-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"主旨\",\"markdown\":\"內文\",\"tier\":\"PREMIUM\","
                    + "\"creditCost\":10,\"slug\":\"premium-post\","
                    + "\"publishedAt\":\"2026-07-25T04:00:00Z\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.campaignId").value(9))
           .andExpect(jsonPath("$.slug").value("premium-post"))
           .andExpect(jsonPath("$.tier").value("PREMIUM"))
           .andExpect(jsonPath("$.creditCost").value(10))
           .andExpect(jsonPath("$.url").value("https://news.example.com/r/news/premium-post"));
    }

    /** 只發布不寄送：publishedAt 格式錯誤回 400，且不呼叫 CampaignService */
    @Test
    void publishWithInvalidPublishedAtReturns400() throws Exception {
        mvc.perform(post("/api/admin/campaign/publish").header("X-Admin-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"主旨\",\"markdown\":\"內文\",\"slug\":\"a-post\","
                    + "\"publishedAt\":\"not-a-date\"}"))
           .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(campaignService);
    }

    /**
     * ★ 下架：<b>無金鑰一律 401</b>，且完全不呼叫 CampaignService。
     *
     * <p>下架會讓文章從 /r/archive 與單篇頁消失，權限等級與發布相同。
     * 把 {@code guard.verify(key)} 拿掉，本測試（與下一條錯誤金鑰）立刻變紅。</p>
     */
    @Test
    void unpublishWithoutKeyReturns401() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/admin/campaigns/9/publication"))
           .andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(campaignService);
    }

    /** 下架：金鑰錯誤同樣 401（固定時間比對由 AdminKeyGuard 負責） */
    @Test
    void unpublishWithWrongKeyReturns401() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/admin/campaigns/9/publication").header("X-Admin-Key", "wrong-key"))
           .andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(campaignService);
    }

    /** 下架：有金鑰時委派 CampaignService.unpublish 並回傳被下架的批次與 slug */
    @Test
    void unpublishDelegatesToService() throws Exception {
        when(campaignService.unpublish(9L))
            .thenReturn(new CampaignService.UnpublishResult(9L, "premium-post"));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/admin/campaigns/9/publication").header("X-Admin-Key", "test-key"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.campaignId").value(9))
           .andExpect(jsonPath("$.slug").value("premium-post"));
    }

    /**
     * ★ 重新上架：<b>無金鑰一律 401</b>，且完全不呼叫 CampaignService。
     *
     * <p>它會讓文章重新出現在 /r/archive 與單篇頁，權限等級與發布／下架相同。
     * 每個後台端點都必須有自己的未授權測試——漏一個就是那一個功能對外全開。
     * 把 {@code guard.verify(key)} 拿掉，本測試（與下一條錯誤金鑰）立刻變紅。</p>
     */
    @Test
    void republishWithoutKeyReturns401() throws Exception {
        mvc.perform(post("/api/admin/campaigns/9/publication"))
           .andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(campaignService);
    }

    /** 重新上架：金鑰錯誤同樣 401（固定時間比對由 AdminKeyGuard 負責） */
    @Test
    void republishWithWrongKeyReturns401() throws Exception {
        mvc.perform(post("/api/admin/campaigns/9/publication").header("X-Admin-Key", "wrong-key"))
           .andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(campaignService);
    }

    /**
     * 重新上架：有金鑰時委派 CampaignService.republish，回傳新的發布時間與公開網址。
     *
     * <p>回應帶 url 是刻意的：管理者按完按鈕就能直接點開，確認文章真的回來了、
     * 而且 paywall 仍如預期（與 publish 端點同一個理由）。</p>
     */
    @Test
    void republishDelegatesToService() throws Exception {
        when(campaignService.republish(9L)).thenReturn(new CampaignService.RepublishResult(
            9L, "premium-post", java.time.OffsetDateTime.parse("2026-07-26T04:00:00Z"),
            "https://news.example.com/r/news/premium-post"));

        mvc.perform(post("/api/admin/campaigns/9/publication").header("X-Admin-Key", "test-key"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.campaignId").value(9))
           .andExpect(jsonPath("$.slug").value("premium-post"))
           .andExpect(jsonPath("$.url").value("https://news.example.com/r/news/premium-post"));
    }

    /**
     * 重新上架端點<b>不接受任何請求本文欄位</b>：它只能把文章放回去。
     *
     * <p>帶著一份「想順手改內容與價格」的 JSON 進來時，那些欄位必須被完全忽略
     * ——service 收到的只有 id。允許改欄位會讓這條端點變成一條可改任意欄位的後門，
     * 而那會讓「已解鎖的讀者付的價格」與「文章現在的價格」永久對不起來。</p>
     */
    @Test
    void republishIgnoresAnyRequestBody() throws Exception {
        when(campaignService.republish(9L)).thenReturn(new CampaignService.RepublishResult(
            9L, "premium-post", java.time.OffsetDateTime.parse("2026-07-26T04:00:00Z"),
            "https://news.example.com/r/news/premium-post"));

        mvc.perform(post("/api/admin/campaigns/9/publication").header("X-Admin-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"creditCost\":1,\"markdown\":\"竄改\",\"publishedAt\":\"2020-01-01T00:00:00Z\"}"))
           .andExpect(status().isOk());

        // 只有 id 被傳下去，沒有任何其他多載或欄位
        org.mockito.Mockito.verify(campaignService).republish(9L);
        org.mockito.Mockito.verifyNoMoreInteractions(campaignService);
    }

    /**
     * ★ 後台 400 的<b>原因</b>必須出現在回應本文裡（{@code ProblemDetail.detail}）。
     *
     * <p><b>為什麼要單獨釘住</b>：Spring Boot 預設 {@code server.error.include-message=never}，
     * reason 根本不會送出來，前端只能顯示「HTTP 400」，管理者無從得知該改什麼——
     * 「slug 已被使用」「加點說明請縮短至 200 字以內」「publishedAt 格式錯誤」
     * 全部變成沒有原因的失敗。{@code ApiExceptionHandler} 是為此加的，
     * 但在本次改動前<b>沒有任何測試斷言過那個 detail 真的到得了客戶端</b>——
     * 只有斷言狀態碼的測試，把 advice 整個刪掉也不會變紅。</p>
     *
     * <p>這條同時是「讀者頁改回 HTML 404」那項改動的護欄：那個修法刻意<b>不動</b>
     * advice 的範圍，本測試確保後台這一端不會被順手改壞。</p>
     */
    @Test
    void adminBadRequestReasonReachesResponseBody() throws Exception {
        mvc.perform(post("/api/admin/campaign/publish").header("X-Admin-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"主旨\",\"markdown\":\"內文\",\"slug\":\"a-post\","
                    + "\"publishedAt\":\"not-a-date\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
               .content().contentTypeCompatibleWith("application/problem+json"))
           .andExpect(jsonPath("$.detail").value("publishedAt 格式錯誤"));
    }

    /** 發送：publishedAt 格式錯誤回 400，且不呼叫 CampaignService */
    @Test
    void sendWithInvalidPublishedAtReturns400() throws Exception {
        mvc.perform(post("/api/admin/campaign/send").header("X-Admin-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"主旨\",\"markdown\":\"內文\",\"mode\":\"now\",\"publishedAt\":\"not-a-date\"}"))
           .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(campaignService);
    }
}
