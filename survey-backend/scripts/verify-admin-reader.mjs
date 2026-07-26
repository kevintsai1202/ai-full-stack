// 後台「讀者管理 + 參數設定」端到端驗證腳本
//
// 用途：驗證 Task 11–14 的後台能力真的可用，並證明 spec §5.11「數字動態注入」成立——
//       後台改參數後，讀者端 /r/rules 顯示的數字真的跟著變（單元測試用的是 mock，
//       只有這裡能證明整條鏈路串起來了）。
//
// 驗證項目：
//   1. 搜尋讀者回 200 且結構正確
//   2. 授予 VIP → 搜尋結果 vipActive: true
//   3. 取消 VIP → vipActive: false 且 vipExpiresAt 為 null
//   4. 批次加點 2 筆 → granted: 2，帳本查得到兩筆 ADMIN_GRANT
//   5. 加點缺 note → 回 400
//   6. credit.premium_cost 改 20 → 讀回 20 → /r/rules 出現「進階文章每篇 20 點」→ 改回原值
//   7. 未帶 X-Admin-Key 的後台端點必須回 401
//
// 用法（需服務已啟動）：
//   $env:ADMIN_API_KEY="<金鑰>"; node survey-backend/scripts/verify-admin-reader.mjs
//   加 --browser 會另外用 Playwright 實際操作 admin.html 的「讀者管理」分頁；
//   此模式下若載不到 playwright 一律視為失敗（exit 1），不會靜默略過。
//   ADMIN_BASE 預設 http://127.0.0.1:8080。
//
// 可重跑：測試用 email 固定（.invalid 網域，不可能是真人），VIP 與點數在結尾會被還原，
//   因此重跑不會累積狀態。credit_txn 帳本依設計只增不改（餘額必須能由帳本重算），
//   還原點數是靠一筆補償性的負數 ADMIN_GRANT，而非刪除歷史列——刻意不刪任何資料。

import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

const BASE = process.env.ADMIN_BASE || 'http://127.0.0.1:8080';
const KEY = process.env.ADMIN_API_KEY;
const USE_BROWSER = process.argv.includes('--browser');
if (!KEY) { console.error('請先設定環境變數 ADMIN_API_KEY'); process.exit(1); }

// 固定的測試 email：.invalid 是 RFC 2606 保留網域，不可能對應真實收件人
const T1 = 'verify-admin-reader-1@example.invalid';
const T2 = 'verify-admin-reader-2@example.invalid';
/** 本次加點的點數，結尾會以同額負數補償回去 */
const GRANT_DELTA = 7;

let failed = 0;
/** 記錄一項失敗（不中斷後續案例，讓一次執行就看到所有問題） */
const fail = (msg) => { console.error('FAIL:', msg); failed++; };
/** 斷言相等 */
const eq = (actual, expected, label) => {
  if (String(actual) !== String(expected)) fail(`${label}：預期 ${expected}，實際 ${actual}`);
  else console.log(`OK   ${label} = ${actual}`);
};
/** 斷言為真 */
const ok = (cond, label) => { if (!cond) fail(label); else console.log(`OK   ${label}`); };
/** 斷言字串包含 */
const has = (text, needle, label) => {
  if (!String(text).includes(needle)) fail(`${label}：預期包含「${needle}」`);
  else console.log(`OK   ${label} 含「${needle}」`);
};

/**
 * 呼叫受保護的後台 API。
 * @param {string} path   端點路徑
 * @param {object} opts   fetch 選項（method / body）
 * @param {boolean} noKey true 時刻意不帶金鑰（用於 401 驗證）
 * @returns {{status:number, body:any}} 狀態碼與已解析的回應
 */
