# 階段 B：讀者身分 + 內容 archive + paywall 實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 讓讀者能以 email + 收信驗證登入、瀏覽歷史電子報、並在 `<!--paywall-->` 之後的內容受權限保護；本階段結束即為可上線的產品。

**Architecture:** 新增 `reader` package（讀者端）與根 package 的 `AppSettingService`（可調參數）。授權判斷收斂到單一 `AccessDecisionService`，內容切分在 server 端的 markdown 層完成——未解鎖者收到的 HTML 完全不含受限區。登入以 DB 表存放一次性 magic link token（可到期、可作廢），session 以 JWT 放在 httpOnly cookie。

**Tech Stack:** Java 21、Spring Boot 3.5.0、PostgreSQL + Flyway、jjwt 0.12.6（新增）、commonmark 0.22.0（既有）、vanilla HTML/JS/CSS（無建置步驟）

## Global Constraints

- **前置條件：階段 A 必須已完成。** 本計畫的所有路徑都假設 `audience` / `mail` / `newsletter` / `form` 四個子 package 已存在。
- **JDK 必須是 21。** 每個 Maven 指令前都要 `$env:JAVA_HOME = "D:\java\jdk-21"`（系統預設是 JDK 8，會編譯失敗）。
- **既有資料不可清除**（spec §4.0）。所有 migration 一律 additive；禁止 `DROP` / `TRUNCATE` / 無條件 `DELETE` / `flyway clean`；本機測試一律用獨立資料庫，**不得連線正式 DB**。
- **本階段一律只發布 BASIC 文章**（spec §11）。PREMIUM 與規則頁隨階段 C 上線——先有付費牆而無規則頁與解鎖途徑，讀者會撞到沒有出路的牆。
- **PARTIAL 回應不得含受限區任何字串**（spec §5.3）。不是 CSS 隱藏、不是前端過濾。這是本階段最重要的一條，且有專屬測試。
- **點數參數存 DB 不存 yml**（spec §9.1）。本階段只有「初始贈點」一個參數會被讀取，但 `AppSettingService` 的機制要一次做對。
- **所有程式碼需具備中文註解**（專案 CLAUDE.md）：函式級註解必備，重要變數與物件也要註解。
- **指令相容 PowerShell 7+**（專案 CLAUDE.md）。
- **測試基線：階段 A 結束時為 72 個測試全綠。** 本階段每個 task 結束時，既有測試必須仍全綠。

### 對 spec 的兩處實作細化

1. **migration 檔案切分**：spec §4.1 把所有新表寫在 V7。本階段只建立階段 B 用得到的 5 張表（`app_setting`、`reader`、`credit_txn`、`article_access`、`login_token`），`email_open` 與 `media_asset` 留給階段 E／D 的 V9／V10。`campaign` 的六個新欄位與 `survey_response.last_engaged_at` 一次在 V8 加完（含 `vip_full_in_mail`、`filter_levels` 這兩個後續階段才用的欄位），避免同一張表被反覆 ALTER。
2. **migration 驗證方式**：spec §12 要求「既有資料保全」與「backfill 正確性」測試，但同時規定「無新測試依賴」。自動化這兩項需要 Testcontainers（新依賴），因此改以可重跑的驗證腳本 `scripts/verify-migration.ps1` 實作，沿用專案既有 `scripts/verify-*.mjs` / `*.ps1` 的慣例。**這是刻意的取捨，不是省略**——腳本必須在每次 migration 部署前執行。

---

## File Structure

### 新增：`world.springai.survey.reader`

| 檔案 | 職責 |
|---|---|
| `Reader.java` | 讀者帳戶 entity（`@Table(name = "reader")`） |
| `ReaderRepository.java` | 讀者資料存取 |
| `CreditTxn.java` | 點數帳本 entity（`@Table(name = "credit_txn")`） |
| `CreditTxnRepository.java` | 點數帳本資料存取 |
| `ArticleAccess.java` | 已解鎖紀錄 entity（`@Table(name = "article_access")`） |
| `ArticleAccessRepository.java` | 已解鎖紀錄資料存取 |
| `LoginToken.java` | magic link token entity（`@Table(name = "login_token")`） |
| `LoginTokenRepository.java` | token 資料存取 |
| `LoginTokenService.java` | token 產生／雜湊／驗證／一次性／節流 |
| `ReaderSessionService.java` | JWT 簽發與驗證、cookie 組裝 |
| `ReaderAccountService.java` | 首次登入建帳戶、發初始贈點、產生邀請碼 |
| `LoginMailService.java` | 寄 magic link 登入信 |
| `ContentSplitter.java` | 依 `<!--paywall-->` 切分 markdown |
| `AccessDecisionService.java` | **唯一**的授權決策點 |
| `ReaderController.java` | 讀者端 API（archive／單篇／登入／登出／我的資料） |
| `ArticleView.java` | 單篇文章的回應 DTO |

### 新增：根 package

| 檔案 | 職責 |
|---|---|
| `AppSettingService.java` | 可調參數讀寫（DB + 60 秒快取，儲存時清除） |
| `AppSetting.java` | 參數 entity（`@Table(name = "app_setting")`） |
| `AppSettingRepository.java` | 參數資料存取 |

### 新增：migration 與腳本

| 檔案 | 職責 |
|---|---|
| `src/main/resources/db/migration/V7__create_reader_platform.sql` | 5 張新表 |
| `src/main/resources/db/migration/V8__extend_campaign_and_engagement.sql` | campaign 欄位、`last_engaged_at`、backfill |
| `scripts/verify-migration.ps1` | 既有資料保全與 backfill 正確性的可重跑驗證 |

### 新增：讀者端靜態頁

| 檔案 | 路徑 |
|---|---|
| `src/main/resources/static/reader/index.html` | `/r/` 訂閱入口 |
| `src/main/resources/static/reader/archive.html` | `/r/archive` 歷史列表 |
| `src/main/resources/static/reader/article.html` | `/r/news/{slug}` 單篇 |
| `src/main/resources/static/reader/login.html` | `/r/login` 登入 |
| `src/main/resources/static/reader/reader.css` | 讀者端共用樣式 |

視覺語言沿用既有頁面：`system-ui, "Microsoft JhengHei", sans-serif`、背景 `#f7f8fa`、主文字 `#1a1a2e`、卡片 `border-radius: 12px` + `box-shadow: 0 8px 30px rgba(0,0,0,.08)`、強調色 `#0d9488`（沿用邀請信按鈕色）。

### 修改：既有檔

| 檔案 | 變更 |
|---|---|
| `pom.xml` | 新增 jjwt 三個依賴 |
| `application.yml` | 新增 `app.reader.*` 部署設定 |
| `newsletter/Campaign.java` | 補 6 個新欄位的 field 與 getter／setter |
| `newsletter/CampaignRepository.java` | 新增 archive 查詢與 slug 查詢 |
| `audience/SurveyResponse.java` | 補 `lastEngagedAt` 欄位 |
| `WebConfig.java` | `/r/**` 路徑對應到 `static/reader/` |

### 依賴方向檢查

`reader` 是上層，可依賴 `audience`（查 consent）、`mail`（寄登入信）、`newsletter`（唯讀 Campaign）與根 package。階段 A 的 `PackageDependencyTest` 會自動涵蓋新的 `reader` package，無需修改該測試。

---

## Task 1: 新增 jjwt 依賴與讀者端部署設定

**Files:**
- Modify: `survey-backend/pom.xml`
- Modify: `survey-backend/src/main/resources/application.yml`

**Interfaces:**
- Consumes: 無
- Produces: `io.jsonwebtoken.Jwts` 可用；設定鍵 `app.reader.jwt-secret`、`app.reader.jwt-ttl-days`、`app.reader.login-token-ttl-minutes`、`app.reader.login-throttle-count`、`app.reader.login-throttle-minutes`、`app.mail.transactional-reserve`

- [ ] **Step 1: 加入 jjwt 依賴**

在 `pom.xml` 的 `<dependencies>` 內，commonmark 那段之後加入：

```xml
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-api</artifactId>
      <version>0.12.6</version>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-impl</artifactId>
      <version>0.12.6</version>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-jackson</artifactId>
      <version>0.12.6</version>
      <scope>runtime</scope>
    </dependency>
```

- [ ] **Step 2: 確認依賴可下載（需要網路）**

```powershell
cd d:\GitHub\hahow-ai-full-stack\survey-backend
$env:JAVA_HOME = "D:\java\jdk-21"
mvn dependency:resolve -q
```

Expected: `BUILD SUCCESS`，無 `Could not resolve dependencies`。

若下載失敗（無網路或倉庫不可達），**停止並回報**——不要改成自製 token 繞過，那是偏離 spec §5.1 的架構決定，需要先取得確認。

- [ ] **Step 3: 新增讀者端設定**

在 `application.yml` 的 `app:` 區塊下，`mail:` 之前插入：

