// 讀者端右上工具列（日夜切換／登出）與暗色主題 WCAG 對比驗證腳本（Task 6）
//
// 結構整體照抄 verify-admin-toolbar-theme.mjs（loadPlaywright / ok-fail 計數 /
// okContrast WCAG 相對亮度公式 / ensureTheme 冪等切換），因為兩者驗證的是同一套
// data-theme 主題機制，只是套用在讀者端頁面而非後台。
//
// 驗證項目：
//   1. 匿名訪客：首頁與文章頁都能看到 #reader-theme-btn（恆顯示），
//      #reader-logout-btn 不存在（未登入不該有登出按鈕）
//   2. 點擊 #reader-theme-btn：data-theme 屬性翻轉、localStorage.reader-theme 更新，
//      reload 後保持
//   3. 模擬登入（以 page.route 攔截 /r/ 回應，把 nav 裡的 href="/r/login"
//      改寫成 href="/r/me"，重現 ReaderNav 登入時渲染出的「我的帳戶」連結）：
//      #reader-logout-btn 出現，點擊後確實送出 POST /api/reader/logout
//   4. 亮色與暗色各跑一輪 WCAG 對比斷言（全部 4.5:1 門檻，不用大字豁免）：
//      body 主文字 / .page-intro / #email 輸入框 / .btn / .site-head nav a /
//      .head-tool-btn / 文章頁 .card 內文字
//
// 用法（後端須已在本機啟動；JDK 21，APP_ALLOW_INSECURE_DEV_SECRETS=true，
// ADMIN_API_KEY=dev-admin-key，DB 為本機容器 survey-test-db）：
//   node survey-backend/scripts/verify-reader-theme.mjs
//   ARTICLE_SLUG=some-slug node survey-backend/scripts/verify-reader-theme.mjs
//
// 絕對不要把 READER_BASE 指向正式站——本腳本會清 localStorage 並重整頁面，
// 預設值刻意設為本機位址（比照 verify-admin-toolbar-theme.mjs 的慣例）。
//
// 可重跑：只讀頁面、切主題、模擬登出（不呼叫真正的登出端點以外的任何寫入），
// 不寫入任何需要清理的後端狀態。

import { existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { pathToFileURL, fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const BASE = process.env.READER_BASE || 'http://127.0.0.1:8080';

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

/**
 * WCAG 2.1 對比度計算（與 verify-admin-toolbar-theme.mjs 完全相同的實作）。
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
 * 確保頁面目前處於指定主題，冪等操作（與 verify-admin-toolbar-theme.mjs 相同慣例）。
 * 每次呼叫都即時從頁面讀取當前的 data-theme，不依賴呼叫端快取的舊變數。
 */
async function ensureTheme(page, theme) {
  const current = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
  if (current !== theme) {
    await page.click('#reader-theme-btn');
    await page.waitForFunction((t) => document.documentElement.getAttribute('data-theme') === t, theme);
  }
}

/** 抓取一組「文字 vs 背景」的 getComputedStyle 結果；元素不存在回傳 null。 */
async function readPair(page, selector) {
  return page.evaluate((sel) => {
    const el = document.querySelector(sel);
    if (!el) return null;
    const s = getComputedStyle(el);
    return { color: s.color, bg: s.backgroundColor };
  }, selector);
}

/** 取得文章頁的第一個 slug：優先用環境變數，否則從 /r/archive 抓第一篇連結。 */
async function resolveArticleSlug(browser) {
  if (process.env.ARTICLE_SLUG) return process.env.ARTICLE_SLUG;
  const page = await browser.newPage();
  await page.goto(`${BASE}/r/archive`, { waitUntil: 'domcontentloaded' });
  const href = await page.evaluate(() => {
    const link = document.querySelector('a[href^="/r/news/"]');
    return link ? link.getAttribute('href') : null;
  });
  await page.close();
  if (!href) return null;
  const matched = href.match(/^\/r\/news\/([a-z0-9-]+)$/);
  return matched ? matched[1] : null;
}

/** 在指定主題下，跑一輪所有 WCAG 對比斷言（首頁 + 文章頁）。 */
async function runContrastPass(browser, theme, articleSlug) {
  const page = await browser.newPage();
  await page.addInitScript((t) => localStorage.setItem('reader-theme', t), theme);
  await page.goto(`${BASE}/r/`, { waitUntil: 'domcontentloaded' });
  const actualTheme = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
  ok(actualTheme === theme, `首頁載入後主題為 ${theme}（實際：${actualTheme}）`);

  const bodyPair = await readPair(page, 'body');
  if (bodyPair) okContrast(bodyPair.color, bodyPair.bg, 4.5, `${theme} body 主文字對背景`);
  else fail(`${theme}：找不到 body 元素`);

  // .page-intro 本身背景透明，實際視覺背景是版面的 --bg（body 底色）；
  // 直接用 getComputedStyle 讀該元素會拿到 rgba(0,0,0,0)，故改為讀 --bg 變數的實際計算值。
  const introPair = await page.evaluate(() => {
    const el = document.querySelector('.page-intro');
    if (!el) return null;
    const bg = getComputedStyle(document.body).backgroundColor;
    return { color: getComputedStyle(el).color, bg };
  });
  if (introPair) okContrast(introPair.color, introPair.bg, 4.5, `${theme} .page-intro 文字對 --bg`);
  else fail(`${theme}：找不到 .page-intro 元素`);

  // #email 需先進 /r/login（首頁登入前沒有這個欄位）
  await page.goto(`${BASE}/r/login`, { waitUntil: 'domcontentloaded' });
  const emailPair = await page.evaluate(() => {
    const el = document.querySelector('#email');
    if (!el) return null;
    const s = getComputedStyle(el);
    return { color: s.color, bg: s.backgroundColor };
  });
  if (emailPair) okContrast(emailPair.color, emailPair.bg, 4.5, `${theme} #email 輸入框文字對背景`);
  else fail(`${theme}：找不到 #email 元素`);

  const btnPair = await page.evaluate(() => {
    const el = document.querySelector('.btn');
    if (!el) return null;
    const s = getComputedStyle(el);
    return { color: s.color, bg: s.backgroundColor };
  });
  if (btnPair) okContrast(btnPair.color, btnPair.bg, 4.5, `${theme} .btn 按鈕文字對背景`);
  else fail(`${theme}：找不到 .btn 元素`);

  const navPair = await page.evaluate(() => {
    const el = document.querySelector('.site-head nav a');
    if (!el) return null;
    const nav = el.closest('nav');
    const s = getComputedStyle(el);
    return { color: s.color, bg: getComputedStyle(nav).backgroundColor };
  });
  if (navPair) okContrast(navPair.color, navPair.bg, 4.5, `${theme} .site-head nav a 文字對 nav 背景`);
  else fail(`${theme}：找不到 .site-head nav a 元素`);

  const toolBtnPair = await page.evaluate(() => {
    const el = document.querySelector('.head-tool-btn');
    if (!el) return null;
    const s = getComputedStyle(el);
    return { color: s.color, bg: s.backgroundColor };
  });
  if (toolBtnPair) okContrast(toolBtnPair.color, toolBtnPair.bg, 4.5, `${theme} .head-tool-btn 文字對背景`);
  else fail(`${theme}：找不到 .head-tool-btn 元素`);

  if (articleSlug) {
    await page.goto(`${BASE}/r/news/${articleSlug}`, { waitUntil: 'domcontentloaded' });
    const cardPair = await page.evaluate(() => {
      const card = document.querySelector('.card');
      if (!card) return null;
      const textEl = card.querySelector('p, h2, span') || card;
      const s = getComputedStyle(textEl);
      return { color: s.color, bg: getComputedStyle(card).backgroundColor };
    });
    if (cardPair) okContrast(cardPair.color, cardPair.bg, 4.5, `${theme} 文章頁 .card 內文字對卡片背景`);
    else fail(`${theme}：文章頁找不到 .card 元素`);
  } else {
    fail(`${theme}：找不到可用的文章 slug（ARTICLE_SLUG 未設且 /r/archive 抓不到連結），無法驗證文章頁對比`);
  }

  await page.close();
}

const browser = await chromium.launch();

try {
  // ---- 0. 準備文章 slug（供對比測試與後續共用） ----
  const articleSlug = await resolveArticleSlug(browser);
  ok(!!articleSlug, `已取得可用的文章 slug（${articleSlug || '無'}）`);

  // ---- 1. 匿名訪客：首頁與文章頁都能看到工具列日夜切換鈕，登出鈕不存在 ----
  const page = await browser.newPage();
  await page.goto(`${BASE}/r/`, { waitUntil: 'domcontentloaded' });
  ok(await page.locator('#reader-theme-btn').isVisible(), '匿名首頁：#reader-theme-btn 存在且可見');
  ok((await page.locator('#reader-logout-btn').count()) === 0, '匿名首頁：#reader-logout-btn 不存在');

  if (articleSlug) {
    await page.goto(`${BASE}/r/news/${articleSlug}`, { waitUntil: 'domcontentloaded' });
    ok(await page.locator('#reader-theme-btn').isVisible(), '匿名文章頁：#reader-theme-btn 存在且可見');
    ok((await page.locator('#reader-logout-btn').count()) === 0, '匿名文章頁：#reader-logout-btn 不存在');
  }

  // ---- 2. 主題切換：點擊翻轉 data-theme、寫入 localStorage，reload 後保持 ----
  await page.goto(`${BASE}/r/`, { waitUntil: 'domcontentloaded' });
  const before = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
  await page.click('#reader-theme-btn');
  const after = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
  ok(before !== after, `data-theme 屬性有切換（${before} → ${after}）`);
  const stored = await page.evaluate(() => localStorage.getItem('reader-theme'));
  ok(stored === after, `localStorage.reader-theme 已寫入且與目前主題一致（${stored}）`);
  await page.reload({ waitUntil: 'domcontentloaded' });
  const afterReload = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
  ok(afterReload === after, `重新整理後主題保持不變（${afterReload}）`);
  await page.close();

  // ---- 3. 模擬登入：攔截 /r/ 回應，把 nav 的 href="/r/login" 改寫成 href="/r/me"，
  //         重現 ReaderNav 登入時真正渲染出的「我的帳戶」連結；驗證登出鈕出現與行為 ----
  {
    const loginPage = await browser.newPage();
    let logoutCalled = false;
    await loginPage.route('**/r/', async (route) => {
      const response = await route.fetch();
      const body = await response.text();
      const rewritten = body.replace('href="/r/login"', 'href="/r/me"');
      await route.fulfill({ response, body: rewritten });
    });
    await loginPage.route('**/api/reader/logout', (route) => {
      logoutCalled = true;
      return route.fulfill({ status: 204, body: '' });
    });
    await loginPage.goto(`${BASE}/r/`, { waitUntil: 'domcontentloaded' });
    ok(await loginPage.locator('#reader-logout-btn').isVisible(),
      '模擬登入 nav（href="/r/me"）：#reader-logout-btn 出現');
    // 點擊後 handler 會 await fetch 再導頁；等到攔截到的 POST 回應完成再斷言，避免競態。
    await Promise.all([
      loginPage.waitForResponse((res) => res.url().includes('/api/reader/logout')),
      loginPage.click('#reader-logout-btn'),
    ]);
    ok(logoutCalled, '點擊登出鈕確實送出 POST /api/reader/logout');
    await loginPage.close();
  }

  // ---- 4. WCAG 對比：亮色、暗色各跑一輪 ----
  await runContrastPass(browser, 'light', articleSlug);
  await runContrastPass(browser, 'dark', articleSlug);

  console.log(failed === 0 ? '\n全部通過 ✅' : `\n共 ${failed} 項失敗 ❌`);
  process.exitCode = failed === 0 ? 0 : 1;
} catch (e) {
  console.error('FAIL:', e.message, e.stack);
  process.exitCode = 1;
} finally {
  await browser.close();
}
