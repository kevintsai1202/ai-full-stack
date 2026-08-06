// 信中投票卡「關係說明與轉換率」驗收腳本（Task 12 / B4）。
//
// 驗證項目：
//   [1] 卡片 hint 文字含「不受上方篩選條件影響」（B4 的關係說明）
//   [2] #vote-summary 文字含「累計 14 票」「完整提交（全版本不分期）8 筆」「57.1%」
//       （14 票、8 筆完整提交 → 8/14 = 57.1% 點擊→完填轉換率）
//   [3] analytics 端點（?allVersions=true）改回 404（表單無已發布版本的常見狀況）時，
//       #vote-summary 仍顯示「累計 14 票」，且優雅降級——不含「轉換率」字樣、不噴 console 錯誤
//
// 用法（後端須已在本機啟動；金鑰為 dev-admin-key，需以
// APP_ALLOW_INSECURE_DEV_SECRETS=true 啟動，慣例與 verify-admin-toolbar-theme.mjs 一致）：
//   ADMIN_BASE=http://127.0.0.1:8080 node survey-backend/scripts/verify-vote-card-context.mjs
//
// 絕不指向正式站：BASE 預設值就是本機位址，不需另外設定環境變數才安全。
// 本腳本只攔截 /api/admin/** 回假資料，不寫入任何後端狀態，可重跑。

import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

const BASE = process.env.ADMIN_BASE || 'http://127.0.0.1:8080';
const ADMIN_KEY = process.env.ADMIN_API_KEY || 'dev-admin-key';
/** 驗證用表單 key：任意值即可，路由攔截只依路徑樣式判斷 */
const FORM_KEY = 'vote-verify-form';

/**
 * 動態載入 playwright：先試專案內解析，再逐一嘗試常見的全域安裝目錄。
 * 慣例與 verify-admin-toolbar-theme.mjs / verify-admin-cost-prefill.mjs 一致。
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
/** 記錄一項失敗（不中斷後續案例，讓一次執行就看到所有問題） */
const fail = (msg) => { console.error('FAIL:', msg); failed++; };
const ok = (cond, label) => { if (!cond) fail(label); else console.log(`OK   ${label}`); };

/**
 * 用金鑰模式登入到主畫面（慣例同 verify-admin-toolbar-theme.mjs）。
 * API 攔截須在 goto 之前註冊完成，故呼叫端要先 setupRoutes 再呼叫本函式。
 */
async function loginWithKey(page) {
  await page.goto(`${BASE}/admin.html`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#gate', { state: 'visible' });
  await page.click('#gate-use-key');
  await page.fill('#gate-key', ADMIN_KEY);
  await page.click('#gate-btn');
  await page.waitForSelector('#app:not([hidden])', { timeout: 15000 });
}

/**
 * 註冊本腳本共用的 API 攔截：/api/admin/forms 回單一表單定義（讓 dynamic-form 下拉
 * 自動選中該表單、觸發 loadDynamicAnalytics → loadVoteStats），其餘 /api/admin/**
 * 一律回空陣列墊底，避免登入後其他分頁（電子報段、名額、寄送歷程等）的背景請求噴錯。
 *
 * votes 與「不分版本 analytics」兩支端點由呼叫端各自覆寫（votesStatus／analyticsStatus
 * 可在測試過程中動態切換，用來模擬 404 降級）。
 */
async function setupRoutes(page, { votesStatus, analyticsStatus }) {
  await page.route('**/api/admin/**', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
  });
  // /api/admin/me 若被上面的萬用攔截一起吞掉回 200，前端會誤判成已有有效 JWT
  // session 而直接略過金鑰閘門（#gate 永遠 hidden）——慣例與 verify-admin-cost-prefill.mjs
  // 一致，明確攔截回 401 讓流程照舊落回「輸入金鑰」這條路。
  await page.route('**/api/admin/me', async (route) => {
    await route.fulfill({ status: 401 });
  });
  await page.route('**/api/admin/forms', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([{
        key: FORM_KEY, version: 1, status: 'PUBLISHED', title: '投票卡驗證表單', fields: [],
      }]),
    });
  });
  // 信中投票統計：totalVotes 14、totalNamed 9（brief 指定值）
  await page.route(`**/api/admin/analytics/forms/${FORM_KEY}/votes`, async (route) => {
    if (votesStatus() !== 200) { await route.fulfill({ status: votesStatus() }); return; }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ totalVotes: 14, totalNamed: 9, options: [], byCampaign: [] }),
    });
  });
  // 不分版本 analytics（?allVersions=true）：submissions 8（brief 指定值）；
  // 帶版本查詢字串的一般 analytics 請求（loadDynamicAnalytics 自己的呼叫）回一個
  // 安全的空殼，避免 #dynamic-summary 那段程式碼噴錯。
  await page.route(new RegExp(`/api/admin/analytics/forms/${FORM_KEY}(\\?|$)`), async (route) => {
    const url = new URL(route.request().url());
    if (url.searchParams.get('allVersions') === 'true') {
      if (analyticsStatus() !== 200) { await route.fulfill({ status: analyticsStatus() }); return; }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ summary: { submissions: 8, uniquePeople: 8, completionRate: 1 } }),
      });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ summary: { submissions: 0, uniquePeople: 0, completionRate: 0 }, dimensions: [] }),
    });
  });
}

