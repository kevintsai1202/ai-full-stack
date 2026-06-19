window.COURSE = {
  "title": "AI 賦能全端開發：從零打造企業級智慧應用",
  "subtitle": "結合 Spring Boot 4 與 React 19，從後端分層、向量資料庫到前端 AI 助理，完成具備 RAG 長期記憶與 MCP 工具鏈的企業級 CRM 系統。",
  "meta": {
    "program": "AI 全端開發系列課程",
    "title": "AI 賦能全端開發：從零打造企業級智慧應用",
    "subtitle": "結合 Spring Boot 4 與 React 19，從後端分層、向量資料庫到前端 AI 助理，完成具備 RAG 長期記憶與 MCP 工具鏈的企業級 CRM 系統。",
    "audience": "具備 Java 基礎，想建立全端 AI 應用之開發人員、後端工程師或全端工程師。",
    "totalHours": 32,
    "projectName": "AI CRM 智慧業務助理",
    "heroImage": "cover.png",
    "overviewImage": "office.png",
    "prerequisites": [
      "具備 Java 基礎語法與基本物件導向開發觀念",
      "具備基本 HTML/CSS/JavaScript 與前端框架概念",
      "本機已安裝 Docker Desktop 並能執行基本容器操作"
    ],
    "objectives": [
      "從零建立 Spring Boot + React 的 AI CRM 專案骨架。",
      "用 Spring MVC 設計客戶、商機、互動紀錄與任務管理 API。",
      "用 Spring Data JPA、Specification 與 Flyway 建立可維護的資料模型。",
      "用 Spring Security + JWT 完成登入、角色與 API 授權。",
      "用 React 建立 CRM Dashboard、客戶列表、商機看板與 AI 助理介面。",
      "用 Spring AI ChatClient 建立可串流回應的 CRM 助理。",
      "用 pgvector 建立客戶互動紀錄、產品文件與銷售話術的 RAG 檢索。",
      "用 tool calling 讓 AI 讀取 CRM 的真實 domain data，而不是憑空回答。",
      "完成結訓專案整合、效能調校與 Demo Day 展示驗收。"
    ],
    "techStack": [
      [
        "開發語言",
        "Java 21 / JavaScript (ES6+)"
      ],
      [
        "後端主框架",
        "Spring Boot 4.0.x"
      ],
      [
        "ORM / 持久化",
        "Spring Data JPA / Spring Data JDBC"
      ],
      [
        "資料庫系統",
        "PostgreSQL 16 + pgvector 向量擴充"
      ],
      [
        "資料庫遷移",
        "Flyway Migration"
      ],
      [
        "安全與授權",
        "Spring Security + JWT + bcrypt"
      ],
      [
        "AI 整合框架",
        "Spring AI 2.0.x / ChatClient"
      ],
      [
        "前端技術棧",
        "React 19 / Vite / Vanilla CSS (HSL) / Axios"
      ],
      [
        "自動化測試",
        "JUnit 5 / Mockito / Playwright (驗收測試)"
      ]
    ],
    "days": [
      {
        "id": "day1",
        "n": 1,
        "hours": 16,
        "title": "Spring Boot 核心與資料庫持久化",
        "date": "Day 1 ~ Day 2"
      },
      {
        "id": "day2",
        "n": 2,
        "hours": 16,
        "title": "Spring AI、企業級 RAG 與 React 全端整合",
        "date": "Day 3 ~ Day 4"
      }
    ],
    "format": "混成學習 (Blended Learning)",
    "location": "實體工作坊 + 線上影音課程",
    "completion": [
      "通過本地自動化驗收測試 (pnpm run verify)",
      "完成結訓測驗並提交成果 GitHub 連結"
    ]
  },
  "overview": {
    "pillars": [
      {
        "label": "課程定位",
        "value": "工程底盤 + AI 整合",
        "detail": "先把後端責任邊界與安全性心法講清楚，再把模型、工具與向量 RAG 接回同一個應用，最後整合成可展示的結訓專案。"
      },
      {
        "label": "教學方式",
        "value": "圖解 + 程式碼 + 驗證",
        "detail": "每個單元都用生活化圖解建立直覺，再回到實際專案與設定檔中進行驗證，並使用 PowerShell / Playwright 自動跑測試驗收。"
      },
      {
        "label": "課程產出",
        "value": "全端專案 + Demo Day 驗收",
        "detail": "課程不是只看說明，而是實作出一套包含登入、客戶管理、AI SSE 聊天與 RAG 知識庫查詢的完整 CRM 系統，並在結訓 Demo Day 完成展示。"
      },
      {
        "label": "完成標準",
        "value": "全站通過 verify 自動驗收",
        "detail": "資料庫能起、JWT 能保、AI 能查真實資料、RAG 能回答產品問題、結訓專案能從登入到 AI 對話完整跑通。"
      }
    ]
  },
  "sharedCase": {
    "intro": "本課程採用真實的企業級 B2B CRM 情境，學員將為以下三家代表性企業客戶提供服務，並設計智慧助理協助業務決策：",
    "brands": [
      {
        "id": "brand1",
        "type": "高價值活躍客戶",
        "name": "亞太智能製造 (APIM)",
        "rows": [
          [
            "產業領域",
            "智慧工廠與工業物聯網 (IIoT)"
          ],
          [
            "合約狀態",
            "合約持續中 (至 2027-12-31)"
          ],
          [
            "近期商機",
            "預估金額 **$1,200,000** (生產線 AI 預測維護模組)"
          ],
          [
            "互動概況",
            "近 30 天互動頻繁，對新技術展現高度興趣，無待解決客訴。"
          ]
        ]
      },
      {
        "id": "brand2",
        "type": "流失風險客戶",
        "name": "環球零售巨擘 (GlobalMart)",
        "rows": [
          [
            "產業領域",
            "跨國連鎖量販與電商平台"
          ],
          [
            "合約狀態",
            "即將到期 (2026-09-30)"
          ],
          [
            "近期商機",
            "預估金額 **$850,000** (智能客服與推薦系統)"
          ],
          [
            "互動概況",
            "最近一次互動提及「預算凍結」並正與競品比較，有 1 筆未解決的系統效能客訴。"
          ]
        ]
      },
      {
        "id": "brand3",
        "type": "續約延遲客戶",
        "name": "鼎峰金融科技 (ApexFin)",
        "rows": [
          [
            "產業領域",
            "財富管理與數位信貸評估"
          ],
          [
            "合約狀態",
            "已過期 (2026-05-15)，正處於寬限期"
          ],
          [
            "近期商機",
            "預估金額 **$600,000** (信用風控與合規 AI 助理)"
          ],
          [
            "互動概況",
            "近 60 天無有效互動，`RENEWAL` 商機停滯，業務人員反映窗口聯繫不上。"
          ]
        ]
      }
    ],
    "roles": [
      [
        "林志明",
        "亞太智能製造",
        "採購總監",
        "對系統穩定度與資料合規要求極高，重視實質 ROI。"
      ],
      [
        "陳美玲",
        "環球零售巨擘",
        "技術經理",
        "目前因舊有系統效能問題對我們有些微不滿，正承受主管壓力。"
      ],
      [
        "張大衛",
        "鼎峰金融科技",
        "營運副總",
        "行程極忙，偏好簡潔、有數據支撐的決策報告與合約條款說明。"
      ]
    ]
  },
  "day1": {
    "id": "day1",
    "title": "Spring Boot 核心與資料庫持久化",
    "date": "Day 1 ~ Day 2",
    "learningGoal": "掌握 Spring Boot 核心分層、例外處理與 PostgreSQL/JPA 資料庫版本遷移",
    "units": [
      {
        "id": "u1",
        "title": "開發環境、專案骨架與 AI 協作流程",
        "subtitle": "建立 Windows/macOS 開發環境，搭建 Spring Boot + React Monorepo 專案骨架並進行健康檢查驗證。",
        "time": "09:00 ~ 12:00",
        "features": [
          "驗證 Java / Maven 環境，透過 Spring Initializr 建立課程初始專案，確認可啟動後再進入後續開發。"
        ],
        "goals": [
          "透過 AI 提示詞引導安裝 JDK 21、Maven 3.9+ 與 Git",
          "確認 Java 21 與 Maven 開發環境可正常使用",
          "透過 Spring Initializr 建立課程初始專案",
          "理解 Spring Boot 標準目錄結構與各層職責",
          "了解 AI 協作在開發流程中的適用時機"
        ],
        "principle": "第一章的核心心法在於對齊工具責任。Java 與 Maven 負責執行環境，VS Code 與 AI 助手負責程式碼編輯與解釋，只有一開始把基線對齊，後續開發才不會被無謂的環境問題打斷。",
        "concepts": [
          {
            "heading": "為什麼選 CRM 作為實作題目",
            "group": "AI CRM 情境導入",
            "body": "CRM（Customer Relationship Management）是企業最常見的核心系統之一，涵蓋客戶管理、商機追蹤、互動紀錄與任務分派。選擇 CRM 作為課程實作題目，有三個關鍵理由：\n\n**1. 領域模型清晰且貼近真實**\nCRM 的核心實體（客戶 Customer、商機 Opportunity、互動紀錄 Activity、任務 Task）關係明確，適合學習 JPA Entity 設計、關聯映射與動態查詢。\n\n**2. AI 能力有明確落地場景**\n業務人員需要快速了解客戶現況、預測流失風險、產生行動建議——這些都是 AI 助理能直接創造價值的場景。Tool Calling 可以讓 AI 讀取真實 CRM 資料，RAG 可以讓 AI 搜尋歷史互動紀錄與產品文件。\n\n**3. 全端技術棧完整覆蓋**\n從後端 REST API、資料庫設計、安全認證，到前端 Dashboard、即時對話介面，CRM 系統能自然地串起本課程所有技術主題。\n\n本課程的三家虛擬客戶（亞太智能製造、環球零售巨擘、鼎峰金融科技）代表了 B2B CRM 的三種典型業務情境：高價值活躍客戶、流失風險客戶、續約延遲客戶。"
          },
          {
            "heading": "環境準備重點",
            "group": "環境準備重點",
            "body": "第一章不是在講工具清單，而是在建立後續兩天都要依賴的開發基線。只要 Java、Maven、VS Code 與 AI 協作方式一開始沒有對齊，後面所有章節都會被環境問題反覆打斷。\n\n這一章的核心目標，是讓學員知道哪些工具是編輯器責任、哪些是執行環境責任，以及 AI 助手應該介入在哪一種工作。\n\n- VS Code 負責編輯、導覽、除錯與擴充整合\n- Java 與 Maven 負責專案編譯、依賴下載與執行\n- AI 助手適合做解釋、產生樣板、補測試與協助排錯\n- PowerShell 7+ 是本課程預設終端機環境"
          },
          {
            "heading": "用 AI Agent 安裝開發工具",
            "group": "開發工具安裝與驗證",
            "body": "本課程需要 JDK 21、Maven 3.9+、Git 與 VS Code（含 Java / Spring 擴充套件）。以下提供三組 AI Agent 提示詞，讓 AI 直接在 PowerShell 中完成安裝與環境變數設定。\n\n**JDK 21 安裝提示詞**\n```text\n我使用 Windows 11，請用 winget 幫我安裝 Eclipse Temurin JDK 21。\n安裝完成後，請設定 JAVA_HOME 環境變數（永久生效），\n確認 Path 中已包含 JDK bin 目錄，並執行 java -version 驗證。\n```\n\n**Maven 安裝提示詞**\n```text\n請用 winget 幫我安裝 Apache Maven 最新版。\n設定 M2_HOME 與 Path（永久生效），確認 Maven 使用 JDK 21，\n執行 mvn -version 顯示完整結果。\n```\n\n**Git 安裝提示詞**\n```text\n請用 winget 幫我安裝 Git，完成後設定 user.name 與 user.email，\n並執行 git --version 確認安裝成功。\n```\n\n**VS Code 必要擴充套件**\n- Extension Pack for Java\n- Spring Boot Extension Pack\n- 確認 Java 擴充套件已啟用內建 Lombok 支援"
          },
          {
            "heading": "環境驗證與常見問題",
            "group": "開發工具安裝與驗證",
            "body": "安裝完成後，在 VS Code 終端機中執行以下命令確認環境：\n\n```powershell\njava -version    # 應顯示 openjdk version \"21.x.x\"\nmvn -version     # 應顯示 Apache Maven 3.9.x，Java version: 21\ngit --version    # 應顯示 git version 2.x.x\n```\n\n**判讀重點**\n- Java 版本必須是 21，若不是代表 `JAVA_HOME` 或 `Path` 設定有誤\n- Maven 顯示的 Java version 也必須是 21，否則 Maven 沒有指向正確的 JDK\n- 若出現「不是內部或外部命令」，通常是 PATH 尚未設定或尚未重新載入終端機\n\n**常見錯誤**\n- VS Code 終端機與系統終端機顯示不同版本：重新啟動 VS Code\n- Spring Boot Extension Pack 無補全：重建 Java Language Server"
          },
          {
            "heading": "建立課程專案",
            "group": "建立課程專案",
            "body": "**透過 Spring Initializr 建立課程專案**\n\nSpring Initializr 是 Spring 官方提供的專案產生器，負責生成標準的 Maven 目錄結構、`pom.xml` 依賴設定與主程式進入點。在 VS Code 中可直接透過命令面板呼叫，不需要離開編輯器。\n\n本課程需要的依賴在這一步就全部選定，後續每一章都會在同一個專案上持續疊加功能。\n\n- 開啟 VS Code 命令面板（Ctrl+Shift+P），輸入 `Spring Initializr: Create a Maven Project`\n- 或直接前往 start.spring.io 在瀏覽器中設定後下載\n- Spring Boot 版本：選擇 4.0.0（課程範例基於此版本）\n- Group：`com.example`，Artifact：`tutorial`，Packaging：Jar，Java：21\n- 依賴選擇：Spring Web、Spring Data JPA、PostgreSQL Driver、Flyway Migration\n\n**Spring Initializr 設定摘要 (text)**\n```text\nProject      : Maven\nLanguage     : Java\nSpring Boot  : 4.0.x\nGroup        : com.example\nArtifact     : tutorial\nPackaging    : Jar\nJava         : 21\n\n依賴 (Dependencies):\n  - Spring Web\n  - Spring Data JPA\n  - PostgreSQL Driver\n  - Flyway Migration\n```\n\n---\n\n**確認專案結構與首次啟動**\n\n產生後的專案已包含標準目錄結構。在進入任何功能開發之前，先確認可以啟動，是避免後續被環境問題卡住的最重要步驟。\n\n首次啟動時資料庫連線會失敗（因為尚未啟動 Docker），這是預期行為。本步驟只驗證 Spring Boot 主程式可以被 Maven 執行、類別掃描沒有錯誤。\n\n- `src/main/java/com/example/tutorial/` — Java 原始碼根目錄\n- `src/main/resources/application.properties` — 應用程式設定檔\n- `pom.xml` — Maven 依賴與建置設定\n- 可先把 `application.properties` 中的資料庫設定暫時移除或留空，讓主程式不因找不到 DB 而無法啟動\n\n**首次執行驗證 (powershell)**\n```powershell\n# 進入專案目錄\ncd tutorial\n\n# 編譯並確認無語法錯誤\nmvn clean compile\n\n# 正常輸出應包含 BUILD SUCCESS\n```"
          },
          {
            "heading": "AI 協作的適用時機",
            "group": "AI 協作的適用時機",
            "body": "AI 不應該被當成黑箱產碼器，而是協作式工具。在這門課中，最有價值的 AI 協作情境是：理解 Spring 標註的作用、解釋錯誤訊息，以及在你已知道架構目標時讓它產生對應樣板。\n\n注意：讓 AI 直接生成完整模組、不理解就貼上，是最常見的學習阻斷原因。先弄清楚「這一層應該做什麼」，再讓 AI 協助填入細節。\n\n- 選取特定程式碼後再發問，避免上下文過大\n- 要求加上中文函式註解與重要變數說明\n- 對於重構請先指定目標，例如符合 Controller / Service 分層\n- 對於錯誤排查要附上實際訊息與預期行為"
          }
        ],
        "prompt": "本章我們要建立整個 AI CRM 專案的開發環境基線：用 winget 安裝 JDK 21、Maven、Git，並建立 backend 與 frontend 的 full-stack 專案骨架。理解原理後，依序使用下方提示詞請 AI Agent 協助完成，再用驗證提示詞核對結果。",
        "promptMac": "本章我們要建立整個 AI CRM 專案的開發環境基線：用 Homebrew 安裝 JDK 21、Maven、Git，並建立 backend 與 frontend 的 full-stack 專案骨架。理解原理後，依序使用下方提示詞請 AI Agent 協助完成，再用驗證提示詞核對結果。",
        "prompts": [
          { "title": "① 用 AI 安裝開發環境（JDK 21 / Maven / Git）", "kind": "build", "note": "把環境準備交給 AI，自己只負責核對結果", "text": "我用 Windows 11，請用 winget 幫我安裝 Eclipse Temurin JDK 21、Apache Maven 與 Git，並用 PowerShell 設定好 JAVA_HOME、M2_HOME 與 Path（永久生效）。完成後我要能在新開的終端機分別執行 java -version、mvn -version、git --version 都看到正確版本。（若我用 macOS，請改用 Homebrew 安裝）" },
          { "title": "② 建立 full-stack 專案骨架", "kind": "build", "note": "monorepo：backend（Spring Boot）+ frontend（之後放 React）", "text": "請幫我建立一個課程用的 monorepo 專案，包含 backend（Spring Boot 3.5、Java 21，先只放 web 起手依賴）與 frontend 兩個資料夾，並初始化 git。完成後我要能 cd 進 backend 用 mvn spring-boot:run 把空專案先跑起來。" },
          { "title": "✅ 驗證 — 環境與骨架就緒", "kind": "verify", "note": "確認版本與啟動都正常", "text": "請幫我逐一確認開發環境就緒：執行 java -version、mvn -version、git --version 檢查版本是否正確，並確認 backend 能用 mvn spring-boot:run 成功啟動。若有任何一項不符，請直接幫我修正設定。" },
          { "title": "🔧 排錯 — 版本不對或啟動失敗", "kind": "fix", "note": "常見：電腦上有舊版 JDK", "text": "我跑驗證指令時看到非預期結果（我會貼上輸出）。常見狀況是電腦上有舊版 JDK 讓 java -version 不是 21。請依我貼上的訊息判斷原因，用 PowerShell 幫我修正 JAVA_HOME 與 Path，並讓 Maven 也使用同一版 JDK 21。" }
        ],
        "tasks": [
          {
            "id": "u1-t1",
            "label": "安裝 JDK 21 並完成 JAVA_HOME 設定"
          },
          {
            "id": "u1-t2",
            "label": "安裝 Maven 3.9+ 並完成 Path 設定"
          },
          {
            "id": "u1-t3",
            "label": "安裝 Git 並完成 user.name / user.email 設定"
          },
          {
            "id": "u1-t4",
            "label": "安裝必要 VS Code 外掛並驗證 Java / Maven 版本"
          },
          {
            "id": "u1-t5",
            "label": "透過 Spring Initializr 建立課程專案並確認啟動成功"
          }
        ],
        "materials": [
          {
            "id": "mat1",
            "type": "MD",
            "name": "Windows_與_macOS_開發環境安裝指引",
            "desc": "開發工具鏈 (Java 21, Maven, Node.js, pnpm) 與 PowerShell 7+ 的安裝與驗證指南。"
          }
        ],
        "illustrations": [
          {
            "name": "u1-1.png",
            "kind": "hero",
            "alt": "Full-stack Monorepo",
            "spec": "Windows + Spring Boot + React 啟動骨架"
          },
          {
            "name": "u1-2.png",
            "kind": "diagram",
            "alt": "專案啟動路線",
            "spec": "流程圖：環境檢查 -> 後端骨架 -> 前端骨架 -> 連線驗證"
          },
          {
            "name": "u1-3-term.png",
            "kind": "term",
            "alt": "開發環境、專案骨架與 AI 協作流程 專業術語解釋",
            "spec": "Monorepo / Health Check / PowerShell / Zsh"
          }
        ]
      },
      {
        "id": "u2",
        "title": "Spring MVC、REST API 與 CRM Domain Modeling",
        "subtitle": "設計 CRM 核心 domain model 與 RESTful API，並使用 Bean Validation 進行請求資料驗證。",
        "time": "13:00 ~ 17:00",
        "features": [
          "理解 Spring MVC 的請求流程與 REST API 設計原則，透過 AI Agent 建立一個不依賴資料庫、可立即啟動的客戶 API 示範專案，驗證 Controller / Service 分工正確。"
        ],
        "goals": [
          "理解 Spring MVC 的 DispatcherServlet 請求流程",
          "掌握 REST API 設計原則：URL 是資源名詞，HTTP 方法是動作動詞",
          "用 AI Agent 建立可獨立運行的客戶 REST API（不含資料庫）",
          "理解 Controller / Service 的分工，為後續接入 JPA 做準備"
        ],
        "principle": "分層架構 (DispatcherServlet -> Controller -> Service -> Repository) 是 Spring MVC 的靈魂。Controller 負責轉接與基本驗證，Service 負責商業邏輯，切忌將邏輯混在一起，才能維持程式碼的長期維護性。",
        "concepts": [
          {
            "heading": "Spring MVC 核心架構",
            "group": "Spring Boot 與 MVC 架構",
            "body": "**Spring Boot 為什麼適合教學起點**\n\nSpring Boot 的核心價值是把大量繁瑣設定折疊起來，讓開發者更快進入業務流程與架構理解。Starter Dependencies 幫你組合常見依賴，自動配置則根據 classpath 與設定推導出合理預設。\n\n教學上最重要的不是背誦 Spring Boot 幫你做了哪些設定，而是理解它讓你省下什麼樣的機械工作。\n\n- Starter 讓依賴以情境為單位引入，例如 web、data-jpa\n- Auto-Configuration 根據環境自動建立常見 Bean\n- 開發者的重點因此可以轉移到模組責任與 API 設計\n\n---\n\n**Spring MVC 的核心：請求如何流動**\n\nSpring MVC 是 Spring Framework 的 Web 層模組，也是 Spring Boot 預設的 HTTP 請求處理機制。它採用「前端控制器（Front Controller）」設計模式，所有進來的 HTTP 請求都會先經過一個叫做 DispatcherServlet 的統一入口，再由它分發給對應的 Controller 方法。\n\n這個設計的好處是：請求路由、例外處理、內容協商（JSON / XML 格式）等橫切關注點都由框架統一管理，Controller 只需要專心寫業務邏輯，完全不需要自己解析 HTTP 細節。\n\n- 瀏覽器或前端發出 HTTP 請求（例如 GET /api/customers）\n- DispatcherServlet 接收後，根據 URL 與 HTTP Method 找到對應的 @Controller 方法\n- Controller 呼叫 Service 取得資料\n- @RestController 自動把回傳的 Java 物件序列化成 JSON 回應給前端\n- 這整個流程對開發者幾乎是透明的，Spring Boot 自動組態幫你啟動好一切\n\n**Spring MVC 請求流程 (text)**\n```text\nHTTP 請求（例如 GET /api/customers）\n    ↓\nDispatcherServlet（Spring MVC 統一入口）\n    ↓  根據 @GetMapping(\"/api/customers\") 路由\n@RestController 方法\n    ↓  呼叫業務層\n@Service\n    ↓  取得資料（本章示範：Java List，下一章才接資料庫）\n資料來源\n    ↓\nJSON 序列化 → 回傳給前端\n```\n\n---\n\n**IoC 與 DI 的實務解釋**\n\nIoC 是控制反轉，意思是物件建立與生命週期不再由你手動處理，而是交給 Spring 容器。DI 則是容器在執行期把相依物件注入給需要它的類別。\n\n在 Spring Boot 中，最推薦的形式是建構子注入。這樣可以保證依賴在物件建立時就完整，並且更容易撰寫測試。\n\n- Controller 依賴 Service\n- Service 依賴 Repository\n- 各層不自己 `new` 彼此，而是由容器安排"
          },
          {
            "heading": "REST API 核心觀念",
            "group": "REST API 設計原則",
            "body": "**什麼是 REST API**\n\nREST（Representational State Transfer）是一種以 HTTP 協定為基礎的 API 設計風格。它的核心思想是：把系統中的每一種資料或功能都當成「資源（Resource）」，並用統一的 URL 路徑表示，再搭配 HTTP 方法（動詞）決定你要對這個資源做什麼事。\n\n以本課程的客戶為例：`/api/customers` 就是「客戶資源」的 URL。你要讀取所有客戶就用 GET，要新增客戶就用 POST，路徑本身不需要改，只有 HTTP 方法不同。這種設計讓 API 的意圖一看就清楚。\n\n- REST 不是協定或規範，而是一套設計風格（Architectural Style）\n- URL 表示「資源是什麼」，HTTP 方法表示「對資源做什麼動作」\n- REST API 是無狀態（Stateless）的——每次請求都要帶齊所有必要資訊，伺服器不記住上次\n- 回傳格式通常為 JSON，因為它輕量、跨語言且容易閱讀\n\n**REST 設計直覺：URL 是名詞，HTTP 方法是動詞 (text)**\n```text\n不好的設計（把動作寫進 URL）：\n  GET /getCustomers\n  POST /createCustomer\n  GET /deleteCustomer?id=1\n\nREST 的設計（URL 是資源，動詞由 HTTP Method 決定）：\n  GET    /api/customers        → 取得所有客戶\n  GET    /api/customers/1      → 取得 ID=1 的客戶\n  POST   /api/customers        → 新增一筆客戶\n  PUT    /api/customers/1      → 更新 ID=1 的客戶\n  DELETE /api/customers/1      → 刪除 ID=1 的客戶\n\n### 銷售漏斗：CRM 的核心流程\n\n在進入實作之前，先理解 CRM 領域最重要的一個概念——銷售漏斗（Sales Funnel）。銷售漏斗是一個視覺化模型，描述客戶從「潛在客戶」到「成交」的完整流程，每一階段都會流失一部分客戶，形狀像一個漏斗。\n\n- **潛在客戶（Lead）**：尚未接觸，但有機會成為客戶的對象\n- **接觸中（Contacted）**：已建立聯繫，正在了解需求\n- **洽談中（Negotiating）**：正在討論合作條件與報價\n- **已成交（Closed Won）**：成功簽約，轉為正式客戶\n- **已流失（Closed Lost）**：未能成交，但仍可追蹤原因\n\n為什麼銷售漏斗對這個課程這麼重要？因為本課程的三家示範客戶（APIM、GlobalMart、ApexFin）正好處於漏斗的不同階段：APIM 是已成交的高價值客戶，GlobalMart 是洽談中但可能流失的風險客戶，ApexFin 是已到期需要緊急聯繫的流失客戶。AI CRM 助理的核心任務之一，就是根據客戶在漏斗中的位置，提供不同的應對策略。\n\n這背後對應的 API 設計就是：\n  GET    /api/opportunities        → 列出所有商機及其漏斗階段\n  GET    /api/opportunities/1      → 查看特定商機的詳情\n  PATCH  /api/opportunities/1/stage → 更新商機階段（例如從 Negotiating 推進到 Closed Won）\n\n---\n\n**請求與回應的結構**\n\n了解 HTTP 請求與回應的完整結構，有助於後面除錯時知道要看哪個部分。每次 API 呼叫都包含兩段：你送出的 Request 與伺服器回應的 Response，兩者都有 Header 與 Body。\n\n**REST API 請求與回應範例（新增客戶） (text)**\n```text\n── HTTP Request（前端送出）──────────────────────\nPOST /api/customers HTTP/1.1\nHost: localhost:8080\nContent-Type: application/json    ← Header：告知伺服器 Body 格式\n\n{                                  ← Body：實際資料（JSON）\n  \"name\": \"台積電\",\n  \"level\": \"VIP\",\n  \"email\": \"contact@tsmc.com\",\n  \"notes\": \"半導體龍頭，高價值潛在客戶\",\n  \"status\": \"Active\"\n}\n\n── HTTP Response（Spring Boot 回傳）─────────────\nHTTP/1.1 201 Created              ← 狀態碼\nContent-Type: application/json    ← Header\n\n{                                  ← Body：建立完成的資料（含 ID）\n  \"id\": 7,\n  \"name\": \"台積電\",\n  \"level\": \"VIP\",\n  \"email\": \"contact@tsmc.com\",\n  \"notes\": \"半導體龍頭，高價值潛在客戶\",\n  \"status\": \"Active\"\n}\n```"
          },
          {
            "heading": "HTTP 方法與狀態碼速查",
            "group": "REST API 設計原則",
            "body": "**HTTP 方法與 CRUD 對應**\n\nREST API 主要用到四種 HTTP 方法，分別對應資料庫的 CRUD（建立、讀取、更新、刪除）操作。理解這個對應關係，後面看 Spring Boot 的 `@GetMapping`、`@PostMapping` 等註解就不會覺得陌生。\n\n- GET 是安全且冪等的——同樣的請求重複發多次，結果一樣、資料不受影響\n- POST 通常不是冪等的——發兩次就會建立兩筆資料\n- PUT / PATCH 是冪等的——重複更新同一份資料，結果相同\n- 本課程以 GET / POST 為主，涵蓋查詢客戶與新增客戶兩個最常見情境\n\n**HTTP 方法對照表 (text)**\n```text\n┌──────────┬──────────┬────────────────────────────────┐\n│ HTTP 方法 │ CRUD 操作 │ 說明                           │\n├──────────┼──────────┼────────────────────────────────┤\n│ GET      │ Read     │ 讀取資料，不修改伺服器狀態     │\n│ POST     │ Create   │ 新增資料，回傳 201 Created      │\n│ PUT      │ Update   │ 整筆更新，需傳入完整資料       │\n│ PATCH    │ Update   │ 部分更新，只傳要修改的欄位     │\n│ DELETE   │ Delete   │ 刪除指定資源                   │\n└──────────┴──────────┴────────────────────────────────┘\n\nSpring Boot 對應註解：\n  @GetMapping    → HTTP GET\n  @PostMapping   → HTTP POST\n  @PutMapping    → HTTP PUT\n  @PatchMapping  → HTTP PATCH\n  @DeleteMapping → HTTP DELETE\n```\n\n---\n\n**HTTP 狀態碼速查**\n\n伺服器每次回應都會帶一個三位數的狀態碼，告訴呼叫方「這次請求的結果是什麼」。Spring Boot 預設已處理大部分狀態碼，但在 Controller 中用 `ResponseEntity` 回傳時，需要明確指定。\n\n- `ResponseEntity.ok(data)` → 200 OK\n- `ResponseEntity.notFound().build()` → 404 Not Found\n- `@ResponseStatus(HttpStatus.CREATED)` 加在 POST 方法上 → 201 Created\n- 未明確設定時，Spring Boot 成功回傳預設為 200，例外預設為 500\n\n**常見 HTTP 狀態碼 (text)**\n```text\n2xx 成功\n  200 OK           → 請求成功，回傳資料（GET / PUT / DELETE）\n  201 Created      → 資源建立成功（POST 新增後回傳）\n  204 No Content   → 成功但不回傳內容（DELETE 常用）\n\n4xx 用戶端錯誤\n  400 Bad Request  → 請求格式錯誤或欄位驗證失敗\n  401 Unauthorized → 未提供或提供了無效的身份憑證\n  403 Forbidden    → 有身份但沒有權限執行此操作\n  404 Not Found    → 找不到指定資源（ID 不存在）\n\n5xx 伺服器錯誤\n  500 Internal Server Error → 伺服器端發生未預期的例外\n```"
          },
          {
            "heading": "Controller / Service 怎麼分工",
            "group": "Controller / Service 分層實作",
            "body": "Controller 負責接 HTTP 請求、處理輸入參數與決定回傳格式；Service 負責業務邏輯與資料操作。這個分工讓兩層各司其職，也讓日後更換資料來源（從 List 換成 JPA、從 JPA 換成其他 ORM）時，Controller 完全不需要改動。\n\n本章示範專案刻意只保留 Controller 與 Service 兩層，資料直接放在 Service 的 Java List 裡，讓你先把 Spring MVC 的流程跑通。下一章（1-3、1-4）才會在 Service 下方再加一層 Repository 對接真正的資料庫。\n\n- Controller — 只處理 HTTP 輸入輸出，不含業務判斷\n- Service — 負責業務規則，本章用 List 模擬資料，下一章換成 JPA Repository\n- 兩層分開的好處：換資料來源時只改 Service，Controller 零修改\n\n**本章示範的兩層流向 (text)**\n```text\n[瀏覽器 / 前端]\n    ↓ HTTP 請求\n[Controller]  ← 處理參數與 HTTP 格式\n    ↓ 呼叫業務方法\n[Service]     ← 業務邏輯 + 資料操作\n    ↓\n[List&lt;Customer&gt;]  ← 記憶體資料（本章不用資料庫）\n\n下一章（1-4）的完整三層：\n[Controller] → [Service] → [Repository] → [資料庫]\n```"
          },
          {
            "heading": "Lombok：省去樣板程式碼",
            "group": "Controller / Service 分層實作",
            "body": "Lombok 是 Java 的編譯期程式碼產生器。它透過註解，在編譯時自動為你加上 getter、setter、建構子、`toString`、`equals` 等方法，讓類別定義只保留欄位本身，大幅減少重複樣板。\n\n在 Spring Boot 專案中，只要 `pom.xml` 引入 Lombok 依賴，VS Code 的 Java 擴充套件就會自動識別，不需要額外外掛。\n\n- `@Data` — 等於同時加上 getter、setter、toString、equals、hashCode\n- `@NoArgsConstructor` — 產生無參數建構子（JPA 之後會需要）\n- `@AllArgsConstructor` — 產生包含所有欄位的建構子，方便初始化測試資料\n- `@Builder` — 產生 Builder 模式，適合欄位較多的類別\n\n**Customer.java（使用 Lombok，取代手寫 getter/setter） (java)**\n```java\n// 不用 Lombok：每個欄位都要手寫 getter/setter，程式碼冗長\npublic class Customer {\n    private Long id;\n    private String name;\n    private String level;\n    public Long getId() { return id; }\n    public void setId(Long id) { this.id = id; }\n    // ...繼續寫 name、level 的 getter/setter...\n}\n\n// 使用 Lombok：三個註解搞定一切\n@Data                   // 自動產生 getter / setter / toString / equals\n@NoArgsConstructor      // 無參建構子\n@AllArgsConstructor     // 全欄位建構子\npublic class Customer {\n    private Long id;\n    private String name;\n    private String level;        // VIP / General / New\n    private String email;\n}\n```"
          },
          {
            "heading": "用 AI Agent 建立可獨立運行的示範專案",
            "group": "Controller / Service 分層實作",
            "body": "在進入 JPA 與資料庫之前，先請 AI Agent 幫你建立一個可以立即啟動的 Spring MVC 示範專案，確認 Controller → Service → 回應的流程跑通後，再到後面章節接上資料庫。\n\n- 只需要 `spring-boot-starter-web`，不需要 JPA 或 PostgreSQL\n- 啟動後即可用 PowerShell 測試 API 回應\n\n**AI Agent 提示詞 — 建立 Spring MVC 示範專案 (text)**\n```text\n【建立專案】\n我有一個 Spring Boot 專案，只有 spring-boot-starter-web 依賴。\n請幫我建立一個簡單的客戶 REST API（資料存在記憶體，不用資料庫）：\n- GET /api/customers → 回傳全部客戶\n- GET /api/customers/{id} → 找不到回傳 404\n- POST /api/customers → 新增客戶，回傳 201\n請加上中文函式級別註解。\n\n【驗證 API】\n專案啟動後（port 8080），請幫我用 PowerShell Invoke-RestMethod 測試上面三個端點是否正常回應。\n\n【排查錯誤】\n執行 mvn spring-boot:run 出現以下錯誤：\n[貼上錯誤訊息]\n請幫我找出原因並修正。\n```"
          },
          {
            "heading": "輸入驗證：為什麼不能信任前端傳來的資料",
            "group": "Bean Validation 輸入驗證",
            "body": "目前的 `create` 端點直接把 `@RequestBody` 拿到的物件存進去，前端傳什麼就存什麼。這代表 `name` 傳空字串、`level` 傳不合法的值，全部都會寫入記憶體（或之後的資料庫）而不會報錯。\n\nBean Validation（JSR-380）讓你把驗證規則標在 Model 欄位上，Controller 只需加一個 `@Valid`，Spring 就會在呼叫 Service 之前自動驗證，不合法的請求直接回傳 400，完全不進入業務邏輯。\n\n- 驗證規則標在 Model / DTO 欄位，不散落在 Service 或 Controller 各處\n- 規則跟資料走：不管從哪個 Controller 端點傳入，同一套規則都生效\n- 驗證失敗時 Spring 自動回傳 `400 Bad Request`，並帶上每個欄位的錯誤訊息"
          },
          {
            "heading": "常用 Bean Validation 標註速查",
            "group": "Bean Validation 輸入驗證",
            "body": "以下是最常用到的驗證標註，依照驗證對象分組。所有標註都來自 `jakarta.validation.constraints` 套件，引入 `spring-boot-starter-validation` 即可使用。\n\n- **字串類**：`@NotBlank`（非空且非空白）、`@NotEmpty`（非空但可以全空白）、`@Size(min, max)`（長度範圍）、`@Email`（Email 格式）、`@Pattern(regexp)`（正規表示式）\n- **數字類**：`@NotNull`（非 null）、`@Min(value)`（整數最小值）、`@Max(value)`（整數最大值）、`@DecimalMin`（含小數的最小值）、`@DecimalMax`（含小數的最大值）、`@Positive`（必須大於 0）、`@PositiveOrZero`（大於等於 0）\n- **集合類**：`@NotEmpty`（集合不可空）、`@Size(min, max)`（集合元素數量範圍）\n- **巢狀物件**：`@Valid` 標在欄位上 → 對該物件的欄位遞迴驗證（如 List 裡的每個元素）"
          },
          {
            "heading": "AI 提示詞練習",
            "group": "Bean Validation 輸入驗證",
            "body": "示範專案跑起來後，試著用以下問題請 AI 助手解釋細節，加深對 Spring MVC 的理解：\n\n- 「`@RestController` 與 `@Controller` 差別是什麼？如果改用 `@Controller`，我需要在哪裡加什麼才能讓回傳值變成 JSON？」\n- 「現在的 CustomerService 用 List 存資料，如果我要換成 HashMap 以加速 ID 查詢，應該怎麼改？請幫我重寫 findById 的邏輯。」\n- 「為什麼 getById 回傳 `ResponseEntity<Customer>` 而不是直接回傳 `Customer`？兩種做法有什麼差別？」\n- 「請幫我在 CustomerController 加一個 DELETE /api/customers/{id} 端點，成功刪除回傳 204，找不到回傳 404。」"
          },
          {
            "heading": "CRM Domain Model 設計思維",
            "group": "CRM 領域建模",
            "body": "AI CRM 系統的資料模型圍繞四個核心實體展開，每個實體都對應真實的業務操作：\n\n**Customer（客戶）**\n記錄企業基本資料、產業類型、合約狀態與聯繫人資訊。是整個 CRM 的核心，所有商機與互動都掛在客戶底下。\n\n**Opportunity（商機）**\n追蹤每一筆潛在或進行中的銷售機會，包含預估金額、成交機率、目前階段（Prospecting → Qualification → Proposal → Closing）與預計結案日。\n\n**Activity（互動紀錄）**\n記錄業務人員與客戶的每一次接觸：會議、電話、郵件、拜訪。這些紀錄將被向量化存入 pgvector，成為 AI 助理的長期記憶來源。\n\n**Task（待辦任務）**\n由 AI 助理根據互動分析自動建議，或由業務人員手動建立的行動項目。\n\n這四個實體的關係：Customer → (1:N) → Opportunity / Activity / Task。在後續章節中，我們會用 JPA Entity 實作這個模型，並透過 Specification 支援動態查詢。"
          }
        ],
        "prompt": "請接續 Unit 1。本章我們用 Spring MVC 與 REST API 設計 CRM 的核心領域模型（客戶、聯絡人、互動紀錄、商機）。理解原理後，依序使用下方提示詞請 AI Agent 從記憶體版暖身做到完整 domain model，最後用驗證提示詞核對。",
        "promptMac": "請接續 Unit 1。本章我們用 Spring MVC 與 REST API 設計 CRM 的核心領域模型（客戶、聯絡人、互動紀錄、商機）。理解原理後，依序使用下方提示詞請 AI Agent 從記憶體版暖身做到完整 domain model，最後用驗證提示詞核對。",
        "prompts": [
          { "title": "① 暖身：記憶體版客戶 REST API", "kind": "build", "note": "先不接資料庫，熟悉 Controller / Service 分工", "text": "我有一個只含 web 起手依賴的 Spring Boot 專案。請幫我做一組客戶 REST API，資料先存在記憶體（不接資料庫）：能查全部、查單筆（查不到回 404）、新增（回 201）。請加中文函式註解。完成後我要能用瀏覽器或 PowerShell 打這幾個端點拿到回應。" },
          { "title": "② 設計 CRM 核心 domain model", "kind": "build", "note": "客戶 / 聯絡人 / 互動紀錄 / 商機與其關聯", "text": "請把這個專案擴充成 CRM 的核心領域模型，涵蓋客戶、聯絡人、互動紀錄與商機四個概念以及它們之間的關聯（一個客戶有多位聯絡人、多筆互動、多個商機），並為每個概念補上對應的 REST API。欄位與關聯細節你依 CRM 常識設計即可，我會再核對。請加中文註解。" },
          { "title": "✅ 驗證 — 用 PowerShell 打 API", "kind": "verify", "note": "整理成可重跑的測試腳本", "text": "專案啟動在 8080 後，請用 PowerShell 的 Invoke-RestMethod 幫我測試客戶與商機的新增、查詢端點是否正常回應，並把測試指令整理成我之後可以重跑的腳本。" }
        ],
        "tasks": [
          {
            "id": "u2-t1",
            "label": "閱讀 Spring MVC 請求流程，理解 DispatcherServlet 的角色"
          },
          {
            "id": "u2-t2",
            "label": "用 AI Agent 建立可獨立運行的客戶 REST API 示範專案"
          },
          {
            "id": "u2-t3",
            "label": "用 PowerShell Invoke-RestMethod 驗證 GET / POST 端點可正常回應"
          }
        ],
        "materials": [
          {
            "id": "mat2",
            "type": "MD",
            "name": "REST_API_命名規範與最佳實踐",
            "desc": "企業級 RESTful API 設計原則、URL 命名、HTTP 方法選用與狀態碼規範。"
          }
        ],
        "illustrations": [
          {
            "name": "u2-1.png",
            "kind": "hero",
            "alt": "Spring MVC Domain API",
            "spec": "Controller / Service / DTO 邊界"
          },
          {
            "name": "u2-2.png",
            "kind": "diagram",
            "alt": "MVC 請求生命週期",
            "spec": "流程圖：HTTP Request -> Controller -> Service -> Response DTO"
          },
          {
            "name": "u2-3-term.png",
            "kind": "term",
            "alt": "Spring MVC、REST API 與 CRM Domain Modeling 專業術語解釋",
            "spec": "DispatcherServlet / DTO / Bean Validation"
          }
        ]
      },
      {
        "id": "u3",
        "title": "PostgreSQL、Flyway、JPA 與動態查詢",
        "subtitle": "使用 Docker 建立 PostgreSQL 資料庫，整合 Flyway 版控遷移，並以 Specification 實作多條件動態查詢。",
        "time": "13:00 ~ 17:00",
        "features": [
          "安裝 Docker Desktop，透過 AI Agent 產生 PostgreSQL 18（含 pgvector）的 docker-compose.yml 與 application.yml，啟動容器後用 Flyway 管理 Schema 演進。",
          "使用 Entity、Repository 與 Query Method 將資料表操作提升為物件導向程式碼。"
        ],
        "goals": [
          "安裝 Docker Desktop 並確認可執行容器",
          "用 AI Agent 產生 docker-compose.yml（PostgreSQL 18 + pgvector）",
          "用 AI Agent 設定 application.yml 資料庫連線與 Flyway",
          "掌握 Flyway 版本腳本的命名規則與管理方式",
          "理解 ORM 與 JPA 在專案中的角色",
          "掌握常見 Entity 註解用途",
          "能用 Query Method 表達常見查詢需求"
        ],
        "principle": "持久層的心法在於版本管理與關聯設計。Flyway 保證了資料庫結構的可追溯性，而 JPA Mapping 則需注意延遲載入 (Lazy Load) 與 N+1 查詢問題，動態查詢則透過 Specification 保持代碼優雅與靈活。",
        "concepts": [
          {
            "heading": "為什麼資料庫要容器化",
            "group": "Docker 與 PostgreSQL 容器化",
            "body": "教學專案最怕的是每位學員本機資料庫版本不同、初始化內容不同、安裝方式也不同。Docker 的價值在於把這些差異壓到最低，讓資料庫可以被快速重建與共享。\n\n這個課程後面還需要 `pgvector` 支援向量欄位，因此從一開始就直接用帶有該擴充功能的 PostgreSQL 映像。"
          },
          {
            "heading": "用 AI Agent 產生 docker-compose.yml",
            "group": "Docker 與 PostgreSQL 容器化",
            "body": "確認 Docker Desktop 已正常運行後，接著請 AI Agent 幫你在專案根目錄建立 `docker-compose.yml`。本課程使用 PostgreSQL 18 並搭配 pgvector 向量擴充（Day 2 的 RAG 功能依賴它），讓 AI Agent 直接生成並啟動容器，你再對照下方說明理解每個欄位的用途。\n\n**AI Agent 提示詞 — 建立 docker-compose.yml (text)**\n```text\n【提示詞 1 — 請 AI Agent 建立並啟動】\n請在我的 Spring Boot 專案根目錄建立 docker-compose.yml，需求如下：\n- 使用 pgvector/pgvector:pg18 映像（PostgreSQL 18 + pgvector 向量擴充）\n- 資料庫名稱：learn_spring\n- 使用者：postgres，密碼：password\n- 本機 5432 埠對應容器 5432 埠\n- 使用具名卷（named volume）讓資料持久化，容器重建後資料不遺失\n- 每個設定項目加上中文註解\n\n建立完成後請幫我執行 docker-compose up -d，\n再執行 docker ps，確認容器狀態為 Up。\n\n【提示詞 2 — 排查容器啟動失敗】\n我執行 docker-compose up -d 後容器狀態不是 Up，\ndocker logs 顯示：\n[貼上錯誤訊息]\n請幫我找出原因並修正。\n```"
          },
          {
            "heading": "Flyway 的角色",
            "group": "Flyway 資料庫版本管理",
            "body": "Flyway 負責管理資料庫 Schema 的歷史。每一個版本腳本都代表一次明確變更，例如建立資料表、加索引、插入測試資料等。\n\n正確的做法不是回頭改舊版本，而是新增下一個版本。這樣整個團隊與教學現場都能用同一條遷移歷史來還原資料庫。\n\n- V1 通常處理基礎 Schema\n- V2 之後處理擴充欄位、測試資料或向量表\n- 應用啟動時 Flyway 會依序檢查並套用缺少的版本"
          },
          {
            "heading": "Flyway 命名規則與雙底線陷阱",
            "group": "Flyway 資料庫版本管理",
            "body": "Flyway 的 SQL 腳本必須嚴格遵守命名格式，否則會被直接忽略，造成 Schema 沒有如預期建立，卻也沒有任何錯誤訊息。\n\n- 格式：`V<版本>__<描述>.sql`，版本與描述之間是「雙底線」（兩個 `_`）\n- `V1__init_schema.sql` — 正確：建立所有 CRM 資料表與初始資料\n- `V1_init_schema.sql` — 錯誤：單底線，Flyway 無法辨識\n- 版本號不能重複，且一旦執行就不能修改檔案內容（雜湊值會不符）\n- 需要新增欄位時，建立 V2 而不是修改 V1\n\n**Flyway 腳本命名規範 (text)**\n```text\n格式：  V<Version>__<Description>.sql\n         ↑         ↑↑\n      大寫V       雙底線（最常出錯的地方！）\n\n範例：\n  V1__init_schema.sql        ← 建立所有 CRM 資料表 + seed data（初始化集中在一支）\n  V2__add_vector_table.sql   ← 後續擴充（例如 RAG 向量表）\n```"
          },
          {
            "heading": "Flyway vs ddl-auto 職責對照",
            "group": "Flyway 資料庫版本管理",
            "body": "Flyway 與 `ddl-auto` 都能管理 Schema，但定位完全不同。本課程選擇讓兩者各司其職：Flyway 負責所有 Schema 演進，JPA 只負責驗證 Entity 與資料庫是否吻合。\n\n**Schema 管理職責分工 (text)**\n```text\n┌─────────────────────────────────────────────────────┐\n│  開發初期（本機驗證）                                 │\n│  ddl-auto: update  → 讓 JPA 自動同步，快速迭代       │\n│  優點：免寫 SQL，欄位改動立即生效                    │\n│  缺點：無版本記錄、無法在其他環境重現相同狀態         │\n├─────────────────────────────────────────────────────┤\n│  測試機 / 正式機（所有共享環境）                     │\n│  ddl-auto: validate + Flyway 管理                    │\n│  優點：每次 Schema 變更都有腳本可追蹤、重現與回滾    │\n│  優點：CI/CD 自動執行遷移，跨環境 Schema 一致        │\n│  優點：Flyway 分散式鎖防止並發啟動的 Schema 競爭    │\n└─────────────────────────────────────────────────────┘\n\n建議時機：Entity 設計穩定後，立即把 ddl-auto: update\n  改為 validate，並用 V1 建立完整的初始化腳本\n```"
          },
          {
            "heading": "用 AI Agent 設定 application.yml 資料庫連線",
            "group": "資料庫連線配置",
            "body": "容器啟動成功後，接著請 AI Agent 修改 Spring Boot 的 `application.yml`，加入資料庫連線資訊與 Flyway 設定。設定完成後執行 `mvn spring-boot:run`，在 log 中看到 Flyway 完成遷移的訊息就代表整個資料庫環境已就緒。\n\n**AI Agent 提示詞 — 設定 application.yml (text)**\n```text\n【提示詞 1 — 請 AI Agent 設定資料庫連線】\n請修改我的 Spring Boot 專案的 src/main/resources/application.yml，\n加入以下設定（若已存在請直接修改，不要重複）：\n1. datasource：連線到 localhost:5432/learn_spring，帳號 postgres，密碼 password\n2. flyway：enabled: true，baseline-on-migrate: true，腳本位置 classpath:db/migration\n3. jpa：ddl-auto: validate（由 Flyway 管理 Schema，JPA 只驗證結構）\n每個設定項目請加上中文註解說明用途。\n\n【提示詞 2 — 驗證連線與 Flyway 遷移】\n設定完成後請幫我執行 mvn spring-boot:run，\n確認 log 中出現 Successfully applied N migration(s) 的訊息。\n若出現連線錯誤或 Flyway 失敗，請幫我找出原因並修正。\n```"
          },
          {
            "heading": "JPA 核心概念與 Entity 設計",
            "group": "JPA Entity 設計與 Repository",
            "body": "**JPA 解決了什麼問題**\n\n如果每一次存取資料都要手寫 SQL、手動把結果塞回 Java 物件，開發與維護成本會很高。JPA 的價值，是讓你以物件模型思考資料，而不是每次都回到低階映射。\n\n這不表示 SQL 不重要，而是代表常見 CRUD 與查詢可以交給更高階的抽象處理。\n\n---\n\n**Entity 設計要點**\n\n- `@Entity` 表示這個類別要對應資料表\n- `@Id` 與主鍵生成策略決定資料識別方式\n- 欄位型別與 nullable 規則要與資料庫 Schema 一致\n- 註解與欄位命名一旦混亂，後續查詢與維護成本會快速升高"
          },
          {
            "heading": "Lombok 與 JPA 的搭配注意事項",
            "group": "JPA Entity 設計與 Repository",
            "body": "1-2 章節已介紹過 Lombok 的基本用法。在 JPA Entity 中使用時，有一個額外要求：`@NoArgsConstructor` 是必要的，因為 JPA 在從資料庫讀取資料時，需要先用無參建構子建立物件實例，再逐欄填入資料。\n\nVS Code 中若發現 Lombok 的 `@Data` 等註解出現紅線，請確認 Java 擴充套件已啟用內建 Lombok 支援（不需要另裝獨立外掛）。"
          },
          {
            "heading": "Repository 與 Query Method",
            "group": "JPA Entity 設計與 Repository",
            "body": "**Repository 與 Query Method**\n\n`JpaRepository` 提供大量現成的 CRUD 能力，讓你不需要為每個模組都重寫基礎存取程式碼。當查詢需求足夠單純時，甚至可以直接透過方法命名表達條件。\n\n例如 `findByNameContaining` 這類寫法，本身就是查詢語意，Spring Data 會把它翻譯成對應 SQL。\n\n**Query Method 範例 (java)**\n```java\npublic interface CustomerRepository extends JpaRepository<Customer, Long> {\n    List<Customer> findByNameContaining(String keyword);\n}\n```\n\n---\n\n**Query Method 命名規則分解**\n\nSpring Data JPA 會解析介面方法名稱，把它翻譯成 SQL。這個機制讓常見查詢完全不用寫 SQL，只要把條件寫在方法名稱裡。\n\n- `find` — SELECT 操作\n- `By` — WHERE 條件起點\n- `Name` — 對應 `name` 欄位\n- `Containing` — 轉為 LIKE '%value%'\n- `IgnoreCase` — 不區分大小寫（LOWER() 函數）\n\n**CustomerRepository.java (java)**\n```java\n@Repository\npublic interface CustomerRepository extends JpaRepository<Customer, Long> {\n\n    // 翻譯為：SELECT * FROM customers WHERE LOWER(name) LIKE LOWER('%name%')\n    List<Customer> findByNameContainingIgnoreCase(String name);\n\n    // JpaRepository 繼承後立即擁有的方法（不需要自己寫）：\n    // save(entity)        — INSERT 或 UPDATE\n    // findById(id)        — SELECT WHERE id = ?\n    // findAll()           — SELECT * FROM table\n    // deleteById(id)      — DELETE WHERE id = ?\n    // count()             — SELECT COUNT(*)\n}\n```"
          },
          {
            "heading": "@Transactional 核心規則",
            "group": "交易管理 (@Transactional)",
            "body": "Spring 的 `@Transactional` 是寫入操作的必備標註，核心規則：\n\n- **寫入方法一定要加 `@Transactional`**：新增、修改、刪除若沒有包在交易中，部分失敗時資料會處於不一致狀態\n- **查詢方法加 `readOnly = true`**：告知 JPA 不需要做變更追蹤（Dirty Checking），對大量查詢有明顯效能提升\n- **標註應加在 Service 層**：Controller 層不應直接管理交易邊界\n- **建議在 Service 類別上加 `@Transactional(readOnly = true)`**，個別寫入方法再覆寫為 `@Transactional`\n\n```java\n@Service\n@Transactional(readOnly = true)  // 預設唯讀\npublic class CustomerService {\n    public List<Customer> findAll() { ... }  // 繼承 readOnly\n\n    @Transactional  // 覆寫為可寫入\n    public Customer create(Customer c) { ... }\n}\n```"
          },
          {
            "heading": "@Modifying 與批次操作",
            "group": "交易管理 (@Transactional)",
            "body": "**@Query 改資料必須同時加上 @Modifying**\n\nRepository 的派生方法（如 `save()`、`deleteById()`）已由 Spring Data 內部處理好交易邏輯，直接呼叫就行。但若你用 `@Query` 自行撰寫 JPQL 的 UPDATE 或 DELETE，Spring Data JPA 預設把它當成 SELECT 語句對待，必須額外加上 `@Modifying` 才能正確執行。\n\n缺少 `@Modifying` 時，Spring Data 會在執行時拋出 `InvalidDataAccessApiUsageException`，錯誤訊息是「Executing an update/delete query」，容易讓人誤以為是 SQL 語法問題。\n\n- `@Modifying` — 告知 Spring Data 這個 `@Query` 是寫入操作，不是查詢\n- `@Transactional` — 寫入操作仍需交易包覆，通常加在 Service；若 Repository 方法需要獨立交易可直接加在此\n- 兩個標註缺一不可，順序不影響結果\n\n---\n\n**@Modifying 批次刪除 vs 派生刪除的選用時機**\n\n- **需要批次效率**（萬筆以上）→ 用 `@Modifying` + `@Query DELETE`，直接下 SQL，不載入 Entity，效率高\n- **需要觸發生命週期事件**（`@PreRemove`、`@EntityListeners`）→ 用派生刪除方法，Spring Data 先查再逐筆刪，每筆都經過 Hibernate 管理\n- **一般 CRUD**（單筆刪除）→ 用 `deleteById()`，Spring Data 已處理好交易與快取\n- **批次更新**（改特定條件下的欄位）→ 幾乎都用 `@Modifying` + `@Query UPDATE`，派生方法做不到批次更新"
          },
          {
            "heading": "Audit 欄位：自動記錄建立與修改時間",
            "group": "Audit 稽核欄位",
            "body": "正式應用中的資料表幾乎都需要 `created_at`、`updated_at`，用來記錄每筆資料的建立與最後修改時間。如果每個 Entity 都手動在 `save()` 前設定，既容易漏，也會讓業務邏輯摻雜技術細節。\n\nSpring Data JPA 的 **JPA Auditing** 功能可以讓這兩個欄位完全自動填入：建立時寫入 `created_at`，之後每次更新只更新 `updated_at`，開發者不需要寫任何設定程式碼。\n\n- 在啟動類別（或 `@Configuration` 類別）加上 `@EnableJpaAuditing`，啟用整個機制\n- 在 Entity 或共用父類別加上 `@EntityListeners(AuditingEntityListener.class)`，告知 Hibernate 要監聽生命週期事件\n- 用 `@CreatedDate` / `@LastModifiedDate` 標記對應欄位，Spring 自動在寫入前填值\n\n**LearnSpringApplication.java — 啟用 JPA Auditing (java)**\n```java\n@SpringBootApplication\n@EnableJpaAuditing  // 啟用 JPA Auditing，應用程式啟動時生效\npublic class LearnSpringApplication {\n    public static void main(String[] args) {\n        SpringApplication.run(LearnSpringApplication.class, args);\n    }\n}\n```"
          },
          {
            "heading": "BaseAuditEntity 設計與繼承",
            "group": "Audit 稽核欄位",
            "body": "**BaseAuditEntity：把 Audit 欄位抽成共用父類別**\n\n多個 Entity 都需要 audit 欄位時，建議抽成 `BaseAuditEntity` 讓所有 Entity 繼承。`@MappedSuperclass` 表示這個類別本身不對應任何資料表，只把欄位定義「繼承」給子類別的資料表。\n\n**BaseAuditEntity.java (java)**\n```java\n@MappedSuperclass\n@EntityListeners(AuditingEntityListener.class)  // 監聽 @PrePersist / @PreUpdate 事件\npublic abstract class BaseAuditEntity {\n\n    // 建立時間：@PrePersist 時由 Spring 自動填入，之後不允許修改\n    @CreatedDate\n    @Column(name = \"created_at\", updatable = false, nullable = false)\n    private LocalDateTime createdAt;\n\n    // 最後修改時間：每次 @PreUpdate 時自動更新\n    @LastModifiedDate\n    @Column(name = \"updated_at\", nullable = false)\n    private LocalDateTime updatedAt;\n\n    public LocalDateTime getCreatedAt() { return createdAt; }\n    public LocalDateTime getUpdatedAt() { return updatedAt; }\n}\n```\n\n---\n\n**Entity 繼承 BaseAuditEntity**\n\nCustomer 繼承 `BaseAuditEntity` 後，資料表自動多出 `created_at` 與 `updated_at` 兩欄。記得補上對應的 Flyway 遷移腳本，否則 `ddl-auto: validate` 會因欄位不符而啟動失敗。\n\n**Customer.java — 繼承 Audit 父類別 (java)**\n```java\n@Data\n@NoArgsConstructor\n@AllArgsConstructor\n@Entity\n@Table(name = \"customers\")\npublic class Customer extends BaseAuditEntity {\n\n    @Id\n    @GeneratedValue(strategy = GenerationType.IDENTITY)\n    private Long id;\n\n    @Column(nullable = false)\n    private String name;\n\n    @Column(nullable = false)\n    private String level;\n\n    @Column(nullable = false)\n    private String email;\n\n    private String phone;\n\n    private String industry;\n\n    // created_at 與 updated_at 從 BaseAuditEntity 繼承，不需要重複宣告\n}\n```"
          },
          {
            "heading": "為什麼需要動態查詢",
            "group": "Specification 動態查詢",
            "body": "**Query Method 的限制：條件一多就爆炸**\n\nQuery Method 命名在條件固定時非常好用，但商業搜尋場景通常有多個「可選過濾條件」：使用者可能同時填名稱與價格上限，也可能只填其中一個，甚至全不填。\n\nQuery Method 無法處理「條件可有可無」的動態查詢——你必須為每種組合寫一個方法，或在 Service 層用 if-else 分支呼叫不同查詢，兩種做法維護成本都很高。\n\n**難以維護的 if-else 分支查詢（反例） (java)**\n```java\n// ❌ 反例：每多一個可選條件就要翻倍方法數\npublic List&lt;Customer&gt; search(String name, Double maxPrice, Boolean inStock) {\n    if (name != null && maxPrice != null && inStock != null) {\n        return repo.findByNameContainingAndPriceLessThanAndStockGreaterThan(...);\n    } else if (name != null && maxPrice != null) {\n        return repo.findByNameContainingAndPriceLessThan(...);\n    } else if (name != null) {\n        return repo.findByNameContaining(name);\n    }\n    // ... 還有更多分支\n}\n```\n\n---\n\n**Specification：動態查詢的正確解法**\n\n`Specification<T>` 是 Spring Data JPA 內建的動態查詢機制，核心概念是把每個查詢條件包裝成一個獨立物件，再自由組合。\n\n每個 Specification 本質上是一個 lambda，簽章為 `(root, query, cb) -> Predicate`：`root` 代表 FROM 的 Entity、`cb`（CriteriaBuilder）是 WHERE 條件的工廠。回傳 `null` 就代表「這個條件不套用」，非常適合可選欄位。\n\n- 不需要修改 SQL 字串，只在 Java 程式碼層組合條件\n- 每個條件獨立封裝，可單獨測試每一個 Predicate\n- `null` 條件自動被 Spring Data 跳過，不會影響查詢語意\n- 透過 `.and()` / `.or()` 自由串接，組合結果仍是一個 Specification 物件"
          },
          {
            "heading": "Specification 與 Query Method 的選用時機",
            "group": "Specification 動態查詢",
            "body": "- **Query Method**：條件固定、不超過 2 個欄位組合 → 命名直觀、無額外程式碼\n- **Specification**：有 1 個以上的可選條件、條件組合數 > 3 → 維護性與可讀性大幅提升\n- **`@Query` JPQL**：需要 GROUP BY、子查詢、特殊函數等 Specification 難以表達的語意\n- 兩者可以共存於同一個 Repository，依查詢複雜度選用不同方式"
          },
          {
            "heading": "用 AI Agent 為既有專案加入 JPA",
            "group": "Specification 動態查詢",
            "body": "1-2 章節已建立一個用 Java List 存放資料的 Spring MVC 專案。本章的目標是讓 AI Agent 幫你把資料來源從記憶體 List 換成真正的 PostgreSQL 資料庫，Controller 完全不需要修改，只改 Customer 類別與 CustomerService。\n\n- pom.xml 加入 JPA 與 PostgreSQL 依賴\n- Customer.java 加上 `@Entity`、`@Table`、`@Id` 等 JPA 註解\n- CustomerService 的 List 換成 CustomerRepository，讓資料寫入資料庫\n\n**AI Agent 提示詞 — 為 Spring MVC 專案加入 JPA (text)**\n```text\n【步驟一：加入依賴】\n我在 1-2 建立了一個 Spring Boot 專案（只有 spring-boot-starter-web），\n請幫我在 pom.xml 加入以下依賴：\n- spring-boot-starter-data-jpa\n- postgresql\n- lombok（若尚未加入）\n\n【步驟二：升級 Customer 類別】\n我目前的 Customer.java 只是普通 POJO，\n請幫我加上 JPA 註解（@Entity、@Table、@Id、@GeneratedValue、@Column），\n並改用 Lombok 的 @Data、@NoArgsConstructor、@AllArgsConstructor 取代手寫 getter/setter。\n\n【步驟三：建立 Repository 並更新 Service】\n請幫我：\n1. 建立 CustomerRepository.java，繼承 JpaRepository<Customer, Long>\n2. 更新 CustomerService.java，把原本的 List<Customer> 換成注入 CustomerRepository，\n   讓 getAll、findById、save 方法改用資料庫操作\n\n完成後請執行 mvn spring-boot:run，確認應用程式能啟動並成功連線資料庫。\n```"
          },
          {
            "heading": "CRM 資料模型如何對應 JPA Entity",
            "group": "CRM 資料模型實作",
            "body": "將前面章節介紹的 CRM Domain Model 對應到 JPA Entity 設計：\n\n**Customer Entity**\n核心欄位：`name`、`industry`（產業類型）、`contractStatus`（合約狀態，Enum）、`contractEndDate`。一對多關聯到 Opportunity、Activity、Task。\n\n**Opportunity Entity**\n核心欄位：`title`、`amount`（預估金額）、`probability`（成交機率）、`stage`（Enum：PROSPECTING / QUALIFICATION / PROPOSAL / CLOSING / WON / LOST）、`expectedCloseDate`。多對一關聯到 Customer。\n\n**Activity Entity**\n核心欄位：`type`（Enum：MEETING / CALL / EMAIL / VISIT）、`summary`（摘要文字，將被向量化）、`occurredAt`。多對一關聯到 Customer。\n\n**設計重點**\n- 所有 Entity 繼承 `BaseAuditEntity` 自動記錄建立/修改時間\n- 用 Specification 支援「依產業類型 + 合約狀態 + 商機金額範圍」的動態組合查詢\n- Activity 的 `summary` 欄位將在 Day 3~4 被 ETL 向量化，存入 pgvector 作為 AI 長期記憶"
          }
        ],
        "prompt": "請接續 Unit 2。本章我們把資料落地：用 Docker 跑 PostgreSQL、用 Flyway 管理 schema、用 JPA 與動態查詢操作資料。理解原理後，依序使用下方提示詞請 AI Agent 完成容器化、遷移與 JPA 接線，最後用驗證提示詞確認資料持久化。",
        "promptMac": "請接續 Unit 2。本章我們把資料落地：用 Docker 跑 PostgreSQL、用 Flyway 管理 schema、用 JPA 與動態查詢操作資料。理解原理後，依序使用下方提示詞請 AI Agent 完成容器化、遷移與 JPA 接線，最後用驗證提示詞確認資料持久化。",
        "prompts": [
          { "title": "① 用 Docker 起一個 PostgreSQL", "kind": "build", "note": "用 pgvector 映像，之後 RAG 要用向量擴充", "text": "請在專案根目錄建立 docker-compose 設定來啟動 PostgreSQL（用 pgvector/pgvector:pg18 映像，因為之後 RAG 會用到向量擴充），資料庫名 learn_spring、本機 5432 埠、資料要持久化。每個設定加中文註解。完成後我要能啟動容器並確認它是 Up。" },
          { "title": "② 讓 Flyway 接管資料庫 schema", "kind": "build", "note": "JPA 只驗證、由 Flyway 建表", "text": "請設定 Spring Boot 連到剛才的 PostgreSQL，並改用 Flyway 管理資料表結構（JPA 設成只驗證、不自動建表）。請建立第一支 migration，把 CRM 的客戶 / 聯絡人 / 互動 / 商機建表並塞入少量示範資料。完成後啟動時我要在 log 看到 Flyway 成功套用 migration。" },
          { "title": "③ domain model 接上 JPA 與動態查詢", "kind": "build", "note": "客戶查詢支援多條件任意組合", "text": "請把現有的 CRM domain model 從記憶體版改為 JPA Entity 並接上資料庫，Repository 用 Spring Data JPA。客戶查詢要支援多條件動態組合（例如產業、分級、關鍵字可任意搭配），請用適合的做法實作可組合的搜尋。請加中文註解。" },
          { "title": "✅ 驗證 — 資料真的進資料庫且持久化", "kind": "verify", "note": "重啟後資料仍在、Flyway 不重複建表", "text": "請幫我確認資料確實寫進 PostgreSQL：啟動專案後新增一筆客戶，用查詢 API 或進 psql 確認資料存在；重啟專案後資料仍在，且 Flyway 沒有重複建表或報錯。" }
        ],
        "tasks": [
          {
            "id": "u3-t1",
            "label": "用 AI Agent 安裝 Docker Desktop 並執行 docker run hello-world 驗證"
          },
          {
            "id": "u3-t2",
            "label": "用 AI Agent 建立 docker-compose.yml 並啟動 PostgreSQL 18 容器"
          },
          {
            "id": "u3-t3",
            "label": "用 AI Agent 設定 application.yml，執行 mvn spring-boot:run 確認 Flyway 遷移成功"
          },
          {
            "id": "u3-t4",
            "label": "用 AI Agent 為 1-2 專案加入 JPA 依賴並更新 Product.java 為 Entity"
          },
          {
            "id": "u3-t5",
            "label": "用 AI Agent 建立 CustomerRepository 並更新 CustomerService 改用資料庫"
          },
          {
            "id": "u3-t6",
            "label": "執行 mvn spring-boot:run，確認 API 仍正常運作且資料已寫入資料庫"
          }
        ],
        "materials": [
          {
            "id": "mat3",
            "type": "MD",
            "name": "Docker_PostgreSQL_與_pgvector_設定說明",
            "desc": "使用 Docker Compose 啟動 PostgreSQL 及向量擴充套件 pgvector 的詳細設定與 Volume 持久化配置。"
          }
        ],
        "illustrations": [
          {
            "name": "u3-1.png",
            "kind": "hero",
            "alt": "PostgreSQL + JPA",
            "spec": "Flyway migration 與動態查詢"
          },
          {
            "name": "u3-2.png",
            "kind": "diagram",
            "alt": "資料持久化與搜尋",
            "spec": "流程圖：Migration -> Entity Mapping -> Repository -> Specification"
          },
          {
            "name": "u3-3-term.png",
            "kind": "term",
            "alt": "PostgreSQL、Flyway、JPA 與動態查詢 專業術語解釋",
            "spec": "Flyway / JPA Entity / Specification"
          }
        ]
      },
      {
        "id": "u4",
        "title": "Spring Security、JWT、OpenAPI 與企業級錯誤處理",
        "subtitle": "整合 JWT 簽發與 Security 認證保護 API，建立 ProblemDetail 全域錯誤處理，並以 Swagger 導出 API 文件。",
        "time": "09:00 ~ 12:00",
        "features": [
          "透過 springdoc-openapi 自動產生互動式 API 文件，讓前端與測試人員不需要看程式碼就能理解與呼叫 API。",
          "用 @RestControllerAdvice 統一攔截應用程式例外，回傳格式一致的錯誤回應，讓前端不再猜測錯誤格式。",
          "善用 @Slf4j 建立有語意的結構化日誌，透過 application.yml 設定 Log層級，再用 Spring Actuator 在不重啟應用的情況下動態調整。"
        ],
        "goals": [
          "理解 OpenAPI 規範與 Swagger UI 的關係",
          "加入 springdoc-openapi 並確認 Swagger UI 可正常存取",
          "用標註豐富 Controller 的 API 說明",
          "設定全域 API 資訊（標題、版本、聯絡方式）",
          "理解沒有全域例外處理的系統有什麼問題",
          "建立統一的 ErrorResponse 回應格式",
          "用 @RestControllerAdvice 集中處理各類例外",
          "自訂業務例外類別，讓錯誤語意更清楚",
          "理解 Spring Boot 預設 Log 機制與層級",
          "用 @Slf4j 寫出結構化、有語意的 Log",
          "透過 application.yml 設定各套件的 Log 層級",
          "用 Spring Actuator 動態調整 Log 層級，無需重啟應用",
          "理解 AOP 解決的問題與核心詞彙",
          "知道 Spring AOP 用 Proxy 實現，並了解其限制",
          "能辨識哪些 Spring 功能背後使用了 AOP",
          "知道在什麼情況下才需要直接撰寫 AOP",
          "理解 Spring Security 的 Authentication（認證）與 Authorization（授權）核心概念",
          "學會以 AI 輔助建立 JWT 過濾器並在 Token 內寫入使用者角色（ADMIN/USER）",
          "實作角色基礎存取控制（RBAC），客戶資料查詢 API 供業務人員使用，客戶資料刪除 API 僅限管理員",
          "保護 Swagger API 文件，限定僅能透過認證身分存取"
        ],
        "principle": "企業級後端的防線在於安全與一致性。Spring Security 與 JWT filter 組成了堅實的驗證防禦，全域錯誤處理 RFC 7807 則給予前端可預期的錯誤 JSON 結構，結合 Swagger 實現規格即文件。",
        "concepts": [
          {
            "heading": "為什麼需要 API 文件",
            "group": "OpenAPI 文件自動生成",
            "body": "後端 API 一旦超過 5 個端點，沒有文件的開發協作就開始痛苦：前端不知道要傳什麼格式、測試人員要翻程式碼才知道有哪些欄位、新人要花大量時間猜 request body 結構。\n\nOpenAPI 規範（前身是 Swagger）定義了一套描述 REST API 的標準格式，springdoc-openapi 能自動從 Spring MVC 的 Controller 掃描產生 OpenAPI 文件，並提供互動式 UI 讓人直接從瀏覽器呼叫 API。\n\n- 自動掃描：不需要手寫文件，從 Controller 標註推導\n- Swagger UI：瀏覽器直接測試每個端點，看到 request / response 格式\n- 機器可讀格式：前端工具可從 `/v3/api-docs` 取得 JSON 格式規格，自動產生 API client"
          },
          {
            "heading": "OpenAPI 標註與 AI 提示詞",
            "group": "OpenAPI 文件自動生成",
            "body": "不加標註時 Swagger UI 只能從方法簽章推導基本資訊。用 `@Operation`、`@Parameter`、`@ApiResponse` 補充說明後，文件立即更完整，前端閱讀效率大幅提升。\n\n**CustomerController.java — 加入 OpenAPI 標註 (java)**\n```java\n@RestController\n@RequestMapping(\"/api/customers\")\n@Tag(name = \"客戶管理\", description = \"客戶的新增、查詢、修改與刪除\")\npublic class CustomerController {\n\n    @Operation(\n        summary = \"查詢所有客戶\",\n        description = \"回傳完整客戶清單，可加 keyword 參數進行模糊搜尋\"\n// ... 完整程式碼請參考課程 GitHub 專案 ...\n    }\n}\n```\n\n---\n\n**AI 提示詞練習**\n\n試著用以下提示詞讓 AI 助手幫你完善 API 文件標註：\n\n- 「請幫我在 CustomerController 的所有端點加上 @Operation 說明，並補充每個可能的 HTTP 狀態碼對應的 @ApiResponse 標註。」\n- 「如何讓 Swagger UI 只在 dev profile 啟用，在 prod profile 自動關閉？請修改 application.yml 與 OpenApiConfig。」"
          },
          {
            "heading": "統一錯誤回應設計",
            "group": "全域例外處理",
            "body": "**沒有全域例外處理的問題**\n\n沒有統一例外處理時，Spring Boot 預設的錯誤回應格式混雜了 Tomcat 訊息與 Java 堆疊資訊，前端無法依賴固定結構解析錯誤。更糟的是，不同端點可能回傳完全不同格式的錯誤，增加前端的防禦成本。\n\n`@RestControllerAdvice` 讓你在一個地方定義所有例外的處理方式：每種例外對應一個方法，統一回傳相同結構的 JSON，Controller 本身完全不需要 try-catch。\n\n- 例外處理集中在一個類別，不散落各個 Controller\n- 回傳格式統一，前端只需解析一種結構\n- Controller 保持乾淨，只做「請求分派」這一件事\n\n---\n\n**建立統一的 ErrorResponse 格式**\n\n先定義所有錯誤回應共用的資料結構。使用 Java Record 讓程式碼簡潔，Jackson 自動序列化為 JSON。\n\n**ErrorResponse.java (java)**\n```java\n/**\n * 統一的 API 錯誤回應格式\n * 所有例外處理方法都回傳此格式，讓前端只需解析一種結構\n */\npublic record ErrorResponse(\n    int status,          // HTTP 狀態碼\n    String error,        // 錯誤類型（如 \"Not Found\"）\n    String message,      // 人類可讀的錯誤說明\n    String path,         // 發生錯誤的 API 路徑\n    LocalDateTime timestamp  // 錯誤發生時間\n) {\n    /** 快速建立標準錯誤回應的工廠方法 */\n    public static ErrorResponse of(HttpStatus status, String message, String path) {\n        return new ErrorResponse(\n            status.value(),\n            status.getReasonPhrase(),\n            message,\n            path,\n            LocalDateTime.now()\n        );\n    }\n}\n```"
          },
          {
            "heading": "ProblemDetail 與錯誤回應對照",
            "group": "全域例外處理",
            "body": "**ProblemDetail：Spring Boot 3 內建標準格式**\n\nSpring Boot 3+ 採用 RFC 9457 的 `ProblemDetail` 作為標準錯誤格式，Spring Boot 4 延續支援並推薦使用。不需要自訂 `ErrorResponse`，可直接在 `GlobalExceptionHandler` 回傳 `ProblemDetail` 物件，格式已符合業界標準。\n\n- `ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, \"客戶不存在\")` → 直接建立標準格式物件\n- 可透過 `problemDetail.setProperty(\"extra\", value)` 加入自訂欄位\n- 在 `application.yml` 加上 `spring.mvc.problemdetails.enabled: true` 可讓 Spring 預設用此格式回傳驗證錯誤\n\n---\n\n**例外處理回應對照**\n\n以下是三種例外情境對應的實際 JSON 回應，前端可依 `status` 欄位決定顯示方式。\n\n**各類例外的回應格式 (json)**\n```json\n// GET /api/customers/999 → 客戶不存在\n{\n  \"status\": 404,\n  \"error\": \"Not Found\",\n  \"message\": \"客戶 不存在：id = 999\",\n  \"path\": \"/api/customers/999\",\n  \"timestamp\": \"2026-06-08T10:30:00\"\n}\n\n// POST /api/customers → 驗證失敗\n{\n  \"status\": 400,\n  \"error\": \"Bad Request\",\n  \"message\": \"輸入資料驗證失敗\",\n  \"errors\": [\"name：客戶名稱不可為空\", \"level：等級必須為 VIP、General 或 New\"],\n  \"path\": \"/api/customers\"\n}\n\n// 任何未預期錯誤\n{\n  \"status\": 500,\n  \"error\": \"Internal Server Error\",\n  \"message\": \"伺服器發生錯誤，請稍後再試\",\n  \"path\": \"/api/customers\"\n}\n```"
          },
          {
            "heading": "Log 核心觀念與最佳實踐",
            "group": "Log 日誌管理",
            "body": "**Spring Boot 預設 Log 機制**\n\nSpring Boot 預設使用 Logback 作為 Log 框架，並透過 SLF4J 提供統一的 API 介面。不需要任何設定就能使用，只要依賴 `spring-boot-starter`（幾乎所有 Starter 都已包含）就自動啟用。\n\n預設 Log 格式包含時間戳記、層級、執行緒、類別名稱與訊息。開發時輸出到 console，可另外設定輸出到檔案。\n\n- `ERROR` → 系統發生嚴重錯誤，需要立即處理\n- `WARN` → 可能有問題，但系統還能運作\n- `INFO` → 正常業務流程的關鍵節點（預設顯示層級）\n- `DEBUG` → 詳細的執行資訊，開發除錯使用\n- `TRACE` → 最詳細層級，通常只在框架內部使用\n\n---\n\n**Log 最佳實踐**\n\n- **INFO**：記錄業務關鍵節點（誰建立了什麼、誰觸發了什麼操作），足以在不看程式碼的情況下理解系統在做什麼\n- **WARN**：可以自動恢復或降級的異常情境（如 retry、fallback），需要關注但不需要立即處理\n- **ERROR**：需要人工介入的問題，搭配 `log.error(\"...\", ex)` 記錄完整 stack trace\n- **避免在 Log 記錄密碼、Token、信用卡號**：即使是 DEBUG 層級，log 檔可能被備份或轉發到第三方\n- **用 `{}` 佔位符而非字串拼接**：`log.debug(\"id={}\", id)` 在 DEBUG 層級關閉時不建立字串，效能更好"
          },
          {
            "heading": "@Slf4j 與結構化 Log 寫法",
            "group": "Log 日誌管理",
            "body": "Lombok 的 `@Slf4j` 自動注入 `log` 物件，省去手動宣告 Logger 的樣板程式碼。Log 訊息用 `{}` 佔位符取代字串拼接，避免不必要的字串建立開銷，也讓訊息格式更清楚。\n\n**CustomerService.java — @Slf4j 使用範例 (java)**\n```java\n@Slf4j   // Lombok：自動注入 private static final Logger log = ...\n@Service\npublic class CustomerService {\n\n    public Customer saveCustomer(Customer customer) {\n        log.info(\"新增客戶：name={}, level={}, email={}\", customer.getName(), customer.getLevel(), customer.getEmail());\n\n        Customer saved = customerRepository.save(customer);\n// ... 完整程式碼請參考課程 GitHub 專案 ...\n    // log.debug(\"查詢條件：\" + filterRequest);\n}\n```"
          },
          {
            "heading": "AOP 解決了什麼問題",
            "group": "AOP 面向切面",
            "body": "寫後端程式時，有一類邏輯天生就不屬於任何單一業務模組，卻又需要出現在幾乎每個地方——交易控制、效能計時、權限驗證、Log 記錄。如果把這些邏輯都寫在每個 Service 方法裡，程式碼會充滿重複，而且修改一次規則要動到幾十個地方。\n\nAOP（Aspect-Oriented Programming，面向切面程式設計）的核心想法是：把這類「橫切關注點（Cross-cutting Concern）」從業務邏輯中抽離出來，統一定義在一個地方，再宣告「在哪些方法的哪個時間點套用」。業務程式碼保持乾淨，橫切邏輯只寫一次。\n\n- **交易管理**：每個寫入操作都需要 begin / commit / rollback，不該散落各 Service\n- **Log 記錄**：記錄方法進入、結束、耗時，不該每個方法都手寫\n- **輸入驗證**：呼叫 Service 前驗證參數，不該在每個方法頭部重複 if-else\n- **權限檢查**：確認使用者有沒有權限呼叫這個方法，屬於安全層而非業務層"
          },
          {
            "heading": "AOP 核心詞彙與實現方式",
            "group": "AOP 面向切面",
            "body": "**Spring AOP 五大核心詞彙**\n\n理解 AOP 只需要掌握五個詞彙，其餘的都是這五個概念的組合。\n\n- **Join Point（連接點）**：程式執行中可以被攔截的時間點，Spring AOP 的 Join Point 就是「方法被呼叫的瞬間」\n- **Pointcut（切入點）**：用來篩選「哪些 Join Point 要套用 Advice」的規則，通常用 execution 表達式描述，例如「所有 Service 套件下的 public 方法」\n- **Advice（增強/通知）**：在 Join Point 要執行的動作，分成 Before（方法前）、After（方法後）、Around（包覆方法前後）、AfterReturning（成功回傳後）、AfterThrowing（拋出例外後）五種\n- **Aspect（切面）**：把 Pointcut 與 Advice 組合在一起的模組，就像「交易管理切面」= 所有 Service 方法（Pointcut）+ 自動 commit/rollback（Advice）\n- **Weaving（織入）**：把 Aspect 應用到目標物件的過程；Spring AOP 在執行期（Runtime）透過 Proxy 物件完成織入，不修改原始類別的 bytecode\n\n---\n\n**AOP 概念圖解**\n\n上半部展示橫切關注點如何切穿所有 Service，下半部展示 Spring 用 Proxy 在執行期攔截方法的原理。\n\n---\n\n**Spring AOP 的實現方式：Proxy**\n\nSpring AOP 不修改你的程式碼，而是在執行期替目標 Bean 建立一個「代理物件（Proxy）」。每次你呼叫 `@Autowired` 注入的 Bean 方法，實際上是呼叫 Proxy，Proxy 先執行 Advice（如開啟交易），再呼叫你的真實方法，最後再執行 Advice（如 commit）。\n\nSpring 根據情況選擇兩種 Proxy 實作：介面存在時用 JDK 動態 Proxy（速度快），無介面時用 CGLIB（繼承方式建立子類別 Proxy）。"
          },
          {
            "heading": "為什麼很少直接寫 AOP",
            "group": "AOP 面向切面",
            "body": "**很少直接寫 AOP 的原因**\n\nSpring 已經把最常用的橫切需求都封裝成標註（Annotation）了。你用 `@Transactional` 就等於在 Service 方法外包了一個 Around Advice，用 `@Valid` 就等於在 Controller 方法前放了一個 Before Advice，根本不需要自己寫 `@Aspect`。\n\n**直接撰寫 AOP 的時機**通常只有兩種：一是需求無法用現有標註表達（例如對所有方法計時、統一寫入稽核 Log）；二是為公司內部框架提供可重用的橫切能力。一般業務開發幾乎不需要接觸 `@Aspect`。\n\n- `@Transactional` → Spring 幫你寫好的交易 AOP，不需要自己包 Around Advice\n- `@Valid` / `@Validated` → Spring 幫你寫好的驗證 AOP，不需要自己在方法頭部驗參數\n- `@RestControllerAdvice` → Spring MVC 幫你寫好的例外攔截，不需要自己包 AfterThrowing\n- `@EnableJpaAuditing` → JPA 幫你寫好的 @PrePersist / @PreUpdate 攔截，不需要自己設 Listener\n- `spring-boot-starter-actuator` → Spring 幫你寫好的管理端點，不需要自己做 Health Check AOP\n\n---\n\n**Day 1 哪些功能背後用了 AOP**\n\n回顧第一天學過的所有功能，以下整理哪些地方在背後使用了 AOP 或相同設計概念，以及對應的 Spring 元件。"
          },
          {
            "heading": "安全防護重點",
            "group": "Spring Security 與 JWT 認證",
            "body": "在生產環境中，API 不能是完全公開的。本章將引入 Spring Security 與 JWT (JSON Web Token)，為我們的 REST API 建立安全防護底盤。\n\n我們將實作「無狀態 (Stateless)」認證：使用者透過 `/api/auth/login` 登入成功後取得 JWT，後續請求都必須在 Header 攜帶此 Token 進行驗證。此外，我們將簡單區分角色：「管理員 (ADMIN)」與「一般用戶 (USER)」，以實作更精細的權限控管。\n\n- Authentication 認證：確認「你是誰」（透過帳號密碼登入並簽發 JWT）\n- Authorization 授權：確認「你能做什麼」（例如管理員能刪除客戶資料，業務人員只能查詢與編輯）\n- 無狀態認證：伺服器不儲存 Session，每次請求均由 JWT 驗證身分與角色"
          },
          {
            "heading": "AI Agent 提示詞 — Security 與 JWT 實作",
            "group": "Spring Security 與 JWT 認證",
            "body": "**請 AI Agent 幫你安裝 Security 與 JWT 依賴**\n\n在 `pom.xml` 中引入 Spring Security Starter 與 JWT 套件。我們使用目前主流且穩定的 `io.jsonwebtoken` (jjwt) 套件來進行 Token 的簽署與解析。\n\n**pom.xml 依賴配置 (xml)**\n```xml\n<!-- Spring Security Starter -->\n<dependency>\n    <groupId>org.springframework.boot</groupId>\n    <artifactId>spring-boot-starter-security</artifactId>\n</dependency>\n\n<!-- JWT (jjwt) 相關依賴 -->\n<dependency>\n    <groupId>io.jsonwebtoken</groupId>\n    <artifactId>jjwt-api</artifactId>\n    <version>0.12.5</version>\n</dependency>\n<dependency>\n    <groupId>io.jsonwebtoken</groupId>\n    <artifactId>jjwt-impl</artifactId>\n    <version>0.12.5</version>\n    <scope>runtime</scope>\n</dependency>\n<dependency>\n    <groupId>io.jsonwebtoken</groupId>\n    <artifactId>jjwt-jackson</artifactId>\n    <version>0.12.5</version>\n    <scope>runtime</scope>\n</dependency>\n```\n\n---\n\n**AI Agent 提示詞 — 身分驗證與角色授權實作**\n\n將以下提示詞複製給 AI Agent，讓它幫你生成完整的安全驗證配置。此提示詞特別強調了角色定義。\n\n- 在 JWT 內寫入使用者角色（簡單區分管理員 ADMIN 與用戶 USER）\n- 限制客戶資料刪除 API 僅限管理員角色存取，客戶資料查詢 API 需登入身分即可\n- 保護 Swagger UI 與 OpenAPI 網頁與端點，需登入才能瀏覽\n\n**AI Agent 提示詞 (text)**\n```text\n請在現有專案中，使用 Spring Security 與 JWT 實作安全防護與登入驗證功能：\n1. 引入安全防護套件（Spring Security）與 JWT 依賴，限制除了登入相關的 API 之外，其餘所有的 API 都需要攜帶 JWT Token 進行驗證才能存取。\n2. 實作一個登入 API（例如 POST /api/auth/login），供使用者傳入帳號密碼進行身分驗證。登入成功後，請在 JWT Token 中寫入使用者的角色（簡單區分「管理員 ADMIN」與「一般用戶 USER」），並將 Token 回傳給前端。\n3. 實作角色權限控制：限制客戶資料刪除 API 必須具備「管理員 ADMIN」角色才能執行，而一般查詢與編輯功能則僅需「用戶 USER」或已登入身分即可。\n4. 保護我們的 API 文件（Swagger UI 網頁與相關端點），設定必須在登入驗證並攜帶 JWT Token 後才能正常瀏覽與測試。\n```"
          },
          {
            "heading": "Swagger 網頁驗證步驟（推薦）",
            "group": "Spring Security 與 JWT 認證",
            "body": "後端啟動後，我們可以透過 Swagger UI 網頁進行 API 測試，視覺化地驗證登入、JWT 簽發與角色基礎授權控制（RBAC）是否正常運作。\n\n<div style=\"margin-top: 16px;\"><ul style=\"list-style-type: decimal; margin-left: 20px; margin-bottom: 16px; line-height: 1.8;\"><li style=\"margin-bottom: 6px;\"><strong>開啟 Swagger 網頁</strong>：在瀏覽器中輸入 <a href=\"http://localhost:8080/swagger-ui/index.html\" target=\"_blank\" class=\"accent-link\">http://localhost:8080/swagger-ui/index.html</a>。</li><li style=\"margin-bottom: 6px;\"><strong>安全登入 (HTTP Basic)</strong>：由於設定了安全防護，瀏覽器會彈出登入對話框。請輸入管理員帳密（帳號：<code>admin</code>，密碼：<code>password</code>）完成登入。</li><li style=\"margin-bottom: 6px;\"><strong>取得 JWT Token</strong>：展開 <code>POST /api/auth/login</code>，點選 <strong>Try it out</strong>，傳入使用者資料（如：<code>{\"username\": \"user\", \"password\": \"password\"}</code>）執行並複製回傳的 token。</li><li style=\"margin-bottom: 6px;\"><strong>點擊 Authorize 帶入 Token</strong>：回到頁面最上方點選 <strong>Authorize</strong> 按鈕，在 <code>BearerAuth</code> 欄位填入剛剛複製的 JWT Token，點擊 Authorize 啟用。</li><li style=\"margin-bottom: 6px;\"><strong>驗證角色存取控制 (RBAC)</strong>：在授權為 <code>user</code> 狀態下呼叫刪除客戶 API，預期應收到 <strong>403 Forbidden</strong>；以同樣方式更換為 <code>admin</code> 的 Token 後呼叫則應成功回傳 <strong>204 No Content</strong>。</li></ul></div>"
          },
          {
            "heading": "CRM 角色與權限模型",
            "group": "CRM 安全設計",
            "body": "AI CRM 系統的角色設計反映真實企業的組織層級：\n\n**ROLE_SALES（業務人員）**\n- 可查看/編輯自己負責的客戶、商機與互動紀錄\n- 可使用 AI 助理進行客戶分析與行動建議\n- 不可查看其他業務人員的客戶資料\n\n**ROLE_MANAGER（業務主管）**\n- 可查看團隊內所有業務人員的客戶與商機\n- 可查看團隊的銷售報表與 AI 預測分析\n- 可指派任務給業務人員\n\n**ROLE_ADMIN（系統管理員）**\n- 可管理使用者帳號、角色與權限\n- 可管理 RAG 知識庫（上傳/刪除產品文件、銷售話術）\n- 可查看系統日誌與 AI 使用統計\n\n**權限控制實作要點**\n- API 層：用 `@PreAuthorize` 標註控制端點存取\n- 資料層：用 Specification 自動加入 `salesRepId` 過濾條件\n- AI 層：工具呼叫自動注入當前使用者的權限範圍，確保 AI 不會洩露跨業務的客戶資料"
          }
        ],
        "prompt": "請接續 Unit 3 的實作內容。現在我們要進入 Unit 4：Spring Security、JWT、OpenAPI 與企業級錯誤處理。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### AI Agent 提示詞 — 身分驗證與角色授權實作\n請在現有專案中，使用 Spring Security 與 JWT 實作安全防護與登入驗證功能：\n1. 引入安全防護套件（Spring Security）與 JWT 依賴，限制除了登入相關的 API 之外，其餘所有的 API 都需要攜帶 JWT Token 進行驗證才能存取。\n2. 實作一個登入 API（例如 POST /api/auth/login），供使用者傳入帳號密碼進行身分驗證。登入成功後，請在 JWT Token 中寫入使用者的角色（簡單區分「管理員 ADMIN」與「一般用戶 USER」），並將 Token 回傳給前端。\n3. 實作角色權限控制：限制客戶資料刪除 API 必須具備「管理員 ADMIN」角色才能執行，而一般查詢與編輯功能則僅需「用戶 USER」或已登入身分即可。\n4. 保護我們的 API 文件（Swagger UI 網頁與相關端點），設定必須在登入驗證並攜帶 JWT Token 後才能正常瀏覽與測試。\n",
        "promptMac": "請接續 Unit 3 的實作內容。現在我們要進入 Unit 4：Spring Security、JWT、OpenAPI 與企業級錯誤處理。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### AI Agent 提示詞 — 身分驗證與角色授權實作\n請在現有專案中，使用 Spring Security 與 JWT 實作安全防護與登入驗證功能：\n1. 引入安全防護套件（Spring Security）與 JWT 依賴，限制除了登入相關的 API 之外，其餘所有的 API 都需要攜帶 JWT Token 進行驗證才能存取。\n2. 實作一個登入 API（例如 POST /api/auth/login），供使用者傳入帳號密碼進行身分驗證。登入成功後，請在 JWT Token 中寫入使用者的角色（簡單區分「管理員 ADMIN」與「一般用戶 USER」），並將 Token 回傳給前端。\n3. 實作角色權限控制：限制客戶資料刪除 API 必須具備「管理員 ADMIN」角色才能執行，而一般查詢與編輯功能則僅需「用戶 USER」或已登入身分即可。\n4. 保護我們的 API 文件（Swagger UI 網頁與相關端點），設定必須在登入驗證並攜帶 JWT Token 後才能正常瀏覽與測試。\n",
        "prompts": [
          {
            "title": "高級 AI 協作提示詞大師範本（Windows 版）",
            "note": "可直接交給 Codex / Gemini 進行輔助開發",
            "text": "請接續 Unit 3 的實作內容。現在我們要進入 Unit 4：Spring Security、JWT、OpenAPI 與企業級錯誤處理。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### AI Agent 提示詞 — 身分驗證與角色授權實作\n請在現有專案中，使用 Spring Security 與 JWT 實作安全防護與登入驗證功能：\n1. 引入安全防護套件（Spring Security）與 JWT 依賴，限制除了登入相關的 API 之外，其餘所有的 API 都需要攜帶 JWT Token 進行驗證才能存取。\n2. 實作一個登入 API（例如 POST /api/auth/login），供使用者傳入帳號密碼進行身分驗證。登入成功後，請在 JWT Token 中寫入使用者的角色（簡單區分「管理員 ADMIN」與「一般用戶 USER」），並將 Token 回傳給前端。\n3. 實作角色權限控制：限制客戶資料刪除 API 必須具備「管理員 ADMIN」角色才能執行，而一般查詢與編輯功能則僅需「用戶 USER」或已登入身分即可。\n4. 保護我們的 API 文件（Swagger UI 網頁與相關端點），設定必須在登入驗證並攜帶 JWT Token 後才能正常瀏覽與測試。\n"
          }
        ],
        "tasks": [
          {
            "id": "u4-t1",
            "label": "在 pom.xml 加入 springdoc-openapi 依賴並啟動應用，確認可存取 /swagger-ui.html"
          },
          {
            "id": "u4-t2",
            "label": "在 CustomerController 加上 @Operation 與 @ApiResponse 標註"
          },
          {
            "id": "u4-t3",
            "label": "建立 OpenApiConfig 設定全域 API 資訊"
          },
          {
            "id": "u4-t4",
            "label": "建立 ErrorResponse record 作為統一錯誤格式"
          },
          {
            "id": "u4-t5",
            "label": "建立 GlobalExceptionHandler，處理 404 與驗證失敗例外"
          },
          {
            "id": "u4-t6",
            "label": "建立 ResourceNotFoundException，在 CustomerService 找不到客戶時拋出"
          },
          {
            "id": "u4-t7",
            "label": "在 CustomerService 加上 @Slf4j，在新增、刪除操作加上適當的 INFO / WARN Log"
          },
          {
            "id": "u4-t8",
            "label": "加入 spring-boot-starter-actuator，驗證可透過 PATCH /actuator/loggers 動態調整層級"
          },
          {
            "id": "u4-t9",
            "label": "閱讀 AOP 五大元素說明，能用自己的話描述 Aspect、Advice、Pointcut 的關係"
          },
          {
            "id": "u4-t10",
            "label": "對照第一天功能清單，說明至少 3 個用到 AOP 的地方與其對應的 Advice 類型"
          },
          {
            "id": "u4-t11",
            "label": "透過 AI 提示詞引入 Security 與 JWT 相關依賴"
          },
          {
            "id": "u4-t12",
            "label": "實作 JwtUtils 產生包含角色資訊的 Token，並完成認證過濾器與 SecurityConfig 配置"
          },
          {
            "id": "u4-t13",
            "label": "實作 /api/auth/login 登入 API，並為客戶 API 設定角色權限控制（ADMIN 限制刪除）"
          }
        ],
        "materials": [
          {
            "id": "mat4",
            "type": "MD",
            "name": "pgvector_環境安裝與向量檢索指令說明",
            "desc": "向量資料庫基礎概念、SQL 向量距離計算及 pgvector 索引優化指令。"
          },
          {
            "id": "mat5",
            "type": "MD",
            "name": "JWT_架構與認證流程設計",
            "desc": "Spring Security 整合 JWT 簽發、驗證與 Filter Chain 保護 API 的完整流程架構。"
          }
        ],
        "illustrations": [
          {
            "name": "u4-1.png",
            "kind": "hero",
            "alt": "JWT Security Boundary",
            "spec": "登入、角色、OpenAPI 與 ProblemDetail"
          },
          {
            "name": "u4-2.png",
            "kind": "diagram",
            "alt": "安全請求管線",
            "spec": "流程圖：Login -> Token -> Filter Chain -> Protected API"
          },
          {
            "name": "u4-3-term.png",
            "kind": "term",
            "alt": "Spring Security、JWT、OpenAPI 與企業級錯誤處理 專業術語解釋",
            "spec": "JWT / Security Filter / ProblemDetail"
          }
        ]
      }
    ]
  },
  "day2": {
    "id": "day2",
    "title": "Spring AI、企業級 RAG 與 React 全端整合",
    "date": "Day 3 ~ Day 4",
    "learningGoal": "整合 React 前端、Spring AI 串流對話、向量資料庫 pgvector 與結訓專案 Demo Day 驗收",
    "units": [
      {
        "id": "u5",
        "title": "React CRM 工作台與前後端整合",
        "subtitle": "建立 React 19 + Vite 前端 CRM UI，對接認證機制與客戶列表、詳情及商機看板。",
        "time": "13:00 ~ 17:00",
        "features": [
          "掌握 Node.js 環境、使用 Vite 建立 React 19 專案、JSX 語法元件結構，並學習如何以 Proxy 串接後端 API 及套用 uiuxpromax 優化前端視覺體驗。"
        ],
        "goals": [
          "理解 Node.js 與 NPM 相依性套件管理機制",
          "學會使用 Vite 初始化 React 19 專案的指令步驟",
          "掌握 JSX 語法特色與 Functional Component 元件結構",
          "理解為何需要開發代理 (Vite Proxy) 以免除 CORS 限制",
          "掌握 uiuxpromax 視覺優化要點（毛玻璃、漸層Header、微動畫與骨架屏）"
        ],
        "principle": "前後端整合的核心在於狀態的一致性與防禦性渲染。Axios Interceptor 可以自動為請求附加 JWT，並在 401 時引導重新登入；UI 上必須對 Loading、Error 及 Empty 進行完整處理，保障使用者體驗。",
        "concepts": [
          {
            "heading": "Node.js 與 React 專案建立",
            "group": "Node.js 與 React 專案建立",
            "body": "Node.js 是前端開發的執行環境，而 NPM (Node Package Manager) 是套件管理工具。在 React 19 的開發中，我們不再使用傳統手動下載 JS 檔的方式，而是使用 NPM 安裝相依套件。\n\n我們使用業界主流、極速的 Vite 作為建置工具，透過以下指令在命令列中建立專案：\n\n- 建立 Vite React 專案：`npx create-vite@latest frontend --template react`\n- 進入專案目錄：`cd frontend`\n- 安裝最新無資安漏洞的 React 19 依賴：`npm install`\n- 啟動本機開發伺服器 (Port 5173)：`npm run dev`"
          },
          {
            "heading": "JSX 語法與 Functional Component 元件結構",
            "group": "JSX 語法與元件結構",
            "body": "JSX 是 JavaScript 的語法擴充，允許我們在 JavaScript 中直接撰寫一種類似 HTML 的結構。React 19 推薦使用 Functional Component（函式元件）進行開發，相比舊版的 Class 元件更加簡潔。\n\nJSX 寫作規範與注意事項：\n\n1. 所有元件必須回傳「單一根節點」（若有多個元素，需用空標籤 `<></>` 包裹）。\n\n2. 由於 class 在 JS 中是保留字，JSX 中必須改寫為 `className`。\n\n3. HTML 事件綁定需改為 React 的小駝峰命名（例如 `onclick` 改為 `onClick`）。\n\n4. 變數與邏輯表達式可直接放在大括號 `{}` 中進行求值與渲染。\n\n**Counter.jsx — 基本 JSX 函式元件範例 (jsx)**\n```jsx\nimport React, { useState } from 'react';\n\n// 宣告一個 Functional Component 元件\nexport default function Counter({ initialCount = 0 }) {\n  // 使用 useState Hook 管理元件內部的狀態\n  const [count, setCount] = useState(initialCount);\n\n  return (\n    <div className=\"counter-container\">\n      <h3>當前計數器：{count}</h3>\n      {/* 點擊事件小駝峰命名，並使用大括號綁定 JavaScript 方法 */}\n      <button onClick={() => setCount(count + 1)}>\n        累加 +1\n      </button>\n    </div>\n  );\n}\n```"
          },
          {
            "heading": "開發端代理與後端 API 串接 (Vite Proxy)",
            "group": "Vite Proxy 前後端串接",
            "body": "在前後端分離的架構中，前端 Vite 伺服器運行在 `http://localhost:5173`，而 Spring Boot 後端運行在 `http://localhost:8080`。如果前端直接向後端發送非同步請求 (Fetch / EventSource)，會因為「同源政策 (Same-Origin Policy)」而被瀏覽器阻擋 (CORS 跨網域錯誤)。\n\n為了解決此問題，我們在 `vite.config.js` 中配置 `server.proxy` 代理轉發，將所有以 `/api` 開頭的請求，在開發環境中自動轉發至 `http://localhost:8080`。這樣前端程式碼只需填寫相對路徑即可，且完全免除了後端配置 CORS 的繁瑣設定！\n\n**vite.config.js — 配置 API 代理轉發 (javascript)**\n```javascript\nimport { defineConfig } from 'vite'\nimport react from '@vitejs/plugin-react'\n\nexport default defineConfig({\n  plugins: [react()],\n  server: {\n    port: 5173,\n    proxy: {\n      // 當前端請求 /api/ai/stream 時，Vite 自動代理為 http://localhost:8080/api/ai/stream\n      '/api': {\n        target: 'http://localhost:8080',\n        changeOrigin: true\n      }\n    }\n  }\n})\n```"
          },
          {
            "heading": "uiuxpromax 前端視覺優化指引",
            "group": "前端視覺優化指引",
            "body": "一個優秀的 Web 應用不僅要能跑，更要能 WOW 使用者。在智慧客服專案中，我們採用 uiuxpromax 的設計哲學，利用 Vanilla CSS 優化整體視覺，徹底告別單調的 MVP 樣式：\n\n- 🎨 毛玻璃效果 (Glassmorphism)：卡片使用 `backdrop-filter: blur(14px)` 搭配半透明邊框，營造精緻浮空感。\n- 🌈 漸層極光配色：Header 使用 linear-gradient(135deg, indigo, purple) 漸層配色，搭配狀態指示燈 (Pulse LED) 動態閃爍。\n- ⚡ 微懸停動畫 (Micro-interactions)：滑鼠懸停於客戶摘要、待辦任務卡片時，加入 `transform: translateY(-4px) scale(1.01)` 與 `transition` 讓卡片活起來。\n- ⏳ 骨架屏載入動畫 (Skeleton Screen)：當 AI 正在思考或呼叫 Tool 時，在對話框中顯示灰白色的骨架屏閃爍 (shimmer keyframe)，極大降地等待期間 of 無聊感。\n\n---\n\n📦 **uiuxpromax 的「安裝」與配置方式**\n\n`uiuxpromax` 並非傳統的 npm 第三方套件，因此不需要執行 `npm install`。它的「安裝與引入方式」是將以下精心調校的 Vanilla CSS 樣式直接整合進 React 專案的 `src/index.css`（或 `App.css`）中，讓元件直接透過 `className` 引用：\n\n```css\n/* uiuxpromax 核心樣式配置 */\n.glass-card {\n  background: rgba(255, 255, 255, 0.45);\n  backdrop-filter: blur(14px);\n  -webkit-backdrop-filter: blur(14px);\n  border: 1px solid rgba(255, 255, 255, 0.25);\n  box-shadow: 0 8px 32px 0 rgba(31, 38, 135, 0.07);\n}\n\n.gradient-header {\n  background: linear-gradient(135deg, #4f46e5, #9333ea);\n}\n\n.micro-interaction {\n  transition: transform 0.2s ease, box-shadow 0.2s ease;\n}\n.micro-interaction:hover {\n  transform: translateY(-4px) scale(1.01);\n  box-shadow: 0 10px 20px rgba(0, 0, 0, 0.1);\n}\n\n@keyframes shimmer {\n  0% { background-position: -200% 0; }\n  100% { background-position: 200% 0; }\n}\n.skeleton-shimmer {\n  background: linear-gradient(90deg, #f3f4f6 25%, #e5e7eb 50%, #f3f4f6 75%);\n  background-size: 200% 100%;\n  animation: shimmer 1.5s infinite linear;\n}\n```"
          },
          {
            "heading": "AI Agent 提示詞 — 建立 React 前端專案",
            "group": "AI 提示詞 — React 前端",
            "body": "理解 React 專案結構、Proxy 原理與視覺優化要點後，複製以下提示詞給 AI Agent，從零建立聊天室的前端專案骨架。\n\n**AI Agent 提示詞 (text)**\n```text\n請幫我建立 CRM 智慧工作台的 React 前端專案：\n1. 在專案根目錄用 Vite 建立 React 19 專案：npx create-vite@latest frontend --template react，並執行 npm install。\n2. 設定 vite.config.js 的 server.proxy，把所有 /api 開頭的請求代理到 http://localhost:8080，解決前後端分離的 CORS 問題。\n3. 建立 App 頁面骨架：包含漸層色 Header（linear-gradient 靛藍到紫色）、毛玻璃卡片容器（backdrop-filter: blur），以及聊天視窗的占位區塊。\n4. CSS 請加上骨架屏（skeleton shimmer）載入動畫，之後聊天室等待 AI 回覆時會用到。\n5. 元件需有中文註解。\n完成後請告訴我如何啟動開發伺服器，以及如何確認 proxy 代理有生效。\n```"
          },
          {
            "heading": "CRM 工作台 UI 設計思維",
            "group": "CRM 前端架構",
            "body": "CRM 前端工作台的核心畫面設計，圍繞業務人員的日常工作流：\n\n**Dashboard（儀表板）**\n展示關鍵指標：本月商機總額、待跟進客戶數、即將到期合約、AI 建議的優先行動。使用卡片式佈局，數據來自後端 API 的即時聚合。\n\n**Customer List（客戶列表）**\n支援動態篩選（產業、合約狀態、最後互動時間）與搜尋。列表使用虛擬滾動處理大量資料，點擊進入客戶詳細頁面。\n\n**Opportunity Board（商機看板）**\n看板式佈局（類似 Trello），每個欄位代表商機階段（Prospecting → Qualification → Proposal → Closing）。可拖拽商機卡片變更階段。\n\n**AI Chat（AI 助理）**\n右側浮動面板或獨立頁面，支援 SSE 串流對話、Markdown 渲染、客戶摘要卡片與行動建議按鈕。\n\n**元件設計原則**\n- 每個頁面是一個 Route，共用 Layout 元件（側欄 + 頂部導覽）\n- 資料請求統一用 Axios + Vite Proxy\n- CSS 使用 HSL 色彩系統，支援深色/淺色主題切換"
          }
        ],
        "prompt": "請接續 Unit 4 的實作內容。現在我們要進入 Unit 5：React CRM 工作台與前後端整合。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### AI Agent 提示詞 — 建立 React 前端專案\n請幫我建立 CRM 智慧工作台的 React 前端專案：\n1. 在專案根目錄用 Vite 建立 React 19 專案：npx create-vite@latest frontend --template react，並執行 npm install。\n2. 設定 vite.config.js 的 server.proxy，把所有 /api 開頭的請求代理到 http://localhost:8080，解決前後端分離的 CORS 問題。\n3. 建立 App 頁面骨架：包含漸層色 Header（linear-gradient 靛藍到紫色）、毛玻璃卡片容器（backdrop-filter: blur），以及聊天視窗的占位區塊。\n4. CSS 請加上骨架屏（skeleton shimmer）載入動畫，之後聊天室等待 AI 回覆時會用到。\n5. 元件需有中文註解。\n完成後請告訴我如何啟動開發伺服器，以及如何確認 proxy 代理有生效。\n",
        "promptMac": "請接續 Unit 4 的實作內容。現在我們要進入 Unit 5：React CRM 工作台與前後端整合。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### AI Agent 提示詞 — 建立 React 前端專案\n請幫我建立 CRM 智慧工作台的 React 前端專案：\n1. 在專案根目錄用 Vite 建立 React 19 專案：npx create-vite@latest frontend --template react，並執行 npm install。\n2. 設定 vite.config.js 的 server.proxy，把所有 /api 開頭的請求代理到 http://localhost:8080，解決前後端分離的 CORS 問題。\n3. 建立 App 頁面骨架：包含漸層色 Header（linear-gradient 靛藍到紫色）、毛玻璃卡片容器（backdrop-filter: blur），以及聊天視窗的占位區塊。\n4. CSS 請加上骨架屏（skeleton shimmer）載入動畫，之後聊天室等待 AI 回覆時會用到。\n5. 元件需有中文註解。\n完成後請告訴我如何啟動開發伺服器，以及如何確認 proxy 代理有生效。\n",
        "prompts": [
          {
            "title": "高級 AI 協作提示詞大師範本（Windows 版）",
            "note": "可直接交給 Codex / Gemini 進行輔助開發",
            "text": "請接續 Unit 4 的實作內容。現在我們要進入 Unit 5：React CRM 工作台與前後端整合。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### AI Agent 提示詞 — 建立 React 前端專案\n請幫我建立 CRM 智慧工作台的 React 前端專案：\n1. 在專案根目錄用 Vite 建立 React 19 專案：npx create-vite@latest frontend --template react，並執行 npm install。\n2. 設定 vite.config.js 的 server.proxy，把所有 /api 開頭的請求代理到 http://localhost:8080，解決前後端分離的 CORS 問題。\n3. 建立 App 頁面骨架：包含漸層色 Header（linear-gradient 靛藍到紫色）、毛玻璃卡片容器（backdrop-filter: blur），以及聊天視窗的占位區塊。\n4. CSS 請加上骨架屏（skeleton shimmer）載入動畫，之後聊天室等待 AI 回覆時會用到。\n5. 元件需有中文註解。\n完成後請告訴我如何啟動開發伺服器，以及如何確認 proxy 代理有生效。\n"
          },
          {
            "title": "API Client 與 JWT 認證管理",
            "note": "建立可重用的 API 呼叫層，自動處理 JWT token 的存儲、帶入與過期重導",
            "text": "請在 React 前端專案中建立統一的 API Client 模組與認證狀態管理：\n\n### 步驟一：安裝 Axios\nnpm install axios\n\n### 步驟二：建立 API Client（src/api/client.js）\n請建立一個 Axios instance，功能如下：\n1. baseURL 設為 '/api'（透過 Vite proxy 代理到後端）\n2. 請求攔截器（request interceptor）：每次請求自動從 localStorage 讀取 JWT token，加到 Authorization: Bearer <token> 標頭\n3. 回應攔截器（response interceptor）：若回傳 401，自動清除 localStorage 中的 token 並導向登入頁\n4. 匯出常用的 get、post、put、delete 方法\n\n### 步驟三：建立 Auth Context（src/context/AuthContext.jsx）\n用 React Context + useReducer 管理認證狀態：\n- state：{ user, token, isAuthenticated }\n- actions：LOGIN（存 token 到 localStorage 並更新 state）、LOGOUT（清除 token 並重導登入頁）\n- 提供 useAuth() 自訂 hook 供各元件取用\n- 應用載入時從 localStorage 還原 token，若 token 存在則呼叫 /api/auth/me 驗證有效性\n\n### 步驟四：建立 API Service 模組\n在 src/api/ 目錄下建立對應的 service 檔案：\n- authService.js：login(username, password)、getCurrentUser()\n- customerService.js：getCustomers(params)、getCustomerById(id)、searchCustomers(filters)\n- opportunityService.js：getOpportunities(filters)、updateStage(id, stage)\n- interactionService.js：getInteractionsByCustomer(customerId)、createInteraction(customerId, data)\n\n每個 service 使用步驟二的 API client，回傳 Promise。\n元件需有中文註解。"
          },
          {
            "title": "React CRM 頁面 — 登入、Dashboard、客戶列表與商機看板",
            "note": "建立 CRM 系統的核心前端頁面，串接後端 API 顯示真實資料",
            "text": "請在 React 專案中建立 CRM 系統的核心頁面，使用 React Router 管理路由：\n\n### 步驟一：安裝依賴與設定路由\nnpm install react-router-dom\n\n在 App.jsx 設定路由結構：\n- /login → LoginPage（未登入時的預設頁面）\n- / → DashboardPage（需登入，受 PrivateRoute 保護）\n- /customers → CustomerListPage\n- /customers/:id → CustomerDetailPage\n- /opportunities → OpportunityBoardPage\n\n建立 PrivateRoute 元件：未登入時自動重導到 /login。\n\n### 步驟二：登入頁（LoginPage.jsx）\n- 帳號與密碼輸入欄位，使用 controlled component\n- 送出後呼叫 POST /api/auth/login，成功後將 JWT 存入 localStorage 並導向 Dashboard\n- 登入失敗顯示紅色錯誤訊息\n- 提供測試帳號提示（user / password 或 admin / password）\n- 毛玻璃卡片樣式，置中於畫面\n\n### 步驟三：CRM Dashboard（DashboardPage.jsx）\n- 頂部顯示歡迎訊息與登出按鈕\n- 四張統計卡片：客戶總數、本月新增客戶、進行中商機數、商機總金額\n- 統計資料從 API 取得（GET /api/customers 計算總數，GET /api/opportunities 計算金額與數量）\n- 載入中顯示骨架屏動畫\n- 卡片有 hover 微動畫效果（translateY + shadow）\n\n### 步驟四：客戶列表頁（CustomerListPage.jsx）\n- 顯示客戶資料表格：公司名稱、產業別、分級（VIP 用金色標籤）、負責業務\n- 頂部搜尋列：關鍵字搜尋 + 產業篩選下拉 + 分級篩選下拉\n- 篩選條件變動時即時呼叫 GET /api/customers/search 更新列表\n- 點擊客戶名稱導向客戶詳情頁\n- 空資料顯示「尚無符合條件的客戶」提示\n- 載入中、載入失敗都有明確的 UI 狀態\n\n### 步驟五：客戶詳情頁（CustomerDetailPage.jsx）\n- 頂部客戶基本資訊卡片（名稱、產業、分級、負責業務、Email、電話）\n- Tab 切換區塊：\n  - 「聯絡人」Tab：列出該客戶所有聯絡人（姓名、職稱、Email）\n  - 「互動紀錄」Tab：時間軸形式顯示互動紀錄（類型圖示 + 日期 + 摘要），最新在上\n  - 「商機」Tab：列出該客戶所有商機（名稱、金額、階段徽章、預計成交日）\n  - 「AI 建議」Tab：留空占位，Unit 6 串接 AI 聊天室\n- 使用 useParams() 取得 customerId，載入時呼叫對應 API\n\n### 步驟六：商機看板（OpportunityBoardPage.jsx）\n- 橫向 Kanban 看板，六個欄位對應六個商機階段（PROSPECTING → CLOSED_LOST）\n- 每筆商機顯示為卡片：商機名稱、客戶名稱、金額、預計成交日\n- 每個階段欄位頂部顯示該階段的商機數量與金額小計\n- 提供手動拖拉或「推進階段」按鈕，呼叫 PUT /api/opportunities/{id}/stage 更新\n\n### 共同要求\n- 所有 API 失敗顯示 toast 或紅色錯誤提示\n- JWT 過期（401）自動跳回登入頁\n- 元件需有中文函式級別註解"
          }
        ],
        "tasks": [
          {
            "id": "u5-t1",
            "label": "安裝 Node.js 並執行 npm 驗證"
          },
          {
            "id": "u5-t2",
            "label": "使用 Vite 建立 React 19 專案並以 npm install 安裝"
          },
          {
            "id": "u5-t3",
            "label": "配置 vite.config.js 的 server.proxy 代理"
          },
          {
            "id": "u5-t4",
            "label": "套用 CSS 動畫實作骨架屏與毛玻璃視覺效果"
          }
        ],
        "materials": [
          {
            "id": "mat6",
            "type": "MD",
            "name": "React_與_Axios_前後端整合說明",
            "desc": "React 19 呼叫後端 API、JWT 自動攜帶與 Axios 攔截器 (Interceptors) 設計說明。"
          }
        ],
        "illustrations": [
          {
            "name": "u5-1.png",
            "kind": "hero",
            "alt": "React CRM Workspace",
            "spec": "Dashboard、列表、看板與 API Client"
          },
          {
            "name": "u5-2.png",
            "kind": "diagram",
            "alt": "前端整合架構",
            "spec": "流程圖：Login UI -> API Client -> Dashboard -> Kanban"
          },
          {
            "name": "u5-3-term.png",
            "kind": "term",
            "alt": "React CRM 工作台與前後端整合 專業術語解釋",
            "spec": "Axios Interceptor / Skeleton State / Role-based UI"
          }
        ]
      },
      {
        "id": "u6",
        "title": "Spring AI ChatClient、SSE 與 tool calling",
        "subtitle": "使用 Spring AI 建立對話助理，以 SSE 將 Token 串流傳遞給前端，並實作 Tool Calling 讓 AI 獲取 CRM 真實數據。",
        "time": "09:00 ~ 12:00",
        "features": [
          "建立 Spring AI 對話入口，理解串流輸出、對話記憶與多 session 管理。",
          "讓模型透過工具呼叫查詢即時資料或呼叫外部 API，而不是只依賴語料記憶作答。",
          "使用 React 串接後端 Server-Sent Events (SSE) 串流 API，利用原生 EventSource 與狀態管理實現即時打字機對話效果。"
        ],
        "goals": [
          "掌握 ChatClient 在 Spring AI 的使用位置",
          "理解 SSE 串流輸出的呈現方式",
          "知道如何以 sessionId 隔離不同使用者對話",
          "理解 Tool Calling 與 Function Calling 的實際用途",
          "分辨 `@Tool` 與 `ToolCallback` 的使用場景",
          "掌握 AI 查詢資料庫的安全責任邊界",
          "理解 SSE 與 WebSockets 的區別與選型",
          "使用 React 的 EventSource 實作後端 API 串接",
          "利用 React 狀態 (State) 實作流式打字渲染效果",
          "掌握流式對話中對話 Session 的前端管理與清除"
        ],
        "principle": "可信任 AI 的界線是「數字由工具算，文字由模型寫」。我們透過 System Prompt 設定邊界，以 Tool Calling 將 CRM 動態領域資料提供給 LLM，並使用 SSE 串流將 token 即時渲染至聊天介面。",
        "concepts": [
          {
            "heading": "AI CRM 助理的商業價值",
            "group": "AI CRM 助理定位",
            "body": "AI 助理不是「聊天機器人」，而是業務人員的智慧工作夥伴。在 CRM 場景中，AI 能創造三層價值：\n\n**第一層：資訊查詢（Tool Calling）**\n業務問「APIM 最近的合約狀況？」，AI 呼叫 CRM API 查詢客戶資料、商機與互動紀錄，整理成結構化摘要。省去業務人員在多個頁面間切換查找的時間。\n\n**第二層：知識檢索（RAG）**\n業務問「APIM 適合推薦哪個方案？」，AI 從向量化的產品文件與銷售話術中檢索相關內容，結合客戶的產業特性與需求給出推薦。\n\n**第三層：洞察建議（結合 Tool Calling + RAG + 歷史記憶）**\n業務問「GlobalMart 有流失風險嗎？該怎麼處理？」，AI 綜合分析：\n- 合約即將到期（CRM 資料）\n- 最近互動提及「預算凍結」與「競品比較」（歷史互動向量搜尋）\n- 過去類似情境的成功挽留策略（知識庫 RAG）\n\n最終產生具體的行動建議：「建議在 7 月 15 日前安排高層會議，提出客製化續約方案，重點強調...」\n\n這就是 AI CRM 的核心價值：**讓每一位業務人員都擁有資深顧問級的分析能力**。"
          },
          {
            "heading": "ChatClient 核心概念",
            "group": "ChatClient 基礎與對話記憶",
            "body": "**ChatClient 在架構中的位置**\n\n這一章開始，專案從純後端工程擴展到 AI 對話應用。`ChatClient` 是 Spring AI 2.0 中相對高階、可組合的入口，負責把使用者輸入、Advisor、Tools 與模型回應串成一條可讀的流程。\n\n教學上可以把它想成客服總機。總機本身不處理所有問題，但它知道該把輸入送到哪裡、要不要帶記憶、要不要叫工具。\n\n---\n\n**串流輸出為什麼重要**\n\n對使用者來說，串流輸出最大的價值不是技術炫耀，而是等待感受不同。模型可以一邊生成一邊顯示，前端不需要等整段文字全部完成才開始呈現。\n\n在這個課程範例中，前端使用 SSE 讀取後端串流結果，這也為後面的 Demo 頁面奠定互動感。\n\n---\n\n**對話記憶設計**\n\n- 不同使用者應對應不同 `sessionId`\n- 記憶不應全站共用，否則會互相污染上下文\n- 清除對話時應一併重建 session 識別值"
          },
          {
            "heading": "ChatClient 與 Advisor 深入解析",
            "group": "ChatClient 基礎與對話記憶",
            "body": "本節為您整理 ChatClient 與 Advisor 的關鍵配置與內建元件說明，幫助您深入理解架構：\n\n<div style=\"margin-top: 16px;\"><strong style=\"color: var(--primary-color); display: block; margin-bottom: 8px;\">1. ChatClient.Builder 常用預設設定：</strong><ul style=\"list-style-type: disc; margin-left: 20px; margin-bottom: 16px; line-height: 1.8;\"><li style=\"margin-bottom: 6px;\"><code>defaultSystem(String)</code>：設定預設的系統提示詞，定義 AI 角色與回覆風格（如親切的智慧助手）。</li><li style=\"margin-bottom: 6px;\"><code>defaultUser(String)</code>：設定預設的使用者輸入提示詞。</li><li style=\"margin-bottom: 6px;\"><code>defaultOptions(ChatOptions)</code>：設定預設的對話參數，例如溫度值（temperature）、使用的模型等。</li><li style=\"margin-bottom: 6px;\"><code>defaultAdvisors(Advisor...)</code>：設定預設的攔截增強器，例如記憶元件（MessageChatMemoryAdvisor）或 RAG 元件（QuestionAnswerAdvisor），使所有對話自動啟用該功能。</li><li style=\"margin-bottom: 6px;\"><code>defaultFunctions(String...)</code>：設定預設啟用的 Tool/Function Calling 工具 Bean 名稱。</li><li style=\"margin-bottom: 6px;\"><code>defaultTools(Object...)</code> / <code>defaultToolCallbacks(ToolCallback...)</code>：設定預設啟用的工具物件或回呼。</li></ul></div>\n\n<div style=\"margin-top: 16px;\"><strong style=\"color: var(--primary-color); display: block; margin-bottom: 8px;\">2. Spring AI 最新的內建 Advisor：</strong><ul style=\"list-style-type: disc; margin-left: 20px; margin-bottom: 16px; line-height: 1.8;\"><li style=\"margin-bottom: 6px;\"><code>MessageChatMemoryAdvisor</code>：最常用的對話記憶 Advisor，負責自動讀取指定 sessionId 的對話歷史並注入上下文，且在回答後自動將新問答存回記憶庫。</li><li style=\"margin-bottom: 6px;\"><code>PromptChatMemoryAdvisor</code>：文字範本式對話記憶 Advisor，將對話歷史格式化為一段文字並透過 Prompt 變數填入，適用於不支援多輪 Message 格式的特殊模型。</li><li style=\"margin-bottom: 6px;\"><code>QuestionAnswerAdvisor</code>：RAG 檢索增強生成 Advisor，自動將使用者提問向量化並至 VectorStore 檢索相關知識片段，作為上下文注入 Prompt 以降低幻覺。</li><li style=\"margin-bottom: 6px;\"><code>SimpleLoggerAdvisor</code>：日誌記錄 Advisor，在調試時可自動將完整 Prompt、參數配置與模型回應印出至 Log。</li><li style=\"margin-bottom: 6px;\"><code>VectorStoreChatMemoryAdvisor</code>：基於向量資料庫的持久化對話記憶 Advisor，適合極大規模或長期對話歷史的語意檢索與儲存。</li><li style=\"margin-bottom: 6px;\"><code>SafeGuardAdvisor</code>：內容安全防護 Advisor，用於在發送給 LLM 前或回應給用戶前，攔截與過濾敏感詞或不當發言。</li></ul></div>"
          },
          {
            "heading": "Groq API 端點設定（application.yml）",
            "group": "ChatClient 基礎與對話記憶",
            "body": "Groq 提供與 OpenAI 相容的 API 介面，因此可以使用 Spring AI 的 OpenAI Starter，只需把 Chat 端點的 base-url 覆寫為 Groq 即可。\n\n- Chat：前往 console.groq.com 建立免費 API Key，設定 `$env:GROQ_API_KEY=\"gsk_xxx...\"`\n- Embedding：前往 voyageai.com 建立 API Key（每月 50M tokens 免費），設定 `$env:VOYAGE_API_KEY=\"pa-xxx...\"`\n- Voyage AI 提供 OpenAI 相容介面，直接設定在 openai.embedding 區塊即可，不需要額外的 Starter 依賴\n\n**application.yml — Spring AI 模型設定 (yaml)**\n```yaml\nspring:\n  ai:\n    # OpenAI Starter 同時處理 Chat（Groq）與 Embedding（Voyage AI）\n    openai:\n      chat:\n        base-url: https://api.groq.com/openai/v1   # 覆寫為 Groq 端點\n        api-key: ${GROQ_API_KEY:your_groq_api_key}\n        options:\n// ... 完整程式碼請參考課程 GitHub 專案 ...\n        options:\n          model: voyage-3-lite  # 每月 50M tokens 免費，512 維度\n```"
          },
          {
            "heading": "AI Agent 提示詞 — 建立 ChatClient 對話入口",
            "group": "ChatClient 基礎與對話記憶",
            "body": "理解上述原理後，複製以下提示詞給 AI Agent，引導它在 Day 1 完成的電商後端上，加入本章的 AI 對話與串流功能。\n\n**AI Agent 提示詞 (text)**\n```text\n請在現有的 Spring Boot CRM 專案中加入 AI 對話功能\n\n1. 請在現有的 Spring Boot CRM 專案中加入 AI 對話功能\n2. 設定 Groq 的 base-url，模型用 openai/gpt-oss-120b，API Key 為 \"xxxxxxx\"。\n3. 系統提示詞：「你是一個親切的 CRM 智慧助手」。\n4. 使用 SSE串流回覆的API，並掛上記憶，以 sessionId 參數隔離不同使用者的對話記憶。\n```"
          },
          {
            "heading": "工具呼叫核心概念",
            "group": "Tool Calling 工具呼叫",
            "body": "**工具呼叫的真正價值**\n\n工具呼叫的本質，不是讓模型變強，而是讓模型不必假裝自己知道即時資料。它可以把需要查詢、計算或呼叫外部服務的工作交給可控工具處理。\n\n對企業系統來說，這一點非常重要，因為真正的業務資料往往存在資料庫、內部 API 或受權限控制的服務中。\n\n---\n\n**@Tool 與 ToolCallback 的使用邏輯**\n\n- `@Tool` 適合你自己在專案內部封裝的功能\n- `ToolCallback` 適合掛接外部或唯讀工具\n- 無論是哪一種，都應該透過既有 Service 邏輯取得資料\n- 不要讓模型直接碰資料庫連線或繞過商業規則"
          },
          {
            "heading": "@Tool vs ToolCallback 選型原則",
            "group": "Tool Calling 工具呼叫",
            "body": "工具呼叫有兩種宣告方式，選擇依據是你是否有原始碼的修改權限。\n\n- `@Tool` — 自己開發的業務方法，直接在方法上標註，最簡單\n- `MethodToolCallback` — 第三方 JAR（無法加註解），用反射包裝現有方法\n- `FunctionToolCallback` — 需要客製化邏輯的外部工具，用 Lambda + DTO 封裝\n- 無論哪種方式，都應透過 Service 層取得資料，不讓模型直接碰資料庫連線"
          },
          {
            "heading": "AI Agent 提示詞 — 加入工具呼叫能力",
            "group": "Tool Calling 工具呼叫",
            "body": "理解工具呼叫原理與兩種宣告方式後，複製以下提示詞給 AI Agent，讓它把 Day 1 寫好的 Service 包裝成 AI 可呼叫的工具，AI 就能查到資料庫的即時資料。\n\n**AI Agent 提示詞 (text)**\n```text\n請為 CRM AI 助手加入工具呼叫（Tool Calling）能力，讓 AI 能查詢資料庫的即時資料：\n1. 建立 CustomerTools 類別，用 @Tool 註解包裝既有的 CustomerService：\n   - getCustomers(query)：查詢客戶列表，支援關鍵字搜尋，query 為空時回傳全部客戶\n   - getCustomerDetail(id)：依客戶 ID 查詢詳細資訊（等級、產業、聯絡方式）\n2. 建立 OpportunityTools 類別，用 @Tool 包裝既有的 OpportunityService：\n   - getOpportunities(customerId)：查詢指定客戶的所有銷售機會\n   - getDealValue(opportunityId)：查詢特定銷售機會的金額與成交機率\n3. 每個 @Tool 的 description 請用中文清楚描述「什麼情況下該呼叫這個工具」，這段文字是寫給大模型看的。\n4. 在 ChatController 的 ChatClient 呼叫鏈中，用 .tools() 掛上這兩個工具類別。\n完成後請示範一句測試提問（例如「台積電目前的客戶等級是什麼？有哪些進行中的銷售機會？」），並說明如何從後端日誌確認工具真的被呼叫。\n```"
          },
          {
            "heading": "工具呼叫的架構定位",
            "group": "Tool Calling 工具呼叫",
            "body": "工具呼叫讓 AI 成為業務邏輯的「呼叫者」，而不是直接存取資料。不論工具背後是資料庫、內部 Service，還是第三方 API，模型看到的都是統一的工具介面。\n\n這種設計讓後端的資料來源可以隨時替換或擴充，而不影響模型的呼叫方式。"
          },
          {
            "heading": "為什麼選擇 SSE (Server-Sent Events)",
            "group": "SSE 前端串流整合",
            "body": "在 AI 聊天應用中，模型的回應是單向且持續產生的，後端需要持續把資料推給前端，而前端不需要在過程中頻繁向後端傳送資料。\n\n相較於 WebSockets 的雙向複雜協定，SSE 是基於標準 HTTP 的單向推播協定，開發簡單、開銷小，且天生支援斷線重連。Spring AI 的 `.stream()` 預設就是輸出標準的 `text/event-stream` 格式，與 SSE 完美搭配。\n\n- WebSockets：雙向通道，適用於多人遊戲、協作工具，但協定較為繁重。\n- SSE (Server-Sent Events)：單向推送，基於 HTTP，最適合大模型流式生成 (Streaming)。\n- React 連線方式：使用瀏覽器內建的 `EventSource` 物件即可建立連線，不需要引入第三方 WebSocket 庫。"
          },
          {
            "heading": "對話中客戶摘要、商機、建議行動的卡片元件呈現方式",
            "group": "SSE 前端串流整合",
            "body": "在 CRM 智慧工作台中，AI 回應不應只是死板的 Markdown 文字，更應該在適當時機，動態將對應的「客戶摘要卡片、商機卡片、建議行動卡片」嵌入到對話流中，提昇 UI/UX 質感。\n\n前端 ChatRoom.jsx 採用「自動偵測與條件渲染」機制來實作此功能：\n\n1. 串流偵測 (detectAndAttachCards)：當前端 EventSource 接收到後端 AI 推播的字元片段時，會將目前的完整對話內容 (fullResponse) 傳入進行關鍵字匹配。當匹配到客戶名稱 (如台積電、聯發科)、或涉及商機關鍵字且提及特定客戶時，自動在訊息物件中附加對應的 `cards` 資料。\n\n2. 條件渲染 (renderCards)：在 React 渲染訊息列表時，如果訊息物件含有 `cards` 屬性，則呼叫 `renderCards` 方法，根據 `cards.cardType` 分別渲染出 <CustomerSummaryCard />、<OpportunityCard />、或 <ActionCard />。\n\n**ChatRoom.jsx — 核心動態識別與元件渲染邏輯 (javascript)**\n```javascript\n/**\n * 偵測 AI 回覆內容並自動對應卡片數據\n */\nconst detectAndAttachCards = (text) => {\n  // 1. 客戶摘要卡片或建議行動卡片偵測\n  const matchedCustomers = knownCustomers.filter(c => text.includes(c.name));\n  if (matchedCustomers.length > 0) {\n    if (text.includes(\"建議\") || text.includes(\"下一步\")) {\n// ... 完整程式碼請參考課程 GitHub 專案 ...\n  }\n};\n```"
          },
          {
            "heading": "AI Agent 提示詞 — 前端串流與安全認證",
            "group": "SSE 前端串流整合",
            "body": "理解 SSE 原理、EventSource 串接方式與安全限制後，複製以下提示詞給 AI Agent，引導它為你的 React 前端聊天室實作 SSE 串流連線，並升級後端過濾器以支援安全認證。\n\n**AI Agent 提示詞 (text)**\n```text\n請在現有專案中完成以下前端與後端的串流對話安全升級：\n1. 由於原生的 EventSource 無法在 Header 中自訂 Token 進行驗證，請修改後端 `JwtAuthenticationFilter.java` 的 `parseJwt` 方法，使其除了支援從 `Authorization` 標頭讀取 Token 外，也支援從 URL 的 Query 參數（例如 `token`）中取得並驗證 Token。\n2. 在前端 `ChatRoom.jsx` 中，連接後端的 `/api/ai/stream` 串流對話介面。連線時，請從 `localStorage` 中讀取先前儲存的 JWT Token，並以網址參數形式帶入（例如 `/api/ai/stream?message=xxx&token=yyy`），以安全地建立 EventSource 串流對話連線。\n```"
          },
          {
            "heading": "系統運行展示：AI 基礎對話畫面",
            "group": "系統運行展示",
            "body": "智慧客服機器人的首頁與打招呼畫面。當使用者開啟聊天室時，機器人會主動打招呼並列出支援的功能與快捷指令，提供順暢的客服引導體驗。"
          },
          {
            "heading": "系統運行展示：客戶資訊查詢卡片",
            "group": "系統運行展示",
            "body": "當使用者詢問「有哪些客戶」時，後端 AI 模型會自動呼叫 CustomerTools 客戶查詢工具，從 JPA 資料庫中取得最新的客戶資料與等級資訊。前端收到資料後，會自動將文字清單轉換為精美的客戶卡片網格。"
          },
          {
            "heading": "系統運行展示：商機與銷售進度查詢",
            "group": "系統運行展示",
            "body": "使用者詢問特定客戶的銷售進度時（例如「台積電有哪些進行中的商機？」），AI 精確識別客戶名稱並呼叫 OpportunityTools 商機查詢工具，回傳對應的銷售機會。前端會將其渲染出包含商機名稱、金額、成交機率與銷售階段的卡片。"
          },
          {
            "heading": "系統運行展示：長期記憶語意客戶推薦",
            "group": "系統運行展示",
            "body": "當使用者點擊「長期記憶語意推薦」時，後端啟動雙路 RAG 檢索，同時在 pgvector 中查找此使用者在過去幾天內聊過的客戶偏好與銷售重點（例如曾提及專注於半導體產業、需要高階 VIP 客戶、對特定合作案感興趣等）。AI 結合記憶推薦 CRM 中的特定客戶或機會，前端渲染出極具說服力的語意推薦卡片與推薦理由。"
          }
        ],
        "prompt": "請接續 Unit 5 的實作內容。現在我們要進入 Unit 6：Spring AI ChatClient、SSE 與 tool calling。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### AI Agent 提示詞 — 建立 ChatClient 對話入口\n請在現有的 Spring Boot CRM 專案中加入 AI 對話功能\n\n1. 請在現有的 Spring Boot CRM 專案中加入 AI 對話功能\n2. 設定 Groq 的 base-url，模型用 openai/gpt-oss-120b，API Key 為 \"xxxxxxx\"。\n3. 系統提示詞：「你是一個親切的 CRM 智慧助手」。\n4. 使用 SSE串流回覆的API，並掛上記憶，以 sessionId 參數隔離不同使用者的對話記憶。\n\n### AI Agent 提示詞 — 加入工具呼叫能力\n請為 CRM AI 助手加入工具呼叫（Tool Calling）能力，讓 AI 能查詢資料庫的即時資料：\n1. 建立 CustomerTools 類別，用 @Tool 註解包裝既有的 CustomerService：\n   - getCustomers(query)：查詢客戶列表，支援關鍵字搜尋，query 為空時回傳全部客戶\n   - getCustomerDetail(id)：依客戶 ID 查詢詳細資訊（等級、產業、聯絡方式）\n2. 建立 OpportunityTools 類別，用 @Tool 包裝既有的 OpportunityService：\n   - getOpportunities(customerId)：查詢指定客戶的所有銷售機會\n   - getDealValue(opportunityId)：查詢特定銷售機會的金額與成交機率\n3. 每個 @Tool 的 description 請用中文清楚描述「什麼情況下該呼叫這個工具」，這段文字是寫給大模型看的。\n4. 在 ChatController 的 ChatClient 呼叫鏈中，用 .tools() 掛上這兩個工具類別。\n完成後請示範一句測試提問（例如「台積電目前的客戶等級是什麼？有哪些進行中的銷售機會？」），並說明如何從後端日誌確認工具真的被呼叫。\n\n### AI Agent 提示詞 — 前端串流與安全認證\n請在現有專案中完成以下前端與後端的串流對話安全升級：\n1. 由於原生的 EventSource 無法在 Header 中自訂 Token 進行驗證，請修改後端 `JwtAuthenticationFilter.java` 的 `parseJwt` 方法，使其除了支援從 `Authorization` 標頭讀取 Token 外，也支援從 URL 的 Query 參數（例如 `token`）中取得並驗證 Token。\n2. 在前端 `ChatRoom.jsx` 中，連接後端的 `/api/ai/stream` 串流對話介面。連線時，請從 `localStorage` 中讀取先前儲存的 JWT Token，並以網址參數形式帶入（例如 `/api/ai/stream?message=xxx&token=yyy`），以安全地建立 EventSource 串流對話連線。\n\n",
        "promptMac": "請接續 Unit 5 的實作內容。現在我們要進入 Unit 6：Spring AI ChatClient、SSE 與 tool calling。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### AI Agent 提示詞 — 建立 ChatClient 對話入口\n請在現有的 Spring Boot CRM 專案中加入 AI 對話功能\n\n1. 請在現有的 Spring Boot CRM 專案中加入 AI 對話功能\n2. 設定 Groq 的 base-url，模型用 openai/gpt-oss-120b，API Key 為 \"xxxxxxx\"。\n3. 系統提示詞：「你是一個親切的 CRM 智慧助手」。\n4. 使用 SSE串流回覆的API，並掛上記憶，以 sessionId 參數隔離不同使用者的對話記憶。\n\n### AI Agent 提示詞 — 加入工具呼叫能力\n請為 CRM AI 助手加入工具呼叫（Tool Calling）能力，讓 AI 能查詢資料庫的即時資料：\n1. 建立 CustomerTools 類別，用 @Tool 註解包裝既有的 CustomerService：\n   - getCustomers(query)：查詢客戶列表，支援關鍵字搜尋，query 為空時回傳全部客戶\n   - getCustomerDetail(id)：依客戶 ID 查詢詳細資訊（等級、產業、聯絡方式）\n2. 建立 OpportunityTools 類別，用 @Tool 包裝既有的 OpportunityService：\n   - getOpportunities(customerId)：查詢指定客戶的所有銷售機會\n   - getDealValue(opportunityId)：查詢特定銷售機會的金額與成交機率\n3. 每個 @Tool 的 description 請用中文清楚描述「什麼情況下該呼叫這個工具」，這段文字是寫給大模型看的。\n4. 在 ChatController 的 ChatClient 呼叫鏈中，用 .tools() 掛上這兩個工具類別。\n完成後請示範一句測試提問（例如「台積電目前的客戶等級是什麼？有哪些進行中的銷售機會？」），並說明如何從後端日誌確認工具真的被呼叫。\n\n### AI Agent 提示詞 — 前端串流與安全認證\n請在現有專案中完成以下前端與後端的串流對話安全升級：\n1. 由於原生的 EventSource 無法在 Header 中自訂 Token 進行驗證，請修改後端 `JwtAuthenticationFilter.java` 的 `parseJwt` 方法，使其除了支援從 `Authorization` 標頭讀取 Token 外，也支援從 URL 的 Query 參數（例如 `token`）中取得並驗證 Token。\n2. 在前端 `ChatRoom.jsx` 中，連接後端的 `/api/ai/stream` 串流對話介面。連線時，請從 `localStorage` 中讀取先前儲存的 JWT Token，並以網址參數形式帶入（例如 `/api/ai/stream?message=xxx&token=yyy`），以安全地建立 EventSource 串流對話連線。\n\n",
        "prompts": [
          {
            "title": "高級 AI 協作提示詞大師範本（Windows 版）",
            "note": "可直接交給 Codex / Gemini 進行輔助開發",
            "text": "請接續 Unit 5 的實作內容。現在我們要進入 Unit 6：Spring AI ChatClient、SSE 與 tool calling。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### AI Agent 提示詞 — 建立 ChatClient 對話入口\n請在現有的 Spring Boot CRM 專案中加入 AI 對話功能\n\n1. 請在現有的 Spring Boot CRM 專案中加入 AI 對話功能\n2. 設定 Groq 的 base-url，模型用 openai/gpt-oss-120b，API Key 為 \"xxxxxxx\"。\n3. 系統提示詞：「你是一個親切的 CRM 智慧助手」。\n4. 使用 SSE串流回覆的API，並掛上記憶，以 sessionId 參數隔離不同使用者的對話記憶。\n\n### AI Agent 提示詞 — 加入工具呼叫能力\n請為 CRM AI 助手加入工具呼叫（Tool Calling）能力，讓 AI 能查詢資料庫的即時資料：\n1. 建立 CustomerTools 類別，用 @Tool 註解包裝既有的 CustomerService：\n   - getCustomers(query)：查詢客戶列表，支援關鍵字搜尋，query 為空時回傳全部客戶\n   - getCustomerDetail(id)：依客戶 ID 查詢詳細資訊（等級、產業、聯絡方式）\n2. 建立 OpportunityTools 類別，用 @Tool 包裝既有的 OpportunityService：\n   - getOpportunities(customerId)：查詢指定客戶的所有銷售機會\n   - getDealValue(opportunityId)：查詢特定銷售機會的金額與成交機率\n3. 每個 @Tool 的 description 請用中文清楚描述「什麼情況下該呼叫這個工具」，這段文字是寫給大模型看的。\n4. 在 ChatController 的 ChatClient 呼叫鏈中，用 .tools() 掛上這兩個工具類別。\n完成後請示範一句測試提問（例如「台積電目前的客戶等級是什麼？有哪些進行中的銷售機會？」），並說明如何從後端日誌確認工具真的被呼叫。\n\n### AI Agent 提示詞 — 前端串流與安全認證\n請在現有專案中完成以下前端與後端的串流對話安全升級：\n1. 由於原生的 EventSource 無法在 Header 中自訂 Token 進行驗證，請修改後端 `JwtAuthenticationFilter.java` 的 `parseJwt` 方法，使其除了支援從 `Authorization` 標頭讀取 Token 外，也支援從 URL 的 Query 參數（例如 `token`）中取得並驗證 Token。\n2. 在前端 `ChatRoom.jsx` 中，連接後端的 `/api/ai/stream` 串流對話介面。連線時，請從 `localStorage` 中讀取先前儲存的 JWT Token，並以網址參數形式帶入（例如 `/api/ai/stream?message=xxx&token=yyy`），以安全地建立 EventSource 串流對話連線。\n\n"
          },
          {
            "title": "AI 客戶摘要與下一步建議 API",
            "note": "課程必做功能：讓 AI 根據真實 CRM 資料自動產生客戶摘要與行動建議",
            "text": "請在現有 CRM 專案中建立客戶摘要與下一步建議的 AI 功能，這是結訓專案的必做功能之一。\n\n### 步驟一：建立 CrmInsightTools\n在既有的 CustomerTools 與 OpportunityTools 之外，新建 CrmInsightTools 類別，用 @Tool 包裝以下查詢能力：\n1. getCustomerSummaryData(customerId)：一次取回客戶基本資訊、最近 5 筆互動紀錄、所有進行中商機，回傳結構化的 JSON 字串供模型閱讀\n2. getCustomerRiskSignals(customerId)：計算並回傳風險訊號清單，邏輯如下：\n   - 最近 30 天無互動紀錄 → 標記「互動冷卻」\n   - 有商機超過預計成交日仍未結案 → 標記「商機逾期」\n   - 客戶分級為 VIP 但近 60 天互動少於 2 次 → 標記「VIP 關注不足」\n3. 每個 @Tool 的 description 描述清楚觸發情境，讓模型知道何時呼叫\n\n### 步驟二：建立客戶摘要 API\n新增一個獨立的 REST endpoint（非串流）：\nGET /api/ai/customer-summary/{customerId}\n\n功能：\n1. 用 ChatClient 呼叫模型，system prompt 定義角色為「CRM 客戶分析師」\n2. user prompt 組裝為：「請根據以下客戶資料，產生一份結構化摘要，包含：(1) 客戶概況 (2) 近期活躍度 (3) 風險訊號 (4) 下一步建議行動（具體到寄信、安排會議、請主管介入或補資料）」\n3. 掛上 CrmInsightTools，讓模型透過工具取得真實資料\n4. 回傳完整的 AI 摘要文字（非串流，一次回傳）\n\n### 步驟三：前端整合\n在 CustomerDetailPage 的「AI 建議」Tab 中：\n1. 點擊「產生客戶摘要」按鈕，呼叫 GET /api/ai/customer-summary/{customerId}\n2. 載入中顯示骨架屏\n3. 摘要回傳後顯示為卡片，區分：客戶概況、風險訊號（紅色標籤）、建議行動（綠色標籤）\n4. 若 API 回傳錯誤，顯示「AI 暫時無法產生摘要，請稍後再試」\n\n### 驗證\n- 對 VIP 客戶（如台積電）產生摘要，確認包含其商機金額、互動頻率等真實數據\n- 對久未互動的客戶產生摘要，確認出現「互動冷卻」等風險訊號\n- AI 的數字（金額、次數）必須來自工具查詢結果，不可自行編造"
          }
        ],
        "tasks": [
          {
            "id": "u6-t1",
            "label": "確認模型端點與金鑰配置"
          },
          {
            "id": "u6-t2",
            "label": "完成 ChatClient 串流流程閱讀"
          },
          {
            "id": "u6-t3",
            "label": "整合 Spring Security 透過 Principal 隔離對話 Session"
          },
          {
            "id": "u6-t4",
            "label": "辨識可抽成工具的方法"
          },
          {
            "id": "u6-t5",
            "label": "整理 `@Tool` 與 `ToolCallback` 差異"
          },
          {
            "id": "u6-t6",
            "label": "記錄 1 個 AI 查客戶即時資料情境"
          },
          {
            "id": "u6-t7",
            "label": "理解 SSE (Server-Sent Events) 單向串流原理與安全限制"
          },
          {
            "id": "u6-t8",
            "label": "升級後端 JwtAuthenticationFilter 支援網址 Query Token"
          },
          {
            "id": "u6-t9",
            "label": "使用 React 實作 EventSource 帶 Token 監聽與狀態更新"
          }
        ],
        "materials": [
          {
            "id": "mat7",
            "type": "MD",
            "name": "Spring_AI_與_Tool_Calling_架構說明",
            "desc": "Spring AI ChatClient 抽象層、System Prompt 設計以及結合 Java Reflection 的 Tool Calling 機制。"
          }
        ],
        "illustrations": [
          {
            "name": "u6-1.png",
            "kind": "hero",
            "alt": "Spring AI Streaming",
            "spec": "ChatClient、SSE 與 Tool Calling"
          },
          {
            "name": "u6-2.png",
            "kind": "diagram",
            "alt": "AI 助理資料調用",
            "spec": "流程圖：User Prompt -> ChatClient -> Tool Call -> Streaming UI"
          },
          {
            "name": "u6-3-term.png",
            "kind": "term",
            "alt": "Spring AI ChatClient、SSE 與 tool calling 專業術語解釋",
            "spec": "SSE / ChatClient / Tool Calling"
          }
        ]
      },
      {
        "id": "u7",
        "title": "RAG、pgvector、MCP 與知識庫擴充",
        "subtitle": "以 pgvector 儲存產品型錄與話術進行向量檢索，並設計 MCP 服務以擴充 AI 與外部工具的整合能力。",
        "time": "13:00 ~ 17:00",
        "features": [
          "將文件切分、向量化並儲存到 pgvector，讓模型先檢索再作答，降低憑空回答風險。",
          "補充說明 MCP 在 AI 系統中的標準化接口角色與 Spring AI 的接入方式，並介紹 Skills 開放標準與 Spring AI Community 官方的 spring-ai-agent-utils 套件（SkillsTool 與 Agent 工具集）。",
          "將聊天室的對話紀錄（User Prompt 與 AI Response）非同步向量化儲存，並在發問時透過多路 RAG 同時檢索客戶知識庫與歷史對話，打造具備長效語意記憶的 AI 助手。"
        ],
        "goals": [
          "理解 RAG 與傳統純對話模型的差異",
          "掌握 Embedding、切分與檢索流程",
          "理解 pgvector 與 VectorStore 在架構中的分工",
          "理解 MCP 對跨工具 AI 生態的價值與 Spring AI 的接入方式",
          "理解 Skills 開放標準與 MCP 的角色差異",
          "認識 Spring AI Community 的 spring-ai-agent-utils 與其 Agent 工具集",
          "知道如何在 Spring Boot 中以 SkillsTool 為應用加入 Skills",
          "理解對話歷史向量化的重要性與長期記憶挑戰",
          "實作在 Spring Boot 串流結束時非同步寫入向量庫",
          "使用 Metadata 進行對話歷史隔離與精確過濾",
          "設計雙路 RAG 檢索：同時查詢知識庫與歷史記憶並進行內容合併"
        ],
        "principle": "知識庫檢索 (RAG) 解決了 LLM 的時效與內部知識盲區問題。pgvector provide SQL 級別的向量距離檢索，配合 Ingestion 切片與長期對話歷史記憶；而 MCP 則為 Agent 提供跨系統工具連結的標準介面。",
        "concepts": [
          {
            "heading": "RAG 核心概念",
            "group": "RAG 原理與 ETL 流程",
            "body": "**RAG 的基本想法**\n\nRAG 可以把它想成「開卷考試」。模型不再只依賴自己訓練時學過的內容，而是先去找與問題最相關的文檔片段，再根據那些片段作答。\n\n這種做法的好處是知識更新成本低、可控性高，而且可以明確限制回答只依據文件內容。\n\n---\n\n**向量嵌入與 pgvector**\n\nEmbedding 會把文字轉成固定長度的向量。在向量空間中，語意越接近，距離就越近。這讓資料庫可以用「相似度」而不是精確關鍵字比對來找內容。\n\n`pgvector` 是 PostgreSQL 的擴充套件，負責提供向量欄位與最近鄰搜尋能力；`VectorStore` 則是 Spring AI 在程式中的抽象介面。"
          },
          {
            "heading": "RAG vs Fine-Tuning 完整比較",
            "group": "RAG 原理與 ETL 流程",
            "body": "選擇 RAG 還是 Fine-Tuning，取決於資料更新頻率、幻覺容忍度與開發成本。對大多數企業文件場景，RAG 是首選。\n\n**RAG vs Fine-Tuning 比較 (text)**\n```text\n                    RAG（本課程方案）        Fine-Tuning（微調）\n──────────────────────────────────────────────────────────\n本質               外部知識檢索（開卷考試）   內部參數調整（閉卷考試）\n即時更新            極快（只需更新向量庫）     極慢（需要重新訓練）\n幻覺控制            極佳（可強制依文件回答）   較差（仍可能胡言亂語）\n開發成本            低                        高\n適用場景            FAQ、內規、產品文件        垂直領域語言風格微調\n```"
          },
          {
            "heading": "ETL 三步驟：文件到向量庫",
            "group": "RAG 原理與 ETL 流程",
            "body": "上傳文件到向量庫要經過 Extract → Transform → Load 三步驟。`TokenTextSplitter` 把長文切成小塊，讓每塊語意聚焦，檢索時相關度才會精準。\n\n- Extract — 用 `TextReader` 讀取檔案，封裝成 `Document` 物件\n- Transform — 用 `TokenTextSplitter` 切分，預設每塊約 800 tokens，相鄰塊重疊 100 tokens 防止語意被截斷\n- Load — `vectorStore.accept()` 自動呼叫 EmbeddingModel 產生向量並寫入 PostgreSQL\n\n**RAGController.java — 文件上傳 ETL 流程 (java)**\n```java\n@PostMapping(\"/upload\")\npublic String uploadDocument(@RequestParam(\"file\") MultipartFile file) {\n    // Extract：讀取檔案為 Document 清單\n    Resource resource = new ByteArrayResource(file.getBytes());\n    List<Document> documents = new TextReader(resource).get();\n\n    // Transform：切分長文，避免 Embedding 向量資訊過度稀釋\n    TokenTextSplitter splitter = new TokenTextSplitter();\n    List<Document> splitDocuments = splitter.apply(documents);\n\n    // Load：向量化並寫入 pgvector\n    vectorStore.accept(splitDocuments);\n\n    return \"已上傳並完成向量化，共 \" + splitDocuments.size() + \" 個片段\";\n}\n```"
          },
          {
            "heading": "AI Agent 提示詞 — 建立 RAG 知識庫",
            "group": "RAG 原理與 ETL 流程",
            "body": "理解 RAG 流程與向量檢索原理後，複製以下提示詞給 AI Agent，引導它完成文件上傳、向量化與知識庫問答兩支 API。\n\n**AI Agent 提示詞 (text)**\n```text\n請為專案加入 RAG 知識庫功能，使用 PostgreSQL pgvector 儲存向量：\n1. 在 pom.xml 加入 spring-ai-starter-vector-store-pgvector 依賴，並確認 application.yml 已設定 Voyage AI 的 embedding 端點（環境變數 VOYAGE_API_KEY）。\n2. 建立 RAGController，提供兩支 API：\n   - POST /api/rag/upload：接收上傳的文字檔，用 TextReader 讀取、TokenTextSplitter 切分成小段，最後用 vectorStore.accept() 向量化寫入資料庫\n   - GET /api/rag/query：掛上 QuestionAnswerAdvisor，讓 AI 先檢索相關文件片段再回答\n3. 程式碼需有中文函式註解。\n完成後請示範驗證流程：先上傳一份「客戶服務規範」文件，再提問「客戶服務規範是什麼？」，確認 AI 是根據文件內容回答，而不是自由發揮。\n```"
          },
          {
            "heading": "MCP 核心概念",
            "group": "MCP 與 Skills 擴充",
            "body": "**MCP 應該怎麼理解**\n\nMCP 可以想成 AI 世界的標準插座。當系統中有多個模型平台、IDE 工具與資料來源時，MCP 提供一致的方式描述工具與上下文。\n\n它的價值不是取代應用程式內部工具呼叫，而是讓不同 AI 客戶端可以以標準方式接入同一批能力。\n\n- Client 是 AI 平台或整合工具\n- Host / Server 提供工具與上下文能力\n- Model 專注在理解與生成\n\n---\n\n**AI 呼叫遠端工具的完整流程**\n\n以「有哪些客戶？」為例，說明從使用者輸入到 AI 回答的跨服務執行路徑：\n\n- ① 使用者呼叫 `GET http://localhost:8081/api/mcp/chat?message=有哪些客戶`\n- ② MCP Client（port 8081）的 ChatClient 收到訊息，交由 LLM 判斷意圖\n- ③ AI 判斷需要查客戶，透過已建立的 SSE 連線，向 MCP Server（port 8080）發送 getCustomers 工具呼叫請求\n- ④ MCP Server 執行 `CustomerTools.getCustomers(\"\")` → 查詢 PostgreSQL → 回傳客戶 JSON\n- ⑤ MCP Client 收到工具執行結果，AI 組合成自然語言回答\n- ⑥ 使用者收到：「目前共有 5 筆客戶：1. 台積電（VIP）...」"
          },
          {
            "heading": "Skills 與 Agent 工具",
            "group": "MCP 與 Skills 擴充",
            "body": "**Skills 是什麼：把專業知識打包給 AI**\n\nSkills（Agent Skills）是 Anthropic 於 2025 年提出的開放標準：把某個領域的程序性知識（操作流程、規範、範本、輔助腳本）打包成一個資料夾，核心是一份帶有名稱與描述的 SKILL.md 說明檔。AI 平時只看到每個 Skill 的一行描述，judged 相關時才載入完整內容，這種「漸進式載入」讓模型能掛上大量專業知識而不撐爆上下文。\n\n如果說 Tool Calling 與 MCP 解決的是「AI 能呼叫什麼工具、拿到什麼資料」，Skills 解決的則是「AI 應該照什麼流程與規範做事」。兩者互補，不是替代關係。\n\n- Skill = 資料夾 + SKILL.md（frontmatter 描述）+ 選配的範本與腳本\n- 平時只載入描述，被點名才載入全文 — 漸進式揭露（progressive disclosure）\n- MCP 擴充「能力與資料」，Skills 擴充「知識與流程」\n\n---\n\n**MCP vs Skills：擴充 AI 能力的兩條路**\n\n- MCP：標準化「連接」— 讓 AI 客戶端以一致方式接上工具與資料來源，重點在執行能力\n- Skills：標準化「知識」— 讓 AI 依描述按需載入工作流程與規範，重點在做事方法\n- 判斷方式：要讓 AI「查得到、做得到」用 MCP / Tool Calling；要讓 AI「做得對、有章法」用 Skills\n- 實務上常見組合：Skill 內的流程指示 AI 在特定步驟呼叫 MCP 工具完成查詢或寫入"
          },
          {
            "heading": "Spring AI 如何加入 Skills：spring-ai-agent-utils",
            "group": "MCP 與 Skills 擴充",
            "body": "Skills 是 Claude、Claude Code 等 AI 客戶端原生支援的標準，但 Spring AI 核心框架尚未內建 Skills 概念。在 Spring Boot 應用中要讓 AI 具備 Skills 能力，目前的推薦做法是引用 Spring AI Community（Spring AI 官方社群組織）維護的 spring-ai-agent-utils 套件 — 它把 Claude Code 風格的 Agent 工具與 Skills 機制帶進 Java 應用。\n\n版本注意：spring-ai-agent-utils 0.9.0 要求 Spring AI 2.0.0-RC1 以上、Java 17+、Spring Boot 3.x / 4.x。本課程專案使用 Spring AI 2.0.0-M8，導入前需先把 Spring AI 升到 RC1 以上版本。\n\n- 路線一（推薦）：引用 spring-ai-agent-utils 的 SkillsTool，以 Markdown + YAML frontmatter 定義可重用知識模組\n- 路線二：自行實作最小核心 — 掃描 skills/ 目錄、解析 SKILL.md、把描述注入 system prompt（原理與套件相同）\n- Skill 檔案本身是純 Markdown、與平台無關，可在 Claude Code 與 Spring Boot 應用間共用同一份"
          },
          {
            "heading": "為什麼需要對話歷史 RAG",
            "group": "對話歷史長期記憶",
            "body": "基本的 ChatMemory (對話記憶) 只能儲存最近幾次對話（短期記憶），且受限於大模型上下文窗口 (Context Window) 的長度與 Token 開銷。當用戶問到「幾天前我們討論過的那個方案」時，短期記憶無能為力。\n\n藉由將「歷史問答」向量化寫入資料庫，我們可以使用 RAG 搜尋「語意相似的過去對話」，動態將相關記憶塞入 Prompt，使 AI 具備橫跨數週甚至數月的長期語意記憶。\n\n- 短期記憶 (ChatMemory)：儲存於記憶體，限制 N 筆對話，成本高且會隨重啟消失。\n- 長期記憶 (History RAG)：向量化存入 PostgreSQL (pgvector)，需要時以語意搜尋檢索，開銷小且永久保存。"
          },
          {
            "heading": "AI Agent 提示詞 — 對話歷史長期記憶",
            "group": "對話歷史長期記憶",
            "body": "理解長期記憶的設計原理（非同步寫入、Metadata 隔離、雙路檢索）後，複製以下提示詞給 AI Agent，為聊天室加上「長期記憶」能力。\n\n**AI Agent 提示詞 (text)**\n```text\n請為聊天室加入「長期記憶」功能，把對話歷史向量化存入 pgvector：\n1. 建立 ChatHistoryService，提供 saveToVectorStore(sessionId, prompt, response) 方法：把使用者問句與 AI 回答組成一份 Document，metadata 加上 sessionId、type 為 chat_history 與建立時間，再寫入 VectorStore。\n2. 修改 SSE 串流介面：用 doOnNext 收集完整回覆內容，並在 doOnComplete 時用 CompletableFuture.runAsync 非同步寫入向量庫，避免阻塞串流回應。\n3. 實作雙路 RAG 檢索方法：使用者提問時同時查兩路 —— 客戶文件（filterExpression 過濾 type 為 customer_doc）取前 3 筆、該使用者的歷史對話（type 為 chat_history 且 sessionId 相符）取前 2 筆，合併後作為上下文交給模型。\n4. 程式碼需有中文函式註解。\n完成後請示範驗證流程：先聊「我專注半導體產業客戶、偏好 VIP 等級」，之後開新對話問「根據我之前說的需求推薦適合的客戶」，確認 AI 記得歷史偏好。\n```"
          },
          {
            "heading": "CRM 知識庫設計：哪些文件該向量化",
            "group": "CRM 知識庫策略",
            "body": "AI CRM 助理的智慧程度取決於知識庫的品質。以下是三類需要向量化的文件，以及各自的 RAG 策略：\n\n**1. 客戶互動紀錄（Activity Summary）**\n每次業務拜訪、電話、會議的摘要文字，是 AI 判斷客戶狀態與意向的核心依據。向量化後可支援「這位客戶最近關注什麼？」「過去有沒有類似的抱怨？」等語意查詢。\n\n**2. 產品文件與方案規格**\n技術規格書、功能比較表、定價方案。讓 AI 能根據客戶需求推薦適合的產品組合，並準確引用規格細節。\n\n**3. 銷售話術與成功案例**\n歷史成交案例的策略復盤、異議處理話術、續約成功的談判要點。讓 AI 在面對類似情境時能提供經過驗證的建議。\n\n**ETL 設計考量**\n- 互動紀錄按時間分段（每次互動一個向量），確保時序資訊可追溯\n- 產品文件按章節分割（chunk），每個 chunk 包含完整語意單元\n- 用 metadata filter 區分文件類型，查詢時可指定只搜尋特定類別"
          }
        ],
        "prompt": "請接續 Unit 6 的實作內容。現在我們要進入 Unit 7：RAG、pgvector、MCP 與知識庫擴充。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### AI Agent 提示詞 — 建立 RAG 知識庫\n請為專案加入 RAG 知識庫功能，使用 PostgreSQL pgvector 儲存向量：\n1. 在 pom.xml 加入 spring-ai-starter-vector-store-pgvector 依賴，並確認 application.yml 已設定 Voyage AI 的 embedding 端點（環境變數 VOYAGE_API_KEY）。\n2. 建立 RAGController，提供兩支 API：\n   - POST /api/rag/upload：接收上傳的文字檔，用 TextReader 讀取、TokenTextSplitter 切分成小段，最後用 vectorStore.accept() 向量化寫入資料庫\n   - GET /api/rag/query：掛上 QuestionAnswerAdvisor，讓 AI 先檢索相關文件片段再回答\n3. 程式碼需有中文函式註解。\n完成後請示範驗證流程：先上傳一份「客戶服務規範」文件，再提問「客戶服務規範是什麼？」，確認 AI 是根據文件內容回答，而不是自由發揮。\n\n### AI Agent 提示詞 — 對話歷史長期記憶\n請為聊天室加入「長期記憶」功能，把對話歷史向量化存入 pgvector：\n1. 建立 ChatHistoryService，提供 saveToVectorStore(sessionId, prompt, response) 方法：把使用者問句與 AI 回答組成一份 Document，metadata 加上 sessionId、type 為 chat_history 與建立時間，再寫入 VectorStore。\n2. 修改 SSE 串流介面：用 doOnNext 收集完整回覆內容，並在 doOnComplete 時用 CompletableFuture.runAsync 非同步寫入向量庫，避免阻塞串流回應。\n3. 實作雙路 RAG 檢索方法：使用者提問時同時查兩路 —— 客戶文件（filterExpression 過濾 type 為 customer_doc）取前 3 筆、該使用者的歷史對話（type 為 chat_history 且 sessionId 相符）取前 2 筆，合併後作為上下文交給模型。\n4. 程式碼需有中文函式註解。\n完成後請示範驗證流程：先聊「我專注半導體產業客戶、偏好 VIP 等級」，之後開新對話問「根據我之前說的需求推薦適合的客戶」，確認 AI 記得歷史偏好。\n\n",
        "promptMac": "請接續 Unit 6 的實作內容。現在我們要進入 Unit 7：RAG、pgvector、MCP 與知識庫擴充。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### AI Agent 提示詞 — 建立 RAG 知識庫\n請為專案加入 RAG 知識庫功能，使用 PostgreSQL pgvector 儲存向量：\n1. 在 pom.xml 加入 spring-ai-starter-vector-store-pgvector 依賴，並確認 application.yml 已設定 Voyage AI 的 embedding 端點（環境變數 VOYAGE_API_KEY）。\n2. 建立 RAGController，提供兩支 API：\n   - POST /api/rag/upload：接收上傳的文字檔，用 TextReader 讀取、TokenTextSplitter 切分成小段，最後用 vectorStore.accept() 向量化寫入資料庫\n   - GET /api/rag/query：掛上 QuestionAnswerAdvisor，讓 AI 先檢索相關文件片段再回答\n3. 程式碼需有中文函式註解。\n完成後請示範驗證流程：先上傳一份「客戶服務規範」文件，再提問「客戶服務規範是什麼？」，確認 AI 是根據文件內容回答，而不是自由發揮。\n\n### AI Agent 提示詞 — 對話歷史長期記憶\n請為聊天室加入「長期記憶」功能，把對話歷史向量化存入 pgvector：\n1. 建立 ChatHistoryService，提供 saveToVectorStore(sessionId, prompt, response) 方法：把使用者問句與 AI 回答組成一份 Document，metadata 加上 sessionId、type 為 chat_history 與建立時間，再寫入 VectorStore。\n2. 修改 SSE 串流介面：用 doOnNext 收集完整回覆內容，並在 doOnComplete 時用 CompletableFuture.runAsync 非同步寫入向量庫，避免阻塞串流回應。\n3. 實作雙路 RAG 檢索方法：使用者提問時同時查兩路 —— 客戶文件（filterExpression 過濾 type 為 customer_doc）取前 3 筆、該使用者的歷史對話（type 為 chat_history 且 sessionId 相符）取前 2 筆，合併後作為上下文交給模型。\n4. 程式碼需有中文函式註解。\n完成後請示範驗證流程：先聊「我專注半導體產業客戶、偏好 VIP 等級」，之後開新對話問「根據我之前說的需求推薦適合的客戶」，確認 AI 記得歷史偏好。\n\n",
        "prompts": [
          {
            "title": "高級 AI 協作提示詞大師範本（Windows 版）",
            "note": "可直接交給 Codex / Gemini 進行輔助開發",
            "text": "請接續 Unit 6 的實作內容。現在我們要進入 Unit 7：RAG、pgvector、MCP 與知識庫擴充。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### AI Agent 提示詞 — 建立 RAG 知識庫\n請為專案加入 RAG 知識庫功能，使用 PostgreSQL pgvector 儲存向量：\n1. 在 pom.xml 加入 spring-ai-starter-vector-store-pgvector 依賴，並確認 application.yml 已設定 Voyage AI 的 embedding 端點（環境變數 VOYAGE_API_KEY）。\n2. 建立 RAGController，提供兩支 API：\n   - POST /api/rag/upload：接收上傳的文字檔，用 TextReader 讀取、TokenTextSplitter 切分成小段，最後用 vectorStore.accept() 向量化寫入資料庫\n   - GET /api/rag/query：掛上 QuestionAnswerAdvisor，讓 AI 先檢索相關文件片段再回答\n3. 程式碼需有中文函式註解。\n完成後請示範驗證流程：先上傳一份「客戶服務規範」文件，再提問「客戶服務規範是什麼？」，確認 AI 是根據文件內容回答，而不是自由發揮。\n\n### AI Agent 提示詞 — 對話歷史長期記憶\n請為聊天室加入「長期記憶」功能，把對話歷史向量化存入 pgvector：\n1. 建立 ChatHistoryService，提供 saveToVectorStore(sessionId, prompt, response) 方法：把使用者問句與 AI 回答組成一份 Document，metadata 加上 sessionId、type 為 chat_history 與建立時間，再寫入 VectorStore。\n2. 修改 SSE 串流介面：用 doOnNext 收集完整回覆內容，並在 doOnComplete 時用 CompletableFuture.runAsync 非同步寫入向量庫，避免阻塞串流回應。\n3. 實作雙路 RAG 檢索方法：使用者提問時同時查兩路 —— 客戶文件（filterExpression 過濾 type 為 customer_doc）取前 3 筆、該使用者的歷史對話（type 為 chat_history 且 sessionId 相符）取前 2 筆，合併後作為上下文交給模型。\n4. 程式碼需有中文函式註解。\n完成後請示範驗證流程：先聊「我專注半導體產業客戶、偏好 VIP 等級」，之後開新對話問「根據我之前說的需求推薦適合的客戶」，確認 AI 記得歷史偏好。\n\n"
          },
          {
            "title": "MCP 工具設計與實作 — 外部系統擴充",
            "note": "建立 MCP Server，讓 AI 助理具備行事曆排程與 Email 草稿等跨系統能力",
            "text": "接續 RAG 知識庫完成後，現在要為 CRM AI 助理加入 MCP（Model Context Protocol）外部工具擴充能力。MCP 與 domain tool calling 的差別在於：domain tool 是專案內部的 @Tool 方法，MCP 則是獨立的外部工具伺服器，可被多個 AI 應用共用。\n\n### 步驟一：理解 MCP 在 CRM 中的定位\n在建立 MCP 之前，先回答以下設計問題（寫在 mcp-design.md 中）：\n1. 查詢客戶列表 → 應該用 domain tool 還是 MCP？為什麼？\n2. 排程明天下午 2 點與客戶開會 → 應該用 domain tool 還是 MCP？為什麼？\n3. 產生一封 Email 草稿寄給客戶 → 應該用 domain tool 還是 MCP？為什麼？\n4. 匯出本月商機報表為 CSV → 應該用 domain tool 還是 MCP？為什麼？\n\n選用原則：\n- domain tool（@Tool）：存取本專案資料庫與 Service 的內部操作\n- MCP：跨系統、可重用、可獨立部署的外部工具（行事曆、郵件、報表、第三方 CRM）\n\n### 步驟二：建立 CRM MCP Server（Spring Boot 獨立專案）\n請用 Spring Boot 建立一個獨立的 MCP Server 專案（crm-mcp-server），功能如下：\n\n1. 加入 spring-ai-starter-mcp-server-webmvc 依賴\n2. 實作以下 3 個 MCP Tool：\n\n【Tool 1：scheduleCalendarEvent】\n- 描述：排程一個行事曆事件（會議、電話、拜訪）\n- 輸入：title（必填）、date（yyyy-MM-dd）、time（HH:mm）、duration（分鐘，預設 60）、attendees（參與者 Email 列表）\n- 輸出：確認訊息與事件 ID（模擬實作，用 UUID 回傳即可）\n- 用途示範：AI 建議「安排下週與台積電張經理的會議」時，可直接呼叫此工具\n\n【Tool 2：draftEmail】\n- 描述：根據 CRM 情境產生 Email 草稿\n- 輸入：to（收件人 Email）、subject（主旨）、context（CRM 情境描述，例如「跟進台積電的設備採購商機」）\n- 輸出：產生的 Email 草稿文字（模擬實作，用模板組裝）\n- 用途示範：AI 建議「寄信跟進商機」時，可產生草稿供業務確認\n\n【Tool 3：exportOpportunityReport】\n- 描述：匯出商機報表摘要\n- 輸入：period（月份，例如 2025-06）、format（csv 或 summary）\n- 輸出：若 format=summary 回傳文字摘要，若 format=csv 回傳 CSV 檔案路徑（模擬實作）\n- 用途示範：主管詢問「本月商機狀況如何？」時呼叫\n\n3. 每個 Tool 用 @Tool 註解標註，description 用中文描述觸發情境\n4. MCP Server 啟動在 port 8081\n\n### 步驟三：在 CRM 主專案中串接 MCP Client\n1. 在主專案 pom.xml 加入 spring-ai-starter-mcp-client 依賴\n2. 在 application.yml 設定 MCP Server 連線位址：http://localhost:8081\n3. 在 ChatClient 的 .tools() 中同時掛上 domain tools（CustomerTools、OpportunityTools）與 MCP tools\n4. 測試提問：「幫我安排明天下午兩點跟台積電張經理的會議」，確認 AI 呼叫 MCP 的 scheduleCalendarEvent\n\n### 驗證\n1. 單獨啟動 MCP Server（port 8081），確認 /actuator/health 正常\n2. 啟動 CRM 主專案（port 8080），確認 log 中出現 MCP tools 註冊成功的訊息\n3. 在 AI 聊天室提問涉及行事曆或 Email 的問題，確認 MCP tool 被正確呼叫\n4. 同一個對話中混合使用 domain tool（查客戶）與 MCP tool（排會議），確認兩者可以共存"
          }
        ],
        "tasks": [
          {
            "id": "u7-t1",
            "label": "整理 RAG 三步驟流程"
          },
          {
            "id": "u7-t2",
            "label": "確認 pgvector 與 VectorStore 的分工"
          },
          {
            "id": "u7-t3",
            "label": "記錄 1 個知識庫問答驗證情境"
          },
          {
            "id": "u7-t4",
            "label": "理解 MCP Server / Client 在 Spring AI 的接入方式與呼叫流程"
          },
          {
            "id": "u7-t5",
            "label": "理解 MCP 與 Skills 的角色差異與適用場景"
          },
          {
            "id": "u7-t6",
            "label": "瀏覽 spring-ai-agent-utils 專案與工具清單，評估導入 SkillsTool 的版本前置條件"
          },
          {
            "id": "u7-t7",
            "label": "設計非同步向量化儲存 Service 邏輯"
          },
          {
            "id": "u7-t8",
            "label": "在 SSE 串流完成時 (doOnComplete) 觸發歷史寫入"
          },
          {
            "id": "u7-t9",
            "label": "實作多路檢索與上下文合併邏輯"
          }
        ],
        "materials": [
          {
            "id": "mat8",
            "type": "MD",
            "name": "Embabel_黑板機制與GOAP動作規劃指南",
            "desc": "（選修附錄）Embabel GOAP 演算法原理與 Blackboard 機制。Embabel 2.0 穩定後可升級為正式單元。"
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
            "name": "u7-2.png",
            "kind": "diagram",
            "alt": "檢索增強生成管線",
            "spec": "流程圖：Chunk -> Embedding -> pgvector -> Citation"
          },
          {
            "name": "u7-3-term.png",
            "kind": "term",
            "alt": "RAG、pgvector、MCP 與知識庫擴充 專業術語解釋",
            "spec": "Embedding / RAG / MCP"
          }
        ]
      },
      {
        "id": "u8",
        "title": "結訓專案衝刺與 Demo Day 驗收",
        "subtitle": "整合 Unit 1-7 所有產出，完成一套從登入、客戶管理、AI 對話到 RAG 知識查詢的完整 AI CRM 系統，並在 Demo Day 進行展示驗收。",
        "time": "13:00 ~ 17:00",
        "features": [
          "全端系統整合與除錯",
          "效能調校與上線檢查清單",
          "Demo Day 展示與驗收"
        ],
        "goals": [
          "整合前後端功能，確認登入、客戶管理、AI 對話、RAG 查詢的完整流程可跑通",
          "撰寫端到端測試案例，涵蓋三種典型客戶場景的完整使用路徑",
          "建立上線檢查清單，涵蓋安全性、效能、錯誤處理與監控指標",
          "執行 pnpm run verify 通過完整全端自動化測試與驗收"
        ],
        "principle": "結訓專案的核心目標不是新增功能，而是讓前七個單元的產出真正「串起來」成為一套可展示的產品。重點在於排除整合時的版本衝突、CORS 錯誤、環境差異，並建立可重複執行的驗證流程。",
        "concepts": [
          {
            "heading": "結訓整合的三大挑戰",
            "group": "結訓整合挑戰",
            "body": "把七個單元的產出串成一套可展示的系統，最常遇到三類問題：(1) 環境差異——Docker 容器、JDK 版本、Node.js 版本在不同機器上不一致；(2) 跨層整合——前端 Axios 的 base URL、CORS 設定、JWT token 在 header 的格式，任何一環不對就 401/403；(3) 資料流斷裂——seed data 的客戶 ID 與前端硬編碼的測試 ID 不一致，導致查無資料。解決方式是建立一份「整合檢查清單」，從資料庫、後端、前端逐層驗證。",
            "note": "整合階段的 bug 通常不在邏輯，而在環境與設定。"
          },
          {
            "heading": "端到端測試案例設計",
            "group": "測試策略",
            "body": "結訓專案需要涵蓋三種典型客戶場景，對應 Unit 3 seed data 的三種客戶類型：(1) 高價值活躍客戶（APIM）——登入後查看客戶詳情、觸發 AI 對話、AI 能正確引用 RAG 知識庫回答產品問題；(2) 資料不足客戶——AI 應誠實回答「查無相關資料」而非編造；(3) 流失風險客戶（GlobalMart）——AI 摘要能正確反映互動頻率下降的趨勢。每個場景都要從登入開始，走完完整的使用路徑。"
          },
          {
            "heading": "AI 功能的測試策略",
            "group": "測試策略",
            "body": "AI 功能的測試不能用傳統的「斷言輸出完全相等」方式，因為 LLM 的輸出具有隨機性。正確的策略是分層測試：(1) Java @Tool 方法——用一般單元測試驗證輸入輸出；(2) Prompt 組裝——驗證 system prompt 是否正確帶入客戶資料、是否設定了正確的角色約束；(3) RAG 檢索——驗證 vector store 查詢是否回傳相關文件；(4) 端到端——用 Playwright 模擬使用者操作，驗證 SSE 串流是否正確顯示在前端。",
            "note": "測試 AI 功能的重點是驗證 prompt 的組裝與工具的調用，而非 LLM 的具體回答。"
          },
          {
            "heading": "上線檢查清單與效能調校",
            "group": "上線準備",
            "body": "AI CRM 上線前需要檢查：(1) 安全性——JWT secret 是否已替換為生產金鑰、CORS 白名單是否已收斂、API 是否有 rate limiting；(2) 效能——SSE 連線是否有 timeout 設定、RAG 查詢的 topK 與 similarity threshold 是否合理、資料庫連線池大小是否足夠；(3) 可觀測性——是否有結構化日誌、是否記錄 AI 呼叫的 token 消耗與延遲；(4) 錯誤處理——LLM API 不可用時是否有 fallback、前端是否能優雅顯示錯誤訊息。"
          },
          {
            "heading": "Demo Day 展示準備",
            "group": "Demo Day 展示準備",
            "body": "Demo Day 的展示順序建議：(1) 先展示登入與角色切換，證明 Spring Security + JWT 正常運作；(2) 展示客戶列表與商機管理，證明 JPA + Flyway 的資料層完整；(3) 開啟 AI 助理聊天室，用 SSE 串流展示即時回應；(4) 提問需要 RAG 知識的問題（如退貨政策），展示 AI 引用正確文件回答；(5) 開啟 Swagger UI 展示 OpenAPI 文件。每個步驟預先準備好測試資料，避免現場輸入導致意外。\n\n**CRM 完整流程展示腳本**\n\n建議按以下順序展示 AI CRM 系統：\n1. 登入系統（展示 JWT 認證）→ 進入 Dashboard 查看本月指標\n2. 進入客戶列表 → 使用動態篩選找到 APIM → 查看客戶詳細資訊\n3. 開啟 AI 助理 → 詢問「APIM 最近的合約狀況和商機進度」（展示 Tool Calling）\n4. 詢問「GlobalMart 有流失風險嗎？建議怎麼處理？」（展示 RAG + 歷史記憶）\n5. AI 產生行動建議 → 確認建立待辦任務（展示完整業務閉環）",
            "note": "Demo 的目的是證明系統可用，不是展示所有功能。選擇最能體現整合價值的路徑。"
          }
        ],
        "prompt": "請接續 Unit 7 的 RAG 知識庫與 MCP 實作。現在我們要進入 Unit 8：結訓專案衝刺與 Demo Day 驗收。目標是把 Unit 1-7 的所有產出整合成一套可展示的 AI CRM 系統，並建立端到端測試與上線檢查清單。\n\n要求：\n1. 全端整合驗證：\n   - 確認 Spring Boot 後端、PostgreSQL 資料庫、React 前端三層能正確啟動與串接。\n   - 驗證 JWT 登入流程、客戶 CRUD、AI 對話 SSE 串流、RAG 知識庫查詢的完整路徑。\n   - 排除常見整合問題：CORS 設定、JWT header 格式、seed data ID 一致性。\n2. 端到端測試案例：\n   - 涵蓋三個典型客戶場景，對應 Unit 3 seed data 的三種客戶：\n     (1) 高價值活躍客戶 → 完整走過 AI 對話與 RAG 查詢流程。\n     (2) 資料不足客戶 → AI 應誠實回答「查無資料」而非編造。\n     (3) 流失風險客戶 → AI 摘要能正確反映互動頻率下降趨勢。\n3. 上線檢查清單：\n   - 安全性：JWT secret 替換、CORS 白名單收斂、rate limiting。\n   - 效能：SSE timeout、RAG topK/threshold、連線池大小。\n   - 可觀測性：結構化日誌、AI token 消耗記錄。\n   - 錯誤處理：LLM API fallback、前端錯誤訊息。\n4. Demo Day 展示準備：\n   - 準備展示腳本：登入 → 客戶列表 → AI 對話 → RAG 查詢 → Swagger UI。\n   - 預先準備測試資料，確保現場展示流暢。\n5. 程式碼需要有函式級別註解(註解使用中文)，重要變數或物件也需要加上註解。\n6. 在 PowerShell 7+ (Windows) 或 zsh / bash (macOS) 終端機中執行驗證測試，確認全站功能通過驗收。",
        "promptMac": "請接續 Unit 7 的 RAG 知識庫與 MCP 實作。現在我們要進入 Unit 8：結訓專案衝刺與 Demo Day 驗收。目標是把 Unit 1-7 的所有產出整合成一套可展示的 AI CRM 系統，並建立端到端測試與上線檢查清單。\n\n要求：\n1. 全端整合驗證：\n   - 確認 Spring Boot 後端、PostgreSQL 資料庫、React 前端三層能正確啟動與串接。\n   - 驗證 JWT 登入流程、客戶 CRUD、AI 對話 SSE 串流、RAG 知識庫查詢的完整路徑。\n   - 排除常見整合問題：CORS 設定、JWT header 格式、seed data ID 一致性。\n2. 端到端測試案例：\n   - 涵蓋三個典型客戶場景，對應 Unit 3 seed data 的三種客戶：\n     (1) 高價值活躍客戶 → 完整走過 AI 對話與 RAG 查詢流程。\n     (2) 資料不足客戶 → AI 應誠實回答「查無資料」而非編造。\n     (3) 流失風險客戶 → AI 摘要能正確反映互動頻率下降趨勢。\n3. 上線檢查清單：\n   - 安全性：JWT secret 替換、CORS 白名單收斂、rate limiting。\n   - 效能：SSE timeout、RAG topK/threshold、連線池大小。\n   - 可觀測性：結構化日誌、AI token 消耗記錄。\n   - 錯誤處理：LLM API fallback、前端錯誤訊息。\n4. Demo Day 展示準備：\n   - 準備展示腳本：登入 → 客戶列表 → AI 對話 → RAG 查詢 → Swagger UI。\n   - 預先準備測試資料，確保現場展示流暢。\n5. 程式碼需要有函式級別註解(註解使用中文)，重要變數或物件也需要加上註解。\n6. 在 zsh / bash (macOS) 終端機中執行驗證測試，確認全站功能通過驗收。",
        "prompts": [
          {
            "title": "高級 AI 協作提示詞大師範本（Windows 版）",
            "note": "可直接交給 Codex / Claude Code / Gemini 進行輔助開發",
            "text": "請接續 Unit 7 的 RAG 知識庫與 MCP 實作。現在我們要進入 Unit 8：結訓專案衝刺與 Demo Day 驗收。目標是把 Unit 1-7 的所有產出整合成一套可展示的 AI CRM 系統，並建立端到端測試與上線檢查清單。\n\n要求：\n1. 全端整合驗證：\n   - 確認 Spring Boot 後端、PostgreSQL 資料庫、React 前端三層能正確啟動與串接。\n   - 驗證 JWT 登入流程、客戶 CRUD、AI 對話 SSE 串流、RAG 知識庫查詢的完整路徑。\n2. 端到端測試案例：涵蓋高價值客戶、資料不足客戶、流失風險客戶三個場景。\n3. 上線檢查清單：安全性、效能、可觀測性、錯誤處理。\n4. Demo Day 展示準備：登入 → 客戶列表 → AI 對話 → RAG 查詢 → Swagger UI。\n5. 程式碼需要有函式級別註解(註解使用中文)。"
          }
        ],
        "tasks": [
          {
            "id": "u8-t1",
            "label": "完成前後端整合，確認登入、客戶管理、AI 對話的完整流程可跑通"
          },
          {
            "id": "u8-t2",
            "label": "撰寫三個典型客戶場景的端到端測試案例"
          },
          {
            "id": "u8-t3",
            "label": "建立上線檢查清單並逐項確認"
          },
          {
            "id": "u8-t4",
            "label": "完成 Demo Day 展示腳本並試跑驗收"
          }
        ],
        "materials": [],
        "illustrations": [
          {
            "name": "u8-1.png",
            "kind": "hero",
            "alt": "結訓專案整合與 Demo Day",
            "spec": "全端整合、測試策略與展示驗收"
          },
          {
            "name": "u8-2.png",
            "kind": "diagram",
            "alt": "端到端測試流程",
            "spec": "流程圖：登入 -> 客戶管理 -> AI 對話 -> RAG 查詢 -> 驗收"
          },
          {
            "name": "u8-3-term.png",
            "kind": "term",
            "alt": "結訓專案衝刺與 Demo Day 驗收 專業術語解釋",
            "spec": "E2E Testing / Checklist / Demo Day"
          }
        ]
      }
    ]
  },
  "materials": [
    {
      "id": "mat1",
      "type": "MD",
      "name": "Windows_與_macOS_開發環境安裝指引",
      "desc": "開發工具鏈 (Java 21, Maven, Node.js, pnpm) 與 PowerShell 7+ 的安裝與驗證指南。"
    },
    {
      "id": "mat2",
      "type": "MD",
      "name": "REST_API_命名規範與最佳實踐",
      "desc": "企業級 RESTful API 設計原則、URL 命名、HTTP 方法選用與狀態碼規範。"
    },
    {
      "id": "mat3",
      "type": "MD",
      "name": "Docker_PostgreSQL_與_pgvector_設定說明",
      "desc": "使用 Docker Compose 啟動 PostgreSQL 及向量擴充套件 pgvector 的詳細設定與 Volume 持久化配置。"
    },
    {
      "id": "mat4",
      "type": "MD",
      "name": "pgvector_環境安裝與向量檢索指令說明",
      "desc": "向量資料庫基礎概念、SQL 向量距離計算及 pgvector 索引優化指令。"
    },
    {
      "id": "mat5",
      "type": "MD",
      "name": "JWT_架構與認證流程設計",
      "desc": "Spring Security 整合 JWT 簽發、驗證與 Filter Chain 保護 API 的完整流程架構。"
    },
    {
      "id": "mat6",
      "type": "MD",
      "name": "React_與_Axios_前後端整合說明",
      "desc": "React 19 呼叫後端 API、JWT 自動攜帶與 Axios 攔截器 (Interceptors) 設計說明。"
    },
    {
      "id": "mat7",
      "type": "MD",
      "name": "Spring_AI_與_Tool_Calling_架構說明",
      "desc": "Spring AI ChatClient 抽象層、System Prompt 設計以及結合 Java Reflection 的 Tool Calling 機制。"
    },
    {
      "id": "mat8",
      "type": "MD",
      "name": "Embabel_黑板機制與GOAP動作規劃指南",
      "desc": "（選修附錄）Embabel GOAP 演算法原理與 Blackboard 機制。Embabel 2.0 穩定後可升級為正式單元。"
    }
  ],
  "quiz": [
    {
      "id": "q1",
      "q": "在 Spring Boot 4.0.x 中，為了支援向量資料庫 pgvector 的 vector 欄位，我們在 Docker Compose 中應該使用哪一個映像檔？",
      "options": [
        "postgres:16",
        "pgvector/pgvector:pg16",
        "mysql:8",
        "redis:latest"
      ],
      "answer": 1
    },
    {
      "id": "q2",
      "q": "關於 @Valid 與 @Validated 的敘述，下列何者正確？",
      "options": [
        "@Validated 支援分組驗證，@Valid 是 Jakarta EE 標準，一般巢狀驗證標在欄位上用 @Valid",
        "@Valid 支援分組驗證 (Validation Groups)，@Validated 是 Jakarta EE 標準",
        "兩者完全等價，可以任意互換，沒有任何功能差異",
        "@Valid 只能用在 Service 層，@Validated 只能用在 Controller 層"
      ],
      "answer": 0
    },
    {
      "id": "q3",
      "q": "當 AI 助理需要即時、逐字地回傳生成的回應時，後端應設計何種 HTTP 端點與前端 React 進行整合？",
      "options": [
        "WebSocket",
        "Server-Sent Events (SSE) / stream",
        "GraphQL Subscription",
        "普通的 REST API JSON 回傳"
      ],
      "answer": 1
    },
    {
      "id": "q4",
      "q": "在 Spring AI Tool Calling 中，如果 AI 找不到使用者詢問的資料，下列何種做法最符合「可信任 AI」的安全防線？",
      "options": [
        "讓 AI 隨便生成一筆合理的虛擬數據回覆，以維持良好的對話體驗",
        "在 Java Tool 中直接丟出異常中斷執行，不給模型 any 回傳值",
        "Java Tool 應清晰回傳「查無此客戶資料」，讓系統提示詞約束模型老實回答「找不到資料」，不可自行編造",
        "直接跳過 Tool Calling，讓 LLM 使用其訓練知識庫憑空猜測"
      ],
      "answer": 2
    },
    {
      "id": "q5",
      "q": "在端到端測試中，驗證 AI 功能的正確策略是什麼？",
      "options": [
        "斷言 LLM 的回答字串完全等於預期輸出",
        "驗證 prompt 組裝是否正確帶入客戶資料與角色約束，以及工具是否被正確調用",
        "不需要測試 AI 功能，因為 LLM 輸出具有隨機性"
      ],
      "answer": 1
    },
    {
      "id": "q6",
      "q": "AI CRM 上線前的安全性檢查，下列何者最為關鍵？",
      "options": [
        "確認前端的 CSS 動畫流暢度",
        "確認 JWT secret 已替換為生產金鑰、CORS 白名單已收斂、API 有 rate limiting",
        "確認所有頁面都有深色模式支援"
      ],
      "answer": 1
    }
  ],
  "appendix": {
    "terminology": [
      "版本描述統一使用 Spring AI 2.0.x",
      "工具呼叫作為主詞，首次出現可補充 Tool Calling / Function Calling",
      "pgvector 指 PostgreSQL 擴充套件，VectorStore 指 Spring AI 的抽象層",
      "Embabel 0.4.x 為選修附錄：Java 21 開發之 GOAP + Blackboard 決策框架，待版本穩定後可納入主線"
    ],
    "faq": [
      {
        "q": "教學網站與原本聊天 Demo 是否可以並存？",
        "a": "可以。教學站放在獨立目錄中，聊天 Demo 仍由 Spring Boot 的 http://localhost:8080/ 提供。"
      },
      {
        "q": "為什麼網站採單頁 SPA 配合 React 19？",
        "a": "此專案採用 Vite + React 19 單頁式架構，將資料與渲染分離，便於利用 HSL 動態主題、CSS 漸變折疊與 Playwright 進行 RWD 功能整合測試驗證。"
      },
      {
        "q": "圖片放在哪裡比較安全？",
        "a": "目前都集中在 teaching-site/assets/illustrations，教學站可獨立預覽與部署。"
      }
    ],
    "verification": [
      "先執行 docker-compose up -d 啟動 PostgreSQL / pgvector",
      "載入 .env 中的 API Key 後執行 mvn spring-boot:run",
      "瀏覽 http://localhost:8080 驗證聊天 Demo，再回到教學網站對照章節內容"
    ]
  },
  "units": [
    {
      "id": "u1",
      "title": "開發環境、專案骨架與 AI 協作流程",
      "subtitle": "建立 Windows/macOS 開發環境，搭建 Spring Boot + React Monorepo 專案骨架並進行健康檢查驗證。",
      "time": "09:00 ~ 12:00",
      "features": [
        "驗證 Java / Maven 環境，透過 Spring Initializr 建立課程初始專案，確認可啟動後再進入後續開發。"
      ],
      "goals": [
        "透過 AI 提示詞引導安裝 JDK 21、Maven 3.9+ 與 Git",
        "確認 Java 21 與 Maven 開發環境可正常使用",
        "透過 Spring Initializr 建立課程初始專案",
        "理解 Spring Boot 標準目錄結構與各層職責",
        "了解 AI 協作在開發流程中的適用時機"
      ],
      "principle": "第一章的核心心法在於對齊工具責任。Java 與 Maven 負責執行環境，VS Code 與 AI 助手負責程式碼編輯與解釋，只有一開始把基線對齊，後續開發才不會被無謂的環境問題打斷。",
      "concepts": [
        {
          "heading": "環境準備重點",
          "group": "環境準備重點",
          "body": "第一章不是在講工具清單，而是在建立後續兩天都要依賴的開發基線。只要 Java、Maven、VS Code 與 AI 協作方式一開始沒有對齊，後面所有章節都會被環境問題反覆打斷。\n\n這一章的核心目標，是讓學員知道哪些工具是編輯器責任、哪些是執行環境責任，以及 AI 助手應該介入在哪一種工作。\n\n- VS Code 負責編輯、導覽、除錯與擴充整合\n- Java 與 Maven 負責專案編譯、依賴下載與執行\n- AI 助手適合做解釋、產生樣板、補測試與協助排錯\n- PowerShell 7+ 是本課程預設終端機環境"
        },
        {
          "heading": "請 AI Agent 幫你安裝 JDK 21",
          "group": "開發工具安裝與驗證",
          "body": "JDK（Java Development Kit）是執行與編譯 Java 程式的核心工具，本課程要求 JDK 21（LTS）。Windows 11 內建 `winget` 套件管理員，可以讓 AI Agent 直接下指令完成安裝，全程不需要你開瀏覽器下載。\n\n把下方提示詞貼給你的 AI Agent（例如 VS Code 內的 Claude、Copilot），讓它幫你執行安裝指令、設定環境變數，並驗證結果。\n\n- Windows 11 / 10 已內建 `winget`，無需另外安裝\n- AI Agent 可直接透過 PowerShell 執行 winget 完成安裝\n- 安裝後 AI Agent 可幫你設定 `JAVA_HOME` 與 `Path` 環境變數\n\n**AI Agent 提示詞 — 安裝 JDK 21 (text)**\n```text\n【提示詞 1 — 請 AI Agent 直接安裝】\n我使用 Windows 11，請用 winget 幫我安裝 Eclipse Temurin JDK 21。\n安裝完成後，請幫我：\n1. 用 PowerShell 設定 JAVA_HOME 環境變數（永久生效）\n2. 確認 Path 中已包含 JDK bin 目錄\n3. 執行 java -version 驗證安裝結果\n\n【提示詞 2 — 請 AI Agent 排查問題】\n我執行 java -version 看到以下訊息：\n[貼上你看到的輸出]\n請幫我判斷是否正確，若有問題請直接用 PowerShell 修正 JAVA_HOME 或 Path 設定。\n\n【提示詞 3 — 多版本衝突】\n我電腦上有舊版 JDK，執行 java -version 顯示的不是 21，\n請用 PowerShell 指令幫我切換成 JDK 21 並讓 Maven 也使用同一版本。\n```"
        },
        {
          "heading": "請 AI Agent 幫你安裝 Maven",
          "group": "開發工具安裝與驗證",
          "body": "Maven 是 Java 建置與依賴管理工具，本課程用它下載 Spring Boot 套件、編譯程式碼與啟動應用。版本要求 3.9 以上。\n\nMaven 需要手動設定 PATH，容易出錯。把提示詞給 AI Agent，讓它用 winget 或 PowerShell 腳本一鍵完成安裝與環境設定。\n\n- winget 可直接安裝 Maven，無需手動解壓縮\n- AI Agent 負責設定 `M2_HOME` 與 `Path`，並確認 Maven 讀到正確的 JDK 21\n- 安裝完後用 `mvn -version` 驗證，確認 Java version 欄位顯示 21\n\n**AI Agent 提示詞 — 安裝 Maven 3.9+ (text)**\n```text\n【提示詞 1 — 請 AI Agent 直接安裝】\n我使用 Windows 11，請用 winget 幫我安裝 Apache Maven 最新版。\n安裝完成後，請幫我：\n1. 設定 M2_HOME 與 Path 環境變數（PowerShell，永久生效）\n2. 確認 Maven 使用的 Java 版本是 JDK 21\n3. 執行 mvn -version 顯示完整結果供我確認\n\n【提示詞 2 — 請 AI Agent 排查問題】\n我執行 mvn -version 顯示的 Java version 不是 21，\n請幫我用 PowerShell 找出原因並修正，讓 Maven 指向正確的 JDK 21。\n\n【提示詞 3 — 加速依賴下載】\n我在台灣，Maven 下載依賴速度很慢，\n請幫我修改 Maven 的 settings.xml，加入國內可用的鏡像站設定。\n```"
        },
        {
          "heading": "請 AI Agent 幫你安裝 Git",
          "group": "開發工具安裝與驗證",
          "body": "Git 是版本管理工具，課程中所有範例都以 Git 管理，建議在開始前安裝完成。Windows 上同樣可以透過 winget 讓 AI Agent 直接安裝，免去手動設定安裝選項的麻煩。\n\n安裝完成後還需要設定提交者身份（user.name 與 user.email），這步驟也可以一併請 AI Agent 完成。\n\n- winget 安裝 Git 會自動加入 Path，無需手動設定\n- AI Agent 可在安裝後立即設定 user.name / user.email\n- 可選：請 AI Agent 協助設定 SSH 金鑰以便推送到 GitHub\n\n**AI Agent 提示詞 — 安裝 Git (text)**\n```text\n【提示詞 1 — 請 AI Agent 直接安裝並設定】\n我使用 Windows 11，請用 winget 幫我安裝 Git。\n安裝完成後，請幫我：\n1. 在 PowerShell 中執行 git --version 確認安裝成功\n2. 設定 git config --global user.name 為「你的名字」\n3. 設定 git config --global user.email 為「你的信箱」\n4. 確認設定已存入（執行 git config --list）\n\n【提示詞 2 — 設定 SSH 金鑰（可選）】\n我想把本課程專案推送到 GitHub，\n請幫我用 PowerShell 產生 SSH 金鑰，並告訴我如何把公鑰加入 GitHub 帳號設定。\n```"
        },
        {
          "heading": "VS Code 必要外掛清單",
          "group": "開發工具安裝與驗證",
          "body": "除了安裝 VS Code 本體之外，這門課至少需要把 Java 與 Spring 相關擴充套件裝齊。Lombok 支援在新版 Java 擴充套件中已內建，因此不應再把某個獨立 Lombok 外掛寫成必要安裝。\n\n- Extension Pack for Java：提供 Java 語言支援、除錯、測試與 Maven 專案能力\n- Spring Boot Extension Pack：提供 Spring Boot Tools、Spring Initializr 與設定檔自動補全\n- 若專案使用 Lombok，請確認 Java 語言伺服器已啟用內建 Lombok 支援，避免 `@Data`、`@NoArgsConstructor` 等註解被誤判\n\n**建議安裝的 VS Code 外掛 (text)**\n```text\n1. Extension Pack for Java\n2. Spring Boot Extension Pack\n3. 確認 Java 擴充套件已啟用 Lombok 內建支援（不另外要求獨立外掛）\n```"
        },
        {
          "heading": "PowerShell 驗證命令",
          "group": "開發工具安裝與驗證",
          "body": "在 VS Code 終端機中先確認 Java 與 Maven 版本，這是所有 Spring Boot 實作的起點。\n\n最低需求必須先講清楚，否則學員即使成功執行命令，也不知道自己的版本是否符合課程要求。\n\n- 最低需求：JDK 21\n- 最低需求：Maven 3.9 以上\n- 建議終端機：PowerShell 7+\n- 若 `java -version` 與 `mvn -version` 顯示的版本不符，先處理 PATH / JAVA_HOME，再進入後續章節\n\n**驗證 Java 與 Maven 版本 (powershell)**\n```powershell\njava -version\nmvn -version\n```"
        },
        {
          "heading": "驗證結果範例與判讀方式",
          "group": "開發工具安裝與驗證",
          "body": "學員不只需要知道要執行什麼命令，還需要知道看到什麼輸出才算正確。下面是一組可接受的範例。\n\n重點不是每個字都一樣，而是主要版本要符合：Java 為 21，Maven 為 3.9 以上，且 Maven 使用的 Java Home 也應指向 JDK 21。\n\n- 若 Java version 不是 21，代表課程環境尚未對齊\n- 若 Maven version 低於 3.9，建議先升級再繼續\n- 若 Maven 顯示的 Java version 不是 21，即使 `java -version` 正確，也表示 Maven 還沒吃到正確 JDK\n- 若出現 `java 不是內部或外部命令`，通常是 PATH 或 JAVA_HOME 尚未設定完成\n\n**可接受的輸出範例 (text)**\n```text\nPS D:\\GitHub\\learn-spring> java -version\nopenjdk version \"21.0.7\" 2025-04-15\nOpenJDK Runtime Environment Temurin-21.0.7+6 (build 21.0.7+6)\nOpenJDK 64-Bit Server VM Temurin-21.0.7+6 (build 21.0.7+6, mixed mode)\n\nPS D:\\GitHub\\learn-spring> mvn -version\nApache Maven 3.9.9\nMaven home: C:\\apache-maven-3.9.9\nJava version: 21.0.7, vendor: Eclipse Adoptium, runtime: D:\\java\\jdk-21\nDefault locale: zh_TW, platform encoding: UTF-8\nOS name: \"windows 11\", version: \"10.0\", arch: \"amd64\", family: \"windows\"\n```"
        },
        {
          "heading": "常見錯誤與排查",
          "group": "常見錯誤與排查",
          "body": "- VS Code 終端機與系統終端機顯示不同版本，通常是 PATH 尚未重新載入\n- 安裝完 JDK 後仍顯示舊版 Java，先檢查 `JAVA_HOME` 與 `Path` 順序\n- Maven 可執行但版本過舊，先更新 Maven 再開始實作，避免相依套件解析異常\n- Spring Boot Extension Pack 安裝後沒有補全，先重新開啟 VS Code 或重建 Java Language Server"
        },
        {
          "heading": "透過 Spring Initializr 建立課程專案",
          "group": "建立課程專案",
          "body": "Spring Initializr 是 Spring 官方提供的專案產生器，負責生成標準的 Maven 目錄結構、`pom.xml` 依賴設定與主程式進入點。在 VS Code 中可直接透過命令面板呼叫，不需要離開編輯器。\n\n本課程需要的依賴在這一步就全部選定，後續每一章都會在同一個專案上持續疊加功能。\n\n- 開啟 VS Code 命令面板（Ctrl+Shift+P），輸入 `Spring Initializr: Create a Maven Project`\n- 或直接前往 start.spring.io 在瀏覽器中設定後下載\n- Spring Boot 版本：選擇 4.0.0（課程範例基於此版本）\n- Group：`com.example`，Artifact：`tutorial`，Packaging：Jar，Java：21\n- 依賴選擇：Spring Web、Spring Data JPA、PostgreSQL Driver、Flyway Migration\n\n**Spring Initializr 設定摘要 (text)**\n```text\nProject      : Maven\nLanguage     : Java\nSpring Boot  : 4.0.x\nGroup        : com.example\nArtifact     : tutorial\nPackaging    : Jar\nJava         : 21\n\n依賴 (Dependencies):\n  - Spring Web\n  - Spring Data JPA\n  - PostgreSQL Driver\n  - Flyway Migration\n```"
        },
        {
          "heading": "確認專案結構與首次啟動",
          "group": "建立課程專案",
          "body": "產生後的專案已包含標準目錄結構。在進入任何功能開發之前，先確認可以啟動，是避免後續被環境問題卡住的最重要步驟。\n\n首次啟動時資料庫連線會失敗（因為尚未啟動 Docker），這是預期行為。本步驟只驗證 Spring Boot 主程式可以被 Maven 執行、類別掃描沒有錯誤。\n\n- `src/main/java/com/example/tutorial/` — Java 原始碼根目錄\n- `src/main/resources/application.properties` — 應用程式設定檔\n- `pom.xml` — Maven 依賴與建置設定\n- 可先把 `application.properties` 中的資料庫設定暫時移除或留空，讓主程式不因找不到 DB 而無法啟動\n\n**首次執行驗證 (powershell)**\n```powershell\n# 進入專案目錄\ncd tutorial\n\n# 編譯並確認無語法錯誤\nmvn clean compile\n\n# 正常輸出應包含 BUILD SUCCESS\n```"
        },
        {
          "heading": "AI 協作的適用時機",
          "group": "AI 協作的適用時機",
          "body": "AI 不應該被當成黑箱產碼器，而是協作式工具。在這門課中，最有價值的 AI 協作情境是：理解 Spring 標註的作用、解釋錯誤訊息，以及在你已知道架構目標時讓它產生對應樣板。\n\n注意：讓 AI 直接生成完整模組、不理解就貼上，是最常見的學習阻斷原因。先弄清楚「這一層應該做什麼」，再讓 AI 協助填入細節。\n\n- 選取特定程式碼後再發問，避免上下文過大\n- 要求加上中文函式註解與重要變數說明\n- 對於重構請先指定目標，例如符合 Controller / Service 分層\n- 對於錯誤排查要附上實際訊息與預期行為"
        }
      ],
      "prompt": "歡迎來到 Unit 1：開發環境、專案骨架與 AI 協作流程。在本章中，我們將建立起我們後續整個 AI CRM 專案所依賴的開發環境基線。我們要設定 VS Code，安裝 Java 21、Maven、Git，並建立 backend 與 frontend 的 full-stack 專案骨架。\n\n以下是本章的 AI 協作提示詞大師範本：\n\n### 請 AI Agent 幫你安裝 JDK 21\n【提示詞 1 — 請 AI Agent 直接安裝】\n我使用 Windows 11，請用 winget 幫我安裝 Eclipse Temurin JDK 21。\n安裝完成後，請幫我：\n1. 用 PowerShell 設定 JAVA_HOME 環境變數（永久生效）\n2. 確認 Path 中已包含 JDK bin 目錄\n3. 執行 java -version 驗證安裝結果\n\n【提示詞 2 — 請 AI Agent 排查問題】\n我執行 java -version 看到以下訊息：\n[貼上你看到的輸出]\n請幫我判斷是否正確，若有問題請直接用 PowerShell 修正 JAVA_HOME 或 Path 設定。\n\n【提示詞 3 — 多版本衝突】\n我電腦上有舊版 JDK，執行 java -version 顯示的不是 21，\n請用 PowerShell 指令幫我切換成 JDK 21 並讓 Maven 也使用同一版本。\n\n### 請 AI Agent 幫你安裝 Maven\n【提示詞 1 — 請 AI Agent 直接安裝】\n我使用 Windows 11，請用 winget 幫我安裝 Apache Maven 最新版。\n安裝完成後，請幫我：\n1. 設定 M2_HOME 與 Path 環境變數（PowerShell，永久生效）\n2. 確認 Maven 使用的 Java 版本是 JDK 21\n3. 執行 mvn -version 顯示完整結果供我確認\n\n【提示詞 2 — 請 AI Agent 排查問題】\n我執行 mvn -version 顯示的 Java version 不是 21，\n請幫我用 PowerShell 找出原因並修正，讓 Maven 指向正確的 JDK 21。\n\n【提示詞 3 — 加速依賴下載】\n我在台灣，Maven 下載依賴速度很慢，\n請幫我修改 Maven 的 settings.xml，加入國內可用的鏡像站設定。\n\n### 請 AI Agent 幫你安裝 Git\n【提示詞 1 — 請 AI Agent 直接安裝並設定】\n我使用 Windows 11，請用 winget 幫我安裝 Git。\n安裝完成後，請幫我：\n1. 在 PowerShell 中執行 git --version 確認安裝成功\n2. 設定 git config --global user.name 為「你的名字」\n3. 設定 git config --global user.email 為「你的信箱」\n4. 確認設定已存入（執行 git config --list）\n\n【提示詞 2 — 設定 SSH 金鑰（可選）】\n我想把本課程專案推送到 GitHub，\n請幫我用 PowerShell 產生 SSH 金鑰，並告訴我如何把公鑰加入 GitHub 帳號設定。\n\n",
      "promptMac": "歡迎來到 Unit 1：開發環境、專案骨架與 AI 協作流程。在本章中，我們將建立起我們後續整個 AI CRM 專案所依賴的開發環境基線。我們要設定 VS Code，安裝 Java 21、Maven、Git，並建立 backend 與 frontend 的 full-stack 專案骨架。\n\n以下是本章的 AI 協作提示詞大師範本：\n\n### 請 AI Agent 幫你安裝 JDK 21\n【提示詞 1 — 請 AI Agent 直接安裝】\n我使用 macOS 11，請用 winget 幫我安裝 Eclipse Temurin JDK 21。\n安裝完成後，請幫我：\n1. 用 PowerShell 設定 JAVA_HOME 環境變數（永久生效）\n2. 確認 Path 中已包含 JDK bin 目錄\n3. 執行 java -version 驗證安裝結果\n\n【提示詞 2 — 請 AI Agent 排查問題】\n我執行 java -version 看到以下訊息：\n[貼上你看到的輸出]\n請幫我判斷是否正確，若有問題請直接用 PowerShell 修正 JAVA_HOME 或 Path 設定。\n\n【提示詞 3 — 多版本衝突】\n我電腦上有舊版 JDK，執行 java -version 顯示的不是 21，\n請用 PowerShell 指令幫我切換成 JDK 21 並讓 Maven 也使用同一版本。\n\n### 請 AI Agent 幫你安裝 Maven\n【提示詞 1 — 請 AI Agent 直接安裝】\n我使用 macOS 11，請用 winget 幫我安裝 Apache Maven 最新版。\n安裝完成後，請幫我：\n1. 設定 M2_HOME 與 Path 環境變數（PowerShell，永久生效）\n2. 確認 Maven 使用的 Java 版本是 JDK 21\n3. 執行 mvn -version 顯示完整結果供我確認\n\n【提示詞 2 — 請 AI Agent 排查問題】\n我執行 mvn -version 顯示的 Java version 不是 21，\n請幫我用 PowerShell 找出原因並修正，讓 Maven 指向正確的 JDK 21。\n\n【提示詞 3 — 加速依賴下載】\n我在台灣，Maven 下載依賴速度很慢，\n請幫我修改 Maven 的 settings.xml，加入國內可用的鏡像站設定。\n\n### 請 AI Agent 幫你安裝 Git\n【提示詞 1 — 請 AI Agent 直接安裝並設定】\n我使用 macOS 11，請用 winget 幫我安裝 Git。\n安裝完成後，請幫我：\n1. 在 PowerShell 中執行 git --version 確認安裝成功\n2. 設定 git config --global user.name 為「你的名字」\n3. 設定 git config --global user.email 為「你的信箱」\n4. 確認設定已存入（執行 git config --list）\n\n【提示詞 2 — 設定 SSH 金鑰（可選）】\n我想把本課程專案推送到 GitHub，\n請幫我用 PowerShell 產生 SSH 金鑰，並告訴我如何把公鑰加入 GitHub 帳號設定。\n\n",
      "prompts": [
        {
          "title": "高級 AI 協作提示詞大師範本（Windows 版）",
          "note": "可直接交給 Codex / Gemini 進行輔助開發",
          "text": "歡迎來到 Unit 1：開發環境、專案骨架與 AI 協作流程。在本章中，我們將建立起我們後續整個 AI CRM 專案所依賴的開發環境基線。我們要設定 VS Code，安裝 Java 21、Maven、Git，並建立 backend 與 frontend 的 full-stack 專案骨架。\n\n以下是本章的 AI 協作提示詞大師範本：\n\n### 請 AI Agent 幫你安裝 JDK 21\n【提示詞 1 — 請 AI Agent 直接安裝】\n我使用 Windows 11，請用 winget 幫我安裝 Eclipse Temurin JDK 21。\n安裝完成後，請幫我：\n1. 用 PowerShell 設定 JAVA_HOME 環境變數（永久生效）\n2. 確認 Path 中已包含 JDK bin 目錄\n3. 執行 java -version 驗證安裝結果\n\n【提示詞 2 — 請 AI Agent 排查問題】\n我執行 java -version 看到以下訊息：\n[貼上你看到的輸出]\n請幫我判斷是否正確，若有問題請直接用 PowerShell 修正 JAVA_HOME 或 Path 設定。\n\n【提示詞 3 — 多版本衝突】\n我電腦上有舊版 JDK，執行 java -version 顯示的不是 21，\n請用 PowerShell 指令幫我切換成 JDK 21 並讓 Maven 也使用同一版本。\n\n### 請 AI Agent 幫你安裝 Maven\n【提示詞 1 — 請 AI Agent 直接安裝】\n我使用 Windows 11，請用 winget 幫我安裝 Apache Maven 最新版。\n安裝完成後，請幫我：\n1. 設定 M2_HOME 與 Path 環境變數（PowerShell，永久生效）\n2. 確認 Maven 使用的 Java 版本是 JDK 21\n3. 執行 mvn -version 顯示完整結果供我確認\n\n【提示詞 2 — 請 AI Agent 排查問題】\n我執行 mvn -version 顯示的 Java version 不是 21，\n請幫我用 PowerShell 找出原因並修正，讓 Maven 指向正確的 JDK 21。\n\n【提示詞 3 — 加速依賴下載】\n我在台灣，Maven 下載依賴速度很慢，\n請幫我修改 Maven 的 settings.xml，加入國內可用的鏡像站設定。\n\n### 請 AI Agent 幫你安裝 Git\n【提示詞 1 — 請 AI Agent 直接安裝並設定】\n我使用 Windows 11，請用 winget 幫我安裝 Git。\n安裝完成後，請幫我：\n1. 在 PowerShell 中執行 git --version 確認安裝成功\n2. 設定 git config --global user.name 為「你的名字」\n3. 設定 git config --global user.email 為「你的信箱」\n4. 確認設定已存入（執行 git config --list）\n\n【提示詞 2 — 設定 SSH 金鑰（可選）】\n我想把本課程專案推送到 GitHub，\n請幫我用 PowerShell 產生 SSH 金鑰，並告訴我如何把公鑰加入 GitHub 帳號設定。\n\n"
        }
      ],
      "tasks": [
        {
          "id": "u1-t1",
          "label": "安裝 JDK 21 並完成 JAVA_HOME 設定"
        },
        {
          "id": "u1-t2",
          "label": "安裝 Maven 3.9+ 並完成 Path 設定"
        },
        {
          "id": "u1-t3",
          "label": "安裝 Git 並完成 user.name / user.email 設定"
        },
        {
          "id": "u1-t4",
          "label": "安裝必要 VS Code 外掛並驗證 Java / Maven 版本"
        },
        {
          "id": "u1-t5",
          "label": "透過 Spring Initializr 建立課程專案並確認啟動成功"
        }
      ],
      "materials": [
        {
          "id": "mat1",
          "type": "MD",
          "name": "Windows_與_macOS_開發環境安裝指引",
          "desc": "開發工具鏈 (Java 21, Maven, Node.js, pnpm) 與 PowerShell 7+ 的安裝與驗證指南。"
        }
      ],
      "illustrations": [
        {
          "name": "u1-1.png",
          "kind": "hero",
          "alt": "Full-stack Monorepo",
          "spec": "Windows + Spring Boot + React 啟動骨架"
        },
        {
          "name": "u1-2.png",
          "kind": "diagram",
          "alt": "專案啟動路線",
          "spec": "流程圖：環境檢查 -> 後端骨架 -> 前端骨架 -> 連線驗證"
        },
        {
          "name": "u1-3-term.png",
          "kind": "term",
          "alt": "開發環境、專案骨架與 AI 協作流程 專業術語解釋",
          "spec": "Monorepo / Health Check / PowerShell / Zsh"
        }
      ]
    },
    {
      "id": "u2",
      "title": "Spring MVC、REST API 與 CRM Domain Modeling",
      "subtitle": "設計 CRM 核心 domain model 與 RESTful API，並使用 Bean Validation 進行請求資料驗證。",
      "time": "13:00 ~ 17:00",
      "features": [
        "理解 Spring MVC 的請求流程與 REST API 設計原則，透過 AI Agent 建立一個不依賴資料庫、可立即啟動的客戶 API 示範專案，驗證 Controller / Service 分工正確。"
      ],
      "goals": [
        "理解 Spring MVC 的 DispatcherServlet 請求流程",
        "掌握 REST API 設計原則：URL 是資源名詞，HTTP 方法是動作動詞",
        "用 AI Agent 建立可獨立運行的客戶 REST API（不含資料庫）",
        "理解 Controller / Service 的分工，為後續接入 JPA 做準備"
      ],
      "principle": "分層架構 (DispatcherServlet -> Controller -> Service -> Repository) 是 Spring MVC 的靈魂。Controller 負責轉接與基本驗證，Service 負責商業邏輯，切忌將邏輯混在一起，才能維持程式碼的長期維護性。",
      "concepts": [
        {
          "heading": "Spring Boot 為什麼適合教學起點",
          "group": "Spring Boot 與 MVC 架構",
          "body": "Spring Boot 的核心價值是把大量繁瑣設定折疊起來，讓開發者更快進入業務流程與架構理解。Starter Dependencies 幫你組合常見依賴，自動配置則根據 classpath 與設定推導出合理預設。\n\n教學上最重要的不是背誦 Spring Boot 幫你做了哪些設定，而是理解它讓你省下什麼樣的機械工作。\n\n- Starter 讓依賴以情境為單位引入，例如 web、data-jpa\n- Auto-Configuration 根據環境自動建立常見 Bean\n- 開發者的重點因此可以轉移到模組責任與 API 設計"
        },
        {
          "heading": "Spring MVC 的核心：請求如何流動",
          "group": "Spring Boot 與 MVC 架構",
          "body": "Spring MVC 是 Spring Framework 的 Web 層模組，也是 Spring Boot 預設的 HTTP 請求處理機制。它採用「前端控制器（Front Controller）」設計模式，所有進來的 HTTP 請求都會先經過一個叫做 DispatcherServlet 的統一入口，再由它分發給對應的 Controller 方法。\n\n這個設計的好處是：請求路由、例外處理、內容協商（JSON / XML 格式）等橫切關注點都由框架統一管理，Controller 只需要專心寫業務邏輯，完全不需要自己解析 HTTP 細節。\n\n- 瀏覽器或前端發出 HTTP 請求（例如 GET /api/customers）\n- DispatcherServlet 接收後，根據 URL 與 HTTP Method 找到對應的 @Controller 方法\n- Controller 呼叫 Service 取得資料\n- @RestController 自動把回傳的 Java 物件序列化成 JSON 回應給前端\n- 這整個流程對開發者幾乎是透明的，Spring Boot 自動組態幫你啟動好一切\n\n**Spring MVC 請求流程 (text)**\n```text\nHTTP 請求（例如 GET /api/customers）\n    ↓\nDispatcherServlet（Spring MVC 統一入口）\n    ↓  根據 @GetMapping(\"/api/customers\") 路由\n@RestController 方法\n    ↓  呼叫業務層\n@Service\n    ↓  取得資料（本章示範：Java List，下一章才接資料庫）\n資料來源\n    ↓\nJSON 序列化 → 回傳給前端\n```"
        },
        {
          "heading": "什麼是 REST API",
          "group": "REST API 設計原則",
          "body": "REST（Representational State Transfer）是一種以 HTTP 協定為基礎的 API 設計風格。它的核心思想是：把系統中的每一種資料或功能都當成「資源（Resource）」，並用統一的 URL 路徑表示，再搭配 HTTP 方法（動詞）決定你要對這個資源做什麼事。\n\n以本課程的客戶為例：`/api/customers` 就是「客戶資源」的 URL。你要讀取所有客戶就用 GET，要新增客戶就用 POST，路徑本身不需要改，只有 HTTP 方法不同。這種設計讓 API 的意圖一看就清楚。\n\n- REST 不是協定或規範，而是一套設計風格（Architectural Style）\n- URL 表示「資源是什麼」，HTTP 方法表示「對資源做什麼動作」\n- REST API 是無狀態（Stateless）的——每次請求都要帶齊所有必要資訊，伺服器不記住上次\n- 回傳格式通常為 JSON，因為它輕量、跨語言且容易閱讀\n\n**REST 設計直覺：URL 是名詞，HTTP 方法是動詞 (text)**\n```text\n不好的設計（把動作寫進 URL）：\n  GET /getCustomers\n  POST /createCustomer\n  GET /deleteCustomer?id=1\n\nREST 的設計（URL 是資源，動詞由 HTTP Method 決定）：\n  GET    /api/customers        → 取得所有客戶\n  GET    /api/customers/1      → 取得 ID=1 的客戶\n  POST   /api/customers        → 新增一筆客戶\n  PUT    /api/customers/1      → 更新 ID=1 的客戶\n  DELETE /api/customers/1      → 刪除 ID=1 的客戶\n\n### 銷售漏斗：CRM 的核心流程\n\n在進入實作之前，先理解 CRM 領域最重要的一個概念——銷售漏斗（Sales Funnel）。銷售漏斗是一個視覺化模型，描述客戶從「潛在客戶」到「成交」的完整流程，每一階段都會流失一部分客戶，形狀像一個漏斗。\n\n- **潛在客戶（Lead）**：尚未接觸，但有機會成為客戶的對象\n- **接觸中（Contacted）**：已建立聯繫，正在了解需求\n- **洽談中（Negotiating）**：正在討論合作條件與報價\n- **已成交（Closed Won）**：成功簽約，轉為正式客戶\n- **已流失（Closed Lost）**：未能成交，但仍可追蹤原因\n\n為什麼銷售漏斗對這個課程這麼重要？因為本課程的三家示範客戶（APIM、GlobalMart、ApexFin）正好處於漏斗的不同階段：APIM 是已成交的高價值客戶，GlobalMart 是洽談中但可能流失的風險客戶，ApexFin 是已到期需要緊急聯繫的流失客戶。AI CRM 助理的核心任務之一，就是根據客戶在漏斗中的位置，提供不同的應對策略。\n\n這背後對應的 API 設計就是：\n  GET    /api/opportunities        → 列出所有商機及其漏斗階段\n  GET    /api/opportunities/1      → 查看特定商機的詳情\n  PATCH  /api/opportunities/1/stage → 更新商機階段（例如從 Negotiating 推進到 Closed Won）"
        },
        {
          "heading": "HTTP 方法與 CRUD 對應",
          "group": "REST API 設計原則",
          "body": "REST API 主要用到四種 HTTP 方法，分別對應資料庫的 CRUD（建立、讀取、更新、刪除）操作。理解這個對應關係，後面看 Spring Boot 的 `@GetMapping`、`@PostMapping` 等註解就不會覺得陌生。\n\n- GET 是安全且冪等的——同樣的請求重複發多次，結果一樣、資料不受影響\n- POST 通常不是冪等的——發兩次就會建立兩筆資料\n- PUT / PATCH 是冪等的——重複更新同一份資料，結果相同\n- 本課程以 GET / POST 為主，涵蓋查詢客戶與新增客戶兩個最常見情境\n\n**HTTP 方法對照表 (text)**\n```text\n┌──────────┬──────────┬────────────────────────────────┐\n│ HTTP 方法 │ CRUD 操作 │ 說明                           │\n├──────────┼──────────┼────────────────────────────────┤\n│ GET      │ Read     │ 讀取資料，不修改伺服器狀態     │\n│ POST     │ Create   │ 新增資料，回傳 201 Created      │\n│ PUT      │ Update   │ 整筆更新，需傳入完整資料       │\n│ PATCH    │ Update   │ 部分更新，只傳要修改的欄位     │\n│ DELETE   │ Delete   │ 刪除指定資源                   │\n└──────────┴──────────┴────────────────────────────────┘\n\nSpring Boot 對應註解：\n  @GetMapping    → HTTP GET\n  @PostMapping   → HTTP POST\n  @PutMapping    → HTTP PUT\n  @PatchMapping  → HTTP PATCH\n  @DeleteMapping → HTTP DELETE\n```"
        },
        {
          "heading": "HTTP 狀態碼速查",
          "group": "REST API 設計原則",
          "body": "伺服器每次回應都會帶一個三位數的狀態碼，告訴呼叫方「這次請求的結果是什麼」。Spring Boot 預設已處理大部分狀態碼，但在 Controller 中用 `ResponseEntity` 回傳時，需要明確指定。\n\n- `ResponseEntity.ok(data)` → 200 OK\n- `ResponseEntity.notFound().build()` → 404 Not Found\n- `@ResponseStatus(HttpStatus.CREATED)` 加在 POST 方法上 → 201 Created\n- 未明確設定時，Spring Boot 成功回傳預設為 200，例外預設為 500\n\n**常見 HTTP 狀態碼 (text)**\n```text\n2xx 成功\n  200 OK           → 請求成功，回傳資料（GET / PUT / DELETE）\n  201 Created      → 資源建立成功（POST 新增後回傳）\n  204 No Content   → 成功但不回傳內容（DELETE 常用）\n\n4xx 用戶端錯誤\n  400 Bad Request  → 請求格式錯誤或欄位驗證失敗\n  401 Unauthorized → 未提供或提供了無效的身份憑證\n  403 Forbidden    → 有身份但沒有權限執行此操作\n  404 Not Found    → 找不到指定資源（ID 不存在）\n\n5xx 伺服器錯誤\n  500 Internal Server Error → 伺服器端發生未預期的例外\n```"
        },
        {
          "heading": "請求與回應的結構",
          "group": "REST API 設計原則",
          "body": "了解 HTTP 請求與回應的完整結構，有助於後面除錯時知道要看哪個部分。每次 API 呼叫都包含兩段：你送出的 Request 與伺服器回應的 Response，兩者都有 Header 與 Body。\n\n**REST API 請求與回應範例（新增客戶） (text)**\n```text\n── HTTP Request（前端送出）──────────────────────\nPOST /api/customers HTTP/1.1\nHost: localhost:8080\nContent-Type: application/json    ← Header：告知伺服器 Body 格式\n\n{                                  ← Body：實際資料（JSON）\n  \"name\": \"台積電\",\n  \"level\": \"VIP\",\n  \"email\": \"contact@tsmc.com\",\n  \"notes\": \"半導體龍頭，高價值潛在客戶\",\n  \"status\": \"Active\"\n}\n\n── HTTP Response（Spring Boot 回傳）─────────────\nHTTP/1.1 201 Created              ← 狀態碼\nContent-Type: application/json    ← Header\n\n{                                  ← Body：建立完成的資料（含 ID）\n  \"id\": 7,\n  \"name\": \"台積電\",\n  \"level\": \"VIP\",\n  \"email\": \"contact@tsmc.com\",\n  \"notes\": \"半導體龍頭，高價值潛在客戶\",\n  \"status\": \"Active\"\n}\n```"
        },
        {
          "heading": "IoC 與 DI 的實務解釋",
          "group": "Spring Boot 與 MVC 架構",
          "body": "IoC 是控制反轉，意思是物件建立與生命週期不再由你手動處理，而是交給 Spring 容器。DI 則是容器在執行期把相依物件注入給需要它的類別。\n\n在 Spring Boot 中，最推薦的形式是建構子注入。這樣可以保證依賴在物件建立時就完整，並且更容易撰寫測試。\n\n- Controller 依賴 Service\n- Service 依賴 Repository\n- 各層不自己 `new` 彼此，而是由容器安排"
        },
        {
          "heading": "Controller / Service 怎麼分工",
          "group": "Controller / Service 分層實作",
          "body": "Controller 負責接 HTTP 請求、處理輸入參數與決定回傳格式；Service 負責業務邏輯與資料操作。這個分工讓兩層各司其職，也讓日後更換資料來源（從 List 換成 JPA、從 JPA 換成其他 ORM）時，Controller 完全不需要改動。\n\n本章示範專案刻意只保留 Controller 與 Service 兩層，資料直接放在 Service 的 Java List 裡，讓你先把 Spring MVC 的流程跑通。下一章（1-3、1-4）才會在 Service 下方再加一層 Repository 對接真正的資料庫。\n\n- Controller — 只處理 HTTP 輸入輸出，不含業務判斷\n- Service — 負責業務規則，本章用 List 模擬資料，下一章換成 JPA Repository\n- 兩層分開的好處：換資料來源時只改 Service，Controller 零修改\n\n**本章示範的兩層流向 (text)**\n```text\n[瀏覽器 / 前端]\n    ↓ HTTP 請求\n[Controller]  ← 處理參數與 HTTP 格式\n    ↓ 呼叫業務方法\n[Service]     ← 業務邏輯 + 資料操作\n    ↓\n[List&lt;Customer&gt;]  ← 記憶體資料（本章不用資料庫）\n\n下一章（1-4）的完整三層：\n[Controller] → [Service] → [Repository] → [資料庫]\n```"
        },
        {
          "heading": "用 AI Agent 建立可獨立運行的示範專案",
          "group": "Controller / Service 分層實作",
          "body": "在進入 JPA 與資料庫之前，先請 AI Agent 幫你建立一個可以立即啟動的 Spring MVC 示範專案，確認 Controller → Service → 回應的流程跑通後，再到後面章節接上資料庫。\n\n- 只需要 `spring-boot-starter-web`，不需要 JPA 或 PostgreSQL\n- 啟動後即可用 PowerShell 測試 API 回應\n\n**AI Agent 提示詞 — 建立 Spring MVC 示範專案 (text)**\n```text\n【建立專案】\n我有一個 Spring Boot 專案，只有 spring-boot-starter-web 依賴。\n請幫我建立一個簡單的客戶 REST API（資料存在記憶體，不用資料庫）：\n- GET /api/customers → 回傳全部客戶\n- GET /api/customers/{id} → 找不到回傳 404\n- POST /api/customers → 新增客戶，回傳 201\n請加上中文函式級別註解。\n\n【驗證 API】\n專案啟動後（port 8080），請幫我用 PowerShell Invoke-RestMethod 測試上面三個端點是否正常回應。\n\n【排查錯誤】\n執行 mvn spring-boot:run 出現以下錯誤：\n[貼上錯誤訊息]\n請幫我找出原因並修正。\n```"
        },
        {
          "heading": "Lombok：省去樣板程式碼",
          "group": "Controller / Service 分層實作",
          "body": "Lombok 是 Java 的編譯期程式碼產生器。它透過註解，在編譯時自動為你加上 getter、setter、建構子、`toString`、`equals` 等方法，讓類別定義只保留欄位本身，大幅減少重複樣板。\n\n在 Spring Boot 專案中，只要 `pom.xml` 引入 Lombok 依賴，VS Code 的 Java 擴充套件就會自動識別，不需要額外外掛。\n\n- `@Data` — 等於同時加上 getter、setter、toString、equals、hashCode\n- `@NoArgsConstructor` — 產生無參數建構子（JPA 之後會需要）\n- `@AllArgsConstructor` — 產生包含所有欄位的建構子，方便初始化測試資料\n- `@Builder` — 產生 Builder 模式，適合欄位較多的類別\n\n**Customer.java（使用 Lombok，取代手寫 getter/setter） (java)**\n```java\n// 不用 Lombok：每個欄位都要手寫 getter/setter，程式碼冗長\npublic class Customer {\n    private Long id;\n    private String name;\n    private String level;\n    public Long getId() { return id; }\n    public void setId(Long id) { this.id = id; }\n    // ...繼續寫 name、level 的 getter/setter...\n}\n\n// 使用 Lombok：三個註解搞定一切\n@Data                   // 自動產生 getter / setter / toString / equals\n@NoArgsConstructor      // 無參建構子\n@AllArgsConstructor     // 全欄位建構子\npublic class Customer {\n    private Long id;\n    private String name;\n    private String level;        // VIP / General / New\n    private String email;\n}\n```"
        },
        {
          "heading": "CustomerService（記憶體版）",
          "group": "Controller / Service 分層實作",
          "body": "本章示範的 CustomerService 不依賴 JPA，直接用 Java `List` 模擬資料儲存。注意 Service 對外提供的方法簽章（`getAll`、`findById`、`save`）與下一章接 JPA 版本完全相同——到時候只需要把 List 換成 Repository，Controller 不需要改任何一行。\n\n**CustomerService.java（記憶體版） (java)**\n```java\n@Service\npublic class CustomerService {\n\n    /** 模擬資料庫的記憶體清單，預載 3 筆潛在客戶 */\n    private final List<Customer> customers = new ArrayList<>(List.of(\n        new Customer(1L, \"台積電\",  \"VIP\",  \"contact@tsmc.com\"),\n        new Customer(2L, \"聯發科\",  \"VIP\",  \"contact@mediatek.com\"),\n        new Customer(3L, \"緯創資通\", \"General\", \"contact@wistron.com\")\n    ));\n\n    /** 下一個可用 ID，每次新增後遞增 */\n    private long nextId = 4L;\n\n    /** 取得所有客戶（唯讀視圖，防止外部直接修改清單） */\n    public List<Customer> getAll() {\n        return Collections.unmodifiableList(customers);\n    }\n\n    /** 依 ID 查詢，找不到回傳 Optional.empty() */\n    public Optional<Customer> findById(Long id) {\n        return customers.stream()\n            .filter(c -> c.getId().equals(id))\n            .findFirst();\n    }\n\n    /** 新增客戶：自動指派 ID 後加入清單並回傳完整物件 */\n    public Customer save(Customer customer) {\n        customer.setId(nextId++);\n        customers.add(customer);\n        return customer;\n    }\n}\n```"
        },
        {
          "heading": "CustomerController（記憶體版）",
          "group": "Controller / Service 分層實作",
          "body": "`@RestController` 是 `@Controller` 與 `@ResponseBody` 的組合，所有方法的回傳物件都會自動序列化為 JSON。搭配 `@RequestMapping` 設定根路徑後，各方法再用 `@GetMapping` / `@PostMapping` 區分 HTTP 動詞。\n\n- `@PathVariable` — 從網址路徑取值，例如 `/api/customers/1` 中的 `1`\n- `@RequestBody` — 把請求的 JSON Body 反序列化為 Java 物件\n- `ResponseEntity` — 讓你精確控制 HTTP 狀態碼（200 / 404 / 201）\n\n**CustomerController.java（記憶體版） (java)**\n```java\n@RestController\n@RequestMapping(\"/api/customers\")\npublic class CustomerController {\n\n    /** 建構子注入：Spring 自動把 CustomerService Bean 傳進來 */\n    private final CustomerService customerService;\n\n    public CustomerController(CustomerService customerService) {\n        this.customerService = customerService;\n    }\n\n    /** GET /api/customers — 回傳全部客戶清單 */\n    @GetMapping\n    public List<Customer> getAll() {\n        return customerService.getAll();\n    }\n\n    /** GET /api/customers/{id} — 找到回傳 200，找不到回傳 404 */\n    @GetMapping(\"/{id}\")\n    public ResponseEntity<Customer> getById(@PathVariable Long id) {\n        return customerService.findById(id)\n            .map(ResponseEntity::ok)\n            .orElse(ResponseEntity.notFound().build());\n    }\n\n    /** POST /api/customers — 新增客戶，回傳 201 Created */\n    @PostMapping\n    @ResponseStatus(HttpStatus.CREATED)\n    public Customer create(@RequestBody Customer customer) {\n        return customerService.save(customer);\n    }\n}\n```"
        },
        {
          "heading": "輸入驗證：為什麼不能信任前端傳來的資料",
          "group": "Bean Validation 輸入驗證",
          "body": "目前的 `create` 端點直接把 `@RequestBody` 拿到的物件存進去，前端傳什麼就存什麼。這代表 `name` 傳空字串、`level` 傳不合法的值，全部都會寫入記憶體（或之後的資料庫）而不會報錯。\n\nBean Validation（JSR-380）讓你把驗證規則標在 Model 欄位上，Controller 只需加一個 `@Valid`，Spring 就會在呼叫 Service 之前自動驗證，不合法的請求直接回傳 400，完全不進入業務邏輯。\n\n- 驗證規則標在 Model / DTO 欄位，不散落在 Service 或 Controller 各處\n- 規則跟資料走：不管從哪個 Controller 端點傳入，同一套規則都生效\n- 驗證失敗時 Spring 自動回傳 `400 Bad Request`，並帶上每個欄位的錯誤訊息"
        },
        {
          "heading": "在 Model 加上 Bean Validation 標註",
          "group": "Bean Validation 輸入驗證",
          "body": "把約束條件直接標在 `Customer` 欄位上。標註描述「這個欄位允許什麼值」，Spring 負責在請求進來時執行驗證。\n\n**Customer.java — 加上驗證標註 (java)**\n```java\n@Data\n@NoArgsConstructor\n@AllArgsConstructor\npublic class Customer {\n\n    private Long id;\n\n    @NotBlank(message = \"客戶名稱不可為空\")\n    @Size(max = 100, message = \"名稱最多 100 字\")\n    private String name;\n\n    @NotBlank(message = \"客戶等級不可為空\")\n    @Pattern(regexp = \"VIP|General|New\", message = \"等級必須為 VIP、General 或 New\")\n    private String level;\n\n    @NotBlank(message = \"聯絡 Email 不可為空\")\n    @Email(message = \"請輸入有效的 Email 地址\")\n    private String email;\n\n    @Size(max = 500, message = \"備註最多 500 字\")\n    private String notes;\n\n    @Pattern(regexp = \"Active|Inactive|Lead\", message = \"狀態必須為 Active、Inactive 或 Lead\")\n    private String status;\n}\n```"
        },
        {
          "heading": "在 Controller 加上 @Valid 觸發驗證",
          "group": "Bean Validation 輸入驗證",
          "body": "只需要在 `@RequestBody` 前加 `@Valid`，Spring 就會在解析完請求 Body 後、進入方法邏輯之前執行驗證。驗證失敗時拋出 `MethodArgumentNotValidException`，Spring 自動回傳 400。\n\n**CustomerController.java — create 端點加上 @Valid (java)**\n```java\n// ✅ 加上 @Valid：Spring 驗證 Customer 欄位，不通過直接回 400\n@PostMapping\n@ResponseStatus(HttpStatus.CREATED)\npublic Customer create(@Valid @RequestBody Customer customer) {\n    return customerService.save(customer);\n}\n\n// PUT 修改也一樣需要驗證\n@PutMapping(\"/{id}\")\npublic ResponseEntity<Customer> update(\n        @PathVariable Long id,\n        @Valid @RequestBody Customer customer) {       // ← @Valid 同樣適用\n    return customerService.findById(id)\n        .map(existing -> {\n            customer.setId(existing.getId());\n            return ResponseEntity.ok(customerService.save(customer));\n        })\n        .orElse(ResponseEntity.notFound().build());\n}\n```"
        },
        {
          "heading": "驗證失敗時的回應格式",
          "group": "Bean Validation 輸入驗證",
          "body": "Spring Boot 預設的驗證失敗回應已包含錯誤欄位與訊息，可直接用於前端顯示。以下是傳入空白名稱與負數價格時的實際回應。\n\n**POST /api/customers — 驗證失敗回應（400 Bad Request） (json)**\n```json\n// 請求 Body（不合法）\n{\n  \"name\": \"\",\n  \"level\": \"InvalidLevel\",\n  \"email\": \"not-an-email\"\n}\n\n// Spring 自動回傳的 400 回應\n{\n  \"status\": 400,\n  \"errors\": [\n    {\n      \"field\": \"name\",\n      \"message\": \"客戶名稱不可為空\"\n    },\n    {\n      \"field\": \"level\",\n      \"message\": \"等級必須為 VIP、General 或 New\"\n    },\n    {\n      \"field\": \"email\",\n      \"message\": \"請輸入有效的 Email 地址\"\n    }\n  ]\n}\n```"
        },
        {
          "heading": "常用 Bean Validation 標註速查",
          "group": "Bean Validation 輸入驗證",
          "body": "以下是最常用到的驗證標註，依照驗證對象分組。所有標註都來自 `jakarta.validation.constraints` 套件，引入 `spring-boot-starter-validation` 即可使用。\n\n- **字串類**：`@NotBlank`（非空且非空白）、`@NotEmpty`（非空但可以全空白）、`@Size(min, max)`（長度範圍）、`@Email`（Email 格式）、`@Pattern(regexp)`（正規表示式）\n- **數字類**：`@NotNull`（非 null）、`@Min(value)`（整數最小值）、`@Max(value)`（整數最大值）、`@DecimalMin`（含小數的最小值）、`@DecimalMax`（含小數的最大值）、`@Positive`（必須大於 0）、`@PositiveOrZero`（大於等於 0）\n- **集合類**：`@NotEmpty`（集合不可空）、`@Size(min, max)`（集合元素數量範圍）\n- **巢狀物件**：`@Valid` 標在欄位上 → 對該物件的欄位遞迴驗證（如 List 裡的每個元素）"
        },
        {
          "heading": "AI 提示詞練習",
          "group": "AI 提示詞練習",
          "body": "示範專案跑起來後，試著用以下問題請 AI 助手解釋細節，加深對 Spring MVC 的理解：\n\n- 「`@RestController` 與 `@Controller` 差別是什麼？如果改用 `@Controller`，我需要在哪裡加什麼才能讓回傳值變成 JSON？」\n- 「現在的 CustomerService 用 List 存資料，如果我要換成 HashMap 以加速 ID 查詢，應該怎麼改？請幫我重寫 findById 的邏輯。」\n- 「為什麼 getById 回傳 `ResponseEntity<Customer>` 而不是直接回傳 `Customer`？兩種做法有什麼差別？」\n- 「請幫我在 CustomerController 加一個 DELETE /api/customers/{id} 端點，成功刪除回傳 204，找不到回傳 404。」"
        }
      ],
      "prompt": "請接續 Unit 1 的實作內容。現在我們要進入 Unit 2：Spring MVC、REST API 與 CRM Domain Modeling。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### 用 AI Agent 建立可獨立運行的示範專案\n\n【角色與開發環境設定】\n你是一位精通 Spring Boot 與 enterprise architecture 的 Java 進階開發專家。請在 Windows 10/11 的 PowerShell 7+ 環境下，為我現有的 Spring Boot 專案（已具備 spring-boot-starter-web 依賴，使用 Java 21 與 Maven）建立一個符合生產級別（Production-ready）的客戶管理 REST API 服務。此階段暫不使用資料庫，資料全部存放在記憶體的執行緒安全容器中（如 ConcurrentHashMap）。\n\n【實作功能與架構要求】\n1. **分層架構與職責邊界**：\n   - 建立 `CustomerController`（控制層）：負責請求分派、參數驗證、API 文件標記與 ResponseEntity 回應。\n   - 建立 `CustomerService`（業務邏輯層）：負責核心商業邏輯，內部使用執行緒安全的容器存取資料。\n   - 建立 `Customer` (Entity/POJO) 與對應的 DTO（如 `CustomerRequest` 與 `CustomerResponse`），落實 DTO 與 Domain Object 的防禦性邊界。\n2. **REST API 規格設計**：\n   - `GET /api/customers`：回傳所有客戶的清單列表。\n   - `GET /api/customers/{id}`：查詢指定 ID 的客戶。若 ID 不存在，必須拋出自訂的資源不存在例外，並交由全域例外處理器回傳 404 狀態碼與 JSON 格式的錯誤訊息。\n   - `POST /api/customers`：新增客戶。輸入資料必須包含客戶名稱、產業類別及負責業務。成功新增後，回傳 201 Created 狀態碼並於 Header 中附加資源位置。\n3. **輸入資料驗證 (Bean Validation)**：\n   - 對新增與更新的請求資料，使用 `@NotBlank`、`@Email` 等註解進行嚴格驗證。例如，客戶名稱不能為空，Email 格式必須正確。\n4. **程式碼品質與註解規範**：\n   - 所有類別、介面與方法必須包含「函式級別註解（使用繁體中文）」，說明其輸入參數、回傳值及潛在的例外。\n   - 程式碼需符合 Java 21 的現代寫法（如使用 record 作為 DTO），並保證錯誤處理的健全度。\n\n【驗證與排查引導】\n1. **PowerShell 驗證步驟**：\n   - 專案啟動後（預設 Port 8080），請提供詳細的 PowerShell `Invoke-RestMethod` 指令腳本，分別測試 GET 列表、POST 新增（包含成功與驗證失敗的 JSON payload）、GET 單筆，以及 404 邊界測試。\n2. **錯誤排查流程**：\n   - 當我執行 `mvn spring-boot:run` 出現錯誤時，我會提供 log，請依據 Spring 啟動生命週期排查。",
      "promptMac": "請接續 Unit 1 的實作內容。現在我們要進入 Unit 2：Spring MVC、REST API 與 CRM Domain Modeling。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### 用 AI Agent 建立可獨立運行的示範專案\n\n【角色與開發環境設定】\n你是一位精通 Spring Boot 與 enterprise architecture 的 Java 進階開發專家。請在 macOS 的 zsh / bash 環境下，為我現有的 Spring Boot 專案（已具備 spring-boot-starter-web 依賴，使用 Java 21 與 Maven）建立一個符合生產級別（Production-ready）的客戶管理 REST API 服務。此階段暫不使用資料庫，資料全部存放在記憶體的執行緒安全容器中（如 ConcurrentHashMap）。\n\n【實作功能與架構要求】\n1. **分層架構與職責邊界**：\n   - 建立 `CustomerController`（控制層）：負責請求分派、參數驗證、API 文件標記與 ResponseEntity 回應。\n   - 建立 `CustomerService`（業務邏輯層）：負責核心商業邏輯，內部使用執行緒安全的容器存取資料。\n   - 建立 `Customer` (Entity/POJO) 與對應的 DTO（如 `CustomerRequest` 與 `CustomerResponse`），落實 DTO 與 Domain Object 的防禦性邊界。\n2. **REST API 規格設計**：\n   - `GET /api/customers`：回傳所有客戶的清單列表。\n   - `GET /api/customers/{id}`：查詢指定 ID 的客戶。若 ID 不存在，必須拋出自訂的資源不存在例外，並交由全域例外處理器回傳 404 狀態碼與 JSON 格式的錯誤訊息。\n   - `POST /api/customers`：新增客戶。輸入資料必須包含客戶名稱、產業類別及負責業務。成功新增後，回傳 201 Created 狀態碼並於 Header 中附加資源位置。\n3. **輸入資料驗證 (Bean Validation)**：\n   - 對新增與更新的請求資料，使用 `@NotBlank`、`@Email` 等註解進行嚴格驗證。例如，客戶名稱不能為空，Email 格式必須正確。\n4. **程式碼品質與註解規範**：\n   - 所有類別、介面與方法必須包含「函式級別註解（使用繁體中文）」，說明其輸入參數、回傳值及潛在的例外。\n   - 程式碼需符合 Java 21 的現代寫法（如使用 record 作為 DTO），並保證錯誤處理的健全度。\n\n【驗證與排查引導】\n1. **macOS 驗證步驟**：\n   - 專案啟動後（預設 Port 8080），請提供詳細的 curl 指令腳本，分別測試 GET 列表、POST 新增（包含成功與驗證失敗的 JSON payload）、GET 單筆，以及 404 邊界測試。\n2. **錯誤排查流程**：\n   - 當我執行 `mvn spring-boot:run` 出現錯誤時，我會提供 log，請依據 Spring 啟動生命週期排查。",
      "prompts": [
        {
          "title": "高級 AI 協作提示詞大師範本（Windows 版）",
          "note": "可直接交給 Codex / Gemini 進行輔助開發",
          "text": "請接續 Unit 1 的實作內容。現在我們要進入 Unit 2：Spring MVC、REST API 與 CRM Domain Modeling。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### 用 AI Agent 建立可獨立運行的示範專案\n\n【角色與開發環境設定】\n你是一位精通 Spring Boot 與 enterprise architecture 的 Java 進階開發專家。請在 Windows 10/11 的 PowerShell 7+ 環境下，為我現有的 Spring Boot 專案（已具備 spring-boot-starter-web 依賴，使用 Java 21 與 Maven）建立一個符合生產級別（Production-ready）的客戶管理 REST API 服務。此階段暫不使用資料庫，資料全部存放在記憶體的執行緒安全容器中（如 ConcurrentHashMap）。\n\n【實作功能與架構要求】\n1. **分層架構與職責邊界**：\n   - 建立 `CustomerController`（控制層）：負責請求分派、參數驗證、API 文件標記與 ResponseEntity 回應。\n   - 建立 `CustomerService`（業務邏輯層）：負責核心商業邏輯，內部使用執行緒安全的容器存取資料。\n   - 建立 `Customer` (Entity/POJO) 與對應的 DTO（如 `CustomerRequest` 與 `CustomerResponse`），落實 DTO 與 Domain Object 的防禦性邊界。\n2. **REST API 規格設計**：\n   - `GET /api/customers`：回傳所有客戶的清單列表。\n   - `GET /api/customers/{id}`：查詢指定 ID 的客戶。若 ID 不存在，必須拋出自訂的資源不存在例外，並交由全域例外處理器回傳 404 狀態碼與 JSON 格式的錯誤訊息。\n   - `POST /api/customers`：新增客戶。輸入資料必須包含客戶名稱、產業類別及負責業務。成功新增後，回傳 201 Created 狀態碼並於 Header 中附加資源位置。\n3. **輸入資料驗證 (Bean Validation)**：\n   - 對新增與更新的請求資料，使用 `@NotBlank`、`@Email` 等註解進行嚴格驗證。例如，客戶名稱不能為空，Email 格式必須正確。\n4. **程式碼品質與註解規範**：\n   - 所有類別、介面與方法必須包含「函式級別註解（使用繁體中文）」，說明其輸入參數、回傳值及潛在的例外。\n   - 程式碼需符合 Java 21 的現代寫法（如使用 record 作為 DTO），並保證錯誤處理的健全度。\n\n【驗證與排查引導】\n1. **PowerShell 驗證步驟**：\n   - 專案啟動後（預設 Port 8080），請提供詳細的 PowerShell `Invoke-RestMethod` 指令腳本，分別測試 GET 列表、POST 新增（包含成功與驗證失敗的 JSON payload）、GET 單筆，以及 404 邊界測試。\n2. **錯誤排查流程**：\n   - 當我執行 `mvn spring-boot:run` 出現錯誤時，我會提供 log，請依據 Spring 啟動生命週期排查。"
        },
        {
          "title": "CRM 完整 Domain API — 聯絡人、互動紀錄與商機",
          "note": "補齊 CRM 核心 domain model，為後續 JPA 與 AI 工具呼叫奠定基礎",
          "text": "接續上方的客戶 API，現在要補齊 CRM 系統的核心 domain model。請在現有 Spring Boot 專案中建立以下 API（資料仍存在記憶體 List，不用資料庫）：\n\n### 聯絡人 API（Contact）\n每個客戶可以有多位聯絡人，請建立 ContactController 與 ContactService：\n- POST /api/customers/{customerId}/contacts → 新增聯絡人（姓名、職稱、Email、電話），回傳 201\n- GET /api/customers/{customerId}/contacts → 查詢該客戶的所有聯絡人\n- PUT /api/contacts/{id} → 更新聯絡人資料\n- DELETE /api/contacts/{id} → 刪除聯絡人，成功回傳 204，找不到回傳 404\n\nContact DTO 欄位：name（@NotBlank）、jobTitle、email（@Email）、phone\n\n### 互動紀錄 API（Interaction）\n每個客戶有互動紀錄，記錄電話、會議、Email 等業務接觸，請建立 InteractionController 與 InteractionService：\n- POST /api/customers/{customerId}/interactions → 新增互動紀錄（類型：PHONE / MEETING / EMAIL、摘要、互動日期），回傳 201\n- GET /api/customers/{customerId}/interactions → 查詢該客戶的所有互動紀錄，依日期倒序排列\n- GET /api/interactions/{id} → 查詢單筆互動紀錄詳情\n\nInteraction DTO 欄位：type（@NotNull，列舉 PHONE/MEETING/EMAIL）、summary（@NotBlank）、interactionDate（@NotNull）\n\n### 商機 API（Opportunity）\n商機是 CRM 的銷售核心，記錄每筆銷售機會的進度，請建立 OpportunityController 與 OpportunityService：\n- POST /api/customers/{customerId}/opportunities → 新增商機（名稱、金額、階段、預計成交日），回傳 201\n- GET /api/opportunities → 查詢所有商機，支援依階段篩選（?stage=PROPOSAL）\n- GET /api/customers/{customerId}/opportunities → 查詢該客戶的所有商機\n- PUT /api/opportunities/{id} → 更新商機資料（名稱、金額、階段）\n- PUT /api/opportunities/{id}/stage → 僅更新商機階段（階段推進）\n\nOpportunity DTO 欄位：name（@NotBlank）、amount（@Positive）、stage（@NotNull，列舉 PROSPECTING / QUALIFICATION / PROPOSAL / NEGOTIATION / CLOSED_WON / CLOSED_LOST）、expectedCloseDate\n\n### 共同要求\n1. 每個 Controller 搭配對應的 Service，商業邏輯只放 Service\n2. 所有 Request DTO 使用 Bean Validation（@NotBlank、@NotNull、@Positive、@Email）做輸入驗證，驗證失敗回傳 400 與可讀的中文錯誤訊息\n3. 每個端點加上中文函式級別註解\n4. 在 pom.xml 加入 spring-boot-starter-validation 依賴\n\n### 驗證 API\n全部完成後，請幫我用 PowerShell Invoke-RestMethod 依序測試：\n1. 新增一位客戶「台積電」\n2. 為台積電新增兩位聯絡人（業務經理張三、技術主管李四）\n3. 為台積電新增一筆互動紀錄（類型 MEETING，摘要「討論年度採購方案」）\n4. 為台積電新增一筆商機（名稱「2025年度設備採購」，金額 5000000，階段 PROSPECTING）\n5. 將商機階段推進到 PROPOSAL\n6. 查詢台積電的所有商機，確認階段已更新為 PROPOSAL"
        }
      ],
      "tasks": [
        {
          "id": "u2-t1",
          "label": "閱讀 Spring MVC 請求流程，理解 DispatcherServlet 的角色"
        },
        {
          "id": "u2-t2",
          "label": "用 AI Agent 建立可獨立運行的客戶 REST API 示範專案"
        },
        {
          "id": "u2-t3",
          "label": "用 PowerShell Invoke-RestMethod 驗證 GET / POST 端點可正常回應"
        }
      ],
      "materials": [
        {
          "id": "mat2",
          "type": "MD",
          "name": "REST_API_命名規範與最佳實踐",
          "desc": "企業級 RESTful API 設計原則、URL 命名、HTTP 方法選用與狀態碼規範。"
        }
      ],
      "illustrations": [
        {
          "name": "u2-1.png",
          "kind": "hero",
          "alt": "Spring MVC Domain API",
          "spec": "Controller / Service / DTO 邊界"
        },
        {
          "name": "u2-2.png",
          "kind": "diagram",
          "alt": "MVC 請求生命週期",
          "spec": "流程圖：HTTP Request -> Controller -> Service -> Response DTO"
        },
        {
          "name": "u2-3-term.png",
          "kind": "term",
          "alt": "Spring MVC、REST API 與 CRM Domain Modeling 專業術語解釋",
          "spec": "DispatcherServlet / DTO / Bean Validation"
        }
      ]
    },
    {
      "id": "u3",
      "title": "PostgreSQL、Flyway、JPA 與動態查詢",
      "subtitle": "使用 Docker 建立 PostgreSQL 資料庫，整合 Flyway 版控遷移，並以 Specification 實作多條件動態查詢。",
      "time": "13:00 ~ 17:00",
      "features": [
        "安裝 Docker Desktop，透過 AI Agent 產生 PostgreSQL 18（含 pgvector）的 docker-compose.yml 與 application.yml，啟動容器後用 Flyway 管理 Schema 演進。",
        "使用 Entity、Repository 與 Query Method 將資料表操作提升為物件導向程式碼。"
      ],
      "goals": [
        "安裝 Docker Desktop 並確認可執行容器",
        "用 AI Agent 產生 docker-compose.yml（PostgreSQL 18 + pgvector）",
        "用 AI Agent 設定 application.yml 資料庫連線與 Flyway",
        "掌握 Flyway 版本腳本的命名規則與管理方式",
        "理解 ORM 與 JPA 在專案中的角色",
        "掌握常見 Entity 註解用途",
        "能用 Query Method 表達常見查詢需求"
      ],
      "principle": "持久層的心法在於版本管理與關聯設計。Flyway 保證了資料庫結構的可追溯性，而 JPA Mapping 則需注意延遲載入 (Lazy Load) 與 N+1 查詢問題，動態查詢則透過 Specification 保持代碼優雅與靈活。",
      "concepts": [
        {
          "heading": "為什麼資料庫要容器化",
          "group": "Docker 與 PostgreSQL 容器化",
          "body": "教學專案最怕的是每位學員本機資料庫版本不同、初始化內容不同、安裝方式也不同。Docker 的價值在於把這些差異壓到最低，讓資料庫可以被快速重建與共享。\n\n這個課程後面還需要 `pgvector` 支援向量欄位，因此從一開始就直接用帶有該擴充功能的 PostgreSQL 映像。"
        },
        {
          "heading": "安裝 Docker Desktop",
          "group": "Docker 與 PostgreSQL 容器化",
          "body": "Docker Desktop 是在 Windows 上執行 Docker 容器的必要工具，安裝後只需一行 `docker-compose up -d` 就能啟動整個 PostgreSQL 資料庫環境，不需要手動安裝資料庫。\n\n請前往官方網站下載並安裝：https://www.docker.com/products/docker-desktop\n\n- Docker Desktop 同時安裝 Docker Engine 與 docker-compose\n- Windows 上依賴 WSL2，安裝完成後需重新啟動電腦讓服務生效\n- 安裝後開啟 PowerShell 執行以下指令確認安裝成功：`docker --version` 與 `docker run hello-world`"
        },
        {
          "heading": "Docker 啟動指令",
          "group": "Docker 與 PostgreSQL 容器化",
          "body": "這一章的驗證不應只停在「有執行指令」，而是要確認 PostgreSQL 與 pgvector 容器真的進入可用狀態。\n\n- 最低需求：Docker Desktop 已完成安裝並可正常啟動\n- 最低需求：`docker compose` 或 `docker-compose` 可於 PowerShell 執行\n- 建議在啟動前先確認 5432 埠未被其他本機 PostgreSQL 佔用\n\n**啟動 PostgreSQL / pgvector 容器 (powershell)**\n```powershell\ndocker-compose up -d\n```"
        },
        {
          "heading": "資料庫驗證範例與判讀方式",
          "group": "Docker 與 PostgreSQL 容器化",
          "body": "啟動完容器後，至少要再看一次容器狀態。若只看到指令執行成功，但容器實際上反覆重啟，後續 Flyway 與 JPA 都會失敗。\n\n- `STATUS` 應為 `Up ...`，若是 `Exited` 或持續重啟，代表資料庫尚未可用\n- 映像名稱應對應 `pgvector/pgvector:pg18`，避免版本與文件不一致\n- 若 5432 埠綁定失敗，通常是本機已有其他 PostgreSQL 服務佔用\n\n**可接受的輸出範例 (text)**\n```text\nPS D:\\GitHub\\learn-spring> docker-compose up -d\n[+] Running 1/1\n ✔ Container learn-spring-postgres-1  Started\n\nPS D:\\GitHub\\learn-spring> docker ps\nCONTAINER ID   IMAGE                    STATUS          PORTS                    NAMES\nabc123def456   pgvector/pgvector:pg18   Up 12 seconds   0.0.0.0:5432->5432/tcp   learn-spring-postgres-1\n```"
        },
        {
          "heading": "常見錯誤與排查",
          "group": "常見錯誤與排查",
          "body": "- 容器啟動後立刻結束，先看 `docker logs` 檢查初始化錯誤\n- 若顯示 port already allocated，請關閉本機既有 PostgreSQL 或改用其他埠\n- 若映像拉取失敗，先確認 Docker Desktop 已登入與網路可用\n- 若 Flyway 後續連不到資料庫，先確認 `docker ps` 狀態不是只看 `up -d` 的當下輸出"
        },
        {
          "heading": "Flyway 的角色",
          "group": "Flyway 資料庫版本管理",
          "body": "Flyway 負責管理資料庫 Schema 的歷史。每一個版本腳本都代表一次明確變更，例如建立資料表、加索引、插入測試資料等。\n\n正確的做法不是回頭改舊版本，而是新增下一個版本。這樣整個團隊與教學現場都能用同一條遷移歷史來還原資料庫。\n\n- V1 負責建立所有基礎 Schema 與初始資料（本課程將四張 CRM 資料表與 seed data 集中在 V1）\n- 後續若需要擴充欄位或新增向量表，再建立 V2\n- 應用啟動時 Flyway 會依序檢查並套用缺少的版本"
        },
        {
          "heading": "版本管理思維",
          "group": "Flyway 資料庫版本管理",
          "body": "教學上建議把 Flyway 想成工程紀錄，而不是單純 SQL 檔案。你不是在「改一張資料表」，而是在「新增一次經過紀錄的結構演進」。\n\n這種思維會直接影響後面的 JPA、RAG 與 Demo 驗證，因為所有資料流程都建立在穩定 Schema 上。"
        },
        {
          "heading": "Flyway 命名規則與雙底線陷阱",
          "group": "Flyway 資料庫版本管理",
          "body": "Flyway 的 SQL 腳本必須嚴格遵守命名格式，否則會被直接忽略，造成 Schema 沒有如預期建立，卻也沒有任何錯誤訊息。\n\n- 格式：`V<版本>__<描述>.sql`，版本與描述之間是「雙底線」（兩個 `_`）\n- `V1__init_schema.sql` — 正確：建立所有 CRM 資料表與初始資料\n- `V1_init_schema.sql` — 錯誤：單底線，Flyway 無法辨識\n- 版本號不能重複，且一旦執行就不能修改檔案內容（雜湊值會不符）\n- 需要新增欄位時，建立 V2 而不是修改 V1\n\n**Flyway 腳本命名規範 (text)**\n```text\n格式：  V<Version>__<Description>.sql\n         ↑         ↑↑\n      大寫V       雙底線（最常出錯的地方！）\n\n範例：\n  V1__init_schema.sql        ← 建立所有 CRM 資料表 + seed data（初始化集中在一支）\n  V2__add_vector_table.sql   ← 後續擴充（例如 RAG 向量表）\n```"
        },
        {
          "heading": "用 AI Agent 產生 docker-compose.yml",
          "group": "Docker 與 PostgreSQL 容器化",
          "body": "確認 Docker Desktop 已正常運行後，接著請 AI Agent 幫你在專案根目錄建立 `docker-compose.yml`。本課程使用 PostgreSQL 18 並搭配 pgvector 向量擴充（Day 2 的 RAG 功能依賴它），讓 AI Agent 直接生成並啟動容器，你再對照下方說明理解每個欄位的用途。\n\n**AI Agent 提示詞 — 建立 docker-compose.yml (text)**\n```text\n【提示詞 1 — 請 AI Agent 建立並啟動】\n請在我的 Spring Boot 專案根目錄建立 docker-compose.yml，需求如下：\n- 使用 pgvector/pgvector:pg18 映像（PostgreSQL 18 + pgvector 向量擴充）\n- 資料庫名稱：learn_spring\n- 使用者：postgres，密碼：password\n- 本機 5432 埠對應容器 5432 埠\n- 使用具名卷（named volume）讓資料持久化，容器重建後資料不遺失\n- 每個設定項目加上中文註解\n\n建立完成後請幫我執行 docker-compose up -d，\n再執行 docker ps，確認容器狀態為 Up。\n\n【提示詞 2 — 排查容器啟動失敗】\n我執行 docker-compose up -d 後容器狀態不是 Up，\ndocker logs 顯示：\n[貼上錯誤訊息]\n請幫我找出原因並修正。\n```"
        },
        {
          "heading": "docker-compose.yml 設定說明",
          "group": "Docker 與 PostgreSQL 容器化",
          "body": "本課程的 docker-compose.yml 使用官方提供的 pgvector 映像，內建 PostgreSQL 18 與向量擴充套件，Day 2 的 RAG 功能直接依賴這個映像。以下是完整的設定內容與詳細的欄位用途說明。\n\n- version: '3.8' — 指定 Docker Compose 檔案格式版本，確保與 Docker Engine 的相容性。\n- services — 所有要執行的容器都必須宣告在此標籤下。本專案只有一個名為 postgres 的服務。\n- image — 使用 pgvector/pgvector:pg18 映像檔。因為 Day 2 的 RAG 功能需要向量檢索（pgvector），故必須選用內建該擴充套件的 PostgreSQL。\n- container_name — 容器名稱設定為 spring-postgres。固定名稱可方便後續執行 docker logs spring-postgres 或 docker exec -it spring-postgres bash。\n- ports — 主機與容器的埠號對應（主機埠:容器埠）。將主機 5432 埠映射到容器的 5432 埠，Spring Boot 等外部工具便可直接透過 localhost:5432 連線資料庫。\n- environment — 定義容器的環境變數。對 postgres 映像檔來說，這是初始化資料庫所必須設定的帳號與密碼資訊。\n- volumes — 本機與容器內部目錄的掛載。/var/lib/postgresql 是 PostgreSQL 儲存資料檔案的地方。將其映射到具名卷 postgres_data 後，即使容器重啟或被刪除重建，資料庫中的客戶、使用者等資料不會丟失。\n- restart: always — 代表重啟策略。當 Docker 服務重啟，或該容器因為非預期錯誤而停止時，Docker 守護程序會自動嘗試重啟該容器，確保服務高可用性。\n- volumes: postgres_data: — 聲明一個名為 postgres_data 的具名資料卷（Named Volume），由 Docker 自動在主機上管理其實際存放路徑，免去手動指定主機絕對路徑的麻煩。\n\n**docker-compose.yml 核心設定 (yaml)**\n```yaml\nversion: '3.8'                     # Docker Compose 檔案格式版本\n\nservices:                          # 定義此 Compose 專案要執行的容器服務\n  postgres:                        # 資料庫服務名稱 (自訂)\n    image: pgvector/pgvector:pg18  # 使用包含向量擴充套件的官方 PostgreSQL 18 映像檔\n    container_name: spring-postgres # 指定容器運作時的名稱，方便透過 CLI 進行管理\n    ports:\n      - \"5432:5432\"                # 將主機的 5432 連接埠映射到容器的 5432 連接埠\n    environment:                   # 設定容器內部的環境變數\n      POSTGRES_DB: learn_spring    # 容器啟動時自動建立的資料庫名稱\n      POSTGRES_USER: postgres      # 資料庫超級使用者（Administrator）的帳號\n      POSTGRES_PASSWORD: password  # 資料庫超級使用者帳號對應的密碼\n    volumes:                       # 掛載資料卷，將容器內資料與本機目錄連結\n      - postgres_data:/var/lib/postgresql # 將資料持久化儲存於具名卷，防止容器重建後資料丟失\n    restart: always                # 容器重啟策略：當容器崩潰或 Docker 引擎重啟時，會自動重啟此服務\n\nvolumes:                           # 宣告全域的具名資料卷\n  postgres_data:                   # 定義名為 postgres_data 的具名卷，供 postgres 服務掛載使用\n```"
        },
        {
          "heading": "用 AI Agent 設定 application.yml 資料庫連線",
          "group": "資料庫連線配置",
          "body": "容器啟動成功後，接著請 AI Agent 修改 Spring Boot 的 `application.yml`，加入資料庫連線資訊與 Flyway 設定。設定完成後執行 `mvn spring-boot:run`，在 log 中看到 Flyway 完成遷移的訊息就代表整個資料庫環境已就緒。\n\n**AI Agent 提示詞 — 設定 application.yml (text)**\n```text\n【提示詞 1 — 請 AI Agent 設定資料庫連線】\n請修改我的 Spring Boot 專案的 src/main/resources/application.yml，\n加入以下設定（若已存在請直接修改，不要重複）：\n1. datasource：連線到 localhost:5432/learn_spring，帳號 postgres，密碼 password\n2. flyway：enabled: true，baseline-on-migrate: true，腳本位置 classpath:db/migration\n3. jpa：ddl-auto: validate（由 Flyway 管理 Schema，JPA 只驗證結構）\n每個設定項目請加上中文註解說明用途。\n\n【提示詞 2 — 驗證連線與 Flyway 遷移】\n設定完成後請幫我執行 mvn spring-boot:run，\n確認 log 中出現 Successfully applied N migration(s) 的訊息。\n若出現連線錯誤或 Flyway 失敗，請幫我找出原因並修正。\n```"
        },
        {
          "heading": "application.yml 資料庫連線配置",
          "group": "資料庫連線配置",
          "body": "將 Spring Boot 連線資訊對應到 Docker 容器的設定。`baseline-on-migrate: true` 的用途是：如果資料庫已存在內容，Flyway 以現況為基準開始記錄，不會強制從頭建立。\n\n**application.yml 資料庫設定 (yaml)**\n```yaml\nspring:\n  datasource:\n    url: jdbc:postgresql://localhost:5432/learn_spring\n    username: postgres\n    password: password\n    driver-class-name: org.postgresql.Driver\n  flyway:\n    enabled: true\n    baseline-on-migrate: true  # 資料庫非空時以現況為遷移起點\n  jpa:\n    hibernate:\n      ddl-auto: validate  # 由 Flyway 管理 Schema，JPA 只做驗證\n```"
        },
        {
          "heading": "AI 提示詞練習",
          "group": "AI 提示詞練習",
          "body": "試著向 AI 助手詢問以下問題，驗證你對 Flyway 的理解：\n\n- 「如果我的 Flyway 腳本 V1 已經執行過了，我可以直接在 V1 中修改資料表結構嗎？為什麼？」\n- 「我需要為現有資料表新增一個 category 欄位，請幫我寫一個 V2__add_category_column.sql 遷移檔案，預設值為 GENERAL。」"
        },
        {
          "heading": "JPA 解決了什麼問題",
          "group": "JPA Entity 設計與 Repository",
          "body": "如果每一次存取資料都要手寫 SQL、手動把結果塞回 Java 物件，開發與維護成本會很高。JPA 的價值，是讓你以物件模型思考資料，而不是每次都回到低階映射。\n\n這不表示 SQL 不重要，而是代表常見 CRUD 與查詢可以交給更高階的抽象處理。"
        },
        {
          "heading": "Entity 設計要點",
          "group": "JPA Entity 設計與 Repository",
          "body": "- `@Entity` 表示這個類別要對應資料表\n- `@Id` 與主鍵生成策略決定資料識別方式\n- 欄位型別與 nullable 規則要與資料庫 Schema 一致\n- 註解與欄位命名一旦混亂，後續查詢與維護成本會快速升高"
        },
        {
          "heading": "Product.java Entity 完整範例",
          "group": "JPA Entity 設計與 Repository",
          "body": "這是本課程 Product Entity 的結構，對應到 Flyway V1 建立的 `products` 資料表。注意主鍵策略使用 `IDENTITY`，代表 PostgreSQL 的 SERIAL 自增欄位。\n\n**Product.java (java)**\n```java\n@Data                    // Lombok：自動生成 getter/setter/toString/equals\n@NoArgsConstructor       // Lombok：JPA 要求無參建構子\n@AllArgsConstructor      // Lombok：方便測試與初始化\n@Entity\n@Table(name = \"products\")\npublic class Product {\n\n    @Id\n    @GeneratedValue(strategy = GenerationType.IDENTITY)  // 對應 PostgreSQL SERIAL\n    private Long id;\n\n    @Column(nullable = false)\n    private String name;\n\n    private String description;\n\n    @Column(nullable = false)\n    private Double price;\n\n    private Integer stock;        // 庫存數量\n\n    @Column(name = \"created_at\")\n    private LocalDateTime createdAt;\n}\n```"
        },
        {
          "heading": "Lombok 與 JPA 的搭配注意事項",
          "group": "JPA Entity 設計與 Repository",
          "body": "1-2 章節已介紹過 Lombok 的基本用法。在 JPA Entity 中使用時，有一個額外要求：`@NoArgsConstructor` 是必要的，因為 JPA 在從資料庫讀取資料時，需要先用無參建構子建立物件實例，再逐欄填入資料。\n\nVS Code 中若發現 Lombok 的 `@Data` 等註解出現紅線，請確認 Java 擴充套件已啟用內建 Lombok 支援（不需要另裝獨立外掛）。"
        },
        {
          "heading": "Repository 與 Query Method",
          "group": "JPA Entity 設計與 Repository",
          "body": "`JpaRepository` 提供大量現成的 CRUD 能力，讓你不需要為每個模組都重寫基礎存取程式碼。當查詢需求足夠單純時，甚至可以直接透過方法命名表達條件。\n\n例如 `findByNameContaining` 這類寫法，本身就是查詢語意，Spring Data 會把它翻譯成對應 SQL。\n\n**Query Method 範例 (java)**\n```java\npublic interface CustomerRepository extends JpaRepository<Customer, Long> {\n    List<Customer> findByNameContaining(String keyword);\n}\n```"
        },
        {
          "heading": "Query Method 命名規則分解",
          "group": "JPA Entity 設計與 Repository",
          "body": "Spring Data JPA 會解析介面方法名稱，把它翻譯成 SQL。這個機制讓常見查詢完全不用寫 SQL，只要把條件寫在方法名稱裡。\n\n- `find` — SELECT 操作\n- `By` — WHERE 條件起點\n- `Name` — 對應 `name` 欄位\n- `Containing` — 轉為 LIKE '%value%'\n- `IgnoreCase` — 不區分大小寫（LOWER() 函數）\n\n**CustomerRepository.java (java)**\n```java\n@Repository\npublic interface CustomerRepository extends JpaRepository<Customer, Long> {\n\n    // 翻譯為：SELECT * FROM customers WHERE LOWER(name) LIKE LOWER('%name%')\n    List<Customer> findByNameContainingIgnoreCase(String name);\n\n    // JpaRepository 繼承後立即擁有的方法（不需要自己寫）：\n    // save(entity)        — INSERT 或 UPDATE\n    // findById(id)        — SELECT WHERE id = ?\n    // findAll()           — SELECT * FROM table\n    // deleteById(id)      — DELETE WHERE id = ?\n    // count()             — SELECT COUNT(*)\n}\n```"
        },
        {
          "heading": "@Transactional：寫入操作一定要加",
          "group": "交易管理 (@Transactional)",
          "body": "Repository 幫你省去了 SQL 撰寫，但「資料一致性的保障」不在 Repository 層，而是在 Service 層的交易（Transaction）控制上。\n\n當一個業務操作需要對資料庫做新增、修改或刪除時，必須在 Service 方法上加 `@Transactional`，讓這些動作被包在同一個交易區塊裡——成功就全部提交，失敗就全部回滾，不會出現「存到一半」的殘缺資料。\n\n- `@Transactional` → 寫入操作（INSERT / UPDATE / DELETE），讓 Hibernate 在方法結束後自動 commit，發生例外時自動 rollback\n- `@Transactional(readOnly = true)` → 純查詢操作，告知 Hibernate 跳過 dirty checking、提示資料庫使用唯讀連線，降低效能開銷\n- **不加的後果**：Hibernate Session 可能在方法執行中途失效，寫入操作拋出 `TransactionRequiredException`，且只在特定流程才觸發，難以排查"
        },
        {
          "heading": "@Transactional 標註規範與 Service 完整範例",
          "group": "交易管理 (@Transactional)",
          "body": "以下是本課程 ProductService 的完整寫法，查詢方法用 `readOnly = true`、寫入方法用預設（可讀可寫）。這個模式是 Spring Boot 專案的標準慣例，直接套用即可。\n\n**CustomerService.java — @Transactional 標準用法 (java)**\n```java\n@Service\npublic class CustomerService {\n\n    private final CustomerRepository customerRepository;\n\n    public CustomerService(CustomerRepository customerRepository) {\n        this.customerRepository = customerRepository;\n    }\n\n    // ✅ 純查詢：readOnly = true，Hibernate 跳過髒數據檢查\n    @Transactional(readOnly = true)\n    public List<Customer> getAllCustomers() {\n        return customerRepository.findAll();\n    }\n\n    // ✅ 純查詢\n    @Transactional(readOnly = true)\n    public Optional<Customer> getCustomerById(Long id) {\n        return customerRepository.findById(id);\n    }\n\n    // ✅ 寫入：使用預設 @Transactional，方法結束後自動 commit\n    @Transactional\n    public Customer saveCustomer(Customer customer) {\n        return customerRepository.save(customer);\n    }\n\n    // ✅ 寫入：包含先查後刪，兩個動作在同一個交易內\n    @Transactional\n    public void deleteCustomer(Long id) {\n        customerRepository.deleteById(id);\n    }\n\n    // ❌ 沒有標註：寫入操作在某些情境下會拋出 TransactionRequiredException\n    public Customer unsafeSave(Customer customer) {\n        return customerRepository.save(customer);  // 不穩定，避免這樣寫\n    }\n}\n```"
        },
        {
          "heading": "readOnly = true 對效能的實際影響",
          "group": "交易管理 (@Transactional)",
          "body": "`readOnly = true` 不只是語意宣告，它在底層帶來兩個具體效果，在高流量查詢場景尤其明顯。\n\n- **跳過 dirty checking**：Hibernate 在交易結束前會掃描所有載入的 Entity 是否有變更（dirty checking），`readOnly = true` 直接略過這個掃描，省去 CPU 與記憶體開銷\n- **提示資料庫使用唯讀連線**：部分資料庫連線池（如 HikariCP）與讀寫分離架構會依此將查詢路由到唯讀副本（Read Replica），降低主庫壓力\n- **防止意外寫入**：標記為 `readOnly` 的方法若試圖執行寫入，資料庫驅動或連線池會報錯，形成一道安全護欄"
        },
        {
          "heading": "@Query 改資料必須同時加上 @Modifying",
          "group": "交易管理 (@Transactional)",
          "body": "Repository 的派生方法（如 `save()`、`deleteById()`）已由 Spring Data 內部處理好交易邏輯，直接呼叫就行。但若你用 `@Query` 自行撰寫 JPQL 的 UPDATE 或 DELETE，Spring Data JPA 預設把它當成 SELECT 語句對待，必須額外加上 `@Modifying` 才能正確執行。\n\n缺少 `@Modifying` 時，Spring Data 會在執行時拋出 `InvalidDataAccessApiUsageException`，錯誤訊息是「Executing an update/delete query」，容易讓人誤以為是 SQL 語法問題。\n\n- `@Modifying` — 告知 Spring Data 這個 `@Query` 是寫入操作，不是查詢\n- `@Transactional` — 寫入操作仍需交易包覆，通常加在 Service；若 Repository 方法需要獨立交易可直接加在此\n- 兩個標註缺一不可，順序不影響結果"
        },
        {
          "heading": "@Modifying + @Query 完整範例",
          "group": "交易管理 (@Transactional)",
          "body": "以下示範三種常見場景：批次更新、條件刪除，以及派生刪除方法（不需要 `@Modifying`）的對比。\n\n**CustomerRepository.java — @Modifying 用法 (java)**\n```java\n@Repository\npublic interface CustomerRepository\n        extends JpaRepository<Customer, Long>,\n                JpaSpecificationExecutor<Customer> {\n\n    // ✅ 批次更新：@Modifying + @Transactional 缺一不可\n    //    clearAutomatically = true：更新後清除 Hibernate 一級快取，\n    //    避免同一 Session 後續查詢拿到舊值（幽靈快取問題）\n    @Modifying(clearAutomatically = true)\n    @Transactional\n    @Query(\"UPDATE Customer c SET c.level = :level WHERE c.id = :id\")\n    int updateLevelById(@Param(\"id\") Long id, @Param(\"level\") String level);\n\n    // ✅ 條件刪除：直接對資料庫下 DELETE，效率高，適合批次\n    @Modifying(clearAutomatically = true)\n    @Transactional\n    @Query(\"DELETE FROM Customer c WHERE c.status = 'Inactive'\")\n    int deleteInactiveCustomers();\n\n    // ✅ 派生刪除：Spring Data 先 SELECT 再逐筆 DELETE\n    //    不需要 @Modifying，但交易需由呼叫方（Service）提供\n    //    優點：會觸發 Hibernate 生命週期回呼（@PreRemove 等）\n    void deleteByNameContaining(String name);\n\n    // ❌ 錯誤示範：@Query 寫入但缺少 @Modifying → 執行時拋出例外\n    // @Query(\"UPDATE Customer c SET c.level = :level WHERE c.id = :id\")\n    // int wrongUpdate(@Param(\"id\") Long id, @Param(\"level\") String level);\n}\n```"
        },
        {
          "heading": "clearAutomatically：不加會有幽靈快取問題",
          "group": "交易管理 (@Transactional)",
          "body": "`@Modifying` 的 `clearAutomatically = true` 選項決定是否在執行 DML 後清除 Hibernate 的 **一級快取（first-level cache）**。不清除時，同一個 Session 後續讀到的 Entity 仍是快取中的舊值，即使資料庫已更新。\n\n- `clearAutomatically = true`（建議預設加上）→ DML 執行後清除一級快取，後續查詢從資料庫重新讀取\n- `flushAutomatically = true` → DML 執行前先將 Session 中待寫入的變更 flush 到 DB，確保 DML 看到最新狀態\n- Hibernate `@PreRemove`、`@PostPersist` 等生命週期回呼：`@Modifying` 的 DELETE 不會觸發，派生刪除方法才會\n\n**幽靈快取問題示意 (java)**\n```java\n// 同一個 @Transactional Service 方法中：\n\nProduct p = productRepository.findById(1L).get();  // 載入進一級快取，price = 100\n\n// 執行批次更新（若未設 clearAutomatically = true）\nproductRepository.updatePriceById(1L, new BigDecimal(\"200\"));\n\n// ⚠️ 快取未清除，拿到的仍是 price = 100 的舊物件\nProduct stale = productRepository.findById(1L).get();\nSystem.out.println(stale.getPrice());  // 印出 100，而不是 200\n\n// ✅ clearAutomatically = true → 一級快取被清除，重新從 DB 讀取\n// stale.getPrice() → 200（正確）\n```"
        },
        {
          "heading": "@Modifying 批次刪除 vs 派生刪除的選用時機",
          "group": "交易管理 (@Transactional)",
          "body": "- **需要批次效率**（萬筆以上）→ 用 `@Modifying` + `@Query DELETE`，直接下 SQL，不載入 Entity，效率高\n- **需要觸發生命週期事件**（`@PreRemove`、`@EntityListeners`）→ 用派生刪除方法，Spring Data 先查再逐筆刪，每筆都經過 Hibernate 管理\n- **一般 CRUD**（單筆刪除）→ 用 `deleteById()`，Spring Data 已處理好交易與快取\n- **批次更新**（改特定條件下的欄位）→ 幾乎都用 `@Modifying` + `@Query UPDATE`，派生方法做不到批次更新"
        },
        {
          "heading": "Audit 欄位：自動記錄建立與修改時間",
          "group": "Audit 稽核欄位",
          "body": "正式應用中的資料表幾乎都需要 `created_at`、`updated_at`，用來記錄每筆資料的建立與最後修改時間。如果每個 Entity 都手動在 `save()` 前設定，既容易漏，也會讓業務邏輯摻雜技術細節。\n\nSpring Data JPA 的 **JPA Auditing** 功能可以讓這兩個欄位完全自動填入：建立時寫入 `created_at`，之後每次更新只更新 `updated_at`，開發者不需要寫任何設定程式碼。\n\n- 在啟動類別（或 `@Configuration` 類別）加上 `@EnableJpaAuditing`，啟用整個機制\n- 在 Entity 或共用父類別加上 `@EntityListeners(AuditingEntityListener.class)`，告知 Hibernate 要監聽生命週期事件\n- 用 `@CreatedDate` / `@LastModifiedDate` 標記對應欄位，Spring 自動在寫入前填值\n\n**LearnSpringApplication.java — 啟用 JPA Auditing (java)**\n```java\n@SpringBootApplication\n@EnableJpaAuditing  // 啟用 JPA Auditing，應用程式啟動時生效\npublic class LearnSpringApplication {\n    public static void main(String[] args) {\n        SpringApplication.run(LearnSpringApplication.class, args);\n    }\n}\n```"
        },
        {
          "heading": "BaseAuditEntity：把 Audit 欄位抽成共用父類別",
          "group": "Audit 稽核欄位",
          "body": "多個 Entity 都需要 audit 欄位時，建議抽成 `BaseAuditEntity` 讓所有 Entity 繼承。`@MappedSuperclass` 表示這個類別本身不對應任何資料表，只把欄位定義「繼承」給子類別的資料表。\n\n**BaseAuditEntity.java (java)**\n```java\n@MappedSuperclass\n@EntityListeners(AuditingEntityListener.class)  // 監聽 @PrePersist / @PreUpdate 事件\npublic abstract class BaseAuditEntity {\n\n    // 建立時間：@PrePersist 時由 Spring 自動填入，之後不允許修改\n    @CreatedDate\n    @Column(name = \"created_at\", updatable = false, nullable = false)\n    private LocalDateTime createdAt;\n\n    // 最後修改時間：每次 @PreUpdate 時自動更新\n    @LastModifiedDate\n    @Column(name = \"updated_at\", nullable = false)\n    private LocalDateTime updatedAt;\n\n    public LocalDateTime getCreatedAt() { return createdAt; }\n    public LocalDateTime getUpdatedAt() { return updatedAt; }\n}\n```"
        },
        {
          "heading": "Entity 繼承 BaseAuditEntity",
          "group": "Audit 稽核欄位",
          "body": "Customer 繼承 `BaseAuditEntity` 後，資料表自動多出 `created_at` 與 `updated_at` 兩欄。記得補上對應的 Flyway 遷移腳本，否則 `ddl-auto: validate` 會因欄位不符而啟動失敗。\n\n**Customer.java — 繼承 Audit 父類別 (java)**\n```java\n@Data\n@NoArgsConstructor\n@AllArgsConstructor\n@Entity\n@Table(name = \"customers\")\npublic class Customer extends BaseAuditEntity {\n\n    @Id\n    @GeneratedValue(strategy = GenerationType.IDENTITY)\n    private Long id;\n\n    @Column(nullable = false)\n    private String name;\n\n    @Column(nullable = false)\n    private String level;\n\n    @Column(nullable = false)\n    private String email;\n\n    private String phone;\n\n    private String industry;\n\n    // created_at 與 updated_at 從 BaseAuditEntity 繼承，不需要重複宣告\n}\n```"
        },
        {
          "heading": "Flyway 遷移腳本需包含 Audit 欄位",
          "group": "Audit 稽核欄位",
          "body": "本課程的 V1 初始化腳本已將 `created_at` 與 `updated_at` 包含在所有資料表中，因此不需要額外的遷移腳本來新增 audit 欄位。\n\n如果是在既有專案中後續加入 audit 欄位，才需要補一支新版本的遷移腳本（例如 V2），不可修改已執行過的 V1——Flyway 不允許改動已執行過的腳本。\n\n**建議做法**：初始化時就把 audit 欄位納入資料表定義，避免後續再補遷移腳本。"
        },
        {
          "heading": "建立者與修改者欄位（需搭配 Spring Security，本課程僅說明）",
          "group": "Audit 稽核欄位",
          "body": "除了時間戳記，正式系統還常需要 `created_by`（建立者帳號）與 `updated_by`（最後修改者帳號）。Spring Data JPA 提供對應的 `@CreatedBy` 與 `@LastModifiedBy` 標註，但它們需要額外實作 `AuditorAware<T>` 介面，告知 Spring「目前登入的使用者是誰」。\n\n由於「取得目前使用者」通常需要讀取 Spring Security 的 `SecurityContextHolder`，本課程暫不實作完整的 Security 整合，僅說明設計結構與整合方式。\n\n- `@CreatedBy` → 建立時由 `AuditorAware.getCurrentAuditor()` 填入，之後不允許修改\n- `@LastModifiedBy` → 每次更新時自動更新為目前使用者\n- `AuditorAware<T>` → 由你實作的介面，Spring 在需要使用者資訊時呼叫；`T` 通常是 `String`（帳號名稱）\n- 測試階段可暫時實作為固定回傳 `\"system\"`，接上 Spring Security 後再替換為真實登入帳號"
        },
        {
          "heading": "AuditorAware 結構說明（示意，非本課程完整實作）",
          "group": "Audit 稽核欄位",
          "body": "以下程式碼展示介面結構與未來整合 Spring Security 的方向，目前專案可先用簡化版讓 audit 功能運作，等課程引入身分驗證後再替換為完整版。\n\n**AuditorAware 實作方向 (java)**\n```java\n// ✅ 開發 / 測試階段：先回傳固定值，讓 @CreatedBy 能正常寫入\n@Component\npublic class StaticAuditorAware implements AuditorAware<String> {\n    @Override\n    public Optional<String> getCurrentAuditor() {\n        return Optional.of(\"system\");  // 暫用固定值\n    }\n}\n\n// ✅ 接上 Spring Security 後替換為此版本（需引入 spring-boot-starter-security）\n// @Component\n// public class SecurityAuditorAware implements AuditorAware<String> {\n//     @Override\n//     public Optional<String> getCurrentAuditor() {\n//         return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())\n//             .filter(Authentication::isAuthenticated)\n//             .map(Authentication::getName);\n//     }\n// }\n\n// @EnableJpaAuditing 需要知道用哪個 AuditorAware，加上 auditorAwareRef 指定\n// @SpringBootApplication\n// @EnableJpaAuditing(auditorAwareRef = \"securityAuditorAware\")\n// public class LearnSpringApplication { ... }\n```"
        },
        {
          "heading": "Query Method 的限制：條件一多就爆炸",
          "group": "Specification 動態查詢",
          "body": "Query Method 命名在條件固定時非常好用，但商業搜尋場景通常有多個「可選過濾條件」：使用者可能同時填名稱與價格上限，也可能只填其中一個，甚至全不填。\n\nQuery Method 無法處理「條件可有可無」的動態查詢——你必須為每種組合寫一個方法，或在 Service 層用 if-else 分支呼叫不同查詢，兩種做法維護成本都很高。\n\n**難以維護的 if-else 分支查詢（反例） (java)**\n```java\n// ❌ 反例：每多一個可選條件就要翻倍方法數\npublic List&lt;Customer&gt; search(String name, Double maxPrice, Boolean inStock) {\n    if (name != null && maxPrice != null && inStock != null) {\n        return repo.findByNameContainingAndPriceLessThanAndStockGreaterThan(...);\n    } else if (name != null && maxPrice != null) {\n        return repo.findByNameContainingAndPriceLessThan(...);\n    } else if (name != null) {\n        return repo.findByNameContaining(name);\n    }\n    // ... 還有更多分支\n}\n```"
        },
        {
          "heading": "Specification：動態查詢的正確解法",
          "group": "Specification 動態查詢",
          "body": "`Specification<T>` 是 Spring Data JPA 內建的動態查詢機制，核心概念是把每個查詢條件包裝成一個獨立物件，再自由組合。\n\n每個 Specification 本質上是一個 lambda，簽章為 `(root, query, cb) -> Predicate`：`root` 代表 FROM 的 Entity、`cb`（CriteriaBuilder）是 WHERE 條件的工廠。回傳 `null` 就代表「這個條件不套用」，非常適合可選欄位。\n\n- 不需要修改 SQL 字串，只在 Java 程式碼層組合條件\n- 每個條件獨立封裝，可單獨測試每一個 Predicate\n- `null` 條件自動被 Spring Data 跳過，不會影響查詢語意\n- 透過 `.and()` / `.or()` 自由串接，組合結果仍是一個 Specification 物件"
        },
        {
          "heading": "步驟一：Repository 加入 JpaSpecificationExecutor",
          "group": "Specification 動態查詢",
          "body": "在原本的 `CustomerRepository` 額外繼承 `JpaSpecificationExecutor<Customer>`，這樣就能呼叫 `findAll(Specification<T>)` 等動態查詢方法，原有的 `JpaRepository` 功能完全不受影響。\n\n**CustomerRepository.java (java)**\n```java\n@Repository\npublic interface CustomerRepository\n        extends JpaRepository<Customer, Long>,\n                JpaSpecificationExecutor<Customer> {  // 加入這一行\n\n    // 原有的 Query Method 保留不動\n    List<Customer> findByNameContainingIgnoreCase(String name);\n}\n```"
        },
        {
          "heading": "步驟二：建立 CustomerSpec 條件工廠",
          "group": "Specification 動態查詢",
          "body": "建議把所有 Specification 條件集中在一個 `CustomerSpec` 類別，以靜態方法的形式對外提供。每個方法負責一個欄位的判斷，傳入 `null` 時回傳 `null`（代表不套用此條件）。\n\n**CustomerSpec.java (java)**\n```java\npublic class CustomerSpec {\n\n    // 名稱包含關鍵字（不分大小寫）\n    public static Specification<Customer> nameContains(String name) {\n        return (root, query, cb) ->\n            name == null ? null\n                : cb.like(cb.lower(root.get(\"name\")), \"%\" + name.toLowerCase() + \"%\");\n    }\n\n    // 客戶等級篩選\n    public static Specification<Customer> levelEquals(String level) {\n        return (root, query, cb) ->\n            level == null ? null\n                : cb.equal(root.get(\"level\"), level);\n    }\n\n    // 產業別篩選\n    public static Specification<Customer> industryEquals(String industry) {\n        return (root, query, cb) ->\n            industry == null ? null\n                : cb.equal(root.get(\"industry\"), industry);\n    }\n}\n```"
        },
        {
          "heading": "步驟三：在 Service 動態組合條件",
          "group": "Specification 動態查詢",
          "body": "`Specification.where()` 建立起點，`.and()` / `.or()` 串接條件。條件方法回傳 `null` 時 Spring Data 自動跳過，不會產生多餘的 `WHERE 1=1 AND NULL` 問題。\n\n**CustomerService.java — 動態查詢方法 (java)**\n```java\n@Service\npublic class CustomerService {\n\n    private final CustomerRepository customerRepository;\n\n    public CustomerService(CustomerRepository customerRepository) {\n        this.customerRepository = customerRepository;\n    }\n\n    /**\n     * 動態搜尋客戶：三個條件皆為可選，null 表示不套用\n     */\n    public List<Customer> search(String name, String level, String industry) {\n\n        Specification<Customer> spec = Specification\n            .where(CustomerSpec.nameContains(name))          // null → 跳過\n            .and(CustomerSpec.levelEquals(level))            // null → 跳過\n            .and(CustomerSpec.industryEquals(industry));     // null → 跳過\n\n        return customerRepository.findAll(spec);\n    }\n}\n```"
        },
        {
          "heading": "步驟四：Controller 接收查詢參數",
          "group": "Specification 動態查詢",
          "body": "Controller 只負責把 HTTP 查詢字串轉成 Java 型別，業務邏輯與查詢組合全部留在 Service 與 Spec 層。三個參數都是 optional（不帶就是 null），對應到 Service 中的「不套用此條件」。\n\n**CustomerController.java — 搜尋端點 (java)**\n```java\n@RestController\n@RequestMapping(\"/api/customers\")\npublic class CustomerController {\n\n    private final CustomerService customerService;\n\n    public CustomerController(CustomerService customerService) {\n        this.customerService = customerService;\n    }\n\n    // GET /api/customers/search?name=台積&level=VIP&industry=半導體\n    @GetMapping(\"/search\")\n    public List<Customer> search(\n            @RequestParam(required = false) String name,\n            @RequestParam(required = false) String level,\n            @RequestParam(required = false) String industry) {\n\n        return customerService.search(name, level, industry);\n    }\n}\n```"
        },
        {
          "heading": "Specification 產生的 SQL 實際長什麼樣",
          "group": "Specification 動態查詢",
          "body": "以下是三種不同傳參組合，對應 Specification 實際產生的 SQL 片段，方便你驗證行為是否符合預期。\n\n**動態 WHERE 子句對照 (sql)**\n```sql\n-- 呼叫：search(\"手機\", 15000.0, true)\nSELECT * FROM products\nWHERE LOWER(name) LIKE '%手機%'\n  AND price <= 15000\n  AND stock > 0;\n\n-- 呼叫：search(\"手機\", null, null)  → 只帶 name\nSELECT * FROM products\nWHERE LOWER(name) LIKE '%手機%';\n\n-- 呼叫：search(null, null, null)  → 全部不帶，等同 findAll()\nSELECT * FROM products;\n```"
        },
        {
          "heading": "Specification 與 Query Method 的選用時機",
          "group": "Specification 動態查詢",
          "body": "- **Query Method**：條件固定、不超過 2 個欄位組合 → 命名直觀、無額外程式碼\n- **Specification**：有 1 個以上的可選條件、條件組合數 > 3 → 維護性與可讀性大幅提升\n- **`@Query` JPQL**：需要 GROUP BY、子查詢、特殊函數等 Specification 難以表達的語意\n- 兩者可以共存於同一個 Repository，依查詢複雜度選用不同方式"
        },
        {
          "heading": "ddl-auto 設定與開發階段策略",
          "group": "Flyway 資料庫版本管理",
          "body": "`spring.jpa.hibernate.ddl-auto` 控制 Spring Boot 啟動時 Hibernate 是否自動同步資料庫 Schema。在正式課程專案（已接 Flyway）中設定為 `validate`，但理解各選項的用途對開發流程非常重要。\n\n- `create` — 每次啟動都「刪除舊資料表、重新建立」，資料全部清空，只適合一次性初始測試\n- `create-drop` — 啟動時建立、關閉時刪除，適合跑完即棄的整合測試\n- `update` — 啟動時比對 Entity 與現有 Schema，只做「新增」操作（加欄位、加資料表），**不會刪除或重新命名已有欄位**\n- `validate` — 只驗證 Entity 與資料庫 Schema 是否一致，不做任何修改；不一致就報錯啟動失敗\n- `none` — 完全不處理 Schema，開發者自行管理資料庫結構"
        },
        {
          "heading": "為什麼正式環境不能靠 ddl-auto",
          "group": "Flyway 資料庫版本管理",
          "body": "`update` 模式看起來方便，但在部署到測試機或正式機後有幾個關鍵風險，這也是為什麼本課程選擇 Flyway 的原因。\n\n- **只增不減**：`update` 只會新增欄位與資料表，無法刪除廢棄欄位或安全重新命名欄位，隨時間累積出 Schema 髒污\n- **無法重現**：同一支程式在不同環境（本機、測試機、正式機）可能產生不同的 Schema 狀態，難以追蹤差異\n- **無版本紀錄**：誰在什麼時間做了哪個 Schema 變更？`ddl-auto` 沒有任何記錄，發生問題時難以回溯\n- **無法回滾**：Flyway 支援 Undo 腳本（企業版）或手動反向腳本，`ddl-auto` 沒有任何回滾機制\n- **並發部署風險**：多個 Pod 同時啟動時，`update` 可能觸發競爭條件，Flyway 有分散式鎖保護避免此問題\n\n**application.yml — 不同階段的推薦設定 (yaml)**\n```yaml\nspring:\n  jpa:\n    hibernate:\n      # ✅ 開發初期（本機，尚未決定最終 Schema）\n      ddl-auto: update\n\n      # ✅ 已接 Flyway（測試機、正式機，Schema 由遷移腳本管理）\n      ddl-auto: validate\n\n      # ✅ 想讓 JPA 完全不插手 Schema（完全手動或外部工具管理）\n      ddl-auto: none\n```"
        },
        {
          "heading": "Flyway vs ddl-auto 職責對照",
          "group": "Flyway 資料庫版本管理",
          "body": "Flyway 與 `ddl-auto` 都能管理 Schema，但定位完全不同。本課程選擇讓兩者各司其職：Flyway 負責所有 Schema 演進，JPA 只負責驗證 Entity 與資料庫是否吻合。\n\n**Schema 管理職責分工 (text)**\n```text\n┌─────────────────────────────────────────────────────┐\n│  開發初期（本機驗證）                                 │\n│  ddl-auto: update  → 讓 JPA 自動同步，快速迭代       │\n│  優點：免寫 SQL，欄位改動立即生效                    │\n│  缺點：無版本記錄、無法在其他環境重現相同狀態         │\n├─────────────────────────────────────────────────────┤\n│  測試機 / 正式機（所有共享環境）                     │\n│  ddl-auto: validate + Flyway 管理                    │\n│  優點：每次 Schema 變更都有腳本可追蹤、重現與回滾    │\n│  優點：CI/CD 自動執行遷移，跨環境 Schema 一致        │\n│  優點：Flyway 分散式鎖防止並發啟動的 Schema 競爭    │\n└─────────────────────────────────────────────────────┘\n\n建議時機：Entity 設計穩定後，立即把 ddl-auto: update\n  改為 validate，並用 V1 建立完整的初始化腳本\n```"
        },
        {
          "heading": "用 AI Agent 為既有專案加入 JPA",
          "group": "Specification 動態查詢",
          "body": "1-2 章節已建立一個用 Java List 存放資料的 Spring MVC 專案。本章的目標是讓 AI Agent 幫你把資料來源從記憶體 List 換成真正的 PostgreSQL 資料庫，Controller 完全不需要修改，只改 Customer 類別與 CustomerService。\n\n- pom.xml 加入 JPA 與 PostgreSQL 依賴\n- Customer.java 加上 `@Entity`、`@Table`、`@Id` 等 JPA 註解\n- CustomerService 的 List 換成 CustomerRepository，讓資料寫入資料庫\n\n**AI Agent 提示詞 — 為 Spring MVC 專案加入 JPA (text)**\n```text\n【步驟一：加入依賴】\n我在 1-2 建立了一個 Spring Boot 專案（只有 spring-boot-starter-web），\n請幫我在 pom.xml 加入以下依賴：\n- spring-boot-starter-data-jpa\n- postgresql\n- lombok（若尚未加入）\n\n【步驟二：升級 Customer 類別】\n我目前的 Customer.java 只是普通 POJO，\n請幫我加上 JPA 註解（@Entity、@Table、@Id、@GeneratedValue、@Column），\n並改用 Lombok 的 @Data、@NoArgsConstructor、@AllArgsConstructor 取代手寫 getter/setter。\n\n【步驟三：建立 Repository 並更新 Service】\n請幫我：\n1. 建立 CustomerRepository.java，繼承 JpaRepository<Customer, Long>\n2. 更新 CustomerService.java，把原本的 List<Customer> 換成注入 CustomerRepository，\n   讓 getAll、findById、save 方法改用資料庫操作\n\n完成後請執行 mvn spring-boot:run，確認應用程式能啟動並成功連線資料庫。\n```"
        },
        {
          "heading": "這一章為什麼重要",
          "group": "Specification 動態查詢",
          "body": "Day 2 的工具呼叫其實要建立在穩定的 Service / Repository 流程上。也就是說，AI 不會直接查資料庫，而是會重用你在這一章建立好的後端查詢能力。\n\n因此 JPA 不是孤立的資料庫章節，而是整個 AI 應用可驗證性的基礎。"
        }
      ],
      "prompt": "請接續 Unit 2 的實作內容。現在我們要進入 Unit 3：PostgreSQL、Flyway、JPA 與動態查詢。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### 用 AI Agent 產生 docker-compose.yml\n【提示詞 1 — 請 AI Agent 建立並啟動】\n請在我的 Spring Boot 專案根目錄建立 docker-compose.yml，需求如下：\n- 使用 pgvector/pgvector:pg18 映像（PostgreSQL 18 + pgvector 向量擴充）\n- 資料庫名稱：learn_spring\n- 使用者：postgres，密碼：password\n- 本機 5432 埠對應容器 5432 埠\n- 使用具名卷（named volume）讓資料持久化，容器重建後資料不遺失\n- 每個設定項目加上中文註解\n\n建立完成後請幫我執行 docker-compose up -d，\n再執行 docker ps，確認容器狀態為 Up。\n\n【提示詞 2 — 排查容器啟動失敗】\n我執行 docker-compose up -d 後容器狀態不是 Up，\ndocker logs 顯示：\n[貼上錯誤訊息]\n請幫我找出原因並修正。\n\n### 用 AI Agent 設定 application.yml 資料庫連線\n【提示詞 1 — 請 AI Agent 設定資料庫連線】\n請修改我的 Spring Boot 專案的 src/main/resources/application.yml，\n加入以下設定（若已存在請直接修改，不要重複）：\n1. datasource：連線到 localhost:5432/learn_spring，帳號 postgres，密碼 password\n2. flyway：enabled: true，baseline-on-migrate: true，腳本位置 classpath:db/migration\n3. jpa：ddl-auto: validate（由 Flyway 管理 Schema，JPA 只驗證結構）\n每個設定項目請加上中文註解說明用途。\n\n【提示詞 2 — 驗證連線與 Flyway 遷移】\n設定完成後請幫我執行 mvn spring-boot:run，\n確認 log 中出現 Successfully applied N migration(s) 的訊息。\n若出現連線錯誤或 Flyway 失敗，請幫我找出原因並修正。\n\n### 用 AI Agent 為既有專案加入 JPA\n【步驟一：加入依賴】\n我在 1-2 建立了一個 Spring Boot 專案（只有 spring-boot-starter-web），\n請幫我在 pom.xml 加入以下依賴：\n- spring-boot-starter-data-jpa\n- postgresql\n- lombok（若尚未加入）\n\n【步驟二：升級 Customer 類別】\n我目前的 Customer.java 只是普通 POJO，\n請幫我加上 JPA 註解（@Entity、@Table、@Id、@GeneratedValue、@Column），\n並改用 Lombok 的 @Data、@NoArgsConstructor、@AllArgsConstructor 取代手寫 getter/setter。\n\n【步驟三：建立 Repository 並更新 Service】\n請幫我：\n1. 建立 CustomerRepository.java，繼承 JpaRepository<Customer, Long>\n2. 更新 CustomerService.java，把原本的 List<Customer> 換成注入 CustomerRepository，\n   讓 getAll、findById、save 方法改用資料庫操作\n\n完成後請執行 mvn spring-boot:run，確認應用程式能啟動並成功連線資料庫。\n\n",
      "promptMac": "請接續 Unit 2 的實作內容。現在我們要進入 Unit 3：PostgreSQL、Flyway、JPA 與動態查詢。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### 用 AI Agent 產生 docker-compose.yml\n【提示詞 1 — 請 AI Agent 建立並啟動】\n請在我的 Spring Boot 專案根目錄建立 docker-compose.yml，需求如下：\n- 使用 pgvector/pgvector:pg18 映像（PostgreSQL 18 + pgvector 向量擴充）\n- 資料庫名稱：learn_spring\n- 使用者：postgres，密碼：password\n- 本機 5432 埠對應容器 5432 埠\n- 使用具名卷（named volume）讓資料持久化，容器重建後資料不遺失\n- 每個設定項目加上中文註解\n\n建立完成後請幫我執行 docker-compose up -d，\n再執行 docker ps，確認容器狀態為 Up。\n\n【提示詞 2 — 排查容器啟動失敗】\n我執行 docker-compose up -d 後容器狀態不是 Up，\ndocker logs 顯示：\n[貼上錯誤訊息]\n請幫我找出原因並修正。\n\n### 用 AI Agent 設定 application.yml 資料庫連線\n【提示詞 1 — 請 AI Agent 設定資料庫連線】\n請修改我的 Spring Boot 專案的 src/main/resources/application.yml，\n加入以下設定（若已存在請直接修改，不要重複）：\n1. datasource：連線到 localhost:5432/learn_spring，帳號 postgres，密碼 password\n2. flyway：enabled: true，baseline-on-migrate: true，腳本位置 classpath:db/migration\n3. jpa：ddl-auto: validate（由 Flyway 管理 Schema，JPA 只驗證結構）\n每個設定項目請加上中文註解說明用途。\n\n【提示詞 2 — 驗證連線與 Flyway 遷移】\n設定完成後請幫我執行 mvn spring-boot:run，\n確認 log 中出現 Successfully applied N migration(s) 的訊息。\n若出現連線錯誤或 Flyway 失敗，請幫我找出原因並修正。\n\n### 用 AI Agent 為既有專案加入 JPA\n【步驟一：加入依賴】\n我在 1-2 建立了一個 Spring Boot 專案（只有 spring-boot-starter-web），\n請幫我在 pom.xml 加入以下依賴：\n- spring-boot-starter-data-jpa\n- postgresql\n- lombok（若尚未加入）\n\n【步驟二：升級 Customer 類別】\n我目前的 Customer.java 只是普通 POJO，\n請幫我加上 JPA 註解（@Entity、@Table、@Id、@GeneratedValue、@Column），\n並改用 Lombok 的 @Data、@NoArgsConstructor、@AllArgsConstructor 取代手寫 getter/setter。\n\n【步驟三：建立 Repository 並更新 Service】\n請幫我：\n1. 建立 CustomerRepository.java，繼承 JpaRepository<Customer, Long>\n2. 更新 CustomerService.java，把原本的 List<Customer> 換成注入 CustomerRepository，\n   讓 getAll、findById、save 方法改用資料庫操作\n\n完成後請執行 mvn spring-boot:run，確認應用程式能啟動並成功連線資料庫。\n\n",
      "prompts": [
        {
          "title": "高級 AI 協作提示詞大師範本（Windows 版）",
          "note": "可直接交給 Codex / Gemini 進行輔助開發",
          "text": "請接續 Unit 2 的實作內容。現在我們要進入 Unit 3：PostgreSQL、Flyway、JPA 與動態查詢。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### 用 AI Agent 產生 docker-compose.yml\n【提示詞 1 — 請 AI Agent 建立並啟動】\n請在我的 Spring Boot 專案根目錄建立 docker-compose.yml，需求如下：\n- 使用 pgvector/pgvector:pg18 映像（PostgreSQL 18 + pgvector 向量擴充）\n- 資料庫名稱：learn_spring\n- 使用者：postgres，密碼：password\n- 本機 5432 埠對應容器 5432 埠\n- 使用具名卷（named volume）讓資料持久化，容器重建後資料不遺失\n- 每個設定項目加上中文註解\n\n建立完成後請幫我執行 docker-compose up -d，\n再執行 docker ps，確認容器狀態為 Up。\n\n【提示詞 2 — 排查容器啟動失敗】\n我執行 docker-compose up -d 後容器狀態不是 Up，\ndocker logs 顯示：\n[貼上錯誤訊息]\n請幫我找出原因並修正。\n\n### 用 AI Agent 設定 application.yml 資料庫連線\n【提示詞 1 — 請 AI Agent 設定資料庫連線】\n請修改我的 Spring Boot 專案的 src/main/resources/application.yml，\n加入以下設定（若已存在請直接修改，不要重複）：\n1. datasource：連線到 localhost:5432/learn_spring，帳號 postgres，密碼 password\n2. flyway：enabled: true，baseline-on-migrate: true，腳本位置 classpath:db/migration\n3. jpa：ddl-auto: validate（由 Flyway 管理 Schema，JPA 只驗證結構）\n每個設定項目請加上中文註解說明用途。\n\n【提示詞 2 — 驗證連線與 Flyway 遷移】\n設定完成後請幫我執行 mvn spring-boot:run，\n確認 log 中出現 Successfully applied N migration(s) 的訊息。\n若出現連線錯誤或 Flyway 失敗，請幫我找出原因並修正。\n\n### 用 AI Agent 為既有專案加入 JPA\n【步驟一：加入依賴】\n我在 1-2 建立了一個 Spring Boot 專案（只有 spring-boot-starter-web），\n請幫我在 pom.xml 加入以下依賴：\n- spring-boot-starter-data-jpa\n- postgresql\n- lombok（若尚未加入）\n\n【步驟二：升級 Customer 類別】\n我目前的 Customer.java 只是普通 POJO，\n請幫我加上 JPA 註解（@Entity、@Table、@Id、@GeneratedValue、@Column），\n並改用 Lombok 的 @Data、@NoArgsConstructor、@AllArgsConstructor 取代手寫 getter/setter。\n\n【步驟三：建立 Repository 並更新 Service】\n請幫我：\n1. 建立 CustomerRepository.java，繼承 JpaRepository<Customer, Long>\n2. 更新 CustomerService.java，把原本的 List<Customer> 換成注入 CustomerRepository，\n   讓 getAll、findById、save 方法改用資料庫操作\n\n完成後請執行 mvn spring-boot:run，確認應用程式能啟動並成功連線資料庫。\n\n"
        },
        {
          "title": "Flyway Migration — 建立 CRM 四張核心資料表",
          "note": "用一支 V1 建立完整 DDL 與初始 seed data",
          "text": "接續上方的 JPA 設定，現在要用 Flyway 建立 CRM 系統所需的四張核心資料表。\n\n### 產生 Flyway Migration 腳本\n請在 src/main/resources/db/migration/ 目錄下，建立一支 V1__init_schema.sql，包含以下所有資料表與初始資料：\n\n【V1__init_schema.sql — 建立所有 CRM 資料表與 seed data】\n\n1. customers 資料表：\n- id BIGSERIAL PRIMARY KEY\n- name VARCHAR(100) NOT NULL（公司名稱）\n- industry VARCHAR(50)（產業別，例如 SEMICONDUCTOR、ELECTRONICS、FINANCE）\n- tier VARCHAR(20) DEFAULT 'NORMAL'（客戶分級：VIP / NORMAL / INACTIVE）\n- owner VARCHAR(50)（負責業務）\n- email VARCHAR(100)\n- phone VARCHAR(30)\n- created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n- updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n\n2. contacts 資料表：\n- id BIGSERIAL PRIMARY KEY\n- customer_id BIGINT NOT NULL REFERENCES customers(id)\n- name VARCHAR(50) NOT NULL\n- job_title VARCHAR(50)\n- email VARCHAR(100)\n- phone VARCHAR(30)\n- created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n\n3. interactions 資料表：\n- id BIGSERIAL PRIMARY KEY\n- customer_id BIGINT NOT NULL REFERENCES customers(id)\n- type VARCHAR(20) NOT NULL（PHONE / MEETING / EMAIL）\n- summary TEXT NOT NULL\n- interaction_date DATE NOT NULL\n- created_by VARCHAR(50)\n- created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n\n4. opportunities 資料表：\n- id BIGSERIAL PRIMARY KEY\n- customer_id BIGINT NOT NULL REFERENCES customers(id)\n- name VARCHAR(200) NOT NULL（商機名稱）\n- amount DECIMAL(15,2)（預計金額）\n- stage VARCHAR(30) NOT NULL DEFAULT 'PROSPECTING'（PROSPECTING / QUALIFICATION / PROPOSAL / NEGOTIATION / CLOSED_WON / CLOSED_LOST）\n- expected_close_date DATE\n- created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n- updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n\n5. 插入初始測試資料（seed data，寫在同一支 V1 最後面）：\n- 3 位客戶：台積電（VIP, SEMICONDUCTOR）、聯發科（VIP, SEMICONDUCTOR）、國泰金控（NORMAL, FINANCE）\n- 每位客戶各 1-2 位聯絡人\n- 每位客戶各 1-2 筆互動紀錄\n- 台積電 2 筆商機（一筆 PROPOSAL 階段，一筆 NEGOTIATION 階段）、聯發科 1 筆商機\n\nSQL 檔案開頭加上中文註解說明此次 migration 的目的。\n完成後請執行 mvn spring-boot:run，確認 log 中出現 Successfully applied 1 migration(s)。"
        },
        {
          "title": "JPA Entity 升級與 Specification 動態查詢",
          "note": "將所有 domain model 接入 JPA，並建立可組合條件的搜尋 API",
          "text": "接續 Flyway migration 完成後，現在要把所有 domain model 從記憶體換成 JPA，並加入動態查詢能力。\n\n### 步驟一：建立完整 JPA Entity\n請將 Unit 2 建立的 Contact、Interaction、Opportunity POJO 升級為 JPA Entity：\n1. 每個 Entity 加上 @Entity、@Table、@Id、@GeneratedValue(strategy = GenerationType.IDENTITY)\n2. 使用 Lombok @Data、@NoArgsConstructor、@AllArgsConstructor\n3. Customer 與 Contact 為 @OneToMany / @ManyToOne 雙向關聯\n4. Customer 與 Interaction 為 @OneToMany / @ManyToOne 雙向關聯\n5. Customer 與 Opportunity 為 @OneToMany / @ManyToOne 雙向關聯\n6. 所有 Entity 包含 createdAt（@CreationTimestamp）、updatedAt（@UpdateTimestamp）audit 欄位\n7. @JsonIgnore 避免雙向序列化無限迴圈\n\n### 步驟二：建立 Repository\n為每個 Entity 建立 Repository：\n- ContactRepository extends JpaRepository<Contact, Long>，加入 findByCustomerId 方法\n- InteractionRepository extends JpaRepository<Interaction, Long>，加入 findByCustomerIdOrderByInteractionDateDesc 方法\n- OpportunityRepository extends JpaRepository<Opportunity, Long>，加入 findByCustomerId 與 findByStage 方法\n\n### 步驟三：更新 Service 為 JPA 版本\n將所有 Service 的記憶體 List 操作改為注入 Repository，使用 @Transactional 標註寫入方法。\n\n### 步驟四：建立客戶搜尋 Specification\n建立 CustomerSpecification 類別，支援以下條件的動態組合：\n- 依產業別（industry）篩選\n- 依客戶分級（tier）篩選\n- 依負責業務（owner）篩選\n- 依公司名稱模糊搜尋（name LIKE %keyword%）\n- 依最近互動時間（最近 N 天內有互動紀錄的客戶）\n\n在 CustomerController 新增搜尋端點：\nGET /api/customers/search?industry=SEMICONDUCTOR&tier=VIP&owner=張三&keyword=台積&recentDays=30\n所有參數皆為選填，可自由組合。\n\n### 驗證\n1. 啟動後確認 seed data 已存在：GET /api/customers 應回傳 3 筆\n2. 搜尋 industry=SEMICONDUCTOR 應回傳台積電與聯發科\n3. 搜尋 tier=VIP&keyword=台積 應只回傳台積電\n4. 重啟應用後資料仍存在（確認使用資料庫而非記憶體）"
        }
      ],
      "tasks": [
        {
          "id": "u3-t1",
          "label": "用 AI Agent 安裝 Docker Desktop 並執行 docker run hello-world 驗證"
        },
        {
          "id": "u3-t2",
          "label": "用 AI Agent 建立 docker-compose.yml 並啟動 PostgreSQL 18 容器"
        },
        {
          "id": "u3-t3",
          "label": "用 AI Agent 設定 application.yml，執行 mvn spring-boot:run 確認 Flyway 遷移成功"
        },
        {
          "id": "u3-t4",
          "label": "用 AI Agent 為 1-2 專案加入 JPA 依賴並更新 Product.java 為 Entity"
        },
        {
          "id": "u3-t5",
          "label": "用 AI Agent 建立 CustomerRepository 並更新 CustomerService 改用資料庫"
        },
        {
          "id": "u3-t6",
          "label": "執行 mvn spring-boot:run，確認 API 仍正常運作且資料已寫入資料庫"
        }
      ],
      "materials": [
        {
          "id": "mat3",
          "type": "MD",
          "name": "Docker_PostgreSQL_與_pgvector_設定說明",
          "desc": "使用 Docker Compose 啟動 PostgreSQL 及向量擴充套件 pgvector 的詳細設定與 Volume 持久化配置。"
        }
      ],
      "illustrations": [
        {
          "name": "u3-1.png",
          "kind": "hero",
          "alt": "PostgreSQL + JPA",
          "spec": "Flyway migration 與動態查詢"
        },
        {
          "name": "u3-2.png",
          "kind": "diagram",
          "alt": "資料持久化與搜尋",
          "spec": "流程圖：Migration -> Entity Mapping -> Repository -> Specification"
        },
        {
          "name": "u3-3-term.png",
          "kind": "term",
          "alt": "PostgreSQL、Flyway、JPA 與動態查詢 專業術語解釋",
          "spec": "Flyway / JPA Entity / Specification"
        }
      ]
    },
    {
      "id": "u4",
      "title": "Spring Security、JWT、OpenAPI 與企業級錯誤處理",
      "subtitle": "整合 JWT 簽發與 Security 認證保護 API，建立 ProblemDetail 全域錯誤處理，並以 Swagger 導出 API 文件。",
      "time": "09:00 ~ 12:00",
      "features": [
        "透過 springdoc-openapi 自動產生互動式 API 文件，讓前端與測試人員不需要看程式碼就能理解與呼叫 API。",
        "用 @RestControllerAdvice 統一攔截應用程式例外，回傳格式一致的錯誤回應，讓前端不再猜測錯誤格式。",
        "善用 @Slf4j 建立有語意的結構化日誌，透過 application.yml 設定 Log層級，再用 Spring Actuator 在不重啟應用的情況下動態調整。"
      ],
      "goals": [
        "理解 OpenAPI 規範與 Swagger UI 的關係",
        "加入 springdoc-openapi 並確認 Swagger UI 可正常存取",
        "用標註豐富 Controller 的 API 說明",
        "設定全域 API 資訊（標題、版本、聯絡方式）",
        "理解沒有全域例外處理的系統有什麼問題",
        "建立統一的 ErrorResponse 回應格式",
        "用 @RestControllerAdvice 集中處理各類例外",
        "自訂業務例外類別，讓錯誤語意更清楚",
        "理解 Spring Boot 預設 Log 機制與層級",
        "用 @Slf4j 寫出結構化、有語意的 Log",
        "透過 application.yml 設定各套件的 Log 層級",
        "用 Spring Actuator 動態調整 Log 層級，無需重啟應用",
        "理解 AOP 解決的問題與核心詞彙",
        "知道 Spring AOP 用 Proxy 實現，並了解其限制",
        "能辨識哪些 Spring 功能背後使用了 AOP",
        "知道在什麼情況下才需要直接撰寫 AOP",
        "理解 Spring Security 的 Authentication（認證）與 Authorization（授權）核心概念",
        "學會以 AI 輔助建立 JWT 過濾器並在 Token 內寫入使用者角色（ADMIN/USER）",
        "實作角色基礎存取控制（RBAC），客戶資料查詢 API 供業務人員使用，客戶資料刪除 API 僅限管理員",
        "保護 Swagger API 文件，限定僅能透過認證身分存取"
      ],
      "principle": "企業級後端的防線在於安全與一致性。Spring Security 與 JWT filter 組成了堅實的驗證防禦，全域錯誤處理 RFC 7807 則給予前端可預期的錯誤 JSON 結構，結合 Swagger 實現規格即文件。",
      "concepts": [
        {
          "heading": "為什麼需要 API 文件",
          "group": "OpenAPI 文件自動生成",
          "body": "後端 API 一旦超過 5 個端點，沒有文件的開發協作就開始痛苦：前端不知道要傳什麼格式、測試人員要翻程式碼才知道有哪些欄位、新人要花大量時間猜 request body 結構。\n\nOpenAPI 規範（前身是 Swagger）定義了一套描述 REST API 的標準格式，springdoc-openapi 能自動從 Spring MVC 的 Controller 掃描產生 OpenAPI 文件，並提供互動式 UI 讓人直接從瀏覽器呼叫 API。\n\n- 自動掃描：不需要手寫文件，從 Controller 標註推導\n- Swagger UI：瀏覽器直接測試每個端點，看到 request / response 格式\n- 機器可讀格式：前端工具可從 `/v3/api-docs` 取得 JSON 格式規格，自動產生 API client"
        },
        {
          "heading": "加入 springdoc-openapi 依賴",
          "group": "OpenAPI 文件自動生成",
          "body": "在 `pom.xml` 加入以下依賴，啟動應用後 Swagger UI 與 OpenAPI JSON 就自動可用，不需要任何額外設定。\n\n**版本對應規則**：springdoc-openapi 與 Spring Boot 的主版本號一一對應 —— Spring Boot 2.x 用 v1.x、Spring Boot 3.x 用 v2.x、Spring Boot 4.0.x 用 v3.x。本課程使用 Spring Boot 4.0.0，因此選用 `3.0.3`。\n\n**pom.xml — 加入 springdoc-openapi (xml)**\n```xml\n<dependency>\n    <groupId>org.springdoc</groupId>\n    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>\n    <version>3.0.3</version>  <!-- Spring Boot 4.0.x 對應 v3.x；Spring Boot 3.x 請用 v2.x -->\n</dependency>\n```"
        },
        {
          "heading": "啟動後的預設存取路徑",
          "group": "OpenAPI 文件自動生成",
          "body": "- `http://localhost:8080/swagger-ui.html` → 互動式 Swagger UI（可直接測試）\n- `http://localhost:8080/v3/api-docs` → OpenAPI JSON 規格（機器讀取）\n- `http://localhost:8080/v3/api-docs.yaml` → YAML 格式規格"
        },
        {
          "heading": "用標註豐富 API 說明",
          "group": "OpenAPI 文件自動生成",
          "body": "不加標註時 Swagger UI 只能從方法簽章推導基本資訊。用 `@Operation`、`@Parameter`、`@ApiResponse` 補充說明後，文件立即更完整，前端閱讀效率大幅提升。\n\n**CustomerController.java — 加入 OpenAPI 標註 (java)**\n```java\n@RestController\n@RequestMapping(\"/api/customers\")\n@Tag(name = \"客戶管理\", description = \"客戶的新增、查詢、修改與刪除\")\npublic class CustomerController {\n\n    @Operation(\n        summary = \"查詢所有客戶\",\n        description = \"回傳完整客戶清單，可加 keyword 參數進行模糊搜尋\"\n    )\n    @GetMapping\n    public ResponseEntity<List<Customer>> getCustomers(\n            @Parameter(description = \"客戶名稱關鍵字（選填）\")\n            @RequestParam(required = false) String keyword) {\n        // ...\n    }\n\n    @Operation(summary = \"新增客戶\")\n    @ApiResponses({\n        @ApiResponse(responseCode = \"201\", description = \"新增成功\"),\n        @ApiResponse(responseCode = \"400\", description = \"輸入資料驗證失敗\")\n    })\n    @PostMapping\n    public ResponseEntity<Customer> createCustomer(\n            @Valid @RequestBody Customer customer) {\n        // ...\n    }\n}\n```"
        },
        {
          "heading": "設定全域 API 資訊",
          "group": "OpenAPI 文件自動生成",
          "body": "建立一個 `@Configuration` 類別，設定整份文件的標題、版本與聯絡資訊，讓 Swagger UI 頁首顯示正確的專案說明。\n\n**OpenApiConfig.java (java)**\n```java\n@Configuration\npublic class OpenApiConfig {\n\n    @Bean\n    public OpenAPI openAPI() {\n        return new OpenAPI()\n            .info(new Info()\n                .title(\"AI CRM 系統 API\")\n                .version(\"1.0.0\")\n                .description(\"Spring Boot 4 + Spring AI 2.0 教學專案 API 文件\")\n                .contact(new Contact()\n                    .name(\"開發團隊\")\n                    .email(\"dev@example.com\")));\n    }\n}\n```"
        },
        {
          "heading": "AI 提示詞練習",
          "group": "AI 提示詞練習",
          "body": "試著用以下提示詞讓 AI 助手幫你完善 API 文件標註：\n\n- 「請幫我在 CustomerController 的所有端點加上 @Operation 說明，並補充每個可能的 HTTP 狀態碼對應的 @ApiResponse 標註。」\n- 「如何讓 Swagger UI 只在 dev profile 啟用，在 prod profile 自動關閉？請修改 application.yml 與 OpenApiConfig。」"
        },
        {
          "heading": "沒有全域例外處理的問題",
          "group": "全域例外處理",
          "body": "沒有統一例外處理時，Spring Boot 預設的錯誤回應格式混雜了 Tomcat 訊息與 Java 堆疊資訊，前端無法依賴固定結構解析錯誤。更糟的是，不同端點可能回傳完全不同格式的錯誤，增加前端的防禦成本。\n\n`@RestControllerAdvice` 讓你在一個地方定義所有例外的處理方式：每種例外對應一個方法，統一回傳相同結構的 JSON，Controller 本身完全不需要 try-catch。\n\n- 例外處理集中在一個類別，不散落各個 Controller\n- 回傳格式統一，前端只需解析一種結構\n- Controller 保持乾淨，只做「請求分派」這一件事"
        },
        {
          "heading": "建立統一的 ErrorResponse 格式",
          "group": "全域例外處理",
          "body": "先定義所有錯誤回應共用的資料結構。使用 Java Record 讓程式碼簡潔，Jackson 自動序列化為 JSON。\n\n**ErrorResponse.java (java)**\n```java\n/**\n * 統一的 API 錯誤回應格式\n * 所有例外處理方法都回傳此格式，讓前端只需解析一種結構\n */\npublic record ErrorResponse(\n    int status,          // HTTP 狀態碼\n    String error,        // 錯誤類型（如 \"Not Found\"）\n    String message,      // 人類可讀的錯誤說明\n    String path,         // 發生錯誤的 API 路徑\n    LocalDateTime timestamp  // 錯誤發生時間\n) {\n    /** 快速建立標準錯誤回應的工廠方法 */\n    public static ErrorResponse of(HttpStatus status, String message, String path) {\n        return new ErrorResponse(\n            status.value(),\n            status.getReasonPhrase(),\n            message,\n            path,\n            LocalDateTime.now()\n        );\n    }\n}\n```"
        },
        {
          "heading": "自訂業務例外類別",
          "group": "全域例外處理",
          "body": "用語意明確的例外類別取代直接拋出 `RuntimeException`，讓 GlobalExceptionHandler 能精確攔截並對應正確的 HTTP 狀態碼。\n\n**ResourceNotFoundException.java (java)**\n```java\n/**\n * 查詢資源不存在時拋出，對應 HTTP 404\n */\npublic class ResourceNotFoundException extends RuntimeException {\n\n    public ResourceNotFoundException(String resourceName, Long id) {\n        super(resourceName + \" 不存在：id = \" + id);\n    }\n}\n\n// Service 使用方式\n@Transactional(readOnly = true)\npublic Customer getCustomerById(Long id) {\n    return customerRepository.findById(id)\n        .orElseThrow(() -> new ResourceNotFoundException(\"客戶\", id));\n}\n```"
        },
        {
          "heading": "建立 GlobalExceptionHandler",
          "group": "全域例外處理",
          "body": "`@RestControllerAdvice` 讓這個類別的 `@ExceptionHandler` 方法攔截整個應用程式的例外。每個方法對應一種例外類型，回傳統一的 `ErrorResponse`。\n\n**GlobalExceptionHandler.java (java)**\n```java\n@RestControllerAdvice\npublic class GlobalExceptionHandler {\n\n    /** 查詢資源不存在 → 404 */\n    @ExceptionHandler(ResourceNotFoundException.class)\n    public ResponseEntity<ErrorResponse> handleNotFound(\n            ResourceNotFoundException ex, HttpServletRequest req) {\n        return ResponseEntity\n            .status(HttpStatus.NOT_FOUND)\n            .body(ErrorResponse.of(HttpStatus.NOT_FOUND, ex.getMessage(), req.getRequestURI()));\n    }\n\n    /** Bean Validation 驗證失敗 → 400，收集所有欄位錯誤 */\n    @ExceptionHandler(MethodArgumentNotValidException.class)\n    public ResponseEntity<Map<String, Object>> handleValidation(\n            MethodArgumentNotValidException ex, HttpServletRequest req) {\n\n        List<String> errors = ex.getBindingResult().getFieldErrors().stream()\n            .map(f -> f.getField() + \"：\" + f.getDefaultMessage())\n            .toList();\n\n        Map<String, Object> body = Map.of(\n            \"status\", 400,\n            \"error\", \"Bad Request\",\n            \"message\", \"輸入資料驗證失敗\",\n            \"errors\", errors,\n            \"path\", req.getRequestURI()\n        );\n        return ResponseEntity.badRequest().body(body);\n    }\n\n    /** 其他未預期例外 → 500，避免洩漏堆疊資訊給前端 */\n    @ExceptionHandler(Exception.class)\n    public ResponseEntity<ErrorResponse> handleGeneral(\n            Exception ex, HttpServletRequest req) {\n        return ResponseEntity\n            .status(HttpStatus.INTERNAL_SERVER_ERROR)\n            .body(ErrorResponse.of(\n                HttpStatus.INTERNAL_SERVER_ERROR,\n                \"伺服器發生錯誤，請稍後再試\",\n                req.getRequestURI()));\n    }\n}\n```"
        },
        {
          "heading": "例外處理回應對照",
          "group": "全域例外處理",
          "body": "以下是三種例外情境對應的實際 JSON 回應，前端可依 `status` 欄位決定顯示方式。\n\n**各類例外的回應格式 (json)**\n```json\n// GET /api/customers/999 → 客戶不存在\n{\n  \"status\": 404,\n  \"error\": \"Not Found\",\n  \"message\": \"客戶 不存在：id = 999\",\n  \"path\": \"/api/customers/999\",\n  \"timestamp\": \"2026-06-08T10:30:00\"\n}\n\n// POST /api/customers → 驗證失敗\n{\n  \"status\": 400,\n  \"error\": \"Bad Request\",\n  \"message\": \"輸入資料驗證失敗\",\n  \"errors\": [\"name：客戶名稱不可為空\", \"level：等級必須為 VIP、General 或 New\"],\n  \"path\": \"/api/customers\"\n}\n\n// 任何未預期錯誤\n{\n  \"status\": 500,\n  \"error\": \"Internal Server Error\",\n  \"message\": \"伺服器發生錯誤，請稍後再試\",\n  \"path\": \"/api/customers\"\n}\n```"
        },
        {
          "heading": "ProblemDetail：Spring Boot 3 內建標準格式",
          "group": "全域例外處理",
          "body": "Spring Boot 3+ 採用 RFC 9457 的 `ProblemDetail` 作為標準錯誤格式，Spring Boot 4 延續支援並推薦使用。不需要自訂 `ErrorResponse`，可直接在 `GlobalExceptionHandler` 回傳 `ProblemDetail` 物件，格式已符合業界標準。\n\n- `ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, \"客戶不存在\")` → 直接建立標準格式物件\n- 可透過 `problemDetail.setProperty(\"extra\", value)` 加入自訂欄位\n- 在 `application.yml` 加上 `spring.mvc.problemdetails.enabled: true` 可讓 Spring 預設用此格式回傳驗證錯誤"
        },
        {
          "heading": "Spring Boot 預設 Log 機制",
          "group": "Log 日誌管理",
          "body": "Spring Boot 預設使用 Logback 作為 Log 框架，並透過 SLF4J 提供統一的 API 介面。不需要任何設定就能使用，只要依賴 `spring-boot-starter`（幾乎所有 Starter 都已包含）就自動啟用。\n\n預設 Log 格式包含時間戳記、層級、執行緒、類別名稱與訊息。開發時輸出到 console，可另外設定輸出到檔案。\n\n- `ERROR` → 系統發生嚴重錯誤，需要立即處理\n- `WARN` → 可能有問題，但系統還能運作\n- `INFO` → 正常業務流程的關鍵節點（預設顯示層級）\n- `DEBUG` → 詳細的執行資訊，開發除錯使用\n- `TRACE` → 最詳細層級，通常只在框架內部使用"
        },
        {
          "heading": "@Slf4j 與結構化 Log 寫法",
          "group": "Log 日誌管理",
          "body": "Lombok 的 `@Slf4j` 自動注入 `log` 物件，省去手動宣告 Logger 的樣板程式碼。Log 訊息用 `{}` 佔位符取代字串拼接，避免不必要的字串建立開銷，也讓訊息格式更清楚。\n\n**CustomerService.java — @Slf4j 使用範例 (java)**\n```java\n@Slf4j   // Lombok：自動注入 private static final Logger log = ...\n@Service\npublic class CustomerService {\n\n    public Customer saveCustomer(Customer customer) {\n        log.info(\"新增客戶：name={}, level={}, email={}\", customer.getName(), customer.getLevel(), customer.getEmail());\n\n        Customer saved = customerRepository.save(customer);\n\n        log.info(\"客戶新增成功：id={}\", saved.getId());\n        return saved;\n    }\n\n    public void deleteCustomer(Long id) {\n        if (customerRepository.existsById(id)) {\n            customerRepository.deleteById(id);\n            log.info(\"客戶刪除成功：id={}\", id);\n        } else {\n            log.warn(\"嘗試刪除不存在的客戶：id={}\", id);\n            throw new ResourceNotFoundException(\"客戶\", id);\n        }\n    }\n\n    // ✅ 用 {} 佔位符（延遲計算，層級關閉時不建立字串）\n    log.debug(\"查詢條件：{}\", filterRequest);\n\n    // ❌ 避免字串拼接（即使 DEBUG 關閉也會建立字串）\n    // log.debug(\"查詢條件：\" + filterRequest);\n}\n```"
        },
        {
          "heading": "application.yml 設定 Log 層級",
          "group": "Log 日誌管理",
          "body": "透過 `application.yml` 控制各套件的 Log 層級，可以讓本專案輸出 DEBUG、同時讓 Hibernate 顯示實際執行的 SQL，方便開發期除錯。\n\n**application.yml — Log 層級設定 (yaml)**\n```yaml\nlogging:\n  level:\n    root: INFO                          # 全域預設層級\n    com.example.tutorial: DEBUG         # 本專案詳細輸出\n    org.hibernate.SQL: DEBUG            # 顯示 Hibernate 產生的 SQL\n    org.hibernate.orm.jdbc.bind: TRACE  # 顯示 SQL 綁定參數值\n```"
        },
        {
          "heading": "動態調整 Log 層級（Spring Actuator）",
          "group": "Log 日誌管理",
          "body": "正式環境不能為了調 Log 就重啟應用。加入 `spring-boot-starter-actuator` 後，可以透過 HTTP API 在執行期動態修改特定套件的 Log 層級，調完問題再改回去。\n\n- 先在 pom.xml 加入 `spring-boot-starter-actuator` 依賴\n- 在 application.yml 開放 loggers 端點：`management.endpoints.web.exposure.include: loggers,health`\n\n**動態調整 Log 層級（PowerShell） (bash)**\n```bash\n# 查詢目前 com.example.tutorial 的 Log 層級\nInvoke-RestMethod -Uri \"http://localhost:8080/actuator/loggers/com.example.tutorial\"\n\n# 動態改為 DEBUG（無需重啟）\nInvoke-RestMethod `\n  -Method PATCH `\n  -Uri \"http://localhost:8080/actuator/loggers/com.example.tutorial\" `\n  -ContentType \"application/json\" `\n  -Body '{\"configuredLevel\": \"DEBUG\"}'\n\n# 問題排查完後改回 INFO\nInvoke-RestMethod `\n  -Method PATCH `\n  -Uri \"http://localhost:8080/actuator/loggers/com.example.tutorial\" `\n  -ContentType \"application/json\" `\n  -Body '{\"configuredLevel\": \"INFO\"}'\n```"
        },
        {
          "heading": "Log 最佳實踐",
          "group": "Log 日誌管理",
          "body": "- **INFO**：記錄業務關鍵節點（誰建立了什麼、誰觸發了什麼操作），足以在不看程式碼的情況下理解系統在做什麼\n- **WARN**：可以自動恢復或降級的異常情境（如 retry、fallback），需要關注但不需要立即處理\n- **ERROR**：需要人工介入的問題，搭配 `log.error(\"...\", ex)` 記錄完整 stack trace\n- **避免在 Log 記錄密碼、Token、信用卡號**：即使是 DEBUG 層級，log 檔可能被備份或轉發到第三方\n- **用 `{}` 佔位符而非字串拼接**：`log.debug(\"id={}\", id)` 在 DEBUG 層級關閉時不建立字串，效能更好"
        },
        {
          "heading": "AOP 解決了什麼問題",
          "group": "AOP 面向切面",
          "body": "寫後端程式時，有一類邏輯天生就不屬於任何單一業務模組，卻又需要出現在幾乎每個地方——交易控制、效能計時、權限驗證、Log 記錄。如果把這些邏輯都寫在每個 Service 方法裡，程式碼會充滿重複，而且修改一次規則要動到幾十個地方。\n\nAOP（Aspect-Oriented Programming，面向切面程式設計）的核心想法是：把這類「橫切關注點（Cross-cutting Concern）」從業務邏輯中抽離出來，統一定義在一個地方，再宣告「在哪些方法的哪個時間點套用」。業務程式碼保持乾淨，橫切邏輯只寫一次。\n\n- **交易管理**：每個寫入操作都需要 begin / commit / rollback，不該散落各 Service\n- **Log 記錄**：記錄方法進入、結束、耗時，不該每個方法都手寫\n- **輸入驗證**：呼叫 Service 前驗證參數，不該在每個方法頭部重複 if-else\n- **權限檢查**：確認使用者有沒有權限呼叫這個方法，屬於安全層而非業務層"
        },
        {
          "heading": "Spring AOP 五大核心詞彙",
          "group": "AOP 面向切面",
          "body": "理解 AOP 只需要掌握五個詞彙，其餘的都是這五個概念的組合。\n\n- **Join Point（連接點）**：程式執行中可以被攔截的時間點，Spring AOP 的 Join Point 就是「方法被呼叫的瞬間」\n- **Pointcut（切入點）**：用來篩選「哪些 Join Point 要套用 Advice」的規則，通常用 execution 表達式描述，例如「所有 Service 套件下的 public 方法」\n- **Advice（增強/通知）**：在 Join Point 要執行的動作，分成 Before（方法前）、After（方法後）、Around（包覆方法前後）、AfterReturning（成功回傳後）、AfterThrowing（拋出例外後）五種\n- **Aspect（切面）**：把 Pointcut 與 Advice 組合在一起的模組，就像「交易管理切面」= 所有 Service 方法（Pointcut）+ 自動 commit/rollback（Advice）\n- **Weaving（織入）**：把 Aspect 應用到目標物件的過程；Spring AOP 在執行期（Runtime）透過 Proxy 物件完成織入，不修改原始類別的 bytecode"
        },
        {
          "heading": "AOP 概念圖解",
          "group": "AOP 面向切面",
          "body": "上半部展示橫切關注點如何切穿所有 Service，下半部展示 Spring 用 Proxy 在執行期攔截方法的原理。"
        },
        {
          "heading": "Spring AOP 的實現方式：Proxy",
          "group": "AOP 面向切面",
          "body": "Spring AOP 不修改你的程式碼，而是在執行期替目標 Bean 建立一個「代理物件（Proxy）」。每次你呼叫 `@Autowired` 注入的 Bean 方法，實際上是呼叫 Proxy，Proxy 先執行 Advice（如開啟交易），再呼叫你的真實方法，最後再執行 Advice（如 commit）。\n\nSpring 根據情況選擇兩種 Proxy 實作：介面存在時用 JDK 動態 Proxy（速度快），無介面時用 CGLIB（繼承方式建立子類別 Proxy）。"
        },
        {
          "heading": "直接撰寫 AOP 的寫法（效能監控範例）",
          "group": "AOP 面向切面",
          "body": "雖然日常開發很少直接寫 AOP，但了解寫法有助於理解 Spring 內建功能的運作原理。以下是一個「記錄所有 Service 方法執行時間」的 Around Advice 範例，加入 `spring-boot-starter-aop` 依賴後即可使用。\n\n**PerformanceAspect.java — 直接撰寫 AOP 範例 (java)**\n```java\n// pom.xml 加入：spring-boot-starter-aop\n@Aspect          // 宣告這是一個切面\n@Component       // 讓 Spring 管理這個 Bean\n@Slf4j\npublic class PerformanceAspect {\n\n    /**\n     * Pointcut：com.example.tutorial.service 套件下所有類別的所有 public 方法\n     * execution(回傳型別 套件.類別.方法名稱(參數))\n     */\n    @Around(\"execution(* com.example.tutorial.service.*.*(..))\")\n    public Object logExecutionTime(ProceedingJoinPoint pjp) throws Throwable {\n\n        long start = System.currentTimeMillis();\n\n        Object result = pjp.proceed();  // 呼叫真實方法（Around 的核心）\n\n        long elapsed = System.currentTimeMillis() - start;\n\n        log.info(\"[AOP] {}#{} 執行 {} ms\",\n            pjp.getTarget().getClass().getSimpleName(),  // 類別名稱\n            pjp.getSignature().getName(),                // 方法名稱\n            elapsed);\n\n        return result;\n    }\n}\n```"
        },
        {
          "heading": "很少直接寫 AOP 的原因",
          "group": "AOP 面向切面",
          "body": "Spring 已經把最常用的橫切需求都封裝成標註（Annotation）了。你用 `@Transactional` 就等於在 Service 方法外包了一個 Around Advice，用 `@Valid` 就等於在 Controller 方法前放了一個 Before Advice，根本不需要自己寫 `@Aspect`。\n\n**直接撰寫 AOP 的時機**通常只有兩種：一是需求無法用現有標註表達（例如對所有方法計時、統一寫入稽核 Log）；二是為公司內部框架提供可重用的橫切能力。一般業務開發幾乎不需要接觸 `@Aspect`。\n\n- `@Transactional` → Spring 幫你寫好的交易 AOP，不需要自己包 Around Advice\n- `@Valid` / `@Validated` → Spring 幫你寫好的驗證 AOP，不需要自己在方法頭部驗參數\n- `@RestControllerAdvice` → Spring MVC 幫你寫好的例外攔截，不需要自己包 AfterThrowing\n- `@EnableJpaAuditing` → JPA 幫你寫好的 @PrePersist / @PreUpdate 攔截，不需要自己設 Listener\n- `spring-boot-starter-actuator` → Spring 幫你寫好的管理端點，不需要自己做 Health Check AOP"
        },
        {
          "heading": "Day 1 哪些功能背後用了 AOP",
          "group": "AOP 面向切面",
          "body": "回顧第一天學過的所有功能，以下整理哪些地方在背後使用了 AOP 或相同設計概念，以及對應的 Spring 元件。"
        },
        {
          "heading": "安全防護重點",
          "group": "Spring Security 與 JWT 認證",
          "body": "在生產環境中，API 不能是完全公開的。本章將引入 Spring Security 與 JWT (JSON Web Token)，為我們的 REST API 建立安全防護底盤。\n\n我們將實作「無狀態 (Stateless)」認證：使用者透過 `/api/auth/login` 登入成功後取得 JWT，後續請求都必須在 Header 攜帶此 Token 進行驗證。此外，我們將簡單區分角色：「管理員 (ADMIN)」與「一般用戶 (USER)」，以實作更精細的權限控管。\n\n- Authentication 認證：確認「你是誰」（透過帳號密碼登入並簽發 JWT）\n- Authorization 授權：確認「你能做什麼」（例如管理員能刪除客戶資料，業務人員只能查詢與編輯）\n- 無狀態認證：伺服器不儲存 Session，每次請求均由 JWT 驗證身分與角色"
        },
        {
          "heading": "請 AI Agent 幫你安裝 Security 與 JWT 依賴",
          "group": "Spring Security 與 JWT 認證",
          "body": "在 `pom.xml` 中引入 Spring Security Starter 與 JWT 套件。我們使用目前主流且穩定的 `io.jsonwebtoken` (jjwt) 套件來進行 Token 的簽署與解析。\n\n**pom.xml 依賴配置 (xml)**\n```xml\n<!-- Spring Security Starter -->\n<dependency>\n    <groupId>org.springframework.boot</groupId>\n    <artifactId>spring-boot-starter-security</artifactId>\n</dependency>\n\n<!-- JWT (jjwt) 相關依賴 -->\n<dependency>\n    <groupId>io.jsonwebtoken</groupId>\n    <artifactId>jjwt-api</artifactId>\n    <version>0.12.5</version>\n</dependency>\n<dependency>\n    <groupId>io.jsonwebtoken</groupId>\n    <artifactId>jjwt-impl</artifactId>\n    <version>0.12.5</version>\n    <scope>runtime</scope>\n</dependency>\n<dependency>\n    <groupId>io.jsonwebtoken</groupId>\n    <artifactId>jjwt-jackson</artifactId>\n    <version>0.12.5</version>\n    <scope>runtime</scope>\n</dependency>\n```"
        },
        {
          "heading": "AI Agent 提示詞 — 身分驗證與角色授權實作",
          "group": "Spring Security 與 JWT 認證",
          "body": "將以下提示詞複製給 AI Agent，讓它幫你生成完整的安全驗證配置。此提示詞特別強調了角色定義。\n\n- 在 JWT 內寫入使用者角色（簡單區分管理員 ADMIN 與用戶 USER）\n- 限制客戶資料刪除 API 僅限管理員角色存取，客戶資料查詢 API 需登入身分即可\n- 保護 Swagger UI 與 OpenAPI 網頁與端點，需登入才能瀏覽\n\n**AI Agent 提示詞 (text)**\n```text\n請在現有專案中，使用 Spring Security 與 JWT 實作安全防護與登入驗證功能：\n1. 引入安全防護套件（Spring Security）與 JWT 依賴，限制除了登入相關的 API 之外，其餘所有的 API 都需要攜帶 JWT Token 進行驗證才能存取。\n2. 實作一個登入 API（例如 POST /api/auth/login），供使用者傳入帳號密碼進行身分驗證。登入成功後，請在 JWT Token 中寫入使用者的角色（簡單區分「管理員 ADMIN」與「一般用戶 USER」），並將 Token 回傳給前端。\n3. 實作角色權限控制：限制客戶資料刪除 API 必須具備「管理員 ADMIN」角色才能執行，而一般查詢與編輯功能則僅需「用戶 USER」或已登入身分即可。\n4. 保護我們的 API 文件（Swagger UI 網頁與相關端點），設定必須在登入驗證並攜帶 JWT Token 後才能正常瀏覽與測試。\n```"
        },
        {
          "heading": "後端安全設定範例 (SecurityConfig.java)",
          "group": "Spring Security 與 JWT 認證",
          "body": "Spring Security 核心設定。我們配置雙重 `SecurityFilterChain`：第一個專門保護 Swagger 文件，限制使用 Basic Auth 登入；第一個保護一般系統 API，採用 JWT 驗證與無狀態 Session 策略，並限制客戶資料刪除為 ADMIN。\n\n**SecurityConfig.java 範例 (java)**\n```java\n@Configuration\n@EnableWebSecurity\npublic class SecurityConfig {\n\n    private final JwtAuthenticationFilter jwtAuthenticationFilter;\n\n    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {\n        this.jwtAuthenticationFilter = jwtAuthenticationFilter;\n    }\n\n    @Bean\n    public PasswordEncoder passwordEncoder() {\n        return new BCryptPasswordEncoder();\n    }\n\n    @Bean\n    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {\n        return config.getAuthenticationManager();\n    }\n\n    /** 1. Swagger UI 與 API 文件安全防護鏈 (以 HTTP Basic 認證保護) */\n    @Bean\n    @Order(1)\n    public SecurityFilterChain swaggerSecurityFilterChain(HttpSecurity http) throws Exception {\n        http\n                .securityMatcher(\"/swagger-ui/**\", \"/v3/api-docs/**\", \"/swagger-ui.html\")\n                .csrf(csrf -> csrf.disable())\n                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))\n                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())\n                .httpBasic(Customizer.withDefaults());\n        return http.build();\n    }\n\n    /** 2. REST APIs 安全防護鏈 (以 JWT 認證與角色權限控制保護) */\n    @Bean\n    @Order(2)\n    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {\n        http\n                .csrf(csrf -> csrf.disable())\n                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))\n                .authorizeHttpRequests(authorize -> authorize\n                        .requestMatchers(\"/api/auth/login\").permitAll() // 登入 API 公開\n                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, \"/api/customers/**\").hasRole(\"ADMIN\") // 刪除客戶限管理員\n                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, \"/api/opportunities/**\").hasRole(\"ADMIN\") // 刪除商機限管理員\n                        .anyRequest().authenticated() // 其餘均需驗證\n                )\n                .exceptionHandling(exception -> exception\n                        .authenticationEntryPoint((request, response, authException) -> {\n                            response.setContentType(\"application/json;charset=UTF-8\");\n                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);\n                            response.getWriter().write(\"{\\\"error\\\": \\\"未授權：請先登入並攜帶有效的 JWT Token\\\"}\");\n                        })\n                )\n                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);\n        return http.build();\n    }\n}\n```"
        },
        {
          "heading": "JWT 工具類別範例 (JwtUtils.java)",
          "group": "Spring Security 與 JWT 認證",
          "body": "JWT 產生、解析與驗證工具。在產生 Token 時，將使用者的角色 (role) 寫入 Claim 中；解析時可將其取出。\n\n**JwtUtils.java 範例 (java)**\n```java\n@Component\npublic class JwtUtils {\n\n    private static final String SECRET_STRING = \"v9y$B&E)H@McQfTjWnZr4u7x!A%C*F-JaNdRgUkXp2s5v8y/B?D(G+KbPeShVmYq\";\n    private final SecretKey key = Keys.hmacShaKeyFor(SECRET_STRING.getBytes(StandardCharsets.UTF_8));\n    private final long expirationMs = 24 * 60 * 60 * 1000L; // 24小時\n\n    /** 生成 Token 時寫入角色資訊 */\n    public String generateToken(String username) {\n        String role = username.equals(\"admin\") ? \"ROLE_ADMIN\" : \"ROLE_USER\"; // 簡單根據帳號指派角色\n        return Jwts.builder()\n                .subject(username)\n                .claim(\"role\", role) // 寫入角色 Claim\n                .issuedAt(new Date())\n                .expiration(new Date(System.currentTimeMillis() + expirationMs))\n                .signWith(key)\n                .compact();\n    }\n\n    public String getUsernameFromToken(String token) {\n        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();\n    }\n\n    public String getRoleFromToken(String token) {\n        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().get(\"role\", String.class);\n    }\n\n    public boolean validateToken(String token) {\n        try {\n            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);\n            return true;\n        } catch (Exception e) {\n            return false;\n        }\n    }\n}\n```"
        },
        {
          "heading": "JWT 身分驗證過濾器範例 (JwtAuthenticationFilter.java)",
          "group": "Spring Security 與 JWT 認證",
          "body": "過濾器會攔截每個 API 請求。從 Authorization Header 中讀取 token 並進行身分認證。\n\n**JwtAuthenticationFilter.java 範例 (java)**\n```java\n@Component\npublic class JwtAuthenticationFilter extends OncePerRequestFilter {\n\n    private final JwtUtils jwtUtils;\n    private final CustomUserDetailsService userDetailsService;\n\n    public JwtAuthenticationFilter(JwtUtils jwtUtils, CustomUserDetailsService userDetailsService) {\n        this.jwtUtils = jwtUtils;\n        this.userDetailsService = userDetailsService;\n    }\n\n    @Override\n    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)\n            throws ServletException, IOException {\n        try {\n            String jwt = parseJwt(request);\n            if (jwt != null && jwtUtils.validateToken(jwt)) {\n                String username = jwtUtils.getUsernameFromToken(jwt);\n                String role = jwtUtils.getRoleFromToken(jwt);\n                \n                // 載入使用者詳細資料與授權角色\n                UserDetails userDetails = userDetailsService.loadUserByUsername(username);\n                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(\n                        userDetails, null, List.of(new SimpleGrantedAuthority(role)));\n                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));\n                \n                // 綁定至安全上下文\n                SecurityContextHolder.getContext().setAuthentication(authentication);\n            }\n        } catch (Exception e) {\n            logger.error(\"Security context binding failed\", e);\n        }\n        filterChain.doFilter(request, response);\n    }\n\n    private String parseJwt(HttpServletRequest request) {\n        // 從 Header 讀取 Authorization Bearer Token\n        String headerAuth = request.getHeader(\"Authorization\");\n        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith(\"Bearer \")) {\n            return headerAuth.substring(7);\n        }\n        return null;\n    }\n}\n```"
        },
        {
          "heading": "登入控制器範例 (AuthController.java)",
          "group": "Spring Security 與 JWT 認證",
          "body": "提供登入端點，傳入帳號密碼並呼叫 `AuthenticationManager` 進行身分驗證。驗證成功後生成 JWT Token 並回傳。\n\n**AuthController.java 範例 (java)**\n```java\n@RestController\n@RequestMapping(\"/api/auth\")\n@Tag(name = \"認證管理\")\npublic class AuthController {\n\n    private final AuthenticationManager authenticationManager;\n    private final JwtUtils jwtUtils;\n\n    public AuthController(AuthenticationManager authenticationManager, JwtUtils jwtUtils) {\n        this.authenticationManager = authenticationManager;\n        this.jwtUtils = jwtUtils;\n    }\n\n    @PostMapping(\"/login\")\n    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {\n        Authentication authentication = authenticationManager.authenticate(\n                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())\n        );\n        SecurityContextHolder.getContext().setAuthentication(authentication);\n        String token = jwtUtils.generateToken(loginRequest.getUsername());\n        return ResponseEntity.ok(new LoginResponse(token));\n    }\n}\n```"
        },
        {
          "heading": "Swagger 網頁驗證步驟（推薦）",
          "group": "Spring Security 與 JWT 認證",
          "body": "後端啟動後，我們可以透過 Swagger UI 網頁進行 API 測試，視覺化地驗證登入、JWT 簽發與角色基礎授權控制（RBAC）是否正常運作。\n\n<div style=\"margin-top: 16px;\"><ul style=\"list-style-type: decimal; margin-left: 20px; margin-bottom: 16px; line-height: 1.8;\"><li style=\"margin-bottom: 6px;\"><strong>開啟 Swagger 網頁</strong>：在瀏覽器中輸入 <a href=\"http://localhost:8080/swagger-ui/index.html\" target=\"_blank\" class=\"accent-link\">http://localhost:8080/swagger-ui/index.html</a>。</li><li style=\"margin-bottom: 6px;\"><strong>安全登入 (HTTP Basic)</strong>：由於設定了安全防護，瀏覽器會彈出登入對話框。請輸入管理員帳密（帳號：<code>admin</code>，密碼：<code>password</code>）完成登入。</li><li style=\"margin-bottom: 6px;\"><strong>取得 JWT Token</strong>：展開 <code>POST /api/auth/login</code>，點選 <strong>Try it out</strong>，傳入使用者資料（如：<code>{\"username\": \"user\", \"password\": \"password\"}</code>）執行並複製回傳的 token。</li><li style=\"margin-bottom: 6px;\"><strong>點擊 Authorize 帶入 Token</strong>：回到頁面最上方點選 <strong>Authorize</strong> 按鈕，在 <code>BearerAuth</code> 欄位填入剛剛複製的 JWT Token，點擊 Authorize 啟用。</li><li style=\"margin-bottom: 6px;\"><strong>驗證角色存取控制 (RBAC)</strong>：在授權為 <code>user</code> 狀態下呼叫刪除客戶 API，預期應收到 <strong>403 Forbidden</strong>；以同樣方式更換為 <code>admin</code> 的 Token 後呼叫則應成功回傳 <strong>204 No Content</strong>。</li></ul></div>"
        },
        {
          "heading": "PowerShell 驗證命令（可選）",
          "group": "Spring Security 與 JWT 認證",
          "body": "若您偏好命令列測試，可在 PowerShell 7+ 中依序執行以下命令進行登入與 API 安全性存取測試。\n\n- 管理員帳號：`admin`，密碼為資料庫已加密儲存值\n- 一般用戶帳號：`user`，密碼為資料庫已加密儲存值\n- 1. 使用 `user` 登入取得 Token，測試查詢客戶列表 API，預期應回傳 200 OK\n- 2. 使用 `user` 登入取得 Token，測試刪除客戶 API，預期應回傳 403 Forbidden\n- 3. 使用 `admin` 登入取得 Token，測試刪除客戶 API，預期應回傳 204 No Content\n\n**驗證 API 存取控制 (powershell)**\n```powershell\n# 1. 以普通用戶登入\n$loginUser = Invoke-RestMethod -Uri \"http://localhost:8080/api/auth/login\" -Method Post -ContentType \"application/json\" -Body '{\"username\":\"user\",\"password\":\"password\"}'\n$userToken = $loginUser.token\n\n# 2. 測試普通用戶查詢客戶 (應成功回傳 200)\n$customers = Invoke-RestMethod -Uri \"http://localhost:8080/api/customers\" -Method Get -Headers @{Authorization=\"Bearer $userToken\"}\n$customers.Count # 輸出客戶筆數\n\n# 3. 測試普通用戶刪除客戶 (應被拒絕回傳 403)\ntry {\n    Invoke-RestMethod -Uri \"http://localhost:8080/api/customers/1\" -Method Delete -Headers @{Authorization=\"Bearer $userToken\"}\n} catch {\n    $_.Exception.Response.StatusCode # 輸出: Forbidden (403)\n}\n\n# 4. 以管理員登入\n$loginAdmin = Invoke-RestMethod -Uri \"http://localhost:8080/api/auth/login\" -Method Post -ContentType \"application/json\" -Body '{\"username\":\"admin\",\"password\":\"password\"}'\n$adminToken = $loginAdmin.token\n\n# 5. 測試管理員刪除客戶 (應成功回傳 204)\nInvoke-RestMethod -Uri \"http://localhost:8080/api/customers/1\" -Method Delete -Headers @{Authorization=\"Bearer $adminToken\"}\nWrite-Output \"客戶已刪除\"\n```"
        }
      ],
      "prompt": "請接續 Unit 3 的實作內容。現在我們要進入 Unit 4：Spring Security、JWT、OpenAPI 與企業級錯誤處理。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### AI Agent 提示詞 — 身分驗證與角色授權實作\n\n【開發角色與安全性要求】\n你是一位精通 Web 安全防護與企業級安全架構的 Spring Security 專家。請在我現有的 Spring Boot 專案（已整合 JPA 與 PostgreSQL）中實作安全防護與登入驗證功能，完全符合 Java 21 開發規範。\n\n【實作功能與安全性規格】\n1. **安全性架構設計 (Spring Security + JWT)**：\n   - 引入 `spring-boot-starter-security` 與 JWT 相依性套件。\n   - 實作 JWT Token 產生器與解析過濾器 (`JwtAuthenticationFilter`)，並將其配置於安全過濾鏈（Filter Chain）中。\n   - 除了登入 API（`POST /api/auth/login`）與 Swagger API 文件端點外，其餘所有對 `/api/**` 的請求皆必須攜帶 JWT Token 才能進行存取，未帶 Token 回傳 401 Unauthorized。\n2. **角色與細粒度授權 (RBAC)**：\n   - 在 JWT 的 Payload 中寫入使用者的角色（「系統管理員 ROLE_ADMIN」與「一般用戶 ROLE_USER」）。\n   - 使用 `@PreAuthorize` 或設定安全過濾鏈，限制「客戶資料刪除」功能（`DELETE /api/customers/{id}`）必須具備 `ROLE_ADMIN` 權限；而客戶查詢與新增僅需 `ROLE_USER` 角色。\n3. **企業級全域錯誤處理與日誌**：\n   - 使用 `@RestControllerAdvice` 建立全域例外處理器，處理諸如驗證失敗 (`MethodArgumentNotValidException`)、身分驗證失敗與自訂的業務例外。\n   - 回傳符合 RFC 7807 標準的 `ProblemDetail` 物件，使錯誤 JSON 結構保持一致。\n   - 在關鍵安全與業務端點加上符合生產級別的 `@Slf4j` 結構化日誌記錄。\n4. **API 文件保護 (OpenAPI / Swagger)**：\n   - 整合 `springdoc-openapi` 以自動掃描產生 Swagger UI，並在 Swagger 設定中配置 Bearer Token 支援，讓前端能在網頁中帶入 Token 進行測試。限制 Swagger 文件的存取權限。\n\n【程式碼與測試規範】\n- 程式碼需有中文註解，詳細說明安全配置、Token 簽發、RBAC 控制的邏輯。\n- 專案需相容於 Windows 環境下的啟動與測試。",
      "promptMac": "請接續 Unit 3 的實作內容。現在我們要進入 Unit 4：Spring Security、JWT、OpenAPI 與企業級錯誤處理。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### AI Agent 提示詞 — 身分驗證與角色授權實作\n\n【開發角色與安全性要求】\n你是一位精通 Web 安全防護與企業級安全架構的 Spring Security 專家。請在我現有的 Spring Boot 專案（已整合 JPA 與 PostgreSQL）中實作安全防護與登入驗證功能，完全符合 Java 21 開發規範。\n\n【實作功能與安全性規格】\n1. **安全性架構設計 (Spring Security + JWT)**：\n   - 引入 `spring-boot-starter-security` 與 JWT 相依性套件。\n   - 實作 JWT Token 產生器與解析過濾器 (`JwtAuthenticationFilter`)，並將其配置於安全過濾鏈（Filter Chain）中。\n   - 除了登入 API（`POST /api/auth/login`）與 Swagger API 文件端點外，其餘所有對 `/api/**` 的請求皆必須攜帶 JWT Token 才能進行存取，未帶 Token 回傳 401 Unauthorized。\n2. **角色與細粒度授權 (RBAC)**：\n   - 在 JWT 的 Payload 中寫入使用者的角色（「系統管理員 ROLE_ADMIN」與「一般用戶 ROLE_USER」）。\n   - 使用 `@PreAuthorize` 或設定安全過濾鏈，限制「客戶資料刪除」功能（`DELETE /api/customers/{id}`）必須具備 `ROLE_ADMIN` 權限；而客戶查詢與新增僅需 `ROLE_USER` 角色。\n3. **企業級全域錯誤處理與日誌**：\n   - 使用 `@RestControllerAdvice` 建立全域例外處理器，處理諸如驗證失敗 (`MethodArgumentNotValidException`)、身分驗證失敗與自訂的業務例外。\n   - 回傳符合 RFC 7807 標準的 `ProblemDetail` 物件，使錯誤 JSON 結構保持一致。\n   - 在關鍵安全與業務端點加上符合生產級別的 `@Slf4j` 結構化日誌記錄。\n4. **API 文件保護 (OpenAPI / Swagger)**：\n   - 整合 `springdoc-openapi` 以自動掃描產生 Swagger UI，並在 Swagger 設定中配置 Bearer Token 支援，讓前端能在網頁中帶入 Token 進行測試。限制 Swagger 文件的存取權限。\n\n【程式碼與測試規範】\n- 程式碼需有中文註解，詳細說明安全配置、Token 簽發、RBAC 控制的邏輯。\n- 專案需相容於 macOS 系統環境。",
      "prompts": [
        {
          "title": "高級 AI 協作提示詞大師範本（Windows 版）",
          "note": "可直接交給 Codex / Gemini 進行輔助開發",
          "text": "請接續 Unit 3 的實作內容。現在我們要進入 Unit 4：Spring Security、JWT、OpenAPI 與企業級錯誤處理。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### AI Agent 提示詞 — 身分驗證與角色授權實作\n\n【開發角色與安全性要求】\n你是一位精通 Web 安全防護與企業級安全架構的 Spring Security 專家。請在我現有的 Spring Boot 專案（已整合 JPA 與 PostgreSQL）中實作安全防護與登入驗證功能，完全符合 Java 21 開發規範。\n\n【實作功能與安全性規格】\n1. **安全性架構設計 (Spring Security + JWT)**：\n   - 引入 `spring-boot-starter-security` 與 JWT 相依性套件。\n   - 實作 JWT Token 產生器與解析過濾器 (`JwtAuthenticationFilter`)，並將其配置於安全過濾鏈（Filter Chain）中。\n   - 除了登入 API（`POST /api/auth/login`）與 Swagger API 文件端點外，其餘所有對 `/api/**` 的請求皆必須攜帶 JWT Token 才能進行存取，未帶 Token 回傳 401 Unauthorized。\n2. **角色與細粒度授權 (RBAC)**：\n   - 在 JWT 的 Payload 中寫入使用者的角色（「系統管理員 ROLE_ADMIN」與「一般用戶 ROLE_USER」）。\n   - 使用 `@PreAuthorize` 或設定安全過濾鏈，限制「客戶資料刪除」功能（`DELETE /api/customers/{id}`）必須具備 `ROLE_ADMIN` 權限；而客戶查詢與新增僅需 `ROLE_USER` 角色。\n3. **企業級全域錯誤處理與日誌**：\n   - 使用 `@RestControllerAdvice` 建立全域例外處理器，處理諸如驗證失敗 (`MethodArgumentNotValidException`)、身分驗證失敗與自訂的業務例外。\n   - 回傳符合 RFC 7807 標準的 `ProblemDetail` 物件，使錯誤 JSON 結構保持一致。\n   - 在關鍵安全與業務端點加上符合生產級別的 `@Slf4j` 結構化日誌記錄。\n4. **API 文件保護 (OpenAPI / Swagger)**：\n   - 整合 `springdoc-openapi` 以自動掃描產生 Swagger UI，並在 Swagger 設定中配置 Bearer Token 支援，讓前端能在網頁中帶入 Token 進行測試。限制 Swagger 文件的存取權限。\n\n【程式碼與測試規範】\n- 程式碼需有中文註解，詳細說明安全配置、Token 簽發、RBAC 控制的邏輯。\n- 專案需相容於 Windows 環境下的啟動與測試。"
        }
      ],
      "tasks": [
        {
          "id": "u4-t1",
          "label": "在 pom.xml 加入 springdoc-openapi 依賴並啟動應用，確認可存取 /swagger-ui.html"
        },
        {
          "id": "u4-t2",
          "label": "在 CustomerController 加上 @Operation 與 @ApiResponse 標註"
        },
        {
          "id": "u4-t3",
          "label": "建立 OpenApiConfig 設定全域 API 資訊"
        },
        {
          "id": "u4-t4",
          "label": "建立 ErrorResponse record 作為統一錯誤格式"
        },
        {
          "id": "u4-t5",
          "label": "建立 GlobalExceptionHandler，處理 404 與驗證失敗例外"
        },
        {
          "id": "u4-t6",
          "label": "建立 ResourceNotFoundException，在 CustomerService 找不到客戶時拋出"
        },
        {
          "id": "u4-t7",
          "label": "在 CustomerService 加上 @Slf4j，在新增、刪除操作加上適當的 INFO / WARN Log"
        },
        {
          "id": "u4-t8",
          "label": "加入 spring-boot-starter-actuator，驗證可透過 PATCH /actuator/loggers 動態調整層級"
        },
        {
          "id": "u4-t9",
          "label": "閱讀 AOP 五大元素說明，能用自己的話描述 Aspect、Advice、Pointcut 的關係"
        },
        {
          "id": "u4-t10",
          "label": "對照第一天功能清單，說明至少 3 個用到 AOP 的地方與其對應的 Advice 類型"
        },
        {
          "id": "u4-t11",
          "label": "透過 AI 提示詞引入 Security 與 JWT 相關依賴"
        },
        {
          "id": "u4-t12",
          "label": "實作 JwtUtils 產生包含角色資訊的 Token，並完成認證過濾器與 SecurityConfig 配置"
        },
        {
          "id": "u4-t13",
          "label": "實作 /api/auth/login 登入 API，並為客戶 API 設定角色權限控制（ADMIN 限制刪除）"
        }
      ],
      "materials": [
        {
          "id": "mat4",
          "type": "MD",
          "name": "pgvector_環境安裝與向量檢索指令說明",
          "desc": "向量資料庫基礎概念、SQL 向量距離計算及 pgvector 索引優化指令。"
        },
        {
          "id": "mat5",
          "type": "MD",
          "name": "JWT_架構與認證流程設計",
          "desc": "Spring Security 整合 JWT 簽發、驗證與 Filter Chain 保護 API 的完整流程架構。"
        }
      ],
      "illustrations": [
        {
          "name": "u4-1.png",
          "kind": "hero",
          "alt": "JWT Security Boundary",
          "spec": "登入、角色、OpenAPI 與 ProblemDetail"
        },
        {
          "name": "u4-2.png",
          "kind": "diagram",
          "alt": "安全請求管線",
          "spec": "流程圖：Login -> Token -> Filter Chain -> Protected API"
        },
        {
          "name": "u4-3-term.png",
          "kind": "term",
          "alt": "Spring Security、JWT、OpenAPI 與企業級錯誤處理 專業術語解釋",
          "spec": "JWT / Security Filter / ProblemDetail"
        }
      ]
    },
    {
      "id": "u5",
      "title": "React CRM 工作台與前後端整合",
      "subtitle": "建立 React 19 + Vite 前端 CRM UI，對接認證機制與客戶列表、詳情及商機看板。",
      "time": "13:00 ~ 17:00",
      "features": [
        "掌握 Node.js 環境、使用 Vite 建立 React 19 專案、JSX 語法元件結構，並學習如何以 Proxy 串接後端 API 及套用 uiuxpromax 優化前端視覺體驗。"
      ],
      "goals": [
        "理解 Node.js 與 NPM 相依性套件管理機制",
        "學會使用 Vite 初始化 React 19 專案的指令步驟",
        "掌握 JSX 語法特色與 Functional Component 元件結構",
        "理解為何需要開發代理 (Vite Proxy) 以免除 CORS 限制",
        "掌握 uiuxpromax 視覺優化要點（毛玻璃、漸層Header、微動畫與骨架屏）"
      ],
      "principle": "前後端整合的核心在於狀態的一致性與防禦性渲染。Axios Interceptor 可以自動為請求附加 JWT，並在 401 時引導重新登入；UI 上必須對 Loading、Error 及 Empty 進行完整處理，保障使用者體驗。",
      "concepts": [
        {
          "heading": "Node.js 與 React 專案建立",
          "group": "Node.js 與 React 專案建立",
          "body": "Node.js 是前端開發的執行環境，而 NPM (Node Package Manager) 是套件管理工具。在 React 19 的開發中，我們不再使用傳統手動下載 JS 檔的方式，而是使用 NPM 安裝相依套件。\n\n我們使用業界主流、極速的 Vite 作為建置工具，透過以下指令在命令列中建立專案：\n\n- 建立 Vite React 專案：`npx create-vite@latest frontend --template react`\n- 進入專案目錄：`cd frontend`\n- 安裝最新無資安漏洞的 React 19 依賴：`npm install`\n- 啟動本機開發伺服器 (Port 5173)：`npm run dev`"
        },
        {
          "heading": "JSX 語法與 Functional Component 元件結構",
          "group": "JSX 語法與元件結構",
          "body": "JSX 是 JavaScript 的語法擴充，允許我們在 JavaScript 中直接撰寫一種類似 HTML 的結構。React 19 推薦使用 Functional Component（函式元件）進行開發，相比舊版的 Class 元件更加簡潔。\n\nJSX 寫作規範與注意事項：\n\n1. 所有元件必須回傳「單一根節點」（若有多個元素，需用空標籤 `<></>` 包裹）。\n\n2. 由於 class 在 JS 中是保留字，JSX 中必須改寫為 `className`。\n\n3. HTML 事件綁定需改為 React 的小駝峰命名（例如 `onclick` 改為 `onClick`）。\n\n4. 變數與邏輯表達式可直接放在大括號 `{}` 中進行求值與渲染。\n\n**Counter.jsx — 基本 JSX 函式元件範例 (jsx)**\n```jsx\nimport React, { useState } from 'react';\n\n// 宣告一個 Functional Component 元件\nexport default function Counter({ initialCount = 0 }) {\n  // 使用 useState Hook 管理元件內部的狀態\n  const [count, setCount] = useState(initialCount);\n\n  return (\n    <div className=\"counter-container\">\n      <h3>當前計數器：{count}</h3>\n      {/* 點擊事件小駝峰命名，並使用大括號綁定 JavaScript 方法 */}\n      <button onClick={() => setCount(count + 1)}>\n        累加 +1\n      </button>\n    </div>\n  );\n}\n```"
        },
        {
          "heading": "開發端代理與後端 API 串接 (Vite Proxy)",
          "group": "Vite Proxy 前後端串接",
          "body": "在前後端分離的架構中，前端 Vite 伺服器運行在 `http://localhost:5173`，而 Spring Boot 後端運行在 `http://localhost:8080`。如果前端直接向後端發送非同步請求 (Fetch / EventSource)，會因為「同源政策 (Same-Origin Policy)」而被瀏覽器阻擋 (CORS 跨網域錯誤)。\n\n為了解決此問題，我們在 `vite.config.js` 中配置 `server.proxy` 代理轉發，將所有以 `/api` 開頭的請求，在開發環境中自動轉發至 `http://localhost:8080`。這樣前端程式碼只需填寫相對路徑即可，且完全免除了後端配置 CORS 的繁瑣設定！\n\n**vite.config.js — 配置 API 代理轉發 (javascript)**\n```javascript\nimport { defineConfig } from 'vite'\nimport react from '@vitejs/plugin-react'\n\nexport default defineConfig({\n  plugins: [react()],\n  server: {\n    port: 5173,\n    proxy: {\n      // 當前端請求 /api/ai/stream 時，Vite 自動代理為 http://localhost:8080/api/ai/stream\n      '/api': {\n        target: 'http://localhost:8080',\n        changeOrigin: true\n      }\n    }\n  }\n})\n```"
        },
        {
          "heading": "uiuxpromax 前端視覺優化指引",
          "group": "前端視覺優化指引",
          "body": "一個優秀的 Web 應用不僅要能跑，更要能 WOW 使用者。在智慧客服專案中，我們採用 uiuxpromax 的設計哲學，利用 Vanilla CSS 優化整體視覺，徹底告別單調的 MVP 樣式：\n\n- 🎨 毛玻璃效果 (Glassmorphism)：卡片使用 `backdrop-filter: blur(14px)` 搭配半透明邊框，營造精緻浮空感。\n- 🌈 漸層極光配色：Header 使用 linear-gradient(135deg, indigo, purple) 漸層配色，搭配狀態指示燈 (Pulse LED) 動態閃爍。\n- ⚡ 微懸停動畫 (Micro-interactions)：滑鼠懸停於客戶摘要、待辦任務卡片時，加入 `transform: translateY(-4px) scale(1.01)` 與 `transition` 讓卡片活起來。\n- ⏳ 骨架屏載入動畫 (Skeleton Screen)：當 AI 正在思考或呼叫 Tool 時，在對話框中顯示灰白色的骨架屏閃爍 (shimmer keyframe)，極大降地等待期間的無聊感。\n\n---\n\n📦 **uiuxpromax 技能安裝與引入方式**\n\n`uiuxpromax` 是本課程設計的 AI Agent 專屬「前端視覺優化技能」（Skill）。透過將此技能載入到你的 AI 工具中，AI 助理在幫你生成程式碼時，就會自動套用這些高級視覺效果，不需要你手動編寫。\n\n### 步驟一：建立技能檔 `SKILL.md`\n在專案的技能存放目錄下（如 `frontend/skills/uiuxpromax/` 或後端 `src/main/resources/skills/uiuxpromax/`），建立名為 `SKILL.md` 的檔案，內容如下：\n\n```markdown\n---\nname: uiuxpromax\ndescription: 專門用於 React 前端 UI/UX 視覺優化的技能，包含毛玻璃卡片、極光漸層、微懸停動畫與骨架屏載入動畫的 CSS 指引。\n---\n\n# uiuxpromax 視覺優化規範\n\n當用戶要求優化前端視覺或聊天室樣式時，必須主動套用以下 CSS 類別與設計哲學：\n- **毛玻璃 (Glassmorphism)**：使用 `.glass-card` 提供 `backdrop-filter: blur(14px)` 與半透明邊框。\n- **漸層 Header**：使用 `.gradient-header` 設計靛藍到紫色的 linear-gradient 漸層。\n- **微懸停**：使用 `.micro-interaction` 於 hover 時套用 `transform: translateY(-4px) scale(1.01)`。\n- **骨架屏**：使用 `.skeleton-shimmer` 與 `@keyframes shimmer` 提供流暢的閃爍載入動畫。\n```\n\n### 步驟二：在 AI 協作工具或專案中載入\n1. **在 AI 編輯器 (如 IDE 或 Claude Code) 中**：將技能路徑加入協作工具的 `skills` 設定中，讓 Agent 在為你寫 React 專案時自動啟動此技能。\n2. **在 Spring AI 後端專案中**：透過 `SkillsTool` 掛載至 ChatClient，使 CRM 智慧助理具備動態輸出視覺樣式的能力：\n   ```java\n   // 載入 uiuxpromax 技能資源\n   SkillsTool skillsTool = SkillsTool.builder()\n           .addSkillsResources(new ClassPathResource(\"skills/uiuxpromax/SKILL.md\"))\n           .build();\n   ```\n\n### 步驟三：於前端 styles.css 寫入對應樣式\n當 AI Agent 讀取該技能後，它會自動在你生成的 React 元件中套用這些 class 類別，你只需在前端 styles.css 中補上對應的 CSS 實現即可。"
        },
        {
          "heading": "AI Agent 提示詞 — 建立 React 前端專案",
          "group": "AI 提示詞 — React 前端",
          "body": "理解 React 專案結構、Proxy 原理與視覺優化要點後，複製以下提示詞給 AI Agent，從零建立聊天室的前端專案骨架。\n\n**AI Agent 提示詞 (text)**\n```text\n請幫我建立 CRM 智慧工作台的 React 前端專案：\n1. 在專案根目錄用 Vite 建立 React 19 專案：npx create-vite@latest frontend --template react，並執行 npm install。\n2. 設定 vite.config.js 的 server.proxy，把所有 /api 開頭的請求代理到 http://localhost:8080，解決前後端分離的 CORS 問題。\n3. 建立 App 頁面骨架：包含漸層色 Header（linear-gradient 靛藍到紫色）、毛玻璃卡片容器（backdrop-filter: blur），以及聊天視窗的占位區塊。\n4. CSS 請加上骨架屏（skeleton shimmer）載入動畫，之後聊天室等待 AI 回覆時會用到。\n5. 元件需有中文註解。\n完成後請告訴我如何啟動開發伺服器，以及如何確認 proxy 代理有生效。\n```"
        }
      ],
      "prompt": "請接續 Unit 4 的實作內容。現在我們要進入 Unit 5：React CRM 工作台與前後端整合。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### AI Agent 提示詞 — 建立 React 前端專案\n\n【開發角色與架構要求】\n你是一位精通 React 19 與 Vite 的前端架構專家。請協助我在專案的根目錄下，建立一個用來串接 Spring Boot 後端的 CRM 前端 React 應用專案。開發環境為 Windows 10/11，使用 PowerShell 7+。\n\n【實作功能與前端規格】\n1. **專案初始化與結構設計**：\n   - 建立 Vite + React + TypeScript 的前端骨架，目錄結構規劃為 `src/components`、`src/api`、`src/context`、`src/hooks` 等模組。\n   - 確保 package.json 依賴最新且無資安漏洞的 React 19 核心套件。\n2. **Vite Proxy 開發代理配置**：\n   - 修改 `vite.config.js` 設定，將所有 `/api` 開頭的請求代理轉發至後端 Spring Boot 服務（`http://localhost:8080`），解決開發環境前後端分離的 CORS 跨網域存取限制。\n3. **頁面佈局與 UIUX Pro Max 視覺優化**：\n   - 建立一個高品質的應用程式基礎佈局（Layout）。\n   - **Header 區塊**：使用 CSS 漸層色設計（靛藍 `#4f46e5` 到紫色 `#9333ea`），右側包含業務人員登入資訊與狀態指示燈（動態閃爍的 LED 狀態效果）。\n   - **毛玻璃卡片容器 (Glassmorphism)**：使用 Vanilla CSS 在資訊卡片上套用磨砂玻璃濾鏡效果（`backdrop-filter: blur(14px)`），搭配半透明邊框與柔和陰影。\n   - **微懸停互動 (Micro-interactions)**：對所有可點擊或 hover 的客戶卡片、任務清單套用微動畫，以 `transform: translateY(-4px)` 提升互動質感。\n   - **骨架屏載入動畫 (Skeleton Shimmer)**：建立等待狀態的骨架屏 CSS 動畫，在等待 API 資料或 AI 回覆時呈現流暢的漸變閃爍載入效果。\n4. **程式碼與狀態規範**：\n   - 所有撰寫的前端元件（JSX/TSX）、CSS 樣式表必須提供中文註解，詳細說明 proxy 配置、CSS 動畫原理與元件渲染邏輯。\n   - 提供啟動開發伺服器的指令，以及在瀏覽器開發者工具中確認 proxy 代理是否生效的驗證步驟。",
      "promptMac": "請接續 Unit 4 的實作內容。現在我們要進入 Unit 5：React CRM 工作台與前後端整合。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### AI Agent 提示詞 — 建立 React 前端專案\n\n【開發角色與架構要求】\n你是一位精通 React 19 與 Vite 的前端架構專家。請協助我在專案的根目錄下，建立一個用來串接 Spring Boot 後端的 CRM 前端 React 應用專案。開發環境為 macOS 系統。\n\n【實作功能與前端規格】\n1. **專案初始化與結構設計**：\n   - 建立 Vite + React + TypeScript 的前端骨架，目錄結構規劃為 `src/components`、`src/api`、`src/context`、`src/hooks` 等模組。\n   - 確保 package.json 依賴最新且無資安漏洞的 React 19 核心套件。\n2. **Vite Proxy 開發代理配置**：\n   - 修改 `vite.config.js` 設定，將所有 `/api` 開頭的請求代理轉發至後端 Spring Boot 服務（`http://localhost:8080`），解決開發環境前後端分離的 CORS 跨網域存取限制。\n3. **頁面佈局與 UIUX Pro Max 視覺優化**：\n   - 建立一個高品質的應用程式基礎佈局（Layout）。\n   - **Header 區塊**：使用 CSS 漸層色設計（靛藍 `#4f46e5` 到紫色 `#9333ea`），右側包含業務人員登入資訊與狀態指示燈（動態閃爍的 LED 狀態效果）。\n   - **毛玻璃卡片容器 (Glassmorphism)**：使用 Vanilla CSS 在資訊卡片上套用磨砂玻璃濾鏡效果（`backdrop-filter: blur(14px)`），搭配半透明邊框與柔和陰影。\n   - **微懸停互動 (Micro-interactions)**：對所有可點擊或 hover 的客戶卡片、任務清單套用微動畫，以 `transform: translateY(-4px)` 提升互動質感。\n   - **骨架屏載入動畫 (Skeleton Shimmer)**：建立等待狀態的骨架屏 CSS 動畫，在等待 API 資料或 AI 回覆時呈現流暢的漸變閃爍載入效果。\n4. **程式碼與狀態規範**：\n   - 所有撰寫的前端元件（JSX/TSX）、CSS 樣式表必須提供中文註解，詳細說明 proxy 配置、CSS 動畫原理與元件渲染邏輯。\n   - 提供啟動開發伺服器的指令，以及在瀏覽器開發者工具中確認 proxy 代理是否生效的驗證步驟。",
      "prompts": [
        {
          "title": "高級 AI 協作提示詞大師範本（Windows 版）",
          "note": "可直接交給 Codex / Gemini 進行輔助開發",
          "text": "請接續 Unit 4 的實作內容。現在我們要進入 Unit 5：React CRM 工作台與前後端整合。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### AI Agent 提示詞 — 建立 React 前端專案\n\n【開發角色與架構要求】\n你是一位精通 React 19 與 Vite 的前端架構專家。請協助我在專案的根目錄下，建立一個用來串接 Spring Boot 後端的 CRM 前端 React 應用專案。開發環境為 Windows 10/11，使用 PowerShell 7+。\n\n【實作功能與前端規格】\n1. **專案初始化與結構設計**：\n   - 建立 Vite + React + TypeScript 的前端骨架，目錄結構規劃為 `src/components`、`src/api`、`src/context`、`src/hooks` 等模組。\n   - 確保 package.json 依賴最新且無資安漏洞的 React 19 核心套件。\n2. **Vite Proxy 開發代理配置**：\n   - 修改 `vite.config.js` 設定，將所有 `/api` 開頭的請求代理轉發至後端 Spring Boot 服務（`http://localhost:8080`），解決開發環境前後端分離的 CORS 跨網域存取限制。\n3. **頁面佈局與 UIUX Pro Max 視覺優化**：\n   - 建立一個高品質的應用程式基礎佈局（Layout）。\n   - **Header 區塊**：使用 CSS 漸層色設計（靛藍 `#4f46e5` 到紫色 `#9333ea`），右側包含業務人員登入資訊與狀態指示燈（動態閃爍的 LED 狀態效果）。\n   - **毛玻璃卡片容器 (Glassmorphism)**：使用 Vanilla CSS 在資訊卡片上套用磨砂玻璃濾鏡效果（`backdrop-filter: blur(14px)`），搭配半透明邊框與柔和陰影。\n   - **微懸停互動 (Micro-interactions)**：對所有可點擊或 hover 的客戶卡片、任務清單套用微動畫，以 `transform: translateY(-4px)` 提升互動質感。\n   - **骨架屏載入動畫 (Skeleton Shimmer)**：建立等待狀態的骨架屏 CSS 動畫，在等待 API 資料或 AI 回覆時呈現流暢的漸變閃爍載入效果。\n4. **程式碼與狀態規範**：\n   - 所有撰寫的前端元件（JSX/TSX）、CSS 樣式表必須提供中文註解，詳細說明 proxy 配置、CSS 動畫原理與元件渲染邏輯。\n   - 提供啟動開發伺服器的指令，以及在瀏覽器開發者工具中確認 proxy 代理是否生效的驗證步驟。"
        },
        {
          "title": "API Client 與 JWT 認證管理",
          "note": "建立可重用的 API 呼叫層，自動處理 JWT token 的存儲、帶入與過期重導",
          "text": "請在 React 前端專案中建立統一的 API Client 模組與認證狀態管理：\n\n### 步驟一：安裝 Axios\nnpm install axios\n\n### 步驟二：建立 API Client（src/api/client.js）\n請建立一個 Axios instance，功能如下：\n1. baseURL 設為 '/api'（透過 Vite proxy 代理到後端）\n2. 請求攔截器（request interceptor）：每次請求自動從 localStorage 讀取 JWT token，加到 Authorization: Bearer <token> 標頭\n3. 回應攔截器（response interceptor）：若回傳 401，自動清除 localStorage 中的 token 並導向登入頁\n4. 匯出常用的 get、post、put、delete 方法\n\n### 步驟三：建立 Auth Context（src/context/AuthContext.jsx）\n用 React Context + useReducer 管理認證狀態：\n- state：{ user, token, isAuthenticated }\n- actions：LOGIN（存 token 到 localStorage 並更新 state）、LOGOUT（清除 token 並重導登入頁）\n- 提供 useAuth() 自訂 hook 供各元件取用\n- 應用載入時從 localStorage 還原 token，若 token 存在則呼叫 /api/auth/me 驗證有效性\n\n### 步驟四：建立 API Service 模組\n在 src/api/ 目錄下建立對應的 service 檔案：\n- authService.js：login(username, password)、getCurrentUser()\n- customerService.js：getCustomers(params)、getCustomerById(id)、searchCustomers(filters)\n- opportunityService.js：getOpportunities(filters)、updateStage(id, stage)\n- interactionService.js：getInteractionsByCustomer(customerId)、createInteraction(customerId, data)\n\n每個 service 使用步驟二的 API client，回傳 Promise。\n元件需有中文註解。"
        },
        {
          "title": "React CRM 頁面 — 登入、Dashboard、客戶列表與商機看板",
          "note": "建立 CRM 系統的核心前端頁面，串接後端 API 顯示真實資料",
          "text": "請在 React 專案中建立 CRM 系統的核心頁面，使用 React Router 管理路由：\n\n### 步驟一：安裝依賴與設定路由\nnpm install react-router-dom\n\n在 App.jsx 設定路由結構：\n- /login → LoginPage（未登入時的預設頁面）\n- / → DashboardPage（需登入，受 PrivateRoute 保護）\n- /customers → CustomerListPage\n- /customers/:id → CustomerDetailPage\n- /opportunities → OpportunityBoardPage\n\n建立 PrivateRoute 元件：未登入時自動重導到 /login。\n\n### 步驟二：登入頁（LoginPage.jsx）\n- 帳號與密碼輸入欄位，使用 controlled component\n- 送出後呼叫 POST /api/auth/login，成功後將 JWT 存入 localStorage 並導向 Dashboard\n- 登入失敗顯示紅色錯誤訊息\n- 提供測試帳號提示（user / password 或 admin / password）\n- 毛玻璃卡片樣式，置中於畫面\n\n### 步驟三：CRM Dashboard（DashboardPage.jsx）\n- 頂部顯示歡迎訊息與登出按鈕\n- 四張統計卡片：客戶總數、本月新增客戶、進行中商機數、商機總金額\n- 統計資料從 API 取得（GET /api/customers 計算總數，GET /api/opportunities 計算金額與數量）\n- 載入中顯示骨架屏動畫\n- 卡片有 hover 微動畫效果（translateY + shadow）\n\n### 步驟四：客戶列表頁（CustomerListPage.jsx）\n- 顯示客戶資料表格：公司名稱、產業別、分級（VIP 用金色標籤）、負責業務\n- 頂部搜尋列：關鍵字搜尋 + 產業篩選下拉 + 分級篩選下拉\n- 篩選條件變動時即時呼叫 GET /api/customers/search 更新列表\n- 點擊客戶名稱導向客戶詳情頁\n- 空資料顯示「尚無符合條件的客戶」提示\n- 載入中、載入失敗都有明確的 UI 狀態\n\n### 步驟五：客戶詳情頁（CustomerDetailPage.jsx）\n- 頂部客戶基本資訊卡片（名稱、產業、分級、負責業務、Email、電話）\n- Tab 切換區塊：\n  - 「聯絡人」Tab：列出該客戶所有聯絡人（姓名、職稱、Email）\n  - 「互動紀錄」Tab：時間軸形式顯示互動紀錄（類型圖示 + 日期 + 摘要），最新在上\n  - 「商機」Tab：列出該客戶所有商機（名稱、金額、階段徽章、預計成交日）\n  - 「AI 建議」Tab：留空占位，Unit 6 串接 AI 聊天室\n- 使用 useParams() 取得 customerId，載入時呼叫對應 API\n\n### 步驟六：商機看板（OpportunityBoardPage.jsx）\n- 橫向 Kanban 看板，六個欄位對應六個商機階段（PROSPECTING → CLOSED_LOST）\n- 每筆商機顯示為卡片：商機名稱、客戶名稱、金額、預計成交日\n- 每個階段欄位頂部顯示該階段的商機數量與金額小計\n- 提供手動拖拉或「推進階段」按鈕，呼叫 PUT /api/opportunities/{id}/stage 更新\n\n### 共同要求\n- 所有 API 失敗顯示 toast 或紅色錯誤提示\n- JWT 過期（401）自動跳回登入頁\n- 元件需有中文函式級別註解"
        }
      ],
      "tasks": [
        {
          "id": "u5-t1",
          "label": "安裝 Node.js 並執行 npm 驗證"
        },
        {
          "id": "u5-t2",
          "label": "使用 Vite 建立 React 19 專案並以 npm install 安裝"
        },
        {
          "id": "u5-t3",
          "label": "配置 vite.config.js 的 server.proxy 代理"
        },
        {
          "id": "u5-t4",
          "label": "套用 CSS 動畫實作骨架屏與毛玻璃視覺效果"
        }
      ],
      "materials": [
        {
          "id": "mat6",
          "type": "MD",
          "name": "React_與_Axios_前後端整合說明",
          "desc": "React 19 呼叫後端 API、JWT 自動攜帶與 Axios 攔截器 (Interceptors) 設計說明。"
        }
      ],
      "illustrations": [
        {
          "name": "u5-1.png",
          "kind": "hero",
          "alt": "React CRM Workspace",
          "spec": "Dashboard、列表、看板與 API Client"
        },
        {
          "name": "u5-2.png",
          "kind": "diagram",
          "alt": "前端整合架構",
          "spec": "流程圖：Login UI -> API Client -> Dashboard -> Kanban"
        },
        {
          "name": "u5-3-term.png",
          "kind": "term",
          "alt": "React CRM 工作台與前後端整合 專業術語解釋",
          "spec": "Axios Interceptor / Skeleton State / Role-based UI"
        }
      ]
    },
    {
      "id": "u6",
      "title": "Spring AI ChatClient、SSE 與 tool calling",
      "subtitle": "使用 Spring AI 建立對話助理，以 SSE 將 Token 串流傳遞給前端，並實作 Tool Calling 讓 AI 獲取 CRM 真實數據。",
      "time": "09:00 ~ 12:00",
      "features": [
        "建立 Spring AI 對話入口，理解串流輸出、對話記憶與多 session 管理。",
        "讓模型透過工具呼叫查詢即時資料或呼叫外部 API，而不是只依賴語料記憶作答。",
        "使用 React 串接後端 Server-Sent Events (SSE) 串流 API，利用原生 EventSource 與狀態管理實現即時打字機對話效果。"
      ],
      "goals": [
        "掌握 ChatClient 在 Spring AI 的使用位置",
        "理解 SSE 串流輸出的呈現方式",
        "知道如何以 sessionId 隔離不同使用者對話",
        "理解 Tool Calling 與 Function Calling 的實際用途",
        "分辨 `@Tool` 與 `ToolCallback` 的使用場景",
        "掌握 AI 查詢資料庫的安全責任邊界",
        "理解 SSE 與 WebSockets 的區別與選型",
        "使用 React 的 EventSource 實作後端 API 串接",
        "利用 React 狀態 (State) 實作流式打字渲染效果",
        "掌握流式對話中對話 Session 的前端管理與清除"
      ],
      "principle": "可信任 AI 的界線是「數字由工具算，文字由模型寫」。我們透過 System Prompt 設定邊界，以 Tool Calling 將 CRM 動態領域資料提供給 LLM，並使用 SSE 串流將 token 即時渲染至聊天介面。",
      "concepts": [
        {
          "heading": "ChatClient 在架構中的位置",
          "group": "ChatClient 基礎與對話記憶",
          "body": "這一章開始，專案從純後端工程擴展到 AI 對話應用。`ChatClient` 是 Spring AI 2.0 中相對高階、可組合的入口，負責把使用者輸入、Advisor、Tools 與模型回應串成一條可讀的流程。\n\n教學上可以把它想成客服總機。總機本身不處理所有問題，但它知道該把輸入送到哪裡、要不要帶記憶、要不要叫工具。"
        },
        {
          "heading": "串流輸出為什麼重要",
          "group": "ChatClient 基礎與對話記憶",
          "body": "對使用者來說，串流輸出最大的價值不是技術炫耀，而是等待感受不同。模型可以一邊生成一邊顯示，前端不需要等整段文字全部完成才開始呈現。\n\n在這個課程範例中，前端使用 SSE 讀取後端串流結果，這也為後面的 Demo 頁面奠定互動感。"
        },
        {
          "heading": "對話記憶設計",
          "group": "ChatClient 基礎與對話記憶",
          "body": "- 不同使用者應對應不同 `sessionId`\n- 記憶不應全站共用，否則會互相污染上下文\n- 清除對話時應一併重建 session 識別值"
        },
        {
          "heading": "ChatClient.Builder 初始化方式",
          "group": "ChatClient 基礎與對話記憶",
          "body": "Spring AI 不直接 `new ChatClient()`，而是注入 `ChatClient.Builder`，讓框架管理模型連線設定。`defaultSystem()` 會為這個客戶端的所有對話預設一個系統提示詞，定義 AI 的角色。\n\n**ChatController.java 建構子 (java)**\n```java\n@RestController\npublic class ChatController {\n    private final ChatClient chatClient;\n\n    // 注入 ChatClient.Builder（由 Spring AI 自動配置）\n    public ChatController(ChatClient.Builder chatClientBuilder) {\n        this.chatClient = chatClientBuilder\n            .defaultSystem(\"你是一個親切的 CRM 智慧助手，\" +\n                           \"請用中文回答使用者問題，並以客戶管理與銷售流程為優先範疇。\")\n            .build();\n    }\n}\n```"
        },
        {
          "heading": "Groq API 端點設定（application.yml）",
          "group": "ChatClient 基礎與對話記憶",
          "body": "Groq 提供與 OpenAI 相容的 API 介面，因此可以使用 Spring AI 的 OpenAI Starter，只需把 Chat 端點的 base-url 覆寫為 Groq 即可。\n\n- Chat：前往 console.groq.com 建立免費 API Key，設定 `$env:GROQ_API_KEY=\"gsk_xxx...\"`\n- Embedding：前往 voyageai.com 建立 API Key（每月 50M tokens 免費），設定 `$env:VOYAGE_API_KEY=\"pa-xxx...\"`\n- Voyage AI 提供 OpenAI 相容介面，直接設定在 openai.embedding 區塊即可，不需要額外的 Starter 依賴\n\n**application.yml — Spring AI 模型設定 (yaml)**\n```yaml\nspring:\n  ai:\n    # OpenAI Starter 同時處理 Chat（Groq）與 Embedding（Voyage AI）\n    openai:\n      chat:\n        base-url: https://api.groq.com/openai/v1   # 覆寫為 Groq 端點\n        api-key: ${GROQ_API_KEY:your_groq_api_key}\n        options:\n          model: llama-3.3-70b-versatile  # Groq 免費高速模型\n          temperature: 0.7\n      # Voyage AI 提供 OpenAI 相容介面，直接設定 embedding 區塊\n      embedding:\n        base-url: https://api.voyageai.com/v1\n        api-key: ${VOYAGE_API_KEY:your_voyage_api_key}\n        options:\n          model: voyage-3-lite  # 每月 50M tokens 免費，512 維度\n```"
        },
        {
          "heading": "MessageChatMemoryAdvisor 對話記憶",
          "group": "ChatClient 基礎與對話記憶",
          "body": "大模型本身是無狀態的，不記得上一句說什麼。`MessageChatMemoryAdvisor` 會在發送請求前自動帶入歷史訊息，並在回覆後存入記憶。`sessionId` 讓不同使用者的對話記憶互相隔離。\n\n**SSE 串流 + 對話記憶 (java)**\n```java\nprivate final InMemoryChatMemory chatMemory = new InMemoryChatMemory();\n\n@GetMapping(value = \"/stream\", produces = MediaType.TEXT_EVENT_STREAM_VALUE)\npublic Flux<String> streamChat(\n        @RequestParam String message,\n        java.security.Principal principal) {\n    String username = principal.getName(); // 從 Spring Security 取得當前登入的使用者名稱\n    return this.chatClient.prompt()\n            .user(message)\n            // 載入對話記憶 Advisor\n            .advisors(MessageChatMemoryAdvisor.builder(this.chatMemory).build())\n            // 傳入會話 ID，不同 sessionId 的記憶彼此隔離\n            .advisors(spec -> spec.param(\"chat_memory_conversation_id\", username))\n            .stream()\n            .content();  // 回傳 Flux<String> 供 SSE 串流\n```"
        },
        {
          "heading": "ChatClient 與 Advisor 深入解析",
          "group": "ChatClient 基礎與對話記憶",
          "body": "本節為您整理 ChatClient 與 Advisor 的關鍵配置與內建元件說明，幫助您深入理解架構：\n\n<div style=\"margin-top: 16px;\"><strong style=\"color: var(--primary-color); display: block; margin-bottom: 8px;\">1. ChatClient.Builder 常用預設設定：</strong><ul style=\"list-style-type: disc; margin-left: 20px; margin-bottom: 16px; line-height: 1.8;\"><li style=\"margin-bottom: 6px;\"><code>defaultSystem(String)</code>：設定預設的系統提示詞，定義 AI 角色與回覆風格（如親切的智慧助手）。</li><li style=\"margin-bottom: 6px;\"><code>defaultUser(String)</code>：設定預設的使用者輸入提示詞。</li><li style=\"margin-bottom: 6px;\"><code>defaultOptions(ChatOptions)</code>：設定預設的對話參數，例如溫度值（temperature）、使用的模型等。</li><li style=\"margin-bottom: 6px;\"><code>defaultAdvisors(Advisor...)</code>：設定預設的攔截增強器，例如記憶元件（MessageChatMemoryAdvisor）或 RAG 元件（QuestionAnswerAdvisor），使所有對話自動啟用該功能。</li><li style=\"margin-bottom: 6px;\"><code>defaultFunctions(String...)</code>：設定預設啟用的 Tool/Function Calling 工具 Bean 名稱。</li><li style=\"margin-bottom: 6px;\"><code>defaultTools(Object...)</code> / <code>defaultToolCallbacks(ToolCallback...)</code>：設定預設啟用的工具物件或回呼。</li></ul></div>\n\n<div style=\"margin-top: 16px;\"><strong style=\"color: var(--primary-color); display: block; margin-bottom: 8px;\">2. Spring AI 最新的內建 Advisor：</strong><ul style=\"list-style-type: disc; margin-left: 20px; margin-bottom: 16px; line-height: 1.8;\"><li style=\"margin-bottom: 6px;\"><code>MessageChatMemoryAdvisor</code>：最常用的對話記憶 Advisor，負責自動讀取指定 sessionId 的對話歷史並注入上下文，且在回答後自動將新問答存回記憶庫。</li><li style=\"margin-bottom: 6px;\"><code>PromptChatMemoryAdvisor</code>：文字範本式對話記憶 Advisor，將對話歷史格式化為一段文字並透過 Prompt 變數填入，適用於不支援多輪 Message 格式的特殊模型。</li><li style=\"margin-bottom: 6px;\"><code>QuestionAnswerAdvisor</code>：RAG 檢索增強生成 Advisor，自動將使用者提問向量化並至 VectorStore 檢索相關知識片段，作為上下文注入 Prompt 以降低幻覺。</li><li style=\"margin-bottom: 6px;\"><code>SimpleLoggerAdvisor</code>：日誌記錄 Advisor，在調試時可自動將完整 Prompt、參數配置與模型回應印出至 Log。</li><li style=\"margin-bottom: 6px;\"><code>VectorStoreChatMemoryAdvisor</code>：基於向量資料庫的持久化對話記憶 Advisor，適合極大規模或長期對話歷史的語意檢索與儲存。</li><li style=\"margin-bottom: 6px;\"><code>SafeGuardAdvisor</code>：內容安全防護 Advisor，用於在發送給 LLM 前或回應給用戶前，攔截與過濾敏感詞或不當發言。</li></ul></div>"
        },
        {
          "heading": "串流回應骨架",
          "group": "ChatClient 基礎與對話記憶",
          "body": "**ChatClient 鏈式呼叫示意 (java)**\n```java\nreturn chatClient.prompt()\n    .user(message)\n    .advisors(memoryAdvisor)\n    .stream()\n    .content();\n```"
        },
        {
          "heading": "AI Agent 提示詞 — 建立 ChatClient 對話入口",
          "group": "ChatClient 基礎與對話記憶",
          "body": "理解上述原理後，複製以下提示詞給 AI Agent，引導它在 Day 1 完成的電商後端上，加入本章的 AI 對話與串流功能。\n\n**AI Agent 提示詞 (text)**\n```text\n請在現有的 Spring Boot CRM 專案中加入 AI 對話功能\n\n1. 請在現有的 Spring Boot CRM 專案中加入 AI 對話功能\n2. 設定 Groq 的 base-url，模型用 openai/gpt-oss-120b，API Key 為 \"xxxxxxx\"。\n3. 系統提示詞：「你是一個親切的 CRM 智慧助手」。\n4. 使用 SSE串流回覆的API，並掛上記憶，以 sessionId 參數隔離不同使用者的對話記憶。\n```"
        },
        {
          "heading": "Spring Boot 啟動驗證範例",
          "group": "ChatClient 基礎與對話記憶",
          "body": "進入 Day 2 前，應用必須已經能穩定啟動。這裡的驗證重點不是只看到 Tomcat 起來，而是 Flyway 已成功套用，Spring Boot 也完成啟動。\n\n- 最低需求：資料庫容器已先啟動\n- 最低需求：`.env` 或環境變數中的 API Key 已載入\n- 最低需求：JDK 21 與 Maven 3.9+ 已完成驗證\n\n**可接受的啟動日誌範例 (text)**\n```text\nPS D:\\GitHub\\learn-spring> mvn spring-boot:run\n...\nFlyway Community Edition 10.x by Redgate\nSuccessfully validated 2 migrations\nCurrent version of schema \"public\": 2\nTomcat started on port 8080 (http) with context path \"/\"\nStarted LearnSpringApplication in 8.532 seconds\n```"
        },
        {
          "heading": "啟動日誌判讀方式",
          "group": "ChatClient 基礎與對話記憶",
          "body": "- 必須同時看到 `Successfully validated` 或 `Successfully applied` 類似 Flyway 訊息\n- 必須看到 `Tomcat started on port 8080` 或等效埠號資訊\n- 必須看到 `Started LearnSpringApplication` 才算完整啟動\n- 若停在 DataSource、Flyway 或 API Key 相關錯誤，先回頭確認環境與設定，而不是直接進行 Day 2 測試"
        },
        {
          "heading": "常見錯誤與排查",
          "group": "常見錯誤與排查",
          "body": "- 啟動時卡在 API Key 相關錯誤，先確認 `.env` 是否真的被載入到目前 PowerShell session\n- 若 Flyway 報資料庫連線失敗，先確認資料庫容器是否還在 `Up` 狀態\n- 若 8080 埠被占用，可先關閉既有應用或調整 Spring Boot 埠號\n- 若只看到編譯錯誤而不是 Spring 日誌，先回頭處理 Java 或 Maven 環境"
        },
        {
          "heading": "工具呼叫的真正價值",
          "group": "Tool Calling 工具呼叫",
          "body": "工具呼叫的本質，不是讓模型變強，而是讓模型不必假裝自己知道即時資料。它可以把需要查詢、計算或呼叫外部服務的工作交給可控工具處理。\n\n對企業系統來說，這一點非常重要，因為真正的業務資料往往存在資料庫、內部 API 或受權限控制的服務中。"
        },
        {
          "heading": "@Tool 與 ToolCallback 的使用邏輯",
          "group": "Tool Calling 工具呼叫",
          "body": "- `@Tool` 適合你自己在專案內部封裝的功能\n- `ToolCallback` 適合掛接外部或唯讀工具\n- 無論是哪一種，都應該透過既有 Service 邏輯取得資料\n- 不要讓模型直接碰資料庫連線或繞過商業規則"
        },
        {
          "heading": "CustomerTools.java 完整範例",
          "group": "Tool Calling 工具呼叫",
          "body": "`@Tool` 的 `description` 欄位是寫給大模型看的，模型靠這段文字判斷「何時」要呼叫這個工具。描述越精確，模型的判斷就越準確。\n\n**CustomerTools.java (java)**\n```java\n@Component\npublic class CustomerTools {\n    private final CustomerService customerService;\n\n    public CustomerTools(CustomerService customerService) {\n        this.customerService = customerService;\n    }\n\n    /**\n     * 搜尋客戶列表或取得全部客戶\n     * @param query 搜尋關鍵字，傳空字串代表取全部\n     */\n    @Tool(description = \"查詢客戶列表。使用者詢問有哪些客戶，或要找特定客戶時呼叫此方法。\" +\n                        \"參數 query 為搜尋關鍵字，可為空值。\")\n    public List<Customer> getCustomers(String query) {\n        if (query == null || query.trim().isEmpty()) {\n            return customerService.getAllCustomers();\n        }\n        return customerService.searchCustomersByName(query);\n    }\n\n    /**\n     * 取得指定 ID 客戶的詳細資訊（等級、產業、聯絡方式）\n     */\n    @Tool(description = \"依客戶 ID 查詢詳細資訊。使用者詢問特定客戶的等級、產業或聯絡方式時呼叫。\")\n    public Optional<Customer> getCustomerDetail(\n            @ToolParam(description = \"客戶的唯一識別碼\") Long id) {\n        return customerService.getCustomerById(id);\n    }\n}\n```"
        },
        {
          "heading": "@Tool vs ToolCallback 選型原則",
          "group": "Tool Calling 工具呼叫",
          "body": "工具呼叫有兩種宣告方式，選擇依據是你是否有原始碼的修改權限。\n\n- `@Tool` — 自己開發的業務方法，直接在方法上標註，最簡單\n- `MethodToolCallback` — 第三方 JAR（無法加註解），用反射包裝現有方法\n- `FunctionToolCallback` — 需要客製化邏輯的外部工具，用 Lambda + DTO 封裝\n- 無論哪種方式，都應透過 Service 層取得資料，不讓模型直接碰資料庫連線"
        },
        {
          "heading": "MethodToolCallback 包裝第三方工具",
          "group": "Tool Calling 工具呼叫",
          "body": "當需要把第三方 JAR 的方法暴露給模型時，使用 `MethodToolCallback` 通過反射包裝，不需修改原始碼。\n\n**ProgrammaticToolConfig.java — 反射包裝第三方服務 (java)**\n```java\n@Configuration\npublic class ProgrammaticToolConfig {\n\n    @Bean\n    public ToolCallback thirdPartyWeatherTool() {\n        ThirdPartyWeatherService weatherService = new ThirdPartyWeatherService();\n\n        // 用反射找到目標方法\n        Method method = ReflectionUtils.findMethod(\n            ThirdPartyWeatherService.class, \"getRealtimeWeather\", String.class);\n\n        return MethodToolCallback.builder()\n            .toolDefinition(ToolDefinitions.builder(method)\n                .description(\"查詢指定城市的即時天氣。參數 city 為城市名稱，例如：台北、東京。\")\n                .build())\n            .toolMethod(method)\n            .toolObject(weatherService)   // 執行方法的目標物件\n            .build();\n    }\n}\n```"
        },
        {
          "heading": "FunctionToolCallback 包裝 Lambda 邏輯",
          "group": "Tool Calling 工具呼叫",
          "body": "當需要對第三方呼叫加入額外轉換邏輯時，使用 `FunctionToolCallback` 搭配 Record DTO，透過 Lambda 包裝業務邏輯。\n\n**ProgrammaticToolConfig.java — Lambda 封裝運費查詢 (java)**\n```java\n// 宣告 Request / Response DTO\npublic record ShippingRequest(\n    @JsonPropertyDescription(\"寄送目的地城市，例如 '台北', '花蓮'\") String destination\n) {}\n\npublic record ShippingResponse(double fee, String estimatedDays) {}\n\n@Bean\npublic ToolCallback shippingFeeTool() {\n    return FunctionToolCallback\n        .builder(\"queryShippingFee\", (ShippingRequest req) -> {\n            String dest = req.destination() != null ? req.destination() : \"\";\n            if (dest.contains(\"花蓮\") || dest.contains(\"離島\")) {\n                return new ShippingResponse(150.0, \"3-5天\");\n            }\n            return new ShippingResponse(80.0, \"1-2天\");\n        })\n        .description(\"查詢寄送到指定目的地的運費與預估天數。\")\n        .inputType(ShippingRequest.class)\n        .build();\n}\n```"
        },
        {
          "heading": "工具方法範例",
          "group": "Tool Calling 工具呼叫",
          "body": "**@Tool 方法示意 (java)**\n```java\n@Tool(description = \"查詢客戶詳細資料與互動記錄\")\npublic String queryProduct(String name) {\n    return productService.findProductSummary(name);\n}\n```"
        },
        {
          "heading": "AI Agent 提示詞 — 加入工具呼叫能力",
          "group": "Tool Calling 工具呼叫",
          "body": "理解工具呼叫原理與兩種宣告方式後，複製以下提示詞給 AI Agent，讓它把 Day 1 寫好的 Service 包裝成 AI 可呼叫的工具，AI 就能查到資料庫的即時資料。\n\n**AI Agent 提示詞 (text)**\n```text\n請為 CRM AI 助手加入工具呼叫（Tool Calling）能力，讓 AI 能查詢資料庫的即時資料：\n1. 建立 CustomerTools 類別，用 @Tool 註解包裝既有的 CustomerService：\n   - getCustomers(query)：查詢客戶列表，支援關鍵字搜尋，query 為空時回傳全部客戶\n   - getCustomerDetail(id)：依客戶 ID 查詢詳細資訊（等級、產業、聯絡方式）\n2. 建立 OpportunityTools 類別，用 @Tool 包裝既有的 OpportunityService：\n   - getOpportunities(customerId)：查詢指定客戶的所有銷售機會\n   - getDealValue(opportunityId)：查詢特定銷售機會的金額與成交機率\n3. 每個 @Tool 的 description 請用中文清楚描述「什麼情況下該呼叫這個工具」，這段文字是寫給大模型看的。\n4. 在 ChatController 的 ChatClient 呼叫鏈中，用 .tools() 掛上這兩個工具類別。\n完成後請示範一句測試提問（例如「台積電目前的客戶等級是什麼？有哪些進行中的銷售機會？」），並說明如何從後端日誌確認工具真的被呼叫。\n```"
        },
        {
          "heading": "工具呼叫驗證範例",
          "group": "Tool Calling 工具呼叫",
          "body": "這一章的成功標準，不是模型有回話而已，而是模型確實在回答過程中呼叫了工具，並帶回即時資料。\n\n- 若 AI 只給泛泛答案、沒有查到即時數字，通常表示工具沒有被掛載成功\n- 若工具有被呼叫但回覆異常，應回頭檢查 Service / Repository 查詢結果\n- 驗證時要同時看前端回答內容與後端工具呼叫日誌\n\n**可接受的驗證情境 (text)**\n```text\n使用者提問：無線降噪耳機 Pro 的庫存與價格是多少？\n\n預期現象：\n1. 前端收到 AI 回覆\n2. 後端日誌出現工具方法被呼叫的紀錄\n3. 回覆內容包含資料庫中的即時價格與庫存，而不是籠統描述\n```"
        },
        {
          "heading": "常見錯誤與排查",
          "group": "常見錯誤與排查",
          "body": "- AI 回覆很自然但沒有即時數字，通常代表模型沒有真的走工具\n- 工具方法有被呼叫但結果空白，先驗證 Repository 是否查得到資料\n- 若工具掛載後應用啟動失敗，先檢查方法簽名與註解設定是否正確\n- 驗證工具呼叫時要同時比對前端回覆與後端日誌，單看其中一邊容易誤判"
        },
        {
          "heading": "工具呼叫的架構定位",
          "group": "Tool Calling 工具呼叫",
          "body": "工具呼叫讓 AI 成為業務邏輯的「呼叫者」，而不是直接存取資料。不論工具背後是資料庫、內部 Service，還是第三方 API，模型看到的都是統一的工具介面。\n\n這種設計讓後端的資料來源可以隨時替換或擴充，而不影響模型的呼叫方式。"
        },
        {
          "heading": "為什麼選擇 SSE (Server-Sent Events)",
          "group": "SSE 前端串流整合",
          "body": "在 AI 聊天應用中，模型的回應是單向且持續產生的，後端需要持續把資料推給前端，而前端不需要在過程中頻繁向後端傳送資料。\n\n相較於 WebSockets 的雙向複雜協定，SSE 是基於標準 HTTP 的單向推播協定，開發簡單、開銷小，且天生支援斷線重連。Spring AI 的 `.stream()` 預設就是輸出標準的 `text/event-stream` 格式，與 SSE 完美搭配。\n\n- WebSockets：雙向通道，適用於多人遊戲、協作工具，但協定較為繁重。\n- SSE (Server-Sent Events)：單向推送，基於 HTTP，最適合大模型流式生成 (Streaming)。\n- React 連線方式：使用瀏覽器內建的 `EventSource` 物件即可建立連線，不需要引入第三方 WebSocket 庫。"
        },
        {
          "heading": "React EventSource 串接核心代碼",
          "group": "SSE 前端串流整合",
          "body": "這是在 React 中透過 `useEffect` 監聽後端 SSE 流的典型寫法。每次後端傳來新的字元片斷，我們就將其追加到當前最新的一筆訊息中。\n\n**[安全升級指引]**：由於原生 EventSource 串流連線無法直接自訂 HTTP 標頭（Header），這會導致我們在 Day 1-9 實作的 Spring Security JWT 驗證將其阻擋。為了安全地完成連線，我們必須進行漸進式升級：\n1. **修改後端**：升級後端的 `JwtAuthenticationFilter.java` 的 `parseJwt` 方法，使其除了支援從 Header 讀取外，也支援從網址 Query 參數中的 `token` 讀取並驗證 JWT。\n2. **修改前端**：在 React 前端建立 `EventSource` 時，從 `localStorage` 取得儲存的 Token，並以網址參數形式帶入。\n\n**ChatRoom.jsx — 串流接收與狀態更新 (javascript)**\n```javascript\nconst handleSend = (text) => {\n  // 1. 新增使用者的發問訊息\n  const userMsg = { id: Date.now(), sender: \"user\", text };\n  const assistantMsg = { id: Date.now() + 1, sender: \"assistant\", text: \"\" };\n  setMessages(prev => [...prev, userMsg, assistantMsg]);\n\n  // 2. 建立 SSE 連線\n  const eventSource = new EventSource(\n    `/api/ai/stream?message=${encodeURIComponent(text)}&token=${encodeURIComponent(token)}`\n  );\n\n  // 3. 監聽後端推播事件\n  eventSource.onmessage = (event) => {\n    // [注意] Spring AI 預設可能回傳字元片段，需要持續追加到最後一筆 assistant 訊息中\n    setMessages(prev => {\n      const updated = [...prev];\n      const lastIndex = updated.length - 1;\n      updated[lastIndex] = {\n        ...updated[lastIndex],\n        text: updated[lastIndex].text + event.data // 追加新收到的字元\n      };\n      return updated;\n    });\n  };\n\n  eventSource.onerror = (err) => {\n    console.error(\"SSE 串流連線中斷或結束：\", err);\n    eventSource.close(); // 發生錯誤或傳輸完畢時關閉連線\n  };\n};\n```"
        },
        {
          "heading": "流式打字機效果與自動滾動",
          "group": "SSE 前端串流整合",
          "body": "為了提升使用者體驗，聊天室通常需要具備「打字機流式輸出」與「新訊息自動滾動到底部」的功能。我們可以使用 React 的 `useRef` 與 `useEffect` 來追蹤對話列表的長度並進行滾動。\n\n**ChatRoom.jsx — 自動滾動到底部 (javascript)**\n```javascript\nconst messageEndRef = useRef(null);\n\n// 監聽訊息陣列變動，若有新訊息或新字元，自動滾動到底部\nuseEffect(() => {\n  messageEndRef.current?.scrollIntoView({ behavior: \"smooth\" });\n}, [messages]);\n\nreturn (\n  <div className=\"chat-container\">\n    <div className=\"messages-list\">\n      {messages.map(msg => (\n        <div key={msg.id} className={`message-bubble ${msg.sender}`}>\n          {msg.text || \"▍\"}\n        </div>\n      ))}\n      <div ref={messageEndRef} />\n    </div>\n  </div>\n);\n```"
        },
        {
          "heading": "完整的 ChatRoom.jsx 元件範例",
          "group": "SSE 前端串流整合",
          "body": "以下是智慧客服聊天室的 React 元件完整實作。包含 isThinking（AI 正在搜尋資料庫或呼叫工具中）狀態提示、isGenerating（AI 正在輸出）狀態鎖定、自動滾動到底部，以及 quickPrompts 快捷對話指令按鈕。\n\n**ChatRoom.jsx (jsx)**\n```jsx\nimport React, { useState, useEffect, useRef } from 'react';\nimport './ChatRoom.css';\n\n/**\n * 智慧客服聊天室元件\n * 實作與 Spring Boot 後端的 SSE (Server-Sent Events) 串流對話，\n * 支援 AI 工具呼叫狀態提示、快捷對話指令以及自動滾動。\n */\nexport default function ChatRoom() {\n  // 對話歷史紀錄\n  const [messages, setMessages] = useState([]);\n  // 輸入框中的文字內容\n  const [input, setInput] = useState('');\n  // AI 正在思考或呼叫工具的狀態（決定是否顯示載入中提示）\n  const [isThinking, setIsThinking] = useState(false);\n  // AI 正在輸出字元片段的狀態（決定是否鎖定輸入與發送按鈕）\n  const [isGenerating, setIsGenerating] = useState(false);\n  // 對話 Session 識別碼，確保不同使用者與會話的歷史記憶互相隔離\n  const [sessionId] = useState(() => `session_${Math.random().toString(36).substr(2, 9)}`);\n  // 用於控制滾動到底部的 DOM 參照\n  const messagesEndRef = useRef(null);\n\n  // 定義快捷對話指令，方便學員一鍵測試工具呼叫與 RAG 推薦\n  const quickPrompts = [\n    { label: \"🔍 查詢所有客戶\", text: \"有哪些客戶？\" },\n    { label: \"📊 查詢台積電的詳細資料\", text: \"台積電的客戶等級跟聯絡方式是什麼？\" },\n    { label: \"☀️ 台北即時天氣\", text: \"台北今天天氣如何？\" },\n    { label: \"✨ 長期記憶語意推薦\", text: \"根據我之前說過的需求，幫我推薦適合的客戶\" }\n  ];\n\n  // 監聽對話紀錄 messages 變化，自動平滑滾動到底部\n  useEffect(() => {\n    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });\n  }, [messages]);\n\n  /**\n   * 處理點擊發送按鈕或在輸入框按下 Enter 鍵的事件\n   */\n  const handleSendClick = () => {\n    if (!input.trim() || isGenerating || isThinking) return;\n    handleSend(input);\n    setInput('');\n  };\n\n  /**\n   * 建立 SSE 連線並發送對話訊息，監聽後端串流推播\n   * @param {string} text - 使用者發送的 Prompt 內容\n   */\n  const handleSend = (text) => {\n    // 1. 新增使用者的提問，以及一個初始空白的 AI 回應物件（作為打字機效果容器）\n    const userMsg = { id: Date.now(), sender: \"user\", text };\n    const assistantMsg = { id: Date.now() + 1, sender: \"assistant\", text: \"\" };\n    setMessages(prev => [...prev, userMsg, assistantMsg]);\n\n    setIsThinking(true);     // 啟動「正在思考/呼叫工具」的載入狀態\n    setIsGenerating(true);   // 鎖定發送與輸入，防止重複提交\n\n    // 2. 建立標準 SSE EventSource 連線（僅支援 GET）\n    const eventSource = new EventSource(\n      `/api/ai/stream?message=${encodeURIComponent(text)}&token=${encodeURIComponent(token)}`\n    );\n\n    // 3. 監聽後端推播事件，更新對話文字\n    eventSource.onmessage = (event) => {\n      setIsThinking(false); // 收到第一個字元片段，代表思考/工具呼叫完畢，關閉思考狀態\n      \n      setMessages(prev => {\n        const updated = [...prev];\n        const lastIndex = updated.length - 1;\n        updated[lastIndex] = {\n          ...updated[lastIndex],\n          text: updated[lastIndex].text + event.data // 逐字追加生成內容\n        };\n        return updated;\n      });\n    };\n\n    // 4. 監聽連線結束或錯誤\n    eventSource.onerror = (err) => {\n      setIsThinking(false);\n      setIsGenerating(false);\n      eventSource.close(); // 傳輸完畢或斷線時務必關閉連線，避免瀏覽器無限重連\n    };\n  };\n\n  return (\n    <div className=\"chat-window\">\n      <div className=\"chat-header\">\n        <h3>Antigravity 智慧客服聊天室</h3>\n        <span className=\"session-badge\">Session ID: {sessionId}</span>\n      </div>\n      \n      {/* 對話對話區 */}\n      <div className=\"messages-container\">\n        {messages.map(msg => (\n          <div key={msg.id} className={`msg-row ${msg.sender}`}>\n            <div className=\"avatar\">{msg.sender === 'user' ? '👤' : '🤖'}</div>\n            <div className=\"bubble\">\n              {msg.text || <span className=\"cursor\">▍</span>}\n            </div>\n          </div>\n        ))}\n        \n        {/* 展示 AI 呼叫工具或思考中的狀態提示 */}\n        {isThinking && (\n          <div className=\"msg-row assistant\">\n            <div className=\"avatar\">🤖</div>\n            <div className=\"bubble thinking\">\n              <span className=\"loading-dots\">AI 正在搜尋資料庫或呼叫工具中...</span>\n            </div>\n          </div>\n        )}\n        \n        <div ref={messagesEndRef} />\n      </div>\n\n      {/* 快速對話指令區 */}\n      <div className=\"quick-actions\">\n        {quickPrompts.map((p, idx) => (\n          <button \n            key={idx} \n            onClick={() => handleSend(p.text)} \n            disabled={isGenerating || isThinking}\n            className=\"quick-btn\"\n          >\n            {p.label}\n          </button>\n        ))}\n      </div>\n\n      {/* 輸入欄區 */}\n      <div className=\"chat-input-bar\">\n        <input \n          type=\"text\" \n          value={input} \n          disabled={isGenerating || isThinking}\n          onChange={e => setInput(e.target.value)}\n          placeholder={isGenerating || isThinking ? \"AI 正在回覆中...\" : \"請輸入您的問題...\"}\n          onKeyDown={e => e.key === 'Enter' && handleSendClick()}\n        />\n        <button onClick={handleSendClick} disabled={isGenerating || isThinking || !input.trim()}>\n          發送\n        </button>\n      </div>\n    </div>\n  );\n}\n```"
        },
        {
          "heading": "對應的 CSS 樣式支援 (ChatRoom.css)",
          "group": "SSE 前端串流整合",
          "body": "以下是聊天室的精美樣式，包含對話泡泡、思考中加載動畫、快速觸發按鈕的毛玻璃與 Hover 效果。\n\n**ChatRoom.css (css)**\n```css\n.chat-window {\n  width: 500px;\n  height: 650px;\n  display: flex;\n  flex-direction: column;\n  border: 1px solid #e0e0e0;\n  border-radius: 16px;\n  overflow: hidden;\n  box-shadow: 0 8px 24px rgba(0,0,0,0.08);\n  background: #ffffff;\n}\n.chat-header {\n  padding: 16px;\n  background: linear-gradient(135deg, #4f46e5, #3b82f6);\n  color: white;\n  display: flex;\n  justify-content: space-between;\n  align-items: center;\n}\n.chat-header h3 {\n  margin: 0;\n  font-size: 16px;\n  font-weight: 600;\n}\n.session-badge {\n  font-size: 11px;\n  background: rgba(255,255,255,0.2);\n  padding: 4px 8px;\n  border-radius: 20px;\n}\n.messages-container {\n  flex: 1;\n  padding: 16px;\n  overflow-y: auto;\n  background-color: #f8fafc;\n}\n.msg-row {\n  display: flex;\n  margin-bottom: 16px;\n  align-items: flex-start;\n}\n.msg-row.user {\n  flex-direction: row-reverse;\n}\n.avatar {\n  width: 32px;\n  height: 32px;\n  border-radius: 50%;\n  background: #e2e8f0;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  font-size: 16px;\n}\n.msg-row.user .avatar {\n  background: #dbeafe;\n}\n.msg-row.assistant .avatar {\n  background: #e0e7ff;\n}\n.bubble {\n  max-width: 70%;\n  padding: 10px 14px;\n  border-radius: 12px;\n  margin: 0 8px;\n  font-size: 14px;\n  line-height: 1.5;\n  word-break: break-all;\n}\n.msg-row.user .bubble {\n  background-color: #3b82f6;\n  color: white;\n  border-top-right-radius: 2px;\n}\n.msg-row.assistant .bubble {\n  background-color: white;\n  color: #1e293b;\n  border-top-left-radius: 2px;\n  border: 1px solid #e2e8f0;\n}\n.bubble.thinking {\n  background-color: #f1f5f9;\n  color: #64748b;\n  font-style: italic;\n}\n.loading-dots {\n  display: inline-block;\n  animation: pulse 1.5s infinite;\n}\n@keyframes pulse {\n  0%, 100% { opacity: 1; }\n  50% { opacity: 0.5; }\n}\n.cursor {\n  animation: blink 1s infinite;\n  color: #3b82f6;\n}\n@keyframes blink {\n  50% { opacity: 0; }\n}\n.quick-actions {\n  display: flex;\n  flex-wrap: wrap;\n  gap: 8px;\n  padding: 12px 16px;\n  background: #ffffff;\n  border-top: 1px solid #f1f5f9;\n}\n.quick-btn {\n  background: #f1f5f9;\n  border: 1px solid #e2e8f0;\n  color: #475569;\n  padding: 6px 12px;\n  border-radius: 20px;\n  font-size: 12px;\n  cursor: pointer;\n  transition: all 0.2s ease;\n}\n.quick-btn:hover:not(:disabled) {\n  background: #e2e8f0;\n  color: #0f172a;\n}\n.quick-btn:disabled {\n  opacity: 0.6;\n  cursor: not-allowed;\n}\n.chat-input-bar {\n  display: flex;\n  padding: 16px;\n  background: #ffffff;\n  border-top: 1px solid #e2e8f0;\n  gap: 8px;\n}\n.chat-input-bar input {\n  flex: 1;\n  padding: 10px 14px;\n  border: 1px solid #cbd5e1;\n  border-radius: 8px;\n  outline: none;\n  font-size: 14px;\n}\n.chat-input-bar input:focus {\n  border-color: #3b82f6;\n}\n.chat-input-bar button {\n  background: #3b82f6;\n  color: white;\n  border: none;\n  padding: 0 18px;\n  border-radius: 8px;\n  font-weight: 500;\n  cursor: pointer;\n  transition: background 0.2s;\n}\n.chat-input-bar button:hover:not(:disabled) {\n  background: #2563eb;\n}\n.chat-input-bar button:disabled {\n  background: #cbd5e1;\n  cursor: not-allowed;\n}\n```"
        },
        {
          "heading": "對話中客戶摘要、商機、建議行動的卡片元件呈現方式",
          "group": "SSE 前端串流整合",
          "body": "在 CRM 智慧工作台中，AI 回應不應只是死板的 Markdown 文字，更應該在適當時機，動態將對應的「客戶摘要卡片、商機卡片、建議行動卡片」嵌入到對話流中，提昇 UI/UX 質感。\n\n前端 ChatRoom.jsx 採用「自動偵測與條件渲染」機制來實作此功能：\n\n1. 串流偵測 (detectAndAttachCards)：當前端 EventSource 接收到後端 AI 推播的字元片段時，會將目前的完整對話內容 (fullResponse) 傳入進行關鍵字匹配。當匹配到客戶名稱 (如台積電、聯發科)、或涉及商機關鍵字且提及特定客戶時，自動在訊息物件中附加對應的 `cards` 資料。\n\n2. 條件渲染 (renderCards)：在 React 渲染訊息列表時，如果訊息物件含有 `cards` 屬性，則呼叫 `renderCards` 方法，根據 `cards.cardType` 分別渲染出 <CustomerSummaryCard />、<OpportunityCard />、或 <ActionCard />。\n\n**ChatRoom.jsx — 核心動態識別與元件渲染邏輯 (javascript)**\n```javascript\n/**\n * 偵測 AI 回覆內容並自動對應卡片數據\n */\nconst detectAndAttachCards = (text) => {\n  // 1. 客戶摘要卡片或建議行動卡片偵測\n  const matchedCustomers = knownCustomers.filter(c => text.includes(c.name));\n  if (matchedCustomers.length > 0) {\n    if (text.includes(\"建議\") || text.includes(\"下一步\")) {\n      return { cardType: \"actions\", list: matchedCustomers };\n    }\n    return { cardType: \"customers\", list: matchedCustomers };\n  }\n\n  // 2. 商機卡片偵測\n  if (text.includes(\"商機\") || text.includes(\"機會\") || text.includes(\"銷售\")) {\n    return { cardType: \"opportunities\", list: [/* 來自後端查出的商機明細 */] };\n  }\n  return null;\n};\n\n/**\n * 條件式渲染不同的 React 元件卡片\n */\nconst renderCards = (cards) => {\n  if (!cards) return null;\n  switch (cards.cardType) {\n    case \"customers\":\n      return <div className=\"customers-list\">{/* 渲染客戶摘要卡片 */}</div>;\n    case \"opportunities\":\n      return <div className=\"opportunities-list\">{/* 渲染商機卡片 */}</div>;\n    case \"actions\":\n      return <div className=\"actions-list\">{/* 渲染建議行動卡片 */}</div>;\n    default:\n      return null;\n  }\n};\n```"
        },
        {
          "heading": "AI Agent 提示詞 — 前端串流與安全認證",
          "group": "SSE 前端串流整合",
          "body": "理解 SSE 原理、EventSource 串接方式與安全限制後，複製以下提示詞給 AI Agent，引導它為你的 React 前端聊天室實作 SSE 串流連線，並升級後端過濾器以支援安全認證。\n\n**AI Agent 提示詞 (text)**\n```text\n請在現有專案中完成以下前端與後端的串流對話安全升級：\n1. 由於原生的 EventSource 無法在 Header 中自訂 Token 進行驗證，請修改後端 `JwtAuthenticationFilter.java` 的 `parseJwt` 方法，使其除了支援從 `Authorization` 標頭讀取 Token 外，也支援從 URL 的 Query 參數（例如 `token`）中取得並驗證 Token。\n2. 在前端 `ChatRoom.jsx` 中，連接後端的 `/api/ai/stream` 串流對話介面。連線時，請從 `localStorage` 中讀取先前儲存的 JWT Token，並以網址參數形式帶入（例如 `/api/ai/stream?message=xxx&token=yyy`），以安全地建立 EventSource 串流對話連線。\n```"
        },
        {
          "heading": "系統運行展示：AI 基礎對話畫面",
          "group": "系統運行展示",
          "body": "智慧客服機器人的首頁與打招呼畫面。當使用者開啟聊天室時，機器人會主動打招呼並列出支援的功能與快捷指令，提供順暢的客服引導體驗。"
        },
        {
          "heading": "系統運行展示：客戶資訊查詢卡片",
          "group": "系統運行展示",
          "body": "當使用者詢問「有哪些客戶」時，後端 AI 模型會自動呼叫 CustomerTools 客戶查詢工具，從 JPA 資料庫中取得最新的客戶資料與等級資訊。前端收到資料後，會自動將文字清單轉換為精美的客戶卡片網格。"
        },
        {
          "heading": "系統運行展示：歷史訂單與物流追蹤",
          "group": "系統運行展示：歷史訂單與物流追蹤",
          "body": "使用者詢問其購買紀錄時（例如「我是 alice，我買了哪些東西？」），AI 精確識別用戶名稱並呼叫 OrderTools 訂單查詢工具，回傳對應的歷史訂單。前端會將其渲染出包含訂單編號、出貨狀態與黑貓物流即時配送進度的卡片。"
        },
        {
          "heading": "系統運行展示：長期記憶語意客戶推薦",
          "group": "系統運行展示",
          "body": "當使用者點擊「長期記憶語意推薦」時，後端啟動雙路 RAG 檢索，同時在 pgvector 中查找此使用者在過去幾天內聊過的興趣與偏好（例如曾提及專注於半導體產業、需要高階 VIP 客戶、對特定合作案感興趣等）。AI 結合記憶推薦 CRM 中的特定客戶或機會，前端渲染出極具說服力的語意推薦卡片與推薦理由。"
        }
      ],
      "prompt": "請接續 Unit 5 的實作內容。現在我們要進入 Unit 6：Spring AI ChatClient、SSE 與 tool calling。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### AI Agent 提示詞 — 建立 ChatClient 對話入口\n請在現有的 Spring Boot CRM 專案中加入 AI 對話功能\n\n1. 請在現有的 Spring Boot CRM 專案中加入 AI 對話功能\n2. 設定 Groq 的 base-url，模型用 openai/gpt-oss-120b，API Key 為 \"xxxxxxx\"。\n3. 系統提示詞：「你是一個親切的 CRM 智慧助手」。\n4. 使用 SSE串流回覆的API，並掛上記憶，以 sessionId 參數隔離不同使用者的對話記憶。\n\n### AI Agent 提示詞 — 加入工具呼叫能力\n請為 CRM AI 助手加入工具呼叫（Tool Calling）能力，讓 AI 能查詢資料庫的即時資料：\n1. 建立 CustomerTools 類別，用 @Tool 註解包裝既有的 CustomerService：\n   - getCustomers(query)：查詢客戶列表，支援關鍵字搜尋，query 為空時回傳全部客戶\n   - getCustomerDetail(id)：依客戶 ID 查詢詳細資訊（等級、產業、聯絡方式）\n2. 建立 OpportunityTools 類別，用 @Tool 包裝既有的 OpportunityService：\n   - getOpportunities(customerId)：查詢指定客戶的所有銷售機會\n   - getDealValue(opportunityId)：查詢特定銷售機會的金額與成交機率\n3. 每個 @Tool 的 description 請用中文清楚描述「什麼情況下該呼叫這個工具」，這段文字是寫給大模型看的。\n4. 在 ChatController 的 ChatClient 呼叫鏈中，用 .tools() 掛上這兩個工具類別。\n完成後請示範一句測試提問（例如「台積電目前的客戶等級是什麼？有哪些進行中的銷售機會？」），並說明如何從後端日誌確認工具真的被呼叫。\n\n### AI Agent 提示詞 — 前端串流與安全認證\n請在現有專案中完成以下前端與後端的串流對話安全升級：\n1. 由於原生的 EventSource 無法在 Header 中自訂 Token 進行驗證，請修改後端 `JwtAuthenticationFilter.java` 的 `parseJwt` 方法，使其除了支援從 `Authorization` 標頭讀取 Token 外，也支援從 URL 的 Query 參數（例如 `token`）中取得並驗證 Token。\n2. 在前端 `ChatRoom.jsx` 中，連接後端的 `/api/ai/stream` 串流對話介面。連線時，請從 `localStorage` 中讀取先前儲存的 JWT Token，並以網址參數形式帶入（例如 `/api/ai/stream?message=xxx&token=yyy`），以安全地建立 EventSource 串流對話連線。\n\n",
      "promptMac": "請接續 Unit 5 的實作內容。現在我們要進入 Unit 6：Spring AI ChatClient、SSE 與 tool calling。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### AI Agent 提示詞 — 建立 ChatClient 對話入口\n請在現有的 Spring Boot CRM 專案中加入 AI 對話功能\n\n1. 請在現有的 Spring Boot CRM 專案中加入 AI 對話功能\n2. 設定 Groq 的 base-url，模型用 openai/gpt-oss-120b，API Key 為 \"xxxxxxx\"。\n3. 系統提示詞：「你是一個親切的 CRM 智慧助手」。\n4. 使用 SSE串流回覆的API，並掛上記憶，以 sessionId 參數隔離不同使用者的對話記憶。\n\n### AI Agent 提示詞 — 加入工具呼叫能力\n請為 CRM AI 助手加入工具呼叫（Tool Calling）能力，讓 AI 能查詢資料庫的即時資料：\n1. 建立 CustomerTools 類別，用 @Tool 註解包裝既有的 CustomerService：\n   - getCustomers(query)：查詢客戶列表，支援關鍵字搜尋，query 為空時回傳全部客戶\n   - getCustomerDetail(id)：依客戶 ID 查詢詳細資訊（等級、產業、聯絡方式）\n2. 建立 OpportunityTools 類別，用 @Tool 包裝既有的 OpportunityService：\n   - getOpportunities(customerId)：查詢指定客戶的所有銷售機會\n   - getDealValue(opportunityId)：查詢特定銷售機會的金額與成交機率\n3. 每個 @Tool 的 description 請用中文清楚描述「什麼情況下該呼叫這個工具」，這段文字是寫給大模型看的。\n4. 在 ChatController 的 ChatClient 呼叫鏈中，用 .tools() 掛上這兩個工具類別。\n完成後請示範一句測試提問（例如「台積電目前的客戶等級是什麼？有哪些進行中的銷售機會？」），並說明如何從後端日誌確認工具真的被呼叫。\n\n### AI Agent 提示詞 — 前端串流與安全認證\n請在現有專案中完成以下前端與後端的串流對話安全升級：\n1. 由於原生的 EventSource 無法在 Header 中自訂 Token 進行驗證，請修改後端 `JwtAuthenticationFilter.java` 的 `parseJwt` 方法，使其除了支援從 `Authorization` 標頭讀取 Token 外，也支援從 URL 的 Query 參數（例如 `token`）中取得並驗證 Token。\n2. 在前端 `ChatRoom.jsx` 中，連接後端的 `/api/ai/stream` 串流對話介面。連線時，請從 `localStorage` 中讀取先前儲存的 JWT Token，並以網址參數形式帶入（例如 `/api/ai/stream?message=xxx&token=yyy`），以安全地建立 EventSource 串流對話連線。\n\n",
      "prompts": [
        {
          "title": "高級 AI 協作提示詞大師範本（Windows 版）",
          "note": "可直接交給 Codex / Gemini 進行輔助開發",
          "text": "請接續 Unit 5 的實作內容。現在我們要進入 Unit 6：Spring AI ChatClient、SSE 與 tool calling。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### AI Agent 提示詞 — 建立 ChatClient 對話入口\n請在現有的 Spring Boot CRM 專案中加入 AI 對話功能\n\n1. 請在現有的 Spring Boot CRM 專案中加入 AI 對話功能\n2. 設定 Groq 的 base-url，模型用 openai/gpt-oss-120b，API Key 為 \"xxxxxxx\"。\n3. 系統提示詞：「你是一個親切的 CRM 智慧助手」。\n4. 使用 SSE串流回覆的API，並掛上記憶，以 sessionId 參數隔離不同使用者的對話記憶。\n\n### AI Agent 提示詞 — 加入工具呼叫能力\n請為 CRM AI 助手加入工具呼叫（Tool Calling）能力，讓 AI 能查詢資料庫的即時資料：\n1. 建立 CustomerTools 類別，用 @Tool 註解包裝既有的 CustomerService：\n   - getCustomers(query)：查詢客戶列表，支援關鍵字搜尋，query 為空時回傳全部客戶\n   - getCustomerDetail(id)：依客戶 ID 查詢詳細資訊（等級、產業、聯絡方式）\n2. 建立 OpportunityTools 類別，用 @Tool 包裝既有的 OpportunityService：\n   - getOpportunities(customerId)：查詢指定客戶的所有銷售機會\n   - getDealValue(opportunityId)：查詢特定銷售機會的金額與成交機率\n3. 每個 @Tool 的 description 請用中文清楚描述「什麼情況下該呼叫這個工具」，這段文字是寫給大模型看的。\n4. 在 ChatController 的 ChatClient 呼叫鏈中，用 .tools() 掛上這兩個工具類別。\n完成後請示範一句測試提問（例如「台積電目前的客戶等級是什麼？有哪些進行中的銷售機會？」），並說明如何從後端日誌確認工具真的被呼叫。\n\n### AI Agent 提示詞 — 前端串流與安全認證\n請在現有專案中完成以下前端與後端的串流對話安全升級：\n1. 由於原生的 EventSource 無法在 Header 中自訂 Token 進行驗證，請修改後端 `JwtAuthenticationFilter.java` 的 `parseJwt` 方法，使其除了支援從 `Authorization` 標頭讀取 Token 外，也支援從 URL 的 Query 參數（例如 `token`）中取得並驗證 Token。\n2. 在前端 `ChatRoom.jsx` 中，連接後端的 `/api/ai/stream` 串流對話介面。連線時，請從 `localStorage` 中讀取先前儲存的 JWT Token，並以網址參數形式帶入（例如 `/api/ai/stream?message=xxx&token=yyy`），以安全地建立 EventSource 串流對話連線。\n\n"
        },
        {
          "title": "AI 客戶摘要與下一步建議 API",
          "note": "課程必做功能：讓 AI 根據真實 CRM 資料自動產生客戶摘要與行動建議",
          "text": "請在現有 CRM 專案中建立客戶摘要與下一步建議的 AI 功能，這是結訓專案的必做功能之一。\n\n### 步驟一：建立 CrmInsightTools\n在既有的 CustomerTools 與 OpportunityTools 之外，新建 CrmInsightTools 類別，用 @Tool 包裝以下查詢能力：\n1. getCustomerSummaryData(customerId)：一次取回客戶基本資訊、最近 5 筆互動紀錄、所有進行中商機，回傳結構化的 JSON 字串供模型閱讀\n2. getCustomerRiskSignals(customerId)：計算並回傳風險訊號清單，邏輯如下：\n   - 最近 30 天無互動紀錄 → 標記「互動冷卻」\n   - 有商機超過預計成交日仍未結案 → 標記「商機逾期」\n   - 客戶分級為 VIP 但近 60 天互動少於 2 次 → 標記「VIP 關注不足」\n3. 每個 @Tool 的 description 描述清楚觸發情境，讓模型知道何時呼叫\n\n### 步驟二：建立客戶摘要 API\n新增一個獨立的 REST endpoint（非串流）：\nGET /api/ai/customer-summary/{customerId}\n\n功能：\n1. 用 ChatClient 呼叫模型，system prompt 定義角色為「CRM 客戶分析師」\n2. user prompt 組裝為：「請根據以下客戶資料，產生一份結構化摘要，包含：(1) 客戶概況 (2) 近期活躍度 (3) 風險訊號 (4) 下一步建議行動（具體到寄信、安排會議、請主管介入或補資料）」\n3. 掛上 CrmInsightTools，讓模型透過工具取得真實資料\n4. 回傳完整的 AI 摘要文字（非串流，一次回傳）\n\n### 步驟三：前端整合\n在 CustomerDetailPage 的「AI 建議」Tab 中：\n1. 點擊「產生客戶摘要」按鈕，呼叫 GET /api/ai/customer-summary/{customerId}\n2. 載入中顯示骨架屏\n3. 摘要回傳後顯示為卡片，區分：客戶概況、風險訊號（紅色標籤）、建議行動（綠色標籤）\n4. 若 API 回傳錯誤，顯示「AI 暫時無法產生摘要，請稍後再試」\n\n### 驗證\n- 對 VIP 客戶（如台積電）產生摘要，確認包含其商機金額、互動頻率等真實數據\n- 對久未互動的客戶產生摘要，確認出現「互動冷卻」等風險訊號\n- AI 的數字（金額、次數）必須來自工具查詢結果，不可自行編造"
        }
      ],
      "tasks": [
        {
          "id": "u6-t1",
          "label": "確認模型端點與金鑰配置"
        },
        {
          "id": "u6-t2",
          "label": "完成 ChatClient 串流流程閱讀"
        },
        {
          "id": "u6-t3",
          "label": "整合 Spring Security 透過 Principal 隔離對話 Session"
        },
        {
          "id": "u6-t4",
          "label": "辨識可抽成工具的方法"
        },
        {
          "id": "u6-t5",
          "label": "整理 `@Tool` 與 `ToolCallback` 差異"
        },
        {
          "id": "u6-t6",
          "label": "記錄 1 個 AI 查客戶即時資料情境"
        },
        {
          "id": "u6-t7",
          "label": "理解 SSE (Server-Sent Events) 單向串流原理與安全限制"
        },
        {
          "id": "u6-t8",
          "label": "升級後端 JwtAuthenticationFilter 支援網址 Query Token"
        },
        {
          "id": "u6-t9",
          "label": "使用 React 實作 EventSource 帶 Token 監聽與狀態更新"
        }
      ],
      "materials": [
        {
          "id": "mat7",
          "type": "MD",
          "name": "Spring_AI_與_Tool_Calling_架構說明",
          "desc": "Spring AI ChatClient 抽象層、System Prompt 設計以及結合 Java Reflection 的 Tool Calling 機制。"
        }
      ],
      "illustrations": [
        {
          "name": "u6-1.png",
          "kind": "hero",
          "alt": "Spring AI Streaming",
          "spec": "ChatClient、SSE 與 Tool Calling"
        },
        {
          "name": "u6-2.png",
          "kind": "diagram",
          "alt": "AI 助理資料調用",
          "spec": "流程圖：User Prompt -> ChatClient -> Tool Call -> Streaming UI"
        },
        {
          "name": "u6-3-term.png",
          "kind": "term",
          "alt": "Spring AI ChatClient、SSE 與 tool calling 專業術語解釋",
          "spec": "SSE / ChatClient / Tool Calling"
        }
      ]
    },
    {
      "id": "u7",
      "title": "RAG、pgvector、MCP 與知識庫擴充",
      "subtitle": "以 pgvector 儲存產品型錄與話術進行向量檢索，並設計 MCP 服務以擴充 AI 與外部工具的整合能力。",
      "time": "13:00 ~ 17:00",
      "features": [
        "將文件切分、向量化並儲存到 pgvector，讓模型先檢索再作答，降低憑空回答風險。",
        "補充說明 MCP 在 AI 系統中的標準化接口角色與 Spring AI 的接入方式，並介紹 Skills 開放標準與 Spring AI Community 官方的 spring-ai-agent-utils 套件（SkillsTool 與 Agent 工具集）。",
        "將聊天室的對話紀錄（User Prompt 與 AI Response）非同步向量化儲存，並在發問時透過多路 RAG 同時檢索客戶知識庫與歷史對話，打造具備長效語意記憶的 AI 助手。"
      ],
      "goals": [
        "理解 RAG 與傳統純對話模型的差異",
        "掌握 Embedding、切分與檢索流程",
        "理解 pgvector 與 VectorStore 在架構中的分工",
        "理解 MCP 對跨工具 AI 生態的價值與 Spring AI 的接入方式",
        "理解 Skills 開放標準與 MCP 的角色差異",
        "認識 Spring AI Community 的 spring-ai-agent-utils 與其 Agent 工具集",
        "知道如何在 Spring Boot 中以 SkillsTool 為應用加入 Skills",
        "理解對話歷史向量化的重要性與長期記憶挑戰",
        "實作在 Spring Boot 串流結束時非同步寫入向量庫",
        "使用 Metadata 進行對話歷史隔離與精確過濾",
        "設計雙路 RAG 檢索：同時查詢知識庫與歷史記憶並進行內容合併"
      ],
      "principle": "知識庫檢索 (RAG) 解決了 LLM 的時效與內部知識盲區問題。pgvector provide SQL 級別的向量距離檢索，配合 Ingestion 切片與長期對話歷史記憶；而 MCP 則為 Agent 提供跨系統工具連結的標準介面。",
      "concepts": [
        {
          "heading": "RAG 的基本想法",
          "group": "RAG 原理與 ETL 流程",
          "body": "RAG 可以把它想成「開卷考試」。模型不再只依賴自己訓練時學過的內容，而是先去找與問題最相關的文檔片段，再根據那些片段作答。\n\n這種做法的好處是知識更新成本低、可控性高，而且可以明確限制回答只依據文件內容。"
        },
        {
          "heading": "RAG 與 Fine-Tuning 差異",
          "group": "RAG 原理與 ETL 流程",
          "body": "- RAG 是外部知識檢索，更新速度快\n- Fine-Tuning 是調整模型參數，成本較高\n- RAG 更適合企業文件、FAQ、內規等常變動內容\n- 若要降低幻覺並保留來源脈絡，RAG 更實用"
        },
        {
          "heading": "RAG vs Fine-Tuning 完整比較",
          "group": "RAG 原理與 ETL 流程",
          "body": "選擇 RAG 還是 Fine-Tuning，取決於資料更新頻率、幻覺容忍度與開發成本。對大多數企業文件場景，RAG 是首選。\n\n**RAG vs Fine-Tuning 比較 (text)**\n```text\n                    RAG（本課程方案）        Fine-Tuning（微調）\n──────────────────────────────────────────────────────────\n本質               外部知識檢索（開卷考試）   內部參數調整（閉卷考試）\n即時更新            極快（只需更新向量庫）     極慢（需要重新訓練）\n幻覺控制            極佳（可強制依文件回答）   較差（仍可能胡言亂語）\n開發成本            低                        高\n適用場景            FAQ、內規、產品文件        垂直領域語言風格微調\n```"
        },
        {
          "heading": "ETL 三步驟：文件到向量庫",
          "group": "RAG 原理與 ETL 流程",
          "body": "上傳文件到向量庫要經過 Extract → Transform → Load 三步驟。`TokenTextSplitter` 把長文切成小塊，讓每塊語意聚焦，檢索時相關度才會精準。\n\n- Extract — 用 `TextReader` 讀取檔案，封裝成 `Document` 物件\n- Transform — 用 `TokenTextSplitter` 切分，預設每塊約 800 tokens，相鄰塊重疊 100 tokens 防止語意被截斷\n- Load — `vectorStore.accept()` 自動呼叫 EmbeddingModel 產生向量並寫入 PostgreSQL\n\n**RAGController.java — 文件上傳 ETL 流程 (java)**\n```java\n@PostMapping(\"/upload\")\npublic String uploadDocument(@RequestParam(\"file\") MultipartFile file) {\n    // Extract：讀取檔案為 Document 清單\n    Resource resource = new ByteArrayResource(file.getBytes());\n    List<Document> documents = new TextReader(resource).get();\n\n    // Transform：切分長文，避免 Embedding 向量資訊過度稀釋\n    TokenTextSplitter splitter = new TokenTextSplitter();\n    List<Document> splitDocuments = splitter.apply(documents);\n\n    // Load：向量化並寫入 pgvector\n    vectorStore.accept(splitDocuments);\n\n    return \"已上傳並完成向量化，共 \" + splitDocuments.size() + \" 個片段\";\n}\n```"
        },
        {
          "heading": "向量嵌入與 pgvector",
          "group": "RAG 原理與 ETL 流程",
          "body": "Embedding 會把文字轉成固定長度的向量。在向量空間中，語意越接近，距離就越近。這讓資料庫可以用「相似度」而不是精確關鍵字比對來找內容。\n\n`pgvector` 是 PostgreSQL 的擴充套件，負責提供向量欄位與最近鄰搜尋能力；`VectorStore` 則是 Spring AI 在程式中的抽象介面。"
        },
        {
          "heading": "ETL 流程程式碼",
          "group": "RAG 原理與 ETL 流程",
          "body": "**切分與寫入向量儲存 (java)**\n```java\nTokenTextSplitter splitter = new TokenTextSplitter();\nList<Document> splitDocuments = splitter.apply(documents);\nvectorStore.accept(splitDocuments);\n```"
        },
        {
          "heading": "QuestionAnswerAdvisor 的價值",
          "group": "RAG 原理與 ETL 流程",
          "body": "Spring AI 2.0 把「檢索相關內容再把上下文送給模型」這件事收斂成 Advisor。這讓程式碼可讀性高很多，也方便後續替換檢索策略。\n\n**RAG 查詢示意 (java)**\n```java\nreturn this.chatClient.prompt()\n    .user(query)\n    .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())\n    .call()\n    .content();\n```"
        },
        {
          "heading": "AI Agent 提示詞 — 建立 RAG 知識庫",
          "group": "RAG 原理與 ETL 流程",
          "body": "理解 RAG 流程與向量檢索原理後，複製以下提示詞給 AI Agent，引導它完成文件上傳、向量化與知識庫問答兩支 API。\n\n**AI Agent 提示詞 (text)**\n```text\n請為專案加入 RAG 知識庫功能，使用 PostgreSQL pgvector 儲存向量：\n1. 在 pom.xml 加入 spring-ai-starter-vector-store-pgvector 依賴，並確認 application.yml 已設定 Voyage AI 的 embedding 端點（環境變數 VOYAGE_API_KEY）。\n2. 建立 RAGController，提供兩支 API：\n   - POST /api/rag/upload：接收上傳的文字檔，用 TextReader 讀取、TokenTextSplitter 切分成小段，最後用 vectorStore.accept() 向量化寫入資料庫\n   - GET /api/rag/query：掛上 QuestionAnswerAdvisor，讓 AI 先檢索相關文件片段再回答\n3. 程式碼需有中文函式註解。\n完成後請示範驗證流程：先上傳一份「客戶服務規範」文件，再提問「客戶服務規範是什麼？」，確認 AI 是根據文件內容回答，而不是自由發揮。\n```"
        },
        {
          "heading": "RAG 上傳與問答驗證範例",
          "group": "RAG 原理與 ETL 流程",
          "body": "RAG 驗證至少要分成兩段：先確認文件成功上傳並完成向量化，再確認查詢結果是根據文件內容回答，而不是一般常識。\n\n- 最低需求：資料庫中的 pgvector 與 VectorStore 已可正常寫入\n- 最低需求：文件上傳 API 可正常回應成功\n- 若回答沒有帶出文件中的關鍵字，先檢查上傳、切分、向量寫入與檢索流程\n- 若上傳成功但查不到內容，通常要先查向量表是否真的有資料\n\n**可接受的驗證情境 (text)**\n```text\n步驟 1：上傳 faq.txt\n內容：客戶服務規範：VIP 客戶享有 24 小時專屬客服專線，一般客戶可透過 Email 聯絡，承諾 2 工作天內回覆。\n\n步驟 2：提問\n客戶服務規範是什麼？\n\n預期回答特徵：\n- 提到 VIP 客戶有 24 小時專線\n- 提到 2 工作天內回覆\n- 回答內容明顯來自上傳文件，而不是模型自由發揮\n```"
        },
        {
          "heading": "常見錯誤與排查",
          "group": "常見錯誤與排查",
          "body": "- 文件上傳成功但查詢沒有結果，通常是切分或向量寫入階段沒有真正落表\n- 若回答內容過度籠統，先確認檢索到的 chunk 是否真的包含關鍵字\n- 若資料庫沒有 pgvector 擴充功能，向量欄位與近鄰搜尋會失敗\n- 測試 RAG 時要用明確且來自文件的句子，不要先用模型本來就可能知道的常識題"
        },
        {
          "heading": "MCP 應該怎麼理解",
          "group": "MCP 與 Skills 擴充",
          "body": "MCP 可以想成 AI 世界的標準插座。當系統中有多個模型平台、IDE 工具與資料來源時，MCP 提供一致的方式描述工具與上下文。\n\n它的價值不是取代應用程式內部工具呼叫，而是讓不同 AI 客戶端可以以標準方式接入同一批能力。\n\n- Client 是 AI 平台或整合工具\n- Host / Server 提供工具與上下文能力\n- Model 專注在理解與生成"
        },
        {
          "heading": "MCP 在 Spring AI 的接入方式（補充說明）",
          "group": "MCP 與 Skills 擴充",
          "body": "Spring AI 已內建 MCP 支援，接入成本非常低，本章以補充說明的方式帶過即可：Day 2-2 寫好的 `@Tool` Bean 完全不需要修改程式碼，Server 端加一個依賴就會自動透過 `/sse` 端點對外發布為 MCP 工具；另一個應用加上 Client 依賴，啟動時就會自動取回遠端工具清單交給 ChatClient 使用。\n\n想完整動手做的學員，可參考專案中的 `mcp/` 目錄（mcp-client-demo），其中已包含可直接執行的 MCP Client 範例與設定檔。\n\n- Server 端：加入 `spring-ai-starter-mcp-server-webmvc` 依賴，既有 @Tool Bean 自動發布為 MCP 工具\n- Client 端：加入 `spring-ai-starter-mcp-client` 依賴，SyncMcpToolCallbackProvider 啟動時自動連線取回工具\n- Server 與 Client 是兩個獨立服務，必須使用不同 port（例如 8080 / 8081），且要先啟動 Server\n- 工具掛載方式與本地工具一致：把取回的工具陣列傳入 `.tools()`，AI 就會自動選用遠端工具"
        },
        {
          "heading": "AI 呼叫遠端工具的完整流程",
          "group": "MCP 與 Skills 擴充",
          "body": "以「有哪些客戶？」為例，說明從使用者輸入到 AI 回答的跨服務執行路徑：\n\n- ① 使用者呼叫 `GET http://localhost:8081/api/mcp/chat?message=有哪些客戶`\n- ② MCP Client（port 8081）的 ChatClient 收到訊息，交由 LLM 判斷意圖\n- ③ AI 判斷需要查客戶，透過已建立的 SSE 連線，向 MCP Server（port 8080）發送 getCustomers 工具呼叫請求\n- ④ MCP Server 執行 `CustomerTools.getCustomers(\"\")` → 查詢 PostgreSQL → 回傳客戶 JSON\n- ⑤ MCP Client 收到工具執行結果，AI 組合成自然語言回答\n- ⑥ 使用者收到：「目前共有 5 筆客戶：1. 台積電（VIP）...」"
        },
        {
          "heading": "Skills 是什麼：把專業知識打包給 AI",
          "group": "MCP 與 Skills 擴充",
          "body": "Skills（Agent Skills）是 Anthropic 於 2025 年提出的開放標準：把某個領域的程序性知識（操作流程、規範、範本、輔助腳本）打包成一個資料夾，核心是一份帶有名稱與描述的 SKILL.md 說明檔。AI 平時只看到每個 Skill 的一行描述，judged 相關時才載入完整內容，這種「漸進式載入」讓模型能掛上大量專業知識而不撐爆上下文。\n\n如果說 Tool Calling 與 MCP 解決的是「AI 能呼叫什麼工具、拿到什麼資料」，Skills 解決的則是「AI 應該照什麼流程與規範做事」。兩者互補，不是替代關係。\n\n- Skill = 資料夾 + SKILL.md（frontmatter 描述）+ 選配的範本與腳本\n- 平時只載入描述，被點名才載入全文 — 漸進式揭露（progressive disclosure）\n- MCP 擴充「能力與資料」，Skills 擴充「知識與流程」"
        },
        {
          "heading": "MCP vs Skills：擴充 AI 能力的兩條路",
          "group": "MCP 與 Skills 擴充",
          "body": "- MCP：標準化「連接」— 讓 AI 客戶端以一致方式接上工具與資料來源，重點在執行能力\n- Skills：標準化「知識」— 讓 AI 依描述按需載入工作流程與規範，重點在做事方法\n- 判斷方式：要讓 AI「查得到、做得到」用 MCP / Tool Calling；要讓 AI「做得對、有章法」用 Skills\n- 實務上常見組合：Skill 內的流程指示 AI 在特定步驟呼叫 MCP 工具完成查詢或寫入"
        },
        {
          "heading": "Spring AI 如何加入 Skills：spring-ai-agent-utils",
          "group": "MCP 與 Skills 擴充",
          "body": "Skills 是 Claude、Claude Code 等 AI 客戶端原生支援的標準，但 Spring AI 核心框架尚未內建 Skills 概念。在 Spring Boot 應用中要讓 AI 具備 Skills 能力，目前的推薦做法是引用 Spring AI Community（Spring AI 官方社群組織）維護的 spring-ai-agent-utils 套件 — 它把 Claude Code 風格的 Agent 工具與 Skills 機制帶進 Java 應用。\n\n版本注意：spring-ai-agent-utils 0.9.0 要求 Spring AI 2.0.0-RC1 以上、Java 17+、Spring Boot 3.x / 4.x。本課程專案使用 Spring AI 2.0.0-M8，導入前需先把 Spring AI 升到 RC1 以上版本。\n\n- 路線一（推薦）：引用 spring-ai-agent-utils 的 SkillsTool，以 Markdown + YAML frontmatter 定義可重用知識模組\n- 路線二：自行實作最小核心 — 掃描 skills/ 目錄、解析 SKILL.md、把描述注入 system prompt（原理與套件相同）\n- Skill 檔案本身是純 Markdown、與平台無關，可在 Claude Code 與 Spring Boot 應用間共用同一份"
        },
        {
          "heading": "spring-ai-agent-utils 提供的 Agent 工具總覽",
          "group": "MCP 與 Skills 擴充",
          "body": "這個套件不只有 Skills，而是一整組 Claude Code 啟發的 Agent 工具，皆以 Spring AI 的 Tool 形式提供，可依需求個別掛入 ChatClient，讓你的 Spring Boot 應用具備接近 Coding Agent 的能力。\n\n- SkillsTool：載入 SKILL.md 知識模組，支援漸進式載入與技能路由\n- FileSystemTools / GrepTool / GlobTool：檔案讀寫編輯、純 Java 的內容搜尋與檔名匹配\n- ShellTools：讓 AI 執行 Shell 命令（生產環境務必搭配權限控管）\n- TaskTool：子代理（subagent）協調，支援 Markdown 定義的本地子代理與 A2A 遠端代理\n- AutoMemoryTools：跨對話的持久化長期記憶\n- SmartWebFetchTool / BraveWebSearchTool：網頁擷取摘要與網路搜尋\n- AskUserQuestionTool / TodoWriteTool：執行期間向使用者提問、結構化任務清單管理\n- Skill 檔案來源可搭配 anthropics/skills（Anthropic 官方開源庫）與 VoltAgent/awesome-agent-skills（社群精選 1000+），格式通用可直接取用"
        },
        {
          "heading": "加入 spring-ai-agent-utils 依賴（pom.xml）",
          "group": "MCP 與 Skills 擴充",
          "body": "套件發布於 Maven Central（groupId 為 org.springaicommunity），官方建議透過 BOM 統一管理版本，再引入核心函式庫。\n\n**pom.xml — 引入 spring-ai-agent-utils（BOM + 核心庫） (xml)**\n```xml\n<!-- 1. dependencyManagement：以 BOM 統一管理 agent-utils 版本 -->\n<dependencyManagement>\n    <dependencies>\n        <dependency>\n            <groupId>org.springaicommunity</groupId>\n            <artifactId>spring-ai-agent-utils-bom</artifactId>\n            <version>0.9.0</version>\n            <type>pom</type>\n            <scope>import</scope>\n        </dependency>\n    </dependencies>\n</dependencyManagement>\n\n<!-- 2. dependencies：引入核心函式庫（版本由 BOM 決定） -->\n<dependency>\n    <groupId>org.springaicommunity</groupId>\n    <artifactId>spring-ai-agent-utils</artifactId>\n</dependency>\n\n<!-- 注意：0.9.0 要求 Spring AI 2.0.0-RC1+ / Java 17+ / Spring Boot 3.x 或 4.x -->\n```"
        },
        {
          "heading": "ChatClient 掛上 SkillsTool 與 Agent 工具",
          "group": "MCP 與 Skills 擴充",
          "body": "把 SkillsTool 與需要的 Agent 工具透過 defaultTools 掛入 ChatClient：SkillsTool 啟動時讀取指定路徑下的 SKILL.md，平時只讓模型看到技能描述，任務匹配時才載入完整內容（漸進式載入）— 與 Claude Code 載入 Skill 的行為一致。\n\n**ChatClient 整合 SkillsTool（節錄） (java)**\n```java\n// 建立 ChatClient 時掛上 Skills 與 Agent 工具\n// skillPaths：SKILL.md 所在的資源路徑清單（例如 classpath:/skills/ 下各技能目錄）\nChatClient chatClient = chatClientBuilder\n        .defaultSystem(\"你是 CRM 智慧助手。\")\n        .defaultTools(\n                // Skills：載入 Markdown + YAML frontmatter 定義的知識模組\n                SkillsTool.builder()\n                        .addSkillsResources(skillPaths)\n                        .build(),\n                // 依需求選掛其他 Agent 工具（檔案存取、Shell 執行等）\n                FileSystemTools.builder().build()\n        )\n        .build();\n```"
        },
        {
          "heading": "為什麼需要對話歷史 RAG",
          "group": "對話歷史長期記憶",
          "body": "基本的 ChatMemory (對話記憶) 只能儲存最近幾次對話（短期記憶），且受限於大模型上下文窗口 (Context Window) 的長度與 Token 開銷。當用戶問到「幾天前我們討論過的那個方案」時，短期記憶無能為力。\n\n藉由將「歷史問答」向量化寫入資料庫，我們可以使用 RAG 搜尋「語意相似的過去對話」，動態將相關記憶塞入 Prompt，使 AI 具備橫跨數週甚至數月的長期語意記憶。\n\n- 短期記憶 (ChatMemory)：儲存於記憶體，限制 N 筆對話，成本高且會隨重啟消失。\n- 長期記憶 (History RAG)：向量化存入 PostgreSQL (pgvector)，需要時以語意搜尋檢索，開銷小且永久保存。"
        },
        {
          "heading": "串流結束時非同步寫入向量庫",
          "group": "對話歷史長期記憶",
          "body": "我們不能在對話進行中阻塞 SSE 串流，否則前端會有嚴重的卡頓感。正確的做法是監聽 `Flux` 的 `doOnComplete()` 事件，並利用非同步執行緒將「完整對話紀錄」送去向量化寫入。\n\n**RAGController.java — 監聽串流完成並非同步寫入 (java)**\n```java\n@GetMapping(value = \"/stream-chat\", produces = MediaType.TEXT_EVENT_STREAM_VALUE)\npublic Flux<String> streamChatWithHistory(\n        @RequestParam String message,\n        @RequestParam String sessionId) {\n\n    StringBuilder fullResponse = new StringBuilder();\n\n    return this.chatClient.prompt()\n            .user(message)\n            .stream()\n            .content()\n            .doOnNext(fullResponse::append) // 持續收集模型生成的字元片段\n            .doOnComplete(() -> {\n                // 串流傳輸完成後，非同步將對話紀錄寫入向量庫\n                CompletableFuture.runAsync(() -> {\n                    chatHistoryService.saveToVectorStore(sessionId, message, fullResponse.toString());\n                });\n            });\n}\n```"
        },
        {
          "heading": "對話歷史向量化 Service 實作",
          "group": "對話歷史長期記憶",
          "body": "利用 `VectorStore` 的 `add` 方法，我們把 User 問句與 AI 答句組合，並加上 metadata 以利日後查詢時，能透過 `sessionId` 隔離不同使用者的隱私。\n\n**ChatHistoryService.java — 寫入向量庫與 Metadata 過濾 (java)**\n```java\n@Service\n@Slf4j\npublic class ChatHistoryService {\n    private final VectorStore vectorStore;\n\n    public ChatHistoryService(VectorStore vectorStore) {\n        this.vectorStore = vectorStore;\n    }\n\n    public void saveToVectorStore(String sessionId, String prompt, String response) {\n        String content = \"使用者提問：\" + prompt + \"\\n助手回答：\" + response;\n        \n        Document doc = new Document(\n            content,\n            Map.of(\n                \"sessionId\", sessionId,\n                \"type\", \"chat_history\",\n                \"created_at\", System.currentTimeMillis()\n            )\n        );\n        \n        vectorStore.add(List.of(doc));\n        log.info(\"[長期記憶] 已成功向量化存入對話歷史，sessionId={}\", sessionId);\n    }\n}\n```"
        },
        {
          "heading": "雙路 RAG 檢索與上下文合併",
          "group": "對話歷史長期記憶",
          "body": "在使用者提問時，我們同時發起兩路檢索：一路查「客戶知識庫（type = 'customer_doc'）」，另一路查「該用戶的對話歷史（type = 'chat_history' AND sessionId = 'xxx'）」，最後將兩者合併作為 Context 提供給大模型。\n\n**RAGController.java — 雙路 RAG 合併檢索 (java)**\n```java\npublic List<Document> retrieveDoubleRoad(String query, String sessionId) {\n    // 第一路：客戶知識庫檢索\n    SearchRequest customerReq = SearchRequest.builder()\n            .query(query)\n            .filterExpression(\"type == 'customer_doc'\")\n            .topK(3)\n            .build();\n    List<Document> customerDocs = vectorStore.similaritySearch(customerReq);\n\n    // 第二路：該使用者歷史對話語意檢索\n    SearchRequest historyReq = SearchRequest.builder()\n            .query(query)\n            .filterExpression(\"type == 'chat_history' && sessionId == '\" + sessionId + \"'\")\n            .topK(2)\n            .build();\n    List<Document> histories = vectorStore.similaritySearch(historyReq);\n\n    // 合併結果並回傳\n    List<Document> combined = new ArrayList<>();\n    combined.addAll(customerDocs);\n    combined.addAll(histories);\n    return combined;\n}\n```"
        },
        {
          "heading": "AI Agent 提示詞 — 對話歷史長期記憶",
          "group": "對話歷史長期記憶",
          "body": "理解長期記憶的設計原理（非同步寫入、Metadata 隔離、雙路檢索）後，複製以下提示詞給 AI Agent，為聊天室加上「長期記憶」能力。\n\n**AI Agent 提示詞 (text)**\n```text\n請為聊天室加入「長期記憶」功能，把對話歷史向量化存入 pgvector：\n1. 建立 ChatHistoryService，提供 saveToVectorStore(sessionId, prompt, response) 方法：把使用者問句與 AI 回答組成一份 Document，metadata 加上 sessionId、type 為 chat_history 與建立時間，再寫入 VectorStore。\n2. 修改 SSE 串流介面：用 doOnNext 收集完整回覆內容，並在 doOnComplete 時用 CompletableFuture.runAsync 非同步寫入向量庫，避免阻塞串流回應。\n3. 實作雙路 RAG 檢索方法：使用者提問時同時查兩路 —— 客戶文件（filterExpression 過濾 type 為 customer_doc）取前 3 筆、該使用者的歷史對話（type 為 chat_history 且 sessionId 相符）取前 2 筆，合併後作為上下文交給模型。\n4. 程式碼需有中文函式註解。\n完成後請示範驗證流程：先聊「我專注半導體產業客戶、偏好 VIP 等級」，之後開新對話問「根據我之前說的需求推薦適合的客戶」，確認 AI 記得歷史偏好。\n```"
        }
      ],
      "prompt": "請接續 Unit 6 的實作內容。現在我們要進入 Unit 7：RAG、pgvector、MCP 與知識庫擴充。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### AI Agent 提示詞 — 建立 RAG 知識庫\n請為專案加入 RAG 知識庫功能，使用 PostgreSQL pgvector 儲存向量：\n1. 在 pom.xml 加入 spring-ai-starter-vector-store-pgvector 依賴，並確認 application.yml 已設定 Voyage AI 的 embedding 端點（環境變數 VOYAGE_API_KEY）。\n2. 建立 RAGController，提供兩支 API：\n   - POST /api/rag/upload：接收上傳的文字檔，用 TextReader 讀取、TokenTextSplitter 切分成小段，最後用 vectorStore.accept() 向量化寫入資料庫\n   - GET /api/rag/query：掛上 QuestionAnswerAdvisor，讓 AI 先檢索相關文件片段再回答\n3. 程式碼需有中文函式註解。\n完成後請示範驗證流程：先上傳一份「客戶服務規範」文件，再提問「客戶服務規範是什麼？」，確認 AI 是根據文件內容回答，而不是自由發揮。\n\n### AI Agent 提示詞 — 對話歷史長期記憶\n請為聊天室加入「長期記憶」功能，把對話歷史向量化存入 pgvector：\n1. 建立 ChatHistoryService，提供 saveToVectorStore(sessionId, prompt, response) 方法：把使用者問句與 AI 回答組成一份 Document，metadata 加上 sessionId、type 為 chat_history 與建立時間，再寫入 VectorStore。\n2. 修改 SSE 串流介面：用 doOnNext 收集完整回覆內容，並在 doOnComplete 時用 CompletableFuture.runAsync 非同步寫入向量庫，避免阻塞串流回應。\n3. 實作雙路 RAG 檢索方法：使用者提問時同時查兩路 —— 客戶文件（filterExpression 過濾 type 為 customer_doc）取前 3 筆、該使用者的歷史對話（type 為 chat_history 且 sessionId 相符）取前 2 筆，合併後作為上下文交給模型。\n4. 程式碼需有中文函式註解。\n完成後請示範驗證流程：先聊「我專注半導體產業客戶、偏好 VIP 等級」，之後開新對話問「根據我之前說的需求推薦適合的客戶」，確認 AI 記得歷史偏好。\n\n",
      "promptMac": "請接續 Unit 6 的實作內容。現在我們要進入 Unit 7：RAG、pgvector、MCP 與知識庫擴充。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### AI Agent 提示詞 — 建立 RAG 知識庫\n請為專案加入 RAG 知識庫功能，使用 PostgreSQL pgvector 儲存向量：\n1. 在 pom.xml 加入 spring-ai-starter-vector-store-pgvector 依賴，並確認 application.yml 已設定 Voyage AI 的 embedding 端點（環境變數 VOYAGE_API_KEY）。\n2. 建立 RAGController，提供兩支 API：\n   - POST /api/rag/upload：接收上傳的文字檔，用 TextReader 讀取、TokenTextSplitter 切分成小段，最後用 vectorStore.accept() 向量化寫入資料庫\n   - GET /api/rag/query：掛上 QuestionAnswerAdvisor，讓 AI 先檢索相關文件片段再回答\n3. 程式碼需有中文函式註解。\n完成後請示範驗證流程：先上傳一份「客戶服務規範」文件，再提問「客戶服務規範是什麼？」，確認 AI 是根據文件內容回答，而不是自由發揮。\n\n### AI Agent 提示詞 — 對話歷史長期記憶\n請為聊天室加入「長期記憶」功能，把對話歷史向量化存入 pgvector：\n1. 建立 ChatHistoryService，提供 saveToVectorStore(sessionId, prompt, response) 方法：把使用者問句與 AI 回答組成一份 Document，metadata 加上 sessionId、type 為 chat_history 與建立時間，再寫入 VectorStore。\n2. 修改 SSE 串流介面：用 doOnNext 收集完整回覆內容，並在 doOnComplete 時用 CompletableFuture.runAsync 非同步寫入向量庫，避免阻塞串流回應。\n3. 實作雙路 RAG 檢索方法：使用者提問時同時查兩路 —— 客戶文件（filterExpression 過濾 type 為 customer_doc）取前 3 筆、該使用者的歷史對話（type 為 chat_history 且 sessionId 相符）取前 2 筆，合併後作為上下文交給模型。\n4. 程式碼需有中文函式註解。\n完成後請示範驗證流程：先聊「我專注半導體產業客戶、偏好 VIP 等級」，之後開新對話問「根據我之前說的需求推薦適合的客戶」，確認 AI 記得歷史偏好。\n\n",
      "prompts": [
        {
          "title": "高級 AI 協作提示詞大師範本（Windows 版）",
          "note": "可直接交給 Codex / Gemini 進行輔助開發",
          "text": "請接續 Unit 6 的實作內容。現在我們要進入 Unit 7：RAG、pgvector、MCP 與知識庫擴充。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### AI Agent 提示詞 — 建立 RAG 知識庫\n請為專案加入 RAG 知識庫功能，使用 PostgreSQL pgvector 儲存向量：\n1. 在 pom.xml 加入 spring-ai-starter-vector-store-pgvector 依賴，並確認 application.yml 已設定 Voyage AI 的 embedding 端點（環境變數 VOYAGE_API_KEY）。\n2. 建立 RAGController，提供兩支 API：\n   - POST /api/rag/upload：接收上傳的文字檔，用 TextReader 讀取、TokenTextSplitter 切分成小段，最後用 vectorStore.accept() 向量化寫入資料庫\n   - GET /api/rag/query：掛上 QuestionAnswerAdvisor，讓 AI 先檢索相關文件片段再回答\n3. 程式碼需有中文函式註解。\n完成後請示範驗證流程：先上傳一份「客戶服務規範」文件，再提問「客戶服務規範是什麼？」，確認 AI 是根據文件內容回答，而不是自由發揮。\n\n### AI Agent 提示詞 — 對話歷史長期記憶\n請為聊天室加入「長期記憶」功能，把對話歷史向量化存入 pgvector：\n1. 建立 ChatHistoryService，提供 saveToVectorStore(sessionId, prompt, response) 方法：把使用者問句與 AI 回答組成一份 Document，metadata 加上 sessionId、type 為 chat_history 與建立時間，再寫入 VectorStore。\n2. 修改 SSE 串流介面：用 doOnNext 收集完整回覆內容，並在 doOnComplete 時用 CompletableFuture.runAsync 非同步寫入向量庫，避免阻塞串流回應。\n3. 實作雙路 RAG 檢索方法：使用者提問時同時查兩路 —— 客戶文件（filterExpression 過濾 type 為 customer_doc）取前 3 筆、該使用者的歷史對話（type 為 chat_history 且 sessionId 相符）取前 2 筆，合併後作為上下文交給模型。\n4. 程式碼需有中文函式註解。\n完成後請示範驗證流程：先聊「我專注半導體產業客戶、偏好 VIP 等級」，之後開新對話問「根據我之前說的需求推薦適合的客戶」，確認 AI 記得歷史偏好。\n\n"
        },
        {
          "title": "MCP 工具設計與實作 — 外部系統擴充",
          "note": "建立 MCP Server，讓 AI 助理具備行事曆排程與 Email 草稿等跨系統能力",
          "text": "接續 RAG 知識庫完成後，現在要為 CRM AI 助理加入 MCP（Model Context Protocol）外部工具擴充能力。MCP 與 domain tool calling 的差別在於：domain tool 是專案內部的 @Tool 方法，MCP 則是獨立的外部工具伺服器，可被多個 AI 應用共用。\n\n### 步驟一：理解 MCP 在 CRM 中的定位\n在建立 MCP 之前，先回答以下設計問題（寫在 mcp-design.md 中）：\n1. 查詢客戶列表 → 應該用 domain tool 還是 MCP？為什麼？\n2. 排程明天下午 2 點與客戶開會 → 應該用 domain tool 還是 MCP？為什麼？\n3. 產生一封 Email 草稿寄給客戶 → 應該用 domain tool 還是 MCP？為什麼？\n4. 匯出本月商機報表為 CSV → 應該用 domain tool 還是 MCP？為什麼？\n\n選用原則：\n- domain tool（@Tool）：存取本專案資料庫與 Service 的內部操作\n- MCP：跨系統、可重用、可獨立部署的外部工具（行事曆、郵件、報表、第三方 CRM）\n\n### 步驟二：建立 CRM MCP Server（Spring Boot 獨立專案）\n請用 Spring Boot 建立一個獨立的 MCP Server 專案（crm-mcp-server），功能如下：\n\n1. 加入 spring-ai-starter-mcp-server-webmvc 依賴\n2. 實作以下 3 個 MCP Tool：\n\n【Tool 1：scheduleCalendarEvent】\n- 描述：排程一個行事曆事件（會議、電話、拜訪）\n- 輸入：title（必填）、date（yyyy-MM-dd）、time（HH:mm）、duration（分鐘，預設 60）、attendees（參與者 Email 列表）\n- 輸出：確認訊息與事件 ID（模擬實作，用 UUID 回傳即可）\n- 用途示範：AI 建議「安排下週與台積電張經理的會議」時，可直接呼叫此工具\n\n【Tool 2：draftEmail】\n- 描述：根據 CRM 情境產生 Email 草稿\n- 輸入：to（收件人 Email）、subject（主旨）、context（CRM 情境描述，例如「跟進台積電的設備採購商機」）\n- 輸出：產生的 Email 草稿文字（模擬實作，用模板組裝）\n- 用途示範：AI 建議「寄信跟進商機」時，可產生草稿供業務確認\n\n【Tool 3：exportOpportunityReport】\n- 描述：匯出商機報表摘要\n- 輸入：period（月份，例如 2025-06）、format（csv 或 summary）\n- 輸出：若 format=summary 回傳文字摘要，若 format=csv 回傳 CSV 檔案路徑（模擬實作）\n- 用途示範：主管詢問「本月商機狀況如何？」時呼叫\n\n3. 每個 Tool 用 @Tool 註解標註，description 用中文描述觸發情境\n4. MCP Server 啟動在 port 8081\n\n### 步驟三：在 CRM 主專案中串接 MCP Client\n1. 在主專案 pom.xml 加入 spring-ai-starter-mcp-client 依賴\n2. 在 application.yml 設定 MCP Server 連線位址：http://localhost:8081\n3. 在 ChatClient 的 .tools() 中同時掛上 domain tools（CustomerTools、OpportunityTools）與 MCP tools\n4. 測試提問：「幫我安排明天下午兩點跟台積電張經理的會議」，確認 AI 呼叫 MCP 的 scheduleCalendarEvent\n\n### 驗證\n1. 單獨啟動 MCP Server（port 8081），確認 /actuator/health 正常\n2. 啟動 CRM 主專案（port 8080），確認 log 中出現 MCP tools 註冊成功的訊息\n3. 在 AI 聊天室提問涉及行事曆或 Email 的問題，確認 MCP tool 被正確呼叫\n4. 同一個對話中混合使用 domain tool（查客戶）與 MCP tool（排會議），確認兩者可以共存"
        }
      ],
      "tasks": [
        {
          "id": "u7-t1",
          "label": "整理 RAG 三步驟流程"
        },
        {
          "id": "u7-t2",
          "label": "確認 pgvector 與 VectorStore 的分工"
        },
        {
          "id": "u7-t3",
          "label": "記錄 1 個知識庫問答驗證情境"
        },
        {
          "id": "u7-t4",
          "label": "理解 MCP Server / Client 在 Spring AI 的接入方式與呼叫流程"
        },
        {
          "id": "u7-t5",
          "label": "理解 MCP 與 Skills 的角色差異與適用場景"
        },
        {
          "id": "u7-t6",
          "label": "瀏覽 spring-ai-agent-utils 專案與工具清單，評估導入 SkillsTool 的版本前置條件"
        },
        {
          "id": "u7-t7",
          "label": "設計非同步向量化儲存 Service 邏輯"
        },
        {
          "id": "u7-t8",
          "label": "在 SSE 串流完成時 (doOnComplete) 觸發歷史寫入"
        },
        {
          "id": "u7-t9",
          "label": "實作多路檢索與上下文合併邏輯"
        }
      ],
      "materials": [
        {
          "id": "mat8",
          "type": "MD",
          "name": "Embabel_黑板機制與GOAP動作規劃指南",
          "desc": "（選修附錄）Embabel GOAP 演算法原理與 Blackboard 機制。Embabel 2.0 穩定後可升級為正式單元。"
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
          "name": "u7-2.png",
          "kind": "diagram",
          "alt": "檢索增強生成管線",
          "spec": "流程圖：Chunk -> Embedding -> pgvector -> Citation"
        },
        {
          "name": "u7-3-term.png",
          "kind": "term",
          "alt": "RAG、pgvector、MCP 與知識庫擴充 專業術語解釋",
          "spec": "Embedding / RAG / MCP"
        }
      ]
    },
    {
      "id": "u8",
      "title": "結訓專案衝刺與 Demo Day 驗收",
      "subtitle": "整合 Unit 1-7 所有產出，完成一套從登入、客戶管理、AI 對話到 RAG 知識查詢的完整 AI CRM 系統，並在 Demo Day 進行展示驗收。",
      "time": "13:00 ~ 17:00",
      "features": [
        "全端系統整合與除錯",
        "效能調校與上線檢查清單",
        "Demo Day 展示與驗收"
      ],
      "goals": [
        "整合前後端功能，確認登入、客戶管理、AI 對話、RAG 查詢的完整流程可跑通",
        "撰寫端到端測試案例，涵蓋三種典型客戶場景的完整使用路徑",
        "建立上線檢查清單，涵蓋安全性、效能、錯誤處理與監控指標",
        "執行 pnpm run verify 通過完整全端自動化測試與驗收"
      ],
      "principle": "結訓專案的核心目標不是新增功能，而是讓前七個單元的產出真正「串起來」成為一套可展示的產品。重點在於排除整合時的版本衝突、CORS 錯誤、環境差異，並建立可重複執行的驗證流程。",
      "concepts": [
        {
          "heading": "結訓整合的三大挑戰",
          "group": "結訓整合挑戰",
          "body": "把七個單元的產出串成一套可展示的系統，最常遇到三類問題：(1) 環境差異——Docker 容器、JDK 版本、Node.js 版本在不同機器上不一致；(2) 跨層整合——前端 Axios 的 base URL、CORS 設定、JWT token 在 header 的格式，任何一環不對就 401/403；(3) 資料流斷裂——seed data 的客戶 ID 與前端硬編碼的測試 ID 不一致，導致查無資料。解決方式是建立一份「整合檢查清單」，從資料庫、後端、前端逐層驗證。",
          "note": "整合階段的 bug 通常不在邏輯，而在環境與設定。"
        },
        {
          "heading": "端到端測試案例設計",
          "group": "測試策略",
          "body": "結訓專案需要涵蓋三種典型客戶場景，對應 Unit 3 seed data 的三種客戶類型：(1) 高價值活躍客戶（APIM）——登入後查看客戶詳情、觸發 AI 對話、AI 能正確引用 RAG 知識庫回答產品問題；(2) 資料不足客戶——AI 應誠實回答「查無相關資料」而非編造；(3) 流失風險客戶（GlobalMart）——AI 摘要能正確反映互動頻率下降的趨勢。每個場景都要從登入開始，走完完整的使用路徑。"
        },
        {
          "heading": "AI 功能的測試策略",
          "group": "測試策略",
          "body": "AI 功能的測試不能用傳統的「斷言輸出完全相等」方式，因為 LLM 的輸出具有隨機性。正確的策略是分層測試：(1) Java @Tool 方法——用一般單元測試驗證輸入輸出；(2) Prompt 組裝——驗證 system prompt 是否正確帶入客戶資料、是否設定了正確的角色約束；(3) RAG 檢索——驗證 vector store 查詢是否回傳相關文件；(4) 端到端——用 Playwright 模擬使用者操作，驗證 SSE 串流是否正確顯示在前端。",
          "note": "測試 AI 功能的重點是驗證 prompt 的組裝與工具的調用，而非 LLM 的具體回答。"
        },
        {
          "heading": "上線檢查清單與效能調校",
          "group": "上線準備",
          "body": "AI CRM 上線前需要檢查：(1) 安全性——JWT secret 是否已替換為生產金鑰、CORS 白名單是否已收斂、API 是否有 rate limiting；(2) 效能——SSE 連線是否有 timeout 設定、RAG 查詢的 topK 與 similarity threshold 是否合理、資料庫連線池大小是否足夠；(3) 可觀測性——是否有結構化日誌、是否記錄 AI 呼叫的 token 消耗與延遲；(4) 錯誤處理——LLM API 不可用時是否有 fallback、前端是否能優雅顯示錯誤訊息。"
        },
        {
          "heading": "Demo Day 展示準備",
          "group": "Demo Day 展示準備",
          "body": "Demo Day 的展示順序建議：(1) 先展示登入與角色切換，證明 Spring Security + JWT 正常運作；(2) 展示客戶列表與商機管理，證明 JPA + Flyway 的資料層完整；(3) 開啟 AI 助理聊天室，用 SSE 串流展示即時回應；(4) 提問需要 RAG 知識的問題（如退貨政策），展示 AI 引用正確文件回答；(5) 開啟 Swagger UI 展示 OpenAPI 文件。每個步驟預先準備好測試資料，避免現場輸入導致意外。",
          "note": "Demo 的目的是證明系統可用，不是展示所有功能。選擇最能體現整合價值的路徑。"
        }
      ],
      "prompt": "請接續 Unit 7 的 RAG 知識庫與 MCP 實作。現在我們要進入 Unit 8：結訓專案衝刺與 Demo Day 驗收。目標是把 Unit 1-7 的所有產出整合成一套可展示的 AI CRM 系統，並建立端到端測試與上線檢查清單。\n\n要求：\n1. 全端整合驗證：\n   - 確認 Spring Boot 後端、PostgreSQL 資料庫、React 前端三層能正確啟動與串接。\n   - 驗證 JWT 登入流程、客戶 CRUD、AI 對話 SSE 串流、RAG 知識庫查詢的完整路徑。\n   - 排除常見整合問題：CORS 設定、JWT header 格式、seed data ID 一致性。\n2. 端到端測試案例：\n   - 涵蓋三個典型客戶場景，對應 Unit 3 seed data 的三種客戶：\n     (1) 高價值活躍客戶 → 完整走過 AI 對話與 RAG 查詢流程。\n     (2) 資料不足客戶 → AI 應誠實回答「查無資料」而非編造。\n     (3) 流失風險客戶 → AI 摘要能正確反映互動頻率下降趨勢。\n3. 上線檢查清單：\n   - 安全性：JWT secret 替換、CORS 白名單收斂、rate limiting。\n   - 效能：SSE timeout、RAG topK/threshold、連線池大小。\n   - 可觀測性：結構化日誌、AI token 消耗記錄。\n   - 錯誤處理：LLM API fallback、前端錯誤訊息。\n4. Demo Day 展示準備：\n   - 準備展示腳本：登入 → 客戶列表 → AI 對話 → RAG 查詢 → Swagger UI。\n   - 預先準備測試資料，確保現場展示流暢。\n5. 程式碼需要有函式級別註解(註解使用中文)，重要變數或物件也需要加上註解。\n6. 在 PowerShell 7+ (Windows) 或 zsh / bash (macOS) 終端機中執行驗證測試，確認全站功能通過驗收。",
      "promptMac": "請接續 Unit 7 的 RAG 知識庫與 MCP 實作。現在我們要進入 Unit 8：結訓專案衝刺與 Demo Day 驗收。目標是把 Unit 1-7 的所有產出整合成一套可展示的 AI CRM 系統，並建立端到端測試與上線檢查清單。\n\n要求：\n1. 全端整合驗證：\n   - 確認 Spring Boot 後端、PostgreSQL 資料庫、React 前端三層能正確啟動與串接。\n   - 驗證 JWT 登入流程、客戶 CRUD、AI 對話 SSE 串流、RAG 知識庫查詢的完整路徑。\n   - 排除常見整合問題：CORS 設定、JWT header 格式、seed data ID 一致性。\n2. 端到端測試案例：\n   - 涵蓋三個典型客戶場景，對應 Unit 3 seed data 的三種客戶：\n     (1) 高價值活躍客戶 → 完整走過 AI 對話與 RAG 查詢流程。\n     (2) 資料不足客戶 → AI 應誠實回答「查無資料」而非編造。\n     (3) 流失風險客戶 → AI 摘要能正確反映互動頻率下降趨勢。\n3. 上線檢查清單：\n   - 安全性：JWT secret 替換、CORS 白名單收斂、rate limiting。\n   - 效能：SSE timeout、RAG topK/threshold、連線池大小。\n   - 可觀測性：結構化日誌、AI token 消耗記錄。\n   - 錯誤處理：LLM API fallback、前端錯誤訊息。\n4. Demo Day 展示準備：\n   - 準備展示腳本：登入 → 客戶列表 → AI 對話 → RAG 查詢 → Swagger UI。\n   - 預先準備測試資料，確保現場展示流暢。\n5. 程式碼需要有函式級別註解(註解使用中文)，重要變數或物件也需要加上註解。\n6. 在 zsh / bash (macOS) 終端機中執行驗證測試，確認全站功能通過驗收。",
      "prompts": [
        {
          "title": "高級 AI 協作提示詞大師範本（Windows 版）",
          "note": "可直接交給 Codex / Claude Code / Gemini 進行輔助開發",
          "text": "請接續 Unit 7 的 RAG 知識庫與 MCP 實作。現在我們要進入 Unit 8：結訓專案衝刺與 Demo Day 驗收。目標是把 Unit 1-7 的所有產出整合成一套可展示的 AI CRM 系統，並建立端到端測試與上線檢查清單。\n\n要求：\n1. 全端整合驗證：確認 Spring Boot 後端、PostgreSQL 資料庫、React 前端三層能正確啟動與串接。\n2. 端到端測試案例：涵蓋高價值客戶、資料不足客戶、流失風險客戶三個場景。\n3. 上線檢查清單：安全性、效能、可觀測性、錯誤處理。\n4. Demo Day 展示準備：登入 → 客戶列表 → AI 對話 → RAG 查詢 → Swagger UI。\n5. 程式碼需要有函式級別註解(註解使用中文)。"
        }
      ],
      "tasks": [
        {
          "id": "u8-t1",
          "label": "完成前後端整合，確認登入、客戶管理、AI 對話的完整流程可跑通"
        },
        {
          "id": "u8-t2",
          "label": "撰寫三個典型客戶場景的端到端測試案例"
        },
        {
          "id": "u8-t3",
          "label": "建立上線檢查清單並逐項確認"
        },
        {
          "id": "u8-t4",
          "label": "完成 Demo Day 展示腳本並試跑驗收"
        }
      ],
      "materials": [],
      "illustrations": [
        {
          "name": "u8-1.png",
          "kind": "hero",
          "alt": "結訓專案整合與 Demo Day",
          "spec": "全端整合、測試策略與展示驗收"
        },
        {
          "name": "u8-2.png",
          "kind": "diagram",
          "alt": "端到端測試流程",
          "spec": "流程圖：登入 -> 客戶管理 -> AI 對話 -> RAG 查詢 -> 驗收"
        },
        {
          "name": "u8-3-term.png",
          "kind": "term",
          "alt": "結訓專案衝刺與 Demo Day 驗收 專業術語解釋",
          "spec": "E2E Testing / Checklist / Demo Day"
        }
      ]
    }
  ]
};
