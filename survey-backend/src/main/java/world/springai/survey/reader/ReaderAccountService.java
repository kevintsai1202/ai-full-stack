package world.springai.survey.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.springai.survey.audience.SurveyResponseRepository;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * 讀者帳戶的建立與登入紀錄。
 *
 * <p>首次登入時建立帳戶並發放初始贈點；帳戶建立與發點在同一交易內完成，
 * 避免出現「有帳戶但沒有對應帳本紀錄」的不一致狀態。</p>
 */
@Service
public class ReaderAccountService {

    private static final Logger log = LoggerFactory.getLogger(ReaderAccountService.class);

    /**
     * 邀請碼字元集：刻意排除 0/O、1/I/L 等易混淆字元。
     * 讀者會口頭轉述或手抄邀請碼，看錯一個字就換成別人的推薦人。
     */
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    /** 邀請碼長度 */
    private static final int CODE_LENGTH = 8;

    /** 邀請碼碰撞重試上限，避免字元集耗盡時無限迴圈 */
    private static final int MAX_CODE_ATTEMPTS = 10;

    private final SecureRandom random = new SecureRandom();

    private final ReaderRepository readerRepository;
    private final CreditTxnRepository creditTxnRepository;
    private final SurveyResponseRepository surveyResponseRepository;
    private final CreditPolicy creditPolicy;

    /** 注入讀者、帳本、名單中心與點數參數唯一來源 */
    public ReaderAccountService(ReaderRepository readerRepository,
                               CreditTxnRepository creditTxnRepository,
                               SurveyResponseRepository surveyResponseRepository,
                               CreditPolicy creditPolicy) {
        this.readerRepository = readerRepository;
        this.creditTxnRepository = creditTxnRepository;
        this.surveyResponseRepository = surveyResponseRepository;
        this.creditPolicy = creditPolicy;
    }

    /**
     * 取得讀者帳戶，不存在則建立（<b>視為一次登入</b>）。
     *
     * <p>首次建立時發放初始贈點（金額取自可調參數）；既有帳戶不重複發。
     * 無論新舊都更新最後登入時間，並更新名單中心的最後互動時間
     * （登入是高可靠的參與度訊號）。</p>
     */
    @Transactional
    public Reader findOrCreate(String email, OffsetDateTime now) {
        return findOrCreate(email, now, true);
    }

    /**
     * 取得讀者帳戶，不存在則建立，但<b>不視為一次登入</b>（後台代為建帳專用）。
     *
     * <p>建帳的其餘行為與 {@link #findOrCreate} 完全相同——初始贈點、帳本、
     * 邀請碼、推薦歸因一個都不少，所以後台不必（也不該）繞過本服務自己 new Reader。
     * 唯一的差別是<b>不更新 {@code last_login_at}、不呼叫
     * {@code touchEngagement}</b>。</p>
     *
     * <p><b>為什麼要區分</b>：站方為從未登入過的學員設 VIP 時，若沿用登入路徑，
     * 該讀者會立刻在後台顯示「剛剛登入過」，名單中心的參與度時間戳也被推到今天。
     * 參與度是名單評分與再行銷判斷的依據，被後台操作污染之後，站方再也分不出
     * 誰真的來過。</p>
     */
    @Transactional
    public Reader findOrCreateWithoutLogin(String email, OffsetDateTime now) {
        return findOrCreate(email, now, false);
    }

    /**
     * 建帳共用流程。
     *
     * @param asLogin true 才更新 last_login_at 與名單中心的參與度時間戳
     */
    private Reader findOrCreate(String email, OffsetDateTime now, boolean asLogin) {
        String normalized = normalize(email);

        Optional<Reader> existing = readerRepository.findByEmailIgnoreCase(normalized);
        Reader reader = existing.orElseGet(() -> createWithSignupGrant(normalized, now));

        if (!asLogin) {
            return reader;
        }

        reader.setLastLoginAt(now);
        reader = readerRepository.save(reader);

        // 更新名單中心的參與度時間戳；該 email 不在名單中時回 0，屬正常情形
        surveyResponseRepository.touchEngagement(normalized, now);

        return reader;
    }

    /** 建立新帳戶並發放初始贈點；餘額與帳本在同一交易內同步 */
    private Reader createWithSignupGrant(String email, OffsetDateTime now) {
        Reader newReader = new Reader(email, generateUniqueReferralCode());

        // 把名單中心的推薦歸因搬到讀者帳戶（spec §5.4）。
        // 這是「誰邀請了我」的長期紀錄；獎勵發放不看這個欄位，而是看
        // credit_txn 的冪等鍵——兩者職責不同，不要合併。
        surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(email)
            .flatMap(ReferralService::referralCodeOf)
            .flatMap(readerRepository::findByReferralCode)
            // 自我邀請不記錄，與 ReferralService 的判定保持一致
            .filter(referrer -> !referrer.getEmail().equalsIgnoreCase(email))
            .ifPresent(referrer -> newReader.setReferredBy(referrer.getId()));

        Reader reader = readerRepository.save(newReader);

        int grant = creditPolicy.signupGrant();
        // 後台可把贈點調成 0（關閉贈點）；此時不寫帳本，避免留下 delta=0 的無意義紀錄
        if (grant > 0) {
            creditTxnRepository.save(new CreditTxn(
                reader.getId(), grant, CreditTxn.REASON_SIGNUP_GRANT, null, "首次登入初始贈點"));
            reader.setCredits(grant);
        }

        log.info("建立讀者帳戶 {} 並發放初始贈點 {} 點", email, grant);
        return reader;
    }

    /** 產生未被使用的邀請碼；碰撞則重試，超過上限拋例外（幾乎不可能發生） */
    private String generateUniqueReferralCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = randomCode();
            if (!readerRepository.existsByReferralCode(code)) {
                return code;
            }
            log.warn("邀請碼碰撞（第 {} 次嘗試）：{}", attempt + 1, code);
        }
        throw new IllegalStateException("連續 " + MAX_CODE_ATTEMPTS + " 次都無法產生未使用的邀請碼");
    }

    /** 從不含易混淆字元的字元集抽出固定長度的碼 */
    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * email 正規化：去前後空白並轉小寫。
     *
     * <p>{@code Locale.ROOT} 不可省略：土耳其語系（tr-TR）下無參數的
     * {@code toLowerCase()} 會把 {@code I} 轉成 {@code ı}，正規化結果與資料庫裡的
     * email 對不起來，該讀者就此查不到。</p>
     */
    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
