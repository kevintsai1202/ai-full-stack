# 彈性受眾資料與 Exam 測驗匯入設計

**狀態**：開發完成並通過本機驗證，待正式環境部署
**日期**：2026-07-27
**範圍**：`survey-backend` 名單中心、Survey 資料模型、Exam 匯入、電子報排程操作狀態
**不包含**：本階段不直接修改正式資料庫、不啟用 Exam 內建寄信功能

## 1. 三句摘要

1. 將現在同時承擔「人物、訂閱、問卷答案」的 `survey_response` 拆成穩定的人物資料與可重複的活動紀錄。
2. 問卷與測驗都以「資料來源＋紀錄類型＋彈性 JSONB＋可搜尋 Fact」保存，新欄位不必再改主表。
3. Exam 匯入時人物依 Email 合併，但每次測驗、填寫資料與結果仍獨立保存，不會因 Email 已存在而整筆略過。

## 2. 現況與問題

### 2.1 已確認的現況

`survey-backend` 的 `survey_response` 現在同時包含：

- 人物：`email`、`name`
- 固定問卷欄位：`role`、`experience`、`frontend_experience`、`interest`、`budget`
- 彈性答案：`answers jsonb`
- 行銷狀態：`consent`、`unsubscribed`
- 歸因與來源：`utm jsonb`、`source`
- 互動：`last_engaged_at`

這使人物、問卷作答與同意狀態綁在同一列。同一 Email 重填問卷會產生多列，
而確認或退訂必須一次更新該 Email 的所有列。

現有 `/api/admin/import` 只接受 `email`、`name`、`source`，只要 Email 已存在就
整筆略過，因此無法再加入同一人的第二次測驗結果或新欄位。

### 2.2 Exam 正式資料結構盤點

2026-07-27 以唯讀方式確認 Exam 正式資料庫：

| 資料 | 數量 | 關係 |
|---|---:|---|
| `student_profile` | 259 | 一人一份主檔，Email 唯一 |
| `student` | 288 | 一人可有多次／多場測驗紀錄 |
| `exam` | 17 | 測驗主檔 |
| `answer` | 764 | 每次測驗的逐題答案 |
| Exam `survey_response` | 0 | Exam 自帶問卷尚未產生正式回覆 |

可匯入的資料包含：

- 人物：姓名、Email、建立時間、來源
- 測驗當時填寫資料：`student.survey_data json`
- 測驗結果：測驗、作答時間、總分、作答題數、答對數、正確率
- 逐題結果：題目、選項、是否答對

Exam 的 `first_consent_at` 與 `consent_version` 目前 259 筆皆為空，因此匯入
Exam 資料不可直接視為已同意接收行銷信。

## 3. 目標與非目標

### 目標

- Survey 可新增不同表單、版本與欄位，不必持續增加 Java Entity 固定欄位。
- 同一人可以擁有多份問卷與多次測驗紀錄。
- 匯入既有人物時仍可增加新的來源、屬性與活動紀錄。
- 每個值保留來源與發生時間，可知道資料來自哪份問卷或哪場測驗。
- Admin 可依來源、問卷答案、測驗分數、參加場次、訂閱狀態進行搜尋與分眾。
- 保留既有確認訂閱、退訂、電子報、讀者登入與點數機制。

### 非目標

- 不合併 Exam 與 `survey-backend` 的資料庫或程式。
- 不把 Exam 所有資料變成 `survey_response` 的新欄位。
- 不因參加測驗而自動取得電子報同意。
- 第一階段不製作完整拖拉式表單設計器。
- 不啟用 Exam 的 `email_campaign`、`email_recipient`、`email_template`。

## 4. 核心設計

### 4.1 資料分成四層

```text
人物 Audience Person
  ├─ 同意狀態 Consent：能不能寄信
  ├─ 外部身分 Identity：在 Exam、Survey 等系統中的 ID
  ├─ 活動紀錄 Record：填問卷、參加測驗、完成課程
  └─ 可搜尋資訊 Fact：職業、Java 經驗、分數、正確率、標籤
```

