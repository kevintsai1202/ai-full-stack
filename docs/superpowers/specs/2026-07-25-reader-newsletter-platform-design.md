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

### 交付範圍（8 個功能區）

| # | 功能區 | 內容 |
|---|---|---|
| 1 | 讀者身分 | email + 收信驗證（magic link）登入，JWT 有效期 4 週 |
| 2 | 內容 archive + paywall | 歷史電子報列表與單篇頁；`<!--paywall-->` 以下需權限 |
| 3 | 點數帳本與規則頁 | 初始贈點、閱讀扣點、後台手動加點，帳本只增不改；`/r/rules` 說明頁 |
| 4 | 邀請成長 | 個人邀請碼、歸因、被邀者確認訂閱後發獎勵 |
| 5 | 寄送紀錄與補寄 | 每篇「誰寄成功／誰失敗」，對未成功寄出者補寄 |
| 6 | 開信追蹤 | 追蹤像素、每篇開信名單與開信率報表 |
| 7 | 內容製作升級 | 圖片上傳（MinIO）、raw HTML 引用、VIP 分組寄送 |
| 8 | 參與度分級與 sunset | 依真實互動分 active / dormant / sunset，發送時選擇級別 |

### 刻意否決的替代方案：寄信扣點

曾評估「每寄一封信就扣該讀者點數、歸零即停寄」，**否決**。記錄理由以免日後重複討論：

1. **消耗方向對著核心讀者**：寄信扣點是被動消耗，讀者什麼都不做也在扣。扣得最快的是訂閱最久、每期都在名單裡的人；而從未開信的殭屍地址扣點速度完全相同。該機制無法區分兩者卻同時處死兩者。
2. **整份名單同步倒數**：所有現有訂閱者從同一天開始扣，約 7 個月後同時歸零（週更、10 點/封計）。這不是篩選讀者，是為整份名單設到期日。
3. **邀請飛輪變質**：回報從「獲得更多深度內容」變成「免於被斷訊」，是懲罰的解除而非獎勵。
4. **價值交換方向相反**：寄信的邊際成本由營運方支付（ZSend 額度），讀者收信是被動接受已同意的事，向其收費無對應價值。

「篩掉不活躍讀者」的正確工具是功能區 8 的 sunset policy——它看真實參與度而非時間流逝，因此鐵粉永不被停寄、殭屍地址快速淘汰，且能保護寄信信譽。

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
│                 UnsubscribeTokenService, AdminImportController, WelcomeMailService,
│                 EngagementService(新)
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

**已知的執行期隱形回邊**（階段 A 全分支審查發現，靜態 import 單向但執行期仍糾纏，階段 B 應處理）：

1. **三個 package 寫死了 `form` 擁有的路由**。`/api/survey/unsubscribe` 與 `/api/survey/confirm` 的端點由 `form/SurveyController` 提供，但 `audience/WelcomeMailService`、`newsletter/CampaignService`、`newsletter/InviteService` 都以**字串**形式組出這兩個網址。`audience → form` 是本節明確禁止的方向，只因為是字串而非 import 才逃過 `PackageDependencyTest`。真要拆服務時，退訂連結會指向被拆走的 form 服務。
2. **`consent` 生命週期由 `form` 的端點驅動**。`SurveyController` 直接呼叫 `unsubscribeByEmail()` 與 `confirmByEmail()`——`survey_response` 的同意狀態變更（本專案最敏感的資料）發生在 `form` 而非 `audience`，而觸發連結的信件由 `newsletter/InviteService` 寄出，形成 `newsletter →(URL)→ form →(write)→ audience` 的隱形環路。

**處置：改期至階段 C**（原定階段 B，執行時未納入任務範圍）。做法是把這兩個端點搬進 `audience`（它們本質是名單同意管理，不是問卷表單功能），並把連結組裝集中成 `audience` 的單一 builder，讓三處字串收斂到一個擁有者。做完之後這條拆解線才真的可拆。

> 改期理由：階段 B 的範圍已聚焦在讀者端的登入與閱讀，而這項搬遷會動到既有的 confirm／unsubscribe 閉環（18 個 `SurveyControllerTest` 覆蓋的行為），風險與階段 B 的目標無關。**在完成之前，這條「拆解線」是名義上的**——`audience` 與 `newsletter` 仍以字串形式依賴 `form` 的路由，而 `PackageDependencyTest` 抓不到字串依賴。

**`form` 與 `newsletter` 之間不授權任何方向的依賴**。目前實際上也沒有這條 import；`PackageDependencyTest` 尚未涵蓋上層之間的檢查（該測試的 Javadoc 已載明此盲區），階段 B 新增 `reader` 時需特別留意——`reader → newsletter` 是唯讀 `Campaign`，是本節唯一授權的上層間依賴。

**共用元件**（`AdminKeyGuard`、`ApiExceptionHandler`、`WebConfig`、`SurveyApplication`、`TrackingController`）留在根 package，並新增 `AppSettingService`（可調參數讀寫，見 §9.1）——它跨越多個領域（點數參數給 `reader`、參與度門檻給 `audience`），不屬於任何單一 package。

`TrackingController` 是廣告追蹤腳本產生器（GA4／Meta Pixel／LINE Tag，供 land-page 與問卷頁共用），**與 §5.7 的開信追蹤無關**，不屬任何單一領域故留在根 package。

**前端**：延續現有 vanilla HTML + JS + CSS、無建置步驟的做法，與 `index.html` / `admin.html` 一致。新增靜態頁放在 `src/main/resources/static/`。

## 4. 資料模型

### 4.0 硬約束：既有資料不可清除

現有的訂閱名單與匯入名單（含 exam 匯入的 254 筆）是**不可重建的資產**——訂閱者的同意是他們親自點擊確認信給出的，一旦清除無法補回，只能重新徵求同意。本 spec 的所有變更受以下約束：

**禁止事項**（適用於 migration、實作與本機測試）：

- 任何 `DROP TABLE` / `DROP COLUMN` / `TRUNCATE`。
- 任何無 WHERE 條件的 `DELETE`。
- 任何會改寫既有列的 `UPDATE`，除 §4.2 的 `last_engaged_at` backfill（僅寫入原本為 NULL 的新欄位）。
- 對正式 DB 執行 `flyway clean`（應以 `spring.flyway.clean-disabled=true` 強制關閉）。
- 本機開發與測試一律使用獨立資料庫，**不得**連線正式 DB 跑測試。

**已驗證的安全事實**（階段 A 的 package 分層不影響資料）：

| 風險 | 查核結果 |
|---|---|
| Hibernate 重建或刪表 | `application.yml` 為 `ddl-auto: validate` — 只驗證不修改結構 |
| 搬 package 導致表名改變 | 四個 entity 皆有明確 `@Table(name = "...")`，與類別所在 package 無關 |
| 搬 package 導致 JPQL 失效 | `SurveyResponseRepository` 的 `@Query` 使用 entity **簡名**（`SurveyResponse`），Hibernate 以簡名解析，搬 package 不改類名故仍有效 |
| Flyway baseline 誤跳 | 正式 DB 已套用 V1–V6，V7/V8 為單純續接 |

**變更策略**：所有 migration 一律 additive（`CREATE TABLE` / `ADD COLUMN` / `CREATE INDEX`）。新欄位皆可為 NULL 或帶 DEFAULT，既有列不需改寫即維持有效。`reader`、`credit_txn`、`article_access` 等讀者端資料全部落在新表，`survey_response` 除新增一個欄位外完全不動。

**部署前置檢查**（實作計畫須列為每階段的第一項）：確認正式 DB 已備份、確認 `ddl-auto` 仍為 `validate`、確認本次 migration 檔內無上述禁止的 SQL。

#### 備份機制與 V7/V8 部署前的備份紀錄

備份走 Zeabur 官方 GraphQL API 的 `createBackup`，工具為 [`survey-backend/scripts/zeabur-db-backup.ps1`](../../../survey-backend/scripts/zeabur-db-backup.ps1)（token 從 `$env:ZEABUR_TOKEN` 讀取，故腳本本身可進版控）。

**刻意不採用的做法**：把 PostgreSQL 的連線埠對外開放以便本機 `pg_dump`。那會讓正式庫暴露在公網，風險遠高於備份本身要解決的問題。Zeabur 的 postgresql 服務沒有 public domain，這是它的預設保護，不該為了備份而拆掉。

**V7/V8 部署前的備份（2026-07-25）**：

| 項目 | 值 |
|---|---|
| 備份 ID | `6a6519be8177cae08f172517` |
| 狀態 / 大小 | SUCCESS / 34,312 bytes（ZIP） |
| 格式 | `pg_dumpall` cluster dump（含 role、`CREATE DATABASE zeabur`、5 張表） |
| 還原驗證 | 灌進一次性 PG18 空容器，`psql -v ON_ERROR_STOP=1` 結束碼 0 |
| 筆數 | `survey_response` 308、`email_log` 553、`campaign` 4、`mail_template` 1、`flyway_schema_history` 6 |
| 結構 | 5 個 PK、1 個 unique、10 個索引全數重建；Flyway 1–6 皆 `success = true` |

