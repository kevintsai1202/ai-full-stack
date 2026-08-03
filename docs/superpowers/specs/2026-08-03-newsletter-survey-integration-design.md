# 電子報問卷整合系統設計

日期：2026-08-03
狀態：待審閱
前置：工商時間提案系統（2026-08-02）已上線——本設計大量沿用其 rt token、內容標記與點擊統計基礎設施。

## 1. 目標

1. **可自訂問卷**：站長可在 admin 後台建立全新問卷（不只改版既有問卷）、編輯欄位、發布。
2. **可整合進電子報**：內文插入問卷區塊；信件呈現「一鍵投票」（點選項即完成），落地到讀者網域接續填完整問卷。
3. **Dashboard 分析**：既有「彈性表單分析」增加電子報期別歸因與信中投票統計。
4. **填問卷拿點數**：完整填答發放點數（可歸戶者限定、每人每問卷一次）。

## 2. 決策記錄

| # | 決策 | 選擇 | 理由 |
| --- | --- | --- | --- |
| D1 | 信中互動模式 | 一鍵投票＋接續填答 | Email 客戶端不支援表單（硬限制）；一鍵投票參與門檻最低，接續頁補收完整資料 |
| D2 | 身分歸戶 | 盡量歸戶、不強制登入：信件走 rt token、網頁走 reader session | rt token 機制工商已驗證；轉換率優先 |
| D3 | 重複投票 | 具名一人一票、後投覆蓋（可改答案）；匿名投票只累計不去重 | 防灌票與允許改變心意並存 |
| D4 | 發點時機 | 完整填答才發點；每人每問卷一次；金額走 AppSetting | 抑制亂點騙點；資料品質與成本平衡 |
| D5 | 完整填答資格 | 需可歸戶（rt token 或 reader session）；純匿名只能一鍵投票 | 既有提交管線與訂閱耦合（見 §3 衝突分析）；rt 讓信件讀者免登入、還可預帶基本資料 |
| D6 | 歸因模型 | 輕量參數式（連結帶 campaignId，伺服器端以 schema 全驗證），不建 placement 表 | YAGNI：問卷無扣點配額對帳需求，工商級 placement 嚴謹度用不上 |
| D7 | 一鍵投票發點 | 不發 | 降低隨手亂點誘因（D4 的配套） |

## 3. 現況與衝突分析

### 3.1 可沿用的既有資產

- `form_definition`／`form_field`（V10）：版本機制（DRAFT→PUBLISHED→ARCHIVED）、欄位型別、options jsonb、analytics 旗標。
- `FormSchemaService`：schema 驅動的提交驗證與動態統計；admin.html「彈性表單分析」自動長圖表。
- `PromoRecipientTokenService`：`__PROMO_RT__` 佔位符在寄送路徑逐收件人替換（`CampaignDeliveryService`），問卷投票連結直接沿用。
- `credit_txn` 帳本＋`CreditPolicy`／`AppSetting`：發點與防重發（partial unique index）皆有前例（referral、promo refund）。
- 內容標記慣例：`<!--promo-->`／`<!--paywall-->` 帶外標記＋渲染端集中管樣式。

### 3.2 衝突：既有提交管線與「訂閱」深度耦合

`FormSchemaService.submit()` 強制要 email（`mergePerson`）、追加 consent 軌跡、寫 legacy `survey_response` 列——它是訂閱漏斗的一部分，不是中性問卷收集器。

**解法（本設計核心修訂）**：提交管線做**通道感知**。新增 NEWSLETTER 通道提交路徑：

- 身分由後端解析（rt token 或 reader session），**不信任前端傳入的 email**。
- 具名提交走既有 audience 管線（`upsertRecord`＋`replaceFacts`→ 圖表零改寫），**跳過** `appendConsent` 與 legacy `survey_response` 寫入（訂閱語意的副作用只留給訂閱漏斗）。
- 純匿名不能送完整問卷（D5），匿名參與管道是一鍵投票。

### 3.3 確認無衝突項

- 訂閱首頁舊端點 `POST /api/survey` 與靜態表單完全不動。
- `fullstack-course-interest` 問卷未設信中題，不會出現在「插入問卷」選單。
- 新路由 `/s/v/`、`/r/survey/` 無碰撞，但須加入 `ReaderEntryHostFilter` 放行清單（`/promo/c/` 有前例）。

### 3.4 既有問卷的定位：照常運作，且可選擇性升級

本設計是**加一個通道，不是換掉舊系統**。`fullstack-course-interest` 與其既有資料：

