# 01-cloudflare-tunnel｜Cloudflare Tunnel 上線實戰旁白稿

本稿依據 `01-cloudflare-tunnel.md` 的教學素材與口語稿整理，供人工錄音使用。共 11 段，錄音時可依段落分開保存，方便後續與投影片時間軸對齊。

建議錄音格式：WAV 或 MP3。每段開頭保持自然，結尾保留約 0.5 秒空白。

## 邊念邊操作提示卡

1. 錄製前先把 Docker、compose、`.env`（只展示變數名）和手機準備好；旁白念到「部署前檢查」時停在 checklist。
2. 依 backend、frontend、postgres、cloudflared 的順序操作，展示 `docker compose config`、`docker compose ps` 和 health 結果，不只展示完成畫面。
3. 用本機 URL 先測登入、查客戶、AI，再從關閉 Wi-Fi 的手機打開 Quick Tunnel；念到公開網址時不要在不必要的地方重複曝光 token 或秘密。
4. 示範停掉再啟動容器，查同一筆資料證明 volume；最後展示 CORS、secret、管理端點、log 脫敏和 Tunnel 撤銷檢查。
5. 若外部測試失敗，保留 cloudflared、nginx、backend 的第一個錯誤，明確說明是 partial，不用剪輯或 mock 補成成功。

## 01｜加碼單元開場

建議檔名：`01-bonus-intro.wav`

嗨，歡迎來到這個特別的單元。先說一件開心的事：這一章是課程達到一百人解鎖的加碼單元，能夠錄這一集，完全是因為大家的支持，真的謝謝你們。這個加碼教學要做的事，是把跑在你本機的服務，安全地公開到網路上——也就是帶你做一件最有成就感的事：讓你的 AI CRM 真正上線。

## 02｜為什麼要上線：localhost 太可惜了

建議檔名：`02-why-go-live.wav`

先講為什麼。我們一路從章節一走到章節八，環境、REST API、資料庫、JWT、React 工作台、Spring AI、RAG，最後在 Demo Day 把整套系統跑給大家看。可是 Demo 完之後呢？這套系統還是只活在 localhost，只有你自己的電腦看得到。你做了一個會給銷售建議的智慧系統，結果想給朋友看一眼，還得叫他來你家。這太可惜了。

## 03｜傳統做法的三個麻煩

建議檔名：`03-port-forwarding-problems.wav`

那把它放上網就好了嘛？問題就出在這裡。傳統做法是 port forwarding：去路由器上開一個入站的孔，把公網 IP 的某個 port 轉到內網機器。但你馬上會遇到三個麻煩。第一，你需要固定的公網 IP，不然就要弄 DDNS。第二，防火牆上永遠開著一個洞，那是永久的攻擊面。第三，也是最多人卡住的——很多住宅網路根本拿不到真實公網 IP，你在電信商的 CGNAT 後面，怎麼開 port 都沒有用。宿舍網路、4G 分享也是一樣。

## 04｜三條路線，我們選自架加 Tunnel

建議檔名：`04-three-routes.wav`

上網的路線其實有三條。雲平台 PaaS 像 Zeabur、Render 最省事，但長期跑有月費，資料庫也在別人機器上。Cloudflare 自家的 Containers 要付費方案，而且容器是用完即睡的無狀態運算，不適合跑 PostgreSQL 這種有狀態資料庫——它家的 D1 是 SQLite，沒有 pgvector，我們的向量檢索就沒了。所以這個單元選第三條：系統整套跑在你自己的機器上——家用電腦、公司閒置主機、NAS 都行——用 Cloudflare Tunnel 把外部流量安全送進來。免公網 IP、防火牆一個 port 都不用開、零月費，最能延續整堂課「所有元件都自己掌控」的精神。

## 05｜第一步打包：多階段建置

建議檔名：`05-multi-stage-build.wav`

上線分兩步：先打包，再打洞。打包用 Docker Compose，第一件事是把前後端各自做成映像檔，關鍵技巧叫多階段建置。以後端來說，第一階段用 Maven 映像跑 mvn package 做出 fat jar；第二階段只用精簡的 JRE 映像承載 jar。建置工具留在建置階段，最終映像只帶執行需要的東西，體積從八百多 MB 降到三百 MB 以下。前端同一個套路：第一階段用 Node 跑建置，第二階段用 nginx 服務靜態檔，而且 nginx 還要把 /api 反向代理到後端容器。這個反代很重要——對瀏覽器來說前端和 API 是同一個來源，CORS 問題自然消失，也是為什麼待會 Tunnel 只需要開一個入口。

## 06｜Compose 四服務與三個關鍵設定

建議檔名：`06-compose-key-settings.wav`

