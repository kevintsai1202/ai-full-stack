# 02-rest-api-design｜REST API 設計原則旁白稿

本稿依據 `02-rest-api-design.md` 的教學素材與口語稿整理，供人工錄音使用。共 8 段，錄音時可依段落分開保存，方便後續與投影片時間軸對齊。

建議錄音格式：WAV 或 MP3。每段開頭保持自然，結尾保留約 0.5 秒空白。

## 01｜沒有規則的 API 有多痛

建議檔名：`01-api-pain.wav`

這一節我們來談 API 的設計。先講一個真實世界的痛點：如果你接手過別人的專案，很可能看過這種 API——getCustomers、createCustomer、deleteCustomer 問號 id 等於 1，而且刪除居然是用 GET。

這種 API 的問題是什麼？每一支的命名都是作者當下的心情，沒有規則可循，接手的人要一支一支猜。REST 就是為了解決這個問題而存在的一套設計風格。注意我的用詞，它是「風格」，不是協定也不是規範，英文叫 Architectural Style——它沒有強制力，但整個業界都用它當共同語言。

## 02｜核心口訣：URL 是名詞，方法是動詞

建議檔名：`02-noun-verb.wav`

REST 的核心思想只有一句話，你把這句記起來，這一節就值回票價了：URL 是名詞，HTTP 方法是動詞。

系統裡的每一種資料，都被當成一種「資源」，用一個固定的 URL 表示。以我們的課程為例，/api/customers 就是「客戶」這個資源。要拿全部客戶？GET /api/customers。要拿第 1 號客戶？GET /api/customers/1。要新增？一樣的路徑，換成 POST。更新用 PUT，刪除用 DELETE。

你發現了嗎？路徑幾乎不變，變的只有 HTTP 方法。這樣的 API，別人不用看文件就能猜到八成的行為。

## 03｜無狀態：Stateless

建議檔名：`03-stateless.wav`

再補充一個 REST 的重要特性：它是無狀態的，Stateless。意思是每一次請求都要自己帶齊所有必要的資訊，伺服器不會記得你上一次做了什麼。

這個特性現在聽起來有點抽象沒關係，後面講到 JWT 認證的時候，我們會再回來呼應它——你到時候就會明白，為什麼認證資訊要每次都帶在請求裡。

## 04｜HTTP 方法對應 CRUD 與冪等性

建議檔名：`04-crud-idempotent.wav`

接下來看 HTTP 方法跟資料庫 CRUD 的對應：GET 對 Read、POST 對 Create、PUT 和 PATCH 對 Update、DELETE 對 Delete。

這裡有個觀念叫「冪等」，很值得記。GET 是安全且冪等的，同一個請求發一百次，結果都一樣，資料不會被改到。POST 通常不是冪等的，按兩次送出就會建立兩筆資料——這就是為什麼有些網站會提醒你「請勿重複點擊」。PUT 和 PATCH 是冪等的，重複更新同一份資料，結果相同。

在 Spring Boot 裡，這些方法各自對應一個註解：@GetMapping、@PostMapping、@PutMapping、@PatchMapping、@DeleteMapping，非常直觀。

## 05｜狀態碼與 ResponseEntity

建議檔名：`05-status-codes.wav`

狀態碼的部分，先記住幾個常客：200 是成功、201 是建立成功、404 是找不到資源、400 是請求格式錯誤或驗證失敗、500 是伺服器自己出包。完整的速查表在教學網站上，不用背，需要的時候查得到。

在 Controller 裡面，我們會用 ResponseEntity 來明確控制狀態碼——ResponseEntity.ok 回 200，ResponseEntity.notFound 回 404，POST 方法加上 @ResponseStatus(HttpStatus.CREATED) 回 201。如果什麼都不設定，Spring Boot 預設成功給 200、例外給 500。

## 06｜銷售漏斗：CRM 的核心流程

建議檔名：`06-sales-funnel.wav`

那這些設計原則跟我們的 CRM 有什麼關係？這裡要先介紹 CRM 領域最重要的概念——銷售漏斗。客戶從「潛在客戶」開始，經過「接觸中」、「洽談中」，最後不是「已成交」就是「已流失」，每一階段都會流失一部分人，所以形狀像漏斗。

我們課程裡的三家示範客戶正好落在漏斗的不同位置：APIM 是已成交的高價值客戶，GlobalMart 是洽談中但可能流失的風險客戶，ApexFin 是已到期需要緊急聯繫的流失客戶。AI CRM 助理的核心任務之一，就是根據客戶在漏斗中的位置給出不同的應對策略。

## 07｜漏斗如何對應 API 設計

建議檔名：`07-funnel-api.wav`

而漏斗反映到 API 設計上就是：GET /api/opportunities 列出所有商機和它們的漏斗階段，PATCH /api/opportunities/1/stage 把某筆商機從洽談中推進到已成交。你看，業務流程和 API 設計是一體兩面的——先懂業務，路徑自然就設計得出來。

我們現在就來暖身。教學網站上有一段提示詞：請 AI 做一個簡單版的客戶資料功能，可以看全部客戶、看某一個客戶、找不到要明確告訴我、以及新增客戶，資料先暫存在程式裡，不用接資料庫。直接複製貼給 AI Agent，你會看到它產出的路徑設計，正是我們剛剛講的那套 REST 風格。

## 08｜本節總結與下一節

建議檔名：`08-closing.wav`

一句話總結：URL 是資源名詞，HTTP 方法是動作動詞，狀態碼說明結果。

下一節，我們來看這些 API 的內部該怎麼分層——Controller 和 Service 各自要負責什麼。我們下一節見。

## 邊念邊操作提示卡

1. 錄製先顯示 customers 的契約表，逐行念 URL、method、輸入與成功狀態；不要直接跳到完成的 Swagger 畫面。
2. 實際執行一個 GET 和一個 POST，再執行查無資料與錯誤輸入；每個回應停兩秒，讓學員看清 status 和 JSON body。
3. 游標指向 URL 中的資源名，說明為什麼不用動詞式路徑；畫面旁保留錯誤格式，示範前端能依一致結構處理。
4. 收尾把契約表和 HTTP 證據存到同一資料夾，念出下一單元會依這份契約拆分責任。

## 交付檔案命名

```text
01-api-pain.wav
02-noun-verb.wav
03-stateless.wav
04-crud-idempotent.wav
05-status-codes.wav
06-sales-funnel.wav
07-funnel-api.wav
08-closing.wav
```
