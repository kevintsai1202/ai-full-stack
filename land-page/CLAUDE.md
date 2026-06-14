# Hahow 課程銷售頁開發指南 (CLAUDE.md)

本專案為「AI 賦能全端開發：從零打造企業級智慧應用」課程的 Hahow 課程銷售 Landing Page 範本。採用 HTML + React (CDN) + Babel Standalone 架構（無建置步驟），方便快速預覽與調整。

## 開發與運行指令

### 本地服務啟動
本專案為純靜態網站，可用任何靜態檔案伺服器啟動：

> **跨平台說明**：Windows 環境下請使用 PowerShell 7+ 執行。

```powershell
# 進入銷售頁目錄
cd land-page

# 方法一：使用 Python 內建伺服器
python -m http.server 5174

# 方法二：使用 npx serve（需要 Node.js）
npx serve -l 5174

# 開啟瀏覽器造訪: http://127.0.0.1:5174
```

## 檔案結構說明
- `index.html` — 網頁入口，載入 CDN 資源（React, ReactDOM, Babel, Lucide）以及各個 JSX 元件。
- `data.js` — 銷售頁核心資料庫 (`window.COURSE`)，保存所有的課程資訊、大綱、價格、常見問題等。
- `icons.jsx` — Lucide 圖標封裝元件，將 Lucide 轉換為 React 可直接呼叫的 `<window.Icon>`。
- `parts1.jsx` — 包含 `Header`, `Hero`, `PurchaseCard` (右側固定的購買卡片) 元件。
- `parts2.jsx` — 包含 `SectionTitle`, `Intro` (完課能力), `Features` (課程特色), `Audience` (適合對象) 元件。
- `parts3.jsx` — 包含 `ProjectShowcase` (實戰專案展示), `Curriculum` (課程大綱手風琴) 元件。
- `parts4.jsx` — 包含 `Instructor` (講師介紹), `Faq` (常見問題), `PricingCTA` (底部募資按鈕), `Footer` 元件。
- `App.jsx` — 主應用邏輯與版面配置，負責組裝所有元件並掛載至 `#hc-app`。
- `_ds/` — 設計系統 (Design System) 套件，提供 UI 元件（`Button`, `Badge`, `Card`, `ProgressBar` 等）與 CSS 樣式。
- `assets/` — 存放首頁 cover、logo 等靜態視覺資源。

## 程式碼開發規範
1. **繁體中文註解**：所有撰寫的 JavaScript / JSX 程式碼，函數級別與重要變數均須使用繁體中文註解。
2. **設計系統一致性**：所有元件樣式必須遵循設計系統規範，直接使用 `window.DesignSystem_55f34f` 提供的 UI 元件，或使用定義好的 CSS 變數（如 `var(--accent)`, `var(--fg)`, `var(--bg)` 等）。
3. **資料驅動渲染**：所有的內容、文字與狀態必須與 `data.js` 的 `window.COURSE` 資料連動，不應在 JSX 元件中寫死靜態字串。
4. **組件模組化**：元件依照功能與區塊，分別放置於對應的 `parts` 檔案中，並使用 `Object.assign(window, { ... })` 掛載到全域環境以供 `App.jsx` 組合。
