/**
 * 直播投影片驗證腳本。
 *
 * 檢查 live-slides/index.html 是否能正常放映：頁數、圖片是否全部載入、
 * 鍵盤導航是否有效、每頁是否都有講者備註。
 *
 * 直播當天沒有時間發現「某張圖沒出來」，所以這件事要能一鍵重跑。
 *
 * 執行（需在 teaching-site 目錄下，因為 playwright 裝在這裡）：
 *   node scripts/verify-slides.mjs                      # 預設 1600×900
 *   node scripts/verify-slides.mjs --viewport 1366x768  # 模擬筆電 100% 顯示
 * 投影片會依視窗等比縮放（index.html 的 fitToViewport），不同視窗都應通過。
 */
import { chromium } from 'playwright';
import { pathToFileURL } from 'node:url';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { existsSync, mkdirSync } from 'node:fs';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const slidesPath = resolve(scriptDir, '..', '..', 'live-slides', 'index.html');
const outputDir = resolve(scriptDir, '..', '..', 'live-slides', 'verify-output');

/** 累積檢查結果，最後一次回報 */
const failures = [];

/** 記錄一項檢查結果 */
function check(name, passed, detail = '') {
  console.log(`${passed ? '  OK  ' : ' FAIL '} ${name}${detail ? ` — ${detail}` : ''}`);
  if (!passed) failures.push(`${name}${detail ? `：${detail}` : ''}`);
}

if (!existsSync(slidesPath)) {
  console.error(`找不到投影片：${slidesPath}`);
  process.exit(1);
}

/** 解析 --viewport WxH，預設為設計基準 1600×900 */
const viewportIndex = process.argv.indexOf('--viewport');
const viewportMatch = viewportIndex >= 0 ? String(process.argv[viewportIndex + 1] || '').match(/^(\d+)x(\d+)$/) : null;
const viewport = viewportMatch
  ? { width: Number(viewportMatch[1]), height: Number(viewportMatch[2]) }
  : { width: 1600, height: 900 };
console.log(`視窗 ${viewport.width}×${viewport.height}`);

