// ============================================================
// 將 assets/ 下的 PNG 批次轉成 WebP（大幅縮小檔案、加快載入）
// 目的：UI 截圖類 PNG 動輒 1.5MB+，轉 WebP 可縮小 8～15 倍且肉眼幾乎無損
// 特性：可重跑（.webp 已存在且比來源新則略過）；產出與來源同目錄同檔名的 .webp
// 相依：需要 sharp。若未安裝，於任一 node 專案執行 `npm install sharp`，
//       並以 NODE_PATH 指向該 node_modules 後執行本腳本，例如：
//   NODE_PATH=<sharp所在>/node_modules node scripts/convert-to-webp.mjs
// 直接執行：node scripts/convert-to-webp.mjs
// ============================================================
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const sharp = require('sharp');

const ROOT = path.resolve(import.meta.dirname, '..');
const ASSETS = path.join(ROOT, 'assets');
const QUALITY = 80; // WebP 品質（截圖類 80 已足夠，肉眼無損）

// 遞迴收集所有 PNG 路徑
function walk(dir) {
  let out = [];
  for (const name of fs.readdirSync(dir)) {
    const p = path.join(dir, name);
    const st = fs.statSync(p);
    if (st.isDirectory()) out = out.concat(walk(p));
    else if (/\.png$/i.test(name)) out.push(p);
  }
  return out;
}

const pngs = walk(ASSETS);
let converted = 0, skipped = 0, beforeBytes = 0, afterBytes = 0;

for (const png of pngs) {
  const webp = png.replace(/\.png$/i, '.webp');
  const srcStat = fs.statSync(png);
  // 可重跑：.webp 已存在且不比來源舊 → 略過
  if (fs.existsSync(webp) && fs.statSync(webp).mtimeMs >= srcStat.mtimeMs) { skipped++; continue; }
  await sharp(png).webp({ quality: QUALITY, effort: 6 }).toFile(webp);
  beforeBytes += srcStat.size;
  afterBytes += fs.statSync(webp).size;
  converted++;
}

const mb = b => (b / 1048576).toFixed(2) + 'MB';
console.log(`轉換完成：${converted} 張、略過 ${skipped} 張`);
if (converted) console.log(`來源 PNG：${mb(beforeBytes)} → WebP：${mb(afterBytes)}（縮小 ${(100 - afterBytes / beforeBytes * 100).toFixed(0)}%）`);
