# 電子報第 018 期範本：一條指令把 Spring Boot + React 送上公網（TUNNEL 系列 T1）

> **用途**：單一主題——**TUNNEL 系列 T1**（Cloudflare Tunnel 實戰篇）：
> 告別本機孤島，用 `cloudflared` 與一份 `config.yml`，在免固定 IP、免開路由器 Port、免手動申請 SSL 憑證的前提下，將本地的 Spring Boot（8080）與 React（5173）透過自訂網域安全發布到外網，並天然解決前後端 CORS 問題。
> **使用方式**：將下方「內文（Markdown）」複製到 admin 後台，先更新預覽並寄測試信，確認後再正式寄送。
>
> **「TUNNEL」系列鋪陳（017 起；回扣 010 WebRTC 與 011 Webhook）**：
>
> - 017 T0 原理篇：從 WebRTC 打洞到 Tunnel 反向通道——為什麼防火牆只擋入站？
> - 018（本期）T1 實戰篇：Cloudflare Tunnel 實戰——一條指令把 Spring Boot + React 送上公網
> - 019 T2 除錯篇：本機收真實 Webhook——告別 Mock，斷點攔截外部回調
> - 020 T3 防護篇：Zero Trust 零信任防護——不寫一行 code 幫 AI 系統加上 Google / OTP 驗證
>
> **前置條件**：017 結尾已預告本期（「一條指令把 Spring Boot + React 送上公網」）——**必須在 017 之後寄出**。
> **主視覺**：已生成 `newsletter/newsletter-18-cover.png`（16:9），**已上傳媒體庫**（URL 見 `assets/uploaded-urls.txt`），設定期數封面時直接取用。
> **素材來源**：兩張 16:9 示意圖為 `newsletter/assets/018-a-tunnel-ingress-routing.svg`、`018-b-quic-multiplexing.svg`（已繪製並轉為 PNG），**已上傳媒體庫且內文 URL 已替換為真實網址**（公開驗證 HTTP 200、SHA256 相符）。
>
> 文章中段以「工商時間」卡片（`<!--promo-->` 標記）宣傳 Hahow 課程，
> **優惠碼與期限需在寄送前確認後填入**。

---

## 建議主旨（三選一）

1. `免固定 IP、不開 Port：一條指令把本地全端系統送上公網`
2. `還在幫後端解 CORS？用 Cloudflare Tunnel 把 Spring Boot 與 React 綁進同一個網域`
3. `TUNNEL 系列（一）：你的本地 AI CRM，今天就可以安全讓全世界連線`

**推薦**：`免固定 IP、不開 Port：一條指令把本地全端系統送上公網`

## 預覽文字（preheader）

> 不用租雲端主機、不碰路由器設定、自帶免費 HTTPS。用一份 config.yml 同時轉發 8080 與 5173，順便徹底消滅跨域 CORS。

---

## 內文（Markdown，直接複製貼上）

```markdown
<!--promo-->
### 工商時間

這系列講的是「讓本地系統安全走入公網」——而在那之前，你得先有一套真正能打的 AI 全端應用：

**《AI 賦能全端開發：從零打造企業級智慧應用》**：從 Spring Boot 後端、React 前端到資料庫與權限，一路加上 Spring AI、RAG 與 AI Agent，用一個專案走完整條路。

🎁 **課程最後額外加碼「Cloudflare Tunnel 穿透實戰」**：教你如何在免固定 IP、不開路由器 Port 的安全前提下，把公司內部或本機架設好的全套系統安全穿透到公網，讓外部客戶、主管或團隊直接連線體驗！

👉 [前往 Hahow 課程頁](https://hahow.in/cr/ai-full-stack)
<!--/promo-->

# 一條指令把 Spring Boot + React 送上公網：Cloudflare Tunnel 實戰

上期我們把內網穿透的底層秘密拆開了：**不要等待外部連進來，而是由本地主動向雲端邊緣（Edge）建立出站長連線。**

這期我們直接動手。

情境很常見：你在筆電上跑著 Spring Boot 後端（`localhost:8080`），前端 React 開發伺服器跑在 Vite（`localhost:5173`）。客戶或主管說想線上看一下 Demo，或者你想在手機上實測觸控體驗。

你不需要去 AWS 開 EC2、不需要買固定 IP、不需要登入路由器設定 Port Forwarding，甚至不用自己去申請 SSL 憑證。

我們只需要一個工具：**`cloudflared`**。

## 四步打通公網隧道

整個設定流程乾淨俐落，在 Windows（PowerShell）或 macOS/Linux 上完全一致：

### 第一步：安裝並登入 Cloudflare

```powershell
# Windows 使用 winget 安裝（或至官方 GitHub 下載單一執行檔）
winget install Cloudflare.cloudflared

