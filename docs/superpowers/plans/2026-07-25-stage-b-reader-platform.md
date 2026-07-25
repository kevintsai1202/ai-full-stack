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
2. **migration 驗證改為自動化 JUnit 測試，但不使用 Testcontainers**（spec §12 已更新）。原設計採手動腳本；改為自動化是因為「既有訂閱名單不可清除」是硬約束（spec §4.0），這道防線不該靠人記得跑腳本。

   **本機環境事實**（實作時直接用，不必自行探索、不必嘗試修 Docker）：

   - **沒有安裝 psql 執行檔。** 需要 SQL 互動時用 `docker exec survey-test-db psql -U postgres ...`。
   - **已備好專用測試容器 `survey-test-db`**：port **5433**，image `pgvector/pgvector:pg18`，帳密 `postgres` / `password`。`MigrationSafetyTest` 連的就是它。
   - **5432 埠是別的專案的容器，不得動用。**
   - **Testcontainers 在本機不可用**：Docker Desktop 29.6.1（API 1.55）與 docker-java 的 npipe 客戶端不相容，已實測 testcontainers 1.21.0／2.0.5 與指定 `DOCKER_HOST` 皆無效。**不要再嘗試修這件事**，也不要改 Docker Desktop 設定（重啟會影響本機其他專案的容器）。

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
| `src/test/java/world/springai/survey/MigrationSafetyTest.java` | 既有資料保全與 backfill 正確性（Testcontainers 起真實 PostgreSQL） |

### 新增：讀者端靜態頁

| 檔案 | 路徑 |
|---|---|
| `src/main/resources/static/reader/index.html` | `/r/` 訂閱入口 |
| `src/main/resources/static/reader/archive.html` | `/r/archive` 歷史列表 |
| `src/main/resources/static/reader/article.html` | `/r/news/{slug}` 單篇 |
| `src/main/resources/static/reader/login.html` | `/r/login` 登入 |
| `src/main/resources/static/reader/reader.css` | 讀者端共用樣式 |

**視覺語言：沿用 `static/index.html` 的設計系統 token**（該套 token 與 land-page `springai.world` 共用，註解已明載「確保視覺一致」）。讀者端是對外產品頁面，必須用這套完整 token，**不要**用 `SurveyController` 裡 confirm／unsubscribe 頁那種極簡卡片樣式——那是交易結果頁的樣式，不是產品頁。

從 `index.html` 複製到 `reader.css` 的 `:root` token（節錄關鍵項）：

```css
:root {
  --font-main: "Inter", "Noto Sans TC", "Microsoft JhengHei", system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  --bg:#f7fafc; --fg:#102033; --muted:#5c6b7d; --muted-2:#8190a3;
  --surface:#ffffff; --surface-2:#f1f6f9; --surface-3:#e6edf3;
  --border:#dce5ee; --border-strong:#b9c7d7;
  --accent:#0d9488; --accent-deep:#0f766e; --accent-soft:#d9f3ef;
  --accent-2:#f59e0b; --accent-2-deep:#b45309; --accent-2-soft:#fff2cc;
  --ok:#16a34a; --err:#dc2626;
  --r-md:14px; --r-lg:22px; --r-pill:999px;
  --shadow-sm:0 10px 28px rgb(15 23 42 / .08);
  --shadow-md:0 18px 42px rgb(15 23 42 / .12);
  --shadow-lift:0 16px 34px rgb(15 23 42 / .18);
}
```

**注意 `index.html` 有 `<meta name="robots" content="noindex">`**（問卷頁刻意不被索引）。讀者端頁面**不可**照抄這行——spec §5.3 明訂 archive 與單篇免費區允許索引，那是 metered paywall 的正當做法。

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
    <!-- migration 安全性測試需要真實的 PostgreSQL（本專案用到 jsonb 與 @> 運算子，
         H2 不支援），但**不引入 Testcontainers**：本機 Docker Desktop 29.6.1（API 1.55）
         與 docker-java 的 npipe 客戶端不相容，會誤報「Could not find a valid Docker
         environment」——即使 docker CLI 與 named pipe 手動 HTTP 都正常。已實測
         testcontainers 1.21.0 與 2.0.5、以及明確指定 DOCKER_HOST 皆無效。

         改為由 MigrationSafetyTest 直接連本機專用測試容器（survey-test-db，port 5433），
         每次重建乾淨的 survey_migration_test 資料庫。既維持自動化（每次 mvn test 都驗證），
         也不新增任何測試依賴。 -->
```

**本任務不新增任何測試依賴。** 上面那段只是註解，說明 migration 測試為何不走 Testcontainers——這個決定是實地測試後的結果，不是偷懶。

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
- Test: `survey-backend/src/test/java/world/springai/survey/MigrationSafetyTest.java`

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

- [ ] **Step 3: 寫 migration 安全性測試（Testcontainers）**

Create `survey-backend/src/test/java/world/springai/survey/MigrationSafetyTest.java`:

```java
package world.springai.survey;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * V7／V8 migration 的既有資料保全與 backfill 正確性測試。
 *
 * <p><b>為什麼需要真實 PostgreSQL</b>：本專案用到 jsonb 與 @&gt; 運算子，H2 不支援。
 * 而「既有訂閱名單不可清除」是硬約束（spec §4.0）——訂閱者的同意是他們親自點確認信
 * 給出的，清掉就只能重新徵求。這道防線不該靠人記得跑腳本，所以做成每次 mvn test
 * 都會執行的自動化測試。</p>
 *
 * <p><b>為什麼不用 Testcontainers</b>：本機 Docker Desktop 29.6.1（API 1.55）與
 * docker-java 的 npipe 客戶端不相容，會誤報「Could not find a valid Docker
 * environment」，即使 docker CLI 與 named pipe 的手動 HTTP 請求都正常。已實測
 * testcontainers 1.21.0、2.0.5 與明確指定 DOCKER_HOST 皆無效。改為直接連本機
 * 專用測試容器。</p>
 *
 * <p><b>環境前提</b>：容器 survey-test-db 必須在執行中（見下方連線失敗時的指引）。
 * 連不上時本測試會明確失敗而非靜默跳過——寧可紅燈也不要假綠燈。</p>
 *
 * <p>流程：重建乾淨的測試資料庫 → 只套用 V1–V6（模擬正式庫現況）→ 塞入代表性
 * 既有資料 → 套用 V7／V8 → 斷言既有資料逐列未變且 backfill 正確。</p>
 */
class MigrationSafetyTest {

    /** 專用測試容器的維護資料庫連線（用於重建測試資料庫） */
    private static final String ADMIN_URL = "jdbc:postgresql://127.0.0.1:5433/postgres";
    /** 測試資料庫名稱；每次執行都會重建，只有本測試使用 */
    private static final String TEST_DB = "survey_migration_test";
    /** 測試資料庫連線 */
    private static final String TEST_URL = "jdbc:postgresql://127.0.0.1:5433/" + TEST_DB;
    private static final String USER = "postgres";
    private static final String PASS = "password";

    /** 既有資料的指紋：email 與同意狀態的組合，用於證明這些欄位逐列未被改寫 */
    private static final String CHECKSUM_SQL = """
        SELECT md5(string_agg(email || ':' || consent || ':' || unsubscribed, ',' ORDER BY id))
          FROM survey_response
        """;

    /** migration 前的 survey_response 筆數 */
    private static int beforeCount;
    /** migration 前的既有資料指紋 */
    private static String beforeChecksum;

    /** 重建測試資料庫 → 套用 V1–V6 → 塞既有資料 → 記錄狀態 → 套用 V7／V8 */
    @BeforeAll
    static void applyMigrations() throws SQLException {
        requireTestDatabase();
        recreateTestDatabase();

        // 只套用到 V6，模擬正式資料庫目前的狀態
        Flyway.configure()
            .dataSource(TEST_URL, USER, PASS)
            .target(MigrationVersion.fromVersion("6"))
            .load()
            .migrate();

        // 三種代表性的既有名單：已確認訂閱、待確認匯入、已退訂；外加一筆既有 campaign
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("""
                INSERT INTO survey_response (email, consent, unsubscribed, source) VALUES
                  ('confirmed@example.com', TRUE,  FALSE, 'survey_form'),
                  ('pending@example.com',   FALSE, FALSE, 'exam'),
                  ('gone@example.com',      TRUE,  TRUE,  'survey_form')
                """);
            st.execute("""
                INSERT INTO campaign (subject, markdown, mode, recipient_count,
                                      accepted_count, failed_count, status)
                VALUES ('既有電子報', '# 內容', 'now', 1, 1, 0, 'sent')
                """);
        }

        beforeCount = queryInt("SELECT count(*) FROM survey_response");
        beforeChecksum = queryString(CHECKSUM_SQL);

        // 套用 V7／V8
        Flyway.configure()
            .dataSource(TEST_URL, USER, PASS)
            .load()
            .migrate();
    }

    /** 既有列一筆都不能少，email 與同意狀態一個字都不能變 */
    @Test
    void existingRowsAreUntouched() throws SQLException {
        assertEquals(beforeCount, queryInt("SELECT count(*) FROM survey_response"),
            "migration 後 survey_response 筆數改變");
        assertEquals(beforeChecksum, queryString(CHECKSUM_SQL),
            "migration 後 email／consent／unsubscribed 有變動");
    }

    /**
     * 已確認訂閱者必須被回填 last_engaged_at。
     *
     * <p>若不回填，階段 F 的參與度分級會因「已寄多期 + last_engaged_at 為 NULL」
     * 把老訂閱者整批判為 sunset 而停寄——資料沒少但收不到信，且要到下次發送才顯現。</p>
     */
    @Test
    void confirmedSubscribersAreBackfilled() throws SQLException {
        assertEquals(1, queryInt("""
            SELECT count(*) FROM survey_response
             WHERE consent = TRUE AND unsubscribed = FALSE AND last_engaged_at IS NOT NULL
            """), "已確認訂閱者未被回填 last_engaged_at");
    }

    /** 待確認與已退訂者刻意不回填，保持 NULL（回填會造出假的參與紀錄） */
    @Test
    void nonSubscribersAreNotBackfilled() throws SQLException {
        assertEquals(0, queryInt("""
            SELECT count(*) FROM survey_response
             WHERE (consent = FALSE OR unsubscribed = TRUE) AND last_engaged_at IS NOT NULL
            """), "未確認或已退訂者被誤回填");
    }

    /** V7 的五張新表都要建立 */
    @Test
    void newTablesAreCreated() throws SQLException {
        for (String table : new String[] {"app_setting", "reader", "credit_txn", "article_access", "login_token"}) {
            assertEquals(1, queryInt(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = '" + table + "'"),
                "資料表 " + table + " 未建立");
        }
    }

    /** 參數初始值要進去，且可安全重跑（ON CONFLICT DO NOTHING） */
    @Test
    void appSettingsAreSeeded() throws SQLException {
        assertEquals(8, queryInt("SELECT count(*) FROM app_setting"), "app_setting 初始值筆數不符");
        assertEquals("300", queryString(
            "SELECT value FROM app_setting WHERE setting_key = 'credit.signup_grant'"));
        assertEquals("10", queryString(
            "SELECT value FROM app_setting WHERE setting_key = 'credit.premium_cost'"));
    }

    /** 既有 campaign 應取得新欄位的預設值，不得為 NULL */
    @Test
    void existingCampaignGetsColumnDefaults() throws SQLException {
        assertEquals(0, queryInt("""
            SELECT count(*) FROM campaign
             WHERE tier IS DISTINCT FROM 'BASIC'
                OR credit_cost <> 0
                OR filter_levels IS DISTINCT FROM 'active'
            """), "既有 campaign 未取得新欄位的預設值");
    }

    /** PREMIUM 卻沒有解鎖成本必須被 CHECK 約束擋下——否則進階內容會全面免費外洩 */
    @Test
    void premiumWithoutCostIsRejected() {
        assertThrows(SQLException.class, () -> {
            try (Connection c = connect(); Statement st = c.createStatement()) {
                st.execute("""
                    INSERT INTO campaign (subject, markdown, mode, recipient_count,
                                          accepted_count, failed_count, status, tier, credit_cost)
                    VALUES ('壞資料', '# x', 'now', 0, 0, 0, 'sent', 'PREMIUM', 0)
                    """);
            }
        }, "tier=PREMIUM 且 credit_cost=0 應被 CHECK 約束拒絕");
    }

    /**
     * 確認本機測試容器可用；連不上時以明確指引失敗。
     *
     * <p>刻意不用 assumeTrue 跳過：這個測試守的是「既有訂閱名單不可清除」，
     * 靜默跳過等於讓防線失效卻顯示綠燈。</p>
     */
    private static void requireTestDatabase() {
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS)) {
            // 能連上即可
        } catch (SQLException e) {
            fail("""
                連不上本機測試資料庫（%s）。

                本測試驗證 migration 不會破壞既有訂閱名單（spec §4.0 的硬約束），
                不能靜默跳過。請先啟動專用測試容器：

                  docker start survey-test-db

                容器不存在時建立它（不要用 5432，那是別的專案的容器）：

                  docker run -d --name survey-test-db -e POSTGRES_PASSWORD=password ^
                    -p 5433:5432 pgvector/pgvector:pg18

                原始錯誤：%s""".formatted(ADMIN_URL, e.getMessage()));
        }
    }

    /**
     * 重建乾淨的測試資料庫。
     *
     * <p>WITH (FORCE) 會斷開既有連線（PostgreSQL 13+），避免前次執行殘留的連線
     * 導致 DROP 失敗。此處 DROP 的是本測試專屬的資料庫，與 spec §4.0 禁止
     * 對正式資料執行 DROP 並不衝突。</p>
     */
    private static void recreateTestDatabase() throws SQLException {
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS);
             Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + TEST_DB + " WITH (FORCE)");
            st.execute("CREATE DATABASE " + TEST_DB);
        }
    }

    /** 取得測試資料庫連線 */
    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(TEST_URL, USER, PASS);
    }

    /** 執行單值整數查詢 */
    private static int queryInt(String sql) throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(), "查詢無結果：" + sql);
            return rs.getInt(1);
        }
    }

    /** 執行單值字串查詢 */
    private static String queryString(String sql) throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(), "查詢無結果：" + sql);
            return rs.getString(1);
        }
    }
}
```

**執行前確認測試容器在跑**：

```powershell
docker ps --filter name=survey-test-db --format "{{.Names}} {{.Status}}"
```

沒有輸出就先 `docker start survey-test-db`。

- [ ] **Step 4: 跑測試確認通過**

```powershell
cd d:\GitHub\hahow-ai-full-stack\survey-backend
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test -Dtest=MigrationSafetyTest
```

Expected: `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`

首次執行會拉 image；本機已有 `pgvector/pgvector:pg18` 故應直接使用。若出現 `Could not find a valid Docker environment`，確認 Docker Desktop 正在執行。

- [ ] **Step 5: 驗證測試真的會抓到 backfill 缺失**

暫時把 V8 最後的 backfill `UPDATE` 三行註解掉（保留其餘 SQL）：

```sql
-- UPDATE survey_response
--    SET last_engaged_at = now()
--  WHERE consent = TRUE AND unsubscribed = FALSE;
```

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test -Dtest=MigrationSafetyTest
```

Expected: **FAIL**，`confirmedSubscribersAreBackfilled` 失敗並顯示「已確認訂閱者未被回填 last_engaged_at」。

**這步不可省略。** 這個測試守的是「老訂閱者不會被整批停寄」——沒親眼看到它在 backfill 缺失時變紅，就不能相信它真的在守。確認後還原註解並重跑一次確認回綠。

（註：改動 migration 檔後 Flyway 的 checksum 會變，但本測試每次都用全新容器，不受既有 schema history 影響。）

- [ ] **Step 6: 確認既有測試仍全綠**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test
```

Expected: `Tests run: 79, Failures: 0, Errors: 0, Skipped: 0`（階段 A 的 72 + 本任務的 7）

（此時 entity 還沒補上新欄位，但 `ddl-auto: validate` 只在應用啟動時驗證；既有單元測試不連 DB，`MigrationSafetyTest` 用的是自己的 Testcontainers 容器且不經過 JPA，所以都不受影響。）

- [ ] **Step 7: Commit**

```powershell
git add src/main/resources/db/migration/V7__create_reader_platform.sql src/main/resources/db/migration/V8__extend_campaign_and_engagement.sql src/test/java/world/springai/survey/MigrationSafetyTest.java
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

驗證：MigrationSafetyTest（Testcontainers 起真實 PostgreSQL）7 個測試——
既有資料逐列不變、已確認訂閱者被回填、未確認者刻意不回填、五張新表齊備、
參數初始值、既有 campaign 取得欄位預設值、PREMIUM 無成本被 CHECK 擋下。
已實測「註解掉 backfill 會讓測試變紅」。

改用 Testcontainers 而非手動腳本：既有訂閱名單不可清除是硬約束（spec §4.0），
這道防線不該靠人記得跑腳本。spec §12 已同步更新。

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

Expected: `Tests run: 84, Failures: 0, Errors: 0, Skipped: 0`（79 + 5）

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

Expected: `Tests run: 91, Failures: 0, Errors: 0, Skipped: 0`（84 + 7）

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

Expected: `Tests run: 99, Failures: 0, Errors: 0, Skipped: 0`（91 + 8）

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

