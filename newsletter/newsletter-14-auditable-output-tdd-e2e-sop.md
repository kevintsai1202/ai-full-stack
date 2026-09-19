# 電子報第 014 期範本：開發驗收期——測試不是驗證工具，是規格文件（可審查產物系列・站 3）＋事件送得出去：交易邊界與 Outbox（EVENT 系列 E2）

> **用途**：一期兩主題。
> 主題一＝「可審查產物」系列站 3（開發驗收期），全系列份量最大的一站。改編自直播站 3：
> 「幫我測試」換成三層——TDD 紅綠重構、Playwright E2E 可重跑腳本、截圖自動生成 SOP。
> 主題二＝**EVENT 系列 E2**：事件在交易 commit 前送出的隱藏 bug、`@TransactionalEventListener`
> 與 Outbox 兩層解法，以及兩者各自擋不住什麼。
> **使用方式**：將下方「內文（Markdown）」複製到 admin 後台，先更新預覽並寄測試信，確認後再正式寄送。
>
> **「EVENT」系列鋪陳（012 起；設計見 `docs/superpowers/specs/2026-08-08-newsletter-event-series-design.md`）**：
>
> - 012 E0 地圖：解耦 ≠ 非同步 ／ 013 E1：`@Async` 的三個陷阱
> - 014（本期）E2：事件送得出去——交易邊界與 Outbox
> - 015 E3：事件收得下來——重複執行的代價
> - 016 E4（升格主題一）：進度、失敗與死信 ／ 017 E5：事件即 Agent 的稽核軌跡
>
> **「可審查產物」系列鋪陳（011 起，每期為當期兩主題之一）**：
>
> - 011 基礎篇：反差場景、核心主張、agentic coding、錯誤成本、四站路線圖
> - 012 站 1 想法期：「能不能做」→ 可行性評估報告
> - 013 站 2 設計期：「幫我寫一個 X」→ 選型分析＋開發計畫
> - 014（本期）站 3 開發驗收期：「幫我測試」→ TDD＋E2E＋截圖生 SOP
> - 015 站 4 上線前：「幫我看漏洞」→ OWASP／CWE 基準稽核＋四句型總表（系列完結）
>
> **前置條件**：013 結尾已預告本期，且本期開頭回收 013 的驗收標準伏筆——**必須在 013 之後寄出**。
> **素材來源**：`live-slides/assets/05-tdd-red.png`、`06-tdd-green.png`、`07-e2e-screenshots.png`、
> `08b-generated-sop-step06.png`，寄送前需上傳媒體庫並替換文中 TODO 圖片 URL。
>
> 文章中段以「工商時間」卡片（`<!--promo-->` 標記）宣傳 Hahow 課程，
> **優惠碼與期限需在寄送前確認後填入**。

---

## 建議主旨（三選一）

1. `AI 寫完程式再補的測試，是在證明它自己對——先紅燈，才算數`
2. `一次測試執行，兩份產物：回歸資產＋自動長出來的操作手冊`
3. `可審查產物系列（三）：TDD、E2E 與會自己更新的 SOP`

**推薦**：`AI 寫完程式再補的測試，是在證明它自己對——先紅燈，才算數`

## 預覽文字（preheader）

> 「幫我測試有沒有問題」拆成三層：開發時 TDD 先紅後綠、完工後 Playwright 可重跑腳本、跑完用截圖自動產出永遠同步的 SOP。

---

## 內文（Markdown，直接複製貼上）

```markdown
# 主題一｜開發驗收期：測試不是驗證工具，是規格文件——可審查產物系列・站 3

嗨，我是凱文大叔。

上期結尾埋了一個伏筆：設計期那批「可打勾」的驗收標準，會一模一樣地出現在測試名稱裡。這期兌現。第三站：**開發驗收期**——全系列份量最大的一站，因為它有三層。

## 一句話換成三層

❌「幫我測試程式有沒有問題」——這句話的問題是，它把測試當成一個**事後的動作**。

但測試不該是事後的。它應該從開發第一分鐘就開始，而且結束時要留下三樣東西：

1. **開發時就 TDD**：先寫測試 → 紅 → 實作到綠 → 重構
2. **完工後 Playwright E2E**：寫成可重跑的腳本檔
3. **E2E 每步截圖 → 用這批圖產出 SOP 操作手冊**

一層一層來。

## 3-1　開發時就 TDD：先讓我看到紅燈

為什麼要它「先寫測試」？因為 AI 寫完程式再補測試，它會寫出**證明自己對**的測試。測試變成實作的鏡子——你拿它驗證，等於拿它自己驗證它自己。

先寫測試，測試就是規格。

### 提示詞全文（可直接複製）

```text
用 TDD 實作【功能】：
1. 先寫測試，跑一次，讓我看到它是紅的（失敗訊息貼給我）
2. 再寫最小實作讓測試轉綠
3. 最後重構，重構後測試必須維持綠

