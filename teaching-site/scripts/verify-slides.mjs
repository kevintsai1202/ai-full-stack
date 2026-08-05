/**
 * 直播投影片驗證腳本。
 *
 * 檢查 live-slides/index.html 是否能正常放映：頁數、圖片是否全部載入、
 * 鍵盤導航是否有效、每頁是否都有講者備註。
 *
 * 直播當天沒有時間發現「某張圖沒出來」，所以這件事要能一鍵重跑。
 *
 * 執行（需在 teaching-site 目錄下，因為 playwright 裝在這裡）：
 *   node scripts/verify-slides.mjs
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

const browser = await chromium.launch();
try {
  const page = await browser.newPage({ viewport: { width: 1600, height: 900 } });

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

  // 講者備註開關
  await page.keyboard.press('n');
  const notesVisible = await page.locator('#notes').evaluate((el) => el.classList.contains('show'));
  check('講者備註可開啟', notesVisible);
  await page.keyboard.press('n');

  // 抽樣存檔，讓人眼確認排版沒有破版
  mkdirSync(outputDir, { recursive: true });
  const samples = [1, 4, 13, 20, 29, total - 2];
  for (const pageNumber of samples) {
    if (pageNumber < 1 || pageNumber > total) continue;
    // 呼叫投影片自己的 show()，底部列與備註才會跟著更新；
    // 直接切 class 會截到「內容是第 N 頁、頁碼還停在第 1 頁」的假畫面
    await page.evaluate((n) => window.show(n - 1), pageNumber);
    // 等淡入動畫結束再截，否則會拍到半透明的中間狀態
    await page.waitForTimeout(260);
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
