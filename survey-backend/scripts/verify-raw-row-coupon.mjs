// 原始資料表「逐列寄券」端到端驗證腳本（Task 9 / B2 前端）
//
// 驗證項目：
//   1. 原始資料表每列都有「寄券」按鈕；已收過券的列（one@example.com）顯示「已寄：」標示
//   2. 點寄券鈕開啟 #coupon-send-dialog：收件人正確；下拉不含已過期券（id 2）；
//      已寄過的券（id 3）在下拉中 disabled 且文字含「已寄過」
//   3. 選未寄過的券（id 1）確認寄出 → 攔截到的 request body 為
//      {"emails":["one@example.com"],"limit":1,"single":true}；dialog 關閉；
//      該列已寄標示就地更新為含兩張券（id 3 與新寄出的 id 1）
//
// 全程以 page.route() 攔截 /api/admin/survey、/api/admin/coupons、
// /api/admin/coupons/sent-map、/api/admin/coupons/1/send 四個 API，餵假資料，
// 不觸碰任何真實資料庫內容；其餘 /api/admin/** 一律回傳空陣列，讓頁面其他
// 初始化流程（表單定義、活動分眾、額度等）安靜通過而不影響本次斷言。
//
// 用法（後端須已在本機啟動；金鑰為 dev-admin-key，需以
// APP_ALLOW_INSECURE_DEV_SECRETS=true 啟動）：
//   node survey-backend/scripts/verify-raw-row-coupon.mjs
//
// 絕對不要把 ADMIN_BASE 指向正式站——預設值刻意設為本機位址（比照
// verify-admin-toolbar-theme.mjs 的慣例）。
//
// 可重跑：全程 route 攔截、不寫入任何後端狀態。

import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

const BASE = process.env.ADMIN_BASE || 'http://127.0.0.1:8080';
const ADMIN_KEY = process.env.ADMIN_API_KEY || 'dev-admin-key';

/**
 * 動態載入 playwright：慣例與 verify-admin-toolbar-theme.mjs 一致，
 * 先試專案內解析，再逐一嘗試常見的全域安裝目錄，載不到一律 exit 1。
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
  const mod = await loadPlaywright();
  const pw = mod.default ?? mod;
  chromium = pw.chromium;
  if (!chromium) throw new Error('載入的 playwright 模組沒有 chromium 匯出');
} catch (e) {
  console.error('FAIL:', e.message);
  process.exit(1);
}

let failed = 0;
/** 記錄一項失敗（不中斷後續案例，讓一次執行就看到所有問題）。 */
const fail = (msg) => { console.error('FAIL:', msg); failed++; };
const ok = (cond, label) => { if (!cond) fail(label); else console.log(`OK   ${label}`); };

// ---- 假資料 ----
/** 兩列原始問卷資料：one@example.com 已收過 id 3 的券；two@example.com 尚未收過任何券。 */
const SURVEY_ROWS = [
  {
    id: 1, createdAt: '2026-08-01T09:00:00+08:00', email: 'one@example.com', name: '甲君',
    role: '前端工程師', experience: '1-3 年', frontendExperience: '1-3 年', budget: '3000-5000',
    interest: ['前端整合'], answers: {}, utm: {}, source: 'survey_form', consent: true, unsubscribed: false,
  },
  {
    id: 2, createdAt: '2026-08-02T09:00:00+08:00', email: 'two@example.com', name: '乙君',
    role: '後端工程師', experience: '1-3 年', frontendExperience: '未填', budget: '5000-8000',
    interest: ['RAG 知識庫'], answers: {}, utm: {}, source: 'survey_form', consent: true, unsubscribed: false,
  },
];
/** 三張優惠券活動：id 1 未過期、id 2 已過期（2020-01-01）、id 3 未過期。 */
const COUPON_CAMPAIGNS = [
  { id: 1, courseName: 'AI 賦能全端開發', pitch: 'p', courseUrl: 'https://example.invalid', couponCode: 'CPN-ONE', expiresAt: '2099-01-01', formKey: 'survey', status: 'ACTIVE', sentCount: 0, createdAt: '2026-08-01T00:00:00+08:00', updatedAt: '2026-08-01T00:00:00+08:00' },
  { id: 2, courseName: '已過期課程', pitch: 'p', courseUrl: 'https://example.invalid', couponCode: 'CPN-EXPIRED', expiresAt: '2020-01-01', formKey: 'survey', status: 'ACTIVE', sentCount: 0, createdAt: '2026-07-01T00:00:00+08:00', updatedAt: '2026-07-01T00:00:00+08:00' },
  { id: 3, courseName: '進階課程', pitch: 'p', courseUrl: 'https://example.invalid', couponCode: 'CPN-THREE', expiresAt: '2099-01-01', formKey: 'survey', status: 'ACTIVE', sentCount: 1, createdAt: '2026-07-15T00:00:00+08:00', updatedAt: '2026-07-15T00:00:00+08:00' },
];
/** 已寄總覽：one@example.com 已收過 id 3。 */
const SENT_MAP = { 'one@example.com': [3] };

