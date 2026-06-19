/**
 * 課程內容精簡腳本：合併概念、移除過度展開的程式碼範例、新增 AI CRM 觀念。
 * 
 * 策略：
 * 1. 讀取現有 course-data.js 物件
 * 2. 對每個 Unit 的 concepts 陣列進行轉換
 * 3. 合併同群組中過於碎片化的概念
 * 4. 精簡 body 中的完整程式碼（保留概念性描述與關鍵片段）
 * 5. 新增 AI CRM 情境概念
 * 6. 寫回 course-data.js
 */
import fs from "fs";

const DATA_PATH = "course-data.js";
const content = fs.readFileSync(DATA_PATH, "utf-8");
const window = {};
eval(content);
const course = window.COURSE;

/**
 * 輔助函式：將多個概念合併為一個概念
 * @param {string} heading - 新標題
 * @param {string} group - 群組名稱
 * @param {Array} concepts - 要合併的概念陣列
 * @param {object} options - 額外選項
 * @returns {object} 合併後的概念
 */
function mergeConcepts(heading, group, concepts, options = {}) {
  const bodies = concepts
    .filter((c) => c.body)
    .map((c) => {
      // 如果有多個概念，在每段前加上小標題
      if (concepts.length > 1 && c.heading) {
        return `**${c.heading}**\n\n${c.body}`;
      }
      return c.body;
    });

  const merged = {
    heading,
    group,
    body: options.body || bodies.join("\n\n---\n\n"),
  };

  // 合併 list、table、note
  const lists = concepts.filter((c) => c.list?.length).flatMap((c) => c.list);
  if (lists.length) merged.list = lists;

  const tables = concepts.find((c) => c.table);
  if (tables) merged.table = tables.table;

  const notes = concepts.filter((c) => c.note).map((c) => c.note);
  if (notes.length) merged.note = notes.join("\n\n");

  return merged;
}

/**
 * 精簡 body 中的完整程式碼區塊
 * 保留概念性描述，將大段程式碼替換為摘要
 */
function trimCodeBlocks(body, maxCodeLines = 15) {
  if (!body) return body;
  return body.replace(/```(\w*)\n([\s\S]*?)```/g, (match, lang, code) => {
    const lines = code.split("\n");
    if (lines.length <= maxCodeLines) return match;
    // 保留前幾行 + 註解 + 最後幾行
    const kept = [
      ...lines.slice(0, 8),
      `// ... 完整程式碼請參考課程 GitHub 專案 ...`,
      ...lines.slice(-3),
    ].join("\n");
    return "```" + lang + "\n" + kept + "```";
  });
}

