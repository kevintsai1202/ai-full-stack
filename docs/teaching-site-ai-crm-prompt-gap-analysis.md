# teaching-site 提示詞與 ai-crm Web App 設計差異分析

日期：2026-08-24
分析範圍：`D:\GitHub\hahow-ai-full-stack\teaching-site`、`課程內容.md` 與 `D:\GitHub\ai-crm` 目前的 Web App、API、後端服務與驗證設計。
文件狀態：僅分析與差異整理；本次未修改 teaching-site 或 ai-crm 的程式、課程資料與既有規格文件。

## 一、結論先行

### 1. 針對課程定義的教學版 AI CRM：可以完成

Unit 1–8 的提示詞已經能串起一條可工作的核心產品路線：

`專案骨架 → CRM domain → PostgreSQL/Flyway/JPA → JWT/RBAC → React 工作台 → SSE AI 助理 → RAG/長期記憶 → Demo Day 驗收`

因此，若目標是課程中定義的「可登入、可查客戶、可看 Dashboard、可用 AI 對話、可做文件問答」教學版，現有提示詞經過逐章執行與人工核對後，具備完成條件。這與課程內容列出的必做功能一致（`課程內容.md:439-458`），也與 Unit 1–8 的提示詞主線一致（`teaching-site/course-data.js:269-1325`）。

### 2. 針對目前的 ai-crm Web App：不能直接完成

現有提示詞不足以從零可靠產出目前的 `ai-crm`。它們比較像「功能方向提示詞」，而目前的 `ai-crm` 已經是帶有明確資料契約、權限隔離、AI 治理、媒體處理、交易一致性與真實 E2E 驗證的 production-shaped Web App。

目前提示詞可以作為核心 MVP 的起點，但不能視為目前 ai-crm 的完整開發規格。若直接照現有提示詞執行，最可能得到的是「畫面和主流程看起來完成」，但在下列地方產生實質落差：

- 角色名稱與資料可見範圍不完整。
- API、資料表、狀態機、錯誤格式與版本控制沒有被固定。
- AI 的 grounding、PII 遮罩、模型能力治理、fallback、audit 與輸出驗證沒有被明確要求。
- 名片 OCR、會議 Copilot、跟進信寄送、任務與 iCalendar、商機健康度、Stakeholder Map 等目前功能沒有對應提示詞。
- Web App 的實際導覽、RWD、i18n、Dashboard 個人化與下鑽返回行為沒有被描述到可實作程度。
- 驗收提示詞多半只要求「確認能運作」，沒有要求保留可追溯的測試證據與真實資料邊界。

## 二、比較基準

### 課程提示詞目前明確覆蓋的內容

課程內容已定義 Java 21、Spring Boot、PostgreSQL + pgvector、Flyway、Spring Security + JWT、Spring AI、React、RAG、Tool Calling、MCP 與 E2E 驗收（`課程內容.md:11-25`、`課程內容.md:36-50`）。

`course-data.js` 的主要提示詞也已經包含：

- Unit 1–4：環境、CRM 四類核心資料、持久化、動態查詢、登入與基本權限。
- Unit 5：登入、客戶清單、客戶詳情、商機看板、Dashboard 圖表、客戶分群與下鑽（`teaching-site/course-data.js:880-888`）。
- Unit 6：SSE 聊天、查真實 CRM 資料、客戶評估、風險、情緒與意圖（`teaching-site/course-data.js:1042-1051`）。
- Unit 7：主管團隊分析、個人工作台、Portfolio 評估、RAG 文件、對話記憶與選修外部工具（`teaching-site/course-data.js:1195-1204`）。
- Unit 8：整合、三種典型客戶、Demo 流程與基本上線檢查（`teaching-site/course-data.js:1320-1325`）。

### 目前 ai-crm 的實際設計範圍

