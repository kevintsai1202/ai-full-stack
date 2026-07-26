package world.springai.survey.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 後台讀者管理的資料層操作：VIP 授予／取消、批次加點、搜尋、帳本查詢。
 *
 * <p><b>為什麼要從 {@link AdminReaderController} 抽出來成為獨立 bean</b>：
 * ① {@code @Transactional} 只有在<b>跨 bean 呼叫</b>時才會經過 Spring proxy——
 * 寫在 controller 自己身上時，日後任何人把交易性程式碼抽成同類別的私有方法，
 * 註解就會靜默失效；② 交易必須開在<b>金鑰驗證之後</b>。註解掛在 controller 上時，
 * proxy 在進入方法前就開好交易，未授權請求也會借走一條連線並開一次交易，
 * 連續打未授權請求足以耗掉連線池。現在 controller 先 {@code guard.verify}
 * 再呼叫本服務，未授權請求連交易都不會開。</p>
 *
 * <p><b>核心不變式</b>：{@code reader.credits} 永遠等於該讀者所有
 * {@link CreditTxn} 的 delta 總和。因此每一次餘額變動都與帳本寫入綁在同一交易內，
 * 且任何「更新回 0 列」的情形一律計為失敗、不寫帳本。</p>
 */
@Service
public class AdminReaderService {

    private static final Logger log = LoggerFactory.getLogger(AdminReaderService.class);

    /**
     * 搜尋結果筆數上限。
     *
     * <p>比照 {@link CreditTxnRepository} 的「無上限版 vs. 分頁版」慣例：
     * 後台搜尋是找人用的，不是全表匯出。沒有上限時，一個能匹配大量讀者的
     * 關鍵字就會把整張 reader 表序列化成單一回應。</p>
     */
    static final int MAX_SEARCH_RESULTS = 200;

    /** LIKE 樣式的跳脫字元；與 {@link ReaderRepository#searchByEmailPattern} 的 escape 子句一致 */
    private static final char LIKE_ESCAPE = '\\';

    private final ReaderRepository readerRepository;
    private final CreditTxnRepository creditTxnRepository;
    private final ReaderAccountService readerAccountService;

    /** 注入讀者、帳本與帳戶建立服務 */
    public AdminReaderService(ReaderRepository readerRepository,
                              CreditTxnRepository creditTxnRepository,
                              ReaderAccountService readerAccountService) {
        this.readerRepository = readerRepository;
        this.creditTxnRepository = creditTxnRepository;
        this.readerAccountService = readerAccountService;
    }

    /** 批次加點結果 */
    public record GrantResult(int granted, int failed, List<String> failedEmails) {}

    /**
     * 依 email 片段搜尋讀者（不分大小寫）。
     *
     * <p>關鍵字中的 {@code %} 與 {@code _} 會先被跳脫成字面字元，否則
     * {@code ?q=%} 一次就能撈出全表。</p>
     */
    public List<Map<String, Object>> search(String fragment) {
        return readerRepository
            .searchByEmailPattern(likeContainsPattern(fragment), PageRequest.of(0, MAX_SEARCH_RESULTS))
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
     * 建帳戶一律走 {@link ReaderAccountService#findOrCreateWithoutLogin}，
     * 不自己 new Reader：那裡才會發初始贈點（連同帳本）並產生邀請碼。
     * 用「不視為登入」的版本，是因為後台代設 VIP 不是讀者本人的參與行為，
     * 不該偽造 {@code last_login_at} 與名單中心的參與度時間戳。</p>
     *
     * <p><b>為什麼要 {@code @Transactional}</b>：findOrCreateWithoutLogin 會寫入
     * reader 與 credit_txn 兩張表，接著本方法再改 tier／到期日。放在同一交易裡，
     * 「建了帳戶卻沒設成 VIP」不會半套落地。這裡沒有捕捉任何例外，
     * 所以不會踩到 {@code UnexpectedRollbackException}（對照
     * {@link UnlockController} 刻意不加交易的理由）。</p>
     *
     * <p><b>為什麼不 {@code save(reader)}</b>：見
     * {@link ReaderRepository#updateVip} 的說明——整列寫回會靜默覆蓋併發扣點。</p>
     */
    @Transactional
    public Map<String, Object> grantVip(String email, int days, OffsetDateTime now) {
        Reader reader = readerRepository.findByEmailIgnoreCase(email)
            .orElseGet(() -> readerAccountService.findOrCreateWithoutLogin(email, now));

        OffsetDateTime expiresAt = now.plusDays(days);
        int affected = readerRepository.updateVip(reader.getId(), Reader.TIER_VIP, expiresAt);
        if (affected == 0) {
            // 讀者列在查詢與更新之間消失。絕不可回 200 讓站方以為設好了。
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "查無此讀者");
        }

        log.info("已授予 VIP：{} 至 {}", reader.getEmail(), expiresAt);
        return toSummary(reader, Reader.TIER_VIP, expiresAt);
    }

    /**
     * 取消 VIP。
     *
     * <p>只把等級改回 FREE，<b>不刪除讀者列</b>：那一列上有點數餘額、邀請碼與
     * 帳本關聯，刪掉等於銷毀對帳依據。到期日必須一併清掉：留著會讓日後重新授予時
     * 在後台看到舊日期而誤判「這人還是 VIP」。</p>
     *
     * <p><b>為什麼不需要 {@code @Transactional}</b>（與 {@link #grantVip} 的差異）：
     * 這裡不會建立帳戶，唯一的寫入就是 {@link ReaderRepository#updateVip} 那一句
     * 條件式 UPDATE，單一敘述本身即為原子操作（{@code @Modifying} 上的
     * {@code @Transactional} 會替它開一個短交易）。舊寫法之所以有問題，是因為它
     * 「讀出來 → 改 → 整列存回」跨了兩個交易，中間任何併發扣點都會被靜默覆蓋；
     * 改成只寫 tier 與到期日兩欄之後，那個時間窗就不存在了。</p>
     */
    public Map<String, Object> revokeVip(String email) {
        Reader reader = readerRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "查無此讀者"));

        int affected = readerRepository.updateVip(reader.getId(), Reader.TIER_FREE, null);
        if (affected == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "查無此讀者");
        }

