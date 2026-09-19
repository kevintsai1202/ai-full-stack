# 課程素材包：駕馭 AI 的全端實戰養成班

依 Hahow 課程頁（https://hahow.in/cr/ai-full-stack）的官方「預計單元」（9 章 36 單元、7 項作業）組織，
素材內容以 `teaching-site/course-data.js` 為主要來源，每個小節附上實際授課用的口語稿。

## 目錄結構

- `00-course-orientation.md`：全課開場「課程總覽、共用情境與這門課怎麼上」（先說明 32 小時課程路線、AI CRM 產出與三家共用 B2B 客戶，再講影片與教學網站的分工、可重複觀看、指令與下載連結都在網站上）。章節 1 的實際播放順序是先播放 `ch01-env-and-ai-workflow/00.mp4`（搭配同目錄的 `00.mp3`、`00.srt`），再進入 `01-environment-setup`；**不列入 Hahow 官方 36 單元**。
- 每個章節一個目錄（`ch01` ~ `ch09`），加上達標解鎖章 `bonus-cloudflare-tunnel` 與延伸部署章 `bonus-deployment`。
- 每個小節（單元）一個 markdown 檔，含：單元定位、教學素材、示範提示詞、**口語稿**。
- 各章的作業獨立成 `assignment-1.md`。
- `_source/`：由 `scripts/export-teaching-site-content.mjs` 從 teaching-site 匯出的原始素材（可重跑），**請勿手改**。
- `teaching-site-mapping.md`：逐一列出每個內容檔、旁白稿、網站 source 概念、提示詞、任務與操作驗收；新增或拆分小節時先更新這份對照表。

## 章節 ↔ 素材來源對照

| 目錄 | Hahow 章節 | teaching-site 來源 | 小節數 |
|---|---|---|---|
| `00-course-orientation.md` | （開場，非官方單元） | `teaching-site` 的 `meta`、`overview`、`sharedCase` 與網站結構 | 1 篇開場 |
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
| `bonus-deployment` | 延伸部署｜從 Docker 到 Kubernetes | `course-data.js` 之 `u10`～`u12`（`_source/` 未匯出） | 3 單元（未列入 Hahow 官方 36 單元） |

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

## 逐步操作與驗收
（實際執行順序、預期結果與證據、失敗分流、章節銜接或作業完成條件）

## 口語稿
（實際錄課／授課時的逐字口語講稿）
```

旁白稿另外必須包含 `## 邊念邊操作提示卡`，把錄音時的畫面切換、停頓點、命令執行、預期畫面與錯誤保留方式寫清楚；這個區塊不取代逐字講稿，而是讓講者能同步操作並留下驗收證據。

## 口語稿風格約定

- 繁體中文、自然口語，像在跟一位坐在旁邊的工程師朋友講話。
- 每節開場先講「為什麼」（痛點或情境），再進入「怎麼做」。
- 講到示範操作時，用「我們現在來⋯⋯你會看到⋯⋯」的帶操作語氣。
- 收尾一句話總結本節重點，並預告下一節。
- 貫穿全課的信任邊界口訣：「數字由程式算、文字由 AI 寫」。

## 邊念邊操作的使用規則

- `*-旁白稿.md` 是錄音與授課用的操作腳本；每段都有建議音檔名，並在「我們現在來」「請執行」「打開」「驗證」等句子安排實際停頓點。
- 朗讀到操作句時先停頓完成動作，再念下一段的預期畫面或結果；完整命令、提示詞與錯誤排查放在同名的教學內容檔，不把關鍵操作藏在口語稿裡。
- 每段操作至少要留下終端機輸出、瀏覽器畫面、API 回應、Log 或測試結果其中一種證據；只看到服務啟動不算功能驗收。
- 逐檔來源請查閱 [`teaching-site-mapping.md`](./teaching-site-mapping.md)。其中明確標示了官方 `u1`～`u9`／`superpowers` 與素材包延伸內容的邊界。
