# 教學網站圖片生成提示詞指南

> 本文件列出所有需要透過 ChatGPT 生成的圖片。
> 生成後將 PNG 檔放入 `teaching-site/assets/illustrations/` 目錄。
> 圖片已預先寫入 `course-data.js` 與 `app.js` 的連結，放入後即刻生效。

---

## 🎨 通用風格指引

請在每次 ChatGPT 生圖時，於提示詞開頭加入以下風格前綴：

```
風格：扁平化商務資訊圖表（flat infographic），白色背景，
色調以深藍（#1e3a5f）、靛藍（#4f6d7a）、橘色（#e8833a）、淺灰為主，
圖標使用簡約線條 icon 風格，文字使用繁體中文，
圖片尺寸 1200×675 像素（16:9），解析度 150 DPI，
不要出現真人照片，不要 3D 效果，不要漸層過重。
```

---

## 一、AI CRM 情境圖（每章 1 張，共 7 張）

這些圖片會顯示在各 Unit 的「🏢 AI CRM 情境應用」手風琴區塊中。

---

### 1. `crm-u1-why-crm.png`

- **所屬章節**：U1 — AI CRM 情境導入
- **對應 heading**：`為什麼選 CRM 作為實作題目`
- **用途**：說明選擇 CRM 作為全端實作題目的三大理由

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張資訊圖表，標題「為什麼選 CRM 作為實作題目」。

畫面分為三個橫向區塊，每個區塊有一個圖標與簡短說明：

1. 左欄：「領域模型清晰」
   圖標：四個相連的方塊（Customer、Opportunity、Contact、Task）
   說明：核心實體關係明確，適合學 JPA

2. 中欄：「AI 落地場景明確」
   圖標：對話泡泡 + 機器人
   說明：Tool Calling / RAG / 商機推薦

3. 右欄：「全端技術完整覆蓋」
   圖標：三層架構（前端 → API → DB）
   說明：從 React 到 Spring Boot 到 PostgreSQL

底部加一條橫幅：「三家虛擬客戶：APIM · GlobalMart · ApexFin」
```

---

### 2. `crm-u2-domain-model.png`

- **所屬章節**：U2 — CRM 領域建模
- **對應 heading**：`CRM Domain Model 設計思維`
- **用途**：展示 CRM 四大核心實體與關聯

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張 CRM 領域模型圖，標題「CRM 核心實體關聯」。

中央放一個大方塊「Customer（客戶）」，從它延伸出三條連線：
- 右上：「Contact（聯絡人）」— 標註 1:N
- 右中：「Opportunity（商機）」— 標註 1:N，內含 stage 欄位圖示
- 右下：「Interaction（互動紀錄）」— 標註 1:N

每個實體方塊內列出 2-3 個關鍵欄位（例如 Customer: name, industry, tier）。
使用不同顏色區分四個實體（深藍、橘、綠、灰藍）。
底部標註：「Domain Model → JPA Entity → REST API → AI Tool Calling」
```

---

### 3. `crm-u3-data-model.png`

- **所屬章節**：U3 — CRM 資料模型實作
- **對應 heading**：`CRM 資料模型如何對應 JPA Entity`
- **用途**：展示 Domain Model → JPA Entity → DB Table 的映射關係

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張三層映射對照圖，標題「CRM 資料模型映射」。

分為三個水平層次，用箭頭從上到下連接：

第一層「Domain Model」：
  Customer / Contact / Opportunity / Interaction 四個概念方塊

第二層「JPA Entity」：
  @Entity / @Table / @Id / @ManyToOne 標註示意

第三層「Database Table」：
  customers / contacts / opportunities / interactions 四張資料表
  標註 BIGSERIAL / REFERENCES / TIMESTAMP 等 SQL 型別

