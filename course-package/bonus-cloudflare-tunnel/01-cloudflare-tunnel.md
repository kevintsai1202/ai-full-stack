# 達標解鎖｜Cloudflare Tunnel 上線實戰

## 單元定位

這是課程募資達到 100 人解鎖的加碼單元，感謝所有學員讓它成真。本單元要解決的問題是：結訓專案做完之後，AI CRM 只能在 `localhost` 給自己看——本單元帶你用 Docker Compose 把前端、後端、資料庫打包成一鍵啟動的部署組合，再透過 Cloudflare Tunnel 反向打洞，把跑在你自己內網機器上的系統推向全世界：免公網 IP、免開防火牆 port、零月費。

- **銜接**：接在章節 8「結訓專案衝刺與 Demo Day 驗收」之後，是整個 AI CRM 主軸專案的最後一哩路——從「Demo 給評審看」變成「外網手機 4G 直接打開你家機器上的 AI CRM」。
- **驗收標準**：外部裝置（非同一內網）完成登入與 AI 對話，容器全停再啟後資料仍在。
- **建議時長**：延伸單元 · 上線實戰（單一長單元，約 25～35 分鐘）。

### 學習目標

- 用 Docker Compose 把前端、後端、資料庫打包成一鍵啟動的部署組合
- 理解 Cloudflare Tunnel 反向打洞的網路模型與安全優勢
- 用 Quick Tunnel 免網域取得公開網址，讓外部實際連入內網系統
- 能判斷何時需要自備網域走 Named Tunnel 取得固定網址
- 完成 CORS、secret 與管理介面的上線安全收尾

### 核心原則

上線的本質是把「只有你的電腦看得到的系統」變成「全世界都連得到的服務」，而風險控制的關鍵在於連線方向。Cloudflare Tunnel 讓內網機器只主動向外建立連線，不在防火牆上開任何入站孔洞；系統打包則交給 Docker Compose，把四個服務的啟動順序、資料持久化與環境變數固定成一份可重複執行的檔案，讓「在我電腦上可以跑」變成「在任何一台機器上都可以跑」。

## 教學素材

### 一、部署選項全景：從 Demo Day 到真正上線

把系統推上網有三條主要路線，各有適用場景：

1. **雲平台 PaaS（如 Zeabur、Render）**：最省事，push 程式碼就自動建置部署，但服務與資料庫都在別人機器上，長期執行有月費。
2. **Cloudflare Containers**：Cloudflare 的容器服務已正式 GA，可以用 wrangler 部署 Docker 映像到全球邊緣節點，但需要 Workers Paid 方案（US$5/月），且容器是「用完即睡」的無狀態運算——**不適合跑 PostgreSQL 這種有狀態資料庫**（Cloudflare 自家的 D1 是 SQLite，沒有 pgvector）。
3. **自架內網 + Cloudflare Tunnel（本單元主軸）**：整套系統跑在你自己的機器上（家用電腦、公司閒置主機、NAS 都行），Cloudflare 只負責把外部流量安全地送進來。零月費、資料完全在自己手上，而且不需要公網 IP。

本單元選擇路線 3：它最能延續課程「所有元件都自己掌控」的精神，pgvector 資料庫照常運作，也是自架服務（self-hosting）社群最主流的上線方式。

### 二、Dockerfile 多階段建置（打包整套系統）

部署的第一步是把前後端各自打包成映像檔。多階段建置（multi-stage build）讓建置工具留在建置階段，最終映像只帶執行需要的東西：

**後端（Spring Boot）**
- 第一階段用 Maven 映像執行 `mvn package`，產出 fat jar
- 第二階段只用精簡 JRE 映像（如 `eclipse-temurin:21-jre`）承載 jar，映像體積從 800MB+ 降到 300MB 以下

**前端（React + Vite）**
- 第一階段用 Node 映像執行 `npm run build`，產出靜態檔
- 第二階段用 nginx 映像服務 `dist/` 靜態檔，並反向代理 `/api` 到後端容器

