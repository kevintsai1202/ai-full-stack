# 課程實作提示詞 × ai-crm 專案逐條比對與修改計畫

日期：2026-09-08
比對對象：`teaching-site/course-data.js` Unit 1–8 的 37 段口語化提示詞、各單元概念段內的 11 段「AI Agent 提示詞」、`course-package/ch01–ch08` 的作業驗收標準，對照 `D:\GitHub\ai-crm` 目前的程式碼（非文件）。
文件狀態：比對完成，決議見第 4a 節，修改已於 2026-09-08 執行完畢（第 8 節）。第 2、3 節的「修改重點」欄是比對當時的建議，實際採用以第 4a 節決議為準。
前一份策略層分析：[teaching-site-ai-crm-prompt-gap-analysis.md](teaching-site-ai-crm-prompt-gap-analysis.md)（2026-08-24）。那份說「方向對、契約不夠」，本文件把「契約」逐條落到每一段提示詞。

---

## 0. 結論

1. 37 段口語化提示詞的「功能方向」都對得上 ai-crm，沒有一段要砍。問題出在**每一段都沒有給 AI 專案基線**（版本、port、命名、角色、錯誤格式、AI 架構），所以 AI 產出的東西「功能像、長相不像」，學員做到第六章就跟 ai-crm 對不起來。
2. 需要改寫的程度（第 3 節共 52 列，含技術提示詞與作業）：🔴 衝突 28 列、🟡 缺契約 12 列、🟢 只需補基線 12 列。
3. 有 **6 個技術路線差異**不是改字就能解，要你先決定（第 4 節）。最關鍵的兩個：第六章「AI 查真實資料」課程教 Tool Calling、ai-crm 用 Java 先算 grounding；第六章 SSE 認證課程教 EventSource 帶 query token、ai-crm 用 fetch 帶 Authorization header。
4. 修改範圍：`course-data.js`（提示詞 47 段＋概念段內的舊 port／指令）→ 重跑匯出 → `course-package` 8 章內容檔、8 份作業、5 份旁白稿。詳見第 6 節。

---

## 1. 範圍與等級定義

| 來源 | 內容 | 數量 |
|---|---|---|
| `teaching-site/course-data.js` `prompts[]` | 口語化 build／verify／fix 提示詞（學員主要複製的東西） | u1 4、u2 3、u3 4、u4 3、u5 6、u6 7、u7 7、u8 3 ＝ 37 |
| `course-data.js` 概念段 `body` 內 | 技術版「AI Agent 提示詞」（給看得懂術語的學員） | u2 1、u3 3、u4 1、u5 1、u6 3、u7 2 ＝ 11 |
| `course-package/ch0N/assignment-1.md` | 作業驗收標準（提示詞的驗收對象） | 7 |
| ai-crm | `backend/pom.xml`、`application*.yml`、`docker-compose.yml`、`db/migration/V1–V27`、`api/*Controller`、`api/Dtos.java`、`security/*`、`service/*`、`frontend/src/**`、`scripts/*.ps1` | 以程式碼為準，`docs/spec.md` 仍寫 Spring Boot 3.5 屬過期文字 |

等級：🔴 提示詞現在的寫法會讓 AI 做出**與 ai-crm 相反或不相容**的東西（命名、協定、架構）；🟡 方向對但**缺契約**，AI 會自由發揮，產出跟 ai-crm 長得不一樣；🟢 內容可用，只需在前面補「專案基線」段。

---

## 2. 全域差異：每一段 build 提示詞都要帶的「專案基線」

這些值目前散落在提示詞裡（8080、5432、learn_spring、npm、USER/ADMIN…），而且每一處都跟 ai-crm 不同。建議做成一段固定的「專案基線」文字，放在每個單元 `prompt` 導言之後、第一段 build 提示詞之前，學員只複製一次；每段提示詞開頭再用一句「沿用專案基線」帶過。

