// 彈性表單分析「來源分佈」端到端驗證腳本
//
// 驗證項目：
//   1. 「來源」下拉改由 /analytics/forms/{key}/sources 供給：選項含中文標籤與筆數，
//      且包含 newsletter_survey 這類不在匯入註冊表裡的程式自動來源
//   2. 未知來源退回顯示原始 key（不隱藏、不丟棄）
//   3. 來源分佈表格列出每個來源的紀錄數與有答案數
//   4. 摘要並列顯示總提交與有填答內容筆數，並標註完成率只計必填欄位
//   5. 套用來源篩選後，摘要的「有填答內容」對齊該來源，而不是全部來源的加總
//
// 用法（後端須已在本機啟動；金鑰為 dev-admin-key，需以
// APP_ALLOW_INSECURE_DEV_SECRETS=true 啟動）：
//   node survey-backend/scripts/verify-form-source-breakdown.mjs
//   ADMIN_BASE=http://127.0.0.1:8081 node survey-backend/scripts/verify-form-source-breakdown.mjs
//
// 絕對不要把 ADMIN_BASE 指向正式站——本腳本用 page.route() 攔截 admin 分析 API
// 餵假資料，預設值刻意設為本機位址（比照 verify-growth-funnel.mjs 的慣例）。
//
// 可重跑：只讀登入、攔截假資料渲染，不寫入任何需要清理的後端狀態。

import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

const BASE = process.env.ADMIN_BASE || 'http://127.0.0.1:8080';
const ADMIN_KEY = process.env.ADMIN_API_KEY || 'dev-admin-key';
const FORM_KEY = 'verify-source-form';

/** 動態載入 playwright：先試專案內解析，再逐一嘗試常見全域安裝目錄。 */
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
  const mod = await loadPlaywright();
  const pw = mod.default ?? mod;
  chromium = pw.chromium;
  if (!chromium) throw new Error('載入的 playwright 模組沒有 chromium 匯出');
} catch (e) {
  console.error('FAIL:', e.message);
  process.exit(1);
}

let failed = 0;
const fail = (msg) => { console.error('FAIL:', msg); failed++; };
const ok = (cond, label) => { if (!cond) fail(label); else console.log(`OK   ${label}`); };

/** 只有一個版本的最小表單定義，讓 #dynamic-form／#dynamic-version 能選定。 */
const FORM_DEFINITIONS = [{
  id: 1, key: FORM_KEY, version: 1, title: '來源分佈驗證表單', status: 'PUBLISHED',
  publicAnalyticsEnabled: false, emailVoteFieldKey: null,
  homepageVisible: false, homepageOrder: null, fields: [],
}];

/**
 * 來源分佈假資料：刻意重現生產環境的形狀——大量空殼紀錄（exam／dify）、
 * 只帶推薦碼的 newsletter，以及唯一有完整答案的 newsletter_survey，
 * 外加一個不在任何標籤表裡的未知來源。
 */
const SOURCE_BREAKDOWN = {
  sources: [
    { key: 'exam', label: '線上測驗', total: 254, answered: 0 },
    { key: 'dify', label: 'Dify 學員', total: 190, answered: 0 },
    { key: 'newsletter', label: '電子報通道', total: 107, answered: 0 },
    { key: 'survey_form', label: '問卷填寫', total: 60, answered: 60 },
    { key: 'mystery_pipeline', label: 'mystery_pipeline', total: 3, answered: 0 },
    { key: 'newsletter_survey', label: '讀者接續填答', total: 1, answered: 1 },
  ],
  totals: { total: 615, answered: 61 },
};

/** 用金鑰模式登入到主畫面。 */
async function loginWithKey(page) {
  await page.goto(`${BASE}/admin.html`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#gate', { state: 'visible' });
  await page.click('#gate-use-key');
  await page.fill('#gate-key', ADMIN_KEY);
  await page.click('#gate-btn');
  await page.waitForSelector('#app:not([hidden])', { timeout: 15000 });
}

/** 攔截表單清單與分析端點，其餘請求照常送往後端。 */
async function mockAnalytics(page) {
  await page.route('**/api/admin/forms', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json',
      body: JSON.stringify(FORM_DEFINITIONS) }));

  await page.route('**/api/admin/analytics/forms/**', (route) => {
    const url = new URL(route.request().url());
    const json = (body) => route.fulfill({ status: 200,
      contentType: 'application/json', body: JSON.stringify(body) });

    if (url.pathname.endsWith('/sources')) {
      // 這支端點不該收到 source 參數——它的職責就是列出全部來源
      if (url.searchParams.has('source')) fail('/sources 端點不應帶 source 參數');
      return json(SOURCE_BREAKDOWN);
    }
    if (url.pathname.endsWith('/votes')) {
      return json({ totalVotes: 0, totalNamed: 0, options: [], byCampaign: [] });
    }
    if (url.pathname.endsWith('/records')) return json([]);

    // 主分析：依 source 篩選回不同提交數，才能驗證摘要有跟著篩選對齊
    const source = url.searchParams.get('source');
    const row = SOURCE_BREAKDOWN.sources.find((item) => item.key === source);
    return json({
      form: { key: FORM_KEY, title: '來源分佈驗證表單', version: 1, allVersions: false },
      summary: {
        submissions: row ? row.total : SOURCE_BREAKDOWN.totals.total,
        uniquePeople: 601,
        completionRate: 1.0,
      },
      dimensions: [],
    });
  });
}