目前 repo 的 README 明確列出 SALES、MANAGER、ADMIN 三種角色、Customer/Contact/Interaction/Opportunity、Dashboard、RFM、情緒、AI governance、PII、RAG、任務、iCalendar、名片 OCR 與 deterministic fallback（`D:\GitHub\ai-crm\README.md:19-30`）。

AI 也不是單純聊天：目前設計包含 grounding-first、由 Java/DB 計算風險與客戶 ID、模型輸出再做權限與 enum 驗證、Chat/Vision/Audio 三種用途分離、每次呼叫記錄 audit，以及無 API key 時的 deterministic fallback（`D:\GitHub\ai-crm\README.md:35-50`）。

前端則已拆成 React Router + feature modules，包含 Dashboard、Customers、Business Card、Meeting Copilot、Follow-up、Opportunity Intelligence、Stakeholder Map、Team、Admin Users 與 Admin Settings 路由（`D:\GitHub\ai-crm\frontend\src\App.tsx:1-49`）；共用 AppShell 還包含健康狀態、角色導覽、桌面側欄收合、手機選單、語系切換與登出流程（`D:\GitHub\ai-crm\frontend\src\app\AppShell.tsx:53-185`）。

## 三、能力覆蓋矩陣

| 能力範圍 | 課程提示詞狀態 | 目前 ai-crm 狀態 | 判定 | 需要補的關鍵內容 |
|---|---|---|---|---|
| 專案骨架與核心 CRM | Unit 1–3 有完整連續流程 | 已有 monorepo、Spring Boot 4.1、React 19、PostgreSQL、Flyway | 高 | 固定實際版本、port、profile、目錄與驗收腳本 |
| Customer / Contact / Interaction / Opportunity | Unit 2–3 有明確概念，但欄位讓 AI 依「一般 CRM 常識」設計 | 已有 owner、續約、lead source、probability、close reason、stage history 等較深欄位 | 中 | 用資料契約取代自由設計欄位，列出 migration 與回填規則 |
| 登入與基本 RBAC | Unit 4 有一般使用者/管理員；教材另有 SALES/MANAGER/ADMIN 概念 | 已有三角色、owner scope、manager scope、admin route 與 API 保護 | 中 | 明確列角色、資料 scope、AI scope、403/401、停用帳號與限流 |
| React 核心工作台 | Unit 5 有登入、列表、詳情、看板、Loading/Error/Empty | 已有 feature-based routes、健康狀態、手機導覽、語系與多個工作流 | 中 | 補 route map、元件邊界、RWD、a11y、i18n 與狀態同步規則 |
| Dashboard 報表 | Unit 5 已要求漏斗、Forecast、產業、風險、續約、排行榜、近期活動與下鑽 | 另有 RFM、情緒雷達、AI 用量、Portfolio、可拖拉/關閉/加回與個人版面 | 中 | 明確定義 block catalog、固定高度、分頁、下鑽麵包屑、偏好 API |
| AI 聊天與 SSE | Unit 6 已要求逐字串流、查真實資料、客戶評估 | 已有真實 Spring AI + fallback、grounding、SSE、歷史、引用、feedback | 中 | 補 SSE event schema、timeout/retry、callId、引用、fallback 與輸出驗證 |
| RAG 與長期記憶 | Unit 7 有文件上傳、引用來源、跨對話記憶 | 已有 pgvector、chunk、embedding fallback、chat memory 與 reindex | 中 | 補 ingestion、chunk metadata、權限過濾、版本、無命中時 fail closed |
| Team / Workspace / Portfolio | Unit 7 已有概念提示詞 | 已有 manager analytics、team insight、workspace recommendation/chat/history | 中 | 補資料 scope、manager-only、建議與正式 task 分離、AI cache/history |
| Agent Trace | 課程有 GOAP/MCP 教學與 Demo 概念，但沒有完整 API/UI 契約 | 目前是 deterministic 教學流程 trace，不是可宣稱的 multi-agent | 低 | 明確標示模擬範圍、step schema、資料不足/高風險路徑與禁止過度宣稱 |
| AI model capability governance | 現有 Unit 6–7 沒有對應規格 | V21：Chat、Vision、Audio 分開，能力有 AUTO/MANUAL/UNKNOWN 與 fail closed | 無 | 新增模型目錄、能力來源、用途指派、實際測試與不可隱式借用模型 |
| CRM Task / iCalendar | 課程只提到待辦與行動建議，沒有完整狀態機 | V22：正式 task、owner scope、version、postpone/complete、RFC 5545 `.ics` | 低 | 新增 task 狀態、樂觀鎖、時間區、下載契約與重複操作驗證 |
| Business Card OCR | 課程沒有名片 intake 流程 | V23：上傳→辨識→review/dedupe→confirm，原子建立四類資料 | 無 | 新增 Vision model、媒體儲存、低信心欄位、去重、Idempotency-Key、post-commit 刪除 |
| Meeting Copilot | 課程只有會議作為互動類型 | V24：音訊轉錄、結構化 change、逐項勾選、選擇性確認 | 無 | 新增音訊限制、transcript、changeId、人工審核、原子套用與保留政策 |
| Follow-up Email | Unit 7 只有選修 Email 草稿/外部工具 | V25：版本鏈、人工核准、寄送、FAILED-only retry、Reply-To 與 audit | 低 | 新增 draft/version/send/retry 狀態、寄送邊界、Idempotency-Key 與禁止自動寄信 |
| Opportunity Intelligence | 課程提到健康分數，但未要求可解釋歷史 | V26：0–100 純規則計算、components/evidence/trend、只由 LLM 生成文字 | 低 | 新增計分公式、證據引用、snapshot、trend 與不修改 stage/probability 的限制 |
| Stakeholder Map | 課程未定義決策鏈模型 | V27：confirmed facts 與 pending suggestions 分離，可確認/拒絕並留 audit | 無 | 新增角色、關係、來源、狀態、跨客戶禁止與 idempotent suggest |
| AI 安全與治理 | 課程有「不可編造」與基本上線清單 | 已有 PII mask、token/audit、feedback、限流、scope 驗證與 fallback | 中 | 把安全邊界改成每一個 AI prompt 的必填驗收項，而非說明文字 |
| 真實驗證 | 課程要求 E2E 與三種客戶情境，但證據格式寬鬆 | 有 PostgreSQL/Testcontainers、MinIO fake、後端/前端測試與 V21–V27 E2E | 中 | 指定真實 DB、外部服務 fake、HTTP/UI assertion、raw artifact 與 unresolved 限制 |

