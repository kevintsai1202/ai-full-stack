package world.springai.survey.coupon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import world.springai.survey.audience.SubscriptionLinkBuilder;
import world.springai.survey.form.FormSchemaService;
import world.springai.survey.mail.CouponMailRenderer;
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.MailSender;

/**
 * 優惠券活動寄送迴圈：對後台指定的收件人子集逐封寄出優惠券信。
 *
 * <p>迴圈語意逐字比照 {@code world.springai.survey.newsletter.InviteService#sendInvites}——
 * 已寄集合正規化小寫比對、{@code limit} 截斷 remaining、逐封 {@code try-catch} 不因單封失敗
 * 中斷整批、{@link EmailLog} 成功記 {@code "sent"}／失敗記 {@code "failed"}＋錯誤訊息。
 * 與邀請信不同之處：本服務多一層「後台勾選名單必須是命中集合子集」的驗證
 * （見 {@link CouponRecipientService#findIllegal}），防止外部夾帶未命中名單的 email。</p>
 *
 * <p>{@link #send} 刻意不加 {@code @Transactional}：迴圈夾外部 ZSend 呼叫，且 provider
 * 副作用無法回滾——若迴圈跑完後 {@code campaignRepository.save} 拋例外，整顆交易回滾會把
 * 本輪已成功寫入的 {@link EmailLog}「sent」列一併吃掉，但信已經真的寄出去了，下次重跑會
 * 對同一批人重複寄送。部分失敗時保留已寫入的 email_log 記錄，比整批回滾更誠實（同理見
 * {@code world.springai.survey.newsletter.CampaignService#send} 的 Javadoc）。</p>
 *
 * <p>{@link EmailLog} 在本服務刻意一律用六參建構子（{@code campaignId} 傳 {@code null}）：
 * 該欄位語意是電子報 {@code Campaign}（{@code newsletter} 套件），優惠券活動與寄送記錄的
 * 關聯改以 {@code type="coupon:"+campaignId} 表達，不是遺漏設定 campaignId。</p>
 *
 * <p><b>批內去重</b>：{@link #send} 一開始即對 {@code emails} 依正規化 email 去重（同批同
 * 一人只保留第一次出現），才進入子集驗證與已寄過濾。理由：後台勾選表格理論上不會產生重複
 * email，但前端若有重整、跨分頁重複勾選等邊界情況把同一人送兩次，迴圈若不去重會對同一人
 * 寄兩封信、{@code sent_count} 也會被誤算兩次——這與「已寄過跳過」的冪等意圖矛盾（已寄過的
 * 判斷是跨批次的，批內重複則是同一次呼叫內的，兩者需要分開處理）。</p>
 */
@Service
public class CouponSendService {

    private static final Logger log = LoggerFactory.getLogger(CouponSendService.class);

    /** email_log 已寄狀態字面值 */
    private static final String STATUS_SENT = "sent";
    /** email_log 寄送失敗狀態字面值 */
    private static final String STATUS_FAILED = "failed";

    private final CouponCampaignRepository campaignRepository;
    private final CouponRecipientService recipientService;
    private final CouponMailRenderer mailRenderer;
    private final MailSender mailSender;
    private final EmailLogRepository emailLogRepository;
    private final SubscriptionLinkBuilder linkBuilder;
    private final FormSchemaService formSchemaService;

    /** 注入活動資料層、名單子集驗證、信件渲染、寄信、寄送記錄、退訂連結組裝與表單 schema 服務 */
    public CouponSendService(CouponCampaignRepository campaignRepository,
                              CouponRecipientService recipientService,
                              CouponMailRenderer mailRenderer,
                              MailSender mailSender,
                              EmailLogRepository emailLogRepository,
                              SubscriptionLinkBuilder linkBuilder,
                              FormSchemaService formSchemaService) {
        this.campaignRepository = campaignRepository;
        this.recipientService = recipientService;
        this.mailRenderer = mailRenderer;
        this.mailSender = mailSender;
        this.emailLogRepository = emailLogRepository;
        this.linkBuilder = linkBuilder;
        this.formSchemaService = formSchemaService;
    }

    /**
     * 寄送結果摘要：
     * attempted=本次實際嘗試寄送數、sent=成功數、skipped=已寄過而跳過數、
     * failed=寄送失敗數、remaining=因 limit 未寄的剩餘數
     */
    public record SendResult(int attempted, int sent, int skipped, int failed, int remaining) {}