接著把四個服務寫進 docker-compose.yml：frontend、backend、postgres 用 pgvector 的映像、再加一個 cloudflared。三個關鍵設定一定要顧到。第一，postgres 的資料目錄要掛 named volume，不然容器一重建，資料就全部消失。第二，healthcheck 加 depends_on：backend 要等 postgres 健康檢查通過才啟動，cloudflared 等 frontend 就緒。第三，資料庫密碼、JWT secret、API key 統統放 .env 檔，記得加進 .gitignore，不寫死任何密碼。還有個小知識：容器之間用服務名稱互連，Docker 內建的 DNS 會解析，你完全不需要知道容器 IP。這些都不用手刻，教材裡的打包提示詞貼給 AI，Dockerfile 跟 compose 檔就都生出來了——提示詞不用抄，教學網站查得到。

## 07｜Tunnel 原理：把連線方向反過來

建議檔名：`07-tunnel-principle.wav`

再來是重頭戲：Cloudflare Tunnel。它的原理一句話就講完了——把連線方向反過來。你內網機器上的 cloudflared 程式，主動往外連到 Cloudflare 的邊緣節點，建立一條加密的持久連線。外部使用者打你的公開網址，流量先進到 Cloudflare，再沿著這條已經打好的洞，反向送回你的機器。注意喔，這個連線是由內往外建立的，跟你打開瀏覽器上網是同一個方向。所以你不需要公網 IP，CGNAT 後面照樣能用；防火牆一個 port 都不用開，沒有入站規則就沒有入站攻擊面；而且外界只看得到 Cloudflare，看不到你家的 IP。

## 08｜Quick Tunnel：五分鐘上線

建議檔名：`08-quick-tunnel.wav`

最快的入門方式叫 Quick Tunnel，不用註冊帳號、不用網域、不用任何設定。一行 docker run 跑 cloudflared 的映像，把 frontend 打洞出去，記得掛上跟 compose 同一個網路。你會看到畫面上跳出一個隨機網址，結尾是 trycloudflare.com。接下來是我最喜歡的時刻——把網址傳到手機上，關掉 WiFi，改用 4G 打開。你的 AI CRM 登入頁就這樣出現在手機上，登入、查客戶、跟 AI 對話，全部都通。這個系統跑在你家的機器上，而全世界都連得到它。要長期用的話，把 cloudflared 直接寫進 compose 檔，跟整套系統一起啟動。

## 09｜限制與 Named Tunnel（選做）

建議檔名：`09-named-tunnel.wav`

不過 Quick Tunnel 的限制要心裡有數：網址是隨機的，每次重啟都會換；官方不保證可用性，只適合 demo 跟測試；也不能綁自己的網域。要把作品放進履歷、長期經營，就走 Named Tunnel。前提是有一個網域掛在 Cloudflare 的 DNS 上，免費方案就夠，網域一年大概十塊美金。流程是：到 Zero Trust 後台建立 Tunnel 拿到 token，cloudflared 改用 token 啟動，設定一個固定子網域指向 frontend，DNS 紀錄自動建好，HTTPS 憑證 Cloudflare 自動簽。做完之後，你的 AI CRM 就有一個可以印在履歷上的正式網址，而伺服器還是你家那台機器。這部分是選做，教材裡的提示詞會一步一步帶你走。

## 10｜上線安全收尾

建議檔名：`10-security-hardening.wav`

最後，安全收尾。系統一公開，安全就不再是作業要求，而是真實防線，四件事逐項確認。一，管理介面不該全世界看得到——用了 Named Tunnel 之後，可以用 Cloudflare Access 免費設一道登入牆，admin 路徑或 Swagger UI 只允許你的 Email 進入。二，CORS 白名單收斂，開發時允許全部來源的設定上線前必須改掉；前端已經同源反代的話，甚至可以整個關掉跨來源。三，secret 全面體檢：JWT secret 換成生產專用的隨機長字串、資料庫密碼不用預設值、確認 git 歷史沒有洩漏過金鑰。四，最小暴露原則：postgres 的 5432 不需要對外，compose 裡不要寫 ports 對映，Tunnel 只指向 frontend 一個入口。

## 11｜驗收標準與總結

建議檔名：`11-closing.wav`

驗收的標準也給你：一個指令啟動全部服務、手機 4G 連入完成登入、瀏覽客戶、跟 AI 對話、知識庫問答正常，最後把容器全部停掉再啟動，資料都還在——全部通過，才叫真正上線成功。中間卡住不用慌，把錯誤訊息原封不動貼給 AI，用教材裡的排錯提示詞判斷是啟動順序、容器網路、反代還是通道設定的問題。總結一句話：這個單元把「只有你的電腦看得到的系統」變成「全世界都連得到的服務」，靠的是連線方向的翻轉，不是在防火牆上開洞。再一次謝謝大家讓這門課達標解鎖。希望你把網址傳給朋友的那一刻，跟我第一次做到時一樣興奮。我們課程裡見，掰掰。

## 交付檔案命名

```text
01-bonus-intro.wav
02-why-go-live.wav
03-port-forwarding-problems.wav
04-three-routes.wav
05-multi-stage-build.wav
06-compose-key-settings.wav
07-tunnel-principle.wav
08-quick-tunnel.wav
09-named-tunnel.wav
10-security-hardening.wav
11-closing.wav
```
