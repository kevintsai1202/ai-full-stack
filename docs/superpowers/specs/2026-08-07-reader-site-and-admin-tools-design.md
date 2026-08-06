# 讀者站體驗強化與後台營運工具 設計文件

- **日期**：2026-08-07
- **專案**：hahow-ai-full-stack / survey-backend
- **狀態**：待審閱

## 1. 背景與問題

前一輪（`2026-08-06-admin-jwt-auth-and-article-editing-design.md`）完成後台的 JWT 認證與已發布文章編輯。本輪處理兩組需求：**讀者站的身分與內容呈現**，以及**後台的營運效率工具**。

兩組共 8 項，合為一份 spec、兩個章節。它們共用同一個部署與測試基礎設施，且其中數項（漏斗語意、投票卡說明）源自同一個根因——**畫面上並排的數字沒有說明彼此的關係**。

### 1.1 觸發本輪的實際問題

| 現象 | 根因 |
| --- | --- |
| 後台同一畫面兩張投票圖、數字不同（8 vs 14），無任何說明 | 上方是完整問卷提交數、下方是信中一鍵題點擊數，兩者是漏斗的兩端（轉換率 57.1%），但畫面沒說 |
| 「Reader 漏斗」的數值會出現後段大於前段 | 它根本不是一條漏斗——是六個獨立事件計數並排（文章瀏覽與首頁瀏覽是**平行入口**，訂閱與解鎖是**兩條不同路徑**） |
| 讀者登入後首頁仍顯示訂閱表單 | 首頁訂閱區塊不感知登入狀態 |
| 發優惠券只能整批發，無法針對單一填答者 | `coupon_campaign` 模型假設「一張券 = 一次批次寄送」 |

## 2. 決策記錄

| # | 決策 | 理由 |
| --- | --- | --- |
| D1 | 一份 spec、兩章節（讀者站／後台），不拆兩輪 | 兩組共用部署與測試基礎設施；後台組體積小，分兩輪要付兩次流程成本 |
| D2 | 讀者站身分區拆兩處：email 放首頁原訂閱位置，日夜切換與登出放右上工具列 | 讓 email **不進導覽列**，保住 `ReaderNav`「輸出永遠是固定字串、零使用者可控值」的安全不變量（見 §3.1） |
| D3 | 首頁問卷列表採「後台勾選曝光」，不自動列出全部 | 自動列出會把 `verify-*` 測試問卷與 `vote-*` 信中一鍵題一併曝光給讀者 |
| D4 | 文章側邊欄顯示「票數分布 + 參與人數」，不含轉換率 | 轉換率是經營指標，對讀者無意義 |
| D5 | 寄券按鈕採「彈視窗選券並確認」，不做下拉預選 | 每次明確看到收件人與券別再送出；寄錯券的代價（寄到讀者信箱、不可撤回）高於多點一次的成本 |
| D6 | 單筆寄券**不**把 `coupon_campaign.status` 翻成 `SENT` | `SENT` 語意是「批次發放已完成」；逐人發放是持續動作，第一次單筆就結案會與實際狀態不符 |
| D7 | 漏斗圖**先修資料語意再畫圖**：頂端為平行入口的加總 | 使用者指出「倒掛就是數據換算錯誤，上層應加總」——這是對的，形狀問題的根因在資料建模 |
| D8 | 讀者站暗色模式的所有修改**一律 scoped 於 `[data-theme="dark"]`**，亮色規則零改動 | `reader.css` 已有完整變數（`--fg`、`--muted` 等），但有 17 處硬編 `color:#…` 與 16 處硬編亮底；比照 admin.html 以暗色補丁規則覆蓋，亮色模式不動任何一行（見 §3.2） |

## 3. 章節 A：讀者站

### 3.1 安全前提：`ReaderNav` 的零可控值不變量

`ReaderNav` 的 javadoc 明訂：

> 本類的輸出**必須永遠是固定字串**：`HtmlTemplate#render` 的契約是替換值原樣插入 HTML、**不做跳脫**。**不得**把任何使用者可控值（email、顯示名稱、slug、query 參數……）拼進導覽列。

並有 `ReaderNavGuardTest` 機械化守著兩件事：① reader 套件生產程式碼中除 `ReaderNav` 外不得出現 `<a href="/r/archive"` 等三個逐字字串；② `templates/reader/*.html` 的 `<nav>` 區塊內只能有 `<!--NAV_LINKS-->` 佔位符。