```yaml
  # 讀者端設定（部署設定，非產品參數；點數類參數存 DB 見 app_setting 表）
  reader:
    # JWT 簽章秘鑰（線上務必換成 32 字元以上的強隨機字串）
    jwt-secret: ${READER_JWT_SECRET:dev-reader-jwt-secret-change-me-32chars}
    # 登入態有效天數
    jwt-ttl-days: ${READER_JWT_TTL_DAYS:28}
    # magic link 連結有效分鐘數
    login-token-ttl-minutes: ${READER_LOGIN_TOKEN_TTL_MINUTES:15}
    # 登入信節流：同一 email 在 login-throttle-minutes 內最多寄幾封
    login-throttle-count: ${READER_LOGIN_THROTTLE_COUNT:3}
    login-throttle-minutes: ${READER_LOGIN_THROTTLE_MINUTES:15}
```

並在既有的 `mail:` 區塊末尾（`fallback-quota` 之後）加入：

```yaml
    # 保留給交易信（登入／確認／歡迎）的額度，行銷群發不得吃掉這部分；
    # 群發把額度用光會導致讀者無法登入，那是產品故障而非延遲
    transactional-reserve: ${MAIL_TRANSACTIONAL_RESERVE:50}
```

- [ ] **Step 4: 確認既有測試仍全綠**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test
```

Expected: `Tests run: 72, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Commit**

```powershell
git add pom.xml src/main/resources/application.yml
git commit -m @'
build(survey-backend): 新增 jjwt 依賴與讀者端部署設定

- jjwt 0.12.6（api + impl/runtime + jackson/runtime）供讀者 session JWT
- app.reader.*：JWT 秘鑰與效期、magic link 效期、登入信節流
- app.mail.transactional-reserve：保留給交易信的額度，
  避免行銷群發吃光額度導致讀者無法登入

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
'@
```

---

## Task 2: V7／V8 migration 與驗證腳本

**Files:**
- Create: `survey-backend/src/main/resources/db/migration/V7__create_reader_platform.sql`
- Create: `survey-backend/src/main/resources/db/migration/V8__extend_campaign_and_engagement.sql`
- Create: `survey-backend/scripts/verify-migration.ps1`

**Interfaces:**
- Consumes: 無
- Produces: 資料表 `app_setting`、`reader`、`credit_txn`、`article_access`、`login_token`；`campaign` 新增 `tier` / `credit_cost` / `slug` / `published_at` / `vip_full_in_mail` / `filter_levels`；`survey_response` 新增 `last_engaged_at`

> **這是本專案第一次在有既有資料的 DB 上做 migration。** 每一行 SQL 都必須是 additive。

- [ ] **Step 1: 建立 V7**

Create `survey-backend/src/main/resources/db/migration/V7__create_reader_platform.sql`:

```sql
-- 讀者端平台核心資料表。全部為新增，不觸碰任何既有表的既有資料。
-- 注意：email_open（階段 E）與 media_asset（階段 D）刻意不在此建立，
-- 各階段自帶 migration，避免建立當下用不到的表。

-- 可調參數：點數與門檻類參數存 DB，讓後台改完立即生效（不必重新部署）
CREATE TABLE app_setting (
    setting_key TEXT        PRIMARY KEY,
    value       TEXT        NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 讀者帳戶：以 email 對應名單中心，1:1 但刻意不與 survey_response 合併。
-- survey_response 管「同意與來源」，reader 管「帳戶與點數」。
-- 不變式：reader 存在不代表已確認訂閱，訂閱狀態一律查 survey_response。
CREATE TABLE reader (
    id             BIGSERIAL   PRIMARY KEY,
    email          TEXT        NOT NULL UNIQUE,          -- 一律正規化為小寫
    tier           TEXT        NOT NULL DEFAULT 'FREE',  -- FREE / VIP
    vip_expires_at TIMESTAMPTZ,                          -- NULL 表無限期（僅 tier=VIP 時有意義）
    credits        INT         NOT NULL DEFAULT 0,       -- 目前餘額，為 credit_txn 的物化總和
    referral_code  TEXT        NOT NULL UNIQUE,          -- 個人邀請碼
    referred_by    BIGINT,                               -- 推薦人 reader.id
    last_login_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reader_referral_code ON reader (referral_code);

-- 點數帳本：只增不改不刪，reader.credits 永遠可由此重算稽核
CREATE TABLE credit_txn (
    id          BIGSERIAL   PRIMARY KEY,
    reader_id   BIGINT      NOT NULL REFERENCES reader(id),
    delta       INT         NOT NULL,   -- 正數加點、負數扣點
    reason      TEXT        NOT NULL,   -- SIGNUP_GRANT / REFERRAL / READ / ADMIN_GRANT
    campaign_id BIGINT,                 -- reason=READ 時的文章
    note        TEXT,                   -- ADMIN_GRANT 時的說明
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_credit_txn_reader ON credit_txn (reader_id, created_at DESC);

-- 已解鎖文章。UNIQUE 同時是併發防線與「同一篇不重複扣點」的保證
CREATE TABLE article_access (
    id          BIGSERIAL   PRIMARY KEY,
    reader_id   BIGINT      NOT NULL REFERENCES reader(id),
    campaign_id BIGINT      NOT NULL,
    cost        INT         NOT NULL,   -- 當時實扣點數（0 表 VIP 或 BASIC 免費通行）
    unlocked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_article_access UNIQUE (reader_id, campaign_id)
);

-- magic link 一次性登入 token。刻意不用無狀態 HMAC：登入必須可到期、可作廢，
-- 而 UnsubscribeTokenService 的簽章沒有到期概念（那對退訂連結是特性）。
-- 只存 SHA-256 雜湊，明文 token 僅出現在寄出的信裡。
CREATE TABLE login_token (
    id         BIGSERIAL   PRIMARY KEY,
    token_hash TEXT        NOT NULL UNIQUE,
    email      TEXT        NOT NULL,   -- 一律正規化為小寫
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,            -- 非 NULL 即已使用，不可重用
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_login_token_email ON login_token (email, created_at DESC);
```

- [ ] **Step 2: 建立 V8**

Create `survey-backend/src/main/resources/db/migration/V8__extend_campaign_and_engagement.sql`:

```sql
-- 擴充 campaign 為「可在網頁上閱讀的文章」，並為名單加上參與度時間戳。
-- 全部 additive：新欄位皆可為 NULL 或帶 DEFAULT，既有列無需改寫即維持有效。

-- 內容分級與網頁閱讀所需欄位
ALTER TABLE campaign ADD COLUMN tier             TEXT    NOT NULL DEFAULT 'BASIC'; -- BASIC / PREMIUM
ALTER TABLE campaign ADD COLUMN credit_cost      INT     NOT NULL DEFAULT 0;       -- PREMIUM 解鎖所需點數
ALTER TABLE campaign ADD COLUMN slug             TEXT;                             -- 網頁網址片段
ALTER TABLE campaign ADD COLUMN published_at     TIMESTAMPTZ;                      -- 非 NULL 才出現在 archive
ALTER TABLE campaign ADD COLUMN vip_full_in_mail BOOLEAN NOT NULL DEFAULT FALSE;   -- VIP 是否在信件收全文（階段 D 才使用）
ALTER TABLE campaign ADD COLUMN filter_levels    TEXT    NOT NULL DEFAULT 'active';-- 寄送的參與度級別（階段 F 才使用）

-- slug 唯一（允許多筆 NULL：尚未設定 slug 的舊 campaign 不進 archive）
CREATE UNIQUE INDEX uq_campaign_slug ON campaign (slug) WHERE slug IS NOT NULL;

-- 防呆：標為 PREMIUM 卻沒有解鎖成本，等同免費卻顯示為付費內容
ALTER TABLE campaign ADD CONSTRAINT ck_campaign_premium_cost
  CHECK (tier <> 'PREMIUM' OR credit_cost > 0);

-- 參與度時間戳。放在名單中心而非 reader，因為從未登入過的殭屍地址沒有 reader 列，
-- 但正是最需要被 sunset 判定的對象（階段 F）。
ALTER TABLE survey_response ADD COLUMN last_engaged_at TIMESTAMPTZ;
CREATE INDEX idx_survey_response_engaged ON survey_response (last_engaged_at);

-- 【必要 backfill】既有訂閱者在此之前沒有參與度追蹤，last_engaged_at 全為 NULL。
-- 若不回填，他們的「已寄期數」可能早已超過階段 F 的淘汰門檻（12 期），
-- 依分級規則會被判為 sunset —— 功能上線當天所有老訂閱者整批停收電子報。
-- 資料沒少但收不到信，且要到下次發送才顯現，極難察覺。
-- 以 migration 執行時間作為起算點，讓既有訂閱者一律從 active 開始。
UPDATE survey_response
   SET last_engaged_at = now()
 WHERE consent = TRUE AND unsubscribed = FALSE;

-- 未確認訂閱者（如已匯入的 exam 名單）刻意不回填，保持 NULL：
-- 他們從未被寄過電子報（邀請信 type='invite' 不計入已寄期數），
-- 「已寄期數 < 沉睡門檻」條件成立，仍會被判為 active。回填反而造出假的參與紀錄。

-- 參數初始值（spec §9.2）。ON CONFLICT 讓此 migration 可安全重跑。
INSERT INTO app_setting (setting_key, value) VALUES
  ('credit.signup_grant',        '300'),
  ('credit.premium_cost',        '10'),
  ('credit.referral_reward',     '100'),
  ('vip.default_days',           '365'),
  ('engagement.dormant_after_campaigns', '6'),
  ('engagement.sunset_after_campaigns',  '12'),
  ('engagement.active_days',     '90'),
  ('engagement.sunset_days',     '365')
ON CONFLICT (setting_key) DO NOTHING;
```

