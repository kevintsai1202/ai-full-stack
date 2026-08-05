package world.springai.survey.newsletter;

import org.springframework.stereotype.Service;
import world.springai.survey.form.FormSchemaService;
import world.springai.survey.form.FormSchemaService.EmailVoteQuestion;
import world.springai.survey.promo.PromoRecipientTokenService;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 問卷標記三通道展開器：把電子報內文中的 {@code <!--survey:FORM_KEY-->} 標記，
 * 依信件／讀者頁／預覽三種通道展開成對應的問卷投票卡片。
 *
 * <p>比對規則同 {@link MarkdownRenderer} 的 promo 標記：在「渲染後 / 折疊前」的字串
 * 層級處理，而非操作 AST；HTML 註解會被 commonmark 原樣輸出，字串替換即可涵蓋
 * 信件、後台預覽、讀者頁三條路徑。</p>
 *
 * <p><b>問卷不可嵌入時標記保留原樣</b>：與 promo 標記「不成對即不轉換」同一哲學——
 * HTML 註解對讀者不可見，是安全的降級，不需要在展開階段拋例外；真正該擋下寄送的
 * 情境交給 {@link #assertEmbeddable(String)} 在寄送前明確檢查並拋例外。</p>
 */
@Service
public class SurveyBlockRenderer {

    /**
     * 信件通道專用的活動 id 佔位符。
     *
     * <p>渲染信件內文時，實際的電子報 campaignId 通常尚未確定（同一份內文會先組出
     * 可預覽的 HTML，campaign 落地與寄送是後續步驟），因此先寫入固定佔位符，
     * 交由呼叫端（Task 9：CampaignService／CampaignDeliveryService）在 campaignId
     * 確定後統一字串替換——作法比照 {@link PromoRecipientTokenService#PLACEHOLDER}
     * 的收件人 token 延遲替換模式。</p>
     */
    public static final String CID_PLACEHOLDER = "__SURVEY_CID__";

    /** 標記樣式：{@code <!--survey:FORM_KEY-->}，比對規則同 promo 成對標記的字串層級處理 */
    static final Pattern MARKER = Pattern.compile("<!--survey:([a-z0-9-]+)-->");

    /** 問卷卡片容器開頭：底色 #eef3fb／左側 5px #1d4ed8 藍條，與工商卡（綠色系）視覺區隔 */
    private static final String CARD_OPEN =
        "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\""
            + " style=\"margin:28px 0;border-collapse:separate;\"><tr>"
            + "<td bgcolor=\"#eef3fb\" style=\"border:1px solid #b6c9ef;"
            + "border-left:5px solid #1d4ed8;border-radius:10px;padding:20px 24px;\">";
    private static final String CARD_CLOSE = "</td></tr></table>";
    /** 預覽通道的「不計票」提示樣式 */
    private static final String PREVIEW_BADGE =
        "<p style=\"margin:0 0 12px;font-size:12px;font-weight:700;color:#b45309;\">預覽不計票</p>";
    /** 點數提示樣式：與卡片同色系但字級較小，排在題目之上 */
    private static final String REWARD_HINT_STYLE =
        "margin:0 0 10px;font-size:13px;font-weight:700;color:#1d4ed8;";
    private static final String TITLE_STYLE =
        "margin:0 0 6px;font-size:16px;font-weight:700;color:#1e3a8a;";
    private static final String LABEL_STYLE =
        "margin:0 0 14px;font-size:14px;color:#334155;";
    /** 選項按鈕樣式：逐一 inline-block 的 {@code <a>}，Email 相容度最高 */
    private static final String OPTION_STYLE =
        "display:inline-block;margin:0 8px 8px 0;padding:8px 16px;border-radius:6px;"
            + "background:#1d4ed8;color:#ffffff;text-decoration:none;font-size:14px;";
    private static final String CONTINUE_STYLE =
        "display:block;margin-top:10px;font-size:13px;color:#1d4ed8;text-decoration:underline;";

    /** 問卷 schema 服務：查詢標記對應的信中一鍵題是否可嵌入 */
    private final FormSchemaService formSchemaService;
    /** 投票獎勵點數的取值來源；只認根套件介面，維持 newsletter 不依賴 reader */
    private final world.springai.survey.SurveyVoteRewardView rewardView;

    /** 注入問卷 schema 服務與投票獎勵取值來源 */
    public SurveyBlockRenderer(FormSchemaService formSchemaService,
                               world.springai.survey.SurveyVoteRewardView rewardView) {
        this.formSchemaService = formSchemaService;
        this.rewardView = rewardView;
    }

    /**
     * 信件通道展開：選項按鈕連結格式
     * {@code {readerBaseUrl}/s/v/{formKey}?f={fieldKey}&o={optionIndex}&c=__SURVEY_CID__&rt=__PROMO_RT__}。
     * campaignId 與收件人 token 皆留待呼叫端延遲替換（見 {@link #CID_PLACEHOLDER}）。
     *
     * <p>點數提示註明「限已註冊讀者」：收件人是訂閱者但未必已建立讀者帳號，
     * 未建帳者投票只計票不發點（見 {@code SurveyVoteRewardService}）。</p>
     */
    public String expandForEmail(String html, String readerBaseUrl) {
        String hint = rewardHint("投票即可獲得 %d 點（限已註冊讀者，每份問卷一次）");
        return expand(html, q -> renderCard(q,
            i -> emailOptionHref(q, i, readerBaseUrl), null, false, hint));
    }

    /**
     * 讀者頁通道展開：選項按鈕連結帶 {@code c}（campaignId，可為 null 則不帶），
     * 不帶 {@code rt}（改由 session 歸戶），並附一條「繼續填完整問卷」連結。
     *
     * <p>點數提示依 {@code loggedIn} 分歧：匿名投票不會發點，對未登入者說
     * 「投票即可獲得」就是假訊息。</p>
     */
    public String expandForWeb(String html, Long campaignId, boolean loggedIn) {
        String hint = loggedIn
            ? rewardHint("投票即可獲得 %d 點（每份問卷一次）")
            : rewardHint("登入後投票可獲得 %d 點");
        return expand(html, q -> renderCard(q,
            i -> webOptionHref(q, i, campaignId), continueHref(q.formKey(), campaignId), false, hint));
    }

    /** 預覽通道展開：卡片視覺與正式通道一致，但加「預覽不計票」標示，連結一律 {@code href="#"} */
    public String expandForPreview(String html) {
        String hint = rewardHint("投票即可獲得 %d 點（限已註冊讀者，每份問卷一次）");
        return expand(html, q -> renderCard(q, i -> "#", null, true, hint));
    }

    /**
     * 組出點數提示列；獎勵為 0（後台關閉投票發點）時回空字串。
     *
     * <p>回空字串而非顯示「獲得 0 點」：後者是把一個沒有好處的動作包裝成有好處，
     * 比不提示更糟。</p>
     */
    private String rewardHint(String template) {
        int reward = rewardView.surveyVoteReward();
        if (reward <= 0) {
            return "";
        }
        return "<p style=\"" + REWARD_HINT_STYLE + "\">🎁 "
            + escapeHtml(template.formatted(reward)) + "</p>";
    }

    /**
     * 寄送前驗證：掃描內文中的問卷標記，任何一個標記對應的問卷「不可嵌入」
     * （未發布或未設定信中一鍵題）就拋例外擋下寄送；無標記或全部可嵌入則靜默通過。
     */
    public void assertEmbeddable(String markdown) {
        if (markdown == null) {
            return;
        }
        Matcher matcher = MARKER.matcher(markdown);
        while (matcher.find()) {
            String formKey = matcher.group(1);
            if (formSchemaService.emailVoteQuestion(formKey).isEmpty()) {
                throw new IllegalArgumentException(
                    "問卷標記無法嵌入（未發布或未設定信中一鍵題）：" + formKey);
            }
        }
    }

    /**
     * 通道無關的展開核心：逐一比對標記，可嵌入就換成卡片 HTML，不可嵌入則保留原樣
     * （跳過不動 cursor，讓該段標記原文透過最終的尾段拷貝留在輸出中）。
     */
    private String expand(String html, Function<EmailVoteQuestion, String> cardRenderer) {
        if (html == null) {
            return "";
        }
        Matcher matcher = MARKER.matcher(html);
        StringBuilder result = new StringBuilder(html.length());
        int cursor = 0;
        while (matcher.find()) {
            String formKey = matcher.group(1);
            Optional<EmailVoteQuestion> question = formSchemaService.emailVoteQuestion(formKey);
            if (question.isEmpty()) {
                continue; // 不可嵌入：無害 HTML 註解原樣保留，安全降級
            }
            result.append(html, cursor, matcher.start());
            result.append(cardRenderer.apply(question.get()));
            cursor = matcher.end();
        }
        return result.append(html, cursor, html.length()).toString();
    }

    /** 組出問卷卡片 HTML：點數提示、標題、題目 label、逐一選項按鈕，選填一條續填連結與預覽標示 */
    private String renderCard(EmailVoteQuestion question, IntFunction<String> optionHref,
                               String continueHref, boolean previewMode, String rewardHint) {
        StringBuilder sb = new StringBuilder();
        sb.append(CARD_OPEN);
        if (previewMode) {
            sb.append(PREVIEW_BADGE);
        }
        // 點數提示排在題目之前：讀者要先知道有好處，才有動機讀題並投票
        sb.append(rewardHint);
        sb.append("<p style=\"").append(TITLE_STYLE).append("\">")
            .append(escapeHtml(question.title())).append("</p>");
        sb.append("<p style=\"").append(LABEL_STYLE).append("\">")
            .append(escapeHtml(question.label())).append("</p>");
        List<String> options = question.options();
        for (int i = 0; i < options.size(); i++) {
            sb.append("<a href=\"").append(optionHref.apply(i)).append("\" style=\"")
                .append(OPTION_STYLE).append("\">")
                .append(escapeHtml(options.get(i))).append("</a>");
        }
        if (continueHref != null) {
            sb.append("<a href=\"").append(continueHref).append("\" style=\"")
                .append(CONTINUE_STYLE).append("\">繼續填完整問卷</a>");
        }
        sb.append(CARD_CLOSE);
        return sb.toString();
    }

    /** 信件通道選項連結：帶 readerBaseUrl 絕對網址、campaignId 與收件人 token 皆為待替換佔位符 */
    private String emailOptionHref(EmailVoteQuestion question, int optionIndex, String readerBaseUrl) {
        return readerBaseUrl + "/s/v/" + question.formKey()
            + "?f=" + question.fieldKey() + "&o=" + optionIndex
            + "&c=" + CID_PLACEHOLDER + "&rt=" + PromoRecipientTokenService.PLACEHOLDER;
    }

    /** 讀者頁通道選項連結：相對路徑，campaignId 存在才帶 c，不帶 rt（session 歸戶） */
    private String webOptionHref(EmailVoteQuestion question, int optionIndex, Long campaignId) {
        return "/s/v/" + question.formKey() + "?f=" + question.fieldKey() + "&o=" + optionIndex
            + (campaignId != null ? "&c=" + campaignId : "");
    }

    /** 「繼續填完整問卷」連結：campaignId 存在才帶 c，規則與選項連結一致 */
    private String continueHref(String formKey, Long campaignId) {
        return "/r/survey/" + formKey + (campaignId != null ? "?c=" + campaignId : "");
    }

    /**
     * 把文字跳脫成可安全插入 HTML 的形式。
     *
     * <p>因套件依賴規則（newsletter 不得依賴 reader）在此複製
     * {@code world.springai.survey.reader.HtmlTemplate#escapeHtml} 的實作，而非直接
     * import 該類別；兩處各自維護但邏輯必須保持一致（同五個 replace，{@code &} 最先
     * 處理，否則後續產生的實體會被再次跳脫）。</p>
     */
    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
