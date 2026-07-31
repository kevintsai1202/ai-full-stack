import playwright from '../../teaching-site/node_modules/playwright/index.js';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const { chromium } = playwright;

const scriptDir = dirname(fileURLToPath(import.meta.url));
const landPageRoot = resolve(scriptDir, '..');
const outputDir = resolve(landPageRoot, 'assets', 'ai-crm', 'hahow-image2-course-content');

const courseTitle = 'AI 賦能全端開發';
const courseSubtitle = '從零打造企業級智慧應用';

const posters = [
  {
    bg: 'bg-01-ai-crm-project.png',
    file: '01-ai-crm-project-course.png',
    label: '課程主軸',
    title: '做出一套\nAI CRM',
    subtitle: '從後端、前端到 AI 助理',
    points: ['Spring Boot 4', 'React 19', 'Spring AI 2'],
    details: ['JWT 登入與權限', 'CRM Dashboard 與客戶列表', 'AI 助理與 Agent Trace'],
    footer: '完課作品：可登入、可查資料、可追蹤 AI 執行過程'
  },
  {
    bg: 'bg-02-eight-unit-roadmap.png',
    file: '02-eight-unit-roadmap-course.png',
    label: '課程內容',
    title: '8 大單元\n章章相連',
    subtitle: '每章都回到同一套專案',
    points: ['環境與骨架', 'API 與資料庫', 'AI 與 Demo'],
    details: ['從健康檢查到 REST API', '從 JPA 查詢到前端工作台', '從 RAG 檢索到 Demo Day 展示'],
    footer: '不是零散範例，而是一路堆成完整產品'
  },
  {
    bg: 'bg-03-rag-tool-calling.png',
    file: '03-rag-tool-calling-course.png',
    label: 'AI 落地',
    title: 'RAG +\nTool Calling',
    subtitle: '讓回答接回真實資料',
    points: ['知識庫檢索', 'CRM 工具呼叫', '串流回應'],
    details: ['pgvector 管理可引用知識', 'Domain tool 讀取真實 CRM 資料', 'SSE 讓 AI 回應即時顯示'],
    footer: 'LLM 負責摘要，資料與規則回到 Java domain service'
  },
  {
    bg: 'bg-04-demo-day-outcome.png',
    file: '04-demo-day-outcome-course.png',
    label: '結訓成果',
    title: 'Demo Day\n跑給別人看',
    subtitle: '完課就是可展示作品',
    points: ['登入與權限', '客戶與商機', '作品集展示'],
    details: ['登入後進入 CRM 工作台', '查看客戶風險與商機狀態', '用 AI 產生下一步建議'],
    footer: '從操作流程到架構說明，都能端到端驗收'
  }
];

const lessonBanners = [
  ['01', '開發環境、專案骨架與 AI 協作流程', '建立可啟動的 full-stack monorepo'],
  ['02', 'Spring MVC、REST API 與 CRM Domain Modeling', '把 Customer、Opportunity、Interaction 拆成可維護 API'],
  ['03', 'JPA、PostgreSQL、Flyway 與資料查詢', '用資料模型支撐後續報表與 AI 工具呼叫'],
  ['04', 'Spring Security、JWT 與權限保護', '讓 AI CRM 不是公開 demo，而是有授權邊界的系統'],
  ['05', 'React CRM 工作台與前後端整合', '建立 Dashboard、列表、表單、商機看板與 AI 助理介面'],
  ['06', 'Spring AI、SSE 串流與 Tool Calling', '讓 AI 能呼叫 Java service，回到真實業務資料'],
  ['07', 'RAG、pgvector、MCP 與知識庫擴充', '讓回答能引用文件來源，不只憑模型記憶回答'],
  ['08', 'Demo Day：從登入到 AI CRM 端到端驗收', '把可操作產品、架構說明與展示腳本整理成作品集']
];

/**
 * 將本地圖片讀成 data URI，讓截圖渲染不依賴相對路徑載入。
 */
async function imageDataUri(name) {
  const file = resolve(outputDir, name);
  const buffer = await readFile(file);
  return `data:image/png;base64,${buffer.toString('base64')}`;
}

/**
 * 將換行標題轉成安全的 HTML 片段，避免字串直接破壞版面。
 */
