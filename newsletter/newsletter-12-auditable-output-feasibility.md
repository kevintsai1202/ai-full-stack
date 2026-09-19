# 電子報第 012 期範本：想法期——別再問「能不能做」（可審查產物系列・站 1）＋事件驅動的地圖（EVENT 系列 E0）

> **用途**：一期兩主題。
> 主題一＝「可審查產物」系列站 1（想法期）：改編自直播《別再問 AI「能不能做」》站 1——
> Sycophancy 原理、可行性評估報告提示詞全文、「最沒把握的三件事」實例。
> 主題二＝**EVENT 新系列開場（E0）**：事件驅動的地圖——解耦與非同步的差別，
> 以及「`publishEvent()` 預設是同步的」這個常見誤解的實測證據。
> **使用方式**：將下方「內文（Markdown）」複製到 admin 後台，先更新預覽並寄測試信，確認後再正式寄送。
>
> **「可審查產物」系列鋪陳（011 起，每期為當期兩主題之一）**：
>
> - 011 基礎篇：反差場景、核心主張、agentic coding、錯誤成本、四站路線圖
> - 012（本期）站 1 想法期：「能不能做」→ 可行性評估報告
> - 013 站 2 設計期：「幫我寫一個 X」→ 選型分析＋開發計畫
> - 014 站 3 開發驗收期：「幫我測試」→ TDD＋E2E＋截圖生 SOP
> - 015 站 4 上線前：「幫我看漏洞」→ OWASP／CWE 基準稽核＋四句型總表（系列完結）
>
> **「EVENT」系列鋪陳（012 起，每期為當期兩主題之一；設計見 `docs/superpowers/specs/2026-08-08-newsletter-event-series-design.md`）**：
>
> - 012（本期）E0 地圖：解耦 ≠ 非同步，`publishEvent()` 預設同步
> - 013 E1：為什麼 Agent 一定要非同步、`@Async` 的三個陷阱
> - 014 E2：事件送得出去——交易邊界與 Outbox
> - 015 E3：事件收得下來——重複執行的代價（回收 011 的訊息佇列伏筆）
> - 016 E4（升格主題一）：事件回得了前端——進度、失敗與死信
> - 017 E5：事件即 Agent 的稽核軌跡
>
> **前置條件**：011 主題二已鋪基礎觀念並預告本站——**必須在 011 之後寄出**。
> 主題二開頭回扣 011 的 Webhook（跨系統的事件），亦需 011 先行。
> **素材來源**：主題一為 `live-slides/assets/01-feasibility-difficulty.png`、`02-feasibility-uncertainties.png`；
> 主題二兩張示意圖為 `newsletter/assets/012-a-coupling-vs-event.svg`、`012-b-sync-event-thread.svg`（已繪製）。
> 寄送前需上傳媒體庫並替換文中 TODO 圖片 URL。
>
> 文章中段以「工商時間」卡片（`<!--promo-->` 標記）宣傳 Hahow 課程，
> **優惠碼與期限需在寄送前確認後填入**。

---

## 建議主旨（三選一）

> 主旨以主題一擬定（主題一是本期的推課主線）；第 4 條為主題二備選，若想把新系列開場推到前面可換用。

1. `別再問 AI「能不能做」：yes/no 題只會換來 yes`
2. `「你對這份評估最沒把握的三件事」——整段提示詞最值錢的一句`
3. `AI 說「可以做」的時候，它其實沒有評估過`
4. （主題二備選）`你以為「發事件」是非同步的——它不是`

**推薦**：`別再問 AI「能不能做」：yes/no 題只會換來 yes`

## 預覽文字（preheader）

> 「能不能」幾乎永遠換來「可以」——不是它評估過，是它想讓你滿意。把 yes/no 題換成一份指定格式的可行性評估報告。

---

## 內文（Markdown，直接複製貼上）

