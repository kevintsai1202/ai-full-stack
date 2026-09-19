# 延伸部署 單元 3｜Kubernetes 初體驗：用 Docker Desktop 內建叢集部署 AI CRM

## 單元定位

三部曲的最後一步。前兩個單元你已經能把系統打包、搬運、上線、換版——對一台伺服器來說這樣就夠了。但企業環境的服務通常不只跑在一台機器上，也不能接受「容器掛了要人工重啟」，這就是 Kubernetes（k8s）存在的理由。本單元用 Docker Desktop 內建的單節點 k8s，把你的 AI CRM 部署進叢集：零雲端費用、不用裝任何新軟體，就在你已經很熟的環境裡，把 compose 的每一行翻譯成 k8s 的物件，並體驗 compose 給不了的兩件事——自癒與滾動更新。

- **銜接**：使用單元 1 的映像。單元 2 的「三要素分離」心法在這裡原封不動適用，只是換了一套詞彙：設定變成 ConfigMap 與 Secret，資料變成 PersistentVolumeClaim。
- **定位要誠實**：Docker Desktop 的 k8s 是**單節點學習環境**，目的是讓你把 Deployment、Service、ConfigMap、Secret 這些企業環境天天在用的概念在本機摸過一輪、看得懂同事的 manifest、接得住雲端託管叢集（EKS/GKE/AKS）的工作。它**不是**生產叢集，本單元也不教叢集維運（Ingress controller、HPA、多節點排程都不在範圍內）。
- **驗收標準**：AI CRM 在本機 k8s 跑起來並完成登入與 AI 對話；親手刪掉一個 Pod 看它自動重生；完成一次滾動更新。
- **建議時長**：延伸單元（約 30～40 分鐘）。

### 學習目標

- 說出 k8s 解決了 compose 解決不了的哪些問題（自癒、滾動更新、宣告式收斂、多機調度）
- 啟用 Docker Desktop 內建 Kubernetes 並用 kubectl 確認叢集狀態
- 用 compose ↔ k8s 對照表理解 Deployment、Service、ConfigMap、Secret 四個核心物件
- 撰寫 manifest 把 AI CRM 部署進叢集，用 port-forward 從瀏覽器存取
- 實際演練自癒（刪 Pod 自動重生）與滾動更新（換版不斷線）
- 知道這個學習環境與生產叢集的差距在哪裡

### 核心原則

k8s 的核心是**宣告式收斂**：你不下「啟動容器」這種命令，而是提交一份「期望狀態」的宣告（我要 2 個後端副本、跑 1.0.0 版、吃這些設定），k8s 持續比對現實與宣告，有落差就自動修正。容器掛了？現實少了一個副本，補起來。你改了宣告的映像版本？現實跟宣告不符，逐個換掉。理解了「宣告期望、系統收斂」這一件事，k8s 的各種行為就都說得通了。

## 教學素材

### 一、compose 夠用了，為什麼還要 k8s？

先誠實回答一個問題：單元 2 的部署有什麼不滿意的地方嗎？對一台伺服器、一套系統來說，幾乎沒有。但規模一上來，三個問題浮現：

1. **容器掛了誰重啟？** `restart: unless-stopped` 只能處理容器行程死掉，如果整台機器掛了、或容器活著但服務已經沒回應，compose 無能為力。
2. **換版要斷線。** `compose up -d` 換版時舊容器停、新容器起，中間就是服務中斷；想做「新版起來確認健康後才把流量切過去」，compose 做不到。
3. **多台機器怎麼辦？** compose 管一台機器，十台機器就是十份 compose 各自為政。誰決定哪個容器跑在哪台機器？

k8s 就是回答這三個問題的：偵測到副本數不足就自動補（自癒）、逐個替換副本並確認健康才繼續（滾動更新）、把 N 台機器抽象成一個資源池統一調度（叢集）。代價是一套新的詞彙與物件模型——這正是本單元要帶你跨過的門檻。

### 二、啟用 Docker Desktop 內建 k8s

Docker Desktop → Settings → Kubernetes → 勾選 **Enable Kubernetes** → Apply & Restart。第一次啟用會下載叢集元件，需要幾分鐘。完成後驗證：

```powershell
kubectl get nodes
# NAME             STATUS   ROLES           AGE   VERSION
# docker-desktop   Ready    control-plane   1m    v1.3x.x
```

`kubectl` 是操作 k8s 的 CLI，Docker Desktop 已經幫你裝好並指向內建叢集。看到一個 `Ready` 的節點，你就有一座（單節點的）叢集了。這個節點同時是控制平面（大腦）與工作節點（跑容器的地方）——生產環境這兩者會分開並各有多台，但物件模型與指令完全相同，這正是用它學習的價值。

