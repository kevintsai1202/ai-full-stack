# 子專案 C（第二段）：寄信後台 / 批量電子報 Design

**日期**：2026-06-20
**目標**：在既有 `survey-backend` 上建立一個受 `X-Admin-Key` 保護的寄信後台（`admin.html` + 後端 API），讓管理者能撰寫 Markdown 電子報、依條件挑收件名單、立即或排程發送（每位收件人帶各自退訂連結），並檢視寄送歷史與取消排程。

**所屬計畫**：land-page 行銷三部曲之 C（電子報/寄信）。C 第一段（退訂閉環 + 寄信基礎建設 + 問卷自動歡迎信）已上線，見 `docs/superpowers/specs/2026-06-19-survey-email-and-unsubscribe-design.md`。本文件是 C 第二段。

---

## 範圍

**包含**：
1. 受保護的寄信後台頁 `admin.html`（同源、vanilla HTML/JS、金鑰閘門）。
2. 後端：收件名單篩選查詢、Markdown 渲染、共用 Email 模板、批量發送服務（立即 + 排程）、ZSend batch/schedule/cancel 串接、campaign 資料模型、寄送歷史 API。
3. 安全閥：測試寄送給自己、發送前確認與收件數/額度提示。

**不包含（YAGNI）**：
- 多管理者帳號/角色系統（沿用單一 `X-Admin-Key`）。
- ZSend webhook（開信/點擊/退信即時回寫）；本段寄送狀態以「提交時的 accepted/failed + ZSend 回傳 id」為準。
- A/B 測試、範本庫、草稿自動儲存、富文本所見即所得編輯器。
- 自建排程器（排程交給 ZSend `/emails/schedule`，後端只提交+記錄）。

---

## 決策（已與開發者確認）

| 主題 | 決策 |
|---|---|
| 後台驗證 | 沿用 `X-Admin-Key`：admin.html 貼金鑰存 sessionStorage，所有 API 帶此標頭 |
| 發送時間 | **立即 + 排程都要**（立即走 `/emails/batch`，排程走 `/emails/schedule`） |
| 內文格式 | **Markdown**，伺服器端渲染為 HTML 並套品牌外框 + 退訂頁腳 |
| 發送對象 | 全部合格名單（同意 + 未退訂、去重）+ **基本篩選**（role、interest） |
| Markdown 渲染（取捨 A） | **A1 伺服器端為準**：`/campaign/preview` 回渲染 HTML，前端 iframe 顯示；發送用同一支渲染（所見即所送） |
| 寄送歷史模型（取捨 B） | **B1 新增 `campaign` 表**：每次發送一筆，`email_log` 加 `campaign_id` 關聯 |
| 每人退訂連結 | 一人一封（不可共用 `to:[多人]`），各帶 HMAC 個人化退訂連結（沿用第一段 `UnsubscribeTokenService`） |
| 實作分段 | 一份 spec；實作分兩段計畫：**先後端 API（curl 可完整測），再前端 admin.html** |

---

## ZSend API 能力對照（設計依據）

- `POST /emails/batch`：一次 ≤100 封，每封獨立 from/to/subject/html；**不支援 scheduled_at（僅立即）**。
- `POST /emails/schedule`：可排程，`scheduled_at`（ISO 8601、需未來）必填；一次一封；回 `{id, status:"enqueued"}`。
- `GET /emails/scheduled`、`GET /emails/scheduled/:id`、`DELETE /emails/scheduled/:id`（取消，回 `{success:true}`）。
- 立即與排程都計入每日額度（100/日、3000/月）；超額回 `429`。
- 故：**立即** = 把名單切成 ≤100 一批呼叫 batch；**排程** = 對每位收件人各呼叫一次 schedule（同一 `scheduled_at`）。兩者皆因「每人退訂連結不同」而需逐封建立 payload。

---

## 架構與資料流

