# 電子報第 020 期範本：Zero Trust 零信任防護——不寫一行 code 幫 AI 系統加上 Google / OTP 驗證（TUNNEL 系列 T3・系列完結）

> **用途**：單一主題——**TUNNEL 系列 T3，系列完結**（Cloudflare Access 零信任安全篇）：
> 解決 AI 應用上線後最致命的資安痛點——「Token 帳單被爬蟲或路人刷爆」。利用 Cloudflare Access 將驗證防線前移至全球邊緣節點（Edge），不改一列後端程式碼即可實現 Google OAuth 與 Email OTP 團隊白名單防護，並示範 Webhook 專用 Bypass 規則與 JWT Header 身份透傳。最後收攏 TUNNEL 系列（T0–T3）。
> **使用方式**：將下方「內文（Markdown）」複製到 admin 後台，先更新預覽並寄測試信，確認後再正式寄送。
>
> **「TUNNEL」系列鋪陳（017 起；回扣 010 WebRTC 與 011 Webhook）**：
>
> - 017 T0 原理篇：從 WebRTC 打洞到 Tunnel 反向通道——為什麼防火牆只擋入站？
> - 018 T1 實戰篇：Cloudflare Tunnel 實戰——一條指令把 Spring Boot + React 送上公網
> - 019 T2 除錯篇：本機收真實 Webhook——告別 Mock，斷點攔截外部回調
> - 020（本期）T3 防護篇：Zero Trust 零信任防護——不寫一行 code 幫 AI 系統加上 Google / OTP 驗證（系列完結）
>
> **前置條件**：019 結尾已預告本期（「Zero Trust 零信任防護」）——**必須在 019 之後寄出**。
> **素材來源**：兩張 16:9 示意圖為 `newsletter/assets/020-a-zero-trust-edge-barrier.svg`、`020-b-tunnel-series-summary.svg`（已繪製並轉為 PNG），寄送前需上傳媒體庫並替換文中 TODO 圖片 URL。
>
> 文章中段以「工商時間」卡片（`<!--promo-->` 標記）宣傳 Hahow 課程，
> **優惠碼與期限需在寄送前確認後填入**。系列完結期適合放課程卡。

---

## 建議主旨（三選一）

1. `你的 AI 網址被爬蟲抓到的那一刻，Token 帳單就開始失控了`
2. `不寫一行 Auth 程式碼：用 Cloudflare Access 給系統裝上企業級防盜門`
3. `TUNNEL 系列完結：從本機開發到零信任防護，打造最安全的發布閉環`

**推薦**：`你的 AI 網址被爬蟲抓到的那一刻，Token 帳單就開始失控了`

## 預覽文字（preheader）

> 把驗證防線推到全球 CDN 邊緣。未授權者連你的本地伺服器都碰不到，未登入先擋下，守護你的 API 帳單。

---

## 內文（Markdown，直接複製貼上）

