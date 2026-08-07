# 章節 2 單元 2｜REST API 設計原則

## 單元定位

上一節理解了請求怎麼流進 Spring Boot，這一節要解決的問題是：「API 的路徑和方法該怎麼設計，才不會做出一堆別人看不懂的介面？」核心口訣只有一句：URL 是資源名詞，HTTP 方法是動作動詞。同時建立 HTTP 方法對應 CRUD、常見狀態碼、以及請求／回應結構的完整地圖，並用 CRM 的銷售漏斗說明這些設計如何對應真實業務。下一節進入 Controller / Service 的分層實作。建議時長：15～18 分鐘。

## 教學素材

### 什麼是 REST API

REST（Representational State Transfer）是一種以 HTTP 協定為基礎的 API 設計風格。核心思想：把系統中的每一種資料或功能都當成「資源（Resource）」，用統一的 URL 路徑表示，再搭配 HTTP 方法（動詞）決定你要對這個資源做什麼事。

- REST 不是協定或規範，而是一套設計風格（Architectural Style）
- URL 表示「資源是什麼」，HTTP 方法表示「對資源做什麼動作」
- REST API 是無狀態（Stateless）的——每次請求都要帶齊所有必要資訊，伺服器不記住上次
- 回傳格式通常為 JSON，因為它輕量、跨語言且容易閱讀

```text
不好的設計（把動作寫進 URL）：
  GET /getCustomers
  POST /createCustomer
  GET /deleteCustomer?id=1

REST 的設計（URL 是資源，動詞由 HTTP Method 決定）：
  GET    /api/customers        → 取得所有客戶
  GET    /api/customers/1      → 取得 ID=1 的客戶
  POST   /api/customers        → 新增一筆客戶
  PUT    /api/customers/1      → 更新 ID=1 的客戶
  DELETE /api/customers/1      → 刪除 ID=1 的客戶
```

### 銷售漏斗：CRM 的核心流程

銷售漏斗（Sales Funnel）是一個視覺化模型，描述客戶從「潛在客戶」到「成交」的完整流程，每一階段都會流失一部分客戶，形狀像一個漏斗：

- **潛在客戶（Lead）**：尚未接觸，但有機會成為客戶的對象
- **接觸中（Contacted）**：已建立聯繫，正在了解需求
- **洽談中（Negotiating）**：正在討論合作條件與報價
- **已成交（Closed Won）**：成功簽約，轉為正式客戶
- **已流失（Closed Lost）**：未能成交，但仍可追蹤原因

本課程的三家示範客戶正好處於漏斗的不同階段：APIM 是已成交的高價值客戶，GlobalMart 是洽談中但可能流失的風險客戶，ApexFin 是已到期需要緊急聯繫的流失客戶。AI CRM 助理的核心任務之一，就是根據客戶在漏斗中的位置提供不同的應對策略。對應的 API 設計：

```text
GET    /api/opportunities         → 列出所有商機及其漏斗階段
GET    /api/opportunities/1       → 查看特定商機的詳情
PATCH  /api/opportunities/1/stage → 更新商機階段（例如從 Negotiating 推進到 Closed Won）
```

### 請求與回應的結構

每次 API 呼叫都包含兩段：你送出的 Request 與伺服器回應的 Response，兩者都有 Header 與 Body。理解結構有助於除錯時知道要看哪個部分。

```text
── HTTP Request（前端送出）──────────────────────
POST /api/customers HTTP/1.1
Host: localhost:8080
Content-Type: application/json    ← Header：告知伺服器 Body 格式

{                                  ← Body：實際資料（JSON）
  "name": "台積電",
  "level": "VIP",
  "email": "contact@tsmc.com",
  "notes": "半導體龍頭，高價值潛在客戶",
  "status": "Active"
}

── HTTP Response（Spring Boot 回傳）─────────────
HTTP/1.1 201 Created              ← 狀態碼
Content-Type: application/json    ← Header

{                                  ← Body：建立完成的資料（含 ID）
  "id": 7,
  "name": "台積電",
  ...
}
```

### HTTP 方法與 CRUD 對應

| HTTP 方法 | CRUD 操作 | 說明 | Spring Boot 註解 |
|---|---|---|---|
| GET | Read | 讀取資料，不修改伺服器狀態 | `@GetMapping` |
| POST | Create | 新增資料，回傳 201 Created | `@PostMapping` |
| PUT | Update | 整筆更新，需傳入完整資料 | `@PutMapping` |
| PATCH | Update | 部分更新，只傳要修改的欄位 | `@PatchMapping` |
| DELETE | Delete | 刪除指定資源 | `@DeleteMapping` |

- GET 是安全且冪等的——同樣的請求重複發多次，結果一樣、資料不受影響
- POST 通常不是冪等的——發兩次就會建立兩筆資料
- PUT / PATCH 是冪等的——重複更新同一份資料，結果相同
- 本課程以 GET / POST 為主，涵蓋查詢客戶與新增客戶兩個最常見情境

### HTTP 狀態碼速查