原則是：

- 人物去重。
- 活動不去重；同一人的每次活動都保留。
- 同一筆外部活動重複匯入時必須冪等，不可重複新增。
- 原始資料完整保存，常用欄位另外轉成可搜尋 Fact。

### 4.2 建議資料表

| 資料表 | 用途 | 重要欄位 |
|---|---|---|
| `audience_person` | 唯一人物主檔 | `id`、`email`、`email_normalized`、`display_name`、`created_at`、`updated_at` |
| `audience_consent` | 各管道同意狀態 | `person_id`、`channel`、`status`、`source_key`、`consent_version`、`occurred_at` |
| `audience_identity` | 對應外部系統 ID | `person_id`、`source_key`、`external_type`、`external_id` |
| `audience_record` | 可重複的活動紀錄 | `person_id`、`record_type`、`schema_key`、`external_record_id`、`occurred_at`、`raw_data jsonb`、`summary_data jsonb` |
| `audience_fact` | 跨來源的搜尋與分眾欄位 | `person_id`、`record_id`、`fact_key`、typed value、`source_key`、`observed_at` |
| `form_definition` | 問卷定義與版本 | `form_key`、`version`、`title`、`status` |
| `form_field` | 問卷欄位定義 | `form_definition_id`、`field_key`、`label`、`field_type`、`required`、`options jsonb` |
| `import_batch` | 每次匯入稽核 | `source_key`、`import_type`、`status`、`cursor`、成功／略過／錯誤數 |
| `import_item` | 單筆匯入結果 | `batch_id`、`external_record_id`、`status`、`error_code`、`payload_hash` |

### 4.3 為什麼不只用一個 JSONB

全部塞入 JSONB 雖然彈性高，但 Admin 要查詢「分數大於 7 且填過 Java 經驗」
會變得難以維護。因此採混合方式：

- `raw_data`：保留來源原貌，方便追溯與日後重新轉換。
- `summary_data`：提供詳情頁快速顯示。
- `audience_fact`：只抽取需要搜尋、統計或寄信分眾的欄位。
- `audience_person`：只放真正穩定的核心欄位。

新增一般問卷題目通常只需新增欄位定義與 Fact mapping，不需新增 migration。

## 5. Survey 彈性化

### 5.1 表單定義

每份 Survey 使用 `form_key + version` 識別，例如：

```json
{
  "formKey": "fullstack-course-interest",
  "version": 2,
  "fields": [
    {"key": "occupation", "type": "select", "required": true},
    {"key": "javaExperience", "type": "select", "required": false},
    {"key": "painPoints", "type": "multi_select", "required": false},
    {"key": "otherComment", "type": "long_text", "required": false}
  ]
}
```

送出後建立一筆：

```json
{
  "recordType": "survey_submission",
  "schemaKey": "fullstack-course-interest@2",
  "rawData": {
    "answers": {
      "occupation": "工程師",
      "javaExperience": "1-3 年",
      "painPoints": ["系統設計", "AI 整合"]
    },
    "utm": {"source": "newsletter"}
  }
}
```

### 5.2 相容性

- 現有 `POST /api/survey` 先保留。
- 內部轉接到新 `SurveySubmissionService`，避免前端一次全部重寫。
- 新版端點建議為 `POST /api/forms/{formKey}/submissions`。
- 舊的固定欄位轉成同名 Fact，既有 Admin 統計在過渡期仍可使用。

## 6. Exam 直接匯入

### 6.1 系統邊界

維持「跨系統走 API、不共用 DB」：

```text
Admin 按下同步
  → survey-backend 呼叫 Exam 匯出 API
  → 預覽新增人物／更新人物／新增測驗紀錄／錯誤
  → 管理員確認
  → 建立 import_batch 並冪等寫入
```

