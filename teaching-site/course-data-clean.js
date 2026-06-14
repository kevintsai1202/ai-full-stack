      
<truncated 45283 bytes>
'**集合類**：`@NotEmpty`（集合不可空）、`@Size(min, max)`（集合元素數量範圍）',
558:                 '**巢狀物件**：`@Valid` 標在欄位上 → 對該物件的欄位遞迴驗證（如 List 裡的每個元素）'
559:               ],
560:               callout: {
561:                 type: 'info',
562:                 title: '@Valid 與 @Validated 的差別',
563:                 body: '@Valid 是 Jakarta EE 標準，@Validated 是 Spring 的擴充版本。功能幾乎相同，差別在於 @Validated 支援「群組驗證（Validation Groups）」，可以為「新增」和「修改」分別定義不同的規則組合。一般情況用 @Valid 就夠；需要分組時再換 @Validated。'
564:               }
565:             },
566:             {
567:               title: 'AI 提示詞練習',
568:               type: 'text',
569:               paragraphs: [
570:                 '示範專案跑起來後，試著用以下問題請 AI 助手解釋細節，加深對 Spring MVC 的理解：'
571:               ],
572:               bullets: [
573:                 '「`@RestController` 與 `@Controller` 差別是什麼？如果改用 `@Controller`，我需要在哪裡加什麼才能讓回傳值變成 JSON？」',
574:                 '「現在的 ProductService 用 List 存資料，如果我要換成 HashMap 以加速 ID 查詢，應該怎麼改？請幫我重寫 findById 的邏輯。」',
575:                 '「為什麼 getBy
      '透過 Spring AI 建立串流對話、工具呼叫與 RAG 問答能力',
      '完成可展示、可驗證、可持續擴充的智慧商城客服系統'
    ],
    techStack: [
      'Spring Boot 4.0.0',
      'Spring AI 2.0.0-M8',
      'Spring Data JPA',
      'PostgreSQL / pgvector',
      'Flyway',
      'ChatClient / SSE / MCP'
    ]
  },
  overview: {
    pillars: [
      
    flow: ["環境檢查", "後端骨架", "前端骨架", "連線驗證"],
    colors: [palette.blue, palette.cyan, palette.green, palette.amber]
  },
  {
    id: "u2",
    heroTitle: "Spring MVC Domain API",
    heroSubtitle: "Controller / Service / DTO 邊界",
    diagramTitle: "MVC 請求生命週期",
    terms: [
  
## 程式碼開發規範
1. **繁體中文註解**：所有撰寫的 JavaScript / Node 腳本，函數級別與重要變數均須使用繁體中文註解。
2. **React 19 原則**：互動 UI 需以 React 19 元件撰寫，入口維持 Vite；不要把大量 DOM 操作重新塞回 `index.html`。
3. **資料與渲染分離**：所有課程教學內容與任務字串，必須定義在 `course-data.js` 物件中；React 元件僅能讀取此資料進行渲染。
4. **防禦性安全性**：React 中不得使用 `dangerouslySetInnerHTML` 渲染課程變數；Markdown-like 片段需轉成安全 React 節點。
5. **圖片覆蓋率底線**：每個新增的單元在 `course-data.js` 裡必須配置 `illustrations` 陣列（至少 3 個圖片：hero、diagram、term），且必須在 `assets/illustrations/` 目錄中建立實體 SVG/PNG 檔案。
























<truncated 4693 bytes>
接將建議輸出給業務人員。\n3. Agent Trace UI 與觀測：\n   - 記錄每個 Action 的執行順序、耗時、輸入、輸出以及 LLM Token 消耗。\n   - 在前端 React 介面實作「Agent 軌跡面板 (Trace Panel)」，以時間線形式清晰展現 Agent 的決策過程（如：步驟 1 讀取客戶資料成功 -> 步驟 2 檢索知識庫匹配 2 筆 -> 步驟 3 發現流失風險 80 分 -> 步驟 4 生成建議 -> 步驟 5 審核成功輸出）。\n4. 測試與上線檢查：\n   - 撰寫單元與整合測試，驗證純 Java Actions 與 LLM Prompts 的邊界。\n   - 必須涵蓋三個端到端結訓案例，逐一對應 Blackboard 走向不同的 Action 路徑，且與 Unit 3 seed data 的三種客戶對應，確保痛點場景可被完整驗證：(1) 高價值活躍客戶 → 正常產出並通過審核；(2) 資料不足客戶 → 觸發「要求補資料」Replanning，不產生臆測建議；(3) 流失 / 續約延遲風險客戶 → 風險分數超標觸發「轉交主管」路徑。\n   - 提供 AI 上線檢查清單 (Checklist) 作為學員驗收標準。\n5. 程式碼需要有函式級別註解(註解使用中文)，重要變數或物件也需要加上註解。\n6. 在 PowerShell 7+ (Windows) 或 zsh / bash (macOS) 終端機中執行 `pnpm run verify`，確認 verify-site (資料結構、8單元、17個SVG、prompt 字數) 與 verify-render (Playwright RWD、搜尋、Checkbox持久化、複製功能) 全數通過，無水平溢出與主控台錯誤。",
      "principle": "Embabel 的核心價值在於將不可控的單一 Prompt 推理，轉化為可控、可規劃 (GOAP) 且具備型別約束與輸出審核的 Agent Workflow。透過 Blackboard 與 Agent Trace 機制，讓 AI 決策流程透明、可維護且易於偵錯，符合企業維運的要求。"
    }
  ]
};





















































































    "units": [
      {
        "id": "u1",
        "title": "開發環境、專案骨架與 AI 協作流程",
        "subtitle": "建立 Full-stack Monorepo 與連線健康檢查",
        "features": [
          "前後端專案骨架啟動",
          "/api/health 健康檢查 API",
          "前端連線狀態呼吸燈"
        ],
        "time": "09:00 ~ 12:00",
        "goals": [
          "建立 Windows (PowerShell 7+) 或 macOS (zsh + Homebrew) 下的 Java 21 / Node.js / Docker 開發環境",
          "初始化 backend (Spring Boot 4.0.x) 與 frontend (Vite + React) 專案目錄結構",
          "理解 AI Agent 協作三部曲：讀取需求、產生程式、運行驗證",
          "在後端實作 /api/health，在前端實作連線健康狀態呼吸燈"
        ],
        "concepts": [
          {
            "heading": "本機開發環境版本基準與架構考量",
            "body": "本課程同時支援 Windows 與 macOS 兩種開發環境。後端基於 Java 21 與 Spring Boot 4.0.x。Java 21 LTS 引入的虛擬執行緒 (Virtual Threads) 能以極低的記憶體代價建立數百萬個輕量級執行緒，非常適合高併發的企業級 CRM 系統。前端使用 React 19 與 Vite，React 19 全新編譯器能自動處理元件 memoization，免除手動 useMemo / useCallback 的累贅。Windows 以 PowerShell 7+ 做為命令列，而 macOS 以 zsh 搭配 Homebrew 快速安裝 Java 及工具鏈。資料庫採用 Docker Compose 執行 Postg











                "Spring Boot 後端 Maven 專案，包含控制器、服務層與資料存取"
              ],
              [
                "frontend/",
                "Vite + React + TypeScript 前端專案，包含 Dashboard、客戶工作台與 AI 聊天室"
              ]
            ]
          }
        ],
        "prompts": [
          {
            "id": "d1-p1",
            "title": "A. Windows 環境與目錄初始化",
            "note": "引導 AI 檢查本機 JDK、Maven、Node 與 Docker 版本，並建立專案結構",
            "text": "請為我檢查 Windows 11 本機環境... (完整提示詞詳見 course-data.js prompt)"
          }
        ],
        "tasks": [
          {
            "id": "d1-u1-t1",
            "label": "安裝 JDK 21, Maven 3.9+, Node.js v18+, Docker Desktop，並以 PowerShell 驗證版本"
          },
          {
            "id": "d1-u1-t2",
            "label": "建立後端 Spring Boot 4.0.x 專案，新增 /api/health 回傳 status=UP 的 JSON"
          },
          {
            "id": "d1-u1-t3",
            "label": "建立前端 Vite + React 專案，實作 API 呼叫，連線成功顯示綠燈，失敗顯示紅燈"
          }
        ],
        "materials": [
          {
            "id": "d1-u1-m1",
            "name": "Windows_與_macOS_開發環境安裝指引",
            "type": "MD"
          }
        ],
  











          },
          {
            "name": "u1-3-term.svg",
            "kind": "term",
            "alt": "開發環境、專案骨架與 AI 協作流程 專業術語解釋",
            "spec": "Monorepo / Health Check / PowerShell / Zsh"
          }
        ],
        "prompt": "你是一位精通 Windows 11 (PowerShell 7+) 環境的資深軟體架構師。現在我們需要初始化一套名為「AI CRM 智慧業務助理」的企業級全端專案。\n此專案採用前後端分離架構，後端使用 Spring Boot 4.0.x + Java 21，前端使用 Vite + React + TypeScript。\n請為我規劃並產生一套適用於 Windows 環境的環境檢查與專案初始化腳本。\n\n要求：\n1. 設計環境檢查腳本 `check-env.ps1` (PowerShell 7)，檢查項目包含：\n   - 檢查 JDK 21 是否安裝，並驗證 `java -version` 是否包含 \"21\"。\n   - 檢查 Maven 是否安裝，驗證 `mvn -version`。\n   - 檢查 Node.js 是否安裝 (要求 v18+)，驗證 `node -v` 與 `npm -v`。\n   - 檢查 Docker 是否正在運行，驗證 `docker info`（使用 Docker Desktop）。\n   - 若任何檢查失敗，以 `Write-Host -ForegroundColor Red` 顯示明確錯誤訊息，並提供 Windows 安裝指引（winget 指令或官網下載連結）。\n\n2. 提供初始化專案目錄結構的 PowerShell 指令：\n   - 根目錄：`ai-crm/`\n   - 後端目錄：`ai-crm/backend/` (由 Spring Initializr 建立，包含 Spring Web, JPA, Flyway, PostgreSQL, Security, Spring AI 等依賴)\n   - 前端目錄：`ai-crm/frontend/` (

        "principle": "AI 協作開發的核心原則是：『先讀懂需求，再產生程式碼，最後用自動化腳本驗證。』本課程同時支援 Windows 與 macOS：Windows 以 PowerShell 7+、macOS 以 zsh + Homebrew 管理工具鏈，確保環境變數與目錄結構一致，避免因作業系統不同導致的通訊死角。"
      },
      {
            "body": "Entity 類別反映的是資料庫 schema 與 ORM 關係。我們絕不在 API 入口或出口直接曝露 Entity，否則容易引起不必要的欄位洩露、Jackson 序列化雙向關聯時發生循環參照 StackOverflow 錯誤。更重要的是，在 Service 交易（Transaction）結束後，Entity 的 Hibernate Session 已關記，此時若直接把 Entity 回傳給 Spring MVC 渲染，極易觸發著名的 `LazyInitializationException`。透過 RequestDTO 與 ResponseDTO，我們在 Service 層建立明確的資料邊界，僅提取前端需要的資料，阻斷延遲載入產生的資料庫二次查詢，並降低傳輸負載，也使後端資料庫重構時不至於直接使前端 API 崩潰。"
          },
          {
            "heading": "Lombok 的實務效能與記憶體洩漏陷阱",
            "body": "Lombok 通過 `@Data` 註解能在編譯期自動生成 Getter、Setter 及 `equals` / `hashCode` 方法。然而，在 JPA Entity 實體上使用 `@Data` 是實務上的重大禁忌：(1) `@EqualsAndHashCode` 預設會包含所有欄位，若關聯實體為延遲載入 (Lazy Fetch)，在調用 `equals` 時會強制觸發資料庫查詢，導致 LazyInitializationException 或嚴重的 N+1 效能問題；(2) 若雙向關聯的實體皆標註 `@Data`，Jackson 或 Lombok 進行 `toString` 或 `hashCode` 計算時會產生無窮迴圈而導致 StackOverflowError。因此，對於 JPA 實體，應明確宣告 `@Getter` 與 `@Setter`，並手寫或使用 IDE 生成排除關聯欄位的特定 `equals` / `hashCode` 方法，或以實體的主鍵 (Id) 作為唯一判定基準。"
          },
          {
            "heading": "Spring 的構造注入與 @RequiredArgsConstructor",























































































































































































      unit.illustrations = [
        { name: `${unit.id}-1.png`, kind: "hero", alt: spec.heroTitle, spec: spec.heroSubtitle },
        { name: `${unit.id}-2.svg`, kind: "diagram", alt: spec.diagramTitle, spec: `流程圖：${spec.flow.join(" -> ")}` },
        { name: `${unit.id}-3-term.svg`, kind: "term", alt: `${unit.title} 專業術語解釋`, spec: spec.terms.map(([term]) => term).join(" / ") }
      ];
      allUnits.push(unit);
    }
  }
  course.units = allUnits;
}

