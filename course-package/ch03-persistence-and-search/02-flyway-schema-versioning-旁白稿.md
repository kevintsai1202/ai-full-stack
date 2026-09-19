# 02-flyway-schema-versioning｜透過 Flyway 進行資料庫 Schema 版控旁白稿

本稿依據 `02-flyway-schema-versioning.md` 的教學素材與口語稿整理，供人工錄音使用。共 7 段，錄音時可依段落分開保存，方便後續與投影片時間軸對齊。

建議錄音格式：WAV 或 MP3。每段開頭保持自然，結尾保留約 0.5 秒空白。

## 01｜資料庫結構也需要版本管理

建議檔名：`01-why-schema-versioning.wav`

資料庫跑起來了，接下來要建表。你可能會想：建表不就是打開資料庫工具，下幾行 CREATE TABLE 就好了嗎？可以，但我先問你一個「為什麼」：三個月之後，這個專案的資料庫多了十幾張表、改過幾十次欄位，你還記得每一次是誰改的、為什麼改、改了什麼嗎？測試機和正式機的結構還一致嗎？新同事加入專案，要怎麼在他的電腦上長出一模一樣的資料庫？

如果建表、改表都是手動下 SQL，這些問題通通沒有答案。程式碼我們有 Git 做版本管理，那資料庫結構呢？資料庫結構也需要版本管理，這就是 Flyway 要解決的問題。

## 02｜Flyway 的運作方式

建議檔名：`02-how-flyway-works.wav`

Flyway 的運作方式很直觀：你把每一次的 Schema 變更寫成一支 SQL 腳本，放在專案的 db/migration 目錄下，每支腳本有一個版本號。應用程式啟動時，Flyway 會檢查資料庫目前套用到哪一版，把還沒跑過的版本依序執行。

所以 V1 通常負責基礎 Schema，把最初的資料表建起來；之後要加欄位、加索引、放測試資料，或像我們 Day 2 要加向量表，就開 V2、V3 往下編。

## 03｜紀律：只加新版本、不改舊版本

建議檔名：`03-never-edit-old-versions.wav`

這裡有一個非常重要的紀律：永遠不要回頭改舊版本，而是新增下一個版本。

V1 一旦在任何環境執行過，它就是歷史了。Flyway 會記住它的雜湊值，你偷偷改了內容，下次啟動就會直接報錯。要改結構？開一支新的 V2。這樣整個團隊、每一個環境，都是用同一條遷移歷史還原出資料庫。

## 04｜雙底線陷阱

建議檔名：`04-double-underscore.wav`

再來我要特別提醒一個新手幾乎人人踩過的坑——命名規則的雙底線陷阱。

Flyway 腳本的命名格式是大寫 V、版本號、然後「兩個底線」、再接描述，例如 V1__init_schema.sql。注意，版本號和描述中間是雙底線。如果你只打了一個底線，Flyway 不會報錯，它只是安靜地忽略這支檔案。結果就是你啟動程式，表沒有建出來，卻連一行錯誤訊息都沒有，你會查得非常痛苦。

所以之後只要遇到「表怎麼沒建出來」的狀況，第一件事就是回頭數一數底線是不是兩個。

## 05｜Flyway 與 ddl-auto 的分工

建議檔名：`05-flyway-vs-ddl-auto.wav`

接著講一個觀念上的取捨：Flyway 和 JPA 的 ddl-auto。學過 JPA 的同學可能知道，ddl-auto 設成 update，Hibernate 會自動比對 Entity 和資料庫，幫你把缺的表和欄位補上。聽起來很方便，那還要 Flyway 幹嘛？

關鍵在於「有沒有版本記錄」。update 適合開發初期在自己本機快速迭代，但它沒有留下任何腳本，你沒辦法在測試機、正式機重現相同的狀態，也沒辦法回滾。所以我們課程的分工是：所有共享環境一律由 Flyway 管理 Schema 演進，ddl-auto 改成 validate——JPA 不再動資料庫，只在啟動時檢查 Entity 和資料表結構有沒有對上，對不上就直接啟動失敗，等於多一道保險。切換時機是 Entity 設計穩定之後，馬上把 update 改成 validate，並用 V1 建立完整的初始化腳本。

## 06｜用口語需求讓 AI 選對工具

建議檔名：`06-build-with-ai.wav`

我們現在來實際做。這次的提示詞我刻意用很口語的講法：「請把建立資料表這件事，改成有版本管理的方式，這樣以後修改資料表結構才追蹤得到、不會亂掉。先把客戶、聯絡人、往來紀錄、生意機會這幾張表建起來，放一些示範資料進去。」

注意，我沒有在提示詞裡出現 Flyway 這個字。我描述的是「需求」——要有版本管理、要追蹤得到——AI Agent 自然會選用 Flyway，幫我們加依賴、建 db/migration 目錄、寫出 V1__init_schema.sql，把 CRM 的四張表連同 seed data 一次建好，初始化集中在一支腳本。完整提示詞在教學網站上都查得到，課堂上你只要理解為什麼這樣描述就好。

## 07｜本節總結與下一節

建議檔名：`07-closing.wav`

總結一下：Flyway 讓資料庫結構的每一次變更都有版本、可追溯，紀律是「只加新版本、不改舊版本」，命名要小心雙底線，Schema 的主導權交給 Flyway、JPA 只做 validate 驗證。

腳本準備好了，但 Spring Boot 還不知道要去哪裡連資料庫。下一節，我們來設定 application.yml，把應用程式、資料庫、Flyway 三方接起來，讓遷移真正跑起來。

## 邊念邊操作提示卡

1. 先在檔案樹打開 `db/migration/V1__init.sql`，念出版本、兩個底線和描述，再執行 migration。
2. 切到 `flyway_schema_history` 查詢，逐欄指出 version、description、success；不要只展示 Spring 啟動成功。
3. 新增 V2 後重新執行，讓學員看見只套用一次；再展示修改 V1 造成的 checksum mismatch，保留錯誤畫面。
4. 結尾說明修正要新增 V3，並將 history 和 schema 差異存成證據。

## 交付檔案命名

```text
01-why-schema-versioning.wav
02-how-flyway-works.wav
03-never-edit-old-versions.wav
04-double-underscore.wav
05-flyway-vs-ddl-auto.wav
06-build-with-ai.wav
07-closing.wav
```
