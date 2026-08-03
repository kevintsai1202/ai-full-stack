# 課程優惠券寄送系統設計

日期：2026-08-04
狀態：待審閱
前置：電子報問卷整合系統（2026-08-03）已上線——名單來源即其填答資料；寄送機制沿用邀請信（InviteService）與工商卡（email-safe 版型）前例。

## 1. 目標

站長建立「課程優惠券活動」（課程內容自填），寄送給**填過指定問卷的人**：可依答案條件篩選、可逐人勾選，同一活動同人終身只寄一次。

## 2. 決策記錄

| # | 決策 | 選擇 | 理由 |
| --- | --- | --- | --- |
| D1 | 優惠券形式 | 共用優惠碼（平台端建碼，本系統只負責寄） | 實作最簡；兌換限制由課程平台管理 |
| D2 | 內容填寫 | 固定欄位套版（課程名／文案／連結／優惠碼／期限） | 填寫門檻低、視覺一致、不會排壞版 |
| D3 | 名單粒度 | 選問卷＋依答案篩＋**逐人勾選** | SurveyFilter 既有能力＋人工終審控制 |
| D4 | 防重寄 | email_log type=`coupon:{campaignId}` 終身一次 | 補寄安全、沿用邀請信冪等前例 |
| D5 | 點擊追蹤 | 範圍外（course_url 直連） | 兌換數據平台後台已有，不重複造輪子 |
| D6 | 架構 | 獨立 coupon 模組（不泛化 InviteService、不掛 Campaign 管線） | 職責乾淨；Campaign 語意是期別電子報，混入會汙染統計 |

## 3. 現況依據（已查證）

- `AudienceSearchService.SurveyFilter(formKey, version, answers)`：已支援「填過某問卷」＋「答案條件」查詢，`search(SearchRequest)` 併帶 `consentStatus` 過濾。
- `InviteService`：目標名單批次寄送前例——`email_log` 冪等（已寄跳過）、`limit` 額度、逐封 try-catch 不中斷、結果摘要 record。
- `MailTemplate`／品牌信件外框、`SubscriptionLinkBuilder`（退訂連結唯一擁有者）、工商卡 email-safe 版型（單格 table＋inline style）。

## 4. 資料模型（V22 migration）

```sql
CREATE TABLE coupon_campaign (
    id            BIGSERIAL PRIMARY KEY,
    course_name   VARCHAR(150) NOT NULL,
    pitch         TEXT NOT NULL,
    course_url    VARCHAR(1000) NOT NULL,
    coupon_code   VARCHAR(100) NOT NULL,
    expires_at    DATE,
    form_key      VARCHAR(100) NOT NULL,
    answer_filter JSONB NOT NULL DEFAULT '{}'::jsonb,
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    sent_at       TIMESTAMPTZ,
    sent_count    INT NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_coupon_status CHECK (status IN ('DRAFT', 'SENT')),
    CONSTRAINT ck_coupon_course_url CHECK (course_url LIKE 'https://%')
);
```

- 防重寄不建新表：`email_log.type` 存 `coupon:{campaignId}`。
- `answer_filter` 為建立時的條件快照；補寄時以快照重查名單，確保口徑一致。
- DB CHECK 與應用層驗證**兩層同步**（2026-08-03 mailto 案的教訓：改連結規則必查 CHECK）。

## 5. API

全部掛 admin 保護（X-Admin-Key＋guard.verify，既有慣例）：

| 端點 | 行為 |
| --- | --- |
| `POST /api/admin/coupons` | 建活動；驗證：必填、course_url 限 `https://`、期限格式 |
| `GET /api/admin/coupons` | 活動列表（含狀態、sent_count、sent_at） |
| `POST /api/admin/coupons/{id}/preview-recipients` | 以活動快照條件跑 SurveyFilter（固定 `consentStatus=CONFIRMED`），回命中清單：email、稱呼、填答日、關鍵答案摘要、`alreadySent` 旗標 |
| `POST /api/admin/coupons/{id}/send` body `{emails, limit}` | 寄送（見 §6） |

