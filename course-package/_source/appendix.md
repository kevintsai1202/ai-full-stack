# 附錄（術語 / FAQ / 驗收）

```json
{
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
}
```