# superpowers｜課程最後補充：用 superpowers 規格先行

前面 8 個單元帶你手動走過開發一套系統的完整流程——這是為了讓你建立 Domain Knowhow，能判斷 AI 產出的對錯。實際開發時，我們改用 Claude Code 的 superpowers 技能組：先把需求與規格想清楚、寫成計畫，再讓 AI 照計畫實作與驗證，而不是想到哪寫到哪。以下說明各技能的用途。

## 規劃：先想清楚再動手

### brainstorming
- **zh**：需求發想
- **purpose**：在寫任何程式前，先釐清「要做什麼、為誰做、有哪些取捨與邊界」，把模糊的想法收斂成明確需求。

### writing-plans
- **zh**：撰寫實作計畫
- **purpose**：把需求轉成一份可執行的多步驟計畫：拆解任務、定義每一步的產出與驗收標準，作為開發藍圖。

## 執行：照計畫穩定產出

### executing-plans
- **zh**：執行計畫
- **purpose**：依計畫逐步實作，每完成一個段落就停下來檢查，確保不偏離藍圖。

### subagent-driven-development
- **zh**：子代理開發
- **purpose**：把計畫中彼此獨立的任務分派給子代理並行完成，加速開發。

### test-driven-development
- **zh**：測試驅動開發
- **purpose**：先寫測試再寫實作，用測試守住正確性，避免「看起來會動但其實錯」。

### using-git-worktrees
- **zh**：Git Worktree 隔離
- **purpose**：為新功能建立獨立的工作目錄，與主工作區隔離，避免互相干擾。

## 品質：除錯與審查

### systematic-debugging
- **zh**：系統化除錯
- **purpose**：遇到 bug 時先建立假設、逐一驗證找出根因，而不是亂試一通。

### requesting-code-review
- **zh**：請求程式碼審查
- **purpose**：完成一個段落後，主動請另一個代理以審查者角度檢視程式碼品質與風險。

### receiving-code-review
- **zh**：回應審查意見
- **purpose**：有條理地處理審查回饋，逐項修正或說明。

### verification-before-completion
- **zh**：完工前驗證
- **purpose**：宣告「做完」之前，實際執行並觀察行為，確認真的做對、做完。

## 收尾與進階

### finishing-a-development-branch
- **zh**：收尾開發分支
- **purpose**：功能完成、測試通過後，協助決定如何整合：合併、開 PR 或清理分支。

### dispatching-parallel-agents
- **zh**：派發並行代理
- **purpose**：面對多個彼此獨立、無先後相依的任務時，同時派發多個代理處理。

### using-superpowers
- **zh**：技能入口
- **purpose**：所有任務的起點：判斷該用哪個技能，並確保照技能流程確實執行。