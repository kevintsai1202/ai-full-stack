# 電子報第 010 期範本：WebSocket、WebRTC 與下一代——雙向、點對點、選型總表

> **用途**：即時通訊三期系列的完結篇。WebSocket（雙向）、WebRTC（點對點）、
> Web Push 與 WebTransport（新世代），收尾附全系列選型決策表與「AI 寫即時功能的常見錯配」提醒。
> **使用方式**：將下方「內文（Markdown）」複製到 admin 後台，先更新預覽並寄測試信，確認後再正式寄送。
>
> **本期為三期系列的第三期（完結篇）**：
>
> - 008 地圖＋輪詢：即時通訊的本質問題與輪詢家族
> - 009 SSE：單向串流，AI 打字機效果的主角
> - 010（本期）WebSocket／WebRTC／新世代：雙向、點對點與選型總表
>
> **前置條件**：開頭引用前兩期，且 009 結尾已預告本期的「選型決策表」——**必須在 008、009 之後寄出**。
>
> 文章中段以「工商時間」卡片（`<!--promo-->` 標記）宣傳 Hahow 課程，
> **優惠碼與期限需在寄送前確認後填入**。

---

## 建議主旨（三選一）

1. `聊天室用 WebSocket、視訊用 WebRTC——然後呢？完整選型表在這`
2. `WebSocket 之後還有新協定？WebTransport 與 Web Push 一次看懂`
3. `即時通訊完結篇：一張表決定你該用哪個協定`

**推薦**：`聊天室用 WebSocket、視訊用 WebRTC——然後呢？完整選型表在這`

## 預覽文字（preheader）

> 雙向找 WebSocket、點對點找 WebRTC、背景通知找 Web Push——系列完結篇，附一張可以直接抄的決策表。

---

## 內文（Markdown，直接複製貼上）

