# AI CRM 課程教學網站開發指南 (CLAUDE.md)

本專案為「AI 賦能全端開發：從零打造企業級智慧應用」課程的互動式教學網站。採用純 HTML + JS + CSS 架構（無框架、無建置步驟），資料與渲染邏輯分離，進度儲存於本地瀏覽器 (localStorage)。

## 開發與運行指令

### 本地服務啟動
本專案為純靜態網站，可用任何靜態檔案伺服器啟動：

> **跨平台說明（Windows / macOS）**：Windows 使用 PowerShell 7+，macOS 使用 Terminal (zsh)。

```powershell
# 方法一：使用 Python 內建伺服器
python -m http.server 5173

# 方法二：使用 npx serve（需要 Node.js）
npx serve -l 5173

# 開啟瀏覽器造訪: http://127.0.0.1:5173
```

### 自動化測試驗證
```powershell
# 僅執行資料完整性與檔案結構校驗 (verify-site.mjs)
node scripts/verify-site.mjs

# 僅執行瀏覽器渲染、RWD 與功能互動測試 (verify-render.mjs，需要 Playwright)
node scripts/verify-render.mjs
```

## 檔案結構說明
- `index.html` — 網站入口，直接載入 `styles.css`、`course-data.js` 與 `app.js`。
- `app.js` — 主應用邏輯，負責將 `course-data.js` 中的結構化資料渲染為課程網站，並管理主題、搜尋、進度、測驗與複製提示詞互動。使用事件委派（Event Delegation）模式處理所有互動事件。
- `styles.css` — 設計系統與樣式表，包含磨砂玻璃卡片效果、深淺色切換變數及回應式排版。
- `course-data.js` — 核心資料庫 (window.COURSE)，保存所有的單元大綱、高級 AI 提示詞大師範本、技術原則與學習任務。
- `assets/illustrations/` — 存放網頁上呈現的視覺素材；首頁與各單元 hero 使用 PNG 生圖，流程圖與專業術語解釋圖使用可維護 SVG。
- `scripts/generate-assets.mjs` — 視覺素材 manifest 與 SVG 產生器，負責同步每個單元的 hero、diagram 與 term 圖片引用。
- `scripts/verify-site.mjs` — 資料校驗腳本，驗證 8 個單元、所有引用圖片存在性，與提示詞字數限制。
- `scripts/verify-render.mjs` — Playwright 自動化驗證腳本，測試 RWD 水平溢出、進度儲存與複製按鈕。

## 程式碼開發規範
1. **繁體中文註解**：所有撰寫的 JavaScript / Node 腳本，函數級別與重要變數均須使用繁體中文註解。
2. **純 JS 原則**：互動 UI 以 vanilla JavaScript 撰寫，不引入任何前端框架；使用 template literal 產生 HTML，透過 `data-action` 屬性做事件委派。
3. **資料與渲染分離**：所有課程教學內容與任務字串，必須定義在 `course-data.js` 物件中；`app.js` 僅能讀取此資料進行渲染。
4. **防禦性安全性**：所有動態插入的文字必須經過 `esc()` 函式跳脫 HTML 特殊字元，防止 XSS 攻擊。
5. **圖片覆蓋率底線**：每個新增的單元在 `course-data.js` 裡必須配置 `illustrations` 陣列（至少 3 個圖片：hero、diagram、term），且必須在 `assets/illustrations/` 目錄中建立實體 SVG/PNG 檔案。
