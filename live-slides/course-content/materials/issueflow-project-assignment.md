# IssueFlow 專案作業：打造一個類 Linear 的可審查任務工作台

## 作業定位

本作業不是複製完整 Linear，而是用一個小型 `IssueFlow` 專案練習「需求評估 → 技術計畫 → 開發驗收 → 上線前稽核」的完整閉環。學員每一章都要留下可交接的產物，下一章直接使用上一章的結果。

為了與既有 `materials/lab/` 對接，程式內可以繼續使用 `task`、`dependsOnTaskId` 等欄位名稱；產品與畫面說明則以「Issue／任務」並列稱呼。

## Linear 工具旁帶

課堂會用約 10 分鐘介紹 Linear 的工作方法，不要求註冊或操作真實 Linear。學員閱讀 [`linear-tool-brief.md`](linear-tool-brief.md)，並在 Ch01 或 Ch02 的作業中補寫：

- Linear 的 `Project`、`Issue`、`Milestone`、`View` 分別解決什麼工作問題？
- IssueFlow MVP 借用了哪些概念？哪些功能刻意不做？
- 在自己的工作中，哪一個交接或進度同步問題可能受益？哪一個問題仍需要團隊規則或測試解決？

這段反思是工具觀察，不列為核心程式實作；若時間不足，保留核心 IssueFlow 驗收優先。

## MVP 範圍

### 必做功能

1. 顯示單一專案中的 Issue／任務清單。
2. 顯示標題、優先級、建立時間、狀態與前置任務。
3. 前置任務未完成時，後續任務顯示 `BLOCKED` 且不可完成。
4. 前置任務完成後，後續任務解除阻塞，完成比例與下一步建議更新。
5. 以 TDD、Playwright E2E 與截圖 SOP 保留驗收證據。
6. 以 OWASP／CWE 與相依套件資料完成基準式稽核，並修補一個可重現問題。

### 有時間才做

- 新增／編輯 Issue 的表單。
- 依狀態、優先級或關鍵字篩選。
- List 與簡化 Kanban 兩種檢視。
- `localStorage` 保存本機資料。

以上延伸功能只有在核心流程已通過驗收後才能加入；不能用延伸功能取代測試、SOP 或稽核產物。

### 本課不做

- 登入、團隊邀請、角色權限與多人即時協作。
- 正式後端、資料庫 migration、通知、留言、計費與雲端部署。
- 完整複製 Linear 的產品功能或視覺介面。
- 未經授權的真實系統掃描或滲透測試。

## 四章作業分配

| 章節 | 作業任務 | 學員操作 | 本章交付物 | 下一章用途 |
|---|---|---|---|---|
| Ch01 想法期 | `d1-u1-t1`、`d1-u1-t2`、`d1-u1-t3`、`d1-u1-t4` | 定義 IssueFlow MVP 邊界、列出未知數、完成可行性報告與 15 分鐘 spike | `artifacts/issueflow/ch01-feasibility-report.md` | 作為選型與計畫的輸入 |
| Ch02 設計期 | `d1-u2-t1`、`d1-u2-t2`、`d1-u2-t3`、`d1-u2-t4` | 比較實作方案，拆出資料、規則、畫面、測試資料與里程碑 | `artifacts/issueflow/ch02-technology-selection.md`、`ch02-development-plan.md` | 作為開發與測試規格 |
| Ch03 開發驗收期 | `d2-u1-t1`、`d2-u1-t2`、`d2-u1-t3`、`d2-u1-t4` | 先寫紅燈測試，再完成阻塞規則，建立 E2E 與截圖 SOP | `artifacts/issueflow/ch03-validation/` | 作為安全稽核的可執行證據 |
| Ch04 上線前 | `d2-u2-t1`、`d2-u2-t2`、`d2-u2-t3`、`d2-u2-t4` | 建立基準稽核表、執行相依套件稽核、重現並修補一項問題 | `artifacts/issueflow/ch04-security-audit.md`、`ch04-remediation.md`、`final-summary.md` | 結訓展示與最終交付 |

## 各章完成條件

### Ch01：可行性評估

- 報告明確寫出 MVP 必做、延伸與不做範圍。
- 至少列出三個未知數，包含 `BLOCKED` 的推導方式、依賴資料錯誤與畫面更新時機。
- 至少一個未知數有 15 分鐘內可完成的 spike。
- 不能以「應該可以」作為唯一結論。

### Ch02：選型與開發計畫

- 至少公平比較三個方案。
- 計畫至少包含資料模型、規則層、畫面層、測試資料與四個里程碑。
- 必須把「任務 2 被任務 1 阻塞，完成任務 1 後解除」寫成輸入、動作、預期結果。
- 必須明確列出本課不做的 Linear 類功能。

### Ch03：TDD、E2E 與 SOP

- 紅燈能證明測試驗證的是 IssueFlow 規則，而不是環境錯誤。
- 綠燈至少涵蓋正常路徑、阻塞路徑與一個錯誤／邊界案例。
- E2E 至少跑完：開頁 → 確認 BLOCKED → 完成前置 → 確認解除 → 檢查進度。
- SOP 每一步都具備操作、截圖、預期結果與失敗處理。

### Ch04：安全基準與修補

- 稽核表同時列出命中、未命中與無法判定項目。
- 相依套件報告記錄執行日期、版本、lockfile 限制與實際輸出。
- 至少一項問題有修補前回應、修補後回應與回歸測試。
- 最終報告清楚說明 AI 輔助稽核不等於滲透測試。

## 最終提交結構

```text
artifacts/issueflow/
├── ch01-feasibility-report.md
├── ch02-technology-selection.md
├── ch02-development-plan.md
├── ch03-validation/
│   ├── task-rules.test.js
│   ├── task-dependency.spec.js
│   ├── tdd-red.txt
│   ├── tdd-green.txt
│   ├── screenshots/
│   └── sop.md
├── ch04-security-audit.md
├── ch04-remediation.md
└── final-summary.md
```

## 結訓展示

每組用 5 分鐘展示一條垂直流程：說明需求邊界、展示 `BLOCKED` 到解除阻塞、執行一條 E2E、出示一項修補前後證據，最後說明哪一個功能刻意沒有做以及原因。
