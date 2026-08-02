package world.springai.survey.promo;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** snippet 生成、Markdown escape、URL 解析 */
class PromoPlacementServiceSnippetTest {

    private final PromoPlacementRepository placementRepository = mock(PromoPlacementRepository.class);
    private final PromoProposalRepository proposalRepository = mock(PromoProposalRepository.class);
    private final PromoPlacementService service =
        new PromoPlacementService(placementRepository, proposalRepository);

    @Test
    void escape跳脫markdown特殊字元() {
        assertEquals("\\*粗體\\*與\\[連結\\]\\(x\\)",
            PromoPlacementService.escapeMarkdown("*粗體*與[連結](x)"));
    }

    @Test
    void 產生成對promo區塊snippet() {
        PromoProposal p = new PromoProposal(1L, "王", "a@b.c", "好課",
            "限時 *5* 折", "馬上看", "https://example.com", 2, 100);
        org.springframework.test.util.ReflectionTestUtils.setField(p, "id", 9L);
        p.setStatus(PromoProposal.STATUS_APPROVED);
        when(proposalRepository.findById(9L)).thenReturn(Optional.of(p));
        when(placementRepository.save(any())).thenAnswer(inv -> {
            PromoPlacement pl = inv.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(pl, "id", 55L);
            return pl;
        });

        PromoPlacementService.Snippet s = service.createPlacement(9L);

        assertEquals(55L, s.placementId());
        String md = s.markdown();
        assertTrue(md.startsWith("<!--promo-->\n"));
        assertTrue(md.endsWith("<!--/promo-->\n"));
        assertTrue(md.contains("限時 \\*5\\* 折"));           // 文案已 escape
        assertTrue(md.contains("[馬上看](/promo/c/55?rt=__PROMO_RT__)"));
    }

    @Test
    void 非核准提案不可建版位() {
        PromoProposal p = new PromoProposal(1L, "王", "a@b.c", "好課",
            "文案", "看", "https://example.com", 2, 100);
        org.springframework.test.util.ReflectionTestUtils.setField(p, "id", 9L);
        when(proposalRepository.findById(9L)).thenReturn(Optional.of(p)); // 仍 PENDING
        assertThrows(PromoProposalService.PromoValidationException.class,
            () -> service.createPlacement(9L));
    }

    @Test
    void 解析markdown中的placementId_去重保序() {
        String md = "x [a](/promo/c/3?rt=__PROMO_RT__) y [b](/promo/c/12?rt=__PROMO_RT__)"
            + " 重複 [c](/promo/c/3?rt=__PROMO_RT__)";
        assertEquals(List.of(3L, 12L), PromoPlacementService.parsePlacementIds(md));
    }
}