// ═══════════════════════════════════════════
// U1：開發環境（11 → 6）
// ═══════════════════════════════════════════
function transformU1(concepts) {
  const result = [];

  // 新增：為什麼選 CRM 作為實作題目
  result.push({
    heading: "為什麼選 CRM 作為實作題目",
    group: "AI CRM 情境導入",
    body: "CRM（Customer Relationship Management）是企業最常見的核心系統之一，涵蓋客戶管理、商機追蹤、互動紀錄與任務分派。選擇 CRM 作為課程實作題目，有三個關鍵理由：\n\n**1. 領域模型清晰且貼近真實**\nCRM 的核心實體（客戶 Customer、商機 Opportunity、互動紀錄 Activity、任務 Task）關係明確，適合學習 JPA Entity 設計、關聯映射與動態查詢。\n\n**2. AI 能力有明確落地場景**\n業務人員需要快速了解客戶現況、預測流失風險、產生行動建議——這些都是 AI 助理能直接創造價值的場景。Tool Calling 可以讓 AI 讀取真實 CRM 資料，RAG 可以讓 AI 搜尋歷史互動紀錄與產品文件。\n\n**3. 全端技術棧完整覆蓋**\n從後端 REST API、資料庫設計、安全認證，到前端 Dashboard、即時對話介面，CRM 系統能自然地串起本課程所有技術主題。\n\n本課程的三家虛擬客戶（亞太智能製造、環球零售巨擘、鼎峰金融科技）代表了 B2B CRM 的三種典型業務情境：高價值活躍客戶、流失風險客戶、續約延遲客戶。",
  });

  // 保留：環境準備重點
  const envPrep = concepts.find((c) => c.heading === "環境準備重點");
  if (envPrep) result.push(envPrep);

  // 合併：工具安裝 AI 提示詞（JDK + Maven + Git + VS Code）
  const installConcepts = concepts.filter((c) =>
    ["請 AI Agent 幫你安裝 JDK 21", "請 AI Agent 幫你安裝 Maven", "請 AI Agent 幫你安裝 Git", "VS Code 必要外掛清單"].includes(c.heading)
  );
  if (installConcepts.length) {
    result.push({
      heading: "用 AI Agent 安裝開發工具",
      group: "開發工具安裝與驗證",
      body: "本課程需要 JDK 21、Maven 3.9+、Git 與 VS Code（含 Java / Spring 擴充套件）。以下提供三組 AI Agent 提示詞，讓 AI 直接在 PowerShell 中完成安裝與環境變數設定。\n\n**JDK 21 安裝提示詞**\n```text\n我使用 Windows 11，請用 winget 幫我安裝 Eclipse Temurin JDK 21。\n安裝完成後，請設定 JAVA_HOME 環境變數（永久生效），\n確認 Path 中已包含 JDK bin 目錄，並執行 java -version 驗證。\n```\n\n**Maven 安裝提示詞**\n```text\n請用 winget 幫我安裝 Apache Maven 最新版。\n設定 M2_HOME 與 Path（永久生效），確認 Maven 使用 JDK 21，\n執行 mvn -version 顯示完整結果。\n```\n\n**Git 安裝提示詞**\n```text\n請用 winget 幫我安裝 Git，完成後設定 user.name 與 user.email，\n並執行 git --version 確認安裝成功。\n```\n\n**VS Code 必要擴充套件**\n- Extension Pack for Java\n- Spring Boot Extension Pack\n- 確認 Java 擴充套件已啟用內建 Lombok 支援",
    });
  }

  // 合併：驗證與排錯
  const verifyConcepts = concepts.filter((c) =>
    ["PowerShell 驗證命令", "驗證結果範例與判讀方式", "常見錯誤與排查"].includes(c.heading)
  );
  if (verifyConcepts.length) {
    result.push({
      heading: "環境驗證與常見問題",
      group: "開發工具安裝與驗證",
      body: "安裝完成後，在 VS Code 終端機中執行以下命令確認環境：\n\n```powershell\njava -version    # 應顯示 openjdk version \"21.x.x\"\nmvn -version     # 應顯示 Apache Maven 3.9.x，Java version: 21\ngit --version    # 應顯示 git version 2.x.x\n```\n\n**判讀重點**\n- Java 版本必須是 21，若不是代表 `JAVA_HOME` 或 `Path` 設定有誤\n- Maven 顯示的 Java version 也必須是 21，否則 Maven 沒有指向正確的 JDK\n- 若出現「不是內部或外部命令」，通常是 PATH 尚未設定或尚未重新載入終端機\n\n**常見錯誤**\n- VS Code 終端機與系統終端機顯示不同版本：重新啟動 VS Code\n- Spring Boot Extension Pack 無補全：重建 Java Language Server",
    });
  }

  // 合併：建立課程專案
  const projectConcepts = concepts.filter((c) =>
    ["透過 Spring Initializr 建立課程專案", "確認專案結構與首次啟動"].includes(c.heading)
  );
  if (projectConcepts.length) {
    result.push(mergeConcepts("建立課程專案", "建立課程專案", projectConcepts));
  }

  // 保留：AI 協作的適用時機
  const aiCollab = concepts.find((c) => c.heading === "AI 協作的適用時機");
  if (aiCollab) result.push(aiCollab);

  return result;
}

