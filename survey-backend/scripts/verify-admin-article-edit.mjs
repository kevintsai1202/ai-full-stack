// 後台「文章編輯入口」端到端驗證腳本（Task 12：整案最終任務——讓管理者能在後台直接
// 改已發布文章的內容，不必再手動 UPDATE 資料庫）。
//
// 驗證項目：
//   1. 歷史文章列表每一列都出現「編輯」按鈕（class=btn-edit，data-id 帶 campaign id）
//   2. 點擊後，編輯器確實載入該筆的「實際值」（比對主旨／內文全文字串，非只檢查非空）
//   3. 編輯模式下，分級（#art-tier）／解鎖點數（#art-cost）／slug（#art-slug）
//      三個唯讀不可改欄位皆被停用（disabled）
//   4. 進入編輯模式時，既有 hashtag 的勾選狀態被清空（不是靜默沿用畫面殘留狀態）；
//      存檔的 confirm() 對話框本身會依目前勾選狀態明講「將清空」或「將設為 X」；
//      實際存檔後以公開的 /r/archive 頁面驗證 hashtag 確實依「當下勾選」覆蓋（審查修正）
//   5. 修改內容後存檔，實際發出的請求是 PUT /api/admin/campaigns/{id}/content
//      （以 page.on('request') 攔截驗證方法與路徑，不只看有沒有出錯）
//   6. 存檔後重新透過 API 讀取該筆，內容確實已更新（直接打後端 API 核對，非只信任畫面）
//   7. 「取消編輯」按鈕能在不存檔的情況下離開編輯模式，且不會發出任何 PUT 請求、
//      不會改動該文章既有內容（審查修正：編輯模式原本沒有手動出口）
//   8. 離開編輯模式後，「新建」流程仍走原本的 publish 路徑——這是 D4「零改動」承諾的
//      關鍵驗收：故意在編輯完成後立刻新建一篇，攔截請求確認是 POST /api/admin/campaign/publish
//      （而非誤送 PUT .../content），且新建出來的是一筆全新 campaign（id 不同於被編輯那筆）
//
// 用法（需服務已啟動，指向本機）：
//   ADMIN_BASE=http://127.0.0.1:8080 ADMIN_API_KEY=dev-admin-key node survey-backend/scripts/verify-admin-article-edit.mjs
//   ADMIN_BASE 預設 http://127.0.0.1:8080；ADMIN_API_KEY 預設 dev-admin-key（本機開發預設值）。
//
// ── 可重跑，且不依賴猜測資料庫連線位置 ─────────────────────────────────
// 測試文章的 slug 與 hashtag 都帶執行當下的時間戳（Date.now()），有 UNIQUE／唯一性
// 需求的欄位天生不會與上一次執行撞號，不需要「先刪除上次殘留」這一步——也因此不需要
// 直接連資料庫（本機這台開發機同時有多個容器與專案共用相近的連線位置，從腳本外部猜
// 「後端真正連的是哪一個」風險很高，猜錯會靜默清到不相干的資料，或者以為清乾淨了其實
// 根本沒清到——這正是本任務先前踩過的坑）。
//
// hashtag 的實際結果改用公開的 /r/archive 頁面驗證（純 fetch，不需金鑰）：後台既有
// 端點群完全沒有「取得單一文章既有 hashtag」的讀取管道，但已發布文章的 hashtag 會
// 顯示在 /r/archive 的文章卡片上，這是唯一不需猜測資料庫位置就能驗證 tag 是否真的
// 寫入的方式。
//
// 跑完後改用「已驗證過的既有端點」盡量收尾：呼叫 DELETE .../publication 把測試文章
// 下架（從 /r/archive 與 /r/news/{slug} 移除），是 best-effort（失敗只印警告，
// 不影響整體 exit code）；campaign 資料列本身沒有「整列刪除」的 API，會如同其他
// 手動驗證留下的 fixture 一樣留在資料庫（僅為 BASIC、不含真實內容），不影響下次執行。
//
// ── 不可假通過 ──────────────────────────────────────────────────────────
// 每一項斷言失敗都計入 failures 並讓 exit code 非 0；載不到 playwright 一律算失敗，
// 不會只印警告然後 exit 0。confirm() 對話框透過持續存在的單一 page.on('dialog', ...)
// 監聽器攔截（見 setupDialogCapture()），可切換 accept/dismiss 並取回訊息文字，
// 用來驗證「拒絕存檔時看到什麼警告文字」這類案例——單純的全域自動接受做不到這點。

import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

const BASE = process.env.ADMIN_BASE || 'http://127.0.0.1:8080';
const ADMIN_KEY = process.env.ADMIN_API_KEY || 'dev-admin-key';

