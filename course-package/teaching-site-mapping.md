# teaching-site ↔ course-package 逐檔對照

本文件是課程素材包的對應索引。來源以目前的
`teaching-site/course-data.js` 為準；`course-package/_source/` 是由匯出腳本產生的
快照，不能取代網站 source of truth。

## 使用方式

每一列都同時指向三個東西：

1. teaching-site 的來源單元、概念、提示詞與任務。
2. 學員閱讀的教學內容檔（不含 `-旁白稿`）。
3. 講師／錄音使用的旁白稿檔（含 `-旁白稿`）。

旁白稿不是把講義再念一遍，而是依照「先說目的 → 指示操作 → 等待畫面／終端機結果 → 做驗收 → 預告下一步」編排。錄製時讀到「我們現在來」「請執行」「打開」「驗證」等句子，就在該句後停頓，完成對應操作；詳細提示詞、完整命令與預期結果回到同列的教學內容檔查閱。

## 官方來源邊界

- `u1`～`u8`：教學網站 Day 1、Day 2 的正式單元，拆成課程素材包的多個可授課小節。
- `u9`：教學網站的 Cloudflare Tunnel 達標解鎖單元，對應 `bonus-cloudflare-tunnel/`，不列入官方 36 個基本單元。
- `superpowers`：教學網站目前的第 9 章技能補充，包含規劃、執行、品質、收尾四組共 13 個技能。
- `ch09-dev-skills/02-ui-ux-pro-max.md` 與 `03-deep-memory.md`：目前素材包保留的延伸教材；現行 `teaching-site/course-data.js` 沒有相同的 unit 或 skill 項目，因此本索引不把它們宣稱為網站正式來源。若要讓網站與素材包完全一致，後續應先把這兩個延伸單元加入網站 source，再重新匯出與驗證。
- `ch01-env-and-ai-workflow/00-course-orientation-旁白重錄稿.md` 是開場影片的錄音稿副本，不是 Hahow 官方單元；正式開場內容是根目錄的 `00-course-orientation.md`，錄製成品為 `ch01-env-and-ai-workflow/00.mp4`（另附 `00.mp3`、`00.srt`）。播放順序必須先完成 00 開場，再進入 `01-environment-setup` 的環境安裝。

## 開場（非正式單元）

| 內容檔 | 旁白稿 | 來源與操作範圍 |
|---|---|---|
| `00-course-orientation.md` | `00-course-orientation-旁白重錄稿.md` | 先對齊 teaching-site 的 32 小時課程總覽、AI CRM 產出與三家共用 B2B 客戶情境，再說明影片與網站分工、複製提示詞、不要抄程式碼與跟丟時的回放策略；完成後才進入 `ch01/01-environment-setup`。 |

## 章節 1｜開發環境、專案骨架與 AI 協作流程（`u1`）

| 內容檔 | 旁白稿 | teaching-site 對應 |
|---|---|---|
| `ch01-env-and-ai-workflow/01-environment-setup.md` | `ch01-env-and-ai-workflow/01-environment-setup-旁白稿.md` | 播放 `ch01-env-and-ai-workflow/00.mp4`（課程總覽與共用情境）後再開始本單元。概念：環境準備重點、用 AI Agent 安裝開發工具、環境驗證與常見問題。提示詞：① 用 AI 把開發環境準備好、✅ 驗證、🔧 排錯。任務：`u1-t6`、`u1-t1`～`u1-t4`。操作結果：PowerShell 7、JDK 21、Maven、Git、Node.js、Python 與 VS Code 外掛可被命令列驗證。 |
| `ch01-env-and-ai-workflow/02-project-scaffold.md` | `ch01-env-and-ai-workflow/02-project-scaffold-旁白稿.md` | 概念：建立課程專案。提示詞：② 建立專案的資料夾骨架、✅ 驗證、🔧 排錯。任務：`u1-t5`。操作結果：Spring Initializr 專案建立、首次啟動成功，並能說明 backend／frontend／database 的責任邊界。 |
| `ch01-env-and-ai-workflow/03-when-to-use-ai.md` | `ch01-env-and-ai-workflow/03-when-to-use-ai-旁白稿.md` | 概念：AI 協作的適用時機。以網站的「先讀需求 → 產程式 → 驗證」原則示範何時交給 AI、何時由人核對，不把 AI 當成免審查的黑箱。 |
| `ch01-env-and-ai-workflow/04-why-crm.md` | `ch01-env-and-ai-workflow/04-why-crm-旁白稿.md` | 概念：為什麼選 CRM 作為實作題目。連到 shared case 的三家虛擬客戶與後續的 Customer、Interaction、Opportunity、Task 業務情境。 |
| `ch01-env-and-ai-workflow/assignment-1.md` | — | 作業彙整 `u1-t6`、`u1-t1`～`u1-t5`，驗收環境與專案骨架，不另製旁白檔。 |

