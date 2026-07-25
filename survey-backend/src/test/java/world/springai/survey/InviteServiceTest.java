package world.springai.survey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import world.springai.survey.audience.SurveyResponse;
import world.springai.survey.audience.SurveyResponseRepository;
import world.springai.survey.audience.UnsubscribeTokenService;
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.MailSender;
import world.springai.survey.mail.MailTemplate;
import world.springai.survey.mail.MailTemplateRepository;
import world.springai.survey.newsletter.InviteService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** InviteService 行為測試：對待確認名單寄邀請信（含 HMAC 確認連結）、記錄 email_log、統計成敗 */
class InviteServiceTest {

    private SurveyResponseRepository repository;
    private MailSender mailSender;
    private EmailLogRepository emailLogRepository;
    private MailTemplateRepository templateRepository;
    private UnsubscribeTokenService tokenService;
    private InviteService service;

    @BeforeEach
    void setUp() {
        repository = mock(SurveyResponseRepository.class);
        mailSender = mock(MailSender.class);
        emailLogRepository = mock(EmailLogRepository.class);
        templateRepository = mock(MailTemplateRepository.class);
        // 預設資料庫無範本 → 走內建預設內文
        when(templateRepository.findByTemplateKey("invite")).thenReturn(java.util.Optional.empty());
        tokenService = new UnsubscribeTokenService("test-secret");
        service = new InviteService(repository, mailSender, emailLogRepository,
            templateRepository, tokenService, "https://survey.example.com");
    }

    /** 建立一筆待確認名單資料 */
    private SurveyResponse pending(String email) {
        SurveyResponse r = new SurveyResponse();
        r.setEmail(email);
        r.setSource("exam");
        r.setConsent(false);
        return r;
    }

    /** 應對每位待確認者寄信，信中含該收件人的個人化 HMAC 確認連結 */
    @Test
    void sendsInviteWithPersonalConfirmLink() {
        when(repository.findBySourceAndConsentFalseAndUnsubscribedFalse("exam"))
            .thenReturn(List.of(pending("a@example.com"), pending("b@example.com")));
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        InviteService.InviteResult result = service.sendInvites("exam", null);

        assertEquals(2, result.recipientCount());
        assertEquals(2, result.accepted());
        assertEquals(0, result.failed());
        // 驗證 a@example.com 的信含其專屬確認連結（HMAC token）
        String expectedToken = tokenService.sign("a@example.com");
        verify(mailSender).send(eq("a@example.com"), anyString(),
            contains("/api/survey/confirm?email=a%40example.com&t=" + expectedToken));
    }

    /** 已寄過邀請（email_log type=invite status=sent）的人應跳過，不重複寄 */
    @Test
    void skipsAlreadyInvitedRecipients() {
        when(repository.findBySourceAndConsentFalseAndUnsubscribedFalse("exam"))
            .thenReturn(List.of(pending("a@example.com"), pending("b@example.com")));
        when(emailLogRepository.findByTypeAndStatus("invite", "sent"))
            .thenReturn(List.of(new EmailLog("A@example.com", "主旨", "invite", "msg-0", "sent", null)));
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        InviteService.InviteResult result = service.sendInvites("exam", null);

        assertEquals(1, result.recipientCount());
        assertEquals(1, result.accepted());
        assertEquals(1, result.alreadyInvited());
        verify(mailSender, org.mockito.Mockito.never()).send(eq("a@example.com"), anyString(), anyString());
        verify(mailSender).send(eq("b@example.com"), anyString(), anyString());
    }

    /** limit 應限制單次寄送數量，其餘計入 remaining 供分天寄送 */
    @Test
    void limitCapsSendCountAndReportsRemaining() {
        when(repository.findBySourceAndConsentFalseAndUnsubscribedFalse("exam"))
            .thenReturn(List.of(pending("a@example.com"), pending("b@example.com"), pending("c@example.com")));
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        InviteService.InviteResult result = service.sendInvites("exam", 2);

        assertEquals(2, result.recipientCount());
        assertEquals(2, result.accepted());
        assertEquals(1, result.remaining());
        verify(mailSender, org.mockito.Mockito.times(2)).send(anyString(), anyString(), anyString());
    }

    /** 每封信成功寄出後應寫入 type=invite、status=sent 的寄送記錄 */
    @Test
    void logsSentInvite() {
        when(repository.findBySourceAndConsentFalseAndUnsubscribedFalse("exam"))
            .thenReturn(List.of(pending("a@example.com")));
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-9");

        service.sendInvites("exam", null);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        assertEquals("invite", captor.getValue().getType());
        assertEquals("sent", captor.getValue().getStatus());
        assertEquals("msg-9", captor.getValue().getProviderMessageId());
    }

