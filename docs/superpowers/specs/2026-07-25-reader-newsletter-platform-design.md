# 讀者端電子報平台設計（訂閱入口 / 內容 archive / 點數 / 邀請 / 寄送追蹤）

**日期**：2026-07-25
**範圍**：在既有 `survey-backend` 內擴充，長出一個對外的**讀者端產品**，並補強營運端的寄送追蹤與內容製作能力。
**狀態**：設計待審

---

## 1. 背景

目前 `survey-backend` 是**營運端**系統：問卷收集、名單匯入、campaign 群發、HMAC 確認/退訂閉環、`email_log` 稽核、admin 後台。它沒有讀者登入態，唯一的認證是 `AdminKeyGuard`。

本次要新增的是**讀者端**：讀者有身分、有帳戶餘額、能翻閱歷史內容、部分內容需要權限。這條邊界（對內營運工具 vs 對外讀者產品）比「問卷 vs 電子報」深，但兩邊共用同一份最關鍵的可變狀態——**訂閱者名單與同意狀態**——所以不拆專案，在同一服務內以 package 分層切開。

此決定與 [2026-07-24-audience-hub-integration-design.md](2026-07-24-audience-hub-integration-design.md) 的原則一致：
- 原則 3「同意狀態只有一份」→ 讀者端不自帶訂閱狀態，一律讀 `survey_response.consent`。
- 原則 2「一律走 API，不共用資料庫」是針對**跨 Zeabur 專案**（exam 系統）的邊界；讀者端與名單中心屬同一專案，共用交易邊界是刻意選擇（點數扣抵需要交易保證）。

## 2. 範圍

### 交付範圍（7 個功能區）

| # | 功能區 | 內容 |
|---|---|---|
| 1 | 讀者身分 | email + 收信驗證（magic link）登入，JWT 有效期 4 週 |
| 2 | 內容 archive + paywall | 歷史電子報列表與單篇頁；`<!--paywall-->` 以下需權限 |
| 3 | 點數帳本 | 初始贈點、閱讀扣點、後台手動加點，帳本只增不改 |
| 4 | 邀請成長 | 個人邀請碼、歸因、被邀者確認訂閱後發獎勵 |
| 5 | 寄送紀錄與補寄 | 每篇「誰寄成功／誰失敗」，對未成功寄出者補寄 |
| 6 | 開信追蹤 | 追蹤像素、每篇開信名單與開信率報表 |
| 7 | 內容製作升級 | 圖片上傳（MinIO）、raw HTML 引用、VIP 分組寄送 |

### 非目標（明確不做）

- **任何金流**：不賣點數包、不賣訂閱方案。VIP 由 admin 手動授予。
- **點數過期機制**：點數不過期，不做排程回收。
- **送達／退信 webhook**：補寄只依「是否成功寄出」判定，不接 ZSend delivered/bounced 事件。
- **讀者密碼**：只有 magic link，不做密碼登入、不做第三方 OAuth。
- **exam 系統自動同步**（audience-hub Phase 1）：與本設計無關，維持現狀。

## 3. 架構：一個服務、六個 package、單向依賴

```
world.springai.survey/
├─ audience/    名單中心 ── 唯一真相：這個 email 是誰、同意了什麼、從哪來
│                 SurveyResponse, SurveyResponseRepository, RecipientService,
│                 UnsubscribeTokenService, AdminImportController, WelcomeMailService
├─ mail/        寄信唯一出口 ── 所有信都從這裡出去，額度在這裡統一控管
│                 MailSender, ZSendMailSender, NoopMailSender, MailConfig,
│                 MailQuotaService, EmailLog(+Repository), MailTemplate(+Repository), EmailTemplate
├─ media/       圖片託管（新）── S3 client 對 MinIO 上傳與提供公開 URL
├─ newsletter/  營運端 ── 撰文、發送、排程、補寄、開信報表
│                 Campaign(+Repository), CampaignService, AdminCampaignController,
│                 MarkdownRenderer, InviteService, ContentSplitter(新), OpenTracking(新)
├─ reader/      讀者端（新）── 登入、archive、paywall、點數、邀請、個人資料
└─ form/        問卷表單 ── SurveyController, SurveyStats, SurveyRequest/Response
```

