// 電子報「問卷投票卡」與接續填答頁離線視覺預覽腳本：不需啟動後端。
// 用法：node survey-backend/scripts/preview-survey-card.mjs
// 做法：
//   1. email 投票卡：手工重現 SurveyBlockRenderer.renderCard(previewMode=true) 的輸出
//      結構（CARD_OPEN/CARD_CLOSE、標題與選項樣式），因為該類別在後端且無法離線呼叫；
//      若日後改動樣式常數，此腳本需手動同步（見下方常數區註解）。
//   2. 接續頁：讀 templates/reader/survey.html 樣板 → 以樣本 FIELDS_JSON 等佔位符替換
//      （同 SurveyPortalController#survey 的替換邏輯）→ 產出 preview HTML。
// 兩者皆以 Playwright 用桌機 1280 與手機 375 兩種寬度截圖到
// survey-backend/target/survey-preview/，並實測接續頁動態表單依 schema 長出對應
// 數量的 radio 選項（斷言 radio 群數量與樣本選項數一致）。
// 需求：playwright（本機為全域安裝：npm i -g playwright）
import { mkdir, readFile, writeFile, cp } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const backendRoot = join(here, '..');
const outDir = join(backendRoot, 'target', 'survey-preview');

/** 動態載入 playwright：先試專案內解析，再逐一嘗試常見的全域安裝目錄（同 preview-promo-contact.mjs） */
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
 * 手工重現 SurveyBlockRenderer 的問卷卡片樣式常數。
 *
 * 來源：src/main/java/world/springai/survey/newsletter/SurveyBlockRenderer.java
 * 若該檔案的樣式常數（CARD_OPEN/CARD_CLOSE/PREVIEW_BADGE/TITLE_STYLE/LABEL_STYLE/
 * OPTION_STYLE）有變動，這裡需手動同步，否則本預覽會與實際信件外觀失真。
 */
const CARD_OPEN =
  '<table role="presentation" width="100%" cellpadding="0" cellspacing="0"'
  + ' style="margin:28px 0;border-collapse:separate;"><tr>'
  + '<td bgcolor="#eef3fb" style="border:1px solid #b6c9ef;'
  + 'border-left:5px solid #1d4ed8;border-radius:10px;padding:20px 24px;">';
const CARD_CLOSE = '</td></tr></table>';
const PREVIEW_BADGE =
  '<p style="margin:0 0 12px;font-size:12px;font-weight:700;color:#b45309;">預覽不計票</p>';
const TITLE_STYLE = 'margin:0 0 6px;font-size:16px;font-weight:700;color:#1e3a8a;';
const LABEL_STYLE = 'margin:0 0 14px;font-size:14px;color:#334155;';
const OPTION_STYLE =
  'display:inline-block;margin:0 8px 8px 0;padding:8px 16px;border-radius:6px;'
  + 'background:#1d4ed8;color:#ffffff;text-decoration:none;font-size:14px;';

/** 樣本問卷（信中一鍵題）：4 個選項，供卡片渲染與後續接續頁欄位共用同一份定義。 */
const SAMPLE_QUESTION = {
  title: '本週電子報意見調查',
  fieldKey: 'interest_channel',
  label: '你想下一期優先講哪個主題？',
  options: ['RAG 知識庫', 'Tool Calling', 'Agent SDK', '資料庫整合'],
};

/** 依 SurveyBlockRenderer.renderCard 的邏輯手工組出預覽模式（previewMode=true）卡片 HTML。 */
function renderCardHtml(question) {
  const options = question.options
    .map((opt) => `<a href="#" style="${OPTION_STYLE}">${escapeHtml(opt)}</a>`)
    .join('');
  return CARD_OPEN
    + PREVIEW_BADGE
    + `<p style="${TITLE_STYLE}">${escapeHtml(question.title)}</p>`
    + `<p style="${LABEL_STYLE}">${escapeHtml(question.label)}</p>`
    + options
    + CARD_CLOSE;
}

/** 與 SurveyBlockRenderer#escapeHtml 相同的五個 replace，& 最先處理。 */
function escapeHtml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/** 產出可獨立開啟的 email 卡片預覽頁（模擬信件版面寬度 600px）。 */
function buildEmailCardPage(question) {
  return `<!doctype html><html lang="zh-Hant"><head><meta charset="utf-8">
<title>電子報投票卡預覽</title>
<style>body{margin:0;padding:24px;background:#f4f6f9;font-family:"Noto Sans TC","Microsoft JhengHei",sans-serif}
.mail-shell{max-width:600px;margin:0 auto;background:#ffffff;padding:24px;border-radius:8px}</style>
</head><body>
<div class="mail-shell" id="mail-shell">
<p style="margin:0 0 8px;color:#52645f">（模擬電子報內文片段，只截取問卷卡片區塊）</p>
${renderCardHtml(question)}
</div>
</body></html>`;
}

