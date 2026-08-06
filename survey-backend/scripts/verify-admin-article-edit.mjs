// 後台「文章編輯入口」端到端驗證腳本（Task 12：整案最終任務——讓管理者能在後台直接
// 改已發布文章的內容，不必再手動 UPDATE 資料庫）。
//
// 驗證項目：
//   1. 歷史文章列表每一列都出現「編輯」按鈕（class=btn-edit，data-id 帶 campaign id）
//   2. 點擊後，編輯器確實載入該筆的「實際值」（比對主旨／內文全文字串，非只檢查非空）
//   3. 編輯模式下，分級（#art-tier）／解鎖點數（#art-cost）／slug（#art-slug）
//      三個唯讀不可改欄位皆被停用（disabled）
//   4. 修改內容後存檔，實際發出的請求是 PUT /api/admin/campaigns/{id}/content
//      （以 page.on('request') 攔截驗證方法與路徑，不只看有沒有出錯）
//   5. 存檔後重新透過 API 讀取該筆，內容確實已更新（直接打後端 API 核對，非只信任畫面）
//   6. 離開編輯模式後，「新建」流程仍走原本的 publish 路徑——這是 D4「零改動」承諾的
//      關鍵驗收：故意在編輯完成後立刻新建一篇，攔截請求確認是 POST /api/admin/campaign/publish
//      （而非誤送 PUT .../content），且新建出來的是一筆全新 campaign（id 不同於被編輯那筆）
//
// 用法（需服務已啟動，指向本機）：
//   ADMIN_BASE=http://127.0.0.1:8080 ADMIN_API_KEY=dev-admin-key node survey-backend/scripts/verify-admin-article-edit.mjs
//   ADMIN_BASE 預設 http://127.0.0.1:8080；ADMIN_API_KEY 預設 dev-admin-key（本機開發預設值）。
//
// ── 可重跑，且不依賴猜測資料庫連線位置 ─────────────────────────────────
// 兩篇測試文章的 slug 都帶執行當下的時間戳（Date.now()），slug 有 UNIQUE 約束，
// 用時間戳可確保每次執行都是全新 slug，天生不會與上一次執行撞號，不需要「先刪除
// 上次殘留」這一步——也因此不需要直接連資料庫（本機這台開發機同時有多個容器與專案
// 共用相近的連線位置，從腳本外部猜「後端真正連的是哪一個」風險很高，猜錯會靜默
// 清到不相干的資料，或者以為清乾淨了其實根本沒清到）。
//
// 跑完後改用「已驗證過的既有端點」盡量收尾：呼叫 DELETE .../publication 把兩篇測試
// 文章下架（從 /r/archive 與 /r/news/{slug} 移除），是 best-effort（失敗只印警告，
// 不影響整體 exit code）；campaign 資料列本身沒有「整列刪除」的 API，會如同其他
// 手動驗證留下的 fixture 一樣留在資料庫（僅為 BASIC、不含真實內容），不影響下次執行。
//
// ── 不可假通過 ──────────────────────────────────────────────────────────
// 每一項斷言失敗都計入 failures 並讓 exit code 非 0；載不到 playwright 一律算失敗，
// 不會只印警告然後 exit 0。

import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

const BASE = process.env.ADMIN_BASE || 'http://127.0.0.1:8080';
const ADMIN_KEY = process.env.ADMIN_API_KEY || 'dev-admin-key';

// ── 測試文章 fixture（slug 帶時間戳，天生不與歷次執行衝突） ────────────
const RUN_ID = Date.now();
const EDIT_SLUG = `admin-article-edit-fixture-${RUN_ID}`;
const EDIT_SUBJECT = `Task12 編輯入口驗證文章（fixture ${RUN_ID}）`;
const ORIGINAL_SENTINEL = 'ARTICLE_EDIT_ORIGINAL_SENTINEL';
const ORIGINAL_MARKDOWN = `# ${EDIT_SUBJECT}\n\n${ORIGINAL_SENTINEL}\n`;
const UPDATED_SUBJECT = EDIT_SUBJECT + '（已修改）';
const UPDATED_SENTINEL = 'ARTICLE_EDIT_UPDATED_SENTINEL';
const UPDATED_MARKDOWN = `# ${UPDATED_SUBJECT}\n\n${UPDATED_SENTINEL}\n`;

