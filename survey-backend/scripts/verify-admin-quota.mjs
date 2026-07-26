// 後台「動態寄信額度」UI 驗證腳本（離線、不需資料庫、不實際寄信）
//
// 用途：驗證 admin.html 依 /api/admin/mail-quota 的回應動態調整
//       ①「單次上限」欄位的 max 與說明文字 ②額度說明列 ③超量警告門檻與文案，
//       取代過去寫死的「每日 100 封」。
//
// 做法：用 node 內建 http 起一個靜態站台直接吐 src/main/resources/static/admin.html，
//       所有 /api/admin/** 請求都用 Playwright route 攔截並回假資料，
//       因此完全不需要啟動 Spring Boot 或 Postgres，可隨時重跑。
//
// 用法：node survey-backend/scripts/verify-admin-quota.mjs
// 需求：playwright（本地或全域安裝皆可；首次會自動下載 chromium）

import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { dirname, join } from 'node:path';

/**
 * 載入 playwright：優先用專案內安裝，找不到時退回全域安裝目錄。
 * ESM 的模組解析以「腳本所在目錄」為基準，而 survey-backend 沒有 node_modules，
 * 因此必須顯式列出全域安裝路徑，否則此腳本無法在本專案直接重跑。
 */
async function loadPlaywright() {
  try {
    return await import('playwright');
  } catch {
    const candidates = [
      process.env.APPDATA && join(process.env.APPDATA, 'npm', 'node_modules'), // Windows 全域
      '/usr/local/lib/node_modules',
      '/usr/lib/node_modules',
    ].filter(Boolean);
    for (const root of candidates) {
      const entry = join(root, 'playwright', 'index.js');
      if (existsSync(entry)) return await import(pathToFileURL(entry).href);
    }
    throw new Error('找不到 playwright，請執行 npm i -g playwright 或在本目錄安裝');
  }
}
const playwright = await loadPlaywright();
// 全域安裝走 CJS 匯入時具名匯出可能落在 default 上，兩種都容忍
const chromium = playwright.chromium ?? playwright.default?.chromium;

const STATIC_DIR = join(dirname(fileURLToPath(import.meta.url)), '..', 'src', 'main', 'resources', 'static');

let failed = 0;
/** 記錄一項失敗（不中斷後續案例，讓一次執行就看到所有問題） */
const fail = (msg) => { console.error('FAIL:', msg); failed++; };
/** 斷言相等 */
const eq = (actual, expected, label) => {
  if (String(actual) !== String(expected)) fail(`${label}：預期 ${expected}，實際 ${actual}`);
  else console.log(`OK   ${label} = ${actual}`);
};
/** 斷言字串包含 */
const has = (actual, needle, label) => {
  if (!String(actual).includes(needle)) fail(`${label}：預期包含「${needle}」，實際「${actual}」`);
  else console.log(`OK   ${label} 含「${needle}」`);
};

/**
 * 組一份 mail-quota 假回應（欄位與 MailQuotaService.Quota 一致）。
 *
 * reserve / marketingRemaining 未指定時自動由 remaining 推導，
 * 讓各案例只要覆寫 remaining 就能維持三個數字的一致關係
 * （後端的 marketingRemaining = max(0, remaining - reserve)）。
 */
function quota(overrides = {}) {
  const base = {
    source: 'zeabur', status: 'healthy',
    dailyQuota: 999999999, dailySent: 0, dailyRemaining: 999999999,
    monthlyQuota: 50000, monthlySent: 1200, monthlyRemaining: 48800,
    remaining: 48800, batchMax: 500,
    reserve: 50,
    overageBillingEnabled: true,
    quotaResetAt: '2026-07-26T00:00:00Z', monthlyResetAt: '2026-07-28T16:18:35Z',
    ...overrides,
  };
  if (base.marketingRemaining == null) base.marketingRemaining = Math.max(0, base.remaining - base.reserve);
  if (base.marketingBatchMax == null) base.marketingBatchMax = Math.min(base.marketingRemaining, base.batchMax);
  return base;
}

// 只服務 admin.html 的極簡靜態站台（其他路徑交給 Playwright route 攔截）
const server = createServer(async (req, res) => {
  try {
    const html = await readFile(join(STATIC_DIR, 'admin.html'), 'utf8');
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(html);
  } catch (e) {
    res.writeHead(500).end(e.message);
  }
});
await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
const BASE = `http://127.0.0.1:${server.address().port}`;

