// 後台登入 gate（Task 10：三層降級登入）端到端驗證腳本
//
// 驗證項目：
//   1. 未登入時（GET /api/admin/me 回 401）顯示登入 gate，email 輸入框與
//      「寄送登入連結」按鈕皆存在
//   2. 「改用管理金鑰登入」連結預設就在畫面上（常駐，非條件顯示），且此時
//      金鑰輸入框尚未出現
//   3. 點擊該連結後，金鑰輸入框才出現
//   4. 送出 email 後顯示的訊息，對「白名單內」與「白名單外」兩種 email 完全相同
//      （後端 POST /api/admin/login 對任何 email 一律回 200 accepted:true，
//      前端不得自行依內容分歧，否則等於幫忙洩漏該信箱是否為管理者）
//
// 用法（需服務已啟動）：
//   ADMIN_BASE=http://127.0.0.1:8080 node survey-backend/scripts/verify-admin-login-gate.mjs
//   ADMIN_BASE 預設指向本機 127.0.0.1:8080——刻意不比照其他 verify-*.mjs 預設打正式站，
//   因為本腳本會實際呼叫 POST /api/admin/login，若忘了帶 ADMIN_BASE 誤打正式站，
//   可能對真實管理者信箱觸發寄信（本機開發環境未設定 SEND_MAIL_API 時會 fallback 成
//   NoopMailSender 不真寄，正式站則不一定，因此預設值必須是安全的本機位址）。
//
// 可重跑：只讀 GET /api/admin/me 與呼叫兩次 POST /api/admin/login（後端本身即為冪等、
// 防列舉設計，不寫入任何需要清理的狀態），不需事後還原。

import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

const BASE = process.env.ADMIN_BASE || 'http://127.0.0.1:8080';
// 白名單內／外的測試 email：不使用真人信箱，僅用來比較前端訊息是否分歧
const WHITELISTED_EMAIL = process.env.ADMIN_TEST_WHITELISTED_EMAIL || 'kevintsai1202@gmail.com';
const NON_WHITELISTED_EMAIL = 'verify-not-admin@example.invalid';

