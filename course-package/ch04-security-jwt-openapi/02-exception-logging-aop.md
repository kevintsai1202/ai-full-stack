# 章節 4 單元 2｜全域例外 / log 日誌 / AOP 講解

## 單元定位

單元 1 讓 API「看得懂」，本節讓 API「錯得漂亮、查得到原因」：用 `@RestControllerAdvice` 建立 GlobalExceptionHandler 統一錯誤格式（含 Spring Boot 內建的 ProblemDetail 標準），用 `@Slf4j` 寫出有語意的結構化 Log 並以 Actuator 動態調層級，最後用 AOP 的視角理解這些「橫切關注點」背後的共同設計。AOP 與動態日誌屬於選讀深化內容。建議時長：30～35 分鐘。

## 教學素材

### 沒有全域例外處理的問題

沒有統一例外處理時，Spring Boot 預設的錯誤回應格式混雜了 Tomcat 訊息與 Java 堆疊資訊，前端無法依賴固定結構解析錯誤。更糟的是，不同端點可能回傳完全不同格式的錯誤，增加前端的防禦成本。

`@RestControllerAdvice` 讓你在一個地方定義所有例外的處理方式：每種例外對應一個方法，統一回傳相同結構的 JSON，Controller 本身完全不需要 try-catch。

- 例外處理集中在一個類別，不散落各個 Controller。
- 回傳格式統一，前端只需解析一種結構。
- Controller 保持乾淨，只做「請求分派」這一件事。

### 統一的 ErrorResponse 格式

先定義所有錯誤回應共用的資料結構，使用 Java Record 讓程式碼簡潔，Jackson 自動序列化為 JSON：

```java
/**
 * 統一的 API 錯誤回應格式
 * 所有例外處理方法都回傳此格式，讓前端只需解析一種結構
 */
public record ErrorResponse(
    int status,          // HTTP 狀態碼
    String error,        // 錯誤類型（如 "Not Found"）
    String message,      // 人類可讀的錯誤說明
    String path,         // 發生錯誤的 API 路徑
    LocalDateTime timestamp  // 錯誤發生時間
) {
    /** 快速建立標準錯誤回應的工廠方法 */
    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return new ErrorResponse(status.value(), status.getReasonPhrase(), message, path, LocalDateTime.now());
    }
}
```

### ProblemDetail：Spring Boot 內建標準格式

Spring Boot 3+ 採用 RFC 9457 的 `ProblemDetail` 作為標準錯誤格式，Spring Boot 4 延續支援並推薦使用。不需要自訂 `ErrorResponse`，可直接在 `GlobalExceptionHandler` 回傳 `ProblemDetail` 物件，格式已符合業界標準：

- `ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "客戶不存在")` → 直接建立標準格式物件。
- 可透過 `problemDetail.setProperty("extra", value)` 加入自訂欄位。
- 在 `application.yml` 加上 `spring.mvc.problemdetails.enabled: true` 可讓 Spring 預設用此格式回傳驗證錯誤。

三種典型情境的回應對照（前端可依 `status` 欄位決定顯示方式）：404 客戶不存在、400 驗證失敗（附 `errors` 陣列逐欄說明）、500 未預期錯誤（只回「伺服器發生錯誤，請稍後再試」，不洩漏內部細節）。

搭配自訂業務例外 `ResourceNotFoundException`：CustomerService 找不到客戶時拋出，由 GlobalExceptionHandler 統一轉成 404 回應，錯誤語意更清楚。

### Log 日誌：層級、@Slf4j 與動態調整

Spring Boot 預設使用 Logback，透過 SLF4J 提供統一 API，零設定即可使用。五個層級：

- `ERROR` → 系統發生嚴重錯誤，需要立即處理（搭配 `log.error("...", ex)` 記錄完整 stack trace）。
- `WARN` → 可能有問題但系統還能運作（如 retry、fallback 等可自動恢復的情境）。
- `INFO` → 正常業務流程的關鍵節點（預設顯示層級）。
- `DEBUG` → 詳細執行資訊，開發除錯使用。
- `TRACE` → 最詳細層級，通常只在框架內部使用。