## 6. 寄送流程與防線

1. **子集驗證（注入防線）**：送來的 emails 必須全部屬於「該活動快照條件的命中集合」，任一不合法回 400 並列出違規 email——防任意地址被塞進行銷名單（admin API 也不豁免，縱深防禦）。
2. **合規防線**：命中集合本身固定 `consentStatus=CONFIRMED`（填過問卷但未確認訂閱者不進名單）；信尾含寄送原因（「你收到這封信是因為你填過問卷『○○』」）與退訂連結（`SubscriptionLinkBuilder`）。
3. **冪等**：逐封查 `email_log`（type=`coupon:{id}`）已寄跳過；limit 截斷（超出部分計入 remaining）。
4. **容錯**：逐封 try-catch，單封失敗不中斷批次（同 InviteService）；結果摘要 `{attempted, sent, skipped, failed, remaining}`。
5. 活動狀態：首次寄送成功後標 `SENT`＋`sent_at`；`sent_count` 累加；SENT 活動仍可補寄（冪等保證不重複）。

## 7. 信件版型（CouponMailRenderer）

固定欄位套 email-safe 卡（單格 table＋inline style，Outlook 相容；琥珀色系 `#fef3c7`／左條 `#d97706`，與工商綠卡、問卷藍卡區隔）：

品牌頭 → 課程名稱（H2）→ 文案段落 → **優惠碼大字虛線框**（等寬字體）→ 「前往課程」按鈕（course_url 直連）→ 期限行（`expires_at` 有值才顯示「優惠至 YYYY-MM-DD」）→ footer（寄送原因＋退訂連結）。

動態值（課程名、文案、優惠碼）一律 HTML 跳脫；渲染器放 `mail` 套件（與 MailSender／MailTemplate 同套件，不觸及 newsletter↛reader 依賴規則）。

## 8. Admin UI（新「優惠券」分頁）

1. **建立表單**：五欄位＋即時信件預覽（iframe srcdoc，沿用既有預覽慣例）。
2. **名單區**：問卷下拉（列有填答資料的問卷）＋答案條件（選欄位→選選項值）→「查詢名單」→ 命中清單表格（預設全選、可逐人取消、`alreadySent` 標記灰顯、即時顯示勾選人數）。
3. **寄送**：確認框含最終人數與活動名 → 呼叫 send → 顯示結果摘要。
4. **活動列表**：狀態 pill、寄送數、時間；點入可補寄（重跑名單，已寄自動跳過）。
5. 動態值進 DOM 一律 `textContent`（既有 XSS 慣例）。

## 9. 錯誤處理

| 情境 | 行為 |
| --- | --- |
| course_url 非 https | 400（應用層）＋DB CHECK 兜底 |
| 名單為空／全部已寄 | 擋寄送，明確訊息 |
| 子集驗證失敗 | 400＋列出違規 email |
| 單封寄送失敗 | 記 failed 續寄，不中斷 |
| 活動不存在 | 404 |

## 10. 測試策略（全程 TDD）

- 單元：子集驗證（合法／混入外部 email）、冪等跳過、limit 截斷、consent 過濾進查詢參數、course_url 驗證、renderer 跳脫與期限有無分支。
- 整合：V22 跑真實 Flyway＋`ddl-auto=validate`（5433 PG）。
- E2E：`preview-coupon-mail.mjs`（信件卡雙視口截圖）＋`verify-admin.mjs` 新段落（建活動→查名單→勾選→寄送 dry 斷言——不實際寄送，斷言到確認框為止）。

## 11. 範圍外（本輪不做）

- 每人唯一優惠碼（碼池匯入／分配）。
- 點擊追蹤與 CTR 統計（D5）。
- 排程寄送（本輪即時寄送）、寄送前測試信。
- 以「投過信中一鍵投票但未完整填答」者為對象（本輪名單來源限完整填答資料）。