| 項目 | 課程提示詞現況 | ai-crm 實況（證據） | 等級 | 提示詞處理 |
|---|---|---|---|---|
| 後端 port | `localhost:8080`（u2 技術提示詞、u4 Swagger 步驟、u5 proxy） | `server.port: 18080`（`application.yml`） | 🔴 | 全部改 18080；`course-data.js` 概念段另有 14 處、`course-package` 28 處、旁白稿 5 份要連帶改 |
| 資料庫 | `pgvector/pgvector:pg18`、`learn_spring`、`postgres/password`、5432 | `pgvector/pgvector:pg16`、db／user／password 皆 `aicrm`、`15432:5432`（`docker-compose.yml`） | 🔴 | 改成 ai-crm 值；pg18 → pg16（ai-crm 測試用 Testcontainers 也綁 pg16） |
| Java／Spring | 只說 JDK 21、Spring Boot（無版本） | Java 21、Spring Boot 4.1.0、Spring AI 2.0.0（bom）、springdoc 2.8.9、Jackson 3 `tools.jackson`（`backend/pom.xml`） | 🟡 | 基線寫死版本；提醒 Boot 4 的 Jackson 3 與 `spring-boot-starter-flyway` |
| 專案結構 | u1「一個放後端、一個放網頁」；作業 1 要求 Group `com.example`、Artifact `tutorial` | 根 `pom.xml` 為 aggregator `com.aicrm:ai-crm-parent`，module `backend`；`frontend/`、`docs/`、`scripts/`、`e2e/`；主類別 `com.aicrm.AiCrmApplication`，套件 `com.aicrm.crm.{api,domain,repository,security,service,config,bootstrap}` | 🔴 | 作業 1 改成 `com.aicrm` / `ai-crm-parent` + `backend`；提示詞指定套件分層 |
| 前端工具鏈 | `npx create-vite --template react`、`npm install`、`vite.config.js`、`.jsx` | pnpm（`pnpm-lock.yaml`、`pnpm-workspace.yaml`）、React 19 + **TypeScript** + Vite 7、`vite.config.ts`、react-router-dom v7、axios（`frontend/package.json`） | 🔴 | 改 `--template react-ts`、pnpm、`.tsx`；u1 安裝提示詞加 pnpm（corepack） |
| 角色 | 「一般使用者 USER」與「管理員 ADMIN」 | `Role`：SALES、MANAGER、ADMIN；帳號 `sales@aurora.local`、`manager@aurora.local`、`admin@aurora.local`，密碼 `password123`，由 `AiCrmApplication.seedUsers` 建立 | 🔴 | u4 起全部改三角色＋三帳號；u7 團隊分析的「主管」才有依據 |
| 錯誤格式 | 「格式一致、看得懂」、作業要求 `errors` 陣列 | RFC 7807 `ProblemDetail`（`type/title/status/detail/instance`），驗證錯誤把 fieldErrors 串進 `detail`（`GlobalExceptionHandler`） | 🟡 | 明寫 ProblemDetail；作業 4 驗收改成看 `detail` 而非 `errors[]` |
| DTO 與分層 | 未規定 | 所有 request/response record 集中 `api/Dtos.java`，不暴露 Entity；分頁回 `PageResponse(items,page,size,totalElements,totalPages)`，欄位叫 `items` 不是 `content`（`Dtos.java:70`） | 🟡 | u2 ②、u3 ③ 寫進去 |
| AI 供應商 | Groq base-url、`openai/gpt-oss-120b`、`GROQ_API_KEY` | OpenAI 相容端點：`OPENAI_API_KEY`、`BASE_URL`（須含 `/v1`）、`OPENAI_CHAT_MODEL`（預設 gpt-4o-mini）、`max-completion-tokens`；無金鑰走 deterministic fallback（`application.yml`、`InsightService`） | 🔴 | 改成三個環境變數＋fallback；Groq 可當 BASE_URL 的一種例子（Groq 本身就是 OpenAI 相容） |
| 向量 | `spring-ai-starter-vector-store-pgvector`、`VOYAGE_API_KEY` | Voyage `voyage-4-lite` 1024 維，自製 `VoyageEmbeddingClient` + `DeterministicEmbedding` fallback；向量欄位不映進 JPA，用 JdbcTemplate（`KnowledgeVectorRepository`） | 🔴 | 見決策 D4-b |
| 秘密管理 | 「金鑰放設定裡讀取」 | `spring.config.import: optional:file:.env[.properties]`，`.env` 不入版控；JWT secret `APP_SECURITY_JWT_SECRET` ≥ 32 字元，空值或等於歷史外洩值即拒絕啟動（`JwtService`） | 🟡 | u4、u6 提示詞明寫 `.env` 與 fail-fast 規則 |
| 註解語言 | 「請加中文註解」 | 函式級與重要變數註解用繁體中文（前後端 CLAUDE.md） | 🟢 | 統一措辭「函式級別繁體中文註解」 |
| 種子客戶 | 課程情境三家：亞太智能製造 (APIM)、環球零售巨擘 (GlobalMart)、鼎峰金融科技 (ApexFin)；u6 驗證提示詞點名「亞太智能製造」 | `V2` 四家：星河製造（高價值活躍）、海岳物流（流失風險）、晨曦醫材（續約延遲）、空白測試客戶（資料不足）；負責業務 林宜庭、陳柏翰 | 🔴 | 見決策 D3 |

---

## 3. 逐章比對

欄位說明：「現況問題」寫這段提示詞交給 AI 後最可能長歪的地方；「ai-crm 對應」寫專案實際怎麼做（含檔案）；「修改重點」是改寫時要塞進提示詞的契約，確認後我照這欄改。

### 3.1 Unit 1｜環境與骨架

| 提示詞 | 現況問題 | ai-crm 對應 | 等級 | 修改重點 |
|---|---|---|---|---|
| ① 用 AI 把開發環境準備好 | 只裝 Java／建置工具／Git；沒有 pnpm、Docker Desktop、PowerShell 7 | `check-env.ps1` 檢查 JDK 21、Maven、Node、pnpm、Docker；`start-crm.ps1` 用 `docker-compose` + `mvn -pl backend` + `pnpm --dir frontend` | 🟡 | 補 Node LTS、pnpm、Docker Desktop、PowerShell 7；要求產出一支 `check-env.ps1` 逐項驗證版本 |
| ② 建立專案的資料夾骨架 | 「一個放後端、一個放網頁」，AI 會建單模組 Spring 專案 | 根 pom aggregator + `backend` module；`frontend/`；`docs/`、`scripts/`；`.gitignore` 含 `.env`、`target/`、`node_modules/` | 🔴 | 指定 monorepo 結構、`com.aicrm`、Spring Boot 4.1.x parent、Java 21、port 18080、`/api/health` 回 `status`；`.env.example` |
| ✅ 驗證 | 只驗工具版本＋後端能啟動 | `GET /api/health` 公開端點（`HealthController`） | 🟢 | 加「`Invoke-RestMethod http://127.0.0.1:18080/api/health` 回 UP」 |
| 🔧 排錯 | 通用 | — | 🟢 | 不改 |
| 作業 1 驗收 | Group `com.example`／Artifact `tutorial`／依賴 Web、JPA、PostgreSQL、Flyway | `com.aicrm:ai-crm-parent`、module `backend`；依賴另含 validation、security（第四章才加） | 🔴 | 改 Group／Artifact／結構；依賴保留四個，其餘留到對應章節 |

### 3.2 Unit 2｜Spring MVC、REST 與 CRM Domain

