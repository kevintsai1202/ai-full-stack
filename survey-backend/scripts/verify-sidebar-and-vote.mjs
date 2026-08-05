// 文章頁側欄 ＋ 一鍵投票發點 端到端驗證（2026-08-05 計畫的人工補驗自動化版）
//
// 涵蓋：
//   [1] 快建投票 API（POST /api/admin/forms/quick-vote）
//   [2] 種入驗證用文章與標籤（同標籤交集決定相關文章排序）
//   [3] 文章頁側欄：分類卡、相關文章卡、排序、連結、受限區不洩漏
//   [4] 問卷卡點數提示：未登入／已登入兩種文案
//   [5] 一鍵投票 → 發點 → 接續頁橫幅顯示真實發點狀態
//   [6] 改票不重發（帳本仍只有一筆）
//   [7] 後台快建面板實際操作（填題目與選項 → 建立並插入 → 標記進編輯器）
//   [8] 截圖：桌機側欄、窄螢幕側欄、後台面板
//
// 環境準備（本機 5432／5433 常被其他容器佔用，故另開 5434 專用驗證庫）：
//   docker run -d --name survey-verify-db -e POSTGRES_PASSWORD=password \
//     -e POSTGRES_DB=survey -p 5434:5432 pgvector/pgvector:pg18
//   （容器已存在時改用 docker start survey-verify-db）
//
//   JAVA_HOME=/d/java/jdk-21 JDBC_URL=jdbc:postgresql://127.0.0.1:5434/survey \
//     PORT=8080 APP_ALLOW_INSECURE_DEV_SECRETS=true ADMIN_API_KEY=dev-admin-key \
//     mvn spring-boot:run
//
// 用法：
//   node scripts/verify-sidebar-and-vote.mjs
//   node scripts/verify-sidebar-and-vote.mjs --base http://127.0.0.1:8080 --db survey-verify-db
//
// 這支腳本只對本機驗證庫執行，預設不會碰到任何正式環境——與 verify-admin.mjs
// 不同（那支的 BASE 預設是正式站網址，跑之前務必先設 ADMIN_BASE）。
//
// 為何寫成腳本：CLAUDE.md 規定瀏覽器自動化與流程驗證要可重跑、可逐行檢查，
// 而不是一次性互動指令。截圖輸出到 scripts/verify-output/（已 gitignore）。

import { execFileSync } from 'node:child_process';
import { createHmac } from 'node:crypto';
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

/**
 * 動態載入 playwright：先試專案內解析，再逐一嘗試常見的全域安裝目錄。
 *
 * 本專案沒有 package.json，靜態 `import { chromium } from 'playwright'` 會直接
 * ERR_MODULE_NOT_FOUND。作法與 verify-admin.mjs／verify-publish-endpoint.mjs 一致。
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

// 全域安裝的 playwright 以 CJS 形式被動態 import 時，具名匯出掛在 default 底下
const playwrightModule = await loadPlaywright();
const chromium = (playwrightModule.default ?? playwrightModule).chromium;
if (!chromium) {
  console.error('FAIL: 載入的 playwright 模組沒有 chromium 匯出');
  process.exit(1);
}

const args = process.argv.slice(2);
/** 取具名參數，未給則用預設值 */
function arg(name, fallback) {
  const i = args.indexOf(`--${name}`);
  return i >= 0 ? args[i + 1] : fallback;
}

const BASE = arg('base', 'http://127.0.0.1:8080');
const DB_CONTAINER = arg('db', 'survey-verify-db');
const ADMIN_KEY = arg('key', 'dev-admin-key');
// 與 application.yml 的 app.reader.jwt-secret 預設值一致；本機驗證才可這樣簽發
const JWT_SECRET = arg('jwt-secret', 'dev-reader-jwt-secret-change-me-32chars');
const OUT_DIR = 'scripts/verify-output';
/** 受限區哨兵：只要出現在側欄或未授權回應中就是洩漏 */
const SENTINEL = 'SENTINEL_GATED_VERIFY';
const READER_EMAIL = 'sidebar-verify@example.com';

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

/**
 * 在驗證資料庫執行 SQL，回傳資料列（-t -A 為無表頭、無對齊）。
 *
 * psql 即使加了 -t -A 仍會把 `INSERT 0 1` 這類命令標記印到 stdout，
 * 混在 RETURNING 的結果裡會讓呼叫端拿到 "2\nINSERT 0 1"。這裡濾掉命令標記，
 * 只留真正的資料列。
 */