function renderTitle(title) {
  return title.split('\n').map((line) => `<span>${line}</span>`).join('');
}

/**
 * 產生每張圖的重點膠囊，文字固定由程式渲染以避免 AI 字形錯誤。
 */
function renderPoints(points) {
  return points.map((point) => `<li>${point}</li>`).join('');
}

/**
 * 產生更細的補充列表，填補直式圖下方留白並提高資訊密度。
 */
function renderDetails(details) {
  return details.map((item) => `<li>${item}</li>`).join('');
}

/**
 * 組出單張 9:16 課程內容圖，底圖來自 image2，中文文案由本地 CSS 疊加。
 */
function renderPoster(poster, background) {
  return `<!doctype html>
<html lang="zh-Hant">
<head>
  <meta charset="utf-8" />
  <style>
    * { box-sizing: border-box; }
    html, body { margin: 0; width: 941px; height: 1672px; overflow: hidden; }
    body {
      font-family: "Noto Sans TC", "Microsoft JhengHei", "PingFang TC", system-ui, sans-serif;
      color: #102a2e;
      background: #f7fffd;
    }
    .poster {
      position: relative;
      width: 941px;
      height: 1672px;
      overflow: hidden;
      background-image: url("${background}");
      background-size: cover;
      background-position: center;
    }
    .veil {
      position: absolute;
      inset: 0;
      background:
        linear-gradient(90deg, rgba(255,255,255,.96) 0%, rgba(255,255,255,.9) 31%, rgba(255,255,255,.36) 58%, rgba(255,255,255,.06) 100%),
        linear-gradient(180deg, rgba(247,255,253,.08), rgba(0,184,166,.16));
    }
    .content {
      position: relative;
      z-index: 1;
      width: 100%;
      height: 100%;
      padding: 78px 72px 58px;
      display: flex;
      flex-direction: column;
    }
    .label {
      display: inline-flex;
      align-items: center;
      gap: 12px;
      width: fit-content;
      padding: 13px 20px;
      border-radius: 999px;
      background: #102a2e;
      color: #ffffff;
      font-size: 24px;
      line-height: 1;
      font-weight: 900;
      letter-spacing: 0;
      box-shadow: 0 14px 34px rgba(16,42,46,.18);
    }
    .dot {
      width: 17px;
      height: 17px;
      border-radius: 999px;
      background: #00c7b1;
      box-shadow: 19px 0 0 #ffb347;
    }
    h1 {
      display: grid;
      gap: 8px;
      margin: 42px 0 0;
      max-width: 590px;
      color: #102a2e;
      font-size: 88px;
      line-height: 1.03;
      font-weight: 950;
      letter-spacing: 0;
    }
    h1 span:nth-child(2) { color: #00a390; }
    .subtitle {
      max-width: 610px;
      margin: 24px 0 0;
      color: #455b60;
      font-size: 32px;
      line-height: 1.42;
      font-weight: 850;
      letter-spacing: 0;
    }
    .points {
      display: grid;
      gap: 16px;
      max-width: 570px;
      padding: 0;
      margin: 36px 0 0;
      list-style: none;
    }
    .points li {
      position: relative;
      min-height: 76px;
      display: flex;
      align-items: center;
      padding: 17px 24px 17px 64px;
      border-radius: 24px;
      background: rgba(255,255,255,.82);
      border: 2px solid rgba(0,188,166,.18);
      box-shadow: 0 14px 40px rgba(18,76,77,.1);
      color: #133238;
      font-size: 30px;
      line-height: 1.25;
      font-weight: 900;
    }
    .points li::before {
      content: "";
      position: absolute;
      left: 24px;
      width: 22px;
      height: 22px;
      border-radius: 999px;
      background: #00c7b1;
      box-shadow: inset 0 0 0 6px #dff8f5;
    }
    .details {
      display: grid;
      gap: 12px;
      max-width: 620px;
      margin: 24px 0 0;
      padding: 24px 28px;
      border-radius: 28px;
      list-style: none;
      background: rgba(255,255,255,.74);
      border: 2px solid rgba(16,42,46,.08);
      box-shadow: 0 18px 48px rgba(18,76,77,.09);
      backdrop-filter: blur(8px);
    }
    .details li {
      position: relative;
      padding-left: 30px;
      color: #2f4c52;
      font-size: 24px;
      line-height: 1.42;
      font-weight: 850;
    }
    .details li::before {
      content: "✓";
      position: absolute;
      left: 0;
      top: 0;
      color: #00a390;
      font-weight: 950;
    }
    .footer {
      margin-top: 24px;
      max-width: 650px;
      padding: 22px 28px;
      border-radius: 30px;
      background: rgba(16,42,46,.92);
      color: rgba(255,255,255,.84);
      font-size: 23px;
      line-height: 1.45;
      font-weight: 850;
      box-shadow: 0 20px 54px rgba(16,42,46,.26);
    }
    .source {
      margin-top: 16px;
      color: #08756b;
      font-size: 20px;
      font-weight: 900;
    }
  </style>
</head>
<body>
  <main class="poster">
    <div class="veil"></div>
    <section class="content">
      <div class="label"><span class="dot"></span>${poster.label}</div>
      <h1>${renderTitle(poster.title)}</h1>
      <p class="subtitle">${poster.subtitle}</p>
      <ul class="points">${renderPoints(poster.points)}</ul>
      <ul class="details">${renderDetails(poster.details)}</ul>
      <div class="footer">${poster.footer}</div>
      <div class="source">AI 賦能全端開發 · Hahow 課程宣傳素材</div>
    </section>
  </main>
</body>
</html>`;
}

