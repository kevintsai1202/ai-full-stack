# Admin JWT 認證與已發布文章編輯功能 設計文件

- **日期**：2026-08-06
- **專案**：hahow-ai-full-stack / survey-backend
- **分支**：`agent/admin-jwt-article-editing`
- **狀態**：待審閱

## 1. 背景與問題

電子報第 010 期（`nl-20260806-9mxx`）寄送後，發現內文有技術錯誤需要勘誤。查證後確認：**系統沒有任何 API 能修改「已發布且已寄送」的文章內容**。

| 途徑 | 為何不可行 |
| --- | --- |
| `POST /api/admin/campaign/publish` | `slug` 有 UNIQUE 約束，同 slug 必定 400 |
| `DELETE /api/admin/campaigns/{id}/publication` | 有寄送記錄時回 409（`CampaignService` 第 553 行） |
| `POST /api/admin/campaign/send` | 只能建新列，碰不到既有文章 |
| reschedule | 要求 `status='scheduled'`，且會**重寄整批信** |

`setMarkdown()` 全專案僅一個呼叫點（`CampaignService` 第 789 行），位於 reschedule 內。`AdminCampaignController` 第 264 行的註解已明言：唯一修復手段是手動 `UPDATE campaign`。

同時，現行 admin 認證為單一共用金鑰（`AdminKeyGuard`，`X-Admin-Key` 存 sessionStorage），存在三個問題：金鑰不過期、外洩即全開、無法辨識操作者。

### 1.1 需求

1. 在 admin 介面直接修改已發布文章
2. admin 登入改為 JWT，管理者帳號由環境變數指定
3. admin 介面右上角增加登出 icon 與日夜切換

## 2. 決策記錄

| # | 決策 | 理由 |
| --- | --- | --- |
| D1 | admin email 由環境變數 `ADMIN_EMAILS` 指定，不進資料庫 | 目前僅一位管理者，免 migration；換人只需改 Zeabur 變數 |
| D2 | 登入複用讀者 magic-link 收信機制 | 既有 `LoginToken` / `LoginMailService` 可用，無密碼可外洩 |
| D3 | 只開放內容欄位（`subject`／`markdown`／封面／`tags`） | 計費欄位變動會與已扣點紀錄不一致，正是原鎖死設計要保護的對象 |
| D4 | `X-Admin-Key` 與 JWT 並存 | 78 處 `guard.verify()` 與 9 支驗證腳本零改動；金鑰兼作緊急後路 |
| D5 | 複用現有編輯器，列表加「編輯」入口 | 避免兩套 markdown 編輯 UI 漸行漸遠 |
| D6 | 後台網域 `https://admin.springai.world` | 決定 magic-link 連結與 cookie 種在哪個 host |
| D7 | 登入降級：JWT → magic-link → 金鑰後路，金鑰入口常駐 | 常駐可避免「先判定你不是 admin」洩漏白名單成員 |
| D8 | 只加 `updated_at`，不做修改歷史 | 單人管理規模，完整版本史為過度設計 |

## 3. 認證架構

### 3.1 安全前提：magic-link token 必須做用途隔離

現行 `login_token` 表僅有 `token_hash` / `email` / `expires_at` / `used_at` / `created_at`，**沒有用途欄位**。若 admin 直接複用同一張表，將產生提權路徑：

```text
讀者站請求登入 → 信箱收到 token
                    ↓
        同一個 token 打 /api/admin/login/verify
                    ↓
              取得 admin 權限（提權）
```

**對策**：`login_token` 新增 `purpose` 欄位（`reader` / `admin`），簽發與驗證兩端都必須比對。

### 3.2 環境變數

| 變數 | 用途 | 範例 |
| --- | --- | --- |
| `ADMIN_EMAILS` | admin 白名單，逗號分隔 | `admin@example.com` |
| `ADMIN_BASE_URL` | magic-link 連結的目標站 | `https://admin.springai.world` |
| `ADMIN_JWT_SECRET` | admin session 簽章金鑰，與讀者的分開 | 32 字元以上 |
| `ADMIN_JWT_TTL_DAYS` | session 效期 | `7` |
| `ADMIN_API_KEY` | 既有金鑰，保留不動 | 沿用現值 |

