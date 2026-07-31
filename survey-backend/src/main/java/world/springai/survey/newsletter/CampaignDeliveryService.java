package world.springai.survey.newsletter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.ReaderSiteLinks;
import world.springai.survey.audience.RecipientService;
import world.springai.survey.audience.SubscriptionLinkBuilder;
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.EmailTemplate;
import world.springai.survey.mail.MailQuotaService;
import world.springai.survey.mail.MailSender;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 同一篇電子報的分批寄送、補寄、逐收件人狀態與取消排程。 */
@Service
public class CampaignDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(CampaignDeliveryService.class);
    /** ZSend batch API 的單次上限。 */
    private static final int PROVIDER_BATCH_SIZE = 100;
    /** 收件人列表單頁上限。 */
    private static final int MAX_PAGE_SIZE = 200;

    private final CampaignRepository campaignRepository;
    private final CampaignBatchRepository batchRepository;
    private final CampaignRecipientRepository recipientRepository;
    private final EmailLogRepository emailLogRepository;
    private final RecipientService recipientService;
    private final MailSender mailSender;
    private final MailQuotaService quotaService;
    private final EmailTemplate emailTemplate;
    private final SubscriptionLinkBuilder linkBuilder;
    private final ReaderSiteLinks readerSiteLinks;
    private final JdbcTemplate jdbc;
    /** 信件版內文的唯一產生點：補寄一律由 markdown 重新折疊，不信任存下來的 body_html */
    private final MailBodyRenderer mailBodyRenderer;

    /** 注入寄送、名單、稽核與文章連結依賴。 */
    public CampaignDeliveryService(
            CampaignRepository campaignRepository,
            CampaignBatchRepository batchRepository,
            CampaignRecipientRepository recipientRepository,
            EmailLogRepository emailLogRepository,
            RecipientService recipientService,
            MailSender mailSender,
            MailQuotaService quotaService,
            EmailTemplate emailTemplate,
            SubscriptionLinkBuilder linkBuilder,
            ReaderSiteLinks readerSiteLinks,
            JdbcTemplate jdbc,
            MailBodyRenderer mailBodyRenderer) {
        this.mailBodyRenderer = mailBodyRenderer;
        this.campaignRepository = campaignRepository;
        this.batchRepository = batchRepository;
        this.recipientRepository = recipientRepository;
        this.emailLogRepository = emailLogRepository;
        this.recipientService = recipientService;
        this.mailSender = mailSender;
        this.quotaService = quotaService;
        this.emailTemplate = emailTemplate;
        this.linkBuilder = linkBuilder;
        this.readerSiteLinks = readerSiteLinks;
        this.jdbc = jdbc;
    }

    /** 收件人管理頁的一列。 */
    public record RecipientView(
            Long personId,
            String email,
            String name,
            String status,
            boolean selectable,
            String reason,
            Long batchId,
            OffsetDateTime sentAt,
            OffsetDateTime scheduledAt,
            String error,
            boolean newlyEligible) {}

    /** 收件人管理頁的分頁結果與狀態計數。 */
    public record RecipientPage(
            Long campaignId,
            String subject,
            List<RecipientView> items,
            long total,
            int page,
            int size,
            Map<String, Long> counts) {}

    /** 建立補寄批次的結果。 */
    public record BatchResult(
            Long campaignId,
            Long batchId,
            int requested,
            int accepted,
            int failed,
            int skipped,
            String status) {}

    /** 取消排程批次的結果。 */
    public record CancelResult(Long batchId, int cancelled, int failed) {}

    /**
     * 列出「目前符合寄送條件」與「過去已建立狀態」的聯集。
     * 已寄送與排程中的人不能再選，失敗及取消者在仍符合訂閱條件時可重試。
     */
    public RecipientPage recipients(
            Long campaignId,
            String status,
            String query,
            Integer pageValue,
            Integer sizeValue) {
        Campaign campaign = campaign(campaignId);
        synchronizeInitialBatch(campaign);
        recipientRepository.markElapsedSchedules(campaignId, OffsetDateTime.now(ZoneOffset.UTC));

        int page = pageValue == null ? 0 : pageValue;
        int size = sizeValue == null ? 50 : sizeValue;
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "page 不得小於 0，size 必須介於 1 到 200");
        }

        Set<String> eligible = new LinkedHashSet<>(currentEligible(campaign));
        Map<String, CampaignRecipient> existing = new HashMap<>();
        for (CampaignRecipient row : recipientRepository.findByCampaignId(campaignId)) {
            existing.put(row.getEmailNormalized(), row);
        }
        Map<String, PersonSummary> people = people();

        Set<String> allEmails = new LinkedHashSet<>(eligible);
        allEmails.addAll(existing.keySet());
        String normalizedQuery = normalize(query);
        List<RecipientView> all = new ArrayList<>();
        for (String email : allEmails) {
            CampaignRecipient saved = existing.get(email);
            PersonSummary person = people.get(email);
            boolean currentlyEligible = eligible.contains(email);
            RecipientView view = view(email, person, saved, currentlyEligible);
            if (normalizedQuery != null
                    && !email.contains(normalizedQuery)
                    && (view.name() == null
                        || !view.name().toLowerCase(Locale.ROOT).contains(normalizedQuery))) {
                continue;
            }
            boolean eligibleFilter = "ELIGIBLE".equalsIgnoreCase(status) && view.selectable();
            if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)
                    && !eligibleFilter && !status.equalsIgnoreCase(view.status())) {
                continue;
            }
            all.add(view);
        }
        all.sort(Comparator
            .comparing(RecipientView::selectable).reversed()
            .thenComparing(Comparator.comparing(RecipientView::newlyEligible).reversed())
            .thenComparing(RecipientView::email));

        Map<String, Long> counts = new LinkedHashMap<>();
        for (RecipientView row : all) {
            counts.merge(row.status(), 1L, Long::sum);
        }
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return new RecipientPage(
            campaignId, campaign.getSubject(), List.copyOf(all.subList(from, to)),
            all.size(), page, size, counts);
    }

    /**
     * 建立立即或排程補寄。requestedEmails 必須由管理頁明確送出，
     * 後端仍會重新驗證訂閱狀態與永久寄送狀態。
     */
    public BatchResult createBatch(
            Long campaignId,
            List<String> requestedEmails,
            String mode,
            Instant scheduledAt) {
        Campaign campaign = campaign(campaignId);
        synchronizeInitialBatch(campaign);
        if (!Campaign.TIER_BASIC.equals(campaign.getTier())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "PREMIUM 文章不能將受限全文補寄到信箱");
        }
        boolean scheduled = "schedule".equals(mode);
        if (!scheduled && !"now".equals(mode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode 僅接受 now 或 schedule");
        }
        if (scheduled && (scheduledAt == null || !scheduledAt.isAfter(Instant.now()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "排程時間需為未來");
        }
        List<String> requested = requestedEmails == null
            ? List.of()
            : requestedEmails.stream()
                .map(this::normalize)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (requested.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請至少選擇一位收件人");
        }

        Set<String> eligible = Set.copyOf(currentEligible(campaign));
        List<String> valid = requested.stream().filter(eligible::contains).toList();
        int skipped = requested.size() - valid.size();
        MailQuotaService.Quota quota = quotaService.current();
        int allowed = (int) Math.min(valid.size(), quota.marketingBatchMax());
        if (allowed <= 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "目前沒有可用的行銷寄送額度，請等額度重置");
        }
        skipped += valid.size() - allowed;
        List<String> limited = valid.subList(0, allowed);

        OffsetDateTime scheduledOffset = scheduled
            ? OffsetDateTime.ofInstant(scheduledAt, ZoneOffset.UTC)
            : null;
        CampaignBatch batch = batchRepository.save(new CampaignBatch(
            campaignId, mode, scheduledOffset, scheduled ? "scheduled" : "sending", requested.size()));

        String reserveStatus = scheduled ? "SCHEDULED" : "SENDING";
        List<String> reserved = new ArrayList<>();
        for (String email : limited) {
            int changed = recipientRepository.reserveNew(
                campaignId, batch.getId(), email, reserveStatus, scheduledOffset);
            if (changed == 0) {
                changed = recipientRepository.reserveRetry(
                    campaignId, batch.getId(), email, reserveStatus, scheduledOffset);
            }
            if (changed == 1) {
                reserved.add(email);
            } else {
                skipped++;
            }
        }

        int[] result = scheduled
            ? schedule(campaign, batch, reserved, scheduledAt)
            : sendNow(campaign, batch, reserved);
        int accepted = result[0];
        int failed = result[1];
        String finalStatus = finalStatus(scheduled, accepted, failed);
        batch.complete(finalStatus, accepted, failed, skipped);
        batchRepository.save(batch);
        updateCampaignTotals(campaignId);
        quotaService.invalidate();
        return new BatchResult(
            campaignId, batch.getId(), requested.size(), accepted, failed, skipped, finalStatus);
    }

    /** 取得 campaign 的全部實際寄送批次。 */
    public List<CampaignBatch> batches(Long campaignId) {
        Campaign campaign = campaign(campaignId);
        synchronizeInitialBatch(campaign);
        return batchRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId);
    }

    /** 取消尚未到時間的補寄排程，成功後對應讀者即可再次選取。 */
    public CancelResult cancelBatch(Long campaignId, Long batchId) {
        campaign(campaignId);
        CampaignBatch batch = batchRepository.findById(batchId)
            .filter(row -> campaignId.equals(row.getCampaignId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到寄送批次"));
        if (!"scheduled".equals(batch.getStatus())
                || batch.getScheduledAt() == null
                || !batch.getScheduledAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "此批次已無法取消");
        }
        int cancelled = 0;
        int failed = 0;
        for (CampaignRecipient row : recipientRepository.findByBatchIdOrderByEmailNormalized(batchId)) {
            if (!"SCHEDULED".equals(row.getStatus())) {
                continue;
            }
            try {
                if (mailSender.cancelScheduled(row.getProviderMessageId())) {
                    recipientRepository.markCancelled(row.getId(), batchId);
                    cancelled++;
                } else {
                    failed++;
                }
            } catch (Exception exception) {
                log.warn("取消補寄排程失敗 batchId={} email={}：{}",
                    batchId, row.getEmail(), exception.getMessage());
                failed++;
            }
        }
        for (EmailLog logRow : emailLogRepository.findByBatchIdAndStatus(batchId, "scheduled")) {
            CampaignRecipient recipient = recipientRepository
                .findByCampaignIdAndEmailNormalized(campaignId, normalize(logRow.getRecipient()))
                .orElse(null);
            if (recipient != null && "CANCELLED".equals(recipient.getStatus())) {
                logRow.setStatus("cancelled");
                emailLogRepository.save(logRow);
            }
        }
        batch.cancel(cancelled, failed);
        batchRepository.save(batch);
        updateCampaignTotals(campaignId);
        return new CancelResult(batchId, cancelled, failed);
    }

    /** 立即模式以 provider batch API 每 100 封送出。 */
    private int[] sendNow(Campaign campaign, CampaignBatch batch, List<String> emails) {
        int accepted = 0;
        int failed = 0;
        for (int index = 0; index < emails.size(); index += PROVIDER_BATCH_SIZE) {
            List<String> chunk = emails.subList(
                index, Math.min(index + PROVIDER_BATCH_SIZE, emails.size()));
            List<MailSender.Email> messages = chunk.stream()
                .map(email -> new MailSender.Email(
                    email, campaign.getSubject(), renderFor(campaign, email)))
                .toList();
            try {
                String providerId = mailSender.sendBatch(messages);
                OffsetDateTime sentAt = OffsetDateTime.now(ZoneOffset.UTC);
                for (String email : chunk) {
                    recipientRepository.finishAttempt(
                        campaign.getId(), batch.getId(), email,
                        "SENT", providerId, null, sentAt);
                    saveLog(campaign, batch, email, providerId, "sent", null);
                }
                accepted += chunk.size();
            } catch (Exception exception) {
                log.warn("補寄批次失敗 campaignId={} size={}：{}",
                    campaign.getId(), chunk.size(), exception.getMessage());
                for (String email : chunk) {
                    recipientRepository.finishAttempt(
                        campaign.getId(), batch.getId(), email,
                        "FAILED", null, exception.getMessage(), null);
                    saveLog(campaign, batch, email, null, "failed", exception.getMessage());
                }
                failed += chunk.size();
            }
        }
        return new int[] { accepted, failed };
    }

    /** 排程模式逐封取得可取消的 provider id。 */
    private int[] schedule(
            Campaign campaign,
            CampaignBatch batch,
            List<String> emails,
            Instant scheduledAt) {
        int accepted = 0;
        int failed = 0;
        for (String email : emails) {
            try {
                String providerId = mailSender.schedule(
                    new MailSender.Email(email, campaign.getSubject(), renderFor(campaign, email)),
                    scheduledAt);
                recipientRepository.finishAttempt(
                    campaign.getId(), batch.getId(), email,
                    "SCHEDULED", providerId, null, null);
                saveLog(campaign, batch, email, providerId, "scheduled", null);
                accepted++;
            } catch (Exception exception) {
                recipientRepository.finishAttempt(
                    campaign.getId(), batch.getId(), email,
                    "FAILED", null, exception.getMessage(), null);
                saveLog(campaign, batch, email, null, "failed", exception.getMessage());
                failed++;
            }
        }
        return new int[] { accepted, failed };
    }

    /**
     * 建立含個人退訂連結、文章直達與登入入口的完整信件 HTML。
     *
     * <p><b>內文由 markdown 重新折疊，不使用 {@code campaign.getBodyHtml()}</b>：
     * 那個欄位在折疊功能存在之前寫入的每一列都存著全文，直接重播等於把受限區
     * 補寄出去。而且「重新渲染」也讓這條路徑不必依賴任何寫入端記得先折疊——
     * 正確性由這裡自己保證，不是靠上游的善意。</p>
     */
    private String renderFor(Campaign campaign, String email) {
        String slug = campaign.getSlug();
        String path = slug == null ? "/r/archive" : "/r/news/" + slug;
        String articleLink = slug == null
            ? readerSiteLinks.archive()
            : readerSiteLinks.article(slug);
        return emailTemplate.wrapCampaign(
            mailBodyRenderer.html(campaign.getMarkdown(), slug),
            linkBuilder.unsubscribeLink(email),
            articleLink,
            readerSiteLinks.login(path));
    }

    /** 寫入不可變的逐次寄送稽核紀錄。 */
    private void saveLog(
            Campaign campaign,
            CampaignBatch batch,
            String email,
            String providerId,
            String status,
            String error) {
        EmailLog row = new EmailLog(
            email, campaign.getSubject(), "campaign", providerId, status, error, campaign.getId());
        row.setBatchId(batch.getId());
        emailLogRepository.save(row);
    }

    /**
     * 新 migration 上線後若仍從既有 send 入口建立 campaign，
     * 第一次打開收件人管理頁時把 email_log 同步為第一個 batch。
     */
    private void synchronizeInitialBatch(Campaign campaign) {
        if (batchRepository.existsByCampaignId(campaign.getId())) {
            return;
        }
        List<EmailLog> logs = emailLogRepository.findByCampaignId(campaign.getId());
        if (logs.isEmpty()) {
            return;
        }
        CampaignBatch batch = new CampaignBatch(
            campaign.getId(),
            campaign.getMode() == null ? "legacy" : campaign.getMode(),
            campaign.getScheduledAt(),
            campaign.getStatus(),
            logs.size());
        batch.complete(
            campaign.getStatus(),
            campaign.getAcceptedCount(),
            campaign.getFailedCount(),
            0);
        batch = batchRepository.save(batch);
        for (EmailLog row : logs) {
            row.setBatchId(batch.getId());
            emailLogRepository.save(row);
            String status = switch (row.getStatus()) {
                case "sent" -> "SENT";
                case "scheduled" -> "SCHEDULED";
                case "cancelled" -> "CANCELLED";
                default -> "FAILED";
            };
            recipientRepository.importHistorical(
                campaign.getId(), batch.getId(), row.getRecipient(), status,
                row.getProviderMessageId(), row.getError(),
                "SCHEDULED".equals(status) ? campaign.getScheduledAt() : null,
                "SENT".equals(status) ? row.getCreatedAt() : null,
                row.getCreatedAt() == null ? OffsetDateTime.now(ZoneOffset.UTC) : row.getCreatedAt());
        }
    }

    /** 取得原 campaign 篩選條件當下仍可寄送的 email。 */
    private List<String> currentEligible(Campaign campaign) {
        return recipientService.recipients(
            campaign.getFilterRole(),
            campaign.getFilterInterest(),
            campaign.getFilterJson(),
            campaign.getSavedSegmentId());
    }

    /** 讀者永久狀態與當下訂閱資格合併成 UI 狀態。 */
    private RecipientView view(
            String email,
            PersonSummary person,
            CampaignRecipient saved,
            boolean currentlyEligible) {
        if (saved == null) {
            return new RecipientView(
                person == null ? null : person.id(),
                email,
                person == null ? null : person.name(),
                currentlyEligible ? "ELIGIBLE" : "INELIGIBLE",
                currentlyEligible,
                currentlyEligible ? "尚未寄送" : "目前未確認訂閱或已退訂",
                null, null, null, null, currentlyEligible);
        }
        String savedStatus = saved.getStatus();
        boolean retryable = ("FAILED".equals(savedStatus) || "CANCELLED".equals(savedStatus))
            && currentlyEligible;
        String reason = switch (savedStatus) {
            case "SENT" -> "已寄出（服務商已接受）";
            case "SCHEDULED" -> "已排程，需先取消才能重選";
            case "SENDING" -> "正在寄送";
            case "FAILED" -> currentlyEligible ? "寄送失敗，可重試" : "寄送失敗且目前不可寄送";
            case "CANCELLED" -> currentlyEligible ? "排程已取消，可重新選取" : "排程已取消且目前不可寄送";
            default -> "目前不可寄送";
        };
        return new RecipientView(
            saved.getPersonId(),
            saved.getEmail(),
            person == null ? null : person.name(),
            savedStatus,
            retryable,
            reason,
            saved.getBatchId(),
            saved.getSentAt(),
            saved.getScheduledAt(),
            saved.getError(),
            false);
    }

    /** 名單中心的人物摘要，以正規化 email 建索引供列表補姓名。 */
    private Map<String, PersonSummary> people() {
        Map<String, PersonSummary> result = new HashMap<>();
        jdbc.query("""
            SELECT id, email_normalized, display_name
              FROM audience_person
            """, rs -> {
                result.put(
                    rs.getString("email_normalized"),
                    new PersonSummary(rs.getLong("id"), rs.getString("display_name")));
            });
        return result;
    }

    /** campaign 歷史摘要以永久狀態重新計算。 */
    private void updateCampaignTotals(Long campaignId) {
        int total = Math.toIntExact(recipientRepository.countByCampaignId(campaignId));
        int accepted = Math.toIntExact(
            recipientRepository.countByCampaignIdAndStatus(campaignId, "SENT")
                + recipientRepository.countByCampaignIdAndStatus(campaignId, "SCHEDULED"));
        int failed = Math.toIntExact(
            recipientRepository.countByCampaignIdAndStatus(campaignId, "FAILED"));
        campaignRepository.updateDeliveryTotals(campaignId, total, accepted, failed);
    }

    /** 依 id 取得文章，統一 404。 */
    private Campaign campaign(Long campaignId) {
        return campaignRepository.findById(campaignId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到此電子報"));
    }

    /** email 與搜尋字串的共同正規化。 */
    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /** 批次最終狀態。 */
    private String finalStatus(boolean scheduled, int accepted, int failed) {
        if (accepted == 0 && failed > 0) {
            return "failed";
        }
        if (accepted > 0 && failed > 0) {
            return "partial";
        }
        return scheduled ? "scheduled" : "sent";
    }

    /** 列表所需人物欄位。 */
    private record PersonSummary(Long id, String name) {}
}