| 提示詞 | 現況問題 | ai-crm 對應 | 等級 | 修改重點 |
|---|---|---|---|---|
| ① 暖身：簡單客戶功能（記憶體） | 拋棄式練習，可保留；但欄位沒指定，AI 會用 name／email／level／status="Active" | `CreateCustomerRequest`：name、email、phone（`^09\d{8}$`）、taxId（8 碼）、industry、ownerId、contractStartDate／EndDate／renewalDueDate；`CustomerStatus` ACTIVE／INACTIVE／LEVERAGED | 🟡 | 暖身就用 ai-crm 的客戶欄位與 enum，避免第三章重做；回應用 record DTO |
| ② 擴充成完整 CRM 資料 | 「欄位照一般 CRM 常識」→ AI 自由設計，第三章 Flyway 會跟 V1 對不上 | V1 六張表：`app_users`、`customers`、`contacts`、`interactions`、`opportunities`、`knowledge_documents`；enum `InteractionType` PHONE／MEETING／EMAIL／SUPPORT_TICKET、`OpportunityStage` QUALIFICATION／PROPOSAL／NEGOTIATION／CLOSED_WON／CLOSED_LOST、`OpportunityType` NEW_BUSINESS／RENEWAL；Customer 對三者 `@OneToMany` cascade | 🔴 | 直接給四張表的欄位與 enum 值；DTO 集中 `Dtos.java`；`CustomerDetailResponse` 一次帶 contacts／interactions／opportunities |
| ✅ 驗證 | 「整理成可重跑腳本」 | `scripts/test-crm-api.ps1`（API 煙霧測試） | 🟢 | 指定腳本名與位置 `scripts/test-crm-api.ps1`、PowerShell 7、`Invoke-RestMethod` |
| 技術提示詞：建立 Spring MVC 示範專案 | `port 8080`、只有 GET／GET id／POST | `CustomerController`：GET 分頁、GET `/options`、GET `/{id}`、POST、PUT `/{id}`、DELETE `/{id}`、POST `/{id}/contacts`、PUT `/{id}/status`、POST `/{id}/interactions` | 🟡 | port 改 18080；端點清單對齊（暖身可只做前四個，但路徑與狀態碼照 ai-crm） |
| 作業 2 驗收 | 只依賴 starter-web、三端點 | 同上 | 🟢 | 補「欄位與 enum 依第二章 ② 契約」 |

### 3.3 Unit 3｜PostgreSQL、Flyway、JPA、動態查詢

| 提示詞 | 現況問題 | ai-crm 對應 | 等級 | 修改重點 |
|---|---|---|---|---|
| ① 準備正式資料庫 | 沒給映像、port、db 名 | `docker-compose.yml`：`pgvector/pgvector:pg16`、`15432:5432`、`aicrm/aicrm/aicrm`、named volume（MinIO 服務本章不需要） | 🔴 | 寫死映像、port、帳密、volume；「不要用 5432，避免與本機 PostgreSQL 衝突」 |
| ② 版本管理建表＋示範資料 | 表名、欄位、種子沒指定 | `V1__init_schema.sql`（六表＋四索引，含 `created_at/updated_at/created_by/updated_by` 稽核欄）、`V2__insert_seed_data.sql`（四家客戶、三聯絡人、九互動、三商機、三知識文件） | 🔴 | 指定 V1／V2 檔名、表名、稽核欄、四家客戶名稱與情境（見 D3）、interactions 的日期要能讓風險等級算出高／中／低 |
| ③ 存進資料庫＋多條件搜尋 | 「產業加分級加關鍵字」，分級（level）在 ai-crm 不存在 | `JpaSpecificationExecutor` + `CustomerService.buildSpec`；參數 `page`（0）、`size`（10）、`keyword`、`industry`、`owner`、`status`、`riskLevel`、`renewalFrom`、`renewalTo`；回 `PageResponse(items,…)` | 🔴 | 改成 ai-crm 的查詢參數與分頁 DTO；用 Specification 不用 QueryDSL |
| ✅ 驗證 | 通用 | `ddl-auto: validate`、`open-in-view: false` | 🟢 | 加「重啟 log 不出現 Flyway 重複套用、validate 通過」 |
| 技術提示詞：docker-compose | pg18、learn_spring、5432 | 同 ① | 🔴 | 同 ① |
| 技術提示詞：application.yml | `localhost:5432/learn_spring`、`baseline-on-migrate` | `127.0.0.1:15432/aicrm`、`server.port 18080`、`flyway.enabled`、`ddl-auto validate`、`open-in-view false`、`spring.config.import optional:file:.env` | 🔴 | 改值；加 `.env` 匯入與 `open-in-view: false` |
| 技術提示詞：加入 JPA | Lombok `@Data`、`getAll/findById/save` | ai-crm **不用 Lombok**（pom 無）；`AuditableEntity` + `AuditingEntityListener`；`CustomerMapper` 轉 DTO | 🔴 | 拿掉 Lombok；加 `AuditableEntity` 與 `@EnableJpaAuditing`；Service 回 DTO |
| 作業 3 | u3-t4 寫「更新 Product.java 為 Entity」是別課殘留 | — | 🔴 | 改 `Customer.java`；驗收加 `PageResponse` 形狀 |

### 3.4 Unit 4｜Security、JWT、OpenAPI、錯誤處理

