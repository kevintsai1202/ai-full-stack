package world.springai.survey.promo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.springai.survey.newsletter.ContentSplitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 版位生命週期：建立（編輯器插入）、對帳（寄送／發布定案）、歸還（重排／取消）。
 * 關聯真相在 promo_placement；markdown 只是對帳的核對對象（spec §6）。
 */
@Service
public class PromoPlacementService {

    private static final Logger log = LoggerFactory.getLogger(PromoPlacementService.class);

    /** 轉址連結的確定性格式；對帳解析與 snippet 生成共用同一來源（子字串比對，前面加不加網域皆可解析） */
    private static final Pattern PLACEMENT_URL = Pattern.compile("/promo/c/(\\d+)");

    private final PromoPlacementRepository placementRepository;
    private final PromoProposalRepository proposalRepository;
    /**
     * 用來把 snippet 內的連結組成絕對網址。
     *
     * <p><b>優先取讀者站網域（app.reader.base-url）</b>而非 app.public-base-url：
     * reader session cookie 是 host-only（綁讀者網域），工商連結若指向 survey 網域，
     * 讀者在網頁版點擊時瀏覽器不會帶 cookie，登入讀者的點擊會全部降級成匿名、
     * 進不了唯一點擊——違反「網頁版點擊以登入 reader 歸戶」的產品決策。
     * 讀者網域的 {@code ReaderEntryHostFilter} 已放行 {@code GET /promo/c/}；
     * 信件通道靠 rt token 歸戶、與網域無關。未設讀者網域時後備到
     * public-base-url，本機與測試環境相容（後備模式同 {@code ReaderSiteLinks}）。</p>
     */
    private final String publicBaseUrl;
    /** 對帳時偵測 promo 標記是否落在受限區、成對是否正確（spec §6.6，僅警告不擋寄送） */
    private final ContentSplitter contentSplitter;

    /** 對帳/預檢失敗的專屬例外：只讓 {@code ApiExceptionHandler} 精準映射這一類，
     *  不擴大到全域 {@link IllegalStateException}（例如 UnlockService 的既有語意不受影響）。 */
    public static class PromoReconcileException extends IllegalStateException {
        public PromoReconcileException(String message) { super(message); }
    }

    /** 注入版位與提案 repository、組絕對網址所需的對外網址設定，以及切分內文的 ContentSplitter */
    public PromoPlacementService(PromoPlacementRepository placementRepository,
                                 PromoProposalRepository proposalRepository,
                                 @Value("${app.reader.base-url:${app.public-base-url}}") String publicBaseUrl,
                                 ContentSplitter contentSplitter) {
        this.placementRepository = placementRepository;
        this.proposalRepository = proposalRepository;
        this.publicBaseUrl = publicBaseUrl;
        this.contentSplitter = contentSplitter;
    }

    /** 編輯器插入結果：版位 id 與可直接貼進內文的成對 promo 區塊 */
    public record Snippet(long placementId, String markdown) {}

    /** 建立 DRAFT 版位並生成文案快照 snippet；提案須 APPROVED 且配額未滿 */
    @Transactional
    public Snippet createPlacement(Long proposalId) {
        PromoProposal p = proposalRepository.findById(proposalId)
            .orElseThrow(() -> new PromoProposalService.PromoValidationException(
                "提案不存在：id=" + proposalId));
        if (!PromoProposal.STATUS_APPROVED.equals(p.getStatus())) {
            throw new PromoProposalService.PromoValidationException(
                "僅已核准提案可插入，目前狀態：" + p.getStatus());
        }
        if (p.getPlacementUsed() >= p.getPlacementQuota()) {
            throw new PromoProposalService.PromoValidationException(
                "提案投放次數已用罄（" + p.getPlacementUsed() + "/" + p.getPlacementQuota() + "）");
        }
        PromoPlacement placement = placementRepository.save(new PromoPlacement(p.getId()));
        // 文案快照：escape 後落地，審核內容即凍結；連結由欄位生成、不信任文案內語法。
        // 連結一律組成絕對網址（含 publicBaseUrl）——這是信件中唯一的相對網址曾造成兩層斷路：
        // 郵件客戶端沒有 base URL 可補完，且讀者網域的 ReaderEntryHostFilter 也需要完整網址才比對得到路徑。
        String md = "<!--promo-->\n"
            + escapeMarkdown(p.getBodyText()) + "\n\n"
            + "[" + escapeMarkdown(p.getLinkText()) + "](" + publicBaseUrl + "/promo/c/" + placement.getId()
            + "?rt=" + PromoRecipientTokenService.PLACEHOLDER + ")\n"
            + "<!--/promo-->\n";
        return new Snippet(placement.getId(), md);
    }

