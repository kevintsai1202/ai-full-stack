// 課程優惠券信件版型離線視覺預覽腳本：不需啟動後端。
// 用法：node survey-backend/scripts/preview-coupon-mail.mjs
// 做法：
//   手工重現 CouponMailRenderer.body(...) 的輸出結構（CARD_OPEN/CARD_CLOSE、
//   標題/文案/優惠碼/按鈕/期限/頁腳樣式），因為該類別在後端且無法離線呼叫；
//   若日後改動樣式常數，此腳本需手動同步（見下方常數區註解，同 preview-survey-card.mjs 慣例）。
// 樣本活動含「期限」與「無期限」兩版，各自以 Playwright 用桌機 1280 與手機 375
// 兩種寬度截圖到 survey-backend/target/coupon-preview/，並斷言畫面含優惠碼文字。
// 需求：playwright（本機為全域安裝：npm i -g playwright）
import { mkdir, writeFile } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const backendRoot = join(here, '..');
const outDir = join(backendRoot, 'target', 'coupon-preview');

/** 動態載入 playwright：先試專案內解析，再逐一嘗試常見的全域安裝目錄（同 preview-survey-card.mjs） */
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

/**
 * 手工重現 CouponMailRenderer 的優惠卡樣式常數。
 *
 * 來源：src/main/java/world/springai/survey/mail/CouponMailRenderer.java
 * 若該檔案的樣式常數（CARD_OPEN/CARD_CLOSE/TITLE_STYLE/PITCH_STYLE/CODE_STYLE/
 * BUTTON_STYLE/EXPIRES_STYLE/FOOTER_STYLE）有變動，這裡需手動同步，否則本預覽會
 * 與實際信件外觀失真——與 CouponMailRenderer 手動同步。
 */
const CARD_OPEN =
  '<table role="presentation" width="100%" cellpadding="0" cellspacing="0"'
  + ' style="margin:24px 0;border-collapse:separate;"><tr>'
  + '<td bgcolor="#fef3c7" style="border:1px solid #fcd34d;'
  + 'border-left:5px solid #d97706;border-radius:10px;padding:20px 24px;">';
const CARD_CLOSE = '</td></tr></table>';
const TITLE_STYLE = 'margin:0 0 8px;font-size:18px;font-weight:700;color:#92400e;';
const PITCH_STYLE = 'margin:0 0 14px;font-size:14px;color:#334155;';
const CODE_STYLE =
  'display:inline-block;margin:0 0 14px;padding:6px 14px;font-family:monospace;'
  + 'font-size:16px;font-weight:700;color:#92400e;border:1px dashed #d97706;'
  + 'border-radius:6px;background:#fffbeb;';
const BUTTON_STYLE =
  'display:inline-block;margin:0 0 10px;padding:10px 22px;border-radius:6px;'
  + 'background:#d97706;color:#ffffff;text-decoration:none;font-size:14px;font-weight:700;';
const EXPIRES_STYLE = 'margin:0;font-size:13px;color:#92400e;';
const FOOTER_STYLE = 'margin:16px 0 0;font-size:12px;color:#64748b;';

/** 與 CouponMailRenderer#escapeHtml 相同的五個 replace，& 最先處理。 */
function escapeHtml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/** 樣本優惠券活動：含期限版，供渲染兩種樣式之一。 */
const SAMPLE_WITH_EXPIRES = {
  courseName: 'AI 賦能全端開發：從零打造企業級智慧應用',
  pitch: '限時讀者專屬優惠，把你在問卷中提到的痛點一次補齊。',
  courseUrl: 'https://example.com/courses/ai-full-stack',
  couponCode: 'AI2026READER',
  expiresAt: '2026-09-30',
  formTitle: '電子報讀者意見調查',
};

/** 樣本優惠券活動：無期限版（expiresAt 為 null，不應出現「優惠至」那行）。 */
const SAMPLE_NO_EXPIRES = {
  ...SAMPLE_WITH_EXPIRES,
  couponCode: 'AI2026NOLIMIT',
  expiresAt: null,
};