```markdown
<!--promo-->
### 工商時間

這四期講完了「從本地穿透到零信任上線」——如果你想把這一整套架構親手實踐在真實企業專案中：

**《AI 賦能全端開發：從零打造企業級智慧應用》**：從 Spring Boot 後端、React 前端到資料庫與權限，一路加上 Spring AI、RAG 與 AI Agent，用一個專案走完整條路。

🎁 **課程最後額外加碼「Cloudflare Tunnel 穿透實戰」**：教你如何在免固定 IP、不開路由器 Port 的安全前提下，把公司內部或本機架設好的全套系統安全穿透到公網，讓外部客戶、主管或團隊直接連線體驗！

🤖 **課程也會再加碼說明如何透過 AI 控制 Cloudflare Tunnel**：讓 AI 協助你處理 Tunnel 的啟動、設定與發布流程，把「本機服務如何安全上線」從手動操作變成可理解、可執行的 AI 協作流程。

👉 [前往 Hahow 課程頁](https://hahow.in/cr/ai-full-stack)
<!--/promo-->

# 不寫一行 code，幫 AI 系統加上 Google / OTP 驗證：Zero Trust 實戰

這是 TUNNEL 系列的最後一期。

前兩期我們完成了兩件大事：把本地 Spring Boot 與 React 發布到公網（018 T1），並能接收全球真實 Webhook 斷點除錯（019 T2）。

最近 Zeabur 官方狀態頁也列出「Unauthorized Access to Project Environment Variable Data」事件（寄送前請以[官方最新更新](https://status.zeabur.com/incidents)為準）。這再次提醒我們：服務不一定要長期放在公有 PaaS 上才叫上線。

如果你手上已經有一台 24 小時不關機的主機，或某些服務根本不需要一直啟動，透過 Cloudflare Tunnel 把本機或內網服務接到 Cloudflare 邊緣，對這類情境往往是目前最省錢、也最方便的做法之一：不用固定 IP、不用開路由器 Port，也不必為閒置服務持續支付 PaaS 運算費。想補齊這段脈絡，可以回頭複習 018 T1「Cloudflare Tunnel 實戰」與 019 T2「本機收真實 Webhook」。

然後許多工程師在半夜驚醒：

> 「我的網址是公開的，如果有人發現了我的 `/api/ai/generate` 端點，拿迴圈狂送請求，我不就得為他的惡作劇付上萬元的 LLM 帳單？」

傳統的防禦方式非常耗時：自己寫 User 表、搞 JWT 登入、接入 Google OAuth SDK、做 Email 驗證碼服務、處理 Session 過期——隨便做都要兩週，而且寫得不好還有越權或 Token 外洩風險。

這期我們要用現代架構師的做法：**零信任（Zero Trust / Cloudflare Access）——把防禦防線推到全世界的雲端邊緣（Edge），不改動後端一列程式碼！**

## 防線前移：未授權者連你的電腦都碰不到

傳統的資安防禦是「後端防守」：請求先打進你的 Spring Boot，Spring Security 檢查失敗才回傳 401 Unauthorized。但這時候，**連線已經建立、伺服器資源已經被消耗**。

Zero Trust 的邏輯截然不同：**邊緣攔截（Edge Interception）**。

```text
【未登入 / 未授權的使用者】
使用者 ──▶ 雲端邊緣 (Cloudflare Edge) ──❌ [直接攔截！要求 Google / OTP 登入]
             （連線根本不會抵達本地伺服器）

【授權的團隊成員】
使用者 ──▶ 雲端邊緣 [驗證通過] ──▶ 順著 Tunnel ──▶ 本地 Spring Boot + React
```

任何沒有通過驗證的請求，在 Cloudflare 全球邊緣節點就直接被擋下來，連你本地電腦的 TCP 握手都碰不到。

<!--paywall-->

## 深入底層：Zero Trust 邊緣身分驗證與 JWT 信任鏈

在進入後台操作之前，我們先理解為什麼 Zero Trust 能做到「不改後端程式碼，卻具備企業級防護」的兩大底層架構：

### 1. 邊緣身分驗證生命週期（Edge Auth Lifecycle）
傳統架構下，驗證是伺服器的事；Zero Trust 架構下，**驗證在離使用者最近的 Anycast 邊緣節點就已完成**：

```text
瀏覽器請求 ──▶ [Cloudflare 邊緣節點]
                     │
                     ├─▶ 檢查是否有合法的 CF_Authorization Cookie？
                     │     ├─ [無] ──▶ 302 重新導向至 Cloudflare 登入頁（Google SSO / OTP）
                     │     │            登入成功後，發放加密 JWT Cookie，跳回原網址
                     │     │
                     │     └─ [有] ──▶ 邊緣驗證 JWT 簽章有效
                     │
                     ▼
             將使用者資訊拆解並注入 Header：
             • Cf-Access-Authenticated-User-Email: kevin@company.com
             • Cf-Access-Jwt-Assertion: eyJhbGci...
                     │
                     ▼（走 QUIC Tunnel 送達）
             本地 Spring Boot (localhost:8080)
```

本地後端接收到的每一個請求，都是已經被全球 CDN 邊緣篩選、認證過的「乾淨流量」。

### 2. 非對稱密碼學（RS256）與雙重防禦
如果你連 Cloudflare 邊緣轉發的 Header 都不想「盲目相信」，想要在 Spring Boot 進行第二層嚴格驗證：
* **為什麼不需要打 API 問 Cloudflare？** Cloudflare 採用 **RS256 非對稱加密**。Cloudflare Edge 使用私鑰為 JWT 簽名，並公開了公鑰證書端點（JWKS：`https://<your-team>.cloudflareaccess.com/cdn-cgi/access/certs`）。
* **本地離線秒驗**：後端 Spring Security 只要快取這組公鑰，就能在本地 CPU 花費不到 1 毫秒離線驗證 `Cf-Access-Jwt-Assertion` 的真偽與過期時間，做到完全無網路延遲的雙重防禦！
* **⚠️ 快取要會自動刷新**：Cloudflare 每 **6 週輪替一次簽章金鑰**（舊金鑰有 7 天寬限期）。請使用會依 TTL 自動重抓 JWKS 的機制（例如 Spring Security 的 `NimbusJwtDecoder.withJwkSetUri(...)`），不要在啟動時抓一次就寫死，否則長時間不重啟的服務會在某一天全數驗簽失敗。

