import playwright from '../../teaching-site/node_modules/playwright/index.js';
import { mkdir, readFile } from 'node:fs/promises';
import { dirname, extname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const { chromium } = playwright;

const scriptDir = dirname(fileURLToPath(import.meta.url));
const landPageRoot = resolve(scriptDir, '..');
const outputDir = resolve(landPageRoot, 'assets', 'ai-crm', 'hahow-style-vertical');

const assetPaths = {
  dashboard: 'assets/ai-crm/promo-screenshots/01-dashboard-reports-full.png',
  charts: 'assets/ai-crm/promo-screenshots/02-crm-classic-charts.png',
  agent: 'assets/ai-crm/promo-screenshots/03-customer-ai-agent-detail.png',
  funnel: 'assets/ai-crm/promo-screenshots/charts/01-pipeline-funnel.png',
  forecast: 'assets/ai-crm/promo-screenshots/charts/02-monthly-forecast.png',
  risk: 'assets/ai-crm/promo-screenshots/charts/04-risk-breakdown.png',
  drilldown: 'assets/ai-crm/promo-screenshots/interactions/02-chart-drilldown-open.png',
  chat: 'assets/ai-crm/promo-screenshots/interactions/03-ai-chat-response.png'
};

// 讀取本地截圖並轉成 data URI，讓 Playwright 產圖時不依賴相對路徑解析。
async function loadAssetDataUri(relativePath) {
  const absolutePath = resolve(landPageRoot, relativePath);
  const ext = extname(relativePath).toLowerCase();
  const mime = ext === '.jpg' || ext === '.jpeg' ? 'image/jpeg' : 'image/png';
  const imageBuffer = await readFile(absolutePath);
  return `data:${mime};base64,${imageBuffer.toString('base64')}`;
}

// 將陣列內容轉成 Hahow 風格的重點膠囊。
function renderPills(items) {
  return items.map((item) => `<span class="pill">${item}</span>`).join('');
}

// 產生編號步驟卡，對應直式課程內容圖常見的分段節奏。
function renderStepCards(items) {
  return items.map((item, index) => `
    <div class="step-card">
      <div class="step-number">${String(index + 1).padStart(2, '0')}</div>
      <div>
        <strong>${item.title}</strong>
        <p>${item.text}</p>
      </div>
    </div>
  `).join('');
}

// 產生螢幕截圖卡，保留真實 AI CRM 產品畫面的說服力。
function renderShot(src, title, caption, className = '') {
  return `
    <figure class="shot ${className}">
      <img src="${src}" alt="${title}" />
      <figcaption><strong>${title}</strong><span>${caption}</span></figcaption>
    </figure>
  `;
}

// 組出單張 1000x1777 直式資訊圖的完整 HTML。
function renderPoster(poster, assets) {
  return `<!doctype html>
<html lang="zh-Hant">
<head>
  <meta charset="utf-8" />
  <style>
    * { box-sizing: border-box; }
    html, body { margin: 0; width: 1000px; height: 1777px; overflow: hidden; }
    body {
      font-family: "Noto Sans TC", "Microsoft JhengHei", "PingFang TC", system-ui, sans-serif;
      background: #eef7f6;
      color: #17252b;
    }
    .poster {
      position: relative;
      width: 1000px;
      height: 1777px;
      padding: 72px 70px 64px;
      background:
        radial-gradient(circle at 88% 8%, rgba(255, 191, 71, .22), transparent 30%),
        linear-gradient(180deg, #f7fffd 0%, #edf7f5 48%, #f9fbf8 100%);
      overflow: hidden;
    }
    .poster::before {
      content: "";
      position: absolute;
      inset: 30px;
      border: 2px solid rgba(0, 188, 166, .14);
      border-radius: 42px;
      pointer-events: none;
    }
    .topline {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 24px;
      position: relative;
      z-index: 1;
    }
    .brand {
      display: inline-flex;
      align-items: center;
      gap: 12px;
      padding: 12px 20px;
      border-radius: 999px;
      background: #102a2e;
      color: #fff;
      font-size: 22px;
      font-weight: 900;
      letter-spacing: 0;
    }
    .brand-mark {
      width: 18px;
      height: 18px;
      border-radius: 999px;
      background: #00c7b1;
      box-shadow: 18px 0 0 #ffb347;
    }
    .unit {
      color: #08756b;
      font-size: 21px;
      font-weight: 900;
    }
    h1 {
      position: relative;
      z-index: 1;
      max-width: 830px;
      margin: 46px 0 0;
      color: #102a2e;
      font-size: 78px;
      line-height: 1.08;
      font-weight: 950;
      letter-spacing: 0;
    }
    h1 .accent { color: #00aa9a; }
    .lead {
      position: relative;
      z-index: 1;
      margin: 28px 0 0;
      max-width: 800px;
      color: #52666b;
      font-size: 30px;
      line-height: 1.55;
      font-weight: 700;
    }
    .pill-row {
      position: relative;
      z-index: 1;
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      margin-top: 30px;
    }
    .pill {
      display: inline-flex;
      align-items: center;
      min-height: 48px;
      padding: 10px 18px;
      border-radius: 999px;
      background: #dff5f1;
      color: #08756b;
      border: 2px solid rgba(0, 188, 166, .2);
      font-size: 21px;
      font-weight: 900;
    }
    .body {
      position: relative;
      z-index: 1;
      display: grid;
      gap: 22px;
      margin-top: 42px;
    }
    .card {
      border-radius: 32px;
      background: rgba(255, 255, 255, .92);
      border: 2px solid rgba(25, 75, 78, .08);
      box-shadow: 0 22px 60px rgba(33, 82, 84, .12);
      padding: 30px;
    }
    .step-card {
      display: grid;
      grid-template-columns: 88px minmax(0, 1fr);
      gap: 22px;
      align-items: start;
      padding: 22px 24px;
      border-radius: 28px;
      background: #ffffff;
      border: 2px solid rgba(0, 188, 166, .14);
    }
    .step-number {
      display: grid;
      place-items: center;
      width: 72px;
      height: 72px;
      border-radius: 50%;
      background: #00c7b1;
      color: #fff;
      font-size: 29px;
      font-weight: 950;
    }
    .step-card strong {
      display: block;
      color: #102a2e;
      font-size: 31px;
      line-height: 1.25;
      font-weight: 950;
    }
    .step-card p {
      margin: 10px 0 0;
      color: #5b6d73;
      font-size: 22px;
      line-height: 1.5;
      font-weight: 700;
    }
    .grid-two { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
    .grid-three { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
    .shot {
      overflow: hidden;
      margin: 0;
      border-radius: 28px;
      background: #102a2e;
      border: 2px solid rgba(25, 75, 78, .08);
      box-shadow: 0 18px 44px rgba(16, 42, 46, .16);
    }
    .shot img {
      display: block;
      width: 100%;
      height: 315px;
      object-fit: cover;
      object-position: top center;
      background: #f3f6f8;
    }
    .shot.tall img { height: 440px; }
    .shot.compact img { height: 238px; }
    .shot figcaption {
      display: grid;
      gap: 6px;
      padding: 20px 22px 22px;
      color: #fff;
    }
    .shot figcaption strong {
      font-size: 24px;
      line-height: 1.2;
      font-weight: 950;
    }
    .shot figcaption span {
      color: rgba(255, 255, 255, .76);
      font-size: 18px;
      line-height: 1.45;
      font-weight: 700;
    }
    .callout {
      display: grid;
      gap: 8px;
      padding: 26px 30px;
      border-radius: 30px;
      background: #fffbeb;
      border: 2px solid rgba(245, 158, 11, .24);
      color: #7c4a03;
    }
    .callout strong { font-size: 30px; line-height: 1.25; font-weight: 950; }
    .callout span { font-size: 22px; line-height: 1.5; font-weight: 800; }
    .diagram {
      display: grid;
      gap: 16px;
      padding: 28px;
      border-radius: 32px;
      background: #ffffff;
      border: 2px solid rgba(0, 188, 166, .14);
    }
    .lane {
      display: grid;
      grid-template-columns: 180px minmax(0, 1fr);
      gap: 18px;
      align-items: center;
      min-height: 86px;
      padding: 18px 22px;
      border-radius: 24px;
      background: #f4fbfa;
    }
    .lane strong {
      color: #00a390;
      font-size: 23px;
      font-weight: 950;
    }
    .lane span {
      color: #40565b;
      font-size: 22px;
      line-height: 1.45;
      font-weight: 800;
    }
    .footer {
      position: absolute;
      left: 70px;
      right: 70px;
      bottom: 48px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      color: #6c7d81;
      font-size: 19px;
      font-weight: 900;
      z-index: 1;
    }
    .footer b { color: #00a390; }
  </style>
</head>
<body>
  <main class="poster">
    <div class="topline">
      <div class="brand"><span class="brand-mark"></span>AI CRM 全端實戰</div>
      <div class="unit">${poster.unit}</div>
    </div>
    <h1>${poster.title}</h1>
    <p class="lead">${poster.lead}</p>
    <div class="pill-row">${renderPills(poster.pills)}</div>
    <section class="body">${poster.render(assets)}</section>
    <div class="footer"><span>AI 賦能全端開發</span><b>Spring Boot 4 + Spring AI 2</b></div>
  </main>
</body>
</html>`;
}

// 定義所有要輸出的 Hahow 直式宣傳圖內容。
function createPosters() {
  return [
    {
      file: '01-ai-crm-system-map.png',
      unit: '作品主軸',
      title: '一套 <span class="accent">AI CRM</span><br>貫穿全端開發',
      lead: '不是零散範例，而是從登入、資料庫、報表到 AI 建議都能跑通的企業級作品。',
      pills: ['React', 'Spring Boot', 'Security', 'JPA', 'Spring AI', 'RAG'],
      render: (assets) => `
        <div class="card">${renderStepCards([
          { title: '先做出可上線的 CRM 骨架', text: 'API、資料庫、權限與前端工作台完整串起來。' },
          { title: '再把 AI 放進真實業務流程', text: '客戶摘要、風險判斷、下一步建議都接回 domain service。' },
          { title: '最後能當作品集展示', text: 'Demo Day 從登入到 AI 對話完整跑通，有畫面也有架構。' }
        ])}</div>
        ${renderShot(assets.dashboard, '完課作品畫面', 'Dashboard、客戶列表、AI 助理與 Agent Trace 同頁呈現。', 'tall')}
      `
    },
    {
      file: '02-eight-unit-roadmap.png',
      unit: '8 大單元',
      title: '每一章都接到<br><span class="accent">同一套專案</span>',
      lead: '學員不是看完就忘，而是每個單元都把能力堆回 AI CRM，最後形成一個完整產品。',
      pills: ['環境', 'REST API', 'JPA', 'JWT', 'React', 'AI', 'RAG', 'Demo'],
      render: () => `
        <div class="card" style="display:grid;gap:14px;">${renderStepCards([
          { title: '環境與專案骨架', text: 'Java 21、Spring Boot、React、Docker 與開發流程。' },
          { title: 'CRM REST API', text: 'Controller、Service、DTO、Validation 與 Swagger。' },
          { title: 'JPA 與資料庫', text: 'Customer、Opportunity、Interaction 與查詢規格。' },
          { title: 'Security + JWT', text: '登入、角色權限與 API 保護。' },
          { title: 'React CRM 工作台', text: '表單、列表、報表與前後端串接。' },
          { title: 'Spring AI + SSE', text: '串流回應、Tool Calling 與 AI 助理。' },
          { title: 'RAG + pgvector', text: '產品文件、服務條款與可引用的回答。' },
          { title: 'Demo Day 驗收', text: '登入、客戶、AI、RAG、Swagger 全流程展示。' }
        ])}</div>
      `
    },
    {
      file: '03-interactive-dashboard.png',
      unit: '報表互動',
      title: 'CRM 圖表不是裝飾<br><span class="accent">可以下鑽操作</span>',
      lead: '把報表拆成可展示素材：漏斗、Forecast、風險結構與點擊後的底層商機明細。',
      pills: ['Pipeline', 'Forecast', 'Risk', 'Drill-down'],
      render: (assets) => `
        <div class="grid-three">
          ${renderShot(assets.funnel, '銷售漏斗', '依階段看金額與筆數。', 'compact')}
          ${renderShot(assets.forecast, '月度 Forecast', '預估營收與趨勢。', 'compact')}
          ${renderShot(assets.risk, '風險結構', '高風險客戶一眼看見。', 'compact')}
        </div>
        ${renderShot(assets.drilldown, '互動下鑽狀態', '點擊圖表段落後，直接展開客戶與商機明細。', 'tall')}
        <div class="callout"><strong>宣傳頁可拆開使用</strong><span>每張圖表都能獨立截圖，也能搭配互動過程截圖，強化「不是靜態 mockup」的說服力。</span></div>
      `
    },
    {
      file: '04-rag-tool-calling.png',
      unit: 'AI 落地',
      title: 'RAG + Tool Calling<br><span class="accent">讓回答接回真資料</span>',
      lead: 'LLM 負責摘要與建議，CRM 資料、金額、權限與規則仍由 Java domain service 掌控。',
      pills: ['ChatClient', 'SSE', 'pgvector', 'Domain Tool'],
      render: (assets) => `
        <div class="diagram">
          <div class="lane"><strong>使用者提問</strong><span>「這個客戶為什麼是高風險？下一步要做什麼？」</span></div>
          <div class="lane"><strong>Tool Calling</strong><span>讀取客戶、商機、互動紀錄與健康分數。</span></div>
          <div class="lane"><strong>RAG 檢索</strong><span>引用產品文件、銷售話術與服務條款，不憑空亂答。</span></div>
          <div class="lane"><strong>串流回應</strong><span>用 SSE token-by-token 回到 React AI 助理視窗。</span></div>
        </div>
        ${renderShot(assets.chat, 'AI 助理回應', '把客戶風險、下一步建議與資料來源整理成可追蹤回答。', 'tall')}
      `
    },
    {
      file: '05-ai-agent-workflow.png',
      unit: 'AI 協作',
      title: '不是叫 AI 寫完<br><span class="accent">而是有方法驗收</span>',
      lead: '每單元都練習先讀需求、再產程式、最後用測試與畫面驗證，讓 AI 協作能進入工程流程。',
      pills: ['需求拆解', '提示詞', '測試', '驗收'],
      render: (assets) => `
        <div class="card">${renderStepCards([
          { title: '讀規格', text: '先把 CRM user story、API contract 與資料模型釐清。' },
          { title: '產程式', text: '讓 AI 協助生成 Controller、Service、React 元件與測試。' },
          { title: '跑驗證', text: '用 Swagger、Playwright、API 測試與截圖確認功能真的可用。' },
          { title: '修邊界', text: '補權限、錯誤處理、RAG 檢索與 tool calling 邊界。' }
        ])}</div>
        ${renderShot(assets.agent, 'Agent Trace 與客戶頁', '學員能看見 AI 執行過程，不只看最後答案。', 'tall')}
      `
    },
    {
      file: '06-demo-day-outcome.png',
      unit: '結訓產出',
      title: 'Demo Day<br><span class="accent">跑給別人看</span>',
      lead: '結訓不是交作業截圖，而是把一條完整展示腳本跑通，成為可放履歷與提案的作品。',
      pills: ['登入', '客戶管理', 'AI 對話', 'RAG 查詢', 'Swagger'],
      render: (assets) => `
        <div class="card">${renderStepCards([
          { title: '登入與權限', text: '展示安全授權與角色保護，不是公開 demo 頁。' },
          { title: '客戶與商機', text: '建立、查詢、篩選與更新真實 CRM 資料。' },
          { title: 'AI 業務助理', text: '對客戶提出問題，取得摘要、風險與下一步建議。' },
          { title: '引用與追蹤', text: '用 RAG 引用文件，用 Agent Trace 看過程。' }
        ])}</div>
        <div class="grid-two">
          ${renderShot(assets.dashboard, '作品集主畫面', '完整產品工作台。', 'compact')}
          ${renderShot(assets.charts, '報表成果', '可展示商業洞察。', 'compact')}
        </div>
        <div class="callout"><strong>最後拿到的是一套作品</strong><span>可登入、可查資料、可引用知識庫、可產生業務建議，並能在 Demo Day 端到端驗收。</span></div>
      `
    }
  ];
}

// 主流程：載入素材、逐張渲染 HTML，並截成固定尺寸 PNG。
async function main() {
  await mkdir(outputDir, { recursive: true });

  const assets = {};
  for (const [name, relativePath] of Object.entries(assetPaths)) {
    assets[name] = await loadAssetDataUri(relativePath);
  }

  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1000, height: 1777 }, deviceScaleFactor: 1 });

  for (const poster of createPosters()) {
    await page.setContent(renderPoster(poster, assets), { waitUntil: 'load' });
    await page.screenshot({ path: resolve(outputDir, poster.file), fullPage: false });
    console.log(`generated ${poster.file}`);
  }

  await browser.close();
}

await main();
