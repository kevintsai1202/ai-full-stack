// 「只發布不寄送」端點端到端驗收：POST /api/admin/campaign/publish
//
// 目的：證明這條端點真的解掉它要解的問題——PREMIUM 文章可以純粹用後台 API 上線，
// 而且上線後的 paywall 在 HTTP 層次真的守得住。單元測試驗不到這一整條路徑
// （建立 → 網頁渲染 → 授權判斷 → 扣點 → archive → 後台歷史列表）。
//
// 驗到的性質：
//   ① 用新端點發布一篇 PREMIUM 文章（含 <!--paywall-->），回應帶正確的公開網址
//   ② 未登入讀者開 /r/news/{slug} → **HTTP 回應本文不含受限段落**（直接檢查回應字串）
//   ③ 已登入、餘額足夠 → 看到解鎖按鈕（CAN_UNLOCK），**回應本文仍不含受限段落**
//   ④ 按下解鎖 → 扣點、受限段落出現、credit_txn 有一筆 READ
//   ⑤ 跑完後「餘額 == credit_txn 總和」仍成立（直接查資料庫驗算）
//   ⑥ 該文章出現在 /r/archive
//   ⑦ 後台歷史列表不把它顯示成失敗的群發（mode=publish、status=published、寄送統計全 0）
//   ⑧ 守門仍在：同一篇 PREMIUM 用 /api/admin/campaign/send 寄送必須被拒（400）
//   ⑨ 缺 slug 回 400、未帶金鑰回 401、重複 slug 回 400
//   ⑩ PREMIUM 缺 <!--paywall--> 標記（含大小寫打錯）一律 400——否則頁面說「解鎖 N 點」
//      而 ContentSplitter 把全文當免費區，未登入訪客整篇免費拿走
//   ⑪ 下架（DELETE /api/admin/campaigns/{id}/publication）：從 archive 與單篇頁消失，
//      但已解鎖者的 article_access 與 credit_txn 完整保留（帳本只增不改）；
//      未帶金鑰 401、狀態非 published 回 409、已寄過信回 409
//
// 用法（需服務已啟動；預設連本機 8080 與 docker 容器 survey-test-db）：
//   $env:ADMIN_API_KEY="<金鑰>"; node survey-backend/scripts/verify-publish-endpoint.mjs
//   node survey-backend/scripts/verify-publish-endpoint.mjs --base http://127.0.0.1:8081
//   node survey-backend/scripts/verify-publish-endpoint.mjs --browser   # 追加真實 Chromium
//
// ── 可重跑 ──────────────────────────────────────────────────────────────
// 測試身分是固定的 .invalid email（RFC 2606 保留網域，不可能對應真人），
// 測試文章是固定的兩個 slug。腳本開頭把這些 fixture 刪除重建成已知起點，
// 所以可以連續執行兩次都通過，也不會每跑一次就在資料庫累積新資料。
// （slug 有 UNIQUE 約束，這裡必須刪除重建而不能沿用——端點本身會擋重複 slug，
//   那正是第 ⑨ 項要驗的行為。）
//
// ── 不可假通過 ──────────────────────────────────────────────────────────
// 每一項斷言失敗都計入 failures 並讓 exit code 非 0；還原步驟本身也檢查回傳值。
// --browser 模式載不到 playwright 一律算失敗，不會只印警告然後 exit 0。
//
// ── 既有資料的保護 ──────────────────────────────────────────────────────
// 沒有 DELETE 無 WHERE、沒有 TRUNCATE、沒有 DROP。所有刪除都限定在
// 「本腳本自己建立的固定 slug 與白名單 .invalid email」上，且 email 先過
// SAFE_EMAIL 正則，不符就中止整個腳本（不是略過）。腳本輸出不含任何真實 email。

import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
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
const ADMIN_KEY = process.env.ADMIN_API_KEY;
const WITH_BROWSER = args.includes('--browser');

if (!ADMIN_KEY) {
  console.error('請先設定環境變數 ADMIN_API_KEY');
  process.exit(1);
}

// ── 測試身分與測試文章 ──────────────────────────────────────────────────
/** 只有符合這個式樣的 email 允許被 resetFixtures 刪除 */
const SAFE_EMAIL = /^publish-[a-z]+@example\.invalid$/;
const READER_EMAIL = 'publish-reader@example.invalid';
/** 主測試文章（PREMIUM）與「重複 slug」測試共用同一個 slug */
const SLUG = 'publish-endpoint-premium';
/** 第二篇（BASIC）：驗 BASIC 也能走這條端點、且會出現在 archive */
const BASIC_SLUG = 'publish-endpoint-basic';
const SUBJECT = '只發布不寄送端到端測試文章';
const FREE = 'PUBLISH_E2E_FREE_INTRO';
/** 受限區哨兵：出現在不該出現的回應裡就是外洩 */
const GATED = 'PUBLISH_E2E_SENTINEL_GATED';
const COST = 12;
/** 瀏覽器模式那篇的主旨（歷史列表要靠它找到對應的列來按「下架」） */
const UI_SUBJECT = '只發布不寄送（後台 UI）';
/** markdown：以 <!--paywall--> 切開免費區與受限區 */
const MARKDOWN = `# ${SUBJECT}\n\n${FREE}\n\n<!--paywall-->\n\n${GATED}\n`;

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
/** 斷言相等（一律以字串比較，避免 psql 回傳字串與 JS 數字比不起來） */
function eq(actual, expected, name) {
  check(`${name} = ${expected}`, String(actual) === String(expected), `實際 ${actual}`);
}