## 三步啟用 Cloudflare Access

### 第一步：在 Zero Trust 後台建立應用程式（Application）

1. 登入 Cloudflare Zero Trust 後台 ➡️ **Access** ➡️ **Applications** ➡️ 點擊 **Add an application**。
2. 選擇 **Self-hosted**。
3. 填入你的應用名稱（例如 `AI CRM Internal`）與網域名稱（`crm.yourdomain.com`）。

### 第二步：設定存取原則（Policy）

在 Policies 標籤頁中，你可以輕鬆設定細緻的准入規則：

* **團隊 Email 網域白名單**：只允許 `*@yourcompany.com` 的員工透過 Google Workspace 一鍵登入。
* **特定人員白名單**：加入特定外部合作夥伴的 Email，系統會自動寄一組 **單次驗證碼（One-Time PIN, OTP；10 分鐘內有效、只能使用一次）** 到他的信箱，免註冊即可登入（需先在 **Zero Trust ➡️ Integrations ➡️ Identity providers** 啟用 One-time PIN；且只有已被 Policy 允許的 Email 才會收到信）。
* **地理位置限制**：只允許來自台灣或特定國家/地區的 IP。

### 第三步：關鍵細節——Webhook 路徑如何「免驗證放行（Bypass）」？

當你把整個 `crm.yourdomain.com` 鎖上之後，一般使用者必須登入才能看到畫面。

但第 019 期提到的 **第三方 Webhook（如 Stripe 或 LINE）** 是機器伺服器，它們不可能透過瀏覽器跳出 Google 登入！

**解法**：Access 的保護範圍是由 **Application（網域 ＋ 路徑）** 決定，Policy 只負責判斷「誰能過」——**Policy 的條件並不支援 URL 路徑**（可用的條件是 Email、Email 網域、IP 區段、國家、mTLS 憑證、裝置狀態、IdP 群組、Service Token 等）。所以正確做法是「**再開一個帶路徑的 Application**」：

1. 回到 **Access** ➡️ **Applications** ➡️ **Add an application** ➡️ **Self-hosted**，建立第二個應用。
2. 網域填 `crm.yourdomain.com`，**Path 欄位填 `/api/webhook/*`**（Cloudflare 會以最精確的路徑匹配優先套用這個應用的規則）。
3. 對這個應用建立一條 Policy，Action 選擇 **Bypass**，Include 條件選 **Everyone**。
4. 這樣一來：**人類瀏覽器必須登入才能看 CRM 與打 AI，而機器 Webhook 則能順利進入後端，由後端自己的 HMAC 簽章守護！**

⚠️ **兩個必須知道的代價**：Bypass 不套用任何 Access 安全控制，且該路徑的請求**不會寫進 Access 稽核 log**。所以 019 期教的 HMAC 簽章驗證不是加分項，而是這條路徑上唯一的防線。