## 章節 2｜Spring MVC、REST API 與 CRM Domain Modeling（`u2`）

| 內容檔 | 旁白稿 | teaching-site 對應 |
|---|---|---|
| `ch02-spring-mvc-rest-domain/01-spring-boot-mvc.md` | `ch02-spring-mvc-rest-domain/01-spring-boot-mvc-旁白稿.md` | 概念：Spring MVC 核心架構、IoC 與 DI。提示詞 ① 暖身客戶資料功能。任務：`u2-t1`、`u2-t2`。操作結果：能追蹤 DispatcherServlet → Controller → Service 的請求流。 |
| `ch02-spring-mvc-rest-domain/02-rest-api-design.md` | `ch02-spring-mvc-rest-domain/02-rest-api-design-旁白稿.md` | 概念：REST、CRM 銷售漏斗、請求／回應結構、HTTP 方法與狀態碼。提示詞 ①、②。任務：`u2-t2`、`u2-t3`。操作結果：用 `Invoke-RestMethod` 實際驗證 GET／POST 端點與回應狀態。 |
| `ch02-spring-mvc-rest-domain/03-layered-architecture.md` | `ch02-spring-mvc-rest-domain/03-layered-architecture-旁白稿.md` | 概念：Controller／Service 分工、Lombok、用 AI Agent 建立可獨立運行示範專案。提示詞 ②。任務：`u2-t2`。操作結果：專案可單獨啟動，業務規則不直接塞在 Controller。 |
| `ch02-spring-mvc-rest-domain/04-input-validation.md` | `ch02-spring-mvc-rest-domain/04-input-validation-旁白稿.md` | 概念：不能信任前端資料、常用 Bean Validation 標註。連到提示詞 ② 與任務 `u2-t2`、`u2-t3` 的失敗輸入驗證。操作結果：缺欄位、格式錯誤與非法值會得到可理解的錯誤回應。 |
| `ch02-spring-mvc-rest-domain/05-crm-domain-model.md` | `ch02-spring-mvc-rest-domain/05-crm-domain-model-旁白稿.md` | 概念：CRM Domain Model 設計思維。提示詞 ② 設計完整 CRM 客戶關係資料。任務：`u2-t2`。操作結果：能畫出 Customer、Contact、Interaction、Opportunity 的關係並說明它們如何支援業務流程。 |
| `ch02-spring-mvc-rest-domain/assignment-1.md` | — | 作業彙整 `u2-t1`～`u2-t3`：讀流程、產生 REST API、用 PowerShell 走端點驗收。 |

## 章節 3｜PostgreSQL、Flyway、JPA 與動態查詢（`u3`）

