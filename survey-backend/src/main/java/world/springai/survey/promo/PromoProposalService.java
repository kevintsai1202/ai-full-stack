package world.springai.survey.promo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import world.springai.survey.reader.CreditPolicy;
import world.springai.survey.reader.CreditTxn;
import world.springai.survey.reader.CreditTxnRepository;
import world.springai.survey.reader.Reader;
import world.springai.survey.reader.ReaderRepository;

/**
 * 工商提案：申請（扣點）與審核（狀態機＋退點）。
 *
 * <p>扣點交易設計完全比照 {@code UnlockService}：條件式扣款
 * （{@code WHERE credits >= :cost}）是併發防線；扣款、落單、寫帳本
 * 同一交易，任一失敗整組回滾，維持「餘額恆等於帳本總和」不變式。</p>
 */
@Service
public class PromoProposalService {

    /** 待審中提案的每人上限（防濫用） */
    static final int MAX_PENDING_PER_READER = 3;
    /** 投放次數上下限（spec §2） */
    static final int MIN_PLACEMENTS = 1;
    static final int MAX_PLACEMENTS = 3;

    /** 驗證不過（欄位格式／上限），controller 轉 400 */
    public static class PromoValidationException extends IllegalArgumentException {
        public PromoValidationException(String message) { super(message); }
    }

    /** 餘額不足，controller 轉 409 */
    public static class InsufficientCreditsException extends IllegalStateException {
        public InsufficientCreditsException(String message) { super(message); }
    }

    /** 申請請求；placements 為投放次數（1–3） */
    public record ApplyRequest(String contactName, String contactEmail, String title,
                               String bodyText, String linkText, String linkUrl,
                               int placements) {}

    /** 申請結果；credits 為扣款後重新讀取的權威餘額 */
    public record ApplyResult(long proposalId, int totalCost, int credits) {}

    private final PromoProposalRepository proposalRepository;
    private final ReaderRepository readerRepository;
    private final CreditTxnRepository creditTxnRepository;
    private final CreditPolicy creditPolicy;

    /** 注入提案、讀者、帳本與點數參數 */
    public PromoProposalService(PromoProposalRepository proposalRepository,
                                ReaderRepository readerRepository,
                                CreditTxnRepository creditTxnRepository,
                                CreditPolicy creditPolicy) {
        this.proposalRepository = proposalRepository;
        this.readerRepository = readerRepository;
        this.creditTxnRepository = creditTxnRepository;
        this.creditPolicy = creditPolicy;
    }

    /** 送出申請：驗證 → 扣點（單價×次數）→ 落單 → 寫帳本，同一交易 */
    @Transactional
    public ApplyResult apply(Long readerId, ApplyRequest req) {
        validate(readerId, req);

        int unitCost = creditPolicy.promoPlacementCost();
        int totalCost = unitCost * req.placements();

        Reader reader = readerRepository.findById(readerId)
            .orElseThrow(() -> new PromoValidationException("讀者不存在：id=" + readerId));

        if (totalCost > 0) {
            // 條件式扣款是併發防線：回 0 列代表餘額不足（或期間被其他交易扣走）
            if (reader.getCredits() < totalCost
                || readerRepository.deductCredits(readerId, totalCost) == 0) {
                throw new InsufficientCreditsException(
                    "點數不足：需要 " + totalCost + " 點，目前 " + reader.getCredits() + " 點");
            }
        }

        PromoProposal proposal = proposalRepository.save(new PromoProposal(
            readerId, req.contactName().trim(), req.contactEmail().trim(),
            req.title().trim(), req.bodyText().trim(), req.linkText().trim(),
            req.linkUrl().trim(), req.placements(), unitCost));

        if (totalCost > 0) {
            creditTxnRepository.save(new CreditTxn(readerId, -totalCost,
                CreditTxn.REASON_PROMO_APPLY, null, proposal.getTitle(), proposal.getId()));
        }

        // 扣款後重新讀取權威餘額（理由同 UnlockService：不用記憶體算術）
        int remaining = readerRepository.findById(readerId)
            .map(Reader::getCredits)
            .orElseThrow(() -> new IllegalStateException("扣款後讀不到讀者：id=" + readerId));
        return new ApplyResult(proposal.getId(), totalCost, remaining);
    }

    /** 欄位與上限驗證；訊息面向讀者、可直接顯示 */
    private void validate(Long readerId, ApplyRequest req) {
        requireLen(req.contactName(), 100, "聯絡人");
        requireLen(req.contactEmail(), 255, "Email");
        requireLen(req.title(), 150, "提案名稱");
        requireLen(req.bodyText(), 2000, "文案");
        requireLen(req.linkText(), 100, "連結文字");
        requireLen(req.linkUrl(), 1000, "網址");
        if (!req.contactEmail().contains("@")) {
            throw new PromoValidationException("Email 格式不正確");
        }
        if (!req.linkUrl().trim().startsWith("https://")) {
            throw new PromoValidationException("網址僅接受 https:// 開頭");
        }
        // 禁 HTML／Script：任何欄位含 '<' 一律拒絕；同時拒絕佔位符字面，
        // 否則寄送時每收件人替換機制會把收件人 token 誤代入文案（spec §7.2）
        for (String field : new String[]{req.title(), req.bodyText(), req.linkText()}) {
            if (field.contains("<")) {
                throw new PromoValidationException("內容不可包含 HTML（偵測到 < 字元）");
            }
            if (field.contains(PromoRecipientTokenService.PLACEHOLDER)) {
                throw new PromoValidationException("內容含保留字 __PROMO_RT__，請移除");
            }
        }
        if (req.placements() < MIN_PLACEMENTS || req.placements() > MAX_PLACEMENTS) {
            throw new PromoValidationException("投放次數僅接受 1–3 次");
        }
        if (proposalRepository.countByReaderIdAndStatus(readerId, PromoProposal.STATUS_PENDING)
            >= MAX_PENDING_PER_READER) {
            throw new PromoValidationException("同時最多 " + MAX_PENDING_PER_READER + " 件待審提案");
        }
    }

