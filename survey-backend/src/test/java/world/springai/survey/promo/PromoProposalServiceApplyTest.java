package world.springai.survey.promo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import world.springai.survey.reader.CreditPolicy;
import world.springai.survey.reader.CreditTxn;
import world.springai.survey.reader.CreditTxnRepository;
import world.springai.survey.reader.Reader;
import world.springai.survey.reader.ReaderRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 申請＋扣點：驗證、餘額防線、扣款先於落單、帳本寫入 */
class PromoProposalServiceApplyTest {

    private final PromoProposalRepository proposalRepository = mock(PromoProposalRepository.class);
    private final ReaderRepository readerRepository = mock(ReaderRepository.class);
    private final CreditTxnRepository creditTxnRepository = mock(CreditTxnRepository.class);
    private final CreditPolicy creditPolicy = mock(CreditPolicy.class);
    private PromoProposalService service;

    /** 合法申請的基準樣本，各測試再局部改壞 */
    private PromoProposalService.ApplyRequest valid;

    @BeforeEach
    void setUp() {
        service = new PromoProposalService(
            proposalRepository, readerRepository, creditTxnRepository, creditPolicy);
        valid = new PromoProposalService.ApplyRequest(
            "王小明", "ming@example.com", "好課推薦", "這是一段純文字文案",
            "立即報名", "https://example.com/course", 2);
        when(creditPolicy.promoPlacementCost()).thenReturn(100);
        when(proposalRepository.countByReaderIdAndStatus(1L, PromoProposal.STATUS_PENDING))
            .thenReturn(0);
        Reader reader = mock(Reader.class);
        when(reader.getCredits()).thenReturn(500);
        when(readerRepository.findById(1L)).thenReturn(Optional.of(reader));
        when(readerRepository.deductCredits(1L, 200)).thenReturn(1);
        // 模擬資料庫 save：設定 id（如同真實 DB 生成 ID）
        when(proposalRepository.save(any())).thenAnswer(inv -> {
            PromoProposal proposal = inv.getArgument(0);
            try {
                java.lang.reflect.Field idField = PromoProposal.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(proposal, 1000L);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return proposal;
        });
    }

    @Test
    void 成功申請_扣款先於落單且寫入帳本() {
        service.apply(1L, valid);
        InOrder inOrder = inOrder(readerRepository, proposalRepository, creditTxnRepository);
        inOrder.verify(readerRepository).deductCredits(1L, 200); // 100×2
        inOrder.verify(proposalRepository).save(any(PromoProposal.class));
        inOrder.verify(creditTxnRepository).save(argThat(txn ->
            txn.getDelta() == -200 && CreditTxn.REASON_PROMO_APPLY.equals(txn.getReason())));
    }

    @Test
    void 餘額不足擋下申請() {
        when(readerRepository.deductCredits(1L, 200)).thenReturn(0);
        assertThrows(PromoProposalService.InsufficientCreditsException.class,
            () -> service.apply(1L, valid));
        verify(proposalRepository, never()).save(any());
    }

    @Test
    void 非https網址拒絕() {
        var bad = new PromoProposalService.ApplyRequest(valid.contactName(), valid.contactEmail(),
            valid.title(), valid.bodyText(), valid.linkText(), "http://example.com", 1);
        assertThrows(PromoProposalService.PromoValidationException.class,
            () -> service.apply(1L, bad));
    }

    @Test
    void mailto連結接受並原樣儲存() {
        var req = new PromoProposalService.ApplyRequest(valid.contactName(), valid.contactEmail(),
            valid.title(), valid.bodyText(), valid.linkText(), "mailto:sales@example.com", 2);
        service.apply(1L, req);
        verify(proposalRepository).save(argThat(p ->
            "mailto:sales@example.com".equals(p.getLinkUrl())));
    }

    @Test
    void 純Email自動正規化為mailto() {
        var req = new PromoProposalService.ApplyRequest(valid.contactName(), valid.contactEmail(),
            valid.title(), valid.bodyText(), valid.linkText(), "sales@example.com", 2);
        service.apply(1L, req);
        verify(proposalRepository).save(argThat(p ->
            "mailto:sales@example.com".equals(p.getLinkUrl())));
    }

    @Test
    void mailto帶subject參數接受() {
        var req = new PromoProposalService.ApplyRequest(valid.contactName(), valid.contactEmail(),
            valid.title(), valid.bodyText(), valid.linkText(),
            "mailto:sales@example.com?subject=合作洽詢", 2);
        service.apply(1L, req);
        verify(proposalRepository).save(argThat(p ->
            "mailto:sales@example.com?subject=合作洽詢".equals(p.getLinkUrl())));
    }

    @Test
    void mailto信箱格式不正確拒絕() {
        var bad = new PromoProposalService.ApplyRequest(valid.contactName(), valid.contactEmail(),
            valid.title(), valid.bodyText(), valid.linkText(), "mailto:not-an-email", 1);
        assertThrows(PromoProposalService.PromoValidationException.class,
            () -> service.apply(1L, bad));
    }

    @Test
    void 非網址也非Email的文字拒絕() {
        var bad = new PromoProposalService.ApplyRequest(valid.contactName(), valid.contactEmail(),
            valid.title(), valid.bodyText(), valid.linkText(), "hello world", 1);
        assertThrows(PromoProposalService.PromoValidationException.class,
            () -> service.apply(1L, bad));
    }

    @Test
    void 含小於號拒絕_禁HTML() {
        var bad = new PromoProposalService.ApplyRequest(valid.contactName(), valid.contactEmail(),
            valid.title(), "hello <b>bold</b>", valid.linkText(), valid.linkUrl(), 1);
        assertThrows(PromoProposalService.PromoValidationException.class,
            () -> service.apply(1L, bad));
    }

    @Test
    void 含佔位符字面拒絕() {
        var bad = new PromoProposalService.ApplyRequest(valid.contactName(), valid.contactEmail(),
            valid.title(), "嵌入 __PROMO_RT__ 攻擊", valid.linkText(), valid.linkUrl(), 1);
        assertThrows(PromoProposalService.PromoValidationException.class,
            () -> service.apply(1L, bad));
    }

    @Test
    void 投放次數超出1到3拒絕() {
        var bad = new PromoProposalService.ApplyRequest(valid.contactName(), valid.contactEmail(),
            valid.title(), valid.bodyText(), valid.linkText(), valid.linkUrl(), 4);
        assertThrows(PromoProposalService.PromoValidationException.class,
            () -> service.apply(1L, bad));
    }

    @Test
    void 待審上限3件擋下() {
        when(proposalRepository.countByReaderIdAndStatus(1L, PromoProposal.STATUS_PENDING))
            .thenReturn(3);
        assertThrows(PromoProposalService.PromoValidationException.class,
            () -> service.apply(1L, valid));
    }

    @Test
    void 單價0時免扣點也不寫帳本負項() {
        when(creditPolicy.promoPlacementCost()).thenReturn(0);
        service.apply(1L, valid);
        verify(readerRepository, never()).deductCredits(anyLong(), anyInt());
        verify(creditTxnRepository, never()).save(any());
    }
}