**依賴方向（單向，不可逆）**：

```
form ──┐
       ├──▶ audience ◀── mail
reader ┤        ▲          ▲
       │        └──────────┘
newsletter ──▶ mail, media, audience
reader ──▶ audience, mail, newsletter(唯讀 campaign)
```

- `audience`、`mail`、`media` 是下層，**不得** import `reader` / `newsletter` / `form` 的任何型別。
- `reader` 讀取 `newsletter` 的 `Campaign` 作為「文章」，但不呼叫發送邏輯。
- 這層 package 邊界即為日後真要拆服務時的拆解線。

**共用元件**（`AdminKeyGuard`、`ApiExceptionHandler`、`WebConfig`、`SurveyApplication`）留在根 package。

**前端**：延續現有 vanilla HTML + JS + CSS、無建置步驟的做法，與 `index.html` / `admin.html` 一致。新增靜態頁放在 `src/main/resources/static/`。

## 4. 資料模型

### 4.1 新增表

```sql
-- V7：讀者帳戶。以 email 對應名單中心，1:1 但刻意不合併
CREATE TABLE reader (
    id              BIGSERIAL PRIMARY KEY,
    email           TEXT        NOT NULL UNIQUE,       -- 正規化小寫
    tier            TEXT        NOT NULL DEFAULT 'FREE', -- FREE / VIP
    vip_expires_at  TIMESTAMPTZ,                       -- tier=VIP 時的到期時間；NULL 表無限期
    credits         INT         NOT NULL DEFAULT 0,    -- 目前餘額（credit_txn 的物化總和）
    referral_code   TEXT        NOT NULL UNIQUE,       -- 個人邀請碼
    referred_by     BIGINT,                            -- 推薦人 reader.id
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reader_referral_code ON reader (referral_code);

-- V7：點數帳本。只增不改不刪，reader.credits 永遠可由此重算稽核
CREATE TABLE credit_txn (
    id          BIGSERIAL PRIMARY KEY,
    reader_id   BIGINT      NOT NULL REFERENCES reader(id),
    delta       INT         NOT NULL,   -- 正數為加點、負數為扣點
    reason      TEXT        NOT NULL,   -- SIGNUP_GRANT / REFERRAL / READ / ADMIN_GRANT
    campaign_id BIGINT,                 -- reason=READ 時的文章
    note        TEXT,                   -- ADMIN_GRANT 時的說明（如「2026 春季班學員」）
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_credit_txn_reader ON credit_txn (reader_id, created_at DESC);

-- V7：已解鎖文章。UNIQUE 是併發防線，也保證同一篇不重複扣點
CREATE TABLE article_access (
    id          BIGSERIAL PRIMARY KEY,
    reader_id   BIGINT      NOT NULL REFERENCES reader(id),
    campaign_id BIGINT      NOT NULL,
    cost        INT         NOT NULL,   -- 當時實扣點數（0 表 VIP 或 BASIC 免費通行）
    unlocked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_article_access UNIQUE (reader_id, campaign_id)
);

-- V7：magic link 一次性登入 token。不用無狀態 HMAC，因為需要到期與一次性
CREATE TABLE login_token (
    id          BIGSERIAL PRIMARY KEY,
    token_hash  TEXT        NOT NULL UNIQUE,  -- SHA-256(raw token)，明文只出現在信裡
    email       TEXT        NOT NULL,         -- 正規化小寫
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,                  -- 非 NULL 即已用過，不可重用
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_login_token_email ON login_token (email, created_at DESC);

-- V7：開信事件。同一人可多次開信，全部記錄
CREATE TABLE email_open (
    id          BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT      NOT NULL,
    recipient   TEXT        NOT NULL,   -- 正規化小寫
    user_agent  TEXT,
    opened_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_email_open_campaign ON email_open (campaign_id, recipient);

-- V7：媒體檔案索引。實體存 MinIO，此表只記中介資料
CREATE TABLE media_asset (
    id            BIGSERIAL PRIMARY KEY,
    object_key    TEXT        NOT NULL UNIQUE,  -- MinIO 內的 key（含內容 hash）
    content_type  TEXT        NOT NULL,
    size_bytes    BIGINT      NOT NULL,
    original_name TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### 4.2 既有表變更

```sql
-- V8：campaign 擴充為「可在網頁上閱讀的文章」
ALTER TABLE campaign ADD COLUMN tier            TEXT    NOT NULL DEFAULT 'BASIC'; -- BASIC / PREMIUM
ALTER TABLE campaign ADD COLUMN credit_cost     INT     NOT NULL DEFAULT 0;       -- PREMIUM 解鎖所需點數
ALTER TABLE campaign ADD COLUMN slug            TEXT;                             -- 網頁網址片段
ALTER TABLE campaign ADD COLUMN published_at    TIMESTAMPTZ;                      -- 非 NULL 才出現在 archive
ALTER TABLE campaign ADD COLUMN vip_full_in_mail BOOLEAN NOT NULL DEFAULT FALSE;  -- VIP 是否在信件收全文
CREATE UNIQUE INDEX uq_campaign_slug ON campaign (slug) WHERE slug IS NOT NULL;