## 四、最重要的改進差異

### A. 把「功能願望」改成「可執行契約」

目前提示詞常用「欄位你照一般 CRM 常識設計就好」或「做一個現代感介面」。這對教學暖身很適合，但不足以產出可與現有 ai-crm 相容的程式。

每一個後續提示詞都應至少帶入：

1. **前置上下文**：目前 repo、既有檔案、上一單元的 API/資料表、不可覆蓋的範圍。
2. **版本與執行契約**：Java 21、Spring Boot 4.1.x、Spring AI 2.0.x、React 19、Vite、PostgreSQL 16 + pgvector、實際 port/profile。
3. **資料契約**：Entity/DTO 欄位、enum、關聯、分頁、錯誤格式、migration 編號與 seed 場景。
4. **權限契約**：誰可以讀、寫、確認、拒絕、刪除；AI 產出的內容要套用同一個 scope。
5. **輸出範圍**：允許修改的檔案、不得改動的檔案、函式級中文註解、完成後列出 changed files。
6. **驗證契約**：要跑哪些指令、要檢查哪些 HTTP/UI 結果、要保存哪些 raw output；不能用「看起來正常」代替證據。

### B. Unit 1 要從「安裝 Java 工具」補成「可重現的全端基線」

現有 Unit 1 主要要求 JDK、Maven、Git、backend/frontend 骨架。若要對齊目前 ai-crm，關鍵差異是：

