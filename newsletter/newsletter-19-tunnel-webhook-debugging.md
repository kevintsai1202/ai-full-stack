# 電子報第 019 期範本：本機收真實 Webhook——告別 Mock，斷點攔截外部回調（TUNNEL 系列 T2）

> **用途**：單一主題——**TUNNEL 系列 T2**（本地 Webhook 實戰除錯篇）：
> 解決全端開發者串接金流、通訊軟體與外部 AI 時最痛苦的痛點——「收不到 Webhook 或只能瞎子摸象看 Log」。利用固定的 Tunnel 通道，讓本地 IDE 成為真實 Webhook 終端點，直接在 Java / Node 中設中斷點除錯，並解決串流重讀（ContentCaching）與簽章驗證（HMAC）經典地雷。
> **使用方式**：將下方「內文（Markdown）」複製到 admin 後台，先更新預覽並寄測試信，確認後再正式寄送。
>
> **「TUNNEL」系列鋪陳（017 起；回扣 010 WebRTC 與 011 Webhook）**：
>
> - 017 T0 原理篇：從 WebRTC 打洞到 Tunnel 反向通道——為什麼防火牆只擋入站？
> - 018 T1 實戰篇：Cloudflare Tunnel 實戰——一條指令把 Spring Boot + React 送上公網
> - 019（本期）T2 除錯篇：本機收真實 Webhook——告別 Mock，斷點攔截外部回調
> - 020 T3 防護篇：Zero Trust 零信任防護——不寫一行 code 幫 AI 系統加上 Google / OTP 驗證
>
> **前置條件**：018 結尾已預告本期（「本機收真實 Webhook」）——**必須在 018 之後寄出**。
> 本期同時回收 011 期的 Webhook 接收端原理、014/015 交易冪等與 018 的 Tunnel 路由。
> **素材來源**：兩張 16:9 示意圖為 `newsletter/assets/019-a-webhook-breakpoint-flow.svg`、`019-b-request-wrapper-hmac.svg`（已繪製並轉為 PNG），寄送前需上傳媒體庫並替換文中 TODO 圖片 URL。
>
> 文章中段以「工商時間」卡片（`<!--promo-->` 標記）宣傳 Hahow 課程，
> **優惠碼與期限需在寄送前確認後填入**。

---

## 建議主旨（三選一）

1. `別再用 System.out.println 盲猜 Webhook——直接在本地 IDE 設斷點`
2. `為什麼你的 Webhook 簽章總是校驗失敗？Request Body 只能讀一次的經典坑`
3. `TUNNEL 系列（二）：用真實外部回調，測出系統最脆弱的那一行程式碼`

**推薦**：`別再用 System.out.println 盲猜 Webhook——直接在本地 IDE 設斷點`

## 預覽文字（preheader）

> 固定網域打通本地隧道，讓 Stripe、LINE 與 GitHub 的真實事件直接打進你的 IntelliJ / VSCode。搞懂 Body Stream 重讀與簽章驗算。

---

## 內文（Markdown，直接複製貼上）

```markdown
<!--promo-->
### 工商時間

這系列講的是「在本地攔截真實外部世界」——如果想把 Webhook、事件驅動與 AI Agent 從頭串成企業級產品：

**《AI 賦能全端開發：從零打造企業級智慧應用》**：從 Spring Boot 後端、React 前端到資料庫與權限，一路加上 Spring AI、RAG 與 AI Agent，用一個專案走完整條路。

🎁 **課程最後額外加碼「Cloudflare Tunnel 穿透實戰」**：教你如何在免固定 IP、不開路由器 Port 的安全前提下，把公司內部或本機架設好的全套系統安全穿透到公網，讓外部客戶、主管或團隊直接連線體驗！

👉 [前往 Hahow 課程頁](https://hahow.in/cr/ai-full-stack)
🎉 **募資進度已突破1000%，感謝大家的支持！**🎉

<!--/promo-->

# 本機收真實 Webhook：告別 Mock，斷點攔截外部回調

如果你寫過 Webhook（例如 Stripe 刷卡通知、LINE Bot 訊息、GitHub Actions 狀態回傳，或是外部非同步 AI 產圖的回調），你一定經歷過這段痛苦循環：

1. **Mock 假資料**：自己用 Postman 造假 Payload，測起來全部綠燈。
2. **上線部署**：滿懷信心把程式推上測試機。
3. **正式打擊**：外部平台一觸發，後端直接噴 `400 Bad Request` 或 `Signature Verification Failed`。
4. **瞎子摸象**：在程式裡到處狂加 `log.info("payload: " + body)`，反覆部署十幾次，只為了看第三方到底傳了什麼 Header。

在第 011 期我們講過 Webhook 的接收端深水區。現在有了上期的 **Cloudflare Tunnel**，我們有了固定的公開網域 `https://crm.yourdomain.com`。

這意味著：**你可以把第三方後台的 Webhook URL 直接填上你的 Tunnel 網址，讓全世界的真實事件直接打進你本地的 IDE！**

## 在本地斷點面前，沒有任何黑盒子