// 「離開編輯模式後仍能正常新建」測試用的第二篇文章
const NEWBUILD_SLUG = `admin-article-edit-newbuild-fixture-${RUN_ID}`;
const NEWBUILD_SUBJECT = `Task12 編輯後新建迴歸文章（fixture ${RUN_ID}）`;
const NEWBUILD_MARKDOWN = `# ${NEWBUILD_SUBJECT}\n\nARTICLE_EDIT_NEWBUILD_SENTINEL\n`;

let failed = 0;
/** 記錄一項失敗（不中斷後續案例，讓一次執行就看到所有問題） */
const fail = (msg) => { console.error('FAIL:', msg); failed++; };
const ok = (cond, label) => { if (!cond) fail(label); else console.log(`OK   ${label}`); };

/** 呼叫後台 API；非 2xx 會拋錯，回傳解析後的 JSON */
async function admin(path, opts = {}) {
  const headers = { 'Content-Type': 'application/json', 'X-Admin-Key': ADMIN_KEY, ...(opts.headers || {}) };
  const res = await fetch(BASE + path, { ...opts, headers });
  const text = await res.text();
  if (!res.ok) throw new Error(`${path} → HTTP ${res.status}: ${text}`);
  return text ? JSON.parse(text) : null;
}

/** 收尾用：把測試文章下架（best-effort，失敗只印警告，不影響腳本整體成敗） */
async function bestEffortUnpublish(id, label) {
  if (id == null) return;
  try {
    await admin(`/api/admin/campaigns/${id}/publication`, { method: 'DELETE' });
    console.log(`OK   收尾：已下架 ${label}（批次 #${id}）`);
  } catch (e) {
    console.warn(`WARN 收尾下架 ${label}（批次 #${id}）失敗，不影響本次驗證結果：${e.message}`);
  }
}

