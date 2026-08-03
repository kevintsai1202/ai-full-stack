package world.springai.survey.promo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import world.springai.survey.reader.ReaderSessionService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 轉址與歸戶：RECIPIENT > READER > ANON；只有 COMMITTED 記點擊 */
class PromoClickServiceTest {

    private final PromoPlacementRepository placementRepository = mock(PromoPlacementRepository.class);
    private final PromoProposalRepository proposalRepository = mock(PromoProposalRepository.class);
    private final PromoClickRepository clickRepository = mock(PromoClickRepository.class);
    private final PromoRecipientTokenService tokenService = mock(PromoRecipientTokenService.class);
    private final ReaderSessionService sessionService = mock(ReaderSessionService.class);
    private PromoClickService service;

    @BeforeEach
    void setUp() {
        service = new PromoClickService(placementRepository, proposalRepository,
            clickRepository, tokenService, sessionService);
        when(tokenService.verify(any())).thenReturn(Optional.empty());
        when(sessionService.readReaderId(any(), any())).thenReturn(Optional.empty());
    }

    /** 準備版位＋提案：id=55、proposal=9、目的地 https://example.com */
    private void placement(String status) {
        PromoPlacement pl = new PromoPlacement(9L);
        org.springframework.test.util.ReflectionTestUtils.setField(pl, "id", 55L);
        pl.setStatus(status);
        when(placementRepository.findById(55L)).thenReturn(Optional.of(pl));
        PromoProposal p = new PromoProposal(1L, "王", "a@b.c", "好課",
            "文", "看", "https://example.com", 2, 100);
        when(proposalRepository.findById(9L)).thenReturn(Optional.of(p));
    }

    @Test
    void 有效token記EMAIL_RECIPIENT並轉址() {
        placement(PromoPlacement.STATUS_COMMITTED);
        when(tokenService.verify("tok")).thenReturn(Optional.of("alice@example.com"));
        assertEquals(Optional.of(new PromoClickService.Destination("https://example.com", "好課")),
            service.resolveAndRecord(55L, "tok", null));
        verify(clickRepository).save(argThat(c ->
            PromoClick.CHANNEL_EMAIL.equals(c.getChannel())
            && PromoClick.IDENTITY_RECIPIENT.equals(c.getIdentityType())
            && "alice@example.com".equals(c.getIdentityKey())));
    }

    @Test
    void 無token有session記WEB_READER() {
        placement(PromoPlacement.STATUS_COMMITTED);
        when(sessionService.readReaderId(eq("cookie"), any())).thenReturn(Optional.of(42L));
        service.resolveAndRecord(55L, null, "cookie");
        verify(clickRepository).save(argThat(c ->
            PromoClick.CHANNEL_WEB.equals(c.getChannel())
            && PromoClick.IDENTITY_READER.equals(c.getIdentityType())
            && "42".equals(c.getIdentityKey())));
    }

    @Test
    void 皆無記WEB_ANON_identityKey為null() {
        placement(PromoPlacement.STATUS_COMMITTED);
        service.resolveAndRecord(55L, null, null);
        verify(clickRepository).save(argThat(c ->
            PromoClick.IDENTITY_ANON.equals(c.getIdentityType()) && c.getIdentityKey() == null));
    }

    @Test
    void DRAFT版位照樣轉址但不記錄() {
        placement(PromoPlacement.STATUS_DRAFT);
        assertEquals(Optional.of(new PromoClickService.Destination("https://example.com", "好課")),
            service.resolveAndRecord(55L, null, null));
        verify(clickRepository, never()).save(any());
    }

    @Test
    void 版位不存在回empty() {
        when(placementRepository.findById(55L)).thenReturn(Optional.empty());
        assertTrue(service.resolveAndRecord(55L, null, null).isEmpty());
    }

    @Test
    void 記錄失敗不影響轉址() {
        placement(PromoPlacement.STATUS_COMMITTED);
        when(clickRepository.save(any())).thenThrow(new RuntimeException("db down"));
        assertEquals(Optional.of(new PromoClickService.Destination("https://example.com", "好課")),
            service.resolveAndRecord(55L, null, null)); // 讀者體驗優先，統計 best-effort
    }
}
