# 讀者頁側欄與投票發點系統設計

- 日期：2026-08-05
- 範圍：`survey-backend`（讀者頁 `article.html`／`reader.css`、後台編輯器 `admin.html`、`form`／`newsletter`／`reader` 套件、migration V23）
- 前置：[2026-08-03 電子報問卷整合系統設計](2026-08-03-newsletter-survey-integration-design.md)（本文所有問卷相關改動都建立在該份的資料模型與三通道渲染之上）

## 1. 目標

三項彼此獨立、可分別驗收的改動：

1. **文章頁右側欄**：進入單篇文章後，右側出現「相關文章」與「分類選單」，形成類 blog 的閱讀動線（目前文章讀完只有一條「回到歷史內容」）。
2. **發送頁快建問卷投票**：管理員可在電子報編輯器直接輸入題目與選項建立可嵌入的投票，不必先到「動態表單」分頁走完四步設定。
3. **投票發點與提示**：信中／頁面一鍵投票即發點數，並在問卷卡題目上方明示可得點數。

### 1.1 現況查證（改動的前提）

- 「內嵌問題可直接在 email 中點擊」與「去重」**已實作完成**，本輪不重做：`SurveyBlockRenderer.expandForEmail` 展開成 email-safe `<table>` + inline style 的 `<a>` 按鈕，指向 `/s/v/{formKey}`；去重靠 V21 的 `uq_survey_vote_identity` partial unique index（具名身分一人一票，重投視為改票 upsert；匿名不受限）。
- **但點數目前只發給完整填答**：`credit_txn.reason='SURVEY_REWARD'` 僅由 `NewsletterSubmissionService.grantRewardIfEligible` 觸發。因此「投票後可增加點數」這句提示在現況下是假訊息——本輪必須連發點機制一起做，否則不能寫這句話。
- **「難設」的根因**是四步流程加上代號輸入：建立 form → 加 select 欄位 → 發布 → 指定信中一鍵題，然後回編輯器用 `prompt()` 輸入清單編號（`admin.html` 的 `insertSurvey()`）。

## 2. 決策記錄

| # | 決策 | 理由 |
|---|---|---|
| D1 | 相關文章＝同 hashtag 交集優先，不足補最新 | 純 SQL 可做、零新表，效果最接近 blog 的相關文章；「只列最新」相關性太弱，「後台手動指定」每篇都要人工維護 |
| D2 | 分類選單沿用現有 hashtag（`content_tag`／`campaign_tag`） | 零 migration，且與 `/r/archive` 篩選列、文章頁標籤三處同一份資料，不會出現兩套分類語彙 |
| D3 | 快建問卷走既有 form 機制，不另建輕量投票 | 沿用現成的統計、analytics、去重與發點；「活動內輕量投票」等於開第二套並行系統，投票資料拿不到既有能力 |
| D4 | `formKey` 由後端自動生成，管理員不填代號 | 中文標題無法可靠轉 slug，要求管理員自創代號正是「難設」的一部分 |
| D5 | 一鍵投票即發點，與完整填答發點為兩個獨立 reason | 提示文字才會是真的；兩者獨立防重發，投票 5 點、填完整問卷 20 點，各拿一次 |
| D6 | 問卷卡點數提示透過根套件介面取值，不注入 `CreditPolicy` | `PackageDependencyTest.newsletterMustNotDependOnReader` 禁止 `newsletter` import `reader`；依賴反轉讓 `CreditPolicy` 仍是點數唯一來源（下限保護不會漏） |
| D7 | 側欄只輸出標題、日期、封面，不放摘要 | 摘要需跑 `ContentSplitter`，多一條可能漏出受限區的路徑；側欄寬度也放不下摘要 |
| D8 | 接續頁「已投票」橫幅實查帳本，不用 URL 參數帶點數 | URL 參數可偽造，會出現「頁面說已獲得 N 點、帳本沒有」的落差，正是 `CreditPolicy` 這一層存在要避免的事 |

## 3. 功能一：文章頁右側欄

### 3.1 版面

`templates/reader/article.html` 改為兩欄結構，主欄內容順序與現況完全一致：