/** 掛上本測試需要的四個 API 攔截，其餘 /api/admin/** 一律回空陣列。 */
async function installRoutes(page, capture) {
  await page.route('**/api/admin/**', (route) => {
    const req = route.request();
    const url = new URL(req.url());
    if (url.pathname === '/api/admin/survey' && req.method() === 'GET') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(SURVEY_ROWS) });
    }
    if (url.pathname === '/api/admin/coupons' && req.method() === 'GET') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(COUPON_CAMPAIGNS) });
    }
    if (url.pathname === '/api/admin/coupons/sent-map' && req.method() === 'GET') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(SENT_MAP) });
    }
    if (url.pathname === '/api/admin/me' && req.method() === 'GET') {
      // 強制回 401：本測試走金鑰模式登入，避免 bootAuth() 誤把「[]」當成已登入的 JWT session 而跳過登入閘門。
      return route.fulfill({ status: 401, contentType: 'application/json', body: '{}' });
    }
    if (url.pathname === '/api/admin/coupons/1/send' && req.method() === 'POST') {
      capture.body = req.postData();
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ attempted: 1, sent: 1, skipped: 0, failed: 0, remaining: 0 }),
      });
    }
    // 其他 admin API（表單定義、活動分眾、額度、寄送歷程……）：回空陣列，讓頁面初始化安靜通過。
    return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
  });
}