/** 在資料庫容器內執行 SQL，回傳 stdout（-tA：無表頭、無對齊） */
function sql(statement) {
  return execFileSync('docker',
    ['exec', DB_CONTAINER, 'psql', '-U', DB_USER, '-d', DB_NAME, '-tA', '-c', statement],
    { encoding: 'utf8' }).trim();
}
/** 把字串包成 PostgreSQL 字面值（單引號需重複兩次） */
function quote(text) {
  return `'${String(text).replace(/'/g, "''")}'`;
}

/** 呼叫後台 API；非 2xx 不拋錯，回 {status, body} 供呼叫端檢查。key=null 代表不帶金鑰 */
async function admin(path, opts = {}, key = ADMIN_KEY) {
  const headers = { 'Content-Type': 'application/json' };
  if (key) headers['X-Admin-Key'] = key;
  const res = await fetch(BASE + path, { ...opts, headers });
  const text = await res.text();
  let body = text;
  try { body = text ? JSON.parse(text) : null; } catch { /* 非 JSON 原樣保留 */ }
  return { status: res.status, body };
}

/** 取頁面內容（cookie 選填），不自動跟隨轉址 */
async function page(path, cookie) {
  const res = await fetch(BASE + path, {
    headers: cookie ? { Cookie: cookie } : {},
    redirect: 'manual',
  });
  return { res, body: await res.text() };
}

/** POST 解鎖端點 */
async function postUnlock(cookie, slug = SLUG) {
  const res = await fetch(`${BASE}/api/reader/unlock/${slug}`, {
    method: 'POST', headers: cookie ? { Cookie: cookie } : {},
  });
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { /* 非 JSON 時保持 null */ }
  return { status: res.status, data, text };
}

/**
 * 以 magic link 完成一次真實登入，回傳 reader_session cookie。
 *
 * 明文 token 只存在於寄出的信裡（DB 只有 SHA-256 雜湊），腳本收不到信，
 * 因此自己寫一筆已知明文的 login_token 再走 verify 端點——拿到的是應用
 * 真正簽發的 httpOnly cookie，走的也是正式登入路徑。
 */
async function loginAs(email) {
  const raw = `publish-e2e-${email}-${Date.now()}`;
  const hash = createHash('sha256').update(raw, 'utf8').digest('base64url');
  sql(`DELETE FROM login_token WHERE lower(email) = ${quote(email)};`);
  sql(`INSERT INTO login_token (token_hash, email, expires_at)
       VALUES (${quote(hash)}, ${quote(email)}, now() + interval '15 minutes');`);
  const res = await fetch(
    `${BASE}/api/reader/login/verify?t=${encodeURIComponent(raw)}&redirect=%2Fr%2Fme`,
    { redirect: 'manual' });
  const setCookie = res.headers.get('set-cookie') || '';
  const match = setCookie.match(/reader_session=([^;]+)/);
  return { status: res.status, cookie: match ? `reader_session=${match[1]}` : null };
}

/** 送出訂閱（等同 /r/ 訂閱頁的 POST） */
async function subscribe(email) {
  const res = await fetch(`${BASE}/api/survey`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, consent: true, source: 'newsletter' }),
  });
  return res.status;
}

/**
 * 重設 fixture：刪掉本腳本的測試文章與測試讀者，讓每次執行都從相同起點開始。
 *
 * 文章非刪不可：slug 有 UNIQUE 約束，而本腳本要驗的正是「用端點建立一篇新文章」。
 * 沿用舊列就只能驗到「重複 slug 被擋」，驗不到成功路徑。
 * 讀者也一併刪除，讓餘額由帳本重新累積——否則會像 verify-unlock-flow.mjs 那樣
 * 在資料庫留下一列「餘額 != 帳本總和」的假資料，讓第 ⑤ 項不變式失去意義。
 */
function resetFixtures() {
  if (!SAFE_EMAIL.test(READER_EMAIL)) {
    console.error('測試 email 不符白名單，中止（避免誤刪真實資料）');
    process.exit(1);
  }
  // 文章：先刪依賴它的 article_access，再刪 campaign（email_log 一律沒有，因為不寄信）
  for (const slug of [SLUG, BASIC_SLUG]) {
    sql(`DELETE FROM article_access
          WHERE campaign_id IN (SELECT id FROM campaign WHERE slug = ${quote(slug)});`);
    sql(`DELETE FROM email_log
          WHERE campaign_id IN (SELECT id FROM campaign WHERE slug = ${quote(slug)});`);
    sql(`DELETE FROM campaign WHERE slug = ${quote(slug)};`);
  }
  // 讀者：以先查出的 id 逐一刪除，不以模糊條件批次刪
  const ids = sql(`SELECT id FROM reader WHERE lower(email) = ${quote(READER_EMAIL)};`)
    .split('\n').map(s => s.trim()).filter(Boolean);
  for (const id of ids) {
    if (!/^\d+$/.test(id)) continue;
    sql(`DELETE FROM article_access WHERE reader_id = ${id};`);
    sql(`DELETE FROM credit_txn WHERE reader_id = ${id};`);
    sql(`DELETE FROM reader WHERE id = ${id};`);
  }
  sql(`DELETE FROM login_token WHERE lower(email) = ${quote(READER_EMAIL)};`);
  sql(`DELETE FROM survey_response WHERE lower(email) = ${quote(READER_EMAIL)};`);
  return ids.length;
}

/**
 * 核心不變式：reader.credits 必須等於該讀者 credit_txn 的 delta 總和。
 *
 * 直接查資料庫而不是看 API 回應——API 回的就是 reader.credits 本身，
 * 用它來驗證 reader.credits 等於帳本總和是循環論證。
 */
