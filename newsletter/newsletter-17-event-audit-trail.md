# 電子報第 017 期範本：事件即 Agent 的稽核軌跡（EVENT 系列 E5・系列完結）＋從 WebRTC 打洞到 Tunnel 反向通道（TUNNEL 系列 T0）

> **用途**：一期兩主題。
> 主題一＝**EVENT 系列 E5，系列完結**：把 Agent 每一步記成事件流，用來回放、稽核、算成本；
> 並在結尾收攏整個 EVENT 系列（E0–E5），同時回扣 012–015「可審查產物」的主軸。
> 主題二＝**TUNNEL 系列 T0，全新系列啟動**：從 010 期的 WebRTC NAT 打洞概念出發，解析為什麼本機 Web 服務不能用 P2P、防火牆「只出不進」的限制，以及現代內網穿透（Tunnel）如何用「反向通道」解決本地開發收 Webhook 與免開 Port 上線問題。
> **使用方式**：將下方「內文（Markdown）」複製到 admin 後台，先更新預覽並寄測試信，確認後再正式寄送。
>
> **「EVENT」系列鋪陳（012 起；設計見 `docs/superpowers/specs/2026-08-08-newsletter-event-series-design.md`）**：
>
> - 012 E0 地圖：解耦 ≠ 非同步，`publishEvent()` 預設同步
> - 013 E1：為什麼 Agent 一定要非同步、`@Async` 的三個陷阱
> - 014 E2：事件送得出去——交易邊界與 Outbox
> - 015 E3：事件收得下來——重複執行的代價
> - 016 E4：事件回得了前端——進度、失敗與死信
> - 017（本期）E5：事件即 Agent 的稽核軌跡（系列完結）
>
> **「TUNNEL」系列鋪陳（017 起；回扣 010 WebRTC 與 011 Webhook）**：
>
> - 017（本期）T0：從 WebRTC 打洞到 Tunnel 反向通道——為什麼防火牆只擋入站？
> - 018 T1：Cloudflare Tunnel 實戰——一條指令把 Spring Boot + React 送上公網
> - 019 T2：本機收真實 Webhook——告別 Mock，斷點攔截外部回調
> - 020 T3：Zero Trust 零信任防護——不寫一行 code 幫 AI 系統加上 Google / OTP 驗證
>
> **前置條件**：016 結尾已預告本期（「Agent 自己跑的每一步，誰來審查」）——**必須在 016 之後寄出**。
> 本期主題二承接 010 期 WebRTC 的 NAT / STUN / TURN 概念與 011 期 Webhook 的本地接收痛點。
> **主視覺**：已生成 `newsletter/newsletter-17-cover.png`（16:9）；寄送前上傳媒體庫。
> **素材來源**：三張示意圖為 `newsletter/assets/017-a-agent-trace.svg`、
> `017-b-trace-three-uses.svg`、`017-c-reverse-tunnel.svg`（已繪製），寄送前需上傳媒體庫並替換文中 TODO 圖片 URL。
>
> 文章中段以「工商時間」卡片（`<!--promo-->` 標記）宣傳 Hahow 課程，
> **優惠碼與期限需在寄送前確認後填入**。系列完結期適合放課程卡。

---

## 建議主旨（三選一）

1. `「這份摘要是怎麼來的？」——你答得出來嗎（加映：內網穿透的底層秘密）`
2. `事件流就是 Agent 的稽核軌跡（EVENT 完結）＋為什麼你需要 Tunnel`
3. `你要求 AI 交出可審查的產物，那 Agent 自己呢？`

**推薦**：`「這份摘要是怎麼來的？」——你答得出來嗎（加映：內網穿透的底層秘密）`

## 預覽文字（preheader）

> Agent 讀了哪些資料、花了多少 token——把每一步記成事件，稽核就有答案。加映：從 WebRTC 打洞看懂內網穿透與 Tunnel。

---

## 內文（Markdown，直接複製貼上）