| 提示詞 | 現況問題 | ai-crm 對應 | 等級 | 修改重點 |
|---|---|---|---|---|
| ① 加上登入與權限控管 | 兩角色；「只有管理員可以刪除」 | 三角色；規則集中 `SecurityConfig`（無 `@PreAuthorize`）：DELETE `/api/customers/**` ADMIN、`/api/manager/**` MANAGER｜ADMIN、`/api/admin/**`、`/api/dev/**` ADMIN、其餘 authenticated；`POST /api/auth/login` 回 `LoginResponse(token, user{id,username,displayName,role})`；BCrypt；JWT 手刻 HMAC-SHA256，claims sub/uid/name/role/iat/exp，TTL 28800 秒；secret fail-fast | 🔴 | 三角色三帳號；規則寫進提示詞；`LoginResponse` 形狀；`APP_SECURITY_JWT_SECRET` 規則；是否用 jjwt 見 D6 |
| ② 線上操作說明頁＋統一錯誤 | 「說明頁要登入後才能用」 | `/swagger-ui/**`、`/v3/api-docs/**` **permitAll**，`OpenApiConfig` 宣告 `bearerAuth` scheme，Try it out 才需 token；錯誤走 `ProblemDetail` | 🔴 | 見 D7；錯誤格式明寫 ProblemDetail 與對應表（400／401／403／404／409） |
| ✅ 驗證權限流程 | 「一般使用者刪客戶被擋」 | `sales@aurora.local` 刪客戶 403、`admin@aurora.local` 204；未帶 token 401 為 JSON `{title,status,detail,instance}` | 🟢 | 換帳號名；加 401 JSON 形狀 |
| 技術提示詞：Security 與 JWT | jjwt 0.12.5、USER／ADMIN、Swagger 受保護 | 見 ① ② | 🔴 | 同 ① ② |
| 作業 4 | u4-t7 `@Slf4j`（Lombok）、u4-t8 Actuator `PATCH /actuator/loggers`、驗收 `errors` 陣列、`user`／`admin` 帳號 | 無 Lombok、無 Actuator、無 AOP、無 request logging | 🔴 | `@Slf4j` 改 `LoggerFactory`；Actuator 見 D8；`errors` 改 `detail`；帳號改三帳號 |

### 3.5 Unit 5｜React CRM 工作台

| 提示詞 | 現況問題 | ai-crm 對應 | 等級 | 修改重點 |
|---|---|---|---|---|
| ① 外觀骨架 | 「漸層標題列、卡片、過場動畫」，沒指定路由與殼層 | `AppShell`（側邊欄＋`<Outlet/>`、健康燈、使用者卡、登出）、`ProtectedRoute`／`ManagerRoute`／`AdminRoute`；純手寫 `styles.css` + `skeleton.css`，無 Tailwind | 🟡 | 指定 react-ts、pnpm、路由殼層三件套、純 CSS、骨架屏 |
| ② 接上後端並記住登入 | 沒說 token 存哪、怎麼帶 | axios `baseURL = VITE_API_BASE_URL || "/api"`、request 攔截器加 Bearer、401 派發 `auth:logout`；token 存 `sessionStorage` key `ai-crm-token`；`AuthContext` | 🔴 | 指定 axios 攔截器、sessionStorage（不是 localStorage）、`AuthContext`、401 導回 `/login` |
| ③ 核心頁面接真實資料 | 頁面清單對，但路由與資料來源沒定 | `/login`、`/dashboard`、`/customers`、`/customers/:id`（選取客戶以 URL 為單一真實來源）；`GET /api/customers` 分頁、`GET /api/customers/{id}` 回 `CustomerDetailResponse`；看板拖拽 `PUT /api/opportunities/{id}/stage` 樂觀更新＋失敗 rollback | 🟡 | 寫入路由表、API 對應、看板拖拽契約、loading／error／empty 三態 |
| ④ 儀表板圖表 | 七張圖名稱與 ai-crm **完全一致** | `ReportsSection` 內 `PipelineFunnel`、`MonthlyForecastChart`、`IndustryBreakdown`、`RiskBreakdown`、`RenewalForecast`、`OwnerLeaderboard`、`ActivityReportList`；`GET /api/dashboard/summary`、`/reports`、`/drilldown`；Caffeine 2 分鐘快取 | 🟢 | 補端點與元件名；下鑽用 `DrilldownModal` |
| ⑤ 優先追哪些客戶（分群） | 三面向即 RFM，方向正確；但標籤名「重點客戶／有潛力／需要喚醒」與 ai-crm 不同 | `RfmService.decideSegment`：冠軍客戶／瀕危流失／忠誠客戶／具潛力／需關注（R／F／M 各 1–5 分，≥4 為高）；`GET /api/dashboard/rfm` | 🟡 | 點名 RFM、端點與五個標籤名 |
| ✅ 驗證 | 通用 | Vitest（jsdom）、Playwright `frontend/e2e/*.spec.ts` | 🟡 | 要求寫成 Playwright 腳本（登入→客戶→詳情→登出），不是口頭確認 |
| 技術提示詞：建立 React 專案 | `--template react`、npm、`vite.config.js`、proxy 8080、聊天視窗占位 | `--template react-ts`、pnpm、`vite.config.ts`、proxy `127.0.0.1:18080`、`host 127.0.0.1` | 🔴 | 全部改 |
| 作業 5 | npm、`vite.config.js`、8080、Axios interceptor（這點對） | 同上 | 🔴 | 同上；驗收加 sessionStorage 與 401 分流 |

### 3.6 Unit 6｜Spring AI、SSE、AI 查真實資料

