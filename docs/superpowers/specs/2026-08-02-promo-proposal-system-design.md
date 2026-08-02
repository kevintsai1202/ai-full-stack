# 工商時間提案系統設計

日期：2026-08-02
狀態：已與開發者確認需求與方案，待實作計畫

## 1. 背景與目標

電子報已有 `<!--promo-->` 工商區塊的渲染慣例（`MarkdownRenderer.wrapPromoBlocks()`，信件／後台預覽／讀者頁三路徑共用），但工商內容目前由管理員手寫、無申請流程、無成效歸因。本輪建立完整管線：

1. 讀者端（登入後）提交工商提案，並以點數支付投放費用。
2. 管理員審核提案，核准後可在電子報編輯器選用、插入既有工商區塊。
3. 寄送／發布時保存「電子報 × 工商提案」的明確關聯，不靠 Markdown 內容猜測歸因。
4. 工商連結經安全轉址記錄點擊，後台呈現發送數、總點擊、唯一點擊、CTR。

## 2. 需求決策記錄

| 決策點 | 定案 |
| --- | --- |
| 點擊統計範圍 | 信件＋讀者網頁版都統計；唯一點擊歸戶＝信件收件人（HMAC token）或登入 reader；未登入網頁訪客只進匿名總點擊 |
| 發送數定義 | 沿用 `accepted_count`（寄信商已接受數），不冒充送達數 |
| 提案重用 | 可跨多期，申請時自選投放次數 1–3 為配額，每次 COMMIT 扣一次 |
| 提案歸戶 | 綁定提交者 `reader_id`，本輪就做讀者端「我的提案」列表 |
| 扣點 | 送出申請當下扣「單價 × 投放次數」，餘額不足擋下申請；單價走 AppSetting（後備 100 點） |
| 退點 | 提案進入終態時退「(投放次數 − 已投放) × 單價」：被拒絕全額退、封存退未用餘額 |
| 訂閱數比例計價 | 本輪不做（無金流時點數來源上限低、結算時點矛盾、訂閱數≠發送數）；以後台調整單價替代，未來串金流後用 `pricing_type` 擴充 CPM／級距制 |
| 收費 | 本輪不串金流；`pricing_type` 預設 `FREE`、`payment_status` 預留 |
| 測試信／預覽 | 不納入統計（由版位 DRAFT／COMMITTED 狀態自然達成，見 §6） |
| 掃描器誤差 | 郵件安全掃描器可能污染點擊，統計介面加註說明 |

## 3. 資料模型（Flyway V19）

### 3.1 `promo_proposal` 工商提案

| 欄位 | 型別 | 說明 |
| --- | --- | --- |
| id | BIGSERIAL PK | |
| reader_id | BIGINT NOT NULL → reader | 提交者 |
| contact_name | VARCHAR(100) NOT NULL | 聯絡人 |
| contact_email | VARCHAR(255) NOT NULL | |
| title | VARCHAR(150) NOT NULL | 提案名稱 |
| body_text | TEXT NOT NULL | 純文字文案（禁 HTML／圖片／Script，見 §7.2） |
| link_text | VARCHAR(100) NOT NULL | 連結文字 |
| link_url | VARCHAR(1000) NOT NULL | 僅接受 `https://` |
| status | VARCHAR(20) NOT NULL DEFAULT 'PENDING' | PENDING／APPROVED／REJECTED／ARCHIVED |
| review_note | TEXT NULL | 拒絕理由等 |
| reviewed_at | TIMESTAMPTZ NULL | |
| placement_quota | INT NOT NULL | 申請時自選 1–3 |
| placement_used | INT NOT NULL DEFAULT 0 | 已投放（COMMIT）次數 |
| unit_cost | INT NOT NULL | 申請當下的單價快照（點），退點以此計算，不受後台後續調價影響 |
| pricing_type | VARCHAR(20) NOT NULL DEFAULT 'FREE' | 收費預留 |
| payment_status | VARCHAR(20) NULL | 收費預留 |
| created_at / updated_at | TIMESTAMPTZ | |

### 3.2 `promo_placement` 版位（電子報 × 提案）