## Task 6: LoginTokenService（magic link token）

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/reader/LoginTokenService.java`
- Test: `survey-backend/src/test/java/world/springai/survey/reader/LoginTokenServiceTest.java`

**Interfaces:**
- Consumes: `LoginToken`、`LoginTokenRepository`（Task 5）
- Produces:
  - `LoginTokenService.issue(String email, OffsetDateTime now) → String`（回**明文** token，只有這一刻存在於記憶體，之後只留雜湊）
  - `LoginTokenService.consume(String rawToken, OffsetDateTime now) → Optional<String>`（回正規化後的 email；成功即標記 used）
  - `LoginTokenService.isThrottled(String email, OffsetDateTime now) → boolean`

- [ ] **Step 1: 寫失敗的測試**

Create `survey-backend/src/test/java/world/springai/survey/reader/LoginTokenServiceTest.java`:

```java
package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** LoginTokenService 行為測試：只存雜湊、一次性、可到期、節流 */
class LoginTokenServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-25T12:00:00+08:00");

    private LoginTokenRepository repository;
    private LoginTokenService service;

    @BeforeEach
    void setUp() {
        repository = mock(LoginTokenRepository.class);
        // TTL 15 分鐘、節流 15 分鐘內最多 3 封
        service = new LoginTokenService(repository, 15, 3, 15);
    }

    /** 明文 token 不得等於入庫的雜湊——DB 外洩時 token 必須不可用 */
    @Test
    void storesHashNotPlaintext() {
        when(repository.save(any(LoginToken.class))).thenAnswer(i -> i.getArgument(0));

        String rawToken = service.issue("User@Example.com", NOW);

        ArgumentCaptor<LoginToken> captor = ArgumentCaptor.forClass(LoginToken.class);
        verify(repository).save(captor.capture());
        assertNotEquals(rawToken, captor.getValue().getTokenHash(), "入庫的必須是雜湊，不是明文");
        assertFalse(captor.getValue().getTokenHash().contains(rawToken), "雜湊不得包含明文");
    }

    /** email 一律正規化為小寫並去除前後空白 */
    @Test
    void normalisesEmailBeforeStoring() {
        when(repository.save(any(LoginToken.class))).thenAnswer(i -> i.getArgument(0));

        service.issue("  User@Example.COM  ", NOW);

        ArgumentCaptor<LoginToken> captor = ArgumentCaptor.forClass(LoginToken.class);
        verify(repository).save(captor.capture());
        assertEquals("user@example.com", captor.getValue().getEmail());
    }

    /** 到期時間為簽發時間 + TTL */
    @Test
    void expiryIsIssuedAtPlusTtl() {
        when(repository.save(any(LoginToken.class))).thenAnswer(i -> i.getArgument(0));

        service.issue("user@example.com", NOW);

        ArgumentCaptor<LoginToken> captor = ArgumentCaptor.forClass(LoginToken.class);
        verify(repository).save(captor.capture());
        assertEquals(NOW.plusMinutes(15), captor.getValue().getExpiresAt());
    }

    /** 正常兌換：回傳 email 並標記為已使用 */
    @Test
    void consumeValidTokenReturnsEmailAndMarksUsed() {
        when(repository.save(any(LoginToken.class))).thenAnswer(i -> i.getArgument(0));
        String rawToken = service.issue("user@example.com", NOW);

        LoginToken stored = new LoginToken(service.hash(rawToken), "user@example.com", NOW.plusMinutes(15));
        when(repository.findByTokenHash(service.hash(rawToken))).thenReturn(Optional.of(stored));

        Optional<String> email = service.consume(rawToken, NOW.plusMinutes(1));

        assertTrue(email.isPresent());
        assertEquals("user@example.com", email.get());
        assertTrue(stored.isUsed(), "兌換後必須標記為已使用");
    }

    /** 同一 token 不得兌換兩次 */
    @Test
    void consumeRejectsAlreadyUsedToken() {
        String rawToken = "some-raw-token";
        LoginToken used = new LoginToken(service.hash(rawToken), "user@example.com", NOW.plusMinutes(15));
        used.markUsed(NOW);
        when(repository.findByTokenHash(service.hash(rawToken))).thenReturn(Optional.of(used));

        assertTrue(service.consume(rawToken, NOW.plusMinutes(1)).isEmpty());
    }

    /** 過期 token 不得兌換 */
    @Test
    void consumeRejectsExpiredToken() {
        String rawToken = "some-raw-token";
        LoginToken expired = new LoginToken(service.hash(rawToken), "user@example.com", NOW.plusMinutes(15));
        when(repository.findByTokenHash(service.hash(rawToken))).thenReturn(Optional.of(expired));

        assertTrue(service.consume(rawToken, NOW.plusMinutes(16)).isEmpty());
    }

    /** 不存在的 token 不得兌換，且不得因此拋例外 */
    @Test
    void consumeRejectsUnknownToken() {
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertTrue(service.consume("forged-token", NOW).isEmpty());
    }

    /** 空白或 null token 直接拒絕，不查資料庫 */
    @Test
    void consumeRejectsBlankTokenWithoutQuerying() {
        assertTrue(service.consume(null, NOW).isEmpty());
        assertTrue(service.consume("   ", NOW).isEmpty());
        verify(repository, never()).findByTokenHash(anyString());
    }

    /** 未達上限不節流 */
    @Test
    void notThrottledBelowLimit() {
        when(repository.countByEmailAndCreatedAtAfter("user@example.com", NOW.minusMinutes(15))).thenReturn(2L);

        assertFalse(service.isThrottled("user@example.com", NOW));
    }

    /** 達到上限即節流（避免被當寄信放大器） */
    @Test
    void throttledAtLimit() {
        when(repository.countByEmailAndCreatedAtAfter("user@example.com", NOW.minusMinutes(15))).thenReturn(3L);

        assertTrue(service.isThrottled("user@example.com", NOW));
    }

    /** 節流檢查也要正規化 email，否則大小寫變化可繞過 */
    @Test
    void throttleCheckNormalisesEmail() {
        when(repository.countByEmailAndCreatedAtAfter("user@example.com", NOW.minusMinutes(15))).thenReturn(3L);

        assertTrue(service.isThrottled("USER@Example.com", NOW));
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test -Dtest=LoginTokenServiceTest
```

Expected: 編譯失敗，`cannot find symbol: class LoginTokenService`

- [ ] **Step 3: 實作 LoginTokenService**

Create `survey-backend/src/main/java/world/springai/survey/reader/LoginTokenService.java`:

```java
package world.springai.survey.reader;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * magic link 登入 token 的簽發與兌換。
 *
 * <p>刻意不用 UnsubscribeTokenService 的無狀態 HMAC：那個簽章沒有到期概念也無法作廢
 * （對退訂連結是正確的特性），但登入 token 必須能到期、能一次性作廢。因此走資料庫。</p>
 *
 * <p>資料庫只存 SHA-256 雜湊，明文 token 僅在簽發當下存在於記憶體並寄進信裡——
 * 資料庫外洩時 token 不可被反推使用。</p>
 */
@Service
public class LoginTokenService {

    /** token 隨機位元組數；32 bytes = 256 bits，足以抵抗暴力猜測 */
    private static final int TOKEN_BYTES = 32;

    /** 密碼學安全的隨機來源 */
    private final SecureRandom random = new SecureRandom();

    private final LoginTokenRepository repository;
    /** magic link 有效分鐘數 */
    private final int ttlMinutes;
    /** 節流視窗內允許的最大封數 */
    private final int throttleCount;
    /** 節流視窗分鐘數 */
    private final int throttleMinutes;

    /** 注入資料存取層與部署設定 */
    public LoginTokenService(LoginTokenRepository repository,
                            @Value("${app.reader.login-token-ttl-minutes}") int ttlMinutes,
                            @Value("${app.reader.login-throttle-count}") int throttleCount,
                            @Value("${app.reader.login-throttle-minutes}") int throttleMinutes) {
        this.repository = repository;
        this.ttlMinutes = ttlMinutes;
        this.throttleCount = throttleCount;
        this.throttleMinutes = throttleMinutes;
    }

    /**
     * 簽發一個新的登入 token。
     *
     * @return **明文** token，呼叫端應立即組成連結寄出，不得記錄於日誌
     */
    public String issue(String email, OffsetDateTime now) {
        byte[] raw = new byte[TOKEN_BYTES];
        random.nextBytes(raw);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        repository.save(new LoginToken(hash(rawToken), normalize(email), now.plusMinutes(ttlMinutes)));
        return rawToken;
    }

    /**
     * 兌換 token：驗證存在、未過期、未使用，成功則標記已使用並回傳 email。
     *
     * <p>任何失敗一律回 empty 而不拋例外，也不區分「不存在」與「已使用」——
     * 對外不洩漏 token 的狀態。</p>
     */
    public Optional<String> consume(String rawToken, OffsetDateTime now) {
        if (!StringUtils.hasText(rawToken)) {
            return Optional.empty();
        }
        Optional<LoginToken> found = repository.findByTokenHash(hash(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        LoginToken token = found.get();
        if (token.isUsed() || token.isExpired(now)) {
            return Optional.empty();
        }
        token.markUsed(now);
        repository.save(token);
        return Optional.of(token.getEmail());
    }

    /** 該 email 在節流視窗內是否已達上限（避免服務被當成寄信放大器） */
    public boolean isThrottled(String email, OffsetDateTime now) {
        long recent = repository.countByEmailAndCreatedAtAfter(
            normalize(email), now.minusMinutes(throttleMinutes));
        return recent >= throttleCount;
    }

    /** 計算 token 的 SHA-256 雜湊（Base64 URL-safe 無 padding）；測試需要故為 public */
    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("找不到 SHA-256 演算法", e);
        }
    }

    /** email 正規化：去前後空白並轉小寫，與名單中心的比對基準一致 */
    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
```

- [ ] **Step 4: 跑測試確認通過**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test -Dtest=LoginTokenServiceTest
```

Expected: `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: 跑全部測試並 commit**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test
```

Expected: `Tests run: 110, Failures: 0, Errors: 0, Skipped: 0`（99 + 11）

```powershell
git add src/main/java/world/springai/survey/reader/LoginTokenService.java src/test/java/world/springai/survey/reader/LoginTokenServiceTest.java
git commit -m @'
feat(reader): LoginTokenService —— magic link token 簽發與兌換

- 32 bytes SecureRandom 亂數，DB 只存 SHA-256 雜湊，
  明文僅在簽發當下存在並寄進信裡
- 一次性（used_at）+ 可到期（expires_at）：這是不沿用
  UnsubscribeTokenService 無狀態 HMAC 的原因
- 兌換失敗一律回 empty 且不區分原因，不對外洩漏 token 狀態
- 節流：同 email 15 分鐘內最多 3 封，且節流檢查同樣正規化 email
  （否則改大小寫即可繞過）

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
'@
```

---

## Task 7: ReaderSessionService（JWT + cookie）

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/reader/ReaderSessionService.java`
- Test: `survey-backend/src/test/java/world/springai/survey/reader/ReaderSessionServiceTest.java`

**Interfaces:**
- Consumes: jjwt（Task 1）
- Produces:
  - `ReaderSessionService.COOKIE_NAME = "reader_session"`
  - `issueJwt(Long readerId, OffsetDateTime now) → String`
  - `readReaderId(String jwt, OffsetDateTime now) → Optional<Long>`
  - `buildSessionCookie(String jwt) → ResponseCookie`
  - `buildClearCookie() → ResponseCookie`

> **本機開發的 cookie 陷阱**：`Secure` cookie 在 `http://` 下會被瀏覽器丟棄，本機開發就永遠登不進去。因此 `secure` 旗標依 `app.public-base-url` 是否以 `https://` 開頭自動決定——不另加設定項，正式環境必然是 https，也就不會忘記開。

- [ ] **Step 1: 寫失敗的測試**

Create `survey-backend/src/test/java/world/springai/survey/reader/ReaderSessionServiceTest.java`:

```java
package world.springai.survey.reader;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ReaderSessionService 行為測試：JWT 往返、篡改與過期拒絕、cookie 安全屬性 */
class ReaderSessionServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-25T12:00:00+08:00");
    /** 秘鑰需 ≥ 32 bytes（HS256 要求 256 bits） */
    private static final String SECRET = "test-secret-key-at-least-32-bytes-long!!";

    /** 建立以 https 為對外網址的服務（正式環境情境） */
    private ReaderSessionService httpsService() {
        return new ReaderSessionService(SECRET, 28, "https://news.example.com");
    }

    /** 簽發後應能讀回同一個 readerId */
    @Test
    void issuedJwtRoundTripsReaderId() {
        ReaderSessionService service = httpsService();

        String jwt = service.issueJwt(42L, NOW);
        Optional<Long> readerId = service.readReaderId(jwt, NOW.plusDays(1));

        assertEquals(Optional.of(42L), readerId);
    }

    /** 被篡改的 JWT 必須拒絕 */
    @Test
    void tamperedJwtIsRejected() {
        ReaderSessionService service = httpsService();
        String jwt = service.issueJwt(42L, NOW);

        // 改掉最後一個字元破壞簽章
        String tampered = jwt.substring(0, jwt.length() - 1) + (jwt.endsWith("A") ? "B" : "A");

        assertTrue(service.readReaderId(tampered, NOW).isEmpty());
    }

    /** 以別的秘鑰簽的 JWT 必須拒絕 */
    @Test
    void jwtSignedWithOtherSecretIsRejected() {
        String otherJwt = new ReaderSessionService(
            "another-secret-key-at-least-32-bytes!!!!", 28, "https://news.example.com")
            .issueJwt(42L, NOW);

        assertTrue(httpsService().readReaderId(otherJwt, NOW).isEmpty());
    }

    /** 過期的 JWT 必須拒絕 */
    @Test
    void expiredJwtIsRejected() {
        ReaderSessionService service = httpsService();
        String jwt = service.issueJwt(42L, NOW);

        assertTrue(service.readReaderId(jwt, NOW.plusDays(29)).isEmpty(), "28 天效期，第 29 天應失效");
    }

    /** 格式錯誤或空白的 JWT 一律拒絕，不拋例外 */
    @Test
    void malformedJwtIsRejected() {
        ReaderSessionService service = httpsService();

        assertTrue(service.readReaderId(null, NOW).isEmpty());
        assertTrue(service.readReaderId("", NOW).isEmpty());
        assertTrue(service.readReaderId("not-a-jwt", NOW).isEmpty());
    }

    /** session cookie 必須 httpOnly（防 XSS 竊取）、SameSite=Lax、path=/、帶有效期 */
    @Test
    void sessionCookieHasSecurityAttributes() {
        ResponseCookie cookie = httpsService().buildSessionCookie("dummy-jwt");

        assertEquals(ReaderSessionService.COOKIE_NAME, cookie.getName());
        assertTrue(cookie.isHttpOnly(), "必須 httpOnly，否則 XSS 可讀取 session");
        assertEquals("Lax", cookie.getSameSite());
        assertEquals("/", cookie.getPath());
        assertEquals(Duration.ofDays(28), cookie.getMaxAge());
    }

    /** 對外網址為 https 時 cookie 必須帶 Secure */
    @Test
    void secureFlagIsSetForHttpsBaseUrl() {
        assertTrue(httpsService().buildSessionCookie("dummy-jwt").isSecure());
    }

    /** 對外網址為 http（本機開發）時不得帶 Secure，否則瀏覽器會丟棄 cookie 導致永遠登不進去 */
    @Test
    void secureFlagIsOmittedForHttpBaseUrl() {
        ReaderSessionService local = new ReaderSessionService(SECRET, 28, "http://127.0.0.1:8080");

        assertFalse(local.buildSessionCookie("dummy-jwt").isSecure());
    }

    /** 清除用的 cookie 必須 maxAge=0 且值為空 */
    @Test
    void clearCookieExpiresImmediately() {
        ResponseCookie cookie = httpsService().buildClearCookie();

        assertEquals(ReaderSessionService.COOKIE_NAME, cookie.getName());
        assertEquals("", cookie.getValue());
        assertEquals(Duration.ZERO, cookie.getMaxAge());
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test -Dtest=ReaderSessionServiceTest
```

Expected: 編譯失敗，`cannot find symbol: class ReaderSessionService`

- [ ] **Step 3: 實作 ReaderSessionService**

Create `survey-backend/src/main/java/world/springai/survey/reader/ReaderSessionService.java`:

```java
package world.springai.survey.reader;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Optional;

/**
 * 讀者登入態：以 JWT 承載 reader id，放在 httpOnly cookie。
 *
 * <p>放 cookie 而非 localStorage 是因為 httpOnly 讓 XSS 無法讀取 session；
 * 前端與後端同源（同一個 Spring Boot），所以不需處理跨域 cookie。</p>
 */
@Service
public class ReaderSessionService {

    private static final Logger log = LoggerFactory.getLogger(ReaderSessionService.class);

    /** session cookie 名稱 */
    public static final String COOKIE_NAME = "reader_session";

    /** JWT 簽章金鑰（HS256） */
    private final SecretKey key;
    /** 登入態有效天數 */
    private final int ttlDays;
    /**
     * cookie 是否帶 Secure 旗標。
     * 依對外網址是否為 https 自動決定：Secure cookie 在 http 下會被瀏覽器丟棄，
     * 本機開發就永遠登不進去；改用自動判斷可避免多一個設定項又忘記在正式環境開啟。
     */
    private final boolean secureCookie;

    /** 注入 JWT 秘鑰、效期與對外網址 */
    public ReaderSessionService(@Value("${app.reader.jwt-secret}") String secret,
                                @Value("${app.reader.jwt-ttl-days}") int ttlDays,
                                @Value("${app.public-base-url}") String publicBaseUrl) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlDays = ttlDays;
        this.secureCookie = publicBaseUrl != null && publicBaseUrl.startsWith("https://");
    }

    /** 簽發 JWT，subject 為 reader id，效期為 ttlDays 天 */
    public String issueJwt(Long readerId, OffsetDateTime now) {
        return Jwts.builder()
            .subject(String.valueOf(readerId))
            .issuedAt(Date.from(now.toInstant()))
            .expiration(Date.from(now.plusDays(ttlDays).toInstant()))
            .signWith(key)
            .compact();
    }

    /**
     * 從 JWT 讀出 reader id；簽章不符、過期、格式錯誤一律回 empty。
     *
     * <p>刻意不拋例外：無效的 session 應被當成「未登入」處理，而不是讓請求變成 500。</p>
     */
    public Optional<Long> readReaderId(String jwt, OffsetDateTime now) {
        if (!StringUtils.hasText(jwt)) {
            return Optional.empty();
        }
        try {
            String subject = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(now.toInstant())) // 由呼叫端決定「現在」，方便測試過期
                .build()
                .parseSignedClaims(jwt)
                .getPayload()
                .getSubject();
            return Optional.of(Long.valueOf(subject));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("讀者 session 無效：{}", e.getMessage());
            return Optional.empty();
        }
    }

    /** 組 session cookie：httpOnly 防 XSS、SameSite=Lax 防 CSRF、Secure 依對外網址決定 */
    public ResponseCookie buildSessionCookie(String jwt) {
        return ResponseCookie.from(COOKIE_NAME, jwt)
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ofDays(ttlDays))
            .build();
    }

    /** 組登出用的清除 cookie（同名、空值、立即過期） */
    public ResponseCookie buildClearCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ZERO)
            .build();
    }
}
```

- [ ] **Step 4: 跑測試確認通過**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test -Dtest=ReaderSessionServiceTest
```

Expected: `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`

若 `expiredJwtIsRejected` 未通過，確認 `Jwts.parser()` 有串接 `.clock(...)`——jjwt 預設用系統時間，測試傳入的 `now` 會被忽略。

- [ ] **Step 5: 跑全部測試並 commit**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test
```

Expected: `Tests run: 119, Failures: 0, Errors: 0, Skipped: 0`（110 + 9）

```powershell
git add src/main/java/world/springai/survey/reader/ReaderSessionService.java src/test/java/world/springai/survey/reader/ReaderSessionServiceTest.java
git commit -m @'
feat(reader): ReaderSessionService —— JWT session 與 httpOnly cookie

- JWT 放 httpOnly cookie 而非 localStorage：XSS 無法讀取 session；
  前後端同源故不需處理跨域 cookie
- SameSite=Lax、path=/、maxAge 對齊 JWT 效期（28 天）
- Secure 旗標依 app.public-base-url 是否 https 自動決定：
  Secure cookie 在 http 下會被瀏覽器丟棄，本機開發就永遠登不進去；
  自動判斷可免掉多一個設定項又忘記在正式環境開啟
- 無效 session（篡改／過期／格式錯誤／換秘鑰）一律回 empty 當未登入，
  不讓請求變成 500
- parser 串接 .clock() 讓「現在」由呼叫端決定，過期行為才可測

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
'@
```

---

## Task 8: 名單中心查詢擴充 + ReaderAccountService + LoginMailService

**Files:**
- Modify: `survey-backend/src/main/java/world/springai/survey/audience/SurveyResponseRepository.java`
- Create: `survey-backend/src/main/java/world/springai/survey/reader/ReaderAccountService.java`
- Create: `survey-backend/src/main/java/world/springai/survey/reader/LoginMailService.java`
- Test: `survey-backend/src/test/java/world/springai/survey/reader/ReaderAccountServiceTest.java`
- Test: `survey-backend/src/test/java/world/springai/survey/reader/LoginMailServiceTest.java`

**Interfaces:**
- Consumes: `Reader`／`ReaderRepository`／`CreditTxn`／`CreditTxnRepository`（Task 5）、`LoginTokenService`（Task 6）、`AppSettingService`（Task 3）、`MailSender`／`EmailLog`／`EmailLogRepository`（`mail` package）
- Produces:
  - `SurveyResponseRepository.isSubscribed(String email) → boolean`
  - `SurveyResponseRepository.touchEngagement(String email, OffsetDateTime at) → int`
  - `ReaderAccountService.findOrCreate(String email, OffsetDateTime now) → Reader`
  - `LoginMailService.sendLoginLink(String email, String redirect, OffsetDateTime now) → SendResult`（record：`boolean sent()`、`boolean throttled()`）
  - `LoginMailService.LOG_TYPE = "login"`

> **兩個關鍵設計點**
>
> 1. **登入信不套 `EmailTemplate.wrap()`。** 那個外框的頁腳寫「你會收到這封信，是因為你填寫了興趣調查並同意接收課程資訊」並附退訂連結，但登入信是**交易信**：讀者即使退訂了行銷信，仍然必須能登入看他解鎖過的文章。給交易信附退訂連結是把兩種同意混為一談。
> 2. **寄送失敗必須讓呼叫端知道**（spec §6）。這點與 `WelcomeMailService` 刻意吞例外的做法相反——歡迎信晚到沒差，但讀者正在等登入信，顯示成功假象會讓他一直重試。

- [ ] **Step 1: 為 SurveyResponseRepository 加兩個查詢**

Modify `survey-backend/src/main/java/world/springai/survey/audience/SurveyResponseRepository.java`，在 `findDistinctRecipients()` 之後加入：

```java
    /**
     * 該 email 是否為已確認訂閱者（同意且未退訂）。
     *
     * <p>讀者端的授權判斷用它——訂閱狀態只有名單中心這一份真相，
     * reader 表刻意不自帶訂閱狀態。</p>
     */
    @Query("""
        select count(s) > 0 from SurveyResponse s
         where lower(s.email) = lower(:email)
           and s.consent = true
           and s.unsubscribed = false
        """)
    boolean isSubscribed(@Param("email") String email);

    /**
     * 更新最後互動時間（供參與度分級使用）。
     *
     * <p>高可靠互動訊號：確認訂閱、登入、解鎖文章、更新個人資料。
     * 開信是低可靠訊號（信箱常封鎖圖片）但同樣會更新。</p>
     *
     * @return 受影響筆數；0 表示該 email 不在名單中（讀者可能尚未訂閱）
     */
    @Modifying
    @Transactional
    @Query("update SurveyResponse s set s.lastEngagedAt = :at where lower(s.email) = lower(:email)")
    int touchEngagement(@Param("email") String email, @Param("at") OffsetDateTime at);
```

並在 import 區塊補上：

```java
import java.time.OffsetDateTime;
```

- [ ] **Step 2: 寫 ReaderAccountService 的失敗測試**

Create `survey-backend/src/test/java/world/springai/survey/reader/ReaderAccountServiceTest.java`:

```java
package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import world.springai.survey.AppSettingService;
import world.springai.survey.audience.SurveyResponseRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ReaderAccountService 行為測試：首次建帳發初始贈點、既有帳戶不重複發、邀請碼不碰撞 */
class ReaderAccountServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-25T12:00:00+08:00");

    private ReaderRepository readerRepository;
    private CreditTxnRepository creditTxnRepository;
    private SurveyResponseRepository surveyResponseRepository;
    private AppSettingService appSettingService;
    private ReaderAccountService service;

    @BeforeEach
    void setUp() {
        readerRepository = mock(ReaderRepository.class);
        creditTxnRepository = mock(CreditTxnRepository.class);
        surveyResponseRepository = mock(SurveyResponseRepository.class);
        appSettingService = mock(AppSettingService.class);
        when(appSettingService.getInt(eq(AppSettingService.CREDIT_SIGNUP_GRANT), anyInt())).thenReturn(300);
        // save 回傳帶 id 的物件，模擬資料庫產生主鍵
        when(readerRepository.save(any(Reader.class))).thenAnswer(i -> {
            Reader r = i.getArgument(0);
            if (r.getId() == null) {
                r.setId(1L);
            }
            return r;
        });
        service = new ReaderAccountService(readerRepository, creditTxnRepository,
            surveyResponseRepository, appSettingService);
    }

    /** 首次登入：建立帳戶、發初始贈點、餘額同步為贈點數 */
    @Test
    void firstLoginCreatesAccountWithSignupGrant() {
        when(readerRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());

        Reader reader = service.findOrCreate("user@example.com", NOW);

        assertEquals("user@example.com", reader.getEmail());
        assertEquals(300, reader.getCredits(), "餘額應同步為初始贈點");
        assertNotNull(reader.getReferralCode());

        ArgumentCaptor<CreditTxn> txn = ArgumentCaptor.forClass(CreditTxn.class);
        verify(creditTxnRepository).save(txn.capture());
        assertEquals(300, txn.getValue().getDelta());
        assertEquals(CreditTxn.REASON_SIGNUP_GRANT, txn.getValue().getReason());
    }

    /** 初始贈點金額取自可調參數，不寫死 */
    @Test
    void signupGrantAmountComesFromSettings() {
        when(appSettingService.getInt(eq(AppSettingService.CREDIT_SIGNUP_GRANT), anyInt())).thenReturn(150);
        when(readerRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());

        Reader reader = service.findOrCreate("user@example.com", NOW);

        assertEquals(150, reader.getCredits());
    }

    /** 既有帳戶再次登入：不得重複發贈點 */
    @Test
    void existingAccountDoesNotReceiveGrantAgain() {
        Reader existing = new Reader("user@example.com", "OLDCODE1");
        existing.setId(7L);
        existing.setCredits(120);
        when(readerRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(existing));

        Reader reader = service.findOrCreate("user@example.com", NOW);

        assertEquals(7L, reader.getId());
        assertEquals(120, reader.getCredits(), "餘額不得被重設");
        verify(creditTxnRepository, never()).save(any(CreditTxn.class));
    }

    /** email 一律正規化為小寫後才查詢與建立 */
    @Test
    void emailIsNormalisedToLowerCase() {
        when(readerRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());

        Reader reader = service.findOrCreate("  User@EXAMPLE.com  ", NOW);

        assertEquals("user@example.com", reader.getEmail());
    }

    /** 邀請碼碰撞時要重新產生，不得直接寫入造成 UNIQUE 衝突 */
    @Test
    void referralCodeCollisionTriggersRetry() {
        when(readerRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        // 前兩次都說碼已存在，第三次才放行
        when(readerRepository.existsByReferralCode(anyString()))
            .thenReturn(true, true, false);

        Reader reader = service.findOrCreate("user@example.com", NOW);

        assertNotNull(reader.getReferralCode());
        verify(readerRepository, times(3)).existsByReferralCode(anyString());
    }

    /** 邀請碼不含容易看錯的字元（0/O、1/I/L），因為讀者會口頭或手抄傳播 */
    @Test
    void referralCodeAvoidsAmbiguousCharacters() {
        when(readerRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(readerRepository.existsByReferralCode(anyString())).thenReturn(false);

        for (int i = 0; i < 50; i++) {
            String code = service.findOrCreate("user" + i + "@example.com", NOW).getReferralCode();
            assertTrue(code.matches("[A-HJ-NP-Z2-9]{8}"),
                "邀請碼 " + code + " 含有易混淆字元或長度不符");
        }
    }

    /** 登入是高可靠互動訊號，必須更新名單中心的 last_engaged_at */
    @Test
    void loginTouchesEngagementTimestamp() {
        when(readerRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());

        service.findOrCreate("user@example.com", NOW);

        verify(surveyResponseRepository).touchEngagement("user@example.com", NOW);
    }

    /** 每次登入都要更新 last_login_at */
    @Test
    void loginUpdatesLastLoginAt() {
        Reader existing = new Reader("user@example.com", "OLDCODE1");
        existing.setId(7L);
        when(readerRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(existing));

        Reader reader = service.findOrCreate("user@example.com", NOW);

        assertEquals(NOW, reader.getLastLoginAt());
    }
}
```

- [ ] **Step 3: 跑測試確認失敗**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test -Dtest=ReaderAccountServiceTest
```

Expected: 編譯失敗，`cannot find symbol: class ReaderAccountService`

- [ ] **Step 4: 實作 ReaderAccountService**

Create `survey-backend/src/main/java/world/springai/survey/reader/ReaderAccountService.java`:

```java
package world.springai.survey.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.springai.survey.AppSettingService;
import world.springai.survey.audience.SurveyResponseRepository;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 讀者帳戶的建立與登入紀錄。
 *
 * <p>首次登入時建立帳戶並發放初始贈點；帳戶建立與發點在同一交易內完成，
 * 避免出現「有帳戶但沒有對應帳本紀錄」的不一致狀態。</p>
 */
@Service
public class ReaderAccountService {

    private static final Logger log = LoggerFactory.getLogger(ReaderAccountService.class);

    /**
     * 邀請碼字元集：刻意排除 0/O、1/I/L 等易混淆字元。
     * 讀者會口頭轉述或手抄邀請碼，看錯一個字就換成別人的推薦人。
     */
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    /** 邀請碼長度 */
    private static final int CODE_LENGTH = 8;

    /** 邀請碼碰撞重試上限，避免字元集耗盡時無限迴圈 */
    private static final int MAX_CODE_ATTEMPTS = 10;

    /** 初始贈點的預設值；實際值取自 app_setting，此為查不到時的後備 */
    private static final int DEFAULT_SIGNUP_GRANT = 300;

    private final SecureRandom random = new SecureRandom();

    private final ReaderRepository readerRepository;
    private final CreditTxnRepository creditTxnRepository;
    private final SurveyResponseRepository surveyResponseRepository;
    private final AppSettingService appSettingService;

    /** 注入讀者、帳本、名單中心與參數服務 */
    public ReaderAccountService(ReaderRepository readerRepository,
                               CreditTxnRepository creditTxnRepository,
                               SurveyResponseRepository surveyResponseRepository,
                               AppSettingService appSettingService) {
        this.readerRepository = readerRepository;
        this.creditTxnRepository = creditTxnRepository;
        this.surveyResponseRepository = surveyResponseRepository;
        this.appSettingService = appSettingService;
    }

    /**
     * 取得讀者帳戶，不存在則建立。
     *
     * <p>首次建立時發放初始贈點（金額取自可調參數）；既有帳戶不重複發。
     * 無論新舊都更新最後登入時間，並更新名單中心的最後互動時間
     * （登入是高可靠的參與度訊號）。</p>
     */
    @Transactional
    public Reader findOrCreate(String email, OffsetDateTime now) {
        String normalized = normalize(email);

        Optional<Reader> existing = readerRepository.findByEmailIgnoreCase(normalized);
        Reader reader = existing.orElseGet(() -> createWithSignupGrant(normalized, now));

        reader.setLastLoginAt(now);
        reader = readerRepository.save(reader);

        // 更新名單中心的參與度時間戳；該 email 不在名單中時回 0，屬正常情形
        surveyResponseRepository.touchEngagement(normalized, now);

        return reader;
    }

    /** 建立新帳戶並發放初始贈點；餘額與帳本在同一交易內同步 */
    private Reader createWithSignupGrant(String email, OffsetDateTime now) {
        Reader reader = new Reader(email, generateUniqueReferralCode());
        reader = readerRepository.save(reader);

        int grant = appSettingService.getInt(AppSettingService.CREDIT_SIGNUP_GRANT, DEFAULT_SIGNUP_GRANT);
        creditTxnRepository.save(new CreditTxn(
            reader.getId(), grant, CreditTxn.REASON_SIGNUP_GRANT, null, "首次登入初始贈點"));
        reader.setCredits(grant);

        log.info("建立讀者帳戶 {} 並發放初始贈點 {} 點", email, grant);
        return reader;
    }

    /** 產生未被使用的邀請碼；碰撞則重試，超過上限拋例外（幾乎不可能發生） */
    private String generateUniqueReferralCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = randomCode();
            if (!readerRepository.existsByReferralCode(code)) {
                return code;
            }
            log.warn("邀請碼碰撞（第 {} 次嘗試）：{}", attempt + 1, code);
        }
        throw new IllegalStateException("連續 " + MAX_CODE_ATTEMPTS + " 次都無法產生未使用的邀請碼");
    }

    /** 從不含易混淆字元的字元集抽出固定長度的碼 */
    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    /** email 正規化：去前後空白並轉小寫 */
    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
```

- [ ] **Step 5: 跑測試確認通過**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test -Dtest=ReaderAccountServiceTest
```

Expected: `Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 6: 寫 LoginMailService 的失敗測試**

Create `survey-backend/src/test/java/world/springai/survey/reader/LoginMailServiceTest.java`:

```java
package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.MailSender;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** LoginMailService 行為測試：magic link 內容、節流、失敗回報、不含退訂連結 */
class LoginMailServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-25T12:00:00+08:00");

    private LoginTokenService tokenService;
    private MailSender mailSender;
    private EmailLogRepository emailLogRepository;
    private LoginMailService service;

    @BeforeEach
    void setUp() {
        tokenService = mock(LoginTokenService.class);
        mailSender = mock(MailSender.class);
        emailLogRepository = mock(EmailLogRepository.class);
        service = new LoginMailService(tokenService, mailSender, emailLogRepository,
            "https://news.example.com");
    }

    /** 正常寄送：信中含帶 token 的登入連結 */
    @Test
    void sendsMailWithMagicLink() {
        when(tokenService.isThrottled("user@example.com", NOW)).thenReturn(false);
        when(tokenService.issue("user@example.com", NOW)).thenReturn("RAW-TOKEN-123");
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        LoginMailService.SendResult result = service.sendLoginLink("user@example.com", null, NOW);

        assertTrue(result.sent());
        assertFalse(result.throttled());

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(eq("user@example.com"), anyString(), html.capture());
        assertTrue(html.getValue().contains("https://news.example.com/api/reader/login/verify?t=RAW-TOKEN-123"),
            "信中必須含帶 token 的登入連結");
    }

    /** 交易信不得含退訂連結：讀者退訂行銷信後仍須能登入看已解鎖的文章 */
    @Test
    void loginMailDoesNotContainUnsubscribeLink() {
        when(tokenService.isThrottled(anyString(), any())).thenReturn(false);
        when(tokenService.issue(anyString(), any())).thenReturn("RAW-TOKEN-123");
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        service.sendLoginLink("user@example.com", null, NOW);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(anyString(), anyString(), html.capture());
        assertFalse(html.getValue().contains("unsubscribe"), "登入信是交易信，不得含退訂連結");
        assertFalse(html.getValue().contains("取消訂閱"), "登入信是交易信，不得含退訂字樣");
    }

    /** redirect 參數要帶進連結並做 URL 編碼 */
    @Test
    void redirectIsAppendedAndEncoded() {
        when(tokenService.isThrottled(anyString(), any())).thenReturn(false);
        when(tokenService.issue(anyString(), any())).thenReturn("TOK");
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        service.sendLoginLink("user@example.com", "/r/news/hello-world", NOW);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(anyString(), anyString(), html.capture());
        assertTrue(html.getValue().contains("redirect=%2Fr%2Fnews%2Fhello-world"),
            "redirect 必須經過 URL 編碼");
    }

    /** 節流時不簽發 token、不寄信，並回報 throttled */
    @Test
    void throttledRequestDoesNotSendOrIssueToken() {
        when(tokenService.isThrottled("user@example.com", NOW)).thenReturn(true);

        LoginMailService.SendResult result = service.sendLoginLink("user@example.com", null, NOW);

        assertFalse(result.sent());
        assertTrue(result.throttled());
        verify(tokenService, never()).issue(anyString(), any());
        verify(mailSender, never()).send(anyString(), anyString(), anyString());
    }

    /** 寄送成功要寫 email_log，type=login */
    @Test
    void logsSuccessfulSend() {
        when(tokenService.isThrottled(anyString(), any())).thenReturn(false);
        when(tokenService.issue(anyString(), any())).thenReturn("TOK");
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-9");

        service.sendLoginLink("user@example.com", null, NOW);

        ArgumentCaptor<EmailLog> logCaptor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(logCaptor.capture());
        assertEquals(LoginMailService.LOG_TYPE, logCaptor.getValue().getType());
        assertEquals("sent", logCaptor.getValue().getStatus());
    }

    /**
     * 寄送失敗必須回報 sent=false（與 WelcomeMailService 吞例外的做法相反）。
     * 讀者正在等這封信，顯示成功假象會讓他一直重試。
     */
    @Test
    void failedSendIsReportedAndLogged() {
        when(tokenService.isThrottled(anyString(), any())).thenReturn(false);
        when(tokenService.issue(anyString(), any())).thenReturn("TOK");
        when(mailSender.send(anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("provider down"));

        LoginMailService.SendResult result = service.sendLoginLink("user@example.com", null, NOW);

        assertFalse(result.sent(), "寄送失敗不得回報成功");
        assertFalse(result.throttled());

        ArgumentCaptor<EmailLog> logCaptor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(logCaptor.capture());
        assertEquals("failed", logCaptor.getValue().getStatus());
    }

    /** 站外 redirect 必須被丟棄，避免變成開放式轉址 */
    @Test
    void externalRedirectIsRejected() {
        when(tokenService.isThrottled(anyString(), any())).thenReturn(false);
        when(tokenService.issue(anyString(), any())).thenReturn("TOK");
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("msg-1");

        service.sendLoginLink("user@example.com", "https://evil.example.com/steal", NOW);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(anyString(), anyString(), html.capture());
        assertFalse(html.getValue().contains("evil.example.com"), "站外 redirect 必須被丟棄");
        assertFalse(html.getValue().contains("redirect="), "無效 redirect 不應出現在連結中");
    }
}
```

- [ ] **Step 7: 實作 LoginMailService**

Create `survey-backend/src/main/java/world/springai/survey/reader/LoginMailService.java`:

```java
package world.springai.survey.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.MailSender;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

/**
 * 寄送 magic link 登入信。
 *
 * <p><b>刻意不使用 EmailTemplate.wrap()</b>：那個外框的頁腳寫「你會收到這封信，
 * 是因為你填寫了興趣調查並同意接收課程資訊」並附退訂連結。但登入信是**交易信**——
 * 讀者即使退訂了行銷信，仍然必須能登入看他已解鎖的文章。給交易信附退訂連結
 * 是把兩種同意混為一談。</p>
 *
 * <p><b>失敗必須回報</b>（spec §6）：與 WelcomeMailService 刻意吞例外的做法相反。
 * 歡迎信晚到沒差，但讀者正在等登入信，顯示成功假象會讓他一直重試。</p>
 */
@Service
public class LoginMailService {

    private static final Logger log = LoggerFactory.getLogger(LoginMailService.class);

    /** email_log 的信件類型 */
    public static final String LOG_TYPE = "login";

    /** 登入信主旨 */
    private static final String SUBJECT = "你的登入連結";

    private final LoginTokenService tokenService;
    private final MailSender mailSender;
    private final EmailLogRepository emailLogRepository;
    /** 組登入連結用的對外網址 */
    private final String publicBaseUrl;

    /** 注入 token 服務、寄信、寄送記錄與對外網址 */
    public LoginMailService(LoginTokenService tokenService,
                           MailSender mailSender,
                           EmailLogRepository emailLogRepository,
                           @Value("${app.public-base-url}") String publicBaseUrl) {
        this.tokenService = tokenService;
        this.mailSender = mailSender;
        this.emailLogRepository = emailLogRepository;
        this.publicBaseUrl = publicBaseUrl;
    }

    /**
     * 寄送結果。
     *
     * @param sent      是否成功寄出
     * @param throttled 是否因節流而未寄（前端要顯示不同訊息）
     */
    public record SendResult(boolean sent, boolean throttled) {}

    /**
     * 寄一封登入信。
     *
     * @param redirect 登入成功後要回到的站內路徑；站外網址一律丟棄（防開放式轉址）
     */
    public SendResult sendLoginLink(String email, String redirect, OffsetDateTime now) {
        if (tokenService.isThrottled(email, now)) {
            log.info("登入信節流，暫不寄送 to={}", email);
            return new SendResult(false, true);
        }

        String rawToken = tokenService.issue(email, now);
        String link = buildLoginLink(rawToken, redirect);
        String html = buildHtml(link);

        try {
            String providerId = mailSender.send(email, SUBJECT, html);
            saveLog(email, providerId, "sent", null);
            return new SendResult(true, false);
        } catch (Exception e) {
            log.warn("登入信寄送失敗 to={}：{}", email, e.getMessage());
            saveLog(email, null, "failed", e.getMessage());
            return new SendResult(false, false);
        }
    }

    /** 組登入連結；只接受站內相對路徑作為 redirect */
    private String buildLoginLink(String rawToken, String redirect) {
        StringBuilder link = new StringBuilder(publicBaseUrl)
            .append("/api/reader/login/verify?t=")
            .append(URLEncoder.encode(rawToken, StandardCharsets.UTF_8));
        if (isSafeRedirect(redirect)) {
            link.append("&redirect=").append(URLEncoder.encode(redirect, StandardCharsets.UTF_8));
        }
        return link.toString();
    }

    /**
     * 只允許站內相對路徑。
     *
     * <p>必須排除 {@code //evil.com} 這種 protocol-relative 網址——它以 / 開頭，
     * 但瀏覽器會當成站外網址處理。</p>
     */
    private boolean isSafeRedirect(String redirect) {
        return StringUtils.hasText(redirect)
            && redirect.startsWith("/")
            && !redirect.startsWith("//");
    }

    /** 組登入信 HTML；刻意不含退訂連結（交易信） */
    private String buildHtml(String loginLink) {
        return """
            <div style="font-family:system-ui,'Microsoft JhengHei',sans-serif;line-height:1.7;max-width:560px;margin:0 auto;color:#102033">
              <h2>點下面的按鈕就能登入</h2>
              <p>這個連結 15 分鐘內有效，而且只能使用一次。</p>
              <p style="text-align:center;margin:28px 0">
                <a href="%s" style="background:#0d9488;color:#fff;padding:12px 28px;border-radius:8px;text-decoration:none;font-weight:700">登入電子報</a>
              </p>
              <hr style="border:none;border-top:1px solid #dce5ee;margin:24px 0">
              <p style="color:#8190a3;font-size:.85rem">
                如果不是你要求登入的，直接忽略這封信即可——沒有人能在未點擊此連結的情況下進入你的帳戶。
              </p>
            </div>
            """.formatted(loginLink);
    }

    /** 寫一筆寄送記錄；記錄失敗只記 log，不影響回報給呼叫端的結果 */
    private void saveLog(String email, String providerId, String status, String error) {
        try {
            emailLogRepository.save(new EmailLog(email, SUBJECT, LOG_TYPE, providerId, status, error));
        } catch (Exception e) {
            log.warn("寫入 email_log 失敗 to={}：{}", email, e.getMessage());
        }
    }
}
```

- [ ] **Step 8: 跑測試確認通過**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test -Dtest=LoginMailServiceTest
```

Expected: `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 9: 跑全部測試並 commit**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test
```

Expected: `Tests run: 134, Failures: 0, Errors: 0, Skipped: 0`（119 + 8 + 7）

```powershell
git add src/main/java/world/springai/survey/audience/SurveyResponseRepository.java src/main/java/world/springai/survey/reader/ReaderAccountService.java src/main/java/world/springai/survey/reader/LoginMailService.java src/test/java/world/springai/survey/reader/ReaderAccountServiceTest.java src/test/java/world/springai/survey/reader/LoginMailServiceTest.java
git commit -m @'
feat(reader): 讀者帳戶建立與 magic link 登入信

SurveyResponseRepository 擴充（訂閱狀態只有名單中心一份真相）：
- isSubscribed(email)：授權判斷用
- touchEngagement(email, at)：參與度時間戳

ReaderAccountService：
- 首次登入建帳戶 + 發初始贈點，同一交易避免「有帳戶無帳本」
- 贈點金額取自 app_setting，不寫死
- 邀請碼排除 0/O、1/I/L 等易混淆字元（讀者會口頭轉述或手抄），
  碰撞重試上限 10 次
- 登入同時更新名單中心的 last_engaged_at

LoginMailService：
- 刻意不套 EmailTemplate.wrap()：那個外框帶退訂連結，但登入信是
  交易信——讀者退訂行銷信後仍須能登入看已解鎖的文章
- 寄送失敗回報 sent=false（與 WelcomeMailService 吞例外相反）：
  讀者正在等信，成功假象會讓他一直重試
- redirect 只接受站內相對路徑，並排除 //evil.com 這種
  protocol-relative 網址（防開放式轉址）

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
'@
```

---

## Task 9: Campaign 擴充與 archive 查詢

**Files:**
- Modify: `survey-backend/src/main/java/world/springai/survey/newsletter/Campaign.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/newsletter/CampaignRepository.java`
- Test: `survey-backend/src/test/java/world/springai/survey/newsletter/CampaignTierTest.java`

**Interfaces:**
- Consumes: V8 的 campaign 新欄位（Task 2）
- Produces:
  - `Campaign`：`getTier/setTier`、`getCreditCost/setCreditCost`、`getSlug/setSlug`、`getPublishedAt/setPublishedAt`、`isVipFullInMail/setVipFullInMail`、`getFilterLevels/setFilterLevels`
  - 常數 `Campaign.TIER_BASIC = "BASIC"`、`Campaign.TIER_PREMIUM = "PREMIUM"`
  - 方法 `Campaign.isPremium()`、`Campaign.isPublished()`
  - `CampaignRepository.findByPublishedAtIsNotNullOrderByPublishedAtDesc() → List<Campaign>`
  - `CampaignRepository.findBySlug(String slug) → Optional<Campaign>`

- [ ] **Step 1: 寫失敗的測試**

Create `survey-backend/src/test/java/world/springai/survey/newsletter/CampaignTierTest.java`:

```java
package world.springai.survey.newsletter;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Campaign 內容分級與發布狀態的行為測試 */
class CampaignTierTest {

    /** 建立一筆最小可用的 campaign */
    private Campaign campaign() {
        return new Campaign("主旨", "# 內容", "<h1>內容</h1>",
            null, null, "now", null, 0, "sent");
    }

    /** 新建的 campaign 預設為 BASIC、0 點、未發布（與 V8 的 DEFAULT 一致） */
    @Test
    void defaultsToBasicUnpublished() {
        Campaign c = campaign();

        assertEquals(Campaign.TIER_BASIC, c.getTier());
        assertEquals(0, c.getCreditCost());
        assertFalse(c.isPremium());
        assertFalse(c.isPublished(), "未設 publishedAt 即未發布，不出現在 archive");
        assertFalse(c.isVipFullInMail(), "第一版預設所有人信件都折疊");
        assertEquals("active", c.getFilterLevels(), "預設只寄給 active 級別");
    }

    /** 設為 PREMIUM 後 isPremium 為 true */
    @Test
    void premiumTierIsRecognised() {
        Campaign c = campaign();
        c.setTier(Campaign.TIER_PREMIUM);
        c.setCreditCost(10);

        assertTrue(c.isPremium());
        assertEquals(10, c.getCreditCost());
    }

    /** 設了 publishedAt 才算已發布 */
    @Test
    void publishedAtDeterminesPublishState() {
        Campaign c = campaign();
        assertFalse(c.isPublished());

        c.setPublishedAt(OffsetDateTime.parse("2026-07-25T12:00:00+08:00"));
        assertTrue(c.isPublished());
    }

    /** slug 可設定，供 /r/news/{slug} 使用 */
    @Test
    void slugIsSettable() {
        Campaign c = campaign();
        c.setSlug("hello-world");

        assertEquals("hello-world", c.getSlug());
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test -Dtest=CampaignTierTest
```

Expected: 編譯失敗，`cannot find symbol: method getTier()`

- [ ] **Step 3: 為 Campaign 補上六個欄位**

Modify `survey-backend/src/main/java/world/springai/survey/newsletter/Campaign.java`：

在類別開頭（`@Id` 之前）加入常數：

```java
    /** 基本內容：已確認訂閱者即可閱讀 */
    public static final String TIER_BASIC = "BASIC";
    /** 進階內容：需點數解鎖或 VIP 身分 */
    public static final String TIER_PREMIUM = "PREMIUM";
```

在 `createdAt` 欄位宣告之前加入六個新欄位：

```java
    /** 內容分級：BASIC 或 PREMIUM */
    @Column(nullable = false)
    private String tier = TIER_BASIC;

    /** PREMIUM 解鎖所需點數；BASIC 為 0。資料庫層有 CHECK 約束禁止 PREMIUM 卻為 0 */
    @Column(name = "credit_cost", nullable = false)
    private int creditCost = 0;

    /** 網頁網址片段，供 /r/news/{slug} 使用；NULL 表示不在 archive 中露出 */
    private String slug;

    /** 發布時間；非 NULL 才會出現在 archive */
    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    /** VIP 是否在信件中直接收到全文（階段 D 才會使用；第一版一律折疊） */
    @Column(name = "vip_full_in_mail", nullable = false)
    private boolean vipFullInMail = false;

    /** 本次寄送的參與度級別，逗號分隔（階段 F 才會使用），供補寄重建相同對象 */
    @Column(name = "filter_levels", nullable = false)
    private String filterLevels = "active";
```

在 getter 區塊加入：

```java
    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
    public int getCreditCost() { return creditCost; }
    public void setCreditCost(int creditCost) { this.creditCost = creditCost; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
    public boolean isVipFullInMail() { return vipFullInMail; }
    public void setVipFullInMail(boolean vipFullInMail) { this.vipFullInMail = vipFullInMail; }
    public String getFilterLevels() { return filterLevels; }
    public void setFilterLevels(String filterLevels) { this.filterLevels = filterLevels; }

    /** 是否為進階內容（需點數或 VIP） */
    public boolean isPremium() {
        return TIER_PREMIUM.equals(tier);
    }

    /** 是否已發布（未發布者不出現在 archive） */
    public boolean isPublished() {
        return publishedAt != null;
    }
```

- [ ] **Step 4: 為 CampaignRepository 加兩個查詢**

Modify `survey-backend/src/main/java/world/springai/survey/newsletter/CampaignRepository.java`，在既有方法後加入：

```java
    /** archive 列表：只列已發布者，新到舊 */
    List<Campaign> findByPublishedAtIsNotNullOrderByPublishedAtDesc();

    /** 依 slug 查單篇文章 */
    Optional<Campaign> findBySlug(String slug);
```

並補上 import：

```java
import java.util.Optional;
```

- [ ] **Step 5: 跑測試並 commit**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test
```

Expected: `Tests run: 138, Failures: 0, Errors: 0, Skipped: 0`（134 + 4）

```powershell
git add src/main/java/world/springai/survey/newsletter/Campaign.java src/main/java/world/springai/survey/newsletter/CampaignRepository.java src/test/java/world/springai/survey/newsletter/CampaignTierTest.java
git commit -m @'
feat(newsletter): Campaign 擴充為可在網頁閱讀的文章

- tier（BASIC/PREMIUM）、creditCost、slug、publishedAt、
  vipFullInMail、filterLevels 六個欄位，預設值與 V8 的 DEFAULT 一致
- isPremium() / isPublished() 便利方法
- archive 查詢只列已發布者（publishedAt 非 NULL），新到舊
- findBySlug 供 /r/news/{slug} 使用

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
'@
```

---

## Task 10: AccessDecisionService（唯一的授權決策點）

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/reader/AccessDecisionService.java`
- Test: `survey-backend/src/test/java/world/springai/survey/reader/AccessDecisionServiceTest.java`

**Interfaces:**
- Consumes: `Reader`（Task 5）、`Campaign`（Task 9）、`ArticleAccessRepository`（Task 5）、`AppSettingService`（Task 3）
- Produces:
  - `enum AccessDecisionService.Access { FULL, PARTIAL }`
  - `enum AccessDecisionService.Reason { NOT_LOGGED_IN, NOT_SUBSCRIBED, BASIC_OPEN, VIP, ALREADY_UNLOCKED, NEEDS_CREDITS }`
  - `record AccessDecisionService.Decision(Access access, Reason reason, int shortfall)`
  - `decide(Reader readerOrNull, boolean subscribed, Campaign campaign, OffsetDateTime now) → Decision`（**純函式，無副作用**）
  - `recordAccess(Reader reader, Campaign campaign, Decision decision) → void`（FULL 時記錄閱讀歷史）

> **對 spec §5.2 的實作調整**：spec 把「VIP → FULL 同時補寫 `article_access`」寫在決策規則裡，但那讓決策函式帶有寫入副作用——每個授權測試都得 mock 一個寫入，而且 `decide()` 被呼叫幾次就會寫幾次。這裡把 `decide()` 保持純函式、另設 `recordAccess()` 由 controller 在 FULL 時呼叫一次。行為相同，決策邏輯可獨立測試。
>
> **階段 B 的範圍**：`NEEDS_CREDITS` 一律回 PARTIAL，**不扣點**。扣點路徑（`article_access` 寫入 + 條件式 UPDATE）是階段 C 的工作。`shortfall` 欄位此時已回傳正確數值，供前端顯示「還差幾點」，但階段 B 的頁面只需顯示「進階內容即將開放」——因為 Global Constraints 規定階段 B 一律只發布 BASIC 文章。

- [ ] **Step 1: 寫失敗的測試**

Create `survey-backend/src/test/java/world/springai/survey/reader/AccessDecisionServiceTest.java`:

```java
package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import world.springai.survey.AppSettingService;
import world.springai.survey.newsletter.Campaign;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AccessDecisionService 行為測試。
 *
 * <p>每條路徑一個測試，含 VIP 已到期、餘額剛好、餘額少 1 點等邊界。
 * 授權是本階段最敏感的邏輯，路徑覆蓋必須完整。</p>
 */
class AccessDecisionServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-25T12:00:00+08:00");

    private ArticleAccessRepository articleAccessRepository;
    private AppSettingService appSettingService;
    private AccessDecisionService service;

    @BeforeEach
    void setUp() {
        articleAccessRepository = mock(ArticleAccessRepository.class);
        appSettingService = mock(AppSettingService.class);
        when(appSettingService.getInt(eq(AppSettingService.CREDIT_PREMIUM_COST), anyInt())).thenReturn(10);
        service = new AccessDecisionService(articleAccessRepository, appSettingService);
    }

    /** 建立一篇文章 */
    private Campaign article(String tier, int cost) {
        Campaign c = new Campaign("主旨", "# 內容", "<h1>內容</h1>", null, null, "now", null, 0, "sent");
        c.setTier(tier);
        c.setCreditCost(cost);
        c.setPublishedAt(NOW.minusDays(1));
        return c;
    }

    /** 建立一位讀者 */
    private Reader reader(String tier, int credits) {
        Reader r = new Reader("user@example.com", "CODE1234");
        r.setId(1L);
        r.setTier(tier);
        r.setCredits(credits);
        return r;
    }

    /** 未登入：一律 PARTIAL，即使文章是 BASIC */
    @Test
    void notLoggedInGetsPartial() {
        AccessDecisionService.Decision d =
            service.decide(null, false, article(Campaign.TIER_BASIC, 0), NOW);

        assertEquals(AccessDecisionService.Access.PARTIAL, d.access());
        assertEquals(AccessDecisionService.Reason.NOT_LOGGED_IN, d.reason());
    }

    /** 已登入但未確認訂閱：PARTIAL（訂閱狀態來自名單中心，不是 reader 表） */
    @Test
    void loggedInButNotSubscribedGetsPartial() {
        AccessDecisionService.Decision d =
            service.decide(reader(Reader.TIER_FREE, 300), false, article(Campaign.TIER_BASIC, 0), NOW);

        assertEquals(AccessDecisionService.Access.PARTIAL, d.access());
        assertEquals(AccessDecisionService.Reason.NOT_SUBSCRIBED, d.reason());
    }

    /** 已確認訂閱 + BASIC 文章：FULL */
    @Test
    void subscribedReaderGetsFullBasicArticle() {
        AccessDecisionService.Decision d =
            service.decide(reader(Reader.TIER_FREE, 0), true, article(Campaign.TIER_BASIC, 0), NOW);

        assertEquals(AccessDecisionService.Access.FULL, d.access());
        assertEquals(AccessDecisionService.Reason.BASIC_OPEN, d.reason());
    }

    /** 有效 VIP + PREMIUM 文章：FULL，且不需要查解鎖紀錄 */
    @Test
    void activeVipGetsFullPremiumArticle() {
        Reader vip = reader(Reader.TIER_VIP, 0);
        vip.setVipExpiresAt(NOW.plusDays(30));

        AccessDecisionService.Decision d =
            service.decide(vip, true, article(Campaign.TIER_PREMIUM, 10), NOW);

        assertEquals(AccessDecisionService.Access.FULL, d.access());
        assertEquals(AccessDecisionService.Reason.VIP, d.reason());
    }

    /** VIP 已到期 + PREMIUM：視為 FREE，餘額不足則 PARTIAL */
    @Test
    void expiredVipFallsBackToFreeRules() {
        Reader expired = reader(Reader.TIER_VIP, 5);
        expired.setVipExpiresAt(NOW.minusDays(1));
        when(articleAccessRepository.existsByReaderIdAndCampaignId(1L, null)).thenReturn(false);

        AccessDecisionService.Decision d =
            service.decide(expired, true, article(Campaign.TIER_PREMIUM, 10), NOW);

        assertEquals(AccessDecisionService.Access.PARTIAL, d.access());
        assertEquals(AccessDecisionService.Reason.NEEDS_CREDITS, d.reason());
    }

    /** 已解鎖過的 PREMIUM：FULL 且不重複扣點（一次解鎖永久可讀的承諾） */
    @Test
    void alreadyUnlockedArticleStaysFull() {
        Campaign premium = article(Campaign.TIER_PREMIUM, 10);
        when(articleAccessRepository.existsByReaderIdAndCampaignId(1L, premium.getId())).thenReturn(true);

        AccessDecisionService.Decision d = service.decide(reader(Reader.TIER_FREE, 0), true, premium, NOW);

        assertEquals(AccessDecisionService.Access.FULL, d.access());
        assertEquals(AccessDecisionService.Reason.ALREADY_UNLOCKED, d.reason());
    }

    /** 餘額不足：PARTIAL 並回報還差幾點 */
    @Test
    void insufficientCreditsReportsShortfall() {
        AccessDecisionService.Decision d =
            service.decide(reader(Reader.TIER_FREE, 4), true, article(Campaign.TIER_PREMIUM, 10), NOW);

        assertEquals(AccessDecisionService.Access.PARTIAL, d.access());
        assertEquals(AccessDecisionService.Reason.NEEDS_CREDITS, d.reason());
        assertEquals(6, d.shortfall(), "還差 6 點");
    }

    /** 餘額剛好等於成本：階段 B 尚未接上扣點，仍為 PARTIAL 但 shortfall 為 0 */
    @Test
    void exactCreditsInStageBStillPartialWithZeroShortfall() {
        AccessDecisionService.Decision d =
            service.decide(reader(Reader.TIER_FREE, 10), true, article(Campaign.TIER_PREMIUM, 10), NOW);

        assertEquals(AccessDecisionService.Access.PARTIAL, d.access(),
            "階段 B 未接扣點路徑，PREMIUM 對非 VIP 一律 PARTIAL");
        assertEquals(0, d.shortfall());
    }

    /** creditCost 為 0 的 PREMIUM（理論上被 DB CHECK 擋掉）改用參數預設值，不當成免費 */
    @Test
    void premiumWithZeroCostFallsBackToSettingValue() {
        AccessDecisionService.Decision d =
            service.decide(reader(Reader.TIER_FREE, 4), true, article(Campaign.TIER_PREMIUM, 0), NOW);

        assertEquals(AccessDecisionService.Access.PARTIAL, d.access());
        assertEquals(6, d.shortfall(), "應改用 app_setting 的 10 點計算，而非把 0 當免費");
    }

    /** recordAccess 只在 FULL 時寫入，且已存在紀錄時不重複寫 */
    @Test
    void recordAccessWritesOnlyOnceForFullDecision() {
        Campaign premium = article(Campaign.TIER_PREMIUM, 10);
        Reader vip = reader(Reader.TIER_VIP, 0);
        vip.setVipExpiresAt(NOW.plusDays(30));
        AccessDecisionService.Decision full = new AccessDecisionService.Decision(
            AccessDecisionService.Access.FULL, AccessDecisionService.Reason.VIP, 0);
        when(articleAccessRepository.existsByReaderIdAndCampaignId(anyLong(), any())).thenReturn(false);

        service.recordAccess(vip, premium, full);

        verify(articleAccessRepository).save(any(ArticleAccess.class));
    }

    /** recordAccess 對 PARTIAL 決策不得寫入 */
    @Test
    void recordAccessSkipsPartialDecision() {
        AccessDecisionService.Decision partial = new AccessDecisionService.Decision(
            AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NEEDS_CREDITS, 6);

        service.recordAccess(reader(Reader.TIER_FREE, 4), article(Campaign.TIER_PREMIUM, 10), partial);

        verify(articleAccessRepository, never()).save(any(ArticleAccess.class));
    }

    /** recordAccess 對已有紀錄者不重複寫入（避免 UNIQUE 衝突） */
    @Test
    void recordAccessSkipsWhenAlreadyRecorded() {
        Campaign premium = article(Campaign.TIER_PREMIUM, 10);
        AccessDecisionService.Decision full = new AccessDecisionService.Decision(
            AccessDecisionService.Access.FULL, AccessDecisionService.Reason.ALREADY_UNLOCKED, 0);
        when(articleAccessRepository.existsByReaderIdAndCampaignId(anyLong(), any())).thenReturn(true);

        service.recordAccess(reader(Reader.TIER_FREE, 0), premium, full);

        verify(articleAccessRepository, never()).save(any(ArticleAccess.class));
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test -Dtest=AccessDecisionServiceTest
```

Expected: 編譯失敗，`cannot find symbol: class AccessDecisionService`

- [ ] **Step 3: 實作 AccessDecisionService**

Create `survey-backend/src/main/java/world/springai/survey/reader/AccessDecisionService.java`:

```java
package world.springai.survey.reader;

import org.springframework.stereotype.Service;
import world.springai.survey.AppSettingService;
import world.springai.survey.newsletter.Campaign;

import java.time.OffsetDateTime;

/**
 * 閱讀授權的**唯一**決策點。
 *
 * <p>所有 controller 都只呼叫本類，不得自行判斷 tier、VIP 或餘額。授權規則散落在
 * 各個端點是這類系統最常見的腐化方式——改一次規則要改十個地方，漏一個就是漏洞。</p>
 *
 * <p>{@link #decide} 是純函式（不寫入任何東西），寫入閱讀歷史交給
 * {@link #recordAccess}。這與 spec §5.2 把「VIP 時補寫 article_access」寫在
 * 決策規則裡的描述略有不同：行為相同，但決策可獨立測試，且不會因為
 * decide() 被呼叫多次而重複寫入。</p>
 *
 * <p><b>階段 B 範圍</b>：扣點路徑尚未接上，PREMIUM 對非 VIP 一律 PARTIAL。
 * shortfall 已回傳正確值供前端顯示，實際扣點是階段 C 的工作。</p>
 */
@Service
public class AccessDecisionService {

    /** 可讀取的範圍 */
    public enum Access {
        /** 全文（含受限區） */
        FULL,
        /** 只有免費區 */
        PARTIAL
    }

    /** 判定原因，供前端顯示對應的提示與行動按鈕 */
    public enum Reason {
        /** 尚未登入 */
        NOT_LOGGED_IN,
        /** 已登入但未確認訂閱 */
        NOT_SUBSCRIBED,
        /** 基本內容，訂閱者免費 */
        BASIC_OPEN,
        /** 有效 VIP */
        VIP,
        /** 先前已解鎖 */
        ALREADY_UNLOCKED,
        /** 需要點數才能解鎖 */
        NEEDS_CREDITS
    }

    /**
     * 授權判定結果。
     *
     * @param access    可讀取範圍
     * @param reason    判定原因
     * @param shortfall 還差幾點才能解鎖；非 NEEDS_CREDITS 時為 0
     */
    public record Decision(Access access, Reason reason, int shortfall) {}

    /** PREMIUM 解鎖點數的後備預設值；實際值取自 app_setting */
    private static final int DEFAULT_PREMIUM_COST = 10;

    private final ArticleAccessRepository articleAccessRepository;
    private final AppSettingService appSettingService;

    /** 注入解鎖紀錄與參數服務 */
    public AccessDecisionService(ArticleAccessRepository articleAccessRepository,
                                AppSettingService appSettingService) {
        this.articleAccessRepository = articleAccessRepository;
        this.appSettingService = appSettingService;
    }

    /**
     * 判定該讀者對該文章的可讀範圍。純函式，不產生任何寫入。
     *
     * @param reader     讀者；null 表示未登入
     * @param subscribed 是否為已確認訂閱者（由名單中心提供，不從 reader 推導）
     */
    public Decision decide(Reader reader, boolean subscribed, Campaign campaign, OffsetDateTime now) {
        if (reader == null) {
            return new Decision(Access.PARTIAL, Reason.NOT_LOGGED_IN, 0);
        }
        if (!subscribed) {
            return new Decision(Access.PARTIAL, Reason.NOT_SUBSCRIBED, 0);
        }
        if (!campaign.isPremium()) {
            return new Decision(Access.FULL, Reason.BASIC_OPEN, 0);
        }
        if (reader.isActiveVip(now)) {
            return new Decision(Access.FULL, Reason.VIP, 0);
        }
        if (articleAccessRepository.existsByReaderIdAndCampaignId(reader.getId(), campaign.getId())) {
            return new Decision(Access.FULL, Reason.ALREADY_UNLOCKED, 0);
        }

        // 階段 B：不扣點，一律回 PARTIAL 並附上還差幾點（階段 C 會在此接上扣點路徑）
        int cost = resolveCost(campaign);
        int shortfall = Math.max(0, cost - reader.getCredits());
        return new Decision(Access.PARTIAL, Reason.NEEDS_CREDITS, shortfall);
    }

    /**
     * 記錄閱讀歷史：僅在 FULL 且尚無紀錄時寫入，cost 為 0（本階段不扣點）。
     *
     * <p>由 controller 在取得 FULL 決策後呼叫一次。已有紀錄時跳過，
     * 避免撞上 article_access 的 UNIQUE 約束。</p>
     */
    public void recordAccess(Reader reader, Campaign campaign, Decision decision) {
        if (decision.access() != Access.FULL || reader == null) {
            return;
        }
        if (articleAccessRepository.existsByReaderIdAndCampaignId(reader.getId(), campaign.getId())) {
            return;
        }
        articleAccessRepository.save(new ArticleAccess(reader.getId(), campaign.getId(), 0));
    }

    /**
     * 取得該文章的解鎖成本。
     *
     * <p>campaign.creditCost 為 0 時改用參數預設值——PREMIUM 卻成本為 0 理論上
     * 已被資料庫 CHECK 擋掉，但若真的出現，把它當成免費會讓進階內容全面外洩，
     * 所以這裡選擇保守處理。</p>
     */
    private int resolveCost(Campaign campaign) {
        if (campaign.getCreditCost() > 0) {
            return campaign.getCreditCost();
        }
        return appSettingService.getInt(AppSettingService.CREDIT_PREMIUM_COST, DEFAULT_PREMIUM_COST);
    }
}
```

- [ ] **Step 4: 跑測試確認通過**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test -Dtest=AccessDecisionServiceTest
```

Expected: `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: 跑全部測試並 commit**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test
```

Expected: `Tests run: 150, Failures: 0, Errors: 0, Skipped: 0`（138 + 12）

```powershell
git add src/main/java/world/springai/survey/reader/AccessDecisionService.java src/test/java/world/springai/survey/reader/AccessDecisionServiceTest.java
git commit -m @'
feat(reader): AccessDecisionService —— 唯一的授權決策點

- 六種 Reason 讓前端能顯示對應提示，不必自行推導狀態
- decide() 為純函式；寫入閱讀歷史分離到 recordAccess()
  （spec §5.2 把補寫 article_access 寫在決策裡，會讓每個授權測試
   都要 mock 寫入，且 decide 被呼叫幾次就寫幾次）
- 訂閱狀態由呼叫端從名單中心取得後傳入，不從 reader 推導
  （同意狀態只有一份真相）
- VIP 到期在判斷當下比對，不做自動降級排程
- PREMIUM 卻 creditCost=0 時改用參數值而非視為免費——DB CHECK
  理論上已擋掉，但真出現時當免費會讓進階內容全面外洩
- 階段 B 不接扣點：PREMIUM 對非 VIP 一律 PARTIAL，但已回傳
  正確的 shortfall 供前端顯示

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
'@
```

---

## Task 11: HtmlTemplate 與登入 API

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/reader/HtmlTemplate.java`
- Create: `survey-backend/src/main/java/world/springai/survey/reader/ReaderAuthController.java`
- Create: `survey-backend/src/main/resources/static/reader/reader.css`
- Create: `survey-backend/src/main/resources/static/reader/index.html`
- Create: `survey-backend/src/main/resources/static/reader/login.html`
- Test: `survey-backend/src/test/java/world/springai/survey/reader/HtmlTemplateTest.java`
- Test: `survey-backend/src/test/java/world/springai/survey/reader/ReaderAuthControllerTest.java`

**Interfaces:**
- Consumes: `LoginMailService`、`LoginTokenService`、`ReaderSessionService`、`ReaderAccountService`（Task 6–8）
- Produces:
  - `HtmlTemplate.render(String resourcePath, Map<String, String> replacements) → String`
  - `HtmlTemplate.escapeHtml(String text) → String`（static）
  - `POST /api/reader/login` → `{"sent":bool,"throttled":bool}`
  - `GET /api/reader/login/verify?t=&redirect=` → 302 + `Set-Cookie`
  - `POST /api/reader/logout` → 204 + 清除 cookie
  - `GET /api/reader/me` → `{"email":...,"tier":...,"credits":...,"referralCode":...}` 或 401
  - `GET /r/login` → login.html
  - `GET /r/` → index.html

> **拆成兩個 controller 的理由**：把頁面渲染與登入 API 放在同一個類會需要注入 10 個依賴（登入 4 個 + 內容 4 個 + 名單 2 個）。依 CLAUDE.md 的單一任務原則拆為 `ReaderAuthController`（本 task）與 `ReaderPageController`（Task 12），各約 5 個依賴。

- [ ] **Step 1: 寫 HtmlTemplate 的失敗測試**

Create `survey-backend/src/test/java/world/springai/survey/reader/HtmlTemplateTest.java`:

```java
package world.springai.survey.reader;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HtmlTemplate 行為測試：佔位符替換、HTML 跳脫、找不到資源時明確失敗 */
class HtmlTemplateTest {

    private final HtmlTemplate template = new HtmlTemplate();

    /** 佔位符會被替換掉，且替換後不留殘跡 */
    @Test
    void replacesPlaceholders() {
        String rendered = template.render("static/reader/login.html",
            Map.of("<!--PAGE_TITLE-->", "登入測試"));

        assertTrue(rendered.contains("登入測試"));
        assertFalse(rendered.contains("<!--PAGE_TITLE-->"), "佔位符不得殘留");
    }

    /** 找不到資源要明確拋例外，不可回空字串讓頁面靜默變空白 */
    @Test
    void missingResourceFailsLoudly() {
        assertThrows(IllegalStateException.class,
            () -> template.render("static/reader/does-not-exist.html", Map.of()));
    }

    /** HTML 跳脫：五個危險字元都要處理 */
    @Test
    void escapesDangerousCharacters() {
        assertEquals("&lt;script&gt;", HtmlTemplate.escapeHtml("<script>"));
        assertEquals("&amp;", HtmlTemplate.escapeHtml("&"));
        assertEquals("&quot;", HtmlTemplate.escapeHtml("\""));
        assertEquals("&#39;", HtmlTemplate.escapeHtml("'"));
    }

    /** & 必須先跳脫，否則會把後續產生的實體再次跳脫成 &amp;lt; */
    @Test
    void escapesAmpersandFirst() {
        assertEquals("&amp;lt;", HtmlTemplate.escapeHtml("&lt;"));
    }

    /** null 視為空字串 */
    @Test
    void escapeHandlesNull() {
        assertEquals("", HtmlTemplate.escapeHtml(null));
    }
}
```

- [ ] **Step 2: 實作 HtmlTemplate**

Create `survey-backend/src/main/java/world/springai/survey/reader/HtmlTemplate.java`:

```java
package world.springai.survey.reader;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 極簡的 HTML 佔位符替換，用於 server 端渲染讀者端頁面。
 *
 * <p>為什麼需要 server 渲染而不是「靜態頁 + fetch API」：spec §5.3 要求免費區
 * 可被搜尋引擎索引；更關鍵的是，只有 server 渲染才能讓「未授權者的回應完全不含
 * 受限區」在 HTTP 層次成立——若改由前端 fetch，API 就得回傳整篇內容再由 JS
 * 決定顯示哪段，受限區便出現在網路回應中，paywall 形同虛設。</p>
 *
 * <p>為什麼不引入 Thymeleaf：需求只是替換幾個佔位符，HTML 仍維護在 .html 檔中。
 * 為此加一個 template engine 依賴不划算。</p>
 *
 * <p><b>刻意不快取</b>：每次請求都重讀檔案。讀取 classpath 資源的成本遠低於
 * 一次資料庫查詢，而不快取讓開發時改 HTML 不必重啟——這不是高流量系統，
 * 用可觀測的微小成本換開發體驗是值得的。</p>
 */
@Component
public class HtmlTemplate {

    /**
     * 讀取資源並替換佔位符。
     *
     * <p><b>注意</b>：替換值會原樣插入 HTML。若值來自使用者輸入或需視為純文字，
     * 呼叫端必須先經過 {@link #escapeHtml}；已渲染完成的 HTML（如 markdown 輸出）
     * 才可直接傳入。</p>
     *
     * @param resourcePath classpath 路徑，如 {@code static/reader/article.html}
     */
    public String render(String resourcePath, Map<String, String> replacements) {
        String html = load(resourcePath);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            html = html.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return html;
    }

    /** 讀取 classpath 資源；找不到時明確拋例外，不回空字串讓頁面靜默變空白 */
    private String load(String resourcePath) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("找不到或無法讀取頁面資源：" + resourcePath, e);
        }
    }

    /**
     * 把文字跳脫成可安全插入 HTML 的形式。
     *
     * <p>{@code &} 必須最先處理，否則後續產生的實體會被再次跳脫
     * （{@code &lt;} 變成 {@code &amp;lt;}）。</p>
     */
    public static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
```

- [ ] **Step 3: 建立 reader.css**

Create `survey-backend/src/main/resources/static/reader/reader.css`:

```css
/* 讀者端共用樣式。設計系統 token 與 static/index.html、land-page 共用，確保視覺一致 */
:root {
  --font-main: "Inter", "Noto Sans TC", "Microsoft JhengHei", system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  --bg:#f7fafc; --fg:#102033; --muted:#5c6b7d; --muted-2:#8190a3;
  --surface:#ffffff; --surface-2:#f1f6f9; --surface-3:#e6edf3;
  --border:#dce5ee; --border-strong:#b9c7d7;
  --accent:#0d9488; --accent-deep:#0f766e; --accent-soft:#d9f3ef;
  --accent-2:#f59e0b; --accent-2-deep:#b45309; --accent-2-soft:#fff2cc;
  --ok:#16a34a; --err:#dc2626;
  --r-md:14px; --r-lg:22px; --r-pill:999px;
  --shadow-sm:0 10px 28px rgb(15 23 42 / .08);
  --shadow-md:0 18px 42px rgb(15 23 42 / .12);
}

* { box-sizing:border-box; }
html, body { max-width:100%; overflow-x:hidden; }
body { margin:0; font-family:var(--font-main); color:var(--fg); line-height:1.75; background:var(--bg); }
a { color:var(--accent-deep); }
img { max-width:100%; display:block; }

.wrap { max-width:760px; margin:0 auto; padding:0 18px 80px; }

/* 頁首 */
.site-head { border-bottom:1px solid var(--border); background:var(--surface); margin-bottom:32px; }
.site-head-inner { max-width:760px; margin:0 auto; padding:16px 18px; display:flex; align-items:center; justify-content:space-between; gap:12px; }
.site-head a.brand { font-weight:800; text-decoration:none; color:var(--fg); }
.site-head nav { display:flex; gap:16px; font-size:.92rem; }

/* 卡片 */
.card { background:var(--surface); border:1px solid var(--border); border-radius:var(--r-md); padding:22px; box-shadow:var(--shadow-sm); }

/* 按鈕 */
.btn { display:inline-block; background:var(--accent); color:#fff; border:0; padding:12px 26px;
       border-radius:8px; font-weight:700; font-size:1rem; cursor:pointer; text-decoration:none; font-family:inherit; }
.btn:hover { background:var(--accent-deep); }
.btn[disabled] { opacity:.6; cursor:not-allowed; }

/* 表單 */
input[type=email] { width:100%; padding:12px 14px; border:1px solid var(--border-strong); border-radius:8px;
                    font-size:1rem; font-family:inherit; }
.form-row { display:flex; gap:10px; flex-wrap:wrap; }
.form-row input { flex:1 1 240px; }

/* 訊息提示 */
.msg { margin-top:14px; padding:12px 14px; border-radius:8px; font-size:.94rem; display:none; }
.msg.show { display:block; }
.msg.ok { background:var(--accent-soft); color:var(--accent-deep); }
.msg.err { background:#fdecec; color:var(--err); }

/* archive 列表 */
.article-list { list-style:none; padding:0; margin:0; display:grid; gap:14px; }
.article-item { background:var(--surface); border:1px solid var(--border); border-radius:var(--r-md); padding:18px 20px; }
.article-item h2 { margin:0 0 6px; font-size:1.12rem; }
.article-item h2 a { text-decoration:none; color:var(--fg); }
.article-item h2 a:hover { color:var(--accent-deep); }
.article-meta { color:var(--muted); font-size:.86rem; display:flex; gap:10px; flex-wrap:wrap; align-items:center; }
.tag { font-size:.75rem; font-weight:800; padding:3px 10px; border-radius:var(--r-pill); background:var(--surface-3); color:var(--muted); }
.tag.premium { background:var(--accent-2-soft); color:var(--accent-2-deep); }
.tag.unlocked { background:var(--accent-soft); color:var(--accent-deep); }

/* 文章內文 */
.article-body { font-size:1.06rem; }
.article-body h1, .article-body h2, .article-body h3 { line-height:1.4; margin:1.8em 0 .6em; }
.article-body img { border-radius:var(--r-md); margin:1.2em 0; }
.article-body pre { background:var(--surface-3); padding:14px 16px; border-radius:8px; overflow-x:auto; }
.article-body code { background:var(--surface-3); padding:2px 6px; border-radius:4px; font-size:.92em; }
.article-body blockquote { border-left:3px solid var(--accent); margin:1.4em 0; padding:.2em 0 .2em 16px; color:var(--muted); }

/* paywall 提示區塊 */
.gate { margin-top:36px; padding:26px 22px; text-align:center; background:var(--surface);
        border:1px solid var(--border); border-radius:var(--r-md); box-shadow:var(--shadow-sm); }
.gate h3 { margin:0 0 8px; font-size:1.14rem; }
.gate p { margin:0 0 18px; color:var(--muted); }
.gate .fade { height:80px; margin:-100px 0 20px; background:linear-gradient(transparent, var(--bg)); }

.empty { color:var(--muted); text-align:center; padding:40px 0; }
.foot { margin-top:50px; padding-top:20px; border-top:1px solid var(--border); color:var(--muted-2); font-size:.86rem; }
```

- [ ] **Step 4: 建立 index.html（訂閱入口）**

Create `survey-backend/src/main/resources/static/reader/index.html`:

```html
<!doctype html>
<html lang="zh-Hant">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>凱文大叔的電子報</title>
<meta name="description" content="RAG、AI Agent、全端實戰的實作細節與踩雷筆記，固定寄給訂閱者。">
<link rel="stylesheet" href="/r/reader.css">
</head>
<body>
<header class="site-head">
  <div class="site-head-inner">
    <a class="brand" href="/r/">凱文大叔的電子報</a>
    <nav>
      <a href="/r/archive">歷史內容</a>
      <a href="/r/login">登入</a>
    </nav>
  </div>
</header>

<div class="wrap">
  <h1>把研究和實戰的東西，整理成你能直接用的筆記</h1>
  <p>訂閱後你會收到：<strong>深入的技術討論</strong>（RAG、AI Agent、全端實戰的實作細節與踩雷筆記）、<strong>AI 新知與新技術</strong>的第一手觀察，以及<strong>課程與工具的專屬優惠</strong>。</p>

  <div class="card" style="margin-top:26px">
    <h2 style="margin-top:0;font-size:1.1rem">訂閱電子報</h2>
    <p style="color:var(--muted);font-size:.94rem">填入 email，你會收到一封確認信；點了確認才算訂閱，之前不會收到任何信。</p>
    <form id="subscribe-form" class="form-row">
      <input type="email" id="email" name="email" placeholder="your@email.com" required autocomplete="email">
      <button class="btn" type="submit">訂閱</button>
    </form>
    <div class="msg" id="msg"></div>
  </div>

  <p style="margin-top:22px;color:var(--muted);font-size:.92rem">
    已經訂閱過了？<a href="/r/login">用 email 登入</a>就能看歷史內容。
  </p>

  <div class="foot">
    <p>每封信都有一鍵退訂，隨時可以走。</p>
  </div>
</div>

<script>
  // 訂閱表單：沿用既有 /api/survey 端點（consent=true 表示同意接收）
  // 邀請碼 ?ref= 先讀進來備用；歸因寫入是階段 C 的工作
  const params = new URLSearchParams(location.search);
  const ref = params.get('ref');

  const form = document.getElementById('subscribe-form');
  const msg = document.getElementById('msg');

  /** 顯示提示訊息 */
  function showMsg(text, ok) {
    msg.textContent = text;
    msg.className = 'msg show ' + (ok ? 'ok' : 'err');
  }

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const button = form.querySelector('button');
    button.disabled = true;
    try {
      const body = { email: document.getElementById('email').value, consent: true };
      if (ref) { body.ref = ref; }
      const res = await fetch('/api/survey', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      });
      if (res.status === 201 || res.status === 204) {
        showMsg('已送出，請到信箱點確認連結完成訂閱。', true);
        form.reset();
      } else {
        showMsg('送出失敗，請確認 email 格式後再試一次。', false);
      }
    } catch (err) {
      showMsg('連線失敗，請稍後再試。', false);
    } finally {
      button.disabled = false;
    }
  });
</script>
</body>
</html>
```

- [ ] **Step 5: 建立 login.html**

Create `survey-backend/src/main/resources/static/reader/login.html`:

```html
<!doctype html>
<html lang="zh-Hant">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title><!--PAGE_TITLE--></title>
<meta name="robots" content="noindex">
<link rel="stylesheet" href="/r/reader.css">
</head>
<body>
<header class="site-head">
  <div class="site-head-inner">
    <a class="brand" href="/r/">凱文大叔的電子報</a>
    <nav><a href="/r/archive">歷史內容</a></nav>
  </div>
</header>

<div class="wrap">
  <div class="card">
    <h1 style="margin-top:0;font-size:1.3rem">登入</h1>
    <p style="color:var(--muted);font-size:.94rem">
      不需要密碼。填入訂閱時用的 email，我們寄一個登入連結給你，連結 15 分鐘內有效、只能用一次。
    </p>
    <form id="login-form" class="form-row">
      <input type="email" id="email" name="email" placeholder="your@email.com" required autocomplete="email">
      <button class="btn" type="submit">寄送登入連結</button>
    </form>
    <div class="msg" id="msg"></div>
  </div>

  <p style="margin-top:22px;color:var(--muted);font-size:.92rem">
    還沒訂閱？<a href="/r/">先訂閱電子報</a>。
  </p>
</div>

<script>
  // 登入請求：把當前的 redirect 參數一併帶上，登入後回到原本要看的頁面
  const redirect = new URLSearchParams(location.search).get('redirect');
  const form = document.getElementById('login-form');
  const msg = document.getElementById('msg');

  /** 顯示提示訊息 */
  function showMsg(text, ok) {
    msg.textContent = text;
    msg.className = 'msg show ' + (ok ? 'ok' : 'err');
  }

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const button = form.querySelector('button');
    button.disabled = true;
    try {
      const res = await fetch('/api/reader/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: document.getElementById('email').value, redirect: redirect })
      });
      const data = await res.json();
      if (data.sent) {
        showMsg('登入連結已寄出，請到信箱點開。', true);
        form.reset();
      } else if (data.throttled) {
        showMsg('剛剛已經寄過幾封了，請稍等一下再試，或直接找先前那封信。', false);
      } else {
        // 寄送失敗不顯示成功假象（spec §6）——讀者正在等這封信
        showMsg('登入信寄送失敗，請稍後再試一次。', false);
      }
    } catch (err) {
      showMsg('連線失敗，請稍後再試。', false);
    } finally {
      button.disabled = false;
    }
  });
</script>
</body>
</html>
```

- [ ] **Step 6: 寫 ReaderAuthController 的失敗測試**

Create `survey-backend/src/test/java/world/springai/survey/reader/ReaderAuthControllerTest.java`:

```java
package world.springai.survey.reader;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ReaderAuthController 行為測試：登入請求、magic link 驗證、cookie 設定、登出、redirect 安全 */
@WebMvcTest(ReaderAuthController.class)
@Import({HtmlTemplate.class, ReaderSessionService.class})
@TestPropertySource(properties = {
    "app.reader.jwt-secret=test-secret-key-at-least-32-bytes-long!!",
    "app.reader.jwt-ttl-days=28",
    "app.public-base-url=https://news.example.com",
    "app.cors-allowed-origins=http://localhost"
})
class ReaderAuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ReaderSessionService sessionService;

    @MockBean LoginMailService loginMailService;
    @MockBean LoginTokenService loginTokenService;
    @MockBean ReaderAccountService readerAccountService;
    @MockBean ReaderRepository readerRepository;

    /** 建立一位讀者 */
    private Reader reader() {
        Reader r = new Reader("user@example.com", "CODE1234");
        r.setId(1L);
        r.setCredits(300);
        return r;
    }

    /** 登入請求成功時回 sent=true */
    @Test
    void loginRequestReportsSent() throws Exception {
        when(loginMailService.sendLoginLink(eq("user@example.com"), any(), any()))
            .thenReturn(new LoginMailService.SendResult(true, false));

        mvc.perform(post("/api/reader/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.sent").value(true))
           .andExpect(jsonPath("$.throttled").value(false));
    }

    /** 節流時回 throttled=true，前端要顯示不同訊息 */
    @Test
    void loginRequestReportsThrottled() throws Exception {
        when(loginMailService.sendLoginLink(anyString(), any(), any()))
            .thenReturn(new LoginMailService.SendResult(false, true));

        mvc.perform(post("/api/reader/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.sent").value(false))
           .andExpect(jsonPath("$.throttled").value(true));
    }

    /** email 格式無效回 400，且不寄信 */
    @Test
    void invalidEmailIsRejected() throws Exception {
        mvc.perform(post("/api/reader/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\"}"))
           .andExpect(status().isBadRequest());

        verify(loginMailService, never()).sendLoginLink(anyString(), any(), any());
    }

    /** magic link 驗證成功：設定 session cookie 並 302 導向 */
    @Test
    void verifyValidTokenSetsCookieAndRedirects() throws Exception {
        when(loginTokenService.consume(eq("GOOD-TOKEN"), any()))
            .thenReturn(Optional.of("user@example.com"));
        when(readerAccountService.findOrCreate(eq("user@example.com"), any())).thenReturn(reader());

        mvc.perform(get("/api/reader/login/verify").param("t", "GOOD-TOKEN"))
           .andExpect(status().isFound())
           .andExpect(header().string("Location", "/r/archive"))
           .andExpect(cookie().exists(ReaderSessionService.COOKIE_NAME))
           .andExpect(cookie().httpOnly(ReaderSessionService.COOKIE_NAME, true));
    }

    /** 帶站內 redirect 時導向該路徑 */
    @Test
    void verifyHonoursInternalRedirect() throws Exception {
        when(loginTokenService.consume(anyString(), any())).thenReturn(Optional.of("user@example.com"));
        when(readerAccountService.findOrCreate(anyString(), any())).thenReturn(reader());

        mvc.perform(get("/api/reader/login/verify")
                .param("t", "GOOD-TOKEN")
                .param("redirect", "/r/news/hello"))
           .andExpect(status().isFound())
           .andExpect(header().string("Location", "/r/news/hello"));
    }

    /** 站外 redirect 必須被丟棄，改導向預設頁（防開放式轉址） */
    @Test
    void verifyRejectsExternalRedirect() throws Exception {
        when(loginTokenService.consume(anyString(), any())).thenReturn(Optional.of("user@example.com"));
        when(readerAccountService.findOrCreate(anyString(), any())).thenReturn(reader());

        mvc.perform(get("/api/reader/login/verify")
                .param("t", "GOOD-TOKEN")
                .param("redirect", "https://evil.example.com"))
           .andExpect(status().isFound())
           .andExpect(header().string("Location", "/r/archive"));
    }

    /** protocol-relative 網址（//evil.com）也必須被丟棄 */
    @Test
    void verifyRejectsProtocolRelativeRedirect() throws Exception {
        when(loginTokenService.consume(anyString(), any())).thenReturn(Optional.of("user@example.com"));
        when(readerAccountService.findOrCreate(anyString(), any())).thenReturn(reader());

        mvc.perform(get("/api/reader/login/verify")
                .param("t", "GOOD-TOKEN")
                .param("redirect", "//evil.example.com"))
           .andExpect(status().isFound())
           .andExpect(header().string("Location", "/r/archive"));
    }

    /** 無效 token：導向登入頁並帶錯誤標記，不設 cookie */
    @Test
    void verifyInvalidTokenRedirectsToLoginWithoutCookie() throws Exception {
        when(loginTokenService.consume(anyString(), any())).thenReturn(Optional.empty());

        mvc.perform(get("/api/reader/login/verify").param("t", "BAD-TOKEN"))
           .andExpect(status().isFound())
           .andExpect(header().string("Location", "/r/login?error=invalid"))
           .andExpect(cookie().doesNotExist(ReaderSessionService.COOKIE_NAME));

        verify(readerAccountService, never()).findOrCreate(anyString(), any());
    }

    /** 登出：清除 cookie */
    @Test
    void logoutClearsCookie() throws Exception {
        mvc.perform(post("/api/reader/logout"))
           .andExpect(status().isNoContent())
           .andExpect(cookie().maxAge(ReaderSessionService.COOKIE_NAME, 0));
    }

    /** /api/reader/me 未登入回 401 */
    @Test
    void meRequiresLogin() throws Exception {
        mvc.perform(get("/api/reader/me"))
           .andExpect(status().isUnauthorized());
    }

    /** /api/reader/me 已登入回帳戶資訊 */
    @Test
    void meReturnsAccountInfo() throws Exception {
        when(readerRepository.findById(1L)).thenReturn(Optional.of(reader()));
        String jwt = sessionService.issueJwt(1L, OffsetDateTime.now());

        mvc.perform(get("/api/reader/me").cookie(
                new jakarta.servlet.http.Cookie(ReaderSessionService.COOKIE_NAME, jwt)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.email").value("user@example.com"))
           .andExpect(jsonPath("$.credits").value(300))
           .andExpect(jsonPath("$.referralCode").value("CODE1234"));
    }

    /** 登入頁可正常載入 */
    @Test
    void loginPageRenders() throws Exception {
        mvc.perform(get("/r/login"))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }
}
```

（需在 import 區補上 `import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;`）

- [ ] **Step 7: 實作 ReaderAuthController**

Create `survey-backend/src/main/java/world/springai/survey/reader/ReaderAuthController.java`:

```java
package world.springai.survey.reader;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 讀者登入相關端點：magic link 請求與驗證、登出、帳戶資訊。
 *
 * <p>與內容頁面分成兩個 controller（另一個是 ReaderPageController）：合在一起
 * 需要注入十個依賴，違反單一任務原則。</p>
 */
@RestController
public class ReaderAuthController {

    /** 登入成功後的預設落點 */
    private static final String DEFAULT_REDIRECT = "/r/archive";

    private final LoginMailService loginMailService;
    private final LoginTokenService loginTokenService;
    private final ReaderAccountService readerAccountService;
    private final ReaderSessionService sessionService;
    private final ReaderRepository readerRepository;
    private final HtmlTemplate htmlTemplate;

    /** 注入登入流程所需的服務 */
    public ReaderAuthController(LoginMailService loginMailService,
                               LoginTokenService loginTokenService,
                               ReaderAccountService readerAccountService,
                               ReaderSessionService sessionService,
                               ReaderRepository readerRepository,
                               HtmlTemplate htmlTemplate) {
        this.loginMailService = loginMailService;
        this.loginTokenService = loginTokenService;
        this.readerAccountService = readerAccountService;
        this.sessionService = sessionService;
        this.readerRepository = readerRepository;
        this.htmlTemplate = htmlTemplate;
    }

    /** 登入請求：email 必填且需為合法格式，redirect 選填 */
    public record LoginRequest(@NotBlank @Email String email, String redirect) {}

    /** 登入頁（無動態內容，但需由 controller 提供以支援 /r/login 這種無副檔名路徑） */
    @GetMapping(value = "/r/login", produces = MediaType.TEXT_HTML_VALUE)
    public String loginPage() {
        return htmlTemplate.render("static/reader/login.html",
            Map.of("<!--PAGE_TITLE-->", "登入｜凱文大叔的電子報"));
    }

    /** 訂閱入口頁 */
    @GetMapping(value = "/r/", produces = MediaType.TEXT_HTML_VALUE)
    public String indexPage() {
        return htmlTemplate.render("static/reader/index.html", Map.of());
    }

    /**
     * 請求登入信。
     *
     * <p>回傳 sent / throttled 兩個布林讓前端顯示不同訊息——寄送失敗時不可顯示
     * 成功假象（spec §6），讀者正在等這封信。</p>
     */
    @PostMapping("/api/reader/login")
    public Map<String, Boolean> requestLogin(@Valid @RequestBody LoginRequest request) {
        LoginMailService.SendResult result =
            loginMailService.sendLoginLink(request.email(), request.redirect(), OffsetDateTime.now());
        return Map.of("sent", result.sent(), "throttled", result.throttled());
    }

    /**
     * 承接 magic link：兌換 token、建立或取得帳戶、簽發 session cookie 後導向。
     *
     * <p>token 無效（不存在／過期／已使用）時導向登入頁並帶 error 標記，不設 cookie，
     * 也不建立任何帳戶。</p>
     */
    @GetMapping("/api/reader/login/verify")
    public ResponseEntity<Void> verifyLogin(@RequestParam("t") String token,
                                           @RequestParam(value = "redirect", required = false) String redirect) {
        OffsetDateTime now = OffsetDateTime.now();
        Optional<String> email = loginTokenService.consume(token, now);

        if (email.isEmpty()) {
            return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, "/r/login?error=invalid")
                .build();
        }

        Reader reader = readerAccountService.findOrCreate(email.get(), now);
        String jwt = sessionService.issueJwt(reader.getId(), now);

        return ResponseEntity.status(302)
            .header(HttpHeaders.LOCATION, safeRedirect(redirect))
            .header(HttpHeaders.SET_COOKIE, sessionService.buildSessionCookie(jwt).toString())
            .build();
    }

    /** 登出：清除 session cookie */
    @PostMapping("/api/reader/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, sessionService.buildClearCookie().toString())
            .build();
    }

    /** 目前登入者的帳戶資訊；未登入回 401 */
    @GetMapping("/api/reader/me")
    public ResponseEntity<Map<String, Object>> me(
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {
        Optional<Reader> reader = currentReader(sessionCookie);
        if (reader.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        Reader r = reader.get();
        return ResponseEntity.ok(Map.of(
            "email", r.getEmail(),
            "tier", r.getTier(),
            "credits", r.getCredits(),
            "referralCode", r.getReferralCode()));
    }

    /** 由 session cookie 取出目前登入的讀者；無效一律視為未登入 */
    private Optional<Reader> currentReader(String sessionCookie) {
        return sessionService.readReaderId(sessionCookie, OffsetDateTime.now())
            .flatMap(readerRepository::findById);
    }

    /**
     * 只接受站內相對路徑作為導向目標。
     *
     * <p>必須排除 {@code //evil.com}：它以 / 開頭，但瀏覽器會視為站外網址，
     * 是開放式轉址最常見的漏法。</p>
     */
    private String safeRedirect(String redirect) {
        if (StringUtils.hasText(redirect) && redirect.startsWith("/") && !redirect.startsWith("//")) {
            return redirect;
        }
        return DEFAULT_REDIRECT;
    }
}
```

- [ ] **Step 8: 跑測試並修正**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test -Dtest=HtmlTemplateTest+ReaderAuthControllerTest
```

Expected: `Tests run: 17, Failures: 0, Errors: 0, Skipped: 0`（5 + 12）

`/r/reader.css` 需要能被靜態資源解析。Spring Boot 預設會把 `classpath:/static/` 對應到根路徑，因此 `static/reader/reader.css` 天然對應 `/reader/reader.css`——**不是** `/r/reader.css`。加上路由對應：

Modify `survey-backend/src/main/java/world/springai/survey/WebConfig.java`，加入：

```java
    /**
     * 讓 /r/** 能取到 static/reader/ 下的資源（如 reader.css）。
     * HTML 頁面本身由 controller 提供（需要 server 端渲染），這裡只服務靜態附件。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/r/*.css", "/r/*.js")
                .addResourceLocations("classpath:/static/reader/");
    }
```

並補 import：

```java
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
```

- [ ] **Step 9: 跑全部測試並 commit**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test
```

Expected: `Tests run: 167, Failures: 0, Errors: 0, Skipped: 0`（150 + 17）

```powershell
git add src/main/java/world/springai/survey/reader/HtmlTemplate.java src/main/java/world/springai/survey/reader/ReaderAuthController.java src/main/java/world/springai/survey/WebConfig.java src/main/resources/static/reader/ src/test/java/world/springai/survey/reader/HtmlTemplateTest.java src/test/java/world/springai/survey/reader/ReaderAuthControllerTest.java
git commit -m @'
feat(reader): HtmlTemplate、登入 API 與訂閱／登入頁

HtmlTemplate：
- 極簡佔位符替換，不引入 Thymeleaf（需求只是替換幾個佔位符）
- 刻意不快取：讀 classpath 成本遠低於一次 DB 查詢，換得改 HTML
  不必重啟的開發體驗
- 找不到資源明確拋例外，不回空字串讓頁面靜默變空白
- escapeHtml 先處理 &，否則實體會被二次跳脫

ReaderAuthController（與內容頁分開，避免單一 controller 吃十個依賴）：
- POST /api/reader/login 回 sent/throttled 兩個布林，寄送失敗
  不顯示成功假象（spec §6）
- GET /api/reader/login/verify 兌換 token → 建帳 → 發 cookie → 302
- redirect 只接受站內相對路徑，並排除 //evil.com 這種
  protocol-relative 網址
- 無效 token 導向 /r/login?error=invalid 且不建立任何帳戶

頁面：index.html（訂閱入口，讀 ?ref= 備用）、login.html、
reader.css（設計系統 token 與 static/index.html、land-page 共用）

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
'@
```

---

## Task 12: ReaderContext、內容頁面與「PARTIAL 不洩漏」驗證

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/reader/ReaderContext.java`
- Create: `survey-backend/src/main/java/world/springai/survey/reader/ReaderPageController.java`
- Create: `survey-backend/src/main/resources/static/reader/archive.html`
- Create: `survey-backend/src/main/resources/static/reader/article.html`
- Modify: `survey-backend/src/main/java/world/springai/survey/reader/ReaderAuthController.java`（`currentReader` 改用 `ReaderContext`）
- Test: `survey-backend/src/test/java/world/springai/survey/reader/ReaderPageControllerTest.java`

**Interfaces:**
- Consumes: 全部前置 task
- Produces:
  - `record ReaderContext.Current(Reader reader, boolean subscribed)`
  - `ReaderContext.resolve(String sessionCookie) → Optional<Current>`
  - `GET /r/archive` → server 渲染的歷史列表
  - `GET /r/news/{slug}` → server 渲染的單篇，**未授權時回應完全不含受限區**

> **為什麼多一個 `ReaderContext`**：`ReaderPageController` 若自行解析 session，會需要注入 9 個依賴，而且「cookie → Reader → 是否訂閱」這段邏輯會與 `ReaderAuthController` 重複。抽成一個 component 後兩邊共用，controller 各降到 7 個以內。

- [ ] **Step 1: 建立 ReaderContext**

Create `survey-backend/src/main/java/world/springai/survey/reader/ReaderContext.java`:

```java
package world.springai.survey.reader;

import org.springframework.stereotype.Component;
import world.springai.survey.audience.SurveyResponseRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 由 session cookie 解析出「目前是誰、有沒有確認訂閱」。
 *
 * <p>抽成獨立元件的理由：這段邏輯被登入 API 與內容頁面共用，重複實作會讓兩邊
 * 對「訂閱」的判定有機會走偏。訂閱狀態一律取自名單中心（spec 原則 3），
 * 不從 reader 表推導。</p>
 */
@Component
public class ReaderContext {

    private final ReaderSessionService sessionService;
    private final ReaderRepository readerRepository;
    private final SurveyResponseRepository surveyResponseRepository;

    /** 注入 session、讀者與名單中心 */
    public ReaderContext(ReaderSessionService sessionService,
                        ReaderRepository readerRepository,
                        SurveyResponseRepository surveyResponseRepository) {
        this.sessionService = sessionService;
        this.readerRepository = readerRepository;
        this.surveyResponseRepository = surveyResponseRepository;
    }

    /**
     * 目前的讀者狀態。
     *
     * @param reader     已登入的讀者
     * @param subscribed 是否為已確認訂閱者（來自名單中心）
     */
    public record Current(Reader reader, boolean subscribed) {}

    /** 解析 session cookie；無效或未登入回 empty */
    public Optional<Current> resolve(String sessionCookie) {
        return sessionService.readReaderId(sessionCookie, OffsetDateTime.now())
            .flatMap(readerRepository::findById)
            .map(reader -> new Current(reader, surveyResponseRepository.isSubscribed(reader.getEmail())));
    }
}
```

- [ ] **Step 2: 把 ReaderAuthController 的 currentReader 改用 ReaderContext**

Modify `survey-backend/src/main/java/world/springai/survey/reader/ReaderAuthController.java`：

將建構子中的 `ReaderSessionService sessionService, ReaderRepository readerRepository` 保留（`verifyLogin` 與 `logout` 仍需要 `sessionService`），但把 `readerRepository` 換成 `ReaderContext readerContext`，並改寫 `currentReader`：

```java
    /** 由 session cookie 取出目前登入的讀者；無效一律視為未登入 */
    private Optional<Reader> currentReader(String sessionCookie) {
        return readerContext.resolve(sessionCookie).map(ReaderContext.Current::reader);
    }
```

同步更新 `ReaderAuthControllerTest`：把 `@MockBean ReaderRepository readerRepository` 換成 `@MockBean ReaderContext readerContext`，並把 `meReturnsAccountInfo` 的 stub 改為：

```java
        when(readerContext.resolve(anyString()))
            .thenReturn(Optional.of(new ReaderContext.Current(reader(), true)));
```

- [ ] **Step 3: 建立 archive.html**

Create `survey-backend/src/main/resources/static/reader/archive.html`:

```html
<!doctype html>
<html lang="zh-Hant">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>歷史內容｜凱文大叔的電子報</title>
<meta name="description" content="過去發送的電子報：RAG、AI Agent、全端實戰的實作細節與踩雷筆記。">
<link rel="stylesheet" href="/r/reader.css">
</head>
<body>
<header class="site-head">
  <div class="site-head-inner">
    <a class="brand" href="/r/">凱文大叔的電子報</a>
    <nav><!--NAV_LINKS--></nav>
  </div>
</header>

<div class="wrap">
  <h1 style="font-size:1.5rem">歷史內容</h1>
  <!--ARTICLE_LIST-->
</div>
</body>
</html>
```

- [ ] **Step 4: 建立 article.html**

Create `survey-backend/src/main/resources/static/reader/article.html`:

```html
<!doctype html>
<html lang="zh-Hant">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title><!--PAGE_TITLE--></title>
<meta name="description" content="<!--PAGE_DESCRIPTION-->">
<link rel="stylesheet" href="/r/reader.css">
</head>
<body>
<header class="site-head">
  <div class="site-head-inner">
    <a class="brand" href="/r/">凱文大叔的電子報</a>
    <nav><!--NAV_LINKS--></nav>
  </div>
</header>

<div class="wrap">
  <article>
    <h1 style="font-size:1.6rem;line-height:1.4;margin-bottom:8px"><!--ARTICLE_TITLE--></h1>
    <div class="article-meta" style="margin-bottom:26px"><!--ARTICLE_META--></div>
    <div class="article-body"><!--ARTICLE_CONTENT--></div>
  </article>
  <!--GATE_BLOCK-->
  <p style="margin-top:40px"><a href="/r/archive">← 回到歷史內容</a></p>
</div>
</body>
</html>
```

- [ ] **Step 5: 寫「PARTIAL 不洩漏」與頁面渲染的失敗測試**

Create `survey-backend/src/test/java/world/springai/survey/reader/ReaderPageControllerTest.java`:

```java
package world.springai.survey.reader;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import world.springai.survey.AppSettingService;
import world.springai.survey.newsletter.Campaign;
import world.springai.survey.newsletter.CampaignRepository;
import world.springai.survey.newsletter.MarkdownRenderer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ReaderPageController 行為測試。
 *
 * <p>最重要的一組是「受限區不洩漏」：以哨兵字串斷言未授權者的回應**完全不含**
 * 受限內容。只檢查有無某個 CSS class 或提示文字是不夠的——那不能證明內容
 * 沒被送到瀏覽器。</p>
 */
@WebMvcTest(ReaderPageController.class)
@Import({HtmlTemplate.class, ContentSplitter.class, MarkdownRenderer.class})
@TestPropertySource(properties = {
    "app.cors-allowed-origins=http://localhost",
    "app.public-base-url=https://news.example.com"
})
class ReaderPageControllerTest {

    /** 受限區的哨兵字串：只要它出現在回應中就是洩漏 */
    private static final String SENTINEL = "SENTINEL_GATED_9f3a";

    /** 免費區的標記字串：應該永遠看得到 */
    private static final String FREE_MARKER = "FREE_INTRO_TEXT";

    @Autowired MockMvc mvc;

    @MockBean CampaignRepository campaignRepository;
    @MockBean AccessDecisionService accessDecisionService;
    @MockBean ArticleAccessRepository articleAccessRepository;
    @MockBean ReaderContext readerContext;
    @MockBean AppSettingService appSettingService;

    /** 建立一篇含 paywall 標記的文章 */
    private Campaign gatedArticle(String tier, int cost) {
        String markdown = FREE_MARKER + "\n\n<!--paywall-->\n\n" + SENTINEL;
        Campaign c = new Campaign("測試文章", markdown, null, null, null, "now", null, 1, "sent");
        c.setTier(tier);
        c.setCreditCost(cost);
        c.setSlug("test-article");
        c.setPublishedAt(OffsetDateTime.parse("2026-07-20T10:00:00+08:00"));
        return c;
    }

    /** 建立一位讀者 */
    private Reader reader(String tier, int credits) {
        Reader r = new Reader("user@example.com", "CODE1234");
        r.setId(1L);
        r.setTier(tier);
        r.setCredits(credits);
        return r;
    }

    /** 讓 decide 回傳指定決策 */
    private void stubDecision(AccessDecisionService.Access access, AccessDecisionService.Reason reason, int shortfall) {
        when(accessDecisionService.decide(any(), org.mockito.ArgumentMatchers.anyBoolean(), any(), any()))
            .thenReturn(new AccessDecisionService.Decision(access, reason, shortfall));
    }

    /** 未登入：回應不得含受限區，但要看得到免費區 */
    @Test
    void anonymousResponseNeverContainsGatedContent() throws Exception {
        when(campaignRepository.findBySlug("test-article")).thenReturn(Optional.of(gatedArticle(Campaign.TIER_BASIC, 0)));
        when(readerContext.resolve(any())).thenReturn(Optional.empty());
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_LOGGED_IN, 0);

        String body = mvc.perform(get("/r/news/test-article"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains(SENTINEL), "未登入者的回應不得含受限區內容");
        assertTrue(body.contains(FREE_MARKER), "免費區應正常顯示");
    }

    /** 已登入但未確認訂閱：同樣不得含受限區 */
    @Test
    void unsubscribedResponseNeverContainsGatedContent() throws Exception {
        when(campaignRepository.findBySlug("test-article")).thenReturn(Optional.of(gatedArticle(Campaign.TIER_BASIC, 0)));
        when(readerContext.resolve(any()))
            .thenReturn(Optional.of(new ReaderContext.Current(reader(Reader.TIER_FREE, 300), false)));
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_SUBSCRIBED, 0);

        String body = mvc.perform(get("/r/news/test-article"))
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains(SENTINEL), "未確認訂閱者的回應不得含受限區內容");
    }

    /** 已訂閱但 PREMIUM 點數不足：同樣不得含受限區 */
    @Test
    void insufficientCreditsResponseNeverContainsGatedContent() throws Exception {
        when(campaignRepository.findBySlug("test-article"))
            .thenReturn(Optional.of(gatedArticle(Campaign.TIER_PREMIUM, 10)));
        when(readerContext.resolve(any()))
            .thenReturn(Optional.of(new ReaderContext.Current(reader(Reader.TIER_FREE, 4), true)));
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NEEDS_CREDITS, 6);

        String body = mvc.perform(get("/r/news/test-article"))
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains(SENTINEL), "點數不足者的回應不得含受限區內容");
    }

    /** 授權為 FULL：受限區才會出現 */
    @Test
    void fullAccessIncludesGatedContent() throws Exception {
        when(campaignRepository.findBySlug("test-article")).thenReturn(Optional.of(gatedArticle(Campaign.TIER_BASIC, 0)));
        when(readerContext.resolve(any()))
            .thenReturn(Optional.of(new ReaderContext.Current(reader(Reader.TIER_FREE, 300), true)));
        stubDecision(AccessDecisionService.Access.FULL, AccessDecisionService.Reason.BASIC_OPEN, 0);

        String body = mvc.perform(get("/r/news/test-article"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains(SENTINEL), "授權為 FULL 時受限區應顯示");
        assertTrue(body.contains(FREE_MARKER));
    }

    /** paywall 標記本身不得出現在頁面上 */
    @Test
    void paywallMarkerNeverAppearsInOutput() throws Exception {
        when(campaignRepository.findBySlug("test-article")).thenReturn(Optional.of(gatedArticle(Campaign.TIER_BASIC, 0)));
        when(readerContext.resolve(any()))
            .thenReturn(Optional.of(new ReaderContext.Current(reader(Reader.TIER_FREE, 300), true)));
        stubDecision(AccessDecisionService.Access.FULL, AccessDecisionService.Reason.BASIC_OPEN, 0);

        String body = mvc.perform(get("/r/news/test-article"))
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains(ContentSplitter.PAYWALL_MARKER), "paywall 標記不得洩漏到頁面");
    }

    /** 未發布的文章回 404（即使 slug 存在） */
    @Test
    void unpublishedArticleReturns404() throws Exception {
        Campaign unpublished = gatedArticle(Campaign.TIER_BASIC, 0);
        unpublished.setPublishedAt(null);
        when(campaignRepository.findBySlug("test-article")).thenReturn(Optional.of(unpublished));

        mvc.perform(get("/r/news/test-article")).andExpect(status().isNotFound());
    }

    /** 不存在的 slug 回 404 */
    @Test
    void unknownSlugReturns404() throws Exception {
        when(campaignRepository.findBySlug("nope")).thenReturn(Optional.empty());

        mvc.perform(get("/r/news/nope")).andExpect(status().isNotFound());
    }

    /** 文章標題必須經 HTML 跳脫，避免標題含標籤時破版或注入 */
    @Test
    void articleTitleIsHtmlEscaped() throws Exception {
        Campaign c = gatedArticle(Campaign.TIER_BASIC, 0);
        c.setSubject("<img src=x onerror=alert(1)>");
        when(campaignRepository.findBySlug("test-article")).thenReturn(Optional.of(c));
        when(readerContext.resolve(any())).thenReturn(Optional.empty());
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_LOGGED_IN, 0);

        String body = mvc.perform(get("/r/news/test-article"))
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("<img src=x onerror"), "標題必須跳脫");
        assertTrue(body.contains("&lt;img src=x onerror"), "應為跳脫後的形式");
    }

    /** archive 只列已發布文章 */
    @Test
    void archiveListsPublishedArticles() throws Exception {
        when(campaignRepository.findByPublishedAtIsNotNullOrderByPublishedAtDesc())
            .thenReturn(List.of(gatedArticle(Campaign.TIER_BASIC, 0)));
        when(readerContext.resolve(any())).thenReturn(Optional.empty());

        String body = mvc.perform(get("/r/archive"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("測試文章"));
        assertTrue(body.contains("/r/news/test-article"));
    }

    /** archive 的列表也不得洩漏任何文章內容（連摘要都取自免費區） */
    @Test
    void archiveNeverLeaksGatedContent() throws Exception {
        when(campaignRepository.findByPublishedAtIsNotNullOrderByPublishedAtDesc())
            .thenReturn(List.of(gatedArticle(Campaign.TIER_PREMIUM, 10)));
        when(readerContext.resolve(any())).thenReturn(Optional.empty());

        String body = mvc.perform(get("/r/archive"))
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains(SENTINEL), "archive 列表不得含任何受限區內容");
    }

    /** 沒有已發布文章時顯示空狀態，不是錯誤頁 */
    @Test
    void emptyArchiveShowsEmptyState() throws Exception {
        when(campaignRepository.findByPublishedAtIsNotNullOrderByPublishedAtDesc()).thenReturn(List.of());
        when(readerContext.resolve(any())).thenReturn(Optional.empty());

        mvc.perform(get("/r/archive")).andExpect(status().isOk());
    }
}
```

- [ ] **Step 6: 跑測試確認失敗**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test -Dtest=ReaderPageControllerTest
```

