/**
 * 電子報第 010 期（WebSocket／WebRTC）程式碼片段驗證腳本
 *
 * 用途：把 newsletter-10-realtime-websocket-webrtc.md 內文中的 JavaScript
 *       程式碼片段，實際放進真實 Chromium 執行，驗證其語法與行為是否正確，
 *       避免電子報寄出「看起來對但跑不起來」的範例。
 *
 * 驗證項目：
 *   T1  WebSocket 自動重連片段：語法可執行、指數退避序列符合 1s→2s→4s…上限 30s
 *   T2  WebRTC 片段：在真實 RTCPeerConnection 上執行，檢查 createOffer 回傳結構
 *   T3  WebRTC 訊令封包：驗證 `{ type:'offer', sdp: offer }` 能否被對端接受
 *   T4  STUN 伺服器位址：驗證 RTCPeerConnection 接受該 iceServers 設定
 *
 * 執行方式（PowerShell 7+）：
 *   node newsletter/scripts/verify-010-code.mjs
 *
 * 相依：teaching-site/node_modules/playwright（專案既有安裝）
 */

import { chromium } from '../../teaching-site/node_modules/playwright/index.mjs';

/** 測試結果收集器：記錄每項檢查的通過與否與說明 */
const results = [];
function record(id, name, passed, detail) {
  results.push({ id, name, passed, detail });
  const mark = passed ? 'PASS' : 'FAIL';
  console.log(`[${mark}] ${id} ${name}\n       ${detail}\n`);
}

const browser = await chromium.launch();
const page = await browser.newPage();
// 需要一個真實來源才能使用 WebRTC / WebSocket API，about:blank 亦可但改用 data URL 較穩定
await page.goto('about:blank');

/* ------------------------------------------------------------------ *
 * T1：WebSocket 自動重連片段（電子報第 115–127 行）
 *     以假的 WebSocket 建構子取代真實連線，量測連續斷線的重連延遲序列。
 * ------------------------------------------------------------------ */
const t1 = await page.evaluate(() => {
  const delays = [];          // 記錄每次 setTimeout 的延遲值
  let closeHook = null;       // 保存目前連線的 onclose，供測試主動觸發

  // 假的 WebSocket：不建立真實連線，只暴露 onopen/onmessage/onclose 掛載點
  class FakeWebSocket {
    constructor(url) { this.url = url; FakeWebSocket.last = this; }
  }
  const realSetTimeout = setTimeout;
  const originalWebSocket = window.WebSocket;
  window.WebSocket = FakeWebSocket;
  // 攔截 setTimeout：記錄延遲但立即執行，避免測試等待真實時間
  window.setTimeout = (fn, ms) => { delays.push(ms); return realSetTimeout(fn, 0); };

  // ↓↓↓ 以下為電子報原文片段，未做任何修改 ↓↓↓
  let ws, delay = 1000;
  function connect() {
    ws = new WebSocket('wss://example.com/ws');
    ws.onopen = () => { delay = 1000; };            // 連上就重置退避
    ws.onmessage = (e) => handle(JSON.parse(e.data));
    ws.onclose = () => {                            // 斷線：指數退避＋抖動重連
      setTimeout(connect, delay + Math.random() * 500);
      delay = Math.min(delay * 2, 30000);
    };
  }
  connect();
  // ↑↑↑ 電子報原文片段結束 ↑↑↑

  // 連續觸發 8 次斷線，觀察退避是否遞增並封頂
  for (let i = 0; i < 8; i++) {
    closeHook = FakeWebSocket.last.onclose;
    closeHook();
  }

  window.WebSocket = originalWebSocket;
  window.setTimeout = realSetTimeout;
  // 扣掉抖動後的基底延遲（floor 到 500 的倍數以還原基底）
  return { delays, base: delays.map((d) => Math.floor(d / 500) * 500) };
});

// 期望基底序列：1000, 2000, 4000, 8000, 16000, 30000, 30000, 30000
const expectedBase = [1000, 2000, 4000, 8000, 16000, 30000, 30000, 30000];
const t1Pass = JSON.stringify(t1.base) === JSON.stringify(expectedBase);
record(
  'T1', 'WebSocket 重連片段：語法可執行且指數退避正確',
  t1Pass,
  `實測基底延遲(ms)=${JSON.stringify(t1.base)}；期望=${JSON.stringify(expectedBase)}`
);

/* ------------------------------------------------------------------ *
 * T2 / T3 / T4：WebRTC 片段（電子報第 171–180 行）
 * ------------------------------------------------------------------ */
