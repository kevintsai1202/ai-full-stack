// 階段 C 端到端驗收：一條完整的真實讀者路徑
//
// 目的：找出「前面 14 個任務各自通過單元測試、串起來之後才會浮現」的問題。
// 因此這裡刻意不重跑單元測試驗過的東西，而是專驗只有端到端才驗得出來的性質：
//
//   ① 讀者在 /r/rules、/r/me、paywall gate 三處看到的點數，與**實際扣的點數**一致（spec §5.11）
//   ② 「餘額 == credit_txn 總和」這條核心不變式，跑完整條路徑後仍成立（直接查資料庫驗算）
//   ③ 受限內容在按下解鎖前**不在 HTTP 回應本文裡**（伺服器端渲染，不是靠 CSS 藏）
//   ④ 邀請人的交易明細**不含被邀者 email**（連 @ 前的 local part 也不得出現）
//   ⑤ 「不該發生的事沒有發生」：未確認不發獎、重複確認不重複發、重複解鎖不重複扣
//
// 涵蓋的流程（brief Step 1 的 11 步）：
//   A 訂閱 → 首次登入拿初始贈點 → 取邀請連結 → B 透過連結訂閱 → B 未確認前 A 餘額不變
//   → B 確認 → A 得獎勵 → 重複確認不再發 → 發布 PREMIUM → B 看不到受限區 → B 解鎖
//   → 重複解鎖不扣點 → 後台改參數三處同步 → 後台授予 VIP → VIP 免費看全文
//
// 用法（需服務已啟動；預設連本機 8080 與 docker 容器 survey-test-db）：
//   $env:ADMIN_API_KEY="<金鑰>"; node survey-backend/scripts/verify-stage-c.mjs
//   node survey-backend/scripts/verify-stage-c.mjs --base http://127.0.0.1:8081
//   node survey-backend/scripts/verify-stage-c.mjs --browser   # 追加真實 Chromium 驗證
//
// ── 可重跑 ──────────────────────────────────────────────────────────────
// 測試身分是兩個固定的 .invalid（RFC 2606 保留網域，不可能對應真人）email，
// 腳本開頭會把「這兩個 email 的」fixture 重設成已知起點，所以可以連續執行兩次都通過，
// 也不會每跑一次就在資料庫留下新的一組資料。
//
// ── 不可假通過 ──────────────────────────────────────────────────────────
// 每一項斷言失敗都計入 failures 並讓 exit code 非 0；還原步驟本身也檢查回傳值。
// --browser 模式載不到 playwright 一律算失敗，不會只印警告然後 exit 0。
//
// ── 既有資料的保護 ──────────────────────────────────────────────────────
// 沒有 DELETE 無 WHERE、沒有 TRUNCATE、沒有 DROP。
// 唯一的刪除是 resetFixtures() 對「這兩個 .invalid email」的列，且：
//   ・email 先過 SAFE_EMAIL 白名單正則，不符就直接中止整個腳本（不是略過）
//   ・reader 以先查出的 id 逐一刪除，不以模糊條件批次刪
//   ・這兩位讀者的每一列都是本腳本自己建立的合成資料，不是任何真人的同意紀錄
// 為什麼非刪不可：初始贈點只在 reader 那一列被「建立」時發放，留著舊列就永遠驗不到
// 「首次登入拿到初始贈點」。改用補償性加點只能還原餘額，還原不了「首次」這個事實。
// 至於後台參數與 VIP 這類會影響其他人的狀態，還原一律用補償性操作並檢查回傳值。

import { createHash, createHmac } from 'node:crypto';
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
/** 必須與應用的 app.unsubscribe-secret 一致，否則自組的確認連結會被判為偽造 */
const UNSUB_SECRET = arg('secret', process.env.UNSUBSCRIBE_SECRET || 'dev-unsub-secret');
const ADMIN_KEY = process.env.ADMIN_API_KEY;
const WITH_BROWSER = args.includes('--browser');

if (!ADMIN_KEY) {
  console.error('請先設定環境變數 ADMIN_API_KEY');
  process.exit(1);
}

