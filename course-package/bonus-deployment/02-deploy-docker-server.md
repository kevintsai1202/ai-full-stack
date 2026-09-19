# 延伸部署 單元 2｜把映像部署到 Docker 伺服器：搬運、上線、更新與退版

## 單元定位

上一單元做出了有版本號的映像，但它們還在你的開發機上。本單元解決「搬到另一台機器跑起來」這件事——也就是大多數中小型系統真實的上線形態：一台裝了 Docker 的伺服器（公司內部主機、雲端 VM、家裡的舊電腦都算），用 docker compose 把服務跑起來。你會學到兩條搬運路線（免 registry 的 save/load 與正規的 registry 推拉）、伺服器端的設定注入，以及上線後最重要的日常操作：更新版本與一鍵退版。

- **銜接**：使用單元 1 產出的 `ai-crm-backend:1.0.0` 與 `ai-crm-frontend:1.0.0` 映像。若你手邊真的沒有第二台機器，可以在同一台機器上用「刪除本機映像再還原」的方式完整演練搬運流程，流程與指令完全相同。
- **驗收標準**：映像成功搬到目標環境、compose 啟動全套服務、外部瀏覽器完成登入與 AI 對話，並實際演練一次「更新到新版 → 發現問題 → 退回舊版」。
- **建議時長**：延伸單元（約 20～30 分鐘）。

### 學習目標

- 理解部署的三要素：映像、設定、資料，以及三者分離的原因
- 用 `docker save` / `docker load` 在沒有 registry 的環境搬運映像
- 理解 registry 的角色，並能把映像推上 GHCR（GitHub Container Registry）
- 在伺服器端用 compose + `.env` 完成設定注入與啟動
- 完成版本更新與退版操作，理解為什麼舊 tag 是你的保險
- 收斂伺服器的暴露面：只開必要的 port

### 核心原則

部署的本質是把三樣東西放到目標機器上：**映像**（程式與環境）、**設定**（每個環境不同的變數與密碼）、**資料**（volume，跟著機器走不跟著映像走）。三者嚴格分離：映像到處搬但永不修改、設定留在各環境的 `.env` 不進版控、資料只存在 volume。分離做得乾淨，「更新」就只是換一個 tag，「退版」就只是換回舊 tag——資料與設定原地不動。

## 教學素材

### 一、部署三要素：映像、設定、資料

先建立心智模型。一套跑在伺服器上的系統由三個生命週期完全不同的東西組成：

| 要素 | 內容 | 生命週期 | 存放位置 |
|---|---|---|---|
| 映像 | 程式碼＋執行環境 | 每次發版換新，舊版保留 | 本機映像庫或 registry |
| 設定 | DB 密碼、JWT secret、AI key、網址 | 每個環境一份，很少變動 | 伺服器上的 `.env`（不進 git） |
| 資料 | PostgreSQL 的資料目錄 | 持續成長，絕不隨映像換版消失 | named volume |

所有部署事故幾乎都源自三者混在一起：密碼烙進映像（換環境就爆）、資料放在容器內（換版就消失）、設定進了 git（洩漏）。三者分離之後，接下來的每個操作都變得單純。

### 二、路線 A：免 registry 的 save / load 搬運

沒有 registry、目標機器在內網、或只是想快速驗證時，最直接的搬運方式是把映像匯出成檔案：

```powershell
# 開發機：把映像匯出成 tar 檔（兩個映像一起打包）
docker save -o ai-crm-images.tar ai-crm-backend:1.0.0 ai-crm-frontend:1.0.0

# 把 tar 檔傳到伺服器（scp、隨身碟、內網共享都行）
scp ai-crm-images.tar user@server:/opt/ai-crm/

# 伺服器：把映像載入本機映像庫
docker load -i ai-crm-images.tar
docker images   # 確認兩個映像都在、tag 正確
```

