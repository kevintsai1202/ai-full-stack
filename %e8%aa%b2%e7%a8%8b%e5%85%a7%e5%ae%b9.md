# AI 賦能全端開發：從零打造企業級智慧應用

> 課程英文名：AI-Empowered Full-Stack: Building Enterprise-Grade Smart Apps

## 1. 課程定位

本課程整合 `D:\GitHub\learn-spring` 與 `D:\GitHub\learn-embabel` 的內容，重新設計為一門以「AI CRM」為主軸的全端實戰課。學員會從零建立一套企業級 CRM 系統，先完成 Spring Boot 後端、React 前端、資料庫、認證授權與 API 文件，再逐步加入 Spring AI、RAG、工具呼叫、MCP 與 Embabel agent orchestration，最後完成可登入、可查詢客戶、可產生 AI 客戶摘要、可產生銷售建議、可追蹤 agent 執行紀錄的完整系統。

這不是把 Spring 與 Embabel 分成兩門課串在一起，而是用同一套 AI CRM 範例把每個技術放進同一條產品開發路線中。每一章的產出都會成為後續章節的基礎，避免學員只學到零散範例。

## 2. 技術版本基準

| 類別 | 課程採用 |
|---|---|
| Java | Java 21 |
| 後端框架 | Spring Boot 3.5.x |
| Web | Spring MVC |
| ORM | Spring Data JPA |
| 資料庫 | PostgreSQL + pgvector |
| Migration | Flyway |
| 安全 | Spring Security + JWT |
| API 文件 | SpringDoc OpenAPI |
| AI | Spring AI 1.1.x |
| Agent | Embabel 0.4.x |
| 前端 | React + TypeScript + Vite |
| 開發環境 | Windows + PowerShell 7+ |

> 版本說明：整合 Embabel 時以 Spring Boot 3.5.x 作為主線，因為目前來源課程已明確標註 Embabel 0.4.x 尚不支
<truncated 17558 bytes>

## 10. 教學節奏建議

每個單元都採用同一個節奏：

1. 原理講解：先說明技術解決什麼問題。
2. 系統情境：把技術放回 AI CRM 需求。
3. 程式範例：由講師帶著建立最小可用版本。
4. AI Agent 提示詞：讓學員用 AI 協助產生或檢查程式。
5. 驗證情境：用 PowerShell、Swagger、前端畫面或測試確認結果。
6. 常見錯誤排查：補上 Windows、Java、Maven、Docker、JWT、SSE、RAG 與 Embabel 常見問題。

## 11. 與來源課程的整合方式

| 來源 | 原始重點 | 新課程中的位置 |
|---|---|---|
| `learn-spring` Day 1 | Spring Boot、MVC、JPA、Flyway、Security、API 文件、錯誤處理 | Unit 1-4 |
| `learn-spring` Day 2 | Spring AI、tool calling、RAG、MCP、React、SSE、完整系統驗證 | Unit 5-7 |
| `learn-embabel` | GOAP、Action、Goal、Blackboard、Spring Boot 落地、工具分工、測試觀測 | Unit 8 |

新課程會將 `learn-spring` 原本偏電商客服的範例改為 CRM 場景，並將 `learn-embabel` 原本旅遊客服活動摘要的 agent flow 轉化為「客戶摘要、商機風險、產品推薦、下一步行動審核」流程。這樣技術線一致，業務案例也從頭到尾連貫。

## 12. 課程賣點文案

這門課不是只教你「怎麼呼叫 LLM API」，而是帶你從零打造一套真正能放進企業系統的 AI 全端應用。你會學到 Spring Boot、Spring MVC、Spring Data JPA、Spring Security、Spring AI、Embabel 與 React 如何各司其職，並用一個完整 AI CRM 專案串起資料庫、權限、前端、RAG、工具呼叫與 agent flow。

課程結束時，你不只會有一個聊天機器人，而是一套可登入、可查資料、可引用知識庫、可產生業務建議、可追蹤 AI 執行過程的企業級智慧應用。
