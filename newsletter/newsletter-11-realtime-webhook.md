# 電子報第 011 期範本：新系列開場＋Webhook 完結篇（本期起一期兩主題）

> **用途**：本期起電子報改為**一期兩主題**。
> 主題一＝「可審查產物」新系列開場（基礎篇，免費導流）：改編自直播《別再問 AI「能不能做」》的開場段——
> 反差場景、核心主張、agentic coding 迴圈、錯誤成本，與四站路線圖（012–015 依序鋪陳）。
> 主題二＝即時通訊系列第四期（完結篇，專業深度內容）：深入 Webhook——反向 API 的觀念、平台整合場景，
> 以及接收端的四大生產課題（簽章驗證、冪等去重、快速回應與順序防護）。
> **付費牆設在主題二的「後段：接收端的四道關卡」之前**——免費區＝新系列開場＋Webhook 觀念與場景，
> 付費區＝四道關卡實作與事件驅動觀念升級。
> **使用方式**：將下方「內文（Markdown）」複製到 admin 後台，先更新預覽並寄測試信，確認後再正式寄送。
>
> **本期為四期系列的第四期（完結篇）**：
>
> - 008 地圖＋輪詢：即時通訊的本質問題與輪詢家族
> - 009 SSE：單向串流，AI 打字機效果的主角
> - 010 WebSocket／WebRTC／新世代：雙向、點對點與選型總表
> - 011（本期）Webhook：伺服器之間的即時通訊與接收端深水區
>
> **前置條件**：010 的「常被誤會的親戚」小節已預告本期——**必須在 010 之後寄出**。
>
> 文章中段以「工商時間」卡片（`<!--promo-->` 標記）宣傳 Hahow 課程，
> **優惠碼與期限需在寄送前確認後填入**。

---

## 建議主旨（三選一）

1. `同一個需求、同一個模型，兩個人問出兩種答案——差別只在問法`
2. `新系列開跑＋Webhook 完結篇：金流回調掉單的坑都在收的那一端`
3. `別再問 AI「能不能做」；還有，Webhook 不是設個 URL 就完事`

**推薦**：`同一個需求、同一個模型，兩個人問出兩種答案——差別只在問法`（主打免費區的新系列鉤子，付費深度內容由內文轉化）

## 預覽文字（preheader）

> 新系列開場：決定 AI 交付品質的是問法，不是提示詞長度。加映即時通訊完結篇——收 webhook 的簽章、冪等、順序一次講清楚。

---

## 內文（Markdown，直接複製貼上）

