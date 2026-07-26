// 後台「切到 PREMIUM 自動預填解鎖點數」的可重跑驗收（C2）。
//
// 為什麼需要真實瀏覽器：這段行為完全在 admin.html 的 JS 裡，HTTP 層完全看不到——
// 監聽器沒接上、prefillPremiumCost 拋錯、或預填覆蓋掉操作者已輸入的值，
// 後端測試一條都驗不出來。專案沒有 JS 測試框架，所以寫成可重跑的 Playwright 腳本。
//
// 為什麼不需要跑起後端、也不需要 ADMIN_API_KEY：本腳本自己起一個只回傳
// admin.html 的極簡靜態伺服器，並用 page.route() 攔截所有 /api/admin/** 請求回假資料。
// 驗的是前端邏輯，攔截讓它與後端狀態、金鑰、資料庫完全解耦——任何時候都能重跑。
//
// 執行：
//   node survey-backend/scripts/verify-admin-cost-prefill.mjs
//   node survey-backend/scripts/verify-admin-cost-prefill.mjs --headed   # 看得到瀏覽器
//
// 載不到 playwright 一律算失敗（exit 1），不會只印警告然後 exit 0——
// 那樣斷言完全沒跑而 CI 顯示綠燈，比沒有這支腳本更糟。

import { createServer } from 'node:http';
import { readFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const ADMIN_HTML = join(HERE, '..', 'src', 'main', 'resources', 'static', 'admin.html');
const HEADED = process.argv.includes('--headed');

/** 假的全域預設解鎖點數。刻意不是 10（真實預設值）：用真實值的話，
 *  即使實作把數字寫死在 HTML 的 placeholder 上，斷言也會通過。 */
const FAKE_PREMIUM_COST = 37;
/** 操作者自己輸入的值；用來驗「不得覆蓋」 */
const OPERATOR_COST = 5;

let failures = 0;

/** 印出一條斷言結果；失敗時累計 failures（最後決定 exit code） */
function check(label, ok, detail = '') {
  console.log(`  ${ok ? '✓' : '✗'} ${label}${detail ? `　→ ${detail}` : ''}`);
  if (!ok) failures++;
}

/** 值相等斷言 */
function eq(actual, expected, label) {
  check(label, String(actual) === String(expected), `實際 ${JSON.stringify(actual)}／預期 ${JSON.stringify(expected)}`);
}

/**
 * 載入 playwright：先試專案內解析，再逐一嘗試常見的全域安裝目錄。
 * 刻意不呼叫 `npm root -g`（那需要 shell），作法與 verify-publish-endpoint.mjs 一致。
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
  throw new Error('專案內與常見全域安裝目錄都找不到 playwright');
}

/** 只回傳 admin.html 的極簡靜態伺服器；回傳 {port, close} */
async function serveAdminHtml() {
  const html = readFileSync(ADMIN_HTML, 'utf8');
  const server = createServer((req, res) => {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(html);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  return { port: server.address().port, close: () => new Promise(r => server.close(r)) };
}

const main = async () => {
  if (!existsSync(ADMIN_HTML)) {
    check('找到 admin.html', false, ADMIN_HTML);
    return;
  }

  let playwright;
  try {
    const mod = await loadPlaywright();
    playwright = mod.default ?? mod;
  } catch (e) {
    check('載入 playwright', false, `${e.message}；請安裝：npm i -g playwright`);
    return;
  }

  const site = await serveAdminHtml();
  const browser = await playwright.chromium.launch({ headless: !HEADED });
  /** /api/admin/settings 被實際呼叫的次數；用來驗快取（不該每次切換都打一次） */
  let settingsCalls = 0;

  try {
    const page = await browser.newPage();

    // 攔截全部後台 API：本腳本驗的是前端邏輯，不需要真的後端、金鑰或資料庫。
    //
    // 註冊順序有意義：Playwright 的 route 是「後註冊者先比對」，所以萬用攔截必須
    // 先註冊、專用攔截後註冊。順序寫反的話 `**/api/admin/**` 會把 settings 請求
    // 一起吃掉並回空陣列，於是預填永遠拿不到值——這個坑實際踩過一次。
    await page.route('**/api/admin/**', async route => {
      const url = route.request().url();
      const body = url.includes('/mail-quota')
        ? { used: 0, limit: 100, remaining: 100, source: 'stub', reserve: 0, marketingRemaining: 100, marketingBatchMax: 100 }
        : url.includes('/recipients') ? { count: 0, sample: [] }
        : url.includes('/invites') ? { invitedCount: 0, remindedCount: 0, confirmedCount: 0, pendingCount: 0, logs: [] }
        : [];
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    });
    await page.route('**/api/admin/settings', async route => {
      settingsCalls++;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        // 回應形狀刻意照真實的 {value,min,max}：若實作誤把整個物件當數字用，
        // 欄位會出現 "[object Object]" 而被下面的斷言抓到。
        body: JSON.stringify({
          'credit.signup_grant': { value: 300, min: 0, max: 100000 },
          'credit.premium_cost': { value: FAKE_PREMIUM_COST, min: 1, max: 10000 },
          'credit.referral_reward': { value: 100, min: 0, max: 100000 },
          'vip.default_days': { value: 365, min: 1, max: 3650 },
        }),
      });
    });

    await page.goto(`http://127.0.0.1:${site.port}/admin.html`, { waitUntil: 'domcontentloaded' });

    // 解鎖金鑰閘門（/api/admin/survey 已被攔截成 200，任何金鑰都會通過）
    await page.fill('#gate-key', 'stub-key');
    await page.click('#gate-btn');
    await page.waitForSelector('#app:not([hidden])', { timeout: 10000 });
    // 切到電子報頁；刻意<b>不</b>開參數分頁——要驗的正是「參數分頁還沒開過、
    // settings 還沒載入」時預填仍然要能work（loadSettings 是惰性的）。
    await page.click('.tab[data-view="campaign"]');
    await page.waitForSelector('#art-tier', { state: 'visible', timeout: 10000 });

    console.log('\n[1] 空欄位切到 PREMIUM → 預填全域預設值（參數分頁從未開過）');
    eq(settingsCalls, 0, '★ 切換前尚未打過 /api/admin/settings（惰性載入）');
    eq(await page.inputValue('#art-cost'), '', '前置條件：解鎖點數欄位是空的');
    await page.selectOption('#art-tier', 'PREMIUM');
    await page.waitForFunction(
      expected => document.querySelector('#art-cost')?.value === String(expected),
      FAKE_PREMIUM_COST, { timeout: 10000 });
    eq(await page.inputValue('#art-cost'), FAKE_PREMIUM_COST, '★ 解鎖點數已預填成全域預設值');
    eq(settingsCalls, 1, '★ 預填時單獨 fetch 了一次 settings');
    const costHint = await page.textContent('#cost-hint');
    check('★ hint 說明「已帶入目前的全域預設值，可修改」',
      !!costHint && costHint.includes('已帶入目前的全域預設值，可修改'), costHint);
    check('★ hint 說明「發布後價格即凍結」',
      !!costHint && costHint.includes('發布後價格即凍結'), costHint);
    check('欄位維持必填、未被鎖定（可改）', !(await page.isDisabled('#art-cost')));

    console.log('\n[2] 欄位非空時不得覆蓋操作者已輸入的值（本項的核心）');
    // 回到 BASIC 並填入操作者自己的價格，再切回 PREMIUM
    await page.selectOption('#art-tier', 'BASIC');
    await page.fill('#art-cost', String(OPERATOR_COST));
    await page.selectOption('#art-tier', 'PREMIUM');
    // 給預填邏輯足夠的時間跑完（它是 async）；若它會覆蓋，這段等待就會抓到
    await page.waitForTimeout(500);
    eq(await page.inputValue('#art-cost'), OPERATOR_COST,
      '★ 操作者輸入的 5 未被預設值 37 覆蓋');

    // 刻意不驗「只留空白字元」：type=number 的 input 不接受空白字元，
    // 瀏覽器會直接把它正規化成空字串，造不出那個狀態。實作的 trim() 是防禦性的。
    console.log('\n[3] 操作者把值清空後再切回 PREMIUM，會重新預填');
    await page.selectOption('#art-tier', 'BASIC');
    await page.fill('#art-cost', '');
    await page.selectOption('#art-tier', 'PREMIUM');
    await page.waitForFunction(
      expected => document.querySelector('#art-cost')?.value === String(expected),
      FAKE_PREMIUM_COST, { timeout: 10000 });
    eq(await page.inputValue('#art-cost'), FAKE_PREMIUM_COST, '清空後再切回 PREMIUM 會重新預填');

    console.log('\n[4] 反覆切換分級不得重複打 API（快取）');
    const before = settingsCalls;
    for (let i = 0; i < 3; i++) {
      await page.selectOption('#art-tier', 'BASIC');
      await page.fill('#art-cost', '');
      await page.selectOption('#art-tier', 'PREMIUM');
      await page.waitForFunction(
        expected => document.querySelector('#art-cost')?.value === String(expected),
        FAKE_PREMIUM_COST, { timeout: 10000 });
    }
    eq(settingsCalls, before, '★ 三輪切換後 settings 呼叫次數未增加（沿用快取）');

    console.log('\n[5] 切回 BASIC 清掉價格凍結說明（那句話只適用 PREMIUM）');
    await page.selectOption('#art-tier', 'BASIC');
    await page.waitForFunction(() => document.querySelector('#cost-hint')?.textContent === '', null, { timeout: 5000 });
    eq(await page.textContent('#cost-hint'), '', 'BASIC 不顯示解鎖點數的說明');

    console.log('\n[6] 儲存參數後快取失效，預填改帶新值');
    const NEW_COST = 88;
    // 讓 PUT 成功，並讓之後的 GET 回新值
    await page.unroute('**/api/admin/settings');
    await page.route('**/api/admin/settings', async route => {
      if (route.request().method() === 'PUT') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
        return;
      }
      settingsCalls++;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ 'credit.premium_cost': { value: NEW_COST, min: 1, max: 10000 } }),
      });
    });
    await page.click('.tab[data-view="settings"]');
    await page.waitForSelector('#settings-fields input[data-key]', { timeout: 10000 });
    await page.click('#settings-save-btn');
    await page.waitForFunction(
      () => document.querySelector('#settings-msg')?.textContent?.includes('已儲存'),
      null, { timeout: 10000 });
    await page.click('.tab[data-view="campaign"]');
    await page.fill('#art-cost', '');
    await page.selectOption('#art-tier', 'BASIC');
    await page.selectOption('#art-tier', 'PREMIUM');
    await page.waitForFunction(
      expected => document.querySelector('#art-cost')?.value === String(expected),
      NEW_COST, { timeout: 10000 });
    eq(await page.inputValue('#art-cost'), NEW_COST,
      '★ 改過預設值之後，預填帶的是新值（快取已失效）');

    console.log('\n[7] settings 讀不到時不預填，但不阻斷發布流程');
    await page.unroute('**/api/admin/settings');
    await page.route('**/api/admin/settings', route =>
      route.fulfill({ status: 500, contentType: 'application/json', body: '{"detail":"boom"}' }));
    // 重新載入頁面，讓快取歸零（快取是頁面生命週期內的變數）
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#app:not([hidden])', { timeout: 10000 });
    await page.click('.tab[data-view="campaign"]');
    await page.selectOption('#art-tier', 'PREMIUM');
    await page.waitForFunction(
      () => (document.querySelector('#cost-hint')?.textContent || '').includes('請手動填入'),
      null, { timeout: 10000 });
    eq(await page.inputValue('#art-cost'), '', '讀不到預設值時欄位留空（不填假值）');
    check('★ 提示操作者手動填入，且發布按鈕仍可用',
      !(await page.isDisabled('#publish-btn')), await page.textContent('#cost-hint'));

    // 主控台不得有未處理錯誤（預填是 async，拋錯很容易只在 console 留痕）
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    await page.selectOption('#art-tier', 'BASIC');
    await page.selectOption('#art-tier', 'PREMIUM');
    await page.waitForTimeout(300);
    check('沒有未捕捉的頁面錯誤', errors.length === 0, errors.join('；'));

  } finally {
    try { await browser.close(); } catch (e) { console.log(`  ! 關閉瀏覽器失敗：${e.message}`); }
    await site.close();
  }
};

await main();
console.log(`\n${failures === 0 ? '全部通過' : `失敗 ${failures} 項`}`);
process.exit(failures === 0 ? 0 : 1);