### 三、核心翻譯表：compose ↔ k8s

你已經很懂 compose，所以學 k8s 最快的路徑是翻譯，不是從零學：

| compose 裡的概念 | k8s 對應物件 | 差異重點 |
|---|---|---|
| `services.backend`（跑幾個容器） | **Deployment** | 多了 `replicas` 副本數，k8s 負責讓現實維持這個數字 |
| 服務名稱互連（`backend:8080`） | **Service** | 一樣是穩定的內部 DNS 名稱，但背後多了「把流量分給多個副本」的負載均衡 |
| `environment:` 非敏感設定 | **ConfigMap** | 設定獨立成物件，可以不重建映像只換設定 |
| `.env` 裡的密碼金鑰 | **Secret** | 與 ConfigMap 同構，但列印與權限行為更收斂（內容是 base64 編碼，不是加密——別把 Secret manifest 進 git） |
| `volumes:` named volume | **PersistentVolumeClaim（PVC）** | 宣告「我要一塊儲存空間」，由叢集撮合實際存放位置 |
| `ports:` 對外開孔 | **Service（NodePort）／port-forward** | 本單元用 port-forward 即可；生產環境是 Ingress／LoadBalancer 的職責（不在本課範圍） |

### 四、動手寫 manifest：後端 Deployment + Service

k8s 的宣告寫成 YAML manifest。後端的最小可用版本：

```yaml
# k8s/backend.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: backend
spec:
  replicas: 2                     # 期望 2 個副本，自癒與滾動更新的基礎
  selector:
    matchLabels: { app: backend }
  template:
    metadata:
      labels: { app: backend }
    spec:
      containers:
        - name: backend
          image: ai-crm-backend:1.0.0
          imagePullPolicy: IfNotPresent   # 用本機映像，不去 registry 拉（單機練習的關鍵）
          ports: [{ containerPort: 8080 }]
          envFrom:
            - configMapRef: { name: crm-config }   # 非敏感設定
            - secretRef: { name: crm-secret }      # 密碼金鑰
---
apiVersion: v1
kind: Service
metadata:
  name: backend                   # 叢集內部 DNS 名稱，前端 nginx 反代就指它
spec:
  selector: { app: backend }
  ports: [{ port: 8080, targetPort: 8080 }]
```

兩個單機練習的關鍵細節：

1. **`imagePullPolicy: IfNotPresent`**：Docker Desktop 的 k8s 與 Docker 共用同一套本機映像庫，設了這個，k8s 會直接用你單元 1 build 的映像，不會跑去 registry 拉（拉不到就是經典的 `ImagePullBackOff`）。
2. **`replicas: 2`**：後端是無狀態的（狀態都在資料庫），可以放心開兩個副本——這也是等下自癒與滾動更新示範的舞台。前端同理；**資料庫不適用**，見第六節。

### 五、ConfigMap 與 Secret：設定的 k8s 形態

單元 2 的 `.env` 在 k8s 裡拆成兩個物件——非敏感的進 ConfigMap，敏感的進 Secret：

```powershell
# 非敏感設定：直接寫 manifest 或用指令建立
kubectl create configmap crm-config --from-literal=SPRING_PROFILES_ACTIVE=prod

# 敏感設定：用指令從值建立，不寫成檔案（避免 base64 的 Secret manifest 被誤進 git）
kubectl create secret generic crm-secret `
  --from-literal=POSTGRES_PASSWORD=<生產密碼> `
  --from-literal=JWT_SECRET=<隨機長字串> `
  --from-literal=OPENAI_API_KEY=<你的key>