// ═══════════════════════════════════════════
// U2：Spring MVC / REST API（18 → 9）
// ═══════════════════════════════════════════
function transformU2(concepts) {
  const result = [];

  // 合併：Spring MVC 核心架構
  const mvcConcepts = concepts.filter((c) =>
    ["Spring Boot 為什麼適合教學起點", "Spring MVC 的核心：請求如何流動", "IoC 與 DI 的實務解釋"].includes(c.heading)
  );
  if (mvcConcepts.length) {
    result.push(mergeConcepts("Spring MVC 核心架構", "Spring Boot 與 MVC 架構", mvcConcepts));
  }

  // 合併：REST 核心觀念
  const restCore = concepts.filter((c) =>
    ["什麼是 REST API", "請求與回應的結構"].includes(c.heading)
  );
  if (restCore.length) {
    result.push(mergeConcepts("REST API 核心觀念", "REST API 設計原則", restCore));
  }

  // 保留：HTTP 方法與狀態碼速查（合併兩個表）
  const httpConcepts = concepts.filter((c) =>
    ["HTTP 方法與 CRUD 對應", "HTTP 狀態碼速查"].includes(c.heading)
  );
  if (httpConcepts.length) {
    result.push(mergeConcepts("HTTP 方法與狀態碼速查", "REST API 設計原則", httpConcepts));
  }

  // 精簡：Controller / Service 分層（保留概念，精簡程式碼）
  const layerConcept = concepts.find((c) => c.heading === "Controller / Service 怎麼分工");
  if (layerConcept) {
    result.push({ ...layerConcept, group: "Controller / Service 分層實作" });
  }

  const lombok = concepts.find((c) => c.heading === "Lombok：省去樣板程式碼");
  if (lombok) {
    result.push({ ...lombok, group: "Controller / Service 分層實作" });
  }

  // AI 提示詞
  const aiDemo = concepts.find((c) => c.heading === "用 AI Agent 建立可獨立運行的示範專案");
  if (aiDemo) {
    result.push({ ...aiDemo, group: "Controller / Service 分層實作" });
  }

  // 合併：Bean Validation（精簡版）
  const validConcepts = concepts.filter((c) =>
    ["輸入驗證：為什麼不能信任前端傳來的資料", "在 Model 加上 Bean Validation 標註", "在 Controller 加上 @Valid 觸發驗證", "驗證失敗時的回應格式", "常用 Bean Validation 標註速查", "AI 提示詞練習"].includes(c.heading)
  );
  if (validConcepts.length) {
    // 保留「為什麼」概念 + 標註速查表
    const whyConcept = validConcepts.find((c) => c.heading.includes("為什麼"));
    const speedRef = validConcepts.find((c) => c.heading.includes("速查"));
    const aiPrompt = validConcepts.find((c) => c.heading === "AI 提示詞練習");
    const kept = [whyConcept, speedRef, aiPrompt].filter(Boolean);
    kept.forEach((c) => result.push({ ...c, group: "Bean Validation 輸入驗證" }));
  }

  // 新增：CRM Domain Model 設計思維
  result.push({
    heading: "CRM Domain Model 設計思維",
    group: "CRM 領域建模",
    body: "AI CRM 系統的資料模型圍繞四個核心實體展開，每個實體都對應真實的業務操作：\n\n**Customer（客戶）**\n記錄企業基本資料、產業類型、合約狀態與聯繫人資訊。是整個 CRM 的核心，所有商機與互動都掛在客戶底下。\n\n**Opportunity（商機）**\n追蹤每一筆潛在或進行中的銷售機會，包含預估金額、成交機率、目前階段（Prospecting → Qualification → Proposal → Closing）與預計結案日。\n\n**Activity（互動紀錄）**\n記錄業務人員與客戶的每一次接觸：會議、電話、郵件、拜訪。這些紀錄將被向量化存入 pgvector，成為 AI 助理的長期記憶來源。\n\n**Task（待辦任務）**\n由 AI 助理根據互動分析自動建議，或由業務人員手動建立的行動項目。\n\n這四個實體的關係：Customer → (1:N) → Opportunity / Activity / Task。在後續章節中，我們會用 JPA Entity 實作這個模型，並透過 Specification 支援動態查詢。",
  });

  return result;
}

