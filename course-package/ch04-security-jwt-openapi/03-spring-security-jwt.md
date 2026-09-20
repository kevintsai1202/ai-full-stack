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

依賴部分：`spring-boot-starter-security` ＋ jjwt 三件組（`jjwt-api`、`jjwt-impl`、`jjwt-jackson`，0.13.0，後兩者 scope 為 runtime）。

## 示範與提示詞

**AI Agent 提示詞 — 身分驗證與角色授權實作**（u4.md 原文）：

```text
請在現有專案中，使用 Spring Security 與 JWT 實作安全防護與登入驗證功能：
1. 引入 spring-boot-starter-security 與 jjwt 0.13.x（jjwt-api、jjwt-impl、jjwt-jackson），限制除了 /api/auth/login 與 /api/health 之外，其餘所有的 API 都需要攜帶 Authorization: Bearer <JWT> 才能存取；無狀態（SessionCreationPolicy.STATELESS），未登入回 401、權限不足回 403，格式都用 ProblemDetail。
2. 用 Flyway 新增 app_users 表（username、password_hash、display_name、role、enabled），密碼用 BCrypt；啟動時若無帳號就建立四個示範帳號：sales 與 sales2（SALES 業務）、manager（MANAGER 主管，兩位業務都歸他管）、admin（ADMIN 管理員），密碼都是 password123；要記得住「誰是誰的主管」。
3. 實作 POST /api/auth/login：傳入帳號密碼，成功回傳 token 與使用者資訊（id、username、displayName、role）。登入後我要能查「我是誰、我的角色、我的主管是誰」；主管和管理員還要能列出所有使用者和他們的角色，這樣指派客戶或測試時才找得到人的編號。JWT 的 claims 要有 sub、uid、role、exp，有效期 8 小時；簽章密鑰從環境變數 APP_SECURITY_JWT_SECRET 讀取，長度不足 32 字元就拒絕啟動，不可寫死在程式碼。
4. 實作角色權限控制：DELETE /api/customers/** 只有 ADMIN 能執行；/api/manager/** 只有 MANAGER 與 ADMIN；/api/admin/** 只有 ADMIN；其餘查詢與編輯功能只需已登入。規則集中寫在 SecurityConfig 的 requestMatchers。
5. 保護我們的 API 文件（Swagger UI 網頁與相關端點），設定必須在登入驗證並攜帶 JWT Token 後才能正常瀏覽與測試，並在 OpenAPI 設定宣告 bearerAuth 讓 Authorize 按鈕可用。
請加上繁體中文函式級別註解。
```

面向一般使用者的說法分兩步。先用本章 prompts「⓪ 盤點路徑、產生空權限表」讓 AI 只盤點現況、由學員自己決定權限（填完再對照講義《CRM_角色權限矩陣與RBAC實作規格》的參考答案）：

```text
請掃描這個專案所有 Controller 與 SecurityConfig，盤點目前實際存在的 API 端點（含 /swagger-ui/**、/actuator/**），不要臆測尚未實作的功能。
依盤點結果產生一張 Markdown 空權限表，直接輸出讓我填寫：
- 欄位：功能面向、代表端點、SALES 業務、MANAGER 主管、ADMIN 管理員；三個角色欄位一律留空
- 表下方附圖例：✅ 可存取全部資料｜🔸 可存取但自動套用資料範圍過濾｜❌ 直接回 403 Forbidden
- 另附一張「資料可視範圍」空表（欄位：角色、判定依據、自動加上的查詢條件），讓我填 🔸 的實際規則
盤點完請對照附上的《CRM_角色權限矩陣與RBAC實作規格.md》第二節，另外列一張「講義上有、但我們專案還沒做出來的功能」清單，讓我決定要先補還是先預留。只要輸出表格與清單，先不要寫任何程式。
```

接著才是「① 加上登入與權限控管」，規格改為引用學員填好的權限表：

