/**
 * 電子報第 018 期示範伺服器：驗證 Cloudflare Tunnel 把本機服務送上公網。
 *
 * 模擬電子報中「前端頁面 + /api 後端」同源架構，並涵蓋三種長連線／回調場景：
 *   - GET  /               回傳 index.html（模擬 React 前端）
 *   - GET  /api/hello      回傳 JSON（模擬 REST API）
 *   - GET  /api/sse        SSE 串流：每秒吐一個事件，驗證經 Tunnel 不被緩衝（018 避坑點一）
 *   - POST /api/webhook    接收外部回調並暫存（019 期 Webhook 主題）
 *   - GET  /api/webhook/latest  查看最近收到的 Webhook
 *   - WS   /ws             WebSocket 回聲伺服器（零依賴手刻握手與訊框）
 *
 * 執行方式：node newsletter/demo-018/server.mjs
 * 發布方式：cloudflared tunnel --protocol http2 --url http://localhost:8018
 */
import { createServer } from 'node:http';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { hostname } from 'node:os';

/** 服務埠號；避開常用的 8080/5173 以免與其他開發服務衝突。 */
const PORT = 8018;

/** index.html 所在目錄（與本腳本同層）。 */
const ROOT = dirname(fileURLToPath(import.meta.url));

/** 啟動時間，用來在 API 回應中證明是同一個本機行程在服務公網請求。 */
const STARTED_AT = new Date().toISOString();

/** 最近收到的 Webhook 回調（記憶體暫存，最多保留 10 筆）。 */
const receivedWebhooks = [];

/**
 * 從請求標頭擷取 Cloudflare 邊緣資訊，證明流量經過 Cloudflare。
 * @param {import('node:http').IncomingMessage} req 收到的請求
 * @returns {{cfRay: string|null, cfConnectingIp: string|null, cfIpCountry: string|null}}
 */
function cloudflareInfo(req) {
  return {
    cfRay: req.headers['cf-ray'] ?? null,
    cfConnectingIp: req.headers['cf-connecting-ip'] ?? null,
    cfIpCountry: req.headers['cf-ipcountry'] ?? null,
  };
}

const server = createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');

  // 模擬後端 REST API：回傳本機資訊，證明公網請求真的打進了這台電腦
  if (url.pathname === '/api/hello') {
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({
      message: '你好，這個回應來自凱文大叔的本機電腦！',
      host: hostname(),
      startedAt: STARTED_AT,
      servedAt: new Date().toISOString(),
      viaCloudflare: cloudflareInfo(req),
    }, null, 2));
    return;
  }

  // SSE 串流：每秒送出一個事件共 5 個，模擬 AI 打字機效果。
  // 重點標頭即電子報 018 避坑點一的解法（X-Accel-Buffering: no + 不壓縮）。
  if (url.pathname === '/api/sse') {
    res.writeHead(200, {
      'Content-Type': 'text/event-stream; charset=utf-8',
      'Cache-Control': 'no-cache',
      'X-Accel-Buffering': 'no',
      Connection: 'keep-alive',
    });
    // 反緩衝填充：部分代理（含 Cloudflare 鏈路實測）會攢滿最小位元組門檻才 flush，
    // 先送一段 SSE 規格允許的註解行（以冒號開頭，客戶端會忽略）衝過門檻
    res.write(':' + ' '.repeat(4096) + '\n\n');
    let count = 0;
    const timer = setInterval(() => {
      count += 1;
      // 每個事件帶伺服器端時間戳，讓客戶端能比對「送出時間 vs 到達時間」驗證未被緩衝
      res.write(`data: ${JSON.stringify({ seq: count, sentAt: new Date().toISOString() })}\n\n`);
      if (count >= 5) {
        clearInterval(timer);
        res.end();
      }
    }, 1000);
    req.on('close', () => clearInterval(timer));
    return;
  }

  // Webhook 接收端：外部服務 POST 進來的回調存入記憶體，模擬 019 期本機收真實 Webhook
  if (url.pathname === '/api/webhook' && req.method === 'POST') {
    let body = '';
    req.on('data', (chunk) => { body += chunk; });
    req.on('end', () => {
      const record = {
        receivedAt: new Date().toISOString(),
        payload: (() => { try { return JSON.parse(body); } catch { return body; } })(),
        viaCloudflare: cloudflareInfo(req),
      };
      receivedWebhooks.unshift(record);
      receivedWebhooks.length = Math.min(receivedWebhooks.length, 10);
      res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ ok: true, stored: record }, null, 2));
    });
    return;
  }

  // 查詢最近收到的 Webhook，驗證回調真的送達本機
  if (url.pathname === '/api/webhook/latest') {
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ count: receivedWebhooks.length, latest: receivedWebhooks[0] ?? null }, null, 2));
    return;
  }

  // 其餘路徑一律回傳前端頁面（模擬 SPA fallback）
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  res.end(readFileSync(join(ROOT, 'index.html')));
});

