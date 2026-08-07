// 邀請／問卷獲點排行榜驗證腳本（API + 瀏覽器）：
//   1. API：GET /api/admin/readers/reward-leaderboard 回陣列、欄位齊全、依合計降冪；
//      limit 越界（0、101）回 400
//   2. 種子資料：直接對本機測試資料庫 insert 兩位讀者與獎勵交易
//      （idempotent：固定 email，重跑前先刪同 email 的舊資料）
//   3. 瀏覽器（--browser）：登入後台 → 讀者管理分頁 → 排行榜表格渲染出種子讀者並截圖
//
// 用法（後端須已在本機啟動，DB 為本機容器 survey-test-db）：
//   ADMIN_API_KEY=dev-admin-key ADMIN_BASE=http://127.0.0.1:8087 SEED_DB=survey_visual \
//     node survey-backend/scripts/verify-admin-leaderboard.mjs --browser
//
// 可重跑：種子 email 用 .invalid 保留網域，每次執行先清掉自己上次的種子資料再重種。

import { execFileSync } from 'node:child_process';
import { mkdir } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const __dirname = dirname(fileURLToPath(import.meta.url));
const BASE = process.env.ADMIN_BASE || 'http://127.0.0.1:8080';
const KEY = process.env.ADMIN_API_KEY;
const SEED_DB = process.env.SEED_DB || 'survey_visual';
const USE_BROWSER = process.argv.includes('--browser');
if (!KEY) { console.error('請先設定環境變數 ADMIN_API_KEY'); process.exit(1); }

const T1 = 'verify-leaderboard-1@example.invalid';
const T2 = 'verify-leaderboard-2@example.invalid';

let failed = 0;
const fail = (msg) => { console.error('FAIL:', msg); failed++; };
const ok = (cond, label) => { if (!cond) fail(label); else console.log('OK  ', label); };

/** 對本機測試容器執行 SQL（docker exec，與其他 verify 腳本同一個 survey-test-db 前提） */
function sql(statement) {
  return execFileSync('docker',
    ['exec', 'survey-test-db', 'psql', '-U', 'postgres', '-d', SEED_DB, '-tAc', statement],
    { encoding: 'utf8' }).trim();
}

/** 呼叫受保護的後台 API，回 { status, body } */
async function api(path) {
  const res = await fetch(BASE + path, { headers: { 'X-Admin-Key': KEY } });
  const text = await res.text();
  let body = null;
  try { body = JSON.parse(text); } catch { body = text; }
  return { status: res.status, body };
}

// ── 種子資料（idempotent：先清自己上次的種子）─────────────────────
sql(`DELETE FROM credit_txn WHERE reader_id IN (SELECT id FROM reader WHERE email IN ('${T1}','${T2}'))`);
sql(`DELETE FROM reader WHERE email IN ('${T1}','${T2}')`);
sql(`INSERT INTO reader (email, referral_code) VALUES ('${T1}','VLB00001'),('${T2}','VLB00002')`);
sql(`INSERT INTO credit_txn (reader_id, delta, reason, note)
     SELECT id, 100, 'REFERRAL', '排行榜驗證種子:' || email FROM reader WHERE email='${T1}'`);
sql(`INSERT INTO credit_txn (reader_id, delta, reason, note)
     SELECT id, 20, 'SURVEY_REWARD', '排行榜驗證種子:' || email FROM reader WHERE email='${T1}'`);
sql(`INSERT INTO credit_txn (reader_id, delta, reason, note)
     SELECT id, 5, 'SURVEY_VOTE_REWARD', '排行榜驗證種子:' || email FROM reader WHERE email='${T2}'`);
console.log('OK   種子資料已寫入（T1=120 點、T2=5 點）');

// ── API 驗證 ─────────────────────────────────────────────────────
const list = await api('/api/admin/readers/reward-leaderboard?limit=50');
ok(list.status === 200, 'GET reward-leaderboard 回 200');
ok(Array.isArray(list.body), '回傳為陣列');
const row1 = list.body.find(r => r.email === T1);
const row2 = list.body.find(r => r.email === T2);
ok(row1 && row1.referralCredits === 100 && row1.surveyCredits === 20 && row1.totalCredits === 120,
   `T1 邀請 100／問卷 20／合計 120（實際 ${JSON.stringify(row1)}）`);
ok(row2 && row2.totalCredits === 5, `T2 合計 5（實際 ${JSON.stringify(row2)}）`);
ok(list.body.indexOf(row1) < list.body.indexOf(row2), 'T1（120 點）排在 T2（5 點）前面');
const sorted = list.body.every((r, i, a) => i === 0 || a[i - 1].totalCredits >= r.totalCredits);
ok(sorted, '整體依合計降冪');
ok((await api('/api/admin/readers/reward-leaderboard?limit=0')).status === 400, 'limit=0 回 400');
ok((await api('/api/admin/readers/reward-leaderboard?limit=101')).status === 400, 'limit=101 回 400');

// ── 瀏覽器驗證（--browser 時執行）────────────────────────────────
if (USE_BROWSER) {
  const require = createRequire(join(__dirname, '..', '..', 'teaching-site', 'package.json'));
  const { chromium } = require('playwright');
  const OUT = join(__dirname, '..', 'data', 'admin-leaderboard-render');
  await mkdir(OUT, { recursive: true });
  const browser = await chromium.launch();
  try {
    const page = await browser.newPage({ viewport: { width: 1360, height: 900 } });
    await page.goto(BASE + '/admin.html', { waitUntil: 'networkidle' });
    // 後台金鑰閘門：預設顯示 magic link 流程，須先點「改用金鑰」展開 key 輸入
    // （比照 verify-admin-toolbar-theme.mjs 的登入慣例）
    await page.waitForSelector('#gate', { state: 'visible' });
    await page.click('#gate-use-key');
    await page.fill('#gate-key', KEY);
    await page.click('#gate-btn');
    await page.waitForSelector('#app:not([hidden])', { timeout: 8000 });
    await page.click('#tab-readers');
    await page.waitForSelector('#leaderboard-table tbody tr', { timeout: 8000 });
    const rows = await page.locator('#leaderboard-table tbody tr').count();
    ok(rows >= 2, `排行榜表格渲染出 ${rows} 列（>= 2）`);
    const text = await page.locator('#leaderboard-table').innerText();
    ok(text.includes(T1), '表格含 T1 email');
    await page.locator('#leaderboard-table').scrollIntoViewIfNeeded();
    await page.screenshot({ path: join(OUT, 'admin-leaderboard.png'), fullPage: true });
    console.log('OK   截圖已輸出 —', OUT);
  } finally {
    await browser.close();
  }
}

if (failed > 0) { console.error(`\n共 ${failed} 項未通過`); process.exit(1); }
console.log('\n排行榜驗證全部通過。');