// ── 測試身分 ────────────────────────────────────────────────────────────
/** 只有符合這個式樣的 email 允許被 resetFixtures 刪除 */
const SAFE_EMAIL = /^stage-c-[a-z]+@example\.invalid$/;
const A_EMAIL = 'stage-c-inviter@example.invalid';   // 邀請人
const B_EMAIL = 'stage-c-invitee@example.invalid';   // 被邀者
/** B email 的 local part：驗「個資不得外洩」時連這一段都不允許出現 */
const B_LOCAL = B_EMAIL.split('@')[0];

// ── 測試文章 ────────────────────────────────────────────────────────────
const FREE = 'STAGE_C_FREE_INTRO';
/** 四篇文章各自的受限區哨兵字串；出現在不該出現的回應裡就是洩漏 */
const ART = {
  unlock:  { slug: 'stage-c-premium-unlock',  cost: 10, gated: 'STAGE_C_GATED_UNLOCK' },
  cost20:  { slug: 'stage-c-premium-cost20',  cost: 20, gated: 'STAGE_C_GATED_COST20' },
  vip:     { slug: 'stage-c-premium-vip',     cost: 30, gated: 'STAGE_C_GATED_VIP' },
  browser: { slug: 'stage-c-premium-browser', cost: 5,  gated: 'STAGE_C_GATED_BROWSER' },
};
/** 後台在第 10 步把 credit.premium_cost 改成這個值 */
const NEW_PREMIUM_COST = 20;

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

/** 呼叫受保護的後台 API；非 2xx 不拋錯，回 {status, body} 供呼叫端檢查 */
async function admin(path, opts = {}) {
  const res = await fetch(BASE + path, {
    ...opts,
    headers: { 'Content-Type': 'application/json', 'X-Admin-Key': ADMIN_KEY },
  });
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

/** 送出訂閱（等同 /r/ 訂閱頁的 POST；ref 選填） */
async function subscribe(email, ref) {
  const body = { email, consent: true, source: 'newsletter' };
  if (ref) body.ref = ref;
  const res = await fetch(`${BASE}/api/survey`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
  });
  return res.status;
}

/**
 * 以 magic link 完成一次真實登入，回傳 reader_session cookie。
 *
 * 明文 token 只存在於寄出的信裡（DB 只有 SHA-256 雜湊），腳本收不到信，
 * 因此改為自己寫一筆已知明文的 login_token 再走 verify 端點——
 * 拿到的是應用真正簽發的 httpOnly cookie，走的也是正式登入路徑
 * （首次登入建帳與發放初始贈點都會真的發生，這正是第 1 步要驗的）。
 */
