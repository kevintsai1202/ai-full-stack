# 設計文件：100 人達標解鎖課程 — Cloudflare Tunnel 上線實戰

- 日期：2026-07-21
- 狀態：已與課程作者確認方向（架構主軸、網域範圍、呈現方式三項決策均已核准）

## 1. 背景與目標

課程「AI 賦能全端開發：從零打造企業級智慧應用」目前有 8 個正式單元，結訓專案止於本機 Demo Day 驗收，缺少「把系統推上網」的最後一哩。本設計新增一個**募資達 100 人解鎖**的加碼單元，教學員把整套 AI CRM 部署上線。

## 2. 已驗證的技術事實（2026-07 查證）

| 問題 | 結論 |
|---|---|
| Cloudflare 能否用 Docker 部署？ | Cloudflare Containers 已於 2026-04-13 GA，可用 `wrangler deploy` 部署 Docker 映像，但需 Workers Paid（US$5/月），且容器為無狀態、閒置即睡的運算單元，**不適合跑 PostgreSQL/pgvector**（D1 為 SQLite，無 pgvector）。 |
| 前後端+資料庫整套 Docker 的正解 | Docker Compose 跑在**自己的內網機器**（frontend/backend/postgres/cloudflared 四服務），Cloudflare Tunnel 負責對外流量。 |
| Tunnel 是否需要自備網域？ | Named Tunnel（固定網址）**需要**網域掛在 Cloudflare DNS（免費方案即可，網域約 US$10/年）；Quick Tunnel（TryCloudflare）**不需要**網域與帳號，取得隨機 `*.trycloudflare.com` 網址，適合 demo。兩者皆免公網 IP、免開防火牆 port。 |

## 3. 設計決策（已核准）

1. **架構主軸**：內網自架 + Cloudflare Tunnel。Cloudflare Containers 與 Pages 只做定位比較，不實作。
2. **網域範圍**：主線教 Quick Tunnel（免網域）；Named Tunnel + 自備網域列為選做延伸。
3. **呈現方式**：`課程內容.md` 與 `teaching-site` 同步更新。

## 4. 變更內容

### 4.1 課程內容.md

- 第 6 節「課程架構總覽」表格新增一列：解鎖加碼單元（標明 100 人達標解鎖）。
- 第 7 節「詳細課綱」在 Unit 8 之後新增 `### Bonus Unit（100 人達標解鎖）：Cloudflare Tunnel 上線實戰——把 AI CRM 從內網推向全世界`，沿用現有模板（學習目標／課程內容／實作任務／AI Agent 提示詞主題／驗收標準）。
- 第 9 節「課程素材清單」新增 `cloudflare-tunnel-deploy.md` 一列。
- 核心驗收標準：用手機 4G（非同一內網）開啟前端、登入、完成一次 AI 對話與 RAG 查詢。

### 4.2 teaching-site

- `course-data.js`：day2.units 新增第 9 個單元（id `u9`），新增 `unlock: { threshold: 100, label: "募資達 100 人解鎖" }` 欄位；內容含 subtitle、features、goals、principle（≥80 字）、concepts（含群組分頁）、prompt/promptMac、prompts（至少 1 build + 1 verify，禁止 code fence）、tasks、illustrations（hero/diagram/term 3 張）。
- `app.js`：`renderUnitCard` 與 roadmap 卡片依 `unit.unlock` 渲染「🔒 解鎖徽章」；不影響無此欄位的既有單元。
- `styles.css`：新增解鎖徽章樣式（深淺色主題皆可讀）。
- `assets/illustrations/`：新增 u9 三張實體圖（沿用現有產圖流程，SVG 可維護格式）。
- `scripts/verify-site.mjs`：單元數 8 → 9。
- 驗證：`node scripts/verify-site.mjs` 與 `node scripts/verify-render.mjs` 全數通過。

## 5. 不做的事（YAGNI）

- 不教 Cloudflare Containers / Pages 實作（僅比較定位）。
- 不做 Kubernetes、CI/CD pipeline。
- 不動 quiz 題庫、不動 land-page 宣傳頁（另行任務）。
- 不代學員購買網域；課程僅示範流程與判斷準則。

## 6. 風險與緩解

- **verify-render.mjs 依賴 Playwright**：teaching-site 已有 node_modules，若瀏覽器未安裝則以 `npx playwright install chromium` 補齊。
- **單元數硬編碼**：除 verify-site.mjs 外，若 app.js/導覽列有其他硬編碼 8 的位置，需一併排查（實作時 grep 確認）。
- **解鎖語意**：課綱與網站僅「標示」解鎖條件，不實作真正的鎖定邏輯（內容照常可讀），符合募資宣傳慣例。