| 內容檔 | 旁白稿 | teaching-site 對應 |
|---|---|---|
| `ch03-persistence-and-search/01-docker-database.md` | `ch03-persistence-and-search/01-docker-database-旁白稿.md` | 概念：資料庫容器化、用 AI Agent 產生 `docker-compose.yml`。提示詞 ①。任務：`u3-t1`、`u3-t2`。操作結果：`hello-world` 通過、PostgreSQL／pgvector 容器啟動、具名 volume 保留資料。 |
| `ch03-persistence-and-search/02-flyway-schema-versioning.md` | `ch03-persistence-and-search/02-flyway-schema-versioning-旁白稿.md` | 概念：Flyway 角色、命名規則與雙底線陷阱、Flyway 與 `ddl-auto` 分工。提示詞 ②。任務：`u3-t3`。操作結果：啟動時 migration 按版本執行，重跑不會靠 Hibernate 偷改正式 schema。 |
| `ch03-persistence-and-search/03-datasource-config.md` | `ch03-persistence-and-search/03-datasource-config-旁白稿.md` | 概念：用 AI Agent 設定 `application.yml` 資料庫連線。提示詞 ③、✅ 驗證。任務：`u3-t3`。操作結果：Spring Boot 能連容器中的資料庫，Log 可看見 datasource／Flyway 啟動成功。 |
| `ch03-persistence-and-search/04-orm-entities.md` | `ch03-persistence-and-search/04-orm-entities-旁白稿.md` | 概念：JPA／Entity、Lombok 注意事項、Repository、`@Transactional`、`@Modifying`、Audit 與 `BaseAuditEntity`。提示詞 ③。任務：`u3-t4`、`u3-t5`。操作結果：Product／Customer 由 Entity 對應資料表，寫入與更新由 transaction 保護。 |
| `ch03-persistence-and-search/05-dynamic-query.md` | `ch03-persistence-and-search/05-dynamic-query-旁白稿.md` | 概念：Query Method 的限制、Specification、選型時機。提示詞 ③。任務：`u3-t5`。操作結果：可組合關鍵字、產業、狀態等條件，不用為每種組合新增一個方法。 |
| `ch03-persistence-and-search/06-crm-data-model-integration.md` | `ch03-persistence-and-search/06-crm-data-model-integration-旁白稿.md` | 概念：CRM 資料模型如何對應 JPA Entity。提示詞 ③、✅ 驗證。任務：`u3-t6`。操作結果：重啟服務後 API 仍能讀到資料，證明資料真的落在 PostgreSQL 而非記憶體。 |
| `ch03-persistence-and-search/assignment-1.md` | — | 作業彙整 `u3-t1`～`u3-t6`，驗收 Docker、migration、JPA、Repository、搜尋與持久化。 |

## 章節 4｜Spring Security、JWT、OpenAPI 與企業級錯誤處理（`u4`）

| 內容檔 | 旁白稿 | teaching-site 對應 |
|---|---|---|
| `ch04-security-jwt-openapi/01-openapi-swagger.md` | `ch04-security-jwt-openapi/01-openapi-swagger-旁白稿.md` | 概念：API 文件、OpenAPI 標註。提示詞 ①。任務：`u4-t1`～`u4-t3`。操作結果：開啟 Swagger UI，看得到端點、參數、回應格式並能按 Try it out。 |
| `ch04-security-jwt-openapi/02-exception-logging-aop.md` | `ch04-security-jwt-openapi/02-exception-logging-aop-旁白稿.md` | 概念：統一錯誤回應、ProblemDetail、Log、`@Slf4j`、Actuator 與 AOP。提示詞 ②。任務：`u4-t4`～`u4-t10`。操作結果：404／驗證失敗格式一致，能讀 Log 並理解橫切關注點。 |
| `ch04-security-jwt-openapi/03-spring-security-jwt.md` | `ch04-security-jwt-openapi/03-spring-security-jwt-旁白稿.md` | 概念：安全防護、JWT 三段式結構、Spring Security 五大零件。提示詞 ①、②。任務：`u4-t11`、`u4-t12`。操作結果：無 Token 得 401、登入取得 JWT、一般角色不能執行管理操作。 |
| `ch04-security-jwt-openapi/04-crm-authorization.md` | `ch04-security-jwt-openapi/04-crm-authorization-旁白稿.md` | 概念：CRM 角色與權限模型、API／資料／AI 三層防線。提示詞 ✅。任務：`u4-t13`。操作結果：以 SALES、MANAGER、ADMIN 分別驗證能做與不能做的事。 |
| `ch04-security-jwt-openapi/assignment-1.md` | — | 作業彙整 `u4-t1`～`u4-t13`，以 Swagger UI 完成文件、錯誤、Log、JWT 與角色權限驗收。 |

## 章節 5｜React CRM 工作台與前後端整合（`u5`）