/**
 * 組出 Hahow 長方形封面圖，依官方建議尺寸 1000x620 輸出。
 */
function renderWideCover(background) {
  return `<!doctype html>
<html lang="zh-Hant">
<head>
  <meta charset="utf-8" />
  <style>
    * { box-sizing: border-box; }
    html, body { margin: 0; width: 1000px; height: 620px; overflow: hidden; }
    body { font-family: "Noto Sans TC", "Microsoft JhengHei", "PingFang TC", system-ui, sans-serif; }
    .cover {
      position: relative;
      width: 1000px;
      height: 620px;
      background-image: url("${background}");
      background-size: cover;
      background-position: center;
      overflow: hidden;
    }
    .cover::before {
      content: "";
      position: absolute;
      inset: 0;
      background:
        linear-gradient(90deg, rgba(16,42,46,.94) 0%, rgba(16,42,46,.84) 42%, rgba(16,42,46,.24) 72%, rgba(16,42,46,.08) 100%),
        radial-gradient(circle at 18% 14%, rgba(0,199,177,.3), transparent 28%);
    }
    .content {
      position: relative;
      z-index: 1;
      width: 690px;
      height: 100%;
      padding: 62px 70px;
      color: #fff;
      display: flex;
      flex-direction: column;
      justify-content: center;
    }
    h1 {
      margin: 0;
      max-width: 560px;
      font-size: 52px;
      line-height: 1.12;
      font-weight: 950;
      letter-spacing: 0;
    }
    h1 span { display: block; }
    h1 span:nth-child(2) {
      margin-top: 14px;
      color: #32dcc9;
      font-size: 38px;
      line-height: 1.18;
    }
  </style>
</head>
<body>
  <main class="cover">
    <section class="content">
      <h1><span>${courseTitle}</span><span>${courseSubtitle}</span></h1>
    </section>
  </main>
</body>
</html>`;
}

/**
 * 組出 Hahow 正方形封面圖，依官方建議尺寸 800x800 輸出。
 */
function renderSquareCover(background) {
  return `<!doctype html>
<html lang="zh-Hant">
<head>
  <meta charset="utf-8" />
  <style>
    * { box-sizing: border-box; }
    html, body { margin: 0; width: 800px; height: 800px; overflow: hidden; }
    body { font-family: "Noto Sans TC", "Microsoft JhengHei", "PingFang TC", system-ui, sans-serif; }
    .cover {
      position: relative;
      width: 800px;
      height: 800px;
      padding: 58px;
      background-image: url("${background}");
      background-size: cover;
      background-position: center;
      overflow: hidden;
    }
    .cover::before {
      content: "";
      position: absolute;
      inset: 0;
      background:
        linear-gradient(180deg, rgba(255,255,255,.94), rgba(255,255,255,.78) 46%, rgba(0,184,166,.2)),
        radial-gradient(circle at 80% 22%, rgba(255,179,71,.34), transparent 30%);
    }
    .content {
      position: relative;
      z-index: 1;
      height: 100%;
      display: flex;
      flex-direction: column;
      justify-content: center;
      color: #102a2e;
    }
    h1 {
      margin: 0;
      max-width: 610px;
      font-size: 62px;
      line-height: 1.12;
      font-weight: 950;
      letter-spacing: 0;
    }
    h1 span {
      display: block;
      margin-top: 16px;
      color: #00a390;
      font-size: 52px;
      line-height: 1.18;
    }
  </style>
</head>
<body>
  <main class="cover">
    <section class="content">
      <h1>${courseTitle}<span>${courseSubtitle}</span></h1>
    </section>
  </main>
</body>
</html>`;
}

