# 信箱驗證 CTA 與推薦獎勵補發設計

**日期**：2026-08-08
**範圍**：`survey-backend`（`audience` / `newsletter` / `reader` 套件 + `admin.html`）
**Flyway**：不需要新 migration（現行最高 V26 保持不動）

---

## 1. 背景與問題診斷

後台成長分頁的分享漏斗長期顯示「信箱確認 0」「審核通過 0」，「填表→確認」為 0%。查證後確認**這不是統計錯誤，而是流程上根本沒有任何一段會產生「信箱確認」事件**。

### 1.1 指標的實際來源

漏斗第三層來自 `AdminReferralGrowthController`：

```sql
select count(*) from referral_conversion where confirmed_at is not null
```

`confirmed_at` 只有一條寫入路徑：

```text
讀者點 /subscription/confirm?email=&t=
  → SubscriptionController.confirm
  → 發布 SubscriptionConfirmedEvent
  → ReferralRewardListener.onSubscriptionConfirmed
  → ReferralGrowthService.confirmAndReward
  → conversion.confirm(...)  ← 寫入 confirmed_at
```

### 1.2 三個結構性斷點

| # | 斷點 | 位置 |
| --- | --- | --- |
| 1 | reader 首頁訂閱是 single opt-in，送出時 `consent: true`，沒有待確認狀態 | `templates/reader/index.html` |
| 2 | 歡迎信內文只有退訂連結，**沒有確認連結**，填表者從未收到任何可確認的東西 | `WelcomeMailService.buildHtml` |
| 3 | 唯一產生確認連結的 `InviteService` 只撈 `consent = false` 的名單，而 reader 訂閱者 `consent` 一開始就是 `true`，永遠不會入選 | `InviteService.sendInvites` |

### 1.3 附帶影響：推薦獎勵從未發放

`confirmAndReward` 是**唯一**的發獎入口，且只由 `SubscriptionConfirmedEvent` 觸發。既然沒有人點過確認連結：

- 帶推薦碼的訂閱，推薦人與被邀者的點數都沒發
- `referral_conversion` 沒有任何列，後台待審清單恆空
- 里程碑徽章（`MILESTONES`）從未觸發

---

## 2. 決策記錄

| 編號 | 決策 | 理由 |
| --- | --- | --- |
| D1 | 補發重用 `confirmed_at`，同時修正漏斗標籤 | 重用已測試的冪等路徑、零 migration。第三層改名「轉換成立」，另新增獨立的「信箱確認」KPI 改從 `audience_consent` 計數，讓 A/D 的效果有乾淨的量尺 |
| D2 | 驗證 CTA 不發點數獎勵 | 不新增點數科目、冪等鍵與 AppSetting。先觀察確認率，不夠再加 |
| D3 | 統計放成長分頁，新增「訂閱者邀請成效」表 | 讀者管理列表已有多欄，加欄會擠壓版面且多數讀者該欄為 0 |
| D4 | 補發**直接核准**，略過風控 | 這 16 筆是歷史累積而非短時間刷單。若照跑速度規則（同一推薦人 10 分鐘內 ≥ 3 筆），補發是連續執行、`confirmed_at` 都是 now，任何帶 3 人以上的推薦人會全數落入 `PENDING_REVIEW` |
| D5 | 補發的轉換時點採該筆 `survey_response.created_at` | `campaignMultiplier(sourceSlug, now)` 用「現在」算倍率，補發歷史轉換會套到今天的活動倍率而非當時的。錨在真實提交時間，`confirmed_at` 與倍率都誠實 |
| D6 | 補寄確認信獨立為 `ReconfirmService`，不擴充 `InviteService` | `InviteService` 名單口徑是 `consent = false`，本需求是 `consent = true` 且未確認 —— 條件相反。該檔已有兩個高度相似方法，第三個語意會讓它更難讀 |
| D7 | A 與 D 的文案都不提推薦獎勵 | 兩者都是全體收件，多數人沒有推薦人；要按有無 `_ref` 分支就得多一套邏輯與測試。統一訴求「信箱可達性」對每個收件人都成立 |
| D8 | D 的文案採中性語氣，不主動說明不點確認的後果 | 保留點擊動力。同時不得暗示會被取消訂閱（那是欺騙） |
| D9 | 補寄信範本程式內建，不入 `mail_template` | YAGNI。之後要後台編輯再走 `getTemplate()` 的「DB 優先 + 程式退路」模式 |