當 Stripe 刷卡成功，幾毫秒內，你的 IntelliJ IDEA 或 VSCode 就在 Controller 的第一行**穩穩停住了**。

你可以：
* 展開查看最真實、未被簡化過的複雜 JSON 結構與嵌套物件。
* 一步一步（Step Over）觀察 Jackson 反序列化的每一個欄位轉換。
* 檢查 Headers 裡的所有 Metadata（如 `Stripe-Signature`、`X-Hub-Signature-256`、`X-Line-Signature`）。

![真實 Webhook 本地斷點除錯流程與秒級解耦架構：外部 5 秒超時限制下，本地斷點攔截、HMAC 驗簽、50ms 秒回 200 OK，業務邏輯交由 Spring 事件總線異步執行](https://springai-media.zeabur.app/newsletter-media/images/TODO-019-webhook-breakpoint-flow.png)

但當你第一次在本地攔截真實 Webhook 時，幾乎 100% 的開發者都會踩進這兩個致命大坑：

<!--paywall-->

## 深入底層：Webhook 接收端的兩大核心機制

在動手寫 Controller 之前，我們必須先搞清楚「為什麼 Webhook 比一般 REST API 容易踩雷」的兩個底層密碼學與 I/O 原理：

### 1. Socket 串流的「不可倒帶性（Non-rewindable）」
HTTP 請求本質上是作業系統從 TCP Socket 緩衝區接收的一串資料流（Stream）。在 Servlet 規範中，這對應到 `ServletInputStream`。
* **指針只能往前走**：串流內部有一個讀取游標（Cursor），資料被讀取一個 byte，游標就往前一步，直到 EOF（檔案結尾）。
* **Spring 的預處理陷阱**：當你在方法參數宣告 `@RequestBody StripeEventDto dto` 時，Spring 的 `HttpMessageConverter`（底層由 Jackson 驅動）會在進入你寫的 Controller 第一行程式前，**就主動把整個 InputStream 從頭讀到尾轉成 Java 物件**。這意味著游標已經停在 EOF，後續程式碼若再嘗試呼叫 `request.getInputStream()`，只會讀到空值。

### 2. HMAC 簽章與「時序攻擊（Timing Attack）」防禦
第三方金流（如 Stripe）不是在 Payload 裡夾帶明文密碼，而是使用 **HMAC-SHA256**：

```text
第三方伺服器：HMAC_SHA256(原始 Body 字節, Webhook Secret) ──▶ 產生簽章（放入 Header）
                                                                 │
                                                          （網路傳輸）
                                                                 ▼
你的本機後端：HMAC_SHA256(取得的 Body 字節, 本機 Secret)  ──▶ 計算出本地簽章 ──▶ 比對兩者！
```

* **Body 一字不差**：只要你的 JSON 反序列化格式化改變了一顆空格或換行，算出來的雜湊就會天差地別。
* **比對不能用 `String.equals()`**：標準的 Java 字串比對是「一旦比對到第一個不相符的字元就提早返回（Early Exit）」。攻擊者只要測量微秒級的回應時間差異，就能暴力破解出有效簽章！因此比對簽章必須使用**常數時間比對演算法**（如 `MessageDigest.isEqual()`），不管在哪個位置出錯，都會耗費完全相同的時間比對完所有字元。

![InputStream 串流不可逆性 vs HMAC 常數時間驗簽：底層 InputStream 枯竭反例與 Raw Bytes 提取 ＋ MessageDigest.isEqual 常數時間防時序攻擊解法](https://springai-media.zeabur.app/newsletter-media/images/TODO-019-request-wrapper-hmac.png)

## 痛點一：Request Body 只能讀一次的「串流枯竭」陷阱

為了防竄改，所有具備資安意識的 Webhook 都會要求**驗證 HMAC 簽章**：第三方用 Secret Key 將「原始請求 Body」雜湊出簽章，放在 Header 送過來；你的後端必須拿「一模一樣的原始 Body 字節」重算一次比對。

在 Spring Boot 裡，常有人這樣寫：

```java
// ❌ 經典錯誤：Spring 已經把 InputStream 讀光了
@PostMapping("/api/webhook/stripe")
public ResponseEntity<String> handleWebhook(
        @RequestHeader("Stripe-Signature") String sigHeader,
        @RequestBody StripeEventDto dto) { // Spring 在這裡已經把 Request Stream 讀完轉成 DTO
    
    // 這裡你拿不到原始的 Raw Body bytes，若自己從 request.getInputStream() 讀，會直接讀到空值！
    boolean isValid = verifySignature(sigHeader, ...); 
    ...
}
```

**原因**：HTTP Request Body 是底層的 `ServletInputStream`，它是一條**單向串流（Stream）**，讀過一次就乾了，無法重複讀取（Cannot re-read）。

### 正確解法：`ContentCachingRequestWrapper`

利用 Spring 提供的 Wrapper 或 Filter 把 Request 包裝起來，快取一份 Byte Array：

```java
@PostMapping("/api/webhook/stripe")
public ResponseEntity<String> handleWebhook(
        @RequestHeader("Stripe-Signature") String sigHeader,
        HttpServletRequest request) throws IOException {

    // 1. 取得原始 Raw Bytes（完全不依賴已解析的 DTO）
    byte[] rawBody = request.getInputStream().readAllBytes();

    // 2. 驗證簽章（使用原始字節與 Webhook Secret 運算）
    if (!verifyHmacSha256(rawBody, sigHeader, webhookSecret)) {
        log.warn("Webhook 簽章驗證失敗，拒絕請求！");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
    }

    // 3. 驗證通過後，再手動用 ObjectMapper 反序列化
    StripeEventDto event = objectMapper.readValue(rawBody, StripeEventDto.class);

    // 4. 丟進事件總線解耦處理（回扣 012-017 的 Event 體系）
    eventPublisher.publishEvent(new PaymentCompletedEvent(event.getId(), event.getAmount()));

    // 5. 立刻回傳 200 OK，絕對不要讓外部等待業務邏輯！
    return ResponseEntity.ok("success");
}
```

## 痛點二：本地斷點停太久，引發第三方的「重試風暴」

當你在 IDE 停在斷點上仔細觀察變數時，時間過了 10 秒鐘。

外部平台（如 Stripe 或 LINE）預設有很短的超時限制（通常為 **5 秒**）。當它 5 秒內沒收到 `200 OK`，它會判定「你的伺服器掛了」，並立刻依照指數退避發起**重試（Retry）**！

結果：你在本機剛按下一步，IDE 馬上又跳出第二個、第三個斷點連環觸發。

### 解決心法（串聯 014 / 015 系列精髓）：

1. **接收端秒回 200 OK**：如上方範例，驗簽通過後立刻發送 Spring Event 解耦，50ms 內回傳 `200 OK`，不要在 Webhook Thread 裡面執行耗時的 AI 或 DB 重度操作。
2. **配合 015 冪等鍵（Idempotency Key）**：第三方帶有 `event.id`。即便本地斷點超時引發重複重送，你的後端只要依賴 015 講的 `processed_events` 表，重複的事件就會被天然安全過濾。

---

🤖 想快速為你的專案產生對應第三方平台（Stripe、LINE、GitHub、Paddle 等）的安全驗簽 Controller，可直接使用此提示詞：

```text
我正在使用 Spring Boot 3 / 4 開發 Webhook 接收端，需要接入 [平台名稱，例如 Stripe / LINE Messaging API]：
要求：
1. 撰寫完整的 Controller 範例，安全處理原始 Request Body 與 Header 簽章比對（HMAC-SHA256）
2. 避免 InputStream 串流枯竭問題
3. 採用非同步事件解耦模式：驗簽後立即回傳 200 OK，並發布內部 ApplicationEvent
4. 包含錯誤處理與防禦常數時間比對（MessageDigest.isEqual 防止時序攻擊）
```

## 系列小結與下期預告

這期我們解鎖了全端開發最強大的除錯能力：**讓本地環境直接接收全球真實 Webhook，並在 IDE 內從容設斷點與防禦簽章地雷。**

現在你的本地系統：
1. 能透過 Tunnel 讓外部公開存取（018 T1）。
2. 能安全接收全世界的 Webhook 回調（019 T2）。

但最後一個終極問題來了——**你的系統現在完全暴露在公網上。**

如果路人、爬蟲或競爭對手抓到了你的網址，狂打你的 `/api/ai/generate` 端點，你的 OpenAI / Claude Token 帳單會在半小時內被刷爆！

**我們該如何在「不改動一列後端程式碼、不自建複雜會員系統」的前提下，為整個本地系統加上企業級 Google 登入與 Email OTP 白名單防護？**

下期（020 期）TUNNEL 系列完結篇：**《Zero Trust 零信任防護：不寫一行 code 為 AI 系統加上 Google / OTP 驗證》**，我們來給系統裝上最堅固的防盜門！

—— 凱文大叔

---

延伸閱讀：

- [Stripe Webhook Signature Verification 官方規範](https://docs.stripe.com/webhooks/signatures)
- [Spring Framework：ContentCachingRequestWrapper 深度解析](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/util/ContentCachingRequestWrapper.html)
- [防止 Timing Attack：MessageDigest.isEqual 原理](https://codahale.com/a-lesson-in-timing-attacks/)
```

---

## 寄送提醒

- **必須在 018 之後寄出**：018 結尾已預告本期（「本機收真實 Webhook」）。
- **付費分段**：`<!--paywall-->` 切在「深入底層：Webhook 接收端的兩大核心機制」之前。
- **示意圖就緒**：包含 2 張 16:9 繁體中文資訊圖（`019-a-webhook-breakpoint-flow.png`、`019-b-request-wrapper-hmac.png`，位於 `newsletter/assets/png/`）。寄送前上傳媒體庫並替換文中 TODO URL。
- **工商卡片**：文章前半段包含 Hahow 課程與 Cloudflare Tunnel 加碼單元宣傳。
- **下期預告**：結尾預告 020 期（T3 Zero Trust 零信任防護・系列完結）。
