/**
 * 精準版：只在 concepts 陣列內的 heading 後面插入 group 欄位。
 * 使用括號匹配追蹤 JSON 結構深度，確保只在 concepts 陣列中操作。
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DATA_PATH = path.resolve(__dirname, "..", "course-data.js");

/** 各概念 heading → group 名稱對應表 */
const GROUP_MAP = {
  // ─── U1 ───
  "環境準備重點": "環境準備重點",
  "請 AI Agent 幫你安裝 JDK 21": "開發工具安裝與驗證",
  "請 AI Agent 幫你安裝 Maven": "開發工具安裝與驗證",
  "請 AI Agent 幫你安裝 Git": "開發工具安裝與驗證",
  "VS Code 必要外掛清單": "開發工具安裝與驗證",
  "PowerShell 驗證命令": "開發工具安裝與驗證",
  "驗證結果範例與判讀方式": "開發工具安裝與驗證",
  "常見錯誤與排查": "常見錯誤與排查",
  "透過 Spring Initializr 建立課程專案": "建立課程專案",
  "確認專案結構與首次啟動": "建立課程專案",
  "AI 協作的適用時機": "AI 協作的適用時機",
  // ─── U2 ───
  "Spring Boot 為什麼適合教學起點": "Spring Boot 與 MVC 架構",
  "Spring MVC 的核心：請求如何流動": "Spring Boot 與 MVC 架構",
  "IoC 與 DI 的實務解釋": "Spring Boot 與 MVC 架構",
  "什麼是 REST API": "REST API 設計原則",
  "HTTP 方法與 CRUD 對應": "REST API 設計原則",
  "HTTP 狀態碼速查": "REST API 設計原則",
  "請求與回應的結構": "REST API 設計原則",
  "Controller / Service 怎麼分工": "Controller / Service 分層實作",
  "用 AI Agent 建立可獨立運行的示範專案": "Controller / Service 分層實作",
  "Lombok：省去樣板程式碼": "Controller / Service 分層實作",
  "CustomerService（記憶體版）": "Controller / Service 分層實作",
  "CustomerController（記憶體版）": "Controller / Service 分層實作",
  "輸入驗證：為什麼不能信任前端傳來的資料": "Bean Validation 輸入驗證",
  "在 Model 加上 Bean Validation 標註": "Bean Validation 輸入驗證",
  "在 Controller 加上 @Valid 觸發驗證": "Bean Validation 輸入驗證",
  "驗證失敗時的回應格式": "Bean Validation 輸入驗證",
  "常用 Bean Validation 標註速查": "Bean Validation 輸入驗證",
  "AI 提示詞練習": "AI 提示詞練習",
  // ─── U3 ───
  "為什麼資料庫要容器化": "Docker 與 PostgreSQL 容器化",
  "安裝 Docker Desktop": "Docker 與 PostgreSQL 容器化",
  "Docker 啟動指令": "Docker 與 PostgreSQL 容器化",
  "資料庫驗證範例與判讀方式": "Docker 與 PostgreSQL 容器化",
  "用 AI Agent 產生 docker-compose.yml": "Docker 與 PostgreSQL 容器化",
  "docker-compose.yml 設定說明": "Docker 與 PostgreSQL 容器化",
  "Flyway 的角色": "Flyway 資料庫版本管理",
  "版本管理思維": "Flyway 資料庫版本管理",
  "Flyway 命名規則與雙底線陷阱": "Flyway 資料庫版本管理",
  "ddl-auto 設定與開發階段策略": "Flyway 資料庫版本管理",
  "為什麼正式環境不能靠 ddl-auto": "Flyway 資料庫版本管理",
  "Flyway vs ddl-auto 職責對照": "Flyway 資料庫版本管理",
  "用 AI Agent 設定 application.yml 資料庫連線": "資料庫連線配置",
  "application.yml 資料庫連線配置": "資料庫連線配置",
  "JPA 解決了什麼問題": "JPA Entity 設計與 Repository",
  "Entity 設計要點": "JPA Entity 設計與 Repository",
  "Product.java Entity 完整範例": "JPA Entity 設計與 Repository",
  "Lombok 與 JPA 的搭配注意事項": "JPA Entity 設計與 Repository",
  "Repository 與 Query Method": "JPA Entity 設計與 Repository",
  "Query Method 命名規則分解": "JPA Entity 設計與 Repository",
  "@Transactional：寫入操作一定要加": "交易管理 (@Transactional)",
  "@Transactional 標註規範與 Service 完整範例": "交易管理 (@Transactional)",
  "readOnly = true 對效能的實際影響": "交易管理 (@Transactional)",
  "@Query 改資料必須同時加上 @Modifying": "交易管理 (@Transactional)",
  "@Modifying + @Query 完整範例": "交易管理 (@Transactional)",
  "clearAutomatically：不加會有幽靈快取問題": "交易管理 (@Transactional)",
  "@Modifying 批次刪除 vs 派生刪除的選用時機": "交易管理 (@Transactional)",
  "Audit 欄位：自動記錄建立與修改時間": "Audit 稽核欄位",
  "BaseAuditEntity：把 Audit 欄位抽成共用父類別": "Audit 稽核欄位",
  "Entity 繼承 BaseAuditEntity": "Audit 稽核欄位",
  "Flyway 遷移腳本需同步新增 Audit 欄位": "Audit 稽核欄位",
  "建立者與修改者欄位（需搭配 Spring Security，本課程僅說明）": "Audit 稽核欄位",
  "AuditorAware 結構說明（示意，非本課程完整實作）": "Audit 稽核欄位",
  "Query Method 的限制：條件一多就爆炸": "Specification 動態查詢",
  "Specification：動態查詢的正確解法": "Specification 動態查詢",
  "步驟一：Repository 加入 JpaSpecificationExecutor": "Specification 動態查詢",
  "步驟二：建立 CustomerSpec 條件工廠": "Specification 動態查詢",
  "步驟三：在 Service 動態組合條件": "Specification 動態查詢",
  "步驟四：Controller 接收查詢參數": "Specification 動態查詢",
  "Specification 產生的 SQL 實際長什麼樣": "Specification 動態查詢",
  "Specification 與 Query Method 的選用時機": "Specification 動態查詢",
  "用 AI Agent 為既有專案加入 JPA": "Specification 動態查詢",
  "這一章為什麼重要": "Specification 動態查詢",
  // ─── U4 ───
  "為什麼需要 API 文件": "OpenAPI 文件自動生成",
  "加入 springdoc-openapi 依賴": "OpenAPI 文件自動生成",
  "啟動後的預設存取路徑": "OpenAPI 文件自動生成",
  "用標註豐富 API 說明": "OpenAPI 文件自動生成",
  "設定全域 API 資訊": "OpenAPI 文件自動生成",
  "沒有全域例外處理的問題": "全域例外處理",
  "建立統一的 ErrorResponse 格式": "全域例外處理",
  "自訂業務例外類別": "全域例外處理",
  "建立 GlobalExceptionHandler": "全域例外處理",
  "例外處理回應對照": "全域例外處理",
  "ProblemDetail：Spring Boot 3 內建標準格式": "全域例外處理",
  "Spring Boot 預設 Log 機制": "Log 日誌管理",
  "@Slf4j 與結構化 Log 寫法": "Log 日誌管理",
  "application.yml 設定 Log 層級": "Log 日誌管理",
  "動態調整 Log 層級（Spring Actuator）": "Log 日誌管理",
  "Log 最佳實踐": "Log 日誌管理",
  "AOP 解決了什麼問題": "AOP 面向切面",
  "Spring AOP 五大核心詞彙": "AOP 面向切面",
  "AOP 概念圖解": "AOP 面向切面",
  "Spring AOP 的實現方式：Proxy": "AOP 面向切面",
  "直接撰寫 AOP 的寫法（效能監控範例）": "AOP 面向切面",
  "很少直接寫 AOP 的原因": "AOP 面向切面",
  "Day 1 哪些功能背後用了 AOP": "AOP 面向切面",
  "安全防護重點": "Spring Security 與 JWT 認證",
  "請 AI Agent 幫你安裝 Security 與 JWT 依賴": "Spring Security 與 JWT 認證",
  "AI Agent 提示詞 — 身分驗證與角色授權實作": "Spring Security 與 JWT 認證",
  "後端安全設定範例 (SecurityConfig.java)": "Spring Security 與 JWT 認證",
  "JWT 工具類別範例 (JwtUtils.java)": "Spring Security 與 JWT 認證",
  "JWT 身分驗證過濾器範例 (JwtAuthenticationFilter.java)": "Spring Security 與 JWT 認證",
  "登入控制器範例 (AuthController.java)": "Spring Security 與 JWT 認證",
  "Swagger 網頁驗證步驟（推薦）": "Spring Security 與 JWT 認證",
  "PowerShell 驗證命令（可選）": "Spring Security 與 JWT 認證",
  // ─── U5 ───
  "Node.js 與 React 專案建立": "Node.js 與 React 專案建立",
  "JSX 語法與 Functional Component 元件結構": "JSX 語法與元件結構",
  "開發端代理與後端 API 串接 (Vite Proxy)": "Vite Proxy 前後端串接",
  "uiuxpromax 前端視覺優化指引": "前端視覺優化指引",
  "AI Agent 提示詞 — 建立 React 前端專案": "AI 提示詞 — React 前端",
  // ─── U6 ───
  "ChatClient 在架構中的位置": "ChatClient 基礎與對話記憶",
  "串流輸出為什麼重要": "ChatClient 基礎與對話記憶",
  "對話記憶設計": "ChatClient 基礎與對話記憶",
  "ChatClient.Builder 初始化方式": "ChatClient 基礎與對話記憶",
  "Groq API 端點設定（application.yml）": "ChatClient 基礎與對話記憶",
  "MessageChatMemoryAdvisor 對話記憶": "ChatClient 基礎與對話記憶",
  "ChatClient 與 Advisor 深入解析": "ChatClient 基礎與對話記憶",
  "串流回應骨架": "ChatClient 基礎與對話記憶",
  "AI Agent 提示詞 — 建立 ChatClient 對話入口": "ChatClient 基礎與對話記憶",
  "Spring Boot 啟動驗證範例": "ChatClient 基礎與對話記憶",
  "啟動日誌判讀方式": "ChatClient 基礎與對話記憶",
  "工具呼叫的真正價值": "Tool Calling 工具呼叫",
  "@Tool 與 ToolCallback 的使用邏輯": "Tool Calling 工具呼叫",
  "CustomerTools.java 完整範例": "Tool Calling 工具呼叫",
  "@Tool vs ToolCallback 選型原則": "Tool Calling 工具呼叫",
  "MethodToolCallback 包裝第三方工具": "Tool Calling 工具呼叫",
  "FunctionToolCallback 包裝 Lambda 邏輯": "Tool Calling 工具呼叫",
  "工具方法範例": "Tool Calling 工具呼叫",
  "AI Agent 提示詞 — 加入工具呼叫能力": "Tool Calling 工具呼叫",
  "工具呼叫驗證範例": "Tool Calling 工具呼叫",
  "工具呼叫的架構定位": "Tool Calling 工具呼叫",
  "為什麼選擇 SSE (Server-Sent Events)": "SSE 前端串流整合",
  "React EventSource 串接核心代碼": "SSE 前端串流整合",
  "流式打字機效果與自動滾動": "SSE 前端串流整合",
  "完整的 ChatRoom.jsx 元件範例": "SSE 前端串流整合",
  "對應的 CSS 樣式支援 (ChatRoom.css)": "SSE 前端串流整合",
  "對話中客戶摘要、商機、建議行動的卡片元件呈現方式": "SSE 前端串流整合",
  "AI Agent 提示詞 — 前端串流與安全認證": "SSE 前端串流整合",
  "系統運行展示：AI 基礎對話畫面": "系統運行展示",
  "系統運行展示：客戶資訊查詢卡片": "系統運行展示",
  "系統運行展示：商機與銷售進度查詢": "系統運行展示",
  "系統運行展示：長期記憶語意客戶推薦": "系統運行展示",
  // ─── U7 ───
  "RAG 的基本想法": "RAG 原理與 ETL 流程",
  "RAG 與 Fine-Tuning 差異": "RAG 原理與 ETL 流程",
  "RAG vs Fine-Tuning 完整比較": "RAG 原理與 ETL 流程",
  "ETL 三步驟：文件到向量庫": "RAG 原理與 ETL 流程",
  "向量嵌入與 pgvector": "RAG 原理與 ETL 流程",
  "ETL 流程程式碼": "RAG 原理與 ETL 流程",
  "QuestionAnswerAdvisor 的價值": "RAG 原理與 ETL 流程",
  "AI Agent 提示詞 — 建立 RAG 知識庫": "RAG 原理與 ETL 流程",
  "RAG 上傳與問答驗證範例": "RAG 原理與 ETL 流程",
  "MCP 應該怎麼理解": "MCP 與 Skills 擴充",
  "MCP 在 Spring AI 的接入方式（補充說明）": "MCP 與 Skills 擴充",
  "AI 呼叫遠端工具的完整流程": "MCP 與 Skills 擴充",
  "Skills 是什麼：把專業知識打包給 AI": "MCP 與 Skills 擴充",
  "MCP vs Skills：擴充 AI 能力的兩條路": "MCP 與 Skills 擴充",
  "Spring AI 如何加入 Skills：spring-ai-agent-utils": "MCP 與 Skills 擴充",
  "spring-ai-agent-utils 提供的 Agent 工具總覽": "MCP 與 Skills 擴充",
  "加入 spring-ai-agent-utils 依賴（pom.xml）": "MCP 與 Skills 擴充",
  "ChatClient 掛上 SkillsTool 與 Agent 工具": "MCP 與 Skills 擴充",
  "為什麼需要對話歷史 RAG": "對話歷史長期記憶",
  "串流結束時非同步寫入向量庫": "對話歷史長期記憶",
  "對話歷史向量化 Service 實作": "對話歷史長期記憶",
  "雙路 RAG 檢索與上下文合併": "對話歷史長期記憶",
  "AI Agent 提示詞 — 對話歷史長期記憶": "對話歷史長期記憶",
  // ─── U8 ───
  "結訓整合的三大挑戰": "結訓整合挑戰",
  "端到端測試案例設計": "測試策略",
  "AI 功能的測試策略": "測試策略",
  "上線檢查清單與效能調校": "上線準備",
  "Demo Day 展示準備": "Demo Day 展示準備",
};

