# 章節 1 單元 2｜建立專案

## 單元定位

本節要解決的問題：在已驗證的環境上，透過 Spring Initializr 建立課程初始專案，理解 Spring Boot 標準目錄結構與各層職責，並在進入任何功能開發之前先確認專案「可以啟動」。這個專案骨架會貫穿整門課——後續每一章（REST API、資料庫、安全認證、AI 功能）都在同一個專案上持續疊加。

與前後節的銜接：承接單元 1 已就緒的 JDK 21 + Maven 環境；下一節（單元 3）會退一步談 AI 協作的適用時機，建立全課的協作紀律。

建議時長：20～30 分鐘（含建立專案與首次啟動示範）。

## 教學素材

### 透過 Spring Initializr 建立課程專案

Spring Initializr 是 Spring 官方提供的專案產生器，負責生成標準的 Maven 目錄結構、`pom.xml` 依賴設定與主程式進入點。在 VS Code 中可直接透過命令面板呼叫，不需要離開編輯器。

本課程需要的依賴在這一步就全部選定，後續每一章都會在同一個專案上持續疊加功能。

- 開啟 VS Code 命令面板（Ctrl+Shift+P），輸入 `Spring Initializr: Create a Maven Project`
- 或直接前往 start.spring.io 在瀏覽器中設定後下載
- Spring Boot 版本：選擇 4.0.0（課程範例基於此版本）
- Group：`com.example`，Artifact：`tutorial`，Packaging：Jar，Java：21
- 依賴選擇：Spring Web、Spring Data JPA、PostgreSQL Driver、Flyway Migration

**Spring Initializr 設定摘要**

```text
Project      : Maven
Language     : Java
Spring Boot  : 4.0.x
Group        : com.example
Artifact     : tutorial
Packaging    : Jar
Java         : 21

依賴 (Dependencies):
  - Spring Web
  - Spring Data JPA
  - PostgreSQL Driver
  - Flyway Migration
```

### 確認專案結構與首次啟動

產生後的專案已包含標準目錄結構。在進入任何功能開發之前，先確認可以啟動，是避免後續被環境問題卡住的最重要步驟。

- `src/main/java/com/example/tutorial/` — Java 原始碼根目錄
- `src/main/resources/application.properties` — 應用程式設定檔
- `pom.xml` — Maven 依賴與建置設定

首次啟動時資料庫連線會失敗（因為尚未啟動 Docker），這是預期行為。本步驟只驗證 Spring Boot 主程式可以被 Maven 執行、類別掃描沒有錯誤。可先把 `application.properties` 中的資料庫設定暫時移除或留空，讓主程式不因找不到 DB 而無法啟動。

**首次執行驗證**

```powershell
# 進入專案目錄
cd tutorial

# 編譯並確認無語法錯誤
mvn clean compile

# 正常輸出應包含 BUILD SUCCESS
```

## 示範與提示詞

### ② 建立專案的資料夾骨架［build］

> 一個放後端、一個放之後要做的網頁畫面

```text
請幫我建立這個課程專案的資料夾結構：一個資料夾放「後端程式」、另一個資料夾放「之後要做的網頁畫面」，並開始做版本控制。後端先給我一個最簡單、空的、但能跑起來的程式就好。完成後我要能把這個後端空專案實際啟動起來，確認一切就緒。
```

### ✅ 驗證 — 環境與骨架就緒［verify］

> 確認工具版本與後端能啟動

```text
請幫我逐一確認開發環境都就緒：檢查剛才裝的那幾個工具版本是否正確，並確認後端的空專案能成功啟動。如果有任何一項不對，請直接幫我修好。
```

### 🔧 排錯 — 裝錯版本或啟動失敗［fix］

> 常見：電腦上原本就有舊版本造成衝突

```text
我照驗證步驟做，但看到不對的結果（我會把畫面上的訊息貼給你）。常見原因是電腦上原本就裝了舊版本造成衝突。請依我貼的訊息判斷原因並幫我修正設定，讓工具都指向正確的新版本。
```