```dockerfile
# 後端多階段建置示意
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

FROM eclipse-temurin:21-jre
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

nginx 反向代理讓前端和後端對瀏覽器來說是同一個網址來源（same origin），CORS 問題自然消失，這也是為什麼 Tunnel 只需要開一個入口。

### 三、docker-compose.yml 四服務編排

Docker Compose 把四個服務的啟動順序、網路與資料持久化寫成一份宣告式檔案：

| 服務 | 映像 | 職責 |
|---|---|---|
| frontend | 自建 nginx 映像 | 服務靜態檔 + 反代 /api |
| backend | 自建 Spring Boot 映像 | REST API + AI 整合 |
| postgres | pgvector/pgvector:pg16 | 資料庫 + 向量檢索 |
| cloudflared | cloudflare/cloudflared | 對外打洞 |

三個關鍵設定：

1. **volume 持久化**：postgres 的資料目錄一定要掛 named volume，否則容器重建資料就消失。
2. **healthcheck 與 depends_on**：backend 要等 postgres 健康檢查通過才啟動，避免開機時連線失敗；cloudflared 等 frontend 就緒。
3. **環境變數與 secret**：資料庫密碼、JWT secret、OpenAI API key 統一放 `.env` 檔（記得加入 `.gitignore`），compose 檔用 `${VAR}` 引用，不把任何密碼寫死在檔案裡。

容器之間用服務名稱互相連線（如 `jdbc:postgresql://postgres:5432/crm`），Docker 內建的 DNS 會解析，完全不需要知道容器 IP。

### 四、Tunnel 反向連線原理：為什麼不用開 port

傳統把內網服務公開的做法是 port forwarding：在路由器上開一個入站孔洞，把公網 IP 的某個 port 轉到內網機器。問題很多——需要固定公網 IP（或 DDNS）、防火牆上有永久開放的攻擊面、住宅網路常常拿不到真實公網 IP（CGNAT）。

Cloudflare Tunnel 把方向反過來：

1. 內網機器上的 `cloudflared` 程式**主動向外**連到 Cloudflare 邊緣節點，建立加密的持久連線
2. 外部使用者造訪你的公開網址時，流量進到 Cloudflare 邊緣
3. Cloudflare 沿著這條「已經打好的洞」把請求反向送回你的內網機器

因為連線是由內往外建立的（跟你打開瀏覽器上網是同一個方向），所以：

- **不需要公網 IP**——CGNAT、4G 分享、宿舍網路都能用
- **防火牆一個 port 都不用開**——沒有入站規則就沒有入站攻擊面
- **來源 IP 被隱藏**——外界只看得到 Cloudflare，看不到你家的 IP

### 五、Quick Tunnel 實作：五分鐘取得公開網址

Quick Tunnel（TryCloudflare）是最快的入門方式：**不用註冊帳號、不用網域、不用任何設定**，一行指令就取得公開網址：

```powershell
# 直接用 Docker 跑 cloudflared，把 frontend 服務打洞出去
docker run --rm --network ai-crm_default cloudflare/cloudflared:latest tunnel --url http://frontend:80
```

執行後畫面會顯示一個隨機網址（形如 `https://xxxx-yyyy.trycloudflare.com`），把它傳到手機上用 4G 開啟——你內網機器上的 AI CRM 就這樣上線了。

寫進 docker-compose.yml 則是：

```yaml
  cloudflared:
    image: cloudflare/cloudflared:latest
    command: tunnel --url http://frontend:80
    depends_on:
      - frontend
```

Quick Tunnel 的限制要心裡有數：

- 網址是**隨機的**，每次重啟都會換
- 官方不保證可用性，**只適合 demo 與測試**
- 不能綁自己的網域、不能搭配 Cloudflare Access 做存取控制

要固定網址與正式經營，就要進入下一節的 Named Tunnel。

### 六、需要自備網域嗎？Named Tunnel 與固定網址（選做）

「要不要買網域」是這個部署路線最常見的問題，答案取決於用途：

| 情境 | 需要網域嗎 | 做法 |
|---|---|---|
| 課堂 demo、給朋友看 | 不需要 | Quick Tunnel 隨機網址 |
| 作品集、長期經營、正式服務 | **需要** | Named Tunnel + 自有網域 |

Named Tunnel 的前置條件是**網域必須掛在 Cloudflare DNS**（Cloudflare 免費方案即可）。網域本身一年約 US$10，可以直接在 Cloudflare Registrar 購買（成本價、免轉入設定），也可以在其他註冊商買了再把 DNS 指到 Cloudflare。

設定流程（選做實作）：

1. Cloudflare Zero Trust 後台建立 Tunnel，取得 token
2. cloudflared 改用 `tunnel run --token <TOKEN>` 啟動
3. 在 Tunnel 的 Public Hostname 設定 `crm.你的網域.com` 指向 `http://frontend:80`
4. DNS 紀錄自動建立，固定網址即刻生效，HTTPS 憑證由 Cloudflare 自動簽發