這條路線的優點是零依賴——不需要帳號、不需要對外網路；缺點是每次發版都要手動搬檔案，映像也不小（幾百 MB）。它適合內網部署、教學演練與緊急救援，正式的持續部署還是要走 registry。

> 沒有第二台機器的演練方式：`docker save` 之後，用 `docker rmi` 把本機的兩個映像刪掉（模擬「目標機器上沒有映像」），再 `docker load` 還原——整條搬運流程的指令與驗證方式一模一樣。

### 三、路線 B：registry 推拉（GHCR）

registry 是映像的集中倉庫：開發機 `push` 上去，任何一台伺服器 `pull` 下來。Docker Hub 是預設倉庫，這裡示範 GHCR——因為你已經有 GitHub 帳號，而且它跟程式碼倉庫放在同一個地方。

```powershell
# 1. 用 GitHub Personal Access Token（勾選 write:packages 權限）登入
docker login ghcr.io -u <你的GitHub帳號>

# 2. registry 的 tag 有命名規則：ghcr.io/<帳號>/<映像名>:<版本>
docker tag ai-crm-backend:1.0.0 ghcr.io/<帳號>/ai-crm-backend:1.0.0
docker push ghcr.io/<帳號>/ai-crm-backend:1.0.0

# 3. 伺服器端拉取（私有映像需要先 docker login）
docker pull ghcr.io/<帳號>/ai-crm-backend:1.0.0
```

兩個要點：

1. **registry tag 是「地址＋名字＋版本」**：`ghcr.io/帳號/名字:版本`。`docker tag` 不會複製映像，只是幫同一個映像多掛一個名字。
2. **預設是私有的**。推上去的映像預設只有你能拉，要給伺服器拉就在伺服器上 `docker login`；千萬不要為了省登入把含商業邏輯的映像設成 public。

### 四、伺服器端的 compose 與 `.env`

伺服器上只需要兩個檔案：`docker-compose.yml` 與 `.env`。compose 檔可以進 git（裡面沒有秘密），`.env` 只存在伺服器上。

```yaml
# /opt/ai-crm/docker-compose.yml
services:
  backend:
    image: ghcr.io/<帳號>/ai-crm-backend:${APP_VERSION}
    env_file: .env
    restart: unless-stopped
    depends_on:
      postgres:
        condition: service_healthy
  frontend:
    image: ghcr.io/<帳號>/ai-crm-frontend:${APP_VERSION}
    restart: unless-stopped
    ports: ["80:80"]
  postgres:
    image: pgvector/pgvector:pg16
    restart: unless-stopped
    env_file: .env
    volumes: ["pgdata:/var/lib/postgresql/data"]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U crm"]
volumes:
  pgdata:
```

```text
# /opt/ai-crm/.env（只存在伺服器，權限收緊）
APP_VERSION=1.0.0
POSTGRES_PASSWORD=<生產專用強密碼>
JWT_SECRET=<生產專用隨機長字串>
OPENAI_API_KEY=<生產用的 key>
```

跟開發環境的三個差異值得注意：

1. **`restart: unless-stopped`**：伺服器重開機後服務自動回來，這在開發機上不需要、在伺服器上是必備。
2. **版本號抽成 `${APP_VERSION}`**：整套系統的版本由 `.env` 的一行控制，這就是等下更新與退版的開關。
3. **只有 frontend 開 port**：postgres 完全不寫 `ports:`，後端也不需要——nginx 反代是唯一入口。防火牆上也只開 80（有 HTTPS 需求再開 443，或直接套用 Tunnel 章的 Cloudflare Tunnel，一個 port 都不用開）。

### 五、日常操作：更新與退版

上線不是終點，之後每一次改版都是同一套循環：