```markdown
# 主題一｜決定 AI 交付品質的，不是提示詞，是你怎麼問

嗨，我是凱文大叔。

這期增加一個較軟性的主題。第一個主題**你怎麼問**。第二個主題把即時通訊系列收尾。

## 先講一個場景

同一個需求、同一天、同一個模型，兩個人去問 AI。

A 問：「這個功能能不能做？」他得到一句話——「可以做，建議用 React」。

B 問：「幫我做這個需求的可行性評估報告。」他得到一份文件，裡面有難度分級、有風險清單、有建議的驗證順序。

同一天、同一個模型、同一個需求。差別不在模型，在問法。

所以這個系列的核心主張只有一句話：

> **決定 AI 交付品質的，不是提示詞寫得漂不漂亮，是你有沒有要求它交出一份可以被審查的產物。**

「能不能做」換來的是一句話——你沒得反駁、沒得追蹤、也沒得改。「可行性評估報告」換來的是一份文件——可以拿去開會、可以被主管打槍、可以在兩週後回頭對照當初評估對不對。

## 基礎觀念一：AI 是代理，不是自動販賣機

現在 AI 開發的標準做法有個名字，叫 **agentic coding**。AI 不是你丟一句話、它吐一段程式碼就結束的自動販賣機；它是一個**代理**（agent）：理解需求 → 規劃 → 生成 → 驗證 → 修正——自己會把這個迴圈跑完。

![Agentic Coding 五步迴圈：理解需求（把模糊想法變成規格）→ 規劃（拆解步驟、訂驗收標準）→ 生成（產出程式與文件）→ 驗證（測試、實跑、稽核）→ 修正（依據證據迭代到交付）](https://springai-media.zeabur.app/newsletter-media/images/071fed4d242d0b17a5a451a661e5c301730732c8745614fb1db786a3860bf925.png)

人的角色也跟著變了：從「逐行下指令」變成**審查每一站的產物**。這句話是整個系列的地基——你要審查，就得先有「可以被審查的東西」。

## 基礎觀念二：問法決定你除錯幾次

模糊的提問，AI 會自行腦補規格。腦補出來的東西跟你心裡想的有偏差，你就開始除錯——改一次、跑一次、再改一次。每一次來回，都是重跑一整圈「生成、驗證、修正」。

精準的提問，把規格在**生成之前**就對齊了。錯誤根本沒機會被寫進程式碼裡。

除錯次數不是運氣，是提問品質的函數。錯誤在需求期被發現，改一句話；寫完程式才發現，改一天；上線後才發現，再放大十倍。**正確的提示詞，就是把錯誤攔在最便宜的階段。**

## 接下來的路線圖

這個系列會沿著開發生命週期走四站，每站各有一句大家最常講、但最不該講的話：

1. **想法期**：「這個能不能做？」→ 可行性評估報告
2. **設計期**：「幫我用 React 寫一個 X」→ 選型分析＋開發計畫
3. **開發驗收期**：「幫我測試有沒有問題」→ TDD＋E2E＋自動生成 SOP
4. **上線前**：「幫我看看有沒有漏洞」→ OWASP／CWE 逐項稽核

![四站地圖：想法期（能不能做→可行性評估）→ 設計期（幫我寫→選型＋計畫）→ 開發驗收（幫我測試→TDD＋E2E＋SOP）→ 上線前（查漏洞→逐項稽核）](https://springai-media.zeabur.app/newsletter-media/images/7910dfc40962ff6ae8cf717a81df73b365681af7788d05a93fa2b70e02c6302e.png)

下期走進第一站：想法期——為什麼「能不能做」幾乎永遠只會換來「可以」？那不是 AI 評估過，是它想讓你滿意。

<!--promo-->
### 工商時間

金流串接、webhook 入帳、狀態機守護——這些「整合的髒活」正是企業級應用的日常。

**《AI 賦能全端開發：從零打造企業級智慧應用》**：用同一套 AI CRM 專案，從 Spring Boot 後端、React 前端到資料庫與權限，一路加上 Spring AI、RAG 與 AI Agent，完成真正能上線的企業級應用。

👉 [前往 Hahow 課程頁](https://hahow.in/cr/ai-full-stack)
<!--/promo-->

---

# 主題二｜Webhook——伺服器之間的即時通訊，與它的深水區

上期結尾提到那位「常被誤會的親戚」：Webhook。它不在瀏覽器即時技術的選型清單裡（瀏覽器接不到它），但在**系統整合**的世界，它是「它來說」哲學的伺服器版——而且幾乎每個接過金流、串過第三方服務的人，都在它身上摔過跤。

這期是系列完結篇：把 Webhook 講深，尤其是**摔跤最多的接收端**。

## Webhook 是什麼：一支反過來打的 API

一般的 API 是**你去問**：你的程式呼叫金流服務「這筆付款成功了嗎？」。Webhook 把方向反過來，變成**它來說**：你先在對方後台登記一個 URL，事件發生的當下，對方主動對這個 URL 發 HTTP POST，把事件內容送上門。

所以 Webhook 也常被叫做「反向 API」（Reverse API）或 HTTP 回呼。你不用輪詢對方的查詢介面，事件自己找上門——熟悉嗎？這就是 008 講過的「輪詢 vs 推送」，只是舞台從瀏覽器搬到了伺服器之間。

## 到處都是它：你可能已經在用了

- **金流**：付款成功／失敗／退款，金流服務用 webhook 通知你的後端入帳
- **GitHub／GitLab**：push、PR、issue 事件觸發你的 CI 或機器人
- **通訊平台**：LINE、Telegram、Slack 的 bot 收訊息，本質都是 webhook
- **Email 服務**：寄送成功、開信、退信事件回報（這份電子報系統的寄送狀態就是這樣收的）
- **自動化平台**：n8n、Zapier、IFTTT 的「觸發器」，一大半是 webhook 包裝

判斷句很簡單：**「外部服務發生事件時，要通知『你的後端』」→ Webhook**。要再通知到使用者的瀏覽器，就接上這系列前三期的技術（webhook → 後端 → SSE/WS → 畫面）。

![Webhook 互補鏈：外部服務以 webhook 通知你的後端，後端再以 SSE 或 WebSocket 接力推到使用者的瀏覽器](https://springai-media.zeabur.app/newsletter-media/images/6522f68ce284c6ba0f6dab02253fcea45f2152d2a6c7953d1f4663c58f20e409.png)

## 為什麼說坑都在接收端

提供方（金流、GitHub）的工作很單純：事件發生、發 HTTP 請求、收不到 2xx 就重試。接收方卻要面對四個現實：

1. **請求可能是假的**——任何人知道你的 URL 都能對它 POST
2. **同一事件可能送好幾次**——對方採「至少一次」送達，逾時就重送
3. **你處理太慢，對方會判定失敗**——然後又重送，雪上加霜
4. **事件可能亂序到達**——「付款成功」比「處理中」先到

這四題就是後段的全部內容。

![接收端四道關卡管線：驗章、去重、立刻回 200、狀態機——本質是在 HTTP 上手工補回訊息佇列原生提供的保證](https://springai-media.zeabur.app/newsletter-media/images/caa59eec40e71786a8d717078bf7eb5fd2b213b5aaf2dabbce6ba703325e7ef7.png)

<!--paywall-->
## 後段：接收端的四道關卡

照系列慣例，每道關卡附一句**可以直接丟給 AI 的提示詞**——看懂關卡在防什麼，實作交給它。

### 關卡一：簽章驗證——先確認是本人

主流做法是 HMAC：雙方共享一把密鑰，提供方用它對請求內容算出簽章放在標頭（如 GitHub 的 `X-Hub-Signature-256`），你收到後用同一把鑰匙重算、比對：

```java
/** 驗證 webhook 簽章：用共享密鑰對原始 body 重算 HMAC-SHA256 再比對 */
public boolean verifySignature(byte[] rawBody, String signatureHeader) {
  try {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256"));
    String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(rawBody));
    // 用常數時間比較，避免 timing attack；絕不要用 equals()
    return MessageDigest.isEqual(expected.getBytes(UTF_8), signatureHeader.getBytes(UTF_8));
  } catch (GeneralSecurityException e) {
    return false;
  }
}
```

兩個細節最常被弄錯：**要對「原始 bytes」算簽章**（先被框架反序列化再序列化回來，順序或空白一變就對不上）；**比對要用常數時間函式**（`MessageDigest.isEqual`），不然比對耗時的差異會洩漏資訊。另外，把對方帶的**時間戳**一起驗（超過 5 分鐘就拒收），能擋住重放攻擊。

> 🤖 丟給 AI：「幫我的 webhook 接收端加上 HMAC 簽章驗證。」

### 關卡二：冪等去重——同一事件只入帳一次

提供方保證「至少一次」，代表你**一定會收到重複事件**。標準解是拿事件的唯一 ID 做去重，而且要用**資料庫唯一約束**兜底，不能只靠「先查再寫」：

```java
@Transactional
public void handle(WebhookEvent event) {
  try {
    // processed_event.event_id 有 UNIQUE 約束——這行是防線本體
    processedEventRepository.save(new ProcessedEvent(event.id()));
  } catch (DataIntegrityViolationException e) {
    return;   // 撞唯一鍵＝已處理過，安靜返回 200 即可
  }
  orderService.markPaid(event.orderId());   // 真正的業務處理
}
```

為什麼不能只「先查再寫」？兩個重送請求同時進來，都查到「沒處理過」，就雙雙入帳——唯一約束是資料庫層的最後防線。（008 講輪詢退避時提過 thundering herd，這裡是它的兄弟題。）

> 🤖 丟給 AI：「幫我的 webhook 用資料庫唯一約束做事件去重。」

### 關卡三：快速回 2xx——先簽收，再拆貨

對方通常給你幾秒鐘，逾時就當失敗重送。所以 webhook handler 的鐵律是：**驗章、去重、落地，然後立刻回 200**；耗時的業務（寄信、產報表、呼叫其他服務）丟給佇列或非同步任務：

```java
@PostMapping("/webhook/payment")
public ResponseEntity<Void> receive(@RequestBody byte[] rawBody,
                                    @RequestHeader("X-Signature") String sig) {
  if (!verifier.verifySignature(rawBody, sig)) {
    return ResponseEntity.status(401).build();   // 驗章失敗才拒收
  }
  webhookQueue.enqueue(rawBody);                 // 先落地
  return ResponseEntity.ok().build();            // 立刻簽收，重活稍後做
}
```

反例就是把整段業務同步塞在 handler 裡：處理 8 秒、對方 5 秒逾時判失敗、重送、你又處理 8 秒……自己把自己打成重送風暴。

> 🤖 丟給 AI：「幫我把 webhook 的耗時處理改成非同步，handler 驗完章立刻回 200。」

### 關卡四：順序不保證——用狀態機擋住時光倒流

網路重試會讓事件**亂序**：你可能先收到「付款成功」、後收到早先發出的「處理中」。如果照單全收，訂單狀態就會倒退。解法是把狀態轉移寫成**只進不退**的規則：

```java
/** 狀態機守護：只允許合法前進，晚到的舊事件直接忽略 */
private static final Map<String, Set<String>> ALLOWED = Map.of(
    "PENDING",    Set.of("PROCESSING", "PAID", "FAILED"),
    "PROCESSING", Set.of("PAID", "FAILED"),
    "PAID",       Set.of("REFUNDED"));   // PAID 不准退回 PROCESSING

