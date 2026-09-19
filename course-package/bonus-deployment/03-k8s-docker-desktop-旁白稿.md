# 03-k8s-docker-desktop｜Kubernetes 初體驗旁白稿

本稿依據 `03-k8s-docker-desktop.md` 的教學素材與口語稿整理，供人工錄音使用。共 11 段，錄音時可依段落分開保存，方便後續與投影片時間軸對齊。

建議錄音格式：WAV 或 MP3。每段開頭保持自然，結尾保留約 0.5 秒空白。

## 邊念邊操作提示卡

1. 錄製前先在另一台機器（或前一天）試跑一次 Enable Kubernetes——第一次啟用要下載元件，錄影當下等太久可以剪，但 `kubectl get nodes` 的 Ready 畫面必須是真的。
2. 念到「把你的 compose 檔貼給 AI」時停頓，實際貼提示詞①，鏡頭停在 AI 產出 manifest 的中文對照註解上。
3. 建 Secret 時用 `--from-literal` 指令示範，值用假密碼；畫面絕不出現真實金鑰，也不要產生含密碼的 YAML 檔入鏡。
4. 自癒實驗是本單元的高光：先開一個終端機跑 `kubectl get pods -w`，另一個終端機 delete pod，讓觀眾即時看到重生過程——這段不要剪接，完整保留時間流。
5. 滾動更新要兩個畫面並排：`rollout status` 的逐副本替換輸出，以及瀏覽器持續操作不中斷的畫面；`rollout undo` 後回舊版的畫面也要入鏡。
6. 任何 Pod 起不來就現場示範 `describe` 的 Events 段與 `logs --previous`，保留排錯過程，明講是 partial 不要補拍成一次成功。

## 01｜開場與定位：不是維運課，是初體驗

建議檔名：`01-intro-positioning.wav`

歡迎來到延伸部署章的最後一個單元，也是這門課真正的最後一哩：Kubernetes。先說好這一集的定位——我不是要把你變成 k8s 維運工程師，那是另一門完整的課。我要做的是帶你用 Docker Desktop 內建的 k8s，零成本、不裝任何新東西，把你的 AI CRM 部署進一座真的叢集，讓 Deployment、Service、ConfigMap、Secret 這些企業環境天天在講的詞，變成你親手操作過的東西。

## 02｜為什麼需要 k8s：compose 的三個極限

建議檔名：`02-why-k8s.wav`

先誠實回答一個問題：上一集的部署不是好好的嗎，為什麼還要 k8s？對一台伺服器來說，真的夠了。但規模一上來，三個問題就冒出來。第一，容器掛了誰重啟？restart 策略只救得了行程死掉，機器掛了、或服務假死沒回應，compose 無能為力。第二，換版要斷線，compose up 是停舊起新，中間服務就是空窗。第三，十台機器就是十份 compose 各自為政，誰來決定哪個容器跑哪台？k8s 就是回答這三題的：自癒、滾動更新、把一堆機器抽象成一個資源池。

## 03｜核心思想：宣告式收斂

建議檔名：`03-declarative.wav`

k8s 的核心思想我用一句話講：宣告式收斂。你不對 k8s 下「啟動容器」這種命令，你提交的是一份期望狀態的宣告——我要兩個後端副本、跑 1.0.0 版、吃這些設定——然後 k8s 一直比對現實跟宣告，有落差就自動修。容器掛了？現實少一個副本，補。你改了宣告的版本號？現實不符，逐個換。把這句話記住，等下看到的每個行為都說得通。

## 04｜啟用內建叢集

建議檔名：`04-enable-k8s.wav`

我們現在來開叢集。Docker Desktop 的設定裡勾一個 Enable Kubernetes，等它幾分鐘，然後 kubectl get nodes——看到一個 Ready 的節點，你就有一座叢集了。是的，只有一個節點，大腦跟工人是同一台，生產環境不會這樣。但物件模型跟指令跟生產環境完全相同，這就是拿它學習的價值。

## 05｜學習訣竅：用 compose 翻譯，不從零學

建議檔名：`05-translation-table.wav`

