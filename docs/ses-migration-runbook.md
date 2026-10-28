# 寄信服務切換手冊：Zeabur Email（ZSend）→ Amazon SES

適用：survey-backend（電子報後台）。SES 寄信由自製的 [ses-mail-spring-boot-starter](https://github.com/kevintsai1202/ses-mail-spring-boot-starter) 提供，環境變數 `SESMAIL_ENABLED=true` 就改用 SES，關掉即回到 ZSend（ZSend 金鑰保留為退路）。

## 0. 先知道的差異

| | ZSend | SES（本系統的做法） |
| --- | --- | --- |
| 交易信（登入、確認、歡迎） | 同步寄出 | 同步寄出 |
| 批次群發 | ZSend 批次 API | 寫入本機佇列 `scheduled_mail`，派送器每 10 秒依速率送出 |
| 排程與取消 | ZSend 排程 API | 本機佇列；取消只對還沒被派送器認領的信有效 |
| 額度 | 每日／每月額度 | 24 小時滾動上限，剩餘已扣掉佇列待寄 |
| 退信 | — | 第一階段只靠 SES 帳號層級停寄名單（第二階段再接 SNS 事件） |

後台群發顯示「已送出」時，信是**進了佇列**，實際寄出約在一分鐘內完成。派送失敗會把對應的 `email_log` 改成 `failed`。

## 1. 開 SES 與驗證網域

1. AWS 主控台 → Amazon SES，右上角區域選 **Asia Pacific (Tokyo) `ap-northeast-1`**（要與 `AWS_SES_REGION` 一致）。
2. **Configuration → Identities → Create identity → Domain**，輸入 `springai.world`：
   - 勾選 **Easy DKIM**（RSA 2048）。
   - 勾選 **Use a custom MAIL FROM domain**，填 `mail.springai.world`，「Behavior on MX failure」選 *Use default MAIL FROM domain*。
3. `springai.world` 的 DNS 在 **GoDaddy**（名稱伺服器 `domaincontrol.com`），不在 Route53，所以建立身分時兩個「發佈 DNS 記錄至 Route53」都要取消勾選。到 GoDaddy → 網域 → DNS → 新增記錄，照主控台給的值新增（GoDaddy 的「名稱」只填子網域部分，不含 `.springai.world`）：
   - 3 筆 DKIM `CNAME`：`xxxx._domainkey` → `xxxx.dkim.amazonses.com`。
   - `mail.springai.world` 的 `MX`：`10 feedback-smtp.ap-northeast-1.amazonses.com`。
   - `mail.springai.world` 的 `TXT`：`v=spf1 include:amazonses.com ~all`。
   - **根網域的 SPF 不用動**：SPF 是對 MAIL FROM（`mail.springai.world`）檢查的。一個網域只能有一筆 `v=spf1`，不要在根網域再加第二筆。
   - DMARC（`_dmarc.springai.world`）沿用現有設定即可；DKIM 對齊 `springai.world`，DMARC 會通過。
4. 等主控台的 Identity 狀態變成 **Verified**、DKIM 與 MAIL FROM 都是 **Successful**（通常幾分鐘到一小時）。

## 2. 申請離開沙箱

沙箱只能寄給已驗證的信箱，每天 200 封、每秒 1 封。**Account dashboard → Request production access**：

- Mail type：**Marketing**
- Website URL：讀者站網址
- Use case 可參考：

```text
We send a personal technical newsletter (Traditional Chinese) to about 310 subscribers, 1–2 issues per week, plus transactional emails (magic-link login, subscription confirmation, welcome).
All subscribers opted in through our website with double opt-in confirmation. Every newsletter contains a one-click personal unsubscribe link, and unsubscribes are applied immediately.
We rely on the SES account-level suppression list for bounces and complaints, and we monitor bounce and complaint rates in the SES console. We expect fewer than 5,000 emails per month.
```

通常一天內會回覆；被要求補資料就照實補上退訂與名單來源的說明。

## 3. 建立 IAM 金鑰

建立一個專用 IAM 使用者（例如 `survey-backend-ses`），只掛這份 inline policy，再建立 Access key（SQS 那段由第 10 節的腳本加上）：

```json
{
  "Version": "2012-10-17",
  "Statement": [
    { "Effect": "Allow", "Action": ["ses:SendEmail", "ses:GetAccount"], "Resource": "*" },
    { "Effect": "Allow", "Action": ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:ChangeMessageVisibility"],
      "Resource": "arn:aws:sqs:ap-northeast-1:322987693466:ses-events" }
  ]
}
```

## 4. 設定環境變數（Zeabur 服務）

| 變數 | 值 |
| --- | --- |
| `SESMAIL_ENABLED` | `true` |
| `AWS_SES_REGION` | `ap-northeast-1` |
| `AWS_SES_ACCESS_KEY_ID` | 上一步的 Access key ID |
| `AWS_SES_SECRET_ACCESS_KEY` | 上一步的 Secret |
| `MAIL_SES_MAX_SEND_RATE` | 沙箱填 `1`；離開沙箱後依主控台顯示的 *Maximum send rate*，建議填 `5` |

`SEND_MAIL_API`、`ZEABUR_API_TOKEN` 先**保留**，回滾時用得到。`MAIL_FROM` 沿用即可，中文顯示名稱程式會自動編碼。

## 5. 切換前檢查

切換那一刻，ZSend 上已排程的信會留在 ZSend（照常寄出），但後台再也取消不了。所以要挑**沒有待寄排程**的時候切換：

```powershell
# 連線字串改成正式資料庫；兩個數字都要是 0 才切換
psql $env:JDBC_PSQL_URL -c "SELECT count(*) AS zsend_scheduled FROM email_log WHERE status = 'scheduled';"
psql $env:JDBC_PSQL_URL -c "SELECT count(*) AS campaign_scheduled FROM campaign WHERE status = 'scheduled';"
```

## 6. 切換與驗證

1. 改好環境變數後重新部署，啟動 log 要看到 `寄信服務：Amazon SES`。
2. 後台寄信額度列要顯示 `SES 24 小時上限 …`；還在沙箱會註明「沙箱模式」。
3. 用自己的信箱走一次讀者登入（交易信，同步寄出）。在 Gmail 的「顯示原始郵件」確認 **SPF、DKIM、DMARC 都是 PASS**，寄件人顯示「凱文大叔」。
4. 挑一期 **BASIC** 的舊文，對 1～2 個自己的信箱補寄（批次，走佇列），約一分鐘內要收到。PREMIUM 文章不能補寄，後台會擋。
5. 觀察 log 有沒有 `SES 寄信失敗`、`SES 寄信暫時失敗`。

## 7. 回滾

1. `SESMAIL_ENABLED` 改回 `false`（或刪除）並重新部署。
2. 佇列裡還沒送出的信不會再被派送，先看有多少、決定要不要取消：

```powershell
psql $env:JDBC_PSQL_URL -c "SELECT status, count(*) FROM scheduled_mail GROUP BY status;"
# 確定不寄了才執行：
psql $env:JDBC_PSQL_URL -c "UPDATE scheduled_mail SET status = 'CANCELLED', updated_at = now() WHERE status = 'PENDING';"
```

## 8. 卡在 SENDING 的信（程序中斷後）

派送器認領一封信後、呼叫 SES 前後若程序被重啟，那一列會停在 `SENDING`。程式**不會自動重寄**：無法得知 SES 到底收到了沒，重複寄出一期電子報比漏寄一封更糟。log 每 30 分鐘會出現一次 `卡在 SENDING` 的警告。

```powershell
psql $env:JDBC_PSQL_URL -c "SELECT id, recipient, subject, updated_at FROM scheduled_mail WHERE status = 'SENDING' AND updated_at < now() - interval '10 minutes';"
```

逐筆判斷：

- 確定讀者有收到（或寧可漏寄）：`UPDATE scheduled_mail SET status = 'SENT', updated_at = now() WHERE id = <id>;`
- 確定沒收到、要補寄：`UPDATE scheduled_mail SET status = 'PENDING', next_attempt_at = now(), updated_at = now() WHERE id = <id>;`

## 9. 日常監控

已設定的警報（2026-10-27，CloudWatch 東京區，通知經 SNS topic `ses-alerts` 寄到 kevintsai1202@gmail.com，訂閱需先在信箱點確認）：

| 警報 | 指標 | 門檻（每小時最大值） | AWS 介入門檻 |
| --- | --- | --- | --- |
| `ses-bounce-rate-high` | `AWS/SES Reputation.BounceRate` | > 2% | 5% 警告、10% 可能暫停 |
| `ses-complaint-rate-high` | `AWS/SES Reputation.ComplaintRate` | > 0.05% | 0.1% 警告、0.5% 可能暫停 |

還沒開始寄信時狀態是 INSUFFICIENT_DATA，屬正常；缺資料視為未超標。恢復正常時也會寄一封 OK 通知。

- SES 主控台 **Reputation metrics**：退信率維持在 5% 以下、檢舉率 0.1% 以下，超過會被 AWS 警告甚至暫停寄送（後台額度列會顯示「SES 寄送已暫停」）。
- 停寄名單：**Configuration → Suppression list**，退信與檢舉的地址會自動進來、之後不會再寄。

## 10. 退信／檢舉／送達事件（SQS 或 webhook 二選一）

事件一律由 **SES configuration set → SNS topic** 發出，survey-backend 有兩種接收方式，用環境變數切換，**只開一個**（兩個都開會收到重複事件）：

| | SQS 拉取（建議） | HTTPS webhook |
| --- | --- | --- |
| 流程 | SNS → SQS 佇列 → 本服務長輪詢 | SNS → `POST /sesmail/sns` |
| 開關 | `SESMAIL_SQS_ENABLED=true` | `SESMAIL_SNS_ENABLED=true` |
| 要建的 AWS 資源 | SQS 佇列、死信佇列、IAM SQS 權限 | 只有 SNS 的 HTTPS 訂閱 |
| 服務停機、重新部署時 | 事件留在佇列，回來再處理 | SNS 短暫重試（預設約 1 分鐘）後遺失 |
| 處理失敗（例如資料庫錯誤） | 不刪訊息，SQS 重送，5 次仍失敗進死信佇列 | 只記 log，不重試 |

兩種模式共用同一套驗簽、topic 白名單（`SESMAIL_SNS_TOPIC_ARNS` 都必填）與事件處理：

- 永久退信、檢舉 → 讀者自動退訂（同意事件來源 `ses-bounce`／`ses-complaint`）；暫時退信只記 log。
- 送達 → `email_log.delivered_at`、`campaign_recipient.delivered_at` 記下第一次送達時間，後台收件人清單顯示「已送達（收件端已收下）」。狀態值不變。

### 10.1 SQS 拉取

AWS 資源（東京區）以 [`survey-backend/scripts/aws/setup-ses-events.sh`](../survey-backend/scripts/aws/setup-ses-events.sh) 在 CloudShell 建立（可重複執行），結束時會印出要設的環境變數：

| 資源 | 名稱 |
| --- | --- |
| SNS topic | `ses-events`（存取政策允許本帳號的 SES 發佈） |
| SQS 佇列 | `ses-events`（可見逾時 60 秒、重試 5 次後轉死信佇列；佇列政策只允許 `ses-events` topic 發送） |
| SQS 死信佇列 | `ses-events-dlq`（保留 14 天） |
| 訂閱 | `ses-events` topic → `ses-events` 佇列（不開 raw message delivery） |
| SES configuration set | `newsletter`，事件目的地 Bounce、Complaint、Delivery → `ses-events` topic |
| IAM | `survey-backend-ses` 的 `ses-send-only` 政策加 `sqs:ReceiveMessage`、`sqs:DeleteMessage`、`sqs:ChangeMessageVisibility`（只限 `ses-events` 佇列） |

**不要**再到 SES 身分頁設定 Feedback notifications：configuration set 已經會發佈退信與檢舉，兩邊都設的話每封退信會收到兩次。

Zeabur 環境變數（AWS 資源建好後才設；`AWS_SES_CONFIGURATION_SET` 指向不存在的 configuration set 會讓寄信失敗）：

| 變數 | 值 |
| --- | --- |
| `SESMAIL_SQS_ENABLED` | `true` |
| `SESMAIL_SQS_QUEUE_URL` | `https://sqs.ap-northeast-1.amazonaws.com/322987693466/ses-events` |
| `SESMAIL_SNS_TOPIC_ARNS` | `arn:aws:sns:ap-northeast-1:322987693466:ses-events` |
| `AWS_SES_CONFIGURATION_SET` | `newsletter` |

驗證（切到 SES 之後）：在 SES 主控台「傳送測試電子郵件」，收件人填模擬信箱 `bounce@simulator.amazonses.com`、`complaint@simulator.amazonses.com`、`success@simulator.amazonses.com`，configuration set 選 `newsletter`。log 應出現對應事件；模擬信箱不在名單上，不會有人被退訂。

死信佇列有訊息時，代表某些事件連續處理失敗，先看 log 的錯誤，修好後到 SQS 主控台把 `ses-events-dlq` 的訊息「開始重新驅動」回 `ses-events`。

### 10.2 HTTPS webhook（不用 SQS）

適合不想多管 SQS、能接受停機期間漏掉少數事件的情況。順序很重要：webhook 要先上線，SNS 才能完成訂閱確認。

1. **SNS topic** `ses-events` 與 **configuration set** `newsletter` 的事件目的地同 10.1 表格（可在 SES 主控台手動建，或在 CloudShell 只執行 `setup-ses-events.sh` 第 4 段）。不要建 SQS 佇列的訂閱，否則改回 SQS 前事件會堆在佇列裡。
2. **設環境變數並部署**：`SESMAIL_SNS_ENABLED=true`、`SESMAIL_SNS_TOPIC_ARNS=arn:aws:sns:ap-northeast-1:322987693466:ses-events`，`SESMAIL_SQS_ENABLED` 保持 `false`。
3. **確認 webhook 從外面打得到**（必須回 **403**：程式收到了、偽造內容被驗簽拒絕；回 404 代表請求沒進到程式，先別建訂閱）：

```powershell
curl.exe -s -o NUL -w "%{http_code}" -X POST https://springai-survey.zeabur.app/sesmail/sns -d '{}'
```

4. **建 HTTPS 訂閱**：SNS → `ses-events` topic → Create subscription，Protocol **HTTPS**，Endpoint `https://springai-survey.zeabur.app/sesmail/sns`（**問卷網域**；讀者網域會被 Host 白名單擋掉），**不要**勾 raw message delivery。webhook 會自動確認，訂閱狀態幾秒內變成 Confirmed；沒變的話看 log 有沒有 `SNS 訊息簽章驗證失敗` 或 `未允許的 topic`。
5. **設 `AWS_SES_CONFIGURATION_SET=newsletter`**，再照 10.1 的模擬信箱方式驗證。

從 webhook 改成 SQS：先照 10.1 建資源並設好 SQS 變數，再把 `SESMAIL_SNS_ENABLED` 改回 `false`，最後刪掉 HTTPS 訂閱。

已知限制：交易信（登入信等）的送達事件有極小機率比寄送記錄先到，那一封的送達時間會是空的，不影響任何功能。

## 尚未實作

- 一鍵退訂標頭（`List-Unsubscribe`、`List-Unsubscribe-Post`）。
- 定期清理 `scheduled_mail` 已送出的舊資料（每封信存著完整 HTML）。
