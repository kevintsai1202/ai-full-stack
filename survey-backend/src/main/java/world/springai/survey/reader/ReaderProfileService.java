package world.springai.survey.reader;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.springai.survey.audience.SurveyResponse;
import world.springai.survey.audience.SurveyResponseRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 讀者個人資料的寫入。
 *
 * <p><b>為什麼要獨立成一個 bean，而不是留在 {@code ReaderPortalController} 上</b>：
 * 原本 {@code @Transactional} 掛在 controller 方法上，交易在<b>身分驗證之前</b>就開了——
 * 未帶 cookie 的請求會先向連線池借一條連線、開一個交易，再回 401。而
 * {@code POST /api/reader/profile} 是<b>公開端點</b>（不需 admin key），任何人都打得到，
 * 被大量打時等於用未授權流量消耗連線池。這與 Task 11 判為 Critical 的後台端點是同一個
 * 缺陷，只是這一支更容易被外部觸及。</p>
 *
 * <p><b>{@code @Transactional} 必須落在「被注入的另一個 bean」上</b>：Spring 的交易是
 * 靠 proxy 實作的，同類別內部呼叫（controller 自己呼叫自己的私有方法）不經過 proxy，
 * 註解會<b>靜默失效</b>——沒有錯誤、沒有日誌，只是兩個寫入落到兩個獨立交易。
 * 這也是為什麼必須有 {@code ReaderProfileTransactionTest} 那種會啟動 context 的測試：
 * {@code ReaderPortalControllerTest} 用 {@code standaloneSetup} 直接 new controller，
 * 那裡完全沒有 proxy，交易註解在那份測試中是零覆蓋。</p>
 *
 * <p><b>為什麼兩個寫入必須同一交易</b>：改名（{@code save}）與參與度時間戳
 * （{@code touchEngagement}）描述的是同一次互動。若分成兩個交易而中途失敗，
 * 會出現「名字改了但沒算成互動」或反之，名單中心的參與度分級（spec §5.10）
 * 據此判斷誰還活著，資料半套會讓判斷失真。</p>
 */
@Service
public class ReaderProfileService {

    /** 顯示名稱最長字元數（以 code point 計，避免切斷 surrogate pair） */
    static final int DISPLAY_NAME_MAX_LENGTH = 40;

    private final SurveyResponseRepository surveyResponseRepository;

    /** 注入名單中心資料層 */
    public ReaderProfileService(SurveyResponseRepository surveyResponseRepository) {
        this.surveyResponseRepository = surveyResponseRepository;
    }

    /**
     * 更新某位讀者在名單中心的顯示名稱，並記錄一次參與度。
     *
     * <p>名單中查無此 email 時回 {@code false} 而<b>不建新列</b>：建列會讓「讀者維護
     * 個人資訊」變成「讀者可往名單中心插資料」，而名單中心的每一列都代表一份同意紀錄。</p>
     *
     * <p>名稱只截斷不拒絕：使用者輸入超長名稱時默默存前 40 字比回 400 友善，
     * 而顯示名稱沒有任何正確性要求。</p>
     *
     * @param email 讀者 email（由 session 解析而來，已可信）
     * @param name  使用者輸入的顯示名稱，可為 null
     * @return true 表示已更新；false 表示名單中查無此 email，呼叫端應回 404
     */
    @Transactional
    public boolean updateName(String email, String name) {
        Optional<SurveyResponse> row =
            surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(email);
        if (row.isEmpty()) {
            return false;
        }

        String trimmed = name == null ? "" : name.trim();
        row.get().setName(truncateByCodePoint(trimmed, DISPLAY_NAME_MAX_LENGTH));
        surveyResponseRepository.save(row.get());
        // 更新個人資料是高可靠的參與度訊號（spec §5.10）
        surveyResponseRepository.touchEngagement(email, OffsetDateTime.now());
        return true;
    }

    /**
     * 依 code point（而非 UTF-16 char）截斷字串，避免切在 surrogate pair 中間。
     *
     * <p>{@code String.substring} 以 UTF-16 code unit 計數，若名稱含 4-byte emoji
     * （以代理對表示），切點若正好落在代理對中間，會留下孤立的高代理字元，
     * 寫入 PostgreSQL 時 JDBC 編碼失敗或存成 {@code ?}。</p>
     */
    private static String truncateByCodePoint(String value, int maxLength) {
        if (value.codePointCount(0, value.length()) <= maxLength) {
            return value;
        }
        return value.codePoints()
            .limit(maxLength)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();
    }
}