// ═══════════════════════════════════════════
// U3：PostgreSQL / Flyway / JPA（45 → 18）
// ═══════════════════════════════════════════
function transformU3(concepts) {
  const result = [];

  // Docker 容器化（7 → 2）
  const dockerWhy = concepts.find((c) => c.heading === "為什麼資料庫要容器化");
  if (dockerWhy) result.push({ ...dockerWhy, group: "Docker 與 PostgreSQL 容器化" });

  const dockerAI = concepts.find((c) => c.heading === "用 AI Agent 產生 docker-compose.yml");
  if (dockerAI) result.push({ ...dockerAI, group: "Docker 與 PostgreSQL 容器化" });

  // Flyway 版本管理（6 → 3）
  const flywayRole = concepts.find((c) => c.heading === "Flyway 的角色");
  if (flywayRole) result.push({ ...flywayRole, group: "Flyway 資料庫版本管理" });

  const flywayNaming = concepts.find((c) => c.heading === "Flyway 命名規則與雙底線陷阱");
  if (flywayNaming) result.push({ ...flywayNaming, group: "Flyway 資料庫版本管理" });

  const flywayCompare = concepts.find((c) => c.heading === "Flyway vs ddl-auto 職責對照");
  if (flywayCompare) result.push({ ...flywayCompare, group: "Flyway 資料庫版本管理" });

  // 資料庫連線（3 → 1）
  const dbAI = concepts.find((c) => c.heading === "用 AI Agent 設定 application.yml 資料庫連線");
  if (dbAI) result.push({ ...dbAI, group: "資料庫連線配置" });

  // JPA Entity（6 → 3）
  const jpaConcepts = concepts.filter((c) =>
    ["JPA 解決了什麼問題", "Entity 設計要點"].includes(c.heading)
  );
  if (jpaConcepts.length) {
    result.push(mergeConcepts("JPA 核心概念與 Entity 設計", "JPA Entity 設計與 Repository", jpaConcepts));
  }

  const lombokJpa = concepts.find((c) => c.heading === "Lombok 與 JPA 的搭配注意事項");
  if (lombokJpa) result.push({ ...lombokJpa, group: "JPA Entity 設計與 Repository" });

  const qmConcepts = concepts.filter((c) =>
    ["Repository 與 Query Method", "Query Method 命名規則分解"].includes(c.heading)
  );
  if (qmConcepts.length) {
    result.push(mergeConcepts("Repository 與 Query Method", "JPA Entity 設計與 Repository", qmConcepts));
  }

  // 交易管理（7 → 2）
  const txCore = concepts.filter((c) =>
    ["@Transactional：寫入操作一定要加", "@Transactional 標註規範與 Service 完整範例", "readOnly = true 對效能的實際影響"].includes(c.heading)
  );
  if (txCore.length) {
    result.push({
      heading: "@Transactional 核心規則",
      group: "交易管理 (@Transactional)",
      body: "Spring 的 `@Transactional` 是寫入操作的必備標註，核心規則：\n\n- **寫入方法一定要加 `@Transactional`**：新增、修改、刪除若沒有包在交易中，部分失敗時資料會處於不一致狀態\n- **查詢方法加 `readOnly = true`**：告知 JPA 不需要做變更追蹤（Dirty Checking），對大量查詢有明顯效能提升\n- **標註應加在 Service 層**：Controller 層不應直接管理交易邊界\n- **建議在 Service 類別上加 `@Transactional(readOnly = true)`**，個別寫入方法再覆寫為 `@Transactional`\n\n```java\n@Service\n@Transactional(readOnly = true)  // 預設唯讀\npublic class CustomerService {\n    public List<Customer> findAll() { ... }  // 繼承 readOnly\n\n    @Transactional  // 覆寫為可寫入\n    public Customer create(Customer c) { ... }\n}\n```",
    });
  }

  const modifying = concepts.filter((c) =>
    ["@Query 改資料必須同時加上 @Modifying", "@Modifying 批次刪除 vs 派生刪除的選用時機"].includes(c.heading)
  );
  if (modifying.length) {
    result.push(mergeConcepts("@Modifying 與批次操作", "交易管理 (@Transactional)", modifying));
  }

  // Audit 稽核（6 → 2）
  const auditIntro = concepts.find((c) => c.heading === "Audit 欄位：自動記錄建立與修改時間");
  if (auditIntro) result.push({ ...auditIntro, group: "Audit 稽核欄位" });

  const auditImpl = concepts.filter((c) =>
    ["BaseAuditEntity：把 Audit 欄位抽成共用父類別", "Entity 繼承 BaseAuditEntity"].includes(c.heading)
  );
  if (auditImpl.length) {
    result.push(mergeConcepts("BaseAuditEntity 設計與繼承", "Audit 稽核欄位", auditImpl));
  }

  // Specification 動態查詢（10 → 3）
  const specWhy = concepts.find((c) => c.heading === "Query Method 的限制：條件一多就爆炸");
  const specIntro = concepts.find((c) => c.heading === "Specification：動態查詢的正確解法");
  if (specWhy && specIntro) {
    result.push(mergeConcepts("為什麼需要動態查詢", "Specification 動態查詢", [specWhy, specIntro]));
  }

  const specCompare = concepts.find((c) => c.heading === "Specification 與 Query Method 的選用時機");
  if (specCompare) result.push({ ...specCompare, group: "Specification 動態查詢" });

  const specAI = concepts.find((c) => c.heading === "用 AI Agent 為既有專案加入 JPA");
  if (specAI) result.push({ ...specAI, group: "Specification 動態查詢" });

  // 新增：CRM 資料模型如何對應 JPA Entity
  result.push({
    heading: "CRM 資料模型如何對應 JPA Entity",
    group: "CRM 資料模型實作",
    body: "將前面章節介紹的 CRM Domain Model 對應到 JPA Entity 設計：\n\n**Customer Entity**\n核心欄位：`name`、`industry`（產業類型）、`contractStatus`（合約狀態，Enum）、`contractEndDate`。一對多關聯到 Opportunity、Activity、Task。\n\n**Opportunity Entity**\n核心欄位：`title`、`amount`（預估金額）、`probability`（成交機率）、`stage`（Enum：PROSPECTING / QUALIFICATION / PROPOSAL / CLOSING / WON / LOST）、`expectedCloseDate`。多對一關聯到 Customer。\n\n**Activity Entity**\n核心欄位：`type`（Enum：MEETING / CALL / EMAIL / VISIT）、`summary`（摘要文字，將被向量化）、`occurredAt`。多對一關聯到 Customer。\n\n**設計重點**\n- 所有 Entity 繼承 `BaseAuditEntity` 自動記錄建立/修改時間\n- 用 Specification 支援「依產業類型 + 合約狀態 + 商機金額範圍」的動態組合查詢\n- Activity 的 `summary` 欄位將在 Day 3~4 被 ETL 向量化，存入 pgvector 作為 AI 長期記憶",
  });

  return result;
}

