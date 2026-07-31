package world.springai.survey.newsletter;

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
 * <p><b>折疊不看 tier</b>：切分與 tier 正交（見 {@link ContentSplitter}），
 * BASIC 文章也可以有受限區，所以任何以 tier 為條件的守門都擋不到那個組合。
 * 判斷依據只有一個：作者插了標記，就是不想讓這段出現在信裡。</p>
 */
@Component
public class MailBodyRenderer {

    private final ContentSplitter contentSplitter;
    private final MarkdownRenderer markdownRenderer;
    private final ReaderSiteLinks readerSiteLinks;

    /** 注入切分、渲染與讀者站連結三項相依 */
    public MailBodyRenderer(ContentSplitter contentSplitter,
                            MarkdownRenderer markdownRenderer,
                            ReaderSiteLinks readerSiteLinks) {
        this.contentSplitter = contentSplitter;
        this.markdownRenderer = markdownRenderer;
        this.readerSiteLinks = readerSiteLinks;
    }

    /**
     * 由 markdown 產生信件版 HTML；受限區的內容<b>絕不</b>出現在回傳值中。
     *
     * <p><b>一律從 markdown 重新渲染，不接受現成的 HTML</b>：這個方法刻意不提供
     * 「傳入已渲染 HTML」的多載。補寄那條路徑就是因為信任資料庫裡存的
     * {@code campaign.body_html} 而外洩——那些列是折疊功能存在之前寫入的，
     * 存的是全文。markdown 原文才是唯一可信的來源。</p>
     *
     * @param markdown 電子報原文；null 視為空字串
     * @param slug     文章網址片段；null 表示這封信沒有對應文章（測試信），
     *                 解鎖卡片的 CTA 改導向歷史內容列表
     */
    public String html(String markdown, String slug) {
        ContentSplitter.Split split = contentSplitter.split(markdown);
        if (!split.hasGate()) {
            return markdownRenderer.toHtml(markdown);
        }
        return markdownRenderer.toHtml(split.freeMarkdown()) + gateCard(slug);
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