        log.info("已取消 VIP：{}", reader.getEmail());
        return toSummary(reader, Reader.TIER_FREE, null);
    }

    /**
     * 批次加點（單筆即長度 1 的陣列）。
     *
     * <p><b>失敗語意：逐筆獨立，不是全有全無。</b>單筆失敗不中斷整批，
     * 回報 granted / failed 與失敗清單——貼一整班學員的名單時，其中一個
     * 打錯字不該讓其他人都拿不到點數。可預期的失敗（查無讀者、餘額不足、
     * 更新回 0 列）只累計計數，不拋例外，因此不會把交易標記成 rollback-only。</p>
     *
     * <p><b>重複的 email 會加兩次點</b>（兩次 UPDATE、兩筆帳本）。這是刻意的：
     * 站方貼同一個 email 兩次時，兩筆帳本各自對應一次真實的餘額變動，不變式仍然成立；
     * 反之若「順手去重」，站方就無法用重複列表達「這個人要加兩份」，而且靜默丟掉
     * 一筆輸入比多加一次更難察覺。</p>
     *
     * <p><b>為什麼整批仍包在一個 {@code @Transactional} 裡</b>：餘額變動
     * （{@code addCredits}／{@code deductCredits}，兩者自帶 REQUIRED 交易）與
     * 帳本寫入必須在同一交易內提交，否則「餘額加了但帳本沒寫」會直接破壞
     * 「餘額可由帳本重算」的不變式。單一交易同時讓非預期例外（例如資料庫
     * 連線中斷）整批回滾——餘額與帳本一起回滾，不變式照樣成立。</p>
     */
    @Transactional
    public GrantResult grantCredits(List<String> emails, int delta, String note) {
        int granted = 0;
        List<String> failed = new ArrayList<>();
        for (String raw : emails) {
            String email = normalizeEmail(raw);
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
    public List<CreditTxn> ledger(String email) {
        Reader reader = readerRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "查無此讀者"));
        return creditTxnRepository.findByReaderIdOrderByCreatedAtDesc(reader.getId());
    }

    /** 讀者摘要，等級與到期日取自實體本身 */
    private Map<String, Object> toSummary(Reader reader) {
        return toSummary(reader, reader.getTier(), reader.getVipExpiresAt());
    }

    /**
     * 讀者摘要。
     *
     * <p>{@code tier} 與 {@code vipExpiresAt} 以參數傳入而非直接讀實體：VIP 的變更
     * 走條件式 UPDATE，實體上的值仍是更新前的舊值（我們刻意不去 setter 它，
     * 否則 Hibernate 的髒檢查又會整列寫回、覆蓋 credits）。</p>
     *
     * <p>{@code vipActive} 以 {@link Reader#isActiveVip} 的規則計算而非直接看 tier：
     * 系統不做自動降級（spec §13.5），資料庫裡會有「tier=VIP 但已過期」的列，
     * 後台若照 tier 顯示會誤判。</p>
     *
     * <p>只放管理需要的欄位：不夾帶任何登入憑證（session / login token 都不在
     * 本表上，但仍以白名單方式逐一放入，避免日後 Reader 新增欄位就自動外洩）。
     * 用 {@link LinkedHashMap} 而非 {@code HashMap}：後者的 JSON 欄位順序取決於
     * 雜湊值，欄位一改順序就跳動，回應難以肉眼比對、也做不了字面快照測試。</p>
     */
    private Map<String, Object> toSummary(Reader reader, String tier, OffsetDateTime vipExpiresAt) {
        OffsetDateTime now = OffsetDateTime.now();
        boolean vipActive = Reader.TIER_VIP.equals(tier)
            && (vipExpiresAt == null || vipExpiresAt.isAfter(now));

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("email", reader.getEmail());
        map.put("tier", tier);
        map.put("vipActive", vipActive);
        map.put("vipExpiresAt", vipExpiresAt);
        map.put("credits", reader.getCredits());
        map.put("referralCode", reader.getReferralCode());
        map.put("lastLoginAt", reader.getLastLoginAt());
        return map;
    }

    /**
     * email 正規化：去前後空白並轉小寫。
     *
     * <p>{@code Locale.ROOT} 不可省略：土耳其語系（tr-TR）下無參數的
     * {@code toLowerCase()} 會把 {@code I} 轉成 {@code ı}，正規化結果與資料庫裡的
     * email 對不起來，該讀者就此查不到。</p>
     */
    static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 把使用者輸入的搜尋關鍵字轉成「包含」語意的 LIKE 樣式。
     *
     * <p>先跳脫跳脫字元本身，再跳脫 {@code %} 與 {@code _}，最後才前後補上真正的
     * 萬用字元。順序不能顛倒：先跳脫 {@code %} 再跳脫 {@code \} 會把剛加上去的
     * 跳脫字元又跳脫一次。</p>
     */
    private static String likeContainsPattern(String fragment) {
        String escaped = fragment
            .replace(String.valueOf(LIKE_ESCAPE), LIKE_ESCAPE + "" + LIKE_ESCAPE)
            .replace("%", LIKE_ESCAPE + "%")
            .replace("_", LIKE_ESCAPE + "_");
        return "%" + escaped + "%";
    }
}
