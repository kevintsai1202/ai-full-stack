# 01-docker-database｜Docker 與資料庫建立旁白稿

本稿依據 `01-docker-database.md` 的教學素材與口語稿整理，供人工錄音使用。共 7 段，錄音時可依段落分開保存，方便後續與投影片時間軸對齊。

建議錄音格式：WAV 或 MP3。每段開頭保持自然，結尾保留約 0.5 秒空白。

## 01｜資料為什麼要落地

建議檔名：`01-why-persistence.wav`

歡迎來到章節三。在動手之前，我先問你一個問題：上一章我們做的客戶 API，資料是存在哪裡的？答案是存在一個 Java 的 List 裡面，也就是記憶體。開發初期這樣很方便，但它有一個致命的問題——程式一重開，資料就全部不見了。

你想像一下，一套 CRM 系統，業務辛辛苦苦建了三百筆客戶資料，伺服器重開機一次全部歸零，這個系統是完全不能用的。所以企業專案的資料一定要「落地」，要存進真正的資料庫。這一章，我們就要把客戶 API 從記憶體暫存，換成真正的 PostgreSQL。

## 02｜為什麼用 Docker 跑資料庫

建議檔名：`02-why-docker.wav`

第一步是把資料庫裝起來。這裡先講一個「為什麼」：為什麼不直接在電腦上安裝 PostgreSQL，而是用 Docker？

如果你帶過團隊或上過實體課，一定遇過這種情況——每個人本機的資料庫版本不一樣、初始化內容不一樣、安裝方式也不一樣，光是把環境弄到一致就耗掉一個下午。Docker 的價值，就是把這些差異壓到最低。資料庫變成一個容器，用一份設定檔描述清楚，任何人拿到這份檔案，一個指令就能長出一模一樣的資料庫，壞掉了也能快速重建。

## 03｜一次到位選 pgvector 映像

建議檔名：`03-pgvector-image.wav`

還有一個原因，是為課程後面鋪路。我們 Day 2 要做 RAG，會需要一個叫 pgvector 的向量擴充，讓資料庫可以存向量、做相似搜尋。

所以我們從一開始就不用普通的 PostgreSQL 映像，直接用帶有 pgvector 的 PostgreSQL 18 映像，一次到位，之後就不用再換。

## 04｜把 docker-compose.yml 交給 AI

建議檔名：`04-compose-with-ai.wav`

我們來實際操作。先確認 Docker Desktop 正常運行，最簡單的驗證就是跑一次 docker run hello-world，看到歡迎訊息就沒問題。

接著我們不自己手寫設定檔，而是把需求講清楚，請 AI Agent 在專案根目錄建立 docker-compose.yml。提示詞的完整原文在教學網站上，不用抄，你只要聽懂重點：映像用 pgvector/pgvector:pg18；資料庫叫 learn_spring，使用者 postgres、密碼 password；本機 5432 埠對應容器 5432 埠，這樣 Spring Boot 才連得進去。還有最後一項，我下一段單獨講，因為它是本章的驗收重點。

## 05｜具名卷：重開之後資料還在

建議檔名：`05-named-volume.wav`

最重要的需求，是要用具名卷，named volume，讓資料持久化。什麼意思？容器本身是可以隨時砍掉重建的，如果資料存在容器裡面，容器一刪，資料就跟著消失。具名卷就是把資料放在容器外面的一塊空間，容器重建之後再掛回來，資料就還在。

這正是這一章反覆強調的驗收重點：重開之後，資料還要在。另外我也請 AI 在每個設定項目加上中文註解，之後回頭看檔案，每一行在做什麼都一目了然。

## 06｜啟動驗證與排錯

建議檔名：`06-verify-and-troubleshoot.wav`

提示詞送出後，AI Agent 會建立檔案，執行 docker-compose up -d 把容器在背景啟動，接著跑 docker ps 列出正在執行的容器。重點看 STATUS 那一欄——狀態是 Up，就代表 PostgreSQL 成功跑起來了。

那如果不順利呢？容器啟動失敗很常見，最典型的就是 5432 埠被本機另一個 PostgreSQL 佔走了。遇到這種情況不要慌，教材裡有第二個提示詞：把 docker logs 的錯誤訊息原封不動貼給 AI Agent，請它找出原因並修正。這就是我們一直在練的協作模式——你不用背每一種錯誤的解法，但你要會把完整的錯誤訊息交給 AI。

## 07｜本節總結與下一節

建議檔名：`07-closing.wav`

總結一下：這一節我們用 Docker 跑起了 PostgreSQL 18 加 pgvector 的容器，並且用具名卷確保資料不會因為容器重建而消失。

資料庫有了，但現在裡面還是空的，一張表都沒有。下一節，我們要用 Flyway，以「有版本管理」的方式把資料表建起來——為什麼建表也需要版本管理？我們下一節見。

## 邊念邊操作提示卡

1. 先打開 compose 檔，游標依序停在 image、port、database、volume；念到密碼時說明使用環境變數或遮罩。
2. 執行 `docker compose up -d`、`docker compose ps` 和 `docker compose logs --tail=100 postgres`，每個命令之間停一下，讓學員看見 ready 證據。
3. 示範用 psql 查詢資料庫，再重啟 container；旁白要等查詢結果出現，明確指出資料保留不是由畫面猜測。
4. 故意展示錯誤 port 的連線失敗，說明接下來如何分辨容器、網路與 Spring 設定問題。

## 交付檔案命名

```text
01-why-persistence.wav
02-why-docker.wav
03-pgvector-image.wav
04-compose-with-ai.wav
05-named-volume.wav
06-verify-and-troubleshoot.wav
07-closing.wav
```
