# 章節 3 單元 3｜資料庫連線設定

## 單元定位

容器裡的 PostgreSQL 已就緒、Flyway 腳本也準備好了，本節把最後一段接起來：在 `application.yml` 設定 datasource 連線、Flyway 與 JPA 的 `ddl-auto: validate`，然後啟動應用程式，在 log 中確認 Flyway 遷移成功。做完這一節，整個資料庫環境就算正式就緒。建議時長：10～15 分鐘。

## 教學素材

### 用 AI Agent 設定 application.yml 資料庫連線

容器啟動成功後，接著請 AI Agent 修改 Spring Boot 的 `application.yml`，加入資料庫連線資訊與 Flyway 設定。設定完成後執行 `mvn spring-boot:run`，在 log 中看到 Flyway 完成遷移的訊息（`Successfully applied N migration(s)`）就代表整個資料庫環境已就緒。

需要加入的三塊設定：

1. **datasource**：連線到 `localhost:5432/learn_spring`，帳號 `postgres`，密碼 `password`
2. **flyway**：`enabled: true`、`baseline-on-migrate: true`、腳本位置 `classpath:db/migration`
3. **jpa**：`ddl-auto: validate`（由 Flyway 管理 Schema，JPA 只驗證結構）

每個設定項目加上中文註解說明用途。

## 示範與提示詞

**AI Agent 提示詞 — 設定 application.yml**

```text
【提示詞 1 — 請 AI Agent 設定資料庫連線】
請修改我的 Spring Boot 專案的 src/main/resources/application.yml，
加入以下設定（若已存在請直接修改，不要重複）：
1. datasource：連線到 localhost:5432/learn_spring，帳號 postgres，密碼 password
2. flyway：enabled: true，baseline-on-migrate: true，腳本位置 classpath:db/migration
3. jpa：ddl-auto: validate（由 Flyway 管理 Schema，JPA 只驗證結構）
每個設定項目請加上中文註解說明用途。

【提示詞 2 — 驗證連線與 Flyway 遷移】
設定完成後請幫我執行 mvn spring-boot:run，
確認 log 中出現 Successfully applied N migration(s) 的訊息。
若出現連線錯誤或 Flyway 失敗，請幫我找出原因並修正。
```

## 口語稿

到目前為止，我們手上有兩個各自獨立的東西：一邊是 Docker 裡跑著的 PostgreSQL 容器，一邊是專案裡的 Flyway 遷移腳本。但你如果現在啟動 Spring Boot，什麼事都不會發生——因為應用程式根本不知道資料庫在哪裡、帳號密碼是什麼、腳本要不要跑。這一節要做的事情很單純，就是把這三方接起來，而接線的地方就是 Spring Boot 的設定檔 application.yml。

為什麼所有連線資訊都集中在 application.yml？這是 Spring Boot 的設計哲學：程式碼描述「行為」，設定檔描述「環境」。同一份程式碼，在你的本機連 localhost 的容器，到了正式機換一份設定就能連正式資料庫，程式碼一行都不用改。所以把連線設定寫對、寫清楚，是一件基本功。

我們要加的設定有三塊。第一塊是 datasource，也就是資料來源：URL 指向 localhost 的 5432 埠、資料庫名稱 learn_spring，帳號 postgres、密碼 password——這組資訊要和上一個單元 docker-compose.yml 裡設定的完全一致，少一個字都連不上。第二塊是 flyway：enabled 設為 true 啟用它，腳本位置指向 classpath:db/migration，也就是我們放 V1 腳本的目錄；另外加上 baseline-on-migrate: true，這個設定是處理「資料庫已經存在但沒有 Flyway 記錄」的情況，讓 Flyway 能以現況為基準開始接管。第三塊是 jpa 的 ddl-auto: validate——還記得上一節的職責分工嗎？Schema 由 Flyway 管，JPA 只負責驗證 Entity 和資料表結構有沒有吻合，不准它自己動手改表。

我們現在來實際操作。一樣把需求交給 AI Agent，提示詞裡我特別加了一句「若已存在請直接修改，不要重複」——這是實務上很重要的小細節，因為 application.yml 裡可能已經有其他設定，你不希望 AI 疊出兩份重複的區塊造成格式錯誤。然後照慣例，每個設定項目都要加中文註解，之後回頭看才知道每一行的用途。

設定完成後，重頭戲來了：請 AI Agent 執行 mvn spring-boot:run。這一次啟動和以前不一樣，你會看到 log 裡多了 Flyway 的訊息——它先連上資料庫，檢查目前的版本狀態，然後開始套用還沒執行過的腳本。你要找的關鍵字是「Successfully applied N migration(s)」，看到這一行，就代表 V1 腳本已經成功執行，客戶、聯絡人、往來紀錄、生意機會這幾張表都建進資料庫了。這行訊息就是本節的驗收點。

而且我要你多做一件事：把應用程式停掉，再啟動一次。第二次啟動時，你會看到 Flyway 說 Schema 已經是最新版，沒有任何腳本需要套用——它不會重複建表，也不會報錯。這正是我們這一章一開始就講的驗收重點之一：「重開時不會發生重複建表的錯誤」。Flyway 在資料庫裡有一張自己的歷史表，記錄每支腳本跑過沒有，所以它永遠知道該做什麼、不該做什麼。

那如果啟動失敗呢？最常見的兩種：一種是連線被拒絕，八成是容器沒開，先 docker ps 檢查；另一種是 Flyway 報 migration 失敗，通常是 SQL 語法或命名問題。處理方式一樣：把 log 裡的錯誤訊息完整貼給 AI Agent，請它找出原因並修正，不要只貼一行，前後文都給它。

總結一下：這一節我們在 application.yml 接好了 datasource、Flyway 和 JPA 三塊設定，啟動後看到 Successfully applied 訊息，資料庫環境正式就緒，而且重開也不會重複建表。不過現在資料表雖然有了，我們的程式還是用 List 在存資料，兩邊根本沒關係。下一節，我們就要進入 ORM 的世界，用 JPA 的 Entity 和 Repository，讓 Java 物件真正對接到資料表。