| 內容檔 | 旁白稿 | teaching-site 對應 |
|---|---|---|
| `ch05-react-crm-workbench/01-react-project-setup.md` | `ch05-react-crm-workbench/01-react-project-setup-旁白稿.md` | 概念：Node.js／React 專案、Vite Proxy、前後端串接。提示詞 ①。任務：`u5-t1`～`u5-t3`。操作結果：Vite 5173 可開、`/api` 代理到 8080，不靠把 CORS 全開來掩蓋設定問題。 |
| `ch05-react-crm-workbench/02-jsx-basics.md` | `ch05-react-crm-workbench/02-jsx-basics-旁白稿.md` | 概念：JSX 與 Functional Component。以 `u5-t2` 建立的專案做熱更新操作，驗證狀態改變會驅動畫面。 |
| `ch05-react-crm-workbench/03-frontend-visual-guidelines.md` | `ch05-react-crm-workbench/03-frontend-visual-guidelines-旁白稿.md` | 概念：uiuxpromax 指引、AI Agent 產生視覺骨架、Loading／Error／Empty 三態。提示詞 ①。任務：`u5-t4`。操作結果：毛玻璃、漸層、骨架屏與三態畫面可實際看見。 |
| `ch05-react-crm-workbench/04-crm-workbench-design.md` | `ch05-react-crm-workbench/04-crm-workbench-design-旁白稿.md` | 概念：CRM 工作台 UI 設計。提示詞 ②～⑥、✅。任務：`u5-t3`、`u5-t4`。操作結果：登入、Dashboard、客戶列表、詳情、商機看板與分析分群都讀真實 API 資料。 |
| `ch05-react-crm-workbench/assignment-1.md` | — | 作業彙整 `u5-t1`～`u5-t4`，驗收 React、Proxy、視覺狀態與真實資料串接。 |

## 章節 6｜Spring AI ChatClient、SSE 與 tool calling（`u6`）

| 內容檔 | 旁白稿 | teaching-site 對應 |
|---|---|---|
| `ch06-spring-ai-sse-toolcalling/01-ai-chat-and-memory.md` | `ch06-spring-ai-sse-toolcalling/01-ai-chat-and-memory-旁白稿.md` | 概念：ChatClient、Builder、Advisor、Groq 端點、串流與對話記憶。提示詞 ①。任務：`u6-t1`～`u6-t3`。操作結果：模型設定可用、不同 session 不互相讀到對話，能完成第一個 AI 對話入口。 |
| `ch06-spring-ai-sse-toolcalling/02-tool-calling.md` | `ch06-spring-ai-sse-toolcalling/02-tool-calling-旁白稿.md` | 概念：工具呼叫、`@Tool`／`ToolCallback`、工具架構與實際展示。提示詞 ②。任務：`u6-t4`～`u6-t6`。操作結果：AI 先呼叫 Customer／Opportunity 工具取得真實數字，再由模型整理文字；查不到就明說。 |
| `ch06-spring-ai-sse-toolcalling/03-sse-streaming.md` | `ch06-spring-ai-sse-toolcalling/03-sse-streaming-旁白稿.md` | 概念：SSE、EventSource JWT Query Token、客戶摘要／商機／建議行動卡片。提示詞 ③。任務：`u6-t7`～`u6-t9`。操作結果：React 逐段收到事件、畫面即時更新，未登入或 Token 錯誤會被阻擋。 |
| `ch06-spring-ai-sse-toolcalling/04-business-value.md` | `ch06-spring-ai-sse-toolcalling/04-business-value-旁白稿.md` | 概念：AI 基礎對話、客戶查詢、商機進度、長期記憶推薦。提示詞 ④～⑦。任務：承接 `u6-t6`、`u6-t9`。操作結果：數字由工具／程式計算，摘要、風險、情緒與意圖由 AI 撰寫。 |
| `ch06-spring-ai-sse-toolcalling/assignment-1.md` | — | 作業彙整 `u6-t1`～`u6-t9`，用真實資料、串流畫面與驗證提示詞完成 AI 助理驗收。 |

## 章節 7｜團隊智慧分析、全公司評估與 RAG 知識庫（`u7`）

