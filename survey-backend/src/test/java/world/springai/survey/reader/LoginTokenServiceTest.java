package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** LoginTokenService 行為測試：只存雜湊、一次性、可到期、節流 */
class LoginTokenServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-25T12:00:00+08:00");

    private LoginTokenRepository repository;
    private LoginTokenService service;

    @BeforeEach
    void setUp() {
        repository = mock(LoginTokenRepository.class);
        // TTL 15 分鐘、節流 15 分鐘內最多 3 封
        service = new LoginTokenService(repository, 15, 3, 15);
    }

    /** 明文 token 不得等於入庫的雜湊——DB 外洩時 token 必須不可用 */
    @Test
    void storesHashNotPlaintext() {
        when(repository.save(any(LoginToken.class))).thenAnswer(i -> i.getArgument(0));

        String rawToken = service.issue("User@Example.com", NOW);

        ArgumentCaptor<LoginToken> captor = ArgumentCaptor.forClass(LoginToken.class);
        verify(repository).save(captor.capture());
        assertNotEquals(rawToken, captor.getValue().getTokenHash(), "入庫的必須是雜湊，不是明文");
        assertFalse(captor.getValue().getTokenHash().contains(rawToken), "雜湊不得包含明文");
    }

    /** email 一律正規化為小寫並去除前後空白 */
    @Test
    void normalisesEmailBeforeStoring() {
        when(repository.save(any(LoginToken.class))).thenAnswer(i -> i.getArgument(0));

        service.issue("  User@Example.COM  ", NOW);

        ArgumentCaptor<LoginToken> captor = ArgumentCaptor.forClass(LoginToken.class);
        verify(repository).save(captor.capture());
        assertEquals("user@example.com", captor.getValue().getEmail());
    }

    /** 到期時間為簽發時間 + TTL */
    @Test
    void expiryIsIssuedAtPlusTtl() {
        when(repository.save(any(LoginToken.class))).thenAnswer(i -> i.getArgument(0));

        service.issue("user@example.com", NOW);

        ArgumentCaptor<LoginToken> captor = ArgumentCaptor.forClass(LoginToken.class);
        verify(repository).save(captor.capture());
        assertEquals(NOW.plusMinutes(15), captor.getValue().getExpiresAt());
    }

    /**
     * 正常兌換：回傳 email，且必須是透過原子的 markUsedIfUnused 標記已使用。
     *
     * <p>注意：這裡不能只斷言 stored 物件的 isUsed()——stored 是同一個物件參照，
     * 就算把 markUsedIfUnused 整段拿掉、只留下回傳 email 的路徑，物件狀態的
     * 斷言依然會通過（因為前面 token.isUsed() 前置檢查根本沒改到 usedAt）。
     * 真正要守住的行為是「有沒有呼叫原子更新」，所以改用 verify 驗證呼叫本身。</p>
     */
    @Test
    void consumeValidTokenReturnsEmailAndMarksUsed() {
        when(repository.save(any(LoginToken.class))).thenAnswer(i -> i.getArgument(0));
        String rawToken = service.issue("user@example.com", NOW);
        String tokenHash = service.hash(rawToken);

        LoginToken stored = new LoginToken(tokenHash, "user@example.com", NOW.plusMinutes(15));
        when(repository.findByTokenHash(tokenHash)).thenReturn(Optional.of(stored));
        OffsetDateTime consumeTime = NOW.plusMinutes(1);
        when(repository.markUsedIfUnused(tokenHash, consumeTime)).thenReturn(1);

        Optional<String> email = service.consume(rawToken, consumeTime);

        assertTrue(email.isPresent());
        assertEquals("user@example.com", email.get());
        verify(repository).markUsedIfUnused(tokenHash, consumeTime);
    }

    /**
     * 併發下第二個請求必須失敗：token 存在且未過期，但 markUsedIfUnused 回傳 0，
     * 表示已被另一個並行請求搶先兌換走了（原子 UPDATE 的 usedAt IS NULL 條件不成立）。
     */
    @Test
    void consumeReturnsEmptyWhenConcurrentRequestAlreadyMarkedUsed() {
        String rawToken = "some-raw-token";
        String tokenHash = service.hash(rawToken);
        LoginToken stored = new LoginToken(tokenHash, "user@example.com", NOW.plusMinutes(15));
        when(repository.findByTokenHash(tokenHash)).thenReturn(Optional.of(stored));
        OffsetDateTime consumeTime = NOW.plusMinutes(1);
        when(repository.markUsedIfUnused(tokenHash, consumeTime)).thenReturn(0);

        assertTrue(service.consume(rawToken, consumeTime).isEmpty());
    }

    /** 同一 token 不得兌換兩次 */
    @Test
    void consumeRejectsAlreadyUsedToken() {
        String rawToken = "some-raw-token";
        LoginToken used = new LoginToken(service.hash(rawToken), "user@example.com", NOW.plusMinutes(15));
        used.markUsed(NOW);
        when(repository.findByTokenHash(service.hash(rawToken))).thenReturn(Optional.of(used));

        assertTrue(service.consume(rawToken, NOW.plusMinutes(1)).isEmpty());
    }

    /** 過期 token 不得兌換 */
    @Test
    void consumeRejectsExpiredToken() {
        String rawToken = "some-raw-token";
        LoginToken expired = new LoginToken(service.hash(rawToken), "user@example.com", NOW.plusMinutes(15));
        when(repository.findByTokenHash(service.hash(rawToken))).thenReturn(Optional.of(expired));

        assertTrue(service.consume(rawToken, NOW.plusMinutes(16)).isEmpty());
    }

    /** 不存在的 token 不得兌換，且不得因此拋例外 */
    @Test
    void consumeRejectsUnknownToken() {
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertTrue(service.consume("forged-token", NOW).isEmpty());
    }

    /** 空白或 null token 直接拒絕，不查資料庫 */
    @Test
    void consumeRejectsBlankTokenWithoutQuerying() {
        assertTrue(service.consume(null, NOW).isEmpty());
        assertTrue(service.consume("   ", NOW).isEmpty());
        verify(repository, never()).findByTokenHash(anyString());
    }

    /** 未達上限不節流 */
    @Test
    void notThrottledBelowLimit() {
        when(repository.countByEmailAndCreatedAtAfter("user@example.com", NOW.minusMinutes(15))).thenReturn(2L);

        assertFalse(service.isThrottled("user@example.com", NOW));
    }

    /** 達到上限即節流（避免被當寄信放大器） */
    @Test
    void throttledAtLimit() {
        when(repository.countByEmailAndCreatedAtAfter("user@example.com", NOW.minusMinutes(15))).thenReturn(3L);

        assertTrue(service.isThrottled("user@example.com", NOW));
    }

    /** 節流檢查也要正規化 email，否則大小寫變化可繞過 */
    @Test
    void throttleCheckNormalisesEmail() {
        when(repository.countByEmailAndCreatedAtAfter("user@example.com", NOW.minusMinutes(15))).thenReturn(3L);

        assertTrue(service.isThrottled("USER@Example.com", NOW));
    }
}
