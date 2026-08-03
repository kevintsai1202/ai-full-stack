package world.springai.survey.form;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import world.springai.survey.form.FormSchemaService.EmailVoteQuestion;
import world.springai.survey.newsletter.CampaignRepository;
import world.springai.survey.promo.PromoRecipientTokenService;
import world.springai.survey.reader.ReaderSessionService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 信中一鍵投票（含讀者頁快投）核心服務：驗證投票目標合法性、依身分歸戶並 upsert 落票。
 *
 * <p><b>身分優先序</b> rt token（RECIPIENT，以收件位址識別）→ session cookie
 * （READER，以讀者 id 識別）→ 匿名（ANON）。具名身分依 V21 的
 * {@code uq_survey_vote_identity} partial unique index 限制為一人一票，重複投票視為
 * 改票（先查既有列再 upsert 覆蓋）；匿名不受此限制，每次都新增一列。</p>
 *
 * <p><b>落票失敗不擋轉址</b>：投票統計是輔助數據，讀者順利跳轉到接續頁才是主體驗，
 * 記錄失敗只寫 log 讓監控看到，沿用 {@code PromoClickService} 的 best-effort 哲學。</p>
 */
@Service
public class SurveyVoteService {

    private static final Logger log = LoggerFactory.getLogger(SurveyVoteService.class);

    private final FormSchemaService formSchemaService;
    private final SurveyVoteRepository voteRepository;
    private final PromoRecipientTokenService tokenService;
    private final ReaderSessionService sessionService;
    private final CampaignRepository campaignRepository;

    /** 注入表單 schema、投票資料層、token／session 歸戶服務與活動存在性檢查 */
    public SurveyVoteService(FormSchemaService formSchemaService,
                             SurveyVoteRepository voteRepository,
                             PromoRecipientTokenService tokenService,
                             ReaderSessionService sessionService,
                             CampaignRepository campaignRepository) {
        this.formSchemaService = formSchemaService;
        this.voteRepository = voteRepository;
        this.tokenService = tokenService;
        this.sessionService = sessionService;
        this.campaignRepository = campaignRepository;
    }

    /** 驗證＋落票；empty＝目標不合法（controller 轉 404，不洩漏 schema 細節），present＝接續頁 redirect 路徑 */
    public Optional<String> vote(String formKey, String fieldKey, int optionIndex,
                                 Long campaignId, String rt, String sessionCookie) {
        Optional<EmailVoteQuestion> question = formSchemaService.emailVoteQuestion(formKey);
        if (question.isEmpty() || !question.get().fieldKey().equals(fieldKey)
            || optionIndex < 0 || optionIndex >= question.get().options().size()) {
            return Optional.empty();
        }
        // M2 修正：rt 來自外部信件連結，可能帶 CR/LF 或 &/= 等特殊字元；
        // 未編碼會破壞查詢字串結構甚至讓病態值變成 500（HttpServletResponse
        // 對含 CR/LF 的 Location header 會拋例外），故一律先做 URL 編碼。
        String redirect = "/r/survey/" + formKey + "?voted=" + optionIndex
            + (campaignId != null ? "&c=" + campaignId : "")
            + (StringUtils.hasText(rt)
                ? "&rt=" + URLEncoder.encode(rt, StandardCharsets.UTF_8)
                : "");
        // c 參數存在但 campaign 不存在（含測試信固定帶的 c=0）→ 照常轉址、不落票
        if (campaignId != null && !campaignRepository.existsById(campaignId)) {
            return Optional.of(redirect);
        }
        try {
            recordVote(question.get(), optionIndex, campaignId, rt, sessionCookie);
        } catch (RuntimeException e) {
            log.warn("問卷投票記錄失敗 form={}，轉址照常", formKey, e); // best-effort，同 PromoClickService 哲學
        }
        return Optional.of(redirect);
    }

    /** 依身分歸戶落票：具名（RECIPIENT/READER）先查既有列 upsert 改票，匿名一律新增 */
    private void recordVote(EmailVoteQuestion question, int optionIndex,
                            Long campaignId, String rt, String sessionCookie) {
        String optionValue = question.options().get(optionIndex);
        // channel 只看 rt 本身是否帶值：信中一鍵一律視為 EMAIL 管道，網頁快投視為 WEB
        String channel = StringUtils.hasText(rt) ? SurveyVote.CHANNEL_EMAIL : SurveyVote.CHANNEL_WEB;

        String identityType;
        String identityKey;
        Optional<String> email = tokenService.verify(rt);
        if (email.isPresent()) {
            identityType = SurveyVote.IDENTITY_RECIPIENT;
            identityKey = email.get();
        } else {
            Optional<Long> readerId = sessionService.readReaderId(sessionCookie, OffsetDateTime.now());
            if (readerId.isPresent()) {
                identityType = SurveyVote.IDENTITY_READER;
                identityKey = String.valueOf(readerId.get());
            } else {
                identityType = SurveyVote.IDENTITY_ANON;
                identityKey = null;
            }
        }
        // 防禦：具名身分的 identityKey 若為空白（例如 rt 驗證回病態空字串），必須在此降級為匿名。
        // NULL 對唯一索引不生效（多個 NULL 視為互不相等），具名身分若留 NULL identityKey
        // 會逃過一票限制；因此 blank 一律降級 ANON，由 WHERE identity_type <> 'ANON' 明確排除
        if (!SurveyVote.IDENTITY_ANON.equals(identityType) && !StringUtils.hasText(identityKey)) {
            identityType = SurveyVote.IDENTITY_ANON;
            identityKey = null;
        }

        if (SurveyVote.IDENTITY_ANON.equals(identityType)) {
            voteRepository.save(new SurveyVote(question.formKey(), question.fieldKey(),
                optionValue, campaignId, channel, identityType, identityKey));
            return;
        }
        Optional<SurveyVote> existing = voteRepository
            .findByFormKeyAndIdentityTypeAndIdentityKey(question.formKey(), identityType, identityKey);
        if (existing.isPresent()) {
            SurveyVote vote = existing.get();
            // M1 修正：信中一鍵題可能被改綁到不同欄位，fieldKey 必須跟著同步更新，
            // 否則會留下 optionValue 屬於新欄位、fieldKey 卻仍指向舊欄位的自相矛盾列。
            vote.setFieldKey(question.fieldKey());
            vote.setOptionValue(optionValue);
            vote.setCampaignId(campaignId);
            vote.setChannel(channel);
            vote.setUpdatedAt(OffsetDateTime.now());
            voteRepository.save(vote);
        } else {
            voteRepository.save(new SurveyVote(question.formKey(), question.fieldKey(),
                optionValue, campaignId, channel, identityType, identityKey));
        }
    }
}