/**
 * 寫出格式化後的 course-data.js，保留其作為網站資料唯一來源。
 */
async function writeCourse(course) {
  const code = `// AI CRM 課程教學網站 - 結構化課程資料 (Single Source of Truth)\n// 此檔案由 scripts/generate-assets.mjs 同步插圖 manifest 後產生。\n\nwindow.COURSE = ${JSON.stringify(course, null, 2)};\n`;
  await fs.writeFile(coursePath, code, "utf8");
}

/**
 * 主流程：重製 cover、每章 hero、流程插圖與專業術語解釋圖片。
 */
async function main() {
  await fs.mkdir(assetDir, { recursive: true });
  await fs.writeFile(path.join(assetDir, "cover.svg"), coverSvg(), "utf8");
  for (const [index, unit] of unitSpecs.entries()) {
        <g transform="translate(506,456)">
          ${["Spring Boot", "React", "Spring AI", "RAG", "MCP", "Embabel"].map((label, i) =>
            `<rect x="${(i % 3) * 170}" y="${Math.floor(i / 3) * 52}" width="142" height="34" rx="17" fill="${[palette.blue, palette.cyan, palette.green, palette.amber, palette.violet, palette.rose][i]}" opacity="0.92"/>
             <text x="${(i % 3) * 170 + 71}" y="${Math.floor(i / 3) * 52 + 23}" text-anchor="middle" fill="#ffffff" font-size="14" font-weight="900" font-family="system-ui, 'Microsoft JhengHei', sans-serif">${label}</text>`
          ).join("")}
        </g>
      </g>`
  });
}

/**
 * 讀取現有 course-data.js 以便同步更新 day units 與 flattened units。
 */
async function loadCourse() {
  const code = await fs.readFile(coursePath, "utf8");
  const context = { window: {} };
  vm.createContext(context);
  vm.runInContext(code, context);
  return context.window.COURSE;
}

/**
 * 將新插圖 manifest 寫回 day units 與頂層 units，避免渲染與驗證看到不同資料。
 */
function syncIllustrations(course) {
  const byId = new Map(unitSpecs.map((unit) => [unit.id, unit]));
  const allUnits = [];
  for (const dayMeta of course.meta.days) {
    const day = course[dayMeta.id];
    for (const unit of day.units || []) {
      const spec = byId.get(unit.id);
      if (!spec) continue;
      unit.illustrations = [
        { name: `${unit.id}-1.png`, kind: "hero", alt: spec.heroTitle, spec: spec.heroSubtitle },
        { name: `${unit.id}-2.svg`, kind: "diagram", alt: spec.diagramTitle, spec: `流程圖：${spec.flow.join(" -> ")}` },
        { name: `${unit.id}-3-term.svg`, kind: "term", alt: `${unit.title} 專業術語解釋`, spec: spec.terms.map(([term]) => term).join(" / ") }
      ];
      allUnits.push(unit);
    }
  }
  course.units = allUnits;
}

/**
 * 寫出格式化後的 course-data.js，保留其作為網站資料唯一來源。
 */
async function writeCourse(course) {
  const code = `// AI CRM 課程教學網站 - 結構化課程資料 (Single Source of Truth)\n// 此檔案由 scripts/generate-assets.mjs 同步插圖 manifest 後產生。\n\nwindow.COURSE = ${JSON.stringify(course, null, 2)};\n`;
        "principle": "在安全性設計中，應採用無狀態 (Stateless) 的 JWT 認證機制以提昇水平擴充力。Spring Security 的 Filter Chain 為 API 的第一道關卡，全域錯誤處理與 RFC 7807 ProblemDetail 設計能提供前端一致且安全的錯誤回應格式，絕不將原始錯誤堆疊回傳給學員。"
      }
    ]
  },
  "day3": {
    "id": "day3",
    "title": "React 整合與 AI 串流助理",
    "date": "Day 3",
    "hours": "6 hours",
    "learningGoal": "建立 React CRM 系統前端工作台，並在後端整合 Spring AI，透過 SSE 將 AI 聊天串流推送到 React 助理聊天室，並啟用 Tool Calling 連接真實資料。",
    "schedule": [
      [
        "09:00 - 10:30",
        "Unit 5: React CRM 工作台與前後端整合",
        "建立 Axios API Client，配置 Token 攔截器，實作登入與 Token 過期重導向。"
      ],
      [
        "10:30 - 12:00",
        "Unit 5: Dashboard 與商機 Kanban",
        "實作 React 客戶列表分頁、多條件篩選與商機拖拽 Kanban 看板，優化 Loading 狀態。"
      ],
      [
        "13:00 - 15:00",
        "Unit 6: Spring AI ChatClient 與 SSE 串流",
        "配置 Spring AI 依賴，設計 /api/ai/chat SSE 端點，實作字元級串流輸出。"
      ],
      [
        "15:00 - 17:00",
        "Unit 6: AI Tool Calling 實戰",
        "實作 CrmInsightTools，讓 AI 助理自動判斷並呼叫 Java 邏輯讀取真實客戶資訊，串接前端面板。"
      ]
    ],
    "units": [
      {
        "id": "u5",
        "title": "React CRM 工作台與前後端整合",
        "subtitle": "Axios 攔截器、TypeScript 介面對接與 Dashboard/Kanban 實作",
        "features": [
          "React 登入頁與 CRM Dash
              title: 'AI 提示詞練習',
              type: 'text',
              paragraphs: [
                '示範專案跑起來後，試著用以下問題請 AI 助手解釋細節，加深對 Spring MVC 的理解：'
              ],
              bullets: [
                '「`@RestController` 與 `@Controller` 差別是什麼？如果改用 `@Controller`，我需要在哪裡加什麼才能讓回傳值變成 JSON？」',
                '「現在的 ProductService 用 List 存資料，如果我要換成 HashMap 以加速 ID 查詢，應該怎麼改？請幫我重寫 findById 的邏輯。」',
                '「為什麼 getById 回傳 `ResponseEntity<Product>` 而不是直接回傳 `Product`？兩種做法有什麼差別？」',
                '「請幫我在 ProductController 加一個 DELETE /api/products/{id} 端點，成功刪除回傳 204，找不到回傳 404。」'
              ]
            }
          ]
        },
        {
          id: 'd1-u3',
     




































































































































        "principle": "Spring AI 的 ChatClient 和 Tool Calling 技術將 AI 的黑盒子轉化為具備資料庫查詢能力的智慧代理。LLM 應該只負責『摘要、推理與表達』，而『金額、次數、統計』等精準計算必須交由 Java Domain Service 處理，並透過 SSE 提供平滑打字渲染。"
      }
    ]
  },
  "day4": {
    "id": "day4",
    "title": "RAG、MCP 與 Embabel Agent 自動化",
    "date": "Day 4",
    "hours": "6 hours",
    "learningGoal": "使用 pgvector 實作知識庫向量檢索 RAG 流程，設計 MCP 連接器連結外部系統，並使用 Embabel 實作型別安全且可審核的 GOAP 行動代理。",















































          }
        ],
        "prompts": [
          {
            "id": "d4-p1",
            "title": "A. Vector Ingestion 與 RAG 查詢",
            "note": "請 AI 產生 pgvector Table 初始化、文字切片寫入與 RAG 檢索結合 Service 程式碼",
            "text": "請接續 Unit 6 成果，在 PostgreSQL 設定 pgvector... (完整提示詞詳見 course-data.js prompt)"
          }
        ],
        "tasks": [
          {
            "id": "d4-u7-t1",
            "label": "建立向量資料表，實作 Ingestion Pipeline，將產品手冊切片向量化後寫入 pgvector"
          },
          {
            "id": "d4-u7-t2",
            "label": "實作 RAG Vector 檢索服務，AI 回答產品推薦時自動引用最相關的產品說明片段"
          },
          {
            "id": "d4-u7-t3",
            "label": "前端實作『引用來源』浮水印卡片，點擊可看引用比對的原文段落與相似度分數"
          }
        ],
        "materials": [
          {
            "id": "d4-u7-m1",
            "name": "pgvector_環境安裝與向量檢索指令說明",
            "type": "MD"
          }
        ],
        "illustrations": [
          {
            "name": "u7-1.png",
            "kind": "hero",
            "alt": "RAG + MCP Extension",
            "spec": "pgvector 知識庫與外部工具協定"
          },
          {
            "name": "u7-2.svg",
            "kind": "diagram",
            "alt": "檢索增強生成管線",
            "spec": "流程圖：Chunk -> Embedding -> pgvector -> Citation"
          },
          {
            "name": "u7-3-term.svg",
            "kind": "term",
            "alt": "RAG、pgvector、MCP 與知識庫擴充 專業術語解釋",
            "spec": "Embedding / RAG / MCP"
          }
        ],
        "prompt": "請接續 Unit 6 的 AI 串流助理與 Tool Calling 實作。現在我們要進入 Unit 7：向量搜尋 (RAG) 與外部工具 (MCP) 擴充。我們要在資料庫中為 `pgvector` 建立產品型錄、常見銷售話術 (Playbook) 與服務條款的 vector table。當業務詢問產品推薦或合約規範時，AI 助理能自動檢索最相關的知識庫文件片段進行 RAG (Retrieval-Augmented Generation) 回答。此外，我們將設計 MCP (Model Context Protocol) 服務以擴充外部工具連結能力。\n\n要求：\n1. RAG 知識庫架構與 Ingestion 實作：\n   - 建立向量資料表 `knowledge_documents`，欄位包含主鍵、內容文字、向量欄位 `embedding` (使用 pgvector vector 類型) 與元資料 `metadata` (JS
              type: 'code',
              paragraphs: [
                '這是本課程 Product Entity 的結構，對應到 Flyway V1 建立的 `prod
      </div>
      <div className="quiz-container">
        {quizList.map((quiz, index) => (
          <article className="quiz-card" key={quiz.id}>
            <div className="quiz-question">{index + 1}. {quiz.q}</div>
            <div className="quiz-options">
              {quiz.options.map((option, optionIndex) => (
                <label className="quiz-option" ke
















































































































































    document.body.classList.remove("sidebar-visible-mobile");
    patchState((current) => ({ ...current, sidebarHidden: hidden }));
  }

  function restoreSidebar() {
    if (window.innerWidth <= 1180) {
      document.body.classList.toggle("sidebar-visible-mobile");
      return;
    }
    toggleSidebar(false);
  }

  function toggleTask(taskId, checked) {
    patchState((current) => {
      const tasks = { ...(current.tasks || {}) };
      if (checked) tasks[taskId] = true;
      else delete tasks[taskId];
      return { ...current, tasks };
    });
  }

  function answerQuiz(quizId, optionIndex) {
    patchState((current) => ({ ...current, quiz: { ...(current.quiz || {}), [quizId]: optionIndex } }));
  }

  function resetProgress() {
    patchState((current) => ({ ...current, tasks: {} }));
  }

  function toggleNavGroup(dayId, open) {
    patchState((current) => ({ ...current, navGroups: { ...(current.navGroups || {}), [dayId]: open } }));
  }

  // 切換開發平台偏好：windows ↔ mac，影響提示詞顯示語言
  function togglePlatform() {
    patchState((current) => ({ ...current, platform: current.platform === "mac" ? "windows" : "mac" }));
  }

  const normalizedSearch = searchQuery.trim().toLowerCase();

  return (
    <>
      <button id="sidebar-restore" className="sidebar-restore-btn" type="button" onClick={restoreSi
        "直接拋出異常，崩潰停止運行",
        "忽視安全規則，強制將不安全的建議輸出給業務人員",
        "觸發 Replanning (重新規劃)，在 Blackboard 上更新 state 並動態尋找其他 Actions 重新產生符合
            <MaterialsOverview materials={course.materials} onPreview={setPreviewMaterial} />
            <Quiz quizList={course.quiz} quizState={state.quiz || {}} onAnswerQuiz={answerQuiz} />
            <footer className="site-footer">教學網站已改用 React 19 + Vite 呈現，課程資料仍由 course-data.js 驅動。</footer>
          </div>
      </div>
      <div className="shell">
        <Sidebar course={course} state={state} progress={progress} onToggleNavGroup={toggleNavGroup} onResetProgress={resetProgress} onHideSidebar={() => toggleSidebar(true)} />
        <main className="main">
          <div id="heroRoot"><Hero meta={course.meta} /></div>
          <div id="content" className="content">
            <Overview meta={course.meta} />
            <FeatureRoadmap course={course} />
            <SharedCase sharedCase={course.sharedCase} />
            {course.meta.days.map((dayMeta) => (
              <DayBlock
                course={course}
                dayMeta={dayMeta}
                taskState={state.tasks || {}}
                onToggleTask={toggleTask}
                accordionOpen={accordionOpen}
                searchQuery={normalizedSearch}
                onPreview={setPreviewMaterial}
                platform={state.platform || "windows"}
                key={dayMeta.id}
              />
            ))}
            <MaterialsOverview materials={course.materials} onPreview={setPreviewMaterial} />
            <Quiz quizList={course.quiz} quizState={state.quiz || {}} onAnswerQuiz={answerQuiz} />
            <footer className="site-footer">教學網站已改用 React 19 + Vite 呈現，課程資料仍由 course-data.js 驅動。</footer>
          </div>
        </main>
      </div>
      <MaterialPreviewModal material={previewMaterial} onClose={() => setPreviewMaterial(null)} />
    </>
  );
}





















































        {
          "name": "u1-3-term.svg",
          "kind": "term",
          "alt": "開發環境、專案骨架與 AI 協作流程 專業術語解釋",
          "spec": "Monorepo / Health Check / PowerShell / Zsh"
        }
      ],
      "prompt": "你是一位同時精通 Windows 11 (PowerShell 7+) 與 macOS (zsh + Homebrew) 環境的資深軟體架構師。現在我們需要初始化一套名為「AI CRM 智慧業務助理」的企業級全端專案。\n此專案採用前後端分離架構，後端使用 Spring Boot 4.0.x + Java 21，前端使用 Vite + React + TypeScript。\n請為我規劃並產生一套「跨平台」的環境檢查與專案初始化腳本，必須同時支援 Windows 與 macOS 兩種開發環境。\n\n要求：\n1. 設計環境檢查腳本，需同時提供 Windows 版 `check-env.ps1` (PowerShell 7) 與 macOS / Linux 版 `check-env.sh` (zsh / bash) 兩個版本，檢查項目一致：\n   - 檢查 JDK 21 是否安裝，並驗證 `java -version` 是否包含 \"21\"。\n   - 檢查 Maven 是否安裝，驗證 `mvn -version`。\n   - 檢查 Node.js 是否安裝 (要求 v18+)，驗證 `node -v` 與 `npm -v`。\n   - 檢查 Docker 是否正在運行，驗證 `docker info`（Windows 使用 Docker Desktop；macOS 可用 Docker Desktop 或 colima）。\n   - 若任何檢查失敗，以醒目顏色（PowerShell 用紅色、bash 用 ANSI 紅色）顯示明確錯誤訊息與對應平台的安裝指引：Windows 提供 winget 或官網下載連結；macOS 提供 Homebrew 安裝指令（例如 `brew ins
      "principle": "AI 協作開發的核心原則是：『先讀懂需求，再產生程式碼，最後用自動化腳本驗證。』本課程同時支援 Windows 與 macOS：Windows 以 PowerShell 7+、macOS 以 zsh + Homebrew 管理工具鏈，確保環境變數與目錄結構一致，避免因作業系統不同導致的通訊死角。"
    },
    {
      "id": "u2",
      "title": "Spring MVC、REST API 與 CRM Domain Modeling",
      "subtitle": "設計 CRM 核心實體、三層架構與防禦性校驗",
      "features": [
        "客戶 / 聯絡人 / 互動 / 商機 REST API",
        "Bean Validation 輸入防呆",
        "合約與續約欄位的領域模型"
      ],
      "time": "13:00 ~ 17:00",
      "goals": [
        "理解 DispatcherServlet 與 Spring MVC 請求生命週期",
        "設計 Customer、Contact、Interaction 領域實體與 DTO 邊界結構",
        "利用 Bean Validation (如 @NotNull, @Email, @Pattern) 保護 REST API 輸入資料",
        "使用 PowerShell Invoke-RestMethod 驗證 API 路由與 400 錯誤處理"
      ],
      "concepts": [
        {
          "heading": "Spring MVC 請求處理生命週期",
          "body": "客戶端的 HTTP 請求首先進入 DispatcherServlet（前端控制器），它會調用 HandlerMapping 尋找對應的 Controller 方法。接著，由 HandlerAdapter 執行該 Controller，Controller 呼叫 Service 處理商業邏輯，最後將結果封裝為 ResponseEntity（在 REST 模式下），並透過 HttpMessageConverter（預設為 Jackson）序列化成 JSON 回傳。"
        },
        {
          "heading": "DTO 邊界隔離原則",
          "body": "Entity 類別反映的是資料庫 schema 與 ORM 關聯結構。我們絕不在 API 入口或出口直接曝露 Entity，否則容易引起不必要的欄位洩露、Jackson 循環參照 StackOverflow 錯誤，或在資料表異動時導致前端 API 直接壞掉。我們必須建立 RequestDTO 與 ResponseDTO 作為資料載體，並於 Service 層進行轉換。"
        }
      ],




















































































































































              ],
              code: {
                language: 'java',
                title: 'ProductController.java — 加入 OpenAPI 標註',
                content: '@RestController\n@RequestMapping("/api/products")\n@Tag(name = "商品管理", description = "商品的新增、查詢、修改與刪除")\npublic class ProductController {\n\n    @Operation(\n        summary = "查詢所有商品",\n        description = "回傳完整商品清單，可加 keyword 參數進行模糊搜尋"\n    )\n    @GetMapping\n    public ResponseEntity<List<Product>> getProducts(\n            @Parameter(description = "商品名稱關鍵字（選填）")\n            @RequestParam(required = false) String keyword) {\n        // ...\n    }\n\n    @Operation(summary = "新增商品")\n    @ApiResponses({\n        @ApiResponse(responseCode = "201", description = "新增成功"),\n        @ApiResponse(responseCode = "400", description = "輸入資料驗證失敗")\n    })\n    @PostMapping\n    public ResponseEntity<Product> createProduct(\n            @Valid @RequestBody Product product) {\n        // ...\n    }\n}'
              }
            },
            {
              title: '設定全域 API 資訊',
              type: 'code',
              paragraphs: [
                '建立一個 `@Configuration` 類別，設定整份文件的標題、版本與聯絡資訊，讓 Swagger UI 頁首顯示正確的專案說明。'
              ],
              code: {
                language: 'java',
                title: 'OpenApiConfig.java',
                content: '@Configuration\npublic class Ope




























































































































































































































































































              type: 'text',
      "subtitle": "GOAP 目標規劃、型別安全 Action Record、Trace 看板與 Playwright 驗收",
      "features": [
        "Embabel Agent Flow 智慧建議",
        "資料不足補件 / 高風險轉交主管",
        "Agent Trace 決策軌跡面板"
      ],
      "time": "13:00 ~ 17:00",
      "goals": [
        "導入 Embabel 0.4.x，以 GOAP action table 設計智慧業務行動建議流程",
        "以 Java 21 Record 實作型別安全與具審核機制 (Replanning) 的 Action 流程",
        "在 React 實作展示 AI 決策流程與 Token 消耗的 Agent Trace 時間線面板",
        "修復 verify-render.mjs，執行 pnpm run verify 確認全站功能通過驗收"
      ],
      "concepts": [
        {
          "heading": "Embabel 智慧 Agent、GOAP 演算法與 Blackboard 機制",
          "body": "一般的 AI API 呼叫是線性的，若 LLM 回答錯誤或資料不足，則流程直接中斷。Embabel 框架引入了 GOAP (Goal-Oriented Action Planning) 規劃。我們在 Blackboard (黑板) 上宣告目前 World State 與最終 Goal（例如：一份通過審核的下一步客戶業務建議），框架會根據已註冊的 `@Action` 的前提 (Preconditions) 與效果 (Effects)，以 A* 或類似演算法動態生成一條執行路徑。若途中某個步驟被拒絕（如審核未通過），Agent 將進行 Replanning 重新規劃路徑。設計 Blackboard 能有效克服大語言模型上下文窗口限制與「記憶力碎片化
























































      "principle": "Embabel 的核心價值在於將不可控的單一 Prompt 推理，轉化為可控、可規劃 (GOAP) 且具備型別約束與輸出審核的 Agent Workflow。透過 Blackboard 與 Agent Trace 機制，讓 AI 決策流程透明、可維護且易於偵錯，符合企業維運的要求。"
    }
  ]
};


























































































































              image: 'assets/teaching-site/09-swagger-ui-dashboard.png',
              imageAlt: 'Swagger UI 儀表板',
              imageCaption: '登入成功後的 Swagger UI 儀表板，可在此進行 Token 授權與 API 存取控制測試。'
            },
            {
              title: 'PowerShell 驗證命令（可選）',
              type: 'code',
              paragraphs: [
                '若您偏好命令列測試，可在 PowerShell 7+ 中依序執行以下命令進行登入與 API 安全性存取測試。'
              ],
              bullets: [
                '管理員帳號：`admin`，密碼為資料庫已加密儲存值',
                '一般用戶帳號：`user`，密碼為資料庫已加密儲存值',
                '1. 使用 `user` 登入取得 Token，測試呼叫新增商品 API，預期應回傳 403 Forbidden',
                '2. 使用 `admin` 登入取得 Token，測試呼叫新增商品 API，預期應回傳 201 Created'
              ],
              code: {
                language: 'powershell',
                title: '驗證 API 存取控制',
                content: '# 1. \u4ee5\u666e\u901a\u7528\u6236\u767b\u5165\n$loginUser = Invoke-RestMethod -Uri \"http://localhost:8080/api/auth/login\" -Method Post -ContentType \"application/json\" -Body \'{\"username\":\"user\",\"password\":\"password\"}\'\n$userToken = $loginUser.token\n\n# 2. \u6e2c\u8a66\u666e\u901a\u7528\u6236\u65b0\u589e\u5























































































































































































































































              }
            },
            {
              title: '@Tool vs ToolCallback 選型原則',
              type: 'text',
              paragraphs: [
                '工具呼叫有兩種宣告方式，選擇依據是你是否有原始碼的修改權限。'
              ],
              bullets: [
                '`@Tool` — 自己開發的業務方法，直接在方法上標註，最簡單',
                '`MethodToolCallback` — 第三方 JAR（無法加註解），用反射包裝現有方法',
                '`FunctionToolCallback` — 需要客製化邏輯的外部工具，用 Lambda + DTO 封裝',
                '無論哪種方式，都應透過 Service 層取得資料，不讓模型直接碰資料庫連線'
              ]
            },
            {
              title: 'MethodToolCallback 包裝第三方工具',
              type: 'code',
              paragraphs: [
                '當需要把第三方 JAR 的方法暴露給模型時，使用 `MethodTo

























































































































































































































































































              bullets: [
                '① 使用者呼叫 `GET http://localhost:8081/api/mcp/chat?message=有哪些商品`',
                '② MCP Client（port 8081）的 ChatClient 收到訊息，交由 LLM 判斷意圖',
                '③ AI 判斷需要查商品，透過已建立的 SSE 連線，向 MCP Server（port 8080）發送 getProducts 工具呼叫請求',
                '④ MCP Server 執行 `ProductTools.getProducts("")` → 查詢 PostgreSQL → 回傳商品 JSON',
                '⑤ MCP Client 收到工具執行結果，AI 組合成自然語言回答',
                '⑥ 使用者收到：「目前商城共有 5 項商品：1. 無線耳機 Pro（NT$2,999，庫存 50）...」'
              ]
            },
            {
              title: 'Skills 是什麼：把專業知識打包給 AI',
              type: 'text',
              paragraphs: [
                'Skills（Agent Skills）是 Anthropic 於 2025 年提出的開放標準：把某個領域的程序性知識（操作流程、規範、範本、輔助腳本）打包成一個資料夾，核心是一份帶有名稱與描述的 SKILL.md 說明檔。AI 平時只看到每個 Skill 的一行描述，judged 相關時才載入完整內容，這種「漸進式載入」讓模型能掛上大量專業知識而不撐爆上下文。',
                '如果說 Tool Calling 與 MCP 解決的是「AI 能呼叫什麼工具、拿到什麼資料」，Skills 解決的則是「AI 應該照什麼流程與規範做事」。兩者互補，不是替代關係。'
              ],
              bullets: [
                'Skill = 資料夾 + SKILL.md（frontmatter 描述）+ 選配的範本與腳本',
                '平時只載入描述，被點名才載入
