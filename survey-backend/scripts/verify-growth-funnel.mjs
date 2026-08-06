// 行銷漏斗圖渲染端到端驗證腳本（Task 11 / B3 前端）
//
// 驗證項目（§4.3.1）：
//   1. 正常遞減資料：#share-funnel-chart .funnel-layer 的 offsetWidth 嚴格遞減、
//      .funnel-rate 顯示轉換率、不出現 .funnel-warn
//   2. 倒掛資料（submitted > clicks）：寬度仍嚴格遞減（用 min(比例寬, 上層寬-6%) 撐住形狀），
//      但倒掛層要出現 .funnel-warn 且文字含「高於上一層」，且該層文字仍照顯實際數值
//      （異常不被隱藏，只是形狀不跟著倒掛）
//   3. readerFunnelStructured：頂層文字含「總瀏覽」與加總值（120=100+20 的假資料）、
//      且畫面上出現「訂閱路徑」「解鎖路徑」兩塊
//
// 用法（後端須已在本機啟動；金鑰為 dev-admin-key，需以
// APP_ALLOW_INSECURE_DEV_SECRETS=true 啟動）：
//   node survey-backend/scripts/verify-growth-funnel.mjs
//
// 絕對不要把 ADMIN_BASE 指向正式站——本腳本用 page.route() 攔截
// GET /api/admin/referrals/dashboard 餵假資料，預設值刻意設為本機位址
// （比照 verify-admin-toolbar-theme.mjs 的慣例）。
//
// 可重跑：只讀登入、攔截假資料渲染，不寫入任何需要清理的後端狀態。

