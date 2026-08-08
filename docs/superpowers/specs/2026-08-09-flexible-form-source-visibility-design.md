# 彈性表單分析：來源可見度設計

**日期**：2026-08-09
**狀態**：已核准，待實作

## 問題

管理員在 admin「彈性表單分析」看不到從 `reader.springai.world/r/survey/{formKey}` 填答的資料，
懷疑資料沒進系統。實測後確認**資料有進去，但無法被看見**。

### 實測數據（生產環境，`fullstack-course-interest`，全版本）

`GET /api/admin/analytics/forms/fullstack-course-interest?allVersions=true` 回報 612 筆。
匯出原始紀錄後依 `source` 統計：

| source | 筆數 | `answers` 實際內容 |
|---|---|---|
| `exam` | 254 | `{}` 全空 |
| `dify` | 190 | `{}` 全空 |
| `newsletter` | 107 | 只有 `_ref` |
| `survey_form` | 60 | 8 個欄位，完整 |
| `newsletter_survey` | 1 | 9 個欄位，完整 |

### 兩個獨立缺陷

**缺陷 A：來源選單少了一半的來源。**
`admin.html` 的「來源」下拉選單（`#dynamic-source`）選項來自
`GET /api/admin/import/sources`，那是**匯入來源註冊表**（管理員手動登記，供匯入 CSV
與邀請信使用），只有 `survey_form`／`exam`／`dify` 三項。而 `audience_record.source_key`
是**實際發生的來源**，含程式自動寫入的 `newsletter`、`newsletter_survey`。
兩份清單各說各話，導致任何非人工登記的來源在畫面上不存在，無法篩選。

**缺陷 B：提交總數會誤導。**
612 筆之中只有 61 筆帶有實際問卷答案；其餘 551 筆是考試、Dify、電子報通道
為了掛人物身分而建立的 `record_type='survey_submission'` 空殼紀錄。
畫面把 612 當成「提交數」，管理員無從得知真實填答量。

`CouponRecipientService` 早已有 `REAL_SUBMISSION_SOURCES = List.of("survey_form",
"newsletter_survey")` 這個常數與說明註解，代表「只有這兩個來源是真實填答」的領域知識
在專案內已經存在，但只落在優惠券模組，彈性表單分析沒有套用。

**附帶觀察（本次不修）**：`summary.completionRate` 對本表單恆為 1.000。
`FormSchemaService.completionRate` 只檢查 `required` 欄位，本表單沒有任何必填欄位，
`allMatch` 對空集合恆為 true，於是 254 筆空紀錄也算「完成」。
修改演算法會影響所有表單，超出本次範圍，僅在畫面標註其語意。

## 設計

### 1. 新增來源分佈端點

`GET /api/admin/analytics/forms/{formKey}/sources`（Admin 金鑰保護）

參數與 `analytics` 同語意：`version`、`allVersions`、`from`、`to`、`campaignId`
（**不含 `source`**——這支端點的職責就是列出所有來源）。

回應：

```json
{
  "sources": [
    {"key": "exam", "label": "線上測驗", "total": 254, "answered": 0},
    {"key": "newsletter_survey", "label": "讀者接續填答", "total": 1, "answered": 1}
  ],
  "totals": {"total": 612, "answered": 61}
}
```

依 `total` 由大到小排序。

### 2. `answered` 的判定

「有實際答案」定義為：`raw_data->'answers'` 內**至少有一個 key 屬於該表單 schema 定義的欄位**。

不採用「`answers` 非空」，因為 `newsletter` 來源的紀錄帶有 `_ref`（推薦碼），
那是歸因用的中繼資料而非問卷答案，會讓 107 筆假性計入。
以 schema 欄位 key 比對是精確判定，也不依賴底線前綴這種命名慣例。

`allVersions=true` 時，欄位 key 取全部版本的聯集，與 `analytics` 合併各版本欄位的行為一致。

### 3. 來源標籤解析順序

1. 查 `AudienceSourceService.list()`（含管理員自訂來源）→ 取 `label`
2. 查內建對照表：`newsletter_survey` → 「讀者接續填答」、`newsletter` → 「電子報通道」
3. 都查不到 → **顯示原始 key**（不隱藏、不丟棄）

第 3 點是刻意的：未知來源必須看得見，否則就重演這次「資料在但看不到」的缺陷。

### 4. 前端調整（`admin.html`）

- `#dynamic-source` 選項改由新端點供給，選項文字為 `label`，值為 `key`。
  不再由 `loadAudienceSources()` 覆寫（其餘四個選單維持吃匯入註冊表，語意不變）。
- 分析區塊新增「來源分佈」表格：來源／筆數／有答案筆數。
- 摘要列並列顯示總提交與有答案筆數，例如
  `提交 612 筆（其中 61 筆有填答內容）· 不重複人物 601 人`。
  **並列而非取代**，避免改變既有數字語意。
- 完成率後標註「僅計必填欄位」。

## 不做

- 不改 `completionRate` 演算法
- 不動 reader 通道的推薦歸因（`_ref`）——使用者明確排除
- 不新增 migration
- 不顯示「測驗名稱」：`exam` 紀錄的 `answers` 為空、`schema_key` 與其他來源相同，
  紀錄裡沒有測驗名稱可撈；要做需從考試系統那側補寫 metadata，屬另一個議題

## 檔案結構

| 檔案 | 責任 |
|---|---|
| `form/FormSourceBreakdownService.java`（新增） | 來源分佈 SQL 聚合與標籤解析 |
| `form/FormSchemaController.java`（修改） | 新增 `/sources` 端點 |
| `static/admin.html`（修改） | 來源選單改吃實際來源、來源分佈表格、摘要並列 |
| `test/.../FormSourceBreakdownServiceTest.java`（新增） | 真實 PG 整合測試 |
| `scripts/verify-form-source-breakdown.mjs`（新增） | Playwright 前端驗證 |

`FormSourceBreakdownService` 依賴 `FormSchemaService`（取 schema 欄位與版本）而非反向，
因此 `FormSchemaService` 的建構子不變。`FormSchemaController` 建構子多一個參數，
Spring context 測試須一併更新。