async function loginAs(email) {
  const raw = `stage-c-${email}-${Date.now()}`;
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

/** 組確認訂閱連結的 HMAC token（與 UnsubscribeTokenService.sign 完全一致） */
function confirmToken(email) {
  return createHmac('sha256', UNSUB_SECRET)
    .update(email.trim().toLowerCase(), 'utf8').digest('base64url');
}

/** 讀取某位讀者的欄位（查無此人回空字串） */
function readerField(email, field) {
  return sql(`SELECT ${field} FROM reader WHERE lower(email) = ${quote(email)};`);
}
/** 讀取某位讀者的餘額（數字） */
function credits(email) {
  return Number(readerField(email, 'credits') || '0');
}

/**
 * 以 slug upsert 一篇已發布的 PREMIUM 測試文章。
 *
 * 為什麼直接寫資料庫：目前沒有「只發布不寄送」的後台路徑——
 * /api/admin/campaign/send 對 PREMIUM 無條件回 400（階段 D 才解除），
 * 而 campaign 同時扮演「發送批次」與「文章」。這一點本身就是階段 C 的已知限制。
 */
function publishArticle(a) {
  const markdown = `${FREE}\n\n<!--paywall-->\n\n${a.gated}`;
  sql(`
    INSERT INTO campaign (subject, markdown, mode, recipient_count, accepted_count, failed_count,
                          status, tier, credit_cost, slug, published_at)
    VALUES (${quote('階段 C 驗收文章 ' + a.slug)}, ${quote(markdown)}, 'now', 0, 0, 0,
            'sent', 'PREMIUM', ${a.cost}, ${quote(a.slug)}, now())
    ON CONFLICT (slug) WHERE slug IS NOT NULL
    DO UPDATE SET markdown = EXCLUDED.markdown, tier = 'PREMIUM',
                  credit_cost = ${a.cost}, published_at = now(), status = 'sent';
  `);
  return sql(`SELECT id FROM campaign WHERE slug = ${quote(a.slug)};`);
}

/** POST 解鎖端點 */
async function unlock(cookie, slug) {
  const res = await fetch(`${BASE}/api/reader/unlock/${slug}`, {
    method: 'POST', headers: cookie ? { Cookie: cookie } : {},
  });
  const text = await res.text();
  let data = null;
  try { data = JSON.parse(text); } catch { /* 401 等空 body */ }
  return { status: res.status, data, text };
}

/**
 * 把兩位測試讀者的 fixture 重設成已知起點。
 *
 * 安全性見檔頭「既有資料的保護」：只碰白名單正則允許的兩個 .invalid email，
 * 不符就中止整個腳本而不是略過——「守衛沒生效卻照跑」比直接失敗危險得多。
 */
function resetFixtures() {
  for (const email of [A_EMAIL, B_EMAIL]) {
    if (!SAFE_EMAIL.test(email)) {
      console.error(`測試 email ${email} 不符白名單，為避免誤刪真實資料，中止執行`);
      process.exit(2);
    }
  }
  const emails = [A_EMAIL, B_EMAIL].map(quote).join(', ');
  // reader id 先查出來，之後只依 id 刪，不用模糊條件批次刪
  const ids = sql(`SELECT id FROM reader WHERE lower(email) IN (${emails});`)
    .split('\n').map((s) => s.trim()).filter(Boolean);
  if (ids.length) {
    const idList = ids.join(', ');
    sql(`DELETE FROM article_access WHERE reader_id IN (${idList});`);
    sql(`DELETE FROM credit_txn WHERE reader_id IN (${idList});`);
    // referred_by 沒有外鍵約束，仍主動清掉指向即將刪除之列的參照
    sql(`UPDATE reader SET referred_by = NULL WHERE referred_by IN (${idList});`);
    sql(`DELETE FROM reader WHERE id IN (${idList});`);
  }
  sql(`DELETE FROM login_token WHERE lower(email) IN (${emails});`);
  sql(`DELETE FROM survey_response WHERE lower(email) IN (${emails});`);
  return ids.length;
}

/**
 * 核心不變式：reader.credits 必須等於該讀者 credit_txn 的 delta 總和。
 *
 * 直接查資料庫而不是看 API 回應——API 回的就是 reader.credits 本身，
 * 用它來驗證 reader.credits 等於帳本總和是循環論證，驗不出任何東西。
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

console.log(`\n=== 階段 C 端到端驗收（${BASE}）===\n`);

/** 還原用：後台原始的 credit.premium_cost */
let originalPremiumCost = null;
/** 還原用：是否曾授予 B VIP */
let vipGranted = false;

try {
  // ── [0] 前置：重設 fixture、取得目前的點數參數 ─────────────────────────
  console.log('[0] 前置檢查與 fixture 重設');
  const removed = resetFixtures();
  console.log(`  ! 已重設 ${removed} 位測試讀者的 fixture（僅限兩個 .invalid 測試身分）`);

  const settings = await admin('/api/admin/settings');
  eq(settings.status, 200, '讀取點數參數回應碼');
  const SIGNUP_GRANT = Number(settings.body['credit.signup_grant']);
  const REFERRAL_REWARD = Number(settings.body['credit.referral_reward']);
  originalPremiumCost = settings.body['credit.premium_cost'];
  check(`初始贈點 ${SIGNUP_GRANT}、邀請獎勵 ${REFERRAL_REWARD}、進階單篇 ${originalPremiumCost}`,
    Number.isFinite(SIGNUP_GRANT) && Number.isFinite(REFERRAL_REWARD) && originalPremiumCost != null);
  check('邀請獎勵 > 0（否則第 5 步無從驗證發獎）', REFERRAL_REWARD > 0,
    '後台把 credit.referral_reward 設成 0 時本腳本無法驗收邀請獎勵');

  // ── [1] A 訂閱 → 首次登入 → 拿到初始贈點 ─────────────────────────────
  console.log('\n[1] 讀者 A 訂閱並首次登入');
  eq(await subscribe(A_EMAIL), 201, 'A 訂閱回應碼');
  eq(sql(`SELECT count(*) FROM survey_response WHERE lower(email) = ${quote(A_EMAIL)};`), 1,
    'A 在名單中心的列數');

  const aLogin = await loginAs(A_EMAIL);
  eq(aLogin.status, 302, 'A magic link 回應碼');
  check('A 取得 reader_session cookie', !!aLogin.cookie);
  const aCookie = aLogin.cookie;

  eq(credits(A_EMAIL), SIGNUP_GRANT, 'A 首次登入後餘額');
  eq(sql(`SELECT count(*) FROM credit_txn t JOIN reader r ON r.id = t.reader_id
          WHERE lower(r.email) = ${quote(A_EMAIL)} AND t.reason = 'SIGNUP_GRANT';`), 1,
    'A 的 SIGNUP_GRANT 筆數');
  {
    const { res, body } = await page('/r/me', aCookie);
    eq(res.status, 200, '/r/me 回應碼');
    check(`/r/me 顯示餘額 ${SIGNUP_GRANT} 點`, body.includes(`${SIGNUP_GRANT} 點`));
  }

  // ── [2] A 取得邀請連結 ─────────────────────────────────────────────
  console.log('\n[2] 讀者 A 的邀請連結');
  const CODE = readerField(A_EMAIL, 'referral_code');
  check('A 有邀請碼', /^[A-Z0-9]{8}$/.test(CODE), CODE);
  {
    const { res, body } = await page('/r/invite', aCookie);
    eq(res.status, 200, '/r/invite 回應碼');
    check('邀請頁含完整邀請連結', body.includes(`/r/?ref=${CODE}`));
    check('尚無成功邀請時顯示空狀態', body.includes('還沒有人透過你的連結完成訂閱'));
  }

  // ── [3] B 透過邀請連結訂閱 ──────────────────────────────────────────
  console.log('\n[3] 讀者 B 透過邀請連結訂閱');
  eq(await subscribe(B_EMAIL, CODE), 201, 'B 訂閱回應碼');
  eq(sql(`SELECT answers->>'_ref' FROM survey_response WHERE lower(email) = ${quote(B_EMAIL)};`),
    CODE, 'B 名單資料的 answers._ref');

  // ── [4] B 尚未確認 → A 的餘額不變（防刷核心） ───────────────────────
  console.log('\n[4] ★ B 未點確認信時，A 不得拿到獎勵');
  eq(credits(A_EMAIL), SIGNUP_GRANT, 'A 餘額（應仍為初始贈點）');
  eq(sql(`SELECT count(*) FROM credit_txn WHERE reason = 'REFERRAL' AND note = ${quote(B_EMAIL)};`),
    0, '尚未發獎的 REFERRAL 筆數');

  // ── [5] B 點確認信 → A 拿到獎勵 ────────────────────────────────────
  console.log('\n[5] B 點確認信，A 拿到邀請獎勵');
  const confirmUrl = `/api/survey/confirm?email=${encodeURIComponent(B_EMAIL)}&t=${confirmToken(B_EMAIL)}`;
  eq((await page(confirmUrl)).res.status, 200, '確認訂閱回應碼');
  eq(credits(A_EMAIL), SIGNUP_GRANT + REFERRAL_REWARD, 'A 確認後的餘額');
  eq(sql(`SELECT count(*) FROM credit_txn WHERE reason = 'REFERRAL' AND note = ${quote(B_EMAIL)};`),
    1, '發獎後的 REFERRAL 筆數');
  {
    const { body } = await page('/r/invite', aCookie);
    check('/r/invite 顯示「1 人」', body.includes('1 人'));
    check(`/r/invite 顯示累計獲得 ${REFERRAL_REWARD} 點`,
      body.includes(`累計獲得 ${REFERRAL_REWARD} 點`));
  }

  // ── [6] 重複點同一確認連結 → 不得重複發獎 ───────────────────────────
  console.log('\n[6] ★ 重複點同一確認連結（冪等）');
  eq((await page(confirmUrl)).res.status, 200, '再次確認回應碼');
  eq(credits(A_EMAIL), SIGNUP_GRANT + REFERRAL_REWARD, 'A 餘額（不得再增加）');
  eq(sql(`SELECT count(*) FROM credit_txn WHERE reason = 'REFERRAL' AND note = ${quote(B_EMAIL)};`),
    1, 'REFERRAL 筆數（不得變成 2）');

  // ── [7] 發布 PREMIUM 文章 → B 登入後看不到受限區 ────────────────────
  console.log('\n[7] 發布 PREMIUM 文章，B 未解鎖時不得看到受限區');
  const bLogin = await loginAs(B_EMAIL);
  eq(bLogin.status, 302, 'B magic link 回應碼');
  check('B 取得 reader_session cookie', !!bLogin.cookie);
  const bCookie = bLogin.cookie;
  eq(credits(B_EMAIL), SIGNUP_GRANT, 'B 首次登入後餘額');
  eq(readerField(B_EMAIL, 'referred_by'), readerField(A_EMAIL, 'id'),
    'B 的 referred_by 指向 A');

  publishArticle(ART.unlock);
  {
    const { res, body } = await page(`/r/news/${ART.unlock.slug}`, bCookie);
    eq(res.status, 200, '文章頁回應碼');
    check('看得到免費區', body.includes(FREE));
    check('★ 回應本文完全不含受限區', !body.includes(ART.unlock.gated),
      '受限內容洩漏到未解鎖者的 HTTP 回應中');
    check('paywall 標記未洩漏', !body.includes('<!--paywall-->'));
    check(`gate 顯示「用 ${ART.unlock.cost} 點解鎖」`, body.includes(`用 ${ART.unlock.cost} 點解鎖`));
    check('有解鎖按鈕', body.includes('id="unlock-btn"'));
  }
  {
    // 未登入者更不可看到（gate 之外的第二條路徑）
    const { body } = await page(`/r/news/${ART.unlock.slug}`);
    check('★ 未登入者的回應也不含受限區', !body.includes(ART.unlock.gated));
  }

  // ── [8] B 解鎖 → 扣點、受限區出現 ───────────────────────────────────
  console.log('\n[8] B 按下解鎖');
  {
    const { status, data } = await unlock(bCookie, ART.unlock.slug);
    eq(status, 200, '解鎖回應碼');
    eq(data?.outcome, 'UNLOCKED', 'outcome');
    eq(data?.cost, ART.unlock.cost, '實際扣點');
    eq(credits(B_EMAIL), SIGNUP_GRANT - ART.unlock.cost, 'B 解鎖後餘額');
  }
  {
    const { body } = await page(`/r/news/${ART.unlock.slug}`, bCookie);
    check('★ 解鎖後受限區出現', body.includes(ART.unlock.gated));
    check('不再顯示解鎖按鈕', !body.includes('id="unlock-btn"'));
  }

  // ── [9] 再次解鎖 → ALREADY_UNLOCKED，不得重複扣點 ───────────────────
  console.log('\n[9] ★ B 再次解鎖同一篇（不得重複扣點）');
  {
    const { status, data } = await unlock(bCookie, ART.unlock.slug);
    eq(status, 200, '再次解鎖回應碼');
    eq(data?.outcome, 'ALREADY_UNLOCKED', 'outcome');
    eq(credits(B_EMAIL), SIGNUP_GRANT - ART.unlock.cost, 'B 餘額（不得再減少）');
    eq(sql(`SELECT count(*) FROM credit_txn t JOIN reader r ON r.id = t.reader_id
            WHERE lower(r.email) = ${quote(B_EMAIL)} AND t.reason = 'READ';`), 1,
      'READ 扣點筆數（不得變成 2）');
    eq(sql(`SELECT count(*) FROM article_access a JOIN reader r ON r.id = a.reader_id
            WHERE lower(r.email) = ${quote(B_EMAIL)};`), 1, 'article_access 筆數');
  }

  // ── [10] 後台改參數 → 三處顯示與實扣一致（spec §5.11） ───────────────
  console.log('\n[10] 後台改 credit.premium_cost，驗證三處顯示與實扣一致');
  {
    const put = await admin('/api/admin/settings', {
      method: 'PUT', body: JSON.stringify({ 'credit.premium_cost': String(NEW_PREMIUM_COST) }),
    });
    eq(put.status, 200, '寫入參數回應碼');
    eq(put.body['credit.premium_cost'], NEW_PREMIUM_COST, '寫入後讀回的值');

    const rules = (await page('/r/rules', bCookie)).body;
    check(`/r/rules 顯示「進階文章每篇 ${NEW_PREMIUM_COST} 點」`,
      rules.includes(`進階文章每篇 ${NEW_PREMIUM_COST} 點`));
    const me = (await page('/r/me', bCookie)).body;
    check(`/r/me 顯示「進階內容每篇 ${NEW_PREMIUM_COST} 點」`,
      me.includes(`進階內容每篇 ${NEW_PREMIUM_COST} 點`));

    // gate 那一處：顯示的數字必須等於「按下去真的會扣的點數」。
    // 注意 gate 的數字來自文章自己的 credit_cost（DB CHECK 強制 PREMIUM 的
    // credit_cost > 0，所以 CreditPolicy.costOf 的全域退路在實務上不會被走到），
    // 因此這裡驗的是「顯示 == 實扣」，而不是「gate 會跟著全域參數變」。
    // 後者不成立，已列入報告與 spec 的偏離項。
    publishArticle(ART.cost20);
    const before = credits(B_EMAIL);
    const gate = (await page(`/r/news/${ART.cost20.slug}`, bCookie)).body;
    check(`★ gate 顯示「用 ${NEW_PREMIUM_COST} 點解鎖」`,
      gate.includes(`用 ${NEW_PREMIUM_COST} 點解鎖`));
    check('★ gate 出現前回應不含受限區', !gate.includes(ART.cost20.gated));

    const r = await unlock(bCookie, ART.cost20.slug);
    eq(r.data?.outcome, 'UNLOCKED', '第二篇解鎖 outcome');
    eq(r.data?.cost, NEW_PREMIUM_COST, '★ 實際扣點（必須等於 gate 顯示的數字）');
    eq(credits(B_EMAIL), before - NEW_PREMIUM_COST, '扣點後餘額');
  }

  // ── [11] 後台授予 VIP → 未解鎖的 PREMIUM 直接看全文且不扣點 ──────────
  console.log('\n[11] 後台授予 B VIP，未解鎖的 PREMIUM 直接看全文');
  publishArticle(ART.vip);
  {
    const before = credits(B_EMAIL);
    const grant = await admin('/api/admin/readers/vip', {
      method: 'POST', body: JSON.stringify({ email: B_EMAIL, days: 30 }),
    });
    eq(grant.status, 200, '授予 VIP 回應碼');
    eq(grant.body.vipActive, true, '授予後 vipActive');
    vipGranted = true;

    const { body } = await page(`/r/news/${ART.vip.slug}`, bCookie);
    check('★ VIP 直接看到受限區全文', body.includes(ART.vip.gated));
    check('VIP 不顯示解鎖按鈕', !body.includes('id="unlock-btn"'));
    eq(credits(B_EMAIL), before, '★ VIP 閱讀後餘額不變');

    // 立刻取消 VIP：後續（第 14 步的瀏覽器階段）要驗的是「一般讀者看到 gate」，
    // B 若還掛著 VIP 就會直接看到全文，那段驗證會變成永遠失敗的假訊號。
    // finally 仍會再取消一次（冪等），這裡不是把還原搬走，而是多一道即時還原。
    const revoke = await admin('/api/admin/readers/vip?email=' + encodeURIComponent(B_EMAIL),
      { method: 'DELETE' });
    eq(revoke.status, 200, '取消 VIP 回應碼');
    eq(revoke.body.vipActive, false, '取消後 vipActive');
    eq(revoke.body.vipExpiresAt, null, '取消後 vipExpiresAt 必須清空');
    // VIP 瀏覽時 recordAccess 會補寫一筆 cost=0 的 article_access（免費通行紀錄）。
    // 這是刻意的設計，但也代表「VIP 過期後這篇仍免費」——記在報告的已知行為裡。
    eq(sql(`SELECT cost FROM article_access a JOIN reader r ON r.id = a.reader_id
            JOIN campaign c ON c.id = a.campaign_id
            WHERE lower(r.email) = ${quote(B_EMAIL)} AND c.slug = ${quote(ART.vip.slug)};`),
      0, 'VIP 免費通行的 article_access.cost');
  }

  // ── [12] 核心不變式：餘額 == 帳本總和 ───────────────────────────────
  console.log('\n[12] 核心不變式（直接查資料庫驗算）');
  checkLedgerInvariant('A', A_EMAIL);
  checkLedgerInvariant('B', B_EMAIL);

  // ── [13] 個資：邀請人的明細不得出現被邀者 email ─────────────────────
  console.log('\n[13] ★ 邀請人的交易明細不得洩漏被邀者身分');
  {
    const me = (await page('/r/me', aCookie)).body;
    check('/r/me 不含被邀者完整 email', !me.includes(B_EMAIL));
    check(`/r/me 不含被邀者 local part（${B_LOCAL}）`, !me.includes(B_LOCAL));
    check('改以固定文字呈現', me.includes('一位朋友完成訂閱'));
    const invitePage = (await page('/r/invite', aCookie)).body;
    check('/r/invite 不含被邀者 local part', !invitePage.includes(B_LOCAL));
    // 冪等鍵本身仍存在資料庫（不可改），只是不對讀者呈現——把這件事驗明白，
    // 避免日後有人「為了乾淨」把 note 清掉而讓重複發獎的防線失效
    eq(sql(`SELECT count(*) FROM credit_txn WHERE reason = 'REFERRAL' AND note = ${quote(B_EMAIL)};`),
      1, '冪等鍵仍保存在 credit_txn.note');
  }

  // ── [14] 瀏覽器模式（選用） ─────────────────────────────────────────
  if (WITH_BROWSER) {
    console.log('\n[14] 真實瀏覽器操作');
    await runBrowserStage(bCookie);
  } else {
    console.log('\n[14] 真實瀏覽器（略過，加 --browser 啟用）');
  }
} catch (e) {
  check('腳本執行未中斷', false, e.stack || e.message);
} finally {
  // ── 還原：影響其他人的狀態一律補償回去，且每一步都檢查回傳值 ─────────
  console.log('\n[還原] 後台參數與 VIP');
  try {
    if (originalPremiumCost != null) {
      const back = await admin('/api/admin/settings', {
        method: 'PUT', body: JSON.stringify({ 'credit.premium_cost': String(originalPremiumCost) }),
      });
      eq(back.status, 200, '還原 credit.premium_cost 回應碼');
      eq(back.body && back.body['credit.premium_cost'], originalPremiumCost, '還原後讀回的值');
      const rules = (await page('/r/rules')).body;
      check(`/r/rules 已還原為每篇 ${originalPremiumCost} 點`,
        rules.includes(`進階文章每篇 ${originalPremiumCost} 點`));
    }
    if (vipGranted) {
      const del = await admin('/api/admin/readers/vip?email=' + encodeURIComponent(B_EMAIL),
        { method: 'DELETE' });
      check(`還原：取消 B 的 VIP（回應碼 ${del.status}，接受 200/404）`,
        [200, 404].includes(del.status));
      if (del.status === 200) eq(del.body.vipActive, false, '還原後 vipActive');
    }
    // 測試文章不刪：slug 固定，下一次執行會被 upsert 覆蓋，不會累積。
    // 刪除反而會讓 /r/archive 在兩次執行之間反覆增減，難以人工比對。
    console.log('  ! 測試文章與兩位測試讀者的資料保留原狀，下次執行會重設為相同起點');
  } catch (e) {
    check('還原步驟完成', false, e.stack || e.message);
  }
}

console.log(`\n=== 結果：${failures === 0 ? '全部通過' : `${failures} 項失敗`} ===\n`);
process.exit(failures === 0 ? 0 : 1);

/**
 * 載入 playwright：先試專案內解析，再逐一嘗試常見的全域安裝目錄。
 * 刻意不呼叫 `npm root -g`（那需要 shell），改用固定候選路徑，
 * 作法與 verify-admin-reader.mjs 一致。
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
 * 真實 Chromium：驗只有瀏覽器能驗的兩件事——
 * ① 解鎖按鈕的前端腳本真的會跑（HTTP 斷言看不到 JS）
 * ② 按下之前，瀏覽器實際拿到的 HTML 不含受限區
 *
 * 指定了 --browser 卻載不到 playwright 一律計為失敗：靜默略過等於這段驗證沒跑，
 * 而 exit code 仍是 0——本專案已經因為這個模式讓一整段驗證無聲消失過。
 */
async function runBrowserStage() {
  let playwright;
  try {
    const mod = await loadPlaywright();
    playwright = mod.default ?? mod;
    if (!playwright.chromium) throw new Error('載入的模組沒有 chromium');
  } catch (e) {
    check('載入 playwright（已指定 --browser）', false,
      `${e.message}；請執行 npm i -g playwright，或不要加 --browser`);
    return;
  }

  const a = ART.browser;
  publishArticle(a);
  // 這篇只給瀏覽器階段用，確保處於「未解鎖」狀態（僅刪這一位測試讀者對這一篇的紀錄）
  const bId = readerField(B_EMAIL, 'id');
  const cid = sql(`SELECT id FROM campaign WHERE slug = ${quote(a.slug)};`);
  sql(`DELETE FROM article_access WHERE reader_id = ${bId} AND campaign_id = ${cid};`);

  const raw = `stage-c-browser-${Date.now()}`;
  const hash = createHash('sha256').update(raw, 'utf8').digest('base64url');
  sql(`DELETE FROM login_token WHERE lower(email) = ${quote(B_EMAIL)};`);
  sql(`INSERT INTO login_token (token_hash, email, expires_at)
       VALUES (${quote(hash)}, ${quote(B_EMAIL)}, now() + interval '15 minutes');`);

  const before = credits(B_EMAIL);
  const browser = await playwright.chromium.launch();
  try {
    const p = await browser.newPage();
    await p.goto(`${BASE}/api/reader/login/verify?t=${encodeURIComponent(raw)}`
      + `&redirect=${encodeURIComponent(`/r/news/${a.slug}`)}`, { waitUntil: 'domcontentloaded' });
    check('magic link 導到文章頁', p.url().endsWith(`/r/news/${a.slug}`), p.url());

    const html = await p.content();
    check('★ 按下解鎖前瀏覽器拿到的 HTML 不含受限區', !html.includes(a.gated));
    check('解鎖按鈕存在', await p.locator('#unlock-btn').count() === 1);

    await Promise.all([
      p.waitForNavigation({ waitUntil: 'domcontentloaded' }),
      p.click('#unlock-btn'),
    ]);
    const after = await p.content();
    check('★ 解鎖後受限區出現（前端腳本可正常運作）', after.includes(a.gated));
    eq(credits(B_EMAIL), before - a.cost, '瀏覽器解鎖後的餘額');

    await p.goto(`${BASE}/r/invite`, { waitUntil: 'domcontentloaded' });
    check('邀請頁在真實瀏覽器中可正常渲染', (await p.content()).includes('/r/?ref='));
    await p.close();
  } finally {
    await browser.close();
  }
  // 瀏覽器階段多扣了一次點，帳本必須仍然對得起來
  checkLedgerInvariant('B（瀏覽器階段後）', B_EMAIL);
}
