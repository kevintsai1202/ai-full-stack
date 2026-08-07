# 章節 4 作業 1｜為 AI CRM 加權限控管

## 單元定位

本章作業：把四個單元學到的 OpenAPI 文件、全域錯誤處理、Log、Security 與 JWT，實際落在自己的 AI CRM 專案上，交出一個「未登入擋 401、角色不符擋 403、文件受保護、錯誤格式統一」的受保護 API。這份作業的成果是第五章 React 前端串接登入與 Token 的直接前提。建議時間：課後 2～3 小時。

## 作業說明

依 u4.md 的實作任務清單，分四組完成：

**A. OpenAPI 文件（對應單元 1）**
- u4-t1：在 pom.xml 加入 springdoc-openapi 依賴並啟動應用，確認可存取 `/swagger-ui.html`。
- u4-t2：在 CustomerController 加上 `@Operation` 與 `@ApiResponse` 標註。
- u4-t3：建立 OpenApiConfig 設定全域 API 資訊。

**B. 全域錯誤處理與 Log（對應單元 2）**
- u4-t4：建立 ErrorResponse record 作為統一錯誤格式。
- u4-t5：建立 GlobalExceptionHandler，處理 404 與驗證失敗例外。
- u4-t6：建立 ResourceNotFoundException，在 CustomerService 找不到客戶時拋出。
- u4-t7：在 CustomerService 加上 `@Slf4j`，在新增、刪除操作加上適當的 INFO / WARN Log。
- u4-t8：加入 spring-boot-starter-actuator，驗證可透過 `PATCH /actuator/loggers` 動態調整層級。

**C. AOP 觀念（選讀，對應單元 2）**
- u4-t9：閱讀 AOP 五大元素說明，能用自己的話描述 Aspect、Advice、Pointcut 的關係。
- u4-t10：對照第一天功能清單，說明至少 3 個用到 AOP 的地方與其對應的 Advice 類型。

**D. Security 與 JWT（對應單元 3、4）**
- u4-t11：透過 AI 提示詞引入 Security 與 JWT 相關依賴。
- u4-t12：實作 JwtUtils 產生包含角色資訊的 Token，並完成認證過濾器與 SecurityConfig 配置。
- u4-t13：實作 `/api/auth/login` 登入 API，並為客戶 API 設定角色權限控制（ADMIN 限制刪除）。

**驗收標準**（依 u4.md 的 Swagger 驗證步驟與 prompts ✅ 驗證）：

1. 未帶 Token 呼叫客戶 API → 401。
2. Swagger UI 需登入才能瀏覽；於 `POST /api/auth/login` 取得 JWT 後，用 Authorize 按鈕帶入 `BearerAuth`。
3. 以 `user` 身分呼叫刪除客戶 API → 403 Forbidden；換 `admin` 的 Token → 204 No Content。
4. 錯誤回應（404 / 400 / 403）皆為統一 JSON 格式，非原始堆疊訊息。
5. 所有程式碼具備中文註解。

## 口語稿

好，第四章的作業來了，題目就叫「為 AI CRM 加權限控管」。這份作業非常關鍵，我直接講明它的份量：第五章我們要做 React 前端，前端的第一個畫面就是登入頁，登入之後要存 JWT、每次呼叫 API 自動帶 Token——這一切的前提，就是你這次作業做出來的東西。所以這份作業不是練習題，它是你主軸專案的正式地基。

作業內容分四組，就是這一章四個單元的落地。第一組，文件：加入 springdoc-openapi、確認 swagger-ui 打得開，幫 CustomerController 加上 @Operation 和 @ApiResponse 標註，再建一個 OpenApiConfig 設定標題、版本這些全域資訊。第二組，錯誤處理和 Log：建立 ErrorResponse 統一格式、GlobalExceptionHandler 處理 404 和驗證失敗、自訂 ResourceNotFoundException 在查無客戶時拋出，然後在 CustomerService 加 @Slf4j，新增和刪除操作要有合適的 INFO 和 WARN log，最後加入 Actuator，實際用 PATCH /actuator/loggers 動態調一次層級試試看。第三組是選讀的觀念題：用自己的話說明 Aspect、Advice、Pointcut 的關係，然後對照第一天學過的功能，找出至少三個背後用到 AOP 的地方、說出各是哪種 Advice。這題不用寫程式，但很能檢驗你是不是真的懂了。第四組是重頭戲，Security 和 JWT：用課程提供的 AI 提示詞引入依賴，實作 JwtUtils 讓 Token 裡帶角色資訊，完成認證過濾器和 SecurityConfig，做出 /api/auth/login 登入 API，並且限制刪除客戶必須是 ADMIN。

接下來是驗收標準，請你交作業前自己先跑一遍，我改作業也會照這個順序跑。第一關，不帶 Token 直接呼叫客戶 API，必須看到 401——如果這時候還查得到資料，表示你的 SecurityConfig 沒生效。第二關，打開 Swagger UI，它應該要求登入才能看；登入後用 login 端點拿 JWT，點 Authorize 按鈕貼進去。第三關是角色測試，這是整份作業的靈魂：用 user 的 Token 去刪客戶，必須被 403 擋下來；換成 admin 的 Token，刪除成功回 204。一般使用者刪得掉資料，這份作業就是不及格的，因為它正是我們要防的那個洞。第四關，錯誤格式：404、400、403 這些錯誤回應都必須是統一的 JSON 格式，如果冒出原始的 Java 堆疊訊息，回去檢查你的 GlobalExceptionHandler。最後，老規矩，所有程式碼要有中文註解——用 AI 生程式沒問題，但註解寫不寫得出來，就看你是不是真的看懂了它生的東西。

給你兩個小提醒。第一，實作順序建議照 A、B、D 走，先有文件和錯誤處理，再上 Security，因為 Security 上了之後每次測試都要帶 Token，前面的東西先驗完會省很多事。第二，如果 AI 生完之後「帶了 Token 還是被擋」或「莫名其妙出現 Session」，回去用單元 3 的五大零件清單逐一核對，八成是過濾器沒掛進鏈裡或忘了設 STATELESS。

做完這份作業，你的 AI CRM 就正式從 demo 升級成一個有門禁的系統了。下一章，我們就來打造 React 19 的 CRM 工作台，讓使用者真正透過漂亮的介面登入、查客戶。作業加油，我們第五章見。
