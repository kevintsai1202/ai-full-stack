package world.springai.survey.newsletter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import world.springai.survey.mail.EmailLogRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 文章內容更新：只動內容欄位，不碰計費、slug 與寄出的信件快照 */
class CampaignUpdateContentTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-06T12:00:00+08:00");

    /** 應更新 subject/markdown 並記錄 updated_at */
    @Test
    void updatesContentFields() {
        Campaign campaign = existingCampaign();
        CampaignRepository repo = mock(CampaignRepository.class);
        when(repo.findById(1L)).thenReturn(Optional.of(campaign));
        CampaignMetadataService metadata = mock(CampaignMetadataService.class);

        newService(repo, metadata).updateContent(1L, "新標題", "新內文", "📮", null, List.of("AI"), NOW);

        assertEquals("新標題", campaign.getSubject());
        assertEquals("新內文", campaign.getMarkdown());
        assertNotNull(campaign.getUpdatedAt());
        verify(repo).save(campaign);
    }

    /** 絕不可更動計費、slug 與已寄出信件的 HTML 快照 */
    @Test
    void neverTouchesBillingSlugOrMailSnapshot() {
        Campaign campaign = existingCampaign();
        String originalBody = campaign.getBodyHtml();
        String originalSlug = campaign.getSlug();
        CampaignRepository repo = mock(CampaignRepository.class);
        when(repo.findById(1L)).thenReturn(Optional.of(campaign));

        newService(repo, mock(CampaignMetadataService.class))
            .updateContent(1L, "新標題", "新內文", null, null, List.of(), NOW);

        assertEquals(originalBody, campaign.getBodyHtml(), "信件快照不得變動");
        assertEquals(originalSlug, campaign.getSlug(), "slug 不得變動");
        assertEquals(Campaign.TIER_BASIC, campaign.getTier(), "tier 不得變動");
        assertEquals(12, campaign.getCreditCost(), "解鎖點數不得變動");
    }

    /** 封面與標籤必須交給既有的 metadata 服務，先驗證後更新 */
    @Test
    void delegatesCoverAndTagsToMetadataService() {
        Campaign campaign = existingCampaign();
        CampaignRepository repo = mock(CampaignRepository.class);
        when(repo.findById(1L)).thenReturn(Optional.of(campaign));
        CampaignMetadataService metadata = mock(CampaignMetadataService.class);

        newService(repo, metadata).updateContent(1L, "標題", "內文", "📮", 7L, List.of("AI"), NOW);

        verify(metadata).validate("📮", List.of("AI"), 7L);
        verify(metadata).update(1L, "📮", List.of("AI"), 7L);
    }

    /**
     * 絕不可寫入 email_log——那代表信被重寄了一次。
     * 這是本端點與 reschedule 最關鍵的差異（spec §4.3、§6）。
     */
    @Test
    void neverWritesEmailLog() {
        Campaign campaign = existingCampaign();
        CampaignRepository repo = mock(CampaignRepository.class);
        when(repo.findById(1L)).thenReturn(Optional.of(campaign));
        EmailLogRepository emailLog = mock(EmailLogRepository.class);

        newServiceWithEmailLog(repo, emailLog)
            .updateContent(1L, "新標題", "新內文", null, null, List.of(), NOW);

        verify(emailLog, never()).save(any());
        verify(emailLog, never()).saveAll(any());
    }

    /**
     * campaign 不存在時應回 404，且不得呼叫 metadataService.update
     *（否則會對一篇不存在的文章寫入標籤）。
     */
    @Test
    void campaignNotFoundReturns404AndSkipsMetadataUpdate() {
        CampaignRepository repo = mock(CampaignRepository.class);
        when(repo.findById(99L)).thenReturn(Optional.empty());
        CampaignMetadataService metadata = mock(CampaignMetadataService.class);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> newService(repo, metadata)
                .updateContent(99L, "標題", "內文", null, null, List.of(), NOW));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(metadata, never()).update(anyLong(), any(), any(), any());
    }

    /**
     * metadataService.validate 失敗時不得寫入任何部分更新：validate 必須在
     * save 之前執行，否則封面驗證失敗時會留下「標題已改、封面沒改」的半套更新
     *（測試用 mock repository 不會真的 rollback，鎖的是呼叫順序這個不變量）。
     */
    @Test
    void metadataValidationFailurePreventsPartialUpdate() {
        Campaign campaign = existingCampaign();
        CampaignRepository repo = mock(CampaignRepository.class);
        when(repo.findById(1L)).thenReturn(Optional.of(campaign));
        CampaignMetadataService metadata = mock(CampaignMetadataService.class);
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "封面不合法"))
            .when(metadata).validate(any(), any(), any());

        assertThrows(ResponseStatusException.class,
            () -> newService(repo, metadata)
                .updateContent(1L, "新標題", "新內文", "bad", null, List.of(), NOW));

        verify(repo, never()).save(any());
    }

    /** 建一篇已發布文章：slug/tier/creditCost/bodyHtml 皆為既定值，供各測試共用 */
    private Campaign existingCampaign() {
        Campaign campaign = new Campaign("原標題", "原內文", "<p>original</p>",
            null, null, Campaign.MODE_PUBLISH, null, 0, Campaign.STATUS_PUBLISHED);
        campaign.setSlug("nl-test");
        campaign.setTier(Campaign.TIER_BASIC);
        campaign.setCreditCost(12);
        return campaign;
    }

    /** 建一個只需要 repo 與 metadata 兩個依賴的 CampaignService，其餘依賴一律 mock */
    private CampaignService newService(CampaignRepository repo, CampaignMetadataService metadata) {
        return newServiceWithEmailLog(repo, mock(EmailLogRepository.class), metadata);
    }

    /** 與 newService 相同，但把 EmailLogRepository 換成傳入的 mock，供驗證未被使用 */
    private CampaignService newServiceWithEmailLog(CampaignRepository repo, EmailLogRepository emailLog) {
        return newServiceWithEmailLog(repo, emailLog, mock(CampaignMetadataService.class));
    }

    private CampaignService newServiceWithEmailLog(CampaignRepository repo, EmailLogRepository emailLog,
                                                    CampaignMetadataService metadata) {
        return new CampaignService(
            mock(world.springai.survey.mail.MailSender.class),
            mock(world.springai.survey.audience.RecipientService.class),
            repo,
            emailLog,
            mock(MarkdownRenderer.class),
            mock(world.springai.survey.mail.EmailTemplate.class),
            mock(world.springai.survey.audience.SubscriptionLinkBuilder.class),
            mock(world.springai.survey.mail.MailQuotaService.class),
            mock(ContentSplitter.class),
            mock(world.springai.survey.ReaderSiteLinks.class),
            mock(MailBodyRenderer.class),
            mock(world.springai.survey.promo.PromoPlacementService.class),
            mock(world.springai.survey.promo.PromoRecipientTokenService.class),
            mock(SurveyBlockRenderer.class),
            metadata);
    }
}
