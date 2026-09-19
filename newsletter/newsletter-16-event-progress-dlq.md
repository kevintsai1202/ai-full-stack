# 電子報第 016 期範本：事件回得了前端——進度、失敗與死信（EVENT 系列 E4）

> **用途**：單一主題——**EVENT 系列 E4**（因「可審查產物」系列已於 015 完結，本期改為單主題）：
> 非同步之後使用者失去感知，用狀態機 + SSE 把進度補回來；退避重試的溢位坑；死信與人工介入。
> **使用方式**：將下方「內文（Markdown）」複製到 admin 後台，先更新預覽並寄測試信，確認後再正式寄送。
>
> **「EVENT」系列鋪陳（012 起；設計見 `docs/superpowers/specs/2026-08-08-newsletter-event-series-design.md`）**：
>
> - 012 E0 地圖：解耦 ≠ 非同步，`publishEvent()` 預設同步
> - 013 E1：為什麼 Agent 一定要非同步、`@Async` 的三個陷阱
> - 014 E2：事件送得出去——交易邊界與 Outbox
> - 015 E3：事件收得下來——重複執行的代價
> - 016（本期）E4：事件回得了前端——進度、失敗與死信
> - 017 E5：事件即 Agent 的稽核軌跡（系列完結）
>
> **前置條件**：015 主題二結尾已預告本期（「使用者完全看不到這一切」）——**必須在 015 之後寄出**。
> 本期後段回收 009 期的 SSE，並直接沿用 015 建立的 `processed_events` 狀態表。
> **素材來源**：兩張示意圖為 `newsletter/assets/016-a-status-machine.svg`、
> `016-b-backoff-dlq.svg`（已繪製），寄送前需上傳媒體庫並替換文中 TODO 圖片 URL。
>
> 文章中段以「工商時間」卡片（`<!--promo-->` 標記）宣傳 Hahow 課程，
> **優惠碼與期限需在寄送前確認後填入**。

---

## 建議主旨（三選一）

1. `我們把等待從使用者身上拿掉，也把「知道進度」一起拿掉了`
2. `重試三次還是失敗，然後呢？——死信與人工介入`
3. `退避時間變成負數的那一刻，重試就成了風暴`

**推薦**：`我們把等待從使用者身上拿掉，也把「知道進度」一起拿掉了`

## 預覽文字（preheader）

> 非同步讓存檔變快了，代價是使用者不知道摘要好了沒。這期把進度、失敗與放棄的機制一次補回來。

---

## 內文（Markdown，直接複製貼上）

```markdown
# 事件回得了前端：進度、失敗與死信

前四期把事件的路鋪完了：解耦（E0）、非同步（E1）、送得出去（E2）、收得下來（E3）。

系統現在很穩：存檔立刻回應、交易失敗不會亂送、當機不會遺失、重送不會重跑。

然後你把畫面打開，發現一件事——**使用者完全不知道發生了什麼。**

按下儲存，畫面立刻回來了。摘要區塊空空的。過三十秒，還是空的（因為前端不會自己重新查）。使用者重新整理一次，摘要出現了。

他不知道要等，也不知道等多久，更不知道到底成功了沒有。

## 我們拿掉了等待，也拿掉了感知

同步有一個被低估的好處：**它的進度回報是免費的**。

畫面在轉圈圈，就代表還在跑；轉圈停了、內容出現，就是成功；跳出錯誤訊息，就是失敗。使用者不需要學任何東西就懂。

改成非同步之後，這三件事全部消失了：

| 使用者想知道 | 同步時 | 非同步後 |
|---|---|---|
| 現在在跑嗎 | 看轉圈圈 | **不知道** |
| 成功了嗎 | 內容出現 | **不知道** |
| 失敗了嗎 | 跳錯誤訊息 | **不知道** |

而且第三項最糟：失敗的時候，畫面跟「還沒跑完」長得一模一樣。使用者會一直等一個永遠不會來的東西。

這期就是把這三件事補回來。而且好消息是——上期我們已經把地基打好了。

<!--promo-->
### 工商時間

這個系列講的是「AI 功能上線之後」會遇到的事。而在那之前，你得先有一套真的跑得起來的系統。

**《AI 賦能全端開發：從零打造企業級智慧應用》**：用同一套 AI CRM 專案，從 Spring Boot 後端、React 前端到資料庫與權限，一路加上 Spring AI、RAG 與 AI Agent，完成真正能上線的企業級應用。

👉 [前往 Hahow 課程頁](https://hahow.in/cr/ai-full-stack)
<!--/promo-->

<!--paywall-->

## 後段：狀態、進度、放棄

### 一、狀態機：先讓系統自己知道

上期為了做冪等，我們建了一張表記錄哪些事件處理過。當時多存了幾個欄位，現在派上用場：

```sql
create table event_tasks (
    event_id     varchar(64) primary key,
    status       varchar(20) not null,     -- PENDING / RUNNING / DONE / FAILED
    attempts     int         not null default 0,
    last_error   varchar(500),
    updated_at   timestamp   not null
);
```

四個狀態，一條路徑：

- **PENDING**：已登記，還沒開始
- **RUNNING**：正在呼叫 LLM
- **DONE**：成功了
- **FAILED**：重試到上限，放棄了

關鍵原則是**只進不退**。已經 DONE 的任務不可以再被改回 RUNNING——這條規則會擋掉「重送的舊事件把新結果蓋掉」這類極難查的問題。實作上就是在更新時加條件：

```sql
update event_tasks set status = 'RUNNING'
 where event_id = ? and status = 'PENDING'