/** 掛好路由攔截後，用金鑰模式登入到主畫面，回傳已登入的 page。 */
async function loginWithKey(browser, capture) {
  const page = await browser.newPage();
  await installRoutes(page, capture);
  await page.goto(`${BASE}/admin.html`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#gate', { state: 'visible' });
  await page.click('#gate-use-key');
  await page.fill('#gate-key', ADMIN_KEY);
  await page.click('#gate-btn');
  await page.waitForSelector('#app:not([hidden])', { timeout: 15000 });
  return page;
}

const browser = await chromium.launch();

try {
  const capture = {};
  const page = await loginWithKey(browser, capture);

  // 分析分頁預設就是進站首頁，等原始資料表渲染完成（含操作欄）。
  await page.waitForSelector('#raw-table tbody tr', { timeout: 15000 });
  // loadAnalytics 的券資料是併發補載，等一輪 microtask/宏任務後再讀，確保已寄標示已就地更新。
  await page.waitForFunction(
    () => document.querySelectorAll('#raw-table tbody tr').length === 2,
    { timeout: 15000 },
  );

  // ---- 斷言 1：每列都有寄券按鈕；one@example.com 顯示已寄標示 ----
  const rows = await page.locator('#raw-table tbody tr').all();
  ok(rows.length === 2, `原始資料表渲染出 2 列（實際：${rows.length}）`);

  const sendButtons = page.locator('#raw-table tbody tr button:has-text("寄券")');
  ok(await sendButtons.count() === 2, `每一列都有「寄券」按鈕（實際數量：${await sendButtons.count()}）`);

  // 依 email 定位列（Email 欄在 RAW_COLUMNS 第 3 欄）。
  const rowByEmail = async (email) => {
    const all = await page.locator('#raw-table tbody tr').all();
    for (const tr of all) {
      const emailCell = await tr.locator('td').nth(2).textContent();
      if (emailCell?.trim() === email) return tr;
    }
    return null;
  };

  const oneRow = await rowByEmail('one@example.com');
  ok(oneRow !== null, '找到 one@example.com 那一列');
  const oneRowText = oneRow ? await oneRow.locator('td').last().textContent() : '';
  ok((oneRowText || '').includes('已寄：'), `one@example.com 列顯示「已寄：」標示（實際：「${oneRowText}」）`);

  const twoRow = await rowByEmail('two@example.com');
  ok(twoRow !== null, '找到 two@example.com 那一列');
  const twoRowText = twoRow ? await twoRow.locator('td').last().textContent() : '';
  ok(!(twoRowText || '').includes('已寄：'), `two@example.com 列尚無已寄標示（實際：「${twoRowText}」）`);

  // ---- 斷言 2：點 one@example.com 的寄券鈕 → dialog 開啟，內容正確 ----
  await oneRow.locator('button:has-text("寄券")').click();
  await page.waitForSelector('#coupon-send-dialog[open]', { timeout: 5000 });
  const dialogEmail = await page.locator('#coupon-send-dialog-email').textContent();
  ok(dialogEmail === 'one@example.com', `dialog 收件人正確（實際：「${dialogEmail}」）`);

  const optionTexts = await page.locator('#coupon-send-dialog-select option').allTextContents();
  const optionValues = await page.locator('#coupon-send-dialog-select option').evaluateAll(
    (opts) => opts.map((o) => ({ value: o.value, disabled: o.disabled, text: o.textContent })),
  );
  ok(!optionValues.some((o) => o.value === '2'), `下拉不含已過期券 id 2（實際選項：${JSON.stringify(optionValues)}）`);
  const optThree = optionValues.find((o) => o.value === '3');
  ok(!!optThree, '下拉含未過期已寄過的券 id 3');
  ok(!!optThree && optThree.disabled === true, `id 3 的 option 為 disabled（實際：${optThree && optThree.disabled}）`);
  ok(!!optThree && optThree.text.includes('已寄過'), `id 3 的 option 文字含「已寄過」（實際：「${optThree && optThree.text}」）`);
  const optOne = optionValues.find((o) => o.value === '1');
  ok(!!optOne && optOne.disabled === false, 'id 1（未寄過、未過期）的 option 可選取');

  // ---- 斷言 3：選 id 1 確認寄出 → body 正確、dialog 關閉、已寄標示就地更新 ----
  await page.selectOption('#coupon-send-dialog-select', '1');
  await page.click('#coupon-send-dialog-confirm');
  // 關閉後的 <dialog> 天生零尺寸／不可見，不能用 waitForSelector 的預設 visible 狀態等待；
  // 直接輪詢 DOM 的 open 屬性才是正確訊號。
  await page.waitForFunction(
    () => !document.querySelector('#coupon-send-dialog').open,
    { timeout: 5000 },
  );

  ok(!!capture.body, '攔截到 POST /api/admin/coupons/1/send 的 request body');
  let parsedBody = null;
  try { parsedBody = JSON.parse(capture.body); } catch { /* 留給下面斷言回報 */ }
  ok(
    !!parsedBody
      && Array.isArray(parsedBody.emails) && parsedBody.emails.length === 1 && parsedBody.emails[0] === 'one@example.com'
      && parsedBody.limit === 1 && parsedBody.single === true,
    `request body 為 {"emails":["one@example.com"],"limit":1,"single":true}（實際：${capture.body}）`,
  );

  ok(!(await page.locator('#coupon-send-dialog').isVisible()), 'dialog 已關閉');

  const oneRowAfter = await rowByEmail('one@example.com');
  const oneRowTextAfter = oneRowAfter ? await oneRowAfter.locator('td').last().textContent() : '';
  ok(
    (oneRowTextAfter || '').includes('CPN-ONE') && (oneRowTextAfter || '').includes('CPN-THREE'),
    `one@example.com 列的已寄標示就地更新為含兩張券（實際：「${oneRowTextAfter}」）`,
  );

  await page.close();

  console.log(failed === 0 ? '\n全部通過 ✅' : `\n共 ${failed} 項失敗 ❌`);
  process.exitCode = failed === 0 ? 0 : 1;
} catch (e) {
  console.error('FAIL:', e.message, e.stack);
  process.exitCode = 1;
} finally {
  await browser.close();
}
