# 寄信服務改用 Amazon SES 實作計畫

**Goal:** Zeabur Email（ZSend）方案只剩每日 100 封，寄不完一期電子報（約 310 人）。新增 Amazon SES 寄信實作，以環境變數切換，ZSend 保留為退路。

**Architecture:** 維持 `MailSender` 介面不變，上層（`CampaignService`、`CampaignDeliveryService`、邀請／登入／歡迎信）一行不改。SES 沒有排程 API，所以在 `mail` package 內自建「待寄佇列」（`scheduled_mail` 表 + 背景派送器）：`schedule()` 與 `sendBatch()` 都只寫入佇列，由派送器依 SES 的每秒上限逐封送出；`send()`（交易信）直接同步呼叫 SES。額度改由 SES `GetAccount` 查 24 小時上限與已寄量，並扣掉佇列中尚未送出的封數。

**Tech Stack:** Spring Boot 3.5、AWS SDK v2 `sesv2`（沿用既有 BOM 2.47.3）、Flyway、JUnit 5 + Mockito、本機 `survey-test-db`（5433）。

---

## 範圍

**這次做**

1. `SesMailSender`：`send` 同步寄出；`sendBatch`／`schedule` 寫入佇列；`cancelScheduled` 只取消自己佇列裡還沒送出的信。
2. 寄件人中文顯示名稱做 RFC 2047 編碼；主旨與 HTML 內文指定 UTF-8。
3. `scheduled_mail` 表（V27）、實體、repository；以條件式 UPDATE 認領與取消，避免派送與取消互相競爭。
4. `ScheduledMailDispatcher`：每 10 秒撈到期的待寄信，依速率上限送出。節流與 5xx 退回待寄並延後重試（最多 5 次），被拒絕類錯誤標為失敗。程序中斷後卡在 SENDING 的列**不自動重寄**，只記警告，留給人工確認。派送結果回寫 `email_log`。
5. `MailQuotaService` 支援 `app.mail.provider=ses`：剩餘 = 24 小時上限 − 已寄 − 佇列待寄；`status` 顯示沙箱與帳號狀態。
6. `MailConfig` 依 `app.mail.provider`（預設 `zsend`）選實作；SES 相關 bean 用 `@ConditionalOnProperty`，預設環境與所有既有 Spring 測試行為不變。
7. 管理後台額度文字：SES 顯示「24 小時上限」，fallback 訊息不再寫死 Zeabur。
8. 切換手冊 `docs/ses-migration-runbook.md`（PowerShell 語法）。

**這次不做（第二階段）**

- SES 退信／檢舉事件（SNS → webhook），先靠 SES 帳號層級的停寄名單。
- `List-Unsubscribe` 一鍵退訂標頭。
- 佇列中已送出列的定期清理。

## 任務

- [x] T1 pom 加 `sesv2`（比照 s3 排除 apache5-client／netty-nio-client）
- [x] T2 V27 migration、`ScheduledMail` 實體、`ScheduledMailRepository`（claim／cancel／countPending／findDue）＋ DB 測試
- [x] T3 `MailAddressEncoder`（RFC 2047）＋測試
- [x] T4 `SesMailSender` ＋測試（Mockito 攔 `SendEmailRequest`，驗 UTF-8 與編碼後寄件人）
- [x] T5 `ScheduledMailDispatcher` ＋測試（成功、節流重試、拒絕失敗、速率、取消競爭）
- [x] T6 `MailQuotaService` SES 分支＋測試
- [x] T7 `MailConfig`／`SesMailConfiguration`／application.yml
- [x] T8 admin.html 額度文字
- [x] T9 切換手冊
- [x] T10 驗證：`mail.**`、`newsletter.**`、`MigrationSafetyTest`、`PackageDependencyTest`（295 項全綠）、`SesMailWiringTest`、`verify-admin-quota.mjs`。`postgres` 測試庫不需同步：直連它的測試都是 `ddl-auto=none`，不會檢查新表

## 環境變數（新）

| 變數 | 說明 | 預設 |
| --- | --- | --- |
| `MAIL_PROVIDER` | `zsend` 或 `ses` | `zsend` |
| `AWS_SES_REGION` | SES 區域 | `ap-northeast-1` |
| `AWS_SES_ACCESS_KEY_ID`／`AWS_SES_SECRET_ACCESS_KEY` | 只有 `ses:SendEmail`、`ses:GetAccount` 權限的 IAM 金鑰 | 無 |
| `AWS_SES_CONFIGURATION_SET` | 選填，日後接退信事件用 | 空 |
| `MAIL_SES_MAX_SEND_RATE` | 派送器每秒上限（上限仍受 SES 帳號 MaxSendRate 約束） | `5` |
