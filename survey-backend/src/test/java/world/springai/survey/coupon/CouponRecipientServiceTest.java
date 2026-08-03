package world.springai.survey.coupon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import world.springai.survey.audience.AudienceSearchService;
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CouponRecipientService} 行為測試：以活動快照條件（固定 consent=CONFIRMED）
 * 查命中名單、標記是否已寄過本活動、並驗證外部指定名單是否為命中集合的子集。
 * 純 Mockito 單元測試，mock {@link AudienceSearchService} 與 {@link EmailLogRepository}。
 */
class CouponRecipientServiceTest {

    private AudienceSearchService audienceSearchService;
    private EmailLogRepository emailLogRepository;
    private CouponRecipientService service;

    @BeforeEach
    void setUp() {
        audienceSearchService = mock(AudienceSearchService.class);
        emailLogRepository = mock(EmailLogRepository.class);
        // 預設無寄送記錄；個別測試視需要覆寫
        when(emailLogRepository.findByTypeAndStatus(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(List.of());
        service = new CouponRecipientService(audienceSearchService, emailLogRepository, new ObjectMapper());
    }

    /** 建一筆測試用優惠券活動（狀態、寄送統計對本服務無關） */
    private CouponCampaign campaign(String formKey, String answerFilter) {
        return new CouponCampaign("AI 全端開發", "推薦文案", "https://hahow.in/cr/x",
            "SAVE300", LocalDate.of(2026, 9, 30), formKey, answerFilter);
    }

    /** 建一筆 audience 搜尋結果項目（key 對齊 AudienceSearchService.search 的 item.put 欄位） */
    private Map<String, Object> item(String email, String name) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("email", email);
        row.put("name", name);
        return row;
    }

    /** 反射補上 id，讓 campaign.getId() 有值可供 email_log type=coupon:{id} 判定 */
    private CouponCampaign withId(CouponCampaign campaign, long id) {
        org.springframework.test.util.ReflectionTestUtils.setField(campaign, "id", id);
        return campaign;
    }

    /** resolve 應固定加上 consent=CONFIRMED，且 survey 條件對齊活動快照的 formKey 與解析後的 answers */
    @Test
    void resolveOnlySearchesConfirmedConsentWithSnapshotSurveyFilter() {
        CouponCampaign c = withId(campaign("reader-poll", "{\"pick_topic\":\"RAG\"}"), 1L);
        when(audienceSearchService.search(any())).thenReturn(new AudienceSearchService.SearchResult(
            List.of(item("a@example.com", "小明")), 1, 0, 200, List.of(), Map.of()));

        service.resolve(c);

        ArgumentCaptor<AudienceSearchService.SearchRequest> captor =
            ArgumentCaptor.forClass(AudienceSearchService.SearchRequest.class);
        verify(audienceSearchService).search(captor.capture());
        AudienceSearchService.Filters filters = captor.getValue().filters();
        assertEquals(List.of("CONFIRMED"), filters.consentStatus());
        assertEquals("reader-poll", filters.survey().formKey());
        assertEquals(Map.of("pick_topic", "RAG"), filters.survey().answers());
        // 「填過問卷」只能計真實表單提交來源，不可誤把 dify/exam/名單匯入建立的
        // survey_submission records 也算進命中名單。
        assertEquals(List.of("survey_form", "newsletter_survey"), filters.survey().sources());
    }

    /** 空答案條件 "{}" 應解析為空 Map，而不是拋錯或傳 null 進 SurveyFilter */
    @Test
    void emptyAnswerFilterParsesToEmptyMap() {
        CouponCampaign c = withId(campaign("reader-poll", "{}"), 1L);
        when(audienceSearchService.search(any())).thenReturn(new AudienceSearchService.SearchResult(
            List.of(), 0, 0, 200, List.of(), Map.of()));

        service.resolve(c);

        ArgumentCaptor<AudienceSearchService.SearchRequest> captor =
            ArgumentCaptor.forClass(AudienceSearchService.SearchRequest.class);
        verify(audienceSearchService).search(captor.capture());
        assertEquals(Map.of(), captor.getValue().filters().survey().answers());
    }

    /** alreadySent 由 email_log type=coupon:{id} status=sent 判定，比對大小寫不敏感 */
    @Test
    void alreadySentFlagIsCaseInsensitive() {
        CouponCampaign c = withId(campaign("reader-poll", "{}"), 42L);
        when(audienceSearchService.search(any())).thenReturn(new AudienceSearchService.SearchResult(
            List.of(item("A@Example.com", "小明"), item("b@example.com", "小華")),
            2, 0, 200, List.of(), Map.of()));
        when(emailLogRepository.findByTypeAndStatus("coupon:42", "sent"))
            .thenReturn(List.of(new EmailLog("a@example.com", "主旨", "coupon:42", "m1", "sent", null)));

        List<CouponRecipientService.Recipient> recipients = service.resolve(c);

        assertEquals(2, recipients.size());
        assertTrue(recipients.stream()
            .filter(r -> "A@Example.com".equals(r.email())).findFirst().orElseThrow().alreadySent());
        assertTrue(recipients.stream()
            .filter(r -> "b@example.com".equals(r.email())).findFirst().orElseThrow().alreadySent() == false);
    }

    /**
     * 分頁排序必須帶穩定鍵（email），不能讓 AudienceSearchService 用預設的
     * lastActivityAt（易變欄位）排序——OFFSET 分頁期間若活動時間更新，
     * 會造成跨頁漏筆或重複，進而讓合法收件人被漏收、或 findIllegal 誤判。
     */
    @Test
    void resolveUsesStableSortKeyForPagination() {
        CouponCampaign c = withId(campaign("reader-poll", "{}"), 1L);
        when(audienceSearchService.search(any())).thenReturn(new AudienceSearchService.SearchResult(
            List.of(item("a@example.com", "A")), 1, 0, 200, List.of(), Map.of()));

        service.resolve(c);

        ArgumentCaptor<AudienceSearchService.SearchRequest> captor =
            ArgumentCaptor.forClass(AudienceSearchService.SearchRequest.class);
        verify(audienceSearchService).search(captor.capture());
        AudienceSearchService.Sort sort = captor.getValue().sort();
        assertEquals("email", sort.field(), "應以不可變的 email 欄位排序，避免分頁期間排序位移");
    }

    /** 分頁迴圈：單頁未拉滿 total 時應再拉下一頁，直到累積筆數達 total */
    @Test
    void resolvePaginatesUntilTotalReached() {
        CouponCampaign c = withId(campaign("reader-poll", "{}"), 1L);
        when(audienceSearchService.search(any())).thenReturn(
            new AudienceSearchService.SearchResult(
                List.of(item("a@example.com", "A"), item("b@example.com", "B")), 3, 0, 200, List.of(), Map.of()),
            new AudienceSearchService.SearchResult(
                List.of(item("c@example.com", "C")), 3, 1, 200, List.of(), Map.of()));

        List<CouponRecipientService.Recipient> recipients = service.resolve(c);

        assertEquals(3, recipients.size());
        ArgumentCaptor<AudienceSearchService.SearchRequest> captor =
            ArgumentCaptor.forClass(AudienceSearchService.SearchRequest.class);
        verify(audienceSearchService, times(2)).search(captor.capture());
        assertEquals(0, captor.getAllValues().get(0).page());
        assertEquals(1, captor.getAllValues().get(1).page());
    }

    /** findIllegal：requested 混入不屬於命中集合的外部 email，應回傳該 email */
    @Test
    void findIllegalReturnsEmailsOutsideResolvedSet() {
        CouponCampaign c = withId(campaign("reader-poll", "{}"), 1L);
        when(audienceSearchService.search(any())).thenReturn(new AudienceSearchService.SearchResult(
            List.of(item("a@example.com", "A"), item("b@example.com", "B")), 2, 0, 200, List.of(), Map.of()));

        List<String> illegal = service.findIllegal(c, List.of("a@example.com", "outsider@example.com"));

        assertEquals(List.of("outsider@example.com"), illegal);
    }

    /** findIllegal：requested 全部合法（含大小寫差異）應回空清單 */
    @Test
    void findIllegalReturnsEmptyWhenAllRequestedAreEligible() {
        CouponCampaign c = withId(campaign("reader-poll", "{}"), 1L);
        when(audienceSearchService.search(any())).thenReturn(new AudienceSearchService.SearchResult(
            List.of(item("a@example.com", "A"), item("b@example.com", "B")), 2, 0, 200, List.of(), Map.of()));

        List<String> illegal = service.findIllegal(c, List.of("A@Example.com", "b@example.com"));

        assertEquals(List.of(), illegal);
    }

    /** findIllegal：空 requested 清單視為全部合法，回空清單，且不應觸發搜尋 */
    @Test
    void findIllegalWithEmptyRequestedReturnsEmptyWithoutSearching() {
        CouponCampaign c = withId(campaign("reader-poll", "{}"), 1L);

        List<String> illegal = service.findIllegal(c, List.of());

        assertEquals(List.of(), illegal);
        verify(audienceSearchService, never()).search(any());
    }
}