```text
2xx 成功
  200 OK           → 請求成功，回傳資料（GET / PUT / DELETE）
  201 Created      → 資源建立成功（POST 新增後回傳）
  204 No Content   → 成功但不回傳內容（DELETE 常用）

4xx 用戶端錯誤
  400 Bad Request  → 請求格式錯誤或欄位驗證失敗
  401 Unauthorized → 未提供或提供了無效的身份憑證
  403 Forbidden    → 有身份但沒有權限執行此操作
  404 Not Found    → 找不到指定資源（ID 不存在）

5xx 伺服器錯誤
  500 Internal Server Error → 伺服器端發生未預期的例外
```

在 Controller 中用 `ResponseEntity` 回傳時需要明確指定：

- `ResponseEntity.ok(data)` → 200 OK
- `ResponseEntity.notFound().build()` → 404 Not Found
- `@ResponseStatus(HttpStatus.CREATED)` 加在 POST 方法上 → 201 Created
- 未明確設定時，Spring Boot 成功回傳預設為 200，例外預設為 500

## 示範與提示詞

本節搭配的暖身實作提示詞（引自 u2 提示詞 ①）：

```text
請幫我做一個簡單版的客戶資料功能來練手：可以看全部客戶、看某一個客戶（如果找不到要明確告訴我）、以及新增客戶。資料先暫時存在程式裡就好，還不用接資料庫。請加上中文註解。做完後我要能實際呼叫這些功能拿到結果。
```

## 口語稿

這一節我們來談 API 的設計。先講一個真實世界的痛點：如果你接手過別人的專案，很可能看過這種 API——getCustomers、createCustomer、deleteCustomer 問號 id 等於 1，而且刪除居然是用 GET。這種 API 的問題是什麼？每一支的命名都是作者當下的心情，沒有規則可循，接手的人要一支一支猜。REST 就是為了解決這個問題而存在的一套設計風格。注意我的用詞，它是「風格」，不是協定也不是規範，英文叫 Architectural Style——它沒有強制力，但整個業界都用它當共同語言。

REST 的核心思想只有一句話，你把這句記起來，這一節就值回票價了：URL 是名詞，HTTP 方法是動詞。系統裡的每一種資料，都被當成一種「資源」，用一個固定的 URL 表示。以我們的課程為例，/api/customers 就是「客戶」這個資源。你要拿全部客戶？GET /api/customers。要拿第 1 號客戶？GET /api/customers/1。要新增？一樣的路徑，換成 POST。更新用 PUT，刪除用 DELETE。你發現了嗎？路徑幾乎不變，變的只有 HTTP 方法。這樣的 API，別人不用看文件就能猜到八成的行為。

再補充一個 REST 的重要特性：它是無狀態的，Stateless。意思是每一次請求都要自己帶齊所有必要的資訊，伺服器不會記得你上一次做了什麼。這個特性後面講到 JWT 認證的時候會再回來呼應。

接下來看 HTTP 方法跟資料庫 CRUD 的對應：GET 對 Read、POST 對 Create、PUT 和 PATCH 對 Update、DELETE 對 Delete。這裡有個觀念叫「冪等」，很值得記：GET 是安全且冪等的，同一個請求你發一百次，結果都一樣，資料不會被改到；POST 通常不是冪等的，你按兩次送出就會建立兩筆資料——這就是為什麼有些網站會提醒你「請勿重複點擊」；PUT 和 PATCH 是冪等的，重複更新同一份資料結果相同。在 Spring Boot 裡，這些方法各自對應一個註解：@GetMapping、@PostMapping、@PutMapping、@PatchMapping、@DeleteMapping，非常直觀。

狀態碼的部分，你先記住幾個常客：200 是成功、201 是建立成功、404 是找不到資源、400 是請求格式錯誤或驗證失敗、500 是伺服器自己出包。在 Controller 裡面，我們會用 ResponseEntity 來明確控制狀態碼——ResponseEntity.ok 回 200，ResponseEntity.notFound 回 404，POST 方法加上 @ResponseStatus(HttpStatus.CREATED) 回 201。如果你什麼都不設定，Spring Boot 預設成功給 200、例外給 500。

那這些設計原則跟我們的 CRM 有什麼關係？這裡要先介紹 CRM 領域最重要的概念——銷售漏斗。客戶從「潛在客戶」開始，經過「接觸中」、「洽談中」，最後不是「已成交」就是「已流失」，每一階段都會流失一部分人，所以形狀像漏斗。我們課程裡的三家示範客戶正好落在漏斗的不同位置：APIM 是已成交的高價值客戶，GlobalMart 是洽談中但可能流失的風險客戶，ApexFin 是已到期需要緊急聯繫的流失客戶。AI CRM 助理的核心任務之一，就是根據客戶在漏斗中的位置給出不同的應對策略。而反映到 API 設計上就是：GET /api/opportunities 列出所有商機和它們的漏斗階段，PATCH /api/opportunities/1/stage 把某筆商機從洽談中推進到已成交。你看，業務流程和 API 設計是一體兩面的。

我們現在就來暖身。把這段提示詞丟給 AI Agent：「請幫我做一個簡單版的客戶資料功能來練手：可以看全部客戶、看某一個客戶、找不到要明確告訴我、以及新增客戶，資料先暫存在程式裡，不用接資料庫。」你會看到 AI 產出的路徑設計，正是我們剛剛講的那套 REST 風格。

一句話總結：URL 是資源名詞，HTTP 方法是動作動詞，狀態碼說明結果。下一節，我們來看這些 API 的內部該怎麼分層——Controller 和 Service 各自要負責什麼。
