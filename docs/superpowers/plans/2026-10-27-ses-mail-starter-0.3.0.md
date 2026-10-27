# ses-mail-spring-boot-starter 0.3.0：SQS 拉取事件＋送達事件 實作計畫

**Goal:** 退信／檢舉／送達事件改走 SNS → SQS → 應用程式拉取（部署、重啟期間的事件不會遺失，處理失敗會重試並進死信佇列），並記錄每封信的「已送達」時間。

**決策（已確認，2026-10-27，經 advisor 檢驗）**
- 事件接收以 SQS 拉取為主；0.2.0 的 HTTPS webhook 保留但預設關閉。
- 「已送達」用獨立欄位 `delivered_at`，**不新增狀態值**：`sent`／`SENT` 被邀請信去重、優惠券去重、群發統計、讀者收信數、收件人狀態對照等流程使用，新增狀態會讓它們出錯。

## 實作細節（advisor 指出，開工前定案）

1. **Delivery 事件的 JSON 形狀不同**：`delivery.recipients` 是字串陣列，不是 `{emailAddress}` 物件陣列；解析器與測試要用真實形狀。
2. **失敗語意**：`SnsWebhookHandler` 新增 `Result.FAILED`（任一 listener 拋例外）。SQS 拉取在 `FAILED`／`REJECTED` 時**不刪除**訊息，交給 SQS 重試、超過次數進死信佇列；webhook 對 `FAILED` 仍回 200（對外行為不變）。
3. **索引**：`scheduled_mail (provider_message_id)` 建索引（starter schema SQL 與 survey-backend V28 都加），避免每個事件掃整張含 HTML 的表。
4. **事件記錄新增 `ref` 欄位**（`SesBounce`、`SesComplaint`、新的 `SesDelivery`）：由 starter 用 `scheduled_mail` 把 SES MessageId 對回呼叫端參照（ses-batch-*／ses-sched-*）；查不到時（交易信）`ref` 等於 MessageId。這是 API 不相容變更，唯一使用者是 survey-backend，同一個 commit 一起改，不保留 0.2.0 相容。
5. **分層**：送達 listener 同時用到 `EmailLogRepository`（mail）與 `CampaignRecipientRepository`（newsletter），放在 `newsletter` package。更新條件：`email_log` 以 `(provider_message_id = ref, lower(recipient))`；`campaign_recipient` 以 `(provider_message_id = ref, email_normalized)`。
6. **避免重複事件**：事件由 configuration set 發佈，**不設**身分層級的 Feedback notifications；切換手冊第 10 節改寫成 SQS 流程，移除 HTTPS 訂閱、403 檢查與 `SESMAIL_SNS_ENABLED`。`AWS_SES_CONFIGURATION_SET` 必須在 configuration set 建好之後才設（否則寄信失敗）。
7. **拉取器生命週期**：長輪詢 `WaitTimeSeconds=20`；停止時至少等 25 秒再強制中斷；收取錯誤時退避 30 秒，不空轉。`sqs` 依賴同樣排除 apache5-client／apache-client／netty-nio-client。
8. **後台只動最小範圍**：只有收件人清單在 `delivered_at` 有值時加「已送達」標示；群發統計、讀者收信數、狀態對照完全不動。

備註：參照解析依賴 `scheduled_mail` 的資料還在；日後若做清理，至少保留 30 天（SES 重試最長約 14 小時）。

## 任務

- [x] A1 starter：Delivery 解析（真實 JSON）＋事件記錄加 `ref`＋MessageId→參照解析（索引）
- [x] A2 starter：`Result.FAILED` 失敗語意（webhook 回 200、SQS 不刪）
- [x] A3 starter：`SqsEventPoller`（SmartLifecycle、長輪詢、退避、停止等待）＋`sesmail.sqs.*` 自動設定
- [x] A4 starter：README、tag 0.3.0、JitPack 建置
- [x] B1 survey-backend：V28（兩個 `delivered_at`、message_id 索引）
- [x] B2 survey-backend：`newsletter` 內送達 listener；`SesSuppressionListener` 改用新記錄
- [x] B3 survey-backend：收件人清單「已送達」標示、application.yml `sesmail.sqs.*`、切換手冊第 10 節改寫
- [x] B4 驗證：starter 全部測試；survey-backend `mail`／`newsletter`／`audience`／`MigrationSafetyTest`／`PackageDependencyTest`／wiring，以 JitPack 產物執行
- [ ] C1 AWS（CloudShell，腳本 survey-backend/scripts/aws/setup-ses-events.sh）：SQS 佇列＋死信佇列＋佇列政策、訂閱 topic、configuration set `newsletter` 三種事件、IAM 加 SQS 權限

## 環境變數（新）

| 變數 | 值 |
| --- | --- |
| `SESMAIL_SQS_ENABLED` | `true` |
| `SESMAIL_SQS_QUEUE_URL` | C1 建立的佇列網址 |
| `SESMAIL_SNS_TOPIC_ARNS` | `arn:aws:sns:ap-northeast-1:322987693466:ses-events` |
| `AWS_SES_CONFIGURATION_SET` | `newsletter`（C1 完成後才設） |

## 驗證結果

- starter：66 項測試通過，JitPack 0.3.0 建置成功（tag 90bb691）。
- survey-backend：清掉本機 `~/.m2` 的 starter 後以 JitPack 產物執行 `mail`、`newsletter`、`audience`、`MigrationSafetyTest`、`PackageDependencyTest`，352 項通過。
- 注意：IDE 的 language server 會把（含編譯錯誤的）class 寫進 `target/`，讓 Maven 誤判「已是最新」而略過編譯；驗證一律用 `mvn clean`。
- 已知限制：交易信的送達事件有極小機率比 `email_log` 寫入早到，那封的 delivered_at 會是空的。