右側附註：「V1__init_schema.sql 一次建立所有資料表」
```

---

### 4. `crm-u4-security.png`

- **所屬章節**：U4 — CRM 安全設計
- **對應 heading**：`CRM 角色與權限模型`
- **用途**：展示 CRM 的三層角色權限架構

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張角色權限架構圖，標題「CRM 角色與存取控制」。

畫面分為左右兩區：

左區「角色層級」（由上到下，金字塔形）：
  - ADMIN（系統管理者）— 紅色
  - MANAGER（業務主管）— 橘色
  - SALES（業務人員）— 藍色

右區「API 存取矩陣」（表格形式）：
  | API 端點 | SALES | MANAGER | ADMIN |
  | 客戶查詢 | ✓ | ✓ | ✓ |
  | 商機管理 | 自己的 | 團隊 | 全部 |
  | 使用者管理 | ✗ | ✗ | ✓ |

底部標註：「Spring Security + JWT + @PreAuthorize」
```

---

### 5. `crm-u5-frontend.png`

- **所屬章節**：U5 — CRM 前端架構
- **對應 heading**：`CRM 工作台 UI 設計思維`
- **用途**：展示 CRM 前端四大頁面模組

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張 CRM 前端架構圖，標題「CRM 工作台 UI 模組」。

畫一個模擬的 Web 應用介面框架（Wireframe 風格），分為四個區塊：

1. 左上：「Dashboard 儀表板」— 圓餅圖 + 數字卡片
2. 右上：「客戶列表」— 表格 + 搜尋列
3. 左下：「商機看板」— Kanban 三欄（Prospecting / Proposal / Won）
4. 右下：「AI 對話助理」— 聊天介面 + SSE 串流指示器

左側有導覽列（nav bar），頂部有使用者頭像 + 登出按鈕。
底部標註：「React 19 + Vite + Proxy → Spring Boot API」
```

---

### 6. `crm-u6-ai-value.png`

- **所屬章節**：U6 — AI CRM 助理定位
- **對應 heading**：`AI CRM 助理的商業價值`
- **用途**：展示 AI 助理在 CRM 中的三層價值

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張商業價值金字塔圖，標題「AI CRM 助理的三層價值」。

金字塔由下到上分為三層（每層有圖標 + 說明）：

底層（最寬）：「資訊查詢」— 圖標：搜尋鏡
  說明：查客戶、查商機、查互動紀錄（Tool Calling）

中層：「知識檢索」— 圖標：文件 + 向量
  說明：產品文件、銷售話術、FAQ（RAG）

頂層（最窄）：「洞察建議」— 圖標：燈泡
  說明：客戶流失預警、交叉銷售建議（Agent）

右側備註框：「每一層都對應 Spring AI 的一個核心能力」
```

---

### 7. `crm-u7-knowledge.png`

- **所屬章節**：U7 — CRM 知識庫策略
- **對應 heading**：`CRM 知識庫設計：哪些文件該向量化`
- **用途**：展示 CRM 知識庫的三類文件與向量化策略

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張知識庫架構圖，標題「CRM 知識庫：哪些文件該向量化」。

畫面分為左右兩區，中間用箭頭「ETL Pipeline」連接：

左區「文件來源」（三個堆疊的文件圖標）：
  1. 互動紀錄 — 圖標：對話泡泡 — 標籤「結構化 JSON」
  2. 產品文件 — 圖標：PDF 文件 — 標籤「非結構化 PDF/MD」
  3. 銷售話術 — 圖標：麥克風 — 標籤「半結構化 FAQ」

右區「pgvector 向量庫」（圓柱體資料庫圖標）：
  內部標示 chunk、embedding、metadata 三欄
  頂部標註：「Spring AI VectorStore」

