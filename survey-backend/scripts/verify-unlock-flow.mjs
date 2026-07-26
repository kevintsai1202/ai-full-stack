// 點數解鎖流程驗證：發布一篇 PREMIUM 文章 → 未解鎖時讀單篇（受限區不得洩漏、
// 須顯示「用 10 點解鎖」）→ POST 解鎖端點（UNLOCKED、餘額 290）→ 再讀單篇
// （受限區應出現）→ 再 POST 一次（ALREADY_UNLOCKED，餘額不再減少）。
//
// 用法（需先啟動應用；預設連本機 8080 與 docker 容器 survey-test-db）：
//   node scripts/verify-unlock-flow.mjs
//   node scripts/verify-unlock-flow.mjs --base http://127.0.0.1:8080
//   node scripts/verify-unlock-flow.mjs --skip-seed          # 資料已備好，只跑 HTTP 斷言
//   node scripts/verify-unlock-flow.mjs --db-container my-pg --db survey
//   node scripts/verify-unlock-flow.mjs --browser            # 追加真實 Chromium 點按驗證
//
// 為何寫成腳本：CLAUDE.md 規定這類流程驗證要可重跑、可逐行檢查，而不是一次性
// 的互動指令。腳本會自己把測試資料 upsert 成已知的起始狀態（餘額 300、
// 未解鎖），所以可以連續重跑而不必手動清資料。
//
// 兩個環境限制與對應做法：
//  1. 沒有「發布文章」的 admin API（AdminCampaignController 只管寄送），
//     所以測試文章直接以 SQL upsert 進 campaign 表。
//  2. magic link 的明文 token 只存在於寄出的信裡（DB 只有 SHA-256 雜湊），
//     腳本無法「收信」完成登入。因此改為用與應用相同的
//     app.reader.jwt-secret 自行簽出 reader_session JWT——驗的是解鎖流程，
//     不是登入流程（登入流程由 verify-reader-flow.mjs 負責）。

import { createHash, createHmac } from 'node:crypto';
import { execFileSync, execSync } from 'node:child_process';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

const args = process.argv.slice(2);
/** 取具名參數，未給則用預設值 */
function arg(name, fallback) {
  const i = args.indexOf(`--${name}`);
  return i >= 0 ? args[i + 1] : fallback;
}

const BASE = arg('base', 'http://127.0.0.1:8080');
const DB_CONTAINER = arg('db-container', 'survey-test-db');
const DB_NAME = arg('db', 'survey');
const DB_USER = arg('db-user', 'postgres');
// 必須與應用的 app.reader.jwt-secret 一致，否則簽出的 cookie 會被判為未登入
const JWT_SECRET = arg('secret', process.env.READER_JWT_SECRET || 'dev-reader-jwt-secret-change-me-32chars');
const SKIP_SEED = args.includes('--skip-seed');
// --browser：額外用真實 Chromium 點一次解鎖按鈕（驗前端腳本，需全域 playwright）
const WITH_BROWSER = args.includes('--browser');

const SLUG = 'e2e-unlock';
const EMAIL = 'e2e-unlock@example.com';
const FREE_TEXT = 'E2E_UNLOCK_FREE_INTRO';
const GATED_TEXT = 'E2E_UNLOCK_SENTINEL_GATED';
const START_CREDITS = 300;
const COST = 10;

let failures = 0;

/** 記錄一項檢查結果 */
function check(name, passed, detail = '') {
  if (passed) {
    console.log(`  ✓ ${name}`);
  } else {
    failures++;
    console.log(`  ✗ ${name}${detail ? ` —— ${detail}` : ''}`);
  }
}

/** 在資料庫容器內執行一段 SQL，回傳 stdout（-tA：無表頭、無對齊，方便直接取值） */
function sql(statement) {
  return execFileSync('docker',
    ['exec', DB_CONTAINER, 'psql', '-U', DB_USER, '-d', DB_NAME, '-tA', '-c', statement],
    { encoding: 'utf8' }).trim();
}

/**
 * 簽出 HS256 的 reader_session JWT。
 * 欄位需與 ReaderSessionService.issueJwt 一致：subject 為 reader id。
 */
