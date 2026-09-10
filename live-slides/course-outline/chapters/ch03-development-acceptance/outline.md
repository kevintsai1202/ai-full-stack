# Ch03：將程式轉成 TDD、E2E 與 SOP

## 章節摘要

把「幫我測試程式有沒有問題」改成三層驗收資產：開發時以 TDD 先寫測試並確認紅燈，完工後以 Playwright 建立可重跑的 E2E 腳本，再把 IssueFlow 執行截圖整理成人能閱讀的 SOP。測試不是事後裝飾，而是規格與證據。

**Unit ID**：`d2-u1`

## 來源投影片

- P22–P23：開發驗收期與三層驗收架構
- P24–P26：TDD、紅燈、綠燈與測試即規格
- P27–P32：Playwright、備援影片、截圖軌跡與 SOP
- P33：Q3

## 學習目標

- 能夠為正常路徑、邊界值與錯誤分支先寫測試。
- 能夠確認紅燈是因為功能尚未實作，而不是測試本身寫錯。
- 能夠把一條使用者流程寫成可重跑的 Playwright 腳本。
- 能夠由測試截圖產出包含預期結果與常見錯誤的 SOP。

## 核心主題

1. TDD 紅綠重構：先測試、紅燈、最小實作、綠燈、重構。
2. 防止過擬合測試：寫完程式再補的測試容易只證明實作自己。
3. 腳本是資產：避免只用一次性的互動指令。
4. 一次執行兩份產物：機器用回歸測試，人用操作 SOP。

## 三小時配置

| 時段 | 內容 | 類型 |
|---|---|---|
| 00:00–00:20 | TDD 與測試即規格 | 講授 |
| 00:20–00:40 | 紅燈／綠燈示範與失敗原因判讀 | 示範 |
| 00:40–01:00 | E2E、截圖與 SOP 產出流程 | 講授／示範 |
| 01:00–02:45 | 完成測試、Playwright 腳本與截圖 SOP | 實作 |
| 02:45–02:55 | 檢查執行證據與同步性 | 回顧 |
| 02:55–03:00 | Q3 | 測驗 |

## IssueFlow 專案作業任務

- `d2-u1-t1`：針對 Issue 依賴規則先建立紅燈測試，確認阻塞 Issue 不可完成、不可被推薦。
- `d2-u1-t2`：完成最小規則，使前置完成後阻塞解除、進度前進、下一步建議更新。
- `d2-u1-t3`：用 Playwright 覆蓋 IssueFlow 垂直流程：開啟專案 → 檢查 BLOCKED → 完成前置 → 驗證解除 → 檢查進度。
- `d2-u1-t4`：保存關鍵步驟截圖，產出含圖片、操作說明、預期結果與常見錯誤的 Markdown SOP。

## 驗收標準

- 至少有一個可確認的紅燈與一個綠燈執行紀錄。
- 測試涵蓋正常路徑、至少一個邊界或錯誤分支。
- `npx playwright test` 可重跑，失敗時能定位步驟。
- SOP 的步驟與最新 E2E 執行畫面一致。
- 交付 `artifacts/issueflow/ch03-validation/`，包含測試、紅綠燈輸出、截圖與 SOP，作為 Ch04 稽核證據。

## 需要的素材

- `live-slides/assets/05-tdd-red.png`
- `live-slides/assets/06-tdd-green.png`
- `live-slides/assets/07-e2e-screenshots.png`
- `live-slides/assets/08-generated-sop.png`
- `live-slides/assets/08b-generated-sop-step06.png`
- `live-slides/assets/live-demo.webm`（E2E 失敗時的備援）