Lombok 的 `@Slf4j` 自動注入 `log` 物件，省去手動宣告 Logger 的樣板程式碼：

```java
@Slf4j   // Lombok：自動注入 private static final Logger log = ...
@Service
public class CustomerService {
    public Customer saveCustomer(Customer customer) {
        log.info("新增客戶：name={}, level={}, email={}", customer.getName(), customer.getLevel(), customer.getEmail());
        Customer saved = customerRepository.save(customer);
// ... 完整程式碼請參考課程 GitHub 專案 ...
```

最佳實踐：用 `{}` 佔位符而非字串拼接（DEBUG 關閉時不建立字串、效能更好）；INFO 記錄業務關鍵節點，足以在不看程式碼的情況下理解系統在做什麼；**絕對避免在 Log 記錄密碼、Token、信用卡號**——即使是 DEBUG 層級，log 檔也可能被備份或轉發到第三方。

Log 層級可透過 `application.yml` 逐套件設定；加入 `spring-boot-starter-actuator` 後，可用 `PATCH /actuator/loggers` 在**不重啟應用**的情況下動態調整層級——正式環境排查問題的利器。

### AOP：橫切關注點的統一解法

交易控制、效能計時、權限驗證、Log 記錄——這類邏輯天生不屬於任何單一業務模組，卻需要出現在幾乎每個地方。AOP（面向切面程式設計）把這些「橫切關注點（Cross-cutting Concern）」從業務邏輯抽離，統一定義在一個地方，再宣告「在哪些方法的哪個時間點套用」。

五大核心詞彙：

- **Join Point（連接點）**：可被攔截的時間點；Spring AOP 的 Join Point 就是「方法被呼叫的瞬間」。
- **Pointcut（切入點）**：篩選哪些 Join Point 要套用 Advice 的規則，通常用 execution 表達式描述。
- **Advice（增強/通知）**：要執行的動作，分 Before / After / Around / AfterReturning / AfterThrowing 五種。
- **Aspect（切面）**：Pointcut ＋ Advice 的組合模組。
- **Weaving（織入）**：把 Aspect 應用到目標物件的過程；Spring AOP 在執行期透過 Proxy 完成，不修改 bytecode。

Spring AOP 用 Proxy 實現：`@Autowired` 注入的其實是代理物件，Proxy 先執行 Advice、再呼叫真實方法。有介面時用 JDK 動態 Proxy，無介面時用 CGLIB。

**為什麼很少直接寫 AOP**：Spring 已把常用橫切需求封裝成標註——`@Transactional`（交易 Around Advice）、`@Valid`（驗證 Before Advice）、`@RestControllerAdvice`（例外攔截 AfterThrowing）、`@EnableJpaAuditing`、Actuator 管理端點。直接寫 `@Aspect` 的時機通常只有兩種：需求無法用現有標註表達（例如對所有方法計時、統一寫入稽核操作 Log），或為公司內部框架提供可重用的橫切能力。

## 示範與提示詞

本節與單元 1 共用「② 做一份線上操作說明頁，並統一錯誤訊息」的提示詞，本節負責其中「統一錯誤訊息」的部分：

```text
請幫我做一份「線上的 API 操作說明頁」，讓我能直接在上面看到有哪些功能、並直接測試它們。另外，當操作出錯時（例如資料填錯、找不到、沒權限），都要回給我格式一致、看得懂的錯誤訊息，而不是一堆看不懂的程式錯誤。這個說明頁一樣要登入後才能使用。請加中文註解。
```

驗證方式：在 Swagger UI 用不存在的 id 呼叫 `GET /api/customers/999`，應回 404 且是統一格式的 JSON；POST 一筆缺 name 的客戶，應回 400 並在 `errors` 陣列列出逐欄錯誤；再用 `PATCH /actuator/loggers` 把某套件層級調成 DEBUG，觀察 console 輸出立刻變多。

## 口語稿

