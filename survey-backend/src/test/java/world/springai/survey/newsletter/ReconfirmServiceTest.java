package world.springai.survey.newsletter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import world.springai.survey.audience.SubscriptionLinkBuilder;
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.EmailTemplate;
import world.springai.survey.mail.MailSender;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 補寄確認信：名單口徑由 SQL 負責，本測試驗證寄送編排、內容與逐封容錯。 */
class ReconfirmServiceTest {

    private JdbcTemplate jdbc;
    private MailSender mailSender;
    private EmailLogRepository emailLogRepository;
    private SubscriptionLinkBuilder linkBuilder;
    private ReconfirmService service;

    /** 用真實 EmailTemplate 以驗證外框與退訂頁腳確實套上。 */
    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        mailSender = mock(MailSender.class);
        emailLogRepository = mock(EmailLogRepository.class);
        linkBuilder = mock(SubscriptionLinkBuilder.class);
        service = new ReconfirmService(jdbc, mailSender, emailLogRepository,
            new EmailTemplate(), linkBuilder);

        when(linkBuilder.confirmLink(anyString())).thenReturn("CONFIRM_LINK_MARKER");
        when(linkBuilder.unsubscribeLink(anyString())).thenReturn("UNSUB_LINK_MARKER");
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("zsend-id");
        // alreadySent／alreadyConfirmed 兩支統計查詢，本檔案不驗其數值
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
    }

    /**
     * 信件必須含確認連結與退訂連結，且不得提及推薦獎勵（spec D7）
     * 或說明不點確認的後果（spec D8）。
     */
    @Test
    void mailContainsConfirmLinkAndStaysNeutral() {
        when(jdbc.queryForList(anyString(), eq(String.class)))
            .thenReturn(List.of("alice@example.com"));

        ReconfirmService.ReconfirmResult result = service.sendReconfirmations(null);

        assertThat(result.accepted()).isEqualTo(1);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(anyString(), anyString(), html.capture());
        assertThat(html.getValue()).contains("CONFIRM_LINK_MARKER");
        assertThat(html.getValue()).contains("UNSUB_LINK_MARKER");
        assertThat(html.getValue()).doesNotContain("{{confirmLink}}");
        assertThat(html.getValue()).doesNotContain("獎勵");
        assertThat(html.getValue()).doesNotContain("取消訂閱你的");
    }

    /** limit 小於名單時只寄前 limit 封，其餘回報於 remaining。 */
    @Test
    void limitSplitsBatchAndReportsRemaining() {
        when(jdbc.queryForList(anyString(), eq(String.class)))
            .thenReturn(List.of("alice@example.com", "bob@example.com", "carol@example.com"));

        ReconfirmService.ReconfirmResult result = service.sendReconfirmations(2);

        assertThat(result.recipientCount()).isEqualTo(2);
        assertThat(result.accepted()).isEqualTo(2);
        assertThat(result.remaining()).isEqualTo(1);
        verify(mailSender, times(2)).send(anyString(), anyString(), anyString());
    }

    /** 單封失敗不中斷整批，且失敗要寫 email_log status=failed。 */
    @Test
    void failureIsLoggedAndBatchContinues() {
        when(jdbc.queryForList(anyString(), eq(String.class)))
            .thenReturn(List.of("alice@example.com", "bob@example.com"));
        when(mailSender.send(eq("alice@example.com"), anyString(), anyString()))
            .thenThrow(new RuntimeException("boom"));

        ReconfirmService.ReconfirmResult result = service.sendReconfirmations(null);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.accepted()).isEqualTo(1);
        ArgumentCaptor<EmailLog> logs = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository, times(2)).save(logs.capture());
        assertThat(logs.getAllValues()).anyMatch(l -> "failed".equals(l.getStatus()));
        assertThat(logs.getAllValues()).allMatch(l -> ReconfirmService.LOG_TYPE.equals(l.getType()));
    }

    /** 空名單不寄信也不報錯。 */
    @Test
    void emptyAudienceSendsNothing() {
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of());

        ReconfirmService.ReconfirmResult result = service.sendReconfirmations(null);

        assertThat(result.recipientCount()).isZero();
        verify(mailSender, org.mockito.Mockito.never())
            .send(anyString(), anyString(), anyString());
    }

    /**
     * 名單 SQL 必須保留三道排除條件（spec §4.4）。
     *
     * <p>冪等與防騷擾完全由這段 SQL 保證，不是由 Java 邏輯保證——
     * 少了 email_log 的排除，每次按按鈕都會重寄給同一批人；
     * 少了 audience_consent 的排除，已經確認過的人會被重複打擾。
     * mock 的 JdbcTemplate 驗不了語意，但驗得了條件沒被刪掉。</p>
     */
    @Test
    void pendingSqlKeepsAllThreeExclusions() {
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);

        service.sendReconfirmations(null);

        verify(jdbc).queryForList(sql.capture(), eq(String.class));
        assertThat(sql.getValue()).contains("sr.consent = true");
        assertThat(sql.getValue()).contains("sr.unsubscribed = false");
        assertThat(sql.getValue()).contains("source_key = 'confirmation-link'");
        assertThat(sql.getValue()).contains("el.type = 'reconfirm'");
    }
}
