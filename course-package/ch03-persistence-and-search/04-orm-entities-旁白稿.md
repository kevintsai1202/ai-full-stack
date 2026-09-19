# 04-orm-entities｜ORM 及相關類別建立旁白稿

本稿依據 `04-orm-entities.md` 的教學素材與口語稿整理，供人工錄音使用。共 9 段，錄音時可依段落分開保存，方便後續與投影片時間軸對齊。

建議錄音格式：WAV 或 MP3。每段開頭保持自然，結尾保留約 0.5 秒空白。

## 01｜為什麼需要 ORM

建議檔名：`01-why-orm.wav`

資料表建好了、連線也通了，現在只剩最後一哩路：讓 Java 程式真正對資料庫讀寫。

先講「為什麼」需要 ORM。如果沒有它，每查一次客戶都要手寫 SQL、再把結果逐欄塞回 Java 物件，每張表、每個查詢都重複這套苦工。JPA 的價值就在這裡——讓你用「物件」的角度思考資料：Customer 類別對應 customers 資料表，一個物件就是一筆資料，存取交給框架翻譯成 SQL。這不是說 SQL 不重要，而是常見的 CRUD 可以交給更高階的抽象處理。

## 02｜Entity：對應資料表的類別

建議檔名：`02-entity-design.wav`

要建的類別核心是兩個：Entity 和 Repository。Entity 就是「對應資料表的類別」：@Entity 宣告對應資料表、@Table 指定表名、@Id 加主鍵生成策略、@Column 描述欄位規則。

設計原則是：欄位型別和 nullable 規則一定要和 Schema 一致。因為我們的 ddl-auto 是 validate，對不上就直接啟動失敗——這其實是好事，錯誤越早爆出來越好。

## 03｜Lombok 與 JPA 的搭配

建議檔名：`03-lombok-noargs.wav`

這裡有一個 Lombok 的細節：Entity 上一定要加 @NoArgsConstructor。

為什麼？因為 JPA 從資料庫讀資料時，是先用無參建構子生出一個空物件，再逐欄把值填進去。沒有無參建構子，它連物件都建不出來。

另外，VS Code 如果看到 Lombok 的 @Data 這些註解出現紅線，不用另外裝外掛，確認 Java 擴充套件的內建 Lombok 支援有開啟就好。

## 04｜Repository 與 Query Method

建議檔名：`04-repository-query-method.wav`

第二個角色是 Repository。你只要宣告一個介面繼承 JpaRepository，什麼實作都不用寫，就立刻擁有 save、findById、findAll、deleteById、count 這些現成方法。

更神奇的是 Query Method：宣告一個方法叫 findByNameContainingIgnoreCase，Spring Data 會解析這個名字——find 是 SELECT、By 接 WHERE、Name 對應欄位、Containing 翻成 LIKE、IgnoreCase 套 LOWER——自動翻譯成 SQL。方法名稱本身就是查詢語意，你完全不用寫 SQL。

## 05｜交易規則：@Transactional

建議檔名：`05-transactional.wav`

接下來兩個實戰規則。第一個是交易：寫入方法一定要加 @Transactional，不然新增、修改、刪除做到一半失敗，資料會處於不一致的狀態。查詢方法則加 readOnly = true，告訴 JPA 不需要做變更追蹤，大量查詢時效能有感提升。

實務建議是：在 Service 類別上標 @Transactional(readOnly = true) 當預設，個別寫入方法再覆寫成可寫入。而且標註要放在 Service 層，不放 Controller——交易邊界是業務邏輯的事，不是接口層的事。

## 06｜@Modifying 與批次操作

建議檔名：`06-modifying.wav`

第二個規則是 @Modifying。Repository 的派生方法像 save、deleteById，交易邏輯 Spring Data 都處理好了。但如果你用 @Query 自己寫 JPQL 的 UPDATE 或 DELETE，Spring Data 預設會把它當成 SELECT 處理，必須額外加上 @Modifying 才能執行。

缺了它會拋出一個例外，訊息長得很像 SQL 語法錯誤，非常容易誤判方向。選用時機也給你：萬筆以上的批次刪除、批次更新，用 @Modifying 加 @Query 效率最好；需要觸發生命週期事件就用派生刪除方法；一般單筆刪除，deleteById 就夠了。

## 07｜JPA Auditing 自動記錄時間

建議檔名：`07-jpa-auditing.wav`

還有一個正式系統必備的東西：audit 欄位，created_at 和 updated_at。手動設定既容易漏，又讓業務邏輯摻雜技術細節。JPA Auditing 可以全自動：啟動類別加 @EnableJpaAuditing，把兩個欄位抽成 BaseAuditEntity 父類別，用 @MappedSuperclass 標記——它本身不對應資料表，只把欄位定義繼承給子類別——再加上 @EntityListeners、@CreatedDate、@LastModifiedDate。之後任何 Entity 繼承它，建立時間、修改時間就自動填好。

注意一件事：Entity 多了這兩欄，Flyway 也要補對應的遷移腳本，不然 validate 會因為欄位不符而啟動失敗。

## 08｜實際改造：Controller 一行不動

建議檔名：`08-refactor-with-ai.wav`

我們現在來實際改造。提示詞拆三步交給 AI Agent，完整原文在教學網站上：第一步，pom.xml 加入 data-jpa、postgresql、lombok 依賴；第二步，把 Customer 從普通 POJO 升級成 Entity；第三步，建立 CustomerRepository，更新 CustomerService，把原本的 List 換成注入 Repository，getAll、findById、save 全部改走資料庫。

你會發現一件很漂亮的事：Controller 一行都不用動。上一章有好好分層，底層從記憶體換成資料庫，Controller 完全無感——這就是分層架構的回報。最後執行 mvn spring-boot:run，確認啟動成功、連線正常。

## 09｜本節總結與下一節

建議檔名：`09-closing.wav`

總結一下：Entity 對應資料表、Repository 提供現成 CRUD 和 Query Method、交易規則和 audit 欄位讓系統達到正式水準，而 Controller 零改動。

不過 Query Method 有極限——當搜尋條件「可有可無、任意組合」的時候，方法名稱會爆炸。下一節，我們來看動態查詢的正解：Specification。

## 邊念邊操作提示卡

1. 先左右並排 migration 和 Entity，從 `@Table`、`@Id`、欄位型別開始逐項對照。
2. 打開關聯 Entity，游標停在 owning side、lazy 與 cascade；念到每一個決策時補一句「這會影響什麼」。
3. 執行 repository test，示範寫入、重開 transaction 查回與序列化 response；讓 SQL 或測試輸出留在畫面上。
4. 若出現 lazy 或循環 JSON 問題，保留錯誤並示範回到 transaction、mapper 或 DTO 邊界診斷。

## 交付檔案命名

```text
01-why-orm.wav
02-entity-design.wav
03-lombok-noargs.wav
04-repository-query-method.wav
05-transactional.wav
06-modifying.wav
07-jpa-auditing.wav
08-refactor-with-ai.wav
09-closing.wav
```