---

## 3. 前置查證（決定方案可行性的關鍵事實）

這三項若判斷錯誤，整個方案會靜默失效或違反既有保護：

1. **`confirmByEmail` 沒有 `and consent = false` 條件**
   `SurveyResponseRepository:85` 的 JPQL 是 `update SurveyResponse s set s.consent = true where lower(s.email) = lower(:email)`。所以已 `consent = true` 的 reader 訂閱者點確認連結時 `affected > 0` 仍成立，事件會照發。若那裡有加條件，A 與 D 都會完全無效。

2. **`WelcomeMailService` 已注入 `SubscriptionLinkBuilder`**（為退訂連結所需），A 不需要改建構子。

3. **補寄確認信屬行銷側，必須受額度 reserve 約束**
   `MailQuotaService:82-85` 明確定義：「確認信」指使用者送出訂閱後系統自動寄的那封（交易信，不受限）；**後台主動群發的再徵詢屬行銷側**。`AdminCampaignController.clampLimit()` 已是完整正確的實作（用 `marketingBatchMax()` 而非 `batchMax()`、可用量為 0 時拋 409 而非回 0），D 直接複用。

---

## 4. 設計

### 4.1 A — 歡迎信加信箱驗證 CTA

`WelcomeMailService.sendWelcome` 額外取 `linkBuilder.confirmLink(email)`，`buildHtml` 改收兩個連結，內文加一顆確認按鈕。

- **不分 `source`**：課程問卷與電子報訂閱都放，避免分支邏輯；確認對兩者都有意義（`consent` 軌跡、`touchEngagement` 參與度訊號、推薦發獎）
- **不發點數**（D2）、**不提推薦獎勵**（D7）
- 文案訴求：確認一下信箱，確保之後的信都收得到

**效果**：把系統從 single opt-in 升級為 soft double opt-in —— 訂閱立即生效（不損失轉換），但確認過的人成為可辨識的高品質子集。

### 4.2 B — 推薦獎勵補發

**端點**：`POST /api/admin/referrals/backfill?dryRun={true|false}`（`AdminReferralGrowthController`，經 `AdminKeyGuard`）

**掃描口徑**：

```sql
select lower(sr.email) as email, min(sr.created_at) as occurred_at
  from survey_response sr
  join reader r on r.referral_code = (sr.answers ->> '_ref')
 where sr.answers ? '_ref'
   and sr.consent = true
   and sr.unsubscribed = false
 group by lower(sr.email)
```

`consent = true and unsubscribed = false` 是必要的守門：只對真正成立且未退訂的訂閱發獎。`group by lower(email)` 是因為同一 email 可能有多筆問卷（已在正式資料中實測到有人相隔一個月填了兩次），取 `min(created_at)` 作為該人的轉換時點（D5）。

**服務層**：`ReferralGrowthService` 新增

```java
/** 僅供 admin 補發：直接核准、略過風控，轉換時點由呼叫端指定。 */
@Transactional
public Outcome backfillAndApprove(String inviteeEmail, OffsetDateTime occurredAt)
```

**實作方式**：把 `confirmAndReward` 現有的主體抽成私有方法 `settle(invitee, occurredAt, boolean assessRisk)`，兩個公開方法各自呼叫它。**`confirmAndReward` 的對外行為完全不變**（仍傳 `now()` 與 `assessRisk = true`），由既有的 `ReferralGrowthServiceTest` / `ReferralIdempotencyTest` 保證這一點 —— 若那些測試變紅，就是抽取動作改變了公開路徑，必須修正而非調整測試。

