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

    /**
     * 補發路徑必須略過速度規則直接核准（spec D4）。
     *
     * <p>為什麼這個測試不可省：補發是連續執行、confirmed_at 都落在同一瞬間，
     * 若照跑 assessRisk，任何帶 3 人以上的推薦人會全數落入 PENDING_REVIEW，
     * 等於補發完還要人工按 16 次核准。這裡把速度計數 stub 成必然觸發的值，
     * 斷言補發仍然直接發點。</p>
     */
    @Test
    void backfillSkipsRiskAndApprovesDirectly() {
        // 速度規則門檻是 3，這裡回 9 —— 若補發跑風控必然變 PENDING_REVIEW
        when(conversions.countByReferrerIdAndConfirmedAtAfter(anyLong(), any())).thenReturn(9L);
        OffsetDateTime submittedAt = OffsetDateTime.parse("2026-07-01T10:00:00Z");

        ReferralGrowthService.Outcome outcome =
            service.backfillAndApprove("invitee@example.com", submittedAt);

        assertThat(outcome).isEqualTo(ReferralGrowthService.Outcome.REWARDED);
        ArgumentCaptor<CreditTxn> txn = ArgumentCaptor.forClass(CreditTxn.class);
        verify(credits).saveAndFlush(txn.capture());
        assertThat(txn.getValue().getDelta()).isEqualTo(100);
    }

    /**
     * 補發的轉換時點必須是呼叫端傳入的歷史時間，不是 now()（spec D5）。
     *
     * <p>時點錯不只是資料難看：campaignMultiplier(sourceSlug, now) 用該時間查
     * 當時有效的活動倍率，用 now() 會把今天的倍率套到去年的轉換上，直接發錯點數。</p>
     */
    @Test
    void backfillUsesSuppliedOccurredAtAsConfirmedAt() {
        OffsetDateTime submittedAt = OffsetDateTime.parse("2026-07-01T10:00:00Z");

        service.backfillAndApprove("invitee@example.com", submittedAt);

        ArgumentCaptor<ReferralConversion> saved =
            ArgumentCaptor.forClass(ReferralConversion.class);
        verify(conversions, org.mockito.Mockito.atLeastOnce()).saveAndFlush(saved.capture());
        assertThat(saved.getAllValues())
            .anyMatch(c -> submittedAt.equals(c.getConfirmedAt())
                && ReferralConversion.STATUS_APPROVED.equals(c.getStatus()));
    }

    /** 已經處理過的轉換重跑補發不得再發點（冪等）。 */
    @Test
    void backfillIsIdempotentForAlreadyConfirmedConversion() {
        ReferralConversion existing = new ReferralConversion(
            "invitee@example.com", 7L, "CODE1234", "ai-agent-guide");
        existing.confirm(ReferralConversion.STATUS_APPROVED, 0, "", 100, 1, 100, 20,
            OffsetDateTime.parse("2026-07-01T10:00:00Z"));
        when(conversions.findForUpdate("invitee@example.com")).thenReturn(Optional.of(existing));

        ReferralGrowthService.Outcome outcome = service.backfillAndApprove(
            "invitee@example.com", OffsetDateTime.parse("2026-07-01T10:00:00Z"));

        assertThat(outcome).isEqualTo(ReferralGrowthService.Outcome.ALREADY_PROCESSED);
        verify(credits, never()).saveAndFlush(any(CreditTxn.class));
    }
}