function sql(statement) {
  const raw = execFileSync(
    'docker',
    ['exec', '-i', DB_CONTAINER, 'psql', '-U', 'postgres', '-d', 'survey', '-t', '-A', '-c', statement],
    { encoding: 'utf8' }
  );
  return raw
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line && !/^(INSERT|UPDATE|DELETE|SELECT|COPY|BEGIN|COMMIT|ROLLBACK)\b/.test(line))
    .join('\n')
    .trim();
}

/** 帶管理金鑰打後台 API；body 以 UTF-8 位元組送出，避免非 ASCII 被參數層轉碼 */
async function adminApi(path, options = {}) {
  const res = await fetch(`${BASE}${path}`, {
    ...options,
    headers: {
      'X-Admin-Key': ADMIN_KEY,
      'Content-Type': 'application/json; charset=utf-8',
      ...(options.headers || {})
    }
  });
  const text = await res.text();
  return { status: res.status, body: text, json: () => JSON.parse(text) };
}

/**
 * 自行簽發讀者 session JWT。
 *
 * 登入用的 magic link 明文 token 只存在於寄出的信中（資料庫只留雜湊），腳本取不到；
 * 但 session 是以 app.reader.jwt-secret 簽的標準 HS256 JWT，本機驗證時我們握有同一把
 * 秘鑰，直接簽一個等效的 session 即可，不必動搖「token 不可從資料庫反推」的設計。
 * 對應 ReaderSessionService：subject 為 reader id、HS256（39 bytes 秘鑰）。
 */