function checkLedgerInvariant(label, email) {
  const row = sql(`
    SELECT r.credits, COALESCE(SUM(t.delta), 0)
      FROM reader r LEFT JOIN credit_txn t ON t.reader_id = r.id
     WHERE lower(r.email) = ${quote(email)}
     GROUP BY r.credits;`);
  const [balance, sum] = row.split('|');
  check(`★ ${label} 餘額 == credit_txn 總和（${balance} == ${sum}）`,
    balance !== undefined && balance === sum, `餘額 ${balance} vs 帳本 ${sum}`);
}

console.log(`\n=== 只發布不寄送端點驗收（${BASE}）===\n`);

/** 還原用：是否曾為測試讀者加點 */
let grantedCredits = 0;

try {
  // ── [0] 前置：重設 fixture ─────────────────────────────────────────────
  console.log('[0] 前置：重設 fixture（僅限固定 slug 與白名單 .invalid 讀者）');
  const removed = resetFixtures();
  console.log(`  ! 已清除 ${removed} 位測試讀者與 2 篇測試文章的舊資料`);
  eq(sql(`SELECT count(*) FROM campaign WHERE slug = ${quote(SLUG)};`), 0, '起始時測試文章列數');

  // ── [1] 權限與必填驗證（先驗擋下的情況，避免佔用 slug）───────────────
  console.log('\n[1] 權限與必填驗證');
  {
    const noKey = await admin('/api/admin/campaign/publish',
      { method: 'POST', body: JSON.stringify({ subject: SUBJECT, markdown: MARKDOWN, slug: SLUG }) },
      null);
    eq(noKey.status, 401, '★ 未帶 X-Admin-Key 的回應碼');

    const badKey = await admin('/api/admin/campaign/publish',
      { method: 'POST', body: JSON.stringify({ subject: SUBJECT, markdown: MARKDOWN, slug: SLUG }) },
      'definitely-not-the-key');
    eq(badKey.status, 401, '★ 金鑰錯誤的回應碼');

    const noSlug = await admin('/api/admin/campaign/publish', {
      method: 'POST',
      body: JSON.stringify({ subject: SUBJECT, markdown: MARKDOWN, tier: 'PREMIUM', creditCost: COST }),
    });
    eq(noSlug.status, 400, '★ 缺 slug 的回應碼');

    const badTier = await admin('/api/admin/campaign/publish', {
      method: 'POST',
      body: JSON.stringify({ subject: SUBJECT, markdown: MARKDOWN, tier: 'PREMIUN', creditCost: COST, slug: SLUG }),
    });
    eq(badTier.status, 400, '★ 未知 tier（打錯字）的回應碼');

    // ★ PREMIUM 卻沒有 <!--paywall--> 標記：頁面會標示「進階／解鎖 N 點」而
    // ContentSplitter 把全文都當免費區，未登入訪客直接拿到整篇。必須在入口擋下。
    const noMarker = await admin('/api/admin/campaign/publish', {
      method: 'POST',
      body: JSON.stringify({
        subject: SUBJECT, markdown: `# ${SUBJECT}\n\n${FREE}\n\n${GATED}\n`,
        tier: 'PREMIUM', creditCost: COST, slug: SLUG,
      }),
    });
    eq(noMarker.status, 400, '★ PREMIUM 缺 <!--paywall--> 標記的回應碼');

    // 大小寫打錯（ContentSplitter 是大小寫敏感的精確比對）等同沒有受限區
    const upperMarker = await admin('/api/admin/campaign/publish', {
      method: 'POST',
      body: JSON.stringify({
        subject: SUBJECT, markdown: `${FREE}\n\n<!--PAYWALL-->\n\n${GATED}\n`,
        tier: 'PREMIUM', creditCost: COST, slug: SLUG,
      }),
    });
    eq(upperMarker.status, 400, '★ PREMIUM 標記打成大寫的回應碼');

    const zeroCost = await admin('/api/admin/campaign/publish', {
      method: 'POST',
      body: JSON.stringify({ subject: SUBJECT, markdown: MARKDOWN, tier: 'PREMIUM', creditCost: 0, slug: SLUG }),
    });
    eq(zeroCost.status, 400, 'PREMIUM 但 creditCost=0 的回應碼');

    // 以上全部應被擋下，一列都不該寫進資料庫
    eq(sql(`SELECT count(*) FROM campaign WHERE slug = ${quote(SLUG)};`), 0,
      '★ 被擋下的請求未寫入任何 campaign');
  }

  // ── [2] 發布一篇 PREMIUM 文章 ─────────────────────────────────────────
  console.log('\n[2] 用新端點發布 PREMIUM 文章');
  let campaignId;
  {
    const r = await admin('/api/admin/campaign/publish', {
      method: 'POST',
      body: JSON.stringify({
        subject: SUBJECT, markdown: MARKDOWN, tier: 'PREMIUM', creditCost: COST, slug: SLUG,
      }),
    });
    eq(r.status, 200, '★ PREMIUM 發布回應碼（send 端點會回 400，這條必須放行）');
    campaignId = r.body && r.body.campaignId;
    check('回應含 campaignId', !!campaignId, JSON.stringify(r.body));
    eq(r.body && r.body.tier, 'PREMIUM', '回應 tier');
    eq(r.body && r.body.creditCost, COST, '回應 creditCost');
    eq(r.body && r.body.slug, SLUG, '回應 slug');
    check(`回應含公開網址（結尾 /r/news/${SLUG}）`,
      typeof r.body?.url === 'string' && r.body.url.endsWith(`/r/news/${SLUG}`), r.body?.url);
    check('回應含 publishedAt（非 NULL 才會出現在 archive）', !!r.body?.publishedAt, r.body?.publishedAt);

    // 資料庫落地狀態：不得被讀成「一次寄了 0 封的失敗群發」
    const row = sql(`SELECT mode, status, recipient_count, accepted_count, failed_count,
                            tier, credit_cost, body_html IS NULL, published_at IS NOT NULL
                       FROM campaign WHERE slug = ${quote(SLUG)};`).split('|');
    eq(row[0], 'publish', '★ campaign.mode');
    eq(row[1], 'published', '★ campaign.status（不是 sent 也不是 failed）');
    eq(row[2], 0, 'campaign.recipient_count');
    eq(row[3], 0, 'campaign.accepted_count');
    eq(row[4], 0, 'campaign.failed_count');
    eq(row[5], 'PREMIUM', 'campaign.tier');
    eq(row[6], COST, 'campaign.credit_cost');
    eq(row[7], 't', 'campaign.body_html 為 NULL（沒有信件版內文）');
    eq(row[8], 't', 'campaign.published_at 非 NULL');
    // markdown 完整落地（網頁端渲染讀的是這個欄位，經 ContentSplitter 切分）
    eq(sql(`SELECT position('<!--paywall-->' in markdown) > 0
              FROM campaign WHERE slug = ${quote(SLUG)};`), 't', 'markdown 含 paywall 標記');

    // ★ 完全沒有寄信：這條路徑一封 email_log 都不該產生
    eq(sql(`SELECT count(*) FROM email_log WHERE campaign_id = ${campaignId};`), 0,
      '★ 沒有任何 email_log（一封信都沒寄）');
  }

  // ── [3] 重複 slug 必須回 400（而非唯一索引以 500 失敗）────────────────
  console.log('\n[3] 重複 slug');
  {
    const dup = await admin('/api/admin/campaign/publish', {
      method: 'POST',
      body: JSON.stringify({ subject: SUBJECT, markdown: MARKDOWN, tier: 'PREMIUM', creditCost: COST, slug: SLUG }),
    });
    eq(dup.status, 400, '★ 重複 slug 的回應碼（不是 500）');
    eq(sql(`SELECT count(*) FROM campaign WHERE slug = ${quote(SLUG)};`), 1, '仍只有一列');
  }

  // ── [4] 寄送守門仍在：同一篇 PREMIUM 內容用 send 端點必須被拒 ──────────
  console.log('\n[4] 寄送守門仍在（publish 放行 ≠ send 放行）');
  {
    const sent = await admin('/api/admin/campaign/send', {
      method: 'POST',
      body: JSON.stringify({
        subject: SUBJECT, markdown: MARKDOWN, mode: 'now',
        tier: 'PREMIUM', creditCost: COST, slug: `${SLUG}-mail`,
      }),
    });
    eq(sent.status, 400, '★ PREMIUM 走 send 端點仍回 400（階段 D 前不得寄送）');
    eq(sql(`SELECT count(*) FROM campaign WHERE slug = ${quote(`${SLUG}-mail`)};`), 0,
      '被守門擋下時未寫入 campaign');
  }

  // ── [5] 未登入讀者：回應本文不得含受限段落 ────────────────────────────
  console.log('\n[5] ★ 未登入讀者開文章頁（受限段落不得進入 HTTP 回應）');
  {
    const { res, body } = await page(`/r/news/${SLUG}`);
    eq(res.status, 200, '回應碼');
    check('看得到免費區', body.includes(FREE));
    check('★ 回應本文完全不含受限段落', !body.includes(GATED), '受限內容外洩給未登入者');
    check('顯示「需要登入」的 gate', body.includes('登入繼續閱讀'));
    check('標示為進階內容', body.includes('進階'));
  }

  // ── [6] 已登入、餘額足夠：CAN_UNLOCK，回應本文仍不得含受限段落 ─────────
  console.log('\n[6] 已登入且餘額足夠（CAN_UNLOCK，受限段落仍不得外洩）');
  eq(await subscribe(READER_EMAIL), 201, '測試讀者訂閱回應碼');
  // 訂閱來源是 newsletter，需確認同意才算「已確認訂閱者」；直接把 consent 設為 true
  // 走的是與 confirm 端點相同的資料狀態（登入流程本身由 verify-reader-flow.mjs 驗）
  sql(`UPDATE survey_response SET consent = TRUE, unsubscribed = FALSE
        WHERE lower(email) = ${quote(READER_EMAIL)};`);
  const login = await loginAs(READER_EMAIL);
  eq(login.status, 302, 'magic link 回應碼');
  check('取得 reader_session cookie', !!login.cookie);
  const cookie = login.cookie;
  // cookie 為 null 時必須中止：後續請求會以「匿名」身分發出，
  // 而本階段最核心的哨兵「★ 回應本文完全不含受限段落」對匿名訪客「必然」通過——
  // 它本來要驗的性質（已登入、餘額足夠、尚未解鎖時仍不外洩）完全沒被驗到。
  // 整體不會假通過（上面那條 cookie 斷言已經是紅的），但讓失敗原因保持單一，
  // 而不是連帶產生一堆看起來通過、實際上什麼都沒驗的誤導綠字。
  if (!cookie) throw new Error('取不到 reader_session cookie，中止後續讀者階段（避免以匿名身分產生誤導的通過）');

  // 餘額由後台加點補足（走正式端點，帳本會有對應的一筆，不破壞不變式）
  const balanceBefore = Number(sql(
    `SELECT credits FROM reader WHERE lower(email) = ${quote(READER_EMAIL)};`) || '0');
  if (balanceBefore < COST) {
    const need = COST + 5 - balanceBefore;
    const grant = await admin('/api/admin/readers/credits', {
      method: 'POST',
      body: JSON.stringify({ emails: [READER_EMAIL], delta: need, note: 'publish-e2e' }),
    });
    eq(grant.status, 200, '後台加點回應碼');
    grantedCredits = need;
  }
  const startCredits = Number(sql(
    `SELECT credits FROM reader WHERE lower(email) = ${quote(READER_EMAIL)};`) || '0');
  check(`起始餘額 ${startCredits} >= 解鎖成本 ${COST}`, startCredits >= COST);

  {
    const { res, body } = await page(`/r/news/${SLUG}`, cookie);
    eq(res.status, 200, '回應碼');
    check('★ 回應本文完全不含受限段落', !body.includes(GATED), '受限內容外洩給未解鎖者');
    check('看得到解鎖按鈕（CAN_UNLOCK）', body.includes('id="unlock-btn"'));
    check(`顯示「用 ${COST} 點解鎖」（顯示的數字 == 實際扣的）`, body.includes(`用 ${COST} 點解鎖`));
    check('gate 附規則頁連結', body.includes('/r/rules'));
  }

  // ── [7] 按下解鎖：扣點、受限段落出現、帳本有一筆 READ ─────────────────
  console.log('\n[7] 解鎖（扣點 → 受限段落出現 → 帳本記一筆 READ）');
  {
    const { status, data, text } = await postUnlock(cookie);
    eq(status, 200, '解鎖端點回應碼');
    eq(data && data.outcome, 'UNLOCKED', 'outcome');
    eq(data && data.cost, COST, '扣點金額');
    eq(data && data.credits, startCredits - COST, '解鎖後餘額');
    if (status !== 200) console.log(`    （回應內容：${text}）`);

    eq(sql(`SELECT count(*) FROM credit_txn t JOIN reader r ON r.id = t.reader_id
             WHERE lower(r.email) = ${quote(READER_EMAIL)}
               AND t.reason = 'READ' AND t.delta = ${-COST};`), 1,
      '★ credit_txn 的 READ 扣點筆數');

    const { body } = await page(`/r/news/${SLUG}`, cookie);
    check('★ 解鎖後受限段落出現', body.includes(GATED));
    check('不再顯示解鎖按鈕', !body.includes('id="unlock-btn"'));
  }

  // ── [8] 核心不變式：餘額 == 帳本總和 ─────────────────────────────────
  console.log('\n[8] 核心不變式（直接查資料庫驗算）');
  checkLedgerInvariant('測試讀者', READER_EMAIL);

  // ── [9] BASIC 也能走這條端點，且兩篇都出現在 /r/archive ───────────────
  console.log('\n[9] BASIC 發布與 /r/archive 露出');
  {
    const r = await admin('/api/admin/campaign/publish', {
      method: 'POST',
      body: JSON.stringify({
        subject: '只發布不寄送（BASIC）', markdown: `${FREE}\n\n一般內容`,
        tier: 'BASIC', slug: BASIC_SLUG,
      }),
    });
    eq(r.status, 200, 'BASIC 發布回應碼');
    eq(r.body && r.body.creditCost, 0, 'BASIC 的 creditCost 正規化為 0');

    const { res, body } = await page('/r/archive');
    eq(res.status, 200, '/r/archive 回應碼');
    check(`★ /r/archive 列出 PREMIUM 那篇（/r/news/${SLUG}）`, body.includes(`/r/news/${SLUG}`));
    check(`★ /r/archive 列出 BASIC 那篇（/r/news/${BASIC_SLUG}）`, body.includes(`/r/news/${BASIC_SLUG}`));
    check('★ archive 不含任何受限段落', !body.includes(GATED));
  }

  // ── [10] 後台歷史列表不得把它顯示成失敗的群發 ─────────────────────────
  console.log('\n[10] 後台歷史列表的呈現');
  {
    const list = await admin('/api/admin/campaigns');
    eq(list.status, 200, '/api/admin/campaigns 回應碼');
    const row = Array.isArray(list.body) ? list.body.find(c => c.slug === SLUG) : null;
    check('歷史列表找得到這篇', !!row, '文章沒有出現在後台歷史列表');
    if (row) {
      eq(row.mode, 'publish', '★ mode（不是 now，否則會被讀成立即群發）');
      eq(row.status, 'published', '★ status（不是 failed，否則管理者會去重送）');
      eq(row.recipientCount, 0, 'recipientCount');
      eq(row.acceptedCount, 0, 'acceptedCount');
      eq(row.failedCount, 0, 'failedCount');
      check('★ status 不是 failed／sent（不呈現為群發結果）',
        row.status !== 'failed' && row.status !== 'sent', row.status);
      check('沒有排程時間（不會出現「取消排程」按鈕）', !row.scheduledAt, String(row.scheduledAt));
    }
  }

  // ── [11] 下架（撤回發布）：DELETE /api/admin/campaigns/{id}/publication ──
  //
  // 這是「發布後打錯價格／貼漏內文」時唯一不必手動改資料庫的止血手段。
  // 最關鍵的性質是它「只改可見性，不動已發生的交易」：
  // 已解鎖的讀者其 article_access 與 credit_txn 必須完整留著（重新發布後仍有效），
  // 否則核心不變式「餘額 == 帳本總和」會被破壞，讀者也會被要求為同一篇付第二次。
  console.log('\n[11] 下架（只改可見性，不動 article_access 與 credit_txn）');
  {
    const basicId = sql(`SELECT id FROM campaign WHERE slug = ${quote(BASIC_SLUG)};`).trim();

    // ① 權限：未帶金鑰一律 401（下架讓文章從 archive 消失，權限等級與發布相同）
    const noKey = await admin(`/api/admin/campaigns/${basicId}/publication`, { method: 'DELETE' }, null);
    eq(noKey.status, 401, '★ 未帶 X-Admin-Key 下架的回應碼');
    eq(sql(`SELECT published_at IS NOT NULL FROM campaign WHERE slug = ${quote(BASIC_SLUG)};`), 't',
      '★ 未授權的下架請求沒有生效');

    // ② 不存在的批次回 404（而非 500）
    const missing = await admin('/api/admin/campaigns/99999999/publication', { method: 'DELETE' });
    eq(missing.status, 404, '不存在的批次回應碼');

    // ③ 寄過信的列不可下架：信裡的連結指向 /r/news/{slug}，下架會讓收信人點到 404。
    //    用 SQL 在 BASIC 那篇（沒有任何讀者解鎖過）上臨時插一筆寄送記錄再刪掉，
    //    比真的寄一封信安全（本腳本全程不寄信）。
    sql(`INSERT INTO email_log (recipient, subject, type, provider_message_id, status, campaign_id)
         VALUES (${quote('publish-log@example.invalid')}, ${quote(SUBJECT)}, 'campaign', 'noop', 'sent', ${basicId});`);
    const mailed = await admin(`/api/admin/campaigns/${basicId}/publication`, { method: 'DELETE' });
    eq(mailed.status, 409, '★ 已寄過信的批次下架回應碼（不可讓收信人點到 404）');
    eq(sql(`SELECT published_at IS NOT NULL FROM campaign WHERE slug = ${quote(BASIC_SLUG)};`), 't',
      '★ 被 409 擋下時 published_at 未被改動');
    sql(`DELETE FROM email_log WHERE campaign_id = ${basicId} AND recipient = ${quote('publish-log@example.invalid')};`);
    eq(sql(`SELECT count(*) FROM email_log WHERE campaign_id = ${basicId};`), 0, '還原：移除臨時寄送記錄');

    // ④ 狀態不是 published 的列不可下架（那些是寄送批次，語意不同）。
    //    同樣用 SQL 在自己的 fixture 上臨時改狀態再改回，避免真的寄一批信。
    sql(`UPDATE campaign SET status = 'sent' WHERE slug = ${quote(BASIC_SLUG)};`);
    const wrongStatus = await admin(`/api/admin/campaigns/${basicId}/publication`, { method: 'DELETE' });
    eq(wrongStatus.status, 409, '★ 狀態非 published 的批次下架回應碼');
    eq(sql(`SELECT published_at IS NOT NULL FROM campaign WHERE slug = ${quote(BASIC_SLUG)};`), 't',
      '★ 被 409 擋下時 published_at 未被改動');
    sql(`UPDATE campaign SET status = 'published' WHERE slug = ${quote(BASIC_SLUG)};`);
    eq(sql(`SELECT status FROM campaign WHERE slug = ${quote(BASIC_SLUG)};`), 'published',
      '還原：BASIC 那篇的狀態');

    // ⑤ 成功下架 PREMIUM 那篇（第 [7] 階段已有一位讀者付 COST 點解鎖過它）
    const accessBefore = sql(`SELECT count(*) FROM article_access WHERE campaign_id = ${campaignId};`);
    const txnBefore = sql(`SELECT count(*) FROM credit_txn t JOIN reader r ON r.id = t.reader_id
                            WHERE lower(r.email) = ${quote(READER_EMAIL)};`);
    eq(accessBefore, 1, '下架前：解鎖者的 article_access 筆數');

    const off = await admin(`/api/admin/campaigns/${campaignId}/publication`, { method: 'DELETE' });
    eq(off.status, 200, '★ 下架回應碼');
    eq(off.body && off.body.campaignId, campaignId, '回應 campaignId');
    eq(off.body && off.body.slug, SLUG, '回應 slug');

    // 可見性：published_at 變 NULL，兩個讀者端入口立刻都看不到
    eq(sql(`SELECT published_at IS NULL FROM campaign WHERE slug = ${quote(SLUG)};`), 't',
      '★ campaign.published_at 已設為 NULL');
    eq(sql(`SELECT count(*) FROM campaign WHERE slug = ${quote(SLUG)};`), 1,
      '★ campaign 那一列仍存在（下架不刪列）');
    const single = await page(`/r/news/${SLUG}`);
    eq(single.res.status, 404, '★ /r/news/{slug} 下架後回應碼');
    check('★ 下架後單篇頁不含受限段落', !single.body.includes(GATED));
    const archive = await page('/r/archive');
    check(`★ /r/archive 不再列出這篇（/r/news/${SLUG}）`, !archive.body.includes(`/r/news/${SLUG}`));
    check('★ archive 不含任何受限段落', !archive.body.includes(GATED));

    // ★ 已發生的交易完全不受影響（帳本只增不改；已買過的人重新發布後仍有效）
    eq(sql(`SELECT count(*) FROM article_access WHERE campaign_id = ${campaignId};`), accessBefore,
      '★ 下架後 article_access 筆數不變（已解鎖者的憑證保留）');
    eq(sql(`SELECT count(*) FROM credit_txn t JOIN reader r ON r.id = t.reader_id
             WHERE lower(r.email) = ${quote(READER_EMAIL)};`), txnBefore,
      '★ 下架後 credit_txn 筆數不變（帳本只增不改）');
    eq(sql(`SELECT count(*) FROM credit_txn t JOIN reader r ON r.id = t.reader_id
             WHERE lower(r.email) = ${quote(READER_EMAIL)}
               AND t.reason = 'READ' AND t.delta = ${-COST};`), 1,
      '★ 那筆 READ 扣點紀錄仍在（不得因下架而退點或刪除）');
    checkLedgerInvariant('下架後測試讀者', READER_EMAIL);

    // 其餘欄位一個都不能被動到——條件式 UPDATE 只寫 published_at 一欄；
    // 若改成 save(entity) 整列寫回，這裡驗的是「沒有資料遺失」，
    // 而併發覆蓋的性質由 CampaignServiceTest 的 verify(clearPublishedAt) 守著。
    const after = sql(`SELECT subject, tier, credit_cost, mode, status, slug
                         FROM campaign WHERE slug = ${quote(SLUG)};`).split('|');
    eq(after[0], SUBJECT, '下架後 subject 不變');
    eq(after[1], 'PREMIUM', '下架後 tier 不變');
    eq(after[2], COST, '下架後 credit_cost 不變');
    eq(after[3], 'publish', '下架後 mode 不變');
    eq(after[4], 'published', '下架後 status 不變（下架只改 published_at 一欄）');

    // ⑥ 下架後 slug 仍被那一列佔用，所以「同 slug 重新發布」依舊回 400——
    //    這是刻意的：重新上架應該是把 published_at 設回來（或改用新 slug），
    //    不是再建一列造成兩篇同名文章。此處只記錄事實，不主張它是好體驗。
    const rePublish = await admin('/api/admin/campaign/publish', {
      method: 'POST',
      body: JSON.stringify({ subject: SUBJECT, markdown: MARKDOWN, tier: 'PREMIUM', creditCost: COST, slug: SLUG }),
    });
    eq(rePublish.status, 400, '下架後同 slug 重新發布仍回 400（slug 仍被那一列佔用）');
  }

  // ── [12] 瀏覽器模式（選用）───────────────────────────────────────────
  if (WITH_BROWSER) {
    console.log('\n[12] 真實瀏覽器：後台按「只發布不寄送」→ 讀者頁 paywall → 下架');
    await runBrowserStage();
  } else {
    console.log('\n[12] 真實瀏覽器（略過，加 --browser 啟用）');
  }
} catch (e) {
  check('腳本執行未中斷', false, e.stack || e.message);
} finally {
  // ── 還原：會影響其他人的狀態一律補償回去，且每一步都檢查回傳值 ─────────
  console.log('\n[還原]');
  try {
    if (grantedCredits > 0) {
      // 加點是走正式端點的真實帳本異動，用反向加點沖銷（不刪帳本列，維持只增不改）
      const back = await admin('/api/admin/readers/credits', {
        method: 'POST',
        body: JSON.stringify({ emails: [READER_EMAIL], delta: -grantedCredits, note: 'publish-e2e-revert' }),
      });
      eq(back.status, 200, '還原：沖銷測試加點的回應碼');
      checkLedgerInvariant('還原後測試讀者', READER_EMAIL);
    }
    // 測試文章與測試讀者保留原狀：下一次執行的 resetFixtures 會刪除重建成相同起點。
    // 這裡不刪是為了讓人工檢查看得到剛剛發布的結果（/r/archive、後台歷史列表）。
    console.log('  ! 測試文章與測試讀者保留原狀，下次執行會重設為相同起點');
  } catch (e) {
    check('還原步驟完成', false, e.stack || e.message);
  }
}

