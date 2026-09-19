# 06-crm-data-model-integration｜CRM 資料模型整合進資料庫旁白稿

本稿依據 `06-crm-data-model-integration.md` 的教學素材與口語稿整理，供人工錄音使用。共 8 段，錄音時可依段落分開保存，方便後續與投影片時間軸對齊。

建議錄音格式：WAV 或 MP3。每段開頭保持自然，結尾保留約 0.5 秒空白。

## 01｜回到主軸專案

建議檔名：`01-back-to-crm.wav`

前面五節，我們一路把技術積木疊起來：Docker 跑資料庫、Flyway 管結構、JPA 做映射、Specification 做動態查詢。但到目前為止，我們主要拿 Customer 一個類別在練功。

這一節要做的事，是把章節二設計的整套 CRM Domain Model，完整地搬進資料庫。為什麼這一步值得獨立一節？因為單一 Entity 誰都會寫，真正的設計功力是展現在「多個 Entity 之間的關聯」上——客戶底下有商機、有活動紀錄，這些關係怎麼對應成 JPA 的設計，決定了系統之後好不好查、好不好擴充。

## 02｜Customer Entity

建議檔名：`02-customer-entity.wav`

我們來看三個核心 Entity。第一個是 Customer，客戶。核心欄位有 name、industry 產業類型、contractStatus 合約狀態、還有 contractEndDate 合約到期日。

注意 contractStatus 我們用 Enum 來做，不用字串——因為合約狀態是一組有限的值，用 Enum 可以在編譯期就擋掉亂七八糟的輸入。關聯的部分，Customer 是「一」的那一方：一個客戶底下有多筆 Opportunity、多筆 Activity、多筆 Task，都是一對多。

## 03｜Opportunity Entity

建議檔名：`03-opportunity-entity.wav`

第二個是 Opportunity，商機，這是業務最關心的物件：title 商機名稱、amount 預估金額、probability 成交機率、expectedCloseDate 預期成交日，還有一個 stage 欄位，一樣是 Enum，值是 PROSPECTING、QUALIFICATION、PROPOSAL、CLOSING、WON、LOST——從初步接觸、資格確認、提案、收尾，到最後贏單或丟單，這就是一條銷售管線的完整階段。

Opportunity 對 Customer 是多對一：多筆商機屬於同一個客戶。

## 04｜Activity 與 summary 伏筆

建議檔名：`04-activity-summary.wav`

第三個是 Activity，活動紀錄。type 也是 Enum：MEETING、CALL、EMAIL、VISIT，開會、電話、郵件、拜訪；occurredAt 記錄發生時間。

然後是我要你特別畫重點的欄位——summary，活動摘要文字。現在看它就是一個普通的文字欄位，業務寫「今天拜訪客戶，對方對新方案有興趣但擔心預算」這類紀錄。但到了 Day 3、Day 4，這個欄位會被 ETL 流程向量化，存進 pgvector，變成 AI 的長期記憶——AI 助理之所以能回答「這個客戶最近在關心什麼」，靠的就是這些摘要。這也呼應了第一節為什麼一開始就選帶 pgvector 的映像，每一步都是在為後面鋪路。

## 05｜三個設計重點

建議檔名：`05-design-points.wav`

設計上還有三個重點。第一，這三個 Entity 全部繼承上一節做好的 BaseAuditEntity，每筆客戶、商機、活動的建立時間和修改時間自動記錄，不用寫任何維護程式碼。

第二，搜尋用 Specification 支援「產業類型加合約狀態加商機金額範圍」的動態組合——這正是上一節練的招式，直接用在真實業務場景。

第三，提醒持久層的心法：JPA 的關聯映射要注意延遲載入和 N+1 查詢問題。一個客戶列表頁如果每一列都多發一次查詢去撈商機，效能會很難看，這是關聯設計時要放在心上的事。

## 06｜驗收：重開資料還在

建議檔名：`06-verify-persistence.wav`

我們現在來做最重要的一件事——驗收。這一章開頭我就說過，驗證重點是兩句話：「重開資料還在、沒有重複建表」。

我把驗證也寫成提示詞交給 AI Agent，原文在教學網站上：「請幫我確認資料確實存進了資料庫：新增一筆客戶後查得到；把專案重開後，那筆資料還在；而且重開時沒有發生重複建表之類的錯誤。」你會看到 AI Agent 先呼叫 API 新增一筆客戶、查詢確認拿得到；然後把 Spring Boot 停掉、重新啟動——這是關鍵時刻——再查一次，那筆客戶還在。這代表資料真的落地了，不再是活在記憶體裡。

## 07｜三個檢查點都綠燈

建議檔名：`07-three-green-checks.wav`

同時看啟動 log：Flyway 說 Schema 已是最新、沒有重複執行任何腳本；validate 也通過，代表 Entity 和資料表結構完全吻合。

新增查得到、重開還在、沒有重複建表——三個檢查點都綠燈，這一章的目標就達成了。

## 08｜本章總結與下一章預告

建議檔名：`08-closing.wav`

總結一下：這一節我們把 Customer、Opportunity、Activity 三個 Entity，連同關聯、Enum、audit 欄位完整落進資料庫，並且通過了「重開資料還在、沒有重複建表」的驗收。CRM 的資料底座，從此是真材實料的。

接下來先交給你：作業一會請你把整套流程在自己的專案上完整跑一遍。而資料落地之後，下一個問題馬上就來了——這些客戶資料，誰有資格看、誰有資格改？下一章我們就進入安全性的世界，用 Spring Security 和 JWT 把系統的大門守好。我們下一章見。

## 邊念邊操作提示卡

1. 先顯示 ER 圖，依序指出 Customer、Contact、Opportunity、Interaction 和外鍵，再切到 migration。
2. 按 migration、Entity、Repository、Service、API 的順序操作；每完成一層就執行一次測試，保留失敗點。
3. 建立三筆客戶資料，走過新增機會、更新狀態、查詢待跟進；旁白要等 response 回來再進下一步。
4. 最後展示錯誤外鍵或重複資料案例，說明下一章會加入 OpenAPI、例外和 JWT，並保存 API 腳本。

## 交付檔案命名

```text
01-back-to-crm.wav
02-customer-entity.wav
03-opportunity-entity.wav
04-activity-summary.wav
05-design-points.wav
06-verify-persistence.wav
07-three-green-checks.wav
08-closing.wav
```