function issueReaderSession(readerId) {
  const base64url = (input) =>
    Buffer.from(input).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const now = Math.floor(Date.now() / 1000);
  const header = base64url(JSON.stringify({ alg: 'HS256' }));
  const payload = base64url(JSON.stringify({ sub: String(readerId), iat: now, exp: now + 86400 }));
  const signature = createHmac('sha256', Buffer.from(JWT_SECRET, 'utf8'))
    .update(`${header}.${payload}`)
    .digest('base64')
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${header}.${payload}.${signature}`;
}

/** 取頁面 HTML，可帶 session cookie */
async function getPage(path, sessionCookie) {
  const res = await fetch(`${BASE}${path}`, {
    headers: sessionCookie ? { Cookie: `reader_session=${sessionCookie}` } : {},
    redirect: 'manual'
  });
  return { res, body: await res.text() };
}

mkdirSync(OUT_DIR, { recursive: true });
console.log(`\n=== 側欄與投票發點驗證（${BASE}）===\n`);

// ── [1] 快建投票 API ──────────────────────────────────────────────
console.log('[1] 快建投票 API');
const created = await adminApi('/api/admin/forms/quick-vote', {
  method: 'POST',
  body: JSON.stringify({
    title: '這期你最想看哪個主題？',
    label: '選一個最想深入的',
    options: ['RAG 實戰', 'Agent 架構', '部署維運']
  })
});
check('回應 200', created.status === 200, `實際 ${created.status}：${created.body}`);
const question = created.json();
const FORM_KEY = question.formKey;
check('formKey 符合 vote-yyyyMMdd-xxxx 格式', /^vote-\d{8}-[a-z0-9]{4}$/.test(FORM_KEY), FORM_KEY);
check('中文標題完整往返', question.title === '這期你最想看哪個主題？', question.title);
check('選項順序保留', JSON.stringify(question.options) === JSON.stringify(['RAG 實戰', 'Agent 架構', '部署維運']));

const embeddable = await adminApi('/api/admin/forms/embeddable');
check('立即出現在可嵌入清單（不需再到動態表單分頁設定）',
  embeddable.json().some((f) => f.formKey === FORM_KEY));

// 選項數量與重複的後端驗證（前端面板也擋，但後端才是真正的防線）
const tooFew = await adminApi('/api/admin/forms/quick-vote', {
  method: 'POST',
  body: JSON.stringify({ title: '只有一個選項', label: '說明', options: ['唯一'] })
});
check('選項少於 2 個回 400', tooFew.status === 400, `實際 ${tooFew.status}`);
const duplicated = await adminApi('/api/admin/forms/quick-vote', {
  method: 'POST',
  body: JSON.stringify({ title: '重複選項', label: '說明', options: ['同一個', ' 同一個 '] })
});
check('重複選項回 400（去空白後比對）', duplicated.status === 400, `實際 ${duplicated.status}`);

// ── [2] 種入驗證資料 ──────────────────────────────────────────────
console.log('\n[2] 種入驗證文章與讀者');
sql(`DELETE FROM campaign_tag WHERE campaign_id IN (SELECT id FROM campaign WHERE slug LIKE 'verify-%')`);
sql(`DELETE FROM campaign WHERE slug LIKE 'verify-%'`);
sql(`DELETE FROM credit_txn WHERE reader_id IN (SELECT id FROM reader WHERE email = '${READER_EMAIL}')`);
sql(`DELETE FROM reader WHERE email = '${READER_EMAIL}'`);

/**
 * 插入一篇已發布文章並回傳 id。
 *
 * premium 為 true 時才附上付費牆標記與受限區哨兵——資料庫的
 * ck_campaign_paywall_requires_premium 規定含 <!--paywall--> 的文章必須是 PREMIUM
 * （且 ck_campaign_premium_cost 要求 credit_cost > 0），這條不變式本身就值得被這支
 * 腳本順帶驗到，所以照它的規則種資料而不是繞過它。
 */
function insertCampaign(slug, subject, publishedAt, extraMarkdown = '', premium = false) {
  const body = `這是 ${subject} 的免費開場。\n\n${extraMarkdown}`;
  const markdown = premium ? `${body}\n\n<!--paywall-->\n\n${SENTINEL}` : body;
  const tier = premium ? 'PREMIUM' : 'BASIC';
  const cost = premium ? 10 : 0;
  return sql(`
    INSERT INTO campaign (subject, markdown, mode, status, recipient_count, accepted_count,
                          failed_count, tier, credit_cost, slug, published_at, cover_emoji)
    VALUES ($$${subject}$$, $$${markdown}$$, 'now', 'sent', 0, 0, 0, '${tier}', ${cost},
            '${slug}', '${publishedAt}', '🚀')
    RETURNING id`);
}

/** 把文章掛到指定 slug 的既有 hashtag 上 */
function tagCampaign(campaignId, tagSlug) {
  sql(`INSERT INTO campaign_tag (campaign_id, tag_id)
       SELECT ${campaignId}, id FROM content_tag WHERE slug = '${tagSlug}'`);
}

// 主文章帶問卷標記（驗證問卷卡與點數提示）；相關文章的預期排序見下方斷言
const mainId = insertCampaign('verify-main', '主文章：RAG 與 Agent', '2026-07-25T10:00:00+08:00',
  `<!--survey:${FORM_KEY}-->`, true);
tagCampaign(mainId, 'ai');
tagCampaign(mainId, 'rag');

const twoSharedId = insertCampaign('verify-two-shared', '共兩個標籤的舊文', '2026-06-01T10:00:00+08:00');
tagCampaign(twoSharedId, 'ai');
tagCampaign(twoSharedId, 'rag');

const oneSharedId = insertCampaign('verify-one-shared', '共一個標籤的新文', '2026-06-20T10:00:00+08:00');
tagCampaign(oneSharedId, 'ai');

insertCampaign('verify-no-shared', '沒有共同標籤的最新文', '2026-07-01T10:00:00+08:00');

const readerId = sql(`INSERT INTO reader (email, credits, referral_code)
                      VALUES ('${READER_EMAIL}', 100, 'VERIFY01') RETURNING id`);
check('測試文章與讀者已種入', Boolean(mainId) && Boolean(readerId), `campaign=${mainId} reader=${readerId}`);
const sessionCookie = issueReaderSession(readerId);

// ── [3] 文章頁側欄 ────────────────────────────────────────────────
console.log('\n[3] 文章頁側欄（未登入）');
const anonymous = await getPage('/r/news/verify-main');
check('回應 200', anonymous.res.status === 200, `實際 ${anonymous.res.status}`);
check('★ 回應完全不含受限區', !anonymous.body.includes(SENTINEL), '受限內容洩漏');
check('輸出側欄容器', anonymous.body.includes('class="article-side"'));
check('有分類卡', anonymous.body.includes('>分類</h2>'));
check('有相關文章卡', anonymous.body.includes('>相關文章</h2>'));
check('分類連結指向 archive 篩選', anonymous.body.includes('/r/archive?tag=rag'));
check('本篇所屬分類標 active', /side-tag active" href="\/r\/archive\?tag=(ai|rag)/.test(anonymous.body));

// 相關文章排序：同標籤交集多者優先（兩個標籤 → 一個標籤 → 無交集補最新）
const order = ['verify-two-shared', 'verify-one-shared', 'verify-no-shared']
  .map((slug) => anonymous.body.indexOf(`/r/news/${slug}`));
check('相關文章列出三篇', order.every((i) => i >= 0), JSON.stringify(order));
check('★ 同標籤交集多者排前面（即使發布日較舊）', order[0] < order[1], `位置 ${JSON.stringify(order)}`);
check('無交集者由「補最新」墊底', order[1] < order[2], `位置 ${JSON.stringify(order)}`);
check('側欄不列出本篇', !anonymous.body.includes('>主文章：RAG 與 Agent</strong>'));

// ── [4] 問卷卡點數提示 ────────────────────────────────────────────
console.log('\n[4] 問卷卡點數提示');
check('未登入版文案：登入後投票可獲得 5 點',
  anonymous.body.includes('登入後投票可獲得 5 點'),
  '未登入者投票不發點，不可寫成「投票即可獲得」');
check('未登入版不出現「投票即可獲得」', !anonymous.body.includes('投票即可獲得'));

const loggedIn = await getPage('/r/news/verify-main', sessionCookie);
check('已登入版文案：投票即可獲得 5 點（每份問卷一次）',
  loggedIn.body.includes('投票即可獲得 5 點（每份問卷一次）'));
check('已登入版不提「限已註冊讀者」', !loggedIn.body.includes('限已註冊讀者'));
check('提示排在題目之前',
  loggedIn.body.indexOf('投票即可獲得') < loggedIn.body.indexOf('這期你最想看哪個主題？'),
  '需求是「問答上方提示」');

// ── [5] 一鍵投票 → 發點 → 接續頁橫幅 ──────────────────────────────
console.log('\n[5] 一鍵投票與發點');
const voteRes = await fetch(`${BASE}/s/v/${FORM_KEY}?f=vote&o=0&c=${mainId}`, {
  headers: { Cookie: `reader_session=${sessionCookie}` },
  redirect: 'manual'
});
check('投票端點回 302', voteRes.status === 302, `實際 ${voteRes.status}`);
const redirectTo = voteRes.headers.get('location') || '';
check('導向接續填答頁並帶 voted', redirectTo.includes(`/r/survey/${FORM_KEY}?voted=0`), redirectTo);

const ledger = sql(`SELECT count(*), coalesce(sum(delta), 0) FROM credit_txn
                    WHERE reader_id = ${readerId} AND reason = 'SURVEY_VOTE_REWARD'`);
check('帳本寫入一筆 5 點的投票獎勵', ledger === '1|5', `實際 ${ledger}`);
const credits = sql(`SELECT credits FROM reader WHERE id = ${readerId}`);
check('讀者餘額同步增加（100 → 105）', credits === '105', `實際 ${credits}`);

const banner = await getPage(`/r/survey/${FORM_KEY}?voted=0&c=${mainId}`, sessionCookie);
check('接續頁橫幅顯示實際發點數', banner.body.includes('已發送 5 點'), '橫幅未反映帳本');

// ── [6] 改票不重發 ────────────────────────────────────────────────
console.log('\n[6] 改票不重發');
await fetch(`${BASE}/s/v/${FORM_KEY}?f=vote&o=1&c=${mainId}`, {
  headers: { Cookie: `reader_session=${sessionCookie}` },
  redirect: 'manual'
});
const afterRevote = sql(`SELECT count(*), coalesce(sum(delta), 0) FROM credit_txn
                         WHERE reader_id = ${readerId} AND reason = 'SURVEY_VOTE_REWARD'`);
check('★ 改票後帳本仍只有一筆 5 點', afterRevote === '1|5', `實際 ${afterRevote}`);
const votes = sql(`SELECT count(*), max(option_value) FROM survey_vote
                   WHERE form_key = '${FORM_KEY}' AND identity_type = 'READER'`);
check('投票列被覆蓋成新選項（一人一票）', votes.startsWith('1|'), `實際 ${votes}`);

// 匿名投票不發點
const anonVote = await fetch(`${BASE}/s/v/${FORM_KEY}?f=vote&o=2`, { redirect: 'manual' });
check('匿名投票照樣轉址', anonVote.status === 302, `實際 ${anonVote.status}`);
const anonLedger = sql(`SELECT count(*) FROM credit_txn WHERE reason = 'SURVEY_VOTE_REWARD'`);
check('匿名投票不寫任何發點帳列', anonLedger === '1', `實際 ${anonLedger}`);

// ── [7][8] 瀏覽器實際操作與截圖 ───────────────────────────────────
console.log('\n[7] 後台快建面板實際操作 ＋ [8] 截圖');
const browser = await chromium.launch();
try {
  // 讀者頁：桌機與窄螢幕各一張
  const readerCtx = await browser.newContext({ viewport: { width: 1280, height: 1000 } });
  await readerCtx.addCookies([{
    name: 'reader_session', value: sessionCookie, domain: '127.0.0.1', path: '/'
  }]);
  const readerPage = await readerCtx.newPage();
  await readerPage.goto(`${BASE}/r/news/verify-main`, { waitUntil: 'networkidle' });
  const sideBox = await readerPage.locator('.article-side').boundingBox();
  const mainBox = await readerPage.locator('.article-main').boundingBox();
  check('桌機版側欄在主欄右側',
    sideBox && mainBox && sideBox.x > mainBox.x + mainBox.width - 10,
    `side.x=${sideBox?.x} main.right=${mainBox && mainBox.x + mainBox.width}`);
  await readerPage.screenshot({ path: `${OUT_DIR}/article-sidebar-desktop.png`, fullPage: true });

  await readerPage.setViewportSize({ width: 640, height: 1000 });
  await readerPage.waitForTimeout(300);
  const narrowSide = await readerPage.locator('.article-side').boundingBox();
  const narrowMain = await readerPage.locator('.article-main').boundingBox();
  check('窄螢幕側欄落到主欄下方',
    narrowSide && narrowMain && narrowSide.y > narrowMain.y,
    `side.y=${narrowSide?.y} main.y=${narrowMain?.y}`);
  await readerPage.screenshot({ path: `${OUT_DIR}/article-sidebar-narrow.png`, fullPage: true });

  // 後台：實際操作快建面板
  const adminPage = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  await adminPage.goto(`${BASE}/admin.html`, { waitUntil: 'networkidle' });
  await adminPage.fill('#gate-key', ADMIN_KEY);
  await adminPage.click('#gate-btn');
  await adminPage.waitForSelector('#tab-campaign', { timeout: 10000 });
  await adminPage.click('#tab-campaign');
  await adminPage.locator('#survey-panel summary').click();
  await adminPage.fill('#survey-quick-title', '面板實測：下一期想看什麼？');
  const optionInputs = adminPage.locator('#survey-quick-options input');
  await optionInputs.nth(0).fill('題目 A');
  await optionInputs.nth(1).fill('題目 B');
  await adminPage.click('#survey-add-option');
  await adminPage.locator('#survey-quick-options input').nth(2).fill('題目 C');
  await adminPage.fill('#markdown', '');
  await adminPage.click('#survey-quick-create');
  await adminPage.waitForFunction(
    () => (document.querySelector('#markdown')?.value || '').includes('<!--survey:'),
    null, { timeout: 15000 });
  const editorValue = await adminPage.inputValue('#markdown');
  check('★ 面板建立後標記自動插入編輯器', /<!--survey:vote-\d{8}-[a-z0-9]{4}-->/.test(editorValue),
    JSON.stringify(editorValue));
  check('標記獨立成段（前後有空行或位於開頭）', /^<!--survey:[^>]+-->\n\n/.test(editorValue.trim() + '\n\n'),
    JSON.stringify(editorValue));
  const panelMsg = await adminPage.textContent('#survey-quick-msg');
  check('面板顯示建立成功訊息', (panelMsg || '').includes('已建立並插入'), panelMsg || '');
  await adminPage.screenshot({ path: `${OUT_DIR}/admin-survey-panel.png` });

  // 預覽：問卷卡與點數提示要出現在後台預覽中
  await adminPage.click('#preview-btn');
  await adminPage.waitForTimeout(1500);
  const previewHtml = await adminPage.frameLocator('#preview').locator('body').innerHTML();
  check('後台預覽出現投票卡與點數提示',
    previewHtml.includes('投票即可獲得 5 點') && previewHtml.includes('預覽不計票'),
    previewHtml.slice(0, 200));

  // 面板上限：加到第 7 個選項要被擋
  for (let i = 0; i < 4; i++) await adminPage.click('#survey-add-option');
  const optionCount = await adminPage.locator('#survey-quick-options .form-row').count();
  check('選項列數上限 6（與後端驗證一致）', optionCount === 6, `實際 ${optionCount}`);

  const consoleErrors = [];
  adminPage.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });
  await adminPage.click('#survey-add-option');
  check('面板無 console 錯誤', consoleErrors.length === 0, consoleErrors.join(' | '));
} finally {
  await browser.close();
}

writeFileSync(`${OUT_DIR}/last-run.txt`,
  `base=${BASE}\nformKey=${FORM_KEY}\nreaderId=${readerId}\nfailures=${failures}\n`);

console.log(`\n=== 結果：${failures === 0 ? '全部通過' : `${failures} 項失敗`} ===`);
console.log(`截圖輸出：${OUT_DIR}/\n`);
process.exit(failures === 0 ? 0 : 1);
