# Day 1｜從 IssueFlow 想法到可執行計畫

> 本日 6 小時，對應 `course-outline/day1/outline.md`。兩個單元各 3 小時；以 [IssueFlow 專案作業](../materials/issueflow-project-assignment.md) 為主線，先產出文件，再把文件交給下一日的測試與稽核流程使用。

## 教學節奏

1. 先用一個「同需求、不同問法」的反差建立問題。
2. 講師示範完整提示詞與輸出，不只展示一句漂亮答案。
3. 學員使用共用案例完成自己的產物。
4. 以同儕互審和可勾選標準收尾；沒有達到標準的文件不能進下一單元。

## u-d1-u1：把「能不能做」轉成可行性評估

**對應章節**：Ch01｜想法期（P1–P14）  
**建議時長**：3 小時（講授／示範 1 小時、實作 1.5 小時、回顧／測驗 0.5 小時）  
**對應任務**：`d1-u1-t1`、`d1-u1-t2`、`d1-u1-t3`、`d1-u1-t4`  
**對應素材**：`materials/issueflow-project-assignment.md`、`materials/linear-tool-brief.md`、`materials/task-tracker-requirement.md`、`materials/feasibility-report-template.md`、`materials/glossary.md`

**圖片需求 (illustrations)**:

- `d1-u1-agentic-loop.png` — `diagram`／把理解需求、規劃、生成、驗證、修正畫成循環，標出人的審查點。
- `d1-u1-auditable-output.png` — `diagram`／把模糊問句轉成可行性報告，顯示報告欄位與下一步 spike。
- `01-feasibility-difficulty.png` — `screenshot`／使用現有 `live-slides/assets/01-feasibility-difficulty.png` 作為講師輸出範例，並在圖說標記為直播快照。

### 講師講稿

先不要把今天的主題講成「如何寫更長的 prompt」。我們真正要練的是：當 AI 給出答案時，我們手上是否有一份能被別人檢查、反駁、修改、重跑的產物。

同一個需求，如果問「這個功能能不能做」，模型很容易給你一句「可以，建議使用某某技術」。這個答案的問題不是一定錯，而是它沒有交代假設、未知數與驗證順序。你拿它去開會，別人沒有地方可以指出哪一個條件不成立。

因此第一個轉換是把 yes/no 問題改成報告任務。報告至少要回答五件事：技術上需要什麼、工作量如何分級、風險與未知數在哪裡、需要哪些前置資源，以及哪一個部分要最先做 spike。最後再要求 AI 寫出它最沒有把握的三件事。

這裡的「最沒把握」不是客套話。它是把模型的假設攤在桌上，讓我們可以決定要不要先驗證。若 AI 說不清楚 `BLOCKED` 是由前端計算、後端計算，還是由資料狀態推導出來，那就是規格尚未鎖定，而不是立刻開始寫 code 的理由。