底部加流程：「業務提問 → Embedding → 相似度比對 → LLM 生成回答」
```

---

## 二、技術資訊圖表（核心觀念輔助，共 11 張）

這些圖片會顯示在各 Unit 的「核心觀念與工程判斷」手風琴中，
搭配概念卡片左側的插圖區域顯示。

> ⚠️ 以下圖片目前為 SVG 轉出的 placeholder，內容可能不正確，需用 ChatGPT **全部重新生成**。

---

### 8. `concept-environment-prep.png`

- **所屬章節**：U1 — 環境準備重點
- **對應 heading**：`環境準備重點`
- **用途**：展示課程所需的六大開發工具

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張工具清單資訊圖表，標題「開發環境準備」。

六個工具以 2×3 的網格排列，每個用圓角矩形卡片呈現，內含圖標與名稱：

1. VS Code（程式編輯與除錯）— 圖標：藍色編輯器
2. JDK 21 LTS（Java 執行環境）— 圖標：Java 咖啡杯
3. Maven 3.9+（專案建置與依賴管理）— 圖標：紅色羽毛
4. Git（版本控制與代碼管理）— 圖標：分支圖
5. PowerShell 7+（跨平台命令行終端）— 圖標：終端機
6. Docker / PostgreSQL（資料庫與容器環境）— 圖標：鯨魚 + 大象

底部加一條確認指令行：
  java -version / mvn -v / git --version / docker --version
```

---

### 9. `concept-spring-mvc-flow.png`

- **所屬章節**：U2 — Spring Boot 與 MVC 架構
- **對應 heading**：`Spring MVC 核心架構`
- **用途**：展示 HTTP 請求在 Spring MVC 中的流動路徑

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張流程圖，標題「Spring MVC 請求流程」。

從左到右的流動：
  「Client 瀏覽器」→「DispatcherServlet（前端控制器）」
    → 「Controller」→ 「Service」→ 「Repository」→ 「Database」
    ← 回應沿原路返回（虛線）

DispatcherServlet 上方標注「Spring Boot 自動設定」。
Controller 標注「@RestController / @GetMapping」。
Service 標注「@Service / 商業邏輯」。
Repository 標注「@Repository / JPA」。

底部加註：「所有 HTTP 請求都經過 DispatcherServlet 統一分派」
```

---

### 10. `concept-rest-api.png`

- **所屬章節**：U2 — REST API 設計原則
- **對應 heading**：`REST API 核心觀念`
- **用途**：展示 REST API 的四大 HTTP 方法與資源對應

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張速查表，標題「REST API 核心觀念」。

上方列出四個 HTTP 方法卡片（水平排列，不同顏色）：
  - GET（藍色）— 讀取資源
  - POST（綠色）— 新增資源
  - PUT（橘色）— 更新資源
  - DELETE（紅色）— 刪除資源

中間用一個資源 URL 範例串起：
  /api/customers          ← GET（列表）/ POST（新增）
  /api/customers/{id}     ← GET（單筆）/ PUT（更新）/ DELETE（刪除）

下方列出常用 HTTP 狀態碼（三欄）：
  200 OK / 201 Created / 204 No Content
  400 Bad Request / 404 Not Found / 500 Internal Error
```

---

### 11. `concept-jpa-mapping.png`

- **所屬章節**：U3 — JPA Entity 設計與 Repository
- **對應 heading**：`JPA 核心概念與 Entity 設計`
- **用途**：展示 Java 物件與資料庫表的映射

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張對照圖，標題「JPA：Java 物件 ↔ 資料庫表」。

左側「Java Entity」（藍色方塊）：
  @Entity
  @Table(name = "customers")
  - @Id Long id
  - @Column String name
  - @ManyToOne Customer customer

右側「Database Table」（灰色圓柱）：
  Table: customers
  - id BIGSERIAL PK
  - name VARCHAR(100)
  - customer_id BIGINT FK

中間用雙向箭頭連接，標注「ORM 映射」。

底部列出三個核心元件：
  Entity（資料結構）→ Repository（資料存取）→ Query Method（查詢方法）
