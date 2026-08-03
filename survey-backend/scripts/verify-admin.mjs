// 管理後台 admin.html 端到端驗證腳本（不實際發送）
// 用法：$env:ADMIN_API_KEY="<金鑰>"; node survey-backend/scripts/verify-admin.mjs
// 需求：playwright（本機為全域安裝：npm i -g playwright）
import { mkdir } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

const BASE = process.env.ADMIN_BASE || 'https://springai-survey.zeabur.app';
const KEY = process.env.ADMIN_API_KEY;
if (!KEY) { console.error('請先設定環境變數 ADMIN_API_KEY'); process.exit(1); }

/**
 * 動態載入 playwright：先試專案內解析，再逐一嘗試常見的全域安裝目錄。
 *
 * 原本的靜態 `import { chromium } from 'playwright'` 在 playwright 不在專案
 * node_modules 時直接 ERR_MODULE_NOT_FOUND——這支腳本因此**長期跑不起來**而沒人發現
 * （它不在 mvn test 裡，紅了不會有人看到）。載不到一律 exit 1，不印警告混過去。
 * 作法與 verify-publish-endpoint.mjs / verify-admin-cost-prefill.mjs 一致。
 */
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
    const entry = join(root, 'playwright', 'index.js');
    if (existsSync(entry)) return await import(pathToFileURL(entry).href);
  }
  throw new Error('專案內與常見全域安裝目錄都找不到 playwright；請安裝：npm i -g playwright');
}

let chromium;
try {
  // 全域安裝的 playwright 以 CJS 形式被動態 import 時，具名匯出掛在 default 底下
  const mod = await loadPlaywright();
  const pw = mod.default ?? mod;
  chromium = pw.chromium;
  if (!chromium) throw new Error('載入的 playwright 模組沒有 chromium 匯出');
} catch (e) {
  console.error('FAIL:', e.message);
  process.exit(1);
}

const browser = await chromium.launch();
const page = await browser.newPage();
const fail = (m) => { console.error('FAIL:', m); process.exitCode = 1; };

