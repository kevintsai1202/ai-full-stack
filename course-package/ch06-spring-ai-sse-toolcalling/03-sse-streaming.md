# 章節 6 單元 3｜SSE 前端串流

## 單元定位

本節要解決的問題：把後端 AI 的串流回覆接到 React 前端，做出 token-by-token 的即時打字機效果——包含 SSE 與 WebSockets 的選型、原生 EventSource 的使用與安全限制、JWT 以 Query Token 通過認證，以及對話流中的卡片元件渲染。

與前後節的銜接：前兩節完成了「會記憶、會查真實資料」的後端 AI；本節把它端到使用者面前，接上第五章打造的 React CRM 工作台。下一節（單元 4）回頭談這整套助理的商業價值。

建議時長：30 ～ 35 分鐘。

## 教學素材

### 為什麼選擇 SSE（Server-Sent Events）

在 AI 聊天應用中，模型的回應是單向且持續產生的：後端需要持續把資料推給前端，而前端不需要在過程中頻繁向後端傳送資料。

相較於 WebSockets 的雙向複雜協定，SSE 是基於標準 HTTP 的單向推播協定，開發簡單、開銷小，且天生支援斷線重連。Spring AI 的 `.stream()` 預設就是輸出標準的 `text/event-stream` 格式，與 SSE 完美搭配。

- WebSockets：雙向通道，適用於多人遊戲、協作工具，但協定較為繁重。
- SSE（Server-Sent Events）：單向推送，基於 HTTP，最適合大模型流式生成（Streaming）。
- React 連線方式：使用瀏覽器內建的 `EventSource` 物件即可建立連線，不需要引入第三方 WebSocket 庫。

### EventSource 的安全限制與 JWT Query Token

原生的 `EventSource` 無法在 Header 中自訂 Token 進行驗證，因此需要：

1. 後端：修改 `JwtAuthenticationFilter.java` 的 `parseJwt` 方法，除了支援從 `Authorization` 標頭讀取 Token，也支援從 URL 的 Query 參數（例如 `token`）取得並驗證 Token。
2. 前端：`ChatRoom.jsx` 連接 `/api/ai/stream` 時，從 `localStorage` 讀取先前儲存的 JWT Token，以網址參數形式帶入（例如 `/api/ai/stream?message=xxx&token=yyy`），安全地建立 EventSource 串流連線。

搭配 React 狀態（State）把陸續到達的字元片段累加渲染，實現流式打字效果；清除對話時，前端也要一併管理與重建對話 Session。

### 對話中的卡片元件呈現（客戶摘要、商機、建議行動）

在 CRM 智慧工作台中，AI 回應不應只是死板的 Markdown 文字，更應該在適當時機，動態將對應的「客戶摘要卡片、商機卡片、建議行動卡片」嵌入到對話流中，提昇 UI/UX 質感。前端 `ChatRoom.jsx` 採用「自動偵測與條件渲染」機制：

1. 串流偵測（detectAndAttachCards）：當前端 EventSource 接收到後端 AI 推播的字元片段時，將目前的完整對話內容（fullResponse）傳入進行關鍵字匹配。當匹配到客戶名稱（如台積電、聯發科），或涉及商機關鍵字且提及特定客戶時，自動在訊息物件中附加對應的 `cards` 資料。
2. 條件渲染（renderCards）：React 渲染訊息列表時，若訊息物件含有 `cards` 屬性，則依 `cards.cardType` 分別渲染出 `<CustomerSummaryCard />`、`<OpportunityCard />` 或 `<ActionCard />`。

```javascript
/**
 * 偵測 AI 回覆內容並自動對應卡片數據
 */
const detectAndAttachCards = (text) => {
  // 1. 客戶摘要卡片或建議行動卡片偵測
  const matchedCustomers = knownCustomers.filter(c => text.includes(c.name));
  if (matchedCustomers.length > 0) {
    if (text.includes("建議") || text.includes("下一步")) {
// ... 完整程式碼請參考課程 GitHub 專案 ...
  }
};
```

## 示範與提示詞

### AI Agent 提示詞 — 前端串流與安全認證

```text
請在現有專案中完成以下前端與後端的串流對話安全升級：
1. 由於原生的 EventSource 無法在 Header 中自訂 Token 進行驗證，請修改後端 `JwtAuthenticationFilter.java` 的 `parseJwt` 方法，使其除了支援從 `Authorization` 標頭讀取 Token 外，也支援從 URL 的 Query 參數（例如 `token`）中取得並驗證 Token。
2. 在前端 `ChatRoom.jsx` 中，連接後端的 `/api/ai/stream` 串流對話介面。連線時，請從 `localStorage` 中讀取先前儲存的 JWT Token，並以網址參數形式帶入（例如 `/api/ai/stream?message=xxx&token=yyy`），以安全地建立 EventSource 串流對話連線。
```