- 明確包含 Node.js、pnpm、Docker Desktop、PowerShell 7 與 PostgreSQL/MinIO 的用途。
- 固定 Spring Boot 4.1.x、Spring AI 2.0.x、React 19、TypeScript、Vite 版本基線。
- 固定 backend `18080`、PostgreSQL `15432`、前端 dev server 與 MinIO 服務位置，避免把 `8080`、`5173` 等教材舊值混用。
- 建立 `.env.example`、profile、啟動順序、health probe 與 `check-env.ps1`。
- 第一個驗收不只看後端能啟動，還要確認前端能讀 health、PostgreSQL 可連線、沒有金鑰時 AI 仍走 deterministic fallback。

### C. Unit 2–3 要補「演進式 domain 與 migration」

核心四類資料仍可保留作為教學入口，但提示詞必須說明後續會增加哪些邊界，避免初版 Entity 讓後面全部重做：

- 使用者與 owner 關聯、SALES/MANAGER/ADMIN。
- `CrmTask`、Opportunity stage history、AI call log、chat messages、user preferences。
- knowledge document/chunk/embedding、temporary media、business-card intake、meeting session。
- follow-up draft/version/outbound email、opportunity health snapshot、stakeholder role/relation/suggestion。
- 每次 schema 變更都要有 Flyway migration、向後相容策略、seed/回填策略與重啟驗證。

提示詞不必一次要求學生完成全部資料表，但應把「核心模型 → AI/RAG 擴充 → 智慧工作流」的 migration 邊界說清楚。

### D. Unit 4 要把基本 RBAC 改成資料 scope 與 AI scope

現有「一般使用者 / 管理員」只能驗證有無刪除權限，不能覆蓋目前 app 的實際模型。應改成關鍵要求：

- SALES 只看自己 owner scope；MANAGER 看團隊；ADMIN 看全域。
- 同一個 scope 必須套用到 Customer、Opportunity、Interaction、Task、AI chat、RAG、Portfolio、Trace 與媒體 session。
- 明確決定認證傳遞方式：目前實作同時保留 httpOnly cookie 相容路徑與 response token + sessionStorage Bearer 路徑，提示詞應要求選定並寫成測試契約，不可讓前後端各自猜測。
- 未登入是 401、已登入但越權是 403、資源不存在是 404；統一以 ProblemDetail 回傳。
- CORS、SSE、`Idempotency-Key`、rate limit、停用帳號與 production fail-closed 都要有驗證案例。

### E. Unit 5 的 Web App 提示詞要從「畫面骨架」補成「導覽與狀態設計」

課程現有提示詞已經能產出登入、Dashboard、列表、詳情、看板，但目前 ai-crm 的 Web App 設計還包含：

- `AppShell`、ProtectedRoute、ManagerRoute、AdminRoute 與 feature-based route map。
- 桌面側欄展開/收合並保存偏好；手機單列列首、浮動選單、背景點擊與 Escape 關閉。
- health badge、更新通知、使用者卡、語系切換與登出。
- Dashboard 固定高度、清單分頁、圖表下鑽、麵包屑返回原區塊、拖曳/resize、關閉/加回/還原預設、後端個人偏好持久化（`docs/superpowers/specs/2026-06-19-sp7-dashboard-layout-ux-design.md:6-21`、`140-190`）。
- 1440/1024/768/390 寬度的 RWD、不允許整頁水平溢出、表格只在容器內捲動。
- 英文與繁體中文的 i18n、AI 回答語言與 UI locale 一致。
- 所有頁面和 Modal 都要覆蓋 loading、empty、error、retry、stale response 與 keyboard/a11y 行為。

因此 UI prompt 應指定 route、元件邊界、狀態機、資料來源與驗收 selector，而不是只指定漸層、毛玻璃或動畫。

### F. Unit 6 的 AI prompt 要加入「可信任 AI runtime contract」

現有提示詞已要求「先查真實資料、不可編造」，但目前 ai-crm 的必要邊界更精確：

