# 章節 4 單元 3｜Spring Security 及 JWT 認證

## 單元定位

本章的重頭戲：前三章做出來、上一節錯誤處理也統一的客戶 API，目前仍是完全公開的。本節引入 Spring Security 與 JWT 建立無狀態認證——登入簽發 Token、JWT 過濾器逐請求驗章、SecurityFilterChain 集中管規則、ADMIN/USER 角色授權，並把 Swagger UI 也納入保護。實作交給 AI，我們的工作是建立心智模型以核對 AI 產物。建議時長：35～40 分鐘。

## 教學素材

### 安全防護重點

在生產環境中，API 不能是完全公開的。本節引入 Spring Security 與 JWT（JSON Web Token），為 REST API 建立安全防護底盤，實作「無狀態（Stateless）」認證：使用者透過 `/api/auth/login` 登入成功後取得 JWT，後續請求都必須在 Header 攜帶此 Token 進行驗證，並簡單區分「管理員（ADMIN）」與「一般用戶（USER）」兩種角色。

- **Authentication 認證**：確認「你是誰」（透過帳號密碼登入並簽發 JWT）。
- **Authorization 授權**：確認「你能做什麼」（例如管理員能刪除客戶資料，業務人員只能查詢與編輯）。
- **無狀態認證**：伺服器不儲存 Session，每次請求均由 JWT 驗證身分與角色。

### JWT 三段式結構與無狀態原理

JWT 是一段「自帶簽章、可被任何服務獨立驗證」的字串，長得像 `xxxxx.yyyyy.zzzzz`，由三段 Base64 編碼組成：

- **Header（標頭）**：宣告簽章演算法（例如 HS256）與型別。
- **Payload（負載）**：放使用者資訊與宣告（Claims），例如帳號、角色（ADMIN / USER）、簽發時間與過期時間（exp）。
- **Signature（簽章）**：用只有伺服器知道的密鑰對前兩段做簽章；任何人改動 Header 或 Payload，簽章就會對不起來。

傳統 Session 認證要伺服器記住每個登入者；JWT 把身分與角色直接寫進 Token 並簽章，伺服器只需驗證簽章有效且未過期就能信任內容，不必查任何儲存。好處是水平擴充容易（多台伺服器不需共享 Session）；代價是 Token 一旦簽發，到期前較難即時撤銷。

典型流程：① 帳密呼叫 `/api/auth/login` → ② 驗證成功後把帳號與角色寫進 Payload、簽章回傳 JWT → ③ 前端存起來（如 localStorage），每次請求帶 `Authorization: Bearer <token>` → ④ 伺服器的安全過濾器驗簽章與效期、解析角色，決定能否存取。

**安全提醒**：Payload 只是 Base64 編碼、不是加密，絕對不要放密碼或機密資料；務必設定合理的 exp，簽章密鑰從環境變數讀、不要寫死在程式碼。

### Spring Security 中的 JWT 實作五大零件

實際細節交給 AI 產生即可，先建立整體心智模型，方便核對 AI 的產物：

1. **JWT 工具（JwtUtils，簽發與解析）**：用業界常見的 `io.jsonwebtoken`（jjwt）套件，負責「用密鑰把角色等資訊簽成 Token」與「驗證簽章、解析出 Claims」。
2. **登入端點（簽發 Token）**：`POST /api/auth/login` 收帳密 → 驗證成功後把帳號與角色寫進 Payload、簽發 JWT。整條認證鏈唯一「免 Token 就能存取」的入口。
3. **JWT 驗證過濾器（每個請求驗章）**：自訂過濾器攔截每個請求，從 `Authorization: Bearer <token>` 取出 Token、驗章與效期，成功就把身分與角色放進 SecurityContext。
4. **安全設定鏈（SecurityFilterChain / SecurityConfig）**：關閉 Session 改用無狀態（STATELESS）、放行登入與 Swagger 文件、其餘 API 一律需驗證，並把 JWT 過濾器掛進 Filter Chain。
5. **角色授權（誰能做什麼）**：刪除客戶必須 ADMIN，查詢/編輯一般登入身分即可；可用方法層級（如 `@PreAuthorize`）或在安全設定鏈裡依路由限制。

對照這五個零件即可檢查 AI 生成是否齊全：少了過濾器會「帶了 Token 卻仍被擋」，少了無狀態設定會「莫名其妙產生 Session」，少了角色限制則「一般使用者也刪得掉資料」。

依賴部分：`spring-boot-starter-security` ＋ jjwt 三件組（`jjwt-api`、`jjwt-impl`、`jjwt-jackson`，0.12.5，後兩者 scope 為 runtime）。

## 示範與提示詞

**AI Agent 提示詞 — 身分驗證與角色授權實作**（u4.md 原文）：

```text
請在現有專案中，使用 Spring Security 與 JWT 實作安全防護與登入驗證功能：
1. 引入安全防護套件（Spring Security）與 JWT 依賴，限制除了登入相關的 API 之外，其餘所有的 API 都需要攜帶 JWT Token 進行驗證才能存取。
2. 實作一個登入 API（例如 POST /api/auth/login），供使用者傳入帳號密碼進行身分驗證。登入成功後，請在 JWT Token 中寫入使用者的角色（簡單區分「管理員 ADMIN」與「一般用戶 USER」），並將 Token 回傳給前端。
3. 實作角色權限控制：限制客戶資料刪除 API 必須具備「管理員 ADMIN」角色才能執行，而一般查詢與編輯功能則僅需「用戶 USER」或已登入身分即可。
4. 保護我們的 API 文件（Swagger UI 網頁與相關端點），設定必須在登入驗證並攜帶 JWT Token 後才能正常瀏覽與測試。
```