上一節結束的時候我留了一個伏筆：在 Swagger 上把資料填錯，回傳的東西是一大坨堆疊訊息。我們現在來看看它有多醜。我們現在來呼叫一個不存在的客戶，GET /api/customers/999——你會看到 Spring Boot 預設吐回來的東西混著 Tomcat 訊息和 Java 堆疊，前端拿到這個是完全沒辦法處理的。他要顯示什麼給使用者看？更麻煩的是，不同端點出錯的格式還可能不一樣，前端只好每個地方都寫防禦性程式碼。這就是 demo 跟正式系統的差距之一：正式系統的錯誤，是「設計過的」。

解法叫 @RestControllerAdvice。概念很簡單：與其在每個 Controller 裡寫 try-catch，不如在一個地方——GlobalExceptionHandler——集中定義所有例外的處理方式。每種例外對應一個方法，統一回傳相同結構的 JSON。Controller 從此保持乾淨，只做請求分派這一件事。

那回傳的結構長什麼樣？我們先自己定義一個 ErrorResponse record，五個欄位：status、error、message、path、timestamp，前端只需要解析這一種結構。不過這裡要告訴你一個更省事的選擇：Spring Boot 3 之後內建了 ProblemDetail，這是 RFC 9457 的業界標準錯誤格式，Spring Boot 4 也延續推薦。你可以直接一行 ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "客戶不存在") 建立標準格式物件，需要額外欄位就用 setProperty 加。也就是說，連錯誤格式的「規格」都不用自己發明，跟著業界標準走就對了。

我們順手再建一個自訂例外 ResourceNotFoundException，讓 CustomerService 找不到客戶時拋出它，由 GlobalExceptionHandler 接住轉成 404。你會看到三種情境各有各的樣子：查無資料回 404、驗證失敗回 400 而且 errors 陣列會逐欄告訴你哪裡錯、未預期錯誤回 500——注意 500 的訊息只寫「伺服器發生錯誤，請稍後再試」，內部細節絕對不外洩。

錯誤處理好了，下一個問題是：出錯之後，你怎麼知道系統內部發生了什麼事？這就要靠 Log。Spring Boot 預設就有 Logback，你只要在類別上加 Lombok 的 @Slf4j，log 物件就自動注入了。層級從嚴重到細瑣是 ERROR、WARN、INFO、DEBUG、TRACE，預設顯示到 INFO。用法上有三個重點：第一，INFO 記業務關鍵節點——誰新增了什麼客戶、誰刪了什麼資料，讓你不看程式碼也知道系統在做什麼；第二，寫 log 用大括號佔位符，不要用字串拼接，DEBUG 關閉時它根本不會去組字串，效能更好；第三，也是最重要的——密碼、Token、信用卡號絕對不准寫進 log，就算是 DEBUG 層級也不行，因為 log 檔可能被備份、被轉發到第三方。

再教你一招正式環境的救命技：加入 Actuator 之後，你可以用 PATCH /actuator/loggers 動態調整 Log 層級，完全不用重啟應用。想像半夜線上出問題，你把某個套件臨時調成 DEBUG、看完再調回來，服務全程不中斷。

最後我們拉高視角想一件事：交易、驗證、例外攔截、Log——你有沒有發現這些東西都不屬於任何一個業務模組，卻每個地方都需要？這類東西叫「橫切關注點」，而 AOP 就是把它們從業務邏輯抽出來、只寫一次的設計思想。五個詞彙記起來：Join Point 是可以被攔截的時間點，Pointcut 是挑選哪些要攔，Advice 是攔到之後做什麼，Aspect 是前兩者的組合包，Weaving 是套上去的過程。Spring 的實現方式是 Proxy——你注入的 Bean 其實是代理物件，它先做 Advice 再呼叫你的真實方法。那要不要自己寫 AOP？坦白說很少。@Transactional、@Valid、@RestControllerAdvice 這些你已經在用的標註，背後全都是 Spring 幫你寫好的 AOP。真正需要自己動手的，大概只有「對所有方法計時」或「統一寫稽核操作 Log」這種現成標註做不到的需求。

總結一句：這一節我們讓系統「錯得有格式、查得有紀錄」，而且理解了這些能力背後共同的 AOP 設計。API 好懂了、錯誤漂亮了，但它還是不設防——任何人都能刪客戶。下一節，Spring Security 加 JWT，正式幫系統裝上大門。