**維持兩個公開方法，而非給 `confirmAndReward` 加 `skipRisk` 參數**：後者讓公開端點的呼叫鏈有機會傳錯值而繞過風控。`backfillAndApprove` 這個名字本身標示它是特權路徑，且唯一呼叫點在 `AdminKeyGuard` 之後。

與公開路徑的兩個差異：

- `status` 直接為 `APPROVED`，不呼叫 `assessRisk`（D4）
- `confirmed_at` 與 `campaignMultiplier` 都用傳入的 `occurredAt`，不是 `now()`（D5）

**冪等**：完全依賴既有的 `referral_conversion` 唯一鍵與 `uq_credit_txn_referral_note`。重跑安全，第二次全部回 `ALREADY_PROCESSED`。

**容錯**：逐筆 try/catch 不中斷整批。回傳各 Outcome 計數 `{scanned, rewarded, alreadyProcessed, selfInvite, noReferrer, failed}`。`dryRun=true` 只回掃描名單（遮罩 email）與筆數，不寫入。

### 4.3 C — 統計與漏斗語意修正

三項都併入既有 `GET /api/admin/referrals/dashboard` 回應，維持「一次回傳減少往返」的設計，不新增端點。

**① 漏斗第三層改名**：「信箱確認」→「轉換成立」。資料源不變（`confirmed_at`），KPI 的 `submitToConfirmRate` 語意跟著改為「填表→成立」。

**② 新增真實信箱確認 KPI**：

```sql
select count(distinct p.id)
  from audience_person p
  join audience_consent c on c.person_id = p.id
 where c.channel = 'EMAIL'
   and c.status = 'CONFIRMED'
   and c.source_key = 'confirmation-link'
```

這是 A 與 D 的效果量尺 —— 只有真的點過確認連結的人會被 `appendConsentByEmail(..., "confirmation-link", ...)` 記進來。`channel = 'EMAIL'` 目前是唯一在用的管道，明確寫出來是為了未來新增管道時這個數字不會無聲地把別的管道算進來。回應欄位名 `confirmedByLink`。

**③ 新增「訂閱者邀請成效」表**（回應欄位名 `referrerStats`）：以 `reader` 為主，聚合現有來源：

| 欄位 | 來源 |
| --- | --- |
| 推薦人（遮罩） | `reader.email` 經 `ReferralGrowthService.maskEmail()` |
| 分享點擊 | `referral_click` group by `referrer_id` |
| 完成填表 | `survey_response.answers ->> '_ref'` join `reader.referral_code` |
| 轉換成立 | `referral_conversion` where `confirmed_at is not null` group by `referrer_id` |
| 已發點數 | `referral_conversion.referrer_reward` 加總（status = `APPROVED`） |
| 里程碑 | `referral_badge` count group by `reader_id` |

只列有活動者（點擊 / 填表 / 成立任一 > 0），`order by 成立 desc, 點擊 desc limit 50`。

**④ admin UI**：成長分頁底部新增該表；漏斗與 KPI 標籤同步更新。

### 4.4 D — 補寄確認信給既有訂閱者

**服務**：新增 `newsletter.ReconfirmService`（D6）。該套件依賴 `audience` 是既有且合法的方向（`InviteService` 已如此）。

**名單口徑**：

```sql
consent = true AND unsubscribed = false
  AND NOT EXISTS (audience_consent 有 status='CONFIRMED' AND source_key='confirmation-link')
  AND NOT EXISTS (email_log 有 type='reconfirm' AND status='sent')
```

第二個條件避免騷擾已確認者，第三個條件讓每人終身只補寄一次（沿用 `InviteService` 的 `invited` set 模式）。

**端點**：`POST /api/admin/campaign/reconfirm`，body `{limit}`，複用 `clampLimit()`（前置查證 3）。回傳 `{recipientCount, accepted, failed, alreadySent, alreadyConfirmed, remaining}`。

**信件內容**（主旨：`請確認你的訂閱信箱｜AI 賦能全端開發`）四段，語氣中性（D8）：