Exam 建議新增：

```http
GET /api/integrations/audience-export?since={cursor}
Authorization: Bearer {integration-token}
```

回傳：

```json
{
  "nextCursor": "2026-07-27T12:00:00Z|student:288",
  "profiles": [
    {
      "externalProfileId": "259",
      "email": "student@example.com",
      "name": "王小明",
      "createdAt": "2026-07-20T08:00:00Z",
      "acquisitionSource": "exam"
    }
  ],
  "attempts": [
    {
      "externalAttemptId": "288",
      "externalExamId": "16",
      "examTitle": "測驗名稱",
      "joinedAt": "2026-07-22T10:00:00Z",
      "totalScore": 7,
      "questionCount": 10,
      "answeredCount": 10,
      "correctCount": 7,
      "scoreRate": 0.7,
      "surveyData": {
        "occupation": "工程師",
        "javaExperience": "有"
      },
      "answers": [
        {
          "questionId": "101",
          "selectedOptionId": "404",
          "isCorrect": true,
          "answeredAt": "2026-07-22T10:05:00Z"
        }
      ]
    }
  ]
}
```

### 6.2 Exam mapping

| Exam 資料 | 新模型 |
|---|---|
| `student_profile.id` | `audience_identity(exam, student_profile, id)` |
| `student_profile.email/name` | `audience_person` 合併 |
| `student.id` | `audience_record.external_record_id` |
| `student.survey_data` | `raw_data.surveyData`，需要分眾的鍵轉成 Fact |
| `student.total_score` | Fact：`exam.score` |
| 測驗題數／答對數 | Fact：`exam.question_count`、`exam.correct_count` |
| 正確率 | Fact：`exam.score_rate` |
| `answer[]` | `raw_data.answers`，詳情頁可展開 |
| `exam.id/title` | `summary_data.exam` 與 Fact：`exam.id` |

### 6.3 去重與更新規則

1. Email 以 `lower(trim(email))` 對應人物。
2. Email 已存在時只合併人物，不略過測驗資料。
3. Exam 人物身分以 `(source_key, external_type, external_id)` 唯一。
4. 測驗紀錄以 `(source_key, record_type, external_record_id)` 唯一。
5. 相同 payload hash 重送視為 `unchanged`。
6. 同一 external ID 內容改變則更新該紀錄與 Fact，並保留 import audit。
7. 無 Email 的資料列進錯誤清單，不建立匿名行銷人物。
8. Exam 沒有可驗證的同意時間與版本時，訂閱狀態維持原狀或 `pending`。

## 7. 通用匯入能力

Admin 名單匯入改成四步驟：

1. 選擇來源：Exam、Dify、活動、合作名單或自訂來源。
2. 選擇方式：直接同步、XLSX／CSV、JSON API。
3. 欄位對應：Email、姓名、人物屬性、活動欄位、同意證據。
4. 預覽並確認：新增人物、合併人物、新增紀錄、未變更、錯誤。

可保存 `import_profile`，例如「每月講座名單」下次直接沿用欄位 mapping。

匯入結果不再只有 `imported/skipped`，改為：

```json
{
  "peopleCreated": 20,
  "peopleMerged": 35,
  "recordsCreated": 48,
  "recordsUpdated": 2,
  "unchanged": 5,
  "invalid": 1
}
```

## 8. Admin 顯示方式

### 人物列表

- 姓名、Email、訂閱狀態
- 來源徽章：Survey、Exam、Dify、自訂來源
- 最近活動
- 問卷數、測驗數、最佳／最近分數
- 動態欄位可由管理員勾選成列表欄位

### 人物詳情

```text
基本資料
同意紀錄
來源與外部身分
活動時間軸
  ├─ 填寫「課程興趣問卷 v2」
  ├─ 參加「Exam 16」，7 / 10
  └─ 登入電子報讀者站
```

### 分眾條件範例

