package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Magic Link IP／全域防濫用測試。 */
class LoginAbuseGuardTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-28T10:00:00+08:00");

    private LoginRequestAttemptRepository repository;
    private LoginAbuseGuard guard;

    @BeforeEach
    void setUp() {
        repository = mock(LoginRequestAttemptRepository.class);
        guard = new LoginAbuseGuard(repository, 15, 20, 200);
    }

    /** 未達上限時記錄雜湊後來源，不得保存原始 IP。 */
    @Test
    void allowedRequestStoresOnlyHashedAddress() {
        assertTrue(guard.tryAcquire(" 2001:DB8::1 ", NOW));

        ArgumentCaptor<LoginRequestAttempt> captor =
            ArgumentCaptor.forClass(LoginRequestAttempt.class);
        verify(repository).saveAndFlush(captor.capture());
        assertNotEquals("2001:db8::1", captor.getValue().getIpHash());
        assertEquals(NOW, captor.getValue().getCreatedAt());
    }

    /** 同一來源達上限時不得新增紀錄，也不需要再查全站額度。 */
    @Test
    void rejectsAtIpLimit() {
        String hash = guard.hash("203.0.113.9");
        when(repository.countByIpHashAndCreatedAtAfter(
            hash, NOW.minusMinutes(15))).thenReturn(20L);

        assertFalse(guard.tryAcquire("203.0.113.9", NOW));
        verify(repository, never()).countByCreatedAtAfter(any());
        verify(repository, never()).saveAndFlush(any());
    }

    /** 全站達上限時拒絕輪替 Email／IP 的寄信消耗。 */
    @Test
    void rejectsAtGlobalLimit() {
        when(repository.countByCreatedAtAfter(NOW.minusMinutes(15))).thenReturn(200L);

        assertFalse(guard.tryAcquire("203.0.113.10", NOW));
        verify(repository, never()).saveAndFlush(any());
    }

    /** 空白來源放入共用 bucket，不能以缺少 IP 繞過節流。 */
    @Test
    void missingAddressUsesSharedUnknownBucket() {
        String unknownHash = guard.hash("unknown");
        when(repository.countByIpHashAndCreatedAtAfter(
            eq(unknownHash), eq(NOW.minusMinutes(15)))).thenReturn(20L);

        assertFalse(guard.tryAcquire(null, NOW));
    }
}