- [ ] **Step 3: 建立驗證腳本**

Create `survey-backend/scripts/verify-migration.ps1`:

```powershell
# V7／V8 migration 的既有資料保全與 backfill 正確性驗證。
# 用途：每次要把 migration 套用到有既有資料的資料庫之前，先在該庫的複本上跑一次。
# 為何是腳本而非 JUnit 測試：自動化需要 Testcontainers（新測試依賴），
# 而 spec §12 規定不引入新測試依賴。此腳本可重跑，沿用專案 scripts/verify-* 慣例。
#
# 用法：
#   .\scripts\verify-migration.ps1 -Database survey_copy
#   .\scripts\verify-migration.ps1 -Database survey_copy -PsqlPath "C:\Program Files\PostgreSQL\16\bin\psql.exe"

param(
    [Parameter(Mandatory = $true)][string]$Database,
    [string]$DbHost = "127.0.0.1",
    [int]$Port = 5432,
    [string]$User = "postgres",
    [string]$PsqlPath = "psql"
)

$ErrorActionPreference = "Stop"

# 執行一句 SQL 並回傳單一純量值
function Invoke-Scalar([string]$sql) {
    $result = & $PsqlPath -h $DbHost -p $Port -U $User -d $Database -t -A -c $sql
    if ($LASTEXITCODE -ne 0) { throw "psql 執行失敗：$sql" }
    return $result.Trim()
}

Write-Host "=== migration 前狀態 ===" -ForegroundColor Cyan
$beforeTotal      = Invoke-Scalar "SELECT count(*) FROM survey_response;"
$beforeConsented  = Invoke-Scalar "SELECT count(*) FROM survey_response WHERE consent = TRUE AND unsubscribed = FALSE;"
$beforeChecksum   = Invoke-Scalar "SELECT md5(string_agg(email || ':' || consent || ':' || unsubscribed, ',' ORDER BY id)) FROM survey_response;"
Write-Host "survey_response 總筆數：$beforeTotal"
Write-Host "已確認訂閱筆數：      $beforeConsented"
Write-Host "email/consent/unsubscribed 檢查碼：$beforeChecksum"

Write-Host "`n請在此時對資料庫 [$Database] 套用 V7/V8 migration，完成後按 Enter 繼續..." -ForegroundColor Yellow
Read-Host

Write-Host "`n=== migration 後驗證 ===" -ForegroundColor Cyan
$failures = @()

# 1. 既有資料保全：筆數與關鍵欄位逐列不變
$afterTotal    = Invoke-Scalar "SELECT count(*) FROM survey_response;"
$afterChecksum = Invoke-Scalar "SELECT md5(string_agg(email || ':' || consent || ':' || unsubscribed, ',' ORDER BY id)) FROM survey_response;"
if ($afterTotal -ne $beforeTotal)       { $failures += "survey_response 筆數改變：$beforeTotal -> $afterTotal" }
if ($afterChecksum -ne $beforeChecksum) { $failures += "email/consent/unsubscribed 有變動（檢查碼不符）" }

# 2. backfill：已確認訂閱者全部有 last_engaged_at
$backfilled = Invoke-Scalar "SELECT count(*) FROM survey_response WHERE consent = TRUE AND unsubscribed = FALSE AND last_engaged_at IS NOT NULL;"
if ($backfilled -ne $beforeConsented) { $failures += "backfill 不完整：應 $beforeConsented 筆，實際 $backfilled 筆" }

# 3. 未確認者刻意保持 NULL
$pendingWithStamp = Invoke-Scalar "SELECT count(*) FROM survey_response WHERE consent = FALSE AND last_engaged_at IS NOT NULL;"
if ($pendingWithStamp -ne "0") { $failures += "未確認訂閱者被誤回填 $pendingWithStamp 筆" }

# 4. 新表存在且為空
foreach ($t in "app_setting", "reader", "credit_txn", "article_access", "login_token") {
    $exists = Invoke-Scalar "SELECT count(*) FROM information_schema.tables WHERE table_name = '$t';"
    if ($exists -ne "1") { $failures += "資料表 $t 未建立" }
}
$settingCount = Invoke-Scalar "SELECT count(*) FROM app_setting;"
if ($settingCount -ne "8") { $failures += "app_setting 初始值應為 8 筆，實際 $settingCount 筆" }

# 5. campaign 新欄位存在，既有列取得預設值
foreach ($c in "tier", "credit_cost", "slug", "published_at", "vip_full_in_mail", "filter_levels") {
    $exists = Invoke-Scalar "SELECT count(*) FROM information_schema.columns WHERE table_name = 'campaign' AND column_name = '$c';"
    if ($exists -ne "1") { $failures += "campaign.$c 未建立" }
}
$badTier = Invoke-Scalar "SELECT count(*) FROM campaign WHERE tier IS NULL OR tier <> 'BASIC';"
if ($badTier -ne "0") { $failures += "既有 campaign 的 tier 未全部預設為 BASIC（$badTier 筆異常）" }

Write-Host ""
if ($failures.Count -eq 0) {
    Write-Host "全部驗證通過。" -ForegroundColor Green
    exit 0
} else {
    Write-Host "驗證失敗：" -ForegroundColor Red
    $failures | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
    exit 1
}
```

- [ ] **Step 4: 在本機獨立資料庫上實測 migration**

**絕對不要對正式資料庫執行。** 建立一個帶有代表性既有資料的本機庫：

```powershell
$env:PGPASSWORD = "password"
psql -h 127.0.0.1 -U postgres -c "DROP DATABASE IF EXISTS survey_mig_test;"
psql -h 127.0.0.1 -U postgres -c "CREATE DATABASE survey_mig_test;"
```

先只套用 V1–V6（模擬正式庫現況），再塞入測試資料：

```powershell
cd d:\GitHub\hahow-ai-full-stack\survey-backend
$env:JAVA_HOME = "D:\java\jdk-21"
mvn flyway:migrate "-Dflyway.url=jdbc:postgresql://127.0.0.1:5432/survey_mig_test" "-Dflyway.user=postgres" "-Dflyway.password=password" "-Dflyway.target=6" "-Dflyway.locations=filesystem:src/main/resources/db/migration"
```

塞入三種代表性資料（已確認訂閱者、未確認匯入者、已退訂者）與一筆既有 campaign：

```powershell
psql -h 127.0.0.1 -U postgres -d survey_mig_test -c @'
INSERT INTO survey_response (email, consent, unsubscribed, source) VALUES
  ('confirmed@example.com', TRUE,  FALSE, 'survey_form'),
  ('pending@example.com',   FALSE, FALSE, 'exam'),
  ('gone@example.com',      TRUE,  TRUE,  'survey_form');
INSERT INTO campaign (subject, markdown, mode, recipient_count, accepted_count, failed_count, status)
  VALUES ('既有電子報', '# 內容', 'now', 1, 1, 0, 'sent');
'@
```

- [ ] **Step 5: 跑驗證腳本（前半）→ 套用 V7/V8 → 驗證（後半）**

```powershell
.\scripts\verify-migration.ps1 -Database survey_mig_test
```

腳本會印出 migration 前狀態並暫停。**在另一個終端**套用 V7/V8：

```powershell
cd d:\GitHub\hahow-ai-full-stack\survey-backend
$env:JAVA_HOME = "D:\java\jdk-21"
mvn flyway:migrate "-Dflyway.url=jdbc:postgresql://127.0.0.1:5432/survey_mig_test" "-Dflyway.user=postgres" "-Dflyway.password=password" "-Dflyway.locations=filesystem:src/main/resources/db/migration"
```

回到第一個終端按 Enter。

Expected: `全部驗證通過。`（綠字），exit code 0

若任何一項失敗，修 SQL 後把測試庫 drop 重建再跑一次——不要在已套用的庫上反覆試，Flyway 的版本紀錄會擋住。

- [ ] **Step 6: 確認既有測試仍全綠**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test
```

