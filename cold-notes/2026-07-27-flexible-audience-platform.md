# 彈性名單、Survey 與 Exam 整合開發紀錄

## 已採用的核心設計

- `survey-backend` 是唯一名單中心；Exam 只提供唯讀 cursor 匯出 API，不直接寫 Survey DB。
- 人物依正規化 Email 合併；同意狀態、身分來源、問卷／測驗活動與 typed Fact 分表保存。
- 匯入相同人物會合併人物但保留不同活動；退訂與抑制名單不得被後續匯入復原。
- 公開問卷與 Admin 統計共用 `form_definition`／`form_field`；已發布版本不可原地修改。
- 批次點數／VIP 必須先建立目標 ID 快照，再以 idempotency key 執行。
- Campaign 可用即時 Fact 條件或保存分眾；排程到期後 API 與 UI 都不可修改／取消。

## 主要交付

- Flyway V10 建立人物、活動、Fact、表單、匯入批次、分眾、批次操作、同步 cursor 與抑制資料表。
- Flyway V11 補齊公開問卷選項與自由建議欄位。
- Admin 支援來源管理、Excel 預覽／匯入、讀者伺服器端篩選、批次贈點／VIP、Exam 同步、
  動態統計與匯出、表單版本設定、個資匯出／刪除。
- 公開問卷依目前發布 schema 產生欄位；schema 無法載入時保留舊表單 fallback。
- Exam 正式專案新增 token 保護的 audience export API，並避免多個 bag 同時 EntityGraph fetch。

## 驗證基準

- `survey-backend`：619 tests，0 failure、0 error、0 skipped。
- PostgreSQL 18.4：空 DB V1→V11、既有 V6→V11 均成功。
- 公開動態問卷實際 POST 回 201，人物中心顯示 `CONFIRMED`、`survey_form`、一筆活動。
- Admin 桌機與 390 px 手機瀏覽器驗收完成；整頁無水平溢出。
- Exam 新匯出 API 專項測試通過；Exam 全套 182 tests 另有 7 個既有
  `ExamServiceTest`／`StatisticsServiceTest` 失敗，與本次匯出路徑無關。

## 正式部署前

- 先備份 PostgreSQL，再套用 V10、V11。
- Survey 設 `EXAM_INTEGRATION_BASE_URL`、`EXAM_INTEGRATION_TOKEN`。
- Exam 設相同值的 `AUDIENCE_EXPORT_TOKEN`。
- 先手動 preview／sync 驗證，再開 `EXAM_SYNC_ENABLED=true` 與 cron。
- 正式站重跑設計文件第 10 節遷移不變式；本機完成不代表已部署。