async function api(path, opts = {}, noKey = false) {
  const headers = { 'Content-Type': 'application/json' };
  if (!noKey) headers['X-Admin-Key'] = KEY;
  const res = await fetch(BASE + path, { ...opts, headers });
  const text = await res.text();
  let body = text;
  try { body = text ? JSON.parse(text) : null; } catch { /* 非 JSON 回應原樣保留 */ }
  return { status: res.status, body };
}

/**
 * 載入 playwright：優先用專案內安裝，找不到時退回全域安裝目錄。
 * ESM 的模組解析以「腳本所在目錄」為基準，而 survey-backend 沒有 node_modules，
 * 因此必須顯式列出全域安裝路徑（作法與 verify-admin-quota.mjs 一致）。
 * 找不到時直接拋錯——呼叫端是明確要求了 --browser，靜默略過等於驗證沒跑。
 */
async function loadPlaywright() {
  try {
    return await import('playwright');
  } catch {
    const candidates = [
      process.env.APPDATA && join(process.env.APPDATA, 'npm', 'node_modules'),
      '/usr/local/lib/node_modules',
      '/usr/lib/node_modules',
    ].filter(Boolean);
    for (const root of candidates) {
      const entry = join(root, 'playwright', 'index.js');
      if (existsSync(entry)) return await import(pathToFileURL(entry).href);
    }
    throw new Error('指定了 --browser 但找不到 playwright，請執行 npm i -g playwright');
  }
}

/** 用瀏覽器實際操作 admin.html 的「讀者管理」分頁，確認 UI 真的把資料渲染出來 */
async function verifyBrowser() {
  const playwright = await loadPlaywright();
  const chromium = playwright.chromium ?? playwright.default?.chromium;
  const browser = await chromium.launch();
  try {
    const page = await browser.newPage();
    await page.goto(`${BASE}/admin.html`, { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#gate', { state: 'visible' });
    ok(!(await page.locator('#app').isVisible()), '未驗證前主畫面不顯示');
    await page.fill('#gate-key', KEY);
    await page.click('#gate-btn');
    await page.waitForSelector('#app', { state: 'visible', timeout: 15000 });

    // 讀者管理分頁：搜尋測試帳號應至少列出一列
    await page.click('#tab-readers');
    await page.fill('#reader-q', 'verify-admin-reader');
    await page.click('#reader-search-btn');
    await page.waitForFunction(
      () => document.querySelectorAll('#reader-table tbody tr').length > 0, null, { timeout: 15000 });
    const firstCell = await page.locator('#reader-table tbody tr td').first().textContent();
    has(firstCell, 'verify-admin-reader', '讀者表格第一格顯示 email');

    // XSS 回歸：讀者 email 是使用者可控內容（後台對任意 email 都能授予 VIP，
    // 因此攻擊者能自己決定這個字串）。用 route 攔截塞入兩種 payload，不寫進真實資料庫。
    //
    // payload A：`<img onerror>` —— 針對「innerHTML 完全沒跳脫」。
    // payload B：`" onmouseover="…` —— 針對「用轉義函式拼 innerHTML」。舊版 esc()
    //            是 textContent→innerHTML，只跳脫 & < >，**不跳脫雙引號**，所以
    //            data-email="…" 這種屬性位置仍可被跳出並注入事件處理器。現已改名為
    //            escText() 並補上引號跳脫，但這條回歸仍要留著——正解是不拼字串、
    //            用 textContent 建 DOM，而這條驗的正是那件事沒有退化。
    for (const [label, evilEmail] of [
      ['標籤注入', '"><img src=x onerror=window.__xss=1>@example.invalid'],
      ['屬性注入', 'a" onmouseover="window.__xss=1" data-x="@example.invalid'],
    ]) {
      await page.evaluate(() => { delete window.__xss; });
      await page.route('**/api/admin/readers?*', (route) => route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify([{ email: evilEmail, tier: 'VIP', vipActive: true,
          vipExpiresAt: null, credits: 0, referralCode: 'x', lastLoginAt: null }]),
      }));
      await page.fill('#reader-q', 'xss');
      await page.click('#reader-search-btn');
      await page.waitForFunction(
        (e) => (document.querySelector('#reader-table tbody tr td')?.textContent || '').includes(e),
        evilEmail, { timeout: 15000 });
      // 滑過「取消 VIP」按鈕：屬性注入的事件處理器會在這一刻觸發
      const btn = page.locator('#reader-table tbody tr button');
      if (await btn.count()) await btn.first().hover();
      ok(await page.evaluate(() => window.__xss === undefined), `惡意 email（${label}）未被執行`);
      ok(await page.locator('#reader-table tbody tr td img').count() === 0,
        `惡意 email（${label}）未產生 img 節點`);
      await page.unroute('**/api/admin/readers?*');
    }

    // 參數設定分頁：欄位應由 /api/admin/settings 動態產生
    await page.click('#tab-settings');
    await page.waitForFunction(
      () => document.querySelectorAll('#settings-fields input[data-key]').length > 0, null, { timeout: 15000 });
    const keys = await page.locator('#settings-fields input[data-key]').count();
    ok(keys >= 4, `參數設定欄位數 ${keys} >= 4`);
    // 每個欄位的 min/max 都必須有值，而且是後端回應帶來的（前端不再自寫一份數字）。
    // 少了這條，「後端欄位改名 → 前端 view.max 變 undefined → max 靜默消失」不會被發現。
    const bounds = await page.evaluate(() =>
      [...document.querySelectorAll('#settings-fields input[data-key]')]
        .map(i => ({ key: i.dataset.key, min: i.min, max: i.max })));
    for (const b of bounds) {
      ok(b.min !== '' && b.max !== '' && Number(b.max) > Number(b.min),
        `${b.key} 的輸入欄位帶有允許區間 ${b.min}–${b.max}`);
    }
    await page.close();
  } finally {
    await browser.close();
  }
}