Expected: 編譯失敗，`cannot find symbol: class ReaderPageController`

- [ ] **Step 7: 實作 ReaderPageController**

Create `survey-backend/src/main/java/world/springai/survey/reader/ReaderPageController.java`:

```java
package world.springai.survey.reader;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.AppSettingService;
import world.springai.survey.newsletter.Campaign;
import world.springai.survey.newsletter.CampaignRepository;
import world.springai.survey.newsletter.MarkdownRenderer;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 讀者端內容頁面：歷史列表與單篇文章，皆為 server 端渲染。
 *
 * <p><b>為什麼不做成「靜態頁 + fetch API」</b>：只有 server 渲染才能讓
 * 「未授權者的回應完全不含受限區」在 HTTP 層次成立。若改由前端 fetch，
 * API 就得回傳整篇內容再由 JS 決定顯示哪段，受限區便出現在網路回應中，
 * paywall 形同虛設。同時 server 渲染也讓免費區確定能被搜尋引擎索引（spec §5.3）。</p>
 */
@RestController
public class ReaderPageController {

    /** 日期顯示格式 */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** PREMIUM 解鎖點數的後備預設值 */
    private static final int DEFAULT_PREMIUM_COST = 10;

    private final CampaignRepository campaignRepository;
    private final MarkdownRenderer markdownRenderer;
    private final ContentSplitter contentSplitter;
    private final AccessDecisionService accessDecisionService;
    private final ArticleAccessRepository articleAccessRepository;
    private final ReaderContext readerContext;
    private final HtmlTemplate htmlTemplate;
    private final AppSettingService appSettingService;

    /** 注入內容、授權與渲染所需的服務 */
    public ReaderPageController(CampaignRepository campaignRepository,
                               MarkdownRenderer markdownRenderer,
                               ContentSplitter contentSplitter,
                               AccessDecisionService accessDecisionService,
                               ArticleAccessRepository articleAccessRepository,
                               ReaderContext readerContext,
                               HtmlTemplate htmlTemplate,
                               AppSettingService appSettingService) {
        this.campaignRepository = campaignRepository;
        this.markdownRenderer = markdownRenderer;
        this.contentSplitter = contentSplitter;
        this.accessDecisionService = accessDecisionService;
        this.articleAccessRepository = articleAccessRepository;
        this.readerContext = readerContext;
        this.htmlTemplate = htmlTemplate;
        this.appSettingService = appSettingService;
    }

    /** 歷史內容列表：只列已發布者，登入者會看到自己的解鎖狀態 */
    @GetMapping(value = "/r/archive", produces = MediaType.TEXT_HTML_VALUE)
    public String archive(
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {
        Optional<ReaderContext.Current> current = readerContext.resolve(sessionCookie);
        List<Campaign> articles = campaignRepository.findByPublishedAtIsNotNullOrderByPublishedAtDesc();

        // 登入者的已解鎖清單，用於在列表上標示；未登入則為空集合
        Set<Long> unlocked = current
            .map(c -> articleAccessRepository.findByReaderId(c.reader().getId()).stream()
                .map(ArticleAccess::getCampaignId)
                .collect(Collectors.toSet()))
            .orElse(Set.of());

        return htmlTemplate.render("static/reader/archive.html", Map.of(
            "<!--NAV_LINKS-->", navLinks(current.isPresent()),
            "<!--ARTICLE_LIST-->", renderArticleList(articles, unlocked)));
    }

    /**
     * 單篇文章。
     *
     * <p>依授權決策決定是否把受限區渲染進 HTML——PARTIAL 時受限區
     * <b>完全不進入回應</b>，不是靠 CSS 隱藏。</p>
     */
    @GetMapping(value = "/r/news/{slug}", produces = MediaType.TEXT_HTML_VALUE)
    public String article(@PathVariable String slug,
                         @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {
        Campaign campaign = campaignRepository.findBySlug(slug)
            .filter(Campaign::isPublished)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到這篇文章"));

        Optional<ReaderContext.Current> current = readerContext.resolve(sessionCookie);
        Reader reader = current.map(ReaderContext.Current::reader).orElse(null);
        boolean subscribed = current.map(ReaderContext.Current::subscribed).orElse(false);

        AccessDecisionService.Decision decision =
            accessDecisionService.decide(reader, subscribed, campaign, OffsetDateTime.now());

        ContentSplitter.Split split = contentSplitter.split(campaign.getMarkdown());
        boolean full = decision.access() == AccessDecisionService.Access.FULL;

        // 關鍵：PARTIAL 時只渲染免費區，受限區的 markdown 根本不進入輸出
        String contentHtml = markdownRenderer.toHtml(split.freeMarkdown());
        if (full && split.hasGate()) {
            contentHtml += markdownRenderer.toHtml(split.gatedMarkdown());
        }

        if (full) {
            accessDecisionService.recordAccess(reader, campaign, decision);
        }

        Map<String, String> vars = new HashMap<>();
        vars.put("<!--PAGE_TITLE-->", HtmlTemplate.escapeHtml(campaign.getSubject()) + "｜凱文大叔的電子報");
        vars.put("<!--PAGE_DESCRIPTION-->", HtmlTemplate.escapeHtml(summaryOf(split.freeMarkdown())));
        vars.put("<!--ARTICLE_TITLE-->", HtmlTemplate.escapeHtml(campaign.getSubject()));
        vars.put("<!--ARTICLE_META-->", renderMeta(campaign));
        vars.put("<!--ARTICLE_CONTENT-->", contentHtml); // 已是渲染後的 HTML，不可再跳脫
        vars.put("<!--NAV_LINKS-->", navLinks(current.isPresent()));
        vars.put("<!--GATE_BLOCK-->", full || !split.hasGate() ? "" : renderGate(decision, campaign, slug));
        return htmlTemplate.render("static/reader/article.html", vars);
    }

    /** 依登入狀態顯示不同的導覽連結 */
    private String navLinks(boolean loggedIn) {
        if (loggedIn) {
            return "<a href=\"/r/archive\">歷史內容</a>";
        }
        return "<a href=\"/r/archive\">歷史內容</a><a href=\"/r/login\">登入</a>";
    }

    /** 渲染 archive 的文章列表；摘要一律取自免費區 */
    private String renderArticleList(List<Campaign> articles, Set<Long> unlocked) {
        if (articles.isEmpty()) {
            return "<p class=\"empty\">還沒有已發布的內容，訂閱後第一封就會寄給你。</p>";
        }
        StringBuilder sb = new StringBuilder("<ul class=\"article-list\">");
        for (Campaign c : articles) {
            sb.append("<li class=\"article-item\">")
              .append("<h2><a href=\"/r/news/").append(HtmlTemplate.escapeHtml(c.getSlug())).append("\">")
              .append(HtmlTemplate.escapeHtml(c.getSubject())).append("</a></h2>")
              .append("<div class=\"article-meta\">").append(renderMeta(c));
            if (unlocked.contains(c.getId())) {
                sb.append("<span class=\"tag unlocked\">已解鎖</span>");
            }
            sb.append("</div></li>");
        }
        return sb.append("</ul>").toString();
    }

    /** 渲染日期與分級標籤 */
    private String renderMeta(Campaign campaign) {
        StringBuilder sb = new StringBuilder();
        if (campaign.getPublishedAt() != null) {
            sb.append("<span>").append(campaign.getPublishedAt().format(DATE_FORMAT)).append("</span>");
        }
        if (campaign.isPremium()) {
            sb.append("<span class=\"tag premium\">進階</span>");
        }
        return sb.toString();
    }

    /**
     * 渲染 paywall 提示區塊。
     *
     * <p>階段 B 一律只發布 BASIC 文章（見 Global Constraints），因此 NEEDS_CREDITS
     * 的文案是為階段 C 預備的；此時真正會出現的是「未登入」與「未確認訂閱」兩種。</p>
     */
    private String renderGate(AccessDecisionService.Decision decision, Campaign campaign, String slug) {
        String encodedRedirect = "/r/news/" + slug;
        return switch (decision.reason()) {
            case NOT_LOGGED_IN -> gateHtml("接下來的內容需要登入",
                "用訂閱時的 email 登入就能繼續看，不需要密碼。",
                "<a class=\"btn\" href=\"/r/login?redirect=" + HtmlTemplate.escapeHtml(encodedRedirect) + "\">登入繼續閱讀</a>");
            case NOT_SUBSCRIBED -> gateHtml("確認訂閱後就能看完整內容",
                "你的信箱裡應該有一封確認信，點了就完成訂閱。找不到的話可以重新訂閱一次。",
                "<a class=\"btn\" href=\"/r/\">重新訂閱</a>");
            case NEEDS_CREDITS -> gateHtml("這是進階內容",
                decision.shortfall() > 0
                    ? "解鎖需要 " + resolveCost(campaign) + " 點，你還差 " + decision.shortfall() + " 點。"
                    : "解鎖需要 " + resolveCost(campaign) + " 點。",
                "<a class=\"btn\" href=\"/r/archive\">先看其他內容</a>");
            default -> "";
        };
    }

    /** 組 paywall 區塊的 HTML（含漸層淡出） */
    private String gateHtml(String title, String description, String action) {
        return """
            <div class="gate">
              <div class="fade"></div>
              <h3>%s</h3>
              <p>%s</p>
              %s
            </div>
            """.formatted(title, description, action);
    }

    /** 取得解鎖成本；與 AccessDecisionService 用同一套後備規則 */
    private int resolveCost(Campaign campaign) {
        return campaign.getCreditCost() > 0
            ? campaign.getCreditCost()
            : appSettingService.getInt(AppSettingService.CREDIT_PREMIUM_COST, DEFAULT_PREMIUM_COST);
    }

    /** 從免費區 markdown 取前 120 字作為 meta description（去掉標記符號） */
    private String summaryOf(String freeMarkdown) {
        String plain = freeMarkdown.replaceAll("[#*`>\\[\\]()!_]", " ").replaceAll("\\s+", " ").trim();
        return plain.length() <= 120 ? plain : plain.substring(0, 120) + "…";
    }
}
```

- [ ] **Step 8: 跑測試確認通過**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test -Dtest=ReaderPageControllerTest
```

