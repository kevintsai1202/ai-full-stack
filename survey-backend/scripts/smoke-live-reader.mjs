// 線上讀者站唯讀煙霧測試：部署後確認側欄真的上線，並留一張截圖。
//
// 【唯讀保證】只發 GET、不帶管理金鑰、不建立任何資料——與
// verify-sidebar-and-vote.mjs（會種測試文章與讀者）刻意分開，
// 那支只能對本機驗證庫跑，這支才可以對正式站跑。
//
// 用法：
//   node scripts/smoke-live-reader.mjs
//   node scripts/smoke-live-reader.mjs --base https://springai-survey.zeabur.app
//
// 截圖輸出到 scripts/verify-output/（已 gitignore）。

import { existsSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

const args = process.argv.slice(2);
/** 取具名參數，未給則用預設值 */
function arg(name, fallback) {
  const i = args.indexOf(`--${name}`);
  return i >= 0 ? args[i + 1] : fallback;
}

const BASE = arg('base', 'https://springai-survey.zeabur.app');
const OUT_DIR = 'scripts/verify-output';

let failures = 0;
/** 記錄一項檢查結果 */
function check(name, passed, detail = '') {
  if (passed) console.log(`  ✓ ${name}`);
  else { failures++; console.log(`  ✗ ${name}${detail ? ` —— ${detail}` : ''}`); }
}

/** 動態載入 playwright（本專案無 package.json，作法同 verify-admin.mjs） */
async function loadPlaywright() {
  try { return await import('playwright'); } catch { /* 改找全域 */ }
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
  throw new Error('找不到 playwright；請安裝：npm i -g playwright');
}

mkdirSync(OUT_DIR, { recursive: true });
console.log(`\n=== 線上讀者站煙霧測試（${BASE}）===\n`);

// 從 archive 取第一篇已發布文章，不寫死 slug
console.log('[1] archive 與樣式');
const archive = await fetch(`${BASE}/r/archive`).then((r) => r.text());
const slug = (archive.match(/\/r\/news\/([a-z0-9-]+)/) || [])[1];
check('archive 取得文章 slug', Boolean(slug), '線上可能還沒有已發布文章');
const css = await fetch(`${BASE}/r/reader.css`).then((r) => r.text());
check('reader.css 已含 .article-wrap（新版已上線）', css.includes('.article-wrap'));
check('共用 .wrap 仍是 760px（其他讀者頁未被加寬）',
  css.includes('.wrap { width:min(100% - 36px, 760px)'));

console.log('\n[2] 文章頁側欄');
const article = await fetch(`${BASE}/r/news/${slug}`).then((r) => r.text());
check('輸出側欄容器', article.includes('class="article-side"'));
check('有兩欄版面容器', article.includes('article-layout'));
check('有分類卡', article.includes('>分類</h2>'));
check('分類連結指向 archive 篩選', article.includes('/r/archive?tag='));

console.log('\n[3] 截圖');
const mod = await loadPlaywright();
const chromium = (mod.default ?? mod).chromium;
const browser = await chromium.launch();
try {
  const page = await browser.newPage({ viewport: { width: 1280, height: 1000 } });
  await page.goto(`${BASE}/r/news/${slug}`, { waitUntil: 'networkidle' });
  const side = await page.locator('.article-side').boundingBox();
  const main = await page.locator('.article-main').boundingBox();
  check('側欄在主欄右側', side && main && side.x > main.x + main.width - 10,
    `side.x=${side?.x} main.right=${main && main.x + main.width}`);
  await page.screenshot({ path: `${OUT_DIR}/live-article-sidebar.png`, fullPage: false });
  console.log(`  → ${OUT_DIR}/live-article-sidebar.png`);
} finally {
  await browser.close();
}

console.log(`\n=== 結果：${failures === 0 ? '全部通過' : `${failures} 項失敗`} ===\n`);
process.exit(failures === 0 ? 0 : 1);