-- 防呆：PREMIUM 必須有解鎖成本，否則等同免費卻標成付費內容
ALTER TABLE campaign ADD CONSTRAINT ck_campaign_premium_cost
  CHECK (tier <> 'PREMIUM' OR credit_cost > 0);
```

發布 API 亦須在寫入前驗證此規則，回 400 並附明確訊息（不要只靠 DB 約束丟出 500）。

`survey_response` **不變更結構**。讀者自助維護的個人資料寫回它既有的 `name` / `role` / `experience` / `interest` / `answers` 欄位。

### 4.3 `reader` 與 `survey_response` 為何不合併

| | `survey_response`（audience） | `reader`（reader） |
|---|---|---|
| 職責 | 這個 email 是誰、同意了什麼、從哪來 | 帳戶、餘額、VIP、邀請關係 |
| 消費者 | 群發 filter、匯入、退訂、統計 | 登入態、paywall、點數 |
| 產生時機 | 填問卷 / 被匯入 | 首次登入 |

合併會讓名單中心的 schema 綁上讀者端關注點，日後任一邊重寫都得動另一邊。以 email 關聯的成本只是一次 join，而 email 已有索引。

**不變式**：`reader` 存在 ⇏ 已確認訂閱。訂閱狀態一律查 `survey_response.consent = true AND unsubscribed = false`。

## 5. 核心流程

### 5.1 Magic link 登入

```
讀者輸入 email
  → 產生 32 bytes 隨機 token（Base64 URL-safe），存 SHA-256 hash 進 login_token，
    expires_at = now + 15 分鐘
  → 寄登入信（mail package，type='login'），內含 /r/login?t={raw token}
  → 讀者點連結：查 token_hash、驗未過期且 used_at IS NULL
     → 標記 used_at（一次性）
     → upsert reader（首次登入建帳戶：發 SIGNUP_GRANT 300 點、產生 referral_code）
     → 簽發 JWT（sub=reader.id, exp=now+4 週），寫入 httpOnly + Secure + SameSite=Lax cookie
  → 導向原本要看的頁面（token 請求時帶 redirect 參數，僅允許站內相對路徑）
