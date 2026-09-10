import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const outputDir = fileURLToPath(new URL('../assets/diagrams/', import.meta.url));
const W = 1400;
const H = 620;

/** 將文字安全放入 SVG XML，避免標籤中的特殊字元破壞圖檔。 */
function esc(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

/** 將一段中文拆成多行 SVG tspan，避免固定寬度卡片裁切文字。 */
function textLines(lines, x, y, size = 28, color = '#eaf2ff', weight = 600, gap = 36) {
  return `<text x="${x}" y="${y}" fill="${color}" font-family="Microsoft JhengHei, Noto Sans TC, Segoe UI, sans-serif" font-size="${size}" font-weight="${weight}" text-anchor="middle">${lines.map((line, index) => `<tspan x="${x}" dy="${index === 0 ? 0 : gap}">${esc(line)}</tspan>`).join('')}</text>`;
}

/** 建立流程圖共用的 SVG 標頭與箭頭定義。 */
function svgStart(title, subtitle) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" role="img" aria-labelledby="title desc">
  <title id="title">${esc(title)}</title>
  <desc id="desc">${esc(subtitle)}</desc>
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#0d1728"/><stop offset="1" stop-color="#182b48"/></linearGradient>
    <filter id="shadow" x="-20%" y="-20%" width="140%" height="140%"><feDropShadow dx="0" dy="10" stdDeviation="10" flood-color="#000000" flood-opacity=".28"/></filter>
    <marker id="arrow" markerWidth="12" markerHeight="12" refX="10" refY="6" orient="auto"><path d="M0,0 L12,6 L0,12 z" fill="#8bb8ff"/></marker>
  </defs>
  <rect width="${W}" height="${H}" rx="28" fill="url(#bg)"/>
  ${textLines([title], W / 2, 52, 32, '#ffffff', 750, 38)}
  ${textLines([subtitle], W / 2, 96, 18, '#a7bad7', 400, 26)}
  `;
}

/** 建立代表處理動作的圓角矩形。 */
function action({ x, y, w, h, lines, accent = '#4f8cff' }) {
  return `<g filter="url(#shadow)"><rect x="${x}" y="${y}" width="${w}" height="${h}" rx="14" fill="#213653" stroke="${accent}" stroke-width="3"/>${textLines(lines, x + w / 2, y + h / 2 - ((lines.length - 1) * 16), 24, '#f2f7ff', 700, 34)}</g>`;
}

/** 建立代表狀態或資料物件的膠囊形。 */
function state({ x, y, w, h, lines, accent = '#42cfa0' }) {
  return `<g filter="url(#shadow)"><rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${h / 2}" fill="#173e45" stroke="${accent}" stroke-width="3"/>${textLines(lines, x + w / 2, y + h / 2 - ((lines.length - 1) * 15), 23, '#effff8', 700, 32)}</g>`;
}

/** 建立代表判斷的菱形，讓流程圖不只靠顏色傳達語意。 */
function decision({ x, y, w, h, lines, accent = '#f3b45c' }) {
  const points = `${x + w / 2},${y} ${x + w},${y + h / 2} ${x + w / 2},${y + h} ${x},${y + h / 2}`;
  return `<g filter="url(#shadow)"><polygon points="${points}" fill="#4a3821" stroke="${accent}" stroke-width="3"/>${textLines(lines, x + w / 2, y + h / 2 - ((lines.length - 1) * 15), 22, '#fff7e8', 700, 30)}</g>`;
}

/** 建立代表結束或交付目標的六邊形。 */
function goal({ x, y, w, h, lines, accent = '#d986e8' }) {
  const inset = 28;
  const points = `${x + inset},${y} ${x + w - inset},${y} ${x + w},${y + h / 2} ${x + w - inset},${y + h} ${x + inset},${y + h} ${x},${y + h / 2}`;
  return `<g filter="url(#shadow)"><polygon points="${points}" fill="#432c55" stroke="${accent}" stroke-width="3"/>${textLines(lines, x + w / 2, y + h / 2 - ((lines.length - 1) * 15), 23, '#fff0ff', 700, 32)}</g>`;
}