- 參加過 Exam 16 且分數小於 6。
- Java 經驗為「無」且對 AI Coding 有興趣。
- 填過課程問卷但尚未確認訂閱。
- 最近 30 天有活動且未退訂。

### 8.1 Survey 統計動態化

目前 Admin 問卷分析與 PDF 報表把 `role`、`experience`、
`frontendExperience`、`budget`、`interest`、`status`、`goals`、
`pain_points` 等欄位直接寫在 HTML/JavaScript 中。即使資料庫可以收
`answers jsonb`，新增題目仍不會自動出現在圖表、原始資料欄位、篩選或 PDF。

新版由 `form_definition` 與 `form_field` 決定統計呈現：

| 欄位類型 | 預設呈現 | 可用統計 |
|---|---|---|
| `select`、`boolean` | 選項分布長條圖 | 數量、比例、未填寫 |
| `multi_select` | 多選分布長條圖 | 選取次數、作答人數、比例 |
| `rating`、`number` | 分布圖＋摘要卡 | 平均、中位數、最小／最大 |
| `short_text`、`long_text` | 可搜尋文字清單 | 有效回覆數、未填寫數 |
| `date` | 時間分布 | 日／週／月數量 |
| `email`、敏感欄位 | 不產生圖表 | 只在授權原始資料中顯示 |

`form_field` 增加顯示設定：

```json
{
  "fieldKey": "javaExperience",
  "label": "Java 開發經驗",
  "fieldType": "select",
  "analyticsEnabled": true,
  "analyticsView": "bar",
  "filterable": true,
  "sensitive": false,
  "displayOrder": 20
}
```

Admin 統計 API 建議為：

```http
GET /api/admin/analytics/forms/{formKey}?version=2&from=...&to=...
X-Admin-Key: ...
```

回傳以欄位描述為核心，而不是固定 DTO：

```json
{
  "form": {
    "key": "fullstack-course-interest",
    "title": "課程興趣問卷",
    "version": 2
  },
  "summary": {
    "submissions": 120,
    "uniquePeople": 112,
    "completionRate": 0.86
  },
  "dimensions": [
    {
      "fieldKey": "javaExperience",
      "label": "Java 開發經驗",
      "fieldType": "select",
      "view": "bar",
      "answered": 108,
      "missing": 12,
      "values": [
        {"value": "none", "label": "沒有經驗", "count": 60, "percent": 0.556}
      ]
    }
  ]
}
```

Admin 畫面依 `dimensions[]` 自動建立圖表、篩選器與原始資料欄位。PDF／CSV／JSON
匯出也使用同一份欄位描述，不再各自維護固定欄位清單。

統計頁需要：

- 可選擇 Survey 與版本；預設顯示目前發布版本。
- 可切換「只看此版本」或「合併相同 fieldKey 的所有版本」。
- 可依日期、來源、完成狀態篩選。
- 清楚顯示有效作答與未填寫，避免把未填寫誤當成選項。
- 多選題比例分母使用「作答人數」，同時顯示選取次數。
- 文字題提供搜尋、展開與匯出，不直接製作可能誤導的文字雲。
- 公開 `/api/survey/stats` 與 Admin analytics 分離；公開統計只能使用明確允許欄位，
  不可因動態 schema 意外公開敏感題目。

若未來 Exam 或課程活動也定義 `record schema`，相同 renderer 可顯示：

- 測驗人次、完成率、平均分數、分數分布。
- 不同測驗或不同梯次比較。
- Survey 填寫資料與測驗結果的交叉篩選。

### 8.2 讀者篩選與批次贈送

目前 `GET /api/admin/readers?q=...` 只能依 Email 片段搜尋，且空關鍵字會被拒絕；
點數端點雖可接受 Email 陣列，但重複 Email 會重複加點，VIP 端點則一次只能處理一人。

新版讀者管理使用伺服器端篩選與分頁：