function signReaderJwt(readerId) {
  const b64 = (obj) => Buffer.from(JSON.stringify(obj)).toString('base64url');
  const now = Math.floor(Date.now() / 1000);
  const head = b64({ alg: 'HS256' });
  const body = b64({ sub: String(readerId), iat: now, exp: now + 3600 });
  const sig = createHmac('sha256', JWT_SECRET).update(`${head}.${body}`).digest('base64url');
  return `${head}.${body}.${sig}`;
}

/** 取得頁面內容與回應 */
async function fetchPage(path, cookie) {
  const headers = cookie ? { Cookie: cookie } : {};
  const res = await fetch(`${BASE}${path}`, { headers, redirect: 'manual' });
  return { res, body: await res.text() };
}

/** POST 解鎖端點，回傳 { status, data } */
async function postUnlock(cookie) {
  const res = await fetch(`${BASE}/api/reader/unlock/${SLUG}`, {
    method: 'POST',
    headers: cookie ? { Cookie: cookie } : {}
  });
  const text = await res.text();
  let data = null;
  try { data = JSON.parse(text); } catch { /* 非 JSON（例如 401 空 body）時保持 null */ }
  return { status: res.status, data, text };
}

console.log(`\n=== 點數解鎖流程驗證（${BASE}）===\n`);

let readerId;
let campaignId;

// 1. 準備測試資料：一篇已發布的 PREMIUM 文章 + 一位已確認訂閱、餘額 300 的讀者
console.log('[1] 準備測試資料（PREMIUM 文章 + 已訂閱讀者）');
if (SKIP_SEED) {
  readerId = sql(`SELECT id FROM reader WHERE email = '${EMAIL}';`);
  campaignId = sql(`SELECT id FROM campaign WHERE slug = '${SLUG}';`);
  console.log('  ! --skip-seed：沿用既有資料');
} else {
  // 文章：markdown 以 <!--paywall--> 切開免費區與受限區
  const markdown = `${FREE_TEXT}\n\n<!--paywall-->\n\n${GATED_TEXT}`;
  sql(`
    INSERT INTO campaign (subject, markdown, mode, recipient_count, accepted_count, failed_count,
                          status, tier, credit_cost, slug, published_at)
    VALUES ('端到端解鎖測試文章', ${quote(markdown)}, 'now', 1, 1, 0,
            'sent', 'PREMIUM', ${COST}, '${SLUG}', now())
    ON CONFLICT (slug) WHERE slug IS NOT NULL
    DO UPDATE SET markdown = EXCLUDED.markdown, tier = 'PREMIUM',
                  credit_cost = ${COST}, published_at = now();
  `);
  campaignId = sql(`SELECT id FROM campaign WHERE slug = '${SLUG}';`);

  // 讀者：已確認訂閱（名單中心 consent=true）、餘額固定回到 300
  // survey_response.email 沒有 UNIQUE（同一人可能多次填問卷），
  // 因此用 NOT EXISTS 而非 ON CONFLICT，避免每次重跑都多一列
  sql(`
    INSERT INTO survey_response (email, consent)
    SELECT '${EMAIL}', TRUE
     WHERE NOT EXISTS (SELECT 1 FROM survey_response WHERE email = '${EMAIL}');
  `);
  sql(`UPDATE survey_response SET consent = TRUE, unsubscribed = FALSE WHERE email = '${EMAIL}';`);
  sql(`
    INSERT INTO reader (email, credits, referral_code)
    VALUES ('${EMAIL}', ${START_CREDITS}, 'E2EUNLK1')
    ON CONFLICT (email) DO UPDATE SET credits = ${START_CREDITS}, tier = 'FREE', vip_expires_at = NULL;
  `);
  readerId = sql(`SELECT id FROM reader WHERE email = '${EMAIL}';`);

  // 清掉前一次執行留下的解鎖紀錄與帳本，讓每次重跑都從「未解鎖」開始
  sql(`DELETE FROM article_access WHERE reader_id = ${readerId};`);
  sql(`DELETE FROM credit_txn WHERE reader_id = ${readerId};`);
}
check('取得 reader id', !!readerId, String(readerId));
check('取得 campaign id', !!campaignId, String(campaignId));
check('起始餘額為 300', sql(`SELECT credits FROM reader WHERE id = ${readerId};`) === String(START_CREDITS));