// ═══════════════════════════════════════════
// U4：Security / JWT / OpenAPI（33 → 14）
// ═══════════════════════════════════════════
function transformU4(concepts) {
  const result = [];

  // OpenAPI（6 → 2）
  const apiDocWhy = concepts.find((c) => c.heading === "為什麼需要 API 文件");
  if (apiDocWhy) result.push({ ...apiDocWhy, group: "OpenAPI 文件自動生成" });

  const apiAnnotation = concepts.find((c) => c.heading === "用標註豐富 API 說明");
  const apiPrompt = concepts.find((c) => c.heading === "AI 提示詞練習");
  if (apiAnnotation) {
    result.push({
      heading: "OpenAPI 標註與 AI 提示詞",
      group: "OpenAPI 文件自動生成",
      body: trimCodeBlocks(apiAnnotation.body) + (apiPrompt ? "\n\n---\n\n**AI 提示詞練習**\n\n" + apiPrompt.body : ""),
    });
  }

  // 全域例外處理（6 → 2）
  const exWhy = concepts.find((c) => c.heading === "沒有全域例外處理的問題");
  const exFormat = concepts.find((c) => c.heading === "建立統一的 ErrorResponse 格式");
  if (exWhy && exFormat) {
    result.push(mergeConcepts("統一錯誤回應設計", "全域例外處理", [exWhy, exFormat]));
  }

  const exProblem = concepts.find((c) => c.heading === "ProblemDetail：Spring Boot 3 內建標準格式");
  const exTable = concepts.find((c) => c.heading === "例外處理回應對照");
  if (exProblem || exTable) {
    result.push(mergeConcepts("ProblemDetail 與錯誤回應對照", "全域例外處理", [exProblem, exTable].filter(Boolean)));
  }

  // Log 日誌管理（5 → 2）
  const logConcepts = concepts.filter((c) =>
    ["Spring Boot 預設 Log 機制", "Log 最佳實踐"].includes(c.heading)
  );
  if (logConcepts.length) {
    result.push(mergeConcepts("Log 核心觀念與最佳實踐", "Log 日誌管理", logConcepts));
  }

  const slf4j = concepts.find((c) => c.heading === "@Slf4j 與結構化 Log 寫法");
  if (slf4j) result.push({ ...slf4j, group: "Log 日誌管理", body: trimCodeBlocks(slf4j.body) });

  // AOP 面向切面（7 → 3）
  const aopWhy = concepts.find((c) => c.heading === "AOP 解決了什麼問題");
  if (aopWhy) result.push({ ...aopWhy, group: "AOP 面向切面" });

  const aopVocab = concepts.find((c) => c.heading === "Spring AOP 五大核心詞彙");
  const aopDiagram = concepts.find((c) => c.heading === "AOP 概念圖解");
  const aopProxy = concepts.find((c) => c.heading === "Spring AOP 的實現方式：Proxy");
  if (aopVocab) {
    result.push(mergeConcepts("AOP 核心詞彙與實現方式", "AOP 面向切面", [aopVocab, aopDiagram, aopProxy].filter(Boolean)));
  }

  const aopWhy2 = concepts.find((c) => c.heading === "很少直接寫 AOP 的原因");
  const aopDay1 = concepts.find((c) => c.heading === "Day 1 哪些功能背後用了 AOP");
  if (aopWhy2) {
    result.push(mergeConcepts("為什麼很少直接寫 AOP", "AOP 面向切面", [aopWhy2, aopDay1].filter(Boolean)));
  }

  // Spring Security（9 → 3）
  const secWhy = concepts.find((c) => c.heading === "安全防護重點");
  if (secWhy) result.push({ ...secWhy, group: "Spring Security 與 JWT 認證" });

  const secAI1 = concepts.find((c) => c.heading === "請 AI Agent 幫你安裝 Security 與 JWT 依賴");
  const secAI2 = concepts.find((c) => c.heading === "AI Agent 提示詞 — 身分驗證與角色授權實作");
  if (secAI1 || secAI2) {
    result.push(mergeConcepts("AI Agent 提示詞 — Security 與 JWT 實作", "Spring Security 與 JWT 認證", [secAI1, secAI2].filter(Boolean)));
  }

  const secVerify = concepts.find((c) => c.heading === "Swagger 網頁驗證步驟（推薦）");
  if (secVerify) result.push({ ...secVerify, group: "Spring Security 與 JWT 認證" });

  // 新增：CRM 角色與權限模型
  result.push({
    heading: "CRM 角色與權限模型",
    group: "CRM 安全設計",
    body: "AI CRM 系統的角色設計反映真實企業的組織層級：\n\n**ROLE_SALES（業務人員）**\n- 可查看/編輯自己負責的客戶、商機與互動紀錄\n- 可使用 AI 助理進行客戶分析與行動建議\n- 不可查看其他業務人員的客戶資料\n\n**ROLE_MANAGER（業務主管）**\n- 可查看團隊內所有業務人員的客戶與商機\n- 可查看團隊的銷售報表與 AI 預測分析\n- 可指派任務給業務人員\n\n**ROLE_ADMIN（系統管理員）**\n- 可管理使用者帳號、角色與權限\n- 可管理 RAG 知識庫（上傳/刪除產品文件、銷售話術）\n- 可查看系統日誌與 AI 使用統計\n\n**權限控制實作要點**\n- API 層：用 `@PreAuthorize` 標註控制端點存取\n- 資料層：用 Specification 自動加入 `salesRepId` 過濾條件\n- AI 層：工具呼叫自動注入當前使用者的權限範圍，確保 AI 不會洩露跨業務的客戶資料",
  });

  return result;
}

