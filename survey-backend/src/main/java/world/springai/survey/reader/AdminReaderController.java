package world.springai.survey.reader;

import org.springframework.http.HttpStatus;
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
 * <p><b>本類別只做三件事：驗金鑰、驗請求格式（400）、委派給
 * {@link AdminReaderService}。</b>所有資料操作與交易都在那個 bean 上，
 * 理由有二：① {@code @Transactional} 只有跨 bean 呼叫才會經過 proxy，寫在
 * controller 自己身上時，日後把交易性程式碼抽成同類別私有方法就會靜默失效；
 * ② 交易必須開在金鑰驗證<b>之後</b>，否則未授權請求也會借走連線並開交易，
 * 連續打就能耗掉連線池。</p>
 */
@RestController
public class AdminReaderController {

    /** HTTP Header 名稱：後台金鑰 */
    private static final String KEY_HEADER = "X-Admin-Key";

    /**
     * 帳本說明文字的長度上限。
     *
     * <p>超過一律回 400 而不是靜默截斷：note 是對帳依據，帳本又只增不改，
     * 被截掉的尾巴永遠補不回來。該丟哪一半要由站方自己決定。</p>
     */
    private static final int MAX_NOTE_LENGTH = 200;

    /**
     * 單次批次加點的 email 筆數上限。
     *
     * <p>沒有上限時，Tomcat 預設 2MB 的 POST 上限約可塞 5 萬筆 email，
     * 等於在單一交易內做 5 萬次 SELECT + UPDATE + INSERT：交易持續數分鐘、
     * 連線與列鎖被長時間佔住，HTTP 逾時後站方也看不到究竟寫進去幾筆
     * （實際上會整批回滾，但站方不知道），失敗清單本身還可能是數 MB 的回應。</p>
     */
    private static final int MAX_BATCH_SIZE = 1000;

    private final AdminKeyGuard guard;
    private final AdminReaderService service;
    private final CreditPolicy creditPolicy;

    /** 注入金鑰守衛、後台讀者管理服務與點數參數 */
    public AdminReaderController(AdminKeyGuard guard,
                                 AdminReaderService service,
                                 CreditPolicy creditPolicy) {
        this.guard = guard;
        this.service = service;
        this.creditPolicy = creditPolicy;
    }

    /** VIP 授予請求；days 為 null 時採用 CreditPolicy 的預設效期 */
    public record VipRequest(String email, Integer days) {}

    /** 加點請求；delta 可為負（修正誤加），note 必填供對帳 */
    public record CreditGrantRequest(List<String> emails, Integer delta, String note) {}

    /** 依 email 片段搜尋讀者 */
    @GetMapping("/api/admin/readers")
    public List<Map<String, Object>> search(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @RequestParam("q") String query) {
        guard.verify(key);

        String fragment = query == null ? "" : query.trim();
        // 空白關鍵字會變成 like '%%'，一次把整張 reader 表（含餘額、邀請碼、
        // 最後登入時間）序列化成單一回應——那不是搜尋，是全表匯出
        if (fragment.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請提供搜尋關鍵字");
        }
        return service.search(fragment);
    }

    /**
     * 授予或延長 VIP。
     *
     * <p>對還沒有 reader 帳戶的 email 會先建立帳戶（不視為登入），
     * 詳見 {@link AdminReaderService#grantVip}。</p>
     */
    @PostMapping("/api/admin/readers/vip")
    public Map<String, Object> grantVip(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @RequestBody VipRequest request) {
        guard.verify(key);

        String email = AdminReaderService.normalizeEmail(request.email());
        // email 空白時若照流程走下去，findOrCreate 會建出一列 email='' 的垃圾讀者
        if (email.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請提供 email");
        }

        int days = request.days() == null ? creditPolicy.vipDefaultDays() : request.days();
        // 0 或負數會產生「授予後立即過期」的 VIP，那不是任何人想要的結果
        if (days <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VIP 天數必須大於 0");
        }

        return service.grantVip(email, days, OffsetDateTime.now());
    }

    /** 取消 VIP；只改等級與到期日，不刪讀者列（見 {@link AdminReaderService#revokeVip}） */
    @DeleteMapping("/api/admin/readers/vip")
    public Map<String, Object> revokeVip(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @RequestParam("email") String email) {
        guard.verify(key);
        return service.revokeVip(AdminReaderService.normalizeEmail(email));
    }

    /**
     * 批次加點（單筆即長度 1 的陣列）。
     *
     * <p>本方法只做請求格式檢查，逐筆加點與帳本寫入見
     * {@link AdminReaderService#grantCredits}。</p>
     */
    @PostMapping("/api/admin/readers/credits")
    public AdminReaderService.GrantResult grantCredits(
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
        // 靜默截斷會讓對帳依據永久少掉後半段，改由站方自己縮短
        if (note.length() > MAX_NOTE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "加點說明請縮短至 " + MAX_NOTE_LENGTH + " 字以內");
        }
        if (request.emails() == null || request.emails().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請至少提供一個 email");
        }
        if (request.emails().size() > MAX_BATCH_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "單次最多 " + MAX_BATCH_SIZE + " 筆 email，請分批送出");
        }

        return service.grantCredits(request.emails(), delta, note);
    }

    /** 某讀者的交易明細（客訴對帳用），回傳完整帳本 */
    @GetMapping("/api/admin/readers/ledger")
    public List<CreditTxn> ledger(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @RequestParam("email") String email) {
        guard.verify(key);
        return service.ledger(AdminReaderService.normalizeEmail(email));
    }
}
