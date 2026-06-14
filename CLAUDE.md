# AI 賦能全端開發專案開發總覽 (CLAUDE.md)

本儲存庫包含了「AI 賦能全端開發：從零打造企業級智慧應用」課程的教學網站（`teaching-site`），此網站採用純 HTML + JS + CSS 架構（無框架、無建置步驟）展示整個課程的實戰路徑與任務。

## 目錄導覽
- `teaching-site/`：課程官方教學網站，包含靜態網頁（HTML/CSS/JS）與自動化驗證測試。
- `課程內容.md`：課程的詳細大綱與各單元學習目標、提示詞主題與驗收標準。
- `課程內容需求.md`：原始課程整併需求。
- `課程名稱.md`：開課計畫之官方中英文名稱。

## 教學網站開發指令

### 啟動本地伺服器

進入 `teaching-site` 目錄並啟動靜態檔案伺服器：

```powershell
cd teaching-site
python -m http.server 5173
# 或使用 npx serve -l 5173
```

### 執行自動驗證測試

```powershell
cd teaching-site
node scripts/verify-site.mjs
node scripts/verify-render.mjs
```

## 開發規範
1. 所有程式碼均需具備中文註解。
2. 開發新功能時，請先參閱 `teaching-site/CLAUDE.md` 中更詳細的開發規範。

## GitHub Pages 部署

本專案配置了自動化 GitHub Actions 部署工作流，當推送至 `main` 分支時，會自動將 `teaching-site` 發布至 GitHub Pages。

- **官方發布網址**：https://kevintsai1202.github.io/ai-full-stack/
- **手動觸發部署**：可於 GitHub 專案的 `Actions` 頁面，手動觸發 `Deploy static content to Pages` 工作流。