| 欄位 | 型別 | 說明 |
| --- | --- | --- |
| id | BIGSERIAL PK | 即轉址網址中的 placementId |
| campaign_id | BIGINT NOT NULL → campaign | |
| proposal_id | BIGINT NOT NULL → promo_proposal | |
| status | VARCHAR(20) NOT NULL DEFAULT 'DRAFT' | DRAFT／COMMITTED／REMOVED |
| committed_at | TIMESTAMPTZ NULL | |
| created_at | TIMESTAMPTZ | |

約束：`UNIQUE(campaign_id, proposal_id)`——同一期同提案最多一個版位。

### 3.3 `promo_click` 點擊原始紀錄

| 欄位 | 型別 | 說明 |
| --- | --- | --- |
| id | BIGSERIAL PK | |
| placement_id | BIGINT NOT NULL → promo_placement | |
| channel | VARCHAR(10) NOT NULL | EMAIL／WEB |
| identity_type | VARCHAR(10) NOT NULL | RECIPIENT／READER／ANON |
| identity_key | VARCHAR(255) NULL | 正規化 email（RECIPIENT）或 reader_id（READER）；ANON 為 NULL |
| clicked_at | TIMESTAMPTZ | |

只存原始列（append-only），彙總於查詢時計算，不維護計數器。索引：`(placement_id)`。

### 3.4 既有表擴充

- `credit_txn`：新增 nullable 欄位 `promo_proposal_id`；新增 reason 常數 `PROMO_APPLY`（負）、`PROMO_REFUND`（正）。
- AppSetting：新 key `CREDIT_PROMO_PLACEMENT_COST`，由 `CreditPolicy.promoPlacementCost()` 讀取，後備值 100、夾 ≥ 0（0 為合法「免費投放」營運設定）。

## 4. 狀態機

### 4.1 提案

```text
PENDING ──approve──> APPROVED ──archive──> ARCHIVED（終態）
   └──────reject───> REJECTED ──archive──> ARCHIVED
```

- PENDING → APPROVED｜REJECTED；APPROVED／REJECTED → ARCHIVED。無其他轉移。
- REJECTED 時退全額 `placement_quota × unit_cost`（此時 `placement_used` 必為 0）。
- ARCHIVED 時退 `(placement_quota − placement_used) × unit_cost`；REJECTED→ARCHIVED 已退過、不重複退（以「是否已有 PROMO_REFUND 交易」冪等判斷）。
- 封存後退出編輯器選單；既有 COMMITTED 版位與統計不受影響。

### 4.2 版位

```text
DRAFT ──發布/寄送對帳（內文有此版位連結）──> COMMITTED（扣配額 placement_used+1）
  └────發布/寄送對帳（內文已無此連結）────> REMOVED（不扣）
```

## 5. 轉址端點與歸戶

`GET /promo/c/{placementId}?rt=<token>`：

1. 依 placementId 從 DB 查出 `link_url`，302 轉址。**目的地不進 URL 參數，無 open redirect 面**。版位不存在回 404。
2. 歸戶順序：`rt` 驗簽通過 → EMAIL／RECIPIENT（identity＝正規化 email）；無 rt 或驗簽失敗但有讀者 session → WEB／READER；否則 WEB／ANON。
3. **僅 COMMITTED 版位寫入 `promo_click`**；DRAFT／REMOVED 照樣轉址但不記錄。
4. token＝HMAC-SHA256，沿用 `app.unsubscribe-secret` 同一把 secret，簽名內容加 `"promo|"` 前綴做 domain separation（與退訂 token 不可互換），實作模式複製 `UnsubscribeTokenService`（常數時間比對、Base64 URL-safe 無 padding）。

## 6. 插入與寄送管線

1. 編輯器「工商提案」選單：`GET /api/admin/promo/proposals?status=APPROVED&available=true`（配額未滿者）。
2. 選定後 `POST /api/admin/campaigns/{cid}/promo-placements {proposalId}` 建 DRAFT 版位，回傳 markdown snippet，前端插入游標處：

   ```markdown
   <!--promo-->
   （body_text 經 Markdown escape 的快照）

   [連結文字](/promo/c/{placementId}?rt=__PROMO_RT__)
   <!--/promo-->
   ```

   文案快照落地：所見即所得、寄出內容＝審核當下內容；提案事後修改不影響已插入的電子報。