**因此 D2 的切分不是美學選擇，而是安全選擇**：email 顯示在首頁區塊（經 `HtmlTemplate#escapeHtml`），右上工具列作為**獨立於 `<nav>` 的區塊**存在，兩者都不觸碰 `ReaderNav` 的輸出。`ReaderNavGuardTest` 維持原樣不放寬。

### 3.2 A1：日夜模式

**現況盤點（2026-08-07 重新實測，修正前版誤讀）**：`reader.css` 的 `:root`（第 2–13 行）已有**完整的設計 token**——`--fg`、`--muted`、`--muted-2`、`--surface`／`--surface-2`／`--surface-3`、`--border`／`--border-strong`、`--accent`／`--accent-deep`／`--accent-soft`、`--accent-2` 系列、`--ok`、`--err` 等共 22 個變數。前版 spec 稱「只有 9 個變數、無文字色變數」為誤讀，已更正。

實際的暗色模式障礙是：

1. **完全沒有 `[data-theme="dark"]` 區塊**——讀者站目前零暗色支援。
2. **17 處硬編 `color:#…`**（如第 95 行 `.subscriber-count` 的 `#0f766e`、第 198 行 placeholder 的 `#71817c`）。
3. **16 處硬編亮底**（如第 197 行 `input` 的 `background:#fff`、第 367 行 `.campaign-banner` 的 `#fff8df`）與 `.site-head` 的半透明亮色 `rgb(...)`。
4. **一處死引用**：第 372 行 `.account-jump` 引用未定義的 `var(--shadow-soft)`（應為 `--shadow-sm`），該 `box-shadow` 目前靜默失效——順手修復，屬本任務範圍。

**做法（比照 admin.html 第 21–61 行的已驗證模式）**：所有暗色修改**一律 scoped 於 `:root[data-theme="dark"]`**——一組變數覆寫 + 對硬編亮底元素的暗色補丁規則。亮色模式的規則**一行都不改**（唯一例外是上述第 4 點的死引用修復），因此不需要截圖比對，亮色迴歸風險趨近於零。

**行為**：偏好存 `localStorage`（key：`reader-theme`）；首次進站跟隨 `prefers-color-scheme`；每個讀者模板 `<head>` 內、樣式表載入前，放一段內聯啟動腳本設定 `documentElement` 的 `data-theme`，避免暗色偏好者進站閃白。

**驗收**：暗色模式下所有文字須達 WCAG AA（正常文字 4.5:1、大字 3:1），以可重跑腳本實際量測，不以目視判定。

### 3.3 A2：右上工具列

日夜切換鈕 + 登出鈕，作為獨立於 `<nav>` 的區塊。

- 登出：呼叫既有的讀者登出機制清除 `reader_session` cookie 後重載
- 未登入時只顯示日夜切換，不顯示登出

### 3.4 A3：首頁身分區

首頁 `index.html` 的「訂閱電子報」區塊（email 輸入框 + 訂閱按鈕）依登入狀態分流：

| 狀態 | 顯示 |
| --- | --- |
| 未登入 | 現況不變：email 輸入框 + 訂閱按鈕 |
| 已登入 | 換成「已訂閱：`<email>`」，email **必須經 `HtmlTemplate#escapeHtml`** |

### 3.5 A4：首頁問卷列表

首頁列出**後台勾選曝光**的問卷，連向既有的 `/r/survey/{formKey}` 讀者填答頁。

- 資料層：`form_definition` 新增 `homepage_visible BOOLEAN NOT NULL DEFAULT false` 與 `homepage_order INT`（**注意**：專案沒有 `form_schema` 表也沒有 JPA entity——表單存在 `form_definition` + `form_field`，由 `FormSchemaService` 以 JdbcTemplate 直接下 SQL；前版 spec 誤植表名，已更正）
- 曝光旗標語意：以 `form_key` 為單位——後台切換時對該 key 的**所有版本列**一致寫入，避免版本間旗標漂移；讀者端查詢取「已發布且勾選曝光」的每個 key 最新已發布版本的標題
- 預設 `false`：既有問卷（含 `verify-*` 測試問卷與 `vote-*` 信中一鍵題）不會突然曝光
- 後台：表單管理處提供勾選與排序
- 讀者端：無勾選任何問卷時整個區塊不顯示（不出現空標題）
- **排序規則**：依 `homepage_order` 升冪；`NULL` 排在最後，其內部再依建立時間新到舊。避免新勾選的問卷因忘了填順序而消失在清單中間

