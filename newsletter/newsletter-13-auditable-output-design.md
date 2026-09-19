# 電子報第 013 期範本：設計期——先別急著寫（可審查產物系列・站 2）＋為什麼 AI Agent 一定要非同步（EVENT 系列 E1）

> **用途**：一期兩主題。
> 主題一＝「可審查產物」系列站 2（設計期）：改編自直播站 2——
> 「幫我用 React 寫一個 X」的隱密問題、三段式提問（選型分析 → 開發計畫 → 才實作）、
> 約束前置原理、可驗證驗收標準的寫法。
> 主題二＝**EVENT 系列 E1**：Agent 的三個特性（慢、貴、不可重複）為何讓同步變成真問題，
> 以及 `@Async` 的三個不會報錯的陷阱（self-invocation、例外被吞、預設執行器不重用執行緒）。
> **使用方式**：將下方「內文（Markdown）」複製到 admin 後台，先更新預覽並寄測試信，確認後再正式寄送。
>
> **「可審查產物」系列鋪陳（011 起，每期為當期兩主題之一）**：
>
> - 011 基礎篇：反差場景、核心主張、agentic coding、錯誤成本、四站路線圖
> - 012 站 1 想法期：「能不能做」→ 可行性評估報告
> - 013（本期）站 2 設計期：「幫我寫一個 X」→ 選型分析＋開發計畫
> - 014 站 3 開發驗收期：「幫我測試」→ TDD＋E2E＋截圖生 SOP
> - 015 站 4 上線前：「幫我看漏洞」→ OWASP／CWE 基準稽核＋四句型總表（系列完結）
>
> **「EVENT」系列鋪陳（012 起；設計見 `docs/superpowers/specs/2026-08-08-newsletter-event-series-design.md`）**：
>
> - 012 E0 地圖：解耦 ≠ 非同步，`publishEvent()` 預設同步
> - 013（本期）E1：為什麼 Agent 一定要非同步、`@Async` 的三個陷阱
> - 014 E2：事件送得出去——交易邊界與 Outbox
> - 015 E3：事件收得下來——重複執行的代價
> - 016 E4（升格主題一）：事件回得了前端——進度、失敗與死信
> - 017 E5：事件即 Agent 的稽核軌跡
>
> **前置條件**：012 結尾已預告本期——**必須在 012 之後寄出**。
> 主題二開頭直接回收 012 主題二的鉤子（「publishEvent 預設同步 → AI 摘要要跑三十秒」），
> **012 主題二必須先寄出**。
> **素材來源**：主題一為 `live-slides/assets/03-tech-selection.png`、`04-milestone-acceptance.png`；
> 主題一流程圖為 `newsletter/assets/013-e-design-flow.svg`；主題二示意圖為 `newsletter/assets/013-a-sync-vs-async-timeline.svg`、`013-b-async-pitfalls.svg`、`013-f-async-proxy-boundary.svg`（已繪製）。
> **主視覺**：已生成 `newsletter/newsletter-13-cover.png`；若本期使用封面，寄送前上傳媒體庫。
> 寄送前需上傳媒體庫並替換文中 TODO 圖片 URL。
>
> 文章中段以「工商時間」卡片（`<!--promo-->` 標記）宣傳 Hahow 課程，
> **優惠碼與期限需在寄送前確認後填入**。

---

## 建議主旨（三選一）

1. `「幫我用 React 寫一個」——這句話的問題是：你憑什麼確定 React 是對的？`
2. `計畫改一行，程式碼改一天：叫 AI 動手前的兩個便宜產物`
3. `可審查產物系列（二）：選型分析與開發計畫`

**推薦**：`「幫我用 React 寫一個」——這句話的問題是：你憑什麼確定 React 是對的？`

## 預覽文字（preheader）

> 一句「幫我寫」換成三句：先選型分析、再開發計畫、才實作——外加一句一定要加的「先不要寫程式碼」。

---

## 內文（Markdown，直接複製貼上）

