// 原始資料表「逐列寄券」失敗訊息可見性驗證腳本
//
// 背景：使用者回報原始資料表的「寄券」按鈕失效——按下「確認寄出」後畫面毫無反應。
// 本腳本重現寄不出去的各種情境，檢查 #coupon-send-dialog-msg 的訊息「文字有寫入」
// 而且「畫面上看得到」（computed display 不是 none）：
//   1. 後端回 400（單筆寄送資格不符，例如對方已退訂；修正前則是「不在本活動命中名單內」）
//      ——修正前這類錯誤訊息被 .msg{display:none} 藏起來，按鈕看起來像失效
//   2. 後端回 200 但 sent=0（例如 skipped／failed）
//   3. 所有優惠券都已過期，開啟視窗時應提示「沒有可寄送的未過期優惠券」
//   4. 下拉的券全部已寄過（option 全部停用），按「確認寄出」不能靜默無反應
//   5. 上一次的錯誤不能殘留：出錯後關閉視窗再開，訊息框要清空並隱藏
//
// 全程不需要啟動後端：admin.html 直接讀本機檔案、以 page.route() 回應，
// /api/admin/** 也全部攔截餵假資料，不觸碰任何真實資料、不會真的寄信。
//
// 用法：
//   node survey-backend/scripts/verify-raw-row-coupon-errors.mjs

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

/** 假站台來源：所有請求都被 route 攔截，不會真的連線 */
const BASE = 'http://admin.test';
/** 金鑰模式登入用的假金鑰（API 全被攔截，值不重要） */
const ADMIN_KEY = 'dev-admin-key';
/** 本機 admin.html 路徑 */
const ADMIN_HTML = join(dirname(fileURLToPath(import.meta.url)), '..', 'src', 'main', 'resources', 'static', 'admin.html');

/** 動態載入 playwright：先試專案內，再找常見全域安裝目錄（慣例同 verify-raw-row-coupon.mjs） */
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

const pw = await loadPlaywright();
const { chromium } = pw.default ?? pw;

let failed = 0;
/** 記錄一項結果（失敗不中斷，一次跑完看到所有問題） */
const ok = (cond, label) => {
  if (cond) console.log(`OK   ${label}`);
  else { console.error(`FAIL ${label}`); failed++; }
};

// ---- 假資料 ----
/** 一列原始問卷資料（來源為 Dify 匯入，模擬不在命中名單的人） */
const SURVEY_ROWS = [{
  id: 1, createdAt: '2026-08-01T09:00:00+08:00', email: 'someone@example.com', name: '甲君',
  role: '其他', experience: '', frontendExperience: '', budget: '4000 以下',
  interest: ['RAG 知識庫'], answers: {}, utm: {}, source: 'dify', consent: true, unsubscribed: false,
}];
/** 未過期的優惠券活動 */
const LIVE_CAMPAIGN = { id: 1, courseName: 'AI 賦能全端開發', pitch: 'p', courseUrl: 'https://example.invalid', couponCode: 'CPN-ONE', expiresAt: '2099-01-01', formKey: 'survey', status: 'DRAFT', sentCount: 0, createdAt: '2026-08-01T00:00:00+08:00' };
/** 已過期的優惠券活動 */
const EXPIRED_CAMPAIGN = { ...LIVE_CAMPAIGN, id: 2, couponCode: 'CPN-OLD', expiresAt: '2020-01-01' };

/**
 * 開一個已用金鑰登入的頁面。
 * @param browser   playwright browser
 * @param campaigns /api/admin/coupons 要回的活動清單
 * @param sendReply /api/admin/coupons/{id}/send 的回應 { status, body }
 * @param sentMap   /api/admin/coupons/sent-map 要回的已寄總覽（email → 活動 id 陣列）
 */