    /**
     * 對指定活動的收件人子集逐封寄送優惠券信；單封失敗不中斷整批。
     *
     * <p>驗證順序：① 活動存在 ② {@code emails} 為命中名單子集（不合法直接 400、不觸發任何寄信）
     * ③ 已寄過者（email_log type={@code "coupon:"+campaignId} status=sent）自動跳過
     * ④ 扣除已寄後若無任何可寄對象則 400 ⑤ 套用 {@code limit} 截斷。</p>
     *
     * @param campaignId 優惠券活動 id
     * @param emails     後台指定的收件人子集（大小寫不敏感比對）
     * @param limit      單次最多寄送封數，null 或 &le;0 視為不限（配合寄信額度分批寄送）
     */
    public SendResult send(long campaignId, List<String> emails, Integer limit) {
        CouponCampaign campaign = campaignRepository.findById(campaignId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到指定優惠券活動"));

        // 批內去重：同批同一 email（正規化後）只保留第一次出現，避免同一人被誤寄兩封、
        // sent_count 重複累計（見類別 Javadoc「批內去重」說明）
        List<String> requested = dedupeNormalized(emails == null ? List.of() : emails);
        // 子集驗證：後台勾選名單必須全部落在活動命中集合內，否則整批拒絕、不觸發任何寄信
        List<String> illegal = recipientService.findIllegal(campaign, requested);
        if (!illegal.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "以下收件人不在本活動命中名單內，拒絕寄送：" + String.join(", ", illegal));
        }

        String type = "coupon:" + campaignId;
        // 已寄過本活動的 email 集合（正規化為小寫比對）
        Set<String> alreadySent = emailLogRepository.findByTypeAndStatus(type, STATUS_SENT).stream()
            .map(l -> normalize(l.getRecipient()))
            .collect(java.util.stream.Collectors.toSet());
        List<String> eligible = requested.stream()
            .filter(e -> !alreadySent.contains(normalize(e)))
            .toList();
        int skipped = requested.size() - eligible.size();
        if (eligible.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "名單為空或皆已寄送過，無可寄送對象");
        }
        // 套用單次寄送上限；剩餘數留給下次呼叫
        List<String> targets = (limit != null && limit > 0 && limit < eligible.size())
            ? eligible.subList(0, limit) : eligible;
        int remaining = eligible.size() - targets.size();

        // 寄送原因：取該 formKey 最新版問卷標題，查無版本時退回 formKey 原字串（不擋寄送）
        String formTitle = resolveFormTitle(campaign.getFormKey());
        String subject = mailRenderer.subject(campaign);

        int sent = 0;
        int failed = 0;
        for (String email : targets) {
            try {
                String unsubscribeLink = linkBuilder.unsubscribeLink(email);
                String html = mailRenderer.body(campaign, formTitle, unsubscribeLink);
                String id = mailSender.send(email, subject, html);
                emailLogRepository.save(new EmailLog(email, subject, type, id, STATUS_SENT, null));
                sent++;
            } catch (Exception e) {
                log.warn("優惠券信寄送失敗 campaignId={} to={}：{}", campaignId, email, e.getMessage());
                emailLogRepository.save(new EmailLog(email, subject, type, null, STATUS_FAILED, e.getMessage()));
                failed++;
            }
        }

        // 至少一封成功才更新活動狀態；sentAt 只在首次成功寄送時寫入，補寄不覆蓋原始時間
        if (sent > 0) {
            if (campaign.getSentAt() == null) {
                campaign.setSentAt(OffsetDateTime.now());
            }
            campaign.setStatus(CouponCampaign.STATUS_SENT);
            campaign.setSentCount(campaign.getSentCount() + sent);
            campaignRepository.save(campaign);
        }

        return new SendResult(targets.size(), sent, skipped, failed, remaining);
    }

    /** 取該 formKey 最新版本的問卷標題，當作寄送原因顯示；查無任何版本時退回 formKey 原字串 */
    private String resolveFormTitle(String formKey) {
        return formSchemaService.listDefinitions().stream()
            .filter(definition -> definition.key().equals(formKey))
            .max(Comparator.comparingInt(FormSchemaService.FormDefinition::version))
            .map(FormSchemaService.FormDefinition::title)
            .orElse(formKey);
    }

    /** email 正規化：去除頭尾空白並轉小寫，作為大小寫不敏感比對的統一格式 */
    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 依正規化 email 去重，保留每個人第一次出現時的原始字串（維持呼叫端原始大小寫供顯示／寄送）。
     * {@code null} 元素刻意不參與去重（每個 null 都視為獨立元素、全數保留）——讓後續
     * {@link CouponRecipientService#findIllegal} 仍能對每一筆 null 各自判定為不合法，
     * 不因去重而悄悄吃掉應該被攔下的錯誤輸入。
     */
    private List<String> dedupeNormalized(List<String> emails) {
        Set<String> seen = new java.util.LinkedHashSet<>();
        List<String> result = new java.util.ArrayList<>();
        for (String email : emails) {
            String key = email == null ? null : normalize(email);
            if (key == null || seen.add(key)) {
                result.add(email);
            }
        }
        return result;
    }
}
