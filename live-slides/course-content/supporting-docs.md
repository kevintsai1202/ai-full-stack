# 課前準備、講師備課與限制

## IssueFlow 專案作業

本課不是分散完成四個互不相關的練習，而是共同完成一個小型 `IssueFlow` 專案。它採用類 Linear 的核心概念：單一專案、Issue／任務清單、狀態、優先級與相依關係；課堂只要求一條可驗證的垂直流程，不要求完整複製 Linear。Linear 的功能與工作價值補充見 [`materials/linear-tool-brief.md`](materials/linear-tool-brief.md)。

請先閱讀 [`materials/issueflow-project-assignment.md`](materials/issueflow-project-assignment.md)。四章的交接順序是：

1. Ch01 交付可行性評估，決定 MVP 與先行 spike。
2. Ch02 交付選型與開發計畫，鎖定可測試的 `BLOCKED` 流程。
3. Ch03 交付 TDD、E2E、截圖與 SOP，形成可執行證據。
4. Ch04 交付稽核、修補前後證據與最終展示摘要。

延伸功能（新增 Issue、篩選、Kanban、`localStorage`）只能在核心流程驗收後加入；登入、多人協作、留言、通知、正式資料庫與雲端部署不列入本課作業。

## 學員課前準備

本課以準備好的 `materials/lab` 為練習環境，不要求學員從零建置完整產品。課前至少完成：

```powershell
node --version
npm --version
python --version
```

若要執行 Playwright：

```powershell
npm install -D @playwright/test
npx playwright install chromium
npx playwright --version
```

Playwright 官方安裝文件提供 `npm init playwright@latest`、瀏覽器安裝與 `npx playwright test` 的完整流程，課堂若使用現有專案，應先確認版本與瀏覽器已安裝。[Playwright Installation](https://playwright.dev/docs/intro)

## 課堂啟動流程

```powershell
cd live-slides/course-content/materials/lab
python -m http.server 4173
```

另開 PowerShell：

```powershell
cd live-slides/course-content/materials/lab
node --test task-rules.test.js
npx playwright test task-tracker.spec.js
```

若使用其他實際專案，將 `baseURL`、測試 selector 與啟動命令改成該專案的真實設定，不要為了讓測試變綠而刪除驗收條件。

## 講師課前檢查

- [ ] 四章提示詞可正常複製。
- [ ] `materials/lab/task-rules.test.js` 先紅後綠。
- [ ] Playwright 可產生截圖與 HTML report。
- [ ] `npm audit --json` 能輸出報告；若沒有漏洞，也保留 exit code 與執行時間。
- [ ] SVG 與 PNG 流程圖均可開啟，中文字沒有被裁切或重疊。
- [ ] 講師知道哪些資料是「來源快照」，哪些是「本次現場重新執行結果」。

## 安全與資料使用界線

- 只對自己擁有或明確獲得授權的程式碼、依賴與測試環境執行稽核。
- 不在公開課堂貼出 API key、token、真實客戶資料或未公開漏洞細節。
- `npm audit` 的結果依 lockfile、registry 與執行時間而變動；教材不固定宣稱某個漏洞數字。
- OWASP、CWE、CVSS 頁面會更新；課堂報告必須記錄來源 URL、版本與執行日期。
- AI 產生的風險判定需要人工審查與實際證據，不可直接作為上線核准。

## 本課不涵蓋

- 從零建置正式身份驗證、資料庫與雲端部署。
- 取代專業滲透測試、紅隊演練或正式合規稽核。
- 以固定的歷史漏洞數字代表所有專案的現況。
- 任何特定 AI 供應商或模型的保證效果。
