package world.springai.survey.mail;

import org.springframework.stereotype.Service;
import world.springai.survey.coupon.CouponCampaign;

/**
 * 優惠券信件版型渲染器：把 {@link CouponCampaign} 的固定欄位套版渲染成寄送用的
 * 主旨與內文 HTML。
 *
 * <p>版型語彙比照 {@code world.springai.survey.newsletter.SurveyBlockRenderer} 的
 * email-safe 單格 table＋inline style 寫法，改用琥珀色系（底色 #fef3c7、左條 5px
 * #d97706），與問卷卡（藍）、工商卡（teal）視覺區隔，讓讀者一眼認出「這是優惠券」。</p>
 *
 * <p>本類別置於 mail 套件（架構下層基礎設施），依 {@code PackageDependencyTest} 的
 * 單向依賴規則不得依賴 reader／newsletter／form（上層）。因此下方 {@link #escapeHtml}
 * 複製 {@code world.springai.survey.reader.HtmlTemplate#escapeHtml} 的實作而非直接
 * import，作法與 {@code newsletter.SurveyBlockRenderer} 的同名 private 方法同理——
 * 兩處各自維護，邏輯必須保持一致（同五個 replace，{@code &} 最先處理，否則後續產生的
 * 實體會被再次跳脫）。</p>
 */
@Service
public class CouponMailRenderer {

    /** 優惠卡容器開頭：底色 #fef3c7（琥珀）、左側 5px #d97706 條 */
    private static final String CARD_OPEN =
        "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\""
            + " style=\"margin:24px 0;border-collapse:separate;\"><tr>"
            + "<td bgcolor=\"#fef3c7\" style=\"border:1px solid #fcd34d;"
            + "border-left:5px solid #d97706;border-radius:10px;padding:20px 24px;\">";
    private static final String CARD_CLOSE = "</td></tr></table>";

    /** 課程名稱標題樣式 */
    private static final String TITLE_STYLE =
        "margin:0 0 8px;font-size:18px;font-weight:700;color:#92400e;";
    /** 推薦文案樣式 */
    private static final String PITCH_STYLE =
        "margin:0 0 14px;font-size:14px;color:#334155;";
    /** 優惠碼樣式：等寬字型＋虛線框，與正文明顯區隔 */
    private static final String CODE_STYLE =
        "display:inline-block;margin:0 0 14px;padding:6px 14px;font-family:monospace;"
            + "font-size:16px;font-weight:700;color:#92400e;border:1px dashed #d97706;"
            + "border-radius:6px;background:#fffbeb;";
    /** 領取優惠按鈕樣式 */
    private static final String BUTTON_STYLE =
        "display:inline-block;margin:0 0 10px;padding:10px 22px;border-radius:6px;"
            + "background:#d97706;color:#ffffff;text-decoration:none;font-size:14px;font-weight:700;";
    /** 期限行樣式 */
    private static final String EXPIRES_STYLE = "margin:0;font-size:13px;color:#92400e;";
    /** 寄送原因與退訂頁腳樣式 */
    private static final String FOOTER_STYLE = "margin:16px 0 0;font-size:12px;color:#64748b;";

    /**
     * 組出信件主旨：課程名稱＋固定優惠語。
     *
     * <p>主旨為信頭純文字（多數信箱 App 不會渲染 HTML 標籤），故不需 HTML 跳脫。</p>
     */
    public String subject(CouponCampaign campaign) {
        return "《" + campaign.getCourseName() + "》讀者專屬優惠";
    }

    /**
     * 渲染優惠卡＋寄送原因／退訂頁腳，組成完整信件內文 HTML。
     *
     * @param campaign        優惠券活動（課程名／文案／連結／優惠碼／期限）
     * @param formTitle       寄送原因顯示的問卷名稱，頁腳文案「你收到這封信是因為你填過問卷『{formTitle}』」
     * @param unsubscribeLink 逐收件人退訂連結，由呼叫端以 SubscriptionLinkBuilder.unsubscribeLink(email) 產生
     */
    public String body(CouponCampaign campaign, String formTitle, String unsubscribeLink) {
        StringBuilder html = new StringBuilder();
        html.append(CARD_OPEN);
        html.append("<p style=\"").append(TITLE_STYLE).append("\">")
            .append(escapeHtml(campaign.getCourseName())).append("</p>");
        html.append("<p style=\"").append(PITCH_STYLE).append("\">")
            .append(escapeHtml(campaign.getPitch())).append("</p>");
        html.append("<p><code style=\"").append(CODE_STYLE).append("\">")
            .append(escapeHtml(campaign.getCouponCode())).append("</code></p>");
        html.append("<p><a href=\"").append(escapeHtml(campaign.getCourseUrl()))
            .append("\" style=\"").append(BUTTON_STYLE).append("\">前往課程</a></p>");
        if (campaign.getExpiresAt() != null) {
            html.append("<p style=\"").append(EXPIRES_STYLE).append("\">優惠至 ")
                .append(campaign.getExpiresAt()).append("</p>");
        }
        html.append(CARD_CLOSE);
        html.append("<p style=\"").append(FOOTER_STYLE)
            .append("\">你收到這封信是因為你填過問卷『").append(escapeHtml(formTitle))
            .append("』。若不想再收到，<a href=\"").append(escapeHtml(unsubscribeLink))
            .append("\">點此取消訂閱</a>。</p>");
        return html.toString();
    }

    /**
     * 把文字跳脫成可安全插入 HTML 的形式。
     *
     * <p>因套件依賴規則（mail 不得依賴 reader）在此複製
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