Agentic Coding 在本課的用法，是一個工作流程名稱，不是某個產品品牌：理解需求、規劃、生成、驗證、修正。人的工作從逐行指揮，改成審查每一站交出的中間產物。GitHub 對 agent mode 的說明也把「研究 repository、建立計畫、修改程式、迭代修正」列為代理式工作模式的典型流程，這可作為延伸閱讀；本課不把任何單一工具當作必要條件。[GitHub Copilot agent mode](https://docs.github.com/en/copilot/get-started/features)

接著說明 Sycophancy。這個詞在研究中指模型可能迎合使用者的信念，而不一定優先追求正確。研究者在多種任務上觀察到這個傾向，因此課堂上的對策不是「相信模型會自我懷疑」，而是要求它交出欄位固定、可以被查驗的報告。[Towards Understanding Sycophancy in Language Models](https://arxiv.org/abs/2310.13548)

最後提醒學員：可行性報告不是承諾書。它的價值是讓錯誤更早暴露，讓我們知道先驗證什麼；它不能替代實際 spike、測試或專業審查。

### 旁帶案例：Linear 在工作上的位置

這裡可以順帶看一個工作上常見的工具：Linear。它不是「幫你想需求」的 AI，也不是測試或資安稽核工具；它比較像是把工作拆成可追蹤單位、集中狀態與協作上下文的工作台。Linear 官方將 Issue 描述為較細的具體工作，Project 則是共同指向功能或發布目標的一組 Issues；更高層還有 Initiative。[Intro to Linear](https://linear.app/learn/intro-to-linear)

對團隊來說，這種分層的幫助是把「我們最近在做什麼」變成可以共同查看的資料：Project 放目標與背景，Issue 放可執行工作，狀態與優先級讓大家知道進度與下一步，Milestone 則把工作分成階段。這不是工具自動帶來的好處，而是團隊先定義工作單位與更新規則後，工具才有辦法降低交接與同步成本。[Project overview](https://linear.app/docs/project-overview) 、[Project milestones](https://linear.app/docs/project-milestones)

本課的 `IssueFlow` 只取這個概念的最小閉環：單一 Project、2–3 個 Issue、狀態、優先級與一條前置關係。學員要練的不是把 Linear 畫面重做一次，而是理解工具背後的工作方法：需求要有範圍、Issue 要有完成條件、阻塞要可見、完成要有測試證據。工具旁帶的完整對照請見 [`materials/linear-tool-brief.md`](../materials/linear-tool-brief.md)。

### 講師示範提示詞

```text
針對以下 IssueFlow 專案需求，幫我做一份可行性評估報告。

【需求】
請在 IssueFlow 任務工作台加入「Issue 相依」功能：
- 後續任務若依賴尚未完成的前置任務，顯示 BLOCKED。
- BLOCKED 任務不可被標記完成，也不應被推薦為下一步。
- 前置任務完成後，後續任務解除 BLOCKED，進度與下一步建議更新。

【已知限制】
- 執行環境：Windows + PowerShell 7+
- 既有專案：Node.js 靜態前端，使用原生 JavaScript；產品只做單一專案與 2–3 個 Issue
- 課堂時間：90 分鐘，必須先完成一條最小可驗證流程
- 不得先假設要改用框架或資料庫

報告必須包含：
1. 技術可行性：需要哪些資料、規則與介面
2. 難度分級：拆成子項目，每項標示 S / M / L 並說明理由
3. 風險與未知數：列出可能造成返工的假設
4. 前置資源：需要的檔案、環境、權限與測試資料
5. 建議驗證順序：先做哪一個 spike，如何判定成功
6. 不支援或刻意不處理的範圍

最後列出：你對這份評估最沒把握的三件事，並為每件事提出一個可在 15 分鐘內執行的驗證。
先不要寫實作程式碼。
```

### 實作任務

#### `d1-u1-t1` 建立 IssueFlow 需求邊界

1. 閱讀 `materials/task-tracker-requirement.md`。
2. 參考 `materials/issueflow-project-assignment.md`，圈出 MVP「一定要有」、延伸功能與本課不做的句子。
3. 把一個未知數改寫成可驗證問題，例如：「BLOCKED 是資料欄位還是由 `dependsOnTaskId` 與前置狀態即時計算？」

#### `d1-u1-t2` 產出 IssueFlow 可行性報告

1. 把需求貼入提示詞。
2. 檢查輸出是否真的有五個指定欄位，而不是只有技術名詞清單。
3. 將 S/M/L 分級理由改成可觀察的工作量描述，例如「需要新增資料欄位、排序規則與兩條 UI 狀態」而不是「有點複雜」。
4. 儲存為 `artifacts/issueflow/ch01-feasibility-report.md`，保留提示詞與 AI 輸出摘要。

#### `d1-u1-t3` 找出最先驗證的 IssueFlow spike

1. 從三個未知數中選一個。
2. 寫出輸入、操作、預期結果與失敗時的下一步。
3. 限制 spike 不超過 15 分鐘，不能以「先把整個功能做完」作為驗證方案。

#### `d1-u1-t4` 同儕互審並完成章節交接

用下列問題交換檢查：

- 報告是否存在未說明的技術假設？
- 每個風險是否都有驗證順序？
- 「最沒把握的三件事」是否真的能讓需求方做決策？
- 如果只能保留一個 spike，這個選擇是否合理？

### 產物驗收

- [ ] 報告有五個固定欄位與刻意不處理範圍。
- [ ] 至少三個子項目有 S/M/L 分級與理由。
- [ ] 至少三個未知數各有驗證方式。
- [ ] 沒有在尚未確認規格前產生正式實作程式碼。
- [ ] 文件可交給下一位同學，不需要口頭補完關鍵假設。
- [ ] 報告明確標示 IssueFlow MVP 與不做範圍，並可作為 Ch02 選型輸入。

### 課中測驗 q1

**sourceUnit**：`d1-u1`  
**題目**：你想確認一個功能是否做得出來，哪一個提問最容易讓 AI 把風險與未知數攤開？

- A. 這個功能能不能做？
- B. 這個功能難不難？
- C. 請交出包含技術可行性、難度、風險、前置資源與驗證順序的可行性評估報告，並列出最沒把握的三件事。
- D. 這個功能大概要多久？

答案與解析放在講師教材 [`materials/quiz-answer-key.md`](../materials/quiz-answer-key.md)。

## u-d1-u2：把「幫我寫」轉成選型與開發計畫

**對應章節**：Ch02｜設計期（P15–P21）  
**建議時長**：3 小時（講授／示範 1 小時、實作 1.5 小時、回顧／測驗 0.5 小時）  
**對應任務**：`d1-u2-t1`、`d1-u2-t2`、`d1-u2-t3`、`d1-u2-t4`  
**對應素材**：`materials/issueflow-project-assignment.md`、`materials/linear-tool-brief.md`、`materials/technology-selection-template.md`、`materials/development-plan-template.md`、`materials/feasibility-report-template.md`

**圖片需求 (illustrations)**:

- `d1-u2-selection-plan.png` — `diagram`／選型分析 → 開發計畫 → 第一階段實作，並標出每個決策的輸入與輸出。
- `d1-u2-acceptance-criteria.png` — `diagram`／把「功能正常」改成輸入、動作、結果三段式驗收條件。
- `03-tech-selection.png`、`04-milestone-acceptance.png` — `screenshot`／使用現有 `live-slides/assets/03-tech-selection.png` 或 `04-milestone-acceptance.png` 作為講師示範快照。

### 講師講稿

設計期最常見的錯誤是直接把技術選擇寫進問題：「請用 React 寫一個報修系統」。這句話看似具體，其實把最重要的決策藏起來了：為什麼是 React？團隊會什麼？部署環境能不能使用？時程允許多少複雜度？

本章先把限制寫出來，再讓 AI 提出候選方案。沒有環境、團隊、部署與時程限制的選型，最多只是一份網路流行技術列表。選型表真正重要的不是「推薦 React」這四個字，而是它說明在什麼情況下不再推薦 React。

接著是開發計畫。請學員特別注意「先不要寫程式碼」這句話。計畫是便宜的中間產物；如果模組拆錯，改一行文件比改一整天程式碼便宜。好的計畫還要把風險與先行驗證放在里程碑旁邊，而不是最後補一段空泛的風險提醒。

驗收標準要能被第三人執行。不要寫「功能正常」，要寫「當任務 #2 依賴未完成的任務 #1 時，開啟頁面後 #2 顯示 `BLOCKED`，完成 #1 後重新整理或收到狀態更新，#2 顯示可執行且進度條增加」。這樣的句子下一天可以直接轉成測試。

這裡不要求 AI 選出唯一正解，而是要求決策可追溯。當限制變更時，重新跑選型提示詞，看看推薦是否應該變更。如果推薦永遠不變，通常表示限制根本沒有進入判斷。

Linear 的工具案例可以幫助我們理解這個拆分：Project 不只是名稱，還要有目標、背景、階段與進度；Issue 也不只是標題，而要能被分派、排序、移動狀態並驗收。官方文件也展示了以 View 依狀態、優先級、Project 或 Milestone 分組與篩選的做法。[Display options](https://linear.app/docs/display-options)

但課堂不會把「使用 Linear」寫成選型答案。若團隊沒有明確的 Issue 格式、狀態語意與驗收標準，換工具只會把混亂搬到另一個介面；因此學員必須先完成自己的 IssueFlow 計畫，再決定哪些工具概念值得借用。

### 講師示範提示詞一：選型分析

```text
我要為以下 IssueFlow 需求選擇實作方案：
【需求】單一專案中的 Issue 相依、BLOCKED 狀態、進度與下一步建議。

我的限制是：
- 開發環境：Windows + PowerShell 7+
- 既有程式：Node.js + 原生 JavaScript，沒有前端框架
- 團隊：一位熟悉 JavaScript 的工程師，課堂內只能完成一條高價值流程
- 部署：先在本機靜態伺服器驗證，不引入雲端服務
- 資料：先用固定 JSON fixture，不建立正式資料庫

請列出 2–4 個可行方案。每個方案要包含：
1. 核心作法與需要修改的模組
2. 優點、缺點與適用情境
3. 對上述限制的契合度
4. 會造成返工的風險

最後給出推薦，並列出至少三個「若條件成立就應改推薦」的邊界。
不要寫實作程式碼。
```

### 講師示範提示詞二：開發計畫

```text
採用【選定方案】。請寫一份 IssueFlow 第一階段開發計畫，範圍只到：
「Issue 2 的前置 Issue 1 未完成時顯示 BLOCKED，完成 Issue 1 後解除阻塞」。

請包含：
1. 模組拆解與每個模組的職責
2. 資料與狀態流向
3. 里程碑與每階段產出
4. 每階段可執行的驗收標準：包含輸入、動作、預期結果
5. 風險、未知數與先行驗證
6. 不在第一階段處理的功能

先不要寫程式碼。先只交付計畫，等待我確認後再進入第一個里程碑。
```

### 實作任務

#### `d1-u2-t1` 寫出 IssueFlow 限制

把 `d1-u1` 的報告轉成四類限制：環境、團隊、部署、時程／人力。每一項要能回答「如果這個限制改變，哪一個決策可能需要重做？」

#### `d1-u2-t2` 比較 IssueFlow 候選方案

至少比較三個方案，例如：

1. 原生 JavaScript + fixture 狀態推導。
2. 前端框架 + 狀態管理。
3. 後端計算狀態、前端只呈現結果。

不要因為課程使用原生 JavaScript 就把其他方案寫成稻草人；每個方案都要公平列出適用情境。

#### `d1-u2-t3` 產出 IssueFlow 第一階段計畫

把 IssueFlow 拆成「資料形狀、狀態規則、畫面呈現、互動更新、測試資料」五個面向，為每個面向寫一條驗收標準。至少一條標準要能直接變成明天的測試。

#### `d1-u2-t4` 計畫審查並交接 Ch03

同儕只准用文件審查，不直接修改對方程式。找出：漏掉的依賴、無法執行的驗收、沒有先驗證的風險，以及超出 90 分鐘的工作。

### 產物驗收

- [ ] 選型比較至少包含三個方案與限制契合度。
- [ ] 推薦方案有「改推薦條件」。
- [ ] 計畫有模組、資料流、里程碑、驗收、風險與不做範圍。
- [ ] 驗收標準能被下一章直接改寫成測試名稱或斷言。
- [ ] 沒有先寫大段實作來掩蓋設計缺口。
- [ ] 已交付 `artifacts/issueflow/ch02-technology-selection.md` 與 `ch02-development-plan.md`。
- [ ] 已指定 Ch03 必須先驗證的垂直流程：開頁 → BLOCKED → 完成前置 → 解除阻塞 → 進度更新。

### 課中測驗 q2

**sourceUnit**：`d1-u2`  
**題目**：在請 AI 實作前加上「先不要寫程式碼，先給我開發計畫」的主要價值是什麼？

- A. 節省 token
- B. 讓錯誤先出現在便宜、容易修改的計畫層
- C. 讓 AI 寫程式速度變快
- D. 自動消除所有技術風險

答案與解析放在講師教材 [`materials/quiz-answer-key.md`](../materials/quiz-answer-key.md)。