```markdown
# 主題一｜想法期：別再問「能不能做」——可審查產物系列・站 1

嗨，我是凱文大叔。

上期開了新系列：決定 AI 交付品質的，是你有沒有要求一份**可以被審查的產物**。路線圖四站——想法期、設計期、開發驗收期、上線前。這期走進第一站：**想法期**——你有一個想法，還沒動手。

## 換一句話

❌ 別問：「這個功能能不能做？」
✅ 要說：「幫我做這個需求的可行性評估報告。」

為什麼「能不能」是個爛問題？因為它是一個 **yes/no 問題**。LLM 經過人類回饋訓練（RLHF），有很強的**討好傾向**（sycophancy）——面對可行性問題，它幾乎永遠會答「可以」，而且會附上一個聽起來很合理的技術棧，讓你更相信它。不是它評估過，是它想讓你滿意。

**yes/no 題只會換來 yes。** 你要做的不是問得更委婉，而是**指定產出格式**——它才非攤開不可。

## 提示詞全文（可直接複製）

```text
針對以下需求，幫我做一份可行性評估報告：
【貼上需求描述】

報告需包含：
1. 技術可行性：現有技術能否達成，需要哪些關鍵元件
2. 難易度分級：把需求拆成子項目，每項標 S / M / L 並說明理由
3. 風險與未知數：哪些地方我現在無法確定，可能會爆的點在哪
4. 前置資源：需要的環境、帳號、授權、資料、外部服務
5. 建議驗證順序：哪一塊該先做 spike 驗證，為什麼

最後請標出：你對這份評估最沒把握的三件事。
```

前面五項是你要它交的作業，最後一句是整段提示詞最值錢的地方——要它**承認自己的極限**。而它承認的那三件事，通常就是你該優先去驗證的地方。

<!--promo-->
### 工商時間

從提問到交付、從評估報告到上線稽核——這套「讓 AI 當代理、你當審查者」的做法，正是課程的主軸。

**《AI 賦能全端開發：從零打造企業級智慧應用》**：用同一套 AI CRM 專案，從 Spring Boot 後端、React 前端到資料庫與權限，一路加上 Spring AI、RAG 與 AI Agent，完成真正能上線的企業級應用。

👉 [前往 Hahow 課程頁](https://hahow.in/cr/ai-full-stack)
<!--/promo-->

## 實際跑出來長什麼樣

這是我拿一個真實需求（任務追蹤器，含相依阻塞規則）實際跑出來的。它把需求拆成子項目，每項標 S / M / L，而且**每一項都要說明理由**——有了這張表，它就不能再用一句「可以做」蓋過去。

![可行性評估報告的難易度分級表：需求被拆成子項目，每項標 S/M/L 並附理由](https://springai-media.zeabur.app/newsletter-media/images/00a4fec9304661c65e901348a6309b62a87f66b672d449cb8ea94735c50c96de.png)

而「最沒把握的三件事」跑出來是這樣：

![評估報告結尾的「最沒把握的三件事」：AI 主動指出需求裡的模糊地帶並建議動工前確認](https://springai-media.zeabur.app/newsletter-media/images/442f5ddcd76ddc470ace21c6cc55983b92d61b2cf2d79991527158f543f35ece.png)

看第一項。它說：「BLOCKED 這個狀態到底是誰設的？需求兩種解讀都說得通，我選了任一種都可能跟你心裡想的不一樣。建議動工前先確認。」

這件事我寫需求的時候完全沒想到。**是它問我的。** 第三項更狠——它反過來質疑我：「這個功能值不值得做到這麼完整，值得你再想一次。」

我後來真的回頭改了規格。如果我當初只問「這個能不能做」，我會得到「可以」，然後直接開工，然後在寫到一半的時候才發現狀態機設計錯了。

## 站 1 收工

想法期的換句話練習就一句：**把 yes/no 題換成一份指定格式的評估報告**——難易度分級讓它不能矇混、風險清單讓你知道哪裡會爆、「最沒把握的三件事」讓它承認極限。

下一站是設計期。你決定要做了，於是開口：「幫我用 React 寫一個報修系統。」——這句話有一個很隱密的問題：**你已經幫它決定了技術棧，而你憑什麼確定 React 是對的？** 下期見。

---

# 主題二｜事件驅動的地圖：解耦，還不是非同步

上一期講完 Webhook，那是**跨系統**的事件：別人家的服務發生事情，打你的 URL 通知你。

這期開始講**同一個系統內**的事件。同一種思路，尺度不同——而且這是新系列的第一站，之後幾期會一路走到「AI Agent 跑在事件上」。

## 先看一段會長歪的程式

AI CRM 裡有個再普通不過的功能：更新客戶資料。

```java
@Transactional
public void updateCustomer(Long id, CustomerForm form) {
    Customer customer = repository.findById(id).orElseThrow();
    customer.apply(form);
}
```

然後需求開始長。客戶資料變了，AI 摘要要重新產生：

```java
    aiSummaryService.regenerate(customer);
