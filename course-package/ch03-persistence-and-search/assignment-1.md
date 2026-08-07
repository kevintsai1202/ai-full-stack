# 章節 3 作業 1｜完成資料庫建立並測試資料能透過資料庫存取

## 單元定位

本章作業：把章節 3 學到的完整流程——Docker 起資料庫、Flyway 建表、JPA 接資料、驗證持久化——在自己的專案上從頭到尾實際跑一遍。驗收核心是本章反覆強調的兩件事：**重開之後資料還在、重開時沒有重複建表**。

## 作業說明

依序完成以下六個實作任務（對應 u3-t1 ~ u3-t6）：

1. **u3-t1**：用 AI Agent 安裝 Docker Desktop 並執行 `docker run hello-world` 驗證
2. **u3-t2**：用 AI Agent 建立 `docker-compose.yml` 並啟動 PostgreSQL 18 容器
3. **u3-t3**：用 AI Agent 設定 `application.yml`，執行 `mvn spring-boot:run` 確認 Flyway 遷移成功
4. **u3-t4**：用 AI Agent 為 1-2 專案加入 JPA 依賴並更新 Product.java 為 Entity
5. **u3-t5**：用 AI Agent 建立 CustomerRepository 並更新 CustomerService 改用資料庫
6. **u3-t6**：執行 `mvn spring-boot:run`，確認 API 仍正常運作且資料已寫入資料庫

**驗收標準**（可直接用以下驗證提示詞交給 AI Agent 檢查）：

```text
請幫我確認資料確實存進了資料庫：新增一筆客戶後查得到；把專案重開後，那筆資料還在；而且重開時沒有發生「重複建表」之類的錯誤。
```

- `docker ps` 中 PostgreSQL 容器狀態為 `Up`
- 首次啟動 log 出現 `Successfully applied N migration(s)`
- 透過 API 新增資料後查詢得到；應用程式重啟後資料仍在
- 重啟時 Flyway 不重複執行腳本、`ddl-auto: validate` 通過，無任何建表錯誤

## 口語稿

好，來交代這一章的作業。這次作業沒有新的東西，就是把我們這一章走過的完整流程，在你自己的專案上、用你自己的雙手——嗯，正確地說，是用你自己指揮的 AI Agent——從頭到尾跑一遍。我一直相信，看我做十遍，不如你自己做一遍，尤其是環境建置這種事，每台電腦都會遇到自己獨特的小狀況，那些排查的經驗才是你真正帶得走的東西。

作業一共六個任務，我照順序講一遍。第一步，確認 Docker Desktop 裝好了，跑一次 docker run hello-world，看到歡迎訊息才算數。第二步，請 AI Agent 建立 docker-compose.yml，把 PostgreSQL 18 加 pgvector 的容器跑起來，記得要有具名卷做資料持久化。第三步，設定 application.yml——datasource、Flyway、ddl-auto: validate 三塊——然後 mvn spring-boot:run，在 log 裡找到 Successfully applied 幾個 migration 的訊息。第四步，回到 1-2 章節建立的那個專案，請 AI Agent 加入 JPA 依賴，把 Product.java 升級成 Entity。第五步，建立 CustomerRepository，更新 CustomerService，把記憶體的 List 換成真正的資料庫操作。第六步，再跑一次 mvn spring-boot:run，確認 API 的行為跟以前一模一樣，但資料已經是寫進資料庫了。

那怎麼樣才算通過驗收？標準就是本章講了一整章的那兩句話：重開資料還在、沒有重複建表。具體的檢查點有四個。第一，docker ps 看得到容器狀態是 Up。第二，第一次啟動的 log 有 Flyway 遷移成功的訊息。第三，也是最核心的一個：用 API 新增一筆客戶，查詢確認查得到，然後把應用程式整個關掉、重新啟動，再查一次——那筆資料必須還在。如果重開之後資料不見了，通常是資料庫其實沒接上、還在走記憶體，或者 volume 沒設好，回去檢查第二步和第五步。第四，重啟的時候觀察 log，Flyway 不會重複執行腳本，validate 也要通過，不能出現任何重複建表或結構不符的錯誤。這四點我也幫你整理成一段驗證提示詞，放在作業說明裡，你可以直接把它丟給 AI Agent，讓它幫你逐項檢查——不過我建議你先自己看懂每個檢查點在驗什麼，再讓 AI 動手。

做作業的時候有兩個提醒。第一，過程中一定會遇到錯誤——埠被占用、連線被拒絕、Flyway 腳本命名打錯——遇到錯誤不要急著重來，把完整的錯誤訊息貼給 AI Agent，請它排查，這個來回的過程本身就是這門課要練的能力。第二，做完之後留意一下你的 Flyway 腳本命名，雙底線數一數，很多「表沒建出來又沒報錯」的靈異事件都是它。

這份作業完成之後，你的 CRM 就有了一個真正可靠的資料底座：結構有版本管理、資料重開不丟、查詢還能多條件任意組合。下一章，我們要在這個底座上蓋安全機制——Spring Security 加 JWT，讓系統知道「你是誰、你能做什麼」。祝作業順利，我們下一章見。