完成後你的 AI CRM 就有一個可以印在履歷上的正式網址，而伺服器還是你家裡那台機器。

### 七、上線安全收尾

系統一旦公開，安全設定就從「作業要求」變成「真實防線」。上線前逐項確認：

1. **管理介面不該全世界看得到**：用了 Named Tunnel 後，可以在 Cloudflare Access 免費設定存取政策：例如 `/admin` 路徑或 Swagger UI 只允許特定 Email 登入後存取，等於在你的系統前面多了一道 Cloudflare 的登入牆。
2. **CORS 白名單收斂**：開發時常設 `allowedOrigins("*")`，上線前必須收斂成正式網址；如果前端由 nginx 同源反代，甚至可以完全關閉跨來源存取。
3. **secret 全面體檢**：JWT secret 換成生產專用的隨機長字串、資料庫密碼不用預設值、所有 secret 只存在 `.env`（已加入 .gitignore），確認 git 歷史沒有洩漏。
4. **最小暴露原則**：postgres 的 5432 不需要對外，compose 裡不要寫 `ports:` 對映——容器網路內部互通即可。Tunnel 只指向 frontend 一個入口。

### 實作任務

- **u9-t1**：為前端與後端撰寫多階段 Dockerfile 並建置成功
- **u9-t2**：撰寫 docker-compose.yml 一鍵啟動四服務，確認資料持久化
- **u9-t3**：用 cloudflared Quick Tunnel 取得公開網址
- **u9-t4**：手機 4G 連入完成登入與 AI 對話驗收
- **u9-t5**：（選做）自備網域設定 Named Tunnel 固定網址與 Access 保護

## 示範與提示詞

### ① 把整套系統打包成 Docker Compose［build］

> 前端、後端、資料庫三合一，一鍵啟動

```text
請幫我把這個專案打包成可以一鍵啟動的部署組合：網頁前端做成一個容器（用 nginx 服務靜態檔，並把 API 請求轉給後端）、後端做成一個容器（用多階段建置讓映像越小越好）、資料庫用支援向量檢索的 PostgreSQL 映像。資料庫的資料要放在持久化空間，容器重建後資料不能消失；所有密碼和金鑰都放在獨立的環境變數檔案，不要寫死在設定裡。完成後教我用一個指令啟動全部服務，並確認彼此連得通。請加中文註解。
```

### ② 用 Cloudflare Tunnel 打洞上線［build］

> 免公網 IP、免開防火牆 port 取得公開網址

```text
我的系統跑在內網機器上，沒有公網 IP，也不想在防火牆開任何 port。請幫我在剛才的部署組合中加入 Cloudflare 的打洞服務（cloudflared），用免費的快速通道模式把網頁前端公開出去，讓我拿到一個外部可以連的網址。請解釋這個連線是往哪個方向建立的、為什麼這樣比開 port 安全，以及這個隨機網址有什麼限制。
```

### ③ 選做 — 綁定自己的網域取得固定網址［build］

> 有自備網域者：Named Tunnel + 固定子網域

```text
我有一個自己的網域，已經把 DNS 掛到 Cloudflare 上。請一步一步教我把剛才的快速通道升級成正式通道：在 Cloudflare 後台建立通道、把啟動方式改成用 token、設定一個固定子網域指向我的網頁前端，並確認 HTTPS 憑證自動生效。最後幫我用 Cloudflare Access 把管理介面保護起來，只允許我的 Email 登入後存取。
```

### ✅ 驗證 — 手機 4G 完整走一次系統［verify］

> 非同一內網連入才算真正上線

```text
請陪我完成上線驗收：先確認一個指令能啟動全部服務，再把公開網址傳到手機上，關掉 WiFi 改用行動網路開啟網頁，完成登入、瀏覽客戶、跟 AI 對話並確認知識庫問答正常。最後測試把容器全部停掉重新啟動，確認資料都還在。全部通過才算真正上線成功。
```

### 🔧 排錯 — 容器互連或打洞失敗［fix］

> 常見：後端連不上資料庫、通道網址打不開

```text
我照步驟做但遇到問題（我會把錯誤訊息貼給你）。常見狀況有：後端容器啟動時連不上資料庫、前端打得開但 API 都失敗、打洞服務啟動了但外部網址打不開。請依我貼的訊息判斷是啟動順序、容器網路名稱、反向代理設定還是通道設定的問題，並直接幫我修正。
```