```

業務要收到通知：

```java
    salesNotifier.notifyOwner(customer);
```

稽核要留紀錄：

```java
    auditLogger.record(customer);
```

看起來沒什麼問題，但你已經踩到一件事了：**每多一個下游，你就要回頭改一次上游**。`updateCustomer` 這個方法明明只想做一件事——更新客戶資料——現在卻得知道系統裡有誰對這件事有興趣。

而且它們的關係是反的。AI 摘要需要知道「客戶更新了」，這很合理；但「更新客戶」需要知道 AI 摘要的存在嗎？不需要。**是下游依賴上游，不該是上游去認識下游。**

## 事件驅動在解的就是這件事

事件驅動的座標只有三個問題：

- **誰發**：發生事情的那一方，只負責宣告「發生了什麼」
- **誰收**：對這件事有興趣的一方，自己去登記
- **承諾什麼保證**：送達幾次？順序保證嗎？收不到會怎樣？

前兩個問題是**解耦**，第三個問題是**可靠性**。這個系列的後面幾期都在處理第三個問題，但這期先把前兩個講清楚——因為第三個問題只有在你已經解耦之後才會遇到。

改寫成事件之後，上面那段程式長這樣：

```java
@Transactional
public void updateCustomer(Long id, CustomerForm form) {
    Customer customer = repository.findById(id).orElseThrow();
    customer.apply(form);

    // 只宣告「發生了什麼」，不決定誰要處理
    publisher.publishEvent(new CustomerUpdated(customer.getId()));
}
```

以後再加第四個、第五個下游，這個方法一個字都不用改。

![同一個需求兩種接法：左邊直接呼叫，updateCustomer 得認識 AI 摘要、業務通知、稽核紀錄三個下游，每多一個就要回頭改一次；右邊發 CustomerUpdated 事件，三個下游自己登記，上游一個字都不用改](https://springai-media.zeabur.app/newsletter-media/images/f858c6690e196f787bd009a9042cd3e644830a7665ab11d520f87e04aff29807.png)

<!--paywall-->

## 後段：三十行把它接起來

Spring 內建就有，不用裝任何東西。

**一、定義事件。** 用 record 就夠了，它就是一個「發生過的事實」，不該有行為：

```java
/** 客戶資料已更新（過去式命名：事件描述已發生的事，不是待辦指令） */
public record CustomerUpdated(Long customerId) {}
```

命名用**過去式**是有意義的。`CustomerUpdated` 是在說「這件事發生了」，`UpdateCustomer` 則變成在指使別人做事——後者你就又把耦合寫回去了。

**二、發事件。** 注入 `ApplicationEventPublisher`，就是上面那行 `publishEvent`。

**三、收事件。** 每個下游自己登記，上游完全不知道它們存在：

```java
@Component
public class AiSummaryListener {

