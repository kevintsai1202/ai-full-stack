/**
 * AI CRM 課程教學網站 — 純 HTML/JS/CSS 版本
 * 將原 React 19 + Vite SPA 轉換為零框架、零建置步驟的靜態應用。
 * 資料來源：window.COURSE（由 course-data.js 提供）
 * 狀態持久化：localStorage
 */

/* ──────────────────────────────────────────────
   1. 狀態管理
   ────────────────────────────────────────────── */

const STORE_KEY = "hahow-ai-crm-progress-v2";

/** 每個單元的觀念分頁索引（key = unit.id） */
const conceptTabState = {};

/**
 * 讀取 localStorage 狀態，集中管理主題、任務、測驗與側欄狀態。
 * @returns {object} 狀態物件
 */
function loadState() {
  try {
    const raw = localStorage.getItem(STORE_KEY);
    return raw ? JSON.parse(raw) : { theme: "light", tasks: {}, quiz: {}, navGroups: {}, platform: "windows" };
  } catch {
    return { theme: "light", tasks: {}, quiz: {}, navGroups: {}, platform: "windows" };
  }
}

/**
 * 寫回 localStorage 狀態，確保重新整理後進度仍存在。
 * @param {object} s 狀態物件
 */
function saveState(s) {
  localStorage.setItem(STORE_KEY, JSON.stringify(s));
}

/** 全域應用程式狀態 */
let state = loadState();

/** 全域搜尋文字 */
let searchQuery = "";

/** 手風琴預設展開 */
let accordionOpen = true;

/** 目前預覽中的教材（null 表示關閉） */
let previewMaterial = null;

/**
 * 套用狀態變更並更新 DOM
 * @param {object|function} patch 要合併的狀態片段或更新函式
 */
function patchState(patch) {
  state = typeof patch === "function" ? patch(state) : { ...state, ...patch };
  saveState(state);
  applyTheme();
  renderApp();
}

/** 將目前主題套用到 HTML 根元素 */
function applyTheme() {
  document.documentElement.setAttribute("data-theme", state.theme || "light");
  document.body.classList.toggle("sidebar-hidden", !!state.sidebarHidden);
}

/* ──────────────────────────────────────────────
   2. 工具函式
   ────────────────────────────────────────────── */

/**
 * 根據當前選擇的平台，動態轉換文字中的系統關鍵字。
 * 並且統一過濾移除提示詞標題開頭的 "A. " 字首。
 * @param {string} text 原始文字
 * @param {string} platform 當前平台 ("windows" 或 "mac")
 * @returns {string} 轉換後的文字
 */
function translatePlatformText(text, platform) {
  if (!text) return text;
  let result = text.replace(/^A\.\s*/, "");
  if (platform !== "mac") return result;
  return result
    .replace(/Windows 11/g, "macOS")
    .replace(/Windows/g, "macOS")
    .replace(/PowerShell 7\+/g, "zsh / bash")
    .replace(/PowerShell 7/g, "zsh / bash")
    .replace(/PowerShell/g, "zsh")
    .replace(/Invoke-RestMethod/g, "curl")
    .replace(/test-crm-api\.ps1/g, "test-crm-api.sh")
    .replace(/verify-u3\.ps1/g, "verify-u3.sh")
    .replace(/verify-u4\.ps1/g, "verify-u4.sh");
}

/**
 * 跳脫 HTML 特殊字元，防止 XSS
 * @param {string} str 原始字串
 * @returns {string} 安全字串
 */
function esc(str) {
  if (!str) return "";
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

/**
 * 將粗體、行內程式碼與 Markdown 連結轉成 HTML 字串。
 * @param {string} text 原始 Markdown 行內文字
 * @returns {string} HTML 字串
 */
function inlineMarkdown(text) {
  if (!text) return "";
  const pattern = /(\*\*[^*]+\*\*|`[^`]+`|\[[^\]]+\]\([^)]+\))/g;
  return String(text).split(pattern).map((part) => {
    if (!part) return "";
    if (part.startsWith("**") && part.endsWith("**")) {
      return `<strong>${esc(part.slice(2, -2))}</strong>`;
    }
    if (part.startsWith("`") && part.endsWith("`")) {
      return `<code>${esc(part.slice(1, -1))}</code>`;
    }
    const linkMatch = part.match(/^\[([^\]]+)\]\(([^)]+)\)$/);
    if (linkMatch) {
      return `<a href="${esc(linkMatch[2])}" target="_blank" rel="noopener">${esc(linkMatch[1])}</a>`;
    }
    return esc(part);
  }).join("");
}

/**
 * 簡易安全 Markdown 解析器，將講義 Markdown 字串轉換為 HTML。
 * 支援標題、段落、列表、程式碼區塊及行內標記的解析。
 * @param {string} mdText Markdown 原始文字
 * @returns {string} HTML 字串
 */
function parseMarkdown(mdText) {
  if (!mdText) return "";
  const lines = mdText.split(/\r?\n/);
  const parts = [];

  let inCodeBlock = false;
  let codeLang = "";
  let codeLines = [];
  let listType = null;
  let listItems = [];

  /** 將暫存的列表項目轉為 HTML 並推入 parts */
  const flushList = () => {
    if (listItems.length > 0) {
      const tag = listType === "ol" ? "ol" : "ul";
      parts.push(`<${tag}>${listItems.join("")}</${tag}>`);
      listItems = [];
      listType = null;
    }
  };

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];

    if (line.trim().startsWith("```")) {
      if (inCodeBlock) {
        parts.push(`<pre class="code-block lang-${esc(codeLang)}"><code>${esc(codeLines.join("\n"))}</code></pre>`);
        codeLines = [];
        inCodeBlock = false;
        codeLang = "";
      } else {
        flushList();
        inCodeBlock = true;
        codeLang = line.trim().slice(3).trim();
      }
      continue;
    }

    if (inCodeBlock) { codeLines.push(line); continue; }

    const trimmed = line.trim();
    if (!trimmed) { flushList(); continue; }

    if (line.startsWith("# "))   { flushList(); parts.push(`<h1>${inlineMarkdown(line.slice(2))}</h1>`); continue; }
    if (line.startsWith("## "))  { flushList(); parts.push(`<h2>${inlineMarkdown(line.slice(3))}</h2>`); continue; }
    if (line.startsWith("### ")) { flushList(); parts.push(`<h3>${inlineMarkdown(line.slice(4))}</h3>`); continue; }

    const ulMatch = line.match(/^(\s*)[-*]\s+(.*)$/);
    if (ulMatch) {
      if (listType !== "ul") { flushList(); listType = "ul"; }
      listItems.push(`<li>${inlineMarkdown(ulMatch[2])}</li>`);
      continue;
    }

    const olMatch = line.match(/^(\s*)\d+\.\s+(.*)$/);
    if (olMatch) {
      if (listType !== "ol") { flushList(); listType = "ol"; }
      listItems.push(`<li>${inlineMarkdown(olMatch[2])}</li>`);
      continue;
    }

    flushList();
    // 檢查此行是否包含 HTML 標籤結構以避免轉義，直接輸出 HTML 標籤
    if (/<[a-zA-Z\/][^>]*>/.test(line)) {
      parts.push(line);
    } else {
      parts.push(`<p class="preview-paragraph">${inlineMarkdown(line)}</p>`);
    }
  }

  flushList();
  return parts.join("");
}

/**
 * 複製文字到剪貼簿；瀏覽器權限不允許時改用 textarea fallback。
 * @param {string} text 要複製的文字
 */
async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    const textarea = document.createElement("textarea");
    textarea.value = text;
    textarea.style.position = "fixed";
    textarea.style.top = "0";
    textarea.style.left = "0";
    document.body.append(textarea);
    textarea.focus();
    textarea.select();
    document.execCommand("copy");
    textarea.remove();
  }
}

/**
 * 依照 day metadata 彙整所有課程單元，供導覽、進度與渲染共用。
 * @param {object} course 課程資料物件
 * @returns {Array} 單元列表
 */
function getAllUnits(course) {
  return course.meta.days.flatMap((dayMeta) => {
    const day = course[dayMeta.id];
    return (day?.units || []).map((unit) => ({ dayMeta, day, unit }));
  });
}

/**
 * 將圖片種類轉成使用者可讀標籤。
 * @param {string} kind 圖片種類
 * @returns {string} 中文標籤
 */
function getIllustrationKindLabel(kind) {
  const labels = { hero: "章節主視覺", diagram: "架構插圖", term: "專業術語圖" };
  return labels[kind] || "教學圖片";
}

// 8 個單元各自的 SVG 圖示路徑
const UNIT_GLYPH_PATHS = [
  "M5 15c-1 2-1 4-1 4s2 0 4-1m-3-3a8 8 0 0 1 2-5c3-4 7-5 11-5 0 4-1 8-5 11a8 8 0 0 1-5 2zm9-9a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z",
  "M9 4c-2 0-3 1-3 3v2c0 1-1 2-2 2 1 0 2 1 2 2v4c0 2 1 3 3 3m6-21c2 0 3 1 3 3v2c0 1 1 2 2 2-1 0-2 1-2 2v4c0 2-1 3-3 3",
  "M12 4c4 0 8 1 8 3s-4 3-8 3-8-1-8-3 4-3 8-3zm-8 3v5c0 2 4 3 8 3s8-1 8-3V7m-16 5v5c0 2 4 3 8 3s8-1 8-3v-5",
  "M12 3l7 3v5c0 4-3 8-7 10-4-2-7-6-7-10V6zm-2.5 8.5l2 2 4-4",
  "M4 5h16v14H4zm0 5h16M9 10v9",
  "M4 5h16v11H9l-5 4z",
  "M5 4h11a2 2 0 0 1 2 2v14H7a2 2 0 0 0-2 2zm2 5h8m-8 3h6",
  "M6 5a2 2 0 1 0 0 4 2 2 0 0 0 0-4zm12 0a2 2 0 1 0 0 4 2 2 0 0 0 0-4zM6 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM8 7h8m-2 0v4a2 2 0 0 1-2 2H8m0 0v2"
];

/**
 * 依單元序號回傳對應的 inline SVG 圖示 HTML。
 * @param {number} index 單元索引 (0 起算)
 * @returns {string} SVG HTML
 */
