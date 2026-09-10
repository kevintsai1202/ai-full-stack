# live-slides 兩日課程完整內容

本目錄是 `live-slides` 46 張投影片的兩日課程教材包，規劃為 4 個半天單元、共 12 小時。課程以 `IssueFlow`（類 Linear 的教學版任務工作台）作為貫穿四章的專案作業，補上講師口語稿、學員任務、可交付產物、驗收條件、測驗、術語、來源與流程圖素材。

## 課程入口

- [第一天：想法期與設計期](day1/content.md)
- [第二天：開發驗收與上線前稽核](day2/content.md)
- [課程章節目錄與時間配置](../course-outline/overview.md)
- [共同案例與分組方式](../course-outline/shared-scenario.md)
- [教材與實作環境說明](supporting-docs.md)
- [教材索引](materials/README.md)
- [IssueFlow 專案作業說明](materials/issueflow-project-assignment.md)
- [Linear 工具旁帶案例](materials/linear-tool-brief.md)
- [完整來源索引](materials/source-registry.md)
- [術語表](materials/glossary.md)

## 時間配置

| 半天 | 單元 | 投影片 | 講授／示範 | 實作 | 討論／驗收 |
|---|---|---:|---:|---:|---:|
| Day 1 AM | d1-u1 可行性評估 | P1–P14 | 1 小時 | 1 小時 30 分 | 30 分 |
| Day 1 PM | d1-u2 選型與開發計畫 | P15–P21 | 1 小時 | 1 小時 30 分 | 30 分 |
| Day 2 AM | d2-u1 TDD、E2E 與 SOP | P22–P33 | 1 小時 | 1 小時 45 分 | 15 分 |
| Day 2 PM | d2-u2 安全基準與上線前稽核 | P34–P46 | 1 小時 | 1 小時 15 分 | 45 分 |
| **合計** | 4 個半天 | **P1–P46** | **4 小時** | **6 小時** | **2 小時** |

## 流程圖產製

流程圖的 SVG 原始檔與 PNG 成品放在 [`assets/diagrams/`](assets/diagrams/)。每張圖都保留 SVG 來源，並由 `scripts/render-diagrams.mjs` 以 Playwright 轉成 2 倍解析度 PNG；重新產圖時執行：

```powershell
node scripts/generate-diagrams.mjs
node scripts/render-diagrams.mjs
node scripts/validate-course-content.mjs
```

流程圖規格與檔案對照請見 [`assets/diagrams/README.md`](assets/diagrams/README.md)。

## 完整性原則

- 網路數據、專門術語與測試依據都要在教材內附來源連結，並集中登錄於 `materials/source-registry.md`。
- OWASP、CWE、npm audit、Playwright、CVSS、NIST 與 TDD 等外部資料只作為可查驗基準；學員仍需提交自己的程式碼、測試結果與修補證據。
- `materials/lab/` 是可執行的小型任務看板練習；綠燈測試驗證完成規則，紅燈測試示範先寫測試再補實作。
