/* @ds-bundle: {"format":3,"namespace":"DesignSystem_55f34f","components":[{"name":"Button","sourcePath":"components/buttons/Button.jsx"},{"name":"Accordion","sourcePath":"components/feedback/Accordion.jsx"},{"name":"ProgressBar","sourcePath":"components/feedback/ProgressBar.jsx"},{"name":"Badge","sourcePath":"components/labels/Badge.jsx"},{"name":"Eyebrow","sourcePath":"components/labels/Eyebrow.jsx"},{"name":"Callout","sourcePath":"components/surfaces/Callout.jsx"},{"name":"Card","sourcePath":"components/surfaces/Card.jsx"},{"name":"Stat","sourcePath":"components/surfaces/Stat.jsx"}],"sourceHashes":{"components/buttons/Button.jsx":"b09aff93a7c8","components/feedback/Accordion.jsx":"9148c5efdd53","components/feedback/ProgressBar.jsx":"d296604fe1f2","components/labels/Badge.jsx":"9e49cb4dd567","components/labels/Eyebrow.jsx":"267890120d94","components/surfaces/Callout.jsx":"f7edb0d683b2","components/surfaces/Card.jsx":"a4d427b4d4f1","components/surfaces/Stat.jsx":"14d2c9a88ee1","ui_kits/hahow-course/App.jsx":"b3ba208726b0","ui_kits/hahow-course/data.js":"bc18ce13ba04","ui_kits/hahow-course/icons.jsx":"5593a8824798","ui_kits/hahow-course/parts1.jsx":"03a3aaff857e","ui_kits/hahow-course/parts2.jsx":"d83ea0d7a8f7","ui_kits/hahow-course/parts3.jsx":"7d329a02fa51","ui_kits/hahow-course/parts4.jsx":"2970a1a31d63"},"inlinedExternals":[],"unexposedExports":[]} */

(() => {

const __ds_ns = (window.DesignSystem_55f34f = window.DesignSystem_55f34f || {});

const __ds_scope = {};

(__ds_ns.__errors = __ds_ns.__errors || []);

// components/buttons/Button.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * 凱文大叔 Button — rounded, friendly pill button.
 * Variants: primary (solid accent), secondary (outline), ghost (subtle), light (white-on-dark).
 */
function Button({
  children,
  variant = "primary",
  size = "md",
  disabled = false,
  iconLeft = null,
  iconRight = null,
  as = "button",
  href,
  onClick,
  style = {},
  ...rest
}) {
  const sizes = {
    sm: {
      minHeight: 38,
      padding: "0 16px",
      fontSize: "0.88rem"
    },
    md: {
      minHeight: 46,
      padding: "0 20px",
      fontSize: "1rem"
    },
    lg: {
      minHeight: 54,
      padding: "0 28px",
      fontSize: "1.06rem"
    }
  };
  const variants = {
    primary: {
      background: "var(--accent)",
      color: "#fff",
      border: "1px solid var(--accent)"
    },
    secondary: {
      background: "var(--surface)",
      color: "var(--fg)",
      border: "1px solid var(--border-strong)"
    },
    ghost: {
      background: "transparent",
      color: "var(--accent-deep)",
      border: "1px solid transparent"
    },
    light: {
      background: "#fff",
      color: "var(--accent-deep)",
      border: "1px solid #fff"
    }
  };
  const base = {
    display: "inline-flex",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    borderRadius: "var(--r-pill)",
    fontFamily: "var(--font-main)",
    fontWeight: 800,
    textDecoration: "none",
    cursor: disabled ? "not-allowed" : "pointer",
    opacity: disabled ? 0.5 : 1,
    transition: "transform var(--dur-fast) var(--ease-standard), box-shadow var(--dur-fast) var(--ease-standard), filter var(--dur-fast)",
    whiteSpace: "nowrap",
    ...sizes[size],
    ...variants[variant],
    ...style
  };
  const Tag = href ? "a" : as;
  return /*#__PURE__*/React.createElement(Tag, _extends({
    href: href,
    onClick: disabled ? undefined : onClick,
    "aria-disabled": disabled || undefined,
    style: base,
    onMouseEnter: e => {
      if (disabled) return;
      e.currentTarget.style.transform = "translateY(-1px)";
      e.currentTarget.style.boxShadow = "var(--shadow-lift)";
      e.currentTarget.style.filter = "brightness(1.04)";
    },
    onMouseLeave: e => {
      e.currentTarget.style.transform = "";
      e.currentTarget.style.boxShadow = "";
      e.currentTarget.style.filter = "";
    }
  }, rest), iconLeft, children, iconRight);
}
Object.assign(__ds_scope, { Button });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/buttons/Button.jsx", error: String((e && e.message) || e) }); }

// components/feedback/Accordion.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * Accordion — a native <details> disclosure used for FAQ and curriculum.
 * The summary shows a + that rotates to × when open.
 */
function Accordion({
  summary,
  children,
  defaultOpen = false,
  style = {},
  ...rest
}) {
  return /*#__PURE__*/React.createElement("details", _extends({
    open: defaultOpen,
    style: {
      overflow: "hidden",
      border: "1px solid var(--border)",
      borderRadius: "var(--r-lg)",
      background: "color-mix(in oklab, var(--surface) 90%, transparent)",
      ...style
    }
  }, rest), /*#__PURE__*/React.createElement("summary", {
    style: {
      display: "flex",
      alignItems: "center",
      justifyContent: "space-between",
      gap: 16,
      padding: "18px 22px",
      cursor: "pointer",
      listStyle: "none",
      fontSize: "1.04rem",
      fontWeight: 800,
      color: "var(--fg)"
    }
  }, /*#__PURE__*/React.createElement("span", null, summary), /*#__PURE__*/React.createElement("span", {
    className: "acc-plus",
    "aria-hidden": "true",
    style: {
      color: "var(--accent-deep)",
      fontSize: "1.35rem",
      lineHeight: 1,
      transition: "transform var(--dur-fast) var(--ease-standard)"
    }
  }, "+")), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: "0 22px 20px",
      color: "var(--muted)",
      lineHeight: "var(--leading-loose)"
    }
  }, children), /*#__PURE__*/React.createElement("style", null, `details[open] > summary .acc-plus { transform: rotate(45deg); } summary::-webkit-details-marker { display: none; }`));
}
Object.assign(__ds_scope, { Accordion });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/feedback/Accordion.jsx", error: String((e && e.message) || e) }); }

// components/feedback/ProgressBar.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * ProgressBar — gradient fill track (accent → amber), used for course
 * progress and fundraising/enrolment goals.
 */
function ProgressBar({
  value = 0,
  max = 100,
  label,
  showValue = false,
  height = 9,
  style = {},
  ...rest
}) {
  const pct = Math.max(0, Math.min(100, value / max * 100));
  return /*#__PURE__*/React.createElement("div", _extends({
    style: {
      ...style
    }
  }, rest), (label || showValue) && /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      justifyContent: "space-between",
      marginBottom: 8,
      fontSize: "0.9rem",
      fontWeight: 800,
      color: "var(--fg)"
    }
  }, /*#__PURE__*/React.createElement("span", null, label), showValue ? /*#__PURE__*/React.createElement("span", {
    style: {
      color: "var(--accent-deep)"
    }
  }, Math.round(pct), "%") : null), /*#__PURE__*/React.createElement("div", {
    style: {
      height,
      overflow: "hidden",
      borderRadius: "var(--r-pill)",
      background: "var(--surface-3)"
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      width: `${pct}%`,
      height: "100%",
      borderRadius: "inherit",
      background: "linear-gradient(90deg, var(--accent), var(--accent-2))",
      transition: "width var(--dur-slow) var(--ease-out)"
    }
  })));
}
Object.assign(__ds_scope, { ProgressBar });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/feedback/ProgressBar.jsx", error: String((e && e.message) || e) }); }