public void transition(Order order, String next) {
  if (!ALLOWED.getOrDefault(order.getStatus(), Set.of()).contains(next)) {
    log.info("忽略過期事件：{} -> {}", order.getStatus(), next);
    return;   // 舊事件晚到：忽略，不是報錯
  }
  order.setStatus(next);
}
```

搭配事件本身的時間戳判斷會更穩，但「狀態機只進不退」是最容易落地的第一道防線。

> 🤖 丟給 AI：「幫我的訂單狀態加上只進不退的狀態機，晚到的舊事件直接忽略。」

### 加碼：本機開發怎麼收 webhook？

對方要打的是**公網 URL**，你的 localhost 它搆不到。開發期的標準解是隧道工具：`cloudflared tunnel` 或 ngrok，把公網位址轉發進你的本機——記得隧道 URL 每次重啟會變，測完要更新對方後台的設定。

> 🤖 丟給 AI：「用 cloudflared tunnel 幫我把本機的 webhook 接收端開到公網測試。」

## 觀念升級：Webhook 其實是「事件驅動架構」的一塊拼圖

把鏡頭拉遠一點。這期講的所有東西，背後是同一套思維：**事件驅動（Event-Driven）**——系統之間不互相輪詢對方的狀態，而是「事件發生的那一刻，通知在乎它的人」。

事件驅動架構有三個角色：**生產者**（發生事件的一方）、**消費者**（在乎事件的一方）、以及中間傳遞事件的**通道**。差別只在通道長什麼樣：

- **同一個系統內部**：用訊息佇列或 pub/sub 當通道——Kafka、RabbitMQ，或上上期用來做 WebSocket 跨實例廣播的 Redis pub/sub。內部通道可靠、有序、可重播，是事件驅動的完全體。
- **跨組織邊界**：金流服務跟你之間**沒有共享的訊息佇列**（人家不可能連進你的 Kafka），所以退而求其次用最通用的語言——HTTP。**Webhook 就是「用 HTTP 湊合出來的跨界事件通道」**。

看懂這層，本期四道關卡的「為什麼」就通了：內部佇列原生提供的可靠投遞、去重、順序保證，HTTP 通通沒有——所以簽章、冪等、狀態機這些工，全都是**在 HTTP 上手工補回佇列的保證**。

實務上兩者常常接力：webhook 進門（跨界）→ 驗章去重後丟進內部佇列（可靠處理）→ 處理完再透過 SSE/WS 通知瀏覽器（觸及使用者）。事件從外部一路流到使用者眼前，每一段用的都是那個舞台最合適的通道。（事件驅動架構的深水區——Event Sourcing、CQRS、Saga——又是另一個系列的份量了，有興趣回信告訴我。）

## 系列完結：一張圖收工

四期講完，整張地圖收攏成一句話——**一切都是「你去問」與「它來說」的選擇題**，差別只在舞台：

- 瀏覽器對伺服器：輪詢（問）→ SSE（單向說）→ WebSocket（對講）→ WebRTC（點對點）
- 伺服器對伺服器：輪詢 API（問）→ Webhook（它來說）

下次接金流、串平台、讓 AI 寫整合程式時，記得先問：這是誰對誰說話？然後把這系列對應的那期翻出來。

四期完結，感謝收看。

—— 凱文大叔

---

延伸閱讀：

- [GitHub Webhooks：驗證簽章](https://docs.github.com/en/webhooks/using-webhooks/validating-webhook-deliveries)
- [Stripe Webhooks 最佳實務](https://docs.stripe.com/webhooks)
- [Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/)
```