function unitGlyph(index) {
  const path = UNIT_GLYPH_PATHS[index % UNIT_GLYPH_PATHS.length];
  return `<svg class="unit-glyph" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${path}"/></svg>`;
}

/** 小型已完成勾選 SVG 標記 */
function featureCheckIcon() {
  return `<svg class="feature-check-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7"/></svg>`;
}

/* ──────────────────────────────────────────────
   3. SVG 圖解渲染函式
   ────────────────────────────────────────────── */

/** Spring MVC 請求流動 SVG 圖解 */
function svgSpringMvcFlow() {
  return `<svg class="concept-svg-illustration" viewBox="0 0 400 220" fill="none" xmlns="http://www.w3.org/2000/svg">
    <rect width="400" height="220" rx="12" fill="var(--surface)" stroke="var(--border)" stroke-width="1"/>
    <g class="svg-flow-node" transform="translate(20, 90)">
      <rect width="70" height="40" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" stroke-width="1.5"/>
      <text x="35" y="24" fill="var(--fg)" font-size="10" font-weight="800" text-anchor="middle">Client 瀏覽器</text>
    </g>
    <path d="M 90 110 H 130" stroke="var(--accent)" stroke-width="1.5" marker-end="url(#arrow)"/>
    <path class="svg-flow-arrow" d="M 90 110 H 130" stroke="white" stroke-width="1.5"/>
    <g class="svg-flow-node" transform="translate(130, 60)">
      <rect width="90" height="100" rx="10" fill="var(--accent)" opacity="0.1"/>
      <rect width="90" height="100" rx="10" stroke="var(--accent)" stroke-width="2"/>
      <text x="45" y="45" fill="var(--accent-deep)" font-size="10" font-weight="800" text-anchor="middle">Dispatcher</text>
      <text x="45" y="62" fill="var(--accent-deep)" font-size="10" font-weight="800" text-anchor="middle">Servlet</text>
      <text x="45" y="80" fill="var(--muted)" font-size="8" text-anchor="middle">(前端控制器)</text>
    </g>
    <path d="M 220 95 L 260 70" stroke="var(--border-strong)" stroke-width="1.5"/>
    <path class="svg-flow-arrow" d="M 220 95 L 260 70" stroke="var(--accent)" stroke-width="1.5"/>
    <g class="svg-flow-node" transform="translate(260, 40)">
      <rect width="90" height="36" rx="6" fill="var(--surface-2)" stroke="var(--border-strong)" stroke-width="1.5"/>
      <text x="45" y="22" fill="var(--fg)" font-size="9" font-weight="800" text-anchor="middle">Controller</text>
    </g>
    <path d="M 305 76 V 130" stroke="var(--border-strong)" stroke-width="1.5"/>
    <g class="svg-flow-node" transform="translate(260, 130)">
      <rect width="90" height="36" rx="6" fill="var(--surface-2)" stroke="var(--border-strong)" stroke-width="1.5"/>
      <text x="45" y="22" fill="var(--fg)" font-size="9" font-weight="800" text-anchor="middle">Service</text>
    </g>
    <path d="M 260 150 L 220 125" stroke="var(--border-strong)" stroke-width="1.5"/>
    <defs>
      <marker id="arrow" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="5" markerHeight="5" orient="auto-start-reverse">
        <path d="M 0 0 L 10 5 L 0 10 z" fill="var(--accent)"/>
      </marker>
    </defs>
  </svg>`;
}

/** REST API 設計語意 SVG 圖解 */
function svgRestApi() {
  return `<svg class="concept-svg-illustration" viewBox="0 0 400 220" fill="none" xmlns="http://www.w3.org/2000/svg">
    <rect width="400" height="220" rx="12" fill="var(--surface)" stroke="var(--border)" stroke-width="1"/>
    <rect x="25" y="25" width="350" height="30" rx="6" fill="var(--surface-2)" stroke="var(--border-strong)" stroke-width="1.2"/>
    <text x="40" y="44" fill="var(--fg)" font-size="10" font-family="monospace">GET /api/products</text>
    <g transform="translate(35, 75)">
      <rect width="140" height="100" rx="8" fill="color-mix(in oklab, var(--success) 8%, var(--surface))" stroke="var(--success)" stroke-width="1.2"/>
      <text x="70" y="25" fill="var(--success)" font-size="11" font-weight="800" text-anchor="middle">HTTP 動詞 (Action)</text>
      <rect x="20" y="40" width="100" height="18" rx="4" fill="var(--surface)" stroke="var(--border)" stroke-width="1"/>
      <text x="70" y="52" fill="var(--accent)" font-size="9" font-weight="800" text-anchor="middle">GET / POST</text>
      <rect x="20" y="65" width="100" height="18" rx="4" fill="var(--surface)" stroke="var(--border)" stroke-width="1"/>
      <text x="70" y="77" fill="var(--muted)" font-size="9" font-weight="800" text-anchor="middle">PUT / DELETE</text>
    </g>
    <path d="M 185 125 H 215" stroke="var(--border-strong)" stroke-width="1.5" stroke-dasharray="3"/>
    <g transform="translate(225, 75)">
      <rect width="140" height="100" rx="8" fill="color-mix(in oklab, var(--accent) 8%, var(--surface))" stroke="var(--accent)" stroke-width="1.2"/>
      <text x="70" y="25" fill="var(--accent-deep)" font-size="11" font-weight="800" text-anchor="middle">資源名詞 (Resource)</text>
      <rect x="20" y="45" width="100" height="36" rx="4" fill="var(--surface)" stroke="var(--border)" stroke-width="1"/>
      <text x="70" y="60" fill="var(--fg)" font-size="10" text-anchor="middle">/products</text>
      <text x="70" y="73" fill="var(--muted)" font-size="8" text-anchor="middle">(複數名詞集合)</text>
    </g>
  </svg>`;
}

/** AOP 概念與橫切關注點 SVG 圖解 */
function svgAopConcept() {
  return `<svg class="concept-svg-illustration" viewBox="0 0 400 220" fill="none" xmlns="http://www.w3.org/2000/svg">
    <rect width="400" height="220" rx="12" fill="var(--surface)" stroke="var(--border)" stroke-width="1"/>
    <g transform="translate(30, 25)"><rect x="0" y="0" width="70" height="120" rx="6" fill="var(--surface-2)" stroke="var(--border-strong)" stroke-width="1.5"/><text x="35" y="60" fill="var(--fg)" font-size="9" font-weight="800" text-anchor="middle">Product</text><text x="35" y="75" fill="var(--fg)" font-size="9" font-weight="800" text-anchor="middle">Service</text></g>
    <g transform="translate(130, 25)"><rect x="0" y="0" width="70" height="120" rx="6" fill="var(--surface-2)" stroke="var(--border-strong)" stroke-width="1.5"/><text x="35" y="60" fill="var(--fg)" font-size="9" font-weight="800" text-anchor="middle">Order</text><text x="35" y="75" fill="var(--fg)" font-size="9" font-weight="800" text-anchor="middle">Service</text></g>
    <g transform="translate(230, 25)"><rect x="0" y="0" width="70" height="120" rx="6" fill="var(--surface-2)" stroke="var(--border-strong)" stroke-width="1.5"/><text x="35" y="60" fill="var(--fg)" font-size="9" font-weight="800" text-anchor="middle">User</text><text x="35" y="75" fill="var(--fg)" font-size="9" font-weight="800" text-anchor="middle">Service</text></g>
    <g class="svg-flow-node" transform="translate(15, 65)"><rect width="300" height="26" rx="6" fill="var(--accent)" opacity="0.15"/><rect width="300" height="26" rx="6" stroke="var(--accent)" stroke-width="1.8"/><text x="150" y="17" fill="var(--accent-deep)" font-size="9" font-weight="800" text-anchor="middle">Aspect 橫切切面：交易控制 / Log / 權限</text></g>
    <text x="165" y="195" fill="var(--muted)" font-size="9.5" text-anchor="middle">AOP (面向切面)：解耦非業務橫切邏輯</text>
  </svg>`;
}

/** Spring Security 閥門 Filter Chain SVG 圖解 */
function svgSecurityChain() {
  const filterItem = (x, label) => `<g class="svg-filter-item" transform="translate(${x}, 70)"><rect width="42" height="80" rx="6" fill="var(--surface)" stroke="var(--border-strong)" stroke-width="1.5"/><line x1="21" y1="15" x2="21" y2="65" stroke="var(--border-strong)" stroke-width="1.5"/><circle class="svg-valve-gate" cx="21" cy="40" r="9" fill="var(--accent)" opacity="0.25"/><circle class="svg-valve-gate" cx="21" cy="40" r="7" stroke="var(--accent)" stroke-width="1.5"/><text x="21" y="88" fill="var(--fg)" font-size="7" text-anchor="middle">${label}</text></g>`;
  return `<svg class="concept-svg-illustration" viewBox="0 0 400 220" fill="none" xmlns="http://www.w3.org/2000/svg">
    <rect width="400" height="220" rx="12" fill="var(--surface)" stroke="var(--border)" stroke-width="1"/>
    <rect x="15" y="90" width="370" height="40" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" stroke-width="1.5"/>
    <text x="25" y="114" fill="var(--muted)" font-size="8">Request</text>
    ${filterItem(120, "CorsFilter")}${filterItem(180, "JwtFilter")}${filterItem(240, "AuthFilter")}
    <g transform="translate(310, 92)"><rect width="55" height="36" rx="6" fill="color-mix(in oklab, var(--success) 10%, var(--surface))" stroke="var(--success)" stroke-width="1.5"/><text x="27.5" y="22" fill="var(--success)" font-size="9" font-weight="800" text-anchor="middle">Controller</text></g>
    <path class="svg-flow-arrow" d="M 65 110 H 310" stroke="var(--accent)" stroke-width="2" stroke-dasharray="6"/>
  </svg>`;
}