// ── 測試文章 fixture（slug／hashtag 都帶時間戳，天生不與歷次執行衝突） ──
const RUN_ID = Date.now();
const EDIT_SLUG = `admin-article-edit-fixture-${RUN_ID}`;
const EDIT_SUBJECT = `Task12 編輯入口驗證文章（fixture ${RUN_ID}）`;
const ORIGINAL_SENTINEL = 'ARTICLE_EDIT_ORIGINAL_SENTINEL';
const ORIGINAL_MARKDOWN = `# ${EDIT_SUBJECT}\n\n${ORIGINAL_SENTINEL}\n`;
const ORIGINAL_TAG = `t12orig${RUN_ID}`;
const NEW_TAG = `t12new${RUN_ID}`;
const UPDATED_SUBJECT = EDIT_SUBJECT + '（已修改）';
const UPDATED_SENTINEL = 'ARTICLE_EDIT_UPDATED_SENTINEL';
const UPDATED_MARKDOWN = `# ${UPDATED_SUBJECT}\n\n${UPDATED_SENTINEL}\n`;
// 「取消編輯」測試用：故意打進去、期望被丟棄、不該存進資料庫的內容
const THROWAWAY_SUBJECT = EDIT_SUBJECT + '（取消編輯不該存進去）';

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

/** 抓 /r/archive 完整 HTML（公開頁面，不需金鑰），供比對特定文章卡片是否顯示指定 hashtag */
async function fetchArchiveHtml() {
  const res = await fetch(`${BASE}/r/archive`);
  if (!res.ok) throw new Error(`GET /r/archive → HTTP ${res.status}`);
  return res.text();
}

/** 從整頁 archive HTML 擷取指定 subject 對應的 <article class="article-card">…</article> 區塊 */
function extractArticleCard(html, subject) {
  const idx = html.indexOf(subject);
  if (idx === -1) return null;
  const start = html.lastIndexOf('<article class="article-card">', idx);
  const endTagIdx = html.indexOf('</article>', idx);
  if (start === -1 || endTagIdx === -1) return null;
  return html.slice(start, endTagIdx + '</article>'.length);
}

/**
 * 註冊唯一一次、持續存在的 dialog 監聽器（登入前就掛上），回傳可變狀態物件。
 * 曾試過每次呼叫才用 page.waitForEvent('dialog') 搭配 Promise.all([...,action()])
 * 臨時註冊，結果 click() 觸發的 confirm() 與監聽器註冊之間有競態，導致
 * page.click() 卡滿 30 秒逾時——持續存在的監聽器（與先前版本相同的作法）才穩定。
 */
function setupDialogCapture(page) {
  const state = { accept: true, message: null };
  page.on('dialog', async (dialog) => {
    state.message = dialog.message();
    if (state.accept) await dialog.accept(); else await dialog.dismiss();
  });
  return state;
}

