package world.springai.survey.promo;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import world.springai.survey.reader.ReaderSessionService;

/**
 * 工商連結安全轉址：目的地一律由 DB 依 placementId 查出，
 * 網址不進參數——無 open redirect 攻擊面（spec §5）。
 */
@RestController
public class PromoClickController {

    private final PromoClickService clickService;

    /** 注入點擊服務 */
    public PromoClickController(PromoClickService clickService) {
        this.clickService = clickService;
    }

    /** 302 轉址；版位不存在回 404 */
    @GetMapping("/promo/c/{placementId}")
    public ResponseEntity<Void> click(
            @PathVariable long placementId,
            @RequestParam(value = "rt", required = false) String rt,
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {
        return clickService.resolveAndRecord(placementId, rt, sessionCookie)
            .map(url -> ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, url).<Void>build())
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