```

要記住的一件事：Secret 的內容是 **base64 編碼，不是加密**——任何拿得到叢集讀取權限的人都解得開。它的價值在於與映像、與 ConfigMap 的職責分離，以及叢集端的權限控管；「密碼不進 git」的紀律跟單元 2 完全一樣。

### 六、資料庫怎麼辦：誠實的取捨

資料庫是有狀態服務，k8s 對它有專門的物件（StatefulSet + PVC），但把生產資料庫跑進 k8s 是一個需要專業維運的決策，不是本課範圍。單機練習給你兩條誠實的路：

- **路線 A（本單元採用）**：postgres 也進叢集，用單副本 Deployment + PVC。在單節點學習環境完全夠用，也讓你練到 PVC 的宣告；但要知道生產環境不會這樣做。
- **路線 B**：postgres 留在 docker compose（或雲端託管資料庫），k8s 裡只跑無狀態的前後端。這其實更接近多數企業的實務：**無狀態服務進 k8s，資料庫用託管服務**。

課堂選 A 是為了教學完整性；工作上遇到請優先評估 B。

### 七、體驗 k8s 的招牌：自癒與滾動更新

全部 `kubectl apply -f k8s/` 之後，先開 port-forward 讓瀏覽器連進叢集：

```powershell
kubectl port-forward service/frontend 8080:80
# 瀏覽器開 http://localhost:8080，完成登入與 AI 對話
```

然後是本單元的高光時刻。**自癒**：

```powershell
kubectl get pods                  # 記下某個 backend pod 的名字
kubectl delete pod backend-xxxxx  # 親手殺掉它
kubectl get pods -w               # 看著新的 pod 在幾秒內自動長出來
```

你沒有下任何「重啟」指令——k8s 發現現實（1 個副本）與宣告（2 個副本）不符，自己把它補齊了。**滾動更新**：

```powershell
kubectl set image deployment/backend backend=ai-crm-backend:1.1.0
kubectl rollout status deployment/backend   # 逐個替換：新的起來健康了才殺舊的
kubectl rollout undo deployment/backend     # 一行退版
```

對照單元 2：compose 換版是「停舊起新」有斷線；k8s 換版是逐副本替換，服務全程有人接客。退版也從「改 .env 再 up」變成一行 `rollout undo`。

### 八、學習環境與生產的差距（帶走這張清單）

| 本單元（Docker Desktop） | 生產環境 |
|---|---|
| 單節點，控制平面＋工作節點同一台 | 多節點，控制平面高可用 |
| port-forward 進叢集 | Ingress + LoadBalancer + TLS |
| 本機映像 `IfNotPresent` | 私有 registry + 映像簽章掃描 |
| `kubectl apply` 手動部署 | GitOps（Argo CD / Flux）自動同步 |
| 資料庫進叢集練習 | 託管資料庫或專業 StatefulSet 維運 |

左邊學會的物件模型、manifest 語法、kubectl 操作，到右邊全部沿用——差的是規模與周邊配套，不是核心概念。

### 實作任務

- **u12-t1**：啟用 Docker Desktop Kubernetes，`kubectl get nodes` 顯示節點 Ready
- **u12-t2**：建立 ConfigMap 與 Secret，撰寫 postgres（含 PVC）、backend、frontend 的 manifest 並 apply 成功
- **u12-t3**：port-forward 之後從瀏覽器完成登入、查客戶與 AI 對話
- **u12-t4**：刪除一個 backend Pod，用 `kubectl get pods -w` 記錄它自動重生的過程
- **u12-t5**：對 backend 執行滾動更新到新版本、確認不斷線，再用 rollout undo 退版

## 示範與提示詞

### ① compose 翻譯成 k8s manifest［build］

> 用你已經懂的 compose 當 Rosetta Stone

```text
這是我目前在用的 docker compose 檔（我會貼上）。請幫我把它翻譯成 Kubernetes manifest，部署到 Docker Desktop 內建的單節點叢集：後端和前端各做一個 Deployment（後端開兩個副本）加 Service，資料庫做單副本 Deployment 加 PersistentVolumeClaim。因為映像在我本機，請設定成優先使用本機映像、不要去 registry 拉。請在每個 k8s 物件旁邊用中文註解標明它對應 compose 檔的哪一行，讓我能對照著理解。
```

### ② 設定與密碼抽離［build］

> ConfigMap 管設定、Secret 管密碼

```text
請幫我把部署設定抽離成 Kubernetes 的 ConfigMap 與 Secret：非敏感的設定（如 Spring profile、服務網址）放 ConfigMap，資料庫密碼、JWT secret、AI 金鑰放 Secret，並改寫 Deployment 用 envFrom 一次注入。Secret 請教我用 kubectl 指令從值直接建立，不要產生含密碼的 YAML 檔。最後請解釋 Secret 的 base64 是編碼還是加密、這對「什麼能進 git」有什麼影響。
```

### ③ 自癒與滾動更新示範［build］

> k8s 給你、compose 給不了的兩件事

```text
我的服務已經在本機 Kubernetes 上跑起來了。請帶我做兩個實驗：第一，親手刪掉一個後端 Pod，教我用什麼指令即時觀察它自動重生，並解釋 k8s 為什麼會這樣做；第二，把後端滾動更新到新版本映像，教我怎麼確認更新過程中服務沒有斷線，然後再用一行指令退回原版本。每個實驗請先告訴我預期會看到什麼，再開始操作。
```

### ✅ 驗證 — 全套 k8s 驗收［verify］

> 從叢集狀態到業務功能一路檢查

```text
請陪我完成 Kubernetes 部署的完整驗收：kubectl get nodes 節點 Ready、get pods 全部 Running 且副本數符合宣告、get svc 確認 Service 都建立、port-forward 之後從瀏覽器完成登入、查客戶、AI 對話與知識庫問答。接著驗證持久化：刪掉資料庫 Pod 等它重生，確認之前的資料還在。最後把我做過的物件用 kubectl get all 列出來，幫我逐一解說每一項是什麼、為什麼存在。
```

### 🔧 排錯 — Pod 起不來［fix］

> 常見：ImagePullBackOff、CrashLoopBackOff、Pending

```text
我的 Pod 狀態不正常（我會貼上 kubectl get pods 和 kubectl describe pod 的輸出）。常見狀況有：ImagePullBackOff（可能是 imagePullPolicy 設定或映像名稱 tag 打錯，我的映像在本機）、CrashLoopBackOff（容器一直重啟，可能要看 kubectl logs 找應用錯誤，常見是連不上資料庫或 Secret 沒注入）、Pending（可能是 PVC 綁不到儲存）。請教我用 describe 和 logs 的哪些段落判斷根因，並直接幫我修正 manifest。
```

## 逐步操作與驗收

### 從零到叢集上的 AI CRM

1. 啟用 Docker Desktop Kubernetes，`kubectl get nodes` 確認 Ready；如果 kubectl 指到別的叢集，用 `kubectl config use-context docker-desktop` 切回來。
2. 用指令建立 ConfigMap 與 Secret（密碼不落檔），`kubectl get secret crm-secret` 確認存在。
3. 撰寫 postgres manifest（單副本 Deployment + PVC + Service）先 apply，`kubectl get pods -w` 等它 Running，`kubectl logs` 確認資料庫初始化完成。
4. apply backend（2 副本）與 frontend 的 manifest，等全部 Running；backend 起不來先 `kubectl logs` 看是不是資料庫連線或 Secret 注入問題。
5. `kubectl port-forward service/frontend 8080:80`，瀏覽器完成登入、查客戶、AI 對話——業務驗收跟前兩個單元同一套標準。
6. 自癒實驗：刪一個 backend Pod，`kubectl get pods -w` 記錄重生過程（保留這段輸出當證據）。
7. 滾動更新實驗：`set image` 到 1.1.0、`rollout status` 全程觀察、瀏覽器確認新版生效且操作不中斷，最後 `rollout undo` 退版並確認。

### 預期結果與證據

- 叢集上全部 Pod Running 且副本數符合宣告；瀏覽器完成完整業務流程；自癒與滾動更新各有一段可回放的指令輸出。
- 交付：`kubectl get all` 輸出、port-forward 後的業務操作畫面、Pod 重生的 `-w` 輸出、`rollout status` 與 `rollout undo` 前後的版本證據。

### 失敗分流

- `ImagePullBackOff`：九成是 `imagePullPolicy` 沒設 `IfNotPresent` 或映像名/tag 打錯——k8s 跑去 registry 找你只存在本機的映像。`kubectl describe pod` 的 Events 段會寫得很清楚。
- `CrashLoopBackOff`：容器起了又死，`kubectl logs <pod> --previous` 看上一次死掉前的 log，最常見是資料庫還沒 ready 或 Secret 的 key 名稱與程式讀的環境變數對不上。
- `Pending`：`describe` 看 Events，單機環境多半是 PVC 綁定問題；Docker Desktop 內建 default StorageClass，manifest 裡不要指定不存在的 storageClassName。
- port-forward 連不上：確認 forward 的是 Service 名稱且終端機沒關；port-forward 行程一關連線就斷，這是它跟正式 Ingress 的差別。

## 口語稿

歡迎來到延伸部署章的最後一個單元，也是這門課真正的最後一哩：Kubernetes。先說好這一集的定位——我不是要把你變成 k8s 維運工程師，那是另一門完整的課。我要做的是帶你用 Docker Desktop 內建的 k8s，零成本、不裝任何新東西，把你的 AI CRM 部署進一座真的叢集，讓 Deployment、Service、ConfigMap、Secret 這些企業環境天天在講的詞，變成你親手操作過的東西。

先誠實回答一個問題：上一集的部署不是好好的嗎，為什麼還要 k8s？對一台伺服器來說，真的夠了。但規模一上來，三個問題就冒出來。第一，容器掛了誰重啟？restart 策略只救得了行程死掉，機器掛了、或服務假死沒回應，compose 無能為力。第二，換版要斷線，compose up 是停舊起新，中間服務就是空窗。第三，十台機器就是十份 compose 各自為政，誰來決定哪個容器跑哪台？k8s 就是回答這三題的：自癒、滾動更新、把一堆機器抽象成一個資源池。

它的核心思想我用一句話講：宣告式收斂。你不對 k8s 下「啟動容器」這種命令，你提交的是一份期望狀態的宣告——我要兩個後端副本、跑 1.0.0 版、吃這些設定——然後 k8s 一直比對現實跟宣告，有落差就自動修。容器掛了？現實少一個副本，補。你改了宣告的版本號？現實不符，逐個換。把這句話記住，等下看到的每個行為都說得通。

我們現在來開叢集。Docker Desktop 的設定裡勾一個 Enable Kubernetes，等它幾分鐘，然後 kubectl get nodes——看到一個 Ready 的節點，你就有一座叢集了。是的，只有一個節點，大腦跟工人是同一台，生產環境不會這樣。但物件模型跟指令跟生產環境完全相同，這就是拿它學習的價值。

再來是本單元的學習訣竅：翻譯，不要從零學。你已經很懂 compose 了，k8s 的每個核心物件都能對照過去。compose 的一個 service，對應 k8s 的 Deployment，差別是多了 replicas 副本數；服務名稱互連對應 Service，一樣是內部 DNS，但背後多了負載均衡；environment 對應 ConfigMap；.env 裡的密碼對應 Secret；named volume 對應 PVC。教材裡有完整的翻譯表，第一個提示詞更直接——把你的 compose 檔貼給 AI，請它翻成 manifest，而且每個物件旁邊都註解對應 compose 的哪一行。

寫 manifest 的時候，單機練習有一個關鍵細節：imagePullPolicy 要設 IfNotPresent。Docker Desktop 的 k8s 跟 Docker 共用同一套本機映像庫，設了這個它就直接用你單元一 build 的映像；不設的話它會跑去 registry 拉一個根本不存在的映像，然後你就會看到經典的 ImagePullBackOff。後端我們開兩個副本——它是無狀態的，狀態都在資料庫，放心開，這也是等下表演的舞台。

設定的部分，上一集的 .env 在這裡拆成兩個物件：非敏感的進 ConfigMap，密碼金鑰進 Secret。Secret 有一件事一定要知道：它的內容是 base64 編碼，編碼不是加密，拿得到叢集權限的人都解得開。所以密碼不進 git 的紀律一樣要守——Secret 用 kubectl 指令直接從值建立，不要產生含密碼的 YAML 檔。

資料庫怎麼辦？這裡我要給你一個誠實的取捨。課堂上我們讓 postgres 也進叢集，單副本加 PVC，因為這樣你能練到持久化宣告，教學才完整。但你要知道，多數企業的實務是另一條路：無狀態服務進 k8s，資料庫用託管服務。把生產資料庫跑進 k8s 是需要專業維運的決策，工作上遇到，請優先評估託管。

全部 apply 之後，port-forward 把前端接到本機瀏覽器，登入、查客戶、跟 AI 對話——業務驗收的標準三集都一樣。然後，本單元的高光時刻到了。第一個實驗，自癒：kubectl get pods 記下一個後端 pod 的名字，親手把它刪掉，然後用 -w 看著——幾秒之內，一個新的 pod 自己長出來。你沒有下任何重啟指令，是 k8s 發現現實跟宣告差了一個副本，自己補齊的。第一次看到這個畫面通常會起雞皮疙瘩。第二個實驗，滾動更新：set image 換到新版本，rollout status 看它逐個替換——新的起來、健康檢查過了，才殺舊的，服務全程有人接客。發現新版有問題？rollout undo，一行退版。對照上一集：compose 換版有斷線、退版要改檔案重跑；k8s 這裡是不斷線、一行退。

最後帶走一張清單：我們今天的環境跟生產叢集差在哪。單節點對多節點、port-forward 對 Ingress 加 LoadBalancer、本機映像對私有 registry、手動 apply 對 GitOps 自動同步、資料庫進叢集對託管資料庫。左邊學會的物件模型、manifest、kubectl，到右邊全部沿用——差的是規模跟周邊配套，不是核心概念。所以下次面試或同事聊到 k8s，你不只聽得懂，還能說「我部署過，自癒跟滾動更新我都親手跑過」。

總結整個延伸部署章：單元一，把系統做成環境自帶、版本可考的映像；單元二，映像、設定、資料三分離，搬到伺服器上線並學會換版退版；單元三，同一組映像交給 k8s，用宣告式收斂拿到自癒跟滾動更新。從 localhost 到叢集，你的 AI CRM 走完了一條企業級應用真實會走的路。恭喜你，也謝謝你一路跟到這裡。我們有緣再見，掰掰。
