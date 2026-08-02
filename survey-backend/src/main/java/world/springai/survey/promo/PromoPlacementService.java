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
}
