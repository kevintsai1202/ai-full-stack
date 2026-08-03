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
        when(mailRenderer.body(any(CouponCampaign.class), anyString(), anyString())).thenReturn("<html/>");
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
}