```

---

### 12. `concept-flyway-strategy.png`

- **所屬章節**：U3 — Flyway 資料庫版本管理
- **對應 heading**：`Flyway 的角色`
- **用途**：展示 Flyway 版本遷移策略

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張時間線圖，標題「Flyway 版本遷移策略」。

水平時間軸從左到右：
  V1__init_schema.sql → V2__add_columns.sql → V3__xxx.sql → ...

每個版本節點下方標注：
  V1：「建立所有 CRM 表 + seed data（集中初始化）」
  V2：「後續擴充（新增欄位 / 向量表）」
  V3：「更多變更...」

上方標注 Flyway 的三個保證：
  1. ✓ 已執行的腳本不可修改（checksum）
  2. ✓ 每次啟動自動補齊缺少版本
  3. ✓ flyway_schema_history 表追蹤記錄

底部強調：「命名格式：V<版本>__<描述>.sql（雙底線！）」
```

---

### 13. `concept-aop.png`

- **所屬章節**：U4 — AOP 面向切面
- **對應 heading**：`AOP 解決了什麼問題`
- **用途**：展示 AOP 如何將橫切關注點從業務邏輯抽離

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張對比圖，標題「AOP：面向切面程式設計」。

左側「沒有 AOP」（紅色邊框）：
  三個 Service 方塊，每個內部都有重複的：
  - 日誌記錄 log
  - 權限檢查
  - 效能監控
  標註：「重複代碼散落各處」

右側「使用 AOP」（綠色邊框）：
  三個 Service 方塊（乾淨，只有商業邏輯）
  外圍一個大括弧「Aspect 切面」環繞，內含：
  - @Before — 前置通知
  - @AfterReturning — 後置通知
  - @Around — 環繞通知
  標註：「橫切關注點集中管理」

底部備註：「Spring Boot 內建 AOP：@Transactional、@Cacheable、@Async 都是 AOP」
```

---

### 14. `concept-security-chain.png`

- **所屬章節**：U4 — Spring Security 與 JWT 認證
- **對應 heading**：`安全防護重點`
- **用途**：展示 Spring Security 的過濾器鏈與 JWT 認證流程

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張流程圖，標題「Spring Security 過濾器鏈」。

從左到右的請求流動：
  「HTTP Request」→「Security Filter Chain」（多層盾牌）
    1. CORS Filter
    2. JWT Authentication Filter（解析 Token）
    3. Authorization Filter（檢查角色權限）
  → 通過後進入「Controller」

下方分支（JWT 認證流程）：
  登入：POST /api/auth/login → 驗證帳密 → 發放 JWT Token
  存取：GET /api/customers → Header: Bearer <token> → 解析驗證 → 放行

底部備註：「SecurityConfig.java 設定哪些路徑需要認證、哪些角色可以存取」
```

---

### 15. `concept-vite-proxy.png`

- **所屬章節**：U5 — Vite Proxy 前後端串接
- **對應 heading**：`開發端代理與後端 API 串接 (Vite Proxy)`
- **用途**：展示 Vite dev server 如何代理 API 請求到 Spring Boot

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張架構圖，標題「Vite Proxy 前後端串接」。

三個主要區塊（左到右）：

1.「React 前端」（藍色，port 5173）
  - 瀏覽器 → http://localhost:5173
  - 靜態頁面 + API 請求

