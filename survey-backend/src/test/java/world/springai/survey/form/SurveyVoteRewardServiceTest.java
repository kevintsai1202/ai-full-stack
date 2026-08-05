package world.springai.survey.form;

import org.junit.jupiter.api.Test;
import world.springai.survey.reader.CreditPolicy;
import world.springai.survey.reader.CreditTxn;
import world.springai.survey.reader.CreditTxnRepository;
import world.springai.survey.reader.Reader;
import world.springai.survey.reader.ReaderRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SurveyVoteRewardService} 純 Mockito 單元測試：身分對映、冪等與帳本一致性。
 *
 * <p>形狀比照 {@code SurveyVoteServiceTest}（同套件、同 mock 風格、不需 DB）。</p>
 */
class SurveyVoteRewardServiceTest {

    private final ReaderRepository readerRepository = mock(ReaderRepository.class);
    private final CreditTxnRepository creditTxnRepository = mock(CreditTxnRepository.class);
    private final CreditPolicy creditPolicy = mock(CreditPolicy.class);
    private final SurveyVoteRewardService service =
        new SurveyVoteRewardService(readerRepository, creditTxnRepository, creditPolicy);

    /** 建一位有 id 的讀者 */
    private Reader reader(long id, String email) {
        Reader r = new Reader(email, "CODE" + id);
        r.setId(id);
        return r;
    }

    /** RECIPIENT 身分（信中連結）：以 email 反查讀者後發點 */
    @Test
    void recipient身分以email反查後發點() {
        when(creditPolicy.surveyVoteReward()).thenReturn(5);
        when(readerRepository.findByEmailIgnoreCase("a@example.com"))
            .thenReturn(Optional.of(reader(9L, "a@example.com")));
        when(creditTxnRepository.existsByReaderIdAndSurveyFormKeyAndReason(
            9L, "reader-poll", CreditTxn.REASON_SURVEY_VOTE_REWARD)).thenReturn(false);
        when(readerRepository.addCredits(9L, 5)).thenReturn(1);

        Optional<Integer> granted = service.grantIfEligible(
            "reader-poll", "滿意度調查", SurveyVote.IDENTITY_RECIPIENT, "a@example.com", 3L);

        assertEquals(Optional.of(5), granted);
        verify(creditTxnRepository).save(any(CreditTxn.class));
        verify(readerRepository).addCredits(9L, 5);
    }

    /** READER 身分（網頁已登入）：identityKey 即 readerId，不需反查 email */
    @Test
    void reader身分直接以id發點() {
        when(creditPolicy.surveyVoteReward()).thenReturn(5);
        when(readerRepository.findById(9L)).thenReturn(Optional.of(reader(9L, "a@example.com")));
        when(creditTxnRepository.existsByReaderIdAndSurveyFormKeyAndReason(
            9L, "reader-poll", CreditTxn.REASON_SURVEY_VOTE_REWARD)).thenReturn(false);
        when(readerRepository.addCredits(9L, 5)).thenReturn(1);

        Optional<Integer> granted = service.grantIfEligible(
            "reader-poll", "滿意度調查", SurveyVote.IDENTITY_READER, "9", null);

        assertEquals(Optional.of(5), granted);
        verify(readerRepository, never()).findByEmailIgnoreCase(any());
    }

    /** 匿名投票不發點，也不得寫任何帳本列 */
    @Test
    void 匿名身分不發點() {
        Optional<Integer> granted = service.grantIfEligible(
            "reader-poll", "滿意度調查", SurveyVote.IDENTITY_ANON, null, null);

        assertTrue(granted.isEmpty());
        verify(creditTxnRepository, never()).save(any());
        verify(readerRepository, never()).addCredits(anyLong(), anyInt());
    }

    /** email 找不到對應讀者（訂閱者尚未建帳）：照收投票、不發點 */
    @Test
    void 非註冊讀者不發點() {
        when(readerRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        Optional<Integer> granted = service.grantIfEligible(
            "reader-poll", "滿意度調查", SurveyVote.IDENTITY_RECIPIENT, "ghost@example.com", null);

        assertTrue(granted.isEmpty());
        verify(creditTxnRepository, never()).save(any());
    }

    /** 同一問卷已發過投票點數：改票不重發 */
    @Test
    void 同問卷已發過不重發() {
        when(readerRepository.findByEmailIgnoreCase("a@example.com"))
            .thenReturn(Optional.of(reader(9L, "a@example.com")));
        when(creditTxnRepository.existsByReaderIdAndSurveyFormKeyAndReason(
            9L, "reader-poll", CreditTxn.REASON_SURVEY_VOTE_REWARD)).thenReturn(true);

        Optional<Integer> granted = service.grantIfEligible(
            "reader-poll", "滿意度調查", SurveyVote.IDENTITY_RECIPIENT, "a@example.com", null);

        assertTrue(granted.isEmpty());
        verify(creditTxnRepository, never()).save(any());
        verify(readerRepository, never()).addCredits(anyLong(), anyInt());
    }

    /** 後台把投票獎勵設為 0（關閉發點）：不寫帳本列，避免留下一堆 0 點交易 */
    @Test
    void 獎勵為零時不寫帳本() {
        when(creditPolicy.surveyVoteReward()).thenReturn(0);
        when(readerRepository.findByEmailIgnoreCase("a@example.com"))
            .thenReturn(Optional.of(reader(9L, "a@example.com")));
        when(creditTxnRepository.existsByReaderIdAndSurveyFormKeyAndReason(
            9L, "reader-poll", CreditTxn.REASON_SURVEY_VOTE_REWARD)).thenReturn(false);

        Optional<Integer> granted = service.grantIfEligible(
            "reader-poll", "滿意度調查", SurveyVote.IDENTITY_RECIPIENT, "a@example.com", null);

        assertTrue(granted.isEmpty());
        verify(creditTxnRepository, never()).save(any());
    }

    /** addCredits 回 0 列代表讀者列已不存在：必須拋例外讓交易回滾，不可靜默放行 */
    @Test
    void 加點影響零列時拋例外() {
        when(creditPolicy.surveyVoteReward()).thenReturn(5);
        when(readerRepository.findByEmailIgnoreCase("a@example.com"))
            .thenReturn(Optional.of(reader(9L, "a@example.com")));
        when(creditTxnRepository.existsByReaderIdAndSurveyFormKeyAndReason(
            9L, "reader-poll", CreditTxn.REASON_SURVEY_VOTE_REWARD)).thenReturn(false);
        when(readerRepository.addCredits(9L, 5)).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.grantIfEligible(
            "reader-poll", "滿意度調查", SurveyVote.IDENTITY_RECIPIENT, "a@example.com", null),
            "帳本已寫入但餘額沒更新到，必須回滾而非靜默成功");
    }

    /** identityKey 為病態值（READER 身分卻不是數字）：不發點，不得讓 NumberFormatException 竄出 */
    @Test
    void reader身分identityKey非數字時不發點() {
        Optional<Integer> granted = service.grantIfEligible(
            "reader-poll", "滿意度調查", SurveyVote.IDENTITY_READER, "not-a-number", null);

        assertTrue(granted.isEmpty());
        verify(creditTxnRepository, never()).save(any());
    }
}
