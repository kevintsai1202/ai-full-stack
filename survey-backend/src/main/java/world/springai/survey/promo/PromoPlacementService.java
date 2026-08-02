package world.springai.survey.promo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 版位生命週期：建立（編輯器插入）、對帳（寄送／發布定案）、歸還（重排／取消）。
 * 關聯真相在 promo_placement；markdown 只是對帳的核對對象（spec §6）。
 */
@Service
public class PromoPlacementService {

    /** 轉址連結的確定性格式；對帳解析與 snippet 生成共用同一來源 */
    private static final Pattern PLACEMENT_URL = Pattern.compile("/promo/c/(\\d+)");

    private final PromoPlacementRepository placementRepository;
    private final PromoProposalRepository proposalRepository;

    /** 注入版位與提案 repository */
    public PromoPlacementService(PromoPlacementRepository placementRepository,
                                 PromoProposalRepository proposalRepository) {
        this.placementRepository = placementRepository;
        this.proposalRepository = proposalRepository;
    }

    /** 編輯器插入結果：版位 id 與可直接貼進內文的成對 promo 區塊 */
    public record Snippet(long placementId, String markdown) {}

    /** 建立 DRAFT 版位並生成文案快照 snippet；提案須 APPROVED 且配額未滿 */
    @Transactional
    public Snippet createPlacement(Long proposalId) {
        PromoProposal p = proposalRepository.findById(proposalId)
            .orElseThrow(() -> new PromoProposalService.PromoValidationException(
                "提案不存在：id=" + proposalId));
        if (!PromoProposal.STATUS_APPROVED.equals(p.getStatus())) {
            throw new PromoProposalService.PromoValidationException(
                "僅已核准提案可插入，目前狀態：" + p.getStatus());
        }
        if (p.getPlacementUsed() >= p.getPlacementQuota()) {
            throw new PromoProposalService.PromoValidationException(
                "提案投放次數已用罄（" + p.getPlacementUsed() + "/" + p.getPlacementQuota() + "）");
        }
        PromoPlacement placement = placementRepository.save(new PromoPlacement(p.getId()));
        // 文案快照：escape 後落地，審核內容即凍結；連結由欄位生成、不信任文案內語法
        String md = "<!--promo-->\n"
            + escapeMarkdown(p.getBodyText()) + "\n\n"
            + "[" + escapeMarkdown(p.getLinkText()) + "](/promo/c/" + placement.getId()
            + "?rt=" + PromoRecipientTokenService.PLACEHOLDER + ")\n"
            + "<!--/promo-->\n";
        return new Snippet(placement.getId(), md);
    }

    /** 跳脫 Markdown 特殊字元：提案文字是純文字，不得讓語法意外生效 */
    static String escapeMarkdown(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if ("\\`*_[]()#!>|".indexOf(c) >= 0) sb.append('\\');
            sb.append(c);
        }
        return sb.toString();
    }

    /** 掃描內文中出現的版位 id（自己生成的確定性 URL），去重保序 */
    static List<Long> parsePlacementIds(String markdown) {
        Set<Long> ids = new LinkedHashSet<>();
        Matcher m = PLACEMENT_URL.matcher(markdown == null ? "" : markdown);
        while (m.find()) {
            ids.add(Long.parseLong(m.group(1)));
        }
        return new ArrayList<>(ids);
    }

    /**
     * 寄送前預檢（不寫入）：讓「擋下」發生在 Campaign 列建立與任何寄信副作用之前。
     * CampaignService 刻意無交易（ZSend 副作用無法回滾），所以順序是
     * 預檢 → 建 Campaign → reconcile → 寄送；預檢失敗時什麼都還沒發生。
     */
    public void assertCommittable(String markdown) {
        for (Long id : parsePlacementIds(markdown)) {
            PromoPlacement pl = placementRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                    "工商版位不存在（id=" + id + "），請重新插入提案"));
            if (pl.getCampaignId() != null
                && PromoPlacement.STATUS_COMMITTED.equals(pl.getStatus())) {
                continue; // 同 campaign 重寄／重排的冪等情境，reconcile 再驗歸屬
            }
            PromoProposal p = proposalRepository.findById(pl.getProposalId())
                .orElseThrow(() -> new IllegalStateException(
                    "工商提案不存在（placement=" + id + "）"));
            if (p.getPlacementUsed() >= p.getPlacementQuota()) {
                throw new IllegalStateException(
                    "提案「" + p.getTitle() + "」投放次數已用罄，請移除該工商區塊");
            }
        }
    }

    /** 對帳定案：內文出現的 DRAFT → COMMIT＋扣配額；本期已 COMMIT 但消失 → REMOVED＋歸還 */
    @Transactional
    public void reconcile(Long campaignId, String markdown) {
        List<Long> present = parsePlacementIds(markdown);

        for (Long id : present) {
            PromoPlacement pl = placementRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                    "工商版位不存在（id=" + id + "），請重新插入提案"));
            if (PromoPlacement.STATUS_COMMITTED.equals(pl.getStatus())) {
                if (!campaignId.equals(pl.getCampaignId())) {
                    throw new IllegalStateException("工商版位 " + id
                        + " 已刊於其他電子報，請刪除該區塊並重新插入提案");
                }
                continue; // 冪等：同期重寄不重複扣
            }
            if (pl.getCampaignId() != null && !campaignId.equals(pl.getCampaignId())) {
                throw new IllegalStateException("工商版位 " + id
                    + " 屬於其他電子報，請刪除該區塊並重新插入提案");
            }
            // 條件式扣配額是唯一防線：回 0 即擋下，交易回滾已 COMMIT 的同批版位
            if (proposalRepository.consumeQuota(pl.getProposalId()) == 0) {
                PromoProposal p = proposalRepository.findById(pl.getProposalId()).orElse(null);
                throw new IllegalStateException("提案「"
                    + (p == null ? pl.getProposalId() : p.getTitle())
                    + "」投放次數已用罄，寄送已取消");
            }
            pl.setCampaignId(campaignId);
            pl.setStatus(PromoPlacement.STATUS_COMMITTED);
            pl.setCommittedAt(java.time.OffsetDateTime.now());
            placementRepository.save(pl);
        }

        // 重排情境：先前已綁本期、但新內文已無該連結 → 視為未刊登，歸還配額
        for (PromoPlacement pl : placementRepository.findByCampaignIdAndStatus(
                campaignId, PromoPlacement.STATUS_COMMITTED)) {
            if (!present.contains(pl.getId())) {
                pl.setStatus(PromoPlacement.STATUS_REMOVED);
                placementRepository.save(pl);
                proposalRepository.releaseQuota(pl.getProposalId());
            }
        }
    }

    /** 取消排程：該期全部 COMMITTED 版位歸還配額（信件未寄出，視為未刊登） */
    @Transactional
    public void releaseForCampaign(Long campaignId) {
        for (PromoPlacement pl : placementRepository.findByCampaignIdAndStatus(
                campaignId, PromoPlacement.STATUS_COMMITTED)) {
            pl.setStatus(PromoPlacement.STATUS_REMOVED);
            placementRepository.save(pl);
            proposalRepository.releaseQuota(pl.getProposalId());
        }
    }
}
