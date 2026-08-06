// 右上工具列（身分顯示／日夜切換／登出）與暗色主題端到端驗證腳本（Task 11）
//
// 驗證項目：
//   1. 工具列三個元素（#tb-identity / #tb-theme / #tb-logout）都存在且可見
//   2. 金鑰模式下 #tb-identity 顯示「金鑰模式」；JWT 模式（以 page.route() 模擬 session）
//      下顯示登入者 email
//   3. 點擊 #tb-theme 會把 documentElement 的 data-theme 屬性在 light/dark 間切換，
//      且用 getComputedStyle 讀 --bg 的「實際計算值」確實改變（不是只有屬性被設上去）
//   4. 主題偏好會寫入 localStorage（key: admin-theme），重新整理頁面後保持
//   5. 首次進站（清空 localStorage）會跟隨 prefers-color-scheme：emulateMedia 設
//      colorScheme:'dark' 後開新分頁，應直接套用 dark 主題
//   6. 登出行為：
//      - 金鑰模式：點 #tb-logout 後 sessionStorage 的金鑰被清除，且頁面重新整理回登入 gate
//      - JWT 模式（模擬）：點 #tb-logout 後必須實際呼叫過 POST /api/admin/logout，
//        且重新整理後（模擬後端已清 cookie）回到登入 gate
//
// 用法（後端須已在本機啟動；金鑰為 dev-admin-key，需以
// APP_ALLOW_INSECURE_DEV_SECRETS=true 啟動）：
//   ADMIN_BASE=http://127.0.0.1:8080 node survey-backend/scripts/verify-admin-toolbar-theme.mjs
//
// 絕對不要把 ADMIN_BASE 指向正式站——本腳本會清 localStorage／sessionStorage 並重整頁面，
// 且預設值刻意設為本機位址（比照 verify-admin-login-gate.mjs 的慣例）。
//
// 可重跑：只讀登入、切主題、登出，不寫入任何需要清理的後端狀態。

import { mkdir } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { pathToFileURL, fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const BASE = process.env.ADMIN_BASE || 'http://127.0.0.1:8080';
const ADMIN_KEY = process.env.ADMIN_API_KEY || 'dev-admin-key';
const OUTPUT_DIR = join(__dirname, 'output');

/**
 * 動態載入 playwright：慣例與 verify-admin.mjs / verify-admin-login-gate.mjs 一致，
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

/**
 * WCAG 2.1 對比度計算（Code Review Finding 2：把報告裡手算的對比數字改成可重跑的斷言）。
 * 解析 getComputedStyle 回傳的 rgb()/rgba() 字串 → sRGB 相對亮度 → 對比比值，
 * 公式與計算過程比照 W3C WCAG 2.1 SC 1.4.3 的定義（含 sRGB gamma 校正）。
 */
function parseRgb(str) {
  const found = (str || '').match(/rgba?\(([^)]+)\)/);
  if (!found) throw new Error(`無法解析顏色字串：${str}`);
  const [r, g, b] = found[1].split(',').slice(0, 3).map((v) => Number(v.trim()));
  return { r, g, b };
}
function relativeLuminance({ r, g, b }) {
  const linear = (c) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b);
}
function contrastRatio(rgbStrA, rgbStrB) {
  const lA = relativeLuminance(parseRgb(rgbStrA));
  const lB = relativeLuminance(parseRgb(rgbStrB));
  const lighter = Math.max(lA, lB);
  const darker = Math.min(lA, lB);
  return (lighter + 0.05) / (darker + 0.05);
}
/** 斷言一組「文字色 vs 底色」的實際對比比值達到門檻，並印出算出來的數字（可重跑、可交叉驗證）。 */
function okContrast(fgColor, bgColor, minRatio, label) {
  const ratio = contrastRatio(fgColor, bgColor);
  ok(ratio >= minRatio,
    `${label}：對比 ${ratio.toFixed(2)}:1（門檻 ${minRatio}:1，文字 ${fgColor} / 底色 ${bgColor}）`);
  return ratio;
}

