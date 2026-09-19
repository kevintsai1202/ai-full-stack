# 章節 3 單元 4｜ORM 及相關類別建立

## 單元定位

資料庫環境已就緒，本節把程式碼這一端接上：理解 ORM 與 JPA 在專案中的角色，把上一章的 Customer POJO 升級成 `@Entity`，建立 `CustomerRepository`，讓 CustomerService 從記憶體 List 換成真正的資料庫操作——Controller 完全不用改。同時涵蓋 Lombok 搭配 JPA 的注意事項、`@Transactional` 交易規則、`@Modifying` 批次操作，以及用 JPA Auditing 自動記錄建立／修改時間。建議時長：25～30 分鐘。

## 教學素材

### JPA 解決了什麼問題

如果每一次存取資料都要手寫 SQL、手動把結果塞回 Java 物件，開發與維護成本會很高。JPA 的價值，是讓你以物件模型思考資料，而不是每次都回到低階映射。這不表示 SQL 不重要，而是代表常見 CRUD 與查詢可以交給更高階的抽象處理。

### Entity 設計要點

- `@Entity` 表示這個類別要對應資料表
- `@Id` 與主鍵生成策略決定資料識別方式
- 欄位型別與 nullable 規則要與資料庫 Schema 一致
- 註解與欄位命名一旦混亂，後續查詢與維護成本會快速升高

### Lombok 與 JPA 的搭配注意事項

在 JPA Entity 中使用 Lombok 時，有一個額外要求：`@NoArgsConstructor` 是必要的，因為 JPA 在從資料庫讀取資料時，需要先用無參建構子建立物件實例，再逐欄填入資料。

VS Code 中若發現 Lombok 的 `@Data` 等註解出現紅線，請確認 Java 擴充套件已啟用內建 Lombok 支援（不需要另裝獨立外掛）。

### Repository 與 Query Method

`JpaRepository` 提供大量現成的 CRUD 能力，讓你不需要為每個模組都重寫基礎存取程式碼。當查詢需求足夠單純時，甚至可以直接透過方法命名表達條件。Spring Data JPA 會解析介面方法名稱，把它翻譯成 SQL：

- `find` — SELECT 操作
- `By` — WHERE 條件起點
- `Name` — 對應 `name` 欄位
- `Containing` — 轉為 LIKE '%value%'
- `IgnoreCase` — 不區分大小寫（LOWER() 函數）

```java
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // 翻譯為：SELECT * FROM customers WHERE LOWER(name) LIKE LOWER('%name%')
    List<Customer> findByNameContainingIgnoreCase(String name);

    // JpaRepository 繼承後立即擁有的方法（不需要自己寫）：
    // save(entity)        — INSERT 或 UPDATE
    // findById(id)        — SELECT WHERE id = ?
    // findAll()           — SELECT * FROM table
    // deleteById(id)      — DELETE WHERE id = ?
    // count()             — SELECT COUNT(*)
}
```

### @Transactional 核心規則

- **寫入方法一定要加 `@Transactional`**：新增、修改、刪除若沒有包在交易中，部分失敗時資料會處於不一致狀態
- **查詢方法加 `readOnly = true`**：告知 JPA 不需要做變更追蹤（Dirty Checking），對大量查詢有明顯效能提升
- **標註應加在 Service 層**：Controller 層不應直接管理交易邊界
- **建議在 Service 類別上加 `@Transactional(readOnly = true)`**，個別寫入方法再覆寫為 `@Transactional`

```java
@Service
@Transactional(readOnly = true)  // 預設唯讀
public class CustomerService {
    public List<Customer> findAll() { ... }  // 繼承 readOnly

    @Transactional  // 覆寫為可寫入
    public Customer create(Customer c) { ... }
}
```

### @Modifying 與批次操作

Repository 的派生方法（如 `save()`、`deleteById()`）已由 Spring Data 內部處理好交易邏輯。但若用 `@Query` 自行撰寫 JPQL 的 UPDATE 或 DELETE，Spring Data JPA 預設把它當成 SELECT 語句對待，必須額外加上 `@Modifying` 才能正確執行。缺少 `@Modifying` 時會拋出 `InvalidDataAccessApiUsageException`（訊息為「Executing an update/delete query」），容易讓人誤以為是 SQL 語法問題。

- `@Modifying` — 告知 Spring Data 這個 `@Query` 是寫入操作，不是查詢
- `@Transactional` — 寫入操作仍需交易包覆；兩個標註缺一不可，順序不影響結果

選用時機：

- **批次效率**（萬筆以上）→ `@Modifying` + `@Query DELETE`，不載入 Entity，效率高
- **需要觸發生命週期事件**（`@PreRemove`、`@EntityListeners`）→ 用派生刪除方法，逐筆經過 Hibernate 管理
- **一般單筆刪除** → `deleteById()`
- **批次更新** → 幾乎都用 `@Modifying` + `@Query UPDATE`，派生方法做不到批次更新

