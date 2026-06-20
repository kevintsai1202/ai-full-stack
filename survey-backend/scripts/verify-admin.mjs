// 寄信後台 admin.html 端到端驗證腳本（不實際發送）
// 用法：$env:ADMIN_API_KEY="<金鑰>"; node survey-backend/scripts/verify-admin.mjs
// 需求：npx playwright（首次會自動下載 chromium）
import { chromium } from 'playwright';

const BASE = process.env.ADMIN_BASE || 'https://springai-survey.zeabur.app';
const KEY = process.env.ADMIN_API_KEY;
if (!KEY) { console.error('請先設定環境變數 ADMIN_API_KEY'); process.exit(1); }

const browser = await chromium.launch();
const page = await browser.newPage();
const fail = (m) => { console.error('FAIL:', m); process.exitCode = 1; };

try {
  // 1. 開頁 → 應出現金鑰閘門
  await page.goto(`${BASE}/admin.html`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#gate', { state: 'visible' });
  if (await page.locator('#app').isVisible()) fail('未驗證前主畫面不應顯示');
  console.log('OK 金鑰閘門出現');

  // 2. 輸入金鑰進入 → 主畫面顯示
  await page.fill('#gate-key', KEY);
  await page.click('#gate-btn');
  await page.waitForSelector('#app', { state: 'visible', timeout: 15000 });
  console.log('OK 金鑰正確，進入主畫面');

  // 3. 收件數載入（數字或 0）
  await page.waitForFunction(() => /\d/.test(document.querySelector('#rcount')?.textContent || ''), null, { timeout: 15000 });
  console.log('OK 收件數載入：', await page.locator('#rcount').textContent());

  // 4. 撰寫 + 預覽 → iframe 應有渲染內容
  await page.fill('#subject', '驗證用主旨');
  await page.fill('#markdown', '# Hello\n\nverify body');
  await page.click('#preview-btn');
  await page.waitForFunction(() => {
    const f = document.querySelector('#preview');
    return f && f.srcdoc && f.srcdoc.includes('Hello');
  }, null, { timeout: 15000 });
  console.log('OK 預覽渲染成功');

  // 5. 截圖留存
  await page.screenshot({ path: 'survey-backend/scripts/admin-verify.png', fullPage: true });
  console.log('OK 截圖 survey-backend/scripts/admin-verify.png');

  console.log('\n全部通過 ✅（未實際發送）');
} catch (e) {
  fail(e.message);
} finally {
  await browser.close();
}
