# 章節 2 單元 3｜分層實作的好處

## 單元定位

前兩節建立了請求流程與 REST 設計原則的觀念，本節進入實作：用 AI Agent 建立一個「不依賴資料庫、可立即啟動」的客戶 REST API 示範專案，並在過程中理解 Controller / Service 的分工——這是本章的核心原則，也是下一章接入 JPA 時 Controller 能夠零修改的關鍵。同場加映 Lombok，省去樣板程式碼。建議時長：18～20 分鐘。

## 教學素材

### 核心原則

分層架構（DispatcherServlet → Controller → Service → Repository）是 Spring MVC 的靈魂。Controller 負責轉接與基本驗證，Service 負責商業邏輯，切忌將邏輯混在一起，才能維持程式碼的長期維護性。

### Controller / Service 怎麼分工

- **Controller** — 只處理 HTTP 輸入輸出（接請求、處理輸入參數、決定回傳格式），不含業務判斷
- **Service** — 負責業務規則與資料操作；本章用 List 模擬資料，下一章換成 JPA Repository
- 兩層分開的好處：日後更換資料來源（從 List 換成 JPA、從 JPA 換成其他 ORM）時，只改 Service，Controller 零修改

本章示範專案刻意只保留 Controller 與 Service 兩層，資料直接放在 Service 的 Java List 裡，讓你先把 Spring MVC 的流程跑通。下一章才會在 Service 下方再加一層 Repository 對接真正的資料庫。

```text
[瀏覽器 / 前端]
    ↓ HTTP 請求
[Controller]  ← 處理參數與 HTTP 格式
    ↓ 呼叫業務方法
[Service]     ← 業務邏輯 + 資料操作
    ↓
[List<Customer>]  ← 記憶體資料（本章不用資料庫）

下一章的完整三層：
[Controller] → [Service] → [Repository] → [資料庫]
```

### Lombok：省去樣板程式碼

Lombok 是 Java 的編譯期程式碼產生器，透過註解在編譯時自動加上 getter、setter、建構子、`toString`、`equals` 等方法，讓類別定義只保留欄位本身。在 Spring Boot 專案中，只要 `pom.xml` 引入 Lombok 依賴，VS Code 的 Java 擴充套件就會自動識別，不需要額外外掛。

- `@Data` — 等於同時加上 getter、setter、toString、equals、hashCode
- `@NoArgsConstructor` — 產生無參數建構子（JPA 之後會需要）
- `@AllArgsConstructor` — 產生包含所有欄位的建構子，方便初始化測試資料
- `@Builder` — 產生 Builder 模式，適合欄位較多的類別

```java
// 不用 Lombok：每個欄位都要手寫 getter/setter，程式碼冗長
public class Customer {
    private Long id;
    private String name;
    private String level;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    // ...繼續寫 name、level 的 getter/setter...
}

// 使用 Lombok：三個註解搞定一切
@Data                   // 自動產生 getter / setter / toString / equals
@NoArgsConstructor      // 無參建構子
@AllArgsConstructor     // 全欄位建構子
public class Customer {
    private Long id;
    private String name;
    private String level;        // VIP / General / New
    private String email;
}
```

### 用 AI Agent 建立可獨立運行的示範專案

在進入 JPA 與資料庫之前，先請 AI Agent 建立一個可以立即啟動的 Spring MVC 示範專案，確認 Controller → Service → 回應的流程跑通：

- 只需要 `spring-boot-starter-web`，不需要 JPA 或 PostgreSQL
- 啟動後即可用 PowerShell 測試 API 回應

## 示範與提示詞

AI Agent 提示詞——建立 Spring MVC 示範專案（引自 u2 原文）：

```text
【建立專案】
我有一個 Spring Boot 專案，只有 spring-boot-starter-web 依賴。
請幫我建立一個簡單的客戶 REST API（資料存在記憶體，不用資料庫）：
- GET /api/customers → 回傳全部客戶
- GET /api/customers/{id} → 找不到回傳 404
- POST /api/customers → 新增客戶，回傳 201
請加上中文函式級別註解。

【驗證 API】
專案啟動後（port 8080），請幫我用 PowerShell Invoke-RestMethod 測試上面三個端點是否正常回應。

【排查錯誤】
執行 mvn spring-boot:run 出現以下錯誤：
[貼上錯誤訊息]
請幫我找出原因並修正。
```

延伸練習提示詞（引自 u2 的 AI 提示詞練習）：