    /** 跳脫 Markdown 特殊字元：提案文字是純文字，不得讓語法意外生效 */
    static String escapeMarkdown(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if ("\\`*_[]()#!>|".indexOf(c) >= 0) sb.append('\\');
            sb.append(c);
        }
        return sb.toString();
    }

    /** 掃描內文中出現的版位 id（自己生成的確定性 URL），去重保序 */
    static List<Long> parsePlacementIds(String markdown) {
        Set<Long> ids = new LinkedHashSet<>();
        Matcher m = PLACEMENT_URL.matcher(markdown == null ? "" : markdown);
        while (m.find()) {
            ids.add(Long.parseLong(m.group(1)));
        }
        return new ArrayList<>(ids);
    }

    /**
     * 寄送前預檢（不寫入）：讓「擋下」發生在 Campaign 列建立與任何寄信副作用之前。
     * CampaignService 刻意無交易（ZSend 副作用無法回滾），所以順序是
     * 預檢 → 建 Campaign → reconcile → 寄送；預檢失敗時什麼都還沒發生。
     *
     * <p><b>任何 campaignId != null 的版位一律拒絕</b>：呼叫端只有 send／publish，
     * 而這兩者當下 campaign 恆為新建，此刻仍看到既有綁定（不論 COMMITTED 或 REMOVED）
     * 代表這個版位已經對帳過，不該被當成本期未刊登的候選——舊版對 COMMITTED
     * 無條件放行是錯的，會讓「已刊他期」的情況一路撐到 reconcile 才在 Campaign
     * 落地後爆掉，留下殘局。</p>
     *
     * <p>同一提案在同一次寄送若插入多個版位（同提案多次投放），需按提案分組加總
     * 本次需要的投放數，聚合與剩餘配額比較，而非逐版位各自比對——否則兩個各自
     * 「看起來都還有配額」的版位仍可能一起超額。</p>
     */
    public void assertCommittable(String markdown) {
        List<Long> present = parsePlacementIds(markdown);
        // 依提案分組本次要占用的版位，才能算出聚合需求數
        Map<Long, List<Long>> placementIdsByProposal = new LinkedHashMap<>();
        for (Long id : present) {
            PromoPlacement pl = placementRepository.findById(id)
                .orElseThrow(() -> new PromoReconcileException(
                    "工商版位不存在（id=" + id + "），請重新插入提案"));
            if (pl.getCampaignId() != null) {
                throw new PromoReconcileException("工商版位 " + id
                    + " 已綁定過電子報，請刪除該區塊並重新插入提案");
            }
            placementIdsByProposal.computeIfAbsent(pl.getProposalId(), k -> new ArrayList<>()).add(id);
        }
        for (Map.Entry<Long, List<Long>> entry : placementIdsByProposal.entrySet()) {
            PromoProposal p = proposalRepository.findById(entry.getKey())
                .orElseThrow(() -> new PromoReconcileException(
                    "工商提案不存在（proposalId=" + entry.getKey() + "）"));
            int needed = entry.getValue().size();
            int remaining = p.getPlacementQuota() - p.getPlacementUsed();
            if (needed > remaining) {
                throw new PromoReconcileException(
                    "提案「" + p.getTitle() + "」投放次數已用罄，請移除該工商區塊");
            }
        }
    }

    /** 對帳定案：內文出現的 DRAFT → COMMIT＋扣配額；本期已 COMMIT 但消失 → REMOVED＋歸還 */
    @Transactional
    public void reconcile(Long campaignId, String markdown) {
        List<Long> present = parsePlacementIds(markdown);

        for (Long id : present) {
            PromoPlacement pl = placementRepository.findById(id)
                .orElseThrow(() -> new PromoReconcileException(
                    "工商版位不存在（id=" + id + "），請重新插入提案"));
            if (PromoPlacement.STATUS_COMMITTED.equals(pl.getStatus())) {
                if (!campaignId.equals(pl.getCampaignId())) {
                    throw new PromoReconcileException("工商版位 " + id
                        + " 已刊於其他電子報，請刪除該區塊並重新插入提案");
                }
                continue; // 冪等：同期重寄不重複扣
            }
            if (pl.getCampaignId() != null && !campaignId.equals(pl.getCampaignId())) {
                throw new PromoReconcileException("工商版位 " + id
                    + " 屬於其他電子報，請刪除該區塊並重新插入提案");
            }
            // 條件式扣配額是唯一防線：回 0 即擋下，交易回滾已 COMMIT 的同批版位
            if (proposalRepository.consumeQuota(pl.getProposalId()) == 0) {
                PromoProposal p = proposalRepository.findById(pl.getProposalId()).orElse(null);
                throw new PromoReconcileException("提案「"
                    + (p == null ? pl.getProposalId() : p.getTitle())
                    + "」投放次數已用罄，寄送已取消");
            }
            pl.setCampaignId(campaignId);
            pl.setStatus(PromoPlacement.STATUS_COMMITTED);
            pl.setCommittedAt(java.time.OffsetDateTime.now());
            placementRepository.save(pl);
        }

        // 重排情境：先前已綁本期、但新內文已無該連結 → 視為未刊登，歸還配額
        for (PromoPlacement pl : placementRepository.findByCampaignIdAndStatus(
                campaignId, PromoPlacement.STATUS_COMMITTED)) {
            if (!present.contains(pl.getId())) {
                pl.setStatus(PromoPlacement.STATUS_REMOVED);
                placementRepository.save(pl);
                proposalRepository.releaseQuota(pl.getProposalId());
            }
        }

        // spec §6.6：僅警告、不擋寄送——放在定案之後執行，不影響上面的交易結果
        warnIfPlacementOnlyInGatedArea(markdown, present);
        warnIfPromoMarkersUnpaired(markdown);
    }

    /**
     * 偵測「版位連結只出現在受限區」：受限區需要解鎖才看得到，
     * 工商連結放在這裡等於大幅限縮曝光，多半是作者誤把區塊插在付費牆之後。
     * 依 spec §6.6 僅警告，不擋寄送——商業判斷交給編輯自行決定是否要動。
     */
    private void warnIfPlacementOnlyInGatedArea(String markdown, List<Long> present) {
        ContentSplitter.Split split = contentSplitter.split(markdown);
        if (!split.hasGate() || present.isEmpty()) {
            return;
        }
        Set<Long> inFree = new LinkedHashSet<>(parsePlacementIds(split.freeMarkdown()));
        Set<Long> inGated = new LinkedHashSet<>(parsePlacementIds(split.gatedMarkdown()));
        for (Long id : present) {
            if (inGated.contains(id) && !inFree.contains(id)) {
                String title = proposalRepository.findById(
                        placementRepository.findById(id).map(PromoPlacement::getProposalId).orElse(null))
                    .map(PromoProposal::getTitle)
                    .orElse("未知提案");
                log.warn("工商版位僅出現在受限區，曝光將大幅受限：proposal={} placementId={}", title, id);
            }
        }
    }

    /**
     * 偵測 promo 標記（{@code <!--promo-->}／{@code <!--/promo-->}）是否在
     * paywall 兩側（免費區／受限區）各自成對。標記本身只是內容標示，不成對
     * 只代表區塊被 paywall 切斷或作者手動編輯造成缺漏，沿用既有「單邊標記
     * 降級為無害註解」的行為（不影響渲染），依 spec §6.6 僅警告不擋寄送。
     */
    private void warnIfPromoMarkersUnpaired(String markdown) {
        ContentSplitter.Split split = contentSplitter.split(markdown);
        checkPairing(split.freeMarkdown(), "免費區");
        if (split.hasGate()) {
            checkPairing(split.gatedMarkdown(), "受限區");
        }
    }

    /** 計算某一區段內 promo 開合標記數量是否相等，不相等則警告 */
    private void checkPairing(String segment, String areaLabel) {
        int open = countOccurrences(segment, "<!--promo-->");
        int close = countOccurrences(segment, "<!--/promo-->");
        if (open != close) {
            log.warn("工商標記於{}未成對（<!--promo-->={}、<!--/promo-->={}），內容可能被截斷或誤刪",
                areaLabel, open, close);
        }
    }

    /** 計算子字串出現次數（不重疊） */
    private int countOccurrences(String text, String token) {
        if (text == null || text.isEmpty()) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(token, idx)) >= 0) {
            count++;
            idx += token.length();
        }
        return count;
    }

    /** 取消排程：該期全部 COMMITTED 版位歸還配額（信件未寄出，視為未刊登） */
    @Transactional
    public void releaseForCampaign(Long campaignId) {
        for (PromoPlacement pl : placementRepository.findByCampaignIdAndStatus(
                campaignId, PromoPlacement.STATUS_COMMITTED)) {
            pl.setStatus(PromoPlacement.STATUS_REMOVED);
            placementRepository.save(pl);
            proposalRepository.releaseQuota(pl.getProposalId());
        }
    }
}
