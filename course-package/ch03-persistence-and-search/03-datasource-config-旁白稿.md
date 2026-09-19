# 03-datasource-config｜資料庫連線設定旁白稿

本稿依據 `03-datasource-config.md` 的教學素材與口語稿整理，供人工錄音使用。共 7 段，錄音時可依段落分開保存，方便後續與投影片時間軸對齊。

建議錄音格式：WAV 或 MP3。每段開頭保持自然，結尾保留約 0.5 秒空白。

## 01｜把三方接起來

建議檔名：`01-connect-the-pieces.wav`

到目前為止，我們手上有兩個各自獨立的東西：一邊是 Docker 裡跑著的 PostgreSQL 容器，一邊是專案裡的 Flyway 遷移腳本。但你現在啟動 Spring Boot，什麼事都不會發生——因為應用程式根本不知道資料庫在哪裡、帳號密碼是什麼、腳本要不要跑。

這一節要做的事很單純，就是把這三方接起來，而接線的地方，就是 Spring Boot 的設定檔 application.yml。

## 02｜程式碼描述行為，設定檔描述環境

建議檔名：`02-config-philosophy.wav`

為什麼所有連線資訊都集中在 application.yml？這是 Spring Boot 的設計哲學：程式碼描述「行為」，設定檔描述「環境」。

同一份程式碼，在你的本機連 localhost 的容器，到了正式機換一份設定就能連正式資料庫，程式碼一行都不用改。所以把連線設定寫對、寫清楚，是一件基本功。

## 03｜三塊設定的用意

建議檔名：`03-three-config-blocks.wav`

我們要加的設定有三塊。第一塊是 datasource，資料來源：URL 指向 localhost 的 5432 埠、資料庫名稱 learn_spring，帳號 postgres、密碼 password——這組資訊要和上一個單元 docker-compose.yml 裡的完全一致，少一個字都連不上。

第二塊是 flyway：enabled 設為 true，腳本位置指向 classpath:db/migration，也就是放 V1 腳本的目錄；再加上 baseline-on-migrate: true，這是處理「資料庫已經存在但沒有 Flyway 記錄」的情況，讓 Flyway 能以現況為基準開始接管。

第三塊是 jpa 的 ddl-auto: validate——還記得上一節的職責分工嗎？Schema 由 Flyway 管，JPA 只負責驗證 Entity 和資料表結構有沒有吻合，不准它自己動手改表。

## 04｜交給 AI 修改設定檔

建議檔名：`04-config-with-ai.wav`

我們來實際操作。一樣把需求交給 AI Agent，提示詞在教學網站上都有，不用抄。但有一句我要特別點出來：「若已存在請直接修改，不要重複」。

這是實務上很重要的小細節，因為 application.yml 裡可能已經有其他設定，你不希望 AI 疊出兩份重複的區塊造成格式錯誤。然後照慣例，每個設定項目都要加中文註解，之後回頭看才知道每一行的用途。

## 05｜驗收點：Successfully applied

建議檔名：`05-first-migration.wav`

設定完成後，重頭戲來了：請 AI Agent 執行 mvn spring-boot:run。這次啟動和以前不一樣，你會看到 log 裡多了 Flyway 的訊息——它先連上資料庫，檢查目前的版本狀態，然後開始套用還沒執行過的腳本。

你要找的關鍵字是「Successfully applied N migration(s)」。看到這一行，就代表 V1 腳本成功執行，客戶、聯絡人、往來紀錄、生意機會這幾張表都建進資料庫了。這行訊息就是本節的驗收點。

## 06｜重開一次：不會重複建表

建議檔名：`06-restart-check.wav`

而且我要你多做一件事：把應用程式停掉，再啟動一次。第二次啟動時，你會看到 Flyway 說 Schema 已經是最新版，沒有任何腳本需要套用——它不會重複建表，也不會報錯。

這正是本章一開始就講的驗收重點之一：「重開時不會發生重複建表的錯誤」。Flyway 在資料庫裡有一張自己的歷史表，記錄每支腳本跑過沒有，所以它永遠知道該做什麼、不該做什麼。

那如果啟動失敗呢？最常見兩種：連線被拒絕，八成是容器沒開，先 docker ps 檢查；Flyway 報 migration 失敗，通常是 SQL 語法或命名問題。處理方式一樣：把 log 的錯誤訊息完整貼給 AI Agent，不要只貼一行，前後文都給它。

## 07｜本節總結與下一節

建議檔名：`07-closing.wav`

總結一下：這一節我們在 application.yml 接好了 datasource、Flyway 和 JPA 三塊設定，啟動後看到 Successfully applied 訊息，資料庫環境正式就緒，而且重開也不會重複建表。

不過現在資料表雖然有了，我們的程式還是用 List 在存資料，兩邊根本沒關係。下一節，我們就要進入 ORM 的世界，用 JPA 的 Entity 和 Repository，讓 Java 物件真正對接到資料表。

## 邊念邊操作提示卡

1. 先顯示設定表，再打開 `application.yml`，逐一指出 URL、username、profile 與秘密值來源；密碼只顯示遮罩。
2. 啟動 PostgreSQL 後先執行 `docker compose ps`，再啟動 Spring；旁白念「現在比對實際 log」時，停在 datasource 資訊。
3. 執行一個 repository test 或查詢 endpoint，讓畫面出現真正讀到資料庫的證據。
4. 用錯誤帳密或錯誤 database 做一次分流示範，最後保存 local/test profile 的結果。

## 交付檔案命名

```text
01-connect-the-pieces.wav
02-config-philosophy.wav
03-three-config-blocks.wav
04-config-with-ai.wav
05-first-migration.wav
06-restart-check.wav
07-closing.wav
```