# 授權登入（會自動彈出瀏覽器，選擇你託管在 Cloudflare 的網域名稱）
cloudflared tunnel login
```

### 第二步：建立專屬隧道（Tunnel）

```powershell
# 建立名為 ai-crm 的隧道
cloudflared tunnel create ai-crm
```

執行後會得到一個隧道的 **UUID**（例如 `a1b2c3d4-5678-90ab-cdef-1234567890ab`），並在本地的 `.cloudflared` 資料夾生成以該 UUID 命名的認證憑證檔案（`<UUID>.json`）。

### 第三步：核心靈魂——一份 `config.yml` 搞定前後端同站路由

傳統部署全端專案最煩的就是 **CORS（跨來源資源共用）**：前端在 `domain-a.com`，後端在 `api.domain-b.com`，每次發請求瀏覽器都要先送一次 `OPTIONS` preflight，Headers 沒設好就直接報紅。

Cloudflare Tunnel 的 Ingress Rules 讓我們用一份檔案，把前後端**收攏進同一個網域名稱**：

```yaml
# config.yml
tunnel: a1b2c3d4-5678-90ab-cdef-1234567890ab
credentials-file: C:\Users\your-name\.cloudflared\a1b2c3d4-5678-90ab-cdef-1234567890ab.json

ingress:
  # 1. 將 /api 開頭的所有後端請求，導向 Spring Boot (8080)
  - hostname: crm.yourdomain.com
    path: ^/api
    service: http://localhost:8080

  # 2. 其餘所有頁面請求，導向 React Vite 前端 (5173)
  - hostname: crm.yourdomain.com
    service: http://localhost:5173

  # 3. 兜底規則（Catch-all）：未匹配的請求回傳 404
  - service: http_status:404
```

![Cloudflare Tunnel Ingress 路由：一份 config.yml 同時轉發後端 8080 與前端 5173，消除 CORS 預檢請求](https://springai-media.zeabur.app/newsletter-media/images/91d18af24edf0973e6d0d1b45d74fb787af5a861919be32fe6cc41eab487fa2a.png)

### 第四步：綁定 DNS 並啟動隧道

```powershell
# 讓 Cloudflare 自動在你的網域下加上 CNAME DNS 紀錄
cloudflared tunnel route dns ai-crm crm.yourdomain.com

# 啟動隧道！
cloudflared tunnel run ai-crm
```

按下 Enter 的那一瞬間，打開瀏覽器輸入 `https://crm.yourdomain.com`——
**你的本地開發機，已經頂著全球 Anycast CDN 與正式 HTTPS 憑證，正式對全世界開放了。**

<!--paywall-->

## 深入底層：Tunnel Ingress 與 QUIC 多工傳輸原理

在進入具體設定的地雷之前，我們先把 `cloudflared` 是如何在網路層做到「一條通道載入多個服務」的底層機制拆開：

### 1. 預設 4 條 QUIC 平行長連線
當你執行 `cloudflared tunnel run` 時，它並不是發起一般的 HTTP 代理，而是**主動向離你最近的 Cloudflare Anycast 邊緣節點建立 4 條平行的 QUIC（基於 UDP 7844 埠）加密長連線**。
* **為什麼是 4 條？** 為了高可用性（HA）與負載平衡，分別連往不同的邊緣伺服器。
* **多工傳輸（Multiplexing）**：所有的網頁請求、靜態資源、REST API 以及 SSE / WebSocket 長連線，全部被包裝成獨立的 Stream 在這 4 條 QUIC 通道中平行傳輸。這徹底消滅了傳統 HTTP/1.1 或 TCP 連線的**隊頭阻塞（Head-of-Line Blocking）**。

