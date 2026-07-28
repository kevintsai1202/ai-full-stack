package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import world.springai.survey.audience.SurveyResponse;
import world.springai.survey.audience.SurveyResponseRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 病毒成長獎勵、風控與里程碑的快速單元測試。 */
class ReferralGrowthServiceTest {

    private SurveyResponseRepository surveyResponses;
    private ReaderRepository readers;
    private CreditTxnRepository credits;
    private ReferralConversionRepository conversions;
    private ReferralBadgeRepository badges;
    private ReferralCampaignRepository campaigns;
    private CreditPolicy policy;
    private ReferralGrowthService service;
    private Reader referrer;

    /** 每個案例建立獨立 mock，避免互相污染風險計數。 */
    @BeforeEach
    void setUp() {
        surveyResponses = mock(SurveyResponseRepository.class);
        readers = mock(ReaderRepository.class);
        credits = mock(CreditTxnRepository.class);
        conversions = mock(ReferralConversionRepository.class);
        badges = mock(ReferralBadgeRepository.class);
        campaigns = mock(ReferralCampaignRepository.class);
        policy = mock(CreditPolicy.class);
        service = new ReferralGrowthService(surveyResponses, readers, credits,
            conversions, badges, campaigns, policy);

        SurveyResponse response = new SurveyResponse();
        response.setEmail("invitee@example.com");
        response.setAnswers(Map.of("_ref", "CODE1234", "_share_article", "ai-agent-guide"));
        referrer = new Reader("owner@example.com", "CODE1234");
        referrer.setId(7L);

        when(surveyResponses.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc("invitee@example.com"))
            .thenReturn(Optional.of(response));
        when(readers.findByReferralCode("CODE1234")).thenReturn(Optional.of(referrer));
        when(readers.findByIdForUpdate(7L)).thenReturn(Optional.of(referrer));
        when(conversions.findForUpdate("invitee@example.com")).thenReturn(Optional.empty());
        when(conversions.saveAndFlush(any(ReferralConversion.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(conversions.save(any(ReferralConversion.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(credits.saveAndFlush(any(CreditTxn.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(readers.addCredits(anyLong(), anyInt())).thenReturn(1);
        when(readers.findByEmailIgnoreCase("invitee@example.com")).thenReturn(Optional.empty());
        when(campaigns.findActiveAt(any())).thenReturn(List.of());
        when(policy.referralReward()).thenReturn(100);
        when(policy.referralInviteeReward()).thenReturn(20);
        when(policy.referralDailyLimit()).thenReturn(10);
        when(policy.referralVelocityThreshold()).thenReturn(3);
        when(conversions.countByReferrerIdAndStatus(anyLong(), anyString())).thenReturn(1L);
    }

    @Test
    void safeConfirmationRewardsReferrer() {
        ReferralGrowthService.Outcome outcome = service.confirmAndReward("invitee@example.com");

        assertThat(outcome).isEqualTo(ReferralGrowthService.Outcome.REWARDED);
        ArgumentCaptor<CreditTxn> txn = ArgumentCaptor.forClass(CreditTxn.class);
        verify(credits).saveAndFlush(txn.capture());
        assertThat(txn.getValue().getReason()).isEqualTo(CreditTxn.REASON_REFERRAL);
        assertThat(txn.getValue().getDelta()).isEqualTo(100);
    }

    @Test
    void dailyLimitMovesConversionToReviewWithoutPaying() {
        when(conversions.countByReferrerIdAndStatusAndConfirmedAtBetween(
            anyLong(), anyString(), any(), any())).thenReturn(10L);

        ReferralGrowthService.Outcome outcome = service.confirmAndReward("invitee@example.com");

        assertThat(outcome).isEqualTo(ReferralGrowthService.Outcome.PENDING_REVIEW);
        verify(credits, never()).saveAndFlush(any());
        verify(readers, never()).addCredits(anyLong(), anyInt());
    }

    @Test
    void activeArticleCampaignMultipliesRewardAndThirdInviteAwardsMilestone() {
        ReferralCampaign campaign = new ReferralCampaign("AI Agent 週", "ai-agent-guide",
            null, 2, OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(1));
        when(campaigns.findActiveAt(any())).thenReturn(List.of(campaign));
        when(conversions.countByReferrerIdAndStatus(7L, ReferralConversion.STATUS_APPROVED))
            .thenReturn(3L);
        when(policy.referralMilestoneReward(3)).thenReturn(50);

        ReferralGrowthService.Outcome outcome = service.confirmAndReward("invitee@example.com");

        assertThat(outcome).isEqualTo(ReferralGrowthService.Outcome.REWARDED);
        ArgumentCaptor<CreditTxn> txns = ArgumentCaptor.forClass(CreditTxn.class);
        verify(credits, org.mockito.Mockito.times(2)).saveAndFlush(txns.capture());
        assertThat(txns.getAllValues()).extracting(CreditTxn::getDelta).containsExactly(200, 50);
        verify(badges).saveAndFlush(any(ReferralBadge.class));
    }
}