## 口語稿

上一節我們把環境驗證通過了，這一節，我們終於要把課程專案建起來。不過在動手之前，我想先講一個很多初學者會犯的錯：拿到一個新專案的想法，第一件事就是狂寫功能。寫了兩個小時，才發現專案根本跑不起來——可能是依賴衝突、可能是版本不對——然後你就得在一堆自己寫的程式碼裡面，去猜到底是環境問題還是程式問題。這種除錯是最痛苦的，因為變因太多了。所以這一節的核心原則就一句話：在進入任何功能開發之前，先確認專案可以啟動。

那專案怎麼建？我們用 Spring 官方提供的專案產生器，叫做 Spring Initializr。它會幫你生成標準的 Maven 目錄結構、pom.xml 的依賴設定，還有主程式的進入點。你完全不需要手刻這些骨架。

我們現在來實際操作。在 VS Code 裡按 Ctrl+Shift+P 打開命令面板，輸入 Spring Initializr: Create a Maven Project。如果你不想在編輯器裡做，也可以直接開瀏覽器到 start.spring.io，效果一樣。接下來你會看到一連串的選項，我們一項一項填：Spring Boot 版本選 4.0.0，這是課程範例的基準版本；Group 填 com.example，Artifact 填 tutorial；Packaging 選 Jar；Java 選 21——記得，跟我們上一節裝的 JDK 對齊。然後是依賴，這一步很關鍵，本課程需要的依賴我們現在就一次選定：Spring Web、Spring Data JPA、PostgreSQL Driver、還有 Flyway Migration。你可能會問：資料庫我們不是第三章才會用到嗎？對，但先選起來，後續每一章都會在同一個專案上持續疊加功能，我們不會砍掉重練。

按下產生之後，你會看到一個完整的專案出現在你面前。我們花一分鐘認識一下這個目錄結構，因為接下來所有的課都在這裡面活動：src/main/java/com/example/tutorial 是 Java 原始碼的根目錄，你寫的所有程式都放這裡；src/main/resources/application.properties 是應用程式的設定檔，之後資料庫連線、AI 金鑰都會設定在這；pom.xml 則是 Maven 的依賴跟建置設定。

接著就是這一節最重要的動作——首次啟動驗證。打開終端機，cd 進 tutorial 目錄，執行 mvn clean compile。你會看到 Maven 開始下載依賴、編譯程式，正常的話最後會出現 BUILD SUCCESS。這裡先預告一個「預期中的失敗」：如果你直接啟動應用程式，資料庫連線會失敗，因為我們還沒有啟動 Docker、還沒有資料庫。這不是你做錯了，這是預期行為。這一步我們只驗證一件事：Spring Boot 主程式可以被 Maven 執行、類別掃描沒有錯誤。一個小技巧是，先把 application.properties 裡的資料庫設定暫時移除或留空，讓主程式不會因為找不到 DB 而起不來。

那 AI 在這一節扮演什麼角色？教材裡有一段建骨架的提示詞，你可以請 AI 幫你建立整個課程專案的資料夾結構：一個資料夾放後端程式、一個放之後要做的網頁畫面，並且開始做版本控制。注意提示詞的最後一句：「完成後我要能把這個後端空專案實際啟動起來，確認一切就緒。」我們又看到同樣的模式了——先把需求講清楚，讓 AI 產出，最後一定要驗證。如果啟動失敗，把錯誤訊息貼給 AI，用排錯提示詞讓它幫你修。

總結這一節：用 Spring Initializr 建立標準骨架、一次選好全課的依賴、然後在寫任何功能之前先讓它 BUILD SUCCESS。下一節我們暫停動手，來談一個更重要的觀念——AI 協作到底什麼時候該用、什麼時候不該用，把這門課的協作紀律講清楚。