async function openPage(browser, campaigns, sendReply, sentMap = {}) {
  const page = await browser.newPage();
  const html = readFileSync(ADMIN_HTML, 'utf8');
  await page.route('**/*', (route) => {
    const req = route.request();
    const url = new URL(req.url());
    const json = (status, body) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    if (url.pathname === '/admin.html') return route.fulfill({ status: 200, contentType: 'text/html; charset=utf-8', body: html });
    if (url.pathname === '/api/admin/me') return json(401, {});  // 強制走金鑰登入閘門
    if (url.pathname === '/api/admin/survey') return json(200, SURVEY_ROWS);
    if (url.pathname === '/api/admin/coupons' && req.method() === 'GET') return json(200, campaigns);
    if (url.pathname === '/api/admin/coupons/sent-map') return json(200, sentMap);
    if (/^\/api\/admin\/coupons\/\d+\/send$/.test(url.pathname)) return json(sendReply.status, sendReply.body);
    if (url.pathname.startsWith('/api/')) return json(200, []);  // 其餘初始化 API 安靜通過
    return route.fulfill({ status: 404, body: '' });
  });
  await page.goto(`${BASE}/admin.html`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#gate', { state: 'visible' });
  await page.click('#gate-use-key');
  await page.fill('#gate-key', ADMIN_KEY);
  await page.click('#gate-btn');
  await page.waitForSelector('#app:not([hidden])', { timeout: 15000 });
  await page.waitForSelector('#raw-table tbody tr button:has-text("寄券")', { timeout: 15000 });
  return page;
}

/** 讀取對話框訊息框的文字與實際顯示狀態 */
async function readDialogMsg(page) {
  return page.$eval('#coupon-send-dialog-msg', (el) => ({
    text: el.textContent,
    className: el.className,
    display: getComputedStyle(el).display,
    visible: el.offsetWidth > 0 && el.offsetHeight > 0,
  }));
}

const browser = await chromium.launch();
try {
  // ---- 情境 1：後端 400（單筆寄送資格不符）----
  {
    const reason = '收件人已退訂，不寄送行銷信：someone@example.com';
    const page = await openPage(browser, [LIVE_CAMPAIGN], { status: 400, body: { status: 400, message: reason, detail: reason } });
    await page.click('#raw-table tbody tr button:has-text("寄券")');
    await page.waitForSelector('#coupon-send-dialog[open]');
    await page.click('#coupon-send-dialog-confirm');
    await page.waitForFunction(() => document.querySelector('#coupon-send-dialog-msg').textContent.length > 0, null, { timeout: 5000 });
    const msg = await readDialogMsg(page);
    console.log('     情境 1 訊息框：', JSON.stringify(msg));
    ok(msg.text.includes('寄送失敗'), '情境 1：400 時訊息文字有寫入');
    ok(msg.visible, '情境 1：400 時訊息畫面上看得到');
    await page.close();
  }

  // ---- 情境 2：後端 200 但 sent=0 ----
  {
    const page = await openPage(browser, [LIVE_CAMPAIGN], { status: 200, body: { attempted: 1, sent: 0, skipped: 0, failed: 1, remaining: 0 } });
    await page.click('#raw-table tbody tr button:has-text("寄券")');
    await page.waitForSelector('#coupon-send-dialog[open]');
    await page.click('#coupon-send-dialog-confirm');
    await page.waitForFunction(() => document.querySelector('#coupon-send-dialog-msg').textContent.length > 0, null, { timeout: 5000 });
    const msg = await readDialogMsg(page);
    console.log('     情境 2 訊息框：', JSON.stringify(msg));
    ok(msg.text.includes('未寄出'), '情境 2：sent=0 時訊息文字有寫入');
    ok(msg.visible, '情境 2：sent=0 時訊息畫面上看得到');
    await page.close();
  }

  // ---- 情境 3：所有券都已過期 ----
  {
    const page = await openPage(browser, [EXPIRED_CAMPAIGN], { status: 200, body: {} });
    await page.click('#raw-table tbody tr button:has-text("寄券")');
    await page.waitForSelector('#coupon-send-dialog[open]');
    const msg = await readDialogMsg(page);
    console.log('     情境 3 訊息框：', JSON.stringify(msg));
    ok(msg.text.includes('沒有可寄送'), '情境 3：無可寄券時提示文字有寫入');
    ok(msg.visible, '情境 3：無可寄券時提示畫面上看得到');
    await page.close();
  }

  // ---- 情境 4：唯一的券已寄過（option 停用），按確認寄出 ----
  {
    const page = await openPage(browser, [LIVE_CAMPAIGN], { status: 200, body: {} }, { 'someone@example.com': [LIVE_CAMPAIGN.id] });
    await page.click('#raw-table tbody tr button:has-text("寄券")');
    await page.waitForSelector('#coupon-send-dialog[open]');
    await page.click('#coupon-send-dialog-confirm');
    await page.waitForTimeout(300);  // 給點擊處理一點時間；靜默 return 時不會有任何 DOM 變化可等
    const msg = await readDialogMsg(page);
    console.log('     情境 4 訊息框：', JSON.stringify(msg));
    ok(msg.text.length > 0, '情境 4：沒有可選的券時按確認寄出有提示文字');
    ok(msg.visible, '情境 4：沒有可選的券時提示畫面上看得到');
    await page.close();
  }

  // ---- 情境 5：出錯後關閉再開，訊息框要清空 ----
  {
    const reason = '收件人未同意接收行銷信，不寄送：someone@example.com';
    const page = await openPage(browser, [LIVE_CAMPAIGN], { status: 400, body: { status: 400, message: reason, detail: reason } });
    await page.click('#raw-table tbody tr button:has-text("寄券")');
    await page.waitForSelector('#coupon-send-dialog[open]');
    await page.click('#coupon-send-dialog-confirm');
    await page.waitForFunction(() => document.querySelector('#coupon-send-dialog-msg').textContent.length > 0, null, { timeout: 5000 });
    await page.click('#coupon-send-dialog-cancel');
    await page.click('#raw-table tbody tr button:has-text("寄券")');
    await page.waitForSelector('#coupon-send-dialog[open]');
    const msg = await readDialogMsg(page);
    console.log('     情境 5 訊息框：', JSON.stringify(msg));
    ok(msg.text === '' && !msg.visible, '情境 5：重新開啟視窗時不殘留上一次的錯誤訊息');
    await page.close();
  }

  console.log(failed === 0 ? '\n全部通過 ✅' : `\n共 ${failed} 項失敗 ❌`);
  process.exitCode = failed === 0 ? 0 : 1;
} catch (e) {
  console.error('FAIL:', e.message, e.stack);
  process.exitCode = 1;
} finally {
  await browser.close();
}
