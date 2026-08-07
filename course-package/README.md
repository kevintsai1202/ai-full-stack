# 課程素材包：駕馭 AI 的全端實戰養成班

依 Hahow 課程頁（https://hahow.in/cr/ai-full-stack）的官方「預計單元」（9 章 36 單元、7 項作業）組織，
素材內容以 `teaching-site/course-data.js` 為主要來源，每個小節附上實際授課用的口語稿。

## 目錄結構

- 每個章節一個目錄（`ch01` ~ `ch09`），加上達標解鎖章 `bonus-cloudflare-tunnel`。
- 每個小節（單元）一個 markdown 檔，含：單元定位、教學素材、示範提示詞、**口語稿**。
- 各章的作業獨立成 `assignment-1.md`。
- `_source/`：由 `scripts/export-teaching-site-content.mjs` 從 teaching-site 匯出的原始素材（可重跑），**請勿手改**。

## 章節 ↔ 素材來源對照

| 目錄 | Hahow 章節 | teaching-site 來源 | 小節數 |
|---|---|---|---|
| `ch01-env-and-ai-workflow` | 章節 1｜開發環境、專案骨架與 AI 協作流程 | `_source/u1.md` | 4 單元＋作業 1 |
| `ch02-spring-mvc-rest-domain` | 章節 2｜Spring MVC、REST API 與 CRM Domain Modeling | `_source/u2.md` | 5 單元＋作業 1 |
| `ch03-persistence-and-search` | 章節 3｜資料持久化與搜尋 | `_source/u3.md` | 6 單元＋作業 1 |
| `ch04-security-jwt-openapi` | 章節 4｜Spring Security、JWT、OpenAPI 與企業級錯誤處理 | `_source/u4.md` | 4 單元＋作業 1 |
| `ch05-react-crm-workbench` | 章節 5｜React CRM 工作台與前後端整合 | `_source/u5.md` | 4 單元＋作業 1 |
| `ch06-spring-ai-sse-toolcalling` | 章節 6｜Spring AI ChatClient、SSE 與 tool calling | `_source/u6.md` | 4 單元＋作業 1 |
| `ch07-rag-pgvector-mcp` | 章節 7｜RAG、pgvector、MCP 與知識庫擴充 | `_source/u7.md` | 4 單元＋作業 1 |
| `ch08-capstone-demo-day` | 章節 8｜結訓專案衝刺與 Demo Day 驗收 | `_source/u8.md` | 2 單元 |
| `ch09-dev-skills` | 章節 9｜常用開發技能介紹 | `_source/superpowers.md` | 3 單元 |
| `bonus-cloudflare-tunnel` | 達標解鎖｜Cloudflare Tunnel 上線實戰 | `_source/u9.md` | 1 單元（未列入 Hahow 官方 36 單元） |

## 小節檔案格式

每個小節檔案統一使用以下結構：

```markdown
# 章節 N 單元 M｜{單元標題}

## 單元定位
（本節要解決的問題、與前後節的銜接、建議時長）

## 教學素材
（從 teaching-site 對應 concepts 摘錄整理的講解內容）

## 示範與提示詞
（本節示範用的 AI 提示詞與驗證方式，若無則省略）

## 口語稿
（實際錄課／授課時的逐字口語講稿）
```

## 口語稿風格約定

- 繁體中文、自然口語，像在跟一位坐在旁邊的工程師朋友講話。
- 每節開場先講「為什麼」（痛點或情境），再進入「怎麼做」。
- 講到示範操作時，用「我們現在來⋯⋯你會看到⋯⋯」的帶操作語氣。
- 收尾一句話總結本節重點，並預告下一節。
- 貫穿全課的信任邊界口訣：「數字由程式算、文字由 AI 寫」。