- **訂閱首頁照常收件**：靜態表單、`POST /api/survey`、audience 合併、consent 軌跡全部不變。
- **已收集資料與圖表不動**：「彈性表單分析」讀同一資料來源；新功能只是加 campaign 篩選與投票統計卡。
- **同一套 schema 基礎設施**：新問卷與舊問卷都是 `form_definition`，不是兩套系統。
- **舊問卷可選擇性嵌入電子報**：只要在 admin 為它指定一個信中題，它同樣能插入電子報——
  屆時同一份問卷有兩個入口：訂閱首頁（訂閱漏斗語意、含 consent）與電子報
  （讀者互動語意、跳過 consent），答案匯入同一份分析。是否啟用由站長決定，預設不啟用。

## 4. 資料模型（V21 migration）

```sql
-- 信中一鍵投票（含讀者頁快投）
CREATE TABLE survey_vote (
    id            BIGSERIAL PRIMARY KEY,
    form_key      VARCHAR(100) NOT NULL,
    field_key     VARCHAR(100) NOT NULL,
    option_value  TEXT NOT NULL,                    -- 存選項文字（提交時以 schema 驗證）
    campaign_id   BIGINT REFERENCES campaign(id),   -- 歸因；讀者頁直接投票可為 NULL
    channel       VARCHAR(10) NOT NULL,             -- EMAIL / WEB
    identity_type VARCHAR(10) NOT NULL,             -- RECIPIENT / READER / ANON
    identity_key  VARCHAR(255),                     -- email 或 readerId 字串；ANON 為 NULL
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_survey_vote_channel CHECK (channel IN ('EMAIL', 'WEB')),
    CONSTRAINT ck_survey_vote_identity CHECK (identity_type IN ('RECIPIENT', 'READER', 'ANON'))
);
-- 具名一人一票（跨期同問卷仍只一票；後投 upsert 覆蓋並更新歸因）
CREATE UNIQUE INDEX uq_survey_vote_identity
    ON survey_vote (form_key, identity_type, identity_key)
    WHERE identity_type <> 'ANON';
CREATE INDEX idx_survey_vote_form ON survey_vote (form_key, campaign_id);
```

既有表增量：

- `form_definition` 加 `email_vote_field_key VARCHAR(100)`（nullable）：指定信中一鍵題（必須是單選 select）。
- `audience_record` 的 raw jsonb 內新增 `campaignId`／`channel` 鍵（無 schema 變更）；record metadata 已有 formTitle／version 前例。
- `credit_txn` 加 nullable `survey_form_key VARCHAR(100)`＋partial unique index：

```sql
ALTER TABLE credit_txn ADD COLUMN survey_form_key VARCHAR(100);
CREATE UNIQUE INDEX uq_credit_txn_survey_reward
    ON credit_txn (reader_id, survey_form_key)
    WHERE reason = 'SURVEY_REWARD';
```

- `AppSetting` 新鍵 `CREDIT_SURVEY_REWARD`（後備 20 點），經 `CreditPolicy` 讀取。

## 5. 內容標記與渲染分流

- 標記語法：`<!--survey:FORM_KEY-->`，整行單獨存在（同 promo 慣例）；admin 編輯器工具列加「插入問卷」按鈕，選單只列**已發布且已設信中題**的問卷。
- `MarkdownRenderer` 保持通道無關（標記原樣輸出為 HTML 註解），展開在下游：

| 通道 | 展開結果 |
| --- | --- |
| 信件（`MailBodyRenderer` 之後、逐收件人替換之前） | email-safe 投票卡（單格 table＋inline style）：信中題標題＋每選項一顆按鈕連結 `{readerBase}/s/v/{formKey}?f={fieldKey}&o={optionIndex}&c={campaignId}&rt=__PROMO_RT__` |
| 讀者文章頁 | 互動問卷卡：信中題選項按鈕（點擊走同一投票端點、session 歸戶）＋「繼續填完整問卷」連結 |
| 後台預覽／測試信 | 投票卡視覺樣，連結標示「預覽不計票」；測試信照 §6 規則不落票 |

- **寄送前驗證**：標記指向不存在／未發布／未設信中題的問卷 → 擋下寄送並回明確錯誤（比照 InviteService 佔位符驗證），不做靜默降級。

## 6. 一鍵投票端點

`GET /s/v/{formKey}?f={fieldKey}&o={optionIndex}&c={campaignId}&rt={token}`

1. **Schema 驗證**：formKey 已發布、fieldKey 等於該問卷 `email_vote_field_key`、optionIndex 在選項範圍內；不合法一律 404（不洩漏 schema）。
2. **身分解析**（優先序同工商點擊）：rt 有效 → RECIPIENT(email)；reader session → READER(readerId)；皆無 → ANON。
3. **落票**：具名 upsert（撞 `uq_survey_vote_identity` 改 UPDATE，即改答案）；匿名 insert。`c` 指向未發送／不存在的 campaign → 照樣轉址但不落票（涵蓋測試信與預覽）。
4. **轉址**：302 → `/r/survey/{formKey}?voted={optionIndex}&c={campaignId}&rt={token}`（rt 原樣帶下去供接續頁歸戶）。落票失敗不擋轉址（best-effort，同工商哲學）。