測試要涵蓋：正常路徑、邊界值、錯誤分支。
在我確認紅燈之前，不要寫實作程式碼。
```

最後一句約束不能少：「在我確認紅燈之前，不要寫實作程式碼。」沒有這句，它會一口氣寫完，然後告訴你「測試都過了」——你既沒看到紅燈，也不知道測試到底驗了什麼。

紅燈長這樣：

![TDD 紅燈：七個測試失敗，失敗原因全部是 not implemented——為了對的理由失敗](https://springai-media.zeabur.app/newsletter-media/images/021d24c7463f01cc7889d3673ba09f95ea3c85e1ee9c244754529cd25107b70b.png)

注意失敗原因：`not implemented`——**是功能未實作，不是打錯字**。這件事要確認，否則你紅得沒有意義。紅燈證明測試真的在驗證（不是永遠會過），綠燈才證明實作滿足規格。

然後是綠燈——這是整場直播我最喜歡的一張：

![TDD 綠燈：21 個測試通過，測試名稱逐條描述系統規則——前置任務未完成顯示 BLOCKED、同優先級同時間 ID 小者優先、被阻塞任務不被建議](https://springai-media.zeabur.app/newsletter-media/images/7f02a774473f0dc810fc2b8dd0466021d45d8545197cf5f614d722ab97d6f4e3.png)

到這裡我**沒有給你們看過任何一行程式碼**。但你現在已經知道這個系統的所有規則了：前置任務未完成時，任務對外顯示為 BLOCKED；同優先級同建立時間時，ID 較小者優先；被阻塞的任務不會被建議。

這就是先寫測試的真正價值——**測試不是驗證工具，是規格文件**。而且對照上期：這些測試名稱，正是設計期那批驗收標準的一比一翻譯。

<!--promo-->
### 工商時間

TDD、E2E、自動化驗證——課程裡每個功能都是這樣長出來的，不是先寫完再祈禱。

**《AI 賦能全端開發：從零打造企業級智慧應用》**：用同一套 AI CRM 專案，從 Spring Boot 後端、React 前端到資料庫與權限，一路加上 Spring AI、RAG 與 AI Agent，完成真正能上線的企業級應用。

👉 [前往 Hahow 課程頁](https://hahow.in/cr/ai-full-stack)
<!--/promo-->

## 3-2　完工後 E2E：腳本是資產

單元測試驗的是規則，E2E 驗的是「使用者真的能走完流程」。這裡有一個很容易忽略的要求：

### 提示詞全文（可直接複製）

```text
用 Playwright 幫我寫 e2e 測試腳本，涵蓋這幾條使用者流程：
【流程 1】【流程 2】【流程 3】

