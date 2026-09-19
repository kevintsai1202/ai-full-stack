# 延伸部署 單元 1｜把專案打包成 Docker 映像：從「能跑」到「能交付」

## 單元定位

這是延伸部署章「從 Docker 到 Kubernetes」三部曲的第一步。結訓專案完成後，你的 AI CRM 只存在於你的開發機上——原始碼、本機 JDK、本機 Node、本機資料庫缺一不可。本單元要把整套系統封裝成**映像檔（image）**：一個自帶執行環境、在任何裝了 Docker 的機器上行為一致的交付物。這是後面兩個單元（部署到 Docker 伺服器、部署到 Kubernetes）共同的地基。

- **銜接**：接在章節 8「結訓專案衝刺與 Demo Day 驗收」之後。若你已完成達標解鎖章「Cloudflare Tunnel 上線實戰」，那裡已經動手寫過多階段 Dockerfile 與 compose——本單元會快速複習核心概念，然後補上 Tunnel 章沒講的映像管理紀律：`.dockerignore`、建置快取、體積檢查與 tag 版本策略。沒看過 Tunnel 章也不影響，本單元可以獨立完成。
- **驗收標準**：後端與前端映像建置成功、改一行程式碼重建能命中快取、映像有明確版本 tag，並以 `docker compose up` 在本機完整跑通登入與 AI 對話。
- **建議時長**：延伸單元（約 20～30 分鐘）。

### 學習目標

- 理解「打包成映像」與「在我電腦上可以跑」的本質差異
- 為 Spring Boot 後端與 React 前端撰寫多階段（multi-stage）Dockerfile
- 用 `.dockerignore` 與層快取（layer cache）讓重建又快又乾淨
- 用 `docker images`、`docker history` 檢查映像體積與分層
- 建立 tag 版本策略：不再只用 `latest`
- 用 docker compose 在本機驗證整套映像可以協同運作

### 核心原則

映像是**不可變的交付物**：程式碼、依賴、執行環境一次封死，之後不管搬到哪台機器，跑起來都一樣。所以打包階段的所有決策——用哪個 base image、哪些檔案進映像、tag 怎麼命名——都是在替後面的部署鋪路。打包做得紮實，單元 2 的搬移和單元 3 的 k8s 編排就只是「換個地方執行同一個映像」；打包做得隨便，每換一個環境就重新踩一次坑。

## 教學素材

### 一、為什麼要打包：交付物的演進

回顧一下你交付軟體的方式演進：

1. **交付原始碼**：對方要自己裝 JDK 21、Node、設定環境變數，任何一個版本不對就跑不起來。
2. **交付 jar 檔**：好一點，但對方還是要有正確版本的 JRE，而且前端靜態檔、資料庫都要另外處理。
3. **交付映像**：JRE、jar、設定的讀取方式全部封在裡面，對方只需要 Docker。`docker run` 下去，行為跟你機器上一模一樣。

「在我電腦上可以跑」之所以是工程界的老哏，就是因為前兩種交付方式把環境問題留給了對方。映像把環境也變成交付物的一部分，這件事在單機時代是便利，到了 k8s 時代是前提——k8s 只認映像，不認你的原始碼。

### 二、後端多階段 Dockerfile 剖析

多階段建置（multi-stage build）的核心思想：**建置工具不進最終映像**。Maven、原始碼、`.m2` 快取只存在於建置階段，最終映像只有 JRE 加一個 jar。

```dockerfile
# ── 建置階段：用完整的 Maven + JDK 21 映像 ──
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# 先只複製 pom.xml 並下載依賴：只要 pom 沒變，這一層永遠命中快取
COPY pom.xml .
RUN mvn dependency:go-offline
# 再複製原始碼並打包：改程式碼只會讓這一層之後重跑
COPY src ./src
RUN mvn package -DskipTests

# ── 執行階段：只用精簡 JRE 映像 ──
FROM eclipse-temurin:21-jre
COPY --from=build /app/target/*.jar app.jar
# 設定值一律走環境變數注入，不烙進映像
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

三個設計重點：

1. **base image 明確指定 JDK 21**（`eclipse-temurin-21`）。本課程專案用 Java 21 語法，若 base image 版本不對，錯誤訊息常常不會直說「版本錯誤」，而是出現一堆看似編碼或檔案損壞的誤導訊息——版本要寫死在 Dockerfile 裡，不依賴任何機器的 `JAVA_HOME`。
2. **`COPY pom.xml` 與 `COPY src` 分兩層**。Docker 逐層快取：pom 沒變就不重新下載依賴，改一行 Java 程式碼的重建時間從幾分鐘縮到幾十秒。
3. **執行階段用 `-jre` 而不是完整 JDK**。跑 jar 不需要編譯器，映像體積從 800MB+ 降到 300MB 以下。

### 三、前端 Dockerfile 與 nginx 反向代理

前端同一個套路：Node 只負責建置，nginx 負責服務。

```dockerfile
# ── 建置階段 ──
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# ── 執行階段：nginx 服務靜態檔 ──
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
```

`nginx.conf` 裡把 `/api` 反向代理到後端容器（`proxy_pass http://backend:8080`），瀏覽器看到的前端與 API 是同一個來源，CORS 問題從根本上消失。這個「前端容器兼反代」的架構會原封不動延用到單元 2 的伺服器與單元 3 的 k8s。

