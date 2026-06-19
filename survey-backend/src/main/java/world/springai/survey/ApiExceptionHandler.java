package world.springai.survey;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 統一錯誤回應：把驗證失敗轉成 400 ProblemDetail */
@RestControllerAdvice
public class ApiExceptionHandler {
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
