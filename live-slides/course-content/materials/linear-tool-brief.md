# Linear 工具旁帶案例：從需求到可追蹤工作

## 使用方式

本文件是課堂的補充閱讀，不要求學員註冊 Linear，也不把 Linear 當作 IssueFlow 作業的執行環境。講師用它說明：為什麼工作團隊需要把需求拆成可追蹤的工作單位，以及工具如何把狀態、範圍、進度與協作上下文放在同一條流程中。

## Linear 在工作流程中的位置

依 Linear 官方入門文件，`Issue` 是較細的工作單位，通常代表一個可以在數小時或數天內完成的具體工作；`Project` 是一組共同指向某個功能或發布目標的 Issues；`Initiative` 則位於更高層，用來組織產品方向。[Intro to Linear](https://linear.app/learn/intro-to-linear)

Linear 的 Project Overview 可以放置專案摘要、詳細說明、外部連結、專案文件與 Milestones，並追蹤里程碑進度。這讓專案不只是一串待辦事項，也包含決策背景與目前狀態。[Project overview](https://linear.app/docs/project-overview) 、[Project milestones](https://linear.app/docs/project-milestones)

Issues 與 Projects 也可以用不同 Views 依狀態、負責人、優先級、Project 或 Milestone 分組與篩選；清單與 Board 是同一批工作資料的不同觀察方式，不是兩份資料。[Display options](https://linear.app/docs/display-options) 、[Custom Views](https://linear.app/docs/custom-views)

Linear 也提供 GitHub 串接，能將 Issue 與 branch、commit、pull request 連結，並依 GitHub 活動自動更新部分 Issue 狀態。課堂只把它當成「需求與程式變更可以互相追溯」的概念示例，不要求現場設定整合。[GitHub integration](https://linear.app/docs/github-integration)

## 對工作的幫助

### 1. 把模糊需求變成可以交接的工作

「做一個任務頁」太大，無法直接分派或驗收；拆成 Issue 後，可以補上標題、狀態、優先級、負責人、Project 與完成條件。這也對應本課 Ch01 的可行性評估：先把範圍與未知數攤開，再決定要不要做。

### 2. 讓進度討論有共同資料

Project、Issue、Milestone 與 View 提供一個共同的工作狀態。團隊會議可以從「誰記得目前做到哪裡」改成「哪些 Issue 被阻塞、哪個 Milestone 落後、下一步需要什麼決策」。這是工作上的推論與使用方式，不代表工具本身會自動消除溝通成本。

### 3. 讓阻塞與優先順序可見

Issue 關係、狀態與優先級能讓團隊看到先後關係與下一步，而不是只看一張沒有上下文的待辦清單。本課的 `BLOCKED`、前置任務、進度與下一步建議，就是把這個工作問題縮小成可以 TDD 與 E2E 驗證的 MVP。

### 4. 連接規格、程式變更與驗收證據

當 Issue 能連到 Project、Milestone、Pull Request 或 commit，團隊比較容易追溯「為什麼做、改了什麼、是否驗證」。但工具連結不能取代驗收標準、測試、SOP 或資安稽核；本課仍要求學員保留自己的文件與執行證據。

## 與 IssueFlow 作業的對照

| Linear 概念 | IssueFlow 教學版 | 課程對應 |
|---|---|---|
| Project | 單一 IssueFlow 專案 | Ch01 定義範圍、Ch02 計畫 |
| Issue | 現有 lab 的 Task／Issue | Ch02 資料模型、Ch03 測試 |
| Status | `TODO`、`IN_PROGRESS`、`DONE`、`BLOCKED` | Ch03 TDD 與 E2E |
| Priority | 優先級與下一步排序 | Ch02 驗收標準、Ch03 回歸 |
| Issue relation | `dependsOnTaskId` 一條前置關係 | Ch01 未知數、Ch03 阻塞流程 |
| Milestone | 課堂四章交付節點 | 四章產物交接 |
| View | 課堂可延伸的篩選／List／Kanban | MVP 完成後才選做 |
| GitHub integration | 課堂以文件與測試證據模擬追溯 | Ch03／Ch04 產物鏈 |

## 工具使用的限制

- 工具不是產品決策；如果 Issue 本身沒有清楚的完成條件，狀態再漂亮也只是管理介面。
- 工具不是測試；`DONE` 不等於通過 TDD、E2E 或安全稽核。
- 工具不是溝通責任的替代品；團隊仍需定義狀態語意、更新規則與交接格式。
- 本課使用 IssueFlow 的最小閉環，是為了讓學員看見工作方法，而不是在兩天內重做完整 Linear。
