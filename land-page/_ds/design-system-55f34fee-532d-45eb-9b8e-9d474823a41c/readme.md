# 凱文大叔 Design System (Uncle Kevin)

A design system for **凱文大叔 (Uncle Kevin)** — a Taiwanese developer-educator who teaches enterprise Java, Spring Boot, Spring AI and full-stack development. This system captures the brand's warm-but-technical visual language and ships the foundations, components, and a full **Hahow course sales page** for the flagship course *AI 賦能全端開發：從零打造企業級智慧應用* (AI-Empowered Full-Stack: Building Enterprise-Grade Smart Apps).

The primary deliverable in this project is the **Hahow 課程銷售網頁** (`ui_kits/hahow-course/`) — content, imagery and sales materials for the online course.

---

## Sources

This system was built by reading the brand's own teaching-site codebase. Explore these to go deeper:

- **GitHub — course content & teaching site:** https://github.com/kevintsai1202/ai-full-stack
  - `課程內容.md` — the full 8-unit syllabus, learning outcomes, and 賣點文案 (sales copy) the sales page draws from.
  - `teaching-site/styles.css` — the source of the OKLCH color system, Nunito + 粉圓 type stack, glass/shadow tokens reproduced here.
  - `teaching-site/assets/illustrations/` — the hero render (`cover.png`) and flat illustrations (`office.png`, etc.) used as imagery.
- **Author:** kevintsai1202 (also publishes the *Spring AI* tutorial series). Browse https://github.com/kevintsai1202 for related Spring AI / Spring Boot teaching repos.

> The reader is encouraged to explore the `ai-full-stack` repo directly to recreate or extend designs with full fidelity.

---

## CONTENT FUNDAMENTALS

**Language.** Traditional Chinese (zh-Hant, Taiwan) first, with English subtitles/keys for technical terms (e.g. "AI CRM 智慧業務助理", "Spring AI ChatClient"). Technical nouns stay in English inside Chinese sentences — never translated (`tool calling`, `RAG`, `pgvector`, `JWT`).

**Voice & person.** Direct second person — speaks to "你". Confident and practical, never hypey-empty. The signature framing is *contrast*: "這不是…而是…" ("this isn't X, it's Y"), e.g. *「這不是把多門課串在一起，而是用同一套 AI CRM 範例…」*. Promises are concrete and outcome-led: "完課後，你會做出什麼？" rather than vague benefits.

**Tone.** Warm, mentor-like, grounded. The brand name itself ("大叔" / "uncle") signals an approachable senior who's been there. Copy reassures ("寫過簡單 API 就能跟上") while staying rigorous ("數字由工具算、文字由模型寫").

**Casing.** Chinese has no case; English technical terms keep their canonical casing (Spring Boot, Spring AI, React, PostgreSQL). UPPERCASE is reserved for small Latin eyebrows/labels with letter-spacing (`UNIT 06`, `PROMPT`).

**Punctuation.** Full-width Chinese punctuation（，。、？）in prose. Numbers and units stay half-width ("8 大單元", "NT$ 3,680", "Java 21").

**Emoji.** Essentially none in product copy. The one recurring glyph is a **★ star** used inside amber eyebrow pills. Iconography is line-icons, not emoji.

**Vibe.** "務實的技術職人" — a pragmatic craftsperson. Every claim is backed by a deliverable; every unit lists its 核心產出 (core output). Avoid buzzword soup.

---

## VISUAL FOUNDATIONS

**Color.** Authored in **OKLCH** for perceptual consistency. Two accents over a cool-neutral base:
- **Primary — Spring/React blue** `--accent: oklch(52% 0.15 240)` with `-deep` and `-soft` variants. Used for links, primary buttons, key numbers.
- **Secondary — warm amber** `--accent-2: oklch(68% 0.14 65)`. The brand's "highlight" — eyebrow pills, day numerals, PROMPT tags, big price figures. This blue↔amber pairing is the brand's signature.
- **Neutrals** are very slightly blue-tinted (hue 220–250) rather than pure gray, giving a cool, calm UI.
- **Status:** success green `oklch(56% 0.14 150)`, warning, danger — each with a `-soft` tint.
- Full **light + dark** themes; `[data-theme="dark"]` re-points the same semantic names.

**Type.** **Nunito** (rounded humanist sans) for Latin + numerals, paired with **粉圓 / jf-openhuninn** (a rounded open-source Traditional-Chinese face) for CJK — falling back to Microsoft JhengHei / PingFang TC. Mono is JetBrains Mono. The whole system reads *rounded and friendly*. Weights run heavy (800–900 for headings; Nunito reads light otherwise). CJK body uses loose `1.9` line-height for readability.
  - ⚠️ **Font substitution:** 粉圓/open-huninn is not on Google Fonts, so this system loads **Noto Sans TC** as the web fallback. To restore 1:1 fidelity, drop the open-huninn webfont into `tokens/` and add an `@font-face`. *Please send the real font files if exact match matters.*

