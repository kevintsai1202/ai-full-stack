# 05-dynamic-query｜進階動態查詢旁白稿

本稿依據 `05-dynamic-query.md` 的教學素材與口語稿整理，供人工錄音使用。共 7 段，錄音時可依段落分開保存，方便後續與投影片時間軸對齊。

建議錄音格式：WAV 或 MP3。每段開頭保持自然，結尾保留約 0.5 秒空白。

## 01｜真實需求：條件可有可無

建議檔名：`01-real-world-search.wav`

上一節結束時我留了一個伏筆：Query Method 有極限。這一節我們就從一個真實的需求開始講。

想像你是 CRM 的使用者，畫面上有一排搜尋欄位——產業、客戶分級、名稱關鍵字——你可能只填產業，可能填產業加分級，也可能三個都填，甚至全部空白直接按搜尋。每一個條件都是「可有可無」的。這種需求在商業系統裡到處都是，但你用 Query Method 做做看，馬上就會撞牆。

## 02｜Query Method 為什麼撞牆

建議檔名：`02-query-method-limit.wav`

為什麼？因為 Query Method 的條件是寫死在方法名稱裡的。findByIndustry 就是一定要有產業條件，findByIndustryAndLevel 就是兩個條件都要。

三個可選條件，排列組合下來你要寫七、八個方法，然後在 Service 層用一大串 if-else 判斷使用者填了哪些欄位、去呼叫對應的方法。每多一個可選條件，分支數量直接翻倍。這種程式碼寫的時候痛苦，改的時候更痛苦，是典型的維護災難。

## 03｜Specification：把條件變成物件

建議檔名：`03-specification-concept.wav`

那正解是什麼？Spring Data JPA 內建了一套動態查詢機制，叫做 Specification。它的核心概念一句話就能講完：把每一個查詢條件，包裝成一個獨立的物件，再自由組合。

「產業等於某某」是一個 Specification，「分級等於某某」是另一個，「名稱包含關鍵字」又是一個。要組合的時候用 .and() 或 .or() 串起來，串完的結果仍然是一個 Specification，丟給 Repository 執行就好。

## 04｜root、cb 與 null 的妙用

建議檔名：`04-lambda-and-null.wav`

技術上，每個 Specification 本質是一個 lambda，接 root、query、cb 三個參數，回傳一個 Predicate。你可以這樣理解：root 代表 SQL 裡 FROM 的那個 Entity，你從它身上取欄位；cb 是 CriteriaBuilder，製造 WHERE 條件的工廠，equal、like、between 都跟它要。

而它最漂亮的設計是：回傳 null 就代表「這個條件不套用」。所以「使用者沒填產業」這件事，處理方式就是那個 Specification 回傳 null，Spring Data 會自動跳過它，完全不影響查詢語意。剛剛那一大串 if-else，就這樣消失了。而且每個條件都是獨立封裝的物件，你可以針對單一 Predicate 寫測試，SQL 字串完全不用手動拼接。

## 05｜三種查詢方式的選用時機

建議檔名：`05-when-to-use-what.wav`

不過我要強調，Specification 不是要取代 Query Method，這是選用時機的問題。三條判斷準則：

條件固定、不超過兩個欄位的組合，用 Query Method，命名直觀、零額外程式碼。有一個以上的可選條件、或條件組合數超過三種，用 Specification，維護性和可讀性大幅提升。需要 GROUP BY、子查詢、特殊函數這種 Specification 難以表達的語意，就用 @Query 直接寫 JPQL。

這三種方式可以共存在同一個 Repository 裡，依查詢複雜度各取所需。

## 06｜實作 CRM 客戶搜尋

建議檔名：`06-build-with-ai.wav`

我們現在來實作 CRM 的客戶搜尋。提示詞一樣講需求就好，原文在教學網站上：「客戶查詢要能多個條件任意組合——例如我可以只用產業篩、也可以產業加分級加關鍵字一起篩。」

AI Agent 看到「可選條件、任意組合」這種描述，就會選用 Specification 來實作：Repository 加上對應的擴充介面、為每個條件建立獨立的 Specification、在 Service 層依參數是否為 null 組合條件。生成之後你會看到，搜尋 API 不管使用者填幾個條件，都是同一個進入點、同一段組合邏輯。驗收方式：只帶產業參數查一次，再帶產業加分級加關鍵字查一次，兩種組合都拿到正確結果，就代表動態查詢成功了。

## 07｜本節總結與下一節

建議檔名：`07-closing.wav`

總結一下：Query Method 適合固定條件，可選條件任意組合的場景交給 Specification——把條件封裝成物件、null 就跳過、用 and 和 or 自由串接。

到這裡，持久層的核心技術都到齊了。下一節，我們把視角拉回主軸專案，把整個 CRM 的資料模型——客戶、商機、活動——完整地對應進資料庫。

## 邊念邊操作提示卡

1. 先顯示查詢條件矩陣，逐個輸入關鍵字、狀態、日期、排序與分頁；每加一項就執行一次測試。
2. 示範空條件、組合條件、日期邊界與無結果，停在 response 讓學員看 total、page 和順序。
3. 打開 SQL log 或 explain，指出參數 binding 和索引觀察；不要用口頭宣稱「一定很快」。
4. 結尾把測試矩陣和結果存檔，說明下一單元會將查詢接回完整 CRM 模型。

## 交付檔案命名

```text
01-real-world-search.wav
02-query-method-limit.wav
03-specification-concept.wav
04-lambda-and-null.wav
05-when-to-use-what.wav
06-build-with-ai.wav
07-closing.wav
```