const browser = await chromium.launch();

try {
  let votesHttpStatus = 200;
  let analyticsHttpStatus = 200;
  const page = await browser.newPage();
  const consoleErrors = [];
  page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });
  const pageErrors = [];
  page.on('pageerror', (e) => pageErrors.push(e.message));

  await setupRoutes(page, {
    votesStatus: () => votesHttpStatus,
    analyticsStatus: () => analyticsHttpStatus,
  });

  await loginWithKey(page);

  // ---- [1] hint 文案：關係說明 ----
  console.log('\n[1] hint 文案含關係說明');
  const hintText = await page.locator('.card:has(#vote-summary) .hint').first().textContent();
  ok((hintText || '').includes('不受上方篩選條件影響'),
    `hint 含「不受上方篩選條件影響」（實際：「${hintText}」）`);

  // ---- [2] 正常情境：summary 文字含累計票數、完整提交筆數與轉換率 ----
  console.log('\n[2] 正常情境：投票與完整提交的漏斗關係與轉換率');
  await page.waitForFunction(
    () => (document.querySelector('#vote-summary')?.textContent || '').includes('累計 14 票'),
    null, { timeout: 10000 });
  const summaryText = await page.locator('#vote-summary').textContent();
  ok((summaryText || '').includes('累計 14 票'), `含「累計 14 票」（實際：「${summaryText}」）`);
  ok((summaryText || '').includes('完整提交（全版本不分期）8 筆'),
    `含「完整提交（全版本不分期）8 筆」（實際：「${summaryText}」）`);
  ok((summaryText || '').includes('57.1%'), `含轉換率「57.1%」（實際：「${summaryText}」）`);

  // ---- [3] analytics 404 降級：仍顯示累計票數，且不含「轉換率」，也不噴 console 錯誤 ----
  console.log('\n[3] analytics 端點 404（無已發布版本）時優雅降級');
  analyticsHttpStatus = 404;
  await page.click('#dynamic-refresh'); // 觸發重新載入（含 loadVoteStats）
  await page.waitForTimeout(800); // 給 Promise.all 與 DOM 更新時間
  const degradedText = await page.locator('#vote-summary').textContent();
  ok((degradedText || '').includes('累計 14 票'),
    `analytics 404 時仍顯示「累計 14 票」（實際：「${degradedText}」）`);
  ok(!(degradedText || '').includes('轉換率'),
    `analytics 404 時不含「轉換率」（優雅降級，實際：「${degradedText}」）`);
  ok(!(degradedText || '').includes('undefined') && !(degradedText || '').includes('NaN'),
    `降級文字不含 undefined／NaN（實際：「${degradedText}」）`);

  // 「Failed to load resource」是 Chromium 對 401/404 這類非 2xx 回應的網路層記錄，
  // 不是應用程式呼叫 console.error()——本情境刻意觸發 404（且 /api/admin/me 本就
  // 故意回 401），這類噪音必須被濾除，才不會把「瀏覽器如實記錄失敗的請求」
  // 誤判成「應用程式碼吞錯後又自己噴錯」。
  const appConsoleErrors = consoleErrors.filter((m) => !/Failed to load resource/.test(m));
  ok(appConsoleErrors.length === 0,
    `analytics 404 降級時應用程式碼未額外噴 console 錯誤（實際：${appConsoleErrors.join(' | ')}）`);
  ok(pageErrors.length === 0, `全程無未捕捉頁面錯誤（實際：${pageErrors.join(' | ')}）`);

  console.log(failed === 0 ? '\n全部通過 ✅' : `\n共 ${failed} 項失敗 ❌`);
  process.exitCode = failed === 0 ? 0 : 1;
} catch (e) {
  console.error('FAIL:', e.message, e.stack);
  process.exitCode = 1;
} finally {
  await browser.close();
}
