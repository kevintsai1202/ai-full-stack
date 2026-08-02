package world.springai.survey.promo;

import org.junit.jupiter.api.Test;
import world.springai.survey.newsletter.Campaign;
import world.springai.survey.newsletter.CampaignRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 統計組裝：通道分列、CTR 分母為 accepted_count、無發送時 CTR 為 null */
class PromoStatsServiceTest {

    /** 手工 ChannelStat 樁 */
    private record Stat(Long placementId, String channel, long total, long uniq)
        implements PromoClickRepository.ChannelStat {
        public Long getPlacementId() { return placementId; }
        public String getChannel() { return channel; }
        public long getTotal() { return total; }
        public long getUniq() { return uniq; }
    }

    @Test
    void 通道分列且CTR以accepted為分母() {
        var proposalRepository = mock(PromoProposalRepository.class);
        var placementRepository = mock(PromoPlacementRepository.class);
        var clickRepository = mock(PromoClickRepository.class);
        var campaignRepository = mock(CampaignRepository.class);
        var service = new PromoStatsService(proposalRepository, placementRepository,
            clickRepository, campaignRepository);

        PromoProposal p = new PromoProposal(1L, "王", "a@b.c", "好課",
            "文", "看", "https://example.com", 3, 100);
        org.springframework.test.util.ReflectionTestUtils.setField(p, "id", 9L);
        when(proposalRepository.findAll()).thenReturn(List.of(p));

        PromoPlacement pl = new PromoPlacement(9L);
        org.springframework.test.util.ReflectionTestUtils.setField(pl, "id", 55L);
        pl.setCampaignId(100L);
        pl.setStatus(PromoPlacement.STATUS_COMMITTED);
        when(placementRepository.findByProposalId(9L)).thenReturn(List.of(pl));

        when(clickRepository.statsForPlacements(List.of(55L))).thenReturn(List.of(
            new Stat(55L, "EMAIL", 30, 20),
            new Stat(55L, "WEB", 10, 4)));

        Campaign campaign = mock(Campaign.class);
        when(campaign.getSubject()).thenReturn("第 12 期");
        when(campaign.getAcceptedCount()).thenReturn(200);
        when(campaignRepository.findById(100L)).thenReturn(Optional.of(campaign));

        var stats = service.overview();
        var row = stats.get(0).placements().get(0);
        assertEquals(20, row.emailUnique());
        assertEquals(10, row.webTotal());
        assertEquals(0.10, row.emailCtr(), 1e-9); // 20/200
    }
}