```text
請幫這套系統加上登入與權限控管，規格照附上的《JWT_架構與認證流程設計.md》與我填好的權限表（圖例、資料可視範圍與 404／403 規則見《CRM_角色權限矩陣與RBAC實作規格.md》）：
- Spring Security + JWT（jjwt 0.13.x）；除了登入與健康檢查，其餘 API 都要帶 Bearer token，無狀態；401／403 回 ProblemDetail
- POST /api/auth/login 回傳 token 與使用者資訊；token 帶角色、8 小時到期，密鑰讀環境變數
- 用 Flyway 建 app_users 與四個示範帳號：兩位業務 sales 與 sales2、一位主管 manager（兩位業務都歸他管）、一位管理員 admin，密碼都是 password123、用 BCrypt；要記得住「誰是誰的主管」
- 客戶與生意機會原本只記了「負責業務的名字」，請改成真正對應到帳號，並把既有的示範資料掛好：亞太智能製造、環球零售巨擘歸 sales，鼎峰金融科技和那家資料不足的新客戶歸 sales2；查出來仍要看得到負責業務的名字。新增或編輯客戶、生意機會時可以指定負責業務，不指定就是目前登入的人
- 登入後我要能查「我是誰、我的角色、我的主管是誰」；主管和管理員還要能列出所有使用者和他們的角色，這樣指派客戶或測試時才找得到人的編號
- 角色規則照我填的權限表：標「❌」的擋在 SecurityConfig，標「🔸」的在資料層用 Specification 自動補 owner_id
請加繁體中文註解。完成後我要能驗證：sales 只看得到自己的客戶，查 sales2 的客戶回 404；manager 看得到兩位業務的客戶；sales 刪客戶被 403 擋下，換 admin 才刪得掉。
```

**Swagger 網頁驗證步驟（推薦）**：

1. 開啟 `http://localhost:8080/swagger-ui/index.html`。
2. 安全登入（HTTP Basic）：瀏覽器彈出登入對話框，輸入管理員帳密（帳號 `admin`、密碼 `password123`）。
3. 取得 JWT：展開 `POST /api/auth/login`，Try it out 傳入 `{"username": "sales", "password": "password123"}`，複製回傳的 token。
4. 點頁面上方 **Authorize** 按鈕，在 `BearerAuth` 欄位貼上 JWT 並啟用。
5. 驗證 RBAC：以 `sales`（SALES 角色）授權狀態呼叫刪除客戶 API，預期 **403 Forbidden**；換成 `admin` 的 Token 重呼叫，應成功回傳 **204 No Content**。

## 逐步操作與驗收

### 驗證身份與保護請求

1. 先畫出 login、簽發 token、帶 Bearer token 呼叫 API、過期與登出的流程；標出 token 由誰簽發、在哪裡驗證、哪些 endpoint 公開。
2. 設定 SecurityFilterChain 與 JWT decoder/validator，明確列出 public path；用一個公開 endpoint 確認未登入可用，再用受保護 endpoint 確認未帶 token 回 401。
3. 取得測試 token 後用 `Invoke-RestMethod -Headers @{ Authorization = "Bearer ..." }` 呼叫 API，依序測試有效 token、錯誤 token、過期 token 和有效身份但不足權限。
4. 檢查 token claims、issuer、audience、expiry 與角色 mapping；不要只 decode base64 就把 token 當成已驗證。

### 預期結果與證據

- 公開路由、有效 token、401 與 403 行為清楚可重現；錯誤 response 不洩漏簽章或內部驗證細節。
- 交付 auth flow 圖、Security 設定、四組 HTTP 結果、測試 token 的非敏感 claims 與 expiry 記錄。

### 失敗分流與銜接

- 所有請求都 401 時先查 filter、issuer、clock 與 header 格式；所有請求都通過時查 public matcher 是否過寬。
- 下一單元會把角色權限落到 CRM 資源，先保存身份驗證已成功的 endpoint 證據。

## 口語稿

好，來到這一章最重要的一節。我先問你一個問題：現在你的客戶 API，如果我拿到網址，我能不能把你資料庫裡的客戶全部刪光？答案是可以，而且不需要任何身分。這就是 demo 跟正式系統最赤裸裸的差距——正式系統的第一條防線就是：沒登入的人，什麼都不能做。

