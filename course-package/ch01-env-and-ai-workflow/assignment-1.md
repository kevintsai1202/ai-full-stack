# 章節 1 作業 1｜完成開發環境設定與專案建立

## 單元定位

章節 1 的實作驗收：學員獨立把「環境安裝 → 專案建立 → 啟動驗證」完整走一遍，確保進入章節 2 之前，每個人手上都有一套可啟動的課程專案。這份作業同時是「先讀需求 → 產程式 → 驗證」三原則的第一次獨立演練。

## 作業說明

### 作業要求（對應 u1 實作任務）

依序完成以下六項任務，建議搭配單元 1、2 的提示詞讓 AI 協助執行，但每一項的驗證必須自己動手核對：

- **u1-t6**：安裝 PowerShell 7 並確認 `$PSVersionTable` 主版本號 >= 7（Windows 請最先完成；macOS 可略過）
- **u1-t1**：安裝 JDK 21 並完成 `JAVA_HOME` 設定
- **u1-t2**：安裝 Maven 3.9+ 並完成 `Path` 設定
- **u1-t3**：安裝 Git 並完成 `user.name` / `user.email` 設定
- **u1-t4**：安裝必要 VS Code 外掛（Extension Pack for Java、Spring Boot Extension Pack）並驗證 Java / Maven 版本
- **u1-t5**：透過 Spring Initializr 建立課程專案並確認啟動成功

### 繳交物

1. 環境驗證截圖：終端機執行 `$PSVersionTable.PSVersion`（Windows）、`java -version`、`mvn -version`、`git --version` 的完整輸出畫面
2. 專案結構截圖：VS Code 中課程專案（`tutorial`）的目錄樹，可看到 `src/main/java`、`application.properties` 與 `pom.xml`
3. 建置成功截圖：`mvn clean compile` 執行結果，包含 `BUILD SUCCESS` 字樣

### 驗收標準

- Windows：`$PSVersionTable.PSVersion` 主版本號 >= 7，且 VS Code 預設終端機已改為 PowerShell 7（pwsh）
- `java -version` 顯示 openjdk version "21.x.x"
- `mvn -version` 顯示 Apache Maven 3.9.x，且其中 Java version 為 21
- `git --version` 顯示 git version 2.x.x，且 `git config user.name` / `user.email` 已設定
- 專案由 Spring Initializr 產生：Group `com.example`、Artifact `tutorial`、Java 21、Packaging Jar，依賴包含 Spring Web、Spring Data JPA、PostgreSQL Driver、Flyway Migration
- `mvn clean compile` 結果為 `BUILD SUCCESS`
- 首次啟動時資料庫連線失敗屬預期行為（尚未啟動 Docker），不影響驗收；重點是主程式可被 Maven 執行、類別掃描無錯誤

## 口語稿

好，章節 1 的內容到這裡告一段落，接下來是你的第一份作業。我知道有些同學看到「作業」兩個字會想跳過——反正影片裡都示範過了嘛。但我要很誠實地跟你說：這份作業是整門課裡最不能跳過的一份。因為從章節 2 開始，我們每一章都會在這個專案上疊加功能，如果你的環境沒有真的通過驗證、專案沒有真的建起來，你後面每一節課都會卡住，而且卡住的原因會跟課程內容完全無關，那是最消耗學習動力的狀況。

作業的內容其實就是把這一章示範過的東西，自己完整走一遍，一共六項任務。第零項，Windows 的同學請最先安裝 PowerShell 7，執行 $PSVersionTable.PSVersion 確認主版本號是 7 以上，並且把 VS Code 的預設終端機改成 pwsh——這一步沒做，後面有些指令會打不進去，你會以為是自己哪裡做錯，其實只是殼太舊。接著第一，安裝 JDK 21，並且完成 JAVA_HOME 的設定。第二，安裝 Maven 3.9 以上的版本，完成 Path 設定。第三，安裝 Git，設定好 user.name 跟 user.email。第四，裝好 VS Code 的必要外掛——Extension Pack for Java 跟 Spring Boot Extension Pack——然後驗證 Java 跟 Maven 的版本。第五，透過 Spring Initializr 建立課程專案，並且確認它能啟動成功。

做的方式，我鼓勵你用課堂上的協作三原則來走：先讀需求——把單元 1 跟單元 2 的提示詞拿出來，看懂每一段提示詞在要求什麼；然後產程式——讓 AI 幫你執行安裝跟建立專案；最後驗證——這一步一定要自己動手，親眼看到每一個版本號、親手跑出 BUILD SUCCESS。AI 可以幫你裝，但核對結果的責任在你。

繳交物有三張截圖。第一張，環境驗證：終端機執行 java -version、mvn -version、git --version 的完整輸出。第二張，專案結構：VS Code 裡打開課程專案的目錄樹，要能看到 src/main/java、application.properties 跟 pom.xml。第三張，建置成功：mvn clean compile 的執行結果，畫面上要有 BUILD SUCCESS 這個字樣。

驗收標準我一條一條講清楚，你交作業之前自己先對一次。Java 的版本必須是 21——不是 17、不是 8，就是 21。Maven 要是 3.9 以上，而且注意，mvn -version 輸出裡面那一行 Java version 也必須是 21，這是最多人漏看的地方；如果那一行不是 21，代表 Maven 指到了電腦裡另一顆舊的 JDK，請回去用排錯提示詞把它修正。Git 要能顯示版本，而且 user.name 跟 user.email 要設定完成。專案的設定要跟課程一致：Group 是 com.example、Artifact 是 tutorial、Java 21、Packaging 選 Jar，四個依賴——Spring Web、Spring Data JPA、PostgreSQL Driver、Flyway Migration——一個都不能少，因為後面的章節都靠它們。最後，mvn clean compile 必須是 BUILD SUCCESS。

還有一件事要特別交代，免得你白白緊張：如果你嘗試啟動應用程式，看到資料庫連線失敗的錯誤——這是正常的、預期中的行為。因為我們還沒有啟動 Docker、還沒有建資料庫，那是第三章的事。這份作業只驗收到「主程式可以被 Maven 執行、類別掃描沒有錯誤」這個程度。需要的話，可以先把 application.properties 裡的資料庫設定暫時留空。

如果過程中卡住了，記得排錯的正確姿勢：把畫面上的錯誤訊息原封不動貼給 AI，說明你預期看到什麼、實際看到什麼，讓它幫你判斷。最常見的原因就是電腦裡原本裝過舊版本造成衝突，這在單元 1 我們已經演練過怎麼處理。

把這三張截圖交上來，你就正式拿到了進入下一章的門票。章節 2 我們要開始寫真正的程式了——理解 Spring MVC 的請求流程，把 CRM 的領域拆解成 Customer、Contact、Interaction、Opportunity，然後用 AI 產出你的第一組客戶 REST API。環境已經就緒，我們章節 2 見。