// ═══════════════════════════════════════════
// U5：React 前端（5 → 6，加 CRM UI）
// ═══════════════════════════════════════════
function transformU5(concepts) {
  const result = [...concepts]; // 保留全部

  // 新增：CRM 工作台 UI 設計
  result.push({
    heading: "CRM 工作台 UI 設計思維",
    group: "CRM 前端架構",
    body: "CRM 前端工作台的核心畫面設計，圍繞業務人員的日常工作流：\n\n**Dashboard（儀表板）**\n展示關鍵指標：本月商機總額、待跟進客戶數、即將到期合約、AI 建議的優先行動。使用卡片式佈局，數據來自後端 API 的即時聚合。\n\n**Customer List（客戶列表）**\n支援動態篩選（產業、合約狀態、最後互動時間）與搜尋。列表使用虛擬滾動處理大量資料，點擊進入客戶詳細頁面。\n\n**Opportunity Board（商機看板）**\n看板式佈局（類似 Trello），每個欄位代表商機階段（Prospecting → Qualification → Proposal → Closing）。可拖拽商機卡片變更階段。\n\n**AI Chat（AI 助理）**\n右側浮動面板或獨立頁面，支援 SSE 串流對話、Markdown 渲染、客戶摘要卡片與行動建議按鈕。\n\n**元件設計原則**\n- 每個頁面是一個 Route，共用 Layout 元件（側欄 + 頂部導覽）\n- 資料請求統一用 Axios + Vite Proxy\n- CSS 使用 HSL 色彩系統，支援深色/淺色主題切換",
  });

  return result;
}