3. 寄送時在既有 `renderFor()` 每收件人替換點多一個替換：`__PROMO_RT__` → 該收件人的簽章。後台預覽與讀者網頁版不替換，佔位符驗簽失敗自然落到 session／匿名歸戶。
4. **發布／寄送時對帳**（關聯定案的唯一時點）：掃描內文實際出現的 `/promo/c/{id}`（解析自己生成的確定性 URL），出現的 DRAFT → COMMITTED＋扣配額；配額不足（`placement_used ≥ placement_quota`）**擋下寄送**並提示；未出現的 DRAFT → REMOVED。管理員刪除區塊無需額外操作。
5. `MarkdownRenderer`／`MailBodyRenderer` 零改動。對帳時附帶檢查 promo 標記於 paywall 兩側各自成對，不成對僅警告（沿用既有「單邊標記降級為無害註解」行為）。

## 7. 讀者端

### 7.1 頁面與導覽

- `GET /r/promo`：上半申請表單、下半「我的提案」列表（名稱、狀態、投放次數 已用/總數、送出時間、退點紀錄）。未登入導向登入頁。
- 申請頁顯示：目前單價（`CreditPolicy.promoPlacementCost()`）、總費用試算、目前訂閱規模（透明但不與價格掛鉤）。
- `ReaderNav` 新增常數「工商合作」，僅出現在登入分支；符合 `ReaderNavGuardTest` 守衛（新頁面導覽一律呼叫 `ReaderNav.links()`）。

### 7.2 申請驗證（`POST /r/promo/apply`）

- 欄位長度依 §3.1；`link_url` 僅 `https://`；`body_text`／`title`／`link_text` 拒絕含 `<` 字元（禁 HTML／Script），圖片與附件無入口；並拒絕含字面 `__PROMO_RT__`（避免寄送時被每收件人替換機制誤代入）。
- 投放次數限 1–3。
- 扣點：同一交易內檢查餘額 ≥ `單價 × 次數`，寫入 `PROMO_APPLY` 負向交易並更新 `reader.credits`；不足回明確錯誤。
- 防濫用：每位 reader 同時最多 3 件 PENDING 提案。

## 8. 管理後台（admin.html）

- 新「工商提案」分頁：四狀態清單、核准／拒絕（填理由）／封存操作。
- 統計區兩維度：按提案（跨期加總）、按電子報（各版位明細）。欄位：發送數（accepted_count）、信件總點擊、信件唯一點擊、**信件 CTR＝信件唯一點擊 ÷ accepted_count**；網頁點擊獨立兩欄（READER 唯一、含匿名總數）不進 CTR。加註掃描器誤差說明。
- 新增 API：提案列表／審核動作／統計查詢，以及 §6 的版位建立端點，皆掛既有 admin 驗證。

## 9. 測試計畫

- 單元：token 簽驗與 domain separation（promo token ≠ 退訂 token）、提案狀態機（非法轉移拒絕）、退點計算與冪等（REJECTED→ARCHIVED 不重複退）、配額 enforce（第 quota+1 次 COMMIT 擋寄送）、對帳（出現才 COMMIT、消失轉 REMOVED）、點擊彙總（唯一／總數／ANON 排除於唯一）、Markdown escape、申請驗證（https、`<` 拒絕、餘額不足、PENDING 上限）。
- 整合：申請→扣點→審核→插入→寄送→點擊→統計全流程；測試信階段版位為 DRAFT 不入統計；`ReaderNavGuardTest` 照舊通過；`MarkdownRendererTest` 不受影響。
- 慣例：mvn 需 `JAVA_HOME=/d/java/jdk-21`。

## 10. 非目標（本輪不做）

- 金流、報價、付款確認、退款（僅預留欄位）。
- 訂閱數比例（CPM／級距）計價——以後台調整 `CREDIT_PROMO_PLACEMENT_COST` 人工替代。
- 提案人自助修改／撤回已送出的提案（需要時由管理員拒絕後重新申請）。
- 已投放電子報的文案回溯更新（快照即凍結）。