```markdown
<!--promo-->
### 工商時間

這六期都建立在同一套 AI CRM 上——如果你想從頭把它做出來，而不只是讀懂它：

**《AI 賦能全端開發：從零打造企業級智慧應用》**：從 Spring Boot 後端、React 前端到資料庫與權限，一路加上 Spring AI、RAG 與 AI Agent，用一個專案走完整條路。

🎁 **課程最後額外加碼「Cloudflare Tunnel 穿透實戰」**：教你如何在免固定 IP、不開路由器 Port 的安全前提下，把公司內部或本機架設好的全套系統安全穿透到公網，讓外部客戶、主管或團隊直接連線體驗！

👉 [前往 Hahow 課程頁](https://hahow.in/cr/ai-full-stack)
<!--/promo-->

# 主題一｜事件即 Agent 的稽核軌跡

這是 EVENT 系列的最後一期。

前五期把事件的路鋪完了：解耦、非同步、送得出去、收得下來、回得了前端。系統現在跑得又快又穩。

然後客戶問了一句話：

> 「這份摘要是怎麼來的？」

你打開資料庫，看到 `summary` 欄位裡躺著一段文字。你知道它是 AI 產生的。除此之外，什麼都不知道。

## 三個你回答不出來的問題

**它讀了什麼？** 摘要說「客戶近期互動頻率下降」——它是根據哪幾筆互動紀錄講的？如果那幾筆資料本身有問題，這句結論就是錯的，但你查不到它讀了哪幾筆。

**它花了多少錢？** 這個月 LLM 帳單三萬塊。哪些功能花的？哪個客戶的操作最貴？有沒有哪個迴圈在默默重跑？帳單只給你一個總數。

**為什麼這次跟上次不一樣？** 同一個客戶，上週的摘要說「關係穩定」，這週說「有流失風險」。是資料真的變了，還是你上週改了 system prompt？你想不起來，也查不出來。

三個問題有一個共通點：**它們問的都是「過程」，但你只留下了「結果」。**

## 這件事其實很眼熟

回想 012 到 015 那個系列的主軸：不要滿足於 AI 給你一句話，要求它交出**可以被審查的產物**——可行性評估報告、選型分析、測試、稽核表。

那些都是在講「你怎麼要求 AI」。

現在把鏡頭轉過來：**你的 Agent 每天自動跑幾千次，它自己交出了什麼可以被審查的東西？**

答案通常是：一個結果欄位。跟「這個功能能不能做？」換來的那句「可以做」，本質上是同一種東西——**沒得追蹤、沒得反駁、沒得改**。

而修補的方法，跟前面五期一直在做的事情是同一招：**把過程記成事件。**


<!--paywall-->

## 後段：一張表，三個答案

### 軌跡表長什麼樣

```sql
create table agent_steps (
    id        bigint auto_increment primary key,
    trace_id  varchar(64)  not null,     -- 同一次執行的所有步驟共用
    step_no   int          not null,     -- 這次執行的第幾步
    step_type varchar(30)  not null,     -- PROMPT / TOOL_CALL / TOOL_RESULT / LLM_RESPONSE
    content   varchar(500),
    tokens    int          not null default 0,
    created_at timestamp   not null
);
```

兩個設計重點：

**一、`trace_id` 是主角。** 一次 Agent 執行從頭到尾共用同一個 `trace_id`，這樣才能把散落的步驟重新串成一條線。用什麼當 trace_id 都行，UUID 最省事。

**二、只 insert，不 update。** 這張表是 append-only 的——每一步都是「發生過的事實」，事實不會被修改。這跟 E0 講事件命名要用過去式是同一個道理，而且它讓這張表天然具備稽核價值：**沒有人能事後改寫歷史**。

### 記哪些步驟

一次典型的 AI CRM 摘要產生，軌跡長這樣：

```text
PROMPT        system prompt v3 + 客戶 42 的近況請求          320 tokens
TOOL_CALL     findRecentInteractions(customerId=42)
TOOL_RESULT   回傳 5 筆互動紀錄                              210 tokens
TOOL_CALL     calculateHealthScore(customerId=42)
TOOL_RESULT   健康分數 72                                     40 tokens
LLM_RESPONSE  客戶 42 近期互動頻率下降，建議主動聯繫         180 tokens
```

![一次摘要產生留下的六行軌跡，共用同一個 trace_id：PROMPT（system prompt v3 加客戶 42 的近況請求，320 tokens）、TOOL_CALL（findRecentInteractions）、TOOL_RESULT（回傳 5 筆互動紀錄，210 tokens）、TOOL_CALL（calculateHealthScore）、TOOL_RESULT（健康分數 72，40 tokens）、LLM_RESPONSE（客戶 42 近期互動頻率下降建議主動聯繫，180 tokens），總計 750 tokens](https://springai-media.zeabur.app/newsletter-media/images/TODO-017-agent-trace.png)

六行，把「這份摘要是怎麼來的」完整交代了。

我把這段軌跡真的寫進表裡，然後用 SQL 把前面那三個問題各回答一次。

### 答案一：回放

```sql
select step_type, content from agent_steps
 where trace_id = ? order by step_no