/**
 * 確保頁面目前處於指定主題，冪等操作（Code Review Finding：過期變數導致的雙重切換）。
 *
 * 每次呼叫都即時從頁面讀取當前的 data-theme，不依賴呼叫端快取的舊變數——原本兩處
 * 各自用同一個「重新整理後讀到的 themeAfterReload」判斷是否要點擊，但點擊之後
 * 這個變數從未更新，若環境剛好是 light（例如另一台機器或 CI 的 prefers-color-scheme
 * 預設值不同），第一處會點成 dark，第二處讀到同一個過期的 'light' 又點一次變回
 * light——結果是「暗色截圖其實存到亮色畫面」且沒有任何斷言會發現。
 * 改成每次都重新讀取現況再決定是否要點擊，兩處（以及未來若再新增）都呼叫同一個
 * helper，就不會再犯同樣的錯。
 */
async function ensureTheme(page, theme) {
  const current = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
  if (current !== theme) {
    await page.click('#tb-theme');
    await page.waitForFunction((t) => document.documentElement.getAttribute('data-theme') === t, theme);
  }
}

/** 用金鑰模式登入到主畫面，回傳已登入的 page。 */
async function loginWithKey(browser) {
  const page = await browser.newPage();
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
  await mkdir(OUTPUT_DIR, { recursive: true });

  // ---- 1. 金鑰模式登入 → 工具列三元素存在且可見 ----
  const page = await loginWithKey(browser);
  ok(await page.locator('#tb-identity').isVisible(), '#tb-identity 存在且可見');
  ok(await page.locator('#tb-theme').isVisible(), '#tb-theme 存在且可見');
  ok(await page.locator('#tb-logout').isVisible(), '#tb-logout 存在且可見');

  // ---- 2. 金鑰模式：身分顯示應為「金鑰模式」 ----
  const identityKeyMode = await page.locator('#tb-identity').textContent();
  ok(identityKeyMode === '金鑰模式', `金鑰模式身分顯示為「金鑰模式」（實際：「${identityKeyMode}」）`);

  // ---- 3. 主題切換：切換屬性且實際計算樣式真的改變 ----
  const before = await page.evaluate(() => ({
    theme: document.documentElement.getAttribute('data-theme'),
    bg: getComputedStyle(document.documentElement).getPropertyValue('--bg').trim(),
    bodyBg: getComputedStyle(document.body).backgroundColor,
  }));
  await page.click('#tb-theme');
  const after = await page.evaluate(() => ({
    theme: document.documentElement.getAttribute('data-theme'),
    bg: getComputedStyle(document.documentElement).getPropertyValue('--bg').trim(),
    bodyBg: getComputedStyle(document.body).backgroundColor,
  }));
  ok(before.theme !== after.theme, `data-theme 屬性有切換（${before.theme} → ${after.theme}）`);
  ok(before.bg !== after.bg, `--bg 變數的計算值真的改變（${before.bg} → ${after.bg}）`);
  ok(before.bodyBg !== after.bodyBg,
    `body 實際渲染背景色真的改變（getComputedStyle，${before.bodyBg} → ${after.bodyBg}），非僅屬性被設上去`);
  console.log(`OK   切換後主題為：${after.theme}`);

  // 按鈕圖示應同步切換（☀ ↔ 🌙）
  const themeBtnIcon = await page.locator('#tb-theme').textContent();
  ok(after.theme === 'dark' ? themeBtnIcon === '🌙' : themeBtnIcon === '☀',
    `切換鈕圖示與目前主題一致（${after.theme} → 「${themeBtnIcon}」）`);

  // ---- 4. 偏好寫入 localStorage，重新整理後保持 ----
  const storedTheme = await page.evaluate(() => localStorage.getItem('admin-theme'));
  ok(storedTheme === after.theme, `localStorage.admin-theme 已寫入且與目前主題一致（${storedTheme}）`);
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#app:not([hidden])', { timeout: 15000 });
  const themeAfterReload = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
  ok(themeAfterReload === after.theme, `重新整理後主題保持不變（${themeAfterReload}）`);

  // ---- 4.5 暗色模式對比度斷言（Code Review Finding 2）：
  //      改成實際計算，而非報告裡的手算敘述；不足 4.5:1 者算失敗，不因「大字例外」放水
  //      （.tab 是 16px／font-weight:750，不符合 WCAG 大字門檻 ≥18.66px+bold，故一律套用 4.5:1）。
  await ensureTheme(page, 'dark');
  await page.click('#tab-campaign');
  await page.waitForSelector('#campaign-view:not([hidden])', { timeout: 10000 });
  const contrastSamples = await page.evaluate(() => {
    const pairOf = (el) => {
      const s = getComputedStyle(el);
      return { color: s.color, bg: s.backgroundColor };
    };
    const result = {};
    const tabActive = document.querySelector('.tab.active');
    if (tabActive) result.tabActive = pairOf(tabActive);
    const count = document.querySelector('.count');
    if (count) {
      const card = count.closest('.card') || document.body;
      result.count = { color: getComputedStyle(count).color, bg: getComputedStyle(card).backgroundColor };
    }
    const ghostBtn = document.querySelector('#tb-theme');
    if (ghostBtn) result.ghostBtn = pairOf(ghostBtn);
    const th = document.querySelector('th');
    if (th) result.th = pairOf(th);
    const input = document.querySelector('#subject');
    if (input) result.input = pairOf(input);
    return result;
  });
  await page.click('#tab-analytics');
  await page.waitForSelector('#analytics-view:not([hidden])', { timeout: 10000 });

  if (contrastSamples.tabActive) {
    okContrast(contrastSamples.tabActive.color, contrastSamples.tabActive.bg, 4.5,
      '暗色模式 .tab.active 文字對其底色');
  } else fail('找不到 .tab.active 元素，無法驗證分頁籤對比度');
  if (contrastSamples.count) {
    okContrast(contrastSamples.count.color, contrastSamples.count.bg, 4.5,
      '暗色模式 .count 強調文字對卡片底色');
  } else fail('找不到 .count 元素，無法驗證強調文字對比度');
  if (contrastSamples.ghostBtn) {
    okContrast(contrastSamples.ghostBtn.color, contrastSamples.ghostBtn.bg, 4.5,
      '暗色模式 .btn.ghost（工具列日夜切換鈕）文字對其底色');
  } else fail('找不到 .btn.ghost 元素，無法驗證按鈕對比度');
  if (contrastSamples.th) {
    okContrast(contrastSamples.th.color, contrastSamples.th.bg, 4.5,
      '暗色模式表格欄名（th）文字對其底色');
  } else fail('找不到 th 元素，無法驗證表格欄名對比度');
  if (contrastSamples.input) {
    okContrast(contrastSamples.input.color, contrastSamples.input.bg, 4.5,
      '暗色模式輸入欄位文字對其底色');
  } else fail('找不到輸入欄位元素，無法驗證表單對比度');

  // 截圖前確保停在暗色（用 ensureTheme 即時讀取現況，不沿用任何快取變數），
  // 並在存檔前再斷言一次實際 data-theme，讓「存錯截圖」變成會失敗的測試，而不是無聲錯誤。
  await ensureTheme(page, 'dark');
  const themeBeforeDarkShot = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
  ok(themeBeforeDarkShot === 'dark', `暗色截圖存檔前，data-theme 確實為 dark（實際：${themeBeforeDarkShot}）`);
  await page.screenshot({ path: join(OUTPUT_DIR, 'admin-theme-dark.png'), fullPage: true });
  console.log('OK   已截暗色模式全頁圖：scripts/output/admin-theme-dark.png');

  // 切回亮色再截一張
  await ensureTheme(page, 'light');
  const themeBeforeLightShot = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
  ok(themeBeforeLightShot === 'light', `亮色截圖存檔前，data-theme 確實為 light（實際：${themeBeforeLightShot}）`);
  await page.screenshot({ path: join(OUTPUT_DIR, 'admin-theme-light.png'), fullPage: true });
  console.log('OK   已截亮色模式全頁圖：scripts/output/admin-theme-light.png');

  // ---- 5. 金鑰模式登出：sessionStorage 被清、頁面回到登入 gate ----
  await page.click('#tb-logout');
  await page.waitForSelector('#gate', { state: 'visible', timeout: 15000 });
  const keyAfterLogout = await page.evaluate(() => sessionStorage.getItem('survey_admin_key'));
  ok(keyAfterLogout === null, '金鑰模式登出後 sessionStorage 的金鑰已被清除');
  ok(!(await page.locator('#app').isVisible()), '金鑰模式登出後主畫面不再顯示，回到登入 gate');
  await page.close();

  // ---- 6. 首次進站（清空 localStorage）跟隨系統深色偏好 ----
  {
    const darkContext = await browser.newContext({ colorScheme: 'dark' });
    const darkPage = await darkContext.newPage();
    await darkPage.addInitScript(() => localStorage.removeItem('admin-theme'));
    await darkPage.goto(`${BASE}/admin.html`, { waitUntil: 'domcontentloaded' });
    const initialTheme = await darkPage.evaluate(() => document.documentElement.getAttribute('data-theme'));
    ok(initialTheme === 'dark', `清空 localStorage 且系統為深色時，首次進站跟隨系統套用 dark（實際：${initialTheme}）`);
    await darkContext.close();
  }
  {
    const lightContext = await browser.newContext({ colorScheme: 'light' });
    const lightPage = await lightContext.newPage();
    await lightPage.addInitScript(() => localStorage.removeItem('admin-theme'));
    await lightPage.goto(`${BASE}/admin.html`, { waitUntil: 'domcontentloaded' });
    const initialTheme = await lightPage.evaluate(() => document.documentElement.getAttribute('data-theme'));
    ok(initialTheme === 'light', `清空 localStorage 且系統為淺色時，首次進站跟隨系統套用 light（實際：${initialTheme}）`);
    await lightContext.close();
  }

  // ---- 7. JWT 模式：身分顯示與登出行為（以 page.route() 模擬已登入 session，
  //         理由與 verify-admin-login-gate.mjs 第 6 段相同：真正的 magic-link 兌換
  //         需要收信才能取得一次性 token，故用攔截模擬「已有有效 JWT session」） ----
  {
    const jwtPage = await browser.newPage();
    let loggedOut = false;
    let logoutCalled = false;
    const MOCK_EMAIL = 'verify-jwt-toolbar@example.invalid';
    await jwtPage.route('**/api/admin/**', (route) => {
      const req = route.request();
      const url = new URL(req.url());
      if (url.pathname === '/api/admin/logout' && req.method() === 'POST') {
        logoutCalled = true;
        loggedOut = true;
        return route.fulfill({ status: 204, body: '' });
      }
      if (url.pathname === '/api/admin/me') {
        if (loggedOut) return route.fulfill({ status: 401, contentType: 'application/json', body: '{}' });
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ email: MOCK_EMAIL, mode: 'jwt' }),
        });
      }
      return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    });
    await jwtPage.goto(`${BASE}/admin.html`, { waitUntil: 'domcontentloaded' });
    await jwtPage.waitForSelector('#app:not([hidden])', { timeout: 15000 });

    const jwtIdentity = await jwtPage.locator('#tb-identity').textContent();
    ok(jwtIdentity === MOCK_EMAIL, `JWT 模式身分顯示為登入者 email（實際：「${jwtIdentity}」）`);

    await jwtPage.click('#tb-logout');
    await jwtPage.waitForSelector('#gate', { state: 'visible', timeout: 15000 });
    ok(logoutCalled, 'JWT 模式登出時，實際呼叫了 POST /api/admin/logout');
    ok(!(await jwtPage.locator('#app').isVisible()), 'JWT 模式登出後主畫面不再顯示，回到登入 gate');
    await jwtPage.close();
  }

  console.log(failed === 0 ? '\n全部通過 ✅' : `\n共 ${failed} 項失敗 ❌`);
  process.exitCode = failed === 0 ? 0 : 1;
} catch (e) {
  console.error('FAIL:', e.message, e.stack);
  process.exitCode = 1;
} finally {
  await browser.close();
}