const browser = await chromium.launch();
try {
  const page = await browser.newPage({ viewport });

  // 收集載入失敗的資源，圖片沒出來是直播當天最常見也最致命的問題
  const failedRequests = [];
  page.on('requestfailed', (request) => failedRequests.push(request.url()));

  await page.goto(pathToFileURL(slidesPath).href, { waitUntil: 'load' });

  const total = await page.locator('.slide').count();
  check('投影片載入', total > 0, `共 ${total} 頁`);

  // 每頁都要有講者備註，否則直播時該頁沒有提示
  const missingNotes = await page.evaluate(() =>
    Array.from(document.querySelectorAll('.slide'))
      .map((slide, index) => ({ index: index + 1, note: slide.dataset.note }))
      .filter((item) => !item.note)
      .map((item) => item.index)
  );
  check('每頁都有講者備註', missingNotes.length === 0, missingNotes.length ? `缺第 ${missingNotes.join(', ')} 頁` : '');

  // 圖片必須真的解碼成功，src 存在不代表載得到
  const brokenImages = await page.evaluate(() =>
    Array.from(document.images)
      .filter((img) => !img.complete || img.naturalWidth === 0)
      .map((img) => img.getAttribute('src'))
  );
  check('所有圖片載入成功', brokenImages.length === 0, brokenImages.join(', '));
  check('無資源載入失敗', failedRequests.length === 0, failedRequests.join(', '));

  // 導航：從第一頁按右鍵應該前進
  await page.keyboard.press('Home');
  const firstActive = await page.evaluate(() =>
    Array.from(document.querySelectorAll('.slide')).findIndex((s) => s.classList.contains('active'))
  );
  await page.keyboard.press('ArrowRight');
  const afterRight = await page.evaluate(() =>
    Array.from(document.querySelectorAll('.slide')).findIndex((s) => s.classList.contains('active'))
  );
  check('鍵盤導航可前進', afterRight === firstActive + 1, `${firstActive} → ${afterRight}`);

  // 點擊右半邊也要能前進——套用視窗縮放後座標系容易出錯，直接用實際點擊驗
  await page.mouse.click(Math.round(viewport.width * 0.9), Math.round(viewport.height * 0.5));
  const afterClick = await page.evaluate(() =>
    Array.from(document.querySelectorAll('.slide')).findIndex((s) => s.classList.contains('active'))
  );
  check('點擊右半可前進', afterClick === afterRight + 1, `${afterRight} → ${afterClick}`);

  // 分站頁進場動畫：翻到分站頁後，該站卡片要放大到 1.1 倍以上；
  // 直播時這個動畫是「現在講到哪一站」的視覺提示，沒播等於少了一個節奏點
  const stationScales = await page.evaluate(async () => {
    const result = [];
    const slides = Array.from(document.querySelectorAll('.slide'));
    for (let i = 0; i < slides.length; i += 1) {
      const target = slides[i].querySelector('.map:not(.all) .station.on');
      if (!target) continue;
      window.show(i);
      // 動畫延遲 .25s＋長度 .7s，等它跑完再量
      await new Promise((done) => setTimeout(done, 1100));
      // 以同頁一張未高亮的卡片當基準，算高亮卡片放大了幾倍
      const before = slides[i].querySelector('.station:not(.on)').getBoundingClientRect();
      const rect = target.getBoundingClientRect();
      result.push({ page: i + 1, scale: Number((rect.width / before.width).toFixed(2)) });
    }
    return result;
  });
  const unscaled = stationScales.filter((s) => s.scale < 1.1);
  check('分站頁卡片放大動畫', stationScales.length === 4 && unscaled.length === 0,
    stationScales.map((s) => `P${s.page}×${s.scale}`).join(' '));
  await page.keyboard.press('Home');

  // 講者備註開關
  await page.keyboard.press('n');
  const notesVisible = await page.locator('#notes').evaluate((el) => el.classList.contains('show'));
  check('講者備註可開啟', notesVisible);
  await page.keyboard.press('n');

  // 逐頁檢查內容是否超出投影片可視範圍——字級放大後最容易發生，
  // 破版在直播當下才發現就來不及了，所以做成自動檢查
  const overflowPages = await page.evaluate(() => {
    const result = [];
    document.querySelectorAll('.slide').forEach((slide, index) => {
      slide.classList.add('active');
      // 投影片是 justify-content:center 的 flex 欄，內容太高時會「上下同時」溢出，
      // scrollHeight 抓不到往上溢出的部分，所以改量子元素外框是否超出內距框
      const style = getComputedStyle(slide);
      const slideRect = slide.getBoundingClientRect();
      // 視窗縮放（zoom）下 getBoundingClientRect 與 clientHeight 座標系可能不同，
      // 以兩者比值換算內距，讓比較全部落在 rect 的座標系
      const ratio = slideRect.height / slide.clientHeight;
      const usableTop = slideRect.top + parseFloat(style.paddingTop) * ratio;
      const usableBottom = slideRect.bottom - parseFloat(style.paddingBottom) * ratio;
      let top = Infinity, bottom = -Infinity;
      for (const child of slide.children) {
        const r = child.getBoundingClientRect();
        if (r.width === 0 || r.height === 0) continue;
        top = Math.min(top, r.top); bottom = Math.max(bottom, r.bottom);
      }
      // 允許 4px 誤差，避免次像素捨入造成誤報
      if (top < usableTop - 4 || bottom > usableBottom + 4) result.push(index + 1);
      slide.classList.remove('active');
    });
    return result;
  });
  check('無內容垂直溢出', overflowPages.length === 0, overflowPages.length ? `第 ${overflowPages.join(', ')} 頁超出畫面` : '');

  // 提示詞區塊（pre）有 max-height 上限，字級放大後可能變成內部捲動——
  // 直播時被藏住的底部內容等於不存在，所以也要擋下來
  const scrollingPre = await page.evaluate(() => {
    const result = [];
    document.querySelectorAll('.slide').forEach((slide, index) => {
      slide.classList.add('active');
      slide.querySelectorAll('pre').forEach((pre) => {
        if (pre.scrollHeight > pre.clientHeight + 4) result.push(index + 1);
      });
      slide.classList.remove('active');
    });
    return [...new Set(result)];
  });
  check('提示詞區塊無內部捲動', scrollingPre.length === 0, scrollingPre.length ? `第 ${scrollingPre.join(', ')} 頁 pre 內容被截斷` : '');

  // 抽樣存檔，讓人眼確認排版沒有破版
  mkdirSync(outputDir, { recursive: true });
  // 抽樣頁涵蓋：封面、職缺開場鉤子頁（P2）、流程頁／除錯成本頁、置中測驗頁（Q1，驗主標底線置中）、
  // 四張知識點小卡（站1/站2/站3/站4）、收尾總表，以及課程介紹段
  // （倒數第 6 頁＝課程過場、倒數第 4 頁＝九單元表、倒數第 1 頁＝QR 折扣頁；最後一頁是 Q&A 不抽）
  // 28 是 E2E 錄影展示頁（含影片），破圖或影片載不到都要在這裡被看見；
  // 8 是掃碼加入頁（EXAM QR Code＋加入碼＋Q0），QR 破圖等於學員進不來。
  // 投影片最前面多了一頁待機頁（P0，也放 QR），所以實際頁碼＝逐字稿頁碼＋1
  // 19 是開發計畫提示詞頁，pre 行數最多，字級或行數一動最容易被 max-height 截斷
  // 9 是第一站分站頁，要看放大動畫跑完的定格
  // P31 之後又插了兩頁（P31b 紅框標註、P31c 前後比對），所以 P32 起實際頁碼＝逐字稿頁碼＋3
  const scriptToActual = (n) => n + 1 + (n > 31 ? 2 : 0);
  const scriptPages = [1, 2, 5, 6, 8, 9, 10, 14, 17, 19, 24, 28, 35].map(scriptToActual);
  // P31b／P31c（E2E 紅框標註、前後比對，實際第 33、34 頁）沒有逐字稿頁碼，直接指定
  scriptPages.push(33, 34);
  const samples = [1, ...scriptPages, total - 8, total - 6, total - 5, total - 4, total - 3, total - 2, total - 1];
  for (const pageNumber of samples) {
    if (pageNumber < 1 || pageNumber > total) continue;
    // 呼叫投影片自己的 show()，底部列與備註才會跟著更新；
    // 直接切 class 會截到「內容是第 N 頁、頁碼還停在第 1 頁」的假畫面
    await page.evaluate((n) => window.show(n - 1), pageNumber);
    // 等淡入動畫結束再截，否則會拍到半透明的中間狀態；分站頁還有 .25s 延遲＋.7s 的放大動畫
    const hasPop = await page.evaluate(() => !!document.querySelector('.slide.active .map:not(.all) .station.on'));
    await page.waitForTimeout(hasPop ? 1100 : 260);
    await page.screenshot({ path: `${outputDir}/slide-${String(pageNumber).padStart(2, '0')}.png` });
  }
  check('抽樣截圖已輸出', true, outputDir);
} finally {
  await browser.close();
}

console.log('');
if (failures.length > 0) {
  console.error(`驗證失敗 ${failures.length} 項：`);
  failures.forEach((item) => console.error(`  - ${item}`));
  process.exit(1);
}
console.log('投影片驗證全部通過。');