```markdown
# 主題一｜設計期：先別急著寫——可審查產物系列・站 2

嗨，我是凱文大叔。

上期在想法期把「能不能做」換成了可行性評估報告。這期進第二站：**設計期**——你確定要做了，但還沒開始寫。

## 一句話裡藏的決定

❌ 別說：「幫我用 React 寫一個報修系統。」

這句話有一個很隱密的問題：**你已經幫它決定了技術棧**，而你憑什麼確定 React 是對的？也許這個工具只有你一個人用、畫面只有三頁、資料量三百筆——一個純 HTML 加一點 JS 就夠了；也許你的團隊全是 Java 人，前端只有一個人扛。

不先分析就點名技術，AI 不會反駁你——它會照做（上期講過的討好傾向，這裡再度登場）。所以設計期的換句話，是**一句換三句**：

1. **先做選型分析**：列候選方案、優缺點、適用情境、推薦與理由
2. **再寫開發計畫**：模組、里程碑、驗收標準——**先不要寫程式碼**
3. **才照計畫實作**第一階段

![設計期三段式產物鏈：先把需求與限制攤開，做選型分析，再寫含模組、里程碑與驗收標準的開發計畫，最後才進入第一階段實作；中間以「先不要寫程式碼」保留審查點](https://springai-media.zeabur.app/newsletter-media/images/616257cb6651ecd4ff3b6cce9472feaeba844ce46c045c36762cede2051a921a.png)

## ① 選型分析：沒有限制的建議等於沒建議

### 提示詞全文（可直接複製）

```text
我要做【需求】。我的限制是：
- 開發環境：【例：Windows + PowerShell】
- 團隊技能：【例：熟 Java，前端只有一人】
- 部署環境：【例：公司內網，不能連外】
- 時程與人力：【…】

請列出 2-4 個可行的技術選型方案，每個方案說明：
優點、缺點、適用情境、在我上述限制下的契合度。
最後給出你的推薦與理由，並說明什麼情況下你會改推薦別的方案。
```

重點在「**我的限制是**」那四個欄位。不先講限制，AI 會直接套訓練資料裡出現最多次的組合——那是「大家最常用的」，不是「你能用的」。把環境、技能、時程寫進提示詞，等於**把驗收條件搬到生成之前**——這一招叫約束前置（constraint-first），後面幾站會反覆用到。

最後一句也很關鍵：「說明什麼情況下你會改推薦別的方案。」實際跑出來是這樣：

![選型分析的「改推薦條件」表：列出時程、使用人數、畫面數、資料量等邊界，任一條成立推薦就會變](https://springai-media.zeabur.app/newsletter-media/images/TODO-013-tech-selection.png)

這張表比推薦本身更重要。它告訴你這個決定的**有效邊界**：時程放寬到三天以上、這工具要給團隊用、畫面超過八個、資料到數萬筆——任何一條成立，推薦就會變。一個沒有附上邊界條件的建議，你沒辦法判斷它什麼時候會過期。

<!--promo-->
### 工商時間

選型、模組拆解、里程碑與驗收標準——這些設計期的功夫，課程裡是拿一整套企業級專案從頭做給你看的。

**《AI 賦能全端開發：從零打造企業級智慧應用》**：用同一套 AI CRM 專案，從 Spring Boot 後端、React 前端到資料庫與權限，一路加上 Spring AI、RAG 與 AI Agent，完成真正能上線的企業級應用。

👉 [前往 Hahow 課程頁](https://hahow.in/cr/ai-full-stack)
<!--/promo-->

## ② 開發計畫：一句一定要加的話

### 提示詞全文（可直接複製）

```text
採用【選定方案】。請寫一份開發計畫：
1. 模組拆解：每個模組的職責與相依關係
2. 里程碑：分幾個階段，每階段的產出是什麼
3. 每階段的驗收標準：怎樣算做完（要可驗證，不要「功能正常」這種寫法）
4. 風險點與對應的先行驗證

