package world.springai.survey.promo;

import org.springframework.stereotype.Service;
import world.springai.survey.newsletter.Campaign;
import world.springai.survey.newsletter.CampaignRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 工商提案成效統計：以提案為單位彙總各版位的 EMAIL／WEB 通道點擊數，
 * 並以所屬電子報批次的 accepted_count 為分母計算 EMAIL CTR。
 *
 * <p>只統計已定案（COMMITTED）版位——DRAFT 尚未寄出、REMOVED 未刊登，
 * 兩者皆無點擊可言，列入統計只會讓報表出現無意義的 0。</p>
 */
@Service
public class PromoStatsService {

    /** 通道代碼：電子報信件 */
    private static final String CHANNEL_EMAIL = "EMAIL";
    /** 通道代碼：網頁（含 archive 頁的匿名點擊） */
    private static final String CHANNEL_WEB = "WEB";

    private final PromoProposalRepository proposalRepository;
    private final PromoPlacementRepository placementRepository;
    private final PromoClickRepository clickRepository;
    private final CampaignRepository campaignRepository;

    /** 注入提案、版位、點擊與電子報批次 repository */
    public PromoStatsService(PromoProposalRepository proposalRepository,
                             PromoPlacementRepository placementRepository,
                             PromoClickRepository clickRepository,
                             CampaignRepository campaignRepository) {
        this.proposalRepository = proposalRepository;
        this.placementRepository = placementRepository;
        this.clickRepository = clickRepository;
        this.campaignRepository = campaignRepository;
    }

    /**
     * 單一版位的成效彙總。
     *
     * @param emailCtr EMAIL 唯一點擊率；{@code accepted == 0}（尚未寄出或查無批次）時為 {@code null}，
     *                 避免以 0 為分母算出誤導性的 CTR
     */
    public record PlacementStats(long placementId, Long campaignId, String campaignSubject,
                                 long accepted, long emailTotal, long emailUnique,
                                 long webTotal, long webUnique, Double emailCtr) {}

    /** 單一提案的成效彙總：基本資訊＋旗下各已定案版位的明細 */
    public record ProposalStats(long proposalId, String title, String status,
                                int placementQuota, int placementUsed,
                                List<PlacementStats> placements) {}

    /** 彙總全部提案的成效總覽，供後台統計頁使用 */
    public List<ProposalStats> overview() {
        List<ProposalStats> result = new ArrayList<>();
        for (PromoProposal proposal : proposalRepository.findAll()) {
            result.add(summarizeProposal(proposal));
        }
        return result;
    }

    /** 彙總單一提案：只取 COMMITTED 版位，彙整通道點擊並算 CTR */
    private ProposalStats summarizeProposal(PromoProposal proposal) {
        List<PromoPlacement> committed = placementRepository.findByProposalId(proposal.getId())
            .stream()
            .filter(pl -> PromoPlacement.STATUS_COMMITTED.equals(pl.getStatus()))
            .toList();

        Map<Long, List<PromoClickRepository.ChannelStat>> statsByPlacement = fetchClickStats(committed);

        List<PlacementStats> placementStats = new ArrayList<>();
        for (PromoPlacement placement : committed) {
            placementStats.add(summarizePlacement(placement,
                statsByPlacement.getOrDefault(placement.getId(), List.of())));
        }

        return new ProposalStats(proposal.getId(), proposal.getTitle(), proposal.getStatus(),
            proposal.getPlacementQuota(), proposal.getPlacementUsed(), placementStats);
    }

    /** 批次查詢已定案版位的點擊統計，並依 placementId 分組 */
    private Map<Long, List<PromoClickRepository.ChannelStat>> fetchClickStats(List<PromoPlacement> committed) {
        Map<Long, List<PromoClickRepository.ChannelStat>> statsByPlacement = new HashMap<>();
        if (committed.isEmpty()) {
            return statsByPlacement;
        }
        List<Long> placementIds = committed.stream().map(PromoPlacement::getId).toList();
        for (PromoClickRepository.ChannelStat stat : clickRepository.statsForPlacements(placementIds)) {
            statsByPlacement.computeIfAbsent(stat.getPlacementId(), k -> new ArrayList<>()).add(stat);
        }
        return statsByPlacement;
    }

    /** 彙總單一版位：拆分 EMAIL／WEB 通道，並以所屬批次的 accepted_count 算 CTR */
    private PlacementStats summarizePlacement(PromoPlacement placement,
                                              List<PromoClickRepository.ChannelStat> stats) {
        long emailTotal = 0;
        long emailUnique = 0;
        long webTotal = 0;
        long webUnique = 0;
        for (PromoClickRepository.ChannelStat stat : stats) {
            if (CHANNEL_EMAIL.equals(stat.getChannel())) {
                emailTotal = stat.getTotal();
                emailUnique = stat.getUniq();
            } else if (CHANNEL_WEB.equals(stat.getChannel())) {
                webTotal = stat.getTotal();
                webUnique = stat.getUniq();
            }
        }

        Long campaignId = placement.getCampaignId();
        String campaignSubject = null;
        long accepted = 0;
        if (campaignId != null) {
            Optional<Campaign> campaign = campaignRepository.findById(campaignId);
            if (campaign.isPresent()) {
                campaignSubject = campaign.get().getSubject();
                accepted = campaign.get().getAcceptedCount();
            }
        }

        // accepted 為 0（尚未寄出或查無批次）時 CTR 無意義，回 null 而非 0
        Double emailCtr = accepted > 0 ? (double) emailUnique / accepted : null;

        return new PlacementStats(placement.getId(), campaignId, campaignSubject,
            accepted, emailTotal, emailUnique, webTotal, webUnique, emailCtr);
    }
}