```
admin.html (sessionStorage 存 X-Admin-Key)
  │  fetch（帶 X-Admin-Key）
  ▼
AdminKeyGuard（HandlerInterceptor，攔 /api/admin/**）
  ▼
AdminCampaignController
  ├─ GET  /api/admin/recipients?role=&interest=   → RecipientService（jsonb 篩選、去重）→ {count, sample[]}
  ├─ POST /api/admin/campaign/preview             → MarkdownRenderer + EmailTemplate → HTML
  ├─ POST /api/admin/campaign/test                → 寄 1 封給指定信箱（立即）
  ├─ POST /api/admin/campaign/send                → CampaignService.send(...)
  ├─ GET  /api/admin/campaigns / {id}             → 歷史與明細（campaign + email_log）
  └─ DELETE /api/admin/campaigns/{id}/schedule    → 逐筆 cancelScheduled(email_log.provider_message_id)

CampaignService.send：
  建 campaign(status=sending|scheduled)
   → RecipientService 取名單
   → 每人：EmailTemplate(MarkdownRenderer(md), 個人化退訂連結)
   → mode=now  : MailSender.sendBatch(批 ≤100)
     mode=sched: MailSender.schedule(每封, scheduledAt)
   → 每筆寫 email_log(campaign_id, status sent|scheduled|failed, provider id / error)
   → 更新 campaign(recipient_count/accepted/failed/status)
```

---

## 元件設計

### 1. 存取
- `AdminKeyGuard`：`HandlerInterceptor` 攔截 `/api/admin/**`，用既有常數時間比對驗 `X-Admin-Key`，失敗回 401。把目前 `SurveyController.list` 內嵌的金鑰檢查改用此 guard（移除重複）。
- `admin.html`：`noindex`；載入時若 sessionStorage 無金鑰則顯示輸入框；所有 fetch 自動帶 `X-Admin-Key`；收到 401 清除金鑰並重新要求。

### 2. 資料模型（Flyway `V4__create_campaign.sql`）
```
campaign(
  id BIGSERIAL PK,
  subject TEXT NOT NULL,
  markdown TEXT NOT NULL,
  body_html TEXT,                 -- 渲染後（稽核/重寄用）
  filter_role TEXT,               -- 篩選條件（null=不限）
  filter_interest TEXT,           -- 篩選條件（null=不限）
  mode TEXT NOT NULL,             -- now | schedule
  scheduled_at TIMESTAMPTZ,       -- mode=schedule 才有
  recipient_count INT NOT NULL DEFAULT 0,
  accepted_count INT NOT NULL DEFAULT 0,
  failed_count INT NOT NULL DEFAULT 0,
  status TEXT NOT NULL,           -- sending|sent|scheduled|cancelled|failed
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
)
```
`email_log` 加欄：`ALTER TABLE email_log ADD COLUMN campaign_id BIGINT;`（歡迎信為 null）。對應 `EmailLog` 新增 `campaignId` 欄位。

### 3. 後端服務
- **EmailTemplate**：把 `WelcomeMailService` 內嵌外框抽出為共用元件 `wrap(bodyHtml, unsubscribeLink)`；歡迎信與電子報共用（DRY；歡迎信改用它，行為不變）。
- **MarkdownRenderer**：commonmark-java（`org.commonmark:commonmark`）把 markdown→HTML。admin 為可信作者，做輕量處理即可。
- **RecipientService**：`findRecipients(role, interest)` 回去重小寫 email 清單；`consent=true AND unsubscribed=false`，role 用欄位相等、interest 用 PostgreSQL jsonb 包含（native query：`interest @> jsonb_build_array(:interest)` 或 `jsonb_exists`）。提供 count 與 sample（前 N 筆預覽）。
- **CampaignService**：依上述資料流發送；立即用 batch、排程用 schedule；每封帶個人化退訂連結（`UnsubscribeTokenService.sign` + `APP_PUBLIC_BASE_URL`）。逐筆寫 email_log；429/例外記 failed 不中斷整批。回 `{campaignId, recipientCount, accepted, failed}`。

### 4. ZSend client 擴充（`MailSender` 介面）
- 新增 `record Email(String to, String subject, String html)`。
- `List<SendResult> sendBatch(List<Email> emails)` → `/emails/batch`（≤100/呼叫，呼叫端負責切批）。
- `String schedule(Email email, Instant scheduledAt)` → `/emails/schedule`。
- `boolean cancelScheduled(String providerId)` → `DELETE /emails/scheduled/:id`。
- `ZSendMailSender` 實作；`NoopMailSender` 回假 id（`noop`）/ true，供本機與測試。
- 既有 `send(to,subject,html)` 保留（歡迎信用）。