先不要寫程式碼。
```

「**先不要寫程式碼**」這句請一定要加。為什麼？因為開發計畫是一個**便宜的中間產物**——

> **計畫改一行，程式碼改一天。**

計畫寫在紙上，你才看得出它哪裡不準；程式寫完才發現方向錯，成本差一個數量級。沒有這句話，AI 很樂意直接動工，把你還沒審過的假設全部寫進程式碼裡。

另一個重點是第三項驗收標準的寫法：「怎樣算做完，**要可驗證**，不要『功能正常』這種寫法。」實際產出長這樣：

![開發計畫的里程碑驗收標準：每一條都是可打勾的具體判準，例如空專案回 0、三任務完成一個回 1/3、循環相依被拒絕](https://springai-media.zeabur.app/newsletter-media/images/TODO-013-milestone-acceptance.png)

每一條都能打勾：空專案進度回 0、三個任務完成一個回 1/3、A→B→A 的循環相依被拒絕……「功能正常」四個字你沒辦法審查，這批句子可以。

而且先劇透一件事：**這批驗收標準等一下會直接變成測試**——下一站你會看到它們一模一樣地出現在測試名稱裡。這就是「可審查產物」會產生的複利：這一站的產物，是下一站的輸入。

## 站 2 收工

設計期的換句話練習：**一句「幫我寫」拆成三句**——選型分析逼出有效邊界、開發計畫給你便宜的審查點、然後才動工。外加一句護身符：「先不要寫程式碼。」

下一站是全系列最大的一站：開發驗收期。「幫我測試程式有沒有問題」這句話的毛病在哪？為什麼 AI 寫完程式再補的測試，會變成「證明自己對」的測試？還有一招我覺得整場最划算的——讓測試跑完自己長出一份操作手冊。下期見。

---

# 主題二｜為什麼 AI Agent 一定要非同步

上期的結論是：Spring 的 `publishEvent()` 預設是同步的，監聽器跟發布端跑在同一條執行緒上。

那時候留了一個問題沒回答——

AI CRM 的「產生客戶摘要」要呼叫 LLM，**一跑就是三十秒**。如果監聽器跟發布端同一條執行緒，使用者按下「儲存客戶資料」之後，畫面就得轉三十秒的圈圈。

而且問題不只是「久」。

## 三十秒會引發的連鎖反應

使用者不會乖乖等。他會：**重新整理，然後再按一次儲存。**

於是第二次請求進來，第二次 LLM 呼叫也開始跑。第一次那個沒有停——它還在別的地方跑著，只是沒人在等它的結果了。

你付了兩次錢，拿到一個沒人要的結果。

更糟的是，這件事有很大機率不是使用者主動觸發的。反向代理有預設逾時（Nginx 常見是 60 秒）、瀏覽器有逾時、前端的 HTTP 客戶端通常也設了重試——**這條鏈上任何一環先放棄，重試就自動發生了**，使用者甚至不知道自己送出了兩次。

## Agent 跟一般 CRUD 不一樣的三件事

一般的 CRUD 操作，同步做完全沒問題：查個資料庫幾毫秒，慢也慢不到哪去。但 Agent 有三個特性，讓「同步」從小缺點變成真問題：

| | 一般 CRUD | AI Agent |
|---|---|---|
| **快慢** | 毫秒級 | 數秒到數十秒 |
| **成本** | 幾乎為零 | 按 token 計費，跑一次付一次 |
| **可重複性** | 重跑結果一樣 | 重跑結果**不一樣** |

第三點最容易被忽略。一般的重試是安全的——重算一次總額，答案還是同一個。但 LLM 重跑會給你**不同的摘要**，於是「重試」不再是無害的補救，而是變成資料不一致的來源。

所以結論很直接：**Agent 這類工作，不該讓使用者的請求在原地等它。**

要把「更新客戶資料」和「產生 AI 摘要」在**時間上**切開——前者立刻做完回應使用者，後者自己在背景慢慢跑。

上期我們已經把它們在**結構上**切開了（事件解耦）。這期補上時間上的那一刀。

![同步與非同步的時間軸對比：同步時「存檔→呼叫 LLM 產生摘要→回應使用者」串在一條線上，使用者從按下儲存到畫面有反應要等三十秒，等不及重新整理就會讓 LLM 再跑一次；非同步時 main 執行緒只做「存檔→回應使用者」零點幾秒就結束，呼叫 LLM 產生摘要移到背景執行緒](https://springai-media.zeabur.app/newsletter-media/images/TODO-013-sync-vs-async-timeline.png)

<!--paywall-->

## 後段：一個註解，三個陷阱

先講好消息：把上期那個監聽器變成非同步，只要加一個註解。

```java
@Component
public class AiSummaryListener {

    /** 客戶資料變了就重新產生 AI 摘要——丟到別的執行緒跑，不擋住存檔 */
    @Async
    @EventListener
    public void on(CustomerUpdated event) {
        aiSummaryService.regenerate(event.customerId());
    }
}
```

再到設定類別上加 `@EnableAsync`，就完成了。使用者按下儲存，交易 commit、立刻回應；摘要在別條執行緒上慢慢跑。

壞消息是，這個註解有三個陷阱，而且三個都**不會報錯**——程式照樣編譯、照樣執行，只是沒有照你以為的方式跑。

![@Async 的三個陷阱：① 自己呼叫自己——同一個 bean 內 this.generate() 繞過 proxy，實測 caller 與執行緒都是 main，等於沒加；② 例外被吞掉——void 方法拋例外時呼叫端早已返回，抓不到例外、只剩一行 SEVERE log，摘要空白且無提示；③ 預設執行器——沒有 taskExecutor bean 就退回 SimpleAsyncTaskExecutor，送 5 次開 5 條新執行緒且沒有上限](https://springai-media.zeabur.app/newsletter-media/images/TODO-013-async-pitfalls.png)

以下三段的實測輸出，都來自我實際跑起來的 Spring context（Spring 6.2.12 / JDK 21）。

## 陷阱一：自己呼叫自己，`@Async` 直接失效

這段程式看起來完全合理：

```java
@Service
public class SummaryService {

