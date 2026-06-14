import { Fragment, useEffect, useMemo, useState } from "react";

const STORE_KEY = "hahow-ai-crm-progress-v2";

/**
 * 讀取 localStorage 狀態，集中管理主題、任務、測驗與側欄狀態。
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
 */
function saveState(nextState) {
  localStorage.setItem(STORE_KEY, JSON.stringify(nextState));
}

/**
 * 根據當前選擇的平台，動態轉換文字中的系統關鍵字（如 Windows 轉為 macOS 等）。
 * 並且統一過濾移除提示詞標題開頭的 "A. " 字首。
 * 
 * @param {string} text 原始文字
 * @param {string} platform 當前平台 ("windows" 或 "mac")
 * @returns {string} 轉換後的文字
 */
function translatePlatformText(text, platform) {
  if (!text) return text;
  
  // 統一移除開頭的 "A. " 標記
  let result = text.replace(/^A\.\s*/, '');
  
  // 如果不是 macOS，則不需要進行替換，直接回傳結果
  if (platform !== "mac") return result;
  
  // 進行 Windows 關鍵字到 macOS 關鍵字的對應替換
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
 * 將粗體、行內程式碼與 Markdown 連結轉成 React 節點，避免使用 innerHTML。
 */
function InlineMarkdown({ text }) {
  if (!text) return null;
  const pattern = /(\*\*[^*]+\*\*|`[^`]+`|\[[^\]]+\]\([^)]+\))/g;
  return String(text).split(pattern).map((part, index) => {
    if (!part) return null;
    if (part.startsWith("**") && part.endsWith("**")) {
      return <strong key={index}>{part.slice(2, -2)}</strong>;
    }
    if (part.startsWith("`") && part.endsWith("`")) {
      return <code key={index}>{part.slice(1, -1)}</code>;
    }
    const linkMatch = part.match(/^\[([^\]]+)\]\(([^)]+)\)$/);
    if (linkMatch) {
      return (
        <a key={index} href={linkMatch[2]} target="_blank" rel="noopener">
          {linkMatch[1]}
        </a>
      );
    }
    return <Fragment key={index}>{part}</Fragment>;
  });
}

/**
 * 簡易安全 Markdown 解析器，將講義 Markdown 字串轉換為 React 元素。
 * 遵循 React 19 安全規範，不使用 dangerouslySetInnerHTML。
 * 支援標題、段落、列表、程式碼區塊及行內標記的解析。
 */
function parseMarkdown(mdText) {
  if (!mdText) return [];
  const lines = mdText.split(/\r?\n/);
  const elements = [];
  
  let inCodeBlock = false;
  let codeLang = "";
  let codeLines = [];
  
  let listType = null; // null, "ul", "ol"
  let listItems = [];

  // 輔助函數：將暫存的列表項目轉為 React 節點並推入 elements
  const flushList = (key) => {
    if (listItems.length > 0) {
      if (listType === "ul") {
        elements.push(<ul key={`ul-${key}`}>{...listItems}</ul>);
      } else if (listType === "ol") {
        elements.push(<ol key={`ol-${key}`}>{...listItems}</ol>);
      }
      listItems = [];
      listType = null;
    }
  };

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];

    // 處理程式碼區塊標記 (```)
    if (line.trim().startsWith("```")) {
      if (inCodeBlock) {
        // 結束程式碼區塊
        elements.push(
          <pre key={`code-${i}`} className={`code-block lang-${codeLang}`}>
            <code>{codeLines.join("\n")}</code>
          </pre>
        );
        codeLines = [];
        inCodeBlock = false;
        codeLang = "";
      } else {
        // 開始新程式碼區塊前，先清空可能未完成的列表
        flushList(i);
        inCodeBlock = true;
        codeLang = line.trim().slice(3).trim();
      }
      continue;
    }

    if (inCodeBlock) {
      codeLines.push(line);
      continue;
    }

    const trimmed = line.trim();

    // 處理空行
    if (!trimmed) {
      flushList(i);
      continue;
    }

    // 處理標題
    if (line.startsWith("# ")) {
      flushList(i);
      elements.push(<h1 key={`h1-${i}`}><InlineMarkdown text={line.slice(2)} /></h1>);
      continue;
    }
    if (line.startsWith("## ")) {
      flushList(i);
      elements.push(<h2 key={`h2-${i}`}><InlineMarkdown text={line.slice(3)} /></h2>);
      continue;
    }
    if (line.startsWith("### ")) {
      flushList(i);
      elements.push(<h3 key={`h3-${i}`}><InlineMarkdown text={line.slice(4)} /></h3>);
      continue;
    }

    // 處理無序列表項目 (- 或 *)
    const ulMatch = line.match(/^(\s*)[-*]\s+(.*)$/);
    if (ulMatch) {
      if (listType !== "ul") {
        flushList(i);
        listType = "ul";
      }
      const content = ulMatch[2];
      listItems.push(<li key={`li-${i}`}><InlineMarkdown text={content} /></li>);
      continue;
    }

    // 處理有序列表項目 (1., 2. 等)
    const olMatch = line.match(/^(\s*)\d+\.\s+(.*)$/);
    if (olMatch) {
      if (listType !== "ol") {
        flushList(i);
        listType = "ol";
      }
      const content = olMatch[2];
      listItems.push(<li key={`li-${i}`}><InlineMarkdown text={content} /></li>);
      continue;
    }

    // 處理普通段落
    flushList(i);
    elements.push(<p key={`p-${i}`} className="preview-paragraph"><InlineMarkdown text={line} /></p>);
  }

  flushList("end");
  return elements;
}

/**
 * 教材素材預覽 Modal 元件
 * 提供 Markdown 格式化預覽、原始檔下載與 PDF 列印匯出功能
 */
function MaterialPreviewModal({ material, onClose }) {
  const [content, setContent] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // 當選擇的素材變更時，抓取對應的實體 Markdown 檔案內容
  useEffect(() => {
    if (!material) return;
    setLoading(true);
    setError(null);
    const filePath = `/materials/${material.name}.${(material.type || "MD").toLowerCase()}`;
    
    fetch(filePath)
      .then((res) => {
        if (!res.ok) {
          throw new Error(`無法載入素材檔案 (${res.status})`);
        }
        return res.text();
      })
      .then((text) => {
        setContent(text);
        setLoading(false);
      })
      .catch((err) => {
        setError(err.message);
        setLoading(false);
      });
  }, [material]);

  if (!material) return null;

  // 點擊 Modal 半透明背景時關閉預覽
  const handleOverlayClick = (e) => {
    if (e.target.classList.contains("modal-overlay")) {
      onClose();
    }
  };

  return (
    <div className="modal-overlay" onClick={handleOverlayClick}>
      <div className="modal-card">
        <div className="modal-header">
          <h3>講義預覽：{material.name}</h3>
          <button className="modal-close" onClick={onClose} aria-label="關閉預覽">&times;</button>
        </div>
        <div className="modal-body markdown-body">
          {loading ? (
            <div className="modal-loading">正在載入講義內容...</div>
          ) : error ? (
            <div className="modal-error">讀取失敗：{error}</div>
          ) : (
            parseMarkdown(content)
          )}
        </div>
        <div className="modal-actions">
          <a 
            className="modal-btn download-btn" 
            href={`/materials/${material.name}.${(material.type || "MD").toLowerCase()}`} 
            download={`${material.name}.${(material.type || "MD").toLowerCase()}`}
          >
            下載 Markdown 原始檔
          </a>
          <button className="modal-btn print-btn" onClick={() => window.print()}>
            列印 / 匯出 PDF
          </button>
          <button className="modal-btn close-btn" onClick={onClose}>
            關閉
          </button>
        </div>
      </div>
    </div>
  );
}

/**
 * 依照 day metadata 彙整所有課程單元，供導覽、進度與渲染共用。
 */
function getAllUnits(course) {
  return course.meta.days.flatMap((dayMeta) => {
    const day = course[dayMeta.id];
    return (day?.units || []).map((unit) => ({ dayMeta, day, unit }));
  });
}

/**
 * 將圖片種類轉成使用者可讀標籤。
 */
function getIllustrationKindLabel(kind) {
  const labels = {
    hero: "章節主視覺",
    diagram: "架構插圖",
    term: "專業術語圖"
  };
  return labels[kind] || "教學圖片";
}

// 8 個單元各自的 SVG 圖示路徑（24x24 viewBox，描邊式向量圖，依 currentColor 上色）。
// 以主題語意對應：啟動火箭 / API 大括號 / 資料庫 / 安全盾 / 版面 / 對話 / 知識書 / 流程節點。
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
 * 依單元序號回傳對應的 inline SVG 圖示。
 * @param {{ index: number }} props 單元在全課中的索引 (0 起算)
 */
function UnitGlyph({ index }) {
  const path = UNIT_GLYPH_PATHS[index % UNIT_GLYPH_PATHS.length];
  return (
    <svg className="unit-glyph" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={path} />
    </svg>
  );
}

/**
 * 小型「已完成功能」勾選 SVG 標記，供功能重點標示使用。
 */
function FeatureCheckIcon() {
  return (
    <svg className="feature-check-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M5 12.5l4.5 4.5L19 7" />
    </svg>
  );
}

/**
 * 複製文字到剪貼簿；瀏覽器權限不允許時改用 textarea fallback。
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
 * 首頁全幅 Hero，使用生圖 cover 作為第一視覺訊號。
 */
