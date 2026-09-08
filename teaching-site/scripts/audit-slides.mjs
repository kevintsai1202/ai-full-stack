/**
 * 直播投影片全頁 E2E 體檢腳本。
 *
 * 逐頁放映 live-slides/index.html，每頁截圖並量測：
 *   1. 文字最小字級（投影時字太小等於看不到）
 *   2. 內容區塊的左右留白（內容擠在左側、右邊一大片空白＝偏左）
 *   3. 內容區塊的上下留白（內容太矮＝字級還有放大的空間）
 * 依門檻標出需要調整的頁面，並輸出 JSON 報告供人工複核。
 *
 * 執行（需在 teaching-site 目錄下，因為 playwright 裝在這裡）：
 *   node scripts/audit-slides.mjs            # 全頁體檢＋截圖
 *   node scripts/audit-slides.mjs --no-shot  # 只量測不截圖
 */
import { chromium } from 'playwright';
import { pathToFileURL, fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';
import { mkdirSync, writeFileSync } from 'node:fs';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const slidesPath = resolve(scriptDir, '..', '..', 'live-slides', 'index.html');
const outputDir = resolve(scriptDir, '..', '..', 'live-slides', 'verify-output', 'audit');
const takeShots = !process.argv.includes('--no-shot');

/** 放映解析度：預設與設計基準 1600×900 一致，可用 --viewport WxH 模擬其他視窗（投影片會等比縮放） */
const viewportIndex = process.argv.indexOf('--viewport');
const viewportMatch = viewportIndex >= 0 ? String(process.argv[viewportIndex + 1] || '').match(/^(\d+)x(\d+)$/) : null;
const VIEWPORT = viewportMatch
  ? { width: Number(viewportMatch[1]), height: Number(viewportMatch[2]) }
  : { width: 1600, height: 900 };

/** 門檻：投影機取向，1600 寬下 22px 以下的字在教室後排已難辨識 */
const MIN_FONT_PX = 22;
/** 底部列高度（.bar 46px），量留白時要扣掉 */
const BAR_HEIGHT = 46;
/** 左右留白差超過畫面寬度的這個比例＝內容偏左（或偏右） */
const SKEW_RATIO = 0.18;
/** 內容高度低於可用高度的這個比例＝上下留白過多，字級可放大 */
const MIN_FILL_RATIO = 0.5;

mkdirSync(outputDir, { recursive: true });

const browser = await chromium.launch();
const report = [];
try {
  const page = await browser.newPage({ viewport: VIEWPORT });
  await page.goto(pathToFileURL(slidesPath).href, { waitUntil: 'load' });
  const total = await page.locator('.slide').count();

  for (let n = 1; n <= total; n += 1) {
    await page.evaluate((i) => window.show(i - 1), n);
    await page.waitForTimeout(220);

    // 在頁面內量測：文字節點字級、內容外框
    const metrics = await page.evaluate(({ barHeight }) => {
      const slide = document.querySelector('.slide.active');
      const slideRect = slide.getBoundingClientRect();
      const usableBottom = slideRect.height - barHeight;

      /** 收集所有「直接含文字」的元素：字級與位置 */
      const texts = [];
      const walker = document.createTreeWalker(slide, NodeFilter.SHOW_TEXT);
      let node;
      while ((node = walker.nextNode())) {
        if (!node.textContent.trim()) continue;
        const el = node.parentElement;
        const rect = el.getBoundingClientRect();
        if (rect.width === 0 || rect.height === 0) continue;
        const size = parseFloat(getComputedStyle(el).fontSize);
        texts.push({ tag: el.tagName.toLowerCase(), cls: el.className || '', size, text: node.textContent.trim().slice(0, 24) });
      }

      /** 內容外框：slide 直接子元素的聯集 */
      let left = Infinity, right = -Infinity, top = Infinity, bottom = -Infinity;
      for (const child of slide.children) {
        const r = child.getBoundingClientRect();
        if (r.width === 0 || r.height === 0) continue;
        left = Math.min(left, r.left); right = Math.max(right, r.right);
        top = Math.min(top, r.top); bottom = Math.max(bottom, r.bottom);
      }
      return {
        heading: (slide.querySelector('h1, h2, h3')?.textContent || '').trim().slice(0, 30),
        isCenter: slide.classList.contains('center') || slide.classList.contains('cover'),
        texts,
        box: { left, right, top, bottom },
        slide: { width: slideRect.width, height: usableBottom },
      };
    }, { barHeight: BAR_HEIGHT });

    const { box, slide } = metrics;
    const leftGap = box.left;
    const rightGap = slide.width - box.right;
    const topGap = box.top;
    const bottomGap = slide.height - box.bottom;
    const fill = (box.bottom - box.top) / slide.height;
    // getComputedStyle 回傳的是設計基準 px（不含 zoom），門檻直接比；留白量測用 rect（含 zoom）
    const smallTexts = metrics.texts.filter((t) => t.size < MIN_FONT_PX);
    const minFont = Math.min(...metrics.texts.map((t) => t.size));

    const issues = [];
    if (smallTexts.length) issues.push(`字級<${MIN_FONT_PX}px：${smallTexts.length} 處（最小 ${minFont}px）`);
    if (rightGap - leftGap > slide.width * SKEW_RATIO) issues.push(`偏左：左留白 ${Math.round(leftGap)}px／右留白 ${Math.round(rightGap)}px`);
    if (leftGap - rightGap > slide.width * SKEW_RATIO) issues.push(`偏右：左留白 ${Math.round(leftGap)}px／右留白 ${Math.round(rightGap)}px`);
    if (fill < MIN_FILL_RATIO) issues.push(`內容偏矮：只佔高度 ${Math.round(fill * 100)}%（上 ${Math.round(topGap)}／下 ${Math.round(bottomGap)}）`);

    report.push({ page: n, heading: metrics.heading, minFont, fill: Number(fill.toFixed(2)), leftGap: Math.round(leftGap), rightGap: Math.round(rightGap), smallTexts, issues });

    if (takeShots) {
      await page.screenshot({ path: `${outputDir}/slide-${String(n).padStart(2, '0')}.png` });
    }
  }
} finally {
  await browser.close();
}

writeFileSync(`${outputDir}/report.json`, JSON.stringify(report, null, 2), 'utf8');

// 主控台摘要：只列有問題的頁
const flagged = report.filter((r) => r.issues.length);
for (const r of flagged) {
  console.log(`P${String(r.page).padStart(2, '0')} ${r.heading}`);
  r.issues.forEach((i) => console.log(`    - ${i}`));
}
console.log('');
console.log(`共 ${report.length} 頁，需調整 ${flagged.length} 頁。報告：${outputDir}/report.json`);
