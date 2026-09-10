# 專門術語表

| 術語 | 本課定義 | 不要誤解成 | 來源 |
|---|---|---|---|
| Agentic Coding | 本課對「理解需求、規劃、生成、驗證、修正，並由人審查中間產物」的工作流程稱呼 | AI 可以不受監督地交付正式系統 | [GitHub Copilot agent mode](https://docs.github.com/en/copilot/get-started/features) |
| Sycophancy | 模型迎合使用者信念或暗示，可能犧牲真實性或正確性 | 每一次模型回答都一定是討好 | [Sharma et al., 2023](https://arxiv.org/abs/2310.13548) |
| Constraint-first | 本課教學標籤：先提供環境、技能、部署、時程等限制，再要求選型 | 一個已被正式標準化的演算法 | 本課教學定義；提示設計延伸可參考 [OpenAI prompting guide](https://platform.openai.com/docs/guides/prompt-engineering) |
| TDD | 先用測試描述行為，再以最小實作讓測試通過，最後重構 | 測試通過就證明沒有任何 bug | [Martin Fowler: TDD](https://www.martinfowler.com/bliki/TestDrivenDevelopment.html) |
| Red–Green–Refactor | TDD 的循環：先紅、再綠、再整理程式 | 只要看到綠燈就可以停止所有測試 | [Martin Fowler: TDD](https://www.martinfowler.com/bliki/TestDrivenDevelopment.html) |
| E2E test | 從使用者可見入口走過一條完整流程並驗證結果 | 只測單一函式的 unit test | [Playwright Writing Tests](https://playwright.dev/docs/writing-tests) |
| Locator | Playwright 用來定位頁面元素的方式，應優先使用 role、label、text 或穩定 test id | 任意 CSS selector 都同樣穩定 | [Playwright Test generator](https://playwright.dev/docs/codegen) |
| Trace | Playwright 執行期間留下的時間軸、DOM snapshot、網路與操作證據 | 一份通用的滲透測試報告 | [Playwright Best Practices](https://playwright.dev/docs/best-practices) |
| SOP | 可由人依序執行的標準操作程序，需包含前提、步驟、預期結果與回復方式 | 只把測試 log 貼到 Markdown | 本課教學定義 |
| OWASP Top 10 | OWASP 的 Web 應用程式重大風險 awareness 文件；本課使用 2025 版 | 完整安全測試清單或滲透測試 | [OWASP Top 10:2025](https://owasp.org/Top10/) |
| CWE | MITRE 維護的軟體弱點分類；本課使用 CWE Top 25:2025 作基準索引 | 直接等於某一個 CVE | [MITRE CWE Top 25](https://cwe.mitre.org/top25/) |
| CVE | 公開揭露的特定漏洞識別資料 | 所有 CWE 弱點都一定有同一個 CVE | [CVE Program](https://www.cve.org/) |
| CVSS | FIRST 維護的漏洞嚴重度評分框架；報告需標示 v4.0 與 vector | 單一分數就能決定所有修補優先序 | [FIRST CVSS v4.0](https://www.first.org/cvss/v4.0/) |
| `npm audit` | 依 npm registry 與 lockfile 檢查已知依賴漏洞的 CLI | 完整原始碼安全審查 | [npm audit](https://docs.npmjs.com/cli/v11/commands/npm-audit/) |
| Baseline audit | 依固定外部基準逐項列出命中、未命中與不適用原因 | 已完成滲透測試 | 本課教學定義；安全測試背景見 [NIST SP 800-115](https://csrc.nist.gov/pubs/sp/800/115/final) |
| Linear | 一種把 Project、Issue、Milestone、Views 與協作上下文放在同一工作流程中的專案管理工具；本課只作旁帶案例 | 使用工具就自動完成需求分析、測試與稽核 | [Intro to Linear](https://linear.app/learn/intro-to-linear) |
| Issue | 可在較短時間內完成、可被追蹤與交接的具體工作單位；本課的 lab 可用 Task 欄位實作 | 一個沒有完成條件的大型需求 | [Intro to Linear](https://linear.app/learn/intro-to-linear) |
| Project | 一組共同指向功能或發布目標的 Issues，通常包含摘要、文件、Milestones 與進度 | 只有待辦清單的資料夾 | [Project overview](https://linear.app/docs/project-overview) |
| Milestone | 專案中的階段性節點，用來組織 Issues 與觀察階段進度 | 另一套獨立的任務清單 | [Project milestones](https://linear.app/docs/project-milestones) |
