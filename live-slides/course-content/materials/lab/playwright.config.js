import { defineConfig } from '@playwright/test';

/**
 * 課堂用 Playwright 設定：以 Python 靜態伺服器提供 lab 頁面，避免 file:// 行為差異。
 */
export default defineConfig({
  testDir: '.',
  testMatch: 'task-tracker.spec.js',
  timeout: 15000,
  use: {
    baseURL: 'http://127.0.0.1:4173',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  reporter: [['list'], ['html', { outputFolder: 'artifacts/playwright-report', open: 'never' }]],
  webServer: {
    command: 'python -m http.server 4173',
    url: 'http://127.0.0.1:4173/task-tracker.html',
    reuseExistingServer: true,
    timeout: 15000,
  },
});
