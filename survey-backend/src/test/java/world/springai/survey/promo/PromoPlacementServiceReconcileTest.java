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
        service = new PromoPlacementService(placementRepository, proposalRepository,
            "https://example.org", new world.springai.survey.newsletter.ContentSplitter());
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

    /**
     * I1 修正：預檢須鏡射 reconcile 的全部拒絕條件。COMMITTED 版位曾經無條件
     * continue，讓「已刊他期」的情況撐到 reconcile 在 Campaign 落地後才爆——
     * 現在任何已綁定 campaignId 的版位（COMMITTED 或 REMOVED）都應在預檢階段擋下。
     */
    @Test
    void COMMITTED版位在預檢時被拒絕() {
        placement(55L, 9L, PromoPlacement.STATUS_COMMITTED, 999L);
        assertThrows(PromoPlacementService.PromoReconcileException.class,
            () -> service.assertCommittable("[看](/promo/c/55?rt=__PROMO_RT__)"));
    }

    /** REMOVED 版位一樣保留 campaignId，預檢同樣應拒絕，理由同上 */
    @Test
    void REMOVED版位在預檢時被拒絕() {
        placement(55L, 9L, PromoPlacement.STATUS_REMOVED, 999L);
        assertThrows(PromoPlacementService.PromoReconcileException.class,
            () -> service.assertCommittable("[看](/promo/c/55?rt=__PROMO_RT__)"));
    }

    /**
     * I1 修正：同一提案在同一次寄送插入兩個版位時，須按提案分組聚合本次需求數，
     * 與「配額 − 已用」比較，而非逐版位各自比對（否則兩個各自看起來都還有配額的
     * 版位仍可能一起超額）。此案例：quota=2、used=1（剩 1），但同時插入兩個版位
     * → 聚合需求 2 > 剩餘 1，應擋下並提示提案名稱。
     */
    @Test
    void 同提案兩版位聚合超額時預檢擋下() {
        placement(55L, 9L, PromoPlacement.STATUS_DRAFT, null);
        placement(56L, 9L, PromoPlacement.STATUS_DRAFT, null);
        PromoProposal p = new PromoProposal(1L, "王", "a@b.c", "好課",
            "文", "看", "https://example.com", 2, 100);
        org.springframework.test.util.ReflectionTestUtils.setField(p, "id", 9L);
        p.setStatus(PromoProposal.STATUS_APPROVED);
        p.setPlacementUsed(1); // 剩餘配額只有 1
        when(proposalRepository.findById(9L)).thenReturn(Optional.of(p));

        PromoPlacementService.PromoReconcileException ex = assertThrows(
            PromoPlacementService.PromoReconcileException.class,
            () -> service.assertCommittable(
                "[看1](/promo/c/55?rt=x) [看2](/promo/c/56?rt=x)"));
        assertTrue(ex.getMessage().contains("好課"), ex.getMessage());
    }
}