再來是本單元的學習訣竅：翻譯，不要從零學。你已經很懂 compose 了，k8s 的每個核心物件都能對照過去。compose 的一個 service，對應 k8s 的 Deployment，差別是多了 replicas 副本數；服務名稱互連對應 Service，一樣是內部 DNS，但背後多了負載均衡；environment 對應 ConfigMap；.env 裡的密碼對應 Secret；named volume 對應 PVC。教材裡有完整的翻譯表，第一個提示詞更直接——把你的 compose 檔貼給 AI，請它翻成 manifest，而且每個物件旁邊都註解對應 compose 的哪一行。

## 06｜manifest 關鍵細節：本機映像與副本數

建議檔名：`06-manifest-details.wav`

寫 manifest 的時候，單機練習有一個關鍵細節：imagePullPolicy 要設 IfNotPresent。Docker Desktop 的 k8s 跟 Docker 共用同一套本機映像庫，設了這個它就直接用你單元一 build 的映像；不設的話它會跑去 registry 拉一個根本不存在的映像，然後你就會看到經典的 ImagePullBackOff。後端我們開兩個副本——它是無狀態的，狀態都在資料庫，放心開，這也是等下表演的舞台。

## 07｜ConfigMap 與 Secret

建議檔名：`07-configmap-secret.wav`

設定的部分，上一集的 .env 在這裡拆成兩個物件：非敏感的進 ConfigMap，密碼金鑰進 Secret。Secret 有一件事一定要知道：它的內容是 base64 編碼，編碼不是加密，拿得到叢集權限的人都解得開。所以密碼不進 git 的紀律一樣要守——Secret 用 kubectl 指令直接從值建立，不要產生含密碼的 YAML 檔。

## 08｜資料庫的誠實取捨

建議檔名：`08-database-tradeoff.wav`

資料庫怎麼辦？這裡我要給你一個誠實的取捨。課堂上我們讓 postgres 也進叢集，單副本加 PVC，因為這樣你能練到持久化宣告，教學才完整。但你要知道，多數企業的實務是另一條路：無狀態服務進 k8s，資料庫用託管服務。把生產資料庫跑進 k8s 是需要專業維運的決策，工作上遇到，請優先評估託管。

## 09｜高光時刻：自癒與滾動更新

建議檔名：`09-selfheal-rollout.wav`

全部 apply 之後，port-forward 把前端接到本機瀏覽器，登入、查客戶、跟 AI 對話——業務驗收的標準三集都一樣。然後，本單元的高光時刻到了。第一個實驗，自癒：kubectl get pods 記下一個後端 pod 的名字，親手把它刪掉，然後用 -w 看著——幾秒之內，一個新的 pod 自己長出來。你沒有下任何重啟指令，是 k8s 發現現實跟宣告差了一個副本，自己補齊的。第一次看到這個畫面通常會起雞皮疙瘩。第二個實驗，滾動更新：set image 換到新版本，rollout status 看它逐個替換——新的起來、健康檢查過了，才殺舊的，服務全程有人接客。發現新版有問題？rollout undo，一行退版。對照上一集：compose 換版有斷線、退版要改檔案重跑；k8s 這裡是不斷線、一行退。

## 10｜學習環境與生產的差距

建議檔名：`10-gap-to-production.wav`

最後帶走一張清單：我們今天的環境跟生產叢集差在哪。單節點對多節點、port-forward 對 Ingress 加 LoadBalancer、本機映像對私有 registry、手動 apply 對 GitOps 自動同步、資料庫進叢集對託管資料庫。左邊學會的物件模型、manifest、kubectl，到右邊全部沿用——差的是規模跟周邊配套，不是核心概念。所以下次面試或同事聊到 k8s，你不只聽得懂，還能說「我部署過，自癒跟滾動更新我都親手跑過」。

## 11｜整章總結

建議檔名：`11-closing.wav`

總結整個延伸部署章：單元一，把系統做成環境自帶、版本可考的映像；單元二，映像、設定、資料三分離，搬到伺服器上線並學會換版退版；單元三，同一組映像交給 k8s，用宣告式收斂拿到自癒跟滾動更新。從 localhost 到叢集，你的 AI CRM 走完了一條企業級應用真實會走的路。恭喜你，也謝謝你一路跟到這裡。我們有緣再見，掰掰。
