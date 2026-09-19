# AI 賦能全端開發專案開發總覽 (AGENTS.md)

本儲存庫包含了「AI 賦能全端開發：從零打造企業級智慧應用」課程的教學網站（`teaching-site`），以及可獨立放映的直播投影片（`live-slides`）。兩者均採用靜態 HTML + JS + CSS 架構（無框架、無建置步驟）。

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
2. 開發新功能時，請先參閱 `teaching-site/AGENTS.md` 中更詳細的開發規範。

## 字幕與斷句規範
1. 以「語意完整、自然閱讀」為最高原則斷句，不要只按照固定字數切割。
2. 絕對不要把一個完整詞彙、專有名詞、人名、品牌名、數字＋單位拆開。
3. 不要在語法關係緊密的位置斷開，例如：
   - 主詞／謂語之間
   - 動詞／受詞之間
   - 介系詞／其後名詞之間
   - 修飾語／被修飾語之間
   - 助動詞／主要動詞之間
   - 固定搭配、慣用語或片語中間
4. 優先在自然的語意停頓處斷句，例如：
   - 逗號、句號、問號等標點附近
   - 子句與子句之間
   - 語意轉折處
   - 說話者自然換氣或停頓的位置
5. 每一段字幕都應該讓觀眾「單獨看到這一段時，也能快速理解」。
6. 避免上一段留下懸而未決的詞，例如「因為、所以、但是、如果、就是、而且」等，除非口語節奏確實需要。
7. **標點符號規範**：字幕原則上不加入標點符號，僅有特殊必要（如書名號、頓號）可視情況使用；句中停頓（如逗號「，」、句號「。」）建議以半形空格取代，句末不留標點或空格。

## GitHub Pages 部署

本專案配置了自動化 GitHub Actions 部署工作流，當推送至 `main` 分支時，會自動將 `teaching-site` 發布至 GitHub Pages 根網址，並將 `live-slides` 發布至同一 Pages 網站的 `/live-slides/` 子路徑。

- **官方發布網址**：https://kevintsai1202.github.io/ai-full-stack/
- **直播投影片網址**：https://kevintsai1202.github.io/ai-full-stack/live-slides/
- **手動觸發部署**：可於 GitHub 專案的 `Actions` 頁面，手動觸發 `Deploy static content to Pages` 工作流。
