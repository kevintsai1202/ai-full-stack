package world.springai.survey.reader;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** token 用途隔離：reader 的登入連結不得被拿去兌換 admin 權限 */
class LoginTokenPurposeTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-06T12:00:00+08:00");

    /**
     * 以 reader 用途簽發的 token，用 admin 用途兌換必須失敗（提權防護），
     * <b>且該 token 不得因此被消耗掉</b>。
     *
     * <p>「不消耗」這一半同樣是不變量：若用途不符時仍把 token 標記成已使用，
     * 任何人只要對讀者的 magic-link 打一次 admin 兌換，就能讓那條連結失效——
     * 一個免費的阻斷服務。原本的測試只斷言「兌換失敗」，把實作改成
     * 「先標記已使用再檢查用途」照樣全綠，所以這裡補上第二段。</p>
     */
    @Test
    void readerTokenCannotBeConsumedAsAdmin() {
        LoginTokenService service = newService();

        String raw = service.issue("kevin@example.com", LoginToken.PURPOSE_READER, NOW);
        Optional<String> result = service.consume(raw, LoginToken.PURPOSE_ADMIN, NOW);

        assertTrue(result.isEmpty(), "reader token 不得兌換成 admin");
        assertEquals(Optional.of("kevin@example.com"), service.consume(raw, LoginToken.PURPOSE_READER, NOW),
            "用途不符的兌換嘗試不得消耗 token，否則等於任何人都能讓別人的登入連結失效");
    }

    /** 用途相符時應正常兌換並回傳 email */
    @Test
    void adminTokenConsumesWithMatchingPurpose() {
        LoginTokenService service = newService();

        String raw = service.issue("kevin@example.com", LoginToken.PURPOSE_ADMIN, NOW);
        Optional<String> result = service.consume(raw, LoginToken.PURPOSE_ADMIN, NOW);

        assertEquals(Optional.of("kevin@example.com"), result);
    }

    /**
     * 沿用 {@code LoginTokenServiceTest} 既有的建構方式：以 Mockito mock repository，
     * 並讓 save/markUsedIfUnused 表現得像真正的資料庫（回傳存入的物件、原子標記已使用）。
     */
    private LoginTokenService newService() {
        LoginTokenRepository repository = mock(LoginTokenRepository.class);
        // TTL 15 分鐘、節流 3 封/15 分鐘，與既有測試一致
        LoginTokenService service = new LoginTokenService(repository, 15, 3, 15);

        // save 回傳同一個物件，讓後續 findByTokenHash 能取回剛存入的 token
        when(repository.save(any(LoginToken.class))).thenAnswer(invocation -> {
            LoginToken token = invocation.getArgument(0);
            when(repository.findByTokenHash(token.getTokenHash())).thenReturn(Optional.of(token));
            when(repository.markUsedIfUnused(org.mockito.ArgumentMatchers.eq(token.getTokenHash()), any(OffsetDateTime.class)))
                .thenAnswer(markInvocation -> {
                    token.markUsed(markInvocation.getArgument(1));
                    return 1;
                });
            return token;
        });

        return service;
    }
}