```
<div class="wrap article-wrap">
  <div class="article-layout">
    <div class="article-main">
      <article>…</article>
      GATE_BLOCK / SUBSCRIBE_CTA / 分享卡 / completion-share / back-link
    </div>
    <aside class="article-side"><!--ARTICLE_SIDEBAR--></aside>
  </div>
</div>
```

`reader.css`：

- `.article-wrap { width:min(100% - 36px, 1120px); }` —— **只加在文章頁**，`.wrap` 的 760px 對其他讀者頁（archive／me／rules／survey／login）保持不動。
- `.article-layout { display:grid; grid-template-columns:minmax(0,1fr) 300px; gap:32px; align-items:start; }`
- `.article-side { position:sticky; top:24px; }`
- `@media (max-width:960px)`：單欄，側欄排在主欄之後。

`/r/archive` **不加側欄**：該頁已有 hashtag 篩選列與文章格線，再放一份分類選單會是同一份資料的第二套 UI。

### 3.2 相關文章查詢

新增 `world.springai.survey.newsletter.PublicRelatedArticleService`（與 `PublicCampaignTagService` 同套件、同「公開文章唯讀查詢」職責，`reader → newsletter` 是既有授權方向）。

```java
/** 側欄用的精簡文章描述；刻意不含摘要（見 D7） */
public record RelatedArticle(String slug, String subject, OffsetDateTime publishedAt,
                             Long coverMediaId, String coverEmoji) {}

public List<RelatedArticle> relatedTo(long campaignId, int limit)
```

兩段式，總數上限 `limit`（預設 5）：

1. **同標籤交集**：`campaign c JOIN campaign_tag ct ON ct.campaign_id = c.id`，條件 `ct.tag_id IN (本篇 tag ids)`、`c.id <> 本篇`、`c.slug IS NOT NULL`、`c.published_at IS NOT NULL`；`GROUP BY c.id ORDER BY count(*) DESC, c.published_at DESC LIMIT ?`。
2. **補齊**：第 1 段不足 `limit` 時，查最新已發布文章補足，排除本篇與第 1 段已入選的 id。

本篇沒有任何標籤時，第 1 段自然回空集合，全部由第 2 段補（不需特例分支，但要有測試釘住）。

### 3.3 側欄渲染

`ReaderPageController` 新增 `<!--ARTICLE_SIDEBAR-->` 佔位符與 `renderSidebar(campaign)`，輸出兩張卡：

- **分類**：`campaignTagService.publicTags()`（名稱＋已發布篇數），連結 `/r/archive?tag={slug}`；本篇所屬分類加 `active` 樣式。
- **相關文章**：`relatedTo()` 結果，每列為封面（`mediaAssetService.publicUrls()` 批次取，無圖時用 `coverEmoji`，預設 `📝`）＋標題＋日期，連結 `/r/news/{slug}`。

降級行為比照既有寫法：`campaignTagService`／`mediaAssetService` 為 null（舊單元測試相容建構式）時該卡輸出空字串，不拋例外。相關文章為空時整張卡不輸出。

回應標頭維持 `Cache-Control: private, no-store` 與 `Vary: Cookie`，不因側欄改變。

## 4. 功能二：發送頁快建問卷投票

### 4.1 服務層

`FormSchemaService` 新增：

```java
public record QuickVoteRequest(String title, String label, List<String> options) {}

@Transactional
public EmailVoteQuestion createQuickVoteForm(QuickVoteRequest request)
```

單一交易內依序呼叫既有方法，不繞過任何現有驗證：

1. `formKey` 自動生成：`vote-{yyyyMMdd}-{4 碼小寫英數}`，符合既有 `[a-z0-9-]{3,50}` 規則；碰撞時重試（上限 5 次）後拋 409。
2. `createForm(formKey, title)`
3. `addField(formKey, 1, "vote", FieldRequest(label, "select", …, options))` —— `analyticsEnabled=true`、`publicAnalytics=false`、`sensitive=false`、`required=false`，與手動建立的預設一致。
4. `publish(formKey, 1)`
5. `updateEmailVoteField(formKey, 1, "vote")`

驗證（在寫入前，交易不留半套資料）：標題必填；選項去空白後需 2–6 個且互不重複；選項字數上限 40。違反一律 400 並附具體原因。

### 4.2 端點