## 4. 章節 B：後台

### 4.1 B1：文章側邊欄投票統計

文章頁已有 `<aside class="article-side"><!--ARTICLE_SIDEBAR--></aside>` 佔位，由 `ReaderPageController#renderSidebar(campaign)` 填充。

**顯示內容**：該篇文章內嵌問卷的各選項票數與百分比，加上「共 N 人參與」。**不顯示**轉換率（D4）。

**條件**：文章內文含 `<!--survey:FORM_KEY-->` 標記時才顯示；無內嵌問卷的文章側邊欄維持原樣。

**多份問卷**：一篇文章若含多個 `<!--survey:-->` 標記，側邊欄依標記在內文出現的順序**全部列出**，各自標題為該問卷的題目。不做「只取第一份」的截斷——那會讓後面的問卷有票卻不見天日。

### 4.2 B2：原始資料逐列寄券

「完整原始資料」表格每列新增操作欄，含「寄券」按鈕與**已寄券別的顯示**。

**寄送流程**（D5）：點按鈕 → 彈視窗顯示收件人 email 與券別下拉 → 確認後送出。

**券別下拉的內容**：列出**未過期**（`expires_at` 為 null 或未來）的優惠券活動，依建立時間新到舊。已過期的券不列出——寄出一張當下就無法使用的券，對讀者是負面體驗且無法撤回。該收件人**已收過的券**在下拉中標示並停用，避免明知故犯（後端本就會擋，此處是把結果前移到操作當下）。

**寄送本身完全複用既有機制**（新增的只有：send 請求的一個選填欄位，與一個唯讀的已寄總覽端點 `GET /api/admin/coupons/sent-map`）：

```text
POST /api/admin/coupons/{id}/send
body: { emails: ["one@example.com"], limit: 1, single: true }
```

（`single: true` 為本輪新增的選填欄位，用來承載 D6 的狀態語意：單筆寄送不把 `status` 翻成 `SENT`、`sent_at` 記錄最後一次寄送時間；不帶此欄位的既有批次路徑行為完全不變。）

`CouponSendService` 已具備本需求所需的全部性質：

- **跨批次冪等**：已寄過的收件人會被過濾，同一人同一張券按兩次不會重複寄
- **批內去重**：同批同一 email 只保留第一次出現
- **子集驗證**：`CouponRecipientService#findIllegal` 防止夾帶未命中名單的 email
- **逐封 try-catch**：單封失敗不中斷整批，成功記 `email_log` 的 `sent`、失敗記 `failed` + 錯誤訊息

**已寄券的顯示**：資料已存在，**不需要新表**。`CouponSendService` 刻意以 `type = "coupon:" + campaignId` 在 `email_log` 表達關聯（`campaignId` 欄位留 null，因其語意是電子報 `Campaign`）。查詢該表即可得出每個 email 收過哪些券。

**狀態語意（D6）**：單筆寄送時 `coupon_campaign.status` 維持 `DRAFT`，`sent_count` 照實累加，`sent_at` 記錄最後一次寄送時間。

### 4.3 B3：行銷漏斗圖

**先修資料語意，再畫圖。**

現況（`AdminReferralGrowthController#dashboard`）：

```java
// 分享漏斗
clicks    = count(*) from referral_click
submitted = survey_response join reader on referral_code = answers->>'_ref'
confirmed = referral_conversion where confirmed_at is not null
approved  = referral_conversion where status = 'APPROVED'

// Reader 漏斗 —— 六個獨立事件計數
articleViews / subscriptionHomeViews / subscribeAttempts / subscribeSuccess / unlockClicks / unlockSuccess
```

**問題**：Reader 漏斗的六個數字之間**沒有包含關係**。`articleViews` 與 `subscriptionHomeViews` 是平行入口；`subscribeAttempts→subscribeSuccess` 與 `unlockClicks→unlockSuccess` 是兩條不同路徑。把它們並排成一條漏斗，倒掛是必然而非例外。

**修法**：