| 分類 | 篩選條件 |
|---|---|
| 基本資料 | Email／姓名關鍵字、來源、標籤 |
| 訂閱 | 已確認、待確認、已退訂 |
| Reader 帳戶 | 已建立帳戶、未登入、最後登入區間 |
| VIP | 一般、有效 VIP、即將到期、已過期、到期日區間 |
| 點數 | 點數最小／最大值 |
| Survey | 填過指定表單、版本、指定答案 |
| Exam | 參加指定測驗、分數／正確率區間、最近測驗時間 |
| 活動 | 最近互動時間、活動類型 |

查詢 API：

```http
POST /api/admin/audience/search
X-Admin-Key: ...
Content-Type: application/json

{
  "filters": {
    "consentStatus": ["CONFIRMED"],
    "sourceKeys": ["exam"],
    "vipStatus": ["NONE", "EXPIRED"],
    "exam": {"examId": "16", "scoreRateMax": 0.6}
  },
  "sort": {"field": "lastActivityAt", "direction": "DESC"},
  "page": 0,
  "size": 50
}
```

回傳 `items`、`total`、可用動態欄位與 facet 計數。不得將整張人物表一次傳到前端再篩選。

#### 批次操作流程

```text
設定篩選
  → 顯示符合人數
  → 勾選部分人物或「選取全部篩選結果」
  → 選擇贈送點數／VIP
  → 後端建立 10 分鐘有效的選取快照
  → 預覽影響
  → 二次確認
  → 執行並產生可稽核結果
```

建議 API：

```http
POST /api/admin/readers/bulk/preview
POST /api/admin/readers/bulk/execute
GET  /api/admin/readers/bulk/{operationId}
```

預覽回傳：

```json
{
  "selectionToken": "short-lived-token",
  "action": "GRANT_CREDITS",
  "targeted": 86,
  "eligible": 82,
  "skipped": 4,
  "willCreateReaderAccounts": 0,
  "totalCreditDelta": 8200,
  "expiresAt": "2026-07-27T13:10:00Z"
}
```

安全規則：

1. 目標人物以 `personId` 去重，禁止重複 Email 重複領取。
2. Preview 產生目標 ID 快照；Execute 不可重新解讀即時篩選，以免人數在確認後改變。
3. Execute 要帶 `idempotencyKey`，重送不得重複加點或延長 VIP。
4. 點數操作必填原因，每位成功者仍寫入既有 `credit_txn` 帳本。
5. 點數預設只處理已有 Reader 帳戶者；未建立帳戶者列入 skipped，不默默建帳戶。
6. VIP 可沿用既有行為建立尚未登入的 Reader 帳戶，但預覽必須顯示建立人數。
7. VIP 預設採 `EXTEND`：有效 VIP 從目前到期日往後加天數，無效 VIP 從現在起算，
   避免再次授予反而縮短原有效期。
8. 單次同步執行上限建議 500 人；超過改成背景批次並顯示進度。
9. 回傳 `targeted / succeeded / failed / skipped` 與可下載錯誤清單。
10. 任何單筆失敗不回滾其他成功項目，但每筆都要留下操作 ID 方便對帳。

Admin UI 在篩選列上方顯示條件 chips，批次操作列固定顯示：

- 已選取人數。
- 贈送點數與說明。
- VIP 天數與延長策略。
- 預覽按鈕。
- 明確的確認摘要，例如「將對 82 位讀者各贈送 100 點，共 8,200 點」。

### 8.3 排程寄送完成後的操作狀態

目前寄送歷史只要看到 `campaign.status = scheduled`，就顯示「修改」與
「取消排程」。但排程時間到達後，後端列表不會自動更新這個狀態，因此已經
寄出的舊排程仍可能保留兩顆不可再使用的按鈕。

這項修正列為其他 Phase 開始前可獨立交付的先行項目：

1. 後端以伺服器時間判斷排程是否仍可操作；只有
   `status = scheduled && scheduledAt > now` 才回傳 `canModifySchedule = true`。
