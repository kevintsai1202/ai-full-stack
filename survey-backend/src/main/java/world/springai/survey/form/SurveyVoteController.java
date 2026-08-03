package world.springai.survey.form;

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
 * 信中一鍵投票端點：目標合法即落票（best-effort，失敗不擋轉址）並 302 到接續頁；
 * 目標不合法（表單／欄位不符或選項越界）一律回 404，不洩漏 schema 細節。
 */
@RestController
public class SurveyVoteController {

    private final SurveyVoteService voteService;

    /** 注入投票服務 */
    public SurveyVoteController(SurveyVoteService voteService) {
        this.voteService = voteService;
    }

    /**
     * {@code o}（選項索引）刻意宣告為 {@code String} 自行 parse，而非讓 Spring
     * 用 {@code @RequestParam int} 直接綁定：綁定失敗會被預設的
     * {@code MethodArgumentTypeMismatchException} handler 接住而回 500，
     * 對「信中連結被亂改／爬蟲亂打」這種外部可觸發的輸入來說 500 不合適，
     * 應與其他不合法目標一致回 404。
     */
    @GetMapping("/s/v/{formKey}")
    public ResponseEntity<Void> vote(
            @PathVariable String formKey,
            @RequestParam(value = "f", required = false) String fieldKey,
            @RequestParam(value = "o", required = false) String optionIndex,
            @RequestParam(value = "c", required = false) Long campaignId,
            @RequestParam(value = "rt", required = false) String rt,
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {
        int index;
        try {
            index = Integer.parseInt(optionIndex);
        } catch (NumberFormatException | NullPointerException e) {
            return ResponseEntity.notFound().build();
        }
        return voteService.vote(formKey, fieldKey, index, campaignId, rt, sessionCookie)
            .map(path -> ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, path).<Void>build())
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
