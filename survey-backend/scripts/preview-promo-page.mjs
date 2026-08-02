// 工商合作頁（/r/promo）離線視覺預覽腳本：不需啟動後端。
// 用法：node survey-backend/scripts/preview-promo-page.mjs
// 做法：讀模板 → 以樣本資料替換佔位符（含四種狀態徽章）→ 產出 preview HTML
//       → Playwright 以桌機 1280 與手機 375 兩種寬度截圖到 survey-backend/target/promo-preview/。
// 需求：playwright（本機為全域安裝：npm i -g playwright）
import { mkdir, readFile, writeFile, cp } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const backendRoot = join(here, '..');
const outDir = join(backendRoot, 'target', 'promo-preview');

/** 動態載入 playwright：先試專案內解析，再逐一嘗試常見的全域安裝目錄（同 verify-admin.mjs） */
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

/** 登入版導覽列（與 ReaderNav.links(true) 一致），用來驗證 /r/promo 圖示不再是方框 */
const NAV = '<a href="/r/">首頁</a><a href="/r/archive">歷史內容</a><a href="/r/me">我的帳戶</a>'
  + '<a href="/r/promo" aria-current="page">工商合作</a><a href="/r/rules">遊戲規則</a>';

/** 我的提案樣本列：涵蓋四種狀態徽章與拒絕備註（與 PromoPortalController.renderProposalRows 輸出結構一致） */
const ROWS = [
  '<tr><td>AI 全端開發實戰課程・早鳥優惠</td><td><span class="promo-status pending">待審核</span></td><td>0/2</td><td>2026-08-02</td></tr>',
  '<tr><td>Spring Boot 進階工作坊</td><td><span class="promo-status approved">已核准</span></td><td>1/3</td><td>2026-07-28</td></tr>',
  '<tr><td>某某產品聯名推廣</td><td><span class="promo-status rejected">已拒絕</span>'
    + '<div class="promo-note">文案與讀者屬性不符，歡迎調整後重新申請</div></td><td>0/1</td><td>2026-07-20</td></tr>',
  '<tr><td>舊活動（已下架）</td><td><span class="promo-status archived">已封存</span></td><td>2/2</td><td>2026-06-30</td></tr>',
].join('');

const main = async () => {
  const { chromium } = await loadPlaywright();
  await mkdir(outDir, { recursive: true });

  // 佔位符替換成樣本資料；資產路徑改成本地相對路徑讓 file:// 也能載入 CSS
  let html = await readFile(join(backendRoot, 'src/main/resources/templates/reader/promo.html'), 'utf8');
  html = html
    .replaceAll('<!--NAV_LINKS-->', NAV)
    .replaceAll('<!--SUBSCRIBER_COUNT-->', '227')
    .replaceAll('<!--UNIT_COST-->', '100')
    .replaceAll('<!--CREDITS-->', '300')
    .replaceAll('<!--PROPOSAL_ROWS-->', ROWS)
    .replace('href="/r/reader.css"', 'href="./reader.css"')
    // 預覽不需要追蹤與導覽腳本（它們打的是線上端點）
    .replace(/<script src="\/[^"]+" defer><\/script>\s*/g, '');
  await cp(join(backendRoot, 'src/main/resources/static/reader/reader.css'), join(outDir, 'reader.css'));
  const page = join(outDir, 'promo-preview.html');
  await writeFile(page, html, 'utf8');

  const browser = await chromium.launch();
  for (const [name, viewport] of [['desktop', { width: 1280, height: 1200 }], ['mobile', { width: 375, height: 1400 }]]) {
    const ctx = await browser.newContext({ viewport });
    const p = await ctx.newPage();
    await p.goto(pathToFileURL(page).href);
    await p.screenshot({ path: join(outDir, `promo-${name}.png`), fullPage: true });
    await ctx.close();
    console.log(`已輸出 ${join(outDir, `promo-${name}.png`)}`);
  }
  await browser.close();
};

main().catch((e) => { console.error(e); process.exit(1); });