## 7. 接續填答頁與發點

`GET /r/survey/{formKey}`（讀者網域，`templates/reader/survey.html`）

- 帶 `voted` 參數：頂部顯示「已收到你的投票」，信中題預選該選項。
- **身分呈現**：rt 或 session 解析成功 → 顯示「以 ○○○ 身分作答」並預帶 email／稱呼（audience／reader 資料），附「不是你？」連結導向登入頁（信件轉寄防呆）。
- **純匿名**：顯示題目唯讀預覽＋「登入作答可獲得 N 點」引導（magic link 門檻低）；不能送出。
- 表單依 `GET /api/forms/{formKey}` schema 動態渲染（比照訂閱首頁 schema 驅動做法）。

提交：`POST /api/forms/{formKey}/newsletter-submissions`（新端點，與訂閱漏斗端點分開）

- 請求帶答案＋`campaignId`＋rt（或依 session cookie）；後端解析身分，解析失敗回 401。
- 寫入：audience `upsertRecord`＋`replaceFacts`（source 標記 `newsletter_survey`），**不碰** consent 與 legacy 列。
- **發點（同一交易）**：身分反查 reader（RECIPIENT 以 email 查、READER 直接用）→ 是讀者且該問卷首次完整填答 → `credit_txn` 寫 `SURVEY_REWARD`＋加點；重送（改答案）不重發（unique index 兜底）；非註冊讀者照收答案、回應提示「訂閱成為讀者即可獲得點數」。
- 發點失敗整筆回滾（帳本不變式優先），讀者重送即可。

## 8. 自訂問卷（admin 補強）

1. `POST /api/admin/forms`（body：formKey、title）→ 建立 v1 DRAFT 空殼；formKey 格式 `[a-z0-9-]{3,50}`、不可與既有重複。admin.html 加「建立新問卷」按鈕。
2. 欄位編輯器加「信中一鍵題」下拉：只列該版本的單選 select 欄位；寫入 `email_vote_field_key`。
3. 既有欄位編輯／版本／發布 UI 沿用，本輪不重做。

## 9. Dashboard 分析

在既有「彈性表單分析」擴充，不另起爐灶：

- `/api/admin/analytics/forms/{formKey}` 加 `campaignId` 篩選參數（record raw 內的 campaignId）。
- 新統計卡「信中投票」：資料來源 `GET /api/admin/analytics/forms/{formKey}/votes`——各選項票數（沿用 `renderBars`）、具名／匿名分層、依 campaign 分組的票數與「投票→完整填答」轉換率。
- 期別下拉資料沿用既有 campaign 列表 API。

## 10. 錯誤處理原則

| 情境 | 行為 |
| --- | --- |
| 投票端點參數不合法 | 404（不洩漏 schema 存在性） |
| 落票 DB 失敗 | log.warn，照樣 302（讀者體驗優先） |
| 寄送時標記指向無效問卷 | 擋下寄送、回明確錯誤訊息 |
| 發點失敗 | 整筆提交回滾（不出現「收了答案沒給點」的中間態） |
| rt 過期／無效 | 視同匿名處理（可投票、不能完整填答），不報錯 |

## 11. 測試策略（全程 TDD）

- **單元**：投票 schema 驗證各分支；具名 upsert 改答案／匿名不去重；發點冪等（首次發、改答重送不發、非 reader 不發）；標記展開（信件卡含 `__PROMO_RT__` 與正確 URL、預覽卡不含真連結、未發布問卷擋寄送）；新問卷建立（formKey 格式／重複擋下）。
- **整合**：V21 跑真實 Flyway＋`ddl-auto=validate`；`uq_survey_vote_identity` 與 `uq_credit_txn_survey_reward` 併發兜底（DataIntegrityViolation 路徑）。
- **架構守衛**：`survey.html` 列入 ReaderNavGuardTest 的 `STATIC_NAV_TEMPLATES` 終點頁豁免（常由信件進入、非登入態，與 promo-contact.html 同理）；`ReaderEntryHostFilter` 放行 `/s/v/`、`/r/survey/` 的測試。
- **E2E 腳本**：`preview-survey-card.mjs`（信件投票卡＋接續頁雙視口截圖）；擴充 `verify-admin.mjs`（建立問卷→設信中題→插入標記→預覽斷言）。

## 12. 範圍外（本輪不做）

- 匿名完整填答的雙軌儲存與分析合併（未來需求出現再議）。
- 問卷投放次數配額／placement 對帳模型（D6）。
- 一鍵投票的即時結果頁（落地頁不顯示目前統計，避免從眾偏誤；未來可依 `public_analytics` 旗標開放）。
- 欄位編輯器 UI 重做、題型擴充（評分星等、矩陣題）。
- 多份問卷同一期電子報（技術上標記可插多個，本輪僅驗證單一區塊的體驗與統計）。
