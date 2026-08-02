package world.springai.survey.promo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 對帳：COMMIT／擋下／REMOVED／冪等／配額歸還 */
class PromoPlacementServiceReconcileTest {

    private final PromoPlacementRepository placementRepository = mock(PromoPlacementRepository.class);
    private final PromoProposalRepository proposalRepository = mock(PromoProposalRepository.class);
    private PromoPlacementService service;

    @BeforeEach
    void setUp() {
        service = new PromoPlacementService(placementRepository, proposalRepository);
        when(placementRepository.findByCampaignIdAndStatus(anyLong(), anyString()))
            .thenReturn(List.of());
        when(placementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(proposalRepository.consumeQuota(anyLong())).thenReturn(1);
    }

    /** 建一個版位 mock：id、proposalId、目前狀態與綁定 campaign */
    private PromoPlacement placement(long id, long proposalId, String status, Long campaignId) {
        PromoPlacement pl = new PromoPlacement(proposalId);
        org.springframework.test.util.ReflectionTestUtils.setField(pl, "id", id);
        pl.setStatus(status);
        pl.setCampaignId(campaignId);
        when(placementRepository.findById(id)).thenReturn(Optional.of(pl));
        return pl;
    }

    @Test
    void 內文出現的DRAFT被綁定並COMMIT扣配額() {
        PromoPlacement pl = placement(55L, 9L, PromoPlacement.STATUS_DRAFT, null);
        service.reconcile(100L, "[看](/promo/c/55?rt=__PROMO_RT__)");
        assertEquals(PromoPlacement.STATUS_COMMITTED, pl.getStatus());
        assertEquals(100L, pl.getCampaignId());
        verify(proposalRepository).consumeQuota(9L);
    }

    @Test
    void 配額不足擋下並回滾() {
        placement(55L, 9L, PromoPlacement.STATUS_DRAFT, null);
        when(proposalRepository.consumeQuota(9L)).thenReturn(0);
        assertThrows(IllegalStateException.class,
            () -> service.reconcile(100L, "[看](/promo/c/55?rt=__PROMO_RT__)"));
    }

    @Test
    void 已綁其他campaign擋下_markdown複製誤用() {
        placement(55L, 9L, PromoPlacement.STATUS_COMMITTED, 999L);
        assertThrows(IllegalStateException.class,
            () -> service.reconcile(100L, "[看](/promo/c/55?rt=__PROMO_RT__)"));
    }

    @Test
    void 同campaign重複對帳冪等不重複扣() {
        placement(55L, 9L, PromoPlacement.STATUS_COMMITTED, 100L);
        service.reconcile(100L, "[看](/promo/c/55?rt=__PROMO_RT__)");
        verify(proposalRepository, never()).consumeQuota(anyLong());
    }

    @Test
    void 重排時消失的版位轉REMOVED並歸還配額() {
        PromoPlacement gone = placement(66L, 9L, PromoPlacement.STATUS_COMMITTED, 100L);
        when(placementRepository.findByCampaignIdAndStatus(100L, PromoPlacement.STATUS_COMMITTED))
            .thenReturn(List.of(gone));
        service.reconcile(100L, "內文已無任何工商連結");
        assertEquals(PromoPlacement.STATUS_REMOVED, gone.getStatus());
        verify(proposalRepository).releaseQuota(9L);
    }

    @Test
    void 取消排程全數歸還() {
        PromoPlacement pl = placement(55L, 9L, PromoPlacement.STATUS_COMMITTED, 100L);
        when(placementRepository.findByCampaignIdAndStatus(100L, PromoPlacement.STATUS_COMMITTED))
            .thenReturn(List.of(pl));
        service.releaseForCampaign(100L);
        assertEquals(PromoPlacement.STATUS_REMOVED, pl.getStatus());
        verify(proposalRepository).releaseQuota(9L);
    }

    @Test
    void DRAFT版位已綁其他campaign擋下不扣配額() {
        placement(77L, 9L, PromoPlacement.STATUS_DRAFT, 999L);
        assertThrows(IllegalStateException.class,
            () -> service.reconcile(100L, "[看](/promo/c/77?rt=__PROMO_RT__)"));
        verify(proposalRepository, never()).consumeQuota(anyLong());
    }

    @Test
    void 預檢不寫入任何狀態() {
        placement(55L, 9L, PromoPlacement.STATUS_DRAFT, null);
        PromoProposal p = new PromoProposal(1L, "王", "a@b.c", "好課",
            "文", "看", "https://example.com", 2, 100);
        p.setStatus(PromoProposal.STATUS_APPROVED);
        when(proposalRepository.findById(9L)).thenReturn(Optional.of(p));
        service.assertCommittable("[看](/promo/c/55?rt=__PROMO_RT__)");
        verify(placementRepository, never()).save(any());
        verify(proposalRepository, never()).consumeQuota(anyLong());
    }
}