```

```text
V1 回放：依 step_no 取回的順序與執行順序一致
   實際=[PROMPT, TOOL_CALL, TOOL_RESULT, TOOL_CALL, TOOL_RESULT, LLM_RESPONSE]
```

順序回得來，就代表你可以完整重現當時發生的事——包括它呼叫了哪些工具、拿到什麼結果。

### 答案二：成本歸屬

```sql
select sum(tokens) from agent_steps where trace_id = ?
```

```text
V2 成本歸屬：單次執行的 token 可加總
   trace-a 總 token=750
```

有了這個，帳單就不再是一個總數。你可以算「每次摘要平均花多少」、「哪個客戶最貴」、「這個月哪個功能成長最快」。

更重要的是——**你可以在成本異常的時候發現它**。某個迴圈開始重跑，token 用量會先跳起來，而不是等到月底看帳單才發現。

### 答案三：稽核

```sql
select step_type, content from agent_steps
 where trace_id = ? and step_type in ('PROMPT', 'TOOL_CALL', 'TOOL_RESULT', 'LLM_RESPONSE')
```

```text
V3 稽核：提示、工具呼叫、工具結果與最終回應都查得回來
   提示=1 / 工具呼叫=2 / 工具結果=2
   最終回應=「客戶 42 近期互動頻率下降，建議主動聯繫」
```

客戶問「這份摘要是怎麼來的」，你現在有一條完整的答案鏈：**用了 v3 這版提示、查了這 5 筆互動、算出健康分數 72、據此得到這個結論。**

順帶一提，多次執行混在同一張表也不會亂：

```text
V4 兩次執行混在同一張表，依 trace_id 查詢不互相汙染
   trace-b 步數=2 / token=390 / 全表共=8
```

![同一張 agent_steps 軌跡表能回答三個問題：回放（order by step_no）回答「它讀了什麼」、成本歸屬（sum(tokens)）回答「這次花了多少」、稽核（依 step_type 查）回答「這結論怎麼來的」；表只 insert 不 update，事實不會被改寫](https://springai-media.zeabur.app/newsletter-media/images/TODO-017-trace-three-uses.png)

### 三個實務上一定會遇到的問題

**個資怎麼辦？** 軌跡裡會有客戶資料。做法是：`content` 欄位存摘要或引用（例如「回傳 5 筆互動紀錄，ID 101–105」）而不是原文照抄，需要細節時再回原表查。這樣軌跡表本身的個資密度大幅降低。

**提示詞版本要記。** 上面範例寫的是 `system prompt v3` 而不是完整提示詞全文。提示詞會改，改了之後輸出就會變——**不記版本，就回答不了「為什麼這次跟上次不一樣」**，而那正是三個問題裡最常被問的一個。

**保留多久？** 這張表長得很快（一次執行六筆）。實務上分兩層：詳細軌跡留 30 到 90 天，聚合後的統計（每日 token、每功能成本）永久保留。真正需要逐步回放的情境，幾乎都發生在事發後不久。

🤖 想幫自己的 Agent 補上軌跡，可以把這句丟給 AI：

```text
分析這個專案裡呼叫 LLM 的流程，設計一張 agent_steps 軌跡表，要求：
1. 列出這個流程中值得記錄的步驟類型，說明每一種各自能回答什麼問題
2. 指出哪些欄位可能含個資，並提出「存引用而非原文」的具體做法
3. 標出程式中應該插入記錄的位置（呼叫前、工具回傳後、回應後）
4. 說明如果只記最終結果，會失去回答哪些問題的能力
先給設計與位置清單，不要改程式碼。
```

## 系列收工

六期走完，回頭看整條路：

| | 問題 | 解法 |
|---|---|---|
| **E0** | 上游得認識每一個下游 | 事件解耦（但預設是同步的） |
| **E1** | Agent 要跑三十秒，使用者乾等 | `@Async`（小心三個陷阱） |
| **E2** | 交易 rollback 了，事件卻送出去了 | `@TransactionalEventListener` / Outbox |
| **E3** | 重送一次就多付一次錢 | 冪等鍵，而且順序不能顛倒 |
| **E4** | 使用者看不到進度與失敗 | 狀態機 + SSE + 退避 + 死信 |
| **E5** | 沒人知道 Agent 做了什麼 | 事件流當稽核軌跡 |

如果要我把六期壓成一句話，我會說：

> **每一步都留下一個「發生過什麼」的紀錄，剩下的問題大多會自己有答案。**

事件解耦是它、Outbox 是它、冪等鍵是它、狀態表是它、死信表是它、稽核軌跡也是它。同一招換六個場景。

而這也剛好接回前一個系列的主張——**要求可以被審查的產物**。差別只在於，那時候是你對 AI 提要求，這次是你的系統對自己提要求。

---

# 主題二｜從 WebRTC 打洞，到 Web 服務的反向通道：內網穿透與 Tunnel 原理

在第 010 期講即時通訊時，我們提過 WebRTC 的點對點（P2P）連線——兩台電腦各自躲在 NAT 與路由器後面，彼此看不到對方的真實 IP，所以需要 STUN 伺服器幫忙查公網位址，甚至靠 NAT 打洞（Hole Punching）建立連線，真的穿不透時才退回 TURN 中繼。

但如果你今天是在本地開發 Web 系統呢？

你在本機寫好了 Spring Boot 後端、啟動了 React，或是正在串接第 011 期講的 **Webhook**（例如接收金流通知、LINE Bot、GitHub Actions 或外部 AI 模型的異步回調）。

這時候就會發生每個工程師可能都經歷過的經典名場面：

> 興高采烈把網址貼到 Slack 給遠端的主管：「主管我做好了，你點 `http://127.0.0.1:8080` 看成果！」  
> 兩分鐘後主管困惑地回：「呃……我點進去怎麼只看到我自己的 Apache 測試頁？」