/** 建立帶方向的流程箭頭。 */
function arrow(x1, y1, x2, y2, label = '') {
  const labelMarkup = label ? textLines([label], (x1 + x2) / 2, (y1 + y2) / 2 - 12, 17, '#b9cdf0', 600, 24) : '';
  return `<path d="M${x1} ${y1} L${x2} ${y2}" fill="none" stroke="#8bb8ff" stroke-width="4" marker-end="url(#arrow)"/>${labelMarkup}`;
}

/** 建立圖例，說明不同形狀代表的概念。 */
function legend() {
  return `<g transform="translate(58 548)">
    <rect x="0" y="0" width="18" height="18" rx="5" fill="#213653" stroke="#4f8cff" stroke-width="2"/><text x="28" y="15" fill="#a7bad7" font-size="16">Action</text>
    <rect x="110" y="0" width="52" height="18" rx="9" fill="#173e45" stroke="#42cfa0" stroke-width="2"/><text x="174" y="15" fill="#a7bad7" font-size="16">狀態／資料</text>
    <polygon points="300,9 312,0 324,9 312,18" fill="#4a3821" stroke="#f3b45c" stroke-width="2"/><text x="336" y="15" fill="#a7bad7" font-size="16">判斷</text>
    <polygon points="405,0 445,0 457,9 445,18 405,18 393,9" fill="#432c55" stroke="#d986e8" stroke-width="2"/><text x="469" y="15" fill="#a7bad7" font-size="16">交付</text>
  </g>`;
}

/** 將一張流程圖的 SVG 來源寫入 assets/diagrams。 */
async function writeDiagram(name, content) {
  await writeFile(resolve(outputDir, `${name}.svg`), `${content}</svg>`, 'utf8');
}

await mkdir(outputDir, { recursive: true });

await writeDiagram('d1-u1-agentic-loop', `${svgStart('Agentic Coding 審查迴圈', '人的角色不是逐行指揮，而是審查每一站的產物')}
  ${action({ x: 90, y: 190, w: 210, h: 100, lines: ['理解需求'], accent: '#4f8cff' })}
  ${action({ x: 370, y: 190, w: 210, h: 100, lines: ['規劃'], accent: '#4f8cff' })}
  ${action({ x: 650, y: 190, w: 210, h: 100, lines: ['生成'], accent: '#4f8cff' })}
  ${action({ x: 930, y: 190, w: 210, h: 100, lines: ['驗證'], accent: '#4f8cff' })}
  ${decision({ x: 1190, y: 175, w: 140, h: 130, lines: ['證據', '足夠？'] })}
  ${arrow(300, 240, 370, 240)}${arrow(580, 240, 650, 240)}${arrow(860, 240, 930, 240)}${arrow(1140, 240, 1190, 240)}
  ${arrow(1260, 305, 1260, 430, '否')}${arrow(1190, 470, 300, 470, '回到需求／計畫')}${arrow(1330, 240, 1360, 240, '是')}
  ${goal({ x: 1170, y: 420, w: 180, h: 90, lines: ['可審查', '交付物'] })}
  ${textLines(['每一步都留下可閱讀、可驗證、可重跑的中間產物'], W / 2, 505, 20, '#d9e7ff', 500, 28)}
  ${legend()}`);