```powershell
# ── 更新到 1.1.0 ──
# 開發機：build → tag → push（或 save/scp/load）
# 伺服器：
#   1. 改 .env 的 APP_VERSION=1.1.0
#   2. 重新拉起（compose 只會重建映像變了的服務）
docker compose pull
docker compose up -d
docker compose ps          # 確認狀態
docker compose logs -f backend   # 盯一下啟動 log

# ── 發現 1.1.0 有問題，退版 ──
#   1. 改 .env 的 APP_VERSION=1.0.0
#   2. 再跑一次 up
docker compose up -d
```

退版之所以能在一分鐘內完成，靠的是兩件你在單元 1 就開始做的事：**舊 tag 的映像還在**（沒被 latest 覆蓋），以及**資料在 volume 裡**（換版不動資料）。這裡也要誠實講一個限制：資料庫 schema 如果在新版跑了不可逆的 migration，退版就不只是換 tag 的事——所以 Flyway 的 migration 要保持向後相容至少一個版本，這是章節 3 教過的紀律在部署場景的回報。

### 六、上線檢查清單

- `.env` 權限收緊、不在 git 歷史裡；密碼與 JWT secret 都換成生產專用值
- postgres 與 backend 沒有對外 port，防火牆只開 80/443
- `restart: unless-stopped` 已設定，重開機自動恢復
- `docker compose ps` 全部 healthy；伺服器重開機後服務自動回來
- 舊版本映像保留至少一版，退版流程實際演練過一次

### 實作任務

- **u11-t1**：用 `docker save` / `docker load` 完成一次映像搬運（雙機或單機模擬皆可），`docker images` 證明載入成功
- **u11-t2**：在目標環境建立 compose＋`.env`，啟動全套服務並從另一台裝置的瀏覽器完成登入與 AI 對話
- **u11-t3**：發布 1.1.0（可以只是改個頁面標題）、更新上線，再演練退回 1.0.0，全程記錄指令與結果
- **u11-t4**：（選做）把兩個映像推上 GHCR 並在目標環境拉取成功

## 示範與提示詞

### ① 免 registry 搬運部署［build］

> save / load 把映像帶去任何一台有 Docker 的機器

```text
我在開發機上有兩個建好的映像（後端和前端，各有版本 tag），想把它們部署到另一台裝了 Docker 但連不上 registry 的機器。請教我用 docker save 把兩個映像打包成一個檔案、傳到目標機器後用 docker load 還原，並告訴我每一步怎麼驗證成功（例如怎麼確認載入後的 tag 正確）。如果我手邊沒有第二台機器，請設計一個在同一台機器上完整演練這個流程的方法。
```

### ② 推上 GHCR（選做）［build］

> 正規的映像發布路線

```text
請教我把本機映像推上 GitHub Container Registry：從建立 Personal Access Token（需要哪些權限）、docker login、依照 ghcr.io 的命名規則重新 tag、到 push 成功。然後告訴我在另一台伺服器上要怎麼登入並拉取這個私有映像。請順便解釋 docker tag 指令做的事情是複製映像還是掛別名，以及為什麼映像預設應該保持私有。
```

### ③ 伺服器端 compose 上線［build］

> 設定注入、restart 策略、最小暴露

```text
請幫我為生產伺服器撰寫 docker compose 檔與 .env 範本：後端與前端引用有版本號的映像（版本號抽成環境變數，讓我之後改一行就能換版）、資料庫用支援向量檢索的 PostgreSQL 並掛持久化 volume、全部服務設定成重開機自動恢復。只有前端對外開 port，資料庫和後端都不對外。所有密碼放 .env 並告訴我怎麼收緊這個檔案的權限。請加中文註解，並列出上線前的檢查清單。
```

### ✅ 驗證 — 更新與退版演練［verify］

> 換版只換 tag，資料原地不動

