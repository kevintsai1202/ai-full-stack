# 電子報第 009 期範本：SSE——被低估的單向串流，AI 時代的主角

> **用途**：即時通訊三期系列的第二期。深入 Server-Sent Events（SSE）：
> 為什麼它是 AI 打字機效果的標準解、EventSource 的自動重連機制、以及反向代理緩衝這類生產環境的坑。
> **使用方式**：將下方「內文（Markdown）」複製到 admin 後台，先更新預覽並寄測試信，確認後再正式寄送。
>
> **本期為三期系列的第二期**：
>
> - 008 地圖＋輪詢：即時通訊的本質問題與輪詢家族
> - 009（本期）SSE：單向串流，AI 打字機效果的主角
> - 010 WebSocket／WebRTC／新世代：雙向、點對點與選型總表
>
> **前置條件**：開頭引用上期輪詢內容，**必須在 008 之後寄出**。
>
> 文章中段以「工商時間」卡片（`<!--promo-->` 標記）宣傳 Hahow 課程（SSE 與課程的
> Spring AI 串流單元直接相關），**優惠碼與期限需在寄送前確認後填入**。

---

## 建議主旨（三選一）

1. `ChatGPT 的打字機效果，背後是一個 2004 年的老技術`
2. `SSE：三行程式碼就能讓伺服器主動說話`
3. `AI 串流回覆該用 WebSocket 嗎？先認識 SSE 再決定`

**推薦**：`ChatGPT 的打字機效果，背後是一個 2004 年的老技術`

## 預覽文字（preheader）

> 單向、純文字、自帶斷線重連——SSE 的三個「限制」，恰好是 AI 串流場景的三個完美貼合。

---

## 內文（Markdown，直接複製貼上）

```markdown
# SSE——被低估的單向串流，AI 時代的主角

嗨，我是凱文大叔。

上期講完輪詢家族：「你去問」的模式適合低頻鬆即時的場景，但頻率一高就是災難。這期進入「它來說」的世界，先從最容易被跳過、卻在 AI 時代意外翻紅的主角開始：**SSE（Server-Sent Events）**。

你每天都在用 SSE，只是不知道。ChatGPT、Claude、Gemini 的回答逐字浮現的「打字機效果」——那不是前端動畫，是**伺服器真的一小段一小段把文字推過來**，而承載這件事的協定，絕大多數就是 SSE。

## SSE 是什麼：一條「不掛斷的 HTTP」

SSE 的原理簡單到令人懷疑：瀏覽器發一個普通的 HTTP GET，伺服器回應 `Content-Type: text/event-stream`，然後**不關閉連線**，有資料就往裡面寫一段：

```text
data: 第一則訊息

data: 第二則訊息
```

就這樣。沒有新協定、沒有握手升級，就是一條不掛斷的 HTTP 回應。這帶來三個很實際的好處：

- **防火牆、代理、企業網路一路綠燈**——它就是 HTTP，不會像 WebSocket 偶爾被中間設備擋掉
- **瀏覽器原生支援**，前端三行就能動：

```js
const es = new EventSource('/api/stream');
es.onmessage = (e) => render(e.data);
es.onerror   = () => console.log('瀏覽器會自動重連，通常不用你管');
```

- **自動重連是規格內建的**——斷線後瀏覽器自己重連，還會帶上 `Last-Event-ID` 標頭告訴伺服器「我收到哪了」，讓伺服器能補發漏掉的事件。WebSocket 的重連要自己寫，SSE 送你。

當然，它有兩個明確的限制：**單向**（只能伺服器→瀏覽器；瀏覽器要說話請另外發 HTTP 請求）、**純文字**（UTF-8；二進位資料不行）。

## 什麼場景該選 SSE

共同特徵：**資料主要往一個方向流**。

- **AI 串流回覆**：LLM 逐 token 輸出，天生單向、天生文字——SSE 跟這個場景貼合到像是為它發明的
- **通知、動態牆**：新留言、新訂單、系統廣播
- **進度回報**：長任務的百分比、部署日誌的逐行輸出
- **看板數據**：股價、監控指標、比分

反過來說，聊天室、協作編輯這種**雙向高頻**的場景，SSE 就不是主角了——那是下期 WebSocket 的地盤。

<!--promo-->
### 工商時間

想親手做一次 AI 打字機效果？我的 Hahow 課程 **《AI 賦能全端開發：從零打造企業級智慧應用》** 裡，Spring AI 串流回覆就是實戰單元之一——從後端 Flux 串流到前端逐字渲染，整條路線帶你走一遍，做出你自己的企業級 AI 應用。

👉 [前往 Hahow 課程頁](https://hahow.in/cr/ai-full-stack)
<!--/promo-->

## 後段：把 SSE 寫到生產等級

### 1. 事件格式的完整樣貌

`data:` 只是最小用法。完整的事件有四個欄位：

```text
event: order-created
id: 42
retry: 5000
data: {"orderId": 1001, "amount": 300}
```

- `event`：事件名，前端用 `es.addEventListener('order-created', ...)` 分流
- `id`：事件序號，斷線重連時瀏覽器自動帶回 `Last-Event-ID`，伺服器據此**補發遺漏**
- `retry`：告訴瀏覽器重連間隔（毫秒）
- 多行 `data:` 會被合併成一則（以換行相接）

### 2. Spring 端：SseEmitter 與 Spring AI

Spring MVC 用 `SseEmitter`：

```java
@GetMapping(value = "/api/ai/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter chat(@RequestParam String q) {
  SseEmitter emitter = new SseEmitter(120_000L);   // 兩分鐘超時
  chatClient.prompt().user(q).stream().content()  // Spring AI 的串流輸出
      .subscribe(
          token -> send(emitter, token),           // 每個 token 推一段
          emitter::completeWithError,
          emitter::complete);
  return emitter;
}