### 四、`.dockerignore`：別把整個資料夾都送進建置

`docker build` 會把建置目錄（build context）整包送給 Docker daemon。沒有 `.dockerignore` 的話，`node_modules/`、`target/`、`.git/` 全部跟著上傳——建置變慢、快取容易失效，更糟的是 `.env` 這種含密碼的檔案可能被不小心 `COPY` 進映像。

```text
# backend/.dockerignore
target/
.git/
.env
*.md

# frontend/.dockerignore
node_modules/
dist/
.git/
.env
```

原則：**映像裡只該有「執行需要的東西」，建置 context 裡只該有「建置需要的東西」**。

### 五、檢查你的映像：體積與分層

打包完不要急著跑，先看看你做出了什麼：

```powershell
# 看映像清單與體積
docker images ai-crm-backend

# 看每一層是哪個指令產生的、各佔多少空間
docker history ai-crm-backend:1.0.0
```

`docker history` 是快取除錯的利器：如果你發現改一行程式碼後「下載依賴」那層也重跑了，通常是 `COPY` 順序寫錯（把 `COPY . .` 放在依賴下載之前），一眼就能看出來。

### 六、tag 版本策略：`latest` 是陷阱

`docker build -t ai-crm-backend .` 預設 tag 是 `latest`，但 `latest` 只是一個會被不斷覆蓋的浮動標籤——它不代表「最新版」，只代表「最後一次沒寫 tag 的 build」。部署場景的兩個災難：

- 伺服器上 `docker pull` 拉到的 `latest` 跟你以為的版本不同，而你無從查證。
- 新版有 bug 想退回上一版，但上一版的映像已經被 `latest` 覆蓋，無版可退。

從本單元開始養成習慣：**每次建置都打明確的版本 tag**，格式可以是語意化版本或 git commit 短碼：

```powershell
docker build -t ai-crm-backend:1.0.0 ./backend
# 或用 git short SHA，跟程式碼版本一一對應
docker build -t "ai-crm-backend:$(git rev-parse --short HEAD)" ./backend
```

舊 tag 的映像會留在本機，單元 2 的「一鍵退版」就是靠它。

### 七、compose 本機驗證：映像的第一次整合測試

映像各自建好後，用 docker compose 做整合驗證。與開發時期最大的差異：**compose 檔引用的是建好的映像，不是原始碼目錄**。

```yaml
services:
  backend:
    image: ai-crm-backend:1.0.0     # 用映像，不用 build:
    env_file: .env
    depends_on:
      postgres:
        condition: service_healthy
  frontend:
    image: ai-crm-frontend:1.0.0
    ports: ["80:80"]
  postgres:
    image: pgvector/pgvector:pg16
    volumes: ["pgdata:/var/lib/postgresql/data"]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U crm"]
volumes:
  pgdata:
```

跑通「登入 → 查客戶 → AI 對話」之後，你手上就有一組**驗證過的、有版本號的映像**——這是下一單元要搬去伺服器的貨。

### 實作任務

- **u10-t1**：為後端撰寫多階段 Dockerfile，建置成功並確認映像體積在 400MB 以下
- **u10-t2**：為前端撰寫多階段 Dockerfile（nginx 服務靜態檔＋反代 `/api`），建置成功
- **u10-t3**：補上 `.dockerignore`，改一行程式碼重建，用建置輸出證明依賴層命中快取
- **u10-t4**：為兩個映像打版本 tag，用 compose 引用映像啟動全套並完成登入與 AI 對話

## 示範與提示詞

### ① 打包後端映像［build］

> 多階段建置＋層快取設計，一次到位