/** 依 CouponMailRenderer#body 的邏輯手工組出優惠卡＋頁腳 HTML。 */
function renderBodyHtml(campaign, unsubscribeLink) {
  let html = '';
  html += CARD_OPEN;
  html += `<p style="${TITLE_STYLE}">${escapeHtml(campaign.courseName)}</p>`;
  html += `<p style="${PITCH_STYLE}">${escapeHtml(campaign.pitch)}</p>`;
  html += `<p><code style="${CODE_STYLE}">${escapeHtml(campaign.couponCode)}</code></p>`;
  html += `<p><a href="${escapeHtml(campaign.courseUrl)}" style="${BUTTON_STYLE}">前往課程</a></p>`;
  if (campaign.expiresAt != null) {
    html += `<p style="${EXPIRES_STYLE}">優惠至 ${campaign.expiresAt}</p>`;
  }
  html += CARD_CLOSE;
  html += `<p style="${FOOTER_STYLE}">你收到這封信是因為你填過問卷『${escapeHtml(campaign.formTitle)}』。`
    + `若不想再收到，<a href="${escapeHtml(unsubscribeLink)}">點此取消訂閱</a>。</p>`;
  return html;
}

/** 產出可獨立開啟的 email 優惠卡預覽頁（模擬信件版面寬度 600px）。 */
function buildEmailPage(campaign) {
  const unsubscribeLink = 'https://example.com/r/unsubscribe?token=preview-sample';
  return `<!doctype html><html lang="zh-Hant"><head><meta charset="utf-8">
<title>優惠券信件預覽</title>
<style>body{margin:0;padding:24px;background:#f4f6f9;font-family:"Noto Sans TC","Microsoft JhengHei",sans-serif}
.mail-shell{max-width:600px;margin:0 auto;background:#ffffff;padding:24px;border-radius:8px}</style>
</head><body>
<div class="mail-shell" id="mail-shell">
<p style="margin:0 0 8px;color:#52645f">（模擬電子報內文片段，只截取優惠券卡片區塊）</p>
${renderBodyHtml(campaign, unsubscribeLink)}
</div>
</body></html>`;
}

const main = async () => {
  const { chromium } = await loadPlaywright();
  await mkdir(outDir, { recursive: true });

  const versions = [
    ['with-expires', SAMPLE_WITH_EXPIRES],
    ['no-expires', SAMPLE_NO_EXPIRES],
  ];
  const viewports = [['desktop', { width: 1280, height: 900 }], ['mobile', { width: 375, height: 800 }]];

  const browser = await chromium.launch();

  for (const [versionName, campaign] of versions) {
    const pagePath = join(outDir, `coupon-mail-${versionName}.html`);
    await writeFile(pagePath, buildEmailPage(campaign), 'utf8');

    for (const [viewportName, viewport] of viewports) {
      const ctx = await browser.newContext({ viewport });
      const page = await ctx.newPage();
      await page.goto(pathToFileURL(pagePath).href);

      // 優惠碼文字必須存在，否則代表樣式重現失真或版型脫落
      const shellText = await page.textContent('#mail-shell');
      if (!shellText || !shellText.includes(campaign.couponCode)) {
        console.error(`FAIL: [${versionName}] 優惠卡預覽缺少優惠碼「${campaign.couponCode}」`);
        process.exitCode = 1;
      } else {
        console.log(`OK [${versionName}/${viewportName}] 優惠碼「${campaign.couponCode}」出現在畫面中`);
      }

      // 無期限版不應出現「優惠至」字樣；含期限版必須出現
      const hasExpiresLine = shellText?.includes('優惠至') ?? false;
      if (versionName === 'with-expires' && !hasExpiresLine) {
        console.error(`FAIL: [${versionName}] 含期限版缺少「優惠至」期限行`);
        process.exitCode = 1;
      }
      if (versionName === 'no-expires' && hasExpiresLine) {
        console.error(`FAIL: [${versionName}] 無期限版不應出現「優惠至」期限行`);
        process.exitCode = 1;
      }

      const screenshotPath = join(outDir, `coupon-mail-${versionName}-${viewportName}.png`);
      await page.screenshot({ path: screenshotPath, fullPage: true });
      console.log(`已輸出 ${screenshotPath}`);
      await ctx.close();
    }
  }

  await browser.close();
  if (process.exitCode === 1) {
    console.error('\n有斷言失敗 ❌');
  } else {
    console.log('\n全部通過 ✅（離線預覽，未連接後端）');
  }
};

main().catch((e) => { console.error(e); process.exit(1); });
