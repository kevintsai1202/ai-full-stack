# 章節 1 單元 1｜環境準備

> **播放順序｜請先播放 00 開場**：進入本單元前，先播放同目錄的 `00.mp4`（課程總覽、共用情境與影片／網站使用方式；約 4 分 26 秒），需要獨立音訊或字幕時使用 `00.mp3`、`00.srt`。播放完成後，再從本檔開始環境安裝。

## 單元定位

本節要解決的問題：承接開場已說明的課程總覽與三家共用 AI CRM 情境，讓學員把開發基線對齊——JDK 21、Maven 3.9+、Git、Node.js、Python 與 VS Code 全部裝好、驗證通過，避免後續章節被環境問題反覆打斷。這是整個課程的地基，也是第一次示範「把安裝工作交給 AI、自己只負責核對結果」的協作方式。

與前後節的銜接：本節承接非正式開場的課程總覽與共用情境；接著開始環境安裝。下一節（單元 2）會在這個已驗證的環境上，用 Spring Initializr 建立課程專案骨架。

建議時長：25～35 分鐘（含實際安裝與驗證示範）。

## 教學素材

### 核心心法：對齊工具責任

第一章不是在講工具清單，而是在建立後續開發都要依賴的開發基線。核心心法在於**對齊工具責任**：

- **VS Code** 負責編輯、導覽、除錯與擴充整合
- **Java 與 Maven** 負責專案編譯、依賴下載與執行
- **AI 助手** 適合做解釋、產生樣板、補測試與協助排錯
- **Git** 是 AI Agent 開發工具的必要安裝；**Node.js 與 Python** 是 Skills 的必要腳本執行工具
- **PowerShell 7+** 是本課程預設終端機環境
- **IDE（Antigravity 或 VS Code 擇一）** 是唯一需要手動安裝的工具，其餘一律交給 AI Agent 安裝；**Java / Spring 擴充套件** 等 JDK 21 裝好後再裝

只要 Java、Maven、VS Code 與 AI 協作方式一開始沒有對齊，後面所有章節都會被環境問題反覆打斷。這一節的目標，是讓你知道哪些工具是編輯器責任、哪些是執行環境責任，以及 AI 助手應該介入在哪一種工作。

### 第一步：先手動安裝 IDE（Antigravity 或 VS Code 擇一）

**為什麼這一步要自己動手？**

本章後面所有工具（PowerShell 7、JDK 21、Maven、Git、Node.js、Python）都是「把提示詞貼給 AI Agent，讓它在終端機裡幫你安裝」。但在 AI Agent 本身還沒裝好之前，沒有人可以替你執行這些提示詞，所以 IDE 必須先由你手動完成。這是全課程唯一需要自己下載安裝檔的地方。

**1. 安裝 IDE：Antigravity 或 VS Code 擇一，課程主要使用 Antigravity**

兩者都是 VS Code 體系的編輯器，操作方式相同，只需要裝一個。課堂示範一律以 Antigravity 進行。