要求：
- 寫成可重跑的腳本檔，放在 e2e/ 目錄下，加中文註解說明用途
- 不要用一次性的互動指令
- 每個腳本可獨立執行，失敗時輸出足以定位問題的訊息
```

重點是那兩句：「**寫成可重跑的腳本檔**」「**不要用一次性的互動指令**」。AI 很喜歡當場開瀏覽器幫你點一遍，然後跟你說「測過了，沒問題」——那個「測過」跑完就蒸發了，下次改了程式又要重來。

> 一次性指令跑完就沒了。腳本是資產。

之後每次改完程式，一行 `npx playwright test`，它自己開瀏覽器、自己填欄位、自己點按鈕、自己驗證結果。

## 3-3　最划算的一招：讓測試自己長出操作手冊

到這裡都還算正常。接下來這一招，是我覺得整場最划算的。

E2E 反正每一步都在操作畫面——那就順手把每一步截圖存下來：

![E2E 步驟截圖軌跡牆：一次執行留下的十張截圖，完整記錄操作流程](https://springai-media.zeabur.app/newsletter-media/images/da886aa77cc5e23605d35130ee1118fc4232a79b19611684a3d07daf63ac39e0.png)

然後：

### 提示詞全文（可直接複製）

```text
在 e2e 腳本的每個關鍵步驟後加上截圖，存到 e2e/screenshots/ 並依步驟編號命名。
跑完後，用這批截圖幫我產出一份操作 SOP 文件：
每個步驟一張圖 + 操作說明 + 預期結果 + 常見錯誤。
輸出成 Markdown。
```

同一次執行，兩份產物：**一份給機器看（回歸測試），一份給人看（SOP 操作手冊）**。

![自動生成的 SOP 文件頁面：步驟截圖搭配操作說明與預期結果，全文由測試執行自動產出](https://springai-media.zeabur.app/newsletter-media/images/f84883fe0e2317c5f7d9776f756be34e8c2a09a545fb3bd39015fa9b05efd233.png)

沒有人手寫這份文件。它是測試跑完自己長出來的。而且它**永遠跟程式同步**——因為它是從實際跑過的畫面生出來的。流程改了、測試會跟著改，SOP 下次重跑就自動更新。

你有多少份文件，是寫完那天就開始過期的？

## 站 3 收工

開發驗收期的換句話練習：**一句「幫我測試」換成三層產物**——TDD 讓測試先於實作（規格不是鏡子）、E2E 腳本是可重跑的資產、截圖生 SOP 讓文件永遠跟程式同步。

下期是系列完結篇：上線前。「幫我看看有沒有資安漏洞」這句話缺了什麼？為什麼全綠的稽核報告反而可疑？還有一句誠實話：AI 稽核取代不了什麼。最後把四站收成一張你可以直接存下來的總表。下期見。

---

# 主題二｜事件送得出去：交易邊界與 Outbox

上期把 AI 摘要丟到背景執行緒，存檔變快了。這期要講一個在那之後才會浮現的問題。

先看這段程式——它有一個 bug，但你可能看了三遍都找不出來：

```java
@Transactional
public void updateCustomer(Long id, CustomerForm form) {
    Customer customer = repository.findById(id).orElseThrow();
    customer.apply(form);

    publisher.publishEvent(new CustomerUpdated(customer.getId()));
}
```

問題是：**`publishEvent` 這一行執行的時候，交易還沒有 commit。**

`@Transactional` 的 commit 發生在方法**返回之後**。所以事件送出去的那一刻，客戶資料還只存在於這個交易裡，資料庫外面的世界看不到它。

平常這沒事，因為交易通常都會成功。但只要有一次沒成功——

## 一次 rollback 就穿幫

我實際跑了一次：讓交易在 `publishEvent` 之後拋例外、rollback，然後看監聽器有沒有跑。

```text
T1 交易 rollback 後，一般 @EventListener 竟然已經執行過了
   監聽器執行=true / 資料庫實際內容=原本的名字
```

監聽器執行了。資料庫裡卻還是「原本的名字」——那筆更新根本沒存進去。

上期我們已經讓摘要在背景執行緒跑了，所以這裡發生的是：**AI 對著一筆根本不存在的更新，認真地產生了一份摘要，然後把它存起來。**

使用者看到的畫面是：客戶資料沒變（存檔失敗了），但摘要換成了新的、描述著一個從未存在過的版本。

這種 bug 最麻煩的地方在於——**它平常不會出現**。你的測試會過、開發環境會過、上線頭三個月都不會有事。它只在 rollback 的那一次出手，而那通常發生在半夜、發生在你查不到的地方。

![事件送出時機的三種結果：① 用 @EventListener 遇上 rollback，摘要已經開始跑，資料卻退回原狀；② 改用 @TransactionalEventListener 遇上 rollback，事件掛起等 commit、最後直接丟棄，摘要不會產生；③ 用 @TransactionalEventListener 但 commit 成功後當機，資料在、事件消失且沒有任何紀錄——Outbox 補的就是第三種](https://springai-media.zeabur.app/newsletter-media/images/491111e4f3f896c301c614d37b64ece70a34d09f62f1d36115fa37f69efde6ac.png)

<!--paywall-->

## 後段：兩層解法

### 第一層：讓監聽器等交易 commit

Spring 有現成的東西，把 `@EventListener` 換掉就好：

```java
@Component
public class AiSummaryListener {