const browser = await chromium.launch();
try {
  const page = await browser.newPage();
  await mockAnalytics(page);
  await loginWithKey(page);

  // 等首次分析渲染完成（摘要換掉「載入中」字樣即代表 loadDynamicAnalytics 已跑完）
  await page.waitForFunction(
    () => document.querySelector('#dynamic-summary')?.textContent?.includes('提交'),
    null, { timeout: 15000 });

  // ── 1. 來源下拉改吃實際來源 ──────────────────────────────
  const optionTexts = await page.evaluate(() =>
    [...document.querySelectorAll('#dynamic-source option')].map((node) => node.textContent));
  ok(optionTexts[0] === '全部', `第一個選項為「全部」（實際：${optionTexts[0]}）`);
  ok(optionTexts.includes('讀者接續填答（1）'),
    `下拉含程式自動來源 newsletter_survey 的中文標籤（實際：${optionTexts.join(' / ')}）`);
  ok(optionTexts.includes('線上測驗（254）'), '下拉含匯入註冊表來源標籤與筆數');

  // ── 2. 未知來源退回原始 key ──────────────────────────────
  ok(optionTexts.includes('mystery_pipeline（3）'), '未知來源以原始 key 顯示，不被隱藏');

  // ── 3. 來源分佈表格 ─────────────────────────────────────
  const rows = await page.evaluate(() =>
    [...document.querySelectorAll('#source-breakdown tbody tr')]
      .map((node) => [...node.children].map((cell) => cell.textContent)));
  ok(rows.length === 6, `分佈表列出全部 6 個來源（實際 ${rows.length} 列）`);
  const examRow = rows.find((row) => row[0] === '線上測驗');
  ok(examRow && examRow[1] === '254' && examRow[2] === '0',
    `線上測驗 254 筆全為空殼（實際：${JSON.stringify(examRow)}）`);
  const readerRow = rows.find((row) => row[0] === '讀者接續填答');
  ok(readerRow && readerRow[1] === '1' && readerRow[2] === '1',
    `讀者接續填答 1 筆且有答案（實際：${JSON.stringify(readerRow)}）`);

  // ── 4. 摘要並列總提交與有填答內容 ────────────────────────
  const summary = await page.evaluate(() =>
    document.querySelector('#dynamic-summary').textContent);
  ok(summary.includes('提交 615 筆（其中 61 筆有填答內容）'),
    `摘要並列總提交與有填答筆數（實際：${summary}）`);
  ok(summary.includes('僅計必填欄位'), `完成率標註計算口徑（實際：${summary}）`);

  // ── 5. 套用來源篩選後摘要對齊該來源 ──────────────────────
  await page.selectOption('#dynamic-source', 'exam');
  await page.waitForFunction(
    () => document.querySelector('#dynamic-summary')?.textContent?.includes('提交 254 筆'),
    null, { timeout: 10000 });
  const filtered = await page.evaluate(() =>
    document.querySelector('#dynamic-summary').textContent);
  ok(filtered.includes('提交 254 筆（其中 0 筆有填答內容）'),
    `篩選 exam 後摘要對齊該來源而非全部加總（實際：${filtered}）`);

  // 篩選後分佈表仍列出全部來源（它的職責是回答「有哪些來源」）
  const rowsAfter = await page.evaluate(() =>
    document.querySelectorAll('#source-breakdown tbody tr').length);
  ok(rowsAfter === 6, `篩選後分佈表仍列出全部來源（實際 ${rowsAfter} 列）`);
} catch (error) {
  fail(error.message);
} finally {
  await browser.close();
}

console.log(failed ? `\n${failed} 項失敗` : '\n全部通過');
process.exit(failed ? 1 : 0);