Expected: `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 9: 驗證洩漏測試真的會失敗（刻意破壞一次）**

暫時把 `article()` 裡的條件改成無論如何都附上受限區：

```java
        String contentHtml = markdownRenderer.toHtml(split.freeMarkdown());
        if (split.hasGate()) {   // 故意移除 full && 條件
            contentHtml += markdownRenderer.toHtml(split.gatedMarkdown());
        }
```

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test -Dtest=ReaderPageControllerTest
```

Expected: **FAIL**，三個哨兵測試都失敗並顯示「不得含受限區內容」。

**一定要做這步。** 這是本階段最重要的一道防線，沒驗證過它會失敗，就等於沒有這道防線。確認後還原：

```powershell
git checkout -- src/main/java/world/springai/survey/reader/ReaderPageController.java
```

（若尚未 commit 過此檔，改為手動把 `full &&` 條件加回去。）

- [ ] **Step 10: 跑全部測試並 commit**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test
```

Expected: `Tests run: 178, Failures: 0, Errors: 0, Skipped: 0`（167 + 11）

```powershell
git add src/main/java/world/springai/survey/reader/ src/main/resources/static/reader/ src/test/java/world/springai/survey/reader/
git commit -m @'
feat(reader): 內容頁面（archive／單篇）與受限區不洩漏保證