/** SSE Token 串流傳輸 SVG 圖解 */
function svgSseStream() {
  return `<svg class="concept-svg-illustration" viewBox="0 0 400 220" fill="none" xmlns="http://www.w3.org/2000/svg">
    <rect width="400" height="220" rx="12" fill="var(--surface)" stroke="var(--border)" stroke-width="1"/>
    <g transform="translate(25, 80)"><rect width="85" height="60" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" stroke-width="1.5"/><text x="42.5" y="28" fill="var(--fg)" font-size="10" font-weight="800" text-anchor="middle">Spring AI</text><text x="42.5" y="44" fill="var(--muted)" font-size="8.5" text-anchor="middle">(後端 Server)</text></g>
    <g transform="translate(290, 80)"><rect width="85" height="60" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" stroke-width="1.5"/><text x="42.5" y="28" fill="var(--fg)" font-size="10" font-weight="800" text-anchor="middle">React APP</text><text x="42.5" y="44" fill="var(--muted)" font-size="8.5" text-anchor="middle">(前端 Client)</text></g>
    <rect x="110" y="95" width="180" height="30" rx="4" fill="none" stroke="var(--border-strong)" stroke-width="1.5" stroke-dasharray="4"/>
    <text x="200" y="145" fill="var(--accent-deep)" font-size="9" font-weight="800" text-anchor="middle">Server-Sent Events (SSE) 串流</text>
    <g transform="translate(110, 95)"><circle class="svg-sse-bubble" cx="15" cy="15" r="7" fill="var(--accent)" opacity="0.85"/><circle class="svg-sse-bubble-2" cx="15" cy="15" r="6" fill="var(--accent)" opacity="0.85"/><circle class="svg-sse-bubble-3" cx="15" cy="15" r="5" fill="var(--accent)" opacity="0.85"/></g>
  </svg>`;
}

/** 開發環境準備與 AI Agent 協作圖解 */
function svgEnvironmentPrep() {
  const toolBox = (x, y, title, sub) => `<g class="svg-flow-node" transform="translate(${x}, ${y})"><rect width="135" height="50" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" stroke-width="1.5"/><text x="67.5" y="24" fill="var(--fg)" font-size="10.5" font-weight="800" text-anchor="middle">${title}</text><text x="67.5" y="38" fill="var(--muted)" font-size="8.5" text-anchor="middle">${sub}</text></g>`;
  return `<svg class="concept-svg-illustration" viewBox="0 0 500 280" fill="none" xmlns="http://www.w3.org/2000/svg">
    <rect width="500" height="280" rx="16" fill="var(--surface)" stroke="var(--border)" stroke-width="1.2"/>
    ${toolBox(25, 30, "VS Code", "(程式編輯與除錯)")}
    ${toolBox(182, 30, "JDK 21 (LTS)", "(Java 執行環境)")}
    ${toolBox(340, 30, "Maven 3.9+", "(專案建置與依賴管理)")}
    ${toolBox(25, 105, "Git", "(版本控制與代碼管理)")}
    ${toolBox(182, 105, "PowerShell 7+", "(跨平台命令行終端)")}
    ${toolBox(340, 105, "Docker / PostgreSQL", "(資料庫與容器環境)")}
    <g class="svg-agent-brain" transform="translate(250, 220)"><circle cx="0" cy="0" r="32" fill="var(--accent)" opacity="0.12"/><circle cx="0" cy="0" r="26" stroke="var(--accent)" stroke-width="2" stroke-dasharray="5"/><text x="0" y="4" fill="var(--accent-deep)" font-size="10.5" font-weight="800" text-anchor="middle">AI Agent</text></g>
    <path d="M 92.5 155 L 218 220" stroke="var(--border-strong)" stroke-width="1.2" stroke-dasharray="3"/>
    <path d="M 250 155 V 188" stroke="var(--border-strong)" stroke-width="1.2" stroke-dasharray="3"/>
    <path d="M 407.5 155 L 282 220" stroke="var(--border-strong)" stroke-width="1.2" stroke-dasharray="3"/>
  </svg>`;
}

/** Flyway 版本控制遷移軌跡圖解 */
function svgFlywayStrategy() {
  return `<svg class="concept-svg-illustration" viewBox="0 0 400 220" fill="none" xmlns="http://www.w3.org/2000/svg">
    <rect width="400" height="220" rx="12" fill="var(--surface)" stroke="var(--border)" stroke-width="1"/>
    <g class="svg-flyway-step" transform="translate(35, 75)"><circle class="svg-flyway-v" cx="20" cy="20" r="20" fill="var(--surface-2)" stroke="var(--border-strong)" stroke-width="1.5"/><text x="20" y="24" fill="var(--fg)" font-size="10" font-weight="800" text-anchor="middle">V1</text><text x="20" y="55" fill="var(--muted)" font-size="8" text-anchor="middle">建立 Schema</text></g>
    <path d="M 75 95 H 135" stroke="var(--border-strong)" stroke-width="1.5"/>
    <g class="svg-flyway-step" transform="translate(145, 75)"><circle class="svg-flyway-v" cx="20" cy="20" r="20" fill="var(--surface-2)" stroke="var(--border-strong)" stroke-width="1.5"/><text x="20" y="24" fill="var(--fg)" font-size="10" font-weight="800" text-anchor="middle">V2</text><text x="20" y="55" fill="var(--muted)" font-size="8" text-anchor="middle">新增 Audit</text></g>
    <path d="M 185 95 H 245" stroke="var(--accent)" stroke-width="2" stroke-dasharray="4"/>
    <path class="svg-flow-arrow" d="M 185 95 H 245" stroke="var(--accent)" stroke-width="2"/>
    <g class="svg-flyway-step" transform="translate(255, 75)"><circle class="svg-flyway-v" cx="20" cy="20" r="22" fill="var(--accent)" opacity="0.1"/><circle class="svg-flyway-v" cx="20" cy="20" r="20" stroke="var(--accent)" stroke-width="2.5"/><text x="20" y="24" fill="var(--accent-deep)" font-size="11" font-weight="800" text-anchor="middle">V3</text><text x="20" y="55" fill="var(--accent-deep)" font-size="8.5" font-weight="800" text-anchor="middle">新增 Vector</text></g>
  </svg>`;
}

/** JPA ORM 映射機制圖解 */
function svgJpaMapping() {
  return `<svg class="concept-svg-illustration" viewBox="0 0 400 220" fill="none" xmlns="http://www.w3.org/2000/svg">
    <rect width="400" height="220" rx="12" fill="var(--surface)" stroke="var(--border)" stroke-width="1"/>
    <g transform="translate(25, 40)"><rect width="130" height="130" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" stroke-width="1.5"/><text x="65" y="25" fill="var(--fg)" font-size="10" font-weight="800" text-anchor="middle">@Entity Product</text><line x1="15" y1="35" x2="115" y2="35" stroke="var(--border-strong)" stroke-width="1"/><text x="20" y="55" fill="var(--muted)" font-size="9">private Long id;</text><text x="20" y="75" fill="var(--muted)" font-size="9">private String name;</text><text x="20" y="95" fill="var(--muted)" font-size="9">private Double price;</text></g>
    <g class="svg-flow-node" transform="translate(175, 95)"><polygon points="0,5 20,5 20,0 35,10 20,20 20,15 0,15" fill="var(--accent)"/><text x="17" y="-10" fill="var(--accent-deep)" font-size="9" font-weight="800" text-anchor="middle">JPA Mapping</text></g>
    <g transform="translate(245, 40)"><rect width="130" height="130" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" stroke-width="1.5"/><text x="65" y="25" fill="var(--fg)" font-size="10" font-weight="800" text-anchor="middle">Table: products</text><line x1="15" y1="35" x2="115" y2="35" stroke="var(--border-strong)" stroke-width="1"/><text x="20" y="55" fill="var(--muted)" font-size="9">id : bigint (PK)</text><text x="20" y="75" fill="var(--muted)" font-size="9">name : varchar</text><text x="20" y="95" fill="var(--muted)" font-size="9">price : float8</text></g>
  </svg>`;
}

/** Vite Dev Server Proxy 代理流向圖解 */
function svgViteProxy() {
  return `<svg class="concept-svg-illustration" viewBox="0 0 400 220" fill="none" xmlns="http://www.w3.org/2000/svg">
    <rect width="400" height="220" rx="12" fill="var(--surface)" stroke="var(--border)" stroke-width="1"/>
    <g transform="translate(20, 80)"><rect width="80" height="50" rx="6" fill="var(--surface-2)" stroke="var(--border-strong)" stroke-width="1.5"/><text x="40" y="24" fill="var(--fg)" font-size="9" font-weight="800" text-anchor="middle">React 前端</text><text x="40" y="38" fill="var(--muted)" font-size="8" text-anchor="middle">localhost:5173</text></g>
    <g class="svg-flow-node" transform="translate(145, 60)"><rect width="110" height="90" rx="8" fill="var(--accent)" opacity="0.1"/><rect width="110" height="90" rx="8" stroke="var(--accent)" stroke-width="2"/><text x="55" y="35" fill="var(--accent-deep)" font-size="10" font-weight="800" text-anchor="middle">Vite Dev Server</text><text x="55" y="55" fill="var(--accent-deep)" font-size="10" font-weight="800" text-anchor="middle">API Proxy</text><text x="55" y="73" fill="var(--muted)" font-size="8" text-anchor="middle">(避開 CORS 錯誤)</text></g>
    <g transform="translate(295, 80)"><rect width="85" height="50" rx="6" fill="var(--surface-2)" stroke="var(--border-strong)" stroke-width="1.5"/><text x="42.5" y="24" fill="var(--fg)" font-size="9" font-weight="800" text-anchor="middle">Spring 後端</text><text x="42.5" y="38" fill="var(--muted)" font-size="8" text-anchor="middle">localhost:8080</text></g>
    <path class="svg-flow-arrow" d="M 100 105 H 145" stroke="var(--accent)" stroke-width="1.5"/>
    <path class="svg-flow-arrow" d="M 255 105 H 295" stroke="var(--accent)" stroke-width="1.5"/>
  </svg>`;
}