## 口語稿

嗨，歡迎來到這個特別的單元。先說一件開心的事：這一章是課程達到一百人解鎖的加碼單元，能夠錄這一集，完全是因為大家的支持，真的謝謝你們。那既然是加碼，我就要帶你做一件最有成就感的事——把你的 AI CRM 真正推上網路。

先講為什麼。你想想看，我們一路從章節一走到章節八，環境、REST API、資料庫、JWT、React 工作台、Spring AI、RAG，最後在 Demo Day 把整套系統跑給大家看。可是 Demo 完之後呢？這套系統還是只活在 `localhost`，只有你自己的電腦看得到。你做了一個會給銷售建議、能追蹤 AI 決策過程的智慧系統，結果想給朋友看一眼，還得叫他來你家。這太可惜了。

那你可能會說，好啊，那我把它放上網就好了嘛。問題就出在這裡。傳統做法是 port forwarding：去路由器上開一個入站的孔，把公網 IP 的某個 port 轉到你的內網機器。但你馬上會遇到幾個很現實的麻煩。第一，你需要一個固定的公網 IP，不然就要弄 DDNS。第二，防火牆上永遠開著一個洞，那就是一個永久的攻擊面。第三，也是最多人卡住的——現在很多住宅網路根本拿不到真實的公網 IP，你在電信商的 CGNAT 後面，路由器上看到的那個 IP 不是真的對外 IP，你怎麼開 port 都沒有用。宿舍網路、4G 分享也是一樣的狀況。

所以這個單元我們選的路線是：系統整套跑在你自己的機器上——家用電腦、公司閒置主機、NAS 都行——然後用 Cloudflare Tunnel 把外部流量安全地送進來。免公網 IP、防火牆一個 port 都不用開、零月費，資料還完全在自己手上。順帶一提，上網的路線其實有三條：雲平台 PaaS 像 Zeabur、Render 最省事，但長期跑有月費，資料庫也在別人機器上；Cloudflare 自家的 Containers 服務要付費方案，而且容器是用完即睡的無狀態運算，不適合跑 PostgreSQL 這種有狀態的資料庫——它家的 D1 是 SQLite，沒有 pgvector，我們的向量檢索就沒了。所以自架加 Tunnel 這條路，最能延續我們整堂課「所有元件都自己掌控」的精神。

好，進入怎麼做。上線分兩步：第一步先把系統打包，第二步再打洞出去。

打包的部分，我們用 Docker Compose。第一件事是把前後端各自做成映像檔，這裡有個關鍵技巧叫多階段建置。以後端來說，第一階段用 Maven 的映像跑 `mvn package`，把 fat jar 做出來；第二階段只用精簡的 JRE 映像，像 `eclipse-temurin:21-jre`，把 jar 搬進去。建置工具留在建置階段，最終映像只帶執行需要的東西，體積可以從八百多 MB 降到三百 MB 以下。前端也是同一個套路：第一階段用 Node 跑 `npm run build`，第二階段用 nginx 服務 `dist/` 靜態檔，而且 nginx 還要幫我們把 `/api` 反向代理到後端容器。這個反代很重要——對瀏覽器來說，前端和 API 變成同一個來源，CORS 問題自然消失，也是為什麼待會 Tunnel 只需要開一個入口。

接著把四個服務寫進 docker-compose.yml：frontend、backend、postgres 用 pgvector 的映像、再加一個 cloudflared。這裡有三個關鍵設定你一定要顧到。第一，postgres 的資料目錄一定要掛 named volume，不然容器一重建，資料就全部消失。第二，healthcheck 加 depends_on：backend 要等 postgres 健康檢查通過才啟動，不然開機瞬間連不上資料庫就掛了；cloudflared 則等 frontend 就緒。第三，密碼和金鑰——資料庫密碼、JWT secret、OpenAI API key——統統放 `.env` 檔，記得加進 `.gitignore`，compose 檔裡用變數引用，不寫死任何一個密碼。還有一個小知識：容器之間用服務名稱互連，像 `jdbc:postgresql://postgres:5432/crm`，Docker 內建的 DNS 會解析，你完全不需要知道容器的 IP。

