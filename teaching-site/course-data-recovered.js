      
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
```

### 自動化測試驗證
```powershell
# 執行完整驗證測試 (資料校驗 + Playwright 畫面渲染測試)
pnpm run verify

# 僅執行資料完整性與檔案結構校驗 (verify-site.mjs)
pnpm run verify:data

# 僅執行瀏覽器渲染、RWD 與功能互動測試 (verify-render.mjs)
pnpm run verify:render
```

## 檔案結構說明
- [index.html](file:///d:/GitHub/hahow-ai-full-stack/t
    "index.html",
    "styles.css",
    "course-data.js",
    "src/main.jsx",
- [assets/illustrations/](file:///d:/GitHub/hahow-ai-full-stack/teaching-site/assets/illustrations) - 存放網頁上呈現的視覺素材；首頁與各單元 hero 使用 PNG 生圖，流程圖與專業術語解釋圖使用可維護 SVG。
- [scripts/generate-assets.mjs](file:///d:/GitHub/hahow-ai-full-stack/teaching-site/scripts/generate-assets.mjs) - 視覺素材 manifest 與 SVG 產生器，負責同步每個單元的 hero、diagram 與 term 圖片引用。
- [scripts/verify-site.mjs](file:///d:/GitHub/hahow-ai-full-stack/teaching-site/scripts/verify-site.mjs) - 資料校驗腳本，驗證 8 個單元、所有引用圖片存在性，與提示詞字數限制。
- [scripts/verify-render.mjs](file:///d:/GitHub/hahow-ai-full-stack/teaching-site/scripts/verify-render.mjs) - Playwright 自動化驗證腳本，測試 RWD 水平溢出、進度儲存與複製按鈕。

## 程式碼開發規範
1. **繁體中文註解**：所有撰寫的 JavaScript / Node 腳本，函數級別與重要變數均須使用繁體中文註解。
2. **React 19 原則**：互動 UI 需以 React 19 元件撰寫，入口維持 Vite；不要把大量 DOM 操作重新塞回 `index.html`。
3. **資料與渲染分離**：所有課程教學內容與任務字串，必須定義在 `course-data.js` 物件中；React 元件僅能讀取此資料進行渲染。
4. **防禦性安全性**：React 中不得使用 `dangerouslySetInnerHTML` 渲染課程變數；Markdown-like 片段需轉成安全 React 節點。
5. **圖片覆蓋率底線**：每個新增的單元在 `course-data.js` 裡必須配置 `illustrations` 陣列（至少 3 個圖片：hero、diagram、term），且必須在 `assets/illustrations/` 目錄中建立實體 SVG/PNG 檔案。

  for (const unit of course.units || []) {
    if (!unit.prompt || unit.prompt.length < 800) errors.push(`${unit.id} prompt is too thin`);
    if (!unit.principle || unit.principle.length < 80) errors.push(`${unit.id} principle is too thin`);
    if (!Array.isArray(unit.illustrations) || unit.illustrations.length < 3) errors.push(`${unit.id} needs hero, diagram and term illustrations`);
    if (!unit.prompt.includes("請接續") && unit.id !== "u1") errors.push(`${unit.id} prompt does not continue from previous unit`);
    for (const illustration of unit.illustrations || []) {
      const asset = path.join(root, "assets", "illustrations", illustration.name);
      if (!(await exists(asset))) errors.push(`${unit.id} missing illustration: ${illustration.name}`);
    }
  }

  if (errors.length) {
    console.error(errors.join("\n"));
    process.exitCode = 1;
    return;
  }

  const assetCount = (course.units || []).reduce((sum, unit) => sum + (unit.illustrations?.length || 0), 1);
  console.log(`OK: ${course.units.length} units, ${assetCount} referenced visual assets verified.`);
}

main().catch((error) => {
  console.error(error);
<truncated 4693 bytes>
接將建議輸出給業務人員。\n3. Agent Trace UI 與觀測：\n   - 記錄每個 Action 的執行順序、耗時、輸入、輸出以及 LLM Token 消耗。\n   - 在前端 React 介面實作「Agent 軌跡面板 (Trace Panel)」，以時間線形式清晰展現 Agent 的決策過程（如：步驟 1 讀取客戶資料成功 -> 步驟 2 檢索知識庫匹配 2 筆 -> 步驟 3 發現流失風險 80 分 -> 步驟 4 生成建議 -> 步驟 5 審核成功輸出）。\n4. 測試與上線檢查：\n   - 撰寫單元與整合測試，驗證純 Java Actions 與 LLM Prompts 的邊界。\n   - 必須涵蓋三個端到端結訓案例，逐一對應 Blackboard 走向不同的 Action 路徑，且與 Unit 3 seed data 的三種客戶對應，確保痛點場景可被完整驗證：(1) 高價值活躍客戶 → 正常產出並通過審核；(2) 資料不足客戶 → 觸發「要求補資料」Replanning，不產生臆測建議；(3) 流失 / 續約延遲風險客戶 → 風險分數超標觸發「轉交主管」路徑。\n   - 提供 AI 上線檢查清單 (Checklist) 作為學員驗收標準。\n5. 程式碼需要有函式級別註解(註解使用中文)，重要變數或物件也需要加上註解。\n6. 在 PowerShell 7+ (Windows) 或 zsh / bash (macOS) 終端機中執行 `pnpm run verify`，確認 verify-site (資料結構、8單元、17個SVG、prompt 字數) 與 verify-render (Playwright RWD、搜尋、Checkbox持久化、複製功能) 全數通過，無水平溢出與主控台錯誤。",
      "principle": "Embabel 的核心價值在於將不可控的單一 Prompt 推理，轉化為可控、可規劃 (GOAP) 且具備型別約束與輸出審核的 Agent Workflow。透過 Blackboard 與 Agent Trace 機制，讓 AI 決策流程透明、可維護且易於偵錯，符合企業維運的要求。"
    }
  ]
};

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
        "detail": "先把後端責任邊界與安全性安全心法講清楚，再把模型、工具、向量 RAG 與 Embabel Agent 編排接回同一個應用。"
      },
      {
        "label": "教學方式",
        "value": "圖解 + 程式碼 + 驗證",
        "detail": "每個單元都用生活化圖解建立直覺，再回到實際專案與設定檔中進行驗證，並使用 PowerShell / Playwright 自動跑測試驗收。"
      },
      {
        "label": "課程產出",
        "value": "全端專案 + 決策 Trace 面板",
        "detail": "課程不是只看說明，而是實作出一套包含登入、客戶管理、AI SSE 聊天與 Embabel GOAP Agent 執行軌跡展示的完整 CRM 系統。"
      },
      {
        "label": "完成標準",
        "value": "全站通過 verify 自動驗收",
        "detail": "資料庫能起、JWT 能保、AI 能查真實資料、RAG 能回答產品問題、Agent 能夠進行 Replanning 智慧路由並正常繪製 Trace 線。"
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




































































































          },
          {
            "heading": "輸入驗證：為什麼不能信任前端傳來的資料",
            "body": "目前的 `create` 端點直接把 `@RequestBody` 拿到的物件存進去，前端傳什麼就存什麼。這代表 `name` 傳空字串、`price` 傳負數，全部都會寫入記憶體（或之後的資料庫）而不會報錯。\n\nBean Validation（JSR-380）讓你把驗證規則標在 Model 欄位上，Controller 只需加一個 `@Valid`，Spring 就會在呼叫 Service 之前自動驗證，不合法的請求直接回傳 400，完全不進入業務邏輯。\n\n- 驗證規則標在 Model / DTO 欄位，不散落在 Service 或 Controller 各處\n- 規則跟資料走：不管從哪個 Controller 端點傳入，同一套規則都生效\n- 驗























































































































































































              ['LLM call', '模型、prompt、token cost'],
              ['tool invocation', '哪個工具、參數、結果'],
              ['失敗原因', '用於 replanning 分析與 guardrail 補強']
            ]
          },
          {
            heading: '案例實作⑦：summarize action 的測試與觀測',
            body: '案例收尾：fetchActivity 是純程式，用一般單元測試 + mock 報表服務即可。summarize 是 LLM action，重點不是斷言輸出字串，而是驗證 prompt 組裝：門檻參數有沒有帶入、@Tool 統計有沒有暴露、模型角色對不對。上線後若客訴「方案發錯」，靠 ActionAudit 回溯是哪一步、哪個 prompt、哪次 tool call 出的問題。',
            table: {
              head: ['對象', '測法', '斷言重點'],
              rows: [
                ['fetchActivity', '單元測試 + mock TravelActivityReportingService', '查無客戶時不產生 TravellerActivity'],
                ['summarize（prompt）', 'prompt 組裝測試', '帶入 maxWords / 高消費門檻；暴露 activity 的 @Tool；用預設 LLM 角色'],
                ['reviewOffer', '規則單元測試', '超出折扣上限的 OfferDraft 必須被擋下'],
        q: "為什麼網站採單頁 SPA 配合 React 19？",
        a: "此專案採用 Vite + React 19 單頁式架構，將資料與渲染分離，便於利用 HSL 動態主題、CSS 漸變折疊與 Playwright 進行 RWD 功能整合測試驗證。"
      },
      {
        q: "圖片放在哪裡比較安全？",
        a: "目前都集中在 teaching-site/assets/illustrations，教學站可獨立預覽與部署。"
      }
    ],
    verification: [
      "先執行 docker-compose up -d 啟動 PostgreSQL / pgvector",
      "載入 .env 中的 API Key 後執行 mvn spring-boot:run",
      "瀏覽 http://localhost:8080 驗證聊天 Demo，再回到教學網站對照章節內容"
    ]
  },
  units: finalUnits
};

// 驗證字數限制與關鍵字
finalUnits.forEach(unit => {
  if (!unit.prompt || unit.prompt.length < 800) {
    console.error(`Error: ${unit.id} prompt length is ${unit.prompt ? unit.prompt.length : 0}, which is less than 800`);
  }
  if (!unit.principle || unit.principle.length < 80) {
    console.error(`Error: ${unit.id} principle length is ${unit.principle ? unit.principle.length : 0}, which is less than 80`);
  }
  if (unit.id !== 'u1' && !unit.prompt.includes('請接續')) {
    console.error(`Error: ${unit.id} prompt does not contain '請接續'`);
  }
});

// 產生最終寫入的 JavaScript 文字檔內容
const outputJS = `// 核心資料庫 (COURSE) - 保存所有的單元大綱、高級 AI 提示詞大師範本、技術原則與學習任務。
window.COURSE = ${JSON.stringify(targetCourse, null, 2)};
`;

fs.writeFileSync('d:/GitHub/hahow-ai-full-stack/teaching-site/course-data.js', outputJS, 'utf8');
console.log('Successfully merged and generated d:/GitHub/hahow-ai-full-stack/teaching-site/course-data.js');
































































      options: [
        '它是一個全域字串 map，action 用魔法字串存取',
        '它只儲存最後一個 action 的輸出',
        '它是 action 間共享的狀態區，Embabel 依型別與名稱把物件綁定到 method parameters'
      ],
      answer: 2,
      sourceUnit: 'day1-u-4'
    },
    {
      id: 'q4',
      type: 'single',
      q: 'Embabel（0.4.0）教學專案的環境需求，何者正確？',
      options: [
        'Java 8 即可，Spring Boot 版本不限',
        'Java 21 以上、Spring Boot 3.5.x（MVC 用 spring-boot-starter-web）；Boot 4 需等 Embabel 2.0',
        '只能用 Kotlin 開發'
      ],
      answer: 1,
      sourceUnit: 'day1-u-5'
    },
    {
      id: 'q5',
      type: 'single',
      q: '案例中「判斷客戶是否為高消費」的正確做法是？',
      options: [
        'TravellerActivity 用 @Tool 暴露 totalSpend() 等統計方法，由 Java 計算、LLM 引用',
        '把 trips 清單貼進 prompt，請 LLM 自己加總',
        '先寫進向量資料庫再用 RAG 查回來'
      ],
      answer: 0,
      sourceUnit: 'day1-u-6'
    },
    {
      id: 'q6',
      type: 'single',
      q: '對需要 LLM 的 action，Embabel 建議的可測試重點是什麼？',
      options: [
        '無法測試，只能上線後人工抽查',
        '只測最終輸出字串是否完全相等',
        '測 prompt 是否帶齊重要資料、是否使用正確模型角色、是否暴露正確工具'
      ],
      answer: 2,
      sourceUnit: 'day1-u-7'
    }
  ]
};



















































      <div className="unit-meta-row">
        <span className="unit-meta-chip">{unit.time}</span>
        <span className="unit-meta-chip unit-progress-pill">{done} / {total} 完成</span>
      </div>
      <UnitFeatureHighlight index={index} features={unit.features} />
      <Illustrations unit={unit} />
      {unit.principle ? (
        <div className="note">
          <strong>技術設計原則與核心心法</strong>
          <p>{translatePlatformText(unit.principle, platform)}</p>
        </div>
      ) : null}
      <div className="content-block">
        <h4>學習目標</h4>
        <GoalList goals={unit.goals || []} platform={platform} />
      </div>
      <div className="accordion">
        <ConceptAccordion unit={unit} open={accordionOpen} />
        <PromptAccordion unit={unit} open={accordionOpen} platform={platform} onTogglePlatform={onTogglePlatform} />
      </div>
      <TaskPanel unit={unit} taskState={taskState} onToggleTask={onToggleTask} onPreview={onPreview} platform={platform} />
    </article>
  );
}

/**
 * 顯示每日課程區塊。
 */
function DayBlock({ course, dayMeta, taskState, onToggleTask, accordionOpen, searchQuery, onPreview, platform, onTogglePlatform }) {
  const day = course[dayMeta.id];
  const allUnits = getAllUnits(course);
  if (!day) return null;
  return (
    <section className="day-block" id={day.id}>
      <div className="day-header glass-card">

































































































































































































          },
          {
            "heading": "JSX 語法與 Functional Component 元件結構",
            "body": "JSX 是 JavaScript 的語法擴充，允許我們在 JavaScript 中直接撰寫一種類似 HTML 的結構。React 19 推薦使用 Functional Component（函式元件）進行開發，相比舊版的 Class 元件更加簡潔。\n\nJSX 寫作規範與注意事項：\n\n1. 所有元件必須回傳「單一根節點」（若有多個元素，需用空標籤 `<></>` 包裹）。\n\n2. 由於 class 在 JS 中是保留字，JSX 中必須改寫為 `className`。\n\n3. HTML 事件綁定需改為 React 的小駝峰命名（例如 `onclick` 改為 `onClick`）。\n\n4. 變數與邏輯表達式可直接放在大括號 `{}` 中進行求值與渲染。\n\n**Counter.jsx — 基本 JSX 函式元件範例 (jsx)**\n```jsx\nimport React, { useState } from 'react';\n\n// 宣告一個 Functional Component 元件\nexport default function Counter({ initialCount = 0 }) {\n  // 使用 useState Hook 管理元件內部的狀態\n  const [count, setCount] = useState(initialCount);\n\n  return (\n    <div className=\"counter-container\">\n      <h3>當前計數器：{count}</h3>\n      {/* 點擊事件小駝峰命名，並使用大括號綁定 JavaScript 方法 */}\n      <button onClick={() => setCount(count + 1)}>\n        累加 +1\n      </button>\n    </div>\n  );\n}\n```"
          },
          {
            "heading": "開發端代理與後端 API 串接 (Vite Proxy)",
            "body": "在前後端分離的架構中






































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
                onTogglePlatform={togglePlatform}
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

























































































































































































































          },
          {
            "heading": "加入 spring-ai-agent-utils 依賴（pom.xml）",
            "body": "套件發布於 Maven Central（groupId 為 org.springaicommunity），官方建議透過 BOM 統一管理版本，再引入核心函式庫。\n\n**pom.xml — 引入 spring-ai-agent-utils（BOM + 核心庫） (xml)**\n```xml\n<!-- 1. dependencyManagement：以 BOM 統一管理 agent-utils 版本 -->\n<dependencyManagement>\n    <dependencies>\n        <dependency>\n            <groupId>org.springaicommunity</groupId>\n            <artifactId>spring-ai-agent-utils-bom</artifactId>\n            <version>0.9.0</version>\n            <type>pom</type>\n            <scope>import</scope>\n        </dependency>\n    </dependencies>\n</dependencyManagement>\n\n<!-- 2. dependencies：引入核心函式庫（版本由 BOM 決定） -->\n<dependency>\n    <groupId>org.springaicommunity</groupId>\n    <artifactId>spring-ai-agent-utils</artifactId>\n</dependency>\n\n<!-- 注意：0.9.0 要求 Spring AI 2.0.0-RC1+ / Java 17+ / Spring Boot 3.x 或 4.x -->\n```"
          },
          {
            "heading": "ChatClient 掛上 SkillsTool 與 Agent 工具",
            "body": "把 SkillsTool 與需


















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
            "body": "RAG 可以把它想成「開卷考試」。模型不再只依賴自己訓練時學過的內容，而是先去找與問題最相關的文檔片段，再根據那些片段作答。\n\n這種做法的好處是知識更新成本低、可控性高，而且可以明確限制回答只依據文件內容。"
          },
          {
            "heading": "RAG 與 Fine-Tuning 差異",
            "body": "- RAG 是外部知識檢索，更新速度快\n- Fine-Tuning 是調整模型參數，成本較高\n- RAG 更適合企業文件、FAQ、內規等常變動內容\n- 若要降低幻覺並保留來源脈絡，RAG 更實用"
          },
          {
            "heading": "RAG vs Fine-Tuning 完整比較",
































        ],
        "materials": [
          {
            "id": "mat4",
            "type": "MD",
            "name": "pgvector_環境安裝與向量檢索指令說明",
            "desc": "向量資料庫基礎概念、SQL 向量距離計算及 pgvector 索引優化指令。"
          }
        ],
        "illustrations": [
          {
            "name": "u7-1.png",
            "kind": "hero",
            "alt": "RAG 知識庫與 MCP 擴充"
          },
          {
            "name": "u7-2.svg",
            "kind": "diagram",
            "alt": "檢索增強生成流程與 MCP 協定"
          },
          {
            "name": "u7-3-term.svg",
            "kind": "term",
            "alt": "Vector Embedding 與 MCP 術語表"
          }
        ]
      },
      {
        "id": "u8",
        "title": "Embabel Agent Flow、測試、觀測與結訓專案",
        "subtitle": "導入 Embabel 智慧 Agent，使用 GOAP 演算法與 Blackboard 機制規劃客戶下一步行動建議，實作決策軌跡 Trace 面板並進行 Playwright 整合驗證。",
        "time": "13:00 ~ 17:00",
        "features": [
          "Embabel Agent Flow 智慧建議",
          "資料不足補件 / 高風險轉交主管",
          "Agent Trace 決策軌跡面板"
        ],
        "goals": [
          "導入 Embabel 0.4.x，以 GOAP action table 
          "以 Java 21 Record 實作型別安全與具審核機制 (Replanning) 的 Action 流程",
          "在 React 實作展示 AI 決策流程與 Token 消耗的 Agent Trace 時間線面板",
          "修復 verify-render.mjs，執行 pnpm run verify 確認全站功能通過驗收"
        ],
        "principle": "Embabel 的核心價值在於將不可控的單一 Prompt 推理，轉化為可控、可規劃 (GOAP) 且具備型別約束與輸出審核的 Agent Workflow。透過 Blackboard 與 Agent Trace 機制，讓 AI 決策流程透明、可維護且易於偵錯，符合企業維運的要求。",
        "concepts": [
          {


































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
            "desc": "Embabel GOAP 演算法原理、Blackboard 世界狀態定義及 Type-Safe Action 規劃與 Replanning 機制。"
          }
        ],
        "illustrations": [
          {
            "name": "u7-1.png",
            "kind": "hero",
            "alt": "RAG 知識庫與 MCP 擴充"
          },
          {
            "name": "u7-2.svg",
            "kind": "diagram",
            "alt": "檢索增強生成流程與 MCP 協定"
          },
          {
            "name": "u7-3-term.svg",
            "kind": "term",
            "alt": "Vector Embedding 與 MCP 術語表"
          }
        ]
      },
      {
        "id": "u8",
        "title": "Embabel Agent Flow、測試、觀測與結訓專案",
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
      "desc": "Embabel GOAP 演算法原理、Blackboard 世界狀態定義及 Type-Safe Action 規劃與 Replanning 機制。"
    }
  ],
  "quiz": [
    {
      "id": "q1",
      "q": "在 Spring Boot 3.5.x 中，為了支援向量資料庫 pgvector 的 vector 欄位，我們在 Docker Compose 中應該使用哪一個映像檔？",
      "options": [
        "postgres:16",
        "pgvector/pgvector:pg16",
        "mysql:8",
        "redis:latest"
      ],
      "answer": 1
          {
            "name": "u8-1.png",
            "kind": "hero",
            "alt": "Embabel Agent Flow 與 決策軌跡"
          },
          {
            "name": "u8-2.svg",
            "kind": "diagram",
            "alt": "GOAP A* 規劃與 Blackboard Replanning 流程"
          },
          {
            "name": "u8-3-term.svg",
            "kind": "term",
            "alt": "GOAP, Blackboard 與 Trace UI 專有名詞"
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
      "desc": "React 19 呼叫後端 API、JWT 自動攜帶與 Axios 攔截器 (Intercepto
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
        "redis:latest"
      ],
      "answer": 1
    },
    {
      "id": "q2",
      "q": "關於 @Valid 與 @Validated 的敘述，下列何者正確？",
      "options": [
        "@Valid 支援分組驗證 (Validation Groups)，@Validated 是 Jakarta EE 標準",
        "@Validated 支援分組驗證，@Valid 是 Jakarta EE 標準，一般巢狀驗證標在欄位上用 @Valid",
        "兩者完全等價，可以任意互換，沒有任何功能差異",
        "@Valid 只能用在 Service 層，@Validated 只能用在 Controller 層"
      ],
      "answer": 1
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
      "id": "q2",
      "q": "關於 @Valid 與 @Validated 的敘述，下列何者正確？",
      "options": [
        "@Valid 支援分組驗證 (Validation Groups)，@Validated 是 Jakarta EE 標準",
        "@Validated 支援分組驗證，@Valid 是 Jakarta EE 標準，一般巢狀驗證標在欄位上用 @Valid",
        "兩者完全等價，可以任意互換，沒有任何功能差異",
        "@Valid 只能用在 Service 層，@Validated 只能用在 Controller 層"
      ],
      "answer": 1
    },
    {
      "id": "q3",
      "q": "當 AI 助理需要即時、逐字地回傳生成的回應時，後端應設計何種 HTTP 端點與前端 React 進行整合？",
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
          "alt": "開發環境與 AI 協作設定"
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










        },
        {
          "heading": "HTTP 方法與 CRUD 對應",
          "body": "REST API 主要用到四種 HTTP 方法，分別對應資料庫的 CRUD（建立、讀取、更新、刪除）操作。理解這個對應關係，後面看 Spring Boot 的 `@GetMapping`、`@PostMapping` 等註解就不會覺得陌生。\n\n- GET 是安全且冪等的——同樣的請求重複發多次，結果一樣、資料不受影響\n- POST 通常不是冪等的——發兩次就會建立兩筆資料\n- PUT / PATCH 是冪等的——重複更新同一份資料，結果相同\n- 本課程以 GET / POST 為主，涵蓋查詢商品與新增商品兩個最常見情境\n\n**HTTP 方法對照表 (text)**\n```text\n┌──────────┬──────────┬────────────────────────────────┐\n│ HTTP 方法 │ CRUD 操作 │ 說明                           │\n├──────────┼──────────┼────────────────────────────────┤\n│ GET      │ Read     │ 讀取資料，不修改伺服器狀態     │\n│ POST     │ Create   │ 新增資料，回傳 201 Created      │\n│ PUT      │ Update   │ 整筆更新，需傳入完整資料       │\n│ PATCH    │ Update   │ 部分更新，只傳要修改的欄位     │\n│ DELETE   │ Delete   │ 刪除指定資源                   │\n└──────────┴──────






































































































































        },
        {
          "heading": "在 Controller 加上 @Valid 觸發驗證",
          "body": "只需要在 `@RequestBody` 前加 `@Valid`，Spring 就會在解析完請求 Body 後、進入方法邏輯之前執行驗證。驗證失敗時拋出 `MethodArgumentNotValidException`，Spring 自動回傳 400。\n\n**ProductController.java — create 端點加上 @Valid (java)**\n```java\n// ✅ 加上 @Valid：Spring 驗證 Product 欄位，不通過直接回 400\n@PostMapping\n@ResponseStatus(HttpStatus.CREATED)\npublic Product create(@Valid @RequestBody Product product) {\n    return productService.save(product);\n}\n\n// PUT 修改也一樣需要驗證\n@PutMapping(\"/{id}\")\npublic ResponseEntity<Product> update(\n        @PathVariable Long id,\n        @Valid @RequestBody Product product) {       // ← @Valid 同樣適用\n    return productService.findById(id)\n        .map(existing -> {\n            product.setId(existing.getId());\n            return ResponseEntity.ok(productService.save(product));\n        })\n        .orElse(ResponseEntity.notFound().build());\n}\n```"
        },
        {
          "heading": "驗證失敗時的回應格式",
      
        },
        {
          "heading": "常用 Bean Validation 標註速查",
          "body": "以下是最常用到的驗證標註，依照驗證對象分組。所有標註都來自 `jakarta.validation.constraints` 套件，引入 `spring-boot-starter-validation` 即可使用。\n\n- **字串類**：`@NotBlank`（非空且非空白）、`@NotEmpty`（非空但可以全空白）、`@Size(min, max)`（長度範圍）、`@Email`（Email 格式）、`@Pattern(regexp)`（正規表示式）\n- **數字類**：`@NotNull`（非 null）、`@Min(value)`（整數最小值）、`@Max(value)`（整數最大值）、`@DecimalMin`（含小數的最小值）、`@DecimalMax`（含小數的最大值）、`@Positive`（必須大於 0）、`@PositiveOrZero`（大於等於 0）\n- **集合類**：`@NotEmpty`（集合不可空）、`@Size(min, max)`（集合元素數量範圍）\n- **巢狀物件**：`@Valid` 標在欄位上 → 對該物件的



























































          "body": "Repository 幫你省去了 SQL 撰寫，但「資料一致性的保障」不在 Repository 層，而是在 Service 層的交易（Transaction）控制上。\n\n當一個業務操作需要對資料庫做新增、修改或刪除時，必須在 Service 方法上加 `@Transactional`，讓這些動作被包在同一個交易區塊裡——成功就全部提交，失敗就全部回滾，不會出現「存到一半」的殘缺資料。\n\n- `@Transactional` → 寫入操作（INSERT / UPDATE / DELETE），讓 Hibernate 在方法結束後自動 commit，發生例外時自動 rollback\n- `@Transactional(readOnly = true)` → 純查詢操作，告知 Hibernate 跳過 dirty checking、提示資料庫使用唯讀連線，降低效能開銷\n- **不加的後果**：Hibernate Session 可能在方法執行中途失效，寫入操作拋出 `TransactionRequiredException`，且只在特定流程才觸發，難以排查"
        },
        {
          "heading": "@Transactional 標註規範與 Service 完整範例",
          "body": "以下是本課程 ProductService 的完整寫法，查詢方法用 `readOnly = true`、寫入方法用預設（可讀可寫）。這個模式是 Spring Boot 專案的標準慣例，直接套用即可。\n\n**ProductService.java — @Transactional 標準用法 (java)**\n```java\n@Service\npublic class ProductService {\n\n    private final ProductRepository productRepository;\n\n    public ProductService(ProductRepository productRepository) {\n        this.productRepository = productRepository;\n    }\n\n   































































































          "body": "Day 2 的工具呼叫其實要建立在穩定的 Service / Repository 流程上。也就是說，AI 不會直接查資料庫，而是會重用你在這一章建立好的後端查詢能力。\n\n因此 JPA 不是孤立的資料庫章節，而是整個 AI 應用可驗證性的基礎。"
        }
      ],
      "prompt": "請接續 Unit 2 的實作內容。現在我們要進入 Unit 3：PostgreSQL、Flyway、JPA 與動態查詢。\n\n我們將使用 AI 協作方式來輔助開發。請在與 AI 溝通時，參考以下高級協作提示詞大師範本以獲取精準的程式碼：\n\n### 用 AI Agent 產生 docker-compose.yml\n【提示詞 1 — 請 AI Agent 建立並啟動】\n請在我的 Spring Boot 專案根目錄建立 docker-compose.yml，需求如下：\n- 使用 pgvector/pgvector:pg18 映像（PostgreSQL 18 + pgvector 向量擴充）\n- 資料庫名稱：learn_spring\n- 使用者：postgres，密碼：password\n- 本機 5432 埠對應容器 5432 埠\n- 使用具名卷（named volume）讓資料持久化，容器重建後資料不遺失\n- 每個設定項目加上中文註解\n\n建立完成後請幫我執行 docker-compose up -d，\n再執行 docker ps，確認容器狀態為 Up。\n\n【提示詞 2 — 排查容器啟動失敗】\n我執行 docker-compose up -d 後容器狀態不是 Up，\ndocker logs 顯示：\n[貼上錯誤訊息]\n請幫我找出原因並修正。\n\n### 用 AI Agent 設定 application.yml 資料庫連線\n【提示詞 1 — 請 AI Agent 設定資料庫連線】\n請修改我的 Spring Boot 專案



























































        },
        {
          "heading": "步驟四：Controller 接收查詢參數",
          "body": "Controller 只負責把 HTTP 查詢字串轉成 Java 型別，業務邏輯與查詢組合全部留在 Service 與 Spec 層。三個參數都是 optional（不帶就是 null），對應到 Service 中的「不套用此條件」。\n\n**ProductController.java — 搜尋端點 (java)**\n```java\n@RestController\n@RequestMapping(\"/api/products\")\npublic class ProductController {\n\n    private final ProductService productService;\n\n    public ProductController(ProductService productService) {\n        this.productService = productService;\n    }\n\n    // GET /api/products/search?name=手機&maxPrice=15000&inStock=true\n    @GetMapping(\"/search\")\n    public List<Product> search(\n            @RequestParam(required = false) String name,\n            @RequestParam(required = false) Double maxPrice,\n            @RequestParam(required = false) Boolean inStock) {\n\n        return productService.search(name, maxPrice, inStock);\n    }\n}\n```"
        },
        {
          "heading": "Specification 產生的 SQL 實際長什麼樣",
          "body": "以下是三種不同傳參組合，對應 Specification 實際產生的 SQL 片段，方便你驗證行為是否符合預期。\n\n**動態 WHERE 子句對照 (sql)**\n```sql\n-- 呼叫：search(\"手機\", 15000.0, true)\nSELECT * FROM products\nWHERE LOWER(name) LIKE '%手






















































        },
        {
          "heading": "建立統一的 ErrorResponse 格式",
          "body": "先定義所有錯誤回應共用的資料結構。使用 Java Record 讓程式碼簡潔，Jackson 自動序列化為 JSON。\n\n**ErrorResponse.java (java)**\n```java\n/**\n * 統一的 API 錯誤回應格式\n * 所有例外處理方法都回傳此格式，讓前端只需解析一種結構\n */\npublic record ErrorResponse(\n    int status,          // HTTP 狀態碼\n    String error,        // 錯誤類型（如 \"Not Found\"）\n    String message,      // 人類可讀的錯誤說明\n    String path,         // 發生錯誤的 API 路徑\n    LocalDateTime timestamp  // 錯誤發生時間\n) {\n    /** 快速建立標準錯誤回應的工廠方法 */\n    public static ErrorResponse of(HttpStatus status, String message, String path) {\n        return new ErrorResponse(\n            status.value(),\n            status.getReasonPhrase(),\n            message,\n            path,\n            LocalDateTime.now()\n        );\n    }\n}\n```"
        },
        {
          "heading": "自訂業務例外類別",
          "body": "用語意明確的例外類別取代直接拋出 `RuntimeException`，讓 GlobalExceptionHandler 能精確攔截並對應正確的 HTTP 狀態碼。\n\n**ResourceNotFoundException.java (java)**\n```java\n/**\n * 查詢資源不存在時拋出，對應 HTTP 404\n */\npublic class ResourceNotFoundException extends RuntimeException {\n\n    public ResourceNotFoundException(String resourceName, Long id) {\n        super(resourceName + \" 不存在：id = \" + id);\n    }\n}\n\n// Service 使用方式\n@Transactional(r























































































          "body": "提供登入端點，傳入帳號密碼並呼叫 `AuthenticationManager` 進行身分驗證。驗證成功後生成 JWT Token 並回傳。\n\n**AuthController.java 範例 (java)**\n```java\n@RestController\n@RequestMapping(\"/api/auth\")\n@Tag(name = \"認證管理\")\npublic class AuthController {\n\n    private final AuthenticationManager authenticationManager;\n    private final JwtUtils jwtUtils;\n\n    public AuthController(AuthenticationManager authenticationManager, JwtUtils jwtUtils) {\n        this.authenticationManager = authenticationManager;\n        this.jwtUtils = jwtUtils;\n    }\n\n    @PostMapping(\"/login\")\n    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {\n        Authentication authentication = authenticationManager.authenticate(\n                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())\n        );\n        SecurityContextHolder.getContext().setAuthentication(authentication);\n        String token = jwtUtils.generateToken(loginRequest.getUsername());\n        return ResponseEntity.ok(new LoginResponse(token));\n    }\n}\n```"
        },
        {
          "heading": "Swagger 網頁驗證步驟（推薦）",
          "body": "後端啟動後，我們可以透過 Swagger UI 網頁進行 API 測試，視覺化地驗證登入、JWT 簽發與角色基礎授權控制（RBAC）是否正常運作。\n\n<div style=\"margin-top: 16px;\"><ul style=\"list-style-type: decimal; margin-left: 20px; margin-bottom: 16px; line-height: 1.8;\"><li style=\"margin-bottom: 6px;\"><strong>開啟 Swagger 網頁</strong>：在瀏覽器中輸入 <a href=\"http://localhost:8080/swagger-ui/index.html\" target=\"_blank\" class=\"accent-link\">http











































































        {
          "heading": "後端安全設定範例 (SecurityConfig.java)",
          "body": "Spring Security 核心設定。我們配置雙重 `SecurityFilterChain`：第一個專門保護 Swagger 文件，限制使用 Basic Auth 登入；第一個保護一般系統 API，採用 JWT 驗證與無狀態 Session 策略，並限制商品寫入為 ADMIN。\n\n**SecurityConfig.java 範例 (java)**\n```java\n@Configuration\n@EnableWebSecurity\npublic class SecurityConfig {\n\n    private final JwtAuthenticationFilter jwtAuthenticationFilter;\n\n    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {\n        this.jwtAuthenticationFilter = jwtAuthenticationFilter;\n    }\n\n    @Bean\n    public PasswordEncoder passwordEncoder() {\n        return new BCryptPasswordEncoder();\n    }\n\n    @Bean\n    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {\n        return config.getAuthenticationManager();\n    }\n\n    /** 1. Swagger UI 與 API 文件安全防護鏈 (以 HTTP Basic 認證保護) */\n    @Bean\n    @Order(1)\n    public SecurityFilterChain swaggerSecurityFilterChain(HttpSecurity http) throws Exception {\n        http\n                .securityMatcher(\"/swagger-ui/**\", \"/v3/api-docs/**\", \"/swagger-ui.html\")\n                .csrf(csrf -> csrf.disable())\n                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))\n                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())\n      



































































































































































































































































        },
        {
          "heading": "串流回應骨架",
          "body": "**ChatClient 鏈式呼叫示意 (java)**\n```java\nreturn chatClient.prompt()\n    .user(message)\n    .advisors(memoryAdvisor)\n    .stream()\n    .content();\n```"
        },
        {
          "heading": "AI Agent 提示詞 — 建立 ChatClient 對話入口",
          "body": "理解上述原理後，複製以下提示詞給 AI Agent，引導它在 Day 1 完成的電商後端上，加入本章的 AI 對話與串流功能。\n\n**AI Agent 提示詞 (text)**\n```text\n請在現有的 Spring Boot 電商專案中加入 AI 對話功能\n\n1. 請在現有的 Spring Boot 電商專案中加入 AI 對話功能\n2. 設定 Groq 的 base-url，模型用 openai/gpt-oss-120b，API Key 為 \"xxxxxxx\"。\n3. 系統提示詞：「你是一個親切的商城智慧助手」。\n4. 使用 SSE串流回覆的API，並掛上記憶，以 sessionId 參數隔離不同使用者的對話記






























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
          "label": "記錄 1 個 AI 查商品即時資料情境"
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
          "alt": "Spring AI ChatClient 與 Tool Calling"
        },
        {
          "name": "u6-2.svg",
          "kind": "diagram",
          "alt": "Server-Sent Events 串流與 Tool Calling 架構"
        },
        {























































        {
          "heading": "QuestionAnswerAdvisor 的價值",
          "body": "Spring AI 2.0 把「檢索相關內容再把上下文送給模型」這件事收斂成 Advisor。這讓程式碼可讀性高很多，也方便後續替換檢索策略。\n\n**RAG 查詢示意 (java)**\n```java\nreturn this.chatClient.prompt()\n    .user(query)\n    .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())\n    .call()\n    .content();\n```"
        },
        {
          "heading": "AI Agent 提示詞 — 建立 RAG 知識庫",
          "body": "理解 RAG 流程與向量檢索原理後，複製以下提示詞給 AI Agent，引導它完成文件上傳、向量化與知識庫問答兩支 API。\n\n**AI Agent 提示詞 (text)**\n```text\n請為專案加入 RAG 知識庫功能，使用 PostgreSQL pgvector 儲存向量：\n1. 在 pom.xml 加入 spring-ai-starter-vector-store-pgvector 依賴，並確認 application.yml 已設定 Voyage AI 的 embedding 端點（環境變數 VOYAGE_API_KEY）。\n2. 建立 RAGController，提供兩支 API：\n   - POST /api/rag/upload：接收上傳的文字檔，用 TextReader 讀取、TokenTextSplitter 切分成小段，最後用 vectorStore.accept() 向量化寫入資料庫\n   - GET /api/rag/query：掛上 QuestionAnswerAdvisor，讓 AI 先檢索相關文件片段再回答\n3. 程式碼需有中文函式註解。\n完成後請示範驗證流程：先上傳一份「退貨政策」文件，再提問「商城退貨政策是什麼？」，確認 AI 是根據文件內容回答，而不是自由











































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
          "alt": "Spring AI ChatClient 與 Tool Calling"
        },
        {
          "name": "u6-2.svg",
          "kind": "diagram",
          "alt": "Server-Sent Events 串流與 Tool Calling 架構"
        },
        {
          "name": "u6-3-term.svg",
          "kind": "term",
          "alt": "SSE 串流與 Function Calling 術語表"
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
































































          "body": "Spring AI 已內建 MCP 支援，接入成本非常低，本章以補充說明的方式帶過即可：Day 2-2 寫好的 `@Tool` Bean 完全不需要修改程式碼，Server 端加一個依賴就會自動透過 `/sse` 端點對外發布為 MCP 工具；另一個應用加上 Client 依賴，啟動時就會自動取回遠端工具清單交給 ChatClient 使用。\n\n想完整動手做的學員，可參考專案中的 `mcp/` 目錄（mcp-client-demo），其中已包含可直接執行的 MCP Client 範例與設定檔。\n\n- Server 端：加入 `spring-ai-starter-mcp-server-webmvc` 依賴，既有 @Tool Bean 自動發布為 MCP 工具\n- Client 端：加入 `spring-ai-starter-mcp-client` 依賴，SyncMcpToolCallbackProvider 啟動時自動連線取回工具\n- Server 與 Client 是兩個獨立服務，必須使用不同 port（例如 8080 / 8081），且要先啟動 Server\n- 工具掛載方式與本地工具一致：把取回的工具陣列傳入 `.tools()`，AI 就會自動選用遠端工具"
        },
        {
          "heading": "AI 呼叫遠端工具的完整流程",
          "body": "以「有哪些商品？」為例，說明從使用者輸入到 AI 回答的跨服務執行路徑：\n\n- ① 使用者呼叫 `GET http://localhost:8081/api/mcp/chat?message=有哪些商品`\n- ② MCP Client（port 8081）的 ChatClient 收到訊息，交由 LLM 判斷意圖\n- ③ AI 判斷需要查商品，透過已建立的 SSE 連線，向 MCP Server（port 8080）發送 getProducts 工具呼叫請求\n- ④ MCP Server 執行 `P


























        }
      ],
      "tasks": [
        {
          "id": "u8-t1",
          "label": "導入 Embabel 0.4.x 並定義客戶建議 of World State 與 Goal"
        },
        {
          "id": "u8-t2",
          "label": "以 Java 21 Record 實作 Action 流程與審核/Replanning 邏輯"
        },
        {
          "id": "u8-t3",
          "label": "在 React 前端開發 Agent Trace 軌跡時間線面板"
        },
        {
          "id": "u8-t4",
          "label": "執行 pnpm run verify 通過完整全端自動化測試與驗收"
        }
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
          "desc": "Embabel GOAP 演算法原理、Blackboard 世界狀態定義及 Type-Safe Action 規劃與 Replanning 機制。"
        }
      ],
      "illustrations": [
        {
          "name": "u7-1.png",
          "kind": "hero",
          "alt": "RAG 知識庫與 MCP 擴充"
        },
        {
          "name": "u7-2.svg",
          "kind": "diagram",
          "alt": "檢索增強生成流程與 MCP 協定"
        },
        {
          "name": "u7-3-term.svg",
          "kind": "term",
          "alt": "Vector Embedding 與 MCP 術語表"
        }
      ]
    },
    {
      "id": "u8",
      "title": "Embabel Agent Flow、測試、觀測與結訓專案",
      "subtitle": "導入 Embabel 智慧 Agent，使用 GOAP 演算法與 Blackboard 機制規劃客戶下一步行動建議，實作決策軌跡 Trace 面板並進行 Playwright 整合驗證。",
      "time": "13:00 ~ 17:00",
      "features": [
        "Embabel Agent Flow 智慧建議",
        "資料不足補件 / 高風險轉交主管",
        "Agent Trace 決策軌跡面板"
      ],
      "goals": [
        "導入 Embabel 0.4.x，以 GOAP action table 設計智慧業務行動建議流程",
        "以 Java 21 Record 實作型別安全與具審核機制 (Replanning) 的 Action 流程",
        "在 React 實作展示 AI 決策流程與 Token 消耗的 Agent Trace 時間線面板",
        "修復 verify-render.mjs，執行 pnpm run verify 確認全站功能通過驗收"
      ],
      "principle": "Embabel 的核心價值在於將不可控的單一 Prompt 推理，轉化為可控、可規劃 (GOAP) 且具備型別約束與輸出審核的 Agent Workflow。透過 Blackboard 與 Agent Trace 機制，讓 AI 決策流程透明、可維護且易於偵錯，符合企業維運的要求。",
      "concepts": [
        {
          "heading": "Embabel 智慧 Agent、GOAP 演算法與 Blackboard 機制",
          "body": "一般的 AI API 呼叫是線性的，若 LLM 回答錯誤或資料不足，則流程直接中斷。Embabel 框架引入了 GOAP (Goal-Oriented Action Planning) 規劃。我們在 Blackboard (黑板) 上宣告目前 World State 與最終 Goal（例如：一份通過審核的下一步客戶業務建議），框架會根據已註冊的 @Action 的前提 (Preconditions) 與效果 (Effects)，以 A* 或類似演算法動態生成一條執行路徑。若途中某個步驟被拒絕（如審核未通過），Agent 將進行 Replanning 重新規劃路徑。設計 Blackboard 能有效克服大語言模型上下文窗口限制與「記憶力碎片化」的痛點。",
        
        {
          "name": "u7-2.svg",
          "kind": "diagram",
          "alt": "檢索增強生成流程與 MCP 協定"
        },
        {
          "name": "u7-3-term.svg",
          "kind": "term",
          "alt": "Vector Embedding 與 MCP 術語表"
        }
      ]
    },
    {
        }
      ],
      "tasks": [
        {
          "id": "u8-t1",
          "label": "導入 Embabel 0.4.x 並定義客戶建議 of World State 與 Goal"
        },
        {
          "id": "u8-t2",
          "label": "以 Java 21 Record 實作 Action 流程與審核/Replanning 邏輯"
        },
        {
          "id": "u8-t3",
          "label": "在 React 前端開發 Agent Trace 軌跡時間線面板"
        },
        {
          "id": "u8-t4",
          "label": "執行 pnpm run verify 通過完整全端自動化測試與驗收"
        }
      ],
      "materials": [],
      "illustrations": [
        {
          "name": "u8-1.png",
          "kind": "hero",
          "alt": "Embabel Agent Flow 與 決策軌跡"
        },
        {
          "name": "u8-2.svg",
          "kind": "diagram",
          "alt": "GOAP A* 規劃與 Blackboard Replanning 流程"
        },
        {
          "name": "u8-3-term.svg",
          "kind": "term",
          "alt": "GOAP, Blackboard 與 Trace UI 專有名詞"
        }
      ]
    }
  ]
};

          "id": "u8-t2",
          "label": "以 Java 21 Record 實作 Action 流程與審核/Replanning 邏輯"
        },
        {
          "id": "u8-t3",
          "label": "在 React 前端開發 Agent Trace 軌跡時間線面板"
        },
        {
          "id": "u8-t4",
          "label": "執行 pnpm run verify 通過完整全端自動化測試與驗收"
        }
      ],
      "materials": [
        {
          "id": "mat8",
          "type": "MD",
          "name": "Embabel_黑板機制與GOAP動作規劃指南",
          "desc": "Embabel GOAP 演算法原理、Blackboard 世界狀態定義及 Type-Safe Action 規劃與 Replanning 機制。"
        }
      ],
      "illustrations": [
        {
          "name": "u8-1.png",
          "kind": "hero",
          "alt": "Embabel Agent Flow 與 決策軌跡"
        },
        {
          "name": "u8-2.svg",
          "kind": "diagram",
          "alt": "GOAP A* 規劃與 Blackboard Replanning 流程"
        },
        {
          "name": "u8-3-term.svg",
          "kind": "term",
          "alt": "GOAP, Blackboard 與 Trace UI 專有名詞"
        }
      ]
    }
  ]
};

