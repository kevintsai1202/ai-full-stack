# SES 寄信模組抽成 Spring Boot Starter 實作計畫

**Goal:** 把 survey-backend 的 SES 寄信、待寄佇列、額度查詢抽成可重用的套件 `ses-mail-spring-boot-starter`（獨立公開 repo，JitPack 發佈），survey-backend 改為依賴它；再加上退信／檢舉事件（SNS webhook）。

**Architecture:** 套件以 JdbcClient 存取佇列（不用 JPA，避免 `@EntityScan` 蓋掉應用程式的實體掃描），自帶單執行緒排程器派送，透過 `DeliveryListener`／`SesEventListener` 回呼把結果交給應用程式。survey-backend 保留 `MailSender` 介面、交易信保留額度、email_log 回寫與讀者退訂邏輯。

**Tech Stack:** Java 21、Spring Boot 3.5.x（以 `spring-boot-dependencies` BOM 管理，不用 starter-parent）、AWS SDK v2 2.47.3（`sesv2`、`sns-message-manager`）、JUnit 5 + Mockito、本機 `survey-test-db`（5433）。

---

## 發佈與座標

- repo：`https://github.com/kevintsai1202/ses-mail-spring-boot-starter`（公開），本機路徑 `D:\GitHub\ses-mail-spring-boot-starter`
- 座標：`com.github.kevintsai1202:ses-mail-spring-boot-starter:<tag>`，tag 不加 v（`0.1.0`），本機 install 與 JitPack 座標一致
- `jitpack.yml`：`jdk: openjdk21`、`install: mvn -B install -DskipTests`（DB 測試只在本機跑）
- Java package：`io.github.kevintsai1202.sesmail`

## 版本切分

**0.1.0（先上線）**：寄信、RFC 2047 寄件人編碼、待寄佇列（排程／取消／批次）、派送器、額度快照、`DeliveryListener`、schema SQL 與 `initialize-schema`。

**0.2.0**：SNS webhook（簽章驗證用 SDK 的 `sns-message-manager`、Topic ARN 白名單、只自動確認白名單 topic 的訂閱）、`SesEventListener`（永久退信、暫時退信、檢舉）。

**順序保護**：如果 SES 生產存取權在重構驗證完之前核准，就照原計畫部署 `047ac79` 切換；套件遷移之後再上。survey-backend 的重構要等 tag 推上 GitHub、JitPack 建置成功、並在**清掉本機 `~/.m2` 的套件快取**後用 JitPack 產物跑過測試，才推 main。

## 套件內容（0.1.0）

| 類別 | 職責 |
| --- | --- |
| `SesMailProperties` | `sesmail.*` 設定（enabled、region、金鑰、from、reply-to、configuration-set、max-send-rate、dispatch-interval、queue.initialize-schema） |
| `SesMailAutoConfiguration` | `@AutoConfiguration`、`@ConditionalOnClass(SesV2Client)`、`@ConditionalOnProperty(sesmail.enabled)`；金鑰空白時改用 SDK 預設憑證鏈 |
| `MailAddressEncoder` | 中文顯示名稱 RFC 2047 編碼 |
| `SesMailClient` | `send`（同步）、`enqueueBatch`（單一交易寫入）、`schedule`、`cancel` |
| `ScheduledMailQueue` | JdbcClient 版佇列：條件式 UPDATE 認領／取消／完成／重試／失敗、待寄數、卡住數 |
| `ScheduledMailDispatcher` | 專用單執行緒 `ScheduledExecutorService` + `SmartLifecycle`；停止時取消後續並等待進行中的寄送；listener 例外不中斷迴圈；卡在 SENDING 不自動重寄 |
| `DeliveryListener` | `onSent(ref, recipient, messageId)`、`onFailed(ref, recipient, error)` |
| `SesQuotaService` | `snapshot()`：status（sandbox／paused／healthy…）、max24h、sent24h、pending、maxSendRate |
| `schema/ses-mail-postgresql.sql` | 與 survey-backend V27 逐欄相同，`IF NOT EXISTS` |

## survey-backend 遷移

- 刪除：`ScheduledMail`、`ScheduledMailRepository`、`ScheduledMailDispatcher`、`SesQuotaProbe`、`MailAddressEncoder`、`SesMailConfiguration` 與對應測試（測試移到套件）。保留 V27。
- `SesMailSender` 改成把 `MailSender` 轉給 `SesMailClient` 的薄轉接層。
- `MailQuotaService` 讀 `SesQuotaService.snapshot()`，保留 reserve 計算。
- `mail` 內新增 `DeliveryListener` 實作：失敗時呼叫 `EmailLogRepository.markDeliveryFailed`。
- 0.2.0：`audience` 內新增 `SesEventListener` 實作：永久退信與檢舉 → `unsubscribeByEmail` + `appendConsentByEmail(..., CONSENT_UNSUBSCRIBED, "ses-bounce"／"ses-complaint")`，email 正規化沿用 `trim().toLowerCase(Locale.ROOT)`；暫時退信只記 log。
- 設定改用 `sesmail.*`（環境變數名稱沿用 `AWS_SES_*`、`MAIL_SES_*`，切換手冊同步更新）。
- 0.2.0 前確認 SNS 端點不受 `AdminEntryHostFilter`、gateway、Zero Trust 擋住。

## 驗證

- 套件：單元測試 + JdbcClient 佇列的 DB 測試（5433）。
- survey-backend：`mail.**`、`newsletter.**`、`audience.**`（0.2.0）、`MigrationSafetyTest`、`PackageDependencyTest`、`SesMailWiringTest`、`verify-admin-quota.mjs`，以 JitPack 產物執行。
- 不跑：全套測試、真實 SES 寄送、SNS 端對端（需 AWS 端設定完成）。

## 任務

- [x] S1 建立套件專案骨架（pom、jitpack.yml、README、自動設定註冊）
- [x] S2 TDD：encoder、client、queue（DB）、dispatcher、quota、auto-config 測試
- [x] S3 建 GitHub repo、推送、tag 0.1.0、觸發 JitPack 建置並確認成功
- [x] S4 survey-backend 改用套件（清快取後以 JitPack 產物跑測試）
- [x] S5 commit survey-backend、更新切換手冊
- [ ] S6 0.2.0：SNS webhook + SesEventListener（套件）＋ survey-backend 退訂實作