（又或者，你在第三方金流的 Webhook URL 欄位天真地填上 `http://localhost:8080/webhook`，然後等了一整個下午納悶為什麼刷卡成功永遠收不到回調。）

「There is no place like 127.0.0.1」——家裡再溫馨，外面的人就是連不進來。你的本機 IP 是 `192.168.1.42`（甚至前面還有好幾層公司的路由器 NAT），外部伺服器根本找不到你。

外部平台不可能跟你跑 WebRTC 握手打洞，它們只認得標準的公網 HTTPS 網址。

## 老派解法的痛苦與代價

傳統工程師遇到這個問題，通常有幾種老派解法：

1. **申請固定 IP ＋ 搞 DDNS（動態 DNS）**：每個月多花錢，而且在租屋處、咖啡廳或公司內網，你根本不可能有權限去要固定 IP。
2. **登入路由器設定 Port Forwarding（連接埠轉發）**：把路由器的 80/443 port 轉發給你的本機。同樣地，只要你不在自己家，你就動不了路由器。
3. **最危險的代價——資安門戶大開**：一旦你把本機 port 直接暴露給公網，幾分鐘內就會被全球的自動化爬蟲與漏洞掃描工具瘋狂試探。如果你的本地環境沒做好嚴格防護，你的整台開發機就直接在公網裸奔。

這就是為什麼現代開發幾乎不再有人用 Port Forwarding——我們改用**「反向通道（Reverse Tunnel）」**。

## 既然進不來，那就由我「主動連出去」

為什麼 Tunnel 能無視路由器與防火牆？關鍵就在於防火牆的運作邏輯：

* **入站流量（Inbound）**：嚴格阻擋。外部主動連進來的未知連線，防火牆一律直接丟棄（Drop）。
* **出站流量（Outbound）**：通常完全放行。因為你上網查資料、看網頁，本來就需要主動往外建立連線。

**Tunnel 內網穿透的底層秘密只有一句話：把「被動等待連入」變成「主動向外連線」。**