`ADMIN_JWT_SECRET` 與讀者的 `READER_JWT_SECRET` 刻意分開：讀者 token 簽發若出現瑕疵，不應蔓延到後台。

`ADMIN_EMAILS` 未設定時，JWT 登入路徑停用（`POST /api/admin/login` 一律不寄信），金鑰路徑照常運作。此為降級預設，確保漏設變數時後台不會完全無法進入。

### 3.3 新增端點

全部掛在 `/api/admin/` 之下，天然通過 `AdminEntryHostFilter` 白名單，不需修改 filter。

| 端點 | 行為 |
| --- | --- |
| `POST /api/admin/login` | 收 email。**僅白名單內的信箱會實際寄出** `purpose=admin` 的 magic-link；但 HTTP 回應**一律 200 且訊息相同**，避免被逐一測試出誰是管理者 |
| `GET /api/admin/login/verify?t=` | 驗 token（`purpose=admin`、未使用、未過期）→ **再次比對白名單** → 種 `admin_session` cookie → 302 導向 `/admin.html` |
| `POST /api/admin/logout` | 清除 `admin_session` cookie |
| `GET /api/admin/me` | 回登入者 email 與登入模式（`jwt` / `key`），供 UI 顯示；未登入回 401 |

verify 端點必須**再次**比對白名單，不可只信任簽發時的檢查：白名單來自環境變數，可能在 token 簽發後、使用前被調整（例如管理者換人）。二次比對確保已撤下的信箱無法用手上的舊連結登入。

### 3.4 AdminKeyGuard 擴充（78 處呼叫點零改動）

`verify(String key)` 的**簽名維持不變**，內部改為兩者擇一通過：

```java
public void verify(String key) {
    if (matchesApiKey(key)) return;                    // 機器：X-Admin-Key
    if (adminSessionValid(currentRequest())) return;   // 瀏覽器：admin_session cookie
    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid admin credential");
}
```

`currentRequest()` 透過 `RequestContextHolder` 取得當前請求，因此不需在 78 個呼叫點多傳參數。這是本方案成立的支點。

### 3.5 Session cookie

沿用 `ReaderSessionService` 既有模式，新增 `AdminSessionService`：

- cookie 名稱 `admin_session`
- `httpOnly` + `SameSite=Lax`
- `Secure` 依 `ADMIN_BASE_URL` 是否為 https 自動判斷
- JWT subject 為 admin email，HS256，效期 `ADMIN_JWT_TTL_DAYS`（預設 7 天）
- 解析失敗（簽章不符／過期／格式錯）一律視為未登入，不拋例外

## 4. 文章編輯

### 4.1 資料層變更（Flyway V24）

```sql
ALTER TABLE login_token ADD COLUMN purpose VARCHAR(16) NOT NULL DEFAULT 'reader';
ALTER TABLE campaign ADD COLUMN updated_at TIMESTAMPTZ;
```

`purpose` 預設 `reader` 讓既有資料維持原語意。

標籤存於獨立的 `campaign_tag` 表，由 `CampaignMetadataService` 管理，**本輪不需變更該表**。

### 4.2 新增端點

```text
PUT /api/admin/campaigns/{id}/content
body: { subject, markdown, coverEmoji, coverMediaId, tags }
```

| 欄位 | 儲存位置 | 可編輯 |
| --- | --- | --- |
| `subject`、`markdown` | `campaign` 表 | 是（本端點新邏輯） |
| `coverEmoji`、`coverMediaId`、`tags` | `campaign` 表與 `campaign_tag` 表 | 是（複用既有服務，見下） |
| `tier`、`credit_cost`、`slug`、`published_at`、寄送統計 | `campaign` 表 | 否（請求帶入時忽略，不報錯） |

封面與標籤**不自行實作**，複用 `CampaignMetadataService` 既有的 public 方法，與 publish 端點（`AdminCampaignController` 第 252、256 行）走同一條路徑：

```java
metadataService.validate(coverEmoji, tags, coverMediaId);  // 先驗證
// … 更新 subject / markdown / updated_at …
metadataService.update(campaignId, coverEmoji, tags, coverMediaId);
```

如此可確保新舊兩條路徑對封面與標籤的驗證規則永遠一致。

### 4.3 端點的安全邊界

此端點**明確不做**下列三件事，實作與測試都須守住：