import { mkdir } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { pathToFileURL, fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const BASE = process.env.ADMIN_BASE || 'http://127.0.0.1:8080';
const ADMIN_KEY = process.env.ADMIN_API_KEY || 'dev-admin-key';
const OUTPUT_DIR = join(__dirname, 'output');

/**
 * 動態載入 playwright：慣例與其他 verify-*.mjs 一致，先試專案內解析，
 * 再逐一嘗試常見的全域安裝目錄，載不到一律 exit 1。
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

/** 假資料：分享漏斗嚴格遞減（clicks > submitted > confirmed > approved）。 */
const NORMAL_DASHBOARD = {
  funnel: { clicks: 100, submitted: 60, confirmed: 40, approved: 20, clickToSubmitRate: 60, submitToConfirmRate: 66.7 },
  readerFunnel: {
    articleViews: 0, subscriptionHomeViews: 0, subscribeAttempts: 0, subscribeSuccess: 0,
    unlockClicks: 0, unlockSuccess: 0, homeToSubscribeRate: 0, unlockSuccessRate: 0,
  },
  readerTopArticles: [],
  topArticles: [],
  reviews: [],
  campaigns: [],
  readerFunnelStructured: {
    totalViews: 120,
    subscribePath: [
      { key: 'home_views', label: '進訂閱首頁', count: 100 },
      { key: 'attempts', label: '送出訂閱', count: 40 },
      { key: 'success', label: '訂閱成功', count: 30 },
    ],
    unlockPath: [
      { key: 'article_views', label: '看文章', count: 20 },
      { key: 'unlock_clicks', label: '點選解鎖', count: 10 },
      { key: 'unlock_success', label: '解鎖成功', count: 5 },
    ],
  },
};

/** 假資料：分享漏斗倒掛（submitted(150) > clicks(100)），驗證形狀仍遞減但異常照顯與警示。 */
const INVERTED_DASHBOARD = {
  ...NORMAL_DASHBOARD,
  funnel: { clicks: 100, submitted: 150, confirmed: 40, approved: 20, clickToSubmitRate: 150, submitToConfirmRate: 26.7 },
};

/**
 * 假資料：Reader 漏斗回歸測試專用（Important 修復）——訂閱／解鎖兩路徑的相鄰兩層
 * 百分比寬分別為 30%/24% 與 30%/24%（未觸及 JS 14% 地板，仍屬正常遞減資料，
 * 只是數值較小）。在 `.funnel-split .funnel{min-width:220px}` 的窄容器下，
 * 修復前的 `.funnel-layer{min-width:72px}` 會把這兩個換算後 <72px 的百分比寬拉平成
 * 相同的 72px（tie），違反 §4.3.1 嚴格遞減；修復後兩層應保持嚴格遞減。
 */
const NARROW_READER_DASHBOARD = {
  ...NORMAL_DASHBOARD,
  readerFunnelStructured: {
    totalViews: 150,
    subscribePath: [
      { key: 'home_views', label: '進訂閱首頁', count: 100 },
      { key: 'attempts', label: '送出訂閱', count: 30 },
      { key: 'success', label: '訂閱成功', count: 24 },
    ],
    unlockPath: [
      { key: 'article_views', label: '看文章', count: 50 },
      { key: 'unlock_clicks', label: '點選解鎖', count: 15 },
      { key: 'unlock_success', label: '解鎖成功', count: 12 },
    ],
  },
};

/**
 * 假資料：「窄容器＋倒掛」組合（審查第 2 輪 Important 回歸測試）——訂閱路徑第 2 層
 * （送出訂閱，150）大於第 1 層（進訂閱首頁，100），會觸發 .funnel-warn 警示；
 * 同時整條路徑渲染在窄容器（480px 視窗、`.funnel-split .funnel{min-width:220px}`）內。
 * 唯一會踩到「第 1 輪修復（拿掉固定 min-width）改用 nowrap+ellipsis 把警示一起裁掉」
 * 這個新 Important 的組合，先前的窄容器測資用的是正常遞減資料，沒有警示可測。
 */
const NARROW_INVERTED_READER_DASHBOARD = {
  ...NORMAL_DASHBOARD,
  readerFunnelStructured: {
    totalViews: 150,
    subscribePath: [
      { key: 'home_views', label: '進訂閱首頁', count: 100 },
      { key: 'attempts', label: '送出訂閱', count: 150 },
      { key: 'success', label: '訂閱成功', count: 20 },
    ],
    unlockPath: [
      { key: 'article_views', label: '看文章', count: 50 },
      { key: 'unlock_clicks', label: '點選解鎖', count: 15 },
      { key: 'unlock_success', label: '解鎖成功', count: 12 },
    ],
  },
};

/**
 * 在既有 page 上攔截 dashboard API，回傳指定假資料，並觸發一次 loadGrowth。
 * 等待條件刻意比對「這次注入的 clicks 數值」是否已出現在畫面上，而不是只等
 * `.funnel-layer` 數量 > 0——連續兩次呼叫若分享漏斗層數相同（例如都是 4 層），
 * 用層數判斷會在舊一輪的 DOM 還沒被换掉前就提前判定「已完成」，造成後續讀到
 * 尚未更新的 reader 漏斗內容（審查第 2 輪修復時發現的競態，曾導致誤判找不到
 * .funnel-warn）。
 */
async function mockDashboardAndLoad(page, dashboardData) {
  await page.unroute('**/api/admin/referrals/dashboard').catch(() => {});
  await page.route('**/api/admin/referrals/dashboard', (route) => {
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(dashboardData) });
  });
  await page.click('#tab-growth');
  await page.waitForSelector('#growth-view:not([hidden])', { timeout: 10000 });
  await page.click('#growth-refresh');
  // 用「這組資料特有」的四組 label+count 全部比對，避免兩次呼叫剛好有相同 clicks 數值時
  // 誤判成已經渲染完成（實際還是上一輪的舊 DOM）。
  const f = dashboardData.funnel;
  const expectedShareParts = [`分享點擊${f.clicks}`, `完成填表${f.submitted}`, `信箱確認${f.confirmed}`, `審核通過${f.approved}`];
  await page.waitForFunction(
    (parts) => {
      const layers = document.querySelectorAll('#share-funnel-chart .funnel-layer');
      if (layers.length === 0) return false;
      const text = Array.from(layers).map((el) => el.textContent).join('|');
      return parts.every((p) => text.includes(p));
    },
    expectedShareParts,
    { timeout: 10000 },
  );
  if (dashboardData.readerFunnelStructured) {
    // 逐一比對「這組資料特有」的 label+count 組合（不是只看層數或單一數值），
    // 確保真的是這次注入的資料渲染完成，而不是還沒被换掉的上一輪畫面。
    const s = dashboardData.readerFunnelStructured;
    const expectedPairs = [
      `總瀏覽（文章＋訂閱首頁）${s.totalViews}`,
      ...s.subscribePath.map((step) => `${step.label}${step.count}`),
      ...s.unlockPath.map((step) => `${step.label}${step.count}`),
    ];
    await page.waitForFunction(
      (pairs) => {
        const root = document.querySelector('#reader-funnel-chart');
        return !!root && pairs.every((pair) => root.textContent.includes(pair));
      },
      expectedPairs,
      { timeout: 10000 },
    );
  }
}

/** 讀取指定容器選擇器下 .funnel-layer 各層的 offsetWidth 陣列（share/reader 兩種漏斗共用）。 */
async function readLayerWidths(page, containerSelector) {
  return page.evaluate((sel) => Array.from(document.querySelectorAll(`${sel} .funnel-layer`)).map((el) => el.offsetWidth), containerSelector);
}

