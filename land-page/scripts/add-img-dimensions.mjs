// ============================================================
// 為 index.html 內的本地 <img> 自動注入 width / height 屬性
// 目的：瀏覽器可在圖片載入前依固有比例預留空間，消除版面位移（改善 CLS / Core Web Vitals）
// 特性：可重跑（已含 width 的 <img> 會略過）；僅處理本地 PNG，外部 URL 不動
// 執行：node scripts/add-img-dimensions.mjs
// ============================================================
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '..');
const HTML = path.join(ROOT, 'index.html');

// 讀取 PNG 的固有寬高（從 IHDR chunk）
function pngDim(file) {
  try {
    const b = fs.readFileSync(file);
    if (b.readUInt32BE(0) !== 0x89504e47) return null; // 非 PNG 簽章
    return { w: b.readUInt32BE(16), h: b.readUInt32BE(20) };
  } catch {
    return null;
  }
}

let html = fs.readFileSync(HTML, 'utf8');
let injected = 0;
let skipped = 0;

// 逐一處理 <img ...> 標籤
html = html.replace(/<img\b([^>]*?)(\/?)>/g, (full, attrs, slash) => {
  // 已有 width 屬性 → 略過（維持可重跑）
  if (/\bwidth\s*=/.test(attrs)) { skipped++; return full; }
  const srcMatch = attrs.match(/\bsrc\s*=\s*"([^"]+)"/);
  if (!srcMatch) return full;
  const src = srcMatch[1];
  if (src.startsWith('http') || src.startsWith('data:')) return full; // 外部資源不處理
  const dim = pngDim(path.join(ROOT, src));
  if (!dim) return full;
  injected++;
  // 在 src 屬性後插入 width / height
  const newAttrs = attrs.replace(srcMatch[0], `${srcMatch[0]} width="${dim.w}" height="${dim.h}"`);
  return `<img${newAttrs}${slash}>`;
});

fs.writeFileSync(HTML, html);
console.log(`已注入 width/height 的 <img>：${injected}；略過（已有或外部）：${skipped}`);