1. **你為什麼收到這封信** —— 你先前訂閱了這份電子報，這不是重新徵求同意。
2. **為什麼請你確認** —— 確認過的信箱我們才能確保信件確實送達；未確認的地址無法分辨「收到但沒打開」與「信根本沒送達」，打錯字的地址、已停用的信箱、被歸進垃圾信匣的，在數據上長得一模一樣。
3. **你需要做的** —— 點一下按鈕，只需幾秒。
4. **不想再收** —— 底部退訂連結（`EmailTemplate.wrap` 既有頁腳）。

不主動說明不點的後果，也不得暗示會被取消訂閱。內文以 `{{confirmLink}}` 佔位，寄送時逐封替換為個人化 HMAC 連結；整批共用一次範本讀取，不在迴圈內重複取得。

**admin UI**：邀請分頁「寄送邀請信／補送提醒」旁新增第三顆「補寄確認信」，並顯示待補寄人數。

### 4.5 執行順序

順序為 B（補發）→ 部署 A（歡迎信 CTA）→ D（補寄）。

D 放最後：D 寄出後湧入的確認會走與 A 相同的端點，此時 B 已把歷史帳清完，新確認產生的轉換都是即時且乾淨的資料，不會與補發混在一起難以歸因。

---

## 5. 測試策略

| 目標 | 測試 |
| --- | --- |
| A | `WelcomeMailServiceTest`：寄出的 HTML 含確認連結、不殘留佔位符、退訂連結仍在 |
| B 冪等 | 連跑兩次 `backfillAndApprove`，點數不加倍、第二次回 `ALREADY_PROCESSED` |
| B 口徑 | `consent = false` 不入選、`unsubscribed = true` 不入選、自我邀請回 `SELF_INVITE` |
| B 略過風控 | 同一推薦人連續補發 3 筆以上，`status` 全為 `APPROVED`（不是 `PENDING_REVIEW`） |
| B 時點 | `confirmed_at` 等於傳入的 `occurredAt`，不是 `now()` |
| B 公開路徑未受影響 | 既有 `confirmAndReward` 的風控測試全綠（速度規則仍會觸發 `PENDING_REVIEW`） |
| C | dashboard 回應含 `referrerStats` 與新的確認數欄位；`verify-growth-funnel.mjs` 更新第三層標籤斷言與新表斷言 |
| D 名單 | 已確認者不入選、已寄過者不入選、退訂者不入選 |
| D 冪等 | 連跑兩次，第二次 `recipientCount = 0`、`alreadySent` 等於第一次的 `accepted` |
| D 額度 | `marketingBatchMax = 0` 時端點回 409；`limit` 超過上限時被收斂 |

`survey-backend` 測試需 `JAVA_HOME=D:/java/jdk-21`（系統預設 java 是 1.8，會編譯失敗）。

---

## 6. 不做（YAGNI）

- 驗證獎勵點數科目（D2）
- 補寄信範本的後台編輯 UI（D9）
- reader 端自助「重寄驗證信」按鈕
- 把 reader 訂閱改成強制 double opt-in（會損失訂閱轉換，A 的 soft 版本已足夠）
- 依有無 `_ref` 分支的個人化文案（D7）

---

## 7. 驗收標準

1. 新訂閱者收到的歡迎信含可點擊的確認連結，點擊後 `audience_consent` 出現 `source_key = 'confirmation-link'` 的 `CONFIRMED` 列
2. 補發以 `dryRun=true` 先列出名單，正式執行後 `referral_conversion` 出現對應列且全為 `APPROVED`，推薦人與被邀者的 `credit_txn` 各自入帳；重跑不加倍
3. 成長分頁漏斗第三層顯示「轉換成立」，另有獨立的「信箱確認」KPI 反映真實點擊數
4. 成長分頁「訂閱者邀請成效」表列出有活動的推薦人，email 已遮罩
5. 補寄確認信對既有未確認訂閱者寄出一次，重按不重寄；行銷額度用盡時回 409
6. `mvn clean test` 全綠，`scripts/verify-growth-funnel.mjs` 通過
