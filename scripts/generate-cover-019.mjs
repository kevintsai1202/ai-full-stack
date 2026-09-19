// 電子報第 019 期封面產生腳本：使用 Playwright 依據 016/017/018 風格渲染 16:9 高解析度封面
import { readFile } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { existsSync } from 'node:fs';

const here = dirname(fileURLToPath(import.meta.url));
const rootDir = join(here, '..');

/** 動態載入 playwright */
async function loadPlaywright() {
  try { return await import('playwright'); } catch {}
  const roots = [
    process.env.APPDATA && join(process.env.APPDATA, 'npm', 'node_modules'),
    process.env.ProgramFiles && join(process.env.ProgramFiles, 'nodejs', 'node_modules'),
    '/usr/local/lib/node_modules', '/usr/lib/node_modules',
  ].filter(Boolean);
  for (const root of roots) {
    const entry = join(root, 'playwright', 'index.js');
    if (existsSync(entry)) return await import(pathToFileURL(entry).href);
  }
  throw new Error('找不到 playwright');
}

const mod = await loadPlaywright();
const { chromium } = mod.default ?? mod;

const htmlContent = `<!DOCTYPE html>
<html lang="zh-TW">
<head>
<meta charset="UTF-8">
<style>
  @import url('https://fonts.googleapis.com/css2?family=Noto+Sans+TC:wght@400;500;600;700;800;900&family=JetBrains+Mono:wght@600;700;800&display=swap');

  * {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
    -webkit-font-smoothing: antialiased;
  }

  body {
    width: 1376px;
    height: 768px;
    background: linear-gradient(150deg, #f0f6fc 0%, #e6eff8 45%, #dbe8f4 100%);
    font-family: 'Noto Sans TC', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    position: relative;
    overflow: hidden;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: space-between;
    padding: 34px 44px 28px;
  }

  /* 裝飾性背景光斑 */
  .glow-left {
    position: absolute;
    width: 550px;
    height: 550px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(14, 165, 233, 0.09) 0%, rgba(255, 255, 255, 0) 70%);
    top: -120px;
    left: -120px;
    pointer-events: none;
  }

  .glow-right {
    position: absolute;
    width: 550px;
    height: 550px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(16, 185, 129, 0.09) 0%, rgba(255, 255, 255, 0) 70%);
    bottom: -120px;
    right: -120px;
    pointer-events: none;
  }

  /* 頂部標題區 */
  .header-section {
    display: flex;
    flex-direction: column;
    align-items: center;
    text-align: center;
    width: 100%;
    z-index: 10;
  }

  .badge-row {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-bottom: 12px;
  }

  .badge-line {
    width: 130px;
    height: 2px;
    background: #94a3b8;
    position: relative;
  }

  .badge-line.left::after {
    content: '';
    position: absolute;
    right: 0;
    top: -4px;
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: #334155;
  }

  .badge-line.right::before {
    content: '';
    position: absolute;
    left: 0;
    top: -4px;
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: #334155;
  }

  .issue-badge {
    background: #1e293b;
    color: #ffffff;
    padding: 6px 28px;
    border-radius: 9999px;
    font-size: 22px;
    font-weight: 800;
    letter-spacing: 1px;
    box-shadow: 0 4px 12px rgba(15, 23, 42, 0.18);
  }

  .issue-badge span {
    color: #fbbf24;
  }

  .main-title {
    font-size: 40px;
    font-weight: 900;
    color: #0f172a;
    line-height: 1.2;
    letter-spacing: -0.5px;
    margin-bottom: 8px;
  }

  .main-subtitle {
    font-size: 20px;
    font-weight: 700;
    color: #1e293b;
    letter-spacing: -0.2px;
  }

  /* 中間雙卡片 */
  .cards-container {
    display: flex;
    gap: 26px;
    width: 100%;
    max-width: 1288px;
    z-index: 10;
    justify-content: center;
  }

  .card {
    flex: 1;
    background: #ffffff;
    border-radius: 20px;
    border: 1.5px solid #cbd5e1;
    box-shadow: 0 14px 30px -6px rgba(15, 23, 42, 0.1), 0 4px 12px -2px rgba(15, 23, 42, 0.05);
    padding: 18px 22px 16px;
    display: flex;
    flex-direction: column;
    align-items: center;
    position: relative;
    height: 395px;
  }

  .card-pill {
    background: #1e293b;
    color: #ffffff;
    font-size: 13px;
    font-weight: 700;
    padding: 4px 18px;
    border-radius: 9999px;
    margin-bottom: 6px;
    letter-spacing: 0.5px;
  }

  .card-title {
    font-size: 25px;
    font-weight: 900;
    color: #0f172a;
    margin-bottom: 3px;
  }

  .card-subtitle {
    font-size: 15px;
    font-weight: 700;
    color: #334155;
    margin-bottom: 12px;
  }

  .card-body {
    width: 100%;
    flex: 1;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
  }

  /* 左卡片：架構圖 */
  .flow-container {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    margin-top: 2px;
  }

  .source-col {
    width: 145px;
    background: #f8fafc;
    border: 1.5px solid #e2e8f0;
    border-radius: 14px;
    padding: 10px 8px;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    box-shadow: 0 2px 6px rgba(0,0,0,0.03);
  }

  .source-heading {
    font-size: 13px;
    font-weight: 800;
    color: #0f172a;
  }

  .brand-pills {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 4px;
    width: 100%;
  }

  .brand-pill {
    font-size: 10.5px;
    font-weight: 700;
    padding: 2px 6px;
    border-radius: 6px;
    background: #f1f5f9;
    color: #475569;
    border: 1px solid #e2e8f0;
  }

  .brand-pill.stripe { background: #635bff15; color: #635bff; border-color: #635bff40; }
  .brand-pill.line { background: #06c75515; color: #06c755; border-color: #06c75540; }
  .brand-pill.github { background: #24292f15; color: #24292f; border-color: #24292f40; }

  .timeout-warning {
    background: #fee2e2;
    border: 1.5px solid #f87171;
    color: #dc2626;
    font-size: 11px;
    font-weight: 800;
    border-radius: 8px;
    padding: 3px 6px;
    text-align: center;
    width: 100%;
    margin-top: 2px;
  }

  /* Tunnel 箭頭區 */
  .tunnel-bridge {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
  }

  .tunnel-badge {
    background: #d9f1ec;
    color: #0f766e;
    border: 1px solid #99f6e4;
    font-size: 11px;
    font-weight: 800;
    padding: 3px 8px;
    border-radius: 9999px;
    white-space: nowrap;
  }

  .tunnel-arrow-svg {
    width: 48px;
    height: 18px;
  }

  /* 本地端 Spring Boot + IDE 容器 */
  .dest-col {
    flex: 1;
    background: #f8fafc;
    border: 1.5px solid #cbd5e1;
    border-radius: 14px;
    padding: 10px 12px;
    display: flex;
    flex-direction: column;
    gap: 6px;
    box-shadow: 0 2px 6px rgba(0,0,0,0.03);
  }

  .dest-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-bottom: 4px;
    border-bottom: 1px solid #e2e8f0;
  }

  .dest-title {
    font-size: 13px;
    font-weight: 800;
    color: #0f172a;
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .dest-port {
    font-family: 'JetBrains Mono', monospace;
    font-size: 11px;
    font-weight: 700;
    color: #0f766e;
    background: #ccfbf1;
    padding: 1px 6px;
    border-radius: 4px;
  }

  .dest-steps {
    display: flex;
    flex-direction: column;
    gap: 5px;
  }

  .dest-step {
    display: flex;
    align-items: center;
    gap: 8px;
    background: #ffffff;
    border: 1.5px solid #e2e8f0;
    border-radius: 8px;
    padding: 5px 8px;
    font-size: 12px;
    font-weight: 700;
    color: #1e293b;
  }

  .dest-step.step-1 { border-color: #fde68a; background: #fffbeb; }
  .dest-step.step-2 { border-color: #93c5fd; background: #eff6ff; color: #1d4ed8; }
  .dest-step.step-3 { border-color: #a7f3d0; background: #f0fdf4; color: #065f46; }

  .dot-icon {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    flex-shrink: 0;
  }
  .dot-red { background: #ef4444; box-shadow: 0 0 6px #ef4444; }
  .dot-blue { background: #3b82f6; }
  .dot-green { background: #10b981; }

  /* 右卡片：串流枯竭與防護 */
  .exhaust-warning {
    background: #fef2f2;
    border: 1.5px solid #fca5a5;
    border-radius: 12px;
    padding: 8px 12px;
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 8px;
  }

  .exhaust-tag {
    background: #dc2626;
    color: #ffffff;
    font-size: 11px;
    font-weight: 800;
    padding: 2px 7px;
    border-radius: 6px;
    white-space: nowrap;
  }

  .exhaust-desc {
    font-size: 11.5px;
    font-weight: 700;
    color: #991b1b;
    line-height: 1.35;
  }

  .pipeline-cards {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 7px;
  }

  .pipe-item {
    background: #f8fafc;
    border: 1.5px solid #e2e8f0;
    border-radius: 10px;
    padding: 7px 10px;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .pipe-item.teal {
    background: #f0fdfa;
    border-color: #99f6e4;
  }

  .pipe-item.blue {
    background: #eff6ff;
    border-color: #bfdbfe;
  }

  .pipe-title {
    font-size: 12px;
    font-weight: 800;
    color: #0f172a;
    display: flex;
    align-items: center;
    gap: 5px;
  }

  .pipe-code {
    font-family: 'JetBrains Mono', monospace;
    font-size: 10.5px;
    font-weight: 700;
    color: #475569;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .pipe-item.teal .pipe-title { color: #0f766e; }
  .pipe-item.teal .pipe-code { color: #115e59; }
  .pipe-item.blue .pipe-title { color: #1d4ed8; }
  .pipe-item.blue .pipe-code { color: #1e40af; }

  /* 卡片底部膠囊 */
  .card-bottom-pill {
    background: #eef5fc;
    color: #1e40af;
    font-size: 13.5px;
    font-weight: 700;
    padding: 6px 18px;
    border-radius: 9999px;
    text-align: center;
    border: 1px solid #bfdbfe;
    width: 100%;
    margin-top: 10px;
  }

  /* 底部導覽系列膠囊 */
  .footer-roadmap {
    display: flex;
    align-items: center;
    gap: 12px;
    z-index: 10;
  }

  .roadmap-pill {
    background: #ffffff;
    border: 1.5px solid #cbd5e1;
    color: #1e293b;
    padding: 7px 22px;
    border-radius: 9999px;
    font-size: 15px;
    font-weight: 700;
    display: flex;
    align-items: center;
    gap: 8px;
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
  }

  .roadmap-pill.active {
    background: #1e293b;
    border-color: #10b981;
    color: #ffffff;
    font-weight: 800;
    box-shadow: 0 0 18px rgba(16, 185, 129, 0.55), 0 4px 10px rgba(0, 0, 0, 0.2);
    transform: scale(1.04);
  }

  .roadmap-pill.active .pill-tag {
    background: #10b981;
    color: #ffffff;
    font-weight: 800;
    padding: 1px 6px;
    border-radius: 4px;
    font-size: 13px;
  }

  .roadmap-arrow {
    color: #64748b;
    font-size: 18px;
    font-weight: 800;
  }
</style>
</head>
<body>
  <div class="glow-left"></div>
  <div class="glow-right"></div>

  <!-- 頂部標題區 -->
  <div class="header-section">
    <div class="badge-row">
      <div class="badge-line left"></div>
      <div class="issue-badge">第 <span>019</span> 期</div>
      <div class="badge-line right"></div>
    </div>
    <h1 class="main-title">本機收真實 Webhook：告別 Mock，斷點攔截外部回調</h1>
    <div class="main-subtitle">解決 Request Body 串流枯竭與 HMAC 簽章驗算，50ms 秒回解耦告別重試風暴</div>
  </div>

  <!-- 中間雙核心卡片 -->
  <div class="cards-container">
    <!-- 左卡片：核心架構 -->
    <div class="card">
      <div class="card-pill">核心架構 | TUNNEL 系列 T2・本地斷點</div>
      <div class="card-title">本地斷點攔截真實 Webhook</div>
      <div class="card-subtitle">Stripe / LINE / GitHub ➔ Tunnel 直通 IDE 斷點</div>
      
      <div class="card-body">
        <div class="flow-container">
          <!-- 外部來源 -->
          <div class="source-col">
            <div class="source-heading">外部 Webhook 來源</div>
            <div class="brand-pills">
              <span class="brand-pill stripe">Stripe</span>
              <span class="brand-pill line">LINE</span>
              <span class="brand-pill github">GitHub</span>
            </div>
            <div class="timeout-warning">⚡ 5 秒超時限制</div>
          </div>

          <!-- Tunnel 穿透橋樑 -->
          <div class="tunnel-bridge">
            <span class="tunnel-badge">Tunnel 穿透</span>
            <svg class="tunnel-arrow-svg" viewBox="0 0 48 18" fill="none">
              <path d="M2 9H42M42 9L34 3M42 9L34 15" stroke="#0f766e" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>

          <!-- 本地 Spring Boot 接收端 -->
          <div class="dest-col">
            <div class="dest-header">
              <div class="dest-title">
                <span class="dot-icon dot-red"></span>
                <span>本地 IDE Controller</span>
              </div>
              <span class="dest-port">localhost:8080</span>
            </div>
            <div class="dest-steps">
              <div class="dest-step step-1">
                <span class="dot-icon dot-red"></span>
                <span>① IDE 中斷點（展開真實 JSON/Header）</span>
              </div>
              <div class="dest-step step-2">
                <span class="dot-icon dot-blue"></span>
                <span>② 50ms 內秒回 200 OK（終止重試風暴）</span>
              </div>
              <div class="dest-step step-3">
                <span class="dot-icon dot-green"></span>
                <span>③ publishEvent 拋入 Spring 事件總線</span>
              </div>
            </div>
          </div>
        </div>

        <div class="card-bottom-pill">
          50ms 秒回 200 OK・消滅第三方 5s 超時重試風暴
        </div>
      </div>
    </div>

    <!-- 右卡片：底層機制 -->
    <div class="card">
      <div class="card-pill">底層機制 | 串流枯竭與常數時間驗簽</div>
      <div class="card-title">Raw Bytes 提取 ＋ HMAC 驗簽</div>
      <div class="card-subtitle">解決 InputStream 只能讀一次 × MessageDigest.isEqual</div>

      <div class="card-body">
        <div class="exhaust-warning">
          <span class="exhaust-tag">串流陷阱</span>
          <span class="exhaust-desc">ServletInputStream 串流不可逆：@RequestBody 讀取後游標抵達 EOF，二次讀取為空！</span>
        </div>

        <div class="pipeline-cards">
          <div class="pipe-item teal">
            <div class="pipe-title">① 提取 Raw Bytes</div>
            <div class="pipe-code">request.getInputStream().readAllBytes()</div>
          </div>
          <div class="pipe-item teal">
            <div class="pipe-title">② 本地重算 HMAC</div>
            <div class="pipe-code">HMAC_SHA256(rawBytes, secret)</div>
          </div>
          <div class="pipe-item blue">
            <div class="pipe-title">③ 常數時間驗簽</div>
            <div class="pipe-code">MessageDigest.isEqual() 防時序攻擊</div>
          </div>
          <div class="pipe-item">
            <div class="pipe-title">④ 安全轉 DTO 物件</div>
            <div class="pipe-code">objectMapper.readValue(rawBytes, Dto)</div>
          </div>
        </div>

        <div class="card-bottom-pill">
          InputStream 不可倒帶・常數時間比對防 Timing Attack
        </div>
      </div>
    </div>
  </div>

  <!-- 底部導覽系列 -->
  <div class="footer-roadmap">
    <div class="roadmap-pill">1. 原理篇 T0</div>
    <div class="roadmap-arrow">➔</div>
    <div class="roadmap-pill">2. 實戰篇 T1</div>
    <div class="roadmap-arrow">➔</div>
    <div class="roadmap-pill active">
      <span>3. 除錯篇</span>
      <span class="pill-tag">T2</span>
    </div>
    <div class="roadmap-arrow">➔</div>
    <div class="roadmap-pill">4. 防護篇 T3</div>
  </div>
</body>
</html>
`;

const browser = await chromium.launch();
const page = await browser.newPage({
  viewport: { width: 1376, height: 768 },
  deviceScaleFactor: 2
});

await page.setContent(htmlContent, { waitUntil: 'networkidle' });

const outCover = join(rootDir, 'newsletter', 'newsletter-19-cover.png');
const outCover169 = join(rootDir, 'newsletter', 'newsletter-19-cover-16-9.png');

await page.screenshot({ path: outCover });
await page.screenshot({ path: outCover169 });

await browser.close();
console.log('✅ 已成功生成 19 期封面：', outCover, outCover169);
