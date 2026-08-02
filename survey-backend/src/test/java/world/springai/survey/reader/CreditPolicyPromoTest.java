package world.springai.survey.reader;

import org.junit.jupiter.api.Test;
import world.springai.survey.AppSettingService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** promoPlacementCost：後備 100、可設 0（免費投放合法）、負值夾 0 */
class CreditPolicyPromoTest {

    private final AppSettingService settings = mock(AppSettingService.class);
    private final CreditPolicy policy = new CreditPolicy(settings);

    @Test
    void 未設定時採後備值100() {
        when(settings.getInt(eq(AppSettingService.CREDIT_PROMO_PLACEMENT_COST), anyInt()))
            .thenAnswer(inv -> inv.getArgument(1));
        assertEquals(100, policy.promoPlacementCost());
    }

    @Test
    void 設0為合法的免費投放() {
        when(settings.getInt(eq(AppSettingService.CREDIT_PROMO_PLACEMENT_COST), anyInt()))
            .thenReturn(0);
        assertEquals(0, policy.promoPlacementCost());
    }

    @Test
    void 負值夾到0() {
        when(settings.getInt(eq(AppSettingService.CREDIT_PROMO_PLACEMENT_COST), anyInt()))
            .thenReturn(-50);
        assertEquals(0, policy.promoPlacementCost());
    }
}