這些你都不用自己手刻。我們現在來用第一個提示詞，直接請 AI 把整個專案打包成一鍵啟動的部署組合——前端容器、後端多階段建置、支援向量檢索的 PostgreSQL、持久化、環境變數檔，一次講清楚。你會看到 AI 把 Dockerfile 和 compose 檔都生出來，然後你下一個指令，四個服務就全部起來了。

再來是重頭戲：Cloudflare Tunnel。它的原理其實一句話就講完了——把連線方向反過來。你內網機器上的 cloudflared 程式，主動往外連到 Cloudflare 的邊緣節點，建立一條加密的持久連線。外部使用者打你的公開網址，流量先進到 Cloudflare，Cloudflare 再沿著這條已經打好的洞，把請求反向送回你的機器。注意喔，這個連線是由內往外建立的，跟你打開瀏覽器上網是同一個方向，所以你不需要公網 IP，CGNAT 後面照樣能用；防火牆一個 port 都不用開，沒有入站規則就沒有入站攻擊面；而且外界只看得到 Cloudflare，看不到你家的 IP。

最快的入門方式叫 Quick Tunnel，不用註冊帳號、不用網域、不用任何設定。我們現在來跑跑看：一行 `docker run`，跑 cloudflared 的映像，指定 `tunnel --url http://frontend:80`，記得掛上跟 compose 同一個網路。你會看到畫面上跳出一個隨機網址，長得像 `https://某某某.trycloudflare.com`。接下來是我最喜歡的時刻——把這個網址傳到你手機上，關掉 WiFi，改用 4G 打開。你會看到你的 AI CRM 登入頁就這樣出現在手機上，登入、查客戶、跟 AI 對話，全部都通。這個系統跑在你家的機器上，而全世界都連得到它。確定要長期用的話，就把 cloudflared 直接寫進 compose 檔，跟著整套系統一起啟動。

不過 Quick Tunnel 的限制你要心裡有數：網址是隨機的，每次重啟都會換；官方不保證可用性，只適合 demo 跟測試；也不能綁自己的網域。所以如果你要把這個作品放進履歷、想長期經營，就走 Named Tunnel。前提是要有一個網域，掛在 Cloudflare 的 DNS 上，免費方案就夠，網域本身一年大概十塊美金。流程是：到 Zero Trust 後台建立 Tunnel 拿到 token，cloudflared 改用 token 啟動，再設定一個固定子網域，像 `crm.你的網域.com`，指向 frontend，DNS 紀錄自動建好，HTTPS 憑證 Cloudflare 自動簽。做完之後，你的 AI CRM 就有一個可以印在履歷上的正式網址，而伺服器還是你家那台機器。這部分是選做，第三個提示詞會一步一步帶你走。

最後，安全收尾。系統一公開，安全就不再是作業要求，而是真實防線。四件事上線前逐項確認：一，管理介面不該全世界看得到——用了 Named Tunnel 之後，可以用 Cloudflare Access 免費設一道登入牆，`/admin` 或 Swagger UI 只允許你的 Email 進入。二，CORS 白名單收斂，開發時的 `allowedOrigins("*")` 上線前必須改掉；如果前端已經是 nginx 同源反代，甚至可以整個關掉跨來源。三，secret 全面體檢：JWT secret 換成生產專用的隨機長字串、資料庫密碼不用預設值、確認 git 歷史沒有洩漏過任何金鑰。四，最小暴露原則：postgres 的 5432 不需要對外，compose 裡不要寫 ports 對映，Tunnel 只指向 frontend 一個入口。

驗收的標準也給你：用驗證提示詞陪你走一遍——一個指令啟動全部服務、手機 4G 連入完成登入、瀏覽客戶、跟 AI 對話、知識庫問答正常，最後把容器全部停掉再啟動，資料都還在。全部通過，才叫真正上線成功。中間卡住也不用慌，排錯提示詞列了最常見的三種狀況：後端連不上資料庫、前端打得開但 API 都失敗、通道起來了但外部網址打不開，把錯誤訊息貼給 AI，讓它判斷是啟動順序、容器網路、反代還是通道設定的問題。

總結一句話：這個單元把「只有你的電腦看得到的系統」變成「全世界都連得到的服務」，而且靠的是連線方向的翻轉，不是在防火牆上開洞。走到這裡，你手上已經不只是一個課程作業，而是一個真正在網路上跑的、屬於你自己的企業級智慧應用。再一次謝謝大家讓這門課達標解鎖，才有這個加碼單元。希望你把網址傳給朋友的那一刻，跟我第一次做到時一樣興奮。我們課程裡見，掰掰。
