package world.springai.survey.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.AdminKeyGuard;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 後台讀者管理：VIP 授予、手動／批次加點、帳本查詢（spec §7）。
 *
 * <p>VIP 一律由站方手動授予——本系統刻意不做任何金流（spec §2 非目標）。</p>
 *
 * <p><b>每個端點都必須先過 {@link AdminKeyGuard#verify}。</b>漏掉任何一個
 * 就是「任何人都能授予自己 VIP 或無限加點的洞」，而這種漏洞不會在功能測試中
 * 出現——功能測試都會帶金鑰。故測試除了合併檢查，還為每個端點各留一個
 * 未授權測試。</p>
 *
 * <p><b>核心不變式</b>：{@code reader.credits} 永遠等於該讀者所有
 * {@link CreditTxn} 的 delta 總和（餘額永遠可由帳本重算稽核）。本類別是本階段
 * 唯一會主動增加點數的地方，因此每一次餘額變動都與帳本寫入綁在同一個交易內，
 * 且任何「更新回 0 列」的情形一律計為失敗、不寫帳本。</p>
 */
@RestController
public class AdminReaderController {

    private static final Logger log = LoggerFactory.getLogger(AdminReaderController.class);

    /** HTTP Header 名稱：後台金鑰 */
    private static final String KEY_HEADER = "X-Admin-Key";

    /** 帳本說明文字的長度上限，避免超長 note 撐爛後台清單與帳本頁 */
    private static final int MAX_NOTE_LENGTH = 200;

    private final AdminKeyGuard guard;
    private final ReaderRepository readerRepository;
    private final CreditTxnRepository creditTxnRepository;
    private final ReaderAccountService readerAccountService;
    private final CreditPolicy creditPolicy;

    /** 注入金鑰守衛、讀者、帳本、帳戶建立與點數參數 */
    public AdminReaderController(AdminKeyGuard guard,
                                ReaderRepository readerRepository,
                                CreditTxnRepository creditTxnRepository,
                                ReaderAccountService readerAccountService,
                                CreditPolicy creditPolicy) {
        this.guard = guard;
        this.readerRepository = readerRepository;
        this.creditTxnRepository = creditTxnRepository;
        this.readerAccountService = readerAccountService;
        this.creditPolicy = creditPolicy;
    }

    /** VIP 授予請求；days 為 null 時採用 CreditPolicy 的預設效期 */
    public record VipRequest(String email, Integer days) {}

    /** 加點請求；delta 可為負（修正誤加），note 必填供對帳 */
    public record CreditGrantRequest(List<String> emails, Integer delta, String note) {}

    /** 批次加點結果 */
    public record GrantResult(int granted, int failed, List<String> failedEmails) {}

    /** 依 email 片段搜尋讀者 */
    @GetMapping("/api/admin/readers")
    public List<Map<String, Object>> search(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @RequestParam("q") String query) {
        guard.verify(key);
        return readerRepository.findByEmailContainingIgnoreCaseOrderByEmailAsc(query.trim())
            .stream()
            .map(this::toSummary)
            .toList();
    }

    /**
     * 授予或延長 VIP。
     *
     * <p>對還沒有 reader 帳戶的 email 會先建立帳戶——這是實際情境：
     * 課程學員名單匯入後尚未登入過，站方要先把 VIP 設好。若回 404，
     * 站方得請學員先登入一次再回來設定，而那正是最容易漏掉的一步。
     * 建帳戶一律走 {@link ReaderAccountService#findOrCreate}，不自己 new Reader：
     * 那裡才會發初始贈點（連同帳本）並產生邀請碼。</p>
     *
     * <p><b>為什麼要 {@code @Transactional}</b>：findOrCreate 會寫入 reader 與
     * credit_txn 兩張表，接著本方法再改 tier／到期日。放在同一交易裡，
     * 「建了帳戶卻沒設成 VIP」不會半套落地。這裡沒有捕捉任何例外，
     * 所以不會踩到 {@code UnexpectedRollbackException}（對照
     * {@link UnlockController} 刻意不加交易的理由）。</p>
     */
    @PostMapping("/api/admin/readers/vip")
    @Transactional
    public Map<String, Object> grantVip(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @RequestBody VipRequest request) {
        guard.verify(key);

        String email = normalize(request.email());
        // email 空白時若照流程走下去，findOrCreate 會建出一列 email='' 的垃圾讀者
        if (email.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請提供 email");
        }

        int days = request.days() == null ? creditPolicy.vipDefaultDays() : request.days();
        // 0 或負數會產生「授予後立即過期」的 VIP，那不是任何人想要的結果
        if (days <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VIP 天數必須大於 0");
        }

        OffsetDateTime now = OffsetDateTime.now();
        Reader reader = readerRepository.findByEmailIgnoreCase(email)
            .orElseGet(() -> readerAccountService.findOrCreate(email, now));

        reader.setTier(Reader.TIER_VIP);
        reader.setVipExpiresAt(now.plusDays(days));
        Reader saved = readerRepository.save(reader);

        log.info("已授予 VIP：{} 至 {}", saved.getEmail(), saved.getVipExpiresAt());
        return toSummary(saved);
    }

    /**
     * 取消 VIP。
     *
     * <p>只把等級改回 FREE，<b>不刪除讀者列</b>：那一列上有點數餘額、邀請碼與
     * 帳本關聯，刪掉等於銷毀對帳依據。</p>
     *
     * <p>到期日必須一併清掉：留著會讓日後重新授予時在後台看到舊日期而誤判
     * 「這人還是 VIP」。</p>
     */
    @DeleteMapping("/api/admin/readers/vip")
    public Map<String, Object> revokeVip(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @RequestParam("email") String email) {
        guard.verify(key);

        Reader reader = readerRepository.findByEmailIgnoreCase(normalize(email))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "查無此讀者"));
        reader.setTier(Reader.TIER_FREE);
        reader.setVipExpiresAt(null);

        log.info("已取消 VIP：{}", reader.getEmail());
        return toSummary(readerRepository.save(reader));
    }

    /**
     * 批次加點（單筆即長度 1 的陣列）。
     *
     * <p><b>失敗語意：逐筆獨立，不是全有全無。</b>單筆失敗不中斷整批，
     * 回報 granted / failed 與失敗清單——貼一整班學員的名單時，其中一個
     * 打錯字不該讓其他人都拿不到點數。可預期的失敗（查無讀者、餘額不足、
     * 更新回 0 列）只累計計數，不拋例外，因此不會把交易標記成 rollback-only。</p>
     *
     * <p><b>為什麼整批仍包在一個 {@code @Transactional} 裡</b>：餘額變動
     * （{@code addCredits}／{@code deductCredits}，兩者自帶 REQUIRED 交易）與
     * 帳本寫入必須在同一交易內提交，否則「餘額加了但帳本沒寫」會直接破壞
     * 「餘額可由帳本重算」的不變式。單一交易同時讓非預期例外（例如資料庫
     * 連線中斷）整批回滾——餘額與帳本一起回滾，不變式照樣成立。</p>
     */
    @PostMapping("/api/admin/readers/credits")
    @Transactional
    public GrantResult grantCredits(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @RequestBody CreditGrantRequest request) {
        guard.verify(key);

        int delta = request.delta() == null ? 0 : request.delta();
        // delta=0 沒有任何效果，只會在只增不改的帳本裡留下一筆永久的噪音
        if (delta == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "點數變動不得為 0");
        }
        // ADMIN_GRANT 沒有說明就無法對帳：帳本只增不改，事後補不了說明
        if (request.note() == null || request.note().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請填寫加點說明（供日後對帳）");
        }
        String note = request.note().trim();
        if (note.length() > MAX_NOTE_LENGTH) {
            note = note.substring(0, MAX_NOTE_LENGTH);
        }
        if (request.emails() == null || request.emails().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請至少提供一個 email");
        }

        int granted = 0;
        List<String> failed = new ArrayList<>();
        for (String raw : request.emails()) {
            String email = normalize(raw);
            // 貼上的名單常帶空行，空字串直接略過（不計成功也不計失敗）
            if (email.isEmpty()) {
                continue;
            }
            var found = readerRepository.findByEmailIgnoreCase(email);
            if (found.isEmpty()) {
                failed.add(email);
                continue;
            }
            Long readerId = found.get().getId();
            // 扣點走條件式 UPDATE，避免餘額變負——負餘額會讓
            // credits >= cost 永遠為假，讀者連 0 點的提示都看不對
            int affected = delta > 0
                ? readerRepository.addCredits(readerId, delta)
                : readerRepository.deductCredits(readerId, -delta);
            if (affected == 0) {
                // 回 0 列代表「讀者列不存在」或「餘額不足」。絕不可視為成功而照樣寫帳本：
                // 那會留下一筆沒有對應餘額變動的帳本列，reader.credits 再也無法由
                // credit_txn 重算，而且不會有任何錯誤訊息，要等對帳時才會發現。
                log.warn("後台加點失敗：readerId={} delta={} 更新 0 列（讀者不存在或餘額不足）",
                    readerId, delta);
                failed.add(email);
                continue;
            }
            creditTxnRepository.save(new CreditTxn(
                readerId, delta, CreditTxn.REASON_ADMIN_GRANT, null, note));
            granted++;
        }

        log.info("後台加點 {} 點：成功 {} 筆、失敗 {} 筆（{}）", delta, granted, failed.size(), note);
        return new GrantResult(granted, failed.size(), failed);
    }

    /**
     * 某讀者的交易明細（客訴對帳用）。
     *
     * <p>回傳完整帳本、無筆數上限：對帳需要看到全部，不能被顯示上限截掉。
     * {@code note} 在 {@code reason=REFERRAL} 時是被邀者 email——後台看得到
     * 訂閱者資料是正常的，故不遮蔽。</p>
     */
    @GetMapping("/api/admin/readers/ledger")
    public List<CreditTxn> ledger(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @RequestParam("email") String email) {
        guard.verify(key);

        Reader reader = readerRepository.findByEmailIgnoreCase(normalize(email))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "查無此讀者"));
        return creditTxnRepository.findByReaderIdOrderByCreatedAtDesc(reader.getId());
    }

    /**
     * 讀者摘要。
     *
     * <p>{@code vipActive} 以 {@link Reader#isActiveVip} 計算而非直接看 tier：
     * 系統不做自動降級（spec §13.5），資料庫裡會有「tier=VIP 但已過期」的列，
     * 後台若照 tier 顯示會誤判。</p>
     *
     * <p>只放管理需要的欄位：不夾帶任何登入憑證（session / login token 都不在
     * 本表上，但仍以白名單方式逐一放入，避免日後 Reader 新增欄位就自動外洩）。</p>
     */
    private Map<String, Object> toSummary(Reader reader) {
        Map<String, Object> map = new HashMap<>();
        map.put("email", reader.getEmail());
        map.put("tier", reader.getTier());
        map.put("vipActive", reader.isActiveVip(OffsetDateTime.now()));
        map.put("vipExpiresAt", reader.getVipExpiresAt());
        map.put("credits", reader.getCredits());
        map.put("referralCode", reader.getReferralCode());
        map.put("lastLoginAt", reader.getLastLoginAt());
        return map;
    }

    /** email 正規化：去前後空白並轉小寫 */
    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