ReaderContext：把「cookie → Reader → 是否訂閱」抽成共用元件，
避免兩個 controller 各自實作而對「訂閱」判定走偏，
同時讓 controller 依賴數降到 7 個以內。

ReaderPageController（server 端渲染，非靜態頁 + fetch API）：
- PARTIAL 時受限區的 markdown 根本不進入輸出，不是 CSS 隱藏；
  只有 server 渲染能讓這件事在 HTTP 層次成立
- 免費區可被搜尋引擎索引（spec §5.3 的 metered paywall 做法）
- 標題經 escapeHtml，內容是 MarkdownRenderer 輸出故不再跳脫
- paywall 提示依 Reason 給不同文案與行動按鈕
- 未發布文章一律 404（即使 slug 存在）

測試：以哨兵字串斷言未登入／未訂閱／點數不足三種情境的回應
完全不含受限內容，並已實測「刻意移除授權條件會讓測試失敗」。

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
'@
```

---

## Task 13: 端到端驗證與部署前檢查

**Files:**
- Create: `survey-backend/scripts/verify-reader-flow.mjs`
- 不修改生產程式碼（純驗證）

**Interfaces:**
- Consumes: Task 1–12 的全部產出
- Produces: 可重跑的讀者流程驗證腳本

> CLAUDE.md 規定瀏覽器自動化必須寫成可重跑的腳本檔而非一次性互動指令。本 task 沿用專案既有 `scripts/verify-*.mjs` 慣例。

- [ ] **Step 1: 準備本機環境**

```powershell
cd d:\GitHub\hahow-ai-full-stack\survey-backend
$env:PGPASSWORD = "password"
psql -h 127.0.0.1 -U postgres -c "DROP DATABASE IF EXISTS survey_reader_e2e;"
psql -h 127.0.0.1 -U postgres -c "CREATE DATABASE survey_reader_e2e;"
```

- [ ] **Step 2: 啟動應用（NoopMailSender 模式，登入連結印在日誌）**

不設 `SEND_MAIL_API` 即自動 fallback 成 `NoopMailSender`，不會真的寄信。

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
$env:JDBC_URL = "jdbc:postgresql://127.0.0.1:5432/survey_reader_e2e"
mvn spring-boot:run
```