```text
「現在的 CustomerService 用 List 存資料，如果我要換成 HashMap 以加速 ID 查詢，應該怎麼改？請幫我重寫 findById 的邏輯。」

「請幫我在 CustomerController 加一個 DELETE /api/customers/{id} 端點，成功刪除回傳 204，找不到回傳 404。」
```

## 口語稿

這一節我們要真的動手把 API 做出來了。但在動手之前，先講一個你未來一定會遇到的災難場景：有一種 Controller，裡面塞了三百行程式碼——收參數、判斷業務規則、組資料、算折扣、回傳 JSON，全部混在同一個方法裡。這種程式碼寫的當下很爽，三個月後要改一個規則，你會發現動一行、壞三處，而且完全沒辦法寫測試。這就是為什麼我說，分層架構是 Spring MVC 的靈魂。

分層的原則其實很簡單：Controller 負責轉接和基本驗證，Service 負責商業邏輯，兩邊的邏輯切忌混在一起。講白話一點，Controller 就是接待櫃檯——它只管接 HTTP 請求、把參數整理好、決定回傳格式和狀態碼，它不做任何業務判斷；Service 才是真正幹活的部門，業務規則、資料操作都在這裡。這樣分有什麼實際好處？最直接的一個：這一章我們的資料放在記憶體的 List 裡，下一章要換成 PostgreSQL 加 JPA，到時候你只需要改 Service，Controller 一行都不用動。換資料來源不動門面，這就是分層的威力。

這裡我要特別說明一個教學設計：這一章的示範專案，刻意只有 Controller 和 Service 兩層，資料就放在 Service 裡的一個 Java List。為什麼不一次到位直接上資料庫？因為我要你先把 Spring MVC 的流程「跑通」——請求進來、路由、呼叫 Service、回 JSON，這條路先走順了，下一章再往 Service 底下加一層 Repository 接資料庫，你就會很清楚每一層在做什麼。

動手之前還有一個小工具要介紹：Lombok。寫過 Java 的人都知道那個痛——一個類別四個欄位，你要手寫八個 getter、setter，再加 toString、equals，滿滿兩頁都是樣板程式碼。Lombok 是一個編譯期的程式碼產生器，你只要在類別上標註解，編譯的時候它自動幫你生出這些方法。最常用的三個：@Data，一個註解等於 getter、setter、toString、equals、hashCode 全包；@NoArgsConstructor 產生無參數建構子，這個之後 JPA 會需要；@AllArgsConstructor 產生全欄位建構子，初始化測試資料很方便。還有一個 @Builder，欄位多的類別用起來特別舒服。在 Spring Boot 專案裡，pom.xml 引入 Lombok 依賴之後，VS Code 的 Java 擴充套件會自動識別，不用再裝額外外掛。

好，我們現在來實際操作。打開 AI Agent，把這段提示詞貼進去：「我有一個 Spring Boot 專案，只有 spring-boot-starter-web 依賴。請幫我建立一個簡單的客戶 REST API，資料存在記憶體，不用資料庫：GET /api/customers 回傳全部客戶、GET /api/customers/{id} 找不到回傳 404、POST /api/customers 新增客戶回傳 201，請加上中文函式級別註解。」注意這段提示詞的細節：我明確講了依賴只有 web、明確講了三個端點和它們的狀態碼行為。你會看到 AI 產出一個 Customer 的 Model、一個 CustomerService、一個 CustomerController，正好就是我們剛講的分層。產出來之後，別急著跑，先看一眼：Controller 裡面有沒有混業務邏輯？Service 是不是乾淨地管著那個 List？這個檢查動作，就是你作為架構把關者的角色。

專案啟動之後，用第二段提示詞請 AI 幫你用 PowerShell 的 Invoke-RestMethod 把三個端點都打一遍，你會看到 GET 回傳客戶清單的 JSON、POST 回 201。如果 mvn spring-boot:run 噴錯也不用慌，把錯誤訊息原封不動貼給 AI 排查，這就是第一章講的協作流程。行有餘力，再做兩個延伸練習：請 AI 把 List 換成 HashMap 加速查詢，或者加一個 DELETE 端點、成功回 204。這兩個練習會讓你體會到——改的都只有一層，另一層不動。

總結一句：Controller 管門面、Service 管邏輯，層分清楚，換資料來源才不用大動土木。下一節我們來補一個目前這支 API 的大漏洞——它現在前端傳什麼就存什麼，我們要加上輸入驗證。