    /** 只在交易 commit 成功後才執行——預設 phase 就是 AFTER_COMMIT */
    @Async
    @TransactionalEventListener
    public void on(CustomerUpdated event) {
        aiSummaryService.regenerate(event.customerId());
    }
}
```

同樣的 rollback 情境再跑一次，這次乾淨了：

```text
T2 同一次 rollback，@TransactionalEventListener 沒有執行
   監聽器執行=false

T3 交易 commit 後，@TransactionalEventListener 確實執行
   監聽器執行=true
```

`publishEvent` 依然是在交易中間呼叫的，但 Spring 會把事件**掛在交易的同步機制上**，等 commit 成功才真的派發出去。rollback 就直接丟掉。

**有個地雷要先講**：`@TransactionalEventListener` 在**沒有交易**的情況下，預設**完全不會執行**。如果你在某個沒有 `@Transactional` 的地方發了同一個事件，監聽器會安靜地跳過，不會有任何錯誤訊息。要改變這個行為得自己設 `fallbackExecution = true`——但更多時候，這代表你該檢查為什麼那裡沒有交易。

### 但它擋不住這個缺口

`AFTER_COMMIT` 解決了「交易失敗卻送出事件」，但它有一個反過來的缺口：

**交易 commit 成功了，事件卻送不出去。**

commit 完成、Spring 正要派發事件的那一瞬間，如果程式當掉、容器被 kill、機器重開——事件就這樣消失了。資料庫裡有一筆更新完成的客戶資料，但那份摘要永遠不會產生，而且**沒有任何地方記錄過「這件事該做卻沒做」**。

這個缺口用「更小心地寫程式」是補不起來的，因為問題出在：**事件活在記憶體裡，而記憶體不會跟資料庫一起 commit。**

### 第二層：Outbox——把事件變成一筆資料

解法是讓事件跟資料活在同一個地方：不要發到記憶體，先**寫進資料庫的一張表**，而且用同一個交易寫。

```java
@Transactional
public void updateCustomer(Long id, CustomerForm form) {
    Customer customer = repository.findById(id).orElseThrow();
    customer.apply(form);

    // 事件不是「送出去」，而是「寫下來」——跟客戶資料同一個交易
    jdbc.update("insert into outbox(event_type, payload) values (?, ?)",
                "CustomerUpdated", String.valueOf(customer.getId()));
}
```

這樣一來，事件和資料就綁在同一次 commit 上，只有兩種結果，沒有中間態：

```text
T4 交易 rollback 時，outbox 紀錄一起消失      outbox 筆數=0
T5 交易 commit 時，outbox 紀錄一定在          outbox 筆數=1
```

![Outbox 的原子性：同一個交易框住「更新客戶資料」與「寫入 outbox 一筆」兩個動作，commit 時兩筆都在、rollback 時兩筆都沒有，沒有中間態；commit 之後再由排程撈出未送出的事件真的送出去](https://springai-media.zeabur.app/newsletter-media/images/a2cb2492162e19b5ba1eb68bdc8537b86e5c5c717ce835b3c39890660aee465b.png)

要嘛兩個都在，要嘛兩個都沒有。這就是 Outbox 的全部價值——**它把「兩件事要一起成功」這個難題，降級成「一次資料庫交易」這個已經解決的問題**。

接著再開一個排程，定期把 outbox 裡還沒送出的事件撈出來、真的送出去、標記完成：

```sql
select * from outbox where sent_at is null order by id limit 100
```

### 該用哪一個

| | `@TransactionalEventListener` | Outbox |
|---|---|---|
| **擋得住 rollback** | ✅ | ✅ |
| **擋得住 commit 後當機** | ❌ 事件消失 | ✅ 重開後照樣送 |
| **成本** | 換一個註解 | 多一張表、多一個排程 |
| **適合** | 下游可以偶爾漏掉 | 下游漏了會出事 |

我的建議很務實：**先用 `@TransactionalEventListener`**。它幾乎沒有成本，而且擋掉了那個最容易發生、後果最詭異的問題（對著 rollback 的資料產生摘要）。

等到你能明確說出「這件事漏掉會怎樣」而答案很嚴重時——例如漏掉的是扣款、發信、對外通知——再上 Outbox。

🤖 檢查自己專案的事件送出時機，可以把這句丟給 AI：

```text
找出這個專案裡所有 publishEvent 的呼叫位置，逐一回答：
1. 它是不是在一個 @Transactional 方法內部被呼叫的？
2. 對應的監聽器用的是 @EventListener 還是 @TransactionalEventListener？
3. 如果是前者，那個監聽器做的事在交易 rollback 後會造成什麼後果？
   （特別標出：會寫資料、會發信、會呼叫外部 API、會花錢的）