```text
請幫我為這個 Spring Boot 專案撰寫多階段建置的 Dockerfile：建置階段用支援 Java 21 的 Maven 映像，執行階段只用精簡的 JRE 21 映像。請把「複製 pom.xml 並下載依賴」和「複製原始碼並打包」拆成不同層，讓我改程式碼時不用重新下載依賴。同時幫我建立 .dockerignore，排除建置產物、git 目錄和環境變數檔。所有設定值都要能用環境變數注入，不要寫死在映像裡。請加中文註解，並解釋每一層的快取行為。
```

### ② 打包前端映像［build］

> Node 建置、nginx 服務、反代 /api 到後端

```text
請幫我為這個 React + Vite 專案撰寫多階段建置的 Dockerfile：建置階段用 Node 映像執行 npm ci 與 npm run build，執行階段用 nginx 精簡映像服務 dist 靜態檔。另外幫我寫 nginx 設定檔，把 /api 開頭的請求反向代理到名為 backend 的容器 8080 埠，其他路徑都回傳前端頁面（SPA fallback）。請加中文註解，並說明為什麼反代之後就不會有 CORS 問題。
```

### ✅ 驗證 — 快取、體積與版本 tag［verify］

> 打包品質的三項體檢

```text
請陪我驗證剛才建好的兩個映像：第一，用 docker images 和 docker history 檢查映像體積與分層，告訴我哪幾層最大、是否合理；第二，我會改一行程式碼再重建一次，請幫我從建置輸出判斷依賴下載層有沒有命中快取；第三，幫我用版本號替兩個映像打 tag，並把 docker compose 檔改成引用映像（不用 build），啟動全套服務後完成登入與 AI 對話驗收。
```

### 🔧 排錯 — 建置失敗或跑不起來［fix］

> 常見：JDK 版本不符、快取失效、容器互連失敗

```text
我的映像建置或啟動遇到問題（我會把完整錯誤訊息貼給你）。常見狀況有：Maven 建置報出看不懂的編譯錯誤（可能是 base image 的 JDK 版本不對）、每次重建都重新下載全部依賴（可能是 COPY 順序或 .dockerignore 問題）、compose 啟動後後端連不上資料庫（可能是 healthcheck 或服務名稱問題）。請依我貼的訊息判斷根因並直接修正，修正後告訴我要用什麼指令驗證。
```

## 逐步操作與驗收

### 從原始碼到有版本號的映像

1. 先確認 Docker Desktop 執行中、專案 `mvn package` 與 `npm run build` 在本機能直接成功——本機都過不了的建置，進容器只會更難排錯。
2. 撰寫後端 Dockerfile 與 `.dockerignore`，`docker build -t ai-crm-backend:1.0.0 ./backend` 建置，記錄第一次建置時間。
3. 撰寫前端 Dockerfile、nginx 設定與 `.dockerignore`，`docker build -t ai-crm-frontend:1.0.0 ./frontend` 建置。
4. 改一行後端程式碼重建，比對兩次建置輸出：依賴下載層應顯示 `CACHED`，總時間應明顯縮短——這是 t3 的證據。
5. 用 `docker images`、`docker history` 檢查體積：後端映像應在 400MB 以下；若超過，檢查執行階段是否誤用完整 JDK。
6. compose 檔改為引用映像啟動全套，完成登入、查客戶、AI 對話，容器全停再啟確認資料仍在。

### 預期結果與證據

- 兩個映像有明確版本 tag、體積合理、重建命中快取；compose 全套跑通登入與 AI 對話。
- 交付：`docker images` 輸出、兩次建置時間對比（含 `CACHED` 字樣的輸出）、`docker history` 截圖、登入與 AI 對話畫面。

### 失敗分流

- 建置階段失敗：先看是哪個 stage——Maven 錯誤先確認 base image tag 含 `21`；npm 錯誤先確認 `package-lock.json` 有進 context（沒被 `.dockerignore` 誤殺）。
- 啟動失敗：`docker compose logs backend` 看第一個錯誤，資料庫連線失敗先查 healthcheck 與 JDBC URL 的服務名稱；前端 502 查 nginx 反代目標名稱與 port。
- 快取永遠不命中：檢查 `COPY . .` 是否寫在依賴下載之前，以及 `.dockerignore` 是否漏掉會頻繁變動的檔案（log、建置產物）。

## 口語稿

