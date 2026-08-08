package world.springai.survey.newsletter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.AdminKeyGuard;
import world.springai.survey.audience.RecipientService;
import world.springai.survey.mail.MailQuotaService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 補寄確認信端點：額度收斂與 409 守門。 */
class AdminReconfirmEndpointTest {

    private MailQuotaService mailQuotaService;
    private ReconfirmService reconfirmService;
    private AdminCampaignController controller;

    /** 只裝配本測試需要的協作者，其餘傳 mock。 */
    @BeforeEach
    void setUp() {
        mailQuotaService = mock(MailQuotaService.class);
        reconfirmService = mock(ReconfirmService.class);
        controller = new AdminCampaignController(mock(AdminKeyGuard.class),
            mock(CampaignService.class), mock(RecipientService.class),
            mock(InviteService.class), mailQuotaService, reconfirmService);
        when(reconfirmService.sendReconfirmations(any()))
            .thenReturn(new ReconfirmService.ReconfirmResult(0, 0, 0, 0, 0, 0));
    }

    /** 行銷可用額度為 0 時必須回 409，絕不可放行整份名單。 */
    @Test
    void rejectsWhenMarketingQuotaExhausted() {
        when(mailQuotaService.current()).thenReturn(quotaWithMarketingBatchMax(0));

        assertThatThrownBy(() ->
                controller.reconfirm("key", new AdminCampaignController.ReconfirmRequest(null)))
            .isInstanceOf(ResponseStatusException.class);

        verify(reconfirmService, never()).sendReconfirmations(any());
    }

    /**
     * limit 為 null（不限）時必須被收斂成行銷可用上限。
     *
     * <p>把 null 或 0 原樣傳給 ReconfirmService 的後果是「整份名單全寄」，
     * 而且正好發生在額度最吃緊的時候——與意圖完全相反。</p>
     */
    @Test
    void nullLimitIsClampedToMarketingBatchMax() {
        when(mailQuotaService.current()).thenReturn(quotaWithMarketingBatchMax(30));

        controller.reconfirm("key", new AdminCampaignController.ReconfirmRequest(null));

        verify(reconfirmService).sendReconfirmations(eq(30));
    }

    /** 前端送來的 limit 小於上限時原樣採用。 */
    @Test
    void smallerLimitIsPreserved() {
        when(mailQuotaService.current()).thenReturn(quotaWithMarketingBatchMax(30));

        controller.reconfirm("key", new AdminCampaignController.ReconfirmRequest(10));

        verify(reconfirmService).sendReconfirmations(eq(10));
    }

    /** 待補寄人數端點原樣透傳服務的計數。 */
    @Test
    void pendingEndpointReturnsServiceCount() {
        when(reconfirmService.pendingCount()).thenReturn(72);

        assertThat(controller.reconfirmPending("key").get("pending")).isEqualTo(72);
    }

    /**
     * 只有 marketingBatchMax 影響本測試，其餘欄位填中性值。
     *
     * <p>Quota 共 16 個 component：source、status，接著 11 個 long
     * （dailyQuota、dailySent、dailyRemaining、monthlyQuota、monthlySent、
     * monthlyRemaining、remaining、batchMax、reserve、marketingRemaining、
     * marketingBatchMax），最後 overageBillingEnabled、quotaResetAt、monthlyResetAt。</p>
     */
    private static MailQuotaService.Quota quotaWithMarketingBatchMax(long marketingBatchMax) {
        return new MailQuotaService.Quota("fallback", "unknown",
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, marketingBatchMax,
            false, null, null);
    }
}
