package world.springai.survey.audience;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * 名單同意管理的公開端點：確認訂閱與退訂。
 *
 * <p>自 {@code form.SurveyController} 搬入——這兩個端點本質是名單中心的職責，
 * 不是問卷表單功能。<b>URL 路徑刻意保持不變</b>（見
 * {@link SubscriptionLinkBuilder}）：已寄出的信件內含這些網址。</p>
 *
 * <p>四項必須保留的安全性質：
 * ① 防偽——僅當 HMAC 簽章正確才執行；
 * ② 冪等——重複點擊、名單查無此人都回相同成功頁；
 * ③ 不洩漏名單——不論結果（含簽章不符）一律相同回應與 200，
 *    否則此端點會變成「某個 email 在不在名單裡」的查詢工具；
 * ④ 回應頁為固定字串、不回顯使用者輸入。</p>
 */
@RestController
public class SubscriptionController {

    private final SurveyResponseRepository repository;
    private final UnsubscribeTokenService tokenService;
    /** 確認成功後發布事件；讓 reader 能在不被 audience 依賴的前提下發放推薦獎勵 */
    private final ApplicationEventPublisher eventPublisher;

    /** 注入名單資料層、HMAC 驗證與事件發布器 */
    public SubscriptionController(SurveyResponseRepository repository,
                                  UnsubscribeTokenService tokenService,
                                  ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.tokenService = tokenService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 公開確認訂閱端點：使用者從邀請信點擊確認連結（GET）後以瀏覽器開啟，故回 HTML。
     *
     * <p>只有在「簽章正確」<b>且</b>「名單中確實有這筆」時才發布事件——
     * confirmByEmail 回 0 代表查無此 email，此時發事件會讓下游對一個
     * 不存在的訂閱者計算獎勵。</p>
     */
    @GetMapping(value = SubscriptionLinkBuilder.CONFIRM_PATH, produces = "text/html; charset=UTF-8")
    public ResponseEntity<String> confirm(@RequestParam(value = "email", required = false) String email,
                                          @RequestParam(value = "t", required = false) String token) {
        if (StringUtils.hasText(email) && tokenService.verify(email, token)) {
            String normalized = email.trim().toLowerCase();
            int affected = repository.confirmByEmail(normalized);
            if (affected > 0) {
                // 確認訂閱是高可靠的參與度訊號（spec §5.10）
                repository.touchEngagement(normalized, OffsetDateTime.now());
                eventPublisher.publishEvent(new SubscriptionConfirmedEvent(normalized));
            }
        }
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/html; charset=UTF-8"))
            .body(CONFIRM_HTML);
    }

    /**
     * 公開退訂端點：使用者從行銷信件點擊退訂連結（GET）後以瀏覽器開啟，故回 HTML。
     * 設計與確認端點一致：防偽、冪等、不洩漏名單、固定回應頁。
     */
    @GetMapping(value = SubscriptionLinkBuilder.UNSUBSCRIBE_PATH, produces = "text/html; charset=UTF-8")
    public ResponseEntity<String> unsubscribe(@RequestParam(value = "email", required = false) String email,
                                              @RequestParam(value = "t", required = false) String token) {
        if (StringUtils.hasText(email) && tokenService.verify(email, token)) {
            repository.unsubscribeByEmail(email.trim().toLowerCase());
        }
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/html; charset=UTF-8"))
            .body(UNSUBSCRIBE_HTML);
    }

    /** 確認訂閱成功頁（固定內容，不含使用者輸入）；中文提示，置中簡潔樣式 */
    private static final String CONFIRM_HTML = """
            <!doctype html>
            <html lang="zh-Hant">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>訂閱確認成功</title>
              <style>
                body { font-family: system-ui, "Microsoft JhengHei", sans-serif; background: #f7f8fa;
                       display: flex; min-height: 100vh; margin: 0; align-items: center; justify-content: center; }
                .card { background: #fff; padding: 2.5rem 2rem; border-radius: 12px; max-width: 420px;
                        box-shadow: 0 8px 30px rgba(0,0,0,.08); text-align: center; }
                h1 { font-size: 1.4rem; margin: 0 0 .75rem; color: #1a1a2e; }
                p { color: #555; line-height: 1.6; margin: 0; }
              </style>
            </head>
            <body>
              <div class="card">
                <h1>✅ 訂閱確認成功</h1>
                <p>謝謝你！之後的深入技術內容、新課程與學員專屬消息會寄到你的信箱。<br>若改變心意，每封信都有一鍵退訂。</p>
              </div>
            </body>
            </html>
            """;

    /** 退訂成功頁（固定內容，不含使用者輸入）；中文提示，置中簡潔樣式 */
    private static final String UNSUBSCRIBE_HTML = """
            <!doctype html>
            <html lang="zh-Hant">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>已取消訂閱</title>
              <style>
                body { font-family: system-ui, "Microsoft JhengHei", sans-serif; background: #f7f8fa;
                       display: flex; min-height: 100vh; margin: 0; align-items: center; justify-content: center; }
                .card { background: #fff; padding: 2.5rem 2rem; border-radius: 12px; max-width: 420px;
                        box-shadow: 0 8px 30px rgba(0,0,0,.08); text-align: center; }
                h1 { font-size: 1.4rem; margin: 0 0 .75rem; color: #1a1a2e; }
                p { color: #555; line-height: 1.6; margin: 0; }
              </style>
            </head>
            <body>
              <div class="card">
                <h1>您已成功取消訂閱</h1>
                <p>我們不會再寄送行銷訊息給您。<br>若這是誤點，重新填寫問卷即可再次訂閱。</p>
              </div>
            </body>
            </html>
            """;
}