/**
 * 動態載入 playwright：先試專案內解析，再逐一嘗試常見的全域安裝目錄。
 * 慣例與既有 verify-admin*.mjs 一致：載不到一律 exit 1，不靜默略過。
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
  const mod = await loadPlaywright();
  const pw = mod.default ?? mod;
  chromium = pw.chromium;
  if (!chromium) throw new Error('載入的 playwright 模組沒有 chromium 匯出');
} catch (e) {
  console.error('FAIL:', e.message);
  process.exit(1);
}

// ── 準備 fixture：用已驗證過的 publish 端點建立一篇已發布 BASIC 文章 ──────
const created = await admin('/api/admin/campaign/publish', {
  method: 'POST',
  body: JSON.stringify({
    subject: EDIT_SUBJECT, markdown: ORIGINAL_MARKDOWN,
    tier: 'BASIC', creditCost: null, slug: EDIT_SLUG, publishedAt: null,
  }),
});
const editCampaignId = created.campaignId;
console.log(`OK   fixture 建立：批次 #${editCampaignId}（slug=${EDIT_SLUG}）`);

const browser = await chromium.launch();
let newBuildCampaignId = null;

try {
  const page = await browser.newPage();

  // 攔截所有請求，記錄方法與路徑，供後面兩個關鍵斷言（PUT /content、POST /publish）使用
  /** @type {{method:string,pathname:string}[]} */
  const requestLog = [];
  page.on('request', (req) => {
    const url = new URL(req.url());
    if (url.pathname.startsWith('/api/admin/')) {
      requestLog.push({ method: req.method(), pathname: url.pathname });
    }
  });
  // 存檔與發布都會跳出 confirm()；一律接受
  page.on('dialog', (dialog) => dialog.accept());

  // ── 登入（金鑰路徑，比照既有慣例） ──────────────────────────────────
  await page.goto(`${BASE}/admin.html`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#gate', { state: 'visible' });
  await page.click('#gate-use-key');
  await page.fill('#gate-key', ADMIN_KEY);
  await page.click('#gate-btn');
  await page.waitForSelector('#app:not([hidden])', { timeout: 15000 });

  // 切到「電子報寄送」分頁（doHistory 已在 init() 跑過，資料已在 DOM 裡）
  await page.click('#tab-campaign');
  await page.waitForSelector('#hist tbody tr', { timeout: 10000 });

  // ── 1. 列表出現「編輯」按鈕 ─────────────────────────────────────────
  const editRow = page.locator('#hist tbody tr', { hasText: EDIT_SUBJECT });
  await editRow.waitFor({ state: 'visible', timeout: 10000 });
  const editButton = editRow.locator('button.btn-edit');
  ok(await editButton.count() === 1, '歷史列表該筆文章的列上有且僅有一顆「編輯」按鈕');
  const dataId = await editButton.getAttribute('data-id');
  ok(dataId === String(editCampaignId), `編輯按鈕 data-id 帶出正確的 campaign id（實際 ${dataId}，預期 ${editCampaignId}）`);
  ok((await editButton.textContent()).trim() === '編輯', '編輯按鈕文字為「編輯」');

  // ── 2. 點擊後載入該筆「實際值」（比對全文字串，不只檢查非空） ─────────
  await editButton.click();
  await page.waitForFunction(
    (expected) => document.getElementById('subject')?.value === expected,
    EDIT_SUBJECT, { timeout: 5000 },
  );
  const loadedSubject = await page.inputValue('#subject');
  const loadedMarkdown = await page.inputValue('#markdown');
  ok(loadedSubject === EDIT_SUBJECT, `編輯器載入的主旨與原文完全相符（實際「${loadedSubject}」）`);
  ok(loadedMarkdown === ORIGINAL_MARKDOWN, `編輯器載入的內文與原文完全相符（實際長度 ${loadedMarkdown.length}，預期 ${ORIGINAL_MARKDOWN.length}）`);

  // ── 3. 編輯模式下計費／識別欄位唯讀或停用 ───────────────────────────
  ok(await page.locator('#art-tier').isDisabled(), '編輯模式下「分級」欄位已停用');
  ok(await page.locator('#art-cost').isDisabled(), '編輯模式下「解鎖點數」欄位已停用');
  ok(await page.locator('#art-slug').isDisabled(), '編輯模式下「slug」欄位已停用');
  ok(await page.locator('#publish-btn').isDisabled(), '編輯模式下「只發布不寄送」按鈕已停用（避免誤建新文章）');
  const sendBtnLabel = (await page.locator('#send-btn').textContent()).trim();
  ok(sendBtnLabel === '儲存內容', `存檔按鈕文字已改為「儲存內容」（實際「${sendBtnLabel}」）`);

  // ── 4. 修改內容後存檔，實際呼叫 PUT /api/admin/campaigns/{id}/content ─
  await page.fill('#subject', UPDATED_SUBJECT);
  await page.fill('#markdown', UPDATED_MARKDOWN);
  requestLog.length = 0; // 只看接下來這次存檔真正送出的請求
  await page.click('#send-btn');
  await page.waitForFunction(
    () => (document.getElementById('send-msg')?.textContent || '').includes('已儲存'),
    null, { timeout: 10000 },
  );
  const contentPutCalls = requestLog.filter(
    (r) => r.method === 'PUT' && r.pathname === `/api/admin/campaigns/${editCampaignId}/content`,
  );
  ok(contentPutCalls.length === 1,
    `存檔實際呼叫 PUT /api/admin/campaigns/${editCampaignId}/content（實際攔到：${JSON.stringify(requestLog)}）`);
  const wrongCalls = requestLog.filter((r) =>
    r.pathname === '/api/admin/campaign/send' || r.pathname === '/api/admin/campaign/publish');
  ok(wrongCalls.length === 0, '存檔過程沒有誤呼叫 send／publish 端點');

  // ── 5. 存檔後重新讀取該筆，內容確實已更新（直接打 API，不信畫面） ────
  const afterSave = await admin('/api/admin/campaigns');
  const savedRow = afterSave.find((c) => c.id === editCampaignId);
  ok(!!savedRow, '存檔後仍能在列表 API 找到該筆 campaign');
  ok(savedRow?.subject === UPDATED_SUBJECT, `後端持久化的主旨已更新為新值（實際「${savedRow?.subject}」）`);
  ok(savedRow?.markdown === UPDATED_MARKDOWN, '後端持久化的內文已更新為新值（全文比對）');
  ok(savedRow?.tier === 'BASIC' && savedRow?.slug === EDIT_SLUG,
    'tier／slug 在內容編輯後維持原樣不受影響（後端安靜忽略這兩欄）');

  // ── 6. 離開編輯模式後檢查狀態已清除 ─────────────────────────────────
  // contentEditCampaignId 是模組內 let（未掛在 window 上），故改用可觀察的畫面狀態驗證：
  // 按鈕文字／欄位啟用狀態全部還原，即代表 exitContentEditMode() 確實跑過。
  const sendBtnAfterSave = (await page.locator('#send-btn').textContent()).trim();
  ok(sendBtnAfterSave === '發送', `存檔成功後存檔按鈕文字已還原為「發送」（實際「${sendBtnAfterSave}」）`);
  ok(!(await page.locator('#publish-btn').isDisabled()), '存檔成功後「只發布不寄送」按鈕已重新啟用');
  ok(!(await page.locator('#art-tier').isDisabled()), '存檔成功後「分級」欄位已重新啟用');
  ok(!(await page.locator('#art-cost').isDisabled()), '存檔成功後「解鎖點數」欄位已重新啟用');
  ok(!(await page.locator('#art-slug').isDisabled()), '存檔成功後「slug」欄位已重新啟用');

  // ── 6b（最關鍵）：離開編輯模式後，新建流程仍走原本的 publish 路徑 ───────
  // 直接沿用目前畫面（未重新整理頁面），模擬「剛編輯完，立刻接著建一篇新文章」
  // 這個最容易踩坑的操作順序——若 contentEditCampaignId 沒被正確清除，這裡會誤送
  // PUT .../content 到「上一篇被編輯的文章」，而不是真的建立一篇新文章。
  await page.fill('#subject', NEWBUILD_SUBJECT);
  await page.fill('#markdown', NEWBUILD_MARKDOWN);
  await page.fill('#art-slug', NEWBUILD_SLUG);
  await page.selectOption('#art-tier', 'BASIC');
  requestLog.length = 0;
  await page.click('#publish-btn');
  await page.waitForFunction(
    () => (document.getElementById('send-msg')?.textContent || '').includes('已發布'),
    null, { timeout: 10000 },
  );
  const publishCalls = requestLog.filter(
    (r) => r.method === 'POST' && r.pathname === '/api/admin/campaign/publish');
  ok(publishCalls.length === 1,
    `編輯後新建走的是 POST /api/admin/campaign/publish（實際攔到：${JSON.stringify(requestLog)}）`);
  const leakedPutCalls = requestLog.filter((r) => r.method === 'PUT' && r.pathname.includes('/content'));
  ok(leakedPutCalls.length === 0,
    `編輯後新建沒有誤送 PUT .../content 到舊文章（實際攔到：${JSON.stringify(requestLog)}）`);

  const afterNewBuild = await admin('/api/admin/campaigns');
  const newBuildRow = afterNewBuild.find((c) => c.slug === NEWBUILD_SLUG);
  ok(!!newBuildRow, '新建的文章確實以新 slug 出現在列表中');
  newBuildCampaignId = newBuildRow?.id ?? null;
  ok(newBuildRow?.id !== editCampaignId, `新建文章是全新的 campaign（id ${newBuildRow?.id}，非被編輯的 #${editCampaignId}）`);
  ok(newBuildRow?.subject === NEWBUILD_SUBJECT, '新建文章的主旨為本次新建輸入值（未被舊編輯內容污染）');
  // 而「被編輯的那一篇」內容仍維持存檔時的更新值，沒有被新建流程覆蓋回去
  const editRowUnchanged = afterNewBuild.find((c) => c.id === editCampaignId);
  ok(editRowUnchanged?.subject === UPDATED_SUBJECT, '被編輯的文章在新建流程後內容維持不變（存檔值持續有效）');

  await page.close();

  console.log(failed === 0 ? '\n全部通過 ✅' : `\n共 ${failed} 項失敗 ❌`);
} catch (e) {
  console.error('FAIL:', e.stack || e.message);
  failed++;
} finally {
  await browser.close();
  // 收尾（best-effort）：把兩篇測試文章下架，減少對 /r/archive 與後台列表的干擾
  await bestEffortUnpublish(editCampaignId, 'fixture');
  await bestEffortUnpublish(newBuildCampaignId, '新建迴歸 fixture');
}

process.exitCode = failed === 0 ? 0 : 1;
