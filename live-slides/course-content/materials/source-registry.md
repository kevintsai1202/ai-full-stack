# 網路資料、術語與測試來源登錄

> 存檔日期：2026-08-09（Asia/Taipei）。外部頁面可能更新；正式授課前重新確認版本與頁面內容。

| ID | 類型 | 官方／原始來源 | 本課使用位置 | 使用方式與限制 |
|---|---|---|---|---|
| REF-01 | Agent mode | [GitHub Copilot features](https://docs.github.com/en/copilot/get-started/features) | Day 1 Ch01 Agentic Coding | 只用來說明代理式工作流程，不把 Copilot 當成本課必要工具 |
| REF-02 | 研究論文 | [Towards Understanding Sycophancy in Language Models](https://arxiv.org/abs/2310.13548) | Day 1 Ch01 Sycophancy | 使用論文對 sycophancy 的定義與實驗觀察，不宣稱所有模型每次都會迎合 |
| REF-03 | TDD | [Martin Fowler: Test Driven Development](https://www.martinfowler.com/bliki/TestDrivenDevelopment.html) | Day 2 Ch03 | 使用 Red–Green–Refactor 與先列測試案例的說明 |
| REF-04 | Playwright | [Writing tests](https://playwright.dev/docs/writing-tests) | Day 2 Ch03 | 使用 action + assertion、web-first assertion 與 isolation 概念 |
| REF-05 | Playwright | [Running and debugging tests](https://playwright.dev/docs/running-tests) | Day 2 Ch03 | 使用 `npx playwright test`、`--headed`、`--ui` 與 report 指令 |
| REF-06 | Playwright | [Best practices](https://playwright.dev/docs/best-practices) | Day 2 Ch03 | 使用 user-visible behavior、test isolation、trace viewer 建議 |
| REF-07 | OWASP | [OWASP Top 10:2025](https://owasp.org/Top10/) | Day 2 Ch04 | 使用完整十類名稱作基準；不是滲透測試替代品 |
| REF-08 | CWE | [CWE Top 25 home](https://cwe.mitre.org/top25/) | Day 2 Ch04 | 使用 2025 清單背景與用途 |
| REF-09 | CWE | [CWE Top 25:2025 archive](https://cwe.mitre.org/top25/archive/2025/2025_cwe_top25.html) | Day 2 Ch04 | 使用完整 rank、ID、名稱、score 與 CVEs in KEV 表格 |
| REF-10 | npm | [npm audit CLI](https://docs.npmjs.com/cli/v11/commands/npm-audit/) | Day 2 Ch04 | 使用 lockfile、exit code、audit-level、manual remediation 說明 |
| REF-11 | CVSS | [FIRST CVSS v4.0](https://www.first.org/cvss/v4.0/) | Day 2 Ch04 | 使用版本入口與官方 specification/calculator 連結 |
| REF-12 | CVSS | [CVSS v4.0 specification](https://www.first.org/cvss/specification-document) | Day 2 Ch04 | 使用 Base、Threat、Environmental、Supplemental 群組說明 |
| REF-13 | 安全測試 | [NIST SP 800-115](https://csrc.nist.gov/pubs/sp/800/115/final) | Day 2 Ch04 | 用於說明安全測試包含規劃、執行、分析與緩解，不宣稱本課完成完整測試 |
| REF-14 | SVG | [W3C SVG 2](https://www.w3.org/TR/SVG2/) | 流程圖產線 | 手繪 SVG 是可維護來源，PNG 是課堂與簡報相容輸出 |
| REF-15 | 專案管理工具 | [Intro to Linear](https://linear.app/learn/intro-to-linear) | Day 1 工具旁帶、IssueFlow 作業 | 使用官方對 Issue、Project、Initiative 的分層說明；不要求註冊或使用 Linear |
| REF-16 | 專案管理工具 | [Project overview](https://linear.app/docs/project-overview) 、[Project milestones](https://linear.app/docs/project-milestones) | Day 1 Ch02、IssueFlow 作業 | 使用 Project 摘要、文件、外部連結、Milestone 與進度概念；功能可能依方案與設定而異 |
| REF-17 | 專案管理工具 | [Display options](https://linear.app/docs/display-options) 、[Custom Views](https://linear.app/docs/custom-views) | Day 1 Ch02 延伸功能 | 使用清單／Board、分組、篩選與儲存 View 的概念；不列為兩日 MVP 必做 |
| REF-18 | 開發協作整合 | [GitHub integration](https://linear.app/docs/github-integration) | Day 2 Ch03、工具旁帶 | 使用 Issue 與 branch、commit、pull request 可追溯的概念；課堂不要求設定第三方整合 |

## 版本與數據原則

- CWE 的 `score`、`CVEs in KEV` 是官方 2025 清單欄位，不是本課 lab 的漏洞數量。
- OWASP／CWE 名稱保留官方英文，教材解說可補繁中，但不自行改寫代號。
- npm audit 報告一定附 lockfile、執行日期、Node／npm 版本與命令；不將歷史截圖的依賴數量當成現況。
- CVSS 報告一定附版本與 vector；沒有 vector 時標成資料不足，不自行補分。
- Playwright 測試結果一定附測試命令、瀏覽器 project、pass/fail、trace／screenshot 路徑。