// components/labels/Badge.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * Badge / Tag — small status or category label.
 * `tone` maps to the semantic palette; `solid` fills with the tone color.
 */
function Badge({
  children,
  tone = "accent",
  solid = false,
  style = {},
  ...rest
}) {
  const map = {
    accent: ["var(--accent-soft)", "var(--accent-deep)", "var(--accent)"],
    amber: ["var(--accent-2-soft)", "var(--accent-2-deep)", "var(--accent-2)"],
    success: ["var(--success-soft)", "var(--success)", "var(--success)"],
    warning: ["var(--warning-soft)", "var(--accent-2-deep)", "var(--warning)"],
    danger: ["var(--danger-soft)", "var(--danger)", "var(--danger)"],
    neutral: ["var(--surface-3)", "var(--muted)", "var(--muted)"]
  };
  const [soft, fg, strong] = map[tone] || map.accent;
  return /*#__PURE__*/React.createElement("span", _extends({
    style: {
      display: "inline-flex",
      alignItems: "center",
      gap: 6,
      padding: "4px 10px",
      borderRadius: "var(--r-pill)",
      fontSize: "0.78rem",
      fontWeight: 900,
      letterSpacing: "0.02em",
      background: solid ? strong : soft,
      color: solid ? "#fff" : fg,
      ...style
    }
  }, rest), children);
}
Object.assign(__ds_scope, { Badge });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/labels/Badge.jsx", error: String((e && e.message) || e) }); }

// components/labels/Eyebrow.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * Eyebrow — the brand's signature amber pill kicker that sits above headings.
 * Defaults to a leading ★. Pass `tone="accent"` for the blue variant.
 */
function Eyebrow({
  children,
  tone = "amber",
  icon = "★",
  style = {},
  ...rest
}) {
  const tones = {
    amber: {
      background: "var(--accent-2-soft)",
      color: "var(--accent-2-deep)"
    },
    accent: {
      background: "var(--accent-soft)",
      color: "var(--accent-deep)"
    }
  };
  return /*#__PURE__*/React.createElement("span", _extends({
    style: {
      display: "inline-flex",
      alignItems: "center",
      gap: 8,
      width: "fit-content",
      padding: "6px 12px",
      borderRadius: "var(--r-pill)",
      fontSize: "0.86rem",
      fontWeight: 900,
      letterSpacing: "0.03em",
      ...tones[tone],
      ...style
    }
  }, rest), icon ? /*#__PURE__*/React.createElement("span", {
    "aria-hidden": "true"
  }, icon) : null, children);
}
Object.assign(__ds_scope, { Eyebrow });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/labels/Eyebrow.jsx", error: String((e && e.message) || e) }); }

// components/surfaces/Callout.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * Callout — a bordered note block with an optional title and a left accent bar.
 * tone tints the left bar + soft background.
 */
function Callout({
  children,
  title,
  tone = "accent",
  style = {},
  ...rest
}) {
  const map = {
    accent: ["var(--accent)", "color-mix(in oklab, var(--accent) 8%, transparent)"],
    amber: ["var(--accent-2)", "color-mix(in oklab, var(--accent-2) 10%, transparent)"],
    success: ["var(--success)", "var(--success-soft)"],
    warning: ["var(--warning)", "var(--warning-soft)"],
    danger: ["var(--danger)", "var(--danger-soft)"]
  };
  const [bar, bg] = map[tone] || map.accent;
  return /*#__PURE__*/React.createElement("div", _extends({
    style: {
      padding: "16px 18px",
      borderRadius: "var(--r-md)",
      border: "1px solid var(--border)",
      borderLeft: `4px solid ${bar}`,
      background: bg,
      color: "var(--muted)",
      lineHeight: "var(--leading-body)",
      ...style
    }
  }, rest), title ? /*#__PURE__*/React.createElement("strong", {
    style: {
      display: "block",
      marginBottom: 8,
      color: "var(--fg)",
      fontWeight: 800
    }
  }, title) : null, children);
}
Object.assign(__ds_scope, { Callout });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/surfaces/Callout.jsx", error: String((e && e.message) || e) }); }

// components/surfaces/Card.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * Card — the base surface container.
 * variant: "solid" (opaque surface), "glass" (frosted backdrop-blur),
 * "sunken" (surface-2). Hover lift is opt-in via `hover`.
 */
function Card({
  children,
  variant = "solid",
  hover = false,
  padding = 24,
  style = {},
  ...rest
}) {
  const variants = {
    solid: {
      background: "var(--surface)",
      border: "1px solid var(--border)"
    },
    sunken: {
      background: "color-mix(in oklab, var(--surface-2) 88%, transparent)",
      border: "1px solid var(--border)"
    },
    glass: {
      background: "var(--glass-fill)",
      border: "1px solid color-mix(in oklab, var(--border) 90%, transparent)",
      backdropFilter: "var(--glass-blur)",
      WebkitBackdropFilter: "var(--glass-blur)"
    }
  };
  return /*#__PURE__*/React.createElement("div", _extends({
    style: {
      borderRadius: "var(--r-lg)",
      boxShadow: "var(--shadow-lift)",
      padding,
      transition: "transform var(--dur-fast) var(--ease-standard), border-color var(--dur-fast)",
      ...variants[variant],
      ...style
    },
    onMouseEnter: e => {
      if (!hover) return;
      e.currentTarget.style.transform = "translateY(-3px)";
      e.currentTarget.style.borderColor = "var(--accent)";
    },
    onMouseLeave: e => {
      if (!hover) return;
      e.currentTarget.style.transform = "";
      e.currentTarget.style.borderColor = "";
    }
  }, rest), children);
}
Object.assign(__ds_scope, { Card });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/surfaces/Card.jsx", error: String((e && e.message) || e) }); }

// components/surfaces/Stat.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * Stat — a number/label metric tile. `label` is the uppercase caption,
 * `value` the big figure, `hint` the optional sub-line.
 */
function Stat({
  label,
  value,
  hint,
  tone = "accent",
  style = {},
  ...rest
}) {
  const color = {
    accent: "var(--accent)",
    amber: "var(--accent-2-deep)",
    success: "var(--success)",
    fg: "var(--fg)"
  }[tone] || "var(--accent)";
  return /*#__PURE__*/React.createElement("div", _extends({
    style: {
      padding: 20,
      borderRadius: "var(--r-md)",
      border: "1px solid var(--border)",
      background: "var(--surface)",
      ...style
    }
  }, rest), label ? /*#__PURE__*/React.createElement("small", {
    style: {
      display: "block",
      marginBottom: 8,
      color: "var(--muted-2)",
      fontSize: "0.78rem",
      fontWeight: 800,
      letterSpacing: "0.06em",
      textTransform: "uppercase"
    }
  }, label) : null, /*#__PURE__*/React.createElement("strong", {
    style: {
      display: "block",
      fontSize: "2rem",
      fontWeight: 900,
      lineHeight: 1,
      color
    }
  }, value), hint ? /*#__PURE__*/React.createElement("span", {
    style: {
      display: "block",
      marginTop: 8,
      color: "var(--muted)",
      fontSize: "0.92rem"
    }
  }, hint) : null);
}
Object.assign(__ds_scope, { Stat });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/surfaces/Stat.jsx", error: String((e && e.message) || e) }); }