```

更新到 0 筆，就代表這個事件不該由你處理，直接跳過。

![任務狀態機：PENDING（已登記還沒開始）→ RUNNING（正在呼叫 LLM）→ 分為 DONE（成功）與 FAILED（重試到上限放棄），FAILED 再進入死信表（查得到、可人工重跑）；DONE 不可退回 RUNNING，靠 update … where status = 'PENDING' 更新到 0 筆就跳過來保證只進不退](https://springai-media.zeabur.app/newsletter-media/images/e6eaec6732ff38b1cda669af5a70db8e425d4db382ff6491900df9d9bf51ee99.png)

### 二、把狀態送到畫面上

有了狀態，前端就有東西可問了。最簡單的做法是輪詢：畫面每兩秒問一次「好了沒」。

但這個系列的讀者應該記得 009 期——這正是 **SSE** 的主場：一條不掛斷的 HTTP，伺服器有進度就推一次。

```java
@GetMapping(value = "/api/customers/{id}/summary-status",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter status(@PathVariable Long id) {
    SseEmitter emitter = new SseEmitter(60_000L);   // 一分鐘沒結果就讓前端重連
    statusBroadcaster.register(id, emitter);
    return emitter;
}
```

然後在狀態每次變更的地方推一則事件出去。前端收到 `DONE` 就把摘要抓回來、收到 `FAILED` 就顯示錯誤與重試按鈕。

**這裡有個很容易忽略的細節**：連線建立的當下要**先推一次目前狀態**。否則使用者重新整理頁面時，如果任務早就跑完了，SSE 之後不會再有任何事件，畫面就會永遠停在「處理中」。

### 三、失敗要退，但不能退到負數

呼叫 LLM 會失敗——限流、逾時、回傳格式不對。失敗就重試，但不能立刻重試（對方正在喘氣，你馬上再打一次只會更糟）。

標準做法是**指數退避**：每次失敗就把等待時間加倍，並且設上限。

```java
private static final long BASE_MS = 1000;
private static final long MAX_MS  = 30_000;

/** 指數退避：先把指數夾在安全範圍，再位移 */
long backoffMillis(int attempt) {
    int safe = Math.min(attempt, 30);
    long delay = BASE_MS << (safe - 1);
    return Math.min(delay, MAX_MS);
}
```

實測前八次：

```text
B1 退避序列 1s→2s→4s→8s→16s→30s（碰上限後維持）
   實際=[1000, 2000, 4000, 8000, 16000, 30000, 30000, 30000]
```

那個 `Math.min(attempt, 30)` 看起來多餘，其實是整段程式最重要的一行。

如果沒有它，直接寫 `BASE_MS * (1L << (attempt - 1))`，當 `attempts` 一路累加上去，位移就會溢位：

```text
B2 反例——沒夾住指數時退避會歸零或變負（等於完全不等）
   backoffUnsafe(55)=-432345564227567616 / backoffUnsafe(64)=0 毫秒
```

![指數退避與溢位反例：正確版的退避長條依 1s、2s、4s、8s、16s 遞增，第 6 次之後碰到 30 秒上限維持不變；反例則是沒夾住指數時，attempt=55 退避變成 −4.3×10¹⁷ 毫秒、attempt=64 退避變成 0 毫秒，兩者都等於完全不等](https://springai-media.zeabur.app/newsletter-media/images/177a1fb2b5280db206664884a36af8ac4a5d254e4fd09e5eaf5282125d3fd755.png)

負的退避、零的退避，效果一樣：**完全不等，立刻重試**。你以為自己寫了一個愈退愈慢的保護，實際上它會在某個時間點突然變成全速重試風暴——而且是對著一個已經在出問題的下游。

夾住之後就穩定了：

```text
B3 正確版同樣的 attempt 仍回傳上限，不會歸零或變負
   backoffMillis(55)=30000 / backoffMillis(64)=30000 毫秒
```

（順帶一提：如果你有設重試上限——下一段就要講——`attempts` 根本不會長到 55。這兩件事是互相保護的：上限擋住了溢位，夾指數則擋住「有人不小心把上限拿掉」的那一天。）

### 四、死信：承認放棄，而不是無限重試

重試不能永遠試下去。試到某個次數還是失敗，就該停手，把它搬到一個專門的地方：

```java
if (attempts >= MAX_ATTEMPTS) {
    jdbc.update("insert into dead_letters(event_id, attempts, last_error) values (?, ?, ?)",
                eventId, attempts, lastError);
    jdbc.update("delete from event_tasks where event_id = ?", eventId);
}
```

實測（上限設 3 次）：

```text
B4 重試達上限後移入死信，且不再被撈出
   待處理=0 / 死信=1 / 死信中記錄的嘗試次數=3
```

死信表的價值不在於「存放垃圾」，而在於它把一件事變成**看得見的**：

- 有幾筆任務徹底失敗了？`select count(*) from dead_letters`
- 為什麼失敗？`last_error` 裡有
- 誰受影響？`event_id` 查得回去

沒有死信表的系統，失敗的任務要嘛無限重試（消耗資源、洗版 log），要嘛安靜消失。兩種都不好，因為**你不會知道它發生過**。

這也呼應了 E1 講 `@Async` 例外被吞掉時說的那句話：例外沒有消失，只是換了去處。死信表就是替它準備一個你會去看的去處。

**最後一步是人**：死信不該只是躺在那裡。後台給一個列表、一顆「重新處理」按鈕，讓人看過原因之後決定要不要再跑一次。自動化處理不了的事，就明確地交給人——這比假裝系統全自動要誠實得多。

🤖 檢查自己專案的非同步任務有沒有這三層，可以把這句丟給 AI：

```text
找出這個專案裡所有非同步執行的任務（@Async、訊息佇列消費者、排程）。
逐一回答：
1. 這個任務有沒有狀態紀錄？使用者或後台查得到「現在跑到哪」嗎？
2. 失敗時有沒有重試？重試間隔是固定的還是指數退避？有沒有上限？
3. 重試到上限之後，失敗的任務去了哪裡？有沒有地方可以查、可以人工重跑？
把三項都缺的標為高風險。先不要改程式碼。
```

## 下期預告

系列走到這裡，事件的路已經完整了：送得出去、收得下來、回得了前端。

最後一期要換個角度看同一件事。

前幾期我們一直在講「怎麼要求 AI 交出可以被審查的產物」。但反過來——**Agent 自己跑的每一步，誰來審查？**

它讀了哪些資料、用了哪個提示、呼叫了哪些工具、花了多少 token、為什麼最後給出這個答案？如果客戶問「這份摘要是怎麼來的」，你答得出來嗎？

下期，也是這個系列的最後一期：把事件流變成 Agent 的稽核軌跡。

—— 凱文大叔

---

延伸閱讀：

- [Spring Framework：Asynchronous execution 與 TaskExecutor 設定](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [AWS Builders' Library：Timeouts, retries, and backoff with jitter](https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/)
```

---

## 寄送提醒

- **必須在 015 之後寄出**：015 結尾已預告本期（「使用者完全看不到這一切」），本期開頭直接回收該鉤子。
- **單一主題寄出**：「可審查產物」系列已於 015 完結，016 由 EVENT 系列獨挑大樑，採單主題寄出。內文開頭有一句「前四期把事件的路鋪完了」作為新讀者的定位錨點。
- **付費分段**：`<!--paywall-->` 切在「後段：狀態、進度、放棄」之前。
- **程式碼已實跑驗證**：`pwsh newsletter/scripts/verify-016-retry-dlq-java.ps1`（Spring 6.2.12 + H2 2.3.232 + JDK 21）。文中四段實測輸出（B1–B4）直接取自該腳本，**改動任何 Java 片段或實測輸出後必須重跑**。
- **B2 是反例佐證**：腳本同時驗「正確版回傳上限」與「錯誤版歸零或變負」，錯誤版留成迴歸測試，也是文中警語的證據。
- **SSE 片段未列入自動驗證**：`SseEmitter` 那段需要啟動完整 Web 環境，本期驗證腳本未涵蓋，**寄送前請以 009 期既有的 SSE 驗證方式人工確認**，或在測試信中標明該段為節錄示意。
- **驗證腳本刻意不呼叫 LLM**：驗的是退避計算與狀態流轉。
- **兩張示意圖已上傳**：`016-a-status-machine.png`、`016-b-backoff-dlq.png`，文中圖片 URL 已替換為 Zeabur 媒體庫真網址，記錄 hash 已追加至 `uploaded-urls.txt`。
- **工商卡片未填優惠碼**：優惠碼寄送前確認。
- **回收 009 期 SSE**：後段明確提到「這個系列的讀者應該記得 009 期」——若讀者名單有大量新訂閱者，可考慮補一句 SSE 的一行說明。
- **結尾已預告 017（E5，系列完結）**：以「Agent 自己跑的每一步，誰來審查」開鉤子——**017 必須接這條線**。