let originalPremiumCost = null;
/**
 * 是否真的執行過 [4] 批次加點。
 * finally 的補償加點原本無條件執行：try 區塊若在 [4] 之前就拋錯（例如服務中途重啟），
 * 補償仍然扣 7 點，每一次失敗執行都讓測試讀者的餘額往下漂 7 點。
 */
let creditsGranted = false;
try {
  // ---- 7. 未授權必須被擋（先做：避免後面任何步驟意外用到無金鑰路徑） ----
  console.log('\n[0] 未帶金鑰的後台端點');
  eq((await api('/api/admin/readers?q=a', {}, true)).status, 401, '搜尋讀者未帶金鑰');
  eq((await api('/api/admin/settings', {}, true)).status, 401, '參數設定未帶金鑰');

  // ---- 2. 授予 VIP（對尚未登入過的 email 會自動建立帳戶） ----
  console.log('\n[1] 授予 VIP');
  const granted = await api('/api/admin/readers/vip', {
    method: 'POST', body: JSON.stringify({ email: T1, days: 30 }),
  });
  eq(granted.status, 200, '授予 VIP 回應碼');
  eq(granted.body.vipActive, true, '授予後 vipActive');

  // ---- 1. 搜尋讀者結構 ----
  console.log('\n[2] 搜尋讀者');
  const search = await api('/api/admin/readers?q=' + encodeURIComponent('verify-admin-reader'));
  eq(search.status, 200, '搜尋回應碼');
  ok(Array.isArray(search.body), '搜尋回傳陣列');
  const found = search.body.find((r) => r.email === T1);
  ok(!!found, `搜尋結果包含 ${T1}`);
  if (found) {
    ['email', 'tier', 'vipActive', 'vipExpiresAt', 'credits', 'lastLoginAt'].forEach(
      (f) => ok(f in found, `讀者摘要含欄位 ${f}`));
    eq(found.vipActive, true, '搜尋結果 vipActive');
    // 摘要絕不可夾帶登入憑證類欄位
    ok(!('sessionToken' in found) && !('loginToken' in found), '讀者摘要不含登入憑證欄位');
  }

  // ---- 3. 取消 VIP ----
  console.log('\n[3] 取消 VIP');
  const revoked = await api('/api/admin/readers/vip?email=' + encodeURIComponent(T1), { method: 'DELETE' });
  eq(revoked.status, 200, '取消 VIP 回應碼');
  eq(revoked.body.vipActive, false, '取消後 vipActive');
  eq(revoked.body.vipExpiresAt, null, '取消後 vipExpiresAt 必須清空');

  // ---- 4. 批次加點 2 筆 ----
  console.log('\n[4] 批次加點');
  // T2 尚未有帳戶時加點會失敗（加點不建帳戶），先用授予 VIP 建好再立刻取消
  await api('/api/admin/readers/vip', { method: 'POST', body: JSON.stringify({ email: T2, days: 1 }) });
  await api('/api/admin/readers/vip?email=' + encodeURIComponent(T2), { method: 'DELETE' });
  const grantNote = 'verify-admin-reader 驗證腳本';
  const grant = await api('/api/admin/readers/credits', {
    method: 'POST', body: JSON.stringify({ emails: [T1, T2], delta: GRANT_DELTA, note: grantNote }),
  });
  eq(grant.status, 200, '批次加點回應碼');
  // 只要端點回了 200，帳本就可能已經寫入，補償就必須執行（即使 granted < 2）
  if (grant.status === 200) creditsGranted = true;
  eq(grant.body.granted, 2, '成功筆數');
  eq(grant.body.failed, 0, '失敗筆數');

  const ledger = await api('/api/admin/readers/ledger?email=' + encodeURIComponent(T1));
  eq(ledger.status, 200, '帳本查詢回應碼');
  const adminGrants = ledger.body.filter((t) => t.reason === 'ADMIN_GRANT' && t.note === grantNote);
  ok(adminGrants.length >= 1, `帳本查得到 ADMIN_GRANT（本次 note 共 ${adminGrants.length} 筆）`);

  // ---- 5. 加點缺 note → 400 ----
  console.log('\n[5] 加點缺 note');
  const noNote = await api('/api/admin/readers/credits', {
    method: 'POST', body: JSON.stringify({ emails: [T1], delta: 1 }),
  });
  eq(noNote.status, 400, '缺 note 應回 400');
  const zeroDelta = await api('/api/admin/readers/credits', {
    method: 'POST', body: JSON.stringify({ emails: [T1], delta: 0, note: 'x' }),
  });
  eq(zeroDelta.status, 400, 'delta 為 0 應回 400');

  // ---- 6. 參數改動要真的傳到讀者端頁面（spec §5.11） ----
  console.log('\n[6] 參數設定 → /r/rules 動態注入');
  const before = await api('/api/admin/settings');
  eq(before.status, 200, '讀取參數回應碼');
  // 每個鍵回 {value,min,max}：界限只在後端定義一份，後台頁面拿它設 input 的 min/max
  originalPremiumCost = before.body['credit.premium_cost'].value;
  ok(originalPremiumCost != null, `原始 credit.premium_cost = ${originalPremiumCost}`);
  // 界限必須真的隨值回傳，否則後台的 max 會靜默消失（欄位缺漏時前端不填假值）
  ok(before.body['credit.premium_cost'].max > before.body['credit.premium_cost'].min,
    `credit.premium_cost 允許區間 ${before.body['credit.premium_cost'].min}`
      + `–${before.body['credit.premium_cost'].max}`);

  const put = await api('/api/admin/settings', {
    method: 'PUT', body: JSON.stringify({ 'credit.premium_cost': '20' }),
  });
  eq(put.status, 200, '寫入參數回應碼');
  eq(put.body['credit.premium_cost'].value, 20, '寫入後讀回的值');

  // 上限外的值必須被擋（沒有上限時 signup_grant 可被設成 21 億，
  // 而點數不過期、規則調整也不回收，事後清不乾淨）
  const tooBig = await api('/api/admin/settings', {
    method: 'PUT', body: JSON.stringify({ 'credit.signup_grant': '2147483647' }),
  });
  eq(tooBig.status, 400, '超過上限的參數應回 400');

  // 關鍵斷言：後台改的數字必須立刻出現在讀者看到的頁面上（不是等 60 秒快取過期）
  const rulesHtml = await (await fetch(BASE + '/r/rules')).text();
  has(rulesHtml, '進階文章每篇 20 點', '/r/rules 顯示新的解鎖點數');

  // 白名單以外的鍵必須被拒（否則這支端點等於任意 key-value 寫入口）
  const badKey = await api('/api/admin/settings', {
    method: 'PUT', body: JSON.stringify({ 'evil.key': '1' }),
  });
  eq(badKey.status, 400, '非白名單參數應回 400');
} catch (e) {
  fail(e.stack || e.message);
} finally {
  // ---- 還原：參數改回原值、VIP 取消、加點以負數補償 ----
  try {
    if (originalPremiumCost != null) {
      await api('/api/admin/settings', {
        method: 'PUT', body: JSON.stringify({ 'credit.premium_cost': String(originalPremiumCost) }),
      });
      const restored = await (await fetch(BASE + '/r/rules')).text();
      has(restored, `進階文章每篇 ${originalPremiumCost} 點`, '/r/rules 已還原為原始點數');
    }
    // 還原步驟的回傳值一律檢查並計入 failures。
    // api() 對非 2xx 不拋錯（回 {status, body}），先前這裡完全不看回傳值就直接印
    //「已還原」——補償加點若回 granted:0/failed:2，腳本照樣 exit 0，
    // 而下一次執行的起始餘額已經偏移。假通過比沒有還原更糟：它讓偏移無聲累積。
    for (const email of [T1, T2]) {
      const del = await api('/api/admin/readers/vip?email=' + encodeURIComponent(email), { method: 'DELETE' });
      // 404 代表這位讀者本來就沒有帳戶（前面的步驟沒跑到），同樣是「已無 VIP」的合格終態
      ok([200, 404].includes(del.status), `還原：取消 VIP 回應碼 ${del.status}（接受 200/404）`);
    }
    // 帳本只增不改，還原餘額靠補償性負數加點而非刪除歷史列
    if (creditsGranted) {
      const back = await api('/api/admin/readers/credits', {
        method: 'POST',
        body: JSON.stringify({ emails: [T1, T2], delta: -GRANT_DELTA, note: 'verify-admin-reader 還原' }),
      });
      eq(back.status, 200, '還原：補償加點回應碼');
      eq(back.body && back.body.granted, 2, '還原：補償加點成功筆數');
      eq(back.body && back.body.failed, 0, '還原：補償加點失敗筆數');
    } else {
      console.log('OK   還原：本次未執行批次加點，略過補償（避免餘額往下漂）');
    }
    console.log('OK   測試資料已還原（VIP 取消、點數補償歸零；帳本列依設計保留）');
  } catch (e) {
    fail('還原測試資料失敗：' + (e.stack || e.message));
  }
}

// 瀏覽器模式放在最後：前面已建好測試資料，UI 才有東西可以顯示
if (USE_BROWSER) {
  console.log('\n[7] 瀏覽器操作 admin.html');
  try {
    await verifyBrowser();
  } catch (e) {
    // 明確要求 --browser 卻跑不起來一律算失敗，不可只印警告就當成功
    fail('瀏覽器驗證失敗：' + (e.stack || e.message));
  }
}

console.log(failed === 0 ? '\n全部通過 ✅（未實際寄信）' : `\n有 ${failed} 項失敗 ❌`);
process.exitCode = failed === 0 ? 0 : 1;