2. 排程時間已到時，寄送歷史不再顯示「修改」與「取消排程」按鈕。
3. 後端同步整理已到期排程的顯示狀態，避免畫面在寄出後仍標示為
   `scheduled`；若尚未串接寄信商狀態查詢，至少以排程時間已到標示為
   `sent`，成功／失敗封數仍沿用既有統計，不能假裝成逐封投遞結果。
4. 修改與取消 API 都必須重新檢查 `scheduledAt > now`；即使使用者停留在
   舊畫面或直接呼叫 API，排程時間已到仍回傳 `409 Conflict`，不可只靠
   前端隱藏按鈕。
5. 操作剛好跨過排程時間時，以後端收到請求的時間為準，避免前端與伺服器
   時鐘不同造成競態。
6. 「重新整理」後必須立即反映最新可操作狀態；不要求管理員手動清除快取。

驗收案例：

- 未到排程時間：顯示「修改」與「取消排程」，兩項操作皆可成功。
- 已到或超過排程時間：兩顆按鈕皆不顯示。
- 在舊頁面保留按鈕直到時間到後再點擊：後端回 `409`，不會取消或重排。
- `cancelled`、`sent`、`failed`、立即寄送與只發布紀錄都不顯示排程操作。
- 手機與電腦同時開啟 Admin 時，兩邊重新整理後得到一致結果。

## 9. 遷移與上線順序

### Phase 0：寄送歷史排程狀態修正

**狀態：已完成（2026-07-27）**

- 新增後端 `canModifySchedule` 判斷與到期排程狀態整理。
- 修改、取消排程 API 增加時間邊界檢查。
- Admin 僅依後端回傳的可操作狀態顯示按鈕。
- 補上未到期、已到期、舊頁面競態與時區邊界測試。

### Phase 1：人物與活動底座

**狀態：已完成（2026-07-27）**

- 建立 `audience_person`、`audience_consent`、`audience_identity`、
  `audience_record`、`audience_fact`、匯入稽核表。
- 回填既有 `survey_response`。
- Email 合併時保留每份歷史問卷。
- 既有 API 透過相容服務讀寫新模型。

### Phase 2：Exam 手動直接同步

**狀態：已完成（2026-07-27）**

- Exam 提供只讀整合匯出 API。
- Admin 增加「同步 Exam」與預覽。
- 匯入人物、`survey_data`、測驗摘要及逐題結果。
- 完成 idempotency、錯誤報告與重新執行。

### Phase 3：Survey schema 化

**狀態：已完成（2026-07-27）**

- 建立 `form_definition`、`form_field`。
- 前端依 schema 顯示欄位。
- 新表單不再增加 `survey_response` 固定欄位。
- Admin 統計、圖表、原始資料欄位與 PDF 依 schema 動態產生。
- 提供簡易欄位設定，拖拉式設計器另案處理。

### Phase 4：讀者篩選與安全批次贈送

**狀態：已完成（2026-07-27）**

- 建立 Audience server-side filter API 與分頁。
- 支援基本資料、訂閱、VIP、點數、Survey、Exam 與活動條件。
- 建立選取快照、preview、execute、idempotency 與操作稽核。
- 點數與 VIP 統一回報成功、失敗與略過名單。

### Phase 5：分眾與自動同步

**狀態：已完成（2026-07-27）**

- Campaign filter 改讀 Fact。
- 保存常用分眾條件。
- Exam 依 cursor 排程增量同步。
- 加入資料保留、刪除與匯出流程。

### 9.1 本機驗證紀錄

- `survey-backend` 全套測試：619 項，0 failure、0 error、0 skipped。
- PostgreSQL 18.4 從空資料庫套用 V1～V11，以及既有 V6 升級至 V11，皆成功。
- Exam 匯出 API 的 Controller／Service 專項測試通過。
- 公開問卷實測會讀取已發布 schema，動態提交回傳 HTTP 201，並在人物中心形成
  `CONFIRMED` 的 `survey_form` 活動。
