package world.springai.survey.form;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.audience.AudiencePlatformService;
import world.springai.survey.promo.PromoRecipientTokenService;
import world.springai.survey.reader.CreditPolicy;
import world.springai.survey.reader.CreditTxn;
import world.springai.survey.reader.CreditTxnRepository;
import world.springai.survey.reader.Reader;
import world.springai.survey.reader.ReaderRepository;
import world.springai.survey.reader.ReaderSessionService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 電子報通道（NEWSLETTER channel）問卷提交服務——spec §3.2「通道感知」衝突解法核心。
 *
 * <p>{@link FormSchemaService#submit} 是訂閱漏斗的一部分：強制要 email、追加 consent
 * 軌跡、寫 legacy {@code survey_response} 列。但信中一鍵題與接續填答頁的填答者未必是
 * 訂閱者，也不該被「填問卷」這個動作意外訂閱。本服務是中性問卷收集器：</p>
 * <ul>
 *   <li>身分完全由後端解析（rt token 或讀者 session），<b>不信任前端傳入的 email</b>；</li>
 *   <li>只寫 audience {@code upsertRecord}／{@code replaceFacts}（source 標記
 *       {@code newsletter_survey}），<b>刻意跳過</b> {@code appendConsent} 與 legacy 寫入；</li>
 *   <li>發點與提交同一交易：任何一步失敗整筆回滾，帳本不變式優先於「已經填完」的體驗，
 *       讀者重送即可。</li>
 * </ul>
 *
 * <p><b>結構上不注入 legacy repository 是刻意設計，不只是「沒呼叫」</b>：本類別的建構子
 * 完全沒有 {@code SurveyResponseRepository} 這個依賴，而不是「注入了但跳過呼叫」——
 * 這樣即使日後有人不小心在本類別新增一段寫入 legacy 的邏輯，編譯期就會被迫先加一個
 * 新的建構子參數，而不能悄悄接上一個「反正已經在手邊」的既有欄位。{@code
 * NewsletterSubmissionServiceTest} 用反射斷言建構子參數不含該型別，把這個結構守衛
 * 釘死在測試上（spec §3.2）。</p>
 */
@Service
public class NewsletterSubmissionService {

    /** 寄送管道：rt token 歸戶（信中一鍵題／轉寄連結點擊進來填答）。 */
    public static final String CHANNEL_EMAIL = "EMAIL";
    /** 寄送管道：讀者 session 歸戶（已登入讀者在接續頁填答）。 */
    public static final String CHANNEL_READER = "READER";
    /** 本服務寫入 audience 的來源代碼；與訂閱漏斗慣用的 {@code survey_form} 區分。 */
    private static final String SOURCE_KEY = "newsletter_survey";

    private final FormSchemaService formSchemaService;
    private final AudiencePlatformService audience;
    private final PromoRecipientTokenService tokenService;
    private final ReaderSessionService sessionService;
    private final ReaderRepository readerRepository;
    private final CreditTxnRepository creditTxnRepository;
    private final CreditPolicy creditPolicy;

    /** 注入表單 schema、名單核心、身分解析與點數相關服務。 */
    public NewsletterSubmissionService(
            FormSchemaService formSchemaService,
            AudiencePlatformService audience,
            PromoRecipientTokenService tokenService,
            ReaderSessionService sessionService,
            ReaderRepository readerRepository,
            CreditTxnRepository creditTxnRepository,
            CreditPolicy creditPolicy) {
        this.formSchemaService = formSchemaService;
        this.audience = audience;
        this.tokenService = tokenService;
        this.sessionService = sessionService;
        this.readerRepository = readerRepository;
        this.creditTxnRepository = creditTxnRepository;
        this.creditPolicy = creditPolicy;
    }

    /** 電子報通道提交要求；campaignId 為觸發提交的電子報活動，rt 為信中收件人 token（可省，改用 session）。 */
    public record SubmitRequest(Map<String, Object> answers, Long campaignId, String rt) {}

    /** 提交結果；rewardHint 為前端可直接顯示的發點狀態說明文字。 */
    public record SubmitResult(String submissionId, boolean rewarded, int rewardCredits, String rewardHint) {}

    /** 後端解析出的填答身分：email 供人物合併與發點反查使用，channel 供 raw 標記寄送管道。 */
    private record Identity(String email, String channel) {}

    /**
     * 提交電子報通道問卷答案。
     *
     * <p>身分解析失敗（無 rt 也無有效 session）拋 401 {@link ResponseStatusException}；
     * 答案格式錯誤（未知欄位／缺必填）由 {@link FormSchemaService#validateAnswers} 拋 400；
     * 解析出的 email 若在停止處理名單上（{@link AudiencePlatformService.SuppressedEmailException}）
     * 轉 409，比照 {@link FormSchemaService#submit} 現行處理——三者都在寫入前發生，
     * 交易不會留下半套資料。</p>
     */
    @Transactional
    public SubmitResult submit(String formKey, SubmitRequest request, String sessionCookie) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Identity identity = resolveIdentity(request.rt(), sessionCookie, now);

        FormSchemaService.FormDefinition form = formSchemaService.getDefinition(formKey, null);
        Map<String, Object> answers = request.answers() == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(request.answers());
        formSchemaService.validateAnswers(form, answers);

        AudiencePlatformService.PersonResult person;
        try {
            person = audience.mergePerson(identity.email(), null, now);
        } catch (AudiencePlatformService.SuppressedEmailException exception) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "此 Email 已要求停止處理，如需重新訂閱請聯絡管理員");
        }
        audience.upsertIdentity(person.personId(), SOURCE_KEY, "email", person.emailNormalized(), now);

        String submissionId = UUID.randomUUID().toString();
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("answers", answers);
        raw.put("campaignId", request.campaignId());
        raw.put("channel", identity.channel());
        AudiencePlatformService.RecordResult record = audience.upsertRecord(
            person.personId(),
            SOURCE_KEY,
            "survey_submission",
            schemaKey(form),
            submissionId,
            now,
            raw,
            Map.of("formTitle", form.title(), "version", form.version()));
        audience.replaceFacts(person.personId(), record.recordId(), SOURCE_KEY, now, facts(form, answers));

        return grantRewardIfEligible(formKey, identity.email(), request.campaignId(), submissionId);
    }

    /** rt 優先解析（信中一鍵題身分），其次讀者 session；兩者皆無效視為未登入，拋 401。 */
    private Identity resolveIdentity(String rt, String sessionCookie, OffsetDateTime now) {
        Optional<String> rtEmail = tokenService.verify(rt);
        if (rtEmail.isPresent() && StringUtils.hasText(rtEmail.get())) {
            return new Identity(rtEmail.get(), CHANNEL_EMAIL);
        }
        Optional<Long> readerId = sessionService.readReaderId(sessionCookie, now);
        if (readerId.isPresent()) {
            Reader reader = readerRepository.findById(readerId.get())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "請先登入"));
            return new Identity(reader.getEmail(), CHANNEL_READER);
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "請先登入或使用信中連結填答");
    }

    /**
     * 發點：email 對應現有讀者且該問卷未曾發過才加點並寫帳本（同一交易，失敗自然回滾）；
     * 已發過或非註冊讀者都照收答案，只是不觸發發點。
     */
    private SubmitResult grantRewardIfEligible(
            String formKey, String email, Long campaignId, String submissionId) {
        Optional<Reader> reader = readerRepository.findByEmailIgnoreCase(email);
        if (reader.isEmpty()) {
            return new SubmitResult(submissionId, false, 0, "填答已收到！訂閱成為讀者即可獲得問卷點數");
        }
        Long readerId = reader.get().getId();
        if (creditTxnRepository.existsByReaderIdAndSurveyFormKeyAndReason(
                readerId, formKey, CreditTxn.REASON_SURVEY_REWARD)) {
            return new SubmitResult(submissionId, false, 0, "此問卷先前已發過點數，不會重複發送");
        }
        int rewardCredits = creditPolicy.surveyReward();
        CreditTxn txn = new CreditTxn(readerId, rewardCredits, CreditTxn.REASON_SURVEY_REWARD, campaignId, null);
        txn.setSurveyFormKey(formKey);
        creditTxnRepository.save(txn);
        // 條件式 UPDATE 回 0 列代表讀者列已不存在（例如帳戶剛好被刪除），
        // 帳本已寫入若靜默放行，reader.credits 與 sum(credit_txn) 就對不起來——
        // 比照 ReferralGrowthService.addCredit 一律拋例外讓交易回滾，不可視為成功。
        if (readerRepository.addCredits(readerId, rewardCredits) == 0) {
            throw new IllegalStateException("問卷發點失敗：readerId=" + readerId);
        }
        return new SubmitResult(submissionId, true, rewardCredits, "感謝填答，已發送 " + rewardCredits + " 點數");
    }

    /** formKey 與 version 組成活動 schemaKey；與 {@link FormSchemaService} 內部邏輯一致。 */
    private String schemaKey(FormSchemaService.FormDefinition form) {
        return form.key() + "@" + form.version();
    }

    /** 依欄位 factKey 建立 typed Fact；未設定時使用 {@code survey.{fieldKey}}，與 {@link FormSchemaService} 一致。 */
    private Map<String, Object> facts(FormSchemaService.FormDefinition form, Map<String, Object> answers) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (FormSchemaService.FieldDefinition field : form.fields()) {
            Object value = answers.get(field.key());
            if (!isMissing(value)) {
                result.put(
                    StringUtils.hasText(field.factKey()) ? field.factKey() : "survey." + field.key(),
                    value);
            }
        }
        return result;
    }

    /** 空字串、空集合與 null 都視為未填寫；與 {@link FormSchemaService} 一致。 */
    private boolean isMissing(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.isBlank();
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        return false;
    }
}
