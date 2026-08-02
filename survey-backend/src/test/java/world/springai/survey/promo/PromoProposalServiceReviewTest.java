package world.springai.survey.promo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import world.springai.survey.reader.CreditPolicy;
import world.springai.survey.reader.CreditTxn;
import world.springai.survey.reader.CreditTxnRepository;
import world.springai.survey.reader.ReaderRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 狀態機轉移矩陣與退點金額／冪等 */
class PromoProposalServiceReviewTest {

    private final PromoProposalRepository proposalRepository = mock(PromoProposalRepository.class);
    private final ReaderRepository readerRepository = mock(ReaderRepository.class);
    private final CreditTxnRepository creditTxnRepository = mock(CreditTxnRepository.class);
    private PromoProposalService service;

    @BeforeEach
    void setUp() {
        service = new PromoProposalService(
            proposalRepository, readerRepository, creditTxnRepository, mock(CreditPolicy.class));
        when(readerRepository.addCredits(anyLong(), anyInt())).thenReturn(1);
        when(creditTxnRepository.existsByPromoProposalIdAndReason(anyLong(), anyString()))
            .thenReturn(false);
    }

    /** 建一筆指定狀態／配額的提案 mock（id=7, reader=1, quota=3, used 由參數給） */
    private PromoProposal proposal(String status, int used) {
        PromoProposal p = new PromoProposal(1L, "王小明", "ming@example.com", "好課",
            "文案", "報名", "https://example.com", 3, 100);
        p.setStatus(status);
        p.setPlacementUsed(used);
        // id 由 JPA 產生，測試用反射或 setter；實作時給 PromoProposal 一個測試可用的 setId 或改用 spy
        org.springframework.test.util.ReflectionTestUtils.setField(p, "id", 7L);
        when(proposalRepository.findById(7L)).thenReturn(Optional.of(p));
        // save 返回同一物件
        when(proposalRepository.save(p)).thenReturn(p);
        return p;
    }

    @Test
    void 待審可核准() {
        proposal(PromoProposal.STATUS_PENDING, 0);
        assertEquals(PromoProposal.STATUS_APPROVED, service.approve(7L).getStatus());
        verify(readerRepository, never()).addCredits(anyLong(), anyInt());
    }

    @Test
    void 拒絕時全額退點並寫帳本() {
        proposal(PromoProposal.STATUS_PENDING, 0);
        service.reject(7L, "文案不符規範");
        verify(readerRepository).addCredits(1L, 300); // 3×100 全退
        verify(creditTxnRepository).save(argThat(txn ->
            txn.getDelta() == 300 && CreditTxn.REASON_PROMO_REFUND.equals(txn.getReason())));
    }

    @Test
    void 核准後封存退未用餘額() {
        proposal(PromoProposal.STATUS_APPROVED, 2);
        service.archive(7L);
        verify(readerRepository).addCredits(1L, 100); // (3-2)×100
    }

    @Test
    void 已拒絕再封存不重複退點() {
        proposal(PromoProposal.STATUS_REJECTED, 0);
        when(creditTxnRepository.existsByPromoProposalIdAndReason(
            7L, CreditTxn.REASON_PROMO_REFUND)).thenReturn(true);
        service.archive(7L);
        verify(readerRepository, never()).addCredits(anyLong(), anyInt());
    }

    @Test
    void 配額用罄時封存不產生退點交易() {
        proposal(PromoProposal.STATUS_APPROVED, 3);
        service.archive(7L);
        verify(readerRepository, never()).addCredits(anyLong(), anyInt());
        verify(creditTxnRepository, never()).save(any());
    }

    @Test
    void 非法轉移一律拒絕() {
        proposal(PromoProposal.STATUS_APPROVED, 0);
        assertThrows(PromoProposalService.PromoValidationException.class,
            () -> service.approve(7L));   // APPROVED 不能再核准
        assertThrows(PromoProposalService.PromoValidationException.class,
            () -> service.reject(7L, "x")); // APPROVED 不能改拒絕
        proposal(PromoProposal.STATUS_ARCHIVED, 0);
        assertThrows(PromoProposalService.PromoValidationException.class,
            () -> service.archive(7L));   // 終態不能再封存
    }
}