/** GOAP Agent Blackboard 決策圖解（選修附錄用） */
function svgAgentGoap() {
  return `<svg class="concept-svg-illustration" viewBox="0 0 500 240" fill="none" xmlns="http://www.w3.org/2000/svg">
    <rect width="500" height="240" rx="16" fill="var(--surface)" stroke="var(--border)" stroke-width="1.2"/>
    <g transform="translate(20, 30)"><rect width="110" height="180" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" stroke-width="1.5"/><text x="55" y="24" fill="var(--fg)" font-size="11" font-weight="800" text-anchor="middle">Blackboard</text><line x1="10" y1="35" x2="100" y2="35" stroke="var(--border-strong)" stroke-width="1"/><text x="15" y="55" fill="var(--muted)" font-size="8.5">clientType: VIP</text><text x="15" y="75" fill="var(--muted)" font-size="8.5">riskScore: 85</text><text x="15" y="95" fill="var(--accent-deep)" font-size="8.5" font-weight="700">highRisk: true</text><text x="15" y="115" fill="var(--muted)" font-size="8.5">hasRAG: true</text><text x="15" y="135" fill="var(--muted)" font-size="8.5">goal: APPROVED</text><text x="15" y="155" fill="var(--muted)" font-size="8.5">step: REVIEW</text></g>
    <path d="M 130 120 H 160" stroke="var(--border-strong)" stroke-width="1.5" stroke-dasharray="3"/>
    <g class="svg-agent-brain" transform="translate(250, 60)"><circle cx="0" cy="0" r="30" fill="var(--accent)" opacity="0.12"/><circle cx="0" cy="0" r="25" stroke="var(--accent)" stroke-width="2" stroke-dasharray="4"/><text x="0" y="2" fill="var(--accent-deep)" font-size="10" font-weight="800" text-anchor="middle">GOAP</text><text x="0" y="13" fill="var(--accent-deep)" font-size="9" font-weight="800" text-anchor="middle">Planner</text></g>
    <g transform="translate(160, 110)"><rect width="180" height="100" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" stroke-width="1.5"/><text x="90" y="18" fill="var(--fg)" font-size="10" font-weight="800" text-anchor="middle">Action Pool (行為庫)</text>
      <g transform="translate(12, 28)"><rect width="72" height="26" rx="4" fill="var(--surface)" stroke="var(--border)" stroke-width="1"/><text x="36" y="16" fill="var(--muted)" font-size="8.5" text-anchor="middle">A1: Fetch</text></g>
      <g transform="translate(96, 28)"><rect width="72" height="26" rx="4" fill="var(--surface)" stroke="var(--border)" stroke-width="1"/><text x="36" y="16" fill="var(--muted)" font-size="8.5" text-anchor="middle">A2: RAG</text></g>
      <g transform="translate(12, 60)"><rect width="72" height="26" rx="4" fill="var(--surface)" stroke="var(--border)" stroke-width="1"/><text x="36" y="16" fill="var(--muted)" font-size="8.5" text-anchor="middle">A3: Draft</text></g>
      <g transform="translate(96, 60)"><rect width="72" height="26" rx="4" fill="var(--accent)" opacity="0.1"/><rect width="72" height="26" rx="4" stroke="var(--accent)" stroke-width="1.2"/><text x="36" y="16" fill="var(--accent-deep)" font-size="8.5" font-weight="700" text-anchor="middle">A4: Review</text></g>
    </g>
    <path d="M 250 90 V 110" stroke="var(--accent)" stroke-width="1.5" marker-end="url(#arrow-down)"/>
    <g transform="translate(370, 30)" opacity="0.4"><rect width="110" height="50" rx="8" fill="var(--surface-2)" stroke="var(--border)" stroke-width="1"/><text x="55" y="24" fill="var(--muted)" font-size="9.5" font-weight="800" text-anchor="middle">直接產出建議</text><text x="55" y="38" fill="var(--muted)" font-size="8" text-anchor="middle">(Precondition 阻擋)</text></g>
    <g class="svg-flow-node" transform="translate(370, 120)"><rect width="110" height="70" rx="8" fill="color-mix(in oklab, var(--accent) 8%, var(--surface))" stroke="var(--accent)" stroke-width="2"/><text x="55" y="28" fill="var(--accent-deep)" font-size="10.5" font-weight="800" text-anchor="middle">主管審核</text><text x="55" y="44" fill="var(--accent-deep)" font-size="8.5" font-weight="800" text-anchor="middle">(Replanning)</text><text x="55" y="58" fill="var(--muted)" font-size="8" text-anchor="middle">A* 重新尋路成功</text></g>
    <path d="M 340 160 H 370" stroke="var(--accent)" stroke-width="2" class="svg-flow-arrow"/>
    <path d="M 280 60 H 370" stroke="var(--border-strong)" stroke-width="1.2" stroke-dasharray="3"/>
    <defs><marker id="arrow-down" viewBox="0 0 10 10" refX="5" refY="5" markerWidth="4" markerHeight="4" orient="auto"><path d="M 0 0 L 5 5 L 0 10 z" fill="var(--accent)"/></marker></defs>
  </svg>`;
}

/** RAG 檢索增強生成工作流圖解 */
function svgRAGFlow() {
  return `<svg class="concept-svg-illustration" viewBox="0 0 500 240" fill="none" xmlns="http://www.w3.org/2000/svg">
    <rect width="500" height="240" rx="16" fill="var(--surface)" stroke="var(--border)" stroke-width="1.2"/>
    <g transform="translate(15, 80)"><circle cx="25" cy="25" r="20" fill="var(--surface-2)" stroke="var(--border-strong)" stroke-width="1.5"/><text x="25" y="29" fill="var(--fg)" font-size="14" text-anchor="middle">&#x1F464;</text><text x="25" y="60" fill="var(--fg)" font-size="8.5" font-weight="800" text-anchor="middle">User</text><rect x="-10" y="70" width="75" height="30" rx="4" fill="var(--surface-2)" stroke="var(--border)" stroke-width="1"/><text x="27.5" y="88" fill="var(--muted)" font-size="7.5" text-anchor="middle">"退貨政策為何？"</text></g>
    <path d="M 85 110 H 135" stroke="var(--accent)" stroke-width="1.5" class="svg-flow-arrow"/>
    <g transform="translate(135, 30)"><rect width="210" height="70" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" stroke-width="1.5"/><text x="105" y="18" fill="var(--fg)" font-size="10" font-weight="800" text-anchor="middle">pgvector 相似度檢索</text><line x1="15" y1="28" x2="195" y2="28" stroke="var(--border-strong)" stroke-width="1"/><text x="20" y="44" fill="var(--accent-deep)" font-size="8" font-weight="700">Similarity Search (topK=3)</text><text x="20" y="58" fill="var(--muted)" font-size="7.5">比對 Cosine Distance &lt;= 0.15</text></g>
    <path d="M 240 100 V 130" stroke="var(--border-strong)" stroke-width="1.2" stroke-dasharray="3"/>
    <g transform="translate(145, 130)"><rect width="190" height="75" rx="6" fill="color-mix(in oklab, var(--success) 8%, var(--surface))" stroke="var(--success)" stroke-width="1.5"/><text x="95" y="18" fill="var(--success)" font-size="9.5" font-weight="800" text-anchor="middle">Prompt 上下文合併</text><line x1="10" y1="26" x2="180" y2="26" stroke="var(--success)" stroke-width="1" opacity="0.3"/><text x="15" y="40" fill="var(--fg)" font-size="7.5">Context: "退貨政策：14天內憑發票..."</text><text x="15" y="54" fill="var(--fg)" font-size="7.5">Question: "退貨政策為何？"</text><text x="15" y="68" fill="var(--muted)" font-size="7.5" font-style="italic">(開卷考試防幻覺)</text></g>
    <path d="M 335 167 H 375" stroke="var(--accent)" stroke-width="1.5" class="svg-flow-arrow"/>
    <g class="svg-agent-brain" transform="translate(425, 110)"><circle cx="0" cy="0" r="30" fill="var(--accent)" opacity="0.12"/><circle cx="0" cy="0" r="25" stroke="var(--accent)" stroke-width="2" stroke-dasharray="4"/><text x="0" y="4" fill="var(--accent-deep)" font-size="10.5" font-weight="800" text-anchor="middle">LLM</text></g>
    <path d="M 425 140 L 335 167" stroke="var(--border-strong)" stroke-width="1" stroke-dasharray="3"/>
    <path d="M 345 65 L 425 80" stroke="var(--border-strong)" stroke-width="1" stroke-dasharray="3"/>
    <path d="M 425 140 C 425 220, 25 220, 25 190" stroke="var(--success)" stroke-width="1.8" stroke-dasharray="5"/>
  </svg>`;
}

/** RAG ETL 資料清洗與寫入 Pipeline 圖解 */
function svgEtlPipeline() {
  return `<svg class="concept-svg-illustration" viewBox="0 0 500 240" fill="none" xmlns="http://www.w3.org/2000/svg">
    <rect width="500" height="240" rx="16" fill="var(--surface)" stroke="var(--border)" stroke-width="1.2"/>
    <g class="svg-flow-node" transform="translate(20, 85)"><rect width="120" height="65" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" stroke-width="1.5"/><text x="60" y="20" fill="var(--fg)" font-size="10.5" font-weight="800" text-anchor="middle">1. Extract (提取)</text><text x="60" y="38" fill="var(--muted)" font-size="8.5" text-anchor="middle">TextReader 讀取</text><text x="60" y="52" fill="var(--muted)" font-size="8" text-anchor="middle">txt / pdf / docx</text></g>
    <path d="M 140 117 H 180" stroke="var(--border-strong)" stroke-width="1.5" marker-end="url(#arrow-right)"/>
    <g class="svg-flow-node" transform="translate(180, 85)"><rect width="130" height="65" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" stroke-width="1.5"/><text x="65" y="20" fill="var(--fg)" font-size="10.5" font-weight="800" text-anchor="middle">2. Transform (切分)</text><text x="65" y="38" fill="var(--accent-deep)" font-size="8.5" font-weight="700" text-anchor="middle">TokenTextSplitter</text><text x="65" y="52" fill="var(--muted)" font-size="8" text-anchor="middle">800 chars / 100 overlap</text></g>
    <path d="M 310 117 H 350" stroke="var(--border-strong)" stroke-width="1.5" marker-end="url(#arrow-right)"/>
    <g class="svg-agent-brain" transform="translate(245, 30)"><circle cx="0" cy="0" r="20" fill="var(--accent)" opacity="0.12"/><circle cx="0" cy="0" r="16" stroke="var(--accent)" stroke-width="1.5" stroke-dasharray="3"/><text x="0" y="3.5" fill="var(--accent-deep)" font-size="8.5" font-weight="800" text-anchor="middle">Embedding</text></g>
    <path d="M 245 85 V 50" stroke="var(--border-strong)" stroke-width="1" stroke-dasharray="3"/>
    <path d="M 261 30 C 310 30, 360 65, 390 85" stroke="var(--accent)" stroke-width="1.2" stroke-dasharray="3"/>
    <g class="svg-flow-node" transform="translate(350, 85)"><rect width="130" height="65" rx="8" fill="color-mix(in oklab, var(--success) 8%, var(--surface))" stroke="var(--success)" stroke-width="2"/><text x="65" y="20" fill="var(--success)" font-size="10.5" font-weight="800" text-anchor="middle">3. Load (載入庫)</text><text x="65" y="38" fill="var(--fg)" font-size="8.5" font-weight="800" text-anchor="middle">pgvector 寫入</text><text x="65" y="52" fill="var(--muted)" font-size="8" text-anchor="middle">Vector: 1536 Dimensions</text></g>
    <defs><marker id="arrow-right" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="4" markerHeight="4" orient="auto"><path d="M 0 0 L 5 5 L 0 10 z" fill="var(--border-strong)"/></marker></defs>
  </svg>`;
}