| 內容檔 | 旁白稿 | teaching-site 對應 |
|---|---|---|
| `ch07-rag-pgvector-mcp/01-rag-and-etl.md` | `ch07-rag-pgvector-mcp/01-rag-and-etl-旁白稿.md` | 概念：RAG、向量嵌入、pgvector、Fine-tuning 比較、ETL。提示詞 ④。任務：`u7-t1`～`u7-t3`。操作結果：文件 Extract → Transform → Load 後可被檢索，回答附上來源。 |
| `ch07-rag-pgvector-mcp/02-mcp-and-skills.md` | `ch07-rag-pgvector-mcp/02-mcp-and-skills-旁白稿.md` | 概念：MCP、遠端工具流程、domain tool／RAG／MCP 選型、Skills、`spring-ai-agent-utils`。提示詞 ⑥。任務：`u7-t4`～`u7-t6`。操作結果：能說明 MCP Server／Client，並先檢查版本前置條件再評估導入。 |
| `ch07-rag-pgvector-mcp/03-long-term-memory.md` | `ch07-rag-pgvector-mcp/03-long-term-memory-旁白稿.md` | 概念：對話歷史 RAG、非同步寫入、Metadata 隔離、多路檢索與共同知識。提示詞 ⑤。任務：`u7-t7`～`u7-t9`。操作結果：SSE 完成後背景寫入歷史，新 session 能召回同一使用者的偏好。 |
| `ch07-rag-pgvector-mcp/04-crm-knowledge-base.md` | `ch07-rag-pgvector-mcp/04-crm-knowledge-base-旁白稿.md` | 概念：CRM 文件哪些該向量化與 ETL 設計。提示詞 ④、⑤、✅。任務：`u7-t3`、`u7-t9`。操作結果：主管分析、業務工作台、文件問答與歷史建議可以在同一個 CRM 情境中被驗收。 |
| `ch07-rag-pgvector-mcp/assignment-1.md` | — | 作業彙整 `u7-t1`～`u7-t9`，驗收 RAG、MCP／Skills、長期記憶與多路檢索。 |

## 章節 8｜結訓專案衝刺與 Demo Day 驗收（`u8`）

| 內容檔 | 旁白稿 | teaching-site 對應 |
|---|---|---|
| `ch08-capstone-demo-day/01-full-demo.md` | `ch08-capstone-demo-day/01-full-demo-旁白稿.md` | 概念：整合三大挑戰、Demo Day 準備與完整展示腳本。提示詞 ①、✅。任務：`u8-t1`、`u8-t4`。操作結果：從零啟動、登入、看客戶、查 AI、看分析、做知識庫問答，能完整走完展示路徑。 |
| `ch08-capstone-demo-day/02-testing-strategy.md` | `ch08-capstone-demo-day/02-testing-strategy-旁白稿.md` | 概念：三種端到端客戶場景、AI 分層測試、上線清單與效能調校。提示詞 ②。任務：`u8-t2`、`u8-t3`。操作結果：確定性的工具／Prompt／RAG 測試與 Playwright E2E 都有可重跑證據。 |

## 章節 9｜常用開發技能（`superpowers`）

| 內容檔 | 旁白稿 | teaching-site 對應 |
|---|---|---|
| `ch09-dev-skills/01-superpowers.md` | `ch09-dev-skills/01-superpowers-旁白稿.md` | 正式來源：`superpowers`。涵蓋 brainstorming、writing-plans、executing-plans、subagent-driven-development、test-driven-development、using-git-worktrees、systematic-debugging、requesting-code-review、receiving-code-review、verification-before-completion、finishing-a-development-branch、dispatching-parallel-agents、using-superpowers，共 13 項。 |
| `ch09-dev-skills/02-ui-ux-pro-max.md` | `ch09-dev-skills/02-ui-ux-pro-max-旁白稿.md` | 延伸教材，現行 `course-data.js` 無同名 source。內容以章節 5 的 CRM 工作台為練習情境，示範設計系統、視覺決策與 UX 驗收；不得在對外說明中標成 teaching-site 正式 unit。 |
| `ch09-dev-skills/03-deep-memory.md` | `ch09-dev-skills/03-deep-memory-旁白稿.md` | 延伸教材，現行 `course-data.js` 無同名 source。內容以本 repo 的記憶工作流為練習情境，示範熱庫／冷庫、查詢／工作／回寫；不得在對外說明中標成 teaching-site 正式 unit。 |

## 達標解鎖｜Cloudflare Tunnel（`u9`）