列成表格並依風險排序。先不要改程式碼。
```

## 下期預告

事件現在送得出去了，而且不會在交易失敗時亂送。

但你如果選了 Outbox，會馬上遇到下一個問題：排程把事件送出去之後、還來不及標記「已送出」就當機了——重開之後，這筆事件**會再送一次**。

這就是分散式系統那句有名的「至少一次送達」。在一般系統裡，重送一次頂多是重算一次；但在 AI Agent 身上，重送一次代表**重新呼叫一次 LLM、重新付一次錢，而且拿到不一樣的結果**。

下期就處理這件事。

—— 凱文大叔

---

延伸閱讀：

- [Playwright 官方文件](https://playwright.dev/docs/intro)
- [Martin Fowler：TestDrivenDevelopment](https://martinfowler.com/bliki/TestDrivenDevelopment.html)
- [Kent Beck《Test-Driven Development: By Example》](https://www.oreilly.com/library/view/test-driven-development/0321146530/)
```

---

## 寄送提醒

- **必須在 013 之後寄出**：本期開頭直接回收 013 的「驗收標準變測試」伏筆。
- **主題二（EVENT 系列 E2）**：
  - **付費分段**：主題一全篇免費，`<!--paywall-->` 切在主題二的「後段：兩層解法」之前（體例同 011–013）。
  - **程式碼已實跑驗證**：`pwsh newsletter/scripts/verify-014-tx-event-java.ps1`（Spring 6.2.12 + H2 2.3.232 + JDK 21，用真實交易製造一次 rollback）。文中五段實測輸出（T1–T5）直接取自該腳本，**改動任何 Java 片段或實測輸出後必須重跑**。
  - **驗證腳本刻意不呼叫 LLM**：驗的是交易邊界與事件送出時機。
  - **兩張示意圖已上傳**：來源為 `newsletter/assets/014-a-event-timing.svg`、`014-b-outbox-atomicity.svg` 的 PNG 產物，正式 URL 已回填；hash 與檔名記錄於 `uploaded-urls.txt`。重繪指令：`python newsletter/assets/generate_diagrams.py && node newsletter/assets/render-diagrams.mjs`。
  - **前置條件**：開頭直接回收 013 主題二的鉤子（「事件在交易 commit 之前就送出去了」）——**013 主題二必須先寄出**。
  - **結尾已預告 015 主題二（E3）**：以「Outbox 排程送出後、標記前當機會重送」開鉤子——**015 主題二必須接這條線**。
- **四張截圖已上傳**：來源為 `live-slides/assets/05-tdd-red.png`、`06-tdd-green.png`、`07-e2e-screenshots.png`、`08b-generated-sop-step06.png`，正式 URL 已回填；**綠燈那張是本期主視覺，測試信務必確認測試名稱文字可讀**。
- **工商卡片未填優惠碼**：優惠碼寄送前確認。四期皆有工商卡，建議以投放配額只選其中一〜兩期。
- 提示詞全文以 ```text 圍欄呈現（3 段）——**測試信確認巢狀圍欄渲染正常**；若後台外層 fence 與內層衝突，改縮排式碼塊。
- 「AI 補測試傾向證明自己對」為經驗性觀察（過擬合測試），非引用研究數據，措辭維持經驗口吻；`npx playwright test` 為行內指令，確認等寬字型渲染。
- 結尾預告 015（資安稽核＋四句型總表完結篇）——**015 必須在本期之後寄出**。
- 本期 `<!--paywall-->` 位於主題二後段之前：主題一全篇免費，主題二的「兩層解法」為付費段。