Expected: 啟動成功，日誌顯示 Flyway 套用 V1–V8，無 `Schema-validation` 錯誤。

- [ ] **Step 3: 準備一篇已發布的測試文章**

另開終端：

```powershell
$env:PGPASSWORD = "password"
psql -h 127.0.0.1 -U postgres -d survey_reader_e2e -c @'
INSERT INTO survey_response (email, consent, unsubscribed, source, last_engaged_at)
  VALUES ('e2e@example.com', TRUE, FALSE, 'survey_form', now());
INSERT INTO campaign (subject, markdown, mode, recipient_count, accepted_count, failed_count,
                      status, tier, credit_cost, slug, published_at)
  VALUES ('端到端測試文章',
          E'這段是免費的開場，所有人都看得到。\n\n<!--paywall-->\n\n這段是受限區，未登入不該看到。',
          'now', 1, 1, 0, 'sent', 'BASIC', 0, 'e2e-test', now());
'@
```

- [ ] **Step 4: 建立驗證腳本**

Create `survey-backend/scripts/verify-reader-flow.mjs`:

```javascript
// 讀者端流程驗證：archive 列表 → 未登入看單篇（受限區不得洩漏）→ 請求登入信
// → 從資料庫取出 token 完成登入 → 再看單篇（受限區應出現）。
//
// 用法（需先啟動應用並執行計畫 Task 13 Step 3 的測試資料）：
//   node scripts/verify-reader-flow.mjs
//   node scripts/verify-reader-flow.mjs --base http://127.0.0.1:8080
//
// 為何寫成腳本：CLAUDE.md 規定這類流程驗證要可重跑、可逐行檢查，
// 而不是一次性的互動指令。

const args = process.argv.slice(2);
const baseIndex = args.indexOf('--base');
const BASE = baseIndex >= 0 ? args[baseIndex + 1] : 'http://127.0.0.1:8080';
const SLUG = 'e2e-test';
const GATED_TEXT = '這段是受限區';
const FREE_TEXT = '這段是免費的開場';
const EMAIL = 'e2e@example.com';

let failures = 0;

/** 記錄一項檢查結果 */
function check(name, passed, detail = '') {
  if (passed) {
    console.log(`  ✓ ${name}`);
  } else {
    failures++;
    console.log(`  ✗ ${name}${detail ? ` —— ${detail}` : ''}`);
  }
}

/** 取得頁面內容與回應 */
async function fetchPage(path, cookie) {
  const headers = cookie ? { Cookie: cookie } : {};
  const res = await fetch(`${BASE}${path}`, { headers, redirect: 'manual' });
  return { res, body: await res.text() };
}

console.log(`\n=== 讀者端流程驗證（${BASE}）===\n`);

// 1. archive 列表
console.log('[1] archive 列表');
{
  const { res, body } = await fetchPage('/r/archive');
  check('回應 200', res.status === 200, `實際 ${res.status}`);
  check('列出測試文章', body.includes('端到端測試文章'));
  check('archive 不含受限區內容', !body.includes(GATED_TEXT));
}

// 2. 未登入看單篇 —— 最關鍵的一項
console.log('\n[2] 未登入讀單篇（受限區不得洩漏）');
{
  const { res, body } = await fetchPage(`/r/news/${SLUG}`);
  check('回應 200', res.status === 200, `實際 ${res.status}`);
  check('看得到免費區', body.includes(FREE_TEXT));
  check('★ 回應完全不含受限區', !body.includes(GATED_TEXT),
    '受限內容洩漏到未登入者的回應中');
  check('paywall 標記未洩漏', !body.includes('<!--paywall-->'));
  check('顯示登入提示', body.includes('需要登入'));
}

// 3. 不存在的文章
console.log('\n[3] 不存在的 slug');
{
  const { res } = await fetchPage('/r/news/does-not-exist');
  check('回應 404', res.status === 404, `實際 ${res.status}`);
}

// 4. 請求登入信
console.log('\n[4] 請求登入信');
{
  const res = await fetch(`${BASE}/api/reader/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: EMAIL, redirect: `/r/news/${SLUG}` })
  });
  const data = await res.json();
  check('回應 200', res.status === 200);
  check('sent 為 true（NoopMailSender 也算成功送出）', data.sent === true,
    JSON.stringify(data));
}

