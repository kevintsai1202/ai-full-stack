# 章節 3 單元 5｜進階動態查詢

## 單元定位

上一節的 Query Method 在條件固定時很好用，但 CRM 的客戶搜尋是「多個可選條件任意組合」——只填產業、或產業加分級加關鍵字一起篩。本節說明 Query Method 的極限，導入 Spring Data JPA 的 `Specification<T>` 動態查詢機制，並整理 Query Method、Specification、`@Query` JPQL 三者的選用時機。建議時長：15～20 分鐘。

## 教學素材

### Query Method 的限制：條件一多就爆炸

Query Method 命名在條件固定時非常好用，但商業搜尋場景通常有多個「可選過濾條件」：使用者可能同時填名稱與價格上限，也可能只填其中一個，甚至全不填。

Query Method 無法處理「條件可有可無」的動態查詢——你必須為每種組合寫一個方法，或在 Service 層用 if-else 分支呼叫不同查詢，兩種做法維護成本都很高。

```java
// ❌ 反例：每多一個可選條件就要翻倍方法數
public List<Customer> search(String name, Double maxPrice, Boolean inStock) {
    if (name != null && maxPrice != null && inStock != null) {
        return repo.findByNameContainingAndPriceLessThanAndStockGreaterThan(...);
    } else if (name != null && maxPrice != null) {
        return repo.findByNameContainingAndPriceLessThan(...);
    } else if (name != null) {
        return repo.findByNameContaining(name);
    }
    // ... 還有更多分支
}
```

### Specification：動態查詢的正確解法

`Specification<T>` 是 Spring Data JPA 內建的動態查詢機制，核心概念是把每個查詢條件包裝成一個獨立物件，再自由組合。

每個 Specification 本質上是一個 lambda，簽章為 `(root, query, cb) -> Predicate`：`root` 代表 FROM 的 Entity、`cb`（CriteriaBuilder）是 WHERE 條件的工廠。回傳 `null` 就代表「這個條件不套用」，非常適合可選欄位。

- 不需要修改 SQL 字串，只在 Java 程式碼層組合條件
- 每個條件獨立封裝，可單獨測試每一個 Predicate
- `null` 條件自動被 Spring Data 跳過，不會影響查詢語意
- 透過 `.and()` / `.or()` 自由串接，組合結果仍是一個 Specification 物件

### Specification 與 Query Method 的選用時機

- **Query Method**：條件固定、不超過 2 個欄位組合 → 命名直觀、無額外程式碼
- **Specification**：有 1 個以上的可選條件、條件組合數 > 3 → 維護性與可讀性大幅提升
- **`@Query` JPQL**：需要 GROUP BY、子查詢、特殊函數等 Specification 難以表達的語意
- 兩者可以共存於同一個 Repository，依查詢複雜度選用不同方式

## 示範與提示詞

**口語化任務提示詞 — 把資料真正存進資料庫，並支援多條件搜尋［build］**

```text
請把剛才暫存在程式裡的客戶資料，改成真正存進資料庫。另外客戶查詢要能「多個條件任意組合」——例如我可以只用產業篩、也可以產業加分級加關鍵字一起篩。請加中文註解。
```

## 口語稿

上一節結束的時候我留了一個伏筆：Query Method 有極限。這一節我們就從一個真實的需求開始講。想像你是 CRM 的使用者，畫面上有一排搜尋欄位——產業、客戶分級、名稱關鍵字——你可能只填產業，可能填產業加分級，也可能三個都填，甚至全部空白直接按搜尋。每一個條件都是「可有可無」的。這種需求在商業系統裡到處都是，但你用 Query Method 做做看，馬上就會撞牆。

為什麼？因為 Query Method 的條件是寫死在方法名稱裡的。findByIndustry 就是一定要有產業條件，findByIndustryAndLevel 就是兩個條件都要。三個可選條件，排列組合下來你要寫七、八個方法，然後在 Service 層用一大串 if-else 判斷使用者填了哪些欄位、去呼叫對應的方法。你看我放的這個反例——if 名稱不是 null 而且價格不是 null 而且庫存不是 null，就呼叫一個超長名字的方法；不然如果名稱和價格不是 null，就呼叫另一個⋯⋯每多一個可選條件，分支數量直接翻倍。這種程式碼寫的時候痛苦，改的時候更痛苦，是典型的維護災難。

那正解是什麼？Spring Data JPA 其實內建了一套動態查詢機制，叫做 Specification。它的核心概念一句話就能講完：把每一個查詢條件，包裝成一個獨立的物件，再自由組合。「產業等於某某」是一個 Specification，「分級等於某某」是另一個，「名稱包含關鍵字」又是一個。要組合的時候用 .and() 或 .or() 串起來，串完的結果仍然是一個 Specification，丟給 Repository 執行就好。

技術上，每個 Specification 本質是一個 lambda，簽章是 root、query、cb 三個參數，回傳一個 Predicate。你可以這樣理解：root 代表 SQL 裡 FROM 的那個 Entity，你從它身上取欄位；cb 是 CriteriaBuilder，就是製造 WHERE 條件的工廠，equal、like、between 這些條件都跟它要。而它最漂亮的一個設計是：回傳 null 就代表「這個條件不套用」。所以「使用者沒填產業」這件事，處理方式就是那個 Specification 回傳 null，Spring Data 會自動把它跳過，完全不影響查詢語意。剛剛那一大串 if-else，就這樣消失了。而且因為每個條件都是獨立封裝的物件，你可以針對單一 Predicate 寫測試，SQL 字串也完全不用手動拼接。

不過我要強調，Specification 不是要取代 Query Method，這是一個選用時機的問題。我給你三條判斷準則：條件固定、不超過兩個欄位的組合，用 Query Method，命名直觀、零額外程式碼；有一個以上的可選條件、或條件組合數超過三種，用 Specification，維護性和可讀性大幅提升；至於需要 GROUP BY、子查詢、特殊函數這種 Specification 難以表達的語意，就用 @Query 直接寫 JPQL。這三種方式可以共存在同一個 Repository 裡，依查詢複雜度各取所需。

我們現在來實作 CRM 的客戶搜尋。提示詞一樣講需求就好，你聽：「客戶查詢要能多個條件任意組合——例如我可以只用產業篩、也可以產業加分級加關鍵字一起篩。」AI Agent 看到「可選條件、任意組合」這種描述，就會選用 Specification 來實作：Repository 加上對應的擴充介面、為每個條件建立獨立的 Specification、在 Service 層依參數是否為 null 組合條件。程式碼生成之後，你會看到搜尋 API 不管使用者填幾個條件，都是同一個進入點、同一段組合邏輯，乾乾淨淨。實際測一下：只帶產業參數查一次，再帶產業加分級加關鍵字查一次，兩種組合都拿得到正確結果，就代表動態查詢成功了。

總結一下：Query Method 適合固定條件，可選條件任意組合的場景交給 Specification——把條件封裝成物件、null 就跳過、用 and 和 or 自由串接。到這裡，持久層的核心技術都到齊了。下一節，我們把視角拉回主軸專案，把整個 CRM 的資料模型——客戶、商機、活動——完整地對應進資料庫。
