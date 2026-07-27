package world.springai.survey.newsletter;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.AdminKeyGuard;

import java.time.Instant;
import java.util.List;

/** 同一篇電子報的收件人管理、補寄批次與取消排程 API。 */
@RestController
public class AdminCampaignDeliveryController {

    private final AdminKeyGuard guard;
    private final CampaignDeliveryService service;

    /** 注入管理金鑰守衛與分批寄送服務。 */
    public AdminCampaignDeliveryController(AdminKeyGuard guard, CampaignDeliveryService service) {
        this.guard = guard;
        this.service = service;
    }

    /** 補寄請求；emails 為管理者在收件人頁明確選取的清單。 */
    public record BatchRequest(List<String> emails, String mode, String scheduledAt) {}

    /** 取得某篇電子報的逐收件人狀態。 */
    @GetMapping("/api/admin/campaigns/{campaignId}/recipients")
    public CampaignDeliveryService.RecipientPage recipients(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @PathVariable Long campaignId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "50") Integer size) {
        guard.verify(key);
        return service.recipients(campaignId, status, q, page, size);
    }

    /** 取得某篇電子報的新到舊寄送批次。 */
    @GetMapping("/api/admin/campaigns/{campaignId}/batches")
    public List<CampaignBatch> batches(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @PathVariable Long campaignId) {
        guard.verify(key);
        return service.batches(campaignId);
    }

    /** 建立立即或排程補寄批次。 */
    @PostMapping("/api/admin/campaigns/{campaignId}/batches")
    public CampaignDeliveryService.BatchResult createBatch(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @PathVariable Long campaignId,
            @RequestBody BatchRequest request) {
        guard.verify(key);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少寄送內容");
        }
        Instant scheduledAt = null;
        if (request.scheduledAt() != null && !request.scheduledAt().isBlank()) {
            try {
                scheduledAt = Instant.parse(request.scheduledAt());
            } catch (java.time.format.DateTimeParseException exception) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "scheduledAt 格式錯誤");
            }
        }
        return service.createBatch(
            campaignId, request.emails(), request.mode(), scheduledAt);
    }

    /** 取消尚未到時間的補寄排程批次。 */
    @DeleteMapping("/api/admin/campaigns/{campaignId}/batches/{batchId}")
    public CampaignDeliveryService.CancelResult cancelBatch(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @PathVariable Long campaignId,
            @PathVariable Long batchId) {
        guard.verify(key);
        return service.cancelBatch(campaignId, batchId);
    }
}