// 5. 登入驗證需要明文 token —— 只存在於「寄出的信」中
console.log('\n[5] 完成登入');
console.log('  ! 明文 token 只存在於寄出的信裡（DB 只有雜湊），無法從此腳本取得。');
console.log('  ! 請改用下列方式之一手動完成，並確認登入後受限區可見：');
console.log('    a) 設定真實的 SEND_MAIL_API 後收信點連結');
console.log('    b) 在 LoginMailService 暫時加一行 log.info 印出連結（僅本機，不得提交）');
console.log('  ! 這是刻意的設計結果：token 不可從資料庫反推使用。');

// 6. 無效 token 不得放行
console.log('\n[6] 無效 token');
{
  const { res } = await fetchPage('/api/reader/login/verify?t=forged-token-value');
  const location = res.headers.get('location') || '';
  check('回應 302', res.status === 302, `實際 ${res.status}`);
  check('導向登入頁並帶錯誤標記', location.includes('/r/login?error=invalid'), location);
  check('未設定 session cookie', !(res.headers.get('set-cookie') || '').includes('reader_session'));
}

console.log(`\n=== 結果：${failures === 0 ? '全部通過' : `${failures} 項失敗`} ===\n`);
process.exit(failures === 0 ? 0 : 1);
```

- [ ] **Step 5: 執行驗證腳本**

```powershell
node scripts/verify-reader-flow.mjs
```

Expected: `=== 結果：全部通過 ===`

第 5 節會提示手動完成登入驗證——這是刻意的設計結果（明文 token 不入庫，無法從腳本取得）。請依提示以方式 (b) 在本機臨時加一行 log 完成一次完整登入，確認：

- 登入後 cookie 已設定
- 再次讀 `/r/news/e2e-test` 時**受限區可見**
- `reader` 表新增一列、`credit_txn` 有一筆 300 點的 `SIGNUP_GRANT`
- `survey_response.last_engaged_at` 已更新

**驗證完務必移除那行臨時 log**，並確認未被提交。

- [ ] **Step 6: spec §4.0 部署前置檢查**

正式部署前逐項確認：

```powershell
cd d:\GitHub\hahow-ai-full-stack\survey-backend
# 1. 確認 migration 內無禁止的 SQL
Select-String -Path src/main/resources/db/migration/V7*.sql, src/main/resources/db/migration/V8*.sql -Pattern "DROP |TRUNCATE|DELETE FROM"
# 2. 確認 ddl-auto 仍為 validate
Select-String -Path src/main/resources/application.yml -Pattern "ddl-auto"
# 3. 確認全部測試綠
$env:JAVA_HOME = "D:\java\jdk-21"
mvn clean test
```

Expected:
1. 第一項**無輸出**（V8 的 `UPDATE ... WHERE consent = TRUE` 是唯一允許的既有列改寫，且不在此比對範圍內）
2. `ddl-auto: validate`
3. `Tests run: 178, Failures: 0, Errors: 0`

**另外必做**：`MigrationSafetyTest` 驗證的是 migration **邏輯**（在乾淨容器上），不是正式資料庫的**真實資料**。部署前仍須對正式 DB 的複本實際套用一次 V7／V8，確認：`survey_response` 筆數不變、既有名單的 email／consent／unsubscribed 未變、已確認訂閱者的 `last_engaged_at` 全部非 NULL。**正式 DB 備份完成前不得部署。**

本機沒有 psql 執行檔；可用專用測試容器 `survey-test-db`（port 5433）執行，例如：

```powershell
docker exec survey-test-db psql -U postgres -d <複本DB> -c "SELECT count(*) FROM survey_response WHERE consent = TRUE AND unsubscribed = FALSE AND last_engaged_at IS NULL;"
```

回傳 0 才算 backfill 完整。

- [ ] **Step 7: Commit**

```powershell
git add scripts/verify-reader-flow.mjs
git commit -m @'
test(reader): 新增讀者端流程驗證腳本