// ui_kits/hahow-course/App.jsx
try { (() => {
// App composition + enrol toast + mount
function Toast({
  show
}) {
  return /*#__PURE__*/React.createElement("div", {
    style: {
      position: "fixed",
      left: "50%",
      bottom: show ? 28 : -80,
      transform: "translateX(-50%)",
      transition: "bottom var(--dur-med) var(--ease-out)",
      zIndex: 200,
      display: "flex",
      alignItems: "center",
      gap: 12,
      padding: "14px 22px",
      borderRadius: "var(--r-pill)",
      background: "var(--fg)",
      color: "var(--bg)",
      boxShadow: "var(--shadow-lift)",
      fontWeight: 800
    }
  }, /*#__PURE__*/React.createElement(window.Icon, {
    name: "party-popper",
    size: 20
  }), "\u5DF2\u52A0\u5165\u8CFC\u7269\u8ECA \xB7 \u65E9\u9CE5\u50F9\u5DF2\u70BA\u4F60\u9396\u5B9A\uFF01");
}
function App() {
  const [toast, setToast] = React.useState(false);
  const enroll = React.useCallback(() => {
    setToast(true);
    window.clearTimeout(window.__t);
    window.__t = window.setTimeout(() => setToast(false), 2600);
  }, []);
  return /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(window.Header, {
    onEnroll: enroll
  }), /*#__PURE__*/React.createElement(window.Hero, null), /*#__PURE__*/React.createElement("div", {
    className: "hc-body",
    style: {
      maxWidth: 1180,
      margin: "0 auto",
      padding: "clamp(32px,5vw,56px) clamp(16px,4vw,40px)",
      display: "grid",
      gridTemplateColumns: "minmax(0,1fr) 360px",
      gap: 40,
      alignItems: "start"
    }
  }, /*#__PURE__*/React.createElement("main", {
    style: {
      display: "grid",
      gap: "clamp(40px, 5vw, 64px)",
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement(window.Intro, null), /*#__PURE__*/React.createElement(window.Features, null), /*#__PURE__*/React.createElement(window.Audience, null), /*#__PURE__*/React.createElement(window.ProjectShowcase, null), /*#__PURE__*/React.createElement(window.Curriculum, null), /*#__PURE__*/React.createElement(window.Instructor, null), /*#__PURE__*/React.createElement(window.Faq, null), /*#__PURE__*/React.createElement(window.PricingCTA, {
    onEnroll: enroll
  })), /*#__PURE__*/React.createElement("aside", {
    className: "hc-aside"
  }, /*#__PURE__*/React.createElement(window.PurchaseCard, {
    onEnroll: enroll
  }))), /*#__PURE__*/React.createElement(window.Footer, null), /*#__PURE__*/React.createElement(Toast, {
    show: toast
  }));
}
ReactDOM.createRoot(document.getElementById("root")).render(/*#__PURE__*/React.createElement(App, null));
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/hahow-course/App.jsx", error: String((e && e.message) || e) }); }

// ui_kits/hahow-course/data.js
try { (() => {
// 凱文大叔 — AI 賦能全端開發 · Hahow 課程銷售頁資料
// 內容整理自課程內容.md（kevintsai1202/ai-full-stack）
window.COURSE = {
  titleZh: "AI 賦能全端開發：從零打造企業級智慧應用",
  titleEn: "AI-Empowered Full-Stack: Building Enterprise-Grade Smart Apps",
  tagline: "用一套 AI CRM 實戰專案，把 Spring Boot、Spring AI 與 React 串成可上線的企業級智慧應用。",
  instructor: "凱文大叔 Uncle Kevin",
  pricing: {
    early: 3680,
    list: 5200,
    goalPct: 72,
    goalLabel: "募資目標達成率",
    backers: 1284,
    daysLeft: 18
  },
  outcomes: [{
    t: "從零搭起全端骨架",
    d: "建立 Spring Boot + React 的 AI CRM 專案，前後端分離、可啟動可驗證。"
  }, {
    t: "企業級後端分層",
    d: "用 Spring MVC 設計客戶、商機、互動紀錄與任務管理 API，分層清楚。"
  }, {
    t: "可維護的資料模型",
    d: "用 Spring Data JPA、Specification 與 Flyway 建立可遷移的資料庫。"
  }, {
    t: "登入與權限控管",
    d: "用 Spring Security + JWT 完成登入、角色與 API 授權保護。"
  }, {
    t: "可串流的 AI 助理",
    d: "用 Spring AI ChatClient + SSE 打造 token-by-token 回應的 CRM 助理。"
  }, {
    t: "讓 AI 讀真實資料",
    d: "用 tool calling 與 pgvector RAG，讓 AI 引用文件、不憑空亂答。"
  }],
  features: [{
    icon: "layers",
    t: "一條產品開發路線",
    d: "不是把多門課拼在一起，而是用同一套 AI CRM 範例，把每個技術放進同一條開發路線。"
  }, {
    icon: "shield-check",
    t: "技術由工具算、文字由模型寫",
    d: "所有金額、訂單、權限由 Java domain service 提供；LLM 只負責摘要、分類、建議與草稿。"
  }, {
    icon: "bot",
    t: "AI Agent 協作開發",
    d: "每單元都附 AI 提示詞主題：先讀需求、再產程式、最後驗證，產出可測試可維運的程式碼。"
  }, {
    icon: "git-branch",
    t: "章章相連、產出累積",
    d: "每一章的產出都成為下一章的基礎，避免只學到零散範例。"
  }],
  audience: ["已具備 Java 基礎，想把 Spring Boot、React 與 AI 串成完整產品的工程師。", "會寫基本 REST API，但還不熟企業級分層、權限、錯誤處理與前後端整合的開發者。", "想理解 Spring AI 與 RAG 如何落地在真實 JVM 系統，而不只做聊天機器人 demo 的後端工程師。", "想用 AI Agent 協作開發，但希望產出可測試、可維運、可上線程式碼的全端工程師。"],
  stack: ["Java 21", "Spring Boot 4", "Spring MVC", "Spring Data JPA", "PostgreSQL + pgvector", "Flyway", "Spring Security + JWT", "SpringDoc OpenAPI", "Spring AI 2.0", "React + TypeScript", "Vite"],
  units: [{
    n: "01",
    t: "開發環境、專案骨架與 AI 協作流程",
    out: "可啟動的 full-stack monorepo",
    tags: ["環境建置", "Monorepo", "AI 協作"],
    points: ["JDK 21 / Maven / Node / Docker 安裝與驗證", "Spring Initializr + Vite 建立前後端", "後端 /api/health、前端健康狀態紅綠燈", "第一份 AI Agent 提示詞：檢查本機環境"]
  }, {
    n: "02",
    t: "Spring MVC、REST API 與 CRM Domain Modeling",
    out: "客戶與互動紀錄 API",
    tags: ["Spring MVC", "REST", "DTO"],
    points: ["DispatcherServlet 與請求生命週期", "Customer / Contact / Interaction / Opportunity 拆解", "Controller / Service 分工與 DTO 邊界", "Bean Validation 與可讀的 400 錯誤"]
  }, {
    n: "03",
    t: "PostgreSQL、Flyway、JPA 與動態查詢",
    out: "CRM 資料庫與查詢服務",
    tags: ["JPA", "Flyway", "Specification"],
    points: ["Docker Compose 啟動 PostgreSQL + pgvector", "Flyway 命名規則與 migration 策略", "Entity mapping、關聯與 audit 欄位", "用 Specification 組合多條件搜尋"]
  }, {
    n: "04",
    t: "Spring Security、JWT、OpenAPI 與企業級錯誤處理",
    out: "安全可測的後端 API",
    tags: ["Security", "JWT", "OpenAPI"],
    points: ["Filter chain 與密碼雜湊", "JWT 簽發、角色與過期時間", "保護 /api/customers/**、/api/opportunities/**", "@ControllerAdvice + ProblemDetail 統一錯誤"]
  }, {
    n: "05",
    t: "React CRM 工作台與前後端整合",
    out: "可登入的 CRM UI",
    tags: ["React", "TypeScript", "整合"],
    points: ["API client、JWT 儲存與自動帶 token", "CRM Dashboard 資訊架構", "客戶列表、搜尋、客戶詳情頁", "商機看板與 loading / error / empty 狀態"]
  }, {
    n: "06",
    t: "Spring AI ChatClient、SSE 與 tool calling",
    out: "AI CRM 助理聊天室",
    tags: ["Spring AI", "SSE", "Tool Calling"],
    points: ["ChatClient 與 system prompt 設計", "SSE endpoint 與 React 串流顯示", "CrmInsightTools：客戶統計、商機健康分數", "信任邊界：數字由工具算、文字由模型寫"]
  }, {
    n: "07",
    t: "RAG、pgvector、MCP 與知識庫擴充",
    out: "可引用文件的 AI 建議",
    tags: ["RAG", "pgvector", "MCP"],
    points: ["文件切片、embedding 與 metadata 設計", "pgvector schema 與檢索流程", "AI 推薦產品時引用知識庫來源", "domain tool / RAG / MCP 的選用判斷"]
  }, {
    n: "08",
    t: "結訓專案衝刺與 Demo Day 驗收",
    out: "可展示的完整 AI CRM",
    tags: ["整合", "測試", "Demo Day"],
    points: ["端到端整合：CORS、JWT、seed data 對齊", "三種典型客戶場景的端到端測試", "上線檢查清單：安全 / 效能 / 可觀測性", "Demo Day 腳本：登入→客戶→AI→RAG→Swagger"]
  }],
  faq: [{
    q: "這門課適合完全沒寫過 Java 的新手嗎？",
    a: "建議先具備基本 Java 語法與 REST API 概念。課程著重在企業級分層、整合與 AI 落地，而不是從零教語法；若你寫過簡單的 API，就能順利跟上。"
  }, {
    q: "課程用的是哪些技術版本？",
    a: "Java 21、Spring Boot 4.0.x、Spring AI 2.0.x (GA)、PostgreSQL + pgvector、React + TypeScript + Vite。開發環境以 Windows + PowerShell 7+ 示範，macOS / Linux 也可比照操作。"
  }, {
    q: "這跟坊間「呼叫 LLM API」的課有什麼不同？",
    a: "我們不只教你呼叫 LLM，而是帶你把 AI 放進一套真正能上線的企業系統：資料庫、權限、前端、RAG、工具呼叫與端到端整合一次到位。"
  }, {
    q: "Embabel / AI Agent 框架會教嗎？",
    a: "Embabel 的 GOAP、Action、Goal、Blackboard 內容會以選修附錄提供，待 Embabel 2.0 與 Spring AI 2.0 穩定後納入正式單元。"
  }, {
    q: "買了之後可以看多久？",
    a: "購買後可不限次數、無限期觀看，課程更新也會免費同步給你。"
  }, {
    q: "完成課程後我會做出什麼？",
    a: "一套可登入、可查客戶、可引用知識庫、可產生業務建議、可追蹤 AI 執行過程的企業級 AI CRM，並能在 Demo Day 完整跑通展示。"
  }]
};
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/hahow-course/data.js", error: String((e && e.message) || e) }); }

// ui_kits/hahow-course/icons.jsx
try { (() => {
// Lucide → React icon helper, shared via window.Icon
(function () {
  const toPascal = s => s.replace(/(^|-)(\w)/g, (_, __, c) => c.toUpperCase());
  function Icon({
    name,
    size = 22,
    stroke = 2,
    style,
    ...rest
  }) {
    const L = window.lucide || {};
    const pas = toPascal(name);
    const node = L.icons && (L.icons[pas] || L.icons[name]) || L[pas] || L[name];
    if (!node) return null;
    // lucide nodes come as ["svg", attrs, [[tag,attrs],...]] or an iconNode array of [tag,attrs]
    let arr;
    if (Array.isArray(node) && node[0] === "svg") arr = node[2] || [];else if (Array.isArray(node)) arr = node;else arr = node.iconNode || [];
    const children = arr.map((c, i) => React.createElement(c[0], {
      key: i,
      ...c[1]
    }));
    return React.createElement("svg", {
      width: size,
      height: size,
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      strokeWidth: stroke,
      strokeLinecap: "round",
      strokeLinejoin: "round",
      style: {
        display: "block",
        flex: "0 0 auto",
        ...style
      },
      ...rest
    }, children);
  }
  window.Icon = Icon;
})();
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/hahow-course/icons.jsx", error: String((e && e.message) || e) }); }

// ui_kits/hahow-course/parts1.jsx
try { (() => {
// Header + Hero + sticky PurchaseCard
const {
  Button,
  Eyebrow,
  Badge,
  Card,
  ProgressBar
} = window.DesignSystem_55f34f;
function Header({
  onEnroll
}) {
  const links = [["課程介紹", "#intro"], ["課程大綱", "#curriculum"], ["實戰專案", "#project"], ["講師介紹", "#instructor"], ["常見問題", "#faq"]];
  return /*#__PURE__*/React.createElement("header", {
    style: {
      position: "sticky",
      top: 0,
      zIndex: 50,
      display: "flex",
      alignItems: "center",
      justifyContent: "space-between",
      gap: 24,
      padding: "14px clamp(16px, 4vw, 56px)",
      background: "color-mix(in oklab, var(--surface) 88%, transparent)",
      borderBottom: "1px solid var(--border)",
      backdropFilter: "var(--glass-blur)",
      WebkitBackdropFilter: "var(--glass-blur)"
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      alignItems: "center",
      gap: 10,
      fontWeight: 900,
      fontSize: "1.15rem"
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      color: "var(--accent)"
    }
  }, "\u51F1\u6587"), "\u5927\u53D4", /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: "0.72rem",
      fontWeight: 800,
      color: "var(--muted-2)",
      letterSpacing: "0.16em",
      textTransform: "uppercase",
      marginLeft: 4
    }
  }, "Uncle Kevin")), /*#__PURE__*/React.createElement("nav", {
    style: {
      display: "flex",
      gap: 4,
      alignItems: "center"
    },
    className: "hc-nav"
  }, links.map(([label, href]) => /*#__PURE__*/React.createElement("a", {
    key: href,
    href: href,
    style: {
      padding: "8px 14px",
      borderRadius: "var(--r-pill)",
      textDecoration: "none",
      color: "var(--muted)",
      fontWeight: 700,
      fontSize: "0.94rem"
    },
    onMouseEnter: e => {
      e.currentTarget.style.background = "var(--surface-2)";
      e.currentTarget.style.color = "var(--fg)";
    },
    onMouseLeave: e => {
      e.currentTarget.style.background = "transparent";
      e.currentTarget.style.color = "var(--muted)";
    }
  }, label))), /*#__PURE__*/React.createElement(Button, {
    size: "sm",
    onClick: onEnroll,
    iconRight: /*#__PURE__*/React.createElement(window.Icon, {
      name: "arrow-right",
      size: 16
    })
  }, "\u7ACB\u5373\u5831\u540D"));
}
function Hero() {
  const c = window.COURSE;
  return /*#__PURE__*/React.createElement("section", {
    style: {
      position: "relative",
      overflow: "hidden",
      color: "#fff"
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      position: "absolute",
      inset: 0
    }
  }, /*#__PURE__*/React.createElement("img", {
    src: "../../assets/cover.png",
    alt: "",
    style: {
      width: "100%",
      height: "100%",
      objectFit: "cover",
      opacity: 0.28,
      mixBlendMode: "luminosity"
    }
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      position: "absolute",
      inset: 0,
      background: "linear-gradient(120deg, oklch(20% 0.04 248 / 0.94), oklch(34% 0.07 232 / 0.78))"
    }
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      position: "relative",
      maxWidth: 1100,
      margin: "0 auto",
      padding: "clamp(48px, 7vw, 88px) clamp(16px, 4vw, 56px)"
    }
  }, /*#__PURE__*/React.createElement(Eyebrow, null, "\u2605 Hahow \u7DDA\u4E0A\u8AB2\u7A0B\u52DF\u8CC7\u4E2D"), /*#__PURE__*/React.createElement("h1", {
    style: {
      margin: "20px 0 18px",
      fontSize: "clamp(2.2rem, 5vw, 4rem)",
      lineHeight: 1.08,
      fontWeight: 900,
      maxWidth: 920
    }
  }, c.titleZh), /*#__PURE__*/React.createElement("p", {
    style: {
      margin: 0,
      maxWidth: 760,
      fontSize: "clamp(1.02rem, 1.6vw, 1.2rem)",
      lineHeight: 1.9,
      color: "oklch(96% 0.01 250 / 0.92)"
    }
  }, c.tagline), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      flexWrap: "wrap",
      gap: 22,
      marginTop: 30,
      alignItems: "center"
    }
  }, /*#__PURE__*/React.createElement(HeroStat, {
    value: c.units.length,
    label: "\u5BE6\u6230\u55AE\u5143"
  }), /*#__PURE__*/React.createElement(Divider, null), /*#__PURE__*/React.createElement(HeroStat, {
    value: "1",
    label: "\u5B8C\u6574 AI CRM \u5C08\u6848"
  }), /*#__PURE__*/React.createElement(Divider, null), /*#__PURE__*/React.createElement(HeroStat, {
    value: c.stack.length + "+",
    label: "\u6280\u8853\u68E7\u6574\u5408"
  }), /*#__PURE__*/React.createElement(Divider, null), /*#__PURE__*/React.createElement(HeroStat, {
    value: c.pricing.backers.toLocaleString(),
    label: "\u5DF2\u5831\u540D\u5B78\u54E1"
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      flexWrap: "wrap",
      gap: 8,
      marginTop: 30
    }
  }, c.stack.slice(0, 8).map(s => /*#__PURE__*/React.createElement("span", {
    key: s,
    style: {
      padding: "7px 13px",
      borderRadius: "var(--r-pill)",
      border: "1px solid oklch(100% 0 0 / 0.2)",
      background: "oklch(100% 0 0 / 0.1)",
      fontSize: "0.86rem",
      fontWeight: 700
    }
  }, s)))));
}
function HeroStat({
  value,
  label
}) {
  return /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: "1.9rem",
      fontWeight: 900,
      lineHeight: 1,
      color: "var(--accent-2)"
    }
  }, value), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: "0.84rem",
      color: "oklch(94% 0.01 250 / 0.78)",
      marginTop: 5,
      fontWeight: 700
    }
  }, label));
}
function Divider() {
  return /*#__PURE__*/React.createElement("div", {
    style: {
      width: 1,
      height: 34,
      background: "oklch(100% 0 0 / 0.18)"
    }
  });
}
function PurchaseCard({
  onEnroll
}) {
  const p = window.COURSE.pricing;
  const includes = [["clapperboard", "8 大單元 · 40+ 實戰影片"], ["infinity", "購買後終身無限觀看"], ["sparkles", "全課 AI Agent 提示詞包"], ["file-badge", "結業證書與專案原始碼"]];
  return /*#__PURE__*/React.createElement(Card, {
    variant: "solid",
    padding: 0,
    style: {
      overflow: "hidden",
      position: "sticky",
      top: 88
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      position: "relative"
    }
  }, /*#__PURE__*/React.createElement("img", {
    src: "../../assets/cover.png",
    alt: "\u8AB2\u7A0B\u5C01\u9762",
    style: {
      width: "100%",
      height: 168,
      objectFit: "cover"
    }
  }), /*#__PURE__*/React.createElement("button", {
    onClick: onEnroll,
    "aria-label": "\u64AD\u653E\u8A66\u770B",
    style: {
      position: "absolute",
      inset: 0,
      margin: "auto",
      width: 58,
      height: 58,
      borderRadius: "var(--r-pill)",
      border: "none",
      cursor: "pointer",
      background: "color-mix(in oklab, var(--accent) 92%, black)",
      color: "#fff",
      display: "grid",
      placeItems: "center",
      boxShadow: "var(--shadow-lift)"
    }
  }, /*#__PURE__*/React.createElement(window.Icon, {
    name: "play",
    size: 24,
    style: {
      marginLeft: 3
    }
  })), /*#__PURE__*/React.createElement("span", {
    style: {
      position: "absolute",
      top: 12,
      left: 12
    }
  }, /*#__PURE__*/React.createElement(Badge, {
    tone: "amber",
    solid: true
  }, "\u65E9\u9CE5\u52DF\u8CC7\u4E2D"))), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: "20px 22px 24px"
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      alignItems: "baseline",
      gap: 10
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: "1.9rem",
      fontWeight: 900,
      color: "var(--accent-deep)"
    }
  }, "NT$ ", p.early.toLocaleString()), /*#__PURE__*/React.createElement("span", {
    style: {
      color: "var(--muted-2)",
      textDecoration: "line-through",
      fontWeight: 700
    }
  }, "NT$ ", p.list.toLocaleString())), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: 16
    }
  }, /*#__PURE__*/React.createElement(ProgressBar, {
    value: p.goalPct,
    label: p.goalLabel,
    showValue: true
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      justifyContent: "space-between",
      marginTop: 10,
      fontSize: "0.86rem",
      color: "var(--muted)",
      fontWeight: 700
    }
  }, /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement("b", {
    style: {
      color: "var(--fg)"
    }
  }, p.backers.toLocaleString()), " \u4EBA\u8D0A\u52A9"), /*#__PURE__*/React.createElement("span", null, "\u5012\u6578 ", /*#__PURE__*/React.createElement("b", {
    style: {
      color: "var(--accent-2-deep)"
    }
  }, p.daysLeft), " \u5929"))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "grid",
      gap: 10,
      marginTop: 20
    }
  }, /*#__PURE__*/React.createElement(Button, {
    size: "lg",
    onClick: onEnroll,
    style: {
      width: "100%"
    }
  }, "\u7ACB\u5373\u5831\u540D \xB7 \u9396\u5B9A\u65E9\u9CE5\u50F9"), /*#__PURE__*/React.createElement(Button, {
    size: "md",
    variant: "secondary",
    onClick: onEnroll,
    style: {
      width: "100%"
    },
    iconLeft: /*#__PURE__*/React.createElement(window.Icon, {
      name: "play",
      size: 16
    })
  }, "\u514D\u8CBB\u8A66\u770B\u55AE\u5143")), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "grid",
      gap: 11,
      marginTop: 22
    }
  }, includes.map(([icon, label]) => /*#__PURE__*/React.createElement("div", {
    key: label,
    style: {
      display: "flex",
      alignItems: "center",
      gap: 10,
      color: "var(--muted)",
      fontSize: "0.92rem",
      fontWeight: 600
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      color: "var(--success)"
    }
  }, /*#__PURE__*/React.createElement(window.Icon, {
    name: icon,
    size: 18
  })), label)))));
}
Object.assign(window, {
  Header,
  Hero,
  PurchaseCard
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/hahow-course/parts1.jsx", error: String((e && e.message) || e) }); }

// ui_kits/hahow-course/parts2.jsx
try { (() => {
// Section heading helper + Intro/Outcomes, Features, Audience
const {
  Eyebrow: Eb,
  Card: Cd,
  Callout: Co,
  Badge: Bd
} = window.DesignSystem_55f34f;
function SectionTitle({
  kicker,
  title,
  lead,
  id
}) {
  return /*#__PURE__*/React.createElement("div", {
    id: id,
    style: {
      scrollMarginTop: 88,
      marginBottom: 28
    }
  }, kicker ? /*#__PURE__*/React.createElement(Eb, {
    tone: "accent",
    icon: "\u25CF"
  }, kicker) : null, /*#__PURE__*/React.createElement("h2", {
    style: {
      margin: "14px 0 0",
      fontSize: "clamp(1.6rem, 3vw, 2.1rem)",
      fontWeight: 900,
      lineHeight: 1.2
    }
  }, title), lead ? /*#__PURE__*/React.createElement("p", {
    style: {
      margin: "12px 0 0",
      maxWidth: 680,
      color: "var(--muted)",
      lineHeight: 1.9,
      fontSize: "1.02rem"
    }
  }, lead) : null);
}
function Intro() {
  const c = window.COURSE;
  return /*#__PURE__*/React.createElement("section", {
    style: {
      paddingTop: 8
    }
  }, /*#__PURE__*/React.createElement(SectionTitle, {
    id: "intro",
    kicker: "\u8AB2\u7A0B\u4ECB\u7D39",
    title: "\u5B8C\u8AB2\u5F8C\uFF0C\u4F60\u6703\u505A\u51FA\u4EC0\u9EBC\uFF1F",
    lead: "\u9019\u4E0D\u662F\u4E00\u5806\u96F6\u6563\u7BC4\u4F8B\uFF0C\u800C\u662F\u4E00\u5957\u5F9E\u767B\u5165\u5230 AI \u5C0D\u8A71\u5B8C\u6574\u8DD1\u901A\u7684\u4F01\u696D\u7D1A AI CRM\u3002\u4EE5\u4E0B\u662F\u4F60\u6703\u89AA\u624B\u5EFA\u7ACB\u7684\u80FD\u529B\uFF1A"
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "grid",
      gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))",
      gap: 16
    }
  }, c.outcomes.map((o, i) => /*#__PURE__*/React.createElement(Cd, {
    key: i,
    variant: "solid",
    hover: true,
    padding: 22,
    style: {
      boxShadow: "var(--shadow-sm)"
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      alignItems: "center",
      gap: 12,
      marginBottom: 12
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      display: "grid",
      placeItems: "center",
      width: 40,
      height: 40,
      borderRadius: 12,
      background: "color-mix(in oklab, var(--accent) 14%, transparent)",
      color: "var(--accent)",
      fontWeight: 900
    }
  }, String(i + 1).padStart(2, "0")), /*#__PURE__*/React.createElement("strong", {
    style: {
      fontSize: "1.04rem",
      lineHeight: 1.3
    }
  }, o.t)), /*#__PURE__*/React.createElement("p", {
    style: {
      margin: 0,
      color: "var(--muted)",
      lineHeight: 1.8,
      fontSize: "0.95rem"
    }
  }, o.d)))));
}
function Features() {
  const c = window.COURSE;
  return /*#__PURE__*/React.createElement("section", null, /*#__PURE__*/React.createElement(SectionTitle, {
    kicker: "\u70BA\u4EC0\u9EBC\u9078\u9019\u9580\u8AB2",
    title: "\u4E0D\u53EA\u6559\u4F60\u547C\u53EB LLM\uFF0C\u800C\u662F\u8B93 AI \u771F\u6B63\u4E0A\u7DDA"
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "grid",
      gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))",
      gap: 16
    }
  }, c.features.map((f, i) => /*#__PURE__*/React.createElement(Cd, {
    key: i,
    variant: "sunken",
    padding: 24,
    style: {
      boxShadow: "none",
      display: "flex",
      gap: 16
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      display: "grid",
      placeItems: "center",
      width: 48,
      height: 48,
      borderRadius: 14,
      flex: "0 0 auto",
      background: "var(--surface)",
      border: "1px solid var(--border)",
      color: "var(--accent)"
    }
  }, /*#__PURE__*/React.createElement(window.Icon, {
    name: f.icon,
    size: 24
  })), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("strong", {
    style: {
      display: "block",
      fontSize: "1.06rem",
      marginBottom: 6
    }
  }, f.t), /*#__PURE__*/React.createElement("p", {
    style: {
      margin: 0,
      color: "var(--muted)",
      lineHeight: 1.8,
      fontSize: "0.95rem"
    }
  }, f.d))))));
}
function Audience() {
  const c = window.COURSE;
  return /*#__PURE__*/React.createElement("section", null, /*#__PURE__*/React.createElement(SectionTitle, {
    kicker: "\u9069\u5408\u5C0D\u8C61",
    title: "\u9019\u9580\u8AB2\u70BA\u8AB0\u800C\u8A2D\u8A08"
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "grid",
      gap: 12
    }
  }, c.audience.map((a, i) => /*#__PURE__*/React.createElement("div", {
    key: i,
    style: {
      display: "flex",
      alignItems: "flex-start",
      gap: 14,
      padding: "16px 18px",
      borderRadius: "var(--r-md)",
      border: "1px solid var(--border)",
      background: "var(--surface)"
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      color: "var(--success)",
      marginTop: 1
    }
  }, /*#__PURE__*/React.createElement(window.Icon, {
    name: "circle-check-big",
    size: 22
  })), /*#__PURE__*/React.createElement("span", {
    style: {
      lineHeight: 1.75,
      color: "var(--fg)"
    }
  }, a)))), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: 16
    }
  }, /*#__PURE__*/React.createElement(Co, {
    tone: "amber",
    title: "\u5148\u5099\u77E5\u8B58"
  }, "\u9700\u5177\u5099\u57FA\u672C Java \u8A9E\u6CD5\u8207 REST API \u6982\u5FF5\u3002\u8AB2\u7A0B\u8457\u91CD\u4F01\u696D\u7D1A\u5206\u5C64\u3001\u6574\u5408\u8207 AI \u843D\u5730\uFF0C\u4E0D\u5F9E\u96F6\u6559\u8A9E\u6CD5\u2014\u2014\u5BEB\u904E\u7C21\u55AE API \u5C31\u80FD\u8DDF\u4E0A\u3002")));
}
Object.assign(window, {
  SectionTitle,
  Intro,
  Features,
  Audience
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/hahow-course/parts2.jsx", error: String((e && e.message) || e) }); }

// ui_kits/hahow-course/parts3.jsx
try { (() => {
// Project showcase (AI CRM) + Curriculum accordion
const {
  Card: PCard,
  Badge: PBadge,
  Accordion: PAccordion
} = window.DesignSystem_55f34f;
function ProjectShowcase() {
  const modules = [["認證與權限", "登入、JWT、角色權限、API 保護", "Spring Security"], ["客戶管理", "客戶、聯絡人、標籤、分級", "Spring MVC + JPA"], ["商機管理", "階段、金額、預計成交日、風險狀態", "JPA + Specification"], ["AI 摘要", "客戶摘要、風險訊號、下一步建議", "Spring AI"], ["RAG 知識庫", "產品文件、銷售話術、條款檢索", "pgvector"], ["工具呼叫", "讀真實資料、算商機健康分數", "Tool Calling"]];
  return /*#__PURE__*/React.createElement("section", null, /*#__PURE__*/React.createElement(window.SectionTitle, {
    id: "project",
    kicker: "\u8CAB\u7A7F\u6848\u4F8B",
    title: "\u4E00\u5957 B2B AI CRM \u667A\u6167\u696D\u52D9\u52A9\u7406",
    lead: "\u5F9E\u57FA\u672C CRM \u529F\u80FD\u958B\u59CB\uFF0C\u9010\u6B65\u52A0\u5165 AI \u80FD\u529B\u3002AI \u4E0D\u66FF\u4EE3\u6C7A\u7B56\uFF0C\u800C\u662F\u63D0\u4F9B\u53EF\u8FFD\u8E64\u7684\u5EFA\u8B70\u2014\u2014\u91D1\u984D\u8207\u6B0A\u9650\u7531 Java \u7B97\uFF0C\u6458\u8981\u8207\u8349\u7A3F\u7531\u6A21\u578B\u5BEB\u3002"
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "grid",
      gridTemplateColumns: "1.15fr 1fr",
      gap: 22,
      alignItems: "stretch"
    },
    className: "hc-project"
  }, /*#__PURE__*/React.createElement(PCard, {
    variant: "solid",
    padding: 0,
    style: {
      overflow: "hidden"
    }
  }, /*#__PURE__*/React.createElement("img", {
    src: "../../assets/cover.png",
    alt: "AI CRM Dashboard",
    style: {
      width: "100%",
      height: 260,
      objectFit: "cover",
      display: "block"
    }
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: "18px 20px"
    }
  }, /*#__PURE__*/React.createElement("strong", {
    style: {
      display: "block",
      marginBottom: 6
    }
  }, "\u696D\u52D9\u6253\u958B\u5BA2\u6236\u9801\uFF0C\u7CFB\u7D71\u81EA\u52D5\u6574\u7406\u8FD1\u6CC1"), /*#__PURE__*/React.createElement("p", {
    style: {
      margin: 0,
      color: "var(--muted)",
      lineHeight: 1.8,
      fontSize: "0.94rem"
    }
  }, "\u5BA2\u6236\u662F\u5426\u6D3B\u8E8D\uFF1F\u6709\u54EA\u4E9B\u98A8\u96AA\u8A0A\u865F\uFF1F\u662F\u5426\u53EF\u80FD\u6D41\u5931\uFF1F\u8A72\u63A8\u85A6\u4EC0\u9EBC\u65B9\u6848\uFF1F\u4E0B\u4E00\u6B65\u8A72\u5BC4\u4FE1\u3001\u7D04\u6703\u8B70\u9084\u662F\u8ACB\u4E3B\u7BA1\u4ECB\u5165\uFF1F"))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "grid",
      gridTemplateColumns: "1fr",
      gap: 10
    }
  }, modules.map(([t, d, tech]) => /*#__PURE__*/React.createElement("div", {
    key: t,
    style: {
      display: "flex",
      alignItems: "center",
      gap: 14,
      padding: "13px 16px",
      borderRadius: "var(--r-md)",
      border: "1px solid var(--border)",
      background: "var(--surface)"
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1
    }
  }, /*#__PURE__*/React.createElement("strong", {
    style: {
      fontSize: "0.98rem"
    }
  }, t), /*#__PURE__*/React.createElement("div", {
    style: {
      color: "var(--muted)",
      fontSize: "0.86rem",
      marginTop: 2
    }
  }, d)), /*#__PURE__*/React.createElement(PBadge, {
    tone: "accent"
  }, tech))))));
}
function Curriculum() {
  const c = window.COURSE;
  return /*#__PURE__*/React.createElement("section", null, /*#__PURE__*/React.createElement(window.SectionTitle, {
    id: "curriculum",
    kicker: "\u8AB2\u7A0B\u5927\u7DB1",
    title: "8 \u5927\u55AE\u5143\uFF0C\u7AE0\u7AE0\u76F8\u9023",
    lead: "\u6BCF\u500B\u55AE\u5143\u90FD\u6709\u660E\u78BA\u7522\u51FA\uFF0C\u4E14\u5168\u90E8\u63A5\u5230\u540C\u4E00\u5957 AI CRM \u5C08\u6848\u3002\u53EF\u62C6\u6210 4 \u5929\u5DE5\u4F5C\u574A\u6216 Hahow \u7DDA\u4E0A\u7AE0\u7BC0\u3002"
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "grid",
      gap: 12
    }
  }, c.units.map((u, i) => /*#__PURE__*/React.createElement(PAccordion, {
    key: u.n,
    defaultOpen: i === 0,
    summary: /*#__PURE__*/React.createElement("span", {
      style: {
        display: "flex",
        alignItems: "center",
        gap: 14,
        flexWrap: "wrap"
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: "1.5rem",
        fontWeight: 900,
        color: "var(--accent-2)",
        lineHeight: 1
      }
    }, u.n), /*#__PURE__*/React.createElement("span", {
      style: {
        fontWeight: 800
      }
    }, u.t))
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      flexWrap: "wrap",
      gap: 8,
      margin: "2px 0 16px"
    }
  }, u.tags.map(t => /*#__PURE__*/React.createElement(PBadge, {
    key: t,
    tone: "neutral"
  }, t))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "grid",
      gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
      gap: 10,
      marginBottom: 16
    }
  }, u.points.map((p, j) => /*#__PURE__*/React.createElement("div", {
    key: j,
    style: {
      display: "flex",
      gap: 9,
      alignItems: "flex-start",
      fontSize: "0.92rem",
      color: "var(--fg)"
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      color: "var(--accent)",
      marginTop: 1
    }
  }, /*#__PURE__*/React.createElement(window.Icon, {
    name: "check",
    size: 16
  })), p))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "inline-flex",
      alignItems: "center",
      gap: 8,
      padding: "8px 14px",
      borderRadius: "var(--r-pill)",
      background: "color-mix(in oklab, var(--success) 12%, transparent)",
      color: "var(--success)",
      fontWeight: 800,
      fontSize: "0.9rem"
    }
  }, /*#__PURE__*/React.createElement(window.Icon, {
    name: "package-check",
    size: 17
  }), "\u6838\u5FC3\u7522\u51FA \xB7 ", u.out)))));
}
Object.assign(window, {
  ProjectShowcase,
  Curriculum
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/hahow-course/parts3.jsx", error: String((e && e.message) || e) }); }

// ui_kits/hahow-course/parts4.jsx
try { (() => {
// Instructor + FAQ + Pricing CTA + Footer
const {
  Card: FCard,
  Accordion: FAccordion,
  Button: FButton,
  Eyebrow: FEyebrow,
  Badge: FBadge
} = window.DesignSystem_55f34f;
function Instructor() {
  const creds = ["《Spring AI》系列技術作者，鐵人賽 30 天 Spring AI 連載", "深耕 Java / Spring Boot 企業開發與 AI 落地教學", "經營 AI 全端開發社群，帶領上千名工程師導入 AI Agent 協作"];
  return /*#__PURE__*/React.createElement("section", null, /*#__PURE__*/React.createElement(window.SectionTitle, {
    id: "instructor",
    kicker: "\u8B1B\u5E2B\u4ECB\u7D39",
    title: "\u4F60\u7684\u8B1B\u5E2B\uFF1A\u51F1\u6587\u5927\u53D4"
  }), /*#__PURE__*/React.createElement(FCard, {
    variant: "solid",
    padding: 0,
    style: {
      overflow: "hidden",
      display: "grid",
      gridTemplateColumns: "300px 1fr"
    },
    className: "hc-instructor"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      background: "linear-gradient(150deg, oklch(34% 0.07 232), oklch(22% 0.04 248))",
      display: "grid",
      placeItems: "center",
      padding: 28
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      width: 132,
      height: 132,
      borderRadius: "var(--r-pill)",
      background: "oklch(100% 0 0 / 0.12)",
      border: "2px solid oklch(100% 0 0 / 0.3)",
      display: "grid",
      placeItems: "center",
      color: "#fff",
      fontSize: "2.6rem",
      fontWeight: 900
    }
  }, "\u51F1")), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: "28px 30px"
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      alignItems: "center",
      gap: 12,
      flexWrap: "wrap"
    }
  }, /*#__PURE__*/React.createElement("strong", {
    style: {
      fontSize: "1.4rem"
    }
  }, "\u51F1\u6587\u5927\u53D4 Uncle Kevin"), /*#__PURE__*/React.createElement(FBadge, {
    tone: "amber",
    solid: true
  }, "Spring AI \u4F5C\u8005")), /*#__PURE__*/React.createElement("p", {
    style: {
      margin: "12px 0 18px",
      color: "var(--muted)",
      lineHeight: 1.9
    }
  }, "\u5C08\u6CE8\u628A Spring Boot\u3001Spring AI \u8207 React \u4E32\u6210\u53EF\u4E0A\u7DDA\u7684\u4F01\u696D\u7D1A\u7CFB\u7D71\u3002\u6559\u5B78\u98A8\u683C\u52D9\u5BE6\u2014\u2014\u4E0D\u5806\u780C\u540D\u8A5E\uFF0C\u5E36\u4F60\u4E00\u884C\u4E00\u884C\u628A AI \u653E\u9032\u771F\u5BE6\u7522\u54C1\u3002"), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "grid",
      gap: 10
    }
  }, creds.map((cr, i) => /*#__PURE__*/React.createElement("div", {
    key: i,
    style: {
      display: "flex",
      gap: 10,
      alignItems: "flex-start",
      fontSize: "0.95rem",
      color: "var(--fg)"
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      color: "var(--accent)",
      marginTop: 1
    }
  }, /*#__PURE__*/React.createElement(window.Icon, {
    name: "badge-check",
    size: 19
  })), cr))))));
}
function Faq() {
  const c = window.COURSE;
  return /*#__PURE__*/React.createElement("section", null, /*#__PURE__*/React.createElement(window.SectionTitle, {
    id: "faq",
    kicker: "\u5E38\u898B\u554F\u984C",
    title: "\u5831\u540D\u524D\uFF0C\u5148\u770B\u9019\u4E9B"
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "grid",
      gap: 12
    }
  }, c.faq.map((f, i) => /*#__PURE__*/React.createElement(FAccordion, {
    key: i,
    summary: f.q
  }, f.a))));
}
function PricingCTA({
  onEnroll
}) {
  const p = window.COURSE.pricing;
  return /*#__PURE__*/React.createElement("section", null, /*#__PURE__*/React.createElement(FCard, {
    padding: 0,
    style: {
      overflow: "hidden",
      position: "relative",
      color: "#fff",
      border: "none"
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      position: "absolute",
      inset: 0,
      background: "linear-gradient(120deg, oklch(30% 0.07 248), oklch(46% 0.12 240))"
    }
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      position: "relative",
      padding: "clamp(32px, 5vw, 56px)",
      textAlign: "center"
    }
  }, /*#__PURE__*/React.createElement(FEyebrow, null, "\u2605 \u52DF\u8CC7\u9650\u5B9A \xB7 \u65E9\u9CE5\u512A\u60E0"), /*#__PURE__*/React.createElement("h2", {
    style: {
      margin: "18px 0 10px",
      fontSize: "clamp(1.7rem, 3.5vw, 2.6rem)",
      fontWeight: 900
    }
  }, "\u73FE\u5728\u5831\u540D\uFF0C\u9396\u5B9A\u6700\u4F4E\u65E9\u9CE5\u50F9"), /*#__PURE__*/React.createElement("p", {
    style: {
      margin: "0 auto 22px",
      maxWidth: 560,
      lineHeight: 1.85,
      color: "oklch(96% 0.01 250 / 0.9)"
    }
  }, "\u5B8C\u8AB2\u5F8C\u4F60\u6703\u64C1\u6709\u4E00\u5957\u53EF\u767B\u5165\u3001\u53EF\u67E5\u8CC7\u6599\u3001\u53EF\u5F15\u7528\u77E5\u8B58\u5EAB\u3001\u53EF\u7522\u751F\u696D\u52D9\u5EFA\u8B70\u7684\u4F01\u696D\u7D1A AI CRM\u3002"), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      alignItems: "baseline",
      justifyContent: "center",
      gap: 12,
      marginBottom: 22
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: "2.6rem",
      fontWeight: 900,
      color: "var(--accent-2)"
    }
  }, "NT$ ", p.early.toLocaleString()), /*#__PURE__*/React.createElement("span", {
    style: {
      textDecoration: "line-through",
      opacity: 0.7,
      fontWeight: 700
    }
  }, "NT$ ", p.list.toLocaleString())), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      gap: 14,
      justifyContent: "center",
      flexWrap: "wrap"
    }
  }, /*#__PURE__*/React.createElement(FButton, {
    variant: "light",
    size: "lg",
    onClick: onEnroll,
    iconRight: /*#__PURE__*/React.createElement(window.Icon, {
      name: "arrow-right",
      size: 18
    })
  }, "\u7ACB\u5373\u5831\u540D"), /*#__PURE__*/React.createElement(FButton, {
    variant: "ghost",
    size: "lg",
    onClick: onEnroll,
    style: {
      color: "#fff",
      border: "1px solid oklch(100% 0 0 / 0.32)"
    }
  }, "\u514D\u8CBB\u8A66\u770B\u55AE\u5143")))));
}
function Footer() {
  return /*#__PURE__*/React.createElement("footer", {
    style: {
      textAlign: "center",
      padding: "40px 16px 28px",
      color: "var(--muted)",
      lineHeight: 1.9
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontWeight: 900,
      fontSize: "1.05rem",
      color: "var(--fg)",
      marginBottom: 6
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      color: "var(--accent)"
    }
  }, "\u51F1\u6587"), "\u5927\u53D4 \xB7 AI \u8CE6\u80FD\u5168\u7AEF\u958B\u767C"), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: "0.9rem"
    }
  }, "AI-Empowered Full-Stack \xB7 Building Enterprise-Grade Smart Apps"), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: "0.82rem",
      marginTop: 12,
      color: "var(--muted-2)"
    }
  }, "\xA9 2026 Uncle Kevin. Hahow \u8AB2\u7A0B\u92B7\u552E\u9801\u7BC4\u672C \xB7 \u5167\u5BB9\u6574\u7406\u81EA ai-full-stack \u8AB2\u7DB1\u3002"));
}
Object.assign(window, {
  Instructor,
  Faq,
  PricingCTA,
  Footer
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/hahow-course/parts4.jsx", error: String((e && e.message) || e) }); }

__ds_ns.Button = __ds_scope.Button;

__ds_ns.Accordion = __ds_scope.Accordion;

__ds_ns.ProgressBar = __ds_scope.ProgressBar;

__ds_ns.Badge = __ds_scope.Badge;

__ds_ns.Eyebrow = __ds_scope.Eyebrow;

__ds_ns.Callout = __ds_scope.Callout;

__ds_ns.Card = __ds_scope.Card;

__ds_ns.Stat = __ds_scope.Stat;

})();