嗨，歡迎來到延伸部署章。這一章要帶你走完一條很多課程不會帶你走完的路：把你做好的 AI CRM，從你的開發機，一路部署到 Docker 伺服器，最後交給 Kubernetes 編排。三個單元就是三步：打包、搬運、編排。今天先做第一步，打包。

先講為什麼要打包。你回想一下，現在別人要跑你的系統，需要什麼？要裝 JDK 21、要裝 Node、要設定資料庫、還要把環境變數弄對——任何一步版本不對就跑不起來。這就是「在我電腦上可以跑」這個老哏的由來：交付原始碼，等於把環境問題留給對方。映像檔解決的就是這件事——JRE、程式、設定的讀法，全部封在一個不可變的交付物裡，對方只要有 Docker，跑起來就跟你機器上一模一樣。而且我先預告：第三單元的 Kubernetes 只認映像，不認原始碼，所以這一步是整章的地基。

如果你看過 Cloudflare Tunnel 那個加碼單元，多階段建置你已經動手做過了，這裡我快速複習核心，然後補上那時候沒講的映像管理紀律。多階段建置一句話：建置工具不進最終映像。後端第一階段用 Maven 加 JDK 21 的映像跑 mvn package，第二階段只用精簡 JRE 承載 jar，體積從八百多 MB 降到三百以下。這裡有個很容易踩的坑要特別提醒：base image 的 JDK 版本一定要明確寫 21。版本不對的時候，錯誤訊息常常不會直說版本錯誤，而是丟一堆看起來像編碼壞掉、檔案損毀的訊息，你會排錯排到懷疑人生。版本寫死在 Dockerfile 裡，不要依賴任何機器的 JAVA_HOME。

再來是快取設計，這是本單元第一個新重點。Docker 是逐層快取的，所以 Dockerfile 裡指令的順序就是快取策略。我們把「複製 pom.xml 下載依賴」跟「複製原始碼打包」拆成兩層——pom 沒變，依賴那層就永遠命中快取，改一行程式碼重建，時間從幾分鐘縮到幾十秒。前端同一個道理，先複製 package.json 跑 npm ci，再複製原始碼建置。等一下實作的時候我們會故意改一行程式碼重建一次，你會在建置輸出裡看到 CACHED 這個字——那就是證據。

第二個新重點：.dockerignore。docker build 會把整個目錄送給 Docker，你不擋的話，node_modules、target、.git 全部跟著上傳，建置又慢、快取又容易失效。更嚴重的是 .env 這種放密碼的檔案，可能被不小心複製進映像——映像是會到處搬的，密碼跟著搬就出事了。原則一句話：映像裡只放執行需要的東西，建置 context 只放建置需要的東西。

第三個新重點，也是我最想讓你帶走的習慣：tag 版本策略。很多人 build 完就是一個 latest 用到底。但 latest 不是「最新版」的意思，它只是「最後一次沒寫 tag 的 build」，一個會被不斷覆蓋的浮動標籤。部署場景會發生兩個災難：伺服器上拉到的 latest 跟你以為的版本不同，你無從查證；新版有 bug 想退回上一版，發現上一版已經被覆蓋掉了，無版可退。所以從今天開始，每次建置都打明確的版本號，1.0.0 也好、git commit 短碼也好。下一個單元的一鍵退版，靠的就是這些留在機器上的舊 tag。

好，我們現在來動手。打開教材裡的第一個提示詞，請 AI 幫你寫後端的多階段 Dockerfile 加 .dockerignore，注意看它產出的分層順序；接著第二個提示詞做前端，nginx 服務靜態檔、反代 /api 到後端——反代之後前端跟 API 對瀏覽器來說是同一個來源，CORS 問題從根本上消失，這個架構會一路用到 k8s。build 完先別急著跑，用 docker images 看體積、docker history 看分層，後端超過四百 MB 通常是執行階段誤用了完整 JDK。然後改一行程式碼重建，看到依賴層 CACHED、時間大幅縮短，快取設計就驗證完成了。

最後做整合驗證：把 compose 檔改成引用映像，不是指向原始碼目錄——這一步很有儀式感，因為從這一刻起，跑你系統的不再是你的程式碼，而是一個有版本號的交付物。啟動全套，登入、查客戶、跟 AI 對話，容器全停再啟確認資料還在。全部通過，你手上就有一組驗證過的、有版本號的映像。

總結一句話：打包不是把東西塞進容器就好，而是做出一個環境自帶、快取友善、版本可考的交付物。下一個單元，我們就把這組映像搬到另一台機器上跑起來——真正的「部署」，要開始了。我們下個單元見。
