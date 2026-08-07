# 章節 3 單元 6｜CRM 資料模型整合進資料庫

## 單元定位

前五節把 Docker、Flyway、JPA、Specification 的技術基礎都備齊了，本節回到主軸專案：把章節 2 設計的 CRM Domain Model 完整對應成 JPA Entity——Customer、Opportunity、Activity 三個核心 Entity、彼此的關聯、Enum 欄位設計，全部繼承 `BaseAuditEntity`，並用 Specification 支撐業務端的多條件搜尋。這一節也為 Day 3～4 的 AI 功能埋下伏筆：Activity 的 `summary` 欄位之後會被向量化存入 pgvector。建議時長：15～20 分鐘。

## 教學素材

### CRM 資料模型如何對應 JPA Entity

將前面章節介紹的 CRM Domain Model 對應到 JPA Entity 設計：

**Customer Entity**

核心欄位：`name`、`industry`（產業類型）、`contractStatus`（合約狀態，Enum）、`contractEndDate`。一對多關聯到 Opportunity、Activity、Task。

**Opportunity Entity**

核心欄位：`title`、`amount`（預估金額）、`probability`（成交機率）、`stage`（Enum：PROSPECTING / QUALIFICATION / PROPOSAL / CLOSING / WON / LOST）、`expectedCloseDate`。多對一關聯到 Customer。

**Activity Entity**

核心欄位：`type`（Enum：MEETING / CALL / EMAIL / VISIT）、`summary`（摘要文字，將被向量化）、`occurredAt`。多對一關聯到 Customer。

**設計重點**

- 所有 Entity 繼承 `BaseAuditEntity` 自動記錄建立/修改時間
- 用 Specification 支援「依產業類型 + 合約狀態 + 商機金額範圍」的動態組合查詢
- Activity 的 `summary` 欄位將在 Day 3~4 被 ETL 向量化，存入 pgvector 作為 AI 長期記憶

（持久層心法回顧：Flyway 保證資料庫結構的可追溯性；JPA Mapping 需注意延遲載入（Lazy Load）與 N+1 查詢問題；動態查詢透過 Specification 保持代碼優雅與靈活。）

## 示範與提示詞

**驗證提示詞 — 資料真的進資料庫、重開也還在［verify］**

```text
請幫我確認資料確實存進了資料庫：新增一筆客戶後查得到；把專案重開後，那筆資料還在；而且重開時沒有發生「重複建表」之類的錯誤。
```

## 口語稿

前面五節，我們一路把技術積木疊起來：Docker 跑資料庫、Flyway 管結構、JPA 做映射、Specification 做動態查詢。但到目前為止，我們主要拿 Customer 一個類別在練功。這一節要做的事，是把章節二設計的整套 CRM Domain Model，完整地搬進資料庫。為什麼這一步值得獨立一節來講？因為單一 Entity 誰都會寫，真正的設計功力是展現在「多個 Entity 之間的關聯」上——客戶底下有商機、有活動紀錄，這些關係怎麼對應成 JPA 的設計，決定了這套系統之後好不好查、好不好擴充。

我們來看三個核心 Entity。第一個是 Customer，客戶。核心欄位有 name、industry 產業類型、contractStatus 合約狀態、還有 contractEndDate 合約到期日。注意 contractStatus 我們用 Enum 來做，不用字串——因為合約狀態是一組有限的值，用 Enum 可以在編譯期就擋掉亂七八糟的輸入。關聯的部分，Customer 是「一」的那一方：一個客戶底下有多筆 Opportunity、多筆 Activity、多筆 Task，都是一對多。

第二個是 Opportunity，商機。這是業務最關心的物件：title 商機名稱、amount 預估金額、probability 成交機率、expectedCloseDate 預期成交日，還有一個 stage 欄位，一樣是 Enum，值是 PROSPECTING、QUALIFICATION、PROPOSAL、CLOSING、WON、LOST——從初步接觸、資格確認、提案、收尾，到最後贏單或丟單，這就是一條銷售管線的完整階段。Opportunity 對 Customer 是多對一：多筆商機屬於同一個客戶。

第三個是 Activity，活動紀錄。type 也是 Enum：MEETING、CALL、EMAIL、VISIT，開會、電話、郵件、拜訪；occurredAt 記錄發生時間；然後是我要你特別畫重點的欄位——summary，活動摘要文字。現在看它就是一個普通的文字欄位，業務寫「今天拜訪客戶，對方對新方案有興趣但擔心預算」這類的紀錄。但是到了 Day 3、Day 4，這個欄位會被 ETL 流程向量化，存進 pgvector，變成 AI 的長期記憶——AI 助理之所以能回答「這個客戶最近在關心什麼」，就是靠這些摘要。這也呼應了我們第一節為什麼一開始就選帶 pgvector 的資料庫映像，每一步都是在為後面鋪路。

設計上還有三個重點。第一，這三個 Entity 全部繼承上一節做好的 BaseAuditEntity，每筆客戶、商機、活動的建立時間和修改時間自動記錄，不用寫任何一行維護程式碼。第二，搜尋用 Specification 支援「產業類型加合約狀態加商機金額範圍」的動態組合——這正是上一節練的招式，直接用在真實業務場景。第三，提醒一下持久層的心法：JPA 的關聯映射要注意延遲載入和 N+1 查詢問題，一個客戶列表頁如果每列都多發一次查詢去撈商機，效能會很難看，這是關聯設計時要放在心上的事。

我們現在來做最重要的一件事——驗收。這一章開頭我就說過，驗證重點是兩句話：「重開資料還在、沒有重複建表」。我把驗證也寫成提示詞交給 AI Agent，你聽聽看：「請幫我確認資料確實存進了資料庫：新增一筆客戶後查得到；把專案重開後，那筆資料還在；而且重開時沒有發生重複建表之類的錯誤。」你會看到 AI Agent 先呼叫 API 新增一筆客戶、查詢確認拿得到；然後把 Spring Boot 停掉、重新啟動——這是關鍵時刻——再查一次，那筆客戶還在。這代表資料真的落地了，不再是活在記憶體裡。同時看啟動 log，Flyway 說 Schema 已是最新、沒有重複執行任何腳本，validate 也通過，代表 Entity 和資料表結構完全吻合。三個檢查點都綠燈，這一章的目標就達成了。

總結一下：這一節我們把 Customer、Opportunity、Activity 三個 Entity 連同關聯、Enum、audit 欄位完整落進資料庫，並且通過了「重開資料還在、沒有重複建表」的驗收。CRM 的資料底座從此是真材實料的。接下來就交給你了——作業一會請你把整套流程在自己的專案上完整跑一遍，我們在作業說明見。
