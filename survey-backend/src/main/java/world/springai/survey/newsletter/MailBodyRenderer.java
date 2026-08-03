package world.springai.survey.newsletter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import world.springai.survey.ReaderSiteLinks;

/**
 * 「信件版內文」的<b>唯一</b>產生點：把電子報 markdown 渲染成要寄出的 HTML，
 * 內容含 {@code <!--paywall-->} 時只取免費區並接上解鎖卡片。
 *
 * <p><b>為什麼要獨立成一個元件</b>：會寄出電子報的服務不只一個
 * （{@link CampaignService} 的測試信／群發／重排，以及 {@link CampaignDeliveryService}
 * 的補寄），而每多一個自己決定「要不要折疊」的地方，就多一條會漏的路徑。
 * 這個專案已經因此漏過兩次——第一次是 {@code send()} 與 {@code reschedule()}
 * 直接渲染整份 markdown，第二次是補寄直接重播存下來的 {@code body_html}。
 * 兩次的共同原因都是「折疊的判斷散落在呼叫端」。</p>
 *
 * <p><b>折疊仍以標記為準</b>：服務層會強制含標記的文章使用 PREMIUM；本元件仍只
 * 依標記折疊，讓補寄舊資料時不會因 tier 歷史值不一致而把受限全文寄出去。</p>
 *
 * <p><b>Task 9 接線</b>：本類也是信件通道問卷卡展開（{@link SurveyBlockRenderer#expandForEmail}）
 * 的唯一插入點——理由與折疊判斷完全相同：{@link CampaignService} 與
 * {@link CampaignDeliveryService} 都經由 {@link #html} 產生信件內文，插在這裡
 * 才能保證兩條路徑都會展開問卷卡，不會有一邊漏接。</p>
 */
@Component
public class MailBodyRenderer {

    private final ContentSplitter contentSplitter;
    private final MarkdownRenderer markdownRenderer;
    private final ReaderSiteLinks readerSiteLinks;
    /** 問卷標記展開器：信件通道專用，campaignId 於此刻尚未確定，展開後留下 CID 佔位符 */
    private final SurveyBlockRenderer surveyBlockRenderer;
    /**
     * 信件內問卷連結所需的絕對網址。
     *
     * <p>解析順序與後備邏輯照 {@link world.springai.survey.promo.PromoPlacementService}
     * 既有的讀者網域解析（同一組設定鍵、同一後備順序）：優先取讀者站網域，
     * 未設定時退回對外公開網址，本機與舊環境相容。</p>
     */
    private final String readerBaseUrl;

    /** 注入切分、渲染、讀者站連結、問卷展開器與問卷連結絕對網址 */
    public MailBodyRenderer(ContentSplitter contentSplitter,
                            MarkdownRenderer markdownRenderer,
                            ReaderSiteLinks readerSiteLinks,
                            SurveyBlockRenderer surveyBlockRenderer,
                            @Value("${app.reader.base-url:${app.public-base-url}}") String readerBaseUrl) {
        this.contentSplitter = contentSplitter;
        this.markdownRenderer = markdownRenderer;
        this.readerSiteLinks = readerSiteLinks;
        this.surveyBlockRenderer = surveyBlockRenderer;
        this.readerBaseUrl = readerBaseUrl;
    }

    /**
     * 由 markdown 產生信件版 HTML；受限區的內容<b>絕不</b>出現在回傳值中。
     *
     * <p><b>一律從 markdown 重新渲染，不接受現成的 HTML</b>：這個方法刻意不提供
     * 「傳入已渲染 HTML」的多載。補寄那條路徑就是因為信任資料庫裡存的
     * {@code campaign.body_html} 而外洩——那些列是折疊功能存在之前寫入的，
     * 存的是全文。markdown 原文才是唯一可信的來源。</p>
     *
     * <p>回傳值可能含問卷卡的 {@link SurveyBlockRenderer#CID_PLACEHOLDER}，
     * 呼叫端須在確定 campaignId 後自行替換（見 {@link CampaignService#send}
     * 與 {@link CampaignDeliveryService} 的替換點）。</p>
     *
     * @param markdown 電子報原文；null 視為空字串
     * @param slug     文章網址片段；null 表示這封信沒有對應文章（測試信），
     *                 解鎖卡片的 CTA 改導向歷史內容列表
     */
    public String html(String markdown, String slug) {
        ContentSplitter.Split split = contentSplitter.split(markdown);
        String rendered = split.hasGate()
            ? markdownRenderer.toHtml(split.freeMarkdown()) + gateCard(slug)
            : markdownRenderer.toHtml(markdown);
        // 問卷標記展開放在折疊判斷之後、統一收斂的最後一步：兩個分支都要展開，
        // 少了任何一邊就是一條會漏接問卷卡片的後門。
        return surveyBlockRenderer.expandForEmail(rendered, readerBaseUrl);
    }

    /**
     * 解鎖卡片：告訴讀者內容在此處被截斷，以及要去哪裡讀完。
     *
     * <p><b>為什麼不能只靠信件外框既有的「前往閱讀完整版」按鈕</b>：那個按鈕每封信都有，
     * 讀者無從得知這一封的內文其實被切掉了一半——沒有這張卡片，折疊看起來就只是
     * 「這篇比較短」。卡片標示的是截斷點本身。</p>
     *
     * <p>Email 相容性：容器用單格 table＋inline style（Outlook 對 div 支援差），
     * 配色沿用讀者頁主題綠。</p>
     */
    private String gateCard(String slug) {
        String href = slug == null ? readerSiteLinks.archive() : readerSiteLinks.article(slug);
        return """
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" \
            style="margin:28px 0"><tr><td style="padding:24px 20px;\
            border:2px dashed #0f766e;border-radius:12px;background:#f0fdfa;\
            color:#134e4a;text-align:center">
              <strong style="display:block;font-size:16px">🔒 這是進階內容</strong>
              <span style="display:block;margin-top:8px;font-size:13px">\
            後續內容請到網站上閱讀，實際可讀範圍依你的帳號權限而定。</span>
              <a href="\
            """ + href + """
            " style="display:inline-block;margin-top:14px;\
            background:#0f766e;color:#ffffff;padding:10px 22px;border-radius:8px;\
            text-decoration:none;font-weight:700">前往網站閱讀完整內容</a>
            </td></tr></table>
            """;
    }
}