function Hero({ meta }) {
  const techPills = ["Spring Boot 3.5", "React 19 + Vite", "Spring AI", "pgvector RAG", "MCP", "Embabel GOAP"];
  return (
    <div className="hero-band">
      <section className="hero" id="top">
        <div className="hero-media">
          <img src="assets/illustrations/cover.png" alt="AI CRM 課程全幅主視覺" />
        </div>
        <div className="hero-overlay">
          <div className="hero-copy">
            <span className="eyebrow">{meta.program}</span>
            <h2>{meta.title}</h2>
            <p>
              用同一套 AI CRM 專案，把 Spring MVC、JPA、安全性、React 19、Spring AI、RAG、MCP 與 Embabel agent flow 串成可驗證的企業級全端學習路線。
            </p>
            <div className="pill-row">
              {techPills.map((item) => (
                <span className="pill" key={item}>{item}</span>
              ))}
            </div>
            <div className="hero-actions">
              <a className="primary-button" href="#overview">開始閱讀課程</a>
              <a className="secondary-button" href="#day1">查看 Day 1</a>
            </div>
          </div>
          <aside className="hero-panel">
            <h3>完成後你會得到</h3>
            <ul>
              {meta.objectives.slice(0, 5).map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </aside>
        </div>
      </section>
    </div>
  );
}

/**
 * 課程總覽區塊，呈現形式、時數、結訓專案與驗收方式。
 */
function Overview({ meta }) {
  const hours = meta.days.reduce((sum, day) => sum + day.hours, 0);
  return (
    <section className="section glass-card" id="overview">
      <div className="section-header">
        <h3>課程總覽</h3>
        <p>這門課不是零散技術拼盤，而是讓每個單元都接續前一個產出，最後完成可登入、可查資料、可引用知識庫、可追蹤 agent 決策的 AI CRM。</p>
      </div>
      <div className="overview-lead">
        <h4>四天的主線是一套產品逐步長出來的路徑。</h4>
        <p>前半段先把 Spring Boot、REST、JPA、Flyway、Security 與 React 工作台打穩；後半段把 Spring AI、SSE、tool calling、RAG、MCP 與 Embabel 接回同一套 CRM domain。每個章節都有明確產出與驗收方式。</p>
        <div className="pill-row overview-pills">
          {meta.days.map((day) => (
            <span className="pill" key={day.id}>{day.date} / {day.hours}h</span>
          ))}
        </div>
      </div>
      <div className="stat-grid">
        <article className="stat-card"><small>課程形式</small><strong>{meta.format}</strong><p>{meta.location}</p></article>
        <article className="stat-card"><small>授課時數</small><strong>{hours} 小時</strong><p>可拆成四天工作坊或線上章節節奏。</p></article>
        <article className="stat-card"><small>結訓專案</small><strong>AI CRM 智慧業務助理</strong><p>從登入、客戶資料、AI 聊天到 agent trace 完整跑通。</p></article>
        <article className="stat-card"><small>驗收方式</small><strong>本機 verify + 作業提交</strong><p>{meta.completion[0]}</p></article>
      </div>
    </section>
  );
}

/**
 * 全課功能藍圖：用 SVG 圖示卡片，總覽每個章節將為 AI CRM 完成哪些功能。
 * @param {{ course: object }} props 課程資料
 */
function FeatureRoadmap({ course }) {
  const allUnits = getAllUnits(course);
  return (
    <section className="section glass-card" id="feature-roadmap">
      <div className="section-header">
        <h3>AI CRM 功能藍圖</h3>
        <p>八個章節像一條產品生產線，每一站都為同一套 AI CRM 補上一塊可運作的功能。點任一卡片即可跳到該章節。</p>
      </div>
      <div className="feature-roadmap-grid">
        {allUnits.map(({ dayMeta, day, unit }, index) => (
          <a className="roadmap-card" href={`#${day.id}-${unit.id}`} key={unit.id}>
            <div className="roadmap-card-head">
              <span className="roadmap-glyph"><UnitGlyph index={index} /></span>
              <div className="roadmap-card-title">
                <span className="roadmap-kicker">Unit {index + 1} · {dayMeta.date}</span>
                <strong>{unit.title}</strong>
              </div>
            </div>
            <ul className="roadmap-feature-list">
              {(unit.features || []).map((feature) => (
                <li key={feature}><FeatureCheckIcon />{feature}</li>
              ))}
            </ul>
          </a>
        ))}
      </div>
    </section>
  );
}

/**
 * 單元卡片開頭的「本章將完成的功能」重點標示。
 * @param {{ index: number, features: string[] }} props 單元索引與功能清單
 */
function UnitFeatureHighlight({ index, features }) {
  if (!features?.length) return null;
  return (
    <div className="unit-feature-highlight">
      <span className="unit-feature-glyph"><UnitGlyph index={index} /></span>
      <div className="unit-feature-body">
        <strong>本章將完成的功能</strong>
        <div className="feature-chip-row">
          {features.map((feature) => (
            <span className="feature-chip" key={feature}><FeatureCheckIcon />{feature}</span>
          ))}
        </div>
      </div>
    </div>
  );
}

/**
 * 呈現貫穿全課的 AI CRM 共同企業情境。
 */
function SharedCase({ sharedCase }) {
  if (!sharedCase) return null;
  return (
    <section className="section glass-card" id="shared-case">
      <div className="section-header">
        <h3>貫穿全程的 AI CRM 情境</h3>
        <p>{sharedCase.intro}</p>
      </div>
      <div className="brand-grid">
        {sharedCase.brands.map((brand) => (
          <article className="brand-card brand-card-split" key={brand.id}>
            <div className="brand-card-info">
              <small>{brand.type}</small>
              <h4>{brand.name}</h4>
              <table className="data-table">
                <tbody>
                  {brand.rows.map(([key, value]) => (
                    <tr key={key}><td>{key}</td><td><InlineMarkdown text={value} /></td></tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="brand-card-media">
              <img src="assets/illustrations/office.png" alt={`${brand.name} 辦公空間`} />
            </div>
          </article>
        ))}
      </div>
      <div className="summary-grid">
        {sharedCase.roles.map(([name, brand, role, desc]) => (
          <article className="summary-card" key={name}>
            <small>{brand}</small>
            <strong>{name} / {role}</strong>
            <p>{desc}</p>
          </article>
        ))}
      </div>
    </section>
  );
}

/**
 * 顯示單元學習目標。
 * 根據當前平台 (Windows / macOS) 轉換文字描述。
 */
function GoalList({ goals, platform }) {
  return (
    <div className="goal-list">
      {(goals || []).map((goal) => (
        <div className="goal-item" key={goal}>
          {translatePlatformText(goal, platform)}
        </div>
      ))}
    </div>
  );
}

/**
 * 顯示單元圖片，依 hero / diagram / term 套用不同布局。
 */
function Illustrations({ unit }) {
  if (!unit.illustrations?.length) return null;
  return (
    <div className="unit-visual-grid">
      {unit.illustrations.map((illustration) => (
        <figure className={`illustration illustration-${illustration.kind || "image"}`} key={illustration.name}>
          <img src={`assets/illustrations/${illustration.name}`} alt={illustration.alt || unit.title} />
          <figcaption className="caption">
            <strong>{getIllustrationKindLabel(illustration.kind)}</strong>
            <span>{illustration.alt || unit.title}</span>
          </figcaption>
        </figure>
      ))}
    </div>
  );
}

/**
 * 顯示單一核心觀念內容。
 */
/**
 * Spring MVC 請求流動 SVG 圖解
 */
function SvgSpringMvcFlow() {
  return (
    <svg className="concept-svg-illustration" viewBox="0 0 400 220" fill="none" xmlns="http://www.w3.org/2000/svg">
      <rect width="400" height="220" rx="12" fill="var(--surface)" stroke="var(--border)" strokeWidth="1" />
      <g className="svg-flow-node" transform="translate(20, 90)">
        <rect width="70" height="40" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <text x="35" y="24" fill="var(--fg)" fontSize="10" fontWeight="800" textAnchor="middle">Client 瀏覽器</text>
      </g>
      <path d="M 90 110 H 130" stroke="var(--accent)" strokeWidth="1.5" markerEnd="url(#arrow)" />
      <path className="svg-flow-arrow" d="M 90 110 H 130" stroke="white" strokeWidth="1.5" />
      <g className="svg-flow-node" transform="translate(130, 60)">
        <rect width="90" height="100" rx="10" fill="var(--accent)" opacity="0.1" />
        <rect width="90" height="100" rx="10" stroke="var(--accent)" strokeWidth="2" />
        <text x="45" y="45" fill="var(--accent-deep)" fontSize="10" fontWeight="800" textAnchor="middle">Dispatcher</text>
        <text x="45" y="62" fill="var(--accent-deep)" fontSize="10" fontWeight="800" textAnchor="middle">Servlet</text>
        <text x="45" y="80" fill="var(--muted)" fontSize="8" textAnchor="middle">(前端控制器)</text>
      </g>
      <path d="M 220 95 L 260 70" stroke="var(--border-strong)" strokeWidth="1.5" />
      <path className="svg-flow-arrow" d="M 220 95 L 260 70" stroke="var(--accent)" strokeWidth="1.5" />
      <g className="svg-flow-node" transform="translate(260, 40)">
        <rect width="90" height="36" rx="6" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <text x="45" y="22" fill="var(--fg)" fontSize="9" fontWeight="800" textAnchor="middle">Controller</text>
      </g>
      <path d="M 305 76 V 130" stroke="var(--border-strong)" strokeWidth="1.5" />
      <g className="svg-flow-node" transform="translate(260, 130)">
        <rect width="90" height="36" rx="6" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <text x="45" y="22" fill="var(--fg)" fontSize="9" fontWeight="800" textAnchor="middle">Service</text>
      </g>
      <path d="M 260 150 L 220 125" stroke="var(--border-strong)" strokeWidth="1.5" />
      <defs>
        <marker id="arrow" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="5" markerHeight="5" orient="auto-start-reverse">
          <path d="M 0 0 L 10 5 L 0 10 z" fill="var(--accent)" />
        </marker>
      </defs>
    </svg>
  );
}

/**
 * REST API 設計語意 SVG 圖解
 */
function SvgRestApi() {
  return (
    <svg className="concept-svg-illustration" viewBox="0 0 400 220" fill="none" xmlns="http://www.w3.org/2000/svg">
      <rect width="400" height="220" rx="12" fill="var(--surface)" stroke="var(--border)" strokeWidth="1" />
      <rect x="25" y="25" width="350" height="30" rx="6" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.2" />
      <text x="40" y="44" fill="var(--fg)" fontSize="10" fontFamily="monospace">GET /api/products</text>
      <g transform="translate(35, 75)">
        <rect width="140" height="100" rx="8" fill="color-mix(in oklab, var(--success) 8%, var(--surface))" stroke="var(--success)" strokeWidth="1.2" />
        <text x="70" y="25" fill="var(--success)" fontSize="11" fontWeight="800" textAnchor="middle">HTTP 動詞 (Action)</text>
        <rect x="20" y="40" width="100" height="18" rx="4" fill="var(--surface)" stroke="var(--border)" strokeWidth="1" />
        <text x="70" y="52" fill="var(--accent)" fontSize="9" fontWeight="800" textAnchor="middle">GET / POST</text>
        <rect x="20" y="65" width="100" height="18" rx="4" fill="var(--surface)" stroke="var(--border)" strokeWidth="1" />
        <text x="70" y="77" fill="var(--muted)" fontSize="9" fontWeight="800" textAnchor="middle">PUT / DELETE</text>
      </g>
      <path d="M 185 125 H 215" stroke="var(--border-strong)" strokeWidth="1.5" strokeDasharray="3" />
      <g transform="translate(225, 75)">
        <rect width="140" height="100" rx="8" fill="color-mix(in oklab, var(--accent) 8%, var(--surface))" stroke="var(--accent)" strokeWidth="1.2" />
        <text x="70" y="25" fill="var(--accent-deep)" fontSize="11" fontWeight="800" textAnchor="middle">資源名詞 (Resource)</text>
        <rect x="20" y="45" width="100" height="36" rx="4" fill="var(--surface)" stroke="var(--border)" strokeWidth="1" />
        <text x="70" y="60" fill="var(--fg)" fontSize="10" textAnchor="middle">/products</text>
        <text x="70" y="73" fill="var(--muted)" fontSize="8" textAnchor="middle">(複數名詞集合)</text>
      </g>
    </svg>
  );
}

/**
 * AOP 概念與橫切關注點 SVG 圖解
 */
function SvgAopConcept() {
  return (
    <svg className="concept-svg-illustration" viewBox="0 0 400 220" fill="none" xmlns="http://www.w3.org/2000/svg">
      <rect width="400" height="220" rx="12" fill="var(--surface)" stroke="var(--border)" strokeWidth="1" />
      <g transform="translate(30, 25)">
        <rect x="0" y="0" width="70" height="120" rx="6" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <text x="35" y="60" fill="var(--fg)" fontSize="9" fontWeight="800" textAnchor="middle">Product</text>
        <text x="35" y="75" fill="var(--fg)" fontSize="9" fontWeight="800" textAnchor="middle">Service</text>
      </g>
      <g transform="translate(130, 25)">
        <rect x="0" y="0" width="70" height="120" rx="6" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <text x="35" y="60" fill="var(--fg)" fontSize="9" fontWeight="800" textAnchor="middle">Order</text>
        <text x="35" y="75" fill="var(--fg)" fontSize="9" fontWeight="800" textAnchor="middle">Service</text>
      </g>
      <g transform="translate(230, 25)">
        <rect x="0" y="0" width="70" height="120" rx="6" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <text x="35" y="60" fill="var(--fg)" fontSize="9" fontWeight="800" textAnchor="middle">User</text>
        <text x="35" y="75" fill="var(--fg)" fontSize="9" fontWeight="800" textAnchor="middle">Service</text>
      </g>
      <g className="svg-flow-node" transform="translate(15, 65)">
        <rect width="300" height="26" rx="6" fill="var(--accent)" opacity="0.15" />
        <rect width="300" height="26" rx="6" stroke="var(--accent)" strokeWidth="1.8" />
        <text x="150" y="17" fill="var(--accent-deep)" fontSize="9" fontWeight="800" textAnchor="middle">Aspect 橫切切面：交易控制 / Log / 權限</text>
      </g>
      <text x="165" y="195" fill="var(--muted)" fontSize="9.5" textAnchor="middle">AOP (面向切面)：解耦非業務橫切邏輯</text>
    </svg>
  );
}

/**
 * Spring Security 閥門 Filter Chain SVG 圖解
 */
function SvgSecurityChain() {
  return (
    <svg className="concept-svg-illustration" viewBox="0 0 400 220" fill="none" xmlns="http://www.w3.org/2000/svg">
      <rect width="400" height="220" rx="12" fill="var(--surface)" stroke="var(--border)" strokeWidth="1" />
      <rect x="15" y="90" width="370" height="40" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.5" />
      <text x="25" y="114" fill="var(--muted)" fontSize="8">Request</text>
      <g className="svg-filter-item" transform="translate(120, 70)">
        <rect width="42" height="80" rx="6" fill="var(--surface)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <line x1="21" y1="15" x2="21" y2="65" stroke="var(--border-strong)" strokeWidth="1.5" />
        <circle className="svg-valve-gate" cx="21" cy="40" r="9" fill="var(--accent)" opacity="0.25" />
        <circle className="svg-valve-gate" cx="21" cy="40" r="7" stroke="var(--accent)" strokeWidth="1.5" />
        <text x="21" y="88" fill="var(--fg)" fontSize="7" textAnchor="middle">CorsFilter</text>
      </g>
      <g className="svg-filter-item" transform="translate(180, 70)">
        <rect width="42" height="80" rx="6" fill="var(--surface)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <line x1="21" y1="15" x2="21" y2="65" stroke="var(--border-strong)" strokeWidth="1.5" />
        <circle className="svg-valve-gate" cx="21" cy="40" r="9" fill="var(--accent)" opacity="0.25" />
        <circle className="svg-valve-gate" cx="21" cy="40" r="7" stroke="var(--accent)" strokeWidth="1.5" />
        <text x="21" y="88" fill="var(--fg)" fontSize="7" textAnchor="middle">JwtFilter</text>
      </g>
      <g className="svg-filter-item" transform="translate(240, 70)">
        <rect width="42" height="80" rx="6" fill="var(--surface)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <line x1="21" y1="15" x2="21" y2="65" stroke="var(--border-strong)" strokeWidth="1.5" />
        <circle className="svg-valve-gate" cx="21" cy="40" r="9" fill="var(--accent)" opacity="0.25" />
        <circle className="svg-valve-gate" cx="21" cy="40" r="7" stroke="var(--accent)" strokeWidth="1.5" />
        <text x="21" y="88" fill="var(--fg)" fontSize="7" textAnchor="middle">AuthFilter</text>
      </g>
      <g transform="translate(310, 92)">
        <rect width="55" height="36" rx="6" fill="color-mix(in oklab, var(--success) 10%, var(--surface))" stroke="var(--success)" strokeWidth="1.5" />
        <text x="27.5" y="22" fill="var(--success)" fontSize="9" fontWeight="800" textAnchor="middle">Controller</text>
      </g>
      <path className="svg-flow-arrow" d="M 65 110 H 310" stroke="var(--accent)" strokeWidth="2" strokeDasharray="6" />
    </svg>
  );
}

/**
 * SSE Token 串流傳輸 SVG 圖解
 */
function SvgSseStream() {
  return (
    <svg className="concept-svg-illustration" viewBox="0 0 400 220" fill="none" xmlns="http://www.w3.org/2000/svg">
      <rect width="400" height="220" rx="12" fill="var(--surface)" stroke="var(--border)" strokeWidth="1" />
      <g transform="translate(25, 80)">
        <rect width="85" height="60" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <text x="42.5" y="28" fill="var(--fg)" fontSize="10" fontWeight="800" textAnchor="middle">Spring AI</text>
        <text x="42.5" y="44" fill="var(--muted)" fontSize="8.5" textAnchor="middle">(後端 Server)</text>
      </g>
      <g transform="translate(290, 80)">
        <rect width="85" height="60" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <text x="42.5" y="28" fill="var(--fg)" fontSize="10" fontWeight="800" textAnchor="middle">React APP</text>
        <text x="42.5" y="44" fill="var(--muted)" fontSize="8.5" textAnchor="middle">(前端 Client)</text>
      </g>
      <rect x="110" y="95" width="180" height="30" rx="4" fill="none" stroke="var(--border-strong)" strokeWidth="1.5" strokeDasharray="4" />
      <text x="200" y="145" fill="var(--accent-deep)" fontSize="9" fontWeight="800" textAnchor="middle">Server-Sent Events (SSE) 串流</text>
      <g transform="translate(110, 95)">
        <circle className="svg-sse-bubble" cx="15" cy="15" r="7" fill="var(--accent)" opacity="0.85" />
        <circle className="svg-sse-bubble-2" cx="15" cy="15" r="6" fill="var(--accent)" opacity="0.85" />
        <circle className="svg-sse-bubble-3" cx="15" cy="15" r="5" fill="var(--accent)" opacity="0.85" />
      </g>
    </svg>
  );
}

/**
 * 開發環境準備與 AI Agent 協作圖解
 * 呈現 VS Code、JDK 21、Maven、Git、PowerShell 7+ 與 Docker/PG 六大工具支柱，
 * 以及底部的 AI Agent 如何與這些開發底盤進行互動、排錯與協作。
 */
function SvgEnvironmentPrep() {
  return (
    <svg className="concept-svg-illustration" viewBox="0 0 500 280" fill="none" xmlns="http://www.w3.org/2000/svg">
      {/* 背景圓角底座框 */}
      <rect width="500" height="280" rx="16" fill="var(--surface)" stroke="var(--border)" strokeWidth="1.2" />
      
      {/* 1. VS Code */}
      <g className="svg-flow-node" transform="translate(25, 30)">
        <rect width="135" height="50" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <text x="67.5" y="24" fill="var(--fg)" fontSize="10.5" fontWeight="800" textAnchor="middle">VS Code</text>
        <text x="67.5" y="38" fill="var(--muted)" fontSize="8.5" textAnchor="middle">(程式編輯與除錯)</text>
      </g>
      
      {/* 2. JDK 21 */}
      <g className="svg-flow-node" transform="translate(182, 30)">
        <rect width="135" height="50" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <text x="67.5" y="24" fill="var(--fg)" fontSize="10.5" fontWeight="800" textAnchor="middle">JDK 21 (LTS)</text>
        <text x="67.5" y="38" fill="var(--muted)" fontSize="8.5" textAnchor="middle">(Java 執行環境)</text>
      </g>
      
      {/* 3. Maven 3.9+ */}
      <g className="svg-flow-node" transform="translate(340, 30)">
        <rect width="135" height="50" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <text x="67.5" y="24" fill="var(--fg)" fontSize="10.5" fontWeight="800" textAnchor="middle">Maven 3.9+</text>
        <text x="67.5" y="38" fill="var(--muted)" fontSize="8.5" textAnchor="middle">(專案建置與依賴管理)</text>
      </g>
      
      {/* 4. Git */}
      <g className="svg-flow-node" transform="translate(25, 105)">
        <rect width="135" height="50" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <text x="67.5" y="24" fill="var(--fg)" fontSize="10.5" fontWeight="800" textAnchor="middle">Git</text>
        <text x="67.5" y="38" fill="var(--muted)" fontSize="8.5" textAnchor="middle">(版本控制與代碼管理)</text>
      </g>
      
      {/* 5. PowerShell 7+ */}
      <g className="svg-flow-node" transform="translate(182, 105)">
        <rect width="135" height="50" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <text x="67.5" y="24" fill="var(--fg)" fontSize="10.5" fontWeight="800" textAnchor="middle">PowerShell 7+</text>
        <text x="67.5" y="38" fill="var(--muted)" fontSize="8.5" textAnchor="middle">(跨平台命令行終端)</text>
      </g>
      
      {/* 6. Docker / PostgreSQL */}
      <g className="svg-flow-node" transform="translate(340, 105)">
        <rect width="135" height="50" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <text x="67.5" y="24" fill="var(--fg)" fontSize="10.5" fontWeight="800" textAnchor="middle">Docker / PostgreSQL</text>
        <text x="67.5" y="38" fill="var(--muted)" fontSize="8.5" textAnchor="middle">(資料庫與容器環境)</text>
      </g>
      
      {/* 底部中央：AI Agent 腦部 */}
      <g className="svg-agent-brain" transform="translate(250, 220)">
        <circle cx="0" cy="0" r="32" fill="var(--accent)" opacity="0.12" />
        <circle cx="0" cy="0" r="26" stroke="var(--accent)" strokeWidth="2" strokeDasharray="5" />
        <text x="0" y="4" fill="var(--accent-deep)" fontSize="10.5" fontWeight="800" textAnchor="middle">AI Agent</text>
      </g>
      
      {/* 工具與 AI Agent 之間的連線 */}
      <path d="M 92.5 155 L 218 220" stroke="var(--border-strong)" strokeWidth="1.2" strokeDasharray="3" />
      <path d="M 250 155 V 188" stroke="var(--border-strong)" strokeWidth="1.2" strokeDasharray="3" />
      <path d="M 407.5 155 L 282 220" stroke="var(--border-strong)" strokeWidth="1.2" strokeDasharray="3" />
    </svg>
  );
}

/**
 * Flyway 版本控制遷移軌跡圖解
 */
function SvgFlywayStrategy() {
  return (
    <svg className="concept-svg-illustration" viewBox="0 0 400 220" fill="none" xmlns="http://www.w3.org/2000/svg">
      <rect width="400" height="220" rx="12" fill="var(--surface)" stroke="var(--border)" strokeWidth="1" />
      <g className="svg-flyway-step" transform="translate(35, 75)">
        <circle className="svg-flyway-v" cx="20" cy="20" r="20" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <text x="20" y="24" fill="var(--fg)" fontSize="10" fontWeight="800" textAnchor="middle">V1</text>
        <text x="20" y="55" fill="var(--muted)" fontSize="8" textAnchor="middle">建立 Schema</text>
      </g>
      <path d="M 75 95 H 135" stroke="var(--border-strong)" strokeWidth="1.5" />
      <g className="svg-flyway-step" transform="translate(145, 75)">
        <circle className="svg-flyway-v" cx="20" cy="20" r="20" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <text x="20" y="24" fill="var(--fg)" fontSize="10" fontWeight="800" textAnchor="middle">V2</text>
        <text x="20" y="55" fill="var(--muted)" fontSize="8" textAnchor="middle">新增 Audit</text>
      </g>
      <path d="M 185 95 H 245" stroke="var(--accent)" strokeWidth="2" strokeDasharray="4" />
      <path className="svg-flow-arrow" d="M 185 95 H 245" stroke="var(--accent)" strokeWidth="2" />
      <g className="svg-flyway-step" transform="translate(255, 75)">
        <circle className="svg-flyway-v" cx="20" cy="20" r="22" fill="var(--accent)" opacity="0.1" />
        <circle className="svg-flyway-v" cx="20" cy="20" r="20" stroke="var(--accent)" strokeWidth="2.5" />
        <text x="20" y="24" fill="var(--accent-deep)" fontSize="11" fontWeight="800" textAnchor="middle">V3</text>
        <text x="20" y="55" fill="var(--accent-deep)" fontSize="8.5" fontWeight="800" textAnchor="middle">新增 Vector</text>
      </g>
    </svg>
  );
}

/**
 * JPA ORM 映射機制圖解
 */
function SvgJpaMapping() {
  return (
    <svg className="concept-svg-illustration" viewBox="0 0 400 220" fill="none" xmlns="http://www.w3.org/2000/svg">
      <rect width="400" height="220" rx="12" fill="var(--surface)" stroke="var(--border)" strokeWidth="1" />
      <g transform="translate(25, 40)">
        <rect width="130" height="130" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <text x="65" y="25" fill="var(--fg)" fontSize="10" fontWeight="800" textAnchor="middle">@Entity Product</text>
        <line x1="15" y1="35" x2="115" y2="35" stroke="var(--border-strong)" strokeWidth="1" />
        <text x="20" y="55" fill="var(--muted)" fontSize="9">private Long id;</text>
        <text x="20" y="75" fill="var(--muted)" fontSize="9">private String name;</text>
        <text x="20" y="95" fill="var(--muted)" fontSize="9">private Double price;</text>
      </g>
      <g className="svg-flow-node" transform="translate(175, 95)">
        <polygon points="0,5 20,5 20,0 35,10 20,20 20,15 0,15" fill="var(--accent)" />
        <text x="17" y="-10" fill="var(--accent-deep)" fontSize="9" fontWeight="800" textAnchor="middle">JPA Mapping</text>
      </g>
      <g transform="translate(245, 40)">
        <rect width="130" height="130" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <text x="65" y="25" fill="var(--fg)" fontSize="10" fontWeight="800" textAnchor="middle">Table: products</text>
        <line x1="15" y1="35" x2="115" y2="35" stroke="var(--border-strong)" strokeWidth="1" />
        <text x="20" y="55" fill="var(--muted)" fontSize="9">id : bigint (PK)</text>
        <text x="20" y="75" fill="var(--muted)" fontSize="9">name : varchar</text>
        <text x="20" y="95" fill="var(--muted)" fontSize="9">price : float8</text>
      </g>
    </svg>
  );
}

/**
 * Vite Dev Server Proxy 代理流向圖解
 */
function SvgViteProxy() {
  return (
    <svg className="concept-svg-illustration" viewBox="0 0 400 220" fill="none" xmlns="http://www.w3.org/2000/svg">
      <rect width="400" height="220" rx="12" fill="var(--surface)" stroke="var(--border)" strokeWidth="1" />
      <g transform="translate(20, 80)">
        <rect width="80" height="50" rx="6" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <text x="40" y="24" fill="var(--fg)" fontSize="9" fontWeight="800" textAnchor="middle">React 前端</text>
        <text x="40" y="38" fill="var(--muted)" fontSize="8" textAnchor="middle">localhost:5173</text>
      </g>
      <g className="svg-flow-node" transform="translate(145, 60)">
        <rect width="110" height="90" rx="8" fill="var(--accent)" opacity="0.1" />
        <rect width="110" height="90" rx="8" stroke="var(--accent)" strokeWidth="2" />
        <text x="55" y="35" fill="var(--accent-deep)" fontSize="10" fontWeight="800" textAnchor="middle">Vite Dev Server</text>
        <text x="55" y="55" fill="var(--accent-deep)" fontSize="10" fontWeight="800" textAnchor="middle">API Proxy</text>
        <text x="55" y="73" fill="var(--muted)" fontSize="8" textAnchor="middle">(避開 CORS 錯誤)</text>
      </g>
      <g transform="translate(295, 80)">
        <rect width="85" height="50" rx="6" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <text x="42.5" y="24" fill="var(--fg)" fontSize="9" fontWeight="800" textAnchor="middle">Spring 後端</text>
        <text x="42.5" y="38" fill="var(--muted)" fontSize="8" textAnchor="middle">localhost:8080</text>
      </g>
      <path className="svg-flow-arrow" d="M 100 105 H 145" stroke="var(--accent)" strokeWidth="1.5" />
      <path className="svg-flow-arrow" d="M 255 105 H 295" stroke="var(--accent)" strokeWidth="1.5" />
    </svg>
  );
}

/**
 * Embabel GOAP Agent Blackboard 決策圖解
 */
/**
 * Embabel GOAP Agent Blackboard 決策圖解
 * 呈現 Blackboard 世界狀態、GOAP Planner 規劃核心、Action Pool 行為工具箱，
 * 以及當風險分數超標時，Planner 如何進行 Replanning 轉向主管審核的分歧決策過程。
 */
function SvgAgentGoap() {
  return (
    <svg className="concept-svg-illustration" viewBox="0 0 500 240" fill="none" xmlns="http://www.w3.org/2000/svg">
      {/* 背景大框 */}
      <rect width="500" height="240" rx="16" fill="var(--surface)" stroke="var(--border)" strokeWidth="1.2" />
      
      {/* 1. 左側：Blackboard (黑板世界狀態) */}
      <g transform="translate(20, 30)">
        <rect width="110" height="180" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <text x="55" y="24" fill="var(--fg)" fontSize="11" fontWeight="800" textAnchor="middle">Blackboard</text>
        <line x1="10" y1="35" x2="100" y2="35" stroke="var(--border-strong)" strokeWidth="1" />
        
        {/* 世界狀態鍵值 */}
        <text x="15" y="55" fill="var(--muted)" fontSize="8.5">clientType: VIP</text>
        <text x="15" y="75" fill="var(--muted)" fontSize="8.5">riskScore: 85</text>
        <text x="15" y="95" fill="var(--accent-deep)" fontSize="8.5" fontWeight="700">highRisk: true</text>
        <text x="15" y="115" fill="var(--muted)" fontSize="8.5">hasRAG: true</text>
        <text x="15" y="135" fill="var(--muted)" fontSize="8.5">goal: APPROVED</text>
        <text x="15" y="155" fill="var(--muted)" fontSize="8.5">step: REVIEW</text>
      </g>
      
      {/* 連接線：Blackboard 狀態輸入至 Planner */}
      <path d="M 130 120 H 160" stroke="var(--border-strong)" strokeWidth="1.5" strokeDasharray="3" />
      
      {/* 2. 中間上：GOAP Planner 規劃引擎 */}
      <g className="svg-agent-brain" transform="translate(250, 60)">
        <circle cx="0" cy="0" r="30" fill="var(--accent)" opacity="0.12" />
        <circle cx="0" cy="0" r="25" stroke="var(--accent)" strokeWidth="2" strokeDasharray="4" />
        <text x="0" y="2" fill="var(--accent-deep)" fontSize="10" fontWeight="800" textAnchor="middle">GOAP</text>
        <text x="0" y="13" fill="var(--accent-deep)" fontSize="9" fontWeight="800" textAnchor="middle">Planner</text>
      </g>
      
      {/* 中間下：Action Pool (行為庫) */}
      <g transform="translate(160, 110)">
        <rect width="180" height="100" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <text x="90" y="18" fill="var(--fg)" fontSize="10" fontWeight="800" textAnchor="middle">Action Pool (行為庫)</text>
        
        {/* 行為節點網格 */}
        <g transform="translate(12, 28)">
          <rect width="72" height="26" rx="4" fill="var(--surface)" stroke="var(--border)" strokeWidth="1" />
          <text x="36" y="16" fill="var(--muted)" fontSize="8.5" textAnchor="middle">A1: Fetch</text>
        </g>
        <g transform="translate(96, 28)">
          <rect width="72" height="26" rx="4" fill="var(--surface)" stroke="var(--border)" strokeWidth="1" />
          <text x="36" y="16" fill="var(--muted)" fontSize="8.5" textAnchor="middle">A2: RAG</text>
        </g>
        <g transform="translate(12, 60)">
          <rect width="72" height="26" rx="4" fill="var(--surface)" stroke="var(--border)" strokeWidth="1" />
          <text x="36" y="16" fill="var(--muted)" fontSize="8.5" textAnchor="middle">A3: Draft</text>
        </g>
        <g transform="translate(96, 60)">
          <rect width="72" height="26" rx="4" fill="var(--accent)" opacity="0.1" />
          <rect width="72" height="26" rx="4" stroke="var(--accent)" strokeWidth="1.2" />
          <text x="36" y="16" fill="var(--accent-deep)" fontSize="8.5" fontWeight="700" textAnchor="middle">A4: Review</text>
        </g>
      </g>
      
      {/* 連接線：Planner 掃描行為庫 */}
      <path d="M 250 90 V 110" stroke="var(--accent)" strokeWidth="1.5" markerEnd="url(#arrow-down)" />
      
      {/* 3. 右側：決策分歧與 Replanning */}
      {/* 右上：直接產出 (失效灰色) */}
      <g transform="translate(370, 30)" opacity="0.4">
        <rect width="110" height="50" rx="8" fill="var(--surface-2)" stroke="var(--border)" strokeWidth="1" />
        <text x="55" y="24" fill="var(--muted)" fontSize="9.5" fontWeight="800" textAnchor="middle">直接產出建議</text>
        <text x="55" y="38" fill="var(--muted)" fontSize="8" textAnchor="middle">(Precondition 阻擋)</text>
      </g>
      
      {/* 右下：主管審核 (啟用高亮) */}
      <g className="svg-flow-node" transform="translate(370, 120)">
        <rect width="110" height="70" rx="8" fill="color-mix(in oklab, var(--accent) 8%, var(--surface))" stroke="var(--accent)" strokeWidth="2" />
        <text x="55" y="28" fill="var(--accent-deep)" fontSize="10.5" fontWeight="800" textAnchor="middle">主管審核</text>
        <text x="55" y="44" fill="var(--accent-deep)" fontSize="8.5" fontWeight="800" textAnchor="middle">(Replanning)</text>
        <text x="55" y="58" fill="var(--muted)" fontSize="8" textAnchor="middle">A* 重新尋路成功</text>
      </g>
      
      {/* 決策尋路指示線 */}
      <path d="M 340 160 H 370" stroke="var(--accent)" strokeWidth="2" className="svg-flow-arrow" />
      <path d="M 280 60 H 370" stroke="var(--border-strong)" strokeWidth="1.2" strokeDasharray="3" />
      
      <defs>
        <marker id="arrow-down" viewBox="0 0 10 10" refX="5" refY="5" markerWidth="4" markerHeight="4" orient="auto">
          <path d="M 0 0 L 5 5 L 0 10 z" fill="var(--accent)" />
        </marker>
      </defs>
    </svg>
  );
}

/**
 * RAG 檢索增強生成工作流圖解
 * 呈現使用者提問、向量化檢索、上下文 Prompt 合併以及 LLM 生成精準回答的完整開卷考試流程。
 */
function SvgRAGFlow() {
  return (
    <svg className="concept-svg-illustration" viewBox="0 0 500 240" fill="none" xmlns="http://www.w3.org/2000/svg">
      {/* 背景大底座 */}
      <rect width="500" height="240" rx="16" fill="var(--surface)" stroke="var(--border)" strokeWidth="1.2" />
      
      {/* 1. 使用者提問區 */}
      <g transform="translate(15, 80)">
        <circle cx="25" cy="25" r="20" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <text x="25" y="29" fill="var(--fg)" fontSize="14" textAnchor="middle">👤</text>
        <text x="25" y="60" fill="var(--fg)" fontSize="8.5" fontWeight="800" textAnchor="middle">User</text>
        <rect x="-10" y="70" width="75" height="30" rx="4" fill="var(--surface-2)" stroke="var(--border)" strokeWidth="1" />
        <text x="27.5" y="88" fill="var(--muted)" fontSize="7.5" textAnchor="middle">"退貨政策為何？"</text>
      </g>
      
      {/* 提問流向向量檢索 */}
      <path d="M 85 110 H 135" stroke="var(--accent)" strokeWidth="1.5" className="svg-flow-arrow" />
      
      {/* 2. 向量相似度檢索核心 */}
      <g transform="translate(135, 30)">
        <rect width="210" height="70" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <text x="105" y="18" fill="var(--fg)" fontSize="10" fontWeight="800" textAnchor="middle">pgvector 相似度檢索</text>
        
        {/* Cosine Similarity 示意 */}
        <line x1="15" y1="28" x2="195" y2="28" stroke="var(--border-strong)" strokeWidth="1" />
        <text x="20" y="44" fill="var(--accent-deep)" fontSize="8" fontWeight="700">Similarity Search (topK=3)</text>
        <text x="20" y="58" fill="var(--muted)" fontSize="7.5">比對 Cosine Distance &lt;= 0.15</text>
      </g>
      
      {/* 檢索出的 Context 流向 Prompt 合併 */}
      <path d="M 240 100 V 130" stroke="var(--border-strong)" strokeWidth="1.2" strokeDasharray="3" />
      
      {/* 3. Prompt 增強上下文 */}
      <g transform="translate(145, 130)">
        <rect width="190" height="75" rx="6" fill="color-mix(in oklab, var(--success) 8%, var(--surface))" stroke="var(--success)" strokeWidth="1.5" />
        <text x="95" y="18" fill="var(--success)" fontSize="9.5" fontWeight="800" textAnchor="middle">Prompt 上下文合併</text>
        <line x1="10" y1="26" x2="180" y2="26" stroke="var(--success)" strokeWidth="1" opacity="0.3" />
        <text x="15" y="40" fill="var(--fg)" fontSize="7.5">Context: "退貨政策：14天內憑發票..."</text>
        <text x="15" y="54" fill="var(--fg)" fontSize="7.5">Question: "退貨政策為何？"</text>
        <text x="15" y="68" fill="var(--muted)" fontSize="7.5" fontStyle="italic">(開卷考試防幻覺)</text>
      </g>
      
      {/* 增強 Prompt 送至 LLM */}
      <path d="M 335 167 H 375" stroke="var(--accent)" strokeWidth="1.5" className="svg-flow-arrow" />
      
      {/* 4. LLM 腦部與生成 */}
      <g className="svg-agent-brain" transform="translate(425, 110)">
        <circle cx="0" cy="0" r="30" fill="var(--accent)" opacity="0.12" />
        <circle cx="0" cy="0" r="25" stroke="var(--accent)" strokeWidth="2" strokeDasharray="4" />
        <text x="0" y="4" fill="var(--accent-deep)" fontSize="10.5" fontWeight="800" textAnchor="middle">LLM</text>
      </g>
      
      {/* LLM 與 Prompt 合併連線 */}
      <path d="M 425 140 L 335 167" stroke="var(--border-strong)" strokeWidth="1" strokeDasharray="3" />
      <path d="M 345 65 L 425 80" stroke="var(--border-strong)" strokeWidth="1" strokeDasharray="3" />
      
      {/* 回答流回 User */}
      <path d="M 425 140 C 425 220, 25 220, 25 190" stroke="var(--success)" strokeWidth="1.8" strokeDasharray="5" />
    </svg>
  );
}

/**
 * RAG ETL 資料清洗與寫入 Pipeline 圖解
 * 呈現 Extract (提取)、Transform (切分)、Load (載入) 與 Embedding 向量化的完整靜態入庫管線。
 */
function SvgEtlPipeline() {
  return (
    <svg className="concept-svg-illustration" viewBox="0 0 500 240" fill="none" xmlns="http://www.w3.org/2000/svg">
      <rect width="500" height="240" rx="16" fill="var(--surface)" stroke="var(--border)" strokeWidth="1.2" />
      
      {/* 1. Extract 階段 */}
      <g className="svg-flow-node" transform="translate(20, 85)">
        <rect width="120" height="65" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <text x="60" y="20" fill="var(--fg)" fontSize="10.5" fontWeight="800" textAnchor="middle">1. Extract (提取)</text>
        <text x="60" y="38" fill="var(--muted)" fontSize="8.5" textAnchor="middle">TextReader 讀取</text>
        <text x="60" y="52" fill="var(--muted)" fontSize="8" textAnchor="middle">txt / pdf / docx</text>
      </g>
      
      <path d="M 140 117 H 180" stroke="var(--border-strong)" strokeWidth="1.5" markerEnd="url(#arrow-right)" />
      
      {/* 2. Transform 階段 */}
      <g className="svg-flow-node" transform="translate(180, 85)">
        <rect width="130" height="65" rx="8" fill="var(--surface-2)" stroke="var(--border-strong)" strokeWidth="1.5" />
        <text x="65" y="20" fill="var(--fg)" fontSize="10.5" fontWeight="800" textAnchor="middle">2. Transform (切分)</text>
        <text x="65" y="38" fill="var(--accent-deep)" fontSize="8.5" fontWeight="700" textAnchor="middle">TokenTextSplitter</text>
        <text x="65" y="52" fill="var(--muted)" fontSize="8" textAnchor="middle">800 chars / 100 overlap</text>
      </g>
      
      <path d="M 310 117 H 350" stroke="var(--border-strong)" strokeWidth="1.5" markerEnd="url(#arrow-right)" />
      
      {/* 3. Embedding 向量化算力橋樑 */}
      <g className="svg-agent-brain" transform="translate(245, 30)">
        <circle cx="0" cy="0" r="20" fill="var(--accent)" opacity="0.12" />
        <circle cx="0" cy="0" r="16" stroke="var(--accent)" strokeWidth="1.5" strokeDasharray="3" />
        <text x="0" y="3.5" fill="var(--accent-deep)" fontSize="8.5" fontWeight="800" textAnchor="middle">Embedding</text>
      </g>
      <path d="M 245 85 V 50" stroke="var(--border-strong)" strokeWidth="1" strokeDasharray="3" />
      <path d="M 261 30 C 310 30, 360 65, 390 85" stroke="var(--accent)" strokeWidth="1.2" strokeDasharray="3" />
      
      {/* 4. Load 階段 */}
      <g className="svg-flow-node" transform="translate(350, 85)">
        <rect width="130" height="65" rx="8" fill="color-mix(in oklab, var(--success) 8%, var(--surface))" stroke="var(--success)" strokeWidth="2" />
        <text x="65" y="20" fill="var(--success)" fontSize="10.5" fontWeight="800" textAnchor="middle">3. Load (載入庫)</text>
        <text x="65" y="38" fill="var(--fg)" fontSize="8.5" fontWeight="800" textAnchor="middle">pgvector 寫入</text>
        <text x="65" y="52" fill="var(--muted)" fontSize="8" textAnchor="middle">Vector: 1536 Dimensions</text>
      </g>
      
      <defs>
        <marker id="arrow-right" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="4" markerHeight="4" orient="auto">
          <path d="M 0 0 L 5 5 L 0 10 z" fill="var(--border-strong)" />
        </marker>
      </defs>
    </svg>
  );
}

/**
 * 依據概念標題動態匹配並渲染精美 inline SVG 圖解。
 * 
 * @param {object} props 元件屬性
 * @param {string} props.heading 觀念的標題文字
 * @returns {React.ReactElement|null} 返回對應的 SVG 圖解或 null
 */
function ConceptVisual({ heading }) {
  if (!heading) return null;
  
  switch (heading.trim()) {
    case "環境準備重點":
      return <SvgEnvironmentPrep />;
    case "Spring MVC 的核心：請求如何流動":
      return <SvgSpringMvcFlow />;
    case "什麼是 REST API":
      return <SvgRestApi />;
    case "JPA 解決了什麼問題":
      return <SvgJpaMapping />;
    case "Flyway 的角色":
      return <SvgFlywayStrategy />;
    case "AOP 概念圖解":
      return <SvgAopConcept />;
    case "後端安全設定範例 (SecurityConfig.java)":
      return <SvgSecurityChain />;
    case "開發端代理與後端 API 串接 (Vite Proxy)":
      return <SvgViteProxy />;
    case "串流輸出為什麼重要":
      return <SvgSseStream />;
    case "GOAP規劃":
    case "Embabel 智慧 Agent、GOAP 演算法與 Blackboard 機制":
      return <SvgAgentGoap />;
    case "RAG 的基本想法":
      return <img src="assets/illustrations/rag_flow.png" className="concept-svg-illustration" alt="RAG 的基本想法" />;
    case "ETL 三步驟：文件到向量庫":
      return <img src="assets/illustrations/etl_pipeline.png" className="concept-svg-illustration" alt="ETL 三步驟：文件到向量庫" />;
    default:
      return null;
  }
}

/**
 * 顯示單一核心觀念內容卡片。
 * 原本使用 InlineMarkdown 會造成多行文字、列表與程式碼區塊 md 格式跑掉，
 * 現改用 parseMarkdown 配合 markdown-body 樣式進行完整 Markdown 解析渲染。
 * 
 * @param {object} props 元件屬性
 * @param {object} props.concept 單一概念的資料物件
 */
function Concept({ concept }) {
  const visualElement = ConceptVisual({ heading: concept.heading });

  return (
    <article className="concept">
      {concept.heading && <h5 className="concept-heading">{concept.heading}</h5>}
      
      {visualElement ? (
        <div className="concept-layout">
          <div className="concept-visual-col">
            {visualElement}
          </div>
          <div className="concept-text-col">
            {/* 核心觀念內容：採用完整的 parseMarkdown 進行多行、列表及程式碼區塊的格式解析 */}
            {concept.body && (
              <div className="concept-body markdown-body">
                {parseMarkdown(concept.body)}
              </div>
            )}
          </div>
        </div>
      ) : (
        concept.body && (
          <div className="concept-body markdown-body">
            {parseMarkdown(concept.body)}
          </div>
        )
      )}
      
      {concept.list?.length ? (
        <div className="kv">
          {concept.list.map(([key, value]) => (
            <div className="kv-row" key={key}>
              <div className="kv-key">{key}</div>
              <div className="kv-value"><InlineMarkdown text={value} /></div>
            </div>
          ))}
        </div>
      ) : null}
      
      {concept.table ? (
        <table className="data-table">
          <thead>
            <tr>
              {concept.table.head.map((head) => (
                <th key={head}><InlineMarkdown text={head} /></th>
              ))}
            </tr>
          </thead>
          <tbody>
            {concept.table.rows.map((row, rowIndex) => (
              <tr key={rowIndex}>
                {row.map((cell, cellIndex) => (
                  <td key={cellIndex}><InlineMarkdown text={cell} /></td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}
      
      {concept.note && (
        <div className="note">
          <InlineMarkdown text={concept.note} />
        </div>
      )}
    </article>
  );
}

/**
 * 顯示概念手風琴（橫向 Tabs 與 iOS 風格動態圓點步進器重構版）。
 * 當切換不同單元時，透過監聽 unit.id 的變化重置 activeTab 狀態為 0。
 * 
 * @param {object} props 元件屬性
 * @param {object} props.unit 課程單元資料
 * @param {boolean} props.open 是否預設展開手風琴
 */
function ConceptAccordion({ unit, open }) {
  // activeTab 用於追蹤當前點選的觀念分頁索引，預設為第一個 (0)
  const [activeTab, setActiveTab] = useState(0);

  // 當單元 (unit.id) 改變時，重設 activeTab 狀態，防止發生陣列越界
  useEffect(() => {
    setActiveTab(0);
  }, [unit.id]);

  if (!unit.concepts?.length) return null;

  return (
    <details className="accordion-item" open={open}>
      <summary className="accordion-summary">核心觀念與工程判斷</summary>
      <div className="accordion-body">
        {/* 橫向概念 Tabs 導覽列與上方快捷切換按鈕 */}
        <div className="concept-tabs-nav-wrapper">
          <button
            className="concept-tabs-nav-arrow"
            type="button"
            disabled={activeTab === 0}
            onClick={() => setActiveTab((prev) => prev - 1)}
            title="上一觀念"
            aria-label="上一觀念"
          >
            &larr;
          </button>
          
          <div className="concept-tabs">
            {unit.concepts.map((concept, index) => {
              // 優先使用 concept.heading 作為分頁名稱，否則以「觀念 N」呈現
              const tabTitle = concept.heading || `觀念 ${index + 1}`;
              return (
                <button
                  key={index}
                  className={`concept-tab-btn ${activeTab === index ? "active" : ""}`}
                  type="button"
                  onClick={() => setActiveTab(index)}
                >
                  {tabTitle}
                </button>
              );
            })}
          </div>

          <button
            className="concept-tabs-nav-arrow"
            type="button"
            disabled={activeTab === unit.concepts.length - 1}
            onClick={() => setActiveTab((prev) => prev + 1)}
            title="下一觀念"
            aria-label="下一觀念"
          >
            &rarr;
          </button>
        </div>

        {/* 概念內容卡片區域 */}
        <div className="concept-list">
          <Concept concept={unit.concepts[activeTab]} />
        </div>

        {/* 底部 iOS 風格步進器與頁碼指示點 */}
        <div className="concept-nav-footer">
          <button
            className="concept-nav-btn"
            type="button"
            disabled={activeTab === 0}
            onClick={() => setActiveTab((prev) => prev - 1)}
          >
            &larr; 上一觀念
          </button>
          
          <div className="concept-dots">
            {unit.concepts.map((_, index) => (
              <span
                key={index}
                className={`concept-dot ${activeTab === index ? "active" : ""}`}
                onClick={() => setActiveTab(index)}
                title={`跳至觀念 ${index + 1}`}
                role="button"
                aria-label={`跳至觀念 ${index + 1}`}
              />
            ))}
          </div>

          <button
            className="concept-nav-btn"
            type="button"
            disabled={activeTab === unit.concepts.length - 1}
            onClick={() => setActiveTab((prev) => prev + 1)}
          >
            下一觀念 &rarr;
          </button>
        </div>
      </div>
    </details>
  );
}

/**
 * 顯示可複製的 AI 協作提示詞。
 */
function PromptBox({ title, note, text }) {
  const [copied, setCopied] = useState(false);

  async function handleCopy() {
    await copyText(text);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1500);
  }

  return (
    <div className="prompt-box">
      <div className="prompt-head">
        <div>
          <strong>{title}</strong>
          {note ? <span>{note}</span> : null}
        </div>
        <button className={`prompt-copy-btn${copied ? " ok" : ""}`} type="button" data-copy="true" data-label="複製提示詞" onClick={handleCopy}>
          {copied ? "已複製" : "複製提示詞"}
        </button>
      </div>
      <pre><code>{text}</code></pre>
    </div>
  );
}

/**
 * 顯示單元提示詞手風琴，依平台（windows / mac）切換顯示對應版本的提示詞。
 * 在提示詞上方提供平台切換按鈕，供使用者直接切換。
 */
/**
 * 顯示單一提示詞手風琴，依平台（windows / mac）切換顯示對應版本的提示詞。
 * 在提示詞上方提供平台切換按鈕，供使用者直接切換。
 * 並且防範 prompts 陣列中已包含主範本時，重複渲染造成的重複顯示問題。
 * 
 * @param {object} props 元件屬性
 * @param {object} props.unit 課程單元資料
 * @param {boolean} props.open 是否預設展開
 * @param {string} props.platform 當前選取的作業系統平台 ("windows" | "mac")
 * @param {function} props.onTogglePlatform 切換平台狀態的回呼函式
 */
function PromptAccordion({ unit, open, platform, onTogglePlatform }) {
  // 依平台偏好選用有效提示詞：mac 且有 promptMac 時使用 macOS 版，否則使用預設 Windows 版
  const effectivePrompt = (platform === "mac" && unit.promptMac) ? unit.promptMac : unit.prompt;
  
  // 儲存待渲染之提示詞卡片物件清單
  const promptCards = [];

  // 檢查子提示詞是否含有佔位符。如果是，代表它本來只是 placeholder，應以完整的提示詞替換，且不需重複顯示頂層卡片。
  const hasPlaceholder = (unit.prompts || []).some(
    (p) => p.text && p.text.includes("(完整提示詞詳見")
  );

  if (hasPlaceholder) {
    (unit.prompts || []).forEach((p) => {
      const text = (p.text && p.text.includes("(完整提示詞詳見")) ? effectivePrompt : p.text;
      // 對 title 與 note 進行平台文字轉換
      const title = translatePlatformText(p.title, platform);
      const note = translatePlatformText(p.note, platform);
      promptCards.push({ ...p, title, note, text });
    });
  } else {
    // 檢查 unit.prompts 陣列中是否已經包含了主範本的文字 (防止重複顯示)
    const alreadyContainsEffective = (unit.prompts || []).some(
      (p) => p.text === unit.prompt || (p.title && p.title.includes("大師範本"))
    );

    // 如果 effectivePrompt 存在，且子 prompts 中尚未包含此主範本，才手動推入
    if (effectivePrompt && !alreadyContainsEffective) {
      const platformLabel = platform === "mac" && unit.promptMac ? "（macOS 版）" : "（Windows 版）";
      const title = translatePlatformText(`高級 AI 協作提示詞大師範本 ${platformLabel}`, platform);
      const note = translatePlatformText("可直接交給 Codex / Claude Code / Gemini 進行輔助開發", platform);
      promptCards.push({ title, note, text: effectivePrompt });
    }

    (unit.prompts || []).forEach((prompt) => {
      // 若子提示詞本身就是主範本，且已標記 alreadyContainsEffective，我們就直接在此轉換其為當前作業系統的版本，而不另外重複渲染
      if (alreadyContainsEffective && (prompt.text === unit.prompt || (prompt.title && prompt.title.includes("大師範本")))) {
        const platformLabel = platform === "mac" && unit.promptMac ? "（macOS 版）" : "（Windows 版）";
        const title = translatePlatformText(`高級 AI 協作提示詞大師範本 ${platformLabel}`, platform);
        const note = translatePlatformText("可直接交給 Codex / Claude Code / Gemini 進行輔助開發", platform);
        promptCards.push({ ...prompt, title, note, text: effectivePrompt });
      } else {
        const title = translatePlatformText(prompt.title, platform);
        const note = translatePlatformText(prompt.note, platform);
        // 如果子提示詞 text 剛好是預設的 Windows prompt，且使用者已切換成 Mac，則動態轉為 macOS 版本的 promptMac
        const text = (prompt.text === unit.prompt && platform === "mac" && unit.promptMac) ? unit.promptMac : prompt.text;
        promptCards.push({ ...prompt, title, note, text });
      }
    });
  }

  if (!promptCards.length) return null;

  return (
    <details className="accordion-item" open={open}>
      <summary className="accordion-summary">AI 協作提示詞</summary>
      <div className="accordion-body concept-list">
        {/* 新增的平台切換按鈕列，直接放在所有提示詞上方 */}
        <div className="prompt-platform-bar">
          <span className="prompt-platform-label">當前提示詞環境偏好：</span>
          <button 
            className={`prompt-platform-btn ${platform === "windows" ? "active" : ""}`}
            type="button"
            onClick={() => platform !== "windows" && onTogglePlatform()}
          >
            Windows
          </button>
          <button 
            className={`prompt-platform-btn ${platform === "mac" ? "active" : ""}`}
            type="button"
            onClick={() => platform !== "mac" && onTogglePlatform()}
          >
            macOS
          </button>
        </div>
        {promptCards.map((prompt, index) => (
          <PromptBox title={prompt.title} note={prompt.note} text={prompt.text} key={`${unit.id}-prompt-${index}`} />
        ))}
      </div>
    </details>
  );
}

/**
 * 顯示任務清單並同步進度狀態。
 * 根據當前平台 (Windows / macOS) 轉換任務文字描述（例如將 PowerShell 轉為 zsh）。
 */
function TaskList({ tasks, taskState, onToggleTask, platform }) {
  return (
    <div className="checklist">
      {(tasks || []).map((task) => {
        const checked = !!taskState[task.id];
        const textContent = translatePlatformText(task.label || task.text, platform);
        return (
          <label className={`check${checked ? " done" : ""}`} data-task-id={task.id} key={task.id}>
            <input type="checkbox" checked={checked} onChange={(event) => onToggleTask(task.id, event.target.checked)} />
            <span>{textContent}</span>
          </label>
        );
      })}
    </div>
  );
}

/**
 * 顯示教材素材清單。
 */
/**
 * 顯示教材素材清單，點選後會呼叫 onPreview 開啟 Modal 預覽。
 */
function Materials({ materials, onPreview }) {
  if (!materials?.length) return null;
  return (
    <div className="materials">
      {materials.map((material) => (
        <div className="material-row" key={material.id || material.name}>
          <span className="material-tag">{material.type || "MD"}</span>
          <div>
            <a 
              className="material-name" 
              href={`/materials/${material.name}.${(material.type || "MD").toLowerCase()}`} 
              onClick={(e) => {
                // 如果素材是 MD (Markdown)，攔截預設下載並開啟預覽視窗
                if ((material.type || "MD").toUpperCase() === "MD") {
                  e.preventDefault();
                  onPreview(material);
                }
              }}
            >
              {material.name}
            </a>
            {material.desc ? <div className="material-desc">{material.desc}</div> : null}
          </div>
        </div>
      ))}
    </div>
  );
}

/**
 * 顯示單元任務與教材素材。
 */
function TaskPanel({ unit, taskState, onToggleTask, onPreview, platform }) {
  return (
    <section className="task-panel">
      <h4>本章學習進度</h4>
      <TaskList tasks={unit.tasks || []} taskState={taskState} onToggleTask={onToggleTask} platform={platform} />
      {unit.materials?.length ? (
        <div className="content-block">
          <h4>本單元教學素材</h4>
          <Materials materials={unit.materials} onPreview={onPreview} />
        </div>
      ) : null}
    </section>
  );
}

/**
 * 顯示單一課程單元卡片。
 */
function UnitCard({ day, unit, index, taskState, onToggleTask, accordionOpen, searchQuery, onPreview, platform, onTogglePlatform }) {
  const unitId = `${day.id}-${unit.id}`;
  const total = unit.tasks?.length || 0;
  const done = (unit.tasks || []).filter((task) => taskState[task.id]).length;
  
  // 對搜尋文本及卡片顯示文字進行平台文字轉換，確保搜尋與顯示一致
  const titleText = translatePlatformText(unit.title, platform);
  const subtitleText = translatePlatformText(unit.subtitle || unit.principle, platform);
  
  const searchText = `${titleText} ${subtitleText} ${unit.prompt || ""}`.toLowerCase();
  const hidden = searchQuery && !(`${searchText} ${unit.principle || ""}`.toLowerCase().includes(searchQuery));

  return (
    <article className={`unit unit-card glass-card${hidden ? " hidden-by-search" : ""}`} id={unitId} data-search={searchText}>
      <span className="unit-kicker">Unit {index + 1} / {day.date}</span>
      <h4 className="unit-title">{titleText}</h4>
      <p className="unit-summary">{subtitleText}</p>
      <div className="unit-meta-row">
        <span className="unit-meta-chip">{unit.time}</span>
        <span className="unit-meta-chip unit-progress-pill">{done} / {total} 完成</span>
      </div>
      <UnitFeatureHighlight index={index} features={unit.features} />
      <Illustrations unit={unit} />
      {unit.principle ? (
        <div className="note">
          <strong>技術設計原則與核心心法</strong>
          <p>{translatePlatformText(unit.principle, platform)}</p>
        </div>
      ) : null}
      <div className="content-block">
        <h4>學習目標</h4>
        <GoalList goals={unit.goals || []} platform={platform} />
      </div>
      <div className="accordion">
        <ConceptAccordion unit={unit} open={accordionOpen} />
        <PromptAccordion unit={unit} open={accordionOpen} platform={platform} onTogglePlatform={onTogglePlatform} />
      </div>
      <TaskPanel unit={unit} taskState={taskState} onToggleTask={onToggleTask} onPreview={onPreview} platform={platform} />
    </article>
  );
}

/**
 * 顯示每日課程區塊。
 */
function DayBlock({ course, dayMeta, taskState, onToggleTask, accordionOpen, searchQuery, onPreview, platform, onTogglePlatform }) {
  const day = course[dayMeta.id];
  const allUnits = getAllUnits(course);
  if (!day) return null;
  return (
    <section className="day-block" id={day.id}>
      <div className="day-header glass-card">
        <div className="day-header-grid">
          <div className="day-numeral">D{dayMeta.n}</div>
          <div>
            <span className="eyebrow">{day.date} / {dayMeta.hours} 小時</span>
            <h3>{day.title}</h3>
            <p>{day.learningGoal}</p>
          </div>
        </div>
      </div>
      {day.units.map((unit) => (
        <UnitCard
          key={unit.id}
          day={day}
          unit={unit}
          index={allUnits.findIndex((item) => item.unit.id === unit.id)}
          taskState={taskState}
          onToggleTask={onToggleTask}
          accordionOpen={accordionOpen}
          searchQuery={searchQuery}
          onPreview={onPreview}
          platform={platform}
          onTogglePlatform={onTogglePlatform}
        />
      ))}
    </section>
  );
}

/**
 * 顯示全站教材素材總覽。
 */
function MaterialsOverview({ materials, onPreview }) {
  if (!materials?.length) return null;
  return (
    <section className="section glass-card" id="materials-overview">
      <div className="section-header">
        <h3>教學素材與講義下載總覽</h3>
        <p>彙整範例代碼、SQL 遷移檔、提示詞大綱與環境指引，讓每個單元都能回到同一套 AI CRM 專案驗證。</p>
      </div>
      <Materials materials={materials} onPreview={onPreview} />
    </section>
  );
}

/**
 * 顯示結訓單選測驗。
 */
function Quiz({ quizList, quizState, onAnswerQuiz }) {
  if (!quizList?.length) return null;

  function handleSubmit() {
    const score = quizList.filter((quiz) => quizState?.[quiz.id] === quiz.answer).length;
    const unanswered = quizList.filter((quiz) => quizState?.[quiz.id] == null).length;
    if (unanswered) {
      alert(`還有 ${unanswered} 題尚未作答。`);
      return;
    }
    alert(`答對 ${score} / ${quizList.length} 題。`);
  }

  return (
    <section className="section glass-card" id="quiz">
      <div className="section-header">
        <h3>結訓總體驗收（{quizList.length} 題單選）</h3>
        <p>完成所有實作任務後再作答，檢查自己是否理解每個技術在 AI CRM 系統中的責任邊界。</p>
      </div>
      <div className="quiz-container">
        {quizList.map((quiz, index) => (
          <article className="quiz-card" key={quiz.id}>
            <div className="quiz-question">{index + 1}. {quiz.q}</div>
            <div className="quiz-options">
              {quiz.options.map((option, optionIndex) => (
                <label className="quiz-option" key={option}>
                  <input
                    type="radio"
                    name={quiz.id}
                    checked={quizState?.[quiz.id] === optionIndex}
                    onChange={() => onAnswerQuiz(quiz.id, optionIndex)}
                  />
                  <span>{option}</span>
                </label>
              ))}
            </div>
          </article>
        ))}
      </div>
      <button className="quiz-submit-btn" type="button" onClick={handleSubmit}>送出測驗答案</button>
    </section>
  );
}

/**
 * 顯示側邊導覽與學習總進度。
 */
function Sidebar({ course, state, progress, onToggleNavGroup, onResetProgress, onHideSidebar }) {
  return (
    <aside className="sidebar">
      <button id="sidebar-close" className="sidebar-close-btn" type="button" title="收起選單" onClick={onHideSidebar}>x</button>
      <div className="brand">
        <small>Teaching Site</small>
        <h1 id="courseTitle">{course.meta.title}</h1>
        <p id="courseSummary">{course.meta.subtitle}</p>
      </div>
      <nav id="unitNav" aria-label="課程章節導覽">
        <a className="nav-link" href="#overview" data-target="overview"><strong>課程總覽</strong><span>定位與準備</span></a>
        <a className="nav-link" href="#feature-roadmap" data-target="feature-roadmap"><strong>功能藍圖</strong><span>各章節產出</span></a>
        <a className="nav-link" href="#shared-case" data-target="shared-case"><strong>AI CRM 情境</strong><span>共同案例</span></a>
        {course.meta.days.map((dayMeta) => {
          const day = course[dayMeta.id];
          if (!day) return null;
          const open = state.navGroups?.[day.id] !== false;
          return (
            <details className="nav-group" open={open} key={day.id} onToggle={(event) => onToggleNavGroup(day.id, event.currentTarget.open)}>
              <summary className="nav-group-header">
                <span className="nav-group-badge">{day.date}</span>
                <span className="nav-group-title">{day.title}</span>
              </summary>
              <div className="nav-group-body">
                {day.units.map((unit, index) => (
                  <a className="nav-chapter-link" href={`#${day.id}-${unit.id}`} data-target={`${day.id}-${unit.id}`} key={unit.id}>
                    <span className="nav-chapter-num">U{index + 1}</span>
                    <span className="nav-chapter-title">{unit.title}</span>
                  </a>
                ))}
              </div>
            </details>
          );
        })}
        <a className="nav-link" href="#materials-overview" data-target="materials-overview"><strong>素材總覽</strong><span>講義與附件</span></a>
        <a className="nav-link" href="#quiz" data-target="quiz"><strong>結訓測驗</strong><span>學習驗收</span></a>
      </nav>
      <div className="sidebar-tools">
        <div className="progress-box">
          <strong>全課學習進度</strong>
          <p id="progressText">{progress.done} / {progress.total} 任務已完成 ({progress.percent}%)</p>
          <div className="progress-track" aria-hidden="true"><div id="progressFill" className="progress-fill" style={{ width: `${progress.percent}%` }} /></div>
        </div>
        <button id="resetProgress" className="tool-button" type="button" onClick={onResetProgress}>清除學習進度</button>
      </div>
    </aside>
  );
}

/**
 * 根元件：管理 React 19 版教學網站的全域狀態與互動。
 */
export default function App({ course }) {
  const [state, setState] = useState(() => loadState());
  const [searchQuery, setSearchQuery] = useState("");
  const [accordionOpen, setAccordionOpen] = useState(true);
  const [previewMaterial, setPreviewMaterial] = useState(null); // 教材預覽狀態
  const allUnits = useMemo(() => getAllUnits(course), [course]);
  const allTasks = useMemo(() => allUnits.flatMap(({ unit }) => unit.tasks || []), [allUnits]);
  const progress = useMemo(() => {
    const done = allTasks.filter((task) => state.tasks?.[task.id]).length;
    const total = allTasks.length;
    return { done, total, percent: total ? Math.round((done / total) * 100) : 0 };
  }, [allTasks, state.tasks]);

  useEffect(() => {
    document.documentElement.setAttribute("data-theme", state.theme || "light");
    document.body.classList.toggle("sidebar-hidden", !!state.sidebarHidden);
    saveState(state);
  }, [state]);

  useEffect(() => {
    const links = Array.from(document.querySelectorAll(".nav-link, .nav-chapter-link"));
    const sections = links.map((link) => document.getElementById(link.getAttribute("data-target"))).filter(Boolean);
    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        const activeId = entry.target.id;
        links.forEach((link) => link.classList.toggle("active", link.getAttribute("data-target") === activeId));
      });
    }, { rootMargin: "-20% 0px -60% 0px", threshold: 0.08 });
    sections.forEach((section) => observer.observe(section));
    return () => observer.disconnect();
  }, [course]);

  function patchState(patch) {
    setState((current) => {
      const next = typeof patch === "function" ? patch(current) : { ...current, ...patch };
      return next;
    });
  }

  function toggleTheme() {
    patchState((current) => ({ ...current, theme: current.theme === "dark" ? "light" : "dark" }));
  }

  function toggleSidebar(hidden) {
    document.body.classList.remove("sidebar-visible-mobile");
    patchState((current) => ({ ...current, sidebarHidden: hidden }));
  }

  function restoreSidebar() {
    if (window.innerWidth <= 1180) {
      document.body.classList.toggle("sidebar-visible-mobile");
      return;
    }
    toggleSidebar(false);
  }

  function toggleTask(taskId, checked) {
    patchState((current) => {
      const tasks = { ...(current.tasks || {}) };
      if (checked) tasks[taskId] = true;
      else delete tasks[taskId];
      return { ...current, tasks };
    });
  }

  function answerQuiz(quizId, optionIndex) {
    patchState((current) => ({ ...current, quiz: { ...(current.quiz || {}), [quizId]: optionIndex } }));
  }

  function resetProgress() {
    patchState((current) => ({ ...current, tasks: {} }));
  }

  function toggleNavGroup(dayId, open) {
    patchState((current) => ({ ...current, navGroups: { ...(current.navGroups || {}), [dayId]: open } }));
  }

  // 切換開發平台偏好：windows ↔ mac，影響提示詞顯示語言
  function togglePlatform() {
    patchState((current) => ({ ...current, platform: current.platform === "mac" ? "windows" : "mac" }));
  }

  const normalizedSearch = searchQuery.trim().toLowerCase();

  return (
    <>
      <button id="sidebar-restore" className="sidebar-restore-btn" type="button" onClick={restoreSidebar}>選單</button>
      <div className="top-actions">
        <input id="searchInput" className="search" type="search" placeholder="搜尋單元、觀念或 AI 提示詞..." value={searchQuery} onChange={(event) => setSearchQuery(event.target.value)} />
        <button id="expandAll" className="top-action-button" type="button" onClick={() => setAccordionOpen((open) => !open)}>{accordionOpen ? "收合全部" : "展開全部"}</button>
        {/* 平台切換按鈕已移至各單元提示詞上方，Header 處不再顯示 */}
        <button id="themeToggle" className="top-theme-toggle" type="button" onClick={toggleTheme}>{state.theme === "dark" ? "切換淺色模式" : "切換深色模式"}</button>
      </div>
      <div className="shell">
        <Sidebar course={course} state={state} progress={progress} onToggleNavGroup={toggleNavGroup} onResetProgress={resetProgress} onHideSidebar={() => toggleSidebar(true)} />
        <main className="main">
          <div id="heroRoot"><Hero meta={course.meta} /></div>
          <div id="content" className="content">
            <Overview meta={course.meta} />
            <FeatureRoadmap course={course} />
            <SharedCase sharedCase={course.sharedCase} />
            {course.meta.days.map((dayMeta) => (
              <DayBlock
                course={course}
                dayMeta={dayMeta}
                taskState={state.tasks || {}}
                onToggleTask={toggleTask}
                accordionOpen={accordionOpen}
                searchQuery={normalizedSearch}
                onPreview={setPreviewMaterial}
                platform={state.platform || "windows"}
                onTogglePlatform={togglePlatform}
                key={dayMeta.id}
              />
            ))}
            <MaterialsOverview materials={course.materials} onPreview={setPreviewMaterial} />
            <Quiz quizList={course.quiz} quizState={state.quiz || {}} onAnswerQuiz={answerQuiz} />
            <footer className="site-footer">教學網站已改用 React 19 + Vite 呈現，課程資料仍由 course-data.js 驅動。</footer>
          </div>
        </main>
      </div>
      <MaterialPreviewModal material={previewMaterial} onClose={() => setPreviewMaterial(null)} />
    </>
  );
}