```markdown
# WebSocket、WebRTC 與下一代——雙向、點對點、選型總表

嗨，我是凱文大叔。

前兩期把「你去問」（輪詢）和「它來說」（SSE）講完了。這期進入即時通訊的重武器區：**雙向**的 WebSocket、**點對點**的 WebRTC，以及幾個你該認識的新面孔——最後兌現承諾，給你一張可以直接抄的選型決策表。

## WebSocket：真正的雙向對講機

WebSocket 從一個普通的 HTTP 請求出發，透過 `Upgrade: websocket` 握手，把這條連線「升級」成**全雙工通道**：之後雙方隨時都能開口，訊息可以是文字或二進位，沒有請求—回應的回合限制。

它的主場是**雙向、高頻、低延遲**：

- 聊天室、即時客服——訊息你來我往
- 多人協作——白板、共編文件，每個人的操作要即時廣播給所有人
- 網頁遊戲——狀態同步每秒好幾次
- 交易下單——按下去的那一刻就要送達

一句話對比上期：SSE 是廣播電台，WebSocket 是對講機。**只聽廣播的場景別買對講機**——你會多付「連線管理」這筆隱形成本，下面後段會講它有多真實。

## WebRTC：根本不走你的伺服器

WebRTC 解的是另一個維度的問題：**瀏覽器跟瀏覽器直接連**，音訊、視訊、任意資料（DataChannel）點對點傳輸，內建加密與網路自適應。

- 視訊通話、線上會議、螢幕分享
- P2P 檔案傳輸（檔案不經過伺服器）
- 即時性極端敏感的資料通道（雲端遊戲的操作流）

但「點對點」不代表「不需要伺服器」——這是 WebRTC 最大的認知陷阱。你仍然需要：

- **信令伺服器**：兩個瀏覽器交換「怎麼連上我」的資訊（通常拿 WebSocket 來做）
- **STUN**：幫雙方發現自己在 NAT 後面的公網位址
- **TURN**：雙方都穿不透 NAT 時的中繼備援——**這是要花頻寬錢的**，做視訊產品時 TURN 流量是實打實的成本項

所以 WebRTC 的定位很清楚：**媒體流與 P2P 才用它**。拿它做聊天室是用火箭筒打蚊子。

## 新世代與家族其他成員

- **Web Push**：唯一能在**使用者沒開你網站時**送通知的技術（配合 Service Worker，走瀏覽器廠商的推播服務）。「離線也要通知」找它，不是 WebSocket。
- **WebTransport**：跑在 HTTP/3（QUIC）上的新協定，同時支援可靠與**不可靠**傳輸——遊戲、串流這種「舊資料晚到不如丟掉」的場景等它很久了，瀏覽器支援已逐漸到位，是 WebSocket 未來最有力的挑戰者。
- **HTTP/2 Server Push**：曾被寄予厚望，結果沒人用得好，Chrome 已經**移除支援**——留名警世：不是掛著「Push」就適合做即時通訊。
- **Socket.IO／STOMP／SignalR／MQTT over WebSocket**：注意，這些是**程式庫或訊息協定**，不是傳輸層——底下跑的還是 WebSocket（含降級路徑）。它們解決的是重連、房間、訊息格式這些「WebSocket 沒管的事」。

<!--promo-->
### 工商時間

這系列講的是「選對技術」，選完之後的「做出整套系統」在這裡——

**《AI 賦能全端開發：從零打造企業級智慧應用》**：用同一套 AI CRM 專案，從 Spring Boot 後端、React 前端到資料庫與權限，一路加上 Spring AI、RAG 與 AI Agent，完成真正能上線的企業級應用。

👉 [前往 Hahow 課程頁](https://hahow.in/cr/ai-full-stack)
<!--/promo-->

## 後段：WebSocket 的生產等級清單

WebSocket 上線後你才會發現，握手成功只是開始。

### 1. 心跳與重連——自己來

不像 SSE，WebSocket 的斷線偵測和重連**規格不管**。生產等級的前端至少長這樣：

    let ws, delay = 1000;
    function connect() {
      ws = new WebSocket('wss://example.com/ws');
      ws.onopen = () => { delay = 1000; };            // 連上就重置退避
      ws.onmessage = (e) => handle(JSON.parse(e.data));
      ws.onclose = () => {                            // 斷線：指數退避＋抖動重連
        setTimeout(connect, delay + Math.random() * 500);
        delay = Math.min(delay * 2, 30000);
      };
    }
    connect();

伺服器端則要定期 ping、逾時未 pong 就主動斷開——否則殭屍連線會慢慢吃光資源。

### 2. 認證的小陷阱

瀏覽器的 `new WebSocket()` **不能自訂 Authorization 標頭**。實務解法：握手時走 cookie；或把一次性 token 放在查詢字串／第一則訊息內驗證。別在 URL 放長效 token——它會進存取日誌。

### 3. 多實例廣播：單機聊天室的畢業考

服務一水平擴展，「廣播給所有人」就破功了——A 實例收到的訊息，掛在 B 實例上的使用者收不到。標準解是掛一個 pub/sub（最常見 Redis）：

    // 收到訊息：不直接廣播，先發布到 Redis
    redis.convertAndSend("chat", message);

    // 每個實例都訂閱同頻道，收到後推給「自己身上」的連線
    @Override
    public void onMessage(String message) {
      sessions.forEach(s -> send(s, message));
    }

Spring 端的最小骨架（原生 `TextWebSocketHandler`，不套 STOMP）：

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
      sessions.add(session);       // 上線登記
    }
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage msg) {
      redis.convertAndSend("chat", msg.getPayload());  // 進 pub/sub，不直發
    }

### 4. WebRTC 最小心智模型：三次握手之外的三步

    // 1. 造一個 PeerConnection（帶 STUN）
    const pc = new RTCPeerConnection({ iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] });
    // 2. 我方開價（offer）→ 經信令伺服器交給對方 → 對方回價（answer）
    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);
    signaling.send({ type: 'offer', sdp: offer });
    // 3. 雙方持續交換 ICE 候選位址，直到找到能通的路
    pc.onicecandidate = (e) => e.candidate && signaling.send({ type: 'ice', candidate: e.candidate });

記住結構就好：**offer/answer 換能力、ICE 換路徑、STUN/TURN 幫穿牆**——細節查文件，結構不對 AI 也救不了你。

## 完結篇：選型決策表

| 你的場景 | 用什麼 | 一句理由 |
| --- | --- | --- |
| 低頻更新、儀表板、批次進度 | 短輪詢（＋ETag） | 最簡單，serverless 也能用 |
| 準即時通知、相容性至上 | 長輪詢 | 持久連線的降級備案 |
| AI 串流、通知、動態牆、單向看板 | SSE | 自帶重連，基礎設施友善 |
| 聊天、協作、遊戲、雙向高頻 | WebSocket | 全雙工，但連線管理自己扛 |
| 視訊、語音、P2P 傳檔 | WebRTC | 點對點；記得信令＋TURN 成本 |
| 使用者離線也要通知 | Web Push | 唯一能背景送達的選項 |
| 需要「不可靠傳輸」的低延遲流 | WebTransport | HTTP/3 世代，開始關注 |

最後回到系列開頭那句話：現在很多即時功能是 AI 寫的。**AI 最常見的兩個錯配**：拿 WebSocket 做單向通知（其實 SSE 就好，還自帶重連）、拿短輪詢做聊天室（上線就被流量教訓）。技術地圖在你腦裡，AI 才是你的工具——反過來就危險了。

三期完結。下次再有人問「即時功能要用什麼」，把這張表丟給他。

—— 凱文大叔

---

延伸閱讀：

- [MDN：WebSocket API](https://developer.mozilla.org/docs/Web/API/WebSockets_API)
- [MDN：WebRTC API](https://developer.mozilla.org/docs/Web/API/WebRTC_API)
- [MDN：Push API](https://developer.mozilla.org/docs/Web/API/Push_API)
- [WebTransport 概觀（web.dev）](https://web.dev/webtransport/)
```

---

## 寄送提醒

- **必須在 008、009 之後寄出**：開頭引用前兩期，且 009 結尾已承諾本期的「選型決策表」。
- **工商卡片未填優惠碼**：完結篇文案回到課程整體定位；優惠碼寄送前確認。若三期都保留工商卡，考慮只在其中一〜兩期投放以免疲乏（工商提案系統的投放次數配額可直接支援此策略）。
- 本期表格一張（選型決策表）＋程式碼區塊 5 段。**測試信重點確認選型表在手機信箱的換行**——表格是 Email 相容性最差的元素，破版就把表改成條列。
- 「Chrome 已移除 HTTP/2 Server Push 支援」為事實陳述（Chrome 106 起）；WebTransport 瀏覽器支援描述用「逐漸到位」保留語氣，**請勿改寫成絕對敘述**。
- STUN 範例用 Google 公開伺服器位址，僅為教學示意；正式產品應自建或採用商用 TURN 服務，文中已點出成本考量。
- 本期**不含** `<!--paywall-->`，全文免費——完結篇以分享轉發優先；系列三期寄畢後，可考慮在讀者頁把三期整理成一個系列合集入口。