```text
請陪我完整演練一次版本更新與退版：我先改一個看得到的小地方（例如頁面標題）發布成新版本映像，在伺服器上更新 .env 的版本號並重新拉起，用瀏覽器確認改動生效、資料還在；然後假設新版有問題，把版本號改回上一版再拉起一次，確認畫面退回舊版、資料依然完好。每一步請告訴我要記錄什麼證據，最後解釋為什麼這個流程能這麼快，以及什麼情況下退版沒有這麼簡單（提示：資料庫 migration）。
```

### 🔧 排錯 — 搬運或啟動失敗［fix］

> 常見：pull 權限、port 佔用、環境變數沒吃到

```text
我在伺服器部署時遇到問題（我會把錯誤訊息貼給你）。常見狀況有：docker pull 回報 denied 或 unauthorized（可能是私有映像沒登入或 token 權限不足）、compose up 說 port is already allocated（可能是伺服器上有舊服務佔用）、容器起來了但後端報資料庫密碼錯誤（可能是 .env 沒被讀到或變數名稱不符）、瀏覽器打得開前端但 API 全部失敗。請依我貼的訊息判斷根因並直接修正。
```

## 逐步操作與驗收

### 從開發機到伺服器的完整流程

1. 選定搬運路線：內網或演練走 save/load，有 GitHub 帳號想走正規流程就推 GHCR；兩條路線的終點相同——目標環境 `docker images` 看得到兩個有版本號的映像。
2. 在目標環境建立部署目錄（如 `/opt/ai-crm/`），放入 compose 檔並手工建立 `.env`：所有密碼換成生產專用值，`APP_VERSION` 設為當前版本。
3. `docker compose up -d` 啟動，`docker compose ps` 確認三個服務 healthy，`docker compose logs backend` 確認 Flyway migration 跑完、應用啟動成功。
4. 從**另一台裝置**的瀏覽器連伺服器 IP，完成登入、查客戶、AI 對話——用別台裝置才能證明不是 localhost 幻覺。
5. 演練更新：發布 1.1.0 → 伺服器改 `APP_VERSION` → `docker compose pull && docker compose up -d` → 瀏覽器確認改動生效。
6. 演練退版：`APP_VERSION` 改回 1.0.0 → `up -d` → 確認畫面退回、資料完好。記錄整輪的指令與時間。

### 預期結果與證據

- 目標環境跑起全套服務、外部裝置完成業務驗收、更新與退版各成功一次且資料無損。
- 交付：搬運過程輸出（save/load 或 push/pull）、`docker compose ps` 結果、外部裝置操作畫面、更新前後與退版後的版本證據（頁面差異截圖）、`.env` 範本（密碼遮罩）。

### 失敗分流

- 映像層問題（load 後 tag 不對、pull denied）先解決再碰 compose——地基沒好不要蓋房子。
- 容器起了但行為不對：優先懷疑設定注入——`docker compose config` 看展開後的環境變數（密碼會顯示，注意別截圖外流）、確認 `.env` 與 compose 在同一目錄。
- 外部裝置連不上但伺服器本機可以：查伺服器防火牆與雲端安全群組的 80 port 是否放行，這一步跟 Docker 無關。

## 口語稿

歡迎回來，延伸部署第二單元。上一集我們做出了有版本號的映像，這一集要做的事情聽起來很簡單：把它們搬到另一台機器上跑起來。但「部署」這兩個字的所有細節——搬運、設定、更新、退版——都藏在這個聽起來很簡單的動作裡。

先建立一個心智模型，這是本單元最重要的一張圖：一套跑在伺服器上的系統，是三個生命週期完全不同的東西組成的。第一是映像，程式加環境，每次發版換新的，但舊版要留著。第二是設定，資料庫密碼、JWT secret、AI 的 key，每個環境一份，放在伺服器的 .env 檔裡，永遠不進 git。第三是資料，PostgreSQL 的資料目錄，放在 volume 裡持續長大，絕對不能跟著換版消失。你去看所有部署出過的事故，幾乎都是這三個東西混在一起造成的：密碼烙進映像、資料放在容器裡、設定推上 git。三者分離之後，你會發現部署的每個操作都變得非常單純。