/** 依 SurveyPortalController#survey 的替換邏輯，把樣本資料填入 survey.html 樣板。 */
async function buildContinuationPage(question) {
  let html = await readFile(
    join(backendRoot, 'src/main/resources/templates/reader/survey.html'), 'utf8');
  const fields = [
    {
      key: question.fieldKey, label: question.label, type: 'select',
      required: true, options: question.options,
    },
    {
      key: 'more_feedback', label: '其他想補充的意見（選填）', type: 'long_text',
      required: false, options: [],
    },
  ];
  const replacements = {
    '<!--FORM_TITLE-->': escapeHtml('電子報讀者意見調查'),
    '<!--VOTED_BANNER-->': '',
    '<!--IDENTITY_BLOCK-->':
      '<div class="identity-block" data-identified="true">'
      + '<p>以 <strong>r***@example.com</strong> 身分作答。<a href="#">不是你？</a></p></div>',
    '<!--FIELDS_JSON-->': toJsLiteral(fields),
    '<!--FORM_KEY-->': toJsLiteral('sample-form'),
    '<!--REWARD_CREDITS-->': toJsLiteral(5),
    '<!--CAMPAIGN_ID-->': toJsLiteral(null),
    '<!--RT-->': toJsLiteral(null),
    '<!--VOTED_INDEX-->': toJsLiteral(null),
    '<!--VOTE_FIELD_KEY-->': toJsLiteral(question.fieldKey),
  };
  for (const [key, value] of Object.entries(replacements)) {
    html = html.split(key).join(value);
  }
  return html
    .replace('href="/r/reader.css"', 'href="./reader.css"')
    // 預覽不需要追蹤與導覽腳本（它們打的是線上端點），同 preview-promo-contact.mjs 的做法
    .replace(/<script src="\/[^"]+" defer><\/script>\s*/g, '');
}

/** 序列化成可安全內嵌 <script> 的 JS 字面值，同 SurveyPortalController#toJsLiteral。 */
function toJsLiteral(value) {
  return JSON.stringify(value === undefined ? null : value).replace(/<\//g, '<\\/');
}

const main = async () => {
  const { chromium } = await loadPlaywright();
  await mkdir(outDir, { recursive: true });

  // --- 1. email 投票卡預覽 ---
  const emailPagePath = join(outDir, 'email-card-preview.html');
  await writeFile(emailPagePath, buildEmailCardPage(SAMPLE_QUESTION), 'utf8');

  // --- 2. 接續頁預覽 ---
  await cp(
    join(backendRoot, 'src/main/resources/static/reader/reader.css'),
    join(outDir, 'reader.css'));
  const continuationHtml = await buildContinuationPage(SAMPLE_QUESTION);
  const continuationPagePath = join(outDir, 'survey-continue-preview.html');
  await writeFile(continuationPagePath, continuationHtml, 'utf8');

  const browser = await chromium.launch();
  const viewports = [['desktop', { width: 1280, height: 900 }], ['mobile', { width: 375, height: 800 }]];

  for (const [name, viewport] of viewports) {
    const ctx = await browser.newContext({ viewport });
    const page = await ctx.newPage();
    await page.goto(pathToFileURL(emailPagePath).href);
    // 卡片本身必須含「預覽不計票」標示與全部選項文字，否則代表樣式重現失真
    const shellText = await page.textContent('#mail-shell');
    if (!shellText || !shellText.includes('預覽不計票')) {
      console.error('FAIL: email 投票卡預覽缺少「預覽不計票」標示');
      process.exitCode = 1;
    }
    for (const option of SAMPLE_QUESTION.options) {
      if (!shellText.includes(option)) {
        console.error(`FAIL: email 投票卡預覽缺少選項「${option}」`);
        process.exitCode = 1;
      }
    }
    await page.screenshot({ path: join(outDir, `email-card-${name}.png`), fullPage: true });
    console.log(`已輸出 ${join(outDir, `email-card-${name}.png`)}`);
    await ctx.close();
  }

  for (const [name, viewport] of viewports) {
    const ctx = await browser.newContext({ viewport });
    const page = await ctx.newPage();
    await page.goto(pathToFileURL(continuationPagePath).href);
    // 實測接續頁動態表單依 schema 長出對應數量的 radio（select 型欄位 → radio 群）
    const radioCount = await page.locator(
      `input[type="radio"][name="${SAMPLE_QUESTION.fieldKey}"]`).count();
    if (radioCount !== SAMPLE_QUESTION.options.length) {
      console.error(
        `FAIL: [${name}] radio 群數量應為 ${SAMPLE_QUESTION.options.length}，實際為 ${radioCount}`);
      process.exitCode = 1;
    } else {
      console.log(`OK [${name}] 接續頁 radio 群數量 = ${radioCount}（符合樣本選項數）`);
    }
    const longTextCount = await page.locator('textarea[name="more_feedback"]').count();
    if (longTextCount !== 1) {
      console.error(`FAIL: [${name}] 應長出 1 個 long_text 欄位，實際為 ${longTextCount}`);
      process.exitCode = 1;
    }
    await page.screenshot({ path: join(outDir, `continue-${name}.png`), fullPage: true });
    console.log(`已輸出 ${join(outDir, `continue-${name}.png`)}`);
    await ctx.close();
  }

  await browser.close();
  if (process.exitCode === 1) {
    console.error('\n有斷言失敗 ❌');
  } else {
    console.log('\n全部通過 ✅（離線預覽，未連接後端）');
  }
};

main().catch((e) => { console.error(e); process.exit(1); });