await writeDiagram('d1-u1-auditable-output', `${svgStart('從一句話到可行性評估', '指定產物格式，讓風險與未知數在寫程式前被看見')}
  ${state({ x: 80, y: 210, w: 250, h: 82, lines: ['模糊需求'], accent: '#ef8c6b' })}
  ${arrow(330, 251, 410, 251)}
  ${action({ x: 410, y: 190, w: 250, h: 122, lines: ['固定欄位', '可行性報告'] })}
  ${arrow(660, 251, 740, 251)}
  ${decision({ x: 740, y: 188, w: 180, h: 126, lines: ['有未知數？'] })}
  ${arrow(920, 251, 1010, 251, '有')}
  ${action({ x: 1010, y: 190, w: 250, h: 122, lines: ['設計 spike', '先驗證'] })}
  ${arrow(830, 314, 830, 420, '無／已釐清')}
  ${goal({ x: 690, y: 420, w: 280, h: 90, lines: ['可進入', '設計期'] })}
  ${textLines(['五個欄位：技術、難度、風險、資源、驗證順序'], W / 2, 370, 22, '#d9e7ff', 600, 30)}
  ${legend()}`);

await writeDiagram('d1-u2-selection-plan', `${svgStart('設計期三段式', '先選型、再計畫、最後才實作；每段都有可審查輸出')}
  ${state({ x: 80, y: 210, w: 220, h: 90, lines: ['需求＋限制'] })}
  ${arrow(300, 255, 370, 255)}
  ${action({ x: 370, y: 190, w: 260, h: 130, lines: ['選型分析', '比較 2–4 方案'] })}
  ${arrow(630, 255, 700, 255)}
  ${action({ x: 700, y: 190, w: 260, h: 130, lines: ['開發計畫', '里程碑＋驗收'] })}
  ${arrow(960, 255, 1030, 255)}
  ${action({ x: 1030, y: 190, w: 260, h: 130, lines: ['第一階段', '最小實作'] })}
  ${arrow(1160, 320, 1160, 425)}
  ${goal({ x: 1010, y: 425, w: 300, h: 90, lines: ['可驗收', '實作切片'] })}
  ${textLines(['推薦必須附上有效邊界：條件變了，推薦也要能變'], W / 2, 390, 22, '#d9e7ff', 600, 30)}
  ${legend()}`);

await writeDiagram('d1-u2-acceptance-criteria', `${svgStart('把「功能正常」改成可驗收條件', '驗收句型：當……，執行……，應該……')}
  ${state({ x: 80, y: 205, w: 220, h: 90, lines: ['當', '輸入狀態'] })}
  ${arrow(300, 250, 390, 250)}
  ${action({ x: 390, y: 190, w: 230, h: 120, lines: ['執行', '使用者動作'] })}
  ${arrow(620, 250, 710, 250)}
  ${decision({ x: 710, y: 185, w: 180, h: 130, lines: ['結果', '可觀察？'] })}
  ${arrow(890, 250, 990, 250, '是')}
  ${goal({ x: 990, y: 205, w: 290, h: 90, lines: ['留下證據', '測試／截圖'] })}
  ${arrow(800, 315, 800, 425, '否')}
  ${action({ x: 650, y: 425, w: 300, h: 85, lines: ['改寫驗收句子'] })}
  ${textLines(['「功能正常」不能直接打勾；輸入、動作、結果要能被第三人重做'], W / 2, 370, 22, '#d9e7ff', 600, 30)}
  ${legend()}`);

await writeDiagram('d2-u1-tdd-red-green-refactor', `${svgStart('TDD：Red → Green → Refactor', '先用測試描述行為，再用最小實作滿足它')}
  ${state({ x: 85, y: 215, w: 220, h: 90, lines: ['測試案例'], accent: '#ef8c6b' })}
  ${arrow(305, 260, 390, 260)}
  ${action({ x: 390, y: 195, w: 230, h: 130, lines: ['Red', '確認有意義的失敗'], accent: '#ef8c6b' })}
  ${arrow(620, 260, 705, 260)}
  ${action({ x: 705, y: 195, w: 230, h: 130, lines: ['Green', '最小實作通過'], accent: '#42cfa0' })}
  ${arrow(935, 260, 1020, 260)}
  ${action({ x: 1020, y: 195, w: 230, h: 130, lines: ['Refactor', '不改變行為'], accent: '#d986e8' })}
  ${arrow(1135, 325, 1135, 430, '下一個案例')}
  ${arrow(1135, 430, 195, 430)}
  ${textLines(['紅燈不是任何錯誤；必須能說明失敗正好對應尚未滿足的規格'], W / 2, 380, 22, '#d9e7ff', 600, 30)}
  ${legend()}`);