private void send(SseEmitter emitter, String token) {
  try {
    emitter.send(SseEmitter.event().data(token));
  } catch (IOException e) {
    emitter.completeWithError(e);   // 客戶端斷線：結束串流，別讓例外悶著
  }
}
```

WebFlux 更精簡：直接回 `Flux<ServerSentEvent<String>>`，框架幫你打點格式。

### 3. 生產環境的三個坑

**反向代理緩衝**——最經典的坑：本機打字機效果順暢，上線後整段回答「憋到最後一次吐出來」。因為 Nginx 預設會**緩衝**上游回應。解法是對 SSE 路徑關閉緩衝：

```nginx
location /api/ai/ {
  proxy_pass http://backend;
  proxy_buffering off;          # 或由後端回 X-Accel-Buffering: no
  proxy_read_timeout 3600s;     # 別讓代理先把長連線掐掉
}
```

**閒置逾時**——中間設備（LB、代理、防火牆）常把「太久沒流量」的連線靜默掐掉。標準解是伺服器定期送**註解行心跳**（`:` 開頭的行，瀏覽器會忽略）：

```text
: keep-alive
```

每 15–30 秒一次，連線就不會被判定閒置。

**HTTP/1.1 的六連線上限**——瀏覽器對同網域的 HTTP/1.1 連線上限約 6 條，每個 SSE 佔一條；使用者開多個分頁就撞牆。解法很直接：**上 HTTP/2**，多路復用之後這個限制實質消失。你的站台如果已經全站 HTTPS＋HTTP/2（現在幾乎是預設），這題自動解掉。

### 4. SSE vs WebSocket 的快速判斷

問自己一句話：**「瀏覽器需要高頻地回話嗎？」**

- 不需要（偶爾發個 HTTP 請求就夠）→ SSE：更簡單、自帶重連、基礎設施友善
- 需要（每秒多次、雙向你來我往）→ WebSocket，下期見

## 本期帶走三句話

- SSE 就是一條不掛斷的 HTTP：單向、純文字、瀏覽器原生自動重連。
- AI 串流回覆的標準解是 SSE，不是 WebSocket——單向文字流用雙向協定是過度設計。
- 上線三查：代理緩衝關了沒、心跳送了沒、HTTP/2 開了沒。

下期是系列完結篇：WebSocket 的雙向世界、WebRTC 的點對點宇宙，加上 Web Push 與 WebTransport 這些新面孔，最後給你一張完整的選型決策表。

—— 凱文大叔

---

延伸閱讀：

- [MDN：Server-Sent Events](https://developer.mozilla.org/docs/Web/API/Server-sent_events/Using_server-sent_events)
- [WHATWG HTML 規格：Server-sent events](https://html.spec.whatwg.org/multipage/server-sent-events.html)
- [Spring AI：Chat Client 串流](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
```

---

## 寄送提醒

- **必須在 008 之後寄出**：開頭直接引用上期輪詢結論（「你去問」vs「它來說」）。
- **工商卡片未填優惠碼**：文案主打「打字機效果實戰單元」，與本期主題強關聯；優惠碼寄送前確認。
- 本期程式碼區塊較多（6 段，圍欄式含 js/java/nginx/text 語言標記）——**測試信務必檢查程式碼在 Gmail 手機版的呈現**。
- ChatGPT／Claude／Gemini 僅作為讀者熟悉的實例提及，未做功能比較；「絕大多數就是 SSE」的措辭保留餘地（部分實作走 fetch streaming），**請勿改寫成絕對敘述**。
- 本期**不含** `<!--paywall-->`，全文免費。
- 下期預告已寫死「選型決策表」，010 內容需兌現此承諾。
