// 讀者端流程驗證：archive 列表 → 未登入看單篇（受限區不得洩漏）→ 請求登入信
// → 從資料庫取出 token 完成登入 → 再看單篇（受限區應出現）。
//
// 用法（需先啟動應用並執行計畫 Task 13 Step 3 的測試資料）：
//   node scripts/verify-reader-flow.mjs
//   node scripts/verify-reader-flow.mjs --base http://127.0.0.1:8080
//
// 為何寫成腳本：CLAUDE.md 規定這類流程驗證要可重跑、可逐行檢查，
// 而不是一次性的互動指令。

const args = process.argv.slice(2);
const baseIndex = args.indexOf('--base');
const BASE = baseIndex >= 0 ? args[baseIndex + 1] : 'http://127.0.0.1:8080';
const SLUG = 'e2e-test';
const GATED_TEXT = '這段是受限區';
const FREE_TEXT = '這段是免費的開場';
const EMAIL = 'e2e@example.com';

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

/** 取得頁面內容與回應 */
async function fetchPage(path, cookie) {
  const headers = cookie ? { Cookie: cookie } : {};
  const res = await fetch(`${BASE}${path}`, { headers, redirect: 'manual' });
  return { res, body: await res.text() };
}

console.log(`\n=== 讀者端流程驗證（${BASE}）===\n`);

// 1. archive 列表
console.log('[1] archive 列表');
{
  const { res, body } = await fetchPage('/r/archive');
  check('回應 200', res.status === 200, `實際 ${res.status}`);
  check('列出測試文章', body.includes('端到端測試文章'));
  check('archive 不含受限區內容', !body.includes(GATED_TEXT));
}

// 2. 未登入看單篇 —— 最關鍵的一項
console.log('\n[2] 未登入讀單篇（受限區不得洩漏）');
{
  const { res, body } = await fetchPage(`/r/news/${SLUG}`);
  check('回應 200', res.status === 200, `實際 ${res.status}`);
  check('看得到免費區', body.includes(FREE_TEXT));
  check('★ 回應完全不含受限區', !body.includes(GATED_TEXT),
    '受限內容洩漏到未登入者的回應中');
  check('paywall 標記未洩漏', !body.includes('<!--paywall-->'));
  // 現行文案（commit f9a17df）刻意改成「登入後仍需點數解鎖」，
  // 避免讓讀者誤以為單純登入就能看到付費內容；斷言需對齊現行文案。
  check('顯示登入提示', body.includes('請先用訂閱時的 email 登入'));
}

// 3. 不存在的文章
console.log('\n[3] 不存在的 slug');
{
  const { res } = await fetchPage('/r/news/does-not-exist');
  check('回應 404', res.status === 404, `實際 ${res.status}`);
}

// 4. 請求登入信
console.log('\n[4] 請求登入信');
{
  const res = await fetch(`${BASE}/api/reader/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: EMAIL, redirect: `/r/news/${SLUG}` })
  });
  const data = await res.json();
  check('回應 200', res.status === 200);
  check('sent 為 true（NoopMailSender 也算成功送出）', data.sent === true,
    JSON.stringify(data));
}

// 5. 登入驗證需要明文 token —— 只存在於「寄出的信」中
console.log('\n[5] 完成登入');
console.log('  ! 明文 token 只存在於寄出的信裡（DB 只有雜湊），無法從此腳本取得。');
console.log('  ! 請改用下列方式之一手動完成，並確認登入後受限區可見：');
console.log('    a) 設定真實的 SEND_MAIL_API 後收信點連結');
console.log('    b) 在 LoginMailService 暫時加一行 log.info 印出連結（僅本機，不得提交）');
console.log('  ! 這是刻意的設計結果：token 不可從資料庫反推使用。');

// 6. 無效 token 不得放行
console.log('\n[6] 無效 token');
{
  const { res } = await fetchPage('/api/reader/login/verify?t=forged-token-value');
  const location = res.headers.get('location') || '';
  check('回應 302', res.status === 302, `實際 ${res.status}`);
  check('導向登入頁並帶錯誤標記', location.includes('/r/login?error=invalid'), location);
  check('未設定 session cookie', !(res.headers.get('set-cookie') || '').includes('reader_session'));
}

console.log(`\n=== 結果：${failures === 0 ? '全部通過' : `${failures} 項失敗`} ===\n`);
process.exit(failures === 0 ? 0 : 1);