---

## 寄送提醒

- **必須在 010 之後寄出**：010 的 Webhook 親戚小節已預告本期。
- **工商卡片未填優惠碼**：文案主打金流串接與整合實戰；優惠碼寄送前確認。四期皆有工商卡，建議以投放配額只選其中一〜兩期。
- 本期含「事件驅動架構」觀念補充小節（Kafka/RabbitMQ/Redis pub/sub 僅點名不比較）；程式碼區塊 4 段（圍欄式 `java`）＋示意圖 2 張（媒體庫 PNG，測試信確認載入）——簽章驗證程式碼為教學示意，**寄送前確認未影射任何特定金流商的實際欄位名**（現用通用的 `X-Signature`）。
- 後段四道關卡＋加碼小節各附一句「🤖 丟給 AI」提示詞（blockquote 格式，沿用 008 慣例），**測試信確認引用區塊樣式正常**（不支援就改粗體行內文字）。
- GitHub／Stripe／LINE 等僅作場景舉例，未做服務比較；「至少一次送達」為業界通則描述。
- **主題一含 2 張投影片流程圖（已上傳媒體庫，URL 已替換）**：來源 `newsletter/assets/png/011-c-agentic-coding-loop.png`、`011-d-four-stations-map.png`（由 `newsletter/assets/capture-slide-flows.mjs` 從 live-slides 擷取，可重跑；URL 記錄於 `uploaded-urls.txt`）；圖為深色底，**測試信確認在淺色信件背景中觀感正常**。
- **本期含 `<!--paywall-->`**：設在主題二「後段：接收端的四道關卡」之前——免費區＝主題一新系列開場＋Webhook 觀念與場景（含工商卡），付費區＝四道關卡實作、隧道工具與事件驅動觀念升級。**測試信分別以免費／付費讀者身分確認截斷位置正確**。讀者頁的 paywall 卡片會**自動列出牆後的 H2／H3 章節標題**（本期為「後段：接收端的四道關卡」＋四道關卡＋加碼＋觀念升級＋系列完結，共 8 條），無需手動撰寫預告。
- 系列四期寄畢後，可在讀者頁把四期整理成系列合集入口（010 的寄送提醒原建議三期，以此為準更新）。
- **本期起改一期兩主題，且主題一＝新系列開場（免費導流）、主題二＝Webhook 深水區（專業內容，設付費牆）**：基礎篇純觀念無程式碼無截圖放前面吸新讀者，專業實作放後面支撐付費價值。012–015 依序走四站——**012 必須在本期之後寄出**。**測試信確認兩主題間的 H1 分隔渲染正常**（若信件模板不支援雙 H1，主題二標題改粗體大字）。
