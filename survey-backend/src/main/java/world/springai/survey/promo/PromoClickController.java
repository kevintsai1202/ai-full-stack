package world.springai.survey.promo;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import world.springai.survey.reader.HtmlTemplate;
import world.springai.survey.reader.ReaderSessionService;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 工商連結安全轉址：目的地一律由 DB 依 placementId 查出，
 * 網址不進參數——無 open redirect 攻擊面（spec §5）。
 */
@RestController
public class PromoClickController {

    private final PromoClickService clickService;
    private final HtmlTemplate htmlTemplate;

    /** 注入點擊服務與頁面渲染 */
    public PromoClickController(PromoClickService clickService, HtmlTemplate htmlTemplate) {
        this.clickService = clickService;
        this.htmlTemplate = htmlTemplate;
    }

    /** https 302 轉址、mailto 渲染聯絡中介頁；版位不存在回 404 */
    @GetMapping("/promo/c/{placementId}")
    public ResponseEntity<String> click(
            @PathVariable long placementId,
            @RequestParam(value = "rt", required = false) String rt,
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {
        return clickService.resolveAndRecord(placementId, rt, sessionCookie)
            .map(this::respond)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 依目的地類型決定回應：https 照舊 302；mailto 不轉址，改渲染聯絡中介頁。
     *
     * <p><b>為何 mailto 不能 302</b>：點擊先落在瀏覽器，302 到 {@code mailto:} 是把
     * 目的地丟給作業系統預設郵件程式——桌機讀者多用網頁版信箱、沒設定本機郵件軟體，
     * 結果是空白頁或系統跳「選擇應用程式」，等於死路。中介頁顯示地址＋一鍵複製，
     * 任何環境都有效；點擊統計在 {@code resolveAndRecord} 已記錄，兩種目的地口徑一致。</p>
     */
    private ResponseEntity<String> respond(PromoClickService.Destination destination) {
        if (!destination.linkUrl().startsWith("mailto:")) {
            return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, destination.linkUrl()).build();
        }
        // 顯示與複製用的純信箱：去掉 mailto: 前綴與 ?subject= 等參數；href 保留完整 URI
        String address = destination.linkUrl().substring("mailto:".length()).split("\\?", 2)[0];
        Map<String, String> vars = new HashMap<>();
        vars.put("<!--PROMO_TITLE-->", HtmlTemplate.escapeHtml(destination.title()));
        vars.put("<!--CONTACT_EMAIL-->", HtmlTemplate.escapeHtml(address));
        vars.put("<!--MAILTO_HREF-->", HtmlTemplate.escapeHtml(destination.linkUrl()));
        // charset 必須明講：只給 text/html 時 StringHttpMessageConverter 可能以
        // ISO-8859-1 解讀，中文會變亂碼
        return ResponseEntity.ok()
            .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
            .body(htmlTemplate.render("templates/reader/promo-contact.html", vars));
    }
}
