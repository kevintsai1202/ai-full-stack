// 工商 mailto 聯絡中介頁（/promo/c/{id} 的 mailto 分支）離線視覺預覽腳本：不需啟動後端。
// 用法：node survey-backend/scripts/preview-promo-contact.mjs
// 做法：讀模板 → 以樣本資料替換佔位符（同 PromoClickController.respond 的替換邏輯）
//       → 產出 preview HTML → Playwright 以桌機 1280 與手機 375 兩種寬度截圖到
//       survey-backend/target/promo-contact-preview/，並實測「複製」按鈕的成功訊息。
// 需求：playwright（本機為全域安裝：npm i -g playwright）
import { mkdir, readFile, writeFile, cp } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const backendRoot = join(here, '..');
const outDir = join(backendRoot, 'target', 'promo-contact-preview');

/** 動態載入 playwright：先試專案內解析，再逐一嘗試常見的全域安裝目錄（同 preview-promo-page.mjs） */
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
    try {
      return await import(pathToFileURL(join(root, 'playwright', 'index.mjs')).href);
    } catch { /* 下一個 */ }
  }
  console.error('找不到 playwright，請先 npm i -g playwright');
  process.exit(1);
}

const main = async () => {
  const { chromium } = await loadPlaywright();
  await mkdir(outDir, { recursive: true });

  // 佔位符替換成樣本資料（與 controller 相同：title／純信箱／完整 mailto URI）
  let html = await readFile(join(backendRoot, 'src/main/resources/templates/reader/promo-contact.html'), 'utf8');
  html = html
    .replaceAll('<!--PROMO_TITLE-->', 'OpenClaw AI 數位助理')
    .replaceAll('<!--CONTACT_EMAIL-->', 'sales@example.com')
    .replaceAll('<!--MAILTO_HREF-->', 'mailto:sales@example.com?subject=合作洽詢')
    .replace('href="/r/reader.css"', 'href="./reader.css"')
    // 預覽不需要追蹤與導覽腳本（它們打的是線上端點）
    .replace(/<script src="\/[^"]+" defer><\/script>\s*/g, '');
  await cp(join(backendRoot, 'src/main/resources/static/reader/reader.css'), join(outDir, 'reader.css'));
  const page = join(outDir, 'promo-contact-preview.html');
  await writeFile(page, html, 'utf8');

  const browser = await chromium.launch();
  for (const [name, viewport] of [['desktop', { width: 1280, height: 900 }], ['mobile', { width: 375, height: 800 }]]) {
    const ctx = await browser.newContext({ viewport, permissions: ['clipboard-read', 'clipboard-write'] });
    const p = await ctx.newPage();
    await p.goto(pathToFileURL(page).href);
    // 實測複製按鈕：點擊後應出現成功訊息（file:// 下 clipboard API 可能失敗，退路訊息也算有回饋）
    await p.click('#copy-email');
    const msg = await p.textContent('#msg');
    if (!msg || !msg.trim()) {
      console.error('複製按鈕點擊後沒有任何回饋訊息');
      process.exit(1);
    }
    console.log(`[${name}] 複製回饋：${msg.trim()}`);
    await p.screenshot({ path: join(outDir, `promo-contact-${name}.png`), fullPage: true });
    await ctx.close();
    console.log(`已輸出 ${join(outDir, `promo-contact-${name}.png`)}`);
  }
  await browser.close();
};

main().catch((e) => { console.error(e); process.exit(1); });