要搞懂 API 安全，先分清楚兩個詞。Authentication，認證，回答的是「你是誰」——你用帳號密碼登入，系統確認你的身分。Authorization，授權，回答的是「你能做什麼」——同樣是登入的人，管理員能刪客戶，業務人員只能查詢和編輯。這兩個詞整章會一直出現，認證是門禁，授權是門禁後面的房間鑰匙。

那身分要怎麼在每次請求之間傳遞？傳統做法是 Session，伺服器記住每個登入的人。但我們要做的是無狀態認證，用的是 JWT。JWT 長什麼樣子？三段字串用點連起來。第一段 Header，宣告簽章演算法；第二段 Payload，放你的帳號、角色、過期時間；第三段 Signature，是伺服器用密鑰對前兩段做的簽章。關鍵就在這個簽章：任何人只要改動前兩段的任何一個字，簽章就對不起來，伺服器立刻知道這是假的。所以伺服器收到 Token，只要驗簽章、看沒過期，就能信任裡面寫的身分和角色，完全不用查資料庫、不用記 Session。這就是「無狀態」，好處是以後開十台伺服器也不用共享 Session。但有兩件事你一定要記住：第一，Payload 只是 Base64 編碼、不是加密，任何人都解得開來看，所以絕對不要把密碼放進去；第二，密鑰要從環境變數讀，不要寫死在程式碼裡。

概念懂了，實作怎麼做？這一段我們交給 AI，但是——這正是這門課一直強調的——你要先有心智模型，才有能力核對 AI 的產物。Spring Security 加 JWT 總共就五個零件。第一，JwtUtils，用 jjwt 套件負責簽發和解析 Token。第二，登入端點，POST /api/auth/login，收帳密、驗證成功就把角色寫進 Payload 簽發 Token，這是整條認證鏈唯一不用帶 Token 的入口。第三，JWT 驗證過濾器，攔截每一個進來的請求，從 Authorization: Bearer 標頭取出 Token 驗章，成功就把身分放進 SecurityContext。第四，SecurityFilterChain，集中設定規則：關掉 Session 改成 STATELESS、放行登入和 Swagger、其他一律要驗證，然後把過濾器掛進鏈裡。第五，角色授權，刪除客戶必須 ADMIN，可以用 @PreAuthorize 或在設定鏈裡依路由限制。這五個零件記熟，AI 少做哪個你一眼就看得出來：少了過濾器，你帶了 Token 還是被擋；少了無狀態設定，系統會莫名其妙長出 Session；少了角色限制，一般使用者也刪得掉資料。

我們現在來實際跑一次。把課程提供的提示詞丟給 AI Agent，它會引入 Security 和 jjwt 依賴、生出這五個零件。跑完之後重啟應用，來驗證——這是本節最過癮的部分。先不帶 Token 呼叫客戶查詢 API，你會看到 401，被擋在門外了，這是好事。接著打開 Swagger UI，因為文件也被保護了，瀏覽器會先跳出登入框，輸入 admin 和 password 進去。然後展開 /api/auth/login，用 user 帳號執行，把回傳的 token 複製起來，點頁面最上方的 Authorize 按鈕，貼進 BearerAuth 欄位。現在你是「一般使用者」的身分了——查詢客戶，成功；試著刪除客戶，你會看到 403 Forbidden，被角色權限擋下來了。最後換成 admin 的 Token 重新 Authorize，再刪一次——204 No Content，刪掉了。401、403、204，這三個狀態碼跑一輪，你的認證和授權就都驗證完了。

總結：這一節我們用五個零件把 API 從「裸奔」變成「有門禁、有房間鑰匙」的受保護系統，而且全程在 Swagger 上視覺化驗證。不過 ADMIN 和 USER 兩種角色對真實的 CRM 來說還太粗糙——業務看得到誰的客戶？主管看得到什麼報表？下一節我們就來設計 AI CRM 真正的權限模型。