/**
 * 組出課程內容用的橫幅長條文字圖，適合放在章節介紹區塊上方。
 */
function renderLessonBanner([number, title, subtitle]) {
  return `<!doctype html>
<html lang="zh-Hant">
<head>
  <meta charset="utf-8" />
  <style>
    * { box-sizing: border-box; }
    html, body { margin: 0; width: 1600px; height: 420px; overflow: hidden; }
    body { font-family: "Noto Sans TC", "Microsoft JhengHei", "PingFang TC", system-ui, sans-serif; background: #f5fbfa; }
    .banner {
      position: relative;
      width: 1600px;
      height: 420px;
      padding: 48px 64px;
      overflow: hidden;
      background:
        radial-gradient(circle at 88% 18%, rgba(255,179,71,.38), transparent 28%),
        radial-gradient(circle at 15% 80%, rgba(0,199,177,.24), transparent 32%),
        linear-gradient(120deg, #ffffff 0%, #effbf9 50%, #d9f5ef 100%);
      border: 1px solid rgba(0,188,166,.18);
    }
    .banner::after {
      content: "";
      position: absolute;
      right: -30px;
      bottom: -120px;
      width: 520px;
      height: 520px;
      border-radius: 50%;
      background: rgba(0,163,144,.18);
      box-shadow: -120px -80px 0 rgba(255,179,71,.16);
    }
    .content { position: relative; z-index: 1; display: grid; grid-template-columns: 150px minmax(0, 1fr); gap: 34px; align-items: center; height: 100%; }
    .num {
      display: grid;
      place-items: center;
      width: 140px;
      height: 140px;
      border-radius: 36px;
      color: #ffffff;
      background: #102a2e;
      font-size: 64px;
      font-weight: 950;
      box-shadow: 0 18px 50px rgba(16,42,46,.2);
    }
    .label {
      width: fit-content;
      padding: 9px 14px;
      border-radius: 999px;
      background: rgba(0,199,177,.13);
      color: #08756b;
      font-size: 22px;
      line-height: 1;
      font-weight: 900;
    }
    h2 {
      margin: 18px 0 0;
      color: #102a2e;
      font-size: 54px;
      line-height: 1.16;
      font-weight: 950;
      letter-spacing: 0;
    }
    p {
      margin: 16px 0 0;
      color: #455b60;
      font-size: 28px;
      line-height: 1.42;
      font-weight: 820;
    }
  </style>
</head>
<body>
  <main class="banner">
    <section class="content">
      <div class="num">${number}</div>
      <div>
        <div class="label">課程內容</div>
        <h2>${title}</h2>
        <p>${subtitle}</p>
      </div>
    </section>
  </main>
</body>
</html>`;
}

/**
 * 產生獨立預覽頁，方便檢查素材；不修改正式 landing page。
 */