```

**設計理由**：
- **不用既有的無狀態 HMAC**（`UnsubscribeTokenService`）：那個簽章沒有到期也不能失效，對退訂連結是特性，對登入是漏洞。登入必須可到期、可一次性作廢，所以走 DB 表。
- **只存 hash 不存明文**：DB 洩漏時 token 不可用。
- **JWT 放 httpOnly cookie 而非 localStorage**：XSS 無法讀取。因為前端與後端同源（同一個 Spring Boot），不需要處理跨域 cookie。
- **email 不存在名單中也照樣可登入**：登入後導向訂閱確認流程，而不是拒絕登入——降低摩擦，且讓「我明明訂閱了為何進不去」這類客訴消失。

**節流**：同一 email 15 分鐘內最多發 3 次登入信（查 `login_token` 計數），避免被當寄信放大器。

### 5.2 閱讀授權（單一決策點）

`reader/AccessDecisionService.decide(readerOrNull, campaign) → FULL | PARTIAL`

```
未登入                                    → PARTIAL
未確認訂閱（consent=false 或 unsubscribed） → PARTIAL
campaign.tier == BASIC                    → FULL
reader.tier == VIP 且未到期                → FULL（同時補寫 article_access，cost=0）
已存在 article_access                     → FULL
credits >= campaign.credit_cost           → FULL + 扣點
否則                                      → PARTIAL（回傳「還差幾點」與邀請碼）
```

只有這個方法能做授權判斷，controller 只呼叫、不重複判斷。

**扣點的一致性**（`@Transactional`）：
1. `INSERT INTO article_access` — 若違反 UNIQUE 約束，表示同時有另一個請求已解鎖，捕捉後轉為「已解鎖」路徑，不重複扣。
2. `INSERT INTO credit_txn (delta = -cost, reason='READ')`
3. `UPDATE reader SET credits = credits - cost WHERE id = ? AND credits >= ?` — 附帶條件，回傳 0 列即代表併發下餘額不足，整個交易回滾。

與 `CampaignService` 刻意不加 `@Transactional` 的情況相反：那裡有無法回滾的 ZSend 副作用，這裡純本地狀態，所以必須是交易性的。

### 5.3 內容分區（`<!--paywall-->`）

作者在 markdown 中插入單獨一行 `<!--paywall-->`：

```markdown
免費區：勾住讀者的開場。

<!--paywall-->

受限區：需要權限才看得到。
```

`newsletter/ContentSplitter` 在**渲染前**於 markdown 層切分，各段獨立渲染成 HTML：

- **FULL**：免費區 HTML + 受限區 HTML
- **PARTIAL**：只有免費區 HTML + 解鎖提示區塊

**受限區絕不出現在 PARTIAL 的回應中**——不是 CSS 隱藏、不是前端過濾。否則檢視原始碼或爬蟲即可取得全文，整套點數機制失效。

正交性：標籤決定「**哪裡**開始要權限」，`tier` + `credit_cost` 決定「**要什麼**權限」。無標籤 = 全文自由，與 tier 無關。

**SEO**：archive 頁與單篇免費區允許索引（`sitemap.xml` 納入），受限區不輸出。這正是 Google 認可的 metered paywall 做法，不構成 cloaking。

### 5.4 邀請歸因與獎勵

```
讀者在「我的邀請」頁取得連結：{base}/r/subscribe?ref={referral_code}
被邀者填 email 訂閱
  → 訂閱時把 ref 對應的 reader.id 暫存到該 email 的待歸因關係
  → 被邀者收確認信、點擊 /api/survey/confirm（既有 HMAC 閉環）
  → confirm 成功的同一交易內：推薦人 +100 點（credit_txn reason='REFERRAL'）
```

**防刷**：獎勵只在被邀者真的點了自己信箱裡的確認信時發放，填假 email 拿不到點數。此機制沿用既有 confirm 閉環，不需額外防刷設施。第一版**不設邀請人數上限**（只有 admin 能批次匯入名單，讀者無此能力，濫用面很窄）；`credit_txn` 帳本可事後查出異常集中的 REFERRAL 紀錄。

**待歸因關係的儲存**：confirm 時被邀者可能還沒有 `reader` 列（`reader` 只在首次登入才建立），因此歸因必須先存在名單中心側。做法：訂閱寫入 `survey_response` 時，把推薦碼放進 `answers` jsonb 的 `_ref` 鍵（既有欄位，不需新 migration）。

實作注意：

- `answers` 對匯入者為 `NULL`，寫入前需初始化為空物件再放 `_ref`。
- `_ref` 底線前綴用於區別「系統欄位」與問卷答案，問卷統計須排除底線開頭的鍵。
- confirm 成功時讀出 `_ref` → 查推薦人 → 發 `REFERRAL` 獎勵；同時把值搬到 `reader.referred_by`（該讀者首次登入建帳戶時）。
- 冪等：`credit_txn` 以 `(reason='REFERRAL', note=被邀者 email)` 檢查是否已發過，重複 confirm 不重複發獎。

### 5.5 發送：依 tier 分組（兩種內文）

改寫 `CampaignService.send`：

```
渲染兩個版本：fullHtml（含受限區）、foldedHtml（僅免費區 + 「到網頁解鎖」連結）
收件人依 reader.tier 分兩組（無 reader 列者視為 FREE）
  VIP 組  → campaign.vip_full_in_mail ? fullHtml : foldedHtml
  FREE 組 → foldedHtml