const cookie = `reader_session=${signReaderJwt(readerId)}`;

// 2. 未解鎖時讀單篇：受限區不得洩漏，且要看到解鎖按鈕
console.log('\n[2] 未解鎖讀單篇（受限區不得洩漏，須有解鎖按鈕）');
{
  const { res, body } = await fetchPage(`/r/news/${SLUG}`, cookie);
  check('回應 200', res.status === 200, `實際 ${res.status}`);
  check('看得到免費區', body.includes(FREE_TEXT));
  check('★ 回應完全不含受限區', !body.includes(GATED_TEXT), '受限內容洩漏到未解鎖者的回應中');
  check(`顯示「用 ${COST} 點解鎖」`, body.includes(`用 ${COST} 點解鎖`));
  check('有解鎖按鈕', body.includes('id="unlock-btn"'));
  check('gate 區塊附規則頁連結', body.includes('/r/rules'));
}

// 3. 解鎖：扣點並回傳新餘額
console.log('\n[3] POST 解鎖端點');
{
  const { status, data, text } = await postUnlock(cookie);
  check('回應 200', status === 200, `實際 ${status} ${text}`);
  check('outcome 為 UNLOCKED', data?.outcome === 'UNLOCKED', JSON.stringify(data));
  check(`cost 為 ${COST}`, data?.cost === COST, JSON.stringify(data));
  check(`credits 為 ${START_CREDITS - COST}`, data?.credits === START_CREDITS - COST, JSON.stringify(data));
  check('帳本有一筆 READ 扣點', sql(
    `SELECT count(*) FROM credit_txn WHERE reader_id = ${readerId} AND reason = 'READ' AND delta = ${-COST};`) === '1');
}

// 4. 解鎖後讀單篇：受限區必須出現
console.log('\n[4] 解鎖後讀單篇（受限區應出現）');
{
  const { res, body } = await fetchPage(`/r/news/${SLUG}`, cookie);
  check('回應 200', res.status === 200, `實際 ${res.status}`);
  check('★ 受限區已可見', body.includes(GATED_TEXT));
  check('不再顯示解鎖按鈕', !body.includes('id="unlock-btn"'));
}

// 5. 重複解鎖：回 ALREADY_UNLOCKED，且不得再扣點
console.log('\n[5] 重複 POST 解鎖端點（不得重複扣點）');
{
  const { status, data } = await postUnlock(cookie);
  check('回應 200', status === 200, `實際 ${status}`);
  check('outcome 為 ALREADY_UNLOCKED', data?.outcome === 'ALREADY_UNLOCKED', JSON.stringify(data));
  check(`餘額仍為 ${START_CREDITS - COST}`,
    sql(`SELECT credits FROM reader WHERE id = ${readerId};`) === String(START_CREDITS - COST));
  check('帳本仍只有一筆 READ 扣點', sql(
    `SELECT count(*) FROM credit_txn WHERE reader_id = ${readerId} AND reason = 'READ';`) === '1');
}

// 6. 未登入不得解鎖（頁面上看不到按鈕 ≠ 端點不能被呼叫）
console.log('\n[6] 未登入 POST 解鎖端點');
{
  const { status } = await postUnlock(null);
  check('回應 401', status === 401, `實際 ${status}`);
}

// 7. 解鎖端點不可用 GET（GET 會被預抓／連結掃描器觸發而無感扣點）
console.log('\n[7] GET 解鎖端點');
{
  const { res } = await fetchPage(`/api/reader/unlock/${SLUG}`, cookie);
  check('回應 405', res.status === 405, `實際 ${res.status}`);
}