Expected: `Tests run: 72, Failures: 0, Errors: 0, Skipped: 0`

（此時 entity 還沒補上新欄位，但 `ddl-auto: validate` 只在啟動時驗證，單元測試不連 DB，所以不受影響。）

- [ ] **Step 7: Commit**

```powershell
git add src/main/resources/db/migration/V7__create_reader_platform.sql src/main/resources/db/migration/V8__extend_campaign_and_engagement.sql scripts/verify-migration.ps1
git commit -m @'
feat(reader): V7/V8 migration —— 讀者端資料表與 campaign 擴充

V7（5 張新表）：app_setting、reader、credit_txn、article_access、login_token
- article_access 的 UNIQUE(reader_id, campaign_id) 同時是併發防線
- login_token 走 DB 而非無狀態 HMAC：登入需可到期、可作廢，且只存雜湊

V8（additive 擴充）：
- campaign 加 tier/credit_cost/slug/published_at/vip_full_in_mail/filter_levels
- CHECK 約束防止 PREMIUM 卻 credit_cost=0
- survey_response 加 last_engaged_at
- 【關鍵 backfill】已確認訂閱者回填 now()，否則階段 F 的分級會把
  老訂閱者（已寄多期 + last_engaged_at 為 NULL）整批判為 sunset 而停寄
- 未確認匯入者刻意不回填（已寄期數為 0，本就判為 active）
- app_setting 初始值 8 筆，ON CONFLICT DO NOTHING 可安全重跑

驗證：scripts/verify-migration.ps1 檢查既有資料逐列不變、backfill
完整性、新表與新欄位齊備。已在獨立測試庫實測通過。
（不用 Testcontainers 是因 spec §12 規定無新測試依賴。）

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
'@
```

---

## Task 3: AppSettingService（可調參數）

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/AppSetting.java`
- Create: `survey-backend/src/main/java/world/springai/survey/AppSettingRepository.java`
- Create: `survey-backend/src/main/java/world/springai/survey/AppSettingService.java`
- Test: `survey-backend/src/test/java/world/springai/survey/AppSettingServiceTest.java`

**Interfaces:**
- Consumes: `app_setting` 表（Task 2）
- Produces:
  - `AppSettingService.getInt(String key, int defaultValue) → int`
  - `AppSettingService.set(String key, String value) → void`（會清除快取）
  - 常數 `AppSettingService.CREDIT_SIGNUP_GRANT = "credit.signup_grant"`、`CREDIT_PREMIUM_COST = "credit.premium_cost"`、`CREDIT_REFERRAL_REWARD = "credit.referral_reward"`、`VIP_DEFAULT_DAYS = "vip.default_days"`

- [ ] **Step 1: 寫失敗的測試**

Create `survey-backend/src/test/java/world/springai/survey/AppSettingServiceTest.java`:

```java
package world.springai.survey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** AppSettingService 行為測試：查無回預設、快取避免重複查詢、寫入後立即生效 */
class AppSettingServiceTest {

    private AppSettingRepository repository;
    private AppSettingService service;

    @BeforeEach
    void setUp() {
        repository = mock(AppSettingRepository.class);
        service = new AppSettingService(repository);
    }

    /** 查無此 key 時回傳呼叫端給的預設值（故新增參數不需 data migration） */
    @Test
    void missingKeyFallsBackToDefault() {
        when(repository.findById("credit.signup_grant")).thenReturn(Optional.empty());
        assertEquals(300, service.getInt(AppSettingService.CREDIT_SIGNUP_GRANT, 300));
    }

    /** 有值時回傳 DB 的值 */
    @Test
    void storedValueOverridesDefault() {
        when(repository.findById("credit.premium_cost"))
            .thenReturn(Optional.of(new AppSetting("credit.premium_cost", "50")));
        assertEquals(50, service.getInt(AppSettingService.CREDIT_PREMIUM_COST, 10));
    }

    /** 值不是數字時退回預設，不得讓壞資料炸掉授權判斷 */
    @Test
    void nonNumericValueFallsBackToDefault() {
        when(repository.findById("credit.premium_cost"))
            .thenReturn(Optional.of(new AppSetting("credit.premium_cost", "abc")));
        assertEquals(10, service.getInt(AppSettingService.CREDIT_PREMIUM_COST, 10));
    }

    /** 同一 key 連續讀取只查一次 DB（授權判斷每次都會讀，不能每次打 DB） */
    @Test
    void repeatedReadsUseCache() {
        when(repository.findById("credit.premium_cost"))
            .thenReturn(Optional.of(new AppSetting("credit.premium_cost", "10")));

        service.getInt(AppSettingService.CREDIT_PREMIUM_COST, 10);
        service.getInt(AppSettingService.CREDIT_PREMIUM_COST, 10);
        service.getInt(AppSettingService.CREDIT_PREMIUM_COST, 10);

        verify(repository, times(1)).findById("credit.premium_cost");
    }