| 內容檔 | 旁白稿 | teaching-site 對應 |
|---|---|---|
| `bonus-cloudflare-tunnel/01-cloudflare-tunnel.md` | `bonus-cloudflare-tunnel/01-cloudflare-tunnel-旁白稿.md` | 概念：雲平台／Cloudflare Containers／自架 + Tunnel、Dockerfile 多階段建置、Compose 四服務、反向連線、Quick Tunnel、Named Tunnel 與安全收尾。提示詞：①～③、✅、🔧。任務：`u9-t1`～`u9-t5`。操作結果：本機服務被打包、公開網址可取得，並用手機 4G 完成登入與 AI 對話驗收。 |

## 延伸部署｜從 Docker 到 Kubernetes（`u10`～`u12`）

本章三個單元已加入 `teaching-site/course-data.js` 的獨立延伸章 `day3.units`（章名「延伸實戰：上線與部署」，順序為 u9 Tunnel → u10～u12 部署三部曲，與核心章節 day1/day2 分開呈現），為網站正式渲染單元，但不列入 Hahow 官方 36 單元；`_source/` 尚未重新匯出（`export-teaching-site-content.mjs` 目前只涵蓋 u1～u9），需要時再補匯出。與 `bonus-cloudflare-tunnel` 的分工：Tunnel 章解決「外網怎麼連進來」，本章解決「系統怎麼打包、搬運與編排」；單元 1 與 Tunnel 章的多階段建置段落有刻意的複習重疊，並在單元定位中明示銜接關係。

| 內容檔 | 旁白稿 | teaching-site 對應 |
|---|---|---|
| `bonus-deployment/01-docker-image-packaging.md` | `bonus-deployment/01-docker-image-packaging-旁白稿.md` | 正式來源：`u10`。概念：交付物演進、多階段建置與 JDK 21 base image、層快取、`.dockerignore`、`docker history` 體檢、tag 版本策略、compose 引用映像驗證。提示詞：①②、✅、🔧。任務：`u10-t1`～`u10-t4`。操作結果：兩個有版本 tag 的映像、重建命中快取證據、compose 全套跑通登入與 AI 對話。 |
| `bonus-deployment/02-deploy-docker-server.md` | `bonus-deployment/02-deploy-docker-server-旁白稿.md` | 正式來源：`u11`。概念：映像／設定／資料三分離、`docker save`/`load` 免 registry 搬運、GHCR 推拉、伺服器端 compose 與 `.env`（`APP_VERSION` 換版開關、restart 策略、最小暴露）、更新與退版、上線檢查清單。提示詞：①～③、✅、🔧。任務：`u11-t1`～`u11-t4`（GHCR 為選做 `u11-t4`）。操作結果：目標環境上線、外部裝置完成業務驗收、更新與退版各演練一次。 |
| `bonus-deployment/03-k8s-docker-desktop.md` | `bonus-deployment/03-k8s-docker-desktop-旁白稿.md` | 正式來源：`u12`。概念：compose 的三個極限、宣告式收斂、Docker Desktop 內建 k8s、compose↔k8s 翻譯表（Deployment/Service/ConfigMap/Secret/PVC）、`imagePullPolicy: IfNotPresent`、資料庫取捨、自癒與滾動更新、學習環境與生產差距。提示詞：①～③、✅、🔧。任務：`u12-t1`～`u12-t5`。操作結果：AI CRM 在單節點叢集跑通業務流程、Pod 自癒與滾動更新／退版各留下一段可回放證據。 |

## 作業與旁白的共同操作規則

- 作業檔只整理驗收，不另外複製單元旁白；錄課時使用同章節的單元旁白，再用作業檔逐項勾選。
- 旁白中的命令、URL、帳號、測試客戶與預期狀態碼，若與目前專案實作不同，以同列內容檔和 teaching-site 最新提示詞為準。
- 每次操作都要保存一個可審查結果：終端機輸出、瀏覽器畫面、API 回應、Log 或測試報告。不要只用「看起來成功」作為驗收。
- 全課信任邊界固定為：「數字由程式算、文字由 AI 寫」。只要是金額、次數、權限、狀態或是否成功，都必須用程式或測試確認；AI 只負責說明與洞察。