/**
 * 依據概念標題動態匹配並渲染精美 inline SVG 圖解。
 * @param {string} heading 觀念的標題文字
 * @returns {string} SVG HTML 或空字串
 */
function conceptVisual(heading) {
  if (!heading) return "";
  /** 觀念標題 → PNG 檔名對應表（原行內 SVG 已轉為 PNG，可陸續替換為新圖） */
  const conceptImageMap = {
    /* ── 既有技術概念圖 ── */
    "環境準備重點": "concept-environment-prep.png",
    "Spring MVC 的核心：請求如何流動": "concept-spring-mvc-flow.png",
    "Spring MVC 核心架構": "concept-spring-mvc-flow.png",
    "什麼是 REST API": "concept-rest-api.png",
    "JPA 解決了什麼問題": "concept-jpa-mapping.png",
    "JPA 核心概念與 Entity 設計": "concept-jpa-mapping.png",
    "Flyway 的角色": "concept-flyway-strategy.png",
    "AOP 概念圖解": "concept-aop.png",
    "AOP 解決了什麼問題": "concept-aop.png",
    "後端安全設定範例 (SecurityConfig.java)": "concept-security-chain.png",
    "安全防護重點": "concept-security-chain.png",
    "JWT 是什麼？三段式結構與無狀態認證": "concept-jwt-structure.png",
    "開發端代理與後端 API 串接 (Vite Proxy)": "concept-vite-proxy.png",
    "串流輸出為什麼重要": "concept-sse-stream.png",
    "為什麼選擇 SSE (Server-Sent Events)": "concept-sse-stream.png",
    "GOAP規劃": "concept-agent-goap.png",
    "Embabel 智慧 Agent、GOAP 演算法與 Blackboard 機制": "concept-agent-goap.png",
    "RAG 的基本想法": "rag_flow.png",
    "RAG 核心概念": "rag_flow.png",
    "ETL 三步驟：文件到向量庫": "etl_pipeline.png",

    /* ── AI CRM 情境圖（每章 1 張） ── */
    "為什麼選 CRM 作為實作題目": "crm-u1-why-crm.png",
    "CRM Domain Model 設計思維": "crm-u2-domain-model.png",
    "CRM 資料模型如何對應 JPA Entity": "crm-u3-data-model.png",
    "CRM 角色與權限模型": "crm-u4-security.png",
    "CRM 工作台 UI 設計思維": "crm-u5-frontend.png",
    "AI CRM 助理的商業價值": "crm-u6-ai-value.png",
    "CRM 知識庫設計：哪些文件該向量化": "crm-u7-knowledge.png",

    /* ── 新增技術資訊圖表 ── */
    "輸入驗證：為什麼不能信任前端傳來的資料": "concept-bean-validation.png",
    "Controller / Service 怎麼分工": "concept-controller-service.png",
    "為什麼資料庫要容器化": "concept-docker-postgres.png",
    "@Transactional 核心規則": "concept-transactional.png",
    "為什麼需要動態查詢": "concept-specification.png",
    "為什麼需要 API 文件": "concept-openapi.png",
    "統一錯誤回應設計": "concept-global-exception.png",
    "ChatClient 核心概念": "concept-chatclient.png",
    "工具呼叫核心概念": "concept-tool-calling.png",
    "MCP 核心概念": "concept-mcp.png",
    "為什麼需要對話歷史 RAG": "concept-conversation-memory.png",
  };
  const filename = conceptImageMap[heading.trim()];
  if (!filename) return "";
  return `<img src="assets/illustrations/${filename}" class="concept-svg-illustration" alt="${heading}"/>`;
}

/* ──────────────────────────────────────────────
   4. 元件渲染函式（回傳 HTML 字串）
   ────────────────────────────────────────────── */

/** 首頁全幅 Hero */
function renderHero(meta) {
  const techPills = ["Spring Boot 4", "React 19 + Vite", "Spring AI 2.0", "pgvector RAG", "MCP", "Demo Day"];
  return `<div class="hero-band"><section class="hero" id="top">
    <div class="hero-media"><img src="assets/illustrations/cover.png" alt="AI CRM 課程全幅主視覺"/></div>
    <div class="hero-overlay"><div class="hero-copy">
      <span class="eyebrow">${esc(meta.program)}</span>
      <h2>${esc(meta.title)}</h2>
      <p>用同一套 AI CRM 專案，把 Spring MVC、JPA、安全性、React 19、Spring AI、RAG 與 MCP 串成可驗證的企業級全端學習路線。</p>
      <div class="pill-row">${techPills.map((t) => `<span class="pill">${esc(t)}</span>`).join("")}</div>
      <div class="hero-actions"><a class="primary-button" href="#overview">開始閱讀課程</a><a class="secondary-button" href="#day1">查看 Day 1</a></div>
    </div>
    <aside class="hero-panel"><h3>完成後你會得到</h3><ul>${meta.objectives.slice(0, 5).map((o) => `<li>${esc(o)}</li>`).join("")}</ul></aside>
    </div>
  </section></div>`;
}

/** 課程總覽區塊 */
function renderOverview(meta) {
  const hours = meta.days.reduce((s, d) => s + d.hours, 0);
  return `<section class="section glass-card" id="overview">
    <div class="section-header"><h3>課程總覽</h3><p>這門課不是零散技術拼盤，而是讓每個單元都接續前一個產出，最後完成可登入、可查資料、可引用知識庫、可追蹤 agent 決策的 AI CRM。</p></div>
    <div class="overview-lead"><h4>四天的主線是一套產品逐步長出來的路徑。</h4><p>前半段先把 Spring Boot、REST、JPA、Flyway、Security 與 React 工作台打穩；後半段把 Spring AI、SSE、tool calling、RAG 與 MCP 接回同一套 CRM domain，最後整合為結訓專案。每個章節都有明確產出與驗收方式。</p>
      <div class="pill-row overview-pills">${meta.days.map((d) => `<span class="pill">${esc(d.date)} / ${d.hours}h</span>`).join("")}</div>
    </div>
    <div class="stat-grid">
      <article class="stat-card"><small>課程形式</small><strong>${esc(meta.format)}</strong><p>${esc(meta.location)}</p></article>
      <article class="stat-card"><small>授課時數</small><strong>${hours} 小時</strong><p>可拆成四天工作坊或線上章節節奏。</p></article>
      <article class="stat-card"><small>結訓專案</small><strong>AI CRM 智慧業務助理</strong><p>從登入、客戶資料、AI 聊天到 agent trace 完整跑通。</p></article>
      <article class="stat-card"><small>驗收方式</small><strong>本機 verify + 作業提交</strong><p>${esc(meta.completion[0])}</p></article>
    </div>
  </section>`;
}

/** 功能藍圖區塊 */
function renderFeatureRoadmap(course) {
  const allUnits = getAllUnits(course);
  const cards = allUnits.map(({ dayMeta, day, unit }, index) => {
    const features = (unit.features || []).map((f) => `<li>${featureCheckIcon()}${esc(f)}</li>`).join("");
    return `<a class="roadmap-card" href="#${day.id}-${unit.id}">
      <div class="roadmap-card-head"><span class="roadmap-glyph">${unitGlyph(index)}</span><div class="roadmap-card-title"><span class="roadmap-kicker">Unit ${index + 1} · ${esc(dayMeta.date)}</span><strong>${esc(unit.title)}</strong></div></div>
      <ul class="roadmap-feature-list">${features}</ul>
    </a>`;
  }).join("");
  return `<section class="section glass-card" id="feature-roadmap"><div class="section-header"><h3>AI CRM 功能藍圖</h3><p>八個章節像一條產品生產線，每一站都為同一套 AI CRM 補上一塊可運作的功能。點任一卡片即可跳到該章節。</p></div><div class="feature-roadmap-grid">${cards}</div></section>`;
}

/** 課程最後補充：superpowers 技能組，依階段分組列出每個技能的用途 */
function renderSuperpowers(sp) {
  if (!sp) return "";
  // 將每個階段渲染成一個子區塊，內含該階段的技能卡片
  const groups = (sp.groups || []).map((group) => {
    const cards = (group.skills || []).map((skill) =>
      `<article class="summary-card sp-skill-card"><code class="sp-skill-name">${esc(skill.name)}</code><strong>${esc(skill.zh)}</strong><p>${esc(skill.purpose)}</p></article>`
    ).join("");
    return `<div class="sp-group"><h4 class="sp-phase">${esc(group.phase)}</h4><div class="summary-grid">${cards}</div></div>`;
  }).join("");
  return `<section class="section glass-card" id="superpowers"><div class="section-header"><h3>${esc(sp.title)}</h3><p>${esc(sp.intro)}</p></div>${groups}</section>`;
}

