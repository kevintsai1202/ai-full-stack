package world.springai.survey.mail;

import org.springframework.stereotype.Component;

/** 信件品牌外框：把內文 HTML 套上版面與退訂頁腳，歡迎信與電子報共用 */
@Component
public class EmailTemplate {

    /** 以固定外框包住內文，並在頁腳放入該收件人的退訂連結 */
    public String wrap(String bodyHtml, String unsubscribeLink) {
        return """
            <div style="font-family:system-ui,'Microsoft JhengHei',sans-serif;line-height:1.7;max-width:560px;margin:0 auto;color:#1a1a2e">
              %s
              <hr style="border:none;border-top:1px solid #eee;margin:24px 0">
              <p style="color:#888;font-size:.85rem">
                你會收到這封信，是因為你在課程網站填寫了興趣調查並同意接收課程資訊。<br>
                若不想再收到，<a href="%s" style="color:#4f46e5">點此取消訂閱</a>。
              </p>
            </div>
            """.formatted(bodyHtml, unsubscribeLink);
    }

    /**
     * 電子報外框：在退訂頁腳前加入「直接閱讀文章」主按鈕與登入入口。
     *
     * <p>信件客戶端對 CSS 支援不一致，因此這裡保留 inline style；
     * 按鈕使用完整網址，讀者不論從哪個信箱 App 開啟都能進站。</p>
     */
    public String wrapCampaign(String bodyHtml, String unsubscribeLink,
                               String articleLink, String loginLink, long subscriberCount) {
        return """
            <div style="font-family:system-ui,'Microsoft JhengHei',sans-serif;line-height:1.7;max-width:560px;margin:0 auto;color:#102033">
              <div style="margin:0 0 24px;padding:10px 16px;border-radius:999px;background:#ecfdf5;color:#0f766e;text-align:center;font-size:13px;font-weight:700">
                已有 %s 位讀者訂閱凱文大叔的電子報
              </div>
              %s
              <div style="margin:32px 0 24px;padding:24px;border:1px solid #dce5ee;border-radius:12px;background:#f7fafc;text-align:center">
                <p style="margin:0 0 14px;font-weight:700">想用更舒服的版面閱讀或解鎖內容？</p>
                <p style="margin:0 0 18px">
                  <a href="%s" style="display:inline-block;background:#0f766e;color:#fff;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:700">到網站閱讀／解鎖</a>
                </p>
                <p style="margin:0;color:#5c6b7d;font-size:.9rem">
                  已有帳戶？<a href="%s" style="color:#0f766e;font-weight:700">登入讀者中心</a>
                </p>
              </div>
              <hr style="border:none;border-top:1px solid #dce5ee;margin:24px 0">
              <p style="color:#5c6b7d;font-size:.85rem">
                你會收到這封信，是因為你曾訂閱凱文大叔的電子報。<br>
                若不想再收到，<a href="%s" style="color:#0f766e">點此取消訂閱</a>。
              </p>
            </div>
            """.formatted(subscriberCount, bodyHtml, articleLink, loginLink, unsubscribeLink);
    }
}