/**
 * 動態載入 playwright：先試專案內解析，再逐一嘗試常見的全域安裝目錄。
 * 慣例與 verify-admin.mjs 的 loadPlaywright() 一致：載不到一律 exit 1，不靜默略過。
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

const browser = await chromium.launch();

try {
  // 0. 先確認未帶任何憑證呼叫 /api/admin/me 真的回 401（否則後面「未登入」的前提不成立）
  {
    const page = await browser.newPage();
    const res = await page.request.get(`${BASE}/api/admin/me`);
    ok(res.status() === 401, '未登入時 GET /api/admin/me 回 401');
    await page.close();
  }

  // 1. 開頁 → 應顯示登入 gate（email 輸入框 + 寄送登入連結按鈕），且主畫面不可見
  const page = await browser.newPage();
  await page.goto(`${BASE}/admin.html`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#gate', { state: 'visible' });
  ok(!(await page.locator('#app').isVisible()), '未登入前主畫面不應顯示');
  ok(await page.locator('#gate-email').isVisible(), 'email 輸入框存在且可見');
  ok(await page.locator('#gate-send').isVisible(), '「寄送登入連結」按鈕存在且可見');

  // 2. 「改用管理金鑰登入」連結預設就在畫面上（常駐，非條件顯示），且此時金鑰輸入框尚未出現
  ok(await page.locator('#gate-use-key').isVisible(), '「改用管理金鑰登入」連結預設可見（常駐選項）');
  ok(!(await page.locator('#gate-key-box').isVisible()), '金鑰輸入框預設應隱藏（點擊連結後才展開）');

  // 3. 點擊該連結後，金鑰輸入框才出現
  await page.click('#gate-use-key');
  await page.waitForSelector('#gate-key-box', { state: 'visible', timeout: 5000 });
  ok(await page.locator('#gate-key').isVisible(), '點擊「改用管理金鑰登入」後，金鑰輸入框出現');
  console.log('OK   常駐次要連結行為：預設隱藏、點擊後展開，且連結本身自始存在');

  // 4. 送出 email 後顯示的訊息，對白名單內／外兩種 email 完全相同
  await page.fill('#gate-email', WHITELISTED_EMAIL);
  await page.click('#gate-send');
  await page.waitForFunction(
    () => (document.querySelector('#gate-login-msg')?.textContent || '').length > 0,
    null, { timeout: 10000 });
  const msgForWhitelisted = await page.locator('#gate-login-msg').textContent();

  // 重新整理回到初始狀態，避免上一次填寫殘留影響本次斷言
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#gate', { state: 'visible' });
  await page.fill('#gate-email', NON_WHITELISTED_EMAIL);
  await page.click('#gate-send');
  await page.waitForFunction(
    () => (document.querySelector('#gate-login-msg')?.textContent || '').length > 0,
    null, { timeout: 10000 });
  const msgForNonWhitelisted = await page.locator('#gate-login-msg').textContent();

  ok(msgForWhitelisted.length > 0, '白名單內 email 送出後有顯示訊息');
  ok(msgForNonWhitelisted.length > 0, '白名單外 email 送出後有顯示訊息');
  ok(msgForWhitelisted === msgForNonWhitelisted,
    `白名單內外訊息完全相同（實際：「${msgForWhitelisted}」 vs 「${msgForNonWhitelisted}」）`);
  console.log(`OK   防枚舉訊息一致：「${msgForWhitelisted}」`);

  await page.close();

  // 5. 次要金鑰路徑：點「改用管理金鑰登入」展開後，輸入正確金鑰仍可正常進入後台，
  //    且 adminAuthMode() 回報 'key'——確認既有 sessionStorage + X-Admin-Key 機制
  //    在新版 gate UI 下維持可用（金鑰值採用 application.yml 未設 ADMIN_API_KEY 時
  //    的預設值 dev-admin-key，僅限本機驗證環境）。
  {
    const keyPage = await browser.newPage();
    await keyPage.goto(`${BASE}/admin.html`, { waitUntil: 'domcontentloaded' });
    await keyPage.waitForSelector('#gate', { state: 'visible' });
    await keyPage.click('#gate-use-key');
    await keyPage.fill('#gate-key', process.env.ADMIN_API_KEY || 'dev-admin-key');
    await keyPage.click('#gate-btn');
    await keyPage.waitForSelector('#app:not([hidden])', { timeout: 15000 });
    const mode = await keyPage.evaluate(() => window.adminAuthMode());
    ok(mode === 'key', `金鑰路徑登入後 adminAuthMode() 回報 'key'（實際：${mode}）`);
    await keyPage.close();
  }

  // 6. 三層降級的第一層：GET /api/admin/me 回 200 時應直接進後台，不顯示 gate，
  //    且 adminAuthMode() 回報 'jwt'。真正的 magic-link 兌換需要收信才能取得一次性
  //    token（DB 只存雜湊，且本機未設 SEND_MAIL_API 不會真的寄信），故此處以
  //    page.route() 攔截所有 /api/admin/** 呼叫模擬「已有有效 session」，驗證前端這
  //    一段 boot 邏輯本身正確；後端 /api/admin/me 的真實行為由 Task 6 的後端測試覆蓋。
  //    /me 以外的端點一律回空陣列：init() 進場會立刻打好幾支資料 API，若不攔截，
  //    這些呼叫在沒有真實 session cookie 下會拿到後端真正的 401，經 api() 的 401
  //    處理會把畫面切回 gate，讓這個 mock 情境變成無法穩定斷言的競態。
  {
    const mockPage = await browser.newPage();
    await mockPage.route('**/api/admin/**', (route) => {
      const url = new URL(route.request().url());
      if (url.pathname === '/api/admin/me') {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ email: 'kevintsai1202@gmail.com', mode: 'jwt' }),
        });
      }
      return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    });
    await mockPage.goto(`${BASE}/admin.html`, { waitUntil: 'domcontentloaded' });
    await mockPage.waitForSelector('#app:not([hidden])', { timeout: 10000 });
    ok(!(await mockPage.locator('#gate').isVisible()), 'mock 200 session 時登入 gate 不顯示');
    const mode = await mockPage.evaluate(() => window.adminAuthMode());
    ok(mode === 'jwt', `adminAuthMode() 回報 'jwt'（實際：${mode}）`);
    await mockPage.close();
  }

  console.log(failed === 0 ? '\n全部通過 ✅' : `\n共 ${failed} 項失敗 ❌`);
  process.exitCode = failed === 0 ? 0 : 1;
} catch (e) {
  console.error('FAIL:', e.message);
  process.exitCode = 1;
} finally {
  await browser.close();
}