/** 貫穿全程的 AI CRM 情境 */
function renderSharedCase(sharedCase) {
  if (!sharedCase) return "";
  /** 品牌 ID 與企業示意圖檔名的對應表 */
  const brandImageMap = {
    brand1: "brand-apim.png",
    brand2: "brand-globalmart.png",
    brand3: "brand-apexfin.png"
  };
  const brands = sharedCase.brands.map((brand) => {
    const rows = brand.rows.map(([k, v]) => `<tr><td>${esc(k)}</td><td>${inlineMarkdown(v)}</td></tr>`).join("");
    const brandImg = brandImageMap[brand.id] || "office.png";
    return `<article class="brand-card brand-card-split"><div class="brand-card-info"><small>${esc(brand.type)}</small><h4>${esc(brand.name)}</h4><table class="data-table"><tbody>${rows}</tbody></table></div><div class="brand-card-media"><img src="assets/illustrations/${brandImg}" alt="${esc(brand.name)} 企業示意圖"/></div></article>`;
  }).join("");
  const roles = sharedCase.roles.map(([name, brand, role, desc]) =>
    `<article class="summary-card"><small>${esc(brand)}</small><strong>${esc(name)} / ${esc(role)}</strong><p>${esc(desc)}</p></article>`
  ).join("");
  return `<section class="section glass-card" id="shared-case"><div class="section-header"><h3>貫穿全程的 AI CRM 情境</h3><p>${esc(sharedCase.intro)}</p></div><div class="brand-grid">${brands}</div><div class="summary-grid">${roles}</div></section>`;
}

/** 單一核心觀念內容卡片 */
function renderConcept(concept) {
  const visual = conceptVisual(concept.heading);
  let bodyHtml = "";
  if (concept.body) bodyHtml = `<div class="concept-body markdown-body">${parseMarkdown(concept.body)}</div>`;

  let content;
  if (visual) {
    content = `<div class="concept-layout"><div class="concept-visual-col">${visual}</div><div class="concept-text-col">${bodyHtml}</div></div>`;
  } else {
    content = bodyHtml;
  }

  let kvHtml = "";
  if (concept.list?.length) {
    kvHtml = `<div class="kv">${concept.list.map(([k, v]) => `<div class="kv-row"><div class="kv-key">${esc(k)}</div><div class="kv-value">${inlineMarkdown(v)}</div></div>`).join("")}</div>`;
  }

  let tableHtml = "";
  if (concept.table) {
    const head = concept.table.head.map((h) => `<th>${inlineMarkdown(h)}</th>`).join("");
    const rows = concept.table.rows.map((row) => `<tr>${row.map((c) => `<td>${inlineMarkdown(c)}</td>`).join("")}</tr>`).join("");
    tableHtml = `<table class="data-table"><thead><tr>${head}</tr></thead><tbody>${rows}</tbody></table>`;
  }

  const noteHtml = concept.note ? `<div class="note">${inlineMarkdown(concept.note)}</div>` : "";

  return `<article class="concept">${concept.heading ? `<h5 class="concept-heading">${esc(concept.heading)}</h5>` : ""}${content}${kvHtml}${tableHtml}${noteHtml}</article>`;
}

/**
 * 判斷群組名稱是否為 AI CRM 相關
 * @param {string} groupName - 群組名稱
 * @returns {boolean}
 */
function isCrmGroup(groupName) {
  return groupName.includes("CRM");
}

/**
 * 渲染概念 Tab 列的共用函式
 * @param {string} unitId - 單元 ID
 * @param {string[]} groupOrder - 群組名稱陣列
 * @param {object} groupMap - 群組名稱 → 概念陣列
 * @param {string} stateKey - 用於 conceptTabState 的鍵名（區分技術/CRM Tab 狀態）
 * @returns {string} HTML 字串
 */
function renderConceptTabPanel(unitId, groupOrder, groupMap, stateKey) {
  if (!groupOrder.length) return "";

  const activeGroupIdx = conceptTabState[stateKey] || 0;
  const safeIdx = Math.min(activeGroupIdx, groupOrder.length - 1);

  /** 群組 Tab 按鈕列 */
  const tabs = groupOrder.map((groupName, i) =>
    `<button class="concept-tab-btn${safeIdx === i ? " active" : ""}" type="button" data-action="concept-tab" data-unit="${esc(stateKey)}" data-index="${i}">${esc(groupName)}</button>`
  ).join("");

  /** 步進器圓點 */
  const dots = groupOrder.map((_, i) =>
    `<span class="concept-dot${safeIdx === i ? " active" : ""}" data-action="concept-tab" data-unit="${esc(stateKey)}" data-index="${i}" role="button" aria-label="跳至群組 ${i + 1}" title="跳至群組 ${i + 1}"></span>`
  ).join("");

  /** 選中群組內的所有概念 */
  const activeGroupName = groupOrder[safeIdx];
  const groupConcepts = groupMap[activeGroupName] || [];
  const conceptsHtml = groupConcepts.map((concept) => renderConcept(concept)).join("");

  return `
      <div class="concept-tabs-nav-wrapper">
        <button class="concept-tabs-nav-arrow" type="button" data-action="concept-prev" data-unit="${esc(stateKey)}"${safeIdx === 0 ? " disabled" : ""} title="上一群組" aria-label="上一群組">&larr;</button>
        <div class="concept-tabs">${tabs}</div>
        <button class="concept-tabs-nav-arrow" type="button" data-action="concept-next" data-unit="${esc(stateKey)}"${safeIdx === groupOrder.length - 1 ? " disabled" : ""} title="下一群組" aria-label="下一群組">&rarr;</button>
      </div>
      <div class="concept-list">${conceptsHtml}</div>
      <div class="concept-nav-footer">
        <button class="concept-nav-btn" type="button" data-action="concept-prev" data-unit="${esc(stateKey)}"${safeIdx === 0 ? " disabled" : ""}>&larr; 上一群組</button>
        <div class="concept-dots">${dots}</div>
        <button class="concept-nav-btn" type="button" data-action="concept-next" data-unit="${esc(stateKey)}"${safeIdx === groupOrder.length - 1 ? " disabled" : ""}>下一群組 &rarr;</button>
      </div>`;
}

/** 觀念手風琴（分為技術概念與 AI CRM 兩個獨立區塊） */
function renderConceptAccordion(unit) {
  if (!unit.concepts?.length) return "";

  /**
   * 依 group 欄位分組，同時區分技術群組與 CRM 群組
   */
  const techGroupOrder = [];
  const techGroupMap = {};
  const crmGroupOrder = [];
  const crmGroupMap = {};

  for (const concept of unit.concepts) {
    const groupName = concept.group || concept.heading || "其他";
    if (isCrmGroup(groupName)) {
      if (!crmGroupMap[groupName]) {
        crmGroupMap[groupName] = [];
        crmGroupOrder.push(groupName);
      }
      crmGroupMap[groupName].push(concept);
    } else {
      if (!techGroupMap[groupName]) {
        techGroupMap[groupName] = [];
        techGroupOrder.push(groupName);
      }
      techGroupMap[groupName].push(concept);
    }
  }

  /** 技術概念 accordion（先講） */
  let techHtml = "";
  if (techGroupOrder.length) {
    const techPanel = renderConceptTabPanel(unit.id, techGroupOrder, techGroupMap, unit.id);
    techHtml = `<details class="accordion-item"${accordionOpen ? " open" : ""}>
    <summary class="accordion-summary">核心觀念與工程判斷</summary>
    <div class="accordion-body">${techPanel}
    </div>
  </details>`;
  }

  /** AI CRM 情境 accordion（後講） */
  let crmHtml = "";
  if (crmGroupOrder.length) {
    const crmStateKey = unit.id + "__crm";
    const crmPanel = renderConceptTabPanel(unit.id, crmGroupOrder, crmGroupMap, crmStateKey);
    crmHtml = `<details class="accordion-item"${accordionOpen ? " open" : ""}>
    <summary class="accordion-summary">🏢 AI CRM 情境應用</summary>
    <div class="accordion-body">${crmPanel}
    </div>
  </details>`;
  }

  return techHtml + crmHtml;
}

/** 可複製的 AI 協作提示詞；kind 控制徽章樣式（build/verify/fix） */
function renderPromptBox(title, note, text, key, kind) {
  const cls = kind === "verify" ? " is-verify" : (kind === "fix" ? " is-fix" : "");
  return `<div class="prompt-box${cls}">
    <div class="prompt-head"><div><strong>${esc(title)}</strong>${note ? `<span>${esc(note)}</span>` : ""}</div>
      <button class="prompt-copy-btn" type="button" data-action="copy-prompt" data-prompt-key="${esc(key)}" data-label="複製提示詞">複製提示詞</button>
    </div>
    <pre><code>${esc(text)}</code></pre>
  </div>`;
}

/** 單元提示詞手風琴 */
function renderPromptAccordion(unit, platform) {
  const hasOsSpecific = unit.prompt && unit.promptMac && unit.prompt !== unit.promptMac;
  const effectivePrompt = (platform === "mac" && unit.promptMac) ? unit.promptMac : unit.prompt;
  const promptCards = [];
  if (Array.isArray(unit.prompts) && unit.prompts.length) {
    // 新結構：依序渲染 prompts[]，保留平台文字轉換，攜帶 kind 供徽章使用
    unit.prompts.forEach((p) => {
      promptCards.push({
        title: translatePlatformText(p.title, platform),
        note: translatePlatformText(p.note, platform),
        text: translatePlatformText(p.text, platform),
        kind: p.kind || "build"
      });
    });
  } else if (effectivePrompt) {
    // 舊結構退回：整章總提示詞單卡
    const platformLabel = hasOsSpecific ? (platform === "mac" && unit.promptMac ? "（macOS 版）" : "（Windows 版）") : "";
    promptCards.push({ title: translatePlatformText(`AI 協作提示詞 ${platformLabel}`, platform).trim(), note: "", text: effectivePrompt, kind: "build" });
  }

  if (!promptCards.length) return "";

  // 將提示詞文字存入全域 map 供複製時取用
  promptCards.forEach((card, i) => { promptTextMap[`${unit.id}-${i}`] = card.text; });

  const cardsHtml = promptCards.map((card, i) => renderPromptBox(card.title, card.note, card.text, `${unit.id}-${i}`, card.kind)).join("");
  const platformBar = hasOsSpecific ? `<div class="prompt-platform-bar">
    <span class="prompt-platform-label">當前提示詞環境偏好：</span>
    <button class="prompt-platform-btn${platform === "windows" ? " active" : ""}" type="button" data-action="set-platform" data-platform="windows">Windows</button>
    <button class="prompt-platform-btn${platform === "mac" ? " active" : ""}" type="button" data-action="set-platform" data-platform="mac">macOS</button>
  </div>` : "";
  return `<details class="accordion-item"${accordionOpen ? " open" : ""}>
    <summary class="accordion-summary">AI 協作提示詞</summary>
    <div class="accordion-body concept-list">
      ${platformBar}
      ${cardsHtml}
    </div>
  </details>`;
}