`FormSchemaController` 新增 `POST /api/admin/forms/quick-vote`，受既有 `AdminKeyGuard` 保護，回傳 `EmailVoteQuestion`（前端需要 `formKey` 組標記）。

### 4.3 後台 UI

`admin.html` 編輯器工具列：現有 `#insert-survey-btn` 改為開啟一個 `<details>` 面板（沿用既有 `media-panel` 樣式），內容：

- **快速建立**：題目、題目說明、2–6 列選項輸入（可增／減列），一顆「建立並插入」。
- **插入現有問卷**：`GET /api/admin/forms/embeddable` 結果的正式 `<select>` 加「插入」鈕，取代目前 `prompt()` 輸入編號。

兩條路徑都走既有 `markdownBlock()` 插入獨立一行的 `<!--survey:{formKey}-->`，然後 `composeChanged()` 觸發預覽更新——標記必須整行單獨存在，`SurveyBlockRenderer` 才比對得到。

## 5. 功能三：投票發點與提示

### 5.1 資料模型（migration V23）

```sql
-- 一鍵投票發點：每人每問卷一次（比照 uq_credit_txn_survey_reward 的形狀）
CREATE UNIQUE INDEX uq_credit_txn_survey_vote_reward
    ON credit_txn (reader_id, survey_form_key)
    WHERE reason = 'SURVEY_VOTE_REWARD';
```

沿用 V21 已加的 `credit_txn.survey_form_key` 欄位，不新增欄位。`credit_txn.reason` 沒有 CHECK 約束（已查證），新 reason 只需 Java 常數。

### 5.2 參數與常數

- `CreditTxn.REASON_SURVEY_VOTE_REWARD = "SURVEY_VOTE_REWARD"`
- `AppSettingService.CREDIT_SURVEY_VOTE_REWARD = "credit.survey_vote_reward"`
- `CreditPolicy.surveyVoteReward()`：後備值 5，負值夾到 0（0 為合法的「關閉投票發點」設定，比照 `signupGrant()`）。
- `AdminSettingController`：新鍵同時加入 `ADJUSTABLE`（`Bound(0, CREDIT_MAX)`）、`DISPLAY_DEFAULTS`（5）、`ordered()`；`admin.html` 的 LABELS 表加對應中文說明。**四處被 `AdminSettingControllerTest` 互相釘住，漏一處即紅燈。**

> 附帶落差記錄：既有的 `credit.survey_reward`（完整填答獎勵）目前不在 `ADJUSTABLE`／`DISPLAY_DEFAULTS` 中，後台無法調整、只吃後備值 20。本輪不順手修（屬既有範圍外的行為變更），但列在此供後續決定。

### 5.3 發點路徑

新增 `world.springai.survey.form.SurveyVoteRewardService`（與 `NewsletterSubmissionService` 同套件、同「form 套件內處理發點」的既有先例）：

```java
@Transactional
public Optional<Integer> grantIfEligible(String formKey, String identityType,
                                         String identityKey, Long campaignId)
```

- 身分對映：`RECIPIENT` → email 反查 reader；`READER` → `identityKey` 即 readerId；`ANON` → 不發點。
- 非註冊讀者照計票、不發點（比照 `NewsletterSubmissionService` 對非讀者的處理）。
- 已存在同 `(readerId, formKey, REASON_SURVEY_VOTE_REWARD)` 帳列 → 不發（**改票不重發**；`existsBy` 先擋，unique index 兜底防併發）。
- `credit_txn` 與 `reader.credits` 同交易；`readerRepository.addCredits()` 回 0 列一律拋例外回滾（比照 `ReferralGrowthService.addCredit`），不容許帳本與 `reader.credits` 對不起來。
- 帳本 note：`完成投票「{問卷標題}」獎勵`（比照 `SURVEY_REWARD` 的 note 慣例，讓明細看得出來由）。

`SurveyVoteService.recordVote()` 在落票成功後呼叫它。發點失敗只寫 log、不擋轉址，沿用該類既有的 best-effort 哲學——投票統計與發點都是輔助，讀者順利跳轉到接續頁才是主體驗。

### 5.4 提示文字（三通道）

根套件新增介面（`ReaderSiteLinks` 是「根套件共用視圖」的既有先例）：