![Tunnel 底層連線：4 條 QUIC 平行長連線與多工傳輸，單一 Agent 乘載 REST API、React、SSE 與 WebSocket，消滅 TCP 隊頭阻塞](https://springai-media.zeabur.app/newsletter-media/images/b7abac17e993622964a30bf7ac51a365a6d74d172ed54488528a2425fd7dab15.png)

### 2. Ingress 規則比對生命週期（First-match-wins）
當外部請求打到 `crm.yourdomain.com/api/customers` 時，Cloudflare 邊緣節點只負責一件事：依網域名稱把流量塞進對應的隧道。你的 `config.yml` 從頭到尾都留在本機——**是 `cloudflared` 在你的電腦上，對每一個進來的請求進行由上而下的順序比對（First-match-wins）**：

```text
外部請求 ──▶ 命中 第一條 (path: ^/api) ──▶ 立即轉向 Spring Boot (localhost:8080)
                                            （不再往下比對）
外部請求 ──▶ 未命中 ^/api ──▶ 命中 第二條 (hostname: crm...) ──▶ 轉向 React Vite (localhost:5173)

外部請求 ──▶ 網域名稱完全不符 ──▶ 跌入 最後一條 Catch-all ──▶ 回傳 404 Not Found
```

這就是為什麼在 `config.yml` 中，**越精準、越特定的路徑規則一定要放在最前面**，而 React 的泛用規則放後面，最後以 `service: http_status:404` 收尾。

## 後段：魔鬼在細節——三個上線實戰避坑點

很多工程師第一次跑通 Tunnel 後很興奮，但一接到真實 AI CRM 系統，馬上會遇到三個深水區問題：

### 一、SSE 串流打字機效果被「卡住」了？

在第 009 期我們講過，Spring AI 的 ChatClient 串流回應是走 **SSE（Server-Sent Events）**。

如果你透過 Cloudflare Tunnel 存取，發現 AI 回應不再是一字一字吐出來，而是「卡住 10 秒後整大包一口氣噴出來」，這是代理鏈路上的**回應緩衝（Response Buffering）**在作怪——串流被中間層攢成一大包才放行。

**官方說法**：Cloudflare 官方 Troubleshooting 文件明載「經 Tunnel 代理的流量**預設會被緩衝**，除非 origin 回應帶有 `Content-Type: text/event-stream` 標頭」；歷史回報 cloudflared issue #199 也由 Cloudflare 工程師在 2025 年 5 月宣告修復關閉（「SSE 已修復，即使沒設對標頭也能運作」）。

**但我們實測踩到了官方修復的漏網之魚**：用 cloudflared 2026.1.2 開 Quick Tunnel 實測（伺服器每秒送 1 筆、共 5 筆），即使 `text/event-stream`、`X-Accel-Buffering: no` 都設了、回應也未壓縮——

* **GET 的 SSE**：5 筆全部在串流結束的瞬間同時到達（被整包緩衝）❌
* **POST 的同一個 SSE 端點**：每筆約 8ms 延遲即時到達 ✅
* **同一條隧道的 WebSocket**：每秒 1 筆完美即時 ✅

這個「GET 被緩衝、POST 即時」的行為與仍然 open 的 cloudflared issue #1449 完全吻合——官方尚未回應此 issue。

**實戰結論**：瀏覽器原生 `EventSource` 只能發 GET，正好踩在雷區上。要讓 AI 打字機效果穿過 Tunnel，選這三條路之一：

1. **改用 fetch + POST 讀取串流**（多數 LLM 聊天介面本來就這樣做），後端 SSE 端點改收 POST；
2. **改走 WebSocket**（實測同隧道即時無緩衝）；
3. 保底仍把標頭設好——`text/event-stream` 是官方文件明定的停用緩衝開關，別因為它「這次沒救到 GET」就拔掉：

```java
// Spring Controller SSE 正確標頭宣告
@GetMapping(value = "/api/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> streamChat(@RequestParam String prompt, HttpServletResponse response) {
    // 告知 Cloudflare 與 Nginx 等反向代理不要緩衝此串流
    response.setHeader("X-Accel-Buffering", "no");
    response.setHeader("Cache-Control", "no-cache");
    return chatService.stream(prompt);
}
```

### 二、天然消滅 CORS，但 React 前端要做對一件事

當你的 `config.yml` 設定了同站路由後：
* 瀏覽器看的是：`https://crm.yourdomain.com`（由 Vite 提供 React 靜態檔案）
* API 呼叫發向：`https://crm.yourdomain.com/api/customers`（由 Cloudflare 轉給 Spring Boot）

兩者**完全同源（Same-Origin）**！這意味著：
1. 瀏覽器**永遠不會發送 OPTIONS preflight 預檢請求**，網路延遲直接少一半。
2. 你甚至可以把 Spring Security 中那一大串複雜脆弱的 `CorsConfigurationSource` 拔掉。

**前端唯一要注意的**：在 React（如 Axios 或 fetch）中，**API Base URL 不要寫死 `http://localhost:8080`**，直接使用相對路徑 `/api`：

```typescript
// ❌ 傳統分開部署的寫法（引發 CORS 且環境切換麻煩）
const API_URL = "http://localhost:8080/api";

// ✅ 配合 Tunnel Ingress 的同源寫法（乾淨、安全、無 CORS）
const API_URL = "/api";
```

### 三、把本地電腦變成真正不中斷的伺服器

如果你是用筆電或桌機做長期的內部系統 Demo，只要終端機視窗一關閉，Tunnel 就會斷線。

Cloudflare 支援直接把 Tunnel 註冊成 **系統原生背景服務（Windows Service / systemd）**：

```powershell
# 以管理員權限執行：安裝為 Windows 系統服務
cloudflared service install

# 關鍵避坑：服務是以 SYSTEM 帳戶執行的，讀不到你使用者目錄下的設定！
# 必須把 config.yml 與 <UUID>.json 憑證複製到 SYSTEM 帳戶的 .cloudflared 目錄
Copy-Item "$HOME\.cloudflared\*" "C:\Windows\System32\config\systemprofile\.cloudflared\"

# 啟動服務（開機自動在背景啟動，即便使用者登出依然在線）
Start-Service cloudflared
```

漏掉中間那步複製，服務會裝得起來、卻在啟動時因為找不到設定檔而默默失敗——這是官方文件明載、也是最多人第一次裝服務就踩中的坑。

這樣一來，你的本地電腦只要開著機連上網路，就是一台固若金湯的專屬伺服器。

---

🤖 想讓 AI 幫你為複雜的多服務專案產生最精確的 `config.yml`，可以直接使用這段提示詞：

```text
我有以下本地全端微服務架構，需要使用 Cloudflare Tunnel 整合到單一網域 https://app.example.com：
1. 前端 React (Vite): http://localhost:5173 (處理首頁與靜態頁面)
2. 核心 API (Spring Boot): http://localhost:8080 (處理 /api/* 請求)
3. WebSocket 即時通訊: http://localhost:8080/ws (處理雙向連線)
4. Grafana 監控後台: http://localhost:3000 (處理 /metrics 與 /grafana/*)

請幫我撰寫一份 production-ready 的 cloudflared config.yml，要求：
1. Ingress 規則路徑優先順序正確，避免廣義規則蓋過特定路徑
2. 包含 catch-all 404 兜底
3. 標註說明針對 SSE / WebSocket 長連線的最佳化參數與注意事項
```

## 系列小結與下期預告

這期我們完成了第一步：**把本地前後端安全、無痛、免開 Port 地送上公網。**

系統現在能讓全世界連線了。但作為開發者，你馬上會遇到第二個更頭痛的問題：

當你要串接 LINE Bot、Stripe 刷卡、GitHub Actions 或外部 AI 平台的 **Webhook** 時，外部通知打進來了——**你要怎麼在 本機 IDE 裡設斷點（Breakpoint），一行一行攔截除錯那些動態 Payload 與簽章？**

下期（019 期）TUNNEL 系列第二篇：**《本機收真實 Webhook：告別 Mock，斷點攔截外部回調》**，我們來把開發體驗拉到極致！

—— 凱文大叔

---

延伸閱讀：

- [Cloudflare Tunnel 官方設定檔與 Ingress Rules 手冊](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/do-more-with-tunnels/local-management/configuration-file/)
- [MDN：Understanding CORS and Same-Origin Policy](https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS)
- [Server-Sent Events (SSE) Proxy Buffering Best Practices](https://www.nginx.com/resources/wiki/start/topics/examples/x-accel/)
- [Cloudflare Tunnel 官方 Troubleshooting：串流回應預設緩衝的說明](https://developers.cloudflare.com/tunnel/troubleshooting/)
- [cloudflared issue #199：SSE 緩衝歷史回報（官方已修復關閉）](https://github.com/cloudflare/cloudflared/issues/199)
- [cloudflared issue #1449：Quick Tunnel 上 GET SSE 仍被整包緩衝（open，本文實測重現）](https://github.com/cloudflare/cloudflared/issues/1449)

```

---

## 寄送提醒

- **必須在 017 之後寄出**：017 主題二結尾已預告本期（「一條指令把 Spring Boot + React 送上公網」）。
- **付費分段**：`<!--paywall-->` 切在「深入底層：Tunnel Ingress 與 QUIC 多工傳輸原理」之前。
- **示意圖已上傳**：2 張 16:9 繁體中文資訊圖（`018-a-tunnel-ingress-routing.png`、`018-b-quic-multiplexing.png`）與封面 `newsletter-18-cover.png` 均已上傳媒體庫，內文 URL 已替換為真實網址並通過公開驗證（HTTP 200、SHA256 相符），無需再處理圖片。
- **工商卡片**：文章前半段包含 Hahow 課程與 Cloudflare Tunnel 加碼單元宣傳。
- **下期預告**：結尾預告 019 期（T2 Webhook 本地斷點除錯）。