### Audit 欄位：自動記錄建立與修改時間

正式應用中的資料表幾乎都需要 `created_at`、`updated_at`。Spring Data JPA 的 **JPA Auditing** 可以讓這兩個欄位完全自動填入：

- 在啟動類別加上 `@EnableJpaAuditing` 啟用整個機制
- 在 Entity 或共用父類別加上 `@EntityListeners(AuditingEntityListener.class)`
- 用 `@CreatedDate` / `@LastModifiedDate` 標記對應欄位

```java
@SpringBootApplication
@EnableJpaAuditing  // 啟用 JPA Auditing，應用程式啟動時生效
public class LearnSpringApplication {
    public static void main(String[] args) {
        SpringApplication.run(LearnSpringApplication.class, args);
    }
}
```

### BaseAuditEntity 設計與繼承

多個 Entity 都需要 audit 欄位時，抽成 `BaseAuditEntity` 讓所有 Entity 繼承。`@MappedSuperclass` 表示這個類別本身不對應任何資料表，只把欄位定義「繼承」給子類別的資料表。

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)  // 監聽 @PrePersist / @PreUpdate 事件
public abstract class BaseAuditEntity {

    // 建立時間：@PrePersist 時由 Spring 自動填入，之後不允許修改
    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    // 最後修改時間：每次 @PreUpdate 時自動更新
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
```

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "customers")
public class Customer extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String level;

    @Column(nullable = false)
    private String email;

    private String phone;

    private String industry;

    // created_at 與 updated_at 從 BaseAuditEntity 繼承，不需要重複宣告
}
```

注意：Entity 繼承 `BaseAuditEntity` 後，資料表自動多出 `created_at` 與 `updated_at` 兩欄，記得補上對應的 Flyway 遷移腳本，否則 `ddl-auto: validate` 會因欄位不符而啟動失敗。

## 示範與提示詞

**AI Agent 提示詞 — 為 Spring MVC 專案加入 JPA**

```text
【步驟一：確認依賴、打開資料庫設定】
我在 1-2 建立了一個 Spring Boot 4.1.x 專案，pom.xml 已有 web、validation、data-jpa、postgresql、flyway 依賴，
請幫我確認並補齊：
- spring-boot-starter-data-jpa、spring-boot-starter-flyway 與 flyway-database-postgresql、postgresql（runtime）都在
- lombok（若尚未加入）
- 把第一章為了能啟動而排除的資料庫自動設定拿掉，改用 application.yml 的 datasource 設定

【步驟二：升級四個 Entity】
我目前的 Customer、Contact、Interaction、Opportunity 只是普通 POJO，
請幫我加上 JPA 註解（@Entity、@Table、@Id、@GeneratedValue、@Column、@Enumerated(EnumType.STRING)），
關聯用 Customer 對三者的 @OneToMany（cascade ALL、orphanRemoval）與對應的 @ManyToOne，
再抽一個 AuditableEntity（created_at、updated_at、created_by、updated_by，用 @EnableJpaAuditing 自動填）給四個 Entity 繼承，
並改用 Lombok 的 @Getter、@Setter、@NoArgsConstructor 取代手寫 getter/setter（Entity 不要用 @Data，避免關聯欄位的 equals/hashCode 迴圈）。
欄位名稱、enum 值必須與 Flyway V1 的資料表完全一致，讓 ddl-auto: validate 通過。

【步驟三：建立 Repository 並更新 Service】
請幫我：
1. 建立 CustomerRepository.java，繼承 JpaRepository<Customer, Long> 與 JpaSpecificationExecutor<Customer>；Contact、Interaction、Opportunity 也各建一個 Repository
2. 更新 CustomerService.java，把原本的 List<Customer> 換成注入 CustomerRepository，
   讓查詢、findById、save 方法改用資料庫操作；Controller 與 DTO 完全不動

完成後請執行 mvn spring-boot:run，確認應用程式能啟動、Flyway 遷移成功並通過 validate。
```

## 逐步操作與驗收

### 建立可預期的 Entity 映射

1. 先從 migration 反推 Entity：確認 `@Entity`、`@Table`、`@Id`、生成策略、欄位型別、nullable 與 unique constraint，不要讓 Hibernate 自動猜出與資料庫不同的結構。
2. 逐一實作 Customer、Contact、Opportunity、Interaction 的欄位與關聯，特別檢查 `@ManyToOne` 的 owning side、lazy loading、cascade 與 orphan removal 是否真的符合生命週期。
3. 用 repository test 建立、查詢、更新與刪除一筆資料，再重開 transaction 查回來，確認不是只在記憶體中成功；檢查 SQL log 是否出現非預期 N+1。
4. Entity 和 API response 之間使用 DTO 或 mapper，實際序列化一筆含關聯的資料，確認不會循環 JSON 或暴露內部 audit 欄位。

### 預期結果與證據

- Entity metadata 與 migration 對得上，CRUD test 通過；關聯查詢有明確策略，JSON 輸出不含 lazy proxy 或循環結構。
- 交付 Entity-to-table 對照表、repository test、SQL/查詢觀察與 DTO response 範例。

### 失敗分流與銜接

- schema-validation 失敗先比對欄位型別、長度、nullable 與表名；LazyInitializationException 則追查 transaction 邊界，不要直接改成 eager 全載入。
- 下一單元會在 repository 上加入動態條件，先確認基本單筆 CRUD 的基線。

## 口語稿

資料表建好了、連線也通了，現在只剩最後一哩路：讓 Java 程式真正對資料庫讀寫。先講「為什麼」需要 ORM。如果沒有它，每查一次客戶都要手寫 SQL、再把結果逐欄塞回 Java 物件，每張表、每個查詢都重複這套苦工。JPA 的價值就在這裡——讓你用「物件」的角度思考資料：Customer 類別對應 customers 資料表，一個物件就是一筆資料，存取交給框架翻譯成 SQL。這不是說 SQL 不重要，而是常見的 CRUD 可以交給更高階的抽象處理。

要建的類別核心是兩個：Entity 和 Repository。Entity 就是「對應資料表的類別」：@Entity 宣告對應資料表、@Table 指定表名、@Id 加主鍵生成策略、@Column 描述欄位規則。原則是：欄位型別和 nullable 規則一定要和 Schema 一致——ddl-auto 是 validate，對不上就直接啟動失敗，這其實是好事，錯誤越早爆出來越好。另外一個 Lombok 細節：Entity 上一定要加 @NoArgsConstructor，因為 JPA 讀資料時是先用無參建構子生出空物件再逐欄填值，沒有它連物件都建不出來。VS Code 看到 Lombok 註解紅線，確認 Java 擴充套件的內建 Lombok 支援有開啟就好。

第二個角色是 Repository。宣告一個介面繼承 JpaRepository，什麼實作都不用寫，就立刻擁有 save、findById、findAll、deleteById、count 這些現成方法。更神奇的是 Query Method：宣告一個方法叫 findByNameContainingIgnoreCase，Spring Data 會解析名字——find 是 SELECT、By 接 WHERE、Name 對應欄位、Containing 翻成 LIKE、IgnoreCase 套 LOWER——自動翻譯成 SQL。方法名稱本身就是查詢語意。

再來兩個實戰規則。第一，交易：寫入方法一定要加 @Transactional，不然做到一半失敗，資料會不一致；查詢方法加 readOnly = true，省掉變更追蹤，大量查詢效能有感提升。建議在 Service 類別上標 @Transactional(readOnly = true) 當預設，寫入方法再覆寫；標註放 Service 層，不放 Controller。第二，@Modifying：用 @Query 自己寫 UPDATE 或 DELETE 時，Spring Data 預設當 SELECT 處理，必須加 @Modifying 才能執行，不然丟出的例外訊息長得很像 SQL 語法錯誤，容易誤判。批次刪除、批次更新用 @Modifying 加 @Query 效率最好；單筆刪除用 deleteById 就夠。

還有一個正式系統必備的東西：audit 欄位，created_at 和 updated_at。手動設定既容易漏、又讓業務邏輯摻雜技術細節。JPA Auditing 可以全自動：啟動類別加 @EnableJpaAuditing，把兩個欄位抽成 BaseAuditEntity 父類別，用 @MappedSuperclass 標記——它不對應資料表，只把欄位繼承給子類別——加上 @EntityListeners、@CreatedDate、@LastModifiedDate，之後任何 Entity 繼承它，時間就自動填好。注意 Entity 多了兩欄，Flyway 也要補對應腳本，不然 validate 過不了。

我們現在來實際改造。提示詞拆三步交給 AI Agent：一，pom.xml 加入 data-jpa、postgresql、lombok 依賴；二，把 Customer 從普通 POJO 升級成 Entity；三，建立 CustomerRepository，更新 CustomerService——把原本的 List 換成注入 Repository，getAll、findById、save 全部改走資料庫。你會發現一件很漂亮的事：Controller 一行都不用動。上一章有好好分層，底層從記憶體換成資料庫，Controller 完全無感——這就是分層架構的回報。最後執行 mvn spring-boot:run，確認啟動成功、連線正常。

總結一下：Entity 對應資料表、Repository 提供現成 CRUD 和 Query Method、交易規則和 audit 欄位讓系統達到正式水準，Controller 零改動。不過 Query Method 有極限——搜尋條件「可有可無、任意組合」時方法名稱會爆炸，下一節我們來看動態查詢的正解：Specification。