    public void handleUpdate(Long customerId) {
        this.generate(customerId);   // ← 這一行不會非同步
    }

    @Async
    public void generate(Long customerId) {
        // 呼叫 LLM，很慢
    }
}
```

`@Async` 是靠 **proxy** 實現的：Spring 在你的 bean 外面包一層代理，外部呼叫先經過代理，代理才把工作丟給執行緒池。

![Async 正確執行邊界：外部呼叫先經過 Spring proxy，再交給有界 taskExecutor 與背景 worker；下方對比 this.generate() 繞過 proxy、void 例外只剩 log、沒有 taskExecutor 時退回無上限的 SimpleAsyncTaskExecutor](https://springai-media.zeabur.app/newsletter-media/images/c8c7d27cb787795bb051fc312e7db30b68e863a2c5ab772fa5391fee1080534a.png)

但 `this.generate(...)` 是**從物件內部呼叫自己**——它根本沒經過代理，直接就是一次普通的方法呼叫。

實測：

```text
A2 self-invocation 讓 @Async 失效（仍同步、同執行緒）
   caller=main / 執行於=main
```

呼叫端在 `main`，被呼叫的 `@Async` 方法也在 `main`。註解等於沒加。

**怎麼避免**：讓 `@Async` 方法待在另一個 bean 裡，從外部呼叫它。上面那個 `@EventListener` 寫法天生就是安全的——事件是 Spring 幫你派發的，本來就經過代理。

## 陷阱二：例外不見了

```java
@Async
public void generate(Long customerId) {
    throw new IllegalStateException("LLM 回傳格式不對");
}
```

呼叫端 try-catch 包好包滿，**什麼都抓不到**：

```text
A3 @Async void 的例外不會傳回呼叫端（被吞掉）
   呼叫端沒有收到任何例外
```

原因很單純：呼叫端早就返回了，例外發生的時候已經沒有人在等它——這是非同步的必然結果，不是 bug。

但例外沒有消失，**它換了去處**。Spring 把它交給 `AsyncUncaughtExceptionHandler`，預設實作就是印一行 log：

```text
SEVERE: Unexpected exception occurred invoking async method:
        public void SummaryService.generate(java.lang.Long)
java.lang.IllegalStateException: LLM 回傳格式不對
```

問題是——**你會去看那行 log 嗎？**

摘要沒產生出來，使用者只會看到「摘要空白」，不會看到任何錯誤訊息。畫面上一切正常，資料默默地少了一塊。

**怎麼避免**：非同步工作的失敗必須有明確去處，不能只留在 log 裡。最低限度是在方法內自己 try-catch，把失敗狀態寫回資料庫（例如 `summary_status = FAILED`），讓畫面有東西可顯示、讓你有東西可查。這也是後面幾期要處理的主題。

## 陷阱三：你以為有執行緒池，其實沒有

如果你只加了 `@EnableAsync`、沒有定義任何執行器，Spring 會在啟動時印這行：

```text
INFO: No task executor bean found for async processing:
      no bean of type TaskExecutor and no bean named 'taskExecutor' either
```

然後退回用 `SimpleAsyncTaskExecutor`。這東西的行為是——**每一次呼叫都開一條新執行緒，用完就丟，完全不重用**。

實測連續送五次：

```text
A4 預設 SimpleAsyncTaskExecutor 不重用執行緒
   送 5 次，用掉 5 條不同執行緒
   [SimpleAsyncTaskExecutor-3, -4, -5, -7, -6]
```

五次五條。而且它**沒有上限**——一百個使用者同時存檔，就是一百條執行緒，每條都卡著等 LLM 回應三十秒。

換成有界的執行緒池，同樣送五次：

```text
A5 改用 ThreadPoolTaskExecutor(core=2) 後執行緒被重用
   送 5 次，只用掉 2 條執行緒 [crm-async-1, crm-async-2]