console.log(`\n=== 結果：${failures === 0 ? '全部通過' : `${failures} 項失敗`} ===\n`);
process.exit(failures === 0 ? 0 : 1);

/**
 * 載入 playwright：先試專案內解析，再逐一嘗試常見的全域安裝目錄。
 * 刻意不呼叫 `npm root -g`（那需要 shell），改用固定候選路徑，
 * 作法與 verify-stage-c.mjs / verify-admin-reader.mjs 一致。
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
  throw new Error('專案內與常見全域安裝目錄都找不到 playwright');
}

/**
 * 真實 Chromium：驗只有瀏覽器能驗的事——後台那顆「只發布不寄送」按鈕真的會跑
 * （HTTP 斷言看不到 JS：按鈕沒接上事件、或 doPublish 拋錯，HTTP 層完全驗不出來）。
 *
 * 流程：開後台 → 輸入金鑰解鎖 → 切到電子報頁 → 填主旨／內文／slug／PREMIUM／點數
 * → 按「只發布不寄送」→ 確認訊息含公開網址 → 直接開那個網址確認 paywall 生效。
 */
async function runBrowserStage() {
  let playwright;
  try {
    const mod = await loadPlaywright();
    playwright = mod.default ?? mod;
  } catch (e) {
    // 明確加了 --browser 卻載不到 playwright 必須算失敗，不可只印警告就 return：
    // 那樣 failures 不增加、exit code 仍是 0，而本階段唯一會實際執行後台 JS 的
    // 斷言完全沒跑——按鈕變成死按鈕也驗不出來。不想跑就不要加 --browser。
    check('載入 playwright（已指定 --browser）', false,
      `${e.message}；請安裝（npm i -g playwright）或移除 --browser`);
    return;
  }

  /** 瀏覽器模式專用的第三個 slug；每次執行前先清掉 */
  const uiSlug = 'publish-endpoint-ui';
  sql(`DELETE FROM article_access
        WHERE campaign_id IN (SELECT id FROM campaign WHERE slug = ${quote(uiSlug)});`);
  sql(`DELETE FROM campaign WHERE slug = ${quote(uiSlug)};`);
  const uiGated = 'PUBLISH_E2E_UI_SENTINEL_GATED';

  const browser = await playwright.chromium.launch();
  try {
    const page2 = await browser.newPage();
    await page2.goto(`${BASE}/admin.html`, { waitUntil: 'domcontentloaded' });
    // 後台以 sessionStorage 存金鑰；填入閘門欄位並送出
    // Task 10 把金鑰輸入框改成預設隱藏、點「改用管理金鑰登入」才展開，故填值前需先展開。
    await page2.click('#gate-use-key');
    await page2.fill('#gate-key', ADMIN_KEY);
    await page2.click('#gate-btn');
    await page2.waitForSelector('.tab[data-view="campaign"]', { state: 'visible', timeout: 15000 });
    await page2.click('.tab[data-view="campaign"]');

    await page2.fill('#subject', UI_SUBJECT);
    await page2.fill('#markdown', `${FREE}\n\n<!--paywall-->\n\n${uiGated}\n`);
    await page2.fill('#art-slug', uiSlug);
    await page2.selectOption('#art-tier', 'PREMIUM');
    await page2.fill('#art-cost', String(COST));

    // confirm() 對話框一律接受（腳本無人操作）
    page2.on('dialog', d => d.accept());
    await page2.click('#publish-btn');
    await page2.waitForFunction(
      () => document.querySelector('#send-msg')?.textContent?.includes('已發布'),
      null, { timeout: 15000 });

    const msg = await page2.textContent('#send-msg');
    check('★ 後台按鈕真的發布成功（訊息顯示「已發布」）', !!msg && msg.includes('已發布'), msg);
    check('訊息含文章公開網址', !!msg && msg.includes(`/r/news/${uiSlug}`), msg);
    eq(sql(`SELECT count(*) FROM campaign WHERE slug = ${quote(uiSlug)} AND status = 'published';`),
      1, '資料庫確實建立了這篇文章');
    eq(sql(`SELECT count(*) FROM email_log el JOIN campaign c ON c.id = el.campaign_id
             WHERE c.slug = ${quote(uiSlug)};`), 0, '★ 沒有任何 email_log（一封信都沒寄）');

    // 用另一個乾淨的瀏覽器情境（未登入）開文章頁，確認 paywall 生效
    const anon = await browser.newPage();
    await anon.goto(`${BASE}/r/news/${uiSlug}`, { waitUntil: 'domcontentloaded' });
    const html = await anon.content();
    check('★ 未登入者在瀏覽器拿到的 HTML 不含受限段落', !html.includes(uiGated));
    check('看得到免費區', html.includes(FREE));

    // 後台那顆「下架」按鈕：HTTP 斷言看不到 JS——按鈕沒接上事件、或 doUnpublish
    // 打錯路徑，HTTP 層完全驗不出來。這裡走真實點擊，確認它真的會下架。
    await page2.click('#hist-btn');
    const row = page2.locator('#hist tbody tr', { hasText: UI_SUBJECT }).first();
    await row.waitFor({ state: 'visible', timeout: 15000 });
    await row.getByRole('button', { name: '下架' }).click();
    // 成功後 doHistory() 重繪，那一列的動作欄從按鈕換成「已下架」說明。
    // 等不到就記為失敗（而不是讓例外冒出去變成籠統的「腳本執行中斷」）
    let uiUnpublished = true;
    try {
      await page2.waitForFunction(
        subject => Array.from(document.querySelectorAll('#hist tbody tr'))
          .some(tr => tr.textContent.includes(subject) && tr.textContent.includes('已下架')),
        UI_SUBJECT, { timeout: 15000 });
    } catch (e) {
      uiUnpublished = false;
    }
    check('★ 後台「下架」按鈕真的生效（列表改顯示「已下架」）', uiUnpublished,
      '按下「下架」後歷史列表沒有出現「已下架」');
    eq(sql(`SELECT published_at IS NULL FROM campaign WHERE slug = ${quote(uiSlug)};`), 't',
      '★ 下架後 published_at 為 NULL');
    await anon.goto(`${BASE}/r/news/${uiSlug}`, { waitUntil: 'domcontentloaded' });
    const gone = await anon.content();
    check('★ 下架後未登入者開單篇頁看不到內容', !gone.includes(uiGated) && !gone.includes(FREE));
  } finally {
    // close() 的失敗不可以吃掉後面的清理：Windows 上 chromium 行程殘留時它會拋錯，
    // 一旦讓例外冒出去，下面的刪除與那條還原斷言全部不執行，
    // 帶 PREMIUM 哨兵的測試文章就留在 /r/archive 污染下一次人工檢查。
    try { await browser.close(); } catch (e) { console.log(`  ! 關閉瀏覽器失敗（不影響清理）：${e.message}`); }
    // 這篇只在瀏覽器模式用得到，留著會讓 /r/archive 多一篇無關文章
    sql(`DELETE FROM article_access
          WHERE campaign_id IN (SELECT id FROM campaign WHERE slug = ${quote(uiSlug)});`);
    const left = sql(`SELECT count(*) FROM campaign WHERE slug = ${quote(uiSlug)};`);
    sql(`DELETE FROM campaign WHERE slug = ${quote(uiSlug)};`);
    // 還原步驟本身也要檢查回傳值（不可假通過）
    eq(sql(`SELECT count(*) FROM campaign WHERE slug = ${quote(uiSlug)};`), 0,
      `還原：刪除瀏覽器模式測試文章（原有 ${left} 列）`);
  }
}
