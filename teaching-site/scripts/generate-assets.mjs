import fs from "node:fs/promises";
import path from "node:path";
import vm from "node:vm";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const assetDir = path.join(root, "assets", "illustrations");
const coursePath = path.join(root, "course-data.js");

const palette = {
  ink: "#0f172a",
  ink2: "#1e293b",
  slate: "#475569",
  muted: "#64748b",
  line: "#cbd5e1",
  bg: "#f8fafc",
  card: "#ffffff",
  blue: "#2563eb",
  cyan: "#0891b2",
  green: "#16a34a",
  amber: "#d97706",
  rose: "#e11d48",
  violet: "#7c3aed"
};

const unitSpecs = [
  {
    id: "u1",
    heroTitle: "Full-stack Monorepo",
    heroSubtitle: "Windows + Spring Boot + React 啟動骨架",
    diagramTitle: "專案啟動路線",
    terms: [
      ["Monorepo", "前後端共用同一個版本庫，讓腳本、文件與驗證集中管理。"],
      ["Health Check", "最小 API 端點，用來確認後端是否真的可連線。"],
      ["PowerShell / Zsh", "Win/macOS 自動化入口，負責環境檢查與啟動腳本。"]
    ],
    flow: ["環境檢查", "後端骨架", "前端骨架", "連線驗證"],
    colors: [palette.blue, palette.cyan, palette.green, palette.amber]
  },
  {
    id: "u2",
    heroTitle: "Spring MVC Domain API",
    heroSubtitle: "Controller / Service / DTO 邊界",
    diagramTitle: "MVC 請求生命週期",
    terms: [
      ["DispatcherServlet", "Spring MVC 的前端控制器，負責找到正確 Controller。"],
      ["DTO", "API 輸入輸出的資料契約，隔離資料庫 Entity。"],
      ["Bean Validation", "用註解保護輸入資料，錯誤回傳可讀的 400 格式。"]
    ],
    flow: ["HTTP Request", "Controller", "Service", "Response DTO"],
    colors: [palette.cyan, palette.blue, palette.violet, palette.green]
  },
  {
    id: "u3",
    heroTitle: "PostgreSQL + JPA",
    heroSubtitle: "Flyway migration 與動態查詢",
    diagramTitle: "資料持久化與搜尋",
    terms: [
      ["Flyway", "用版本化 SQL 管理資料庫結構，避免 ddl-auto 不可控變更。"],
      ["JPA Entity", "對應資料表的物件模型，描述關聯與交易邊界。"],
      ["Specification", "以 Criteria API 組合查詢條件，避免方法爆炸與 SQL 注入。"]
    ],
    flow: ["Migration", "Entity Mapping", "Repository", "Specification"],
    colors: [palette.green, palette.amber, palette.blue, palette.cyan]
  },
  {
    id: "u4",
    heroTitle: "JWT Security Boundary",
    heroSubtitle: "登入、角色、OpenAPI 與 ProblemDetail",
    diagramTitle: "安全請求管線",
    terms: [
      ["JWT", "登入後簽發的權杖，前端放在 Authorization header。"],
      ["Security Filter", "在 Controller 前攔截請求，驗證身分與角色。"],
      ["ProblemDetail", "RFC 7807 錯誤格式，讓前後端能穩定判讀例外。"]
    ],
    flow: ["Login", "Token", "Filter Chain", "Protected API"],
    colors: [palette.rose, palette.amber, palette.violet, palette.blue]
  },
  {
    id: "u5",
    heroTitle: "React CRM Workspace",
    heroSubtitle: "Dashboard、列表、看板與 API Client",
    diagramTitle: "前端整合架構",
    terms: [
      ["Axios Interceptor", "集中掛載 JWT 與處理 401，避免每個 API 重複寫邏輯。"],
      ["Skeleton State", "資料載入時的佔位 UI，讓畫面穩定不跳動。"],
      ["Role-based UI", "依使用者角色隱藏或停用不該出現的操作。"]
    ],
    flow: ["Login UI", "API Client", "Dashboard", "Kanban"],
    colors: [palette.blue, palette.violet, palette.cyan, palette.green]
  },
  {
    id: "u6",
    heroTitle: "Spring AI Streaming",
    heroSubtitle: "ChatClient、SSE 與 Tool Calling",
    diagramTitle: "AI 助理資料調用",
    terms: [
      ["SSE", "Server-Sent Events，讓 AI 回應可以逐字串流到前端。"],
      ["ChatClient", "Spring AI 的對話入口，集中設定模型與 system prompt。"],
      ["Tool Calling", "模型需要真實 CRM 數據時，回頭呼叫 Java service。"]
    ],
    flow: ["User Prompt", "ChatClient", "Tool Call", "Streaming UI"],
    colors: [palette.violet, palette.blue, palette.green, palette.cyan]
  },
  {
    id: "u7",
    heroTitle: "RAG + MCP Extension",
    heroSubtitle: "pgvector 知識庫與外部工具協定",
    diagramTitle: "檢索增強生成管線",
    terms: [
      ["Embedding", "把文件片段轉成向量，讓資料庫能做語意搜尋。"],
      ["RAG", "先檢索相關文件，再把上下文交給模型產生答案。"],
      ["MCP", "讓 AI 以標準協定連接外部系統，如信件、行事曆與報表。"]
    ],
    flow: ["Chunk", "Embedding", "pgvector", "Citation"],
    colors: [palette.cyan, palette.green, palette.blue, palette.amber]
  },
  {
    id: "u8",
    heroTitle: "Embabel Agent Trace",
    heroSubtitle: "GOAP、Blackboard、Replanning 與觀測",
    diagramTitle: "可追蹤 Agent Flow",
    terms: [
      ["GOAP", "Goal-Oriented Action Planning，依目標動態規劃 action 路徑。"],
      ["Blackboard", "Agent 共用狀態板，保存每一步 action 的輸入與輸出。"],
      ["Replanning", "審核失敗或資料不足時，重新規劃下一條可行路徑。"]
    ],
    flow: ["Snapshot", "Risk Review", "Draft Action", "Trace Panel"],
    colors: [palette.violet, palette.rose, palette.amber, palette.green]
  }
];