- Java/DB 先計算 customer ID、scope、risk、todos、amount、stage 與 citations，再交給模型組織語言。
- 送往外部模型前做 PII 遮罩；資料庫與 UI 的原值不能因遮罩而遺失。
- Chat、Vision OCR、Audio transcription 是三種用途，不能因缺少設定就偷偷共用 Chat model。
- 模型無金鑰或呼叫失敗時要 deterministic fallback；用途設定不完整時要回報 unavailable/fail closed。
- SSE 要固定事件格式，至少區分 content、citations、risk、todos、drafts、callId、done/error。
- 每次呼叫要有 audit、token/usage、prompt/model metadata 與 feedback；前端能顯示引用與採納/拒絕結果。
- 模型輸出的 customerId 必須落在 caller scope；stage 必須正規化為合法 enum；客戶名稱、金額、風險不得由模型自行建立。
- AI 建議與正式 CRM 寫入要分離，任何會改資料的動作都要有人確認或走明確 API。

### G. Unit 7 的 RAG/MCP prompt 要補完整資料管線與動作防線

「上傳文件後能引用來源」仍不夠。若目標是目前 ai-crm，至少要說明：

- 文件 ingestion、文字切片、chunk metadata、embedding provider、deterministic embedding fallback、pgvector index、reindex 與失敗重試。
- 文件版本、來源、權限 scope 與引用 chunk；無相關文件時要說明查無資料，不可用模型常識硬答。
- 客戶互動、產品文件與歷史對話是不同資料來源，要分別定義檢索策略與 metadata filter。
- 長期記憶是 chat message 的時序/語意召回，不應直接把所有對話永久混成一般產品知識。
- MCP 或外部工具只負責跨系統能力；domain tool 讀 CRM 真實資料；RAG 讀非結構化文件。三者要有選用理由。
- 產生 Email、建立 Task、行事曆、報表匯出等會改變外部或 CRM 狀態的動作，要先產生 draft，再經人工確認、冪等與 audit。

### H. 新增一組 V21–V27 智慧工作流差異提示詞

這些能力不是現有 Unit 1–8 的自然結果，應另外列為「目前 ai-crm 相容擴充」：

| 擴充 | 提示詞必須帶到的關鍵內容 | 驗收重點 |
|---|---|---|
| V21 模型治理 | provider/model catalog、capability、AUTO/MANUAL/UNKNOWN、Chat/Vision/Audio 分離、DB/ENV resolution、fail closed | 不相容模型不可被指派；測試只回安全摘要，不洩漏 OCR/轉錄內容 |
| V22 任務 | OPEN/IN_PROGRESS/COMPLETED/CANCELLED、owner scope、version、postpone/complete、Asia/Taipei、RFC 5545 | stale version 回 409；`.ics` UTF-8/CRLF/UID 穩定；正式 task 與 rule recommendation 分開 |
| V23 名片 | multipart image、temporary object storage、低信心欄位、duplicate CREATE/MERGE、review→confirm | `Idempotency-Key` 重送只產生一次；Customer/Contact/Opportunity/PHONE_CALL task 原子寫入；commit 後刪原圖 |
| V24 會議 Copilot | MP3/M4A/WAV 限制、transcript、summary、stable changeId、逐項勾選、selective confirm | 只套用選取 change；低信心建議預設不選；音訊刪除但 transcript 保留 |
| V25 跟進信 | grounded draft、immutable version chain、人審後寄送、統一寄件者、Reply-To、FAILED retry | 非人審不得寄信；同 key 不重寄；SENT 不可 retry；秘密不得回傳或寫 log |
| V26 商機健康 | 純 deterministic 0–100、stage dwell/close/interaction/sentiment/task/decision-chain components、evidence、snapshot/trend | component 總和等於 total；LLM 只潤飾 next action；不得改 stage/probability |
| V27 Stakeholder | role/influence/stance/relation、AI/MANUAL、SUGGESTED/CONFIRMED/REJECTED、confirm/reject audit | confirmed 與 suggestion 分開呈現；reject 不得再當事實；跨客戶關係拒絕；suggest idempotent |