2.「Vite Dev Server」（橘色，中間代理）
  - /api/** → 轉發到後端
  - 其他 → 回應前端資源
  標注 vite.config.js proxy 設定

3.「Spring Boot 後端」（綠色，port 8080）
  - /api/customers → JSON 回應
  - /api/auth/login → JWT Token

底部備註：「開發時前後端分離、部署時可整合為單一 JAR」
```

---

### 16. `concept-sse-stream.png`

- **所屬章節**：U6 — SSE 前端串流整合
- **對應 heading**：`為什麼選擇 SSE (Server-Sent Events)`
- **用途**：展示 SSE 串流輸出的運作方式

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張對比圖，標題「SSE：Server-Sent Events 串流」。

上半部「傳統 REST」：
  Client → Request → Server（等待完整回應）→ 一次回傳全部文字
  標注：「使用者等 5-10 秒才看到回答」（沙漏圖標）

下半部「SSE 串流」：
  Client → Request → Server
  Server 持續推送：「你」→「好」→「，」→「台積」→「電」→「...」
  標注：「逐字出現，即時回饋」（閃電圖標）

右側技術對比框：
  | | REST | SSE | WebSocket |
  | 方向 | 請求-回應 | Server→Client | 雙向 |
  | 適合 | CRUD | AI 串流 | 即時聊天 |

底部備註：「Spring AI 的 .stream() 方法原生支援 SSE」
```

---

### 17. `rag_flow.png`

- **所屬章節**：U7 — RAG 原理與 ETL 流程
- **對應 heading**：`RAG 核心概念`
- **用途**：展示 RAG（Retrieval-Augmented Generation）的完整流程

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張流程圖，標題「RAG：檢索增強生成」。

主流程（左到右，四個步驟）：

1.「使用者提問」（對話泡泡）：「這個產品的保固條款是什麼？」
2.「Embedding + 向量搜尋」（放大鏡 + 資料庫）：
   將問題轉為向量 → 在 pgvector 中搜尋最相似的文件片段
3.「Context 注入」（拼圖圖標）：
   找到的相關文件片段 + 原始問題 → 一起送給 LLM
4.「LLM 生成回答」（AI 大腦圖標）：
   根據文件內容生成有依據的回答

底部對比：
  ❌ 純 LLM：可能產生幻覺（hallucination）
  ✓ RAG：回答有文件依據，可追溯來源
```

---

### 18. `etl_pipeline.png`

- **所屬章節**：U7 — RAG 原理與 ETL 流程
- **對應 heading**：`ETL 三步驟：文件到向量庫`
- **用途**：展示文件向量化的 ETL 三階段

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張流程圖，標題「ETL Pipeline：文件到向量庫」。

三個大步驟，由左到右，每步驟用不同顏色區分：

1. Extract（擷取，藍色）：
   - PDF / Markdown / HTML → DocumentReader
   - 圖標：文件堆疊

2. Transform（轉換，橘色）：
   - 文件 → 分段（chunking）→ 每段 500-1000 字
   - 圖標：剪刀分割文件

3. Load（載入，綠色）：
   - 每段 → Embedding Model → 向量
   - 向量 → pgvector VectorStore 儲存
   - 圖標：資料庫 + 向量箭頭

底部標注 Spring AI 對應元件：
  DocumentReader → TokenTextSplitter → EmbeddingModel → VectorStore
```

---

以下為 **新增** 的技術資訊圖表：

---

### 19. `concept-bean-validation.png`

- **所屬章節**：U2 — Bean Validation 輸入驗證
- **對應 heading**：`輸入驗證：為什麼不能信任前端傳來的資料`
- **用途**：展示輸入驗證在系統架構中的位置

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張流程圖，標題「為什麼需要 Bean Validation」。

畫面從左到右：
1. 「前端」方塊 → 箭頭標「HTTP Request」
2. 「Controller」方塊（藍色），上方標 @Valid
3. 「Bean Validation」檢查站（紅色盾牌圖標）
   - ✓ 通過 → 進入 Service → Repository → DB
   - ✗ 不通過 → 返回 400 Bad Request（紅色箭頭回到前端）

右下角備註框列出常用標註：
  @NotNull / @Size / @Email / @Min / @Max

底部強調：「永遠不能只靠前端驗證！」
```

---

### 20. `concept-controller-service.png`

- **所屬章節**：U2 — Controller / Service 分層實作
- **對應 heading**：`Controller / Service 怎麼分工`
- **用途**：展示 Controller 與 Service 的職責分離

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張分層架構圖，標題「Controller / Service 分工」。

三個水平層級（由上到下），每層有職責說明：

1. 「Controller」（藍色）
   - 接收 HTTP 請求
   - 參數驗證（@Valid）
   - 回傳 HTTP 狀態碼
   圖標：路由器

2. 「Service」（橘色）
   - 商業邏輯
   - 交易管理（@Transactional）
   - 跨 Repository 協調
   圖標：齒輪

3. 「Repository」（灰色）
   - 資料存取
   - JPA / SQL 查詢
   圖標：圓柱體（資料庫）

層與層之間用向下箭頭連接，右側標注「依賴方向」
```

---

### 21. `concept-docker-postgres.png`

- **所屬章節**：U3 — Docker 與 PostgreSQL 容器化
- **對應 heading**：`為什麼資料庫要容器化`
- **用途**：展示 Docker 容器化的優勢

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張對比圖，標題「為什麼資料庫要容器化」。

左半邊「傳統安裝」（灰暗色調，打叉）：
  - 手動安裝 PostgreSQL
  - 版本衝突
  - 環境不一致
  圖標：電腦 + 錯誤符號

右半邊「Docker 容器」（明亮色調，打勾）：
  - docker-compose up -d 一鍵啟動
  - 版本鎖定（pgvector:pg18）
  - 全班環境一致
  圖標：Docker 鯨魚 + PostgreSQL 大象

中間用 VS 分隔。底部列出一行指令：
  docker-compose up -d → docker ps → 確認 Up
```

---

### 22. `concept-transactional.png`

- **所屬章節**：U3 — 交易管理
- **對應 heading**：`@Transactional 核心規則`
- **用途**：展示交易管理的 commit/rollback 流程

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張流程圖，標題「@Transactional 交易管理」。

主流程（左到右）：
  「方法開始」→「自動 BEGIN」→「執行 DB 操作」
  → 分支：
    ✓ 正常完成 →「自動 COMMIT」（綠色）
    ✗ 發生 Exception →「自動 ROLLBACK」（紅色）

下方補充三條規則（帶圖標）：
  1. 🔒 預設只 rollback RuntimeException
  2. ⚠️ 必須從「外部呼叫」才生效（代理機制）
  3. 📖 readOnly = true 讓讀取查詢更高效
```

---

### 23. `concept-specification.png`

- **所屬章節**：U3 — Specification 動態查詢
- **對應 heading**：`為什麼需要動態查詢`
- **用途**：展示動態查詢的組合特性

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張對比圖，標題「動態查詢：Specification vs Query Method」。

左邊「Query Method 的困境」（紅色邊框）：
  - findByName()
  - findByNameAndLevel()
  - findByNameAndLevelAndIndustry()
  - ... 條件越多方法越多（爆炸）

右邊「Specification 的解法」（綠色邊框）：
  - nameContains("台積")  ← 可選
  - .and(levelEquals("VIP"))  ← 可選
  - .and(industryEquals(...))  ← 可選
  標註：「null → 自動跳過，自由組合」

底部用示意 SQL 表達：
  WHERE name LIKE '%台積%' AND level = 'VIP'
```

---

### 24. `concept-openapi.png`

- **所屬章節**：U4 — OpenAPI 文件自動生成
- **對應 heading**：`為什麼需要 API 文件`
- **用途**：展示 OpenAPI / Swagger 的開發流程位置

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張流程圖，標題「OpenAPI 自動生成 API 文件」。

畫面從左到右：
  1. 「Controller + 標註」— @Operation / @Schema
  2. → 「springdoc-openapi 掃描」（自動齒輪圖標）
  3. → 「Swagger UI」（瀏覽器畫面示意）
     標註三個 URL：
     - /swagger-ui.html（互動式文件）
     - /v3/api-docs（JSON 規格）

右側補充框：「前後端分離的溝通橋樑」
- 前端看 Swagger 知道 API 格式
- 測試人員直接在 UI 上測試
- 維護成本趨近零（程式碼即文件）
```

---

### 25. `concept-global-exception.png`

- **所屬章節**：U4 — 全域例外處理
- **對應 heading**：`統一錯誤回應設計`
- **用途**：展示 @ControllerAdvice 統一攔截錯誤的機制

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張架構圖，標題「統一錯誤回應：@ControllerAdvice」。

中央大方塊「@ControllerAdvice」（橘色盾牌），
從它延伸出三條線到左側的三個 Controller：
  Controller A / Controller B / Controller C

右側顯示統一輸出格式（ProblemDetail JSON）：
  {
    "status": 404,
    "title": "客戶不存在",
    "detail": "ID=999 找不到"
  }

底部對比：
  ❌ 每個 Controller 各寫 try-catch（散落各處）
  ✓ 一個地方集中處理所有錯誤（統一格式）
```

---

### 26. `concept-chatclient.png`

- **所屬章節**：U6 — ChatClient 基礎與對話記憶
- **對應 heading**：`ChatClient 核心概念`
- **用途**：展示 Spring AI ChatClient 的 Builder 架構

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張架構圖，標題「Spring AI ChatClient 架構」。

中央是「ChatClient」方塊，從它向外延伸四個組件：

上方：「System Prompt」（定義 AI 角色）
左方：「Advisor Chain」（攔截器鏈）
  - MessageChatMemoryAdvisor（對話記憶）
  - QuestionAnswerAdvisor（RAG）
右方：「Tool Callback」（工具呼叫）
  - 查詢客戶 / 查詢商機 / 新增紀錄
下方：「Model Provider」
  - OpenAI / Groq / Ollama

箭頭方向由外向內匯入 ChatClient，底部寫：「Builder Pattern — 像積木一樣組裝 AI 能力」
```

---

### 27. `concept-tool-calling.png`

- **所屬章節**：U6 — Tool Calling 工具呼叫
- **對應 heading**：`工具呼叫核心概念`
- **用途**：展示 AI 透過 Tool Calling 存取後端 API 的完整流程

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張流程圖，標題「Tool Calling 完整流程」。

流程由左到右分為四個步驟：

1.「使用者提問」（對話泡泡）：「台積電最近有什麼商機？」
2.「LLM 判斷」（AI 大腦圖標）：決定呼叫 searchOpportunities tool
3.「Spring AI 執行」（齒輪圖標）：
   → 呼叫 OpportunityService.search("台積電")
   → 回傳 JSON 結果
4.「LLM 組合回答」（對話泡泡）：
   「台積電目前有 2 筆商機：HPC 晶片供應（PROPOSAL）...」

底部註解：「AI 不直接存取資料庫——透過你定義的 @Tool 方法間接操作」
```

---

### 28. `concept-mcp.png`

- **所屬章節**：U7 — MCP 與 Skills 擴充
- **對應 heading**：`MCP 核心概念`
- **用途**：展示 MCP 的協定架構

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張架構圖，標題「MCP：Model Context Protocol」。

左側「AI Agent」大方塊，右側列出多個「MCP Server」小方塊：
  - 📁 檔案系統 Server
  - 🗄️ 資料庫 Server
  - 🔍 搜尋引擎 Server
  - 📧 Email Server
  - 🏢 CRM Server

中間用雙向箭頭「MCP 協定」連接，標注「JSON-RPC / SSE」。

底部對比框：
  Tool Calling：一對一綁定（硬編碼）
  MCP：標準協定（即插即用，跨工具通用）
```

---

### 29. `concept-conversation-memory.png`

- **所屬章節**：U7 — 對話歷史長期記憶
- **對應 heading**：`為什麼需要對話歷史 RAG`
- **用途**：展示短期 vs 長期記憶的差異

**ChatGPT 提示詞：**

```
[加入風格前綴]

請繪製一張對比圖，標題「對話記憶：短期 vs 長期」。

左半邊「短期記憶」（InMemory）：
  - 圖標：RAM 晶片
  - 特點：本次對話內的上下文
  - 限制：重啟即消失、Token 上限
  - 適合：單次對話延續

右半邊「長期記憶」（pgvector RAG）：
  - 圖標：資料庫 + 向量
  - 特點：跨次對話的歷史檢索
  - 優勢：語意搜尋相關對話
  - 適合：「上次我們討論過的客戶...」

底部流程：「新對話 → Embedding → 搜尋相似歷史 → 注入 Context → LLM 回答」
```

---

## 三、檔名速查表

### CRM 情境圖（7 張）

| # | 檔名 | 章節 | 對應 heading |
|---|------|------|--------------|
| 1 | `crm-u1-why-crm.png` | U1 | 為什麼選 CRM 作為實作題目 |
| 2 | `crm-u2-domain-model.png` | U2 | CRM Domain Model 設計思維 |
| 3 | `crm-u3-data-model.png` | U3 | CRM 資料模型如何對應 JPA Entity |
| 4 | `crm-u4-security.png` | U4 | CRM 角色與權限模型 |
| 5 | `crm-u5-frontend.png` | U5 | CRM 工作台 UI 設計思維 |
| 6 | `crm-u6-ai-value.png` | U6 | AI CRM 助理的商業價值 |
| 7 | `crm-u7-knowledge.png` | U7 | CRM 知識庫設計：哪些文件該向量化 |

### 既有技術概念圖（需重新生成，11 張）

| # | 檔名 | 章節 | 對應 heading |
|---|------|------|--------------|
| 8 | `concept-environment-prep.png` | U1 | 環境準備重點 |
| 9 | `concept-spring-mvc-flow.png` | U2 | Spring MVC 核心架構 |
| 10 | `concept-rest-api.png` | U2 | REST API 核心觀念 |
| 11 | `concept-jpa-mapping.png` | U3 | JPA 核心概念與 Entity 設計 |
| 12 | `concept-flyway-strategy.png` | U3 | Flyway 的角色 |
| 13 | `concept-aop.png` | U4 | AOP 解決了什麼問題 |
| 14 | `concept-security-chain.png` | U4 | 安全防護重點 |
| 15 | `concept-vite-proxy.png` | U5 | 開發端代理與後端 API 串接 (Vite Proxy) |
| 16 | `concept-sse-stream.png` | U6 | 為什麼選擇 SSE (Server-Sent Events) |
| 17 | `rag_flow.png` | U7 | RAG 核心概念 |
| 18 | `etl_pipeline.png` | U7 | ETL 三步驟：文件到向量庫 |

### 新增技術概念圖（11 張）

| # | 檔名 | 章節 | 對應 heading |
|---|------|------|--------------|
| 19 | `concept-bean-validation.png` | U2 | 輸入驗證：為什麼不能信任前端傳來的資料 |
| 20 | `concept-controller-service.png` | U2 | Controller / Service 怎麼分工 |
| 21 | `concept-docker-postgres.png` | U3 | 為什麼資料庫要容器化 |
| 22 | `concept-transactional.png` | U3 | @Transactional 核心規則 |
| 23 | `concept-specification.png` | U3 | 為什麼需要動態查詢 |
| 24 | `concept-openapi.png` | U4 | 為什麼需要 API 文件 |
| 25 | `concept-global-exception.png` | U4 | 統一錯誤回應設計 |
| 26 | `concept-chatclient.png` | U6 | ChatClient 核心概念 |
| 27 | `concept-tool-calling.png` | U6 | 工具呼叫核心概念 |
| 28 | `concept-mcp.png` | U7 | MCP 核心概念 |
| 29 | `concept-conversation-memory.png` | U7 | 為什麼需要對話歷史 RAG |