    /** 寫入後必須立即生效：儲存會清掉該 key 的快取（後台改完立即生效的硬要求） */
    @Test
    void setInvalidatesCacheSoChangeTakesEffectImmediately() {
        when(repository.findById("credit.premium_cost"))
            .thenReturn(Optional.of(new AppSetting("credit.premium_cost", "10")));
        when(repository.save(any(AppSetting.class))).thenAnswer(i -> i.getArgument(0));
        assertEquals(10, service.getInt(AppSettingService.CREDIT_PREMIUM_COST, 10));

        service.set(AppSettingService.CREDIT_PREMIUM_COST, "50");
        when(repository.findById("credit.premium_cost"))
            .thenReturn(Optional.of(new AppSetting("credit.premium_cost", "50")));

        assertEquals(50, service.getInt(AppSettingService.CREDIT_PREMIUM_COST, 10));
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test -Dtest=AppSettingServiceTest
```

Expected: 編譯失敗，訊息含 `cannot find symbol: class AppSettingRepository`（或 `AppSetting` / `AppSettingService`）

- [ ] **Step 3: 建立 entity**

Create `survey-backend/src/main/java/world/springai/survey/AppSetting.java`:

```java
package world.springai.survey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** 可調參數實體，對應資料表 app_setting；點數與門檻類參數存 DB 以便後台改完立即生效 */
@Entity
@Table(name = "app_setting")
public class AppSetting {

    /** 參數鍵，如 credit.signup_grant */
    @Id
    @Column(name = "setting_key")
    private String settingKey;

    /** 參數值，一律以字串存放，由讀取端依需要轉型 */
    @Column(nullable = false)
    private String value;

    /** 最後更新時間，由資料庫維護 */
    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    /** JPA 需要的無參數建構子 */
    protected AppSetting() {
    }

    /** 以鍵值建立一筆參數 */
    public AppSetting(String settingKey, String value) {
        this.settingKey = settingKey;
        this.value = value;
    }

    public String getSettingKey() { return settingKey; }
    public void setSettingKey(String settingKey) { this.settingKey = settingKey; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
```

- [ ] **Step 4: 建立 repository**

Create `survey-backend/src/main/java/world/springai/survey/AppSettingRepository.java`:

```java
package world.springai.survey;

import org.springframework.data.jpa.repository.JpaRepository;

/** 可調參數資料存取層；主鍵即參數鍵，故 findById 就是依鍵查詢 */
public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
```

- [ ] **Step 5: 建立 service**

Create `survey-backend/src/main/java/world/springai/survey/AppSettingService.java`:

```java
package world.springai.survey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 可調參數讀寫：點數與門檻類參數存資料庫，讓後台改完立即生效、不必重新部署。
 *
 * <p>設計理由（spec §9.1）：這些數字第一版是估的，要靠上線後的真實行為迭代。
 * 放進 application.yml 意味著每次調整都要重新部署——實務結果是不會有人去調，
 * 參數就永遠停在初版猜測值。</p>
 *
 * <p>授權判斷每次都會讀參數，因此加 60 秒快取（比照 MailQuotaService 的既有做法）；
 * 寫入時主動清除該鍵的快取，做到「改完立即生效」。</p>
 */
@Service
public class AppSettingService {

    private static final Logger log = LoggerFactory.getLogger(AppSettingService.class);

    /** 首次登入的初始贈點 */
    public static final String CREDIT_SIGNUP_GRANT = "credit.signup_grant";
    /** PREMIUM 文章的預設解鎖點數 */
    public static final String CREDIT_PREMIUM_COST = "credit.premium_cost";
    /** 邀請成功的獎勵點數 */
    public static final String CREDIT_REFERRAL_REWARD = "credit.referral_reward";
    /** VIP 預設效期天數 */
    public static final String VIP_DEFAULT_DAYS = "vip.default_days";

    /** 快取存活時間：授權判斷頻繁讀取，但參數變動極少 */
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final AppSettingRepository repository;

    /** 快取項目：值與寫入時間 */
    private record Cached(String value, Instant at) {}

    /** 以參數鍵為 key 的快取 */
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    /** 注入參數資料存取層 */
    public AppSettingService(AppSettingRepository repository) {
        this.repository = repository;
    }

    /**
     * 讀取整數參數；查無此鍵或值無法解析為整數時回傳呼叫端給的預設值。
     *
     * <p>回傳預設值而非拋例外是刻意的：新增參數不需要 data migration，
     * 而壞資料不應該讓授權判斷整個掛掉。</p>
     */
    public int getInt(String key, int defaultValue) {
        String raw = get(key);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("參數 {} 的值無法解析為整數（{}），改用預設值 {}", key, raw, defaultValue);
            return defaultValue;
        }
    }

    /** 讀取字串參數；查無此鍵回 null。快取命中則不查資料庫 */
    public String get(String key) {
        Cached cached = cache.get(key);
        if (cached != null && Duration.between(cached.at(), Instant.now()).compareTo(CACHE_TTL) < 0) {
            return cached.value();
        }
        String value = repository.findById(key).map(AppSetting::getValue).orElse(null);
        cache.put(key, new Cached(value, Instant.now()));
        return value;
    }

    /** 寫入參數並清除該鍵的快取，讓變更立即生效 */
    public void set(String key, String value) {
        Optional<AppSetting> existing = repository.findById(key);
        AppSetting entity = existing.orElseGet(() -> new AppSetting(key, value));
        entity.setValue(value);
        repository.save(entity);
        cache.remove(key); // 立即生效的關鍵：不等快取自然過期
        log.info("參數 {} 已更新為 {}", key, value);
    }

    /** 清除全部快取（測試與後台批次更新後使用） */
    public void clearCache() {
        cache.clear();
    }
}
```

- [ ] **Step 6: 跑測試確認通過**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test -Dtest=AppSettingServiceTest
```

Expected: `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 7: 跑全部測試**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test
```

Expected: `Tests run: 77, Failures: 0, Errors: 0, Skipped: 0`（72 + 5）

- [ ] **Step 8: Commit**

```powershell
git add src/main/java/world/springai/survey/AppSetting.java src/main/java/world/springai/survey/AppSettingRepository.java src/main/java/world/springai/survey/AppSettingService.java src/test/java/world/springai/survey/AppSettingServiceTest.java
git commit -m @'
feat(reader): AppSettingService —— 可調參數存 DB 且改完立即生效

- 點數與門檻參數存 app_setting 表（spec §9.1），非 application.yml，
  因為第一版數字是估的、要靠上線後行為迭代；放 yml 等於每次調參
  都要重新部署，實務上就不會有人去調
- 60 秒快取（比照 MailQuotaService），set() 主動清除該鍵快取
- 查無鍵或值非數字時回傳呼叫端預設值：新增參數不需 data migration，
  壞資料也不會讓授權判斷掛掉
- 放在根 package：跨越 reader（點數）與 audience（參與度門檻）兩個領域

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
'@
```

---

## Task 4: ContentSplitter（`<!--paywall-->` 內容切分）

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/reader/ContentSplitter.java`
- Test: `survey-backend/src/test/java/world/springai/survey/reader/ContentSplitterTest.java`

**Interfaces:**
- Consumes: 無（純函式）
- Produces:
  - `ContentSplitter.Split` record：`String freeMarkdown()`、`String gatedMarkdown()`、`boolean hasGate()`
  - `ContentSplitter.split(String markdown) → Split`
  - 常數 `ContentSplitter.PAYWALL_MARKER = "<!--paywall-->"`

> **為什麼在 markdown 層切、不在 HTML 層切**：截斷 HTML 字串會斷在標籤中間產生破版。而且切分後兩段各自渲染，`<!--paywall-->` 這行註解本身就消失了——若先渲染再切，commonmark 會把它當 raw HTML 原樣輸出到頁面上。

- [ ] **Step 1: 寫失敗的測試**

Create `survey-backend/src/test/java/world/springai/survey/reader/ContentSplitterTest.java`:

```java
package world.springai.survey.reader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ContentSplitter 行為測試：無標記、單一標記、多重標記、標記在首尾、null 輸入 */
class ContentSplitterTest {

    private final ContentSplitter splitter = new ContentSplitter();

    /** 無標記：全文皆為免費區，hasGate 為 false */
    @Test
    void noMarkerMeansEverythingIsFree() {
        String markdown = "# 標題\n\n內文全部免費。";

        ContentSplitter.Split split = splitter.split(markdown);

        assertFalse(split.hasGate());
        assertEquals(markdown, split.freeMarkdown());
        assertEquals("", split.gatedMarkdown());
    }

    /** 單一標記：標記前為免費區、標記後為受限區，標記本身不出現在任何一段 */
    @Test
    void singleMarkerSplitsIntoTwoParts() {
        String markdown = """
            免費開場，勾住讀者。

            <!--paywall-->

            受限內容，需要權限。""";

        ContentSplitter.Split split = splitter.split(markdown);

        assertTrue(split.hasGate());
        assertTrue(split.freeMarkdown().contains("免費開場"));
        assertFalse(split.freeMarkdown().contains("受限內容"), "免費區不得含受限內容");
        assertFalse(split.freeMarkdown().contains("<!--paywall-->"), "標記不得殘留在免費區");
        assertTrue(split.gatedMarkdown().contains("受限內容"));
        assertFalse(split.gatedMarkdown().contains("<!--paywall-->"), "標記不得殘留在受限區");
    }

    /** 多個標記：以第一個為界，其餘標記視為受限區的普通內容並移除 */
    @Test
    void multipleMarkersUseTheFirstOne() {
        String markdown = "免費\n\n<!--paywall-->\n\n受限一\n\n<!--paywall-->\n\n受限二";

        ContentSplitter.Split split = splitter.split(markdown);

        assertTrue(split.hasGate());
        assertEquals("免費", split.freeMarkdown().trim());
        assertTrue(split.gatedMarkdown().contains("受限一"));
        assertTrue(split.gatedMarkdown().contains("受限二"));
        assertFalse(split.gatedMarkdown().contains("<!--paywall-->"), "多餘標記須清除");
    }

    /** 標記在首行：免費區為空，整篇都受限 */
    @Test
    void markerAtStartMeansEverythingIsGated() {
        ContentSplitter.Split split = splitter.split("<!--paywall-->\n\n全部受限");

        assertTrue(split.hasGate());
        assertEquals("", split.freeMarkdown().trim());
        assertEquals("全部受限", split.gatedMarkdown().trim());
    }

    /** 標記在末行：受限區為空，等同全文免費（但 hasGate 仍為 true） */
    @Test
    void markerAtEndMeansNothingIsGated() {
        ContentSplitter.Split split = splitter.split("全部免費\n\n<!--paywall-->");

        assertTrue(split.hasGate());
        assertEquals("全部免費", split.freeMarkdown().trim());
        assertEquals("", split.gatedMarkdown().trim());
    }

    /** 標記前後有空白也要能認出（作者手動輸入難免帶空白） */
    @Test
    void markerWithSurroundingWhitespaceIsRecognised() {
        ContentSplitter.Split split = splitter.split("免費\n\n   <!--paywall-->   \n\n受限");

        assertTrue(split.hasGate());
        assertEquals("免費", split.freeMarkdown().trim());
        assertEquals("受限", split.gatedMarkdown().trim());
    }

    /** null 視為空字串，不拋例外 */
    @Test
    void nullIsTreatedAsEmpty() {
        ContentSplitter.Split split = splitter.split(null);

        assertFalse(split.hasGate());
        assertEquals("", split.freeMarkdown());
        assertEquals("", split.gatedMarkdown());
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test -Dtest=ContentSplitterTest
```

Expected: 編譯失敗，`cannot find symbol: class ContentSplitter`

- [ ] **Step 3: 實作 ContentSplitter**

Create `survey-backend/src/main/java/world/springai/survey/reader/ContentSplitter.java`:

```java
package world.springai.survey.reader;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 依作者標記把電子報 markdown 切成「免費區」與「受限區」。
 *
 * <p>作者在 markdown 中單獨一行插入 {@value #PAYWALL_MARKER}（沿用 WordPress
 * {@code <!--more-->} 慣例，任何編輯器都不會破版），該行之後的內容需要權限才能看。</p>
 *
 * <p>刻意在 markdown 層切分而非渲染後切 HTML：截斷 HTML 字串會斷在標籤中間造成破版；
 * 而且先渲染再切的話，commonmark 會把這行 HTML 註解原樣輸出到頁面上。</p>
 *
 * <p>正交性：本類決定「哪裡開始要權限」，campaign 的 tier 與 credit_cost 決定
 * 「要什麼權限」。兩者互不干涉，所以 BASIC 文章也可以有受限區。</p>
 */
@Component
public class ContentSplitter {

    /** 受限區起點標記 */
    public static final String PAYWALL_MARKER = "<!--paywall-->";

    /**
     * 切分結果。
     *
     * @param freeMarkdown  免費區 markdown（所有人可見）
     * @param gatedMarkdown 受限區 markdown（需權限；未授權時**絕不可**輸出給前端）
     * @param hasGate       原文是否含標記
     */
    public record Split(String freeMarkdown, String gatedMarkdown, boolean hasGate) {}

    /** 以第一個標記為界切分；無標記時全文皆為免費區。null 視為空字串 */
    public Split split(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return new Split("", "", false);
        }

        String[] lines = markdown.split("\r?\n", -1);
        int markerIndex = indexOfMarkerLine(lines);

        if (markerIndex < 0) {
            return new Split(markdown, "", false);
        }

        String free = String.join("\n", List.of(lines).subList(0, markerIndex));
        // 受限區可能還有多餘標記（作者插了不只一個），一併清除避免顯示在頁面上
        String gated = String.join("\n", stripMarkerLines(lines, markerIndex + 1));
        return new Split(free, gated, true);
    }

    /** 找出第一個「整行只有標記」的行號；找不到回 -1 */
    private int indexOfMarkerLine(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            if (PAYWALL_MARKER.equals(lines[i].trim())) {
                return i;
            }
        }
        return -1;
    }

    /** 取出 from 之後的所有行，並濾掉其中多餘的標記行 */
    private List<String> stripMarkerLines(String[] lines, int from) {
        List<String> kept = new ArrayList<>();
        for (int i = from; i < lines.length; i++) {
            if (!PAYWALL_MARKER.equals(lines[i].trim())) {
                kept.add(lines[i]);
            }
        }
        return kept;
    }
}
```

- [ ] **Step 4: 跑測試確認通過**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test -Dtest=ContentSplitterTest
```

Expected: `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`

若 `noMarkerMeansEverythingIsFree` 失敗，注意該測試用了 `.replace('.', '。')` 的技巧來避免全形句號在原始碼中的混淆——直接改成期望字串 `"# 標題\n\n內文全部免費。"` 也可以。

- [ ] **Step 5: 跑全部測試並 commit**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test
```

Expected: `Tests run: 84, Failures: 0, Errors: 0, Skipped: 0`（77 + 7）

```powershell
git add src/main/java/world/springai/survey/reader/ContentSplitter.java src/test/java/world/springai/survey/reader/ContentSplitterTest.java
git commit -m @'
feat(reader): ContentSplitter —— 依 <!--paywall--> 切分免費區與受限區

- 在 markdown 層切分，非渲染後切 HTML：截 HTML 會斷在標籤中間破版，
  且先渲染再切會讓 commonmark 把註解原樣輸出到頁面上
- 以第一個標記為界；受限區內多餘標記一併清除
- 邊界情形：無標記（全免費）、標記在首行（全受限）、標記在末行、
  標記前後帶空白、null 輸入
- 正交設計：本類決定「哪裡要權限」，tier/credit_cost 決定「要什麼權限」

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
'@
```

---

## Task 5: 讀者端 entity 與 repository

**Files:**
- Create: `reader/Reader.java`、`reader/ReaderRepository.java`、`reader/CreditTxn.java`、`reader/CreditTxnRepository.java`、`reader/ArticleAccess.java`、`reader/ArticleAccessRepository.java`、`reader/LoginToken.java`、`reader/LoginTokenRepository.java`（皆位於 `survey-backend/src/main/java/world/springai/survey/reader/`）
- Modify: `survey-backend/src/main/java/world/springai/survey/audience/SurveyResponse.java`（補 `lastEngagedAt`）
- Test: `survey-backend/src/test/java/world/springai/survey/reader/ReaderEntityMappingTest.java`

**Interfaces:**
- Consumes: V7 的 5 張表（Task 2）
- Produces（後續 task 依賴這些簽章）：
  - `Reader`：`getId/getEmail/getTier/getVipExpiresAt/getCredits/getReferralCode/getReferredBy/getLastLoginAt/getCreatedAt` 與對應 setter；建構子 `Reader(String email, String referralCode)`；常數 `Reader.TIER_FREE = "FREE"`、`Reader.TIER_VIP = "VIP"`；方法 `boolean isActiveVip(OffsetDateTime now)`
  - `ReaderRepository`：`Optional<Reader> findByEmailIgnoreCase(String email)`、`boolean existsByReferralCode(String code)`、`Optional<Reader> findByReferralCode(String code)`
  - `CreditTxn`：建構子 `CreditTxn(Long readerId, int delta, String reason, Long campaignId, String note)`；常數 `REASON_SIGNUP_GRANT = "SIGNUP_GRANT"`、`REASON_REFERRAL = "REFERRAL"`、`REASON_READ = "READ"`、`REASON_ADMIN_GRANT = "ADMIN_GRANT"`
  - `CreditTxnRepository`：`List<CreditTxn> findByReaderIdOrderByCreatedAtDesc(Long readerId)`
  - `ArticleAccess`：建構子 `ArticleAccess(Long readerId, Long campaignId, int cost)`
  - `ArticleAccessRepository`：`boolean existsByReaderIdAndCampaignId(Long readerId, Long campaignId)`、`List<ArticleAccess> findByReaderId(Long readerId)`
  - `LoginToken`：建構子 `LoginToken(String tokenHash, String email, OffsetDateTime expiresAt)`；`markUsed(OffsetDateTime at)`
  - `LoginTokenRepository`：`Optional<LoginToken> findByTokenHash(String hash)`、`long countByEmailAndCreatedAtAfter(String email, OffsetDateTime since)`
  - `SurveyResponse`：新增 `getLastEngagedAt()` / `setLastEngagedAt(OffsetDateTime)`

> **為什麼要 entity mapping 測試**：`ddl-auto: validate` 只在應用啟動時檢查 entity 與資料表是否吻合，單元測試不會觸發。若欄位名拼錯（例如 `vipExpiresAt` 沒對到 `vip_expires_at`），要到部署啟動才炸。這個測試用 Hibernate 的 metadata 做輕量檢查，不需要資料庫。

- [ ] **Step 1: 寫失敗的測試**

Create `survey-backend/src/test/java/world/springai/survey/reader/ReaderEntityMappingTest.java`:

```java
package world.springai.survey.reader;

import jakarta.persistence.Column;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 讀者端 entity 的對應與行為測試。
 *
 * <p>ddl-auto=validate 只在啟動時檢查 entity 與表結構是否吻合，單元測試不會觸發，
 * 因此欄位名拼錯要到部署啟動才會發現。這裡以反射檢查 snake_case 欄位是否都有
 * 明確的 @Column(name)，把該類錯誤提早到測試階段。</p>
 */
class ReaderEntityMappingTest {

    /** 四個 entity 都必須明確指定表名，表名不得依賴類名推導 */
    @Test
    void entitiesDeclareExplicitTableNames() {
        assertEquals("reader", Reader.class.getAnnotation(Table.class).name());
        assertEquals("credit_txn", CreditTxn.class.getAnnotation(Table.class).name());
        assertEquals("article_access", ArticleAccess.class.getAnnotation(Table.class).name());
        assertEquals("login_token", LoginToken.class.getAnnotation(Table.class).name());
    }

    /** 所有駝峰命名的欄位都必須有 @Column(name = "snake_case")，否則 validate 會在啟動時失敗 */
    @Test
    void camelCaseFieldsHaveExplicitColumnNames() {
        List<String> missing = new ArrayList<>();
        for (Class<?> type : List.of(Reader.class, CreditTxn.class, ArticleAccess.class, LoginToken.class)) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isSynthetic() || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                boolean isCamelCase = !field.getName().equals(field.getName().toLowerCase());
                if (!isCamelCase) {
                    continue;
                }
                Column column = field.getAnnotation(Column.class);
                if (column == null || column.name().isEmpty()) {
                    missing.add(type.getSimpleName() + "." + field.getName());
                }
            }
        }
        assertTrue(missing.isEmpty(),
            "以下駝峰欄位缺少 @Column(name = \"snake_case\")，會導致啟動時 validate 失敗：" + missing);
    }

    /** 新讀者預設為 FREE、0 點 */
    @Test
    void newReaderDefaultsToFreeWithZeroCredits() {
        Reader reader = new Reader("user@example.com", "ABC12345");

        assertEquals("user@example.com", reader.getEmail());
        assertEquals(Reader.TIER_FREE, reader.getTier());
        assertEquals(0, reader.getCredits());
        assertEquals("ABC12345", reader.getReferralCode());
    }

    /** FREE 讀者不是有效 VIP */
    @Test
    void freeReaderIsNotActiveVip() {
        Reader reader = new Reader("user@example.com", "ABC12345");
        assertFalse(reader.isActiveVip(OffsetDateTime.now()));
    }

    /** VIP 未到期為有效；已到期視為無效（不做自動降級，靠判斷時比對） */
    @Test
    void vipIsActiveOnlyBeforeExpiry() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-25T00:00:00+08:00");
        Reader reader = new Reader("vip@example.com", "VIP12345");
        reader.setTier(Reader.TIER_VIP);

        reader.setVipExpiresAt(now.plusDays(1));
        assertTrue(reader.isActiveVip(now), "未到期應為有效 VIP");

        reader.setVipExpiresAt(now.minusDays(1));
        assertFalse(reader.isActiveVip(now), "已到期應視為 FREE");
    }

    /** VIP 且到期時間為 NULL 表示無限期 */
    @Test
    void vipWithoutExpiryIsPermanent() {
        Reader reader = new Reader("vip@example.com", "VIP12345");
        reader.setTier(Reader.TIER_VIP);
        reader.setVipExpiresAt(null);

        assertTrue(reader.isActiveVip(OffsetDateTime.now()));
    }

    /** login token 標記為已使用後不可重複使用 */
    @Test
    void loginTokenCanBeMarkedUsedOnce() {
        OffsetDateTime now = OffsetDateTime.now();
        LoginToken token = new LoginToken("hash", "user@example.com", now.plusMinutes(15));

        assertFalse(token.isUsed());
        token.markUsed(now);
        assertTrue(token.isUsed());
    }

    /** login token 過期判斷 */
    @Test
    void loginTokenExpiryIsEvaluatedAgainstGivenTime() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-25T12:00:00+08:00");
        LoginToken token = new LoginToken("hash", "user@example.com", now.plusMinutes(15));

        assertFalse(token.isExpired(now));
        assertTrue(token.isExpired(now.plusMinutes(16)));
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test -Dtest=ReaderEntityMappingTest
```

Expected: 編譯失敗，`cannot find symbol: class Reader`

- [ ] **Step 3: 建立 Reader entity**

Create `survey-backend/src/main/java/world/springai/survey/reader/Reader.java`:

```java
package world.springai.survey.reader;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 讀者帳戶實體，對應資料表 reader。
 *
 * <p>刻意不與 survey_response 合併：survey_response 管「同意與來源」（名單中心的職責），
 * 本表管「帳戶與點數」，兩者以 email 關聯。合併會讓名單中心的 schema 綁上讀者端關注點。</p>
 *
 * <p><b>不變式</b>：本列存在不代表已確認訂閱。訂閱狀態一律查
 * survey_response.consent 與 unsubscribed。</p>
 */
@Entity
@Table(name = "reader")
public class Reader {

    /** 免費讀者 */
    public static final String TIER_FREE = "FREE";
    /** VIP 讀者：進階內容不需點數 */
    public static final String TIER_VIP = "VIP";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 讀者 email，一律正規化為小寫 */
    @Column(nullable = false, unique = true)
    private String email;

    /** 等級：FREE 或 VIP */
    @Column(nullable = false)
    private String tier = TIER_FREE;

    /** VIP 到期時間；NULL 表無限期（僅 tier=VIP 時有意義） */
    @Column(name = "vip_expires_at")
    private OffsetDateTime vipExpiresAt;

    /** 目前點數餘額，為 credit_txn 的物化總和 */
    @Column(nullable = false)
    private int credits = 0;

    /** 個人邀請碼 */
    @Column(name = "referral_code", nullable = false, unique = true)
    private String referralCode;

    /** 推薦人的 reader.id */
    @Column(name = "referred_by")
    private Long referredBy;

    /** 最後登入時間 */
    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    /** 建立時間，由資料庫維護 */
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** JPA 需要的無參數建構子 */
    protected Reader() {
    }

    /** 建立新讀者：預設 FREE、0 點，email 由呼叫端負責正規化為小寫 */
    public Reader(String email, String referralCode) {
        this.email = email;
        this.referralCode = referralCode;
    }

    /**
     * 是否為目前有效的 VIP。
     *
     * <p>不做自動降級排程（spec §13.5）：tier 保持 VIP 但到期時間已過時，
     * 一律在判斷當下視為 FREE。時間由呼叫端傳入，方便測試。</p>
     */
    public boolean isActiveVip(OffsetDateTime now) {
        if (!TIER_VIP.equals(tier)) {
            return false;
        }
        return vipExpiresAt == null || vipExpiresAt.isAfter(now);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
    public OffsetDateTime getVipExpiresAt() { return vipExpiresAt; }
    public void setVipExpiresAt(OffsetDateTime vipExpiresAt) { this.vipExpiresAt = vipExpiresAt; }
    public int getCredits() { return credits; }
    public void setCredits(int credits) { this.credits = credits; }
    public String getReferralCode() { return referralCode; }
    public void setReferralCode(String referralCode) { this.referralCode = referralCode; }
    public Long getReferredBy() { return referredBy; }
    public void setReferredBy(Long referredBy) { this.referredBy = referredBy; }
    public OffsetDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(OffsetDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 4: 建立 CreditTxn entity**

Create `survey-backend/src/main/java/world/springai/survey/reader/CreditTxn.java`:

```java
package world.springai.survey.reader;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 點數帳本實體，對應資料表 credit_txn。
 *
 * <p><b>只增不改不刪</b>：reader.credits 永遠可由本表重算稽核。這也是
 * 「規則調整不扣減既有點數餘額」這個對外承諾能成立的原因——調整參數只影響
 * 未來的扣點金額，不回溯既有交易。</p>
 */
@Entity
@Table(name = "credit_txn")
public class CreditTxn {

    /** 首次登入的初始贈點 */
    public static final String REASON_SIGNUP_GRANT = "SIGNUP_GRANT";
    /** 邀請成功獎勵（被邀者點確認信後才發） */
    public static final String REASON_REFERRAL = "REFERRAL";
    /** 閱讀進階文章扣點 */
    public static final String REASON_READ = "READ";
    /** 後台手動加點（如贈與上課學員） */
    public static final String REASON_ADMIN_GRANT = "ADMIN_GRANT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所屬讀者 */
    @Column(name = "reader_id", nullable = false)
    private Long readerId;

    /** 點數變動：正數加點、負數扣點 */
    @Column(nullable = false)
    private int delta;

    /** 交易原因，取本類的 REASON_* 常數 */
    @Column(nullable = false)
    private String reason;

    /** reason=READ 時對應的文章 */
    @Column(name = "campaign_id")
    private Long campaignId;

    /** 說明文字，ADMIN_GRANT 時記錄贈點理由 */
    private String note;

    /** 建立時間，由資料庫維護 */
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** JPA 需要的無參數建構子 */
    protected CreditTxn() {
    }

    /** 建立一筆點數交易 */
    public CreditTxn(Long readerId, int delta, String reason, Long campaignId, String note) {
        this.readerId = readerId;
        this.delta = delta;
        this.reason = reason;
        this.campaignId = campaignId;
        this.note = note;
    }

    public Long getId() { return id; }
    public Long getReaderId() { return readerId; }
    public int getDelta() { return delta; }
    public String getReason() { return reason; }
    public Long getCampaignId() { return campaignId; }
    public String getNote() { return note; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 5: 建立 ArticleAccess 與 LoginToken entity**

Create `survey-backend/src/main/java/world/springai/survey/reader/ArticleAccess.java`:

```java
package world.springai.survey.reader;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 已解鎖文章實體，對應資料表 article_access。
 *
 * <p>資料表上的 UNIQUE(reader_id, campaign_id) 同時扮演兩個角色：
 * 「同一篇不重複扣點」的保證，以及並發解鎖的防線（同時兩個請求只有一個
 * 能插入成功，另一個轉為「已解鎖」路徑）。</p>
 */
@Entity
@Table(name = "article_access")
public class ArticleAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 解鎖者 */
    @Column(name = "reader_id", nullable = false)
    private Long readerId;

    /** 被解鎖的文章 */
    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    /** 當時實扣點數；0 表 VIP 或 BASIC 免費通行 */
    @Column(nullable = false)
    private int cost;

    /** 解鎖時間，由資料庫維護 */
    @Column(name = "unlocked_at", insertable = false, updatable = false)
    private OffsetDateTime unlockedAt;

    /** JPA 需要的無參數建構子 */
    protected ArticleAccess() {
    }

    /** 建立一筆解鎖紀錄 */
    public ArticleAccess(Long readerId, Long campaignId, int cost) {
        this.readerId = readerId;
        this.campaignId = campaignId;
        this.cost = cost;
    }

    public Long getId() { return id; }
    public Long getReaderId() { return readerId; }
    public Long getCampaignId() { return campaignId; }
    public int getCost() { return cost; }
    public OffsetDateTime getUnlockedAt() { return unlockedAt; }
}
```

Create `survey-backend/src/main/java/world/springai/survey/reader/LoginToken.java`:

```java
package world.springai.survey.reader;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * magic link 一次性登入 token 實體，對應資料表 login_token。
 *
 * <p>刻意不用 UnsubscribeTokenService 的無狀態 HMAC：那個簽章沒有到期概念也無法作廢，
 * 對退訂連結是特性（永久有效才對），對登入則是漏洞。因此登入 token 走資料庫，
 * 具備 expires_at 與 used_at。</p>
 *
 * <p>只存 SHA-256 雜湊，明文 token 僅出現在寄出的信裡——資料庫外洩時 token 不可用。</p>
 */
@Entity
@Table(name = "login_token")
public class LoginToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 明文 token 的 SHA-256 雜湊（Base64 URL-safe） */
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    /** 要登入的 email，一律正規化為小寫 */
    @Column(nullable = false)
    private String email;

    /** 到期時間 */
    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    /** 使用時間；非 NULL 即已使用，不可重用 */
    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    /** 建立時間，由資料庫維護 */
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** JPA 需要的無參數建構子 */
    protected LoginToken() {
    }

    /** 建立一筆待使用的登入 token */
    public LoginToken(String tokenHash, String email, OffsetDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.email = email;
        this.expiresAt = expiresAt;
    }

    /** 是否已被使用過 */
    public boolean isUsed() {
        return usedAt != null;
    }

    /** 相對於指定時間是否已過期 */
    public boolean isExpired(OffsetDateTime now) {
        return !expiresAt.isAfter(now);
    }

    /** 標記為已使用 */
    public void markUsed(OffsetDateTime at) {
        this.usedAt = at;
    }

    public Long getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public String getEmail() { return email; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getUsedAt() { return usedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 6: 建立四個 repository**

Create `survey-backend/src/main/java/world/springai/survey/reader/ReaderRepository.java`:

```java
package world.springai.survey.reader;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 讀者帳戶資料存取層 */
public interface ReaderRepository extends JpaRepository<Reader, Long> {

    /** 依 email 查讀者（不分大小寫），登入時使用 */
    Optional<Reader> findByEmailIgnoreCase(String email);

    /** 邀請碼是否已存在，產生新碼時用於避免碰撞 */
    boolean existsByReferralCode(String referralCode);

    /** 依邀請碼查推薦人，訂閱歸因時使用 */
    Optional<Reader> findByReferralCode(String referralCode);
}
```

Create `survey-backend/src/main/java/world/springai/survey/reader/CreditTxnRepository.java`:

```java
package world.springai.survey.reader;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 點數帳本資料存取層；只新增不修改，故無 update 方法 */
public interface CreditTxnRepository extends JpaRepository<CreditTxn, Long> {

    /** 某讀者的交易明細，新到舊（客訴對帳與「我的帳戶」頁使用） */
    List<CreditTxn> findByReaderIdOrderByCreatedAtDesc(Long readerId);
}
```

Create `survey-backend/src/main/java/world/springai/survey/reader/ArticleAccessRepository.java`:

```java
package world.springai.survey.reader;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 已解鎖文章資料存取層 */
public interface ArticleAccessRepository extends JpaRepository<ArticleAccess, Long> {

    /** 該讀者是否已解鎖此文章（授權判斷的「已解鎖」路徑） */
    boolean existsByReaderIdAndCampaignId(Long readerId, Long campaignId);

    /** 該讀者已解鎖的全部文章，archive 列表用於標示解鎖狀態 */
    List<ArticleAccess> findByReaderId(Long readerId);
}
```

Create `survey-backend/src/main/java/world/springai/survey/reader/LoginTokenRepository.java`:

```java
package world.springai.survey.reader;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

/** 登入 token 資料存取層 */
public interface LoginTokenRepository extends JpaRepository<LoginToken, Long> {

    /** 依雜湊查 token（驗證 magic link 時使用；明文永不入庫） */
    Optional<LoginToken> findByTokenHash(String tokenHash);

    /** 某 email 在指定時間之後發出的 token 數，用於登入信節流 */
    long countByEmailAndCreatedAtAfter(String email, OffsetDateTime since);
}
```

- [ ] **Step 7: 為 SurveyResponse 補上 lastEngagedAt**

Modify `survey-backend/src/main/java/world/springai/survey/audience/SurveyResponse.java`：在 `createdAt` 欄位宣告之前插入：

```java
    /** 最後互動時間（確認訂閱／開信／登入／解鎖／改資料），供階段 F 的參與度分級使用 */
    @Column(name = "last_engaged_at")
    private OffsetDateTime lastEngagedAt;
```

並在 getter 區塊的 `getCreatedAt()` 之前插入：

```java
    public OffsetDateTime getLastEngagedAt() { return lastEngagedAt; }
    public void setLastEngagedAt(OffsetDateTime lastEngagedAt) { this.lastEngagedAt = lastEngagedAt; }
```

- [ ] **Step 8: 跑測試確認通過**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test -Dtest=ReaderEntityMappingTest
```

Expected: `Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 9: 以實機啟動驗證 entity 與資料表吻合**

這是本 task 唯一能真正驗證 `@Column` 對應正確的方式（`ddl-auto: validate` 只在啟動時執行）。對 Task 2 建立的測試庫啟動：

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
$env:JDBC_URL = "jdbc:postgresql://127.0.0.1:5432/survey_mig_test"
mvn spring-boot:run
```

Expected: 啟動成功，日誌**不含** `Schema-validation: missing column` 或 `wrong column type`。看到 `Started SurveyApplication` 後 `Ctrl+C`。

若出現 `Schema-validation: missing column [xxx] in table [yyy]`，比對該欄位的 `@Column(name = ...)` 與 V7/V8 的 SQL 欄位名。這正是這步要抓的錯。

- [ ] **Step 10: 跑全部測試並 commit**

```powershell
Remove-Item Env:\JDBC_URL
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test
```

Expected: `Tests run: 92, Failures: 0, Errors: 0, Skipped: 0`（84 + 8）

```powershell
git add src/main/java/world/springai/survey/reader/ src/main/java/world/springai/survey/audience/SurveyResponse.java src/test/java/world/springai/survey/reader/ReaderEntityMappingTest.java
git commit -m @'
feat(reader): 讀者端 entity 與 repository

- Reader：FREE/VIP、isActiveVip() 於判斷當下比對到期時間
  （不做自動降級排程，spec §13.5）
- CreditTxn：只增不改的帳本，四種 reason 常數
- ArticleAccess：UNIQUE(reader_id, campaign_id) 既保證不重複扣點
  也是並發解鎖的防線
- LoginToken：只存 SHA-256 雜湊，具 expires_at 與 used_at
  （不用無狀態 HMAC，登入必須可到期可作廢）
- SurveyResponse 補 lastEngagedAt 欄位
- entity mapping 測試以反射檢查駝峰欄位都有明確 @Column(name)，
  把「啟動時才炸」的欄位名錯誤提早到測試階段

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
'@
```

---

## 尚未撰寫的任務（Task 6–12）

以下任務的分解與介面契約已確定，**程式碼細節待續寫**：

| Task | 內容 | 關鍵介面 |
|---|---|---|
| 6 | `LoginTokenService` | `issue(email) → String`（明文 token）、`consume(rawToken, now) → Optional<String>`（回 email，含一次性標記）、`isThrottled(email, now) → boolean` |
| 7 | `ReaderSessionService` | `issueJwt(readerId) → String`、`readReaderId(jwt) → Optional<Long>`、`buildCookie(jwt) → ResponseCookie`（httpOnly + Secure + SameSite=Lax）、`buildClearCookie()` |
| 8 | `ReaderAccountService` + `LoginMailService` | `findOrCreate(email) → Reader`（首次建帳戶並發 `SIGNUP_GRANT` 初始贈點、產生不碰撞的邀請碼）、`sendLoginLink(email, redirect)` |
| 9 | `Campaign` 擴充 + `CampaignRepository` 查詢 | 六個新欄位的 getter/setter、`findByPublishedAtIsNotNullOrderByPublishedAtDesc()`、`findBySlug(slug)` |
| 10 | `AccessDecisionService` | `decide(Reader orNull, Campaign) → Decision`（`FULL` / `PARTIAL` + 原因）；本階段五條路徑，扣點路徑留給階段 C |
| 11 | `ReaderController` + 四個靜態頁 + `WebConfig` 路由 | `GET /r/`、`/r/archive`、`/r/news/{slug}`、`/r/login`；`POST /api/reader/login`、`GET /api/reader/login/verify`、`POST /api/reader/logout` |
| 12 | 端到端驗證與部署前檢查 | 完整登入→閱讀流程實測；**PARTIAL 回應不含受限區字串**的專屬測試；spec §4.0 部署前置檢查 |

**續寫前必讀**：Task 12 的「PARTIAL 不洩漏」測試是本階段最重要的一項驗證（spec §5.3 與 Global Constraints），必須斷言回應 HTML 完全不含受限區的任何字串，而非只檢查有無某個 CSS class。