| 提示詞 | 現況問題 | ai-crm 對應 | 等級 | 修改重點 |
|---|---|---|---|---|
| ① 會聊天的 AI 助手 | 「不同使用者的對話分開」→ AI 會做 sessionId；金鑰供應商沒定 | `POST /api/ai/chat`（同路徑 `produces` 分 JSON／SSE），body `ChatRequest(customerId, message, lang)`；記憶 `ChatMemoryService` 以 **customerId** 為鍵存 `chat_messages`（近 6 則＋語意 3 則）；`ChatClient.create(chatModel)`，`ChatModel` 用 `ObjectProvider` 取，無金鑰 → deterministic fallback；`AiGovernanceService` 寫 `ai_call_log`；`PiiMasker` 遮 email／電話／統編 | 🔴 | 見 D4-a（記憶鍵）；供應商改三環境變數；fallback、call log、PII 遮罩三件事寫進去 |
| ② 回答前先查真實資料 | 方向對，作法未定；技術提示詞教 `@Tool` | **無 `@Tool`**；`AiGroundingService` 由 Java 先撈客戶／互動／商機／風險／引用組成 grounding context，模型只組織語言；系統提示詞「僅根據提供的客戶資料、風險評分、知識庫引用回答，不得編造價格、折扣」 | 🔴 | 見 D4（tool calling vs grounding） |
| ③ 網頁 AI 聊天室 | 「只有登入的人能用」，技術提示詞用 EventSource + query token | 前端 `askAssistantStream` 用 `fetch` + `ReadableStream`，`Accept: text/event-stream`，Authorization header；chunk `{"type":"content"|"citations"|"risk"|"callId"}`，結尾 `[DONE]`；聊天視窗掛在客戶詳情頁（`features/ai-assistant`） | 🔴 | 見 D5（SSE 認證方式）；事件格式寫死 |
| ④ 客戶 AI 評估報告 | 內容四段與 ai-crm **一致** | `GET /api/ai/customers/{id}/assessment`（JSON／SSE），`ASSESSMENT_INSTRUCTION` 輸出 Markdown 四段：健康度總評／Pipeline 分析／風險與預警／下一步建議；`ReportModal` 呈現；`AiBadge` 標示真實模型或 fallback | 🟢 | 補端點、四段標題、fallback 標示 |
| ⑤ 風險等級 | 「判斷依據像是…」讓 AI 自訂規則 | `RiskLevelCalculator`（純規則，回字串）：無互動 MEDIUM；距上次互動 > 60 天或續約逾期 HIGH；> 30 天 MEDIUM；否則 LOW；`customers.risk_level`＋`risk_computed_at`（V13）；每日重算 `RiskLevelScheduler`；清單可用 `riskLevel` 篩選 | 🔴 | 規則寫死（數字由程式算，AI 只解釋）；存欄位、排程、篩選參數 |
| ⑥ 情緒與意圖 | 意圖例子與 ai-crm 幾乎一致 | `interaction_insights`（V8）：`Sentiment` POSITIVE／NEUTRAL／NEGATIVE＋分數、`Intent` ASK_PRICING／COMPARE_COMPETITOR／CHURN_SIGNAL／RENEWAL_INTEREST／UPSELL_SIGNAL／COMPLAINT／OTHER；`SentimentIntentService`（LLM）+ `SentimentService`（規則彙整）；`GET /api/dashboard/sentiment` | 🟡 | enum 值、表名、儀表板端點寫進去 |
| ✅ 驗證 | 點名「亞太智能製造」 | 種子是星河製造等四家 | 🔴 | 見 D3；加「問一個不存在的客戶要回查無資料」（作業已有，提示詞沒有） |
| 技術提示詞 ×3（ChatClient／Tool／SSE） | Groq、sessionId、`CustomerTools`/`OpportunityTools`、`parseJwt` 改 query token、`ChatRoom.jsx`、`/api/ai/stream` | 見上 | 🔴 | 依 D4／D5 重寫 |
| 作業 6 | u6-t1 Groq、u6-t3 Principal 隔離、u6-t5 ToolCallback、u6-t8 query token、u6-t9 EventSource | 見上 | 🔴 | 任務標籤連動改 |

### 3.7 Unit 7｜團隊分析、工作台、RAG、長期記憶

| 提示詞 | 現況問題 | ai-crm 對應 | 等級 | 修改重點 |
|---|---|---|---|---|
| ① 團隊分析＋AI 診斷 | 方向一致 | `GET /api/manager/analytics`（`ManagerAnalyticsService`）、`/api/manager/insights/team`、`/owner`（JSON／SSE，`ManagerInsightService`，結果快取 `manager_insight` V14）；`/team` 路由 `ManagerRoute`；`AiCallType` TEAM_ANALYSIS／OWNER_COACHING | 🟢 | 補端點、路由守衛、快取表 |
| ② 我的工作台 | 方向一致 | `/api/workspace/recommendation`（GET／POST）、`/chat`、`/history`（`WorkspaceAiService`）；建議與正式 task 分離；前端 `/my-work` 目前 redirect 到 `/customers`（`features/my-workspace` 存在） | 🟡 | 指定端點；「建議只是建議，狀態以 `/api/tasks` 為準」 |
| ③ 全公司評估＋AI 紀錄 | 方向一致 | `GET /api/ai/portfolio/assessment`（JSON／SSE）、`/api/ai/portfolio/calls`、`/api/ai/customers/{id}/calls`、`/api/ai/usage`（MANAGER｜ADMIN）；`ai_call_log`、`ai_feedback`（ADOPTED／REJECTED）、`AiCallHistoryModal` | 🟢 | 補端點與 feedback |
| ④ RAG 知識庫（上傳文件） | 「我可以上傳文件」 | **沒有上傳端點**；知識來自 `knowledge_documents` 資料列（seed）；`KnowledgeIndexer` 啟動時補 embedding／chunk，`POST /api/ai/knowledge/reindex`（ADMIN）；`TextChunker` 600／80；`RagCitationService` 回 `CitationResponse(title, docType, content, similarity)`；SSE `type:"citations"` | 🔴 | 見 D9（是否新增上傳） |
| ⑤ 長期記憶 | 技術提示詞用 `ChatHistoryService`+`VectorStore`+`doOnComplete`+雙路檢索 | `chat_messages.embedding`（V7）、`ChatMessageVectorRepository.searchTopK`，`recall()` 近期 6＋語意 3，單則截 200 字；寫入在 `ChatMemoryService` 同步完成，非 Reactor | 🔴 | 改成 ai-crm 的 chat_messages 語意召回；「不拖慢回覆」改為要求量測回覆時間，不綁 `CompletableFuture` |
| ⑥（選修）外部工具／MCP | ai-crm 無 MCP | 無 | 🟢 | 保留選修，加一句「ai-crm 主線不含 MCP，另開分支做」 |
| ✅ 驗證 | 通用 | — | 🟡 | 加「問知識庫沒有的問題要說找不到」 |
| 技術提示詞 ×2（RAG／長期記憶） | `spring-ai-starter-vector-store-pgvector`、`TextReader`、`TokenTextSplitter`、`QuestionAnswerAdvisor`、`/api/rag/*` | 見 ④ ⑤ | 🔴 | 依 D4-b／D9 重寫 |
| 作業 7 | u7-t6「Spring AI M8 升 RC1」過期；u7-t7～t9 綁 `doOnComplete` | Spring AI 2.0.0 正式版 | 🔴 | 刪 M8 敘述；t7–t9 改成 chat_messages 向量召回 |

