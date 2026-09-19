# 03-sse-streaming｜SSE 前端串流旁白稿

本稿依據 `03-sse-streaming.md` 的教學素材與口語稿整理，供人工錄音使用。共 8 段，錄音時可依段落分開保存，方便後續與投影片時間軸對齊。

建議錄音格式：WAV 或 MP3。每段開頭保持自然，結尾保留約 0.5 秒空白。

## 01｜為什麼需要打字機效果

建議檔名：`01-why-streaming.wav`

前兩節我們把後端的 AI 大腦做好了：會記憶、會呼叫工具查真實資料。但你有沒有想過，如果使用者按下送出之後，畫面卡住轉圈圈十幾秒，最後「啪」一次吐出一大段文字，體驗會多糟？

現在大家已經被 ChatGPT 訓練出期待了：AI 回話就該一個字一個字跳出來。這不是炫技，而是等待感受完全不同——模型一邊生成、前端一邊顯示，使用者第一秒就看到回應在長出來。這一節，我們就把這個打字機效果做進 React 前端。

## 02｜選型：SSE 而不是 WebSockets

建議檔名：`02-sse-vs-websockets.wav`

先做技術選型。要讓伺服器持續推資料給瀏覽器，你可能第一個想到 WebSockets。但停下來想一下 AI 聊天的資料流向：模型的回應是「單向、持續產生」的，後端一直推，前端在過程中根本不需要頻繁回傳資料。

WebSockets 是雙向通道，適合多人遊戲、協作白板那種你來我往的場景，協定比較繁重；而 SSE，Server-Sent Events，是基於標準 HTTP 的單向推播，開發簡單、開銷小，還天生支援斷線重連。所以結論很清楚——大模型流式生成，選 SSE。

## 03｜Spring AI 與 EventSource 的天作之合

建議檔名：`03-stream-eventsource.wav`

更棒的是，Spring AI 的 .stream() 方法預設輸出的就是標準 text/event-stream 格式，跟 SSE 完美搭配，後端幾乎不用多做什麼。前端也輕鬆：瀏覽器內建的 EventSource 物件就能建立連線，一行第三方套件都不用裝。

這就是我常說的「先懂架構、再讓 AI 開發」的意義：你知道兩邊的協定本來就對得起來，AI 生出來的程式碼你才看得懂它在幹嘛，出問題也知道往哪查。

## 04｜第一道牆：EventSource 不能自訂 Header

建議檔名：`04-eventsource-wall.wav`

不過這裡有一堵所有人第一次做都會撞到的牆，我先幫你把坑指出來：原生的 EventSource「沒辦法自訂 Header」。

我們第四章辛苦做的 JWT 認證，靠的是 Authorization 標頭帶 Token，可是 EventSource 連這個門都進不去——等於已登入的使用者，反而被自己的安全機制擋在外面。這個限制是瀏覽器規格造成的，不是你程式寫錯，所以解法要從設計面下手。

## 05｜解法：JWT 改走 Query Token

建議檔名：`05-query-token.wav`

怎麼辦？兩邊各改一步。後端：修改 JwtAuthenticationFilter 的 parseJwt 方法，讓它除了從 Authorization 標頭讀 Token，也支援從 URL 的 Query 參數、例如 token 這個參數，取得並驗證 Token。前端：在 ChatRoom.jsx 建立連線時，從 localStorage 把之前登入存好的 JWT 讀出來，用網址參數帶上去，像 /api/ai/stream 加上 message 跟 token 兩個參數這樣。

這樣既保住「只有登入的人才能用聊天」，又繞過了 EventSource 的限制。完整的提示詞在教學網站上，直接複製給 AI Agent，它會幫你把後端過濾器跟前端連線一起改好。

## 06｜打字機效果的原理：React 狀態管理

建議檔名：`06-react-state.wav`

接著看前端怎麼渲染。EventSource 每收到一個字元片段，就把它累加進 React 的 state，state 一變 React 就重新渲染——這就是打字機效果的全部原理，靠的就是狀態管理，沒有任何魔法。

另外別忘了 session 的前端管理：使用者按「清除對話」時，不是把訊息陣列清空就好，對話的 session 識別也要一起重建，不然舊記憶還掛在後端，這是上一節那三條鐵律在前端的落實。

## 07｜再推一步：對話裡長出卡片

建議檔名：`07-cards.wav`

做到這裡功能已經完整了，但我們可以再往質感推一步。CRM 智慧工作台的 AI 回應，不該只是死板的 Markdown 文字。我們在 ChatRoom.jsx 用「自動偵測與條件渲染」機制：串流過程中，detectAndAttachCards 函式拿目前累積的完整回應去做關鍵字匹配，比對到客戶名稱、像亞太智能製造或環球零售巨擘，或者提到商機相關字眼，就在訊息物件上附加 cards 資料；渲染訊息列表時，只要訊息帶有 cards 屬性，就依 cardType 渲染出客戶摘要卡片、商機卡片或建議行動卡片。

你會看到 AI 講到亞太智能製造的生意機會時，對話流裡直接長出一張有金額、成交機率、銷售階段的卡片——這就是聊天介面跟智慧工作台的差別。

## 08｜驗收與下一節

建議檔名：`08-verify-and-next.wav`

驗證一下：登入後開聊天室，問「有哪些客戶」，你要看到三件事——文字逐字跳出、卡片自動出現、卡片上的數字跟資料庫一致。然後登出、或不帶 Token 直接打 stream 網址，要被擋下來。數字由工具算、文字由模型寫，現在再加一句：體驗由串流撐。

總結一句：SSE 加 EventSource 加 React 狀態管理，就是 AI 聊天體驗的標準解法，Query Token 補上了認證的最後一塊。下一節我們跳出程式碼，從商業價值的角度看看這套 AI CRM 助理到底值多少錢。我們下一節見。

## 邊念邊操作提示卡

1. 先用非串流請求確認回答正常，再切到瀏覽器 Network 的 `text/event-stream`；旁白念到「現在逐段到達」時停在事件流畫面。
2. 送出一個問題，示範文字逐字累加；再展示完成、錯誤、關閉和重連狀態。
3. 說明 EventSource 的 token 限制，展示 HTTPS、短效 query token 與 log 遮罩檢查，不要在錄影中露出真 token。
4. 最後示範客戶摘要卡片和清除 session，保存 Network、畫面與安全檢查結果。

## 交付檔案命名

```text
01-why-streaming.wav
02-sse-vs-websockets.wav
03-stream-eventsource.wav
04-eventsource-wall.wav
05-query-token.wav
06-react-state.wav
07-cards.wav
08-verify-and-next.wav
```