```java
package world.springai.survey;
/** 問卷卡片顯示投票獎勵所需的唯一取值來源；由 reader 套件的 CreditPolicy 實作 */
public interface SurveyVoteRewardView { int surveyVoteReward(); }
```

`CreditPolicy implements SurveyVoteRewardView`。`SurveyBlockRenderer` 注入介面，在卡片題目**上方**輸出提示列（email-safe inline style，與卡片其他元素同風格）。文案依通道與登入狀態分歧，避免對拿不到點的人說謊：

| 通道 | 文案 |
|---|---|
| 信件（`expandForEmail`） | 🎁 投票即可獲得 {N} 點（限已註冊讀者，每份問卷一次） |
| 讀者頁・已登入 | 🎁 投票即可獲得 {N} 點（每份問卷一次） |
| 讀者頁・未登入 | 🎁 登入後投票可獲得 {N} 點 |
| 後台預覽（`expandForPreview`） | 同信件版；另保留既有「預覽不計票」標示 |

`expandForWeb(html, campaignId)` 增加 `loggedIn` 參數，由 `ReaderPageController` 傳入（該處已解析登入狀態）。`surveyVoteReward()` 為 0（後台關閉投票發點）時整列提示不輸出。

### 5.5 接續填答頁

`SurveyPortalController.votedBanner()` 改為實查帳本後顯示真實狀態（見 D8），三種結果：

- 已因本次投票發點：`✅ 已收到投票，並發送 {N} 點`
- 先前已發過：`✅ 已收到投票（此問卷的投票點數先前已發送）`
- 未歸戶到讀者帳號：`✅ 已收到投票，訂閱成為讀者即可獲得投票點數`

身分解析沿用該類既有的 `resolveIdentity`（rt → session），不新增身分來源。

## 6. 測試策略（全程 TDD）

每項功能都先寫失敗測試再實作。

| 測試 | 釘住的行為 |
|---|---|
| `PublicRelatedArticleServiceTest` | 同標籤優先排序；不足補最新；排除本篇與未發布；本篇無標籤時全走補齊；`limit` 生效 |
| `ReaderPageControllerTest`（擴充） | 文章頁輸出側欄；分類連結指向 `/r/archive?tag=`；相關文章為空時不輸出該卡；側欄不含受限區內容 |
| `ReaderStylesheetTest`（擴充） | `.article-wrap`／`.article-layout` 存在，且 `.wrap` 的 760px 未被改動 |
| `FormSchemaServiceQuickVoteTest` | 一次呼叫後即 PUBLISHED 且 `emailVoteFieldKey` 已綁定；選項 <2 或 >6、重複選項、空標題皆 400；`formKey` 符合格式 |
| `SurveyVoteRewardServiceTest` | RECIPIENT／READER 發點；ANON 不發；非註冊讀者不發；重複投票不重發；`addCredits` 回 0 時拋例外回滾 |
| `SurveyVoteRewardConstraintTest` | V23 unique index 真的擋下同 reader 同問卷的第二筆（DB 層級，比照 `SurveyRewardConstraintTest`） |
| `SurveyBlockRendererTest`（擴充） | 四種通道／狀態的提示文案；`surveyVoteReward()` 為 0 時不輸出提示 |
| `AdminSettingControllerTest`（既有） | 新設定鍵四處同步（此測試已存在，加鍵後必須維持綠燈） |
| `SurveyPortalControllerTest`（擴充） | 三種橫幅狀態依帳本查詢結果而非 URL 參數 |
| `PackageDependencyTest`（既有） | `newsletter` 仍未 import `reader`（D6 的守衛） |

驗證指令：`JAVA_HOME=/d/java/jdk-21 mvn test`（預設 shell 的 `JAVA_HOME` 是 JDK 8，會把錯誤誤報成編碼／檔案損壞問題）。

## 7. 範圍外（本輪不做）

- `/r/archive` 加側欄（見 §3.1 理由）。
- 相關文章的手動指定與權重調整後台。
- 把既有 `credit.survey_reward` 補進後台可調參數（見 §5.2 附註）。
- 信件內直接完成整份問卷（現況仍是一鍵投票後導到接續頁填答）。
- 側欄的熱門文章／點閱排行（需要 `reader_funnel` 事件聚合，屬另一個題目）。