    /** 客戶資料變了，重新產生 AI 摘要 */
    @EventListener
    public void on(CustomerUpdated event) {
        aiSummaryService.regenerate(event.customerId());
    }
}
```

就這樣。要再加業務通知、稽核紀錄，就是再開兩個 `@Component`，`updateCustomer` 那支方法從此不用再動。

## 但這裡有個很多人搞錯的地方

看到「發事件」三個字，很容易以為它是非同步的——丟出去、不等它、繼續往下跑。

**不是。Spring 的 `publishEvent()` 預設是同步的。**

監聽器跟發布端跑在**同一條執行緒**、**同一個交易**裡。`publishEvent()` 這一行會一直卡到所有監聽器都跑完才返回，跟你直接呼叫方法沒有兩樣。

我把它跑起來量了一次：

```text
R1 預設監聽器與發布端同執行緒 -- publisher=main / listener=main
R2 publishEvent 返回時監聽器已執行完畢
R3 監聽器例外會傳回發布端
```

![同步事件的執行時序：updateCustomer、AI 摘要、業務通知、稽核紀錄、方法返回全部串在同一條 main 執行緒上，publishEvent() 這一行卡在三個監聽器的整段期間；監聽器拋出的例外會一路傳回發布端，連交易一起 rollback](https://springai-media.zeabur.app/newsletter-media/images/d36ec0dc96b6e4e5cb2d86c0aa0df26bdd41465c1c68bf2a94d5b5d9abacdd43.png)

第三條特別值得注意：**監聽器裡拋的例外，會一路傳回發布端**。也就是說，如果 AI 摘要那個監聽器爆炸了，你的 `updateCustomer` 也會跟著失敗、交易一起 rollback——即使「更新客戶資料」本身根本沒問題。

所以請把這一期的結論記清楚：

> **事件解的是「誰認識誰」的問題，不是「誰等誰」的問題。**

解耦跟非同步是兩件事。你可以只要解耦不要非同步（很多情況這才是對的），也可以兩個都要——但那要多做一步。

## 什麼時候該用，什麼時候不要

事件很好用，好用到容易被過度使用。我的判斷是這樣：

**適合事件化：**

- 下游會愈接愈多，而且你不希望每次都回頭改上游
- 下游失敗**不應該**害上游一起失敗（稽核寫不進去，不該讓客戶存不了檔）
- 下游是「附加價值」而非「主流程」——摘要、通知、紀錄都算

**不要事件化：**

- 只有一個下游，而且可預見不會變多——直接呼叫更好讀
- 上游需要下游的回傳值（事件是單向宣告，要拿回傳值就別用它）
- 下游失敗時整件事就該失敗——那它根本是主流程的一部分，直接寫在一起才誠實

最後這條是最常見的誤用：把主流程拆成事件，結果出事的時候看不出來哪裡斷掉，還得翻三個檔案才拼得回完整流程。**解耦是有代價的，代價是可讀性。** 值不值得，看下游會不會長。

🤖 想知道自己專案裡哪些地方適合改成事件，可以把這句丟給 AI：

```text
掃描這個專案，找出「一個方法裡連續呼叫多個不同 service」的地方。
針對每一處回答：這些下游呼叫中，哪些是主流程（失敗就該整個失敗）、
哪些是附加效果（失敗不該影響主流程）？
只列出附加效果佔多數的位置，並說明改成事件後上游可以少知道哪些事。
先不要動程式碼。
```

## 下期預告

現在你知道 `publishEvent()` 預設是同步的了。那麼問題來了——

AI CRM 的「產生客戶摘要」要呼叫 LLM，一跑就是**三十秒**。如果監聽器跟發布端同一條執行緒，使用者按下「儲存」之後，就得盯著轉圈圈等三十秒。

下期就處理這件事：為什麼 AI Agent 這種工作，非得非同步不可——以及 `@Async` 那三個一加就中的陷阱。

—— 凱文大叔

---

延伸閱讀：

- [Anthropic：Sycophancy 與模型討好傾向研究](https://www.anthropic.com/research/towards-understanding-sycophancy-in-language-models)
- [Martin Fowler：LLM 輔助開發的工作流觀察](https://martinfowler.com/articles/exploring-gen-ai.html)
```

---

## 寄送提醒