### I. Unit 8 驗收要從「完整走一次」改成「可追溯驗收矩陣」

目前的 Demo prompt 很適合教學展示，但若要證明能完成 ai-crm，應把驗收拆成至少四層：

1. **Backend contract**：migration、PostgreSQL、Testcontainers、Controller status、DTO、ProblemDetail、scope、concurrency。
2. **Frontend contract**：route、role visibility、SSE rendering、loading/error/empty、RWD、i18n、下鑽返回、表單與確認流程。
3. **AI contract**：grounding 數字、引用來源、PII mask、fallback、callId/audit、越權 customerId、無命中文件與模型不可用。
4. **Workflow contract**：V21–V27 的 upload/review/confirm、Idempotency-Key、post-commit cleanup、版本衝突、人工審核。

每一層都應留下：測試命令、HTTP/UI 斷言、原始輸出或截圖、資料庫狀態、失敗項與尚未驗證限制。`全程沒有錯誤` 不能單獨作為完成條件。

## 五、建議的提示詞改版結構

不需要把提示詞寫成很長的完整指令；建議每一個提示詞只固定以下段落：

```text
目標：本次只完成一個可驗證的能力
目前上下文：repo、既有檔案、上一階段 API/資料表、版本與執行 port
不可違反的契約：資料、權限、AI 安全、UI 狀態、不可修改範圍
實作輸出：允許新增/修改的檔案與必要 migration
驗收：命令、HTTP/UI 斷言、角色/錯誤/重試/重啟案例
交付：changed files、測試結果、證據路徑、未解決限制
```

依課程節奏可分成三層，而不是把所有功能塞進 Unit 1–8：

- **核心教學層**：保留 Unit 1–8，完成可展示的 AI CRM MVP。
- **Web App 相容層**：補 AppShell、RWD/i18n、Dashboard 個人化、Task、Workspace、Manager、AI governance 與正式 RAG 契約。
- **智慧工作流層**：新增 V21–V27 的各自 spec、migration、API、UI、權限、人工確認、冪等與 E2E 驗收。

## 六、最終判定

現有課程提示詞「能完成 AI CRM 的核心教學專案」，但「不能單獨完成目前這個 ai-crm Web App」。差異不是再補幾句「請做得完整」就能解決，而是要把提示詞從功能敘述升級成可演進的產品契約：

`版本與 repo 邊界 + domain/API 契約 + role scope + AI governance + UI state/design + workflow transaction + evidence-based verification`

最小可行的改進路線是：先保留現有 Unit 1–8 作為教學主線，再在 Unit 8 之後新增一份「ai-crm Web App 相容規格」與 V21–V27 擴充單元。這樣既不會破壞課程由淺入深的教學節奏，也能讓後續提示詞有機會真正導向目前 repo 的實際設計。

## 七、來源與限制

- 課程來源：`teaching-site/course-data.js`、`課程內容.md`、`teaching-site/AGENTS.md`。
- ai-crm 來源：`README.md`、`docs/spec.md`、`docs/api.md`、`docs/roadmap-progress.md`、`docs/ai-crm-requirements.md`、`frontend/src/App.tsx`、`frontend/src/app/AppShell.tsx`、`frontend/src/api/rest.ts`、backend controllers/services/migrations。
- 本文件是以目前檔案、程式結構與既有文件為基礎的靜態差異分析；本次沒有重新啟動 ai-crm，也沒有把測試綠燈當成所有 runtime 行為已重新驗證的證據。
- `ai-crm/docs/spec.md` 仍有 Spring Boot 3.5 的舊技術決策文字（`docs/spec.md:72-79`），但目前 `backend/pom.xml` 已是 Spring Boot 4.1.0、Spring AI 2.0.0；這本身也是後續提示詞必須先解決的版本來源衝突。
