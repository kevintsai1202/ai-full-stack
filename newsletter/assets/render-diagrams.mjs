// 即時通訊系列示意圖：SVG → PNG 轉檔腳本（Email 客戶端不支援 SVG，信件需用 PNG）。
// 用法：python newsletter/assets/generate_diagrams.py && node newsletter/assets/render-diagrams.mjs
// 輸出：newsletter/assets/png/*.png（deviceScaleFactor 2，高解析度）
// 需求：playwright（本機為全域安裝：npm i -g playwright）
import { mkdir, readdir, readFile } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { existsSync } from 'node:fs';

const here = dirname(fileURLToPath(import.meta.url));
const outDir = join(here, 'png');

/** 動態載入 playwright：先試專案內解析，再逐一嘗試常見的全域安裝目錄（同 verify-admin.mjs 慣例） */
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

await mkdir(outDir, { recursive: true });
const svgs = (await readdir(here)).filter((f) => f.endsWith('.svg')).sort();
if (!svgs.length) { console.error('assets 目錄下沒有 SVG，請先跑 generate_diagrams.py'); process.exit(1); }

const browser = await chromium.launch();
// deviceScaleFactor 2：輸出 2 倍解析度，信件縮放後仍銳利
const ctx = await browser.newContext({ deviceScaleFactor: 2 });
const page = await ctx.newPage();

for (const name of svgs) {
  const svg = await readFile(join(here, name), 'utf8');
  const [, w, h] = svg.match(/width="(\d+)" height="(\d+)"/) || [];
  await page.setViewportSize({ width: Number(w), height: Number(h) });
  // 以 data URL 直接載入 SVG，避免 file:// 相對路徑問題
  await page.goto('data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg));
  const png = join(outDir, name.replace(/\.svg$/, '.png'));
  await page.screenshot({ path: png });
  console.log('已輸出', png);
}
await browser.close();
console.log(`完成：${svgs.length} 張 PNG 於 ${outDir}`);