1. **Reader 漏斗**拆成語意正確的結構——頂端為 `articleViews + subscriptionHomeViews` 的**總瀏覽**，其下分為「訂閱路徑」與「解鎖路徑」兩條各自遞減的鏈。
2. **分享漏斗**維持四層，但 `submitted` 只計帶 `_ref` 的填表，理論上為 `clicks` 子集；若仍出現倒掛（連結被轉寄、點擊未被記錄），以 §4.3.1 處理。

#### 4.3.1 倒掛的處理

資料語意修正後，倒掛應不再發生。但為避免圖形在資料異常時誤導：**寬度嚴格遞減**（後段寬度不得超過前段），並在該層標示實際數值與一個提示，說明此層數值高於上一層。形狀符合「上大下小」，異常不被隱藏。

#### 4.3.2 圖形

各層寬度依實際數值比例，由上而下遞減；每層標示階段名稱與數值；相鄰層之間標示轉換率。

### 4.4 B4：投票卡與表單分析的關係說明

同一畫面的兩組數字需說明彼此關係：

- 投票卡標題下方註明：**「信中一鍵題點擊數，不受上方篩選條件影響」**（現況：投票卡只依 `formKey`，忽略版本／日期／來源／期別，而上方圖表全部吃這些篩選）
- 明確標示兩者為漏斗兩端，並顯示轉換率（點擊 → 完整提交）

## 5. 資料層變更

**8 項需求中只有 1 項需要 migration。**

```sql
-- Flyway V25（目標表為 form_definition；專案沒有 form_schema 表）
ALTER TABLE form_definition ADD COLUMN homepage_visible BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE form_definition ADD COLUMN homepage_order INT;
```

| 需求 | 資料層 |
| --- | --- |
| A4 首頁問卷列表 | **V25 migration**（上方） |
| B2 寄券與已寄清單 | 不需要——`email_log` 的 `type='coupon:{id}'` 已記錄 |
| B3 漏斗圖 | 不需要——只改查詢語意，事件資料本身正確 |
| A1／A2／A3／B1／B4 | 不需要——純呈現層 |

## 6. 測試策略

| 層級 | 重點 |
| --- | --- |
| 資料層 | `homepage_visible` 預設 false，確認既有問卷不被曝光 |
| 漏斗語意 | 頂端加總後，各層確為下層的超集；倒掛時寬度仍遞減且提示出現 |
| 寄券冪等 | 同一人同一張券連按兩次，只寄一封；`email_log` 只有一列 `sent` |
| 寄券狀態 | 單筆寄送後 `coupon_campaign.status` 仍為 `DRAFT`（D6） |
| 暗色模式 | 以可重跑腳本實測 WCAG 對比（斷言比值，非硬編數字），涵蓋文字、輸入框、表頭、卡片 |
| 亮色不回歸 | 所有暗色修改 scoped 於 `[data-theme="dark"]`，亮色規則零改動（結構性保證，不需截圖比對）；驗證腳本同時斷言亮色模式對比達標 |
| 導覽列安全 | `ReaderNavGuardTest` 維持原樣通過，證明 email 未進入導覽列 |
| 迴歸 | 既有 9 支 `verify-*.mjs` 全數通過 |

## 7. 明確不納入本輪範圍

- 讀者端的問卷作答歷史頁（本輪只在首頁列出可填問卷）
- 優惠券的批次「依條件自動發放」（既有機制不動）
- 分享漏斗的點擊歸因改造（只修 Reader 漏斗的加總語意）
- 讀者站的多語系

## 8. 風險與緩解

| 風險 | 緩解 |
| --- | --- |
| A1 改壞亮色模式 | 所有暗色修改 scoped 於 `[data-theme="dark"]`，亮色規則零改動（唯一例外：修復 `--shadow-soft` 死引用） |
| email 進入導覽列造成 XSS | D2 讓 email 只出現在首頁區塊並經 `escapeHtml`；`ReaderNavGuardTest` 不放寬 |
| 單筆寄券把整張券標記為已結案 | D6 明定 `status` 維持 `DRAFT`，並以測試鎖住 |
| 漏斗改造動到既有 KPI 數字 | 保留原始事件計數不變，只改「如何組成漏斗」；新舊數值並存驗證 |
| 首頁問卷曝光測試用問卷 | `homepage_visible` 預設 false，需明確勾選 |