/** 執行會觸發單一 confirm() 對話框的動作：切換監聽器的 accept/dismiss 行為，並取回對話框文字。 */
async function withConfirm(dialogState, action, { accept = true } = {}) {
  dialogState.accept = accept;
  dialogState.message = null;
  await action();
  return dialogState.message;
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

// ── 準備 fixture：用已驗證過的 publish 端點建立一篇已發布 BASIC 文章（帶一個 hashtag） ──
const created = await admin('/api/admin/campaign/publish', {
  method: 'POST',
  body: JSON.stringify({
    subject: EDIT_SUBJECT, markdown: ORIGINAL_MARKDOWN,
    tier: 'BASIC', creditCost: null, slug: EDIT_SLUG, publishedAt: null,
    tags: [ORIGINAL_TAG],
  }),
});
const editCampaignId = created.campaignId;
console.log(`OK   fixture 建立：批次 #${editCampaignId}（slug=${EDIT_SLUG}，hashtag=#${ORIGINAL_TAG}）`);

// 基準檢查：確認 publish 端點真的把 hashtag 寫進去了（否則後面的「清空／覆蓋」比對沒有意義）
{
  const baselineHtml = await fetchArchiveHtml();
  const baselineCard = extractArticleCard(baselineHtml, EDIT_SUBJECT);
  ok(!!baselineCard, 'fixture 建立後能在 /r/archive 找到對應文章卡片');
  ok(!!baselineCard && baselineCard.includes(`#${ORIGINAL_TAG}`),
    `基準檢查：/r/archive 卡片顯示原始 hashtag #${ORIGINAL_TAG}`);
}

const browser = await chromium.launch();
let newBuildCampaignId = null;

try {
  const page = await browser.newPage();
  // dialog 監聽器須在任何互動前就註冊好（見 setupDialogCapture 的說明），避免競態
  const dialogState = setupDialogCapture(page);

  // 攔截所有請求，記錄方法與路徑，供後面幾個關鍵斷言（PUT /content、POST /publish）使用
  /** @type {{method:string,pathname:string}[]} */
  const requestLog = [];
  page.on('request', (req) => {
    const url = new URL(req.url());
    if (url.pathname.startsWith('/api/admin/')) {
      requestLog.push({ method: req.method(), pathname: url.pathname });
    }
  });

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

  // ── 3. 編輯模式下計費／識別欄位唯讀或停用；取消編輯按鈕出現 ───────────
  ok(await page.locator('#art-tier').isDisabled(), '編輯模式下「分級」欄位已停用');
  ok(await page.locator('#art-cost').isDisabled(), '編輯模式下「解鎖點數」欄位已停用');
  ok(await page.locator('#art-slug').isDisabled(), '編輯模式下「slug」欄位已停用');
  ok(await page.locator('#publish-btn').isDisabled(), '編輯模式下「只發布不寄送」按鈕已停用（避免誤建新文章）');
  ok(await page.locator('#cancel-edit-btn').isVisible(), '編輯模式下「取消編輯」出口按鈕已顯示（審查修正）');
  const sendBtnLabel = (await page.locator('#send-btn').textContent()).trim();
  ok(sendBtnLabel === '儲存內容', `存檔按鈕文字已改為「儲存內容」（實際「${sendBtnLabel}」）`);

  // ── 4. 進入編輯模式時，既有 hashtag 勾選狀態已被清空（審查修正核心） ──
  const originalTagCheckbox = page.locator(`#campaign-tag-options input[value="${ORIGINAL_TAG}"]`);
  await originalTagCheckbox.waitFor({ state: 'attached', timeout: 5000 });
  ok(!(await originalTagCheckbox.isChecked()),
    '進入編輯模式後，原有 hashtag 的勾選已被清空（不是靜默沿用畫面殘留狀態）');

  // ── 4b. 不勾任何 hashtag 就嘗試存檔：confirm() 本身要明講「將清空」，且此次先按取消，不真的存檔 ──
  const wipeWarningMessage = await withConfirm(dialogState, () => page.click('#send-btn'), { accept: false });
  ok(/hashtag/i.test(wipeWarningMessage) && /清空/.test(wipeWarningMessage),
    `未勾選任何 hashtag 時，存檔確認框本身明講將清空既有 hashtag（實際訊息：「${wipeWarningMessage}」）`);
  // 取消後應仍停留在編輯模式（沒有被誤判成功）
  ok((await page.locator('#send-btn').textContent()).trim() === '儲存內容',
    '拒絕「將清空 hashtag」的確認框後，仍停留在編輯模式（未被誤判為已存檔）');

  // ── 5. 勾選新 hashtag、修改內容後存檔，confirm() 要列出即將覆蓋的 hashtag，
  //      實際發出的請求是 PUT /api/admin/campaigns/{id}/content ──────────
  await page.fill('#campaign-custom-tag', NEW_TAG);
  await page.click('#campaign-add-tag');
  await page.locator(`#campaign-tag-options input[value="${NEW_TAG}"]`).waitFor({ state: 'attached', timeout: 5000 });
  ok(await page.locator(`#campaign-tag-options input[value="${NEW_TAG}"]`).isChecked(),
    '新增自訂 hashtag 後立即勾選');

  await page.fill('#subject', UPDATED_SUBJECT);
  await page.fill('#markdown', UPDATED_MARKDOWN);
  requestLog.length = 0; // 只看接下來這次存檔真正送出的請求
  const saveWarningMessage = await withConfirm(dialogState, () => page.click('#send-btn'), { accept: true });
  ok(/hashtag/i.test(saveWarningMessage) && saveWarningMessage.includes(`#${NEW_TAG}`),
    `有勾選 hashtag 時，存檔確認框明講即將設為哪些 hashtag（實際訊息：「${saveWarningMessage}」）`);
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

  // ── 6. 存檔後重新讀取該筆，內容確實已更新（直接打 API，不信畫面） ────
  const afterSave = await admin('/api/admin/campaigns');
  const savedRow = afterSave.find((c) => c.id === editCampaignId);
  ok(!!savedRow, '存檔後仍能在列表 API 找到該筆 campaign');
  ok(savedRow?.subject === UPDATED_SUBJECT, `後端持久化的主旨已更新為新值（實際「${savedRow?.subject}」）`);
  ok(savedRow?.markdown === UPDATED_MARKDOWN, '後端持久化的內文已更新為新值（全文比對）');
  ok(savedRow?.tier === 'BASIC' && savedRow?.slug === EDIT_SLUG,
    'tier／slug 在內容編輯後維持原樣不受影響（後端安靜忽略這兩欄）');

  // ── 6b. hashtag 實際結果：透過 /r/archive 卡片驗證「當下勾選」確實覆蓋了原標籤 ──
  {
    const afterSaveHtml = await fetchArchiveHtml();
    const afterSaveCard = extractArticleCard(afterSaveHtml, UPDATED_SUBJECT);
    ok(!!afterSaveCard, '存檔後能在 /r/archive 找到更新後主旨對應的文章卡片');
    ok(!!afterSaveCard && afterSaveCard.includes(`#${NEW_TAG}`),
      `★ /r/archive 卡片顯示存檔時勾選的新 hashtag #${NEW_TAG}`);
    ok(!!afterSaveCard && !afterSaveCard.includes(`#${ORIGINAL_TAG}`),
      `★ /r/archive 卡片不再顯示原始 hashtag #${ORIGINAL_TAG}（已被覆蓋，而非疊加保留）`);
  }

  // ── 7. 離開編輯模式後檢查狀態已清除 ─────────────────────────────────
  // contentEditCampaignId 是模組內 let（未掛在 window 上），故改用可觀察的畫面狀態驗證：
  // 按鈕文字／欄位啟用狀態全部還原，即代表 exitContentEditMode() 確實跑過。
  const sendBtnAfterSave = (await page.locator('#send-btn').textContent()).trim();
  ok(sendBtnAfterSave === '發送', `存檔成功後存檔按鈕文字已還原為「發送」（實際「${sendBtnAfterSave}」）`);
  ok(!(await page.locator('#publish-btn').isDisabled()), '存檔成功後「只發布不寄送」按鈕已重新啟用');
  ok(!(await page.locator('#art-tier').isDisabled()), '存檔成功後「分級」欄位已重新啟用');
  ok(!(await page.locator('#art-cost').isDisabled()), '存檔成功後「解鎖點數」欄位已重新啟用');
  ok(!(await page.locator('#art-slug').isDisabled()), '存檔成功後「slug」欄位已重新啟用');
  ok(!(await page.locator('#cancel-edit-btn').isVisible()), '存檔成功後「取消編輯」按鈕已重新隱藏');

  // ── 8.「取消編輯」出口：重新進入編輯模式、打入不該被保留的內容、按取消 ──
  // 目的：驗證審查 Finding 2——編輯模式必須有不必成功存檔就能離開的手動出口，
  // 且取消後不得發出任何 PUT、不得改動該文章既有內容。
  await page.click('#tab-campaign');
  const editRowAgain = page.locator('#hist tbody tr', { hasText: UPDATED_SUBJECT });
  await editRowAgain.waitFor({ state: 'visible', timeout: 10000 });
  await editRowAgain.locator('button.btn-edit').click();
  await page.waitForFunction(
    (expected) => document.getElementById('subject')?.value === expected,
    UPDATED_SUBJECT, { timeout: 5000 },
  );
  await page.fill('#subject', THROWAWAY_SUBJECT);
  requestLog.length = 0;
  await withConfirm(dialogState, () => page.click('#cancel-edit-btn'), { accept: true });
  await page.waitForFunction(
    () => (document.getElementById('send-btn')?.textContent || '').trim() === '發送',
    null, { timeout: 5000 },
  );
  ok(!(await page.locator('#art-tier').isDisabled()), '取消編輯後「分級」欄位重新啟用');
  ok(!(await page.locator('#cancel-edit-btn').isVisible()), '取消編輯後「取消編輯」按鈕重新隱藏');
  const putCallsDuringCancel = requestLog.filter((r) => r.method === 'PUT' && r.pathname.includes('/content'));
  ok(putCallsDuringCancel.length === 0, '取消編輯過程沒有發出任何 PUT .../content 請求');
  const afterCancel = await admin('/api/admin/campaigns');
  const rowAfterCancel = afterCancel.find((c) => c.id === editCampaignId);
  ok(rowAfterCancel?.subject === UPDATED_SUBJECT,
    `★ 取消編輯後，該文章主旨仍是存檔時的值，未被畫面上尚未存檔的內容覆蓋（實際「${rowAfterCancel?.subject}」）`);

  // ── 8b（最關鍵）：離開編輯模式後，新建流程仍走原本的 publish 路徑 ───────
  // 直接沿用目前畫面（未重新整理頁面），模擬「剛編輯完，立刻接著建一篇新文章」
  // 這個最容易踩坑的操作順序——若 contentEditCampaignId 沒被正確清除，這裡會誤送
  // PUT .../content 到「上一篇被編輯的文章」，而不是真的建立一篇新文章。
  await page.fill('#subject', NEWBUILD_SUBJECT);
  await page.fill('#markdown', NEWBUILD_MARKDOWN);
  await page.fill('#art-slug', NEWBUILD_SLUG);
  await page.selectOption('#art-tier', 'BASIC');
  requestLog.length = 0;
  await withConfirm(dialogState, () => page.click('#publish-btn'), { accept: true });
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
