# 章節 3 單元 1｜Docker 與資料庫建立

## 單元定位

上一章的客戶 API 把資料暫存在記憶體 List 裡，程式一重開資料就歸零。本節要解決的第一個問題，就是替專案準備一個「真正落地」的資料庫環境：用 Docker Desktop 跑起 PostgreSQL 18（含 pgvector 向量擴充）的容器，並用具名卷讓資料在容器重建後依然存在。這是本章後續 Flyway、JPA、動態查詢的地基，也直接支撐 Day 2 的 RAG 功能（pgvector）。建議時長：15～20 分鐘。

## 教學素材

### 下載與安裝 Docker Desktop

開始建立容器化資料庫之前，本機需要先安裝好 Docker Desktop。

**下載網址**：[Docker Desktop 官方下載頁面](https://www.docker.com/products/docker-desktop/)

**安裝步驟**：

1. 開啟上方網址，依作業系統選擇下載版本（Windows / Mac / Linux）
2. 下載完成後執行安裝檔，全程使用預設選項即可
3. Windows 安裝完成後，依提示重新啟動電腦
4. 開啟 Docker Desktop，等待左下角狀態顯示綠色的「Engine running」
5. 開啟終端機執行 `docker run hello-world`，看到 `Hello from Docker!` 訊息即代表安裝成功

（下圖為官方下載頁面實際畫面，可對照確認下載按鈕位置，對應教學網站 Unit 3 插圖 `u3-4-docker-desktop-download.png`）

### 為什麼資料庫要容器化

教學專案最怕的是每位學員本機資料庫版本不同、初始化內容不同、安裝方式也不同。Docker 的價值在於把這些差異壓到最低，讓資料庫可以被快速重建與共享。

這個課程後面還需要 `pgvector` 支援向量欄位，因此從一開始就直接用帶有該擴充功能的 PostgreSQL 映像。

### 用 AI Agent 產生 docker-compose.yml

確認 Docker Desktop 已正常運行後（可先執行 `docker run hello-world` 驗證），接著請 AI Agent 幫你在專案根目錄建立 `docker-compose.yml`。本課程使用 PostgreSQL 18 並搭配 pgvector 向量擴充（Day 2 的 RAG 功能依賴它），讓 AI Agent 直接生成並啟動容器，你再對照說明理解每個欄位的用途。

`docker-compose.yml` 的關鍵需求：

- 使用 `pgvector/pgvector:pg18` 映像（PostgreSQL 18 + pgvector 向量擴充）
- 資料庫名稱：`learn_spring`
- 使用者：`postgres`，密碼：`password`
- 本機 5432 埠對應容器 5432 埠
- 使用具名卷（named volume）讓資料持久化，容器重建後資料不遺失
- 每個設定項目加上中文註解

建立完成後執行 `docker-compose up -d` 啟動容器，再執行 `docker ps`，確認容器狀態為 `Up`。

## 示範與提示詞

**AI Agent 提示詞 — 建立 docker-compose.yml**

```text
【提示詞 1 — 請 AI Agent 建立並啟動】
請在我的 Spring Boot 專案根目錄建立 docker-compose.yml，需求如下：
- 使用 pgvector/pgvector:pg18 映像（PostgreSQL 18 + pgvector 向量擴充）
- 資料庫名稱：learn_spring
- 使用者：postgres，密碼：password
- 本機 5432 埠對應容器 5432 埠
- 使用具名卷（named volume）讓資料持久化，容器重建後資料不遺失
- 每個設定項目加上中文註解

建立完成後請幫我執行 docker-compose up -d，
再執行 docker ps，確認容器狀態為 Up。

【提示詞 2 — 排查容器啟動失敗】
我執行 docker-compose up -d 後容器狀態不是 Up，
docker logs 顯示：
[貼上錯誤訊息]
請幫我找出原因並修正。
```

**口語化任務提示詞 — 準備一個正式的資料庫［build］**

```text
請幫我準備一個正式的資料庫來存這些客戶資料：在專案根目錄建立 docker-compose.yml，用最新的 pgvector/pgvector:pg18 映像（PostgreSQL 18 加上向量擴充，之後做「讓 AI 找出相似內容」會用到），資料庫名稱 learn_spring、帳號 postgres、密碼 password、本機 5432 對應容器 5432，並用具名卷（named volume）設定成「資料不會因為重開而消失」。完成後幫我啟動容器，用 docker ps 確認狀態是 Up。每個設定請加繁體中文說明。
```

## 逐步操作與驗收

### 建立可重現的 PostgreSQL

1. 先檢查 Docker Desktop 或 Docker Engine 正常，再閱讀 `compose.yaml`；確認 image、container name、port、database、user、password 與 volume 各自的用途，不要把密碼直接寫進公開檔案。
2. 在含有 compose 檔的目錄執行 `docker compose up -d`，接著用 `docker compose ps` 確認狀態，再用 `docker compose logs --tail=100 postgres` 檢查資料庫是否真的 ready。
3. 以 `docker exec` 進入 PostgreSQL 執行資料庫清單和 schema 查詢，確認資料庫名稱和使用者權限；再重啟 container，驗證 volume 能保留資料。
4. 故意以錯誤 port 或資料庫名稱連線一次，保存錯誤並說明它和應用程式設定的對應，不要只看 container 顯示 Up 就宣稱完成。

### 預期結果與證據

- `docker compose ps` 顯示資料庫 running/healthy，log 出現可接受連線的訊息，psql 能登入正確 database。
- 交付 compose 檢查表、`ps`、log、psql 查詢與重啟前後的資料保留證據；密碼以遮罩或環境變數形式保存。

### 失敗分流與銜接

- port 被占用時先用 `docker ps` 和 `Get-NetTCPConnection` 找衝突，不要隨意改應用程式 port；改動後要同步記錄。
- 下一單元會用 Flyway 建 schema，先確定資料庫容器穩定且連線資訊已被明確保存。

## 口語稿

歡迎來到章節三。在開始動手之前，我想先問你一個問題：上一章我們做的客戶 API，資料是存在哪裡的？答案是存在一個 Java 的 List 裡面，也就是記憶體。這在開發初期很方便，但它有一個致命的問題——程式一重開，資料就全部不見了。你可以想像一下，如果一套 CRM 系統，業務辛辛苦苦建了三百筆客戶資料，結果伺服器重開機一次就全部歸零，這個系統是完全不能用的。所以企業專案不能只靠記憶體資料，資料一定要「落地」，要存進真正的資料庫。這一章，我們就要把上一章的客戶 API，從記憶體暫存換成真正的 PostgreSQL 資料庫。

那第一步，就是把資料庫裝起來。這裡我要先講一個「為什麼」：為什麼我們不直接在電腦上安裝 PostgreSQL，而是要用 Docker？如果你有帶過團隊或上過實體課，你一定遇過這種情況——每個人本機的資料庫版本不一樣、初始化的內容不一樣、安裝方式也不一樣，光是把大家的環境弄到一致，就可以耗掉整個下午。Docker 的價值，就是把這些差異壓到最低。資料庫變成一個「容器」，用一份設定檔描述清楚，任何人拿到這份檔案，一個指令就能長出一模一樣的資料庫，壞掉了也可以快速重建。

還有一個原因是為了課程後面鋪路。我們 Day 2 要做 RAG，會需要一個叫 pgvector 的向量擴充功能，讓資料庫可以存向量、做相似搜尋。所以我們從一開始就不用普通的 PostgreSQL 映像，而是直接用帶有 pgvector 的映像，一次到位，之後就不用再換。

我們現在來實際操作。首先請確認你的 Docker Desktop 已經安裝而且正常運行，最簡單的驗證方式就是跑一次 docker run hello-world，看到歡迎訊息就代表 Docker 環境沒問題。接著，我們不自己手寫設定檔，而是把需求講清楚，請 AI Agent 幫我們在專案根目錄建立 docker-compose.yml。你看我給它的提示詞，重點有幾個：第一，映像要用 pgvector/pgvector:pg18，這就是 PostgreSQL 18 加上 pgvector 擴充；第二，資料庫名稱叫 learn_spring，使用者 postgres、密碼 password；第三，本機的 5432 埠對應到容器的 5432 埠，這樣我們的 Spring Boot 應用才連得進去；第四，也是最重要的一個——要用具名卷，named volume，讓資料持久化。這一點是什麼意思？容器本身是可以隨時砍掉重建的，如果資料存在容器裡面，容器一刪資料就跟著消失。具名卷就是把資料放在容器外面的一塊空間，容器重建之後再掛回來，資料就還在。這正是我們這一章反覆強調的驗收重點：重開之後，資料還要在。最後我還請它在每個設定項目加上中文註解，這樣你回頭看檔案的時候，每一行在做什麼都一目了然。

提示詞送出之後，AI Agent 會建立檔案，然後幫我們執行 docker-compose up -d 把容器在背景啟動。你會看到它接著跑 docker ps，這個指令會列出正在執行的容器，重點看 STATUS 那一欄——狀態是 Up，就代表 PostgreSQL 已經成功跑起來了。

那如果不順利呢？容器啟動失敗其實很常見，最典型的就是 5432 埠已經被本機另一個 PostgreSQL 佔走了。遇到這種情況不要慌，我準備了第二個提示詞：把 docker logs 印出來的錯誤訊息原封不動貼給 AI Agent，請它找出原因並修正。這也是我們這門課一直在練的協作模式——你不需要背下每一種錯誤的解法，但你要會把完整的錯誤訊息交給 AI，讓它幫你排查。

總結一下：這一節我們用 Docker 跑起了 PostgreSQL 18 加 pgvector 的容器，並且用具名卷確保資料不會因為容器重建而消失。資料庫有了，但現在裡面還是空的，一張表都沒有。下一節，我們要用 Flyway，以「有版本管理」的方式把資料表建起來——為什麼建表也需要版本管理？我們下一節見。
