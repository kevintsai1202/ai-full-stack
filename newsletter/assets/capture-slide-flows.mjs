// 電子報第 011 期主題一流程圖：從 live-slides 投影片擷取現成的流程圖區塊。
// 擷取對象：
//   P4「Agentic Coding 流程」的五步迴圈（理解需求→規劃→生成→驗證→修正）
//   P6「今天走這四站」的四站地圖（想法期→設計期→開發驗收→上線前）
// 用法：node newsletter/assets/capture-slide-flows.mjs
// 輸出：newsletter/assets/png/011-c-agentic-coding-loop.png、011-d-four-stations-map.png
//       （deviceScaleFactor 2，高解析度；沿用 render-diagrams.mjs 的輸出慣例）
// 需求：playwright（先找專案內，再找全域安裝，同 render-diagrams.mjs 慣例）
import { mkdir } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { existsSync } from 'node:fs';

const here = dirname(fileURLToPath(import.meta.url));
const outDir = join(here, 'png');
const slidesHtml = join(here, '..', '..', 'live-slides', 'index.html');

/** 動態載入 playwright：先試專案內解析，再逐一嘗試常見的全域安裝目錄 */
async function loadPlaywright() {
  try { return await import('playwright'); } catch { /* 專案內沒有，改找全域 */ }
  const roots = [
    process.env.APPDATA && join(process.env.APPDATA, 'npm', 'node_modules'),
    process.env.ProgramFiles && join(process.env.ProgramFiles, 'nodejs', 'node_modules'),
    '/usr/local/lib/node_modules', '/usr/lib/node_modules',
  ].filter(Boolean);
  for (const root of roots) {
    const entry = join(root, 'playwright', 'index.js');
    if (existsSync(entry)) return await import(pathToFileURL(entry).href);
  }
  throw new Error('找不到 playwright，請先 npm i -g playwright');
}

const mod = await loadPlaywright();
const { chromium } = mod.default ?? mod;

/** 要擷取的投影片清單：hash 為 1-based 頁碼（投影片以 location.hash 記頁次） */
const targets = [
  { hash: 4, out: '011-c-agentic-coding-loop.png', label: 'Agentic Coding 五步迴圈（P4）' },
  { hash: 6, out: '011-d-four-stations-map.png', label: '四站地圖（P6）' },
];

await mkdir(outDir, { recursive: true });
const browser = await chromium.launch();
// deviceScaleFactor 2：輸出 2 倍解析度，信件縮放後仍銳利
const ctx = await browser.newContext({ viewport: { width: 1600, height: 900 }, deviceScaleFactor: 2 });
const page = await ctx.newPage();

for (const t of targets) {
  // 投影片只在頁面載入時讀取 hash 決定頁次（無 hashchange 監聽），
  // 同網址僅換 hash 的 goto 不會重新載入——先導向空白頁強制下一次為全新載入
  await page.goto('about:blank');
  await page.goto(pathToFileURL(slidesHtml).href + `#${t.hash}`);
  // 底部列與講者備註不屬於流程圖內容，擷取前先隱藏
  await page.addStyleTag({ content: '.bar, .notes { display: none !important; }' });
  const map = page.locator('.slide.active .map');
  await map.waitFor({ state: 'visible' });
  const box = await map.boundingBox();
  if (!box) throw new Error(`${t.label}：找不到 .map 區塊`);
  // 以 clip 外擴 24px 留白，讓深色背景框住整張流程圖
  const pad = 24;
  await page.screenshot({
    path: join(outDir, t.out),
    clip: { x: box.x - pad, y: box.y - pad, width: box.width + pad * 2, height: box.height + pad * 2 },
  });
  console.log('已輸出', join(outDir, t.out), '←', t.label);
}
await browser.close();
console.log('完成：2 張投影片流程圖 PNG');