面向一般使用者的說法（本章 prompts「① 加上登入與權限控管」）：

```text
請幫這套系統加上登入功能：沒登入的人不能使用、要登入後才能看資料。而且要分權限——分成「一般使用者」和「管理員」兩種，只有管理員可以刪除客戶。請加中文註解。完成後我要能驗證：用一般帳號登入後查得到客戶，但刪客戶會被擋下來；換成管理員才刪得掉。
```

**Swagger 網頁驗證步驟（推薦）**：

1. 開啟 `http://localhost:8080/swagger-ui/index.html`。
2. 安全登入（HTTP Basic）：瀏覽器彈出登入對話框，輸入管理員帳密（帳號 `admin`、密碼 `password`）。
3. 取得 JWT：展開 `POST /api/auth/login`，Try it out 傳入 `{"username": "user", "password": "password"}`，複製回傳的 token。
4. 點頁面上方 **Authorize** 按鈕，在 `BearerAuth` 欄位貼上 JWT 並啟用。
5. 驗證 RBAC：以 `user` 授權狀態呼叫刪除客戶 API，預期 **403 Forbidden**；換成 `admin` 的 Token 重呼叫，應成功回傳 **204 No Content**。

## 口語稿

好，來到這一章最重要的一節。我先問你一個問題：現在你的客戶 API，如果我拿到網址，我能不能把你資料庫裡的客戶全部刪光？答案是可以，而且不需要任何身分。這就是 demo 跟正式系統最赤裸裸的差距——正式系統的第一條防線就是：沒登入的人，什麼都不能做。

要搞懂 API 安全，先分清楚兩個詞。Authentication，認證，回答的是「你是誰」——你用帳號密碼登入，系統確認你的身分。Authorization，授權，回答的是「你能做什麼」——同樣是登入的人，管理員能刪客戶，業務人員只能查詢和編輯。這兩個詞整章會一直出現，認證是門禁，授權是門禁後面的房間鑰匙。

那身分要怎麼在每次請求之間傳遞？傳統做法是 Session，伺服器記住每個登入的人。但我們要做的是無狀態認證，用的是 JWT。JWT 長什麼樣子？三段字串用點連起來。第一段 Header，宣告簽章演算法；第二段 Payload，放你的帳號、角色、過期時間；第三段 Signature，是伺服器用密鑰對前兩段做的簽章。關鍵就在這個簽章：任何人只要改動前兩段的任何一個字，簽章就對不起來，伺服器立刻知道這是假的。所以伺服器收到 Token，只要驗簽章、看沒過期，就能信任裡面寫的身分和角色，完全不用查資料庫、不用記 Session。這就是「無狀態」，好處是以後開十台伺服器也不用共享 Session。但有兩件事你一定要記住：第一，Payload 只是 Base64 編碼、不是加密，任何人都解得開來看，所以絕對不要把密碼放進去；第二，密鑰要從環境變數讀，不要寫死在程式碼裡。

概念懂了，實作怎麼做？這一段我們交給 AI，但是——這正是這門課一直強調的——你要先有心智模型，才有能力核對 AI 的產物。Spring Security 加 JWT 總共就五個零件。第一，JwtUtils，用 jjwt 套件負責簽發和解析 Token。第二，登入端點，POST /api/auth/login，收帳密、驗證成功就把角色寫進 Payload 簽發 Token，這是整條認證鏈唯一不用帶 Token 的入口。第三，JWT 驗證過濾器，攔截每一個進來的請求，從 Authorization: Bearer 標頭取出 Token 驗章，成功就把身分放進 SecurityContext。第四，SecurityFilterChain，集中設定規則：關掉 Session 改成 STATELESS、放行登入和 Swagger、其他一律要驗證，然後把過濾器掛進鏈裡。第五，角色授權，刪除客戶必須 ADMIN，可以用 @PreAuthorize 或在設定鏈裡依路由限制。這五個零件記熟，AI 少做哪個你一眼就看得出來：少了過濾器，你帶了 Token 還是被擋；少了無狀態設定，系統會莫名其妙長出 Session；少了角色限制，一般使用者也刪得掉資料。

我們現在來實際跑一次。把課程提供的提示詞丟給 AI Agent，它會引入 Security 和 jjwt 依賴、生出這五個零件。跑完之後重啟應用，來驗證——這是本節最過癮的部分。先不帶 Token 呼叫客戶查詢 API，你會看到 401，被擋在門外了，這是好事。接著打開 Swagger UI，因為文件也被保護了，瀏覽器會先跳出登入框，輸入 admin 和 password 進去。然後展開 /api/auth/login，用 user 帳號執行，把回傳的 token 複製起來，點頁面最上方的 Authorize 按鈕，貼進 BearerAuth 欄位。現在你是「一般使用者」的身分了——查詢客戶，成功；試著刪除客戶，你會看到 403 Forbidden，被角色權限擋下來了。最後換成 admin 的 Token 重新 Authorize，再刪一次——204 No Content，刪掉了。401、403、204，這三個狀態碼跑一輪，你的認證和授權就都驗證完了。

總結：這一節我們用五個零件把 API 從「裸奔」變成「有門禁、有房間鑰匙」的受保護系統，而且全程在 Swagger 上視覺化驗證。不過 ADMIN 和 USER 兩種角色對真實的 CRM 來說還太粗糙——業務看得到誰的客戶？主管看得到什麼報表？下一節我們就來設計 AI CRM 真正的權限模型。
