# 測驗題

```json
[
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
]
```