const rtc = await page.evaluate(async () => {
  const out = {};
  const sent = [];                                   // 攔截 signaling 送出的封包
  const signaling = { send: (msg) => sent.push(msg) };

  // ↓↓↓ 以下為電子報原文片段，未做任何修改 ↓↓↓
  // 1. 造一個 PeerConnection（帶 STUN）
  const pc = new RTCPeerConnection({ iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] });
  // 2. 先掛好 ICE 監聽——候選位址一產生就送出，直到雙方找到能通的路
  pc.onicecandidate = (e) => e.candidate && signaling.send({ type: 'ice', candidate: e.candidate });
  // 3. 我方開價（offer）→ 經信令伺服器交給對方 → 對方回價（answer）
  const offer = await pc.createOffer();
  await pc.setLocalDescription(offer);
  signaling.send({ type: 'offer', sdp: offer.sdp });   // 送 offer.sdp 字串，不是整個 offer 物件
  // ↑↑↑ 電子報原文片段結束 ↑↑↑

  // T4：確認 iceServers 設定被瀏覽器接受（未拋錯即為接受），並回報實際生效設定
  out.iceServers = pc.getConfiguration().iceServers;

  // T2：createOffer 回傳的物件結構
  out.offerType = offer.type;
  out.sdpTypeOf = typeof offer.sdp;
  out.sdpHead = String(offer.sdp).slice(0, 6);

  // T3：把片段實際送出的封包，交給「對端」嘗試 setRemoteDescription
  const packet = sent.find((m) => m.type === 'offer');
  const peer = new RTCPeerConnection();
  try {
    await peer.setRemoteDescription({ type: 'offer', sdp: packet.sdp });
    out.asStringField = 'accepted';
  } catch (err) {
    out.asStringField = `rejected: ${err.name}: ${err.message}`;
  }

  // 反例佐證：舊寫法 sdp: offer（整個物件）確實會被對端拒絕，
  // 這是電子報「送 offer.sdp 字串，不是整個 offer 物件」那句提醒的依據
  const peerBad = new RTCPeerConnection();
  try {
    await peerBad.setRemoteDescription({ type: 'offer', sdp: offer });
    out.objectForm = 'accepted';
  } catch (err) {
    out.objectForm = `rejected: ${err.name}`;
  }

  // T5：佐證「createOffer 前要先 addTrack/createDataChannel」——
  // 未加時 SDP 無 m= 行，加了才有
  out.hasMediaLineWithout = String(offer.sdp).includes('\nm=');
  const pc2 = new RTCPeerConnection();
  pc2.createDataChannel('demo');
  const offer2 = await pc2.createOffer();
  out.hasMediaLineWith = String(offer2.sdp).includes('\nm=');

  pc.close(); peer.close(); peerBad.close(); pc2.close();
  return out;
});

record(
  'T2', 'createOffer() 回傳結構符合規格（type/sdp）',
  rtc.offerType === 'offer' && rtc.sdpTypeOf === 'string' && rtc.sdpHead.startsWith('v=0'),
  `type=${rtc.offerType}, typeof sdp=${rtc.sdpTypeOf}, sdp 開頭=${JSON.stringify(rtc.sdpHead)}`
);

record(
  'T3', '訊令封包的 sdp 欄位可被對端 setRemoteDescription 直接接受',
  rtc.asStringField === 'accepted' && rtc.objectForm.startsWith('rejected'),
  `送 offer.sdp 字串 → ${rtc.asStringField}；反例：送整個 offer 物件 → ${rtc.objectForm}`
);

record(
  'T4', 'STUN 設定 stun:stun.l.google.com:19302 被瀏覽器接受',
  JSON.stringify(rtc.iceServers).includes('stun.l.google.com:19302'),
  `getConfiguration().iceServers=${JSON.stringify(rtc.iceServers)}`
);

record(
  'T5', '佐證：createOffer 前需先 addTrack/createDataChannel，否則產生空 offer',
  rtc.hasMediaLineWithout === false && rtc.hasMediaLineWith === true,
  `未加媒體時 SDP 含 m= 行=${rtc.hasMediaLineWithout}；` +
  `呼叫 createDataChannel 後=${rtc.hasMediaLineWith}（電子報已就此提醒讀者）`
);

await browser.close();

/* ---------------------------- 總結 ---------------------------- */
const failed = results.filter((r) => !r.passed);
console.log('='.repeat(64));
console.log(`總計 ${results.length} 項，通過 ${results.length - failed.length} 項，未通過 ${failed.length} 項`);
if (failed.length) {
  console.log('未通過項目：');
  failed.forEach((r) => console.log(`  - ${r.id} ${r.name}`));
}
process.exit(failed.length ? 1 : 0);
