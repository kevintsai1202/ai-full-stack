# 可重跑 Lab

## 安裝

在本目錄執行：

```powershell
npm install -D @playwright/test
npx playwright install chromium
```

## TDD 紅綠循環

先看故意失敗的測試：

```powershell
npm run test:red
```

再執行目前已完成的規則測試：

```powershell
npm run test:unit
```

課堂講解時，學員應把 `task-rules.test.js` 的測試案例當作規格，先在自己的分支看到紅燈，再逐步完成 `task-rules.js`。

## Playwright E2E

```powershell
npm run test:e2e
npx playwright show-report artifacts/playwright-report
```

測試會自動啟動 `python -m http.server 4173`，並把截圖與 report 放到 `artifacts/`。如果 4173 已被其他服務使用，先停止該服務或修改 `playwright.config.js` 的 port 與 baseURL。