> **「status = SUCCESS」不等於備份可用。** 0 位元組的 dump、只含 schema 不含資料的 dump 都可能是 SUCCESS。備份唯一的用途是還原，所以驗收條件必須是「實際還原一次且筆數吻合」。上表的還原驗證正是因此而做，也順帶發現一件事：cluster dump 必須灌進**空的 cluster**——灌進既有測試庫會因 `role "root" already exists` 中止，那是還原程序的錯而非備份的錯，但若沒實測過，災難當下才發現就來不及了。
>
> **這次備份也是本專案的第一次備份**（備份清單原本是 0 筆）。建議另行以 `setAutoBackup` 開啟每日自動備份，否則「有備份」這件事只在人記得的時候成立。

**backfill 目標數已由 93 更新為 95**：複本驗證（紀錄於 `.superpowers/sdd/progress.md`）當時 `consent = true` 為 94 筆，備份驗證時為 96 筆，而總筆數兩次都是 308、`source` 分布與最新 `created_at`（2026-07-24）均未變——所以不是新增訂閱，而是**兩筆既有資料的 `consent` 由 false 翻成 true**（兩人點了確認信）。扣掉 `consent = true` 且 `unsubscribed = true` 的 1 筆（id=45），§4.2 的 backfill 會命中 95 列。

> 這個數字會隨上線前的真實訂閱行為繼續變動，**不該被當成驗收條件**。backfill 的正確性來自它的 WHERE 條件（`consent = TRUE AND unsubscribed = FALSE`），不是來自某個特定筆數。記錄它的用途是部署後對照「命中列數是否落在合理範圍」。

### 4.1 新增表

> **版本歸屬**：V7 只建立階段 B 用得到的五張表（`app_setting`、`reader`、`credit_txn`、`article_access`、`login_token`）。`email_open` 與 `media_asset` 的結構列在下方供設計參考，但**實際建立於後續階段**（分別是階段 E 的 V9 與階段 D 的 V10）——刻意不在用不到的時候建表。

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