那映像怎麼搬過去？兩條路線。路線 A 最直接，連 registry 都不用：docker save 把映像匯出成一個 tar 檔，用 scp、隨身碟、內網共享隨便什麼方式傳到伺服器，docker load 載入，結束。零依賴、不用帳號、不用對外網路，內網環境跟緊急救援的救命招。如果你手邊沒有第二台機器也沒關係，save 完把本機映像刪掉，模擬目標機器的狀態，再 load 回來——整條流程的指令跟驗證一模一樣，照樣算完成任務。

路線 B 是正規做法：registry。它就是映像的集中倉庫，開發機 push 上去，任何伺服器 pull 下來。我們用 GHCR，因為你本來就有 GitHub 帳號。有兩個點要講清楚。第一，registry 的 tag 有命名規則：ghcr.io 斜線你的帳號斜線映像名冒號版本，而 docker tag 這個指令不是複製映像，只是幫同一個映像多掛一個名字。第二，推上去的映像預設是私有的，伺服器要拉就在伺服器上登入一次——不要為了省這個登入把映像設成 public，裡面可是你的商業邏輯。

映像到位之後，伺服器上只需要兩個檔案：compose 檔跟 .env。compose 檔可以進 git，因為裡面沒有任何秘密；.env 只存在伺服器上。跟開發環境比，有三個差異你要注意。第一，每個服務都加 restart unless-stopped，伺服器重開機服務自動回來，這在開發機不需要，在伺服器是必備。第二，映像的版本號抽成變數，寫成 APP_VERSION，整套系統的版本由 .env 的一行字控制——這就是等下更新退版的開關。第三，最小暴露：只有前端開 80 port，postgres 跟後端連 ports 都不寫，nginx 反代是唯一入口，防火牆也只開 80。想要 HTTPS 跟隱藏 IP？直接把 Tunnel 章的 cloudflared 加進來，連 80 都不用開。

我們現在來上線。用第三個提示詞請 AI 生出伺服器版的 compose 跟 .env 範本，密碼全部換成生產專用值，up -d 啟動，compose ps 看三個服務 healthy，log 裡確認 Flyway 跑完。然後重點來了：驗收要用另一台裝置的瀏覽器連伺服器 IP，登入、查客戶、跟 AI 對話。為什麼堅持用別台裝置？因為 localhost 是會騙人的，只有從外面連進來成功，才證明你真的部署好了。

接著是我認為本單元最有價值的演練：更新與退版。你改個頁面標題，發布 1.1.0，伺服器上把 APP_VERSION 改成 1.1.0，compose pull、up -d，瀏覽器看到新標題——更新完成，資料原地不動。然後我們假裝 1.1.0 有 bug：APP_VERSION 改回 1.0.0，再 up 一次，畫面退回舊版，資料還是完好。整輪不用一分鐘。它為什麼能這麼快？因為你在上一個單元就開始留舊 tag，因為資料在 volume 裡不跟映像走。不過我也要誠實講一個限制：如果新版跑了不可逆的資料庫 migration，退版就不只是換 tag 的事了——所以章節三教的 Flyway 紀律，migration 保持向後相容，在這裡拿到了回報。

收尾前把上線檢查清單過一遍：.env 權限收緊、密碼全換生產值、資料庫跟後端不對外、restart 策略設好、舊版映像至少留一版、退版流程真的演練過。全部打勾，你就有一套能持續營運的部署，而不是「上線一次就不敢動」的花瓶。

總結一句話：部署等於映像、設定、資料三者分離，之後的更新與退版都只是換一個 tag。下一個單元是最後一步，我們把同一組映像交給 Kubernetes——你會看到 compose 裡的每一行，在 k8s 裡都有一個對應的概念，而 k8s 額外給你的，是自癒跟滾動更新。我們下個單元見。