// ═══════════════════════════════════════════
// U6：Spring AI / SSE / Tool Calling（34 → 14）
// ═══════════════════════════════════════════
function transformU6(concepts) {
  const result = [];

  // 新增：AI CRM 助理的商業價值
  result.push({
    heading: "AI CRM 助理的商業價值",
    group: "AI CRM 助理定位",
    body: "AI 助理不是「聊天機器人」，而是業務人員的智慧工作夥伴。在 CRM 場景中，AI 能創造三層價值：\n\n**第一層：資訊查詢（Tool Calling）**\n業務問「APIM 最近的合約狀況？」，AI 呼叫 CRM API 查詢客戶資料、商機與互動紀錄，整理成結構化摘要。省去業務人員在多個頁面間切換查找的時間。\n\n**第二層：知識檢索（RAG）**\n業務問「APIM 適合推薦哪個方案？」，AI 從向量化的產品文件與銷售話術中檢索相關內容，結合客戶的產業特性與需求給出推薦。\n\n**第三層：洞察建議（結合 Tool Calling + RAG + 歷史記憶）**\n業務問「GlobalMart 有流失風險嗎？該怎麼處理？」，AI 綜合分析：\n- 合約即將到期（CRM 資料）\n- 最近互動提及「預算凍結」與「競品比較」（歷史互動向量搜尋）\n- 過去類似情境的成功挽留策略（知識庫 RAG）\n\n最終產生具體的行動建議：「建議在 7 月 15 日前安排高層會議，提出客製化續約方案，重點強調...」\n\n這就是 AI CRM 的核心價值：**讓每一位業務人員都擁有資深顧問級的分析能力**。",
  });

  // ChatClient（12 → 4）
  const chatConcepts = concepts.filter((c) =>
    ["ChatClient 在架構中的位置", "串流輸出為什麼重要", "對話記憶設計"].includes(c.heading)
  );
  if (chatConcepts.length) {
    result.push(mergeConcepts("ChatClient 核心概念", "ChatClient 基礎與對話記憶", chatConcepts));
  }

  const advisorConcept = concepts.find((c) => c.heading === "ChatClient 與 Advisor 深入解析");
  if (advisorConcept) result.push({ ...advisorConcept, group: "ChatClient 基礎與對話記憶" });

  const groqConfig = concepts.find((c) => c.heading === "Groq API 端點設定（application.yml）");
  if (groqConfig) result.push({ ...groqConfig, group: "ChatClient 基礎與對話記憶", body: trimCodeBlocks(groqConfig.body) });

  const chatAI = concepts.find((c) => c.heading === "AI Agent 提示詞 — 建立 ChatClient 對話入口");
  if (chatAI) result.push({ ...chatAI, group: "ChatClient 基礎與對話記憶" });

  // Tool Calling（11 → 4）
  const toolValue = concepts.find((c) => c.heading === "工具呼叫的真正價值");
  const toolLogic = concepts.find((c) => c.heading === "@Tool 與 ToolCallback 的使用邏輯");
  if (toolValue || toolLogic) {
    result.push(mergeConcepts("工具呼叫核心概念", "Tool Calling 工具呼叫", [toolValue, toolLogic].filter(Boolean)));
  }

  const toolSelect = concepts.find((c) => c.heading === "@Tool vs ToolCallback 選型原則");
  if (toolSelect) result.push({ ...toolSelect, group: "Tool Calling 工具呼叫" });

  const toolAI = concepts.find((c) => c.heading === "AI Agent 提示詞 — 加入工具呼叫能力");
  if (toolAI) result.push({ ...toolAI, group: "Tool Calling 工具呼叫" });

  const toolArch = concepts.find((c) => c.heading === "工具呼叫的架構定位");
  if (toolArch) result.push({ ...toolArch, group: "Tool Calling 工具呼叫" });

  // SSE 前端（7 → 3）
  const sseWhy = concepts.find((c) => c.heading === "為什麼選擇 SSE (Server-Sent Events)");
  if (sseWhy) result.push({ ...sseWhy, group: "SSE 前端串流整合" });

  const sseCards = concepts.find((c) => c.heading === "對話中客戶摘要、商機、建議行動的卡片元件呈現方式");
  if (sseCards) result.push({ ...sseCards, group: "SSE 前端串流整合", body: trimCodeBlocks(sseCards.body) });

  const sseAI = concepts.find((c) => c.heading === "AI Agent 提示詞 — 前端串流與安全認證");
  if (sseAI) result.push({ ...sseAI, group: "SSE 前端串流整合" });

  // 系統運行展示（保留全部）
  const demos = concepts.filter((c) => c.heading?.startsWith("系統運行展示"));
  demos.forEach((c) => result.push(c));

  return result;
}