/** 任務清單 */
function renderTaskList(tasks, taskState, platform) {
  return `<div class="checklist">${(tasks || []).map((task) => {
    const checked = !!taskState[task.id];
    const text = translatePlatformText(task.label || task.text, platform);
    return `<label class="check${checked ? " done" : ""}" data-action="toggle-task" data-task-id="${esc(task.id)}"><input type="checkbox" data-task="${esc(task.id)}"${checked ? " checked" : ""}><span>${esc(text)}</span></label>`;
  }).join("")}</div>`;
}

/** 教材素材清單 */
function renderMaterials(materials) {
  if (!materials?.length) return "";
  return `<div class="materials">${materials.map((m) => {
    const ext = (m.type || "MD").toLowerCase();
    const isMd = ext === "md";
    return `<div class="material-row"><span class="material-tag">${esc(m.type || "MD")}</span><div><a class="material-name" href="/materials/${esc(m.name)}.${esc(ext)}"${isMd ? ` data-action="preview-material" data-material-name="${esc(m.name)}" data-material-type="${esc(m.type || "MD")}"` : ""}>${esc(m.name)}</a>${m.desc ? `<div class="material-desc">${esc(m.desc)}</div>` : ""}</div></div>`;
  }).join("")}</div>`;
}

/** 單元圖片 */
function renderIllustrations(unit) {
  if (!unit.illustrations?.length) return "";
  return `<div class="unit-visual-grid">${unit.illustrations.map((ill) =>
    `<figure class="illustration illustration-${esc(ill.kind || "image")}"><img src="assets/illustrations/${esc(ill.name)}" alt="${esc(ill.alt || unit.title)}"/><figcaption class="caption"><strong>${esc(getIllustrationKindLabel(ill.kind))}</strong><span>${esc(ill.alt || unit.title)}</span></figcaption></figure>`
  ).join("")}</div>`;
}

/** 單一課程單元卡片 */
function renderUnitCard(day, unit, index, taskState, platform) {
  const unitId = `${day.id}-${unit.id}`;
  const total = unit.tasks?.length || 0;
  const done = (unit.tasks || []).filter((t) => taskState[t.id]).length;
  const titleText = translatePlatformText(unit.title, platform);
  const subtitleText = translatePlatformText(unit.subtitle || unit.principle, platform);
  const searchText = `${titleText} ${subtitleText} ${unit.prompt || ""}`.toLowerCase();
  const hidden = searchQuery && !(`${searchText} ${unit.principle || ""}`.toLowerCase().includes(searchQuery.trim().toLowerCase()));
  const features = (unit.features || []).length ? `<div class="unit-feature-highlight"><span class="unit-feature-glyph">${unitGlyph(index)}</span><div class="unit-feature-body"><strong>本章將完成的功能</strong><div class="feature-chip-row">${unit.features.map((f) => `<span class="feature-chip">${featureCheckIcon()}${esc(f)}</span>`).join("")}</div></div></div>` : "";
  const principle = unit.principle ? `<div class="note"><strong>技術設計原則與核心心法</strong><p>${esc(translatePlatformText(unit.principle, platform))}</p></div>` : "";
  const goals = (unit.goals || []).map((g) => `<div class="goal-item">${esc(translatePlatformText(g, platform))}</div>`).join("");
  const materialsHtml = unit.materials?.length ? `<div class="content-block"><h4>本單元教學素材</h4>${renderMaterials(unit.materials)}</div>` : "";

  return `<article class="unit unit-card glass-card${hidden ? " hidden-by-search" : ""}" id="${esc(unitId)}" data-search="${esc(searchText)}">
    <span class="unit-kicker">Unit ${index + 1} / ${esc(day.date)}</span>
    <h4 class="unit-title">${esc(titleText)}</h4>
    <p class="unit-summary">${esc(subtitleText)}</p>
    <div class="unit-meta-row"><span class="unit-meta-chip">${esc(unit.time)}</span><span class="unit-meta-chip unit-progress-pill">${done} / ${total} 完成</span></div>
    ${features}
    ${renderIllustrations(unit)}
    ${principle}
    <div class="content-block"><h4>學習目標</h4><div class="goal-list">${goals}</div></div>
    <div class="accordion">
      ${renderConceptAccordion(unit)}
      ${renderPromptAccordion(unit, platform)}
    </div>
    <section class="task-panel"><h4>本章學習進度</h4>${renderTaskList(unit.tasks || [], taskState, platform)}${materialsHtml}</section>
  </article>`;
}

/** 每日課程區塊 */
function renderDayBlock(course, dayMeta) {
  const day = course[dayMeta.id];
  if (!day) return "";
  const allUnits = getAllUnits(course);
  const units = day.units.map((unit) => {
    const index = allUnits.findIndex((item) => item.unit.id === unit.id);
    return renderUnitCard(day, unit, index, state.tasks || {}, state.platform || "windows");
  }).join("");
  return `<section class="day-block" id="${esc(day.id)}">
    <div class="day-header glass-card"><div class="day-header-grid"><div class="day-numeral">D${dayMeta.n}</div><div><span class="eyebrow">${esc(day.date)} / ${dayMeta.hours} 小時</span><h3>${esc(day.title)}</h3><p>${esc(day.learningGoal)}</p></div></div></div>
    ${units}
  </section>`;
}

/** 全站教材素材總覽 */
function renderMaterialsOverview(materials) {
  if (!materials?.length) return "";
  return `<section class="section glass-card" id="materials-overview"><div class="section-header"><h3>教學素材與講義下載總覽</h3><p>彙整範例代碼、SQL 遷移檔、提示詞大綱與環境指引，讓每個單元都能回到同一套 AI CRM 專案驗證。</p></div>${renderMaterials(materials)}</section>`;
}

/** 結訓單選測驗 */
function renderQuiz(quizList, quizState) {
  if (!quizList?.length) return "";
  const cards = quizList.map((quiz, index) => {
    const options = quiz.options.map((option, oi) =>
      `<label class="quiz-option"><input type="radio" name="${esc(quiz.id)}" data-action="answer-quiz" data-quiz="${esc(quiz.id)}" data-option="${oi}"${quizState?.[quiz.id] === oi ? " checked" : ""}/><span>${esc(option)}</span></label>`
    ).join("");
    return `<article class="quiz-card"><div class="quiz-question">${index + 1}. ${esc(quiz.q)}</div><div class="quiz-options">${options}</div></article>`;
  }).join("");
  return `<section class="section glass-card" id="quiz"><div class="section-header"><h3>結訓總體驗收（${quizList.length} 題單選）</h3><p>完成所有實作任務後再作答，檢查自己是否理解每個技術在 AI CRM 系統中的責任邊界。</p></div><div class="quiz-container">${cards}</div><button class="quiz-submit-btn" type="button" data-action="submit-quiz">送出測驗答案</button></section>`;
}

/** 側邊導覽與學習總進度 */
function renderSidebar(course) {
  const allUnits = getAllUnits(course);
  const allTasks = allUnits.flatMap(({ unit }) => unit.tasks || []);
  const done = allTasks.filter((t) => state.tasks?.[t.id]).length;
  const total = allTasks.length;
  const percent = total ? Math.round((done / total) * 100) : 0;

  const navGroups = course.meta.days.map((dayMeta) => {
    const day = course[dayMeta.id];
    if (!day) return "";
    const open = state.navGroups?.[day.id] !== false;
    const links = day.units.map((unit, i) =>
      `<a class="nav-chapter-link" href="#${day.id}-${unit.id}" data-target="${day.id}-${unit.id}"><span class="nav-chapter-num">U${i + 1}</span><span class="nav-chapter-title">${esc(unit.title)}</span></a>`
    ).join("");
    return `<details class="nav-group"${open ? " open" : ""} data-day="${esc(day.id)}"><summary class="nav-group-header"><span class="nav-group-badge">${esc(day.date)}</span><span class="nav-group-title">${esc(day.title)}</span></summary><div class="nav-group-body">${links}</div></details>`;
  }).join("");

  return `<aside class="sidebar">
    <button id="sidebar-close" class="sidebar-close-btn" type="button" title="收起選單" data-action="hide-sidebar">x</button>
    <div class="brand"><small>Teaching Site</small><h1 id="courseTitle">${esc(course.meta.title)}</h1><p id="courseSummary">${esc(course.meta.subtitle)}</p></div>
    <nav id="unitNav" aria-label="課程章節導覽">
      <a class="nav-link" href="#overview" data-target="overview"><strong>課程總覽</strong><span>定位與準備</span></a>
      <a class="nav-link" href="#feature-roadmap" data-target="feature-roadmap"><strong>功能藍圖</strong><span>各章節產出</span></a>
      <a class="nav-link" href="#shared-case" data-target="shared-case"><strong>AI CRM 情境</strong><span>共同案例</span></a>
      ${navGroups}
      <a class="nav-link" href="#materials-overview" data-target="materials-overview"><strong>素材總覽</strong><span>講義與附件</span></a>
      <a class="nav-link" href="#quiz" data-target="quiz"><strong>結訓測驗</strong><span>學習驗收</span></a>
      <a class="nav-link" href="#superpowers" data-target="superpowers"><strong>superpowers 補充</strong><span>規格先行技能組</span></a>
    </nav>
    <div class="sidebar-tools">
      <div class="progress-box"><strong>全課學習進度</strong><p id="progressText">${done} / ${total} 任務已完成 (${percent}%)</p><div class="progress-track" aria-hidden="true"><div id="progressFill" class="progress-fill" style="width:${percent}%"></div></div></div>
      <button id="resetProgress" class="tool-button" type="button" data-action="reset-progress">清除學習進度</button>
    </div>
  </aside>`;
}

