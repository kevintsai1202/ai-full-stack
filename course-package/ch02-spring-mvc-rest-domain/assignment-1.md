# 章節 2 作業 1｜完成測試用 API

## 單元定位

本章作業。學員需獨立完成一個不依賴資料庫、可立即啟動的客戶 REST API，並以 PowerShell 實際驗證端點行為，作為下一章接入 PostgreSQL / JPA 的起點。

## 作業說明

依 u2 的實作任務（tasks）完成以下三項：

- **u2-t1**：閱讀 Spring MVC 請求流程，理解 DispatcherServlet 的角色
- **u2-t2**：用 AI Agent 建立可獨立運行的客戶 REST API 示範專案
- **u2-t3**：用 PowerShell `Invoke-RestMethod` 驗證 GET / POST 端點可正常回應

### 驗收標準

1. 專案只依賴 `spring-boot-starter-web`，不含 JPA 或 PostgreSQL，`mvn spring-boot:run` 可直接啟動。
2. 三個端點行為正確：
   - `GET /api/customers` → 回傳全部客戶
   - `GET /api/customers/{id}` → 找不到回傳 404
   - `POST /api/customers` → 新增客戶，回傳 201
3. Controller / Service 分層清楚：Controller 不含業務邏輯，資料由 Service 的記憶體集合管理。
4. 程式碼具備中文函式級別註解。
5. 附上 PowerShell `Invoke-RestMethod` 的驗證結果（GET 與 POST 皆需實際呼叫成功）。

## 口語稿

好，這一章的作業來了，題目叫「完成測試用 API」。我先把要做的三件事講清楚，再講我驗收的時候會看什麼。

第一件事，u2-t1，是閱讀任務：把 Spring MVC 的請求流程再走一遍，重點是理解 DispatcherServlet 的角色。你要能夠不看講義，自己講出一個請求從進來到回傳 JSON 中間經過哪幾站。為什麼我把「讀懂流程」列成作業的一部分？因為後面每一章出問題的時候，你腦中有沒有這張流程圖，除錯速度會差十倍。

第二件事，u2-t2，是實作任務：用 AI Agent 建立一個可以獨立運行的客戶 REST API 示範專案。注意「獨立運行」四個字——這個專案只需要 spring-boot-starter-web 一個依賴，不要 JPA、不要 PostgreSQL，mvn spring-boot:run 下去就要能起來。三個端點的行為我明確規定：GET /api/customers 回傳全部客戶；GET /api/customers/{id} 找不到的時候要回 404，不是回空的、也不是噴 500；POST /api/customers 新增成功要回 201，不是 200。這些狀態碼的差別就是這一章第二節講的內容，作業就是在檢查你有沒有把它做進去。另外兩個要求：Controller 和 Service 要分層乾淨，Controller 裡面不准出現業務邏輯，資料放在 Service 的記憶體集合裡管理；還有，程式碼要有中文的函式級別註解——這不只是課程規定，AI 產的程式碼你要求它加中文註解，其實也是在逼它把每個方法的意圖講清楚，方便你審查。

第三件事，u2-t3，是驗證任務：用 PowerShell 的 Invoke-RestMethod，把 GET 和 POST 端點實際打一遍，確認正常回應。我特別強調「實際打一遍」——不是看程式碼覺得應該沒問題就交了。你要真的啟動專案、真的發請求、真的看到 GET 回來一串客戶 JSON、POST 回來 201 和新建的那筆資料。交作業的時候，請把這些驗證的指令和結果一起附上來。如果你行有餘力，把上課教的那招用上：請 AI 把驗證步驟整理成一個可以重跑的腳本，下一章換上資料庫之後同一份腳本再跑一次，你就能親眼看到「介面沒變、底層換了」是什麼感覺。

驗收標準我再收攏一次，總共五條：一、只依賴 starter-web、可直接啟動；二、三個端點的路徑、方法、狀態碼行為全部正確；三、分層乾淨；四、中文函式註解；五、附上 PowerShell 的實測結果。五條都達標，這份作業就過了。

最後給一個建議：做這份作業的時候，不要只當「提示詞的搬運工」。AI 產出來的每一個檔案，都花一分鐘看過——它為什麼這樣分層、404 是怎麼回的、201 是在哪裡設定的。這一章的作業是後面所有章節的地基，下一章開始，我們就要把這套 API 底下的記憶體 List，換成真正的 PostgreSQL 資料庫了。我們下一章見。