// ═══════════════════════════════════════════
// U7：RAG / MCP / Skills（24 → 11）
// ═══════════════════════════════════════════
function transformU7(concepts) {
  const result = [];

  // RAG（10 → 4）
  const ragCore = concepts.filter((c) =>
    ["RAG 的基本想法", "向量嵌入與 pgvector"].includes(c.heading)
  );
  if (ragCore.length) {
    result.push(mergeConcepts("RAG 核心概念", "RAG 原理與 ETL 流程", ragCore));
  }

  const ragCompare = concepts.find((c) => c.heading === "RAG vs Fine-Tuning 完整比較");
  if (ragCompare) result.push({ ...ragCompare, group: "RAG 原理與 ETL 流程" });

  const etl = concepts.find((c) => c.heading === "ETL 三步驟：文件到向量庫");
  if (etl) result.push({ ...etl, group: "RAG 原理與 ETL 流程" });

  const ragAI = concepts.find((c) => c.heading === "AI Agent 提示詞 — 建立 RAG 知識庫");
  if (ragAI) result.push({ ...ragAI, group: "RAG 原理與 ETL 流程" });

  // MCP 與 Skills（9 → 3）
  const mcpCore = concepts.filter((c) =>
    ["MCP 應該怎麼理解", "AI 呼叫遠端工具的完整流程"].includes(c.heading)
  );
  if (mcpCore.length) {
    result.push(mergeConcepts("MCP 核心概念", "MCP 與 Skills 擴充", mcpCore));
  }

  const skills = concepts.filter((c) =>
    ["Skills 是什麼：把專業知識打包給 AI", "MCP vs Skills：擴充 AI 能力的兩條路"].includes(c.heading)
  );
  if (skills.length) {
    result.push(mergeConcepts("Skills 與 Agent 工具", "MCP 與 Skills 擴充", skills));
  }

  const mcpSpring = concepts.find((c) => c.heading === "Spring AI 如何加入 Skills：spring-ai-agent-utils");
  if (mcpSpring) result.push({ ...mcpSpring, group: "MCP 與 Skills 擴充" });

  // 對話歷史（5 → 2）
  const historyWhy = concepts.find((c) => c.heading === "為什麼需要對話歷史 RAG");
  if (historyWhy) result.push({ ...historyWhy, group: "對話歷史長期記憶" });

  const historyAI = concepts.find((c) => c.heading === "AI Agent 提示詞 — 對話歷史長期記憶");
  if (historyAI) result.push({ ...historyAI, group: "對話歷史長期記憶" });

  // 新增：CRM 知識庫設計
  result.push({
    heading: "CRM 知識庫設計：哪些文件該向量化",
    group: "CRM 知識庫策略",
    body: "AI CRM 助理的智慧程度取決於知識庫的品質。以下是三類需要向量化的文件，以及各自的 RAG 策略：\n\n**1. 客戶互動紀錄（Activity Summary）**\n每次業務拜訪、電話、會議的摘要文字，是 AI 判斷客戶狀態與意向的核心依據。向量化後可支援「這位客戶最近關注什麼？」「過去有沒有類似的抱怨？」等語意查詢。\n\n**2. 產品文件與方案規格**\n技術規格書、功能比較表、定價方案。讓 AI 能根據客戶需求推薦適合的產品組合，並準確引用規格細節。\n\n**3. 銷售話術與成功案例**\n歷史成交案例的策略復盤、異議處理話術、續約成功的談判要點。讓 AI 在面對類似情境時能提供經過驗證的建議。\n\n**ETL 設計考量**\n- 互動紀錄按時間分段（每次互動一個向量），確保時序資訊可追溯\n- 產品文件按章節分割（chunk），每個 chunk 包含完整語意單元\n- 用 metadata filter 區分文件類型，查詢時可指定只搜尋特定類別",
  });

  return result;
}

// ═══════════════════════════════════════════
// U8：結訓驗收（微調，加 CRM 展示腳本）
// ═══════════════════════════════════════════
function transformU8(concepts) {
  const result = concepts.map((c) => {
    if (c.heading === "Demo Day 展示準備") {
      return {
        ...c,
        body: c.body + "\n\n**CRM 完整流程展示腳本**\n\n建議按以下順序展示 AI CRM 系統：\n1. 登入系統（展示 JWT 認證）→ 進入 Dashboard 查看本月指標\n2. 進入客戶列表 → 使用動態篩選找到 APIM → 查看客戶詳細資訊\n3. 開啟 AI 助理 → 詢問「APIM 最近的合約狀況和商機進度」（展示 Tool Calling）\n4. 詢問「GlobalMart 有流失風險嗎？建議怎麼處理？」（展示 RAG + 歷史記憶）\n5. AI 產生行動建議 → 確認建立待辦任務（展示完整業務閉環）",
      };
    }
    return c;
  });
  return result;
}

// ═══════════════════════════════════════════
// 執行轉換
// ═══════════════════════════════════════════
const transformMap = {
  u1: transformU1,
  u2: transformU2,
  u3: transformU3,
  u4: transformU4,
  u5: transformU5,
  u6: transformU6,
  u7: transformU7,
  u8: transformU8,
};

for (const dayKey of ["day1", "day2"]) {
  const day = course[dayKey];
  if (!day) continue;
  for (const unit of day.units) {
    const transform = transformMap[unit.id];
    if (transform && unit.concepts) {
      const before = unit.concepts.length;
      unit.concepts = transform(unit.concepts);
      const after = unit.concepts.length;
      console.log(`${unit.id}: ${before} → ${after} concepts`);
    }
  }
}

// ═══════════════════════════════════════════
// 寫回檔案
// ═══════════════════════════════════════════
const output = `// AI CRM 課程教學網站 - 結構化課程資料 (Single Source of Truth)
// 此檔案由 scripts/generate-assets.mjs 同步插圖 manifest 後產生。

window.COURSE = ${JSON.stringify(course, null, 2)};
`;

fs.writeFileSync(DATA_PATH, output, "utf-8");
console.log("\n✅ course-data.js 已更新");

// 統計
let total = 0;
let totalChars = 0;
for (const dayKey of ["day1", "day2"]) {
  const day = course[dayKey];
  if (!day) continue;
  for (const unit of day.units) {
    if (unit.concepts) {
      total += unit.concepts.length;
      totalChars += unit.concepts.reduce((s, c) => s + (c.body?.length || 0), 0);
    }
  }
}
console.log(`Total concepts: ${total}`);
console.log(`Total chars: ${totalChars}`);