async function writePreview() {
  const coverCards = [
    ['cover-wide-title.png', 'Hahow 長方形封面 1000x620'],
    ['cover-square-title.png', 'Hahow 方形封面 800x800']
  ].map(([file, caption]) => `
    <figure>
      <picture>
        <source srcset="${file.replace(/\.png$/, '.webp')}" type="image/webp" />
        <img src="${file}" alt="${caption}" />
      </picture>
      <figcaption>${caption}</figcaption>
    </figure>
  `).join('\n');

  const cards = posters.map((poster) => `
    <figure>
      <picture>
        <source srcset="${poster.file.replace(/\.png$/, '.webp')}" type="image/webp" />
        <img src="${poster.file}" width="941" height="1672" alt="${poster.label}：${poster.title.replace(/\n/g, ' ')}" />
      </picture>
      <figcaption>${poster.label} · ${poster.subtitle}</figcaption>
    </figure>
  `).join('\n');

  const bannerCards = lessonBanners.map(([number, title]) => {
    const file = `banner-${number}.png`;
    return `
      <figure class="wide">
        <picture>
          <source srcset="${file.replace(/\.png$/, '.webp')}" type="image/webp" />
          <img src="${file}" width="1600" height="420" alt="${number} ${title}" />
        </picture>
        <figcaption>${number} · ${title}</figcaption>
      </figure>
    `;
  }).join('\n');

  await writeFile(resolve(outputDir, 'preview.html'), `<!doctype html>
<html lang="zh-Hant">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Hahow 課程內容圖獨立預覽</title>
  <style>
    body { margin: 0; font-family: system-ui, "Microsoft JhengHei", sans-serif; background: #f5fbfa; color: #102a2e; }
    main { max-width: 1180px; margin: 0 auto; padding: 36px 18px; }
    h1 { margin: 0 0 10px; font-size: clamp(1.7rem, 4vw, 2.5rem); }
    p { margin: 0 0 24px; color: #52666b; line-height: 1.8; }
    h2 { margin: 32px 0 16px; font-size: 1.45rem; }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(230px, 1fr)); gap: 18px; }
    figure { margin: 0; }
    img { width: 100%; height: auto; aspect-ratio: 9 / 16; object-fit: cover; border-radius: 10px; border: 1px solid rgba(15, 42, 46, .12); box-shadow: 0 18px 46px rgba(16,42,46,.12); }
    .cover-grid img { aspect-ratio: auto; object-fit: contain; background: #fff; }
    figure.wide { grid-column: 1 / -1; }
    figure.wide img { aspect-ratio: 1600 / 420; object-fit: cover; }
    figcaption { margin-top: 10px; color: #52666b; font-weight: 800; line-height: 1.5; }
  </style>
</head>
<body>
  <main>
    <h1>Hahow 課程內容圖獨立預覽</h1>
    <p>此頁只用於素材檢查，不會被接回 <code>land-page/index.html</code>。</p>
    <h2>封面圖</h2>
    <section class="grid cover-grid">${coverCards}</section>
    <h2>直式文字圖</h2>
    <section class="grid">${cards}</section>
    <h2>課程內容橫幅</h2>
    <section class="grid">${bannerCards}</section>
  </main>
</body>
</html>`, 'utf8');
}

/**
 * 主流程：逐張輸出有字版 PNG，並同步產生獨立預覽頁。
 */
async function main() {
  await mkdir(outputDir, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 941, height: 1672 }, deviceScaleFactor: 1 });
  const coverBackground = await imageDataUri('bg-01-ai-crm-project.png');

  for (const poster of posters) {
    const background = await imageDataUri(poster.bg);
    await page.setContent(renderPoster(poster, background), { waitUntil: 'load' });
    await page.screenshot({ path: resolve(outputDir, poster.file), fullPage: false });
    console.log(`generated ${poster.file}`);
  }

  await page.setViewportSize({ width: 1000, height: 620 });
  await page.setContent(renderWideCover(coverBackground), { waitUntil: 'load' });
  await page.screenshot({ path: resolve(outputDir, 'cover-wide-title.png'), fullPage: false });
  console.log('generated cover-wide-title.png');

  await page.setViewportSize({ width: 800, height: 800 });
  await page.setContent(renderSquareCover(coverBackground), { waitUntil: 'load' });
  await page.screenshot({ path: resolve(outputDir, 'cover-square-title.png'), fullPage: false });
  console.log('generated cover-square-title.png');

  await page.setViewportSize({ width: 1600, height: 420 });
  for (const banner of lessonBanners) {
    const [number] = banner;
    await page.setContent(renderLessonBanner(banner), { waitUntil: 'load' });
    await page.screenshot({ path: resolve(outputDir, `banner-${number}.png`), fullPage: false });
    console.log(`generated banner-${number}.png`);
  }

  await browser.close();
  await writePreview();
}

await main();