1. **不重寄信**：不呼叫 `scheduleAll`、不寫 `email_log`、不碰 provider 排程
2. **不改 `bodyHtml`**：那是寄出信件的歷史快照，保留它才能事後查證「當初寄了什麼」
3. **不動解鎖與扣點**：`article_access`、`credit_txn` 完全不碰

### 4.4 生效機制

`ReaderPageController` 第 189 行讀 `campaign.getMarkdown()` 即時渲染，因此更新完成即生效，不需重新發布或重啟服務。已寄出的信件內容不受影響（設計如此）。

## 5. 使用者介面

### 5.1 登入 gate

改造 `admin.html` 既有的 `#gate-key` 區塊，不新增頁面：

```text
進入 admin.html
    ↓
GET /api/admin/me ─── 200 ──▶ 直接進後台
    │
    └── 401 ──▶ 顯示登入 gate
                 [ email ____ ] [寄送登入連結]
                 改用管理金鑰登入 ←（常駐連結）
```

送出後一律顯示「若該信箱為管理員，登入連結已寄出」。金鑰路徑維持現狀（存 `sessionStorage`、API 帶 `X-Admin-Key`）。

### 5.2 右上角工具列

```text
電子報後台          admin@example.com  ☀  ⏻
                                            ↑   ↑
                                     日夜切換  登出
```

- email 來自 `GET /api/admin/me`；金鑰模式顯示「金鑰模式」
- 登出：JWT 模式呼叫 `POST /api/admin/logout`；金鑰模式清除 `sessionStorage`

### 5.3 日夜切換

`admin.html` 既有 CSS 變數（`--bg` / `--fg` / `--muted` / `--border` / `--accent` / `--danger` 等）已足以支撐，只需新增一組覆寫，不動元件樣式：

```css
:root[data-theme="dark"] {
  --bg: #16181c;
  --fg: #e8e8e8;
  --muted: #9aa0a6;
  --border: #2c2f36;
}
```

- 偏好存 `localStorage`；首次進站跟隨 `prefers-color-scheme`
- 切換只改 `documentElement` 的 `data-theme`，即時生效
- `--accent` / `--amber` / `--danger` 在暗底的對比度需實機調校，實作時以 Playwright 截圖比對

### 5.4 文章編輯入口

歷史文章列表每列新增「編輯」，載入現有內容進既有編輯器（D5）。存檔依模式分流：

| 模式 | 存檔呼叫 |
| --- | --- |
| 新建 | `POST /api/admin/campaign/publish` 或 `/send`（現況） |
| 編輯 | `PUT /api/admin/campaigns/{id}/content`（新增） |

編輯模式下 `tier`、解鎖點數、`slug` 顯示為唯讀，避免使用者填了才發現存不進去。

## 6. 測試策略

| 層級 | 重點 |
| --- | --- |
| 認證單元測試 | 金鑰通過、JWT 通過、兩者皆無回 401、過期 JWT 視為未登入 |
| 提權防護測試 | `purpose=reader` 的 token 打 admin verify 必須失敗（對應 3.1） |
| 白名單測試 | 非白名單 email 即使持有效 token 也不得取得 admin session |
| 編輯端點測試 | 可編輯欄位確實更新；`tier`／`credit_cost`／`slug`／`bodyHtml` 保持不變；`email_log` 無新增列 |
| 迴歸測試 | 既有 9 支 `verify-admin*.mjs` 等腳本在改動後仍全數通過（驗證 D4 的零改動承諾） |

## 7. 明確不納入本輪範圍

- 多管理者的後台管理介面（D1 決定用環境變數，需要時再擴充）
- 文章修改歷史與還原（D8）
- 讀者站的日夜模式（本輪僅 `admin.html`）
- 已寄出信件的補寄或更正信

## 8. 風險與緩解

| 風險 | 緩解 |
| --- | --- |
| magic-link token 提權 | `purpose` 欄位隔離 + 驗證端二次比對白名單（3.1） |
| JWT 流程故障導致無法進後台 | 金鑰入口常駐，隨時可用 `X-Admin-Key` 進入（D4／D7） |
| 編輯端點誤觸發重寄 | 端點完全不碰 `email_log` 與排程，並以測試守住（4.3、6） |
| 環境變數未設造成啟動異常 | `ADMIN_EMAILS` 未設時停用 JWT 登入路徑、金鑰照常運作 |