// ── WebSocket 回聲伺服器（零依賴：手刻 RFC 6455 握手與文字訊框）─────────────

/** RFC 6455 規定的握手固定 GUID。 */
const WS_GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11';

/**
 * 將文字打包成伺服器端 WebSocket 文字訊框（伺服器端不需遮罩）。
 * @param {string} text 要送出的文字
 * @returns {Buffer} 完整訊框位元組
 */
function encodeTextFrame(text) {
  const payload = Buffer.from(text, 'utf-8');
  // 0x81 = FIN + 文字訊框；長度 <126 用 1 byte，否則用 126 + 2 bytes 擴充長度
  if (payload.length < 126) {
    return Buffer.concat([Buffer.from([0x81, payload.length]), payload]);
  }
  const header = Buffer.alloc(4);
  header[0] = 0x81;
  header[1] = 126;
  header.writeUInt16BE(payload.length, 2);
  return Buffer.concat([header, payload]);
}

server.on('upgrade', (req, socket) => {
  if (new URL(req.url, 'http://localhost').pathname !== '/ws') {
    socket.destroy();
    return;
  }

  // 完成 RFC 6455 握手：以 SHA-1 計算 Sec-WebSocket-Accept
  const accept = createHash('sha1')
    .update(req.headers['sec-websocket-key'] + WS_GUID)
    .digest('base64');
  socket.write(
    'HTTP/1.1 101 Switching Protocols\r\n' +
    'Upgrade: websocket\r\n' +
    'Connection: Upgrade\r\n' +
    `Sec-WebSocket-Accept: ${accept}\r\n\r\n`,
  );

  const cf = cloudflareInfo(req);

  socket.on('data', (buffer) => {
    const opcode = buffer[0] & 0x0f;
    if (opcode === 0x8) { // 關閉訊框：回應後結束連線
      socket.end(Buffer.from([0x88, 0x00]));
      return;
    }
    if (opcode !== 0x1) return; // 僅處理文字訊框

    // 解析客戶端訊框：長度欄位 + 4 bytes 遮罩金鑰，逐位元組解遮罩
    let len = buffer[1] & 0x7f;
    let offset = 2;
    if (len === 126) { len = buffer.readUInt16BE(2); offset = 4; }
    const mask = buffer.subarray(offset, offset + 4);
    const data = buffer.subarray(offset + 4, offset + 4 + len);
    const text = Buffer.from(data.map((b, i) => b ^ mask[i % 4])).toString('utf-8');

    // 串流模式：收到「stream」時每秒推 1 筆共 5 筆，
    // 用來與 SSE 對照——驗證 http2 backhaul 是否也會緩衝 WebSocket
    if (text === 'stream') {
      let seq = 0;
      const wsTimer = setInterval(() => {
        seq += 1;
        socket.write(encodeTextFrame(JSON.stringify({ seq, sentAt: new Date().toISOString() })));
        if (seq >= 5) clearInterval(wsTimer);
      }, 1000);
      socket.on('close', () => clearInterval(wsTimer));
      return;
    }

    // 回聲：附上伺服器時間與 Cloudflare 邊緣資訊，證明雙向通道經過隧道
    socket.write(encodeTextFrame(JSON.stringify({
      echo: text,
      host: hostname(),
      at: new Date().toISOString(),
      cfRay: cf.cfRay,
    })));
  });

  socket.on('error', () => socket.destroy());
});

server.listen(PORT, () => {
  console.log(`示範伺服器已啟動：http://localhost:${PORT}（REST + SSE + Webhook + WebSocket）`);
});
