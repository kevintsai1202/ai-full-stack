package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import world.springai.survey.AdminKeyGuard;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 推薦獎勵補發端點：掃描口徑委派、Outcome 彙總與 dryRun 不寫入。 */
class AdminReferralBackfillTest {

    private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-07-01T10:00:00Z");
    private static final OffsetDateTime T2 = OffsetDateTime.parse("2026-07-02T11:00:00Z");

    private AdminKeyGuard guard;
    private JdbcTemplate jdbc;
    private ReferralGrowthService growth;
    private AdminReferralGrowthController controller;

    /** 兩位候選人的掃描結果，供各案例共用。 */
    @BeforeEach
    void setUp() {
        guard = mock(AdminKeyGuard.class);
        jdbc = mock(JdbcTemplate.class);
        growth = mock(ReferralGrowthService.class);
        controller = new AdminReferralGrowthController(guard, jdbc,
            mock(ReferralConversionRepository.class),
            mock(ReferralCampaignRepository.class), growth);

        when(jdbc.queryForList(anyString())).thenReturn(List.of(
            Map.of("email", "alice@example.com", "occurred_at", T1),
            Map.of("email", "bob@example.com", "occurred_at", T2)));
    }

    /** 正式執行：逐筆帶各自的時點委派，並依 Outcome 彙總計數。 */
    @Test
    void backfillDelegatesPerCandidateAndAggregatesOutcomes() {
        when(growth.backfillAndApprove("alice@example.com", T1))
            .thenReturn(ReferralGrowthService.Outcome.REWARDED);
        when(growth.backfillAndApprove("bob@example.com", T2))
            .thenReturn(ReferralGrowthService.Outcome.ALREADY_PROCESSED);

        Map<String, Object> result = controller.backfill("key", false);

        assertThat(result.get("scanned")).isEqualTo(2);
        assertThat(result.get("rewarded")).isEqualTo(1);
        assertThat(result.get("alreadyProcessed")).isEqualTo(1);
        assertThat(result.get("failed")).isEqualTo(0);
        verify(growth).backfillAndApprove("alice@example.com", T1);
        verify(growth).backfillAndApprove("bob@example.com", T2);
    }

    /** 單筆拋例外不得中斷整批，計入 failed。 */
    @Test
    void backfillCountsFailureAndContinues() {
        when(growth.backfillAndApprove("alice@example.com", T1))
            .thenThrow(new IllegalStateException("boom"));
        when(growth.backfillAndApprove("bob@example.com", T2))
            .thenReturn(ReferralGrowthService.Outcome.REWARDED);

        Map<String, Object> result = controller.backfill("key", false);

        assertThat(result.get("failed")).isEqualTo(1);
        assertThat(result.get("rewarded")).isEqualTo(1);
    }

    /**
     * dryRun 只回名單且 email 必須遮罩，絕不呼叫發獎。
     *
     * <p>fixture 刻意使用多字元 local part（alice/bob）：單字元 local part
     * 會走 {@code maskEmail} 的 {@code at <= 1} 分支直接回傳 {@code "***"}，
     * 那樣驗不到「保留首字、其餘遮蔽」的格式，測試就退化成只證明字串被換掉，
     * 而非證明遮罩格式正確。請勿把 fixture 改回單字元 email。</p>
     */
    @Test
    void dryRunListsMaskedCandidatesWithoutGranting() {
        Map<String, Object> result = controller.backfill("key", true);

        assertThat(result.get("dryRun")).isEqualTo(true);
        assertThat(result.get("scanned")).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates =
            (List<Map<String, Object>>) result.get("candidates");
        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).get("email")).isEqualTo("a***@example.com");
        verify(growth, never()).backfillAndApprove(anyString(), any());
    }

    /**
     * 掃描 SQL 必須保留 consent／unsubscribed 兩道守門（spec §4.2）。
     *
     * <p>這兩個條件是「只對真正成立且未退訂的訂閱發獎」的唯一實作處。
     * 用 mock 的 JdbcTemplate 無法驗證 SQL 語意，但可以驗證條件字串沒被刪掉——
     * 少了它們，補發會對退訂者與未同意者發點，那是合規問題而非小 bug。</p>
     */
    @Test
    void scanSqlKeepsConsentAndUnsubscribedGuards() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        controller.backfill("key", true);

        verify(jdbc).queryForList(sql.capture());
        assertThat(sql.getValue()).contains("sr.consent = true");
        assertThat(sql.getValue()).contains("sr.unsubscribed = false");
        assertThat(sql.getValue()).contains("group by lower(sr.email)");
        assertThat(sql.getValue()).contains("min(sr.created_at)");
    }

    /** 金鑰必須先驗；守衛拋例外時不得掃描。 */
    @Test
    void guardRunsBeforeScanning() {
        org.mockito.Mockito.doThrow(new RuntimeException("401"))
            .when(guard).verify(eq("bad"));

        try {
            controller.backfill("bad", true);
        } catch (RuntimeException expected) {
            // 預期被守衛擋下
        }

        verify(jdbc, never()).queryForList(anyString());
    }

    /**
     * dashboard 必須回傳真實信箱確認數（來自 audience_consent），
     * 與 referral_conversion 的「轉換成立」是兩個不同的指標（spec D1）。
     */
    @Test
    void dashboardExposesConfirmedByLinkAndReferrerStats() {
        // dashboard 內多支 count 查詢共用同一個 stub，回 0 即可；本測試只驗新欄位存在與接線
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
            Map.of("email", "alice@example.com", "clicks", 42, "submissions", 6,
                   "conversions", 6, "rewarded", 600, "badges", 1)));

        Map<String, Object> result = controller.dashboard("key");

        assertThat(result).containsKey("confirmedByLink");
        assertThat(result).containsKey("referrerStats");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stats =
            (List<Map<String, Object>>) result.get("referrerStats");
        assertThat(stats.get(0).get("email")).isEqualTo("a***@example.com");
        assertThat(stats.get(0).get("clicks")).isEqualTo(42);
    }
}