verify-reader-flow.mjs 涵蓋 archive、未登入讀單篇（受限區不得洩漏）、
404、登入信請求、無效 token 不放行。

第 5 節（完成登入）刻意保留為手動步驟並在腳本中說明原因：
明文 token 只存在於寄出的信裡，DB 只有雜湊，無法從腳本反推——
這正是 LoginTokenService 的設計目的。

依 CLAUDE.md 寫成可重跑腳本而非一次性互動指令。

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
'@
```

---

## 階段 B 完成標準

全部滿足才算完成：

- [ ] `mvn clean test` 為 `Tests run: 178, Failures: 0, Errors: 0`
- [ ] `MigrationSafetyTest` 7 個測試通過，且已實測「註解掉 backfill 會讓它變紅」
- [ ] 對正式 DB 複本實際套用 V7／V8 驗證通過（筆數不變、同意狀態未變、backfill 完整）
- [ ] `scripts/verify-reader-flow.mjs` 全部通過
- [ ] 手動完成一次完整登入，確認 `reader` / `credit_txn` / `last_engaged_at` 三處都正確寫入，且臨時 log 已移除
- [ ] 「受限區不洩漏」測試已實測會在授權條件被破壞時失敗（Task 12 Step 9）
- [ ] 實機啟動無 `Schema-validation` 錯誤（Task 5 Step 9）
- [ ] `PackageDependencyTest` 仍通過（`reader` 未反向被下層依賴）
- [ ] 正式 DB 已備份，且 `ddl-auto` 仍為 `validate`

## 給階段 C 的交接資訊

- **扣點路徑的接入點**：`AccessDecisionService.decide()` 中 `NEEDS_CREDITS` 那一段。加入「`credits >= cost` → `FULL` + 扣點」，扣點需 `@Transactional`：先 `INSERT article_access`（撞 UNIQUE 表示併發，轉已解鎖路徑）→ 寫 `credit_txn`（delta 為負）→ `UPDATE reader SET credits = credits - ? WHERE id = ? AND credits >= ?`（回 0 列即回滾）。
- **`recordAccess` 需調整**：目前一律寫 `cost = 0`，階段 C 要改為寫入實扣點數。
- **規則頁 `/r/rules` 與 PREMIUM 發布能力必須同階段上線**（spec §11）——階段 B 刻意只發布 BASIC。
- **`AppSettingService` 已可用**：`CREDIT_PREMIUM_COST`、`CREDIT_REFERRAL_REWARD`、`VIP_DEFAULT_DAYS` 三個鍵已有初始值，規則頁的數字一律從這裡取，不得寫死（spec §5.11）。
- **邀請歸因的暫存位置**：`survey_response.answers` 的 `_ref` 鍵（spec §5.4），欄位已存在無需 migration；注意 `answers` 對匯入者為 NULL，寫入前要初始化。

