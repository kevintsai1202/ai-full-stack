package world.springai.survey;

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
}