![傳統入站被防火牆阻擋 vs Tunnel 主動建立出站長連線：本地 cloudflared 向雲端邊緣發起連線，外部請求順著通道塞回本機，免開 Port、免固定 IP、自帶免費 SSL](https://springai-media.zeabur.app/newsletter-media/images/TODO-017-reverse-tunnel.png)

1. 你的本機啟動一個輕量的 Tunnel Agent（例如 `cloudflared`）。
2. Agent 主動向雲端邊緣伺服器建立一條加密的出站長連線（走 QUIC / HTTP/2）。
3. 當外部使用者或 Webhook 發送 HTTP 請求到你的專屬網址時，雲端邊緣節點收到請求，**順著剛才那條早就建好的出站通道，把請求「塞回」你的本機**。
4. 本機處理完後，再把回應順著通道送回去。

整個過程**不需要開任何路由器 Port、不需要固定 IP、不受多層 NAT 影響**，哪怕你在高鐵上用手機熱點上網，通道一樣能穩穩連通。

## 工具選型：為什麼以 Cloudflare Tunnel 為主力？

講到內網穿透，很多人第一個想到的是 **ngrok**。

ngrok 是極其經典的工具，下指令五秒鐘就能拿到網址。但用過的人都知道痛點：免費版每次重開網址都會變（Webhook 設定要重填）、有連線數與頻寬限制。它非常適合「5 分鐘臨時除錯」，但不適合當作穩定的部署方案。

而 **Cloudflare Tunnel（`cloudflared`）** 則是另一個維度的存在：

* **完全免費且不限流量**：背靠 Cloudflare 全球 Anycast CDN 網路。
* **支援綁定自己的網域名稱**：網址永久固定，再也不用每次重啟改 Webhook URL。
* **自動管理免費 SSL 憑證**：不用自己搞 Let's Encrypt，天然支援 HTTPS。
* **內建 Zero Trust（零信任）防護**：可以在不改程式碼的前提下，一鍵加上 Google 登入或 Email OTP 白名單，防止路人亂刷你的 AI Token 帳單。

## 下期預告

從這期開始，我們啟動全新的 **【TUNNEL 系列】**。

下期（018 期）第一篇實戰：**《一條指令把 Spring Boot + React 送上公網》**——我們會動手寫一份 `config.yml`，用同一個自訂網域名稱，優雅地把 `/api` 導給後端、其餘流量導給前端，完成真正的免伺服器安全上線！

—— 凱文大叔

---

延伸閱讀：

- [Cloudflare Tunnel 官方架構與運作原理](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/)
- [OpenTelemetry：分散式追蹤的 trace 與 span 概念](https://opentelemetry.io/docs/concepts/signals/traces/)
- [Martin Fowler：Event Sourcing](https://martinfowler.com/eaaDev/EventSourcing.html)
```

---

## 寄送提醒

- **必須在 016 之後寄出**：016 結尾已預告本期（「Agent 自己跑的每一步，誰來審查」），本期開頭直接回收該鉤子。
- **同時回扣 012–015 主題一**：內文「這件事其實很眼熟」一節引用「可審查產物」主軸，**該系列需已寄畢**，否則讀者接不上。
- **雙主題架構**：本期主題一為 EVENT 系列收官（E5），主題二為 TUNNEL 系列開篇（T0，回扣 010 WebRTC 與 011 Webhook）。
- **付費分段**：`<!--paywall-->` 切在主題一的「後段：一張表，三個答案」之前。
- **程式碼已實跑驗證**：`pwsh newsletter/scripts/verify-017-audit-trail-java.ps1`（Spring 6.2.12 + H2 2.3.232 + JDK 21）。文中四段實測輸出（V1–V4）與那段六行軌跡範例的數字，直接取自該腳本，**改動任何 SQL、軌跡內容或 token 數字後必須重跑**（V2 的 750 與 V4 的 390／8 都會跟著變）。
- **三張示意圖已繪製並轉為 PNG**：`newsletter/assets/017-a-agent-trace.svg`、`017-b-trace-three-uses.svg`、`017-c-reverse-tunnel.svg`（PNG 位於 `newsletter/assets/png/`）。重繪指令：`python newsletter/assets/generate_diagrams.py && node newsletter/assets/render-diagrams.mjs`。寄送前上傳媒體庫並替換 `TODO-017-*.png` 三個 URL，記錄 hash 到 `uploaded-urls.txt`。
- **本期是系列完結＋新系列啟動**：主題一結尾有六期總表，主題二結尾有 TUNNEL 新系列預告。
- **工商卡片**：系列完結期適合放課程卡，優惠碼寄送前確認。