各組仍以 ≤100 封 batch 送出，退訂連結與開信像素 per-recipient 個人化
```

第一版 `vip_full_in_mail` 預設 `false`（所有人信件都折疊）。要開放 VIP 信件全文時，發布勾選即可，不需改程式。

**批次效率不受影響**：分組是兩個迴圈，不是 per-recipient 渲染。

### 5.6 補寄

```
補寄對象 = 該 campaign 的原始 filter 條件下的當前可寄名單
          MINUS  email_log 中該 campaign_id 且 status='sent' 的 recipient
```

差集自然涵蓋三種人：寄送失敗者、寄送當時不在名單但之後確認訂閱者、當時額度不足未送到者。

複用 191a692 的邀請信補送模式：逐筆檢查 `email_log` 冪等跳過、`limit` 分批、受 `MailQuotaService.BATCH_CAP` 限制。補寄成功寫入新的 `email_log`（同 `campaign_id`，`type='campaign_resend'`），保留完整軌跡。

### 5.7 開信追蹤

電子報 HTML 尾端插入：

```html
<img src="{publicBaseUrl}/api/track/open?c={campaignId}&e={email}&t={hmac}" width="1" height="1" alt="">
```

`t` 為 HMAC-SHA256(`campaignId:normalizedEmail`)，沿用 `UnsubscribeTokenService` 的簽章模式（新增 `sign(campaignId, email)` 多載或獨立 `OpenTrackingTokenService`）。**必須有簽章**，否則任何人可枚舉 email 偽造開信紀錄污染報表。

端點行為：驗簽 → 寫 `email_open` → 回 1×1 透明 GIF，`Cache-Control: no-store`（否則信箱代理快取會讓後續開信漏記）。驗簽失敗一律照樣回圖片但不記錄（不對外洩漏驗證結果）。

報表（admin）：每篇的寄出數、開信人數、開信率、未開信名單。

**已知限制**（寫入報表 UI 說明，避免誤判）：許多信箱預設封鎖遠端圖片，開信率是**下限**而非實數。因此開信資料**不作為補寄依據**（依 5.6 只看是否成功寄出）。

### 5.8 圖片上傳（MinIO）

- Zeabur 部署 MinIO 服務；bucket `newsletter-media` 設為公開讀取（信件圖片必須可被收件人信箱客戶端從外部匿名抓取）。
- 後端以 AWS SDK for Java v2 的 S3 client 連 MinIO（`endpointOverride` + `pathStyleAccessEnabled(true)`）。
- 上傳流程：admin 上傳 → 計算內容 SHA-256 → `object_key = {hash前16}.{ext}` → 已存在則直接回傳既有 URL（內容去重）→ 寫 `media_asset` → 回傳絕對 URL。
- 限制：僅 `image/png|jpeg|gif|webp`，單檔 ≤ 5 MB。
- **未來遷移 Cloudflare R2**：R2 與 MinIO 同為 S3 API 相容，遷移只需搬物件 + 改 endpoint/credentials/bucket 設定，**不需要程式抽象層**。刻意不做 `MediaStorage` 介面（YAGNI）。

### 5.9 Raw HTML 引用

`MarkdownRenderer` 目前使用 commonmark 預設設定，**已允許 raw HTML 通過**（註解已標明「管理者為可信作者」）。此需求**不需改程式**。

在 spec 中明確記錄此為刻意決定：內容作者僅限 admin（受 `AdminKeyGuard` 保護），不做 HTML 淨化。若日後開放非 admin 投稿，必須先加 sanitizer——這是啟用投稿功能的前置條件。

## 6. 寄信額度：交易信優先權

**風險**：ZSend 有日／月額度上限（`MailQuotaService` 已能動態偵測）。magic link 登入信是**交易信**——讀者當下在等它。若額度被電子報群發吃光，讀者將無法登入，這是產品級故障，而不只是「信晚一點到」。

**對策**：
1. 設定 `app.mail.transactional-reserve`（預設 50 封）。
2. 群發與補寄前檢查：`剩餘額度 - reserve` 才是可用於行銷信的量，不足則縮減批量並在後台顯示原因。
3. 登入信、確認信、歡迎信不受 reserve 限制（它們就是 reserve 的使用者）。
4. 登入信送出失敗時，前端明確告知「登入信寄送失敗，請稍後再試」，不要顯示成功假象。

## 7. 後台新增功能

| 功能 | 說明 | 後端 |
|---|---|---|
| 名單匯入 UI | 貼上 email 清單（一行一筆，可 `email,name`）或上傳 CSV，選 source | 既有 `POST /api/admin/import`，**幾乎不需改** |
| VIP 授予 | 搜尋 email → 設 `tier=VIP` + 到期日（預設 1 年） | 新增 |
| 手動加點 | 搜尋 email → 輸入點數與說明 → `credit_txn reason='ADMIN_GRANT'` | 新增 |
| 批次加點 | 貼 email 清單一次加同樣點數（給整班學員） | 新增 |
| 文章設定 | 發布時設 `tier` / `credit_cost` / `slug` / `published_at` / `vip_full_in_mail` | 擴充 |
| 寄送紀錄 | 每篇的成功／失敗名單，一鍵補寄 | 新增 |
| 開信報表 | 每篇開信率與未開信名單 | 新增 |
| 點數帳本查詢 | 依 email 查交易明細（客訴對帳用） | 新增 |

## 8. 讀者端頁面

| 路徑 | 內容 | 需登入 |
|---|---|---|
| `/r/` | 訂閱入口：價值說明 + email 訂閱表單（吃 `?ref=`） | 否 |
| `/r/archive` | 歷史電子報列表（標題、日期、tier、是否已解鎖） | 否 |
| `/r/news/{slug}` | 單篇：免費區 + （FULL 時）受限區 / （PARTIAL 時）解鎖提示 | 否 |
| `/r/login` | 輸入 email 送登入信 / 承接 magic link 回跳 | 否 |
| `/r/me` | 我的帳戶：餘額、交易明細、VIP 狀態、個人資料編輯 | 是 |
| `/r/invite` | 我的邀請碼與連結、已成功邀請人數與獲得點數 | 是 |

沿用 `index.html` 的視覺語言與 vanilla JS 做法，無建置步驟。

## 9. 參數表（全部由 `application.yml` 或後台設定，不寫死）

| 參數 | 值 |
|---|---|
| 初始贈點 | 300 |
| PREMIUM 單篇預設解鎖點數 | 10 |
| 邀請成功獎勵 | 100 |
| 受限區起點 | 作者以 `<!--paywall-->` 標記 |
| VIP 預設效期 | 1 年 |
| 點數過期 | 不過期 |
| magic link 有效期 | 15 分鐘 |
| JWT 有效期 | 4 週 |
| 登入信節流 | 同 email 15 分鐘內 3 封 |
| 交易信保留額度 | 50 封 |
| 圖片單檔上限 | 5 MB |
| `vip_full_in_mail` 預設 | false |

## 10. 安全考量彙整

| 風險 | 對策 |
|---|---|
| 受限內容被繞過讀取 | server 端切分，PARTIAL 回應完全不含受限 HTML |
| magic link 被重用／永久有效 | DB token，15 分鐘到期 + `used_at` 一次性 + 只存 hash |
| JWT 被 XSS 竊取 | httpOnly + Secure + SameSite=Lax cookie |
| 開信紀錄被偽造 | 追蹤 URL 帶 HMAC 簽章，驗簽失敗不記錄 |
| 併發重複扣點 | `article_access` UNIQUE + `credits >= cost` 條件式 UPDATE |
| 邀請刷點 | 獎勵僅在被邀者點確認信後發放；帳本可稽核 |
| 登入信被當寄信放大器 | 同 email 節流 3 封 / 15 分鐘 |
| 讀者無法登入（額度耗盡） | 交易信保留額度 |
| 開放式 redirect | magic link 的 redirect 僅允許站內相對路徑 |
| raw HTML XSS | 作者僅限 admin；開放投稿前必須加 sanitizer |

## 11. 交付階段（範圍不縮，僅為交付順序）

每階段結束都應可部署並實測。

**階段 A：package 分層**
純搬移 + 調整 import，不改行為。既有測試（`SurveyControllerTest`、`InviteServiceTest`）全綠即為驗證。先做這步，後續每個功能才有正確的落點。

**階段 B：讀者身分 + archive + paywall**（功能區 1、2）
V7/V8 migration（**含 `credit_txn`，因為首次登入即需發 300 點 `SIGNUP_GRANT`**）、magic link、JWT、`ContentSplitter`、`AccessDecisionService`。此階段的授權只走「未登入 / 未確認訂閱 / BASIC / VIP / 已解鎖」五條路徑——**扣點路徑尚未接上，PREMIUM 對非 VIP 一律 PARTIAL**。讀者端 4 個頁面（`/r/`、`/r/archive`、`/r/news/{slug}`、`/r/login`）。此階段結束即為可上線產品。

**階段 C：點數消耗 + 邀請**（功能區 3、4）
接上 `AccessDecisionService` 的扣點路徑（`article_access` + 條件式 UPDATE）、後台手動／批次加點、VIP 授予、邀請碼與 confirm 時發獎、`/r/me`、`/r/invite`。

**階段 D：內容製作升級**（功能區 7）
MinIO 服務與上傳、後台插圖 UI、發送依 tier 分組、`vip_full_in_mail`、交易信保留額度。

**階段 E：寄送追蹤**（功能區 5、6）
補寄（差集 + 冪等）、開信像素與 HMAC、開信報表、名單匯入 UI。

## 12. 測試策略

沿用既有 `spring-boot-starter-test`（無新測試依賴）。

| 目標 | 測試 |
|---|---|
| `ContentSplitter` | 無標籤 / 有標籤 / 多個標籤（取第一個）/ 標籤在首行 / 標籤在末行 |
| `AccessDecisionService` | 六條路徑各一，含 VIP 已到期、餘額剛好等於 cost、餘額少 1 點 |
| 扣點併發 | 同一 reader 同一 campaign 並發兩次解鎖，斷言只扣一次 |
| magic link | 正常 / 過期 / 已用過 / 偽造 token / 節流觸發 |
| 邀請獎勵 | confirm 後推薦人加點；重複 confirm 不重複加點 |
| 補寄差集 | 部分成功部分失敗的 campaign，斷言補寄對象正確且冪等 |
| 開信追蹤 | 驗簽成功記錄 / 驗簽失敗不記錄但仍回圖片 |
| 額度保留 | 剩餘額度低於 reserve 時群發縮減、登入信照送 |
| PARTIAL 不洩漏 | 斷言 PARTIAL 回應的 HTML **不含**受限區任何字串 |

## 13. 未決事項與已接受風險

1. **開信率是下限**：受信箱封鎖遠端圖片影響，僅供趨勢參考，已明確不作為補寄依據。
2. **`reader.credits` 是物化欄位**：與 `credit_txn` 總和理論上恆等，但若出現不一致需要人工重算工具。第一版不做自動對帳排程，改為後台提供「重算此讀者餘額」按鈕。
3. **邀請無上限**：接受風險，理由見 5.4。若帳本出現異常集中的 REFERRAL，再加上限。
4. **MinIO bucket 公開讀取**：圖片 URL 無法撤回（信已寄出，收件人隨時可能重看舊信）。因此不得上傳敏感圖片，此為使用約定而非技術限制。
5. **VIP 到期後**：`tier` 保持 `VIP` 但 `vip_expires_at` 已過 → 授權判斷視為 FREE。不做自動降級排程，也不寄到期通知（第一版）。