```

設定也不複雜：

```java
/** bean 名稱必須叫 taskExecutor，@Async 才會預設採用它 */
@Bean
public ThreadPoolTaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(50);          // 滿了就排隊，不要無限開執行緒
    executor.setThreadNamePrefix("crm-async-");  // 出事時 log 看得出是誰
    executor.initialize();
    return executor;
}
```

那個 bean 名稱是硬性的：**必須叫 `taskExecutor`**，否則 Spring 找不到，又默默退回 `SimpleAsyncTaskExecutor`——你設定了一個沒人用的執行緒池，而且不會有任何錯誤提示。

（補充一句：如果你的 Spring Boot 開了虛擬執行緒——3.2 起可用 `spring.threads.virtual.enabled=true`——這一題的性質會不同，虛擬執行緒本來就是設計成可以大量建立的。但該選項預設關閉，所以多數人遇到的還是上面這個版本。）

🤖 檢查自己專案的三個陷阱，可以把這句丟給 AI：

```text
檢查這個專案裡所有標了 @Async 的方法，逐一回答：
1. 有沒有被同一個類別內部以 this. 呼叫（self-invocation，會讓 @Async 失效）？
2. 回傳型別是不是 void？若是，方法內有沒有自己處理例外？沒有的話失敗只會進 log。
3. 專案裡有沒有名為 taskExecutor 的 TaskExecutor bean？沒有的話用的是不重用執行緒的預設實作。
列成表格，每一項標出檔案位置與風險等級。先不要改程式碼。
```

## 下期預告

現在存檔很快、摘要在背景跑，看起來很完美。

但你把事件的發送時機和資料庫交易放在一起看，會發現一個很不妙的問題：**事件是在交易 commit 之前送出去的。**

也就是說，摘要那條執行緒可能已經開始跑了，而客戶資料的交易還沒 commit——甚至最後 rollback 了。Agent 對著一筆不存在的資料，認真地產生了一份摘要。

下期就處理這件事：事件到底該在什麼時候送出去。

—— 凱文大叔

---

延伸閱讀：

- [Thoughtworks Technology Radar：技術選型的決策脈絡](https://www.thoughtworks.com/radar)
- [Martin Fowler：探索 LLM 輔助開發的工作流](https://martinfowler.com/articles/exploring-gen-ai.html)
```

---

## 寄送提醒

- **必須在 012 之後寄出**：012 結尾已預告本期，且本期開頭引用上期（討好傾向、可行性評估）。
- **主題二（EVENT 系列 E1）**：
  - **付費分段**：主題一全篇免費，`<!--paywall-->` 切在主題二的「後段：一個註解，三個陷阱」之前（體例同 011、012）。
  - **程式碼已實跑驗證**：`pwsh newsletter/scripts/verify-013-async-java.ps1`（Spring 6.2.12 + JDK 21）。文中四段實測輸出（A2／A3／A4／A5）與那行 `No task executor bean found` 的 INFO 訊息，皆直接取自該腳本執行結果，**改動任何 Java 片段或實測輸出後必須重跑**。
  - **驗證腳本刻意不呼叫 LLM**：驗的是事件與執行緒機制本身。
  - **補充流程／架構圖已上傳**：`newsletter/assets/013-e-design-flow.svg`、`013-f-async-proxy-boundary.svg` 已轉成 PNG 並上傳媒體庫；正式 URL 已回填本文，對應 hash 已記錄在 `newsletter/assets/uploaded-urls.txt`。重繪指令：`python newsletter/assets/generate_diagrams.py && node newsletter/assets/render-diagrams.mjs`。
  - **版本敘述會過期**：文末括號提到 Spring Boot 3.2 起的 `spring.threads.virtual.enabled`（預設關閉）——**跨年後寄送須重新確認該選項的預設值與行為**。
  - **結尾已預告 014 主題二（E2）**：以「事件在交易 commit 之前就送出去了」開鉤子——**014 主題二必須接這條線**。
- **圖片為 TODO 佔位**：兩張截圖來源為 `live-slides/assets/03-tech-selection.png`、`04-milestone-acceptance.png`，寄送前上傳媒體庫並替換 URL；**測試信確認截圖文字可讀**。
- **本機圖片產物**：`newsletter/assets/png/013-c-tech-selection.png`、`013-d-milestone-acceptance.png` 已納入 `prepare-screenshots.py` 的裁切流程；寄送前上傳媒體庫並替換兩個 TODO URL。
- **工商卡片未填優惠碼**：文案主打設計期功夫，優惠碼寄送前確認。四期皆有工商卡，建議以投放配額只選其中一〜兩期。
- 提示詞全文以 ```text 圍欄呈現（2 段）——**測試信確認巢狀圍欄渲染正常**；若後台外層 fence 與內層衝突，改縮排式碼塊。
- 「計畫改一行，程式碼改一天」為金句式量級描述非精確統計；「約束前置（constraint-first）」為講者自訂教學用語，非業界標準術語，文中已以教學口吻呈現。
- 結尾預告 014（TDD／E2E／SOP）——**014 必須在本期之後寄出**。
- 本期 `<!--paywall-->` 位於主題二後段之前：主題一全篇免費，主題二的「三個陷阱」為付費段。
