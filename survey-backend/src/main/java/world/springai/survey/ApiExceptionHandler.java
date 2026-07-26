package world.springai.survey;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** 統一錯誤回應：把驗證失敗轉成 400 ProblemDetail */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * 把 {@link ResponseStatusException} 的 reason 放進回應本文。
     *
     * <p><b>為什麼需要</b>：後台丟出的 400 幾乎都是「使用者必須知道原因才能修正」的錯
     * ——「slug 已被使用」「加點說明請縮短至 200 字以內」「不是可調參數：x」
     * 「VIP 天數必須大於 0」。但 Spring Boot 預設 {@code server.error.include-message=never}，
     * reason 不會出現在回應裡，前端只能顯示「HTTP 400」，管理者無從得知該改什麼。</p>
     *
     * <p>刻意不改用 {@code server.error.include-message=always}：那會連同未預期例外的
     * 訊息（含堆疊裡的內部細節）一併對外送出。這裡只回傳<b>我們自己刻意寫下的
     * reason</b>，狀態碼原樣沿用，不影響任何既有契約。</p>
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> onResponseStatus(ResponseStatusException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(ex.getStatusCode());
        String reason = ex.getReason();
        if (reason != null && !reason.isBlank()) {
            pd.setDetail(reason);
        }
        // 保留例外自帶的標頭（例如 401 的 WWW-Authenticate），避免因為改走本處理器而遺失
        return ResponseEntity.status(ex.getStatusCode()).headers(ex.getHeaders()).body(pd);
    }
    /** Bean Validation 失敗（含 email 格式、consent 必須為 true）回 400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onValidation(MethodArgumentNotValidException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("問卷資料驗證失敗");
        pd.setDetail(ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + " " + e.getDefaultMessage())
            .reduce((a, b) -> a + "; " + b).orElse("invalid request"));
        return pd;
    }
}