- **必須在 011 之後寄出**：011 主題二已鋪基礎觀念（主張、agentic coding、錯誤成本）並預告本站；本期開頭僅簡短回扣，不重複鋪陳。
- **主題一兩張截圖已前處理，待上傳**：來源為 `live-slides/assets/01-feasibility-difficulty.png`、`02-feasibility-uncertainties.png`（白底報告截圖，非終端畫面）。已用 `python newsletter/assets/prepare-screenshots.py` 自動裁掉四周白邊，輸出到 `newsletter/assets/png/012-c-feasibility-difficulty.png`（1120×577，高度減 19%）與 `012-d-feasibility-uncertainties.png`（1116×274，高度減 31%）——信件寬度通常限制在 600px，裁掉留白等於把有效內容放大約兩成。
  - 上傳：`pwsh newsletter/scripts/upload-media.ps1 newsletter/assets/png/012-c-*.png newsletter/assets/png/012-d-*.png`（需先設 `$env:ADMIN_API_KEY`），URL 會自動追加到 `uploaded-urls.txt`。
  - 上傳後替換內文的 `TODO-012-feasibility-difficulty.png` 與 `TODO-012-feasibility-uncertainties.png` 兩個 URL。
  - **測試信必須確認截圖文字可讀**：第二張（最沒把握的三件事）是文字密集的長句，在手機上縮到 600px 後最容易出問題；若不可讀，考慮改為只截其中第 1 項放大呈現。
- **工商卡片未填優惠碼**：優惠碼寄送前確認。系列各期皆有工商卡，建議以投放配額只選其中一〜兩期。
- 提示詞全文以 ```text 圍欄呈現（本系列的主角是提示詞本身，與 008–011 的「🤖 丟給 AI」短句定位不同）——**測試信確認巢狀圍欄渲染正常**；若後台的外層 markdown fence 與內層衝突，把內層改為縮排式碼塊。主題二含多段 ```java 圍欄，一併確認。
- Sycophancy／RLHF 描述為通行學術用語；「除錯成本放大十倍」為量級描述非精確統計，措辭已保留餘地。
- 結尾已預告 013（設計期）——**013 必須在本期之後寄出**。

### 主題二（EVENT 系列 E0）專屬

- **付費分段**：主題一全篇免費，`<!--paywall-->` 切在主題二的「後段：三十行把它接起來」之前——體例同 011（主題一免費、主題二後段付費）。
- **程式碼已實跑驗證**：`pwsh newsletter/scripts/verify-012-event-java.ps1`（Spring 6.2.12 + JDK 21）。文中三行實測輸出（R1／R2／R3）直接取自該腳本執行結果，**改動任何 Java 片段或那三行輸出後必須重跑**。
- **驗證腳本刻意不呼叫 LLM**：本系列驗的是事件機制本身；真的呼叫 LLM 會產生費用且結果不可重現。
- **兩張示意圖已繪製，待上傳**：`newsletter/assets/012-a-coupling-vs-event.svg`（直接呼叫 vs 發事件）、`012-b-sync-event-thread.svg`（同步事件的執行時序），PNG 在 `newsletter/assets/png/`。重繪指令：`python newsletter/assets/generate_diagrams.py && node newsletter/assets/render-diagrams.mjs`。寄送前上傳媒體庫，替換文中 `TODO-012-coupling-vs-event.png`、`TODO-012-sync-event-thread.png` 兩個 URL，並記錄 hash 到 `uploaded-urls.txt`。
- **版本敘述會過期**：「`publishEvent()` 預設同步」為 Spring 長期行為，風險低；但 `@Async` 預設執行器（`SimpleAsyncTaskExecutor`）在 Spring Boot 啟用虛擬執行緒時行為不同——該點留待 013（E1）詳述，本期未觸及，**跨年後寄送仍建議重跑驗證腳本確認**。
- **結尾已預告 013 主題二（E1）**：以「AI 摘要要跑三十秒」開的鉤子——**013 主題二必須接這條線**。
