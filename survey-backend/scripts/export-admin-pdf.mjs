// 問卷統計報告 PDF 匯出腳本：與 admin.html 的 buildReportHtml() 共用同一模板，避免兩份版面漂移。
// 用法一（線上真實資料，輸出 output/survey-admin-report.pdf，含個資摘錄，不得提交 repo）：
//   $env:ADMIN_API_KEY="<金鑰>"; node survey-backend/scripts/export-admin-pdf.mjs
// 用法二（離線範例資料驗證版面，不需金鑰與伺服器，輸出 output/survey-admin-report-sample.pdf）：
//   node survey-backend/scripts/export-admin-pdf.mjs --sample
// 需求：playwright（本 repo 無根 package.json，依序嘗試就近安裝、teaching-site、全域 npm）
import { mkdir } from 'node:fs/promises';
import { fileURLToPath, pathToFileURL } from 'node:url';
import path from 'node:path';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));

/** 解析 playwright 模組：survey-backend 無自己的 node_modules，故提供多個候選位置 */
async function loadChromium() {
  try { return (await import('playwright')).chromium; } catch { /* 落入候選位置 */ }
  const candidates = [
    path.resolve(scriptDir, '../../teaching-site/node_modules/playwright/index.mjs'),
    path.join(process.env.APPDATA || '', 'npm/node_modules/playwright/index.mjs'),
  ];
  for (const candidate of candidates) {
    try { return (await import(pathToFileURL(candidate).href)).chromium; } catch { /* 換下一個 */ }
  }
  throw new Error('找不到 playwright，請先安裝：npm i -g playwright（或於 teaching-site 目錄 npm i playwright）');
}
const chromium = await loadChromium();

const SAMPLE_MODE = process.argv.includes('--sample');
const BASE = process.env.ADMIN_BASE || 'https://springai-survey.zeabur.app';
const KEY = process.env.ADMIN_API_KEY;

// 範例資料：涵蓋單選、複選、自由建議、UTM、退訂等所有報表面向，僅供版面驗證
const SAMPLE_ROWS = [
  { id: 1, email: 'a@example.com', name: '小明', role: '後端工程師', experience: '3-5 年', frontendExperience: '不熟', budget: '4000-5000',
    interest: ['RAG 知識庫', 'Tool Calling'], answers: { status: '在職進修', goals: ['轉型 AI 應用'], pain_points: ['前端不熟'], suggestion: '希望多一點實戰案例。' },
    utm: { utm_source: 'facebook' }, consent: true, unsubscribed: false, createdAt: '2026-07-01T10:00:00Z' },
  { id: 2, email: 'b@example.com', name: '小華', role: '前端工程師', experience: '沒碰過', frontendExperience: '3 年以上', budget: '5000-6000',
    interest: ['前端整合', 'AI 輔助程式開發'], answers: { status: '想轉職', goals: ['補齊後端'], pain_points: ['部署不熟', 'API 設計'], suggestion: '' },
    utm: {}, consent: true, unsubscribed: false, createdAt: '2026-07-05T12:30:00Z' },
  { id: 3, email: 'c@example.com', name: '', role: '學生', experience: '1 年以下', frontendExperience: '1 年以下', budget: '4000以下',
    interest: ['RAG 知識庫'], answers: { status: '學習中', goals: ['找到第一份工作'], pain_points: ['缺乏專案經驗'], suggestion: '學生希望有優惠價。' },
    utm: { utm_source: 'ig', utm_medium: 'story' }, consent: false, unsubscribed: false, createdAt: '2026-07-08T08:15:00Z' },
  { id: 4, email: 'd@example.com', name: '阿宏', role: '技術主管／PM', experience: '5 年以上', frontendExperience: '不熟', budget: '6000以上',
    interest: ['Spring Security', 'Docker 部署', 'RAG 知識庫'], answers: { status: '評估團隊導入', goals: ['帶團隊導入 AI'], pain_points: ['資安疑慮'], suggestion: '想了解企業內訓方案。' },
    utm: { utm_source: 'facebook' }, consent: true, unsubscribed: true, createdAt: '2026-07-12T20:45:00Z' },
];

/** 統一失敗處理：印出訊息並以非零碼結束 */
const fail = (message) => { console.error('FAIL:', message); process.exitCode = 1; };

const browser = await chromium.launch();
try {
  const page = await browser.newPage();

  // 模板一律使用本地 admin.html 的 buildReportHtml()（file:// 開啟即可呼叫，不受線上部署版本影響）
  const adminHtml = path.resolve(scriptDir, '../src/main/resources/static/admin.html');
  await page.goto(pathToFileURL(adminHtml).href, { waitUntil: 'domcontentloaded' });

  // 依模式取得問卷資料：離線用內建範例、線上直接呼叫受保護 API
  let rows;
  let outName;   // 依模式決定輸出檔名
  if (SAMPLE_MODE) {
    rows = SAMPLE_ROWS;
    outName = 'survey-admin-report-sample.pdf';
    console.log(`OK 以 ${rows.length} 筆範例資料產生報表`);
  } else {
    if (!KEY) { fail('請先設定環境變數 ADMIN_API_KEY，或改用 --sample 模式驗證版面'); process.exit(1); }
    const response = await page.request.get(`${BASE}/api/admin/survey`, { headers: { 'X-Admin-Key': KEY } });
    if (!response.ok()) throw new Error(`問卷 API 失敗：HTTP ${response.status()}`);
    rows = await response.json();
    outName = 'survey-admin-report.pdf';
    console.log(`OK 已載入線上問卷資料：${rows.length} 筆`);
  }
  const reportHtml = await page.evaluate((data) => buildReportHtml(data), rows);

  // 以獨立分頁渲染報表 HTML，再用 Chromium 列印引擎輸出 A4 PDF（保留長條底色）
  const reportPage = await browser.newPage({ viewport: { width: 900, height: 1200 } });
  await reportPage.setContent(reportHtml, { waitUntil: 'networkidle' });
  await mkdir('output', { recursive: true });
  const outPath = path.join('output', outName);
  await reportPage.pdf({ path: outPath, format: 'A4', printBackground: true, margin: { top: '14mm', bottom: '14mm', left: '14mm', right: '14mm' } });
  // 同步輸出整頁 PNG，方便不開 PDF 也能直接檢視報表
  const pngPath = outPath.replace(/\.pdf$/, '.png');
  await reportPage.screenshot({ path: pngPath, fullPage: true });
  console.log(`OK PDF 已輸出：${outPath}`);
  console.log(`OK PNG 已輸出：${pngPath}${SAMPLE_MODE ? '' : '（含線上個資摘錄，不得提交 repo）'}`);
} catch (error) {
  fail(error.message);
} finally {
  await browser.close();
}