    /** 必填＋長度上限檢查 */
    private void requireLen(String value, int max, String label) {
        if (!StringUtils.hasText(value)) {
            throw new PromoValidationException(label + " 為必填");
        }
        if (value.trim().length() > max) {
            throw new PromoValidationException(label + " 超過長度上限 " + max);
        }
    }

    /** 核准：僅 PENDING 可核准 */
    @Transactional
    public PromoProposal approve(Long id) {
        PromoProposal p = requireStatus(id, PromoProposal.STATUS_PENDING, "核准");
        p.setStatus(PromoProposal.STATUS_APPROVED);
        p.setReviewedAt(java.time.OffsetDateTime.now());
        return proposalRepository.save(p);
    }

    /** 拒絕：僅 PENDING 可拒絕；全額退點（此時必未投放） */
    @Transactional
    public PromoProposal reject(Long id, String note) {
        PromoProposal p = requireStatus(id, PromoProposal.STATUS_PENDING, "拒絕");
        p.setStatus(PromoProposal.STATUS_REJECTED);
        p.setReviewNote(note);
        p.setReviewedAt(java.time.OffsetDateTime.now());
        refundRemaining(p);
        return proposalRepository.save(p);
    }

    /** 封存：APPROVED／REJECTED 皆可；退未投放餘額（冪等，已退過不重複） */
    @Transactional
    public PromoProposal archive(Long id) {
        PromoProposal p = proposalRepository.findById(id)
            .orElseThrow(() -> new PromoValidationException("提案不存在：id=" + id));
        if (!PromoProposal.STATUS_APPROVED.equals(p.getStatus())
            && !PromoProposal.STATUS_REJECTED.equals(p.getStatus())) {
            throw new PromoValidationException("狀態 " + p.getStatus() + " 不可封存");
        }
        p.setStatus(PromoProposal.STATUS_ARCHIVED);
        refundRemaining(p);
        return proposalRepository.save(p);
    }

    /**
     * 退還未投放餘額：(quota − used) × unit_cost。
     * 冪等防線：同一提案只會有一筆 PROMO_REFUND——REJECTED 時已退過的，
     * 之後 ARCHIVED 不重複退。
     *
     * <p><b>應用層判斷有競態窗口，DB 唯一索引兜底</b>：{@code existsByPromoProposalIdAndReason}
     * 到 {@code creditTxnRepository.save} 之間並非原子操作，併發雙擊 reject／archive
     * （或 reject 後立刻 archive 的重複請求）理論上可能兩者都通過冪等檢查。
     * V19 的 {@code uq_credit_txn_promo_refund} partial unique index
     * （{@code (promo_proposal_id, reason) WHERE reason = 'PROMO_REFUND'}）是最終防線。</p>
     *
     * <p><b>UNIQUE 撞擊不在此捕捉</b>，理由同 {@code UnlockService.unlock}：
     * {@code save} 觸發 {@link org.springframework.dao.DataIntegrityViolationException} 後
     * 交易已被標記 rollback-only，在方法內捕捉並正常回傳會讓 commit 改拋
     * {@code UnexpectedRollbackException}——必須讓例外往外傳播到交易邊界之外，
     * 由呼叫端（admin 端點）接手，屬罕見併發情境，回 409／500 可接受。</p>
     *
     * @throws org.springframework.dao.DataIntegrityViolationException 併發雙退撞上 UNIQUE
     */
    private void refundRemaining(PromoProposal p) {
        int amount = (p.getPlacementQuota() - p.getPlacementUsed()) * p.getUnitCost();
        if (amount <= 0) return;
        if (creditTxnRepository.existsByPromoProposalIdAndReason(
                p.getId(), CreditTxn.REASON_PROMO_REFUND)) {
            return;
        }
        // 條件式加點是併發防線；回 0 代表讀者不存在
        if (readerRepository.addCredits(p.getReaderId(), amount) == 0) {
            throw new IllegalStateException("退點失敗：讀者不存在 readerId=" + p.getReaderId());
        }
        creditTxnRepository.save(new CreditTxn(p.getReaderId(), amount,
            CreditTxn.REASON_PROMO_REFUND, null, p.getTitle(), p.getId()));
    }

    /** 取出提案並要求目前狀態；不符拋驗證例外 */
    private PromoProposal requireStatus(Long id, String expected, String action) {
        PromoProposal p = proposalRepository.findById(id)
            .orElseThrow(() -> new PromoValidationException("提案不存在：id=" + id));
        if (!expected.equals(p.getStatus())) {
            throw new PromoValidationException(
                "狀態 " + p.getStatus() + " 不可" + action + "（僅 " + expected + " 可）");
        }
        return p;
    }
}
