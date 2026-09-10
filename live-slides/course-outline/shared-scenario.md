# 共用案例：IssueFlow 類 Linear 任務工作台

## 案例定位

課程沿用 `live-slides` 直播示範中的小型任務管理 Web 專案，將它包裝成教學版 `IssueFlow`。它借用 Linear 類工具的核心概念：一個專案、一組 Issue／任務、狀態、優先級與相依關係；不要求複製完整產品。它的功能不大，但能同時展示需求不確定性、可驗收規則、TDD、E2E、SOP 與上線前稽核，適合在 12 小時內完成一個可觀察的最小閉環。

## 核心情境

專案負責人要在 IssueFlow 中管理一個版本的開發任務。當前置任務尚未完成時，後續 Issue 必須顯示為 `BLOCKED`，不能被標記完成，也不應被系統推薦為下一步。前置任務完成後，阻塞狀態解除、進度更新，下一個可執行 Issue 重新排序。

學員不是單純把功能做出來，而是要讓 AI 交出可以被人檢查的中間產物：先說明能不能做，再說明怎麼做，接著用測試證明規則，最後依外部基準檢查風險。

## 最小資料形狀

```text
Project
  id, name

Issue / Task
  id, title, priority, createdAt, status, dependsOnTaskId

Test evidence
  scenario, step, screenshotPath, expectedResult

Audit finding
  baselineId, matched, location, severity, recommendation
```

## 四章共用流程

| 階段 | 案例問題 | 學員產物 |
|---|---|---|
| 想法期 | IssueFlow MVP 能不能在課堂限制內完成？有哪些未知數？ | 可行性評估報告 |
| 設計期 | 用什麼技術與模組切分最符合現有限制？ | 選型表與開發計畫 |
| 開發驗收期 | 如何證明 Issue 從 `BLOCKED` 到可完成、進度前進與推薦排序都正確？ | 紅綠測試、E2E 腳本、截圖 SOP |
| 上線前 | 程式與第三方相依套件有哪些可被重現的風險？ | 基準式稽核與修補證據 |

## 案例範圍控制

- 本課只要求一個專案、兩至三個 Issue／任務與一條相依關係，避免把時間花在完整產品功能。
- Linear 類功能只取「專案／Issue／狀態／優先級／相依關係」作為 MVP；新增表單、篩選、Kanban、留言與多人協作列為延伸。
- Ch01、Ch02 先不寫大量程式，重點是把規格與驗收標準寫清楚。
- Ch03 只實作一條高價值流程；若現場環境不穩，使用既有示範專案與備援影片。
- Ch04 以一個可重現的錯誤回應或資訊洩漏問題作為修補目標，不宣稱完成滲透測試。