try {
  // 1. 開頁 → 應出現金鑰閘門
  await page.goto(`${BASE}/admin.html`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#gate', { state: 'visible' });
  if (await page.locator('#app').isVisible()) fail('未驗證前主畫面不應顯示');
  const unauthorized = await page.request.get(`${BASE}/api/admin/survey`);
  if (unauthorized.status() !== 401) fail(`未帶金鑰的問卷 API 應回 401，實際為 ${unauthorized.status()}`);
  console.log('OK 金鑰閘門出現');

  // 2. 輸入金鑰進入 → 主畫面顯示
  await page.fill('#gate-key', KEY);
  await page.click('#gate-btn');
  await page.waitForSelector('#app', { state: 'visible', timeout: 15000 });
  console.log('OK 金鑰正確，進入主畫面');

  // 3. 問卷分析預設顯示，KPI 與原始資料表皆需載入
  await page.waitForFunction(() => /^\d+$/.test(document.querySelector('#kpi-total')?.textContent || ''), null, { timeout: 15000 });
  const total = Number(await page.locator('#kpi-total').textContent());
  const rawRows = await page.locator('#raw-table tbody tr').count();
  if (total > 0 && rawRows < 1) fail('有問卷資料時，完整原始資料表不得為空');
  console.log(`OK 問卷分析載入：${total} 筆`);

  // 4. CSV 匯出 API 必須包含完整 UTM 欄位
  const csv = await page.request.get(`${BASE}/api/admin/survey?format=csv`, { headers: { 'X-Admin-Key': KEY } });
  if (!csv.ok()) fail(`CSV 匯出失敗：HTTP ${csv.status()}`);
  if (!(await csv.text()).includes(',answers,utm,consent,')) fail('CSV 缺少 answers 或 utm 欄位');
  console.log('OK CSV 完整欄位驗證通過');

  // 5. 切換電子報頁籤後，收件數仍可正常載入
  await page.click('#tab-campaign');
  await page.waitForFunction(() => /\d/.test(document.querySelector('#rcount')?.textContent || ''), null, { timeout: 15000 });
  console.log('OK 收件數載入：', await page.locator('#rcount').textContent());

  // 6. 分隔線與付費牆必須插入真正換行，不可把 "\n" 字面值寫進內文
  await page.fill('#markdown', '');
  await page.getByRole('button', { name: '分隔線', exact: true }).click();
  await page.getByRole('button', { name: '付費牆', exact: true }).click();
  const blockMarkdown = await page.inputValue('#markdown');
  if (blockMarkdown !== '---\n\n<!--paywall-->\n\n') {
    fail(`Markdown 區塊插入格式錯誤：${JSON.stringify(blockMarkdown)}`);
  }
  console.log('OK 分隔線與付費牆插入真正換行');

  // 6.5 工商區塊按鈕：選取文字要被成對 promo 標記包成獨立段落
  await page.fill('#markdown', '工商內容');
  await page.locator('#markdown').evaluate((editor) => editor.setSelectionRange(0, editor.value.length));
  await page.getByRole('button', { name: '工商', exact: true }).click();
  const promoMarkdown = await page.inputValue('#markdown');
  if (promoMarkdown !== '<!--promo-->\n\n工商內容\n\n<!--/promo-->\n\n') {
    fail(`工商區塊插入格式錯誤：${JSON.stringify(promoMarkdown)}`);
  }
  console.log('OK 工商區塊插入成對 promo 標記');

  // 7. 同類格式群組：H2 與 Java fenced code 都能在目前選取範圍正確插入
  await page.fill('#markdown', '段落標題');
  await page.locator('.format-group').filter({ hasText: '標題 H' }).locator('summary').click();
  await page.getByRole('button', { name: 'H2　段落標題', exact: true }).click();
  if (await page.inputValue('#markdown') !== '## 段落標題') fail('H2 群組插入格式錯誤');
  await page.fill('#markdown', 'System.out.println("Hello");');
  await page.locator('#markdown').evaluate((editor) => editor.setSelectionRange(0, editor.value.length));
  await page.locator('.format-group').filter({ hasText: '程式碼' }).locator('summary').click();
  await page.getByRole('button', { name: 'Java 區塊', exact: true }).click();
  const codeMarkdown = await page.inputValue('#markdown');
  if (codeMarkdown !== '```java\nSystem.out.println("Hello");\n```\n\n') {
    fail(`Java 程式碼插入格式錯誤：${JSON.stringify(codeMarkdown)}`);
  }
  console.log('OK H1–H3 與多語言程式碼群組可操作');

  // 8. 預覽框要比舊版高且可調整，避免只能在 360px 小視窗內反覆捲動
  const previewStyle = await page.locator('#preview').evaluate((frame) => {
    const style = getComputedStyle(frame);
    return { minHeight: parseFloat(style.minHeight), resize: style.resize };
  });
  if (previewStyle.minHeight < 620 || previewStyle.resize !== 'vertical') {
    fail(`預覽尺寸不符：${JSON.stringify(previewStyle)}`);
  }
  console.log('OK 預覽高度至少 620px 並可垂直拉伸');

  // 9. 撰寫 + 付費牆 + 中繼資料預覽：封面／hashtag／圖片限制都要進入 iframe
  await page.fill('#subject', '驗證用主旨');
  await page.selectOption('#cover-emoji', '🚀');
  const firstTag = page.locator('#campaign-tag-options input').first();
  if (await firstTag.count()) await firstTag.check();
  await page.fill('#markdown',
    '# Hello\n\n![大圖](https://example.com/large.png)\n\n免費內容\n\n'
    + '<!--promo-->\n\n工商卡片內容\n\n<!--/promo-->\n\n<!--paywall-->\n\n付費內容');
  await page.click('#preview-btn');
  await page.waitForFunction(() => {
    const f = document.querySelector('#preview');
    return f && f.srcdoc
      && f.srcdoc.includes('Hello')
      && f.srcdoc.includes('🚀')
      && f.srcdoc.includes('文章 Hashtag')
      && f.srcdoc.includes('max-width:100%')
      && f.srcdoc.includes('付費牆分界')
      && f.srcdoc.includes('付費內容預覽')
      && !f.srcdoc.includes('<!--paywall-->')
      // promo 區塊要渲染成優惠卡片（左側綠條），標記註解不可殘留
      && f.srcdoc.includes('border-left:5px solid #087f72')
      && f.srcdoc.includes('工商卡片內容')
      && !f.srcdoc.includes('<!--promo-->');
  }, null, { timeout: 15000 });
  console.log('OK 封面、Hashtag、響應式圖片、工商卡片與付費牆預覽渲染成功');

  // 10. 問卷卡整條接線：建立問卷 → 加 select 欄 → 設信中一鍵題 → 發布 → 編輯器插入標記 → 預覽斷言
  // 投票卡出現且標示「預覽不計票」。formKey 帶時間戳避免重跑時撞已存在的 409。
  {
    const suffix = Date.now().toString(36).slice(-8);
    const formKey = `verify-survey-${suffix}`;
    const title = `驗證問卷 ${suffix}`;
    const fieldKey = 'pick_topic';
    const fieldLabel = '你想聽哪個主題？';
    const options = ['RAG 知識庫', 'Tool Calling'];

    // 原生 prompt/confirm 對話框佇列：依呼叫順序 accept 對應文字，null 表示單純 confirm。
    const dialogQueue = [];
    page.on('dialog', async (dialog) => {
      const next = dialogQueue.shift();
      if (next === undefined) { await dialog.dismiss(); return; }
      if (next === null) await dialog.accept();
      else await dialog.accept(String(next));
    });

    await page.click('#tab-analytics');
    dialogQueue.push(formKey, title);
    await page.click('#form-create-btn');
    await page.waitForFunction(
      (fk) => [...document.querySelectorAll('#dynamic-form option')].some((o) => o.value === fk),
      formKey, { timeout: 10000 });
    await page.selectOption('#dynamic-form', formKey);
    console.log('OK 建立新問卷：', formKey);

    // 展開簡易欄位設定並新增一個 select 欄位（信中一鍵題只能綁定單選欄位）
    const settingsDetails = page.locator('.schema-settings');
    if (!(await settingsDetails.evaluate((el) => el.open))) {
      await settingsDetails.locator('summary').click();
    }
    await page.fill('#field-key', fieldKey);
    await page.fill('#field-label', fieldLabel);
    await page.selectOption('#field-type', 'select');
    await page.fill('#field-options', options.join('\n'));
    await page.click('#field-add');
    await page.waitForFunction(
      (fk) => [...document.querySelectorAll('#email-vote-field option')].some((o) => o.value === fk),
      fieldKey, { timeout: 10000 });
    console.log('OK 新增 select 欄位：', fieldKey);

    // 新草稿版本不繼承任何信中一鍵題設定，下拉必須從「（未設定）」開始
    if ((await page.inputValue('#email-vote-field')) !== '') {
      fail('新草稿版本的信中一鍵題下拉不應預設繼承任何欄位');
    }
    await page.selectOption('#email-vote-field', fieldKey);
    await page.waitForFunction(
      () => (document.querySelector('#schema-msg')?.textContent || '').includes('已設定信中一鍵題'),
      null, { timeout: 10000 });
    console.log('OK 設定信中一鍵題');

    // 發布目前草稿（會觸發 confirm 對話框）
    dialogQueue.push(null);
    await page.click('#form-publish-version');
    await page.waitForFunction(
      () => (document.querySelector('#schema-msg')?.textContent || '').includes('已發布'),
      null, { timeout: 10000 });
    console.log('OK 發布問卷版本');

    // 編輯器插入標記：先用 API 確認新問卷在可嵌入清單中的實際編號，避免猜測順序
    const embeddableRes = await page.request.get(`${BASE}/api/admin/forms/embeddable`,
      { headers: { 'X-Admin-Key': KEY } });
    const embeddableList = await embeddableRes.json();
    const embedIndex = embeddableList.findIndex((f) => f.formKey === formKey);
    if (embedIndex < 0) fail('新問卷未出現在可嵌入清單中（未發布或未設信中一鍵題？）');

    await page.click('#tab-campaign');
    await page.fill('#markdown', '');
    dialogQueue.push(String(embedIndex + 1));
    await page.click('#insert-survey-btn');
    await page.waitForFunction(
      (fk) => (document.querySelector('#markdown')?.value || '').includes(`<!--survey:${fk}-->`),
      formKey, { timeout: 10000 });
    const markerMarkdown = await page.inputValue('#markdown');
    if (!markerMarkdown.startsWith(`<!--survey:${formKey}-->\n\n`)) {
      fail(`問卷標記插入格式錯誤：${JSON.stringify(markerMarkdown)}`);
    }
    console.log('OK 編輯器插入問卷標記，獨立成段');

    // 預覽：投票卡必須出現、選項文字齊全，且標示「預覽不計票」
    await page.click('#preview-btn');
    await page.waitForFunction(({ label, opts }) => {
      const f = document.querySelector('#preview');
      if (!f || !f.srcdoc) return false;
      return f.srcdoc.includes('預覽不計票') && f.srcdoc.includes(label)
        && opts.every((opt) => f.srcdoc.includes(opt));
    }, { label: fieldLabel, opts: options }, { timeout: 15000 });
    console.log('OK 預覽出現問卷投票卡，含全部選項且標示「預覽不計票」');

    page.removeAllListeners('dialog');
  }

  // 11. 回到分析頁並截圖留存
  await page.click('#tab-analytics');
  await mkdir('output/playwright', { recursive: true });
  await page.screenshot({ path: 'output/playwright/survey-admin-verify.png', fullPage: true });
  console.log('OK 截圖 output/playwright/survey-admin-verify.png（含線上個資，不得提交）');

  console.log('\n全部通過 ✅（未實際發送）');
} catch (e) {
  fail(e.message);
} finally {
  await browser.close();
}