/**
 * 將文字中的特殊字元轉成 SVG 可安全呈現的實體。
 */
function esc(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

/**
 * 依固定字數切分文字，讓 SVG 中的中文說明不會超出卡片。
 */
/**
 * 智慧型文字切分換行函式，專門優化 SVG 卡片中的中英文混合排版。
 * (1) 遇到中日韓 (CJK) 字元時，可在字元間任意換行。
 * (2) 遇到英文單字時，保持單字完整，絕不在單字內部折行打斷。
 * (3) 考慮字元寬度差異，半形字元算 0.5 寬度，全形字元算 1.0 寬度。
 * 
 * @param {string} text 原始文字
 * @param {number} max 每一行最大允許寬度 (以等效全形字數計)
 * @returns {string[]} 切分後的行陣列
 */
function wrapText(text, max = 18) {
  if (!text) return [];
  
  // 透過正則表達式，將英文單字（字母、數字及底線、連字號）與 CJK 單字元分開
  const tokens = [];
  const regex = /([a-zA-Z0-9_\-]+|[\s\S])/g;
  let match;
  while ((match = regex.exec(String(text))) !== null) {
    if (match[0]) {
      tokens.push(match[0]);
    }
  }

  const lines = [];
  let currentLine = "";
  let currentWidth = 0; // 當前行累積的等效全形寬度

  for (const token of tokens) {
    // 計算該 token 的寬度
    let tokenWidth = 0;
    for (let i = 0; i < token.length; i++) {
      const code = token.charCodeAt(i);
      // 半形字元 (ASCII 0-127) 寬度計為 0.5，其餘全形字元計為 1.0
      if (code >= 0 && code <= 127) {
        tokenWidth += 0.5;
      } else {
        tokenWidth += 1.0;
      }
    }

    // 如果加入當前 token 會超出每行最大寬度限制，且當前行已有內容，則換行
    if (currentWidth + tokenWidth > max) {
      if (currentLine) {
        lines.push(currentLine);
        currentLine = token;
        currentWidth = tokenWidth;
      } else {
        // 若單一 token 寬度即大於 max (例如極長英文單字)，則強行放入當前行後換行
        currentLine = token;
        currentWidth = tokenWidth;
      }
    } else {
      // 否則累加至當前行
      currentLine += token;
      currentWidth += tokenWidth;
    }
  }

  // 放入最後未滿的一行
  if (currentLine) {
    lines.push(currentLine);
  }

  // 移除每行前後多餘的空格並過濾掉空行
  return lines.map((line) => line.trim()).filter(Boolean);
}

/**
 * 建立可重用的濾鏡、漸層與箭頭 marker。
 */
function svgDefs(id, accent = palette.blue) {
  return `
    <defs>
      <linearGradient id="${id}-bg" x1="0" x2="1" y1="0" y2="1">
        <stop offset="0%" stop-color="#f8fafc"/>
        <stop offset="52%" stop-color="#eff6ff"/>
        <stop offset="100%" stop-color="#ecfeff"/>
      </linearGradient>
      <linearGradient id="${id}-accent" x1="0" x2="1" y1="0" y2="1">
        <stop offset="0%" stop-color="${accent}"/>
        <stop offset="100%" stop-color="#0f172a"/>
      </linearGradient>
      <filter id="${id}-shadow" x="-20%" y="-20%" width="140%" height="150%">
        <feDropShadow dx="0" dy="16" stdDeviation="16" flood-color="#0f172a" flood-opacity="0.16"/>
      </filter>
      <marker id="${id}-arrow" markerWidth="14" markerHeight="14" refX="11" refY="7" orient="auto">
        <path d="M2,2 L12,7 L2,12 Z" fill="${accent}"/>
      </marker>
    </defs>`;
}

/**
 * 建立 SVG 標準外框與頂部標題。
 */
function frame({ id, title, subtitle, children, accent = palette.blue }) {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 675" width="1200" height="675" role="img" aria-labelledby="${id}-title ${id}-desc">
  <title id="${id}-title">${esc(title)}</title>
  <desc id="${id}-desc">${esc(subtitle)}</desc>
  ${svgDefs(id, accent)}
  <rect width="1200" height="675" rx="36" fill="url(#${id}-bg)"/>
  <path d="M0 126 C220 40 335 120 520 62 C715 0 835 82 1200 26 L1200 0 L0 0 Z" fill="${accent}" opacity="0.1"/>
  <circle cx="1030" cy="108" r="116" fill="${accent}" opacity="0.09"/>
  <circle cx="112" cy="572" r="92" fill="#14b8a6" opacity="0.08"/>
  <text x="56" y="70" fill="${palette.ink}" font-size="34" font-weight="800" font-family="system-ui, 'Microsoft JhengHei', sans-serif">${esc(title)}</text>
  <text x="58" y="108" fill="${palette.slate}" font-size="18" font-weight="600" font-family="system-ui, 'Microsoft JhengHei', sans-serif">${esc(subtitle)}</text>
  ${children}
</svg>
`;
}

/**
 * 建立章節 hero 圖：以產品工作台與技術模組表現章節主題。
 */
function heroSvg(unit, index) {
  const accent = unit.colors[0];
  const bars = unit.colors.map((color, i) => {
    const h = [134, 92, 168, 118][i];
    return `<rect x="${740 + i * 66}" y="${456 - h}" width="38" height="${h}" rx="12" fill="${color}" opacity="0.82"/>`;
  }).join("");
  const chips = unit.flow.map((item, i) =>
    `<g transform="translate(${88 + i * 246},548)">
      <rect width="196" height="54" rx="27" fill="#ffffff" stroke="${unit.colors[i]}" stroke-width="2"/>
      <circle cx="30" cy="27" r="10" fill="${unit.colors[i]}"/>
      <text x="52" y="34" fill="${palette.ink}" font-size="17" font-weight="700" font-family="system-ui, 'Microsoft JhengHei', sans-serif">${esc(item)}</text>
    </g>`
  ).join("");

  return frame({
    id: `${unit.id}-hero`,
    title: `Unit ${index + 1}｜${unit.heroTitle}`,
    subtitle: unit.heroSubtitle,
    accent,
    children: `
      <g filter="url(#${unit.id}-hero-shadow)">
        <rect x="64" y="152" width="672" height="350" rx="28" fill="#ffffff" stroke="${palette.line}"/>
        <rect x="64" y="152" width="672" height="64" rx="28" fill="${palette.ink}"/>
        <circle cx="110" cy="184" r="8" fill="#ef4444"/>
        <circle cx="136" cy="184" r="8" fill="#f59e0b"/>
        <circle cx="162" cy="184" r="8" fill="#22c55e"/>
        <text x="198" y="192" fill="#e2e8f0" font-size="18" font-weight="800" font-family="system-ui, 'Microsoft JhengHei', sans-serif">AI CRM Workspace</text>
        <rect x="104" y="250" width="218" height="206" rx="22" fill="#eff6ff" stroke="#bfdbfe"/>
        <text x="130" y="286" fill="${palette.blue}" font-size="18" font-weight="800" font-family="system-ui, 'Microsoft JhengHei', sans-serif">Customer Snapshot</text>
        <rect x="130" y="318" width="150" height="14" rx="7" fill="#93c5fd"/>
        <rect x="130" y="350" width="112" height="14" rx="7" fill="#bfdbfe"/>
        <rect x="130" y="392" width="164" height="36" rx="18" fill="${accent}" opacity="0.88"/>
        <text x="155" y="416" fill="#ffffff" font-size="15" font-weight="800" font-family="system-ui, 'Microsoft JhengHei', sans-serif">Verified Data</text>
        <rect x="356" y="250" width="330" height="206" rx="22" fill="#f8fafc" stroke="#dbe4ef"/>
        <path d="M390 410 C440 322 492 352 536 296 C578 242 632 304 664 260" fill="none" stroke="${accent}" stroke-width="8" stroke-linecap="round"/>
        <circle cx="390" cy="410" r="10" fill="${unit.colors[1]}"/>
        <circle cx="536" cy="296" r="10" fill="${unit.colors[2]}"/>
        <circle cx="664" cy="260" r="10" fill="${unit.colors[3]}"/>
        <text x="388" y="286" fill="${palette.ink}" font-size="18" font-weight="800" font-family="system-ui, 'Microsoft JhengHei', sans-serif">Delivery Signal</text>
        <text x="388" y="438" fill="${palette.muted}" font-size="15" font-weight="600" font-family="system-ui, 'Microsoft JhengHei', sans-serif">每章產出都回到同一套 AI CRM</text>
      </g>
      <g filter="url(#${unit.id}-hero-shadow)">
        <rect x="780" y="156" width="334" height="350" rx="28" fill="${palette.ink}" opacity="0.96"/>
        <text x="818" y="208" fill="#ffffff" font-size="24" font-weight="900" font-family="system-ui, 'Microsoft JhengHei', sans-serif">Engineering Stack</text>
        <text x="818" y="242" fill="#cbd5e1" font-size="16" font-weight="600" font-family="system-ui, 'Microsoft JhengHei', sans-serif">可測試、可維運、可追蹤</text>
        ${bars}
        <rect x="818" y="472" width="240" height="2" fill="#334155"/>
        <text x="818" y="306" fill="#e2e8f0" font-size="17" font-weight="700" font-family="system-ui, 'Microsoft JhengHei', sans-serif">${esc(unit.heroTitle)}</text>
        <text x="818" y="338" fill="#94a3b8" font-size="15" font-weight="600" font-family="system-ui, 'Microsoft JhengHei', sans-serif">Chapter hero visual</text>
      </g>
      ${chips}`
  });
}

/**
 * 建立精準流程圖：使用膠囊、矩形、菱形與六邊形區分不同語意。
 */
function diagramSvg(unit, index) {
  const accent = unit.colors[1];
  const x = [92, 350, 610, 880];
  const labels = unit.flow;
  
  const styleBlock = `
    <style>
      @keyframes flow-${unit.id} {
        to {
          stroke-dashoffset: -18;
        }
      }
      @keyframes scale-pulse-${unit.id} {
        0%, 100% {
          transform: scale(1);
          filter: drop-shadow(0 2px 4px rgba(15,23,42,0.06));
        }
        50% {
          transform: scale(1.06);
          filter: drop-shadow(0 12px 20px rgba(15,23,42,0.12));
        }
      }
      .flow-arrow-${unit.id} {
        stroke-dasharray: 10, 8;
        animation: flow-${unit.id} 1.2s linear infinite;
      }
      .node-${unit.id}-anim {
        transform-box: fill-box;
        transform-origin: center;
      }
      .node-${unit.id}-0 {
        animation: scale-pulse-${unit.id} 3s ease-in-out infinite;
        animation-delay: 0s;
      }
      .node-${unit.id}-1 {
        animation: scale-pulse-${unit.id} 3s ease-in-out infinite;
        animation-delay: 0.6s;
      }
      .node-${unit.id}-2 {
        animation: scale-pulse-${unit.id} 3s ease-in-out infinite;
        animation-delay: 1.2s;
      }
      .node-${unit.id}-3 {
        animation: scale-pulse-${unit.id} 3s ease-in-out infinite;
        animation-delay: 1.8s;
      }
    </style>
  `;

  const blocks = labels.map((label, i) => {
    const color = unit.colors[i];
    if (i === 0) {
      return `<g transform="translate(${x[i]},260)">
        <g class="node-${unit.id}-anim node-${unit.id}-0">
          <rect width="180" height="90" rx="45" fill="#ffffff" stroke="${color}" stroke-width="3"/>
          <text x="90" y="52" text-anchor="middle" fill="${palette.ink}" font-size="20" font-weight="900" font-family="system-ui, 'Microsoft JhengHei', sans-serif">${esc(label)}</text>
        </g>
      </g>`;
    }
    if (i === 2) {
      return `<g transform="translate(${x[i]},260)">
        <g class="node-${unit.id}-anim node-${unit.id}-2">
          <polygon points="90,0 180,45 90,90 0,45" fill="#ffffff" stroke="${color}" stroke-width="3"/>
          <text x="90" y="52" text-anchor="middle" fill="${palette.ink}" font-size="19" font-weight="900" font-family="system-ui, 'Microsoft JhengHei', sans-serif">${esc(label)}</text>
        </g>
      </g>`;
    }
    if (i === 3) {
      return `<g transform="translate(${x[i]},260)">
        <g class="node-${unit.id}-anim node-${unit.id}-3">
          <polygon points="28,0 152,0 180,45 152,90 28,90 0,45" fill="#ffffff" stroke="${color}" stroke-width="3"/>
          <text x="90" y="52" text-anchor="middle" fill="${palette.ink}" font-size="19" font-weight="900" font-family="system-ui, 'Microsoft JhengHei', sans-serif">${esc(label)}</text>
        </g>
      </g>`;
    }
    return `<g transform="translate(${x[i]},260)">
      <g class="node-${unit.id}-anim node-${unit.id}-${i}">
        <rect width="180" height="90" rx="18" fill="#ffffff" stroke="${color}" stroke-width="3"/>
        <text x="90" y="52" text-anchor="middle" fill="${palette.ink}" font-size="20" font-weight="900" font-family="system-ui, 'Microsoft JhengHei', sans-serif">${esc(label)}</text>
      </g>
    </g>`;
  }).join("");

  const arrows = [0, 1, 2].map((i) =>
    `<path class="flow-arrow-${unit.id}" d="M${x[i] + 194} 305 L${x[i + 1] - 22} 305" stroke="${accent}" stroke-width="5" fill="none" marker-end="url(#${unit.id}-diagram-arrow)"/>`
  ).join("");

  return frame({
    id: `${unit.id}-diagram`,
    title: `Unit ${index + 1}｜${unit.diagramTitle}`,
    subtitle: "把章節核心流程轉成可維護的工程圖，而不是裝飾性圖案。",
    accent,
    children: `
      ${styleBlock}
      <g filter="url(#${unit.id}-diagram-shadow)">
        <rect x="64" y="152" width="1072" height="386" rx="30" fill="#ffffff" stroke="${palette.line}"/>
        <text x="96" y="206" fill="${palette.ink}" font-size="24" font-weight="900" font-family="system-ui, 'Microsoft JhengHei', sans-serif">核心流程</text>
        <text x="96" y="238" fill="${palette.muted}" font-size="16" font-weight="600" font-family="system-ui, 'Microsoft JhengHei', sans-serif">用固定形狀語彙表示狀態、處理、判斷與目標</text>
        ${arrows}
        ${blocks}
        <g transform="translate(132,444)">
          <rect width="934" height="54" rx="16" fill="#f8fafc" stroke="#e2e8f0"/>
          <circle cx="34" cy="27" r="10" fill="${unit.colors[0]}"/><text x="54" y="33" fill="${palette.slate}" font-size="15" font-weight="700" font-family="system-ui, 'Microsoft JhengHei', sans-serif">條件 / 狀態</text>
          <rect x="206" y="17" width="20" height="20" rx="5" fill="${unit.colors[1]}"/><text x="238" y="33" fill="${palette.slate}" font-size="15" font-weight="700" font-family="system-ui, 'Microsoft JhengHei', sans-serif">處理步驟</text>
          <polygon points="422,17 442,27 422,37 402,27" fill="${unit.colors[2]}"/><text x="458" y="33" fill="${palette.slate}" font-size="15" font-weight="700" font-family="system-ui, 'Microsoft JhengHei', sans-serif">判斷點</text>
          <polygon points="650,17 688,17 700,27 688,37 650,37 638,27" fill="${unit.colors[3]}"/><text x="716" y="33" fill="${palette.slate}" font-size="15" font-weight="700" font-family="system-ui, 'Microsoft JhengHei', sans-serif">目標產出</text>
        </g>
      </g>`
  });
}

/**
 * 建立術語解釋圖：每張圖收斂三個本章最重要的專業詞。
 */
function termSvg(unit, index) {
  const accent = unit.colors[2];
  const cards = unit.terms.map(([term, desc], i) => {
    const lines = wrapText(desc, 14).slice(0, 4).map((line, lineIndex) =>
      `<text x="32" y="${104 + lineIndex * 28}" fill="${palette.slate}" font-size="17" font-weight="600" font-family="system-ui, 'Microsoft JhengHei', sans-serif">${esc(line)}</text>`
    ).join("");
    return `<g transform="translate(${90 + i * 350},190)" filter="url(#${unit.id}-term-shadow)">
      <rect width="310" height="320" rx="28" fill="#ffffff" stroke="#dbe4ef"/>
      <rect x="0" y="0" width="310" height="78" rx="28" fill="${unit.colors[i]}" opacity="0.96"/>
      <text x="32" y="50" fill="#ffffff" font-size="24" font-weight="900" font-family="system-ui, 'Microsoft JhengHei', sans-serif">${esc(term)}</text>
      ${lines}
      <path d="M32 260 L278 260" stroke="#e2e8f0" stroke-width="2"/>
      <text x="32" y="294" fill="${unit.colors[i]}" font-size="15" font-weight="900" font-family="system-ui, 'Microsoft JhengHei', sans-serif">Unit ${index + 1} keyword</text>
    </g>`;
  }).join("");

  return frame({
    id: `${unit.id}-term`,
    title: `Unit ${index + 1}｜專業術語解釋`,
    subtitle: "把抽象名詞變成可快速複習的概念圖卡。",
    accent,
    children: `
      ${cards}
      <text x="90" y="562" fill="${palette.muted}" font-size="17" font-weight="700" font-family="system-ui, 'Microsoft JhengHei', sans-serif">這張圖用於章節內快速回顧，協助學員把術語和實作位置連起來。</text>`
  });
}

/**
 * 建立全站首頁主視覺。
 */
function coverSvg() {
  return frame({
    id: "cover",
    title: "AI 賦能全端開發",
    subtitle: "從 Spring Boot + React 到 Spring AI + Embabel 的企業級 AI CRM 實戰路線",
    accent: palette.blue,
    children: `
      <g filter="url(#cover-shadow)">
        <rect x="84" y="156" width="1028" height="388" rx="34" fill="#ffffff" stroke="${palette.line}"/>
        <rect x="124" y="202" width="304" height="274" rx="28" fill="${palette.ink}"/>
        <text x="158" y="254" fill="#ffffff" font-size="27" font-weight="900" font-family="system-ui, 'Microsoft JhengHei', sans-serif">AI CRM</text>
        <text x="158" y="290" fill="#cbd5e1" font-size="17" font-weight="700" font-family="system-ui, 'Microsoft JhengHei', sans-serif">智慧業務助理</text>
        <rect x="158" y="336" width="216" height="24" rx="12" fill="#334155"/>
        <rect x="158" y="382" width="170" height="24" rx="12" fill="#475569"/>
        <rect x="158" y="428" width="226" height="24" rx="12" fill="#2563eb"/>
        <path d="M492 408 C560 260 646 442 714 300 C770 184 866 290 964 218" fill="none" stroke="${palette.blue}" stroke-width="12" stroke-linecap="round"/>
        <circle cx="492" cy="408" r="16" fill="${palette.cyan}"/>
        <circle cx="714" cy="300" r="16" fill="${palette.green}"/>
        <circle cx="964" cy="218" r="16" fill="${palette.amber}"/>
        <g transform="translate(506,456)">
          ${["Spring Boot", "React", "Spring AI", "RAG", "MCP", "Embabel"].map((label, i) =>
            `<rect x="${(i % 3) * 170}" y="${Math.floor(i / 3) * 52}" width="142" height="34" rx="17" fill="${[palette.blue, palette.cyan, palette.green, palette.amber, palette.violet, palette.rose][i]}" opacity="0.92"/>
             <text x="${(i % 3) * 170 + 71}" y="${Math.floor(i / 3) * 52 + 23}" text-anchor="middle" fill="#ffffff" font-size="14" font-weight="900" font-family="system-ui, 'Microsoft JhengHei', sans-serif">${label}</text>`
          ).join("")}
        </g>
      </g>`
  });
}

/**
 * 讀取現有 course-data.js 以便同步更新 day units 與 flattened units。
 */
async function loadCourse() {
  const code = await fs.readFile(coursePath, "utf8");
  const context = { window: {} };
  vm.createContext(context);
  vm.runInContext(code, context);
  return context.window.COURSE;
}

/**
 * 將新插圖 manifest 寫回 day units 與頂層 units，避免渲染與驗證看到不同資料。
 */
function syncIllustrations(course) {
  const byId = new Map(unitSpecs.map((unit) => [unit.id, unit]));
  const allUnits = [];
  for (const dayMeta of course.meta.days) {
    const day = course[dayMeta.id];
    for (const unit of day.units || []) {
      const spec = byId.get(unit.id);
      if (!spec) continue;
      unit.illustrations = [
        { name: `${unit.id}-1.png`, kind: "hero", alt: spec.heroTitle, spec: spec.heroSubtitle },
        { name: `${unit.id}-2.svg`, kind: "diagram", alt: spec.diagramTitle, spec: `流程圖：${spec.flow.join(" -> ")}` },
        { name: `${unit.id}-3-term.svg`, kind: "term", alt: `${unit.title} 專業術語解釋`, spec: spec.terms.map(([term]) => term).join(" / ") }
      ];
      allUnits.push(unit);
    }
  }
  course.units = allUnits;
}

/**
 * 寫出格式化後的 course-data.js，保留其作為網站資料唯一來源。
 */
async function writeCourse(course) {
  const code = `// AI CRM 課程教學網站 - 結構化課程資料 (Single Source of Truth)\n// 此檔案由 scripts/generate-assets.mjs 同步插圖 manifest 後產生。\n\nwindow.COURSE = ${JSON.stringify(course, null, 2)};\n`;
  await fs.writeFile(coursePath, code, "utf8");
}

/**
 * 主流程：重製 cover、每章 hero、流程插圖與專業術語解釋圖片。
 */
async function main() {
  await fs.mkdir(assetDir, { recursive: true });
  await fs.writeFile(path.join(assetDir, "cover.svg"), coverSvg(), "utf8");
  for (const [index, unit] of unitSpecs.entries()) {
    await fs.writeFile(path.join(assetDir, `${unit.id}-1.svg`), heroSvg(unit, index), "utf8");
    await fs.writeFile(path.join(assetDir, `${unit.id}-2.svg`), diagramSvg(unit, index), "utf8");
    await fs.writeFile(path.join(assetDir, `${unit.id}-3-term.svg`), termSvg(unit, index), "utf8");
  }
  const course = await loadCourse();
  syncIllustrations(course);
  await writeCourse(course);
  console.log(`OK: generated cover + ${unitSpecs.length * 3} unit visuals in ${assetDir}`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