### 5. Admin API（全部經 AdminKeyGuard）
| 端點 | 請求 | 回應 |
|---|---|---|
| `GET /api/admin/recipients` | `?role=&interest=` | `{count, sample:[email...]}` |
| `POST /api/admin/campaign/preview` | `{subject, markdown}` | 渲染後 HTML（含示意退訂連結） |
| `POST /api/admin/campaign/test` | `{subject, markdown, to}` | `{providerId}`（立即寄 1 封） |
| `POST /api/admin/campaign/send` | `{subject, markdown, filter:{role?,interest?}, mode, scheduledAt?}` | `{campaignId, recipientCount, accepted, failed}` |
| `GET /api/admin/campaigns` | — | campaign 列表 |
| `GET /api/admin/campaigns/{id}` | — | campaign + 每筆 email_log 狀態 |
| `DELETE /api/admin/campaigns/{id}/schedule` | — | 取消該 campaign 所有排程信，回 `{cancelled, failed}` |

### 6. 前端 admin.html
- 金鑰閘門 → 撰寫區（主旨、markdown textarea、iframe 即時預覽，預覽呼叫 `/campaign/preview`）→ 篩選（role 下拉「單選」、interest 下拉「單選」，皆可留空=不限；變更即更新「將寄給 N 人」由 `/recipients`）→ 測試寄送欄（寄給自己）→ 模式切換（立即 / 排程 + datetime-local）→ 發送（確認對話框顯示人數；>100 警告）→ 排程清單（可取消）→ 寄送歷史（campaign 列表 + 明細）。
- 單一 vanilla HTML 檔；若過大可拆出 `admin.js`/`admin.css`（實作時視情況）。

### 7. 安全 / 額度
- 所有 admin 端點走 `AdminKeyGuard`；金鑰只存 sessionStorage（關閉分頁即清）。
- 發送前：顯示收件數；> 100 顯示「超過每日額度，將有部分失敗」警告；確認對話框二次確認。
- 測試寄送給自己作為上線前安全閥。
- 排程時間需為未來（前端 + 後端雙驗）。

---

## 測試

- **單元（@WebMvcTest / 純 JUnit）**：
  - `MarkdownRenderer`：markdown → 預期 HTML 片段。
  - `EmailTemplate`：輸出含退訂連結與外框；歡迎信改用後既有測試仍綠。
  - `CampaignService`（mock MailSender + repos）：立即走 sendBatch、排程走 schedule；每封含個人化退訂連結（ArgumentCaptor 驗 html 內含該 email 的 token 連結）；email_log 逐筆 + campaign 統計；某封失敗 → failed 計數且不中斷。
  - `AdminKeyGuard`：無/錯金鑰 → 401；正確 → 放行（沿用既有測試風格）。
  - ZSend batch/schedule/cancel：`MockRestServiceServer` 驗 URL/body/`scheduled_at`，不打真 API。
- **整合（需 DB / 金鑰，手動）**：interest jsonb 篩選查詢、線上「測試寄送 → 立即發送一小撮 → 排程 → 取消排程」端到端。
- **前端**：可重跑 Playwright 腳本（放 `survey-backend/scripts/` 或 `e2e/`，中文註解）：金鑰閘門、預覽、收件數更新、確認對話框、（mock 或測試金鑰下）送出流程。

---

## 既有狀態 / 相依（實作起點）

- 第一段已提供：`UnsubscribeTokenService`、`MailSender`/`ZSendMailSender`/`NoopMailSender`/`MailConfig`、`EmailLog`/`EmailLogRepository`、`SurveyResponseRepository.findDistinctRecipients`、`WelcomeMailService`、`application.yml` 的 mail/secret/base-url 設定、V3 migration。
- 本段擴充：`MailSender` 介面加 batch/schedule/cancel、`email_log` 加 `campaign_id`（V4 含 ALTER）、新增 `campaign` 表、抽 `EmailTemplate`、`AdminKeyGuard` 取代內嵌金鑰檢查。
- 新依賴：`org.commonmark:commonmark`（Markdown 渲染）。
- 環境變數沿用第一段（`SEND_MAIL_API`/`MAIL_FROM`/`UNSUBSCRIBE_SECRET`/`APP_PUBLIC_BASE_URL`）；無新變數。
- Spring Boot 3.5.0 / Java 21；測試 `@WebMvcTest` + `@MockBean`（非 4.x）。

---

## 非目標（YAGNI）

- 不做帳號系統、webhook 回寫、A/B、範本庫、草稿自動存、WYSIWYG、自建排程器。
- 不改第一段既有行為（歡迎信僅改為共用 EmailTemplate，輸出等價）。
