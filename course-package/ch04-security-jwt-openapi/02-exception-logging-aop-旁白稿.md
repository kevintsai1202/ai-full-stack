# 02-exception-logging-aop｜全域例外、Log 日誌與 AOP旁白稿

本稿依據 `02-exception-logging-aop.md` 的教學素材與口語稿整理，供人工錄音使用。共 8 段，錄音時可依段落分開保存，方便後續與投影片時間軸對齊。

建議錄音格式：WAV 或 MP3。每段開頭保持自然，結尾保留約 0.5 秒空白。

## 01｜錯誤訊息有多醜

建議檔名：`01-ugly-error.wav`

上一節結束的時候我留了一個伏筆：在 Swagger 上把資料填錯，回傳的東西是一大坨堆疊訊息。我們現在來看看它有多醜。呼叫一個不存在的客戶，GET /api/customers/999——你會看到 Spring Boot 預設吐回來的東西混著 Tomcat 訊息和 Java 堆疊，前端拿到這個是完全沒辦法處理的。他要顯示什麼給使用者看？更麻煩的是，不同端點出錯的格式還可能不一樣，前端只好每個地方都寫防禦性程式碼。這就是 demo 跟正式系統的差距之一：正式系統的錯誤，是「設計過的」。

## 02｜集中處理：@RestControllerAdvice

建議檔名：`02-controller-advice.wav`

解法叫 @RestControllerAdvice。概念很簡單：與其在每個 Controller 裡寫 try-catch，不如在一個地方——GlobalExceptionHandler——集中定義所有例外的處理方式。每種例外對應一個方法，統一回傳相同結構的 JSON。Controller 從此保持乾淨，只做請求分派這一件事。你只要理解「集中處理」這個設計，程式碼讓 AI 生，你負責核對。

## 03｜ErrorResponse 與 ProblemDetail

建議檔名：`03-problem-detail.wav`

那回傳的結構長什麼樣？我們先自己定義一個 ErrorResponse record，五個欄位：status、error、message、path、timestamp，前端只需要解析這一種結構。不過這裡要告訴你一個更省事的選擇：Spring Boot 3 之後內建了 ProblemDetail，這是 RFC 9457 的業界標準錯誤格式，Spring Boot 4 也延續推薦。一行 ProblemDetail.forStatusAndDetail 就能建立標準格式物件，需要額外欄位就用 setProperty 加。也就是說，連錯誤格式的「規格」都不用自己發明，跟著業界標準走就對了。

## 04｜三種錯誤情境

建議檔名：`04-three-scenarios.wav`

我們順手再建一個自訂例外 ResourceNotFoundException，讓 CustomerService 找不到客戶時拋出它，由 GlobalExceptionHandler 接住轉成 404。你會看到三種情境各有各的樣子：查無資料回 404；驗證失敗回 400，而且 errors 陣列會逐欄告訴你哪裡錯；未預期錯誤回 500——注意 500 的訊息只寫「伺服器發生錯誤，請稍後再試」，內部細節絕對不外洩。

## 05｜Log 日誌的三個重點

建議檔名：`05-logging-basics.wav`

錯誤處理好了，下一個問題是：出錯之後，你怎麼知道系統內部發生了什麼事？這就要靠 Log。Spring Boot 預設就有 Logback，你只要在類別上加 Lombok 的 @Slf4j，log 物件就自動注入了。層級從嚴重到細瑣是 ERROR、WARN、INFO、DEBUG、TRACE，預設顯示到 INFO。用法上有三個重點：第一，INFO 記業務關鍵節點——誰新增了什麼客戶、誰刪了什麼資料，讓你不看程式碼也知道系統在做什麼；第二，寫 log 用大括號佔位符，不要用字串拼接，DEBUG 關閉時它根本不會去組字串，效能更好；第三，也是最重要的——密碼、Token、信用卡號絕對不准寫進 log，就算是 DEBUG 層級也不行，因為 log 檔可能被備份、被轉發到第三方。

## 06｜不重啟就能調 Log 層級

建議檔名：`06-actuator-loggers.wav`

再教你一招正式環境的救命技：加入 Actuator 之後，你可以用 PATCH /actuator/loggers 動態調整 Log 層級，完全不用重啟應用。想像半夜線上出問題，你把某個套件臨時調成 DEBUG、看完再調回來，服務全程不中斷。這一段屬於選讀深化內容，指令細節不用記，網站上查得到。

## 07｜拉高視角：AOP 橫切關注點

建議檔名：`07-aop.wav`

最後我們拉高視角想一件事：交易、驗證、例外攔截、Log——你有沒有發現這些東西都不屬於任何一個業務模組，卻每個地方都需要？這類東西叫「橫切關注點」，而 AOP 就是把它們從業務邏輯抽出來、只寫一次的設計思想。五個詞彙記起來：Join Point 是可以被攔截的時間點，Pointcut 是挑選哪些要攔，Advice 是攔到之後做什麼，Aspect 是前兩者的組合包，Weaving 是套上去的過程。Spring 的實現方式是 Proxy——你注入的 Bean 其實是代理物件，它先做 Advice 再呼叫你的真實方法。那要不要自己寫 AOP？坦白說很少。@Transactional、@Valid、@RestControllerAdvice 這些你已經在用的標註，背後全都是 Spring 幫你寫好的 AOP。真正需要自己動手的，大概只有「對所有方法計時」或「統一寫稽核操作 Log」這種現成標註做不到的需求。

## 08｜本節總結與下一節

建議檔名：`08-closing.wav`

總結一句：這一節我們讓系統「錯得有格式、查得有紀錄」，而且理解了這些能力背後共同的 AOP 設計。API 好懂了、錯誤漂亮了，但它還是不設防——任何人都能刪客戶。下一節，Spring Security 加 JWT，正式幫系統裝上大門。我們下一節見。

## 邊念邊操作提示卡

1. 先顯示錯誤 response schema，逐欄念 status、code、message、path、traceId 和欄位錯誤。
2. 依序送出驗證錯誤、查無資料、衝突與未預期錯誤；每次停在 response，再切到 log 對照同一 traceId。
3. 打開 log 設定，指出 Authorization、JWT、密碼與個資應遮罩；畫面若有洩漏就保留並示範修正。
4. 結尾說明 500 不能被吞成 200，並保存四類案例。

## 交付檔案命名

```text
01-ugly-error.wav
02-controller-advice.wav
03-problem-detail.wav
04-three-scenarios.wav
05-logging-basics.wav
06-actuator-loggers.wav
07-aop.wav
08-closing.wav
```
