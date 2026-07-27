package world.springai.survey.newsletter;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import world.springai.survey.ReaderSiteLinks;
import world.springai.survey.audience.RecipientService;
import world.springai.survey.audience.SubscriptionLinkBuilder;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.EmailTemplate;
import world.springai.survey.mail.MailQuotaService;
import world.springai.survey.mail.MailSender;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 同篇電子報補寄資格與永久防重複規則測試。 */
class CampaignDeliveryServiceTest {

    /** 已寄出者不可再選；新加入與失敗者會出現在「尚未寄送」篩選。 */
    @Test
    void eligibleFilterIncludesNewAndRetryableButExcludesSent() {
        CampaignRepository campaignRepository = mock(CampaignRepository.class);
        CampaignBatchRepository batchRepository = mock(CampaignBatchRepository.class);
        CampaignRecipientRepository recipientRepository = mock(CampaignRecipientRepository.class);
        RecipientService recipientService = mock(RecipientService.class);
        Campaign campaign = new Campaign(
            "主旨", "內容", "<p>內容</p>", null, null, "now", null, 3, "sent");
        ReflectionTestUtils.setField(campaign, "id", 42L);
        CampaignRecipient sent = recipient("sent@example.com", "SENT");
        CampaignRecipient failed = recipient("failed@example.com", "FAILED");

        when(campaignRepository.findById(42L)).thenReturn(Optional.of(campaign));
        when(batchRepository.existsByCampaignId(42L)).thenReturn(true);
        when(recipientService.recipients(null, null, null, null))
            .thenReturn(List.of("sent@example.com", "failed@example.com", "new@example.com"));
        when(recipientRepository.findByCampaignId(42L)).thenReturn(List.of(sent, failed));

        CampaignDeliveryService service = new CampaignDeliveryService(
            campaignRepository,
            batchRepository,
            recipientRepository,
            mock(EmailLogRepository.class),
            recipientService,
            mock(MailSender.class),
            mock(MailQuotaService.class),
            new EmailTemplate(),
            mock(SubscriptionLinkBuilder.class),
            mock(ReaderSiteLinks.class),
            mock(JdbcTemplate.class));

        CampaignDeliveryService.RecipientPage page =
            service.recipients(42L, "ELIGIBLE", null, 0, 50);

        assertEquals(List.of("new@example.com", "failed@example.com"),
            page.items().stream().map(CampaignDeliveryService.RecipientView::email).toList());
        assertTrue(page.items().getFirst().newlyEligible());
        assertTrue(page.items().get(1).selectable());
        assertFalse(sentView(service).selectable());
    }

    /** 取得已寄出列並驗證永久不可勾選。 */
    private CampaignDeliveryService.RecipientView sentView(CampaignDeliveryService service) {
        return service.recipients(42L, "SENT", null, 0, 50).items().getFirst();
    }

    /** 以反射建立 JPA 唯讀測試資料。 */
    private CampaignRecipient recipient(String email, String status) {
        CampaignRecipient recipient = new CampaignRecipient();
        ReflectionTestUtils.setField(recipient, "campaignId", 42L);
        ReflectionTestUtils.setField(recipient, "email", email);
        ReflectionTestUtils.setField(recipient, "emailNormalized", email);
        ReflectionTestUtils.setField(recipient, "status", status);
        return recipient;
    }
}