### 口語化建置提示詞 ③｜在網頁上做出 AI 聊天室

> 即時打字效果，而且要確認是已登入的人

```text
請在網頁上做一個 AI 聊天室，連上剛才的助手，要有訊息即時一個字一個字跳出來的效果。同時要確保只有「已經登入的人」才能使用這個聊天，外人不能亂用。請加中文註解。
```

## 口語稿

前兩節我們把後端的 AI 大腦做好了：會記憶、會呼叫工具查真實資料。但你有沒有想過，如果使用者按下送出之後，畫面卡住轉圈圈十幾秒，最後「啪」一次吐出一大段文字，體驗會多糟？現在大家已經被 ChatGPT 訓練出期待了：AI 回話就該一個字一個字跳出來。這不是炫技，而是等待感受完全不同——模型一邊生成、前端一邊顯示，使用者第一秒就看到回應在長出來。這一節，我們就把這個打字機效果做進 React 前端。

先做技術選型。要讓伺服器持續推資料給瀏覽器，你可能第一個想到 WebSockets。但停下來想一下 AI 聊天的資料流向：模型的回應是「單向、持續產生」的，後端一直推、前端在過程中根本不需要頻繁回傳資料。WebSockets 是雙向通道，適合多人遊戲、協作白板那種你來我往的場景，協定比較繁重；而 SSE，Server-Sent Events，是基於標準 HTTP 的單向推播，開發簡單、開銷小，還天生支援斷線重連。更棒的是，Spring AI 的 .stream() 方法預設輸出的就是標準 text/event-stream 格式，跟 SSE 完美搭配，後端幾乎不用多做什麼。前端也輕鬆：瀏覽器內建的 EventSource 物件就能建立連線，一行第三方套件都不用裝。所以結論很清楚——大模型流式生成，選 SSE。

不過這裡有一個所有人第一次做都會撞到的牆，我先幫你把坑指出來：原生的 EventSource「沒辦法自訂 Header」。我們第四章辛苦做的 JWT 認證，靠的是 Authorization 標頭帶 Token，可是 EventSource 連這個門都進不去，等於已登入的使用者反而被自己的安全機制擋在外面。怎麼辦？兩邊各改一步。後端：修改 JwtAuthenticationFilter 的 parseJwt 方法，讓它除了從 Authorization 標頭讀 Token，也支援從 URL 的 Query 參數、例如 token 這個參數，取得並驗證 Token。前端：在 ChatRoom.jsx 建立連線時，從 localStorage 把之前登入存好的 JWT 讀出來，用網址參數帶上去，像 /api/ai/stream?message=xxx&token=yyy 這樣。這樣既保住「只有登入的人才能用聊天」，又繞過了 EventSource 的限制。

我們現在來實作。把講義裡的提示詞交給 AI Agent，它會幫你改好後端過濾器、接好前端連線。接著看前端怎麼渲染：EventSource 每收到一個字元片段，就把它累加進 React 的 state，state 一變 React 就重新渲染——這就是打字機效果的全部原理，靠的就是狀態管理。另外別忘了 session 的前端管理：使用者按「清除對話」時，不是把訊息陣列清空就好，對話的 session 識別也要一起重建，不然舊記憶還掛在後端。

做到這裡功能已經完整了，但我們可以再往質感推一步。CRM 智慧工作台的 AI 回應，不該只是死板的 Markdown 文字。我們在 ChatRoom.jsx 用「自動偵測與條件渲染」機制：串流過程中，detectAndAttachCards 函式拿目前累積的完整回應去做關鍵字匹配，比對到客戶名稱、像台積電或聯發科，或者提到商機相關字眼，就在訊息物件上附加 cards 資料；渲染訊息列表時，只要訊息帶有 cards 屬性，就依 cardType 渲染出客戶摘要卡片、商機卡片或建議行動卡片。你會看到 AI 講到台積電的商機時，對話流裡直接長出一張有金額、成交機率、銷售階段的漂亮卡片——這就是聊天介面跟智慧工作台的差別。

驗證一下：登入後開聊天室，問「有哪些客戶」，你會看到文字逐字跳出、卡片自動出現，而且卡片上的數字跟資料庫一致；登出或不帶 Token 直接打 stream 網址，會被擋下來。數字由工具算、文字由模型寫，現在再加一句：體驗由串流撐。

總結一句：SSE 加 EventSource 加 React 狀態管理，就是 AI 聊天體驗的標準解法，Query Token 則補上了認證的最後一塊。下一節我們跳出程式碼，從商業價值的角度看看這套 AI CRM 助理到底值多少錢。