### 3.8 Unit 8｜整合與 Demo Day

| 提示詞 | 現況問題 | ai-crm 對應 | 等級 | 修改重點 |
|---|---|---|---|---|
| ① 整合＋三種典型客戶測試 | 三種客戶對應 V2 種子 **剛好一對一**（星河製造／空白測試客戶／海岳物流） | `start-crm.ps1`、`stop-crm.ps1`；Playwright `frontend/e2e/sp1-smoke.spec.ts` 等；後端 Testcontainers `PostgresTestBase` | 🟡 | 點名種子客戶；要求產出 `start-crm.ps1`／`stop-crm.ps1`、Playwright 三情境 spec、後端整合測試 |
| ② 上線檢查＋展示腳本 | 通用 | `RateLimitFilter`（429）、`prod` profile 關 `demo.reset-enabled`、JWT fail-fast、CORS 白名單、`/api/health` 的 `ai` 欄位回報模式 | 🟡 | 檢查清單列出這五項 |
| ✅ 驗證 | 「全程沒有錯誤」 | `scripts/verify-phase-gate.ps1` | 🟡 | 要求留下證據：Playwright report、API 腳本輸出、截圖 |

---

## 4. 需要你決定的事

每項附建議；沒回覆就照建議做。

| # | 問題 | 選項 | 建議與理由 |
|---|---|---|---|
| D1 | 對齊到哪一層 | A. 只對齊 ai-crm 核心（客戶四表、三角色、儀表板、AI 對話／評估／風險／情緒、RAG、團隊／工作台／全公司）。B. 連 V21–V27（模型治理、任務、名片 OCR、會議、跟進信、商機健康、決策鏈）也納入 | **A**。V21–V27 不是 Unit 1–8 的自然結果，前一份分析已建議另開「相容擴充」單元 |
| D2 | port 與環境值全面改成 ai-crm（18080／15432／aicrm／pnpm） | 改／不改 | **改**。`start-crm.ps1`、`test-crm-api.ps1`、Playwright、Testcontainers 都綁死這些值；不改的話學員做出來的東西跑不了 ai-crm 的任何腳本。代價：概念段與旁白稿 47 處字串連動 |
| D3 | 種子客戶名稱 | A. 提示詞改用 ai-crm V2 的四家（星河製造、海岳物流、晨曦醫材、空白測試客戶），課程情境頁加一張「APIM＝星河製造…」對照表。B. 改 ai-crm 的 V2 種子成 APIM／GlobalMart／ApexFin（要動 ai-crm 與 74 個測試） | **A**。種子在 migration 裡，AI 照提示詞產出才會跟 ai-crm 一模一樣；情境故事不用改，只補對照 |
| D4-a | 第六章「AI 查真實資料」 | A. 改成 ai-crm 的 grounding-first（Java 先撈資料組 context，模型只組織語言），tool calling 留在概念段講並說明 ai-crm 為何不用。B. 保留 `@Tool` 提示詞，接受與 ai-crm 不同。C. grounding 為主、`@Tool` 讀取工具為輔 | **A**。README 把 grounding 當 anti-hallucination boundary，是專案的核心賣點；C 會讓 AI 做出兩套查資料路徑。單元標題「tool calling」可保留（概念仍教） |
| D4-b | 第七章向量層 | A. 照 ai-crm：Voyage `EmbeddingClient` + deterministic fallback + JdbcTemplate。B. 保留 Spring AI `VectorStore` 抽象 | **A**。B 會多一套 `vector_store` 表與 ai-crm 的 `knowledge_documents.embedding` 並存 |
| D4-c | 對話記憶鍵 | A. customerId（ai-crm，聊天視窗掛在客戶詳情頁）。B. sessionId | **A** |
| D5 | SSE 認證 | A. fetch + ReadableStream + Authorization header（ai-crm）。B. EventSource + query token（現行課程） | **A**。更安全，且第六章單元 3 的概念段「EventSource 無法自訂 Header」要改成「所以 ai-crm 改用 fetch」 |
| D6 | JWT 實作 | A. 手刻 HMAC-SHA256（ai-crm，無外部依賴）。B. jjwt 0.12（現行課程） | **B 也可接受**。這點不影響任何契約（token 格式、claims、header 都一樣）；建議提示詞只定 claims 與 secret 規則，不指定套件 |
| D7 | Swagger 是否需登入 | A. 文件頁公開、Try it out 需 Authorize（ai-crm）。B. 整頁受保護（現行課程） | **A**，並在提示詞說明「文件公開不等於 API 公開」 |
| D8 | Actuator（作業 4 的 `PATCH /actuator/loggers`） | A. 提示詞加 `spring-boot-starter-actuator`，只開 health／loggers、需 ADMIN。B. 刪掉 u4-t8 | **A**。加一個依賴比刪任務便宜，也讓 ai-crm 多一個有用能力 |
| D9 | RAG 文件上傳 | A. 提示詞新增 `POST /api/ai/knowledge/documents`（ADMIN，接文字或 Markdown，寫 `knowledge_documents` 後即時 chunk＋embedding）。B. 改成「用 SQL 或 seed 加文件後 reindex」 | **A**。「上傳客戶服務規範再提問」是第七章與 Demo Day 的主線；B 會讓展示變成貼 SQL |