- Admin 實測可顯示動態圖表、伺服器端讀者篩選與批次操作預覽。
- 390 px 手機視窗沒有整頁橫向溢出；寬表格只在自己的容器內捲動。

## 10. 遷移不變式

以下條件未通過不得切換：

- 同一 Email 在 `audience_person` 只能有一筆。
- 原有每一筆 `survey_response` 都能追到一筆 `survey_submission` record。
- 已退訂 Email 遷移後仍為退訂，且任何匯入都不能把它改回訂閱。
- 已確認訂閱者人數與可寄送去重 Email 集合完全一致。
- Exam 同一批資料重跑兩次，第二次 `recordsCreated=0`。
- 同一人參加兩場 Exam，必須顯示一個人物、兩筆測驗紀錄。
- 名單匯入不自動寄信。
- Admin 新增 Survey 欄位後，統計圖表、原始資料表與 PDF 不需改 JavaScript 即可顯示。
- 公開統計不得出現標記為敏感或未允許公開的欄位。
- 相同人物在篩選結果重複出現時，批次加點仍只能成功一次。
- Preview 後篩選結果改變，不得影響已確認快照的實際目標。
- 相同 `idempotencyKey` 重送，點數與 VIP 不得再次發放。
- 授予有效 VIP 時不得縮短既有到期日。
- 排程時間已到的 campaign 不得再由 UI 或 API 修改、取消。

## 11. 成功指標

| 指標 | 目標 |
|---|---:|
| 新增 Survey 欄位所需 DB migration | 0 |
| Exam 同批資料重跑的重複活動 | 0 |
| 原有訂閱／退訂狀態遺失 | 0 |
| 有效 Exam profile 匯入成功率 | ≥ 99% |
| Admin 可追溯到來源與 import batch | 100% |
| 新增自訂來源與 mapping | 不需改程式 |
| 新增可統計 Survey 題目所需前端固定圖表程式 | 0 |
| 批次操作重複發放 | 0 |
| 批次操作可追溯率 | 100% |
| 已到期排程仍顯示修改／取消按鈕 | 0 |
| 10,000 人條件查詢首屏回應 | ≤ 2 秒 |

## 12. 建議先確認的產品決策

本設計先採以下建議值：

1. Exam 匯入逐題答案，但 Admin 列表只顯示摘要，詳情頁才展開。
2. Exam 匯入不代表同意寄信；沒有 consent timestamp＋version 就維持 pending。
3. 第一版 Survey 彈性化採 schema 設定，不先做拖拉式設計器。
4. 既有 `/api/survey` 保留相容期，避免一次重寫讀者入口與宣傳頁。
5. `survey-backend` 繼續作名單中心，Exam 不直接寫入它的資料庫。
6. Admin Survey 統計、圖表、原始資料與 PDF 全部共用 schema 描述。
7. 批次點數只處理已有 Reader 帳戶者；VIP 可建帳戶，且有效 VIP 採延長而非覆蓋。

以上七點已依建議值實作。正式部署時需先備份資料庫，再讓 Flyway 套用
V10、V11，並在 Zeabur 設定以下環境變數：

- `EXAM_INTEGRATION_BASE_URL`：Exam 正式站 API 根網址。
- `EXAM_INTEGRATION_TOKEN`：Survey 與 Exam 共用的唯讀整合 token。
- Exam 端 `AUDIENCE_EXPORT_TOKEN`：值必須與上述 token 相同。
- `EXAM_SYNC_ENABLED=true`：確認手動 preview／sync 正常後才開啟排程。
- `EXAM_SYNC_CRON`：預設每小時第 15 分鐘增量同步。

上線驗收仍須在正式資料庫重跑本文件第 10 節不變式；本機驗證不等同已部署。
