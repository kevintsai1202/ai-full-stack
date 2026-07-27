// 管理後台 admin.html 端到端驗證腳本（不實際發送）
// 用法：$env:ADMIN_API_KEY="<金鑰>"; node survey-backend/scripts/verify-admin.mjs
// 需求：playwright（本機為全域安裝：npm i -g playwright）
import { mkdir } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

const BASE = process.env.ADMIN_BASE || 'https://springai-survey.zeabur.app';
const KEY = process.env.ADMIN_API_KEY;
if (!KEY) { console.error('請先設定環境變數 ADMIN_API_KEY'); process.exit(1); }

/**
 * 動態載入 playwright：先試專案內解析，再逐一嘗試常見的全域安裝目錄。
 *
 * 原本的靜態 `import { chromium } from 'playwright'` 在 playwright 不在專案
 * node_modules 時直接 ERR_MODULE_NOT_FOUND——這支腳本因此**長期跑不起來**而沒人發現
 * （它不在 mvn test 裡，紅了不會有人看到）。載不到一律 exit 1，不印警告混過去。
 * 作法與 verify-publish-endpoint.mjs / verify-admin-cost-prefill.mjs 一致。
 */
async function loadPlaywright() {
  try {
    return await import('playwright');
  } catch { /* 專案內沒有，改找全域 */ }
  const roots = [
    process.env.APPDATA && join(process.env.APPDATA, 'npm', 'node_modules'),
    process.env.ProgramFiles && join(process.env.ProgramFiles, 'nodejs', 'node_modules'),
    '/usr/local/lib/node_modules',
    '/usr/lib/node_modules',
  ].filter(Boolean);
  for (const root of roots) {
    const entry = join(root, 'playwright', 'index.js');
    if (existsSync(entry)) return await import(pathToFileURL(entry).href);
  }
  throw new Error('專案內與常見全域安裝目錄都找不到 playwright；請安裝：npm i -g playwright');
}

let chromium;
try {
  // 全域安裝的 playwright 以 CJS 形式被動態 import 時，具名匯出掛在 default 底下
  const mod = await loadPlaywright();
  const pw = mod.default ?? mod;
  chromium = pw.chromium;
  if (!chromium) throw new Error('載入的 playwright 模組沒有 chromium 匯出');
} catch (e) {
  console.error('FAIL:', e.message);
  process.exit(1);
}

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

  // 6. 分隔線與付費牆必須插入真正換行，不可把 "\n" 字面值寫進內文
  await page.fill('#markdown', '');
  await page.getByRole('button', { name: '分隔線', exact: true }).click();
  await page.getByRole('button', { name: '付費牆', exact: true }).click();
  const blockMarkdown = await page.inputValue('#markdown');
  if (blockMarkdown !== '---\n\n<!--paywall-->\n\n') {
    fail(`Markdown 區塊插入格式錯誤：${JSON.stringify(blockMarkdown)}`);
  }
  console.log('OK 分隔線與付費牆插入真正換行');

  // 7. 撰寫 + 付費牆預覽 → iframe 應顯示免費區、分界與付費內容預覽
  await page.fill('#subject', '驗證用主旨');
  await page.fill('#markdown', '# Hello\n\n免費內容\n\n<!--paywall-->\n\n付費內容');
  await page.click('#preview-btn');
  await page.waitForFunction(() => {
    const f = document.querySelector('#preview');
    return f && f.srcdoc
      && f.srcdoc.includes('Hello')
      && f.srcdoc.includes('付費牆分界')
      && f.srcdoc.includes('付費內容預覽')
      && !f.srcdoc.includes('<!--paywall-->');
  }, null, { timeout: 15000 });
  console.log('OK 付費牆預覽渲染成功');

  // 8. 回到分析頁並截圖留存
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