### 4a. 決議（2026-09-08，依使用者回覆）

| # | 決議 | 落實方式 |
|---|---|---|
| D1 | 只對齊 ai-crm 核心功能，V21–V27 不納入 | 提示詞只補核心功能的欄位、端點、角色、驗收 |
| D2 | **不改** port 與環境值，沿用課程慣用的標準值（8080、5432、5173、learn_spring、npm） | 上表「全部改 18080」等建議不採用；只把版本更新到最新（Spring Boot 4.1.x、Spring AI 2.0.x GA、Vite 最新版、pgvector pg18、springdoc-openapi 3.x、jjwt 0.13.x） |
| D3 | 客戶情境以網頁為準：亞太智能製造、環球零售巨擘、鼎峰金融科技 | 第三章種子資料提示詞寫入三家的產業、合約、聯絡人、生意機會與往來，另加一家資料不足的新客戶供第八章情境；教材範例裡的「台積電／聯發科／VIP 等級」全部改成課程客戶與固定欄位 |
| D4 | 技術路線以課程章節為準：Tool Calling、MessageChatMemoryAdvisor＋sessionId、Spring AI VectorStore（pgvector）＋QuestionAnswerAdvisor、對話結果向量化的長期記憶、MCP 選修 | 保留課程技術，但把 ai-crm 的功能效果寫成契約：工具只讀、套權限、遮罩個資、查無資料要老實說；風險規則由程式算；情緒／意圖固定標籤；評估報告固定四段；每次呼叫記 ai_call_log。長期記憶另加 `GET /api/ai/history/search` 讓對話結果可事後語意查詢分析 |
| D5 | SSE 認證沿用課程方法：EventSource＋網址參數 token | 只補「僅 /api/ai/stream 接受網址參數 token」與 sessionId 綁定登入者 |
| D6 | JWT 用課程的 jjwt | 版本升到 0.13.x |
| D7 | Swagger 沿用課程：需登入 | 不改 |
| D8 | Actuator 沿用課程作業 | 不改 |
| D9 | 知識庫保留上傳 | 提示詞明寫 `POST /api/rag/upload`（ADMIN）、列出／刪除文件、引用來源、找不到要說 |
| 角色 | 採 SALES／MANAGER／ADMIN 三角色（課程第四章單元 4 本來就教三角色，第七章團隊分析也需要） | 示範帳號 sales／manager／admin，密碼 password123；Swagger 驗證步驟與作業 4 同步 |

---

## 5. 改寫範例（確認風格用）

以下三段示範改寫後的長度與寫法：仍是口語、仍是一段，但把契約塞進括號與條列，AI 讀得到、學員不用懂術語也能貼。

### 5.1 專案基線（每章導言後放一次）

```text
【專案基線，本章所有提示詞都沿用】
- 專案是 monorepo：根目錄 pom.xml 是 com.aicrm:ai-crm-parent 的 aggregator，後端在 backend/（Spring Boot 4.1.x、Java 21、Spring AI 2.0.x 用 spring-ai-bom 管版本），前端在 frontend/（React 19 + TypeScript + Vite，套件管理用 pnpm）。
- 後端 port 18080；資料庫是 Docker 的 pgvector/pgvector:pg16，本機 port 15432，資料庫／帳號／密碼都是 aicrm。
- 角色只有 SALES、MANAGER、ADMIN；預設帳號 sales@aurora.local、manager@aurora.local、admin@aurora.local，密碼 password123。
- 錯誤一律回 RFC 7807 ProblemDetail（title/status/detail/instance）；所有 request/response 都用 record DTO 集中在 api/Dtos.java，不直接回 Entity。
- 秘密只放專案根目錄 .env（已在 .gitignore），Spring 用 spring.config.import 讀。
- 每個函式與重要變數都要有繁體中文註解；做完列出改了哪些檔案，並實際執行驗證指令給我看結果。
```

### 5.2 Unit 2 ②（🔴 → 改寫）

```text
沿用專案基線。請把暖身版擴充成完整的 CRM 資料模型，四種東西與關係如下（一個客戶可以有多位聯絡人、多筆往來紀錄、多個生意機會）：
- 客戶 customers：name、email、phone（台灣手機 09 開頭 10 碼）、taxId（8 碼統編）、industry、ownerName（負責業務）、status（ACTIVE / INACTIVE / LEVERAGED）、contractStartDate、contractEndDate、renewalDueDate。
- 聯絡人 contacts：name、title、email。
- 往來紀錄 interactions：type（PHONE / MEETING / EMAIL / SUPPORT_TICKET）、occurredAt、content。
- 生意機會 opportunities：name、stage（QUALIFICATION / PROPOSAL / NEGOTIATION / CLOSED_WON / CLOSED_LOST）、amount、expectedCloseDate、type（NEW_BUSINESS / RENEWAL）。
每種都要能新增和查詢；查單一客戶時一次把他的聯絡人、往來紀錄、生意機會都帶回來。輸入要用 @Valid 檢查，錯誤回 ProblemDetail。資料先暫存在程式裡，下一章再接資料庫，但欄位名現在就要固定，之後不再改。
```

### 5.3 Unit 6 ②（🔴，依 D4-a 選 A 改寫）

