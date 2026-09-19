/**
 * 電子報 018 期示範驗證腳本：對公網網址實測四種流量是否穿過 Cloudflare Tunnel。
 *
 * 測試項目：
 *   1. REST  GET /api/hello       — 應回 200 且帶 cf-ray
 *   2. SSE   GET /api/sse         — 5 個事件應「逐秒到達」（間隔 >0.5s 表示未被緩衝）
 *   3. Webhook POST /api/webhook  — 從公網 POST 後，查 /api/webhook/latest 應查得到
 *   4. WebSocket wss://…/ws       — 送出訊息應收到含 cfRay 的回聲
 *
 * 執行方式：node newsletter/demo-018/verify-tunnel.mjs <公網網址>
 * 例如：    node newsletter/demo-018/verify-tunnel.mjs https://xxx.trycloudflare.com
 */

/** 待測的公網基底網址（去除尾端斜線）。 */
const BASE = (process.argv[2] ?? '').replace(/\/$/, '');
if (!BASE.startsWith('http')) {
  console.error('用法：node verify-tunnel.mjs https://<你的隧道網址>');
  process.exit(1);
}

/** 累計失敗數；結束時決定 exit code。 */
let failures = 0;

/**
 * 輸出單項測試結果並累計失敗。
 * @param {string} name 測試名稱
 * @param {boolean} pass 是否通過
 * @param {string} detail 補充說明
 */
function report(name, pass, detail) {
  console.log(`${pass ? '✅' : '❌'} ${name}：${detail}`);
  if (!pass) failures += 1;
}

// ── 1. REST API ──────────────────────────────────────────────
const helloRes = await fetch(`${BASE}/api/hello`);
const hello = await helloRes.json();
report('REST /api/hello', helloRes.ok && !!hello.viaCloudflare?.cfRay,
  `HTTP ${helloRes.status}，host=${hello.host}，cf-ray=${hello.viaCloudflare?.cfRay}`);

// ── 2. SSE 串流：量測每個事件的「到達時間差」───────────────────
const sseRes = await fetch(`${BASE}/api/sse`);
const arrivals = [];
const reader = sseRes.body.getReader();
const decoder = new TextDecoder();
let sseBuffer = '';
for (;;) {
  const { done, value } = await reader.read();
  if (done) break;
  sseBuffer += decoder.decode(value, { stream: true });
  // 依 SSE 規格用空行切事件；記錄每個完整事件的本地到達時刻
  let idx;
  while ((idx = sseBuffer.indexOf('\n\n')) >= 0) {
    const raw = sseBuffer.slice(0, idx);
    sseBuffer = sseBuffer.slice(idx + 2);
    // 以冒號開頭的是 SSE 註解（伺服器的反緩衝填充），依規格忽略
    if (raw.startsWith(':')) continue;
    arrivals.push({ at: Date.now(), data: JSON.parse(raw.replace(/^data: /, '')) });
  }
}
// 計算相鄰事件的到達間隔：若被緩衝會全部同時到（間隔≈0）
const gaps = arrivals.slice(1).map((a, i) => a.at - arrivals[i].at);
const minGap = Math.min(...gaps);
report('SSE /api/sse 逐筆到達', arrivals.length === 5 && minGap > 500,
  `${arrivals.length} 個事件，相鄰到達間隔 ${gaps.map((g) => g + 'ms').join(', ')}（最小 ${minGap}ms，>500ms 即未被緩衝）`);

// ── 3. Webhook：模擬外部服務從公網 POST 回調──────────────────
const marker = `verify-${Date.now()}`;
const postRes = await fetch(`${BASE}/api/webhook`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ event: 'demo.verified', marker, from: 'verify-tunnel.mjs' }),
});
const latest = await (await fetch(`${BASE}/api/webhook/latest`)).json();
report('Webhook POST /api/webhook', postRes.ok && latest.latest?.payload?.marker === marker,
  `HTTP ${postRes.status}，本機已存 marker=${latest.latest?.payload?.marker}，來源 IP=${latest.latest?.viaCloudflare?.cfConnectingIp}`);

// ── 4. WebSocket：Node 24 原生 WebSocket client 直連 wss──────
const wsResult = await new Promise((resolve) => {
  const ws = new WebSocket(`${BASE.replace(/^http/, 'ws')}/ws`);
  const timeout = setTimeout(() => { ws.close(); resolve(null); }, 10000);
  ws.onopen = () => ws.send('哈囉，隧道另一端的本機！');
  ws.onmessage = (event) => {
    clearTimeout(timeout);
    ws.close();
    resolve(JSON.parse(event.data));
  };
  ws.onerror = () => { clearTimeout(timeout); resolve(null); };
});
report('WebSocket /ws 回聲', wsResult?.echo === '哈囉，隧道另一端的本機！',
  wsResult ? `echo=「${wsResult.echo}」，host=${wsResult.host}，cf-ray=${wsResult.cfRay}` : '未收到回聲（逾時或連線失敗）');

// ── 5. WebSocket 串流：與 SSE 對照，驗證 http2 backhaul 是否緩衝 WS──
const wsGaps = await new Promise((resolve) => {
  const ws = new WebSocket(`${BASE.replace(/^http/, 'ws')}/ws`);
  /** 每筆訊息的本地到達時刻。 */
  const times = [];
  const timeout = setTimeout(() => { ws.close(); resolve(null); }, 15000);
  ws.onopen = () => ws.send('stream');
  ws.onmessage = () => {
    times.push(Date.now());
    if (times.length >= 5) {
      clearTimeout(timeout);
      ws.close();
      // 回傳相鄰訊息的到達間隔（毫秒）
      resolve(times.slice(1).map((t, i) => t - times[i]));
    }
  };
  ws.onerror = () => { clearTimeout(timeout); resolve(null); };
});
report('WebSocket 串流逐筆到達', !!wsGaps && Math.min(...wsGaps) > 500,
  wsGaps ? `5 筆訊息，相鄰到達間隔 ${wsGaps.map((g) => g + 'ms').join(', ')}（最小 ${Math.min(...wsGaps)}ms）` : '未收滿 5 筆（逾時或連線失敗）');

// ── 總結 ─────────────────────────────────────────────────────
console.log(failures === 0 ? '\n🎉 四項全部通過：Tunnel 可承載 REST / SSE / Webhook / WebSocket。' : `\n⚠️ 有 ${failures} 項失敗`);
process.exit(failures === 0 ? 0 : 1);