await writeDiagram('d2-u1-e2e-to-sop', `${svgStart('一次執行，兩份資產', 'E2E 給機器回歸，截圖與說明給人操作')}
  ${state({ x: 75, y: 220, w: 220, h: 90, lines: ['使用者流程'] })}
  ${arrow(295, 265, 385, 265)}
  ${action({ x: 385, y: 195, w: 250, h: 130, lines: ['Playwright', '可重跑腳本'] })}
  ${arrow(635, 265, 725, 265)}
  ${action({ x: 725, y: 195, w: 250, h: 130, lines: ['執行與斷言', '留下 trace'] })}
  ${arrow(850, 325, 850, 430)}
  ${state({ x: 730, y: 430, w: 240, h: 80, lines: ['步驟截圖'] })}
  ${arrow(975, 470, 1070, 470)}
  ${goal({ x: 1070, y: 425, w: 260, h: 90, lines: ['Markdown', 'SOP'] })}
  ${textLines(['流程改變 → 測試重跑 → 圖片與 SOP 同步更新'], W / 2, 375, 22, '#d9e7ff', 600, 30)}
  ${legend()}`);

await writeDiagram('d2-u2-baseline-audit', `${svgStart('基準式稽核：兩條路徑，一份報告', '程式碼風險與供應鏈風險要分開檢查')}
  ${state({ x: 70, y: 205, w: 230, h: 90, lines: ['專案版本'] })}
  ${arrow(300, 250, 390, 250)}
  ${action({ x: 390, y: 170, w: 260, h: 105, lines: ['程式碼稽核', 'OWASP／CWE'] })}
  ${arrow(300, 250, 390, 440)}
  ${action({ x: 390, y: 390, w: 260, h: 105, lines: ['相依稽核', 'npm audit／CVE'] })}
  ${arrow(650, 223, 760, 310)}${arrow(650, 442, 760, 350)}
  ${action({ x: 760, y: 250, w: 260, h: 105, lines: ['證據整理', '命中／未命中'] })}
  ${arrow(1020, 302, 1110, 302)}
  ${goal({ x: 1110, y: 255, w: 230, h: 95, lines: ['稽核報告', '含限制'] })}
  ${textLines(['基準能提高覆蓋率，但不能把稽核報告冒充滲透測試'], W / 2, 545, 22, '#d9e7ff', 600, 30)}
  ${legend()}`);

await writeDiagram('d2-u2-finding-remediation', `${svgStart('發現到修補的證據鏈', '每一個 finding 都要能重現、修補、回歸')}
  ${state({ x: 70, y: 220, w: 210, h: 90, lines: ['Finding'] })}
  ${arrow(280, 265, 365, 265)}
  ${action({ x: 365, y: 195, w: 220, h: 130, lines: ['重現', '保存原始證據'] })}
  ${arrow(585, 265, 670, 265)}
  ${action({ x: 670, y: 195, w: 220, h: 130, lines: ['修補', '對外／內部分流'] })}
  ${arrow(890, 265, 975, 265)}
  ${decision({ x: 975, y: 190, w: 180, h: 150, lines: ['回歸', '通過？'] })}
  ${arrow(1155, 265, 1250, 265, '是')}
  ${goal({ x: 1170, y: 205, w: 190, h: 95, lines: ['關閉', '並記錄'] })}
  ${arrow(1065, 340, 1065, 445, '否')}
  ${arrow(1065, 445, 475, 445, '回到修補')}
  ${textLines(['不能只把錯誤藏起來：資訊要從回應移到適當日誌，並留下回歸測試'], W / 2, 380, 22, '#d9e7ff', 600, 30)}
  ${legend()}`);