```text
沿用專案基線。請讓 AI 助手回答前先拿到真實資料，做法是「程式先算、AI 只寫」：
1. 後端先依 customerId 從資料庫撈出客戶基本資料、最近的往來紀錄、進行中的生意機會、程式算好的風險等級，組成一段「事實清單」放進 system prompt，再把使用者的問題交給模型。
2. 系統提示詞要求模型只能根據事實清單回答，不得自行編造金額、折扣、日期；資料不足就明說缺什麼。
3. 送給模型前把 email、電話、統編遮罩（例如 [已遮罩EMAIL]），資料庫與畫面上的原值不受影響。
4. 每一次呼叫都記一筆 ai_call_log（類型、客戶、模型、token 用量、是否真的呼叫模型、是否遮罩、回答內容）。
5. 沒有設定 OPENAI_API_KEY 時，走一條不呼叫模型、直接用事實清單組成固定格式回答的備援流程，畫面上要標示這是備援回答。
完成後示範：問「星河製造目前有哪些進行中的生意？金額多少？」，AI 回答的金額要跟 opportunities 表一致；再問一個不存在的客戶，AI 要回查無資料。
```

---

## 6. 修改範圍、順序與驗證

| 步驟 | 動作 | 檔案 | 驗證 |
|---|---|---|---|
| 1 | 改 `course-data.js`：8 個單元的 `prompt` 導言＋基線段、37 段口語化提示詞、11 段技術提示詞、受影響的 `tasks[].label`（u3-t4、u5-t2、u6-t1／t3／t5／t8／t9、u7-t6～t9）、概念段內 `8080`／`5432`／`learn_spring`／`npm` 字串 | `teaching-site/course-data.js` | `node scripts/verify-site.mjs`、`node scripts/verify-render.mjs`（在 teaching-site 下） |
| 2 | 重跑匯出，讓 `_source/u1–u8.md` 反映新提示詞 | `scripts/export-teaching-site-content.mjs` → `course-package/_source/` | diff 確認只有提示詞段變動 |
| 3 | 同步 `course-package` 各章「示範與提示詞」段、作業驗收標準、旁白稿裡引述提示詞內容的句子（ch01 02、ch02 03／05、ch03 01／03、ch05 04、ch06 01 共 5 份含 port 或指令） | `course-package/ch01–ch08/*.md` | `node scripts/verify-course-package.mjs`（根目錄） |
| 4 | 課程情境頁補「課程三家客戶 ↔ ai-crm 種子四家」對照（D3 選 A 時） | `course-data.js` shared case 段、`course-package/00-course-orientation.md` | 同步驟 1 |
| 5 | 抽兩段改寫後的提示詞（Unit 2 ②、Unit 3 ③）在暫存目錄實際丟給 Agent 跑一次，對照 ai-crm 的 `Dtos.java` 與 `CustomerController` 檢查產出是否對齊 | 暫存目錄，不進 repo | 人工比對欄位與端點 |

不動的東西：`ch01/00.mp4`、`02.mp4` 與字幕（已錄影，提示詞內容變動只在文字教材）；`live-slides/`（直播投影片是 task-mgr 案例，與 ai-crm 無關）；ai-crm 本身（除非 D3 選 B）。

---

## 7. 本次不處理但要記錄的事

- ai-crm 自己的文件過期：`docs/spec.md` 技術決策段仍寫 Spring Boot 3.5；`backend/CLAUDE.md` 寫 Flyway V1–V17（實際 V27）；`frontend/CLAUDE.md` 的目錄描述（`src/api.ts`、`features/agent-trace/`）與現況不符；`start-crm.ps1` 前端用 5175 但 `vite.config.ts` 是 5173。改提示詞時以程式碼為準，這些文件另外開 task 修。
- `AgentFlowService` 的 `/api/agent/customers/{id}/trace` 是 deterministic 教學流程，README 已聲明不是多步驟 tool-calling agent；第七章 MCP 選修與第九章 superpowers 提到 GOAP 時不要把它說成真的 agent。
- 第五章「毛玻璃、漸層、骨架屏」等視覺要求與 ai-crm 現行 `styles.css` 的視覺不一定相同；本次只對齊結構（路由、殼層、狀態），不對齊配色。

---

## 8. 執行紀錄（2026-09-08）

| 步驟 | 內容 | 驗證 |
|---|---|---|
| 1 | `teaching-site/course-data.js`：36 段口語化提示詞全部改寫（🔧 排錯不動）、10 段技術提示詞改寫、8 個單元導言補「請整段複製」、jjwt 0.13.0、測驗題 q1 改 Spring Boot 4.1.x／pg18、Swagger 驗證步驟改 sales／admin／password123、u3-t4 任務標籤、範例客戶改課程情境 | `node scripts/verify-site.mjs` OK、`node scripts/verify-render.mjs` OK |
| 2 | `teaching-site/app.js`：macOS 版提示詞文字轉換補 check-env／start-crm／stop-crm 的 .sh 對應 | 同上 |
| 3 | `scripts/export-teaching-site-content.mjs`、`scripts/verify-course-package.mjs`：原本用 JSON.parse 讀 course-data.js，檔案早已含尾逗號而失效；改為 vm 沙箱載入，並把 day3（u9–u12）納入單元來源 | `node scripts/verify-course-package.mjs` PASS |
| 4 | `course-package/_source/`：重新匯出 | — |
| 5 | `course-package/ch01–ch08`：逐字引用的提示詞同步；作業 1 依賴清單、Spring Boot 4.1.x、作業 3 的 Product.java、作業 4 的 ProblemDetail 與帳號、第七章 Spring AI M8／RC1 過期敘述、第二章「欄位照常識」旁白、第六／七章「台積電、半導體、VIP」範例 | `verify-course-package` PASS；grep 無殘留 |

沒動的：`ch01/00.mp4`、`02.mp4` 與字幕（影片內容仍是舊的 Initializr 畫面，文字教材已更新）；`live-slides/`；ai-crm 本身。
第七章 ①②③（團隊分析、工作台、全公司評估）課程包沒有逐字引用網站提示詞，只在 `_source/u7.md` 更新。

