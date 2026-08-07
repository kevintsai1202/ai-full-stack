// 讀者接續填答頁（/r/survey/{formKey}）視覺驗證腳本：
// 驗證「一題一卡＋選項膠囊」改版（.q-block / .q-no / .choice-option）有實際渲染，
// 並截 desktop（亮/暗）與 mobile 圖供人眼比對，避免只憑讀 CSS 判斷「應該沒問題」。
//
// 驗證項目：
//   1. 頁面回 200 且 #fields-container 內渲染出至少 5 個 .q-block（課程問卷有 9 題）
//   2. 每個 .q-block 都有 .q-no 題號徽章
//   3. select/multi_select 的 .choice-option 至少 20 顆（九題中多數是選擇題）
//   4. 亮色與暗色主題各截一張 desktop 圖、亮色截一張 mobile（390px）圖
//
// 用法（後端須已在本機啟動；比照 verify-reader-theme.mjs 的慣例）：
//   node survey-backend/scripts/verify-survey-portal-visual.mjs
//   FORM_KEY=other-key node survey-backend/scripts/verify-survey-portal-visual.mjs
//
// 可重跑：只讀頁面與切主題（localStorage），不寫入任何後端狀態。

import { existsSync } from 'node:fs';
import { mkdir } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { pathToFileURL, fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const __dirname = dirname(fileURLToPath(import.meta.url));
const BASE = process.env.READER_BASE || 'http://127.0.0.1:8080';
const FORM_KEY = process.env.FORM_KEY || 'fullstack-course-interest';
const OUT = join(__dirname, '..', 'data', 'survey-portal-render');

// 從 teaching-site 借用已安裝的 playwright（比照 verify-survey-page.mjs 的慣例）
const require = createRequire(join(__dirname, '..', '..', 'teaching-site', 'package.json'));
const { chromium } = require('playwright');

let failures = 0;
/** 統一的 ok/fail 輸出；fail 不中斷，全部跑完再以 exit code 匯總 */
function check(name, condition, detail) {
  if (condition) { console.log(`  OK   ${name}`); }
  else { failures += 1; console.error(`  FAIL ${name}${detail ? ' — ' + detail : ''}`); }
}

const browser = await chromium.launch();
try {
  await mkdir(OUT, { recursive: true });
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  const url = `${BASE}/r/survey/${FORM_KEY}`;
  const res = await page.goto(url, { waitUntil: 'networkidle' });
  check('頁面回應 200', res && res.status() === 200, `實際 ${res && res.status()}｜${url}`);

  // 表單欄位由前端依 schema 動態產生，等第一個 q-block 出現
  await page.waitForSelector('.q-block', { timeout: 5000 }).catch(() => {});
  const qBlocks = await page.locator('.q-block').count();
  const qNos = await page.locator('.q-block .q-no').count();
  const choices = await page.locator('.choice-option').count();
  check('至少 5 張題目卡（.q-block）', qBlocks >= 5, `實際 ${qBlocks}`);
  check('每張題目卡都有題號徽章（.q-no）', qNos === qBlocks, `q-no=${qNos} q-block=${qBlocks}`);
  check('選項膠囊（.choice-option）至少 20 顆', choices >= 20, `實際 ${choices}`);

  // 亮色 desktop
  await page.evaluate(() => { localStorage.setItem('reader-theme', 'light'); });
  await page.reload({ waitUntil: 'networkidle' });
  await page.screenshot({ path: join(OUT, 'survey-desktop-light.png'), fullPage: true });
  // 暗色 desktop：驗 token 翻轉後的膠囊與題卡對比
  await page.evaluate(() => { localStorage.setItem('reader-theme', 'dark'); });
  await page.reload({ waitUntil: 'networkidle' });
  await page.screenshot({ path: join(OUT, 'survey-desktop-dark.png'), fullPage: true });
  // 亮色 mobile：驗選項膠囊收單欄後不破版
  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => { localStorage.setItem('reader-theme', 'light'); });
  await page.reload({ waitUntil: 'networkidle' });
  await page.screenshot({ path: join(OUT, 'survey-mobile-light.png'), fullPage: true });
  console.log(`  OK   截圖已輸出 — ${OUT}`);
} finally {
  await browser.close();
}

if (failures > 0) { console.error(`\n共 ${failures} 項未通過`); process.exit(1); }
console.log('\n問卷填答頁視覺驗證全部通過。');