- **選 Antigravity（建議）**：前往 [https://antigravity.google/](https://antigravity.google/) 下載並安裝 Antigravity IDE，以 Google 帳號登入。AI Agent 已內建，不必再裝其他東西
- **選 VS Code**：前往 [https://code.visualstudio.com/](https://code.visualstudio.com/) 下載安裝，Windows 安裝時勾選「加入 PATH」與「以 Code 開啟」右鍵選單。VS Code 本身沒有 Agent，請再到擴充套件市集安裝 Claude Code 擴充套件並登入 Anthropic 帳號
- 先不要裝 Java / Spring 擴充套件。它們要等 JDK 21 裝好之後再裝，才能一次抓到正確的 JDK，後面有專門一段說明

**2. 確認 Agent 已就位**
- 開啟任一空資料夾，在 Agent 對話框輸入「請執行 git --version 並告訴我結果」。看到它嘗試在終端機執行指令，就代表 Agent 已就位
- 此時回報找不到 git 是正常的，下一段就會讓它自己安裝。終端機仍是舊版 Windows PowerShell 5.1 也沒關係，同樣交給下一段升級

**3. 完成後你手上有什麼**
- 一個 IDE：Antigravity 或 VS Code
- 一個能替你執行指令的 AI Agent：Antigravity 內建，或 VS Code 中的 Claude Code
- 接下來的每一項安裝，都只需要把下一段的提示詞貼給 Agent，然後核對它回報的驗證結果

### 需要安裝的工具

本課程需要：PowerShell 7、JDK 21、Maven 3.9+、Git、Node.js（前端開發會用到，AI 也會用它撰寫自動化腳本）、Python（AI 會用它撰寫自動化腳本）。IDE（Antigravity 或 VS Code）已在上一步手動裝好，以下工具全部交給 AI Agent 安裝；Java / Spring 擴充套件則等 JDK 裝好後再裝（見下一節）。

**為什麼 Git、Node.js、Python 是必裝項目？**

這三個工具不只是「課程會用到」，而是 AI 協作開發的基礎設施：

- **Git**：目前已是 AI Agent 開發工具（如 Claude Code、Antigravity）的必要安裝工具——版本控制、分支協作與 Agent 的變更管理都依賴它，請務必先安裝
- **Node.js 與 Python**：是 Skills（技能）必要的腳本執行工具，Agent 執行技能時會用它們跑自動化腳本，所以也都需要先安裝；這兩項可以直接讓 Agent 代為安裝

**為什麼要先裝 PowerShell 7？**（Windows 使用者請最先安裝）

Windows 內建的 Windows PowerShell 只有 5.1 版，版本過舊——課程中的部分指令在 5.1 上會無法輸入或執行，錯誤訊息還會誤導排查方向。PowerShell 7 是跨平台的新版 shell，裝好後請一律用 `pwsh` 開啟，並把 VS Code 的預設終端機也改成 PowerShell 7。macOS 使用者可略過此項，直接使用內建終端機。

**工具官方下載網址**（若不透過 AI Agent 或套件管理器安裝，也可直接前往官方網站下載安裝檔）

- PowerShell 7：https://github.com/PowerShell/PowerShell/releases
- JDK 21（Eclipse Temurin）：https://adoptium.net/
- Maven：https://maven.apache.org/download.cgi
- Git：https://git-scm.com/downloads
- Node.js（LTS）：https://nodejs.org/
- Python 3：https://www.python.org/downloads/
- VS Code：https://code.visualstudio.com/
- Antigravity IDE：https://antigravity.google/

### JDK 裝好之後：安裝 Java / Spring 擴充套件

**為什麼要等 JDK 裝好才裝擴充套件？**

Extension Pack for Java 啟動時會掃描系統的 JAVA_HOME 與 PATH 來決定專案使用的 JDK。JDK 21 先就位，擴充套件裝完就直接可用；反過來先裝套件，之後還得重開 IDE 讓它重新掃描，初學者常在這一步看到專案一片紅字就以為裝錯了。

另外要知道：Java 擴充套件內建的 JRE 只用來啟動它自己的語言伺服器，**不會**替你的專案安裝 JDK。編譯與執行 Spring Boot 專案靠的是上一段由 AI 安裝並設定 JAVA_HOME 的 JDK 21，Maven 也依賴同一份設定。

**安裝步驟**
1. 確認上一段的 AI 安裝已完成，並且在新開的終端機執行 `java -version` 能看到 21
2. 完全關閉再重新開啟 IDE（Antigravity 或 VS Code），讓它讀到新的 JAVA_HOME 與 PATH
3. 開啟左側「擴充功能」面板（Ctrl + Shift + X），搜尋並安裝兩組擴充套件：**Extension Pack for Java** 與 **Spring Boot Extension Pack**
4. 安裝後確認 Java 擴充套件已啟用內建 Lombok 支援

**怎麼確認擴充套件抓到正確的 JDK**
- 開啟命令面板（Ctrl + Shift + P）執行「Java: Configure Java Runtime」，Project JDKs 應列出 JDK 21 並標示為預設
- 若列出的是其他版本或空白，代表 JAVA_HOME 沒設好或 IDE 沒重開，回上一段用 AI 重新檢查環境變數後再重開 IDE

這兩組擴充套件會在後面建立 Spring Boot 專案時提供補全、導覽與除錯，是課程接下來每一章都會用到的基礎。

### 環境驗證命令

安裝完成後，在 VS Code 終端機中執行以下命令確認環境：

```powershell
$PSVersionTable.PSVersion   # 主版本號應 >= 7（顯示 5.x 代表仍在舊版 Windows PowerShell）
java -version      # 應顯示 openjdk version "21.x.x"
mvn -version       # 應顯示 Apache Maven 3.9.x，Java version: 21
git --version      # 應顯示 git version 2.x.x
node --version     # 應顯示 v22.x.x（LTS）
python --version   # 應顯示 Python 3.x.x
```

**判讀重點**

- Java 版本必須是 21，若不是代表 `JAVA_HOME` 或 `Path` 設定有誤
- Maven 顯示的 Java version 也必須是 21，否則 Maven 沒有指向正確的 JDK
- 若出現「不是內部或外部命令」，通常是 PATH 尚未設定或尚未重新載入終端機
- 若 PSVersion 顯示 5.x，代表開到 Windows 內建的舊版 PowerShell——請改開 pwsh（PowerShell 7），否則課程中的部分指令會無法執行

**常見錯誤**

- VS Code 終端機與系統終端機顯示不同版本：重新啟動 VS Code
- VS Code 終端機預設仍是 Windows PowerShell 5.1：按終端機面板的「＋」旁下拉選單 →「選取預設設定檔」→ 改選 PowerShell 7（pwsh）後重開終端機
- Spring Boot Extension Pack 無補全：重建 Java Language Server

## 示範與提示詞

### 用 AI Agent 安裝開發工具（分項提示詞）

**PowerShell 7 安裝提示詞**（Windows 請最先執行這一段）

```text
我使用 Windows 11，請用 winget 幫我安裝 PowerShell 7（套件 ID：Microsoft.PowerShell）。
安裝完成後告訴我怎麼從開始功能表開啟 pwsh，
執行 $PSVersionTable.PSVersion 確認主版本號 >= 7，
最後幫我把 VS Code 的預設終端機改成 PowerShell 7。
```

**JDK 21 安裝提示詞**

```text
我使用 Windows 11，請用 winget 幫我安裝 Eclipse Temurin JDK 21。
安裝完成後，請設定 JAVA_HOME 環境變數（永久生效），
確認 Path 中已包含 JDK bin 目錄，並執行 java -version 驗證。
```

**Maven 安裝提示詞**

```text
請用 winget 幫我安裝 Apache Maven 最新版。
設定 M2_HOME 與 Path（永久生效），確認 Maven 使用 JDK 21，
執行 mvn -version 顯示完整結果。
```

**Git 安裝提示詞**（AI Agent 開發工具的必要安裝，請務必完成）

```text
請用 winget 幫我安裝 Git，完成後設定 user.name 與 user.email，
並執行 git --version 確認安裝成功。
```

**Node.js 安裝提示詞**（前端開發會用到，也是 Skills 必要的腳本執行工具；可讓 Agent 代為安裝）

```text
請用 winget 幫我安裝 Node.js LTS 版本，
確認 Path 已包含 Node.js 目錄，
並執行 node --version 與 npm --version 驗證安裝成功。
```

**Python 安裝提示詞**（Skills 必要的腳本執行工具；可讓 Agent 代為安裝）

```text
請用 winget 幫我安裝 Python 3 最新穩定版，
安裝時將 Python 加入 Path，
並執行 python --version 與 pip --version 驗證安裝成功。
```

### ① 用 AI 把開發環境準備好［build］

> 把安裝設定交給 AI，自己只負責核對結果

```text
我要開始學寫程式，請幫我把電腦準備好：裝好寫 Java 程式需要的工具（Java 本身、用來建置專案的工具、還有版本控制工具），並幫我設定到「打開一個新的終端機視窗就能直接使用」。我用的是 Windows 11（如果是 Mac 請改用對應的做法）。完成後請告訴我怎麼一一確認都裝好了。
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

## 逐步操作與驗收

### 操作順序

1. 開一個全新的 PowerShell 7 視窗，依序執行 `$PSVersionTable.PSVersion`、`java -version`、`mvn -version`、`git --version`、`node --version` 與 `python --version`；不要只截安裝程式畫面，要保留實際命令輸出。
2. 看到 Maven 的輸出後，再確認其中的 `Java version` 是 21。若系統的 `java -version` 是 21、Maven 卻指向另一個 JDK，先記錄 `JAVA_HOME`、`Get-Command java`、`Get-Command mvn` 的結果，再處理 PATH 順序。
3. 重新開啟 IDE（Antigravity 或 VS Code），在同一個終端機確認預設 shell 是 PowerShell 7，再安裝 Java、Spring Boot 與 YAML 語法支援；此時只驗證編輯體驗，不要先加入尚未用到的外掛。
4. 把版本輸出貼給 AI，要求它只根據輸出判斷缺少的工具、版本不符與環境變數問題，並要求它列出每個判斷對應的證據。

### 預期結果與證據

- PowerShell 主版本至少為 7、Java 與 Maven 實際使用 Java 21；Git、Node.js、Python 皆能回應版本。
- 交付 `environment-check.txt` 或終端機截圖，內容須含命令本身、完整輸出、執行日期與目前專案路徑。

### 失敗分流

- 出現「不是內部或外部命令」時，先用 `Get-Command <工具名> -All` 找實際路徑，再重新開終端機；不要直接把錯誤訊息刪掉。
- PowerShell 仍是 5.1 時，啟動 `pwsh` 後重新執行全部檢查，並在報告中分開記錄兩個 shell 的結果。
- JDK 與 Maven 不一致時，先修正 `JAVA_HOME` 或 PATH，再重跑 Maven；環境尚未一致前不得宣稱專案可建置。

## 口語稿

好，剛才的 00 開場影片已經先把整門課的路線、AI CRM 的三家共用客戶，以及影片和網站的分工講清楚了。現在我們正式進入第一個實作單元，開始準備環境。

那我們現在正式開始第一節課。在我們寫下任何一行程式之前，我想先問你一個問題：你有沒有過這種經驗——興沖沖地想學一個新框架，結果光是裝環境就卡了一個晚上？Java 版本不對、環境變數沒設好、終端機說「不是內部或外部命令」，最後熱情就在這些訊息裡被磨光了。我帶課這麼多年，看過太多人不是被程式打敗，而是被環境打敗的。

所以第一節課，我們不急著寫程式，我們先把地基打穩。這一節的重點只有一個觀念，叫做「對齊工具責任」。什麼意思？就是你要很清楚地知道：VS Code 負責的是編輯、導覽、除錯跟擴充整合；Java 跟 Maven 負責的是專案的編譯、依賴下載跟執行；而 AI 助手呢，它適合做解釋、產生樣板程式、補測試、還有幫你排錯。這三塊責任一開始就分清楚，後面兩天的課程才不會一直被環境問題打斷。另外提醒一下，本課程預設的終端機環境是 PowerShell 7+，我的示範機器是 Windows 11，如果你用 Mac 也沒關係，等一下你會看到，我們的提示詞會請 AI 自己換成對應的做法。

在裝其他工具之前，Windows 的同學有一件事要最先做：安裝 PowerShell 7。你可能會說，我的 Windows 本來就有 PowerShell 啊？注意，那個是內建的 Windows PowerShell，版本停在 5.1，已經很多年沒有大更新了——課程裡有些指令在 5.1 上會直接打不進去、或者執行到一半噴錯，而且錯誤訊息還會把你帶去完全不相干的方向。所以我們現在來把它裝起來：把教材裡的 PowerShell 7 安裝提示詞貼給 AI，它會用 winget 幫你裝好，裝完之後你從開始功能表搜尋 pwsh 打開它，執行 $PSVersionTable.PSVersion，你會看到主版本號是 7——看到 7 才算過關，看到 5 就代表你開錯視窗了。最後一步很多人會漏掉：把 VS Code 的預設終端機也改成 PowerShell 7，不然你在 VS Code 裡開終端機，用的還是舊的 5.1。用 Mac 的同學這一段可以直接跳過，你內建的終端機就夠用了。

好，接下來要裝哪些東西？JDK 21、Maven 3.9 以上、Git、Node.js、Python，還有 VS Code 加上兩組擴充套件——Extension Pack for Java 跟 Spring Boot Extension Pack。這裡我要特別解釋三個工具的角色，免得你覺得清單太長想偷懶跳過。第一個是 Git——你可能以為它只是版本控制，但現在的 AI Agent 開發工具，像我們課程用的 Claude Code、Antigravity，都把 Git 當成必要的基礎安裝，Agent 管理程式碼變更、開分支協作，背後全靠它，所以這一項請務必裝好。再來是 Node.js 跟 Python——它們是 Skills，也就是技能，必要的腳本執行工具；Agent 執行技能的時候，就是用這兩個環境去跑自動化腳本的。所以這三樣都需要先安裝。好消息是，Node.js 跟 Python 這兩項你連自己動手都不用，直接讓 Agent 代為安裝就行，教材裡的提示詞貼上去就好。你可能會想：哇，這麼多工具，要裝到什麼時候？這就是這門課跟傳統課程不一樣的地方了——我們不自己一個一個裝，我們把安裝這件事交給 AI，自己只負責核對結果。

我們現在來實際做一次。我打開 AI 助手，貼上這段提示詞：「我要開始學寫程式，請幫我把電腦準備好：裝好寫 Java 程式需要的工具，並幫我設定到打開一個新的終端機視窗就能直接使用。我用的是 Windows 11。完成後請告訴我怎麼一一確認都裝好了。」注意這段話的結構，我沒有講任何指令細節，但我把「需求」講得很清楚：要哪些工具、要設定到什麼程度、還有最重要的——完成後要告訴我怎麼驗證。你會看到 AI 開始用 winget 逐一安裝 JDK 21、Maven、Git，還會幫你把 JAVA_HOME 跟 Path 設成永久生效。如果你想要更精準的控制，教材裡也附了分項的提示詞，一個工具一段，你可以一項一項來。

裝完之後，重頭戲來了：驗證。這是這門課從頭到尾都要堅持的紀律。我們在 VS Code 的終端機裡先執行 $PSVersionTable.PSVersion 確認自己在 PowerShell 7 裡面，再執行 java -version、mvn -version、git --version、node --version、python --version。你會看到 Java 應該顯示 21 點多的版本——注意，一定要是 21，如果不是，代表 JAVA_HOME 或 Path 設錯了。再來看 mvn -version 的輸出，裡面有一行 Java version，這一行也必須是 21，不然就是 Maven 指到了別顆 JDK。這是最容易踩的坑：很多人電腦裡原本就有舊版 Java，結果 Maven 一直用舊的，錯誤訊息還會誤導你往完全不相干的方向查。

那如果真的驗證不過怎麼辦？不要慌，也不要自己瞎猜。把畫面上的錯誤訊息原封不動貼給 AI，用教材裡的排錯提示詞，告訴它「我照驗證步驟做，但看到不對的結果」，它會依你貼的訊息判斷原因，把工具重新指向正確的新版本。記住，排錯的時候，附上實際訊息，永遠比用自己的話描述來得有效。

好，總結一下這一節：環境準備的本質是對齊工具責任，安裝交給 AI，驗證自己來，一項都不能跳過。下一節，我們就要在這個乾淨的環境上，用 Spring Initializr 建立貫穿整門課的專案骨架，讓第一個 Spring Boot 程式真正跑起來。我們下一節見。
