// 管理後台 admin.html 端到端驗證腳本（不實際發送）
// 用法：$env:ADMIN_API_KEY="<金鑰>"; node survey-backend/scripts/verify-admin.mjs
// 需求：npx playwright（首次會自動下載 chromium）
import { chromium } from 'playwright';
import { mkdir } from 'node:fs/promises';

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
  const unauthorized = await page.request.get(`${BASE}/api/admin/survey`);
  if (unauthorized.status() !== 401) fail(`未帶金鑰的問卷 API 應回 401，實際為 ${unauthorized.status()}`);
  console.log('OK 金鑰閘門出現');

  // 2. 輸入金鑰進入 → 主畫面顯示
  await page.fill('#gate-key', KEY);
  await page.click('#gate-btn');
  await page.waitForSelector('#app', { state: 'visible', timeout: 15000 });
  console.log('OK 金鑰正確，進入主畫面');

  // 3. 問卷分析預設顯示，KPI 與原始資料表皆需載入
  await page.waitForFunction(() => /^\d+$/.test(document.querySelector('#kpi-total')?.textContent || ''), null, { timeout: 15000 });
  const total = Number(await page.locator('#kpi-total').textContent());
  const rawRows = await page.locator('#raw-table tbody tr').count();
  if (total > 0 && rawRows < 1) fail('有問卷資料時，完整原始資料表不得為空');
  console.log(`OK 問卷分析載入：${total} 筆`);

  // 4. CSV 匯出 API 必須包含完整 UTM 欄位
  const csv = await page.request.get(`${BASE}/api/admin/survey?format=csv`, { headers: { 'X-Admin-Key': KEY } });
  if (!csv.ok()) fail(`CSV 匯出失敗：HTTP ${csv.status()}`);
  if (!(await csv.text()).includes(',answers,utm,consent,')) fail('CSV 缺少 answers 或 utm 欄位');
  console.log('OK CSV 完整欄位驗證通過');

  // 5. 切換電子報頁籤後，收件數仍可正常載入
  await page.click('#tab-campaign');
  await page.waitForFunction(() => /\d/.test(document.querySelector('#rcount')?.textContent || ''), null, { timeout: 15000 });
  console.log('OK 收件數載入：', await page.locator('#rcount').textContent());

  // 6. 撰寫 + 預覽 → iframe 應有渲染內容
  await page.fill('#subject', '驗證用主旨');
  await page.fill('#markdown', '# Hello\n\nverify body');
  await page.click('#preview-btn');
  await page.waitForFunction(() => {
    const f = document.querySelector('#preview');
    return f && f.srcdoc && f.srcdoc.includes('Hello');
  }, null, { timeout: 15000 });
  console.log('OK 預覽渲染成功');

  // 7. 回到分析頁並截圖留存
  await page.click('#tab-analytics');
  await mkdir('output/playwright', { recursive: true });
  await page.screenshot({ path: 'output/playwright/survey-admin-verify.png', fullPage: true });
  console.log('OK 截圖 output/playwright/survey-admin-verify.png（含線上個資，不得提交）');

  console.log('\n全部通過 ✅（未實際發送）');
} catch (e) {
  fail(e.message);
} finally {
  await browser.close();
}
