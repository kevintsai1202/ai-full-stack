package world.springai.survey.audience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(SubscriptionController.class);

    private final SurveyResponseRepository repository;
    private final UnsubscribeTokenService tokenService;
    /** 確認成功後發布事件；讓 reader 能在不被 audience 依賴的前提下發放推薦獎勵 */
    private final ApplicationEventPublisher eventPublisher;
    /** 新版同意事件資料層；與舊布林欄位同步維護。 */
    private final AudiencePlatformService audiencePlatformService;

    /** 注入名單資料層、HMAC 驗證、事件發布器與新版同意資料層。 */
    public SubscriptionController(SurveyResponseRepository repository,
                                  UnsubscribeTokenService tokenService,
                                  ApplicationEventPublisher eventPublisher,
                                  AudiencePlatformService audiencePlatformService) {
        this.repository = repository;
        this.tokenService = tokenService;
        this.eventPublisher = eventPublisher;
        this.audiencePlatformService = audiencePlatformService;
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
            String normalized = normalize(email);
            int affected = repository.confirmByEmail(normalized);
            if (affected > 0) {
                OffsetDateTime now = OffsetDateTime.now();
                audiencePlatformService.appendConsentByEmail(
                    normalized,
                    AudiencePlatformService.CONSENT_CONFIRMED,
                    AudiencePlatformService.SOURCE_CONFIRMATION_LINK,
                    java.util.Map.of("method", "signed-link"),
                    now);
                // 確認訂閱是高可靠的參與度訊號（spec §5.10）
                repository.touchEngagement(normalized, now);
                // 事件發布是同步的，例外會往上拋。發放獎勵失敗不該影響「使用者已經
                // 同意訂閱」這個已經成立且已提交的事實——更關鍵的是：若讓例外變成
                // 500，「不論結果一律回相同的 200」這條性質就破了，端點會變成
                // 「這個 email 有沒有推薦關係」的探測器。
                // 這道防護刻意放在發布端，不依賴下游監聽器記得自己吞例外。
                try {
                    eventPublisher.publishEvent(new SubscriptionConfirmedEvent(normalized));
                } catch (Exception e) {
                    log.error("確認訂閱的後續處理失敗（同意已記錄，不影響訂閱狀態）：{}", normalized, e);
                }
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
            String normalized = normalize(email);
            int affected = repository.unsubscribeByEmail(normalized);
            if (affected > 0) {
                audiencePlatformService.appendConsentByEmail(
                    normalized,
                    AudiencePlatformService.CONSENT_UNSUBSCRIBED,
                    "unsubscribe-link",
                    java.util.Map.of("method", "signed-link"),
                    OffsetDateTime.now());
            }
        }
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/html; charset=UTF-8"))
            .body(UNSUBSCRIBE_HTML);
    }

    /**
     * email 正規化：去前後空白並轉小寫。
     *
     * <p>{@code Locale.ROOT} 不可省略：土耳其語系（tr-TR）下無參數的
     * {@code toLowerCase()} 會把 {@code I} 轉成 {@code ı}。這裡的值不只用來查名單，
     * 還會原樣傳進 {@code SubscriptionConfirmedEvent} 成為
     * {@code ReferralService} 的<b>發獎冪等鍵</b>——與 {@code reader} 套件那三處
     * （皆已帶 {@code Locale.ROOT}）算出不同的鍵，就會重複發獎。
     * 四處必須用同一套正規化規則。</p>
     */
    private static String normalize(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
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