    /** 寄送失敗不中斷整批：計入 failed 並記錄 status=failed */
    @Test
    void failedSendIsCountedAndLogged() {
        when(repository.findBySourceAndConsentFalseAndUnsubscribedFalse("exam"))
            .thenReturn(List.of(pending("bad@example.com"), pending("ok@example.com")));
        when(mailSender.send(eq("bad@example.com"), anyString(), anyString()))
            .thenThrow(new RuntimeException("boom"));
        when(mailSender.send(eq("ok@example.com"), anyString(), anyString())).thenReturn("msg-2");

        InviteService.InviteResult result = service.sendInvites("exam", null);

        assertEquals(2, result.recipientCount());
        assertEquals(1, result.accepted());
        assertEquals(1, result.failed());
        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(l -> "failed".equals(l.getStatus())));
    }

    /** 邀請信內文以電子報為核心：技術討論、AI 新知、好康優惠三大好處都要提到 */
    @Test
    void inviteBodyMentionsBenefits() {
        when(repository.findBySourceAndConsentFalseAndUnsubscribedFalse("exam"))
            .thenReturn(List.of(pending("a@example.com")));
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        service.sendInvites("exam", null);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(anyString(), anyString(), html.capture());
        assertTrue(html.getValue().contains("深入的技術討論"), "應提到技術討論");
        assertTrue(html.getValue().contains("AI 新知"), "應提到 AI 新知與新技術");
        assertTrue(html.getValue().contains("優惠"), "應提到好康優惠");
    }

    /** 資料庫有範本時：主旨與內文採用範本，{{confirmLink}} 佔位符替換成個人化確認連結 */
    @Test
    void usesDbTemplateWhenPresent() {
        when(templateRepository.findByTemplateKey("invite")).thenReturn(java.util.Optional.of(
            new MailTemplate("invite", "自訂主旨", "<p>自訂內文 <a href=\"{{confirmLink}}\">確認</a></p>")));
        when(repository.findBySourceAndConsentFalseAndUnsubscribedFalse("exam"))
            .thenReturn(List.of(pending("a@example.com")));
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        service.sendInvites("exam", null);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(eq("a@example.com"), eq("自訂主旨"), html.capture());
        assertTrue(html.getValue().contains("自訂內文"), "應使用資料庫範本內文");
        assertTrue(html.getValue().contains(tokenService.sign("a@example.com")), "佔位符應替換為個人化確認連結");
        assertTrue(!html.getValue().contains("{{confirmLink}}"), "佔位符不得殘留");
    }

    /** 更新範本：合法內容存入資料庫；缺佔位符或空白內容應拒絕（400） */
    @Test
    void updateTemplateValidatesAndSaves() {
        when(templateRepository.save(any(MailTemplate.class))).thenAnswer(i -> i.getArgument(0));

        MailTemplate saved = service.updateTemplate("新主旨", "<p>hi {{confirmLink}}</p>");
        assertEquals("新主旨", saved.getSubject());

        // 缺 {{confirmLink}} 佔位符 → 拒絕
        org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.web.server.ResponseStatusException.class,
            () -> service.updateTemplate("主旨", "<p>沒有連結</p>"));
        // 空白主旨 → 拒絕
        org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.web.server.ResponseStatusException.class,
            () -> service.updateTemplate(" ", "<p>{{confirmLink}}</p>"));
    }

    /** 邀請信須讓收件人認得寄件人：自我介紹（凱文大叔）、提及基礎課程與線上測驗、說明正在準備電子報 */
    @Test
    void inviteBodyIntroducesSenderAndContext() {
        when(repository.findBySourceAndConsentFalseAndUnsubscribedFalse("exam"))
            .thenReturn(List.of(pending("a@example.com")));
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        service.sendInvites("exam", null);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(anyString(), anyString(), html.capture());
        assertTrue(html.getValue().contains("凱文大叔"), "應自我介紹是凱文大叔");
        assertTrue(html.getValue().contains("基礎課程"), "應提及上過基礎課程");
        assertTrue(html.getValue().contains("線上測驗"), "應提及參加過線上測驗");
        assertTrue(html.getValue().contains("電子報"), "應說明正在準備電子報");
    }

    /** 建立一筆指定時間的邀請寄送記錄（createdAt 由 DB 產生，測試用反射補值） */
    private EmailLog inviteLog(String email, String type, java.time.OffsetDateTime at) {
        EmailLog l = new EmailLog(email, "主旨", type, "m", "sent", null);
        org.springframework.test.util.ReflectionTestUtils.setField(l, "createdAt", at);
        return l;
    }

    /** 補送提醒：只寄給「已邀請滿 3 天且未確認、未被提醒過」者，記錄 type=invite_reminder */
    @Test
    void remindersTargetInvitedUnconfirmedAfterInterval() {
        java.time.OffsetDateTime old = java.time.OffsetDateTime.now().minusDays(4);
        java.time.OffsetDateTime fresh = java.time.OffsetDateTime.now().minusDays(1);
        // 待確認名單：a（滿3天）、b（未滿3天）、c（已提醒過）、d（從未邀請過）
        when(repository.findBySourceAndConsentFalseAndUnsubscribedFalse("exam"))
            .thenReturn(List.of(pending("a@x.com"), pending("b@x.com"), pending("c@x.com"), pending("d@x.com")));
        when(emailLogRepository.findByTypeAndStatus("invite", "sent")).thenReturn(List.of(
            inviteLog("a@x.com", "invite", old),
            inviteLog("b@x.com", "invite", fresh),
            inviteLog("c@x.com", "invite", old)));
        when(emailLogRepository.findByTypeAndStatus("invite_reminder", "sent")).thenReturn(List.of(
            inviteLog("c@x.com", "invite_reminder", old)));
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        InviteService.ReminderResult r = service.sendReminders("exam", null);

        assertEquals(1, r.recipientCount());   // 只有 a 符合
        assertEquals(1, r.accepted());
        assertEquals(1, r.tooRecent());        // b 未滿 3 天
        assertEquals(1, r.alreadyReminded());  // c 已提醒過（最多 1 次）
        verify(mailSender).send(eq("a@x.com"), anyString(), anyString());
        // 記錄為 invite_reminder，不影響首次邀請的跳過邏輯
        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        assertEquals("invite_reminder", captor.getValue().getType());
    }

    /** 補送提醒：limit 限制單次封數，其餘回報於 remaining */
    @Test
    void remindersRespectLimit() {
        java.time.OffsetDateTime old = java.time.OffsetDateTime.now().minusDays(5);
        when(repository.findBySourceAndConsentFalseAndUnsubscribedFalse("exam"))
            .thenReturn(List.of(pending("a@x.com"), pending("b@x.com"), pending("c@x.com")));
        when(emailLogRepository.findByTypeAndStatus("invite", "sent")).thenReturn(List.of(
            inviteLog("a@x.com", "invite", old), inviteLog("b@x.com", "invite", old), inviteLog("c@x.com", "invite", old)));
        when(emailLogRepository.findByTypeAndStatus("invite_reminder", "sent")).thenReturn(List.of());
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        InviteService.ReminderResult r = service.sendReminders("exam", 2);

        assertEquals(2, r.recipientCount());
        assertEquals(2, r.accepted());
        assertEquals(1, r.remaining());
    }

    /** 總覽統計：已寄邀請去重計數、剩餘待邀請扣掉已寄者、已確認取自 repository 計數 */
    @Test
    void overviewSummarizesInviteProgress() {
        // 邀請記錄：a 成功兩次（重跑）、b 失敗一次 → invitedCount 應去重為 1
        when(emailLogRepository.findByTypeOrderByCreatedAtDesc("invite")).thenReturn(List.of(
            new EmailLog("a@example.com", "主旨", "invite", "m1", "sent", null),
            new EmailLog("A@example.com", "主旨", "invite", "m2", "sent", null),
            new EmailLog("b@example.com", "主旨", "invite", null, "failed", "429")));
        // 待確認名單：a 已邀請過應排除、c 未邀請 → pendingCount=1
        when(repository.findBySourceAndConsentFalseAndUnsubscribedFalse("exam"))
            .thenReturn(List.of(pending("a@example.com"), pending("c@example.com")));
        when(repository.countBySourceAndConsentTrueAndUnsubscribedFalse("exam")).thenReturn(5L);

        // 補送提醒記錄：a 被提醒過一次 → remindedCount=1，記錄併入列表
        when(emailLogRepository.findByTypeOrderByCreatedAtDesc("invite_reminder")).thenReturn(List.of(
            new EmailLog("a@example.com", "主旨", "invite_reminder", "m3", "sent", null)));

        InviteService.InviteOverview o = service.overview("exam");

        assertEquals(1, o.invitedCount());   // a 去重（大小寫不敏感），b 失敗不計
        assertEquals(1, o.remindedCount());  // a 補送過一次
        assertEquals(1, o.pendingCount());   // 只剩 c
        assertEquals(5, o.confirmedCount());
        assertEquals(4, o.logs().size());    // 邀請 3 筆 + 提醒 1 筆合併列出
    }

    /** 邀請信不以課程宣傳為主軸：內文不得出現課程名稱推銷 */
    @Test
    void inviteBodyDoesNotPromoteCourse() {
        when(repository.findBySourceAndConsentFalseAndUnsubscribedFalse("exam"))
            .thenReturn(List.of(pending("a@example.com")));
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        service.sendInvites("exam", null);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(anyString(), anyString(), html.capture());
        assertTrue(!html.getValue().contains("AI 賦能全端開發"), "不應以新課程宣傳為主軸");
    }
}
