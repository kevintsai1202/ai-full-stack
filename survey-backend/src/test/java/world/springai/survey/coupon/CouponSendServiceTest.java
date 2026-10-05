package world.springai.survey.coupon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import world.springai.survey.audience.SubscriptionLinkBuilder;
import world.springai.survey.form.FormSchemaService;
import world.springai.survey.mail.CouponMailRenderer;
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.MailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link CouponSendService} 行為測試：寄送迴圈語意逐字比照
 * {@code world.springai.survey.newsletter.InviteService#sendInvites}
 * ——子集驗證、已寄冪等跳過、額度截斷、單封容錯、活動狀態與累計。
 * 純 Mockito 單元測試，全依賴皆為 mock。
 */
class CouponSendServiceTest {

    private CouponCampaignRepository campaignRepository;
    private CouponRecipientService couponRecipientService;
    private CouponMailRenderer mailRenderer;
    private MailSender mailSender;
    private EmailLogRepository emailLogRepository;
    private SubscriptionLinkBuilder linkBuilder;
    private FormSchemaService formSchemaService;
    private CouponSendService service;

    @BeforeEach
    void setUp() {
        campaignRepository = mock(CouponCampaignRepository.class);
        couponRecipientService = mock(CouponRecipientService.class);
        mailRenderer = mock(CouponMailRenderer.class);
        mailSender = mock(MailSender.class);
        emailLogRepository = mock(EmailLogRepository.class);
        linkBuilder = mock(SubscriptionLinkBuilder.class);
        formSchemaService = mock(FormSchemaService.class);

        // 預設：子集驗證全部合法、無已寄記錄、無表單版本（fallback 用 formKey 原字串）
        when(couponRecipientService.findIllegal(any(), any())).thenReturn(List.of());
        when(emailLogRepository.findByTypeAndStatus(anyString(), anyString())).thenReturn(List.of());
        when(formSchemaService.listDefinitions()).thenReturn(List.of());
        when(mailRenderer.subject(any(CouponCampaign.class))).thenReturn("《AI 全端開發》讀者專屬優惠");
        // 第二參數（寄送原因的問卷標題）single 模式會傳 null，用 any() 才匹配得到
        when(mailRenderer.body(any(CouponCampaign.class), any(), anyString())).thenReturn("<html/>");
        // 逐 email 不同值，驗證每人拿到專屬退訂連結
        when(linkBuilder.unsubscribeLink(anyString()))
            .thenAnswer(i -> "UNSUB_LINK_FOR:" + i.getArgument(0));

        service = new CouponSendService(campaignRepository, couponRecipientService, mailRenderer,
            mailSender, emailLogRepository, linkBuilder, formSchemaService);
    }

    /** 建一筆測試用優惠券活動（狀態、寄送統計由測試逐項覆寫觀察） */
    private CouponCampaign campaign() {
        return new CouponCampaign("AI 全端開發", "推薦文案", "https://hahow.in/cr/x",
            "SAVE300", LocalDate.of(2026, 9, 30), "reader-poll", "{}");
    }

    /** 反射補上 id，讓 campaign.getId() 有值可供 email_log type=coupon:{id} 判定 */
    private CouponCampaign withId(CouponCampaign campaign, long id) {
        org.springframework.test.util.ReflectionTestUtils.setField(campaign, "id", id);
        return campaign;
    }

    /** 子集驗證不過（名單含 findIllegal 判定的違規 email）應回 400，訊息列出違規 email，且不觸發任何寄信 */
    @Test
    void 子集驗證失敗回400含違規email() {
        CouponCampaign c = withId(campaign(), 9L);
        when(campaignRepository.findById(9L)).thenReturn(Optional.of(c));
        when(couponRecipientService.findIllegal(eq(c), any())).thenReturn(List.of("evil@x.com"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.send(9L, List.of("evil@x.com"), null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("evil@x.com"));
        verifyNoInteractions(mailSender);
    }

    /** 已寄過的收件人（email_log type=coupon:9 status=sent）自動跳過並計入 skipped，只寄未寄過的對象 */
    @Test
    void 已寄過自動跳過並計入skipped() {
        CouponCampaign c = withId(campaign(), 9L);
        when(campaignRepository.findById(9L)).thenReturn(Optional.of(c));
        when(emailLogRepository.findByTypeAndStatus("coupon:9", "sent"))
            .thenReturn(List.of(new EmailLog("a@x.com", "s", "coupon:9", null, "sent", null)));
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        CouponSendService.SendResult result = service.send(9L, List.of("a@x.com", "b@x.com"), null);

        assertEquals(1, result.attempted());
        assertEquals(1, result.sent());
        assertEquals(1, result.skipped());
        assertEquals(0, result.failed());
        verify(mailSender).send(eq("b@x.com"), anyString(), anyString());
        verify(mailSender, never()).send(eq("a@x.com"), anyString(), anyString());
    }

    /** limit 有值時截斷本次寄送數，其餘計入 remaining，attempted 只反映實際嘗試數 */
    @Test
    void limit截斷計入remaining() {
        CouponCampaign c = withId(campaign(), 9L);
        when(campaignRepository.findById(9L)).thenReturn(Optional.of(c));
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        CouponSendService.SendResult result =
            service.send(9L, List.of("a@x.com", "b@x.com", "c@x.com"), 2);

        assertEquals(2, result.attempted());
        assertEquals(2, result.sent());
        assertEquals(1, result.remaining());
    }

    /** 單封寄送失敗不中斷整批：該封計入 failed 並記錄 email_log status=failed＋錯誤訊息，後續收件人照常寄出 */
    @Test
    void 單封失敗不中斷且記failed() {
        CouponCampaign c = withId(campaign(), 9L);
        when(campaignRepository.findById(9L)).thenReturn(Optional.of(c));
        when(mailSender.send(eq("a@x.com"), anyString(), anyString()))
            .thenThrow(new RuntimeException("寄送失敗：逾時"));
        when(mailSender.send(eq("b@x.com"), anyString(), anyString())).thenReturn("msg-2");

        CouponSendService.SendResult result = service.send(9L, List.of("a@x.com", "b@x.com"), null);

        assertEquals(1, result.failed());
        assertEquals(1, result.sent());
        verify(mailSender).send(eq("b@x.com"), anyString(), anyString());

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository, times(2)).save(captor.capture());
        EmailLog failedLog = captor.getAllValues().stream()
            .filter(log -> "failed".equals(log.getStatus())).findFirst().orElseThrow();
        assertEquals("a@x.com", failedLog.getRecipient());
        assertEquals("寄送失敗：逾時", failedLog.getError());
        EmailLog sentLog = captor.getAllValues().stream()
            .filter(log -> "sent".equals(log.getStatus())).findFirst().orElseThrow();
        assertEquals("b@x.com", sentLog.getRecipient());
        assertEquals("msg-2", sentLog.getProviderMessageId());
    }

    /** 首次成功寄送後活動應轉為 SENT、記錄首次寄送時間、且累計 sent_count（以 getSentCount()+本次 sent 累加） */
    @Test
    void 首次寄送後活動標SENT並累計sent_count() {
        CouponCampaign c = withId(campaign(), 9L);
        when(campaignRepository.findById(9L)).thenReturn(Optional.of(c));
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        service.send(9L, List.of("a@x.com"), null);

        assertEquals(CouponCampaign.STATUS_SENT, c.getStatus());
        assertNotNull(c.getSentAt());
        assertEquals(1, c.getSentCount());
        verify(campaignRepository).save(c);
    }

    /**
     * 同批次內同一 email（含大小寫與頭尾空白差異）只應視為一人：只實際寄一封，
     * attempted／sent 皆計 1，不因批內重複而重複寄送或重複累計 sent_count。
     */
    @Test
    void 同批重複email正規化後只寄一封() {
        CouponCampaign c = withId(campaign(), 9L);
        when(campaignRepository.findById(9L)).thenReturn(Optional.of(c));
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        CouponSendService.SendResult result =
            service.send(9L, List.of("a@x.com", "A@X.com", " a@x.com "), null);

        assertEquals(1, result.attempted());
        assertEquals(1, result.sent());
        assertEquals(1, c.getSentCount());
        verify(mailSender, times(1)).send(anyString(), anyString(), anyString());
    }

    /**
     * 名單為空（emails 為空清單）應回 400，且不觸發任何寄信；與「全部已寄過」分開單獨釘住，
     * 避免日後重構把「空清單」與「全部已寄」合併成同一分支時，其中一種情境被誤刪或改錯訊息。
     */
    @Test
    void 名單為空拋400() {
        CouponCampaign c = withId(campaign(), 9L);
        when(campaignRepository.findById(9L)).thenReturn(Optional.of(c));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.send(9L, List.of(), null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(mailSender);
    }

    /** 名單全部已寄過（去除已寄後無任何合法可寄對象）應回 400，且不觸發任何寄信 */
    @Test
    void 名單全部已寄拋400() {
        CouponCampaign c = withId(campaign(), 9L);
        when(campaignRepository.findById(9L)).thenReturn(Optional.of(c));
        when(emailLogRepository.findByTypeAndStatus("coupon:9", "sent"))
            .thenReturn(List.of(new EmailLog("a@x.com", "s", "coupon:9", null, "sent", null)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.send(9L, List.of("a@x.com"), null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(mailSender);
    }

    /** D6：單筆寄送成功後 status 維持 DRAFT、sent_count 累加、sent_at 記錄本次時間 */
    @Test
    void singleSendKeepsDraftStatus() {
        CouponCampaign campaign = withId(campaign(), 1L);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        CouponSendService.SendResult result = service.send(1L, List.of("one@example.com"), 1, true);

        assertEquals(1, result.sent());
        assertEquals(CouponCampaign.STATUS_DRAFT, campaign.getStatus());   // 不翻 SENT
        assertNotNull(campaign.getSentAt());
        assertEquals(1, campaign.getSentCount());
    }

    /** D6 對照組：批次寄送（single=false）行為不變——status 翻 SENT、sentAt 首次寫入後不覆蓋 */
    @Test
    void batchSendStillMarksSent() {
        CouponCampaign campaign = withId(campaign(), 1L);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        CouponSendService.SendResult result = service.send(1L, List.of("one@example.com"), 1, false);

        assertEquals(CouponCampaign.STATUS_SENT, campaign.getStatus());
    }

    /** 單筆重複寄送：同一人同一張券第二次呼叫被已寄過濾擋下（400），email_log 不會多一列 sent */
    @Test
    void singleResendIsRejectedByIdempotencyFilter() {
        CouponCampaign campaign = withId(campaign(), 1L);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(emailLogRepository.findByTypeAndStatus("coupon:1", "sent"))
            .thenReturn(List.of(new EmailLog("one@example.com", "s", "coupon:1", "id", "sent", null)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.send(1L, List.of("one@example.com"), 1, true));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(mailSender, never()).send(any(), any(), any());
    }

    /**
     * 單筆寄送（問卷分析逐列寄券）不驗券的命中名單：收件人不必填過該券的名單來源問卷，
     * 改由訂閱資格判斷放行。findIllegal 一次都不能被呼叫——否則不同問卷的讀者仍會被 400 擋下。
     */
    @Test
    void single模式不驗命中名單改驗訂閱資格() {
        CouponCampaign campaign = withId(campaign(), 1L);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        // 命中名單驗證若被呼叫會判違規，用來抓「single 仍走 findIllegal」的回歸
        when(couponRecipientService.findIllegal(any(), any())).thenReturn(List.of("one@example.com"));
        when(couponRecipientService.findSingleSendRejection("one@example.com")).thenReturn(Optional.empty());
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        CouponSendService.SendResult result = service.send(1L, List.of("one@example.com"), 1, true);

        assertEquals(1, result.sent());
        verify(couponRecipientService).findSingleSendRejection("one@example.com");
        verify(couponRecipientService, never()).findIllegal(any(), any());
    }

    /** 單筆寄送資格不符（已退訂／未同意）回 400，訊息帶出原因，且不觸發任何寄信 */
    @Test
    void single模式資格不符回400且不寄信() {
        CouponCampaign campaign = withId(campaign(), 1L);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(couponRecipientService.findSingleSendRejection("gone@example.com"))
            .thenReturn(Optional.of("收件人已退訂，不寄送行銷信：gone@example.com"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.send(1L, List.of("gone@example.com"), 1, true));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("已退訂"));
        verifyNoInteractions(mailSender);
    }

    /**
     * 單筆寄送必須剛好一位收件人：single 跳過了命中名單驗證，若允許多人，
     * 就能拿 single=true 一次寄給整批人、繞過批次寄送的名單限制。
     */
    @Test
    void single模式收件人不是剛好一位回400() {
        CouponCampaign campaign = withId(campaign(), 1L);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));

        for (List<String> emails : List.of(
                List.of("a@example.com", "b@example.com"),
                List.<String>of(),
                java.util.Collections.<String>singletonList(null),
                List.of("   "))) {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.send(1L, emails, 1, true), "收件人 " + emails + " 應回 400");
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }
        verifyNoInteractions(mailSender);
        verify(couponRecipientService, never()).findSingleSendRejection(any());
    }

    /** 同一人大小寫不同的兩筆在批內去重後只算一位，single 模式照常寄出 */
    @Test
    void single模式同一人大小寫重複視為一位() {
        CouponCampaign campaign = withId(campaign(), 1L);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(couponRecipientService.findSingleSendRejection(anyString())).thenReturn(Optional.empty());
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        CouponSendService.SendResult result =
            service.send(1L, List.of("One@Example.com", "one@example.com"), 1, true);

        assertEquals(1, result.sent());
    }

    /**
     * 單筆寄送的信件不帶寄送原因：收件人不一定填過券的名單來源問卷，
     * 傳 null 讓渲染器省略「你收到這封信是因為你填過問卷『…』」，也不必查問卷標題。
     */
    @Test
    void single模式信件不帶寄送原因() {
        CouponCampaign campaign = withId(campaign(), 1L);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        service.send(1L, List.of("one@example.com"), 1, true);

        verify(mailRenderer).body(eq(campaign), isNull(), eq("UNSUB_LINK_FOR:one@example.com"));
        verifyNoInteractions(formSchemaService);
    }

    /** 對照組：批次寄送的信件照舊帶寄送原因（查無問卷版本時退回 formKey 原字串） */
    @Test
    void 批次模式信件仍帶寄送原因() {
        CouponCampaign campaign = withId(campaign(), 1L);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        service.send(1L, List.of("one@example.com"), null, false);

        verify(mailRenderer).body(eq(campaign), eq("reader-poll"), anyString());
    }

    /** 對照組：批次寄送（single=false）仍走命中名單驗證，不使用單筆資格判斷 */
    @Test
    void 批次模式仍驗命中名單() {
        CouponCampaign campaign = withId(campaign(), 1L);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        service.send(1L, List.of("one@example.com"), null, false);

        verify(couponRecipientService).findIllegal(eq(campaign), any());
        verify(couponRecipientService, never()).findSingleSendRejection(any());
    }
}