/** 教材預覽 Modal */
function renderMaterialPreviewModal() {
  if (!previewMaterial) return "";
  return `<div class="modal-overlay" data-action="modal-overlay-click">
    <div class="modal-card">
      <div class="modal-header"><h3>講義預覽：${esc(previewMaterial.name)}</h3><button class="modal-close" data-action="close-modal" aria-label="關閉預覽">&times;</button></div>
      <div class="modal-body markdown-body" id="modal-content"><div class="modal-loading">正在載入講義內容...</div></div>
      <div class="modal-actions">
        <a class="modal-btn download-btn" href="materials/${esc(previewMaterial.name)}.${esc((previewMaterial.type || "MD").toLowerCase())}" download="${esc(previewMaterial.name)}.${esc((previewMaterial.type || "MD").toLowerCase())}">下載 Markdown 原始檔</a>
        <button class="modal-btn print-btn" data-action="print-page">列印 / 匯出 PDF</button>
        <button class="modal-btn close-btn" data-action="close-modal">關閉</button>
      </div>
    </div>
  </div>`;
}

/* ──────────────────────────────────────────────
   5. 提示詞文字暫存（供複製功能使用）
   ────────────────────────────────────────────── */

/** 提示詞文字 map，在 renderPromptAccordion 時填入 */
const promptTextMap = {};

/* ──────────────────────────────────────────────
   6. 主渲染函式
   ────────────────────────────────────────────── */

/**
 * 重新渲染整個應用程式
 */
function renderApp() {
  const course = window.COURSE;
  if (!course) return;

  // 清空提示詞暫存
  Object.keys(promptTextMap).forEach((k) => delete promptTextMap[k]);

  const root = document.getElementById("root");
  root.innerHTML = `
    <button id="sidebar-restore" class="sidebar-restore-btn" type="button" data-action="restore-sidebar">選單</button>
    <div class="top-actions">
      <input id="searchInput" class="search" type="search" placeholder="搜尋單元、觀念或 AI 提示詞..." value="${esc(searchQuery)}"/>
      <button id="expandAll" class="top-action-button" type="button" data-action="toggle-accordion">${accordionOpen ? "收合全部" : "展開全部"}</button>
      <button id="themeToggle" class="top-theme-toggle" type="button" data-action="toggle-theme">${state.theme === "dark" ? "切換淺色模式" : "切換深色模式"}</button>
    </div>
    <div class="shell">
      ${renderSidebar(course)}
      <main class="main">
        <div id="heroRoot">${renderHero(course.meta)}</div>
        <div id="content" class="content">
          ${renderOverview(course.meta)}
          ${renderFeatureRoadmap(course)}
          ${renderSharedCase(course.sharedCase)}
          ${course.meta.days.map((dayMeta) => renderDayBlock(course, dayMeta)).join("")}
          ${renderMaterialsOverview(course.materials)}
          ${renderQuiz(course.quiz, state.quiz || {})}
          ${renderSuperpowers(course.superpowers)}
          <footer class="site-footer">教學網站採用純 HTML + JS + CSS 呈現，課程資料仍由 course-data.js 驅動。</footer>
        </div>
      </main>
    </div>
    ${renderMaterialPreviewModal()}`;

  // 恢復搜尋框焦點
  const searchInput = document.getElementById("searchInput");
  if (searchInput && document.activeElement?.id === "searchInput") {
    searchInput.focus();
    searchInput.setSelectionRange(searchInput.value.length, searchInput.value.length);
  }

  // 載入教材預覽內容
  if (previewMaterial) loadMaterialContent();

  // 設定 IntersectionObserver 做 scrollspy
  setupScrollSpy();
}

/* ──────────────────────────────────────────────
   7. Scrollspy 與 Modal 載入
   ────────────────────────────────────────────── */

let scrollSpyObserver = null;

/** 設定 IntersectionObserver 做側邊欄 scrollspy */
function setupScrollSpy() {
  if (scrollSpyObserver) scrollSpyObserver.disconnect();
  const links = Array.from(document.querySelectorAll(".nav-link, .nav-chapter-link"));
  const sections = links.map((link) => document.getElementById(link.getAttribute("data-target"))).filter(Boolean);
  scrollSpyObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) return;
      const activeId = entry.target.id;
      links.forEach((link) => link.classList.toggle("active", link.getAttribute("data-target") === activeId));
    });
  }, { rootMargin: "-20% 0px -60% 0px", threshold: 0.08 });
  sections.forEach((section) => scrollSpyObserver.observe(section));
}

/** 載入教材預覽 Modal 的 Markdown 內容 */
function loadMaterialContent() {
  if (!previewMaterial) return;
  const filePath = `materials/${previewMaterial.name}.${(previewMaterial.type || "MD").toLowerCase()}`;
  const contentEl = document.getElementById("modal-content");
  if (!contentEl) return;
  fetch(filePath)
    .then((res) => { if (!res.ok) throw new Error(`無法載入素材檔案 (${res.status})`); return res.text(); })
    .then((text) => { contentEl.innerHTML = parseMarkdown(text); })
    .catch((err) => { contentEl.innerHTML = `<div class="modal-error">讀取失敗：${esc(err.message)}</div>`; });
}

/* ──────────────────────────────────────────────
   8. 事件委派（Event Delegation）
   ────────────────────────────────────────────── */

document.addEventListener("click", (e) => {
  const target = e.target.closest("[data-action]");
  if (!target) return;
  const action = target.dataset.action;

  switch (action) {
    case "toggle-theme":
      patchState((s) => ({ ...s, theme: s.theme === "dark" ? "light" : "dark" }));
      break;

    case "toggle-accordion":
      accordionOpen = !accordionOpen;
      renderApp();
      break;

    case "hide-sidebar":
      document.body.classList.remove("sidebar-visible-mobile");
      patchState((s) => ({ ...s, sidebarHidden: true }));
      break;

    case "restore-sidebar":
      if (window.innerWidth <= 1180) {
        document.body.classList.toggle("sidebar-visible-mobile");
      } else {
        patchState((s) => ({ ...s, sidebarHidden: false }));
      }
      break;

    case "reset-progress":
      patchState((s) => ({ ...s, tasks: {} }));
      break;

    case "toggle-task": {
      const taskId = target.dataset.taskId;
      const was = target.classList.contains("done");
      patchState((s) => {
        const tasks = { ...(s.tasks || {}) };
        if (!was) tasks[taskId] = true; else delete tasks[taskId];
        return { ...s, tasks };
      });
      break;
    }

    case "answer-quiz": {
      const quizId = target.dataset.quiz;
      const optionIndex = parseInt(target.dataset.option, 10);
      patchState((s) => ({ ...s, quiz: { ...(s.quiz || {}), [quizId]: optionIndex } }));
      break;
    }

    case "submit-quiz": {
      const course = window.COURSE;
      const quizList = course.quiz || [];
      const quizState = state.quiz || {};
      const score = quizList.filter((q) => quizState[q.id] === q.answer).length;
      const unanswered = quizList.filter((q) => quizState[q.id] == null).length;
      if (unanswered) { alert(`還有 ${unanswered} 題尚未作答。`); return; }
      alert(`答對 ${score} / ${quizList.length} 題。`);
      break;
    }

    case "set-platform": {
      const platform = target.dataset.platform;
      if (state.platform !== platform) {
        patchState((s) => ({ ...s, platform }));
      }
      break;
    }

    case "concept-tab": {
      const unitId = target.dataset.unit;
      const index = parseInt(target.dataset.index, 10);
      conceptTabState[unitId] = index;
      renderApp();
      break;
    }

    case "concept-prev": {
      const unitId = target.dataset.unit;
      const current = conceptTabState[unitId] || 0;
      if (current > 0) { conceptTabState[unitId] = current - 1; renderApp(); }
      break;
    }

    case "concept-next": {
      const stateKey = target.dataset.unit;
      const current = conceptTabState[stateKey] || 0;
      const course = window.COURSE;
      const allUnits = getAllUnits(course);
      /** stateKey 可能是 "u1" 或 "u1__crm"，需要解析出真實 unitId */
      const realUnitId = stateKey.replace("__crm", "");
      const isCrm = stateKey.endsWith("__crm");
      const unitData = allUnits.find((u) => u.unit.id === realUnitId);
      if (unitData) {
        /** 依據是否為 CRM 區塊，計算對應的群組數量 */
        const groupNames = new Set(
          unitData.unit.concepts
            .filter((c) => {
              const g = c.group || c.heading;
              return isCrm ? isCrmGroup(g) : !isCrmGroup(g);
            })
            .map((c) => c.group || c.heading)
        );
        if (current < groupNames.size - 1) {
          conceptTabState[stateKey] = current + 1;
          renderApp();
        }
      }
      break;
    }

    case "copy-prompt": {
      const key = target.dataset.promptKey;
      const text = promptTextMap[key];
      if (text) {
        copyText(text).then(() => {
          target.textContent = "已複製";
          target.classList.add("ok");
          setTimeout(() => { target.textContent = "複製提示詞"; target.classList.remove("ok"); }, 1500);
        });
      }
      break;
    }

    case "preview-material": {
      e.preventDefault();
      previewMaterial = { name: target.dataset.materialName, type: target.dataset.materialType };
      renderApp();
      break;
    }

    case "close-modal":
      previewMaterial = null;
      renderApp();
      break;

    case "modal-overlay-click":
      if (e.target.classList.contains("modal-overlay")) { previewMaterial = null; renderApp(); }
      break;

    case "print-page":
      window.print();
      break;
  }
});

// 搜尋框即時篩選
document.addEventListener("input", (e) => {
  if (e.target.id === "searchInput") {
    searchQuery = e.target.value;
    renderApp();
    // 重新聚焦搜尋框
    const el = document.getElementById("searchInput");
    if (el) { el.focus(); el.setSelectionRange(el.value.length, el.value.length); }
  }
});

// 側邊欄導覽群組展開/收合狀態追蹤
document.addEventListener("toggle", (e) => {
  if (e.target.classList?.contains("nav-group")) {
    const dayId = e.target.dataset.day;
    if (dayId) {
      state.navGroups = { ...(state.navGroups || {}), [dayId]: e.target.open };
      saveState(state);
    }
  }
}, true);

/* ──────────────────────────────────────────────
   9. 應用程式初始化
   ────────────────────────────────────────────── */

document.addEventListener("DOMContentLoaded", () => {
  applyTheme();
  renderApp();
});