const browser = await chromium.launch();

/**
 * 開一個已通過金鑰閘門的後台頁面，並以指定的假額度攔截所有 admin API。
 * @param {object} q            mail-quota 的假回應
 * @param {number} count        recipients 的假收件人數
 * @param {object} [sendResult] campaign/send 的假回應（不給則不會用到）
 */
async function openAdmin(q, count, sendResult) {
  const page = await browser.newPage();
  // 預先塞金鑰，讓 api() 帶得出 X-Admin-Key（實際驗證由 route 假造成功）
  await page.addInitScript(() => sessionStorage.setItem('survey_admin_key', 'verify-key'));
  await page.route('**/api/admin/**', async (route) => {
    const url = route.request().url();
    const json = (body) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    if (url.includes('/mail-quota')) return json(q);
    if (url.includes('/campaign/send')) return json(sendResult || {});
    if (url.includes('/recipients')) return json({ count, sample: [] });
    if (url.includes('/api/admin/survey')) return json([]);
    if (url.includes('/api/admin/invites')) return json({ invitedCount: 0, remindedCount: 0, confirmedCount: 0, pendingCount: 0, logs: [] });
    if (url.includes('/api/admin/campaigns')) return json([]);
    if (url.includes('/api/admin/templates/invite')) return json({ subject: 's', bodyHtml: '{{confirmLink}}' });
    return json({});
  });
  // sessionStorage 已有金鑰時，頁面載入即自行驗證並進入主畫面（不需操作金鑰閘門）
  await page.goto(`${BASE}/admin.html`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#app', { state: 'visible', timeout: 15000 });
  // 額度說明與警告都在「電子報」頁籤內，切過去才看得到（預設頁籤為問卷分析）
  await page.click('#tab-campaign');
  // 等額度說明列被 renderQuota() 覆寫（初始文字為「額度偵測中…」），以及收件數載入完成
  await page.waitForFunction(() => !document.querySelector('#quota-info').textContent.includes('偵測中'), null, { timeout: 15000 });
  await page.waitForFunction(() => /^\d+$/.test(document.querySelector('#rcount').textContent), null, { timeout: 15000 });
  return page;
}

try {
  // 案例 1：偵測成功（Pro 方案）→ 上限與說明皆依實際額度，收件數未超量時不警告
  console.log('\n[1] Zeabur 偵測成功，收件數未超量');
  let page = await openAdmin(quota(), 10);
  eq(await page.locator('#invite-limit').getAttribute('max'), 500, '單次上限欄位 max');
  has(await page.locator('#invite-limit-label').textContent(), '剩餘 48800 封', '單次上限標籤');
  has(await page.locator('#invite-limit-label').textContent(), '單批上限 500', '單次上限標籤');
  has(await page.locator('#quota-info').textContent(), '本期額度 50000 封', '額度說明列');
  has(await page.locator('#quota-info').textContent(), '剩餘 48800', '額度說明列');
  // 三個數字的關係必須一起看得到：總剩餘 / 保留給交易信 / 行銷可用
  has(await page.locator('#quota-info').textContent(), '保留 50 封給登入信等交易信', '額度說明列（保留額度）');
  has(await page.locator('#quota-info').textContent(), '行銷可用 48750 封', '額度說明列（行銷可用）');
  eq(await page.locator('#quota-warn').isVisible(), false, '超量警告（未超量時隱藏）');
  await page.close();

  // 案例 2：收件數超過行銷可用額度 → 顯示警告，說明有幾人不會收到
  console.log('\n[2] 收件數超過行銷可用額度');
  page = await openAdmin(quota({ monthlyRemaining: 500, remaining: 500 }), 1200);
  eq(await page.locator('#quota-warn').isVisible(), true, '超量警告顯示');
  has(await page.locator('#quota-warn').textContent(), '超過行銷可用額度 450 封', '超量警告文案');
  has(await page.locator('#quota-warn').textContent(), '其餘 750 人本次不會收到', '超量警告文案（被略過人數）');
  await page.close();

  // 案例 3：**回歸測試**——收件數落在「總剩餘」與「行銷可用」之間時仍必須警告。
  // 這正是「頁面說 A、實際做 B」的失效情境：remaining=260、reserve=50 → 行銷可用 210，
  // 收件 250 人。若門檻誤用 remaining（250 <= 260）就完全不會有警告，
  // 後端卻靜默截斷到 210，管理者會以為 250 人都收到了。
  console.log('\n[3] 收件數介於行銷可用額度與總剩餘之間（門檻必須用 marketingRemaining）');
  page = await openAdmin(quota({ remaining: 260, monthlyRemaining: 260, batchMax: 260 }), 250);
  eq(await page.locator('#quota-warn').isVisible(), true, '超量警告顯示（門檻誤用 remaining 時此項會失敗）');
  has(await page.locator('#quota-warn').textContent(), '超過行銷可用額度 210 封', '超量警告文案');
  has(await page.locator('#quota-warn').textContent(), '其餘 40 人本次不會收到', '超量警告文案（被略過人數）');
  await page.close();

  // 案例 4：剩餘額度小於欄位預設值 50 → 欄位既有值應被下修，避免送出後才被後端截掉
  console.log('\n[4] 剩餘額度小於欄位預設值');
  page = await openAdmin(quota({ remaining: 30, monthlyRemaining: 30, batchMax: 30 }), 10);
  eq(await page.locator('#invite-limit').getAttribute('max'), 30, '單次上限欄位 max');
  eq(await page.locator('#invite-limit').inputValue(), 30, '單次上限欄位既有值（應被下修）');
  await page.close();

  // 案例 5：偵測不可用（未設 ZEABUR_API_TOKEN）→ 明確告知未偵測到，並以保守額度為上限
  console.log('\n[5] 未設定 token，退回保守額度');
  page = await openAdmin(quota({
    source: 'fallback', status: 'unknown',
    dailyQuota: 100, dailyRemaining: 100, monthlyQuota: 100, monthlySent: 0, monthlyRemaining: 100,
    remaining: 100, batchMax: 100, overageBillingEnabled: false,
    quotaResetAt: null, monthlyResetAt: null,
  }), 10);
  has(await page.locator('#quota-info').textContent(), '未偵測到 Zeabur 額度', '額度說明列（fallback）');
  has(await page.locator('#quota-info').textContent(), '暫以 100 封為上限', '額度說明列（fallback）');
  eq(await page.locator('#invite-limit').getAttribute('max'), 100, '單次上限欄位 max');
  await page.close();

  // 案例 6：帳號狀態異常 → 警告優先提示帳號狀態，而非只看額度數字
  console.log('\n[6] ZSend 帳號狀態異常');
  page = await openAdmin(quota({ status: 'suspended' }), 10);
  eq(await page.locator('#quota-warn').isVisible(), true, '帳號異常警告顯示');
  has(await page.locator('#quota-warn').textContent(), 'suspended', '帳號異常警告文案');
  await page.close();

  // 案例 7：**回歸測試**——後端靜默截斷時，送出前的確認框與送出後的結果訊息
  // 都必須明講「有幾個人不會收到、為什麼」。原本的成功訊息只顯示 recipientCount，
  // 管理者看到「已送出：收件 210、成功 210、失敗 0」會以為 250 人都收到了。
  console.log('\n[7] 送出後 skippedForQuota > 0 必須顯示原因');
  page = await openAdmin(
    quota({ remaining: 260, monthlyRemaining: 260, batchMax: 260 }), 250,
    { campaignId: 1, recipientCount: 210, accepted: 210, failed: 0, skippedForQuota: 40 });
  await page.fill('#subject', '額度驗證用主旨');
  let dialogText = '';
  page.on('dialog', async (d) => { dialogText = d.message(); await d.accept(); });
  await page.click('#send-btn');
  await page.waitForFunction(
    () => document.querySelector('#send-msg').textContent.includes('已送出'), null, { timeout: 15000 });
  has(dialogText, '行銷可用額度只剩 210 封', '確認框預告截斷');
  has(dialogText, '其中 40 人本次不會寄出', '確認框預告被略過人數');
  const sendMsg = await page.locator('#send-msg').textContent();
  has(sendMsg, '另有 40 人未寄出', '結果訊息說明未寄出人數');
  has(sendMsg, '保留 50 封交易信', '結果訊息說明原因（保留交易信額度）');
  eq(await page.locator('#send-msg').getAttribute('class'), 'msg err',
    '有人未寄出時不得只顯示綠色成功樣式');
  await page.close();

  console.log(failed === 0 ? '\n全部通過 ✅（離線驗證，未實際寄信）' : `\n有 ${failed} 項失敗 ❌`);
} catch (e) {
  fail(e.stack || e.message);
} finally {
  await browser.close();
  server.close();
  process.exitCode = failed === 0 ? 0 : 1;
}