/** 讀取指定容器選擇器下 .funnel-layer 各層的 offsetWidth（維持向下相容的別名）。 */
async function readShareLayerWidths(page) {
  return readLayerWidths(page, '#share-funnel-chart');
}

/** 讀取指定選擇器下各節點的文字內容（.funnel-layer / .funnel-rate / .funnel-warn 共用）。 */
async function readTexts(page, selector) {
  return page.evaluate((sel) => Array.from(document.querySelectorAll(sel)).map((el) => el.textContent), selector);
}

const browser = await chromium.launch();

try {
  await mkdir(OUTPUT_DIR, { recursive: true });

  const page = await loginWithKey(browser);

  // ---- 1. 正常遞減資料 ----
  await mockDashboardAndLoad(page, NORMAL_DASHBOARD);

  const shareWidths = await readShareLayerWidths(page);
  ok(shareWidths.length === 4, `分享漏斗渲染 4 層（實際 ${shareWidths.length} 層）`);
  let strictlyDecreasing = shareWidths.every((w, i) => i === 0 || w < shareWidths[i - 1]);
  ok(strictlyDecreasing, `正常資料下分享漏斗寬度嚴格遞減（實際：${shareWidths.join(', ')}）`);

  const shareRates = await readTexts(page, '#share-funnel-chart .funnel-rate');
  ok(shareRates.length === 3, `分享漏斗顯示 3 個轉換率（層數-1）（實際 ${shareRates.length} 個）`);
  ok(shareRates.every((t) => t.includes('%')), `轉換率文字皆含百分比（實際：${JSON.stringify(shareRates)}）`);

  const shareWarnCountNormal = await page.locator('#share-funnel-chart .funnel-warn').count();
  ok(shareWarnCountNormal === 0, `正常遞減資料下不出現 .funnel-warn（實際 ${shareWarnCountNormal} 個）`);

  // ---- 2. 倒掛資料（submitted > clicks）----
  await mockDashboardAndLoad(page, INVERTED_DASHBOARD);

  const invertedWidths = await readShareLayerWidths(page);
  strictlyDecreasing = invertedWidths.every((w, i) => i === 0 || w < invertedWidths[i - 1]);
  ok(strictlyDecreasing, `倒掛資料下分享漏斗寬度仍嚴格遞減（§4.3.1，形狀不隨數值倒掛，實際：${invertedWidths.join(', ')}）`);

  const warnLocator = page.locator('#share-funnel-chart .funnel-warn');
  const warnCount = await warnLocator.count();
  ok(warnCount === 1, `倒掛層出現 1 個 .funnel-warn 警示（實際 ${warnCount} 個）`);
  if (warnCount === 1) {
    const warnText = await warnLocator.first().textContent();
    ok(warnText.includes('高於上一層'), `警示文字含「高於上一層」（實際：「${warnText}」）`);
  }
  // 倒掛層（完成填表，數值 150）本身的實際數值仍要照顯，不能被隱藏
  const layersText = await readTexts(page, '#share-funnel-chart .funnel-layer');
  ok(layersText.some((t) => t.includes('完成填表') && t.includes('150')),
    `倒掛層仍照顯實際數值 150（實際各層文字：${JSON.stringify(layersText)}）`);

  // ---- 3. readerFunnelStructured：頂層總瀏覽、訂閱／解鎖雙路徑 ----
  const readerChartText = await page.locator('#reader-funnel-chart').textContent();
  ok(readerChartText.includes('總瀏覽'), `Reader 漏斗頂層文字含「總瀏覽」（實際片段：${readerChartText.slice(0, 60)}...）`);
  ok(readerChartText.includes('120'), `Reader 漏斗頂層加總值 120（=100+20）有顯示`);
  ok(readerChartText.includes('訂閱路徑'), `畫面上出現「訂閱路徑」分塊`);
  ok(readerChartText.includes('解鎖路徑'), `畫面上出現「解鎖路徑」分塊`);

  // ---- 4. 窄容器（Reader 漏斗分塊）回歸測試：Important 修復——
  //         `.funnel-layer` 拿掉 min-width:72px 後，即使容器窄到 ~220px、
  //         換算後的百分比寬 < 72px，每條路徑內仍要嚴格遞減，不能被固定像素拉平成相同寬 ----
  await page.setViewportSize({ width: 480, height: 900 });
  await mockDashboardAndLoad(page, NARROW_READER_DASHBOARD);

  // DOM 順序：#reader-funnel-chart 的第 1 個 .funnel 是頂層總瀏覽，
  // 第 2 個是「訂閱路徑」外層 wrap，第 3 個是「解鎖路徑」外層 wrap；
  // 各 wrap 內巢狀的 .funnel 才是真正畫層的圖表容器。
  const subscribeWidths = await readLayerWidths(page, '#reader-funnel-chart > .funnel:nth-of-type(2) > .funnel');
  const unlockWidths = await readLayerWidths(page, '#reader-funnel-chart > .funnel:nth-of-type(3) > .funnel');

  ok(subscribeWidths.length === 3, `訂閱路徑渲染 3 層（實際 ${subscribeWidths.length} 層）`);
  ok(unlockWidths.length === 3, `解鎖路徑渲染 3 層（實際 ${unlockWidths.length} 層）`);

  const subscribeDecreasing = subscribeWidths.every((w, i) => i === 0 || w < subscribeWidths[i - 1]);
  ok(subscribeDecreasing, `窄容器下訂閱路徑寬度嚴格遞減（實際：${subscribeWidths.join(', ')}）`);

  const unlockDecreasing = unlockWidths.every((w, i) => i === 0 || w < unlockWidths[i - 1]);
  ok(unlockDecreasing, `窄容器下解鎖路徑寬度嚴格遞減（實際：${unlockWidths.join(', ')}）`);

  // 額外用 getComputedStyle 直接確認 CSS 已無殘留的固定像素 min-width（回歸的根因）
  const computedMinWidth = await page.evaluate(() => {
    const layer = document.querySelector('#reader-funnel-chart .funnel-layer');
    return layer ? getComputedStyle(layer).minWidth : null;
  });
  ok(computedMinWidth === '0px' || computedMinWidth === 'auto',
    `.funnel-layer 的 CSS min-width 已不再是固定像素值（實際計算值：${computedMinWidth}）`);

  // ---- 5. 窄容器（480px）＋倒掛資料組合：第 2 輪 Important 修復——
  //         驗證 .funnel-warn 的「視覺可見性」，不只是 textContent 存在。
  //         第 1 輪修復把 .funnel-layer 改成 white-space:nowrap+overflow:hidden+ellipsis 後，
  //         警示 span 是該行最後一個 inline 子元素，行寬不夠時最先被視覺裁掉——
  //         這個組合（窄容器＋倒掛）是唯一會踩到這個新缺陷的情境，先前測資都沒覆蓋。 ----
  await mockDashboardAndLoad(page, NARROW_INVERTED_READER_DASHBOARD);

  const warnGeom = await page.evaluate(() => {
    const layers = Array.from(document.querySelectorAll('#reader-funnel-chart .funnel-layer'));
    const warnLayer = layers.find((l) => l.querySelector('.funnel-warn'));
    if (!warnLayer) return null;
    const warn = warnLayer.querySelector('.funnel-warn');
    const layerRect = warnLayer.getBoundingClientRect();
    const warnRect = warn.getBoundingClientRect();
    return {
      text: warn.textContent,
      offsetParent: warn.offsetParent !== null,
      width: warnRect.width,
      height: warnRect.height,
      // 完整落在所屬層框內（右緣／下緣都沒超出），代表沒有被裁到看不見的地方
      containedRight: warnRect.right <= layerRect.right + 0.5,
      containedBottom: warnRect.bottom <= layerRect.bottom + 0.5,
      // 沒有橫向被裁掉的內容（scrollWidth 超過 clientWidth 代表有東西溢出可視範圍外）
      noHorizontalClip: warnLayer.scrollWidth <= warnLayer.clientWidth + 1,
    };
  });

  ok(warnGeom !== null, '窄容器＋倒掛資料下，找得到含 .funnel-warn 的層');
  if (warnGeom) {
    ok(warnGeom.text.includes('高於上一層'), `警示文字仍含「高於上一層」（實際：「${warnGeom.text}」）`);
    ok(warnGeom.offsetParent, '.funnel-warn 有 offsetParent（未被 display:none 之類隱藏）');
    ok(warnGeom.width > 0 && warnGeom.height > 0,
      `.funnel-warn 有實際渲染尺寸，非零寬高（寬 ${warnGeom.width.toFixed(1)}px，高 ${warnGeom.height.toFixed(1)}px）`);
    ok(warnGeom.containedRight && warnGeom.containedBottom,
      '.funnel-warn 完整落在所屬層框內（未超出邊界，代表沒有被裁到看不見的地方）');
    ok(warnGeom.noHorizontalClip,
      `所屬層無橫向視覺裁切（scrollWidth ${warnGeom.noHorizontalClip ? '<=' : '>'} clientWidth，代表警示未被 overflow 吃掉）`);
  }

  await page.close();

  console.log(failed === 0 ? '\n全部通過 ✅' : `\n共 ${failed} 項失敗 ❌`);
  process.exitCode = failed === 0 ? 0 : 1;
} catch (e) {
  console.error('FAIL:', e.message, e.stack);
  process.exitCode = 1;
} finally {
  await browser.close();
}