// 8. 真實瀏覽器：確認解鎖按鈕的前端腳本真的能跑（HTTP 斷言驗不到 JS）
//    需要 playwright（本機為全域安裝）；沒有時只警告不算失敗。
if (WITH_BROWSER) {
  console.log('\n[8] 真實瀏覽器點按解鎖按鈕');
  await runBrowserStage();
} else {
  console.log('\n[8] 真實瀏覽器（略過，加 --browser 啟用）');
}

console.log(`\n=== 結果：${failures === 0 ? '全部通過' : `${failures} 項失敗`} ===\n`);
process.exit(failures === 0 ? 0 : 1);

/**
 * 用真實 Chromium 走一次「看到 gate → 按下解鎖 → 頁面重載後看到受限區」。
 *
 * <p>登入方式：直接把一筆 login_token 寫進資料庫（只存 SHA-256 的
 * base64url 雜湊，與 LoginTokenService.hash 一致），再讓瀏覽器走 magic link。
 * 這樣拿到的是應用真正簽發的 httpOnly cookie；httpOnly cookie 無法用 JS 塞，
 * 所以不能像前面幾步那樣自簽 JWT。</p>
 */
async function runBrowserStage() {
  let playwright;
  try {
    // 用 execSync 傳整串指令：Windows 上 npm 是 .cmd 批次檔，
    // execFileSync('npm.cmd', ...) 在 Node 20+ 會因安全性限制回 EINVAL
    const globalRoot = execSync('npm root -g', { encoding: 'utf8' }).trim();
    // playwright 是 CJS，動態 import 會包一層 namespace，chromium 落在 .default 上
    const mod = await import(pathToFileURL(join(globalRoot, 'playwright', 'index.js')).href);
    playwright = mod.default ?? mod;
  } catch (e) {
    console.log(`  ! 找不到 playwright，略過瀏覽器驗證：${e.message}`);
    return;
  }

  // 回到「未解鎖」狀態，讓瀏覽器看得到 gate 區塊
  sql(`DELETE FROM article_access WHERE reader_id = ${readerId};`);
  sql(`DELETE FROM credit_txn WHERE reader_id = ${readerId};`);
  sql(`UPDATE reader SET credits = ${START_CREDITS} WHERE id = ${readerId};`);

  // 寫入一筆已知明文的 magic link token（DB 只存雜湊，故明文由此處決定）
  const rawToken = `e2e-unlock-token-${Date.now()}`;
  const tokenHash = createHash('sha256').update(rawToken, 'utf8').digest('base64url');
  sql(`DELETE FROM login_token WHERE email = '${EMAIL}';`);
  sql(`
    INSERT INTO login_token (token_hash, email, expires_at)
    VALUES (${quote(tokenHash)}, '${EMAIL}', now() + interval '15 minutes');
  `);

  const browser = await playwright.chromium.launch();
  try {
    const page = await browser.newPage();
    // 走 magic link 取得真實 session cookie，並直接被導到文章頁
    await page.goto(
      `${BASE}/api/reader/login/verify?t=${encodeURIComponent(rawToken)}`
      + `&redirect=${encodeURIComponent(`/r/news/${SLUG}`)}`,
      { waitUntil: 'domcontentloaded' });
    check('magic link 導到文章頁', page.url().endsWith(`/r/news/${SLUG}`), page.url());

    const before = await page.content();
    check('★ 按下解鎖前瀏覽器拿到的 HTML 不含受限區', !before.includes(GATED_TEXT));
    check('解鎖按鈕存在', await page.locator('#unlock-btn').count() === 1);

    // 按下解鎖：腳本會 POST 端點，成功後 location.reload()
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'domcontentloaded' }),
      page.click('#unlock-btn')
    ]);

    const after = await page.content();
    check('★ 解鎖後受限區出現（前端腳本可正常運作）', after.includes(GATED_TEXT));
    check('解鎖按鈕已消失', await page.locator('#unlock-btn').count() === 0);
    check('餘額已扣點',
      sql(`SELECT credits FROM reader WHERE id = ${readerId};`) === String(START_CREDITS - COST));
  } finally {
    await browser.close();
  }
}

/** 把字串包成 PostgreSQL 字面值（單引號需重複兩次） */
function quote(text) {
  return `'${text.replace(/'/g, "''")}'`;
}