// ── 讀取檔案 ──
const content = fs.readFileSync(DATA_PATH, "utf-8");
const lines = content.split("\n");
const newLines = [];
let insideConceptsArray = false;
let bracketDepth = 0;
let insertCount = 0;

for (let i = 0; i < lines.length; i++) {
  const line = lines[i];
  const trimmed = line.trim();

  // 偵測進入 "concepts" 陣列
  if (trimmed.startsWith('"concepts"') && trimmed.includes("[")) {
    insideConceptsArray = true;
    bracketDepth = 0;
    // 計算此行的方括號深度
    for (const ch of line) {
      if (ch === "[") bracketDepth++;
      if (ch === "]") bracketDepth--;
    }
    newLines.push(line);
    continue;
  }

  // 追蹤方括號深度
  if (insideConceptsArray) {
    for (const ch of line) {
      if (ch === "[") bracketDepth++;
      if (ch === "]") bracketDepth--;
    }
    if (bracketDepth <= 0) {
      insideConceptsArray = false;
    }
  }

  newLines.push(line);

  // 只在 concepts 陣列內處理 heading
  if (insideConceptsArray) {
    const headingMatch = line.match(/^(\s*)"heading":\s*"(.+?)"/);
    if (headingMatch) {
      const indent = headingMatch[1];
      const heading = headingMatch[2];

      // 確認下一行不是已有的 "group"
      const nextLine = lines[i + 1] || "";
      if (!nextLine.trim().startsWith('"group"')) {
        let groupName = GROUP_MAP[heading] || heading;
        const escaped = groupName.replace(/\\/g, "\\\\").replace(/"/g, '\\"');
        newLines.push(`${indent}"group": "${escaped}",`);
        insertCount++;
      }
    }
  }
}

fs.writeFileSync(DATA_PATH, newLines.join("\n"), "utf-8");
console.log(`📝 已在 concepts 陣列中插入 ${insertCount} 個 group 欄位`);

// 驗證
const verifyContent = fs.readFileSync(DATA_PATH, "utf-8");
const groupCount = (verifyContent.match(/"group"/g) || []).length;
console.log(`✅ 驗證：檔案中共有 ${groupCount} 個 group 欄位`);