-- V9（階段 E 才建立，此處僅列出結構供設計參考）：開信事件。同一人可多次開信，全部記錄
CREATE TABLE email_open (
    id          BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT      NOT NULL,
    recipient   TEXT        NOT NULL,   -- 正規化小寫
    user_agent  TEXT,
    opened_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_email_open_campaign ON email_open (campaign_id, recipient);

-- V10（階段 D 才建立，此處僅列出結構供設計參考）：媒體檔案索引。實體存 MinIO，此表只記中介資料
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
ALTER TABLE campaign ADD COLUMN filter_levels   TEXT NOT NULL DEFAULT 'active';   -- 本次寄送的參與度級別（逗號分隔），供補寄重建對象
CREATE UNIQUE INDEX uq_campaign_slug ON campaign (slug) WHERE slug IS NOT NULL;

-- 防呆：PREMIUM 必須有解鎖成本，否則等同免費卻標成付費內容
ALTER TABLE campaign ADD CONSTRAINT ck_campaign_premium_cost
  CHECK (tier <> 'PREMIUM' OR credit_cost > 0);
```

發布 API 亦須在寫入前驗證此規則，回 400 並附明確訊息（不要只靠 DB 約束丟出 500）。

```sql
-- V8：參與度時間戳。放在名單中心而非 reader，因為未登入過的殭屍地址沒有 reader 列，
-- 但正是最需要被 sunset 判定的對象
ALTER TABLE survey_response ADD COLUMN last_engaged_at TIMESTAMPTZ;
CREATE INDEX idx_survey_response_engaged ON survey_response (last_engaged_at);

-- 【必要 backfill】既有訂閱者在此之前沒有參與度追蹤，last_engaged_at 全為 NULL。
-- 若不回填，他們的「已寄期數」可能早已超過淘汰門檻（12 期），依 5.10 規則會被判為
-- sunset —— 分級功能上線當天，所有老訂閱者整批停收電子報。資料沒少，但收不到信，
-- 且症狀要到下次發送才會顯現，極難察覺。
-- 以 migration 執行時間作為參與度起算點，讓既有訂閱者一律從 active 開始，
-- 並享有完整觀察期（6 期／90 天）後才可能依「migration 之後的真實行為」被降級。
UPDATE survey_response
   SET last_engaged_at = now()
 WHERE consent = TRUE AND unsubscribed = FALSE;
```

未確認訂閱者（如已匯入的 exam 名單）**刻意不回填**，保持 NULL：他們從未被寄過電子報（邀請信 `type='invite'` 不計入已寄期數），因此「已寄期數 < 沉睡門檻」條件成立，仍會被判為 active。回填反而會讓他們的參與度時間戳虛假。

`survey_response` 的其餘結構不變。讀者自助維護的個人資料寫回它既有的 `name` / `role` / `experience` / `interest` / `answers` 欄位。

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
- **email 不存在名單中也照樣可登入**：不拒絕登入——降低摩擦，且讓「我明明訂閱了為何進不去」這類客訴消失。登入後導向 `redirect` 參數指定的頁面（預設 `/r/archive`）；未確認訂閱者在讀文章時會被 `AccessDecisionService` 判為 `NOT_SUBSCRIBED`，由頁面的 gate 區塊引導他去訂閱。
  > 實作註記：原設計寫「登入後導向訂閱確認流程」，實際實作是導向原本要看的頁面並由 gate 引導。後者的摩擦更低（讀者仍能瀏覽免費區），且不需要一條專屬的確認流程頁。

**節流**：同一 email 15 分鐘內最多發 3 次登入信（查 `login_token` 計數），避免被當寄信放大器。

### 5.2 閱讀授權（單一決策點）

`reader/AccessDecisionService.decide(readerOrNull, campaign) → FULL | PARTIAL`

```
未發布（published_at 為 null）              → PARTIAL（草稿不對任何人開放，連 VIP 也不行）
未登入                                    → PARTIAL
未確認訂閱（consent=false 或 unsubscribed） → PARTIAL
campaign.tier 精確等於 'BASIC'             → FULL
reader.tier == VIP 且未到期                → FULL（並補寫 article_access，cost=0）
已存在 article_access                     → FULL
credits >= campaign.credit_cost           → PARTIAL + CAN_UNLOCK（顯示解鎖按鈕，尚未扣點）
否則                                      → PARTIAL（回傳「還差幾點」與邀請碼）
```

**四條在實作階段由審查補上的規則**，都是 fail-closed 方向的收斂：

1. **BASIC 判斷必須是「精確等於 `'BASIC'`」，不可寫成「不是 `'PREMIUM'`」。** 後者會讓 `tier` 打錯字（小寫 `premium`、前後空白、`null`）的進階文章被判為 BASIC 而全文外洩。而資料庫層沒有 `tier IN ('BASIC','PREMIUM')` 白名單，`ck_campaign_premium_cost` 也只檢查 `tier <> 'PREMIUM' OR credit_cost > 0`——所以 `tier = 'premium'` 會同時繞過該 CHECK 與 paywall。
2. **未發布檢查放在最前面。** 草稿的授權前提不該留給呼叫端判斷，否則「唯一的授權決策點」名不副實。
3. **`recordAccess()` 只在 VIP（以及階段 C 的付費解鎖）時寫入 `article_access`，不對 BASIC 寫。** 因為 `article_access` 同時是 `ALREADY_UNLOCKED` 的判斷來源：若 BASIC 閱讀也留紀錄，文章日後改為 PREMIUM 時，該讀者會走 `ALREADY_UNLOCKED` 永久免費。
4. **`resolveCost()` 永遠回 ≥ 1。** 若後台把 `credit.premium_cost` 設成 0 或負數，階段 C 接上 `credits >= cost` 後會變成「所有 PREMIUM 免費」。
5. **餘額足夠不等於直接放行。** spec 原本寫「`credits >= cost` → FULL + 扣點」，實作改為「PARTIAL + 顯示解鎖按鈕，讀者確認才扣」。理由：讀者從電子報連結點進來就被無感扣點，會被感受為未經同意的收費，而 §5.11 整節的訴求正是點數機制的可信度；誤點的成本從「10 點」降為「0」。代價是多一次互動，但這次互動本身就是讓讀者理解機制的時機（gate 區塊同時放規則頁連結）。實際扣點集中在 `UnlockService`，`decide()` 維持純函式。

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
- 冪等：由資料庫的 `uq_credit_txn_referral_note`（V9）保證，`note` 存被邀者 email，重複 confirm 不重複發獎。

> **已處理（V9，`fix/stage-c-followups`）**：原本的冪等是 check-then-act
> （先 `existsByReasonAndNote` 再寫），而 `credit_txn` 沒有唯一索引——對比
> `article_access` 有 `uq_article_access` 作為併發防線，這裡沒有對應設計。
> 若同一封確認信的連結被**近乎同時**觸發兩次（Outlook Safe Links、Gmail 的圖片
> 代理會對信中連結做背景 GET，與使用者本人的點擊構成真實的併發），
> 兩個獨立交易可能都在對方提交前判讀為「未發過」而各自發獎。
>
> **已實作的修法**：
> ```sql
> CREATE UNIQUE INDEX uq_credit_txn_referral_note
>     ON credit_txn (note) WHERE reason = 'REFERRAL';
> ```
> 三個實作細節都在 migration 與 javadoc 內留了理由：
>
> 1. **必須是部分索引**，不可寫成 `(reason, note)` 複合唯一。其他 reason 的 note
>    本來就會重複而且是日常操作：`SIGNUP_GRANT` 的 note 對每位讀者都是同一句
>    「首次登入初始贈點」、`READ` 的 note 是文章主旨（第二個人解鎖同一篇就重複）、
>    `ADMIN_GRANT` 的批次加點每筆都填同一句說明。複合唯一會讓第二位讀者建不了帳。
> 2. **`note IS NULL` 的 REFERRAL 列不受保護**（PostgreSQL 的 UNIQUE 視 NULL 互異）。
>    這是刻意保留的殘留空隙：程式端保證 REFERRAL 一律寫入被邀者 email，
>    由 `ReferralServiceTest.ledgerNoteIsExactlyTheInviteeEmail` 釘住那個值；
>    在述詞裡再加 `AND note IS NOT NULL` 對唯一性語意毫無差別，只會看起來像多了保護。
> 3. **前置檢查整個移除**（連 `CreditTxnRepository.existsByReasonAndNote` 一併刪掉），
>    改成「直接寫、撞了才知道」。留著它會讓唯一有效的防線在絕大多數情境永遠跑不到。
>
> **捕捉位置（本專案已被咬過三次的陷阱）**：`rewardFor` 內部**絕不捕捉**
> `DataIntegrityViolationException`——交易一旦被標記 rollback-only，
> 在其內部捕捉後正常回傳會在 commit 時改拋 `UnexpectedRollbackException`
> （與 `UnlockService` 同一個陷阱，見 §5.2）。因此：
> `rewardFor` 改為 `@Transactional(REQUIRES_NEW)`（唯一的交易邊界就在它的 proxy 上），
> `ReferralRewardListener` **移除 `@Transactional`** 並在交易邊界外捕捉、視為
> `ALREADY_REWARDED`（記 INFO 不記 ERROR：舊確認信被重複點擊是常態，記 ERROR 會蓋掉真失敗），
> 帳本寫入改用 `saveAndFlush` 讓約束違反在該行就被 repository 轉譯成
> `DataIntegrityViolationException`。與 `UnlockController`／`UnlockService` 這一對完全同構。
>
> **驗證**：`ReferralIdempotencyTest`（真實 PostgreSQL、套用全部 migration、
> `ddl-auto=validate`）直接讀 `pg_indexes` 斷言索引本體與述詞，並實跑
> 「第二次發獎被擋下且餘額與帳本一起不變」。破壞性驗證實測：把 UNIQUE 拿掉 →
> 餘額變 200／帳本兩筆；把捕捉點搬進 `rewardFor` →
> 真的拋出 `UnexpectedRollbackException: Transaction silently rolled back`。

> **實作偏離本節「同一交易內」的描述，原因與取捨如下**：
>
> 1. 實作用普通的 `@EventListener`，**不是**「同一交易內」發放獎勵。獎勵自己的交易由
>    `ReferralService.rewardFor` 的 `@Transactional(REQUIRES_NEW)` 開；**V9 之後監聽器本身刻意不帶
>    `@Transactional`**（原本在監聽器上），否則它的 `catch` 會落在交易邊界之內，見上面的冪等說明。
> 2. **為什麼不能用 `@TransactionalEventListener(AFTER_COMMIT)`**（本計畫第一版寫錯，已修）：發布端 `SubscriptionController.confirm` **沒有交易**——它呼叫的 `confirmByEmail` 與 `touchEngagement` 是 repository 上各自帶 `@Transactional` 的方法，各自立即提交。`publishEvent` 被呼叫時沒有進行中的交易，而 `@TransactionalEventListener` 在無交易時**預設完全不觸發也不報錯**。結果會是「獎勵永遠不發放、日誌乾淨、測試因監聽器被 mock 而全綠」——最惡劣的靜默失效。
> 3. 不需要同交易也已保住優先順序：確認訂閱在 publish 之前就已提交，所以發獎失敗時同意紀錄已落地。這正是要的方向——確認訂閱是不可重建的同意紀錄，推薦獎勵可由後台手動加點補救；spec 原本寫的「同一交易」會讓可補救的失敗回滾掉不可補救的資產。
> 4. `REQUIRES_NEW` 的作用是讓獎勵的兩個寫入（加餘額、寫帳本）成為一個原子單位，**不是**為了與確認訂閱隔離（本來就已隔離）。
>    它從監聽器移到 `rewardFor` 之後這個作用不變，還多了一個好處：交易邊界固定在 `rewardFor` 的 proxy 上，不受呼叫端有沒有交易影響。
> 5. 例外在監聽器與發布端**雙重**吞掉：`publishEvent` 同步且例外會往上拋，若變成 500，「不論結果一律回相同的 200」這條安全性質就破了，公開端點會變成「這個 email 有沒有推薦關係」的探測器。防護不依賴任一端記得。
>    **V9 之後兩道的職責變了**：監聽器不再是交易邊界，所以它的 `catch` 現在是真正接住的那一層；發布端那道從「唯一真正接住的層」變成縱深防禦（防任何現有或日後新增的監聽器漏出例外）。
> 6. 代價與補救：發獎失敗會靜默損失一次獎勵，以 ERROR 記錄，後台手動加點。
> 7. 依賴方向：事件是為了不讓 `audience` 依賴 `reader`（spec §3）。
>
> **邀請獎勵關成 0 時，`/r/invite` 的「已成功邀請人數」會停止成長**
> ——**已處理（B1，`fix/stage-c-followups`）**，以下保留當時的分析作為紀錄，
> 修法與現況見本段末尾的「已實作」小節：
> `ReferralService.stats()` 數的是該推薦人 `credit_txn` 中 `reason='REFERRAL'` 的筆數，
> 而 `rewardFor()` 在 `creditPolicy.referralReward() <= 0` 時**完全不寫帳本**
> （刻意設計：不佔用冪等鍵，日後把獎勵調回 100 時這位被邀者仍拿得到獎勵）。
> 兩件事合起來的結果是：後台把 `credit.referral_reward` 設為 0 之後，朋友確實完成了
> 確認訂閱，但邀請人的頁面完全沒有反應——人數不動、也沒有任何「已記錄但暫停發放」的痕跡。
> 而 `/r/invite` 與 `/r/rules` 的文案在獎勵為 0 時原本寫的是「目前邀請獎勵暫停發放，
> **成功邀請仍會被記錄**」，這句話不成立——而且會造成**同一個 HTTP 回應內自我矛盾**：
> `/r/invite` 上半頁承諾「仍會被記錄」，下半頁的成效區塊卻顯示「還沒有人透過你的連結
> 完成訂閱」，即使讀者已經成功邀請了五個人。
>
> **文案已修（全分支終審）**：兩處都改成「目前邀請獎勵暫停發放，恢復發放後成功的邀請
> 才會開始累計」——不再對行為做出程式碼不保證的承諾。兩份測試以「承諾字眼黑名單」
> 釘住（換句話說也不行，換掉的必須是那個承諾本身）。**機制**仍待下一階段。
>
> **影響**：只有在站方主動把獎勵調成 0 時才會發生，且不影響任何帳務正確性；
> 但它是一句對讀者的明確承諾沒有兌現。
>
> **已實作（B1）**：計數來源與「發了多少獎」脫鉤，但**不需要新表也不需要新欄位**
> ——`reader.referred_by` 早在 V7 就存在，`ReaderAccountService.findOrCreate` 也已經
> 在建帳時填好（含排除自我邀請）。當時的分析漏看了這一點。
>
> - **人數**改為 `ReaderRepository.countByReferredBy(referrerId)`；
> - **點數**仍加總 `credit_txn` 的 REFERRAL delta（帳本是稽核來源，不可用
>   「人數 × 目前獎勵金額」推算——獎勵金額會被調整，推算值會與歷史實付金額不符）。
>
> **兩個數字從此會有落差，而且兩個方向都可能**，這是刻意接受的：
> ① 獎勵暫停期間人數成長、點數不動（正是本次要修的情境）；
> ② 被邀者**早就有 reader 帳戶**時發了獎但人數不動（`referred_by` 只在建帳當下寫入、
> 既有帳戶不回填）。②的對象本來就不是「新帶進來的讀者」，不計入可接受，帳務仍正確。
> 完整說明寫在 `ReferralService.stats` 的 javadoc。
>
> **文案再修一次（重點）**：`referred_by` 是在被邀者**首次登入**時寫入，**不是**在他
> 點確認信時。所以「成功邀請仍會被記錄」即使在 B1 之後**仍然是過度承諾**——
> 朋友確認了訂閱但還沒登入的那段期間，人數確實不會成長。兩處 0 值文案因此改成
> 「目前邀請獎勵暫停發放；朋友完成訂閱**並首次登入**後，仍會計入你的邀請人數，
> 點數則要等恢復發放後才開始累計」。兩份測試改為正面斷言：必須同時出現
> 「暫停發放」「邀請人數」「首次登入」，並禁止「點數仍會累計」這類說法
> （破壞性驗證：把條件句刪掉改回「成功邀請仍會被記錄」→ 測試變紅）。

> **另一項必須寫進 spec 的發現**：`confirmByEmail` 的 JPQL 是 `update ... set consent = true where lower(email) = lower(:email)`，**沒有排除 `unsubscribed = true`**，而 PostgreSQL 的 UPDATE 即使值沒變也會回報 1 列。所以 `SubscriptionController` 那道 `affected > 0` 的條件，實際上只擋掉「名單裡完全沒有這個 email」——已退訂者、早已確認過的人，每次點舊連結都會發出事件。因此 `ReferralService` 的冪等檢查（`credit_txn` 的 `note` = 被邀者 email）**不是可選的加強，而是唯一真正的防重複發獎機制**，不可簡化。

### 5.5 發送：依 tier 分組（兩種內文）

改寫 `CampaignService.send`：

```
取收件人：既有 filter_role / filter_interest 過濾
          → 再以參與度級別過濾（filter_levels，見 5.10）
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

### 5.10 參與度分級與 sunset policy

**目的**：不再寄給早已不看的人（保護寄信信譽與送達率），同時保留對這些人寄「重大優惠／召回信」的能力。

**唯一的參與度事實**：`survey_response.last_engaged_at`。以下行為全部更新它（取 `now()`）：

| 行為 | 可靠性 | 寫入點 |
|---|---|---|
| 點確認信完成訂閱 | 高 | `/api/survey/confirm` |
| 開信（追蹤像素） | 低（信箱常封鎖圖片） | `email_open` 寫入時 |
| magic link 登入成功 | 高 | 登入流程 |
| 解鎖／閱讀文章 | 高 | `article_access` 寫入時 |
| 更新個人資料 | 高 | `/r/me` 儲存時 |

**分級規則**（`audience/EngagementService` 即時計算，**不物化 level 欄位**——衍生值物化會過期）：

```
已寄期數 = email_log 中該 recipient 且 type IN ('campaign','campaign_resend') 的 campaign 去重計數

active   ← last_engaged_at 在 90 天內
         或 已寄期數 < 沉睡門檻(6)          ← 新訂閱者未達觀察期，一律視為 active
dormant  ← 不符 active，且 已寄期數 < 淘汰門檻(12)
sunset   ← 已寄期數 >= 淘汰門檻(12) 且 last_engaged_at 為 NULL 或超過 365 天
```

**關鍵邊界條件一（新訂閱者）**：新訂閱者 `last_engaged_at` 可能為 `NULL`（匯入者尚未確認、或確認後從未開信）。若不特別處理，新人**第一封信就會被判為不活躍而收不到**——這會讓整個訂閱漏斗失效。因此「已寄期數 < 沉睡門檻」必須優先於時間判斷。

**關鍵邊界條件二（migration 當下的既有訂閱者）**：現有訂閱者在本功能之前完全沒有參與度紀錄，`last_engaged_at` 為 NULL，但「已寄期數」可能早已超過淘汰門檻 —— 兩個條件同時成立就是 `sunset` 的定義。若不處理，**分級上線當天所有老訂閱者整批停收電子報**。已由 §4.2 的 backfill 解決（回填 `now()`，從 active 起算並享有完整觀察期）。此為 §4.0 硬約束下唯一允許改寫既有列的操作。

**發送時的級別選擇**：`AdminCampaignController` 的發送與排程請求新增 `levels` 參數（可多選 `active` / `dormant` / `sunset`）：

- 常規電子報 → 只勾 `active`（預設）
- 重大優惠、課程開賣、召回信（即「訂閱廣告」）→ 勾 `active` + `dormant`
- `sunset` 預設不勾，需明確勾選才寄（保留能力但不會誤觸）

級別過濾疊加在既有的 `filter_role` / `filter_interest` 之上，`campaign` 表記錄本次所選級別（新增 `filter_levels TEXT` 欄位，逗號分隔），供補寄時重建相同對象。

**與開信率下限的關係**（見 5.7）：開信是低可靠訊號，因此門檻刻意放寬（6 期沉睡、12 期淘汰、365 天），且登入／解鎖／改資料這些高可靠行為都會重置。設計上寧可留下一個不活躍者，也不要誤斷一個只是封鎖圖片的真實讀者。

**不做的部分**：不自動改變任何人的訂閱狀態（`consent` / `unsubscribed` 完全不動）。sunset 只影響「這次要不要寄給他」，不是退訂。讀者任何時候回來互動，`last_engaged_at` 更新後自動回到 active，無需人工處理。

### 5.11 遊戲規則頁（`/r/rules`）

**目的**：讀者要能一眼看懂「點數怎麼來、怎麼用、為什麼有些文章要點數」。這頁是點數機制的可信度來源——機制不透明時，扣點會被感受為不當收費。

**曝光位置**（三處，缺一不可）：

1. 訂閱入口 `/r/`：訂閱表單下方連結。
2. **PARTIAL 的解鎖提示區塊**：最重要的一處。讀者第一次遇到點數就是在這裡，也是唯一會認真讀規則的時刻。
3. `/r/me` 的餘額區塊旁。

**所有數字動態注入，不寫死**：頁面渲染時從 `AppSettingService` 取值填入。涵蓋初始贈點、PREMIUM 單篇點數、邀請獎勵、VIP 效期。

> **這是硬要求**：§9.2 明訂第一版參數就是要靠上線後數據校準的。若規則頁寫死「一篇 10 點」而後台已調成 50 點，讀者看到的代價與實際扣的不一致——這是最傷信任的一類落差。同理，`/r/me` 與解鎖提示區塊的數字也一律走同一個來源。

> **階段 C 的實際狀態與一處必須講清楚的偏離**（端到端驗收 `verify-stage-c.mjs` 實測）：
>
> 三處的數字都不是寫死的，但**來源不是同一個**：
>
> | 位置 | 數字來源 | 改 `credit.premium_cost` 後會變嗎 |
> |---|---|---|
> | `/r/rules` | `CreditPolicy.premiumCost()` | 會 |
> | `/r/me` | `CreditPolicy.premiumCost()` | 會 |
> | paywall gate（`/r/news/{slug}`） | `CreditPolicy.costOf(campaign)` → **該篇的 `campaign.credit_cost`** | **不會** |
>
> `costOf()` 的設計是「文章自訂值優先，未設定（0）時退回全域預設」，但
> `ck_campaign_premium_cost` 強制 PREMIUM 的 `credit_cost > 0`，而
> `CampaignService.validateCreditCost` 也對 PREMIUM 要求 `creditCost > 0`——
> 所以**任何一篇 PREMIUM 文章的 `credit_cost` 都不可能是 0，全域退路那條分支在實務上是死碼**。
> 結果是：後台調整 `credit.premium_cost` 只會改變「規則頁與帳戶頁上的說明數字」，
> 以及**日後新文章的預設填值**，不會改變任何已發布文章的實際解鎖價。
>
> **這算不算違反本節的硬要求？** 本節真正要防的落差是「讀者看到的代價 ≠ 實際扣的點數」。
> 這一條**成立**：gate 顯示的數字與 `UnlockService` 扣的是同一個 `costOf(campaign)`，
> 端到端驗收有專門的斷言（gate 顯示 20 → 實扣 20）。不成立的是本節文字暗示的
> 「三處會同步變動」——因為每篇文章有自己的定價，本來就不該被全域參數追溯改價
> （已解鎖的讀者付的是當時的價）。
>
> **已處理（全分支終審）**：`/r/rules` 與 `/r/me` 的文案已改為「進階文章**通常**每篇 N 點，
> 實際點數以各篇文章頁顯示為準」。原本排給階段 D，但那個排期低估了嚴重性——因為
> `costOf()` 的全域退路是死碼，這兩頁顯示的 `premiumCost()` **在任何情況下都不會是實際扣款額**
> （除非數值恰好巧合）。這不是「三處偶爾不同步」的邊緣情況，而是「規則頁上那個數字
> 結構性地永遠是錯的」。規則頁的存在理由（本節）就是點數機制的可信度來源，
> 帶著結構性錯誤的數字上線比沒有規則頁更傷；而修法是純文案、零行為風險，沒有延後的理由。
>
> 同時補上規則頁的 VIP 段落：「**VIP 到期後，VIP 期間讀過的文章仍然永久免費**」。
> 這是 `AccessDecisionService.recordAccess` 已經成立的行為（VIP 閱讀寫一筆 `cost=0` 的
> `article_access`，到期後仍命中 ALREADY_UNLOCKED），對讀者有利，講出來只會加分。
> 三條文案都有測試釘住（`RulesPageControllerTest`、`ReaderPortalControllerTest`）。

**內容綱要**（以讀者會問的問題組織，非條文式）：

| 段落 | 要回答的問題 | 注意 |
|---|---|---|
| 訂閱能拿到什麼 | 免費訂閱者看得到什麼？ | 講清楚 BASIC 全文免費，不是誘餌 |
| 點數怎麼來 | 初始贈點、邀請獎勵、活動贈點 | 數字動態注入 |
| 點數怎麼用 | 進階文章解鎖，**一次解鎖永久可讀** | 這點對讀者有利，必須明講 |
| 點數會不會過期 | 不會 | 明確承諾，見 §9.2 |
| 為什麼有些文章要點數 | 一句話說明深度內容的取捨 | 誠實，不要包裝成「限量特權」 |
| VIP 是什麼 | 怎麼取得、有什麼差別 | 措辭見下方（已定案，逐字採用） |
| 邀請怎麼算成功 | 被邀者點確認信才算 | 先講清楚避免爭議 |
| 最後更新日期 | — | 規則涉及權益，必須有日期 |

**VIP 段落的措辭**（已定案，實作時逐字採用）：

> 「VIP 目前由站方主動授予給課程學員與合作夥伴，尚未開放付費訂閱。VIP 期間所有進階內容不需點數。」

理由：VIP 若寫得模糊（「特定讀者可獲得」）會顯得不透明，反而傷害整頁的可信度。明確說明「尚未開放付費」既誠實，也為日後開賣自然鋪路——讀者已經知道這條路存在。

**未來付費機制的預留**（已定案）：頁面結構預留付費段落的位置，但**第一版不顯示、不預告時程、不開放候補名單**。理由是預告未定案的收費會提前引發疑慮，而且時程一延就成為信任負債。等真的要做時，付費是本 spec 之外的獨立設計（涉及訂單、發票、續訂、退款——見 §2 非目標）。

**規則變更對既有讀者**：規則頁載明「點數規則調整不會扣減既有點數餘額」。這是 `credit_txn` 只增不改的帳本設計本來就成立的性質（調整參數只影響未來的扣點金額，不回溯），把它寫成對外承諾沒有額外成本，卻能免掉調參數時的爭議。

**刻意不做 CMS**（YAGNI）：頁面骨架與文案寫在靜態 HTML，只有數字動態注入。文案大改需要部署一次，但這頻率遠低於參數調整。不為此建 `static_page` 表或後台編輯器——`mail_template` 那種入庫模式是因為信件範本需要頻繁微調，規則頁沒有同等需求。

## 6. 寄信額度：交易信優先權

**風險**：ZSend 有日／月額度上限（`MailQuotaService` 已能動態偵測）。magic link 登入信是**交易信**——讀者當下在等它。若額度被電子報群發吃光，讀者將無法登入，這是產品級故障，而不只是「信晚一點到」。

**對策**：
1. 設定 `app.mail.transactional-reserve`（預設 50 封）。
2. 群發與補寄前檢查：`剩餘額度 - reserve` 才是可用於行銷信的量，不足則縮減批量並在後台顯示原因。
3. 登入信、確認信、歡迎信不受 reserve 限制（它們就是 reserve 的使用者）。
4. 登入信送出失敗時，前端明確告知「登入信寄送失敗，請稍後再試」，不要顯示成功假象。

> **階段 C 的實際狀態（誠實記錄）**：第 1、3、4 項完成；第 2 項**部分完成**——「群發前檢查、不足則縮減批量並在後台顯示原因」已完成（第 2 項原規劃在階段 D，依使用者決定提前至階段 C 完成，見 Task 13、Task 14），但「**補寄**前檢查」尚未成立，因為補寄功能本身要到階段 E 才存在。`MailQuotaService.current()` 回傳的 `Quota` 新增 `reserve`／`marketingRemaining`／`marketingBatchMax` 三個欄位，`marketingRemaining = max(0, 剩餘額度 - reserve)`；`reserve` 讀自 `app.mail.transactional-reserve`（預設 50 封），不再是「存在但沒人讀」的死設定。負值的 reserve 會被夾到 0（負數會反向放大可用量）；偵測失敗（`source=fallback`）時行銷可用量另外收斂到保守常數，避免把 `MAIL_FALLBACK_QUOTA` 調大就等於解除保護。
>
> `CampaignService.send()` 與 `reschedule()` 在取得收件人清單後、建立／更新 campaign 之前檢查此值：`marketingRemaining <= 0` 時整批拒絕並回 409（不寄 0 封後回報成功）；收件人數超過 `marketingRemaining` 時縮減批量，縮減人數以 `SendResult.skippedForQuota` 回報，且 `campaign.recipientCount` 記錄的是**實際寄送人數**（供階段 E 補寄算差集使用）。登入信、確認信、歡迎信完全不受此檢查影響——它們走的是 `LoginMailService` 等既有路徑，未經過 `CampaignService`。
>
> **第二條路徑（全分支終審發現，已修）**：上面那段原本寫「階段 C 已補上防護」，但防護當時只存在於 `CampaignService`。**邀請信／提醒信走的是 `InviteService`，全程沒有任何額度判斷**，而 `AdminCampaignController.clampLimit()` 用的是 `Quota.batchMax()`（= `min(remaining, 500)`，**未扣 reserve**）。於是月剩餘 300、reserve 50 時，管理者按一次「寄邀請信」就會逐封寄出 300 封把額度歸零，之後讀者點 magic link 收不到登入信——正是本節要防的那個故障，只是從另一個入口發生。物證是 `Quota.marketingBatchMax` 在整個生產程式碼中**零消費者**：正確的值早就算出來了，只是沒接上唯一需要它的呼叫點。
>
> 修法：`clampLimit()` 改用 `marketingBatchMax()`。理由——邀請信是站方主動外推的再徵詢，讀者不在等它，晚一天寄沒有損失；magic link 才是讀者當下盯著信箱等的那一封。另外 `marketingBatchMax` 為 0 時**必須拋 409 而不是把 0 傳下去**：`InviteService` 把 `limit <= 0` 解讀為「不限」，傳 0 的效果會是整份名單全寄，與意圖完全相反且正好發生在額度最吃緊的時候。
>
> **第 3 項的分類仍然成立**：登入信、確認信、歡迎信不受 reserve 限制——它們走 `LoginMailService` 等既有路徑，既不經過 `CampaignService` 也不經過 `AdminCampaignController.clampLimit()`。「不受 reserve 限制」指的是「不被扣掉 reserve 之後的可用量所限」，也就是它們可以動用 reserve；本次改動只約束行銷側（群發、邀請信、提醒信），與這條分類沒有衝突。
>
> 目前狀態：`CampaignService.send()`／`reschedule()`、`POST /api/admin/campaign/invite`、`POST /api/admin/campaign/invite/remind` 三條會實際寄出行銷信的路徑都已受 reserve 保護；「補寄」前檢查仍待階段 E（功能本身尚不存在）。`POST /api/admin/campaign/publish`（只發布不寄送）**不在此清單內且刻意如此**：它一封信都不寄，因此完全不查也不消耗任何額度——若誤加上額度檢查，行銷額度用盡時會讓一篇根本不寄信的文章因為「額度不足」而發不出去。

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
| 名單健康度 | active / dormant / sunset 各級人數與趨勢；可匯出各級名單 | 新增 |
| 發送級別選擇 | 發送與排程時勾選要寄給哪些參與度級別（預設只勾 active） | 擴充 |
| 參數設定 | 修改點數、門檻等所有參數，**存 DB、改完立即生效** | 新增 |

## 8. 讀者端頁面

| 路徑 | 內容 | 需登入 |
|---|---|---|
| `/r/` | 訂閱入口：價值說明 + email 訂閱表單（吃 `?ref=`）+ 規則頁連結 | 否 |
| `/r/rules` | 遊戲規則：點數怎麼來、怎麼用、VIP 是什麼（數字動態注入，見 5.11） | 否 |
| `/r/archive` | 歷史電子報列表（標題、日期、tier、是否已解鎖） | 否 |
| `/r/news/{slug}` | 單篇：免費區 + （FULL 時）受限區 / （PARTIAL 時）解鎖提示 | 否 |
| `/r/login` | 輸入 email 送登入信 / 承接 magic link 回跳 | 否 |
| `/r/me` | 我的帳戶：餘額、交易明細、VIP 狀態、個人資料編輯 | 是 |
| `/r/invite` | 我的邀請碼與連結、已成功邀請人數與獲得點數 | 是 |

沿用 `index.html` 的視覺語言與 vanilla JS 做法，無建置步驟。

## 9. 參數

### 9.1 存放位置：DB 設定表，非 `application.yml`

點數與門檻類參數**必須存 DB 並由後台修改、改完立即生效**。理由是明確的產品意圖：這些數字第一版是估的，要靠上線後的真實行為迭代。放進 `application.yml` 意味著每次調整都要重新部署——實務結果是不會有人去調，參數就永遠停在初版猜測值。

```sql
-- V7：可調參數。key-value 表，型別在讀取端轉換
CREATE TABLE app_setting (
    setting_key TEXT        PRIMARY KEY,
    value       TEXT        NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

讀取端以 60 秒快取（比照 `MailQuotaService` 的既有做法），避免每次授權判斷都打 DB；後台儲存時主動清除快取，做到「改完立即生效」。查無此 key 時退回程式內的預設常數，所以新增參數不需要 data migration。

**留在 `application.yml` 的**（屬部署設定，不是產品參數）：magic link 有效期、JWT 有效期、登入信節流、交易信保留額度、圖片單檔上限、MinIO 連線資訊、各種 secret。

### 9.2 初始值

第一版**照下表上線不調整**，觀察真實解鎖頻率與參與度分布後再校準。

| 參數 | 初始值 | 存放 |
|---|---|---|
| 初始贈點 | 300 | DB |
| PREMIUM 單篇預設解鎖點數 | 10 | DB |
| 邀請成功獎勵 | 100 | DB |
| VIP 預設效期 | 1 年 | DB |
| 沉睡門檻（已寄期數） | 6 期 | DB |
| 淘汰門檻（已寄期數） | 12 期 | DB |
| active 判定天數 | 90 天 | DB |
| sunset 判定天數 | 365 天 | DB |
| 受限區起點 | 作者以 `<!--paywall-->` 標記 | 內容 |
| 點數過期 | 不過期 | 不實作 |
| magic link 有效期 | 15 分鐘 | yml |
| JWT 有效期 | 4 週 | yml |
| 登入信節流 | 同 email 15 分鐘內 3 封 | yml |
| 交易信保留額度 | 50 封 | yml |
| 圖片單檔上限 | 5 MB | yml |
| `vip_full_in_mail` 預設 | false | 每篇設定 |
| 發送預設級別 | 只勾 `active` | 每篇設定 |

**上線後應觀察的指標**（決定怎麼調參數）：PREMIUM 平均解鎖率、300 點的實際耗盡時間中位數、邀請轉換率、各參與度級別的人數分布。

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
純搬移 + 調整 import，不改行為、**不含任何 migration**。既有測試（`SurveyControllerTest`、`InviteServiceTest`）全綠即為驗證。先做這步，後續每個功能才有正確的落點。資料安全性已於 §4.0 逐項查核（`ddl-auto: validate`、entity 有明確 `@Table`、JPQL 用簡名），此階段不動資料庫。

**階段 B：讀者身分 + archive + paywall**（功能區 1、2）
V7/V8 migration（**含 `credit_txn`，因為首次登入即需發 300 點 `SIGNUP_GRANT`**；**含 `app_setting`，因為贈點數必須可調**）、magic link、JWT、`ContentSplitter`、`AccessDecisionService`。此階段的授權只走「未登入 / 未確認訂閱 / BASIC / VIP / 已解鎖」五條路徑——扣點路徑尚未接上。讀者端 4 個頁面（`/r/`、`/r/archive`、`/r/news/{slug}`、`/r/login`）。此階段結束即為可上線產品。

**階段 B 一律只發布 BASIC 文章**（`tier='BASIC'`）。若此時就發 PREMIUM，非 VIP 讀者會撞到一個無法解鎖的 PARTIAL——提示只能寫「即將開放」，而規則頁還不存在，讀者面對的是一個沒有出路的付費牆。因此 PREMIUM 與規則頁隨階段 C 一起上線。階段 B 的 PARTIAL 只有「未登入／未確認訂閱」一種情況，提示明確可行：登入或確認訂閱即可閱讀。

**階段 C：點數消耗 + 邀請 + 規則頁**（功能區 3、4）
接上 `AccessDecisionService` 的扣點路徑（`article_access` + 條件式 UPDATE）、後台手動／批次加點、VIP 授予、邀請碼與 confirm 時發獎、`/r/me`、`/r/invite`、`/r/rules`。**規則頁與 PREMIUM 發布能力必須同一階段上線**——先有付費牆而後有規則說明，讀者體驗上是本末倒置。

> #### 階段 C 實際交付內容（端到端驗收後補記，2026-07-26）
>
> 驗收方式：`survey-backend/scripts/verify-stage-c.mjs`（可重跑、`--browser` 走真實 Chromium），
> 走一條完整的讀者路徑：A 訂閱 → 首次登入拿初始贈點 → 取邀請連結 → B 透過連結訂閱
> → B 未確認前 A 不得加點 → B 確認 → A 得獎勵 → 重複確認不重複發 → 發布 PREMIUM
> → B 未解鎖時回應本文不含受限區 → 解鎖扣點 → 重複解鎖不重複扣 → 後台改參數
> → 後台授予 VIP → VIP 免費看全文。連續執行兩次皆全綠。
>
> **已完成**：
>
> - 讀者可用點數解鎖 PREMIUM，且**必須按下按鈕才扣點**；`article_access` UNIQUE 實測擋住重複扣點。
> - 邀請獎勵只在被邀者點確認信後發放，重複確認不重複發，自我邀請不發獎。
> - 後台讀者管理（搜尋／VIP 授予與取消／批次加點／帳本查詢）與參數設定全部有 UI。
> - 群發保留交易信額度；行銷可用量為 0 時回 409 拒絕，而非靜默寄 0 封。
> - `credit_txn` 只增不改；端到端實測「`reader.credits` == 該讀者 `credit_txn` 總和」成立。
> - 邀請人的交易明細不顯示被邀者 email（連 local part 都不出現），冪等鍵仍保存在 `note`。
>
> **與 spec 原設計的偏離（逐項）**：
>
> 1. **§5.2「`credits >= cost` → FULL + 扣點」改為「PARTIAL + `CAN_UNLOCK`，讀者按按鈕才扣」。**
>    理由與取捨見 §5.2 第 5 點。
> 2. **§5.4「confirm 成功的同一交易內發獎」改為事件 + `REQUIRES_NEW` 的獨立交易**（V9 之後 `REQUIRES_NEW` 掛在 `ReferralService.rewardFor`，監聽器不帶 `@Transactional`）。
>    理由見 §5.4 的偏離說明（發布端根本沒有交易，用 `@TransactionalEventListener` 會靜默不觸發）。
> 3. **§6 的交易信保留額度由階段 D 提前至階段 C 完成**（第 2 項的「群發前檢查」部分；
>    「補寄前檢查」仍不成立，因為補寄要到階段 E 才存在）。
> 4. **§5.11 的「三處數字同步變動」只成立於 `/r/rules` 與 `/r/me`**；paywall gate 的數字來自
>    文章自己的 `credit_cost`，不隨全域參數變動。詳見 §5.11 的偏離說明——
>    「顯示的數字 == 實際扣的點數」這條核心性質仍成立且有端到端斷言。
> 5. **`/r/invite` 的邀請連結是 `/r/?ref={code}`，不是 §5.4 寫的 `/r/subscribe?ref=`。**
>    訂閱入口就是 `/r/`（見 §8 的頁面表），`/r/subscribe` 這個路徑從未存在。
>
> **已知風險與未完成項目**：
>
> - ~~`credit_txn` 缺 `(reason, note)` 的唯一索引，邀請獎勵的冪等是 check-then-act~~
>   **（已修：V9 的部分唯一索引 `uq_credit_txn_referral_note`，捕捉點移到交易邊界外；詳見 §5.4）**
> - `Reader` / `SurveyResponse` 部分路徑仍是整列 `save()`，可能靜默覆蓋條件式 UPDATE（詳見 §13.9；
>   VIP 授予已修，`ReaderAccountService.findOrCreate` 的 `setLastLoginAt` 與
>   `ReaderPortalController.updateProfile` 尚未修）。
> - ~~邀請獎勵被後台關成 0 時，`/r/invite` 的「已成功邀請人數」會停止成長~~
>   **（已修：人數改數 `reader.referred_by`，不再數帳本筆數；人數在被邀者**首次登入**時成長，
>   文案已把這個條件寫明；詳見 §5.4）**
> - ~~**沒有「只發布到網頁、不寄送」的路徑**~~ **（已補：`POST /api/admin/campaign/publish`，見下方）**
> - **VIP 閱讀 PREMIUM 時 `recordAccess` 會補寫 `cost=0` 的 `article_access`**（§5.2 規則 3 的既定設計），
>   因此 **VIP 到期後，該讀者對「VIP 期間讀過的文章」仍永久免費**。這是刻意的（不追溯收費），
>   但沒有寫進規則頁；若日後 VIP 改為付費訂閱，這條要先想清楚。
> - `/r/rules` 與 `/r/me` 的文案把單篇點數說成固定值，與「每篇文章可自訂價」的實作不一致（詳見 §5.11 待辦）。
> - **驗證腳本自身的兩個問題**（端到端驗收時發現，不影響產品邏輯，但會讓驗證失去效力）：
>   ① `verify-unlock-flow.mjs` 的 fixture 重設把 `reader.credits` 設成 300 後又清空該讀者的
>   `credit_txn`，於是測試資料庫裡永久存在一列 `credits=300 / 帳本總和=0`——
>   **全庫唯一違反「餘額 == 帳本總和」的列，是驗證腳本自己造的**。日後做 §13.2 的重算對帳工具時
>   這會是假陽性。修法是照 `verify-stage-c.mjs` 的做法連 `reader` 列一起刪、讓餘額由帳本重新累積，
>   並補一條不變式斷言。
>   ② `verify-admin.mjs` 用頂層 `import 'playwright'`，沒有其他腳本都有的全域安裝退路，
>   在本專案（`survey-backend` 無 `node_modules`）一律 `ERR_MODULE_NOT_FOUND`——
>   這支腳本目前跑不起來，等於一段驗證長期沒在執行。建議把 `loadPlaywright()` 抽成共用模組。

> #### 階段 C 補件：`POST /api/admin/campaign/publish`（只發布不寄送，2026-07-26）
>
> **它解掉的問題**：階段 C 交付了一整套點數機制（扣點解鎖、`CAN_UNLOCK` 的 paywall gate、
> 規則頁、我的帳戶、後台讀者管理），但 `CampaignService.send()` 對非 BASIC 的 tier 無條件回 400
> ——那個守門是正確的（階段 D 的信件折疊完成前，PREMIUM 一旦寄出就會把受限區送進**所有**
> 收件人的信箱）。副作用是 PREMIUM 文章連 API 都沒有建立路徑，只剩手動 `INSERT INTO campaign`，
> 於是整批點數功能在合併後**沒有任何操作人員能讓它跑起來**。這條端點就是解法：
> 建立並發布一篇文章到網頁、完全不寄信；因為不寄信，就沒有「信件端外流付費內容」的問題，
> 所以 PREMIUM 可以放行。
>
> **必須分清楚的兩件事（不美化）**：
>
> | 能力 | 狀態 |
> |---|---|
> | PREMIUM **發布到網頁**（`/r/news/{slug}`、`/r/archive`，含 paywall） | ✅ 已可用，有後台 UI |
> | PREMIUM **寄送給訂閱者** | ❌ 仍不可用，待階段 D 的信件折疊（`foldedHtml`） |
>
> `/api/admin/campaign/send` 對 PREMIUM 的 400 守門**維持原樣**，`reschedule` 的同款守門也維持原樣。
>
> **設計決定與理由**：
>
> - **`slug` 對這條端點是必填**（`send` 的 slug 是選填）。沒有 slug 的「純網頁文章」沒有
>   `/r/news/{slug}` 網址，讀者永遠打不開——寫進資料庫等於消失。缺 slug 回 400。
> - **不呼叫 `mailSender` 的任何方法，也不走 `applyMarketingQuota`**。不寄信就不該佔用
>   （更不該吃掉）交易信的保留額度，也不需要讓 `MailQuotaService` 的快取失效。
>   單元測試對 `sendBatch(List)`／`schedule`／`send(3 args)` **三個**方法都驗 `never()`
>   ——只驗其中一個會讓另外兩條路徑的回歸靜默通過（這個錯誤在階段 C 出現過）。
> - **新增 `mode='publish'` 與 `status='published'`**（`campaign.mode`／`status` 是自由文字，
>   沒有列舉約束，故不需要 migration）。刻意不重用既有值：沿用 `now` 會讓後台歷史列表
>   把這筆讀成「立即群發但只寄了 0 封」，而 `finalStatus` 對 `accepted=0` 會判成 `failed`
>   ——管理者會以為寄送出了問題而去重送。後台歷史列表的「成功/失敗」欄位對
>   `mode='publish'` 顯示「—（未寄送）」而非 `0/0`。
> - **`body_html` 留 `NULL`**。那是「信件版內文」，這條路徑沒有信件版；存一份全文 HTML
>   進去只會成為階段 D 實作折疊時的現成外洩來源。網頁端渲染讀的是 **`markdown`**，
>   再經 `ContentSplitter` 依 `<!--paywall-->` 切分（見 §5.3），與 `body_html` 無關。
> - **驗證與正規化重用 `send` 的同一份實作**（`validateTier`／`validateCreditCost`／
>   `validateSlug`／`resolvePublishedAt`），不複寫第二份。
> - **回應帶文章公開網址**（`${app.public-base-url}/r/news/{slug}`），讓管理者按完按鈕
>   能直接點開驗證 paywall。`publicBaseUrl` 沿用全專案唯一的設定來源。
> - **不碰點數與帳本**：只寫入 `campaign` 一列，核心不變式「餘額 == `credit_txn` 總和」不受影響。
>
> **驗收**：`survey-backend/scripts/verify-publish-endpoint.mjs`（可重跑、`--browser` 走真實
> Chromium 按後台那顆按鈕）。驗到：發布 PREMIUM → 未登入者的 HTTP 回應本文不含受限段落
> → 已登入且餘額足夠仍不含受限段落且看到解鎖按鈕 → 解鎖扣點、受限段落出現、`credit_txn`
> 有一筆 `READ` → 餘額 == 帳本總和 → 文章出現在 `/r/archive` → 後台歷史列表不呈現為失敗群發
> → 同一篇走 `send` 端點仍回 400。連續執行兩次皆全綠（含 `--browser`）。
>
> **仍未解的相關問題**：`campaign` 這張表同時是「發送批次」與「文章」，
> 這條端點沒有改變那個事實，只是為「純文章」的用途補上了一條誠實的入口。
> 未寄送的文章之後若要補寄（階段 E），仍需要一條「以既有 campaign 為內容來源寄送」的路徑——
> 目前沒有，而且它一樣要等階段 D 的折疊完成才能對 PREMIUM 開放。

**階段 D：內容製作升級**（功能區 7）
MinIO 服務與上傳、後台插圖 UI、發送依 tier 分組、`vip_full_in_mail`。（交易信保留額度原規劃在此階段，已提前至階段 C 完成，見 §6。）

**階段 E：寄送追蹤**（功能區 5、6）
補寄（差集 + 冪等）、開信像素與 HMAC、開信報表、名單匯入 UI。

**階段 F：參與度分級與 sunset**（功能區 8）
`EngagementService` 分級計算、發送級別選擇 UI、`filter_levels` 記錄與補寄重建、名單健康度報表、參數設定 UI。

**`last_engaged_at` 的漸進接線**：欄位在階段 B 的 V8 就建立，各個寫入點隨對應功能上線時一併接上（登入→階段 B、解鎖→階段 C、開信→階段 E、confirm 與改資料→各自所屬階段）。階段 F 只負責讀取與分級，不需回頭改前面的功能。這樣到階段 F 上線時已累積數個月的真實參與度資料，分級一開始就有效——若把寫入也留到階段 F，上線當天全部人的 `last_engaged_at` 都是 NULL，分級毫無意義。

## 12. 測試策略

沿用既有 `spring-boot-starter-test`，**不新增任何測試依賴**。

migration 相關測試（`MigrationSafetyTest`）需要真實的 PostgreSQL——H2 不支援本專案用到的 `jsonb` 與 `@>` 運算子。做法是**直接連本機專用測試容器**（`survey-test-db`，port 5433），每次重建一個乾淨的 `survey_migration_test` 資料庫。

> **為什麼不用 Testcontainers**：本機 Docker Desktop 29.6.1（API 1.55）與 `docker-java` 的 npipe 客戶端不相容，會誤報 `Could not find a valid Docker environment`，即使 `docker` CLI 與 named pipe 的手動 HTTP 請求都正常。已實測 testcontainers 1.21.0、2.0.5，以及明確指定 `DOCKER_HOST` 皆無效。唯一的已知修法是開啟 Docker Desktop 的 TCP daemon（需重啟 Docker Desktop，會影響本機其他專案的容器），不值得為此付出。
>
> 代價是測試有環境前提：`survey-test-db` 容器必須在執行中。測試在連不上時會以明確的中文訊息失敗並附上啟動指令，而不是靜默跳過——這道防線守的是「既有訂閱名單不可清除」（§4.0），寧可紅燈也不要假綠燈。

其餘測試（授權判斷、內容切分、token、session）一律不連資料庫，維持純單元測試。架構守衛 `PackageDependencyTest` 也不改用 ArchUnit——自製的 import 掃描已足夠，且已補上真空偵測。

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
| 參與度分級 | **新訂閱者（已寄 0 期、`last_engaged_at` 為 NULL）必須為 active**；已寄 6 期未互動為 dormant；已寄 12 期且逾 365 天為 sunset；互動後立即回 active |
| 級別過濾寄送 | 只勾 active 時 dormant/sunset 不在收件人內；勾 active+dormant 時 sunset 仍排除 |
| 補寄重建對象 | 依 `filter_levels` 重建，斷言與原次寄送的級別條件一致 |
| 參數即時生效 | 改 `app_setting` 後下次授權判斷即採用新值（含快取清除） |
| 規則頁與參數一致 | 改 `app_setting` 後，斷言 `/r/rules`、`/r/me`、PARTIAL 提示三處回應中的點數數字**全部**等於新設定值（防止任一處寫死） |
| **既有資料保全** | Testcontainers 起真實 PostgreSQL → 只套用 V1–V6 → 塞入代表性既有資料（已確認訂閱者／未確認匯入者／已退訂者）→ 套用 V7/V8 → 斷言 `survey_response` 筆數不變、`consent` / `unsubscribed` / `email` 逐列不變 |
| **backfill 正確性** | 同一容器，斷言已確認訂閱者的 `last_engaged_at` 非 NULL；未確認匯入者維持 NULL（刻意不回填）；`campaign` 既有列的 `tier` 皆取得 `BASIC` 預設值 |

### 12.1 測試容器前提與 Zeabur 建置的關係（已查核，無需處理）

`MigrationSafetyTest` 連不到 DB 就失敗，所以若 Zeabur 建置時執行 `mvn test`，建置容器內沒有 `survey-test-db`，V7/V8 的部署會被擋住。已查核三項證據確認**不會發生**：

| 證據 | 結果 |
|---|---|
| 使用者確認 | Zeabur 建置不跑 `mvn test` |
| `service.customBuildCommand` | `null` → 走 zbpack 自動偵測的 Java builder，其預設為 `mvn -DskipTests clean package` |
| 建置日誌 | 無 surefire 痕跡；Maven 步驟 27 秒 vs 本機含測試 38 秒 |

因此**不新增 `zbpack.json`**，也不設 `MIGRATION_TEST_DB_HOST`——沒有問題就不加設定（YAGNI）。

> **但這是建立在「預設行為」上的結論，不是建立在明示設定上的。** `customBuildCommand` 為 `null` 意味著我們依賴 zbpack 的預設值；若日後有人填了自訂建置指令、或 Zeabur 改了預設，這道環境前提就會擋住部署。
>
> 這個失敗方向是**安全的**（建置紅燈、擋下部署，不會動到資料），且錯誤訊息本身就寫明了修法（設 `MIGRATION_TEST_DB_HOST` 或改建置指令加 `-DskipTests`）。記錄在此的目的是讓下一個遇到它的人不必重新查一遍，而不是要現在就防它。

## 13. 未決事項與已接受風險

1. **開信率是下限**：受信箱封鎖遠端圖片影響，僅供趨勢參考，已明確不作為補寄依據。
2. **`reader.credits` 是物化欄位**：與 `credit_txn` 總和理論上恆等，但若出現不一致需要人工重算工具。第一版不做自動對帳排程，改為後台提供「重算此讀者餘額」按鈕。
3. **邀請無上限**：接受風險，理由見 5.4。若帳本出現異常集中的 REFERRAL，再加上限。
4. **MinIO bucket 公開讀取**：圖片 URL 無法撤回（信已寄出，收件人隨時可能重看舊信）。因此不得上傳敏感圖片，此為使用約定而非技術限制。
5. **VIP 到期後**：`tier` 保持 `VIP` 但 `vip_expires_at` 已過 → 授權判斷視為 FREE。不做自動降級排程，也不寄到期通知（第一版）。
6. **點數初版參數是估的**：300 點 / 10 點一篇在週更、半數 PREMIUM 的情境下約 14 個月才耗盡，稀缺感可能不足。已刻意接受並改為上線後依真實數據校準（見 §9.2 觀察指標），這是選擇 `app_setting` DB 設定表而非 yml 的直接原因。
7. **參與度分級依賴開信率下限**：開信是低可靠訊號（信箱封鎖圖片），因此門檻放寬且以登入／解鎖等高可靠行為為主。仍可能有真實讀者被判為 dormant——但因 dormant 只影響「常規信不寄」而非退訂，且任何互動立即恢復 active，損害可逆。刻意接受。
8. **`EngagementService` 的已寄期數需掃 `email_log`**：名單成長後此查詢會變重。第一版直接查（名單規模數百至數千，成本可忽略）；若日後變慢，對策是在 `survey_response` 加物化的 `campaigns_sent_count` 並於寄送時遞增，而非改變分級語意。
9. **`Reader`／`SurveyResponse` 部分路徑仍是整列 `save()`，會靜默覆蓋條件式 UPDATE 寫入的欄位**（階段 C 審查發現）：

> **機制**：`Reader` 沒有 `@Version` 也沒有 `@DynamicUpdate`，Hibernate 對它的 UPDATE 一律帶上所有可更新欄位（含 `credits`）；`SurveyResponse` 同理（含 `consent`、`unsubscribed`、`last_engaged_at`）。凡是「先 `findBy...` 讀出整個 entity、改一兩個欄位、再 `save()`」的路徑，寫回去的不只是被改的欄位，而是 SELECT 當下讀到的**整份快照**。若同一時間另一個請求用條件式 `@Modifying` UPDATE（`addCredits`／`deductCredits`／`touchEngagement`／`confirmByEmail`／`unsubscribeByEmail`）改了同一列的其他欄位，這個 save() 提交時會把那些欄位改動蓋回舊值——不會報錯，帳本／稽核資料與物化欄位從此對不上。
>
> **⚠️ 補充（比 `save()` 本身更容易漏掉的一半）**：`findBy...` 回傳的 entity 是**受管理的**，所以光是 `entity.setXxx(...)` 這一行，Hibernate 的 dirty check 就會在交易提交時自己補一道帶全欄位的 UPDATE，**完全不需要呼叫 `save()`**。也就是說「把 `save()` 刪掉」不等於修好——必須讓 entity 脫離管理（例如條件式 UPDATE 搭配 `clearAutomatically = true`）或全程不碰 setter。同理，**mock 掉 repository 的單元測試驗證不了這件事**，只能用真實資料庫觀測實際送出的 SQL。
>
> **具體失效情境**：SELECT 讀到 `credits=300`，提交前讀者在另一分頁解鎖一篇 10 點文章（`UnlockService` 用條件式 UPDATE 把 DB 改成 290 並寫入 `delta=-10` 的 `credit_txn`），整列 UPDATE 提交時把 `credits` 寫回 300——扣點被無聲還原，但帳本那筆 `-10` 留著，於是 `reader.credits (300) ≠ sum(credit_txn) (290)`，破壞「餘額永遠可由帳本重算稽核」的核心不變式，且無任何錯誤訊息，要等對帳才會發現。

**（A）不需要 migration，可立即修的部分——全部已修**

> 這一段與下面的 (B) 刻意分開列。舊版把兩者寫在同一條，結果「可立即修的那半」被 (B) 的 migration 禁令一起延後了——`grantVip` 與登入路徑的機制逐字相同、修法相同、都不需要 migration，卻只有前者被判為 Critical 立即修掉。分段是為了讓下次不會再發生同一件事。
>
> - **已修（`AdminReaderService.grantVip`／`revokeVip`）**：改用 `ReaderRepository.updateVip`（只寫 `tier`、`vip_expires_at` 兩欄的條件式 UPDATE），`credits` 永遠不在該 UPDATE 敘述裡。
> - **已修（`ReaderAccountService.findOrCreate` 登入分支）**：改用 `ReaderRepository.touchLastLogin(id, now)`（只寫 `last_login_at` 一欄）。這條**比 `grantVip` 更容易發生**：兩端都是讀者本人的即時操作（A 分頁點 magic link、B 分頁解鎖文章），不需要站方同時在後台操作。該查詢帶 `flushAutomatically = true, clearAutomatically = true`，後者同時負責把 entity 清出一級快取，使得之後為了回傳值而設的 `setLastLoginAt` 不會觸發 dirty-check UPDATE。守住這條的測試是 `ReaderLoginPersistenceTest`（真實 PostgreSQL + Hibernate `StatementInspector`，直接斷言送出的 `update reader` 敘述不含 `credits`，並以 `TransactionTemplate` 重現「交易已讀到舊值後才發生併發扣點」）與 `ReaderAccountServiceTest.loginUsesSingleColumnUpdateInsteadOfFullRowSave`。
> - **（同檔案 `createWithSignupGrant` 內的 `readerRepository.save(newReader)` 是新列的 INSERT，不是 read-modify-write，不在此列。）**

**（B）已知且刻意保留：`ReaderPortalController.updateProfile` → `ReaderProfileService.updateName` 對 `SurveyResponse` 的整列 `save()`**

> **狀態：明確接受，非遺漏。** 它 `findFirstByEmailIgnoreCaseOrderByCreatedAtDesc` 讀出整個 `SurveyResponse`、只改 `name`，就整列 `save()`。若同一 email 在這之間被 `confirmByEmail`（改 `consent`）、`unsubscribeByEmail`（改 `unsubscribed`）或 `touchEngagement`（改 `last_engaged_at`）動過，這次 `save()` 會把那些欄位的新值蓋回讀取當下的舊值。這是同一類缺陷，只是發生在 `SurveyResponse` 而非 `Reader`，且 `consent`／`unsubscribed` 是本專案最敏感的同意狀態，覆蓋風險不比 `credits` 輕。
>
> **為什麼還沒修**：`name` 是唯一由讀者自己寫入的欄位，`SurveyResponse` 的可更新欄位遠多於 `Reader`（未來還會增加），逐欄補條件式 UPDATE 只是把同一個競爭窗口切碎，不是根治；`SurveyResponse` 的根治解法是 `@Version` 樂觀鎖，**需要新增 `version` 欄位 ⇒ 需要一支 Flyway migration**，而階段 C 明文禁止新增 migration。因此這一條**確實**屬於 (B) 類，與 (A) 的延後理由不同。
>
> **下一個允許 migration 的階段必做**：原本計畫與 §5.4 的 `uq_credit_txn_referral_note` 一併排入。
> **現況（`fix/stage-c-followups`）：§5.4 的索引已隨 V9 完成，本條仍未做**——V9 的任務範圍
> 只有邀請獎勵的冪等，加 `version` 欄位是另一件會改動 entity 對應與呼叫端錯誤處理的事，
> 不併進同一支 migration。屆時若只想先降低風險而不加 migration，
> `SurveyResponseRepository.updateName(email, name)` 條件式 UPDATE 是可接受的過渡。
>
> 已用 `grep -rn "readerRepository\.save\|surveyResponseRepository\.save" survey-backend/src/main` 掃過全庫，以上為僅有的呼叫點（含前述已排除的 INSERT 一處），無遺漏。

**修法選項備忘**

> 1. **只寫必要欄位的 `@Modifying` 條件式 UPDATE**，與既有 `addCredits`／`deductCredits`／`updateVip`／`touchLastLogin` 一致。代價最低（不需 migration、不需新依賴），(A) 的兩處都採用此法。**注意必須搭配 `clearAutomatically = true`**，否則受管理的 entity 仍會被 dirty check 補一道全欄位 UPDATE。
> 2. **加 `@Version` 樂觀鎖**：從根本阻止靜默覆蓋，但**需要新增 `version INT` 欄位 ⇒ 需要 Flyway migration**，階段 C 不可執行。且樂觀鎖只把問題轉成「後到者拋例外」，呼叫端仍需處理重試或回錯誤，不是免費的午餐。
> 3. **加 `@DynamicUpdate`**：只縮小競爭視窗、非根治——若併發的另一方也改了同一欄位，它不提供任何保護。可作為選項 1 覆蓋不到的欄位的過渡防線，不應作為長期解法。
