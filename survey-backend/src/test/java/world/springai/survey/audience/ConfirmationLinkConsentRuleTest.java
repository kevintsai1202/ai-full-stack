package world.springai.survey.audience;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「確認連結」是 appendConsent「狀態未變即略過」規則的唯一例外（C1）。
 *
 * <p><b>為什麼這組測試不可省</b>：讀者在訂閱首頁勾同意送出時，
 * {@code SurveySubmissionService} 當下就寫了一列 CONFIRMED；他之後真的去信箱點
 * 確認連結時狀態相同，舊規則會直接 return false，於是
 * {@code source_key='confirmation-link'} 的列<b>永遠不會存在</b>——後台的
 * 「信箱確認」KPI 與補寄名單的排除條件對所有 consent=true 的訂閱者結構性恆為 0。
 * 這個缺陷從外部完全看不出來（沒有錯誤、沒有 log，只是數字永遠是 0），
 * 只有這裡的斷言擋得住它被改回去。</p>
 */
class ConfirmationLinkConsentRuleTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-08T10:00:00Z");

    private JdbcTemplate jdbc;
    private AudiencePlatformService service;

    /** 每個案例獨立 mock，避免最新狀態的 stub 互相污染。 */
    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new AudiencePlatformService(jdbc, new ObjectMapper().findAndRegisterModules());
    }

    /** 把「該人目前最新 EMAIL 同意狀態」stub 成指定值；空清單代表沒有任何歷史。 */
    @SuppressWarnings("unchecked")
    private void currentStatus(List<String> latest) {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn((List<Object>) (List<?>) latest);
    }

    /** 已是 CONFIRMED 時，確認連結仍要寫入一列（否則 KPI 恆為 0）。 */
    @Test
    void confirmationLinkWritesRowEvenWhenStatusUnchanged() {
        currentStatus(List.of(AudiencePlatformService.CONSENT_CONFIRMED));

        boolean written = service.appendConsent(1L,
            AudiencePlatformService.CONSENT_CONFIRMED,
            AudiencePlatformService.SOURCE_CONFIRMATION_LINK,
            null, Map.of("method", "signed-link"), NOW);

        assertThat(written).isTrue();
        verify(jdbc).update(anyString(), any(Object[].class));
    }

    /** 其他來源維持原本的「狀態未變即略過」，不得因這次放寬而灌出重複列。 */
    @Test
    void otherSourceStillSkipsWhenStatusUnchanged() {
        currentStatus(List.of(AudiencePlatformService.CONSENT_CONFIRMED));

        boolean written = service.appendConsent(1L,
            AudiencePlatformService.CONSENT_CONFIRMED,
            "newsletter", null, Map.of(), NOW);

        assertThat(written).isFalse();
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    /**
     * 已退訂者點了舊確認信不得被改回訂閱狀態——退訂保護優先於確認連結例外。
     *
     * <p>順序若寫反（先處理 confirmation-link 再擋 UNSUBSCRIBED），退訂者的
     * 最新狀態會被一封舊信改成 CONFIRMED，那是合規事故而不是統計誤差。</p>
     */
    @Test
    void unsubscribedIsNotRevivedByConfirmationLink() {
        currentStatus(List.of(AudiencePlatformService.CONSENT_UNSUBSCRIBED));

        boolean written = service.appendConsent(1L,
            AudiencePlatformService.CONSENT_CONFIRMED,
            AudiencePlatformService.SOURCE_CONFIRMATION_LINK,
            null, Map.of("method", "signed-link"), NOW);

        assertThat(written).isFalse();
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    /** 沒有任何歷史時照常寫入（確認連結的例外不影響首次寫入路徑）。 */
    @Test
    void firstConsentIsAlwaysWritten() {
        currentStatus(List.of());

        boolean written = service.appendConsent(1L,
            AudiencePlatformService.CONSENT_CONFIRMED,
            AudiencePlatformService.SOURCE_CONFIRMATION_LINK,
            null, Map.of(), NOW);

        assertThat(written).isTrue();
    }

    /**
     * KPI 與補寄名單的 SQL 口徑必須共用同一份定義（M5）。
     *
     * <p>字串比對看似弱，但它擋的是最實際的失效方式：其中一處被改到、另一處沒跟上，
     * 兩個本應一致的數字從此各說各話而沒有任何測試會紅。</p>
     */
    @Test
    void sharedSqlFragmentsAgreeOnSourceKey() {
        assertThat(AudiencePlatformService.CONFIRMED_BY_LINK_CONDITIONS)
            .contains("c.source_key = '" + AudiencePlatformService.SOURCE_CONFIRMATION_LINK + "'")
            .contains("c.channel = '" + AudiencePlatformService.CHANNEL_EMAIL + "'")
            .contains("c.status = '" + AudiencePlatformService.CONSENT_CONFIRMED + "'");
        assertThat(AudiencePlatformService.CONFIRMED_BY_LINK_COUNT_SQL)
            .contains(AudiencePlatformService.CONFIRMED_BY_LINK_CONDITIONS)
            .contains("count(distinct p.id)");
    }

    /** appendConsentByEmail 查無此人時安全略過，不會誤建陌生人物。 */
    @Test
    void appendConsentByEmailSkipsUnknownPerson() {
        // 不 stub：mock 的 JdbcTemplate 對 List 回傳型別預設就回空清單，即「查無此人」
        boolean written = service.appendConsentByEmail("Ghost@Example.com",
            AudiencePlatformService.CONSENT_CONFIRMED,
            AudiencePlatformService.SOURCE_CONFIRMATION_LINK, Map.of(), NOW);

        assertThat(written).isFalse();
    }
}
