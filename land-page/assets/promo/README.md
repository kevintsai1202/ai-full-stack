# Landing Page 宣傳圖片素材包

本素材包依據 `land-page/index.html` 與根目錄課程規格整理，定位為資訊類線上課程常用的宣傳圖組。視覺風格統一採用深藍、青綠、琥珀重點色，對應目前銷售頁的企業級 AI 全端課程語氣。

## 廣告檢視重點

- 目前 Landing page 文案完整，但正式素材只有 `cover.png`、`office.png`、`logo.png`，導致多數說服段落仍偏文字卡片，視覺停頓點不足。
- 最需要補圖的位置是：Hero、完課成果、AI CRM 實戰專案、8 單元課綱、Tool Calling、RAG、職涯加速、課程比較、底部早鳥 CTA。
- 本課程最強賣點不是「學 AI」，而是「從登入、資料庫、權限、React、RAG、Tool Calling 到 Demo Day 的完整 AI CRM」。圖片應反覆強化完整系統與可展示作品，而不是泛用 AI 光效。
- 生成圖中的小字若要作為正式可讀資訊，建議後續改用 HTML/SVG 疊字；目前圖片最適合作為段落主視覺、社群素材與情境輔助圖。

## 素材清單

| 檔名 | 建議用途 | 對應區塊 |
|---|---|---|
| `hero-ai-full-stack.png` | 主視覺、OG 圖候選 | Hero |
| `project-ai-crm-dashboard.png` | AI CRM 成品畫面 | 實戰專案 |
| `curriculum-roadmap.png` | 8 單元路線圖 | 課程大綱 |
| `tech-stack-map.png` | 技術棧整合圖 | 課程介紹 / 技術版本 |
| `feature-tool-calling.png` | Tool Calling 概念圖 | 課程特色 / Unit 6 |
| `feature-rag-knowledge.png` | RAG 知識庫概念圖 | 課程特色 / Unit 7 |
| `career-boost.png` | 職涯升級與作品集訴求 | 職涯加速 |
| `market-advantage.png` | 市場優勢資訊圖 | 市場數據 |
| `audience-fit.png` | 適合對象圖卡 | 這門課為誰而設計 |
| `course-comparison.png` | 與其他課程比較 | 課程比較 |
| `demo-day-flow.png` | 結訓 Demo 流程 | Unit 8 / Demo Day |
| `learning-workspace.png` | 線上學習情境 | 課程介紹 / 學習情境 |
| `early-bird-cta.png` | 早鳥募資促購圖 | 側邊購買卡 / 底部 CTA |
| `_contact-sheet.png` | 批次檢查用總覽圖 | 內部 QA |

## 建議導入順序

目前已導入 `land-page/index.html`。導入策略如下：

1. Hero 背景與側邊購買卡封面使用 `hero-ai-full-stack.png`。
2. 「完課後，你會做出什麼？」使用 `tech-stack-map.png` 作為能力總覽。
3. 「不只教你呼叫 LLM」下方使用 `feature-tool-calling.png` 與 `feature-rag-knowledge.png`。
4. 市場趨勢、職涯加速、適合對象分別使用 `market-advantage.png`、`career-boost.png`、`audience-fit.png`。
5. 實戰專案、課程大綱、Demo Day、課程比較分別使用 `project-ai-crm-dashboard.png`、`curriculum-roadmap.png`、`demo-day-flow.png`、`course-comparison.png`。
6. 學員迴響前使用 `learning-workspace.png` 作為線上學習情境，底部 CTA 使用 `early-bird-cta.png` 作為背景氛圍圖；價格與倒數仍以 HTML 文字為準。

## 生成模式

- 使用模式：內建 `image_gen`。
- 輸出尺寸：每張 `1672x941`，接近 16:9。
- 原始生成檔保留於 Codex 內建生成目錄，本資料夾保存可被專案引用的複本。
