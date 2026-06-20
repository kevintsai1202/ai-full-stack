// ============================================================
// 將 index.html 的圖片改用 WebP（PNG 作為後備），並標記首屏 hero 高優先
// 作法：
//   1) 既有 <picture>：在每個 PNG <source>/<img> 前插入對應的 WebP <source>
//   2) 獨立 <img src="*.png">：包成 <picture>，WebP 優先、PNG 後備
//   3) 首屏 hero 圖加 fetchpriority="high" 加速 LCP
// 特性：冪等（已含 type="image/webp" 的 <picture> 會略過，避免重複插入）
// 前置：需先執行 convert-to-webp.mjs 產生對應 .webp 檔
// 執行：node scripts/apply-webp-picture.mjs
// ============================================================
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '..');
const HTML = path.join(ROOT, 'index.html');
const HERO = 'assets/promo/hero-ai-full-stack.png';

let html = fs.readFileSync(HTML, 'utf8');
const webpOf = s => s.replace(/\.png$/i, '.webp');
// 確認對應 webp 實體存在才改寫，避免指向不存在的檔
const hasWebp = src => fs.existsSync(path.join(ROOT, webpOf(src)));

let picturePatched = 0, imgWrapped = 0;
const placeholders = [];

// ── 步驟 1：處理既有 <picture>，補 WebP <source>（並以 placeholder 保護，避免步驟 2 誤包其內部 img）──
html = html.replace(/<picture>[\s\S]*?<\/picture>/g, block => {
  let b = block;
  if (!/type="image\/webp"/.test(b)) {
    // 為每個 <source ... srcset="X.png"> 前插入 webp 版本（保留原 media）
    b = b.replace(/<source([^>]*?)srcset="([^"]+\.png)"([^>]*)>/g, (m, pre, src, post) =>
      hasWebp(src)
        ? `<source${pre}srcset="${webpOf(src)}"${post} type="image/webp" />\n          ${m}`
        : m);
    // 為 <img src="Y.png"> 前插入預設 webp <source>
    b = b.replace(/<img([^>]*?)src="([^"]+\.png)"([^>]*)>/g, (m, pre, src, post) =>
      hasWebp(src)
        ? `<source srcset="${webpOf(src)}" type="image/webp" />\n          ${m}`
        : m);
    if (b !== block) picturePatched++;
  }
  const token = `@@PIC${placeholders.length}@@`;
  placeholders.push(b);
  return token;
});

// ── 步驟 2：包裝剩餘的獨立 <img src="*.png"> ──
html = html.replace(/<img([^>]*?)src="([^"]+\.png)"([^>]*?)>/g, (m, pre, src, post) => {
  if (!hasWebp(src)) return m;
  imgWrapped++;
  return `<picture><source srcset="${webpOf(src)}" type="image/webp" />${m}</picture>`;
});

// ── 還原 placeholder ──
placeholders.forEach((b, i) => { html = html.replace(`@@PIC${i}@@`, b); });

// ── 步驟 3：hero 圖加 fetchpriority="high"（若尚未加）──
const heroImgRe = new RegExp(`<img([^>]*?)src="${HERO.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}"([^>]*?)>`);
html = html.replace(heroImgRe, (m, pre, post) =>
  /fetchpriority/.test(m) ? m : `<img${pre}src="${HERO}"${post} fetchpriority="high">`);

fs.writeFileSync(HTML, html);
console.log(`既有 <picture> 補 WebP：${picturePatched} 個；獨立 <img> 包裝：${imgWrapped} 個`);
console.log(`hero fetchpriority：${/fetchpriority/.test(html) ? '✓ 已套用' : '未套用'}`);