**Spacing & radii.** 4px base scale. Corners are generously rounded — `--r-sm 8` / `--r-md 14` / `--r-lg 20` / pill `999`. Friendly, never sharp.

**Backgrounds.** Soft **radial-gradient washes** of accent + amber bleeding from page corners over a near-white base — subtle, never loud. Hero bands use a dark blue diagonal gradient with a faded `luminosity`-blended product render behind.

**Imagery.** Two registers: (1) **3D glassmorphism tech renders** — cool blue, floating glass icon-tiles (Spring/React/DB/AI), dashboards, holographic UI (`cover.png`); (2) **warm flat vector illustrations** — calm offices, greenery, daylight (`office.png`). Concept diagrams are clean, labelled, light-background. Imagery skews cool/blue for hero, warm for human/illustration moments.

**Elevation & glass.** Signature **long, soft, low-opacity "lift" shadow** (`--shadow-lift`) rather than hard borders. Frosted **glass** surfaces (`backdrop-filter: blur(16px) saturate(140%)`) for sidebars, the top bar, and floating panels.

**Borders.** Hairline 1px `--border` (cool, low-contrast). Accent emphasis via a **left bar** (4px) on callouts/prompt boxes, never full colored outlines.

**Cards.** Rounded (`--r-lg`), `--surface` fill, hairline border, soft lift shadow. Hover = lift `translateY(-3px)` + accent border. Glass and sunken (`surface-2`) variants exist.

**Animation.** Quick and gentle. `--dur-fast 160ms` for hover/press, `--dur-med/slow` for entrances. Easing is `cubic-bezier(0.16,1,0.3,1)` (out) and `(0.4,0,0.2,1)` (standard). Hover lifts (`translateY(-1px)` + lift shadow); accordion `+` rotates 45° to ×; progress fills with a gradient sweep. Tasteful SVG micro-animations on concept diagrams (flowing dashes, pulsing). No bounce, no infinite loops on content.

**Hover / press.** Hover: subtle upward translate + lift shadow + slight brightness. Active surfaces (nav, chips, tabs) fill `surface-2` and darken text. Selected pills/tabs flip to solid `--accent` with white text.

**Transparency & blur.** Used deliberately for floating chrome (top bar, sidebar, hero pills) — `color-mix(... 82% surface, transparent)` + blur. Content surfaces stay opaque.

---

## ICONOGRAPHY

The teaching site draws icons as **inline line-style SVG glyphs** (stroke ~2px, rounded caps) — a Lucide/Feather aesthetic. This system standardizes on **[Lucide](https://lucide.dev)** (CDN-loaded), which matches that stroke weight and rounded-cap style.

- **Approach:** monochrome, stroke-based, `currentColor` so icons inherit text/accent color. ~16–24px in UI; placed inside soft-tinted rounded squares (`14px` radius, `accent @ 14%` fill) for feature/roadmap glyphs.
- **Helper:** `ui_kits/hahow-course/icons.jsx` exposes `window.Icon({name, size, stroke})` that renders any Lucide icon as React. Names are kebab-case (`shield-check`, `circle-check-big`).
- **Emoji:** not used as icons. The only glyph in chrome is the **★** inside amber eyebrows, and ✓ check marks (rendered as Lucide `check` / CSS `✓`) in goal/checklist items.
- **Substitution note:** the brand has no bespoke icon font in the repo, so Lucide is a documented stand-in. If a custom icon set exists, drop the SVGs into `assets/` and point `Icon` at them.

Brand imagery (logos/illustrations) lives in `assets/`: `cover.png` (hero render), `office.png` (flat illustration), `rag-flow.png`, `concept-rest-api.png`, `concept-env.png`.

---

## INDEX — what's in this system

**Foundations (root)**
- `styles.css` — the single stylesheet consumers link (imports only).
- `tokens/` — `fonts.css`, `colors.css`, `typography.css`, `spacing.css`, `effects.css`.
- `cards/` — 12 specimen cards (Colors ×4, Type ×3, Spacing ×3, Brand ×2) for the Design System tab.
- `assets/` — brand imagery.

**Components** (`components/`, exposed on `window.DesignSystem_55f34f`)
- `buttons/` — `Button` (primary / secondary / ghost / light · sm/md/lg · icons · disabled)
- `labels/` — `Eyebrow` (amber ★ kicker), `Badge` (semantic, soft/solid)
- `surfaces/` — `Card` (solid/glass/sunken), `Stat` (metric tile), `Callout` (tinted note)
- `feedback/` — `ProgressBar` (gradient track), `Accordion` (FAQ/curriculum disclosure)

**UI Kits** (`ui_kits/`)
- `hahow-course/` — **the Hahow course sales landing page** (the main deliverable). See its `README.md`.

**Other**
- `SKILL.md` — Agent-Skill manifest so this system can be used in Claude Code.

> Build with the design-system components first; reach for raw CSS tokens only for page-specific composites.
