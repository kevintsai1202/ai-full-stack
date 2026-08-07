# 課程總覽與共用案例

```json
{
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
        "Spring Boot 4.1.x"
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
  }
}
```