![Zero Trust 邊緣身分驗證與 Webhook Bypass 混合防護：人類走 Google SSO / OTP 白名單登入，機器 Webhook 走 Bypass 繞過並由後端 HMAC 簽章守護，惡意流量與爬蟲在邊緣直接 403 阻擋](https://springai-media.zeabur.app/newsletter-media/images/TODO-020-zero-trust-edge-barrier.png)

## 後端如何知道「是誰登入了」？

你完全不需要在前端做登入表單，也不用在 Spring Boot 驗證 Google Token。

當使用者通過 Cloudflare Access 驗證後，Cloudflare 轉發請求給本地時，會自動在 HTTP Header 注入這兩個關鍵欄位：

```text
Cf-Access-Authenticated-User-Email: kevin@company.com
Cf-Access-Jwt-Assertion: eyJhbGciOiJSUzI1NiIs...
```

在 Spring Boot 中，你可以直接一行拿到當前使用者的身份：

```java
// 需搭配 @RestController 與 Lombok 的 @Slf4j（提供 log 物件）
@GetMapping("/api/crm/profile")
public ResponseEntity<Map<String, String>> getCurrentUser(
        @RequestHeader(value = "Cf-Access-Authenticated-User-Email", defaultValue = "anonymous") String email) {
    
    log.info("當前登入者 Email: {}", email);
    return ResponseEntity.ok(Map.of("email", email, "role", "internal-team"));
}
```

安全、優雅、零維護成本。

---

🤖 想為你的專案快速產出完整的 Cloudflare Access + Terraform / API 配置，可直接使用此提示詞：

```text
我有一個透過 Cloudflare Tunnel 部署的全端系統 https://crm.example.com，需要設定 Cloudflare Access (Zero Trust) 權限規則：
要求：
1. 預設規則 (Allow)：僅限公司郵件網域 @mycompany.com 使用 Google SSO 登入
2. 外部訪客規則 (Allow)：允許特定 email 清單使用 Email OTP 登入
3. 外部 Webhook 放行 (Bypass)：對 /api/webhook/* 與 /api/public/* 實施 Bypass
4. 說明後端 Spring Boot 如何透過 Header 讀取經過 Cloudflare 簽章的 JWT 進行第二層身份驗證
```

## 系列完結收工

![TUNNEL 系列全景總結：T0 原理篇（反向通道）、T1 實戰篇（同站路由與 CORS 消除）、T2 除錯篇（本機 Webhook 斷點與秒級解耦）、T3 防護篇（Zero Trust 零信任防禦）](https://springai-media.zeabur.app/newsletter-media/images/TODO-020-tunnel-series-summary.png)

走完四期 TUNNEL 系列，回頭看這條完整的網路基礎建設之路：

| 期數 | 核心問題 | 現代架構解法 |
|---|---|---|
| **T0 (017)** | 防火牆只擋 Inbound，本地無法被存取 | 反向通道（Outbound 長連線到邊緣） |
| **T1 (018)** | 前後端分開部署引發 CORS 與連線繁瑣 | `config.yml` Ingress 同站整合與免費 HTTPS |
| **T2 (019)** | 外部 Webhook 難除錯、串流讀取失敗 | Tunnel 本地斷點 + ContentCaching 驗簽 |
| **T3 (020)** | 公網暴露 AI 服務引發帳單與資安風險 | Zero Trust 邊緣攔截 + Bypass 混合防護 |

從單機開發、真實外部回調，到企業級零信任防禦——你現在擁有了隨時隨地將任何本地或公司內網系統，**安全、穩定、低成本發布給全世界**的完整能力！

—— 凱文大叔

---

延伸閱讀：

- [Zeabur 官方資安事件更新](https://status.zeabur.com/incidents)
- [Cloudflare Access：新增自架 Web 應用程式（含 Path 設定）](https://developers.cloudflare.com/cloudflare-one/access-controls/applications/http-apps/)
- [Access 政策動作與條件（Allow / Block / Bypass / Service Auth）](https://developers.cloudflare.com/cloudflare-one/access-controls/policies/)
- [Service tokens：讓機器對機器的呼叫帶憑證通過 Access](https://developers.cloudflare.com/cloudflare-one/access-controls/service-credentials/service-tokens/)
- [Cloudflare 官方文件：驗證 Access JWT（JWKS / RS256 / 金鑰輪替）](https://developers.cloudflare.com/cloudflare-one/identity/authorization-cookie/validating-json/)
```

---

## 寄送提醒

- **必須在 019 之後寄出**：019 結尾已預告本期（「Zero Trust 零信任防護」）。
- **付費分段**：`<!--paywall-->` 切在「深入底層：Zero Trust 邊緣身分驗證與 JWT 信任鏈」之前。
- **示意圖就緒**：包含 2 張 16:9 繁體中文資訊圖（`020-a-zero-trust-edge-barrier.png`、`020-b-tunnel-series-summary.png`，位於 `newsletter/assets/png/`）。寄送前上傳媒體庫並替換文中 TODO URL。
- **工商卡片**：系列完結期包含 Hahow 課程與 Cloudflare Tunnel 加碼單元宣傳。
- **AI 控制 Tunnel 加碼**：工商卡需保留「透過 AI 控制 Cloudflare Tunnel」的課程加碼說明。
- **Zeabur 事件內容**：寄送前重新確認官方事件更新，避免使用過時敘述。
- **Bypass 設定口徑**：Webhook 放行須「另建帶 `/api/webhook/*` path 的 Application 再掛 Bypass 政策」，非在同一應用內加 path 條件（Access Policy 不支援 URL path 條件）。
- **系列完結**：本期為 TUNNEL 系列第 4 篇（完結篇）。
